package com.lolfm.career;

import java.time.LocalDate;
import java.util.*;
import static com.lolfm.career.CareerManagementState.*;
import static com.lolfm.career.CareerManagementPolicy.*;
import static com.lolfm.career.CareerMarketState.*;
import static com.lolfm.career.CareerMarketPolicy.*;

/** Negotiation and loan lifecycle inside the existing Calendar-serialized market transaction. */
final class CareerTrades {
    private final CareerMarketEngine m;
    final Map<String,Trade> trades=new TreeMap<>();
    final Map<String,Loan> loans=new TreeMap<>();
    CareerTrades(CareerMarketEngine market,CareerManagementState state) {m=market;trades.putAll(state.trades());loans.putAll(state.loans());}
    Loan loan(String player,LocalDate date) {return loans.values().stream().filter(l->l.playerId().equals(player)&&!date.isBefore(l.startDate())&&!date.isAfter(l.endDate())&&"ACTIVE".equals(l.status())).findFirst().orElse(null);}
    boolean hasAgreement(String player) {return trades.values().stream().anyMatch(t->t.terms().playerId().equals(player)&&t.status()==TradeStatus.AGREED);}
    LocalDate decision(String player,LocalDate date) {return trades.values().stream().filter(t->t.open()&&t.terms().playerId().equals(player)&&!t.decisionDate().isBefore(date)).map(Trade::decisionDate).min(LocalDate::compareTo).orElse(date.plusDays(TRADE_DECISION_DAYS));}
    String unavailable(String player,LocalDate date) {
        if(m.lifecycle!=null&&m.lifecycle.announced(player))return "은퇴 발표 선수는 새 이적·임대 대상이 아닙니다.";
        var c=m.active(player,date);
        if(c==null||c.team()==null)return "경쟁 구단의 유효한 원계약이 필요합니다.";
        if(m.scheduled(player)!=null)return "후속 계약이 확정되어 양도할 수 없습니다.";
        if(loan(player,date)!=null)return "임대 중 재임대·완전 이적·조기 복귀는 지원하지 않습니다.";
        if(loans.values().stream().anyMatch(l->l.playerId().equals(player)&&"RETURNED".equals(l.status())&&date.isBefore(l.endDate().plusDays(1+RECONTACT_DAYS))))return "임대 복귀 후 재거래 유예 기간입니다.";
        if(hasAgreement(player))return "이미 적용 예정인 거래가 있습니다.";
        if(m.members.get(player).eligibilityReason()!=null)return "현재 소속·등록 역할 검토가 필요합니다.";
        if(trades.values().stream().anyMatch(t->t.terms().playerId().equals(player)&&t.status()==TradeStatus.COMPLETED&&date.isBefore(t.terms().startDate().plusDays(RECONTACT_DAYS))))return "이동 직후 재거래 유예 기간입니다.";
        return null;
    }
    long estimate(String player,LocalDate date) {var c=m.active(player,date);return c==null?0:value(m.demand(player,date),date,c.terms().endDate());}
    String replacement(String team,String player,LocalDate date) {
        return m.members.values().stream().filter(p->!p.playerId().equals(player)&&team.equals(p.ownerTeam())&&"FIRST_TEAM".equals(p.squad())
                &&m.player(p.playerId()).position()==m.player(player).position()&&m.eligible(p.playerId(),team,date))
                .map(CareerRosterStore.Membership::playerId).sorted(Comparator.comparingInt((String p)->strength(m.player(p))).reversed().thenComparing(p->p)).findFirst().orElse(null);
    }
    long demandFee(TradeTerms t,LocalDate date) {return demandFee(t,date,m.demand(t.playerId(),date));}
    private long demandFee(TradeTerms t,LocalDate date,long salary) {
        long base=t.kind()==Kind.TRANSFER?value(salary,date,m.active(t.playerId(),date).terms().endDate()):loanFee(salary,t.startDate(),t.endDate());
        int percent=SELL_BASE_PERCENT+(m.lineups.get(t.seller()).contains(t.playerId())?SELL_STARTER_PREMIUM:0)
                -(m.promiseEngine.mood(t.playerId(),date)<=MOVE_INTEREST?SELL_UNHAPPY_DISCOUNT:0)-(m.salaryArrears(t.seller())>0?SELL_ARREARS_DISCOUNT:0);
        return Math.multiplyExact(base,percent)/100;
    }
    long buyerLimit(TradeTerms t,LocalDate date) {return buyerLimit(t,date,m.demand(t.playerId(),date));}
    private long buyerLimit(TradeTerms t,LocalDate date,long salary) {
        var held=m.lineups.get(t.buyer()).stream().filter(p->m.player(p).position()==m.player(t.playerId()).position()).findFirst();
        int percent=held.isEmpty()?BUY_URGENT_PERCENT:strength(m.player(t.playerId()))>strength(m.player(held.get()))?BUY_IMPROVEMENT_PERCENT:BUY_BASE_PERCENT;
        long base=t.kind()==Kind.TRANSFER?value(salary,date,m.active(t.playerId(),date).terms().endDate()):loanFee(salary,t.startDate(),t.endDate());
        return Math.min(Math.multiplyExact(base,percent)/100,Math.max(0,m.paymentHeadroom(t.buyer(),date)));
    }
    long reserved(String team) {return trades.values().stream().filter(t->t.open()&&t.buyerAgreed()&&t.terms().buyer().equals(team))
            .mapToLong(t->Math.addExact(t.terms().fee(),t.terms().kind()==Kind.TRANSFER?t.terms().playerTerms().signingBonus():0)).sum();}
    List<Trade> obligations(String team) {return trades.values().stream().filter(t->t.open()&&t.buyerAgreed()&&t.terms().buyer().equals(team)).toList();}
    void validate(TradeTerms t,LocalDate date,LocalDate decision) {
        if(t==null||t.kind()==null||t.playerId()==null||t.seller()==null||t.buyer()==null||!m.accounts.containsKey(t.seller())||!m.accounts.containsKey(t.buyer())||t.seller().equals(t.buyer()))throw invalid("서로 다른 경쟁 구단과 선수를 지정해 주세요.");
        var c=m.active(t.playerId(),date);String why=unavailable(t.playerId(),date);if(why!=null)throw invalid(why);
        if(!t.seller().equals(c.team()))throw invalid("원계약 소유 구단과 판매 구단이 다릅니다.");
        if(t.startDate()==null||t.endDate()==null||t.startDate().isBefore(decision.plusDays(1))||t.startDate().isAfter(decision.plusDays(FA_START_DELAY_DAYS))||t.startDate().isAfter(c.terms().endDate()))throw invalid("공통 결정 다음 날부터 7일 안이며 원계약이 유효한 적용일이 필요합니다.");
        if(t.fee()<0||t.fee()>MAX_MONEY)throw invalid("거래 금액 범위를 확인해 주세요.");
        if(t.kind()==Kind.TRANSFER) {
            CareerMarketPolicy.validate(t.playerTerms());
            if(!t.playerTerms().startDate().equals(t.startDate())||!t.playerTerms().endDate().equals(t.endDate())||t.borrowerSalaryPercent()!=0)throw invalid("이적 계약 기간과 적용일을 일치시켜 주세요.");
        } else {
            long days=java.time.temporal.ChronoUnit.DAYS.between(t.startDate(),t.endDate())+1;
            if(days<LOAN_MIN_DAYS||days>LOAN_MAX_DAYS||t.endDate().isAfter(c.terms().endDate())||t.borrowerSalaryPercent()<0||t.borrowerSalaryPercent()>100
                    ||t.playerTerms()==null||t.playerTerms().role()==null||t.playerTerms().annualSalary()!=c.terms().annualSalary()||t.playerTerms().signingBonus()!=0
                    ||!t.startDate().equals(t.playerTerms().startDate())||!t.endDate().equals(t.playerTerms().endDate()))throw invalid("임대는 28~366일, 원계약 종료 이내, 급여 분담 0~100%이며 원계약 급여를 유지해야 합니다.");
        }
        if(t.playerTerms().role()==Role.DEVELOPMENT&&m.developmentOrganization(t.buyer())==null)throw invalid("연계 육성 조직이 없는 구단입니다.");
    }
    Trade submit(String actor,TradeTerms terms,String previousId,LocalDate date) {
        if(terms==null||terms.seller()==null||terms.buyer()==null||!List.of(terms.seller(),terms.buyer()).contains(actor))throw invalid("거래 당사자만 제안할 수 있습니다.");
        Trade previous=previousId==null?null:trades.get(previousId);LocalDate decision=previous==null?decision(terms.playerId(),date):previous.decisionDate();
        int round=previous==null?1:previous.round()+1;
        if(previousId!=null&&(previous==null||!previous.open()||previous.status()==TradeStatus.AGREED||!previous.terms().playerId().equals(terms.playerId())
                ||!previous.terms().seller().equals(terms.seller())||!previous.terms().buyer().equals(terms.buyer())||round>TRADE_MAX_ROUNDS||!date.isBefore(decision)))throw invalid("수정 가능한 원본 거래와 결정 기간을 확인해 주세요.");
        if(previous==null&&trades.values().stream().anyMatch(t->t.terms().playerId().equals(terms.playerId())&&t.terms().buyer().equals(terms.buyer())
                &&(t.open()||date.isBefore(t.decisionDate().plusDays(RECONTACT_DAYS)))))throw invalid("기존 거래를 수정하거나 재접촉 유예 기간을 기다려 주세요.");
        validate(terms,date,decision);
        var pricing=previous==null?m.quote(terms.playerId(),date):previous.pricing();
        long salary=m.negotiationDemand(terms.playerId(),pricing);
        long wanted=demandFee(terms,date,salary),limit=buyerLimit(terms,date,salary);
        String id=CareerMarketEngine.id(m.career,"TRADE|"+actor+'|'+terms.playerId()+'|'+terms.buyer()+'|'+decision+'|'+round);
        LocalDate response=date.plusDays(TRADE_RESPONSE_DAYS);
        if(response.isAfter(decision))response=decision;
        var t=new Trade(id,m.active(terms.playerId(),date).contractId(),actor,terms,date,response,decision,decision.plusDays(1),round,previousId,
                previous==null?TradeStatus.CLUB_PENDING:TradeStatus.CLUB_COUNTER,actor.equals(terms.seller()),actor.equals(terms.buyer()),value(salary,date,m.active(terms.playerId(),date).terms().endDate()),wanted,limit,null,"상대 구단 조건 검토 대기",CareerManagementPolicy.VERSION,null,pricing);
        if(previous!=null)trades.put(previousId,status(previous,TradeStatus.SUPERSEDED,"새 조건으로 대체",null));trades.put(id,t);
        try {if(t.sellerAgreed())requireSeller(t,date);if(t.buyerAgreed())requireBuyer(t,date);}
        catch(RuntimeException e){trades.remove(id);if(previous!=null)trades.put(previousId,previous);throw e;}
        return t;
    }
    Trade respond(String actor,String id,String action,String replacement,LocalDate date) {
        Trade t=trades.get(id);if(t==null||!t.open()||t.status()==TradeStatus.AGREED||!List.of(t.terms().seller(),t.terms().buyer()).contains(actor))throw invalid("응답 가능한 당사자 거래가 아닙니다.");
        if(action.equals("WITHDRAW")||action.equals("REJECT")){t=status(t,action.equals("WITHDRAW")?TradeStatus.WITHDRAWN:TradeStatus.REJECTED,"구단이 "+(action.equals("WITHDRAW")?"철회":"거절")+"했습니다. 예약 해제",null);trades.put(id,t);return t;}
        if(!action.equals("ACCEPT")||date.isAfter(t.decisionDate()))throw invalid("공통 결정일까지 구단 응답이 필요합니다.");
        validate(t.terms(),date,t.decisionDate());
        Trade prior=t;TradeTerms terms=t.terms();
        if(actor.equals(terms.seller())&&replacement!=null)terms=new TradeTerms(terms.kind(),terms.playerId(),terms.seller(),terms.buyer(),terms.startDate(),terms.endDate(),terms.fee(),terms.borrowerSalaryPercent(),terms.playerTerms(),replacement);
        boolean seller=t.sellerAgreed()||actor.equals(terms.seller()),buyer=t.buyerAgreed()||actor.equals(terms.buyer());
        t=new Trade(t.tradeId(),t.contractId(),t.proposer(),terms,t.submittedDate(),t.responseDate(),t.decisionDate(),t.expiresDate(),t.round(),t.previousTradeId(),seller&&buyer?TradeStatus.PLAYER_PENDING:t.status(),seller,buyer,t.referenceValue(),t.sellerDemand(),t.buyerLimit(),null,"구단 동의 기록 · 선수 동의는 공통 결정일에 별도 평가",t.policyVersion(),null,t.pricing());
        trades.put(id,t);try{if(seller)requireSeller(t,date);if(buyer)requireBuyer(t,date);}catch(RuntimeException e){trades.put(id,prior);throw e;}return t;
    }
    private void requireSeller(Trade t,LocalDate date) {
        var terms=t.terms();var c=m.active(terms.playerId(),date);
        if(c==null||!c.contractId().equals(t.contractId())||m.scheduled(c.playerId())!=null||loan(c.playerId(),date)!=null)throw invalid("원계약 또는 임대 상태가 바뀌었습니다. 재협상해 주세요.");
        if(m.lineups.get(terms.seller()).contains(terms.playerId())||terms.replacementPlayerId()!=null)m.requireReplacement(terms.seller(),terms.playerId(),terms.replacementPlayerId(),date);
        if(m.clEnabled&&terms.seller().startsWith("LCK:")&&!m.planner.canDepart(terms.seller(),terms.playerId(),date))throw invalid("현재/다음 경기의 1군·CL 대체자 또는 등록 자격이 부족합니다.");
        if(m.lineups.get(terms.seller()).size()!=5)throw invalid("판매 구단의 적법한 5인 선발을 먼저 확보해야 합니다.");
    }
    private void requireBuyer(Trade t,LocalDate date) {
        m.requireBudget(t.terms().buyer(),date);
        m.requireRosterCapacity(t.terms().buyer(),t.terms().playerId(),t.terms().playerTerms());
    }
    void cancelForRetirement(String player,LocalDate date) {
        for(var t:new ArrayList<>(trades.values()))if(t.terms().playerId().equals(player)&&t.open()) {
            trades.put(t.tradeId(),status(t,TradeStatus.CANCELLED_RETIREMENT,"은퇴 발표로 거래 취소 · 미지급 이적료/임대료 예약 해제",null));
            m.event(date,"RETIREMENT_TRADE_CANCELLED",player,t.terms().buyer(),t.tradeId(),"거래 효력 전 예약 해제 · 아직 지급되지 않은 비용은 환불 장부를 만들지 않음");
        }
    }
    private Trade status(Trade t,TradeStatus status,String reason,Long score) {return new Trade(t.tradeId(),t.contractId(),t.proposer(),t.terms(),t.submittedDate(),t.responseDate(),t.decisionDate(),t.expiresDate(),t.round(),t.previousTradeId(),status,t.sellerAgreed(),t.buyerAgreed(),t.referenceValue(),t.sellerDemand(),t.buyerLimit(),score,reason,t.policyVersion(),t.playerEvaluation()!=null?t.playerEvaluation():score==null?null:evaluation(t,t.decisionDate()),t.pricing());}
    private Evaluation evaluation(Trade t,LocalDate date) {
        var terms=t.terms();var offer=new Offer(t.tradeId(),terms.playerId(),terms.buyer(),terms.playerTerms(),t.submittedDate(),t.responseDate(),t.decisionDate(),t.expiresDate(),0,OfferStatus.SUBMITTED,null,1,null,"",t.pricing());
        return m.evaluate(offer,date);
    }
    void process(LocalDate date) {
        // Due club responses run before the same day's common player decision.
        for(var t:new ArrayList<>(trades.values()))if(t.open()&&t.status()!=TradeStatus.AGREED&&!date.isBefore(t.responseDate())&&!date.isAfter(t.decisionDate())) {
            try {
                if(!t.sellerAgreed()&&!t.terms().seller().equals(m.managed)) {
                    if(t.terms().fee()<t.sellerDemand())respond(t.terms().seller(),t.tradeId(),"REJECT",null,date);
                    else respond(t.terms().seller(),t.tradeId(),"ACCEPT",m.lineups.get(t.terms().seller()).contains(t.terms().playerId())?replacement(t.terms().seller(),t.terms().playerId(),date):null,date);
                }
                t=trades.get(t.tradeId());
                if(t.open()&&!t.buyerAgreed()&&!t.terms().buyer().equals(m.managed))respond(t.terms().buyer(),t.tradeId(),t.terms().fee()<=t.buyerLimit()?"ACCEPT":"REJECT",null,date);
            }catch(CareerException rejected){trades.put(t.tradeId(),status(t,TradeStatus.REJECTED,rejected.clientMessage(),null));}
        }
        var players=new TreeSet<String>();trades.values().stream().filter(t->t.open()&&t.status()!=TradeStatus.AGREED&&t.decisionDate().equals(date)).forEach(t->players.add(t.terms().playerId()));
        for(String player:players) {
            var candidates=trades.values().stream().filter(t->t.open()&&t.terms().playerId().equals(player)&&t.decisionDate().equals(date)).toList();
            var ranked=candidates.stream().filter(t->t.status()==TradeStatus.PLAYER_PENDING).sorted(Comparator.comparingLong((Trade t)->evaluation(t,date).score()).reversed()
                    .thenComparing(t->t.terms().buyer())).toList();String winner=null;
            for(var t:ranked) {
                var e=evaluation(t,date);long threshold=t.terms().kind()==Kind.LOAN?LOAN_ACCEPT_SCORE:MIN_ACCEPT_SCORE;
                if(e.score()<threshold||t.terms().playerTerms().annualSalary()*100<m.negotiationDemand(player,t.pricing())*MIN_SALARY_PERCENT){trades.put(t.tradeId(),status(t,TradeStatus.REJECTED,"선수가 보수·역할·기회·약속 신뢰·이동 부담을 비교해 거절: "+e.reason(),e.score()));continue;}
                try{requireSeller(t,date);requireBuyer(t,date);winner=t.tradeId();trades.put(winner,status(t,TradeStatus.AGREED,"구단과 선수 동의 완료 · 적용일 최종 검사 대기: "+e.reason(),e.score()));break;}
                catch(CareerException rejected){trades.put(t.tradeId(),status(t,TradeStatus.REJECTED,rejected.clientMessage(),e.score()));}
            }
            for(var t:candidates)if(!t.tradeId().equals(winner)&&trades.get(t.tradeId()).open())trades.put(t.tradeId(),status(t,TradeStatus.REJECTED,winner==null?"구단/선수 합의 미완료 · 예약 해제":"선수가 다른 유효 거래를 선택했습니다. 예약 해제",null));
        }
        for(var t:new ArrayList<>(trades.values()))if(t.status()==TradeStatus.AGREED&&!date.isBefore(t.terms().startDate())) {
            try{complete(t,date);}catch(CareerException rejected){trades.put(t.tradeId(),status(t,TradeStatus.REJECTED,"적용일 재검사 실패: "+rejected.clientMessage(),t.playerScore()));}
        }
    }
    private void complete(Trade t,LocalDate date) {
        if(!date.equals(t.terms().startDate()))throw invalid("적용일을 지난 거래는 소급할 수 없습니다.");
        requireSeller(t,date);requireBuyer(t,date);var terms=t.terms();var c=m.contracts.get(t.contractId());
        // All eligibility and budget checks precede mutation. The caller commits both clubs and the receipt together.
        m.payThrough(c,date,date.minusDays(1));c=m.contracts.get(c.contractId());
        m.charge(terms.buyer(),t.tradeId(),terms.kind()==Kind.TRANSFER?"TRANSFER_FEE_PAID":"LOAN_FEE_PAID",date,terms.fee());
        m.receive(terms.seller(),t.tradeId(),terms.kind()==Kind.TRANSFER?"TRANSFER_FEE_RECEIVED":"LOAN_FEE_RECEIVED",date,terms.fee());
        c=m.contracts.get(c.contractId());
        m.promiseEngine.close(c.playerId(),terms.seller(),date.minusDays(1));
        boolean wasSelected=m.lineups.get(terms.seller()).remove(c.playerId());
        if(wasSelected&&terms.replacementPlayerId()!=null)m.select(terms.seller(),terms.replacementPlayerId(),date);
        if(terms.kind()==Kind.TRANSFER) {
            m.contracts.put(c.contractId(),m.contractStatus(c,ContractStatus.TRANSFERRED,date.minusDays(1),c.paidThrough()));
            String contract=CareerMarketEngine.id(m.career,"TRANSFER_CONTRACT|"+t.tradeId());
            var next=new Contract(contract,m.career,c.playerId(),terms.buyer(),terms.buyer(),date,terms.playerTerms(),ContractStatus.SCHEDULED,0,CareerMarketPolicy.VERSION,"PAID_TRANSFER_AGREEMENT","REMAINING_SALARY_25_PERCENT_V1",null,date.minusDays(1));
            m.members.put(c.playerId(),new CareerRosterStore.Membership(c.playerId(),null,null,"UNAFFILIATED","TRANSFER_ACTIVATION_PENDING"));
            m.contracts.put(contract,next);m.charge(terms.buyer(),contract,"SIGNING_BONUS",date,terms.playerTerms().signingBonus());m.activate(next,date);
        } else {
            var original=m.members.get(c.playerId());String id=CareerMarketEngine.id(m.career,"LOAN|"+t.tradeId());
            var l=new Loan(id,t.tradeId(),c.contractId(),c.playerId(),terms.seller(),terms.buyer(),date,terms.endDate(),terms.fee(),terms.borrowerSalaryPercent(),terms.playerTerms().role(),original.organizationId(),original.squad(),"ACTIVE",CareerManagementPolicy.VERSION);
            loans.put(id,l);String org=l.role()==Role.DEVELOPMENT?m.developmentOrganization(terms.buyer()):terms.buyer();
            m.members.put(c.playerId(),new CareerRosterStore.Membership(c.playerId(),terms.buyer(),org,l.role()==Role.DEVELOPMENT?"DEVELOPMENT":"FIRST_TEAM",null));
            m.promiseEngine.ensure(c,terms.buyer(),id,l.role(),date,l.endDate());
        }
        trades.put(t.tradeId(),status(t,TradeStatus.COMPLETED,"거래 완료 · 금전/계약/운영 소속 원자 적용 · 선발은 별도",t.playerScore()));
        for(var other:new ArrayList<>(trades.values()))if(other.open()&&other.terms().playerId().equals(c.playerId()))trades.put(other.tradeId(),status(other,TradeStatus.REJECTED,"동일 선수 거래 완료로 예약 해제",null));
        for(var o:new ArrayList<>(m.offers.values()))if(o.open()&&o.playerId().equals(c.playerId()))m.offers.put(o.offerId(),m.offerStatus(o,OfferStatus.REJECTED,"이동 합의로 원계약/개인 조건 재확인 필요",null));
        m.event(date,"TRADE_COMPLETED",c.playerId(),terms.buyer(),t.tradeId(),"양 구단 장부와 출전 권한 적용");
    }
    void returns(LocalDate date) {
        for(var l:new ArrayList<>(loans.values()))if("ACTIVE".equals(l.status())&&date.isAfter(l.endDate())) {
            var c=m.contracts.get(l.contractId());m.payThrough(c,date,l.endDate());
            m.lineups.get(l.borrowingTeam()).remove(l.playerId());m.promiseEngine.close(l.playerId(),l.borrowingTeam(),l.endDate());
            loans.put(l.loanId(),new Loan(l.loanId(),l.tradeId(),l.contractId(),l.playerId(),l.parentTeam(),l.borrowingTeam(),l.startDate(),l.endDate(),l.fee(),l.borrowerSalaryPercent(),l.role(),l.returnOrganization(),l.returnSquad(),"RETURNED",l.policyVersion()));
            // Parent ownership reserves a slot throughout the loan; returning never replaces its selected starter.
            m.members.put(l.playerId(),new CareerRosterStore.Membership(l.playerId(),l.parentTeam(),l.returnOrganization(),l.returnSquad(),null));
            if(!date.isAfter(c.terms().endDate()))m.promiseEngine.ensure(c,l.parentTeam(),null,c.terms().role(),date,c.terms().endDate());
            m.event(date,"LOAN_RETURNED",l.playerId(),l.parentTeam(),l.loanId(),"임대 종료 · 원계약 만료 시 같은 날짜 만료/후속 계약 경로 적용");
        }
    }
    Set<LocalDate> nextDates(LocalDate current) {
        var dates=new TreeSet<LocalDate>();for(var t:trades.values())if(t.open()){dates.add(t.responseDate());dates.add(t.decisionDate());dates.add(t.terms().startDate());}
        for(var l:loans.values())if("ACTIVE".equals(l.status()))dates.add(l.endDate().plusDays(1));
        for(var p:m.promiseEngine.promises.values())if(!p.endDate().isBefore(current))dates.add(p.lastEvaluation().plusDays(EVALUATION_DAYS));return dates;
    }
    TradeTerms plannedTerms(String buyer,String player,Role role,LocalDate date) {
        var c=m.active(player,date);if(c==null||unavailable(player,date)!=null)return null;
        LocalDate start=decision(player,date).plusDays(1),end=start.plusYears(AI_CONTRACT_YEARS).minusDays(1);
        String replacement=m.lineups.get(c.team()).contains(player)?replacement(c.team(),player,date):null;
        var personal=new Terms(start,end,m.demand(player,date)*AI_MAX_BID_PERCENT/100,m.demand(player,date)/AI_BONUS_DIVISOR,role);
        var terms=new TradeTerms(Kind.TRANSFER,player,c.team(),buyer,start,end,0,0,personal,replacement);
        long fee=demandFee(terms,date);
        if(fee>buyerLimit(terms,date)||m.paymentHeadroom(buyer,date)<fee+personal.signingBonus()) {
            end=start.plusDays(LOAN_MAX_DAYS-1);if(end.isAfter(c.terms().endDate()))end=c.terms().endDate();
            if(java.time.temporal.ChronoUnit.DAYS.between(start,end)+1<LOAN_MIN_DAYS)return null;
            personal=new Terms(start,end,c.terms().annualSalary(),0,role);
            terms=new TradeTerms(Kind.LOAN,player,c.team(),buyer,start,end,0,LOAN_SHARE_PERCENT,personal,replacement);fee=demandFee(terms,date);
        }
        return fee<=buyerLimit(terms,date)?new TradeTerms(terms.kind(),player,c.team(),buyer,start,end,fee,terms.borrowerSalaryPercent(),personal,replacement):null;
    }
    TradeTerms plannedLoanTerms(String buyer,String player,Role role,LocalDate date) {
        var c=m.active(player,date);if(c==null||unavailable(player,date)!=null)return null;
        LocalDate start=decision(player,date).plusDays(1),end=start.plusDays(LOAN_MAX_DAYS-1);if(end.isAfter(c.terms().endDate()))end=c.terms().endDate();
        if(java.time.temporal.ChronoUnit.DAYS.between(start,end)+1<LOAN_MIN_DAYS)return null;
        String replacement=m.lineups.get(c.team()).contains(player)?replacement(c.team(),player,date):null;
        var personal=new Terms(start,end,c.terms().annualSalary(),0,role);
        var t=new TradeTerms(Kind.LOAN,player,c.team(),buyer,start,end,0,LOAN_SHARE_PERCENT,personal,replacement);long fee=demandFee(t,date);
        return fee<=buyerLimit(t,date)?new TradeTerms(Kind.LOAN,player,c.team(),buyer,start,end,fee,LOAN_SHARE_PERCENT,personal,replacement):null;
    }
    private static CareerException invalid(String reason) {return CareerException.invalid("trade",reason);}
}
