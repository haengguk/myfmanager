package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.simulator.TeamSide;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AutoDraftScalabilityScheduleV1Test {
    private static final Set<String> ALL_TEAMS = Set.of(
            "BFX", "BRO", "DK", "DNS", "GEN", "HLE", "KRX", "KT", "NS", "T1");

    @Test
    void scheduleIsFrozenAndCoversEveryTeamOnBothSides() {
        assertThat(AutoDraftScalabilityScheduleV1.FIXTURES).hasSize(12);
        assertThat(AutoDraftScalabilityScheduleV1.teams(TeamSide.BLUE))
                .isEqualTo(ALL_TEAMS);
        assertThat(AutoDraftScalabilityScheduleV1.teams(TeamSide.RED))
                .isEqualTo(ALL_TEAMS);
        assertThat(AutoDraftScalabilityScheduleV1.SCHEDULE_HASH)
                .hasSize(64)
                .isEqualTo("8888526d5085a5bfcc75b1495223e4babeba0c69fa63dd8c9a8adda9e2315b00");
        assertThat(AutoDraftScalabilityScheduleV1.canonicalSerialization()).endsWith("\n");
    }
}
