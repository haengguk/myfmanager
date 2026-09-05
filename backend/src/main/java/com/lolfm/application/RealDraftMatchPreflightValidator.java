package com.lolfm.application;

import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.champion.ChampionSelectionMode;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.domain.Player;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.FinalDraftResult;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.player.PlayerId;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.player.PlayerRatingKey;
import com.lolfm.simulator.MatchLineupIdentityValidator;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.TeamSide;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Stateless application-boundary validation before a real draft result reaches MatchSimulator. */
@Component
public final class RealDraftMatchPreflightValidator {
    private final PlayerRatingCatalog ratings;
    private final ChampionCatalog champions;

    public RealDraftMatchPreflightValidator(PlayerRatingCatalog ratings, ChampionCatalog champions) {
        this.ratings = Objects.requireNonNull(ratings, "ratings");
        this.champions = Objects.requireNonNull(champions, "champions");
    }

    public void validate(String blueTeamCode, Team blueTeam,
                         String redTeamCode, Team redTeam,
                         DraftTeamContext blueContext, DraftTeamContext redContext,
                         FinalDraftResult draftResult, SeriesDraftHistory seriesHistory) {
        try {
            validatePreflight(blueTeamCode, blueTeam, redTeamCode, redTeam,
                    blueContext, redContext, draftResult, seriesHistory);
        } catch (RealDraftMatchPreflightException error) {
            throw error;
        } catch (IllegalArgumentException error) {
            throw new RealDraftMatchPreflightException(error);
        }
    }

    public void validateFrozen(String blueCode, Team blue, String redCode, Team red,
            DraftTeamContext blueContext, DraftTeamContext redContext,
            FinalDraftResult result, SeriesDraftHistory history,
            com.lolfm.career.CompetitionRosterSnapshot frozen) {
        MatchLineupIdentityValidator.validate(blue, red);
        Map<Position, Player> blueRoster = validateFrozenTeam(blueCode, blue, frozen);
        Map<Position, Player> redRoster = validateFrozenTeam(redCode, red, frozen);
        validateDraftContext(TeamSide.BLUE, blueRoster, blueContext);
        validateDraftContext(TeamSide.RED, redRoster, redContext);
        validateSeriesHistory(result, history);
        validateFinalDraft(result);
    }

    private Map<Position, Player> validateFrozenTeam(String code, Team team,
            com.lolfm.career.CompetitionRosterSnapshot frozen) {
        Map<Position, Player> result = new EnumMap<>(Position.class);
        if (team.getPlayers().size() != 5) throw failure("INVALID_FROZEN_LINEUP", code);
        var expected = frozen.roster(code).players();
        for (Player player : team.getPlayers()) {
            var starter = expected.stream().filter(p -> p.position() == player.getPosition()).findFirst().orElseThrow();
            if (result.put(player.getPosition(), player) != null
                    || !starter.playerId().equals(player.requirePlayerId().value())
                    || !starter.ratings().equals(player.getRatings().asMap()))
                throw failure("FROZEN_ROSTER_PLAYER_MISMATCH", code);
        }
        return Map.copyOf(result);
    }

    private void validatePreflight(String blueTeamCode, Team blueTeam,
                                   String redTeamCode, Team redTeam,
                                   DraftTeamContext blueContext, DraftTeamContext redContext,
                                   FinalDraftResult draftResult,
                                   SeriesDraftHistory seriesHistory) {
        Objects.requireNonNull(blueTeamCode, "blueTeamCode");
        Objects.requireNonNull(blueTeam, "blueTeam");
        Objects.requireNonNull(redTeamCode, "redTeamCode");
        Objects.requireNonNull(redTeam, "redTeam");
        Objects.requireNonNull(blueContext, "blueContext");
        Objects.requireNonNull(redContext, "redContext");
        Objects.requireNonNull(draftResult, "draftResult");
        Objects.requireNonNull(seriesHistory, "seriesHistory");

        MatchLineupIdentityValidator.validate(blueTeam, redTeam);
        Map<Position, Player> blueRoster = validateRealTeam(TeamSide.BLUE, blueTeamCode, blueTeam);
        Map<Position, Player> redRoster = validateRealTeam(TeamSide.RED, redTeamCode, redTeam);
        validateDraftContext(TeamSide.BLUE, blueRoster, blueContext);
        validateDraftContext(TeamSide.RED, redRoster, redContext);
        validateSeriesHistory(draftResult, seriesHistory);
        validateFinalDraft(draftResult);
    }

