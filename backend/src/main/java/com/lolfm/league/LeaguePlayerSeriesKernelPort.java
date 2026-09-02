package com.lolfm.league;

import com.lolfm.application.MatchEngineV1Input;
import com.lolfm.application.MatchEngineV1Output;
import com.lolfm.application.SeriesFormat;
import com.lolfm.application.SeriesGameReceipt;
import com.lolfm.application.SeriesStatus;
import com.lolfm.champion.ChampionId;
import com.lolfm.draft.PlayerControlledDraftResult;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.TeamSide;
import java.util.List;
import java.util.Map;

/** Internal kernel port; no public HTTP setup fields cross this boundary. */
public interface LeaguePlayerSeriesKernelPort {
    boolean canCompleteInitialDraft(LeagueFixtureSeriesBindingV1 binding);

    SeriesReference start(LeagueFixtureSeriesBindingV1 binding);

    SeriesReference resume(LeagueFixtureSeriesBindingV1 binding);

    /** Read-authoritative child Series state used to derive public command eligibility. */
    default SeriesReference inspect(LeagueFixtureSeriesBindingV1 binding) {
        return resume(binding);
    }

    CompletedSeriesEvidence completedEvidence(
            LeagueFixtureSeriesBindingV1 binding,
            SimulationInstrumentation instrumentation
    );

    record SeriesReference(
            String seriesId,
            String bindingHash,
            long revision,
            SeriesStatus status,
            int currentGameNumber,
            boolean replayedStart
    ) {}

    record CompletedSeriesEvidence(
            String seriesId,
            String bindingHash,
            long revision,
            SeriesFormat format,
            String firstTeamCode,
            String secondTeamCode,
            String managedTeamCode,
            long rootSeed,
            Map<String, Integer> score,
            List<ChampionId> consumedPicks,
            String historyHash,
            String winnerTeamCode,
            List<CompletedGameEvidence> orderedGames
    ) {
        public CompletedSeriesEvidence {
            score = Map.copyOf(score);
            consumedPicks = consumedPicks.stream()
                    .sorted(java.util.Comparator.comparing(ChampionId::value)).toList();
            orderedGames = List.copyOf(orderedGames);
        }
    }

    record CompletedGameEvidence(
            int gameNumber,
            String blueTeamCode,
            String redTeamCode,
            TeamSide controlledSide,
            long matchSeed,
            List<ChampionId> historyBefore,
            String historyBeforeHash,
            PlayerControlledDraftResult completedDraft,
            SeriesGameReceipt storedReceipt,
            MatchEngineV1Input verifiedInput,
            MatchEngineV1Output verifiedOutput
    ) {
        public CompletedGameEvidence {
            historyBefore = historyBefore.stream()
                    .sorted(java.util.Comparator.comparing(ChampionId::value)).toList();
        }
    }
}
