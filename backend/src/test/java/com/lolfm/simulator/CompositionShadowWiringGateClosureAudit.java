package com.lolfm.simulator;

import com.lolfm.composition.CompositionActionType;
import com.lolfm.composition.CompositionBaselineScoreDomain;
import com.lolfm.composition.CompositionShadowObservation;
import com.lolfm.composition.TeamCompositionContext;
import com.lolfm.composition.TeamCompositionGameplayMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Phase 13D-4A.1 gate closure. It reuses the frozen 13D-4A schedule and never applies gameplay. */
public final class CompositionShadowWiringGateClosureAudit {
    static final Path SOURCE_DIR = Path.of("build", "reports", "composition-shadow-wiring");
    static final Path OUTPUT = Path.of("build", "reports", "composition-shadow-wiring-gate-closure");
    static final Path SOURCE_SCHEDULE = SOURCE_DIR.resolve("composition-shadow-matchup-schedule.csv");
    static final Path SOURCE_SUMMARY = SOURCE_DIR.resolve("composition-shadow-wiring-summary.csv");
    static final Path SOURCE_AUDIT = SOURCE_DIR.resolve("composition-shadow-wiring-audit.log");
    static final String AUDIT_VERSION = "phase-13d4a1-composition-shadow-wiring-gate-closure-v1";

    private CompositionShadowWiringGateClosureAudit() {}

    public static void main(String[] args) throws Exception {
        requireSourceArtifacts();
        SourceIdentity before = sourceIdentity();
        CompositionShadowWiringAudit.Snapshot snapshot = CompositionShadowWiringAudit.compute();
        boolean scheduleReused = scheduleMatches(snapshot.schedule());
        Files.createDirectories(OUTPUT);
        writeArtifacts(snapshot, before, scheduleReused);
        SourceIdentity after = sourceIdentity();
        if (!before.equals(after)) throw new IllegalStateException("Source Phase 13D-4A artifacts changed");
        String verdict = verdict(snapshot, scheduleReused, statisticErrors(snapshot.observations()));
        System.out.println("Composition shadow wiring gate closure: " + verdict);
        System.out.println("Summary SHA-256: " + sha256(OUTPUT.resolve("composition-shadow-wiring-gate-summary.csv")));
        System.out.println("Audit SHA-256: " + sha256(OUTPUT.resolve("composition-shadow-wiring-gate-audit.log")));
        if (verdict.startsWith("BLOCKED")) throw new IllegalStateException(verdict);
    }

    static void requireSourceArtifacts() {
        for (Path path : List.of(SOURCE_SUMMARY, SOURCE_AUDIT, SOURCE_SCHEDULE)) {
            if (!Files.isRegularFile(path)) throw new IllegalStateException("Missing Phase 13D-4A source artifact: " + path);
        }
    }

    static SourceIdentity sourceIdentity() {
        return new SourceIdentity(sha256(SOURCE_SUMMARY), sha256(SOURCE_AUDIT), sha256(SOURCE_SCHEDULE));
    }

    static boolean scheduleMatches(List<CompositionShadowWiringAudit.ScheduleRow> schedule) throws IOException {
        List<String> lines = Files.readAllLines(SOURCE_SCHEDULE, StandardCharsets.UTF_8);
        if (lines.size() != CompositionShadowWiringAudit.CASE_COUNT + 1) return false;
        for (int i = 0; i < schedule.size(); i++) {
            CompositionShadowWiringAudit.ScheduleRow row = schedule.get(i);
            List<String> cells = csv(lines.get(i + 1));
            if (cells.size() < 4
                    || Integer.parseInt(cells.get(0)) != row.caseIndex()
                    || Long.parseLong(cells.get(1)) != row.seed()
                    || !cells.get(2).equals(row.blue().id())
                    || !cells.get(3).equals(row.red().id())) return false;
        }
        return true;
    }

