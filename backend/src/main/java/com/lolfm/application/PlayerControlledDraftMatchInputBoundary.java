package com.lolfm.application;

import com.lolfm.domain.Team;
import com.lolfm.draft.DraftSelectionContext;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.draft.PlayerControlledDraftResult;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.TeamSide;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** The only production boundary from a raw mixed Draft result to Match Engine input. */
@Component
public final class PlayerControlledDraftMatchInputBoundary {
    private final LckTeamAssembler teams;
    private final PlayerControlledDraftEngine drafts;
    private final MatchEngineV1InputFactory inputs;

    public PlayerControlledDraftMatchInputBoundary(
            LckTeamAssembler teams,
            PlayerControlledDraftEngine drafts,
            MatchEngineV1InputFactory inputs
    ) {
        this.teams = Objects.requireNonNull(teams, "teams");
        this.drafts = Objects.requireNonNull(drafts, "drafts");
        this.inputs = Objects.requireNonNull(inputs, "inputs");
    }

    public MatchEngineV1Input validateAndCreateInput(
            String blueTeamCode,
            String redTeamCode,
            long matchSeed,
            PlayerControlledDraftResult result
    ) {
        blueTeamCode = canonicalTeamCode(blueTeamCode, "blueTeamCode");
        redTeamCode = canonicalTeamCode(redTeamCode, "redTeamCode");
        if (blueTeamCode.equals(redTeamCode)) {
            throw new IllegalArgumentException("PLAYER_DRAFT_TEAM_IDENTITY_COLLISION");
        }
        Objects.requireNonNull(result, "result");
        Team blueTeam = teams.assemble(blueTeamCode);
        Team redTeam = teams.assemble(redTeamCode);
        DraftTeamContext blueContext = DraftTeamContext.from(blueTeam);
        DraftTeamContext redContext = DraftTeamContext.from(redTeam);
        if (blueTeam.getPlayers().size() != 5 || redTeam.getPlayers().size() != 5
                || !blueContext.hasStablePlayerIdentities()
                || !redContext.hasStablePlayerIdentities()) {
            throw new IllegalArgumentException("PLAYER_DRAFT_REAL_ROSTER_PREFLIGHT_FAILED");
        }
        DraftSelectionContext selectionContext = RealDraftSelectionContextFactory.create(
                matchSeed, blueTeamCode, blueTeam, redTeamCode, redTeam, 1, Set.of());
        drafts.validateCompleted(result, blueContext, redContext, selectionContext);
        return inputs.fromValidatedPlayerControlledDraft(new ValidatedDraft(
                blueTeamCode, blueTeam, redTeamCode, redTeam, matchSeed, result));
    }

    MatchEngineV1Input validateAndCreateSeriesInput(
            SeriesPlayerDraftBinding binding,
            PlayerControlledDraftResult result
    ) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(result, "result");
        String blueTeamCode = canonicalTeamCode(binding.blueTeamCode(), "blueTeamCode");
        String redTeamCode = canonicalTeamCode(binding.redTeamCode(), "redTeamCode");
        if (blueTeamCode.equals(redTeamCode)) {
            throw new IllegalArgumentException("PLAYER_DRAFT_TEAM_IDENTITY_COLLISION");
        }
        Team blueTeam = teams.assemble(blueTeamCode);
        Team redTeam = teams.assemble(redTeamCode);
        DraftTeamContext blueContext = DraftTeamContext.from(blueTeam);
        DraftTeamContext redContext = DraftTeamContext.from(redTeam);
        DraftSelectionContext selectionContext = RealDraftSelectionContextFactory.create(
                binding.matchSeed(), blueTeamCode, blueTeam, redTeamCode, redTeam,
                binding.gameNumber(), binding.hardFearlessExclusions());
        if (!selectionContext.seriesHistoryBeforeHash().equals(binding.historyBeforeHash())
                || result.controlledSide() != binding.controlledSide()) {
            throw new IllegalArgumentException("SERIES_DRAFT_BINDING_MISMATCH");
        }
        drafts.validateCompletedSeries(result, blueContext, redContext, selectionContext,
                binding.hardFearlessExclusions());
        return inputs.fromValidatedSeriesPlayerControlledDraft(new ValidatedSeriesDraft(
                binding, blueTeam, redTeam, result));
    }

    /** Only this enclosing validator can construct the token accepted by the unchecked projector. */
    static final class ValidatedDraft {
        private final String blueTeamCode;
        private final Team blueTeam;
        private final String redTeamCode;
        private final Team redTeam;
        private final long matchSeed;
        private final PlayerControlledDraftResult result;

        private ValidatedDraft(
                String blueTeamCode, Team blueTeam,
                String redTeamCode, Team redTeam,
                long matchSeed, PlayerControlledDraftResult result
        ) {
            this.blueTeamCode = blueTeamCode;
            this.blueTeam = blueTeam;
            this.redTeamCode = redTeamCode;
            this.redTeam = redTeam;
            this.matchSeed = matchSeed;
            this.result = result;
        }

        String blueTeamCode() {
            return blueTeamCode;
        }

        Team blueTeam() {
            return blueTeam;
        }

        String redTeamCode() {
            return redTeamCode;
        }

        Team redTeam() {
            return redTeam;
        }

        long matchSeed() {
            return matchSeed;
        }

        PlayerControlledDraftResult result() {
            return result;
        }
    }

    static final class ValidatedSeriesDraft {
        private final SeriesPlayerDraftBinding binding;
        private final Team blueTeam;
        private final Team redTeam;
        private final PlayerControlledDraftResult result;

        private ValidatedSeriesDraft(
                SeriesPlayerDraftBinding binding,
                Team blueTeam,
                Team redTeam,
                PlayerControlledDraftResult result
        ) {
            this.binding = binding;
            this.blueTeam = blueTeam;
            this.redTeam = redTeam;
            this.result = result;
        }

        SeriesPlayerDraftBinding binding() { return binding; }
        Team blueTeam() { return blueTeam; }
        Team redTeam() { return redTeam; }
        PlayerControlledDraftResult result() { return result; }
    }

    record SeriesPlayerDraftBinding(
            String seriesId,
            String gameId,
            int gameNumber,
            String blueTeamCode,
            String redTeamCode,
            TeamSide controlledSide,
            long matchSeed,
            Set<com.lolfm.champion.ChampionId> hardFearlessExclusions,
            String historyBeforeHash
    ) {
        SeriesPlayerDraftBinding {
            seriesId = required(seriesId, "seriesId");
            gameId = required(gameId, "gameId");
            if (gameNumber < 1) throw new IllegalArgumentException("gameNumber");
            Objects.requireNonNull(controlledSide, "controlledSide");
            hardFearlessExclusions = Set.copyOf(hardFearlessExclusions);
            if (historyBeforeHash == null || !historyBeforeHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("historyBeforeHash");
            }
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty() || !normalized.equals(value)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }

    private static String canonicalTeamCode(String value, String field) {
        return required(value, field).toUpperCase(Locale.ROOT);
    }
}
