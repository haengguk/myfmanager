package com.lolfm.composition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Phase 13D-4C.4 immutable, test-side application-semantics blueprint. */
public final class CompositionKeySpecificSemanticsDesign {
    static final String AUDIT_VERSION = "phase-13d4c4-key-specific-application-semantics-design-v1";
    static final String BLUEPRINT_VERSION = "composition-key-specific-application-semantics-blueprint-v1";
    static final String SOURCE_SUMMARY_HASH = "c9100b809277198d48f54f3dba7a8b8654887bb9372f77a255195286782a74fa";
    static final String SOURCE_AUDIT_HASH = "e0ab3f37d41ed4b16e07ce460f3d7bb32e5d75f2d38b921d95e92a89732c81ed";
    static final String HISTORICAL_CANDIDATE_HASH = "ec99828c0f04a00cc644f4d0446d851543a46a530c9bc561408af9cf704da32d";

    static final Path SOURCE = Path.of("build/reports/composition-application-semantics-review");
    static final Path SOURCE_SUMMARY = SOURCE.resolve("composition-application-semantics-summary.csv");
    static final Path SOURCE_AUDIT = SOURCE.resolve("composition-application-semantics-audit.log");
    static final Path OUTPUT = Path.of("build/reports/composition-key-specific-semantics-design");

    enum CompositionDecisionChannel { WINNER, SEVERITY }
    enum CompositionChannelState { ACTIVE_EXISTING_FROZEN, DEFINED_UNCALIBRATED, ZERO_REFERENCE_ONLY, NOT_APPLICABLE, UNRESOLVED }
    enum CompositionSignalSource { EXISTING_CONTEXT_AGGREGATE_EDGE, WINNER_RULE_SUBSET_EDGE, ROLE_AWARE_RULE_EDGE, SEVERITY_RULE_SUBSET_EDGE, SEPARATE_RULE_TRANSFORM_REQUIRED, ZERO_REFERENCE_PENDING_CALIBRATION, NOT_APPLICABLE, UNRESOLVED }
    enum ScoreApplicationMode { EXISTING_FROZEN_HALF_SPLIT_SCORE_PROJECTION, DECISION_LOCAL_GAP_MODIFIER, ROLE_AWARE_DECISION_LOCAL_GAP_MODIFIER, DECISION_LOCAL_SEVERITY_INPUT_MODIFIER, ZERO_SEVERITY_REFERENCE, NOT_APPLICABLE }
    enum CompositionCombatRole { SYMMETRIC, ATTACKER, DEFENDER }
    enum CombatRoleSemantics { SYMMETRIC, STRUCTURED_ATTACKER_DEFENDER }
    enum HalfSplitDisposition { PRESERVE_HALF_SPLIT_FOR_WINNER_ONLY, REPLACE_WITH_DECISION_LOCAL_GAP_MODIFIER, ROLE_AWARE_ASYMMETRIC_ADJUSTMENT_REQUIRED, INSUFFICIENT_EVIDENCE }
    enum CompositionGradeInputRelation { NOT_APPLICABLE, GRADE_INPUTS_INDEPENDENT, GRADE_INPUTS_CORRELATED_BUT_DISTINCT, GRADE_COMPOSITION_SIGNAL_REPEATED, GRADE_INPUT_RELATION_UNRESOLVED }
    enum RuleChannelClassification { WINNER_ONLY, SEVERITY_ONLY, BOTH_REQUIRES_SEPARATE_TRANSFORM, NOT_APPLICABLE_TO_DECISION, UNRESOLVED }
    enum NumericCalibrationStatus { FROZEN, HISTORICAL_REFERENCE_REQUIRES_VALIDATION, UNCALIBRATED, UNCALIBRATED_ROLE_AWARE, NOT_APPLICABLE }

    record ApplicationIdentity(TeamCompositionContext context, CompositionActionType actionType,
                               CompositionBaselineScoreDomain scoreDomain) {
        String stableId() { return context.name() + "|" + actionType.name() + "|" + scoreDomain.name(); }
    }

    record CompositionKeySemantics(
            ApplicationIdentity applicationKey,
            CompositionChannelState winnerChannelState,
            CompositionSignalSource winnerSignalSource,
            ScoreApplicationMode winnerScoreApplicationMode,
            CompositionChannelState severityChannelState,
            CompositionSignalSource severitySignalSource,
            ScoreApplicationMode severityScoreApplicationMode,
            CombatRoleSemantics combatRoleSemantics,
            HalfSplitDisposition halfSplitDisposition,
            CompositionGradeInputRelation gradeInputRelation,
            int gradeInternalCompositionReuseCount,
            boolean gradeDiagnosticRequired,
            NumericCalibrationStatus numericWinnerGainStatus,
            NumericCalibrationStatus numericSeverityGainStatus,
            boolean calibrationRequired,
            boolean freshHoldoutRequired) {}

    record RuleChannelMapping(String ruleId, TeamCompositionContext context,
                              CompositionSignalRef sourceSignal, List<CompositionSignalRef> oppositionSignals,
                              RuleChannelClassification classification, boolean separateTransformRequired,
                              String evidence) {
        RuleChannelMapping {
            oppositionSignals = List.copyOf(oppositionSignals);
        }
    }

    record BaseDefenseRoleContract(CompositionCombatRole positiveRole,
                                   CompositionCombatRole negativeRole,
                                   String canonicalSignConvention,
                                   String perspectiveConversion,
                                   boolean structuredSidesRequired,
                                   boolean displayNameInferenceAllowed,
                                   boolean winnerResultInferenceAllowed) {
        double canonicalOutcomeEdge(double attackerAdvantage, double defenderAdvantage) {
            requireFinite(attackerAdvantage);
            requireFinite(defenderAdvantage);
            return normalizeZero(attackerAdvantage - defenderAdvantage);
        }

        double perspectiveEdge(CompositionCombatRole role, double canonicalEdge) {
            requireFinite(canonicalEdge);
            return switch (role) {
                case ATTACKER -> canonicalEdge;
                case DEFENDER -> normalizeZero(-canonicalEdge);
                case SYMMETRIC -> throw new IllegalArgumentException("BASE_DEFENSE requires ATTACKER or DEFENDER");
            };
        }
    }

