package com.lolfm.career;

import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import static org.assertj.core.api.Assertions.*;
import static com.lolfm.career.CareerOverseasRules.*;
import static com.lolfm.career.CareerOverseasTournament.*;

/** Only the result-store read contract: prepared bracket outcomes are not simulated match observations. */
class CareerFinanceResultsStorageTest {
    @Test void allRegionalResultsAndWorldsAreReadWithoutAnyPrizeEntitlementTable(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:finance-result-v2;DB_CLOSE_DELAY=-1","sa","");var db=new JdbcTemplate(ds);
        try {
            db.execute("CREATE TABLE career_lck_final_ranking_snapshot(career_id VARCHAR,calendar_season_year INT)");
            db.execute("CREATE TABLE career_overseas_state(career_id VARCHAR,season_year INT,competition_id VARCHAR,state_json CLOB,state_hash VARCHAR)");
            db.execute("CREATE TABLE career_international_state(career_id VARCHAR,calendar_season_year INT,competition_id VARCHAR,state_json CLOB,state_hash VARCHAR)");
            String career=CareerInternationalTournamentTest.CAREER;
            var store=new CareerCompetitionRelationalStore(db,new DataSourceTransactionManager(ds),Clock.systemUTC(),new CareerCompetitionRules(new com.fasterxml.jackson.databind.ObjectMapper()));
            var split2=prepared(CareerOverseasTournamentTest.input(Event.LPL_SPLIT_2));save(db,career,split2);
            var teams=lplEntrants(Event.LPL_SPLIT_3,split2.plan().ranking(),split2.plan().seasonEliminated());
            var third=prepared(new Input(Event.LPL_SPLIT_3,2027,51,teams,Map.of("ASCEND",teams.subList(0,8),"NIRVANA",teams.subList(8,12)),Map.of(),split2.plan().points(),4));save(db,career,third);
            for(Event event:List.of(Event.LEC_SUMMER,Event.LCS_SUMMER,Event.LCP_SPLIT_3,Event.CBLOL_ETAPA_2))save(db,career,prepared(CareerOverseasTournamentTest.input(event)));
            CareerInternationalTournamentTest.catalog();var fst=CareerInternationalTournamentTest.finish(CareerInternationalTournamentTest.registration("FIRST_STAND",CareerInternationalRules.REFERENCE_REGIONS,null),"LCK").state();
            var msi=CareerInternationalTournamentTest.finish(CareerInternationalTournamentTest.registration("MSI",fst.plan().regionalPerformance(),null),"LCK").state();
            var registration=CareerInternationalTournamentTest.registration("WORLDS",msi.plan().regionalPerformance(),msi);
            var complete=CareerInternationalTournamentTest.finish(registration,"LCK").state();String json=CareerRosterStore.write(complete);
            db.update("INSERT INTO career_international_state VALUES (?,2027,'WORLDS',?,?)",career,json,CareerRosterStore.hash(json));
            var results=CareerFinanceStore.sportingResults(store,career,2027);
            assertThat(results.domestic()).hasSize(48);assertThat(results.worldsBound()).isTrue();assertThat(results.worlds()).hasSize(complete.entries().size());
            for(String region:List.of("LPL","LEC","LCS","LCP","CBLOL"))assertThat(results.domestic().keySet().stream().filter(t->t.startsWith(region+":"))).containsExactlyInAnyOrderElementsOf(partners(region));
            assertThat(results.domestic()).doesNotContainKeys("LEC:LR","LEC:KCB");
            for(String team:split2.plan().seasonEliminated())assertThat(results.domestic().get(team)).isEqualTo(new CareerFinanceEngine.Rank(13,14));
            assertThat(results.worlds().get(complete.plan().champion())).isEqualTo(new CareerFinanceEngine.Rank(1,1));
            json=CareerRosterStore.write(registration);db.update("UPDATE career_international_state SET state_json=?,state_hash=?",json,CareerRosterStore.hash(json));
            var pending=CareerFinanceStore.sportingResults(store,career,2027);assertThat(pending.worlds()).isEmpty();assertThat(pending.worldsEntrants()).isEqualTo(results.worldsEntrants());assertThat(pending.worldsBound()).isTrue();
            db.update("UPDATE career_international_state SET state_hash='tampered'");assertThatThrownBy(()->CareerFinanceStore.sportingResults(store,career,2027)).hasMessage("INTERNATIONAL_FINANCE_INTEGRITY");
        } finally {db.execute("DROP ALL OBJECTS");db.execute("SHUTDOWN");}
    }
    static CareerOverseasStore.State prepared(Input input){return new CareerOverseasStore.State(VERSION,SOURCE_HASH,input,CareerOverseasTournamentTest.finish(input,new TreeMap<>()));}
    static void save(JdbcTemplate db,String career,CareerOverseasStore.State state){String json=CareerRosterStore.write(state);db.update("INSERT INTO career_overseas_state VALUES (?,2027,?,?,?)",career,state.input().event().name(),json,CareerRosterStore.hash(json));}
}
