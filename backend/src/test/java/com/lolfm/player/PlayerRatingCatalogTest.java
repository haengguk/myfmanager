package com.lolfm.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.Position;
import com.lolfm.factory.DummyDataFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlayerRatingCatalogTest {
    private static final PlayerRatingCatalog CATALOG = PlayerRatingCatalog.loadDefault();
    private static final Set<PlayerSkill> COMMON = EnumSet.of(
            PlayerSkill.MECHANICS, PlayerSkill.DECISION_MAKING, PlayerSkill.MAP_AWARENESS,
            PlayerSkill.POSITIONING, PlayerSkill.COMBAT_EXECUTION, PlayerSkill.CONSISTENCY);

    @Test
    void playerRatingResourceVersionIsExact() {
        assertThat(CATALOG.version()).isEqualTo(PlayerRatingResourceLoader.VERSION);
        assertThat(CATALOG.version()).isEqualTo("lck-player-ratings-2026-08-19-v1");
        assertThat(CATALOG.snapshotAt()).isEqualTo(PlayerRatingResourceLoader.SNAPSHOT_AT);
        assertThat(CATALOG.dataCutoff()).isEqualTo(PlayerRatingResourceLoader.DATA_CUTOFF);
        assertThat(CATALOG.substitutesIncluded()).isFalse();
    }

    @Test
    void playerRatingInputHashIsExact() {
        assertThat(CATALOG.resourceSha256()).isEqualTo(PlayerRatingResourceLoader.EXPECTED_SHA256);
        assertThat(CATALOG.resourceSha256())
                .isEqualTo("2312a8bc7d222fd63b57d1255210fb25104432a90a954d854b2090cc2acb28e0");
    }

    @Test
    void playerRatingCatalogLoadsExactlyFiftyPlayers() {
        assertThat(CATALOG.playerCount()).isEqualTo(50);
        assertThat(CATALOG.all()).hasSize(50);
    }

    @Test
    void playerRatingCatalogContainsExactlyTenTeams() {
        assertThat(CATALOG.teamCount()).isEqualTo(10);
        assertThat(CATALOG.teamCodes()).containsExactly("BFX", "BRO", "DK", "DNS", "GEN", "HLE", "KRX", "KT", "NS", "T1");
        assertThat(CATALOG.all().stream().map(PlayerRatingResource::teamCode).distinct()).hasSize(10);
    }

    @Test
    void everyTeamHasFiveUniquePositions() {
        assertThat(CATALOG.all()).allSatisfy(player -> assertThat(player.position()).isIn(Position.values()));
        for (String team : CATALOG.teamCodes()) {
            assertThat(CATALOG.forTeam(team)).hasSize(5)
                    .extracting(PlayerRatingResource::position)
                    .containsExactly(Position.TOP, Position.JUNGLE, Position.MID, Position.ADC, Position.SUPPORT);
        }
    }

    @Test
    void everyPlayerHasExactlyTwelveActiveRatings() {
        assertThat(CATALOG.all()).allSatisfy(player ->
                assertThat(player.ratings().asMap()).hasSize(12)
                        .containsOnlyKeys(PlayerSkill.forPosition(player.position())));
        assertThat(CATALOG.activeAttributesPerPlayer()).isEqualTo(12);
    }

    @Test
    void everyPlayerHasExactlySixCommonRatings() {
        assertThat(CATALOG.all()).allSatisfy(player -> assertThat(player.ratings().asMap().keySet())
                .filteredOn(COMMON::contains).hasSize(6));
        assertThat(CATALOG.commonAttributeCount()).isEqualTo(6);
    }

    @Test
    void everyPlayerHasExactlySixApplicableRoleRatings() {
        assertThat(CATALOG.all()).allSatisfy(player -> {
            Set<PlayerSkill> roleSkills = EnumSet.copyOf(PlayerSkill.forPosition(player.position()));
            roleSkills.removeAll(COMMON);
            assertThat(player.ratings().asMap().keySet()).containsAll(roleSkills);
            assertThat(roleSkills).hasSize(6);
        });
        assertThat(CATALOG.roleSpecificAttributeCount()).isEqualTo(6);
    }

    @Test
    void allRatingsAreIntegerOneThroughTwenty() {
        assertThat(CATALOG.all()).allSatisfy(player ->
                assertThat(player.ratings().asMap().values()).allMatch(value -> value >= 1 && value <= 20));
    }

    @Test
    void nonApplicableRoleRatingsAreAbsent() {
        assertThat(CATALOG.all()).allSatisfy(player -> assertThat(player.ratings().asMap().keySet())
                .allMatch(skill -> skill.appliesTo(player.position())));
    }

    @Test
    void structuredPlayerIdentityIsUnique() {
        assertThat(CATALOG.all().stream().map(PlayerRatingResource::playerKey).distinct()).hasSize(50);
        assertThat(CATALOG.all().stream().map(value -> value.playerKey().stableId()).distinct()).hasSize(50);
        assertThat(CATALOG.get(new PlayerRatingKey("GEN", Position.TOP)).nickname()).isEqualTo("Kiin");
    }

    @Test
    void nicknameIsNotTheSoleGameplayIdentity() {
        assertThat(PlayerRatingKey.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("teamCode", "position");
        assertThat(CATALOG.find(new PlayerRatingKey("gen", Position.TOP))).isPresent();
        assertThat(CATALOG.find(new PlayerRatingKey("GEN", Position.MID))).isPresent();
        assertThat(CATALOG.find(new PlayerRatingKey("GEN", Position.TOP)).orElseThrow().nickname())
                .isNotEqualTo(CATALOG.find(new PlayerRatingKey("GEN", Position.MID)).orElseThrow().nickname());
    }

    @Test
    void playerRatingLoadingIsDeterministic() {
        PlayerRatingCatalog replay = PlayerRatingCatalog.loadDefault();
        assertThat(snapshot(CATALOG)).isEqualTo(snapshot(replay));
        assertThat(CATALOG.all().stream().map(value -> value.playerKey().stableId()).toList())
                .containsExactlyElementsOf(replay.all().stream().map(value -> value.playerKey().stableId()).toList());
    }

    @Test
    void defaultOverloadUsesTheSuppliedIdentityCatalogInstance() {
        PlayerIdentityCatalog identities = PlayerIdentityCatalog.loadDefault();
        PlayerRatingCatalog catalog = PlayerRatingCatalog.loadDefault(identities);

        assertThat(catalog.identities()).isSameAs(identities);
        assertThat(snapshot(catalog)).isEqualTo(snapshot(CATALOG));
        assertThat(catalog.all().stream().map(value -> value.playerKey().stableId()).toList())
                .containsExactlyElementsOf(CATALOG.all().stream()
                        .map(value -> value.playerKey().stableId()).toList());
    }

    @Test
    void allSixHundredAuthoredRatingValuesLoadExactly() throws IOException {
        JsonNode authored;
        try (InputStream input = PlayerRatingCatalogTest.class.getResourceAsStream(PlayerRatingResourceLoader.RESOURCE)) {
            authored = new ObjectMapper().readTree(input);
        }
        int checked = 0;
        for (JsonNode node : authored.path("players")) {
            PlayerRatingKey key = new PlayerRatingKey(node.path("team").asText(),
                    Position.valueOf(node.path("position").asText()));
            PlayerRatingResource loaded = CATALOG.get(key);
            for (PlayerSkill skill : PlayerSkill.forPosition(key.position())) {
                String jsonName = PlayerRatingResourceLoader.jsonName(skill);
                assertThat(node.path("ratings").path(jsonName).asInt())
                        .as(key.stableId() + ":" + skill)
                        .isEqualTo(loaded.ratings().get(skill));
                checked++;
            }
        }
        assertThat(checked).isEqualTo(600);
    }

    @Test
    void displayCaDoesNotExistInRuntimeResource() throws IOException {
        JsonNode authored;
        try (InputStream input = PlayerRatingCatalogTest.class.getResourceAsStream(PlayerRatingResourceLoader.RESOURCE)) {
            authored = new ObjectMapper().readTree(input);
        }
        assertThat(authored.findValue("displayCa")).isNull();
        assertThat(java.util.Arrays.stream(PlayerRatingResource.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .noneMatch(value -> value.equalsIgnoreCase("displayCa") || value.equalsIgnoreCase("ca"));
    }

    @Test
    void runtimeOvrIsNotDerivedFromPlayerRatings() {
        assertThat(java.util.Arrays.stream(PlayerRatingResource.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .noneMatch(value -> value.toLowerCase().contains("ovr") || value.toLowerCase().contains("overall"));
        assertThat(java.util.Arrays.stream(PlayerRatingCatalog.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .noneMatch(value -> value.toLowerCase().contains("ovr") || value.toLowerCase().contains("overall"));
    }

    @Test
    void championProficiencyRemainsSeparate() {
        PlayerRatingKey key = new PlayerRatingKey("GEN", Position.MID);
        ChampionRoleKey roleKey = new ChampionRoleKey(new ChampionId("azir"), Position.MID);
        ChampionProficiencies proficiencies = new ChampionProficiencies(Map.of(roleKey, 20));
        Player player = CATALOG.createPlayer(key, proficiencies);

        assertThat(player.getRatings()).isEqualTo(CATALOG.ratings(key));
        assertThat(player.getChampionProficiencies()).isSameAs(proficiencies);
        assertThat(player.getChampionProficiencies().get(roleKey)).isEqualTo(20);
        assertThat(PlayerRatingResource.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("playerKey", "nickname", "ratings");
        assertThatThrownBy(() -> CATALOG.createPlayer(key, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void existingNeutralPlayerRatingsStillWork() {
        for (Position position : Position.values()) {
            assertThat(PlayerRatings.neutral(position).asMap()).hasSize(12);
        }
        assertThat(new DummyDataFactory().createBlueTeam().getPlayers())
                .allMatch(Player::isLegacyProfile);
    }

    @Test
    void catalogDoesNotReplaceSyntheticFixturePopulation() {
        List<String> names = new DummyDataFactory().createBlueTeam().getPlayers().stream()
                .map(Player::getName).toList();
        assertThat(names).containsExactly("Atlas", "River", "Pulse", "Nova", "Bell");
        assertThat(CATALOG.get(new PlayerRatingKey("GEN", Position.TOP)).nickname()).isNotIn(names);
    }

    private Map<String, Map<PlayerSkill, Integer>> snapshot(PlayerRatingCatalog catalog) {
        Map<String, Map<PlayerSkill, Integer>> snapshot = new LinkedHashMap<>();
        for (PlayerRatingResource value : catalog.all()) {
            snapshot.put(value.playerKey().stableId(), value.ratings().asMap());
        }
        return snapshot;
    }
}