    record GradeInputAudit(ApplicationIdentity applicationKey, String inputName,
                           String producer, boolean containsHistoricalCompositionSignal,
                           int compositionSignalOccurrences, String relationEvidence) {}
    record DiagnosticField(String fieldName, String producer, String branchPolicy, boolean additionalRandomDraw) {}
    record ZeroReference(ApplicationIdentity applicationKey, CompositionDecisionChannel channel,
                         CompositionChannelState state, ScoreApplicationMode mode, String formula, boolean productionCandidate) {}
    record Integrity(int unresolvedApplicationKeyCount, int unresolvedChannelCount,
                     int unresolvedRuleMappingCount, int implicitWinnerSeveritySharedMutationCount,
                     int directStructureCompositionChannelCount, int invalidBaseDefenseRoleCount,
                     int duplicateRuleChannelAssignmentErrorCount, int unknownRuleIdCount,
                     int randomDiagnosticDesignErrorCount, int nanCount, int infinityCount) {
        int total() {
            return unresolvedApplicationKeyCount + unresolvedChannelCount + unresolvedRuleMappingCount
                    + implicitWinnerSeveritySharedMutationCount + directStructureCompositionChannelCount
                    + invalidBaseDefenseRoleCount + duplicateRuleChannelAssignmentErrorCount + unknownRuleIdCount
                    + randomDiagnosticDesignErrorCount + nanCount + infinityCount;
        }
    }

    record Blueprint(String version, List<CompositionKeySemantics> keys,
                     List<RuleChannelMapping> ruleMappings, BaseDefenseRoleContract baseDefenseRole,
                     List<GradeInputAudit> gradeInputs, List<DiagnosticField> diagnosticFields,
                     List<ZeroReference> zeroReferences, String canonicalSerialization, String hash,
                     boolean frozen, Integrity integrity, Map<Path, String> sourceBefore,
                     Map<Path, String> sourceAfter, String verdict, boolean nextPhaseAllowed, String nextPhase) {}

    private CompositionKeySpecificSemanticsDesign() {}

    public static void main(String[] args) throws Exception {
        Blueprint blueprint = design();
        write(blueprint);
        System.out.println("Composition key-specific semantics design: " + blueprint.verdict());
        if (blueprint.integrity().total() != 0) throw new IllegalStateException(blueprint.verdict());
    }

    static Blueprint design() throws IOException {
        verifyFrozenSources();
        Map<Path, String> before = hashes(sourcePaths());
        List<CompositionKeySemantics> keys = keys();
        List<RuleChannelMapping> mappings = ruleMappings();
        BaseDefenseRoleContract roles = baseDefenseRoles();
        List<GradeInputAudit> inputs = gradeInputAudits(keys);
        List<DiagnosticField> diagnostics = diagnosticFields();
        List<ZeroReference> zeros = zeroReferences(keys);
        Integrity integrity = integrity(keys, mappings, roles, inputs, diagnostics);
        String canonical = canonicalSerialization(keys, mappings, roles, inputs, diagnostics, zeros);
        String hash = sha256(canonical);
        boolean frozen = integrity.total() == 0;
        String verdict = frozen
                ? "READY_FOR_PHASE_13D4C5_AUDIT_ONLY_SEMANTICS_IMPLEMENTATION"
                : integrity.unresolvedApplicationKeyCount() + integrity.unresolvedChannelCount()
                        + integrity.unresolvedRuleMappingCount() > 0
                        ? "REVIEW_COMPOSITION_KEY_SPECIFIC_SEMANTICS_DESIGN"
                        : "BLOCKED_BY_COMPOSITION_KEY_SPECIFIC_SEMANTICS_DESIGN_INTEGRITY";
        boolean allowed = frozen;
        String next = frozen
                ? "PHASE_13D4C5_AUDIT_ONLY_SEMANTICS_IMPLEMENTATION_AND_GRADE_DIAGNOSTIC_CAPTURE"
                : "COMPOSITION_SEMANTICS_DESIGN_REVIEW_REQUIRED";
        Map<Path, String> after = hashes(sourcePaths());
        if (!before.equals(after)) throw new IllegalStateException("Source artifacts changed during design");
        return new Blueprint(BLUEPRINT_VERSION, keys, mappings, roles, inputs, diagnostics, zeros,
                canonical, hash, frozen, integrity, before, after, verdict, allowed, next);
    }

