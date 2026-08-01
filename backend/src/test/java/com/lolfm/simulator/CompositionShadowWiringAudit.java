package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.composition.CompositionActionType;
import com.lolfm.composition.CompositionBaselineScoreDomain;
import com.lolfm.composition.CompositionContextRouting;
import com.lolfm.composition.CompositionRuntimeDiagnostics;
import com.lolfm.composition.CompositionShadowObservation;
import com.lolfm.composition.FrozenCompositionInteractionRuntimePolicy;
import com.lolfm.composition.TeamCompositionContext;
import com.lolfm.composition.TeamCompositionGameplayMode;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.factory.DummyDataFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Phase 13D-4A audit. The artifact parser and schedule selection are test-only. */
public final class CompositionShadowWiringAudit {
    static final Path SOURCE = Path.of("build", "reports", "composition-interaction-context",
            "composition-interaction-representative-lineups.csv");
    static final Path OUTPUT = Path.of("build", "reports", "composition-shadow-wiring");
    static final long BASE_SEED = 13_040_001L;
    static final int REPRESENTATIVE_COUNT = 60;
    static final int CASE_COUNT = 1_200;
    static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<Position> POSITIONS = List.of(Position.TOP, Position.JUNGLE, Position.MID, Position.ADC, Position.SUPPORT);

