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
    @Autowired CareerCalendarApplicationService calendar;@Autowired CareerApplicationService careers;@Autowired CareerClStore cl;@Autowired CareerRosterStore rosters;
    @Autowired CareerMarketStore market;@Autowired CareerDevelopmentStore development;@Autowired CareerLifecycleStore lifecycle;
    @Autowired CareerCompetitionRelationalStore competitions;@Autowired CareerCompetitionExecutionService execution;
    @Autowired LeagueProductionSnapshotProvider snapshots;@Autowired CareerCompetitionPlayerSeriesKernel playerKernel;@Autowired SeriesApiV1Facade facade;@Autowired JdbcTemplate jdbc;
    @Test void demotedStarterRemainsInActualCaptureWithoutClPromiseOrGrowthCredit(){
        var c=careers.create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,"약속 강등 경계","감독","T1",UUID.randomUUID().toString())).career().career();String id=c.careerId();int year=2027;
        var r=rosters.view(id,year);String former=r.state().lineups().get("LCK:T1").stream().filter(p->r.directory().players().get(p).position()==Position.TOP).findFirst().orElseThrow();
        String replacement=r.state().members().values().stream().filter(m->"LCK:T1".equals(m.ownerTeam())&&"DEVELOPMENT".equals(m.squad())&&r.directory().players().get(m.playerId()).position()==Position.TOP).map(CareerRosterStore.Membership::playerId).findFirst().orElseThrow();
        rosters.change(id,new CareerRosterStore.Request("CAREER_ROSTER_COMMAND_V1",year,"LCK:T1",replacement,"MOVE_SQUAD","LCK:T1",null,r.revision(),UUID.randomUUID().toString()));
        rosters.change(id,new CareerRosterStore.Request("CAREER_ROSTER_COMMAND_V1",year,"LCK:T1",replacement,"SELECT_STARTER",null,null,rosters.view(id,year).revision(),UUID.randomUUID().toString()));
        String organization=r.directory().organizations().values().stream().filter(o->"LCK:T1".equals(o.competitiveTeam())&&"DEVELOPMENT".equals(o.kind())).findFirst().orElseThrow().organizationId();
        rosters.change(id,new CareerRosterStore.Request("CAREER_ROSTER_COMMAND_V1",year,"LCK:T1",former,"MOVE_SQUAD",organization,null,rosters.view(id,year).revision(),UUID.randomUUID().toString()));
        var growth=CareerDevelopmentStore.load(jdbc,id);var tx=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        var fixtures=jdbc.query("SELECT fixture_id FROM league_fixture WHERE season_id=? AND (first_team_code='T1' OR second_team_code='T1') ORDER BY round_number,fixture_id LIMIT 6",(v,n)->v.getString(1),c.seasonId());
        for(String fixture:fixtures)tx.executeWithoutResult(t->{rosters.freezeLeagueFixture(c.seasonId(),fixture);CareerAppearanceStore.leagueCompleted(jdbc,c.seasonId(),fixture,"controlled-"+fixture,2);});
        var m=CareerMarketStore.engine(jdbc,id,year,CareerMarketStore.load(jdbc,id));var date=m.state().processedThrough();m.promiseEngine.evaluate(date.plusDays(28));
        var promise=m.promise(former,"LCK:T1",date);assertThat(promise.role()).isEqualTo(CareerMarketState.Role.STARTER);assertThat(promise.opportunities()).isEqualTo(6);assertThat(promise.starts()).isZero();assertThat(promise.sets()).isZero();assertThat(promise.status()).isEqualTo("STARTER_PROMISE_BREACH");
        assertThat(m.promise(replacement,"LCK:T1",date).opportunities()).isZero();assertThat(CareerDevelopmentStore.load(jdbc,id)).isEqualTo(growth);
        assertThat(m.management().appearances().values()).allSatisfy(a->assertThat(a.opportunities().stream().filter(CareerManagementState.Opportunity::selected)).hasSize(10));
    }
    @Test void weeklyAiPromotionUsesSavedGrowthAndNextFrozenInputAtomically() throws Exception {
        var c=careers.create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,"AI 선수단 계획","감독","GEN",UUID.randomUUID().toString())).career().career();String id=c.careerId();int year=2027;
        var tx=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));var r=rosters.view(id,year);String team="LCK:T1";
        String previous=r.state().lineups().get(team).stream().filter(p->r.directory().players().get(p).position()==Position.TOP).findFirst().orElseThrow();
        var candidates=r.state().members().values().stream().filter(v->team.equals(v.ownerTeam())&&"DEVELOPMENT".equals(v.squad())&&r.directory().players().get(v.playerId()).position()==Position.TOP).map(CareerRosterStore.Membership::playerId).sorted().toList();assertThat(candidates).hasSizeGreaterThanOrEqualTo(2);String promote=candidates.getFirst();
        // Prepared current growth and incumbent ratings, not years of simulated development or altered authoring data.
        tx.executeWithoutResult(t->{var old=CareerDevelopmentStore.load(jdbc,id);var growth=new CareerDevelopmentEngine(CareerRosterStore.baseDirectory(jdbc,id),old.state());
            for(String p:List.of(previous,promote)){var v=growth.players.get(p);var ratings=new EnumMap<>(v.internalRatings());ratings.replaceAll((k,x)->p.equals(promote)?16000:12000);growth.players.put(p,new CareerDevelopmentState.Player(ratings,v.internalProficiencies(),v.remainder(),v.proficiencyRemainders(),v.cursors(),v.fatigue(),v.playedOn(),v.override(),v.growthSubRemainder()));}CareerDevelopmentStore.persist(jdbc,id,old,growth);});
        var fixtures=jdbc.query("SELECT fixture_id FROM league_fixture WHERE season_id=? AND (first_team_code='T1' OR second_team_code='T1') ORDER BY round_number,fixture_id LIMIT 2",(v,n)->v.getString(1),c.seasonId());
        var original=tx.execute(t->rosters.freezeLeagueFixture(c.seasonId(),fixtures.getFirst()));var source=CareerMarketStore.load(jdbc,id);var date=source.state().processedThrough().plusDays(1).with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY));
        assertThatThrownBy(()->tx.executeWithoutResult(t->{CareerRosterStore.lockCareer(jdbc,id);CareerMarketStore.processThrough(jdbc,id,date);throw new IllegalStateException("PLAN_ROLLBACK");})).hasMessage("PLAN_ROLLBACK");assertThat(CareerMarketStore.load(jdbc,id)).isEqualTo(source);
        while(calendar.currentDate(c).isBefore(date)){var today=calendar.view(c).state();calendar.advance(c,CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,today.calendarRevision(),"ADVANCE_ONE_DAY",UUID.randomUUID().toString());}
        assertThat(calendar.currentDate(c)).isEqualTo(date);
        var after=rosters.view(id,year);assertThat(after.state().lineups().get(team)).contains(promote).doesNotContain(previous);assertThat(after.state().lineups().get("LCK:GEN")).isEqualTo(r.state().lineups().get("LCK:GEN"));
        assertThat(cl.view(id,year).clubs().stream().filter(v->v.team().equals(team)).findFirst().orElseThrow().blockers()).isEmpty();
        var next=tx.execute(t->rosters.freezeLeagueFixture(c.seasonId(),fixtures.get(1)));assertThat(next.roster("T1").players()).anyMatch(p->p.playerId().equals(promote)&&p.ratings().equals(after.directory().players().get(promote).gameplay().ratings()));
        assertThat(rosters.leagueFixtureRoster(c.seasonId(),fixtures.getFirst())).isEqualTo(original);
        var saved=CareerMarketStore.load(jdbc,id);tx.executeWithoutResult(t->CareerMarketStore.processThrough(jdbc,id,date));assertThat(CareerMarketStore.load(jdbc,id)).isEqualTo(saved);
        var view=market.view(id,year);assertThat(view.squadOperations()).anyMatch(d->promote.equals(d.playerId())&&d.action().equals("PROMOTE")&&d.status().equals("APPLIED"));assertThat(CareerMarketStore.load(jdbc,id)).isEqualTo(saved);
        System.out.println("AI_PLANNING_ACTUAL career="+id+" date="+date+" before="+previous+" after="+promote+" lineup="+after.state().lineups().get(team)+" cl="+cl.view(id,year).clubs().stream().filter(v->v.team().equals(team)).toList()+" operations="+CareerRosterStore.write(saved.state().squadPlanning()));
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/reports/career-ai"));jdbc.execute("SCRIPT TO 'build/reports/career-ai/browser-fixture.sql'");java.nio.file.Files.writeString(java.nio.file.Path.of("build/reports/career-ai/browser-fixture-info.txt"),"career="+id+"\nteam="+team+"\nplayer="+promote);
    }
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
    @Test void controlledNinetySeriesTieGraphAndFivePlayoffsCloseExactlyOnce() throws Exception {
        var career=careers.create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,"CL 통제 대진","감독","T1",UUID.randomUUID().toString())).career().career();String id=career.careerId();int year=2027;
        var snapshot=snapshots.currentSnapshot(snapshots.currentTeamCodes());var tx=new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));int completed=0;
        while(true){var cycle=competitions.load(id,year);var next=cycle.fixtures().stream().filter(f->CareerClPolicy.isCl(f.competitionId())&&!"COMPLETED".equals(f.lifecycleStatus())).findFirst();if(next.isEmpty())break;var f=next.get();
            // Synthetic graph-only evidence uses the existing test completion bridge; it creates no appearance binding or growth.
            var instance=cycle.competitions().stream().filter(c->CareerClPolicy.isCl(c.competitionId())).findFirst().orElseThrow();
            var binding=CareerCompetitionSeriesBindingV1.create(cycle,instance,f,"T1",competitions.rules.resourceHash(),snapshot,snapshots.currentResourceProvenanceHash(),Set.of(),null);
            tx.executeWithoutResult(t->{jdbc.update("INSERT INTO career_competition_series_binding(binding_hash,career_id,calendar_season_year,competition_id,match_id,fixture_id,series_id,execution_mode,binding_schema,binding_canonical,lifecycle_status,created_at,updated_at) VALUES (?,?,?,'LCK_CL',?,?,?,?,?,?,'CREATED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",binding.bindingHash(),id,year,f.matchId(),f.fixtureId(),f.seriesId(),f.executionMode(),CareerCompetitionSeriesBindingV1.SCHEMA,binding.canonicalText());CareerCompetitionTestSupport.applySyntheticVerifiedCompletion(competitions,binding,"T1".equals(f.secondTeamCode())?f.secondTeamCode():f.firstTeamCode());});
            if(++completed>140)throw new AssertionError("CL finite graph did not terminate");
        }
        var finalView=cl.view(id,year);assertThat(finalView.fixtures().stream().filter(f->f.stageId().equals("CL_REGULAR"))).hasSize(90);assertThat(finalView.fixtures().stream().filter(f->f.stageId().equals("CL_PLAYOFFS"))).hasSize(5);assertThat(finalView.fixtures().stream().filter(f->f.stageId().equals("CL_TIEBREAKER"))).isNotEmpty();assertThat(finalView.ranking()).hasSize(10).doesNotHaveDuplicates();
        assertThat(competitions.load(id,year).competitions().stream().filter(c->CareerClPolicy.isCl(c.competitionId())).findFirst().orElseThrow().lifecycleStatus()).isEqualTo("COMPLETED");
        assertThat(CareerMarketStore.load(jdbc,id).state().management().appearances()).isEmpty();
        var finance=CareerMarketStore.load(jdbc,id).state().finance();
        assertThat(finance.awards()).hasSize(10);assertThat(finance.awards().values().stream().mapToLong(CareerFinanceState.Award::krw).sum()).isEqualTo(87_500_000);
        assertThat(finance.awards().values().stream().filter(a->a.placementFrom()==3)).hasSize(2).allSatisfy(a->{assertThat(a.placementThrough()).isEqualTo(4);assertThat(a.krw()).isEqualTo(11_250_000);assertThat(a.allocationPolicy()).isEqualTo("GAME_SHARED_PLACEMENT_POOL");});
        var own=finance.awards().values().stream().filter(a->a.team().equals("LCK:T1")).findFirst().orElseThrow();
        assertThat(own.paidOn()).isNull();assertThat(own.recognizedOn()).isEqualTo(calendar.currentDate(career));assertThat(own.dueOn()).isEqualTo(own.recognizedOn().plusDays(7));
        long initialCash=CareerMarketStore.load(jdbc,id).state().accounts().get("LCK:T1").cash();
        var before=CareerMarketStore.load(jdbc,id);market.view(id,year);tx.executeWithoutResult(t->CareerFinanceStore.recognize(competitions,id,year));assertThat(CareerMarketStore.load(jdbc,id)).isEqualTo(before);
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/reports/career-finance"));
        jdbc.execute("SCRIPT TO 'build/reports/career-finance/browser-fixture.sql'");
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/reports/career-finance/browser-fixture-info.txt"),"career="+id+"\ndue="+own.dueOn()+"\nprize="+own.krw()+"\ncash="+initialCash);
        while(calendar.currentDate(career).isBefore(own.dueOn())){var today=calendar.view(career).state();calendar.advance(career,CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,today.calendarRevision(),"ADVANCE_ONE_DAY",UUID.randomUUID().toString());}
        var paid=CareerMarketStore.load(jdbc,id);assertThat(paid.state().finance().awards().get(own.id()).paidOn()).isEqualTo(own.dueOn());
        assertThat(paid.state().ledger().stream().filter(l->l.team().equals("LCK:T1")&&l.kind().equals("TOURNAMENT_PLACEMENT_PRIZE"))).hasSize(1);
        var previousIds=before.state().ledger().stream().map(CareerMarketState.Ledger::entryId).collect(java.util.stream.Collectors.toSet());
        long otherCashFlow=paid.state().ledger().stream().filter(l->l.team().equals("LCK:T1")&&!previousIds.contains(l.entryId())&&!Set.of("SALARY_ACCRUED","TOURNAMENT_PLACEMENT_PRIZE").contains(l.kind())).mapToLong(CareerMarketState.Ledger::amount).sum();
        assertThat(own.krw()).isEqualTo(40_000_000);assertThat(paid.state().accounts().get("LCK:T1").cash()).isEqualTo(initialCash+own.krw()+otherCashFlow);
        var projected=CareerMarketStore.engine(jdbc,id,year,paid);long available=projected.paymentHeadroom("LCK:T1",own.dueOn());var account=projected.accounts.get("LCK:T1");projected.accounts.put("LCK:T1",new CareerMarketState.Account(account.team(),account.annualBudget(),account.cash()-own.krw(),account.rosterLimit()));
        assertThat(available-projected.paymentHeadroom("LCK:T1",own.dueOn())).isEqualTo(own.krw());
        tx.executeWithoutResult(t->CareerFinanceStore.recognize(competitions,id,year));assertThat(CareerMarketStore.load(jdbc,id)).isEqualTo(paid);
        System.out.println("FINANCE_CL_ACTUAL "+CareerRosterStore.write(own)+" cashBefore="+initialCash+" cashAfter="+paid.state().accounts().get("LCK:T1").cash()+" headroom="+market.view(id,year).finances().stream().filter(f->f.team().equals("LCK:T1")).findFirst().orElseThrow().paymentHeadroom());
        System.out.println("CL_CONTROLLED_GRAPH regular=90 playoffs=5 total="+completed+" ranking="+finalView.ranking());
    }

}