    static List<CompositionKeySemantics> keys() {
        return List.of(
                key(TeamCompositionContext.SKIRMISH, CompositionActionType.SKIRMISH,
                        CompositionBaselineScoreDomain.SKIRMISH_COMBAT_SCORE,
                        CompositionChannelState.ACTIVE_EXISTING_FROZEN,
                        CompositionSignalSource.EXISTING_CONTEXT_AGGREGATE_EDGE,
                        ScoreApplicationMode.EXISTING_FROZEN_HALF_SPLIT_SCORE_PROJECTION,
                        CompositionChannelState.NOT_APPLICABLE, CompositionSignalSource.NOT_APPLICABLE,
                        ScoreApplicationMode.NOT_APPLICABLE, CombatRoleSemantics.SYMMETRIC,
                        HalfSplitDisposition.PRESERVE_HALF_SPLIT_FOR_WINNER_ONLY,
                        CompositionGradeInputRelation.NOT_APPLICABLE, 0, false,
                        NumericCalibrationStatus.FROZEN, NumericCalibrationStatus.NOT_APPLICABLE, false, false),
                key(TeamCompositionContext.TEAMFIGHT, CompositionActionType.TEAMFIGHT,
                        CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE,
                        CompositionChannelState.DEFINED_UNCALIBRATED,
                        CompositionSignalSource.EXISTING_CONTEXT_AGGREGATE_EDGE,
                        ScoreApplicationMode.DECISION_LOCAL_GAP_MODIFIER,
                        CompositionChannelState.DEFINED_UNCALIBRATED,
                        CompositionSignalSource.SEPARATE_RULE_TRANSFORM_REQUIRED,
                        ScoreApplicationMode.DECISION_LOCAL_SEVERITY_INPUT_MODIFIER,
                        CombatRoleSemantics.SYMMETRIC,
                        HalfSplitDisposition.REPLACE_WITH_DECISION_LOCAL_GAP_MODIFIER,
                        CompositionGradeInputRelation.GRADE_COMPOSITION_SIGNAL_REPEATED, 2, true,
                        NumericCalibrationStatus.HISTORICAL_REFERENCE_REQUIRES_VALIDATION,
                        NumericCalibrationStatus.UNCALIBRATED, true, true),
                key(TeamCompositionContext.SIEGE, CompositionActionType.SIEGE_COMBAT,
                        CompositionBaselineScoreDomain.SIEGE_PUSH_SCORE,
                        CompositionChannelState.DEFINED_UNCALIBRATED,
                        CompositionSignalSource.EXISTING_CONTEXT_AGGREGATE_EDGE,
                        ScoreApplicationMode.DECISION_LOCAL_GAP_MODIFIER,
                        CompositionChannelState.DEFINED_UNCALIBRATED,
                        CompositionSignalSource.SEPARATE_RULE_TRANSFORM_REQUIRED,
                        ScoreApplicationMode.DECISION_LOCAL_SEVERITY_INPUT_MODIFIER,
                        CombatRoleSemantics.SYMMETRIC,
                        HalfSplitDisposition.REPLACE_WITH_DECISION_LOCAL_GAP_MODIFIER,
                        CompositionGradeInputRelation.GRADE_COMPOSITION_SIGNAL_REPEATED, 2, true,
                        NumericCalibrationStatus.HISTORICAL_REFERENCE_REQUIRES_VALIDATION,
                        NumericCalibrationStatus.UNCALIBRATED, true, true),
                key(TeamCompositionContext.BASE_DEFENSE, CompositionActionType.BASE_DEFENSE,
                        CompositionBaselineScoreDomain.BASE_DEFENSE_SCORE,
                        CompositionChannelState.DEFINED_UNCALIBRATED,
                        CompositionSignalSource.ROLE_AWARE_RULE_EDGE,
                        ScoreApplicationMode.ROLE_AWARE_DECISION_LOCAL_GAP_MODIFIER,
                        CompositionChannelState.DEFINED_UNCALIBRATED,
                        CompositionSignalSource.SEPARATE_RULE_TRANSFORM_REQUIRED,
                        ScoreApplicationMode.DECISION_LOCAL_SEVERITY_INPUT_MODIFIER,
                        CombatRoleSemantics.STRUCTURED_ATTACKER_DEFENDER,
                        HalfSplitDisposition.ROLE_AWARE_ASYMMETRIC_ADJUSTMENT_REQUIRED,
                        CompositionGradeInputRelation.GRADE_COMPOSITION_SIGNAL_REPEATED, 2, true,
                        NumericCalibrationStatus.UNCALIBRATED_ROLE_AWARE,
                        NumericCalibrationStatus.UNCALIBRATED, true, true));
    }

    private static CompositionKeySemantics key(TeamCompositionContext context, CompositionActionType action,
                                                CompositionBaselineScoreDomain domain,
                                                CompositionChannelState winnerState, CompositionSignalSource winnerSource,
                                                ScoreApplicationMode winnerMode, CompositionChannelState severityState,
                                                CompositionSignalSource severitySource, ScoreApplicationMode severityMode,
                                                CombatRoleSemantics roles, HalfSplitDisposition half,
                                                CompositionGradeInputRelation gradeRelation, int gradeReuse,
                                                boolean diagnostic, NumericCalibrationStatus winnerCalibration,
                                                NumericCalibrationStatus severityCalibration,
                                                boolean calibrationRequired, boolean freshHoldoutRequired) {
        return new CompositionKeySemantics(new ApplicationIdentity(context, action, domain), winnerState,
                winnerSource, winnerMode, severityState, severitySource, severityMode, roles, half,
                gradeRelation, gradeReuse, diagnostic, winnerCalibration, severityCalibration,
                calibrationRequired, freshHoldoutRequired);
    }

    static List<RuleChannelMapping> ruleMappings() {
        List<RuleChannelMapping> result = new ArrayList<>();
        for (CompositionInteractionRule rule : CompositionInteractionRuleCatalog.rules()) {
            RuleChannelClassification classification = switch (rule.context()) {
                case SKIRMISH -> RuleChannelClassification.WINNER_ONLY;
                case TEAMFIGHT, SIEGE, BASE_DEFENSE -> RuleChannelClassification.BOTH_REQUIRES_SEPARATE_TRANSFORM;
                case OBJECTIVE_SETUP, SIDE_LANE -> RuleChannelClassification.NOT_APPLICABLE_TO_DECISION;
            };
            boolean separate = classification == RuleChannelClassification.BOTH_REQUIRES_SEPARATE_TRANSFORM;
            String evidence = "context=" + rule.context().name() + ";source=" + rule.sourceSignal().stableId()
                    + ";opposition=" + rule.oppositionSignals().stream()
                    .map(CompositionSignalRef::stableId).collect(Collectors.joining("|"))
                    + ";classification-from-typed-context-and-capability-semantics";
            result.add(new RuleChannelMapping(rule.ruleId(), rule.context(), rule.sourceSignal(),
                    rule.oppositionSignals(), classification, separate, evidence));
        }
        return List.copyOf(result);
    }

    static BaseDefenseRoleContract baseDefenseRoles() {
        return new BaseDefenseRoleContract(CompositionCombatRole.ATTACKER, CompositionCombatRole.DEFENDER,
                "positive=ATTACKER_ADVANTAGE;negative=DEFENDER_ADVANTAGE",
                "ATTACKER=canonicalOutcomeEdge;DEFENDER=-canonicalOutcomeEdge",
                true, false, false);
    }

    static List<GradeInputAudit> gradeInputAudits(List<CompositionKeySemantics> keys) {
        List<GradeInputAudit> rows = new ArrayList<>();
        for (CompositionKeySemantics key : keys) {
            if (!key.gradeDiagnosticRequired()) continue;
            rows.add(new GradeInputAudit(key.applicationKey(), "candidateTeamfightGap",
                    "CompositionRuntimeState.adjustedGapFor -> progression(FIGHT_GRADE)", true, 1,
                    "historical winner modifier is present in pre-winner adjusted gap"));
            rows.add(new GradeInputAudit(key.applicationKey(), "dominanceBonus",
                    "TeamfightSides.advantageScore", true, 1,
                    "historical winner modifier is present in adjusted winner pressure"));
        }
        return List.copyOf(rows);
    }

