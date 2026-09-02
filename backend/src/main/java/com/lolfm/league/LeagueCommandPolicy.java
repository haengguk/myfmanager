package com.lolfm.league;

import com.lolfm.application.SeriesStatus;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure command eligibility shared by public League and Career navigation views. */
final class LeagueCommandPolicy {
    private static final Set<LeaguePersistenceState.SeasonStatus> RUNNABLE_SEASONS =
            EnumSet.of(LeaguePersistenceState.SeasonStatus.READY,
                    LeaguePersistenceState.SeasonStatus.RUNNING,
                    LeaguePersistenceState.SeasonStatus.WAITING_FOR_PLAYER);
    private static final Set<LeaguePersistenceState.FixtureStatus> TERMINAL_FIXTURES =
            EnumSet.of(LeaguePersistenceState.FixtureStatus.COMPLETED,
                    LeaguePersistenceState.FixtureStatus.CANCELLED);
    private static final Set<LeaguePersistenceState.FixtureStatus> ATTENTION_FIXTURES =
            EnumSet.of(LeaguePersistenceState.FixtureStatus.BLOCKED,
                    LeaguePersistenceState.FixtureStatus.PLAYER_SERIES_RESTART_REQUIRED);
    private static final Set<LeaguePlayerSeriesBindingPort.Status> ATTENTION_BINDINGS =
            EnumSet.of(LeaguePlayerSeriesBindingPort.Status.BLOCKED,
                    LeaguePlayerSeriesBindingPort.Status.PLAYER_SERIES_RESTART_REQUIRED);

    private LeagueCommandPolicy() {}

    static List<String> seasonCommands(
            LeaguePersistenceState.SeasonStatus status,
            boolean currentRoundAutoAvailable,
            List<String> playableFixtureCommands
    ) {
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        allowed.add("VIEW_STANDINGS");
        if (RUNNABLE_SEASONS.contains(status)) {
            if (currentRoundAutoAvailable) {
                allowed.add("RUN_CURRENT_ROUND_AUTO_FIXTURES");
            }
            if (status != LeaguePersistenceState.SeasonStatus.READY) {
                allowed.add("PAUSE_SEASON");
            }
            allowed.add("CANCEL_SEASON");
        } else if (status == LeaguePersistenceState.SeasonStatus.PAUSED) {
            allowed.add("RESUME_SEASON");
            allowed.add("CANCEL_SEASON");
        }
        if (status != LeaguePersistenceState.SeasonStatus.PAUSED) {
            allowed.addAll(playableFixtureCommands);
        }
        return List.copyOf(allowed);
    }

    static List<String> fixtureCommands(
            LeagueFixtureExecutionMode executionMode,
            LeaguePersistenceState.FixtureStatus fixtureStatus,
            LeaguePlayerSeriesBindingPort.Status bindingStatus,
            SeriesStatus childStatus
    ) {
        ArrayList<String> allowed = new ArrayList<>();
        allowed.add("VIEW_FIXTURE");
        if (executionMode != LeagueFixtureExecutionMode.PLAYER_CONTROLLED
                || terminal(fixtureStatus) || fixtureStatus ==
                LeaguePersistenceState.FixtureStatus.BLOCKED) {
            return List.copyOf(allowed);
        }
        if (bindingStatus == null) {
            allowed.add("START_PLAYER_SERIES");
        } else {
            allowed.addAll(playerSeriesCommands(bindingStatus, childStatus));
        }
        return List.copyOf(allowed);
    }

    static List<String> playerSeriesCommands(
            LeaguePlayerSeriesBindingPort.Status bindingStatus,
            SeriesStatus childStatus
    ) {
        if (bindingStatus == LeaguePlayerSeriesBindingPort.Status.CREATED) {
            return List.of("RESUME_PLAYER_SERIES");
        }
        if (bindingStatus == LeaguePlayerSeriesBindingPort.Status.ACTIVE) {
            return childStatus == SeriesStatus.COMPLETED
                    ? List.of("RECONCILE_PLAYER_SERIES_COMPLETION")
                    : List.of("RESUME_PLAYER_SERIES");
        }
        if (bindingStatus == LeaguePlayerSeriesBindingPort.Status
                .COMPLETION_PENDING_VERIFICATION) {
            return List.of("RECONCILE_PLAYER_SERIES_COMPLETION");
        }
        return List.of();
    }

    static boolean terminal(LeaguePersistenceState.FixtureStatus status) {
        return TERMINAL_FIXTURES.contains(status);
    }

    static boolean needsAttention(LeaguePersistenceState.FixtureStatus status) {
        return ATTENTION_FIXTURES.contains(status);
    }

    static boolean needsAttention(LeaguePlayerSeriesBindingPort.Status status) {
        return status != null && ATTENTION_BINDINGS.contains(status);
    }

    static List<LeaguePersistenceState.FixtureStatus> attentionFixtureStatuses() {
        return List.copyOf(ATTENTION_FIXTURES);
    }
}
