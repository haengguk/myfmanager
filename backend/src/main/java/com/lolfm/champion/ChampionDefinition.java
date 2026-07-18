package com.lolfm.champion;

import com.lolfm.domain.Position;
import java.util.Objects;
import java.util.Set;

public record ChampionDefinition(
        ChampionId id,
        String displayNameKo,
        String displayNameEn,
        String riotAssetId,
        Position primaryPosition,
        Set<Position> supportedPositions,
        String portraitUrl,
        String championPoolVersion,
        String riotDataVersion
) {
    public ChampionDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(primaryPosition, "primaryPosition");
        supportedPositions = Set.copyOf(Objects.requireNonNull(supportedPositions, "supportedPositions"));
        if (displayNameKo == null || displayNameKo.isBlank()) throw new IllegalArgumentException("Missing Korean name: " + id);
        if (displayNameEn == null || displayNameEn.isBlank()) throw new IllegalArgumentException("Missing English name: " + id);
        if (riotAssetId == null || riotAssetId.isBlank()) throw new IllegalArgumentException("Missing Riot asset id: " + id);
        if (portraitUrl == null || portraitUrl.isBlank()) throw new IllegalArgumentException("Missing portrait: " + id);
    }
}
