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
        PlayerControlledDraftEngine.AuthoritativeSelectionProjection selectionProjection,
        PlayerDraftCompletionBinding completionBinding,
        PlayerControlledDraftEngine.InteractiveComputationContext computationContext,
        Map<String, ActionReceipt> actionReceipts,
        SimulationReceipt simulationReceipt
) {
    PlayerDraftSession(
            String sessionId, long revision, PlayerDraftSessionStatus status,
            String blueTeamCode, String redTeamCode, TeamSide controlledSide, long matchSeed,
            Instant createdAt, Instant expiresAt, PlayerControlledDraftEngine.Progress progress,
            Map<String, ActionReceipt> actionReceipts, SimulationReceipt simulationReceipt
    ) {
        this(sessionId, revision, status, blueTeamCode, redTeamCode, controlledSide,
                matchSeed, createdAt, expiresAt, progress, null, null, null,
                actionReceipts, simulationReceipt);
    }

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
        if (selectionProjection != null && !selectionProjection.belongsTo(progress)) {
            throw new IllegalArgumentException("PLAYER_DRAFT_SELECTION_PROJECTION_MISMATCH");
        }
        actionReceipts = Map.copyOf(actionReceipts);
    }

    PlayerDraftSession withStatus(PlayerDraftSessionStatus next) {
        if (next != PlayerDraftSessionStatus.ACTIVE && computationContext != null) {
            computationContext.clear();
        }
        return new PlayerDraftSession(sessionId, revision, next, blueTeamCode, redTeamCode,
                controlledSide, matchSeed, createdAt, expiresAt, progress,
                next == PlayerDraftSessionStatus.ACTIVE ? selectionProjection : null,
                next == PlayerDraftSessionStatus.COMPLETED
                        || next == PlayerDraftSessionStatus.SIMULATED
                        ? completionBinding : null,
                next == PlayerDraftSessionStatus.ACTIVE ? computationContext : null,
                actionReceipts, simulationReceipt);
    }

    PlayerDraftSession withAction(
            long nextRevision,
            PlayerControlledDraftEngine.Progress nextProgress,
            PlayerControlledDraftEngine.AuthoritativeSelectionProjection nextProjection,
            PlayerDraftCompletionBinding nextBinding,
            PlayerControlledDraftEngine.InteractiveComputationContext nextContext,
            Map<String, ActionReceipt> receipts
    ) {
        PlayerDraftSessionStatus nextStatus = nextProgress.complete()
                ? PlayerDraftSessionStatus.COMPLETED : PlayerDraftSessionStatus.ACTIVE;
        if (nextStatus != PlayerDraftSessionStatus.ACTIVE && nextContext != null) {
            nextContext.clear();
            nextContext = null;
        }
        return new PlayerDraftSession(sessionId, nextRevision, nextStatus,
                blueTeamCode, redTeamCode, controlledSide, matchSeed, createdAt,
                expiresAt, nextProgress, nextProjection, nextBinding, nextContext,
                receipts, null);
    }

    PlayerDraftSession withAction(
            long nextRevision, PlayerControlledDraftEngine.Progress nextProgress,
            Map<String, ActionReceipt> receipts
    ) {
        return withAction(nextRevision, nextProgress, null, null, null, receipts);
    }

    PlayerDraftSession withSimulationReceipt(SimulationReceipt receipt) {
        return new PlayerDraftSession(sessionId, revision, PlayerDraftSessionStatus.SIMULATED,
                blueTeamCode, redTeamCode, controlledSide, matchSeed, createdAt,
                expiresAt, progress, null, completionBinding, null, actionReceipts,
                Objects.requireNonNull(receipt, "receipt"));
    }

    PlayerDraftSessionView view() {
        return view(revision, status, progress, selectionProjection);
    }

    PlayerDraftSessionView view(
            long selectedRevision,
            PlayerDraftSessionStatus selectedStatus,
            PlayerControlledDraftEngine.Progress selectedProgress,
            PlayerControlledDraftEngine.AuthoritativeSelectionProjection selectedProjection
    ) {
        return new PlayerDraftSessionView(sessionId, selectedRevision, selectedStatus,
                blueTeamCode, redTeamCode, controlledSide, matchSeed, 1,
                createdAt, expiresAt, selectedProgress,
                selectedProjection == null ? null : selectedProjection.view());
    }

    PlayerDraftSessionView view(
            long selectedRevision, PlayerDraftSessionStatus selectedStatus,
            PlayerControlledDraftEngine.Progress selectedProgress
    ) {
        return view(selectedRevision, selectedStatus, selectedProgress, null);
    }

    record ActionReceipt(
            long expectedRevision,
            ChampionId championId,
            long resultingRevision,
            PlayerDraftSessionStatus resultingStatus,
            PlayerControlledDraftEngine.Progress resultingProgress,
            PlayerControlledDraftEngine.AuthoritativeSelectionProjection resultingProjection
    ) {
        ActionReceipt(
                long expectedRevision, ChampionId championId, long resultingRevision,
                PlayerDraftSessionStatus resultingStatus,
                PlayerControlledDraftEngine.Progress resultingProgress
        ) {
            this(expectedRevision, championId, resultingRevision, resultingStatus,
                    resultingProgress, null);
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
