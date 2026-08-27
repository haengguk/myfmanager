package com.lolfm.application;

import com.lolfm.champion.ChampionId;
import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.simulator.TeamSide;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Immutable session aggregate. Mutable ownership remains in the injected repository only. */
record PlayerDraftSession(
        String sessionId,
        long revision,
        PlayerDraftSessionStatus status,
        String blueTeamCode,
        String redTeamCode,
        TeamSide controlledSide,
        long matchSeed,
        Instant createdAt,
        Instant expiresAt,
        PlayerControlledDraftEngine.Progress progress,
        Map<String, ActionReceipt> actionReceipts,
        MatchEngineV1Output simulation
) {
    PlayerDraftSession {
        sessionId = required(sessionId, "sessionId");
        if (revision < 0) throw new IllegalArgumentException("revision");
        Objects.requireNonNull(status, "status");
        blueTeamCode = required(blueTeamCode, "blueTeamCode");
        redTeamCode = required(redTeamCode, "redTeamCode");
        Objects.requireNonNull(controlledSide, "controlledSide");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(progress, "progress");
        actionReceipts = Map.copyOf(actionReceipts);
    }

    PlayerDraftSession withStatus(PlayerDraftSessionStatus next) {
        return new PlayerDraftSession(sessionId, revision, next, blueTeamCode, redTeamCode,
                controlledSide, matchSeed, createdAt, expiresAt, progress,
                actionReceipts, simulation);
    }

    PlayerDraftSession withAction(
            long nextRevision,
            PlayerControlledDraftEngine.Progress nextProgress,
            Map<String, ActionReceipt> receipts
    ) {
        PlayerDraftSessionStatus nextStatus = nextProgress.complete()
                ? PlayerDraftSessionStatus.COMPLETED : PlayerDraftSessionStatus.ACTIVE;
        return new PlayerDraftSession(sessionId, nextRevision, nextStatus,
                blueTeamCode, redTeamCode, controlledSide, matchSeed, createdAt,
                expiresAt, nextProgress, receipts, null);
    }

    PlayerDraftSession withSimulation(MatchEngineV1Output output) {
        return new PlayerDraftSession(sessionId, revision, PlayerDraftSessionStatus.SIMULATED,
                blueTeamCode, redTeamCode, controlledSide, matchSeed, createdAt,
                expiresAt, progress, actionReceipts, Objects.requireNonNull(output, "output"));
    }

    PlayerDraftSessionView view() {
        return view(revision, status, progress);
    }

    PlayerDraftSessionView view(
            long selectedRevision,
            PlayerDraftSessionStatus selectedStatus,
            PlayerControlledDraftEngine.Progress selectedProgress
    ) {
        return new PlayerDraftSessionView(sessionId, selectedRevision, selectedStatus,
                blueTeamCode, redTeamCode, controlledSide, matchSeed, 1,
                createdAt, expiresAt, selectedProgress);
    }

    record ActionReceipt(
            long expectedRevision,
            ChampionId championId,
            long resultingRevision,
            PlayerDraftSessionStatus resultingStatus,
            PlayerControlledDraftEngine.Progress resultingProgress
    ) {
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