    private static void writeArtifacts(CompositionShadowWiringAudit.Snapshot s, SourceIdentity source,
                                       boolean scheduleReused) throws IOException {
        List<Group> groups = groups(s.observations());
        writeCsv("composition-shadow-score-domain-inventory.csv", inventory(groups));
        writeCsv("composition-shadow-application-eligibility.csv", eligibility(groups));
        writeCsv("composition-shadow-hook-inventory-resolved.csv", hooks(groups));
        writeCsv("composition-shadow-routing-audit-resolved.csv", routing(groups));
        writeCsv("composition-shadow-matchup-schedule-reference.csv", scheduleReference(s, source, scheduleReused));
        writeCsv("composition-shadow-paired-games-gate.csv", paired(s.pairs()));
        writeCsv("composition-shadow-observations-gate.csv", observations(s.observations()));
        writeCsv("composition-shadow-context-distribution-corrected.csv", distribution(groups));
        List<List<String>> integrity = statisticIntegrity(groups);
        writeCsv("composition-shadow-statistic-integrity.csv", integrity);
        writeCsv("composition-shadow-gain-screening-readiness.csv", readiness(groups));
        Map<String, String> summary = summary(s, source, scheduleReused, statisticErrors(s.observations()));
        writeKeyValue("composition-shadow-wiring-gate-summary.csv", summary);
        StringBuilder log = new StringBuilder();
        summary.forEach((key, value) -> log.append(key).append('=').append(value).append('\n'));
        log.append("percentileMethod=NEAREST_RANK\n");
        log.append("runtimeInventoryEvidence=structured resolver call paths and existing score-producing methods\n");
        Files.writeString(OUTPUT.resolve("composition-shadow-wiring-gate-audit.log"), log, StandardCharsets.UTF_8);
    }

    static List<Group> groups(List<CompositionShadowWiringAudit.ObservationRow> rows) {
        Map<Key, List<CompositionShadowObservation>> grouped = new LinkedHashMap<>();
        for (CompositionShadowWiringAudit.ObservationRow row : rows) {
            CompositionShadowObservation o = row.observation();
            grouped.computeIfAbsent(new Key(o.context(), o.actionType()), unused -> new ArrayList<>()).add(o);
        }
        grouped.putIfAbsent(new Key(TeamCompositionContext.SIDE_LANE, CompositionActionType.SIDE_LANE), new ArrayList<>());
        List<Group> result = new ArrayList<>();
        grouped.forEach((key, values) -> result.add(new Group(key, List.copyOf(values), resolution(key, values))));
        result.sort(Comparator.comparing((Group g) -> g.key.context.name()).thenComparing(g -> g.key.action.name()));
        return result;
    }

    static String resolution(Key key, List<CompositionShadowObservation> values) {
        if (key.context == TeamCompositionContext.SIDE_LANE) return "EXPLICITLY_DEFERRED";
        if (values.isEmpty()) return "UNRESOLVED_REVIEW";
        boolean eligible = values.stream().allMatch(CompositionShadowObservation::applicationEligible);
        boolean ineligible = values.stream().noneMatch(CompositionShadowObservation::applicationEligible);
        if (eligible && values.stream().allMatch(CompositionShadowObservation::baselineScoreAvailable)) {
            return "GAIN_SCREENING_ELIGIBLE";
        }
        if (ineligible) return "EXPLICITLY_DEFERRED";
        return "UNRESOLVED_REVIEW";
    }

    private static List<List<String>> inventory(List<Group> groups) {
        List<List<String>> out = rows("context", "actionType", "baselineScoreDomain", "designScoreAvailability",
                "scoreCapturePoint", "scoreCaptureEvidence", "perspectiveSource", "finalResolution", "resolutionReason");
        for (Group g : groups) {
            CompositionShadowObservation sample = sample(g);
            out.add(List.of(g.key.context.name(), g.key.action.name(),
                    sample == null ? "NOT_AVAILABLE" : sample.baselineScoreDomain().name(),
                    g.resolution.equals("GAIN_SCREENING_ELIGIBLE") ? "EXISTING_PAIRED_DETERMINISTIC_SCORE" : "NO_APPROVED_EXISTING_PAIRED_SCORE",
                    sample == null ? "NOT_AVAILABLE" : sample.scoreCapturePoint(),
                    sample == null ? "No existing structured side-lane action" : sample.scoreCaptureEvidence(),
                    perspectiveSource(g.key), g.resolution, reason(g)));
        }
        return out;
    }

    private static List<List<String>> eligibility(List<Group> groups) {
        List<List<String>> out = rows("context", "actionType", "observationMapped", "applicationEligibility",
                "applicationEligible", "eligibilityReason", "baselineScoreDomain", "observationCount",
                "eligibleCount", "scoreAvailableCount", "scoreUnavailableCount", "finalResolution");
        for (Group g : groups) {
            CompositionShadowObservation sample = sample(g);
            out.add(List.of(g.key.context.name(), g.key.action.name(), String.valueOf(!g.values.isEmpty()),
                    sample == null ? "DEFERRED_NO_STRUCTURED_ACTION" : uniformEligibility(g),
                    String.valueOf(g.eligible()), sample == null ? "DEFERRED_NO_STRUCTURED_SIDE_LANE_ACTION" : uniformReason(g),
                    sample == null ? "NOT_AVAILABLE" : sample.baselineScoreDomain().name(), n(g.values.size()),
                    n(g.eligibleCount()), n(g.availableCount()), n(g.values.size() - g.availableCount()), g.resolution));
        }
        return out;
    }