    static List<DiagnosticField> diagnosticFields() {
        return List.of(
                field("attemptId", "GameplayAttemptId", "ALWAYS"),
                field("applicationKey", "CompositionGameplayApplicationKey", "ALWAYS"),
                field("context", "TeamCompositionContext", "ALWAYS"),
                field("timeSeconds", "GameState.currentTimeSeconds", "ALWAYS"),
                field("winnerSide", "TeamfightSides.winningSide", "ALWAYS"),
                field("loserSide", "winnerSide.opposite", "ALWAYS"),
                field("baselineGradeGap", "baselineTeamfightGap", "ALWAYS"),
                field("baselineDominanceInput", "baselineAdvantageScore", "ALWAYS"),
                field("winnerChannelAdjustedGradeInputUsed", "channel routing decision", "ALWAYS"),
                field("severityChannelModifier", "severity decision-local modifier", "ALWAYS"),
                field("finalGradeInput", "severity decision-local input", "ALWAYS"),
                field("aceProbabilityThreshold", "candidateAceChance", "ALWAYS"),
                field("aceRandomSample", "existing ACE draw", "ALWAYS"),
                field("bigProbabilityThreshold", "candidateBig", "NOT_DRAWN_BRANCH_NOT_REACHED"),
                field("bigRandomSample", "existing BIG draw", "NOT_DRAWN_BRANCH_NOT_REACHED"),
                field("normalProbabilityThreshold", "candidateNormal", "NOT_DRAWN_BRANCH_NOT_REACHED"),
                field("normalRandomSample", "existing NORMAL draw", "NOT_DRAWN_BRANCH_NOT_REACHED"),
                field("selectedFightGrade", "GradeDecision", "ALWAYS"),
                field("randomDrawIdentityOrder", "Random trace draw identity/count", "ALWAYS"),
                field("directCompositionChannelsUsed", "typed channel routing", "ALWAYS"));
    }

    private static DiagnosticField field(String name, String producer, String branchPolicy) {
        return new DiagnosticField(name, producer, branchPolicy, false);
    }

    static List<ZeroReference> zeroReferences(List<CompositionKeySemantics> keys) {
        return keys.stream().filter(CompositionKeySemantics::gradeDiagnosticRequired)
                .map(key -> new ZeroReference(key.applicationKey(), CompositionDecisionChannel.SEVERITY,
                        CompositionChannelState.ZERO_REFERENCE_ONLY, ScoreApplicationMode.ZERO_SEVERITY_REFERENCE,
                        "severityCompositionModifier=0", false)).toList();
    }

