package com.lolfm.champion;

import com.lolfm.domain.Position;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ChampionRoleMatchupProfileCatalog {
    public static final String PROTOTYPE_VERSION =
            "focused-10-role-profile-prototype-v1";
    public static final String PRODUCTION_VERSION =
            ChampionMatchupProductionPolicy.PROFILE_VERSION;

    private final String version;
    private final boolean prototypeOnly;
    private final Map<ChampionRoleKey, ChampionRoleMatchupProfile> profiles;

    private ChampionRoleMatchupProfileCatalog(
            String version,
            boolean prototypeOnly,
            List<ChampionRoleMatchupProfile> values
    ) {
        this.version = Objects.requireNonNull(version, "version");
        this.prototypeOnly = prototypeOnly;
        LinkedHashMap<ChampionRoleKey, ChampionRoleMatchupProfile> indexed =
                new LinkedHashMap<>();
        for (ChampionRoleMatchupProfile profile : List.copyOf(values)) {
            if (!profile.profileVersion().equals(version)) {
                throw new IllegalArgumentException("Profile version mismatch");
            }
            if (indexed.put(profile.roleKey(), profile) != null) {
                throw new IllegalArgumentException(
                        "Duplicate role profile " + profile.roleKey());
            }
        }
        profiles = Map.copyOf(indexed);
    }

    public static ChampionRoleMatchupProfileCatalog production() {
        return ChampionMatchupProfileResourceLoader.loadDefault();
    }

    static ChampionRoleMatchupProfileCatalog materialized(String version, boolean prototypeOnly, List<ChampionRoleMatchupProfile> values) {
        return new ChampionRoleMatchupProfileCatalog(version, prototypeOnly, values);
    }

    public void validateCoverage(MatchChampionAssignments assignments) {
        Objects.requireNonNull(assignments, "assignments");
        for (ChampionAssignment assignment : assignments.asMap().values()) {
            ChampionRoleKey key = new ChampionRoleKey(
                    assignment.championId(), assignment.selectedPosition());
            if (!profiles.containsKey(key)) {
                throw new UnsupportedChampionRoleMatchupProfileException(key);
            }
        }
    }

    static ChampionRoleMatchupProfileCatalog diagnosticsCandidate(
            String version, List<ChampionRoleMatchupProfile> values
    ) {
        if (!version.contains("candidate")) {
            throw new IllegalArgumentException("Diagnostics candidate version required");
        }
        return new ChampionRoleMatchupProfileCatalog(version, true, values);
    }

    public static ChampionRoleMatchupProfileCatalog prototype() {
        return new ChampionRoleMatchupProfileCatalog(
                PROTOTYPE_VERSION, true, List.of(
                profile("renekton", Position.TOP,
                        5, 6, 16, 12, 12, 15, 12, 13, 5, 15, 15, 14, 10, 10, 8),
                profile("jax", Position.TOP,
                        4, 4, 12, 17, 14, 14, 9, 10, 10, 7, 14, 11, 8, 14, 15),
                profile("lee-sin", Position.JUNGLE,
                        5, 8, 15, 10, 18, 18, 12, 15, 10, 12, 10, 5, 15, 11, 6),
                profile("viego", Position.JUNGLE,
                        5, 5, 12, 16, 13, 13, 9, 9, 7, 14, 10, 6, 10, 8, 12),
                profile("leblanc", Position.MID,
                        13, 13, 18, 5, 20, 18, 10, 8, 18, 3, 4, 8, 19, 11, 2),
                profile("viktor", Position.MID,
                        16, 15, 13, 16, 4, 2, 10, 5, 10, 4, 7, 19, 7, 13, 12),
                profile("lucian", Position.ADC,
                        11, 12, 16, 14, 16, 11, 2, 5, 12, 3, 6, 14, 7, 7, 8),
                profile("jinx", Position.ADC,
                        18, 13, 10, 19, 6, 1, 8, 5, 8, 2, 5, 17, 8, 6, 15),
                profile("nautilus", Position.SUPPORT,
                        5, 4, 10, 3, 5, 16, 20, 20, 7, 2, 18, 5, 19, 12, 3),
                profile("lulu", Position.SUPPORT,
                        14, 11, 5, 4, 8, 2, 14, 4, 19, 8, 6, 8, 5, 20, 2)));
    }

    public Optional<ChampionRoleMatchupProfile> find(ChampionRoleKey key) {
        return Optional.ofNullable(profiles.get(Objects.requireNonNull(key, "key")));
    }

    public String version() { return version; }
    public boolean prototypeOnly() { return prototypeOnly; }
    public Map<ChampionRoleKey, ChampionRoleMatchupProfile> profiles() {
        return profiles;
    }

    private static ChampionRoleMatchupProfile profile(
            String champion, Position position,
            int range, int poke, int burst, int sustained, int mobility,
            int gapClose, int crowdControl, int engage, int disengage, int sustain,
            int durability, int wave, int pick, int antiDive, int antiTank
    ) {
        EnumMap<ChampionMatchupTrait, Integer> traits =
                new EnumMap<>(ChampionMatchupTrait.class);
        traits.put(ChampionMatchupTrait.RANGE_CONTROL, range);
        traits.put(ChampionMatchupTrait.POKE, poke);
        traits.put(ChampionMatchupTrait.BURST, burst);
        traits.put(ChampionMatchupTrait.SUSTAINED_DAMAGE, sustained);
        traits.put(ChampionMatchupTrait.MOBILITY, mobility);
        traits.put(ChampionMatchupTrait.GAP_CLOSE, gapClose);
        traits.put(ChampionMatchupTrait.CROWD_CONTROL, crowdControl);
        traits.put(ChampionMatchupTrait.ENGAGE, engage);
        traits.put(ChampionMatchupTrait.DISENGAGE, disengage);
        traits.put(ChampionMatchupTrait.SUSTAIN, sustain);
        traits.put(ChampionMatchupTrait.DURABILITY, durability);
        traits.put(ChampionMatchupTrait.WAVE_CONTROL, wave);
        traits.put(ChampionMatchupTrait.PICK, pick);
        traits.put(ChampionMatchupTrait.ANTI_DIVE, antiDive);
        traits.put(ChampionMatchupTrait.ANTI_TANK, antiTank);
        return new ChampionRoleMatchupProfile(
                new ChampionRoleKey(new ChampionId(champion), position),
                PROTOTYPE_VERSION, traits);
    }
}
