package com.lolfm.career;

import static com.lolfm.career.CareerContinuousProgress.*;
import java.time.LocalDate;
import java.util.Set;

/** Small deterministic next-action policy. Domain projections supply authority, not display text. */
final class CareerContinuousPlanner {
    record Situation(LocalDate date,boolean seasonMatches,boolean advanceAllowed,boolean refreshNeeded,
                     Stop decision,Intent pending,boolean dueAuto,boolean aiRepair,String blocker) {}
    record Next(Action action,Stop stop) {
        static Next action(Action a){return new Next(a,null);}
        static Next stop(Category c,Reason r,String action){return new Next(null,new Stop(c,r,null,null,action));}
    }
    static Next next(Run run,Situation s) {
        if(!s.seasonMatches())return Next.stop(Category.BOUNDARY,Reason.SEASON_TRANSITION,"SEASONS");
        if(s.decision()!=null)return new Next(null,s.decision());
        if(s.pending()!=null)return Next.action(s.pending().action());
        if(s.refreshNeeded())return Next.action(Action.REFRESH);
        if(s.dueAuto())return Next.action(Action.COMPETITION);
        if(run.mode==Mode.NEXT_MANAGED_MATCH&&!s.date().isBefore(LocalDate.of(run.seasonYear,12,31)))
            return Next.stop(Category.BOUNDARY,Reason.SEASON_TRANSITION,"SEASONS");
        if(s.aiRepair()) {
            if(run.mode==Mode.TARGET_DATE&&!s.date().isBefore(run.targetDate))
                return Next.stop(Category.RECOVERABLE_ERROR,Reason.TARGET_REPAIR_BOUNDARY,"EXTEND_TARGET");
            return s.advanceAllowed()?Next.action(Action.ADVANCE):Next.stop(Category.RECOVERABLE_ERROR,Reason.AI_ROSTER_REPAIR,"RETRY");
        }
        if(run.mode==Mode.TARGET_DATE&&!s.date().isBefore(run.targetDate))return Next.stop(Category.BOUNDARY,Reason.TARGET_REACHED,null);
        if(s.advanceAllowed())return Next.action(Action.ADVANCE);
        if("SEASON_ROLLOVER_REQUIRED".equals(s.blocker()))return Next.stop(Category.BOUNDARY,Reason.SEASON_TRANSITION,"SEASONS");
        return Next.stop(Category.RECOVERABLE_ERROR,Reason.NO_PROGRESS,"CALENDAR");
    }
    static Stop registrationDecision(java.util.List<CareerRegistrationWait> waits) {
        var manager=waits.stream().filter(w->"MANAGER".equals(w.responsibility())).findFirst();
        if(manager.isPresent())return new Stop(Category.USER_DECISION,Reason.ROSTER_DECISION,manager.get().teamId(),manager.get().competitionId(),"ROSTER");
        var unknown=waits.stream().filter(w->!"AI_CLUB".equals(w.responsibility())).findFirst();
        return unknown.map(w->new Stop(Category.RECOVERABLE_ERROR,Reason.ROSTER_DECISION,w.teamId(),w.competitionId(),"ROSTER")).orElse(null);
    }
    static Stop marketDecision(CareerMarketState market,String managed) {return marketDecisions(market,managed).stream().findFirst().orElse(null);}
    static java.util.List<Stop> marketDecisions(CareerMarketState market,String managed) {
        var result=new java.util.ArrayList<Stop>();if(market==null)return result;
        for(var o:market.offers().values().stream().sorted(java.util.Comparator.comparing(CareerMarketState.Offer::offerId)).toList())
            if(managed.equals(o.team())&&o.status()==CareerMarketState.OfferStatus.COUNTER)
                result.add(new Stop(Category.USER_DECISION,Reason.CONTRACT_RESPONSE,managed,o.offerId(),"MARKET"));
        if(market.management()!=null)for(var t:market.management().trades().values().stream().sorted(java.util.Comparator.comparing(CareerManagementState.Trade::tradeId)).toList())
            if(t.open()&&t.status()!=CareerManagementState.TradeStatus.AGREED
                    &&(managed.equals(t.terms().seller())&&!t.sellerAgreed()||managed.equals(t.terms().buyer())&&!t.buyerAgreed()))
                result.add(new Stop(Category.USER_DECISION,Reason.TRADE_RESPONSE,managed,t.tradeId(),"MARKET"));
        return java.util.List.copyOf(result);
    }
    private CareerContinuousPlanner() {}
}
