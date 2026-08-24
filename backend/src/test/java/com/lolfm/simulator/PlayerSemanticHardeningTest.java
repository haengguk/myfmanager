package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionLineupRequest;
import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.champion.ChampionPowerProfileCatalog;
import com.lolfm.champion.ChampionRoleMatchupProfileCatalog;
import com.lolfm.champion.ChampionSelectionRequest;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PlayerSemanticHardeningTest {
    private static final int BATCH_SEEDS = 16;

    @Test
    void explicitFarmingIsCompressedEarlyAndAccumulatesByFifteenMinutes() {
        ResourceContrast atFive = resourceContrast(Position.ADC, 300);
        ResourceContrast atTen = resourceContrast(Position.ADC, 600);
        ResourceContrast atFifteen = resourceContrast(Position.ADC, 900);

        assertTrue(atFive.highCs() >= atFive.lowCs());
        assertTrue(atFive.gap() <= BATCH_SEEDS * 3, "five-minute gap must remain bounded");
        assertTrue(atTen.gap() > atFive.gap());
        assertTrue(atFifteen.gap() > atTen.gap());
        System.out.printf("PLAYER_SEMANTICS laneFarmCs low/high/gap 5m=%d/%d/%d 10m=%d/%d/%d 15m=%d/%d/%d%n",
                atFive.lowCs(), atFive.highCs(), atFive.gap(),
                atTen.lowCs(), atTen.highCs(), atTen.gap(),
                atFifteen.lowCs(), atFifteen.highCs(), atFifteen.gap());
    }

    @Test
    void explicitJungleResourcesAreCompressedOnFirstClearAndAccumulateLater() {
        ResourceContrast atFive = resourceContrast(Position.JUNGLE, 300);
        ResourceContrast atTen = resourceContrast(Position.JUNGLE, 600);
        ResourceContrast atFifteen = resourceContrast(Position.JUNGLE, 900);

        assertTrue(atFive.highCs() >= atFive.lowCs());
        assertTrue(atFive.gap() <= BATCH_SEEDS * 3, "first-clear player-stat gap must remain bounded");
        assertTrue(atTen.gap() > atFive.gap());
        assertTrue(atFifteen.gap() > atTen.gap());
        System.out.printf("PLAYER_SEMANTICS jungleCs low/high/gap 5m=%d/%d/%d 10m=%d/%d/%d 15m=%d/%d/%d%n",
                atFive.lowCs(), atFive.highCs(), atFive.gap(),
                atTen.lowCs(), atTen.highCs(), atTen.gap(),
                atFifteen.lowCs(), atFifteen.highCs(), atFifteen.gap());
    }

    @Test
    void frozenChampionLaneOpportunityCanOverrideModestFarmingDisadvantageEarly() {
        int renekton17 = 0;
        int gwen20 = 0;
        double opportunity = Double.NaN;
        for (long seed = 1; seed <= BATCH_SEEDS; seed++) {
            ChampionLaneResult result = championLaneAtFiveMinutes(seed);
            renekton17 += result.blueLanerCs();
            gwen20 += result.redLanerCs();
            opportunity = result.initialOpportunity();
        }

        System.out.printf("PLAYER_SEMANTICS championLane 5m renektonF17=%d gwenF20=%d initialOpportunity=%.4f%n",
                renekton17, gwen20, opportunity);
        assertTrue(opportunity > 0.0, "frozen runtime must favor the selected early lane");
        assertTrue(renekton17 >= gwen20,
                "early champion opportunity lost: " + renekton17 + " < " + gwen20);
    }

    @Test
    void decisionMakingChangesGenericCombatTendencyAndDecisionQuality() {
        PlayerRatings low = ratings(Position.JUNGLE)
                .with(PlayerSkill.DECISION_MAKING, 5)
                .with(PlayerSkill.OBJECTIVE_DECISION, 14);
        PlayerRatings high = low.with(PlayerSkill.DECISION_MAKING, 20);
        GameState lowState = stateWithBlueTarget(Position.JUNGLE, low);
        GameState highState = stateWithBlueTarget(Position.JUNGLE, high);
        MatchSimulator simulator = simulator();

        assertTrue(simulator.genericSkirmishChance(highState)
                > simulator.genericSkirmishChance(lowState));
        PlayerSkillEvaluator evaluator = new PlayerSkillEvaluator();
        assertTrue(evaluator.objectiveDecision(highState.getBlueTeamState().playerAt(Position.JUNGLE))
                > evaluator.objectiveDecision(lowState.getBlueTeamState().playerAt(Position.JUNGLE)));
    }

    @Test
    void unavailablePlayerDecisionDoesNotAffectGenericSkirmishChance() {
        PlayerRatings low = ratings(Position.JUNGLE)
                .with(PlayerSkill.DECISION_MAKING, 5);
        PlayerRatings high = low.with(PlayerSkill.DECISION_MAKING, 20);
        GameState lowState = stateWithBlueTarget(Position.JUNGLE, low);
        GameState highState = stateWithBlueTarget(Position.JUNGLE, high);
        lowState.advanceTimeSeconds(600);
        highState.advanceTimeSeconds(600);
        lowState.getBlueTeamState().playerAt(Position.JUNGLE)
                .beginRoamActivity(Lane.TOP, Lane.MID, 600);
        highState.getBlueTeamState().playerAt(Position.JUNGLE)
                .beginRoamActivity(Lane.TOP, Lane.MID, 600);
        MatchSimulator simulator = simulator();

        assertEquals(simulator.genericSkirmishChance(lowState),
                simulator.genericSkirmishChance(highState), 0.0);
    }

    @Test
    void executionRatingsStillIncreaseActualLaneCombatScore() {
        PlayerRatings strong = ratings(Position.TOP)
                .with(PlayerSkill.MECHANICS, 20)
                .with(PlayerSkill.TRADING, 20)
                .with(PlayerSkill.COMBAT_EXECUTION, 20);
        PlayerRatings weak = ratings(Position.TOP)
                .with(PlayerSkill.MECHANICS, 5)
                .with(PlayerSkill.TRADING, 5)
                .with(PlayerSkill.COMBAT_EXECUTION, 5);
        GameState state = stateWithTargets(Position.TOP, strong, weak);
        LaneCombatResolver resolver = new LaneCombatResolver();

        double blueEdge = resolver.combatEdge(state, Lane.TOP, TeamSide.BLUE);
        double redEdge = resolver.combatEdge(state, Lane.TOP, TeamSide.RED);
        assertTrue(blueEdge > 0.0);
        assertTrue(blueEdge > redEdge);
        System.out.printf("PLAYER_SEMANTICS executionCombatEdge strong=%.4f weakPerspective=%.4f%n",
                blueEdge, redEdge);
    }

    @Test
    void decisionAndProficiencyDoNotLeakIntoFarmOrExecutionResponsibilities() {
        PositionEconomyResolver economy = new PositionEconomyResolver();
        PlayerState decisionLow = explicitPlayer("d5", Position.ADC,
                ratings(Position.ADC).with(PlayerSkill.DECISION_MAKING, 5), TeamSide.BLUE, 14);
        PlayerState decisionHigh = explicitPlayer("d20", Position.ADC,
                ratings(Position.ADC).with(PlayerSkill.DECISION_MAKING, 20), TeamSide.BLUE, 14);
        assertEquals(economy.farmingMultiplier(decisionLow, 900),
                economy.farmingMultiplier(decisionHigh, 900), 0.0);
        assertEquals(decisionLow.execution(PlayerSkill.MECHANICS),
                decisionHigh.execution(PlayerSkill.MECHANICS), 0.0);

        PlayerState proficiencyLow = explicitPlayer("p5", Position.ADC,
                ratings(Position.ADC), TeamSide.BLUE, 5);
        PlayerState proficiencyHigh = explicitPlayer("p20", Position.ADC,
                ratings(Position.ADC), TeamSide.BLUE, 20);
        assertEquals(economy.farmingMultiplier(proficiencyLow, 900),
                economy.farmingMultiplier(proficiencyHigh, 900), 0.0);
        assertEquals(proficiencyLow.rating(PlayerSkill.DECISION_MAKING),
                proficiencyHigh.rating(PlayerSkill.DECISION_MAKING), 0.0);
        assertTrue(proficiencyHigh.execution(PlayerSkill.MECHANICS)
                > proficiencyLow.execution(PlayerSkill.MECHANICS));
    }

    @Test
    void legacyEconomyIgnoresExplicitTimeRealization() {
        PlayerState legacy = new PlayerState("legacy", Position.ADC,
                new PlayerAttributes(14, 14, 18, 14), 500);
        PositionEconomyResolver economy = new PositionEconomyResolver();
        double expected = 1.0 + (18 - PositionEconomyRuleConfig.FARMING_BASELINE)
                * PositionEconomyRuleConfig.FARMING_MULTIPLIER_PER_POINT;
        assertEquals(expected, economy.farmingMultiplier(legacy, 0), 0.0);
        assertEquals(expected, economy.farmingMultiplier(legacy, 300), 0.0);
        assertEquals(expected, economy.farmingMultiplier(legacy, 900), 0.0);
    }

    private ResourceContrast resourceContrast(Position position, int seconds) {
        int low = 0;
        int high = 0;
        for (long seed = 1; seed <= BATCH_SEEDS; seed++) {
            PlayerRatings lowRatings = ratings(position);
            PlayerRatings highRatings = ratings(position);
            if (position == Position.JUNGLE) {
                lowRatings = lowRatings.with(PlayerSkill.JUNGLE_RESOURCE_MANAGEMENT, 5)
                        .with(PlayerSkill.PATHING, 5);
                highRatings = highRatings.with(PlayerSkill.JUNGLE_RESOURCE_MANAGEMENT, 20)
                        .with(PlayerSkill.PATHING, 20);
            } else {
                lowRatings = lowRatings.with(PlayerSkill.FARMING, 5);
                highRatings = highRatings.with(PlayerSkill.FARMING, 20);
            }
            low += accumulatedCs(position, lowRatings, seconds, seed);
            high += accumulatedCs(position, highRatings, seconds, seed);
        }
        return new ResourceContrast(low, high);
    }

    private int accumulatedCs(Position position, PlayerRatings targetRatings, int seconds, long seed) {
        GameState state = stateWithBlueTarget(position, targetRatings);
        PositionEconomyResolver economy = new PositionEconomyResolver();
        Random random = new Random(seed);
        for (int time = 10; time <= seconds; time += 10) {
            state.advanceTimeSeconds(10);
            economy.resolve(state, state.getBlueTeamState(), TeamSide.BLUE, time, 10, random);
        }
        return state.getBlueTeamState().playerAt(position).getCs();
    }

    private ChampionLaneResult championLaneAtFiveMinutes(long seed) {
        ChampionCatalog champions = new ChampionCatalog(new ObjectMapper());
        MatchChampionAssignments assignments = new ChampionSelectionValidator(champions).resolve(
                new ChampionSelectionRequest(
                        new ChampionLineupRequest("renekton", "sejuani", "azir", "lucian", "nautilus"),
                        new ChampionLineupRequest("gwen", "viego", "viktor", "jinx", "lulu")));
        PlayerRatings renekton = ratings(Position.TOP).with(PlayerSkill.FARMING, 17);
        PlayerRatings gwen = ratings(Position.TOP).with(PlayerSkill.FARMING, 20);
        TeamState blue = explicitTeam("blue", TeamSide.BLUE, Position.TOP, renekton);
        TeamState red = explicitTeam("red", TeamSide.RED, Position.TOP, gwen);
        GameState state = new GameState(blue, red, true, true, true, true, true, true, assignments);
        state.configureChampionPower(new ChampionPowerProfileCatalog(new ObjectMapper(), champions), true);
        state.configureChampionMatchup(ChampionRoleMatchupProfileCatalog.production(),
                ChampionMatchupMode.GEOMETRIC_V2);
        double initialOpportunity = new LaneOpportunityEvaluator().attributeDifference(state, Lane.TOP);

        LanePressureResolver pressure = new LanePressureResolver();
        PositionEconomyResolver economy = new PositionEconomyResolver();
        Random random = new Random(seed);
        for (int time = 10; time <= 300; time += 10) {
            state.advanceTimeSeconds(10);
            pressure.resolve(state, time, random);
            economy.resolve(state, blue, TeamSide.BLUE, time, 10, random);
            economy.resolve(state, red, TeamSide.RED, time, 10, random);
        }
        return new ChampionLaneResult(blue.playerAt(Position.TOP).getCs(),
                red.playerAt(Position.TOP).getCs(), initialOpportunity);
    }

    private GameState stateWithBlueTarget(Position position, PlayerRatings blueRatings) {
        return stateWithTargets(position, blueRatings, ratings(position));
    }

    private GameState stateWithTargets(Position position, PlayerRatings blueRatings,
                                       PlayerRatings redRatings) {
        return new GameState(
                explicitTeam("blue", TeamSide.BLUE, position, blueRatings),
                explicitTeam("red", TeamSide.RED, position, redRatings));
    }

    private TeamState explicitTeam(String name, TeamSide side, Position target,
                                   PlayerRatings targetRatings) {
        List<PlayerState> players = new ArrayList<>();
        for (Position position : Position.values()) {
            PlayerRatings playerRatings = position == target ? targetRatings : ratings(position);
            players.add(explicitPlayer(name + "-" + position, position, playerRatings, side, 14));
        }
        return new TeamState(name, players);
    }

    private PlayerState explicitPlayer(String name, Position position, PlayerRatings ratings,
                                       TeamSide side, int proficiency) {
        PlayerMatchPerformance performance =
                PlayerMatchPerformance.realize(ratings, proficiency, 991L, side);
        return new PlayerState(name, position, new PlayerAttributes(14, 14, 14, 14),
                performance, 500, true);
    }

    private PlayerRatings ratings(Position position) {
        return PlayerRatings.neutral(position).with(PlayerSkill.CONSISTENCY, 20);
    }

    private MatchSimulator simulator() {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver());
    }

    private record ResourceContrast(int lowCs, int highCs) {
        int gap() { return highCs - lowCs; }
    }

    private record ChampionLaneResult(int blueLanerCs, int redLanerCs, double initialOpportunity) {
    }
}
