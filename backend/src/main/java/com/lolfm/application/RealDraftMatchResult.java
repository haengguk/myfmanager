package com.lolfm.application;

import com.lolfm.champion.ChampionId;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Player;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.FinalDraftResult;
import com.lolfm.player.PlayerId;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.TeamSide;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Structured application result without duplicating the Draft-owned champion assignment model. */
public record RealDraftMatchResult(
        String blueTeamCode,
        String redTeamCode,
        Team blueTeam,
        Team redTeam,
        DraftTeamContext blueDraftContext,
        DraftTeamContext redDraftContext,
        FinalDraftResult draftResult,
        MatchTimeline timeline,
        long matchSeed,
        int seriesGameNumber,
        Set<ChampionId> hardFearlessExclusionsBeforeDraft,
        Set<ChampionId> seriesConsumedPicksAfterGame
) {
    public RealDraftMatchResult {
        blueTeamCode = requiredTeamCode(blueTeamCode, "blueTeamCode");
        redTeamCode = requiredTeamCode(redTeamCode, "redTeamCode");
        Objects.requireNonNull(blueTeam, "blueTeam");
        Objects.requireNonNull(redTeam, "redTeam");
        Objects.requireNonNull(blueDraftContext, "blueDraftContext");
        Objects.requireNonNull(redDraftContext, "redDraftContext");
        Objects.requireNonNull(draftResult, "draftResult");
        Objects.requireNonNull(timeline, "timeline");
        if (seriesGameNumber < 1) throw new IllegalArgumentException("seriesGameNumber must be positive");
        hardFearlessExclusionsBeforeDraft = Set.copyOf(hardFearlessExclusionsBeforeDraft);
        seriesConsumedPicksAfterGame = Set.copyOf(seriesConsumedPicksAfterGame);
    }

    /** FinalDraftResult remains the one and only source of match champion assignments. */
    public MatchChampionAssignments matchChampionAssignments() {
        return draftResult.matchChampionAssignments();
    }

    public Map<PlayerKey, PlayerId> playerIdsByMatchSlot() {
        LinkedHashMap<PlayerKey, PlayerId> result = new LinkedHashMap<>();
        addTeamBindings(result, TeamSide.BLUE, blueTeam);
        addTeamBindings(result, TeamSide.RED, redTeam);
        return Collections.unmodifiableMap(result);
    }

    private static void addTeamBindings(Map<PlayerKey, PlayerId> target, TeamSide side, Team team) {
        for (Position position : Position.values()) {
            Player player = team.getPlayers().stream()
                    .filter(value -> value.getPosition() == position)
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "Missing result roster position: " + side + ":" + position));
            target.put(new PlayerKey(side, position), player.requirePlayerId());
        }
    }

    private static String requiredTeamCode(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
