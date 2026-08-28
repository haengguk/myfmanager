package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.dto.SeriesApiV1Dtos;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SeriesProductionV9SmokeTest {
    @Autowired SeriesApiV1Facade series;
    @Autowired SeriesLifecycleService lifecycle;

    @Test
    void genT1Bo3CompletesThroughRealProductionV9AndReplaysWithoutCommit() {
        var created = series.create(new SeriesApiV1Dtos.CreateRequest(
                SeriesApiV1Dtos.CREATE_REQUEST_SCHEMA, SeriesFormat.BO3,
                "GEN", "T1", "GEN", "GEN", "73", "smoke-create"));
        var view = created.series();
        assertThat(view.revision()).isZero();
        assertThat(view.currentGameNumber()).isOne();

        List<SeriesApiV1Dtos.SeriesGameView> committed = new ArrayList<>();
        int command = 0;
        while (view.status() == SeriesStatus.ACTIVE) {
            int gameNumber = view.currentGameNumber();
            var draft = series.createDraft(view.seriesId(),
                    new SeriesApiV1Dtos.DraftCreateRequest(
                            SeriesApiV1Dtos.DRAFT_CREATE_REQUEST_SCHEMA, view.revision(),
                            "smoke-draft-" + gameNumber));
            view = draft.series();
            var child = draft.draftSession().session();
            while (child.status() == PlayerDraftSessionStatus.ACTIVE) {
                String champion = child.selectableChampions().getFirst()
                        .champion().championId();
                var action = series.draftAction(view.seriesId(), gameNumber,
                        new SeriesApiV1Dtos.DraftActionRequest(
                                SeriesApiV1Dtos.DRAFT_ACTION_REQUEST_SCHEMA,
                                view.revision(), child.revision(),
                                "smoke-action-" + command++, champion));
                view = action.series();
                child = action.draftSession().session();
            }
            assertThat(child.decisions()).hasSize(20);
            assertThat(child.completedDraft().finalAssignments()).hasSize(10);
            var simulated = series.simulate(view.seriesId(), gameNumber,
                    new SeriesApiV1Dtos.SimulateRequest(
                            SeriesApiV1Dtos.SIMULATE_REQUEST_SCHEMA,
                            view.revision(), child.revision(),
                            "smoke-simulate-" + gameNumber));
            assertThat(simulated.accepted()).isFalse();
            assertThat(simulated.response().match().integrity().runtimeProfileId())
                    .isEqualTo("PRODUCTION_MATCHUP_COMPOSITION_V1");
            assertThat(simulated.response().match().integrity().engineImplementationVersion())
                    .isEqualTo("MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9");
            assertThat(simulated.response().series().productionIdentity().policyId())
                    .isEqualTo(MatchEngineV1Policy.POLICY_ID);
            assertThat(simulated.response().game().status())
                    .isEqualTo(SeriesGameStatus.COMMITTED);
            committed.add(simulated.response().game());
            assertThat(lifecycle.getGame(view.seriesId(), gameNumber).receipt()
                    .canonicalBytes().length)
                    .isPositive().isLessThanOrEqualTo(SeriesGameReceipt.MAX_CANONICAL_BYTES);
            view = simulated.response().series();
            assertThat(view.excludedChampionIds()).hasSize(committed.size() * 10);
            assertThat(view.score().values().stream().mapToInt(Integer::intValue).sum())
                    .isEqualTo(committed.size());
            if (view.status() == SeriesStatus.ACTIVE) {
                assertThat(view.currentGameNumber()).isEqualTo(committed.size() + 1);
                SeriesApiV1Dtos.SeriesGameView previous = committed.getLast();
                SeriesApiV1Dtos.SeriesGameView current = view.games().getLast();
                assertThat(current.blueTeamCode()).isEqualTo(previous.redTeamCode());
                assertThat(current.redTeamCode()).isEqualTo(previous.blueTeamCode());
                assertThat(new HashSet<>(current.historyBeforeChampionIds()))
                        .containsExactlyInAnyOrderElementsOf(view.excludedChampionIds());
            }
            assertThat(committed).hasSizeLessThanOrEqualTo(3);
        }

        assertThat(view.status()).isEqualTo(SeriesStatus.COMPLETED);
        assertThat(view.winnerTeamCode()).isIn("GEN", "T1");
        assertThat(view.score().get(view.winnerTeamCode())).isEqualTo(2);
        long revision = view.revision();
        var replay = series.replay(view.seriesId(), 1,
                new SeriesApiV1Dtos.ReplayRequest(
                        SeriesApiV1Dtos.REPLAY_REQUEST_SCHEMA, "smoke-replay"));
        assertThat(replay.match().timeline().events()).isNotEmpty();
        assertThat(replay.series().revision()).isEqualTo(revision);
        assertThat(replay.series().score()).isEqualTo(view.score());
        assertThat(replay.series().excludedChampionIds())
                .isEqualTo(view.excludedChampionIds());

        assertThat(java.util.Arrays.stream(SeriesAggregate.class.getRecordComponents())
                .map(RecordComponent::getType)).doesNotContain(MatchEngineV1Output.class);
        assertThat(java.util.Arrays.stream(SeriesGame.class.getRecordComponents())
                .map(RecordComponent::getType)).doesNotContain(MatchEngineV1Output.class);
        assertThat(committed).allSatisfy(game -> assertThat(
                game.receipt().randomTraceHash()).matches("[0-9a-f]{64}"));
        System.out.printf(
                "SERIES_PRODUCTION_V9_SMOKE|format=BO3|rootSeed=73|committedGames=%d"
                        + "|score=%s|history=%d|policy=%s|profile=%s|engine=%s%n",
                committed.size(), view.score(), view.excludedChampionIds().size(),
                view.productionIdentity().policyId(),
                view.productionIdentity().runtimeProfileId(),
                view.productionIdentity().engineImplementationVersion());
    }

    @Test
    void genT1Bo5CompletesThroughRealProductionV9WithLegalGameFivePool() {
        var created = series.create(new SeriesApiV1Dtos.CreateRequest(
                SeriesApiV1Dtos.CREATE_REQUEST_SCHEMA, SeriesFormat.BO5,
                "GEN", "T1", "GEN", "GEN", "73", "smoke-bo5-create"));
        var view = created.series();
        int command = 0;
        int committedGames = 0;
        HashSet<String> gameSeeds = new HashSet<>();

        while (view.status() == SeriesStatus.ACTIVE) {
            int gameNumber = view.currentGameNumber();
            var draft = series.createDraft(view.seriesId(),
                    new SeriesApiV1Dtos.DraftCreateRequest(
                            SeriesApiV1Dtos.DRAFT_CREATE_REQUEST_SCHEMA, view.revision(),
                            "smoke-bo5-draft-" + gameNumber));
            view = draft.series();
            var child = draft.draftSession().session();
            while (child.status() == PlayerDraftSessionStatus.ACTIVE) {
                String champion = child.selectableChampions().getFirst()
                        .champion().championId();
                var action = series.draftAction(view.seriesId(), gameNumber,
                        new SeriesApiV1Dtos.DraftActionRequest(
                                SeriesApiV1Dtos.DRAFT_ACTION_REQUEST_SCHEMA,
                                view.revision(), child.revision(),
                                "smoke-bo5-action-" + command++, champion));
                view = action.series();
                child = action.draftSession().session();
            }
            assertThat(child.decisions()).hasSize(20);
            assertThat(child.completedDraft().finalAssignments()).hasSize(10);
            SeriesApiV1Dtos.SeriesGameView readyGame = view.games().getLast();
            assertThat(readyGame.gameNumber()).isEqualTo(gameNumber);
            assertThat(readyGame.controlledSide()).isEqualTo(
                    readyGame.blueTeamCode().equals("GEN")
                            ? com.lolfm.simulator.TeamSide.BLUE
                            : com.lolfm.simulator.TeamSide.RED);
            assertThat(readyGame.historyBeforeChampionIds())
                    .hasSize(committedGames * 10);
            assertThat(gameSeeds.add(readyGame.matchSeed())).isTrue();
            var simulated = series.simulate(view.seriesId(), gameNumber,
                    new SeriesApiV1Dtos.SimulateRequest(
                            SeriesApiV1Dtos.SIMULATE_REQUEST_SCHEMA,
                            view.revision(), child.revision(),
                            "smoke-bo5-simulate-" + gameNumber));
            assertThat(simulated.accepted()).isFalse();
            assertThat(simulated.response().game().status())
                    .isEqualTo(SeriesGameStatus.COMMITTED);
            assertThat(simulated.response().match().integrity().runtimeProfileId())
                    .isEqualTo("PRODUCTION_MATCHUP_COMPOSITION_V1");
            view = simulated.response().series();
            committedGames++;
            assertThat(view.excludedChampionIds()).hasSize(committedGames * 10);
            assertThat(view.games()).hasSize(committedGames
                    + (view.status() == SeriesStatus.ACTIVE ? 1 : 0));
            if (view.status() == SeriesStatus.ACTIVE) {
                assertThat(view.games().getLast().historyBeforeChampionIds())
                        .hasSize(committedGames * 10);
            }
            assertThat(committedGames).isLessThanOrEqualTo(5);
        }

        assertThat(view.status()).isEqualTo(SeriesStatus.COMPLETED);
        assertThat(view.score().get(view.winnerTeamCode())).isEqualTo(3);
        assertThat(committedGames).isBetween(3, 5);
        if (committedGames == 5) {
            assertThat(view.games().get(4).historyBeforeChampionIds()).hasSize(40);
        }
        assertThat(view.excludedChampionIds()).hasSize(committedGames * 10);
        long completedRevision = view.revision();
        Map<String, Integer> completedScore = view.score();
        List<String> completedHistory = view.excludedChampionIds();
        var replay = series.replay(view.seriesId(), 1,
                new SeriesApiV1Dtos.ReplayRequest(
                        SeriesApiV1Dtos.REPLAY_REQUEST_SCHEMA, "smoke-bo5-replay"));
        assertThat(replay.match().timeline().events()).isNotEmpty();
        assertThat(replay.series().revision()).isEqualTo(completedRevision);
        assertThat(replay.series().score()).isEqualTo(completedScore);
        assertThat(replay.series().excludedChampionIds()).isEqualTo(completedHistory);
        System.out.printf(
                "SERIES_PRODUCTION_V9_SMOKE|format=BO5|rootSeed=73|committedGames=%d"
                        + "|score=%s|history=%d|policy=%s|profile=%s|engine=%s%n",
                committedGames, view.score(), view.excludedChampionIds().size(),
                view.productionIdentity().policyId(),
                view.productionIdentity().runtimeProfileId(),
                view.productionIdentity().engineImplementationVersion());
    }
}
