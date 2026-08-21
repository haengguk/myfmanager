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

    /** Application wiring path that keeps Draft on the caller-owned ChampionCatalog graph. */
    public static DraftResourceSet loadDefault(ObjectMapper mapper,
                                               com.lolfm.champion.ChampionCatalog catalog) {
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(catalog, "catalog");
        ChampionResourceSet champions = ChampionResourceSet.loadDefault(mapper, catalog);
        return new DraftResourceSet(champions, DraftMetaCatalog.loadDefault(mapper, catalog));
    }
}
