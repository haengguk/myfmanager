package com.lolfm.champion;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChampionNeutralityTest {
    @Test void foundationPowerIsExactlyNeutral() {
        assertThat(ChampionNeutrality.MULTIPLIER).isOne();
        assertThat(ChampionNeutrality.SPIKE_BONUS).isZero();
        assertThat(ChampionNeutrality.CONTEXT_MODIFIER).isZero();
        assertThat(ChampionNeutrality.COMBAT_CONTRIBUTION).isZero();
    }
}
