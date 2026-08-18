package com.lolfm.champion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.composition.ChampionCompositionProfile;
import com.lolfm.domain.Position;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.TeamSide;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChampionRoleScopeCorrectionTest {
    private static final ChampionResourceSet RESOURCES = ChampionResourceSet.loadDefault();
    private static final ChampionSelectionValidator SELECTIONS =
            new ChampionSelectionValidator(RESOURCES.catalog());
    private static final Set<ChampionRoleKey> EXPECTED_ADDED_ROLES = Set.of(
            key("varus", Position.TOP),
            key("anivia", Position.TOP),
            key("cassiopeia", Position.ADC),
            key("taliyah", Position.ADC));

    @Test
    void catalogAddsExactlyTheFourRequestedLegalRoleKeys() {
        assertThat(RESOURCES.catalog().all()).hasSize(173);
        assertThat(RESOURCES.catalog().legalRoleKeys()).hasSize(216)
                .containsAll(EXPECTED_ADDED_ROLES)
                .doesNotContain(
                        key("anivia", Position.SUPPORT),
                        key("skarner", Position.SUPPORT),
                        key("rumble", Position.MID),
                        key("annie", Position.TOP),
                        key("sylas", Position.ADC),
                        key("syndra", Position.ADC));

        assertThat(RESOURCES.catalog().get(new ChampionId("varus")).supportedPositions())
                .containsExactlyInAnyOrder(Position.TOP, Position.ADC);
        assertThat(RESOURCES.catalog().get(new ChampionId("anivia")).supportedPositions())
                .containsExactlyInAnyOrder(Position.TOP, Position.MID);
        assertThat(RESOURCES.catalog().get(new ChampionId("cassiopeia")).supportedPositions())
                .containsExactlyInAnyOrder(Position.MID, Position.ADC);
        assertThat(RESOURCES.catalog().get(new ChampionId("taliyah")).supportedPositions())
                .containsExactlyInAnyOrder(Position.MID, Position.JUNGLE, Position.ADC);
    }

    @Test
    void allRoleCatalogsHaveExactLegalCoverageAndJungleClearStaysDormant() {
        Set<ChampionRoleKey> legal = RESOURCES.catalog().legalRoleKeys();
        assertThat(RESOURCES.power().all()).hasSize(173);
        assertThat(RESOURCES.power().all().stream().map(ChampionPowerProfile::championId).toList())
                .containsExactlyInAnyOrderElementsOf(RESOURCES.catalog().all().stream().map(ChampionDefinition::id).toList());
        assertThat(RESOURCES.matchup().profiles().keySet()).containsExactlyInAnyOrderElementsOf(legal);
        assertThat(RESOURCES.composition().profiles().keySet()).containsExactlyInAnyOrderElementsOf(legal);
        assertThat(RESOURCES.jungleClear().profiles()).hasSize(51);
        assertThat(RESOURCES.jungleClear().profiles().values()).allMatch(profile -> !profile.gameplayEnabled());
        assertThat(RESOURCES.jungleClear().profiles().keySet())
                .allMatch(key -> key.position() == Position.JUNGLE);
        assertThat(RESOURCES.matchup().profiles().values())
                .allMatch(profile -> profile.traits().size() == ChampionMatchupTrait.values().length);
        assertThat(RESOURCES.composition().profiles().values())
                .allMatch(profile -> profile.capabilities().size()
                        == com.lolfm.composition.CompositionCapability.values().length);
    }

    @Test
    void expandedRolesAreAcceptedOnlyInTheirStructuredPositions() {
        assertLegal("varus", Position.TOP);
        assertLegal("varus", Position.ADC);
        assertLegal("anivia", Position.TOP);
        assertLegal("anivia", Position.MID);
        assertLegal("cassiopeia", Position.MID);
        assertLegal("cassiopeia", Position.ADC);
        assertLegal("taliyah", Position.JUNGLE);
        assertLegal("taliyah", Position.MID);
        assertLegal("taliyah", Position.ADC);
        assertPositionMismatch("anivia", Position.SUPPORT);
        assertPositionMismatch("varus", Position.MID);
    }

    @Test
    void newRoleProfilesAreCompleteAndRoleSpecificWithoutPowerChanges() {
        assertThat(RESOURCES.matchup().find(key("varus", Position.TOP)).orElseThrow().trait(ChampionMatchupTrait.SUSTAINED_DAMAGE))
                .isEqualTo(17);
        assertThat(RESOURCES.matchup().find(key("anivia", Position.TOP)).orElseThrow().trait(ChampionMatchupTrait.DURABILITY))
                .isEqualTo(10);
        assertThat(RESOURCES.matchup().find(key("cassiopeia", Position.ADC)).orElseThrow().trait(ChampionMatchupTrait.RANGE_CONTROL))
                .isEqualTo(16);
        assertThat(RESOURCES.matchup().find(key("taliyah", Position.ADC)).orElseThrow().trait(ChampionMatchupTrait.WAVE_CONTROL))
                .isEqualTo(20);

        ChampionCompositionProfile varusTop = RESOURCES.composition().profiles().get(key("varus", Position.TOP));
        ChampionCompositionProfile varusAdc = RESOURCES.composition().profiles().get(key("varus", Position.ADC));
        ChampionCompositionProfile aniviaTop = RESOURCES.composition().profiles().get(key("anivia", Position.TOP));
        ChampionCompositionProfile aniviaMid = RESOURCES.composition().profiles().get(key("anivia", Position.MID));
        ChampionCompositionProfile cassioAdc = RESOURCES.composition().profiles().get(key("cassiopeia", Position.ADC));
        ChampionCompositionProfile cassioMid = RESOURCES.composition().profiles().get(key("cassiopeia", Position.MID));
        ChampionCompositionProfile taliyahAdc = RESOURCES.composition().profiles().get(key("taliyah", Position.ADC));
        ChampionCompositionProfile taliyahMid = RESOURCES.composition().profiles().get(key("taliyah", Position.MID));

        assertThat(varusTop.capability(com.lolfm.composition.CompositionCapability.SIDE_LANE_PRESSURE)).isEqualTo(13);
        assertThat(varusTop.capability(com.lolfm.composition.CompositionCapability.WAVE_CLEAR)).isEqualTo(18);
        assertThat(varusTop.capabilities()).isNotEqualTo(varusAdc.capabilities());
        assertThat(aniviaTop.capabilities()).isNotEqualTo(aniviaMid.capabilities());
        assertThat(cassioAdc.capabilities()).isNotEqualTo(cassioMid.capabilities());
        assertThat(taliyahAdc.capabilities()).isNotEqualTo(taliyahMid.capabilities());
    }

    private void assertLegal(String champion, Position position) {
        MatchChampionAssignments assignments = SELECTIONS.resolve(new ChampionSelectionRequest(
                replace(blueLineup(), champion, position), redLineup()));
        assertThat(assignments.get(new PlayerKey(TeamSide.BLUE, position)).championId().value())
                .isEqualTo(champion);
    }

    private void assertPositionMismatch(String champion, Position position) {
        assertThatThrownBy(() -> SELECTIONS.resolve(new ChampionSelectionRequest(
                replace(blueLineup(), champion, position), redLineup())))
                .isInstanceOfSatisfying(ChampionSelectionException.class,
                        error -> assertThat(error.getCode()).isEqualTo("CHAMPION_POSITION_MISMATCH"));
    }

    private ChampionLineupRequest replace(ChampionLineupRequest lineup, String champion, Position position) {
        return new ChampionLineupRequest(
                position == Position.TOP ? champion : lineup.top(),
                position == Position.JUNGLE ? champion : lineup.jgl(),
                position == Position.MID ? champion : lineup.mid(),
                position == Position.ADC ? champion : lineup.adc(),
                position == Position.SUPPORT ? champion : lineup.sup());
    }

    private ChampionLineupRequest blueLineup() {
        return new ChampionLineupRequest("renekton", "sejuani", "azir", "jinx", "nautilus");
    }

    private ChampionLineupRequest redLineup() {
        return new ChampionLineupRequest("jax", "lee-sin", "ahri", "kaisa", "rakan");
    }

    private static ChampionRoleKey key(String champion, Position position) {
        return new ChampionRoleKey(new ChampionId(champion), position);
    }
}