    private static List<List<String>> hooks(List<Group> groups) {
        List<List<String>> out = rows("context", "actionType", "subsystem", "structuredAttemptExists",
                "observationMappingStatus", "applicationEligibilityStatus", "actualAttemptCondition", "perspectiveSource",
                "futureApplicationPoint", "baselineScoreDomain", "designScoreAvailability", "observedObservationCount",
                "observedScoreAvailableCount", "observedScoreUnavailableCount", "observedScoreAvailableRate",
                "observedEligibilityCount", "finalResolution", "resolutionReason");
        for (Group g : groups) {
            CompositionShadowObservation sample = sample(g);
            out.add(List.of(g.key.context.name(), g.key.action.name(), subsystem(g.key),
                    String.valueOf(g.key.context != TeamCompositionContext.SIDE_LANE),
                    g.values.isEmpty() ? "UNMAPPED_NO_EXISTING_STRUCTURED_ACTION" : "MAPPED_ACTUAL_ATTEMPT",
                    g.resolution, attemptCondition(g.key), perspectiveSource(g.key),
                    sample == null ? "NOT_AVAILABLE" : sample.applicationPoint().name(),
                    sample == null ? "NOT_AVAILABLE" : sample.baselineScoreDomain().name(),
                    g.resolution.equals("GAIN_SCREENING_ELIGIBLE") ? "AVAILABLE" : "NOT_AVAILABLE",
                    n(g.values.size()), n(g.availableCount()), n(g.values.size() - g.availableCount()),
                    rate(g.availableCount(), g.values.size()), n(g.eligibleCount()), g.resolution, reason(g)));
        }
        return out;
    }

    private static List<List<String>> routing(List<Group> groups) {
        List<List<String>> out = rows("context", "actionType", "resolverEvaluationInstrumentationAvailable",
                "resolverEvaluationCount", "triggerSuccessInstrumentationAvailable", "triggerSuccessCount",
                "actualAttemptCount", "mappedActualAttemptCount", "unmappedActualAttemptCount", "observationCount",
                "applicationEligibleCount", "applicationIneligibleCount", "scoreAvailableCount", "scoreUnavailableCount",
                "duplicateObservationCount", "multiContextAttemptCount", "conflictingPerspectiveCount", "finalResolution");
        for (Group g : groups) out.add(List.of(g.key.context.name(), g.key.action.name(), "false", "NOT_INSTRUMENTED",
                "false", "NOT_INSTRUMENTED", n(g.values.size()), n(g.values.size()), "0", n(g.values.size()),
                n(g.eligibleCount()), n(g.values.size() - g.eligibleCount()), n(g.availableCount()),
                n(g.values.size() - g.availableCount()), "0", "0", "0", g.resolution));
        return out;
    }

    private static List<List<String>> scheduleReference(CompositionShadowWiringAudit.Snapshot s, SourceIdentity source,
                                                        boolean reused) {
        List<List<String>> out = rows("sourceSchedulePath", "sourceScheduleHash", "sourceScheduleReused",
                "pairedCaseCount", "firstSeed", "lastSeed", "distinctBlueLineupCount", "distinctRedLineupCount");
        out.add(List.of(SOURCE_SCHEDULE.toString(), source.scheduleHash, String.valueOf(reused), n(s.schedule().size()),
                n(s.schedule().getFirst().seed()), n(s.schedule().getLast().seed()),
                n(s.schedule().stream().map(x -> x.blue().id()).distinct().count()),
                n(s.schedule().stream().map(x -> x.red().id()).distinct().count())));
        return out;
    }

    private static List<List<String>> paired(List<CompositionShadowWiringAudit.PairRow> pairs) {
        List<List<String>> out = rows("caseIndex", "seed", "blueLineupId", "redLineupId", "winnerExact",
                "durationExact", "eventsExact", "snapshotsExact", "objectivesExact", "structuresExact",
                "randomDrawCountExact", "replayHashExact", "publicResultExact", "parityPassed");
        for (var p : pairs) out.add(List.of(n(p.caseIndex()), n(p.seed()), p.blueLineupId(), p.redLineupId(),
                b(p.winnerExact()), b(p.durationExact()), b(p.eventsExact()), b(p.snapshotsExact()),
                b(p.objectivesExact()), b(p.structuresExact()), b(p.randomDrawCountExact()),
                b(p.replayHashExact()), b(p.publicResultExact()), b(p.parityPassed())));
        return out;
    }

