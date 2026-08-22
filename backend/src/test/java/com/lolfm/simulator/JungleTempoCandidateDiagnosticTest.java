package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.champion.ChampionResourceSet;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.factory.DummyDataFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Small fixed candidate observation; this is explicitly not a balance calibration. */
@Tag("diagnostic")
@Tag("jungle-tempo-candidate")
class JungleTempoCandidateDiagnosticTest {
    private static final long FIRST_SEED = 2026082201L;
    private static final int MATCH_COUNT = 12;
    private static final ChampionResourceSet RESOURCES = ChampionResourceSet.loadDefault();

    @Test
    void writesBoundedEconomyOnlyVersusTempoCandidateObservation() throws Exception {
        var assignments = new ChampionSelectionValidator(RESOURCES.catalog()).resolve(null);
        ArrayList<MatchObservation> observations = new ArrayList<>();
        ProfileAccumulator economyTotals = new ProfileAccumulator();
        ProfileAccumulator tempoTotals = new ProfileAccumulator();

        for (int offset = 0; offset < MATCH_COUNT; offset++) {
            long seed = FIRST_SEED + offset;
            MatchSimulator.SimulationResult economyOnly = simulate(
                    SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1,
                    seed, assignments);
            MatchSimulator.SimulationResult tempo = simulate(
                    SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1,
                    seed, assignments);

            assertEconomyOnlyHasNoTempoExecution(economyOnly);
            assertTempoConsumptionMatchesActualActions(tempo);
            economyTotals.add(economyOnly);
            tempoTotals.add(tempo);
            observations.add(observation(seed, economyOnly, tempo));
        }

        CandidateDiagnosticReport report = new CandidateDiagnosticReport(
                "JUNGLE_TEMPO_CANDIDATE_DIAGNOSTIC_V1",
                "12 fixed same-seed paired dummy-roster matches; observational only, "
                        + "not calibration and not a production-activation gate",
                profileIdentity(
                        SimulationRuntimeProfileId
                                .FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1),
                profileIdentity(
                        SimulationRuntimeProfileId
                                .FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1),
                new TempoRules(
                        JungleTempoRuleConfig.MIN_CREDIT_EFFICIENCY,
                        JungleTempoRuleConfig.MAX_CREDIT_EFFICIENCY,
                        JungleTempoRuleConfig.FIRST_ACTION_READINESS_SECONDS,
                        JungleTempoRuleConfig.REPEAT_ACTION_READINESS_SECONDS,
                        JungleTempoRuleConfig.ACTION_COST_SECONDS,
                        JungleTempoRuleConfig.MAX_BANKED_CREDIT_SECONDS,
                        JungleTempoRuleConfig.CONTINUITY_GRACE_SECONDS),
                economyTotals.snapshot(), tempoTotals.snapshot(), List.copyOf(observations),
                "NOT_CONNECTED: Jungle Tempo does not gate or modify objective eligibility",
                "CANDIDATE_ONLY_FORWARD_TO_JUNGLE_V1_FOCUSED_HARDENING");

        Path output = Path.of("build", "reports", "jungle-tempo-v1-b");
        Files.createDirectories(output);
        new ObjectMapper().findAndRegisterModules()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(output.resolve("candidate-diagnostic-report.json").toFile(), report);
    }

    private MatchSimulator.SimulationResult simulate(
            SimulationRuntimeProfileId profileId,
            long seed,
            com.lolfm.champion.MatchChampionAssignments assignments
    ) {
        DummyDataFactory teams = new DummyDataFactory();
        var blue = teams.createBlueTeam();
        var red = teams.createRedTeam();
        SideOrientationRandomTraceObserver random = new SideOrientationRandomTraceObserver(
                seed, "JUNGLE_TEMPO_CANDIDATE_DIAGNOSTIC",
                blue.getName(), red.getName(), false);
        return simulator(profileId).simulateWithSideDiagnostics(
                blue, red, assignments, random);
    }

