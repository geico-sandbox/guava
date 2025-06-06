/*
 * Copyright (C) 2008 The Guava Authors
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

package com.google.common.collect;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.CollectPreconditions.checkEntryNotNull;
import static com.google.common.collect.CollectPreconditions.checkNonnegative;
import static java.lang.System.arraycopy;
import static java.util.Arrays.sort;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.J2ktIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.DoNotCall;
import com.google.errorprone.annotations.DoNotMock;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.j2objc.annotations.RetainedWith;
import com.google.j2objc.annotations.WeakOuter;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * A {@link Map} whose contents will never change, with many other important properties detailed at
 * {@link ImmutableCollection}.
 *
 * <p>See the Guava User Guide article on <a href=
 * "https://github.com/google/guava/wiki/ImmutableCollectionsExplained">immutable collections</a>.
 *
 * @author Jesse Wilson
 * @author Kevin Bourrillion
 * @since 2.0
 */
@DoNotMock("Use ImmutableMap.of or another implementation")
@GwtCompatible(emulated = true)
@SuppressWarnings("serial") // we're overriding default serialization
public abstract class ImmutableMap<K, V> implements Map<K, V>, Serializable {

  /**
   * Returns a {@link Collector} that accumulates elements into an {@code ImmutableMap} whose keys
   * and values are the result of applying the provided mapping functions to the input elements.
   * Entries appear in the result {@code ImmutableMap} in encounter order.
   *
   * <p>If the mapped keys contain duplicates (according to {@link Object#equals(Object)}, an {@code
   * IllegalArgumentException} is thrown when the collection operation is performed. (This differs
   * from the {@code Collector} returned by {@link Collectors#toMap(Function, Function)}, which
   * throws an {@code IllegalStateException}.)
   *
   * @since 21.0
   */
  public static <T extends @Nullable Object, K, V>
      Collector<T, ?, ImmutableMap<K, V>> toImmutableMap(
          Function<? super T, ? extends K> keyFunction,
          Function<? super T, ? extends V> valueFunction) {
    return CollectCollectors.toImmutableMap(keyFunction, valueFunction);
  }

  /**
   * Returns a {@link Collector} that accumulates elements into an {@code ImmutableMap} whose keys
   * and values are the result of applying the provided mapping functions to the input elements.
   *
   * <p>If the mapped keys contain duplicates (according to {@link Object#equals(Object)}), the
   * values are merged using the specified merging function. If the merging function returns {@code
   * null}, then the collector removes the value that has been computed for the key thus far (though
   * future occurrences of the key would reinsert it).
   *
   * <p>Entries will appear in the encounter order of the first occurrence of the key.
   *
   * @since 21.0
   */
  public static <T extends @Nullable Object, K, V>
      Collector<T, ?, ImmutableMap<K, V>> toImmutableMap(
          Function<? super T, ? extends K> keyFunction,
          Function<? super T, ? extends V> valueFunction,
          BinaryOperator<V> mergeFunction) {
    return CollectCollectors.toImmutableMap(keyFunction, valueFunction, mergeFunction);
  }

  /**
   * Returns the empty map. This map behaves and performs comparably to {@link
   * Collections#emptyMap}, and is preferable mainly for consistency and maintainability of your
   * code.
   *
   * <p><b>Performance note:</b> the instance returned is a singleton.
   */
  @SuppressWarnings("unchecked")
  public static <K, V> ImmutableMap<K, V> of() {
    return (ImmutableMap<K, V>) RegularImmutableMap.EMPTY;
  }

