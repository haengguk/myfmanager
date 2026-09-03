package com.lolfm.career;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable bracket transition model over verified compact Series receipts. */
public record CareerCompetitionAggregate(
        String careerId,
        int seasonYear,
        String competitionId,
        String managedTeamCode,
        long careerRootSeed,
        String sourceInputHash,
        long revision,
        List<Fixture> fixtures,
        Map<String, String> qualificationOutputs,
        String stateHash
) {
    public static final String STATE_SCHEMA = "CAREER_COMPETITION_AGGREGATE_V1";
    public static final String SEED_ALGORITHM =
            "CAREER_COMPETITION_MATCH_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1";

    public CareerCompetitionAggregate {
        CareerIdentity.requireSha256(sourceInputHash, "sourceInputHash");
        CareerIdentity.requireSha256(stateHash, "stateHash");
        if (seasonYear < 2026 || revision < 0 || fixtures == null
                || qualificationOutputs == null) {
            throw new IllegalArgumentException("Competition aggregate invariant");
        }
        fixtures = List.copyOf(fixtures);
        qualificationOutputs = Map.copyOf(qualificationOutputs);
    }

    public static CareerCompetitionAggregate materialize(
            CareerCompetitionRules rules,
            String careerId,
            int seasonYear,
            String competitionId,
            String managedTeamCode,
            long careerRootSeed,
            String sourceInputHash,
            List<SeededTeam> seeds
    ) {
        Objects.requireNonNull(rules, "rules");
        CareerCompetitionRules.CompetitionRule rule = rules.rule(competitionId);
        if (!"RULE_SOURCE_COMPLETE".equals(rule.ruleStatus())
                || rule.matches().isEmpty()) {
            throw new IllegalStateException("COMPETITION_RULE_NOT_EXECUTABLE");
        }
        Map<Integer, String> ranked = indexSeeds(seeds);
        ArrayList<Fixture> fixtures = new ArrayList<>();
        for (CareerCompetitionRules.MatchRule match : rule.matches()) {
            String first = resolveInitial(match.first(), ranked);
            String second = resolveInitial(match.second(), ranked);
            String seriesId = identity("series_", canonicalIdentity(careerId, seasonYear,
                    competitionId, match.matchId(), "series"));
            long rootSeed = deriveSeed(careerRootSeed, seasonYear, competitionId,
                    match.matchId());
            fixtures.add(new Fixture(match.matchId(), identity("competition_fixture_",
                    canonicalIdentity(careerId, seasonYear, competitionId,
                            match.matchId(), "fixture")), rules.projectDate(seasonYear,
                    match.monthDay()), rule.seriesFormat(), Boolean.TRUE.equals(
                    rule.hardFearless()), match.first(), match.second(), first, second,
                    first != null && second != null ? "READY" : "WAITING_FOR_PREDECESSOR",
                    executionMode(managedTeamCode, first, second), rootSeed, seriesId,
                    match.winnerOutputs(), match.loserOutputs(), null, null, null));
        }
        return create(careerId, seasonYear, competitionId, managedTeamCode,
                careerRootSeed, sourceInputHash, 0, fixtures, Map.of());
    }

    public CompletionResult applyVerifiedCompletion(
            String matchId,
            String seriesId,
            String firstTeamCode,
            String secondTeamCode,
            String winnerTeamCode,
            String receiptHash
    ) {
        CareerIdentity.requireSha256(receiptHash, "receiptHash");
        int index = fixtureIndex(matchId);
        Fixture fixture = fixtures.get(index);
        if (fixture.receiptHash() != null) {
            if (fixture.receiptHash().equals(receiptHash)
                    && fixture.seriesId().equals(seriesId)
                    && fixture.winnerTeamCode().equals(winnerTeamCode)) {
                return new CompletionResult(this, true);
            }
            throw new IllegalStateException("COMPETITION_MATCH_ALREADY_COMPLETED");
        }
        if (!"READY".equals(fixture.lifecycleStatus())
                || !fixture.seriesId().equals(seriesId)
                || !Objects.equals(fixture.firstTeamCode(), firstTeamCode)
                || !Objects.equals(fixture.secondTeamCode(), secondTeamCode)
                || !Set.of(firstTeamCode, secondTeamCode).contains(winnerTeamCode)) {
            throw new IllegalArgumentException("COMPETITION_COMPLETION_SCOPE_MISMATCH");
        }
        String loser = winnerTeamCode.equals(firstTeamCode)
                ? secondTeamCode : firstTeamCode;
        ArrayList<Fixture> nextFixtures = new ArrayList<>(fixtures);
        nextFixtures.set(index, fixture.completed(winnerTeamCode, loser, receiptHash));
        Map<String, Outcome> outcomes = outcomes(nextFixtures);
        // The selectors stored on every fixture are sufficient for ordered propagation.
        for (int candidate = index + 1; candidate < nextFixtures.size(); candidate++) {
            Fixture pending = nextFixtures.get(candidate);
            String first = resolve(pending.firstSelector(), pending.firstTeamCode(), outcomes);
            String second = resolve(pending.secondSelector(), pending.secondTeamCode(), outcomes);
            String status = first != null && second != null
                    ? "READY" : "WAITING_FOR_PREDECESSOR";
            nextFixtures.set(candidate, pending.withParticipants(first, second, status,
                    executionMode(managedTeamCode, first, second)));
        }
        LinkedHashMap<String, String> outputs = new LinkedHashMap<>(
                qualificationOutputs);
        fixture.winnerOutputs().forEach(value ->
                putOutput(outputs, value, winnerTeamCode));
        fixture.loserOutputs().forEach(value ->
                putOutput(outputs, value, loser));
        CareerCompetitionAggregate next = create(careerId, seasonYear, competitionId,
                managedTeamCode, careerRootSeed, sourceInputHash, revision + 1,
                nextFixtures, outputs);
        return new CompletionResult(next, false);
    }

    /** Deterministic 40-fixture Legend/Rise schedule with carried R1~2 records. */
    public static R3R4Stage materializeR3R4(
            String careerId,
            int seasonYear,
            String managedTeamCode,
            long careerRootSeed,
            String sourceInputHash,
            List<SeededTeam> ranking
    ) {
        Map<Integer, String> indexed = indexSeeds(ranking);
        List<SeededTeam> legend = ranking.stream().filter(value -> value.seed() <= 5)
                .toList();
        List<SeededTeam> rise = ranking.stream().filter(value -> value.seed() > 5)
                .toList();
        if (indexed.size() != 10 || legend.size() != 5 || rise.size() != 5) {
            throw new IllegalArgumentException("R3_R4_SPLIT_REQUIRES_TEN_RANKED_TEAMS");
        }
        ArrayList<R3R4Fixture> fixtures = new ArrayList<>();
        fixtures.addAll(groupFixtures("LEGEND", legend.stream().map(
                SeededTeam::teamCode).toList(), careerId, seasonYear, managedTeamCode,
                careerRootSeed));
        fixtures.addAll(groupFixtures("RISE", rise.stream().map(
                SeededTeam::teamCode).toList(), careerId, seasonYear, managedTeamCode,
                careerRootSeed));
        fixtures.sort(java.util.Comparator.comparing(R3R4Fixture::date)
                .thenComparing(R3R4Fixture::matchId));
        if (fixtures.size() != 40 || new HashSet<>(fixtures.stream().map(
                R3R4Fixture::fixtureId).toList()).size() != 40) {
            throw new IllegalStateException("R3_R4_FIXTURE_STRUCTURE_MISMATCH");
        }
        String stateHash = CareerCompetitionRules.sha256(canonicalR3R4(
                careerId, seasonYear, sourceInputHash, ranking, fixtures).getBytes(
                StandardCharsets.UTF_8));
        return new R3R4Stage("LCK_R3_R4_TEN_MATCHDAYS_LINEAR_INCLUSIVE_WINDOW_V1",
                List.copyOf(legend), List.copyOf(rise), List.copyOf(fixtures), stateHash);
    }

    private static List<R3R4Fixture> groupFixtures(
            String group,
            List<String> teams,
            String careerId,
            int seasonYear,
            String managedTeamCode,
            long careerRootSeed
    ) {
        ArrayList<String> rotation = new ArrayList<>(teams);
        rotation.add("BYE");
        ArrayList<R3R4Fixture> result = new ArrayList<>();
        for (int round = 0; round < 5; round++) {
            for (int pair = 0; pair < 3; pair++) {
                String first = rotation.get(pair);
                String second = rotation.get(5 - pair);
                if (!"BYE".equals(first) && !"BYE".equals(second)) {
                    addR3R4Fixture(result, group, round + 1, pair + 1, first, second,
                            careerId, seasonYear, managedTeamCode, careerRootSeed);
                    addR3R4Fixture(result, group, round + 6, pair + 1, second, first,
                            careerId, seasonYear, managedTeamCode, careerRootSeed);
                }
            }
            String last = rotation.removeLast();
            rotation.add(1, last);
        }
        return result;
    }

    private static void addR3R4Fixture(
            List<R3R4Fixture> fixtures,
            String group,
            int round,
            int pair,
            String first,
            String second,
            String careerId,
            int year,
            String managedTeam,
            long careerRootSeed
    ) {
        String matchId = group + "_R" + round + "_M" + pair;
        LocalDate start = LocalDate.of(year, 7, 29);
        LocalDate date = start.plusDays((long) (round - 1) * 25L / 9L);
        String canonical = canonicalIdentity(careerId, year,
                "LCK_REGULAR_R3_R4", matchId, "fixture");
        fixtures.add(new R3R4Fixture(matchId, identity("competition_fixture_", canonical),
                identity("series_", canonicalIdentity(careerId, year,
                        "LCK_REGULAR_R3_R4", matchId, "series")), group, round, date,
                first, second, executionMode(managedTeam, first, second),
                deriveSeed(careerRootSeed, year, "LCK_REGULAR_R3_R4", matchId),
                "BO3", true));
    }

    private static CareerCompetitionAggregate create(
            String careerId, int year, String competitionId, String managedTeam,
            long rootSeed, String inputHash, long revision, List<Fixture> fixtures,
            Map<String, String> outputs
    ) {
        String canonical = canonicalState(careerId, year, competitionId, inputHash,
                revision, fixtures, outputs);
        return new CareerCompetitionAggregate(careerId, year, competitionId,
                managedTeam, rootSeed, inputHash, revision, fixtures, outputs,
                CareerCompetitionRules.sha256(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<Integer, String> indexSeeds(List<SeededTeam> seeds) {
        HashMap<Integer, String> values = new HashMap<>();
        HashSet<String> teams = new HashSet<>();
        for (SeededTeam seed : seeds) {
            if (values.put(seed.seed(), seed.teamCode()) != null
                    || !teams.add(seed.teamCode())) {
                throw new IllegalArgumentException("DUPLICATE_COMPETITION_SEED");
            }
        }
        return Map.copyOf(values);
    }

    private static String resolveInitial(
            CareerCompetitionRules.ParticipantSelector selector,
            Map<Integer, String> seeds
    ) {
        if ("R1_R2_RANK".equals(selector.type())
                || "PLAY_IN_SEED".equals(selector.type())) {
            return seeds.get(Integer.parseInt(selector.value()));
        }
        return null;
    }

    private static String resolve(
            CareerCompetitionRules.ParticipantSelector selector,
            String current,
            Map<String, Outcome> outcomes
    ) {
        if (current != null) return current;
        Outcome outcome = outcomes.get(selector.value());
        if (outcome == null) return null;
        return "MATCH_WINNER".equals(selector.type())
                ? outcome.winner() : "MATCH_LOSER".equals(selector.type())
                ? outcome.loser() : null;
    }

    private static Map<String, Outcome> outcomes(List<Fixture> fixtures) {
        LinkedHashMap<String, Outcome> values = new LinkedHashMap<>();
        fixtures.stream().filter(value -> value.winnerTeamCode() != null).forEach(value ->
                values.put(value.matchId(), new Outcome(value.winnerTeamCode(),
                        value.loserTeamCode())));
        return values;
    }

    private int fixtureIndex(String matchId) {
        for (int index = 0; index < fixtures.size(); index++) {
            if (fixtures.get(index).matchId().equals(matchId)) return index;
        }
        throw new IllegalArgumentException("COMPETITION_MATCH_NOT_FOUND");
    }

    private static void putOutput(Map<String, String> outputs, String name, String team) {
        String prior = outputs.putIfAbsent(name, team);
        if (prior != null && !prior.equals(team)) throw new IllegalStateException(
                "COMPETITION_OUTPUT_CONFLICT");
    }

    private static String executionMode(String managed, String first, String second) {
        return managed != null && (managed.equals(first) || managed.equals(second))
                ? "PLAYER_CONTROLLED" : "FULL_AUTO";
    }

    private static long deriveSeed(long rootSeed, int year, String competition, String match) {
        byte[] digest;
        try {
            digest = java.security.MessageDigest.getInstance("SHA-256").digest((
                    "seedAlgorithm=" + SEED_ALGORITHM + '\n'
                            + "careerRootSeed=" + rootSeed + '\n'
                            + "seasonYear=" + year + '\n'
                            + "competitionId=" + competition + '\n'
                            + "matchId=" + match + '\n').getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        return ByteBuffer.wrap(digest).getLong();
    }

    private static String identity(String prefix, String canonical) {
        return prefix + CareerCompetitionRules.sha256(canonical.getBytes(
                StandardCharsets.UTF_8));
    }

    private static String canonicalIdentity(
            String careerId, int year, String competition, String match, String kind
    ) {
        return "schema=CAREER_COMPETITION_IDENTITY_V1\ncareerId=" + careerId
                + "\nseasonYear=" + year + "\ncompetitionId=" + competition
                + "\nmatchId=" + match + "\nkind=" + kind + '\n';
    }

    private static String canonicalState(
            String careerId, int year, String competition, String inputHash,
            long revision, List<Fixture> fixtures, Map<String, String> outputs
    ) {
        StringBuilder value = new StringBuilder("schema=").append(STATE_SCHEMA)
                .append("\ncareerId=").append(careerId).append("\nseasonYear=")
                .append(year).append("\ncompetitionId=").append(competition)
                .append("\nsourceInputHash=").append(inputHash).append("\nrevision=")
                .append(revision).append('\n');
        fixtures.forEach(fixture -> value.append("fixture=").append(fixture.canonical())
                .append('\n'));
        outputs.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                value.append("output=").append(entry.getKey()).append('|')
                        .append(entry.getValue()).append('\n'));
        return value.toString();
    }

    private static String canonicalR3R4(
            String careerId, int year, String inputHash, List<SeededTeam> ranking,
            List<R3R4Fixture> fixtures
    ) {
        StringBuilder value = new StringBuilder("schema=CAREER_R3_R4_STAGE_V1\ncareerId=")
                .append(careerId).append("\nseasonYear=").append(year)
                .append("\nsourceInputHash=").append(inputHash).append('\n');
        ranking.stream().sorted(java.util.Comparator.comparingInt(SeededTeam::seed))
                .forEach(team -> value.append("carry=").append(team.canonical()).append('\n'));
        fixtures.forEach(fixture -> value.append("fixture=").append(fixture.canonical())
                .append('\n'));
        return value.toString();
    }

    public record SeededTeam(
            int seed, String teamCode, int seriesWins, int seriesLosses,
            int gameWins, int gameLosses
    ) {
        public SeededTeam {
            if (seed < 1 || seed > 10 || teamCode == null
                    || !teamCode.matches("[A-Z0-9]{2,16}")
                    || seriesWins < 0 || seriesLosses < 0
                    || gameWins < 0 || gameLosses < 0) {
                throw new IllegalArgumentException("Invalid seeded team");
            }
        }
        String canonical() {
            return seed + "|" + teamCode + "|" + seriesWins + "|" + seriesLosses
                    + "|" + gameWins + "|" + gameLosses;
        }
    }

    public record Fixture(
            String matchId, String fixtureId, LocalDate date, String seriesFormat,
            boolean hardFearless,
            CareerCompetitionRules.ParticipantSelector firstSelector,
            CareerCompetitionRules.ParticipantSelector secondSelector,
            String firstTeamCode, String secondTeamCode, String lifecycleStatus,
            String executionMode, long rootSeed, String seriesId,
            List<String> winnerOutputs, List<String> loserOutputs,
            String winnerTeamCode, String loserTeamCode, String receiptHash
    ) {
        public Fixture {
            winnerOutputs = List.copyOf(winnerOutputs);
            loserOutputs = List.copyOf(loserOutputs);
        }
        Fixture completed(String winner, String loser, String receipt) {
            return new Fixture(matchId, fixtureId, date, seriesFormat, hardFearless, firstSelector,
                    secondSelector, firstTeamCode, secondTeamCode, "COMPLETED",
                    executionMode, rootSeed, seriesId, winnerOutputs, loserOutputs,
                    winner, loser, receipt);
        }
        Fixture withParticipants(String first, String second, String status, String mode) {
            return new Fixture(matchId, fixtureId, date, seriesFormat, hardFearless, firstSelector,
                    secondSelector, first, second, status, mode, rootSeed, seriesId,
                    winnerOutputs, loserOutputs, winnerTeamCode, loserTeamCode, receiptHash);
        }
        String canonical() {
            return matchId + '|' + fixtureId + '|' + date + '|' + seriesFormat + '|' + hardFearless + '|'
                    + firstSelector.type() + ':' + firstSelector.value() + '|'
                    + secondSelector.type() + ':' + secondSelector.value() + '|'
                    + Objects.toString(firstTeamCode, "") + '|'
                    + Objects.toString(secondTeamCode, "") + '|' + lifecycleStatus + '|'
                    + executionMode + '|' + rootSeed + '|' + seriesId + '|'
                    + String.join(",", winnerOutputs) + '|'
                    + String.join(",", loserOutputs) + '|'
                    + Objects.toString(winnerTeamCode, "") + '|'
                    + Objects.toString(loserTeamCode, "") + '|'
                    + Objects.toString(receiptHash, "");
        }
    }

    public record R3R4Fixture(
            String matchId, String fixtureId, String seriesId, String groupId,
            int groupRound, LocalDate date, String firstTeamCode, String secondTeamCode,
            String executionMode, long rootSeed, String seriesFormat, boolean hardFearless
    ) {
        String canonical() {
            return matchId + '|' + fixtureId + '|' + seriesId + '|' + groupId + '|'
                    + groupRound + '|' + date + '|' + firstTeamCode + '|'
                    + secondTeamCode + '|' + executionMode + '|' + rootSeed + '|'
                    + seriesFormat + '|' + hardFearless;
        }
    }

    public record R3R4Stage(
            String allocationPolicy, List<SeededTeam> legend, List<SeededTeam> rise,
            List<R3R4Fixture> fixtures, String stateHash
    ) {}

    public record CompletionResult(CareerCompetitionAggregate aggregate, boolean replayed) {}
    private record Outcome(String winner, String loser) {}
}
