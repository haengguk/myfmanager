package com.lolfm.composition;

import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.Position;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit kit-function profiles for the approved thirty champion-role keys. */
public final class ThirtyChampionCompositionProfiles {
    public static final String VERSION = "thirty-champion-composition-profile-candidate-v2";
    private static final List<Position> POSITION_ORDER = List.of(
            Position.TOP, Position.JUNGLE, Position.MID, Position.ADC, Position.SUPPORT);
    private static final Map<ChampionRoleKey, ChampionCompositionProfile> PROFILES = createProfiles();
    private static final String PROFILE_HASH = sha256(canonicalSerialization(PROFILES));

    private ThirtyChampionCompositionProfiles() {}

    public static Map<ChampionRoleKey, ChampionCompositionProfile> all() { return PROFILES; }
    public static String profileHash() { return PROFILE_HASH; }
    public static String canonicalSerialization() { return canonicalSerialization(PROFILES); }

    static String canonicalSerialization(Map<ChampionRoleKey, ChampionCompositionProfile> profiles) {
        StringBuilder out = new StringBuilder(VERSION).append('\n');
        for (Position position : POSITION_ORDER) {
            profiles.entrySet().stream()
                    .filter(entry -> entry.getKey().position() == position)
                    .sorted(Map.Entry.comparingByKey((left, right) ->
                            left.stableId().compareTo(right.stableId())))
                    .forEach(entry -> {
                        ChampionCompositionProfile profile = entry.getValue();
                        out.append(entry.getKey().stableId());
                        for (CompositionCapability capability : CompositionCapability.values()) {
                            out.append('|').append(profile.capability(capability));
                        }
                        DamageChannelProfile damage = profile.damageProfile();
                        out.append('|').append(damage.physicalThreat())
                                .append('|').append(damage.magicThreat())
                                .append('|').append(damage.trueDamageThreat()).append('\n');
                    });
        }
        return out.toString();
    }

    private static Map<ChampionRoleKey, ChampionCompositionProfile> createProfiles() {
        LinkedHashMap<ChampionRoleKey, ChampionCompositionProfile> values = new LinkedHashMap<>();
        add(values, "renekton", Position.TOP, c(10,12,4,13,4,11,4,8,12,5,16,10,14,15,14), d(20,0,0));
        add(values, "jax", Position.TOP, c(9,11,7,12,7,8,3,11,11,5,19,15,18,12,17), d(18,3,0));
        add(values, "ornn", Position.TOP, c(19,17,12,20,15,14,6,8,12,17,7,8,6,8,10), d(4,12,0));
        add(values, "gwen", Position.TOP, c(6,10,8,7,4,5,8,9,11,9,18,18,20,10,15), d(2,20,3));
        add(values, "kennen", Position.TOP, c(17,16,8,8,5,10,13,9,14,15,11,9,13,16,18), d(1,19,0));
        add(values, "ksante", Position.TOP, c(14,13,14,19,16,13,3,6,10,12,16,8,10,8,14), d(12,3,2));

        add(values, "lee-sin", Position.JUNGLE, c(13,15,13,11,10,15,6,5,8,7,14,12,13,14,17), d(20,0,0));
        add(values, "viego", Position.JUNGLE, c(8,13,4,9,3,10,2,8,9,4,13,19,19,14,18), d(17,7,0));
        add(values, "sejuani", Position.JUNGLE, c(18,18,14,19,14,16,5,4,8,16,6,5,5,7,13), d(2,10,0));
        add(values, "vi", Position.JUNGLE, c(18,16,5,14,6,18,3,6,9,7,10,15,12,15,20), d(18,2,0));
        add(values, "nidalee", Position.JUNGLE, c(4,8,15,4,8,10,18,11,8,10,15,18,14,15,13), d(2,20,0));
        add(values, "maokai", Position.JUNGLE, c(17,17,16,18,17,16,10,5,9,20,5,6,6,8,11), d(2,12,0));

        add(values, "leblanc", Position.MID, c(7,12,14,4,3,19,15,11,9,7,15,6,7,20,20), d(1,20,0));
        add(values, "viktor", Position.MID, c(5,11,13,6,8,8,16,17,20,17,7,13,18,16,5), d(0,20,0));
        add(values, "azir", Position.MID, c(10,14,19,8,12,7,15,18,18,16,12,19,20,10,7), d(2,20,0));
        add(values, "orianna", Position.MID, c(15,18,17,7,17,12,14,15,18,18,5,12,14,15,8), d(1,19,0));
        add(values, "ahri", Position.MID, c(8,14,13,5,7,18,13,10,14,9,14,8,10,18,17), d(3,12,2));
        add(values, "sylas", Position.MID, c(13,15,8,13,5,13,4,8,12,8,14,12,16,17,17), d(3,18,0));

        add(values, "lucian", Position.ADC, c(3,10,9,3,2,6,12,15,14,3,12,17,17,17,8), d(20,0,0));
        add(values, "jinx", Position.ADC, c(4,12,8,2,3,5,15,20,18,11,8,20,20,14,3), d(20,1,0));
        add(values, "ezreal", Position.ADC, c(2,8,18,2,4,7,20,17,15,5,14,15,16,15,4), d(18,6,0));
        add(values, "kaisa", Position.ADC, c(6,14,10,4,3,8,8,14,13,4,14,19,19,18,18), d(10,16,0));
        add(values, "aphelios", Position.ADC, c(4,11,5,3,5,5,10,17,18,12,6,20,20,16,3), d(20,3,0));
        add(values, "varus", Position.ADC, c(12,15,7,3,5,18,19,17,16,14,5,18,16,19,4), d(15,10,0));

        add(values, "nautilus", Position.SUPPORT, c(20,18,8,17,5,19,3,3,5,12,2,3,2,5,15), d(2,2,0));
        add(values, "lulu", Position.SUPPORT, c(2,9,20,4,20,8,9,7,8,12,2,2,4,5,1), d(0,5,0));
        add(values, "rakan", Position.SUPPORT, c(19,19,17,10,16,14,5,4,6,12,5,2,3,5,16), d(1,6,0));
        add(values, "braum", Position.SUPPORT, c(10,16,20,16,20,8,6,3,5,15,2,3,3,4,7), d(2,3,0));
        add(values, "renata-glasc", Position.SUPPORT, c(8,15,19,5,19,13,13,8,10,18,2,3,4,7,3), d(0,7,0));
        add(values, "bard", Position.SUPPORT, c(11,13,18,6,12,17,12,7,9,14,9,2,6,10,8), d(3,7,0));
        if (values.size() != 30) throw new IllegalStateException("Expected thirty explicit profiles");
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static void add(Map<ChampionRoleKey, ChampionCompositionProfile> out, String champion,
                            Position position, int[] capabilities, DamageChannelProfile damage) {
        if (capabilities.length != CompositionCapability.values().length) {
            throw new IllegalArgumentException("Every profile requires fifteen explicit capabilities");
        }
        EnumMap<CompositionCapability, Integer> ratings = new EnumMap<>(CompositionCapability.class);
        for (int index = 0; index < capabilities.length; index++) {
            ratings.put(CompositionCapability.values()[index], capabilities[index]);
        }
        ChampionRoleKey key = new ChampionRoleKey(new ChampionId(champion), position);
        if (out.put(key, new ChampionCompositionProfile(key, ratings, damage)) != null) {
            throw new IllegalArgumentException("Duplicate composition profile " + key.stableId());
        }
    }

    private static int[] c(int... values) { return values; }
    private static DamageChannelProfile d(int physical, int magic, int trueDamage) {
        return new DamageChannelProfile(physical, magic, trueDamage);
    }
    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
