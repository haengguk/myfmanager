package com.lolfm.league;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Code-owned canonical representation of the frozen V1 product decision table. */
public final class LeagueV1ProductDecisions {
    public static final String SCHEMA = "AI_LEAGUE_V1_PRODUCT_DECISIONS_CANONICAL_SHA256_V1";

    public static final String SEASON_MODE = "AI_LEAGUE_V1_SEASON_MODE";
    public static final String MANAGED_FIXTURE_POLICY =
            "AI_LEAGUE_V1_MANAGED_FIXTURE_POLICY";
    public static final String ROSTER_SNAPSHOT = "AI_LEAGUE_V1_ROSTER_SNAPSHOT";
    public static final String SCHEDULE_FORMAT = "AI_LEAGUE_V1_SCHEDULE_FORMAT";
    public static final String STANDINGS_POLICY = "AI_LEAGUE_V1_STANDINGS_POLICY";
    public static final String BLOCKED_FIXTURE_POLICY =
            "AI_LEAGUE_V1_BLOCKED_FIXTURE_POLICY";
    public static final String EXECUTION_LIMITS = "AI_LEAGUE_V1_EXECUTION_LIMITS";
    public static final String PERSISTENCE_POLICY = "AI_LEAGUE_V1_PERSISTENCE_POLICY";
    public static final String PLAYER_SERIES_HANDOFF =
            "AI_LEAGUE_V1_PLAYER_SERIES_HANDOFF";

    private static final List<Decision> DECISIONS = List.of(
            new Decision(SEASON_MODE,
                    "modes=HYBRID_MANAGER,SPECTATOR_FULL_AUTO;hybridManagedTeams=1;"
                            + "spectatorManagedTeams=0;managedTeamImmutable=true"),
            new Decision(MANAGED_FIXTURE_POLICY,
                    "managedFixture=PLAYER_CONTROLLED;otherFixture=FULL_AUTO;"
                            + "managedFixtureAiDelegation=false"),
            new Decision(ROSTER_SNAPSHOT, rosterSnapshotCanonical()),
            new Decision(SCHEDULE_FORMAT, scheduleFormatCanonical()),
            new Decision(STANDINGS_POLICY,
                    "seriesWinPoints=1;seriesLossPoints=0;draw=false;order=SERIES_WINS,"
                            + "GAME_DIFFERENTIAL,GAME_WINS,MINI_SERIES_WINS,"
                            + "MINI_GAME_DIFFERENTIAL,SEASON_SEED_DRAW"),
            new Decision(BLOCKED_FIXTURE_POLICY,
                    "blockedSeason=true;preserveCompleted=true;forfeit=false;draw=false;"
                            + "ruleRelaxation=false;tuning=false;reseed=false"),
            new Decision(EXECUTION_LIMITS, executionLimitsCanonical()),
            new Decision(PERSISTENCE_POLICY, persistenceCanonical()),
            new Decision(PLAYER_SERIES_HANDOFF,
                    "binding=SERVER_OWNED_DURABLE;receipt=SERVER_CREATED;"
                            + "commit=TRANSACTIONAL_OUTBOX_IDEMPOTENT_CONSUMER;"
                            + "frontendResultAuthority=false"));

    private static final String CANONICAL_TEXT = canonicalText(DECISIONS);
    private static final String PRODUCT_DECISION_HASH = sha256(CANONICAL_TEXT);

    private LeagueV1ProductDecisions() {
    }

    public static List<String> orderedDecisionIds() {
        return DECISIONS.stream().map(Decision::id).toList();
    }

    public static Map<String, String> canonicalValues() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        DECISIONS.forEach(decision -> result.put(decision.id(), decision.value()));
        return java.util.Collections.unmodifiableMap(result);
    }

    public static String canonicalText() {
        return CANONICAL_TEXT;
    }

    public static String productDecisionHash() {
        return PRODUCT_DECISION_HASH;
    }

    private static String executionLimitsCanonical() {
        LeagueV1OperationalConfiguration value =
                LeagueV1OperationalConfiguration.defaults();
        return "teamCount=" + value.activeSeasonTeamCount()
                + ";defaultParallel=" + value.defaultMaxParallelFixtures()
                + ";hardMaxParallel=" + value.hardMaxParallelFixtures()
                + ";leaseSeconds=" + value.fixtureLease().toSeconds()
                + ";heartbeatSeconds=" + value.heartbeatInterval().toSeconds()
                + ";totalAttempts=" + value.transientTotalAttempts()
                + ";cancelStopsNewDispatch=true;commitMayFinish=true";
    }

    private static String rosterSnapshotCanonical() {
        int teamCount = LeagueV1OperationalConfiguration.defaults()
                .activeSeasonTeamCount();
        return "teamCount=" + teamCount + ";playerCount=" + (teamCount * 5)
                + ";membershipRosterPlayerRatingProficiencyFrozen=true;"
                + "championDraftMatchupCompositionRuntimeFrozen=true";
    }

    private static String scheduleFormatCanonical() {
        int fixtureCount = LeagueV1OperationalConfiguration.defaults()
                .doubleRoundRobinFixtureCount();
        return "default=DOUBLE_ROUND_ROBIN;fixtures=" + fixtureCount
                + ";seriesFormat=BO3;pairedLegGame1Side=MIRRORED;"
                + "seriesGameSide=ALTERNATING;custom=false;sideImbalance=false";
    }

    private static String persistenceCanonical() {
        LeagueV1OperationalConfiguration value =
                LeagueV1OperationalConfiguration.defaults();
        return "store=RELATIONAL;seasonReceiptRetention=UNTIL_SEASON_DELETE;"
                + "attemptLogRetentionSeconds="
                + value.jobAttemptLogRetention().toSeconds()
                + ";replayCacheSeconds="
                + value.optionalFullReplayCacheRetention().toSeconds()
                + ";replayCacheAuthority=false";
    }

    private static String canonicalText(List<Decision> decisions) {
        return decisions.stream().map(value -> value.id() + "=" + value.value())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String sha256(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private record Decision(String id, String value) {
        private Decision {
            if (id == null || id.isBlank() || value == null || value.isBlank()
                    || id.indexOf('\n') >= 0 || value.indexOf('\n') >= 0) {
                throw new IllegalArgumentException("Invalid canonical product decision");
            }
        }
    }
}
