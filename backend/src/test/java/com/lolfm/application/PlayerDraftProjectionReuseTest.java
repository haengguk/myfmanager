package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.domain.Team;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.dto.PlayerDraftApiV1Dtos;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.TeamSide;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR"})
class PlayerDraftProjectionReuseTest {
    @Autowired PlayerDraftApiV1Service service;
    @Autowired PlayerDraftSessionRepository sessions;
    @Autowired PlayerDraftApiV1ResponseMapper responses;
    @Autowired PlayerControlledDraftEngine drafts;
    @Autowired LckTeamAssembler teams;

    @Test
    void oneRevisionProjectionIsReusedByGetActionAndExactReplayThenReleasedAtTerminal() {
        PlayerDraftSessionView started = service.start(start(TeamSide.BLUE));
        PlayerDraftSession current = sessions.get(started.sessionId());
        assertThat(current.selectionProjection()).isNotNull();
        assertThat(started.selectionView()).isSameAs(current.selectionProjection().view());
        assertThat(responses.session(started)).isEqualTo(responses.session(service.get(
                started.sessionId())));

        var chosen = current.selectionProjection().view().selectable().getFirst().championId();
        var request = action(current.revision(), "projection-replay-1", chosen.value());
        PlayerDraftSessionView first = service.action(current.sessionId(), request);
        PlayerDraftSession after = sessions.get(current.sessionId());
        PlayerDraftSessionView replay = service.action(current.sessionId(), request);
        assertThat(first).isEqualTo(replay);
        assertThat(sessions.get(current.sessionId())).isSameAs(after);
        assertThat(first.selectionView()).isSameAs(after.selectionProjection().view());
        assertThat(replay.selectionView()).isSameAs(after.actionReceipts()
                .get("projection-replay-1").resultingProjection().view());

        int action = 2;
        while (!after.progress().complete()) {
            var champion = after.selectionProjection().view().selectable()
                    .getFirst().championId();
            service.action(after.sessionId(), action(after.revision(),
                    "projection-terminal-" + action++, champion.value()));
            after = sessions.get(after.sessionId());
        }
        assertThat(after.status()).isEqualTo(PlayerDraftSessionStatus.COMPLETED);
        assertThat(after.selectionProjection()).isNull();
        assertThat(after.computationContext()).isNull();
        assertThat(after.completionBinding()).isNotNull();
        var terminal = responses.session(after.view());
        assertThat(terminal.selectableChampions()).isEmpty();
        assertThat(terminal.unavailableChampions()).isEmpty();
        assertThat(terminal.advisoryRecommendations()).isEmpty();
        assertThat(terminal.selectableSetIdentity()).isNull();
        assertThat(after.withStatus(PlayerDraftSessionStatus.CANCELLED)
                .completionBinding()).isNull();
        assertThat(after.withStatus(PlayerDraftSessionStatus.EXPIRED)
                .completionBinding()).isNull();
    }

    @Test
    void projectionTokensAreSessionLocalAndCannotValidateAnotherProgress() {
        PlayerDraftSession left = sessions.get(service.start(start(TeamSide.BLUE)).sessionId());
        PlayerDraftSession right = sessions.get(service.start(start(TeamSide.BLUE)).sessionId());
        assertThat(left.selectionProjection()).isNotSameAs(right.selectionProjection());

        Team blue = teams.assemble("GEN");
        Team red = teams.assemble("T1");
        var champion = left.selectionProjection().view().selectable().getFirst().championId();
        assertThatThrownBy(() -> drafts.selectProjected(
                left.progress(), DraftTeamContext.from(blue), DraftTeamContext.from(red),
                RealDraftSelectionContextFactory.create(
                        73L, "GEN", blue, "T1", red, 1, Set.of()),
                right.selectionProjection(), champion, "cross-session"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PLAYER_DRAFT_SELECTION_PROJECTION_BINDING_MISMATCH");
    }

    private static PlayerDraftApiV1Dtos.StartRequest start(TeamSide side) {
        return new PlayerDraftApiV1Dtos.StartRequest(
                PlayerDraftApiV1Dtos.START_REQUEST_SCHEMA, "GEN", "T1", side, "73");
    }

    private static PlayerDraftApiV1Dtos.ActionRequest action(
            long revision, String actionId, String championId
    ) {
        return new PlayerDraftApiV1Dtos.ActionRequest(
                PlayerDraftApiV1Dtos.ACTION_REQUEST_SCHEMA,
                revision, actionId, championId);
    }
}
