package com.lolfm.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lolfm.champion.*;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Position;
import com.lolfm.factory.DummyDataFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChampionFoundationDiagnostics {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ChampionCatalog CATALOG = new ChampionCatalog(JSON);
    private static final ChampionSelectionValidator VALIDATOR = new ChampionSelectionValidator(CATALOG);

    public static void main(String[] args) {
        ChampionSelectionRequest c = selection(
                List.of("renekton","sejuani","azir","jinx","nautilus"),
                List.of("jax","lee-sin","ahri","kaisa","rakan"));
        ChampionSelectionRequest d = selection(
                List.of("ornn","vi","orianna","lucian","lulu"),
                List.of("gwen","nidalee","leblanc","ezreal","braum"));
        ChampionSelectionRequest e = selection(
                List.of("kennen","viego","sylas","aphelios","renata-glasc"),
                List.of("ksante","maokai","viktor","varus","bard"));

        int assignmentErrors = 0;
        int snapshotMismatches = 0;
        int gameplayMismatches = 0;
        for (int seed = 1; seed <= 1000; seed++) {
            MatchTimeline defaults = run(seed, null);
            MatchTimeline tc = run(seed, c);
            MatchTimeline td = run(seed, d);
            MatchTimeline te = run(seed, e);
            assignmentErrors += validateTimeline(defaults) + validateTimeline(tc) + validateTimeline(td) + validateTimeline(te);
            String pc = gameplayProjection(tc);
            if (!pc.equals(gameplayProjection(td))) gameplayMismatches++;
            if (!pc.equals(gameplayProjection(te))) gameplayMismatches++;
            if (!sameChampionEverySnapshot(tc)) snapshotMismatches++;
            if (seed % 100 == 0) System.out.println("champion-foundation seeds=" + seed + " gameplayMismatches=" + gameplayMismatches);
        }

        int invalidFailures = invalidFocusedCases();
        int isolationErrors = isolationCheck(c, d);
        Map<String, Integer> countByPosition = new LinkedHashMap<>();
        for (Position position : Position.values()) countByPosition.put(position.name(), CATALOG.forPosition(position).size());
        ChampionFoundationExecutionStats stats = new ChampionFoundationExecutionStats(
                CATALOG.all().size(), countByPosition, 0, 0, 0, 0, 0, 10,
                assignmentErrors, 0, 0, 0, 0, 0, 0, 0,
                ChampionNeutrality.COMBAT_CONTRIBUTION == 0 ? 0 : 1,
                ChampionNeutrality.MULTIPLIER == 1 ? 0 : 1,
                ChampionNeutrality.SPIKE_BONUS == 0 ? 0 : 1,
                ChampionNeutrality.CONTEXT_MODIFIER == 0 ? 0 : 1,
                snapshotMismatches, 0, 0, 0, isolationErrors, gameplayMismatches);
        System.out.println("CHAMPION_FOUNDATION_STATS=" + stats);
        System.out.println("INVALID_FOCUSED_FAILURES=" + invalidFailures + "/6");
        if (assignmentErrors != 0 || snapshotMismatches != 0 || gameplayMismatches != 0
                || invalidFailures != 6 || isolationErrors != 0) {
            throw new IllegalStateException("Champion Foundation diagnostics failed");
        }
        System.out.println("CHAMPION_FOUNDATION_DIAGNOSTICS=PASS");
    }

    private static MatchTimeline run(long seed, ChampionSelectionRequest request) {
        DummyDataFactory teams = new DummyDataFactory();
        return simulator().simulate(teams.createBlueTeam(), teams.createRedTeam(), seed, VALIDATOR.resolve(request));
    }
    private static MatchSimulator simulator() {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(CATALOG),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), SimulationOptions.productionDefaults());
    }
    private static int validateTimeline(MatchTimeline timeline) {
        return timeline.getSnapshots().stream().anyMatch(snapshot -> snapshot.getPlayerSnapshots().size() != 10
                || snapshot.getPlayerSnapshots().stream().anyMatch(player -> player.getChampion() == null
                || player.getChampionPosition() != player.getPosition())) ? 1 : 0;
    }
    private static boolean sameChampionEverySnapshot(MatchTimeline timeline) {
        List<String> first = timeline.getSnapshots().getFirst().getPlayerSnapshots().stream().map(p -> p.getChampionId()).toList();
        return timeline.getSnapshots().stream().allMatch(snapshot ->
                snapshot.getPlayerSnapshots().stream().map(p -> p.getChampionId()).toList().equals(first));
    }
    private static String gameplayProjection(MatchTimeline timeline) {
        JsonNode tree = JSON.valueToTree(timeline);
        removeChampionMetadata(tree);
        return tree.toString();
    }
    private static void removeChampionMetadata(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<String> championFields = new java.util.ArrayList<>();
            object.fieldNames().forEachRemaining(name -> { if (name.toLowerCase().startsWith("champion")) championFields.add(name); });
            championFields.forEach(object::remove);
            object.elements().forEachRemaining(ChampionFoundationDiagnostics::removeChampionMetadata);
        } else if (node.isArray()) node.elements().forEachRemaining(ChampionFoundationDiagnostics::removeChampionMetadata);
    }
    private static int invalidFocusedCases() {
        ChampionLineupRequest red = new ChampionLineupRequest("jax","lee-sin","ahri","kaisa","rakan");
        List<ChampionSelectionRequest> cases = List.of(
                new ChampionSelectionRequest(new ChampionLineupRequest("unknown","sejuani","azir","jinx","nautilus"), red),
                new ChampionSelectionRequest(null, red),
                new ChampionSelectionRequest(new ChampionLineupRequest("azir","sejuani","renekton","jinx","nautilus"), red),
                new ChampionSelectionRequest(new ChampionLineupRequest("renekton","sejuani","azir","jinx","jinx"), red),
                new ChampionSelectionRequest(new ChampionLineupRequest("renekton","sejuani","azir","jinx","nautilus"), new ChampionLineupRequest("renekton","lee-sin","ahri","kaisa","rakan")),
                new ChampionSelectionRequest(new ChampionLineupRequest(" ","sejuani","azir","jinx","nautilus"), red));
        int failures = 0;
        for (ChampionSelectionRequest request : cases) try { VALIDATOR.resolve(request); } catch (ChampionSelectionException expected) { failures++; }
        return failures;
    }
    private static int isolationCheck(ChampionSelectionRequest first, ChampionSelectionRequest second) {
        MatchTimeline a = run(7, first);
        MatchTimeline b = run(7, second);
        String aTop = a.getSnapshots().getFirst().getPlayerSnapshots().stream().filter(p -> p.getTeamSide() == TeamSide.BLUE && p.getPosition() == Position.TOP).findFirst().orElseThrow().getChampionId();
        String bTop = b.getSnapshots().getFirst().getPlayerSnapshots().stream().filter(p -> p.getTeamSide() == TeamSide.BLUE && p.getPosition() == Position.TOP).findFirst().orElseThrow().getChampionId();
        return aTop.equals("renekton") && bTop.equals("ornn") ? 0 : 1;
    }
    private static ChampionSelectionRequest selection(List<String> blue, List<String> red) {
        return new ChampionSelectionRequest(lineup(blue), lineup(red));
    }
    private static ChampionLineupRequest lineup(List<String> ids) {
        return new ChampionLineupRequest(ids.get(0), ids.get(1), ids.get(2), ids.get(3), ids.get(4));
    }
}