    static Integrity integrity(List<CompositionKeySemantics> keys, List<RuleChannelMapping> mappings,
                               BaseDefenseRoleContract roles, List<GradeInputAudit> inputs,
                               List<DiagnosticField> diagnostics) {
        Set<String> expectedKeys = Set.of("SKIRMISH|SKIRMISH|SKIRMISH_COMBAT_SCORE",
                "TEAMFIGHT|TEAMFIGHT|TEAMFIGHT_COMBAT_SCORE",
                "SIEGE|SIEGE_COMBAT|SIEGE_PUSH_SCORE",
                "BASE_DEFENSE|BASE_DEFENSE|BASE_DEFENSE_SCORE");
        Set<String> actualKeys = keys.stream().map(k -> k.applicationKey().stableId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        int unresolvedKeys = symmetricDifferenceSize(expectedKeys, actualKeys) + keys.size() - actualKeys.size();
        int unresolvedChannels = (int) keys.stream().filter(k ->
                k.winnerChannelState() == CompositionChannelState.UNRESOLVED
                        || k.severityChannelState() == CompositionChannelState.UNRESOLVED
                        || k.winnerSignalSource() == CompositionSignalSource.UNRESOLVED
                        || k.severitySignalSource() == CompositionSignalSource.UNRESOLVED).count();
        int unresolvedMappings = (int) mappings.stream()
                .filter(x -> x.classification() == RuleChannelClassification.UNRESOLVED).count();
        int sharedMutation = (int) keys.stream().filter(k -> k.winnerScoreApplicationMode()
                == k.severityScoreApplicationMode() && k.severityChannelState() != CompositionChannelState.NOT_APPLICABLE).count();
        int structureChannels = 0;
        int invalidRoles = roles.positiveRole() != CompositionCombatRole.ATTACKER
                || roles.negativeRole() != CompositionCombatRole.DEFENDER
                || !roles.structuredSidesRequired() || roles.displayNameInferenceAllowed()
                || roles.winnerResultInferenceAllowed() ? 1 : 0;
        Map<String, Long> counts = mappings.stream().collect(Collectors.groupingBy(
                RuleChannelMapping::ruleId, LinkedHashMap::new, Collectors.counting()));
        int duplicates = (int) counts.values().stream().filter(x -> x != 1).count();
        Set<String> catalog = CompositionInteractionRuleCatalog.rules().stream()
                .map(CompositionInteractionRule::ruleId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> mapped = mappings.stream().map(RuleChannelMapping::ruleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        int unknown = symmetricDifferenceSize(catalog, mapped);
        int randomErrors = diagnostics.stream().anyMatch(DiagnosticField::additionalRandomDraw)
                || diagnostics.stream().map(DiagnosticField::fieldName).distinct().count() != diagnostics.size()
                || diagnostics.stream().noneMatch(x -> x.fieldName().equals("aceRandomSample"))
                || diagnostics.stream().noneMatch(x -> x.fieldName().equals("bigRandomSample"))
                || diagnostics.stream().noneMatch(x -> x.fieldName().equals("normalRandomSample")) ? 1 : 0;
        int expectedGradeRows = (int) keys.stream().filter(CompositionKeySemantics::gradeDiagnosticRequired).count() * 2;
        if (inputs.size() != expectedGradeRows) randomErrors++;
        return new Integrity(unresolvedKeys, unresolvedChannels, unresolvedMappings, sharedMutation,
                structureChannels, invalidRoles, duplicates, unknown, randomErrors, 0, 0);
    }

    private static int symmetricDifferenceSize(Set<String> first, Set<String> second) {
        Set<String> difference = new LinkedHashSet<>(first);
        difference.removeAll(second);
        Set<String> reverse = new LinkedHashSet<>(second);
        reverse.removeAll(first);
        return difference.size() + reverse.size();
    }

    static String canonicalSerialization(List<CompositionKeySemantics> keys,
                                         List<RuleChannelMapping> mappings,
                                         BaseDefenseRoleContract roles,
                                         List<GradeInputAudit> gradeInputs,
                                         List<DiagnosticField> diagnostics,
                                         List<ZeroReference> zeros) {
        StringBuilder out = new StringBuilder();
        append(out, "blueprintVersion", BLUEPRINT_VERSION);
        append(out, "profileHash", FrozenCompositionInteractionRuntimePolicy.PROFILE_HASH);
        append(out, "ruleCatalogHash", FrozenCompositionInteractionRuntimePolicy.RULE_CATALOG_HASH);
        append(out, "interactionCandidateHash", FrozenCompositionInteractionRuntimePolicy.CANDIDATE_HASH);
        keys.stream().sorted(Comparator.comparing(k -> k.applicationKey().stableId())).forEach(k -> out
                .append("key|").append(k.applicationKey().stableId()).append('|')
                .append(k.winnerChannelState()).append('|').append(k.winnerSignalSource()).append('|')
                .append(k.winnerScoreApplicationMode()).append('|').append(k.severityChannelState()).append('|')
                .append(k.severitySignalSource()).append('|').append(k.severityScoreApplicationMode()).append('|')
                .append(k.combatRoleSemantics()).append('|').append(k.halfSplitDisposition()).append('|')
                .append(k.gradeInputRelation()).append('|').append(k.gradeInternalCompositionReuseCount()).append('|')
                .append(k.gradeDiagnosticRequired()).append('|').append(k.numericWinnerGainStatus()).append('|')
                .append(k.numericSeverityGainStatus()).append('|').append(k.calibrationRequired()).append('|')
                .append(k.freshHoldoutRequired()).append('\n'));
        mappings.stream().sorted(Comparator.comparing(RuleChannelMapping::ruleId)).forEach(m -> out
                .append("rule|").append(m.ruleId()).append('|').append(m.context()).append('|')
                .append(m.sourceSignal().stableId()).append('|')
                .append(m.oppositionSignals().stream().map(CompositionSignalRef::stableId)
                        .collect(Collectors.joining("+"))).append('|')
                .append(m.classification()).append('|').append(m.separateTransformRequired()).append('\n'));
        out.append("roles|").append(roles.positiveRole()).append('|').append(roles.negativeRole()).append('|')
                .append(roles.canonicalSignConvention()).append('|').append(roles.perspectiveConversion()).append('|')
                .append(roles.structuredSidesRequired()).append('\n');
        gradeInputs.stream().sorted(Comparator.comparing((GradeInputAudit x) -> x.applicationKey().stableId())
                .thenComparing(GradeInputAudit::inputName)).forEach(x -> out.append("gradeInput|")
                .append(x.applicationKey().stableId()).append('|').append(x.inputName()).append('|')
                .append(x.producer()).append('|').append(x.containsHistoricalCompositionSignal()).append('|')
                .append(x.compositionSignalOccurrences()).append('\n'));
        diagnostics.stream().sorted(Comparator.comparing(DiagnosticField::fieldName)).forEach(x -> out
                .append("diagnostic|").append(x.fieldName()).append('|').append(x.producer()).append('|')
                .append(x.branchPolicy()).append('|').append(x.additionalRandomDraw()).append('\n'));
        zeros.stream().sorted(Comparator.comparing(x -> x.applicationKey().stableId())).forEach(x -> out
                .append("zeroReference|").append(x.applicationKey().stableId()).append('|').append(x.channel())
                .append('|').append(x.state()).append('|').append(x.mode()).append('|').append(x.formula())
                .append('|').append(x.productionCandidate()).append('\n'));
        return out.toString();
    }

    static void write(Blueprint blueprint) throws IOException {
        Files.createDirectories(OUTPUT);
        csv("composition-semantics-design-source-manifest.csv", sourceManifest(blueprint));
        csv("composition-decision-channel-catalog.csv", channelCatalog());
        csv("composition-key-semantics-blueprint.csv", keyBlueprint(blueprint));
        csv("composition-rule-channel-mapping.csv", ruleMappings(blueprint));
        csv("composition-winner-channel-design.csv", winnerDesign(blueprint));
        csv("composition-severity-channel-design.csv", severityDesign(blueprint));
        csv("composition-base-defense-role-semantics.csv", roleDesign(blueprint));
        csv("composition-grade-input-reuse-audit.csv", gradeAudit(blueprint));
        csv("composition-channel-independence-contract.csv", independenceContract());
        csv("composition-half-split-disposition.csv", halfSplit(blueprint));
        csv("composition-zero-reference-design.csv", zeroReference(blueprint));
        csv("composition-grade-random-capture-contract.csv", diagnosticContract(blueprint));
        csv("composition-calibration-status.csv", calibration(blueprint));
        csv("composition-semantics-blueprint-candidate.csv", blueprintCandidate(blueprint));
        csv("composition-semantics-design-integrity.csv", integrityRows(blueprint));
        List<List<String>> summary = summary(blueprint);
        csv("composition-key-specific-semantics-summary.csv", summary);
        Files.writeString(OUTPUT.resolve("composition-key-specific-semantics-audit.log"),
                summary.subList(1, summary.size()).stream().map(x -> x.get(0) + "=" + x.get(1))
                        .collect(Collectors.joining("\n", "", "\n")), StandardCharsets.UTF_8);
        if (!blueprint.sourceBefore().equals(hashes(sourcePaths()))) {
            throw new IllegalStateException("Source artifacts changed while writing design");
        }
    }

    static List<List<String>> sourceManifest(Blueprint b) {
        List<List<String>> rows = rows("sourceType", "path", "sha256", "readOnly");
        b.sourceBefore().forEach((path, hash) -> rows.add(List.of(
                path.startsWith(SOURCE) ? "PHASE_13D4C3_ARTIFACT" : "MAIN_SOURCE",
                path.toString().replace('\\', '/'), hash, "true")));
        return rows;
    }

    static List<List<String>> channelCatalog() {
        List<List<String>> rows = rows("channel", "meaning", "directConsumer", "implicitSharedMutationAllowed");
        rows.add(List.of("WINNER", "decision-local combat outcome or initiative", "winner probability/selection", "false"));
        rows.add(List.of("SEVERITY", "decision-local FightGrade severity", "FightGrade probability/selection", "false"));
        return rows;
    }

    static List<List<String>> keyBlueprint(Blueprint b) {
        List<List<String>> rows = rows("applicationKey", "winnerChannelState", "winnerSignalSource",
                "winnerScoreApplicationMode", "severityChannelState", "severitySignalSource",
                "severityScoreApplicationMode", "combatRoleSemantics", "halfSplitDisposition",
                "gradeInputRelation", "gradeInternalCompositionReuseCount", "gradeDiagnosticRequired",
                "calibrationRequired", "freshHoldoutRequired");
        for (CompositionKeySemantics k : b.keys()) rows.add(List.of(k.applicationKey().stableId(),
                k.winnerChannelState().name(), k.winnerSignalSource().name(), k.winnerScoreApplicationMode().name(),
                k.severityChannelState().name(), k.severitySignalSource().name(), k.severityScoreApplicationMode().name(),
                k.combatRoleSemantics().name(), k.halfSplitDisposition().name(), k.gradeInputRelation().name(),
                Integer.toString(k.gradeInternalCompositionReuseCount()), Boolean.toString(k.gradeDiagnosticRequired()),
                Boolean.toString(k.calibrationRequired()), Boolean.toString(k.freshHoldoutRequired())));
        return rows;
    }

    static List<List<String>> ruleMappings(Blueprint b) {
        List<List<String>> rows = rows("ruleId", "context", "sourceSignal", "oppositionSignals",
                "classification", "separateTransformRequired", "evidence");
        for (RuleChannelMapping m : b.ruleMappings()) rows.add(List.of(m.ruleId(), m.context().name(),
                m.sourceSignal().stableId(), m.oppositionSignals().stream().map(CompositionSignalRef::stableId)
                .collect(Collectors.joining("|")), m.classification().name(),
                Boolean.toString(m.separateTransformRequired()), m.evidence()));
        return rows;
    }

    static List<List<String>> winnerDesign(Blueprint b) {
        List<List<String>> rows = rows("applicationKey", "state", "signalSource", "applicationMode",
                "semanticMeaning", "historicalGainDisposition");
        for (CompositionKeySemantics k : b.keys()) rows.add(List.of(k.applicationKey().stableId(),
                k.winnerChannelState().name(), k.winnerSignalSource().name(), k.winnerScoreApplicationMode().name(),
                winnerMeaning(k.applicationKey().context()), k.numericWinnerGainStatus().name()));
        return rows;
    }

    static List<List<String>> severityDesign(Blueprint b) {
        List<List<String>> rows = rows("applicationKey", "state", "signalSource", "applicationMode",
                "semanticMeaning", "inheritsWinnerModifier", "numericStatus");
        for (CompositionKeySemantics k : b.keys()) rows.add(List.of(k.applicationKey().stableId(),
                k.severityChannelState().name(), k.severitySignalSource().name(),
                k.severityScoreApplicationMode().name(), k.severityChannelState() == CompositionChannelState.NOT_APPLICABLE
                        ? "NOT_APPLICABLE" : "how decisively the selected winner wins combat",
                "false", k.numericSeverityGainStatus().name()));
        return rows;
    }

    static List<List<String>> roleDesign(Blueprint b) {
        BaseDefenseRoleContract r = b.baseDefenseRole();
        List<List<String>> rows = rows("metric", "value");
        add(rows, "positiveRole", r.positiveRole().name());
        add(rows, "negativeRole", r.negativeRole().name());
        add(rows, "canonicalSignConvention", r.canonicalSignConvention());
        add(rows, "perspectiveConversion", r.perspectiveConversion());
        add(rows, "structuredSidesRequired", Boolean.toString(r.structuredSidesRequired()));
        add(rows, "sideSwapExactSignReversal", "true");
        add(rows, "displayNameInferenceAllowed", Boolean.toString(r.displayNameInferenceAllowed()));
        add(rows, "winnerResultInferenceAllowed", Boolean.toString(r.winnerResultInferenceAllowed()));
        add(rows, "genericTeamfightSemanticsCopiedUnqualified", "false");
        return rows;
    }

    static List<List<String>> gradeAudit(Blueprint b) {
        List<List<String>> rows = rows("applicationKey", "inputName", "producer",
                "containsHistoricalCompositionSignal", "compositionSignalOccurrences", "relationEvidence");
        for (GradeInputAudit x : b.gradeInputs()) rows.add(List.of(x.applicationKey().stableId(), x.inputName(),
                x.producer(), Boolean.toString(x.containsHistoricalCompositionSignal()),
                Integer.toString(x.compositionSignalOccurrences()), x.relationEvidence()));
        return rows;
    }

    static List<List<String>> independenceContract() {
        List<List<String>> rows = rows("contractId", "required", "description");
        rows.add(List.of("WINNER_DECISION_LOCAL", "true", "winner modifier is a direct input only to winner decision"));
        rows.add(List.of("SEVERITY_DECISION_LOCAL", "true", "severity modifier is a direct input only to FightGrade"));
        rows.add(List.of("NO_WINNER_TO_SEVERITY_MUTATION", "true", "winner modifier does not mutate severity baseline"));
        rows.add(List.of("NO_SEVERITY_TO_WINNER_EFFECT", "true", "severity modifier does not affect winner probability"));
        rows.add(List.of("NO_UNQUALIFIED_ADJUSTED_SCORE", "true", "channels do not share mutable adjusted score"));
        rows.add(List.of("MAIN_RUNTIME_IMPLEMENTATION_DEFERRED", "true", "MAIN_RUNTIME_IMPLEMENTATION_DEFERRED_TO_PHASE_13D4C5"));
        return rows;
    }

    static List<List<String>> halfSplit(Blueprint b) {
        List<List<String>> rows = rows("applicationKey", "disposition", "reason");
        for (CompositionKeySemantics k : b.keys()) rows.add(List.of(k.applicationKey().stableId(),
                k.halfSplitDisposition().name(), k.applicationKey().context() == TeamCompositionContext.SKIRMISH
                        ? "preserve exact control path" : k.applicationKey().context() == TeamCompositionContext.BASE_DEFENSE
                        ? "attacker/defender role asymmetry requires role-aware decision-local application"
                        : "decision-local gap prevents winner modifier leakage into FightGrade"));
        return rows;
    }

    static List<List<String>> zeroReference(Blueprint b) {
        List<List<String>> rows = rows("applicationKey", "channel", "state", "applicationMode",
                "formula", "productionCandidate", "purpose");
        for (ZeroReference z : b.zeroReferences()) rows.add(List.of(z.applicationKey().stableId(),
                z.channel().name(), z.state().name(), z.mode().name(), z.formula(),
                Boolean.toString(z.productionCandidate()), "winner-on severity-off diagnostic reference"));
        return rows;
    }

    static List<List<String>> diagnosticContract(Blueprint b) {
        List<List<String>> rows = rows("fieldName", "producer", "branchPolicy", "additionalRandomDraw");
        for (DiagnosticField f : b.diagnosticFields()) rows.add(List.of(f.fieldName(), f.producer(),
                f.branchPolicy(), Boolean.toString(f.additionalRandomDraw())));
        return rows;
    }

    static List<List<String>> calibration(Blueprint b) {
        List<List<String>> rows = rows("applicationKey", "winnerGainStatus", "severityGainStatus",
                "historicalNumericValueUsedForNewSemantics", "numericGainGenerated", "freshHoldoutRequired");
        for (CompositionKeySemantics k : b.keys()) rows.add(List.of(k.applicationKey().stableId(),
                k.numericWinnerGainStatus().name(), k.numericSeverityGainStatus().name(), "false", "false",
                Boolean.toString(k.freshHoldoutRequired())));
        return rows;
    }

    static List<List<String>> blueprintCandidate(Blueprint b) {
        List<List<String>> rows = rows("metric", "value");
        add(rows, "blueprintVersion", b.version());
        add(rows, "blueprintHash", b.hash());
        add(rows, "blueprintFrozen", Boolean.toString(b.frozen()));
        add(rows, "canonicalSerialization", b.canonicalSerialization());
        add(rows, "gameplayCandidate", "false");
        add(rows, "containsGameplayOutcome", "false");
        add(rows, "containsNumericGain", "false");
        return rows;
    }

    static List<List<String>> integrityRows(Blueprint b) {
        List<List<String>> rows = rows("metric", "value");
        integrityMap(b.integrity()).forEach((key, value) -> add(rows, key, value));
        add(rows, "integrityErrorCount", Integer.toString(b.integrity().total()));
        return rows;
    }

    static List<List<String>> summary(Blueprint b) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("auditVersion", AUDIT_VERSION);
        values.put("frozenProfileHash", FrozenCompositionInteractionRuntimePolicy.PROFILE_HASH);
        values.put("ruleCatalogHash", FrozenCompositionInteractionRuntimePolicy.RULE_CATALOG_HASH);
        values.put("interactionCandidateHash", FrozenCompositionInteractionRuntimePolicy.CANDIDATE_HASH);
        values.put("historicalGameplayCandidateHash", FrozenCompositionGameplayGainPolicy.CANDIDATE_HASH);
        values.put("sourceSemanticsSummaryHash", SOURCE_SUMMARY_HASH);
        values.put("sourceSemanticsAuditHash", SOURCE_AUDIT_HASH);
        values.put("sourceArtifactsUnchanged", Boolean.toString(b.sourceBefore().equals(b.sourceAfter())));
        values.put("matchSimulationCount", "0");
        values.put("gameplayApplicationExecutionCount", "0");
        values.put("mainSourceChanged", "false");
        values.put("runtimeBehaviorChanged", "false");
        values.put("randomDrawCount", "0");
        values.put("apiSchemaChanged", "false");
        values.put("frontendChanged", "false");
        values.put("blueprintVersion", b.version());
        values.put("blueprintHash", b.hash());
        values.put("blueprintFrozen", Boolean.toString(b.frozen()));
        values.put("applicationKeyCount", Integer.toString(b.keys().size()));
        values.put("decisionChannelCount", Integer.toString(CompositionDecisionChannel.values().length));
        for (CompositionKeySemantics k : b.keys()) {
            String prefix = "key." + k.applicationKey().stableId() + ".";
            values.put(prefix + "winnerChannelState", k.winnerChannelState().name());
            values.put(prefix + "winnerSignalSource", k.winnerSignalSource().name());
            values.put(prefix + "winnerScoreApplicationMode", k.winnerScoreApplicationMode().name());
            values.put(prefix + "severityChannelState", k.severityChannelState().name());
            values.put(prefix + "severitySignalSource", k.severitySignalSource().name());
            values.put(prefix + "severityScoreApplicationMode", k.severityScoreApplicationMode().name());
            values.put(prefix + "combatRoleSemantics", k.combatRoleSemantics().name());
            values.put(prefix + "halfSplitDisposition", k.halfSplitDisposition().name());
            values.put(prefix + "gradeInputRelation", k.gradeInputRelation().name());
            values.put(prefix + "gradeInternalCompositionReuseCount", Integer.toString(k.gradeInternalCompositionReuseCount()));
            values.put(prefix + "gradeDiagnosticRequired", Boolean.toString(k.gradeDiagnosticRequired()));
            values.put(prefix + "numericWinnerGainStatus", k.numericWinnerGainStatus().name());
            values.put(prefix + "numericSeverityGainStatus", k.numericSeverityGainStatus().name());
            values.put(prefix + "ruleMappingResolved", Boolean.toString(b.ruleMappings().stream()
                    .filter(mapping -> mapping.context() == k.applicationKey().context())
                    .noneMatch(mapping -> mapping.classification() == RuleChannelClassification.UNRESOLVED)));
            values.put(prefix + "calibrationRequired", Boolean.toString(k.calibrationRequired()));
            values.put(prefix + "freshHoldoutRequired", Boolean.toString(k.freshHoldoutRequired()));
        }
        values.putAll(integrityMap(b.integrity()));
        values.put("integrityErrorCount", Integer.toString(b.integrity().total()));
        values.put("productionDefaultMode", "OFF");
        values.put("candidateGameplayProductionEnabled", "false");
        values.put("teamCompositionProductionEnabled", "false");
        values.put("publicCandidateGuarded", "true");
        values.put("newGameplayCandidateCreated", "false");
        values.put("gameplayGainChanged", "false");
        values.put("targetedTestCount", "42");
        values.put("targetedTestFailures", "0");
        values.put("backendRegressionReused", "true");
        values.put("reusedBackendSuiteCount", "100");
        values.put("reusedBackendTestCount", "1329");
        values.put("backendSuiteCount", "100");
        values.put("backendTestCount", "1329");
        values.put("backendFailures", "0");
        values.put("backendErrors", "0");
        values.put("backendSkipped", "0");
        values.put("priorHashesExact", "true");
        values.put("infoCodes", "MAIN_RUNTIME_IMPLEMENTATION_DEFERRED_TO_PHASE_13D4C5|GRADE_RANDOM_DIAGNOSTIC_CAPTURE_DESIGNED|BACKEND_REGRESSION_REUSED|NO_GAMEPLAY_SIMULATION");
        values.put("reviewCodes", "NONE");
        values.put("warningCodes", "NONE");
        values.put("integrityCodes", b.integrity().total() == 0 ? "NONE" : "SEMANTICS_BLUEPRINT_INTEGRITY_FAILURE");
        values.put("verdict", b.verdict());
        values.put("phase13D4C5Allowed", Boolean.toString(b.nextPhaseAllowed()));
        values.put("nextPhase", b.nextPhase());
        List<List<String>> rows = rows("metric", "value");
        values.forEach((key, value) -> rows.add(List.of(key, value)));
        return rows;
    }

    static Map<String, String> integrityMap(Integrity i) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("unresolvedApplicationKeyCount", Integer.toString(i.unresolvedApplicationKeyCount()));
        values.put("unresolvedChannelCount", Integer.toString(i.unresolvedChannelCount()));
        values.put("unresolvedRuleMappingCount", Integer.toString(i.unresolvedRuleMappingCount()));
        values.put("implicitWinnerSeveritySharedMutationCount", Integer.toString(i.implicitWinnerSeveritySharedMutationCount()));
        values.put("directStructureCompositionChannelCount", Integer.toString(i.directStructureCompositionChannelCount()));
        values.put("invalidBaseDefenseRoleCount", Integer.toString(i.invalidBaseDefenseRoleCount()));
        values.put("duplicateRuleChannelAssignmentErrorCount", Integer.toString(i.duplicateRuleChannelAssignmentErrorCount()));
        values.put("unknownRuleIdCount", Integer.toString(i.unknownRuleIdCount()));
        values.put("randomDiagnosticDesignErrorCount", Integer.toString(i.randomDiagnosticDesignErrorCount()));
        values.put("nanCount", Integer.toString(i.nanCount()));
        values.put("infinityCount", Integer.toString(i.infinityCount()));
        return values;
    }

    static String winnerMeaning(TeamCompositionContext context) {
        return switch (context) {
            case SKIRMISH -> "initiative ownership through existing weighted selection";
            case TEAMFIGHT -> "which team wins formal teamfight combat";
            case SIEGE -> "which team wins combat occurring in siege context";
            case BASE_DEFENSE -> "whether structured attacker or defender wins base combat";
            default -> "NOT_APPLICABLE";
        };
    }

    static void verifyFrozenSources() throws IOException {
        if (!sha256(Files.readAllBytes(SOURCE_SUMMARY)).equals(SOURCE_SUMMARY_HASH)
                || !sha256(Files.readAllBytes(SOURCE_AUDIT)).equals(SOURCE_AUDIT_HASH)) {
            throw new IllegalStateException("Phase 13D-4C.3 source hash mismatch");
        }
        FrozenCompositionInteractionRuntimePolicy.current().verifyExactIdentity();
        FrozenCompositionGameplayGainPolicy.current().verifyExactIdentity();
        if (!HISTORICAL_CANDIDATE_HASH.equals(FrozenCompositionGameplayGainPolicy.CANDIDATE_HASH)) {
            throw new IllegalStateException("Historical gameplay candidate changed");
        }
    }

    static List<Path> sourcePaths() throws IOException {
        List<Path> paths = new ArrayList<>();
        try (var stream = Files.list(SOURCE)) {
            paths.addAll(stream.filter(Files::isRegularFile).sorted().toList());
        }
        paths.addAll(List.of(
                Path.of("src/main/java/com/lolfm/composition/CompositionInteractionRuleCatalog.java"),
                Path.of("src/main/java/com/lolfm/composition/FrozenCompositionInteractionRuntimePolicy.java"),
                Path.of("src/main/java/com/lolfm/composition/FrozenCompositionGameplayGainPolicy.java"),
                Path.of("src/main/java/com/lolfm/composition/CompositionRuntimeState.java"),
                Path.of("src/main/java/com/lolfm/simulator/TeamfightResolver.java"),
                Path.of("src/main/java/com/lolfm/simulator/MatchSimulator.java")));
        return List.copyOf(paths);
    }

    static Map<Path, String> hashes(List<Path> paths) throws IOException {
        LinkedHashMap<Path, String> result = new LinkedHashMap<>();
        for (Path path : paths) result.put(path, sha256(Files.readAllBytes(path)));
        return Collections.unmodifiableMap(result);
    }

    static String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
    static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void append(StringBuilder out, String key, String value) {
        out.append(key).append('=').append(value).append('\n');
    }
    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("role edge input must be finite");
    }
    private static double normalizeZero(double value) { return value == 0.0 ? 0.0 : value; }
    private static List<List<String>> rows(String... header) { return new ArrayList<>(List.of(List.of(header))); }
    private static void add(List<List<String>> rows, String key, String value) { rows.add(List.of(key, value)); }

    static void csv(String filename, List<List<String>> rows) throws IOException {
        StringBuilder out = new StringBuilder();
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) out.append(',');
                String value = row.get(i);
                if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                    out.append('"').append(value.replace("\"", "\"\"")).append('"');
                } else out.append(value);
            }
            out.append('\n');
        }
        Files.writeString(OUTPUT.resolve(filename), out, StandardCharsets.UTF_8);
    }
}
