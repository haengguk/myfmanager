package com.lolfm.career;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
import static com.lolfm.career.CareerFinancePolicy.*;
import static com.lolfm.career.CareerMarketState.*;

class CareerFinancePolicyTest {
    @BeforeAll static void catalog(){CareerMarketEngineTest.catalog();}
    static CareerMarketEngine fresh(){var m=CareerMarketEngineTest.engine("LCK:T1");m.finance=new CareerFinanceEngine(m,CareerFinanceReference.initialize(m,2027,false,Set.of()));m.finance.initializeTargets(2027,CareerMarketEngineTest.DATE,false);return m;}
    @Test void sourceScopeInitialGroupsAndExplicitEconomics(){
        var m=fresh();var root=CareerFinanceReference.json();assertThat(root.path("teams").size()).isEqualTo(56);assertThat(root.path("developmentOrganizations").size()).isEqualTo(24);assertThat(root.path("nonplayableOrganizations").size()).isEqualTo(2);
        assertThat(m.accounts.keySet()).isEqualTo(m.finance.basis.teams().keySet());root.path("teams").forEach(t->t.path("actuals").properties().stream().filter(e->!e.getKey().equals("status")).forEach(e->assertThat(e.getValue().isNull()).isTrue()));
        root.path("developmentOrganizations").forEach(o->{String parent=o.path("parentTeamId").asText(),organization=o.path("organizationId").asText();assertThat(m.accounts).containsKey(parent).doesNotContainKey(organization);assertThat(m.directory.organizations().get(organization).competitiveTeam()).isEqualTo(parent);});
        assertThat(m.finance.basis.prizeRules()).hasSize(24);assertThat(root.path("prizes").size()).isEqualTo(36);
        for(var team:m.finance.basis.teams().values()){
            long salary=m.contracts.values().stream().filter(c->team.team().equals(c.team())).mapToLong(c->c.terms().annualSalary()).sum();
            assertThat(salary).isEqualTo(team.allocatedSalary());assertThat(salary+team.unallocatedCompensation()).isEqualTo(team.playerCompensation());
            assertThat(m.accounts.get(team.team()).cash()).isEqualTo(team.openingCash());assertThat(m.finance.approval(team.team(),CareerMarketEngineTest.DATE).annualIncome()).isEqualTo(team.playerCompensation()+team.nonWage());
        }
        assertThat(m.accounts.get("LCK:T1").cash()).isEqualTo(7_520_526_000L);assertThat(m.accounts.get("LCK:T1").annualBudget()).isEqualTo(15_246_000_000L);
        assertThat(m.accounts.get("LCK:BRO").cash()).isEqualTo(786_920_400L);assertThat(m.accounts.get("LCK:BRO").annualBudget()).isEqualTo(1_237_500_000L);
        for(String team:List.of("LCK:T1","LCK:BRO","CBLOL:RED"))System.out.println("FINANCE_INITIAL "+team+" "+CareerRosterStore.write(m.finance.basis.teams().get(team))+" actualSalary="+m.salaryAt(team,CareerMarketEngineTest.DATE,false)+" paymentHeadroom="+m.paymentHeadroom(team,CareerMarketEngineTest.DATE));
        assertThat(m.accounts).doesNotContainKeys("LPL:OMG","LPL:UP");
    }
    @ParameterizedTest @CsvSource({"1,0.055,0","10,0.055,1","13500000000,1,13500000000","600000,1400,840000000","123456789012,0.055,6790123396"})
    void exactDecimalFxRoundsOnlyAtWon(long original,String rate,long won){assertThat(convert(original,rate)).isEqualTo(won);}
    @Test void allocationIsStableAndWagesUseCumulativeDays(){
        assertThat(allocate(10,Map.of("c",1,"b",1,"a",1))).containsExactlyEntriesOf(new TreeMap<>(Map.of("a",4L,"b",3L,"c",3L)));
        long annual=13_860_000_007L,total=0;for(int month=1;month<=12;month++){var d=LocalDate.of(2028,month,1);total+=CareerMarketPolicy.wages(annual,d,d.withDayOfMonth(d.lengthOfMonth()));}assertThat(total).isEqualTo(annual);
        assertThatThrownBy(()->convert(Long.MAX_VALUE,"1400")).isInstanceOf(ArithmeticException.class);
    }
    @Test void recurringIncomeNonWageAndSalaryAreSeparateAndRetrySafe(){
        var m=fresh();var start=CareerMarketEngineTest.DATE;var end=start.withDayOfMonth(31);String team=m.managed;long cash=m.accounts.get(team).cash();var a=m.finance.approval(team,start);
        m.finance.date(end);long income=CareerMarketPolicy.wages(a.annualSupport(),start,end)+CareerMarketPolicy.wages(a.annualSponsor(),start,end),cost=CareerMarketPolicy.wages(a.annualNonWage(),start,end);
        assertThat(m.accounts.get(team).cash()).isEqualTo(cash+income-cost);long salary=0;
        for(var c:new ArrayList<>(m.contracts.values()))if(team.equals(c.team())){salary+=CareerMarketPolicy.wages(c.terms().annualSalary(),start,end);m.pay(c,end);}
        assertThat(m.accounts.get(team).cash()).isEqualTo(cash+income-cost-salary);var state=m.state();m.finance.date(end);assertThat(m.state()).isEqualTo(state);
        assertThat(m.ledger.stream().filter(l->l.team().equals(team)&&l.kind().equals("NON_WAGE_OPERATING"))).hasSize(1);
    }
    @Test void finalPlacementTotalsSharedClDelayedPaymentAndConflicts(){
        var m=fresh();var f=m.finance;var date=CareerMarketEngineTest.DATE;
        assertThat(placement(f.basis.prizeRules().get("lck_cup_2026"),1,10,false)).isZero();
        assertThat(placement(f.basis.prizeRules().get("lck_cl_season_2026"),3,4,true)).isEqualTo(11_250_000);
        assertThat(placement(f.basis.prizeRules().get("first_stand_2026"),1,1,false)).isEqualTo(250_000);
        assertThat(placement(f.basis.prizeRules().get("ewc_lol_2026"),5,8,false)).isEqualTo(90_000);
        assertThatThrownBy(()->placement(f.basis.prizeRules().get("msi_2026"),9,11,false)).hasMessage("PRIZE_DISTINCT_RANK_REQUIRED");
        long cash=m.accounts.get(m.managed).cash(),headroom=m.paymentHeadroom(m.managed,date);var ranks=Map.of(m.managed,new CareerFinanceEngine.Rank(1,1));
        f.recognize(2027,"EWC_LOL",ranks,date,"sealed-game-result");assertThat(m.paymentHeadroom(m.managed,date)).isEqualTo(headroom);assertThat(m.accounts.get(m.managed).cash()).isEqualTo(cash);
        var award=f.awards.values().iterator().next();assertThat(award.krw()).isEqualTo(840_000_000);assertThat(award.dueOn()).isEqualTo(date.plusDays(42));
        f.date(date.plusDays(41));assertThat(m.accounts.get(m.managed).cash()).isEqualTo(cash);f.date(date.plusDays(42));assertThat(m.accounts.get(m.managed).cash()).isEqualTo(cash+840_000_000);
        var state=m.state();f.recognize(2027,"EWC_LOL",ranks,date.plusDays(2),"sealed-game-result");f.date(date.plusDays(42));assertThat(m.state()).isEqualTo(state);
        assertThatThrownBy(()->f.recognize(2027,"EWC_LOL",ranks,date,"edited-result")).hasMessage("PRIZE_RESULT_CONFLICT");
        f.recognize(2027,"MSI",Map.of(m.managed,new CareerFinanceEngine.Rank(9,11)),date,"incomplete");assertThat(f.held).containsKey("2027|MSI");
    }
    @Test void legacyProjectionPreservesAgreedSalaryAndNextCapDoesNotCreditCash(){
        var old=CareerMarketEngineTest.engine("LCK:T1");var start=old.availableStart("player-bo",CareerMarketEngineTest.DATE);old.submit(old.managed,"player-bo",new Terms(start,start.plusYears(1).minusDays(1),200_000,10_000,Role.RESERVE),null,CareerMarketEngineTest.DATE);
        var pending=old.offers.values().iterator().next();old.offers.put(pending.offerId(),new Offer(pending.offerId(),pending.playerId(),pending.team(),pending.terms(),pending.submittedDate(),pending.responseDate(),pending.decisionDate(),pending.expiresDate(),pending.revision(),OfferStatus.COUNTER,pending.previousOfferId(),pending.round(),220_000L,"저장된 원본 역제안"));
        var converted=CareerFinanceStore.convertLegacy(CareerRosterStore.write(old.state()));assertThat(converted.accounts().get(old.managed).cash()).isEqualTo(old.accounts.get(old.managed).cash()*1000);assertThat(converted.offers().values().iterator().next().terms().signingBonus()).isEqualTo(10_000_000);assertThat(converted.offers().values().iterator().next().requestedSalary()).isEqualTo(220_000_000L);
        var m=new CareerMarketEngine(old.career,old.managed,old.directory,old.roster(),converted);m.finance=new CareerFinanceEngine(m,CareerFinanceReference.initialize(m,2027,true,Set.of("2027|FIRST_STAND")));m.finance.initializeTargets(2027,CareerMarketEngineTest.DATE,true);
        assertThat(m.contracts).isEqualTo(converted.contracts());assertThat(m.accounts).isEqualTo(converted.accounts());long cash=m.accounts.get(m.managed).cash();
        m.finance.recognize(2027,"FIRST_STAND",Map.of(m.managed,new CareerFinanceEngine.Rank(1,1)),CareerMarketEngineTest.DATE,"past");assertThat(m.finance.awards).isEmpty();
        m.finance.close(2027,LocalDate.of(2027,12,31),Map.of(m.managed,1),Map.of(m.managed,1));assertThat(m.finance.targets.get(m.managed+"|2027").bonus()).isZero();assertThat(m.accounts.get(m.managed).cash()).isEqualTo(cash);assertThat(m.contracts).isEqualTo(converted.contracts());
        var state=m.state();m.finance.close(2027,LocalDate.of(2027,12,31),Map.of(m.managed,1),Map.of());assertThat(m.state()).isEqualTo(state);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"LCK:T1","CBLOL:RED"})
    void highAndLowBudgetClubsUseActualWonConstraintsWithoutSalaryFeedback(String team){
        var m=fresh();var date=CareerMarketEngineTest.DATE;
        var c=m.contracts.values().stream().filter(v->team.equals(v.team())&&(team.equals("LCK:T1")?m.player(v.playerId()).position()==com.lolfm.domain.Position.JUNGLE&&v.terms().role()==Role.STARTER:v.terms().role()==Role.RESERVE)).findFirst().orElseThrow();
        String player=c.playerId();long reference=m.demand(player);var terms=c.terms();
        m.contracts.put(c.contractId(),new Contract(c.contractId(),c.careerId(),player,c.team(),c.organizationId(),c.signedDate(),new Terms(terms.startDate(),terms.endDate(),terms.annualSalary()*2,0,terms.role()),c.status(),c.revision(),c.policyVersion(),c.origin(),c.terminationPolicy(),c.endedDate(),c.paidThrough()));
        assertThat(m.demand(player)).isEqualTo(reference);m.contracts.put(c.contractId(),c);
        m.release(team,player,null,date);var start=m.availableStart(player,date);long salary=team.equals("LCK:T1")?2_700_000_000L:reference;
        var offer=m.submit(team,player,new Terms(start,start.plusYears(1).minusDays(1),salary,1_000_000,terms.role()),null,date);assertThat(offer.terms().annualSalary()).isEqualTo(salary);
        var saved=m.state();assertThatThrownBy(()->m.submit(team,player,new Terms(start,start.plusYears(1).minusDays(1),MAX_MONEY,0,terms.role()),offer.offerId(),date)).isInstanceOf(CareerException.class);assertThat(m.state()).isEqualTo(saved);
    }
    @Test void fixedPerformanceBonusAndNextApprovalDoNotCancelInheritedContracts(){
        var m=fresh();var date=CareerMarketEngineTest.DATE;var f=m.finance;var target=f.targets.get(m.managed+"|2027");long cash=m.accounts.get(m.managed).cash();var contracts=Map.copyOf(m.contracts);
        f.close(2027,date,Map.of(m.managed,1),Map.of(m.managed,1));var closed=f.targets.get(m.managed+"|2027");assertThat(closed.sportingStatus()).isEqualTo("EXCEEDED");assertThat(closed.bonus()).isEqualTo(pct(target.fixedSponsor(),10));
        assertThat(m.accounts.get(m.managed).cash()).isEqualTo(cash+closed.bonus());assertThat(m.contracts).isEqualTo(contracts);
        var approved=f.approvals.get(m.managed+"|2028");assertThat(approved.annualIncome()).isEqualTo(pct(f.approval(m.managed,date).annualIncome(),105));assertThat(approved.effectiveOn()).isEqualTo(LocalDate.of(2028,1,1));
        var saved=m.state();f.close(2027,date,Map.of(m.managed,1),Map.of(m.managed,1));assertThat(m.state()).isEqualTo(saved);
        f.recognize(2027,"WORLDS",Map.of(m.managed,new CareerFinanceEngine.Rank(1,1)),LocalDate.of(2027,12,29),"late-worlds");var award=f.awards.values().iterator().next();assertThat(award.dueOn()).isEqualTo(LocalDate.of(2028,1,5));
        long before=m.accounts.get(m.managed).cash();f.date(LocalDate.of(2028,1,1));assertThat(m.accounts.get(m.managed).cash()).isEqualTo(before);f.date(award.dueOn());assertThat(m.accounts.get(m.managed).cash()).isEqualTo(before+1_400_000_000L);
        System.out.println("FINANCE_TARGET "+CareerRosterStore.write(closed)+" NEXT "+CareerRosterStore.write(approved));
    }

}
