package com.lolfm.champion;

import com.lolfm.domain.Position;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class ChampionMatchupCatalog {
    public static final String PRODUCTION_VERSION = "initial-30-matchup-neutral-v1";
    private final String version;
    private final Map<ChampionId, ChampionDefinition> champions;
    private final Map<ChampionMatchupPair, ChampionMatchupProfile> profiles;

    private ChampionMatchupCatalog(
            String version,
            ChampionCatalog championCatalog,
            List<ChampionMatchupProfile> values,
            boolean requireComplete
    ) {
        this.version = Objects.requireNonNull(version, "version");
        champions = Map.copyOf(championCatalog.all().stream().collect(
                Collectors.toMap(ChampionDefinition::id, value -> value)));
        LinkedHashMap<ChampionMatchupPair, ChampionMatchupProfile> indexed = new LinkedHashMap<>();
        for (ChampionMatchupProfile profile : List.copyOf(values)) {
            validatePair(profile.pair());
            if (indexed.put(profile.pair(), profile) != null) {
                throw new IllegalArgumentException("Duplicate matchup pair " + profile.pair());
            }
        }
        profiles = Map.copyOf(indexed);
        if (requireComplete) validateCompleteNeutral();
    }

    public static ChampionMatchupCatalog neutral(ChampionCatalog champions) {
        return new ChampionMatchupCatalog(
                PRODUCTION_VERSION, champions, List.of(), false);
    }

    static ChampionMatchupCatalog testCatalog(
            ChampionCatalog champions,
            List<ChampionMatchupProfile> profiles
    ) {
        return new ChampionMatchupCatalog("test-only", champions, profiles, false);
    }

    static ChampionMatchupCatalog generatedDiagnosticsCatalog(
            String version,
            ChampionCatalog champions,
            List<ChampionMatchupProfile> profiles
    ) {
        if (version == null || !version.startsWith("diagnostics-")) {
            throw new IllegalArgumentException("Generated catalog must be diagnostics-only");
        }
        ChampionMatchupCatalog catalog =
                new ChampionMatchupCatalog(version, champions, profiles, false);
        int expected = expectedPrimaryPositionPairCount(champions);
        if (catalog.profiles.size() != expected) {
            throw new IllegalArgumentException(
                    "Generated diagnostics catalog requires " + expected + " pairs");
        }
        return catalog;
    }

    static ChampionMatchupCatalog validatedNeutralCatalog(
            ChampionCatalog champions,
            List<ChampionMatchupProfile> profiles
    ) {
        return new ChampionMatchupCatalog(PRODUCTION_VERSION, champions, profiles, true);
    }

    public double contribution(
            ChampionId source,
            ChampionId opponent,
            Position position,
            ProgressionCombatContext context
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(opponent, "opponent");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(context, "context");
        if (source.equals(opponent)) return 0.0;
        ChampionDefinition sourceDefinition = champions.get(source);
        ChampionDefinition opponentDefinition = champions.get(opponent);
        if (sourceDefinition == null || opponentDefinition == null) return 0.0;
        if (sourceDefinition.primaryPosition() != position
                || opponentDefinition.primaryPosition() != position) return 0.0;
        ChampionMatchupPair pair = ChampionMatchupPair.of(sourceDefinition, opponentDefinition);
        ChampionMatchupProfile profile = profiles.get(pair);
        if (profile == null) return 0.0;
        double edge = profile.edge(context);
        double directional = pair.first().equals(source) ? edge : -edge;
        return directional == 0.0 ? 0.0 : directional;
    }

    public String version() { return version; }
    public Map<ChampionMatchupPair, ChampionMatchupProfile> profiles() { return profiles; }
    public Set<ChampionId> championIds() { return champions.keySet(); }

    Optional<ChampionMatchupPair> findPair(
            ChampionId source,
            ChampionId opponent,
            Position position
    ) {
        if (source.equals(opponent)) return Optional.empty();
        ChampionDefinition left = champions.get(source);
        ChampionDefinition right = champions.get(opponent);
        if (left == null || right == null
                || left.primaryPosition() != position
                || right.primaryPosition() != position) return Optional.empty();
        ChampionMatchupPair pair = ChampionMatchupPair.of(left, right);
        return profiles.containsKey(pair) || version.equals(PRODUCTION_VERSION)
                ? Optional.of(pair) : Optional.empty();
    }

    private void validatePair(ChampionMatchupPair pair) {
        ChampionDefinition first = champions.get(pair.first());
        ChampionDefinition second = champions.get(pair.second());
        if (first == null || second == null) throw new IllegalArgumentException("Unknown champion in pair");
        if (first.primaryPosition() != pair.position()
                || second.primaryPosition() != pair.position()) {
            throw new IllegalArgumentException("Cross-position matchup pair");
        }
    }

    private void validateCompleteNeutral() {
        int expected = expectedPrimaryPositionPairCount(champions.values());
        if (profiles.size() != expected) {
            throw new IllegalStateException("Expected " + expected + " matchup pairs");
        }
        for (ChampionMatchupProfile profile : profiles.values()) {
            for (double edge : profile.firstChampionEdges().values()) {
                if (edge != 0.0) throw new IllegalStateException("Production matchup must be neutral");
            }
        }
    }

    private static int expectedPrimaryPositionPairCount(ChampionCatalog champions) {
        return expectedPrimaryPositionPairCount(champions.all());
    }

    private static int expectedPrimaryPositionPairCount(
            java.util.Collection<ChampionDefinition> champions
    ) {
        int expected = 0;
        for (Position position : Position.values()) {
            long count = champions.stream()
                    .filter(champion -> champion.primaryPosition() == position)
                    .count();
            expected += Math.toIntExact(count * (count - 1) / 2);
        }
        return expected;
    }
}
