package com.lolfm.draft;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Outcome-independent, balanced Phase 13G-A2 schedule. */
public final class Phase13GA2AuditSchedule {
    public static final String SCHEDULE_VERSION = "PHASE13G_A2_CIRCULANT_SHA256_V1";
    private static final String PERMUTATION_PREFIX = "phase13g-a-v2-schedule|";

    private Phase13GA2AuditSchedule() { }

    public record UnorderedPair(String pairId, String firstContextId, String secondContextId) {
        public UnorderedPair {
            Objects.requireNonNull(pairId, "pairId");
            Objects.requireNonNull(firstContextId, "firstContextId");
            Objects.requireNonNull(secondContextId, "secondContextId");
            if (firstContextId.equals(secondContextId)) throw new IllegalArgumentException("Self pair: " + pairId);
        }
        public String key() { return firstContextId + "|" + secondContextId; }
    }

    public record GameOneCase(String caseId, String pairId, String blueContextId, String redContextId,
                              int orientation) {
        public GameOneCase {
            Objects.requireNonNull(caseId, "caseId");
            Objects.requireNonNull(pairId, "pairId");
            Objects.requireNonNull(blueContextId, "blueContextId");
            Objects.requireNonNull(redContextId, "redContextId");
            if (blueContextId.equals(redContextId)) throw new IllegalArgumentException("Self Game1 case: " + caseId);
            if (orientation != 0 && orientation != 1) throw new IllegalArgumentException("Invalid orientation: " + orientation);
        }
        public String orderedKey() { return blueContextId + "|" + redContextId; }
    }

    public record FearlessSeriesCase(String seriesId, String blueContextId, String redContextId) {
        public FearlessSeriesCase {
            Objects.requireNonNull(seriesId, "seriesId");
            Objects.requireNonNull(blueContextId, "blueContextId");
            Objects.requireNonNull(redContextId, "redContextId");
            if (blueContextId.equals(redContextId)) throw new IllegalArgumentException("Self Fearless pair: " + seriesId);
        }
        public String unorderedKey() { return first(blueContextId, redContextId) + "|" + second(blueContextId, redContextId); }
    }

    public record ControlledProbeCase(String probeId, String blueContextId, String redContextId) {
        public ControlledProbeCase {
            Objects.requireNonNull(probeId, "probeId");
            Objects.requireNonNull(blueContextId, "blueContextId");
            Objects.requireNonNull(redContextId, "redContextId");
        }
    }

    public record Schedule(List<String> permutation, List<UnorderedPair> unorderedPairs,
                           List<GameOneCase> gameOneCases, List<FearlessSeriesCase> fearlessSeries,
                           List<ControlledProbeCase> controlledProbes, String scheduleHash) {
        public Schedule {
            permutation = List.copyOf(permutation);
            unorderedPairs = List.copyOf(unorderedPairs);
            gameOneCases = List.copyOf(gameOneCases);
            fearlessSeries = List.copyOf(fearlessSeries);
            controlledProbes = List.copyOf(controlledProbes);
            Objects.requireNonNull(scheduleHash, "scheduleHash");
            if (permutation.size() != 24 || unorderedPairs.size() != 60 || gameOneCases.size() != 120
                    || fearlessSeries.size() != 12) throw new IllegalArgumentException("V2 schedule cardinality changed");
        }
        public List<GameOneCase> first(int count) { return gameOneCases.stream().limit(count).toList(); }
    }

    public static Schedule freeze(List<Phase13GASyntheticContextFactory.SyntheticContext> contexts) {
        List<String> ids = contexts.stream().map(Phase13GASyntheticContextFactory.SyntheticContext::id)
                .sorted().toList();
        if (ids.size() != 24) throw new IllegalArgumentException("V2 requires exactly 24 contexts");
        List<String> permutation = ids.stream().sorted(Comparator.comparing((String id) -> sha256(PERMUTATION_PREFIX + id))
                .thenComparing(Comparator.naturalOrder())).toList();
        List<UnorderedPair> pairs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < permutation.size(); index++) {
            addPair(pairs, seen, permutation, index, (index + 1) % permutation.size());
            addPair(pairs, seen, permutation, index, (index + 2) % permutation.size());
        }
        for (int index = 0; index < permutation.size() / 2; index++) {
            addPair(pairs, seen, permutation, index, index + permutation.size() / 2);
        }
        pairs.sort(Comparator.comparing(UnorderedPair::pairId));
        if (pairs.size() != 60) throw new IllegalStateException("V2 pair count changed: " + pairs.size());

        List<GameOneCase> gameOne = new ArrayList<>();
        int ordinal = 1;
        for (UnorderedPair pair : pairs) {
            gameOne.add(new GameOneCase("g1-v2-%03d-%s-blue-vs-%s-red".formatted(ordinal++, pair.firstContextId(), pair.secondContextId()),
                    pair.pairId(), pair.firstContextId(), pair.secondContextId(), 0));
            gameOne.add(new GameOneCase("g1-v2-%03d-%s-blue-vs-%s-red".formatted(ordinal++, pair.secondContextId(), pair.firstContextId()),
                    pair.pairId(), pair.secondContextId(), pair.firstContextId(), 1));
        }
        List<FearlessSeriesCase> fearless = new ArrayList<>();
        for (int index = 0; index < permutation.size() / 2; index++) {
            fearless.add(new FearlessSeriesCase("series-v2-%02d-%s-vs-%s".formatted(index + 1, permutation.get(index), permutation.get(index + 12)),
                    permutation.get(index), permutation.get(index + 12)));
        }
        List<ControlledProbeCase> probes = List.of(
                new ControlledProbeCase("neutral-vs-neutral", "synthetic-neutral", "synthetic-neutral"),
                new ControlledProbeCase("meta-aligned-blue-vs-contrarian-red", "synthetic-meta-aligned", "synthetic-meta-contrarian"),
                new ControlledProbeCase("meta-contrarian-blue-vs-aligned-red", "synthetic-meta-contrarian", "synthetic-meta-aligned"),
                new ControlledProbeCase("flex-wide-blue-vs-narrow-red", "synthetic-flex-wide", "synthetic-flex-narrow"),
                new ControlledProbeCase("flex-narrow-blue-vs-wide-red", "synthetic-flex-narrow", "synthetic-flex-wide"));
        String canonical = String.join("\n", permutation) + "\n" + pairs.stream().map(UnorderedPair::key).collect(Collectors.joining("\n"))
                + "\n" + gameOne.stream().map(GameOneCase::orderedKey).collect(Collectors.joining("\n"))
                + "\n" + fearless.stream().map(value -> value.blueContextId() + "|" + value.redContextId()).collect(Collectors.joining("\n"));
        return new Schedule(permutation, pairs, gameOne, fearless, probes, sha256(canonical));
    }

    private static void addPair(List<UnorderedPair> pairs, Set<String> seen, List<String> permutation, int left, int right) {
        String first = first(permutation.get(left), permutation.get(right));
        String second = second(permutation.get(left), permutation.get(right));
        String key = first + "|" + second;
        if (first.equals(second) || !seen.add(key)) throw new IllegalStateException("Duplicate/self V2 pair: " + key);
        pairs.add(new UnorderedPair("pair-v2-%02d".formatted(pairs.size() + 1), first, second));
    }

    private static String first(String left, String right) { return left.compareTo(right) <= 0 ? left : right; }
    private static String second(String left, String right) { return left.compareTo(right) <= 0 ? right : left; }

    public static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is required", error);
        }
    }
}
