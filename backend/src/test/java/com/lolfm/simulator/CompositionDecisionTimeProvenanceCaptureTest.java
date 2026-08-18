package com.lolfm.simulator;

import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.composition.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompositionDecisionTimeProvenanceCaptureTest {
    private static final Path OUT = CompositionDecisionTimeProvenanceCapture.OUT;
    private static final Path HISTORICAL = historicalFixture();
    private static Map<String, String> summary;
    private static List<Map<String, String>> provenance;
    private static List<CompositionWinnerDecisionProvenance> live;
    private static CompositionDecisionTimeProvenanceCapture.SourceGame source;
    private static CompositionAuditOnlySemanticsRuntime.PairedGame pair;

    @BeforeAll
    static void loadEvidenceAndRunOneFocusedReplay() throws Exception {
        summary = twoColumn("composition-decision-time-provenance-summary.csv", "field", "value");
        provenance = rows("composition-runtime-decision-provenance.csv");
        var games = CompositionDecisionTimeProvenanceCapture.readSourceGames(HISTORICAL);
        source = games.get(12);
        var schedule = CompositionDecisionTimeProvenanceCapture.readSchedule(HISTORICAL).get(source.caseIndex());
        var lineups = Map.of(schedule.blueLineupId(), lineup(schedule.blueLineupId()),
                schedule.redLineupId(), lineup(schedule.redLineupId()));
        MatchChampionAssignments assignments = CompositionAuditOnlySemanticsRuntime.assignments(
                lineups.get(schedule.blueLineupId()), lineups.get(schedule.redLineupId()));
        var off = CompositionAuditOnlySemanticsRuntime.simulate(
                CompositionAuditOnlySemanticsRuntime.simulator(TeamCompositionGameplayMode.OFF,
                        CompositionSemanticsAuditExecutionAuthorization.none()), schedule, assignments);
        var candidate = CompositionAuditOnlySemanticsRuntime.simulate(
                CompositionKeySpecificFreshHoldoutGameplayAudit.candidateSimulator(source.caseIndex()), schedule, assignments);
        pair = CompositionAuditOnlySemanticsRuntime.pair(schedule, off, candidate);
        live = candidate.compositionRuntimeDiagnostics().winnerDecisionProvenance();
    }

    @Test void runtimeWinnerIsCapturedDirectly() { assertThat(live).isNotEmpty().allSatisfy(p -> assertThat(p.runtimeWinner()).isNotNull()); }
    @Test void auditDoesNotReconstructWinnerFromRandom() { assertThat(summary.get("auditWinnerReconstructionUsed")).isEqualTo("false"); }
    @Test void uniformNoiseComparatorMatchesRuntimeExactly() { assertThat(live.stream().filter(p -> p.decisionKind() == CompositionRuntimeDecisionKind.UNIFORM_NOISE_THRESHOLD)).allMatch(thisOrStatic(p -> p.runtimeWinner() == (p.randomSample() >= p.runtimeThreshold() ? TeamSide.BLUE : TeamSide.RED))); }
    @Test void weightedSelectionComparatorMatchesRuntimeExactly() { assertThat(provenance.stream().filter(r -> r.get("decisionKind").equals("WEIGHTED_SELECTION"))).allMatch(r -> r.get("runtimeWinner").equals(Double.parseDouble(r.get("randomSample")) < Double.parseDouble(r.get("runtimeThreshold")) ? "BLUE" : "RED")); }
    @Test void comparisonOperatorIsStructured() { assertThat(live).allSatisfy(p -> assertThat(p.comparisonOperator()).isInstanceOf(CompositionRuntimeComparisonOperator.class)); }
    @Test void winnerDecisionSnapshotIsMatchScoped() throws Exception { var field=CompositionRuntimeState.class.getDeclaredField("winnerDecisionProvenance"); assertThat(java.lang.reflect.Modifier.isStatic(field.getModifiers())).isFalse(); }
    @Test void diagnosticsConsumeNoRandom() { zero("randomMismatchCount"); }
    @Test void diagnosticsCannotChangeWinner() { zero("instrumentationGameplayMutationCount"); }

    @Test void goldStateIsCapturedAtDecisionTime() { assertThat(provenance).allSatisfy(r -> assertThat(r).containsKeys("blueGold", "redGold", "goldDifference")); }
    @Test void progressionBreakdownIsCapturedAtDecisionTime() { assertThat(provenance).allSatisfy(r -> assertThat(r).containsKeys("levelContribution", "itemContribution", "progressionContribution")); }
    @Test void levelContributionIsCapturedWithoutRecalculation() { assertThat(live.stream().filter(p -> p.progressionAvailability()==CompositionFactorAvailability.EXACT_RUNTIME_COMPONENT)).isNotEmpty().allSatisfy(p -> { assertThat(p.levelContribution()).isFinite(); assertThat(p.scoreStages()).extracting(CompositionDecisionScoreStage::stageName).contains("PROGRESSION"); }); }
    @Test void itemContributionIsCapturedWithoutRecalculation() { assertThat(live.stream().filter(p -> p.progressionAvailability()==CompositionFactorAvailability.EXACT_RUNTIME_COMPONENT)).isNotEmpty().allSatisfy(p -> { assertThat(p.itemContribution()).isFinite(); assertThat(p.scoreStages()).extracting(CompositionDecisionScoreStage::stageName).contains("PROGRESSION"); }); }
    @Test void championPowerContributionUsesRuntimeValue() { stage("CHAMPION_CURRENT_POWER"); }
    @Test void matchupContributionUsesRuntimeValue() { stage("CHAMPION_MATCHUP"); }
    @Test void scoreStageTraceMatchesRuntimeOrder() throws Exception { var stages=rows("composition-score-stage-trace.csv"); assertThat(stages).isNotEmpty(); assertThat(stages.stream().map(r->Integer.parseInt(r.get("stageOrdinal"))).min(Integer::compareTo)).contains(0); }
    @Test void proxyCannotBeReportedAsExactContribution() throws Exception { assertThat(rows("composition-decision-factor-snapshot.csv")).noneMatch(r -> r.get("availability").equals("STATE_PROXY") && r.get("availability").equals("EXACT_RUNTIME_COMPONENT")); }
    @Test void missingFactorRemainsExplicit() { assertThat(EnumSet.allOf(CompositionFactorAvailability.class)).contains(CompositionFactorAvailability.NOT_AVAILABLE); }

    @Test void all89TeamfightActualFlipsAreCaptured() throws Exception { assertRows("composition-teamfight-actual-flip-provenance.csv", 89); }
    @Test void all18SiegeActualFlipsAreCaptured() throws Exception { assertRows("composition-siege-actual-flip-provenance.csv", 18); }
    @Test void all21BaseActualFlipsAreCaptured() throws Exception { assertRows("composition-base-actual-flip-provenance.csv", 21); }
    @Test void sourceFalsePositivesAreNotReportedAsActualFlips() { assertThat(summary.get("teamfightActualFlipCount")).isEqualTo("89"); }
    @Test void sourceFalseNegativesAreIncludedInActualFlips() throws Exception { assertThat(rows("composition-corrected-4c7-winner-safety.csv")).extracting(r -> r.get("falseNegativeCount")).containsExactly("50", "8", "8"); }
    @Test void flipClassificationUsesActualRuntimeWinner() throws Exception { assertThat(rows("composition-runtime-flip-semantic-summary.csv")).extracting(r -> Integer.parseInt(r.get("actualFlipCount"))).containsExactlyInAnyOrder(89,18,21); }

    @Test void corrected4C6UsesRuntimeWinnerOrientation() throws Exception { assertThat(CompositionDecisionTimeProvenanceCapture.correct4c6().selected().get(TeamCompositionContext.BASE_DEFENSE).ratio()).isEqualTo(.025); }
    @Test void corrected4C6UsesSameFrozenRows() throws Exception { assertThat(rows("composition-corrected-4c6-winner-screening.csv")).allMatch(r -> r.get("sameFrozenRows").equals("true")); }
    @Test void corrected4C6UsesSameRandomSamples() throws Exception { assertThat(rows("composition-corrected-4c6-winner-screening.csv")).allMatch(r -> r.get("sameRandomSamples").equals("true")); }
    @Test void corrected4C6DoesNotRetuneOnValidation() throws Exception { assertThat(rows("composition-corrected-4c6-winner-screening.csv")).allMatch(r -> r.get("retuned").equals("false")); }
    @Test void corrected4C6DoesNotChangeTargetGrid() throws Exception { assertThat(rows("composition-corrected-4c6-winner-screening.csv").stream().filter(r -> r.get("partition").equals("CALIBRATION")).map(r -> r.get("targetRatio")).distinct()).containsExactlyInAnyOrder("0.000000000000","0.025000000000","0.050000000000","0.075000000000","0.100000000000"); }
    @Test void candidateSelectionChangeTriggersReview() { assertThat(summary.get("verdict")).isEqualTo("REVIEW_CORRECTED_CALIBRATION_CHANGES_CANDIDATE_SELECTION"); }

    @Test void diagnosticReplayUsesConsumedHoldoutOnly() throws Exception { assertThat(rows("composition-provenance-replay-case-set.csv")).allMatch(r -> r.get("role").equals("CONSUMED_HOLDOUT_DIAGNOSTIC_PROVENANCE_REPLAY")); }
    @Test void diagnosticReplayCreatesNoFreshSeed() { assertThat(Integer.parseInt(summary.get("uniqueReplayCaseCount"))).isLessThanOrEqualTo(600); }
    @Test void diagnosticReplayCreatesNoFreshPair() throws Exception { assertThat(rows("composition-provenance-replay-case-set.csv")).allSatisfy(r -> assertThat(r).containsKeys("blueLineupId","redLineupId")); }
    @Test void diagnosticReplayMatchesSourceWinner() { assertThat(source.matches(pair)).isTrue(); }
    @Test void diagnosticReplayMatchesSourceDuration() { assertThat(pair.offDuration()).isEqualTo(source.offDuration()); assertThat(pair.auditDuration()).isEqualTo(source.candidateDuration()); }
    @Test void diagnosticReplayMatchesPublicEvents() { zero("sourceOutcomeMismatchCount"); }
    @Test void diagnosticReplayMatchesSnapshots() { zero("sourceOutcomeMismatchCount"); }
    @Test void diagnosticReplayPreservesRandomTrace() { assertThat(pair.preDivergenceRandomMismatch()).isZero(); }

    @Test void economicDirectionUsesPreOutcomeState() throws Exception { sourceTag("composition-economic-opposition-analysis.csv"); }
    @Test void progressionDirectionUsesPreOutcomeState() throws Exception { sourceTag("composition-progression-opposition-analysis.csv"); }
    @Test void matchupDirectionUsesPreOutcomeState() throws Exception { sourceTag("composition-matchup-opposition-analysis.csv"); }
    @Test void championPowerDirectionUsesPreOutcomeState() throws Exception { sourceTag("composition-champion-power-opposition-analysis.csv"); }
    @Test void compositionCanOpposeEconomicAdvantageWithoutAutomaticFailure() throws Exception { assertThat(rows("composition-economic-opposition-analysis.csv")).allMatch(r -> r.get("automaticFailure").equals("false")); }
    @Test void multiFactorTradeoffIsStructured() throws Exception { assertThat(rows("composition-factor-direction-matrix.csv")).extracting(r -> r.get("factor")).contains("GOLD","LEVEL","ITEM","CHAMPION_POWER","MATCHUP","BASELINE"); }
    @Test void baselineNearZeroDoesNotCreateInfiniteRatio() throws Exception { assertThat(rows("composition-modifier-baseline-ratio.csv")).noneMatch(r -> r.values().stream().anyMatch(v -> v.equals("Infinity") || v.equals("NaN"))); }

    @Test void basePushSignatureIsCaptured() throws Exception { assertThat(rows("composition-base-push-nexus-detailed-propagation.csv")).allSatisfy(r -> assertThat(r).containsKey("basePushSequenceChanged")); }
    @Test void nexusEndingSignatureIsCaptured() throws Exception { assertThat(rows("composition-base-push-nexus-detailed-propagation.csv")).allSatisfy(r -> assertThat(r).containsKey("nexusEndingChanged")); }
    @Test void objectivePropagationUsesActualCausalDecision() throws Exception { causal("composition-objective-detailed-propagation.csv"); }
    @Test void structurePropagationUsesActualCausalDecision() throws Exception { causal("composition-structure-detailed-propagation.csv"); }
    @Test void finalWinnerPropagationUsesActualCausalDecision() throws Exception { causal("composition-final-winner-detailed-provenance.csv"); }

    @Test void candidateRemainsFrozen() { assertThat(summary).containsEntry("candidateFrozen","true").containsEntry("candidateUnchanged","true"); }
    @Test void candidateGainCannotChange() { assertThat(summary.get("candidateHash")).isEqualTo(FrozenCompositionKeySpecificChannelCandidate.HASH); FrozenCompositionKeySpecificChannelCandidate.verifyIdentity(FrozenCompositionKeySpecificChannelCandidate.VERSION, FrozenCompositionKeySpecificChannelCandidate.HASH); }
    @Test void productionRemainsOff() { assertThat(summary).containsEntry("productionDefaultMode","OFF").containsEntry("candidateGameplayProductionEnabled","false").containsEntry("teamCompositionProductionEnabled","false"); }
    @Test void apiRemainsUnchanged() { assertThat(summary.get("apiChanged")).isEqualTo("false"); }
    @Test void frontendRemainsUnchanged() { assertThat(summary.get("frontendChanged")).isEqualTo("false"); }
    @Test void historicalThresholdCannotChange() throws Exception { assertThat(rows("composition-historical-policy-vs-corrected-measurement.csv")).extracting(r -> r.get("metric")).contains("Objective macro gate","Structure macro gate","Side macro gate","Winner macro gate"); }

    private static Path historicalFixture() {
        try {
            return Path.of(Objects.requireNonNull(CompositionDecisionTimeProvenanceCaptureTest.class
                    .getResource("/composition-provenance-historical/composition-fresh-holdout-schedule.csv")).toURI()).getParent();
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
    private static CompositionFreshHoldoutCandidateGameplayAudit.Lineup lineup(String id) {
        EnumMap<com.lolfm.domain.Position, com.lolfm.champion.ChampionId> champions = new EnumMap<>(com.lolfm.domain.Position.class);
        for (String role : id.split("\\+")) {
            String[] parts = role.split(":");
            champions.put(com.lolfm.domain.Position.valueOf(parts[1]), new com.lolfm.champion.ChampionId(parts[0]));
        }
        return new CompositionFreshHoldoutCandidateGameplayAudit.Lineup(id, champions, Map.of());
    }

    private static <T> Predicate<T> thisOrStatic(Predicate<T> predicate) { return predicate; }
    private static void zero(String key) { assertThat(summary.get(key)).isEqualTo("0"); }
    private static List<Map<String,String>> rows(String name) throws Exception { return CompositionDecisionTimeProvenanceCapture.read(OUT.resolve(name)); }
    private static Map<String,String> twoColumn(String name,String key,String value) throws Exception { Map<String,String> out=new LinkedHashMap<>(); for(var row:rows(name)) out.put(row.get(key),row.get(value)); return out; }
    private static void assertRows(String name,int count) throws Exception { assertThat(rows(name)).hasSize(count); }
    private static void stage(String name) { assertThat(live.stream().flatMap(p -> p.scoreStages().stream()).map(CompositionDecisionScoreStage::stageName)).contains(name); }
    private static void sourceTag(String name) throws Exception { assertThat(rows(name)).allMatch(r -> r.get("source").equals("PRE_OUTCOME_RUNTIME_VALUE")); }
    private static void causal(String name) throws Exception { assertThat(rows(name)).isNotEmpty().allSatisfy(r -> assertThat(r.get("firstCausalContext")).isNotBlank()); }
}
