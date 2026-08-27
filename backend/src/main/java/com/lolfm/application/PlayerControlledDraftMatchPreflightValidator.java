package com.lolfm.application;

import com.lolfm.domain.Team;
import com.lolfm.draft.DraftSelectionContext;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.draft.PlayerControlledDraftResult;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Fresh reconstruction boundary before mixed Draft assignments reach Match Engine V1. */
@Component
final class PlayerControlledDraftMatchPreflightValidator {
    private final PlayerControlledDraftEngine drafts;

    PlayerControlledDraftMatchPreflightValidator(PlayerControlledDraftEngine drafts) {
        this.drafts = Objects.requireNonNull(drafts, "drafts");
    }

    void validate(
            Team blueTeam,
            Team redTeam,
            DraftTeamContext blueContext,
            DraftTeamContext redContext,
            DraftSelectionContext selectionContext,
            PlayerControlledDraftResult result
    ) {
        Objects.requireNonNull(blueTeam, "blueTeam");
        Objects.requireNonNull(redTeam, "redTeam");
        if (blueTeam.getPlayers().size() != 5 || redTeam.getPlayers().size() != 5
                || !blueContext.hasStablePlayerIdentities()
                || !redContext.hasStablePlayerIdentities()) {
            throw new IllegalArgumentException("PLAYER_DRAFT_REAL_ROSTER_PREFLIGHT_FAILED");
        }
        drafts.validateCompleted(result, blueContext, redContext, selectionContext);
    }
}
