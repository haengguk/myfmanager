package com.lolfm.composition;

import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.champion.ChampionResourceSet;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.domain.Position;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.Lane;
import com.lolfm.simulator.ObjectiveType;
import com.lolfm.simulator.StructureKind;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Match-owned composition state. No resolver or static/global collection owns this data. */
public final class CompositionRuntimeState {
    private static final ChampionCompositionProfileCatalog PROFILES =
            ChampionResourceSet.loadDefault().composition();
    private final TeamCompositionGameplayMode mode;
    private final long matchSeed;
    private final FrozenCompositionInteractionRuntimePolicy frozenPolicy;
    private final FrozenCompositionGameplayGainPolicy gameplayGainPolicy;
    private final CompositionCandidateExecutionAuthorization authorization;
    private final CompositionSemanticsAuditExecutionAuthorization semanticsAuditAuthorization;
    private final CompositionKeySpecificCandidateAuditAuthorization keySpecificCandidateAuthorization;
    private final CompositionContextRouter router = new CompositionContextRouter();
    private final EnumMap<TeamCompositionContext, Double> blueEdges = new EnumMap<>(TeamCompositionContext.class);
    private final EnumMap<TeamCompositionContext, Double> redEdges = new EnumMap<>(TeamCompositionContext.class);
    private final List<CompositionShadowObservation> observations = new ArrayList<>();
    private final List<CompositionContextRouting> routings = new ArrayList<>();
    private final Map<GameplayAttemptId, TeamCompositionContext> primaryContextByAttempt = new HashMap<>();
    private final Map<GameplayAttemptId, TeamSide> perspectiveByAttempt = new HashMap<>();
    private final Set<ObservationKey> observationKeys = new HashSet<>();
    private final Set<ApplicationKey> applicationKeys = new HashSet<>();
    private final Set<CandidateApplicationKey> candidateApplicationKeys = new HashSet<>();
    private final Map<GameplayAttemptId, CandidateScoreAdjustment> candidateAdjustments = new HashMap<>();
    private final List<CompositionCandidateApplicationObservation> candidateApplications = new ArrayList<>();
    private final List<CompositionLocalDecisionComparison> localDecisionComparisons = new ArrayList<>();
    private final List<CompositionWinnerChannelObservation> winnerChannelObservations = new ArrayList<>();
    private final List<FightGradeDecisionDiagnostic> fightGradeDiagnostics = new ArrayList<>();
    private final List<BaseDefenseRoleRoutingDiagnostic> baseDefenseRoleRoutings = new ArrayList<>();
    private final List<CompositionWinnerDecisionProvenance> winnerDecisionProvenance = new ArrayList<>();
    private CompositionTeamLineup blueLineup;
    private CompositionTeamLineup redLineup;
    private TeamCompositionAnalysis blueAnalysis;
    private TeamCompositionAnalysis redAnalysis;
    private boolean initialized;
    private long nextAttemptSequence;
    private GameplayAttemptId lastActualAttemptId;
    private int lineupBuildCount;
    private int teamCompositionAnalysisCount;
    private int interactionAnalysisCount;
    private int contextEdgeCount;
    private int runtimeInteractionRecalculationCount;
    private int resolverEvaluationCount;
    private int triggerSuccessCount;
    private int actualAttemptCount;
    private int mappedActualAttemptCount;
    private int unmappedActualAttemptCount;
    private int shadowObservationCount;
    private int evaluationOnlyObservationCount;
    private int duplicateObservationCount;
    private int multiContextAttemptCount;
    private int conflictingPerspectiveCount;
    private int duplicateApplicationPointCount;
    private int gameplayApplicationCount;
    private int nonZeroModifierCount;
    private int deferredCandidateApplicationCount;

    public CompositionRuntimeState(TeamCompositionGameplayMode mode, long matchSeed) {
        this(mode, matchSeed, CompositionCandidateExecutionAuthorization.none());
    }

    public CompositionRuntimeState(TeamCompositionGameplayMode mode, long matchSeed,
                                   CompositionCandidateExecutionAuthorization authorization) {
        this(mode, matchSeed, authorization, CompositionSemanticsAuditExecutionAuthorization.none());
    }

