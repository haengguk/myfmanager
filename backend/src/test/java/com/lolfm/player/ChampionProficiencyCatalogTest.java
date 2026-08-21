package com.lolfm.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.Position;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChampionProficiencyCatalogTest {
    private static final ChampionProficiencyCatalog CATALOG = ChampionProficiencyCatalog.loadDefault();
    private static final PlayerRatingCatalog RATINGS = PlayerRatingCatalog.loadDefault();
    private static final ChampionCatalog CHAMPIONS = new ChampionCatalog(new ObjectMapper());

    @Test
    void productionResourceIsRawPinnedAndPrerequisitesAreExact() {
        assertThat(CATALOG.version()).isEqualTo("lck-champion-proficiency-2026-08-21-v1");
        assertThat(CATALOG.researchAsOf()).isEqualTo("2026-08-21");
        assertThat(CATALOG.resourceSha256())
                .isEqualTo("2c36b8a109aba9dfe84c1da319fe02708a72a1341d334dc6d5e3f605b0023aad");
        assertThat(CATALOG.requiredPlayerRatingResourceVersion()).isEqualTo(RATINGS.version());
        assertThat(CATALOG.requiredChampionPoolVersion()).isEqualTo(CHAMPIONS.championPoolVersion());
        assertThat(CATALOG.requiredLegalRoleKeyCount()).isEqualTo(216);
    }

    @Test
    void measuredSparsePopulationMatchesTheAuthoritativeSnapshot() {
        ChampionProficiencyPopulationMetrics metrics = CATALOG.metrics();
        assertThat(metrics.teamCount()).isEqualTo(10);
        assertThat(metrics.playerCount()).isEqualTo(50);
        assertThat(metrics.legalRoleKeyCount()).isEqualTo(216);
        assertThat(metrics.potentialPlayerRoleKeyCount()).isEqualTo(2160);
        assertThat(metrics.authoredOverrideCount()).isEqualTo(732);
        assertThat(metrics.neutralFallbackKeyCount()).isEqualTo(1428);
        assertThat(metrics.scoreDistribution()).containsExactlyInAnyOrderEntriesOf(
                Map.of(15, 35, 16, 160, 17, 228, 18, 210, 19, 81, 20, 18));
        assertThat(metrics.highProficiencyCount()).isEqualTo(537);
        assertThat(metrics.eliteProficiencyCount()).isEqualTo(99);
        assertThat(metrics.worldBenchmarkCount()).isEqualTo(18);
        assertThat(metrics.scopeInexpressibleEvidenceCount()).isEqualTo(11);
        assertThat(CATALOG.authoredEntries()).hasSize(metrics.authoredOverrideCount());
        assertThat(CATALOG.all()).hasSize(50);
    }

    @Test
    void knownAuthoredValuesBindToStablePeople() {
        assertThat(value("player-chovy", "azir", Position.MID)).isEqualTo(20);
        assertThat(value("player-canyon", "nidalee", Position.JUNGLE)).isEqualTo(20);
        assertThat(value("player-faker", "leblanc", Position.MID)).isEqualTo(20);
        assertThat(value("player-keria", "bard", Position.SUPPORT)).isEqualTo(20);
    }

    @Test
    void onlyOmittedLegalSamePositionKeysUseNeutralFourteen() {
        PlayerId chovy = new PlayerId("player-chovy");
        ChampionProficiencies profile = CATALOG.get(chovy);
        ChampionRoleKey omitted = CHAMPIONS.legalRoleKeys().stream()
                .filter(key -> key.position() == Position.MID)
                .filter(key -> !profile.asMap().containsKey(key))
                .findFirst().orElseThrow();

        assertThat(CATALOG.value(chovy, omitted)).isEqualTo(ChampionProficiencies.NEUTRAL);
        assertThat(profile.asMap()).doesNotContainKey(omitted);
        assertThatThrownBy(() -> CATALOG.value(chovy,
                new ChampionRoleKey(new ChampionId("not-a-champion"), Position.MID)))
                .hasMessageContaining("Illegal ChampionRoleKey");
        assertThatThrownBy(() -> CATALOG.get(new PlayerId("player-unknown")))
                .hasMessageContaining("Unknown PlayerId");
    }

    @Test
    void crossPositionAndMixedProviderBindingsFailFast() {
        PlayerId chovy = new PlayerId("player-chovy");
        PlayerId faker = new PlayerId("player-faker");
        PlayerRatingKey chovyKey = new PlayerRatingKey("GEN", Position.MID);
        ChampionRoleKey azirMid = new ChampionRoleKey(new ChampionId("azir"), Position.MID);

        assertThat(CATALOG.bind(chovy, chovyKey, chovy)).isSameAs(CATALOG.get(chovy));
        assertThatThrownBy(() -> CATALOG.bind(chovy, chovyKey, faker))
                .hasMessageContaining("PROFICIENCY_BINDING_MISMATCH");
        assertThatThrownBy(() -> CATALOG.value(
                new PlayerRatingKey("GEN", Position.TOP), azirMid))
                .hasMessageContaining("INVALID_SUBJECT_ROLE_BINDING");
        assertThatThrownBy(() -> CATALOG.bind(faker, chovyKey, faker))
                .hasMessageContaining("PLAYER_ID_RATING_KEY_MISMATCH");
    }

    @Test
    void deterministicReloadPreservesSparseEntriesExactly() {
        ChampionProficiencyCatalog replay = ChampionProficiencyCatalog.loadDefault();
        assertThat(replay.resourceSha256()).isEqualTo(CATALOG.resourceSha256());
        assertThat(replay.authoredEntries()).containsExactlyElementsOf(CATALOG.authoredEntries());
        assertThat(replay.all()).isEqualTo(CATALOG.all());
    }

    private int value(String playerId, String championId, Position position) {
        return CATALOG.value(new PlayerId(playerId),
                new ChampionRoleKey(new ChampionId(championId), position));
    }
}