    private Map<Position, Player> validateRealTeam(TeamSide side, String teamCode, Team team) {
        if (team.getPlayers().size() != Position.values().length) {
            throw failure("INVALID_REAL_TEAM_LINEUP", side + " expected exactly five players");
        }
        EnumMap<Position, Player> byPosition = new EnumMap<>(Position.class);
        for (Player player : team.getPlayers()) {
            Position position = player.getPosition();
            if (byPosition.put(position, player) != null) {
                throw failure("DUPLICATE_REAL_TEAM_POSITION", side + ":" + position);
            }
            if (!player.hasStablePlayerId()) {
                throw failure("MISSING_STABLE_PLAYER_ID", side + ":" + position);
            }
            PlayerId playerId = player.requirePlayerId();
            PlayerRatingKey ratingKey = new PlayerRatingKey(teamCode, position);
            PlayerId expectedPlayerId = ratings.playerId(ratingKey);
            if (!expectedPlayerId.equals(playerId)
                    || !ratings.currentRatingKey(playerId).equals(ratingKey)) {
                throw failure("PLAYER_ID_RATING_KEY_MISMATCH",
                        playerId + "/" + ratingKey.stableId());
            }
            if (player.getRatings().position() != position
                    || !player.getRatings().asMap().equals(ratings.ratings(ratingKey).asMap())) {
                throw failure("PLAYER_RATING_PROFILE_MISMATCH", ratingKey.stableId());
            }
        }
        if (!byPosition.keySet().equals(EnumSet.allOf(Position.class))) {
            throw failure("INVALID_REAL_TEAM_POSITION_COVERAGE", side.toString());
        }
        return Map.copyOf(byPosition);
    }

    private void validateDraftContext(TeamSide side, Map<Position, Player> roster,
                                      DraftTeamContext context) {
        if (!context.hasStablePlayerIdentities()) {
            throw failure("DRAFT_CONTEXT_IDENTITY_INCOMPLETE", side.toString());
        }
        for (Position position : Position.values()) {
            Player player = roster.get(position);
            PlayerId contextPlayerId = context.playerId(position).orElseThrow(() ->
                    failure("DRAFT_CONTEXT_IDENTITY_INCOMPLETE", side + ":" + position));
            if (!player.requirePlayerId().equals(contextPlayerId)) {
                throw failure("DRAFT_CONTEXT_PLAYER_ID_MISMATCH", side + ":" + position);
            }
            if (!player.getChampionProficiencies().equals(context.proficiencies().get(position))) {
                throw failure("DRAFT_CONTEXT_PROFICIENCY_MISMATCH", side + ":" + position);
            }
        }
    }

    private void validateSeriesHistory(FinalDraftResult result, SeriesDraftHistory history) {
        Set<ChampionId> consumed = history.consumedPicks();
        int expectedConsumedCount = history.committedGameCount()
                * TeamSide.values().length * Position.values().length;
        if (consumed.size() != expectedConsumedCount) {
            throw failure("HARD_FEARLESS_HISTORY_CORRUPT",
                    "games=" + history.committedGameCount() + " picks=" + consumed.size());
        }
        if (!result.hardFearlessExclusions().equals(consumed)) {
            throw failure("HARD_FEARLESS_HISTORY_MISMATCH", "draft exclusions differ from series");
        }
        if (result.bluePicks().stream().anyMatch(consumed::contains)
                || result.redPicks().stream().anyMatch(consumed::contains)) {
            throw failure("HARD_FEARLESS_PICK_REUSE", "completed draft reused a consumed pick");
        }
    }

