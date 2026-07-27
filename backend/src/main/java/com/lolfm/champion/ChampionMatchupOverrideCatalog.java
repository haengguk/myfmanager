package com.lolfm.champion;

import com.lolfm.domain.Position;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ChampionMatchupOverrideCatalog {
    public static final String PRODUCTION_VERSION =
            "production-matchup-overrides-empty-v1";
    public static final String PROTOTYPE_VERSION =
            "prototype-semantic-overrides-empty-v1";
    public static final String SYNTHETIC_VERSION =
            "diagnostic-synthetic-override-v1";

    private final String version;
    private final boolean productionReachable;
    private final Map<Key, ChampionMatchupOverride> overrides;

    public ChampionMatchupOverrideCatalog(
            String version,
            boolean productionReachable,
            List<ChampionMatchupOverride> values
    ) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        this.version = version;
        this.productionReachable = productionReachable;
        LinkedHashMap<Key, ChampionMatchupOverride> indexed = new LinkedHashMap<>();
        for (ChampionMatchupOverride value : List.copyOf(values)) {
            if (!value.version().equals(version)) {
                throw new IllegalArgumentException("Override version mismatch");
            }
            if (productionReachable
                    && value.reason() == MatchupOverrideReason.DIAGNOSTIC_SYNTHETIC) {
                throw new IllegalArgumentException(
                        "Synthetic override cannot reach production");
            }
            Key key = new Key(value.pair(), value.position(), value.context());
            if (indexed.put(key, value) != null) {
                throw new IllegalArgumentException("Duplicate matchup override " + key);
            }
        }
        overrides = Map.copyOf(indexed);
    }

    public static ChampionMatchupOverrideCatalog production() {
        return new ChampionMatchupOverrideCatalog(
                PRODUCTION_VERSION, true, List.of());
    }

    public static ChampionMatchupOverrideCatalog prototypeSemantic() {
        return new ChampionMatchupOverrideCatalog(
                PROTOTYPE_VERSION, false, List.of());
    }

    public double adjustment(
            ChampionId source,
            ChampionId opponent,
            Position position,
            ProgressionCombatContext context
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(opponent, "opponent");
        if (source.equals(opponent)) return 0.0;
        ChampionMatchupPair pair = source.value().compareTo(opponent.value()) < 0
                ? new ChampionMatchupPair(source, opponent, position)
                : new ChampionMatchupPair(opponent, source, position);
        ChampionMatchupOverride value =
                overrides.get(new Key(pair, position, context));
        if (value == null) return 0.0;
        double result = pair.first().equals(source)
                ? value.canonicalFirstAdjustment()
                : -value.canonicalFirstAdjustment();
        return result == 0.0 ? 0.0 : result;
    }

    public String version() { return version; }
    public boolean productionReachable() { return productionReachable; }
    public List<ChampionMatchupOverride> values() {
        return List.copyOf(overrides.values());
    }

    private record Key(
            ChampionMatchupPair pair,
            Position position,
            ProgressionCombatContext context
    ) {
    }
}
