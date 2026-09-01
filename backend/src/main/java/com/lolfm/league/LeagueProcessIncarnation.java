package com.lolfm.league;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Non-gameplay process identity used only to reconcile local single-node worker leases. */
@Component
final class LeagueProcessIncarnation {
    static final String SCHEMA = "AI_LEAGUE_PROCESS_INCARNATION_V1";

    private final String value;

    LeagueProcessIncarnation() {
        this("process_" + UUID.randomUUID());
    }

    LeagueProcessIncarnation(String value) {
        if (value == null || !value.matches("process_[0-9A-Za-z_-]{8,80}")) {
            throw new IllegalArgumentException("processIncarnationId");
        }
        this.value = value;
    }

    String value() {
        return value;
    }
}
