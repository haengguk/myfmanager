package com.lolfm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.draft.DraftEngine;
import com.lolfm.draft.DraftResourceSet;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.FinalDraftResult;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.MatchSimulator;
import com.lolfm.simulator.TeamSide;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Deterministic application flow from authored LCK rosters through DraftEngine into MatchSimulator. */
@Component
public final class RealDraftMatchOrchestrator {
    private final LckTeamAssembler teams;
    private final DraftEngine drafts;
    private final MatchSimulator matches;
    private final RealDraftMatchPreflightValidator preflight;

    @Autowired
    public RealDraftMatchOrchestrator(ObjectMapper mapper, ChampionCatalog champions,
                                      LckTeamAssembler teams, MatchSimulator matches,
                                      RealDraftMatchPreflightValidator preflight) {
        this(teams, new DraftEngine(DraftResourceSet.loadDefault(mapper, champions)), matches, preflight);
    }

    RealDraftMatchOrchestrator(LckTeamAssembler teams, DraftEngine drafts, MatchSimulator matches,
                               RealDraftMatchPreflightValidator preflight) {
        this.teams = Objects.requireNonNull(teams, "teams");
        this.drafts = Objects.requireNonNull(drafts, "drafts");
        this.matches = Objects.requireNonNull(matches, "matches");
        this.preflight = Objects.requireNonNull(preflight, "preflight");
    }

    /** One isolated game with a fresh series scope. */
    public RealDraftMatchResult orchestrate(String blueTeamCode, String redTeamCode, long matchSeed) {
        return orchestrate(blueTeamCode, redTeamCode, new SeriesDraftHistory(), matchSeed);
    }

    /** One game in a caller-owned series; successful completion commits this game's picks. */
    public RealDraftMatchResult orchestrate(String blueTeamCode, String redTeamCode,
                                            SeriesDraftHistory seriesHistory, long matchSeed) {
        Objects.requireNonNull(seriesHistory, "seriesHistory");
        String normalizedBlueTeamCode = normalizeTeamCode(blueTeamCode, "blueTeamCode");
        String normalizedRedTeamCode = normalizeTeamCode(redTeamCode, "redTeamCode");
        Team blueTeam = teams.assemble(normalizedBlueTeamCode);
        Team redTeam = teams.assemble(normalizedRedTeamCode);
        DraftTeamContext blueContext = DraftTeamContext.from(blueTeam);
        DraftTeamContext redContext = DraftTeamContext.from(redTeam);
        Set<ChampionId> exclusionsBeforeDraft = seriesHistory.consumedPicks();
        int gameNumber = seriesHistory.committedGameCount() + 1;

        FinalDraftResult draftResult = drafts.draft(blueContext, redContext, seriesHistory);
        preflight.validate(normalizedBlueTeamCode, blueTeam, normalizedRedTeamCode, redTeam,
                blueContext, redContext, draftResult, seriesHistory);

        // The Draft-owned object is passed through directly; no reinterpretation or reassignment occurs.
        MatchTimeline timeline = matches.simulate(blueTeam, redTeam, matchSeed,
                draftResult.matchChampionAssignments());

        seriesHistory.commitCompleted(draftResult);
        validateCommittedHistory(seriesHistory, draftResult, exclusionsBeforeDraft, gameNumber);
        return new RealDraftMatchResult(normalizedBlueTeamCode, normalizedRedTeamCode,
                blueTeam, redTeam, blueContext, redContext, draftResult,
                timeline, matchSeed, gameNumber, exclusionsBeforeDraft,
                seriesHistory.consumedPicks());
    }

    private static String normalizeTeamCode(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private static void validateCommittedHistory(SeriesDraftHistory history, FinalDraftResult result,
                                                 Set<ChampionId> exclusionsBeforeDraft,
                                                 int gameNumber) {
        if (history.committedGameCount() != gameNumber) {
            throw new IllegalStateException("HARD_FEARLESS_COMMIT_COUNT_MISMATCH");
        }
        LinkedHashSet<ChampionId> expected = new LinkedHashSet<>(exclusionsBeforeDraft);
        expected.addAll(result.bluePicks());
        expected.addAll(result.redPicks());
        if (!history.consumedPicks().equals(Set.copyOf(expected))) {
            throw new IllegalStateException("HARD_FEARLESS_COMMIT_PICK_MISMATCH");
        }
        int expectedCount = gameNumber * TeamSide.values().length * Position.values().length;
        if (expected.size() != expectedCount) {
            throw new IllegalStateException("HARD_FEARLESS_COMMIT_CARDINALITY_MISMATCH");
        }
    }
}
