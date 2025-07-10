/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.lint.checks.fx.utils.Lattice.Companion.dual
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.intersect
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.plus

/**
 * An [UnboundedSet] is either a finite [PersistentSet], or `null`, representing *all* elements.
 * This means that if [T] is finite, then `null` is a *redundant* `⊤` distinct from the finite set
 * of all elements of [T], and [UnboundedSet] won't be an appropriate representation.
 */
typealias UnboundedSet<T> = PersistentSet<T>?

fun <X, Y> UnboundedSet<X>.map(f: (X) -> Y): UnboundedSet<Y> = this?.map(f)

fun <T> unboundedSetOf(vararg elements: T): UnboundedSet<T> = persistentSetOf(*elements)

fun <T> unboundedSetOfAll(): UnboundedSet<T> = null

infix fun <T> UnboundedSet<T>.isSubsetOf(that: UnboundedSet<T>) =
  when {
    that == null -> true
    this == null -> false
    else -> that.containsAll(this)
  }

infix fun <T> UnboundedSet<T>.intersectedWith(that: UnboundedSet<T>): UnboundedSet<T> =
  when {
    this != null && that != null -> this intersect that
    else -> this ?: that
  }

infix fun <T> UnboundedSet<T>.unionedWith(that: UnboundedSet<T>): UnboundedSet<T> =
  when {
    this != null && that != null -> this + that
    else -> null
  }

private val possibilityLattice =
  object : Lattice<UnboundedSet<Nothing>> {
    override val bottom = persistentSetOf<Nothing>()
    override val top = null

    override fun meetOf(first: UnboundedSet<Nothing>, second: UnboundedSet<Nothing>) =
      first intersectedWith second

    override fun joinOf(first: UnboundedSet<Nothing>, second: UnboundedSet<Nothing>) =
      first unionedWith second

    override fun precede(first: UnboundedSet<Nothing>, second: UnboundedSet<Nothing>) =
      first isSubsetOf second
  }

private val constraintLattice = possibilityLattice.dual()

fun <T> possibilityLattice(): Lattice<UnboundedSet<T>> =
  possibilityLattice as Lattice<UnboundedSet<T>>

fun <T> constraintLattice(): Lattice<UnboundedSet<T>> =
  constraintLattice as Lattice<UnboundedSet<T>>
