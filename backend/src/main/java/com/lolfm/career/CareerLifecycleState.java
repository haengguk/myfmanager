package com.lolfm.career;

import java.time.LocalDate;
import java.util.*;

/** Career-owned life history, separate from placement and immutable authoring data. */
public record CareerLifecycleState(String policyVersion,LocalDate appliedOn,Integer lastReviewedSeason,Map<String,Person> players) {
    public CareerLifecycleState { players=Map.copyOf(players); }
    public enum Status { ACTIVE,CONSIDERING_RETIREMENT,RETIREMENT_ANNOUNCED,RETIRED }
    public record Age(LocalDate publicBirthDate,LocalDate simulationBirthDate,String basis,LocalDate referenceDate,LocalDate appliedOn) {}
    public record Person(Age age,String source,Integer intakeYear,LocalDate introducedOn,int peakCA,LocalDate peakObservedSince,
            Status status,LocalDate announcedOn,LocalDate effectiveOn,String reason,int declineRemainder,int declineCursor,
            int noAppearanceSeasons,Integer lastReviewYear,String lastCoverage,LocalDate freeAgentSince,LocalDate domesticObservedSince) {
        public Person(Age age,String source,Integer intakeYear,LocalDate introducedOn,int peakCA,LocalDate peakObservedSince,Status status,LocalDate announcedOn,LocalDate effectiveOn,String reason,int declineRemainder,int declineCursor,int noAppearanceSeasons,Integer lastReviewYear,String lastCoverage,LocalDate freeAgentSince) {this(age,source,intakeYear,introducedOn,peakCA,peakObservedSince,status,announcedOn,effectiveOn,reason,declineRemainder,declineCursor,noAppearanceSeasons,lastReviewYear,lastCoverage,freeAgentSince,null);}
        public Person peak(int ca) {return new Person(age,source,intakeYear,introducedOn,Math.max(peakCA,ca),peakObservedSince,status,announcedOn,effectiveOn,reason,declineRemainder,declineCursor,noAppearanceSeasons,lastReviewYear,lastCoverage,freeAgentSince,domesticObservedSince);}
        public Person freeAgent(LocalDate since) {return new Person(age,source,intakeYear,introducedOn,peakCA,peakObservedSince,status,announcedOn,effectiveOn,reason,declineRemainder,declineCursor,noAppearanceSeasons,lastReviewYear,lastCoverage,since,domesticObservedSince);}
        public Person domesticSince(LocalDate since) {return new Person(age,source,intakeYear,introducedOn,peakCA,peakObservedSince,status,announcedOn,effectiveOn,reason,declineRemainder,declineCursor,since==null?0:noAppearanceSeasons,lastReviewYear,lastCoverage,freeAgentSince,since);}
        public Person appeared() {return new Person(age,source,intakeYear,introducedOn,peakCA,peakObservedSince,status,announcedOn,effectiveOn,reason,declineRemainder,declineCursor,0,lastReviewYear,lastCoverage,freeAgentSince,domesticObservedSince);}
        public Person retired() {return new Person(age,source,intakeYear,introducedOn,peakCA,peakObservedSince,Status.RETIRED,announcedOn,effectiveOn,reason,declineRemainder,declineCursor,noAppearanceSeasons,lastReviewYear,lastCoverage,null,null);}
    }
    public record Observation(String coverage,int opportunities,int starts,int sets,LocalDate from,LocalDate through) {
        public boolean full(){return "FULL_DOMESTIC_SEASON".equals(coverage);}
    }
    public record Change(String playerId,int age,int beforeCA,int afterCA,int declineBudget,int appliedDecline,int carriedDecline,int limitedDecline,
            Map<String,Integer> signedSkillDeltas,Observation observation,int retirementProbability,int retirementRoll,String outcome,
            LocalDate announcementDate,LocalDate effectiveDate,String reason) {public Change {signedSkillDeltas=Map.copyOf(signedSkillDeltas);}}
    public record Supply(int projectedActive,int normalCount,int emergencyCount,Map<String,Integer> availableByPosition,
            Map<String,Integer> missingByPosition,Map<String,Integer> generatedByPosition,Map<String,Integer> unresolvedByPosition) {
        public Supply {availableByPosition=Map.copyOf(availableByPosition);missingByPosition=Map.copyOf(missingByPosition);generatedByPosition=Map.copyOf(generatedByPosition);unresolvedByPosition=Map.copyOf(unresolvedByPosition);}
    }
    public record Review(int seasonYear,LocalDate reviewedOn,String policyVersion,String kind,List<Change> changes,List<String> rookieIds,Supply supply) {
        public Review {changes=List.copyOf(changes);rookieIds=List.copyOf(rookieIds);}
    }
}
