package com.lolfm.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lolfm.domain.Position;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class PlayerIdentityCatalogTest {
    private static final PlayerIdentityCatalog IDENTITIES = PlayerIdentityCatalog.loadDefault();
    private static final PlayerRatingCatalog RATINGS = PlayerRatingCatalog.loadDefault();

    @Test
    void explicitIdentityResourceIsPinnedAndComplete() {
        assertThat(IDENTITIES.version()).isEqualTo("lck-player-identities-2026-08-21-v1");
        assertThat(IDENTITIES.snapshotAt()).isEqualTo("2026-08-21");
        assertThat(IDENTITIES.resourceSha256())
                .isEqualTo("badbbaa3ae7fbe5eaaf83ee8e97a93134476493a45167ec3d1637c7243909018");
        assertThat(IDENTITIES.all()).hasSize(50);
        assertThat(IDENTITIES.all().stream().map(PlayerIdentity::playerId).distinct()).hasSize(50);
        assertThat(IDENTITIES.all().stream().map(PlayerIdentity::ratingKey).distinct()).hasSize(50);
        assertThat(IDENTITIES.teamCodes()).hasSize(10);
    }

    @Test
    void dualIndexesComeFromTheSameImmutableRecords() {
        PlayerRatingKey chovyKey = new PlayerRatingKey("GEN", Position.MID);
        PlayerId chovyId = new PlayerId("player-chovy");

        assertThat(IDENTITIES.findByRatingKey(chovyKey)).isEqualTo(chovyId);
        assertThat(IDENTITIES.currentRatingKey(chovyId)).isEqualTo(chovyKey);
        assertThat(IDENTITIES.get(chovyId)).isSameAs(IDENTITIES.get(chovyKey));
        assertThat(RATINGS.playerId(chovyKey)).isEqualTo(chovyId);
        assertThat(RATINGS.currentRatingKey(chovyId)).isEqualTo(chovyKey);
        assertThat(RATINGS.ratings(chovyId)).isEqualTo(RATINGS.ratings(chovyKey));
    }

    @Test
    void deterministicReloadKeepsAllAuthoredBindings() {
        PlayerIdentityCatalog replay = PlayerIdentityCatalog.loadDefault();
        assertThat(replay.resourceSha256()).isEqualTo(IDENTITIES.resourceSha256());
        assertThat(replay.all()).containsExactlyElementsOf(IDENTITIES.all());
    }

    @Test
    void playerIdRejectsBlankNonCanonicalAndCaseVariants() {
        assertThatThrownBy(() -> new PlayerId(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlayerId("Chovy")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlayerId("player-Chovy")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlayerId("player_chovy")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loaderRejectsDuplicatePlayerId() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = defaultTree(mapper);
        String firstId = root.withArray("players").get(0).path("playerId").asText();
        ((ObjectNode) root.withArray("players").get(1)).put("playerId", firstId);
        assertSemanticRejection(mapper, root, "Duplicate PlayerId");
    }

    @Test
    void loaderRejectsMissingSubjectAndNicknameMismatch() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode missing = defaultTree(mapper);
        missing.withArray("players").remove(0);
        assertSemanticRejection(mapper, missing, "player count mismatch");

        ObjectNode nickname = defaultTree(mapper);
        ((ObjectNode) nickname.withArray("players").get(0)).put("nickname", "WrongDisplayName");
        assertSemanticRejection(mapper, nickname, "nickname mismatch");
    }

    @Test
    void loaderRejectsMissingBlankAndUnexpectedSnapshotDate() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode missing = defaultTree(mapper);
        missing.remove("snapshotAt");
        assertSemanticRejection(mapper, missing, "snapshotAt mismatch");

        ObjectNode blank = defaultTree(mapper);
        blank.put("snapshotAt", " ");
        assertSemanticRejection(mapper, blank, "snapshotAt mismatch");

        ObjectNode unexpected = defaultTree(mapper);
        unexpected.put("snapshotAt", "2026-08-20");
        assertSemanticRejection(mapper, unexpected, "snapshotAt mismatch");
    }

    private ObjectNode defaultTree(ObjectMapper mapper) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(PlayerIdentityResourceLoader.RESOURCE)) {
            return (ObjectNode) mapper.readTree(input);
        }
    }

    private void assertSemanticRejection(ObjectMapper mapper, ObjectNode root, String message) throws Exception {
        byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertThatThrownBy(() -> PlayerIdentityResourceLoader.load(
                mapper, new ByteArrayInputStream(bytes), sha, PlayerRatingResourceLoader.loadDefault()))
                .hasMessageContaining(message);
    }
}
