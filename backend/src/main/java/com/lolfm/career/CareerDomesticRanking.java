package com.lolfm.career;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** 2026 LCK §2.7–2.8. Pure decisions over scoped match/game evidence, never display text. */
public final class CareerDomesticRanking {
    public static final String POLICY = "LCK_DOMESTIC_RANKING_AND_SEEDED_TIE_DRAW_V1";
    private CareerDomesticRanking() {}

    public record Game(String winner, int seconds) {
        public Game { if (winner == null || seconds <= 0) throw new IllegalArgumentException("GAME_EVIDENCE_REQUIRED"); }
    }
    public record Match(String identity, String first, String second, int firstWins,
                        int secondWins, List<Game> games) {
        public Match {
            games = List.copyOf(games);
            if (identity == null || first.equals(second) || firstWins == secondWins
                    || firstWins < 0 || secondWins < 0
                    || games.stream().anyMatch(g -> !Set.of(first, second).contains(g.winner()))) {
                throw new IllegalArgumentException("RANKING_MATCH_EVIDENCE_INVALID");
            }
        }
        public String winner() { return firstWins > secondWins ? first : second; }
        public String loser() { return firstWins > secondWins ? second : first; }
        boolean between(Set<String> teams) { return teams.contains(first) && teams.contains(second); }
    }
    public record Record(String team, int wins, int losses, int gameWins, int gameLosses) {
        public int difference() { return gameWins - gameLosses; }
    }
    public record Fraction(long numerator, long denominator) implements Comparable<Fraction> {
        public Fraction { if (denominator <= 0) throw new IllegalArgumentException("EMPTY_AVERAGE"); }
        @Override public int compareTo(Fraction other) {
            return Long.compare(Math.multiplyExact(numerator, other.denominator),
                    Math.multiplyExact(other.numerator, denominator));
        }
    }
    public record Group(List<String> teams, List<String> appliedCriteria) {
        public Group { teams = List.copyOf(teams); appliedCriteria = List.copyOf(appliedCriteria); }
    }
    public record Decision(List<Group> groups, Map<String, Record> records,
                           Map<String, Integer> strengthTwice, Map<String, Fraction> winningGameMeans) {
        public Decision { groups = List.copyOf(groups); records = Map.copyOf(records); strengthTwice = Map.copyOf(strengthTwice); winningGameMeans = Map.copyOf(winningGameMeans); }
        public boolean resolved() { return groups.stream().allMatch(g -> g.teams().size() == 1); }
        public List<String> ordered() { return groups.stream().flatMap(g -> g.teams().stream()).toList(); }
    }

    public static Map<String, Record> records(List<String> teams, List<Match> matches) {
        Map<String, int[]> totals = new LinkedHashMap<>();
        teams.forEach(t -> { if (totals.put(t, new int[4]) != null) throw new IllegalArgumentException("DUPLICATE_TEAM"); });
        Set<String> identities = new HashSet<>();
        for (Match m : matches) {
            if (!identities.add(m.identity()) || !totals.containsKey(m.first()) || !totals.containsKey(m.second()))
                throw new IllegalArgumentException("RANKING_MATCH_SCOPE_OR_DUPLICATE");
            int[] a = totals.get(m.first()), b = totals.get(m.second());
            a[m.firstWins() > m.secondWins() ? 0 : 1]++;
            b[m.secondWins() > m.firstWins() ? 0 : 1]++;
            a[2] += m.firstWins(); a[3] += m.secondWins(); b[2] += m.secondWins(); b[3] += m.firstWins();
        }
        Map<String, Record> result = new LinkedHashMap<>();
        totals.forEach((t, n) -> result.put(t, new Record(t, n[0], n[1], n[2], n[3])));
        return result;
    }

