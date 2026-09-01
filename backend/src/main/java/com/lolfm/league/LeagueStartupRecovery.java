package com.lolfm.league;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Startup performs lifecycle reconciliation only; it never starts gameplay implicitly. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
final class LeagueStartupRecovery implements ApplicationRunner {
    private final LeagueSimulationApplicationPort league;

    LeagueStartupRecovery(LeagueSimulationApplicationPort league) {
        this.league = league;
    }

    @Override
    public void run(ApplicationArguments args) {
        league.recoverStartup();
        league.purgeExpiredAttemptLogs();
    }
}
