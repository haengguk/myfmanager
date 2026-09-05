package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import com.lolfm.career.*;
import com.lolfm.league.*;
import com.lolfm.dto.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.datasource.url=jdbc:h2:mem:domestic-engine;DB_CLOSE_DELAY=-1", "logging.level.root=ERROR"})
class CareerDomesticExecutionTest {
    @Autowired CareerApplicationService careers;
    @Autowired CareerCompetitionRelationalStore store;
    @Autowired LeagueProductionSnapshotProvider snapshots;
    @Autowired CareerCompetitionAutomatedSeriesKernel auto;
    @Autowired SeriesLifecycleService lifecycle;
    @Autowired SeriesApiV1Facade series;
    @Autowired JdbcLeagueBoundSeriesCheckpointAdapter checkpoints;

    @Test
    void actualAutoLoserSelectionAndPlayerBo1ReuseParentFearlessAndCheckpoint() {
        var career = careers.create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,
                "국내 실행 검증", "감독", "HLE", UUID.randomUUID().toString())).career().career();
        var fixtures = store.load(career.careerId(), 2027).fixtures();
        var snapshot = snapshots.currentSnapshot(snapshots.currentTeamCodes());
        var binding = CareerCompetitionTestSupport.engineBinding(store, career.careerId(), fixtures.get(0).matchId(),
                "BO3", "GEN", "T1", "HLE", snapshot, snapshots.currentResourceProvenanceHash(), Set.of());
        var evidence = auto.run(binding);
        var verified = CareerCompetitionTestSupport.verifyAuto(binding, evidence);
        assertThat(verified.orderedGames()).hasSizeBetween(2, 3);
        var games = evidence.orderedGames();
        for (int i = 1; i < games.size(); i++) assertThat(games.get(i).redTeamCode()).isEqualTo(games.get(i - 1).winnerTeamCode());
        var inherited = Set.copyOf(games.getLast().historyAfterPicks());
        var player = CareerCompetitionTestSupport.engineBinding(store, career.careerId(), fixtures.get(1).matchId(),
                "BO1", "HLE", "KT", "HLE", snapshot, snapshots.currentResourceProvenanceHash(), inherited);
        assertThat(CareerCompetitionSeriesBindingV1.restoreCanonical(player.canonicalText()).canonicalText()).isEqualTo(player.canonicalText());
        lifecycle.createCompetitionBound(player);
        var view = series.get(player.boundSeriesId());
        assertThat(view.excludedChampionIds()).hasSize(inherited.size());
        assertThat(view.competitionContext().firstPickTeamCode()).isEqualTo("HLE");
        assertThat(view.competitionContext().firstSideChoiceTeamCode()).isEqualTo("KT");
        var draft = series.createDraft(view.seriesId(), new SeriesApiV1Dtos.DraftCreateRequest(
                SeriesApiV1Dtos.DRAFT_CREATE_REQUEST_SCHEMA, view.revision(), "domestic-draft"));
        view = draft.series(); var child = draft.draftSession().session(); int command = 0;
        while (child.status() == PlayerDraftSessionStatus.ACTIVE) {
            var action = series.draftAction(view.seriesId(), 1, new SeriesApiV1Dtos.DraftActionRequest(
                    SeriesApiV1Dtos.DRAFT_ACTION_REQUEST_SCHEMA, view.revision(), child.revision(),
                    "domestic-action-" + command++, child.selectableChampions().getFirst().champion().championId()));
            view = action.series(); child = action.draftSession().session();
        }
        var result = series.simulate(view.seriesId(), 1, new SeriesApiV1Dtos.SimulateRequest(
                SeriesApiV1Dtos.SIMULATE_REQUEST_SCHEMA, view.revision(), child.revision(), "domestic-simulate"));
        assertThat(result.response().series().status()).isEqualTo(SeriesStatus.COMPLETED);
        assertThat(result.response().series().games()).hasSize(1);
        assertThat(result.response().series().excludedChampionIds()).hasSize(inherited.size() + 10);
        var completed = lifecycle.completedCompetitionEvidence(player, com.lolfm.simulator.SimulationInstrumentation.enabled());
        assertThat(CareerCompetitionTestSupport.verifyPlayer(player, completed).orderedGames()).hasSize(1);
        assertThat(checkpoints.load(player.boundSeriesId()).orElseThrow().competitionSidePolicy()).isEqualTo(player.sideSelectionPolicy());
        assertThat(lifecycle.resumeCompetitionBound(player).status()).isEqualTo(SeriesStatus.COMPLETED);
    }
}
