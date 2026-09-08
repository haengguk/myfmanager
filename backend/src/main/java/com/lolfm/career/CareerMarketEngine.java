package com.lolfm.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.lolfm.domain.Position;
import com.lolfm.player.ExpandedPlayerCatalog.Definition;
import java.time.*;
import java.util.*;
import static com.lolfm.career.CareerMarketState.*;
import static com.lolfm.career.CareerMarketPolicy.*;

/** A short-lived, Career-scoped transaction workspace. No match Random or shared mutable players. */
public final class CareerMarketEngine {
    final String career, managed;
    final long seed;
    CareerRosterStore.Directory directory;
    CareerDevelopmentEngine development;
    CareerLifecycleEngine lifecycle;
    Map<String,List<LocalDate>> developmentFixtures=Map.of();
    int developmentYear;
    boolean clEnabled;
    final Map<String,List<String>> clLineups=new TreeMap<>();
    final List<CareerSquadPlanner.Restriction> squadRestrictions=new ArrayList<>();
    final Map<String,Set<String>> internationalPools=new TreeMap<>();
    CareerSquadPlanner planner;
    CareerFinanceEngine finance;
    private LocalDate processed;
    final Map<String,Contract> contracts=new TreeMap<>();
    final Map<String,Offer> offers=new TreeMap<>();
    final Map<String,Account> accounts=new TreeMap<>();
    final Map<String,Preference> preferences=new TreeMap<>();
    final Set<String> freeAgents=new TreeSet<>();
    final List<Ledger> ledger=new ArrayList<>();
    final Map<String,Decision> decisions=new TreeMap<>();
    final List<Event> events=new ArrayList<>();
    final Map<String,CareerRosterStore.Membership> members=new TreeMap<>();
    final Map<String,List<String>> lineups=new TreeMap<>();

