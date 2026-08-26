package com.lolfm.simulator;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionMatchupCatalog;
import com.lolfm.champion.ChampionMatchupExecutionStatsSnapshot;
import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.champion.ChampionJungleClearProfileCatalog;
import com.lolfm.champion.ChampionRoleMatchupProfileCatalog;
import com.lolfm.champion.ChampionResourceSet;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.champion.ChampionPowerProfileCatalog;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.composition.CompositionGameplayConfigurationException;
import com.lolfm.composition.CompositionActionType;
import com.lolfm.composition.CompositionBaselineScoreDomain;
import com.lolfm.composition.FightScale;
import com.lolfm.composition.CompositionRuntimeDiagnostics;
import com.lolfm.composition.CompositionRuntimeState;
import com.lolfm.composition.CompositionCandidateExecutionAuthorization;
import com.lolfm.composition.CompositionLocalDecisionComparison;
import com.lolfm.composition.TeamCompositionContext;
import com.lolfm.composition.FrozenCompositionGameplayGainPolicy;
import com.lolfm.composition.TeamCompositionGameplayMode;
import com.lolfm.composition.*;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.ObjectiveDecisionData;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Player;
import com.lolfm.domain.Team;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MatchSimulator {

    private static final ChampionResourceSet DEFAULT_CHAMPION_RESOURCES = ChampionResourceSet.loadDefault();
    private static final ChampionCatalog DEFAULT_CHAMPION_CATALOG = DEFAULT_CHAMPION_RESOURCES.catalog();
    private static final ChampionPowerProfileCatalog DEFAULT_CHAMPION_POWER_CATALOG = DEFAULT_CHAMPION_RESOURCES.power();
    private static final ChampionMatchupCatalog DEFAULT_CHAMPION_MATCHUP_CATALOG =
            ChampionMatchupCatalog.neutral(DEFAULT_CHAMPION_CATALOG);
    private static final ChampionRoleMatchupProfileCatalog DEFAULT_CHAMPION_MATCHUP_PROFILES =
            DEFAULT_CHAMPION_RESOURCES.matchup();
    private static final ChampionJungleClearProfileCatalog DEFAULT_JUNGLE_CLEAR_PROFILES =
            DEFAULT_CHAMPION_RESOURCES.jungleClear();

    private static final Logger logger = LoggerFactory.getLogger(MatchSimulator.class);
    public static final int SIMULATION_SAFETY_TIMEOUT_SECONDS = 5_400;
    private static final int TICK_SECONDS = 10;
    private static final int STARTING_GOLD = 500;
    private final TeamfightResolver teamfightResolver;
    private final EndGameEvaluator endGameEvaluator;
    private final SnapshotFactory snapshotFactory;
    private final ObjectiveResolver objectiveResolver;
    private final PostFightResolver postFightResolver;
    private final ObjectiveAttemptResolver objectiveAttemptResolver;
    private final StructureResolver structureResolver;
    private final PushResolver pushResolver;
    private final PositionEconomyResolver positionEconomyResolver = new PositionEconomyResolver();
    private final PlayerSkillEvaluator playerSkills = new PlayerSkillEvaluator();
    private final LanePressureResolver lanePressureResolver = new LanePressureResolver();
    private final ObjectivePriorityResolver objectivePriorityResolver = new ObjectivePriorityResolver();
    private final LanePhaseResolver lanePhaseResolver = new LanePhaseResolver();
    private final LaneCombatResolver laneCombatResolver = new LaneCombatResolver();
    private final MidGameMacroResolver midGameMacroResolver = new MidGameMacroResolver();
    private final LateGameMacroResolver lateGameMacroResolver = new LateGameMacroResolver();
    private final JungleGankResolver jungleGankResolver;
    private final RoamResolver roamResolver = new RoamResolver();
    private final GoldAwardService goldAwards = new GoldAwardService();
    private final ProgressionEconomyResolver progressionEconomyResolver = new ProgressionEconomyResolver();
    private final boolean laneCombatEnabled;
    private final boolean farmRecoveryEnabled;
    private final boolean jungleGankEnabled;
    private final boolean counterGankEnabled;
    private final boolean roamEnabled;
    private final boolean diagnosticsEnabled;
    private final boolean objectivePriorityEnabled;
    private final boolean midGameMacroEnabled;
    private final boolean lanePhaseEnabled;
    private final boolean objectiveDecisionEnabled;
    private final boolean lateGameMacroEnabled;
    private final boolean progressionEnabled;
    private final boolean progressionPowerEnabled;
    private final boolean championPowerEnabled;
    private final ChampionMatchupMode championMatchupMode;
    private final ChampionMatchupCatalog championMatchupCatalog;
    private final ChampionRoleMatchupProfileCatalog championMatchupProfiles;
    private final TeamCompositionGameplayMode teamCompositionGameplayMode;
    private final JungleClearContribution jungleClearContribution;
    private final CompositionCandidateExecutionAuthorization candidateExecutionAuthorization;
    private final CompositionSemanticsAuditExecutionAuthorization semanticsAuditAuthorization;
    private final CompositionKeySpecificCandidateAuditAuthorization keySpecificCandidateAuthorization;

    @Autowired
    public MatchSimulator(
            TeamfightResolver teamfightResolver,
            EndGameEvaluator endGameEvaluator,
            SnapshotFactory snapshotFactory,
            ObjectiveResolver objectiveResolver,
            PostFightResolver postFightResolver,
            ObjectiveAttemptResolver objectiveAttemptResolver,
            StructureResolver structureResolver,
            PushResolver pushResolver
    ) {
        this(teamfightResolver, endGameEvaluator, snapshotFactory, objectiveResolver, postFightResolver,
                objectiveAttemptResolver, structureResolver, pushResolver, true, true, true, true);
    }

    MatchSimulator(
            TeamfightResolver teamfightResolver,
            EndGameEvaluator endGameEvaluator,
            SnapshotFactory snapshotFactory,
            ObjectiveResolver objectiveResolver,
            PostFightResolver postFightResolver,
            ObjectiveAttemptResolver objectiveAttemptResolver,
            StructureResolver structureResolver,
            PushResolver pushResolver,
            boolean laneCombatEnabled
    ) {
        this(teamfightResolver, endGameEvaluator, snapshotFactory, objectiveResolver, postFightResolver,
                objectiveAttemptResolver, structureResolver, pushResolver, laneCombatEnabled, true, true, true);
    }

    MatchSimulator(
            TeamfightResolver teamfightResolver,
            EndGameEvaluator endGameEvaluator,
            SnapshotFactory snapshotFactory,
            ObjectiveResolver objectiveResolver,
            PostFightResolver postFightResolver,
            ObjectiveAttemptResolver objectiveAttemptResolver,
            StructureResolver structureResolver,
            PushResolver pushResolver,
            boolean laneCombatEnabled,
            boolean farmRecoveryEnabled
    ) {
        this(teamfightResolver, endGameEvaluator, snapshotFactory, objectiveResolver, postFightResolver,
                objectiveAttemptResolver, structureResolver, pushResolver, laneCombatEnabled, farmRecoveryEnabled, true, true);
    }

    MatchSimulator(
            TeamfightResolver teamfightResolver, EndGameEvaluator endGameEvaluator, SnapshotFactory snapshotFactory,
            ObjectiveResolver objectiveResolver, PostFightResolver postFightResolver,
            ObjectiveAttemptResolver objectiveAttemptResolver, StructureResolver structureResolver, PushResolver pushResolver,
            boolean laneCombatEnabled, boolean farmRecoveryEnabled, boolean jungleGankEnabled
    ) {
        this(teamfightResolver, endGameEvaluator, snapshotFactory, objectiveResolver, postFightResolver,
                objectiveAttemptResolver, structureResolver, pushResolver,
                laneCombatEnabled, farmRecoveryEnabled, jungleGankEnabled, true);
    }

    MatchSimulator(
            TeamfightResolver teamfightResolver, EndGameEvaluator endGameEvaluator, SnapshotFactory snapshotFactory,
            ObjectiveResolver objectiveResolver, PostFightResolver postFightResolver,
            ObjectiveAttemptResolver objectiveAttemptResolver, StructureResolver structureResolver, PushResolver pushResolver,
            boolean laneCombatEnabled, boolean farmRecoveryEnabled, boolean jungleGankEnabled,
            boolean counterGankEnabled
    ) {
        this(teamfightResolver, endGameEvaluator, snapshotFactory, objectiveResolver, postFightResolver,
                objectiveAttemptResolver, structureResolver, pushResolver,
                new SimulationOptions(laneCombatEnabled, farmRecoveryEnabled, jungleGankEnabled,
                        counterGankEnabled, true, true, true, true));
    }

    public MatchSimulator(
            TeamfightResolver teamfightResolver, EndGameEvaluator endGameEvaluator, SnapshotFactory snapshotFactory,
            ObjectiveResolver objectiveResolver, PostFightResolver postFightResolver,
            ObjectiveAttemptResolver objectiveAttemptResolver, StructureResolver structureResolver, PushResolver pushResolver,
            SimulationOptions options
    ) {
        this(teamfightResolver, endGameEvaluator, snapshotFactory, objectiveResolver,
                postFightResolver, objectiveAttemptResolver, structureResolver,
                pushResolver, options, DEFAULT_CHAMPION_MATCHUP_PROFILES);
    }

    public MatchSimulator(
            TeamfightResolver teamfightResolver, EndGameEvaluator endGameEvaluator, SnapshotFactory snapshotFactory,
            ObjectiveResolver objectiveResolver, PostFightResolver postFightResolver,
            ObjectiveAttemptResolver objectiveAttemptResolver, StructureResolver structureResolver, PushResolver pushResolver,
            SimulationOptions options, ChampionMatchupCatalog championMatchupCatalog
    ) {
        this.teamfightResolver = teamfightResolver;
        this.endGameEvaluator = endGameEvaluator;
        this.snapshotFactory = snapshotFactory;
        this.objectiveResolver = objectiveResolver;
        this.postFightResolver = postFightResolver;
        this.objectiveAttemptResolver = objectiveAttemptResolver;
        this.structureResolver = structureResolver;
        this.pushResolver = pushResolver;
        this.laneCombatEnabled = options.laneCombatEnabled();
        this.farmRecoveryEnabled = options.farmRecoveryEnabled();
        this.jungleGankEnabled = options.jungleGankEnabled();
        this.counterGankEnabled = options.counterGankEnabled();
        this.roamEnabled = options.roamEnabled();
        this.diagnosticsEnabled = options.diagnosticsEnabled();
        this.objectivePriorityEnabled = options.objectivePriorityEnabled();
        this.midGameMacroEnabled = options.midGameMacroEnabled();
        this.lanePhaseEnabled = options.lanePhaseEnabled();
        this.objectiveDecisionEnabled = options.objectiveDecisionEnabled();
        this.lateGameMacroEnabled = options.lateGameMacroEnabled();
        this.progressionEnabled = options.progressionEnabled();
        this.progressionPowerEnabled = options.progressionPowerEnabled();
        this.championPowerEnabled = options.championPowerEnabled();
        this.championMatchupMode = options.championMatchupMode();
        this.championMatchupCatalog = java.util.Objects.requireNonNull(
                championMatchupCatalog, "championMatchupCatalog");
        this.championMatchupProfiles = null;
        this.teamCompositionGameplayMode = options.teamCompositionGameplayMode();
        this.jungleClearContribution = options.jungleClearContribution();
        this.candidateExecutionAuthorization = CompositionCandidateExecutionAuthorization.none();
        this.semanticsAuditAuthorization = CompositionSemanticsAuditExecutionAuthorization.none();
        this.keySpecificCandidateAuthorization = CompositionKeySpecificCandidateAuditAuthorization.none();
        this.jungleGankResolver = new JungleGankResolver(counterGankEnabled);
    }

    public MatchSimulator(
            TeamfightResolver teamfightResolver, EndGameEvaluator endGameEvaluator, SnapshotFactory snapshotFactory,
            ObjectiveResolver objectiveResolver, PostFightResolver postFightResolver,
            ObjectiveAttemptResolver objectiveAttemptResolver, StructureResolver structureResolver, PushResolver pushResolver,
            SimulationOptions options, ChampionRoleMatchupProfileCatalog championMatchupProfiles
    ) {
        this(teamfightResolver, endGameEvaluator, snapshotFactory, objectiveResolver, postFightResolver,
                objectiveAttemptResolver, structureResolver, pushResolver, options, championMatchupProfiles,
                CompositionCandidateExecutionAuthorization.none());
    }

    MatchSimulator(
            TeamfightResolver teamfightResolver, EndGameEvaluator endGameEvaluator, SnapshotFactory snapshotFactory,
            ObjectiveResolver objectiveResolver, PostFightResolver postFightResolver,
            ObjectiveAttemptResolver objectiveAttemptResolver, StructureResolver structureResolver, PushResolver pushResolver,
            SimulationOptions options, ChampionRoleMatchupProfileCatalog championMatchupProfiles,
            CompositionCandidateExecutionAuthorization candidateExecutionAuthorization
    ) {
        this(teamfightResolver, endGameEvaluator, snapshotFactory, objectiveResolver, postFightResolver,
                objectiveAttemptResolver, structureResolver, pushResolver, options, championMatchupProfiles,
                candidateExecutionAuthorization, CompositionSemanticsAuditExecutionAuthorization.none());
    }

    MatchSimulator(
            TeamfightResolver teamfightResolver, EndGameEvaluator endGameEvaluator, SnapshotFactory snapshotFactory,
            ObjectiveResolver objectiveResolver, PostFightResolver postFightResolver,
            ObjectiveAttemptResolver objectiveAttemptResolver, StructureResolver structureResolver, PushResolver pushResolver,
            SimulationOptions options, ChampionRoleMatchupProfileCatalog championMatchupProfiles,
            CompositionCandidateExecutionAuthorization candidateExecutionAuthorization,
            CompositionSemanticsAuditExecutionAuthorization semanticsAuditAuthorization
    ) {
        this.teamfightResolver = teamfightResolver;
        this.endGameEvaluator = endGameEvaluator;
        this.snapshotFactory = snapshotFactory;
        this.objectiveResolver = objectiveResolver;
        this.postFightResolver = postFightResolver;
        this.objectiveAttemptResolver = objectiveAttemptResolver;
        this.structureResolver = structureResolver;
        this.pushResolver = pushResolver;
        this.laneCombatEnabled = options.laneCombatEnabled();
        this.farmRecoveryEnabled = options.farmRecoveryEnabled();
        this.jungleGankEnabled = options.jungleGankEnabled();
        this.counterGankEnabled = options.counterGankEnabled();
        this.roamEnabled = options.roamEnabled();
        this.diagnosticsEnabled = options.diagnosticsEnabled();
        this.objectivePriorityEnabled = options.objectivePriorityEnabled();
        this.midGameMacroEnabled = options.midGameMacroEnabled();
        this.lanePhaseEnabled = options.lanePhaseEnabled();
        this.objectiveDecisionEnabled = options.objectiveDecisionEnabled();
        this.lateGameMacroEnabled = options.lateGameMacroEnabled();
        this.progressionEnabled = options.progressionEnabled();
        this.progressionPowerEnabled = options.progressionPowerEnabled();
        this.championPowerEnabled = options.championPowerEnabled();
        this.championMatchupMode = options.championMatchupMode();
        this.championMatchupProfiles = java.util.Objects.requireNonNull(championMatchupProfiles, "championMatchupProfiles");
        this.championMatchupCatalog = null;
        this.teamCompositionGameplayMode = options.teamCompositionGameplayMode();
        this.jungleClearContribution = options.jungleClearContribution();
        this.candidateExecutionAuthorization = java.util.Objects.requireNonNull(candidateExecutionAuthorization, "candidateExecutionAuthorization");
        this.semanticsAuditAuthorization = java.util.Objects.requireNonNull(semanticsAuditAuthorization, "semanticsAuditAuthorization");
        this.jungleGankResolver = new JungleGankResolver(counterGankEnabled);
        this.keySpecificCandidateAuthorization = CompositionKeySpecificCandidateAuditAuthorization.none();
    }

    MatchSimulator(
            TeamfightResolver teamfightResolver, EndGameEvaluator endGameEvaluator, SnapshotFactory snapshotFactory,
            ObjectiveResolver objectiveResolver, PostFightResolver postFightResolver,
            ObjectiveAttemptResolver objectiveAttemptResolver, StructureResolver structureResolver, PushResolver pushResolver,
            SimulationOptions options, ChampionRoleMatchupProfileCatalog championMatchupProfiles,
            CompositionCandidateExecutionAuthorization candidateExecutionAuthorization,
            CompositionSemanticsAuditExecutionAuthorization semanticsAuditAuthorization,
            CompositionKeySpecificCandidateAuditAuthorization keySpecificCandidateAuthorization
    ) {
        this.teamfightResolver = teamfightResolver;
        this.endGameEvaluator = endGameEvaluator;
        this.snapshotFactory = snapshotFactory;
        this.objectiveResolver = objectiveResolver;
        this.postFightResolver = postFightResolver;
        this.objectiveAttemptResolver = objectiveAttemptResolver;
        this.structureResolver = structureResolver;
        this.pushResolver = pushResolver;
        this.laneCombatEnabled = options.laneCombatEnabled();
        this.farmRecoveryEnabled = options.farmRecoveryEnabled();
        this.jungleGankEnabled = options.jungleGankEnabled();
        this.counterGankEnabled = options.counterGankEnabled();
        this.roamEnabled = options.roamEnabled();
        this.diagnosticsEnabled = options.diagnosticsEnabled();
        this.objectivePriorityEnabled = options.objectivePriorityEnabled();
        this.midGameMacroEnabled = options.midGameMacroEnabled();
        this.lanePhaseEnabled = options.lanePhaseEnabled();
        this.objectiveDecisionEnabled = options.objectiveDecisionEnabled();
        this.lateGameMacroEnabled = options.lateGameMacroEnabled();
        this.progressionEnabled = options.progressionEnabled();
        this.progressionPowerEnabled = options.progressionPowerEnabled();
        this.championPowerEnabled = options.championPowerEnabled();
        this.championMatchupMode = options.championMatchupMode();
        this.championMatchupProfiles = java.util.Objects.requireNonNull(championMatchupProfiles, "championMatchupProfiles");
        this.championMatchupCatalog = null;
        this.teamCompositionGameplayMode = options.teamCompositionGameplayMode();
        this.jungleClearContribution = options.jungleClearContribution();
        this.candidateExecutionAuthorization = java.util.Objects.requireNonNull(candidateExecutionAuthorization, "candidateExecutionAuthorization");
        this.semanticsAuditAuthorization = java.util.Objects.requireNonNull(semanticsAuditAuthorization, "semanticsAuditAuthorization");
        this.keySpecificCandidateAuthorization = java.util.Objects.requireNonNull(keySpecificCandidateAuthorization, "keySpecificCandidateAuthorization");
        this.jungleGankResolver = new JungleGankResolver(counterGankEnabled);
    }

    public MatchTimeline simulate(Team blueTeam, Team redTeam, long seed) {
        return simulate(blueTeam, redTeam, seed,
                new ChampionSelectionValidator(DEFAULT_CHAMPION_CATALOG).resolve(null));
    }

    public MatchTimeline simulate(Team blueTeam, Team redTeam, long seed, MatchChampionAssignments assignments) {
        return runSimulation(blueTeam, redTeam, seed, assignments).timeline();
    }

    /**
     * Runs the same seeded gameplay while exposing observational Random consumption identity.
     * The observer delegates every draw to {@link Random} and does not request draws itself.
     */
    public ObservedMatchSimulation simulateObserved(
            Team blueTeam,
            Team redTeam,
            long seed,
            MatchChampionAssignments assignments
    ) {
        StructuredMatchSimulationOutcome outcome = simulateStructuredObserved(
                blueTeam, redTeam, seed, assignments);
        return new ObservedMatchSimulation(outcome.timeline(), outcome.randomFingerprint());
    }

    /**
     * Runs the same seeded gameplay and exposes structured terminal facts for application
     * boundaries. Validation completes before the seeded Random observer is created.
     */
    public StructuredMatchSimulationOutcome simulateStructuredObserved(
            Team blueTeam,
            Team redTeam,
            long seed,
            MatchChampionAssignments assignments
    ) {
        MatchLineupIdentityValidator.validate(blueTeam, redTeam);
        SideOrientationRandomTraceObserver random = new SideOrientationRandomTraceObserver(
                seed, "RUNTIME", blueTeam.getName(), redTeam.getName(), false);
        SimulationResult result = runValidatedSimulation(
                blueTeam, redTeam, assignments, random, seed);
        return new StructuredMatchSimulationOutcome(
                result.timeline(), result.winnerSide(), result.endReason(),
                result.timeline().getDurationSeconds(), random.fingerprint(),
                result.playerMatchPerformances());
    }

    SimulationResult simulateWithDiagnostics(Team blueTeam, Team redTeam, long seed) {
        return runSimulation(blueTeam, redTeam, seed,
                new ChampionSelectionValidator(DEFAULT_CHAMPION_CATALOG).resolve(null));
    }

    SimulationResult simulateWithDiagnostics(Team blueTeam, Team redTeam, long seed,
                                             MatchChampionAssignments assignments) {
        return runSimulation(blueTeam, redTeam, seed, assignments);
    }

    SimulationResult simulateWithSideDiagnostics(
            Team blueTeam,
            Team redTeam,
            MatchChampionAssignments assignments,
            SideOrientationRandomTraceObserver random
    ) {
        MatchLineupIdentityValidator.validate(blueTeam, redTeam);
        return runValidatedSimulation(blueTeam, redTeam, assignments, random, random.seed());
    }

    private SimulationResult runSimulation(Team blueTeam, Team redTeam, long seed, MatchChampionAssignments assignments) {
        MatchLineupIdentityValidator.validate(blueTeam, redTeam);
        return runValidatedSimulation(blueTeam, redTeam, assignments, new Random(seed), seed);
    }

    private SimulationResult runValidatedSimulation(
            Team blueTeam,
            Team redTeam,
            MatchChampionAssignments assignments,
            Random random,
            long seed
    ) {
        validateCompositionModeBeforeMatch();
        GameState gameState = initializeGameState(blueTeam, redTeam, assignments, seed);
        gameState.configureCompositionRuntime(new CompositionRuntimeState(teamCompositionGameplayMode, seed,
                candidateExecutionAuthorization, semanticsAuditAuthorization, keySpecificCandidateAuthorization));
        gameState.getCompositionRuntimeState().initialize(assignments);
        gameState.configureProgression(progressionEnabled, progressionPowerEnabled);
        gameState.getBlueTeamState().validateCompleteLineup();
        gameState.getRedTeamState().validateCompleteLineup();
        List<MatchEvent> events = new ArrayList<>();
        List<MatchSnapshot> snapshots = new ArrayList<>();
        EndGameEvaluator.EndGameDecision endGameDecision = EndGameEvaluator.EndGameDecision.continueGame();

        events.add(new MatchEvent(
                0,
                MatchEventType.GAME_START,
                "소환사의 협곡에 입장했습니다. LoL FM 매치가 시작됩니다.",
                null,
                null,
                List.of()
        ));
        snapshots.add(snapshotFactory.create(gameState));

        while (!gameState.isFinished()
                && gameState.getCurrentTimeSeconds() < SIMULATION_SAFETY_TIMEOUT_SECONDS) {
            gameState.advanceTimeSeconds(Math.min(
                    TICK_SECONDS,
                    SIMULATION_SAFETY_TIMEOUT_SECONDS - gameState.getCurrentTimeSeconds()
            ));
            gameState.expireBaronBuffsIfNeeded();
            midGameMacroResolver.expirePlans(gameState);
            lateGameMacroResolver.expirePlans(gameState);
            gameState.clearMajorCombatParticipantsThisTick();
            gameState.clearStructureActionRegistryThisTick();
            structureResolver.addLifecycleEvents(gameState, events);
            objectivePriorityResolver.decayRecentControl(gameState, gameState.getCurrentTimeSeconds());
            boolean blueEconomy = awardPassiveForTick(gameState.getBlueTeamState(), gameState.getCurrentTimeSeconds());
            boolean redEconomy = awardPassiveForTick(gameState.getRedTeamState(), gameState.getCurrentTimeSeconds());
            randomContext(random, SideOrientationRandomTraceObserver.Source.LANE_PRESSURE, null, gameState);
            lanePressureResolver.resolve(gameState, gameState.getCurrentTimeSeconds(), random);
            randomContext(random, SideOrientationRandomTraceObserver.Source.ECONOMY, TeamSide.BLUE, gameState);
            resolveFarmForTick(random, gameState, gameState.getBlueTeamState(), TeamSide.BLUE,
                    TICK_SECONDS, gameState.getCurrentTimeSeconds(), blueEconomy);
            randomContext(random, SideOrientationRandomTraceObserver.Source.ECONOMY, TeamSide.RED, gameState);
            resolveFarmForTick(random, gameState, gameState.getRedTeamState(), TeamSide.RED,
                    TICK_SECONDS, gameState.getCurrentTimeSeconds(), redEconomy);
            if (blueEconomy && redEconomy) progressionEconomyResolver.resolve(gameState, gameState.getCurrentTimeSeconds());
            gameState.drainProgressionEvents(events);
            boolean roamEvaluationDue = roamEnabled
                    && gameState.shouldResolveRoamAt(gameState.getCurrentTimeSeconds());
            randomContext(random, SideOrientationRandomTraceObserver.Source.JUNGLE_GANK, null, gameState);
            boolean jungleGankAttempted = jungleGankEnabled && jungleGankResolver.resolve(gameState, random, events);
            if (jungleGankAttempted && roamEvaluationDue) gameState.getRoamExecutionStats().recordSkippedByHigherPriority();
            int roamEvaluationBefore = gameState.getLastRoamEvaluationAtSeconds();
            randomContext(random, SideOrientationRandomTraceObserver.Source.ROAM, null, gameState);
            boolean roamAttempted = !jungleGankAttempted && roamEnabled
                    && roamResolver.resolve(gameState, random, events);
            boolean roamEvaluated = gameState.getLastRoamEvaluationAtSeconds() != roamEvaluationBefore;
            boolean laneCombatConsidered = !jungleGankAttempted && !roamAttempted && laneCombatEnabled;
            randomContext(random, SideOrientationRandomTraceObserver.Source.LANE_COMBAT, null, gameState);
            boolean laneCombatAttempted = laneCombatConsidered
                    && laneCombatResolver.resolve(gameState, random, events);
            if (roamAttempted) {
                if (laneCombatEnabled) gameState.getRoamExecutionStats().recordBlockedLaneCombat();
                gameState.getRoamExecutionStats().recordBlockedGeneric();
            } else if (roamEvaluated && laneCombatConsidered) {
                gameState.getRoamExecutionStats().recordFallthroughToLaneCombat();
            }
            boolean majorCombatAttempted = jungleGankAttempted || roamAttempted || laneCombatAttempted;
            objectiveResolver.updateSpawnState(gameState);
            boolean genericCombatAttempted = false;
            if (!majorCombatAttempted) {
                gameState.getCombatExecutionStats().recordGenericSkirmishCall(gameState.getCurrentTimeSeconds());
                randomContext(random, SideOrientationRandomTraceObserver.Source.GENERIC_SKIRMISH, null, gameState);
                genericCombatAttempted = maybeCreateKillEvent(random, blueTeam, redTeam, gameState, events);
                if (genericCombatAttempted) gameState.getCombatExecutionStats().recordGenericSkirmishKill(gameState.getCurrentTimeSeconds());
            }
            randomContext(random, SideOrientationRandomTraceObserver.Source.TEAMFIGHT, null, gameState);
            Optional<TeamfightOutcome> outcome = (majorCombatAttempted || genericCombatAttempted)
                    ? Optional.empty() : teamfightResolver.maybeResolveTeamfight(gameState, blueTeam, redTeam, random, events);
            outcome.ifPresent(result -> objectivePriorityResolver.applyTeamfightWin(
                    gameState, gameState.getCurrentTimeSeconds(), result));
            randomContext(random, SideOrientationRandomTraceObserver.Source.STRUCTURE_PUSH, null, gameState);
            structureResolver.resolveActiveSieges(gameState, events);
            lanePhaseResolver.resolveOuterSieges(
                    gameState, gameState.getCurrentTimeSeconds(), random, structureResolver, events);
            randomContext(random, SideOrientationRandomTraceObserver.Source.OBJECTIVE_CAPTURE, null, gameState);
            Optional<MatchEvent> postFightObjective = outcome.flatMap(result -> postFightResolver.resolve(
                    gameState, result, random, objectiveResolver));
            postFightObjective.ifPresent(event -> { events.add(event); cancelMacroSetupForCapture(gameState, event); });
            boolean postFightSideAlreadyActed = postFightObjective.isPresent() || outcome
                    .map(result -> gameState.wasStructureActionPerformedThisTick(result.winningSide()))
                    .orElse(false);
            randomContext(random, SideOrientationRandomTraceObserver.Source.STRUCTURE_PUSH, null, gameState);
            List<StructureOutcome> postFightStructures = postFightSideAlreadyActed
                    ? List.of()
                    : pushResolver.resolvePostFightWindow(
                            gameState, outcome, postFightObjective, random, structureResolver, events);
            if (!gameState.isFinished() && postFightObjective.isEmpty()) {
                randomContext(random, SideOrientationRandomTraceObserver.Source.OBJECTIVE_FIGHT, null, gameState);
                Optional<MatchEvent> generalObjective = objectiveAttemptResolver.maybeAttemptObjective(gameState, random, objectiveResolver, structureResolver, events);
                generalObjective.ifPresent(event -> { if (!events.contains(event)) events.add(event); cancelMacroSetupForCapture(gameState, event); });
            }
            Optional<MatchEvent> phaseTransition = lanePhaseResolver.transitionIfDue(gameState);
            phaseTransition.ifPresent(events::add);
            if (phaseTransition.isPresent()) midGameMacroResolver.onPhaseTransition(gameState);
            Optional<MatchEvent> lateTransition = lateGameMacroResolver.transitionIfDue(gameState, midGameMacroResolver);
            lateTransition.ifPresent(events::add);
            randomContext(random, SideOrientationRandomTraceObserver.Source.MIDGAME_MACRO, null, gameState);
            midGameMacroResolver.resolveDueEvaluation(gameState, random, events, structureResolver);
            randomContext(random, SideOrientationRandomTraceObserver.Source.LATE_GAME_SIEGE, null, gameState);
            lateGameMacroResolver.resolveDue(gameState, blueTeam, redTeam, random, events, structureResolver, teamfightResolver);
            if (!gameState.isFinished()) {
                randomContext(random, SideOrientationRandomTraceObserver.Source.STRUCTURE_PUSH, null, gameState);
                pushResolver.maybeResolveMacroPush(gameState, random, structureResolver, events);
            }
            endGameDecision = endGameEvaluator.evaluateAfterTick(gameState);
            if (endGameDecision.isFinished()) { midGameMacroResolver.onMatchFinished(gameState); lateGameMacroResolver.onMatchFinished(gameState); }
            gameState.drainProgressionEvents(events);
            snapshots.add(snapshotFactory.create(gameState));
        }

        if (!endGameDecision.isFinished()) {
            throw new IllegalStateException("Simulation loop exited without a terminal state for seed " + seed);
        }
        if (endGameDecision.getReason() == GameEndReason.SIMULATION_TIMEOUT) {
            logSimulationTimeout(seed, gameState);
        }
        events.add(new MatchEvent(
                gameState.getCurrentTimeSeconds(),
                MatchEventType.GAME_END,
                endGameEvaluator.buildGameEndMessage(endGameDecision),
                null,
                null,
                List.of()
        ));

        events.sort(java.util.Comparator.comparingInt(MatchEvent::getTimeSeconds));
        gameState.getCompositionRuntimeState().reconcilePublicActionOrdinals(events);
        if (events.stream().anyMatch(event -> event.getTimeSeconds()
                > gameState.getCurrentTimeSeconds())) {
            throw new IllegalStateException("Timeline event exceeds terminal match time for seed " + seed);
        }

        MatchTimeline timeline = new MatchTimeline(
                gameState.getCurrentTimeSeconds(), endGameDecision.getWinner(), events, snapshots
        );
        return new SimulationResult(
                timeline,
                gameState.getPushAttemptCount(),
                gameState.getPushSuccessCount(),
                gameState.getPushFailureCounts(),
                gameState.getObjectiveState().getSoulOwner(),
                gameState.getObjectiveState().getSoulClaimedAtSeconds(),
                gameState.getDragonCaptureTimes(),
                gameState.getDragonSpawnAliveSeconds(),
                gameState.getGeneralDragonAttemptCount(),
                gameState.getGeneralDragonCaptureCount(),
                gameState.getPostFightDragonCaptureCount(),
                gameState.getDragonCaptures(),
                gameState.getPushWindowCount(),
                gameState.getPushWindowStructureCount(),
                gameState.getAceWindowNexusEndCount(),
                gameState.getEndReason(),
                gameState.getBlueTeamState().getDuplicateEconomyResolutionCount()
                        + gameState.getRedTeamState().getDuplicateEconomyResolutionCount(),
                gameState.getCombatExecutionStats().snapshot(),
                gameState.getRoamExecutionStats().snapshot(),
                gameState.getWinnerSide(),
                gameState.getObjectivePriorityExecutionStats().snapshot(),
                gameState.getLanePhaseExecutionStats().snapshot(),
                gameState.getMidGameMacroState().getExecutionStats().snapshot(),
                gameState.getObjectiveDecisionState().getStats().snapshot(),
                gameState.getObjectiveDecisionState().getHistory(),
                gameState.getStructureActionExecutionStats().snapshot(),
                gameState.getProgressionExecutionStats().snapshot(),
                gameState.getJungleEconomyExecutionStats().snapshot(),
                gameState.getJungleTempoExecutionStats().snapshot(
                        gameState.getJungleTempoStates()),
                gameState.getChampionPowerExecutionStats().snapshot(),
                gameState.getChampionMatchupExecutionStats().snapshot(),
                gameState.getCombatOutcomeExecutionStats().snapshot(),
                random instanceof SideOrientationRandomTraceObserver observer ? observer.drawCount() : 0L,
                random instanceof SideOrientationRandomTraceObserver observer
                        ? observer.traceHash() : null,
                random instanceof SideOrientationRandomTraceObserver observer ? observer.trace() : List.of(),
                gameState.getCompositionRuntimeState().snapshot(),
                playerMatchPerformanceSnapshots(gameState)
        );
    }

    record SimulationResult(
            MatchTimeline timeline,
            int pushAttempts,
            int pushSuccesses,
            Map<PushFailureReason, Integer> pushFailureCounts,
            TeamSide soulOwner,
            int soulClaimedAtSeconds,
            List<Integer> dragonCaptureTimes,
            List<Integer> dragonSpawnAliveSeconds,
            int generalDragonAttemptCount,
            int generalDragonCaptureCount,
            int postFightDragonCaptureCount,
            List<DragonCaptureRecord> dragonCaptures,
            int pushWindowCount,
            int pushWindowStructureCount,
            int aceWindowNexusEndCount,
            GameEndReason endReason,
            int duplicateEconomyResolutions,
            CombatExecutionStatsSnapshot combatExecutionStats,
            RoamExecutionStatsSnapshot roamExecutionStats,
            TeamSide winnerSide,
            ObjectivePriorityExecutionStatsSnapshot objectivePriorityExecutionStats,
            LanePhaseExecutionStatsSnapshot lanePhaseExecutionStats,
            MidGameMacroExecutionStatsSnapshot midGameMacroExecutionStats,
            ObjectiveDecisionExecutionStatsSnapshot objectiveDecisionExecutionStats,
            List<ObjectiveDecisionData> objectiveDecisionHistory,
            StructureActionExecutionStatsSnapshot structureActionExecutionStats,
            ProgressionExecutionStatsSnapshot progressionExecutionStats,
            JungleEconomyExecutionStatsSnapshot jungleEconomyExecutionStats,
            JungleTempoExecutionStatsSnapshot jungleTempoExecutionStats,
            com.lolfm.champion.ChampionPowerExecutionStatsSnapshot championPowerExecutionStats,
            ChampionMatchupExecutionStatsSnapshot championMatchupExecutionStats,
            CombatOutcomeExecutionStatsSnapshot combatOutcomeExecutionStats,
            long randomDrawCount,
            String randomTraceHash,
            List<SideOrientationRandomTraceObserver.Draw> randomTrace,
            CompositionRuntimeDiagnostics compositionRuntimeDiagnostics,
            List<PlayerMatchPerformanceSnapshot> playerMatchPerformances
    ) {
        SimulationResult {
            playerMatchPerformances = List.copyOf(playerMatchPerformances);
        }
    }

    private List<PlayerMatchPerformanceSnapshot> playerMatchPerformanceSnapshots(GameState state) {
        List<PlayerMatchPerformanceSnapshot> snapshots = new ArrayList<>();
        for (TeamSide side : TeamSide.values()) {
            for (PlayerState player : state.getTeamState(side).getPlayers()) {
                if (!player.hasMatchPerformance()) continue;
                if (player.getPlayerKey() == null || player.getPlayerKey().side() != side) {
                    throw new IllegalStateException("Detailed player is missing match-scoped identity");
                }
                PlayerMatchPerformance performance = player.getMatchPerformance();
                snapshots.add(new PlayerMatchPerformanceSnapshot(
                        player.getPlayerKey(), performance.asMap(),
                        performance.championProficiency()));
            }
        }
        return List.copyOf(snapshots);
    }

    private void validateCompositionModeBeforeMatch() {
        if (semanticsAuditAuthorization.enabled()) {
        if (keySpecificCandidateAuthorization.enabled()) {
            if (teamCompositionGameplayMode != TeamCompositionGameplayMode.SHADOW || !semanticsAuditAuthorization.enabled()) {
                throw new CompositionGameplayConfigurationException("COMPOSITION_KEY_SPECIFIC_CANDIDATE_NOT_AUTHORIZED",
                        "Key-specific candidate requires SHADOW semantics audit mode");
            }
            keySpecificCandidateAuthorization.verifyExact();
        }

            if (teamCompositionGameplayMode != TeamCompositionGameplayMode.SHADOW
                    || candidateExecutionAuthorization.auditOnly()) {
                throw new CompositionGameplayConfigurationException(
                        "COMPOSITION_HISTORICAL_CANDIDATE_AND_AUDIT_PATH_MIXED",
                        "Isolated semantics audit cannot mix with the historical candidate path");
            }
            semanticsAuditAuthorization.verifyExact();
        }
        if (teamCompositionGameplayMode == TeamCompositionGameplayMode.PRODUCTION_V2) {
            if (candidateExecutionAuthorization.auditOnly()
                    || semanticsAuditAuthorization.enabled()
                    || keySpecificCandidateAuthorization.enabled()) {
                throw new CompositionGameplayConfigurationException(
                        "COMPOSITION_PRODUCTION_AUTHORIZATION_MIXED",
                        "Frozen V2 production execution cannot mix with candidate or audit authorization");
            }
            FrozenCompositionProductionCandidate.verifyExact();
            return;
        }
        if (teamCompositionGameplayMode != TeamCompositionGameplayMode.CANDIDATE) return;
        FrozenCompositionGameplayGainPolicy policy = FrozenCompositionGameplayGainPolicy.current();
        if (!candidateExecutionAuthorization.auditOnly()) throw new CompositionGameplayConfigurationException(
                "CANDIDATE_CONTEXT_GAINS_NOT_APPROVED",
                "Composition candidate gameplay gains are not approved for this phase");
        if (!candidateExecutionAuthorization.exactFor(policy)) throw new CompositionGameplayConfigurationException(
                "CANDIDATE_GAIN_POLICY_IDENTITY_MISMATCH",
                "Composition candidate gameplay gain identity does not match the frozen policy");
    }

    private void randomContext(
            Random random,
            SideOrientationRandomTraceObserver.Source source,
            TeamSide side,
            GameState state
    ) {
        if (random instanceof SideOrientationRandomTraceObserver observer) {
            observer.context(source, side, state.getCurrentTimeSeconds());
        }
    }

    private void logSimulationTimeout(long seed, GameState state) {
        MapState map = state.getMapState();
        logger.warn(
                "Simulation timeout: seed={}, blueInhibitors={}, redInhibitors={}, blueNexusTurrets={}, redNexusTurrets={}, blueNexusAlive={}, redNexusAlive={}, pushAttempts={}, pushSuccesses={}, pushFailures={}",
                seed,
                map.getAliveInhibitorCount(TeamSide.BLUE),
                map.getAliveInhibitorCount(TeamSide.RED),
                map.getBaseState(TeamSide.BLUE).getNexusTurretsRemaining(),
                map.getBaseState(TeamSide.RED).getNexusTurretsRemaining(),
                map.getBaseState(TeamSide.BLUE).isNexusAlive(),
                map.getBaseState(TeamSide.RED).isNexusAlive(),
                state.getPushAttemptCount(),
                state.getPushSuccessCount(),
                state.getPushFailureCounts()
        );
    }

    private void cancelMacroSetupForCapture(GameState state, MatchEvent event) {
        switch (event.getType()) {
            case DRAGON -> midGameMacroResolver.cancelSetupForObjective(state, ObjectiveType.DRAGON);
            case BARON -> midGameMacroResolver.cancelSetupForObjective(state, ObjectiveType.BARON);
            default -> { }
        }
    }
    private GameState initializeGameState(Team blueTeam, Team redTeam, MatchChampionAssignments assignments,
                                          long seed) {
        GameState state = new GameState(buildTeamState(blueTeam, TeamSide.BLUE, assignments, seed),
                buildTeamState(redTeam, TeamSide.RED, assignments, seed), diagnosticsEnabled,
                objectivePriorityEnabled, lanePhaseEnabled, midGameMacroEnabled, objectiveDecisionEnabled,
                lateGameMacroEnabled, assignments);
        state.configureChampionPower(DEFAULT_CHAMPION_POWER_CATALOG, championPowerEnabled);
        state.configureJungleEconomy(DEFAULT_JUNGLE_CLEAR_PROFILES, jungleClearContribution);
        if (championMatchupMode == ChampionMatchupMode.GEOMETRIC_V2) {
            ChampionRoleMatchupProfileCatalog profiles = championMatchupProfiles == null
                    ? DEFAULT_CHAMPION_MATCHUP_PROFILES : championMatchupProfiles;
            profiles.validateCoverage(assignments);
            state.configureChampionMatchup(profiles, championMatchupMode);
        } else {
            state.configureChampionMatchup(championMatchupCatalog == null
                    ? DEFAULT_CHAMPION_MATCHUP_CATALOG : championMatchupCatalog, championMatchupMode);
        }
        return state;
    }

    private TeamState buildTeamState(Team team, TeamSide side, MatchChampionAssignments assignments, long seed) {
        List<PlayerState> states = new ArrayList<>();
        for (Player player : team.getPlayers()) {
            PlayerKey playerKey = new PlayerKey(side, player.getPosition());
            if (player.isLegacyProfile()) {
                states.add(new PlayerState(playerKey, player.getPlayerId(), player.getName(),
                        player.getPosition(), player.getAttributes(), null, STARTING_GOLD,
                        farmRecoveryEnabled));
                continue;
            }
            com.lolfm.champion.ChampionRoleKey championRoleKey =
                    new com.lolfm.champion.ChampionRoleKey(
                            assignments.get(playerKey).championId(), player.getPosition());
            int proficiency = player.getChampionProficiencies().get(championRoleKey);
            PlayerMatchPerformance performance = PlayerMatchPerformance.realize(
                    player.getRatings(), proficiency, seed, side);
            states.add(new PlayerState(playerKey, player.getPlayerId(), player.getName(),
                    player.getPosition(), player.getAttributes(), performance, STARTING_GOLD,
                    farmRecoveryEnabled));
        }
        return new TeamState(team.getName(), states);
    }

    void applyTickEconomy(Random random, TeamState state, int elapsedSeconds, int currentTime) {
        applyTickEconomy(random, null, state, null, elapsedSeconds, currentTime);
    }

    void applyTickEconomy(Random random, GameState gameState, TeamState state, TeamSide side, int elapsedSeconds, int currentTime) {
        boolean economyStarted = awardPassiveForTick(state, currentTime);
        resolveFarmForTick(random, gameState, state, side, elapsedSeconds, currentTime, economyStarted);
    }

    private boolean awardPassiveForTick(TeamState state, int currentTime) {
        if (!state.shouldResolveEconomyAt(currentTime)) return false;
        if (currentTime < PositionEconomyRuleConfig.ECONOMY_START_SECONDS) return false;
        for (PlayerState player : state.getPlayers()) {
            goldAwards.awardGold(state, player,
                    PositionEconomyRuleConfig.passiveGoldPerTick(player.getPosition()),
                    GoldSource.PASSIVE, false, currentTime);
        }
        return true;
    }

    private void resolveFarmForTick(Random random, GameState gameState, TeamState state, TeamSide side,
                                    int elapsedSeconds, int currentTime, boolean economyStarted) {
        if (!economyStarted) return;
        positionEconomyResolver.resolve(gameState, state, side, currentTime, elapsedSeconds, random);
        state.markEconomyResolvedAt(currentTime);
    }

    boolean maybeCreateKillEvent(
            Random random,
            Team blueTeam,
            Team redTeam,
            GameState state,
            List<MatchEvent> events
    ) {
        List<Lane> eligibleLanes = eligibleLocalizedSkirmishLanes(state);
        if (eligibleLanes.isEmpty()) return false;
        double chance = genericSkirmishChance(state);
        if (random.nextDouble() >= chance) return false;
        TeamSelection attacking = chooseTeamForSkirmish(random, blueTeam, redTeam, state);
        Lane combatLane = eligibleLanes.get(random.nextInt(eligibleLanes.size()));
        state.getCompositionRuntimeState().recordActualAttempt(
                CompositionActionType.SKIRMISH,
                attacking.actingState() == state.getBlueTeamState() ? TeamSide.BLUE : TeamSide.RED,
                attacking.actingState() == state.getBlueTeamState() ? TeamSide.BLUE : TeamSide.RED,
                attacking.actingState() == state.getBlueTeamState() ? TeamSide.RED : TeamSide.BLUE,
                FightScale.SMALL, null, false, null, null, state.getCurrentTimeSeconds(),
                CompositionBaselineScoreDomain.SKIRMISH_COMBAT_SCORE,
                skirmishInitiative(state, attacking.actingState() == state.getBlueTeamState() ? TeamSide.BLUE : TeamSide.RED),
                skirmishInitiative(state, attacking.actingState() == state.getBlueTeamState() ? TeamSide.RED : TeamSide.BLUE));
        if (state.getCompositionRuntimeState().isAuditSemantics()) {
            TeamSide winner = attacking.actingState() == state.getBlueTeamState() ? TeamSide.BLUE : TeamSide.RED;
            state.getCompositionRuntimeState().recordAuditWinnerObservation(
                    state.getCompositionRuntimeState().lastActualAttemptId(), state.getCurrentTimeSeconds(),
                    attacking.auditAdjustment(), TeamSide.BLUE, null, null,
                    attacking.localDecision().candidateScore(), attacking.localDecision().baselineScore(), attacking.localDecision().sample(),
                    attacking.localDecision().sampleIdentity(), winner);
        }
        if (state.getCompositionRuntimeState().isAuditSemantics()
                || state.getCompositionRuntimeState().isProductionV2()) {
            TeamSide winner = attacking.actingState() == state.getBlueTeamState() ? TeamSide.BLUE : TeamSide.RED;
            recordSkirmishDecisionProvenance(state, attacking, winner);
        }
        if (state.getCompositionRuntimeState().isCandidate() && attacking.localDecision() != null) {
            TeamSide perspective = attacking.actingState() == state.getBlueTeamState() ? TeamSide.BLUE : TeamSide.RED;
            boolean perspectiveWasBlue = perspective == TeamSide.BLUE;
            double baselinePerspective = perspectiveWasBlue
                    ? attacking.localDecision().baselineScore() : 1.0 - attacking.localDecision().baselineScore();
            double baselineOpponent = perspectiveWasBlue
                    ? 1.0 - attacking.localDecision().baselineScore() : attacking.localDecision().baselineScore();
            double adjustedPerspective = perspectiveWasBlue
                    ? attacking.localDecision().candidateScore() : 1.0 - attacking.localDecision().candidateScore();
            double adjustedOpponent = perspectiveWasBlue
                    ? 1.0 - attacking.localDecision().candidateScore() : attacking.localDecision().candidateScore();
            String applicationKey = "SKIRMISH|SKIRMISH|SKIRMISH_COMBAT_SCORE";
            String band = FrozenCompositionGameplayGainPolicy.marginBand(applicationKey,
                    baselinePerspective - baselineOpponent);
            state.getCompositionRuntimeState().recordLocalDecisionComparison(new CompositionLocalDecisionComparison(
                    state.getCompositionRuntimeState().matchSeed(),
                    state.getCompositionRuntimeState().lastActualAttemptId(),
                    state.getCurrentTimeSeconds(), applicationKey,
                    "WEIGHTED_INITIATIVE_SIDE_SELECTION", perspective,
                    attacking.localDecision().sampleIdentity(), attacking.localDecision().sample(),
                    baselinePerspective, baselineOpponent, adjustedPerspective, adjustedOpponent,
                    attacking.localDecision().baselineDecision(), attacking.localDecision().candidateDecision(),
                    !attacking.localDecision().baselineDecision().equals(attacking.localDecision().candidateDecision()),
                    band, false,
                    !attacking.localDecision().baselineDecision().equals(attacking.localDecision().candidateDecision()),
                    "HIGH".equals(band) && !attacking.localDecision().baselineDecision().equals(attacking.localDecision().candidateDecision()),
                    true, ""));
        }
        int eventStart = events.size();
        boolean resolved = teamfightResolver.resolveLocalizedSkirmishKill(
                state.getCurrentTimeSeconds(), combatLane, random,
                attacking.actingTeam(), attacking.actingState(),
                attacking.opposingTeam(), attacking.opposingState(),
                events, new HashSet<>()
        );
        if (!resolved) {
            throw new IllegalStateException(
                    "Eligible localized skirmish did not produce an actual attempt");
        }
        String actionId = "COMBAT_AT:" + state.getCurrentTimeSeconds();
        for (int i = eventStart; i < events.size(); i++) {
            events.get(i).setActionId(actionId);
            if (events.get(i).getType() == MatchEventType.KILL) {
                markStructuredParticipants(state, events.get(i));
            }
            state.getCompositionRuntimeState().bindPublicAction(
                    state.getCompositionRuntimeState().lastActualAttemptId(), events.get(i), i);
        }
        return true;
    }

    List<Lane> eligibleLocalizedSkirmishLanes(GameState state) {
        int currentTime = state.getCurrentTimeSeconds();
        List<Lane> eligible = new ArrayList<>();
        for (Lane lane : Lane.values()) {
            if (teamfightResolver.canResolveLocalizedSkirmishKill(
                    currentTime, lane, state.getBlueTeamState(), state.getRedTeamState())) {
                eligible.add(lane);
            }
        }
        return List.copyOf(eligible);
    }

    private void markStructuredParticipants(GameState state, MatchEvent event) {
        java.util.Set<String> ids = new java.util.HashSet<>(event.getAssistPlayerIds());
        if (event.getKillerPlayerId() != null) ids.add(event.getKillerPlayerId());
        if (event.getVictimPlayerId() != null) ids.add(event.getVictimPlayerId());
        for (TeamSide side : TeamSide.values()) {
            for (PlayerState player : state.getTeamState(side).getPlayers()) {
                if (ids.contains(player.getStructuredPlayerId())) state.markMajorCombatParticipant(player);
            }
        }
    }

    private TeamSelection chooseTeamForSkirmish(Random random, Team blueTeam, Team redTeam, GameState state) {
        TeamState blue = state.getBlueTeamState();
        TeamState red = state.getRedTeamState();
        double blueWeight = skirmishInitiative(state, TeamSide.BLUE);
        double redWeight = skirmishInitiative(state, TeamSide.RED);
        double blueCandidate = state.getCompositionRuntimeState().adjustedScoreForCandidate(
                TeamSide.BLUE, TeamCompositionContext.SKIRMISH, CompositionActionType.SKIRMISH,
                CompositionBaselineScoreDomain.SKIRMISH_COMBAT_SCORE, blueWeight, redWeight);
        double redCandidate = state.getCompositionRuntimeState().adjustedScoreForCandidate(
                TeamSide.RED, TeamCompositionContext.SKIRMISH, CompositionActionType.SKIRMISH,
                CompositionBaselineScoreDomain.SKIRMISH_COMBAT_SCORE, redWeight, blueWeight);
        CompositionWinnerDecisionAdjustment auditAdjustment = state.getCompositionRuntimeState().auditWinnerAdjustment(
                TeamSide.BLUE, TeamCompositionContext.SKIRMISH, CompositionActionType.SKIRMISH,
                CompositionBaselineScoreDomain.SKIRMISH_COMBAT_SCORE, blueWeight - redWeight,
                CompositionCombatRole.SYMMETRIC);
        double baselineProbability = new CombatOutcomeProbabilityEvaluator().weightedSelectionProbability(blueWeight, redWeight);
        double candidateProbability = new CombatOutcomeProbabilityEvaluator().weightedSelectionProbability(blueCandidate, redCandidate);
        double sample = random.nextDouble();
        boolean candidateBlue = sample < candidateProbability;
        boolean baselineBlue = sample < baselineProbability;
        LocalDecision local = new LocalDecision(sample,
                random instanceof SideOrientationRandomTraceObserver observer ? observer.drawCount() : -1L,
                baselineProbability, candidateProbability,
                baselineBlue ? "BLUE" : "RED", candidateBlue ? "BLUE" : "RED");
        return candidateBlue
                ? new TeamSelection(blueTeam, blue, redTeam, red, local, auditAdjustment, blueWeight, redWeight, blueCandidate, redCandidate)
                : new TeamSelection(redTeam, red, blueTeam, blue, local, auditAdjustment, blueWeight, redWeight, blueCandidate, redCandidate);
    }

    private void recordSkirmishDecisionProvenance(GameState state, TeamSelection selection, TeamSide runtimeWinner) {
        CompositionWinnerDecisionAdjustment adjustment = selection.auditAdjustment();
        LocalDecision local = selection.localDecision();
        TeamState blue = state.getBlueTeamState(), red = state.getRedTeamState();
        MapState map = state.getMapState(); int time = state.getCurrentTimeSeconds();
        List<CompositionDecisionScoreStage> stages = List.of(
                new CompositionDecisionScoreStage("WEIGHTED_BLUE_INITIATIVE", 0.0, selection.blueWeight(), selection.blueWeight(), selection.blueWeight(), CompositionFactorAvailability.EXACT_RUNTIME_COMPONENT),
                new CompositionDecisionScoreStage("COMPOSITION", selection.blueWeight(), adjustment.rawEdge(),
                        selection.blueCandidateWeight() - selection.blueWeight(), selection.blueCandidateWeight(), CompositionFactorAvailability.EXACT_RUNTIME_COMPONENT));
        state.getCompositionRuntimeState().recordWinnerDecisionProvenance(new CompositionWinnerDecisionProvenance(
                state.getCompositionRuntimeState().matchSeed(), state.getCompositionRuntimeState().isAuditSemantics()
                        ? state.getCompositionRuntimeState().semanticsAuditAuthorization().diagnosticCaseIndex() : -1,
                state.getCompositionRuntimeState().lastActualAttemptId(), "SKIRMISH|SKIRMISH|SKIRMISH_COMBAT_SCORE",
                TeamCompositionContext.SKIRMISH, CompositionActionType.SKIRMISH, CompositionBaselineScoreDomain.SKIRMISH_COMBAT_SCORE,
                time, TeamSide.BLUE, CompositionScoreOrientation.BLUE_MINUS_RED,
                null, null, CompositionCombatRole.SYMMETRIC, CompositionRuntimeDecisionKind.WEIGHTED_SELECTION,
                CompositionRuntimeComparisonOperator.SAMPLE_LESS_THAN_PROBABILITY, adjustment.baselineGap(), adjustment.rawEdge(),
                adjustment.referenceGain(), adjustment.winnerModifier(), adjustment.winnerDecisionGap(),
                adjustment.baselineGap(), adjustment.winnerDecisionGap(), 0.0,
                0.0, 0.0, 0.0,
                local.baselineScore(), local.candidateScore(),
                local.sample(), local.sampleIdentity(), local.candidateScore(), TeamSide.valueOf(local.baselineDecision()), runtimeWinner,
                blue.getGold(), red.getGold(), blue.getKills(), red.getKills(),
                (int)blue.getPlayers().stream().filter(p->p.canParticipateInMajorCombatAt(time)).count(),
                (int)red.getPlayers().stream().filter(p->p.canParticipateInMajorCombatAt(time)).count(),
                selection.blueWeight(), selection.redWeight(), (blue.getGold()-red.getGold())/900.0, (blue.getKills()-red.getKills())*3.0,
                0.0, 0.0, 0.0, 0.0, 0.0, state.getObjectiveState().isSoulOwner(TeamSide.BLUE),
                state.getObjectiveState().isSoulOwner(TeamSide.RED), blue.hasActiveBaronBuff(time), red.hasActiveBaronBuff(time),
                false, false, map.getDestroyedTowerCountByAttackingSide(TeamSide.BLUE), map.getDestroyedTowerCountByAttackingSide(TeamSide.RED),
                map.getAliveInhibitorCount(TeamSide.BLUE), map.getAliveInhibitorCount(TeamSide.RED),
                map.getBaseState(TeamSide.BLUE).getNexusTurretsRemaining(), map.getBaseState(TeamSide.RED).getNexusTurretsRemaining(),
                map.getBaseState(TeamSide.BLUE).isNexusAlive(), map.getBaseState(TeamSide.RED).isNexusAlive(),
                CompositionFactorAvailability.EXACT_RUNTIME_STATE, CompositionFactorAvailability.NOT_AVAILABLE,
                CompositionFactorAvailability.NOT_AVAILABLE, CompositionFactorAvailability.NOT_AVAILABLE, stages));
    }

    private double skirmishInitiative(GameState state, TeamSide side) {
        TeamState team=state.getTeamState(side);int currentTime=state.getCurrentTimeSeconds();
        int alive = 0;
        double total = 0.0;
        for (PlayerState player : team.getPlayers()) {
            if (!player.canParticipateInMajorCombatAt(currentTime)) continue;
            alive++;
            total += playerSkills.combatInitiative(player)
                    * (PlayerImpactRuleConfig.SKIRMISH_INITIATIVE_AGGRESSION_WEIGHT
                    + PlayerImpactRuleConfig.SKIRMISH_INITIATIVE_MECHANICS_WEIGHT
                    + PlayerImpactRuleConfig.SKIRMISH_INITIATIVE_TEAMFIGHTING_WEIGHT);
        }
        if(alive==0)return .1;
        List<PlayerState> own=team.getPlayers().stream().filter(p->p.canParticipateInMajorCombatAt(currentTime)).toList();
        List<PlayerState> enemy=state.getTeamState(side.opposite()).getPlayers().stream().filter(p->p.canParticipateInMajorCombatAt(currentTime)).toList();
        double existing=total+team.getGold()/1_500.0;
        return existing+new CombatProgressionEvaluator().contribution(state,ProgressionCombatContext.GENERIC_SKIRMISH,own,enemy,existing,0,ProgressionApplicationStage.INITIATIVE);
    }

    double genericSkirmishChance(GameState state) {
        int currentTime = state.getCurrentTimeSeconds();
        double averageTendency = (averageSkirmishTendency(
                state.getBlueTeamState(), currentTime)
                + averageSkirmishTendency(state.getRedTeamState(), currentTime)) / 2.0;
        double baseChance = state.getCurrentTimeSeconds() >= 900
                ? CombatRealismRuleConfig.LATE_GENERIC_SKIRMISH_CHANCE
                : CombatRealismRuleConfig.EARLY_GENERIC_SKIRMISH_CHANCE;
        return clamp(baseChance + (averageTendency - PlayerImpactRuleConfig.BASELINE_ATTRIBUTE)
                * PlayerImpactRuleConfig.SKIRMISH_CHANCE_PER_AVERAGE_AGGRESSION_POINT,
                CombatRealismRuleConfig.MIN_GENERIC_SKIRMISH_CHANCE,
                CombatRealismRuleConfig.MAX_GENERIC_SKIRMISH_CHANCE);
    }

    private double averageSkirmishTendency(TeamState team, int currentTime) {
        return team.getPlayers().stream()
                .filter(player -> player.canParticipateInMajorCombatAt(currentTime))
                .mapToDouble(this::combatTendency).average()
                .orElse(PlayerImpactRuleConfig.BASELINE_ATTRIBUTE);
    }

    private double combatTendency(PlayerState player) {
        return playerSkills.decisionQuality(player);
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record TeamSelection(Team actingTeam, TeamState actingState, Team opposingTeam, TeamState opposingState,
                                 LocalDecision localDecision, CompositionWinnerDecisionAdjustment auditAdjustment,
                                 double blueWeight, double redWeight, double blueCandidateWeight, double redCandidateWeight) {
    }

    private record LocalDecision(double sample, long sampleIdentity, double baselineScore, double candidateScore,
                                 String baselineDecision, String candidateDecision) {
    }
}
