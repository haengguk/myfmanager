package com.lolfm.career;

import static com.lolfm.career.CareerRosterStore.*;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.time.LocalDate;
import com.lolfm.dto.CareerApiV1Dtos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"spring.main.banner-mode=off","logging.level.root=ERROR","spring.main.lazy-initialization=true","lolfm.career.continuous.background.enabled=false","lolfm.career.competition.background.enabled=false"})
class CareerScoutingTest {
    @Autowired CareerApplicationService careers;@Autowired CareerScoutingService scouting;@Autowired CareerOpponentService opponents;
    @Autowired CareerCalendarApplicationService calendar;@Autowired CareerInboxService inbox;@Autowired CareerCompetitionRelationalStore competitions;
    @Autowired CareerContinuousApplicationService continuous;@Autowired JdbcTemplate db;@Autowired PlatformTransactionManager manager;
    @TempDir java.nio.file.Path temporary;
    CareerRelationalStore.CareerRow create(String name){return careers.create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,name,"감독","GEN",UUID.randomUUID().toString())).career().career();}
    @Test void currentCareerFilteringComparisonAndRevisionedInterestSurviveReopen() {
        var career=create("스카우팅 저장 경계");String id=career.careerId();var page=scouting.search(id,Map.of());
        assertThat(page.population()).isEqualTo(directory(db,id).players().size());assertThat(page.players()).hasSize(20).isSortedAccordingTo(CareerScoutingService.order("ca","desc"));
        assertThat(scouting.search(id,Map.of("status","UNCONFIRMED")).players()).isNotEmpty().allMatch(p->!p.status().equals("FREE_AGENT")&&"AFFILIATION_UNCONFIRMED".equals(p.membership().eligibilityReason()));
        String target=page.starterByPosition().get("MID");assertThat(scouting.search(id,Map.of("negotiable","true","position","MID")).players()).noneMatch(p->p.player().playerId().equals(target));var mids=scouting.search(id,Map.of("position","MID","caMin","50"));
        assertThat(mids.players()).allMatch(p->p.player().position()==com.lolfm.domain.Position.MID&&p.ca()>=50);
        assertThat(scouting.search(id,Map.of("position","MID","cursor","20","asOf",mids.asOf())).players()).noneMatch(p->mids.players().stream().anyMatch(x->x.player().playerId().equals(p.player().playerId())));
        var before=write(calendar.view(career).state());var marketBefore=db.queryForMap("SELECT revision,state_hash FROM career_market_state WHERE career_id=?",id);
        continuous.command(id,new CareerContinuousProgress.Command(CareerContinuousProgress.REQUEST_SCHEMA,"START",UUID.randomUUID().toString(),null,null,CareerContinuousProgress.Mode.NEXT_MANAGED_MATCH,null));
        var lease=write(continuous.view(id).run());
        var add=new CareerScoutingService.InterestRequest(target,0,true);assertThat(scouting.interest(id,add).revision()).isOne();assertThat(scouting.interest(id,add).revision()).isOne();
        assertThat(scouting.interest(id,new CareerScoutingService.InterestRequest(target,1,false)).revision()).isEqualTo(2);assertThat(scouting.interest(id,add).selected()).isFalse();
        scouting.interest(id,new CareerScoutingService.InterestRequest(target,2,true));
        var compare=scouting.compare(id,List.of(target,mids.players().stream().filter(p->!p.player().playerId().equals(target)).findFirst().orElseThrow().player().playerId()),2027,"");
        assertThat(compare.players()).allSatisfy(p->assertThat(p.ca()).isEqualTo(com.lolfm.player.PlayerAbilityPolicy.currentAbility(directory(db,id).players().get(p.player().playerId()).gameplay().ratings())));
        assertThat(compare.statistics()).isEmpty();assertThat(compare.awards()).isEmpty();assertThat(write(compare)).doesNotContain("buyerLimit");
        assertThat(write(calendar.view(career).state())).isEqualTo(before);assertThat(db.queryForMap("SELECT revision,state_hash FROM career_market_state WHERE career_id=?",id)).isEqualTo(marketBefore);assertThat(write(continuous.view(id).run())).isEqualTo(lease);
        assertThatThrownBy(()->scouting.search(id,Map.of("asOf",page.asOf()))).isInstanceOf(CareerException.class);
        assertThatThrownBy(()->scouting.compare(id,List.of(target,page.starterByPosition().get("TOP")),2027,"")).isInstanceOf(CareerException.class);
        var other=create("관심 목록 격리");assertThat(scouting.search(other.careerId(),Map.of("watch","true")).total()).isZero();
        // Tiny metadata-only file reopen: no fake gameplay rollover or long-lived server.
        String url="jdbc:h2:file:"+temporary.resolve("interest");var file=new JdbcTemplate(new DriverManagerDataSource(url,"sa",""));
        file.execute("CREATE TABLE career_scout_interest(career_id VARCHAR,player_id VARCHAR,revision BIGINT,selected BOOLEAN,PRIMARY KEY(career_id,player_id))");
        var saved=scouting.interests(id).get(target);file.update("INSERT INTO career_scout_interest VALUES (?,?,?,?)",id,target,saved.revision(),saved.selected());file.execute("SHUTDOWN");
        var reopened=new CareerScoutingService(new JdbcTemplate(new DriverManagerDataSource(url,"sa","")),new DataSourceTransactionManager(new DriverManagerDataSource(url,"sa","")),null,null);
        assertThat(reopened.interests(id).get(target)).isEqualTo(saved);
    }
    @Test void generatedIdentityUsesCurrentGrowthAndRetirementKeepsInterest() {
        var c=create("생성 선수와 현재 기량");String id=c.careerId();var date=calendar.currentDate(c);int year=2027;
        var old=CareerMarketStore.load(db,id);var m=CareerMarketStore.engine(db,id,year,old);var oldGrowth=CareerDevelopmentStore.load(db,id);var growth=new CareerDevelopmentEngine(baseDirectory(db,id),oldGrowth.state());
        var rookie=CareerRookieFactory.generate(id,m.seed,year,date,Map.of(com.lolfm.domain.Position.MID,1),true,900,new com.lolfm.champion.ChampionCatalog(new com.fasterxml.jackson.databind.ObjectMapper()),List.of("LCK")).getFirst();var d=rookie.definition();String json=write(d);
        new TransactionTemplate(manager).executeWithoutResult(t->{
            db.update("INSERT INTO career_generated_player VALUES (?,?,?,?,?,?)",id,d.playerId(),year,date,json,hash(json));
            m.lifecycle.people.put(d.playerId(),new CareerLifecycleState.Person(rookie.age(),"GENERATED",year,date,50,date,CareerLifecycleState.Status.ACTIVE,null,null,"작은 생성 선수 준비",0,0,0,null,"NOT_YET_REVIEWED",date));
            var initial=CareerDevelopmentPolicy.initial(d);var ratings=new TreeMap<>(initial.internalRatings());ratings.replaceAll((skill,value)->16000);
            growth.players.put(d.playerId(),new CareerDevelopmentState.Player(ratings,initial.internalProficiencies(),0,Map.of(),Map.of(),0,null,null));
            m.members.put(d.playerId(),new Membership(d.playerId(),null,null,"UNAFFILIATED",null));m.preferences.put(d.playerId(),CareerMarketPolicy.preference(m.seed,d));m.freeAgents.add(d.playerId());
            CareerLifecycleStore.persist(db,id,m.lifecycle);var composed=new CareerDevelopmentEngine(baseDirectory(db,id),growth.state());CareerDevelopmentStore.persist(db,id,oldGrowth,composed);m.directory=composed.directory();CareerMarketStore.persist(db,id,year,old,m);
        });
        var found=scouting.search(id,Map.of("search",d.nickname())).players().stream().filter(p->p.player().playerId().equals(d.playerId())).findFirst().orElseThrow();
        assertThat(found.source()).isEqualTo("GENERATED");assertThat(found.ca()).isEqualTo(com.lolfm.player.PlayerAbilityPolicy.currentAbility(found.player().gameplay().ratings()));assertThat(found.player().gameplay().ratings().values()).containsOnly(16);assertThat(found.status()).isEqualTo("FREE_AGENT");
        scouting.interest(id,new CareerScoutingService.InterestRequest(d.playerId(),0,true));var life=new CareerLifecycleEngine(CareerLifecycleStore.load(db,id));life.people.put(d.playerId(),life.people.get(d.playerId()).retired());CareerLifecycleStore.persist(db,id,life);
        assertThat(scouting.search(id,Map.of("search",d.nickname())).players()).noneMatch(p->p.player().playerId().equals(d.playerId()));assertThat(scouting.search(id,Map.of("watch","true")).players()).singleElement().satisfies(p->{assertThat(p.status()).isEqualTo("RETIRED");assertThat(p.player().playerId()).isEqualTo(d.playerId());});
    }
    @Test void reservedFixtureAndApprovedDraftDirectionsUseOriginalStructuredEvidence() throws Exception {
        var c=create("스카우팅 브라우저 검증");String id=c.careerId();int year=2027;var date=calendar.currentDate(c);
        var f=competitions.load(id,year).fixtures().stream().filter(x->x.competitionId().equals("LCK_CUP")&&x.lifecycleStatus().equals("READY")&&x.executionMode().equals("PLAYER_CONTROLLED")).findFirst().orElseThrow();
        var tx=new TransactionTemplate(manager);tx.executeWithoutResult(t->{db.update("UPDATE career_competition_fixture SET scheduled_date=? WHERE career_id=? AND fixture_id=?",date,id,f.fixtureId());competitions.refreshInstanceHash(id,year,"LCK_CUP");competitions.refreshCycleHash(id,year);});
        var decision=inbox.feed(id,year,"",false,null,0).decisions().stream().filter(d->d.id().equals("COMP:"+f.fixtureId())).findFirst().orElseThrow();
        assertThat(decision.link().matchState()).isEqualTo("UNSTARTED");assertThat(decision.link().seriesId()).isNotBlank();assertThat(db.queryForObject("SELECT COUNT(*) FROM career_competition_series_checkpoint WHERE series_id=?",Integer.class,decision.link().seriesId())).isZero();
        var next=opponents.analyze(id,null,null,null,5).next();assertThat(next.fixtureId()).isEqualTo(f.fixtureId());
        var dir=directory(db,id);var roster=saved(db,id,year).state();var teams=new TreeMap<String,CompetitionRosterSnapshot.Roster>();
        for(String team:List.of(next.first(),next.second())){var players=roster.lineups().get(team).stream().map(p->dir.players().get(p).gameplay()).toList();teams.put(team,new CompetitionRosterSnapshot.Roster(new com.lolfm.player.GlobalTeamRosterCatalog.TeamKey("LCK",team.substring(4)),hash(write(players)),players));}
        var pair=new CompetitionRosterSnapshot(teams);var history=new com.lolfm.draft.SeriesDraftHistory();var games=new ArrayList<com.lolfm.league.LeagueFixtureGameReceiptV1>();var stats=new ArrayList<CareerGameStatistics>();
        for(int number=1;number<=3;number++){String blue=number==2?next.second():next.first(),red=number==2?next.first():next.second();var game=com.lolfm.league.LeagueAutomatedSeriesRunnerTest.syntheticGame("SCOUTING_FIXTURE_"+number,number,blue,red,17+number,history,blue,pair);games.add(game);stats.add(new CareerGameStatistics(number,game.outputHash(),game.durationSeconds(),blue,game.endReason().name(),game.orderedFinalAssignments().stream().map(p->new CareerGameStatistics.Player(p.playerId().value(),p.teamSide(),p.position(),p.championId().value(),p.teamSide()==com.lolfm.simulator.TeamSide.BLUE?blue:red,2,2,4,p.position()==com.lolfm.domain.Position.SUPPORT?0:200,10000,14000,16)).toList()));}
        tx.executeWithoutResult(t->{CareerRecordsStore.stage(db,"scouting-synthetic",stats);CareerRecordsStore.complete(db,id,year,"SCOUTING_SYNTHETIC",next.competition(),"GROUP","synthetic","scouting-synthetic",date,"e".repeat(64),next.first(),next.first(),next.second(),games);});
        var original=db.queryForMap("SELECT record_json,record_hash FROM career_record_series WHERE career_id=?",id);
        var all=opponents.analyze(id,year,null,null,5);assertThat(all.series()).isOne();assertThat(all.sets()).isEqualTo(3);assertThat(all.draftSets()).isEqualTo(3);assertThat(all.blueSets()).isOne();assertThat(all.currentStarters()).hasSize(5);assertThat(all.frozen()).isEmpty();
        assertThat(all.games().get(1).draft().unavailableBefore()).hasSize(10);assertThat(all.bansBy().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(15);assertThat(all.bansAgainst().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(15);
        String redBan=games.getFirst().redBans().getFirst().value();assertThat(all.bansBy()).containsKey(redBan);
        db.update("DELETE FROM career_record_draft WHERE record_id=? AND game_number=3",all.games().getFirst().recordId());var partial=opponents.analyze(id,year,null,null,5);assertThat(partial.draftSets()).isEqualTo(2);assertThat(partial.sets()).isEqualTo(3);assertThat(db.queryForMap("SELECT record_json,record_hash FROM career_record_series WHERE career_id=?",id)).isEqualTo(original);
        // Prepared receipts, never reported as actual simulated games. Browser performs real metadata and navigation requests.
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/reports/career-scouting"));db.execute("SCRIPT TO 'build/reports/career-scouting/browser.sql'");java.nio.file.Files.writeString(java.nio.file.Path.of("build/reports/career-scouting/career.txt"),id);
    }
}
