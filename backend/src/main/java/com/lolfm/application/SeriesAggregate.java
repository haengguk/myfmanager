package com.lolfm.application;

import com.lolfm.champion.ChampionId;
import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.draft.PlayerControlledDraftResult;
import com.lolfm.simulator.TeamSide;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable process-local V1 aggregate. Repository maps own the only mutable references. */
record SeriesAggregate(
        String seriesId,
        long revision,
        SeriesStatus status,
        String terminalReason,
        SeriesFormat format,
        String teamACode,
        String teamBCode,
        String managedTeamCode,
        String game1BlueTeamCode,
        String canonicalRootSeed,
        long rootSeed,
        Map<String, Integer> score,
        List<SeriesGame> games,
        Set<ChampionId> consumedPicks,
        String historyHash,
        String winnerTeamCode,
        Instant createdAt,
        Instant lastActivityAt,
        Instant expiresAt,
        Map<String, SeriesCommandReceipt> commandReceipts
) {
    SeriesAggregate {
        if (revision < 0) throw new IllegalArgumentException("revision");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(format, "format");
        score = Map.copyOf(new LinkedHashMap<>(score));
        games = List.copyOf(games);
        consumedPicks = Set.copyOf(consumedPicks);
        if (!historyHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("historyHash");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(lastActivityAt, "lastActivityAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        commandReceipts = Map.copyOf(new LinkedHashMap<>(commandReceipts));
        validate(format, teamACode, teamBCode, managedTeamCode, game1BlueTeamCode,
                score, games, consumedPicks, historyHash, status, winnerTeamCode);
    }

    int committedGameCount() {
        return (int) games.stream().filter(game -> game.status() == SeriesGameStatus.COMMITTED)
                .count();
    }

    SeriesGame currentGame() {
        return games.getLast();
    }

    SeriesAggregate replaceCurrentGame(
            SeriesGame game, long nextRevision, Instant activity, Instant expiry,
            Map<String, SeriesCommandReceipt> receipts
    ) {
        java.util.ArrayList<SeriesGame> values = new java.util.ArrayList<>(games);
        values.set(values.size() - 1, game);
        return copy(nextRevision, status, terminalReason, score, values, consumedPicks,
                historyHash, winnerTeamCode, activity, expiry, receipts);
    }

    SeriesAggregate copy(
            long nextRevision,
            SeriesStatus nextStatus,
            String nextReason,
            Map<String, Integer> nextScore,
            List<SeriesGame> nextGames,
            Set<ChampionId> nextConsumed,
            String nextHistoryHash,
            String nextWinner,
            Instant activity,
            Instant expiry,
            Map<String, SeriesCommandReceipt> receipts
    ) {
        return new SeriesAggregate(seriesId, nextRevision, nextStatus, nextReason, format,
                teamACode, teamBCode, managedTeamCode, game1BlueTeamCode,
                canonicalRootSeed, rootSeed, nextScore, nextGames, nextConsumed,
                nextHistoryHash, nextWinner, createdAt, activity, expiry, receipts);
    }

    private static void validate(
            SeriesFormat format,
            String teamACode,
            String teamBCode,
            String managedTeamCode,
            String game1BlueTeamCode,
            Map<String, Integer> score,
            List<SeriesGame> games,
            Set<ChampionId> consumedPicks,
            String historyHash,
            SeriesStatus status,
            String winnerTeamCode
    ) {
        if (teamACode.equals(teamBCode) || !score.keySet().equals(Set.of(teamACode, teamBCode))
                || !Set.of(teamACode, teamBCode).contains(managedTeamCode)
                || !Set.of(teamACode, teamBCode).contains(game1BlueTeamCode)) {
            throw new IllegalArgumentException("Series team identity invariant");
        }
        if (games.isEmpty() || games.size() > format.maximumGames()) {
            throw new IllegalArgumentException("Series game cardinality invariant");
        }
        for (int index = 0; index < games.size(); index++) {
            if (games.get(index).gameNumber() != index + 1) {
                throw new IllegalArgumentException("Series game number invariant");
            }
        }
        int committedGameCount = (int) games.stream()
                .filter(game -> game.status() == SeriesGameStatus.COMMITTED).count();
        if (score.values().stream().mapToInt(Integer::intValue).sum() != committedGameCount) {
            throw new IllegalArgumentException("Series score/commit invariant");
        }
        if (consumedPicks.size() != committedGameCount * 10
                || !historyHash.equals(SeriesIdentity.historyHash(
                committedGameCount, consumedPicks))) {
            throw new IllegalArgumentException("Series history invariant");
        }
        if (status == SeriesStatus.COMPLETED
                && (winnerTeamCode == null || score.get(winnerTeamCode) < format.winsRequired())) {
            throw new IllegalArgumentException("Series winner invariant");
        }
    }
}

record SeriesGame(
        String gameId,
        int gameNumber,
        String blueTeamCode,
        String redTeamCode,
        TeamSide controlledSide,
        long matchSeed,
        List<ChampionId> historyBefore,
        String historyBeforeHash,
        SeriesGameStatus status,
        String reason,
        int childGeneration,
        SeriesChildDraft childDraft,
        SeriesSimulationReservation reservation,
        PlayerControlledDraftResult completedDraft,
        MatchEngineV1Output.MatchResultSummaryV1 resultSummary,
        SeriesGameReceipt receipt
) {
    SeriesGame {
        if (gameNumber < 1 || blueTeamCode.equals(redTeamCode)) {
            throw new IllegalArgumentException("Series game identity");
        }
        historyBefore = historyBefore.stream().sorted(java.util.Comparator.comparing(
                ChampionId::value)).toList();
        if (!historyBeforeHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("historyBeforeHash");
        }
        Objects.requireNonNull(status, "status");
        if (status == SeriesGameStatus.COMMITTED
                && (completedDraft == null || resultSummary == null || receipt == null)) {
            throw new IllegalArgumentException("Committed Series game must be compact-complete");
        }
    }
}

record SeriesChildDraft(
        String childId,
        int generation,
        long revision,
        PlayerDraftSessionStatus status,
        Instant createdAt,
        Instant lastActivityAt,
        Instant expiresAt,
        PlayerControlledDraftEngine.Progress progress
) {
    SeriesChildDraft {
        if (generation < 1 || revision < 0) throw new IllegalArgumentException("child identity");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(progress, "progress");
    }

    PlayerDraftSessionView view(SeriesGame game) {
        return new PlayerDraftSessionView(childId, revision, status,
                game.blueTeamCode(), game.redTeamCode(), game.controlledSide(),
                game.matchSeed(), game.gameNumber(), createdAt, expiresAt, progress);
    }
}

record SeriesSimulationReservation(
        String token,
        String commandId,
        String payloadHash,
        long reservedSeriesRevision,
        long expectedDraftRevision,
        Instant createdAt,
        Instant leaseExpiresAt,
        String inputBindingHash
) {
}

record SeriesCommandReceipt(
        String commandId,
        String commandType,
        String payloadHash,
        long resultingSeriesRevision,
        int gameNumber,
        Long resultingDraftRevision,
        String resultIdentity
) {
}