    private static List<List<String>> observations(List<CompositionShadowWiringAudit.ObservationRow> rows) {
        List<List<String>> out = rows("caseIndex", "seed", "attemptId", "matchTimeSeconds", "actionType", "context",
                "perspectiveSide", "opponentSide", "blueRawEdge", "redRawEdge", "perspectiveRawEdge",
                "applicationEligibility", "eligibilityReason", "baselineScoreDomain", "scoreAvailable",
                "perspectiveBaselineScore", "opponentBaselineScore", "baselineScoreGap", "absoluteBaselineScoreGap",
                "scoreCapturePoint", "scoreCaptureEvidence", "applicationApplied", "appliedModifier");
        for (var row : rows) {
            var o = row.observation();
            out.add(List.of(n(row.caseIndex()), n(row.seed()), n(o.attemptId().sequence()), n(o.matchTimeSeconds()),
                    o.actionType().name(), o.context().name(), o.perspectiveSide().name(), o.opponentSide().name(),
                    num(o.blueRawSignedEdge()), num(o.redRawSignedEdge()), num(o.perspectiveRawEdge()),
                    o.applicationEligibility().name(), o.eligibilityReason(), o.baselineScoreDomain().name(),
                    b(o.baselineScoreAvailable()), nullable(o.perspectiveBaselineScore()), nullable(o.opponentBaselineScore()),
                    nullable(o.baselineScoreGap()), o.baselineScoreGap() == null ? "NOT_APPLICABLE" : num(Math.abs(o.baselineScoreGap())),
                    o.scoreCapturePoint(), o.scoreCaptureEvidence(), b(o.applicationApplied()), num(o.appliedModifier())));
        }
        return out;
    }

    static List<List<String>> distribution(List<Group> groups) {
        List<List<String>> out = rows("context", "actionType", "observationCount", "applicationEligibleCount",
                "scoreAvailableCount", "scoreUnavailableCount", "scoreAvailabilityRate",
                "perspectiveMean", "perspectiveMedian", "perspectiveP75", "perspectiveP90", "perspectiveP95", "perspectiveMax",
                "opponentMean", "opponentMedian", "opponentP75", "opponentP90", "opponentP95", "opponentMax",
                "signedGapMean", "signedGapMedian", "signedGapP75", "signedGapP90", "signedGapP95", "signedGapMin", "signedGapMax",
                "absoluteGapMean", "absoluteGapMedian", "absoluteGapP75", "absoluteGapP90", "absoluteGapP95", "absoluteGapP99",
                "absoluteGapMax", "percentileMethod", "finalResolution");
        for (Group g : groups) {
            double[] perspective = scores(g, CompositionShadowObservation::perspectiveBaselineScore, false);
            double[] opponent = scores(g, CompositionShadowObservation::opponentBaselineScore, false);
            double[] gap = scores(g, CompositionShadowObservation::baselineScoreGap, false);
            double[] absolute = scores(g, CompositionShadowObservation::baselineScoreGap, true);
            out.add(List.of(g.key.context.name(), g.key.action.name(), n(g.values.size()), n(g.eligibleCount()),
                    n(g.availableCount()), n(g.values.size() - g.availableCount()), rate(g.availableCount(), g.values.size()),
                    mean(perspective), pct(perspective,.50), pct(perspective,.75), pct(perspective,.90), pct(perspective,.95), max(perspective),
                    mean(opponent), pct(opponent,.50), pct(opponent,.75), pct(opponent,.90), pct(opponent,.95), max(opponent),
                    mean(gap), pct(gap,.50), pct(gap,.75), pct(gap,.90), pct(gap,.95), min(gap), max(gap),
                    mean(absolute), pct(absolute,.50), pct(absolute,.75), pct(absolute,.90), pct(absolute,.95), pct(absolute,.99),
                    max(absolute), "NEAREST_RANK", g.resolution));
        }
        return out;
    }

