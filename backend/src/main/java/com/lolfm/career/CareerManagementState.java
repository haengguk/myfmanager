package com.lolfm.career;

import java.time.LocalDate;
import java.util.*;
import com.lolfm.domain.Position;
import static com.lolfm.career.CareerMarketState.*;

/** Career-owned persistent facts and agreements. Membership.ownerTeam remains the operating team. */
public record CareerManagementState(String policyVersion,LocalDate observationStarted,
        Map<String,Promise> promises,Map<String,Trade> trades,Map<String,Loan> loans,Map<String,Appearance> appearances) {
    public CareerManagementState {
        if(!CareerManagementPolicy.VERSION.equals(policyVersion))throw new IllegalArgumentException("MANAGEMENT_POLICY");
        promises=Map.copyOf(promises);trades=Map.copyOf(trades);loans=Map.copyOf(loans);appearances=Map.copyOf(appearances);
    }
    public static CareerManagementState empty(LocalDate date) {return new CareerManagementState(CareerManagementPolicy.VERSION,date,Map.of(),Map.of(),Map.of(),Map.of());}
    public record Promise(String promiseId,String playerId,String team,String contractId,String loanId,Role role,
            LocalDate startDate,LocalDate endDate,LocalDate observationStart,LocalDate lastEvaluation,
            int opportunities,int starts,int sets,int satisfaction,int trust,String status,String reason,String policyVersion,int evaluatedOpportunities) {}
    public enum Kind { TRANSFER, LOAN }
    public enum TradeStatus { CLUB_PENDING, CLUB_COUNTER, PLAYER_PENDING, AGREED, COMPLETED, REJECTED, WITHDRAWN, EXPIRED, SUPERSEDED, CANCELLED_RETIREMENT }
    public record TradeTerms(Kind kind,String playerId,String seller,String buyer,LocalDate startDate,
            LocalDate endDate,long fee,int borrowerSalaryPercent,Terms playerTerms,String replacementPlayerId) {}
    public record Trade(String tradeId,String contractId,String proposer,TradeTerms terms,LocalDate submittedDate,
            LocalDate responseDate,LocalDate decisionDate,LocalDate expiresDate,int round,String previousTradeId,
            TradeStatus status,boolean sellerAgreed,boolean buyerAgreed,long referenceValue,long sellerDemand,
            long buyerLimit,Long playerScore,String reason,String policyVersion,Evaluation playerEvaluation) {
        public boolean open() {return status==TradeStatus.CLUB_PENDING||status==TradeStatus.CLUB_COUNTER||status==TradeStatus.PLAYER_PENDING||status==TradeStatus.AGREED;}
    }
    public record Loan(String loanId,String tradeId,String contractId,String playerId,String parentTeam,String borrowingTeam,
            LocalDate startDate,LocalDate endDate,long fee,int borrowerSalaryPercent,Role role,String returnOrganization,
            String returnSquad,String status,String policyVersion) {}
    public record Opportunity(String playerId,String team,Position position,String promiseId,boolean eligible,
            boolean selected,String reason) {}
    public record Appearance(String completionId,String fixtureId,String seriesId,int seasonYear,LocalDate date,
            int completedSets,List<Opportunity> opportunities,String squad,String competitionId) {
        public Appearance(String completionId,String fixtureId,String seriesId,int seasonYear,LocalDate date,int completedSets,List<Opportunity> opportunities){this(completionId,fixtureId,seriesId,seasonYear,date,completedSets,opportunities,"FIRST_TEAM",null);}
        public Appearance {opportunities=List.copyOf(opportunities);squad=squad==null?"FIRST_TEAM":squad;}
    }
}
