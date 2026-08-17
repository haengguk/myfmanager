package com.lolfm.composition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.simulator.TeamSide;
import com.lolfm.simulator.CombatOutcomeProbabilityEvaluator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositionFreshCandidateGameplayPolicyTest {
    @Test
    void frozenPolicyContainsExactlyTheFourApprovedStructuredKeys() {
        FrozenCompositionGameplayGainPolicy policy = FrozenCompositionGameplayGainPolicy.current();
        assertThat(policy.candidateVersion()).isEqualTo(FrozenCompositionGameplayGainPolicy.CANDIDATE_VERSION);
        assertThat(policy.candidateHash()).isEqualTo(FrozenCompositionGameplayGainPolicy.CANDIDATE_HASH);
        assertThat(policy.approvedKeys()).hasSize(4);
        assertThat(policy.approvedKeys()).extracting(CompositionGameplayApplicationKey::stableId)
                .containsExactly(
                        "SKIRMISH|SKIRMISH|SKIRMISH_COMBAT_SCORE",
                        "TEAMFIGHT|TEAMFIGHT|TEAMFIGHT_COMBAT_SCORE",
                        "SIEGE|SIEGE_COMBAT|SIEGE_PUSH_SCORE",
                        "BASE_DEFENSE|BASE_DEFENSE|BASE_DEFENSE_SCORE");
        assertThat(policy.productionEnabled()).isFalse();
        assertThat(policy.candidateEnabled()).isFalse();
    }

    @Test
    void candidateRequiresExactInternalAuditAuthorization() {
        assertThatThrownBy(() -> new CompositionRuntimeState(
                TeamCompositionGameplayMode.CANDIDATE, 7L,
                CompositionCandidateExecutionAuthorization.none()))
                .isInstanceOf(CompositionGameplayConfigurationException.class)
                .extracting("code").isEqualTo("CANDIDATE_CONTEXT_GAINS_NOT_APPROVED");
        CompositionCandidateExecutionAuthorization wrong =
                new CompositionCandidateExecutionAuthorization(
                        FrozenCompositionGameplayGainPolicy.CANDIDATE_VERSION,
                        "wrong", FrozenCompositionGameplayGainPolicy.POLICY_HASH, true);
        assertThatThrownBy(() -> new CompositionRuntimeState(
                TeamCompositionGameplayMode.CANDIDATE, 7L, wrong))
                .isInstanceOf(CompositionGameplayConfigurationException.class)
                .extracting("code").isEqualTo("CANDIDATE_GAIN_POLICY_IDENTITY_MISMATCH");
    }

    @Test
    void approvedApplicationUsesHalfSplitAndDuplicateIsIdempotent() {
        var assignments = new ChampionSelectionValidator(new ChampionCatalog(new ObjectMapper())).resolve(null);
        CompositionRuntimeState state = new CompositionRuntimeState(
                TeamCompositionGameplayMode.CANDIDATE, 11L,
                CompositionCandidateExecutionAuthorization.frozenAudit());
        state.initialize(assignments);
        GameplayAttemptId id = state.createActualAttemptId();
        CompositionAttemptDescriptor attempt = new CompositionAttemptDescriptor(id,
                CompositionActionType.TEAMFIGHT, TeamSide.BLUE, TeamSide.BLUE, TeamSide.RED,
                FightScale.FORMAL, null, false, null, null, 1000,
                CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE, 42.0, 39.0);
        CompositionShadowObservation observation = state.recordActualAttempt(attempt);
        assertThat(observation).isNotNull();
        var application = state.candidateApplications().getFirst();
        assertThat(application.applicationApplied()).isTrue();
        assertThat(application.perspectiveAdjustment() + application.opponentAdjustment()).isEqualTo(0.0);
        assertThat(application.adjustedGap()).isCloseTo(application.baselineGap() + application.gapModifier(), org.assertj.core.data.Offset.offset(1.0e-12));
        assertThat(state.recordActualAttempt(attempt)).isNull();
        assertThat(state.snapshot().gameplayApplicationCount()).isEqualTo(1);
        assertThat(state.snapshot().nonZeroModifierCount()).isEqualTo(
                application.gapModifier() == 0.0 ? 0 : 1);
    }

    @Test
    void sharedRandomSamplePureProjectionDoesNotConsumeAnotherDraw() {
        CombatOutcomeProbabilityEvaluator evaluator = new CombatOutcomeProbabilityEvaluator();
        double sample = 0.37;
        double baseline = evaluator.resolveUniformAdvantageScore(4.0, sample);
        double candidate = evaluator.resolveUniformAdvantageScore(9.0, sample);
        assertThat(candidate - baseline).isEqualTo(5.0);
        assertThat(evaluator.resolveUniformAdvantageScore(4.0, sample)).isEqualTo(baseline);
    }

    @Test
    void localDecisionComparisonCarriesStructuredSharedSampleAndOutcome() {
        CompositionLocalDecisionComparison comparison = new CompositionLocalDecisionComparison(
                17L, new GameplayAttemptId(1L), 900,
                "TEAMFIGHT|TEAMFIGHT|TEAMFIGHT_COMBAT_SCORE",
                "UNIFORM_ADVANTAGE_SIDE_SELECTION", TeamSide.BLUE, 42L, .37,
                -2.0, 3.0, "RED", "BLUE", true, "CLOSE", false,
                true, false, true, "");
        assertThat(comparison.changed()).isTrue();
        assertThat(comparison.sharedRandomSampleIdentity()).isEqualTo(42L);
        assertThat(comparison.comparisonAvailable()).isTrue();
    }

    @Test
    void deferredKeyDoesNotApplyOrConsumeRandom() {
        var assignments = new ChampionSelectionValidator(new ChampionCatalog(new ObjectMapper())).resolve(null);
        CompositionRuntimeState state = new CompositionRuntimeState(
                TeamCompositionGameplayMode.CANDIDATE, 13L,
                CompositionCandidateExecutionAuthorization.frozenAudit());
        state.initialize(assignments);
        state.recordActualAttempt(new CompositionAttemptDescriptor(state.createActualAttemptId(),
                CompositionActionType.JUNGLE_GANK, TeamSide.BLUE, TeamSide.BLUE, TeamSide.RED,
                FightScale.SMALL, null, false, null, null, 1000,
                CompositionBaselineScoreDomain.NOT_AVAILABLE, null, null));
        assertThat(state.snapshot().gameplayApplicationCount()).isZero();
        assertThat(state.snapshot().deferredCandidateApplicationCount()).isEqualTo(1);
        assertThat(state.snapshot().directRandomCallCount()).isZero();
        assertThat(state.snapshot().compositionRandomDrawCount()).isZero();
    }
}