    private MatchSimulator simulator(SimulationRuntimeProfileId profileId) {
        SimulationOptions options = SimulationRuntimeProfiles.resolve(profileId)
                .gameplayConfiguration()
                .toSimulationOptions(SimulationInstrumentation.enabled());
        return new MatchSimulator(
                new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(),
                new ObjectiveAttemptResolver(), new StructureResolver(), new PushResolver(),
                options, RESOURCES.matchup());
    }

    private void assertEconomyOnlyHasNoTempoExecution(
            MatchSimulator.SimulationResult result
    ) {
        JungleTempoExecutionStatsSnapshot tempo = result.jungleTempoExecutionStats();
        assertThat(tempo.economyUpdates()).isZero();
        assertThat(tempo.gankReadinessByStatus().values()).containsOnly(0);
        assertThat(tempo.counterGankReadinessByStatus().values()).containsOnly(0);
        assertThat(tempo.actualConsumptions().values()).containsOnly(0);
    }

    private void assertTempoConsumptionMatchesActualActions(
            MatchSimulator.SimulationResult result
    ) {
        assertThat(result.jungleTempoExecutionStats().economyUpdates()).isPositive();
        assertThat(result.jungleTempoExecutionStats().actualConsumptions()
                .get(JungleTempoActionType.GANK))
                .isEqualTo(result.combatExecutionStats().jungleGankAttempts());
        assertThat(result.jungleTempoExecutionStats().actualConsumptions()
                .get(JungleTempoActionType.COUNTER_GANK))
                .isEqualTo(result.combatExecutionStats().counterGankAttempts());
    }

    private MatchObservation observation(
            long seed,
            MatchSimulator.SimulationResult economyOnly,
            MatchSimulator.SimulationResult tempo
    ) {
        return new MatchObservation(
                seed, economyOnly.timeline().getWinner(), tempo.timeline().getWinner(),
                economyOnly.timeline().getDurationSeconds(), tempo.timeline().getDurationSeconds(),
                economyOnly.combatExecutionStats().jungleGankAttempts(),
                tempo.combatExecutionStats().jungleGankAttempts(),
                economyOnly.combatExecutionStats().counterGankAttempts(),
                tempo.combatExecutionStats().counterGankAttempts(),
                tempo.jungleTempoExecutionStats().economyUpdates(),
                tempo.jungleTempoExecutionStats().gankReadinessByStatus(),
                tempo.jungleTempoExecutionStats().counterGankReadinessByStatus(),
                tempo.jungleTempoExecutionStats().actualConsumptions(),
                economyOnly.randomDrawCount(), tempo.randomDrawCount(),
                economyOnly.randomTraceHash(), tempo.randomTraceHash());
    }

    private ProfileIdentity profileIdentity(SimulationRuntimeProfileId profileId) {
        ResolvedSimulationRuntimeProfile profile = SimulationRuntimeProfiles.resolve(profileId);
        return new ProfileIdentity(
                profile.profileId(), profile.configurationHash(),
                profile.activeGameplayRulesVersion(),
                profile.gameplayConfiguration().jungleClearContribution());
    }

    private record CandidateDiagnosticReport(
            String schemaVersion,
            String samplePolicy,
            ProfileIdentity economyOnlyProfile,
            ProfileIdentity tempoCandidateProfile,
            TempoRules tempoRules,
            ProfileTotals economyOnlyTotals,
            ProfileTotals tempoCandidateTotals,
            List<MatchObservation> matches,
            String objectiveIntegration,
            String status
    ) {
    }

    private record ProfileIdentity(
            SimulationRuntimeProfileId profileId,
            String configurationHash,
            String activeGameplayRulesVersion,
            JungleClearContribution jungleClearContribution
    ) {
    }

    private record TempoRules(
            double minimumCreditEfficiency,
            double maximumCreditEfficiency,
            double firstActionReadinessSeconds,
            double repeatActionReadinessSeconds,
            double actionCostSeconds,
            double maximumBankedCreditSeconds,
            int continuityGraceSeconds
    ) {
    }

