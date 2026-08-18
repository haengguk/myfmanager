package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionJungleClearEvaluator;
import com.lolfm.champion.ChampionLineupRequest;
import com.lolfm.champion.ChampionMatchupEvaluator;
import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.champion.ChampionPowerProfileEvaluator;
import com.lolfm.champion.ChampionResourceSet;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.champion.ChampionSelectionException;
import com.lolfm.champion.ChampionSelectionRequest;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.composition.ChampionCompositionProfile;
import com.lolfm.composition.CompositionCapability;
import com.lolfm.composition.CompositionPattern;
import com.lolfm.composition.TeamCompositionAnalysis;
import com.lolfm.composition.TeamCompositionAnalyzer;
import com.lolfm.composition.TeamCompositionLineup;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.Position;
import com.lolfm.factory.DummyDataFactory;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChampionFullIntegratedValidationTest {
    private static final long SEED = 13_005L;
    private static final ChampionResourceSet RESOURCES = ChampionResourceSet.loadDefault();
    private static final ChampionSelectionValidator SELECTIONS =
            new ChampionSelectionValidator(RESOURCES.catalog());

    @Test
    void powerProgressionRatingsAndProficiencyRemainIndependentLayers() {
        Map<String, String> representatives = new LinkedHashMap<>();
        representatives.put("renekton", "EARLY_DOMINANT");
        representatives.put("draven", "EARLY_DOMINANT");
        representatives.put("kennen", "EARLY_MID");
        representatives.put("jayce", "EARLY_MID");
        representatives.put("ambessa", "MID_SPIKE");
        representatives.put("aurora", "MID_SPIKE");
        representatives.put("jax", "LATE_SCALING");
        representatives.put("mel", "LATE_SCALING");
        representatives.put("azir", "HYPER_SCALING");
        representatives.put("smolder", "HYPER_SCALING");
        representatives.put("ornn", "TANK_SCALING");
        representatives.put("chogath", "TANK_SCALING");
        representatives.put("sejuani", "UTILITY_STABLE");
        representatives.put("ivern", "UTILITY_STABLE");
        representatives.put("ezreal", "BALANCED");
        representatives.put("garen", "BALANCED");

        ChampionPowerProfileEvaluator power = new ChampionPowerProfileEvaluator(RESOURCES.power());
        representatives.forEach((id, curve) -> {
            assertThat(RESOURCES.power().get(new ChampionId(id)).levelCurveId()).isEqualTo(curve);
            for (PowerCheckpoint checkpoint : List.of(
                    new PowerCheckpoint(1, ItemProgressStage.STARTING),
                    new PowerCheckpoint(6, ItemProgressStage.FIRST_CORE),
                    new PowerCheckpoint(11, ItemProgressStage.SECOND_CORE),
                    new PowerCheckpoint(16, ItemProgressStage.THIRD_CORE),
                    new PowerCheckpoint(18, ItemProgressStage.FULL_BUILD))) {
                assertThat(power.evaluate(new ChampionId(id), checkpoint.level(), checkpoint.stage(),
                        ProgressionCombatContext.TEAMFIGHT).clampedPlayerChampionPower()).isFinite();
            }
        });

        double earlyRenekton = score(power, "renekton", 3, ItemProgressStage.STARTING,
                ProgressionCombatContext.LANE_COMBAT);
        double earlyJax = score(power, "jax", 3, ItemProgressStage.STARTING,
                ProgressionCombatContext.LANE_COMBAT);
        double lateRenekton = score(power, "renekton", 18, ItemProgressStage.FULL_BUILD,
                ProgressionCombatContext.GENERIC_SKIRMISH);
        double lateJax = score(power, "jax", 18, ItemProgressStage.FULL_BUILD,
                ProgressionCombatContext.GENERIC_SKIRMISH);
        assertThat(earlyRenekton).isGreaterThan(earlyJax);
        assertThat(lateJax).isGreaterThan(lateRenekton);
        assertThat(score(power, "ambessa", 11, ItemProgressStage.SECOND_CORE,
                ProgressionCombatContext.GENERIC_SKIRMISH))
                .isGreaterThan(score(power, "ambessa", 3, ItemProgressStage.STARTING,
                        ProgressionCombatContext.GENERIC_SKIRMISH));

        PlayerProgressionState scaler = new PlayerProgressionState(Position.TOP);
        scaler.awardExperience(ExperienceSource.KILL, 7_300, 600);
        scaler.awardEarnedGold(7_000, GoldSource.KILL, 600);
        double progressedJax = score(power, "jax", scaler.getLevel(), scaler.getItemStage(),
                ProgressionCombatContext.LANE_COMBAT)
                + new PlayerProgressionPowerEvaluator().evaluate(
                        scaler, ProgressionCombatContext.LANE_COMBAT, true).clampedTotalPower();
        assertThat(progressedJax).isGreaterThan(earlyRenekton);
        assertThat(scaler.getExperienceBySource()).containsKey(ExperienceSource.KILL);
        assertThat(scaler.getItemStage()).isNotEqualTo(ItemProgressStage.STARTING);

        PlayerSkillEvaluator skills = new PlayerSkillEvaluator();
        PlayerRatings base = PlayerRatings.neutral(Position.MID).with(PlayerSkill.CONSISTENCY, 20);
        PlayerState lowMechanics = player(base.with(PlayerSkill.MECHANICS, 5), 14);
        PlayerState highMechanics = player(base.with(PlayerSkill.MECHANICS, 20), 14);
        assertThat(skills.combatExecution(highMechanics)).isGreaterThan(skills.combatExecution(lowMechanics));

        PlayerState lowProficiency = player(base, 5);
        PlayerState highProficiency = player(base, 20);
        assertThat(skills.combatExecution(highProficiency)).isGreaterThan(skills.combatExecution(lowProficiency));
        assertThat(skills.farming(highProficiency)).isEqualTo(skills.farming(lowProficiency));
        assertThat(skills.decisionQuality(highProficiency)).isEqualTo(skills.decisionQuality(lowProficiency));

        PlayerState lowDecision = player(base.with(PlayerSkill.DECISION_MAKING, 5), 14);
        PlayerState highDecision = player(base.with(PlayerSkill.DECISION_MAKING, 20), 14);
        assertThat(skills.decisionQuality(highDecision)).isGreaterThan(skills.decisionQuality(lowDecision));
        assertThat(skills.combatExecution(highDecision)).isEqualTo(skills.combatExecution(lowDecision));
    }

    @Test
    void matchupDirectionalityIsFiniteBoundedAndOpponentSpecific() {
        ChampionMatchupEvaluator evaluator = new ChampionMatchupEvaluator(RESOURCES.matchup());
        List<MatchupCase> cases = List.of(
                matchup("renekton", "kayle", Position.TOP),
                matchup("vayne", "ornn", Position.TOP),
                matchup("gwen", "ksante", Position.TOP),
                matchup("poppy", "camille", Position.TOP),
                matchup("fiora", "malphite", Position.TOP),
                matchup("akali", "garen", Position.TOP),
                matchup("lee-sin", "karthus", Position.JUNGLE),
                matchup("kindred", "rammus", Position.JUNGLE),
                matchup("zed", "xerath", Position.MID),
                matchup("zed", "lissandra", Position.MID),
                matchup("lux", "yasuo", Position.MID),
                matchup("cassiopeia", "galio", Position.MID),
                matchup("sylas", "orianna", Position.MID),
                matchup("draven", "smolder", Position.ADC),
                matchup("vayne", "kogmaw", Position.ADC),
                matchup("ezreal", "samira", Position.ADC),
                matchup("leona", "janna", Position.SUPPORT),
                matchup("nautilus", "milio", Position.SUPPORT));

        List<Double> edges = new ArrayList<>();
        for (MatchupCase value : cases) {
            double forward = evaluator.evaluate(value.source(), value.opponent(),
                    ProgressionCombatContext.LANE_COMBAT, ChampionMatchupMode.GEOMETRIC_V2).finalEdge();
            double reverse = evaluator.evaluate(value.opponent(), value.source(),
                    ProgressionCombatContext.LANE_COMBAT, ChampionMatchupMode.GEOMETRIC_V2).finalEdge();
            assertThat(forward).isFinite().isBetween(-.30, .30);
            assertThat(reverse).isEqualTo(-forward);
            edges.add(forward);
        }
        assertThat(edges.stream().distinct().count()).isGreaterThan(8);
        assertThat(edges).anyMatch(value -> value > 0).anyMatch(value -> value < 0);
        assertThat(edges.get(8)).isNotEqualTo(edges.get(9));
    }

    @Test
    void flexProfilesAndSelectionMaterializeByStructuredRoleWithoutLeakage() {
        Map<String, Set<Position>> flex = Map.of(
                "akali", Set.of(Position.MID, Position.TOP),
                "amumu", Set.of(Position.JUNGLE, Position.SUPPORT),
                "galio", Set.of(Position.TOP, Position.MID, Position.SUPPORT),
                "poppy", Set.of(Position.TOP, Position.JUNGLE, Position.SUPPORT),
                "senna", Set.of(Position.ADC, Position.SUPPORT),
                "taliyah", Set.of(Position.MID, Position.JUNGLE),
                "yasuo", Set.of(Position.TOP, Position.MID, Position.ADC),
                "ziggs", Set.of(Position.ADC, Position.MID));
        boolean foundOverride = false;
        boolean foundBaseOnly = false;
        for (var entry : flex.entrySet()) {
            ChampionId id = new ChampionId(entry.getKey());
            assertThat(RESOURCES.catalog().get(id).supportedPositions()).containsAll(entry.getValue());
            List<Map<?, ?>> matchupVectors = new ArrayList<>();
            List<String> compositionVectors = new ArrayList<>();
            for (Position position : entry.getValue()) {
                ChampionRoleKey key = new ChampionRoleKey(id, position);
                assertThat(RESOURCES.catalog().supports(key)).isTrue();
                matchupVectors.add(RESOURCES.matchup().find(key).orElseThrow().traits());
                ChampionCompositionProfile composition = RESOURCES.composition().profiles().get(key);
                compositionVectors.add(composition.capabilities() + "|" + composition.damageProfile());
                assertThat(validateSubstitution(entry.getKey(), position).asMap()).hasSize(10);
            }
            boolean matchupDiffers = matchupVectors.stream().distinct().count() > 1;
            boolean compositionDiffers = compositionVectors.stream().distinct().count() > 1;
            foundOverride |= matchupDiffers || compositionDiffers;
            foundBaseOnly |= !matchupDiffers && !compositionDiffers;
        }
        assertThat(foundOverride).isTrue();
        assertThat(foundBaseOnly).isTrue();
        assertThatThrownBy(() -> validateSubstitution("akali", Position.SUPPORT))
                .isInstanceOf(ChampionSelectionException.class);
    }

    @Test
    void representativeCompositionStylesAndDamageChannelsRemainDifferentiated() {
        Map<String, ChampionLineupRequest> styles = styleLineups();
        Map<String, TeamCompositionAnalysis> analyses = new LinkedHashMap<>();
        styles.forEach((name, lineup) -> analyses.put(name, analyze(lineup)));

        assertThat(analyses.get("ENGAGE").patterns().get(CompositionPattern.ENGAGE_CHAIN).readiness())
                .isGreaterThan(analyses.get("PEEL").patterns().get(CompositionPattern.ENGAGE_CHAIN).readiness());
        assertThat(analyses.get("FRONT_TO_BACK").patterns().get(CompositionPattern.FRONT_TO_BACK).readiness())
                .isGreaterThan(analyses.get("DIVE").patterns().get(CompositionPattern.FRONT_TO_BACK).readiness());
        assertThat(analyses.get("POKE").patterns().get(CompositionPattern.POKE_SIEGE).readiness())
                .isGreaterThan(analyses.get("DIVE").patterns().get(CompositionPattern.POKE_SIEGE).readiness());
        assertThat(analyses.get("PICK").patterns().get(CompositionPattern.PICK_CONVERSION).readiness())
                .isGreaterThan(analyses.get("FRONT_TO_BACK").patterns().get(CompositionPattern.PICK_CONVERSION).readiness());
        assertThat(analyses.get("SPLIT").patterns().get(CompositionPattern.SPLIT_MAP_PRESSURE).readiness())
                .isGreaterThan(analyses.get("ENGAGE").patterns().get(CompositionPattern.SPLIT_MAP_PRESSURE).readiness());
        assertThat(analyses.get("OBJECTIVE").patterns().get(CompositionPattern.OBJECTIVE_CONTROL).readiness())
                .isGreaterThan(analyses.get("PICK").patterns().get(CompositionPattern.OBJECTIVE_CONTROL).readiness());
        assertThat(analyses.get("DIVE").coverage().capability(CompositionCapability.BACKLINE_ACCESS).coverage())
                .isGreaterThan(analyses.get("PEEL").coverage().capability(CompositionCapability.BACKLINE_ACCESS).coverage());
        assertThat(analyses.get("PEEL").coverage().capability(CompositionCapability.PEEL).coverage())
                .isGreaterThan(analyses.get("DIVE").coverage().capability(CompositionCapability.PEEL).coverage());

        assertThat(analyses.values()).allSatisfy(analysis -> {
            analysis.coverage().capabilities().values().forEach(value ->
                    assertThat(value.coverage()).isFinite().isBetween(0.0, 1.0));
            var damage = analysis.coverage().damageChannels();
            assertThat(damage.physicalShare()).isFinite();
            assertThat(damage.magicShare()).isFinite();
            assertThat(damage.trueDamageShare()).isFinite();
        });
        assertThat(analyses.values().stream().map(TeamCompositionAnalysis::coverage).distinct().count())
                .isEqualTo(styles.size());

        long superProviders = RESOURCES.composition().profiles().values().stream()
                .filter(profile -> java.util.Arrays.stream(CompositionCapability.values())
                        .filter(capability -> profile.capability(capability) >= 19).count() >= 10)
                .count();
        assertThat(superProviders).isZero();

        Map<String, TeamCompositionAnalysis> damage = Map.of(
                "PHYSICAL", analyze(lineup("darius", "lee-sin", "zed", "draven", "pyke")),
                "MAGIC", analyze(lineup("ornn", "karthus", "anivia", "ziggs", "velkoz")),
                "MIXED", analyses.get("FRONT_TO_BACK"),
                "TRUE", analyze(lineup("camille", "belveth", "velkoz", "vayne", "thresh")),
                "UTILITY", analyses.get("PEEL"));
        assertThat(damage.get("PHYSICAL").coverage().damageChannels().physicalShare()).isGreaterThan(.55);
        assertThat(damage.get("MAGIC").coverage().damageChannels().magicShare()).isGreaterThan(.55);
        assertThat(damage.get("MIXED").coverage().damageChannels().physicalShare()).isBetween(.20, .70);
        assertThat(damage.get("MIXED").coverage().damageChannels().magicShare()).isBetween(.20, .70);
        assertThat(damage.get("TRUE").coverage().damageChannels().trueDamageShare()).isGreaterThan(.05);
        assertThat(totalThreat(damage.get("UTILITY")))
                .isLessThan(totalThreat(damage.get("PHYSICAL")));
    }

    @Test
    void jungleClearCandidateRemainsDormantAndConsumesNoRuntimeInput() {
        assertThat(RESOURCES.jungleClear().profiles()).hasSize(51);
        assertThat(RESOURCES.jungleClear().profiles().values())
                .allMatch(profile -> !profile.gameplayEnabled());
        assertThat(RESOURCES.jungleClear().profiles().values().stream()
                .map(profile -> profile.early()).distinct().count()).isGreaterThan(1);
        ChampionJungleClearEvaluator evaluator = new ChampionJungleClearEvaluator();
        RESOURCES.jungleClear().profiles().values().stream().limit(8).forEach(profile -> {
            assertThat(evaluator.evaluate(profile, 600)).isEqualTo(1.0);
            assertThat(evaluator.evaluate(profile, 1_200)).isEqualTo(1.0);
            assertThat(evaluator.evaluate(profile, 2_100)).isEqualTo(1.0);
        });
    }

    @Test
    void eightRepresentativeFullMatchesCompleteWithAllProductionLayers() {
        List<ChampionSelectionRequest> cases = matchCases();
        MatchSimulator simulator = simulator();
        for (int index = 0; index < cases.size(); index++) {
            MatchChampionAssignments assignments = SELECTIONS.resolve(cases.get(index));
            SideOrientationRandomTraceObserver random = new SideOrientationRandomTraceObserver(
                    SEED + index, "ORIGINAL", "BLUE_LOGICAL", "RED_LOGICAL", false);
            MatchSimulator.SimulationResult result = simulator.simulateWithSideDiagnostics(
                    new DummyDataFactory().createBlueTeam(), new DummyDataFactory().createRedTeam(),
                    assignments, random);
            assertThat(result.winnerSide()).isIn(TeamSide.BLUE, TeamSide.RED);
            assertThat(result.timeline().getDurationSeconds()).isBetween(1, MatchSimulator.SIMULATION_SAFETY_TIMEOUT_SECONDS);
            assertThat(result.timeline().getEvents()).isNotEmpty();
            assertThat(result.timeline().getSnapshots()).isNotEmpty();
            assertThat(result.championPowerExecutionStats().samples()).isNotEmpty();
            assertThat(result.championMatchupExecutionStats().totalPairApplications()).isGreaterThan(0);
            assertThat(result.championMatchupExecutionStats().directRandomCalls()).isZero();
            assertThat(result.compositionRuntimeDiagnostics().initialized()).isTrue();
            assertThat(result.compositionRuntimeDiagnostics().contextEdgeCount()).isGreaterThan(0);
            assertThat(result.compositionRuntimeDiagnostics().actualAttemptCount()).isGreaterThan(0);
            assertThat(result.compositionRuntimeDiagnostics().mappedActualAttemptCount()).isGreaterThan(0);
            assertThat(result.compositionRuntimeDiagnostics().directRandomCallCount()).isZero();
            assertThat(result.compositionRuntimeDiagnostics().compositionRandomDrawCount()).isZero();
            assertThat(result.randomDrawCount()).isGreaterThan(0);
        }
    }

    @Test
    void sameSeedReplayAndFourSideSwapsAreExactAndLegal() throws Exception {
        List<ChampionSelectionRequest> cases = matchCases();
        MatchChampionAssignments assignments = SELECTIONS.resolve(cases.getFirst());
        MatchSimulator simulator = simulator();
        MatchSimulator.SimulationResult first = traced(simulator, assignments, "REPLAY_A");
        MatchSimulator.SimulationResult second = traced(simulator, assignments, "REPLAY_A");
        ObjectMapper mapper = new ObjectMapper();
        assertThat(mapper.writeValueAsString(first.timeline()))
                .isEqualTo(mapper.writeValueAsString(second.timeline()));
        assertThat(first.winnerSide()).isEqualTo(second.winnerSide());
        assertThat(first.randomDrawCount()).isEqualTo(second.randomDrawCount());
        assertThat(first.randomTrace()).isEqualTo(second.randomTrace());
        assertThat(first.championPowerExecutionStats()).isEqualTo(second.championPowerExecutionStats());
        assertThat(first.championMatchupExecutionStats()).isEqualTo(second.championMatchupExecutionStats());

        for (int index = 0; index < 4; index++) {
            ChampionSelectionRequest original = cases.get(index);
            ChampionSelectionRequest swapped = new ChampionSelectionRequest(original.red(), original.blue());
            MatchChampionAssignments originalAssignments = SELECTIONS.resolve(original);
            MatchChampionAssignments swappedAssignments = SELECTIONS.resolve(swapped);
            for (Position position : Position.values()) {
                assertThat(originalAssignments.get(new PlayerKey(TeamSide.BLUE, position)).championId())
                        .isEqualTo(swappedAssignments.get(new PlayerKey(TeamSide.RED, position)).championId());
                assertThat(originalAssignments.get(new PlayerKey(TeamSide.RED, position)).championId())
                        .isEqualTo(swappedAssignments.get(new PlayerKey(TeamSide.BLUE, position)).championId());
            }
        }
    }

    private double score(ChampionPowerProfileEvaluator evaluator, String id, int level,
                         ItemProgressStage stage, ProgressionCombatContext context) {
        return evaluator.evaluate(new ChampionId(id), level, stage, context).clampedPlayerChampionPower();
    }

    private PlayerState player(PlayerRatings ratings, int proficiency) {
        return new PlayerState("same-champion-player", Position.MID,
                new PlayerAttributes(14, 14, 14, 14),
                PlayerMatchPerformance.realize(ratings, proficiency, 123L, TeamSide.BLUE), 500, true);
    }

    private MatchupCase matchup(String source, String opponent, Position position) {
        return new MatchupCase(new ChampionRoleKey(new ChampionId(source), position),
                new ChampionRoleKey(new ChampionId(opponent), position));
    }

    private MatchChampionAssignments validateSubstitution(String champion, Position position) {
        ChampionSelectionRequest defaults = RESOURCES.catalog().defaultSelection();
        return SELECTIONS.resolve(new ChampionSelectionRequest(
                replace(defaults.blue(), champion, position), defaults.red()));
    }

    private ChampionLineupRequest replace(ChampionLineupRequest value, String champion, Position position) {
        return new ChampionLineupRequest(
                position == Position.TOP ? champion : value.top(),
                position == Position.JUNGLE ? champion : value.jgl(),
                position == Position.MID ? champion : value.mid(),
                position == Position.ADC ? champion : value.adc(),
                position == Position.SUPPORT ? champion : value.sup());
    }

    private TeamCompositionAnalysis analyze(ChampionLineupRequest lineup) {
        EnumMap<Position, ChampionRoleKey> keys = new EnumMap<>(Position.class);
        keys.put(Position.TOP, new ChampionRoleKey(new ChampionId(lineup.top()), Position.TOP));
        keys.put(Position.JUNGLE, new ChampionRoleKey(new ChampionId(lineup.jgl()), Position.JUNGLE));
        keys.put(Position.MID, new ChampionRoleKey(new ChampionId(lineup.mid()), Position.MID));
        keys.put(Position.ADC, new ChampionRoleKey(new ChampionId(lineup.adc()), Position.ADC));
        keys.put(Position.SUPPORT, new ChampionRoleKey(new ChampionId(lineup.sup()), Position.SUPPORT));
        keys.values().forEach(key -> assertThat(RESOURCES.catalog().supports(key)).isTrue());
        return new TeamCompositionAnalyzer().analyze(
                new TeamCompositionLineup(keys), RESOURCES.composition().profiles());
    }

    private int totalThreat(TeamCompositionAnalysis analysis) {
        var damage = analysis.coverage().damageChannels();
        return damage.totalPhysicalThreat() + damage.totalMagicThreat()
                + damage.totalTrueDamageThreat();
    }

    private Map<String, ChampionLineupRequest> styleLineups() {
        Map<String, ChampionLineupRequest> values = new LinkedHashMap<>();
        values.put("ENGAGE", lineup("malphite", "jarvan-iv", "orianna", "miss-fortune", "nautilus"));
        values.put("PEEL", lineup("shen", "ivern", "viktor", "jinx", "janna"));
        values.put("FRONT_TO_BACK", lineup("ornn", "sejuani", "azir", "aphelios", "lulu"));
        values.put("DIVE", lineup("ambessa", "lee-sin", "akali", "kaisa", "rakan"));
        values.put("POKE", lineup("jayce", "nidalee", "xerath", "ezreal", "lux"));
        values.put("SPLIT", lineup("fiora", "nocturne", "twisted-fate", "sivir", "bard"));
        values.put("PICK", lineup("camille", "vi", "ahri", "jhin", "thresh"));
        values.put("OBJECTIVE", lineup("ksante", "maokai", "anivia", "kogmaw", "braum"));
        return values;
    }

    private List<ChampionSelectionRequest> matchCases() {
        List<ChampionLineupRequest> lineups = List.copyOf(styleLineups().values());
        List<ChampionSelectionRequest> result = new ArrayList<>();
        for (int index = 0; index < lineups.size(); index += 2) {
            result.add(new ChampionSelectionRequest(lineups.get(index), lineups.get(index + 1)));
        }
        for (int index = 0; index < lineups.size(); index += 2) {
            result.add(new ChampionSelectionRequest(lineups.get(index + 1), lineups.get(index)));
        }
        return List.copyOf(result);
    }

    private ChampionLineupRequest lineup(String top, String jungle, String mid, String adc, String support) {
        return new ChampionLineupRequest(top, jungle, mid, adc, support);
    }

    private MatchSimulator simulator() {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), SimulationOptions.productionDefaults(),
                RESOURCES.matchup());
    }

    private MatchSimulator.SimulationResult traced(
            MatchSimulator simulator, MatchChampionAssignments assignments, String orientation
    ) {
        SideOrientationRandomTraceObserver random = new SideOrientationRandomTraceObserver(
                SEED, orientation, "BLUE_LOGICAL", "RED_LOGICAL", true);
        return simulator.simulateWithSideDiagnostics(
                new DummyDataFactory().createBlueTeam(), new DummyDataFactory().createRedTeam(),
                assignments, random);
    }

    private record PowerCheckpoint(int level, ItemProgressStage stage) {}
    private record MatchupCase(ChampionRoleKey source, ChampionRoleKey opponent) {}
}
