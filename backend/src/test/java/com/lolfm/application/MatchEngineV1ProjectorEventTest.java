package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.ObjectiveFightEventData;
import com.lolfm.domain.ObjectiveFightSkillImpactData;
import com.lolfm.simulator.TeamSide;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchEngineV1ProjectorEventTest {
    @Test
    void objectiveFightStartHasNoSingleActorAndResultUsesStructuredWinner() {
        ObjectiveFightEventData data = new ObjectiveFightEventData(
                TeamSide.RED, List.of("BLUE:TOP", "RED:TOP"), 1,
                new ObjectiveFightSkillImpactData(14, 14, 14, 14, 0, 0, 0));
        MatchEvent start = new MatchEvent(
                340, MatchEventType.TEAMFIGHT, "display", null, null, List.of());
        start.setObjectiveFight(data);
        MatchEvent result = new MatchEvent(
                340, MatchEventType.TEAMFIGHT_RESULT, "display", null, null, List.of());
        result.setObjectiveFight(data);

        assertThat(MatchEngineV1Projector.actorSide(start, null, null)).isNull();
        assertThat(MatchEngineV1Projector.actorSide(result, null, null)).isEqualTo(TeamSide.RED);
    }
}
