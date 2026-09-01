package com.lolfm.league;

import com.lolfm.application.SeriesFormat;
import java.util.Objects;

/** Versioned schedule policy; CUSTOM and side-imbalance waivers are intentionally absent. */
public record LeagueSchedulePolicy(
        String policyId,
        LeagueScheduleFormat scheduleFormat,
        SeriesFormat seriesFormat
) {
    public static final String DOUBLE_ROUND_ROBIN_BO3_V1 =
            "AI_LEAGUE_DOUBLE_ROUND_ROBIN_BO3_SCHEDULE_V1";
    public static final String SINGLE_ROUND_ROBIN_BO3_V1 =
            "AI_LEAGUE_SINGLE_ROUND_ROBIN_BO3_SCHEDULE_V1";

    public LeagueSchedulePolicy {
        Objects.requireNonNull(scheduleFormat, "scheduleFormat");
        Objects.requireNonNull(seriesFormat, "seriesFormat");
        String expected = switch (scheduleFormat) {
            case DOUBLE_ROUND_ROBIN -> DOUBLE_ROUND_ROBIN_BO3_V1;
            case SINGLE_ROUND_ROBIN -> SINGLE_ROUND_ROBIN_BO3_V1;
        };
        if (!expected.equals(policyId) || seriesFormat != SeriesFormat.BO3) {
            throw new IllegalArgumentException("Unsupported AI League V1 schedule policy");
        }
    }

    public static LeagueSchedulePolicy productionDefault() {
        return new LeagueSchedulePolicy(DOUBLE_ROUND_ROBIN_BO3_V1,
                LeagueScheduleFormat.DOUBLE_ROUND_ROBIN, SeriesFormat.BO3);
    }

    public static LeagueSchedulePolicy singleRoundRobinDesign() {
        return new LeagueSchedulePolicy(SINGLE_ROUND_ROBIN_BO3_V1,
                LeagueScheduleFormat.SINGLE_ROUND_ROBIN, SeriesFormat.BO3);
    }

    int expectedRoundCount(int teamCount) {
        return (teamCount - 1) * scheduleFormat.legs();
    }

    int expectedFixtureCount(int teamCount) {
        return teamCount * (teamCount - 1) / 2 * scheduleFormat.legs();
    }
}
