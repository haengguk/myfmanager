package com.lolfm.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lolfm.domain.Position;
import com.lolfm.player.PlayerId;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlayerCareerResourceLoaderTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rawShaVersionScopeCountsAndCoverageAreExact() {
        PlayerCareerResourceLoader.LoadedResource loaded =
                PlayerCareerResourceLoader.loadDefault();

        assertThat(loaded.version()).isEqualTo(PlayerCareerResourceLoader.VERSION);
        assertThat(loaded.snapshotAt()).isEqualTo("2026-08-24");
        assertThat(loaded.resourceSha256())
                .isEqualTo("4e4f01fe72f68aca7dcb93afb72b43273201ce0daa7d63613f628597ff41ff19");
        assertThat(loaded.scope().league()).isEqualTo("LCK");
        assertThat(loaded.scope().teams()).isEqualTo(10);
        assertThat(loaded.scope().players()).isEqualTo(50);
        assertThat(loaded.scope().startersPerTeam()).isEqualTo(5);
        assertThat(loaded.scope().startersOnly()).isTrue();
        assertThat(loaded.scope().salaryIncluded()).isFalse();
        assertThat(loaded.scope().marketValueIncluded()).isFalse();
        assertThat(loaded.counts()).isEqualTo(new PlayerCareerResourceLoader.Counts(
                50, 10, 50, 248, 154, 21, 248, 43));
        assertThat(loaded.players()).hasSize(50)
                .extracting(PlayerCareerResource::playerId).doesNotHaveDuplicates();
        assertThat(loaded.players()).allSatisfy(player -> {
            assertThat(player.personal().legalName()).isNotBlank();
            assertThat(player.personal().birthDate()).isNotBlank();
            assertThat(player.personal().nationality()).isNotEmpty();
            assertThat(player.contract().endDate()).isNotBlank();
            assertThat(player.career().teamHistory()).isNotEmpty();
            assertThat(player.careerPrizeMoney().amountUsd()).isNotNull();
        });
    }

    @Test
    void teamPositionCoverageAndSnapshotAgeContractSemanticsAreMeasured() {
        PlayerCareerResourceLoader.LoadedResource loaded =
                PlayerCareerResourceLoader.loadDefault();

        assertThat(loaded.players().stream().map(PlayerCareerResource::teamCode).distinct())
                .hasSize(10);
        for (String teamCode : loaded.players().stream()
                .map(PlayerCareerResource::teamCode).collect(java.util.stream.Collectors.toSet())) {
            assertThat(loaded.players().stream().filter(player ->
                    player.teamCode().equals(teamCode)).map(PlayerCareerResource::position))
                    .containsExactlyInAnyOrderElementsOf(Set.of(Position.values()));
        }
        LocalDate snapshot = LocalDate.parse(loaded.snapshotAt());
        assertThat(loaded.players()).allSatisfy(player -> {
            assertThat(player.personal().ageAsOfSnapshot()).isEqualTo(
                    java.time.Period.between(LocalDate.parse(player.personal().birthDate()),
                            snapshot).getYears());
            assertThat(player.contract().daysRemainingAsOfSnapshot()).isEqualTo(
                    ChronoUnit.DAYS.between(snapshot,
                            LocalDate.parse(player.contract().endDate())));
        });
    }

    @Test
    void sourceOrderDatePrecisionAndNullableValuesArePreserved() {
        PlayerCareerResource chovy = PlayerCareerResourceLoader.loadDefault().players().stream()
                .filter(player -> player.playerId().equals(new PlayerId("player-chovy")))
                .findFirst().orElseThrow();

        assertThat(chovy.career().teamHistory())
                .extracting(PlayerCareerResource.TeamHistory::team)
                .containsExactly("Griffin", "DRX", "Hanwha Life Esports", "Gen.G",
                        "Gen.G", "Gen.G");
        assertThat(chovy.career().teamHistory())
                .extracting(PlayerCareerResource.TeamHistory::datePrecision)
                .containsOnly("DAY");
        assertThat(chovy.career().teamHistory().getLast().to()).isNull();
        assertThat(chovy.honors().teamAchievements())
                .anySatisfy(achievement -> assertThat(achievement.sourceUrl()).isNull());
        assertThat(chovy.sources().getFirst().path())
                .isEqualTo("lck-player-identities-2026-08-21-v1.json");
        assertThat(chovy.sources().getFirst().url()).isNull();
    }

    @Test
    void loadedListsAreImmutable() {
        PlayerCareerResourceLoader.LoadedResource loaded =
                PlayerCareerResourceLoader.loadDefault();
        assertThatThrownBy(() -> loaded.players().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> loaded.players().getFirst().sources().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> loaded.players().getFirst().career().teamHistory().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rawShaMismatchFailsBeforeSemanticParsing() {
        assertThatThrownBy(() -> PlayerCareerResourceLoader.load(
                mapper, new ByteArrayInputStream("not-json".getBytes()),
                PlayerCareerResourceLoader.EXPECTED_SHA256))
                .hasMessageContaining("SHA-256 mismatch");
    }

    @Test
    void duplicateMissingAndMalformedPlayerIdsFailClosed() throws Exception {
        ObjectNode duplicate = sourceTree();
        ArrayNode duplicatePlayers = duplicate.withArray("players");
        duplicatePlayers.set(1, duplicatePlayers.get(0).deepCopy());
        assertSemanticRejection(duplicate, "Duplicate career PlayerId");

        ObjectNode missing = sourceTree();
        missing.withArray("players").remove(0);
        assertSemanticRejection(missing, "player count mismatch");

        ObjectNode malformed = sourceTree();
        ((ObjectNode) malformed.withArray("players").get(0))
                .put("playerId", "PLAYER_NOT_CANONICAL");
        assertSemanticRejection(malformed, "Malformed career PlayerId");
    }

    @Test
    void declaredCountsAndSnapshotSemanticsCannotBeRelaxed() throws Exception {
        ObjectNode wrongDays = sourceTree();
        ((ObjectNode) wrongDays.withArray("players").get(0).path("contract"))
                .put("daysRemainingAsOfSnapshot", 83);
        assertSemanticRejection(wrongDays, "Snapshot contract days mismatch");

        ObjectNode missingHistory = sourceTree();
        ((ObjectNode) missingHistory.withArray("players").get(0).path("career"))
                .withArray("teamHistory").remove(0);
        assertSemanticRejection(missingHistory, "Measured player career counts mismatch");
    }

    private ObjectNode sourceTree() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                PlayerCareerResourceLoader.RESOURCE)) {
            return (ObjectNode) mapper.readTree(input);
        }
    }

    private void assertSemanticRejection(ObjectNode root, String message) throws Exception {
        byte[] bytes = mapper.writeValueAsBytes(root);
        String sha = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
        assertThatThrownBy(() -> PlayerCareerResourceLoader.load(
                mapper, new ByteArrayInputStream(bytes), sha))
                .hasMessageContaining(message);
    }
}
