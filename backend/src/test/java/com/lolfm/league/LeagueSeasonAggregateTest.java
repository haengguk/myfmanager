package com.lolfm.league;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LeagueSeasonAggregateTest {
    @Test
    void hybridAndSpectatorModesBindManagedSnapshotExactly() {
        LeagueSeasonFrozenSnapshot snapshot = LeagueDomainTestFixtures.snapshot();
        LeagueSeasonAggregate hybrid = LeagueSeasonAggregate.create(
                LeagueDomainTestFixtures.seasonId(),
                LeagueSeasonMode.HYBRID_MANAGER,
                "GEN",
                snapshot.teamSnapshotIdentity("GEN"),
                snapshot,
                LeagueDomainTestFixtures.ROOT_SEED,
                LeagueSchedulePolicy.productionDefault());
        LeagueSeasonAggregate spectator = LeagueSeasonAggregate.create(
                LeagueDomainTestFixtures.seasonId(),
                LeagueSeasonMode.SPECTATOR_FULL_AUTO,
                null,
                null,
                snapshot,
                LeagueDomainTestFixtures.ROOT_SEED,
                LeagueSchedulePolicy.productionDefault());

        assertThat(hybrid.managedTeamCode()).isEqualTo("GEN");
        assertThat(hybrid.managedTeamSnapshotIdentity()).isEqualTo(
                snapshot.teamSnapshotIdentity("GEN"));
        assertThat(hybrid.aiJobExcludedFixtureIds()).hasSize(18);
        assertThat(hybrid.schedule().rounds()).allSatisfy(round ->
                assertThat(round.fixtures().stream().filter(value -> value.executionMode()
                        == LeagueFixtureExecutionMode.PLAYER_CONTROLLED)).hasSize(1));
        assertThat(spectator.managedTeamCode()).isNull();
        assertThat(spectator.managedTeamSnapshotIdentity()).isNull();
        assertThat(spectator.aiJobExcludedFixtureIds()).isEmpty();
        assertThat(hybrid.productDecisionHash()).isEqualTo(
                LeagueV1ProductDecisions.productDecisionHash());

        assertThatThrownBy(() -> LeagueSeasonAggregate.create(
                LeagueDomainTestFixtures.seasonId(), LeagueSeasonMode.HYBRID_MANAGER,
                "GEN", LeagueDomainTestFixtures.hash("wrong-team-snapshot"), snapshot,
                73L, LeagueSchedulePolicy.productionDefault()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("snapshot identity mismatch");
        assertThatThrownBy(() -> LeagueSeasonAggregate.create(
                LeagueDomainTestFixtures.seasonId(),
                LeagueSeasonMode.SPECTATOR_FULL_AUTO,
                "GEN", snapshot.teamSnapshotIdentity("GEN"), snapshot, 73L,
                LeagueSchedulePolicy.productionDefault()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifiedCompletionIsAppliedExactlyOnceAndAdvancesRevisionOnce() {
        LeagueSeasonFrozenSnapshot snapshot = LeagueDomainTestFixtures.snapshot();
        LeagueSeasonAggregate season = LeagueSeasonAggregate.create(
                LeagueDomainTestFixtures.seasonId(),
                LeagueSeasonMode.SPECTATOR_FULL_AUTO, null, null, snapshot, 73L,
                LeagueSchedulePolicy.productionDefault());
        VerifiedLeagueFixtureCompletion completion =
                LeagueDomainTestFixtures.completion(season.schedule(), "GEN", "T1",
                        "GEN", 2, 1, "gen-t1-leg-one");

        LeagueSeasonAggregate applied = season.applyVerifiedCompletion(completion);
        LeagueSeasonAggregate replayed = applied.applyVerifiedCompletion(completion);

        assertThat(season.revision()).isZero();
        assertThat(applied.revision()).isEqualTo(1);
        assertThat(replayed).isSameAs(applied);
        assertThat(replayed.revision()).isEqualTo(1);
        assertThat(replayed.standings().appliedFixtureCount()).isEqualTo(1);
        assertThat(replayed.standings().rows().get("GEN"))
                .isEqualTo(new LeagueStanding("GEN", 1, 0, 2, 1));
        assertThat(replayed.standings().rows().get("T1"))
                .isEqualTo(new LeagueStanding("T1", 0, 1, 1, 2));
    }

    @Test
    void conflictingDuplicateCrossFixtureReceiptAndInvalidScoreAreRejected() {
        LeagueSeasonFrozenSnapshot snapshot = LeagueDomainTestFixtures.snapshot();
        LeagueSeasonAggregate season = LeagueSeasonAggregate.create(
                LeagueDomainTestFixtures.seasonId(),
                LeagueSeasonMode.SPECTATOR_FULL_AUTO, null, null, snapshot, 73L,
                LeagueSchedulePolicy.productionDefault());
        VerifiedLeagueFixtureCompletion first = LeagueDomainTestFixtures.completion(
                season.schedule(), "GEN", "T1", "GEN", 2, 0, "receipt-a");
        LeagueSeasonAggregate applied = season.applyVerifiedCompletion(first);
        LeagueFixture sameFixture = season.schedule().fixture(first.fixtureId());
        VerifiedLeagueFixtureCompletion conflict = LeagueDomainTestFixtures.opaqueCompletion(
                sameFixture.fixtureId(), LeagueDomainTestFixtures.hash("receipt-b"),
                "T1", "GEN", 2, 1);
        LeagueFixture otherFixture = LeagueDomainTestFixtures.fixture(
                season.schedule(), "DK", "HLE");
        VerifiedLeagueFixtureCompletion reusedReceipt =
                LeagueDomainTestFixtures.opaqueCompletion(otherFixture.fixtureId(),
                        first.canonicalFixtureReceiptHash(), "DK", "HLE", 2, 0);
        VerifiedLeagueFixtureCompletion wrongTeams =
                LeagueDomainTestFixtures.opaqueCompletion(otherFixture.fixtureId(),
                        LeagueDomainTestFixtures.hash("wrong-teams"),
                        "GEN", "T1", 2, 0);
        VerifiedLeagueFixtureCompletion incomplete =
                LeagueDomainTestFixtures.opaqueCompletion(otherFixture.fixtureId(),
                        LeagueDomainTestFixtures.hash("incomplete"),
                        "DK", "HLE", 1, 0);

        assertThatThrownBy(() -> applied.applyVerifiedCompletion(conflict))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different receipt");
        assertThatThrownBy(() -> applied.applyVerifiedCompletion(reusedReceipt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already applied");
        assertThatThrownBy(() -> season.applyVerifiedCompletion(wrongTeams))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("frozen fixture");
        assertThatThrownBy(() -> season.applyVerifiedCompletion(incomplete))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("frozen fixture");
        assertThat(applied.revision()).isEqualTo(1);
        assertThat(applied.standings().appliedFixtureCount()).isEqualTo(1);
    }
}
