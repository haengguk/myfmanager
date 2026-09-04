package com.lolfm.career;

import java.util.Objects;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Explicit persisted-state recovery boundary; ordinary Career GETs stay read-only. */
@Component
public final class CareerPersistenceStartupRecovery {
    private final CareerCalendarRelationalStore calendars;
    private final CareerCompetitionRelationalStore competitions;

    public CareerPersistenceStartupRecovery(
            CareerCalendarRelationalStore calendars,
            CareerCompetitionRelationalStore competitions
    ) {
        this.calendars = Objects.requireNonNull(calendars, "calendars");
        this.competitions = Objects.requireNonNull(competitions, "competitions");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        calendars.recoverLegacyStates();
        competitions.recoverLegacyCompetitions();
    }
}
