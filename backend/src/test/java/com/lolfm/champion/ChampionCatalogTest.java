package com.lolfm.champion;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.domain.Position;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChampionCatalogTest {
    private final ChampionCatalog catalog = new ChampionCatalog(new ObjectMapper());

    @Test void containsExactlyThirtyAndSixPerPositionInRequiredOrder() {
        assertThat(catalog.all()).hasSize(30);
        for (Position position : Position.values()) assertThat(catalog.forPosition(position)).hasSize(6);
        assertThat(catalog.all()).extracting(c -> c.primaryPosition()).startsWith(
                Position.TOP, Position.TOP, Position.TOP, Position.TOP, Position.TOP, Position.TOP,
                Position.JUNGLE);
    }

    @Test void metadataAndCollectionsAreImmutable() {
        assertThat(catalog.championPoolVersion()).isEqualTo("initial-30-v1");
        assertThat(catalog.championBalanceVersion()).isEqualTo("neutral-foundation-v1");
        assertThat(catalog.riotDataVersion()).isEqualTo("16.14.1");
        assertThatThrownBy(() -> catalog.all().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> catalog.get(new ChampionId("renekton")).supportedPositions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void specialAssetIdsAreExactAndDisplayNamesAreNotIdentity() {
        assertThat(Map.of("lee-sin", "LeeSin", "ksante", "KSante", "kaisa", "Kaisa",
                "leblanc", "Leblanc", "renata-glasc", "Renata")).allSatisfy((id, asset) ->
                assertThat(catalog.get(new ChampionId(id)).riotAssetId()).isEqualTo(asset));
        assertThat(catalog.find(new ChampionId("renekton"))).isPresent();
        assertThatThrownBy(() -> new ChampionId("Renekton display name")).isInstanceOf(IllegalArgumentException.class);
    }
}
