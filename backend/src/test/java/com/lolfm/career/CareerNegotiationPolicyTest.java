package com.lolfm.career;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.domain.Position;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static com.lolfm.career.CareerMarketState.*;

class CareerNegotiationPolicyTest {
    @BeforeAll static void catalog(){CareerMarketEngineTest.catalog();}
    static String rookie(CareerMarketEngine m,String region,int sequence){
        var d=CareerRookieFactory.generate(m.career,41,2027,CareerMarketEngineTest.DATE,Map.of(Position.TOP,1),false,sequence,new ChampionCatalog(new ObjectMapper()),List.of(region)).getFirst().definition();
        var players=new TreeMap<>(m.directory.players());players.put(d.playerId(),d);m.directory=new CareerRosterStore.Directory(players,m.directory.organizations());
        m.members.put(d.playerId(),new CareerRosterStore.Membership(d.playerId(),null,null,"UNAFFILIATED",null));m.freeAgents.add(d.playerId());m.preferences.put(d.playerId(),CareerMarketPolicy.preference(m.seed,d));return d.playerId();
    }
    @Test void originalAndGeneratedUseCurrentAbilityAndActualMarketWithoutRoleOrPotentialPricing(){
        var m=CareerFinancePolicyTest.fresh();var date=CareerMarketEngineTest.DATE;var contracts=Map.copyOf(m.contracts);
        var expected=Map.of("LCK",730_000_000L,"LPL",480_000_000L,"LEC",288_000_000L,"LCS",224_000_000L,"LCP",84_000_000L,"CBLOL",62_400_000L);
        int sequence=1;
        for(String region:new TreeSet<>(expected.keySet())){
            String id=rookie(m,region,sequence++);CareerSquadPlanningPolicyTest.rating(m,id,15,200);
            assertThat(m.demand(id)).isEqualTo(expected.get(region));assertThat(m.quote(id,date).region()).isEqualTo(region);
            var original=m.contracts.values().stream().filter(c->c.team().startsWith(region+":")&&m.player(c.playerId()).position()==Position.TOP).findFirst().orElseThrow();
            CareerSquadPlanningPolicyTest.rating(m,original.playerId(),15,180);assertThat(m.demand(original.playerId())).isEqualTo(m.demand(id));
            var member=m.members.get(original.playerId());m.members.put(original.playerId(),new CareerRosterStore.Membership(member.playerId(),member.ownerTeam(),member.organizationId(),"DEVELOPMENT",null));assertThat(m.demand(original.playerId())).isEqualTo(expected.get(region));m.members.put(original.playerId(),member);
            System.out.println("V2_PRICE region="+region+" generated="+id+" original="+original.playerId()+" oldReference="+m.finance.basis.prices().get(original.playerId()).referenceSalary()+" currentContract="+original.terms().annualSalary()+" asking="+m.demand(id));
        }
        assertThat(m.contracts).isEqualTo(contracts);
    }
    @Test void growthChangesOnlyNewQuotesWhileOffersRevisionsAndLegacyNegotiationsKeepTheirBoundary(){
        var m=CareerFinancePolicyTest.fresh();var date=CareerMarketEngineTest.DATE;String id=rookie(m,"LCP",1);
        CareerSquadPlanningPolicyTest.rating(m,id,13,200);long early=m.demand(id);assertThat(early).isEqualTo(33_600_000);
        var start=m.availableStart(id,date);var terms=new Terms(start,start.plusYears(2).minusDays(1),early*2,0,Role.RESERVE);
        var offer=m.submit(m.managed,id,terms,null,date);var before=CareerRosterStore.write(offer);var savedContracts=Map.copyOf(m.contracts);
        CareerSquadPlanningPolicyTest.rating(m,id,15,200);assertThat(m.demand(id)).isEqualTo(84_000_000);assertThat(m.demand(offer)).isEqualTo(early);assertThat(CareerRosterStore.write(m.offers.get(offer.offerId()))).isEqualTo(before);
        var revision=m.submit(m.managed,id,terms,offer.offerId(),date.plusDays(1));assertThat(revision.pricing()).isEqualTo(offer.pricing());
        var restored=new CareerMarketEngine(m.career,m.managed,m.directory,m.roster(),CareerRosterStore.read(CareerRosterStore.write(m.state()),CareerMarketState.class));assertThat(restored.demand(restored.offers.get(revision.offerId()))).isEqualTo(early);assertThat(restored.contracts).isEqualTo(savedContracts);
        var legacy=new Offer("legacy",id,m.managed,terms,date,date.plusDays(2),date.plusDays(5),date.plusDays(6),0,OfferStatus.SUBMITTED,null,1,null,"기존 접수 제안");
        String raw=CareerRosterStore.write(legacy);assertThat(raw).doesNotContain("pricing");assertThat(CareerRosterStore.write(CareerRosterStore.read(raw,Offer.class))).isEqualTo(raw);assertThat(m.demand(legacy)).isEqualTo(m.finance.legacyDemand(id));
        assertThat(m.state()).isEqualTo(restored.state());
    }
    @Test void quoteAndTradeEstimateUseTheProcessingDateBeforeTheDailyCheckpointMoves(){
        var m=CareerFinancePolicyTest.fresh();var oldDay=CareerMarketEngineTest.DATE;var date=oldDay.plusDays(1);String id="player-jiwoo";var c=m.active(id,oldDay);
        var loan=new CareerManagementState.Loan("same-day-loan","trade",c.contractId(),id,c.team(),"LEC:KC",date,date.plusDays(28),0,50,Role.RESERVE,c.organizationId(),"FIRST_TEAM","ACTIVE",CareerManagementPolicy.VERSION);
        m.tradeEngine.loans.put(loan.loanId(),loan);
        assertThat(m.processedThrough()).isEqualTo(oldDay);assertThat(m.quote(id,oldDay).region()).isEqualTo("LCK");
        long current=m.quote(id,date).annualDemand();assertThat(m.demand(id,date)).isEqualTo(current).isNotEqualTo(m.demand(id));
        assertThat(m.tradeEngine.estimate(id,date)).isEqualTo(CareerManagementPolicy.value(current,date,c.terms().endDate()));
        assertThat(m.contracts.get(c.contractId())).isEqualTo(c);assertThat(m.processedThrough()).isEqualTo(oldDay);
    }
    @Test void loanMarketAndNewTransferUseOneQuoteWhileKeepingParentSalaryAndAgreedFees(){
        var m=CareerFinancePolicyTest.fresh();var date=CareerMarketEngineTest.DATE;String id="player-jiwoo";var c=m.active(id,date);long originalSalary=c.terms().annualSalary();
        var loan=new CareerManagementState.Loan("pricing-loan","trade",c.contractId(),id,c.team(),"LEC:KC",date,date.plusDays(28),0,50,Role.RESERVE,c.organizationId(),"FIRST_TEAM","ACTIVE",CareerManagementPolicy.VERSION);
        m.tradeEngine.loans.put(loan.loanId(),loan);assertThat(m.quote(id,date).region()).isEqualTo("LEC");assertThat(m.quote(id,date).marketBasis()).isEqualTo("ACTIVE_LOAN_OPERATING_MARKET");assertThat(m.active(id,date).terms().annualSalary()).isEqualTo(originalSalary);m.tradeEngine.loans.clear();
        var t=m.tradeEngine.plannedTerms(m.managed,id,Role.RESERVE,date);assertThat(t).isNotNull();
        var trade=m.tradeEngine.submit(m.managed,t,null,date);assertThat(trade.pricing().annualDemand()).isEqualTo(m.demand(id));assertThat(trade.referenceValue()).isEqualTo(m.tradeEngine.estimate(id,date));
        long fee=trade.terms().fee();var quote=trade.pricing();CareerSquadPlanningPolicyTest.rating(m,id,20,200);
        var copy=m.tradeEngine.respond(c.team(),trade.tradeId(),"ACCEPT",null,date);assertThat(copy.pricing()).isEqualTo(quote);assertThat(copy.terms().fee()).isEqualTo(fee);assertThat(m.contracts.get(c.contractId()).terms().annualSalary()).isEqualTo(originalSalary);
        var roundtrip=CareerRosterStore.read(CareerRosterStore.write(copy),CareerManagementState.Trade.class);assertThat(roundtrip).isEqualTo(copy);
    }
}
