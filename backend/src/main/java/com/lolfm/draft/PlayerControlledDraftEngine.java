package com.lolfm.draft;

import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionDefinition;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionSelectionMode;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.domain.Position;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Stateless transition engine for one mixed player/AI professional Draft. */
public final class PlayerControlledDraftEngine {
    private final DraftResourceSet resources;
    private final DraftRuleSet rules;
    private final ChampionCatalog champions;
    private final RoleAssignmentSolver assignments;
    private final DraftAvailability availability;
    private final ShallowDraftSearch search;
    private final AutoDraftSelector selector;
    private final FinalRoleAssignmentResolver finalRoles;

    public PlayerControlledDraftEngine(
            DraftResourceSet resources, DraftRuleSet rules, DraftScoringPolicy policy
    ) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.rules = Objects.requireNonNull(rules, "rules");
        champions = resources.champions().catalog();
        assignments = new RoleAssignmentSolver(champions);
        DraftCompositionEvaluator composition = new DraftCompositionEvaluator(
                champions, resources.champions().composition(), assignments);
        availability = new DraftAvailability(champions, assignments);
        DraftMatchupEvaluator matchup = new DraftMatchupEvaluator(
                assignments, resources.champions().matchup());
        PreDraftPlanner planner = new PreDraftPlanner(
                champions, resources.meta(), resources.champions().composition(), assignments);
        PickEvaluator pickEvaluator = new PickEvaluator(
                champions, resources.meta(), matchup, assignments, composition,
                availability, policy);
        BanEvaluator banEvaluator = new BanEvaluator(
                champions, resources.meta(), resources.champions().composition(), assignments,
                availability, composition, matchup, policy);
        DraftCandidateGenerator generator = new DraftCandidateGenerator(
                champions, resources.meta(), assignments, composition, availability, policy);
        search = new ShallowDraftSearch(
                planner, generator, pickEvaluator, banEvaluator, policy);
        selector = new AutoDraftSelector(AutoDraftSelectionPolicy.production());
        finalRoles = new FinalRoleAssignmentResolver(assignments, matchup, composition);
    }

    public Progress start(
            DraftTeamContext blue,
            DraftTeamContext red,
            DraftSelectionContext selectionContext,
            TeamSide controlledSide
    ) {
        if (selectionContext.seriesGameNumber() != 1) {
            throw new IllegalArgumentException("Player Draft V1 supports Game 1 only");
        }
        DraftState fresh = DraftState.fresh(rules, new SeriesDraftHistory());
        return advanceAi(new Progress(controlledSide, fresh, List.of(), null),
                blue, red, selectionContext);
    }

    public Progress select(
            Progress progress,
            DraftTeamContext blue,
            DraftTeamContext red,
            DraftSelectionContext selectionContext,
            ChampionId championId,
            String clientActionId
    ) {
        Objects.requireNonNull(progress, "progress");
        if (progress.complete()) throw new IllegalStateException("PLAYER_DRAFT_ALREADY_COMPLETE");
        DraftState state = progress.state();
        DraftTurn turn = state.currentTurn();
        if (turn.side() != progress.controlledSide()) {
            throw new IllegalStateException("PLAYER_DRAFT_NOT_PLAYER_TURN");
        }
        champions.get(championId);
        SelectionView view = view(progress, blue, red);
        if (!view.selectableChampionIds().contains(championId)) {
            PlayerDraftUnavailableReason reason = view.unavailable().stream()
                    .filter(value -> value.championId().equals(championId))
                    .map(UnavailableChampion::reason).findFirst()
                    .orElse(PlayerDraftUnavailableReason.FUTURE_ROLE_COMPLETION_INFEASIBLE);
            throw new IllegalArgumentException("PLAYER_DRAFT_ILLEGAL_SELECTION:" + reason);
        }
        String before = DraftStateHasher.hash(state);
        DraftAction action = new DraftAction(
                turn.number(), turn.side(), turn.actionType(), championId);
        DraftState afterState = state.apply(action);
        PlayerManualSelectionEvidence manual = new PlayerManualSelectionEvidence(
                progress.controlledSide(), turn.number(), turn.actionType(), championId,
                before, view.selectableSetIdentity(), PlayerSelectionLegality.LEGAL,
                clientActionId);
        DraftTurnControlEvidence evidence = new DraftTurnControlEvidence(
                turn.number(), turn.side(), turn.actionType(), championId,
                DraftDecisionAuthority.PLAYER, before, DraftStateHasher.hash(afterState),
                null, manual);
        ArrayList<DraftTurnControlEvidence> turns = new ArrayList<>(progress.turnEvidence());
        turns.add(evidence);
        return advanceAi(new Progress(progress.controlledSide(), afterState, turns, null),
                blue, red, selectionContext);
    }

    public SelectionView view(
            Progress progress, DraftTeamContext blue, DraftTeamContext red
    ) {
        if (progress.complete()) {
            return new SelectionView(List.of(), List.of(), List.of(),
                    selectableSetIdentity(progress.state(), List.of()));
        }
        DraftState state = progress.state();
        if (state.currentTurn().side() != progress.controlledSide()) {
            throw new IllegalStateException("Player Draft must be advanced to a player turn");
        }
        ArrayList<SelectableChampion> selectable = new ArrayList<>();
        ArrayList<UnavailableChampion> unavailable = new ArrayList<>();
        for (ChampionDefinition champion : champions.all().stream()
                .sorted(Comparator.comparing(value -> value.id().value())).toList()) {
            ChampionId id = champion.id();
            PlayerDraftUnavailableReason fixed = fixedUnavailableReason(state, id);
            if (fixed != null) {
                unavailable.add(new UnavailableChampion(id, fixed));
                continue;
            }
            if (state.currentTurn().actionType() == DraftActionType.PICK) {
                Set<Position> feasible = assignments.feasibleCandidatePositions(
                                state.picks(progress.controlledSide()), id).stream()
                        .filter(position -> availability.canCompleteWithCandidateAtRole(
                                state, progress.controlledSide(), id, position))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
                if (feasible.isEmpty()) {
                    unavailable.add(new UnavailableChampion(
                            id, PlayerDraftUnavailableReason.PARTIAL_ROLE_ASSIGNMENT_INFEASIBLE));
                } else if (!availability.canComplete(state, progress.controlledSide(), id)) {
                    unavailable.add(new UnavailableChampion(
                            id, PlayerDraftUnavailableReason.FUTURE_ROLE_COMPLETION_INFEASIBLE));
                } else {
                    selectable.add(new SelectableChampion(id, feasible));
                }
            } else if (!availability.canCompleteAfterExcluding(
                    state, TeamSide.BLUE, id)
                    || !availability.canCompleteAfterExcluding(state, TeamSide.RED, id)) {
                unavailable.add(new UnavailableChampion(
                        id, PlayerDraftUnavailableReason.BAN_WOULD_BREAK_FUTURE_COMPLETION));
            } else {
                selectable.add(new SelectableChampion(id, Set.of()));
            }
        }
        List<Recommendation> recommendations = recommendations(state, blue, red);
        List<ChampionId> selectableIds = selectable.stream()
                .map(SelectableChampion::championId).toList();
        return new SelectionView(selectable, unavailable, recommendations,
                selectableSetIdentity(state, selectableIds));
    }

    public void validateCompleted(
            PlayerControlledDraftResult result,
            DraftTeamContext blue,
            DraftTeamContext red,
            DraftSelectionContext selectionContext
    ) {
        Objects.requireNonNull(result, "result");
        if (!rules.equals(result.ruleSet()) || result.controlledSide() == null
                || !result.hardFearlessExclusions().isEmpty()) {
            throw new IllegalArgumentException("PLAYER_DRAFT_RULE_OR_SERIES_CONTEXT_MISMATCH");
        }
        DraftState state = new DraftState(rules, 0, List.of(), List.of(), List.of(),
                List.of(), result.hardFearlessExclusions());
        DraftComputationContext computation = DraftComputationContext.cached();
        for (DraftTurnControlEvidence evidence : result.turnEvidence()) {
            DraftTurn turn = state.currentTurn();
            if (evidence.turn() != turn.number() || evidence.side() != turn.side()
                    || evidence.actionType() != turn.actionType()
                    || !evidence.stateBeforeHash().equals(DraftStateHasher.hash(state))
                    || evidence.authority() == DraftDecisionAuthority.PLAYER
                    != (turn.side() == result.controlledSide())) {
                throw new IllegalArgumentException("PLAYER_DRAFT_EVIDENCE_BINDING_MISMATCH");
            }
            DraftAction action = new DraftAction(
                    turn.number(), turn.side(), turn.actionType(), evidence.championId());
            if (evidence.authority() == DraftDecisionAuthority.AI) {
                ShallowDraftSearch.SearchResult evaluated = search.evaluate(
                        state, blue, red, computation);
                AutoDraftSelector.Selection authoritative = selector.select(
                        state, evaluated, selectionContext);
                if (!authoritative.selectedCandidate().championId().equals(
                        evidence.championId())
                        || !authoritative.trace().equals(evidence.autoSelectionTrace())) {
                    throw new IllegalArgumentException(
                            "PLAYER_DRAFT_AUTHORITATIVE_AI_TRACE_MISMATCH");
                }
                state = state.apply(action);
            } else {
                SelectionView legal = view(
                        new Progress(result.controlledSide(), state,
                                result.turnEvidence().subList(0, turn.number() - 1), null),
                        blue, red);
                PlayerManualSelectionEvidence manual = evidence.playerSelectionEvidence();
                if (!legal.selectableChampionIds().contains(evidence.championId())
                        || manual.controlledSide() != result.controlledSide()
                        || manual.turn() != turn.number()
                        || manual.actionType() != turn.actionType()
                        || !manual.championId().equals(evidence.championId())
                        || !manual.stateBeforeHash().equals(DraftStateHasher.hash(state))
                        || !manual.selectableSetIdentity().equals(
                        legal.selectableSetIdentity())
                        || manual.legalityResult() != PlayerSelectionLegality.LEGAL) {
                    throw new IllegalArgumentException("PLAYER_DRAFT_MANUAL_EVIDENCE_MISMATCH");
                }
                state = state.apply(action);
            }
            if (!evidence.stateAfterHash().equals(DraftStateHasher.hash(state))) {
                throw new IllegalArgumentException("PLAYER_DRAFT_STATE_AFTER_MISMATCH");
            }
        }
        if (!state.complete() || !state.blueBans().equals(result.blueBans())
                || !state.redBans().equals(result.redBans())
                || !state.bluePicks().equals(result.bluePicks())
                || !state.redPicks().equals(result.redPicks())) {
            throw new IllegalArgumentException("PLAYER_DRAFT_FINAL_STATE_MISMATCH");
        }
        FinalRoleAssignmentResolver.ResolvedPair resolved = finalRoles.resolve(
                state.bluePicks(), state.redPicks(), blue, red);
        if (!resolved.blue().positions().equals(result.blueFinalRoleAssignments())
                || !resolved.red().positions().equals(result.redFinalRoleAssignments())
                || !toMatchAssignments(resolved.blue(), resolved.red()).asMap().equals(
                result.matchChampionAssignments().asMap())) {
            throw new IllegalArgumentException("PLAYER_DRAFT_FINAL_ASSIGNMENT_MISMATCH");
        }
        result.controlEvidence();
    }

    private Progress advanceAi(
            Progress progress,
            DraftTeamContext blue,
            DraftTeamContext red,
            DraftSelectionContext selectionContext
    ) {
        DraftState state = progress.state();
        ArrayList<DraftTurnControlEvidence> evidence =
                new ArrayList<>(progress.turnEvidence());
        DraftComputationContext context = DraftComputationContext.cached();
        while (!state.complete() && state.currentTurn().side() != progress.controlledSide()) {
            DraftTurn turn = state.currentTurn();
            String before = DraftStateHasher.hash(state);
            ShallowDraftSearch.SearchResult evaluated = search.evaluate(state, blue, red, context);
            AutoDraftSelector.Selection selection = selector.select(
                    state, evaluated, selectionContext);
            ChampionId champion = selection.selectedCandidate().championId();
            DraftState after = state.apply(new DraftAction(
                    turn.number(), turn.side(), turn.actionType(), champion));
            evidence.add(new DraftTurnControlEvidence(
                    turn.number(), turn.side(), turn.actionType(), champion,
                    DraftDecisionAuthority.AI, before, DraftStateHasher.hash(after),
                    selection.trace(), null));
            state = after;
        }
        PlayerControlledDraftResult result = state.complete()
                ? complete(progress.controlledSide(), state, evidence, blue, red, context)
                : null;
        return new Progress(progress.controlledSide(), state, evidence, result);
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

    private List<Recommendation> recommendations(
            DraftState state, DraftTeamContext blue, DraftTeamContext red
    ) {
        ShallowDraftSearch.SearchResult evaluated = search.evaluate(
                state, blue, red, DraftComputationContext.cached());
        ArrayList<Recommendation> result = new ArrayList<>();
        int rank = 1;
        for (DraftSearchCandidate candidate : evaluated.rankedCandidates().stream()
                .filter(value -> isLegal(state, value.championId()))
                .limit(3).toList()) {
            result.add(new Recommendation(
                    candidate.championId(), rank++, candidate.immediateScore(),
                    candidate.continuationScore(), candidate.finalSearchScore()));
        }
        return List.copyOf(result);
    }

    private boolean isLegal(DraftState state, ChampionId id) {
        if (fixedUnavailableReason(state, id) != null) return false;
        if (state.currentTurn().actionType() == DraftActionType.PICK) {
            return !assignments.feasibleCandidatePositions(
                    state.picks(state.currentTurn().side()), id).isEmpty()
                    && availability.canComplete(state, state.currentTurn().side(), id);
        }
        return availability.canCompleteAfterExcluding(state, TeamSide.BLUE, id)
                && availability.canCompleteAfterExcluding(state, TeamSide.RED, id);
    }

    private static PlayerDraftUnavailableReason fixedUnavailableReason(
            DraftState state, ChampionId id
    ) {
        if (state.fearlessExclusions().contains(id)) {
            return PlayerDraftUnavailableReason.HARD_FEARLESS_EXCLUDED;
        }
        if (state.blueBans().contains(id) || state.redBans().contains(id)) {
            return PlayerDraftUnavailableReason.ALREADY_BANNED;
        }
        if (state.bluePicks().contains(id) || state.redPicks().contains(id)) {
            return PlayerDraftUnavailableReason.ALREADY_PICKED;
        }
        return null;
    }

    public static String selectableSetIdentity(
            DraftState state, List<ChampionId> selectable
    ) {
        StringBuilder canonical = new StringBuilder(
                "selectableSetSchema=PLAYER_DRAFT_SELECTABLE_SET_V1\n")
                .append("stateHash=").append(DraftStateHasher.hash(state)).append('\n');
        selectable.stream().map(ChampionId::value).sorted()
                .forEach(value -> canonical.append("selectableChampion=")
                        .append(value).append('\n'));
        return PlayerDraftControlPolicy.hash(canonical.toString());
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

    public record Progress(
            TeamSide controlledSide,
            DraftState state,
            List<DraftTurnControlEvidence> turnEvidence,
            PlayerControlledDraftResult result
    ) {
        public Progress {
            Objects.requireNonNull(controlledSide, "controlledSide");
            Objects.requireNonNull(state, "state");
            turnEvidence = List.copyOf(turnEvidence);
            if (turnEvidence.size() != state.nextTurnIndex()) {
                throw new IllegalArgumentException("Player Draft evidence/state cardinality mismatch");
            }
            if (result != null && !state.complete()) {
                throw new IllegalArgumentException("Player Draft completion/result mismatch");
            }
        }

        public boolean complete() {
            return state.complete();
        }
    }

    public record SelectableChampion(ChampionId championId, Set<Position> feasibleRoles) {
        public SelectableChampion {
            Objects.requireNonNull(championId, "championId");
            feasibleRoles = feasibleRoles.isEmpty()
                    ? Set.of() : Set.copyOf(EnumSet.copyOf(feasibleRoles));
        }
    }

    public record UnavailableChampion(
            ChampionId championId, PlayerDraftUnavailableReason reason
    ) {
    }

    public record Recommendation(
            ChampionId championId,
            int advisoryRank,
            double immediateScore,
            double continuationScore,
            double finalSearchScore
    ) {
    }

    public record SelectionView(
            List<SelectableChampion> selectable,
            List<UnavailableChampion> unavailable,
            List<Recommendation> recommendations,
            String selectableSetIdentity
    ) {
        public SelectionView {
            selectable = List.copyOf(selectable);
            unavailable = List.copyOf(unavailable);
            recommendations = List.copyOf(recommendations);
            if (selectableSetIdentity == null
                    || !selectableSetIdentity.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("selectableSetIdentity");
            }
        }

        public List<ChampionId> selectableChampionIds() {
            return selectable.stream().map(SelectableChampion::championId).toList();
        }
    }
}