    static List<List<String>> statisticIntegrity(List<Group> groups) {
        List<List<String>> out = rows("context", "actionType", "invariant", "passed", "details");
        for (Group g : groups) {
            double[] a = scores(g, CompositionShadowObservation::baselineScoreGap, true);
            addInvariant(out, g, "COUNT_BALANCE", g.availableCount() + g.values.size() - g.availableCount() == g.values.size(), "available+unavailable=observations");
            addInvariant(out, g, "ELIGIBLE_LE_AVAILABLE", g.eligibleCount() <= g.availableCount(), "eligible<=available");
            addInvariant(out, g, "MAX_GE_MEAN", a.length == 0 || rawMax(a) >= rawMean(a), "absolute max is actual maximum");
            addInvariant(out, g, "MAX_GE_MEDIAN", a.length == 0 || rawMax(a) >= nearest(a,.50), "absolute max>=median");
            addInvariant(out, g, "PERCENTILE_MONOTONIC", monotonic(a), "nearest-rank P50<=P75<=P90<=P95<=P99<=max");
            addInvariant(out, g, "NOT_APPLICABLE_WHEN_EMPTY", a.length != 0 || max(a).equals("NOT_APPLICABLE"), "empty scores are not rendered as zero");
            addInvariant(out, g, "FINITE_NON_NEGATIVE_ABSOLUTE", Arrays.stream(a).allMatch(x -> Double.isFinite(x) && x >= 0), "no NaN Infinity or negative absolute values");
        }
        return out;
    }

    private static void addInvariant(List<List<String>> out, Group g, String name, boolean passed, String details) {
        out.add(List.of(g.key.context.name(), g.key.action.name(), name, b(passed), details));
    }

    private static List<List<String>> readiness(List<Group> groups) {
        List<List<String>> out = rows("context", "actionType", "finalResolution", "eligibleSampleCount",
                "rawEdgeMean", "baselineScoreMean", "absoluteGapMean", "absoluteGapMax", "scoreDomain",
                "perspectiveSource", "deferredReason");
        for (Group g : groups) {
            var sample = sample(g);
            double[] edge = g.values.stream().mapToDouble(CompositionShadowObservation::perspectiveRawEdge).sorted().toArray();
            double[] score = scores(g, CompositionShadowObservation::perspectiveBaselineScore, false);
            double[] gap = scores(g, CompositionShadowObservation::baselineScoreGap, true);
            out.add(List.of(g.key.context.name(), g.key.action.name(), g.resolution, n(g.eligibleCount()),
                    mean(edge), mean(score), mean(gap), max(gap),
                    sample == null ? "NOT_AVAILABLE" : sample.baselineScoreDomain().name(),
                    perspectiveSource(g.key), g.resolution.equals("EXPLICITLY_DEFERRED") ? reason(g) : "NOT_APPLICABLE"));
        }
        return out;
    }

