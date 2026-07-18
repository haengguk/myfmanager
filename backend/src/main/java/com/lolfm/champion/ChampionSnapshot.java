package com.lolfm.champion;

import com.lolfm.domain.Position;

public record ChampionSnapshot(String id, String displayNameKo, String displayNameEn, String portraitUrl,
                               Position primaryPosition, String poolVersion) {
    public static ChampionSnapshot from(ChampionDefinition definition) {
        return new ChampionSnapshot(definition.id().value(), definition.displayNameKo(), definition.displayNameEn(),
                definition.portraitUrl(), definition.primaryPosition(), definition.championPoolVersion());
    }
}
