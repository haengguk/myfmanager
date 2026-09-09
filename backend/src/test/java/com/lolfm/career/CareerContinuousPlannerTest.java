package com.lolfm.career;
import static com.lolfm.career.CareerContinuousProgress.*;
import static org.assertj.core.api.Assertions.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
class CareerContinuousPlannerTest {
    final LocalDate today=LocalDate.of(2027,1,4);
    Run run(){var r=new Run();r.seasonYear=2027;r.mode=Mode.TARGET_DATE;r.targetDate=today;return r;}
    @ParameterizedTest @EnumSource(value=Reason.class,names={"PLAYER_MATCH","PLAYER_SERIES","PLAYER_CHOICE","CONTRACT_RESPONSE","TRADE_RESPONSE","ROSTER_DECISION","FINANCE_DECISION"})
    void ownerDecisionPrecedesTargetAndAutomaticWork(Reason reason) {
        var stop=new Stop(Category.USER_DECISION,reason,"LCK:GEN","owned","MATCH");
        var next=CareerContinuousPlanner.next(run(),new CareerContinuousPlanner.Situation(today,true,true,true,stop,null,true,false,null));
        assertThat(next.stop()).isEqualTo(stop);assertThat(next.action()).isNull();
    }
    @ParameterizedTest @EnumSource(CareerMarketState.OfferStatus.class)
    void onlyManagedCounterofferRequiresAnAnswer(CareerMarketState.OfferStatus status) {
        var offer=new CareerMarketState.Offer("offer","player","LCK:GEN",null,today,today,today,today,0,status,null,1,null,null);
        var market=new CareerMarketState(CareerMarketPolicy.VERSION,1,today,java.util.Map.of(),java.util.Map.of("offer",offer),java.util.Map.of(),java.util.Map.of(),java.util.Set.of(),java.util.List.of(),java.util.Map.of(),java.util.List.of());
        assertThat(CareerContinuousPlanner.marketDecision(market,"LCK:GEN")!=null).isEqualTo(status==CareerMarketState.OfferStatus.COUNTER);
        assertThat(CareerContinuousPlanner.marketDecision(market,"LCK:T1")).isNull();
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"CLUB_PENDING,false,true,true","CLUB_COUNTER,true,false,false","PLAYER_PENDING,true,true,false","AGREED,true,true,false","EXPIRED,false,false,false"})
    void clubConsentBelongsToTheManagedParty(CareerManagementState.TradeStatus status,boolean sellerAgreed,boolean buyerAgreed,boolean stops) {
        var terms=new CareerManagementState.TradeTerms(CareerManagementState.Kind.LOAN,"player","LCK:GEN","LCK:T1",today,today.plusDays(7),0,100,null,null);
        var trade=new CareerManagementState.Trade("trade","contract","LCK:T1",terms,today,today,today,today,1,null,status,sellerAgreed,buyerAgreed,0,0,0,null,null,CareerManagementPolicy.VERSION,null);
        var management=new CareerManagementState(CareerManagementPolicy.VERSION,today,java.util.Map.of(),java.util.Map.of("trade",trade),java.util.Map.of(),java.util.Map.of());
        var market=new CareerMarketState(CareerMarketPolicy.VERSION,1,today,java.util.Map.of(),java.util.Map.of(),java.util.Map.of(),java.util.Map.of(),java.util.Set.of(),java.util.List.of(),java.util.Map.of(),java.util.List.of(),management);
        assertThat(CareerContinuousPlanner.marketDecision(market,"LCK:GEN")!=null).isEqualTo(stops);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"MANAGER,USER_DECISION", "ROSTER_REVIEW,RECOVERABLE_ERROR", "AI_CLUB,AUTO"})
    void overdueRegistrationResponsibilityNeverDefaultsToAi(String responsibility,String expected) {
        var wait=new CareerRegistrationWait("ROSTER_REPAIR_REQUIRED","FIRST_STAND",null,"LCK:GEN","LCK:GEN",responsibility,java.util.List.of(),java.util.List.of());
        var stop=CareerContinuousPlanner.registrationDecision(java.util.List.of(wait));
        if(expected.equals("AUTO"))assertThat(stop).isNull();
        else {assertThat(stop.category().name()).isEqualTo(expected);assertThat(stop.reason()).isEqualTo(Reason.ROSTER_DECISION);}
        var r=run();r.targetDate=today.plusDays(5);
        var next=CareerContinuousPlanner.next(r,new CareerContinuousPlanner.Situation(today,true,true,false,stop,null,false,true,"ROSTER_REPAIR_REQUIRED"));
        if(stop==null)assertThat(next.action()).isEqualTo(Action.ADVANCE);
        else {assertThat(next.action()).isNull();assertThat(next.stop()).isEqualTo(stop);}
    }
    @Test void yearEndAllowsDueWorkButNeverAdvancesIntoAnotherSeason() {
        var r=run();r.mode=Mode.NEXT_MANAGED_MATCH;
        var last=LocalDate.of(2027,12,31);
        assertThat(CareerContinuousPlanner.next(r,new CareerContinuousPlanner.Situation(last,true,true,false,null,null,true,false,null)).action()).isEqualTo(Action.COMPETITION);
        assertThat(CareerContinuousPlanner.next(r,new CareerContinuousPlanner.Situation(last,true,true,false,null,null,false,true,"ROSTER_REPAIR_REQUIRED")).stop().reason()).isEqualTo(Reason.SEASON_TRANSITION);
        assertThat(CareerContinuousPlanner.next(r,new CareerContinuousPlanner.Situation(last.minusDays(1),true,true,false,null,null,false,false,null)).action()).isEqualTo(Action.ADVANCE);
    }
    @Test void priorSeasonPausedRunAllowsNewStartAndPermanentFailureRequiresRepair() {
        var r=run();r.status=Status.PAUSED;
        assertThat(allowedCommands(r,2027)).containsExactly("RESUME");
        assertThat(allowedCommands(r,2028)).containsExactly("START");
        CareerContinuousApplicationService.fail(r,new CareerContinuousApplicationService.BlockedChild("original-job","BAD_RECEIPT"));
        assertThat(r.stop.category()).isEqualTo(Category.BLOCKED_ERROR);
        assertThat(r.stop.referenceId()).isEqualTo("original-job");
        assertThat(allowedCommands(r,2027)).isEmpty();
    }
    @Test void inclusiveTargetRunsDueWorkAndTransitionBeforeStopping() {
        var r=run();
        assertThat(CareerContinuousPlanner.next(r,new CareerContinuousPlanner.Situation(today,true,true,true,null,null,true,false,null)).action()).isEqualTo(Action.REFRESH);
        assertThat(CareerContinuousPlanner.next(r,new CareerContinuousPlanner.Situation(today,true,true,false,null,null,true,false,null)).action()).isEqualTo(Action.COMPETITION);
        assertThat(CareerContinuousPlanner.next(r,new CareerContinuousPlanner.Situation(today,true,true,false,null,null,false,false,null)).stop().reason()).isEqualTo(Reason.TARGET_REACHED);
        assertThat(CareerContinuousPlanner.next(r,new CareerContinuousPlanner.Situation(today,true,true,false,null,null,false,true,"ROSTER_REPAIR_REQUIRED")).stop().reason()).isEqualTo(Reason.TARGET_REPAIR_BOUNDARY);
    }
    @Test void pendingOriginalIntentAndPermittedOffseasonContinue() {
        var r=run();r.mode=Mode.NEXT_MANAGED_MATCH;r.targetDate=null;
        var intent=new Intent(Action.ADVANCE,"original",3L,"ADVANCE_ONE_DAY",null,null,today);
        assertThat(CareerContinuousPlanner.next(r,new CareerContinuousPlanner.Situation(today,true,false,true,null,intent,false,false,null)).action()).isEqualTo(Action.ADVANCE);
        assertThat(CareerContinuousPlanner.next(r,new CareerContinuousPlanner.Situation(today,true,true,false,null,null,false,false,"SEASON_ROLLOVER_REQUIRED")).action()).isEqualTo(Action.ADVANCE);
        assertThat(CareerContinuousPlanner.next(r,new CareerContinuousPlanner.Situation(today,true,false,false,null,null,false,false,"SEASON_ROLLOVER_REQUIRED")).stop().reason()).isEqualTo(Reason.SEASON_TRANSITION);
        assertThat(CareerContinuousPlanner.next(r,new CareerContinuousPlanner.Situation(today,false,true,false,null,null,false,false,null)).stop().reason()).isEqualTo(Reason.SEASON_TRANSITION);
    }
}
