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
        properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR",
                "spring.main.lazy-initialization=true"})
class CareerDomesticExecutionTest {
    @Autowired CareerApplicationService careers;
    @Autowired CareerCompetitionRelationalStore store;
    @Autowired LeagueProductionSnapshotProvider snapshots;
    @Autowired CareerCompetitionAutomatedSeriesKernel auto;
    @Autowired SeriesLifecycleService lifecycle;
    @Autowired SeriesApiV1Facade series;
    @Autowired JdbcLeagueBoundSeriesCheckpointAdapter checkpoints;
    @Autowired CareerInternationalParticipants international;

    @Autowired CareerRosterStore rosters;
    @Autowired CareerMarketStore market;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void oldInternationalFiveAndNewRegistrationPoolHaveDifferentEligibilityBoundaries() {
        var career=careers.create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,"KT 등록 경계","감독","KT",UUID.randomUUID().toString())).career().career();
        CareerCompetitionTestSupport.installEwcExecutionFixture(store,career.careerId(),international,List.of("KT","GEN","T1","HLE","DK","BFX","NS","KRX","BRO","DNS"));
        rosters.change(career.careerId(),new CareerRosterStore.Request("CAREER_ROSTER_COMMAND_V1",2027,"LCK:KT","player-jiwoo","SELECT_STARTER",null,null,rosters.view(career.careerId(),2027).revision(),UUID.randomUUID().toString()));
        assertThat(CareerCompetitionTestSupport.applicableEwcRoster(store,career.careerId(),false).roster("LCK:KT").players()).extracting(CompetitionRosterSnapshot.Starter::playerId).contains("player-fenrir").doesNotContain("player-jiwoo");
        assertThat(CareerCompetitionTestSupport.applicableEwcRoster(store,career.careerId(),true).roster("LCK:KT").players()).extracting(CompetitionRosterSnapshot.Starter::playerId).contains("player-jiwoo");
        rosters.change(career.careerId(),new CareerRosterStore.Request("CAREER_ROSTER_COMMAND_V1",2027,"LCK:KT","player-hwichan","MOVE_SQUAD","LCK:KT",null,rosters.view(career.careerId(),2027).revision(),UUID.randomUUID().toString()));
        rosters.change(career.careerId(),new CareerRosterStore.Request("CAREER_ROSTER_COMMAND_V1",2027,"LCK:KT","player-hwichan","SELECT_STARTER",null,null,rosters.view(career.careerId(),2027).revision(),UUID.randomUUID().toString()));
        assertThat(CareerCompetitionTestSupport.applicableEwcRoster(store,career.careerId(),false).roster("LCK:KT").players()).extracting(CompetitionRosterSnapshot.Starter::playerId).contains("player-bdd","player-jiwoo").doesNotContain("player-hwichan");
        assertThat(rosters.view(career.careerId(),2027).registeredPlayers().get("EWC_LOL")).contains("player-jiwoo").doesNotContain("player-hwichan");
        String id=career.careerId();
        var f=store.load(id,2027).fixtures().stream().filter(x->x.competitionId().equals("EWC_LOL")&&x.lifecycleStatus().equals("READY")&&(x.firstTeamCode().equals("LCK:KT")||x.secondTeamCode().equals("LCK:KT"))).findFirst().orElseThrow();
        var frozen=store.bindFixture(id,2027,f.competitionId(),f.matchId(),snapshots.currentSnapshot(snapshots.currentTeamCodes()),snapshots.currentResourceProvenanceHash());
        String registered=jdbc.queryForObject("SELECT state_json FROM career_international_state WHERE career_id=? AND competition_id='EWC_LOL'",String.class,id);
        var view=market.view(id,2027);
        market.command(id,new CareerMarketStore.Request("CAREER_MARKET_COMMAND_KRW_V1",2027,view.revision(),"RELEASE","player-bdd",null,null,null,null,UUID.randomUUID().toString()));
        org.assertj.core.api.Assertions.assertThatThrownBy(()->CareerCompetitionTestSupport.applicableEwcRoster(store,id,false)).isInstanceOf(CareerException.class);
        assertThat(store.bindFixture(id,2027,f.competitionId(),f.matchId(),snapshots.currentSnapshot(snapshots.currentTeamCodes()),snapshots.currentResourceProvenanceHash()).canonicalText()).isEqualTo(frozen.canonicalText());
        market.command(id,new CareerMarketStore.Request("CAREER_MARKET_COMMAND_KRW_V1",2027,market.view(id,2027).revision(),"SUPPLEMENT","player-hwichan",null,null,null,"EWC_LOL",UUID.randomUUID().toString()));
        assertThat(CareerCompetitionTestSupport.applicableEwcRoster(store,id,false).roster("LCK:KT").players()).extracting(CompetitionRosterSnapshot.Starter::playerId).contains("player-hwichan").doesNotContain("player-bdd");
        assertThat(jdbc.queryForObject("SELECT state_json FROM career_international_state WHERE career_id=? AND competition_id='EWC_LOL'",String.class,id)).isEqualTo(registered);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->market.command(id,new CareerMarketStore.Request("CAREER_MARKET_COMMAND_KRW_V1",2027,market.view(id,2027).revision(),"SUPPLEMENT","player-jiwoo",null,null,null,"EWC_LOL",UUID.randomUUID().toString()))).isInstanceOf(CareerException.class);

    }

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
    @Test
    void foreignRostersRunThroughExistingAutoPlayerReceiptsAndDurableCheckpoint() {
        var career = careers.create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,
                "국제 실행 검증", "감독", "HLE", UUID.randomUUID().toString())).career().career();
        var fixtures = CareerCompetitionTestSupport.installEwcExecutionFixture(store, career.careerId(), international,
                List.of("HLE", "GEN", "T1", "KT", "DK", "BFX", "NS", "KRX", "BRO", "DNS"));
        var snapshot = snapshots.currentSnapshot(snapshots.currentTeamCodes());
        var autoFixture = fixtures.stream().filter(f -> f.executionMode().equals("FULL_AUTO")).findFirst().orElseThrow();
        var automatic = store.bindFixture(career.careerId(), 2027, "EWC_LOL", autoFixture.matchId(), snapshot, snapshots.currentResourceProvenanceHash());
        var evidence = auto.run(automatic);
        var verified = CareerCompetitionTestSupport.verifyAuto(automatic, evidence);
        assertThat(verified.orderedGames()).hasSize(1);
        assertThat(verified.orderedGames().getFirst().blueTeamCode()).contains(":");
        // Application uses the same opaque verified result path as the durable Auto worker.
        CareerCompetitionTestSupport.applyRealAutoCompletion(store, automatic, evidence);
        assertThat(store.hasAppliedCompletion(automatic)).isTrue();
        var playerFixture = fixtures.stream().filter(f -> f.executionMode().equals("PLAYER_CONTROLLED")).findFirst().orElseThrow();
        var player = store.bindFixture(career.careerId(), 2027, "EWC_LOL", playerFixture.matchId(), snapshot, snapshots.currentResourceProvenanceHash());
        assertThat(player.frozenRosters().teams()).hasSize(2);
        assertThat(CareerCompetitionSeriesBindingV1.restoreCanonical(player.canonicalText()).frozenRosters()).isEqualTo(player.frozenRosters());
        lifecycle.createCompetitionBound(player);
        var view = series.get(player.boundSeriesId());
        assertThat(view.managedTeamCode()).isEqualTo("LCK:HLE");
        assertThat(view.opponentTeamCode()).doesNotStartWith("LCK:");
        var draft = series.createDraft(view.seriesId(), new SeriesApiV1Dtos.DraftCreateRequest(
                SeriesApiV1Dtos.DRAFT_CREATE_REQUEST_SCHEMA, view.revision(), "international-draft"));
        view = draft.series(); var child = draft.draftSession().session(); int command = 0;
        assertThat(child.teams()).allSatisfy(team -> {
            assertThat(team.lineup()).hasSize(5);
            assertThat(team.lineup().stream().map(com.lolfm.dto.RealMatchApiV1Dtos.OptionPlayer::playerId)).containsExactlyElementsOf(
                    player.frozenRosters().assemble(team.teamCode()).getPlayers().stream()
                            .sorted(java.util.Comparator.comparing(com.lolfm.domain.Player::getPosition))
                            .map(p -> p.requirePlayerId().value()).toList());
        });
        while (child.status() == PlayerDraftSessionStatus.ACTIVE) {
            var action = series.draftAction(view.seriesId(), 1, new SeriesApiV1Dtos.DraftActionRequest(
                    SeriesApiV1Dtos.DRAFT_ACTION_REQUEST_SCHEMA, view.revision(), child.revision(),
                    "international-action-" + command++, child.selectableChampions().getFirst().champion().championId()));
            view = action.series(); child = action.draftSession().session();
        }
        var result = series.simulate(view.seriesId(), 1, new SeriesApiV1Dtos.SimulateRequest(
                SeriesApiV1Dtos.SIMULATE_REQUEST_SCHEMA, view.revision(), child.revision(), "international-simulate"));
        assertThat(result.response().series().status()).isEqualTo(SeriesStatus.COMPLETED);
        var completed = lifecycle.completedCompetitionEvidence(player, com.lolfm.simulator.SimulationInstrumentation.enabled());
        assertThat(CareerCompetitionTestSupport.verifyPlayer(player, completed).orderedGames()).hasSize(1);
        assertThat(checkpoints.load(player.boundSeriesId()).orElseThrow().frozenCompetitionRosters()).isEqualTo(player.frozenRosters());
        assertThat(lifecycle.resumeCompetitionBound(player).status()).isEqualTo(SeriesStatus.COMPLETED);
    }

}
