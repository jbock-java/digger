/*
 * Copyright (C) 2013 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen.extension;

import static java.util.stream.Collectors.collectingAndThen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Utilities for streams. */
public final class DaggerStreams {

  /**
   * Returns a {@link Collector} that accumulates the input elements into a new {@link
   * ImmutableList}, in encounter order.
   */
  // TODO(b/68008628): Use ImmutableList.toImmutableList().
  public static <T> Collector<T, ?, List<T>> toImmutableList() {
    return Collectors.toList();
  }

  /**
   * Returns a {@link Collector} that accumulates the input elements into a new {@link
   * Set}, in encounter order.
   */
  // TODO(b/68008628): Use ImmutableSet.toImmutableSet().
  public static <T> Collector<T, ?, Set<T>> toImmutableSet() {
    return Collectors.toCollection(LinkedHashSet::new);
  }

  /**
   * Returns a {@link Collector} that accumulates elements into an {@code ImmutableMap} whose keys
   * and values are the result of applying the provided mapping functions to the input elements.
   * Entries appear in the result {@code ImmutableMap} in encounter order.
   */
  // TODO(b/68008628): Use ImmutableMap.toImmutableMap().
  public static <T, K, V> Collector<T, ?, ImmutableMap<K, V>> toImmutableMap(
      Function<? super T, K> keyMapper, Function<? super T, V> valueMapper) {
    return Collectors.mapping(
        value -> Maps.immutableEntry(keyMapper.apply(value), valueMapper.apply(value)),
        Collector.of(
            ImmutableMap::builder,
            (ImmutableMap.Builder<K, V> builder, Map.Entry<K, V> entry) -> builder.put(entry),
            (left, right) -> left.putAll(right.build()),
            ImmutableMap.Builder::build));
  }

  /**
   * Returns a {@link Collector} that accumulates elements into an {@code ImmutableSetMultimap}
   * whose keys and values are the result of applying the provided mapping functions to the input
   * elements. Entries appear in the result {@code ImmutableSetMultimap} in encounter order.
   */
  // TODO(b/68008628): Use ImmutableSetMultimap.toImmutableSetMultimap().
  public static <T, K, V> Collector<T, ?, ImmutableSetMultimap<K, V>> toImmutableSetMultimap(
      Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends V> valueMapper) {
    return collectingAndThen(_toImmutableSetMultimap(keyMapper, valueMapper),
        map -> {
          ImmutableSetMultimap.Builder<K, V> builder = ImmutableSetMultimap.builder();
          map.forEach(builder::putAll);
          return builder.build();
        });
  }

  public static <T, K, V> Collector<T, ?, Map<K, Set<V>>> _toImmutableSetMultimap(
      Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends V> valueMapper) {
    return Collectors.mapping(
        value -> {
          Set<V> values = new LinkedHashSet<>();
          values.add(valueMapper.apply(value));
          return new SimpleImmutableEntry<>(keyMapper.apply(value), values);
        },
        Collector.of(
            LinkedHashMap::new,
            (map, entry) ->
                map.merge(entry.getKey(), entry.getValue(), (set1, set2) -> {
                  set1.addAll(set2);
                  return set1;
                }),
            (left, right) -> {
              right.forEach((k, v) ->
                  left.merge(k, v, (set1, set2) -> {
                    set1.addAll(set2);
                    return set1;
                  }));
              return left;
            }));
  }


  /**
   * Returns a function from {@link Object} to {@code Stream<T>}, which returns a stream containing
   * its input if its input is an instance of {@code T}.
   *
   * <p>Use as an argument to {@link Stream#flatMap(Function)}:
   *
   * <pre>{@code Stream<Bar>} barStream = fooStream.flatMap(instancesOf(Bar.class));</pre>
   */
  public static <T> Function<Object, Stream<T>> instancesOf(Class<T> to) {
    return f -> to.isInstance(f) ? Stream.of(to.cast(f)) : Stream.empty();
  }

  /** Returns a stream of all values of the given {@code enumType}. */
  public static <E extends Enum<E>> Stream<E> valuesOf(Class<E> enumType) {
    return EnumSet.allOf(enumType).stream();
  }

  /**
   * Returns a sequential {@link Stream} of the contents of {@code iterable}, delegating to {@link
   * Collection#stream} if possible.
   */
  public static <T> Stream<T> stream(Iterable<T> iterable) {
    return (iterable instanceof Collection)
        ? ((Collection<T>) iterable).stream()
        : StreamSupport.stream(iterable.spliterator(), false);
  }

  private DaggerStreams() {
  }
}
