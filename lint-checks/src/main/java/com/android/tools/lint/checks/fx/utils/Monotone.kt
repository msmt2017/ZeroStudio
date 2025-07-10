/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.lint.checks.fx.utils

import java.util.ArrayDeque
import java.util.Deque
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.plus

typealias Memo<K, V> = PersistentMap<K, V>

private typealias Deps<K> = PersistentMap<K, PersistentSet<K>>

/**
 * A [Monotone] on functions ([K] -> [V]), where [V] forms a lattice. This is conceptually ([K] ->
 * [V]) -> ([K] -> [V]), but the uncurried form is more convenient.
 *
 * An instance `F` of [Monotone] is also expected to satisfy that if `f ⊑ g` then `F(f) ⊑ F(g)`,
 * (or, more spelled out: ∀ `k`, if `f(k) ⊑ g(k)` then `F(f)(k) ⊑ F(g)(k)`).
 */
interface Monotone<K, V> : Lattice<V>, ((K) -> V, K) -> V, DependentMonotone<K, V> {
  override fun latticeAt(point: K) = this
}

/**
 * A [DependentMonotone] is a generalization of [Monotone], over functions that can map each
 * argument to an element belonging to a different lattice.
 *
 * This is needed for when we need to encode a family of mutually recursively functions with
 * different domains and (lattice) ranges. Kotlin doesn't allow expressing dependencies between
 * types, so we squash everything into [V].
 */
interface DependentMonotone<K, V> : ((K) -> V, K) -> V {
  fun latticeAt(point: K): Lattice<V>
}

/**
 * Compute the least fix point of a [Monotone] : ([K] -> [V]) -> ([K] -> [V]) of functions, starting
 * from a [bootstrap] (default being `_ ↦ ⊥`), over a given [domain] of interest.
 *
 * In order for [leastFixPoint] to converge: (1) The domain spanned by [domain] must be finite (i.e.
 * the [Monotone]'s implementation shouldn't keep applying its bootstrapping procedure to fresh
 * arguments), although it's ok for [domain] to span larger as computation progresses. (2) the
 * lattice on [V] must have a finite height.
 *
 * The [Monotone] implementation has the freedom to call its bootstrapping function as much or as
 * little as it wants, as long as it runs finitely. Calling the bootstrapping function too often
 * will only produce excessive and ineffective "checkpoints", while too little will result in
 * suboptimal sharing of intermediate results.
 *
 * An example of a reasonable [Monotone] implementation is an abstract interpreter or type inference
 * that structurally recurses on expressions, but relies on the bootstrapping function for
 * assumptions on top-level bindings.
 */
fun <K : Any, V> DependentMonotone<K, V>.leastFixPoint(
  domain: Collection<K>,
  bootstrap: Memo<K, V> = persistentMapOf(),
): Memo<K, V> {
  var known: Memo<K, V> = bootstrap
  var dependencies: Deps<K> = persistentMapOf()
  val workSet = UniqueDeque(domain)
  while (!workSet.isEmpty()) {
    val argument = workSet.removeNext()
    val (result, resultIsNew, newCallees) = step(known, dependencies, argument)

    // Update dependencies and accumulate new work
    for (newCallee in newCallees) {
      val callers =
        when (val existingCallers = dependencies[newCallee]) {
          null -> persistentSetOf(argument).also { workSet.addFirst(newCallee) }
          else -> existingCallers + argument
        }
      dependencies += newCallee to callers
    }

    // Update this context's result, and invalidate any affected contexts
    if (resultIsNew) {
      known += argument to result
      for (ctxCaller in dependencies[argument] ?: setOf()) workSet.addLast(ctxCaller)
    }

    // If we were visiting multiple contexts in parallel, here we'd also need to explicitly
    // propagate updated results along newly discovered dependencies. But at the moment we're
    // visiting one context at a time, so that would be redundant.
  }

  return known
}

fun <K : Any, V> DependentMonotone<K, V>.leastFixPoint(vararg points: K): Memo<K, V> =
  leastFixPoint(points.asList())

/** Accumulate new results and dependencies */
private fun <K, V> DependentMonotone<K, V>.step(
  knownResults: Memo<K, V>,
  knownDeps: Map<K, Set<K>>,
  point: K,
): StepResult<K, V> {
  val lattice = latticeAt(point)
  // Check for new, un-subsumed result
  val recur = LoggedFunction(knownResults) { latticeAt(it).bottom }
  val existingResult = if (point in knownResults) knownResults[point] as V else lattice.bottom
  val widenedResult = lattice.widen(existingResult, invoke(recur, point))
  val resultIsNew = !(lattice.precede(widenedResult, existingResult))
  // Accumulate new dependencies
  val newDeps = recur.log.filter { point !in (knownDeps[it] ?: persistentSetOf()) }
  return StepResult(widenedResult, resultIsNew, newDeps)
}

/** Unfortunately we need to allow `null` in [V], so use an extra flag [resultIsNew] */
private data class StepResult<K, V>(val result: V, val resultIsNew: Boolean, val newDeps: List<K>)

/**
 * Given a finite map [K] to [V], return a total function on [K] with a [default] result, also with
 * a side effect logging the elements [K]s applied to.
 */
private class LoggedFunction<K, V>(private val memo: Map<K, V>, private val default: (K) -> V) :
  (K) -> V {
  val log = mutableSetOf<K>()

  override fun invoke(x: K): V {
    log += x
    return memo[x] ?: default(x)
  }
}

/** Like a `Deque`, but only storing each element once */
private class UniqueDeque<T>(elements: Collection<T>) {
  private val elements = HashSet(elements)
  private val deque = ArrayDeque(elements)

  fun isEmpty(): Boolean = deque.isEmpty()

  fun addFirst(element: T) = addBy(Deque<T>::addFirst, element)

  fun addLast(element: T) = addBy(Deque<T>::addLast, element)

  fun removeNext(): T = removeBy(Deque<T>::removeFirst)

  private fun addBy(add: Deque<T>.(T) -> Any?, element: T) {
    if (element !in elements) {
      elements += element
      add(deque, element)
    }
  }

  private fun removeBy(rem: Deque<T>.() -> T): T = rem(deque).also(elements::remove)
}
