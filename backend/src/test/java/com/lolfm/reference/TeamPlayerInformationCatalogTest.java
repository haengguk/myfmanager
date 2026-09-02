package com.lolfm.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.Position;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.ChampionProficiencyEntry;
import com.lolfm.player.PlayerId;
import com.lolfm.player.PlayerIdentityCatalog;
import com.lolfm.player.PlayerRatingCatalog;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeamPlayerInformationCatalogTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PlayerIdentityCatalog identities = PlayerIdentityCatalog.loadDefault();
    private final PlayerRatingCatalog ratings = PlayerRatingCatalog.loadDefault(identities);
    private final ChampionCatalog champions = new ChampionCatalog(mapper);
    private final ChampionProficiencyCatalog proficiencies =
            ChampionProficiencyCatalog.loadDefault(ratings, champions);
    private final TeamPlayerInformationCatalog catalog = new TeamPlayerInformationCatalog(
            identities, ratings, proficiencies, champions,
            PlayerCareerResourceLoader.loadDefault());

    @Test
    void fourCatalogsJoinToExactlyTenTeamsAndFiftyStablePlayerIds() {
        assertThat(catalog.counts()).isEqualTo(
                new TeamPlayerInformationCatalog.CatalogCounts(
                        10, 50, 50, 248, 154, 21, 248, 732, 1428, 43));
        assertThat(catalog.teams()).hasSize(10)
                .extracting(TeamPlayerInformationCatalog.TeamInformation::teamCode)
                .containsExactly("BFX", "BRO", "DK", "DNS", "GEN", "HLE", "KRX",
                        "KT", "NS", "T1");
        assertThat(catalog.players()).hasSize(50)
                .extracting(value -> value.identity().playerId()).doesNotHaveDuplicates();
        assertThat(catalog.players()).allSatisfy(player -> {
            assertThat(player.identity().playerId()).isEqualTo(player.career().playerId());
            assertThat(player.identity().nickname()).isEqualTo(player.rating().nickname())
                    .isEqualTo(player.career().nickname());
            assertThat(player.identity().ratingKey().teamCode())
                    .isEqualTo(player.career().teamCode());
            assertThat(player.identity().ratingKey().position())
                    .isEqualTo(player.career().position());
        });
    }

    @Test
    void teamAndPlayerOrderingAreCanonicalAndImmutable() {
        TeamPlayerInformationCatalog.TeamInformation gen = catalog.findTeam("GEN")
                .orElseThrow();
        assertThat(gen.lineup()).extracting(value -> value.identity().playerId().value())
                .containsExactly("player-kiin", "player-canyon", "player-chovy",
                        "player-ruler", "player-duro");
        assertThat(gen.lineup()).extracting(value -> value.identity().ratingKey().position())
                .containsExactly(Position.TOP, Position.JUNGLE, Position.MID,
                        Position.ADC, Position.SUPPORT);
        assertThat(catalog.players().getFirst().identity().ratingKey().teamCode())
                .isEqualTo("BFX");
        assertThat(catalog.players().getFirst().identity().ratingKey().position())
                .isEqualTo(Position.TOP);
        assertThatThrownBy(() -> catalog.players().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> gen.lineup().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void ratingsStayAuthoredTwelveValueProfilesWithoutOvrOrCa() {
        TeamPlayerInformationCatalog.PlayerInformation chovy = chovy();
        assertThat(chovy.rating().ratings().asMap()).hasSize(12)
                .containsOnlyKeys(PlayerSkill.forPosition(Position.MID));
        assertThat(chovy.rating().ratings().asMap().values())
                .allMatch(value -> value >= PlayerRatings.MIN && value <= PlayerRatings.MAX);
        assertThat(PlayerSkill.orderedForPosition(Position.MID))
                .containsExactly(PlayerSkill.MECHANICS, PlayerSkill.DECISION_MAKING,
                        PlayerSkill.MAP_AWARENESS, PlayerSkill.POSITIONING,
                        PlayerSkill.COMBAT_EXECUTION, PlayerSkill.CONSISTENCY,
                        PlayerSkill.FARMING, PlayerSkill.TRADING,
                        PlayerSkill.WAVE_MANAGEMENT, PlayerSkill.LANE_PRESSURE,
                        PlayerSkill.PRIORITY_CONVERSION, PlayerSkill.SIDE_LANE);
        assertThat(java.util.Arrays.stream(
                chovy.rating().getClass().getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .noneMatch(name -> name.equalsIgnoreCase("ovr")
                        || name.equalsIgnoreCase("ca"));
    }

    @Test
    void authoredProficiencyIsSparseAndOrderedByValueThenChampionId() {
        TeamPlayerInformationCatalog.PlayerInformation chovy = chovy();
        assertThat(chovy.authoredProficiencies()).isNotEmpty();
        List<ChampionProficiencyEntry> expected = new ArrayList<>(
                chovy.authoredProficiencies());
        expected.sort(java.util.Comparator.comparingInt(
                        ChampionProficiencyEntry::value).reversed()
                .thenComparing(value -> value.championRoleKey().championId().value()));
        assertThat(chovy.authoredProficiencies()).containsExactlyElementsOf(expected);
        assertThat(chovy.authoredProficiencies()).allSatisfy(entry -> {
            assertThat(entry.playerId()).isEqualTo(new PlayerId("player-chovy"));
            assertThat(entry.championRoleKey().position()).isEqualTo(Position.MID);
            assertThat(entry.value()).isBetween(15, 20);
            assertThat(catalog.champion(entry.championRoleKey().championId()))
                    .isNotNull();
        });
        assertThat(catalog.counts().authoredProficiencies()).isEqualTo(732);
        assertThat(catalog.counts().neutralFallbackKeys()).isEqualTo(1428);
    }

    @Test
    void metadataAndCatalogHashAreStableAcrossFreshMaterializations() {
        TeamPlayerInformationCatalog replay = TeamPlayerInformationCatalog.loadDefault();
        assertThat(catalog.provenance()).isEqualTo(replay.provenance());
        assertThat(catalog.provenance().catalogHash()).matches("[0-9a-f]{64}");
        assertThat(catalog.provenance().resources()).hasSize(4)
                .extracting(TeamPlayerInformationCatalog.ResourceProvenance::rawSha256)
                .containsExactly(
                        "badbbaa3ae7fbe5eaaf83ee8e97a93134476493a45167ec3d1637c7243909018",
                        "2312a8bc7d222fd63b57d1255210fb25104432a90a954d854b2090cc2acb28e0",
                        "2c36b8a109aba9dfe84c1da319fe02708a72a1341d334dc6d5e3f605b0023aad",
                        "4e4f01fe72f68aca7dcb93afb72b43273201ce0daa7d63613f628597ff41ff19");
    }

    @Test
    void optionalExactFiltersNeverReorderTheCanonicalPopulation() {
        assertThat(catalog.players("GEN", null))
                .extracting(value -> value.identity().ratingKey().position())
                .containsExactly(Position.TOP, Position.JUNGLE, Position.MID,
                        Position.ADC, Position.SUPPORT);
        assertThat(catalog.players(null, Position.MID))
                .extracting(value -> value.identity().ratingKey().teamCode())
                .containsExactly("BFX", "BRO", "DK", "DNS", "GEN", "HLE", "KRX",
                        "KT", "NS", "T1");
        assertThat(catalog.players("GEN", Position.MID))
                .singleElement().satisfies(value -> assertThat(
                        value.identity().playerId().value()).isEqualTo("player-chovy"));
    }

    @Test
    void unknownAndDisplayMismatchedCareerSubjectsFailClosed() throws Exception {
        ObjectNode unknown = sourceTree();
        ((ObjectNode) unknown.withArray("players").get(0))
                .put("playerId", "player-unknown-subject");
        assertCatalogRejection(unknown, "PlayerId subject mismatch");

        ObjectNode nickname = sourceTree();
        ((ObjectNode) nickname.withArray("players").get(0)).put("nickname", "Renamed");
        assertCatalogRejection(nickname, "current identity mismatch");
    }

    @Test
    void currentTeamPositionMismatchIsRejectedWithoutNameInference() throws Exception {
        ObjectNode swapped = sourceTree();
        ObjectNode first = (ObjectNode) swapped.withArray("players").get(0);
        ObjectNode second = (ObjectNode) swapped.withArray("players").get(1);
        String firstPosition = first.path("position").asText();
        first.put("position", second.path("position").asText());
        second.put("position", firstPosition);

        assertCatalogRejection(swapped, "current identity mismatch");
    }

    private TeamPlayerInformationCatalog.PlayerInformation chovy() {
        return catalog.findPlayer(new PlayerId("player-chovy")).orElseThrow();
    }

    private ObjectNode sourceTree() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                PlayerCareerResourceLoader.RESOURCE)) {
            return (ObjectNode) mapper.readTree(input);
        }
    }

    private void assertCatalogRejection(ObjectNode root, String message) throws Exception {
        byte[] bytes = mapper.writeValueAsBytes(root);
        String sha = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
        PlayerCareerResourceLoader.LoadedResource loaded =
                PlayerCareerResourceLoader.load(mapper,
                        new ByteArrayInputStream(bytes), sha);
        assertThatThrownBy(() -> new TeamPlayerInformationCatalog(
                identities, ratings, proficiencies, champions, loaded))
                .hasMessageContaining(message);
    }
}
