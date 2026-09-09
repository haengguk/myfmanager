package com.lolfm.career;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionTemplate;

class CareerRecordsStorageTest {
    @TempDir java.nio.file.Path temporary;
    @Test void canonicalFactsAwardsAndFiveSetTotalsAreAtomicImmutableAndSurviveFileReopen() {
        String url="jdbc:h2:file:"+temporary.resolve("records")+";DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1";
        var ds=new DriverManagerDataSource(url,"sa","");var db=new JdbcTemplate(ds);var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        db.execute("CREATE TABLE career_save(career_id VARCHAR(80) PRIMARY KEY,career_root_seed BIGINT,managed_team_code VARCHAR(16))");db.update("INSERT INTO career_save VALUES ('career',17,'T1'),('other',17,'GEN')");
        db.execute("CREATE TABLE career_calendar_state(career_id VARCHAR(80),current_game_date DATE)");db.update("INSERT INTO career_calendar_state VALUES ('career',DATE '2027-10-01')");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V25__career_records_and_awards.sql"),new ClassPathResource("db/migration/V26__career_inbox_and_observation_index.sql"),new ClassPathResource("db/migration/V28__career_scouting_and_draft_evidence.sql")).execute(ds);
        var games=new ArrayList<com.lolfm.league.LeagueFixtureGameReceiptV1>();var stats=new ArrayList<CareerGameStatistics>();var history=new com.lolfm.draft.SeriesDraftHistory();
        for(int number=1;number<=5;number++) {
            var game=com.lolfm.league.LeagueAutomatedSeriesRunnerTest.syntheticGame("SYNTHETIC_RECORDS_"+number,number,"GEN","T1",100+number,history,number<=3?"GEN":"T1",null);games.add(game);
            stats.add(new CareerGameStatistics(number,game.outputHash(),game.durationSeconds(),game.winnerTeamCode(),game.endReason().name(),game.orderedFinalAssignments().stream().map(p->new CareerGameStatistics.Player(p.playerId().value(),p.teamSide(),p.position(),p.championId().value(),p.teamSide()==com.lolfm.simulator.TeamSide.BLUE?"GEN":"T1",2,2,4,p.position()==com.lolfm.domain.Position.SUPPORT?0:200,10000,14000,16)).toList()));
        }
        Runnable complete=()->{CareerRecordsStore.stage(db,"series",stats);CareerRecordsStore.complete(db,"career",2027,"LEAGUE|season|fixture","LCK_REGULAR_R1_R2","R1_R2","fixture","series",LocalDate.of(2027,5,1),"c".repeat(64),"GEN","GEN","T1",games);};
        assertThatThrownBy(()->tx.executeWithoutResult(t->{complete.run();throw new IllegalStateException("ROLLBACK_RECORD_AND_AWARDS");})).hasMessage("ROLLBACK_RECORD_AND_AWARDS");
        for(String table:List.of("career_game_statistics","career_record_series","career_record_player","career_record_award","career_record_draft"))assertThat(db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class)).isZero();
        tx.executeWithoutResult(t->complete.run());var original=db.queryForList("SELECT record_id,record_hash FROM career_record_series");var awards=db.queryForList("SELECT instance_id,award_hash FROM career_record_award ORDER BY instance_id");
        tx.executeWithoutResult(t->complete.run());assertThat(db.queryForList("SELECT record_id,record_hash FROM career_record_series")).isEqualTo(original);assertThat(db.queryForList("SELECT instance_id,award_hash FROM career_record_award ORDER BY instance_id")).isEqualTo(awards);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM career_record_award",Integer.class)).isEqualTo(16);assertThat(db.queryForObject("SELECT COUNT(*) FROM career_record_draft",Integer.class)).isEqualTo(5);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM career_record_player WHERE team_id='LCK:GEN' AND won",Integer.class)).isEqualTo(15);
        String player=stats.getFirst().players().getFirst().playerId();assertThat(db.queryForMap("SELECT COUNT(DISTINCT record_id) AS series,COUNT(*) AS sets,SUM(kills) AS kills,SUM(deaths) AS deaths FROM career_record_player WHERE player_id=?",player)).containsEntry("SERIES",1L).containsEntry("SETS",5L).containsEntry("KILLS",10L).containsEntry("DEATHS",10L);
        assertThatThrownBy(()->tx.executeWithoutResult(t->CareerRecordsStore.complete(db,"career",2027,"LEAGUE|season|fixture","LCK_REGULAR_R1_R2","R1_R2","fixture","series",LocalDate.of(2027,5,1),"d".repeat(64),"GEN","GEN","T1",games))).hasMessage("CAREER_RECORD_CONFLICT");
        var old=stats.getFirst();var conflicting=new CareerGameStatistics(1,old.outputHash(),old.seconds()+1,old.winner(),old.endReason(),old.players());assertThatThrownBy(()->tx.executeWithoutResult(t->CareerRecordsStore.stage(db,"series",List.of(conflicting)))).hasMessage("CAREER_STATISTICS_CONFLICT");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM career_record_series WHERE career_id='other'",Integer.class)).isZero();
        db.execute("CREATE TABLE career_market_state(career_id VARCHAR(80),revision BIGINT,state_json CLOB,state_hash CHAR(64))");
        var record=CareerRecordsStore.period(db,"career",2027,"LCK_REGULAR",1000).getFirst();
        var incompatible=CareerRosterStore.read(CareerRosterStore.write(record).replace(CareerPerformancePolicy.VERSION,"DIFFERENT_PERFORMANCE_MODEL"),CareerRecordsStore.Series.class);
        assertThat(CareerAwardsStore.complete(List.of(record,incompatible))).isFalse();
        var reversed=new ArrayList<>(record.games());Collections.reverse(reversed);
        var sample=new CareerRecordsStore.Series(record.recordId(),record.careerId(),record.seasonYear(),record.origin(),record.competition(),record.stage(),record.fixtureId(),record.seriesId(),record.date(),record.receiptHash(),record.winner(),record.firstTeam(),record.secondTeam(),record.coverage(),reversed);
        var regular=CareerAwardPolicy.DEFINITIONS.stream().filter(d->d.id().equals("LCK_REGULAR_MVP")).findFirst().orElseThrow();
        var period=CareerAwardClosure.periodCandidates(db,"career",2027,regular,List.of(sample),List.of(),"REGULAR");
        assertThat(period).filteredOn(c->c.team().equals("LCK:GEN")).allSatisfy(c->assertThat(c.period().teamPerformance()).isEqualByComparingTo("100"));
        assertThat(period).filteredOn(c->c.team().equals("LCK:T1")).allSatisfy(c->assertThat(c.period().teamPerformance()).isEqualByComparingTo("0"));
        var winner=period.getFirst();var eligible=new CareerAwardsStore.Candidate(winner.playerId(),winner.name(),winner.team(),winner.position(),42,CareerPerformancePolicy.decimal(20),"TRUE","합성 자격 경계",winner.score(),winner.combat(),winner.period(),winner.tieKey());
        for(String definition:List.of("CL_ALL_PRO","EWC_LOL_MVP","FIRST_STAND_MVP")) {
            var policy=CareerAwardPolicy.DEFINITIONS.stream().filter(d->d.id().equals(definition)).findFirst().orElseThrow();
            for(int retry=0;retry<2;retry++)tx.executeWithoutResult(t->CareerAwardsStore.save(db,"career",2027,definition,policy.name(),policy.scope(),"CHAMPIONSHIP",1,record.date(),List.of(record),List.of(policy.period()),false,List.of(eligible),true,policy));
            var award=CareerRosterStore.read(db.queryForObject("SELECT award_json FROM career_record_award WHERE definition_id=?",String.class,definition),CareerAwardsStore.Award.class);
            assertThat(award.slots()).filteredOn(slot->slot.playerId()!=null).hasSize(1);
            assertThat(award.stateHistory()).containsExactly("COLLECTING","INPUT_SEALED","FINALIZED");
            if(definition.equals("CL_ALL_PRO")){assertThat(award.slots()).hasSize(5);assertThat(award.entitlements()).singleElement().satisfies(e->{assertThat(e.amount()).isEqualTo(1000000);assertThat(e.krw()).isEqualTo(1000000);assertThat(e.status()).isEqualTo("UNPAID");});}
            if(definition.equals("EWC_LOL_MVP"))assertThat(award.entitlements()).singleElement().satisfies(e->{assertThat(e.amount()).isEqualTo(25000);assertThat(e.minimumDelayDays()).isEqualTo(35);assertThat(e.exactPaymentDate()).isNull();assertThat(e.krw()).isNull();});
        }
        assertThat(CareerRecordsQuery.teams("LCK:GEN:CL",true)).containsExactly("LCK:GEN","LCK:GEN:CL");
        assertThat(CareerRecordsQuery.teams("LEC:KCB",false)).containsExactly("LEC:KCB");
        assertThat(CareerRecordsQuery.teams("LEC:KCB",true)).containsExactly("LEC:KC","LEC:KCB");
        db.execute("CREATE TABLE career_season(career_id VARCHAR(80),season_year INTEGER,season_id VARCHAR(80),lifecycle_status VARCHAR(32))");
        db.update("INSERT INTO career_season VALUES ('career',2027,'season','ACTIVE')");
        db.execute("CREATE TABLE league_fixture(season_id VARCHAR(80),fixture_id VARCHAR(80),bound_series_id VARCHAR(80),round_number INTEGER,first_team_code VARCHAR(32),second_team_code VARCHAR(32),lifecycle_status VARCHAR(32))");
        db.update("INSERT INTO league_fixture VALUES ('season','fixture','series',1,'GEN','T1','COMPLETED')");
        db.execute("CREATE TABLE career_competition_fixture(career_id VARCHAR(80),calendar_season_year INTEGER,competition_id VARCHAR(64),stage_id VARCHAR(64),match_id VARCHAR(80),series_id VARCHAR(80),scheduled_date DATE,first_team_code VARCHAR(32),second_team_code VARCHAR(32),lifecycle_status VARCHAR(32),fixture_id VARCHAR(80))");
        db.update("INSERT INTO career_competition_fixture VALUES ('career',2027,'LCK_REGULAR_R3_R4','LEGEND_RISE','R3','series-r3',DATE '2027-09-01','GEN','T1','COMPLETED','r3'),('career',2027,'LCK_PLAYOFFS','PLAYOFFS','PO_F','playoffs',DATE '2027-10-01','GEN','T1','READY','po')");
        tx.executeWithoutResult(t->{CareerRecordsStore.stage(db,"series-r3",stats);CareerRecordsStore.complete(db,"career",2027,"COMP|2027|LCK_REGULAR_R3_R4|R3","LCK_REGULAR_R3_R4","LEGEND_RISE","R3","series-r3",LocalDate.of(2027,9,1),"f".repeat(64),"GEN","GEN","T1",games);CareerAwardClosure.close(db,"career",2027,"LCK_REGULAR_R3_R4","R3");});
        tx.executeWithoutResult(t->CareerAwardClosure.close(db,"career",2027,"LCK_REGULAR_R3_R4","R3"));
        var cutoff=CareerRosterStore.read(db.queryForObject("SELECT award_json FROM career_record_award WHERE definition_id='LCK_ALL_PRO'",String.class),CareerAwardsStore.Award.class);
        assertThat(cutoff.inputRecords()).hasSize(2);assertThat(cutoff.cutoffDate()).isEqualTo(LocalDate.of(2027,9,1));assertThat(cutoff.slots()).hasSize(15).allMatch(slot->slot.playerId()==null);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM career_record_award WHERE definition_id='LCK_ALL_PRO'",Integer.class)).isOne();
        // Actual match award writer above produced R1/R3 POG/POM. Add one small period winner,
        // then exercise the exact comparison query without constructing a whole scouting roster.
        tx.executeWithoutResult(t->CareerAwardsStore.save(db,"career",2027,regular.id(),regular.name(),"LCK_REGULAR","scope-check",2,record.date(),List.of(record),List.of("SEASON"),false,List.of(eligible),true,regular));
        var awardQueries=new CareerScoutingService(db,new DataSourceTransactionManager(ds),null,null);
        var recordsQuery=new CareerRecordsQuery(db,new DataSourceTransactionManager(ds));
        var winners=db.queryForList("SELECT DISTINCT player_id FROM career_record_award_candidate WHERE winner=TRUE",String.class);
        for(String scope:List.of("LCK_REGULAR_R1_R2","LCK_REGULAR_R3_R4","")) {
            var compared=awardQueries.awards("career",winners,2027,scope);
            assertThat(compared).extracting(a->a.get("instanceId")).doesNotHaveDuplicates();
            if(!scope.isEmpty())assertThat(compared).allMatch(a->Set.of(scope,"LCK_REGULAR").contains(a.get("scope")));
            assertThat(compared).anyMatch(a->a.get("scope").equals("LCK_REGULAR"));
            assertThat(compared).anyMatch(a->a.get("scope").equals(scope.isEmpty()?"LCK_REGULAR_R1_R2":scope));
            for(String winnerId:winners) {
                var expected=recordsQuery.view("career","PLAYER",winnerId,2027,null,0,scope).awards().stream()
                        .filter(a->!a.analysisBadge()&&a.status().equals("FINALIZED")&&a.slots().stream().anyMatch(slot->winnerId.equals(slot.playerId())))
                        .map(CareerAwardsStore.Award::instanceId).toList();
                assertThat(compared.stream().filter(a->a.get("playerId").equals(winnerId)).map(a->(String)a.get("instanceId"))).containsExactlyInAnyOrderElementsOf(expected);
            }
        }
        assertThat(awardQueries.awards("other",winners,2027,"")).isEmpty();
        assertThat(awardQueries.awards("career",winners,2028,"")).isEmpty();
        for(int index=0;index<60;index++){String occurrence="synthetic-page-"+index;tx.executeWithoutResult(t->CareerAwardsStore.save(db,"career",2027,"SYNTHETIC_POM","합성 페이지 검증","LCK_REGULAR",occurrence,2,record.date(),List.of(record),List.of(),false,List.of(eligible),true,null));}
        var queries=new CareerRecordsQuery(db,new DataSourceTransactionManager(ds));
        var page=queries.view("career","PLAYER",eligible.playerId(),2027,2L,0,null);
        assertThat(page.awards()).hasSize(50);assertThat(page.nextAwardCursor()).isEqualTo(50);assertThat(page.awardCounts()).containsEntry("SYNTHETIC_POM",60L);
        var nextPage=queries.view("career","PLAYER",eligible.playerId(),2027,2L,0,null,false,50);
        assertThat(nextPage.awards()).isNotEmpty();assertThat(nextPage.awards()).extracting(CareerAwardsStore.Award::instanceId).doesNotContainAnyElementsOf(page.awards().stream().map(CareerAwardsStore.Award::instanceId).toList());
        assertThat(queries.matchAwards("career",record.recordId())).hasSizeGreaterThan(60);
        assertThat(queries.awardDetail("career",nextPage.awards().getFirst().instanceId())).isEqualTo(nextPage.awards().getFirst());
        assertThatThrownBy(()->queries.awardDetail("other",nextPage.awards().getFirst().instanceId())).isInstanceOf(CareerException.class);
        tx.executeWithoutResult(t->{CareerRecordsStore.stage(db,"bo1",stats.subList(0,1));for(int retry=0;retry<2;retry++)CareerRecordsStore.complete(db,"career",2027,"BO1","LCK_CUP","GROUP","bo1","bo1",LocalDate.of(2027,5,1),"b".repeat(64),"GEN","GEN","T1",games.subList(0,1));});
        String bo1=db.queryForObject("SELECT record_id FROM career_record_series WHERE series_id='bo1'",String.class);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM career_inbox_item WHERE source_key=?",Integer.class,"SERIES:"+bo1)).isOne();
        assertThat(queries.matchAwards("career",bo1)).filteredOn(a->!a.analysisBadge()).singleElement().satisfies(a->assertThat(a.aliases()).contains("GAME","SERIES"));
        original=db.queryForList("SELECT record_id,record_hash FROM career_record_series");
        awards=db.queryForList("SELECT instance_id,award_hash FROM career_record_award ORDER BY instance_id");
        db.execute("SHUTDOWN");var reopened=new JdbcTemplate(new DriverManagerDataSource(url,"sa",""));assertThat(reopened.queryForList("SELECT record_id,record_hash FROM career_record_series")).isEqualTo(original);assertThat(reopened.queryForList("SELECT instance_id,award_hash FROM career_record_award ORDER BY instance_id")).isEqualTo(awards);reopened.execute("SHUTDOWN");
    }
    @Test void subjectsAreFilteredBeforePagingAndGrowthBoundariesAreIndependent() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:subjects;DB_CLOSE_DELAY=-1","sa","");var db=new JdbcTemplate(ds);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V25__career_records_and_awards.sql"),new ClassPathResource("db/migration/V26__career_inbox_and_observation_index.sql"),new ClassPathResource("db/migration/V28__career_scouting_and_draft_evidence.sql")).execute(ds);
        db.execute("CREATE TABLE career_save(career_id VARCHAR(80),managed_team_code VARCHAR(16))");db.update("INSERT INTO career_save VALUES ('career','T1')");
        db.execute("CREATE TABLE career_season(career_id VARCHAR(80),season_year INTEGER,lifecycle_status VARCHAR(32))");db.update("INSERT INTO career_season VALUES ('career',2027,'ACTIVE')");
        var query=new CareerRecordsQuery(db,new DataSourceTransactionManager(ds));var date=LocalDate.of(2027,1,1);
        // Legacy rows have no search subjects yet. Migration indexes them without changing evidence.
        String opening=CareerRosterStore.write(Map.of("players",Map.of("target",Map.of("internalRatings",Map.of("LANING",1000)))));
        db.update("INSERT INTO career_record_observation VALUES (?,?,?,?,?,?)","career",2027,"OPENING",date,opening,CareerRosterStore.hash(opening));
        CareerHistoryStore.observe(db,"career",2027,"EVENT:target",date,new CareerHistoryStore.Operating("target",date,"TRANSFERRED","target","당시 이름","LCK:T1","offer","이적"),false);
        CareerObservationIndex.backfill(db);
        var before=query.view("career","PLAYER","target",2027,null,0,null);
        assertThat(before.observations()).hasSize(2);assertThat(before.growthObservations()).hasSize(1);
        for(int i=0;i<500;i++)CareerHistoryStore.observe(db,"career",2027,"EVENT:other"+i,date.plusDays(1),new CareerHistoryStore.Operating("other"+i,date.plusDays(1),"CONTRACT_SIGNED","other","다른 선수","LCK:GEN","other","체결"),false);
        var legacyLatest=db.query("SELECT observation_json FROM career_record_observation WHERE career_id='career' AND season_year=2027 ORDER BY observed_date DESC,observation_key LIMIT 500",(r,n)->CareerRosterStore.read(r.getString(1),com.fasterxml.jackson.databind.JsonNode.class));
        assertThat(legacyLatest.stream().filter(v->"target".equals(v.path("playerId").asText())||v.path("players").has("target")).count()).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM career_record_observation WHERE observation_key IN ('OPENING','EVENT:target')",Integer.class)).isEqualTo(2);
        CareerHistoryStore.observe(db,"career",2027,"CLOSING_FINAL",date.plusMonths(11),CareerRosterStore.read(opening,com.fasterxml.jackson.databind.JsonNode.class),false);
        var after=query.view("career","PLAYER","target",2027,null,0,null);
        assertThat(after.observations()).hasSize(3);assertThat(after.growthObservations()).hasSize(2);
        assertThat(query.view("career","TEAM","LCK:T1",2027,null,0,null).observations()).hasSize(1);
        var team=query.view("career","TEAM","LCK:GEN",2027,null,0,null);assertThat(team.observations()).hasSize(50);
        CareerHistoryStore.observe(db,"career",2027,"EVENT:new",date.plusDays(2),new CareerHistoryStore.Operating("new",date,"CONTRACT_SIGNED","other","새 선수","LCK:GEN","new","체결"),false);
        var next=query.view("career","TEAM","LCK:GEN",2027,null,0,null,false,0,team.observationAsOf(),team.nextObservationCursor());
        assertThat(next.observations()).hasSize(50).doesNotContainAnyElementsOf(team.observations());
        CareerObservationIndex.backfill(db);assertThat(db.queryForObject("SELECT observation_json FROM career_record_observation WHERE observation_key='OPENING'",String.class)).isEqualTo(opening);
        db.update("UPDATE career_record_observation SET observed_date=DATE '2027-12-31' WHERE observation_key='CLOSING_FINAL'");
        for(int month=1;month<=12;month++){var end=java.time.YearMonth.of(2027,month).atEndOfMonth();CareerHistoryStore.observe(db,"career",2027,"MONTH:"+end,end,CareerRosterStore.read(opening,com.fasterxml.jackson.databind.JsonNode.class),false);}
        assertThat(query.view("career","PLAYER","target",2027,null,0,null).growthObservations()).hasSize(14).anyMatch(o->o.get("kind").equals("CLOSING_FINAL"));
        db.update("UPDATE career_record_observation SET observed_date=DATE '2026-08-24' WHERE observation_key='OPENING'");
        for(int month=8;month<=12;month++){var end=java.time.YearMonth.of(2026,month).atEndOfMonth();CareerHistoryStore.observe(db,"career",2027,"MONTH:"+end,end,CareerRosterStore.read(opening,com.fasterxml.jackson.databind.JsonNode.class),false);}
        assertThat(db.queryForList("SELECT observation_key FROM career_record_observation WHERE observation_key NOT LIKE 'EVENT:%' ORDER BY observed_date,observation_key LIMIT 14",String.class)).doesNotContain("CLOSING_FINAL");
        var longSeason=query.view("career","PLAYER","target",2027,null,0,null);assertThat(longSeason.growthObservations()).hasSize(19).anyMatch(o->o.get("kind").equals("CLOSING_FINAL"));
        assertThat(query.view("career","PLAYER","uncollected",2027,null,0,null).growthObservations()).isEmpty();
        db.update("DELETE FROM career_observation_subject WHERE sequence IN(SELECT sequence FROM career_observation_index WHERE observation_key='CLOSING_FINAL')");
        assertThat(query.view("career","PLAYER","target",2027,null,0,null).growthObservations()).hasSize(18).noneMatch(o->o.get("kind").equals("CLOSING_FINAL"));

        db.execute("SHUTDOWN");
    }

    @Test void csCoverageSeparatesPlayedTimeFromObservedTime() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:coverage;DB_CLOSE_DELAY=-1","sa","");var db=new JdbcTemplate(ds);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V25__career_records_and_awards.sql"),new ClassPathResource("db/migration/V26__career_inbox_and_observation_index.sql"),new ClassPathResource("db/migration/V28__career_scouting_and_draft_evidence.sql")).execute(ds);
        db.execute("CREATE TABLE career_save(career_id VARCHAR(80),managed_team_code VARCHAR(16))");db.update("INSERT INTO career_save VALUES ('career','T1')");
        db.execute("CREATE TABLE career_season(career_id VARCHAR(80),season_year INTEGER,lifecycle_status VARCHAR(32))");db.update("INSERT INTO career_season VALUES ('career',2027,'ACTIVE')");
        String json=CareerRosterStore.write(new CareerRecordsStore.Series("record","career",2027,"origin","LCK_CUP","GROUP","fixture","series",LocalDate.of(2027,1,1),"h","LCK:T1","LCK:T1","LCK:GEN","PARTIAL",List.of()));
        db.update("INSERT INTO career_record_series(record_id,career_id,season_year,origin_identity,competition_id,stage_id,fixture_id,series_id,played_date,receipt_hash,winner_team,first_team,second_team,game_count,coverage,record_json,record_hash) VALUES ('record','career',2027,'origin','LCK_CUP','GROUP','fixture','series',DATE '2027-01-01','h','LCK:T1','LCK:T1','LCK:GEN',2,'PARTIAL',?,?)",json,CareerRosterStore.hash(json));
        for(int game=1;game<=2;game++)for(var position:com.lolfm.domain.Position.values())db.update("INSERT INTO career_record_player VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)","record",game,position.name(),"career",2027,"LCK:T1",position.name(),"{}","champion",position.name(),game==1?2:null,game==1?1:null,game==1?3:null,game==1?300:null,null,null,1800,true,null);
        var query=new CareerRecordsQuery(db,new DataSourceTransactionManager(ds));
        var mixed=query.view("career","PLAYER","TOP",2027,null,0,null).totals().getFirst();
        assertThat(((Number)mixed.get("seconds")).longValue()).isEqualTo(3600);assertThat(((Number)mixed.get("csobservedseconds")).longValue()).isEqualTo(1800);assertThat(mixed.get("observedcspm")).isEqualTo(10.0);
        assertThat(((Number)mixed.get("cs")).doubleValue()*60/((Number)mixed.get("seconds")).doubleValue()).isEqualTo(5.0);
        var team=query.view("career","TEAM","LCK:T1",2027,null,0,null).totals().getFirst();assertThat(team.get("observedcspm")).isEqualTo(50.0);assertThat(((Number)team.get("csobservedunits")).intValue()).isOne();
        db.update("UPDATE career_record_player SET cs=NULL");assertThat(query.view("career","PLAYER","TOP",2027,null,0,null).totals().getFirst().get("observedcspm")).isNull();
        db.update("UPDATE career_record_player SET cs=300");var complete=query.view("career","PLAYER","TOP",2027,null,0,null).totals().getFirst();assertThat(complete.get("observedcspm")).isEqualTo(10.0);assertThat(((Number)complete.get("csobservedseconds")).intValue()).isEqualTo(3600);
        db.execute("SHUTDOWN");
    }

}
