package com.lolfm.league;

/** Bounded post-command execution trigger; startup never invokes this boundary. */
public interface LeagueBackgroundExecutionPort {
    boolean submit(String ownerId);
}
