package com.lolfm.league;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.application.SeriesFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class LeagueScheduleGeneratorTest {
    @Test
    void productionDefaultCreatesTenTeamNinetyFixtureMirroredSchedule() {
        LeagueSchedule schedule = LeagueDomainTestFixtures.schedule(
                LeagueSeasonMode.HYBRID_MANAGER, "GEN",
                LeagueDomainTestFixtures.ROOT_SEED,
                LeagueSchedulePolicy.productionDefault());

        assertThat(schedule.teamCodes()).containsExactlyElementsOf(
                LeagueDomainTestFixtures.TEAM_CODES);
        assertThat(schedule.rounds()).hasSize(18);
        assertThat(schedule.fixtures()).hasSize(90);
        assertThat(schedule.fixtures()).allSatisfy(fixture ->
                assertThat(fixture.seriesFormat()).isEqualTo(SeriesFormat.BO3));
        assertThat(schedule.fixtures().stream().map(LeagueFixture::fixtureId))
                .doesNotHaveDuplicates();
        assertThat(schedule.fixtures().stream().map(LeagueFixture::fixtureRootSeed))
                .doesNotHaveDuplicates();

        schedule.rounds().forEach(round -> {
            assertThat(round.fixtures()).hasSize(5);
            assertThat(round.fixtures().stream().flatMap(value ->
                    value.teamCodes().stream())).containsExactlyInAnyOrderElementsOf(
                    LeagueDomainTestFixtures.TEAM_CODES);
            assertThat(round.fixtures().stream().filter(value ->
                    value.containsTeam("GEN"))).hasSize(1);
        });

        Map<String, List<LeagueFixture>> byPair = schedule.fixtures().stream()
                .collect(Collectors.groupingBy(LeagueFixture::pairId));
        assertThat(byPair).hasSize(45);
        byPair.values().forEach(fixtures -> {
            assertThat(fixtures).hasSize(2);
            LeagueFixture first = fixtures.stream().filter(value ->
                    value.legNumber() == 1).findFirst().orElseThrow();
            LeagueFixture second = fixtures.stream().filter(value ->
                    value.legNumber() == 2).findFirst().orElseThrow();
            assertThat(second.game1BlueTeamCode()).isEqualTo(
                    first.game1RedTeamCode());
            assertThat(second.game1RedTeamCode()).isEqualTo(
                    first.game1BlueTeamCode());
        });

        assertThat(schedule.fixtures().stream().filter(value -> value.executionMode()
                == LeagueFixtureExecutionMode.PLAYER_CONTROLLED)).hasSize(18);
        assertThat(schedule.fixtures().stream().filter(value -> value.executionMode()
                == LeagueFixtureExecutionMode.FULL_AUTO)).hasSize(72);
    }

    @Test
    void spectatorAndHybridExecutionModeDoNotChangeFixtureOrSeedIdentity() {
        LeagueSchedule hybrid = LeagueDomainTestFixtures.schedule(
                LeagueSeasonMode.HYBRID_MANAGER, "GEN",
                LeagueDomainTestFixtures.ROOT_SEED,
                LeagueSchedulePolicy.productionDefault());
        LeagueSchedule spectator = LeagueDomainTestFixtures.schedule(
                LeagueSeasonMode.SPECTATOR_FULL_AUTO, null,
                LeagueDomainTestFixtures.ROOT_SEED,
                LeagueSchedulePolicy.productionDefault());

        assertThat(hybrid.scheduleIdentity()).isEqualTo(spectator.scheduleIdentity());
        for (int index = 0; index < hybrid.fixtures().size(); index++) {
            LeagueFixture playerCandidate = hybrid.fixtures().get(index);
            LeagueFixture fullAuto = spectator.fixtures().get(index);
            assertThat(playerCandidate.fixtureId()).isEqualTo(fullAuto.fixtureId());
            assertThat(playerCandidate.fixtureRootSeed())
                    .isEqualTo(fullAuto.fixtureRootSeed());
            assertThat(playerCandidate.boundSeriesId()).isEqualTo(fullAuto.boundSeriesId());
            assertThat(playerCandidate.gameSeed(1, "0".repeat(64)))
                    .isEqualTo(fullAuto.gameSeed(1, "0".repeat(64)));
        }
        assertThat(spectator.fixtures()).allSatisfy(fixture ->
                assertThat(fixture.executionMode()).isEqualTo(
                        LeagueFixtureExecutionMode.FULL_AUTO));
    }

    @Test
    void gameSideAlternatesAndFixtureRootIsPassedOnceIntoSeriesSeedChain() {
        LeagueSchedule first = LeagueDomainTestFixtures.schedule();
        LeagueSchedule differentRoot = LeagueDomainTestFixtures.schedule(
                LeagueSeasonMode.SPECTATOR_FULL_AUTO, null, 74L,
                LeagueSchedulePolicy.productionDefault());
        LeagueFixture fixture = first.fixtures().getFirst();
        LeagueFixture changed = differentRoot.fixture(fixture.fixtureId());

        assertThat(fixture.blueTeamCode(2)).isEqualTo(fixture.game1RedTeamCode());
        assertThat(fixture.redTeamCode(2)).isEqualTo(fixture.game1BlueTeamCode());
        assertThat(fixture.blueTeamCode(3)).isEqualTo(fixture.game1BlueTeamCode());
        assertThat(fixture.gameSeed(1, "0".repeat(64)))
                .isNotEqualTo(fixture.gameSeed(2, "0".repeat(64)));
        assertThat(differentRoot.scheduleIdentity()).isEqualTo(first.scheduleIdentity());
        assertThat(changed.fixtureRootSeed()).isNotEqualTo(fixture.fixtureRootSeed());
        assertThat(changed.boundSeriesId()).isNotEqualTo(fixture.boundSeriesId());
        assertThat(changed.gameSeed(1, "0".repeat(64)))
                .isNotEqualTo(fixture.gameSeed(1, "0".repeat(64)));
        assertThat(LeagueIdentity.FIXTURE_ROOT_SEED_ALGORITHM).isEqualTo(
                "AI_LEAGUE_FIXTURE_ROOT_SEED_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1");
        assertThat(LeagueIdentity.GAME_SEED_ALGORITHM).isEqualTo(
                "AI_LEAGUE_BOUND_SERIES_GAME_SEED_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1");
    }

    @Test
    void canonicalInputOrderIsDeterministicAndSingleRoundRobinRemainsDesignable() {
        ArrayList<String> reversed = new ArrayList<>(LeagueDomainTestFixtures.TEAM_CODES);
        java.util.Collections.reverse(reversed);
        LeagueSchedule first = new LeagueScheduleGenerator().generate(
                LeagueDomainTestFixtures.seasonId(), LeagueDomainTestFixtures.ROOT_SEED,
                reversed, LeagueSeasonMode.SPECTATOR_FULL_AUTO, null,
                LeagueSchedulePolicy.productionDefault());
        LeagueSchedule second = LeagueDomainTestFixtures.schedule();
        LeagueSchedule single = LeagueDomainTestFixtures.schedule(
                LeagueSeasonMode.SPECTATOR_FULL_AUTO, null,
                LeagueDomainTestFixtures.ROOT_SEED,
                LeagueSchedulePolicy.singleRoundRobinDesign());

        assertThat(first.scheduleIdentity()).isEqualTo(second.scheduleIdentity());
        assertThat(first.fixtures()).containsExactlyElementsOf(second.fixtures());
        assertThat(single.rounds()).hasSize(9);
        assertThat(single.fixtures()).hasSize(45);
        assertThat(single.fixtures().stream().map(LeagueFixture::pairId))
                .doesNotHaveDuplicates();
    }

    @Test
    void invalidMembershipModeAndGameBoundaryFailBeforeMutationOrRandom() {
        LeagueScheduleGenerator generator = new LeagueScheduleGenerator();
        List<String> nine = LeagueDomainTestFixtures.TEAM_CODES.subList(0, 9);
        List<String> duplicate = new ArrayList<>(LeagueDomainTestFixtures.TEAM_CODES);
        duplicate.set(9, duplicate.getFirst());

        assertThatThrownBy(() -> generator.generate(LeagueDomainTestFixtures.seasonId(),
                73L, nine, LeagueSeasonMode.SPECTATOR_FULL_AUTO, null,
                LeagueSchedulePolicy.productionDefault()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.generate(LeagueDomainTestFixtures.seasonId(),
                73L, duplicate, LeagueSeasonMode.SPECTATOR_FULL_AUTO, null,
                LeagueSchedulePolicy.productionDefault()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.generate(LeagueDomainTestFixtures.seasonId(),
                73L, LeagueDomainTestFixtures.TEAM_CODES,
                LeagueSeasonMode.HYBRID_MANAGER, null,
                LeagueSchedulePolicy.productionDefault()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.generate(LeagueDomainTestFixtures.seasonId(),
                73L, LeagueDomainTestFixtures.TEAM_CODES,
                LeagueSeasonMode.HYBRID_MANAGER, "XYZ",
                LeagueSchedulePolicy.productionDefault()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LeagueDomainTestFixtures.schedule().fixtures().getFirst()
                .gameSeed(0, "0".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LeagueDomainTestFixtures.schedule().fixtures().getFirst()
                .gameSeed(1, "bad"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