    /** Cup opponent multipliers use their own five-team group, or all ten for integrated seeds. */
    public static Decision cup(List<String> candidates, List<String> allTeams,
                               Map<String, String> membership, List<Match> matches, boolean integrated) {
        Map<String, Record> rows = records(allTeams, matches);
        Map<String, Integer> ranks = new LinkedHashMap<>();
        if (integrated) ranks.putAll(primaryRanks(allTeams, rows, 0));
        else for (String group : membership.values().stream().distinct().sorted().toList())
            ranks.putAll(primaryRanks(allTeams.stream().filter(t -> group.equals(membership.get(t))).toList(), rows, 0));
        Map<String, Integer> strength = strength(allTeams, matches, ranks);
        List<Group> groups = primary(candidates, rows);
        groups = split(groups, "H2H_EXCLUDED_BY_CUP_SCOPE", g -> t -> 0, false);
        groups = split(groups, "STRENGTH_OF_VICTORY", g -> strength::get, true);
        groups = times(groups, matches);
        return new Decision(groups, rows, strength, averages(allTeams, null, matches));
    }

    public static Decision regular(List<String> candidates, List<String> allTeams,
                                   Map<String, String> membership, List<Match> matches) {
        Map<String, Record> rows = records(allTeams, matches);
        List<Group> result = new ArrayList<>();
        for (Group group : primary(candidates, rows)) result.addAll(regularTie(group, matches));
        Map<String, Integer> ranks = new LinkedHashMap<>();
        if (membership.isEmpty()) ranks.putAll(regularRanks(allTeams, rows, matches, 0));
        else for (String name : List.of("LEGEND", "RISE"))
            ranks.putAll(regularRanks(allTeams.stream().filter(t -> name.equals(membership.get(t))).toList(), rows, matches, name.equals("RISE") ? 5 : 0));
        return new Decision(result, rows, strength(allTeams, matches, ranks), averages(allTeams, null, matches));
    }

    static List<Group> regularTie(Group group, List<Match> matches) {
        if (group.teams().size() != 2 && group.teams().size() != 3) return List.of(group);
        List<Group> head = split(List.of(group), "HEAD_TO_HEAD", g -> headWins(g.teams(), matches)::get, true);
        if (group.teams().size() == 3) {
            if (head.size() == 1) return head; // Equal mini-league -> seeded stepladder, not overall game wins.
            List<Group> result = new ArrayList<>();
            head.forEach(g -> result.addAll(g.teams().size() == 2 ? regularTie(g, matches) : List.of(g)));
            return result;
        }
        return split(head, "HEAD_TO_HEAD_GAME_DIFFERENTIAL", g -> headDiff(g.teams(), matches)::get, true);
    }

    public static List<String> tieSeeds(Group group, List<Match> matches,
                                       Map<String, Integer> strength, String drawIdentity) {
        List<Group> groups = List.of(group);
        if (group.teams().size() != 3)
            groups = split(groups, "HEAD_TO_HEAD_SEED", g -> headWins(g.teams(), matches)::get, true);
        groups = split(groups, "HEAD_TO_HEAD_GAME_DIFFERENTIAL_SEED", g -> headDiff(g.teams(), matches)::get, true);
        groups = split(groups, "STRENGTH_OF_VICTORY_SEED", g -> strength::get, true);
        groups = times(groups, matches);
        List<String> ordered = new ArrayList<>();
        for (Group g : groups) ordered.addAll(g.teams().stream().sorted(Comparator.comparing(t -> draw(drawIdentity, t))).toList());
        return ordered;
    }

