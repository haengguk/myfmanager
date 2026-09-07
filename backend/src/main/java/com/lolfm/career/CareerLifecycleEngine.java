package com.lolfm.career;

import java.time.LocalDate;
import java.util.*;
import static com.lolfm.career.CareerLifecycleState.*;
import static com.lolfm.career.CareerMarketState.*;
import static com.lolfm.career.CareerManagementState.*;

/** A single locked market operation's lifecycle state. */
final class CareerLifecycleEngine {
    final LocalDate appliedOn;Integer lastReview;boolean clEnabled;
    final Map<String,Person> people=new TreeMap<>();
    CareerLifecycleEngine(CareerLifecycleState state){appliedOn=state.appliedOn();lastReview=state.lastReviewedSeason();people.putAll(state.players());}
    CareerLifecycleState state(){return new CareerLifecycleState(CareerLifecyclePolicy.VERSION,appliedOn,lastReview,people);}
    boolean retired(String id){var p=people.get(id);return p!=null&&p.status()==Status.RETIRED;}
    boolean announced(String id){var p=people.get(id);return p!=null&&(p.status()==Status.RETIREMENT_ANNOUNCED||p.status()==Status.RETIRED);}
    boolean permitsContract(String id,Terms terms){var p=people.get(id);return p==null||p.status()!=Status.RETIRED&&(p.effectiveOn()==null||terms.endDate().isBefore(p.effectiveOn()));}
    void observe(CareerMarketEngine m,LocalDate date) {
        for(var entry:new ArrayList<>(people.entrySet())) {
            observePlacement(entry.getKey(),m.members.get(entry.getKey()),date);var p=people.get(entry.getKey());var definition=m.directory.players().get(entry.getKey());if(definition==null)throw new IllegalStateException("LIFECYCLE_PLAYER_REFERENCE");
            p=p.peak(com.lolfm.player.PlayerAbilityPolicy.currentAbility(definition.gameplay().ratings()));
            boolean fa=m.freeAgents.contains(entry.getKey())&&!retired(entry.getKey());
            if(!fa&&p.freeAgentSince()!=null)p=p.freeAgent(null);else if(fa&&p.freeAgentSince()==null)p=p.freeAgent(date);
            people.put(entry.getKey(),p);
        }
    }
    void observePlacement(String id,CareerRosterStore.Membership member,LocalDate date) {
        var person=people.get(id);if(person==null)return;
        boolean observed=member!=null&&member.ownerTeam()!=null&&"LCK".equals(CareerMarketPolicy.region(member.ownerTeam()))&&("FIRST_TEAM".equals(member.squad())||clEnabled&&"DEVELOPMENT".equals(member.squad()))&&member.eligibilityReason()==null&&!retired(id);
        if(!observed&&person.domesticObservedSince()!=null)people.put(id,person.domesticSince(null));
        else if(observed&&(person.domesticObservedSince()==null||!member.squad().equals(person.observedSquad())))people.put(id,person.domesticSince(date,member.squad()));
    }
    void cancelReservations(CareerMarketEngine m,String id,LocalDate date) {
        var person=people.get(id);
        for(var o:new ArrayList<>(m.offers.values()))if(o.playerId().equals(id)&&o.open()&&!o.terms().endDate().isBefore(person.effectiveOn()))m.offers.put(o.offerId(),m.offerStatus(o,OfferStatus.REJECTED,"은퇴 효력일과 충돌하여 제안 예약 해제",null));
        for(var c:new ArrayList<>(m.contracts.values()))if(c.playerId().equals(id)&&c.status()==ContractStatus.SCHEDULED&&!c.terms().endDate().isBefore(person.effectiveOn())) {
            m.contracts.put(c.contractId(),m.contractStatus(c,ContractStatus.CANCELLED_RETIREMENT,date,c.paidThrough()));
            // Signing bonuses are already paid and non-refundable; future salary reservations disappear with status.
            m.event(date,"RETIREMENT_FUTURE_CONTRACT_CANCELLED",id,c.team(),c.contractId(),"은퇴와 충돌하는 미래 계약 취소 · 미지급 급여 예약 해제 · 지급 계약금 환수 없음");
        }
        m.tradeEngine.cancelForRetirement(id,date);
    }
    void effective(CareerMarketEngine m,LocalDate date) {
        for(var entry:new ArrayList<>(people.entrySet())) {
            String id=entry.getKey();var p=entry.getValue();if(p.status()!=Status.RETIREMENT_ANNOUNCED||date.isBefore(p.effectiveOn()))continue;
            cancelReservations(m,id,date);LocalDate last=p.effectiveOn().minusDays(1);
            for(var c:new ArrayList<>(m.contracts.values()))if(c.playerId().equals(id)&&c.status()==ContractStatus.ACTIVE) {
                m.payThrough(c,date,last);c=m.contracts.get(c.contractId());m.contracts.put(c.contractId(),m.contractStatus(c,ContractStatus.RETIRED,last,c.paidThrough()));
                m.depart(c,date,"CONTRACT_RETIRED");
            }
            for(var l:new ArrayList<>(m.tradeEngine.loans.values()))if(l.playerId().equals(id)&&"ACTIVE".equals(l.status())) {
                m.promiseEngine.close(id,l.borrowingTeam(),last);
                m.tradeEngine.loans.put(l.loanId(),new Loan(l.loanId(),l.tradeId(),l.contractId(),id,l.parentTeam(),l.borrowingTeam(),l.startDate(),last,l.fee(),l.borrowerSalaryPercent(),l.role(),l.returnOrganization(),l.returnSquad(),"RETIRED",l.policyVersion()));
            }
            m.lineups.values().forEach(ids->ids.remove(id));m.freeAgents.remove(id);
            m.members.put(id,new CareerRosterStore.Membership(id,null,null,"UNAFFILIATED","RETIRED"));people.put(id,p.retired());
            if(m.development!=null)m.development.retired.add(id);
            m.event(date,"RETIREMENT_EFFECTIVE",id,null,id+"|"+p.effectiveOn(),"은퇴 효력 · 마지막 급여 포함일 "+last+" · 기존 경기·계약·선수 기록 보존");
        }
    }
}
