package com.lolfm.application;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.PlayerSnapshot;
import com.lolfm.domain.Position;
import com.lolfm.draft.DraftResourceSet;
import com.lolfm.draft.DraftRuleSet;
import com.lolfm.draft.DraftScoringPolicy;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.PlayerIdentityCatalog;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.simulator.CombatExecutionStatsSnapshot;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import com.lolfm.simulator.GameEndReason;
import com.lolfm.simulator.JungleEconomyExecutionStatsSnapshot;
import com.lolfm.simulator.JungleTempoExecutionStatsSnapshot;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.ProgressionExecutionStatsSnapshot;
import com.lolfm.simulator.ResolvedSimulationRuntimeProfile;
import com.lolfm.simulator.SimulationGameplayConfiguration;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.SimulationRandomFingerprint;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import com.lolfm.simulator.TeamSide;
import com.lolfm.simulator.Phase13GB1SimulationExecutor;
import com.lolfm.simulator.Phase13GB1SimulationExecutor.StructuredDiagnostics;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reuses one production real-Draft fixture across the five closed runtime profiles. */
public final class Phase13GB1RealMatchHarness {
    public static final String SCHEMA = "PHASE_13G_B_REAL_MATCH_HARNESS_V2";
    public static final List<SimulationRuntimeProfileId> AUDIT_PROFILES = List.of(
            SimulationRuntimeProfileId.BASELINE_V1,
            SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1,
            SimulationRuntimeProfileId.FULL_SYSTEM_CANDIDATE_V1,
            SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1,
            SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_TEMPO_CANDIDATE_V1);

    private final RealDraftMatchOrchestrator orchestrator;
    private final ConfiguredMatchSimulatorFactory simulators;
    private final SimulationProvenanceService provenance;

