package com.lolfm.league;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.player.LckTeamAssembler;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeagueV1ProductDecisionsTest {
    @Test
    void productDecisionOrderAndCanonicalHashAreCodeOwned() {
        assertThat(LeagueV1ProductDecisions.orderedDecisionIds()).containsExactly(
                LeagueV1ProductDecisions.SEASON_MODE,
                LeagueV1ProductDecisions.MANAGED_FIXTURE_POLICY,
                LeagueV1ProductDecisions.ROSTER_SNAPSHOT,
                LeagueV1ProductDecisions.SCHEDULE_FORMAT,
                LeagueV1ProductDecisions.STANDINGS_POLICY,
                LeagueV1ProductDecisions.BLOCKED_FIXTURE_POLICY,
                LeagueV1ProductDecisions.EXECUTION_LIMITS,
                LeagueV1ProductDecisions.PERSISTENCE_POLICY,
                LeagueV1ProductDecisions.PLAYER_SERIES_HANDOFF);
        assertThat(LeagueV1ProductDecisions.canonicalValues()).hasSize(9);
        assertThat(LeagueV1ProductDecisions.canonicalText())
                .doesNotEndWith("\n")
                .hasLineCount(9);
        assertThat(LeagueV1ProductDecisions.productDecisionHash())
                .isEqualTo("81a4755760fb513c5803d55dd4855c03fda487114bb7c89b431c959a00a0fb14");
    }

    @Test
    void operationalDefaultsAreCentralizedAndExact() {
        LeagueV1OperationalConfiguration value =
                LeagueV1OperationalConfiguration.defaults();

        assertThat(value.configurationId()).isEqualTo(
                LeagueV1OperationalConfiguration.CONFIGURATION_ID);
        assertThat(value.activeSeasonTeamCount()).isEqualTo(10);
        assertThat(value.doubleRoundRobinFixtureCount()).isEqualTo(90);
        assertThat(value.defaultMaxParallelFixtures()).isEqualTo(2);
        assertThat(value.hardMaxParallelFixtures()).isEqualTo(4);
        assertThat(value.fixtureLease()).isEqualTo(Duration.ofMinutes(15));
        assertThat(value.heartbeatInterval()).isEqualTo(Duration.ofSeconds(15));
        assertThat(value.transientTotalAttempts()).isEqualTo(2);
        assertThat(value.jobAttemptLogRetention()).isEqualTo(Duration.ofDays(30));
        assertThat(value.optionalFullReplayCacheRetention()).isEqualTo(
                Duration.ofHours(24));
        assertThatThrownBy(() -> new LeagueV1OperationalConfiguration(
                LeagueV1OperationalConfiguration.CONFIGURATION_ID,
                10, 90, 3, 4, Duration.ofMinutes(15), Duration.ofSeconds(15), 2,
                Duration.ofDays(30), Duration.ofHours(24)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void frozenSnapshotIsCanonicalAndRejectsIncompleteOrInvalidIdentity() {
        LeagueSeasonFrozenSnapshot first = LeagueDomainTestFixtures.snapshot();
        LinkedHashMap<String, String> reordered = new LinkedHashMap<>();
        LeagueDomainTestFixtures.TEAM_CODES.forEach(team ->
                reordered.put(team, first.teamSnapshotIdentity(team)));
        LeagueSeasonFrozenSnapshot second = new LeagueSeasonFrozenSnapshot(reordered,
                first.playerResourceIdentity(), first.championDraftResourceIdentity(),
                first.matchupCompositionResourceIdentity(),
                first.productionRuntimeIdentity());

        assertThat(second.snapshotIdentity()).isEqualTo(first.snapshotIdentity());
        assertThat(second.teamSnapshotIdentities().keySet())
                .containsExactlyElementsOf(LeagueDomainTestFixtures.TEAM_CODES);

        reordered.remove("T1");
        assertThatThrownBy(() -> new LeagueSeasonFrozenSnapshot(reordered,
                first.playerResourceIdentity(), first.championDraftResourceIdentity(),
                first.matchupCompositionResourceIdentity(),
                first.productionRuntimeIdentity()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 10");
        assertThatThrownBy(() -> new LeagueSeasonFrozenSnapshot(
                LeagueDomainTestFixtures.teamSnapshots(), "not-a-hash",
                first.championDraftResourceIdentity(),
                first.matchupCompositionResourceIdentity(),
                first.productionRuntimeIdentity()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SHA-256");
    }

    @Test
    void currentAuthoritativeLckMembershipMatchesTheFrozenV1Envelope() {
        List<String> actual = LckTeamAssembler.loadDefault().teamCodes().stream()
                .sorted().toList();

        assertThat(actual).containsExactlyElementsOf(LeagueDomainTestFixtures.TEAM_CODES);
        assertThat(new LckTeamAssemblerTestView().assembledPlayerCount()).isEqualTo(50);
    }

    private static final class LckTeamAssemblerTestView {
        private final LckTeamAssembler assembler = LckTeamAssembler.loadDefault();

        int assembledPlayerCount() {
            return assembler.assembleAll().stream()
                    .mapToInt(team -> team.getPlayers().size()).sum();
        }
    }
}