  /**
   * Returns an immutable map containing a single entry. This map behaves and performs comparably to
   * {@link Collections#singletonMap} but will not accept a null key or value. It is preferable
   * mainly for consistency and maintainability of your code.
   */
  public static <K, V> ImmutableMap<K, V> of(K k1, V v1) {
    return ImmutableBiMap.of(k1, v1);
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   */
  public static <K, V> ImmutableMap<K, V> of(K k1, V v1, K k2, V v2) {
    return RegularImmutableMap.fromEntries(entryOf(k1, v1), entryOf(k2, v2));
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   */
  public static <K, V> ImmutableMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3) {
    return RegularImmutableMap.fromEntries(entryOf(k1, v1), entryOf(k2, v2), entryOf(k3, v3));
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   */
  public static <K, V> ImmutableMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
    return RegularImmutableMap.fromEntries(
        entryOf(k1, v1), entryOf(k2, v2), entryOf(k3, v3), entryOf(k4, v4));
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   */
  public static <K, V> ImmutableMap<K, V> of(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
    return RegularImmutableMap.fromEntries(
        entryOf(k1, v1), entryOf(k2, v2), entryOf(k3, v3), entryOf(k4, v4), entryOf(k5, v5));
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   * @since 31.0
   */
  public static <K, V> ImmutableMap<K, V> of(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) {
    return RegularImmutableMap.fromEntries(
        entryOf(k1, v1),
        entryOf(k2, v2),
        entryOf(k3, v3),
        entryOf(k4, v4),
        entryOf(k5, v5),
        entryOf(k6, v6));
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   * @since 31.0
   */
  public static <K, V> ImmutableMap<K, V> of(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7) {
    return RegularImmutableMap.fromEntries(
        entryOf(k1, v1),
        entryOf(k2, v2),
        entryOf(k3, v3),
        entryOf(k4, v4),
        entryOf(k5, v5),
        entryOf(k6, v6),
        entryOf(k7, v7));
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   * @since 31.0
   */
  public static <K, V> ImmutableMap<K, V> of(
      K k1,
      V v1,
      K k2,
      V v2,
      K k3,
      V v3,
      K k4,
      V v4,
      K k5,
      V v5,
      K k6,
      V v6,
      K k7,
      V v7,
      K k8,
      V v8) {
    return RegularImmutableMap.fromEntries(
        entryOf(k1, v1),
        entryOf(k2, v2),
        entryOf(k3, v3),
        entryOf(k4, v4),
        entryOf(k5, v5),
        entryOf(k6, v6),
        entryOf(k7, v7),
        entryOf(k8, v8));
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   * @since 31.0
   */
  public static <K, V> ImmutableMap<K, V> of(
      K k1,
      V v1,
      K k2,
      V v2,
      K k3,
      V v3,
      K k4,
      V v4,
      K k5,
      V v5,
      K k6,
      V v6,
      K k7,
      V v7,
      K k8,
      V v8,
      K k9,
      V v9) {
    return RegularImmutableMap.fromEntries(
        entryOf(k1, v1),
        entryOf(k2, v2),
        entryOf(k3, v3),
        entryOf(k4, v4),
        entryOf(k5, v5),
        entryOf(k6, v6),
        entryOf(k7, v7),
        entryOf(k8, v8),
        entryOf(k9, v9));
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   * @since 31.0
   */
  public static <K, V> ImmutableMap<K, V> of(
      K k1,
      V v1,
      K k2,
      V v2,
      K k3,
      V v3,
      K k4,
      V v4,
      K k5,
      V v5,
      K k6,
      V v6,
      K k7,
      V v7,
      K k8,
      V v8,
      K k9,
      V v9,
      K k10,
      V v10) {
    return RegularImmutableMap.fromEntries(
        entryOf(k1, v1),
        entryOf(k2, v2),
        entryOf(k3, v3),
        entryOf(k4, v4),
        entryOf(k5, v5),
        entryOf(k6, v6),
        entryOf(k7, v7),
        entryOf(k8, v8),
        entryOf(k9, v9),
        entryOf(k10, v10));
  }

  // looking for of() with > 10 entries? Use the builder or ofEntries instead.

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   * @since 31.0
   */
  @SafeVarargs
  public static <K, V> ImmutableMap<K, V> ofEntries(Entry<? extends K, ? extends V>... entries) {
    @SuppressWarnings("unchecked") // we will only ever read these
    Entry<K, V>[] entries2 = (Entry<K, V>[]) entries;
    return RegularImmutableMap.fromEntries(entries2);
  }

  /**
   * Verifies that {@code key} and {@code value} are non-null, and returns a new immutable entry
   * with those values.
   *
   * <p>A call to {@link Entry#setValue} on the returned entry will always throw {@link
   * UnsupportedOperationException}.
   */
  static <K, V> Entry<K, V> entryOf(K key, V value) {
    return new ImmutableMapEntry<>(key, value);
  }

  /**
   * Returns a new builder. The generated builder is equivalent to the builder created by the {@link
   * Builder} constructor.
   */
  public static <K, V> Builder<K, V> builder() {
    return new Builder<>();
  }

  /**
   * Returns a new builder, expecting the specified number of entries to be added.
   *
   * <p>If {@code expectedSize} is exactly the number of entries added to the builder before {@link
   * Builder#build} is called, the builder is likely to perform better than an unsized {@link
   * #builder()} would have.
   *
   * <p>It is not specified if any performance benefits apply if {@code expectedSize} is close to,
   * but not exactly, the number of entries added to the builder.
   *
   * @since 23.1
   */
  public static <K, V> Builder<K, V> builderWithExpectedSize(int expectedSize) {
    checkNonnegative(expectedSize, "expectedSize");
    return new Builder<>(expectedSize);
  }

  static void checkNoConflict(
      boolean safe, String conflictDescription, Object entry1, Object entry2) {
    if (!safe) {
      throw conflictException(conflictDescription, entry1, entry2);
    }
  }

  static IllegalArgumentException conflictException(
      String conflictDescription, Object entry1, Object entry2) {
    return new IllegalArgumentException(
        "Multiple entries with same " + conflictDescription + ": " + entry1 + " and " + entry2);
  }

  /**
   * A builder for creating immutable map instances, especially {@code public static final} maps
   * ("constant maps"). Example:
   *
   * {@snippet :
   * static final ImmutableMap<String, Integer> WORD_TO_INT =
   *     new ImmutableMap.Builder<String, Integer>()
   *         .put("one", 1)
   *         .put("two", 2)
   *         .put("three", 3)
   *         .buildOrThrow();
   * }
   *
   * <p>For <i>small</i> immutable maps, the {@code ImmutableMap.of()} methods are even more
   * convenient.
   *
   * <p>By default, a {@code Builder} will generate maps that iterate over entries in the order they
   * were inserted into the builder, equivalently to {@code LinkedHashMap}. For example, in the
   * above example, {@code WORD_TO_INT.entrySet()} is guaranteed to iterate over the entries in the
   * order {@code "one"=1, "two"=2, "three"=3}, and {@code keySet()} and {@code values()} respect
   * the same order. If you want a different order, consider using {@link ImmutableSortedMap} to
   * sort by keys, or call {@link #orderEntriesByValue(Comparator)}, which changes this builder to
   * sort entries by value.
   *
   * <p>Builder instances can be reused - it is safe to call {@link #buildOrThrow} multiple times to
   * build multiple maps in series. Each map is a superset of the maps created before it.
   *
   * @since 2.0
   */
  @DoNotMock
  public static class Builder<K, V> {
    @Nullable Comparator<? super V> valueComparator;
    @Nullable Entry<K, V>[] entries;
    int size;
    boolean entriesUsed;

    /**
     * Creates a new builder. The returned builder is equivalent to the builder generated by {@link
     * ImmutableMap#builder}.
     */
    public Builder() {
      this(ImmutableCollection.Builder.DEFAULT_INITIAL_CAPACITY);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Builder(int initialCapacity) {
      this.entries = new @Nullable Entry[initialCapacity];
      this.size = 0;
      this.entriesUsed = false;
    }

    private void ensureCapacity(int minCapacity) {
      if (minCapacity > entries.length) {
        entries =
            Arrays.copyOf(
                entries, ImmutableCollection.Builder.expandedCapacity(entries.length, minCapacity));
        entriesUsed = false;
      }
    }

    /**
     * Associates {@code key} with {@code value} in the built map. If the same key is put more than
     * once, {@link #buildOrThrow} will fail, while {@link #buildKeepingLast} will keep the last
     * value put for that key.
     */
    @CanIgnoreReturnValue
    public Builder<K, V> put(K key, V value) {
      ensureCapacity(size + 1);
      Entry<K, V> entry = entryOf(key, value);
      // don't inline this: we want to fail atomically if key or value is null
      entries[size++] = entry;
      return this;
    }

    /**
     * Adds the given {@code entry} to the map, making it immutable if necessary. If the same key is
     * put more than once, {@link #buildOrThrow} will fail, while {@link #buildKeepingLast} will
     * keep the last value put for that key.
     *
     * @since 11.0
     */
    @CanIgnoreReturnValue
    public Builder<K, V> put(Entry<? extends K, ? extends V> entry) {
      return put(entry.getKey(), entry.getValue());
    }

    /**
     * Associates all of the given map's keys and values in the built map. If the same key is put
     * more than once, {@link #buildOrThrow} will fail, while {@link #buildKeepingLast} will keep
     * the last value put for that key.
     *
     * @throws NullPointerException if any key or value in {@code map} is null
     */
    @CanIgnoreReturnValue
    public Builder<K, V> putAll(Map<? extends K, ? extends V> map) {
      return putAll(map.entrySet());
    }

    /**
     * Adds all of the given entries to the built map. If the same key is put more than once, {@link
     * #buildOrThrow} will fail, while {@link #buildKeepingLast} will keep the last value put for
     * that key.
     *
     * @throws NullPointerException if any key, value, or entry is null
     * @since 19.0
     */
    @CanIgnoreReturnValue
    public Builder<K, V> putAll(Iterable<? extends Entry<? extends K, ? extends V>> entries) {
      if (entries instanceof Collection) {
        ensureCapacity(size + ((Collection<?>) entries).size());
      }
      for (Entry<? extends K, ? extends V> entry : entries) {
        put(entry);
      }
      return this;
    }

    /**
     * Configures this {@code Builder} to order entries by value according to the specified
     * comparator.
     *
     * <p>The sort order is stable, that is, if two entries have values that compare as equivalent,
     * the entry that was inserted first will be first in the built map's iteration order.
     *
     * @throws IllegalStateException if this method was already called
     * @since 19.0
     */
    @CanIgnoreReturnValue
    public Builder<K, V> orderEntriesByValue(Comparator<? super V> valueComparator) {
      checkState(this.valueComparator == null, "valueComparator was already set");
      this.valueComparator = checkNotNull(valueComparator, "valueComparator");
      return this;
    }

    @CanIgnoreReturnValue
    Builder<K, V> combine(Builder<K, V> other) {
      checkNotNull(other);
      ensureCapacity(this.size + other.size);
      arraycopy(other.entries, 0, this.entries, this.size, other.size);
      this.size += other.size;
      return this;
    }

    private ImmutableMap<K, V> build(boolean throwIfDuplicateKeys) {
      /*
       * If entries is full, or if hash flooding is detected, then this implementation may end up
       * using the entries array directly and writing over the entry objects with non-terminal
       * entries, but this is safe; if this Builder is used further, it will grow the entries array
       * (so it can't affect the original array), and future build() calls will always copy any
       * entry objects that cannot be safely reused.
       */
      switch (size) {
        case 0:
          return of();
        case 1:
          // requireNonNull is safe because the first `size` elements have been filled in.
          Entry<K, V> onlyEntry = requireNonNull(entries[0]);
          return of(onlyEntry.getKey(), onlyEntry.getValue());
        default:
          break;
      }
      // localEntries is an alias for the entries field, except if we end up removing duplicates in
      // a copy of the entries array. Likewise, localSize is the same as size except in that case.
      // It's possible to keep using this Builder after calling buildKeepingLast(), so we need to
      // ensure that its state is not corrupted by removing duplicates that should cause a later
      // buildOrThrow() to fail, or by changing the size.
      @Nullable Entry<K, V>[] localEntries;
      int localSize = size;
      if (valueComparator == null) {
        localEntries = entries;
      } else {
        if (entriesUsed) {
          entries = Arrays.copyOf(entries, size);
        }
        @SuppressWarnings("nullness") // entries 0..localSize-1 are non-null
        Entry<K, V>[] nonNullEntries = (Entry<K, V>[]) entries;
        if (!throwIfDuplicateKeys) {
          // We want to retain only the last-put value for any given key, before sorting.
          // This could be improved, but orderEntriesByValue is rather rarely used anyway.
          Entry<K, V>[] lastEntryForEachKey = lastEntryForEachKey(nonNullEntries, size);
          if (lastEntryForEachKey != null) {
            nonNullEntries = lastEntryForEachKey;
            localSize = lastEntryForEachKey.length;
          }
        }
        sort(
            nonNullEntries,
            0,
            localSize,
            Ordering.from(valueComparator).onResultOf(Maps.<V>valueFunction()));
        localEntries = (@Nullable Entry<K, V>[]) nonNullEntries;
      }
      entriesUsed = true;
      return RegularImmutableMap.fromEntryArray(localSize, localEntries, throwIfDuplicateKeys);
    }

    /**
     * Returns a newly-created immutable map. The iteration order of the returned map is the order
     * in which entries were inserted into the builder, unless {@link #orderEntriesByValue} was
     * called, in which case entries are sorted by value.
     *
     * <p>Prefer the equivalent method {@link #buildOrThrow()} to make it explicit that the method
     * will throw an exception if there are duplicate keys. The {@code build()} method will soon be
     * deprecated.
     *
     * @throws IllegalArgumentException if duplicate keys were added
     */
    public ImmutableMap<K, V> build() {
      return buildOrThrow();
    }

    /**
     * Returns a newly-created immutable map, or throws an exception if any key was added more than
     * once. The iteration order of the returned map is the order in which entries were inserted
     * into the builder, unless {@link #orderEntriesByValue} was called, in which case entries are
     * sorted by value.
     *
     * @throws IllegalArgumentException if duplicate keys were added
     * @since 31.0
     */
    public ImmutableMap<K, V> buildOrThrow() {
      return build(true);
    }

    /**
     * Returns a newly-created immutable map, using the last value for any key that was added more
     * than once. The iteration order of the returned map is the order in which entries were
     * inserted into the builder, unless {@link #orderEntriesByValue} was called, in which case
     * entries are sorted by value. If a key was added more than once, it appears in iteration order
     * based on the first time it was added, again unless {@link #orderEntriesByValue} was called.
     *
     * <p>In the current implementation, all values associated with a given key are stored in the
     * {@code Builder} object, even though only one of them will be used in the built map. If there
     * can be many repeated keys, it may be more space-efficient to use a {@link
     * java.util.LinkedHashMap LinkedHashMap} and {@link ImmutableMap#copyOf(Map)} rather than
     * {@code ImmutableMap.Builder}.
     *
     * @since 31.1
     */
    public ImmutableMap<K, V> buildKeepingLast() {
      return build(false);
    }

    @VisibleForTesting // only for testing JDK backed implementation
    ImmutableMap<K, V> buildJdkBacked() {
      checkState(
          valueComparator == null, "buildJdkBacked is only for testing; can't use valueComparator");
      switch (size) {
        case 0:
          return of();
        case 1:
          // requireNonNull is safe because the first `size` elements have been filled in.
          Entry<K, V> onlyEntry = requireNonNull(entries[0]);
          return of(onlyEntry.getKey(), onlyEntry.getValue());
        default:
          entriesUsed = true;
          return JdkBackedImmutableMap.create(size, entries, /* throwIfDuplicateKeys= */ true);
      }
    }

    /**
     * Scans the first {@code size} elements of {@code entries} looking for duplicate keys. If
     * duplicates are found, a new correctly-sized array is returned with the same elements (up to
     * {@code size}), except containing only the last occurrence of each duplicate key. Otherwise
     * {@code null} is returned.
     */
    private static <K, V> Entry<K, V> @Nullable [] lastEntryForEachKey(
        Entry<K, V>[] entries, int size) {
      Set<K> seen = new HashSet<>();
      BitSet dups = new BitSet(); // slots that are overridden by a later duplicate key
      for (int i = size - 1; i >= 0; i--) {
        if (!seen.add(entries[i].getKey())) {
          dups.set(i);
        }
      }
      if (dups.isEmpty()) {
        return null;
      }
      @SuppressWarnings({"rawtypes", "unchecked"})
      Entry<K, V>[] newEntries = new Entry[size - dups.cardinality()];
      for (int inI = 0, outI = 0; inI < size; inI++) {
        if (!dups.get(inI)) {
          newEntries[outI++] = entries[inI];
        }
      }
      return newEntries;
    }
  }

  /**
   * Returns an immutable map containing the same entries as {@code map}. The returned map iterates
   * over entries in the same order as the {@code entrySet} of the original map. If {@code map}
   * somehow contains entries with duplicate keys (for example, if it is a {@code SortedMap} whose
   * comparator is not <i>consistent with equals</i>), the results of this method are undefined.
   *
   * <p>Despite the method name, this method attempts to avoid actually copying the data when it is
   * safe to do so. The exact circumstances under which a copy will or will not be performed are
   * undocumented and subject to change.
   *
   * @throws NullPointerException if any key or value in {@code map} is null
   */
  public static <K, V> ImmutableMap<K, V> copyOf(Map<? extends K, ? extends V> map) {
    if ((map instanceof ImmutableMap) && !(map instanceof SortedMap)) {
      @SuppressWarnings("unchecked") // safe since map is not writable
      ImmutableMap<K, V> kvMap = (ImmutableMap<K, V>) map;
      if (!kvMap.isPartialView()) {
        return kvMap;
      }
    } else if (map instanceof EnumMap) {
      @SuppressWarnings("unchecked") // safe since map is not writable
      ImmutableMap<K, V> kvMap =
          (ImmutableMap<K, V>)
              copyOfEnumMap(
                  (EnumMap<?, ? extends V>) map); // hide K (violates bounds) from J2KT, preserve V.
      return kvMap;
    }
    return copyOf(map.entrySet());
  }

  /**
   * Returns an immutable map containing the specified entries. The returned map iterates over
   * entries in the same order as the original iterable.
   *
   * @throws NullPointerException if any key, value, or entry is null
   * @throws IllegalArgumentException if two entries have the same key
   * @since 19.0
   */
  public static <K, V> ImmutableMap<K, V> copyOf(
      Iterable<? extends Entry<? extends K, ? extends V>> entries) {
    @SuppressWarnings("unchecked") // we'll only be using getKey and getValue, which are covariant
    Entry<K, V>[] entryArray = (Entry<K, V>[]) Iterables.toArray(entries, EMPTY_ENTRY_ARRAY);
    switch (entryArray.length) {
      case 0:
        return of();
      case 1:
        // requireNonNull is safe because the first `size` elements have been filled in.
        Entry<K, V> onlyEntry = requireNonNull(entryArray[0]);
        return of(onlyEntry.getKey(), onlyEntry.getValue());
      default:
        /*
         * The current implementation will end up using entryArray directly, though it will write
         * over the (arbitrary, potentially mutable) Entry objects actually stored in entryArray.
         */
        return RegularImmutableMap.fromEntries(entryArray);
    }
  }

  private static <K extends Enum<K>, V> ImmutableMap<K, ? extends V> copyOfEnumMap(
      EnumMap<?, ? extends V> original) {
    @SuppressWarnings("unchecked") // the best we could do to make copyOf(Map) compile
    EnumMap<K, V> copy = new EnumMap<>((EnumMap<K, ? extends V>) original);
    for (Entry<K, V> entry : copy.entrySet()) {
      checkEntryNotNull(entry.getKey(), entry.getValue());
    }
    return ImmutableEnumMap.asImmutable(copy);
  }

  static final Entry<?, ?>[] EMPTY_ENTRY_ARRAY = new Entry<?, ?>[0];

  abstract static class IteratorBasedImmutableMap<K, V> extends ImmutableMap<K, V> {
    abstract UnmodifiableIterator<Entry<K, V>> entryIterator();

    Spliterator<Entry<K, V>> entrySpliterator() {
      return Spliterators.spliterator(
          entryIterator(),
          size(),
          Spliterator.DISTINCT | Spliterator.NONNULL | Spliterator.IMMUTABLE | Spliterator.ORDERED);
    }

    @Override
    ImmutableSet<K> createKeySet() {
      return new ImmutableMapKeySet<>(this);
    }

    @Override
    ImmutableSet<Entry<K, V>> createEntrySet() {
      class EntrySetImpl extends ImmutableMapEntrySet<K, V> {
        @Override
        ImmutableMap<K, V> map() {
          return IteratorBasedImmutableMap.this;
        }

        @Override
        public UnmodifiableIterator<Entry<K, V>> iterator() {
          return entryIterator();
        }

        // redeclare to help optimizers with b/310253115
        @SuppressWarnings("RedundantOverride")
        @Override
        @J2ktIncompatible
        @GwtIncompatible
                Object writeReplace() {
          return super.writeReplace();
        }
      }
      return new EntrySetImpl();
    }

    @Override
    ImmutableCollection<V> createValues() {
      return new ImmutableMapValues<>(this);
    }

    // redeclare to help optimizers with b/310253115
    @SuppressWarnings("RedundantOverride")
    @Override
    @J2ktIncompatible
    @GwtIncompatible
        Object writeReplace() {
      return super.writeReplace();
    }
  }

  ImmutableMap() {}

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @CanIgnoreReturnValue
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final @Nullable V put(K k, V v) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @CanIgnoreReturnValue
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final @Nullable V putIfAbsent(K key, V value) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final boolean replace(K key, V oldValue, V newValue) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final @Nullable V replace(K key, V value) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final @Nullable V computeIfPresent(
      K key, BiFunction<? super K, ? super V, ? extends @Nullable V> remappingFunction) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final @Nullable V compute(
      K key, BiFunction<? super K, ? super @Nullable V, ? extends @Nullable V> remappingFunction) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final @Nullable V merge(
      K key, V value, BiFunction<? super V, ? super V, ? extends @Nullable V> function) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final void putAll(Map<? extends K, ? extends V> map) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final @Nullable V remove(@Nullable Object o) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final boolean remove(@Nullable Object key, @Nullable Object value) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the map unmodified.
   *
   * @throws UnsupportedOperationException always
   * @deprecated Unsupported operation.
   */
  @Deprecated
  @Override
  @DoNotCall("Always throws UnsupportedOperationException")
  public final void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public boolean containsKey(@Nullable Object key) {
    return get(key) != null;
  }

  @Override
  public boolean containsValue(@Nullable Object value) {
    return values().contains(value);
  }

  // Overriding to mark it Nullable
  @Override
  public abstract @Nullable V get(@Nullable Object key);

  /**
   * @since 21.0 (but only since 23.5 in the Android <a
   *     href="https://github.com/google/guava#guava-google-core-libraries-for-java">flavor</a>).
   *     Note, however, that Java 8+ users can call this method with any version and flavor of
   *     Guava.
   */
  @Override
  public final @Nullable V getOrDefault(@Nullable Object key, @Nullable V defaultValue) {
    /*
     * Even though it's weird to pass a defaultValue that is null, some callers do so. Those who
     * pass a literal "null" should probably just use `get`, but I would expect other callers to
     * pass an expression that *might* be null. This could happen with:
     *
     * - a `getFooOrDefault(@Nullable Foo defaultValue)` method that returns
     *   `map.getOrDefault(FOO_KEY, defaultValue)`
     *
     * - a call that consults a chain of maps, as in `mapA.getOrDefault(key, mapB.getOrDefault(key,
     *   ...))`
     *
     * So it makes sense for the parameter (and thus the return type) to be @Nullable.
     *
     * Two other points:
     *
     * 1. We'll want to use something like @PolyNull once we can make that work for the various
     * platforms we target.
     *
     * 2. Kotlin's Map type has a getOrDefault method that accepts and returns a "plain V," in
     * contrast to the "V?" type that we're using. As a result, Kotlin sees a conflict between the
     * nullness annotations in ImmutableMap and those in its own Map type. In response, it considers
     * the parameter and return type both to be platform types. As a result, Kotlin permits calls
     * that can lead to NullPointerException. That's unfortunate. But hopefully most Kotlin callers
     * use `get(key) ?: defaultValue` instead of this method, anyway.
     */
    V result = get(key);
    // TODO(b/192579700): Use a ternary once it no longer confuses our nullness checker.
    if (result != null) {
      return result;
    } else {
      return defaultValue;
    }
  }

  @LazyInit @RetainedWith private transient @Nullable ImmutableSet<Entry<K, V>> entrySet;

  /**
   * Returns an immutable set of the mappings in this map. The iteration order is specified by the
   * method used to create this map. Typically, this is insertion order.
   */
  @Override
  public ImmutableSet<Entry<K, V>> entrySet() {
    ImmutableSet<Entry<K, V>> result = entrySet;
    return (result == null) ? entrySet = createEntrySet() : result;
  }

  abstract ImmutableSet<Entry<K, V>> createEntrySet();

  @LazyInit @RetainedWith private transient @Nullable ImmutableSet<K> keySet;

  /**
   * Returns an immutable set of the keys in this map, in the same order that they appear in {@link
   * #entrySet}.
   */
  @Override
  public ImmutableSet<K> keySet() {
    ImmutableSet<K> result = keySet;
    return (result == null) ? keySet = createKeySet() : result;
  }

  /*
   * This could have a good default implementation of return new ImmutableKeySet<K, V>(this),
   * but ProGuard can't figure out how to eliminate that default when RegularImmutableMap
   * overrides it.
   */
  abstract ImmutableSet<K> createKeySet();

  UnmodifiableIterator<K> keyIterator() {
    UnmodifiableIterator<Entry<K, V>> entryIterator = entrySet().iterator();
    return new UnmodifiableIterator<K>() {
      @Override
      public boolean hasNext() {
        return entryIterator.hasNext();
      }

      @Override
      public K next() {
        return entryIterator.next().getKey();
      }
    };
  }

  Spliterator<K> keySpliterator() {
    return CollectSpliterators.map(entrySet().spliterator(), Entry::getKey);
  }

  @LazyInit @RetainedWith private transient @Nullable ImmutableCollection<V> values;

  /**
   * Returns an immutable collection of the values in this map, in the same order that they appear
   * in {@link #entrySet}.
   */
  @Override
  public ImmutableCollection<V> values() {
    ImmutableCollection<V> result = values;
    return (result == null) ? values = createValues() : result;
  }

  /*
   * This could have a good default implementation of {@code return new
   * ImmutableMapValues<K, V>(this)}, but ProGuard can't figure out how to eliminate that default
   * when RegularImmutableMap overrides it.
   */
  abstract ImmutableCollection<V> createValues();

  // cached so that this.multimapView().inverse() only computes inverse once
  @LazyInit private transient @Nullable ImmutableSetMultimap<K, V> multimapView;

  /**
   * Returns a multimap view of the map.
   *
   * @since 14.0
   */
  public ImmutableSetMultimap<K, V> asMultimap() {
    if (isEmpty()) {
      return ImmutableSetMultimap.of();
    }
    ImmutableSetMultimap<K, V> result = multimapView;
    return (result == null)
        ? (multimapView =
            new ImmutableSetMultimap<>(new MapViewOfValuesAsSingletonSets(), size(), null))
        : result;
  }

  @WeakOuter
  private final class MapViewOfValuesAsSingletonSets
      extends IteratorBasedImmutableMap<K, ImmutableSet<V>> {

    @Override
    public int size() {
      return ImmutableMap.this.size();
    }

    @Override
    ImmutableSet<K> createKeySet() {
      return ImmutableMap.this.keySet();
    }

    @Override
    public boolean containsKey(@Nullable Object key) {
      return ImmutableMap.this.containsKey(key);
    }

    @Override
    public @Nullable ImmutableSet<V> get(@Nullable Object key) {
      V outerValue = ImmutableMap.this.get(key);
      return (outerValue == null) ? null : ImmutableSet.of(outerValue);
    }

    @Override
    boolean isPartialView() {
      return ImmutableMap.this.isPartialView();
    }

    @Override
    public int hashCode() {
      // ImmutableSet.of(value).hashCode() == value.hashCode(), so the hashes are the same
      return ImmutableMap.this.hashCode();
    }

    @Override
    boolean isHashCodeFast() {
      return ImmutableMap.this.isHashCodeFast();
    }

    @Override
    UnmodifiableIterator<Entry<K, ImmutableSet<V>>> entryIterator() {
      Iterator<Entry<K, V>> backingIterator = ImmutableMap.this.entrySet().iterator();
      return new UnmodifiableIterator<Entry<K, ImmutableSet<V>>>() {
        @Override
        public boolean hasNext() {
          return backingIterator.hasNext();
        }

        @Override
        public Entry<K, ImmutableSet<V>> next() {
          Entry<K, V> backingEntry = backingIterator.next();
          return new AbstractMapEntry<K, ImmutableSet<V>>() {
            @Override
            public K getKey() {
              return backingEntry.getKey();
            }

            @Override
            public ImmutableSet<V> getValue() {
              return ImmutableSet.of(backingEntry.getValue());
            }
          };
        }
      };
    }

    // redeclare to help optimizers with b/310253115
    @SuppressWarnings("RedundantOverride")
    @Override
    @J2ktIncompatible
    @GwtIncompatible
        Object writeReplace() {
      return super.writeReplace();
    }
  }

  @Override
  public boolean equals(@Nullable Object object) {
    return Maps.equalsImpl(this, object);
  }

  abstract boolean isPartialView();

  @Override
  public int hashCode() {
    return Sets.hashCodeImpl(entrySet());
  }

  boolean isHashCodeFast() {
    return false;
  }

  @Override
  public String toString() {
    return Maps.toStringImpl(this);
  }

  /**
   * Serialized type for all ImmutableMap instances. It captures the logical contents and they are
   * reconstructed using public factory methods. This ensures that the implementation types remain
   * as implementation details.
   */
  @J2ktIncompatible // serialization
  static class SerializedForm<K, V> implements Serializable {
    // This object retains references to collections returned by keySet() and value(). This saves
    // bytes when the both the map and its keySet or value collection are written to the same
    // instance of ObjectOutputStream.

    // TODO(b/160980469): remove support for the old serialization format after some time
    private static final boolean USE_LEGACY_SERIALIZATION = true;

    private final Object keys;
    private final Object values;

    SerializedForm(ImmutableMap<K, V> map) {
      if (USE_LEGACY_SERIALIZATION) {
        Object[] keys = new Object[map.size()];
        Object[] values = new Object[map.size()];
        int i = 0;
        // "extends Object" works around https://github.com/typetools/checker-framework/issues/3013
        for (Entry<? extends Object, ? extends Object> entry : map.entrySet()) {
          keys[i] = entry.getKey();
          values[i] = entry.getValue();
          i++;
        }
        this.keys = keys;
        this.values = values;
        return;
      }
      this.keys = map.keySet();
      this.values = map.values();
    }

    @SuppressWarnings("unchecked")
    final Object readResolve() {
      if (!(this.keys instanceof ImmutableSet)) {
        return legacyReadResolve();
      }

      ImmutableSet<K> keySet = (ImmutableSet<K>) this.keys;
      ImmutableCollection<V> values = (ImmutableCollection<V>) this.values;

      Builder<K, V> builder = makeBuilder(keySet.size());

      UnmodifiableIterator<K> keyIter = keySet.iterator();
      UnmodifiableIterator<V> valueIter = values.iterator();

      while (keyIter.hasNext()) {
        builder.put(keyIter.next(), valueIter.next());
      }

      return builder.buildOrThrow();
    }

    @SuppressWarnings("unchecked")
    final Object legacyReadResolve() {
      K[] keys = (K[]) this.keys;
      V[] values = (V[]) this.values;

      Builder<K, V> builder = makeBuilder(keys.length);

      for (int i = 0; i < keys.length; i++) {
        builder.put(keys[i], values[i]);
      }
      return builder.buildOrThrow();
    }

    /**
     * Returns a builder that builds the unserialized type. Subclasses should override this method.
     */
    Builder<K, V> makeBuilder(int size) {
      return new Builder<>(size);
    }

    @GwtIncompatible @J2ktIncompatible private static final long serialVersionUID = 0;
  }

  /**
   * Returns a serializable form of this object. Non-public subclasses should not override this
   * method. Publicly-accessible subclasses must override this method and should return a subclass
   * of SerializedForm whose readResolve() method returns objects of the subclass type.
   */
  @J2ktIncompatible // serialization
  Object writeReplace() {
    return new SerializedForm<>(this);
  }

  @J2ktIncompatible // java.io.ObjectInputStream
  private void readObject(ObjectInputStream stream) throws InvalidObjectException {
    throw new InvalidObjectException("Use SerializedForm");
  }

  @GwtIncompatible @J2ktIncompatible   private static final long serialVersionUID = 0xcafebabe;
}
