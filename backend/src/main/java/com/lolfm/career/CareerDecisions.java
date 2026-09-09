package com.lolfm.career;

import static com.lolfm.career.CareerContinuousProgress.*;
import java.time.LocalDate;
import java.util.*;

/** Shared read-only decisions, ordered exactly as continuous progression's stop priority. */
final class CareerDecisions {
    public record Decision(String id,String revision,Reason type,String status,String responsibility,boolean blocksProgress,LocalDate date,LocalDate deadline,String title,String summary,CareerInboxStore.Link link,Stop stop) {}
    static List<Decision> current(CareerMarketState market,String managed,CareerCalendarApplicationService.CalendarView view,CareerCalendarLeaguePort.SeasonProjection league,List<CareerRegistrationWait> waits) {
        var result=new ArrayList<Decision>();int year=view.state().seasonYear();LocalDate date=view.state().currentDate();
        result.addAll(market(market,managed,year,date));
        var dates=new HashMap<String,LocalDate>();view.fixtureOverlay().fixtures().forEach(f->dates.put(f.fixtureId(),f.date()));
        for(var f:league.fixtures())if(!"COMPLETED".equals(f.fixtureStatus())&&"PLAYER_CONTROLLED".equals(f.executionMode())&&dates.containsKey(f.fixtureId())&&!dates.get(f.fixtureId()).isAfter(date)) {
            Reason reason=f.boundSeriesId()==null?Reason.PLAYER_MATCH:Reason.PLAYER_SERIES;
            result.add(decision("LEAGUE:"+f.fixtureId(),Objects.toString(f.boundSeriesId(),"UNSTARTED"),reason,date,null,"관리 경기 진행",f.firstTeamCode()+" vs "+f.secondTeamCode(),new CareerInboxStore.Link("MATCH",null,f.fixtureId(),"LCK_REGULAR_R1_R2",year,f.boundSeriesId(),List.of()),new Stop(Category.USER_DECISION,reason,managed,f.boundSeriesId(),"MATCH")));
        }
        var f=view.competition().nextFixture();
        if(f!=null&&!f.date().isAfter(date)&&"PLAYER_CONTROLLED".equals(f.executionMode())) {
            Reason reason=f.bindingHash()==null?Reason.PLAYER_MATCH:Reason.PLAYER_SERIES;
            result.add(decision("COMP:"+f.fixtureId(),Objects.toString(f.bindingHash(),"UNSTARTED"),reason,date,null,"관리 대회 경기 진행",f.competitionId(),new CareerInboxStore.Link("MATCH",null,f.fixtureId(),f.competitionId(),year,f.seriesId(),List.of()),new Stop(Category.USER_DECISION,reason,managed,f.seriesId(),"MATCH")));
        }
        for(var w:waits.stream().filter(w->!"AI_CLUB".equals(w.responsibility())).sorted(Comparator.comparingInt(w->"MANAGER".equals(w.responsibility())?0:1)).toList()) {
            var stop=CareerContinuousPlanner.registrationDecision(List.of(w));
            result.add(new Decision("REGISTRATION:"+w.competitionId()+":"+w.teamId(),CareerRosterStore.hash(CareerRosterStore.write(w)),Reason.ROSTER_DECISION,"OPEN",w.responsibility(),true,date,null,"대회 등록 확인",w.competitionId()+" · "+String.join(", ",w.missingPositions().stream().map(Enum::name).toList()),new CareerInboxStore.Link("ROSTER",null,w.teamId(),w.competitionId(),year,null,w.missingPositions().stream().map(Enum::name).toList()),stop));
        }
        if("SEASON_ROLLOVER_REQUIRED".equals(view.blockingReason())&&view.allowedAdvanceModes().isEmpty())result.add(decision("SEASON:"+year,Integer.toString(year),Reason.SEASON_TRANSITION,date,null,"다음 시즌 전환","시즌 화면에서 명시적으로 전환하세요.",CareerInboxStore.Link.of("SEASONS",null,null,null,year),new Stop(Category.BOUNDARY,Reason.SEASON_TRANSITION,managed,null,"SEASONS")));
        return List.copyOf(result);
    }
    static List<Decision> market(CareerMarketState market,String managed,int year,LocalDate date) {
        if(market==null)return List.of();var result=new ArrayList<Decision>();
        for(var stop:CareerContinuousPlanner.marketDecisions(market,managed)) {
            if(stop.reason()==Reason.CONTRACT_RESPONSE){var offer=market.offers().get(stop.referenceId());result.add(decision("OFFER:"+offer.offerId(),Long.toString(offer.revision()),stop.reason(),date,offer.expiresDate(),"계약 역제안 응답", "요청 연봉 "+offer.requestedSalary()+"원 · "+offer.reason(),CareerInboxStore.Link.of("MARKET",offer.playerId(),offer.offerId(),null,year),stop));}
            else {var trade=market.management().trades().get(stop.referenceId());result.add(decision("TRADE:"+trade.tradeId(),CareerRosterStore.hash(CareerRosterStore.write(trade)),stop.reason(),date,trade.expiresDate(),"유료 이적·임대 조건 응답",trade.reason(),CareerInboxStore.Link.of("TRADE",trade.terms().playerId(),trade.tradeId(),null,year),stop));}
        }return List.copyOf(result);
    }
    private static Decision decision(String id,String revision,Reason type,LocalDate date,LocalDate deadline,String title,String summary,CareerInboxStore.Link link,Stop stop){return new Decision(id,revision,type,"OPEN","MANAGER",true,date,deadline,title,summary,link,stop);}
    private CareerDecisions() {}
}