    public static String draw(String identity, String value) {
        return CareerCompetitionRules.sha256((POLICY + "\n" + identity + "\n" + value + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static List<Group> times(List<Group> groups, List<Match> matches) {
        groups = split(groups, "MEAN_WON_GAMES_AGAINST_TIED_TEAMS", g -> {
            Map<String, Fraction> values = averages(g.teams(), Set.copyOf(g.teams()), matches);
            return values::get;
        }, false);
        return split(groups, "MEAN_WON_GAMES_ALL_OPPONENTS", g -> averages(g.teams(), null, matches)::get, false);
    }

    public static Map<String, Fraction> averages(List<String> teams, Set<String> opponents, List<Match> matches) {
        Map<String, Fraction> result = new LinkedHashMap<>();
        for (String team : teams) {
            long count = 0, seconds = 0;
            for (Match m : matches) {
                if (!team.equals(m.first()) && !team.equals(m.second())) continue;
                String opponent = team.equals(m.first()) ? m.second() : m.first();
                if (opponents != null && !opponents.contains(opponent)) continue;
                for (Game game : m.games()) if (team.equals(game.winner())) { count++; seconds += game.seconds(); }
            }
            if (count > 0) result.put(team, new Fraction(seconds, count));
        }
        return result;
    }

    private static Map<String, Integer> headWins(List<String> teams, List<Match> matches) {
        Map<String, Integer> values = zeros(teams);
        Set<String> scope = Set.copyOf(teams);
        matches.stream().filter(m -> m.between(scope)).forEach(m -> values.compute(m.winner(), (k, v) -> v + 1));
        return values;
    }
    private static Map<String, Integer> headDiff(List<String> teams, List<Match> matches) {
        Map<String, Integer> values = zeros(teams); Set<String> scope = Set.copyOf(teams);
        matches.stream().filter(m -> m.between(scope)).forEach(m -> {
            values.compute(m.first(), (k, v) -> v + m.firstWins() - m.secondWins());
            values.compute(m.second(), (k, v) -> v + m.secondWins() - m.firstWins());
        });
        return values;
    }
    private static Map<String, Integer> zeros(List<String> teams) {
        Map<String, Integer> values = new LinkedHashMap<>(); teams.forEach(t -> values.put(t, 0)); return values;
    }
    private static Map<String, Integer> strength(List<String> teams, List<Match> matches, Map<String, Integer> ranks) {
        Map<String, Integer> values = zeros(teams);
        matches.forEach(m -> values.compute(m.winner(), (k, v) -> v + 11 - ranks.get(m.loser())));
        return values; // exact half-points, no floating-point tie comparison
    }
    private static Map<String, Integer> regularRanks(List<String> teams, Map<String, Record> rows, List<Match> matches, int offset) {
        Map<String, Integer> ranks = new LinkedHashMap<>(); int next = offset + 1;
        for (Group primary : primary(teams, rows)) for (Group group : regularTie(primary, matches)) {
            for (String team : group.teams()) ranks.put(team, next);
            next += group.teams().size();
        }
        return ranks;
    }
    private static Map<String, Integer> primaryRanks(List<String> teams, Map<String, Record> rows, int offset) {
        Map<String, Integer> ranks = new LinkedHashMap<>(); int next = offset + 1;
        for (Group group : primary(teams, rows)) {
            for (String team : group.teams()) ranks.put(team, next);
            next += group.teams().size();
        }
        return ranks;
    }
    private static List<Group> primary(List<String> teams, Map<String, Record> rows) {
        List<Group> groups = split(List.of(new Group(teams, List.of())), "MATCH_WINS", g -> t -> rows.get(t).wins(), true);
        return split(groups, "GAME_DIFFERENTIAL", g -> t -> rows.get(t).difference(), true);
    }
    private static <T extends Comparable<T>> List<Group> split(List<Group> groups, String criterion,
            Function<Group, Function<String, T>> metric, boolean descending) {
        List<Group> result = new ArrayList<>();
        for (Group group : groups) {
            if (group.teams().size() < 2) { result.add(group); continue; }
            Function<String, T> values = metric.apply(group);
            List<String> trace = new ArrayList<>(group.appliedCriteria());
            // No sample is unknown, never a zero-second win or an automatic loss.
            if (group.teams().stream().anyMatch(t -> values.apply(t) == null)) {
                trace.add(criterion + "_NO_COMPARABLE_SAMPLE"); result.add(new Group(group.teams(), trace)); continue;
            }
            trace.add(criterion);
            Comparator<String> comparator = Comparator.comparing(values);
            if (descending) comparator = comparator.reversed();
            List<String> ordered = group.teams().stream().sorted(comparator).toList();
            for (int i = 0; i < ordered.size();) {
                int end = i + 1;
                while (end < ordered.size() && values.apply(ordered.get(i)).compareTo(values.apply(ordered.get(end))) == 0) end++;
                result.add(new Group(ordered.subList(i, end), trace)); i = end;
            }
        }
        return result;
    }
}