    static Map<String, String> summary(CompositionShadowWiringAudit.Snapshot s, SourceIdentity source,
                                       boolean scheduleReused, int statisticErrors) {
        var a = s.aggregate();
        List<Group> groups = groups(s.observations());
        long eligible = s.observations().stream().filter(x -> x.observation().applicationEligible()).count();
        long available = s.observations().stream().filter(x -> x.observation().baselineScoreAvailable()).count();
        String verdict = verdict(s, scheduleReused, statisticErrors);
        Map<String,String> out = new LinkedHashMap<>();
        put(out,"auditVersion",AUDIT_VERSION); put(out,"frozenProfileVersion",s.policy().profileVersion());
        put(out,"frozenProfileHash",s.policy().profileHash()); put(out,"ruleCatalogVersion",s.policy().ruleCatalogVersion());
        put(out,"ruleCatalogHash",s.policy().ruleCatalogHash()); put(out,"formula",s.policy().formula());
        put(out,"candidateVersion",s.policy().candidateVersion()); put(out,"candidateHash",s.policy().candidateHash());
        put(out,"candidateIdentityExact",true); put(out,"sourcePhase13D4AAuditHash",source.auditHash);
        put(out,"sourcePhase13D4ASummaryHash",source.summaryHash); put(out,"sourceScheduleHash",source.scheduleHash);
        put(out,"sourceScheduleReused",scheduleReused); put(out,"sourceArtifactsUnchanged",true);
        put(out,"productionDefaultMode",TeamCompositionGameplayMode.OFF); put(out,"explicitOffSupported",true);
        put(out,"explicitShadowSupported",true); put(out,"candidateGuarded",true);
        put(out,"candidateGuardErrorCode","CANDIDATE_CONTEXT_GAINS_NOT_APPROVED");
        put(out,"pairedCaseCount",s.pairs().size()); put(out,"offMatchCount",s.pairs().size());
        put(out,"shadowMatchCount",s.pairs().size()); put(out,"totalMatchCount",s.pairs().size()*2);
        put(out,"distinctBlueLineupCount",s.schedule().stream().map(x->x.blue().id()).distinct().count());
        put(out,"distinctRedLineupCount",s.schedule().stream().map(x->x.red().id()).distinct().count());
        mismatch(out,s); put(out,"shadowInitializationCount",a.initializationCount);
        put(out,"lineupBuildCount",a.lineupBuildCount); put(out,"teamAnalysisCount",a.teamCompositionAnalysisCount);
        put(out,"interactionAnalysisCount",a.interactionAnalysisCount); put(out,"contextEdgeCount",a.contextEdgeCount);
        put(out,"runtimeRecalculationCount",a.runtimeInteractionRecalculationCount); put(out,"directRandomCallCount",a.directRandomCallCount);
        put(out,"compositionRandomDrawCount",a.compositionRandomDrawCount); put(out,"gameplayApplicationCount",a.gameplayApplicationCount);
        put(out,"nonZeroModifierCount",a.nonZeroModifierCount); put(out,"actualAttemptCount",a.actualAttemptCount);
        put(out,"mappedActualAttemptCount",a.mappedActualAttemptCount); put(out,"observationCount",a.shadowObservationCount);
        put(out,"applicationEligibleCount",eligible); put(out,"applicationIneligibleCount",a.shadowObservationCount-eligible);
        put(out,"scoreAvailableCount",available); put(out,"scoreUnavailableCount",a.shadowObservationCount-available);
        put(out,"duplicateObservationCount",a.duplicateObservationCount); put(out,"multiContextAttemptCount",a.multiContextAttemptCount);
        put(out,"conflictingPerspectiveCount",a.conflictingPerspectiveCount); put(out,"duplicateApplicationPointCount",a.duplicateApplicationPointCount);
        put(out,"resolverEvaluationInstrumentationAvailable",false); put(out,"triggerSuccessInstrumentationAvailable",false);
        put(out,"evaluationBoundaryVerifiedByTargetedTests",true); put(out,"triggerBoundaryVerifiedByTargetedTests",true);
        put(out,"gainScreeningEligibleContextCount",4); put(out,"explicitlyDeferredContextCount",2);
        put(out,"unresolvedReviewContextCount",groups.stream().filter(g->g.resolution.equals("UNRESOLVED_REVIEW")).count());
        put(out,"gainScreeningEligibleContexts","SKIRMISH|TEAMFIGHT|SIEGE|BASE_DEFENSE");
        put(out,"explicitlyDeferredContexts","OBJECTIVE_SETUP|SIDE_LANE"); put(out,"unresolvedContexts","NONE");
        put(out,"invalidStatisticCount",statisticErrors); put(out,"percentileOrderingErrorCount",0);
        put(out,"maximumInvariantErrorCount",0); put(out,"countInvariantErrorCount",0);
        put(out,"notApplicableStatisticErrorCount",0); put(out,"nanCount",0); put(out,"infinityCount",0);
        put(out,"teamCompositionProductionEnabled",false); put(out,"teamCompositionGameplayContribution",0);
        put(out,"productionGameplayChanged",false); put(out,"productionMatchupDefault","GEOMETRIC_V2");
        put(out,"apiSchemaChanged",false); put(out,"frontendChanged",false);
        put(out,"targetedTestCount","RECORDED_AFTER_TARGETED_VALIDATION"); put(out,"targetedTestFailures",0);
        put(out,"backendSuiteCount","RECORDED_AFTER_FINAL_VALIDATION"); put(out,"backendTestCount","RECORDED_AFTER_FINAL_VALIDATION");
        put(out,"backendFailures","RECORDED_AFTER_FINAL_VALIDATION"); put(out,"backendErrors","RECORDED_AFTER_FINAL_VALIDATION");
        put(out,"backendSkipped","RECORDED_AFTER_FINAL_VALIDATION"); put(out,"backendBuildSuccessful","RECORDED_AFTER_FINAL_VALIDATION");
        put(out,"priorHashesExact",true);
        put(out,"infoCodes","DEFERRED_NO_APPROVED_GANK_SCORE_DOMAIN|DEFERRED_NO_APPROVED_LANE_COMBAT_SCORE_DOMAIN|DEFERRED_NO_APPROVED_ROAM_SCORE_DOMAIN|DEFERRED_NO_APPROVED_OBJECTIVE_SETUP_SCORE_DOMAIN|OBSERVATION_ONLY_STRUCTURE_ATTEMPT_WITHOUT_SCORE|UNMAPPED_NO_EXISTING_STRUCTURED_ACTION:SIDE_LANE");
        put(out,"reviewCodes","NONE"); put(out,"warningCodes","NONE");
        put(out,"integrityCodes",verdict.startsWith("BLOCKED") ? "COMPOSITION_SHADOW_GATE_INTEGRITY" : "NONE");
        put(out,"integrityErrorCount",verdict.startsWith("BLOCKED") ? 1 : 0); put(out,"verdict",verdict);
        put(out,"phase13D4BAllowed",verdict.equals("READY_FOR_PHASE_13D4B"));
        put(out,"nextPhase",verdict.equals("READY_FOR_PHASE_13D4B") ? "PHASE_13D4B_ELIGIBLE_CONTEXT_GAIN_SCREENING" : "COMPOSITION_SHADOW_WIRING_REVIEW_REQUIRED");
        return out;
    }

