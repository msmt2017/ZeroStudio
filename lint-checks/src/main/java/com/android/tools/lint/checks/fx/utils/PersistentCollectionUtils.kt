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

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.plus

inline fun <S, reified T : S> Collection<S>.partitionIsInstanceOf():
  Pair<PersistentSet<T>, PersistentSet<S>> {
  var ts = persistentSetOf<T>()
  var fs = persistentSetOf<S>()
  for (s in this) if (s is T) ts += s else fs += s
  return ts to fs
}

fun <T> Collection<T>.partitionToPersistentSets(
  sat: (T) -> Boolean
): Pair<PersistentSet<T>, PersistentSet<T>> {
  var ts = persistentSetOf<T>()
  var fs = persistentSetOf<T>()
  for (s in this) if (sat(s)) ts += s else fs += s
  return ts to fs
}

fun <X, Y> Collection<X>.flatMapToPersistentSet(f: (X) -> Collection<Y>): PersistentSet<Y> =
  fold(persistentSetOf()) { acc, x -> f(x).fold(acc, PersistentSet<Y>::add) }

internal fun <X, Y> PersistentSet<X>.map(f: (X) -> Y): PersistentSet<Y> =
  fold(persistentSetOf()) { ys, x -> ys + f(x) }

fun <T, K, V> Collection<T>.assoc(
  init: PersistentMap<K, V> = persistentMapOf<K, V>(),
  transform: (T) -> Pair<K, V>,
): PersistentMap<K, V> = fold(init) { m, t -> m + transform(t) }

fun <K, V, W> PersistentMap<K, V>.mapValues(f: (K, V) -> W): PersistentMap<K, W> =
  asSequence().fold(persistentMapOf()) { m, (k, v) -> m.put(k, f(k, v)) }
