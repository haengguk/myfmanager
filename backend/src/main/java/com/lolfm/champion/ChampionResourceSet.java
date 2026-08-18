package com.lolfm.champion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.composition.ChampionCompositionProfileCatalog;
import java.util.Objects;

/** Immutable, fully materialized and cross-catalog-validated champion resources. */
public record ChampionResourceSet(
        ChampionResourceManifest manifest,
        ChampionCatalog catalog,
        ChampionPowerProfileCatalog power,
        ChampionRoleMatchupProfileCatalog matchup,
        ChampionCompositionProfileCatalog composition,
        ChampionJungleClearProfileCatalog jungleClear
) {
    public ChampionResourceSet {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(power, "power");
        Objects.requireNonNull(matchup, "matchup");
        Objects.requireNonNull(composition, "composition");
        Objects.requireNonNull(jungleClear, "jungleClear");
        ChampionResourceCompletenessValidator.validate(catalog, power, matchup, composition, jungleClear);
    }

    public static ChampionResourceSet loadDefault() {
        ObjectMapper mapper = new ObjectMapper();
        ChampionResourceManifest manifest = ChampionResourceManifest.loadDefault(mapper);
        return load(mapper, manifest);
    }

    public static ChampionResourceSet load(ObjectMapper mapper, ChampionResourceManifest manifest) {
        ChampionCatalog catalog = new ChampionCatalog(mapper, required(manifest.catalog(), "catalog"));
        ChampionPowerProfileCatalog power = new ChampionPowerProfileCatalog(
                mapper, catalog, required(manifest.power(), "power"));
        ChampionRoleMatchupProfileCatalog matchup = ChampionMatchupProfileResourceLoader.load(
                mapper, catalog, required(manifest.matchup(), "matchup"), false);
        ChampionCompositionProfileCatalog composition = ChampionCompositionProfileCatalog.load(
                mapper, catalog, required(manifest.composition(), "composition"));
        ChampionJungleClearProfileCatalog jungleClear = ChampionJungleClearProfileCatalog.load(
                mapper, catalog, required(manifest.jungleClear(), "jungleClear"));
        return new ChampionResourceSet(manifest, catalog, power, matchup, composition, jungleClear);
    }

    private static java.io.InputStream required(String path, String role) {
        java.io.InputStream input = ChampionResourceManifest.open(path);
        if (input == null) throw new IllegalStateException("Missing " + role + " resource: " + path);
        return input;
    }
}
