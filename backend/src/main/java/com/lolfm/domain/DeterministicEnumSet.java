package com.lolfm.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Immutable enum set with declaration-order iteration for stable API serialization. */
public final class DeterministicEnumSet {
    private DeterministicEnumSet() {
    }

    public static <E extends Enum<E>> Set<E> copyOf(Class<E> enumType, Set<E> values) {
        Objects.requireNonNull(enumType, "enumType");
        Objects.requireNonNull(values, "values");
        EnumSet<E> copy = EnumSet.noneOf(enumType);
        copy.addAll(values);
        return Collections.unmodifiableSet(copy);
    }

    public static <E extends Enum<E>> Set<E> copyOfNullable(
            Class<E> enumType,
            Set<E> values
    ) {
        return values == null ? copyOf(enumType, Set.of()) : copyOf(enumType, values);
    }
}
