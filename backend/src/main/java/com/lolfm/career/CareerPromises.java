package com.lolfm.career;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.lolfm.career.CareerManagementPolicy.*;
import static com.lolfm.career.CareerManagementState.*;
import static com.lolfm.career.CareerMarketState.*;

/** Agreement-bound evaluation, deliberately isolated from player ratings and match execution. */
final class CareerPromises {
    private final CareerMarketEngine market;
    final LocalDate observationStarted;
    final Map<String,Promise> promises=new TreeMap<>();
    final Map<String,Appearance> appearances=new TreeMap<>();
    CareerPromises(CareerMarketEngine market,CareerManagementState saved) {
        this.market=market;observationStarted=saved.observationStarted();promises.putAll(saved.promises());appearances.putAll(saved.appearances());
    }
    void initialize(LocalDate date) {
        for(var c:market.contracts.values())if(c.team()!=null&&c.status()==ContractStatus.ACTIVE&&market.loan(c.playerId(),date)==null)
            ensure(c,c.team(),null,c.terms().role(),date,c.terms().endDate());
    }
    Promise current(String player,String team,LocalDate date) {
        return promises.values().stream().filter(p->p.playerId().equals(player)&&p.team().equals(team)&&!date.isBefore(p.startDate())&&!date.isAfter(p.endDate()))
                .max(Comparator.comparing(Promise::startDate).thenComparing(Promise::promiseId)).orElse(null);
    }
    Promise latest(String player,String team) {
        return promises.values().stream().filter(p->p.playerId().equals(player)&&p.team().equals(team))
                .max(Comparator.comparing(Promise::startDate).thenComparing(Promise::promiseId)).orElse(null);
    }
    LocalDate continuousSince(String player,String team,LocalDate date) {
        var current=current(player,team,date);if(current==null)return date;
        LocalDate since=current.startDate();
        for(var p:promises.values().stream().filter(p->p.playerId().equals(player)&&p.team().equals(team))
                .sorted(Comparator.comparing(Promise::startDate).reversed()).toList())
            if(!p.startDate().isAfter(since)&&!p.endDate().plusDays(1).isBefore(since))since=p.startDate();
        return since;
    }
    void ensure(Contract c,String team,String loan,Role role,LocalDate start,LocalDate end) {
        if(current(c.playerId(),team,start)!=null)return;
        String id=CareerMarketEngine.id(c.careerId(),"PROMISE|"+c.contractId()+'|'+team+'|'+start);
        Promise prior=latest(c.playerId(),team);int trust=prior==null?INITIAL_TRUST:prior.trust();
        promises.put(id,new Promise(id,c.playerId(),team,c.contractId(),loan,role,start,end,start,start,0,0,0,
                INITIAL_SATISFACTION,trust,"OBSERVATION_PENDING","새 약속 관찰 시작 · 과거 출전 부족을 소급 판정하지 않음",VERSION,0));
    }
    void close(String player,String team,LocalDate end) {
        var p=current(player,team,end.plusDays(1));if(p==null)return;
        promises.put(p.promiseId(),new Promise(p.promiseId(),player,team,p.contractId(),p.loanId(),p.role(),p.startDate(),end,
                p.observationStart(),p.lastEvaluation(),p.opportunities(),p.starts(),p.sets(),p.satisfaction(),p.trust(),p.status(),p.reason(),p.policyVersion(),p.evaluatedOpportunities()));
    }
    void apply(Appearance appearance) {
        var prior=appearances.putIfAbsent(appearance.completionId(),appearance);
        if(prior!=null) {if(!prior.equals(appearance))throw new IllegalStateException("APPEARANCE_COMPLETION_CONFLICT");return;}
        for(var fact:appearance.opportunities()) {
            Promise p=promises.get(fact.promiseId());if(p==null||!"FIRST_TEAM".equals(appearance.squad())||p.role()==Role.DEVELOPMENT)continue;
            promises.put(p.promiseId(),new Promise(p.promiseId(),p.playerId(),p.team(),p.contractId(),p.loanId(),p.role(),p.startDate(),p.endDate(),
                    p.observationStart(),p.lastEvaluation(),p.opportunities()+(fact.eligible()?1:0),p.starts()+(fact.eligible()&&fact.selected()?1:0),
                    p.sets()+(fact.selected()?appearance.completedSets():0),p.satisfaction(),p.trust(),p.status(),p.reason(),p.policyVersion(),p.evaluatedOpportunities()));
        }
    }
    void evaluate(LocalDate date) {
        for(var p:new ArrayList<>(promises.values())) {
            if(date.isBefore(p.startDate())||date.isAfter(p.endDate())||date.isBefore(p.lastEvaluation().plusDays(EVALUATION_DAYS)))continue;
            String status,reason;int mood=p.satisfaction(),trust=p.trust();
            boolean observed=ChronoUnit.DAYS.between(p.observationStart(),date)>=OBSERVATION_DAYS;
            if(market.playerArrears(p.playerId(),p.team())>0) {
                status="SALARY_ARREARS";reason="확인된 급여 미지급 · 정산 이후 평가일에 점진적 회복";mood-=ARREARS_LOSS;trust-=TRUST_LOSS;
            } else if(!observed) {status="OBSERVATION_PENDING";reason="최소 관찰 기간 28일 미충족";}
            else if(p.role()==Role.DEVELOPMENT) {
                boolean placed="DEVELOPMENT".equals(market.members.get(p.playerId()).squad());
                status=placed?"PLACEMENT_MET_APPEARANCE_UNKNOWN":"PLACEMENT_BREACH";
                reason=placed?"육성 배치 약속 유지 · CL 출전/훈련 근거는 생성하지 않음":"합의한 육성 배치와 현재 배치가 다름";
                if(placed){mood+=RECOVERY;trust+=TRUST_RECOVERY;}else{mood-=BREACH_LOSS;trust-=TRUST_LOSS;}
            } else if(p.opportunities()<MIN_SERIES) {status="INSUFFICIENT_SERIES";reason="평가 가능한 Series 6회 미충족 · 해외/무경기 기간 불이익 없음";}
            else if(p.opportunities()==p.evaluatedOpportunities()) {status="NO_NEW_SERIES";reason="새로 완료된 평가 가능 Series 없음 · 무경기 기간의 출전 약속 평가 유예";}
            else if(p.role()==Role.STARTER&&p.starts()*100L<p.opportunities()*STARTER_PERCENT) {
                status="STARTER_PROMISE_BREACH";reason="주전 약속: 평가 가능 Series 대비 선발 비율 70% 미만";mood-=BREACH_LOSS;trust-=TRUST_LOSS;
            } else {
                status=p.role()==Role.RESERVE?"RESERVE_ROLE_RESPECTED":"PROMISE_MET";
                reason=p.role()==Role.RESERVE?"후보 역할은 최소 선발 비율을 보장하지 않음":"주전 약속 선발 비율 충족";mood+=RECOVERY;trust+=TRUST_RECOVERY;
            }
            if(market.playerArrears(p.playerId(),p.team())==0&&(p.status().equals("SALARY_ARREARS")||p.status().equals("ARREARS_RECOVERY"))
                    &&(status.equals("NO_NEW_SERIES")||status.equals("INSUFFICIENT_SERIES")||status.equals("OBSERVATION_PENDING"))&&mood<INITIAL_SATISFACTION) {
                mood=Math.min(INITIAL_SATISFACTION,mood+RECOVERY);trust+=TRUST_RECOVERY;status="ARREARS_RECOVERY";reason="급여 체불 정산 확인 · 출전 표본과 별도로 점진 회복";
            }
            promises.put(p.promiseId(),new Promise(p.promiseId(),p.playerId(),p.team(),p.contractId(),p.loanId(),p.role(),p.startDate(),p.endDate(),p.observationStart(),date,
                    p.opportunities(),p.starts(),p.sets(),clamp(mood),clamp(trust),status,reason,VERSION,observed&&p.opportunities()>=MIN_SERIES?p.opportunities():p.evaluatedOpportunities()));
        }
    }
    int trust(String player,String team) {var p=latest(player,team);return p==null?INITIAL_TRUST:p.trust();}
    int mood(String player,LocalDate date) {
        var m=market.members.get(player);var p=m==null||m.ownerTeam()==null?null:current(player,m.ownerTeam(),date);
        return p==null?INITIAL_SATISFACTION:p.satisfaction();
    }
    long adjustment(String player,String target,LocalDate date) {
        long trust=(trust(player,target)-INITIAL_TRUST)*TRUST_SCORE_WEIGHT;
        var m=market.members.get(player);int mood=mood(player,date);
        return trust+(m!=null&&target.equals(m.ownerTeam())?(mood-INITIAL_SATISFACTION)*SATISFACTION_SCORE_WEIGHT:
                Math.max(0,INITIAL_SATISFACTION-mood)*SATISFACTION_SCORE_WEIGHT);
    }
}