    private record MatchObservation(
            long seed,
            String economyOnlyWinner,
            String tempoCandidateWinner,
            int economyOnlyDurationSeconds,
            int tempoCandidateDurationSeconds,
            int economyOnlyGankAttempts,
            int tempoCandidateGankAttempts,
            int economyOnlyCounterGankAttempts,
            int tempoCandidateCounterGankAttempts,
            int tempoEconomyUpdates,
            Map<JungleTempoReadinessStatus, Integer> tempoGankReadiness,
            Map<JungleTempoReadinessStatus, Integer> tempoCounterGankReadiness,
            Map<JungleTempoActionType, Integer> tempoActualConsumptions,
            long economyOnlyRandomDrawCount,
            long tempoCandidateRandomDrawCount,
            String economyOnlyRandomTraceHash,
            String tempoCandidateRandomTraceHash
    ) {
    }

    private static final class ProfileAccumulator {
        private int matches;
        private int gankAttempts;
        private int counterGankAttempts;
        private int noEligibleGankEvaluations;
        private int triggerRolls;
        private int triggerSuccesses;
        private int fallthroughs;
        private int tempoEconomyUpdates;
        private final EnumMap<JungleTempoReadinessStatus, Integer> gankReadiness =
                zeroed(JungleTempoReadinessStatus.class);
        private final EnumMap<JungleTempoReadinessStatus, Integer> counterReadiness =
                zeroed(JungleTempoReadinessStatus.class);
        private final EnumMap<JungleTempoActionType, Integer> consumptions =
                zeroed(JungleTempoActionType.class);

        private void add(MatchSimulator.SimulationResult result) {
            matches++;
            CombatExecutionStatsSnapshot combat = result.combatExecutionStats();
            JungleTempoExecutionStatsSnapshot tempo = result.jungleTempoExecutionStats();
            gankAttempts += combat.jungleGankAttempts();
            counterGankAttempts += combat.counterGankAttempts();
            noEligibleGankEvaluations += combat.jungleGankNoEligibleSides();
            triggerRolls += combat.jungleGankTriggerRolls();
            triggerSuccesses += combat.jungleGankTriggerSuccesses();
            fallthroughs += combat.jungleGankFallthroughs();
            tempoEconomyUpdates += tempo.economyUpdates();
            merge(gankReadiness, tempo.gankReadinessByStatus());
            merge(counterReadiness, tempo.counterGankReadinessByStatus());
            merge(consumptions, tempo.actualConsumptions());
        }

        private ProfileTotals snapshot() {
            return new ProfileTotals(
                    matches, gankAttempts, counterGankAttempts,
                    noEligibleGankEvaluations, triggerRolls, triggerSuccesses,
                    fallthroughs, tempoEconomyUpdates,
                    immutableEnumMap(gankReadiness), immutableEnumMap(counterReadiness),
                    immutableEnumMap(consumptions));
        }

        private static <E extends Enum<E>> void merge(
                EnumMap<E, Integer> target,
                Map<E, Integer> values
        ) {
            values.forEach((key, value) -> target.merge(key, value, Integer::sum));
        }

        private static <E extends Enum<E>> EnumMap<E, Integer> zeroed(Class<E> type) {
            EnumMap<E, Integer> result = new EnumMap<>(type);
            for (E value : type.getEnumConstants()) result.put(value, 0);
            return result;
        }

        private static <E extends Enum<E>> Map<E, Integer> immutableEnumMap(
                EnumMap<E, Integer> values
        ) {
            return Collections.unmodifiableMap(new EnumMap<>(values));
        }
    }

    private record ProfileTotals(
            int matches,
            int gankAttempts,
            int counterGankAttempts,
            int noEligibleGankEvaluations,
            int triggerRolls,
            int triggerSuccesses,
            int fallthroughs,
            int tempoEconomyUpdates,
            Map<JungleTempoReadinessStatus, Integer> gankReadiness,
            Map<JungleTempoReadinessStatus, Integer> counterGankReadiness,
            Map<JungleTempoActionType, Integer> actualConsumptions
    ) {
    }
}
