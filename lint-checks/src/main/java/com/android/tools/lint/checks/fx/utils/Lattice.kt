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

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus

/**
 * An instance of [Lattice] witnesses that [L] is a partial order by [precede], with a "least"
 * element [bottom], "greatest" element [top], least common upperbound [joinOf], and greatest common
 * lower bound [meetOf].
 */
interface Lattice<L> {
  val bottom: L
  val top: L

  fun precede(first: L, second: L): Boolean

  fun joinOf(first: L, second: L): L

  fun meetOf(first: L, second: L): L

  /**
   * While not part of the standard [Lattice] definition, most uses for static analysis need
   * [widen], so we include it here instead of a sub-interface. We require `widen(x, y) ⊒ x ⊔ y`,
   * and give [widen] a default definition as just alias for [join].
   */
  fun widen(prev: L, now: L): L = prev join now

  // Syntactic sugar
  infix fun L.precedes(that: L) = precede(this, that)

  infix fun L.join(that: L) = joinOf(this, that)

  infix fun L.meet(that: L) = meetOf(this, that)

  fun <X> Iterable<X>.joinedOver(f: (X) -> L): L = joinedOver(this@Lattice, f)

  fun <X> Sequence<X>.joinedOver(f: (X) -> L): L = joinedOver(this@Lattice, f)

