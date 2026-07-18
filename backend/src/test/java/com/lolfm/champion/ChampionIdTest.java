package com.lolfm.champion;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChampionIdTest {
    @Test void normalizesTrimAndCase() { assertThat(new ChampionId("  Lee-Sin ").value()).isEqualTo("lee-sin"); }
    @Test void acceptsCanonicalKebabCase() { assertThat(new ChampionId("renata-glasc").value()).isEqualTo("renata-glasc"); }
    @Test void rejectsBlankAndInvalidCharacters() {
        assertThatThrownBy(() -> new ChampionId(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChampionId("Lee Sin")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChampionId("르블랑")).isInstanceOf(IllegalArgumentException.class);
    }
}