    public CompositionRuntimeState(TeamCompositionGameplayMode mode, long matchSeed,
                                   CompositionCandidateExecutionAuthorization authorization,
                                   CompositionSemanticsAuditExecutionAuthorization semanticsAuditAuthorization) {
        this(mode, matchSeed, authorization, semanticsAuditAuthorization,
                CompositionKeySpecificCandidateAuditAuthorization.none());
    }

    public CompositionRuntimeState(TeamCompositionGameplayMode mode, long matchSeed,
                                   CompositionCandidateExecutionAuthorization authorization,
                                   CompositionSemanticsAuditExecutionAuthorization semanticsAuditAuthorization,
                                   CompositionKeySpecificCandidateAuditAuthorization keySpecificCandidateAuthorization) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.matchSeed = matchSeed;
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.semanticsAuditAuthorization = Objects.requireNonNull(semanticsAuditAuthorization, "semanticsAuditAuthorization");
        this.keySpecificCandidateAuthorization = Objects.requireNonNull(keySpecificCandidateAuthorization, "keySpecificCandidateAuthorization");
        if (keySpecificCandidateAuthorization.enabled()) {
            if (mode != TeamCompositionGameplayMode.SHADOW || authorization.auditOnly() || !semanticsAuditAuthorization.enabled()) {
                throw new CompositionGameplayConfigurationException("COMPOSITION_KEY_SPECIFIC_CANDIDATE_NOT_AUTHORIZED",
                        "Key-specific candidate audit requires SHADOW semantics and no historical candidate path");
            }
            keySpecificCandidateAuthorization.verifyExact();
        }
        if (semanticsAuditAuthorization.enabled()) {
            if (mode != TeamCompositionGameplayMode.SHADOW || authorization.auditOnly()) {
                throw new CompositionGameplayConfigurationException("COMPOSITION_HISTORICAL_CANDIDATE_AND_AUDIT_PATH_MIXED",
                        "Isolated semantics audit requires SHADOW mode without historical candidate authorization");
            }
            semanticsAuditAuthorization.verifyExact();
        }
        if (mode == TeamCompositionGameplayMode.PRODUCTION_V2) {
            if (authorization.auditOnly() || semanticsAuditAuthorization.enabled()
                    || keySpecificCandidateAuthorization.enabled()) {
                throw new CompositionGameplayConfigurationException(
                        "COMPOSITION_PRODUCTION_AUTHORIZATION_MIXED",
                        "Frozen V2 production execution cannot mix with candidate or audit authorization");
            }
            FrozenCompositionProductionCandidate.verifyExact();
            this.frozenPolicy = FrozenCompositionInteractionRuntimePolicy.current();
            this.gameplayGainPolicy = null;
        } else if (mode == TeamCompositionGameplayMode.CANDIDATE) {
            FrozenCompositionGameplayGainPolicy policy = FrozenCompositionGameplayGainPolicy.current();
            if (!authorization.auditOnly()) throw new CompositionGameplayConfigurationException(
                    "CANDIDATE_CONTEXT_GAINS_NOT_APPROVED",
                    "Composition candidate gameplay gains require an internal audit-only authorization");
            if (!authorization.exactFor(policy)) throw new CompositionGameplayConfigurationException(
                    "CANDIDATE_GAIN_POLICY_IDENTITY_MISMATCH",
                    "Composition candidate gameplay gain identity does not match the frozen policy");
            this.frozenPolicy = FrozenCompositionInteractionRuntimePolicy.current();
            this.gameplayGainPolicy = policy;
        } else if (mode == TeamCompositionGameplayMode.SHADOW) {
            this.frozenPolicy = FrozenCompositionInteractionRuntimePolicy.current();
            this.gameplayGainPolicy = null;
        } else {
            this.frozenPolicy = null;
            this.gameplayGainPolicy = null;
        }
    }

    public static CompositionRuntimeState off(long matchSeed) {
        return new CompositionRuntimeState(TeamCompositionGameplayMode.OFF, matchSeed);
    }

    public TeamCompositionGameplayMode mode() { return mode; }
    public boolean isShadow() { return mode == TeamCompositionGameplayMode.SHADOW; }
    public boolean isCandidate() { return mode == TeamCompositionGameplayMode.CANDIDATE; }
    public boolean isProductionV2() { return mode == TeamCompositionGameplayMode.PRODUCTION_V2; }
    public boolean isActive() { return mode != TeamCompositionGameplayMode.OFF; }
    public boolean initialized() { return initialized; }
    public long matchSeed() { return matchSeed; }
    public FrozenCompositionInteractionRuntimePolicy frozenPolicy() { return frozenPolicy; }
    public FrozenCompositionGameplayGainPolicy gameplayGainPolicy() { return gameplayGainPolicy; }
    public CompositionCandidateExecutionAuthorization authorization() { return authorization; }
    public CompositionSemanticsAuditExecutionAuthorization semanticsAuditAuthorization() { return semanticsAuditAuthorization; }
    public CompositionKeySpecificCandidateAuditAuthorization keySpecificCandidateAuthorization() { return keySpecificCandidateAuthorization; }
    public boolean isAuditSemantics() { return semanticsAuditAuthorization.enabled(); }
    public boolean isKeySpecificCandidateAudit() { return keySpecificCandidateAuthorization.enabled(); }
    public CompositionTeamLineup blueLineup() { return blueLineup; }
    public CompositionTeamLineup redLineup() { return redLineup; }
    public TeamCompositionAnalysis blueAnalysis() { return blueAnalysis; }
    public TeamCompositionAnalysis redAnalysis() { return redAnalysis; }

    public void initialize(MatchChampionAssignments assignments) {
        Objects.requireNonNull(assignments, "assignments");
        if (!isActive()) return;
        if (initialized) throw new IllegalStateException("Composition runtime is already initialized");
        frozenPolicy.verifyExactIdentity();
        blueLineup = new CompositionTeamLineup(TeamSide.BLUE, buildLineup(TeamSide.BLUE, assignments));
        redLineup = new CompositionTeamLineup(TeamSide.RED, buildLineup(TeamSide.RED, assignments));
        lineupBuildCount = 2;
        TeamCompositionAnalyzer analyzer = new TeamCompositionAnalyzer();
        Map<ChampionRoleKey, ChampionCompositionProfile> profiles = PROFILES.profiles();
        blueAnalysis = analyzer.analyze(blueLineup.lineup(), profiles);
        redAnalysis = analyzer.analyze(redLineup.lineup(), profiles);
        teamCompositionAnalysisCount = 2;
        CompositionInteractionInput blueInput = CompositionInteractionInput.fromAnalysis(blueAnalysis);
        CompositionInteractionInput redInput = CompositionInteractionInput.fromAnalysis(redAnalysis);
        CompositionInteractionAnalysis interaction = new CompositionInteractionEvaluator().evaluate(
                blueInput, redInput, frozenPolicy.formula());
        interactionAnalysisCount = 1;
        for (TeamCompositionContext context : TeamCompositionContext.values()) {
            double blue = normalizeZero(interaction.contexts().get(context).teamASignedEdge());
            double red = normalizeZero(-blue);
            blueEdges.put(context, blue);
            redEdges.put(context, red);
        }
        contextEdgeCount = TeamCompositionContext.values().length;
        initialized = true;
    }

    public double edgeFor(TeamSide perspectiveSide, TeamCompositionContext context) {
        Objects.requireNonNull(perspectiveSide, "perspectiveSide");
        Objects.requireNonNull(context, "context");
        if (!isActive() || !initialized) throw new IllegalStateException("Composition runtime is not initialized");
        return normalizeZero(perspectiveSide == TeamSide.BLUE ? blueEdges.get(context) : redEdges.get(context));
    }

    public GameplayAttemptId lastActualAttemptId() { return lastActualAttemptId; }

    public GameplayAttemptId createActualAttemptId() {
        if (!isActive()) throw new IllegalStateException("OFF runtime cannot issue composition attempt IDs");
        return new GameplayAttemptId(++nextAttemptSequence);
    }

    public void recordResolverEvaluation() {
        if (isActive()) resolverEvaluationCount++;
    }

    public void recordTriggerSuccess() {
        if (isActive()) triggerSuccessCount++;
    }

    public CompositionShadowObservation recordActualAttempt(CompositionAttemptDescriptor attempt) {
        Objects.requireNonNull(attempt, "attempt");
        if (!isActive()) return null;
        lastActualAttemptId = attempt.attemptId();
        if (!initialized) throw new IllegalStateException("Composition runtime must initialize before attempts");
        CompositionContextRouting routing = router.route(attempt);
        ObservationKey key = routing.mapped()
                ? new ObservationKey(attempt.attemptId(), routing.perspectiveSide(), routing.context()) : null;
        if (key != null && observationKeys.contains(key)) {
            duplicateObservationCount++;
            return null;
        }
        if (primaryContextByAttempt.containsKey(attempt.attemptId())) {
            multiContextAttemptCount++;
            if (!Objects.equals(perspectiveByAttempt.get(attempt.attemptId()), routing.perspectiveSide())) {
                conflictingPerspectiveCount++;
            }
            return null;
        }
        actualAttemptCount++;
        routings.add(routing);
        if (!routing.mapped()) {
            unmappedActualAttemptCount++;
            return null;
        }
        primaryContextByAttempt.put(attempt.attemptId(), routing.context());
        perspectiveByAttempt.put(attempt.attemptId(), routing.perspectiveSide());
        observationKeys.add(key);
        ApplicationKey applicationKey = new ApplicationKey(attempt.attemptId(), routing.applicationPoint());
        if (!applicationKeys.add(applicationKey)) duplicateApplicationPointCount++;
        mappedActualAttemptCount++;
        double blue = edgeFor(TeamSide.BLUE, routing.context());
        double red = edgeFor(TeamSide.RED, routing.context());
        double perspective = edgeFor(routing.perspectiveSide(), routing.context());
        Double perspectiveScore = routing.perspectiveBaselineScore();
        Double opponentScore = routing.opponentBaselineScore();
        Double gap = routing.baselineScoreAvailable() ? perspectiveScore - opponentScore : null;
        CompositionShadowObservation observation = new CompositionShadowObservation(
                matchSeed, attempt.attemptId(), attempt.matchTimeSeconds(), attempt.actionType(), routing.context(),
                attempt.attemptOwnerSide(), routing.perspectiveSide(), routing.perspectiveSide().opposite(),
                blue, red, perspective, frozenPolicy.candidateVersion(), frozenPolicy.candidateHash(),
                frozenPolicy.formula(), routing.applicationPoint(), routing.scoreDomain(),
                routing.baselineScoreAvailable(), perspectiveScore, opponentScore, gap,
                routing.applicationEligibility(), routing.applicationEligibility().eligible(),
                routing.eligibilityReason(), routing.scoreCapturePoint(), routing.scoreCaptureEvidence(),
                false, 0.0, routing.mappingReason());
        observations.add(observation);
        shadowObservationCount++;
        if (isCandidate()) {
            recordCandidateApplication(attempt, routing, gap);
        }
        return observation;
    }

    /** Allocates an ID and records one attempt atomically at an actual resolver boundary. */
    public CompositionShadowObservation recordActualAttempt(CompositionActionType actionType,
                                                            TeamSide ownerSide, TeamSide initiatingSide,
                                                            TeamSide defendingSide, FightScale fightScale,
                                                            ObjectiveType objectiveType, boolean objectiveContested,
                                                            StructureKind structureTargetType, Lane lane,
                                                            int matchTimeSeconds, CompositionBaselineScoreDomain scoreDomain,
                                                            Double ownerBaselineScore, Double opponentBaselineScore) {
        if (!isActive()) return null;
        return recordActualAttempt(new CompositionAttemptDescriptor(createActualAttemptId(), actionType,
                ownerSide, initiatingSide, defendingSide, fightScale, objectiveType, objectiveContested,
                structureTargetType, lane, matchTimeSeconds, scoreDomain, ownerBaselineScore, opponentBaselineScore));
    }

    /** Test-only semantic guard): evaluation never creates an observation. */
    public void recordEvaluationOnlyObservationAttempt() {
        if (isActive()) evaluationOnlyObservationCount++;
    }

    public CompositionRuntimeDiagnostics snapshot() {
        return new CompositionRuntimeDiagnostics(mode, initialized, matchSeed, lineupBuildCount,
                teamCompositionAnalysisCount, interactionAnalysisCount, contextEdgeCount,
                runtimeInteractionRecalculationCount, resolverEvaluationCount, triggerSuccessCount,
                actualAttemptCount, mappedActualAttemptCount, unmappedActualAttemptCount,
                shadowObservationCount, evaluationOnlyObservationCount, duplicateObservationCount,
                multiContextAttemptCount, conflictingPerspectiveCount, duplicateApplicationPointCount,
                gameplayApplicationCount, nonZeroModifierCount, 0, 0,
                observations, routings, candidateApplications, localDecisionComparisons,
                deferredCandidateApplicationCount, isAuditSemantics(),
                isAuditSemantics() ? semanticsAuditAuthorization.blueprintVersion() : "NONE",
                isAuditSemantics() ? semanticsAuditAuthorization.blueprintHash() : "NONE",
                isAuditSemantics() ? semanticsAuditAuthorization.diagnosticCaseIndex() : -1,
                isKeySpecificCandidateAudit(),
                isKeySpecificCandidateAudit() ? keySpecificCandidateAuthorization.candidateVersion() : "NONE",
                isKeySpecificCandidateAudit() ? keySpecificCandidateAuthorization.candidateHash() : "NONE",
                isKeySpecificCandidateAudit() ? keySpecificCandidateAuthorization.holdoutCaseIndex() : -1,
                winnerChannelObservations, fightGradeDiagnostics, baseDefenseRoleRoutings, winnerDecisionProvenance);
    }

    public List<CompositionShadowObservation> observations() { return List.copyOf(observations); }
    public List<CompositionContextRouting> routings() { return List.copyOf(routings); }
    public List<CompositionCandidateApplicationObservation> candidateApplications() { return List.copyOf(candidateApplications); }
    public List<CompositionLocalDecisionComparison> localDecisionComparisons() { return List.copyOf(localDecisionComparisons); }
    public List<CompositionWinnerChannelObservation> winnerChannelObservations() { return List.copyOf(winnerChannelObservations); }
    public List<FightGradeDecisionDiagnostic> fightGradeDiagnostics() { return List.copyOf(fightGradeDiagnostics); }
    public List<BaseDefenseRoleRoutingDiagnostic> baseDefenseRoleRoutings() { return List.copyOf(baseDefenseRoleRoutings); }
    public List<CompositionWinnerDecisionProvenance> winnerDecisionProvenance() { return List.copyOf(winnerDecisionProvenance); }

    public void recordWinnerDecisionProvenance(CompositionWinnerDecisionProvenance value) {
        if (!isAuditSemantics()) return;
        Objects.requireNonNull(value, "value");
        if (value.matchSeed() != matchSeed || value.caseIndex() != semanticsAuditAuthorization.diagnosticCaseIndex()) {
            throw new IllegalArgumentException("Winner decision provenance match identity mismatch");
        }
        if (winnerDecisionProvenance.stream().anyMatch(x -> x.attemptId().equals(value.attemptId()))) {
            throw new IllegalStateException("Duplicate winner decision provenance attempt=" + value.attemptId());
        }
        winnerDecisionProvenance.add(value);
    }

    public CompositionWinnerDecisionAdjustment auditWinnerAdjustment(
            TeamSide perspectiveSide, TeamCompositionContext context, CompositionActionType actionType,
            CompositionBaselineScoreDomain scoreDomain, double baselineGap, CompositionCombatRole perspectiveRole) {
        Objects.requireNonNull(perspectiveSide, "perspectiveSide");
        Objects.requireNonNull(perspectiveRole, "perspectiveRole");
        if (!isAuditSemantics() && !isProductionV2()) {
            return new CompositionWinnerDecisionAdjustment(context.name() + "|" + actionType.name() + "|" + scoreDomain.name(),
                    "NOT_APPLICABLE", baselineGap, 0.0, 0.0, 0.0, baselineGap, perspectiveRole);
        }
        FrozenCompositionApplicationSemanticsBlueprint.key(context, actionType, scoreDomain);
        double edge = edgeFor(perspectiveSide, context);
        double referenceGain;
        String status;
        if (context == TeamCompositionContext.SKIRMISH) {
            referenceGain = isProductionV2() ? FrozenCompositionProductionCandidate.winnerGain(context)
                    : isKeySpecificCandidateAudit()
                    ? keySpecificCandidateAuthorization.winnerGain(context)
                    : FrozenCompositionGameplayGainPolicy.SKIRMISH_GAIN;
            status = "FROZEN_EXISTING_WINNER_GAIN";
        } else if (context == TeamCompositionContext.TEAMFIGHT) {
            referenceGain = isProductionV2() ? FrozenCompositionProductionCandidate.winnerGain(context)
                    : isKeySpecificCandidateAudit()
                    ? keySpecificCandidateAuthorization.winnerGain(context)
                    : FrozenCompositionGameplayGainPolicy.TEAMFIGHT_GAIN;
            status = isKeySpecificCandidateAudit() ? "FROZEN_KEY_SPECIFIC_FRESH_HOLDOUT_CANDIDATE" : "DIAGNOSTIC_HISTORICAL_REFERENCE_ONLY";
        } else if (context == TeamCompositionContext.SIEGE) {
            referenceGain = isProductionV2() ? FrozenCompositionProductionCandidate.winnerGain(context)
                    : isKeySpecificCandidateAudit()
                    ? keySpecificCandidateAuthorization.winnerGain(context)
                    : FrozenCompositionGameplayGainPolicy.SIEGE_GAIN;
            status = isKeySpecificCandidateAudit() ? "FROZEN_KEY_SPECIFIC_FRESH_HOLDOUT_CANDIDATE" : "DIAGNOSTIC_HISTORICAL_REFERENCE_ONLY";
        } else if (context == TeamCompositionContext.BASE_DEFENSE) {
            referenceGain = isProductionV2() ? FrozenCompositionProductionCandidate.winnerGain(context)
                    : isKeySpecificCandidateAudit() ? keySpecificCandidateAuthorization.winnerGain(context) : 0.0;
            status = isKeySpecificCandidateAudit() ? "FROZEN_KEY_SPECIFIC_FRESH_HOLDOUT_CANDIDATE" : "BASE_DEFENSE_ROLE_AWARE_WINNER_GAIN_UNCALIBRATED";
        } else {
            throw new CompositionGameplayConfigurationException("COMPOSITION_SEMANTICS_APPLICATION_KEY_UNMAPPED", context.name());
        }
        double modifier = referenceGain * edge;
        return new CompositionWinnerDecisionAdjustment(context.name() + "|" + actionType.name() + "|" + scoreDomain.name(),
                status, baselineGap, edge, referenceGain, modifier, baselineGap + modifier, perspectiveRole);
    }

    public CompositionSeverityDecisionAdjustment auditSeverityAdjustment(
            TeamCompositionContext context, CompositionActionType actionType,
            CompositionBaselineScoreDomain scoreDomain, double baselineSeverityInput) {
        if (isAuditSemantics()) FrozenCompositionApplicationSemanticsBlueprint.key(context, actionType, scoreDomain);
        return new CompositionSeverityDecisionAdjustment(context.name() + "|" + actionType.name() + "|" + scoreDomain.name(),
                baselineSeverityInput, 0.0, baselineSeverityInput);
    }

    public void recordAuditWinnerObservation(
            GameplayAttemptId attemptId, int timeSeconds, CompositionWinnerDecisionAdjustment adjustment,
            TeamSide perspectiveSide, TeamSide attackingSide, TeamSide defendingSide, double winnerProbability,
            double baselineWinnerProbability, double winnerRandomSample, long winnerRandomDrawOrdinal,
            TeamSide winnerResult) {
        if (!isAuditSemantics()) return;
        String[] key = adjustment.applicationKey().split("\\|");
        TeamCompositionContext context = TeamCompositionContext.valueOf(key[0]);
        CompositionActionType action = CompositionActionType.valueOf(key[1]);
        CompositionBaselineScoreDomain domain = CompositionBaselineScoreDomain.valueOf(key[2]);
        winnerChannelObservations.add(new CompositionWinnerChannelObservation(matchSeed,
                semanticsAuditAuthorization.diagnosticCaseIndex(), attemptId, context, action, domain, timeSeconds,
                perspectiveSide, attackingSide, defendingSide, adjustment.perspectiveRole(), adjustment.baselineGap(),
                baselineWinnerProbability, adjustment.rawEdge(), adjustment.gainStatus(), adjustment.referenceGain(), adjustment.winnerModifier(),
                adjustment.winnerDecisionGap(), winnerProbability, winnerRandomSample, winnerRandomDrawOrdinal, winnerResult));
        if (context == TeamCompositionContext.BASE_DEFENSE) {
            if (attackingSide == null || defendingSide == null) {
                throw new CompositionGameplayConfigurationException("COMPOSITION_BASE_DEFENSE_ROLE_MISSING",
                        "BASE_DEFENSE audit routing requires structured attacker and defender");
            }
            double attackerSignal = edgeFor(attackingSide, context);
            double defenderSignal = edgeFor(defendingSide, context);
            double canonical = normalizeZero(attackerSignal);
            baseDefenseRoleRoutings.add(new BaseDefenseRoleRoutingDiagnostic(matchSeed,
                    semanticsAuditAuthorization.diagnosticCaseIndex(), attemptId, timeSeconds, attackingSide, defendingSide,
                    attackerSignal, defenderSignal, canonical, normalizeZero(-canonical),
                    isKeySpecificCandidateAudit() ? "ROLE_ORIENTED_PRODUCT_EXPOSURE_CONTEXT_EDGE_V1" : "COMPONENTS_ONLY_UNCALIBRATED",
                    isKeySpecificCandidateAudit() ? adjustment.winnerModifier() : 0.0, false,
                    isKeySpecificCandidateAudit()));
        }
    }

    public void recordFightGradeDiagnostic(FightGradeDecisionDiagnostic diagnostic) {
        if (!isAuditSemantics()) return;
        if (diagnostic.caseIndex() != semanticsAuditAuthorization.diagnosticCaseIndex()
                || diagnostic.matchSeed() != matchSeed) {
            throw new IllegalArgumentException("FightGrade diagnostic match identity mismatch");
        }
        fightGradeDiagnostics.add(diagnostic);
    }

    /** Records an observational same-sample baseline comparison after the real decision. */
    public void recordLocalDecisionComparison(CompositionLocalDecisionComparison comparison) {
        if (!isCandidate()) return;
        Objects.requireNonNull(comparison, "comparison");
        if (candidateApplications.stream().noneMatch(a -> a.attemptId().equals(comparison.attemptId()) && a.applicationApplied())) {
            throw new IllegalStateException("Local comparison requires an applied candidate attempt");
        }
        localDecisionComparisons.add(comparison);
    }

    /** Pure score projection used before an existing outcome Random draw. */
    public double adjustedScoreForCandidate(TeamSide side, TeamCompositionContext context,
                                            CompositionActionType actionType,
                                            CompositionBaselineScoreDomain scoreDomain,
                                            double sideBaselineScore, double opponentBaselineScore) {
        if ((isAuditSemantics() || isProductionV2()) && context == TeamCompositionContext.SKIRMISH) {
            double gain = isProductionV2() ? FrozenCompositionProductionCandidate.SKIRMISH_WINNER_GAIN
                    : FrozenCompositionGameplayGainPolicy.SKIRMISH_GAIN;
            return sideBaselineScore + gain * edgeFor(side, context) / 2.0;
        }
        if (!isCandidate()) return sideBaselineScore;
        if (!Double.isFinite(sideBaselineScore) || !Double.isFinite(opponentBaselineScore)) {
            throw new IllegalArgumentException("Candidate score projection requires finite scores");
        }
        double gain = gameplayGainPolicy.gainFor(context, actionType, scoreDomain);
        return gain == 0.0 ? sideBaselineScore
                : sideBaselineScore + gain * edgeFor(side, context) / 2.0;
    }

    public double adjustedGapFor(GameplayAttemptId attemptId, double baselinePerspectiveScore,
                                 double baselineOpponentScore) {
        CandidateScoreAdjustment adjustment = candidateAdjustments.get(attemptId);
        return adjustment == null ? baselinePerspectiveScore - baselineOpponentScore : adjustment.adjustedGap();
    }

    private void recordCandidateApplication(CompositionAttemptDescriptor attempt,
                                             CompositionContextRouting routing, Double gap) {
        double rawEdge = edgeFor(routing.perspectiveSide(), routing.context());
        double gain = gameplayGainPolicy.gainFor(routing.context(), attempt.actionType(), routing.scoreDomain());
        String keyText = routing.context().name() + "|" + attempt.actionType().name()
                + "|" + routing.scoreDomain().name();
        CandidateApplicationKey key = new CandidateApplicationKey(attempt.attemptId(), routing.context(),
                attempt.actionType(), routing.scoreDomain());
        if (!candidateApplicationKeys.add(key)) {
            duplicateApplicationPointCount++;
            return;
        }
        boolean eligible = routing.applicationEligibility().eligible()
                && routing.baselineScoreAvailable() && gain != 0.0;
        if (!eligible) {
            deferredCandidateApplicationCount++;
            candidateApplications.add(candidateObservation(attempt, routing, rawEdge, gain, gap,
                    0.0, 0.0, 0.0, null, null, null, false, keyText, routing.eligibilityReason()));
            return;
        }
        double modifier = gain * rawEdge;
        double perspectiveAdjustment = modifier / 2.0;
        double opponentAdjustment = -perspectiveAdjustment;
        double adjustedPerspective = routing.perspectiveBaselineScore() + perspectiveAdjustment;
        double adjustedOpponent = routing.opponentBaselineScore() + opponentAdjustment;
        double adjustedGap = adjustedPerspective - adjustedOpponent;
        candidateAdjustments.put(attempt.attemptId(), new CandidateScoreAdjustment(
                routing.perspectiveSide(), adjustedPerspective, adjustedOpponent, adjustedGap));
        gameplayApplicationCount++;
        if (modifier != 0.0) nonZeroModifierCount++;
        candidateApplications.add(candidateObservation(attempt, routing, rawEdge, gain, gap, modifier,
                perspectiveAdjustment, opponentAdjustment, adjustedPerspective, adjustedOpponent, adjustedGap,
                true, keyText, "APPLIED_APPROVED_KEY"));
    }

    private CompositionCandidateApplicationObservation candidateObservation(CompositionAttemptDescriptor attempt,
                                                                             CompositionContextRouting routing,
                                                                             double rawEdge, double gain, Double gap,
                                                                             double modifier, double perspectiveAdjustment,
                                                                             double opponentAdjustment, Double adjustedPerspective,
                                                                             Double adjustedOpponent, Double adjustedGap, boolean applied,
                                                                             String keyText, String reason) {
        String before = sign(gap);
        String after = sign(adjustedGap);
        boolean flip = applied && !before.equals("ZERO") && !after.equals("ZERO") && !before.equals(after);
        String subtype = flip ? before + "_TO_" + after : "NO_SIGN_FLIP";
        return new CompositionCandidateApplicationObservation(matchSeed, attempt.attemptId(), attempt.matchTimeSeconds(),
                attempt.actionType(), routing.context(), routing.applicationPoint(), routing.scoreDomain(),
                routing.perspectiveSide(), routing.perspectiveSide().opposite(), rawEdge, gain,
                routing.baselineScoreAvailable(), routing.perspectiveBaselineScore(), routing.opponentBaselineScore(),
                gap, modifier, perspectiveAdjustment, opponentAdjustment, adjustedPerspective, adjustedOpponent,
                adjustedGap, true, before, after, flip, subtype, applied, keyText,
                gameplayGainPolicy.candidateVersion(), gameplayGainPolicy.candidateHash(), authorization.policyHash(), reason);
    }

    private String sign(Double value) {
        if (value == null || value == 0.0) return "ZERO";
        return value > 0.0 ? "POSITIVE" : "NEGATIVE";
    }

    private TeamCompositionLineup buildLineup(TeamSide side, MatchChampionAssignments assignments) {
        EnumMap<Position, ChampionRoleKey> values = new EnumMap<>(Position.class);
        for (Position position : Position.values()) {
            ChampionAssignment assignment = assignments.get(new PlayerKey(side, position));
            if (assignment.selectedPosition() != position) {
                throw new IllegalStateException("Composition assignment position mismatch for " + side + "/" + position);
            }
            ChampionRoleKey key = new ChampionRoleKey(new ChampionId(assignment.championId().value()), position);
            if (!PROFILES.profiles().containsKey(key)) {
                throw new IllegalStateException("Missing frozen composition profile: " + key.stableId());
            }
            if (values.put(position, key) != null) throw new IllegalStateException("Duplicate composition position");
        }
        return new TeamCompositionLineup(values);
    }

    private double normalizeZero(double value) { return value == 0.0 ? 0.0 : value; }

    private record ObservationKey(GameplayAttemptId attemptId, TeamSide perspectiveSide,
                                  TeamCompositionContext context) {}
    private record ApplicationKey(GameplayAttemptId attemptId, CompositionApplicationPoint applicationPoint) {}
    private record CandidateApplicationKey(GameplayAttemptId attemptId, TeamCompositionContext context,
                                            CompositionActionType actionType, CompositionBaselineScoreDomain scoreDomain) {}
    private record CandidateScoreAdjustment(TeamSide perspectiveSide, double adjustedPerspective,
                                            double adjustedOpponent, double adjustedGap) {}
}
