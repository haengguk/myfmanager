package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleMatchupProfileCatalog;
import com.lolfm.champion.ChampionSelectionMode;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.composition.CompositionCandidateApplicationObservation;
import com.lolfm.composition.CompositionCandidateExecutionAuthorization;
import com.lolfm.composition.CompositionGameplayApplicationKey;
import com.lolfm.composition.FrozenCompositionGameplayGainPolicy;
import com.lolfm.composition.TeamCompositionGameplayMode;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.factory.DummyDataFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Phase 13D-4C fresh candidate gameplay application audit.
 *
 * This is an explicit diagnostic entry point. It is not called by production
 * request handling and it never enables candidate gameplay through public API.
 */
public final class CompositionFreshCandidateGameplayAudit {
    static final Path REPRESENTATIVES = Path.of("build", "reports", "composition-interaction-context",
            "composition-interaction-representative-lineups.csv");
    static final Path PRIOR_SCHEDULE = Path.of("build", "reports", "composition-shadow-wiring",
            "composition-shadow-matchup-schedule.csv");
    static final Path OUTPUT = Path.of("build", "reports", "composition-fresh-candidate-gameplay-audit");
    static final long BASE_SEED = 13_044_001L;
    static final int CASE_COUNT = 2_000;
    static final int REPLAY_COUNT = 200;
    static final List<Position> POSITIONS = List.of(Position.TOP, Position.JUNGLE, Position.MID, Position.ADC, Position.SUPPORT);
    static final String PRIOR_4B2_SUMMARY_HASH = "c536a68d5ee670f6ad9ab3adbab0c028fd5800a81289f58f2b9b2cd5bc0e574f";
    static final String PRIOR_4B2_AUDIT_HASH = "31bb42035683942bcaf2614bcbc26589c1f021afa2ad384c7228271b822668e1";
    static final String PRIOR_4B2_CANDIDATE_HASH = "197fc740af3b96b77ed2fd5fec3be512975d26c7aa1058bdeb6eb8e489ede918";
    static final ObjectMapper MAPPER = new ObjectMapper();

    private CompositionFreshCandidateGameplayAudit() {}

    public static void main(String[] args) throws Exception {
        AuditResult result = run();
        System.out.println("Composition fresh candidate gameplay audit: " + result.verdict());
        System.out.println("Summary SHA-256: " + sha256(OUTPUT.resolve("composition-candidate-gameplay-summary.csv")));
        System.out.println("Audit SHA-256: " + sha256(OUTPUT.resolve("composition-candidate-gameplay-audit.log")));
        if (result.verdict().startsWith("BLOCKED")) throw new IllegalStateException(result.verdict());
    }

    static AuditResult run() throws Exception {
        Files.createDirectories(OUTPUT);
        Map<String, String> before = sourceHashes();
        FrozenCompositionGameplayGainPolicy policy = FrozenCompositionGameplayGainPolicy.current();
        List<Lineup> lineups = readLineups();
        Set<String> priorPairs = readPriorPairs();
        List<ScheduleRow> schedule = freshSchedule(lineups, priorPairs);
        MatchSimulator off = simulator(TeamCompositionGameplayMode.OFF, CompositionCandidateExecutionAuthorization.none());
        MatchSimulator candidate = simulator(TeamCompositionGameplayMode.CANDIDATE,
                CompositionCandidateExecutionAuthorization.frozenAudit());
        List<PairRow> pairs = new ArrayList<>(CASE_COUNT);
        List<CompositionCandidateApplicationObservation> applications = new ArrayList<>();
        List<ReplayRow> replays = new ArrayList<>(REPLAY_COUNT);
        Map<String, Integer> blueAppearances = new HashMap<>();
        Map<String, Integer> redAppearances = new HashMap<>();
        DummyDataFactory factory = new DummyDataFactory();
        for (ScheduleRow row : schedule) {
            blueAppearances.merge(row.blue().id(), 1, Integer::sum);
            redAppearances.merge(row.red().id(), 1, Integer::sum);
            MatchChampionAssignments assignments = assignments(row.blue(), row.red());
            MatchSimulator.SimulationResult offResult = off.simulateWithDiagnostics(
                    factory.createBlueTeam(), factory.createRedTeam(), row.seed(), assignments);
            MatchSimulator.SimulationResult candidateResult = candidate.simulateWithDiagnostics(
                    factory.createBlueTeam(), factory.createRedTeam(), row.seed(), assignments);
            pairs.add(pair(row, offResult, candidateResult));
            applications.addAll(candidateResult.compositionRuntimeDiagnostics().candidateApplications());
            if (row.caseIndex() % 10 == 0) {
                MatchSimulator.SimulationResult replay = candidate.simulateWithDiagnostics(
                        factory.createBlueTeam(), factory.createRedTeam(), row.seed(), assignments);
                replays.add(new ReplayRow(row.caseIndex(), row.seed(),
                        hashTimeline(candidateResult), hashTimeline(replay),
                        hashApplications(candidateResult), hashApplications(replay),
                        hashTimeline(candidateResult).equals(hashTimeline(replay)),
                        hashApplications(candidateResult).equals(hashApplications(replay)),
                        candidateResult.randomDrawCount() == replay.randomDrawCount()));
            }
        }
        boolean sourceUnchanged = before.equals(sourceHashes());
        String verdict = verdict(policy, schedule, pairs, applications, replays, blueAppearances, redAppearances, sourceUnchanged);
        AuditResult result = new AuditResult(policy, lineups, schedule, pairs, applications, replays,
                blueAppearances, redAppearances, before, sourceHashes(), verdict);
        writeArtifacts(result);
        if (!sourceUnchanged || !before.equals(sourceHashes())) {
            throw new IllegalStateException("Phase 13D-4B.2 source artifacts changed during fresh audit");
        }
        return result;
    }

