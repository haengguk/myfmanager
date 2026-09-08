package com.lolfm.career;

import com.lolfm.league.*;
import com.lolfm.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"spring.main.banner-mode=off","logging.level.root=ERROR","spring.main.lazy-initialization=true"})
class CareerOverseasExecutionTest {
    @Autowired CareerApplicationService careers;
    @Autowired CareerOverseasStore overseas;
    @Autowired CareerCompetitionRelationalStore competitions;
    @Autowired CareerMarketStore market;
    @Autowired JdbcTemplate jdbc;
    @Autowired LeagueProductionSnapshotProvider snapshots;
    @Autowired CareerCompetitionExecutionService execution;
    @Autowired CareerCalendarApplicationService calendar;
    private org.springframework.transaction.support.TransactionTemplate transaction(){return new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));}
    @Test void newCareerActivatesSeventeenEventsAndKeepsReadOnlyViews() {
        var c=careers.create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,"해외 리그 저장 연결","감독","T1",UUID.randomUUID().toString())).career().career();
        String id=c.careerId();int year=2027;
        var saved=CareerMarketStore.load(jdbc,id);var cycle=competitions.load(id,year);
        assertThat(saved.state().accounts()).hasSize(59).containsKeys("LPL:OMG","LPL:UP","LEC:LR").doesNotContainKey("LEC:KCB");
        assertThat(cycle.competitions().stream().filter(i->CareerOverseasRules.isOverseas(i.competitionId()))).hasSize(17);
        var v=overseas.view(id,year,"LEC","LEC_VERSUS");
        assertThat(v.active()).isTrue();assertThat(v.activation().activationYear()).isEqualTo(year);
        assertThat(v.events()).hasSize(1);assertThat(v.events().getFirst().result().regularRanking()).isEmpty();
        assertThat(v.events().getFirst().fixtures()).hasSize(66);
        var engine=CareerMarketStore.engine(jdbc,id,year,saved);
        var guest=CareerOverseasRoster.roster(engine,"LEC:KCB");
        assertThat(guest.players()).hasSize(5).allSatisfy(p->assertThat(engine.members.get(p.playerId()).ownerTeam()).isEqualTo("LEC:KC"));
        assertThat(guest.players().stream().map(CompetitionRosterSnapshot.Starter::playerId)).doesNotContainAnyElementsOf(engine.lineups.get("LEC:KC"));
        for(String league:List.of("LPL","LEC","LCP","CBLOL","LCS"))assertThat(overseas.view(id,year,league,null).events()).isNotEmpty();
        overseas.recover();
        assertThat(CareerMarketStore.load(jdbc,id)).isEqualTo(saved);
        assertThat(competitions.load(id,year)).isEqualTo(cycle);
        assertThatThrownBy(()->CareerOverseasQualification.select(competitions,id,year,"FIRST_STAND")).isInstanceOf(CareerOverseasQualification.Waiting.class).hasMessageContaining("LPL_SPLIT_1");
        var snapshot=snapshots.currentSnapshot(snapshots.currentTeamCodes());
        for(var event:List.of(CareerOverseasRules.Event.LPL_SPLIT_1,CareerOverseasRules.Event.LEC_VERSUS,CareerOverseasRules.Event.LCP_SPLIT_1,CareerOverseasRules.Event.CBLOL_COPA,CareerOverseasRules.Event.LCS_LOCK_IN)){
            var target=competitions.load(id,year).fixtures().stream().filter(f->f.competitionId().equals(event.name()))
                .filter(f->!Set.of("LPL:OMG","LPL:UP","LEC:LR").contains(f.firstTeamCode())&&!Set.of("LPL:OMG","LPL:UP","LEC:LR").contains(f.secondTeamCode())).findFirst().orElseThrow();
            transaction().executeWithoutResult(t->{jdbc.update("UPDATE career_competition_fixture SET scheduled_date=? WHERE career_id=? AND competition_id=? AND match_id=?",CareerMarketStore.date(jdbc,id),id,event.name(),target.matchId());competitions.refreshInstanceHash(id,year,event.name());competitions.refreshCycleHash(id,year);});
            var binding=competitions.bindFixture(id,year,event.name(),target.matchId(),snapshot,snapshots.currentResourceProvenanceHash());
            assertThat(binding.frozenRosters().teams()).hasSize(2);assertThat(binding.executionMode()).isEqualTo("FULL_AUTO");
            // Controlled completion checks each adapter. Actual engine evidence is the separate BO matrix.
            if(event==CareerOverseasRules.Event.LCS_LOCK_IN){
                var beforeCycle=competitions.load(id,year);var beforeMarket=CareerMarketStore.load(jdbc,id);var beforeGrowth=CareerDevelopmentStore.load(jdbc,id);
                assertThatThrownBy(()->transaction().executeWithoutResult(t->{CareerCompetitionTestSupport.applySyntheticVerifiedCompletion(competitions,binding,binding.firstTeamCode());throw new IllegalStateException("OVERSEAS_ROLLBACK");})).hasMessage("OVERSEAS_ROLLBACK");
                assertThat(competitions.load(id,year)).isEqualTo(beforeCycle);assertThat(CareerMarketStore.load(jdbc,id)).isEqualTo(beforeMarket);assertThat(CareerDevelopmentStore.load(jdbc,id)).isEqualTo(beforeGrowth);
            }
            transaction().executeWithoutResult(t->CareerCompetitionTestSupport.applySyntheticVerifiedCompletion(competitions,binding,binding.firstTeamCode()));
            assertThat(competitions.hasAppliedCompletion(binding)).isTrue();assertThat(overseas.view(id,year,event.league,event.name()).events().getFirst().scores()).containsKey(target.matchId());
        }
        // New organizations must obtain missing roles through normal, budget-checked AI offers.
        var recruitment=CareerMarketStore.engine(jdbc,id,year,CareerMarketStore.load(jdbc,id));
        recruitment.advance(CareerMarketStore.date(jdbc,id).plusDays(42));
        for(String team:List.of("LPL:OMG","LPL:UP","LEC:LR")){
            assertThat(CareerOverseasRoster.roster(recruitment,team).players()).hasSize(5);
            int initialRoles=(int)CareerOverseasRoster.candidates(engine,team).stream().map(pid->engine.player(pid).position()).distinct().count();
            long negotiated=recruitment.contracts.values().stream().filter(cn->team.equals(cn.team())&&Set.of("NEGOTIATED_FREE_AGENT","PAID_TRANSFER_AGREEMENT").contains(cn.origin())).count()
                +recruitment.tradeEngine.loans.values().stream().filter(loan->team.equals(loan.borrowingTeam())).count();
            assertThat(negotiated).as(team+" normal FA/transfer/loan recruitment").isGreaterThanOrEqualTo(5-initialRoles);
            System.out.println("OVERSEAS_RECRUITMENT "+team+" initialRoles="+initialRoles+" negotiated="+negotiated+" roster="+CareerRosterStore.write(CareerOverseasRoster.roster(recruitment,team).players().stream().map(CompetitionRosterSnapshot.Starter::nickname).toList()));
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={1,3,5})
    void actualAutoSeriesUpdatesBothRostersAndReplaysOnce(int bestOf) {
        var c=careers.create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,"해외 실제 BO"+bestOf,"감독","T1",UUID.randomUUID().toString())).career().career();
        String id=c.careerId();int year=2027;String event=bestOf==1?"LEC_VERSUS":bestOf==3?"LPL_SPLIT_1":"LCP_SPLIT_1";
        var snapshot=snapshots.currentSnapshot(snapshots.currentTeamCodes());String provenance=snapshots.currentResourceProvenanceHash();
        if(bestOf==5){
            // Only the 28 preceding RR scores are controlled. The BO5 below uses the production runner.
            for(var f:competitions.load(id,year).fixtures().stream().filter(f->f.competitionId().equals(event)&&f.stageId().equals("REGULAR")).toList()){
                var cycle=competitions.load(id,year);
                var binding=CareerCompetitionSeriesBindingV1.createInternational(cycle,competitions.instance(id,year,event),f,"LCK:T1",CareerOverseasStore.activation(jdbc,id).ruleHash(),snapshot,provenance,CareerOverseasRoster.pair(jdbc,id,year,f.firstTeamCode(),f.secondTeamCode()));
                transaction().executeWithoutResult(t->{
                    jdbc.update("INSERT INTO career_competition_series_binding(binding_hash,career_id,calendar_season_year,competition_id,match_id,fixture_id,series_id,execution_mode,binding_schema,binding_canonical,lifecycle_status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,'CREATED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",binding.bindingHash(),id,year,event,f.matchId(),f.fixtureId(),f.seriesId(),f.executionMode(),CareerCompetitionSeriesBindingV1.SCHEMA,binding.canonicalText());
                    CareerCompetitionTestSupport.applySyntheticVerifiedCompletion(competitions,binding,f.firstTeamCode());
                });
            }
            assertThat(CareerMarketStore.load(jdbc,id).state().management().appearances()).isEmpty();
        }
        var target=competitions.load(id,year).fixtures().stream().filter(f->f.competitionId().equals(event)&&!f.lifecycleStatus().equals("COMPLETED")&&f.seriesFormat().equals("BO"+bestOf))
                .filter(f->!Set.of("LPL:OMG","LPL:UP","LEC:LR").contains(f.firstTeamCode())&&!Set.of("LPL:OMG","LPL:UP","LEC:LR").contains(f.secondTeamCode())).findFirst().orElseThrow();
        transaction().executeWithoutResult(t->{jdbc.update("UPDATE career_competition_fixture SET scheduled_date=? WHERE career_id=? AND competition_id=? AND match_id=?",CareerMarketStore.date(jdbc,id),id,event,target.matchId());competitions.refreshInstanceHash(id,year,event);competitions.refreshCycleHash(id,year);});
        if(bestOf==1){calendar.advance(c,CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,0,"ADVANCE_ONE_DAY",UUID.randomUUID().toString());assertThat(calendar.view(c).blockingReason()).isEqualTo("AUTO_COMPETITION_FIXTURE_REQUIRED");}
        var cycle=competitions.load(id,year);String command=UUID.randomUUID().toString();var growthBefore=CareerDevelopmentStore.load(jdbc,id);
        var expectedGrowth=new CareerDevelopmentEngine(CareerRosterStore.baseDirectory(jdbc,id),growthBefore.state());
        var queued=execution.startOrResume(c,year,CareerMarketStore.date(jdbc,id),cycle.revision(),command);
        var done=execution.executeAutoJob(queued.jobId());assertThat(done.status()).isEqualTo("COMPLETED");
        if(bestOf==1){assertThat(calendar.view(c).blockingReason()).isNull();assertThat(calendar.view(c).allowedAdvanceModes()).containsExactly("ADVANCE_ONE_DAY","ADVANCE_TO_NEXT_EVENT");}
        var binding=competitions.loadBinding(id,year,event,target.matchId());assertThat(competitions.hasAppliedCompletion(binding)).isTrue();
        var result=overseas.result(id,year,event,target.matchId());assertThat(result.games()).hasSizeBetween(bestOf/2+1,bestOf);
        var marketAfter=CareerMarketStore.load(jdbc,id);var growthAfter=CareerDevelopmentStore.load(jdbc,id);
        var appearance=marketAfter.state().management().appearances().values().stream().filter(a->a.seriesId().equals(binding.boundSeriesId())).findFirst().orElseThrow();
        assertThat(appearance.squad()).isEqualTo("FIRST_TEAM");assertThat(appearance.competitionId()).isEqualTo(event);
        assertThat(appearance.opportunities().stream().filter(CareerManagementState.Opportunity::selected)).hasSize(10);
        for(var game:result.games())for(var pick:game.picks())expectedGrowth.game(pick.playerId(),pick.champion(),pick.position(),appearance.date(),year);
        // The unchanged growth policy includes PA/missing-PA caps. Compare full state, including every unplayed candidate.
        assertThat(growthAfter.state()).isEqualTo(expectedGrowth.state());
        int totalGrowth=0;
        for(var roster:binding.frozenRosters().teams().values())for(var player:roster.players()){
            var performance=CareerAppearanceStore.performance(jdbc,id,year,player.playerId());assertThat(performance).hasSize(1);
            var played=performance.getFirst();assertThat(played.sets()).isEqualTo(result.games().size());assertThat(played.champions().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(played.sets());var before=growthBefore.state().players().get(player.playerId());var after=growthAfter.state().players().get(player.playerId());
            assertThat(played.internalGain()).isEqualTo(CareerDevelopmentPolicy.sum(after)-CareerDevelopmentPolicy.sum(before));assertThat(played.proficiencyGain()).isNotNegative();assertThat(after.fatigue()).isEqualTo(Math.min(1000,before.fatigue()+played.sets()*CareerDevelopmentPolicy.GAME_FATIGUE));totalGrowth+=played.internalGain();
            System.out.println("OVERSEAS_GROWTH "+player.nickname()+" BO"+bestOf+" before="+CareerDevelopmentPolicy.sum(before)+" after="+CareerDevelopmentPolicy.sum(after)+" fatigue="+before.fatigue()+"->"+after.fatigue()+" "+CareerRosterStore.write(played));
        }
        assertThat(totalGrowth).isPositive();
        assertThat(overseas.view(id,year,CareerOverseasRules.Event.valueOf(event).league,event).events().getFirst().scores()).containsKey(target.matchId());
        execution.startOrResume(c,year,CareerMarketStore.date(jdbc,id),cycle.revision(),command);execution.executeAutoJob(queued.jobId());
        assertThat(CareerMarketStore.load(jdbc,id)).isEqualTo(marketAfter);assertThat(CareerDevelopmentStore.load(jdbc,id)).isEqualTo(growthAfter);
        System.out.println("OVERSEAS_ACTUAL BO"+bestOf+" "+CareerRosterStore.write(appearance));
    }

}
