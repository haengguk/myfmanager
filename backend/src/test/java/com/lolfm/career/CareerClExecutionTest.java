package com.lolfm.career;
import com.lolfm.application.*;
import com.lolfm.league.*;
import com.lolfm.dto.*;
import com.lolfm.domain.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"spring.main.banner-mode=off","logging.level.root=ERROR","spring.main.lazy-initialization=true"})
class CareerClExecutionTest {
    @Autowired CareerApplicationService careers;@Autowired CareerClStore cl;@Autowired CareerRosterStore rosters;
    @Autowired CareerMarketStore market;@Autowired CareerDevelopmentStore development;@Autowired CareerLifecycleStore lifecycle;
    @Autowired CareerCompetitionRelationalStore competitions;@Autowired CareerCompetitionExecutionService execution;
    @Autowired LeagueProductionSnapshotProvider snapshots;@Autowired CareerCompetitionPlayerSeriesKernel playerKernel;@Autowired SeriesApiV1Facade facade;@Autowired JdbcTemplate jdbc;
    @Test void realClAutoGrowthPromotionAndNewPlayerInputPreserveOldSeries() throws Exception {
        var career=careers.create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,"CL 실행 경로","감독","T1",UUID.randomUUID().toString())).career().career();String id=career.careerId();int year=2027;
        var tx=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));var original=rosters.view(id,year);var view=cl.view(id,year);assertThat(view.active()).isTrue();assertThat(view.fixtures()).hasSize(90);
        var club=view.clubs().stream().filter(c->c.team().equals("LCK:T1")).findFirst().orElseThrow();var ids=new ArrayList<String>();for(var role:Position.values())ids.add(club.candidates().stream().filter(p->original.directory().players().get(p).position()==role).findFirst().orElseThrow());
        var request=new CareerClStore.Request(year,view.revision(),view.rosterRevision(),"CONFIRM_LINEUP",ids,null,UUID.randomUUID().toString());assertThatThrownBy(()->tx.executeWithoutResult(t->{cl.change(id,request);throw new IllegalStateException("CL_COMMAND_ROLLBACK");})).hasMessage("CL_COMMAND_ROLLBACK");assertThat(cl.view(id,year)).isEqualTo(view);var saved=cl.change(id,request);assertThat(cl.change(id,request).receipt()).isEqualTo(saved.receipt());assertThat(rosters.view(id,year).state().lineups()).isEqualTo(original.state().lineups());
        assertThatThrownBy(()->cl.change(id,new CareerClStore.Request(year,request.expectedRevision(),request.expectedRosterRevision(),"CONFIRM_LINEUP",ids.reversed(),null,request.clientCommandId()))).isInstanceOf(CareerException.class);
        String player=ids.getFirst();var growthBefore=CareerDevelopmentStore.load(jdbc,id).state().players().get(player);var promisesBefore=CareerMarketStore.load(jdbc,id).state().management().promises();
        // Isolate one existing CL fixture in today's slot; all other season games remain unexecuted.
        String match="CL_R01_M1";tx.executeWithoutResult(s->{CareerRosterStore.lockCareer(jdbc,id);jdbc.update("UPDATE career_competition_fixture SET scheduled_date=? WHERE career_id=? AND competition_id='LCK_CL' AND match_id=?",CareerMarketStore.date(jdbc,id),id,match);competitions.refreshInstanceHash(id,year,"LCK_CL");competitions.refreshCycleHash(id,year);});
        view=cl.view(id,year);cl.change(id,new CareerClStore.Request(year,view.revision(),view.rosterRevision(),"SELECT_AUTO",null,match,UUID.randomUUID().toString()));
        var cycle=competitions.load(id,year);String command=UUID.randomUUID().toString();var queued=execution.startOrResume(career,year,CareerMarketStore.date(jdbc,id),cycle.revision(),command);assertThat(queued.executionMode()).isEqualTo("FULL_AUTO");
        var complete=execution.executeAutoJob(queued.jobId());assertThat(complete.status()).isEqualTo("COMPLETED");
        var binding=competitions.loadBinding(id,year,"LCK_CL",match);assertThat(competitions.hasAppliedCompletion(binding)).isTrue();String canonical=binding.canonicalText();
        var after=CareerDevelopmentStore.load(jdbc,id);var appearances=CareerAppearanceStore.performance(jdbc,id,year,player);assertThat(appearances).hasSize(1);assertThat(appearances.getFirst().sets()).isBetween(2,3);assertThat(appearances.getFirst().squad()).isEqualTo("DEVELOPMENT");assertThat(appearances.getFirst().champions().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(appearances.getFirst().sets());
        assertThat(CareerDevelopmentPolicy.sum(after.state().players().get(player))).isGreaterThan(CareerDevelopmentPolicy.sum(growthBefore));assertThat(CareerMarketStore.load(jdbc,id).state().management().promises()).isEqualTo(promisesBefore);
        execution.startOrResume(career,year,CareerMarketStore.date(jdbc,id),cycle.revision(),command);execution.executeAutoJob(queued.jobId());assertThat(CareerDevelopmentStore.load(jdbc,id)).isEqualTo(after);
        var browserFirst=competitions.load(id,year).fixtures().stream().filter(f->f.competitionId().equals("LCK_CUP")&&("T1".equals(f.firstTeamCode())||"T1".equals(f.secondTeamCode()))).findFirst().orElseThrow();
        tx.executeWithoutResult(t->{jdbc.update("UPDATE career_competition_fixture SET scheduled_date=? WHERE career_id=? AND competition_id='LCK_CUP' AND match_id=?",CareerMarketStore.date(jdbc,id).plusDays(1),id,browserFirst.matchId());competitions.refreshInstanceHash(id,year,"LCK_CUP");competitions.refreshCycleHash(id,year);});
        assertThat(cl.result(id,year,match).games()).hasSize(appearances.getFirst().sets());assertThat(cl.result(id,year,match).games()).allSatisfy(g->assertThat(g.picks()).hasSize(10));
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/reports/career-cl"));
        jdbc.execute("SCRIPT TO 'build/reports/career-cl/browser-fixture.sql'");
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/reports/career-cl/browser-fixture-info.txt"),"career="+id+"\nplayer="+player+"\nmatch="+match+"\nfirst="+browserFirst.matchId());
        var r=rosters.view(id,year);rosters.change(id,new CareerRosterStore.Request("CAREER_ROSTER_COMMAND_V1",year,"LCK:T1",player,"MOVE_SQUAD","LCK:T1",null,r.revision(),UUID.randomUUID().toString()));r=rosters.view(id,year);rosters.change(id,new CareerRosterStore.Request("CAREER_ROSTER_COMMAND_V1",year,"LCK:T1",player,"SELECT_STARTER",null,null,r.revision(),UUID.randomUUID().toString()));
        var first=competitions.load(id,year).fixtures().stream().filter(f->f.competitionId().equals("LCK_CUP")&&("T1".equals(f.firstTeamCode())||"T1".equals(f.secondTeamCode()))).findFirst().orElseThrow();
        assertThatThrownBy(()->competitions.bindFixture(id,year,"LCK_CUP",first.matchId(),snapshots.currentSnapshot(snapshots.currentTeamCodes()),snapshots.currentResourceProvenanceHash())).isInstanceOf(CareerException.class);
        tx.executeWithoutResult(s->CareerMarketStore.processThrough(jdbc,id,CareerMarketStore.date(jdbc,id).plusDays(1)));
        var newInput=competitions.bindFixture(id,year,"LCK_CUP",first.matchId(),snapshots.currentSnapshot(snapshots.currentTeamCodes()),snapshots.currentResourceProvenanceHash());playerKernel.start(newInput);
        var frozen=newInput.frozenRosters().roster("T1").players().stream().filter(p->p.playerId().equals(player)).findFirst().orElseThrow();assertThat(frozen.ratings()).isEqualTo(CareerRosterStore.directory(jdbc,id).players().get(player).gameplay().ratings());assertThat(facade.get(newInput.boundSeriesId()).status()).isEqualTo(SeriesStatus.ACTIVE);
        assertThat(competitions.loadBinding(id,year,"LCK_CL",match).canonicalText()).isEqualTo(canonical);assertThat(cl.view(id,year).clubs().stream().filter(c->c.team().equals("LCK:T1")).findFirst().orElseThrow().blockers()).isNotEmpty();
        System.out.println("CL_ACTUAL career="+id+" series="+binding.boundSeriesId()+" player="+player+" before="+CareerRosterStore.write(growthBefore)+" after="+CareerRosterStore.write(after.state().players().get(player))+" performance="+CareerRosterStore.write(appearances)+" firstInput="+newInput.boundSeriesId());
    }
    @Test void controlledNinetySeriesTieGraphAndFivePlayoffsCloseExactlyOnce(){
        var career=careers.create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,"CL 통제 대진","감독","T1",UUID.randomUUID().toString())).career().career();String id=career.careerId();int year=2027;
        var snapshot=snapshots.currentSnapshot(snapshots.currentTeamCodes());var tx=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));int completed=0;
        while(true){var cycle=competitions.load(id,year);var next=cycle.fixtures().stream().filter(f->CareerClPolicy.isCl(f.competitionId())&&!"COMPLETED".equals(f.lifecycleStatus())).findFirst();if(next.isEmpty())break;var f=next.get();
            // Synthetic graph-only evidence uses the existing test completion bridge; it creates no appearance binding or growth.
            var instance=cycle.competitions().stream().filter(c->CareerClPolicy.isCl(c.competitionId())).findFirst().orElseThrow();
            var binding=CareerCompetitionSeriesBindingV1.create(cycle,instance,f,"T1",competitions.rules.resourceHash(),snapshot,snapshots.currentResourceProvenanceHash(),Set.of(),null);
            tx.executeWithoutResult(t->{jdbc.update("INSERT INTO career_competition_series_binding(binding_hash,career_id,calendar_season_year,competition_id,match_id,fixture_id,series_id,execution_mode,binding_schema,binding_canonical,lifecycle_status,created_at,updated_at) VALUES (?,?,?,'LCK_CL',?,?,?,?,?,?,'CREATED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",binding.bindingHash(),id,year,f.matchId(),f.fixtureId(),f.seriesId(),f.executionMode(),CareerCompetitionSeriesBindingV1.SCHEMA,binding.canonicalText());CareerCompetitionTestSupport.applySyntheticVerifiedCompletion(competitions,binding,f.firstTeamCode());});
            if(++completed>140)throw new AssertionError("CL finite graph did not terminate");
        }
        var finalView=cl.view(id,year);assertThat(finalView.fixtures().stream().filter(f->f.stageId().equals("CL_REGULAR"))).hasSize(90);assertThat(finalView.fixtures().stream().filter(f->f.stageId().equals("CL_PLAYOFFS"))).hasSize(5);assertThat(finalView.fixtures().stream().filter(f->f.stageId().equals("CL_TIEBREAKER"))).isNotEmpty();assertThat(finalView.ranking()).hasSize(10).doesNotHaveDuplicates();
        assertThat(competitions.load(id,year).competitions().stream().filter(c->CareerClPolicy.isCl(c.competitionId())).findFirst().orElseThrow().lifecycleStatus()).isEqualTo("COMPLETED");
        assertThat(CareerMarketStore.load(jdbc,id).state().management().appearances()).isEmpty();
        System.out.println("CL_CONTROLLED_GRAPH regular=90 playoffs=5 total="+completed+" ranking="+finalView.ranking());
    }

}
