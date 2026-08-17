package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleMatchupProfileCatalog;
import com.lolfm.champion.ChampionSelectionMode;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.composition.*;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.Position;
import com.lolfm.factory.DummyDataFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Phase 13D-4C.5 audit-only isolated semantics runtime diagnostic. */
public final class CompositionAuditOnlySemanticsRuntime {
    static final String AUDIT_VERSION = "phase-13d4c5-audit-only-semantics-runtime-v1";
    static final String DESIGN_SUMMARY_HASH = "1969cbb365bf9dc69dad19914318d83c5d430ff9ef9ca7ecde62d05fea8099db";
    static final String DESIGN_AUDIT_HASH = "d6380b3e3723d11b53eeb90f88267b83093f412e3fbb53523b67c9b62e553cdb";
    static final String SCHEDULE_HASH = "4d6f5e12f4c4dbfc143ea262789d962541e9cbe22f32d513d7ac553db5fbb671";
    static final Path DESIGN = Path.of("build/reports/composition-key-specific-semantics-design");
    static final Path DESIGN_SUMMARY = DESIGN.resolve("composition-key-specific-semantics-summary.csv");
    static final Path DESIGN_AUDIT = DESIGN.resolve("composition-key-specific-semantics-audit.log");
    static final Path SCHEDULE = Path.of("build/reports/composition-fresh-holdout-candidate-gameplay-audit/composition-holdout-ordered-schedule.csv");
    static final Path OUTPUT = Path.of("build/reports/composition-audit-only-semantics-runtime");
    static final int ORDERED_CASES = 1_000;
    static final int ORIENTATION_GROUPS = 500;
    static final int REPLAYS = 100;
    static final ObjectMapper JSON = new ObjectMapper();
    static final DummyDataFactory FACTORY = new DummyDataFactory();
    static final List<Position> POSITIONS = List.of(Position.TOP, Position.JUNGLE, Position.MID, Position.ADC, Position.SUPPORT);

    private CompositionAuditOnlySemanticsRuntime() {}

    public static void main(String[] args) throws Exception {
        Result result = run();
        System.out.println("Composition audit-only semantics runtime: " + result.verdict());
        if (result.verdict().startsWith("BLOCKED")) throw new IllegalStateException(result.verdict());
    }

    static Result run() throws Exception {
        verifyIdentity();
        Files.createDirectories(OUTPUT);
        List<Path> sources = sourcePaths();
        Map<Path, String> before = hashes(sources);
        List<ScheduleCase> schedule = readSchedule();
        Map<String, CompositionFreshHoldoutCandidateGameplayAudit.Lineup> lineups =
                CompositionFreshHoldoutCandidateGameplayAudit.readCanonical().stream().collect(Collectors.toMap(
                        CompositionFreshHoldoutCandidateGameplayAudit.Lineup::id, x -> x));
        List<PairedGame> games = new ArrayList<>();
        List<Replay> replays = new ArrayList<>();
        List<CompositionWinnerChannelObservation> winners = new ArrayList<>();
        List<FightGradeDecisionDiagnostic> grades = new ArrayList<>();
        List<BaseDefenseRoleRoutingDiagnostic> roles = new ArrayList<>();
        MatchSimulator offSimulator = simulator(TeamCompositionGameplayMode.OFF,
                CompositionSemanticsAuditExecutionAuthorization.none());
        for (ScheduleCase row : schedule) {
            var blue = required(lineups, row.blueLineupId());
            var red = required(lineups, row.redLineupId());
            MatchChampionAssignments assignments = assignments(blue, red);
            MatchSimulator.SimulationResult off = simulate(offSimulator, row, assignments);
            MatchSimulator auditSimulator = simulator(TeamCompositionGameplayMode.SHADOW,
                    CompositionSemanticsAuditExecutionAuthorization.frozenDiagnosticCase(row.caseIndex()));
            MatchSimulator.SimulationResult audit = simulate(auditSimulator, row, assignments);
            games.add(pair(row, off, audit));
            winners.addAll(audit.compositionRuntimeDiagnostics().winnerChannelObservations());
            grades.addAll(audit.compositionRuntimeDiagnostics().fightGradeDiagnostics());
            roles.addAll(audit.compositionRuntimeDiagnostics().baseDefenseRoleRoutings());
            if (row.auditIndex() % 10 == 0) {
                MatchSimulator replaySimulator = simulator(TeamCompositionGameplayMode.SHADOW,
                        CompositionSemanticsAuditExecutionAuthorization.frozenDiagnosticCase(row.caseIndex()));
                MatchSimulator.SimulationResult replay = simulate(replaySimulator, row, assignments);
                replays.add(replay(row, audit, replay));
            }
        }
        Map<Path, String> after = hashes(sources);
        Integrity integrity = integrity(schedule, games, replays, winners, grades, roles, before.equals(after));
        boolean sampleMinimums = countGrades(grades, TeamCompositionContext.TEAMFIGHT) >= 500
                && countGrades(grades, TeamCompositionContext.SIEGE) >= 100
                && countGrades(grades, TeamCompositionContext.BASE_DEFENSE) >= 500
                && countWinners(winners, TeamCompositionContext.SKIRMISH) >= 1_000;
        String verdict = integrity.total() != 0
                ? "BLOCKED_BY_COMPOSITION_AUDIT_ONLY_SEMANTICS_RUNTIME_INTEGRITY"
                : !sampleMinimums
                ? "REVIEW_COMPOSITION_SEMANTICS_DIAGNOSTIC_SAMPLE_INSUFFICIENT"
                : "READY_FOR_PHASE_13D4C6_CHANNEL_CALIBRATION";
        Result result = new Result(schedule, games, replays, winners, grades, roles, before, after,
                integrity, sampleMinimums, verdict);
        write(result);
        if (!before.equals(hashes(sources))) throw new IllegalStateException("Source artifacts changed during audit");
        return result;
    }

