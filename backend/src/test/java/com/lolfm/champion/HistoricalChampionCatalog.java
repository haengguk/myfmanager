package com.lolfm.champion;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;

/** Explicit initial-30 catalog fixture for frozen historical diagnostics. */
public final class HistoricalChampionCatalog {
    private static final String RESOURCE =
            "/champions/champion-pool-initial-30-v1.json";

    private HistoricalChampionCatalog() {}

    public static ChampionCatalog initialThirty() {
        InputStream input = ChampionResourceManifest.open(RESOURCE);
        if (input == null) throw new IllegalStateException("Missing historical catalog " + RESOURCE);
        return new ChampionCatalog(new ObjectMapper(), input);
    }
}
