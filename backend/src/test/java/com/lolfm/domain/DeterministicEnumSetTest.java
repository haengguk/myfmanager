package com.lolfm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeterministicEnumSetTest {
    @Test
    void copyUsesEnumDeclarationOrderRegardlessOfInputIterationOrder() {
        Set<Position> input = new HashSet<>();
        input.add(Position.SUPPORT);
        input.add(Position.ADC);
        input.add(Position.TOP);

        Set<Position> copy = DeterministicEnumSet.copyOf(Position.class, input);

        assertThat(List.copyOf(copy)).containsExactly(
                Position.TOP, Position.ADC, Position.SUPPORT);
        assertThatThrownBy(() -> copy.add(Position.MID))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullableCopyNormalizesNullToAnImmutableEmptySet() {
        Set<Position> copy = DeterministicEnumSet.copyOfNullable(Position.class, null);

        assertThat(copy).isEmpty();
        assertThatThrownBy(() -> copy.add(Position.TOP))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
