package com.lolfm.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompositionKeySpecificSemanticsDesignTest {
    private CompositionKeySpecificSemanticsDesign.Blueprint blueprint;

    @BeforeAll
    void load() throws Exception {
        blueprint = CompositionKeySpecificSemanticsDesign.design();
    }

    @Test void semanticsReviewHashesAreExact() throws Exception {
        assertThat(CompositionKeySpecificSemanticsDesign.sha256(Files.readAllBytes(
                CompositionKeySpecificSemanticsDesign.SOURCE_SUMMARY)))
                .isEqualTo(CompositionKeySpecificSemanticsDesign.SOURCE_SUMMARY_HASH);
        assertThat(CompositionKeySpecificSemanticsDesign.sha256(Files.readAllBytes(
                CompositionKeySpecificSemanticsDesign.SOURCE_AUDIT)))
                .isEqualTo(CompositionKeySpecificSemanticsDesign.SOURCE_AUDIT_HASH);
    }

    @Test void historicalGameplayCandidateRemainsExact() {
        assertThat(FrozenCompositionGameplayGainPolicy.CANDIDATE_HASH)
                .isEqualTo(CompositionKeySpecificSemanticsDesign.HISTORICAL_CANDIDATE_HASH);
        FrozenCompositionGameplayGainPolicy.current().verifyExactIdentity();
    }

    @Test void frozenProfileAndRuleCatalogRemainExact() {
        FrozenCompositionInteractionRuntimePolicy.current().verifyExactIdentity();
        assertThat(CompositionInteractionRuleCatalog.catalogHash())
                .isEqualTo(FrozenCompositionInteractionRuntimePolicy.RULE_CATALOG_HASH);
    }

    @Test void sourceArtifactsRemainReadOnly() {
        assertThat(blueprint.sourceBefore()).isEqualTo(blueprint.sourceAfter());
    }

    @Test void designRunsNoGameplaySimulation() {
        assertThat(metric("matchSimulationCount")).isEqualTo("0");
        assertThat(metric("gameplayApplicationExecutionCount")).isEqualTo("0");
        assertThat(metric("randomDrawCount")).isEqualTo("0");
    }

    @Test void designCreatesNoGameplayGainCandidate() {
        assertThat(metric("newGameplayCandidateCreated")).isEqualTo("false");
        assertThat(metric("gameplayGainChanged")).isEqualTo("false");
    }

    @Test void skirmishHasWinnerChannelOnly() {
        var key = key(TeamCompositionContext.SKIRMISH);
        assertThat(key.winnerChannelState()).isEqualTo(
                CompositionKeySpecificSemanticsDesign.CompositionChannelState.ACTIVE_EXISTING_FROZEN);
        assertThat(key.severityChannelState()).isEqualTo(
                CompositionKeySpecificSemanticsDesign.CompositionChannelState.NOT_APPLICABLE);
    }

    @Test void teamfightHasDistinctWinnerAndSeverityChannels() {
        assertDistinctChannels(key(TeamCompositionContext.TEAMFIGHT));
    }

    @Test void siegeHasDistinctWinnerAndSeverityChannels() {
        assertDistinctChannels(key(TeamCompositionContext.SIEGE));
    }

    @Test void baseDefenseHasRoleAwareWinnerAndSeparateSeverityChannels() {
        var key = key(TeamCompositionContext.BASE_DEFENSE);
        assertDistinctChannels(key);
        assertThat(key.winnerSignalSource()).isEqualTo(
                CompositionKeySpecificSemanticsDesign.CompositionSignalSource.ROLE_AWARE_RULE_EDGE);
    }

    @Test void winnerAndSeverityDoNotShareMutableAdjustedScore() {
        assertThat(blueprint.keys().stream().filter(CompositionKeySpecificSemanticsDesign.CompositionKeySemantics::gradeDiagnosticRequired))
                .allMatch(x -> x.winnerScoreApplicationMode() != x.severityScoreApplicationMode());
        assertThat(blueprint.integrity().implicitWinnerSeveritySharedMutationCount()).isZero();
    }

    @Test void winnerModifierCannotImplicitlyEnterSeverity() {
        assertThat(CompositionKeySpecificSemanticsDesign.independenceContract())
                .anyMatch(x -> x.contains("NO_WINNER_TO_SEVERITY_MUTATION") && x.contains("true"));
    }

    @Test void severityModifierCannotAffectWinner() {
        assertThat(CompositionKeySpecificSemanticsDesign.independenceContract())
                .anyMatch(x -> x.contains("NO_SEVERITY_TO_WINNER_EFFECT") && x.contains("true"));
    }

    @Test void siegeHasNoDirectStructureCompositionChannel() {
        assertThat(blueprint.integrity().directStructureCompositionChannelCount()).isZero();
        assertThat(blueprint.canonicalSerialization()).doesNotContain("DIRECT_STRUCTURE_COMPOSITION_CHANNEL");
    }

    @Test void baseDefenseUsesStructuredAttackerDefenderRoles() {
        assertThat(blueprint.baseDefenseRole().structuredSidesRequired()).isTrue();
        assertThat(blueprint.baseDefenseRole().positiveRole())
                .isEqualTo(CompositionKeySpecificSemanticsDesign.CompositionCombatRole.ATTACKER);
        assertThat(blueprint.baseDefenseRole().negativeRole())
                .isEqualTo(CompositionKeySpecificSemanticsDesign.CompositionCombatRole.DEFENDER);
    }

    @Test void baseDefenseRoleSignConventionIsDeterministic() {
        var roles = blueprint.baseDefenseRole();
        assertThat(roles.canonicalOutcomeEdge(.8, .3)).isEqualTo(.5);
        assertThat(roles.perspectiveEdge(CompositionKeySpecificSemanticsDesign.CompositionCombatRole.ATTACKER, .5)).isEqualTo(.5);
        assertThat(roles.perspectiveEdge(CompositionKeySpecificSemanticsDesign.CompositionCombatRole.DEFENDER, .5)).isEqualTo(-.5);
    }

    @Test void baseDefenseSideSwapReversesPerspectiveExactly() {
        var roles = blueprint.baseDefenseRole();
        assertThat(roles.canonicalOutcomeEdge(.3, .8)).isEqualTo(-roles.canonicalOutcomeEdge(.8, .3));
    }

    @Test void baseDefenseDoesNotInferRoleFromDisplayName() {
        assertThat(blueprint.baseDefenseRole().displayNameInferenceAllowed()).isFalse();
    }

    @Test void baseDefenseDoesNotCopyGenericTeamfightSemanticsUnqualified() {
        assertThat(key(TeamCompositionContext.BASE_DEFENSE).combatRoleSemantics())
                .isEqualTo(CompositionKeySpecificSemanticsDesign.CombatRoleSemantics.STRUCTURED_ATTACKER_DEFENDER);
        assertThat(key(TeamCompositionContext.BASE_DEFENSE).winnerScoreApplicationMode())
                .isNotEqualTo(key(TeamCompositionContext.TEAMFIGHT).winnerScoreApplicationMode());
    }

    @Test void baseDefenseHalfSplitDispositionIsExplicit() {
        assertThat(key(TeamCompositionContext.BASE_DEFENSE).halfSplitDisposition()).isEqualTo(
                CompositionKeySpecificSemanticsDesign.HalfSplitDisposition.ROLE_AWARE_ASYMMETRIC_ADJUSTMENT_REQUIRED);
    }

    @Test void everyRelevantRuleHasDecisionChannelClassification() {
        assertThat(blueprint.ruleMappings()).hasSameSizeAs(CompositionInteractionRuleCatalog.rules());
        assertThat(blueprint.ruleMappings()).noneMatch(x -> x.classification()
                == CompositionKeySpecificSemanticsDesign.RuleChannelClassification.UNRESOLVED);
    }

    @Test void ruleMappingUsesStructuredRuleIdentity() {
        var catalog = CompositionInteractionRuleCatalog.rules().stream().map(CompositionInteractionRule::ruleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var mapped = blueprint.ruleMappings().stream().map(CompositionKeySpecificSemanticsDesign.RuleChannelMapping::ruleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertThat(mapped).containsExactlyElementsOf(catalog);
        assertThat(blueprint.ruleMappings()).allMatch(x -> x.sourceSignal() instanceof CompositionSignalRef);
    }

    @Test void ruleMappingDoesNotReadGameplayOutcome() {
        assertThat(blueprint.ruleMappings()).allMatch(x -> !x.evidence().contains("winnerRate")
                && !x.evidence().contains("gameplayOutcome") && !x.evidence().contains("holdout"));
    }

    @Test void severityDoesNotAutomaticallyReuseWinnerAggregateEdge() {
        assertThat(blueprint.keys().stream().filter(CompositionKeySpecificSemanticsDesign.CompositionKeySemantics::gradeDiagnosticRequired))
                .allMatch(x -> x.severitySignalSource()
                        == CompositionKeySpecificSemanticsDesign.CompositionSignalSource.SEPARATE_RULE_TRANSFORM_REQUIRED);
    }

    @Test void bothChannelRuleRequiresExplicitSeparateTransform() {
        assertThat(blueprint.ruleMappings().stream().filter(x -> x.classification()
                == CompositionKeySpecificSemanticsDesign.RuleChannelClassification.BOTH_REQUIRES_SEPARATE_TRANSFORM))
                .allMatch(CompositionKeySpecificSemanticsDesign.RuleChannelMapping::separateTransformRequired);
    }

    @Test void unresolvedRuleMappingBlocksBlueprintFreeze() {
        List<CompositionKeySpecificSemanticsDesign.RuleChannelMapping> modified = new ArrayList<>(blueprint.ruleMappings());
        var first = modified.get(0);
        modified.set(0, new CompositionKeySpecificSemanticsDesign.RuleChannelMapping(first.ruleId(), first.context(),
                first.sourceSignal(), first.oppositionSignals(),
                CompositionKeySpecificSemanticsDesign.RuleChannelClassification.UNRESOLVED, false, first.evidence()));
        var integrity = CompositionKeySpecificSemanticsDesign.integrity(blueprint.keys(), modified,
                blueprint.baseDefenseRole(), blueprint.gradeInputs(), blueprint.diagnosticFields());
        assertThat(integrity.unresolvedRuleMappingCount()).isOne();
        assertThat(integrity.total()).isPositive();
    }

    @Test void gradeAdjustedGapAndDominanceInputsAreAuditedSeparately() {
        for (TeamCompositionContext context : List.of(TeamCompositionContext.TEAMFIGHT,
                TeamCompositionContext.SIEGE, TeamCompositionContext.BASE_DEFENSE)) {
            var rows = blueprint.gradeInputs().stream().filter(x -> x.applicationKey().context() == context).toList();
            assertThat(rows).extracting(CompositionKeySpecificSemanticsDesign.GradeInputAudit::inputName)
                    .containsExactly("candidateTeamfightGap", "dominanceBonus");
        }
    }

    @Test void gradeInternalCompositionReuseIsCounted() {
        assertThat(blueprint.keys().stream().filter(CompositionKeySpecificSemanticsDesign.CompositionKeySemantics::gradeDiagnosticRequired))
                .allMatch(x -> x.gradeInternalCompositionReuseCount() == 2);
    }

    @Test void gradeRepeatedCompositionSignalCannotBeHiddenAsOneInput() {
        assertThat(blueprint.gradeInputs().stream().filter(
                CompositionKeySpecificSemanticsDesign.GradeInputAudit::containsHistoricalCompositionSignal)).hasSize(6);
        assertThat(blueprint.keys().stream().filter(CompositionKeySpecificSemanticsDesign.CompositionKeySemantics::gradeDiagnosticRequired))
                .allMatch(x -> x.gradeInputRelation()
                        == CompositionKeySpecificSemanticsDesign.CompositionGradeInputRelation.GRADE_COMPOSITION_SIGNAL_REPEATED);
    }

    @Test void gradeDiagnosticContractCapturesConditionalRandomBranches() {
        Map<String, CompositionKeySpecificSemanticsDesign.DiagnosticField> fields = blueprint.diagnosticFields().stream()
                .collect(Collectors.toMap(CompositionKeySpecificSemanticsDesign.DiagnosticField::fieldName, x -> x));
        assertThat(fields).containsKeys("aceRandomSample", "bigRandomSample", "normalRandomSample",
                "aceProbabilityThreshold", "bigProbabilityThreshold", "normalProbabilityThreshold");
        assertThat(fields.get("bigRandomSample").branchPolicy()).isEqualTo("NOT_DRAWN_BRANCH_NOT_REACHED");
        assertThat(fields.get("normalRandomSample").branchPolicy()).isEqualTo("NOT_DRAWN_BRANCH_NOT_REACHED");
    }

    @Test void gradeDiagnosticContractAddsNoRandomDraw() {
        assertThat(blueprint.diagnosticFields()).noneMatch(CompositionKeySpecificSemanticsDesign.DiagnosticField::additionalRandomDraw);
        assertThat(blueprint.integrity().randomDiagnosticDesignErrorCount()).isZero();
    }

    @Test void skirmishExistingGainCanRemainFrozen() {
        assertThat(key(TeamCompositionContext.SKIRMISH).numericWinnerGainStatus())
                .isEqualTo(CompositionKeySpecificSemanticsDesign.NumericCalibrationStatus.FROZEN);
        assertThat(FrozenCompositionGameplayGainPolicy.SKIRMISH_GAIN).isEqualTo(24.509721397259);
    }

    @Test void failedKeyHistoricalGainIsNotAutomaticallyFrozen() {
        assertThat(blueprint.keys().stream().filter(x -> x.applicationKey().context() != TeamCompositionContext.SKIRMISH))
                .noneMatch(x -> x.numericWinnerGainStatus()
                        == CompositionKeySpecificSemanticsDesign.NumericCalibrationStatus.FROZEN);
    }

    @Test void severityGainIsUncalibrated() {
        assertThat(blueprint.keys().stream().filter(CompositionKeySpecificSemanticsDesign.CompositionKeySemantics::gradeDiagnosticRequired))
                .allMatch(x -> x.numericSeverityGainStatus()
                        == CompositionKeySpecificSemanticsDesign.NumericCalibrationStatus.UNCALIBRATED);
    }

    @Test void baseDefenseRoleAwareWinnerGainIsUncalibrated() {
        assertThat(key(TeamCompositionContext.BASE_DEFENSE).numericWinnerGainStatus()).isEqualTo(
                CompositionKeySpecificSemanticsDesign.NumericCalibrationStatus.UNCALIBRATED_ROLE_AWARE);
    }

    @Test void designDoesNotInventNumericGain() {
        assertThat(blueprint.canonicalSerialization()).doesNotContain("24.509721397259", "11.595061941148",
                "6.805985567298", "10.837956658606");
        assertThat(metric("gameplayGainChanged")).isEqualTo("false");
    }

    @Test void blueprintCanonicalSerializationIsDeterministic() {
        String rebuilt = CompositionKeySpecificSemanticsDesign.canonicalSerialization(blueprint.keys(),
                blueprint.ruleMappings(), blueprint.baseDefenseRole(), blueprint.gradeInputs(),
                blueprint.diagnosticFields(), blueprint.zeroReferences());
        assertThat(rebuilt).isEqualTo(blueprint.canonicalSerialization());
    }

    @Test void blueprintHashIsDeterministic() throws Exception {
        assertThat(blueprint.hash()).isEqualTo(
                CompositionKeySpecificSemanticsDesign.sha256(blueprint.canonicalSerialization()));
        assertThat(CompositionKeySpecificSemanticsDesign.design().hash()).isEqualTo(blueprint.hash());
    }

    @Test void blueprintHashExcludesGameplayOutcome() {
        assertThat(blueprint.canonicalSerialization()).doesNotContain("winnerRate", "matchOutcome",
                "holdoutResult", "lineupId", "seed=");
    }

    @Test void blueprintCannotFreezeWithUnresolvedSemantics() {
        List<CompositionKeySpecificSemanticsDesign.CompositionKeySemantics> modified = new ArrayList<>(blueprint.keys());
        var old = modified.get(1);
        modified.set(1, new CompositionKeySpecificSemanticsDesign.CompositionKeySemantics(old.applicationKey(),
                CompositionKeySpecificSemanticsDesign.CompositionChannelState.UNRESOLVED,
                CompositionKeySpecificSemanticsDesign.CompositionSignalSource.UNRESOLVED,
                old.winnerScoreApplicationMode(), old.severityChannelState(), old.severitySignalSource(),
                old.severityScoreApplicationMode(), old.combatRoleSemantics(), old.halfSplitDisposition(),
                old.gradeInputRelation(), old.gradeInternalCompositionReuseCount(), old.gradeDiagnosticRequired(),
                old.numericWinnerGainStatus(), old.numericSeverityGainStatus(), old.calibrationRequired(),
                old.freshHoldoutRequired()));
        var integrity = CompositionKeySpecificSemanticsDesign.integrity(modified, blueprint.ruleMappings(),
                blueprint.baseDefenseRole(), blueprint.gradeInputs(), blueprint.diagnosticFields());
        assertThat(integrity.unresolvedChannelCount()).isOne();
        assertThat(integrity.total()).isPositive();
    }

    @Test void blueprintDoesNotEnableProduction() {
        assertThat(metric("productionDefaultMode")).isEqualTo("OFF");
        assertThat(metric("candidateGameplayProductionEnabled")).isEqualTo("false");
        assertThat(metric("teamCompositionProductionEnabled")).isEqualTo("false");
        assertThat(metric("publicCandidateGuarded")).isEqualTo("true");
    }

    @Test void verdictIsComputedNotHardcoded() {
        assertThat(blueprint.integrity().total()).isZero();
        assertThat(blueprint.frozen()).isTrue();
        assertThat(blueprint.verdict()).isEqualTo(
                "READY_FOR_PHASE_13D4C5_AUDIT_ONLY_SEMANTICS_IMPLEMENTATION");
        assertThat(blueprint.nextPhaseAllowed()).isTrue();
    }

    private CompositionKeySpecificSemanticsDesign.CompositionKeySemantics key(TeamCompositionContext context) {
        return blueprint.keys().stream().filter(x -> x.applicationKey().context() == context).findFirst().orElseThrow();
    }

    private void assertDistinctChannels(CompositionKeySpecificSemanticsDesign.CompositionKeySemantics key) {
        assertThat(key.winnerChannelState()).isEqualTo(
                CompositionKeySpecificSemanticsDesign.CompositionChannelState.DEFINED_UNCALIBRATED);
        assertThat(key.severityChannelState()).isEqualTo(
                CompositionKeySpecificSemanticsDesign.CompositionChannelState.DEFINED_UNCALIBRATED);
        assertThat(key.winnerScoreApplicationMode()).isNotEqualTo(key.severityScoreApplicationMode());
    }

    private String metric(String name) {
        return CompositionKeySpecificSemanticsDesign.summary(blueprint).stream().skip(1)
                .filter(row -> row.get(0).equals(name)).map(row -> row.get(1)).findFirst().orElseThrow();
    }
}
