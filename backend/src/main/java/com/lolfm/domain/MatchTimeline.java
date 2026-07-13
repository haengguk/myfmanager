package com.lolfm.domain;

import java.util.List;

public class MatchTimeline {

    private final int durationSeconds;
    private final String winner;
    private final List<MatchEvent> events;
    private final List<MatchSnapshot> snapshots;

    public MatchTimeline(
            int durationSeconds,
            String winner,
            List<MatchEvent> events,
            List<MatchSnapshot> snapshots
    ) {
        this.durationSeconds = durationSeconds;
        this.winner = winner;
        this.events = List.copyOf(events);
        this.snapshots = List.copyOf(snapshots);
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public String getWinner() {
        return winner;
    }

    public List<MatchEvent> getEvents() {
        return events;
    }

    public List<MatchSnapshot> getSnapshots() {
        return snapshots;
    }
}
