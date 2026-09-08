package com.lolfm.career;

import com.lolfm.champion.ChampionCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.util.*;

/** Explicit integration-fixture preparation; gameplay completion still uses the real Calendar/Auto path. */
public final class CareerLifecycleTestSupport {
    private CareerLifecycleTestSupport() {}
    public static String recruitGeneratedStarter(JdbcTemplate jdbc,String career,String team) {
        var manager=new DataSourceTransactionManager(jdbc.getDataSource());var store=new CareerLifecycleStore(jdbc,manager,new ChampionCatalog(new com.fasterxml.jackson.databind.ObjectMapper()));
        var date=CareerMarketStore.executionDate(jdbc,career);int year=CareerRosterStore.activeYear(jdbc,career);var review=store.review(career,year,date);
        String id=review.rookieIds().getFirst();var old=CareerMarketStore.load(jdbc,career);var market=CareerMarketStore.engine(jdbc,career,year,old);var start=market.availableStart(id,date);
        var offer=market.submit(team,id,new CareerMarketState.Terms(start,start.plusYears(2).minusDays(1),market.demand(id)*3,0,CareerMarketState.Role.RESERVE),null,date);
        // Fixture setup establishes employment through the normal acceptance path.
        // This test covers Calendar/Auto appearances, not winning a 56-club auction.
        try {
            var accept=CareerMarketEngine.class.getDeclaredMethod("accept",CareerMarketState.Offer.class,java.time.LocalDate.class);
            accept.setAccessible(true);accept.invoke(market,offer,date);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not prepare rookie employment",failure);
        }
        CareerMarketStore.persist(jdbc,career,year,old,market);
        CareerMarketStore.processThrough(jdbc,career,start);old=CareerMarketStore.load(jdbc,career);market=CareerMarketStore.engine(jdbc,career,year,old);
        if(!team.equals(market.members.get(id).ownerTeam()))throw new AssertionError("Fixture rookie offer was not accepted");
        return id;
    }
    public static void prepareOnlyStarter(JdbcTemplate jdbc,String career,String team,String id) {
        int year=CareerRosterStore.activeYear(jdbc,career);var start=CareerMarketStore.executionDate(jdbc,career);var old=CareerMarketStore.load(jdbc,career);var market=CareerMarketStore.engine(jdbc,career,year,old);
        // Leave one legal player in this AI team's role so its ordinary selection can use the rookie.
        var role=market.player(id).position();for(var member:new ArrayList<>(market.members.values()))if(team.equals(member.ownerTeam())&&!id.equals(member.playerId())&&market.player(member.playerId()).position()==role)market.release(team,member.playerId(),null,start);
        market.select(team,id,start);CareerMarketStore.persist(jdbc,career,year,old,market);
    }
}
