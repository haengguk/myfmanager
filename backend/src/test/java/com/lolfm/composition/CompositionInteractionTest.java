package com.lolfm.composition;

import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositionInteractionTest {
    @Test void ruleCatalogContainsExactlyEighteenRules() { assertThat(CompositionInteractionRuleCatalog.rules()).hasSize(18); }
    @Test void eachContextContainsExactlyThreeRules() { for (TeamCompositionContext context : TeamCompositionContext.values()) assertThat(CompositionInteractionRuleCatalog.rules(context)).hasSize(3); }
    @Test void allInitialRuleWeightsAreExactlyOne() { assertThat(CompositionInteractionRuleCatalog.rules()).allMatch(x -> x.weight() == 1.0); }
    @Test void ruleIdsAreUnique() { assertThat(CompositionInteractionRuleCatalog.rules().stream().map(CompositionInteractionRule::ruleId).distinct()).hasSize(18); }
    @Test void ruleSignalsUseStructuredReferences() { assertThat(CompositionInteractionRuleCatalog.rules()).allSatisfy(x -> { assertThat(x.sourceSignal()).isNotInstanceOf(String.class); assertThat(x.oppositionSignals()).allMatch(y -> y instanceof PatternSignalRef || y instanceof CapabilitySignalRef); }); }
    @Test void ruleCatalogIsImmutable() { assertThatThrownBy(() -> CompositionInteractionRuleCatalog.rules().clear()).isInstanceOf(UnsupportedOperationException.class); assertThatThrownBy(() -> CompositionInteractionRuleCatalog.rules(TeamCompositionContext.SIEGE).clear()).isInstanceOf(UnsupportedOperationException.class); }
    @Test void ruleCatalogHashIsDeterministic() { assertThat(CompositionInteractionRuleCatalog.catalogHash()).isEqualTo(CompositionInteractionRuleCatalog.catalogHash()); }
    @Test void ruleCatalogHashDoesNotDependOnMapIterationOrder() { var reversed = new ArrayList<>(CompositionInteractionRuleCatalog.rules()); Collections.reverse(reversed); assertThat(CompositionInteractionRuleCatalog.canonicalSerialization(reversed)).isEqualTo(CompositionInteractionRuleCatalog.canonicalSerialization()); }
    @Test void singleOppositionAggregationIsExact() { assertThat(OppositionAggregationPolicy.aggregate(List.of(.4), OppositionAggregation.SINGLE)).isEqualTo(.4); }
    @Test void complementaryTwoUsesPoint65AndPoint35() { assertThat(OppositionAggregationPolicy.aggregate(List.of(.2, .8), OppositionAggregation.COMPLEMENTARY_TWO)).isEqualTo(.65 * .8 + .35 * .2); }
    @Test void complementaryThreeUsesPoint55Point30Point15() { assertThat(OppositionAggregationPolicy.aggregate(List.of(.2, .8, .4), OppositionAggregation.COMPLEMENTARY_THREE)).isEqualTo(.55 * .8 + .30 * .4 + .15 * .2); }
    @Test void oppositionAggregationIsInputOrderIndependent() { assertThat(OppositionAggregationPolicy.aggregate(List.of(.2, .8), OppositionAggregation.COMPLEMENTARY_TWO)).isEqualTo(OppositionAggregationPolicy.aggregate(List.of(.8, .2), OppositionAggregation.COMPLEMENTARY_TWO)); }
    @Test void oppositionAggregationRemainsWithinBounds() { assertThat(OppositionAggregationPolicy.aggregate(List.of(0.0, 1.0, .5), OppositionAggregation.COMPLEMENTARY_THREE)).isBetween(0.0, 1.0); }
    @Test void oppositionAggregationProducesPositiveZeroForAllZero() { assertThat(Double.doubleToLongBits(OppositionAggregationPolicy.aggregate(List.of(-0.0, 0.0), OppositionAggregation.COMPLEMENTARY_TWO))).isEqualTo(Double.doubleToLongBits(0.0)); }
    @Test void formulasAreExactAndRejectOutOfRangeInput() {
        assertThat(CompositionInteractionFormula.GAP_REFERENCE.exposure(.8, .2)).isEqualTo(.8 - .2);
        assertThat(CompositionInteractionFormula.PRODUCT_EXPOSURE.exposure(.8, .2)).isEqualTo(.8 * (1.0 - .2));
        assertThat(CompositionInteractionFormula.GEOMETRIC_EXPOSURE.exposure(.8, .2)).isEqualTo(Math.sqrt(.64));
        assertThatThrownBy(() -> CompositionInteractionFormula.PRODUCT_EXPOSURE.exposure(1.1, .2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompositionInteractionFormula.PRODUCT_EXPOSURE.exposure(Double.NaN, .2)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void formulaProducesPositiveZeroForZeroExposure() { assertThat(Double.doubleToLongBits(CompositionInteractionFormula.GAP_REFERENCE.exposure(0.0, 1.0))).isEqualTo(Double.doubleToLongBits(0.0)); }
    @Test void selfPlaySignedEdgeIsPositiveZero() { CompositionInteractionInput input = fixtureInput("self"); var result = new CompositionInteractionEvaluator().evaluate(input, input, CompositionInteractionFormula.PRODUCT_EXPOSURE); assertThat(result.contexts().values()).allMatch(x -> Double.doubleToLongBits(x.teamASignedEdge()) == Double.doubleToLongBits(0.0)); }
    @Test void signedEdgeIsAntisymmetric() { CompositionInteractionInput a = fixtureInput("a"), b = fixtureInput("b"); var evaluator = new CompositionInteractionEvaluator(); var ab = evaluator.evaluate(a, b, CompositionInteractionFormula.GEOMETRIC_EXPOSURE); var ba = evaluator.evaluate(b, a, CompositionInteractionFormula.GEOMETRIC_EXPOSURE); for (TeamCompositionContext context : TeamCompositionContext.values()) assertThat(ab.contexts().get(context).teamASignedEdge()).isEqualTo(-ba.contexts().get(context).teamASignedEdge()); }
    @Test void directedPressureNeedNotBeSymmetric() { var result = new CompositionInteractionEvaluator().evaluate(fixtureInput("strong"), fixtureInput("weak"), CompositionInteractionFormula.PRODUCT_EXPOSURE); assertThat(result.contexts().values()).anyMatch(x -> x.teamAToTeamB().pressure() != x.teamBToTeamA().pressure()); }
    @Test void strongerOppositionReducesDirectedPressure() { var evaluator = new CompositionInteractionEvaluator(); double weak = evaluator.directed(TeamCompositionContext.SKIRMISH, fixtureInput("source"), fixtureInput("weak"), CompositionInteractionFormula.PRODUCT_EXPOSURE).pressure(); double strong = evaluator.directed(TeamCompositionContext.SKIRMISH, fixtureInput("source"), fixtureInput("strong"), CompositionInteractionFormula.PRODUCT_EXPOSURE).pressure(); assertThat(weak).isGreaterThan(strong); }
    @Test void sameSourceChangesAcrossOpponents() { var evaluator = new CompositionInteractionEvaluator(); assertThat(evaluator.directed(TeamCompositionContext.TEAMFIGHT, fixtureInput("source"), fixtureInput("weak"), CompositionInteractionFormula.PRODUCT_EXPOSURE).pressure()).isNotEqualTo(evaluator.directed(TeamCompositionContext.TEAMFIGHT, fixtureInput("source"), fixtureInput("strong"), CompositionInteractionFormula.PRODUCT_EXPOSURE).pressure()); }
    @Test void contextUsesOnlyItsOwnRules() { assertThat(CompositionInteractionRuleCatalog.rules(TeamCompositionContext.SIEGE)).allMatch(x -> x.context() == TeamCompositionContext.SIEGE); }
    @Test void interactionInputIsImmutable() { var input = fixtureInput("immutable"); assertThatThrownBy(() -> input.capabilityCoverage().clear()).isInstanceOf(UnsupportedOperationException.class); assertThatThrownBy(() -> input.patternReadiness().clear()).isInstanceOf(UnsupportedOperationException.class); }
    @Test void evaluatorIsStatelessAndDoesNotMutateInput() { var input = fixtureInput("stateless"); var before = input.capabilityCoverage(); new CompositionInteractionEvaluator().evaluate(input, fixtureInput("other"), CompositionInteractionFormula.GAP_REFERENCE); assertThat(input.capabilityCoverage()).isEqualTo(before); }
    @Test void explanationMatchesEvaluation() { var result = new CompositionInteractionEvaluator().evaluate(fixtureInput("a"), fixtureInput("b"), CompositionInteractionFormula.PRODUCT_EXPOSURE); for (TeamCompositionContext context : TeamCompositionContext.values()) { var actual = result.contexts().get(context); var explanation = result.explanation().contexts().get(context); assertThat(explanation.teamAToTeamBPressure()).isEqualTo(actual.teamAToTeamB().pressure()); assertThat(explanation.teamBToTeamAPressure()).isEqualTo(actual.teamBToTeamA().pressure()); assertThat(explanation.teamASignedEdge()).isEqualTo(actual.teamASignedEdge()); } }
    @Test @Tag("diagnostic") @Tag("historical-artifact") void representativeSelectionProducesExactlySixtyLineupsAndIncludesAnchors() throws Exception { var rows = CompositionInteractionContextAudit.readLineups(CompositionInteractionContextAudit.SOURCE); var selected = CompositionInteractionContextAudit.selectRepresentatives(rows); assertThat(selected).hasSize(60); assertThat(selected.stream().filter(x -> x.selectionSource().equals("ANCHOR"))).hasSize(12); }
    @Test @Tag("diagnostic") @Tag("historical-artifact") void representativeSelectionIsDeterministic() throws Exception { var rows = CompositionInteractionContextAudit.readLineups(CompositionInteractionContextAudit.SOURCE); assertThat(CompositionInteractionContextAudit.selectRepresentatives(rows).stream().map(CompositionInteractionContextAudit.RepresentativeLineup::lineupId)).containsExactlyElementsOf(CompositionInteractionContextAudit.selectRepresentatives(rows).stream().map(CompositionInteractionContextAudit.RepresentativeLineup::lineupId).toList()); }

    private static CompositionInteractionInput fixtureInput(String id) {
        EnumMap<Position, ChampionRoleKey> lineupMap = new EnumMap<>(Position.class);
        for (Position position : Position.values()) lineupMap.put(position, new ChampionRoleKey(new ChampionId(id + "-" + position.name().toLowerCase()), position));
        TeamCompositionLineup lineup = new TeamCompositionLineup(lineupMap);
        EnumMap<CompositionCapability, Double> capabilities = new EnumMap<>(CompositionCapability.class);
        for (CompositionCapability capability : CompositionCapability.values()) capabilities.put(capability, id.equals("strong") ? .9 : id.equals("weak") ? .1 : (capability == CompositionCapability.ENGAGE || capability == CompositionCapability.PEEL || capability == CompositionCapability.DISENGAGE || capability == CompositionCapability.FRONTLINE || capability == CompositionCapability.BACKLINE_ACCESS ? .8 : .4));
        EnumMap<CompositionPattern, Double> patterns = new EnumMap<>(CompositionPattern.class); for (CompositionPattern pattern : CompositionPattern.values()) patterns.put(pattern, id.equals("strong") ? .9 : id.equals("weak") ? .1 : .7);
        List<CapabilityExplanation> capabilityExplanations = new ArrayList<>(); for (CompositionCapability capability : CompositionCapability.values()) capabilityExplanations.add(new CapabilityExplanation(capability, CompositionAggregationType.PRIMARY_SOURCE, capabilities.get(capability), List.of()));
        List<PatternExplanation> patternExplanations = new ArrayList<>(); for (CompositionPattern pattern : CompositionPattern.values()) patternExplanations.add(new PatternExplanation(pattern, patterns.get(pattern), Map.of(), 1.0, List.of(), true));
        return new CompositionInteractionInput(lineup, capabilities, patterns, new TeamCompositionExplanation(capabilityExplanations, patternExplanations, List.of()));
    }
}
