package com.lolfm.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionResourceSet;
import java.util.Objects;

public record DraftResourceSet(ChampionResourceSet champions, DraftMetaCatalog meta) {
    public DraftResourceSet {
        Objects.requireNonNull(champions, "champions");
        Objects.requireNonNull(meta, "meta");
    }
    public static DraftResourceSet loadDefault() {
        ChampionResourceSet champions = ChampionResourceSet.loadDefault();
        return new DraftResourceSet(champions,
                DraftMetaCatalog.loadDefault(new ObjectMapper(), champions.catalog()));
    }
}
