package com.lolfm.career;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Small resumable §2.8 bracket. Results are supplied by the durable completion ledger. */
final class CareerDomesticTiebreak {
    record Outcome(String winner, String loser) {}
    record Pending(String matchId, String first, String second) {}
    record Progress(List<String> ranking, List<Pending> pending) {}
    private final String identity;
    private final int lastRelevantPlace;
    private final Map<String, Outcome> outcomes;
    private final List<CareerDomesticRanking.Match> evidence;
    private final Map<String, Integer> strength;
    private final List<Pending> pending = new ArrayList<>();

    private CareerDomesticTiebreak(String identity, Map<String, Outcome> outcomes,
                                  List<CareerDomesticRanking.Match> evidence, Map<String, Integer> strength, int lastRelevantPlace) {
        this.lastRelevantPlace = lastRelevantPlace;
        this.identity = identity; this.outcomes = outcomes; this.evidence = evidence; this.strength = strength;
    }
    static Progress advance(String identity, List<String> seeds, Map<String, Outcome> outcomes,
                            List<CareerDomesticRanking.Match> evidence, Map<String, Integer> strength) {
        return advance(identity, seeds, outcomes, evidence, strength, seeds.size());
    }
    static Progress advance(String identity, List<String> seeds, Map<String, Outcome> outcomes,
                            List<CareerDomesticRanking.Match> evidence, Map<String, Integer> strength, int lastRelevantPlace) {
        var bracket = new CareerDomesticTiebreak(identity, outcomes, evidence, strength, lastRelevantPlace);
        List<String> ranking = bracket.rank(seeds, "", false, 1);
        return new Progress(ranking == null ? List.of() : List.copyOf(ranking), List.copyOf(bracket.pending));
    }
    private List<String> rank(List<String> seeds, String path, boolean recheckHeadToHead, int firstPlace) {
        int n = seeds.size();
        if (n == 1) return seeds;
        // No qualification/prize distinction: retain a deterministic placement order,
        // with no sporting win, fixture, or result receipt invented for the shared rank.
        if (firstPlace > lastRelevantPlace) return seed(seeds, path + "SHARED_PLACEMENT");
        if (recheckHeadToHead && n <= 3) {
            var groups = CareerDomesticRanking.regularTie(new CareerDomesticRanking.Group(seeds, List.of()), evidence);
            if (groups.size() > 1) {
                List<String> ordered = new ArrayList<>(); boolean waiting = false;
                for (int i = 0; i < groups.size(); i++) {
                    var ranked = rank(groups.get(i).teams(), path + "H" + i, false, firstPlace + groups.subList(0, i).stream().mapToInt(g -> g.teams().size()).sum());
                    if (ranked == null) waiting = true; else ordered.addAll(ranked);
                }
                return waiting ? null : ordered;
            }
        }
        if (n == 2) {
            Outcome m = match(path + "F", seeds.get(0), seeds.get(1));
            return m == null ? null : List.of(m.winner(), m.loser());
        }
        if (n == 3) {
            var ordered = seed(seeds, path);
            Outcome lower = match(path + "A", ordered.get(1), ordered.get(2));
            if (lower == null) return null;
            Outcome upper = match(path + "B", ordered.get(0), lower.winner());
            return upper == null ? null : List.of(upper.winner(), upper.loser(), lower.loser());
        }
        if (n == 4) {
            var draw = shuffle(seeds, path);
            Outcome a = match(path + "A", draw.get(0), draw.get(1));
            Outcome b = match(path + "B", draw.get(2), draw.get(3));
            if (a == null || b == null) return null;
            Outcome upper = match(path + "C", a.winner(), b.winner());
            List<String> lower = firstPlace + 2 > lastRelevantPlace ? seed(List.of(a.loser(), b.loser()), path + "SHARED_PLACEMENT") : null;
            if (lower == null) { Outcome m = match(path + "D", a.loser(), b.loser()); if (m != null) lower = List.of(m.winner(), m.loser()); }
            if (upper == null || lower == null) return null;
            return List.of(upper.winner(), upper.loser(), lower.get(0), lower.get(1));
        }
        if (n == 5 || n == 9) {
            var ordered = seed(seeds, path);
            Outcome entry = match(path + "Q", ordered.get(n - 2), ordered.get(n - 1));
            if (entry == null) return null;
            List<String> upper = new ArrayList<>(ordered.subList(0, n - 2)); upper.add(entry.winner());
            List<String> ranked = rank(upper, path + "U", false, firstPlace);
            if (ranked == null) return null;
            ranked = new ArrayList<>(ranked); ranked.add(entry.loser()); return ranked;
        }
        int qualifiers = n == 6 || n == 10 ? 4 : n == 7 ? 6 : 8;
        if (n < 6 || n > 10) throw new IllegalArgumentException("TIE_TEAM_COUNT");
        var ordered = seed(seeds, path);
        var draw = shuffle(ordered.subList(n - qualifiers, n), path);
        List<String> upper = new ArrayList<>(ordered.subList(0, n - qualifiers));
        List<String> lower = new ArrayList<>();
        boolean waiting = false;
        for (int i = 0; i < qualifiers; i += 2) {
            Outcome m = match(path + "Q" + i / 2, draw.get(i), draw.get(i + 1));
            if (m == null) waiting = true; else { upper.add(m.winner()); lower.add(m.loser()); }
        }
        if (waiting) return null;
        List<String> top = rank(upper, path + "U", false, firstPlace);
        List<String> bottom = rank(lower, path + "L", true, firstPlace + upper.size());
        if (top == null || bottom == null) return null;
        List<String> result = new ArrayList<>(top); result.addAll(bottom); return result;
    }
    private List<String> seed(List<String> teams, String path) {
        return CareerDomesticRanking.tieSeeds(new CareerDomesticRanking.Group(teams, List.of()), evidence, strength, identity + path);
    }
    private List<String> shuffle(List<String> teams, String path) {
        return teams.stream().sorted(Comparator.comparing(t -> CareerDomesticRanking.draw(identity + path + "DRAW", t))).toList();
    }
    private Outcome match(String suffix, String first, String second) {
        String id = identity + "_" + suffix;
        Outcome prior = outcomes.get(id);
        if (prior != null) {
            if (!java.util.Set.of(first, second).equals(java.util.Set.of(prior.winner(), prior.loser())))
                throw new IllegalStateException("TIE_RESULT_SCOPE_MISMATCH");
            return prior;
        }
        var owners = CareerDomesticRanking.tieSeeds(new CareerDomesticRanking.Group(List.of(first, second), List.of()), evidence, strength, id + "ROFS");
        pending.add(new Pending(id, owners.get(0), owners.get(1)));
        return null;
    }
}
