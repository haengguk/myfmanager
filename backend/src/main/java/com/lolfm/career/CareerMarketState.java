package com.lolfm.career;

import java.time.LocalDate;
import java.util.*;

/** Persisted Career-owned operating state, separate from research metadata and frozen match inputs. */
public record CareerMarketState(String policyVersion, long seed, LocalDate processedThrough,
        Map<String, Contract> contracts, Map<String, Offer> offers, Map<String, Account> accounts,
        Map<String, Preference> preferences, Set<String> freeAgents, List<Ledger> ledger,
        Map<String, Decision> decisions, List<Event> events, CareerManagementState management) {
    public CareerMarketState {
        if (!CareerMarketPolicy.VERSION.equals(policyVersion)) throw new IllegalArgumentException("MARKET_POLICY");
        contracts=Map.copyOf(contracts); offers=Map.copyOf(offers); accounts=Map.copyOf(accounts);
        preferences=Map.copyOf(preferences); freeAgents=Collections.unmodifiableSet(new TreeSet<>(freeAgents)); ledger=ledger.stream().sorted(Comparator.comparing(Ledger::date).thenComparing(Ledger::entryId)).toList();
        decisions=Map.copyOf(decisions); events=events.stream().sorted(Comparator.comparing(Event::date).thenComparing(Event::eventId)).toList();
    }
    public CareerMarketState(String policyVersion,long seed,LocalDate processedThrough,Map<String,Contract> contracts,
            Map<String,Offer> offers,Map<String,Account> accounts,Map<String,Preference> preferences,Set<String> freeAgents,
            List<Ledger> ledger,Map<String,Decision> decisions,List<Event> events) {
        this(policyVersion,seed,processedThrough,contracts,offers,accounts,preferences,freeAgents,ledger,decisions,events,null);
    }
    public enum Role { STARTER, RESERVE, DEVELOPMENT }
    public enum ContractStatus { ACTIVE, SCHEDULED, EXPIRED, RELEASED, TRANSFERRED, RETIRED, CANCELLED_RETIREMENT }
    public enum OfferStatus { SUBMITTED, COUNTER, ACCEPTED, REJECTED, WITHDRAWN, EXPIRED, SUPERSEDED }
    public record Terms(LocalDate startDate, LocalDate endDate, long annualSalary, long signingBonus, Role role) {}
    public record Contract(String contractId, String careerId, String playerId, String team, String organizationId,
            LocalDate signedDate, Terms terms, ContractStatus status, long revision, String policyVersion,
            String origin, String terminationPolicy, LocalDate endedDate, LocalDate paidThrough) {}
    public record Offer(String offerId, String playerId, String team, Terms terms, LocalDate submittedDate,
            LocalDate responseDate, LocalDate decisionDate, LocalDate expiresDate, long revision,
            OfferStatus status, String previousOfferId, int round, Long requestedSalary, String reason) {
        public boolean open() { return status==OfferStatus.SUBMITTED || status==OfferStatus.COUNTER; }
    }
    public record Account(String team, long annualBudget, long cash, int rosterLimit) {}
    public record Preference(int compensation, int opportunity, int strength, int stability, int familiarity,
            int relocationPenalty, String inclination, String homeRegion) {}
    public record Evaluation(String offerId, String team, int compensation, int opportunity, int strength,
            int stability, int familiarity, int relocation, long score, int tieBreak, String reason,
            int promiseTrust,int satisfaction,long relationshipAdjustment) {}
    public record Decision(String eventId, String playerId, LocalDate date, String winningOfferId,
            List<Evaluation> evaluations, String reason, String policyVersion) {
        public Decision { evaluations=List.copyOf(evaluations); }
    }
    public record Ledger(String entryId, LocalDate date, String team, String contractId, String kind, long amount) {}
    public record Event(String eventId, LocalDate date, String kind, String playerId, String team, String referenceId, String reason) {}
}
