package com.lolfm.application;

import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.simulator.TeamSide;
import java.time.Instant;

public record PlayerDraftSessionView(
        String sessionId,
        long revision,
        PlayerDraftSessionStatus status,
        String blueTeamCode,
        String redTeamCode,
        TeamSide controlledSide,
        long matchSeed,
        int seriesGameNumber,
        Instant createdAt,
        Instant expiresAt,
        PlayerControlledDraftEngine.Progress progress,
        PlayerControlledDraftEngine.SelectionView selectionView
) {
    public PlayerDraftSessionView(
            String sessionId, long revision, PlayerDraftSessionStatus status,
            String blueTeamCode, String redTeamCode, TeamSide controlledSide,
            long matchSeed, int seriesGameNumber, Instant createdAt, Instant expiresAt,
            PlayerControlledDraftEngine.Progress progress
    ) {
        this(sessionId, revision, status, blueTeamCode, redTeamCode, controlledSide,
                matchSeed, seriesGameNumber, createdAt, expiresAt, progress, null);
    }
}