    public Phase13GB1RealMatchHarness(
            RealDraftMatchOrchestrator orchestrator,
            ConfiguredMatchSimulatorFactory simulators,
            ObjectMapper mapper,
            ChampionCatalog champions,
            PlayerIdentityCatalog identities,
            PlayerRatingCatalog ratings,
            ChampionProficiencyCatalog proficiencies
    ) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.simulators = Objects.requireNonNull(simulators, "simulators");
        DraftResourceSet resources = DraftResourceSet.loadDefault(
                Objects.requireNonNull(mapper, "mapper"),
                Objects.requireNonNull(champions, "champions"));
        provenance = new SimulationProvenanceService(
                mapper,
                resources,
                Objects.requireNonNull(identities, "identities"),
                Objects.requireNonNull(ratings, "ratings"),
                Objects.requireNonNull(proficiencies, "proficiencies"),
                DraftRuleSet.professional(),
                DraftScoringPolicy.standard());
    }

    public PreparedFixture prepareFixture(Phase13GB1AuditSchedule.Fixture fixture) {
        requireRegisteredFixture(fixture);
        SeriesDraftHistory history = new SeriesDraftHistory();
        RealDraftMatchResult prepared = null;
        for (int game = 1; game <= fixture.seriesGameNumber(); game++) {
            long preparationSeed = preparationSeed(fixture, game);
            prepared = orchestrator.orchestrate(
                    fixture.blueTeamCode(),
                    fixture.redTeamCode(),
                    history,
                    preparationSeed,
                    SimulationRuntimeProfileId.BASELINE_V1,
                    SimulationInstrumentation.enabled());
        }
        if (prepared == null || prepared.seriesGameNumber() != fixture.seriesGameNumber()) {
            throw new IllegalStateException("Real Draft fixture preparation did not reach target game");
        }
        if (!prepared.blueTeamCode().equals(fixture.blueTeamCode())
                || !prepared.redTeamCode().equals(fixture.redTeamCode())) {
            throw new IllegalStateException("Prepared real Draft side identity mismatch");
        }
        return new PreparedFixture(
                fixture,
                prepared,
                fixture.seriesGameNumber(),
                "PRODUCTION_REAL_DRAFT_ORCHESTRATOR_ONCE_PER_SERIES_GAME_THEN_FIXED_REUSE");
    }

    public List<AuditMatchRun> executeAllProfiles(
            PreparedFixture prepared,
            Phase13GB1AuditSchedule.SampleLane sampleLane,
            long seed
    ) {
        Objects.requireNonNull(prepared, "prepared");
        requireRegisteredFixture(prepared.fixture());
        requireScheduledSeed(prepared.fixture(), sampleLane, seed);
        ArrayList<AuditMatchRun> result = new ArrayList<>(AUDIT_PROFILES.size());
        for (SimulationRuntimeProfileId profileId : AUDIT_PROFILES) {
            result.add(execute(prepared, sampleLane, seed, profileId));
        }
        assertFixedDraftReuse(result);
        return List.copyOf(result);
    }

    public AuditMatchRun execute(
            PreparedFixture prepared,
            Phase13GB1AuditSchedule.SampleLane sampleLane,
            long seed,
            SimulationRuntimeProfileId profileId
    ) {
        Objects.requireNonNull(prepared, "prepared");
        requireRegisteredFixture(prepared.fixture());
        requireScheduledSeed(prepared.fixture(), sampleLane, seed);
        if (!AUDIT_PROFILES.contains(profileId)) {
            throw new IllegalArgumentException("Profile is outside the frozen 13G-B audit set");
        }
        RealDraftMatchResult fixture = prepared.realDraftFixture();
        ResolvedSimulationRuntimeProfile profile = SimulationRuntimeProfiles.resolve(profileId);
        var execution = Phase13GB1SimulationExecutor.execute(
                simulators,
                fixture.blueTeam(),
                fixture.redTeam(),
                fixture.matchChampionAssignments(),
                profileId,
                seed,
                fixture.blueTeamCode(),
                fixture.redTeamCode());
        StructuredDiagnostics diagnostics = execution.structuredDiagnostics();
        SimulationExecutionProvenance executionProvenance = provenance.create(
                profile,
                SimulationInstrumentation.enabled(),
                fixture.blueTeamCode(),
                fixture.blueTeam(),
                fixture.redTeamCode(),
                fixture.redTeam(),
                seed,
                fixture.seriesGameNumber(),
                fixture.hardFearlessExclusionsBeforeDraft(),
                fixture.draftResult(),
                execution.timeline(),
                execution.randomFingerprint());
        MatchSnapshot finalSnapshot = execution.timeline().getSnapshots().getLast();
        return new AuditMatchRun(
                SCHEMA,
                prepared.fixture().fixtureId(),
                prepared.fixture().fixtureLane(),
                sampleLane,
                fixture.blueTeamCode(),
                fixture.redTeamCode(),
                fixture.seriesGameNumber(),
                seed,
                profileId,
                profile.gameplayConfiguration(),
                profile.configurationHash(),
                profile.activeGameplayRulesVersion(),
                executionProvenance.engineImplementationVersion(),
                executionProvenance.resourceProvenance().resourceProvenanceHash(),
                executionProvenance.rosterIdentityHash(),
                executionProvenance.seriesHistoryBeforeHash(),
                executionProvenance.draftDecisionHash(),
                executionProvenance.finalDraftHash(),
                executionProvenance.finalAssignmentHash(),
                executionProvenance.replayProvenanceHash(),
                executionProvenance.timelineHash(),
                execution.timeline(),
                execution.randomFingerprint(),
                execution.timeline().getWinner(),
                execution.winnerSide(),
                execution.endReason(),
                execution.timeline().getDurationSeconds(),
                finalSnapshot.getBlueKills(),
                finalSnapshot.getRedKills(),
                finalSnapshot.getBlueGold(),
                finalSnapshot.getRedGold(),
                finalSnapshot.getBlueDragons(),
                finalSnapshot.getRedDragons(),
                finalSnapshot.getBlueTowersDestroyed(),
                finalSnapshot.getRedTowersDestroyed(),
                jungleCheckpoint(fixture, finalSnapshot, TeamSide.BLUE),
                jungleCheckpoint(fixture, finalSnapshot, TeamSide.RED),
                Phase13GB1SimulationExecutor.structuredDiagnosticsHash(diagnostics),
                diagnostics,
                IntegrityDiagnostics.from(profile.gameplayConfiguration(), diagnostics));
    }

    public SimulationResourceProvenance resourceProvenance() {
        return provenance.resourceProvenance();
    }

    private static void requireScheduledSeed(
            Phase13GB1AuditSchedule.Fixture fixture,
            Phase13GB1AuditSchedule.SampleLane sampleLane,
            long seed
    ) {
        Objects.requireNonNull(sampleLane, "sampleLane");
        boolean valid = switch (sampleLane) {
            case DRY_RUN -> seed == Phase13GB1AuditSchedule.dryRunSeed(fixture);
            case CALIBRATION -> fixture.calibrationSeeds().contains(seed);
            case HOLDOUT -> fixture.holdoutSeeds().contains(seed);
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "Seed is not registered in " + sampleLane + " for " + fixture.fixtureId());
        }
    }

    private static void requireRegisteredFixture(Phase13GB1AuditSchedule.Fixture fixture) {
        Objects.requireNonNull(fixture, "fixture");
        Phase13GB1AuditSchedule.Fixture registered = Phase13GB1AuditSchedule.create()
                .allFixtures().stream()
                .filter(value -> value.fixtureId().equals(fixture.fixtureId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Fixture is outside the frozen 13G-B schedule: " + fixture.fixtureId()));
        if (!registered.equals(fixture)) {
            throw new IllegalArgumentException(
                    "Fixture differs from the frozen 13G-B schedule: " + fixture.fixtureId());
        }
    }

    private static void assertFixedDraftReuse(List<AuditMatchRun> runs) {
        if (runs.size() != AUDIT_PROFILES.size()
                || runs.stream().map(AuditMatchRun::profileId).distinct().count()
                != AUDIT_PROFILES.size()
                || runs.stream().map(AuditMatchRun::draftDecisionHash).distinct().count() != 1
                || runs.stream().map(AuditMatchRun::finalDraftHash).distinct().count() != 1
                || runs.stream().map(AuditMatchRun::finalAssignmentHash).distinct().count() != 1
                || runs.stream().map(AuditMatchRun::resourceProvenanceHash).distinct().count() != 1
                || runs.stream().map(AuditMatchRun::rosterIdentityHash).distinct().count() != 1) {
            throw new IllegalStateException("Five-profile paired run did not reuse one fixed fixture");
        }
    }

    private static JungleCheckpoint jungleCheckpoint(
            RealDraftMatchResult fixture,
            MatchSnapshot snapshot,
            TeamSide side
    ) {
        PlayerSnapshot player = snapshot.getPlayerSnapshots().stream()
                .filter(value -> value.getTeamSide() == side
                        && value.getPosition() == Position.JUNGLE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing final jungle snapshot for " + side));
        var playerId = fixture.playerIdsByMatchSlot().get(new PlayerKey(side, Position.JUNGLE));
        if (playerId == null) throw new IllegalStateException("Missing jungle PlayerId for " + side);
        return new JungleCheckpoint(
                side,
                playerId.value(),
                player.getChampionId(),
                player.getKills(),
                player.getDeaths(),
                player.getAssists(),
                player.getCs(),
                player.getGold(),
                player.getTotalExperience(),
                player.getLevel(),
                player.getItemStage().name());
    }

    private static long preparationSeed(Phase13GB1AuditSchedule.Fixture fixture, int game) {
        return Phase13GB1AuditSchedule.dryRunSeed(fixture)
                ^ Long.rotateLeft(0x9e3779b97f4a7c15L, game * 7);
    }

    public static final class PreparedFixture {
        private final Phase13GB1AuditSchedule.Fixture fixture;
        private final RealDraftMatchResult realDraftFixture;
        private final int productionOrchestrationCount;
        private final String reusePolicy;

        private PreparedFixture(
                Phase13GB1AuditSchedule.Fixture fixture,
                RealDraftMatchResult realDraftFixture,
                int productionOrchestrationCount,
                String reusePolicy
        ) {
            this.fixture = Objects.requireNonNull(fixture, "fixture");
            this.realDraftFixture = Objects.requireNonNull(
                    realDraftFixture, "realDraftFixture");
            if (productionOrchestrationCount != fixture.seriesGameNumber()) {
                throw new IllegalArgumentException("Preparation count must equal target series game");
            }
            this.productionOrchestrationCount = productionOrchestrationCount;
            this.reusePolicy = Objects.requireNonNull(reusePolicy, "reusePolicy");
            if (!realDraftFixture.blueTeamCode().equals(fixture.blueTeamCode())
                    || !realDraftFixture.redTeamCode().equals(fixture.redTeamCode())
                    || realDraftFixture.seriesGameNumber() != fixture.seriesGameNumber()) {
                throw new IllegalArgumentException(
                        "Prepared result differs from the scheduled fixture identity");
            }
        }

        public Phase13GB1AuditSchedule.Fixture fixture() {
            return fixture;
        }

        public RealDraftMatchResult realDraftFixture() {
            return realDraftFixture;
        }

        public int productionOrchestrationCount() {
            return productionOrchestrationCount;
        }

        public String reusePolicy() {
            return reusePolicy;
        }
    }

    public record JungleCheckpoint(
            TeamSide side,
            String playerId,
            String championId,
            int kills,
            int deaths,
            int assists,
            int cs,
            int gold,
            int totalExperience,
            int level,
            String itemStage
    ) {
    }

    public record IntegrityDiagnostics(
            EconomyIntegrity economy,
            ProgressionIntegrity progression,
            ChampionPowerIntegrity championPower,
            ChampionMatchupIntegrity championMatchup,
            CompositionIntegrity composition,
            CombatOutcomeIntegrity combatOutcome,
            ObjectivePriorityIntegrity objectivePriority,
            StructureIntegrity structure,
            LanePhaseIntegrity lanePhase,
            MidGameMacroIntegrity midGameMacro
    ) {
        public IntegrityDiagnostics {
            Objects.requireNonNull(economy, "economy");
            Objects.requireNonNull(progression, "progression");
            Objects.requireNonNull(championPower, "championPower");
            Objects.requireNonNull(championMatchup, "championMatchup");
            Objects.requireNonNull(composition, "composition");
            Objects.requireNonNull(combatOutcome, "combatOutcome");
            Objects.requireNonNull(objectivePriority, "objectivePriority");
            Objects.requireNonNull(structure, "structure");
            Objects.requireNonNull(lanePhase, "lanePhase");
            Objects.requireNonNull(midGameMacro, "midGameMacro");
        }

        public static IntegrityDiagnostics from(
                SimulationGameplayConfiguration configuration,
                StructuredDiagnostics diagnostics
        ) {
            Objects.requireNonNull(configuration, "configuration");
            Objects.requireNonNull(diagnostics, "diagnostics");
            var progression = diagnostics.progression();
            var power = diagnostics.championPower();
            var matchup = diagnostics.championMatchup();
            var composition = diagnostics.composition();
            var outcome = diagnostics.combatOutcome();
            var objective = diagnostics.objectivePriority();
            var structure = diagnostics.structure();
            var lanePhase = diagnostics.lanePhase();
            var macro = diagnostics.midGameMacro();
            boolean compositionActive = configuration.teamCompositionGameplayMode()
                    != com.lolfm.composition.TeamCompositionGameplayMode.OFF;
            int compositionOffModeMutation = !compositionActive && (
                    composition.initialized()
                            || composition.lineupBuildCount() != 0
                            || composition.teamCompositionAnalysisCount() != 0
                            || composition.interactionAnalysisCount() != 0
                            || composition.contextEdgeCount() != 0
                            || composition.runtimeInteractionRecalculationCount() != 0
                            || composition.resolverEvaluationCount() != 0
                            || composition.triggerSuccessCount() != 0
                            || composition.actualAttemptCount() != 0
                            || composition.mappedActualAttemptCount() != 0
                            || composition.unmappedActualAttemptCount() != 0
                            || composition.shadowObservationCount() != 0
                            || composition.evaluationOnlyObservationCount() != 0
                            || composition.duplicateObservationCount() != 0
                            || composition.multiContextAttemptCount() != 0
                            || composition.conflictingPerspectiveCount() != 0
                            || composition.duplicateApplicationPointCount() != 0
                            || composition.gameplayApplicationCount() != 0
                            || composition.nonZeroModifierCount() != 0
                            || composition.directRandomCallCount() != 0
                            || composition.compositionRandomDrawCount() != 0
                            || !composition.observations().isEmpty()
                            || !composition.routings().isEmpty()) ? 1 : 0;
            return new IntegrityDiagnostics(
                    new EconomyIntegrity(
                            diagnostics.duplicateEconomyResolutions(),
                            diagnostics.jungleEconomy().duplicateCalls()),
                    new ProgressionIntegrity(
                            progression.duplicateXpReward(),
                            progression.invalidPlayer(),
                            progression.featureOffMutation(),
                            progression.duplicateLevelEvent(),
                            progression.duplicateItemEvent(),
                            progression.levelRollback(),
                            progression.itemRollback(),
                            progression.levelOver18(),
                            progression.powerOffContributionError(),
                            progression.championMultiplierError(),
                            progression.championSpikeError(),
                            progression.positionMultiplierError(),
                            progression.levelSpikeBonusError()),
                    new ChampionPowerIntegrity(
                            power.missingAssignment(),
                            power.deadParticipantIncludedError(),
                            power.nonparticipantIncludedError(),
                            power.duplicateParticipantError(),
                            power.randomCallCount()),
                    new ChampionMatchupIntegrity(
                            matchup.missingAssignmentErrors(),
                            matchup.deadParticipantErrors(),
                            matchup.nonParticipantErrors(),
                            matchup.sameTeamPairErrors(),
                            matchup.crossPositionErrors(),
                            matchup.duplicateApplicationErrors(),
                            matchup.staleStateErrors(),
                            matchup.directRandomCalls(),
                            matchup.featureOffMismatch(),
                            matchup.mirrorMismatch()),
                    new CompositionIntegrity(
                            composition.mode() == configuration.teamCompositionGameplayMode() ? 0 : 1,
                            compositionActive == composition.initialized() ? 0 : 1,
                            compositionActive && composition.lineupBuildCount() != 2 ? 1 : 0,
                            compositionActive && composition.teamCompositionAnalysisCount() != 2 ? 1 : 0,
                            compositionActive && composition.interactionAnalysisCount() != 1 ? 1 : 0,
                            compositionOffModeMutation,
                            composition.actualAttemptCount()
                                    == composition.mappedActualAttemptCount()
                                    + composition.unmappedActualAttemptCount() ? 0 : 1,
                            composition.shadowObservationCount()
                                    == composition.mappedActualAttemptCount() ? 0 : 1,
                            composition.unmappedActualAttemptCount(),
                            composition.evaluationOnlyObservationCount(),
                            composition.duplicateObservationCount(),
                            composition.multiContextAttemptCount(),
                            composition.conflictingPerspectiveCount(),
                            composition.duplicateApplicationPointCount(),
                            composition.directRandomCallCount(),
                            composition.compositionRandomDrawCount(),
                            composition.auditSemanticsEnabled() ? 1 : 0,
                            composition.keySpecificCandidateAuditEnabled() ? 1 : 0),
                    new CombatOutcomeIntegrity(
                            outcome.duplicateOutcomeRecordErrors(),
                            outcome.outcomeWithoutAttemptErrors(),
                            outcome.outcomeWithoutWinnerErrors(),
                            outcome.participantMismatchErrors()),
                    new ObjectivePriorityIntegrity(
                            objective.priorityAppliedToPostFightError(),
                            objective.priorityAppliedToElderError(),
                            objective.sameTickGeneralPostFightDuplicate(),
                            objective.disabledBonusApplication(),
                            objective.disabledMultiplierApplication(),
                            objective.wrongSideSign(),
                            objective.wrongLaneMultiplier(),
                            objective.summaryKillDoubleImpact()),
                    new StructureIntegrity(
                            structure.sameSideMultipleAttemptError(),
                            structure.sameSideMultipleMutationError(),
                            structure.postFightInternalBlockError()),
                    new LanePhaseIntegrity(
                            lanePhase.duplicateLaneTransitions(),
                            lanePhase.duplicateMatchTransitions()),
                    new MidGameMacroIntegrity(
                            macro.duplicateStructure(),
                            macro.deadAssignmentErrors(),
                            macro.combatParticipantAssignmentErrors()));
        }

        public long errorCount() {
            return economy.errorCount()
                    + progression.errorCount()
                    + championPower.errorCount()
                    + championMatchup.errorCount()
                    + composition.errorCount()
                    + combatOutcome.errorCount()
                    + objectivePriority.errorCount()
                    + structure.errorCount()
                    + lanePhase.errorCount()
                    + midGameMacro.errorCount();
        }

        public boolean clean() {
            return errorCount() == 0;
        }
    }

    public record EconomyIntegrity(
            int duplicateEconomyResolutions,
            int jungleEconomyDuplicateCalls
    ) {
        long errorCount() {
            return (long) duplicateEconomyResolutions + jungleEconomyDuplicateCalls;
        }
    }

    public record ProgressionIntegrity(
            int duplicateXpReward,
            int invalidPlayer,
            int featureOffMutation,
            int duplicateLevelEvent,
            int duplicateItemEvent,
            int levelRollback,
            int itemRollback,
            int levelOver18,
            int powerOffContributionError,
            int championMultiplierError,
            int championSpikeError,
            int positionMultiplierError,
            int levelSpikeBonusError
    ) {
        long errorCount() {
            return (long) duplicateXpReward + invalidPlayer + featureOffMutation
                    + duplicateLevelEvent + duplicateItemEvent + levelRollback + itemRollback
                    + levelOver18 + powerOffContributionError + championMultiplierError
                    + championSpikeError + positionMultiplierError + levelSpikeBonusError;
        }
    }

    public record ChampionPowerIntegrity(
            int missingAssignment,
            int deadParticipantIncludedError,
            int nonparticipantIncludedError,
            int duplicateParticipantError,
            int directRandomCalls
    ) {
        long errorCount() {
            return (long) missingAssignment + deadParticipantIncludedError
                    + nonparticipantIncludedError + duplicateParticipantError + directRandomCalls;
        }
    }

    public record ChampionMatchupIntegrity(
            int missingAssignmentErrors,
            int deadParticipantErrors,
            int nonParticipantErrors,
            int sameTeamPairErrors,
            int crossPositionErrors,
            int duplicateApplicationErrors,
            int staleStateErrors,
            int directRandomCalls,
            int featureOffMismatch,
            int mirrorMismatch
    ) {
        long errorCount() {
            return (long) missingAssignmentErrors + deadParticipantErrors + nonParticipantErrors
                    + sameTeamPairErrors + crossPositionErrors + duplicateApplicationErrors
                    + staleStateErrors + directRandomCalls + featureOffMismatch + mirrorMismatch;
        }
    }

    public record CompositionIntegrity(
            int modeMismatch,
            int initializationMismatch,
            int lineupBuildMismatch,
            int teamAnalysisMismatch,
            int interactionAnalysisMismatch,
            int offModeMutation,
            int attemptAccountingMismatch,
            int observationAccountingMismatch,
            int unmappedActualAttempts,
            int evaluationOnlyObservations,
            int duplicateObservations,
            int multiContextAttempts,
            int conflictingPerspectives,
            int duplicateApplicationPoints,
            int directRandomCalls,
            int compositionRandomDraws,
            int auditSemanticsLeak,
            int keySpecificAuditLeak
    ) {
        long errorCount() {
            return (long) modeMismatch + initializationMismatch + lineupBuildMismatch
                    + teamAnalysisMismatch + interactionAnalysisMismatch + offModeMutation
                    + attemptAccountingMismatch + observationAccountingMismatch
                    + unmappedActualAttempts + evaluationOnlyObservations + duplicateObservations
                    + multiContextAttempts + conflictingPerspectives + duplicateApplicationPoints
                    + directRandomCalls + compositionRandomDraws + auditSemanticsLeak
                    + keySpecificAuditLeak;
        }
    }

    public record CombatOutcomeIntegrity(
            int duplicateOutcomeRecordErrors,
            int outcomeWithoutAttemptErrors,
            int outcomeWithoutWinnerErrors,
            int participantMismatchErrors
    ) {
        long errorCount() {
            return (long) duplicateOutcomeRecordErrors + outcomeWithoutAttemptErrors
                    + outcomeWithoutWinnerErrors + participantMismatchErrors;
        }
    }

    public record ObjectivePriorityIntegrity(
            long priorityAppliedToPostFightError,
            long priorityAppliedToElderError,
            long sameTickGeneralPostFightDuplicate,
            long disabledBonusApplication,
            long disabledMultiplierApplication,
            long wrongSideSign,
            long wrongLaneMultiplier,
            long summaryKillDoubleImpact
    ) {
        long errorCount() {
            return priorityAppliedToPostFightError + priorityAppliedToElderError
                    + sameTickGeneralPostFightDuplicate + disabledBonusApplication
                    + disabledMultiplierApplication + wrongSideSign + wrongLaneMultiplier
                    + summaryKillDoubleImpact;
        }
    }

    public record StructureIntegrity(
            int sameSideMultipleAttemptError,
            int sameSideMultipleMutationError,
            int postFightInternalBlockError
    ) {
        long errorCount() {
            return (long) sameSideMultipleAttemptError + sameSideMultipleMutationError
                    + postFightInternalBlockError;
        }
    }

    public record LanePhaseIntegrity(
            int duplicateLaneTransitions,
            int duplicateMatchTransitions
    ) {
        long errorCount() {
            return (long) duplicateLaneTransitions + duplicateMatchTransitions;
        }
    }

    public record MidGameMacroIntegrity(
            int duplicateStructure,
            int deadAssignmentErrors,
            int combatParticipantAssignmentErrors
    ) {
        long errorCount() {
            return (long) duplicateStructure + deadAssignmentErrors
                    + combatParticipantAssignmentErrors;
        }
    }

    public record AuditMatchRun(
            String schemaVersion,
            String fixtureId,
            Phase13GB1AuditSchedule.FixtureLane fixtureLane,
            Phase13GB1AuditSchedule.SampleLane sampleLane,
            String blueTeamCode,
            String redTeamCode,
            int seriesGameNumber,
            long seed,
            SimulationRuntimeProfileId profileId,
            SimulationGameplayConfiguration resolvedGameplayConfiguration,
            String configurationHash,
            String activeGameplayRulesVersion,
            String engineImplementationVersion,
            String resourceProvenanceHash,
            String rosterIdentityHash,
            String seriesHistoryBeforeHash,
            String draftDecisionHash,
            String finalDraftHash,
            String finalAssignmentHash,
            String replayProvenanceHash,
            String timelineHash,
            @JsonIgnore MatchTimeline timeline,
            SimulationRandomFingerprint randomFingerprint,
            String winnerTeamCode,
            TeamSide winnerSide,
            GameEndReason endReason,
            int durationSeconds,
            int blueKills,
            int redKills,
            int blueGold,
            int redGold,
            int blueDragons,
            int redDragons,
            int blueTowers,
            int redTowers,
            JungleCheckpoint blueJungle,
            JungleCheckpoint redJungle,
            String structuredDiagnosticsHash,
            @JsonIgnore StructuredDiagnostics structuredDiagnostics,
            IntegrityDiagnostics integrityDiagnostics
    ) {
        public AuditMatchRun {
            if (!SCHEMA.equals(schemaVersion)) {
                throw new IllegalArgumentException("Unsupported B1 harness schema");
            }
            Objects.requireNonNull(profileId, "profileId");
            Objects.requireNonNull(resolvedGameplayConfiguration, "resolvedGameplayConfiguration");
            Objects.requireNonNull(timeline, "timeline");
            Objects.requireNonNull(randomFingerprint, "randomFingerprint");
            Objects.requireNonNull(blueJungle, "blueJungle");
            Objects.requireNonNull(redJungle, "redJungle");
            Objects.requireNonNull(structuredDiagnosticsHash, "structuredDiagnosticsHash");
            Objects.requireNonNull(structuredDiagnostics, "structuredDiagnostics");
            Objects.requireNonNull(integrityDiagnostics, "integrityDiagnostics");
            if (structuredDiagnostics.composition().matchSeed() != seed) {
                throw new IllegalArgumentException(
                        "Composition diagnostics differ from the match seed");
            }
            IntegrityDiagnostics expectedIntegrity = IntegrityDiagnostics.from(
                    resolvedGameplayConfiguration, structuredDiagnostics);
            if (!expectedIntegrity.equals(integrityDiagnostics)) {
                throw new IllegalArgumentException(
                        "Integrity diagnostics must be derived from the full structured result");
            }
            String expectedDiagnosticsHash =
                    Phase13GB1SimulationExecutor.structuredDiagnosticsHash(
                            structuredDiagnostics);
            if (!expectedDiagnosticsHash.equals(structuredDiagnosticsHash)) {
                throw new IllegalArgumentException(
                        "Structured diagnostics hash differs from the complete result");
            }
        }

        @JsonProperty
        public CombatExecutionStatsSnapshot combatDiagnostics() {
            return structuredDiagnostics.combat();
        }

        public ProgressionExecutionStatsSnapshot progressionDiagnostics() {
            return structuredDiagnostics.progression();
        }

        @JsonProperty
        public JungleEconomyExecutionStatsSnapshot jungleEconomyDiagnostics() {
            return structuredDiagnostics.jungleEconomy();
        }

        @JsonProperty
        public JungleTempoExecutionStatsSnapshot jungleTempoDiagnostics() {
            return structuredDiagnostics.jungleTempo();
        }
    }
}