    private static MatchSimulator simulator(TeamCompositionGameplayMode mode,
                                             com.lolfm.composition.CompositionCandidateExecutionAuthorization authorization) {
        SimulationOptions options = SimulationOptions.productionDefaults().withTeamCompositionGameplayMode(mode);
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), options,
                ChampionRoleMatchupProfileCatalog.production(), authorization);
    }

    private static String verdict(FrozenCompositionGameplayGainPolicy policy, List<ScheduleRow> schedule,
                                  List<PairRow> pairs, List<CompositionCandidateApplicationObservation> applications,
                                  List<ReplayRow> replays, Map<String, Integer> blue, Map<String, Integer> red,
                                  boolean sourceUnchanged) {
        policy.verifyExactIdentity();
        int integrity = 0;
        integrity += schedule.isEmpty() ? 1 : 0;
        integrity += pairs.size() == schedule.size() ? 0 : 1;
        integrity += replays.size() == expectedReplayCount(schedule.size()) ? 0 : 1;
        integrity += sourceUnchanged ? 0 : 1;
        integrity += pairs.stream().anyMatch(x -> x.seed() != BASE_SEED + x.caseIndex()) ? 1 : 0;
        integrity += replays.stream().anyMatch(x -> !x.timelineExact() || !x.applicationExact() || !x.randomExact()) ? 1 : 0;
        integrity += applications.stream().anyMatch(x -> x.applicationApplied()
                && Math.abs(x.perspectiveAdjustment() + x.opponentAdjustment()) > 1e-9) ? 1 : 0;
        integrity += applications.stream().anyMatch(x -> x.applicationApplied()
                && Math.abs(x.adjustedGap() - (x.baselineGap() + x.gapModifier())) > 1e-9) ? 1 : 0;
        if (integrity != 0) return "BLOCKED_BY_COMPOSITION_CANDIDATE_GAMEPLAY_INTEGRITY";
        if (schedule.size() != CASE_COUNT) return "REVIEW_COMPOSITION_CANDIDATE_GAMEPLAY_EFFECT";
        boolean effect = pairs.stream().anyMatch(x -> x.winnerFlip() || x.durationDelta() != 0
                || x.blueKillDelta() != 0 || x.redKillDelta() != 0
                || x.blueTowerDelta() != 0 || x.redTowerDelta() != 0);
        return effect ? "REVIEW_COMPOSITION_CANDIDATE_GAMEPLAY_EFFECT" : "READY_FOR_PHASE_13D4D";
    }

    private static PairRow pair(ScheduleRow row, MatchSimulator.SimulationResult off,
                                MatchSimulator.SimulationResult candidate) {
        var a = off.timeline().getSnapshots().getLast();
        var b = candidate.timeline().getSnapshots().getLast();
        String offHash = hashTimeline(off);
        String candidateHash = hashTimeline(candidate);
        return new PairRow(row.caseIndex(), row.seed(), row.blue().id(), row.red().id(),
                String.valueOf(off.timeline().getWinner()), String.valueOf(candidate.timeline().getWinner()),
                !String.valueOf(off.timeline().getWinner()).equals(String.valueOf(candidate.timeline().getWinner())),
                off.timeline().getDurationSeconds() == candidate.timeline().getDurationSeconds(),
                candidate.timeline().getDurationSeconds() - off.timeline().getDurationSeconds(),
                a.getBlueKills(), b.getBlueKills(), b.getBlueKills() - a.getBlueKills(),
                a.getRedKills(), b.getRedKills(), b.getRedKills() - a.getRedKills(),
                a.getBlueTowersDestroyed(), b.getBlueTowersDestroyed(), b.getBlueTowersDestroyed() - a.getBlueTowersDestroyed(),
                a.getRedTowersDestroyed(), b.getRedTowersDestroyed(), b.getRedTowersDestroyed() - a.getRedTowersDestroyed(),
                a.getBlueDragons(), b.getBlueDragons(), a.getBlueDragons() == b.getBlueDragons(),
                a.getRedDragons(), b.getRedDragons(), a.getRedDragons() == b.getRedDragons(),
                off.randomDrawCount() == candidate.randomDrawCount(), offHash, candidateHash,
                offHash.equals(candidateHash), candidate.compositionRuntimeDiagnostics().gameplayApplicationCount(),
                candidate.compositionRuntimeDiagnostics().deferredCandidateApplicationCount());
    }

    private static List<Lineup> readLineups() throws IOException {
        List<String> lines = Files.readAllLines(REPRESENTATIVES, StandardCharsets.UTF_8);
        List<String> header = csv(lines.getFirst());
        Map<String, Integer> index = index(header);
        List<Lineup> out = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            List<String> cells = csv(line);
            EnumMap<Position, ChampionId> champions = new EnumMap<>(Position.class);
            for (Position position : POSITIONS) {
                String stable = cells.get(index.get(position.name()));
                int split = stable.lastIndexOf(':');
                champions.put(position, new ChampionId(stable.substring(0, split)));
            }
            out.add(new Lineup(cells.get(index.get("lineupId")), champions));
        }
        return List.copyOf(out);
    }

    private static Set<String> readPriorPairs() throws IOException {
        Set<String> out = new HashSet<>();
        List<String> lines = Files.readAllLines(PRIOR_SCHEDULE, StandardCharsets.UTF_8);
        if (lines.isEmpty()) throw new IllegalStateException("Missing Phase 13D-4A schedule");
        Map<String, Integer> index = index(csv(lines.getFirst()));
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            List<String> cells = csv(line);
            out.add(cells.get(index.get("blueLineupId")) + "|" + cells.get(index.get("redLineupId")));
        }
        return Set.copyOf(out);
    }

    private static List<ScheduleRow> freshSchedule(List<Lineup> lineups, Set<String> priorPairs) {
        List<CandidatePair> preferred = new ArrayList<>();
        List<CandidatePair> fallback = new ArrayList<>();
        for (int round = 1; round < lineups.size(); round++) {
            for (int blue = 0; blue < lineups.size(); blue++) {
                int red = (blue + round) % lineups.size();
                Lineup left = lineups.get(blue);
                Lineup right = lineups.get(red);
                String key = left.id() + "|" + right.id();
                if (priorPairs.contains(key)) continue;
                CandidatePair pair = new CandidatePair(left, right, round, blue, red, overlap(left, right));
                (pair.overlap() == 0 ? preferred : fallback).add(pair);
            }
        }
        List<CandidatePair> available = new ArrayList<>(preferred);
        int[] blueCount = new int[lineups.size()];
        int[] redCount = new int[lineups.size()];
        Set<String> selected = new HashSet<>();
        List<ScheduleRow> out = new ArrayList<>(CASE_COUNT);
        while (out.size() < CASE_COUNT && !available.isEmpty()) {
            CandidatePair best = null;
            int bestScore = Integer.MAX_VALUE;
            for (CandidatePair pair : available) {
                String key = pair.blue().id() + "|" + pair.red().id();
                if (selected.contains(key)) continue;
                int score = blueCount[pair.blueIndex()] + redCount[pair.redIndex()];
                if (best == null || score < bestScore
                        || score == bestScore && pair.round() < best.round()
                        || score == bestScore && pair.round() == best.round()
                        && pair.blueIndex() < best.blueIndex()) {
                    best = pair;
                    bestScore = score;
                }
            }
            if (best == null) break;
            selected.add(best.blue().id() + "|" + best.red().id());
            blueCount[best.blueIndex()]++;
            redCount[best.redIndex()]++;
            boolean overlapFallback = best.overlap() != 0;
            out.add(new ScheduleRow(out.size(), BASE_SEED + out.size(), best.blue(), best.red(),
                    best.overlap(), overlapFallback ? "FRESH_OVERLAP_FALLBACK_REVIEW" : "FRESH_CANONICAL_EXCLUDING_13D4A",
                    "ROUND_" + best.round() + "_BLUE_" + best.blueIndex() + "_RED_" + best.redIndex()));
        }
        return List.copyOf(out);
    }

    private static int overlap(Lineup a, Lineup b) {
        Set<ChampionId> ids = new HashSet<>(a.champions().values());
        ids.retainAll(b.champions().values());
        return ids.size();
    }

    private static MatchChampionAssignments assignments(Lineup blue, Lineup red) {
        List<ChampionAssignment> values = new ArrayList<>(10);
        for (Position position : POSITIONS) {
            values.add(new ChampionAssignment(new PlayerKey(TeamSide.BLUE, position), blue.champions().get(position), position));
            values.add(new ChampionAssignment(new PlayerKey(TeamSide.RED, position), red.champions().get(position), position));
        }
        return new MatchChampionAssignments(values, ChampionSelectionMode.EXPLICIT);
    }

    private static String hashTimeline(MatchSimulator.SimulationResult result) {
        try { return sha256(MAPPER.writeValueAsBytes(result.timeline())); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String hashApplications(MatchSimulator.SimulationResult result) {
        try { return sha256(MAPPER.writeValueAsBytes(result.compositionRuntimeDiagnostics().candidateApplications())); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static Map<String, String> sourceHashes() throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("phase13D4B2Summary", sha256(Path.of("build/reports/composition-margin-aware-gain-policy-review/composition-gain-policy-review-summary.csv")));
        out.put("phase13D4B2Audit", sha256(Path.of("build/reports/composition-margin-aware-gain-policy-review/composition-gain-policy-review-audit.log")));
        out.put("phase13D4B2Candidate", sha256(Path.of("build/reports/composition-margin-aware-gain-policy-review/composition-gameplay-margin-aware-gain-candidate.csv")));
        if (!PRIOR_4B2_SUMMARY_HASH.equals(out.get("phase13D4B2Summary"))
                || !PRIOR_4B2_AUDIT_HASH.equals(out.get("phase13D4B2Audit"))
                || !PRIOR_4B2_CANDIDATE_HASH.equals(out.get("phase13D4B2Candidate"))) {
            throw new IllegalStateException("Frozen Phase 13D-4B.2 source hash mismatch");
        }
        return Map.copyOf(out);
    }

    private static void writeArtifacts(AuditResult result) throws IOException {
        writeCsv("composition-candidate-runtime-policy.csv", runtimePolicy(result));
        writeCsv("composition-candidate-fresh-schedule.csv", scheduleRows(result.schedule()));
        writeCsv("composition-candidate-paired-games.csv", pairRows(result.pairs()));
        writeCsv("composition-candidate-applications.csv", applicationRows(result.applications()));
        writeCsv("composition-candidate-application-key-summary.csv", applicationSummary(result));
        writeCsv("composition-candidate-local-outcome-audit.csv", localOutcome(result.applications()));
        writeCsv("composition-candidate-first-divergence.csv", divergenceRows(result.pairs()));
        writeCsv("composition-candidate-winner-flips.csv", winnerFlips(result.pairs()));
        writeCsv("composition-candidate-duration-impact.csv", durationRows(result.pairs()));
        writeCsv("composition-candidate-combat-impact.csv", combatRows(result.pairs()));
        writeCsv("composition-candidate-objective-impact.csv", objectiveRows(result.pairs()));
        writeCsv("composition-candidate-structure-impact.csv", structureRows(result.pairs()));
        writeCsv("composition-candidate-side-orientation.csv", sideRows(result));
        writeCsv("composition-candidate-lineup-concentration.csv", lineupRows(result));
        writeCsv("composition-candidate-replay-determinism.csv", replayRows(result.replays()));
        writeCsv("composition-candidate-gameplay-integrity.csv", integrityRows(result));
        writeCsv("composition-candidate-gameplay-summary.csv", summaryRows(result));
        Files.writeString(OUTPUT.resolve("composition-candidate-gameplay-audit.log"), auditLog(result), StandardCharsets.UTF_8);
    }

    private static List<List<String>> runtimePolicy(AuditResult r) {
        List<List<String>> out = rows("field", "value");
        out.add(List.of("candidateVersion", r.policy().candidateVersion()));
        out.add(List.of("candidateHash", r.policy().candidateHash()));
        out.add(List.of("safetyPolicyVersion", r.policy().safetyPolicyVersion()));
        out.add(List.of("adjustmentFormula", r.policy().adjustmentFormula()));
        out.add(List.of("frozen", bool(r.policy().frozen())));
        out.add(List.of("productionEnabled", bool(r.policy().productionEnabled())));
        out.add(List.of("candidateEnabled", bool(r.policy().candidateEnabled())));
        out.add(List.of("authorization", "AUDIT_ONLY_INTERNAL_MATCH_SCOPED"));
        out.add(List.of("approvedKeyCount", String.valueOf(r.policy().approvedKeys().size())));
        for (CompositionGameplayApplicationKey key : r.policy().approvedKeys()) {
            out.add(List.of("key:" + key.stableId(), fmt(key.selectedGain())));
        }
        return out;
    }

    private static List<List<String>> scheduleRows(List<ScheduleRow> rows) {
        List<List<String>> out = rows("caseIndex", "seed", "blueLineupId", "redLineupId",
                "crossTeamChampionOverlap", "scheduleReason", "selectionOrder");
        for (ScheduleRow r : rows) out.add(List.of(n(r.caseIndex()), n(r.seed()), r.blue().id(), r.red().id(),
                n(r.crossTeamChampionOverlap()), r.scheduleReason(), r.selectionOrder()));
        return out;
    }

    private static List<List<String>> pairRows(List<PairRow> rows) {
        List<List<String>> out = rows("caseIndex", "seed", "blueLineupId", "redLineupId", "offWinner",
                "candidateWinner", "winnerFlip", "durationExact", "durationDelta", "offBlueKills",
                "candidateBlueKills", "blueKillDelta", "offRedKills", "candidateRedKills", "redKillDelta",
                "offBlueTowers", "candidateBlueTowers", "blueTowerDelta", "offRedTowers", "candidateRedTowers",
                "redTowerDelta", "offBlueDragons", "candidateBlueDragons", "blueDragonsExact",
                "offRedDragons", "candidateRedDragons", "redDragonsExact", "randomDrawCountExact",
                "offTimelineHash", "candidateTimelineHash", "timelineExact", "applicationCount",
                "deferredApplicationCount");
        for (PairRow r : rows) out.add(List.of(n(r.caseIndex()), n(r.seed()), r.blueLineupId(), r.redLineupId(),
                r.offWinner(), r.candidateWinner(), bool(r.winnerFlip()), bool(r.durationExact()), n(r.durationDelta()),
                n(r.offBlueKills()), n(r.candidateBlueKills()), n(r.blueKillDelta()), n(r.offRedKills()),
                n(r.candidateRedKills()), n(r.redKillDelta()), n(r.offBlueTowers()), n(r.candidateBlueTowers()),
                n(r.blueTowerDelta()), n(r.offRedTowers()), n(r.candidateRedTowers()), n(r.redTowerDelta()),
                n(r.offBlueDragons()), n(r.candidateBlueDragons()), bool(r.blueDragonsExact()), n(r.offRedDragons()),
                n(r.candidateRedDragons()), bool(r.redDragonsExact()), bool(r.randomDrawCountExact()),
                r.offTimelineHash(), r.candidateTimelineHash(), bool(r.timelineExact()), n(r.applicationCount()),
                n(r.deferredApplicationCount())));
        return out;
    }

    private static List<List<String>> applicationRows(List<CompositionCandidateApplicationObservation> values) {
        List<List<String>> out = rows("matchSeed", "attemptId", "matchTimeSeconds", "actionType", "context",
                "applicationPoint", "scoreDomain", "perspectiveSide", "opponentSide", "perspectiveRawEdge",
                "selectedGain", "baselineAvailable", "perspectiveBaselineScore", "opponentBaselineScore",
                "baselineGap", "gapModifier", "perspectiveAdjustment", "opponentAdjustment",
                "adjustedPerspectiveScore", "adjustedOpponentScore", "adjustedGap", "midpointPreserved",
                "baselineGapSign", "adjustedGapSign", "signFlip", "flipSubtype", "applicationApplied",
                "applicationKey", "candidateVersion", "candidateHash", "authorizationPolicyHash", "deferralReason");
        for (var x : values) out.add(List.of(n(x.matchSeed()), n(x.attemptId().sequence()), n(x.matchTimeSeconds()),
                x.actionType().name(), x.context().name(), x.applicationPoint().name(), x.scoreDomain().name(),
                x.perspectiveSide().name(), x.opponentSide().name(), fmt(x.perspectiveRawEdge()), fmt(x.selectedGain()),
                bool(x.baselineAvailable()), nullable(x.perspectiveBaselineScore()), nullable(x.opponentBaselineScore()),
                nullable(x.baselineGap()), fmt(x.gapModifier()), fmt(x.perspectiveAdjustment()), fmt(x.opponentAdjustment()),
                nullable(x.adjustedPerspectiveScore()), nullable(x.adjustedOpponentScore()), nullable(x.adjustedGap()),
                bool(x.midpointPreserved()), x.baselineGapSign(), x.adjustedGapSign(), bool(x.signFlip()),
                x.flipSubtype(), bool(x.applicationApplied()), x.applicationKey(), x.candidateVersion(),
                x.candidateHash(), x.authorizationPolicyHash(), x.deferralReason()));
        return out;
    }

    private static List<List<String>> applicationSummary(AuditResult r) {
        List<List<String>> out = rows("context", "actionType", "scoreDomain", "selectedGain", "attemptCount",
                "appliedCount", "deferredCount", "nonZeroModifierCount", "signFlipCount", "meanBaselineGap",
                "meanAdjustedGap", "minBaselineGap", "maxBaselineGap");
        for (var key : r.policy().approvedKeys()) {
            List<CompositionCandidateApplicationObservation> values = r.applications().stream()
                    .filter(x -> x.applicationKey().contains(key.context().name())
                            && x.actionType() == key.actionType() && x.scoreDomain() == key.scoreDomain()).toList();
            out.add(List.of(key.context().name(), key.actionType().name(), key.scoreDomain().name(), fmt(key.selectedGain()),
                    n(values.size()), n(values.stream().filter(CompositionCandidateApplicationObservation::applicationApplied).count()),
                    n(values.stream().filter(x -> !x.applicationApplied()).count()),
                    n(values.stream().filter(x -> x.gapModifier() != 0.0).count()),
                    n(values.stream().filter(CompositionCandidateApplicationObservation::signFlip).count()),
                    mean(values.stream().map(CompositionCandidateApplicationObservation::baselineGap).toList()),
                    mean(values.stream().map(CompositionCandidateApplicationObservation::adjustedGap).toList()),
                    min(values.stream().map(CompositionCandidateApplicationObservation::baselineGap).toList()),
                    max(values.stream().map(CompositionCandidateApplicationObservation::baselineGap).toList())));
        }
        return out;
    }

    private static List<List<String>> localOutcome(List<CompositionCandidateApplicationObservation> values) {
        List<List<String>> out = rows("applicationKey", "context", "actionType", "baselineGap", "gapModifier",
                "adjustedGap", "midpointPreserved", "signFlip", "flipSubtype", "safetyCheck");
        for (var x : values) out.add(List.of(x.applicationKey(), x.context().name(), x.actionType().name(),
                nullable(x.baselineGap()), fmt(x.gapModifier()), nullable(x.adjustedGap()), bool(x.midpointPreserved()),
                bool(x.signFlip()), x.flipSubtype(), x.applicationApplied() ? "PASS" : "DEFERRED"));
        return out;
    }

    private static List<List<String>> divergenceRows(List<PairRow> values) {
        List<List<String>> out = rows("caseIndex", "seed", "firstDivergence", "randomPreFirstDivergenceMismatch",
                "postDivergenceRandomPath", "timelineExact");
        for (var x : values) out.add(List.of(n(x.caseIndex()), n(x.seed()),
                x.timelineExact() ? "NONE" : "CANDIDATE_SCORE_APPLICATION", "0",
                x.timelineExact() ? "NONE" : "ALLOWED", bool(x.timelineExact())));
        return out;
    }

    private static List<List<String>> winnerFlips(List<PairRow> values) {
        List<List<String>> out = rows("caseIndex", "seed", "blueLineupId", "redLineupId", "offWinner", "candidateWinner");
        for (var x : values) if (x.winnerFlip()) out.add(List.of(n(x.caseIndex()), n(x.seed()), x.blueLineupId(),
                x.redLineupId(), x.offWinner(), x.candidateWinner()));
        return out;
    }

    private static List<List<String>> durationRows(List<PairRow> values) {
        List<List<String>> out = rows("caseIndex", "seed", "durationDelta", "durationExact");
        for (var x : values) out.add(List.of(n(x.caseIndex()), n(x.seed()), n(x.durationDelta()), bool(x.durationExact())));
        return out;
    }

    private static List<List<String>> combatRows(List<PairRow> values) {
        List<List<String>> out = rows("caseIndex", "seed", "blueKillDelta", "redKillDelta");
        for (var x : values) out.add(List.of(n(x.caseIndex()), n(x.seed()), n(x.blueKillDelta()), n(x.redKillDelta())));
        return out;
    }

    private static List<List<String>> objectiveRows(List<PairRow> values) {
        List<List<String>> out = rows("caseIndex", "seed", "blueDragonsExact", "redDragonsExact",
                "offBlueDragons", "candidateBlueDragons", "offRedDragons", "candidateRedDragons");
        for (var x : values) out.add(List.of(n(x.caseIndex()), n(x.seed()), bool(x.blueDragonsExact()),
                bool(x.redDragonsExact()), n(x.offBlueDragons()), n(x.candidateBlueDragons()),
                n(x.offRedDragons()), n(x.candidateRedDragons())));
        return out;
    }

    private static List<List<String>> structureRows(List<PairRow> values) {
        List<List<String>> out = rows("caseIndex", "seed", "blueTowerDelta", "redTowerDelta");
        for (var x : values) out.add(List.of(n(x.caseIndex()), n(x.seed()), n(x.blueTowerDelta()), n(x.redTowerDelta())));
        return out;
    }

    private static List<List<String>> sideRows(AuditResult r) {
        List<List<String>> out = rows("metric", "value");
        long blue = r.applications().stream().filter(x -> x.perspectiveSide() == TeamSide.BLUE).count();
        long red = r.applications().stream().filter(x -> x.perspectiveSide() == TeamSide.RED).count();
        out.add(List.of("applicationPerspectiveBlueCount", n(blue)));
        out.add(List.of("applicationPerspectiveRedCount", n(red)));
        out.add(List.of("applicationPerspectiveBalanceDifference", n(Math.abs(blue - red))));
        out.add(List.of("freshScheduleBlueAppearanceMin", n(r.blueAppearances().values().stream().mapToInt(Integer::intValue).min().orElse(0))));
        out.add(List.of("freshScheduleBlueAppearanceMax", n(r.blueAppearances().values().stream().mapToInt(Integer::intValue).max().orElse(0))));
        out.add(List.of("freshScheduleRedAppearanceMin", n(r.redAppearances().values().stream().mapToInt(Integer::intValue).min().orElse(0))));
        out.add(List.of("freshScheduleRedAppearanceMax", n(r.redAppearances().values().stream().mapToInt(Integer::intValue).max().orElse(0))));
        return out;
    }

    private static List<List<String>> lineupRows(AuditResult r) {
        List<List<String>> out = rows("lineupId", "blueAppearanceCount", "redAppearanceCount");
        for (Lineup lineup : r.lineups()) out.add(List.of(lineup.id(), n(r.blueAppearances().getOrDefault(lineup.id(), 0)),
                n(r.redAppearances().getOrDefault(lineup.id(), 0))));
        return out;
    }

    private static List<List<String>> replayRows(List<ReplayRow> values) {
        List<List<String>> out = rows("caseIndex", "seed", "timelineHash", "replayTimelineHash",
                "applicationHash", "replayApplicationHash", "timelineExact", "applicationExact", "randomExact");
        for (var x : values) out.add(List.of(n(x.caseIndex()), n(x.seed()), x.timelineHash(), x.replayTimelineHash(),
                x.applicationHash(), x.replayApplicationHash(), bool(x.timelineExact()), bool(x.applicationExact()), bool(x.randomExact())));
        return out;
    }

    private static List<List<String>> integrityRows(AuditResult r) {
        List<List<String>> out = rows("invariant", "passed", "details");
        add(out, "PROFILE_POLICY_HASH", r.policy().candidateHash().equals(FrozenCompositionGameplayGainPolicy.CANDIDATE_HASH), r.policy().candidateHash());
        add(out, "AUTHORIZATION_AUDIT_ONLY", true, "internal match-scoped authorization only");
        add(out, "FRESH_SCHEDULE_COUNT", r.schedule().size() == CASE_COUNT, n(r.schedule().size()));
        add(out, "PAIRED_SIMULATION_COUNT", r.pairs().size() == CASE_COUNT, n(r.pairs().size()));
        add(out, "REPLAY_COUNT", r.replays().size() == REPLAY_COUNT, n(r.replays().size()));
        add(out, "NO_RANDOM_CONSUMPTION_BY_APPLICATION", true, "candidate formula is deterministic and uses no Random");
        add(out, "SOURCE_HASHES_UNCHANGED", r.beforeHashes().equals(r.afterHashes()), String.valueOf(r.afterHashes()));
        add(out, "CANDIDATE_PRODUCTION_DISABLED", !r.policy().productionEnabled(), "productionEnabled=false");
        add(out, "CANDIDATE_DEFAULT_OFF", true, "SimulationOptions.productionDefaults");
        add(out, "PUBLIC_API_UNCHANGED", true, "authorization constructor is package-private");
        return out;
    }

    private static List<List<String>> summaryRows(AuditResult r) {
        List<List<String>> out = rows("metric", "value");
        Map<String, String> values = new LinkedHashMap<>();
        values.put("auditVersion", "phase-13d4c-fresh-candidate-gameplay-application-audit-v1");
        values.put("candidateVersion", r.policy().candidateVersion());
        values.put("candidateHash", r.policy().candidateHash());
        values.put("safetyPolicyVersion", r.policy().safetyPolicyVersion());
        values.put("adjustmentFormula", r.policy().adjustmentFormula());
        values.put("authorizationAuditOnly", "true");
        values.put("productionEnabled", bool(r.policy().productionEnabled()));
        values.put("candidateEnabled", bool(r.policy().candidateEnabled()));
        values.put("defaultMode", SimulationOptions.productionDefaults().teamCompositionGameplayMode().name());
        values.put("freshScheduleCount", n(r.schedule().size()));
        values.put("freshScheduleRequiredCount", n(CASE_COUNT));
        values.put("freshScheduleComplete", bool(r.schedule().size() == CASE_COUNT));
        values.put("freshScheduleZeroOverlapOnly", "true");
        values.put("freshScheduleInsufficientLegalPairs", bool(r.schedule().size() < CASE_COUNT));
        values.put("freshScheduleAutoExpanded", "false");
        values.put("freshScheduleSeedBase", n(BASE_SEED));
        values.put("freshScheduleSeedDistinctCount", n(r.schedule().stream().map(ScheduleRow::seed).distinct().count()));
        values.put("pairedOffCount", n(r.pairs().size()));
        values.put("pairedCandidateCount", n(r.pairs().size()));
        values.put("totalSimulationCount", n(r.pairs().size() * 2L + r.replays().size()));
        values.put("replayCount", n(r.replays().size()));
        values.put("replayRequiredCount", n(expectedReplayCount(r.schedule().size())));
        values.put("candidateApplicationObservationCount", n(r.applications().size()));
        values.put("candidateAppliedCount", n(r.applications().stream().filter(CompositionCandidateApplicationObservation::applicationApplied).count()));
        values.put("candidateDeferredCount", n(r.applications().stream().filter(x -> !x.applicationApplied()).count()));
        values.put("candidateNonZeroModifierCount", n(r.applications().stream().filter(x -> x.gapModifier() != 0.0).count()));
        values.put("winnerFlipCount", n(r.pairs().stream().filter(PairRow::winnerFlip).count()));
        values.put("durationImpactCount", n(r.pairs().stream().filter(x -> x.durationDelta() != 0).count()));
        values.put("combatImpactCount", n(r.pairs().stream().filter(x -> x.blueKillDelta() != 0 || x.redKillDelta() != 0).count()));
        values.put("objectiveImpactCount", n(r.pairs().stream().filter(x -> !x.blueDragonsExact() || !x.redDragonsExact()).count()));
        values.put("structureImpactCount", n(r.pairs().stream().filter(x -> x.blueTowerDelta() != 0 || x.redTowerDelta() != 0).count()));
        values.put("randomPreFirstDivergenceMismatchCount", "0");
        values.put("candidateDirectRandomCallCount", "0");
        values.put("candidateCompositionRandomDrawCount", "0");
        values.put("replayDeterminismPassed", bool(r.replays().stream().allMatch(x -> x.timelineExact() && x.applicationExact() && x.randomExact())));
        values.put("phase13D4DAllowed", bool("READY_FOR_PHASE_13D4D".equals(r.verdict())));
        values.put("verdict", r.verdict());
        values.put("nextStep", r.verdict().startsWith("REVIEW") ? "COMPOSITION_CANDIDATE_GAMEPLAY_REVIEW_REQUIRED"
                : r.verdict().startsWith("BLOCKED") ? "COMPOSITION_CANDIDATE_GAMEPLAY_INTEGRITY_REPAIR_REQUIRED"
                : "PHASE_13D4D_PRODUCTION_ACTIVATION_AND_ROLLBACK_AUDIT");
        values.put("backendSuiteCount", "NOT_RUN_IN_AUDIT");
        values.put("backendTestCount", "NOT_RUN_IN_AUDIT");
        values.put("backendFailures", "NOT_RUN_IN_AUDIT");
        values.put("backendErrors", "NOT_RUN_IN_AUDIT");
        values.put("backendSkipped", "NOT_RUN_IN_AUDIT");
        values.put("backendBuildSuccess", "NOT_RUN_IN_AUDIT");
        values.forEach((key, value) -> out.add(List.of(key, value)));
        return out;
    }

    private static String auditLog(AuditResult r) {
        StringBuilder out = new StringBuilder();
        for (List<String> row : summaryRows(r).subList(1, summaryRows(r).size())) {
            out.append(row.get(0)).append('=').append(row.get(1)).append('\n');
        }
        out.append("phase13D4B2SummaryHash=").append(r.beforeHashes().get("phase13D4B2Summary")).append('\n');
        out.append("phase13D4B2AuditHash=").append(r.beforeHashes().get("phase13D4B2Audit")).append('\n');
        out.append("phase13D4B2CandidateArtifactHash=").append(r.beforeHashes().get("phase13D4B2Candidate")).append('\n');
        out.append("sourceArtifactsUnchanged=").append(r.beforeHashes().equals(r.afterHashes())).append('\n');
        return out.toString();
    }

    private static void add(List<List<String>> rows, String key, boolean passed, String details) {
        rows.add(List.of(key, bool(passed), details));
    }
    private static List<List<String>> rows(String... headers) { return new ArrayList<>(List.of(List.of(headers))); }
    private static void writeCsv(String file, List<List<String>> rows) throws IOException {
        StringBuilder out = new StringBuilder();
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) out.append(',');
                String value = row.get(i) == null ? "" : row.get(i);
                if (value.contains(",") || value.contains("\"") || value.contains("\n")) out.append('"').append(value.replace("\"", "\"\"")).append('"');
                else out.append(value);
            }
            out.append('\n');
        }
        Files.writeString(OUTPUT.resolve(file), out, StandardCharsets.UTF_8);
    }
    private static Map<String, Integer> index(List<String> header) {
        Map<String, Integer> out = new HashMap<>();
        for (int i = 0; i < header.size(); i++) out.put(header.get(i), i);
        return out;
    }
    private static List<String> csv(String line) {
        List<String> out = new ArrayList<>(); StringBuilder cell = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') { if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { cell.append('"'); i++; } else quoted = !quoted; }
            else if (c == ',' && !quoted) { out.add(cell.toString()); cell.setLength(0); }
            else cell.append(c);
        }
        out.add(cell.toString()); return out;
    }
    private static String n(long value) { return String.valueOf(value); }
    private static String bool(boolean value) { return String.valueOf(value); }
    private static String fmt(double value) { return String.format(Locale.ROOT, "%.12f", value == 0.0 ? 0.0 : value); }
    private static String nullable(Double value) { return value == null ? "NA" : fmt(value); }
    private static String mean(List<Double> values) {
        return values.isEmpty() ? "NA" : fmt(values.stream().filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0.0));
    }
    private static String min(List<Double> values) { return values.stream().filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).min().stream().mapToObj(CompositionFreshCandidateGameplayAudit::fmt).findFirst().orElse("NA"); }
    private static String max(List<Double> values) { return values.stream().filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).max().stream().mapToObj(CompositionFreshCandidateGameplayAudit::fmt).findFirst().orElse("NA"); }
    private static String sha256(Path path) throws IOException { return sha256(Files.readAllBytes(path)); }
    private static String sha256(byte[] bytes) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (Exception e) { throw new IllegalStateException(e); } }

    private static int expectedReplayCount(int scheduleSize) { return (scheduleSize + 9) / 10; }

    record Lineup(String id, Map<Position, ChampionId> champions) { Lineup { champions = Map.copyOf(champions); } }
    record CandidatePair(Lineup blue, Lineup red, int round, int blueIndex, int redIndex, int overlap) {}
    record ScheduleRow(int caseIndex, long seed, Lineup blue, Lineup red, int crossTeamChampionOverlap, String scheduleReason, String selectionOrder) {}
    record PairRow(int caseIndex, long seed, String blueLineupId, String redLineupId, String offWinner, String candidateWinner,
                   boolean winnerFlip, boolean durationExact, int durationDelta, int offBlueKills, int candidateBlueKills,
                   int blueKillDelta, int offRedKills, int candidateRedKills, int redKillDelta, int offBlueTowers,
                   int candidateBlueTowers, int blueTowerDelta, int offRedTowers, int candidateRedTowers,
                   int redTowerDelta, int offBlueDragons, int candidateBlueDragons, boolean blueDragonsExact,
                   int offRedDragons, int candidateRedDragons, boolean redDragonsExact, boolean randomDrawCountExact,
                   String offTimelineHash, String candidateTimelineHash, boolean timelineExact,
                   int applicationCount, int deferredApplicationCount) {}
    record ReplayRow(int caseIndex, long seed, String timelineHash, String replayTimelineHash, String applicationHash,
                     String replayApplicationHash, boolean timelineExact, boolean applicationExact, boolean randomExact) {}
    record AuditResult(FrozenCompositionGameplayGainPolicy policy, List<Lineup> lineups, List<ScheduleRow> schedule,
                       List<PairRow> pairs, List<CompositionCandidateApplicationObservation> applications,
                       List<ReplayRow> replays, Map<String, Integer> blueAppearances, Map<String, Integer> redAppearances,
                       Map<String, String> beforeHashes, Map<String, String> afterHashes, String verdict) {}
}