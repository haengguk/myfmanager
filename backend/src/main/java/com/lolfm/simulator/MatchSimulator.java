package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionMatchupCatalog;
import com.lolfm.champion.ChampionMatchupExecutionStatsSnapshot;
import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.champion.ChampionRoleMatchupProfileCatalog;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.champion.ChampionPowerProfileCatalog;
import com.lolfm.champion.MatchChampionAssignments;
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

    private static final ChampionCatalog DEFAULT_CHAMPION_CATALOG = new ChampionCatalog(new ObjectMapper());
    private static final ChampionPowerProfileCatalog DEFAULT_CHAMPION_POWER_CATALOG = new ChampionPowerProfileCatalog(new ObjectMapper(), DEFAULT_CHAMPION_CATALOG);
    private static final ChampionMatchupCatalog DEFAULT_CHAMPION_MATCHUP_CATALOG =
            ChampionMatchupCatalog.neutral(DEFAULT_CHAMPION_CATALOG);
    private static final ChampionRoleMatchupProfileCatalog DEFAULT_CHAMPION_MATCHUP_PROFILES =
            ChampionRoleMatchupProfileCatalog.production();

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
        this.jungleGankResolver = new JungleGankResolver(counterGankEnabled);
    }

    public MatchSimulator(
            TeamfightResolver teamfightResolver, EndGameEvaluator endGameEvaluator, SnapshotFactory snapshotFactory,
            ObjectiveResolver objectiveResolver, PostFightResolver postFightResolver,
            ObjectiveAttemptResolver objectiveAttemptResolver, StructureResolver structureResolver, PushResolver pushResolver,
            SimulationOptions options, ChampionRoleMatchupProfileCatalog championMatchupProfiles
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
        this.jungleGankResolver = new JungleGankResolver(counterGankEnabled);
    }

    public MatchTimeline simulate(Team blueTeam, Team redTeam, long seed) {
        return simulate(blueTeam, redTeam, seed,
                new ChampionSelectionValidator(DEFAULT_CHAMPION_CATALOG).resolve(null));
    }

    public MatchTimeline simulate(Team blueTeam, Team redTeam, long seed, MatchChampionAssignments assignments) {
        return runSimulation(blueTeam, redTeam, seed, assignments).timeline();
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
        return runSimulation(blueTeam, redTeam, assignments, random, random.seed());
    }

    private SimulationResult runSimulation(Team blueTeam, Team redTeam, long seed, MatchChampionAssignments assignments) {
        return runSimulation(blueTeam, redTeam, assignments, new Random(seed), seed);
    }

    private SimulationResult runSimulation(
            Team blueTeam,
            Team redTeam,
            MatchChampionAssignments assignments,
            Random random,
            long seed
    ) {
        GameState gameState = initializeGameState(blueTeam, redTeam, assignments);
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
            List<StructureOutcome> siegeStructures = lanePhaseResolver.resolveOuterSieges(
                    gameState, gameState.getCurrentTimeSeconds(), random, structureResolver);
            for (StructureOutcome structure : siegeStructures) events.add(structureResolver.createStructureEvent(gameState, structure));
            randomContext(random, SideOrientationRandomTraceObserver.Source.OBJECTIVE_CAPTURE, null, gameState);
            Optional<MatchEvent> postFightObjective = outcome.flatMap(result -> postFightResolver.resolve(
                    gameState, result, random, objectiveResolver));
            postFightObjective.ifPresent(event -> { events.add(event); cancelMacroSetupForCapture(gameState, event); });
            boolean postFightSideAlreadyActed = outcome
                    .map(result -> gameState.wasStructureActionPerformedThisTick(result.winningSide()))
                    .orElse(false);
            randomContext(random, SideOrientationRandomTraceObserver.Source.STRUCTURE_PUSH, null, gameState);
            List<StructureOutcome> postFightStructures = postFightSideAlreadyActed
                    ? List.of()
                    : pushResolver.resolvePostFightWindow(gameState, outcome, postFightObjective, random, structureResolver);
            for (StructureOutcome structure : postFightStructures) events.add(structureResolver.createStructureEvent(gameState, structure));
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
                pushResolver.maybeResolveMacroPush(gameState, random, structureResolver)
                        .ifPresent(push -> events.add(structureResolver.createStructureEvent(gameState, push)));
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
                gameState.getChampionPowerExecutionStats().snapshot(),
                gameState.getChampionMatchupExecutionStats().snapshot(),
                gameState.getCombatOutcomeExecutionStats().snapshot(),
                random instanceof SideOrientationRandomTraceObserver observer ? observer.drawCount() : 0L,
                random instanceof SideOrientationRandomTraceObserver observer ? observer.trace() : List.of()
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
            com.lolfm.champion.ChampionPowerExecutionStatsSnapshot championPowerExecutionStats,
            ChampionMatchupExecutionStatsSnapshot championMatchupExecutionStats,
            CombatOutcomeExecutionStatsSnapshot combatOutcomeExecutionStats,
            long randomDrawCount,
            List<SideOrientationRandomTraceObserver.Draw> randomTrace
    ) {
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
    private GameState initializeGameState(Team blueTeam, Team redTeam, MatchChampionAssignments assignments) {
        GameState state = new GameState(buildTeamState(blueTeam), buildTeamState(redTeam), diagnosticsEnabled,
                objectivePriorityEnabled, lanePhaseEnabled, midGameMacroEnabled, objectiveDecisionEnabled,
                lateGameMacroEnabled, assignments);
        state.configureChampionPower(DEFAULT_CHAMPION_POWER_CATALOG, championPowerEnabled);
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

    private TeamState buildTeamState(Team team) {
        List<PlayerState> states = new ArrayList<>();
        for (Player player : team.getPlayers()) {
            states.add(new PlayerState(player.getName(), player.getPosition(), player.getAttributes(), STARTING_GOLD,
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
        for (PlayerState player : state.getPlayers()) {
            goldAwards.awardGold(state, player, passiveGoldPerTick(), GoldSource.PASSIVE, false, currentTime);
        }
        return true;
    }

    private void resolveFarmForTick(Random random, GameState gameState, TeamState state, TeamSide side,
                                    int elapsedSeconds, int currentTime, boolean economyStarted) {
        if (!economyStarted) return;
        positionEconomyResolver.resolve(gameState, state, side, currentTime, elapsedSeconds, random);
        state.markEconomyResolvedAt(currentTime);
    }

    private int passiveGoldPerTick() { return PositionEconomyRuleConfig.PASSIVE_GOLD_PER_TICK; }

    private boolean maybeCreateKillEvent(Random random, Team blueTeam, Team redTeam, GameState state, List<MatchEvent> events) {
        double averageAggression = (averageAttribute(state.getBlueTeamState(), PlayerState::getAggression)
                + averageAttribute(state.getRedTeamState(), PlayerState::getAggression)) / 2.0;
        double baseChance = state.getCurrentTimeSeconds() >= 900 ? 0.11 : 0.08;
        double chance = clamp(baseChance + (averageAggression - PlayerImpactRuleConfig.BASELINE_ATTRIBUTE)
                * PlayerImpactRuleConfig.SKIRMISH_CHANCE_PER_AVERAGE_AGGRESSION_POINT, 0.05, 0.15);
        if (random.nextDouble() >= chance) return false;
        TeamSelection attacking = chooseTeamForSkirmish(random, blueTeam, redTeam, state);
        boolean resolved = teamfightResolver.resolveKill(
                state.getCurrentTimeSeconds(), random,
                attacking.actingTeam(), attacking.actingState(),
                attacking.opposingTeam(), attacking.opposingState(),
                events, false, new HashSet<>()
        );
        if (resolved) {
            for (PlayerState player : attacking.actingState().getPlayers()) if (player.canParticipateInMajorCombatAt(state.getCurrentTimeSeconds())) state.markMajorCombatParticipant(player);
            for (PlayerState player : attacking.opposingState().getPlayers()) if (player.canParticipateInMajorCombatAt(state.getCurrentTimeSeconds())) state.markMajorCombatParticipant(player);
        }
        return resolved;
    }

    private TeamSelection chooseTeamForSkirmish(Random random, Team blueTeam, Team redTeam, GameState state) {
        TeamState blue = state.getBlueTeamState();
        TeamState red = state.getRedTeamState();
        double blueWeight = skirmishInitiative(state, TeamSide.BLUE);
        double redWeight = skirmishInitiative(state, TeamSide.RED);
        return random.nextDouble() < new CombatOutcomeProbabilityEvaluator().weightedSelectionProbability(blueWeight, redWeight)
                ? new TeamSelection(blueTeam, blue, redTeam, red)
                : new TeamSelection(redTeam, red, blueTeam, blue);
    }

    private double skirmishInitiative(GameState state, TeamSide side) {
        TeamState team=state.getTeamState(side);int currentTime=state.getCurrentTimeSeconds();
        int alive = 0;
        double total = 0.0;
        for (PlayerState player : team.getPlayers()) {
            if (!player.canParticipateInMajorCombatAt(currentTime)) continue;
            alive++;
            total += player.getAggression() * PlayerImpactRuleConfig.SKIRMISH_INITIATIVE_AGGRESSION_WEIGHT
                    + player.getMechanics() * PlayerImpactRuleConfig.SKIRMISH_INITIATIVE_MECHANICS_WEIGHT
                    + player.getTeamfighting() * PlayerImpactRuleConfig.SKIRMISH_INITIATIVE_TEAMFIGHTING_WEIGHT;
        }
        if(alive==0)return .1;
        List<PlayerState> own=team.getPlayers().stream().filter(p->p.canParticipateInMajorCombatAt(currentTime)).toList();
        List<PlayerState> enemy=state.getTeamState(side.opposite()).getPlayers().stream().filter(p->p.canParticipateInMajorCombatAt(currentTime)).toList();
        double existing=total+team.getKills()*3.0+team.getGold()/900.0;
        return existing+new CombatProgressionEvaluator().contribution(state,ProgressionCombatContext.GENERIC_SKIRMISH,own,enemy,existing,0,ProgressionApplicationStage.INITIATIVE);
    }

    private double averageAttribute(TeamState team, java.util.function.ToIntFunction<PlayerState> attribute) {
        double total = 0.0;
        for (PlayerState player : team.getPlayers()) total += attribute.applyAsInt(player);
        return team.getPlayers().isEmpty() ? PlayerImpactRuleConfig.BASELINE_ATTRIBUTE : total / team.getPlayers().size();
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record TeamSelection(Team actingTeam, TeamState actingState, Team opposingTeam, TeamState opposingState) {
    }
}
