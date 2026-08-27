package com.lolfm.application;

import com.lolfm.domain.Team;
import com.lolfm.draft.DraftSelectionContext;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.draft.PlayerControlledDraftResult;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** The only production boundary from a raw mixed Draft result to Match Engine input. */
@Component
public final class PlayerControlledDraftMatchInputBoundary {
    private final PlayerControlledDraftEngine drafts;
    private final MatchEngineV1InputFactory inputs;

    public PlayerControlledDraftMatchInputBoundary(
            PlayerControlledDraftEngine drafts,
            MatchEngineV1InputFactory inputs
    ) {
        this.drafts = Objects.requireNonNull(drafts, "drafts");
        this.inputs = Objects.requireNonNull(inputs, "inputs");
    }

    public MatchEngineV1Input validateAndCreateInput(
            String blueTeamCode,
            Team blueTeam,
            String redTeamCode,
            Team redTeam,
            long matchSeed,
            PlayerControlledDraftResult result
    ) {
        blueTeamCode = required(blueTeamCode, "blueTeamCode");
        redTeamCode = required(redTeamCode, "redTeamCode");
        if (blueTeamCode.equals(redTeamCode)) {
            throw new IllegalArgumentException("PLAYER_DRAFT_TEAM_IDENTITY_COLLISION");
        }
        Objects.requireNonNull(blueTeam, "blueTeam");
        Objects.requireNonNull(redTeam, "redTeam");
        Objects.requireNonNull(result, "result");
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

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty() || !normalized.equals(value)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }
}
