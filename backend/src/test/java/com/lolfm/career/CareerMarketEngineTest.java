package com.lolfm.career;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.player.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
import static com.lolfm.career.CareerMarketState.*;
import static com.lolfm.career.CareerMarketPolicy.*;

class CareerMarketEngineTest {
    static CareerRosterStore.Directory directory;
    static CareerRosterStore.State roster;
    static final LocalDate DATE=LocalDate.of(2027,1,5);
    @BeforeAll static void catalog() {
        var mapper=new ObjectMapper();var ratings=PlayerRatingCatalog.loadDefault();var champions=new ChampionCatalog(mapper);
        var global=new GlobalTeamRosterCatalog(mapper,ratings,ChampionProficiencyCatalog.loadDefault(ratings,champions),champions);
        var catalog=new ExpandedPlayerCatalog(mapper,global,champions);directory=new CareerRosterStore.Directory(catalog.players(),catalog.organizations());
        Map<String,CareerRosterStore.Membership> members=new TreeMap<>();catalog.players().forEach((id,p)->members.put(id,new CareerRosterStore.Membership(id,p.initialOwnerTeam(),p.initialOrganizationId(),p.initialSquad(),p.eligibilityReason())));
        roster=new CareerRosterStore.State(CareerRosterStore.POLICY,members,catalog.initialLineups());
    }
    static CareerMarketEngine engine(String managed) {
        return new CareerMarketEngine("career_"+"a".repeat(64),managed,directory,roster,CareerMarketEngine.initialize("career_"+"a".repeat(64),41,DATE,2027,directory,roster));
    }
    static Terms terms(CareerMarketEngine e,String player,int salaryPercent,Role role) {
        return terms(e,player,salaryPercent,role,DATE);
    }
    static Terms terms(CareerMarketEngine e,String player,int salaryPercent,Role role,LocalDate date) {
        var start=e.availableStart(player,date);long salary=demand(directory.players().get(player))*salaryPercent/100;
        return new Terms(start,start.plusYears(2).minusDays(1),salary,salary/10,role);
    }
    @Test void playerAndTwoAiClubsCompareRealOffersIndependentlyOfSubmissionOrder() {
        var a=engine("LCK:T1");var b=engine("LCK:T1");
        for(var e:List.of(a,b))e.release("LCK:HLE","player-zeus",null,DATE);
        var teams=List.of("LCK:T1","LCK:GEN","LPL:BLG");
        for(String team:teams)a.submit(team,"player-zeus",terms(a,"player-zeus",team.equals("LCK:T1")?140:125,Role.STARTER),null,DATE);
        var reverse=new ArrayList<>(teams);Collections.reverse(reverse);
        for(String team:reverse)b.submit(team,"player-zeus",terms(b,"player-zeus",team.equals("LCK:T1")?140:125,Role.STARTER),null,DATE);
        a.advance(DATE.plusDays(5));b.advance(DATE.plusDays(5));
        assertThat(a.state().decisions()).isEqualTo(b.state().decisions());
        assertThat(a.state().contracts()).isEqualTo(b.state().contracts());
        var decision=a.state().decisions().values().stream().filter(d->d.playerId().equals("player-zeus")).findFirst().orElseThrow();
        assertThat(decision.evaluations()).hasSize(3);assertThat(decision.winningOfferId()).isNotNull();
        assertThat(a.state().offers().values().stream().filter(o->o.playerId().equals("player-zeus")&&o.status()==OfferStatus.ACCEPTED)).hasSize(1);
        assertThat(a.state().ledger().stream().filter(l->l.kind().equals("SIGNING_BONUS"))).hasSize(1);
        for(String team:teams)assertThat(a.reservedCash(team)).isZero();
        assertThat(a.roster().lineups().get("LCK:T1")).contains("player-doran");
        assertThat(a.state().decisions()).containsAllEntriesOf(b.state().decisions());
        System.out.println("MARKET_COMPETITION "+CareerRosterStore.write(decision));
    }
    @Test void counterRejectionWithdrawalAndBudgetReservationsHaveActualConsequences() {
        var e=engine("LCK:T1");e.release("LCK:HLE","player-zeus",null,DATE);
        var low=e.submit("LCK:T1","player-zeus",terms(e,"player-zeus",85,Role.STARTER),null,DATE);
        e.submit("LCK:GEN","player-zeus",terms(e,"player-zeus",125,Role.STARTER),null,DATE);
        long cash=e.state().accounts().get("LCK:T1").cash();assertThat(e.reservedCash("LCK:T1")).isPositive();
        e.advance(DATE.plusDays(2));assertThat(e.state().offers().get(low.offerId()).status()).isEqualTo(OfferStatus.COUNTER);
        e.advance(DATE.plusDays(5));assertThat(e.state().offers().get(low.offerId()).status()).isEqualTo(OfferStatus.REJECTED);
        assertThat(e.active("player-zeus",DATE.plusDays(5)).team()).isEqualTo("LCK:GEN");
        assertThat(e.state().accounts().get("LCK:T1").cash()).isEqualTo(cash);assertThat(e.reservedCash("LCK:T1")).isZero();
        assertThatThrownBy(()->e.submit("LCK:T1","player-zeus",terms(e,"player-zeus",125,Role.STARTER),null,DATE.plusDays(5))).isInstanceOf(RuntimeException.class);
        var fresh=engine("LCK:T1");var offer=fresh.submit("LCK:T1","player-beryl",terms(fresh,"player-beryl",125,Role.RESERVE),null,DATE);
        fresh.withdraw("LCK:T1",offer.offerId(),DATE);assertThat(fresh.reservedCash("LCK:T1")).isZero();
        var begin=fresh.availableStart("player-bo",DATE);
        assertThatThrownBy(()->fresh.submit("LCK:T1","player-bo",new Terms(begin,begin.plusYears(1).minusDays(1),MAX_MONEY,MAX_MONEY,Role.STARTER),null,DATE)).isInstanceOf(CareerException.class);
        assertThat(fresh.state().offers().values().stream().filter(Offer::open)).isEmpty();
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"LCK:T1","LCK:GEN"})
    void bonusCannotSpendPayrollCashAndReservationsAreReleased(String team) {
        var e=engine(team);var begin=e.availableStart("player-bo",DATE);var before=e.state();
        long cash=e.accounts.get(team).cash();
        assertThatThrownBy(()->e.submit(team,"player-bo",new Terms(begin,begin.plusYears(2).minusDays(1),252_000,cash,Role.STARTER),null,DATE))
                .isInstanceOf(CareerException.class).satisfies(x->assertThat(((CareerException)x).clientMessage()).contains("급여"));
        assertThat(e.state()).isEqualTo(before);
        var offer=e.submit(team,"player-bo",new Terms(begin,begin.plusYears(2).minusDays(1),252_000,0,Role.STARTER),null,DATE);
        long maximum=e.paymentHeadroom(team,DATE);
        assertThatThrownBy(()->e.submit(team,"player-bo",new Terms(begin,begin.plusYears(2).minusDays(1),252_000,maximum+1,Role.STARTER),offer.offerId(),DATE)).isInstanceOf(CareerException.class);
        assertThat(e.state().offers().get(offer.offerId())).isEqualTo(offer);
        var revised=e.submit(team,"player-bo",new Terms(begin,begin.plusYears(2).minusDays(1),252_000,maximum,Role.STARTER),offer.offerId(),DATE);
        assertThat(e.paymentHeadroom(team,DATE)).isZero();
        assertThatThrownBy(()->e.submit(team,"player-beryl",terms(e,"player-beryl",150,Role.RESERVE),null,DATE)).isInstanceOf(CareerException.class);
        var funded=new CareerMarketEngine("career_"+"a".repeat(64),team,directory,e.roster(),e.state());
        funded.advance(DATE.withDayOfMonth(31));
        assertThat(funded.offers.get(revised.offerId()).status()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(funded.salaryArrears(team)).isZero();assertThat(funded.accounts.get(team).cash()).isNotNegative();
        e.withdraw(team,revised.offerId(),DATE);assertThat(e.reservedCash(team)).isZero();
        assertThat(e.paymentHeadroom(team,DATE)).isEqualTo(new CareerMarketEngine("career_"+"a".repeat(64),team,directory,roster,before).paymentHeadroom(team,DATE));
        var current=e.active(roster.lineups().get(team).getFirst(),DATE);var account=e.accounts.get(team);
        long onlyRelease=releaseCost(current,DATE)+wages(current.terms().annualSalary(),current.paidThrough().plusDays(1),DATE);
        e.accounts.put(team,new Account(team,account.annualBudget(),onlyRelease,account.rosterLimit()));var beforeRelease=e.state();
        assertThatThrownBy(()->e.release(team,current.playerId(),null,DATE)).isInstanceOf(CareerException.class);
        assertThat(e.state()).isEqualTo(beforeRelease);
    }
    @Test void priorVersionCashExhaustionAccruesDebtAndNextAllocationSettlesOnce() {
        String career="career_"+"a".repeat(64),team="LCK:T1";LocalDate date=LocalDate.of(2027,12,20);
        var e=new CareerMarketEngine(career,team,directory,roster,CareerMarketEngine.initialize(career,41,date,2027,directory,roster));
        var begin=e.availableStart("player-bo",date);
        var o=e.submit(team,"player-bo",new Terms(begin,begin.plusYears(2).minusDays(1),252_000,10_000,Role.STARTER),null,date);
        e.advance(begin);assertThat(e.state().offers().get(o.offerId()).status()).isEqualTo(OfferStatus.ACCEPTED);
        // Reconstruct the already-committed V1 excessive bonus; preserve the signed contract.
        var account=e.accounts.get(team);e.accounts.put(team,new Account(team,account.annualBudget(),0,account.rosterLimit()));
        e.ledger.add(new Ledger("prior-version-excessive-bonus",begin,team,e.active("player-bo",begin).contractId(),"SIGNING_BONUS",-account.cash()));
        e.advance(LocalDate.of(2027,12,31));long arrears=e.salaryArrears(team);assertThat(arrears).isPositive();
        assertThat(e.state().accounts().get(team).cash()).isZero();
        var blocked=e;assertThatThrownBy(()->blocked.submit(team,"player-fofo",terms(blocked,"player-fofo",150,Role.RESERVE,LocalDate.of(2027,12,31)),null,LocalDate.of(2027,12,31))).isInstanceOf(CareerException.class);
        var saved=CareerRosterStore.read(CareerRosterStore.write(e.state()),CareerMarketState.class);
        e=new CareerMarketEngine(career,team,directory,e.roster(),saved);
        e.advance(LocalDate.of(2028,1,1));assertThat(e.salaryArrears(team)).isZero();
        assertThat(e.accounts.get(team).cash()).isEqualTo(account.annualBudget()-arrears);
        var result=e.state();e.advance(LocalDate.of(2028,1,1));assertThat(e.state()).isEqualTo(result);
        assertThat(e.ledger.stream().filter(l->team.equals(l.team())&&l.kind().equals("ANNUAL_ALLOCATION"))).hasSize(1);
    }
    @ParameterizedTest @CsvSource({"0,2027-01-10","1,2027-01-11","2,2027-01-12","3,2027-01-13"})
    void nearExpiryNegotiationUsesSharedDecisionAndDoesNotBackdate(int delay,String expectedDate) {
        var e=engine("LCK:HLE");var old=e.active("player-zeus",DATE);LocalDate end=DATE.plusDays(2);
        var t=new Terms(DATE.minusYears(1),end,old.terms().annualSalary(),0,Role.STARTER);
        e.contracts.put(old.contractId(),new Contract(old.contractId(),old.careerId(),old.playerId(),old.team(),old.organizationId(),old.signedDate(),t,ContractStatus.ACTIVE,0,VERSION,"EXPIRY_BOUNDARY_FIXTURE",old.terminationPolicy(),null,DATE.minusDays(1)));
        LocalDate submitted=DATE.plusDays(delay);e.advance(submitted);LocalDate decision=LocalDate.parse(expectedDate);
        assertThat(e.availableStart("player-zeus",submitted)).isEqualTo(decision);
        var offer=e.submit("LCK:HLE","player-zeus",terms(e,"player-zeus",150,Role.STARTER,submitted),null,submitted);
        if(delay==0) {
            // A persisted V1 open offer with the known invalid Jan 8 start remains intact until explicit revision.
            var bad=new Terms(end.plusDays(1),offer.terms().endDate(),offer.terms().annualSalary(),offer.terms().signingBonus(),offer.terms().role());
            e.offers.put(offer.offerId(),new Offer(offer.offerId(),offer.playerId(),offer.team(),bad,offer.submittedDate(),offer.responseDate(),offer.decisionDate(),offer.expiresDate(),offer.revision(),offer.status(),offer.previousOfferId(),offer.round(),offer.requestedSalary(),offer.reason()));
        }
        e.advance(submitted.plusDays(1));
        var revised=e.submit("LCK:HLE","player-zeus",terms(e,"player-zeus",150,Role.STARTER,submitted.plusDays(1)),offer.offerId(),submitted.plusDays(1));
        var rival=e.submit("LCK:GEN","player-zeus",terms(e,"player-zeus",125,Role.STARTER,submitted.plusDays(1)),null,submitted.plusDays(1));
        assertThat(revised.decisionDate()).isEqualTo(decision);assertThat(rival.decisionDate()).isEqualTo(decision);
        assertThat(rival.terms().startDate()).isEqualTo(decision);
        e.advance(decision.minusDays(1));assertThat(e.active("player-zeus",decision.minusDays(1))).isNull();assertThat(e.freeAgents).contains("player-zeus");
        e.advance(decision);var accepted=e.active("player-zeus",decision);assertThat(accepted).isNotNull();
        assertThat(accepted.terms().startDate()).isEqualTo(decision);assertThat(accepted.paidThrough()).isEqualTo(decision.minusDays(1));
        assertThat(e.contracts.get(old.contractId()).paidThrough()).isEqualTo(end);
        assertThat(e.ledger.stream().filter(l->old.contractId().equals(l.contractId())&&l.kind().equals("SALARY")).mapToLong(l->-l.amount()).sum()).isEqualTo(wages(old.terms().annualSalary(),DATE,end));
    }
    @ParameterizedTest @CsvSource({"2027-01-01,2027-12-31,365", "2028-01-01,2028-12-31,366", "2028-02-01,2028-02-29,29", "2027-12-30,2028-01-02,4"})
    void salaryRoundingIsIdenticalAcrossAdjacentRanges(String fromText,String endText,int days) {
        LocalDate from=LocalDate.parse(fromText),end=LocalDate.parse(endText);long daily=0;
        for(LocalDate date=from;!date.isAfter(end);date=date.plusDays(1))daily+=wages(193_001,date,date);
        assertThat(wages(193_001,from,end)).isEqualTo(daily);
        assertThat(java.time.temporal.ChronoUnit.DAYS.between(from,end)+1).isEqualTo(days);
    }
    @Test void migrationKeepsLongTermsAndExternalOrUnknownOrganizationsSeparateFromFreeAgents() {
        var e=engine("LCK:KT");assertThat(e.state().accounts()).hasSize(56);assertThat(e.state().preferences()).hasSize(460);
        assertThat(e.state().freeAgents()).hasSize(4).doesNotContain("player-armao","player-empyros","player-renye");
        var apa=e.active("player-apa",DATE);assertThat(apa.terms().endDate()).isEqualTo(LocalDate.of(2029,11,21));
        for(var member:roster.members().values())if(member.organizationId()!=null&&member.organizationId().startsWith("LPL:OMG"))assertThat(e.state().freeAgents()).doesNotContain(member.playerId());
        var protectedState=CareerMarketEngine.initialize("career_"+"b".repeat(64),41,LocalDate.of(2030,12,1),2027,directory,roster);
        assertThat(protectedState.contracts().values()).allSatisfy(c->assertThat(c.terms().endDate()).isAfterOrEqualTo(LocalDate.of(2031,1,30)));
    }
    @Test void reservedRenewalStartsAfterExpiryWithoutLosingTheManagedSelectionOrDoublePaying() {
        var e=engine("LCK:HLE");var original=e.active("player-zeus",DATE);
        // Isolated existing-contract fixture: five remaining days on a full-year employment term.
        var terms=new Terms(DATE.minusYears(1).plusDays(6),DATE.plusDays(5),original.terms().annualSalary(),0,Role.STARTER);
        e.contracts.put(original.contractId(),new Contract(original.contractId(),original.careerId(),original.playerId(),original.team(),original.organizationId(),terms.startDate(),terms,ContractStatus.ACTIVE,0,VERSION,"EXISTING_CONTRACT_TEST_FIXTURE",original.terminationPolicy(),null,DATE.minusDays(1)));
        var offer=e.submit("LCK:HLE","player-zeus",terms(e,"player-zeus",140,Role.STARTER),null,DATE);
        e.advance(DATE.plusDays(5));assertThat(e.state().offers().get(offer.offerId()).status()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(e.active("player-zeus",DATE.plusDays(5)).contractId()).isEqualTo(original.contractId());
        assertThat(e.scheduled("player-zeus")).isNotNull();
        e.advance(DATE.plusDays(6));assertThat(e.active("player-zeus",DATE.plusDays(6)).contractId()).isNotEqualTo(original.contractId());
        assertThat(e.state().freeAgents()).doesNotContain("player-zeus");assertThat(e.roster().lineups().get("LCK:HLE")).contains("player-zeus");
        assertThat(e.state().ledger().stream().filter(l->original.contractId().equals(l.contractId())&&l.kind().equals("SALARY")).mapToLong(l->-l.amount()).sum()).isEqualTo(wages(original.terms().annualSalary(),DATE,DATE.plusDays(5)));
        var before=e.state();e.advance(DATE.plusDays(6));assertThat(e.state()).isEqualTo(before);
    }
    @Test void unansweredCounterExpiresAndRevisedOfferCanWin() {
        var e=engine("LCK:T1");var offer=e.submit("LCK:T1","player-bo",terms(e,"player-bo",90,Role.RESERVE),null,DATE);
        e.advance(DATE.plusDays(2));assertThat(e.state().offers().get(offer.offerId()).status()).isEqualTo(OfferStatus.COUNTER);
        e.advance(DATE.plusDays(6));assertThat(e.state().offers().get(offer.offerId()).status()).isEqualTo(OfferStatus.EXPIRED);assertThat(e.reservedCash("LCK:T1")).isZero();
        var revised=engine("LCK:T1");var low=revised.submit("LCK:T1","player-bo",terms(revised,"player-bo",90,Role.RESERVE),null,DATE);
        revised.advance(DATE.plusDays(2));var t=terms(revised,"player-bo",150,Role.STARTER);
        var better=revised.submit("LCK:T1","player-bo",t,low.offerId(),DATE.plusDays(2));revised.advance(DATE.plusDays(5));
        assertThat(revised.state().offers().get(low.offerId()).status()).isEqualTo(OfferStatus.SUPERSEDED);
        assertThat(revised.state().offers().get(better.offerId()).status()).isEqualTo(OfferStatus.ACCEPTED);
    }
    @Test void boundedFiftySixClubCycleAndDailyVersusJumpRecoveryAgree() {
        var jump=engine("LCK:T1");var daily=engine("LCK:T1");
        for(String team:List.of("LCK:HLE","LCK:GEN"))for(String player:roster.members().values().stream().filter(m->team.equals(m.ownerTeam())&&directory.players().get(m.playerId()).position()==com.lolfm.domain.Position.TOP).map(CareerRosterStore.Membership::playerId).toList()) {
            jump.release(team,player,null,DATE);daily.release(team,player,null,DATE);
        }
        // No user offers. A vacancy and a strong released player are lawful isolated game preparation.
        LocalDate end=DATE.plusDays(30);jump.advance(end);
        for(LocalDate d=DATE.plusDays(1);!d.isAfter(end);d=d.plusDays(1))daily.advance(d);
        assertThat(jump.state()).isEqualTo(daily.state());assertThat(jump.roster()).isEqualTo(daily.roster());
        assertThat(jump.state().accounts()).hasSize(56);
        assertThat(jump.state().offers().values()).noneMatch(o->o.team().equals("LCK:T1"));
        assertThat(jump.state().offers().values().stream().filter(o->o.playerId().equals("player-zeus"))).hasSizeGreaterThanOrEqualTo(2);
        assertThat(jump.active("player-zeus",end)).isNotNull();
        var first=jump.state().decisions().values().stream().filter(d->d.winningOfferId()!=null&&d.evaluations().stream().anyMatch(e->e.team().equals("LCK:HLE"))&&d.evaluations().stream().anyMatch(e->e.team().equals("LCK:GEN"))).min(java.util.Comparator.comparing(Decision::date)).orElseThrow();
        String winner=jump.state().offers().get(first.winningOfferId()).team();String loser=winner.equals("LCK:HLE")?"LCK:GEN":"LCK:HLE";
        assertThat(jump.state().offers().values()).anyMatch(o->o.team().equals(loser)&&o.submittedDate().isAfter(first.date())&&o.status()==OfferStatus.ACCEPTED&&!o.playerId().equals(first.playerId()));
        assertThat(jump.roster().lineups().get(loser)).hasSize(5);
        jump.advance(end);assertThat(jump.state()).isEqualTo(daily.state());
        assertThat(jump.roster().lineups().get("LCK:T1")).isEqualTo(roster.lineups().get("LCK:T1"));
        System.out.println("AI_MARKET_ONLY "+CareerRosterStore.write(jump.state().decisions()));
    }
    @ParameterizedTest @CsvSource({"0,0","6,90000","12,180000","24,288000","36,378000"})
    void transferValueUsesPermanentRatingsAndInclusiveContractBoundary(int months,long expected) {
        var p=directory.players().get("player-jiwoo");assertThat(CareerManagementPolicy.value(p,DATE,DATE.plusMonths(months).minusDays(1))).isEqualTo(expected);
        var e=engine("LCK:T1");long value=e.tradeEngine.estimate(p.playerId(),DATE);var c=e.active(p.playerId(),DATE);
        e.contracts.put(c.contractId(),new Contract(c.contractId(),c.careerId(),c.playerId(),c.team(),c.organizationId(),c.signedDate(),new Terms(c.terms().startDate(),c.terms().endDate(),c.terms().annualSalary()*3,0,c.terms().role()),c.status(),c.revision(),c.policyVersion(),c.origin(),c.terminationPolicy(),c.endedDate(),c.paidThrough()));
        assertThat(e.tradeEngine.estimate(p.playerId(),DATE)).isEqualTo(value);
        assertThat(CareerManagementPolicy.value(p,DATE,DATE.plusMonths(12))).isGreaterThan(180000);
    }
    @Test void promiseObservationDistinguishesStarterReserveAndRecoversWithoutReplayingCompletion() {
        var e=engine("LCK:KT");String starter=roster.lineups().get("LCK:KT").stream().filter(p->directory.players().get(p).position()==com.lolfm.domain.Position.ADC).findFirst().orElseThrow();
        var a=e.promise(starter,"LCK:KT",DATE);var b=e.promise("player-jiwoo","LCK:KT",DATE);
        for(int i=1;i<=6;i++) {
            var fact=new CareerManagementState.Appearance("receipt-"+i,"fixture-"+i,"series-"+i,2027,DATE.plusDays(i),2,List.of(
                new CareerManagementState.Opportunity(starter,"LCK:KT",com.lolfm.domain.Position.ADC,a.promiseId(),true,false,"CONTRACT_AT_SERIES_START"),
                new CareerManagementState.Opportunity("player-jiwoo","LCK:KT",com.lolfm.domain.Position.ADC,b.promiseId(),true,false,"CONTRACT_AT_SERIES_START")));
            e.applyAppearance(fact);e.applyAppearance(fact);
        }
        e.promiseEngine.evaluate(DATE.plusDays(14));assertThat(e.promise(starter,"LCK:KT",DATE).satisfaction()).isEqualTo(60);
        var offer=new Offer("offer",starter,"LCK:KT",new Terms(DATE.plusDays(60),DATE.plusYears(2),300000,0,Role.STARTER),DATE,DATE,DATE,DATE,0,OfferStatus.SUBMITTED,null,1,null,"");
        long before=e.evaluate(offer,DATE).score();e.promiseEngine.evaluate(DATE.plusDays(28));
        assertThat(e.promise(starter,"LCK:KT",DATE).satisfaction()).isEqualTo(56);assertThat(e.promise("player-jiwoo","LCK:KT",DATE).satisfaction()).isEqualTo(62);
        assertThat(e.evaluate(offer,DATE).score()).isLessThan(before);var once=e.management();e.promiseEngine.evaluate(DATE.plusDays(28));assertThat(e.management()).isEqualTo(once);
        e.promiseEngine.evaluate(DATE.plusDays(42));assertThat(e.promise(starter,"LCK:KT",DATE).satisfaction()).isEqualTo(56);
        for(int i=7;i<=26;i++)e.applyAppearance(new CareerManagementState.Appearance("receipt-"+i,"fixture-"+i,"series-"+i,2027,DATE.plusDays(i+20),3,List.of(new CareerManagementState.Opportunity(starter,"LCK:KT",com.lolfm.domain.Position.ADC,a.promiseId(),true,true,"CONTRACT_AT_SERIES_START"))));
        e.promiseEngine.evaluate(DATE.plusDays(56));var recovered=e.promise(starter,"LCK:KT",DATE);
        assertThat(recovered.satisfaction()).isEqualTo(58);assertThat(recovered.opportunities()).isEqualTo(26);assertThat(recovered.sets()).isEqualTo(60);assertThat(recovered.starts()).isEqualTo(20);
        assertThat(e.promiseEngine.promises.values().stream().filter(p->p.team().startsWith("LPL:")&&p.role()==Role.STARTER)).allSatisfy(p->{assertThat(p.satisfaction()).isEqualTo(60);assertThat(p.status()).isEqualTo("INSUFFICIENT_SERIES");});
    }
    @Test void renewalKeepsRegistrationAffiliationAndAiUsesBoundedAffordableLoanCandidates() {
        var e=engine("LCK:KT");var c=e.active("player-jiwoo",DATE);var renewal=DATE.plusDays(30);
        e.promiseEngine.close(c.playerId(),c.team(),renewal.minusDays(1));
        e.promiseEngine.ensure(c,c.team(),null,Role.RESERVE,renewal,c.terms().endDate());
        assertThat(e.promiseEngine.continuousSince(c.playerId(),c.team(),renewal)).isEqualTo(DATE);
        e.promiseEngine.close(c.playerId(),c.team(),renewal.plusDays(5));
        e.promiseEngine.ensure(c,c.team(),null,Role.RESERVE,renewal.plusDays(10),c.terms().endDate());
        assertThat(e.promiseEngine.continuousSince(c.playerId(),c.team(),renewal.plusDays(10))).isEqualTo(renewal.plusDays(10));
        var ai=engine("LCK:T1");var account=ai.accounts.get("LCK:HLE");
        ai.lineups.get("LCK:HLE").removeIf(p->ai.player(p).position()==com.lolfm.domain.Position.TOP);
        long retain=account.cash()-ai.paymentHeadroom(account.team(),DATE)+200_000;
        ai.accounts.put(account.team(),new Account(account.team(),account.annualBudget(),retain,account.rosterLimit()));
        ai.tradeEngine.ai(DATE);
        var proposals=ai.management().trades().values();
        assertThat(proposals).anySatisfy(t->{assertThat(t.terms().buyer()).isEqualTo("LCK:HLE");assertThat(t.terms().kind()).isEqualTo(CareerManagementState.Kind.LOAN);assertThat(t.terms().fee()).isLessThanOrEqualTo(t.buyerLimit());});
        assertThat(proposals.stream().collect(java.util.stream.Collectors.groupingBy(t->t.terms().buyer(),java.util.stream.Collectors.counting())).values()).allSatisfy(n->assertThat(n).isLessThanOrEqualTo(2));
        assertThat(ai.management().loans()).isEmpty();assertThat(ai.roster().members()).isEqualTo(roster.members());
        System.out.println("AI_TRADE_PROPOSALS "+CareerRosterStore.write(proposals.stream().filter(t->t.terms().buyer().equals("LCK:HLE")).toList()));
    }
    static CareerManagementState.TradeTerms transfer(CareerMarketEngine e,String player,String buyer,CareerManagementState.Kind kind,int salaryPercent,long fee) {
        var c=e.active(player,DATE);LocalDate start=e.tradeEngine.decision(player,DATE).plusDays(1),end=kind==CareerManagementState.Kind.LOAN?start.plusDays(27):start.plusYears(2).minusDays(1);
        return new CareerManagementState.TradeTerms(kind,player,c.team(),buyer,start,end,fee,kind==CareerManagementState.Kind.LOAN?50:0,
                new Terms(start,end,kind==CareerManagementState.Kind.LOAN?c.terms().annualSalary():demand(directory.players().get(player))*salaryPercent/100,0,Role.RESERVE),e.tradeEngine.replacement(c.team(),player,DATE));
    }
    @ParameterizedTest @CsvSource({"1,LCK:T1", "2,LCK:T1", "1,LCK:KT", "2,LCK:KT"})
    void lateCounterGetsClubResponseBeforeCommonPlayerDecision(int daysBefore,String managed) {
        var e=engine(managed);var terms=transfer(e,"player-jiwoo","LCK:T1",CareerManagementState.Kind.TRANSFER,135,220000);
        // Test the user buyer and user seller directions; the other club must respond automatically.
        if(managed.equals("LCK:KT"))terms=new CareerManagementState.TradeTerms(terms.kind(),terms.playerId(),terms.seller(),terms.buyer(),terms.startDate(),terms.endDate(),1,0,terms.playerTerms(),terms.replacementPlayerId());
        var original=e.tradeEngine.submit(managed,terms,null,DATE);
        LocalDate counterDate=original.decisionDate().minusDays(daysBefore);
        e.advance(counterDate);
        var counter=e.tradeEngine.submit(managed,terms,original.tradeId(),counterDate);
        assertThat(counter.responseDate()).isBeforeOrEqualTo(counter.decisionDate());
        e.advance(counter.decisionDate());
        assertThat(e.tradeEngine.trades.get(original.tradeId()).status()).isEqualTo(CareerManagementState.TradeStatus.SUPERSEDED);
        assertThat(e.tradeEngine.trades.get(counter.tradeId()).status()).isEqualTo(CareerManagementState.TradeStatus.AGREED);
        e.advance(terms.startDate());
        assertThat(e.active(terms.playerId(),terms.startDate()).team()).isEqualTo("LCK:T1");
    }
    @Test void rosterCapacityChecksOnlyTheIncomingIntervalButStillCountsOverlappingLoans() {
        var e=engine("LCK:T1");var account=e.accounts.get("LCK:T1");
        e.accounts.put(account.team(),new Account(account.team(),account.annualBudget(),account.cash(),1));
        LocalDate start=LocalDate.of(2031,1,1);var incoming=new Terms(start,start.plusYears(1).minusDays(1),200000,0,Role.RESERVE);
        var c=e.active("player-jiwoo",DATE);
        var unrelated=new CareerManagementState.Loan("outside","trade",c.contractId(),c.playerId(),"LCK:KT","LCK:GEN",DATE,DATE.plusDays(30),0,50,Role.RESERVE,"LCK:KT","FIRST_TEAM","ACTIVE",CareerManagementPolicy.VERSION);
        e.tradeEngine.loans.put(unrelated.loanId(),unrelated);
        assertThatCode(()->e.requireRosterCapacity("LCK:T1","player-bo",incoming)).doesNotThrowAnyException();
        var overlapping=new CareerManagementState.Loan("inside","trade2",c.contractId(),c.playerId(),"LCK:KT","LCK:T1",start.plusDays(5),start.plusDays(32),0,50,Role.RESERVE,"LCK:KT","FIRST_TEAM","ACTIVE",CareerManagementPolicy.VERSION);
        e.tradeEngine.loans.put(overlapping.loanId(),overlapping);
        assertThatThrownBy(()->e.requireRosterCapacity("LCK:T1","player-bo",incoming)).isInstanceOf(CareerException.class);
    }
    @Test void displayAbilityAndPotentialDoNotRoundMarketStrengthOrChangePrice() {
        var base=directory.players().get("player-zeus");
        var min=new java.util.EnumMap<com.lolfm.domain.PlayerSkill,Integer>(com.lolfm.domain.PlayerSkill.class);
        base.gameplay().ratings().keySet().forEach(k->min.put(k,1));
        assertThat(PlayerAbilityPolicy.currentAbility(min)).isEqualTo(1);
        min.replaceAll((k,v)->20);assertThat(PlayerAbilityPolicy.currentAbility(min)).isEqualTo(200);
        var low=PlayerAbilityPolicy.apply(new ObjectMapper(),base,base.gameplay().ratings(),1,3,"TEST");
        var high=PlayerAbilityPolicy.apply(new ObjectMapper(),base,base.gameplay().ratings(),200,4,"TEST");
        assertThat(PlayerAbilityPolicy.currentAbility(base.gameplay().ratings())).isEqualTo(187);
        assertThat(strength(low)).isEqualTo(225);assertThat(strength(high)).isEqualTo(strength(low));
        assertThat(demand(high)).isEqualTo(225000).isEqualTo(demand(low));
        assertThat(CareerManagementPolicy.value(high,DATE,DATE.plusYears(1).minusDays(1))).isEqualTo(CareerManagementPolicy.value(low,DATE,DATE.plusYears(1).minusDays(1)));
        assertThat(high.gameplay()).isEqualTo(low.gameplay());
    }
    @Test void paidTransferNeedsBothClubAndPlayerAgreementAndMovesMoneyOnce() {
        var e=engine("LCK:T1");var t=transfer(e,"player-jiwoo","LCK:T1",CareerManagementState.Kind.TRANSFER,135,220000);
        String original=e.active(t.playerId(),DATE).contractId();var proposal=e.tradeEngine.submit("LCK:T1",t,null,DATE);
        e.tradeEngine.respond("LCK:KT",proposal.tradeId(),"ACCEPT",null,DATE);
        assertThat(e.members.get(t.playerId()).ownerTeam()).isEqualTo("LCK:KT");
        e.advance(proposal.decisionDate());assertThat(e.tradeEngine.trades.get(proposal.tradeId()).status()).isEqualTo(CareerManagementState.TradeStatus.AGREED);
        assertThat(e.members.get(t.playerId()).ownerTeam()).isEqualTo("LCK:KT");
        e.advance(t.startDate());assertThat(e.active(t.playerId(),t.startDate()).team()).isEqualTo("LCK:T1");
        assertThat(e.contracts.get(original).status()).isEqualTo(ContractStatus.TRANSFERRED);
        assertThat(e.ledger.stream().filter(l->proposal.tradeId().equals(l.contractId())).mapToLong(Ledger::amount).sum()).isZero();
        assertThat(e.ledger.stream().filter(l->proposal.tradeId().equals(l.contractId()))).hasSize(2);
        assertThat(e.ledger.stream().filter(l->original.equals(l.contractId())&&l.kind().equals("RELEASE_COST"))).isEmpty();
        assertThat(e.lineups.get("LCK:T1")).doesNotContain(t.playerId());var state=e.state();e.advance(t.startDate());assertThat(e.state()).isEqualTo(state);
        System.out.println("PAID_TRANSFER "+CareerRosterStore.write(e.tradeEngine.trades.get(proposal.tradeId())));
    }
    @Test void sellerAndPlayerRejectionsLeaveContractAndMoneyUnchangedAndReleaseReservations() {
        var seller=engine("LCK:KT");var t=transfer(seller,"player-jiwoo","LCK:T1",CareerManagementState.Kind.TRANSFER,135,220000);
        var p=seller.tradeEngine.submit("LCK:T1",t,null,DATE);seller.tradeEngine.respond("LCK:KT",p.tradeId(),"REJECT",null,DATE);
        assertThat(seller.reservedCash("LCK:T1")).isZero();assertThat(seller.active(t.playerId(),DATE).team()).isEqualTo("LCK:KT");
        var e=engine("LCK:T1");var low=transfer(e,"player-jiwoo","LCK:T1",CareerManagementState.Kind.TRANSFER,50,220000);
        var proposal=e.tradeEngine.submit("LCK:T1",low,null,DATE);e.tradeEngine.respond("LCK:KT",proposal.tradeId(),"ACCEPT",null,DATE);e.advance(proposal.decisionDate());
        assertThat(e.tradeEngine.trades.get(proposal.tradeId()).status()).isEqualTo(CareerManagementState.TradeStatus.REJECTED);
        assertThat(e.tradeEngine.trades.get(proposal.tradeId()).reason()).contains("선수");assertThat(e.reservedCash("LCK:T1")).isZero();assertThat(e.active(low.playerId(),proposal.decisionDate()).team()).isEqualTo("LCK:KT");
        assertThat(e.ledger.stream().filter(l->proposal.tradeId().equals(l.contractId()))).isEmpty();
        var selected=engine("LCK:T1");String player=roster.lineups().get("LCK:KT").getFirst();var noReplacement=transfer(selected,player,"LCK:T1",CareerManagementState.Kind.TRANSFER,135,500000);
        noReplacement=new CareerManagementState.TradeTerms(noReplacement.kind(),noReplacement.playerId(),noReplacement.seller(),noReplacement.buyer(),noReplacement.startDate(),noReplacement.endDate(),noReplacement.fee(),0,noReplacement.playerTerms(),null);
        var invalid=noReplacement;assertThatThrownBy(()->selected.tradeEngine.submit("LCK:KT",invalid,null,DATE)).isInstanceOf(CareerException.class);
    }
    @Test void loanPreservesOwnershipSplitsOneSalaryAndReturnsWithoutReplacingParentStarter() {
        var e=engine("LCK:KT");var t=transfer(e,"player-jiwoo","LCK:T1",CareerManagementState.Kind.LOAN,100,25000);var original=e.active(t.playerId(),DATE);var parentLineup=List.copyOf(e.lineups.get("LCK:KT"));
        var p=e.tradeEngine.submit("LCK:T1",t,null,DATE);e.tradeEngine.respond("LCK:KT",p.tradeId(),"ACCEPT",null,DATE);e.advance(t.startDate());
        assertThat(e.active(t.playerId(),t.startDate()).contractId()).isEqualTo(original.contractId());assertThat(e.active(t.playerId(),t.startDate()).team()).isEqualTo("LCK:KT");
        assertThat(e.eligible(t.playerId(),"LCK:T1",t.startDate())).isTrue();assertThat(e.eligible(t.playerId(),"LCK:KT",t.startDate())).isFalse();
        assertThat(e.availableStart(t.playerId(),t.startDate())).isNull();assertThatThrownBy(()->e.release("LCK:KT",t.playerId(),null,t.startDate())).isInstanceOf(CareerException.class);
        e.advance(DATE.withDayOfMonth(31));
        long paid=-e.ledger.stream().filter(l->original.contractId().equals(l.contractId())&&Set.of("SALARY","LOAN_SALARY_PARENT","LOAN_SALARY_BORROWER").contains(l.kind())).mapToLong(Ledger::amount).sum();
        assertThat(paid).isEqualTo(wages(original.terms().annualSalary(),DATE,DATE.withDayOfMonth(31)));
        assertThat(e.ledger.stream().filter(l->original.contractId().equals(l.contractId())&&l.kind().equals("LOAN_SALARY_BORROWER")).mapToLong(Ledger::amount).sum()).isEqualTo(-4685);
        e.advance(t.endDate().plusDays(1));assertThat(e.members.get(t.playerId()).ownerTeam()).isEqualTo("LCK:KT");assertThat(e.lineups.get("LCK:KT")).isEqualTo(parentLineup);
        assertThat(e.lineups.get("LCK:T1")).doesNotContain(t.playerId());var state=e.state();e.advance(t.endDate().plusDays(1));assertThat(e.state()).isEqualTo(state);
        System.out.println("LOAN_RETURN "+CareerRosterStore.write(e.management().loans()));
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void loanArrearsYearAllocationAndSameDayOriginalExpiryRemainSingleObligations(boolean expireWithLoan) {
        String career="career_"+"a".repeat(64);LocalDate date=LocalDate.of(2027,12,20),start=date.plusDays(8),end=start.plusDays(27);
        var e=new CareerMarketEngine(career,"LCK:KT",directory,roster,CareerMarketEngine.initialize(career,41,date,2027,directory,roster));
        var c=e.active("player-jiwoo",date);
        if(expireWithLoan){c=new Contract(c.contractId(),c.careerId(),c.playerId(),c.team(),c.organizationId(),c.signedDate(),new Terms(c.terms().startDate(),end,c.terms().annualSalary(),0,c.terms().role()),c.status(),c.revision(),c.policyVersion(),c.origin(),c.terminationPolicy(),null,c.paidThrough());e.contracts.put(c.contractId(),c);}
        var t=new CareerManagementState.TradeTerms(CareerManagementState.Kind.LOAN,c.playerId(),"LCK:KT","LCK:T1",start,end,25000,50,new Terms(start,end,c.terms().annualSalary(),0,Role.RESERVE),null);
        var trade=e.tradeEngine.submit("LCK:T1",t,null,date);e.tradeEngine.respond("LCK:KT",trade.tradeId(),"ACCEPT",null,date);e.advance(start);
        assertThat(e.loan(c.playerId(),start)).isNotNull();var account=e.accounts.get("LCK:T1");e.accounts.put("LCK:T1",new Account(account.team(),account.annualBudget(),0,account.rosterLimit()));
        e.advance(LocalDate.of(2027,12,31));assertThat(e.contractArrears(c.contractId())).isPositive();assertThat(e.salaryArrears("LCK:KT")).isZero();
        e=new CareerMarketEngine(career,"LCK:KT",directory,e.roster(),CareerRosterStore.read(CareerRosterStore.write(e.state()),CareerMarketState.class));
        e.advance(LocalDate.of(2028,1,1));assertThat(e.contractArrears(c.contractId())).isZero();var once=e.state();e.advance(LocalDate.of(2028,1,1));assertThat(e.state()).isEqualTo(once);
        assertThat(e.ledger.stream().filter(l->l.team().equals("LCK:T1")&&l.kind().equals("ANNUAL_ALLOCATION")&&l.date().equals(LocalDate.of(2028,1,1)))).hasSize(1);
        e.advance(end.plusDays(1));assertThat(e.loan(c.playerId(),end.plusDays(1))).isNull();
        if(expireWithLoan){assertThat(e.active(c.playerId(),end.plusDays(1))).isNull();assertThat(e.members.get(c.playerId()).ownerTeam()).isNull();assertThat(e.freeAgents).contains(c.playerId());}
        else {assertThat(e.members.get(c.playerId()).ownerTeam()).isEqualTo("LCK:KT");assertThat(e.salaryAt("LCK:KT",end.plusDays(1),false)).isGreaterThanOrEqualTo(c.terms().annualSalary());}
    }
    @Test void competingBuyersKeepCommonDecisionAndOnlyOneTransferCanSpend() {
        var a=engine("LCK:KT");var b=engine("LCK:KT");
        for(var e:List.of(a,b))for(String buyer:e==a?List.of("LCK:T1","LCK:HLE"):List.of("LCK:HLE","LCK:T1")) {
            var t=transfer(e,"player-jiwoo",buyer,CareerManagementState.Kind.TRANSFER,135,220000);var p=e.tradeEngine.submit(buyer,t,null,DATE);e.tradeEngine.respond("LCK:KT",p.tradeId(),"ACCEPT",null,DATE);
        }
        a.advance(DATE.plusDays(8));b.advance(DATE.plusDays(8));
        assertThat(a.management().trades()).isEqualTo(b.management().trades());
        var completed=a.management().trades().values().stream().filter(t->t.terms().playerId().equals("player-jiwoo")&&t.status()==CareerManagementState.TradeStatus.COMPLETED).toList();assertThat(completed).hasSize(1);
        assertThat(a.reservedCash("LCK:T1")).isZero();assertThat(a.reservedCash("LCK:HLE")).isZero();
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void retirementSettlesLoanAndExpiryOnceWithoutResurrection(boolean sameExpiry) {
        var e=engine("LCK:KT");var t=transfer(e,"player-jiwoo","LCK:T1",CareerManagementState.Kind.LOAN,100,25000);
        var offer=e.tradeEngine.submit("LCK:T1",t,null,DATE);e.tradeEngine.respond("LCK:KT",offer.tradeId(),"ACCEPT",null,DATE);e.advance(t.startDate());
        String id=t.playerId();var c=e.active(id,t.startDate());var end=t.endDate();
        if(sameExpiry){c=new Contract(c.contractId(),c.careerId(),id,c.team(),c.organizationId(),c.signedDate(),new Terms(c.terms().startDate(),end,c.terms().annualSalary(),0,c.terms().role()),c.status(),c.revision(),c.policyVersion(),c.origin(),c.terminationPolicy(),null,c.paidThrough());e.contracts.put(c.contractId(),c);}
        var age=new CareerLifecycleState.Age(null,LocalDate.of(1990,1,1),"TEST_GAME_AGE",DATE,DATE);
        var person=new CareerLifecycleState.Person(age,"AUTHORED",null,DATE,150,DATE,CareerLifecycleState.Status.RETIREMENT_ANNOUNCED,t.startDate(),end.plusDays(1),"합성 은퇴 경계",0,0,0,null,"UNOBSERVED",null);
        e.lifecycle=new CareerLifecycleEngine(new CareerLifecycleState(CareerLifecyclePolicy.VERSION,DATE,null,Map.of(id,person)));
        e.lifecycle.cancelReservations(e,id,t.startDate());e.advance(end.plusDays(1));
        assertThat(e.lifecycle.retired(id)).isTrue();assertThat(e.active(id,end.plusDays(1))).isNull();assertThat(e.freeAgents).doesNotContain(id);assertThat(e.lineups.values()).allMatch(ids->!ids.contains(id));assertThat(e.members.get(id).eligibilityReason()).isEqualTo("RETIRED");
        String contract=c.contractId();long salary=-e.ledger.stream().filter(l->contract.equals(l.contractId())&&Set.of("SALARY","LOAN_SALARY_PARENT","LOAN_SALARY_BORROWER").contains(l.kind())).mapToLong(Ledger::amount).sum();
        assertThat(salary).isEqualTo(wages(c.terms().annualSalary(),DATE,end));assertThat(e.contracts.get(contract).paidThrough()).isEqualTo(end);
        var once=e.state();var life=e.lifecycle.state();e.advance(end.plusDays(1));assertThat(e.state()).isEqualTo(once);assertThat(e.lifecycle.state()).isEqualTo(life);
        var restored=new CareerMarketEngine(e.career,e.managed,directory,e.roster(),CareerRosterStore.read(CareerRosterStore.write(once),CareerMarketState.class));restored.lifecycle=new CareerLifecycleEngine(CareerRosterStore.read(CareerRosterStore.write(life),CareerLifecycleState.class));restored.advance(end.plusDays(2));assertThat(restored.eligible(id,"LCK:KT",end.plusDays(2))).isFalse();assertThat(restored.availableStart(id,end.plusDays(2))).isNull();
    }
    @Test void retirementCancelsFutureContractAndUnpaidTradeReservation() {
        var e=engine("LCK:T1");var start=e.availableStart("player-bo",DATE).plusDays(2);var o=e.submit("LCK:T1","player-bo",new Terms(start,start.plusYears(1).minusDays(1),400000,10000,Role.RESERVE),null,DATE);
        var t=transfer(e,"player-jiwoo","LCK:T1",CareerManagementState.Kind.TRANSFER,135,220000);var trade=e.tradeEngine.submit("LCK:T1",t,null,DATE);
        e.advance(o.decisionDate());var scheduled=e.scheduled("player-bo");assertThat(scheduled).isNotNull();
        var people=new TreeMap<String,CareerLifecycleState.Person>();for(String id:List.of("player-bo","player-jiwoo")){var age=CareerLifecyclePolicy.ageProfile(directory.players().get(id),41,DATE);people.put(id,new CareerLifecycleState.Person(age,"AUTHORED",null,DATE,150,DATE,CareerLifecycleState.Status.RETIREMENT_ANNOUNCED,o.decisionDate(),start,"합성 미래 예약 경계",0,0,0,null,"UNOBSERVED",null));}
        e.lifecycle=new CareerLifecycleEngine(new CareerLifecycleState(CareerLifecyclePolicy.VERSION,DATE,null,people));var ledger=List.copyOf(e.ledger);
        for(String id:people.keySet())e.lifecycle.cancelReservations(e,id,o.decisionDate());
        assertThat(e.contracts.get(scheduled.contractId()).status()).isEqualTo(ContractStatus.CANCELLED_RETIREMENT);assertThat(e.tradeEngine.trades.get(trade.tradeId()).status()).isEqualTo(CareerManagementState.TradeStatus.CANCELLED_RETIREMENT);
        assertThat(e.ledger).isEqualTo(ledger);assertThat(e.reservedCash("LCK:T1")).isZero();e.advance(start);assertThat(e.active("player-bo",start)).isNull();assertThat(e.lifecycle.retired("player-bo")).isTrue();
    }

}
