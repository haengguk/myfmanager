package com.lolfm.league;

import java.time.Duration;

/** Versioned V1 limits. These values are operational and never enter gameplay Random. */
public record LeagueV1OperationalConfiguration(
        String configurationId,
        int activeSeasonTeamCount,
        int doubleRoundRobinFixtureCount,
        int defaultMaxParallelFixtures,
        int hardMaxParallelFixtures,
        Duration fixtureLease,
        Duration heartbeatInterval,
        int transientTotalAttempts,
        Duration jobAttemptLogRetention,
        Duration optionalFullReplayCacheRetention
) {
    public static final String CONFIGURATION_ID = "AI_LEAGUE_V1_OPERATIONAL_LIMITS_V1";
    private static final LeagueV1OperationalConfiguration DEFAULTS =
            new LeagueV1OperationalConfiguration(
                    CONFIGURATION_ID,
                    10,
                    90,
                    2,
                    4,
                    Duration.ofMinutes(15),
                    Duration.ofSeconds(15),
                    2,
                    Duration.ofDays(30),
                    Duration.ofHours(24));

    public LeagueV1OperationalConfiguration {
        if (!CONFIGURATION_ID.equals(configurationId)
                || activeSeasonTeamCount != 10
                || doubleRoundRobinFixtureCount != 90
                || defaultMaxParallelFixtures != 2
                || hardMaxParallelFixtures != 4
                || !Duration.ofMinutes(15).equals(fixtureLease)
                || !Duration.ofSeconds(15).equals(heartbeatInterval)
                || transientTotalAttempts != 2
                || !Duration.ofDays(30).equals(jobAttemptLogRetention)
                || !Duration.ofHours(24).equals(optionalFullReplayCacheRetention)) {
            throw new IllegalArgumentException("Invalid AI League V1 operational configuration");
        }
    }

    public static LeagueV1OperationalConfiguration defaults() {
        return DEFAULTS;
    }
}
