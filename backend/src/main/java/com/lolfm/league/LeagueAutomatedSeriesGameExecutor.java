package com.lolfm.league;

import com.lolfm.application.MatchEngineV1Canonicalizer;
import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.application.PreparedAutoDraftMatch;
import com.lolfm.application.RealDraftMatchOrchestrator;
import com.lolfm.application.SimulationProvenanceService;
import com.lolfm.champion.ChampionId;
import com.lolfm.draft.FinalDraftResult;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.simulator.SimulationInstrumentation;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Package-owned execution port keeps tests bounded without exposing an HTTP/DTO authority. */
interface LeagueAutomatedSeriesGameExecutor {
    boolean canComplete(SeriesDraftHistory history);

    Execution execute(Request request);

    record Request(
            LeagueFixture fixture,
            int gameNumber,
            String blueTeamCode,
            String redTeamCode,
            long gameSeed,
            String matchIdentity,
            SeriesDraftHistory history,
            SimulationInstrumentation instrumentation
    ) {
        public Request {
            Objects.requireNonNull(fixture, "fixture");
            if (gameNumber < 1) throw new IllegalArgumentException("gameNumber");
            LeagueIdentity.requireTeamCode(blueTeamCode);
            LeagueIdentity.requireTeamCode(redTeamCode);
            Objects.requireNonNull(matchIdentity, "matchIdentity");
            Objects.requireNonNull(history, "history");
            Objects.requireNonNull(instrumentation, "instrumentation");
        }
    }

    record Execution(
            FinalDraftResult completedDraft,
            LeagueFixtureGameReceiptV1 gameReceipt
    ) {
        public Execution {
            Objects.requireNonNull(completedDraft, "completedDraft");
            Objects.requireNonNull(gameReceipt, "gameReceipt");
        }
    }
}

/** Reuses the authoritative Production Auto Draft and Match Engine V9 application kernel. */
@Component
final class ProductionLeagueAutomatedSeriesGameExecutor
        implements LeagueAutomatedSeriesGameExecutor {
    private final RealDraftMatchOrchestrator matches;
    private final MatchEngineV1Canonicalizer canonicalizer;

    ProductionLeagueAutomatedSeriesGameExecutor(
            RealDraftMatchOrchestrator matches,
            MatchEngineV1Canonicalizer canonicalizer
    ) {
        this.matches = Objects.requireNonNull(matches, "matches");
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
    }

    @Override
    public boolean canComplete(SeriesDraftHistory history) {
        return matches.canCompleteSeriesDraft(history);
    }

    @Override
    public Execution execute(Request request) {
        Set<ChampionId> historyBefore = request.history().consumedPicks();
        String historyBeforeHash = request.history().identityHash();
        PreparedAutoDraftMatch prepared = matches.prepareV1(
                request.matchIdentity(), request.blueTeamCode(), request.redTeamCode(),
                request.history(), request.gameSeed(), request.instrumentation());
        validate(request, historyBeforeHash, prepared);
        FinalDraftResult draft = prepared.completedDraft();
        HashSet<ChampionId> historyAfter = new HashSet<>(historyBefore);
        historyAfter.addAll(draft.bluePicks());
        historyAfter.addAll(draft.redPicks());
        if (historyAfter.size() != historyBefore.size() + 10) {
            throw new IllegalStateException("LEAGUE_HARD_FEARLESS_TRANSITION_INVALID");
        }
        List<ChampionId> orderedAfter = historyAfter.stream()
                .sorted(java.util.Comparator.comparing(ChampionId::value)).toList();
        return new Execution(draft, LeagueFixtureGameReceiptV1.from(
                prepared.input(), prepared.output(), orderedAfter));
    }

    private void validate(
            Request request,
            String historyBeforeHash,
            PreparedAutoDraftMatch prepared
    ) {
        var input = prepared.input();
        var output = prepared.output();
        var execution = output.executionProvenance();
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();
        boolean valid = prepared.gameNumber() == request.gameNumber()
                && prepared.historyBefore().equals(request.history().consumedPicks())
                && input.matchIdentity().equals(request.matchIdentity())
                && input.blueTeam().teamIdentity().equals(request.blueTeamCode())
                && input.redTeam().teamIdentity().equals(request.redTeamCode())
                && input.matchSeed() == request.gameSeed()
                && input.finalDraft().seriesGameNumber() == request.gameNumber()
                && input.seriesHistoryBeforeHash().equals(historyBeforeHash)
                && output.finalDraft().equals(input.finalDraft())
                && output.productionPolicy().equals(policy)
                && output.configurationHash().equals(policy.configurationHash())
                && execution.runtimeProfileId() == policy.retainedRuntimeProfileId()
                && execution.engineImplementationVersion().equals(
                SimulationProvenanceService.ENGINE_IMPLEMENTATION_VERSION)
                && execution.activeGameplayRulesVersion().equals(
                policy.activeGameplayRulesVersion())
                && execution.blueTeamCode().equals(request.blueTeamCode())
                && execution.redTeamCode().equals(request.redTeamCode())
                && execution.matchSeed() == request.gameSeed()
                && execution.seriesGameNumber() == request.gameNumber()
                && execution.seriesHistoryBeforeHash().equals(historyBeforeHash)
                && output.inputHash().equals(input.inputHash())
                && output.resultSummary().finalDraftHash().equals(
                output.finalDraft().finalDraftHash())
                && output.resultSummary().finalAssignmentHash().equals(
                output.finalDraft().finalAssignmentHash())
                && output.hasValidOutputHash(canonicalizer);
        if (!valid) {
            throw new IllegalStateException("LEAGUE_PRODUCTION_GAME_INTEGRITY_FAILED");
        }
    }
}
