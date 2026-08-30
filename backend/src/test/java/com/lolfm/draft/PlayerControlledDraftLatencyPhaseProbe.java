package com.lolfm.draft;

import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionSelectionMode;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.TeamSide;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Test-side exact phase projection of PlayerControlledDraftEngine.select(). */
public final class PlayerControlledDraftLatencyPhaseProbe {
    private final PlayerControlledDraftEngine engine;
    private final DraftResourceSet resources;
    private final DraftRuleSet rules;
    private final ShallowDraftSearch search;
    private final AutoDraftSelector selector;
    private final FinalRoleAssignmentResolver finalRoles;
    private final LongSupplier clock;

    public PlayerControlledDraftLatencyPhaseProbe(PlayerControlledDraftEngine engine) {
        this(engine, System::nanoTime);
    }

    PlayerControlledDraftLatencyPhaseProbe(
            PlayerControlledDraftEngine engine, LongSupplier clock
    ) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.clock = Objects.requireNonNull(clock, "clock");
        resources = field(engine, "resources", DraftResourceSet.class);
        rules = field(engine, "rules", DraftRuleSet.class);
        search = field(engine, "search", ShallowDraftSearch.class);
        selector = field(engine, "selector", AutoDraftSelector.class);
        finalRoles = field(engine, "finalRoles", FinalRoleAssignmentResolver.class);
    }

    public Observation select(
            PlayerControlledDraftEngine.Progress progress,
            DraftTeamContext blue,
            DraftTeamContext red,
            DraftSelectionContext selectionContext,
            ChampionId championId,
            String clientActionId,
            boolean captureTimings
    ) {
        Objects.requireNonNull(progress, "progress");
        if (progress.complete()) throw new IllegalStateException("PLAYER_DRAFT_ALREADY_COMPLETE");
        DraftState state = progress.state();
        DraftTurn playerTurn = state.currentTurn();
        if (playerTurn.side() != progress.controlledSide()) {
            throw new IllegalStateException("PLAYER_DRAFT_NOT_PLAYER_TURN");
        }

        long legalityStart = tick(captureTimings);
        PlayerControlledDraftEngine.SelectionView legal = engine.view(progress, blue, red);
        if (!legal.selectableChampionIds().contains(championId)) {
            throw new IllegalArgumentException("PLAYER_DRAFT_ILLEGAL_SELECTION");
        }
        long legalityNanos = elapsed(legalityStart, captureTimings);

        long playerStart = tick(captureTimings);
        String before = DraftStateHasher.hash(state);
        DraftState afterPlayer = state.apply(new DraftAction(
                playerTurn.number(), playerTurn.side(), playerTurn.actionType(), championId));
        PlayerManualSelectionEvidence manual = new PlayerManualSelectionEvidence(
                progress.controlledSide(), playerTurn.number(), playerTurn.actionType(),
                championId, before, legal.selectableSetIdentity(),
                PlayerSelectionLegality.LEGAL, clientActionId);
        ArrayList<DraftTurnControlEvidence> evidence =
                new ArrayList<>(progress.turnEvidence());
        evidence.add(new DraftTurnControlEvidence(
                playerTurn.number(), playerTurn.side(), playerTurn.actionType(), championId,
                DraftDecisionAuthority.PLAYER, before, DraftStateHasher.hash(afterPlayer),
                null, manual));
        long playerApplyNanos = elapsed(playerStart, captureTimings);

        DraftComputationContext computation = DraftComputationContext.cached();
        ArrayList<AiTurnObservation> aiTurns = new ArrayList<>();
        DraftState current = afterPlayer;
        long aiStart = tick(captureTimings);
        while (!current.complete()
                && current.currentTurn().side() != progress.controlledSide()) {
            DraftTurn turn = current.currentTurn();
            DraftComputationContext.Snapshot countersBefore = computation.snapshot();
            String aiBefore = DraftStateHasher.hash(current);
            long turnStart = tick(captureTimings);
            ShallowDraftSearch.SearchResult evaluated = search.evaluate(
                    current, blue, red, computation);
            AutoDraftSelector.Selection selection = selector.select(
                    current, evaluated, selectionContext);
            ChampionId selected = selection.selectedCandidate().championId();
            DraftState after = current.apply(new DraftAction(
                    turn.number(), turn.side(), turn.actionType(), selected));
            evidence.add(new DraftTurnControlEvidence(
                    turn.number(), turn.side(), turn.actionType(), selected,
                    DraftDecisionAuthority.AI, aiBefore, DraftStateHasher.hash(after),
                    selection.trace(), null));
            long turnNanos = elapsed(turnStart, captureTimings);
            DraftComputationContext.Snapshot countersAfter = computation.snapshot();
            aiTurns.add(new AiTurnObservation(
                    turn.number(), turn.side(), turn.actionType(), selected,
                    evaluated.rankedCandidates().size(), turnNanos,
                    countersAfter.plannerCandidatePhysicalComputations()
                            - countersBefore.plannerCandidatePhysicalComputations(),
                    countersAfter.roleAssignmentPhysicalComputations()
                            - countersBefore.roleAssignmentPhysicalComputations(),
                    countersAfter.completionPhysicalComputations()
                            - countersBefore.completionPhysicalComputations(),
                    countersAfter.poolHealthPhysicalComputations()
                            - countersBefore.poolHealthPhysicalComputations(),
                    countersAfter.rolePositionMisses() - countersBefore.rolePositionMisses(),
                    countersAfter.peakEntries()));
            current = after;
        }
        long aiTotalNanos = elapsed(aiStart, captureTimings);

        long completionStart = tick(captureTimings);
        PlayerControlledDraftResult result = current.complete()
                ? complete(progress.controlledSide(), current, evidence, blue, red, computation)
                : null;
        long completionNanos = elapsed(completionStart, captureTimings);
        PlayerControlledDraftEngine.Progress projected = new PlayerControlledDraftEngine.Progress(
                progress.controlledSide(), current, evidence, result);
        return new Observation(projected, legalityNanos, playerApplyNanos,
                aiTotalNanos, completionNanos, aiTurns, computation.snapshot());
    }

    private PlayerControlledDraftResult complete(
            TeamSide controlledSide,
            DraftState state,
            List<DraftTurnControlEvidence> evidence,
            DraftTeamContext blue,
            DraftTeamContext red,
            DraftComputationContext context
    ) {
        FinalRoleAssignmentResolver.ResolvedPair resolved = finalRoles.resolve(
                state.bluePicks(), state.redPicks(), blue, red, context);
        return new PlayerControlledDraftResult(
                rules, controlledSide, state.blueBans(), state.redBans(),
                state.bluePicks(), state.redPicks(), evidence,
                resolved.blue().positions(), resolved.red().positions(),
                toMatchAssignments(resolved.blue(), resolved.red()),
                state.fearlessExclusions(), resources.meta().metaVersion(),
                resources.meta().requiredLegalRoleKeyHash(),
                resources.meta().actualLegalRoleKeyHash());
    }

    private static MatchChampionAssignments toMatchAssignments(
            RoleAssignmentSolver.RoleAssignment blue,
            RoleAssignmentSolver.RoleAssignment red
    ) {
        ArrayList<ChampionAssignment> values = new ArrayList<>();
        addAssignments(values, TeamSide.BLUE, blue);
        addAssignments(values, TeamSide.RED, red);
        return new MatchChampionAssignments(values, ChampionSelectionMode.EXPLICIT);
    }

    private static void addAssignments(
            List<ChampionAssignment> target,
            TeamSide side,
            RoleAssignmentSolver.RoleAssignment assignment
    ) {
        assignment.positions().entrySet().stream().sorted(Map.Entry.comparingByValue())
                .forEach(entry -> target.add(new ChampionAssignment(
                        new PlayerKey(side, entry.getValue()), entry.getKey(), entry.getValue())));
    }

    private long tick(boolean capture) {
        return capture ? clock.getAsLong() : 0L;
    }

    private long elapsed(long start, boolean capture) {
        if (!capture) return 0L;
        long value = clock.getAsLong() - start;
        if (value < 0L) throw new IllegalStateException("PROFILING_CLOCK_MOVED_BACKWARDS");
        return value;
    }

    private static <T> T field(Object owner, String name, Class<T> type) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(owner));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Missing Player Draft profiling boundary " + name,
                    error);
        }
    }

    public record AiTurnObservation(
            int turn,
            TeamSide side,
            DraftActionType actionType,
            ChampionId championId,
            int candidateEvaluationCount,
            long elapsedNanos,
            long plannerCandidatePhysicalComputations,
            long roleAssignmentPhysicalComputations,
            long completionPhysicalComputations,
            long poolHealthPhysicalComputations,
            long rolePositionPhysicalComputations,
            int peakCacheEntries
    ) {
        public AiTurnObservation {
            if (candidateEvaluationCount < 1 || elapsedNanos < 0L
                    || plannerCandidatePhysicalComputations < 0L
                    || roleAssignmentPhysicalComputations < 0L
                    || completionPhysicalComputations < 0L
                    || poolHealthPhysicalComputations < 0L
                    || rolePositionPhysicalComputations < 0L || peakCacheEntries < 0) {
                throw new IllegalArgumentException("Invalid AI turn observation");
            }
        }
    }

    public record Observation(
            PlayerControlledDraftEngine.Progress progress,
            long playerLegalityViewNanos,
            long playerApplyEvidenceNanos,
            long aiFollowUpTotalNanos,
            long completionNanos,
            List<AiTurnObservation> aiTurns,
            DraftComputationContext.Snapshot counters
    ) {
        public Observation {
            Objects.requireNonNull(progress, "progress");
            if (playerLegalityViewNanos < 0L || playerApplyEvidenceNanos < 0L
                    || aiFollowUpTotalNanos < 0L || completionNanos < 0L) {
                throw new IllegalArgumentException("Negative phase timing");
            }
            aiTurns = List.copyOf(aiTurns);
            Objects.requireNonNull(counters, "counters");
        }
    }
}
