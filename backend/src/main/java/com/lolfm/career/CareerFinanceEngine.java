package com.lolfm.career;

import java.time.*;
import java.util.*;
import static com.lolfm.career.CareerFinanceState.*;
import static com.lolfm.career.CareerFinancePolicy.*;
import static com.lolfm.career.CareerMarketPolicy.wages;
import static com.lolfm.career.CareerMarketState.*;

/** Mutates only the enclosing market workspace; callers own the existing Career transaction. */
final class CareerFinanceEngine {
    final CareerMarketEngine m;final CareerFinanceState basis;
    LocalDate through;LocalDate recurringEffective;
    final Map<String,Approval> approvals=new TreeMap<>();final Map<String,Target> targets=new TreeMap<>();
    final Map<String,Award> awards=new TreeMap<>();final Map<String,Long> debt=new TreeMap<>();final Map<String,String> held=new TreeMap<>();
    CareerFinanceEngine(CareerMarketEngine market,CareerFinanceState state){m=market;basis=state;through=state.operatingThrough();recurringEffective=state.recurringEffectiveOn();approvals.putAll(state.approvals());targets.putAll(state.targets());awards.putAll(state.awards());debt.putAll(state.operatingArrears());held.putAll(state.heldPrizes());}
    CareerFinanceState state(){return new CareerFinanceState(VERSION,"KRW",basis.referenceSeason(),basis.scenario(),basis.sourceHash(),basis.sourceHashes(),basis.fxPolicyVersion(),basis.fx(),basis.introducedOn(),through,basis.legacyTransition(),recurringEffective,basis.teams(),basis.prices(),basis.prizeRules(),approvals,targets,awards,basis.excludedInstances(),debt,held);}
    Approval approval(String team,LocalDate date){return approvals.values().stream().filter(a->a.team().equals(team)&&!a.effectiveOn().isAfter(date)).max(Comparator.comparing(Approval::effectiveOn).thenComparingInt(Approval::seasonYear)).orElseThrow();}
    boolean recurring(String team,LocalDate date){return FUNDING.equals(approval(team,date).policy());}
    boolean wageLimitBreached(String team,LocalDate from){
        var dates=new TreeSet<LocalDate>();dates.add(from);approvals.values().stream().filter(a->a.team().equals(team)).forEach(a->dates.add(a.effectiveOn()));
        m.contracts.values().stream().filter(c->team.equals(c.team())||m.tradeEngine.loans.values().stream().anyMatch(l->l.contractId().equals(c.contractId())&&team.equals(l.borrowingTeam()))).forEach(c->{dates.add(c.terms().startDate());dates.add(c.terms().endDate().plusDays(1));});
        m.offers.values().stream().filter(o->o.open()&&team.equals(o.team())).forEach(o->{dates.add(o.terms().startDate());dates.add(o.terms().endDate().plusDays(1));});
        m.tradeEngine.loans.values().stream().filter(l->team.equals(l.parentTeam())||team.equals(l.borrowingTeam())).forEach(l->{dates.add(l.startDate());dates.add(l.endDate().plusDays(1));});
        m.tradeEngine.obligations(team).forEach(t->{dates.add(t.terms().startDate());dates.add(t.terms().endDate().plusDays(1));});
        return dates.stream().filter(d->!d.isBefore(from)).anyMatch(d->m.salaryAt(team,d,true)>approval(team,d).wageLimit());
    }
    long demand(String id){var p=basis.prices().get(id);if(p==null){var definition=m.player(id);String region=definition.initialOwnerTeam()==null?"LCK":CareerMarketPolicy.region(definition.initialOwnerTeam());
        p=basis.prices().values().stream().filter(x->x.region().equals(region)&&x.role().equals("DEVELOPMENT")).sorted(Comparator.comparingLong(Price::referenceSalary)).skip(basis.prices().values().stream().filter(x->x.region().equals(region)&&x.role().equals("DEVELOPMENT")).count()/2).findFirst().orElse(new Price(DEFAULT_DEVELOPMENT_REFERENCE,REFERENCE_STRENGTH,"LCK","DEVELOPMENT","REGIONAL_ROLE_REFERENCE"));}
        return Math.max(1,ratio(p.referenceSalary(),CareerMarketPolicy.strength(m.player(id)),p.referenceStrength()));
    }
    private void entry(String team,LocalDate date,String key,String kind,long amount){
        if(amount==0)return;String id=CareerMarketEngine.id(m.career,"FINANCE|"+key+"|"+team);
        if(m.ledger.stream().anyMatch(l->l.entryId().equals(id)))return;
        var a=m.accounts.get(team);long cash=safe(Math.addExact(a.cash(),amount));if(cash<0)throw new IllegalStateException("FINANCE_NEGATIVE_CASH");
        m.accounts.put(team,new Account(team,a.annualBudget(),cash,a.rosterLimit()));m.ledger.add(new Ledger(id,date,team,null,kind,amount));
    }
    void date(LocalDate date){
        for(String team:m.accounts.keySet()){
            var a=approval(team,date);var account=m.accounts.get(team);
            if(a.effectiveOn().equals(date))m.accounts.put(team,new Account(team,a.wageLimit(),account.cash(),account.rosterLimit()));
            if(!recurring(team,date))continue;
            if(date.getDayOfMonth()==date.lengthOfMonth()&&date.isAfter(through)){
                LocalDate start=through.plusDays(1);if(start.isBefore(a.effectiveOn()))start=a.effectiveOn();
                entry(team,date,"SUPPORT|"+date,"GAME_CLUB_SUPPORT",wages(a.annualSupport(),start,date));
                entry(team,date,"SPONSOR|"+date,"GAME_BASE_SPONSOR",wages(a.annualSponsor(),start,date));
                long due=wages(a.annualNonWage(),start,date)+debt.getOrDefault(team,0L);long paid=Math.min(due,m.accounts.get(team).cash());
                entry(team,date,"OPERATING|"+date,"NON_WAGE_OPERATING",-paid);debt.put(team,due-paid);m.settleArrears(team,date);
            }
        }
        if(date.getDayOfMonth()==date.lengthOfMonth()&&date.isAfter(through))through=date;
        for(var a:new ArrayList<>(awards.values()))if(a.paidOn()==null&&!date.isBefore(a.dueOn())){
            entry(a.team(),date,"PRIZE|"+a.id(),"TOURNAMENT_PLACEMENT_PRIZE",a.krw());
            awards.put(a.id(),new Award(a.id(),a.seasonYear(),a.competition(),a.eventId(),a.team(),a.awardType(),a.placementFrom(),a.placementThrough(),a.originalCurrency(),a.originalAmount(),a.evidenceStatus(),a.allocationPolicy(),a.fxPolicy(),a.rate(),a.krw(),a.recognizedOn(),a.dueOn(),date,a.resultHash(),a.referenceHash()));m.settleArrears(a.team(),date);
        }
    }
    void forecast(String team,LocalDate current,LocalDate last,Map<LocalDate,Long> flow){
        for(LocalDate start=through.plusDays(1);!start.isAfter(last);){LocalDate end=start.withDayOfMonth(start.lengthOfMonth());var a=approval(team,end);if(recurring(team,end)){
            LocalDate from=start.isBefore(a.effectiveOn())?a.effectiveOn():start;LocalDate due=end.isBefore(current)?current:end;
            flow.merge(due,wages(a.annualSupport(),from,end)+wages(a.annualSponsor(),from,end)-wages(a.annualNonWage(),from,end),Math::addExact);
        }else if(end.getMonth()==Month.JANUARY&&end.getYear()>current.getYear())flow.merge(LocalDate.of(end.getYear(),1,1),a.annualIncome(),Math::addExact);start=end.plusDays(1);}
        // Receivables remain outside spendable money until their actual posting date.
    }
    record Rank(int from,int through){}
    void recognize(int year,String competition,Map<String,Rank> classification,LocalDate completed,String resultHash){
        String instance=year+"|"+competition;if(basis.excludedInstances().contains(instance))return;
        String event=EVENT_IDS.get(competition);if(event==null)return;var rule=basis.prizeRules().get(event);
        if(awards.values().stream().anyMatch(a->a.seasonYear()==year&&a.competition().equals(competition)&&(!a.resultHash().equals(resultHash)||!a.referenceHash().equals(basis.sourceHash()))))throw new IllegalStateException("PRIZE_RESULT_CONFLICT");
        if(classification.isEmpty()){held.put(instance,"PRIZE_CLASSIFICATION_INCOMPLETE");return;}
        var planned=new ArrayList<Award>();
        try{for(var e:classification.entrySet()){
            String team=e.getKey().contains(":")?e.getKey():"LCK:"+e.getKey();if(!m.accounts.containsKey(team))throw new IllegalArgumentException("PRIZE_TEAM_ID");
            long amount=placement(rule,e.getValue().from(),e.getValue().through(),competition.equals("LCK_CL"));String rate=basis.fx().get(rule.currency());
            String id=CareerMarketEngine.id(m.career,"AWARD|"+instance+"|"+team+"|FINAL_PLACEMENT");
            var next=new Award(id,year,competition,event,team,"FINAL_PLACEMENT",e.getValue().from(),e.getValue().through(),rule.currency(),amount,rule.evidenceStatus(),competition.equals("LCK_CL")&&e.getValue().from()==3?"GAME_SHARED_PLACEMENT_POOL":"GAME_CLUB_RETENTION_100_PERCENT",basis.fxPolicyVersion(),rate,convert(amount,rate),completed,due(competition,completed),null,resultHash,basis.sourceHash());
            var old=awards.get(id);if(old!=null&&(!old.resultHash().equals(resultHash)||old.originalAmount()!=amount||old.placementFrom()!=next.placementFrom()||old.placementThrough()!=next.placementThrough()))throw new IllegalStateException("PRIZE_RESULT_CONFLICT");
            if(old==null)planned.add(next);
        }}catch(IllegalArgumentException incomplete){held.put(instance,incomplete.getMessage());return;}
        planned.forEach(a->awards.put(a.id(),a));held.remove(instance);
    }
    void initializeTargets(int year,LocalDate date,boolean partial){
        var lck=m.accounts.keySet().stream().filter(t->t.startsWith("LCK:")).sorted(Comparator.comparingInt((String t)->m.lineups.get(t).stream().mapToInt(id->CareerMarketPolicy.strength(m.player(id))).sum()).reversed().thenComparing(t->t)).toList();
        var budgetOrder=lck.stream().sorted(Comparator.comparingLong((String t)->m.accounts.get(t).annualBudget()).reversed().thenComparing(t->t)).toList();
        for(String team:m.accounts.keySet()){
            String key=team+"|"+year;if(targets.containsKey(key))continue;var a=approval(team,date);int rank=team.startsWith("LCK:")?(lck.indexOf(team)+budgetOrder.indexOf(team))/2+1:0;
            int required=rank==0||partial?0:rank<=TOP_TARGET?TOP_TARGET:rank<=MID_TARGET?MID_TARGET:LOW_TARGET;
            targets.put(key,new Target(team,year,date,partial,required,required==TOP_TARGET?WORLDS_TARGET:null,wages(a.annualSponsor(),date,LocalDate.of(year,12,31)),a.wageLimit(),"PENDING","PENDING",null,null,0,0,0,0,null,partial?"도입 이후 재정만 평가 · 경기/완전 시즌 보너스 중립":"현재 전력과 승인 예산의 순위 구간으로 고정"));
        }
    }
    void close(int year,LocalDate date,Map<String,Integer> domestic,Map<String,Integer> worlds){
        for(String team:m.accounts.keySet()){
            var t=targets.get(team+"|"+year);if(t==null||t.evaluatedOn()!=null)continue;
            Integer rank=domestic.get(team),world=worlds.get(team);String sport="NOT_EVALUABLE";
            if(!t.partial()&&t.maximumDomesticRank()>0&&rank!=null){boolean met=rank<=t.maximumDomesticRank()&&(t.worldsMaximumRank()==null||world!=null&&world<=t.worldsMaximumRank());sport=met?(rank==1||rank<t.maximumDomesticRank()&&(t.worldsMaximumRank()==null||world<=WORLDS_EXCEEDED)?"EXCEEDED":"MET"):"MISSED";}
            long arrears=m.salaryArrears(team)+debt.getOrDefault(team,0L),headroom=m.paymentHeadroom(team,date);boolean newCommitment=m.contracts.values().stream().anyMatch(c->team.equals(c.team())&&!c.signedDate().isBefore(t.setOn())&&Set.of("NEGOTIATED_RENEWAL","NEGOTIATED_FREE_AGENT","PAID_TRANSFER_AGREEMENT").contains(c.origin()));
            String financial=arrears==0&&headroom>=0&&(!newCommitment||!wageLimitBreached(team,date))?"MET":"MISSED";
            long bonus=t.partial()?0:pct(t.fixedSponsor(),sport.equals("EXCEEDED")?TARGET_BONUS_EXCEEDED:sport.equals("MET")?TARGET_BONUS_MET:0);
            entry(team,date,"TARGET_BONUS|"+year,"GAME_SPONSOR_PERFORMANCE_BONUS",bonus);
            targets.put(team+"|"+year,new Target(team,year,t.setOn(),t.partial(),t.maximumDomesticRank(),t.worldsMaximumRank(),t.fixedSponsor(),t.initialWageLimit(),sport,financial,rank,world,m.accounts.get(team).cash(),headroom,arrears,bonus,date,"봉인 최종 순위·현재 현금·확정 의무·체불"));
            var base=basis.teams().get(team);long original=base.playerCompensation()+base.nonWage();var prior=approval(team,date);
            String outcome=financial.equals("MISSED")?"MISSED":sport;long proposed=pct(FUNDING.equals(prior.policy())?prior.annualIncome():original,outcome.equals("MISSED")?100-FUNDING_CHANGE:outcome.equals("EXCEEDED")?100+FUNDING_CHANGE:100);
            long income=Math.max(pct(original,SUPPORT_FLOOR),Math.min(pct(original,SUPPORT_CEILING),proposed));
            long reserve=ratio(base.playerCompensation()+base.nonWage(),RESERVE_MONTHS,12),free=Math.max(0,m.accounts.get(team).cash()-m.reservedCash(team)-arrears-reserve);
            long cap=Math.max(0,income-base.nonWage())+free/CareerMarketPolicy.MAX_YEARS; // One-off free cash amortized across the longest permitted contract.
            LocalDate effective=LocalDate.of(year+1,1,1);long committed=m.peakSalaryFrom(team,effective);
            approvals.put(team+"|"+(year+1),new Approval(team,year+1,effective,income,pct(income,SUPPORT_PERCENT),income-pct(income,SUPPORT_PERCENT),base.nonWage(),cap,committed,Math.max(0,committed-cap),outcome,FUNDING));
            if(recurringEffective==null)recurringEffective=effective;
        }
    }
}
