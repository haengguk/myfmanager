package com.lolfm.career;

import java.time.*;
import java.util.*;
import com.lolfm.domain.Position;
import static com.lolfm.career.CareerMarketState.*;
import static com.lolfm.career.CareerMarketPolicy.*;
import static com.lolfm.career.CareerSquadPlanningPolicy.*;
import static com.lolfm.career.CareerManagementState.*;

/** One date transaction, one common proposal budget. Persisted state contains decisions, not candidate rankings. */
final class CareerSquadPlanner {
    record Restriction(String playerId,String squad,LocalDate date,boolean pending){}
    private record Proposal(String team,Position position,String squad,String player,Terms terms,TradeTerms trade,String reason){}
    private final CareerMarketEngine m;
    private LocalDate lastReview;
    private final Map<String,LocalDate> cooldowns=new TreeMap<>();
    private final List<CareerSquadPlanningPolicy.Decision> history=new ArrayList<>();
    CareerSquadPlanner(CareerMarketEngine market,State saved){m=market;if(saved!=null){lastReview=saved.lastReview();cooldowns.putAll(saved.cooldowns());history.addAll(saved.decisions());
        for(var d:saved.decisions())if(d.status().equals("APPLIED")&&Set.of("PROMOTE","SELECT","PLACE").contains(d.action()))cooldowns.merge(key(d.team(),d.position())+"|"+d.squad(),d.date().plusDays(REVIEW_WAIT_DAYS),(a,b)->a.isAfter(b)?a:b);}}
    State state(){return new State(CareerSquadPlanningPolicy.VERSION,lastReview,cooldowns,history.stream().skip(Math.max(0,history.size()-HISTORY_LIMIT)).toList());}
    List<CareerSquadPlanningPolicy.Decision> publicDecisions(){
        var result=new ArrayList<CareerSquadPlanningPolicy.Decision>();
        for(var d:history){String status=d.status();boolean disclosed=Set.of("APPLIED","RETAINED").contains(status);String reason=d.reason();
            if(d.referenceId()!=null){var offer=m.offers.get(d.referenceId());var trade=m.tradeEngine.trades.get(d.referenceId());
                if(offer!=null){status=offer.status()==OfferStatus.ACCEPTED?(m.state().processedThrough().isBefore(offer.terms().startDate())?"AGREED":"APPLIED"):offer.open()?"PROPOSED":"CLOSED";disclosed=offer.status()==OfferStatus.ACCEPTED||offer.team().equals(m.managed);}
                if(trade!=null){status=trade.status()==TradeStatus.COMPLETED?"APPLIED":trade.status()==TradeStatus.AGREED?"AGREED":trade.open()?"AWAITING_CONSENT":"CLOSED";disclosed=trade.status()==TradeStatus.COMPLETED||trade.terms().buyer().equals(m.managed)||trade.terms().seller().equals(m.managed);}
            }
            if(!disclosed)reason=status.equals("DEFERRED")?"선수단 자격·대체자·재정 조건을 충족하지 못해 보류":status.equals("CLOSED")?"협상 종료 · 선수단 변경 없음":"선수단 운영 검토 · 조건과 후보는 비공개";
            result.add(new CareerSquadPlanningPolicy.Decision(d.id(),d.seasonYear(),d.date(),d.team(),d.position(),d.squad(),d.action(),status,disclosed?d.playerId():null,disclosed?d.previousPlayerId():null,disclosed?d.effectiveDate():null,null,reason));
        }
        return result.reversed();
    }
    private String key(String team,Position role){return team+'|'+role;}
    private boolean waiting(String team,Position role,LocalDate date){var until=cooldowns.get(key(team,role));return until!=null&&date.isBefore(until);}
    private void record(String team,Position role,String squad,String action,String status,String player,String before,LocalDate date,LocalDate effective,String ref,String reason){
        String id=CareerMarketEngine.id(m.career,"SQUAD|"+date+'|'+team+'|'+role+'|'+squad+'|'+action+'|'+player);
        if(history.stream().noneMatch(d->d.id().equals(id)))history.add(new CareerSquadPlanningPolicy.Decision(id,m.developmentYear>0?m.developmentYear:date.getYear(),date,team,role,squad,action,status,player,before,effective,ref,reason));
    }
    private boolean cl(String team){return m.clEnabled&&team.startsWith("LCK:")||m.overseasEnabled&&team.equals("LEC:KC");}
    private List<String> held(String team,Position role,String squad,LocalDate date){return m.members.values().stream()
            .filter(v->team.equals(v.ownerTeam())&&m.player(v.playerId()).position()==role&&(squad==null||squad.equals(v.squad()))&&m.eligible(v.playerId(),team,date))
            .map(CareerRosterStore.Membership::playerId).sorted(order(m)).toList();}
    private String selected(String team,Position role,String squad,LocalDate date){
        if(m.overseasEnabled&&team.equals("LEC:KC")&&squad.equals("DEVELOPMENT"))return held(team,role,squad,date).stream().filter(id->usableNext(team,id,squad,date)).findFirst().orElse(null);
        return (squad.equals("DEVELOPMENT")?m.clLineups:m.lineups).getOrDefault(team,List.of()).stream()
            .filter(id->m.player(id).position()==role&&m.eligible(id,team,date)&&squad.equals(m.members.get(id).squad())).findFirst().orElse(null);}
    boolean movable(String id,String target,LocalDate date){
        if(m.squadRestrictions.stream().anyMatch(r->r.playerId().equals(id)&&(r.pending()||r.date().equals(date)&&!r.squad().equals(target))))return false;
        if(!"FIRST_TEAM".equals(target)&&m.internationalPools.values().stream().anyMatch(p->p.contains(id)))return false;
        return true;
    }
    private boolean usableNext(String team,String id,String squad,LocalDate date){
        if(!m.eligible(id,team,date)||!movable(id,squad,date))return false;
        if(squad.equals("FIRST_TEAM")&&m.internationalPools.entrySet().stream().anyMatch(e->e.getKey().startsWith(team+'|')&&!e.getValue().contains(id)))return false;
        LocalDate next=m.developmentFixtures.getOrDefault(team+(squad.equals("DEVELOPMENT")?"|DEVELOPMENT":""),List.of()).stream().filter(d->!d.isBefore(date)).min(LocalDate::compareTo).orElse(date);
        return availableOn(team,id,next,squad);
    }
    private boolean availableOn(String team,String id,LocalDate date,String squad){
        var person=m.lifecycle==null?null:m.lifecycle.people.get(id);if(person!=null&&person.effectiveOn()!=null&&!date.isBefore(person.effectiveOn()))return false;
        if(m.tradeEngine.trades.values().stream().anyMatch(t->t.status()==TradeStatus.AGREED&&t.terms().playerId().equals(id)&&t.terms().seller().equals(team)&&!date.isBefore(t.terms().startDate())))return false;
        for(var loan:m.tradeEngine.loans.values())if(loan.playerId().equals(id)&&"ACTIVE".equals(loan.status())) {
            if(!date.isAfter(loan.endDate()))return team.equals(loan.borrowingTeam());
            if(!team.equals(loan.parentTeam())||!squad.equals(loan.returnSquad()))return false;
        }
        return m.contracts.values().stream().anyMatch(c->c.playerId().equals(id)&&team.equals(c.team())&&(c.status()==ContractStatus.ACTIVE||c.status()==ContractStatus.SCHEDULED)
                &&!date.isBefore(c.terms().startDate())&&!date.isAfter(c.terms().endDate())&&(c.status()!=ContractStatus.SCHEDULED||squad.equals(c.terms().role()==Role.DEVELOPMENT?"DEVELOPMENT":"FIRST_TEAM")));
    }
    /** A voluntary exit must leave both played squads covered, including the next scheduled game. */
    boolean canDepart(String team,String id,LocalDate date){
        if(!movable(id,"EXIT",date))return false;
        var member=m.members.get(id);if(member==null||!team.equals(member.ownerTeam()))return false;
        String squad=member.squad();Position role=m.player(id).position();
        return held(team,role,squad,date).stream().anyMatch(p->!p.equals(id)&&usableNext(team,p,squad,date));
    }
    private void place(String team,String id,String squad,LocalDate date){
        if(!m.eligible(id,team,date)||!movable(id,squad,date))throw CareerMarketEngine.invalid("선수단 변경 시점의 계약·출전 자격이 바뀌었습니다.");
        String org=squad.equals("DEVELOPMENT")?m.developmentOrganization(team):team;
        if(org==null)throw CareerMarketEngine.invalid("연계 육성 조직이 없습니다.");
        m.members.put(id,new CareerRosterStore.Membership(id,team,org,squad,null));
        if(m.lifecycle!=null)m.lifecycle.observePlacement(id,m.members.get(id),date);
    }
    private boolean choose(String team,Position role,LocalDate date,boolean emergency){
        String current=selected(team,role,"FIRST_TEAM",date);
        if(!emergency&&selectionWaiting(team,role,"FIRST_TEAM",date))return false;
        var candidates=held(team,role,null,date).stream().filter(id->usableNext(team,id,"FIRST_TEAM",date)).limit(CANDIDATES).toList();
        String best=candidates.isEmpty()?null:candidates.getFirst();if(best==null)return false;
        if(current!=null&&(current.equals(best)||strength(m.player(best))<=strength(m.player(current))+IMPROVEMENT)){
            if(!emergency)record(team,role,"FIRST_TEAM","RETAIN","RETAINED",current,null,date,date,null,"현재 선발 유지 · 교체에 필요한 기량 차이 미충족");return false;
        }
        boolean promotion="DEVELOPMENT".equals(m.members.get(best).squad());String swap=null;
        if(promotion&&cl(team)&&held(team,role,"DEVELOPMENT",date).stream().noneMatch(p->!p.equals(best)&&usableNext(team,p,"DEVELOPMENT",date))){
            // A legal swap keeps the old starter's contract promise intact.
            swap=held(team,role,"FIRST_TEAM",date).stream().filter(p->!p.equals(best)&&usableNext(team,p,"DEVELOPMENT",date)).findFirst().orElse(null);
            if(swap==null){if(!emergency)record(team,role,"FIRST_TEAM","PROMOTE","DEFERRED",best,current,date,null,null,"CL 같은 포지션 대체자 없음");return false;}
        }
        if(swap!=null){m.lineups.get(team).remove(swap);place(team,swap,"DEVELOPMENT",date);}
        place(team,best,"FIRST_TEAM",date);m.select(team,best,date);
        cooldowns.put(key(team,role)+"|FIRST_TEAM",date.plusDays(REVIEW_WAIT_DAYS));
        if(promotion)cooldowns.put(key(team,role)+"|DEVELOPMENT",date.plusDays(REVIEW_WAIT_DAYS));
        record(team,role,"FIRST_TEAM",promotion?"PROMOTE":"SELECT","APPLIED",best,current,date,date,null,(current==null?"실제 선발 공백 보완":"현재 기량 차이 "+(strength(m.player(best))-strength(m.player(current))))+(swap==null?"":" · 기존 선수 CL 배치, 계약 역할 유지"));
        if(swap!=null)record(team,role,"DEVELOPMENT","PLACE","APPLIED",swap,best,date,date,null,"승격에 따른 CL 대체 · 계약 약속 유지");
        return true;
    }
    private boolean selectionWaiting(String team,Position role,String squad,LocalDate date){var until=cooldowns.get(key(team,role)+"|"+squad);return until!=null&&date.isBefore(until);}
    private void chooseCl(String team,Position role,LocalDate date){
        if(selectionWaiting(team,role,"DEVELOPMENT",date))return;
        String current=selected(team,role,"DEVELOPMENT",date);
        var candidates=held(team,role,"DEVELOPMENT",date).stream().filter(id->usableNext(team,id,"DEVELOPMENT",date)).limit(CANDIDATES).toList();
        if(candidates.isEmpty())return;
        String best=candidates.getFirst();
        if(current!=null&&(best.equals(current)||strength(m.player(best))<=strength(m.player(current))+IMPROVEMENT))return;
        if(!usableNext(team,best,"DEVELOPMENT",date)||current!=null&&!movable(current,"DEVELOPMENT",date)){
            record(team,role,"DEVELOPMENT","SELECT","DEFERRED",best,current,date,null,null,"진행 경기·등록·다음 경기 자격으로 CL 선발 변경 보류");return;
        }
        var ids=new ArrayList<>(m.clLineups.getOrDefault(team,List.of()));ids.removeIf(id->m.player(id).position()==role);ids.add(best);ids.sort(Comparator.comparing(id->m.player(id).position()));m.clLineups.put(team,ids);
        cooldowns.put(key(team,role)+"|DEVELOPMENT",date.plusDays(REVIEW_WAIT_DAYS));
        record(team,role,"DEVELOPMENT","SELECT","APPLIED",best,current,date,date,null,"CL 현재 기량 차이에 따른 정기 선발 갱신");
    }
    private boolean recruitable(String team,String id,LocalDate date){return !team.equals(m.members.get(id).ownerTeam())&&(m.lifecycle==null||!m.lifecycle.announced(id))
            &&(m.availableStart(id,date)!=null||m.tradeEngine.unavailable(id,date)==null&&canDepart(m.members.get(id).ownerTeam(),id,date));}
    /** Emergency repairs never replace a legal incumbent. Also used once to build initial CL registration. */
    void repair(LocalDate date){
        for(String team:m.accounts.keySet())if(!team.equals(m.managed)){
            for(Position role:Position.values())if(selected(team,role,"FIRST_TEAM",date)==null)choose(team,role,date,true);
            if(cl(team))for(Position role:Position.values()){
                String current=selected(team,role,"DEVELOPMENT",date);if(current!=null)continue;
                String best=held(team,role,"DEVELOPMENT",date).stream().filter(id->usableNext(team,id,"DEVELOPMENT",date)).findFirst().orElse(null);
                if(best==null){best=held(team,role,"FIRST_TEAM",date).stream().filter(id->!m.lineups.get(team).contains(id)&&usableNext(team,id,"DEVELOPMENT",date)).findFirst().orElse(null);
                    if(best!=null){place(team,best,"DEVELOPMENT",date);record(team,role,"DEVELOPMENT","PLACE","APPLIED",best,null,date,date,null,"CL 포지션 공백을 기존 1군 후보로 보완 · 계약 약속 유지");}}
                if(best!=null){var ids=new ArrayList<>(m.clLineups.getOrDefault(team,List.of()));ids.removeIf(id->m.player(id).position()==role);ids.add(best);ids.sort(Comparator.comparing(id->m.player(id).position()));m.clLineups.put(team,ids);}
            }
        }
    }
    private boolean pending(String team,Position role){return m.offers.values().stream().anyMatch(o->o.open()&&o.team().equals(team)&&m.player(o.playerId()).position()==role)
            ||m.tradeEngine.trades.values().stream().anyMatch(t->t.open()&&t.terms().buyer().equals(team)&&m.player(t.terms().playerId()).position()==role);}
    private LocalDate horizon(String team,String squad,LocalDate date){
        return m.developmentFixtures.getOrDefault(team+(squad.equals("DEVELOPMENT")?"|DEVELOPMENT":""),List.of()).stream().map(d->d.isBefore(date)?date:d).min(LocalDate::compareTo).filter(d->d.isBefore(date.plusDays(EXPIRY_DAYS))).orElse(date.plusDays(EXPIRY_DAYS));
    }
    private boolean safe(String team,String id,String squad,LocalDate date){return availableOn(team,id,horizon(team,squad,date),squad)&&availableOn(team,id,date.plusDays(EXPIRY_DAYS),squad);}
    boolean committedCover(String team,Position role,String squad,LocalDate date){
        LocalDate required=horizon(team,squad,date),through=date.plusDays(EXPIRY_DAYS);
        boolean scheduled=m.contracts.values().stream().anyMatch(c->team.equals(c.team())&&c.status()==ContractStatus.SCHEDULED
                &&m.player(c.playerId()).position()==role&&squad.equals(c.terms().role()==Role.DEVELOPMENT?"DEVELOPMENT":"FIRST_TEAM")
                &&!c.terms().startDate().isAfter(required)&&availableOn(team,c.playerId(),through,squad));
        boolean returning=m.tradeEngine.loans.values().stream().anyMatch(l->team.equals(l.parentTeam())&&"ACTIVE".equals(l.status())
                &&m.player(l.playerId()).position()==role&&squad.equals(l.returnSquad())&&!l.endDate().plusDays(1).isAfter(required)
                &&availableOn(team,l.playerId(),through,squad));
        return scheduled||returning;
    }
    private boolean affordable(Proposal p,LocalDate date){
        // Reuse the existing obligation validator on an isolated workspace; no proposal/receipt is persisted here.
        var trial=new CareerMarketEngine(m.career,m.managed,m.directory,m.roster(),m.state());
        try {if(p.trade()==null)trial.submit(p.team(),p.player(),p.terms(),null,date);else trial.tradeEngine.submit(p.team(),p.trade(),null,date);return true;}
        catch(CareerException insufficient){return false;}
    }
    void review(LocalDate date){
        if(date.getDayOfWeek()!=DayOfWeek.MONDAY||date.equals(lastReview))return;
        // All lineup decisions use today's settled growth. No external contract is awarded in this phase.
        repair(date);for(String team:m.accounts.keySet())if(!team.equals(m.managed))for(Position role:Position.values()){choose(team,role,date,false);if(cl(team))chooseCl(team,role,date);}
        repair(date);
        var proposed=new ArrayList<Proposal>();
        for(String team:m.accounts.keySet())if(!team.equals(m.managed)){
            int count=0,trades=0;
            for(Position role:Position.values()){
                if(count>=NEW_PROPOSALS_PER_CLUB||pending(team,role))continue;
                // Each position shares one slot across first team, development, FA, transfers and loans.
                var first=held(team,role,"FIRST_TEAM",date);
                boolean firstSafe=first.stream().anyMatch(id->safe(team,id,"FIRST_TEAM",date));
                String firstSelected=selected(team,role,"FIRST_TEAM",date);
                boolean renewFirst=firstSelected!=null&&m.availableStart(firstSelected,date)!=null;
                // An unaffordable first-team upgrade must not starve a viable CL need.
                for(String target:firstSafe&&!renewFirst&&cl(team)?List.of("FIRST_TEAM","DEVELOPMENT"):List.of("FIRST_TEAM")){
                var own=held(team,role,target,date);
                String incumbent=selected(team,role,target,date);
                boolean renewalDue=incumbent!=null&&m.availableStart(incumbent,date)!=null;
                boolean safe=!renewalDue&&own.stream().anyMatch(id->safe(team,id,target,date));
                if(committedCover(team,role,target,date)){
                    record(team,role,target,"FUTURE_COVER","PLANNED",null,null,date,null,null,"확정된 계약 또는 임대 반환이 다음 필요 시점을 충족 · 중복 영입 보류");continue;
                }
                if(waiting(team,role,date)&&!own.isEmpty())continue;
                int best=own.stream().mapToInt(id->strength(m.player(id))).max().orElse(0);
                var pool=new ArrayList<String>();
                for(String id:m.directory.players().keySet())if(m.player(id).position()==role&&(m.lifecycle==null||!m.lifecycle.announced(id))){
                    boolean renewal=own.contains(id)&&m.availableStart(id,date)!=null;
                    boolean external=recruitable(team,id,date);
                    if((renewal||external)&&(!safe||own.size()<MAX_POSITION_PLAYERS&&strength(m.player(id))>best+IMPROVEMENT))pool.add(id);
                }
                pool.sort(Comparator.comparingInt((String id)->strength(m.player(id))+(own.contains(id)?AI_RENEWAL_ADVANTAGE:0)).reversed().thenComparing(order(m)));
                Proposal chosen=null;
                var bounded=new ArrayList<>(pool.stream().limit(CANDIDATES).toList());
                if(renewalDue&&!bounded.contains(incumbent)){if(bounded.size()==CANDIDATES)bounded.removeLast();bounded.add(incumbent);}
                for(String id:bounded){
                    if(m.offers.values().stream().anyMatch(o->o.team().equals(team)&&o.playerId().equals(id)&&date.isBefore(o.decisionDate().plusDays(REVIEW_WAIT_DAYS))))continue;
                    Role promise=target.equals("DEVELOPMENT")?Role.DEVELOPMENT:!safe||incumbent==null||id.equals(incumbent)||strength(m.player(id))>strength(m.player(incumbent))+IMPROVEMENT?Role.STARTER:Role.RESERVE;
                    LocalDate start=m.availableStart(id,date);
                    String reason=own.contains(id)?"만료 60일 이내 · 현재 역할 유지 재계약":safe?"명확한 현재 기량 차이에 따른 보강":"확인된 계약 만료·복귀 또는 선수단 공백 대비";
                    if(start!=null){long salary=m.demand(id)*(AI_MIN_BID_PERCENT+variation(m.seed,"AI_BID|"+team+'|'+id+'|'+date,AI_BID_VARIANTS))/100;
                        var candidate=new Proposal(team,role,target,id,new Terms(start,start.plusYears(AI_CONTRACT_YEARS).minusDays(1),salary,m.demand(id)/AI_BONUS_DIVISOR,promise),null,reason);
                        if(affordable(candidate,date)){chosen=candidate;break;}continue;}
                    if(trades>=TRADES_PER_CLUB)continue;
                    var c=m.active(id,date);if(c==null||!canDepart(c.team(),id,date))continue;
                    var t=m.tradeEngine.plannedTerms(team,id,promise,date);if(t!=null){var candidate=new Proposal(team,role,target,id,t.playerTerms(),t,reason);if(affordable(candidate,date)){chosen=candidate;break;}
                        if(t.kind()==Kind.TRANSFER){var loan=m.tradeEngine.plannedLoanTerms(team,id,promise,date);if(loan!=null){candidate=new Proposal(team,role,target,id,loan.playerTerms(),loan,reason);if(affordable(candidate,date)){chosen=candidate;break;}}}}
                }
                if(chosen!=null){proposed.add(chosen);count++;if(chosen.trade()!=null)trades++;}
                else if(!safe)record(team,role,target,"COVERAGE","DEFERRED",own.isEmpty()?null:own.getFirst(),null,date,null,null,"합법적인 후보·대체자·예산 또는 동의 대기 · 강제 계약 없음");
                // Surplus contracts expire naturally; no premature termination or invented proceeds.
                for(String id:own)if(own.size()>1&&!id.equals(own.getFirst())&&m.availableStart(id,date)!=null)
                    record(team,role,target,"EXPIRY_REVIEW","PLANNED",id,null,date,m.active(id,date).terms().endDate().plusDays(1),null,"현재 대체 자원 보유 · 거래는 별도 합의, 미갱신 시 자연 만료");
                if(chosen!=null)break;
                }
            }
        }
        // Proposals only reserve obligations. Existing common player decisions still choose the winner.
        for(var p:proposed){
            if(pending(p.team(),p.position()))continue;
            try {
                String ref=p.trade()==null?m.submit(p.team(),p.player(),p.terms(),null,date).offerId():m.tradeEngine.submit(p.team(),p.trade(),null,date).tradeId();
                cooldowns.put(key(p.team(),p.position()),date.plusDays(REVIEW_WAIT_DAYS));
                record(p.team(),p.position(),p.squad(),p.trade()==null?"CONTRACT_PROPOSAL":p.trade().kind().name(),"PROPOSED",p.player(),null,date,p.terms().startDate(),ref,p.reason());
            }catch(CareerException rejected){cooldowns.put(key(p.team(),p.position()),date.plusDays(REVIEW_WAIT_DAYS));record(p.team(),p.position(),p.squad(),"PROPOSAL","DEFERRED",p.player(),null,date,null,null,rejected.clientMessage());}
        }
        lastReview=date;
    }
}
