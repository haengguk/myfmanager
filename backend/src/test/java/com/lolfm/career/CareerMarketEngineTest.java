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
}
