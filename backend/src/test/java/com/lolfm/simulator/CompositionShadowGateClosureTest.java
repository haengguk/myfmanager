package com.lolfm.simulator;

import com.lolfm.composition.CompositionActionType;
import com.lolfm.composition.CompositionApplicationEligibility;
import com.lolfm.composition.CompositionApplicationPoint;
import com.lolfm.composition.CompositionBaselineScoreDomain;
import com.lolfm.composition.CompositionInteractionFormula;
import com.lolfm.composition.CompositionShadowObservation;
import com.lolfm.composition.GameplayAttemptId;
import com.lolfm.composition.TeamCompositionContext;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompositionShadowGateClosureTest {
    @Test
    void mappedObservationCanBeApplicationIneligibleAndUnavailableIsNotZero() {
        var observation = observation(CompositionActionType.JUNGLE_GANK, TeamCompositionContext.SKIRMISH,
                CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null,
                CompositionApplicationEligibility.INELIGIBLE_NO_EXISTING_SCORE_DOMAIN,
                "DEFERRED_NO_APPROVED_GANK_SCORE_DOMAIN");
        assertThat(observation.context()).isEqualTo(TeamCompositionContext.SKIRMISH);
        assertThat(observation.applicationEligible()).isFalse();
        assertThat(observation.perspectiveBaselineScore()).isNull();
        assertThat(observation.baselineScoreGap()).isNull();
    }

    @Test
    void eligibleObservationUsesExistingScoreDomainAndExactGap() {
        var observation = observation(CompositionActionType.TEAMFIGHT, TeamCompositionContext.TEAMFIGHT,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE, 42.0, 39.0,
                CompositionApplicationEligibility.ELIGIBLE_EXISTING_SCORE_DOMAIN,
                "EXISTING_DETERMINISTIC_SCORE_AT_APPLICATION_POINT");
        assertThat(observation.applicationEligible()).isTrue();
        assertThat(observation.baselineScoreAvailable()).isTrue();
        assertThat(observation.baselineScoreGap()).isEqualTo(3.0);
        assertThat(observation.applicationApplied()).isFalse();
        assertThat(observation.appliedModifier()).isZero();
    }

    @Test
    void skirmishSubtypesAreClassifiedIndependently() {
        List<CompositionShadowWiringGateClosureAudit.Group> groups = CompositionShadowWiringGateClosureAudit.groups(List.of(
                row(observation(CompositionActionType.SKIRMISH, TeamCompositionContext.SKIRMISH,
                        CompositionBaselineScoreDomain.SKIRMISH_COMBAT_SCORE, 20.0, 18.0,
                        CompositionApplicationEligibility.ELIGIBLE_EXISTING_SCORE_DOMAIN, "EXISTING")),
                row(observation(CompositionActionType.JUNGLE_GANK, TeamCompositionContext.SKIRMISH,
                        CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null,
                        CompositionApplicationEligibility.INELIGIBLE_NO_EXISTING_SCORE_DOMAIN, "DEFERRED_GANK")),
                row(observation(CompositionActionType.LANE_COMBAT, TeamCompositionContext.SKIRMISH,
                        CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null,
                        CompositionApplicationEligibility.INELIGIBLE_NO_EXISTING_SCORE_DOMAIN, "DEFERRED_LANE")),
                row(observation(CompositionActionType.ROAM, TeamCompositionContext.SKIRMISH,
                        CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null,
                        CompositionApplicationEligibility.INELIGIBLE_NO_EXISTING_SCORE_DOMAIN, "DEFERRED_ROAM"))));
        assertThat(groups).filteredOn(g -> g.key().action() == CompositionActionType.SKIRMISH)
                .extracting(CompositionShadowWiringGateClosureAudit.Group::resolution)
                .containsExactly("GAIN_SCREENING_ELIGIBLE");
        assertThat(groups).filteredOn(g -> g.key().context() == TeamCompositionContext.SKIRMISH
                        && g.key().action() != CompositionActionType.SKIRMISH)
                .extracting(CompositionShadowWiringGateClosureAudit.Group::resolution)
                .containsOnly("EXPLICITLY_DEFERRED");
    }

    @Test
    void objectiveSetupDoesNotInventScoreAndIsExplicitlyDeferred() {
        var group = group(observation(CompositionActionType.OBJECTIVE_SETUP, TeamCompositionContext.OBJECTIVE_SETUP,
                CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null,
                CompositionApplicationEligibility.INELIGIBLE_NO_EXISTING_SCORE_DOMAIN,
                "DEFERRED_NO_APPROVED_OBJECTIVE_SETUP_SCORE_DOMAIN"));
        assertThat(group.resolution()).isEqualTo("EXPLICITLY_DEFERRED");
        assertThat(group.availableCount()).isZero();
    }

    @Test
    void siegeSeparatesScoreProducingCombatAndObservationOnlyStructureAttempts() {
        var groups = CompositionShadowWiringGateClosureAudit.groups(List.of(
                row(observation(CompositionActionType.SIEGE_COMBAT, TeamCompositionContext.SIEGE,
                        CompositionBaselineScoreDomain.SIEGE_PUSH_SCORE, 31.0, 28.0,
                        CompositionApplicationEligibility.ELIGIBLE_EXISTING_SCORE_DOMAIN, "EXISTING")),
                row(observation(CompositionActionType.SIEGE, TeamCompositionContext.SIEGE,
                        CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null,
                        CompositionApplicationEligibility.INELIGIBLE_NOT_SCORE_PRODUCING_ATTEMPT, "OBSERVATION_ONLY"))));
        assertThat(groups).filteredOn(g -> g.key().action() == CompositionActionType.SIEGE_COMBAT)
                .extracting(CompositionShadowWiringGateClosureAudit.Group::resolution)
                .containsExactly("GAIN_SCREENING_ELIGIBLE");
        assertThat(groups).filteredOn(g -> g.key().action() == CompositionActionType.SIEGE)
                .extracting(CompositionShadowWiringGateClosureAudit.Group::resolution)
                .containsExactly("EXPLICITLY_DEFERRED");
    }

    @Test
    void sideLaneWithoutStructuredActionIsExplicitlyDeferred() {
        assertThat(CompositionShadowWiringGateClosureAudit.groups(List.of()))
                .filteredOn(g -> g.key().context() == TeamCompositionContext.SIDE_LANE)
                .extracting(CompositionShadowWiringGateClosureAudit.Group::resolution)
                .containsExactly("EXPLICITLY_DEFERRED");
    }

    @Test
    void absoluteGapMaximumIsActualMaximumAndPercentilesAreMonotonic() {
        var group = new CompositionShadowWiringGateClosureAudit.Group(
                new CompositionShadowWiringGateClosureAudit.Key(TeamCompositionContext.TEAMFIGHT, CompositionActionType.TEAMFIGHT),
                List.of(
                        observation(CompositionActionType.TEAMFIGHT, TeamCompositionContext.TEAMFIGHT,
                                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE, 10.0, 9.0,
                                CompositionApplicationEligibility.ELIGIBLE_EXISTING_SCORE_DOMAIN, "EXISTING"),
                        observation(CompositionActionType.TEAMFIGHT, TeamCompositionContext.TEAMFIGHT,
                                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE, 30.0, 10.0,
                                CompositionApplicationEligibility.ELIGIBLE_EXISTING_SCORE_DOMAIN, "EXISTING")),
                "GAIN_SCREENING_ELIGIBLE");
        List<String> distribution = CompositionShadowWiringGateClosureAudit.distribution(List.of(group)).get(1);
        assertThat(distribution.get(32)).isEqualTo("20.000000000000");
        assertThat(CompositionShadowWiringGateClosureAudit.statisticIntegrity(List.of(group)).subList(1, 8))
                .allSatisfy(row -> assertThat(row.get(3)).isEqualTo("true"));
    }

    @Test
    void zeroAvailableCountProducesNotApplicableStatistics() {
        var group = group(observation(CompositionActionType.ROAM, TeamCompositionContext.SKIRMISH,
                CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null,
                CompositionApplicationEligibility.INELIGIBLE_NO_EXISTING_SCORE_DOMAIN, "DEFERRED"));
        List<String> distribution = CompositionShadowWiringGateClosureAudit.distribution(List.of(group)).get(1);
        assertThat(distribution.subList(7, 33)).containsOnly("NOT_APPLICABLE");
    }

    @Test
    void routingArtifactReportsUnavailableInstrumentationAsNotInstrumented() {
        String source;
        try {
            source = Files.readString(java.nio.file.Path.of("src/test/java/com/lolfm/simulator/CompositionShadowWiringGateClosureAudit.java"));
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
        assertThat(source).contains("\"false\", \"NOT_INSTRUMENTED\"")
                .contains("evaluationBoundaryVerifiedByTargetedTests")
                .contains("triggerBoundaryVerifiedByTargetedTests");
    }

    @Test
    void gateTaskAndRequiredArtifactsAreDeclaredWithoutGainApplication() throws Exception {
        String build = Files.readString(java.nio.file.Path.of("build.gradle"));
        String source = Files.readString(java.nio.file.Path.of("src/test/java/com/lolfm/simulator/CompositionShadowWiringGateClosureAudit.java"));
        assertThat(build).contains("runCompositionShadowWiringGateClosureAudit");
        assertThat(source).contains("composition-shadow-context-distribution-corrected.csv")
                .contains("composition-shadow-statistic-integrity.csv")
                .contains("composition-shadow-gain-screening-readiness.csv")
                .doesNotContain("Math.random()", "appliedModifier=1", "CANDIDATE_ENABLE");
    }

    private static CompositionShadowWiringGateClosureAudit.Group group(CompositionShadowObservation observation) {
        return CompositionShadowWiringGateClosureAudit.groups(List.of(row(observation))).stream()
                .filter(g -> g.key().context() == observation.context()).findFirst().orElseThrow();
    }

    private static CompositionShadowWiringAudit.ObservationRow row(CompositionShadowObservation observation) {
        return new CompositionShadowWiringAudit.ObservationRow(0, 1L, observation);
    }

    private static CompositionShadowObservation observation(CompositionActionType action,
                                                            TeamCompositionContext context,
                                                            CompositionBaselineScoreDomain domain,
                                                            Double perspectiveScore,
                                                            Double opponentScore,
                                                            CompositionApplicationEligibility eligibility,
                                                            String reason) {
        boolean available = perspectiveScore != null;
        Double gap = available ? perspectiveScore - opponentScore : null;
        return new CompositionShadowObservation(1L, new GameplayAttemptId(action.ordinal() + 1L), 1000,
                action, context, TeamSide.BLUE, TeamSide.BLUE, TeamSide.RED, 0.1, -0.1, 0.1,
                "candidate", "hash", CompositionInteractionFormula.PRODUCT_EXPOSURE,
                context == TeamCompositionContext.TEAMFIGHT ? CompositionApplicationPoint.TEAMFIGHT_COMBAT
                        : context == TeamCompositionContext.BASE_DEFENSE ? CompositionApplicationPoint.BASE_DEFENSE
                        : context == TeamCompositionContext.SIEGE ? CompositionApplicationPoint.SIEGE_PUSH
                        : context == TeamCompositionContext.OBJECTIVE_SETUP ? CompositionApplicationPoint.OBJECTIVE_SETUP
                        : context == TeamCompositionContext.SIDE_LANE ? CompositionApplicationPoint.SIDE_LANE
                        : CompositionApplicationPoint.SKIRMISH_COMBAT,
                domain, available, perspectiveScore, opponentScore, gap, eligibility, eligibility.eligible(),
                reason, available ? "EXISTING_POINT" : "NOT_AVAILABLE", "TEST_STRUCTURED_EVIDENCE",
                false, 0.0, "STRUCTURED_TEST_ROUTING");
    }
}
