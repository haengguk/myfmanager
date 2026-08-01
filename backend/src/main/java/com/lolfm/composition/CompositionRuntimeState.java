package com.lolfm.composition;

import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
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
    private final TeamCompositionGameplayMode mode;
    private final long matchSeed;
    private final FrozenCompositionInteractionRuntimePolicy frozenPolicy;
    private final CompositionContextRouter router = new CompositionContextRouter();
    private final EnumMap<TeamCompositionContext, Double> blueEdges = new EnumMap<>(TeamCompositionContext.class);
    private final EnumMap<TeamCompositionContext, Double> redEdges = new EnumMap<>(TeamCompositionContext.class);
    private final List<CompositionShadowObservation> observations = new ArrayList<>();
    private final List<CompositionContextRouting> routings = new ArrayList<>();
    private final Map<GameplayAttemptId, TeamCompositionContext> primaryContextByAttempt = new HashMap<>();
    private final Map<GameplayAttemptId, TeamSide> perspectiveByAttempt = new HashMap<>();
    private final Set<ObservationKey> observationKeys = new HashSet<>();
    private final Set<ApplicationKey> applicationKeys = new HashSet<>();
    private CompositionTeamLineup blueLineup;
    private CompositionTeamLineup redLineup;
    private TeamCompositionAnalysis blueAnalysis;
    private TeamCompositionAnalysis redAnalysis;
    private boolean initialized;
    private long nextAttemptSequence;
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

    public CompositionRuntimeState(TeamCompositionGameplayMode mode, long matchSeed) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.matchSeed = matchSeed;
        this.frozenPolicy = mode == TeamCompositionGameplayMode.SHADOW
                ? FrozenCompositionInteractionRuntimePolicy.current() : null;
    }

    public static CompositionRuntimeState off(long matchSeed) {
        return new CompositionRuntimeState(TeamCompositionGameplayMode.OFF, matchSeed);
    }

    public TeamCompositionGameplayMode mode() { return mode; }
    public boolean isShadow() { return mode == TeamCompositionGameplayMode.SHADOW; }
    public boolean initialized() { return initialized; }
    public long matchSeed() { return matchSeed; }
    public FrozenCompositionInteractionRuntimePolicy frozenPolicy() { return frozenPolicy; }
    public CompositionTeamLineup blueLineup() { return blueLineup; }
    public CompositionTeamLineup redLineup() { return redLineup; }
    public TeamCompositionAnalysis blueAnalysis() { return blueAnalysis; }
    public TeamCompositionAnalysis redAnalysis() { return redAnalysis; }

    public void initialize(MatchChampionAssignments assignments) {
        Objects.requireNonNull(assignments, "assignments");
        if (!isShadow()) return;
        if (initialized) throw new IllegalStateException("Composition runtime is already initialized");
        frozenPolicy.verifyExactIdentity();
        blueLineup = new CompositionTeamLineup(TeamSide.BLUE, buildLineup(TeamSide.BLUE, assignments));
        redLineup = new CompositionTeamLineup(TeamSide.RED, buildLineup(TeamSide.RED, assignments));
        lineupBuildCount = 2;
        TeamCompositionAnalyzer analyzer = new TeamCompositionAnalyzer();
        Map<ChampionRoleKey, ChampionCompositionProfile> profiles = ThirtyChampionCompositionProfiles.all();
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
        if (!isShadow() || !initialized) throw new IllegalStateException("Composition runtime is not initialized");
        return normalizeZero(perspectiveSide == TeamSide.BLUE ? blueEdges.get(context) : redEdges.get(context));
    }

    public GameplayAttemptId createActualAttemptId() {
        if (!isShadow()) throw new IllegalStateException("OFF runtime cannot issue composition attempt IDs");
        return new GameplayAttemptId(++nextAttemptSequence);
    }

    public void recordResolverEvaluation() {
        if (isShadow()) resolverEvaluationCount++;
    }

    public void recordTriggerSuccess() {
        if (isShadow()) triggerSuccessCount++;
    }

    public CompositionShadowObservation recordActualAttempt(CompositionAttemptDescriptor attempt) {
        Objects.requireNonNull(attempt, "attempt");
        if (!isShadow()) return null;
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
        if (!isShadow()) return null;
        return recordActualAttempt(new CompositionAttemptDescriptor(createActualAttemptId(), actionType,
                ownerSide, initiatingSide, defendingSide, fightScale, objectiveType, objectiveContested,
                structureTargetType, lane, matchTimeSeconds, scoreDomain, ownerBaselineScore, opponentBaselineScore));
    }

    /** Test-only semantic guard): evaluation never creates an observation. */
    public void recordEvaluationOnlyObservationAttempt() {
        if (isShadow()) evaluationOnlyObservationCount++;
    }

    public CompositionRuntimeDiagnostics snapshot() {
        return new CompositionRuntimeDiagnostics(mode, initialized, matchSeed, lineupBuildCount,
                teamCompositionAnalysisCount, interactionAnalysisCount, contextEdgeCount,
                runtimeInteractionRecalculationCount, resolverEvaluationCount, triggerSuccessCount,
                actualAttemptCount, mappedActualAttemptCount, unmappedActualAttemptCount,
                shadowObservationCount, evaluationOnlyObservationCount, duplicateObservationCount,
                multiContextAttemptCount, conflictingPerspectiveCount, duplicateApplicationPointCount,
                gameplayApplicationCount, nonZeroModifierCount, 0, 0,
                observations, routings);
    }

    public List<CompositionShadowObservation> observations() { return List.copyOf(observations); }
    public List<CompositionContextRouting> routings() { return List.copyOf(routings); }

    private TeamCompositionLineup buildLineup(TeamSide side, MatchChampionAssignments assignments) {
        EnumMap<Position, ChampionRoleKey> values = new EnumMap<>(Position.class);
        for (Position position : Position.values()) {
            ChampionAssignment assignment = assignments.get(new PlayerKey(side, position));
            if (assignment.selectedPosition() != position) {
                throw new IllegalStateException("Composition assignment position mismatch for " + side + "/" + position);
            }
            ChampionRoleKey key = new ChampionRoleKey(new ChampionId(assignment.championId().value()), position);
            if (!ThirtyChampionCompositionProfiles.all().containsKey(key)) {
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
}
