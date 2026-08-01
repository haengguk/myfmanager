package com.lolfm.composition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.domain.Position;
import com.lolfm.simulator.SimulationOptions;
import com.lolfm.simulator.TeamSide;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositionRuntimeStateTest {
    @Test
    void productionDefaultCompositionModeIsOff() {
        assertThat(SimulationOptions.productionDefaults().teamCompositionGameplayMode())
                .isEqualTo(TeamCompositionGameplayMode.OFF);
    }

    @Test
    void explicitModesAreSupportedAndCandidateIsNotSilentlyChanged() {
        assertThat(SimulationOptions.productionDefaults()
                .withTeamCompositionGameplayMode(TeamCompositionGameplayMode.OFF)
                .teamCompositionGameplayMode()).isEqualTo(TeamCompositionGameplayMode.OFF);
        assertThat(SimulationOptions.productionDefaults()
                .withTeamCompositionGameplayMode(TeamCompositionGameplayMode.SHADOW)
                .teamCompositionGameplayMode()).isEqualTo(TeamCompositionGameplayMode.SHADOW);
        assertThat(SimulationOptions.productionDefaults()
                .withTeamCompositionGameplayMode(TeamCompositionGameplayMode.CANDIDATE)
                .teamCompositionGameplayMode()).isEqualTo(TeamCompositionGameplayMode.CANDIDATE);
    }

    @Test
    void frozenRuntimePolicyHasExactIdentityAndDeterministicHash() {
        FrozenCompositionInteractionRuntimePolicy policy = FrozenCompositionInteractionRuntimePolicy.current();
        assertThat(policy.profileHash()).isEqualTo(FrozenCompositionInteractionRuntimePolicy.PROFILE_HASH);
        assertThat(policy.ruleCatalogHash()).isEqualTo(FrozenCompositionInteractionRuntimePolicy.RULE_CATALOG_HASH);
        assertThat(policy.candidateHash()).isEqualTo(FrozenCompositionInteractionRuntimePolicy.CANDIDATE_HASH);
        assertThat(FrozenCompositionInteractionRuntimePolicy.candidateHashFor(policy.formula()))
                .isEqualTo(policy.candidateHash());
        assertThat(policy.gain()).isEqualTo("NONE");
        assertThat(policy.deadzone()).isEqualTo("NONE");
        assertThat(policy.overrideCount()).isZero();
    }

    @Test
    void offModeDoesNotBuildCompositionState() {
        CompositionRuntimeState state = CompositionRuntimeState.off(7L);
        state.initialize(assignments());
        assertThat(state.initialized()).isFalse();
        assertThat(state.snapshot().lineupBuildCount()).isZero();
        assertThat(state.snapshot().shadowObservationCount()).isZero();
        assertThat(state.frozenPolicy()).isNull();
    }

    @Test
    void shadowInitializesExactlyOnceAndStoresAntisymmetricEdges() {
        CompositionRuntimeState state = new CompositionRuntimeState(TeamCompositionGameplayMode.SHADOW, 7L);
        state.initialize(assignments());
        assertThat(state.initialized()).isTrue();
        assertThat(state.snapshot().lineupBuildCount()).isEqualTo(2);
        assertThat(state.snapshot().teamCompositionAnalysisCount()).isEqualTo(2);
        assertThat(state.snapshot().interactionAnalysisCount()).isEqualTo(1);
        assertThat(state.snapshot().contextEdgeCount()).isEqualTo(6);
        for (TeamCompositionContext context : TeamCompositionContext.values()) {
            assertThat(state.edgeFor(TeamSide.RED, context)).isEqualTo(-state.edgeFor(TeamSide.BLUE, context));
            if (state.edgeFor(TeamSide.BLUE, context) == 0.0) {
                assertThat(Double.doubleToRawLongBits(state.edgeFor(TeamSide.BLUE, context)))
                        .isEqualTo(Double.doubleToRawLongBits(0.0));
            }
        }
        assertThatThrownBy(() -> state.initialize(assignments()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void actualAttemptCreatesOneObservationAndDuplicateIsIdempotent() {
        CompositionRuntimeState state = new CompositionRuntimeState(TeamCompositionGameplayMode.SHADOW, 9L);
        state.initialize(assignments());
        GameplayAttemptId id = state.createActualAttemptId();
        CompositionAttemptDescriptor attempt = new CompositionAttemptDescriptor(id,
                CompositionActionType.TEAMFIGHT, TeamSide.BLUE, TeamSide.BLUE, TeamSide.RED,
                FightScale.FORMAL, null, false, null, null, 1000,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE, 42.0, 39.0);
        CompositionShadowObservation observation = state.recordActualAttempt(attempt);
        assertThat(observation).isNotNull();
        assertThat(state.recordActualAttempt(attempt)).isNull();
        assertThat(state.snapshot().actualAttemptCount()).isEqualTo(1);
        assertThat(state.snapshot().mappedActualAttemptCount()).isEqualTo(1);
        assertThat(state.snapshot().shadowObservationCount()).isEqualTo(1);
        assertThat(state.snapshot().duplicateObservationCount()).isEqualTo(1);
        assertThat(observation.applicationApplied()).isFalse();
        assertThat(observation.appliedModifier()).isEqualTo(0.0);
        assertThat(observation.baselineScoreGap()).isEqualTo(3.0);
    }

    @Test
    void evaluationDoesNotIssueAttemptIdOrObservation() {
        CompositionRuntimeState state = new CompositionRuntimeState(TeamCompositionGameplayMode.SHADOW, 9L);
        state.initialize(assignments());
        state.recordResolverEvaluation();
        state.recordTriggerSuccess();
        assertThat(state.snapshot().actualAttemptCount()).isZero();
        assertThat(state.snapshot().shadowObservationCount()).isZero();
        assertThat(state.snapshot().resolverEvaluationCount()).isEqualTo(1);
        assertThat(state.snapshot().triggerSuccessCount()).isEqualTo(1);
    }

    @Test
    void routerUsesStructuredMetadataAndLeavesSideLaneUnmappedWhenAbsent() {
        CompositionContextRouter router = new CompositionContextRouter();
        CompositionAttemptDescriptor objective = new CompositionAttemptDescriptor(new GameplayAttemptId(1),
                CompositionActionType.OBJECTIVE_SETUP, TeamSide.RED, TeamSide.RED, TeamSide.BLUE,
                FightScale.FORMAL, com.lolfm.simulator.ObjectiveType.DRAGON, true, null, null, 1200,
                CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null);
        assertThat(router.route(objective).context()).isEqualTo(TeamCompositionContext.OBJECTIVE_SETUP);
        assertThat(router.route(objective).perspectiveSide()).isEqualTo(TeamSide.RED);

        CompositionAttemptDescriptor sideLane = new CompositionAttemptDescriptor(new GameplayAttemptId(2),
                CompositionActionType.SIDE_LANE, TeamSide.BLUE, TeamSide.BLUE, TeamSide.RED,
                FightScale.NONE, null, false, null, null, 1200,
                CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null);
        assertThat(router.route(sideLane).mapped()).isTrue();

        CompositionAttemptDescriptor capture = new CompositionAttemptDescriptor(new GameplayAttemptId(3),
                CompositionActionType.OBJECTIVE_CAPTURE, TeamSide.BLUE, TeamSide.BLUE, TeamSide.RED,
                FightScale.NONE, com.lolfm.simulator.ObjectiveType.BARON, false, null, null, 1200,
                CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null);
        assertThat(router.route(capture).mapped()).isFalse();
    }

    @Test
    void candidateIdentityMismatchFailsWithoutNeutralFallback() {
        FrozenCompositionInteractionRuntimePolicy invalid = new FrozenCompositionInteractionRuntimePolicy(
                FrozenCompositionInteractionRuntimePolicy.PROFILE_VERSION,
                FrozenCompositionInteractionRuntimePolicy.PROFILE_HASH,
                FrozenCompositionInteractionRuntimePolicy.RULE_CATALOG_VERSION,
                FrozenCompositionInteractionRuntimePolicy.RULE_CATALOG_HASH,
                CompositionInteractionFormula.PRODUCT_EXPOSURE,
                FrozenCompositionInteractionRuntimePolicy.CANDIDATE_VERSION,
                "bad-hash", "NONE", "NONE", 0);
        assertThatThrownBy(invalid::verifyExactIdentity)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("candidate");
    }

    private static com.lolfm.champion.MatchChampionAssignments assignments() {
        return new ChampionSelectionValidator(new ChampionCatalog(new ObjectMapper())).resolve(null);
    }
}
