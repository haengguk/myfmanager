package com.lolfm.league;

import java.util.List;
import java.util.Objects;

/** Immutable V1 Season aggregate for frozen schedule and exactly-once standings changes. */
public final class LeagueSeasonAggregate {
    private final String leagueId;
    private final String seasonId;
    private final long revision;
    private final LeagueSeasonMode seasonMode;
    private final String managedTeamCode;
    private final String managedTeamSnapshotIdentity;
    private final LeagueSeasonFrozenSnapshot frozenSnapshot;
    private final long seasonRootSeed;
    private final String productDecisionHash;
    private final LeagueSchedule schedule;
    private final LeagueStandings standings;

    private LeagueSeasonAggregate(
            String leagueId,
            String seasonId,
            long revision,
            LeagueSeasonMode seasonMode,
            String managedTeamCode,
            String managedTeamSnapshotIdentity,
            LeagueSeasonFrozenSnapshot frozenSnapshot,
            long seasonRootSeed,
            String productDecisionHash,
            LeagueSchedule schedule,
            LeagueStandings standings
    ) {
        LeagueIdentity.requireLeagueId(leagueId);
        LeagueIdentity.requireSeasonId(seasonId);
        if (revision < 0) {
            throw new IllegalArgumentException("Season revision cannot be negative");
        }
        this.leagueId = leagueId;
        this.seasonId = seasonId;
        this.revision = revision;
        this.seasonMode = Objects.requireNonNull(seasonMode, "seasonMode");
        this.managedTeamCode = managedTeamCode;
        this.managedTeamSnapshotIdentity = managedTeamSnapshotIdentity;
        this.frozenSnapshot = Objects.requireNonNull(frozenSnapshot, "frozenSnapshot");
        this.seasonRootSeed = seasonRootSeed;
        if (!LeagueV1ProductDecisions.productDecisionHash().equals(productDecisionHash)) {
            throw new IllegalArgumentException("Season product decision identity mismatch");
        }
        this.productDecisionHash = productDecisionHash;
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.standings = Objects.requireNonNull(standings, "standings");
        validateModeAndSnapshot();
        if (!seasonId.equals(schedule.seasonId())
                || seasonRootSeed != schedule.seasonRootSeed()
                || seasonMode != schedule.seasonMode()
                || !Objects.equals(managedTeamCode, schedule.managedTeamCode())
                || !frozenSnapshot.teamSnapshotIdentities().keySet()
                .equals(java.util.Set.copyOf(schedule.teamCodes()))
                || !standings.rows().keySet().equals(
                frozenSnapshot.teamSnapshotIdentities().keySet())) {
            throw new IllegalArgumentException("Season aggregate binding invariant");
        }
    }

    public static LeagueSeasonAggregate create(
            String leagueId,
            String seasonId,
            LeagueSeasonMode seasonMode,
            String managedTeamCode,
            String managedTeamSnapshotIdentity,
            LeagueSeasonFrozenSnapshot frozenSnapshot,
            long seasonRootSeed,
            LeagueSchedulePolicy schedulePolicy
    ) {
        Objects.requireNonNull(frozenSnapshot, "frozenSnapshot");
        LeagueSchedule schedule = new LeagueScheduleGenerator().generate(
                seasonId, seasonRootSeed, frozenSnapshot.teamSnapshotIdentities().keySet(),
                seasonMode, managedTeamCode, schedulePolicy);
        LeagueStandings standings = LeagueStandings.empty(schedule.teamCodes());
        return new LeagueSeasonAggregate(leagueId, seasonId, 0, seasonMode, managedTeamCode,
                managedTeamSnapshotIdentity, frozenSnapshot, seasonRootSeed,
                LeagueV1ProductDecisions.productDecisionHash(), schedule, standings);
    }

    public LeagueSeasonAggregate applyVerifiedCompletion(
            VerifiedLeagueFixtureCompletion completion
    ) {
        LeagueStandings next = standings.apply(schedule, completion);
        if (next == standings) {
            return this;
        }
        return new LeagueSeasonAggregate(leagueId, seasonId,
                Math.addExact(revision, 1), seasonMode,
                managedTeamCode, managedTeamSnapshotIdentity, frozenSnapshot,
                seasonRootSeed, productDecisionHash, schedule, next);
    }

    public String leagueId() {
        return leagueId;
    }

    public String seasonId() {
        return seasonId;
    }

    public long revision() {
        return revision;
    }

    public LeagueSeasonMode seasonMode() {
        return seasonMode;
    }

    public String managedTeamCode() {
        return managedTeamCode;
    }

    public String managedTeamSnapshotIdentity() {
        return managedTeamSnapshotIdentity;
    }

    public LeagueSeasonFrozenSnapshot frozenSnapshot() {
        return frozenSnapshot;
    }

    public long seasonRootSeed() {
        return seasonRootSeed;
    }

    public String productDecisionHash() {
        return productDecisionHash;
    }

    public LeagueSchedule schedule() {
        return schedule;
    }

    public LeagueStandings standings() {
        return standings;
    }

    public LeagueRanking ranking() {
        return standings.ranking(seasonRootSeed);
    }

    public List<String> aiJobExcludedFixtureIds() {
        return schedule.fixtures().stream()
                .filter(value -> value.executionMode()
                        == LeagueFixtureExecutionMode.PLAYER_CONTROLLED)
                .map(LeagueFixture::fixtureId)
                .sorted()
                .toList();
    }

    private void validateModeAndSnapshot() {
        if (seasonMode == LeagueSeasonMode.HYBRID_MANAGER) {
            LeagueIdentity.requireTeamCode(managedTeamCode);
            if (!frozenSnapshot.teamSnapshotIdentities().containsKey(managedTeamCode)
                    || !Objects.equals(managedTeamSnapshotIdentity,
                    frozenSnapshot.teamSnapshotIdentity(managedTeamCode))) {
                throw new IllegalArgumentException(
                        "Hybrid managed team snapshot identity mismatch");
            }
        } else if (managedTeamCode != null || managedTeamSnapshotIdentity != null) {
            throw new IllegalArgumentException(
                    "Spectator full-auto season cannot own a managed team snapshot");
        }
        if (managedTeamSnapshotIdentity != null) {
            LeagueSeasonFrozenSnapshot.requireSha256(managedTeamSnapshotIdentity,
                    "managedTeamSnapshotIdentity");
        }
    }
}
