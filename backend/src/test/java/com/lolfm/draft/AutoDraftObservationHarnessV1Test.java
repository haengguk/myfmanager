package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.Team;
import com.lolfm.player.LckTeamAssembler;
import java.util.List;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AutoDraftObservationHarnessV1Test {
    private static DraftEngine engine;
    private static AutoDraftObservationHarnessV1 observer;
    private static DraftTeamContext blue;
    private static DraftTeamContext red;

    @BeforeAll
    static void prepareProductionFixture() {
        LckTeamAssembler teams = LckTeamAssembler.loadDefault();
        Team blueTeam = teams.assemble("GEN");
        Team redTeam = teams.assemble("T1");
        blue = DraftTeamContext.from(blueTeam);
        red = DraftTeamContext.from(redTeam);
        engine = new DraftEngine(DraftResourceSet.loadDefault());
        observer = new AutoDraftObservationHarnessV1(engine);
    }

    @Test
    void observedDecompositionIsExactlyEqualToProductionDraft() {
        FinalDraftResult production = engine.draft(blue, red, new SeriesDraftHistory());
        AutoDraftObservationHarnessV1.Observation observed = observer.observe(
                blue, red, new SeriesDraftHistory());

        assertThat(AutoDraftObservationHarnessV1.productionEquivalent(
                observed.result(), production)).isTrue();
        assertThat(observed.result().draftIdentity()).isEqualTo(production.draftIdentity());
        assertThat(observed.result().matchChampionAssignments().asMap())
                .isEqualTo(production.matchChampionAssignments().asMap());
        assertThat(observed.turns()).hasSize(20);
        assertThat(observed.turns()).extracting(
                        AutoDraftObservationHarnessV1.TurnObservation::actionType)
                .containsExactlyElementsOf(production.ruleSet().turns().stream()
                        .map(DraftTurn::actionType).toList());
        assertThat(observed.turns()).allSatisfy(turn -> {
            assertThat(turn.turnNanos()).isNotNegative();
            assertThat(turn.rootCandidateScores()).isNotEmpty();
            assertThat(turn.counters().rootCandidateGenerationCalls()).isOne();
            assertThat(turn.counters().rootActionEvaluations())
                    .isEqualTo(turn.rootCandidateScores().size());
        });
    }

    @Test
    void repeatedObservationKeepsExactDecisionsScoresRolesAndDeterministicCounters() {
        AutoDraftObservationHarnessV1.Observation first = observer.observe(
                blue, red, new SeriesDraftHistory());
        AutoDraftObservationHarnessV1.Observation replay = observer.observe(
                blue, red, new SeriesDraftHistory());

        assertThat(AutoDraftObservationHarnessV1.productionEquivalent(
                replay.result(), first.result())).isTrue();
        assertThat(replay.counters()).isEqualTo(first.counters());
        assertThat(replay.turns().stream().map(AutoDraftObservationHarnessV1.TurnObservation::counters).toList())
                .isEqualTo(first.turns().stream().map(
                        AutoDraftObservationHarnessV1.TurnObservation::counters).toList());
        assertThat(first.counters().initialPlanCalls()).isEqualTo(2);
        assertThat(first.counters().replanCalls()).isPositive();
        assertThat(first.counters().candidateGenerationCalls()).isPositive();
        assertThat(first.counters().continuationNodes()).isPositive();
        assertThat(first.counters().pickEvaluations()
                + first.counters().banEvaluations()).isEqualTo(first.counters().actionEvaluations());
        assertThat(first.result().decisions()).extracting(DraftDecision::actionType)
                .containsExactlyElementsOf(List.copyOf(first.result().ruleSet().turns()).stream()
                        .map(DraftTurn::actionType).toList());
    }

    @Test
    void jfrProfilerOnAndOffPreserveDraftIdentityRolesAndCounters(@TempDir Path temporary) {
        AutoDraftObservationHarnessV1.Observation profilerOff = observer.observe(
                blue, red, new SeriesDraftHistory());
        AutoDraftObservationHarnessV1.Observation profilerOn;
        AutoDraftJfrSamplerV1.Profile profile;
        try (AutoDraftJfrSamplerV1.Session session = AutoDraftJfrSamplerV1.start()) {
            profilerOn = observer.observe(blue, red, new SeriesDraftHistory());
            profile = session.finish(temporary.resolve("focused-auto-draft.jfr"));
        }

        assertThat(AutoDraftObservationHarnessV1.productionEquivalent(
                profilerOff.result(), profilerOn.result())).isTrue();
        assertThat(profilerOn.counters()).isEqualTo(profilerOff.counters());
        assertThat(profile.executionSamplePeriodMillis()).isEqualTo(10);
        assertThat(profile.allExecutionSamples()).isPositive();
    }
}
