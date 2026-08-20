package com.lolfm.draft;

import com.lolfm.champion.ChampionDefinition;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.Position;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic, player-free draft contexts for the Phase 13G-A structural audit.
 *
 * <p>The values in this class are audit inputs only.  They are deliberately not
 * wired to production player resources or to the MatchSimulator's player-rating
 * path.</p>
 */
public final class Phase13GASyntheticContextFactory {
    public static final String ALGORITHM_VERSION = "SYNTHETIC_PROFICIENCY_SHA256_V1";
    public static final int HASH_MIN = 6;
    public static final int HASH_MAX = 20;
    private static final int FIXED_CONTEXT_COUNT = 12;
    private static final int HASH_CONTEXT_COUNT = 12;

    private Phase13GASyntheticContextFactory() { }

    public record SyntheticContext(
            String id,
            String kind,
            DraftTeamContext draftContext,
            Map<ChampionRoleKey, Integer> proficiencyByRole
    ) {
        public SyntheticContext {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("context id is required");
            if (kind == null || kind.isBlank()) throw new IllegalArgumentException("context kind is required");
            proficiencyByRole = Map.copyOf(proficiencyByRole);
        }

        public String canonicalProficiency() {
            return proficiencyByRole.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(ChampionRoleKey::stableId)))
                    .map(entry -> entry.getKey().stableId() + "=" + entry.getValue())
                    .collect(Collectors.joining("\n"));
        }
    }

    public static List<SyntheticContext> create(DraftResourceSet resources) {
        List<ChampionRoleKey> legal = resources.champions().catalog().legalRoleKeys().stream()
                .sorted(Comparator.comparing(ChampionRoleKey::stableId)).toList();
        if (legal.size() != 216) throw new IllegalStateException("Expected 216 legal role keys, got " + legal.size());

        List<SyntheticContext> result = new ArrayList<>();
        result.add(fixed("synthetic-neutral", "NEUTRAL", legal, key -> 10));
        result.add(fixed("synthetic-high-baseline", "HIGH_BASELINE", legal, key -> 16));
        result.add(fixed("synthetic-low-baseline", "LOW_BASELINE", legal, key -> 8));
        result.add(specialist("synthetic-top-specialist", Position.TOP, legal));
        result.add(specialist("synthetic-jungle-specialist", Position.JUNGLE, legal));
        result.add(specialist("synthetic-mid-specialist", Position.MID, legal));
        result.add(specialist("synthetic-adc-specialist", Position.ADC, legal));
        result.add(specialist("synthetic-support-specialist", Position.SUPPORT, legal));
        result.add(flexWide(resources, legal));
        result.add(flexNarrow(resources, legal));
        result.add(metaContrarian(resources, legal));
        result.add(metaAligned(resources, legal));

        for (int index = 1; index <= HASH_CONTEXT_COUNT; index++) {
            String id = "synthetic-hash-balanced-%02d".formatted(index);
            result.add(fixed(id, "HASH_BALANCED", legal, key -> stableHashValue(id, key)));
        }
        if (result.size() != FIXED_CONTEXT_COUNT + HASH_CONTEXT_COUNT) {
            throw new IllegalStateException("Synthetic context count mismatch: " + result.size());
        }
        return List.copyOf(result);
    }

    private static SyntheticContext specialist(String id, Position specialist,
                                               List<ChampionRoleKey> legal) {
        return fixed(id, specialist.name() + "_SPECIALIST", legal,
                key -> key.position() == specialist ? 17 + stableHashValue(id, key) % 4 : 10);
    }

    private static SyntheticContext flexWide(DraftResourceSet resources, List<ChampionRoleKey> legal) {
        Set<ChampionId> flex = resources.champions().catalog().all().stream()
                .filter(champion -> champion.supportedPositions().size() > 1)
                .map(ChampionDefinition::id).collect(Collectors.toSet());
        return fixed("synthetic-flex-wide", "FLEX_WIDE", legal,
                key -> flex.contains(key.championId()) ? 19 : 10);
    }

    private static SyntheticContext flexNarrow(DraftResourceSet resources, List<ChampionRoleKey> legal) {
        Map<ChampionId, Position> primary = resources.champions().catalog().all().stream()
                .collect(Collectors.toMap(ChampionDefinition::id, ChampionDefinition::primaryPosition));
        Set<ChampionId> flex = resources.champions().catalog().all().stream()
                .filter(champion -> champion.supportedPositions().size() > 1)
                .map(ChampionDefinition::id).collect(Collectors.toSet());
        return fixed("synthetic-flex-narrow", "FLEX_NARROW", legal, key ->
                flex.contains(key.championId())
                        ? (primary.get(key.championId()) == key.position() ? 20 : 7)
                        : 10);
    }

    private static SyntheticContext metaContrarian(DraftResourceSet resources, List<ChampionRoleKey> legal) {
        return fixed("synthetic-meta-contrarian", "META_CONTRARIAN", legal, key ->
                resources.meta().priority(key) <= 8 ? 20 : 10);
    }

    private static SyntheticContext metaAligned(DraftResourceSet resources, List<ChampionRoleKey> legal) {
        return fixed("synthetic-meta-aligned", "META_ALIGNED", legal, key ->
                resources.meta().priority(key) >= 14 ? 20 : 10);
    }

    private interface ValueFunction {
        int value(ChampionRoleKey key);
    }

    private static SyntheticContext fixed(String id, String kind, List<ChampionRoleKey> legal,
                                          ValueFunction values) {
        LinkedHashMap<ChampionRoleKey, Integer> byRole = new LinkedHashMap<>();
        legal.forEach(key -> byRole.put(key, validate(values.value(key))));
        return new SyntheticContext(id, kind, context(byRole), byRole);
    }

    private static DraftTeamContext context(Map<ChampionRoleKey, Integer> values) {
        EnumMap<Position, ChampionProficiencies> byPosition = new EnumMap<>(Position.class);
        for (Position position : Position.values()) {
            Map<ChampionRoleKey, Integer> roleValues = new HashMap<>();
            values.forEach((key, value) -> {
                if (key.position() == position) roleValues.put(key, value);
            });
            byPosition.put(position, new ChampionProficiencies(roleValues));
        }
        return new DraftTeamContext(byPosition);
    }

    private static int stableHashValue(String scenarioId, ChampionRoleKey key) {
        String input = scenarioId + ":" + key.championId().value() + ":" + key.position().name();
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is required", error);
        }
        int unsigned = ((digest[0] & 0xff) << 24)
                | ((digest[1] & 0xff) << 16)
                | ((digest[2] & 0xff) << 8)
                | (digest[3] & 0xff);
        int span = HASH_MAX - HASH_MIN + 1;
        return HASH_MIN + Math.floorMod(unsigned, span);
    }

    private static int validate(int value) {
        if (value < 1 || value > 20) throw new IllegalArgumentException("Synthetic proficiency out of range: " + value);
        return value;
    }
}