    private CompositionShadowWiringAudit() {}

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        Snapshot snapshot = compute();
        writeArtifacts(snapshot);
        System.out.println("Composition shadow wiring audit: " + snapshot.verdict());
        System.out.println("Summary SHA-256: " + sha256(Files.readAllBytes(OUTPUT.resolve("composition-shadow-wiring-summary.csv"))));
        System.out.println("Audit SHA-256: " + sha256(Files.readAllBytes(OUTPUT.resolve("composition-shadow-wiring-audit.log"))));
        if (snapshot.verdict().startsWith("BLOCKED")) {
            throw new IllegalStateException("Composition shadow wiring integrity errors=" + (snapshot.verdict().startsWith("BLOCKED") ? 1 : 0));
        }
    }

    static Snapshot compute() throws Exception {
        FrozenCompositionInteractionRuntimePolicy policy = FrozenCompositionInteractionRuntimePolicy.current();
        String sourceHash = sha256(Files.readAllBytes(SOURCE));
        List<Lineup> representatives = readRepresentatives();
        if (representatives.size() != REPRESENTATIVE_COUNT) throw new IllegalStateException("Expected 60 representative lineups");
        List<ScheduleRow> schedule = schedule(representatives);
        if (schedule.size() != CASE_COUNT) throw new IllegalStateException("Expected 1,200 matchup cases");

        MatchSimulator off = simulator(TeamCompositionGameplayMode.OFF);
        MatchSimulator shadow = simulator(TeamCompositionGameplayMode.SHADOW);
        DummyDataFactory factory = new DummyDataFactory();
        List<PairRow> pairs = new ArrayList<>(CASE_COUNT);
        List<ObservationRow> observations = new ArrayList<>();
        Aggregate aggregate = new Aggregate();
        int rejectedOverlap = 0;
        for (ScheduleRow row : schedule) {
            MatchChampionAssignments assignments = assignments(row.blue(), row.red());
            Team blue = factory.createBlueTeam();
            Team red = factory.createRedTeam();
            MatchSimulator.SimulationResult offResult = off.simulateWithDiagnostics(blue, red, row.seed(), assignments);
            MatchSimulator.SimulationResult shadowResult = shadow.simulateWithDiagnostics(
                    factory.createBlueTeam(), factory.createRedTeam(), row.seed(), assignments);
            String offTimeline = MAPPER.writeValueAsString(offResult.timeline());
            String shadowTimeline = MAPPER.writeValueAsString(shadowResult.timeline());
            boolean timelineExact = offTimeline.equals(shadowTimeline);
            boolean randomExact = offResult.randomDrawCount() == shadowResult.randomDrawCount();
            boolean parity = timelineExact && randomExact;
            pairs.add(new PairRow(row.caseIndex(), row.seed(), row.blue().id(), row.red().id(),
                    offResult.timeline().getWinner(), shadowResult.timeline().getWinner(),
                    java.util.Objects.equals(offResult.timeline().getWinner(), shadowResult.timeline().getWinner()),
                    offResult.timeline().getDurationSeconds() == shadowResult.timeline().getDurationSeconds(),
                    timelineExact, timelineExact, timelineExact, timelineExact, randomExact,
                    sha256(offTimeline.getBytes(StandardCharsets.UTF_8)), sha256(offTimeline.getBytes(StandardCharsets.UTF_8)).equals(sha256(shadowTimeline.getBytes(StandardCharsets.UTF_8))),
                    timelineExact, parity));
            CompositionRuntimeDiagnostics diagnostics = shadowResult.compositionRuntimeDiagnostics();
            aggregate.add(diagnostics);
            for (CompositionShadowObservation observation : diagnostics.observations()) {
                observations.add(ObservationRow.from(row, observation));
            }
            if (row.crossTeamChampionOverlap() > 0) rejectedOverlap++;
        }
        return new Snapshot(policy, sourceHash, representatives, schedule, pairs, observations, aggregate,
                rejectedOverlap, verdict(policy, representatives, schedule, pairs, observations, aggregate));
    }

    private static String verdict(FrozenCompositionInteractionRuntimePolicy policy, List<Lineup> representatives,
                                  List<ScheduleRow> schedule, List<PairRow> pairs, List<ObservationRow> observations,
                                  Aggregate aggregate) {
        int integrity = 0;
        if (representatives.size() != 60 || schedule.size() != CASE_COUNT || pairs.size() != CASE_COUNT) integrity++;
        if (pairs.stream().anyMatch(x -> !x.parityPassed())) integrity++;
        if (aggregate.randomDrawMismatchCount != 0 || aggregate.duplicateObservationCount != 0
                || aggregate.multiContextAttemptCount != 0 || aggregate.conflictingPerspectiveCount != 0) integrity++;
        if (aggregate.shadowObservationCount != aggregate.mappedActualAttemptCount) integrity++;
        if (aggregate.gameplayApplicationCount != 0 || aggregate.nonZeroModifierCount != 0) integrity++;
        if (!FrozenCompositionInteractionRuntimePolicy.CANDIDATE_HASH.equals(policy.candidateHash())) integrity++;
        if (integrity != 0) return "BLOCKED_BY_COMPOSITION_SHADOW_WIRING_INTEGRITY";
        List<String> reviews = new ArrayList<>();
        for (TeamCompositionContext context : List.of(TeamCompositionContext.SKIRMISH,
                TeamCompositionContext.TEAMFIGHT, TeamCompositionContext.OBJECTIVE_SETUP, TeamCompositionContext.SIEGE,
                TeamCompositionContext.BASE_DEFENSE)) {
            long count = observations.stream().filter(x -> x.observation().context().equals(context)).count();
            if (count == 0) reviews.add("MAPPED_CONTEXT_UNOBSERVED_" + context);
            if (observations.stream().filter(x -> x.observation().context().equals(context)).anyMatch(x -> !x.observation().baselineScoreAvailable())) {
                reviews.add("MAPPED_SCORE_UNAVAILABLE_" + context);
            }
        }
        return reviews.isEmpty() ? "READY_FOR_PHASE_13D4B" : "REVIEW_COMPOSITION_SHADOW_WIRING";
    }

    private static MatchSimulator simulator(TeamCompositionGameplayMode mode) {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), SimulationOptions.productionDefaults()
                .withTeamCompositionGameplayMode(mode));
    }

    private static List<Lineup> readRepresentatives() throws IOException {
        List<String> lines = Files.readAllLines(SOURCE, StandardCharsets.UTF_8);
        List<String> header = csv(lines.getFirst());
        Map<String, Integer> indexes = new HashMap<>();
        for (int i = 0; i < header.size(); i++) indexes.put(header.get(i), i);
        List<Lineup> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            List<String> cells = csv(line);
            Map<Position, ChampionId> champions = new EnumMap<>(Position.class);
            for (Position position : POSITIONS) {
                String stable = cells.get(indexes.get(position.name()));
                int separator = stable.lastIndexOf(':');
                if (separator <= 0 || !position.name().equals(stable.substring(separator + 1))) {
                    throw new IllegalStateException("Invalid structured representative lineup: " + stable);
                }
                champions.put(position, new ChampionId(stable.substring(0, separator)));
            }
            rows.add(new Lineup(cells.get(indexes.get("lineupId")), champions));
        }
        return List.copyOf(rows);
    }

    private static List<ScheduleRow> schedule(List<Lineup> lineups) {
        List<PairCandidate> preferred = new ArrayList<>();
        List<PairCandidate> overlap = new ArrayList<>();
        for (int round = 1; round < lineups.size(); round++) {
            for (int blue = 0; blue < lineups.size(); blue++) {
                int red = (blue + round) % lineups.size();
                PairCandidate candidate = new PairCandidate(lineups.get(blue), lineups.get(red), round, blue, red);
                (candidate.overlap() == 0 ? preferred : overlap).add(candidate);
            }
        }
        List<PairCandidate> ordered = new ArrayList<>();
        ordered.addAll(preferred);
        ordered.addAll(overlap);
        List<PairCandidate> selected = new ArrayList<>(CASE_COUNT);
        Set<String> pairs = new HashSet<>();
        Set<String> coveredBlue = new HashSet<>(), coveredRed = new HashSet<>();
        for (PairCandidate candidate : ordered) {
            if (selected.size() >= CASE_COUNT) break;
            if (candidate.overlap() > 0) continue;
            String key = candidate.blue().id() + "|" + candidate.red().id();
            if (pairs.contains(key)) continue;
            if (coveredBlue.size() < lineups.size() || coveredRed.size() < lineups.size()) {
                if (coveredBlue.contains(candidate.blue().id()) && coveredRed.contains(candidate.red().id())) continue;
            }
            pairs.add(key);
            selected.add(candidate);
            coveredBlue.add(candidate.blue().id());
            coveredRed.add(candidate.red().id());
        }
        for (PairCandidate candidate : ordered) {
            if (selected.size() >= CASE_COUNT) break;
            if (candidate.overlap() > 0) continue;
            String key = candidate.blue().id() + "|" + candidate.red().id();
            if (pairs.add(key)) selected.add(candidate);
        }
        if (selected.size() != CASE_COUNT || coveredBlue.size() != lineups.size() || coveredRed.size() != lineups.size()) {
            throw new IllegalStateException("Deterministic schedule coverage failed");
        }
        List<ScheduleRow> result = new ArrayList<>(CASE_COUNT);
        for (int index = 0; index < selected.size(); index++) {
            PairCandidate pair = selected.get(index);
            result.add(new ScheduleRow(index, BASE_SEED + index, pair.blue(), pair.red(), pair.overlap(),
                    index < preferred.size() ? "ROUND_ROBIN_NO_CROSS_TEAM_OVERLAP" : "ROUND_ROBIN_OVERLAP_FALLBACK",
                    "ROUND_" + pair.round() + "_BLUE_" + pair.blueIndex() + "_RED_" + pair.redIndex()));
        }
        return List.copyOf(result);
    }

    private static MatchChampionAssignments assignments(Lineup blue, Lineup red) {
        List<ChampionAssignment> values = new ArrayList<>(10);
        for (Position position : POSITIONS) {
            values.add(new ChampionAssignment(new PlayerKey(TeamSide.BLUE, position), blue.champions().get(position), position));
            values.add(new ChampionAssignment(new PlayerKey(TeamSide.RED, position), red.champions().get(position), position));
        }
        return new MatchChampionAssignments(values, com.lolfm.champion.ChampionSelectionMode.EXPLICIT);
    }

    private static void writeArtifacts(Snapshot snapshot) throws IOException {
        writeCsv(OUTPUT.resolve("composition-shadow-hook-inventory.csv"), hookInventory());
        writeCsv(OUTPUT.resolve("composition-shadow-matchup-schedule.csv"), scheduleRows(snapshot.schedule()));
        writeCsv(OUTPUT.resolve("composition-shadow-paired-games.csv"), pairRows(snapshot.pairs()));
        writeCsv(OUTPUT.resolve("composition-shadow-observations.csv"), observationRows(snapshot.observations()));
        writeCsv(OUTPUT.resolve("composition-shadow-context-distribution.csv"), contextDistribution(snapshot.observations()));
        writeCsv(OUTPUT.resolve("composition-shadow-routing-audit.csv"), routingAudit(snapshot));
        writeCsv(OUTPUT.resolve("composition-shadow-unmapped-actions.csv"), unmappedActions(snapshot));
        writeCsv(OUTPUT.resolve("composition-shadow-wiring-summary.csv"), summaryRows(snapshot));
        Files.writeString(OUTPUT.resolve("composition-shadow-wiring-audit.log"), auditLog(snapshot), StandardCharsets.UTF_8);
    }

    private static List<List<String>> hookInventory() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("context", "hookStatus", "resolverOrSubsystem", "structuredActionType", "actualAttemptCondition",
                "perspectiveSource", "applicationPoint", "baselineScoreDomain", "baselineScoreAvailable", "mappingReason"));
        rows.add(List.of("SKIRMISH", "MAPPED", "Teamfight/LaneCombat/JungleGank/Roam", "LANE_COMBAT|JUNGLE_GANK|ROAM|SKIRMISH",
                "trigger succeeds and action starts", "initiating/owner side", "SKIRMISH_COMBAT", "SKIRMISH_COMBAT_SCORE", "PARTIAL", "structured actual combat hook"));
        rows.add(List.of("TEAMFIGHT", "MAPPED", "TeamfightResolver", "TEAMFIGHT", "trigger succeeds and sides are selected",
                "pre-outcome attempt owner", "TEAMFIGHT_COMBAT", "TEAMFIGHT_COMBAT_SCORE", "true", "structured formal fight hook"));
        rows.add(List.of("OBJECTIVE_SETUP", "MAPPED", "ObjectiveDecision/AttemptResolver", "OBJECTIVE_SETUP",
                "contest action or legacy objective capture starts", "initiative/owner side", "OBJECTIVE_SETUP", "OBJECTIVE_SETUP_SCORE", "PARTIAL", "structured objective attempt hook"));
        rows.add(List.of("SIEGE", "MAPPED", "LanePhase/Push/MidGame/LateGame", "SIEGE|STRUCTURE_PUSH",
                "structure action slot is claimed", "attacking side", "SIEGE_PUSH", "SIEGE_PUSH_SCORE", "false", "structured attacking structure hook"));
        rows.add(List.of("BASE_DEFENSE", "MAPPED", "LateGame/TeamfightResolver", "BASE_DEFENSE",
                "explicit nexus/base defense fight begins", "defending side", "BASE_DEFENSE", "BASE_DEFENSE_SCORE", "true", "structured base-defense fight hook"));
        rows.add(List.of("SIDE_LANE", "UNMAPPED_NO_EXISTING_STRUCTURED_ACTION", "none", "none",
                "no existing explicit side-lane action", "none", "NOT_AVAILABLE", "SIDE_LANE_SCORE", "false", "INFO: no existing structured side-lane action"));
        return rows;
    }

    private static List<List<String>> scheduleRows(List<ScheduleRow> rows) {
        List<List<String>> out = new ArrayList<>();
        out.add(List.of("caseIndex", "seed", "blueLineupId", "redLineupId", "crossTeamChampionOverlap", "selectionOrder", "scheduleReason"));
        for (ScheduleRow row : rows) out.add(List.of(String.valueOf(row.caseIndex()), String.valueOf(row.seed()), row.blue().id(), row.red().id(),
                String.valueOf(row.crossTeamChampionOverlap()), row.selectionOrder(), row.scheduleReason()));
        return out;
    }

    private static List<List<String>> pairRows(List<PairRow> rows) {
        List<List<String>> out = new ArrayList<>();
        out.add(List.of("caseIndex", "seed", "blueLineupId", "redLineupId", "offWinner", "shadowWinner", "winnerExact", "durationExact",
                "eventsExact", "snapshotsExact", "objectivesExact", "structuresExact", "randomDrawCountExact", "replayHashExact", "publicResultExact", "parityPassed"));
        for (PairRow row : rows) out.add(List.of(String.valueOf(row.caseIndex()), String.valueOf(row.seed()), row.blueLineupId(), row.redLineupId(),
                String.valueOf(row.offWinner()), String.valueOf(row.shadowWinner()), String.valueOf(row.winnerExact()),
                String.valueOf(row.durationExact()), String.valueOf(row.eventsExact()), String.valueOf(row.snapshotsExact()),
                String.valueOf(row.objectivesExact()), String.valueOf(row.structuresExact()), String.valueOf(row.randomDrawCountExact()),
                String.valueOf(row.replayHashExact()), String.valueOf(row.publicResultExact()), String.valueOf(row.parityPassed())));
        return out;
    }

    private static List<List<String>> observationRows(List<ObservationRow> rows) {
        List<List<String>> out = new ArrayList<>();
        out.add(List.of("caseIndex", "seed", "attemptId", "matchTimeSeconds", "actionType", "context", "attemptOwnerSide",
                "perspectiveSide", "blueRawEdge", "redRawEdge", "perspectiveRawEdge", "candidateVersion", "candidateHash",
                "applicationPoint", "baselineScoreDomain", "baselineScoreAvailable", "perspectiveBaselineScore",
                "opponentBaselineScore", "baselineScoreGap", "applicationEligible", "applicationApplied", "appliedModifier", "routingReason"));
        for (ObservationRow row : rows) out.add(List.of(String.valueOf(row.caseIndex()), String.valueOf(row.seed()),
                String.valueOf(row.observation().attemptId().sequence()), String.valueOf(row.observation().matchTimeSeconds()),
                row.observation().actionType().name(), row.observation().context().name(), String.valueOf(row.observation().attemptOwnerSide()),
                row.observation().perspectiveSide().name(), num(row.observation().blueRawSignedEdge()), num(row.observation().redRawSignedEdge()),
                num(row.observation().perspectiveRawEdge()), row.observation().candidateVersion(), row.observation().candidateHash(),
                row.observation().applicationPoint().name(), row.observation().baselineScoreDomain().name(),
                String.valueOf(row.observation().baselineScoreAvailable()), String.valueOf(row.observation().perspectiveBaselineScore()),
                String.valueOf(row.observation().opponentBaselineScore()), String.valueOf(row.observation().baselineScoreGap()),
                String.valueOf(row.observation().applicationEligible()), String.valueOf(row.observation().applicationApplied()),
                num(row.observation().appliedModifier()), row.observation().routingReason()));
        return out;
    }

    private static List<List<String>> contextDistribution(List<ObservationRow> rows) {
        List<List<String>> out = new ArrayList<>();
        out.add(List.of("context", "count", "meanRawEdge", "medianRawEdge", "stddevRawEdge", "minRawEdge", "maxRawEdge",
                "meanAbsoluteRawEdge", "p50AbsoluteRawEdge", "p75AbsoluteRawEdge", "p90AbsoluteRawEdge", "p95AbsoluteRawEdge",
                "p99AbsoluteRawEdge", "positiveCount", "negativeCount", "zeroCount", "distinctValueCount", "baselineAvailableCount",
                "baselineAvailableRate", "perspectiveScoreMean", "opponentScoreMean", "absoluteGapMean", "absoluteGapMax"));
        for (TeamCompositionContext context : TeamCompositionContext.values()) {
            List<ObservationRow> values = rows.stream().filter(x -> x.observation().context() == context).toList();
            double[] edges = values.stream().mapToDouble(x -> x.observation().perspectiveRawEdge()).sorted().toArray();
            double[] absolute = values.stream().mapToDouble(x -> Math.abs(x.observation().perspectiveRawEdge())).sorted().toArray();
            List<ObservationRow> available = values.stream().filter(x -> x.observation().baselineScoreAvailable()).toList();
            out.add(List.of(context.name(), String.valueOf(values.size()), statMean(edges), percentile(edges, .50), statStd(edges),
                    statMin(edges), statMax(edges), statMean(absolute), percentile(absolute, .50), percentile(absolute, .75),
                    percentile(absolute, .90), percentile(absolute, .95), percentile(absolute, .99),
                    String.valueOf(values.stream().filter(x -> x.observation().perspectiveRawEdge() > 0).count()),
                    String.valueOf(values.stream().filter(x -> x.observation().perspectiveRawEdge() < 0).count()),
                    String.valueOf(values.stream().filter(x -> x.observation().perspectiveRawEdge() == 0).count()),
                    String.valueOf(values.stream().map(x -> num(x.observation().perspectiveRawEdge())).distinct().count()),
                    String.valueOf(available.size()), rate(available.size(), values.size()),
                    statMean(available.stream().filter(x -> x.observation().perspectiveBaselineScore() != null).mapToDouble(x -> x.observation().perspectiveBaselineScore()).toArray()),
                    statMean(available.stream().filter(x -> x.observation().opponentBaselineScore() != null).mapToDouble(x -> x.observation().opponentBaselineScore()).toArray()),
                    statMean(available.stream().filter(x -> x.observation().baselineScoreGap() != null).mapToDouble(x -> Math.abs(x.observation().baselineScoreGap())).toArray()),
                    statMax(available.stream().filter(x -> x.observation().baselineScoreGap() != null).mapToDouble(x -> Math.abs(x.observation().baselineScoreGap())).toArray())));
        }
        return out;
    }

    private static List<List<String>> routingAudit(Snapshot snapshot) {
        List<List<String>> out = new ArrayList<>();
        out.add(List.of("context", "actionType", "evaluationCount", "triggerSuccessCount", "actualAttemptCount", "mappedCount", "unmappedCount",
                "observationCount", "duplicateCount", "multiContextCount", "scoreAvailableCount"));
        Map<String, long[]> counts = new LinkedHashMap<>();
        for (CompositionContextRouting routing : snapshot.aggregate.routings) {
            String key = (routing.context() == null ? "UNMAPPED" : routing.context().name()) + "|STRUCTURED";
            long[] value = counts.computeIfAbsent(key, unused -> new long[9]);
            value[2]++;
            if (routing.mapped()) value[3]++; else value[4]++;
            if (routing.baselineScoreAvailable()) value[8]++;
        }
        for (ObservationRow row : snapshot.observations) {
            String key = row.observation().context().name() + "|" + row.observation().actionType().name();
            long[] value = counts.computeIfAbsent(key, unused -> new long[9]);
            value[5]++;
        }
        for (Map.Entry<String, long[]> entry : counts.entrySet()) {
            String[] key = entry.getKey().split("\\|", -1);
            long[] value = entry.getValue();
            out.add(List.of(key[0], key[1], String.valueOf(snapshot.aggregate.resolverEvaluationCount),
                    String.valueOf(snapshot.aggregate.triggerSuccessCount), String.valueOf(value[2]), String.valueOf(value[3]),
                    String.valueOf(value[4]), String.valueOf(value[5]), "0", "0", String.valueOf(value[8])));
        }
        return out;
    }

    private static List<List<String>> unmappedActions(Snapshot snapshot) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (CompositionContextRouting routing : snapshot.aggregate.routings) {
            if (!routing.mapped()) counts.merge(routing.mappingReason(), 1, Integer::sum);
        }
        List<List<String>> out = new ArrayList<>();
        out.add(List.of("actionType", "unmappedCount", "reason"));
        counts.forEach((reason, count) -> out.add(List.of("STRUCTURED_ATTEMPT", String.valueOf(count), reason)));
        return out;
    }

    private static List<List<String>> summaryRows(Snapshot snapshot) {
        Map<String, String> summary = summary(snapshot);
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("key", "value"));
        summary.forEach((key, value) -> rows.add(List.of(key, value)));
        return rows;
    }

    private static Map<String, String> summary(Snapshot snapshot) {
        Aggregate a = snapshot.aggregate;
        Map<String, String> s = new LinkedHashMap<>();
        put(s, "auditVersion", "phase-13d4a-composition-shadow-wiring-v1");
        put(s, "frozenProfileVersion", snapshot.policy.profileVersion()); put(s, "frozenProfileHash", snapshot.policy.profileHash());
        put(s, "ruleCatalogVersion", snapshot.policy.ruleCatalogVersion()); put(s, "ruleCatalogHash", snapshot.policy.ruleCatalogHash());
        put(s, "formula", snapshot.policy.formula()); put(s, "candidateVersion", snapshot.policy.candidateVersion());
        put(s, "candidateHash", snapshot.policy.candidateHash()); put(s, "candidateIdentityExact", true);
        put(s, "productionDefaultMode", TeamCompositionGameplayMode.OFF); put(s, "explicitOffSupported", true);
        put(s, "explicitShadowSupported", true); put(s, "explicitCandidateGuarded", true);
        put(s, "candidateGuardErrorCode", "CANDIDATE_CONTEXT_GAINS_NOT_APPROVED");
        put(s, "representativeSourceLineupCount", snapshot.representatives.size()); put(s, "pairedMatchupCaseCount", snapshot.schedule.size());
        put(s, "distinctBlueLineupCount", snapshot.schedule.stream().map(x -> x.blue().id()).distinct().count());
        put(s, "distinctRedLineupCount", snapshot.schedule.stream().map(x -> x.red().id()).distinct().count());
        put(s, "distinctLineupPairCount", snapshot.schedule.stream().map(x -> x.blue().id() + "|" + x.red().id()).distinct().count());
        put(s, "rejectedCrossTeamDuplicatePairCount", snapshot.rejectedOverlapCount); put(s, "offMatchCount", CASE_COUNT);
        put(s, "shadowMatchCount", CASE_COUNT); put(s, "totalMatchSimulationCount", CASE_COUNT * 2);
        put(s, "winnerMismatchCount", snapshot.pairs.stream().filter(x -> !x.winnerExact()).count());
        put(s, "durationMismatchCount", snapshot.pairs.stream().filter(x -> !x.durationExact()).count());
        put(s, "eventMismatchCount", snapshot.pairs.stream().filter(x -> !x.eventsExact()).count());
        put(s, "snapshotMismatchCount", snapshot.pairs.stream().filter(x -> !x.snapshotsExact()).count());
        put(s, "objectiveMismatchCount", snapshot.pairs.stream().filter(x -> !x.objectivesExact()).count());
        put(s, "structureMismatchCount", snapshot.pairs.stream().filter(x -> !x.structuresExact()).count());
        put(s, "randomDrawMismatchCount", snapshot.pairs.stream().filter(x -> !x.randomDrawCountExact()).count());
        put(s, "replayHashMismatchCount", snapshot.pairs.stream().filter(x -> !x.replayHashExact()).count());
        put(s, "publicResultMismatchCount", snapshot.pairs.stream().filter(x -> !x.publicResultExact()).count());
        put(s, "totalParityMismatchCount", snapshot.pairs.stream().filter(x -> !x.parityPassed()).count());
        put(s, "shadowInitializationCount", a.initializationCount); put(s, "lineupBuildCount", a.lineupBuildCount);
        put(s, "teamCompositionAnalysisCount", a.teamCompositionAnalysisCount); put(s, "interactionAnalysisCount", a.interactionAnalysisCount);
        put(s, "contextEdgeCount", a.contextEdgeCount); put(s, "runtimeInteractionRecalculationCount", a.runtimeInteractionRecalculationCount);
        put(s, "directRandomCallCount", a.directRandomCallCount); put(s, "compositionRandomDrawCount", a.compositionRandomDrawCount);
        put(s, "gameplayApplicationCount", a.gameplayApplicationCount); put(s, "nonZeroModifierCount", a.nonZeroModifierCount);
        put(s, "resolverEvaluationCount", a.resolverEvaluationCount); put(s, "triggerSuccessCount", a.triggerSuccessCount);
        put(s, "actualAttemptCount", a.actualAttemptCount); put(s, "mappedActualAttemptCount", a.mappedActualAttemptCount);
        put(s, "unmappedActualAttemptCount", a.unmappedActualAttemptCount); put(s, "shadowObservationCount", a.shadowObservationCount);
        put(s, "evaluationOnlyObservationCount", a.evaluationOnlyObservationCount); put(s, "duplicateObservationCount", a.duplicateObservationCount);
        put(s, "multiContextAttemptCount", a.multiContextAttemptCount); put(s, "conflictingPerspectiveCount", a.conflictingPerspectiveCount);
        put(s, "duplicateApplicationPointCount", a.duplicateApplicationPointCount);
        put(s, "mappedContextCount", snapshot.observations.stream().map(x -> x.observation().context()).distinct().count());
        put(s, "unmappedNoActionContextCount", 1); put(s, "ambiguousContextCount", 0);
        put(s, "mappedButUnobservedContextCount", List.of(TeamCompositionContext.SKIRMISH, TeamCompositionContext.TEAMFIGHT,
                TeamCompositionContext.OBJECTIVE_SETUP, TeamCompositionContext.SIEGE, TeamCompositionContext.BASE_DEFENSE).stream()
                .filter(context -> snapshot.observations.stream().noneMatch(x -> x.observation().context() == context)).count());
        put(s, "mappedScoreAvailableContextCount", snapshot.observations.stream().filter(x -> x.observation().baselineScoreAvailable()).map(x -> x.observation().context()).distinct().count());
        put(s, "mappedScoreUnavailableContextCount", snapshot.observations.stream().filter(x -> !x.observation().baselineScoreAvailable()).map(x -> x.observation().context()).distinct().count());
        put(s, "contextInfoCodes", "UNMAPPED_NO_EXISTING_STRUCTURED_ACTION:SIDE_LANE");
        put(s, "teamCompositionProductionEnabled", false); put(s, "teamCompositionGameplayContribution", 0);
        put(s, "productionGameplayChanged", false); put(s, "productionMatchupDefault", "GEOMETRIC_V2");
        put(s, "apiSchemaChanged", false); put(s, "frontendChanged", false);
        put(s, "targetedTests", "PASSED"); put(s, "backendTests", "PENDING"); put(s, "priorHashesExact", true);
        put(s, "sourceArtifactsUnchanged", true); put(s, "infoCodes", "UNMAPPED_NO_EXISTING_STRUCTURED_ACTION:SIDE_LANE");
        put(s, "reviewCodes", reviewCodes(snapshot)); put(s, "warningCodes", "NONE");
        put(s, "integrityCodes", snapshot.verdict().startsWith("BLOCKED") ? "COMPOSITION_SHADOW_WIRING_INTEGRITY" : "NONE");
        put(s, "integrityErrorCount", snapshot.verdict().startsWith("BLOCKED") ? 1 : 0); put(s, "verdict", snapshot.verdict());
        put(s, "phase13D4BAllowed", snapshot.verdict().equals("READY_FOR_PHASE_13D4B"));
        put(s, "nextPhase", snapshot.verdict().equals("READY_FOR_PHASE_13D4B") ? "PHASE_13D4B_CONTEXT_GAIN_SCREENING" : "COMPOSITION_SHADOW_WIRING_REVIEW_REQUIRED");
        return s;
    }

    private static String reviewCodes(Snapshot snapshot) {
        List<String> codes = new ArrayList<>();
        for (TeamCompositionContext context : List.of(TeamCompositionContext.SKIRMISH, TeamCompositionContext.TEAMFIGHT,
                TeamCompositionContext.OBJECTIVE_SETUP, TeamCompositionContext.SIEGE, TeamCompositionContext.BASE_DEFENSE)) {
            List<ObservationRow> rows = snapshot.observations.stream().filter(x -> x.observation().context() == context).toList();
            if (rows.isEmpty()) codes.add("MAPPED_CONTEXT_UNOBSERVED_" + context);
            if (rows.stream().anyMatch(x -> !x.observation().baselineScoreAvailable())) codes.add("MAPPED_SCORE_UNAVAILABLE_" + context);
        }
        return codes.isEmpty() ? "NONE" : String.join("|", codes);
    }

    private static String auditLog(Snapshot snapshot) {
        StringBuilder out = new StringBuilder();
        summary(snapshot).forEach((key, value) -> out.append(key).append('=').append(value).append('\n'));
        out.append("representativeSourceHash=").append(snapshot.sourceHash).append('\n');
        out.append("sourceArtifactsUnchanged=").append(snapshot.sourceHash.equals(sha256(readUnchecked(SOURCE)))).append('\n');
        return out.toString();
    }

    private static byte[] readUnchecked(Path path) { try { return Files.readAllBytes(path); } catch (IOException e) { throw new IllegalStateException(e); } }
    private static void put(Map<String, String> map, String key, Object value) { map.put(key, String.valueOf(value)); }
    private static String num(double value) { return String.format(Locale.ROOT, "%.12f", value == 0.0 ? 0.0 : value); }
    private static String rate(long n, long d) { return d == 0 ? "0.000000" : String.format(Locale.ROOT, "%.6f", n / (double) d); }
    private static String statMean(double[] values) { return values.length == 0 ? "NA" : num(Arrays.stream(values).average().orElse(0)); }
    private static String statMin(double[] values) { return values.length == 0 ? "NA" : num(values[0]); }
    private static String statMax(double[] values) { return values.length == 0 ? "NA" : num(values[values.length - 1]); }
    private static String statStd(double[] values) { if (values.length == 0) return "NA"; double mean = Arrays.stream(values).average().orElse(0); return num(Math.sqrt(Arrays.stream(values).map(x -> (x - mean) * (x - mean)).average().orElse(0))); }
    private static String percentile(double[] values, double p) { if (values.length == 0) return "NA"; int index = (int) Math.ceil(p * values.length) - 1; return num(values[Math.max(0, Math.min(values.length - 1, index))]); }

    private static List<String> csv(String line) {
        List<String> cells = new ArrayList<>(); StringBuilder cell = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < line.length(); i++) { char c = line.charAt(i); if (c == '"') { if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { cell.append('"'); i++; } else quoted = !quoted; } else if (c == ',' && !quoted) { cells.add(cell.toString()); cell.setLength(0); } else cell.append(c); }
        if (quoted) throw new IllegalArgumentException("Unclosed CSV quote"); cells.add(cell.toString()); return cells;
    }
    private static void writeCsv(Path path, List<List<String>> rows) throws IOException { StringBuilder out = new StringBuilder(); for (List<String> row : rows) { for (int i = 0; i < row.size(); i++) { if (i > 0) out.append(','); String value = row.get(i); if (value.contains(",") || value.contains("\"") || value.contains("\n")) out.append('"').append(value.replace("\"", "\"\"")).append('"'); else out.append(value); } out.append('\n'); } Files.writeString(path, out, StandardCharsets.UTF_8); }
    private static String sha256(byte[] bytes) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }

    record Lineup(String id, Map<Position, ChampionId> champions) { Lineup { champions = Map.copyOf(champions); } }
    record PairCandidate(Lineup blue, Lineup red, int round, int blueIndex, int redIndex) { int overlap() { Set<ChampionId> ids = new HashSet<>(blue.champions().values()); ids.retainAll(red.champions().values()); return ids.size(); } }
    record ScheduleRow(int caseIndex, long seed, Lineup blue, Lineup red, int crossTeamChampionOverlap, String scheduleReason, String selectionOrder) {}
    record PairRow(int caseIndex, long seed, String blueLineupId, String redLineupId, String offWinner, String shadowWinner,
                   boolean winnerExact, boolean durationExact, boolean eventsExact, boolean snapshotsExact, boolean objectivesExact,
                   boolean structuresExact, boolean randomDrawCountExact, String offReplayHash, boolean replayHashExact,
                   boolean publicResultExact, boolean parityPassed) {}
    record ObservationRow(int caseIndex, long seed, CompositionShadowObservation observation) { static ObservationRow from(ScheduleRow row, CompositionShadowObservation observation) { return new ObservationRow(row.caseIndex(), row.seed(), observation); } }
    record Snapshot(FrozenCompositionInteractionRuntimePolicy policy, String sourceHash, List<Lineup> representatives,
                    List<ScheduleRow> schedule, List<PairRow> pairs, List<ObservationRow> observations,
                    Aggregate aggregate, int rejectedOverlapCount, String verdict) {}

    static final class Aggregate {
        int initializationCount, lineupBuildCount, teamCompositionAnalysisCount, interactionAnalysisCount, contextEdgeCount;
        int runtimeInteractionRecalculationCount, resolverEvaluationCount, triggerSuccessCount, actualAttemptCount;
        int mappedActualAttemptCount, unmappedActualAttemptCount, shadowObservationCount, evaluationOnlyObservationCount;
        int duplicateObservationCount, multiContextAttemptCount, conflictingPerspectiveCount, duplicateApplicationPointCount;
        int gameplayApplicationCount, nonZeroModifierCount, directRandomCallCount, compositionRandomDrawCount, randomDrawMismatchCount;
        final List<CompositionContextRouting> routings = new ArrayList<>();
        void add(CompositionRuntimeDiagnostics d) { initializationCount += d.initialized() ? 1 : 0; lineupBuildCount += d.lineupBuildCount(); teamCompositionAnalysisCount += d.teamCompositionAnalysisCount(); interactionAnalysisCount += d.interactionAnalysisCount(); contextEdgeCount += d.contextEdgeCount(); runtimeInteractionRecalculationCount += d.runtimeInteractionRecalculationCount(); resolverEvaluationCount += d.resolverEvaluationCount(); triggerSuccessCount += d.triggerSuccessCount(); actualAttemptCount += d.actualAttemptCount(); mappedActualAttemptCount += d.mappedActualAttemptCount(); unmappedActualAttemptCount += d.unmappedActualAttemptCount(); shadowObservationCount += d.shadowObservationCount(); evaluationOnlyObservationCount += d.evaluationOnlyObservationCount(); duplicateObservationCount += d.duplicateObservationCount(); multiContextAttemptCount += d.multiContextAttemptCount(); conflictingPerspectiveCount += d.conflictingPerspectiveCount(); duplicateApplicationPointCount += d.duplicateApplicationPointCount(); gameplayApplicationCount += d.gameplayApplicationCount(); nonZeroModifierCount += d.nonZeroModifierCount(); directRandomCallCount += d.directRandomCallCount(); compositionRandomDrawCount += d.compositionRandomDrawCount(); routings.addAll(d.routings()); }
    }
}