  companion object {
    private class Dual<L>(val base: Lattice<L>) : Lattice<L> {
      override val bottom = base.top
      override val top = base.bottom

      override fun precede(first: L, second: L) = base.precede(second, first)

      override fun joinOf(first: L, second: L) = base.meetOf(first, second)

      override fun meetOf(first: L, second: L) = base.joinOf(first, second)
    }

    fun <L> Lattice<L>.dual(): Lattice<L> =
      when (this) {
        is Dual -> this.base
        else -> Dual(this)
      }

    fun <Y, X> lift(inj: (X) -> Y, proj: (Y) -> X, base: Lattice<X>): Lattice<Y> =
      object : Lattice<Y> {
        override val bottom = inj(base.bottom)
        override val top = inj(base.top)

        override fun meetOf(first: Y, second: Y) = inj(base.meetOf(proj(first), proj(second)))

        override fun joinOf(first: Y, second: Y) = inj(base.joinOf(proj(first), proj(second)))

        override fun precede(first: Y, second: Y) = base.precede(proj(first), proj(second))
      }

    fun <T, T1, T2> product(
      inj: (T1, T2) -> T,
      p1: (T) -> T1,
      p2: (T) -> T2,
      onT1: Lattice<T1>,
      onT2: Lattice<T2>,
    ): Lattice<T> =
      object : Lattice<T> {
        override val bottom = inj(onT1.bottom, onT2.bottom)
        override val top = inj(onT1.top, onT2.top)

        override fun meetOf(first: T, second: T) =
          inj(onT1.meetOf(p1(first), p1(second)), onT2.meetOf(p2(first), p2(second)))

        override fun joinOf(first: T, second: T) =
          inj(onT1.joinOf(p1(first), p1(second)), onT2.joinOf(p2(first), p2(second)))

        override fun widen(prev: T, now: T) =
          inj(onT1.widen(p1(prev), p1(now)), onT2.widen(p2(prev), p2(now)))

        override fun precede(first: T, second: T) =
          onT1.precede(p1(first), p1(second)) && onT2.precede(p2(first), p2(second))
      }

    fun <T, T1, T2, T3> product(
      inj: (T1, T2, T3) -> T,
      p1: (T) -> T1,
      p2: (T) -> T2,
      p3: (T) -> T3,
      onT1: Lattice<T1>,
      onT2: Lattice<T2>,
      onT3: Lattice<T3>,
    ): Lattice<T> =
      object : Lattice<T> {
        override val bottom = inj(onT1.bottom, onT2.bottom, onT3.bottom)
        override val top = inj(onT1.top, onT2.top, onT3.top)

        override fun meetOf(first: T, second: T): T =
          inj(
            onT1.meetOf(p1(first), p1(second)),
            onT2.meetOf(p2(first), p2(second)),
            onT3.meetOf(p3(first), p3(second)),
          )

        override fun joinOf(first: T, second: T): T =
          inj(
            onT1.joinOf(p1(first), p1(second)),
            onT2.joinOf(p2(first), p2(second)),
            onT3.joinOf(p3(first), p3(second)),
          )

        override fun widen(prev: T, now: T) =
          inj(
            onT1.widen(p1(prev), p1(now)),
            onT2.widen(p2(prev), p2(now)),
            onT3.widen(p3(prev), p3(now)),
          )

        override fun precede(first: T, second: T) =
          onT1.precede(p1(first), p1(second)) &&
            onT2.precede(p2(first), p2(second)) &&
            onT3.precede(p3(first), p3(second))
      }

    fun <T, T1, T2, T3, T4> product(
      inj: (T1, T2, T3, T4) -> T,
      p1: (T) -> T1,
      p2: (T) -> T2,
      p3: (T) -> T3,
      p4: (T) -> T4,
      onT1: Lattice<T1>,
      onT2: Lattice<T2>,
      onT3: Lattice<T3>,
      onT4: Lattice<T4>,
    ): Lattice<T> =
      object : Lattice<T> {
        override val bottom = inj(onT1.bottom, onT2.bottom, onT3.bottom, onT4.bottom)
        override val top = inj(onT1.top, onT2.top, onT3.top, onT4.top)

        override fun meetOf(first: T, second: T): T =
          inj(
            onT1.meetOf(p1(first), p1(second)),
            onT2.meetOf(p2(first), p2(second)),
            onT3.meetOf(p3(first), p3(second)),
            onT4.meetOf(p4(first), p4(second)),
          )

        override fun joinOf(first: T, second: T): T =
          inj(
            onT1.joinOf(p1(first), p1(second)),
            onT2.joinOf(p2(first), p2(second)),
            onT3.joinOf(p3(first), p3(second)),
            onT4.joinOf(p4(first), p4(second)),
          )

        override fun widen(prev: T, now: T) =
          inj(
            onT1.widen(p1(prev), p1(now)),
            onT2.widen(p2(prev), p2(now)),
            onT3.widen(p3(prev), p3(now)),
            onT4.widen(p4(prev), p4(now)),
          )

        override fun precede(first: T, second: T) =
          onT1.precede(p1(first), p1(second)) &&
            onT2.precede(p2(first), p2(second)) &&
            onT3.precede(p3(first), p3(second)) &&
            onT4.precede(p4(first), p4(second))
      }

    /**
     * Given a lattice [onValue] on [V], produce a lattice on maps from [K] to [V] where:
     * - The absence of an entry means a mapping from it to `⊥`. (In particular, the empty map is
     *   `⊥`.)
     * - `null` represents `⊤`, i.e. the map of every entry to `⊤`.
     */
    fun <K, V> pointWise(onValue: Lattice<V>): Lattice<PersistentMap<K, V>?> =
      object : Lattice<PersistentMap<K, V>?> {
        override val bottom = persistentMapOf<K, V>()
        override val top = null

        override fun meetOf(first: PersistentMap<K, V>?, second: PersistentMap<K, V>?) =
          when {
            first != null && second != null -> {
              fun meet(m1: PersistentMap<K, V>, m2: PersistentMap<K, V>): PersistentMap<K, V> =
                m1.asSequence().fold(persistentMapOf()) { m, (k, v1) ->
                  // NB: We unfortunately allow `null` in `V`,
                  // so here distinguish between `k !in m2` and `m2: k ↦ null`
                  if (k !in m2) return@fold m
                  val v = onValue.meetOf(v1, m2[k] as V)
                  if (v == onValue.bottom) m else m + (k to v)
                }
              when {
                first.size < second.size -> meet(first, second)
                else -> meet(second, first)
              }
            }
            else -> first ?: second
          }

        override fun joinOf(first: PersistentMap<K, V>?, second: PersistentMap<K, V>?) =
          when {
            first != null && second != null -> {
              fun join(m1: PersistentMap<K, V>, m2: PersistentMap<K, V>): PersistentMap<K, V> =
                m1.asSequence().fold(m2) { m, (k, v1) ->
                  // NB: We unfortunately allow `null` in `V`,
                  // so here distinguish between `k !in m2` and `m2: k ↦ null`
                  val v = if (k !in m2) v1 else onValue.joinOf(v1, m2[k] as V)
                  m + (k to v)
                }
              when {
                first.size < second.size -> join(first, second)
                else -> join(second, first)
              }
            }
            else -> null
          }

        override fun precede(first: PersistentMap<K, V>?, second: PersistentMap<K, V>?) =
          when {
            first != null && second != null ->
              first.all { (k, v1) ->
                // NB: We unfortunately allow `null` in `V`,
                // so here distinguish between `k !in m2` and `m2: k ↦ null`
                if (k !in second) return@all false
                onValue.precede(v1, second[k] as V)
              }
            else -> second == null
          }
      }
  }
}

fun <X, L> Sequence<X>.joinedOver(lattice: Lattice<L>, f: (X) -> L): L =
  map(f).fold(lattice.bottom, lattice::joinOf)

fun <X, L> Iterable<X>.joinedOver(lattice: Lattice<L>, f: (X) -> L): L =
  asSequence().joinedOver(lattice, f)
