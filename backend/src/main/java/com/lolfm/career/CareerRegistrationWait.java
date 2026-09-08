package com.lolfm.career;

import java.util.*;
import com.lolfm.domain.Position;

/** Registration has no fixture yet. This read-only context never changes a roster or submits an offer. */
public record CareerRegistrationWait(String code,String competitionId,String requiredEventId,String teamId,
        String ownerTeam,String responsibility,List<Position> missingPositions,List<String> obstacles) {
    public CareerRegistrationWait {missingPositions=List.copyOf(missingPositions);obstacles=List.copyOf(obstacles);}
    static CareerRegistrationWait result(String competition,String required) {
        return new CareerRegistrationWait("OVERSEAS_RESULT_REQUIRED",competition,required,null,null,"RESULTS",List.of(),List.of());
    }
    static CareerRegistrationWait qualification(String competition) {
        return new CareerRegistrationWait("INTERNATIONAL_QUALIFICATION_REQUIRED",competition,null,null,null,"RESULTS",List.of(),List.of());
    }
    static CareerRegistrationWait roster(CareerCompetitionRelationalStore store,String career,int year,String competition,String team) {
        var m=CareerMarketStore.engine(store.jdbc,career,year,CareerMarketStore.load(store.jdbc,career));
        String owner=team==null?null:CareerOverseasRoster.owner(team);var missing=new ArrayList<Position>();var obstacles=new LinkedHashSet<String>();
        if(team!=null) {
            var ids=CareerOverseasRoster.candidates(m,team);
            for(var role:Position.values())if(ids.stream().noneMatch(id->m.player(id).position()==role&&m.eligible(id,owner,m.state().processedThrough()))) {
                missing.add(role);
                if(m.freeAgents.stream().noneMatch(id->m.player(id).position()==role))obstacles.add("NO_FREE_AGENT_FOR_MISSING_ROLE");
            }
            if(missing.isEmpty())obstacles.add("LINEUP_OR_SELECTION_ELIGIBILITY_REQUIRED");
            var account=m.accounts.get(owner);
            if(account==null)obstacles.add("FINANCE_OWNER_UNAVAILABLE");
            else {
                if(m.salaryArrears(owner)>0||m.finance!=null&&m.finance.debt.getOrDefault(owner,0L)>0)obstacles.add("ARREARS_REQUIRE_SETTLEMENT");
                if(account.cash()<m.reservedCash(owner)||m.paymentHeadroom(owner,m.state().processedThrough())<0)obstacles.add("CASH_HEADROOM_SHORTFALL");
                if(account.annualBudget()<=m.peakSalary(owner))obstacles.add("PAYROLL_HEADROOM_EXHAUSTED");
            }
        }
        return new CareerRegistrationWait("ROSTER_REPAIR_REQUIRED",competition,null,team,owner,
                owner==null?"ROSTER_REVIEW":owner.equals(m.managed)?"MANAGER":"AI_CLUB",missing,List.copyOf(obstacles));
    }
}