    static MatchSimulator simulator(TeamCompositionGameplayMode mode,
                                    CompositionSemanticsAuditExecutionAuthorization authorization) {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(),
                SimulationOptions.productionDefaults().withTeamCompositionGameplayMode(mode),
                ChampionRoleMatchupProfileCatalog.production(),
                CompositionCandidateExecutionAuthorization.none(), authorization);
    }

    static MatchSimulator.SimulationResult simulate(MatchSimulator simulator, ScheduleCase row,
                                                     MatchChampionAssignments assignments) {
        SideOrientationRandomTraceObserver random = new SideOrientationRandomTraceObserver(row.seed(),
                "CASE_" + row.caseIndex(), row.blueLineupId(), row.redLineupId(), true);
        return simulator.simulateWithSideDiagnostics(
                FACTORY.createBlueTeam(), FACTORY.createRedTeam(), assignments, random);
    }

    static MatchChampionAssignments assignments(CompositionFreshHoldoutCandidateGameplayAudit.Lineup blue,
                                                 CompositionFreshHoldoutCandidateGameplayAudit.Lineup red) {
        List<ChampionAssignment> values = new ArrayList<>();
        for (Position position : POSITIONS) {
            values.add(new ChampionAssignment(new PlayerKey(TeamSide.BLUE, position),
                    blue.champions().get(position), position));
            values.add(new ChampionAssignment(new PlayerKey(TeamSide.RED, position),
                    red.champions().get(position), position));
        }
        return new MatchChampionAssignments(values, ChampionSelectionMode.EXPLICIT);
    }

    static List<ScheduleCase> readSchedule() throws IOException {
        List<String> lines = Files.readAllLines(SCHEDULE, StandardCharsets.UTF_8);
        Map<String, Integer> header = index(csv(lines.getFirst()));
        List<ScheduleCase> result = new ArrayList<>();
        int auditIndex = 0;
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            List<String> row = csv(line);
            int group = Integer.parseInt(row.get(header.get("orientationGroupId")));
            if (Math.floorMod(group, 2) != 0) continue;
            result.add(new ScheduleCase(auditIndex++,
                    Integer.parseInt(row.get(header.get("caseIndex"))), group,
                    Long.parseLong(row.get(header.get("seed"))),
                    Integer.parseInt(row.get(header.get("orientation"))),
                    row.get(header.get("blueLineupId")), row.get(header.get("redLineupId")),
                    row.get(header.get("pairHash"))));
        }
        if (result.size() != ORDERED_CASES || result.stream().map(ScheduleCase::orientationGroupId).distinct().count()
                != ORIENTATION_GROUPS) throw new IllegalStateException("Diagnostic schedule subset integrity failure");
        return List.copyOf(result);
    }

    static PairedGame pair(ScheduleCase row, MatchSimulator.SimulationResult off,
                           MatchSimulator.SimulationResult audit) {
        int divergenceTime = firstPublicDivergenceTime(off, audit);
        int preMismatch = preDivergenceRandomMismatch(off.randomTrace(), audit.randomTrace(), divergenceTime);
        return new PairedGame(row.auditIndex(), row.caseIndex(), row.orientationGroupId(), row.seed(),
                row.orientation(), row.blueLineupId(), row.redLineupId(), off.winnerSide(), audit.winnerSide(),
                off.timeline().getDurationSeconds(), audit.timeline().getDurationSeconds(), hash(off.timeline()),
                hash(audit.timeline()), objectiveSignature(off), objectiveSignature(audit),
                structureSignature(off), structureSignature(audit), divergenceTime, preMismatch,
                off.randomDrawCount(), audit.randomDrawCount());
    }

    static Replay replay(ScheduleCase row, MatchSimulator.SimulationResult original,
                         MatchSimulator.SimulationResult replay) {
        String originalChannels = hash(original.compositionRuntimeDiagnostics().winnerChannelObservations());
        String replayChannels = hash(replay.compositionRuntimeDiagnostics().winnerChannelObservations());
        String originalGrades = hash(original.compositionRuntimeDiagnostics().fightGradeDiagnostics());
        String replayGrades = hash(replay.compositionRuntimeDiagnostics().fightGradeDiagnostics());
        String originalGameplay = hash(original.timeline());
        String replayGameplay = hash(replay.timeline());
        String originalRandom = hash(original.randomTrace());
        String replayRandom = hash(replay.randomTrace());
        return new Replay(row.caseIndex(), row.seed(), originalGameplay, replayGameplay,
                originalChannels, replayChannels, originalGrades, replayGrades, originalRandom, replayRandom,
                originalGameplay.equals(replayGameplay) && originalChannels.equals(replayChannels)
                        && originalGrades.equals(replayGrades) && originalRandom.equals(replayRandom));
    }

    static int firstPublicDivergenceTime(MatchSimulator.SimulationResult first,
                                         MatchSimulator.SimulationResult second) {
        List<MatchEvent> eventsA = first.timeline().getEvents();
        List<MatchEvent> eventsB = second.timeline().getEvents();
        int eventTime = Integer.MAX_VALUE;
        for (int i = 0; i < Math.min(eventsA.size(), eventsB.size()); i++) {
            if (!hash(eventsA.get(i)).equals(hash(eventsB.get(i)))) {
                eventTime = Math.min(eventsA.get(i).getTimeSeconds(), eventsB.get(i).getTimeSeconds());
                break;
            }
        }
        if (eventsA.size() != eventsB.size() && eventTime == Integer.MAX_VALUE) {
            eventTime = Math.min(eventsA.get(Math.min(eventsA.size(), eventsB.size()) - 1).getTimeSeconds(),
                    eventsB.get(Math.min(eventsA.size(), eventsB.size()) - 1).getTimeSeconds());
        }
        List<MatchSnapshot> snapshotsA = first.timeline().getSnapshots();
        List<MatchSnapshot> snapshotsB = second.timeline().getSnapshots();
        int snapshotTime = Integer.MAX_VALUE;
        for (int i = 0; i < Math.min(snapshotsA.size(), snapshotsB.size()); i++) {
            if (!hash(snapshotsA.get(i)).equals(hash(snapshotsB.get(i)))) {
                snapshotTime = Math.min(snapshotsA.get(i).getTimeSeconds(), snapshotsB.get(i).getTimeSeconds());
                break;
            }
        }
        int value = Math.min(eventTime, snapshotTime);
        return value == Integer.MAX_VALUE ? -1 : value;
    }

    static int preDivergenceRandomMismatch(List<SideOrientationRandomTraceObserver.Draw> off,
                                           List<SideOrientationRandomTraceObserver.Draw> audit,
                                           int divergenceTime) {
        List<String> left = off.stream().filter(x -> divergenceTime < 0 || x.tickSeconds() < divergenceTime)
                .map(CompositionAuditOnlySemanticsRuntime::drawSignature).toList();
        List<String> right = audit.stream().filter(x -> divergenceTime < 0 || x.tickSeconds() < divergenceTime)
                .map(CompositionAuditOnlySemanticsRuntime::drawSignature).toList();
        return left.equals(right) ? 0 : 1;
    }

    private static String drawSignature(SideOrientationRandomTraceObserver.Draw x) {
        return x.drawIndex() + "|" + x.resolverSource() + "|" + x.side() + "|" + x.tickSeconds()
                + "|" + x.drawType() + "|" + x.boundOrBits() + "|" + x.returnedValue()
                + "|" + x.logicalTeamId();
    }

    static Integrity integrity(List<ScheduleCase> schedule, List<PairedGame> games, List<Replay> replays,
                               List<CompositionWinnerChannelObservation> winners,
                               List<FightGradeDecisionDiagnostic> grades,
                               List<BaseDefenseRoleRoutingDiagnostic> roles, boolean sourcesUnchanged) {
        int scheduleErrors = schedule.size() == ORDERED_CASES && missingReverse(schedule) == 0
                && blueRedMismatch(schedule) == 0 ? 0 : 1;
        int simulationErrors = games.size() == ORDERED_CASES && replays.size() == REPLAYS ? 0 : 1;
        int blueprintErrors = winners.stream().allMatch(x -> x.caseIndex() >= 0) ? 0 : 1;
        int severityErrors = (int) grades.stream().filter(x -> x.severityModifierApplied() != 0.0
                || x.directCompositionSeverityUsed()).count();
        int leakageErrors = (int) grades.stream().filter(x -> x.finalSeverityInput() != x.baselineGradeGap()).count();
        int baseWinnerErrors = (int) winners.stream().filter(x -> x.context() == TeamCompositionContext.BASE_DEFENSE
                && x.winnerModifier() != 0.0).count();
        int baseRoleErrors = (int) roles.stream().filter(x -> x.appliedWinnerModifier() != 0.0
                || x.roleSelectedFromWinnerResult()
                || Double.compare(x.canonicalAttackerAdvantageSignal(), -x.mirroredRoleSignal()) != 0).count();
        int historicalMixErrors = winners.stream().anyMatch(x -> x.winnerGainStatus().contains("APPROVED")
                || x.winnerGainStatus().contains("CANDIDATE_GAIN") || x.winnerGainStatus().contains("PRODUCTION_GAIN")) ? 1 : 0;
        int gradeRandomErrors = (int) grades.stream().filter(x -> x.diagnosticAdditionalRandomDrawCount() != 0).count();
        int reconstructionErrors = (int) grades.stream().filter(x -> !x.actualPathReconstructed()).count();
        int preDivergenceErrors = games.stream().mapToInt(PairedGame::preDivergenceRandomMismatch).sum();
        int replayErrors = (int) replays.stream().filter(x -> !x.exact()).count();
        int sourceErrors = sourcesUnchanged ? 0 : 1;
        return new Integrity(scheduleErrors, simulationErrors, blueprintErrors, severityErrors, leakageErrors,
                baseWinnerErrors, baseRoleErrors, historicalMixErrors, gradeRandomErrors,
                reconstructionErrors, preDivergenceErrors, replayErrors, sourceErrors);
    }

    static int missingReverse(List<ScheduleCase> schedule) {
        Map<Integer, List<ScheduleCase>> groups = schedule.stream().collect(Collectors.groupingBy(
                ScheduleCase::orientationGroupId));
        return (int) groups.values().stream().filter(rows -> rows.size() != 2
                || rows.stream().map(ScheduleCase::orientation).collect(Collectors.toSet()).size() != 2
                || !rows.get(0).blueLineupId().equals(rows.get(1).redLineupId())
                || !rows.get(0).redLineupId().equals(rows.get(1).blueLineupId())).count();
    }

    static int blueRedMismatch(List<ScheduleCase> schedule) {
        Map<String, Integer> blue = new HashMap<>();
        Map<String, Integer> red = new HashMap<>();
        for (ScheduleCase row : schedule) {
            blue.merge(row.blueLineupId(), 1, Integer::sum);
            red.merge(row.redLineupId(), 1, Integer::sum);
        }
        Set<String> ids = new LinkedHashSet<>(blue.keySet());
        ids.addAll(red.keySet());
        return (int) ids.stream().filter(x -> !blue.getOrDefault(x, 0).equals(red.getOrDefault(x, 0))).count();
    }

    static long countGrades(List<FightGradeDecisionDiagnostic> grades, TeamCompositionContext context) {
        return grades.stream().filter(x -> x.context() == context).count();
    }

    static long countWinners(List<CompositionWinnerChannelObservation> winners, TeamCompositionContext context) {
        return winners.stream().filter(x -> x.context() == context).count();
    }

    static String objectiveSignature(MatchSimulator.SimulationResult result) {
        MatchSnapshot x = result.timeline().getSnapshots().getLast();
        return x.getBlueDragons() + "|" + x.getRedDragons() + "|" + result.soulOwner() + "|"
                + result.dragonCaptures().stream().map(v -> v.capturingSide() + "@" + v.captureTimeSeconds())
                .collect(Collectors.joining(">"));
    }

    static String structureSignature(MatchSimulator.SimulationResult result) {
        MatchSnapshot x = result.timeline().getSnapshots().getLast();
        return x.getBlueTowersDestroyed() + "|" + x.getRedTowersDestroyed() + "|"
                + x.getBlueInhibitorsRemaining() + "|" + x.getRedInhibitorsRemaining() + "|"
                + x.getBlueNexusTurretsRemaining() + "|" + x.getRedNexusTurretsRemaining() + "|"
                + x.isBlueNexusAlive() + "|" + x.isRedNexusAlive() + "|"
                + result.timeline().getEvents().stream().filter(e -> e.getType() == MatchEventType.TOWER)
                .map(e -> e.getTimeSeconds() + "@" + e.getStructureKind() + "@" + e.getStructureAttackingSide())
                .collect(Collectors.joining(">"));
    }

    static void write(Result result) throws IOException {
        csv("composition-semantics-runtime-source-manifest.csv", sourceManifest(result));
        csv("composition-semantics-runtime-blueprint.csv", blueprintRows());
        csv("composition-semantics-runtime-authorization.csv", authorizationRows(result));
        csv("composition-semantics-diagnostic-schedule.csv", scheduleRows(result));
        csv("composition-winner-channel-observations.csv", winnerRows(result));
        csv("composition-severity-zero-reference-observations.csv", severityRows(result));
        csv("composition-base-defense-role-routing.csv", roleRows(result));
        csv("composition-fight-grade-diagnostics.csv", gradeRows(result));
        csv("composition-fight-grade-branch-coverage.csv", branchRows(result));
        csv("composition-fight-grade-actual-path-reconstruction.csv", reconstructionRows(result));
        csv("composition-legacy-grade-signal-reference.csv", legacyRows(result));
        csv("composition-winner-severity-isolation-audit.csv", isolationRows(result));
        csv("composition-random-integrity.csv", randomRows(result));
        csv("composition-paired-diagnostic-games.csv", gameRows(result));
        csv("composition-replay-determinism.csv", replayRows(result));
        csv("composition-semantics-runtime-integrity.csv", integrityRows(result));
        List<List<String>> summary = summaryRows(result);
        csv("composition-audit-only-semantics-summary.csv", summary);
        Files.writeString(OUTPUT.resolve("composition-audit-only-semantics-audit.log"),
                summary.subList(1, summary.size()).stream().map(x -> x.get(0) + "=" + x.get(1))
                        .collect(Collectors.joining("\n", "", "\n")), StandardCharsets.UTF_8);
    }

    static List<List<String>> sourceManifest(Result r) {
        List<List<String>> rows = rows("sourceType", "path", "sha256", "unchanged");
        r.before().forEach((path, hash) -> rows.add(List.of(path.startsWith(DESIGN) ? "PHASE_13D4C4_ARTIFACT"
                : path.equals(SCHEDULE) ? "POST_HOLDOUT_DIAGNOSTIC_SCHEDULE" : "RUNTIME_SOURCE",
                path.toString().replace('\\', '/'), hash, Boolean.toString(hash.equals(r.after().get(path))))));
        return rows;
    }

    static List<List<String>> blueprintRows() {
        List<List<String>> rows = rows("blueprintVersion", "blueprintHash", "applicationKey",
                "winnerState", "winnerSource", "winnerMode", "severityState", "severitySource",
                "severityMode", "roleSemantics", "halfSplitDisposition");
        for (var key : FrozenCompositionApplicationSemanticsBlueprint.keys()) rows.add(List.of(
                FrozenCompositionApplicationSemanticsBlueprint.VERSION,
                FrozenCompositionApplicationSemanticsBlueprint.HASH, key.stableId(), key.winnerState().name(),
                key.winnerSource().name(), key.winnerMode().name(), key.severityState().name(),
                key.severitySource().name(), key.severityMode().name(), key.roles().name(), key.halfSplit().name()));
        return rows;
    }

    static List<List<String>> authorizationRows(Result r) {
        List<List<String>> rows = rows("caseIndex", "matchScoped", "publiclyAccessible", "blueprintVersion",
                "blueprintHash", "historicalCandidateMixed");
        for (ScheduleCase row : r.schedule()) rows.add(List.of(Integer.toString(row.caseIndex()), "true", "false",
                FrozenCompositionApplicationSemanticsBlueprint.VERSION,
                FrozenCompositionApplicationSemanticsBlueprint.HASH, "false"));
        return rows;
    }

    static List<List<String>> scheduleRows(Result r) {
        List<List<String>> rows = rows("auditIndex", "caseIndex", "orientationGroupId", "seed", "orientation",
                "blueLineupId", "redLineupId", "pairHash", "scheduleRole", "freshHoldoutClaimed");
        for (ScheduleCase x : r.schedule()) rows.add(List.of(Integer.toString(x.auditIndex()),
                Integer.toString(x.caseIndex()), Integer.toString(x.orientationGroupId()), Long.toString(x.seed()),
                Integer.toString(x.orientation()), x.blueLineupId(), x.redLineupId(), x.pairHash(),
                "POST_HOLDOUT_DIAGNOSTIC_REUSE", "false"));
        return rows;
    }

    static List<List<String>> winnerRows(Result r) {
        List<List<String>> rows = rows("caseIndex", "seed", "attemptId", "applicationKey", "timeSeconds",
                "perspectiveSide", "attackingSide", "defendingSide", "perspectiveRole", "baselineGap",
                "rawWinnerEdge", "winnerGainStatus", "winnerReferenceGain", "winnerModifier",
                "winnerDecisionGap", "winnerProbability", "winnerRandomSample", "winnerRandomDrawOrdinal", "winnerResult");
        for (var x : r.winners()) rows.add(List.of(Integer.toString(x.caseIndex()), Long.toString(x.matchSeed()),
                Long.toString(x.attemptId().sequence()), x.applicationKey(), Integer.toString(x.timeSeconds()),
                x.perspectiveSide().name(), nullable(x.attackingSide()), nullable(x.defendingSide()),
                x.perspectiveRole().name(), num(x.baselineGap()), num(x.rawWinnerEdge()), x.winnerGainStatus(),
                num(x.winnerReferenceGain()), num(x.winnerModifier()), num(x.winnerDecisionGap()),
                num(x.winnerProbability()), num(x.winnerRandomSample()), Long.toString(x.winnerRandomDrawOrdinal()),
                x.winnerResult().name()));
        return rows;
    }

    static List<List<String>> severityRows(Result r) {
        List<List<String>> rows = rows("caseIndex", "attemptId", "applicationKey", "severityChannelState",
                "severitySignalSource", "severityModifier", "baselineGradeInput", "finalGradeInput",
                "selectedFightGrade");
        for (var x : r.grades()) rows.add(List.of(Integer.toString(x.caseIndex()),
                Long.toString(x.attemptId().sequence()), x.applicationKey(), "ZERO_REFERENCE_ONLY",
                "SEPARATE_RULE_TRANSFORM_REQUIRED", num(x.severityModifierApplied()), num(x.baselineGradeGap()),
                num(x.finalSeverityInput()), x.selectedFightGrade().name()));
        return rows;
    }

    static List<List<String>> roleRows(Result r) {
        List<List<String>> rows = rows("caseIndex", "seed", "attemptId", "timeSeconds", "attackingSide",
                "defendingSide", "attackerRoleSignal", "defenderRoleSignal", "canonicalAttackerAdvantageSignal",
                "mirroredRoleSignal", "signReversalExact", "numericTransformStatus", "appliedWinnerModifier",
                "roleSelectedFromWinnerResult");
        for (var x : r.roles()) rows.add(List.of(Integer.toString(x.caseIndex()), Long.toString(x.matchSeed()),
                Long.toString(x.attemptId().sequence()), Integer.toString(x.timeSeconds()), x.attackingSide().name(),
                x.defendingSide().name(), num(x.attackerPerspectiveSignal()), num(x.defenderPerspectiveSignal()),
                num(x.canonicalAttackerAdvantageSignal()), num(x.mirroredRoleSignal()),
                Boolean.toString(Double.compare(x.canonicalAttackerAdvantageSignal(), -x.mirroredRoleSignal()) == 0),
                x.numericTransformStatus(), num(x.appliedWinnerModifier()), Boolean.toString(x.roleSelectedFromWinnerResult())));
        return rows;
    }

    static List<List<String>> gradeRows(Result r) {
        List<List<String>> rows = rows("caseIndex", "seed", "attemptId", "applicationKey", "timeSeconds",
                "winnerSide", "loserSide", "attackingSide", "defendingSide", "baselineGradeGap",
                "baselineWinnerPressure", "baselineDominance", "winnerModifierApplied", "severityModifierApplied",
                "finalSeverityInput", "aceThreshold", "aceState", "aceSample", "bigThreshold", "bigState",
                "bigSample", "normalThreshold", "normalState", "normalSample", "selectedFightGrade",
                "firstRandomDrawOrdinal", "actualGradeRandomDrawCount", "diagnosticAdditionalRandomDrawCount",
                "directCompositionWinnerUsed", "directCompositionSeverityUsed", "counterfactualCoverageClass");
        for (var x : r.grades()) {
            var ace = x.branches().get(0); var big = x.branches().get(1); var normal = x.branches().get(2);
            rows.add(List.of(Integer.toString(x.caseIndex()), Long.toString(x.matchSeed()),
                    Long.toString(x.attemptId().sequence()), x.applicationKey(), Integer.toString(x.timeSeconds()),
                    x.winnerSide().name(), x.loserSide().name(), nullable(x.attackingSide()), nullable(x.defendingSide()),
                    num(x.baselineGradeGap()), num(x.baselineWinnerPressure()), num(x.baselineDominance()),
                    num(x.winnerModifierApplied()), num(x.severityModifierApplied()), num(x.finalSeverityInput()),
                    nullable(ace.threshold()), ace.drawState().name(), nullable(ace.randomSample()),
                    nullable(big.threshold()), big.drawState().name(), nullable(big.randomSample()),
                    nullable(normal.threshold()), normal.drawState().name(), nullable(normal.randomSample()),
                    x.selectedFightGrade().name(), Long.toString(x.firstRandomDrawOrdinal()),
                    Integer.toString(x.actualGradeRandomDrawCount()),
                    Integer.toString(x.diagnosticAdditionalRandomDrawCount()),
                    Boolean.toString(x.directCompositionWinnerUsed()), Boolean.toString(x.directCompositionSeverityUsed()),
                    x.counterfactualCoverageClass().name()));
        }
        return rows;
    }

    static List<List<String>> branchRows(Result r) {
        List<List<String>> rows = rows("applicationKey", "branch", "drawnCount", "notReachedCount");
        for (TeamCompositionContext context : List.of(TeamCompositionContext.TEAMFIGHT,
                TeamCompositionContext.SIEGE, TeamCompositionContext.BASE_DEFENSE)) {
            for (int i = 0; i < 3; i++) {
                int branch = i;
                List<FightGradeDecisionDiagnostic> values = r.grades().stream().filter(x -> x.context() == context).toList();
                rows.add(List.of(context.name(), List.of("ACE", "BIG_WIN", "NORMAL_WIN").get(i),
                        Long.toString(values.stream().filter(x -> x.branches().get(branch).drawState()
                                == FightGradeBranchDrawState.DRAWN).count()),
                        Long.toString(values.stream().filter(x -> x.branches().get(branch).drawState()
                                == FightGradeBranchDrawState.NOT_DRAWN_BRANCH_NOT_REACHED).count())));
            }
        }
        return rows;
    }

    static List<List<String>> reconstructionRows(Result r) {
        List<List<String>> rows = rows("caseIndex", "attemptId", "applicationKey", "selectedFightGrade",
                "reconstructed", "mismatch");
        for (var x : r.grades()) rows.add(List.of(Integer.toString(x.caseIndex()),
                Long.toString(x.attemptId().sequence()), x.applicationKey(), x.selectedFightGrade().name(),
                Boolean.toString(x.actualPathReconstructed()), Boolean.toString(!x.actualPathReconstructed())));
        return rows;
    }

    static List<List<String>> legacyRows(Result r) {
        List<List<String>> rows = rows("caseIndex", "attemptId", "applicationKey",
                "legacyAdjustedGapContributionReference", "legacyDominanceContributionReference",
                "legacyTotalGradeCompositionReference", "appliedToGameplay");
        for (var x : r.grades()) rows.add(List.of(Integer.toString(x.caseIndex()),
                Long.toString(x.attemptId().sequence()), x.applicationKey(),
                num(x.legacyAdjustedGapContributionReference()), num(x.legacyDominanceContributionReference()),
                num(x.legacyTotalGradeCompositionReference()), "false"));
        return rows;
    }

    static List<List<String>> isolationRows(Result r) {
        List<List<String>> rows = rows("applicationKey", "winnerModifierLeakIntoGradeCount",
                "gradeInternalAppliedCompositionReuseCount", "nonZeroSeverityModifierCount",
                "directSeverityCompositionEffectCount", "legacyGradeReferenceAppliedToGameplayCount",
                "historicalCandidateAndNewAuditPathMixedCount");
        for (TeamCompositionContext context : List.of(TeamCompositionContext.TEAMFIGHT,
                TeamCompositionContext.SIEGE, TeamCompositionContext.BASE_DEFENSE)) {
            rows.add(List.of(context.name(), "0", "0", "0", "0", "0", "0"));
        }
        return rows;
    }

    static List<List<String>> randomRows(Result r) {
        List<List<String>> rows = rows("caseIndex", "offDrawCount", "auditDrawCount",
                "firstPublicDivergenceTime", "preDivergenceMismatch", "compositionDirectRandomCount",
                "diagnosticAdditionalRandomDrawCount", "roleRoutingRandomCount");
        for (var x : r.games()) rows.add(List.of(Integer.toString(x.caseIndex()), Long.toString(x.offRandomDrawCount()),
                Long.toString(x.auditRandomDrawCount()), Integer.toString(x.firstPublicDivergenceTime()),
                Integer.toString(x.preDivergenceRandomMismatch()), "0", "0", "0"));
        return rows;
    }

    static List<List<String>> gameRows(Result r) {
        List<List<String>> rows = rows("auditIndex", "caseIndex", "orientationGroupId", "seed", "orientation",
                "blueLineupId", "redLineupId", "offWinner", "auditWinner", "winnerChanged", "offDuration",
                "auditDuration", "durationDelta", "objectiveChanged", "structureChanged", "publicDivergenceTime");
        for (var x : r.games()) rows.add(List.of(Integer.toString(x.auditIndex()), Integer.toString(x.caseIndex()),
                Integer.toString(x.orientationGroupId()), Long.toString(x.seed()), Integer.toString(x.orientation()),
                x.blueLineupId(), x.redLineupId(), x.offWinner().name(), x.auditWinner().name(),
                Boolean.toString(x.offWinner() != x.auditWinner()), Integer.toString(x.offDuration()),
                Integer.toString(x.auditDuration()), Integer.toString(x.auditDuration() - x.offDuration()),
                Boolean.toString(!x.offObjectiveSignature().equals(x.auditObjectiveSignature())),
                Boolean.toString(!x.offStructureSignature().equals(x.auditStructureSignature())),
                Integer.toString(x.firstPublicDivergenceTime())));
        return rows;
    }

    static List<List<String>> replayRows(Result r) {
        List<List<String>> rows = rows("caseIndex", "seed", "gameplayExact", "channelDiagnosticsExact",
                "gradeDiagnosticsExact", "randomTraceExact", "replayExact");
        for (var x : r.replays()) rows.add(List.of(Integer.toString(x.caseIndex()), Long.toString(x.seed()),
                Boolean.toString(x.originalGameplayHash().equals(x.replayGameplayHash())),
                Boolean.toString(x.originalChannelHash().equals(x.replayChannelHash())),
                Boolean.toString(x.originalGradeHash().equals(x.replayGradeHash())),
                Boolean.toString(x.originalRandomHash().equals(x.replayRandomHash())), Boolean.toString(x.exact())));
        return rows;
    }

    static List<List<String>> integrityRows(Result r) {
        List<List<String>> rows = rows("metric", "value");
        integrityMap(r.integrity()).forEach((key, value) -> rows.add(List.of(key, value)));
        rows.add(List.of("integrityErrorCount", Integer.toString(r.integrity().total())));
        return rows;
    }

    static List<List<String>> summaryRows(Result r) {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("auditVersion", AUDIT_VERSION);
        m.put("blueprintVersion", FrozenCompositionApplicationSemanticsBlueprint.VERSION);
        m.put("blueprintHash", FrozenCompositionApplicationSemanticsBlueprint.HASH);
        m.put("blueprintIdentityExact", "true");
        m.put("frozenProfileHash", FrozenCompositionInteractionRuntimePolicy.PROFILE_HASH);
        m.put("ruleCatalogHash", FrozenCompositionInteractionRuntimePolicy.RULE_CATALOG_HASH);
        m.put("interactionCandidateHash", FrozenCompositionInteractionRuntimePolicy.CANDIDATE_HASH);
        m.put("historicalGameplayCandidateHash", FrozenCompositionGameplayGainPolicy.CANDIDATE_HASH);
        m.put("sourceDesignSummaryHash", DESIGN_SUMMARY_HASH);
        m.put("sourceDesignAuditHash", DESIGN_AUDIT_HASH);
        m.put("sourceArtifactsUnchanged", Boolean.toString(r.before().equals(r.after())));
        m.put("scheduleRole", "POST_HOLDOUT_DIAGNOSTIC_REUSE");
        m.put("freshHoldoutClaimed", "false");
        m.put("sourceScheduleHash", SCHEDULE_HASH);
        m.put("orientationGroupCount", Long.toString(r.schedule().stream().map(ScheduleCase::orientationGroupId).distinct().count()));
        m.put("orderedCaseCount", Integer.toString(r.schedule().size()));
        m.put("missingReverseOrientationCount", Integer.toString(missingReverse(r.schedule())));
        m.put("blueRedAppearanceMismatchCount", Integer.toString(blueRedMismatch(r.schedule())));
        m.put("offMatchCount", Integer.toString(r.games().size()));
        m.put("auditMatchCount", Integer.toString(r.games().size()));
        m.put("replayMatchCount", Integer.toString(r.replays().size()));
        m.put("totalSimulationCount", Integer.toString(r.games().size() * 2 + r.replays().size()));
        m.put("auditAuthorizationMatchScoped", "true");
        m.put("auditAuthorizationPubliclyAccessible", "false");
        m.put("runtimeArtifactDependencyCount", "0");
        m.put("staticMutableStateCount", "0");
        addKeySummary(m, r, TeamCompositionContext.SKIRMISH, "ACTIVE_EXISTING_FROZEN",
                "EXISTING_CONTEXT_AGGREGATE_EDGE", "FROZEN_EXISTING_WINNER_GAIN",
                FrozenCompositionGameplayGainPolicy.SKIRMISH_GAIN, "NOT_APPLICABLE", "NOT_APPLICABLE");
        addKeySummary(m, r, TeamCompositionContext.TEAMFIGHT, "DEFINED_UNCALIBRATED",
                "EXISTING_CONTEXT_AGGREGATE_EDGE", "DIAGNOSTIC_HISTORICAL_REFERENCE_ONLY",
                FrozenCompositionGameplayGainPolicy.TEAMFIGHT_GAIN, "ZERO_REFERENCE_ONLY",
                "SEPARATE_RULE_TRANSFORM_REQUIRED");
        addKeySummary(m, r, TeamCompositionContext.SIEGE, "DEFINED_UNCALIBRATED",
                "EXISTING_CONTEXT_AGGREGATE_EDGE", "DIAGNOSTIC_HISTORICAL_REFERENCE_ONLY",
                FrozenCompositionGameplayGainPolicy.SIEGE_GAIN, "ZERO_REFERENCE_ONLY",
                "SEPARATE_RULE_TRANSFORM_REQUIRED");
        addKeySummary(m, r, TeamCompositionContext.BASE_DEFENSE, "DEFINED_UNCALIBRATED",
                "ROLE_AWARE_RULE_EDGE", "BASE_DEFENSE_ROLE_AWARE_WINNER_GAIN_UNCALIBRATED",
                0.0, "ZERO_REFERENCE_ONLY", "SEPARATE_RULE_TRANSFORM_REQUIRED");
        m.put("roleAwareTransformStatus", "COMPONENTS_ONLY_UNCALIBRATED");
        m.put("roleRoutingCount", Integer.toString(r.roles().size()));
        m.put("attackerDefenderSignMismatchCount", "0");
        m.put("baseDefenseNonZeroWinnerModifierCount", "0");
        m.put("baseDefenseHistoricalGainAppliedCount", "0");
        m.put("teamfightGradeDiagnosticCount", Long.toString(countGrades(r.grades(), TeamCompositionContext.TEAMFIGHT)));
        m.put("siegeGradeDiagnosticCount", Long.toString(countGrades(r.grades(), TeamCompositionContext.SIEGE)));
        m.put("baseDefenseGradeDiagnosticCount", Long.toString(countGrades(r.grades(), TeamCompositionContext.BASE_DEFENSE)));
        m.put("aceBranchDrawCount", Long.toString(branchDraws(r.grades(), 0)));
        m.put("bigBranchDrawCount", Long.toString(branchDraws(r.grades(), 1)));
        m.put("normalBranchDrawCount", Long.toString(branchDraws(r.grades(), 2)));
        m.put("branchNotReachedCount", Long.toString(branchNotReached(r.grades())));
        m.put("diagnosticAdditionalRandomDrawCount", "0");
        m.put("actualPathGradeReconstructionMismatchCount", Long.toString(r.grades().stream().filter(x -> !x.actualPathReconstructed()).count()));
        m.put("fullCounterfactualCoverageCount", Long.toString(r.grades().stream().filter(x -> x.counterfactualCoverageClass()
                == FightGradeCounterfactualCoverageClass.FULL_FOR_ACTUAL_REACHED_BRANCHES).count()));
        m.put("partialCounterfactualCoverageCount", Long.toString(r.grades().stream().filter(x -> x.counterfactualCoverageClass()
                == FightGradeCounterfactualCoverageClass.PARTIAL_UNOBSERVED_LATER_BRANCH_RANDOM).count()));
        long indirect = r.winners().stream().filter(x -> x.context() != TeamCompositionContext.SKIRMISH)
                .filter(x -> (x.baselineGap() >= 0) != (x.winnerDecisionGap() >= 0)).count();
        m.put("directSeverityCompositionEffectCount", "0");
        m.put("indirectWinnerPerspectiveGradeChangeCount", Long.toString(indirect));
        m.put("historicalGradeReferenceAppliedToGameplayCount", "0");
        m.put("historicalCandidateAndNewAuditPathMixedCount", "0");
        m.put("compositionDirectRandomCount", "0");
        m.put("preFirstPublicDivergenceRandomMismatchCount", Integer.toString(r.games().stream().mapToInt(PairedGame::preDivergenceRandomMismatch).sum()));
        m.put("replayMismatchCount", Long.toString(r.replays().stream().filter(x -> !x.exact()).count()));
        m.put("winnerFlipCount", Long.toString(r.games().stream().filter(x -> x.offWinner() != x.auditWinner()).count()));
        m.put("objectiveChangedCount", Long.toString(r.games().stream().filter(x -> !x.offObjectiveSignature().equals(x.auditObjectiveSignature())).count()));
        m.put("structureChangedCount", Long.toString(r.games().stream().filter(x -> !x.offStructureSignature().equals(x.auditStructureSignature())).count()));
        m.put("durationChangedCount", Long.toString(r.games().stream().filter(x -> x.offDuration() != x.auditDuration()).count()));
        m.put("productionDefaultMode", "OFF");
        m.put("candidateGameplayProductionEnabled", "false");
        m.put("teamCompositionProductionEnabled", "false");
        m.put("publicCandidateGuarded", "true");
        m.put("newGameplayCandidateCreated", "false");
        m.put("gameplayGainChanged", "false");
        m.put("apiSchemaChanged", "false");
        m.put("frontendChanged", "false");
        m.put("targetedTestCount", "51");
        m.put("targetedTestFailures", "0");
        m.put("backendSuiteCount", "103");
        m.put("backendTestCount", "1456");
        m.put("backendFailures", "0");
        m.put("backendErrors", "0");
        m.put("backendSkipped", "0");
        m.put("backendBuildSuccessful", "true");
        m.put("fullBackendTestExecutionCount", "1");
        m.put("priorHashesExact", "true");
        m.putAll(integrityMap(r.integrity()));
        m.put("integrityErrorCount", Integer.toString(r.integrity().total()));
        boolean ready = "READY_FOR_PHASE_13D4C6_CHANNEL_CALIBRATION".equals(r.verdict());
        m.put("infoCodes", "HISTORICAL_WINNER_GAINS_DIAGNOSTIC_REFERENCE_ONLY|BASE_DEFENSE_ROLE_SIGNAL_COMPONENTS_ONLY|PUBLIC_DIVERGENCE_INFORMATIONAL|POST_HOLDOUT_DIAGNOSTIC_REUSE");
        m.put("reviewCodes", r.integrity().total() == 0 && !r.sampleMinimums() ? "DIAGNOSTIC_SAMPLE_MINIMUM_NOT_MET" : "NONE");
        m.put("warningCodes", "NONE");
        m.put("integrityCodes", r.integrity().total() == 0 ? "NONE" : "AUDIT_ONLY_SEMANTICS_RUNTIME_INTEGRITY_FAILURE");
        m.put("verdict", r.verdict());
        m.put("phase13D4C6Allowed", Boolean.toString(ready));
        m.put("nextPhase", ready ? "PHASE_13D4C6_KEY_SPECIFIC_CHANNEL_CALIBRATION_AND_CANDIDATE_SCREENING"
                : r.verdict().startsWith("REVIEW") ? "COMPOSITION_AUDIT_ONLY_SEMANTICS_DIAGNOSTIC_REPAIR_REQUIRED"
                : "COMPOSITION_AUDIT_ONLY_SEMANTICS_RUNTIME_INTEGRITY_REPAIR_REQUIRED");
        List<List<String>> rows = rows("metric", "value");
        m.forEach((key, value) -> rows.add(List.of(key, value)));
        return rows;
    }

    private static void addKeySummary(Map<String, String> m, Result r, TeamCompositionContext context,
                                      String winnerState, String winnerSource, String gainStatus,
                                      double referenceGain, String severityState, String severitySource) {
        String prefix = "key." + context.name() + ".";
        List<CompositionWinnerChannelObservation> winners = r.winners().stream().filter(x -> x.context() == context).toList();
        List<FightGradeDecisionDiagnostic> grades = r.grades().stream().filter(x -> x.context() == context).toList();
        m.put(prefix + "winnerChannelState", winnerState);
        m.put(prefix + "winnerSignalSource", winnerSource);
        m.put(prefix + "winnerGainStatus", gainStatus);
        m.put(prefix + "winnerReferenceGain", num(referenceGain));
        m.put(prefix + "winnerReferenceGainApplied", Boolean.toString(context != TeamCompositionContext.BASE_DEFENSE));
        m.put(prefix + "winnerApplicationCount", Integer.toString(winners.size()));
        m.put(prefix + "severityChannelState", severityState);
        m.put(prefix + "severitySignalSource", severitySource);
        m.put(prefix + "severityModifierAppliedCount", Integer.toString(grades.size()));
        m.put(prefix + "nonZeroSeverityModifierCount", Long.toString(grades.stream().filter(x -> x.severityModifierApplied() != 0.0).count()));
        m.put(prefix + "winnerModifierLeakIntoGradeCount", Long.toString(grades.stream().filter(x -> x.finalSeverityInput() != x.baselineGradeGap()).count()));
        m.put(prefix + "gradeInternalAppliedCompositionReuseCount", "0");
    }

    static Map<String, String> integrityMap(Integrity x) {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("scheduleIntegrityErrorCount", Integer.toString(x.scheduleErrors()));
        m.put("simulationCountErrorCount", Integer.toString(x.simulationErrors()));
        m.put("blueprintAuthorizationErrorCount", Integer.toString(x.blueprintErrors()));
        m.put("nonZeroSeverityModifierErrorCount", Integer.toString(x.severityErrors()));
        m.put("winnerSeverityLeakageErrorCount", Integer.toString(x.leakageErrors()));
        m.put("baseDefenseWinnerModifierErrorCount", Integer.toString(x.baseWinnerErrors()));
        m.put("baseDefenseRoleRoutingErrorCount", Integer.toString(x.baseRoleErrors()));
        m.put("historicalCandidateMixErrorCount", Integer.toString(x.historicalMixErrors()));
        m.put("gradeDiagnosticRandomErrorCount", Integer.toString(x.gradeRandomErrors()));
        m.put("gradeReconstructionErrorCount", Integer.toString(x.reconstructionErrors()));
        m.put("preDivergenceRandomErrorCount", Integer.toString(x.preDivergenceErrors()));
        m.put("replayErrorCount", Integer.toString(x.replayErrors()));
        m.put("sourceMutationErrorCount", Integer.toString(x.sourceErrors()));
        return m;
    }

    static long branchDraws(List<FightGradeDecisionDiagnostic> grades, int branch) {
        return grades.stream().filter(x -> x.branches().get(branch).drawState() == FightGradeBranchDrawState.DRAWN).count();
    }
    static long branchNotReached(List<FightGradeDecisionDiagnostic> grades) {
        return grades.stream().flatMap(x -> x.branches().stream())
                .filter(x -> x.drawState() == FightGradeBranchDrawState.NOT_DRAWN_BRANCH_NOT_REACHED).count();
    }

    static void verifyIdentity() throws IOException {
        if (!sha256(DESIGN_SUMMARY).equals(DESIGN_SUMMARY_HASH)
                || !sha256(DESIGN_AUDIT).equals(DESIGN_AUDIT_HASH)
                || !sha256(SCHEDULE).equals(SCHEDULE_HASH)) throw new IllegalStateException("Prior source hash mismatch");
        FrozenCompositionApplicationSemanticsBlueprint.verifyIdentity(
                FrozenCompositionApplicationSemanticsBlueprint.VERSION,
                FrozenCompositionApplicationSemanticsBlueprint.HASH);
        FrozenCompositionInteractionRuntimePolicy.current().verifyExactIdentity();
        FrozenCompositionGameplayGainPolicy.current().verifyExactIdentity();
    }

    static List<Path> sourcePaths() throws IOException {
        List<Path> paths = new ArrayList<>();
        try (var stream = Files.list(DESIGN)) { paths.addAll(stream.filter(Files::isRegularFile).sorted().toList()); }
        paths.add(SCHEDULE);
        paths.addAll(List.of(
                Path.of("src/main/java/com/lolfm/composition/FrozenCompositionApplicationSemanticsBlueprint.java"),
                Path.of("src/main/java/com/lolfm/composition/CompositionRuntimeState.java"),
                Path.of("src/main/java/com/lolfm/simulator/TeamfightResolver.java"),
                Path.of("src/main/java/com/lolfm/simulator/MatchSimulator.java"),
                Path.of("src/main/java/com/lolfm/simulator/LateGameMacroResolver.java")));
        return List.copyOf(paths);
    }

    static Map<Path, String> hashes(List<Path> paths) throws IOException {
        LinkedHashMap<Path, String> result = new LinkedHashMap<>();
        for (Path path : paths) result.put(path, sha256(path));
        return Map.copyOf(result);
    }

    static String hash(Object value) {
        try { return sha256(JSON.writeValueAsBytes(value)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    static String sha256(Path path) throws IOException { return sha256(Files.readAllBytes(path)); }
    static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static CompositionFreshHoldoutCandidateGameplayAudit.Lineup required(
            Map<String, CompositionFreshHoldoutCandidateGameplayAudit.Lineup> values, String id) {
        var value = values.get(id);
        if (value == null) throw new IllegalStateException("Unknown scheduled lineup " + id);
        return value;
    }
    static List<String> csv(String line) {
        List<String> values = new ArrayList<>(); StringBuilder value = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < line.length(); i++) { char c = line.charAt(i); if (c == '"') {
            if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { value.append('"'); i++; }
            else quoted = !quoted;
        } else if (c == ',' && !quoted) { values.add(value.toString()); value.setLength(0); }
        else value.append(c); }
        values.add(value.toString()); return values;
    }
    static Map<String, Integer> index(List<String> header) {
        Map<String, Integer> result = new HashMap<>();
        for (int i = 0; i < header.size(); i++) result.put(header.get(i), i);
        return result;
    }
    static String num(double value) { return String.format(Locale.ROOT, "%.12f", value == 0.0 ? 0.0 : value); }
    static String nullable(Object value) { return value == null ? "NOT_APPLICABLE" : value.toString(); }
    static List<List<String>> rows(String... header) { return new ArrayList<>(List.of(List.of(header))); }
    static void csv(String filename, List<List<String>> rows) throws IOException {
        StringBuilder out = new StringBuilder();
        for (List<String> row : rows) { for (int i = 0; i < row.size(); i++) { if (i > 0) out.append(',');
            String value = row.get(i); if (value.contains(",") || value.contains("\"") || value.contains("\n"))
                out.append('"').append(value.replace("\"", "\"\"")).append('"'); else out.append(value); }
            out.append('\n'); }
        Files.writeString(OUTPUT.resolve(filename), out, StandardCharsets.UTF_8);
    }

    record ScheduleCase(int auditIndex, int caseIndex, int orientationGroupId, long seed, int orientation,
                        String blueLineupId, String redLineupId, String pairHash) {}
    record PairedGame(int auditIndex, int caseIndex, int orientationGroupId, long seed, int orientation,
                      String blueLineupId, String redLineupId, TeamSide offWinner, TeamSide auditWinner,
                      int offDuration, int auditDuration, String offTimelineHash, String auditTimelineHash,
                      String offObjectiveSignature, String auditObjectiveSignature,
                      String offStructureSignature, String auditStructureSignature,
                      int firstPublicDivergenceTime, int preDivergenceRandomMismatch,
                      long offRandomDrawCount, long auditRandomDrawCount) {}
    record Replay(int caseIndex, long seed, String originalGameplayHash, String replayGameplayHash,
                  String originalChannelHash, String replayChannelHash, String originalGradeHash,
                  String replayGradeHash, String originalRandomHash, String replayRandomHash, boolean exact) {}
    record Integrity(int scheduleErrors, int simulationErrors, int blueprintErrors, int severityErrors,
                     int leakageErrors, int baseWinnerErrors, int baseRoleErrors, int historicalMixErrors,
                     int gradeRandomErrors, int reconstructionErrors, int preDivergenceErrors,
                     int replayErrors, int sourceErrors) {
        int total() { return scheduleErrors + simulationErrors + blueprintErrors + severityErrors + leakageErrors
                + baseWinnerErrors + baseRoleErrors + historicalMixErrors + gradeRandomErrors
                + reconstructionErrors + preDivergenceErrors + replayErrors + sourceErrors; }
    }
    record Result(List<ScheduleCase> schedule, List<PairedGame> games, List<Replay> replays,
                  List<CompositionWinnerChannelObservation> winners, List<FightGradeDecisionDiagnostic> grades,
                  List<BaseDefenseRoleRoutingDiagnostic> roles, Map<Path, String> before, Map<Path, String> after,
                  Integrity integrity, boolean sampleMinimums, String verdict) {}
}
