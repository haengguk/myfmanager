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
            if(!disclosed)reason=status.equals("DEFERRED")?(d.action().equals("COVERAGE")?reason:"선수단 자격·대체자·재정 조건을 충족하지 못해 보류"):status.equals("CLOSED")?"협상 종료 · 선수단 변경 없음":"선수단 운영 검토 · 조건과 후보는 비공개";
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
        if(m.tradeEngine.trades.values().stream().anyMatch(t->t.status()==TradeStatus.AGREED&&t.terms().playerId().equals(id)&&t.terms().seller().equals(team)&&!date.isBefore(t.terms().startDate())&&(t.terms().kind()==Kind.TRANSFER||!date.isAfter(t.terms().endDate()))))return false;
        for(var trade:m.tradeEngine.trades.values())if(trade.status()==TradeStatus.AGREED&&trade.terms().playerId().equals(id)&&trade.terms().buyer().equals(team)
                &&!date.isBefore(trade.terms().startDate())&&!date.isAfter(trade.terms().endDate()))
            return squad.equals(trade.terms().playerTerms().role()==Role.DEVELOPMENT?"DEVELOPMENT":"FIRST_TEAM");
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
    private boolean registrationAllows(String team,String id,String squad,LocalDate required){
        if(squad.equals("FIRST_TEAM")&&m.internationalPools.entrySet().stream().anyMatch(e->e.getKey().startsWith(team+'|')&&!e.getValue().contains(id)))return false;
        if(!squad.equals("FIRST_TEAM")&&m.internationalPools.values().stream().anyMatch(pool->pool.contains(id)))return false;
        // A binding in the same squad remains usable; it prohibits moving that player elsewhere.
        return m.squadRestrictions.stream().noneMatch(r->r.playerId().equals(id)&&!r.squad().equals(squad)&&(r.pending()||r.date().equals(required)));
    }
    private boolean safe(String team,String id,String squad,LocalDate date){return registrationAllows(team,id,squad,horizon(team,squad,date))&&availableOn(team,id,horizon(team,squad,date),squad)&&availableOn(team,id,date.plusDays(EXPIRY_DAYS),squad);}
    boolean committedCover(String team,Position role,String squad,LocalDate date){return committedCover(team,role,squad,date,null);}
    private boolean committedCover(String team,Position role,String squad,LocalDate date,String excludedTrade){
        LocalDate required=horizon(team,squad,date),through=date.plusDays(EXPIRY_DAYS);
        boolean scheduled=m.contracts.values().stream().anyMatch(c->team.equals(c.team())&&c.status()==ContractStatus.SCHEDULED
                &&m.player(c.playerId()).position()==role&&squad.equals(c.terms().role()==Role.DEVELOPMENT?"DEVELOPMENT":"FIRST_TEAM")
                &&!c.terms().startDate().isAfter(required)&&registrationAllows(team,c.playerId(),squad,required)&&availableOn(team,c.playerId(),through,squad));
        boolean returning=m.tradeEngine.loans.values().stream().anyMatch(l->team.equals(l.parentTeam())&&"ACTIVE".equals(l.status())
                &&m.player(l.playerId()).position()==role&&squad.equals(l.returnSquad())&&!l.endDate().plusDays(1).isAfter(required)
                &&registrationAllows(team,l.playerId(),squad,required)&&availableOn(team,l.playerId(),through,squad));
        boolean incoming=m.tradeEngine.trades.values().stream().anyMatch(t->!t.tradeId().equals(excludedTrade)&&t.status()==TradeStatus.AGREED&&team.equals(t.terms().buyer())
                &&m.player(t.terms().playerId()).position()==role&&registrationAllows(team,t.terms().playerId(),squad,required)
                &&availableOn(team,t.terms().playerId(),required,squad)&&availableOn(team,t.terms().playerId(),through,squad));
        return scheduled||returning||incoming;
    }
    /** Ephemeral diagnostics: never serialized or exposed as an opponent's private budget. */
    record Inspection(String team,Position position,String squad,int commonChecks,List<String> rejections,String chosen) {}
    final List<Inspection> inspections=new ArrayList<>();
    private final Map<String,int[]> plannedUse=new HashMap<>();
    private static long upfront(Proposal p){var t=p.trade();return t==null?p.terms().signingBonus():t.fee()+(t.kind()==Kind.TRANSFER?p.terms().signingBonus():0);}
    private static long salary(Proposal p){var t=p.trade();return t!=null&&t.kind()==Kind.LOAN?(Math.multiplyExact(p.terms().annualSalary(),t.borrowerSalaryPercent())+99)/100:p.terms().annualSalary();}
    private String approvalFailure(Proposal p,LocalDate date,List<Proposal> earlier){
        // All provisional obligations exist only in this isolated workspace. The real submit below
        // reserves them once; actual payment dates and future approvals stay in the common validator.
        var trial=trial();
        try {
            for(var chosen:earlier)if(chosen.team().equals(p.team()))submit(trial,chosen,date);
            submit(trial,p,date);return null;
        }catch(CareerException rejected){return rejected.clientMessage();}
    }
    private CareerMarketEngine trial(){
        var trial=new CareerMarketEngine(m.career,m.managed,m.directory,m.roster(),m.state());
        trial.clEnabled=m.clEnabled;trial.overseasEnabled=m.overseasEnabled;trial.developmentYear=m.developmentYear;
        trial.developmentFixtures=m.developmentFixtures;trial.squadRestrictions.addAll(m.squadRestrictions);trial.internationalPools.putAll(m.internationalPools);
        m.clLineups.forEach((team,ids)->trial.clLineups.put(team,new ArrayList<>(ids)));
        if(m.lifecycle!=null)trial.lifecycle=new CareerLifecycleEngine(m.lifecycle.state());
        return trial;
    }
    private static void submit(CareerMarketEngine market,Proposal p,LocalDate date){
        if(p.trade()==null)market.submit(p.team(),p.player(),p.terms(),null,date);
        else {
            var t=market.tradeEngine.submit(p.team(),p.trade(),null,date);
            market.tradeEngine.respond(p.trade().seller(),t.tradeId(),"ACCEPT",p.trade().replacementPlayerId(),date);
            // A forecast in the isolated package; actual consent still runs independently on its due date.
            if(!market.tradeEngine.likelyConsent(t,date))throw CareerMarketEngine.invalid("현재 보수·역할·이동 조건에서 선수 동의 전망 부족");
        }
    }
    /** Eight common approvals total per team/position/squad review, shared by floor and upgrade. */
    private final class Search {
        int checks,financeRejected,approvalRejected;boolean limited;
        final List<String> rejections=new ArrayList<>();
        final Map<LocalDate,Long> existingSalary=new HashMap<>();
        boolean accepts(Proposal p,LocalDate date,List<Proposal> earlier){
            if(!room(p.team(),date,p.trade()!=null)||p.trade()!=null&&!room(p.trade().seller(),date,true))return false;
            if(p.trade()!=null&&earlier.stream().filter(q->q.trade()!=null&&q.trade().seller().equals(p.trade().seller())).count()+plannedUse.getOrDefault(p.trade().seller(),new int[2])[1]>=TRADES_PER_CLUB)return false;
            if(p.trade()!=null&&earlier.stream().filter(q->q.team().equals(p.team())&&q.trade()!=null).count()>=TRADES_PER_CLUB)return false;
            var account=m.accounts.get(p.team());long plannedCash=0,plannedSalary=0;
            for(var chosen:earlier)if(chosen.team().equals(p.team())){
                plannedCash+=upfront(chosen);
                if(!p.terms().startDate().isBefore(chosen.terms().startDate())&&!p.terms().startDate().isAfter(chosen.terms().endDate()))plannedSalary+=salary(chosen);
            }
            long cap=m.finance==null?account.annualBudget():m.finance.approval(p.team(),p.terms().startDate()).wageLimit();
            String cheap=account.cash()-m.reservedCash(p.team())-plannedCash<upfront(p)?"CASH":
                    salary(p)+plannedSalary+existingSalary.computeIfAbsent(p.terms().startDate(),d->m.salaryAt(p.team(),d,true))>cap?"WAGE_LIMIT":
                    m.salaryArrears(p.team())>0||m.finance!=null&&m.finance.debt.getOrDefault(p.team(),0L)>0?"ARREARS":null;
            if(cheap!=null){financeRejected++;if(!rejections.contains(cheap))rejections.add(cheap);return false;}
            if(checks>=COMMON_CHECKS){limited=true;return false;}
            checks++;String failure=approvalFailure(p,date,earlier);if(failure==null)return true;
            approvalRejected++;rejections.add(failure);return false;
        }
        String reason(boolean empty){return empty?"NO_CANDIDATE · 출전·기간·대체자 조건에 맞는 선수 없음":limited?"SEARCH_LIMIT · 제한된 보완 탐색 소진":approvalRejected>0?"COMMON_APPROVAL_REJECTED · 지급·동의·기간·명부 공통 검사 불충족":financeRejected>0?"FINANCE_BLOCKED · 현금·체불·승인 연봉 한도 불충족":"NEGOTIATION_WAIT · 재접촉·거래 한도 대기";}
    }
    private final class Need {
        final String team,squad,incumbent;final Position position;final List<String> own;
        int recontactWait;final boolean safe,essential;final LocalDate required;final List<Proposal> candidates;final Search search=new Search();
        Need(String team,Position position,String squad,LocalDate date){
            this.team=team;this.position=position;this.squad=squad;own=held(team,position,squad,date);incumbent=selected(team,position,squad,date);
            boolean renewal=incumbent!=null&&m.availableStart(incumbent,date)!=null;
            safe=!renewal&&own.stream().anyMatch(id->safe(team,id,squad,date));required=horizon(team,squad,date);
            essential=!safe&&(squad.equals("FIRST_TEAM")||m.developmentFixtures.getOrDefault(team+"|DEVELOPMENT",List.of()).stream().anyMatch(d->!d.isBefore(date)&&!d.isAfter(date.plusDays(EXPIRY_DAYS))));
            int best=own.stream().mapToInt(id->strength(m.player(id))).max().orElse(0);
            var pool=new ArrayList<String>();
            for(String id:m.directory.players().keySet())if(m.player(id).position()==position&&(m.lifecycle==null||!m.lifecycle.announced(id))){
                boolean renewing=own.contains(id)&&m.availableStart(id,date)!=null;
                if((renewing||recruitable(team,id,date))&&(!safe||own.size()<MAX_POSITION_PLAYERS&&strength(m.player(id))>best+IMPROVEMENT))pool.add(id);
            }
            pool.sort(Comparator.comparingInt((String id)->strength(m.player(id))+(own.contains(id)?AI_RENEWAL_ADVANTAGE:0)).reversed().thenComparing(order(m)));
            if(renewal&&pool.remove(incumbent))pool.addFirst(incumbent);
            candidates=new ArrayList<>();
            for(String id:safe?pool.stream().limit(CANDIDATES).toList():pool){
                if(m.offers.values().stream().anyMatch(o->o.team().equals(team)&&o.playerId().equals(id)&&date.isBefore(o.decisionDate().plusDays(REVIEW_WAIT_DAYS)))){recontactWait++;continue;}
                int firstAlternative=candidates.size();
                Role promise=squad.equals("DEVELOPMENT")?Role.DEVELOPMENT:!safe||incumbent==null||id.equals(incumbent)||strength(m.player(id))>strength(m.player(incumbent))+IMPROVEMENT?Role.STARTER:Role.RESERVE;
                LocalDate available=m.availableStart(id,date);
                String reason=own.contains(id)?"만료 60일 이내 · 현재 역할 유지 재계약":safe?"명확한 현재 기량 차이에 따른 보강":"확인된 계약 만료·복귀 또는 선수단 공백 대비";
                if(available!=null){long bid=m.demand(id,date)*(AI_MIN_BID_PERCENT+variation(m.seed,"AI_BID|"+team+'|'+id+'|'+date,AI_BID_VARIANTS))/100;
                    candidates.add(new Proposal(team,position,squad,id,new Terms(available,available.plusYears(AI_CONTRACT_YEARS).minusDays(1),bid,m.demand(id,date)/AI_BONUS_DIVISOR,promise),null,reason));
                }else {
                    var c=m.active(id,date);if(c==null||!canDepart(c.team(),id,date))continue;
                    var terms=m.tradeEngine.plannedTerms(team,id,promise,date);
                    if(terms!=null){candidates.add(new Proposal(team,position,squad,id,terms.playerTerms(),terms,reason));
                        if(terms.kind()==Kind.TRANSFER){var loan=m.tradeEngine.plannedLoanTerms(team,id,promise,date);if(loan!=null)candidates.add(new Proposal(team,position,squad,id,loan.playerTerms(),loan,reason));}}
                }
                // Preserve player quality order. Equivalent-player trade forms use the same
                // effective-date/cost order as the funded package, instead of transfer-first insertion.
                candidates.subList(firstAlternative,candidates.size()).sort(costOrder());
            }
        }
        Comparator<Proposal> costOrder(){return Comparator.comparingInt((Proposal p)->onTime(p)?0:1).thenComparingLong(CareerSquadPlanner::salary).thenComparingLong(CareerSquadPlanner::upfront).thenComparing(Proposal::player).thenComparing(p->p.trade()==null?"":p.trade().kind().name());}
        boolean onTime(Proposal p){
            if(!registrationAllows(team,p.player(),squad,required))return false;
            if(p.terms().endDate().isBefore(required))return false;
            if(!p.terms().startDate().isAfter(required))return true;
            var old=m.active(p.player(),m.processedThrough());
            return own.contains(p.player())&&old!=null&&!old.terms().endDate().isBefore(required)&&!p.terms().startDate().isAfter(old.terms().endDate().plusDays(1));
        }
        String reason(){return candidates.isEmpty()&&recontactWait>0?"NEGOTIATION_WAIT · 재접촉 대기":search.reason(candidates.isEmpty());}
        int priority(){return essential?(squad.equals("FIRST_TEAM")?0:1):2;}
        Proposal renewal(){return candidates.stream().filter(p->p.player().equals(incumbent)&&own.contains(p.player())).findFirst().orElse(null);}
    }
    private List<Need> needs(String team,LocalDate date){
        var result=new ArrayList<Need>();
        for(Position role:Position.values()){
            if(pending(team,role))continue;
            var first=held(team,role,"FIRST_TEAM",date);boolean firstSafe=first.stream().anyMatch(id->safe(team,id,"FIRST_TEAM",date));
            String incumbent=selected(team,role,"FIRST_TEAM",date);boolean renewal=incumbent!=null&&m.availableStart(incumbent,date)!=null;
            boolean scheduledDevelopment=cl(team)&&m.developmentFixtures.getOrDefault(team+"|DEVELOPMENT",List.of()).stream().anyMatch(d->!d.isBefore(date)&&!d.isAfter(date.plusDays(EXPIRY_DAYS)));
            for(String squad:cl(team)&&(firstSafe&&!renewal||scheduledDevelopment)?List.of("FIRST_TEAM","DEVELOPMENT"):List.of("FIRST_TEAM")){
                if(committedCover(team,role,squad,date)){record(team,role,squad,"FUTURE_COVER","PLANNED",null,null,date,null,null,"확정된 계약 또는 임대 반환이 다음 필요 시점을 충족 · 중복 영입 보류");continue;}
                if(waiting(team,role,date)&&held(team,role,squad,date).stream().anyMatch(id->safe(team,id,squad,date)))continue;
                result.add(new Need(team,role,squad,date));
            }
        }
        result.sort(Comparator.comparingInt(Need::priority).thenComparing(n->n.required).thenComparing(n->n.position).thenComparing(n->n.squad.equals("FIRST_TEAM")?0:1));
        return result;
    }
    private static List<Proposal> without(List<Proposal> plan,Proposal own){var rest=new ArrayList<>(plan);rest.remove(own);return rest;}
    /** Count durable submissions, including withdrawn/rejected proposals and both trade parties. */
    private boolean room(String team,LocalDate date,boolean trade){
        LocalDate monday=date.minusDays(date.getDayOfWeek().getValue()-1);
        long offers=m.offers.values().stream().filter(o->o.team().equals(team)&&!o.submittedDate().isBefore(monday)&&!o.submittedDate().isAfter(date)&&o.previousOfferId()==null).count();
        long trades=m.tradeEngine.trades.values().stream().filter(t->(t.terms().buyer().equals(team)||t.terms().seller().equals(team))&&!t.submittedDate().isBefore(monday)&&!t.submittedDate().isAfter(date)&&t.previousTradeId()==null).count();
        var planned=plannedUse.getOrDefault(team,new int[2]);
        return offers+trades+planned[0]<NEW_PROPOSALS_PER_CLUB&&(!trade||trades+planned[1]<TRADES_PER_CLUB);
    }
    private boolean pressure(String team,LocalDate date){
        try{m.requireBudget(team,date);return false;}catch(CareerException insufficient){return true;}
    }
    boolean disposal(String reference){var t=m.tradeEngine.trades.get(reference);return t!=null&&t.proposer().equals(t.terms().seller())&&!t.proposer().equals(m.managed);}
    boolean wantsIncoming(String team,String player,Role role,LocalDate date){return wantsIncoming(team,player,role,date,null);}
    boolean wantsIncoming(String team,String player,Role role,LocalDate date,String excludedTrade){
        String squad=role==Role.DEVELOPMENT?"DEVELOPMENT":"FIRST_TEAM";Position position=m.player(player).position();
        if(!registrationAllows(team,player,squad,horizon(team,squad,date)))return false;
        return !committedCover(team,position,squad,date,excludedTrade)
                &&held(team,position,squad,date).stream().filter(id->!id.equals(player)).noneMatch(id->safe(team,id,squad,date));
    }
    private void tidy(String team,LocalDate date){
        var seen=new HashSet<String>();boolean pressured=pressure(team,date);
        // Confirmed contracts/agreements never enter this withdrawal branch.
        for(var o:new ArrayList<>(m.offers.values()))if(o.team().equals(team)&&o.open()){
            String squad=o.terms().role()==Role.DEVELOPMENT?"DEVELOPMENT":"FIRST_TEAM";String slot=m.player(o.playerId()).position()+"|"+squad;
            if(!seen.add(slot)||pressured&&!wantsIncoming(team,o.playerId(),o.terms().role(),date)){
                m.withdraw(team,o.offerId(),date);record(team,m.player(o.playerId()).position(),squad,"WITHDRAW","APPLIED",o.playerId(),null,date,date,o.offerId(),"재정 압박 또는 중복 보강 예약 정리 · 확정 계약 유지");
            }
        }
        for(var t:new ArrayList<>(m.tradeEngine.trades.values()))if(t.terms().buyer().equals(team)&&t.buyerAgreed()&&t.open()&&t.status()!=TradeStatus.AGREED){
            String squad=t.terms().playerTerms().role()==Role.DEVELOPMENT?"DEVELOPMENT":"FIRST_TEAM";String slot=m.player(t.terms().playerId()).position()+"|"+squad;
            if(!seen.add(slot)||pressured&&!wantsIncoming(team,t.terms().playerId(),t.terms().playerTerms().role(),date,t.tradeId()))m.tradeEngine.respond(team,t.tradeId(),"WITHDRAW",null,date);
        }
    }
    private void relieve(String team,LocalDate date,Set<String> fundedContracts,List<Proposal> purchases){
        if(!room(team,date,true))return;
        // Wait for the existing sale's real effect. Unconfirmed proceeds are never purchase money.
        if(m.tradeEngine.trades.values().stream().anyMatch(t->t.open()&&t.proposer().equals(team)&&t.terms().seller().equals(team)))return;
        var surplus=m.contracts.values().stream().filter(c->c.status()==ContractStatus.ACTIVE&&team.equals(c.team())&&canDepart(team,c.playerId(),date)&&m.tradeEngine.unavailable(c.playerId(),date)==null)
                .sorted(Comparator.comparingLong((Contract c)->c.terms().annualSalary()).reversed().thenComparing(Contract::playerId)).limit(CANDIDATES).toList();
        for(var c:surplus){
            String id=c.playerId();Position position=m.player(id).position();var options=new ArrayList<TradeTerms>();
            for(String buyer:m.accounts.keySet())if(!buyer.equals(team)&&room(buyer,date,true)&&!pending(buyer,position)){
                for(Role role:cl(buyer)?List.of(Role.STARTER,Role.DEVELOPMENT):List.of(Role.STARTER)){
                    if(fundedContracts.contains(key(buyer,position))||!wantsIncoming(buyer,id,role,date)||committedCover(buyer,position,role==Role.DEVELOPMENT?"DEVELOPMENT":"FIRST_TEAM",date))continue;
                    var transfer=m.tradeEngine.plannedTerms(buyer,id,role,date);if(transfer!=null)options.add(transfer);
                    var loan=m.tradeEngine.plannedLoanTerms(buyer,id,role,date);if(loan!=null&&!options.contains(loan))options.add(loan);
                }
            }
            // A permanent transfer removes the returning salary risk; loans preserve their original pay.
            options.sort(Comparator.comparingInt((TradeTerms t)->t.kind()==Kind.TRANSFER?0:1).thenComparing(Comparator.comparingLong(TradeTerms::fee).reversed()).thenComparing(TradeTerms::buyer));
            int checked=0;
            for(var terms:options){
                if(checked++>=COMMON_CHECKS)break;
                if(terms.kind()==Kind.LOAN){
                    LocalDate returned=terms.endDate().plusDays(1);
                    long cap=m.finance==null?m.accounts.get(team).annualBudget():m.finance.approval(team,returned).wageLimit();
                    if(terms.endDate().isBefore(date.plusDays(EXPIRY_DAYS))||m.salaryAt(team,returned,true)>cap)continue;
                }
                try{
                    var trial=trial();
                    // A seller's offer competes for the buyer's complete funded package, not its empty budget.
                    for(var purchase:purchases)if(purchase.team().equals(terms.buyer())&&purchase.position()!=position)submit(trial,purchase,date);
                    var proposed=trial.tradeEngine.submit(team,terms,null,date);
                    trial.tradeEngine.respond(terms.buyer(),proposed.tradeId(),"ACCEPT",null,date);
                    if(!trial.tradeEngine.likelyConsent(proposed,date))continue;
                    var actual=m.tradeEngine.submit(team,terms,null,date);
                    record(team,position,m.members.get(id).squad(),"DISPOSE_"+terms.kind(),"PROPOSED",id,null,date,terms.startDate(),actual.tradeId(),"재정 압박 대응 · 같은 포지션 대체자 유지 · 실제 구단/선수 동의 및 적용 후 재검토");
                    return;
                }catch(CareerException rejected){/* Another affordable counterparty may remain. */}
            }
        }
        record(team,Position.TOP,"FIRST_TEAM","FINANCE","DEFERRED",null,null,date,null,null,"FINANCE_BLOCKED · 현금/예정 급여/승인 압박 · 현재 대체자와 합법적 거래 상대 없음");
    }
    void review(LocalDate date){review(date,Set.of());}
    void review(LocalDate date,Set<String> changed){
        boolean weekly=date.getDayOfWeek()==DayOfWeek.MONDAY;
        if(date.equals(lastReview)||!weekly&&changed.isEmpty())return;
        inspections.clear();plannedUse.clear();
        repair(date);for(String team:m.accounts.keySet())if(weekly&&!team.equals(m.managed))for(Position role:Position.values()){choose(team,role,date,false);if(cl(team))chooseCl(team,role,date);}
        repair(date);var proposed=new ArrayList<Proposal>();var pressureTeams=new LinkedHashSet<String>();
        for(String team:m.accounts.keySet())if(!team.equals(m.managed)&&(weekly||changed.contains(team))){
            tidy(team,date);boolean pressured=pressure(team,date);
            if(pressured)pressureTeams.add(team);
            var needs=needs(team,date).stream().filter(n->n.essential||weekly&&!pressured).toList();var plan=new ArrayList<Proposal>();var floors=new LinkedHashMap<Need,Proposal>();
            // First fund a common-approved low-cost package. No real offers or cash are reserved here.
            // More than four needs may be forecast; only this week's allowed subset is submitted.
            for(var need:needs)if(need.essential){
                Proposal floor=null;var renewal=need.renewal();
                if(renewal!=null&&need.search.accepts(renewal,date,plan))floor=renewal;
                var cheap=new ArrayList<>(need.candidates);
                cheap.sort(need.costOrder());
                for(var candidate:cheap){
                    if(candidate.equals(renewal)){if(floor!=null)break;continue;}
                    if(need.search.accepts(candidate,date,plan)){floor=candidate;break;}
                    if(need.search.checks>=COMMON_CHECKS){need.search.limited=true;break;}
                }
                if(floor!=null){plan.add(floor);floors.put(need,floor);}
            }
            // A failed essential slot may exchange one earlier floor for a different cost structure.
            // Both needs retain their original approval counters; no provisional reservation escapes.
            for(var missing:needs)if(missing.essential&&!floors.containsKey(missing)){
                boolean repaired=false;
                for(var prior:new ArrayList<>(floors.keySet())){
                    if(prior.position==missing.position)continue;
                    var old=floors.get(prior);var rest=without(plan,old);
                    var alternatives=new ArrayList<>(prior.candidates);
                    alternatives.sort(Comparator.comparingInt((Proposal p)->prior.onTime(p)?0:1).thenComparingLong(CareerSquadPlanner::upfront).thenComparingLong(CareerSquadPlanner::salary).thenComparing(Proposal::player));
                    for(var alternative:alternatives){
                        if(alternative.equals(old)||prior.onTime(old)&&!prior.onTime(alternative))continue;
                        if(!prior.search.accepts(alternative,date,rest))continue;
                        var packagePlan=new ArrayList<>(rest);packagePlan.add(alternative);
                        for(var candidate:missing.candidates.stream().sorted(missing.costOrder()).toList()){
                            if(missing.search.accepts(candidate,date,packagePlan)){
                                plan.remove(old);plan.add(alternative);plan.add(candidate);floors.put(prior,alternative);floors.put(missing,candidate);repaired=true;break;
                            }
                            if(missing.search.checks>=COMMON_CHECKS)break;
                        }
                        if(repaired||missing.search.checks>=COMMON_CHECKS)break;
                    }
                    if(repaired||missing.search.checks>=COMMON_CHECKS)break;
                }
            }
            if(needs.stream().anyMatch(n->n.essential&&!floors.containsKey(n)&& (n.search.financeRejected>0||n.search.approvalRejected>0)))pressureTeams.add(team);
            // Improve one position only if every other funded essential position still fits.
            // Incumbent renewal is first in this order and shares, rather than resets, the eight checks.
            for(var need:needs){
                Proposal floor=floors.get(need);
                if(plan.stream().anyMatch(p->p.position()==need.position&&!p.squad().equals(need.squad)))continue;
                if(need.essential&&floor==null)continue;
                var rest=without(plan,floor);
                for(var candidate:need.candidates){
                    if(candidate.equals(floor))break;
                    if(floor!=null&&need.onTime(floor)&&!need.onTime(candidate))continue;
                    if(floor!=null&&!candidate.player().equals(need.incumbent)&&strength(m.player(candidate.player()))<strength(m.player(floor.player())))continue;
                    if(need.search.accepts(candidate,date,rest)){if(floor!=null)plan.remove(floor);plan.add(candidate);floors.put(need,candidate);break;}
                    if(need.search.checks>=COMMON_CHECKS){need.search.limited=true;break;}
                }
            }
            int count=0,trades=0;var positions=EnumSet.noneOf(Position.class);
            for(var need:needs){
                var chosen=floors.get(need);
                inspections.add(new Inspection(team,need.position,need.squad,need.search.checks,List.copyOf(need.search.rejections),chosen==null?null:chosen.player()));
                if(chosen!=null&&count<NEW_PROPOSALS_PER_CLUB&&positions.add(need.position)&&(chosen.trade()==null||trades<TRADES_PER_CLUB)){
                    proposed.add(chosen);count++;if(chosen.trade()!=null)trades++;
                }else if(!need.safe)record(team,need.position,need.squad,"COVERAGE","DEFERRED",need.own.isEmpty()?null:need.own.getFirst(),null,date,null,null,(chosen==null?need.reason():"NEGOTIATION_WAIT · 주간 제안·거래 한도 대기")+" · 강제 계약 없음");
                for(String id:need.own)if(need.own.size()>1&&!id.equals(need.own.getFirst())&&m.availableStart(id,date)!=null)
                    record(team,need.position,need.squad,"EXPIRY_REVIEW","PLANNED",id,null,date,m.active(id,date).terms().endDate().plusDays(1),null,"현재 대체 자원 보유 · 거래는 별도 합의, 미갱신 시 자연 만료");
            }
            for(var p:proposed)if(p.team().equals(team)){
                var buyerUse=plannedUse.computeIfAbsent(team,k->new int[2]);buyerUse[0]++;if(p.trade()!=null){buyerUse[1]++;var sellerUse=plannedUse.computeIfAbsent(p.trade().seller(),k->new int[2]);sellerUse[0]++;sellerUse[1]++;}
            }
        }
        plannedUse.clear();
        // A seller must not displace an already funded essential FA package with an unsolicited loan.
        var fundedContracts=new HashSet<String>();for(var p:proposed)if(p.trade()==null)fundedContracts.add(key(p.team(),p.position()));
        for(String team:pressureTeams)relieve(team,date,fundedContracts,proposed);
        // Proposals only reserve obligations. Existing common player decisions still choose the winner.
        for(var p:proposed){
            if(pending(p.team(),p.position())||!room(p.team(),date,p.trade()!=null)||p.trade()!=null&&!room(p.trade().seller(),date,true))continue;
            try {
                String ref=p.trade()==null?m.submit(p.team(),p.player(),p.terms(),null,date).offerId():m.tradeEngine.submit(p.team(),p.trade(),null,date).tradeId();
                cooldowns.put(key(p.team(),p.position()),date.plusDays(REVIEW_WAIT_DAYS));
                record(p.team(),p.position(),p.squad(),p.trade()==null?"CONTRACT_PROPOSAL":p.trade().kind().name(),"PROPOSED",p.player(),null,date,p.terms().startDate(),ref,p.reason());
            }catch(CareerException rejected){cooldowns.put(key(p.team(),p.position()),date.plusDays(REVIEW_WAIT_DAYS));record(p.team(),p.position(),p.squad(),"PROPOSAL","DEFERRED",p.player(),null,date,null,null,rejected.clientMessage());}
        }
        lastReview=date;
    }
}