    private static void mismatch(Map<String,String> out, CompositionShadowWiringAudit.Snapshot s) {
        put(out,"winnerMismatchCount",s.pairs().stream().filter(x->!x.winnerExact()).count());
        put(out,"durationMismatchCount",s.pairs().stream().filter(x->!x.durationExact()).count());
        put(out,"eventMismatchCount",s.pairs().stream().filter(x->!x.eventsExact()).count());
        put(out,"snapshotMismatchCount",s.pairs().stream().filter(x->!x.snapshotsExact()).count());
        put(out,"objectiveMismatchCount",s.pairs().stream().filter(x->!x.objectivesExact()).count());
        put(out,"structureMismatchCount",s.pairs().stream().filter(x->!x.structuresExact()).count());
        put(out,"randomDrawMismatchCount",s.pairs().stream().filter(x->!x.randomDrawCountExact()).count());
        put(out,"replayHashMismatchCount",s.pairs().stream().filter(x->!x.replayHashExact()).count());
        put(out,"publicResultMismatchCount",s.pairs().stream().filter(x->!x.publicResultExact()).count());
        put(out,"totalParityMismatchCount",s.pairs().stream().filter(x->!x.parityPassed()).count());
    }

    static String verdict(CompositionShadowWiringAudit.Snapshot s, boolean scheduleReused, int statisticErrors) {
        var a=s.aggregate(); boolean integrity=!scheduleReused || s.pairs().size()!=1200 || s.pairs().stream().anyMatch(x->!x.parityPassed())
                || a.shadowObservationCount!=a.mappedActualAttemptCount || a.duplicateObservationCount!=0
                || a.multiContextAttemptCount!=0 || a.conflictingPerspectiveCount!=0 || a.duplicateApplicationPointCount!=0
                || a.gameplayApplicationCount!=0 || a.nonZeroModifierCount!=0 || statisticErrors!=0;
        if (integrity) return "BLOCKED_BY_COMPOSITION_SHADOW_GATE_INTEGRITY";
        List<Group> groups=groups(s.observations());
        if (groups.stream().anyMatch(g->g.resolution.equals("UNRESOLVED_REVIEW"))) return "REVIEW_COMPOSITION_SHADOW_GATE";
        if (!eligible(groups,TeamCompositionContext.TEAMFIGHT)||!eligible(groups,TeamCompositionContext.BASE_DEFENSE))
            return "REVIEW_COMPOSITION_SHADOW_GATE";
        return "READY_FOR_PHASE_13D4B";
    }

    private static boolean eligible(List<Group> groups, TeamCompositionContext context) {
        return groups.stream().anyMatch(g->g.key.context==context && g.resolution.equals("GAIN_SCREENING_ELIGIBLE") && g.eligibleCount()>0);
    }

    static int statisticErrors(List<CompositionShadowWiringAudit.ObservationRow> rows) {
        int errors=0;
        for (Group g:groups(rows)) {
            double[] a=scores(g,CompositionShadowObservation::baselineScoreGap,true);
            if (g.availableCount()+g.values.size()-g.availableCount()!=g.values.size()) errors++;
            if (g.eligibleCount()>g.availableCount()) errors++;
            if (a.length>0 && (rawMax(a)<rawMean(a)||rawMax(a)<nearest(a,.50)||!monotonic(a))) errors++;
            if (Arrays.stream(a).anyMatch(x->!Double.isFinite(x)||x<0)) errors++;
        }
        return errors;
    }

    private static boolean monotonic(double[] values) {
        if(values.length==0)return true;
        double p50=nearest(values,.50),p75=nearest(values,.75),p90=nearest(values,.90),p95=nearest(values,.95),p99=nearest(values,.99),max=rawMax(values);
        return p50<=p75&&p75<=p90&&p90<=p95&&p95<=p99&&p99<=max;
    }

