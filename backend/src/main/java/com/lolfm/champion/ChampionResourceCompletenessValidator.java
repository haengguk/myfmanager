package com.lolfm.champion;

import com.lolfm.composition.ChampionCompositionProfileCatalog;
import com.lolfm.domain.Position;
import java.util.Set;
import java.util.stream.Collectors;

/** Exact cross-catalog coverage gate derived solely from ChampionCatalog identity. */
public final class ChampionResourceCompletenessValidator {
    private ChampionResourceCompletenessValidator() {}

    public static void validate(
            ChampionCatalog champions,
            ChampionPowerProfileCatalog power,
            ChampionRoleMatchupProfileCatalog matchup,
            ChampionCompositionProfileCatalog composition,
            ChampionJungleClearProfileCatalog jungleClear
    ) {
        Set<ChampionId> championIds = champions.all().stream()
                .map(ChampionDefinition::id).collect(Collectors.toSet());
        Set<ChampionId> powerIds = power.all().stream()
                .map(ChampionPowerProfile::championId).collect(Collectors.toSet());
        requireExact("Champion Power", championIds, powerIds);

        Set<ChampionRoleKey> legalRoles = champions.legalRoleKeys();
        requireExact("Matchup", legalRoles, matchup.profiles().keySet());
        requireExact("Composition", legalRoles, composition.profiles().keySet());

        Set<ChampionRoleKey> legalJungleRoles = legalRoles.stream()
                .filter(key -> key.position() == Position.JUNGLE).collect(Collectors.toSet());
        requireExact("Jungle Clear", legalJungleRoles, jungleClear.profiles().keySet());
    }

    private static <T> void requireExact(String catalog, Set<T> expected, Set<T> actual) {
        if (!actual.equals(expected)) {
            Set<T> missing = expected.stream().filter(value -> !actual.contains(value)).collect(Collectors.toSet());
            Set<T> extra = actual.stream().filter(value -> !expected.contains(value)).collect(Collectors.toSet());
            throw new IllegalStateException(catalog + " coverage mismatch; missing=" + missing + ", extra=" + extra);
        }
    }
}