    final CareerPromises promiseEngine;
    final CareerTrades tradeEngine;
    public CareerManagementState management() {return new CareerManagementState(CareerManagementPolicy.VERSION,promiseEngine.observationStarted,
            promiseEngine.promises,tradeEngine.trades,tradeEngine.loans,promiseEngine.appearances);}
    public CareerManagementState.Loan loan(String player,LocalDate date) {return tradeEngine==null?null:tradeEngine.loan(player,date);}
    public CareerManagementState.Promise promise(String player,String team,LocalDate date) {return promiseEngine.current(player,team,date);}
    public void applyAppearance(CareerManagementState.Appearance appearance) {promiseEngine.apply(appearance);}
    public CareerMarketEngine(String career,String managed,CareerRosterStore.Directory directory,
            CareerRosterStore.State roster,CareerMarketState state) {
        this.career=career;this.managed=managed;this.directory=directory;seed=state.seed();processed=state.processedThrough();
        contracts.putAll(state.contracts());offers.putAll(state.offers());accounts.putAll(state.accounts());
        preferences.putAll(state.preferences());freeAgents.addAll(state.freeAgents());ledger.addAll(state.ledger());
        decisions.putAll(state.decisions());events.addAll(state.events());members.putAll(roster.members());
        roster.lineups().forEach((team,ids)->lineups.put(team,new ArrayList<>(ids)));
        var management=state.management()==null?CareerManagementState.empty(processed):state.management();
        promiseEngine=new CareerPromises(this,management);tradeEngine=new CareerTrades(this,management);
        if(state.management()==null)promiseEngine.initialize(processed);
        planner=new CareerSquadPlanner(this,state.squadPlanning());
        if(state.finance()!=null)finance=new CareerFinanceEngine(this,state.finance());
    }
    public static CareerMarketState initialize(String career,long seed,LocalDate date,int firstYear,
            CareerRosterStore.Directory directory,CareerRosterStore.State roster) {
        Map<String,Contract> contracts=new TreeMap<>();Map<String,Preference> preferences=new TreeMap<>();
        Set<String> free=new TreeSet<>();Map<String,Account> accounts=new TreeMap<>();List<Ledger> ledger=new ArrayList<>();
        for(var player:directory.players().values().stream().sorted(Comparator.comparing(Definition::playerId)).toList()) {
            preferences.put(player.playerId(),preference(seed,player));
            var member=roster.members().get(player.playerId());
            if("UNAFFILIATED".equals(member.squad())) {if(INITIAL_GAME_FREE_AGENTS.contains(player.playerId()))free.add(player.playerId());continue;}
            if(member.organizationId()==null || "UNCONFIRMED".equals(member.squad()))continue;
            LocalDate end=LocalDate.of(date.getYear(),11,30);String origin="GAME_INITIAL_CONTRACT_UNKNOWN_PUBLIC_TERMS";
            try {
                JsonNode details=CareerRosterStore.read(player.detailsJson(),JsonNode.class);
                String reported=details.path("contract").path("endDate").asText("");
                String status=details.path("contract").path("status").asText("");
                if(!reported.isBlank() && !status.contains("CONFLICT") && !status.contains("UNVERIFIED") && !status.contains("UNCONFIRMED")
                        && Objects.equals(member.organizationId(),player.initialOrganizationId())) {
                    end=LocalDate.parse(reported).plusYears(firstYear-REFERENCE_YEAR);
                    origin="PUBLIC_END_DATE_SHIFTED_FROM_2026_GAME_POLICY";
                }
            }catch(RuntimeException unknown){ /* Unusable research remains untouched in the directory. */ }
            if(end.isBefore(date.plusDays(PROTECTION_DAYS))) {
                end=LocalDate.of(date.getYear(),11,30);
                if(end.isBefore(date.plusDays(PROTECTION_DAYS)))end=end.plusYears(1);
                origin="EXISTING_SAVE_PROTECTION_GAME_CONTRACT";
            }
            String id=id(career,"INITIAL|"+player.playerId());
            Role role=roster.lineups().values().stream().anyMatch(l->l.contains(player.playerId()))?Role.STARTER:
                    "DEVELOPMENT".equals(member.squad())?Role.DEVELOPMENT:Role.RESERVE;
            contracts.put(id,new Contract(id,career,player.playerId(),member.ownerTeam(),member.organizationId(),date,
                    new Terms(date,end,CareerMarketPolicy.demand(player),0,role),ContractStatus.ACTIVE,0,VERSION,origin,
                    "REMAINING_SALARY_25_PERCENT_V1",null,date.minusDays(1)));
        }
        for(String team:roster.lineups().keySet().stream().sorted().toList()) {
            long salary=contracts.values().stream().filter(c->team.equals(c.team())).mapToLong(c->c.terms().annualSalary()).sum();
            long budget=Math.max(MIN_BUDGET,salary*BUDGET_PERCENT/100);
            int count=(int)roster.members().values().stream().filter(m->team.equals(m.ownerTeam())).count();
            accounts.put(team,new Account(team,budget,budget*INITIAL_CASH_YEARS,Math.max(MIN_ROSTER_LIMIT,count+2)));
            ledger.add(new Ledger(id(career,"INITIAL_BUDGET|"+team),date,team,null,"INITIAL_ALLOCATION",budget*INITIAL_CASH_YEARS));
        }
        return new CareerMarketState(VERSION,seed,date,contracts,Map.of(),accounts,preferences,free,ledger,Map.of(),List.of());
    }
    public CareerMarketState state() {return new CareerMarketState(VERSION,seed,processed,contracts,offers,accounts,preferences,freeAgents,ledger,decisions,events,management(),planner.state(),finance==null?null:finance.state());}
    public CareerRosterStore.State roster() {return new CareerRosterStore.State(CareerRosterStore.OPERATING_POLICY,members,lineups);}
    public static String id(String career,String event) {return "market_"+CareerRosterStore.hash(career+'|'+VERSION+'|'+event);}
    long demand(String id) {return finance==null?CareerMarketPolicy.demand(player(id)):finance.demand(id);}
    Definition player(String id) {var p=directory.players().get(id);if(p==null)throw invalid("선수 정보를 찾을 수 없습니다.");return p;}
    static CareerException invalid(String reason) {return CareerException.invalid("market",reason);}
    public Contract active(String player,LocalDate date) {return contracts.values().stream().filter(c->c.playerId().equals(player)
            && c.status()==ContractStatus.ACTIVE && !date.isBefore(c.terms().startDate()) && !date.isAfter(c.terms().endDate())).findFirst().orElse(null);}
    public Contract scheduled(String player) {return contracts.values().stream().filter(c->c.playerId().equals(player)&&c.status()==ContractStatus.SCHEDULED).findFirst().orElse(null);}
    public boolean eligible(String player,String team,LocalDate date) {
        if(lifecycle!=null&&lifecycle.retired(player))return false;
        var c=active(player,date);var m=members.get(player);
        return c!=null && (loan(player,date)==null?team.equals(c.team()):team.equals(loan(player,date).borrowingTeam())) && m!=null && team.equals(m.ownerTeam()) && m.eligibilityReason()==null;
    }
    public LocalDate decisionDate(String player,LocalDate date) {
        return offers.values().stream().filter(o->o.playerId().equals(player)&&o.open()&&!o.decisionDate().isBefore(date))
                .map(Offer::decisionDate).min(LocalDate::compareTo).orElse(date.plusDays(DECISION_DAYS));
    }
    public LocalDate availableStart(String player,LocalDate date) {
        if(lifecycle!=null&&lifecycle.retired(player))return null;
        var c=active(player,date);if(loan(player,date)!=null||tradeEngine.hasAgreement(player)||scheduled(player)!=null||"V4_REGISTERED_ROLE_REVIEW_REQUIRED".equals(player(player).eligibilityReason()))return null;
        if(c!=null) {
            if(c.team()==null||c.terms().endDate().isAfter(date.plusDays(NEGOTIATION_DAYS)))return null;
            LocalDate end=c.terms().endDate().plusDays(1),decision=decisionDate(player,date);
            return end.isAfter(decision)?end:decision;
        }
        return freeAgents.contains(player)?decisionDate(player,date):null;
    }
    public long reservedCash(String team) {return Math.addExact(tradeEngine.reserved(team),offers.values().stream().filter(o->o.team().equals(team)&&o.open()).mapToLong(o->o.terms().signingBonus()).sum());}
    public long salaryAt(String team,LocalDate date,boolean includeOffers) {
        long sum=0;
        for(var c:contracts.values())if((c.status()==ContractStatus.ACTIVE||c.status()==ContractStatus.SCHEDULED)&&!date.isBefore(c.terms().startDate())&&!date.isAfter(c.terms().endDate())) {
            var l=tradeEngine.loans.values().stream().filter(v->v.contractId().equals(c.contractId())&&!date.isBefore(v.startDate())&&!date.isAfter(v.endDate())).findFirst().orElse(null);
            long borrower=l==null?0:Math.multiplyExact(c.terms().annualSalary(),l.borrowerSalaryPercent())/100;
            if(team.equals(c.team()))sum=Math.addExact(sum,c.terms().annualSalary()-borrower);
            if(l!=null&&team.equals(l.borrowingTeam()))sum=Math.addExact(sum,borrower);
        }
        if(includeOffers) {
            sum+=offers.values().stream().filter(o->team.equals(o.team())&&o.open()&&!date.isBefore(o.terms().startDate())&&!date.isAfter(o.terms().endDate())).mapToLong(o->o.terms().annualSalary()).sum();
            for(var t:tradeEngine.obligations(team))if(!date.isBefore(t.terms().startDate())&&!date.isAfter(t.terms().endDate()))sum=Math.addExact(sum,tradeSalary(t));
        }
        return sum;
    }
    public long peakSalary(String team) {return peakSalaryFrom(team,processed);}
    long peakSalaryFrom(String team,LocalDate from) {
        TreeSet<LocalDate> dates=new TreeSet<>(List.of(from));
        contracts.values().stream().filter(c->team.equals(c.team())&&(c.status()==ContractStatus.ACTIVE||c.status()==ContractStatus.SCHEDULED)).forEach(c->dates.add(c.terms().startDate()));
        offers.values().stream().filter(o->team.equals(o.team())&&o.open()).forEach(o->dates.add(o.terms().startDate()));
        for(var l:tradeEngine.loans.values()){dates.add(l.startDate());dates.add(l.endDate().plusDays(1));}
        for(var t:tradeEngine.obligations(team))dates.add(t.terms().startDate());
        return dates.stream().filter(d->!d.isBefore(from)).mapToLong(d->salaryAt(team,d,true)).max().orElse(0);
    }
    void requireBudget(String team,LocalDate date) {
        var a=accounts.get(team);if(a==null)throw invalid("시장에 참여할 수 없는 조직입니다.");
        if(salaryArrears(team)>0||finance!=null&&finance.debt.getOrDefault(team,0L)>0)throw invalid("미지급 급여를 먼저 정산해야 추가 계약 지출을 할 수 있습니다. 다음 확정 연간 예산에서 우선 정산합니다.");
        if(a.cash()<reservedCash(team))throw invalid("계약금 예약에 필요한 현금이 부족합니다.");
        if(finance==null?a.annualBudget()<peakSalary(team):finance.wageLimitBreached(team,date))throw invalid("계약 기간의 연봉 예산이 부족합니다.");
        if(paymentHeadroom(team,date)<0)throw invalid("계약금 지급 후 확정 급여를 지급할 재원이 부족합니다. 계약금을 줄이거나 진행 중 제안을 정리해 주세요.");
    }
    /** Bonus reservations reduce spendable cash once. Salary is forecast at actual payment boundaries,
     * including accrued unpaid periods, scheduled employment and confirmed annual allocations. */
    public long paymentHeadroom(String team,LocalDate date) {return paymentHeadroom(team,date,null,null);}
    private long paymentHeadroom(String team,LocalDate date,String omittedContract,String omittedPlayerOffers) {
        var flow=new TreeMap<LocalDate,Long>();long cash=accounts.get(team).cash()-salaryArrears(team)-(finance==null?0:finance.debt.getOrDefault(team,0L));
        for(var c:contracts.values())if((team.equals(c.team())||tradeEngine.loans.values().stream().anyMatch(l->l.contractId().equals(c.contractId())&&team.equals(l.borrowingTeam())))&&(c.status()==ContractStatus.ACTIVE||c.status()==ContractStatus.SCHEDULED)&&!c.contractId().equals(omittedContract))
            forecastContract(flow,c,team,recognizedThrough(c).plusDays(1),date);
        for(var o:offers.values())if(team.equals(o.team())&&o.open()&&!o.playerId().equals(omittedPlayerOffers)) {
            cash-=o.terms().signingBonus();forecastWages(flow,o.terms(),o.terms().startDate(),date);
        }
        cash-=tradeEngine.reserved(team);
        for(var t:tradeEngine.obligations(team)) {
            var terms=t.terms();forecastWages(flow,new Terms(terms.startDate(),terms.endDate(),tradeSalary(t),0,terms.playerTerms().role()),terms.startDate(),date);
        }
        LocalDate last=flow.isEmpty()?date:flow.lastKey();
        if(finance!=null)finance.forecast(team,date,last,flow);
        else for(LocalDate allocation=LocalDate.of(date.getYear()+1,1,1);!allocation.isAfter(last);allocation=allocation.plusYears(1))
            flow.merge(allocation,accounts.get(team).annualBudget(),Long::sum);
        long minimum=cash;
        for(long change:flow.values()){cash=Math.addExact(cash,change);minimum=Math.min(minimum,cash);}
        return minimum;
    }
    private static void forecastWages(Map<LocalDate,Long> flow,Terms terms,LocalDate from,LocalDate current) {
        if(from.isBefore(terms.startDate()))from=terms.startDate();
        while(!from.isAfter(terms.endDate())) {
            LocalDate monthEnd=from.withDayOfMonth(from.lengthOfMonth());
            LocalDate end=monthEnd.isBefore(terms.endDate())?monthEnd:terms.endDate();
            LocalDate due=end.equals(monthEnd)?end:end.plusDays(1);
            if(due.isBefore(current))due=current;
            flow.merge(due,-wages(terms.annualSalary(),from,end),Long::sum);from=end.plusDays(1);
        }
    }
    private long tradeSalary(CareerManagementState.Trade t) {return t.terms().kind()==CareerManagementState.Kind.TRANSFER?t.terms().playerTerms().annualSalary():
            (Math.multiplyExact(t.terms().playerTerms().annualSalary(),t.terms().borrowerSalaryPercent())+99)/100;}
    private void forecastContract(Map<LocalDate,Long> flow,Contract c,String team,LocalDate from,LocalDate current) {
        if(from.isBefore(c.terms().startDate()))from=c.terms().startDate();
        while(!from.isAfter(c.terms().endDate())) {
            LocalDate monthEnd=from.withDayOfMonth(from.lengthOfMonth()),end=monthEnd.isBefore(c.terms().endDate())?monthEnd:c.terms().endDate();
            // Loan boundaries settle the accrued partial period before changing the payer split.
            for(var l:tradeEngine.loans.values())if(l.contractId().equals(c.contractId())) {
                for(var edge:List.of(l.startDate().minusDays(1),l.endDate()))if(!edge.isBefore(from)&&edge.isBefore(end))end=edge;
            }
            LocalDate due=end.equals(monthEnd)?end:end.plusDays(1);if(due.isBefore(current))due=current;
            long amount=salaryShares(c,from,end).getOrDefault(team,0L);if(amount!=0)flow.merge(due,-amount,Math::addExact);from=end.plusDays(1);
        }
    }
    Map<String,Long> salaryShares(Contract c,LocalDate from,LocalDate through) {
        var amounts=new TreeMap<String,Long>();if(c.team()==null||through.isBefore(from))return amounts;
        long parent=wages(c.terms().annualSalary(),from,through);
        for(var l:tradeEngine.loans.values())if(l.contractId().equals(c.contractId())) {
            LocalDate a=from.isAfter(l.startDate())?from:l.startDate(),b=through.isBefore(l.endDate())?through:l.endDate();if(b.isBefore(a))continue;
            long share=0;
            for(int year=a.getYear();year<=b.getYear();year++) {
                LocalDate origin=LocalDate.of(year,1,1),x=a.isAfter(origin)?a:origin,last=LocalDate.of(year,12,31),y=b.isBefore(last)?b:last;
                long numerator=Math.multiplyExact(c.terms().annualSalary(),l.borrowerSalaryPercent()),denominator=100L*origin.lengthOfYear();
                share+=numerator*(java.time.temporal.ChronoUnit.DAYS.between(origin,y)+1)/denominator-numerator*java.time.temporal.ChronoUnit.DAYS.between(origin,x)/denominator;
            }
            parent-=share;amounts.merge(l.borrowingTeam(),share,Math::addExact);
        }
        amounts.merge(c.team(),parent,Math::addExact);return amounts;
    }
    long playerArrears(String player,String team) {return contracts.values().stream().filter(c->c.playerId().equals(player)).mapToLong(c->ledger.stream().filter(l->team.equals(l.team())&&c.contractId().equals(l.contractId())&&(l.kind().equals("SALARY_ACCRUED")||l.kind().equals("SALARY_ARREARS_PAYMENT"))).mapToLong(Ledger::amount).sum()).sum();}
    public long salaryArrears(String team) {return ledger.stream().filter(l->team.equals(l.team())&&(l.kind().equals("SALARY_ACCRUED")||l.kind().equals("SALARY_ARREARS_PAYMENT"))).mapToLong(Ledger::amount).sum();}
    long contractArrears(String contract) {return ledger.stream().filter(l->contract.equals(l.contractId())&&(l.kind().equals("SALARY_ACCRUED")||l.kind().equals("SALARY_ARREARS_PAYMENT"))).mapToLong(Ledger::amount).sum();}
    LocalDate recognizedThrough(Contract c) {return ledger.stream().filter(l->c.contractId().equals(l.contractId())&&l.kind().equals("SALARY_ACCRUED")).map(Ledger::date).max(LocalDate::compareTo).filter(d->d.isAfter(c.paidThrough())).orElse(c.paidThrough());}
    void settleArrears(String team,LocalDate date) {
        // Oldest recorded salary first, with contract identity as a deterministic tie-break.
        var unpaid=contracts.values().stream().filter(c->ledger.stream().filter(l->team.equals(l.team())&&c.contractId().equals(l.contractId())&&(l.kind().equals("SALARY_ACCRUED")||l.kind().equals("SALARY_ARREARS_PAYMENT"))).mapToLong(Ledger::amount).sum()>0)
                .sorted(Comparator.comparing(Contract::paidThrough).thenComparing(Contract::contractId)).toList();
        for(var c:unpaid) {
            long due=ledger.stream().filter(l->team.equals(l.team())&&c.contractId().equals(l.contractId())&&(l.kind().equals("SALARY_ACCRUED")||l.kind().equals("SALARY_ARREARS_PAYMENT"))).mapToLong(Ledger::amount).sum(),amount=Math.min(due,accounts.get(team).cash());
            if(amount==0)break;
            charge(team,c.contractId(),"SALARY_ARREARS_PAYMENT",date,amount);
            if(contractArrears(c.contractId())==0)contracts.put(c.contractId(),contractStatus(c,c.status(),c.endedDate(),recognizedThrough(c)));
        }
    }
    public Offer submit(String team,String playerId,Terms terms,String previousId,LocalDate date) {
        validate(terms);player(playerId);if(lifecycle!=null&&!lifecycle.permitsContract(playerId,terms))throw invalid("은퇴 효력일과 충돌하는 계약은 제안할 수 없습니다.");LocalDate available=availableStart(playerId,date);
        if(available==null)throw invalid("현재 계약 보호 기간이거나 이미 확정된 미래 계약이 있어 제안할 수 없습니다.");
        var current=active(playerId,date);
        if(current!=null && !terms.startDate().equals(available) || current==null && (terms.startDate().isBefore(available)||terms.startDate().isAfter(available.plusDays(FA_START_DELAY_DAYS))))
            throw invalid("현재 계약 종료 다음 날과 공통 결정일 중 늦은 시작 가능일을 사용해야 합니다.");
        if(!accounts.containsKey(team))throw invalid("경쟁 구단만 제안할 수 있습니다.");
        if(terms.role()==Role.DEVELOPMENT && developmentOrganization(team)==null)throw invalid("연결된 육성팀이 없는 구단입니다.");
        Offer previous=previousId==null?null:offers.get(previousId);int round=1;
        if(previousId!=null) {
            if(previous==null||!previous.open()||!previous.team().equals(team)||!previous.playerId().equals(playerId)||!date.isBefore(previous.decisionDate()))
                throw invalid("수정할 수 있는 원본 제안이 아닙니다.");
            round=previous.round()+1;if(round>MAX_ROUNDS)throw invalid("이번 협상의 수정 횟수를 모두 사용했습니다.");
        } else if(offers.values().stream().anyMatch(o->o.playerId().equals(playerId)&&o.team().equals(team)&&o.open()))throw invalid("진행 중인 제안을 수정하거나 철회해 주세요.");
        // Cooldown belongs to team/player/game-date, never to the request UUID.
        if(previous==null && offers.values().stream().anyMatch(o->o.team().equals(team)&&o.playerId().equals(playerId)
                && !date.isAfter(o.decisionDate())&&!o.open()))throw invalid("이번 결정 기간이 끝난 뒤 다시 제안할 수 있습니다.");
        LocalDate decision=decisionDate(playerId,date);if(!date.isBefore(decision))throw invalid("선수의 결정일에는 새 조건을 제출할 수 없습니다.");
        String offerId=id(career,"OFFER|"+team+'|'+playerId+'|'+decision+'|'+round);
        if(offers.containsKey(offerId))throw invalid("이미 사용한 협상 단계입니다.");
        if(previous!=null)offers.put(previousId,offerStatus(previous,OfferStatus.SUPERSEDED,"수정 제안으로 대체됨",null));
        var offer=new Offer(offerId,playerId,team,terms,date,date.plusDays(RESPONSE_DAYS).isBefore(decision)?date.plusDays(RESPONSE_DAYS):decision,
                decision,decision.plusDays(1),0,OfferStatus.SUBMITTED,previousId,round,null,"선수가 제안을 검토하고 있습니다.");
        offers.put(offerId,offer);
        try {requireBudget(team,date);requireRosterCapacity(team,playerId,terms);}
        catch(RuntimeException rejected){offers.remove(offerId);if(previous!=null)offers.put(previousId,previous);throw rejected;}
        event(date,"OFFER_SUBMITTED",playerId,team,offerId,"실제 지출 예약을 포함한 계약 제안");return offer;
    }
    void requireRosterCapacity(String team,String incoming,Terms incomingTerms) {
        TreeSet<LocalDate> dates=new TreeSet<>(List.of(incomingTerms.startDate()));
        contracts.values().stream().filter(c->team.equals(c.team())&&(c.status()==ContractStatus.ACTIVE||c.status()==ContractStatus.SCHEDULED)&&overlaps(c.terms(),incomingTerms)).forEach(c->dates.add(c.terms().startDate().isBefore(incomingTerms.startDate())?incomingTerms.startDate():c.terms().startDate()));
        offers.values().stream().filter(o->team.equals(o.team())&&o.open()&&overlaps(o.terms(),incomingTerms)).forEach(o->dates.add(o.terms().startDate().isBefore(incomingTerms.startDate())?incomingTerms.startDate():o.terms().startDate()));
        for(var l:tradeEngine.loans.values()){dates.add(l.startDate());dates.add(l.endDate().plusDays(1));}
        for(var t:tradeEngine.obligations(team))dates.add(t.terms().startDate());
        dates.removeIf(date->date.isBefore(incomingTerms.startDate())||date.isAfter(incomingTerms.endDate()));
        for(LocalDate date:dates) {
            Set<String> ids=new HashSet<>();contracts.values().stream().filter(c->team.equals(c.team())&&(c.status()==ContractStatus.ACTIVE||c.status()==ContractStatus.SCHEDULED)
                    &&!date.isBefore(c.terms().startDate())&&!date.isAfter(c.terms().endDate())).forEach(c->ids.add(c.playerId()));
            offers.values().stream().filter(o->team.equals(o.team())&&o.open()&&!date.isBefore(o.terms().startDate())&&!date.isAfter(o.terms().endDate())).forEach(o->ids.add(o.playerId()));
            for(var l:tradeEngine.loans.values())if(team.equals(l.borrowingTeam())&&!date.isBefore(l.startDate())&&!date.isAfter(l.endDate()))ids.add(l.playerId());
            for(var t:tradeEngine.obligations(team))if(!date.isBefore(t.terms().startDate())&&!date.isAfter(t.terms().endDate()))ids.add(t.terms().playerId());
            ids.add(incoming);if(ids.size()>accounts.get(team).rosterLimit())throw invalid("계약 기간 중 구단의 선수 보유 상한을 넘습니다.");
        }
    }
    public void withdraw(String team,String offerId,LocalDate date) {
        var o=offers.get(offerId);if(o==null||!team.equals(o.team())||!o.open())throw invalid("철회할 진행 중 제안이 없습니다.");
        offers.put(offerId,offerStatus(o,OfferStatus.WITHDRAWN,"구단이 제안을 철회했습니다.",null));
        event(date,"OFFER_WITHDRAWN",o.playerId(),team,offerId,"계약금과 연봉 예약 해제");
    }
    Offer offerStatus(Offer o,OfferStatus s,String reason,Long request) {
        return new Offer(o.offerId(),o.playerId(),o.team(),o.terms(),o.submittedDate(),o.responseDate(),o.decisionDate(),o.expiresDate(),o.revision()+1,s,o.previousOfferId(),o.round(),request,reason);
    }
    public Evaluation evaluate(Offer o,LocalDate date) {
        var p=player(o.playerId());var pref=preferences.get(p.playerId());long wanted=demand(p.playerId());
        long compensation=o.terms().annualSalary()+o.terms().signingBonus()*365/Math.max(1,java.time.temporal.ChronoUnit.DAYS.between(o.terms().startDate(),o.terms().endDate())+1);
        int money=(int)Math.min(SCORE_MAX,compensation*COMPENSATION_AT_DEMAND/wanted); // Saturates at 125% of demand.
        List<String> competitors=members.values().stream().filter(m->o.team().equals(m.ownerTeam())&&!m.playerId().equals(p.playerId())
                &&player(m.playerId()).position()==p.position()&&eligible(m.playerId(),o.team(),date))
                .filter(m->{var c=active(m.playerId(),date);return !c.terms().endDate().isBefore(o.terms().startDate());}).map(CareerRosterStore.Membership::playerId).toList();
        long stronger=competitors.stream().filter(id->strength(player(id))>=strength(p)).count();
        int opportunity=switch(o.terms().role()){case STARTER->Math.max(STARTER_OPPORTUNITY_FLOOR,SCORE_MAX-(int)stronger*STRONGER_COMPETITOR_PENALTY-Math.max(0,competitors.size()-1)*CROWDING_PENALTY);case RESERVE->RESERVE_OPPORTUNITY;case DEVELOPMENT->DEVELOPMENT_OPPORTUNITY;};
        int teamStrength=(int)lineups.get(o.team()).stream().map(this::player).mapToInt(CareerMarketPolicy::strength).average().orElse(0)*100/MAX_PLAYER_STRENGTH;
        int stability=(int)Math.min(100,java.time.temporal.ChronoUnit.MONTHS.between(o.terms().startDate(),o.terms().endDate().plusDays(1))*100/36);
        var current=active(p.playerId(),date);int familiarity=current!=null&&o.team().equals(current.team())?100:0;
        int relocation=pref.homeRegion()!=null&&!pref.homeRegion().equals(region(o.team()))?pref.relocationPenalty():0;
        int weight=pref.compensation()+pref.opportunity()+pref.strength()+pref.stability()+pref.familiarity();
        long score=(money*pref.compensation()+opportunity*pref.opportunity()+teamStrength*pref.strength()+stability*pref.stability()+familiarity*pref.familiarity())*100L/weight-relocation*100L;
        score+=promiseEngine.adjustment(p.playerId(),o.team(),date);
        int tie=variation(seed,"DECISION|"+p.playerId()+'|'+o.decisionDate()+'|'+o.team(),TIE_VARIANTS);
        return new Evaluation(o.offerId(),o.team(),money,opportunity,teamStrength,stability,familiarity,relocation,score,tie,
                "약속 신뢰 "+promiseEngine.trust(p.playerId(),o.team())+" · 현재 만족도 "+promiseEngine.mood(p.playerId(),date)+" · 요구 보수 대비 "+money+" · 출전 기회 "+opportunity+" · 선발 전력 대체 지표 "+teamStrength+" · 안정성 "+stability+" · 익숙함 "+familiarity+" · 지역 이동 부담 "+relocation,promiseEngine.trust(p.playerId(),o.team()),promiseEngine.mood(p.playerId(),date),promiseEngine.adjustment(p.playerId(),o.team(),date));
    }
    public void release(String team,String playerId,String replacement,LocalDate date) {
        var c=active(playerId,date);if(c==null||!team.equals(c.team()))throw invalid("현재 구단의 유효 계약만 방출할 수 있습니다.");
        if(loan(playerId,date)!=null||tradeEngine.hasAgreement(playerId)||scheduled(playerId)!=null)throw invalid("확정된 미래 계약이 있는 선수의 중도 해지는 지원하지 않습니다.");
        long cost=releaseCost(c,date);long wages=wages(c.terms().annualSalary(),recognizedThrough(c).plusDays(1),date);
        if(salaryArrears(team)>0||paymentHeadroom(team,date,c.contractId(),playerId)<cost+wages)throw invalid("미지급 급여·해지 비용과 남은 선수의 급여 지급 재원을 확보해야 방출할 수 있습니다.");
        if(replacement!=null)requireReplacement(team,playerId,replacement,date);
        pay(c,date);c=contracts.get(c.contractId());
        charge(team,c.contractId(),"RELEASE_COST",date,cost);
        contracts.put(c.contractId(),contractStatus(c,ContractStatus.RELEASED,date,c.paidThrough()));
        depart(c,date,"CONTRACT_RELEASED");
        if(replacement!=null)select(team,replacement,date);
        for(var o:new ArrayList<>(offers.values()))if(o.playerId().equals(playerId)&&o.open())offers.put(o.offerId(),offerStatus(o,OfferStatus.REJECTED,"기존 계약 종료로 제안 조건 재확인이 필요합니다.",null));
    }
    void requireReplacement(String team,String old,String replacement,LocalDate date) {
        var m=replacement==null?null:members.get(replacement);if(replacement==null||replacement.equals(old)||m==null||!eligible(replacement,team,date)||!"FIRST_TEAM".equals(m.squad())||player(old).position()!=player(replacement).position())throw invalid("같은 포지션의 유효한 1군 대체 선수를 지정해 주세요.");
    }
    Contract contractStatus(Contract c,ContractStatus status,LocalDate ended,LocalDate paid) {
        return new Contract(c.contractId(),c.careerId(),c.playerId(),c.team(),c.organizationId(),c.signedDate(),c.terms(),status,c.revision()+1,c.policyVersion(),c.origin(),c.terminationPolicy(),ended,paid);
    }
    void depart(Contract c,LocalDate date,String kind) {
        if(c.team()!=null)promiseEngine.close(c.playerId(),c.team(),date.minusDays(1));
        var m=members.get(c.playerId());
        members.put(c.playerId(),new CareerRosterStore.Membership(c.playerId(),null,null,"UNAFFILIATED","SELECT_GAME_CONTRACT_REQUIRED"));
        lineups.values().forEach(ids->ids.remove(c.playerId()));freeAgents.add(c.playerId());
        event(date,kind,c.playerId(),c.team(),c.contractId(),"고용 종료 · 현재 선발 공백 허용 · 시작된 Series 보존");
    }
    void activate(Contract c,LocalDate date) {
        if(lifecycle!=null&&!lifecycle.permitsContract(c.playerId(),c.terms()))throw invalid("은퇴 선수의 계약을 활성화할 수 없습니다.");
        var m=members.get(c.playerId());
        if(m.ownerTeam()!=null&&!Objects.equals(m.ownerTeam(),c.team()))throw new IllegalStateException("MARKET_DOUBLE_OWNERSHIP");
        String organization=c.terms().role()==Role.DEVELOPMENT?developmentOrganization(c.team()):c.team();
        if(organization==null)throw invalid("허용된 선수 배치 조직이 없습니다.");
        members.put(c.playerId(),new CareerRosterStore.Membership(c.playerId(),c.team(),organization,c.terms().role()==Role.DEVELOPMENT?"DEVELOPMENT":"FIRST_TEAM",null));
        contracts.put(c.contractId(),contractStatus(c,ContractStatus.ACTIVE,null,c.paidThrough()));freeAgents.remove(c.playerId());
        promiseEngine.ensure(c,c.team(),null,c.terms().role(),date,c.terms().endDate());
        event(date,"CONTRACT_ACTIVATED",c.playerId(),c.team(),c.contractId(),"입단 완료 · 선발 선택은 별도 · 대회 등록 자격 확인 필요");
    }
    String developmentOrganization(String team) {
        return directory.organizations().values().stream().filter(o->team.equals(o.competitiveTeam())&&"DEVELOPMENT".equals(o.kind()))
                .map(com.lolfm.player.ExpandedPlayerCatalog.Organization::organizationId).sorted().findFirst().orElse(null);
    }
    void select(String team,String playerId,LocalDate date) {
        if(!eligible(playerId,team,date))throw invalid("현재 계약과 소속이 일치하는 선수만 선발할 수 있습니다.");
        var m=members.get(playerId);members.put(playerId,new CareerRosterStore.Membership(playerId,team,team,"FIRST_TEAM",null));
        var lineup=lineups.get(team);lineup.removeIf(id->player(id).position()==player(playerId).position());lineup.add(playerId);
        lineup.sort(Comparator.comparing(id->player(id).position()));
    }
    public void advance(LocalDate target) {
        if(target.isBefore(processed))throw invalid("시장 날짜를 과거로 이동할 수 없습니다.");
        while(processed.isBefore(target)) {
            if(development!=null) {
                development.closeDay(processed,managed,members,developmentFixtures,developmentYear);
                directory=development.directory();
            }
            LocalDate date=processed.plusDays(1);
            // Same-date order: allocation/debt, loan return, expiry/settlement, activation, trade application, salary, promise evaluation, FA responses/proposals/decisions, AI lineup.
            if(finance!=null)finance.date(date);
            if(date.getDayOfYear()==1)for(var a:new ArrayList<>(accounts.values()))if(finance==null||!finance.recurring(a.team(),date))credit(a.team(),date);
            tradeEngine.returns(date);
            Map<String,String> renewedSelections=new TreeMap<>();
            for(var c:new ArrayList<>(contracts.values()))if(c.status()==ContractStatus.ACTIVE&&date.isAfter(c.terms().endDate())) {
                var successor=scheduled(c.playerId());
                if(c.team()!=null&&successor!=null&&c.team().equals(successor.team())&&lineups.get(c.team()).contains(c.playerId())&&successor.terms().role()!=Role.DEVELOPMENT)renewedSelections.put(c.playerId(),c.team());
                pay(c,date);c=contracts.get(c.contractId());
                contracts.put(c.contractId(),contractStatus(c,ContractStatus.EXPIRED,c.terms().endDate(),c.paidThrough()));depart(c,date,"CONTRACT_EXPIRED");
            }
            if(lifecycle!=null)lifecycle.effective(this,date);
            for(var c:new ArrayList<>(contracts.values()))if(c.status()==ContractStatus.SCHEDULED&&!date.isBefore(c.terms().startDate()))activate(c,date);
            renewedSelections.forEach((player,team)->{if(lifecycle==null||!lifecycle.retired(player))select(team,player,date);});
            tradeEngine.process(date);
            if(date.getDayOfMonth()==date.lengthOfMonth())for(var c:new ArrayList<>(contracts.values()))if(c.status()==ContractStatus.ACTIVE)pay(c,date);
            for(var c:contracts.values())if(c.status()==ContractStatus.ACTIVE&&date.equals(c.terms().endDate().minusDays(NEGOTIATION_DAYS)))event(date,"EXPIRY_WARNING",c.playerId(),c.team(),c.contractId(),"계약 만료 60일 전 · 재계약 및 미래 FA 협상 가능");
            promiseEngine.evaluate(date);
            responses(date);
            planner.review(date);
            decide(date);
            for(var o:new ArrayList<>(offers.values()))if(o.open()&&!date.isBefore(o.expiresDate()))offers.put(o.offerId(),offerStatus(o,OfferStatus.EXPIRED,"제안 유효기간 종료",null));
            planner.repair(date);if(lifecycle!=null)lifecycle.observe(this,date);processed=date;
        }
        validateIntegrity();
    }
    public LocalDate nextEvent(LocalDate current) {
        TreeSet<LocalDate> dates=new TreeSet<>(tradeEngine.nextDates(current));dates.add(current.withDayOfMonth(current.lengthOfMonth()));
        if(finance!=null)for(var award:finance.awards.values())if(award.paidOn()==null)dates.add(award.dueOn());
        if(lifecycle!=null)for(var p:lifecycle.people.values())if(p.status()==CareerLifecycleState.Status.RETIREMENT_ANNOUNCED)dates.add(p.effectiveOn());
        dates.add(current.plusDays(1).with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)));
        dates.add(LocalDate.of(current.getYear()+1,1,1));
        for(var c:contracts.values()) {
            if(c.status()==ContractStatus.ACTIVE){dates.add(c.terms().endDate().plusDays(1));dates.add(c.terms().endDate().minusDays(NEGOTIATION_DAYS));}
            if(c.status()==ContractStatus.SCHEDULED)dates.add(c.terms().startDate());
        }
        for(var o:offers.values())if(o.open()){dates.add(o.responseDate());dates.add(o.decisionDate());dates.add(o.expiresDate());}
        return dates.stream().filter(d->d.isAfter(current)).findFirst().orElse(current.plusDays(1));
    }
    private void responses(LocalDate date) {
        for(var o:new ArrayList<>(offers.values())) {
            if(o.status()==OfferStatus.SUBMITTED&&date.equals(o.responseDate())&&date.isBefore(o.decisionDate())) {
                var e=evaluate(o,date);long wanted=demand(o.playerId());
                if(o.terms().annualSalary()*100<wanted*MIN_SALARY_PERCENT) {
                    offers.put(o.offerId(),offerStatus(o,OfferStatus.REJECTED,"요구 보수에 크게 미달하여 거절했습니다.",null));
                    event(date,"OFFER_REJECTED",o.playerId(),o.team(),o.offerId(),"보수 부족 · 예약 해제");
                }else if(o.terms().annualSalary()<wanted||e.score()<MIN_ACCEPT_SCORE) {
                    offers.put(o.offerId(),offerStatus(o,OfferStatus.COUNTER,"보수 또는 출전 기회가 부족합니다. 조건 수정을 요청합니다.",wanted*COUNTER_SALARY_PERCENT/100));
                    event(date,"COUNTER_REQUESTED",o.playerId(),o.team(),o.offerId(),"수정 요청 · 기존 조건으로 자동 동의하지 않음");
                }
            }
        }
        for(var o:new ArrayList<>(offers.values()))if(o.status()==OfferStatus.COUNTER&&!o.team().equals(managed)&&date.isBefore(o.decisionDate())&&o.round()<MAX_ROUNDS) {
            long ceiling=demand(o.playerId())*AI_MAX_BID_PERCENT/100;
            if(o.requestedSalary()<=ceiling) {
                try{submit(o.team(),o.playerId(),new Terms(o.terms().startDate(),o.terms().endDate(),o.requestedSalary(),o.terms().signingBonus(),o.terms().role()),o.offerId(),date);}
                catch(CareerException insufficient){withdraw(o.team(),o.offerId(),date);}
            }else withdraw(o.team(),o.offerId(),date);
        }
    }
    private void decide(LocalDate date) {
        Set<String> players=new TreeSet<>();offers.values().stream().filter(o->o.open()&&o.decisionDate().equals(date)).forEach(o->players.add(o.playerId()));
        for(String playerId:players) {
            String event=id(career,"DECISION|"+playerId+'|'+date);if(decisions.containsKey(event))continue;
            var candidates=offers.values().stream().filter(o->o.playerId().equals(playerId)&&o.open()&&o.decisionDate().equals(date)).toList();
            var scores=candidates.stream().map(o->evaluate(o,date)).sorted(Comparator.comparingLong(Evaluation::score).reversed().thenComparing(Evaluation::team)).toList();
            var acceptable=scores.stream().filter(e->offers.get(e.offerId()).status()==OfferStatus.SUBMITTED&&e.score()>=MIN_ACCEPT_SCORE
                    &&offers.get(e.offerId()).terms().annualSalary()>=demand(playerId)*MIN_SALARY_PERCENT/100).toList();
            Evaluation winning=null;
            if(!acceptable.isEmpty()) {
                long best=acceptable.getFirst().score();
                // At most 0.10 score points of deterministic variance, only inside a near-equal band.
                winning=acceptable.stream().filter(e->best-e.score()<=NEAR_EQUAL_SCORE_BAND).max(Comparator.comparingLong((Evaluation e)->e.score()+e.tieBreak()).thenComparing(Evaluation::team)).orElseThrow();
            }
            String winner=null;String reason="만족할 유효 제안이 없어 기존 계약을 유지하거나 FA로 기다립니다.";
            if(winning!=null) {
                var offer=offers.get(winning.offerId());
                try {accept(offer,date);winner=offer.offerId();reason="보수·출전 기회·전력·안정성·친숙도를 비교해 선택: "+winning.reason();}
                catch(CareerException rejected){reason="결정 시점 자격/예산 재확인 실패: "+rejected.clientMessage();}
            }
            for(var o:candidates)if(!o.offerId().equals(winner)&&!(winner==null&&o.status()==OfferStatus.COUNTER))offers.put(o.offerId(),offerStatus(o,OfferStatus.REJECTED,winner==null?reason:"다른 구단의 실제 제안을 선택했습니다.",null));
            decisions.put(event,new Decision(event,playerId,date,winner,scores,reason,VERSION));
            event(date,"PLAYER_DECISION",playerId,winner==null?null:offers.get(winner).team(),event,reason);
        }
    }
    private void accept(Offer o,LocalDate date) {
        if(lifecycle!=null&&!lifecycle.permitsContract(o.playerId(),o.terms()))throw invalid("은퇴 효력일과 충돌하는 계약입니다.");
        if(loan(o.playerId(),date)!=null||tradeEngine.hasAgreement(o.playerId())||scheduled(o.playerId())!=null)throw invalid("이미 미래 계약이 확정되었습니다.");
        for(var c:contracts.values())if(c.playerId().equals(o.playerId())&&(c.status()==ContractStatus.ACTIVE||c.status()==ContractStatus.SCHEDULED)&&overlaps(c.terms(),o.terms()))throw invalid("기존 고용 계약과 효력 기간이 겹칩니다.");
        var active=active(o.playerId(),date);
        if(active==null&&!freeAgents.contains(o.playerId()))throw invalid("현재 영입 가능한 FA가 아닙니다.");
        if(o.terms().startDate().isBefore(date))throw invalid("이전 제안의 시작일이 결정일보다 빠릅니다. 새 시작일로 재협상해 주세요. 과거 계약이나 급여는 소급하지 않습니다.");
        requireBudget(o.team(),date);requireRosterCapacity(o.team(),o.playerId(),o.terms());
        String contractId=id(career,"CONTRACT|"+o.offerId());
        var c=new Contract(contractId,career,o.playerId(),o.team(),o.team(),date,o.terms(),ContractStatus.SCHEDULED,0,VERSION,
                active!=null&&o.team().equals(active.team())?"NEGOTIATED_RENEWAL":"NEGOTIATED_FREE_AGENT","REMAINING_SALARY_25_PERCENT_V1",null,o.terms().startDate().minusDays(1));
        offers.put(o.offerId(),offerStatus(o,OfferStatus.ACCEPTED,"선수가 제안을 수락했습니다.",null));
        contracts.put(contractId,c);charge(o.team(),contractId,"SIGNING_BONUS",date,o.terms().signingBonus());
        for(var other:new ArrayList<>(offers.values()))if(other.playerId().equals(o.playerId())&&other.open()&&overlaps(other.terms(),o.terms()))offers.put(other.offerId(),offerStatus(other,OfferStatus.REJECTED,"충돌하는 계약이 확정되어 예약이 해제됐습니다.",null));
        if(!date.isBefore(o.terms().startDate()))activate(c,date);
        event(date,"CONTRACT_SIGNED",o.playerId(),o.team(),contractId,"계약금 1회 지급 · 효력 시작 전에는 소속 유지");
    }
    private void credit(String team,LocalDate date) {
        String id=id(career,"ALLOCATION|"+team+'|'+date.getYear());if(ledger.stream().anyMatch(l->l.entryId().equals(id)))return;
        var a=accounts.get(team);accounts.put(team,new Account(team,a.annualBudget(),Math.addExact(a.cash(),a.annualBudget()),a.rosterLimit()));
        ledger.add(new Ledger(id,date,team,null,"ANNUAL_ALLOCATION",a.annualBudget()));
        settleArrears(team,date);
    }
    void charge(String team,String contract,String kind,LocalDate date,long amount) {
        if(team==null)return;
        String id=id(career,kind+'|'+contract+'|'+date+(kind.equals("SALARY_ARREARS_PAYMENT")?"|"+team+"|"+ledger.stream().filter(l->team.equals(l.team())&&contract.equals(l.contractId())&&l.kind().equals(kind)).mapToLong(Ledger::amount).sum():""));if(ledger.stream().anyMatch(l->l.entryId().equals(id)))return;
        var a=accounts.get(team);if(a.cash()<amount)throw invalid("급여 또는 계약 비용 지급 잔액이 부족합니다.");
        accounts.put(team,new Account(team,a.annualBudget(),a.cash()-amount,a.rosterLimit()));ledger.add(new Ledger(id,date,team,contract,kind,-amount));
    }
    void receive(String team,String reference,String kind,LocalDate date,long amount) {
        String id=id(career,kind+'|'+reference+'|'+date);if(ledger.stream().anyMatch(l->l.entryId().equals(id)))return;
        var a=accounts.get(team);accounts.put(team,new Account(team,a.annualBudget(),Math.addExact(a.cash(),amount),a.rosterLimit()));
        ledger.add(new Ledger(id,date,team,reference,kind,amount));settleArrears(team,date);
    }
    void pay(Contract c,LocalDate date) {payThrough(c,date,date);}
    void payThrough(Contract c,LocalDate date,LocalDate through) {
        LocalDate end=through.isBefore(c.terms().endDate())?through:c.terms().endDate();
        LocalDate recognized=recognizedThrough(c);if(!end.isAfter(recognized))return;
        var shares=salaryShares(c,recognized.plusDays(1),end);
        boolean debt=contractArrears(c.contractId())>0||shares.entrySet().stream().anyMatch(p->accounts.get(p.getKey()).cash()<p.getValue());
        if(!debt) {
            for(var p:shares.entrySet())charge(p.getKey(),c.contractId(),shares.size()>1?(p.getKey().equals(c.team())?"LOAN_SALARY_PARENT":"LOAN_SALARY_BORROWER"):"SALARY",date,p.getValue());
            contracts.put(c.contractId(),contractStatus(c,c.status(),c.endedDate(),end));
        } else {
            for(var p:shares.entrySet())if(p.getValue()>0)ledger.add(new Ledger(id(career,"SALARY_ACCRUED|"+c.contractId()+'|'+end+'|'+p.getKey()),end,p.getKey(),c.contractId(),"SALARY_ACCRUED",p.getValue()));
            event(end,"SALARY_ARREARS_RECORDED",c.playerId(),c.team(),c.contractId(),"급여 미지급금 기록 · 각 지급 구단 책임 · 확정 수입에서 우선 정산");
            for(String team:shares.keySet())settleArrears(team,date);
        }
    }
    void event(LocalDate date,String kind,String player,String team,String reference,String reason) {
        String id=id(career,kind+'|'+reference+'|'+date);
        if(events.stream().noneMatch(e->e.eventId().equals(id)))events.add(new Event(id,date,kind,player,team,reference,reason));
    }
    public void validateIntegrity() {
        CareerRosterStore.validate(roster(),directory);
        if(!preferences.keySet().equals(directory.players().keySet())||!directory.players().keySet().containsAll(freeAgents))throw new IllegalStateException("MARKET_POPULATION_REFERENCE");
        // Insufficient funding is recoverable business state, not structural corruption.
        for(var a:accounts.values())if(a.cash()<0||salaryArrears(a.team())<0)throw new IllegalStateException("INVALID_MARKET_CASH_OR_ARREARS");
        var live=contracts.values().stream().filter(c->c.status()==ContractStatus.ACTIVE||c.status()==ContractStatus.SCHEDULED).toList();
        for(int i=0;i<live.size();i++)for(int j=i+1;j<live.size();j++)if(live.get(i).playerId().equals(live.get(j).playerId())&&overlaps(live.get(i).terms(),live.get(j).terms()))throw new IllegalStateException("OVERLAPPING_EMPLOYMENT_CONTRACTS");
        for(var c:live)if(c.status()==ContractStatus.ACTIVE&&!Objects.equals(loan(c.playerId(),processed)==null?c.team():loan(c.playerId(),processed).borrowingTeam(),members.get(c.playerId()).ownerTeam()))throw new IllegalStateException("CONTRACT_MEMBERSHIP_MISMATCH");
        var loans=new ArrayList<>(tradeEngine.loans.values());
        for(int i=0;i<loans.size();i++) {
            var l=loans.get(i);var c=contracts.get(l.contractId());
            if(c==null||!c.playerId().equals(l.playerId())||!Objects.equals(c.team(),l.parentTeam())||l.parentTeam().equals(l.borrowingTeam())
                    ||!accounts.containsKey(l.borrowingTeam())||l.borrowerSalaryPercent()<0||l.borrowerSalaryPercent()>100||l.endDate().isBefore(l.startDate())||l.endDate().isAfter(c.terms().endDate()))throw new IllegalStateException("INVALID_LOAN_OWNERSHIP_OR_OBLIGATION");
            for(int j=i+1;j<loans.size();j++){var other=loans.get(j);if(l.playerId().equals(other.playerId())&&!l.startDate().isAfter(other.endDate())&&!other.startDate().isAfter(l.endDate()))throw new IllegalStateException("OVERLAPPING_LOANS");}
        }
        if(ledger.stream().map(Ledger::entryId).distinct().count()!=ledger.size())throw new IllegalStateException("DUPLICATE_MARKET_PAYMENT");
    }
}
