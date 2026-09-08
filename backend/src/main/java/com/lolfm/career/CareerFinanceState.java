package com.lolfm.career;

import java.time.LocalDate;
import java.util.*;

/** Frozen reference amounts and Career-owned accounting; no real bank balances or personal salaries claimed. */
public record CareerFinanceState(String policyVersion,String currency,int referenceSeason,String scenario,String sourceHash,
        Map<String,String> sourceHashes,String fxPolicyVersion,Map<String,String> fx,LocalDate introducedOn,LocalDate operatingThrough,
        boolean legacyTransition,LocalDate recurringEffectiveOn,Map<String,Team> teams,Map<String,Price> prices,
        Map<String,PrizeRule> prizeRules,Map<String,Approval> approvals,Map<String,Target> targets,Map<String,Award> awards,
        Set<String> excludedInstances,Map<String,Long> operatingArrears,Map<String,String> heldPrizes) {
    public CareerFinanceState {
        if(!CareerFinancePolicy.VERSION.equals(policyVersion)||!"KRW".equals(currency))throw new IllegalArgumentException("FINANCE_POLICY");
        sourceHashes=Map.copyOf(sourceHashes);fx=Map.copyOf(fx);teams=Map.copyOf(teams);prices=Map.copyOf(prices);prizeRules=Map.copyOf(prizeRules);
        approvals=Map.copyOf(approvals);targets=Map.copyOf(targets);awards=Map.copyOf(awards);excludedInstances=Set.copyOf(excludedInstances);operatingArrears=Map.copyOf(operatingArrears);heldPrizes=Map.copyOf(heldPrizes);
    }
    public record Team(String team,String sourceCurrency,String confidence,String evidenceClass,List<String> sourceIds,
            Map<String,Long> originalBase,long playerCompensation,long operatingBudget,long nonWage,long contingency,
            long wageLimit,long openingCash,long protectedCash,long includedTransferAllowance,long allocatedSalary,long unallocatedCompensation) {
        public Team {sourceIds=List.copyOf(sourceIds);originalBase=Map.copyOf(originalBase);}
    }
    public record Price(long referenceSalary,int referenceStrength,String region,String role,String policy){}
    public record Placement(int from,int through,long amount){}
    public record PrizeRule(String eventId,String currency,String evidenceStatus,boolean complete,List<Placement> placements,List<String> sourceIds){public PrizeRule{placements=List.copyOf(placements);sourceIds=List.copyOf(sourceIds);}}
    public record Approval(String team,int seasonYear,LocalDate effectiveOn,long annualIncome,long annualSupport,long annualSponsor,
            long annualNonWage,long wageLimit,long inheritedCommitments,long overCommitted,String evaluation,String policy){}
    public record Target(String team,int seasonYear,LocalDate setOn,boolean partial,int maximumDomesticRank,Integer worldsMaximumRank,
            long fixedSponsor,long initialWageLimit,String sportingStatus,String financeStatus,Integer actualDomesticRank,Integer actualWorldsRank,
            long closingCash,long closingHeadroom,long arrears,long bonus,LocalDate evaluatedOn,String evidence){}
    public record Award(String id,int seasonYear,String competition,String eventId,String team,String awardType,int placementFrom,int placementThrough,
            String originalCurrency,long originalAmount,String evidenceStatus,String allocationPolicy,String fxPolicy,String rate,long krw,
            LocalDate recognizedOn,LocalDate dueOn,LocalDate paidOn,String resultHash,String referenceHash){}
}
