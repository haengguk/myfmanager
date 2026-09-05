package com.lolfm.career;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Persisted registration and the derived bracket, scoped to one Career competition. */
public record CareerInternationalState(
        String careerId, int year, String competitionId, String ruleVersion, String ruleResourceHash,
        String policyVersion, String selectionPolicy, String inputEvidence, long drawSeed,
        List<Entry> entries, CompetitionRosterSnapshot rosters, List<String> regionOrder,
        CareerInternationalTournament.Plan plan) {
    public CareerInternationalState {
        CareerIdentity.requireCareerId(careerId);
        if (!CareerInternationalRules.COMPETITIONS.contains(competitionId) || year < 2026)
            throw new IllegalArgumentException("INTERNATIONAL_SCOPE");
        if (!CareerInternationalRules.VERSION.equals(ruleVersion)
                || !CareerInternationalRules.RESOURCE_HASH.equals(ruleResourceHash)
                || !CareerInternationalRules.POLICY.equals(policyVersion))
            throw new IllegalArgumentException("INTERNATIONAL_RULE_AUTHORITY");
        entries = List.copyOf(entries); regionOrder = List.copyOf(regionOrder);
        if (entries.stream().map(Entry::team).distinct().count() != entries.size()
                || entries.stream().map(e -> e.region() + ':' + e.regionalSeed()).distinct().count() != entries.size()
                || !rosters.teams().keySet().equals(entries.stream().map(Entry::team).collect(java.util.stream.Collectors.toSet())))
            throw new IllegalArgumentException("INTERNATIONAL_ENTRANT_IDENTITY");
        entries.forEach(e -> { if (!rosters.roster(e.team()).team().leagueCode().equals(e.region()))
            throw new IllegalArgumentException("INTERNATIONAL_REGION_IDENTITY"); });
    }
    public CareerInternationalState withPlan(CareerInternationalTournament.Plan value) {
        return new CareerInternationalState(careerId, year, competitionId, ruleVersion, ruleResourceHash,
                policyVersion, selectionPolicy, inputEvidence, drawSeed, entries, rosters, regionOrder, value);
    }
    public record Entry(String team, String region, int regionalSeed, int pool, String phase, String qualification) {
        public Entry {
            Objects.requireNonNull(team); Objects.requireNonNull(region); Objects.requireNonNull(qualification);
            if (regionalSeed < 1 || pool < 1 || pool > 4 || !List.of("MAIN", "PLAY_IN").contains(phase))
                throw new IllegalArgumentException("INTERNATIONAL_ENTRY");
        }
        Entry withPool(int value) { return new Entry(team, region, regionalSeed, value, phase, qualification); }
    }
}
