package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.*;

import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerSnapshot;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.factory.DummyDataFactory;
import java.util.Map;
import java.util.Random;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;

class PlayerRatingsAndProficiencyTest {
    private final PlayerSkillEvaluator skills = new PlayerSkillEvaluator();

    @Test
    void schemaRequiresExactlyTwelveRoleRatingsAndBounds() {
        for (Position position : Position.values()) {
            PlayerRatings ratings = PlayerRatings.neutral(position);
            assertEquals(12, ratings.asMap().size());
            assertEquals(PlayerSkill.forPosition(position), ratings.asMap().keySet());
        }
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerRatings(Position.TOP, Map.of(PlayerSkill.MECHANICS, 14)));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerRatings.neutral(Position.TOP).with(PlayerSkill.FARMING, 21));
    }

    @Test
    void everyCommonAndLanerAttributeChangesItsOwnedScore() {
        assertOwned(Position.TOP, PlayerSkill.MECHANICS, skills::combatExecution);
        assertOwned(Position.TOP, PlayerSkill.DECISION_MAKING, skills::decisionQuality);
        assertOwned(Position.TOP, PlayerSkill.MAP_AWARENESS, skills::mapAwareness);
        assertOwned(Position.TOP, PlayerSkill.POSITIONING, skills::exposureSafety);
        assertOwned(Position.TOP, PlayerSkill.COMBAT_EXECUTION, skills::combatExecution);
        assertOwned(Position.TOP, PlayerSkill.FARMING, skills::farming);
        assertOwned(Position.TOP, PlayerSkill.TRADING, skills::laneTrade);
        assertOwned(Position.TOP, PlayerSkill.WAVE_MANAGEMENT, skills::waveManagement);
        assertOwned(Position.TOP, PlayerSkill.LANE_PRESSURE, skills::lanePressure);
        assertOwned(Position.TOP, PlayerSkill.PRIORITY_CONVERSION, skills::priorityConversion);
        assertOwned(Position.TOP, PlayerSkill.SIDE_LANE, skills::sideLane);
    }

    @Test
    void everyJungleAttributeChangesItsOwnedScore() {
        assertOwned(Position.JUNGLE, PlayerSkill.PATHING, skills::pathing);
        assertOwned(Position.JUNGLE, PlayerSkill.JUNGLE_RESOURCE_MANAGEMENT, skills::jungleResources);
        assertOwned(Position.JUNGLE, PlayerSkill.ENEMY_JUNGLE_TRACKING, skills::jungleTracking);
        assertOwned(Position.JUNGLE, PlayerSkill.LANE_INTERVENTION, skills::laneIntervention);
        assertOwned(Position.JUNGLE, PlayerSkill.OBJECTIVE_DECISION, skills::objectiveDecision);
        assertOwned(Position.JUNGLE, PlayerSkill.OBJECTIVE_SECURE, skills::objectiveSecure);
    }

    @Test
    void everySupportAttributeChangesItsOwnedScore() {
        assertOwned(Position.SUPPORT, PlayerSkill.VISION_CONTROL, skills::visionControl);
        assertOwned(Position.SUPPORT, PlayerSkill.LANE_SUPPORT, skills::laneSupport);
        assertOwned(Position.SUPPORT, PlayerSkill.ROTATION_PLANNING, skills::rotationPlanning);
        assertOwned(Position.SUPPORT, PlayerSkill.ENGAGE_EXECUTION, skills::engageExecution);
        assertOwned(Position.SUPPORT, PlayerSkill.ALLY_PROTECTION, skills::allyProtection);
        assertOwned(Position.SUPPORT, PlayerSkill.AREA_SETUP, skills::areaSetup);
    }

    @Test
    void consistencyRealizationIsMatchScopedRepeatableAndControlsSpread() {
        PlayerRatings low = PlayerRatings.neutral(Position.MID).with(PlayerSkill.CONSISTENCY, 1);
        PlayerRatings high = PlayerRatings.neutral(Position.MID).with(PlayerSkill.CONSISTENCY, 20);
        PlayerMatchPerformance lowA = PlayerMatchPerformance.realize(low, 14, 77L, TeamSide.BLUE);
        PlayerMatchPerformance lowB = PlayerMatchPerformance.realize(low, 14, 77L, TeamSide.BLUE);
        PlayerMatchPerformance highA = PlayerMatchPerformance.realize(high, 14, 77L, TeamSide.BLUE);

        assertEquals(lowA.asMap(), lowB.asMap());
        assertTrue(deviation(lowA) > deviation(highA));
        assertEquals(20.0, highA.rating(PlayerSkill.CONSISTENCY));
    }

    @Test
    void nonTargetRatingsDoNotLeak() {
        PlayerState farmingLow = player(Position.TOP,
                PlayerRatings.neutral(Position.TOP).with(PlayerSkill.CONSISTENCY, 20)
                        .with(PlayerSkill.FARMING, 5), 14);
        PlayerState farmingHigh = player(Position.TOP,
                PlayerRatings.neutral(Position.TOP).with(PlayerSkill.CONSISTENCY, 20)
                        .with(PlayerSkill.FARMING, 20), 14);
        assertEquals(skills.combatExecution(farmingLow), skills.combatExecution(farmingHigh));

        PlayerState visionLow = player(Position.SUPPORT,
                PlayerRatings.neutral(Position.SUPPORT).with(PlayerSkill.CONSISTENCY, 20)
                        .with(PlayerSkill.VISION_CONTROL, 5), 14);
        PlayerState visionHigh = player(Position.SUPPORT,
                PlayerRatings.neutral(Position.SUPPORT).with(PlayerSkill.CONSISTENCY, 20)
                        .with(PlayerSkill.VISION_CONTROL, 20), 14);
        PositionEconomyResolver economy = new PositionEconomyResolver();
        assertEquals(economy.farmingMultiplier(visionLow), economy.farmingMultiplier(visionHigh));

        PlayerState secureLow = player(Position.JUNGLE,
                PlayerRatings.neutral(Position.JUNGLE).with(PlayerSkill.CONSISTENCY, 20)
                        .with(PlayerSkill.OBJECTIVE_SECURE, 5), 14);
        PlayerState secureHigh = player(Position.JUNGLE,
                PlayerRatings.neutral(Position.JUNGLE).with(PlayerSkill.CONSISTENCY, 20)
                        .with(PlayerSkill.OBJECTIVE_SECURE, 20), 14);
        assertEquals(skills.objectiveDecision(secureLow), skills.objectiveDecision(secureHigh));
    }

    @Test
    void proficiencyIsBoundedExecutionOnlyAndRoleKeyIndependent() {
        PlayerRatings base = PlayerRatings.neutral(Position.TOP).with(PlayerSkill.CONSISTENCY, 20);
        PlayerState low = player(Position.TOP, base, 5);
        PlayerState high = player(Position.TOP, base, 20);
        assertTrue(skills.combatExecution(high) > skills.combatExecution(low));
        assertEquals(skills.decisionQuality(low), skills.decisionQuality(high));
        assertTrue(high.execution(PlayerSkill.MECHANICS) - low.execution(PlayerSkill.MECHANICS) <= 5.7);

        ChampionId poppy = new ChampionId("poppy");
        ChampionProficiencies profile = new ChampionProficiencies(Map.of(
                new ChampionRoleKey(poppy, Position.JUNGLE), 19,
                new ChampionRoleKey(poppy, Position.SUPPORT), 15,
                new ChampionRoleKey(poppy, Position.TOP), 11));
        assertEquals(19, profile.get(new ChampionRoleKey(poppy, Position.JUNGLE)));
        assertEquals(15, profile.get(new ChampionRoleKey(poppy, Position.SUPPORT)));
        assertEquals(11, profile.get(new ChampionRoleKey(poppy, Position.TOP)));
    }

    @Test
    void syntheticLanerJungleAndSupportStylesHaveDifferentStrengths() {
        PlayerState laneEconomy = player(Position.TOP, PlayerRatings.neutral(Position.TOP)
                .with(PlayerSkill.CONSISTENCY, 20).with(PlayerSkill.FARMING, 20)
                .with(PlayerSkill.TRADING, 20).with(PlayerSkill.POSITIONING, 7)
                .with(PlayerSkill.COMBAT_EXECUTION, 7), 14);
        PlayerState laneFight = player(Position.TOP, PlayerRatings.neutral(Position.TOP)
                .with(PlayerSkill.CONSISTENCY, 20).with(PlayerSkill.FARMING, 14)
                .with(PlayerSkill.TRADING, 14).with(PlayerSkill.POSITIONING, 20)
                .with(PlayerSkill.COMBAT_EXECUTION, 20), 14);
        assertTrue(skills.farming(laneEconomy) > skills.farming(laneFight));
        assertTrue(skills.combatExecution(laneFight) > skills.combatExecution(laneEconomy));

        PlayerState jungleResource = player(Position.JUNGLE, PlayerRatings.neutral(Position.JUNGLE)
                .with(PlayerSkill.CONSISTENCY, 20).with(PlayerSkill.PATHING, 20)
                .with(PlayerSkill.JUNGLE_RESOURCE_MANAGEMENT, 20), 14);
        PlayerState jungleGank = player(Position.JUNGLE, PlayerRatings.neutral(Position.JUNGLE)
                .with(PlayerSkill.CONSISTENCY, 20).with(PlayerSkill.LANE_INTERVENTION, 20)
                .with(PlayerSkill.COMBAT_EXECUTION, 20), 14);
        assertTrue(skills.jungleResources(jungleResource) > skills.jungleResources(jungleGank));
        assertTrue(skills.laneIntervention(jungleGank) > skills.laneIntervention(jungleResource));

        PlayerState supportVision = player(Position.SUPPORT, PlayerRatings.neutral(Position.SUPPORT)
                .with(PlayerSkill.CONSISTENCY, 20).with(PlayerSkill.VISION_CONTROL, 20)
                .with(PlayerSkill.AREA_SETUP, 20), 14);
        PlayerState supportEngage = player(Position.SUPPORT, PlayerRatings.neutral(Position.SUPPORT)
                .with(PlayerSkill.CONSISTENCY, 20).with(PlayerSkill.ENGAGE_EXECUTION, 20)
                .with(PlayerSkill.ROTATION_PLANNING, 20), 14);
        assertTrue(skills.areaSetup(supportVision) > skills.areaSetup(supportEngage));
        assertTrue(skills.engageExecution(supportEngage) > skills.engageExecution(supportVision));
    }

    @Test
    void farmingRatingChangesActualCsWithoutChangingRandomDiscipline() {
        PlayerState low = player(Position.ADC, PlayerRatings.neutral(Position.ADC)
                .with(PlayerSkill.CONSISTENCY, 20).with(PlayerSkill.FARMING, 5), 14);
        PlayerState high = player(Position.ADC, PlayerRatings.neutral(Position.ADC)
                .with(PlayerSkill.CONSISTENCY, 20).with(PlayerSkill.FARMING, 20), 14);
        TeamState lowTeam = new TeamState("low", java.util.List.of(low));
        TeamState highTeam = new TeamState("high", java.util.List.of(high));
        PositionEconomyResolver economy = new PositionEconomyResolver();
        economy.resolve(lowTeam, 600, 600, new Random(9));
        economy.resolve(highTeam, 600, 600, new Random(9));
        assertTrue(high.getCs() > low.getCs());
        assertTrue(high.getGold() > low.getGold());
    }

    @Test
    void fullMatchIsExactReplayForSameSeedAndProfiles() {
        Team blue = teamWithAdcRatings(PlayerRatings.neutral(Position.ADC)
                .with(PlayerSkill.FARMING, 18).with(PlayerSkill.COMBAT_EXECUTION, 17));
        Team red = new DummyDataFactory().createRedTeam();
        MatchTimeline first = simulator().simulate(blue, red, 41L);
        MatchTimeline second = simulator().simulate(blue, red, 41L);
        assertEquals(first.getDurationSeconds(), second.getDurationSeconds());
        assertEquals(first.getWinner(), second.getWinner());
        assertEquals(first.getEvents().size(), second.getEvents().size());
        for (int i = 0; i < first.getEvents().size(); i++) {
            assertEquals(first.getEvents().get(i).getType(), second.getEvents().get(i).getType());
            assertEquals(first.getEvents().get(i).getMessage(), second.getEvents().get(i).getMessage());
            assertEquals(first.getEvents().get(i).getKiller(), second.getEvents().get(i).getKiller());
            assertEquals(first.getEvents().get(i).getVictim(), second.getEvents().get(i).getVictim());
        }
    }

    @Test
    void modestSeedBatchShowsFarmingMonotonicityAtTenMinutes() {
        PlayerRatings low = PlayerRatings.neutral(Position.ADC)
                .with(PlayerSkill.CONSISTENCY, 20).with(PlayerSkill.FARMING, 5);
        PlayerRatings high = PlayerRatings.neutral(Position.ADC)
                .with(PlayerSkill.CONSISTENCY, 20).with(PlayerSkill.FARMING, 20);
        int lowCs = 0;
        int highCs = 0;
        for (long seed = 1; seed <= 8; seed++) {
            lowCs += adcCsAtTenMinutes(simulator().simulate(
                    teamWithAdcRatings(low), new DummyDataFactory().createRedTeam(), seed));
            highCs += adcCsAtTenMinutes(simulator().simulate(
                    teamWithAdcRatings(high), new DummyDataFactory().createRedTeam(), seed));
        }
        assertTrue(highCs > lowCs);
    }

    private int adcCsAtTenMinutes(MatchTimeline timeline) {
        return timeline.getSnapshots().stream()
                .filter(snapshot -> snapshot.getTimeSeconds() == 600)
                .flatMap(snapshot -> snapshot.getPlayerSnapshots().stream())
                .filter(player -> player.getTeamSide() == TeamSide.BLUE && player.getPosition() == Position.ADC)
                .mapToInt(PlayerSnapshot::getCs).findFirst().orElseThrow();
    }

    private Team teamWithAdcRatings(PlayerRatings ratings) {
        Team source = new DummyDataFactory().createBlueTeam();
        java.util.List<Player> players = source.getPlayers().stream()
                .map(player -> player.getPosition() == Position.ADC
                        ? new Player(player.getName(), Position.ADC, ratings, ChampionProficiencies.neutral())
                        : player)
                .toList();
        return new Team(source.getName(), players);
    }

    private MatchSimulator simulator() {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver());
    }

    private void assertOwned(Position position, PlayerSkill skill, ToDoubleFunction<PlayerState> outcome) {
        PlayerRatings lowRatings = PlayerRatings.neutral(position).with(PlayerSkill.CONSISTENCY, 20);
        PlayerRatings highRatings = lowRatings;
        if (skill == PlayerSkill.CONSISTENCY) return;
        lowRatings = lowRatings.with(skill, 5);
        highRatings = highRatings.with(skill, 20);
        assertTrue(outcome.applyAsDouble(player(position, highRatings, 14))
                        > outcome.applyAsDouble(player(position, lowRatings, 14)),
                () -> skill + " did not change its owned outcome");
    }

    private PlayerState player(Position position, PlayerRatings ratings, int proficiency) {
        PlayerMatchPerformance performance =
                PlayerMatchPerformance.realize(ratings, proficiency, 123L, TeamSide.BLUE);
        return new PlayerState("p", position, new PlayerAttributes(14, 14, 14, 14),
                performance, 500, true);
    }

    private double deviation(PlayerMatchPerformance performance) {
        return performance.asMap().entrySet().stream()
                .filter(entry -> entry.getKey() != PlayerSkill.CONSISTENCY)
                .mapToDouble(entry -> Math.abs(entry.getValue() - 14)).sum();
    }
}
