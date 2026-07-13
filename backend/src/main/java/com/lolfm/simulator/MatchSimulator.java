package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
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
    private final LaneCombatResolver laneCombatResolver = new LaneCombatResolver();
    private final JungleGankResolver jungleGankResolver = new JungleGankResolver();
    private final GoldAwardService goldAwards = new GoldAwardService();
    private final boolean laneCombatEnabled;
    private final boolean farmRecoveryEnabled;
    private final boolean jungleGankEnabled;

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
                objectiveAttemptResolver, structureResolver, pushResolver, true, true, true);
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
                objectiveAttemptResolver, structureResolver, pushResolver, laneCombatEnabled, true, true);
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
                objectiveAttemptResolver, structureResolver, pushResolver, laneCombatEnabled, farmRecoveryEnabled, true);
    }

    MatchSimulator(
            TeamfightResolver teamfightResolver, EndGameEvaluator endGameEvaluator, SnapshotFactory snapshotFactory,
            ObjectiveResolver objectiveResolver, PostFightResolver postFightResolver,
            ObjectiveAttemptResolver objectiveAttemptResolver, StructureResolver structureResolver, PushResolver pushResolver,
            boolean laneCombatEnabled, boolean farmRecoveryEnabled, boolean jungleGankEnabled
    ) {
        this.teamfightResolver = teamfightResolver;
        this.endGameEvaluator = endGameEvaluator;
        this.snapshotFactory = snapshotFactory;
        this.objectiveResolver = objectiveResolver;
        this.postFightResolver = postFightResolver;
        this.objectiveAttemptResolver = objectiveAttemptResolver;
        this.structureResolver = structureResolver;
        this.pushResolver = pushResolver;
        this.laneCombatEnabled = laneCombatEnabled;
        this.farmRecoveryEnabled = farmRecoveryEnabled;
        this.jungleGankEnabled = jungleGankEnabled;
    }

    public MatchTimeline simulate(Team blueTeam, Team redTeam, long seed) {
        return runSimulation(blueTeam, redTeam, seed).timeline();
    }

    SimulationResult simulateWithDiagnostics(Team blueTeam, Team redTeam, long seed) {
        return runSimulation(blueTeam, redTeam, seed);
    }

    private SimulationResult runSimulation(Team blueTeam, Team redTeam, long seed) {
        Random random = new Random(seed);
        GameState gameState = initializeGameState(blueTeam, redTeam);
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
            boolean blueEconomy = awardPassiveForTick(gameState.getBlueTeamState(), gameState.getCurrentTimeSeconds());
            boolean redEconomy = awardPassiveForTick(gameState.getRedTeamState(), gameState.getCurrentTimeSeconds());
            lanePressureResolver.resolve(gameState, gameState.getCurrentTimeSeconds(), random);
            resolveFarmForTick(random, gameState, gameState.getBlueTeamState(), TeamSide.BLUE,
                    TICK_SECONDS, gameState.getCurrentTimeSeconds(), blueEconomy);
            resolveFarmForTick(random, gameState, gameState.getRedTeamState(), TeamSide.RED,
                    TICK_SECONDS, gameState.getCurrentTimeSeconds(), redEconomy);
            boolean jungleGankAttempted = jungleGankEnabled && jungleGankResolver.resolve(gameState, random, events);
            boolean laneCombatAttempted = !jungleGankAttempted && laneCombatEnabled
                    && laneCombatResolver.resolve(gameState, random, events);
            boolean majorCombatAttempted = jungleGankAttempted || laneCombatAttempted;
            objectiveResolver.updateSpawnState(gameState);
            if (!majorCombatAttempted) maybeCreateKillEvent(random, blueTeam, redTeam, gameState, events);

            Optional<TeamfightOutcome> outcome = majorCombatAttempted ? Optional.empty() : teamfightResolver.maybeResolveTeamfight(
                    gameState, blueTeam, redTeam, random, events
            );
            Optional<MatchEvent> postFightObjective = outcome.flatMap(result -> postFightResolver.resolve(
                    gameState, result, random, objectiveResolver
            ));
            postFightObjective.ifPresent(events::add);

            List<StructureOutcome> postFightStructures = pushResolver.resolvePostFightWindow(
                    gameState, outcome, postFightObjective, random, structureResolver
            );
            for (StructureOutcome structure : postFightStructures) {
                events.add(structureResolver.createStructureEvent(gameState, structure));
            }
            boolean towerDestroyedThisTick = !postFightStructures.isEmpty();

            if (!gameState.isFinished() && postFightObjective.isEmpty()) {
                objectiveAttemptResolver.maybeAttemptObjective(gameState, random, objectiveResolver).ifPresent(events::add);
            }
            if (!gameState.isFinished() && !towerDestroyedThisTick) {
                pushResolver.maybeResolveMacroPush(gameState, random, structureResolver)
                        .ifPresent(push -> events.add(structureResolver.createStructureEvent(gameState, push)));
            }

            endGameDecision = endGameEvaluator.evaluateAfterTick(gameState);
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
                        + gameState.getRedTeamState().getDuplicateEconomyResolutionCount()
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
            int duplicateEconomyResolutions
    ) {
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

    private GameState initializeGameState(Team blueTeam, Team redTeam) {
        return new GameState(buildTeamState(blueTeam), buildTeamState(redTeam));
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
            goldAwards.awardGold(state, player, passiveGoldPerTick(), GoldSource.PASSIVE, false);
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

    private void maybeCreateKillEvent(Random random, Team blueTeam, Team redTeam, GameState state, List<MatchEvent> events) {
        double averageAggression = (averageAttribute(state.getBlueTeamState(), PlayerState::getAggression)
                + averageAttribute(state.getRedTeamState(), PlayerState::getAggression)) / 2.0;
        double baseChance = state.getCurrentTimeSeconds() >= 900 ? 0.11 : 0.08;
        double chance = clamp(baseChance + (averageAggression - PlayerImpactRuleConfig.BASELINE_ATTRIBUTE)
                * PlayerImpactRuleConfig.SKIRMISH_CHANCE_PER_AVERAGE_AGGRESSION_POINT, 0.05, 0.15);
        if (random.nextDouble() >= chance) return;
        TeamSelection attacking = chooseTeamForSkirmish(random, blueTeam, redTeam, state);
        teamfightResolver.resolveKill(
                state.getCurrentTimeSeconds(), random,
                attacking.actingTeam(), attacking.actingState(),
                attacking.opposingTeam(), attacking.opposingState(),
                events, false, new HashSet<>()
        );
    }

    private TeamSelection chooseTeamForSkirmish(Random random, Team blueTeam, Team redTeam, GameState state) {
        TeamState blue = state.getBlueTeamState();
        TeamState red = state.getRedTeamState();
        double blueWeight = skirmishInitiative(blue, state.getCurrentTimeSeconds())
                + blue.getKills() * 3.0 + blue.getGold() / 900.0;
        double redWeight = skirmishInitiative(red, state.getCurrentTimeSeconds())
                + red.getKills() * 3.0 + red.getGold() / 900.0;
        return random.nextDouble() < blueWeight / (blueWeight + redWeight)
                ? new TeamSelection(blueTeam, blue, redTeam, red)
                : new TeamSelection(redTeam, red, blueTeam, blue);
    }

    private double skirmishInitiative(TeamState team, int currentTime) {
        int alive = 0;
        double total = 0.0;
        for (PlayerState player : team.getPlayers()) {
            if (!player.isAlive(currentTime)) continue;
            alive++;
            total += player.getAggression() * PlayerImpactRuleConfig.SKIRMISH_INITIATIVE_AGGRESSION_WEIGHT
                    + player.getMechanics() * PlayerImpactRuleConfig.SKIRMISH_INITIATIVE_MECHANICS_WEIGHT
                    + player.getTeamfighting() * PlayerImpactRuleConfig.SKIRMISH_INITIATIVE_TEAMFIGHTING_WEIGHT;
        }
        return alive == 0 ? 0.1 : total;
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