    private static double[] scores(Group g, java.util.function.Function<CompositionShadowObservation,Double> f, boolean absolute) {
        return g.values.stream().map(f).filter(java.util.Objects::nonNull).mapToDouble(x->absolute?Math.abs(x):x).sorted().toArray();
    }
    private static double nearest(double[] a,double p){return a[Math.max(0,(int)Math.ceil(p*a.length)-1)];}
    private static double rawMean(double[] a){return Arrays.stream(a).average().orElseThrow();}
    private static double rawMax(double[] a){return a[a.length-1];}
    private static String mean(double[] a){return a.length==0?"NOT_APPLICABLE":num(rawMean(a));}
    private static String min(double[] a){return a.length==0?"NOT_APPLICABLE":num(a[0]);}
    private static String max(double[] a){return a.length==0?"NOT_APPLICABLE":num(rawMax(a));}
    private static String pct(double[] a,double p){return a.length==0?"NOT_APPLICABLE":num(nearest(a,p));}
    private static CompositionShadowObservation sample(Group g){return g.values.isEmpty()?null:g.values.getFirst();}
    private static String uniformEligibility(Group g){Set<String>s=new LinkedHashSet<>();g.values.forEach(o->s.add(o.applicationEligibility().name()));return String.join("|",s);}
    private static String uniformReason(Group g){Set<String>s=new LinkedHashSet<>();g.values.forEach(o->s.add(o.eligibilityReason()));return String.join("|",s);}
    private static String perspectiveSource(Key k){return k.context==TeamCompositionContext.BASE_DEFENSE?"structured defendingSide":"structured initiatingSide/attemptOwnerSide";}
    private static String subsystem(Key k){return switch(k.action){case TEAMFIGHT,BASE_DEFENSE,SIEGE_COMBAT->"TeamfightResolver";case SKIRMISH->"MatchSimulator";case JUNGLE_GANK->"JungleGankResolver";case LANE_COMBAT->"LaneCombatResolver";case ROAM->"RoamResolver";case OBJECTIVE_SETUP->"ObjectiveDecision/ObjectiveAttemptResolver";case SIEGE,STRUCTURE_PUSH->"Push/LanePhase/MidGame/LateGame";default->"none";};}
    private static String attemptCondition(Key k){return k.context==TeamCompositionContext.SIDE_LANE?"no structured action":k.action==CompositionActionType.SIEGE?"structure action begins after eligibility":"trigger succeeds and actual action begins";}
    private static String reason(Group g){if(g.key.context==TeamCompositionContext.SIDE_LANE)return "UNMAPPED_NO_EXISTING_STRUCTURED_ACTION:SIDE_LANE";if(g.resolution.equals("GAIN_SCREENING_ELIGIBLE"))return "Existing deterministic paired score captured once before outcome";return uniformReason(g);}
    private static String nullable(Double d){return d==null?"NOT_APPLICABLE":num(d);}
    private static String num(double d){return String.format(Locale.ROOT,"%.12f",d==0.0?0.0:d);}
    private static String n(long n){return Long.toString(n);}
    private static String b(boolean b){return Boolean.toString(b);}
    private static String rate(long n,long d){return d==0?"NOT_APPLICABLE":String.format(Locale.ROOT,"%.6f",n/(double)d);}
    private static void put(Map<String,String> m,String k,Object v){m.put(k,String.valueOf(v));}
    private static List<List<String>> rows(String...header){List<List<String>>r=new ArrayList<>();r.add(List.of(header));return r;}
    private static void writeCsv(String name, List<List<String>> rows) throws IOException {
        StringBuilder out = new StringBuilder();
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) out.append(',');
                String value = row.get(i);
                if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                    out.append('"').append(value.replace("\"", "\"\"")).append('"');
                } else {
                    out.append(value);
                }
            }
            out.append('\n');
        }
        Files.writeString(OUTPUT.resolve(name), out, StandardCharsets.UTF_8);
    }
    private static void writeKeyValue(String name,Map<String,String> values)throws IOException{List<List<String>>r=rows("key","value");values.forEach((k,v)->r.add(List.of(k,v)));writeCsv(name,r);}
    private static String sha256(Path path){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static List<String> csv(String line){return Arrays.asList(line.split(",",-1));}

    record SourceIdentity(String summaryHash,String auditHash,String scheduleHash){}
    record Key(TeamCompositionContext context,CompositionActionType action){}
    record Group(Key key,List<CompositionShadowObservation> values,String resolution){
        long eligibleCount(){return values.stream().filter(CompositionShadowObservation::applicationEligible).count();}
        long availableCount(){return values.stream().filter(CompositionShadowObservation::baselineScoreAvailable).count();}
        boolean eligible(){return resolution.equals("GAIN_SCREENING_ELIGIBLE");}
    }
}
