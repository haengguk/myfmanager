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
    private final String career, managed;
    private final long seed;
    private final CareerRosterStore.Directory directory;
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

    public CareerMarketEngine(String career,String managed,CareerRosterStore.Directory directory,
            CareerRosterStore.State roster,CareerMarketState state) {
        this.career=career;this.managed=managed;this.directory=directory;seed=state.seed();processed=state.processedThrough();
        contracts.putAll(state.contracts());offers.putAll(state.offers());accounts.putAll(state.accounts());
        preferences.putAll(state.preferences());freeAgents.addAll(state.freeAgents());ledger.addAll(state.ledger());
        decisions.putAll(state.decisions());events.addAll(state.events());members.putAll(roster.members());
        roster.lineups().forEach((team,ids)->lineups.put(team,new ArrayList<>(ids)));
    }
    public static CareerMarketState initialize(String career,long seed,LocalDate date,int firstYear,
            CareerRosterStore.Directory directory,CareerRosterStore.State roster) {
        Map<String,Contract> contracts=new TreeMap<>();Map<String,Preference> preferences=new TreeMap<>();
        Set<String> free=new TreeSet<>();Map<String,Account> accounts=new TreeMap<>();List<Ledger> ledger=new ArrayList<>();
        for(var player:directory.players().values().stream().sorted(Comparator.comparing(Definition::playerId)).toList()) {
            preferences.put(player.playerId(),preference(seed,player));
            var member=roster.members().get(player.playerId());
            if("UNAFFILIATED".equals(member.squad())) {free.add(player.playerId());continue;}
            if(member.organizationId()==null || "UNCONFIRMED".equals(member.squad()))continue;
            LocalDate end=LocalDate.of(date.getYear(),11,30);String origin="GAME_INITIAL_CONTRACT_UNKNOWN_PUBLIC_TERMS";
            try {
                JsonNode details=CareerRosterStore.read(player.detailsJson(),JsonNode.class);
                String reported=details.path("contract").path("endDate").asText("");
                String status=details.path("contract").path("status").asText("");
                if(!reported.isBlank() && !status.contains("CONFLICT") && !status.contains("UNVERIFIED")
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
                    new Terms(date,end,demand(player),0,role),ContractStatus.ACTIVE,0,VERSION,origin,
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
    public CareerMarketState state() {return new CareerMarketState(VERSION,seed,processed,contracts,offers,accounts,preferences,freeAgents,ledger,decisions,events);}
    public CareerRosterStore.State roster() {return new CareerRosterStore.State(CareerRosterStore.OPERATING_POLICY,members,lineups);}
    public static String id(String career,String event) {return "market_"+CareerRosterStore.hash(career+'|'+VERSION+'|'+event);}
    private Definition player(String id) {var p=directory.players().get(id);if(p==null)throw invalid("선수 정보를 찾을 수 없습니다.");return p;}
    private static CareerException invalid(String reason) {return CareerException.invalid("market",reason);}
    public Contract active(String player,LocalDate date) {return contracts.values().stream().filter(c->c.playerId().equals(player)
            && c.status()==ContractStatus.ACTIVE && !date.isBefore(c.terms().startDate()) && !date.isAfter(c.terms().endDate())).findFirst().orElse(null);}
    public Contract scheduled(String player) {return contracts.values().stream().filter(c->c.playerId().equals(player)&&c.status()==ContractStatus.SCHEDULED).findFirst().orElse(null);}
    public boolean eligible(String player,String team,LocalDate date) {
        var c=active(player,date);var m=members.get(player);
        return c!=null && team.equals(c.team()) && m!=null && team.equals(m.ownerTeam()) && m.eligibilityReason()==null;
    }
    public LocalDate decisionDate(String player,LocalDate date) {
        return offers.values().stream().filter(o->o.playerId().equals(player)&&o.open()&&!o.decisionDate().isBefore(date))
                .map(Offer::decisionDate).min(LocalDate::compareTo).orElse(date.plusDays(DECISION_DAYS));
    }
    public LocalDate availableStart(String player,LocalDate date) {
        var c=active(player,date);if(scheduled(player)!=null)return null;
        if(c!=null)return c.team()!=null&&!c.terms().endDate().isAfter(date.plusDays(NEGOTIATION_DAYS))?c.terms().endDate().plusDays(1):null;
        return freeAgents.contains(player)?decisionDate(player,date):null;
    }
    public long reservedCash(String team) {return offers.values().stream().filter(o->o.team().equals(team)&&o.open()).mapToLong(o->o.terms().signingBonus()).sum();}
    public long salaryAt(String team,LocalDate date,boolean includeOffers) {
        long sum=contracts.values().stream().filter(c->team.equals(c.team())&&(c.status()==ContractStatus.ACTIVE||c.status()==ContractStatus.SCHEDULED)
                && !date.isBefore(c.terms().startDate())&&!date.isAfter(c.terms().endDate())).mapToLong(c->c.terms().annualSalary()).sum();
        if(includeOffers)sum+=offers.values().stream().filter(o->team.equals(o.team())&&o.open()&&!date.isBefore(o.terms().startDate())&&!date.isAfter(o.terms().endDate())).mapToLong(o->o.terms().annualSalary()).sum();
        return sum;
    }
    public long peakSalary(String team) {
        TreeSet<LocalDate> dates=new TreeSet<>(List.of(processed));
        contracts.values().stream().filter(c->team.equals(c.team())&&(c.status()==ContractStatus.ACTIVE||c.status()==ContractStatus.SCHEDULED)).forEach(c->dates.add(c.terms().startDate()));
        offers.values().stream().filter(o->team.equals(o.team())&&o.open()).forEach(o->dates.add(o.terms().startDate()));
        return dates.stream().mapToLong(d->salaryAt(team,d,true)).max().orElse(0);
    }
    private void requireBudget(String team) {
        var a=accounts.get(team);if(a==null)throw invalid("시장에 참여할 수 없는 조직입니다.");
        if(a.cash()<reservedCash(team)||a.annualBudget()<peakSalary(team))throw invalid("계약금 잔액 또는 계약 기간의 연봉 예산이 부족합니다.");
    }
    public Offer submit(String team,String playerId,Terms terms,String previousId,LocalDate date) {
        validate(terms);player(playerId);LocalDate available=availableStart(playerId,date);
        if(available==null)throw invalid("현재 계약 보호 기간이거나 이미 확정된 미래 계약이 있어 제안할 수 없습니다.");
        var current=active(playerId,date);
        if(current!=null && !terms.startDate().equals(available) || current==null && (terms.startDate().isBefore(available)||terms.startDate().isAfter(available.plusDays(FA_START_DELAY_DAYS))))
            throw invalid("현재 계약 종료 다음 날 또는 표시된 FA 결정일 이후부터 계약을 시작해야 합니다.");
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
        if(previous!=null)offers.put(previousId,status(previous,OfferStatus.SUPERSEDED,"수정 제안으로 대체됨",null));
        var offer=new Offer(offerId,playerId,team,terms,date,date.plusDays(RESPONSE_DAYS).isBefore(decision)?date.plusDays(RESPONSE_DAYS):decision,
                decision,decision.plusDays(1),0,OfferStatus.SUBMITTED,previousId,round,null,"선수가 제안을 검토하고 있습니다.");
        offers.put(offerId,offer);
        try {requireBudget(team);requireRosterCapacity(team,playerId,terms);}
        catch(RuntimeException rejected){offers.remove(offerId);if(previous!=null)offers.put(previousId,previous);throw rejected;}
        event(date,"OFFER_SUBMITTED",playerId,team,offerId,"실제 지출 예약을 포함한 계약 제안");return offer;
    }
    private void requireRosterCapacity(String team,String incoming,Terms incomingTerms) {
        TreeSet<LocalDate> dates=new TreeSet<>(List.of(incomingTerms.startDate()));
        contracts.values().stream().filter(c->team.equals(c.team())&&(c.status()==ContractStatus.ACTIVE||c.status()==ContractStatus.SCHEDULED)&&overlaps(c.terms(),incomingTerms)).forEach(c->dates.add(c.terms().startDate().isBefore(incomingTerms.startDate())?incomingTerms.startDate():c.terms().startDate()));
        offers.values().stream().filter(o->team.equals(o.team())&&o.open()&&overlaps(o.terms(),incomingTerms)).forEach(o->dates.add(o.terms().startDate().isBefore(incomingTerms.startDate())?incomingTerms.startDate():o.terms().startDate()));
        for(LocalDate date:dates) {
            Set<String> ids=new HashSet<>();contracts.values().stream().filter(c->team.equals(c.team())&&(c.status()==ContractStatus.ACTIVE||c.status()==ContractStatus.SCHEDULED)
                    &&!date.isBefore(c.terms().startDate())&&!date.isAfter(c.terms().endDate())).forEach(c->ids.add(c.playerId()));
            offers.values().stream().filter(o->team.equals(o.team())&&o.open()&&!date.isBefore(o.terms().startDate())&&!date.isAfter(o.terms().endDate())).forEach(o->ids.add(o.playerId()));
            ids.add(incoming);if(ids.size()>accounts.get(team).rosterLimit())throw invalid("계약 기간 중 구단의 선수 보유 상한을 넘습니다.");
        }
    }
    public void withdraw(String team,String offerId,LocalDate date) {
        var o=offers.get(offerId);if(o==null||!team.equals(o.team())||!o.open())throw invalid("철회할 진행 중 제안이 없습니다.");
        offers.put(offerId,status(o,OfferStatus.WITHDRAWN,"구단이 제안을 철회했습니다.",null));
        event(date,"OFFER_WITHDRAWN",o.playerId(),team,offerId,"계약금과 연봉 예약 해제");
    }
    private Offer status(Offer o,OfferStatus s,String reason,Long request) {
        return new Offer(o.offerId(),o.playerId(),o.team(),o.terms(),o.submittedDate(),o.responseDate(),o.decisionDate(),o.expiresDate(),o.revision()+1,s,o.previousOfferId(),o.round(),request,reason);
    }
    public Evaluation evaluate(Offer o,LocalDate date) {
        var p=player(o.playerId());var pref=preferences.get(p.playerId());long wanted=demand(p);
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
        int tie=variation(seed,"DECISION|"+p.playerId()+'|'+o.decisionDate()+'|'+o.team(),TIE_VARIANTS);
        return new Evaluation(o.offerId(),o.team(),money,opportunity,teamStrength,stability,familiarity,relocation,score,tie,
                "요구 보수 대비 "+money+" · 출전 기회 "+opportunity+" · 선발 전력 대체 지표 "+teamStrength+" · 안정성 "+stability+" · 익숙함 "+familiarity+" · 지역 이동 부담 "+relocation);
    }
    public void release(String team,String playerId,String replacement,LocalDate date) {
        var c=active(playerId,date);if(c==null||!team.equals(c.team()))throw invalid("현재 구단의 유효 계약만 방출할 수 있습니다.");
        if(scheduled(playerId)!=null)throw invalid("확정된 미래 계약이 있는 선수의 중도 해지는 지원하지 않습니다.");
        long cost=releaseCost(c,date);long wages=wages(c.terms().annualSalary(),c.paidThrough().plusDays(1),date);
        if(accounts.get(team).cash()-reservedCash(team)<cost+wages)throw invalid("미지급 급여와 해지 비용을 지급할 잔액이 부족합니다.");
        if(replacement!=null)requireReplacement(team,playerId,replacement,date);
        pay(c,date);c=contracts.get(c.contractId());
        charge(team,c.contractId(),"RELEASE_COST",date,cost);
        contracts.put(c.contractId(),contractStatus(c,ContractStatus.RELEASED,date,c.paidThrough()));
        depart(c,date,"CONTRACT_RELEASED");
        if(replacement!=null)select(team,replacement,date);
        for(var o:new ArrayList<>(offers.values()))if(o.playerId().equals(playerId)&&o.open())offers.put(o.offerId(),status(o,OfferStatus.REJECTED,"기존 계약 종료로 제안 조건 재확인이 필요합니다.",null));
    }
    private void requireReplacement(String team,String old,String replacement,LocalDate date) {
        var m=members.get(replacement);if(replacement.equals(old)||m==null||!eligible(replacement,team,date)||!"FIRST_TEAM".equals(m.squad())||player(old).position()!=player(replacement).position())throw invalid("같은 포지션의 유효한 1군 대체 선수를 지정해 주세요.");
    }
    private Contract contractStatus(Contract c,ContractStatus status,LocalDate ended,LocalDate paid) {
        return new Contract(c.contractId(),c.careerId(),c.playerId(),c.team(),c.organizationId(),c.signedDate(),c.terms(),status,c.revision()+1,c.policyVersion(),c.origin(),c.terminationPolicy(),ended,paid);
    }
    private void depart(Contract c,LocalDate date,String kind) {
        var m=members.get(c.playerId());
        members.put(c.playerId(),new CareerRosterStore.Membership(c.playerId(),null,null,"UNAFFILIATED","SELECT_GAME_CONTRACT_REQUIRED"));
        lineups.values().forEach(ids->ids.remove(c.playerId()));freeAgents.add(c.playerId());
        event(date,kind,c.playerId(),c.team(),c.contractId(),"고용 종료 · 현재 선발 공백 허용 · 시작된 Series 보존");
    }
    private void activate(Contract c,LocalDate date) {
        var m=members.get(c.playerId());
        if(m.ownerTeam()!=null&&!Objects.equals(m.ownerTeam(),c.team()))throw new IllegalStateException("MARKET_DOUBLE_OWNERSHIP");
        String organization=c.terms().role()==Role.DEVELOPMENT?developmentOrganization(c.team()):c.team();
        if(organization==null)throw invalid("허용된 선수 배치 조직이 없습니다.");
        members.put(c.playerId(),new CareerRosterStore.Membership(c.playerId(),c.team(),organization,c.terms().role()==Role.DEVELOPMENT?"DEVELOPMENT":"FIRST_TEAM",null));
        contracts.put(c.contractId(),contractStatus(c,ContractStatus.ACTIVE,null,c.paidThrough()));freeAgents.remove(c.playerId());
        event(date,"CONTRACT_ACTIVATED",c.playerId(),c.team(),c.contractId(),"입단 완료 · 선발 선택은 별도 · 대회 등록 자격 확인 필요");
    }
    private String developmentOrganization(String team) {
        return directory.organizations().values().stream().filter(o->team.equals(o.competitiveTeam())&&"DEVELOPMENT".equals(o.kind()))
                .map(com.lolfm.player.ExpandedPlayerCatalog.Organization::organizationId).sorted().findFirst().orElse(null);
    }
    private void select(String team,String playerId,LocalDate date) {
        if(!eligible(playerId,team,date))throw invalid("현재 계약과 소속이 일치하는 선수만 선발할 수 있습니다.");
        var m=members.get(playerId);members.put(playerId,new CareerRosterStore.Membership(playerId,team,team,"FIRST_TEAM",null));
        var lineup=lineups.get(team);lineup.removeIf(id->player(id).position()==player(playerId).position());lineup.add(playerId);
        lineup.sort(Comparator.comparing(id->player(id).position()));
    }
    public void advance(LocalDate target) {
        if(target.isBefore(processed))throw invalid("시장 날짜를 과거로 이동할 수 없습니다.");
        while(processed.isBefore(target)) {
            LocalDate date=processed.plusDays(1);
            // Same-date order: allocation, end+salary settlement, activation, salary, warning, AI responses/proposals, player decisions, AI lineup.
            if(date.getDayOfYear()==1)for(var a:new ArrayList<>(accounts.values()))credit(a.team(),date);
            Map<String,String> renewedSelections=new TreeMap<>();
            for(var c:new ArrayList<>(contracts.values()))if(c.status()==ContractStatus.ACTIVE&&date.isAfter(c.terms().endDate())) {
                var successor=scheduled(c.playerId());
                if(c.team()!=null&&successor!=null&&c.team().equals(successor.team())&&lineups.get(c.team()).contains(c.playerId())&&successor.terms().role()!=Role.DEVELOPMENT)renewedSelections.put(c.playerId(),c.team());
                pay(c,c.terms().endDate());c=contracts.get(c.contractId());
                contracts.put(c.contractId(),contractStatus(c,ContractStatus.EXPIRED,c.terms().endDate(),c.paidThrough()));depart(c,date,"CONTRACT_EXPIRED");
            }
            for(var c:new ArrayList<>(contracts.values()))if(c.status()==ContractStatus.SCHEDULED&&!date.isBefore(c.terms().startDate()))activate(c,date);
            renewedSelections.forEach((player,team)->select(team,player,date));
            if(date.getDayOfMonth()==date.lengthOfMonth())for(var c:new ArrayList<>(contracts.values()))if(c.status()==ContractStatus.ACTIVE)pay(c,date);
            for(var c:contracts.values())if(c.status()==ContractStatus.ACTIVE&&date.equals(c.terms().endDate().minusDays(NEGOTIATION_DAYS)))event(date,"EXPIRY_WARNING",c.playerId(),c.team(),c.contractId(),"계약 만료 60일 전 · 재계약 및 미래 FA 협상 가능");
            responses(date);
            if(date.getDayOfWeek()==DayOfWeek.MONDAY)aiProposals(date);
            decide(date);
            for(var o:new ArrayList<>(offers.values()))if(o.open()&&!date.isBefore(o.expiresDate()))offers.put(o.offerId(),status(o,OfferStatus.EXPIRED,"제안 유효기간 종료",null));
            aiLineups(date);processed=date;
        }
        validateIntegrity();
    }
    public LocalDate nextEvent(LocalDate current) {
        TreeSet<LocalDate> dates=new TreeSet<>();dates.add(current.withDayOfMonth(current.lengthOfMonth()));
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
                var e=evaluate(o,date);long wanted=demand(player(o.playerId()));
                if(o.terms().annualSalary()*100<wanted*MIN_SALARY_PERCENT) {
                    offers.put(o.offerId(),status(o,OfferStatus.REJECTED,"요구 보수에 크게 미달하여 거절했습니다.",null));
                    event(date,"OFFER_REJECTED",o.playerId(),o.team(),o.offerId(),"보수 부족 · 예약 해제");
                }else if(o.terms().annualSalary()<wanted||e.score()<MIN_ACCEPT_SCORE) {
                    offers.put(o.offerId(),status(o,OfferStatus.COUNTER,"보수 또는 출전 기회가 부족합니다. 조건 수정을 요청합니다.",wanted*COUNTER_SALARY_PERCENT/100));
                    event(date,"COUNTER_REQUESTED",o.playerId(),o.team(),o.offerId(),"수정 요청 · 기존 조건으로 자동 동의하지 않음");
                }
            }
        }
        for(var o:new ArrayList<>(offers.values()))if(o.status()==OfferStatus.COUNTER&&!o.team().equals(managed)&&date.isBefore(o.decisionDate())&&o.round()<MAX_ROUNDS) {
            long ceiling=demand(player(o.playerId()))*AI_MAX_BID_PERCENT/100;
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
                    &&offers.get(e.offerId()).terms().annualSalary()>=demand(player(playerId))*MIN_SALARY_PERCENT/100).toList();
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
            for(var o:candidates)if(!o.offerId().equals(winner)&&!(winner==null&&o.status()==OfferStatus.COUNTER))offers.put(o.offerId(),status(o,OfferStatus.REJECTED,winner==null?reason:"다른 구단의 실제 제안을 선택했습니다.",null));
            decisions.put(event,new Decision(event,playerId,date,winner,scores,reason,VERSION));
            event(date,"PLAYER_DECISION",playerId,winner==null?null:offers.get(winner).team(),event,reason);
        }
    }
    private void accept(Offer o,LocalDate date) {
        if(scheduled(o.playerId())!=null)throw invalid("이미 미래 계약이 확정되었습니다.");
        for(var c:contracts.values())if(c.playerId().equals(o.playerId())&&(c.status()==ContractStatus.ACTIVE||c.status()==ContractStatus.SCHEDULED)&&overlaps(c.terms(),o.terms()))throw invalid("기존 고용 계약과 효력 기간이 겹칩니다.");
        var active=active(o.playerId(),date);
        if(active==null&&!freeAgents.contains(o.playerId()))throw invalid("현재 영입 가능한 FA가 아닙니다.");
        if(o.terms().startDate().isBefore(date))throw invalid("효력 시작일이 지난 제안입니다.");
        requireBudget(o.team());requireRosterCapacity(o.team(),o.playerId(),o.terms());
        String contractId=id(career,"CONTRACT|"+o.offerId());
        var c=new Contract(contractId,career,o.playerId(),o.team(),o.team(),date,o.terms(),ContractStatus.SCHEDULED,0,VERSION,
                active!=null&&o.team().equals(active.team())?"NEGOTIATED_RENEWAL":"NEGOTIATED_FREE_AGENT","REMAINING_SALARY_25_PERCENT_V1",null,o.terms().startDate().minusDays(1));
        offers.put(o.offerId(),status(o,OfferStatus.ACCEPTED,"선수가 제안을 수락했습니다.",null));
        contracts.put(contractId,c);charge(o.team(),contractId,"SIGNING_BONUS",date,o.terms().signingBonus());
        for(var other:new ArrayList<>(offers.values()))if(other.playerId().equals(o.playerId())&&other.open()&&overlaps(other.terms(),o.terms()))offers.put(other.offerId(),status(other,OfferStatus.REJECTED,"충돌하는 계약이 확정되어 예약이 해제됐습니다.",null));
        if(!date.isBefore(o.terms().startDate()))activate(c,date);
        event(date,"CONTRACT_SIGNED",o.playerId(),o.team(),contractId,"계약금 1회 지급 · 효력 시작 전에는 소속 유지");
    }
    private void aiProposals(LocalDate date) {
        // All clubs inspect the same pre-decision state; no player is awarded inside this loop.
        for(String team:accounts.keySet()) {
            if(team.equals(managed))continue;
            for(Position role:Position.values()) {
                List<String> held=members.values().stream().filter(m->team.equals(m.ownerTeam())&&player(m.playerId()).position()==role&&eligible(m.playerId(),team,date)).map(CareerRosterStore.Membership::playerId).toList();
                boolean safe=held.stream().anyMatch(id->{var c=active(id,date);return c.terms().endDate().isAfter(date.plusDays(NEGOTIATION_DAYS))||scheduled(id)!=null&&team.equals(scheduled(id).team());});
                if(offers.values().stream().anyMatch(o->team.equals(o.team())&&o.open()&&player(o.playerId()).position()==role))continue;
                if(contracts.values().stream().anyMatch(c->team.equals(c.team())&&c.status()==ContractStatus.SCHEDULED&&player(c.playerId()).position()==role))continue;
                int best=held.stream().map(this::player).mapToInt(CareerMarketPolicy::strength).max().orElse(0);
                final boolean need=!safe;
                var candidates=directory.players().values().stream().filter(p->p.position()==role&&availableStart(p.playerId(),date)!=null)
                        .filter(p->!safe || held.size()<MAX_POSITION_PLAYERS&&strength(p)>best+AI_IMPROVEMENT_POINTS)
                        .filter(p->!offers.values().stream().anyMatch(o->o.playerId().equals(p.playerId())&&o.team().equals(team)&&!date.isAfter(o.decisionDate())))
                        .sorted(Comparator.comparingInt((Definition p)->strength(p)+(held.contains(p.playerId())?AI_RENEWAL_ADVANTAGE:0)).reversed().thenComparing(Definition::playerId))
                        .limit(AI_CANDIDATES).toList();
                for(var p:candidates) {
                    LocalDate start=availableStart(p.playerId(),date);long salary=demand(p)*(AI_MIN_BID_PERCENT+variation(seed,"AI_BID|"+team+'|'+p.playerId()+'|'+date,AI_BID_VARIANTS))/100;
                    Role promise=need?Role.STARTER:Role.RESERVE;
                    try{submit(team,p.playerId(),new Terms(start,start.plusYears(AI_CONTRACT_YEARS).minusDays(1),salary,demand(p)/AI_BONUS_DIVISOR,promise),null,date);break;}
                    catch(CareerException noBudgetOrEligibility){ /* Try the next bounded candidate under identical spending rules. */ }
                }
            }
        }
    }
    private void aiLineups(LocalDate date) {
        for(String team:accounts.keySet())if(!team.equals(managed))for(Position role:Position.values()) {
            String selected=members.values().stream().filter(m->team.equals(m.ownerTeam())&&player(m.playerId()).position()==role&&eligible(m.playerId(),team,date))
                    .map(CareerRosterStore.Membership::playerId).sorted(Comparator.comparingInt((String p)->strength(player(p))).reversed().thenComparing(p->p)).findFirst().orElse(null);
            if(selected!=null&&!lineups.get(team).contains(selected))select(team,selected,date);
        }
    }
    private void credit(String team,LocalDate date) {
        String id=id(career,"ALLOCATION|"+team+'|'+date.getYear());if(ledger.stream().anyMatch(l->l.entryId().equals(id)))return;
        var a=accounts.get(team);accounts.put(team,new Account(team,a.annualBudget(),Math.addExact(a.cash(),a.annualBudget()),a.rosterLimit()));
        ledger.add(new Ledger(id,date,team,null,"ANNUAL_ALLOCATION",a.annualBudget()));
    }
    private void charge(String team,String contract,String kind,LocalDate date,long amount) {
        if(team==null)return;
        String id=id(career,kind+'|'+contract+'|'+date);if(ledger.stream().anyMatch(l->l.entryId().equals(id)))return;
        var a=accounts.get(team);if(a.cash()<amount)throw invalid("급여 또는 계약 비용 지급 잔액이 부족합니다.");
        accounts.put(team,new Account(team,a.annualBudget(),a.cash()-amount,a.rosterLimit()));ledger.add(new Ledger(id,date,team,contract,kind,-amount));
    }
    private void pay(Contract c,LocalDate date) {
        LocalDate end=date.isBefore(c.terms().endDate())?date:c.terms().endDate();
        if(!end.isAfter(c.paidThrough()))return;
        charge(c.team(),c.contractId(),"SALARY",end,wages(c.terms().annualSalary(),c.paidThrough().plusDays(1),end));
        contracts.put(c.contractId(),contractStatus(c,c.status(),c.endedDate(),end));
    }
    private void event(LocalDate date,String kind,String player,String team,String reference,String reason) {
        String id=id(career,kind+'|'+reference+'|'+date);
        if(events.stream().noneMatch(e->e.eventId().equals(id)))events.add(new Event(id,date,kind,player,team,reference,reason));
    }
    public void validateIntegrity() {
        CareerRosterStore.validate(roster(),directory);
        for(var a:accounts.values())requireBudget(a.team());
        var live=contracts.values().stream().filter(c->c.status()==ContractStatus.ACTIVE||c.status()==ContractStatus.SCHEDULED).toList();
        for(int i=0;i<live.size();i++)for(int j=i+1;j<live.size();j++)if(live.get(i).playerId().equals(live.get(j).playerId())&&overlaps(live.get(i).terms(),live.get(j).terms()))throw new IllegalStateException("OVERLAPPING_EMPLOYMENT_CONTRACTS");
        for(var c:live)if(c.status()==ContractStatus.ACTIVE&&!Objects.equals(c.team(),members.get(c.playerId()).ownerTeam()))throw new IllegalStateException("CONTRACT_MEMBERSHIP_MISMATCH");
        if(ledger.stream().map(Ledger::entryId).distinct().count()!=ledger.size())throw new IllegalStateException("DUPLICATE_MARKET_PAYMENT");
    }
}
