package com.lolfm.application;

import java.util.Map;
import org.springframework.stereotype.Component;

/** Internal restart view for League orchestration; no browser/session pointer is required. */
@Component
public final class LeagueBoundSeriesRecoveryReader {
    private final LeagueBoundSeriesPersistencePort persistence;

    LeagueBoundSeriesRecoveryReader(LeagueBoundSeriesPersistencePort persistence) {
        this.persistence = persistence;
    }

    public RecoveryView load(String seriesId) {
        SeriesAggregate aggregate = persistence.load(seriesId).orElseThrow(() ->
                new IllegalStateException("LEAGUE_BOUND_SERIES_CHECKPOINT_NOT_FOUND"));
        SeriesGame game = aggregate.currentGame();
        SeriesChildDraft child = game.childDraft();
        String nextAction;
        if (aggregate.status() == SeriesStatus.COMPLETED) {
            nextAction = "VERIFY_COMPLETION";
        } else if (game.reservation() != null) {
            nextAction = "WAIT_FOR_SIMULATION_OR_RETRY";
        } else if (child == null) {
            nextAction = "CREATE_DRAFT";
        } else if (child.progress().complete()) {
            nextAction = "SIMULATE";
        } else {
            nextAction = "DRAFT_ACTION";
        }
        return new RecoveryView(aggregate.seriesId(), aggregate.revision(),
                aggregate.status(), Map.copyOf(aggregate.score()),
                aggregate.winnerTeamCode(), aggregate.consumedPicks().size(),
                aggregate.historyHash(), game.gameNumber(), game.matchSeed(),
                game.historyBeforeHash(), game.status(),
                child == null ? null : child.revision(),
                child == null ? 0 : child.progress().turnEvidence().size(),
                child == null ? null : child.status(), nextAction);
    }

    public record RecoveryView(
            String seriesId,
            long revision,
            SeriesStatus status,
            Map<String, Integer> score,
            String winnerTeamCode,
            int excludedChampionCount,
            String historyHash,
            int currentGameNumber,
            long currentGameSeed,
            String historyBeforeHash,
            SeriesGameStatus gameStatus,
            Long draftRevision,
            int draftDecisionCount,
            PlayerDraftSessionStatus draftStatus,
            String allowedNextAction
    ) {}
}
