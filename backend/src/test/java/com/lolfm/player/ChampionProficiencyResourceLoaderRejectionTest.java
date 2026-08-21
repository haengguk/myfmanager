package com.lolfm.player;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lolfm.champion.ChampionCatalog;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class ChampionProficiencyResourceLoaderRejectionTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PlayerRatingCatalog ratings = PlayerRatingCatalog.loadDefault();
    private final ChampionCatalog champions = new ChampionCatalog(mapper);

    @Test
    void rawShaIsCheckedBeforeSemanticParsing() {
        byte[] malformedJson = "not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThatThrownBy(() -> ChampionProficiencyResourceLoader.load(
                mapper, new ByteArrayInputStream(malformedJson), "wrong-sha", ratings, champions))
                .hasMessageContaining("SHA-256 mismatch")
                .hasNoCause();
    }

    @Test
    void duplicateSubjectRoleKeyIsRejected() throws Exception {
        ObjectNode root = defaultTree();
        com.fasterxml.jackson.databind.node.ArrayNode entries =
                (com.fasterxml.jackson.databind.node.ArrayNode) root.withArray("players").get(0).path("proficiencies");
        entries.add(entries.get(0).deepCopy());
        assertRejected(root, "Duplicate proficiency subject-role key");
    }

    @Test
    void illegalRoleAndPositionMismatchAreDistinctRejections() throws Exception {
        ObjectNode illegal = defaultTree();
        ((ObjectNode) illegal.withArray("players").get(0)
                .withArray("proficiencies").get(0)).put("championId", "not-a-champion");
        assertRejected(illegal, "Illegal authored ChampionRoleKey");

        ObjectNode mismatch = defaultTree();
        ((ObjectNode) mismatch.withArray("players").get(0)
                .withArray("proficiencies").get(0)).put("position", "MID");
        assertRejected(mismatch, "INVALID_SUBJECT_ROLE_BINDING");
    }

    @Test
    void v1ValueOutsideAuthoredBandAndPrerequisiteMismatchAreRejected() throws Exception {
        ObjectNode value = defaultTree();
        ((ObjectNode) value.withArray("players").get(0)
                .withArray("proficiencies").get(0)).put("value", 14);
        assertRejected(value, "outside 15..20");

        ObjectNode prerequisite = defaultTree();
        prerequisite.put("requiredChampionPoolVersion", "wrong-pool");
        assertRejected(prerequisite, "pool prerequisite mismatch");
    }

    private ObjectNode defaultTree() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                ChampionProficiencyResourceLoader.RESOURCE)) {
            return (ObjectNode) mapper.readTree(input);
        }
    }

    private void assertRejected(ObjectNode root, String message) throws Exception {
        byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertThatThrownBy(() -> ChampionProficiencyResourceLoader.load(
                mapper, new ByteArrayInputStream(bytes), sha, ratings, champions))
                .hasMessageContaining(message);
    }
}