    private void validateFinalDraft(FinalDraftResult result) {
        if (result.decisions().size() != result.ruleSet().turns().size()
                || result.bluePicks().size() != Position.values().length
                || result.redPicks().size() != Position.values().length) {
            throw failure("INCOMPLETE_FINAL_DRAFT", result.draftIdentity());
        }
        validateSelectionEvidence(result);
        MatchChampionAssignments assignments = Objects.requireNonNull(
                result.matchChampionAssignments(), "matchChampionAssignments");
        if (assignments.selectionMode() != ChampionSelectionMode.EXPLICIT) {
            throw failure("MATCH_ASSIGNMENT_MODE_MISMATCH", assignments.selectionMode().toString());
        }
        Set<PlayerKey> expectedKeys = new LinkedHashSet<>();
        for (TeamSide side : TeamSide.values()) {
            for (Position position : Position.values()) {
                expectedKeys.add(new PlayerKey(side, position));
            }
        }
        if (!assignments.asMap().keySet().equals(expectedKeys)) {
            throw failure("MATCH_ASSIGNMENT_KEY_COVERAGE_MISMATCH",
                    assignments.asMap().keySet().toString());
        }
        validateSideFinalRoles(TeamSide.BLUE, result.bluePicks(),
                result.blueFinalRoleAssignments(), assignments);
        validateSideFinalRoles(TeamSide.RED, result.redPicks(),
                result.redFinalRoleAssignments(), assignments);
    }

    private void validateSelectionEvidence(FinalDraftResult result) {
        if (!result.draftSelectionPolicyId().equals(
                MatchEngineV1Policy.DRAFT_SELECTION_POLICY_ID)
                || !result.draftSelectionPolicyHash().equals(
                MatchEngineV1Policy.DRAFT_SELECTION_POLICY_SHA256)
                || result.selectionTraces().size() != result.decisions().size()
                || !result.selectionTraceHash().matches("[0-9a-f]{64}")) {
            throw failure("DRAFT_SELECTION_EVIDENCE_MISMATCH", result.draftIdentity());
        }
        for (int index = 0; index < result.decisions().size(); index++) {
            var decision = result.decisions().get(index);
            var trace = result.selectionTraces().get(index);
            if (trace.turn() != decision.turn() || trace.side() != decision.side()
                    || trace.actionType() != decision.actionType()
                    || !trace.selectedChampionId().equals(decision.selectedChampionId())
                    || trace.selectedRank() > 3
                    || trace.selectedCanonicalScoreLoss() > 2_000_000L) {
                throw failure("DRAFT_SELECTION_TRACE_MISMATCH", "turn=" + decision.turn());
            }
        }
    }

    private void validateSideFinalRoles(TeamSide side, java.util.List<ChampionId> picks,
                                        Map<ChampionId, Position> finalRoles,
                                        MatchChampionAssignments assignments) {
        if (!finalRoles.keySet().equals(Set.copyOf(picks))) {
            throw failure("FINAL_DRAFT_PICK_ROLE_MISMATCH", side.toString());
        }
        EnumMap<Position, ChampionId> byPosition = new EnumMap<>(Position.class);
        for (Map.Entry<ChampionId, Position> entry : finalRoles.entrySet()) {
            ChampionId prior = byPosition.put(entry.getValue(), entry.getKey());
            if (prior != null) {
                throw failure("FINAL_DRAFT_POSITION_COVERAGE_MISMATCH",
                        side + ":" + entry.getValue());
            }
            ChampionRoleKey roleKey = new ChampionRoleKey(entry.getKey(), entry.getValue());
            if (!champions.supports(roleKey)) {
                throw failure("ILLEGAL_DRAFT_CHAMPION_ROLE", roleKey.stableId());
            }
        }
        if (!byPosition.keySet().equals(EnumSet.allOf(Position.class))) {
            throw failure("FINAL_DRAFT_POSITION_COVERAGE_MISMATCH", side.toString());
        }
        for (Position position : Position.values()) {
            PlayerKey playerKey = new PlayerKey(side, position);
            ChampionAssignment assignment = assignments.get(playerKey);
            if (!assignment.playerKey().equals(playerKey)
                    || assignment.selectedPosition() != position
                    || !assignment.championId().equals(byPosition.get(position))) {
                throw failure("DRAFT_MATCH_ASSIGNMENT_MISMATCH", playerKey.stableId());
            }
        }
    }

    private static RealDraftMatchPreflightException failure(String code, String detail) {
        return new RealDraftMatchPreflightException(code + ": " + detail);
    }
}
