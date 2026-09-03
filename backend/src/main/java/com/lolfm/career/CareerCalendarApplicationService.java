package com.lolfm.career;

import com.lolfm.dto.CareerApiV1Dtos;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Career-owned calendar projection and authoritative date-advance coordinator. */
@Service
public final class CareerCalendarApplicationService {
    public static final String ADVANCE_ONE_DAY = "ADVANCE_ONE_DAY";
    public static final String ADVANCE_TO_NEXT_EVENT = "ADVANCE_TO_NEXT_EVENT";
    private static final List<String> ADVANCE_MODES = List.of(
            ADVANCE_ONE_DAY, ADVANCE_TO_NEXT_EVENT);

    private final CareerCalendarRelationalStore calendars;
    private final CareerCalendarTemplate template;
    private final CareerCalendarLeaguePort leagues;

    public CareerCalendarApplicationService(
            CareerCalendarRelationalStore calendars,
            CareerCalendarTemplate template,
            CareerCalendarLeaguePort leagues
    ) {
        this.calendars = Objects.requireNonNull(calendars, "calendars");
        this.template = Objects.requireNonNull(template, "template");
        this.leagues = Objects.requireNonNull(leagues, "leagues");
    }

    public void initializeNew(CareerRelationalStore.NewCareer career) {
        calendars.initializeNew(career);
    }

    public LocalDate currentDate(CareerRelationalStore.CareerRow career) {
        return ready(career).currentDate();
    }

    public CalendarView view(CareerRelationalStore.CareerRow career) {
        return buildView(career, ready(career));
    }

    public AdvanceResult advance(
            CareerRelationalStore.CareerRow career,
            String schemaVersion,
            long expectedCalendarRevision,
            String mode,
            String clientCommandId
    ) {
        if (!CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA.equals(schemaVersion)) {
            throw CareerException.invalid("schemaVersion",
                    "지원하지 않는 Career 캘린더 진행 schema입니다.");
        }
        if (expectedCalendarRevision < 0) {
            throw CareerException.invalid("expectedCalendarRevision",
                    "expectedCalendarRevision은 0 이상이어야 합니다.");
        }
        if (!ADVANCE_MODES.contains(mode)) {
            throw CareerException.invalid("mode",
                    "지원하는 진행 모드는 ADVANCE_ONE_DAY와 ADVANCE_TO_NEXT_EVENT입니다.");
        }
        String commandId;
        try {
            commandId = CareerIdentity.canonicalCommandId(clientCommandId);
        } catch (IllegalArgumentException invalid) {
            throw CareerException.invalid("clientCommandId",
                    "clientCommandId는 UUID 형식이어야 합니다.");
        }
        ready(career);
        String payloadHash = template.advancePayloadHash(career.careerId(),
                expectedCalendarRevision, mode);
        try {
            CareerCalendarRelationalStore.AdvanceStoreResult stored = calendars.execute(
                    commandId, career.careerId(), expectedCalendarRevision, payloadHash,
                    state -> transition(career, state, mode));
            boolean backgroundAccepted = true;
            if (stored.backgroundRequired()) {
                backgroundAccepted = leagues.wakeBackground(commandId);
                if (!backgroundAccepted) {
                    throw CareerException.calendarBackgroundUnavailable();
                }
            }
            return new AdvanceResult(stored.replayed(), stored.pending(),
                    stored.httpStatus(), stored.stopReason(), backgroundAccepted,
                    buildView(career, stored.state()));
        } catch (CareerCalendarRelationalStore.StaleRevision stale) {
            throw CareerException.calendarStaleRevision();
        } catch (CareerCalendarRelationalStore.CommandConflict conflict) {
            throw CareerException.calendarCommandConflict();
        } catch (CareerCalendarRelationalStore.AdvanceAlreadyPending pending) {
            throw CareerException.calendarAdvanceAlreadyPending();
        } catch (CareerCalendarRelationalStore.CommandReceiptIntegrityFailure corrupt) {
            throw CareerException.calendarCommandIntegrity();
        } catch (CareerCalendarRelationalStore.CalendarMigrationRequired migration) {
            throw CareerException.calendarMigrationRequired();
        } catch (CareerCalendarRelationalStore.CalendarNotFound missing) {
            throw CareerException.calendarNotFound();
        } catch (CareerException known) {
            throw known;
        } catch (DataIntegrityViolationException | IllegalArgumentException
                | IllegalStateException failure) {
            throw CareerException.calendarIntegrity(failure);
        }
    }

    private CareerCalendarRelationalStore.AdvanceMutation transition(
            CareerRelationalStore.CareerRow career,
            CareerCalendarRelationalStore.CalendarRow state,
            String mode
    ) {
        if ("SEASON_ROLLOVER_REQUIRED".equals(state.lifecycleStatus())) {
            return mutation(state, state.currentDate(), state.eventCursor(),
                    state.lastProcessedEventId(), state.lastProcessedDate(),
                    "SEASON_ROLLOVER_REQUIRED", "SEASON_ROLLOVER_REQUIRED",
                    false, false, 200, "SEASON_ROLLOVER_REQUIRED", false);
        }
        CareerCalendarTemplate.ProjectedCalendar projected = template.project(
                state.seasonYear());
        OverlayProjection overlay = overlay(career, state, projected);

        CareerCalendarLeaguePort.GateResult currentGate = gate(state.currentDate(), overlay);
        if (currentGate.stopReason() != null) {
            return gated(state, projected, state.currentDate(), currentGate, false);
        }
        if ("AUTO_FIXTURES_PENDING".equals(state.blockingReason())) {
            ProcessedEvent processed = lastProcessed(projected, state.currentDate());
            return mutation(state, state.currentDate(), state.eventCursor(),
                    processed.eventId(), processed.date(), "ACTIVE", null,
                    true, false, 200, null, false);
        }

        LocalDate target = targetDate(mode, state.currentDate(), projected, overlay);
        if (target == null || target.isAfter(projected.events().getLast().endDate())) {
            return mutation(state, state.currentDate(), state.eventCursor(),
                    state.lastProcessedEventId(), state.lastProcessedDate(),
                    "SEASON_ROLLOVER_REQUIRED", "SEASON_ROLLOVER_REQUIRED",
                    true, false, 200, "SEASON_ROLLOVER_REQUIRED", false);
        }
        CareerCalendarLeaguePort.GateResult targetGate = gate(target, overlay);
        if (targetGate.stopReason() != null) {
            return gated(state, projected, target, targetGate,
                    !target.equals(state.currentDate()));
        }
        ProcessedEvent processed = lastProcessed(projected, target);
        return mutation(state, target, template.eventCursor(projected, target),
                processed.eventId(), processed.date(), "ACTIVE", null,
                !target.equals(state.currentDate()), false, 200, null, false);
    }

    private CareerCalendarRelationalStore.AdvanceMutation gated(
            CareerCalendarRelationalStore.CalendarRow state,
            CareerCalendarTemplate.ProjectedCalendar projected,
            LocalDate date,
            CareerCalendarLeaguePort.GateResult gate,
            boolean dateChanged
    ) {
        ProcessedEvent processed = lastProcessed(projected, date);
        return mutation(state, date, template.eventCursor(projected, date),
                processed.eventId(), processed.date(), "ACTIVE", gate.stopReason(),
                dateChanged || !Objects.equals(state.blockingReason(), gate.stopReason()),
                gate.pending(), gate.pending() ? 202 : 200, gate.stopReason(),
                gate.backgroundRequired());
    }

    private static CareerCalendarRelationalStore.AdvanceMutation mutation(
            CareerCalendarRelationalStore.CalendarRow state,
            LocalDate date,
            int cursor,
            String lastEvent,
            LocalDate lastDate,
            String lifecycle,
            String blocking,
            boolean changed,
            boolean pending,
            int http,
            String stop,
            boolean background
    ) {
        return new CareerCalendarRelationalStore.AdvanceMutation(date, cursor, lastEvent,
                lastDate, lifecycle, blocking, changed, pending, http, stop, background);
    }

    private CareerCalendarLeaguePort.GateResult gate(
            LocalDate date,
            OverlayProjection overlay
    ) {
        List<String> ids = overlay.fixtures().stream()
                .filter(value -> value.date().equals(date))
                .map(FixtureView::fixtureId).toList();
        return leagues.gateAndDispatch(overlay.seasonId(), ids);
    }

    private static LocalDate targetDate(
            String mode,
            LocalDate current,
            CareerCalendarTemplate.ProjectedCalendar projected,
            OverlayProjection overlay
    ) {
        if (ADVANCE_ONE_DAY.equals(mode)) return current.plusDays(1);
        ArrayList<LocalDate> candidates = new ArrayList<>();
        projected.events().stream().map(CareerCalendarTemplate.ProjectedEvent::startDate)
                .filter(value -> value.isAfter(current)).forEach(candidates::add);
        overlay.fixtures().stream().map(FixtureView::date)
                .filter(value -> value.isAfter(current)).forEach(candidates::add);
        return candidates.stream().min(LocalDate::compareTo).orElse(null);
    }

    private CalendarView buildView(
            CareerRelationalStore.CareerRow career,
            CareerCalendarRelationalStore.CalendarRow state
    ) {
        requireStateCareer(career, state);
        CareerCalendarTemplate.ProjectedCalendar projected = template.project(
                state.seasonYear());
        OverlayProjection overlay = overlay(career, state, projected);
        CareerCalendarTemplate.ProjectedEvent current = template.currentEvent(
                projected, state.currentDate());
        CareerCalendarTemplate.ProjectedEvent next = template.nextEvent(
                projected, state.currentDate());
        CareerCalendarTemplate.ProjectedStage currentStage = current == null ? null
                : current.stages().stream().filter(value -> value.startDate() != null
                && !state.currentDate().isBefore(value.startDate())
                && !state.currentDate().isAfter(value.endDate())).findFirst().orElse(null);
        CareerCalendarTemplate.ProjectedStage nextStage = projected.events().stream()
                .flatMap(value -> value.stages().stream())
                .filter(value -> value.startDate() != null
                        && value.startDate().isAfter(state.currentDate()))
                .min(Comparator.comparing(CareerCalendarTemplate.ProjectedStage::startDate))
                .orElse(null);
        List<CareerCalendarTemplate.ProjectedEvent> upcoming = projected.events().stream()
                .filter(value -> !value.endDate().isBefore(state.currentDate()))
                .limit(CareerCalendarTemplate.UPCOMING_LIMIT).toList();
        List<FixtureView> upcomingFixtures = overlay.fixtures().stream()
                .filter(value -> !value.date().isBefore(state.currentDate()))
                .limit(CareerCalendarTemplate.UPCOMING_LIMIT).toList();
        FixtureView nextManaged = overlay.fixtures().stream()
                .filter(value -> "PLAYER_CONTROLLED".equals(value.executionMode()))
                .filter(value -> !"COMPLETED".equals(value.lifecycleStatus()))
                .filter(value -> !value.date().isBefore(state.currentDate()))
                .findFirst().orElse(null);
        List<String> commands = "ACTIVE".equals(state.lifecycleStatus())
                ? ADVANCE_MODES : List.of();
        return new CalendarView(state, template.body().referenceYear(),
                template.body().sourceAsOf(),
                template.body().referenceCatalogSnapshotAt(),
                CareerCalendarTemplate.STATE_HASH_ALGORITHM,
                projected.projectionStatus(), current, next, currentStage, nextStage,
                upcoming,
                overlay.overlay(), upcomingFixtures, nextManaged, commands,
                template.body().counts(), template.body().qualificationEdges(),
                template.body().pendingOfficialFields(),
                List.of(new SourceDataNote("KESPA_CUP", "SOURCE_DATA_NOT_PRESENT")));
    }

    private OverlayProjection overlay(
            CareerRelationalStore.CareerRow career,
            CareerCalendarRelationalStore.CalendarRow state,
            CareerCalendarTemplate.ProjectedCalendar projected
    ) {
        CareerCalendarLeaguePort.SeasonProjection season = leagues.load(
                career.leagueId(), career.seasonId());
        List<CareerCalendarTemplate.FixtureInput> inputs = season.fixtures().stream()
                .map(value -> new CareerCalendarTemplate.FixtureInput(value.fixtureId(),
                        value.roundNumber(), value.executionMode(), value.firstTeamCode(),
                        value.secondTeamCode(), value.fixtureRootSeed())).toList();
        CareerCalendarTemplate.FixtureOverlay overlay = template.overlay(
                state.seasonYear(), inputs);
        java.util.Map<String, CareerCalendarLeaguePort.FixtureProjection> status =
                season.fixtures().stream().collect(java.util.stream.Collectors.toMap(
                        CareerCalendarLeaguePort.FixtureProjection::fixtureId, value -> value));
        List<FixtureView> fixtures = overlay.fixtures().stream().map(value -> {
            CareerCalendarLeaguePort.FixtureProjection fixture = status.get(value.fixtureId());
            if (fixture == null) throw new IllegalStateException(
                    "CAREER_CALENDAR_FIXTURE_STATUS_MISSING");
            return new FixtureView(value.fixtureId(), value.roundNumber(), value.date(),
                    "GAME_DERIVED_SCHEDULE_POLICY", value.executionMode(),
                    value.firstTeamCode(), value.secondTeamCode(), fixture.fixtureStatus(),
                    fixture.boundSeriesId(), fixture.jobStatus(), fixture.pendingOutbox());
        }).sorted(Comparator.comparing(FixtureView::date)
                .thenComparing(FixtureView::fixtureId)).toList();
        return new OverlayProjection(season.seasonId(), overlay, fixtures);
    }

    private CareerCalendarRelationalStore.CalendarRow ready(
            CareerRelationalStore.CareerRow career
    ) {
        try {
            return calendars.loadReady(career);
        } catch (CareerCalendarRelationalStore.CalendarMigrationRequired migration) {
            throw CareerException.calendarMigrationRequired();
        } catch (CareerCalendarRelationalStore.CalendarNotFound missing) {
            throw CareerException.calendarNotFound();
        } catch (CareerCalendarRelationalStore.CalendarIntegrityFailure integrity) {
            throw CareerException.calendarIntegrity(integrity);
        }
    }

    private static void requireStateCareer(
            CareerRelationalStore.CareerRow career,
            CareerCalendarRelationalStore.CalendarRow state
    ) {
        if (!career.careerId().equals(state.careerId())) {
            throw new IllegalStateException("CAREER_CALENDAR_OWNER_MISMATCH");
        }
    }

    private static ProcessedEvent lastProcessed(
            CareerCalendarTemplate.ProjectedCalendar calendar,
            LocalDate date
    ) {
        CareerCalendarTemplate.ProjectedEvent event = calendar.events().stream()
                .filter(value -> value.endDate().isBefore(date))
                .reduce((first, second) -> second).orElse(null);
        return event == null ? new ProcessedEvent(null, null)
                : new ProcessedEvent(event.eventId(), event.endDate());
    }

    public record CalendarView(
            CareerCalendarRelationalStore.CalendarRow state,
            int referenceYear,
            String sourceAsOf,
            String referenceCatalogSnapshotAt,
            String stateHashAlgorithm,
            String projectionStatus,
            CareerCalendarTemplate.ProjectedEvent currentEvent,
            CareerCalendarTemplate.ProjectedEvent nextEvent,
            CareerCalendarTemplate.ProjectedStage currentStage,
            CareerCalendarTemplate.ProjectedStage nextStage,
            List<CareerCalendarTemplate.ProjectedEvent> upcomingEvents,
            CareerCalendarTemplate.FixtureOverlay fixtureOverlay,
            List<FixtureView> upcomingFixtures,
            FixtureView nextManagedFixture,
            List<String> allowedAdvanceModes,
            CareerCalendarTemplate.Counts counts,
            List<CareerCalendarTemplate.QualificationEdge> qualificationEdges,
            List<CareerCalendarTemplate.PendingOfficialField> pendingOfficialFields,
            List<SourceDataNote> sourceDataNotes
    ) {
        public CalendarView {
            upcomingEvents = List.copyOf(upcomingEvents);
            upcomingFixtures = List.copyOf(upcomingFixtures);
            allowedAdvanceModes = List.copyOf(allowedAdvanceModes);
            qualificationEdges = List.copyOf(qualificationEdges);
            pendingOfficialFields = List.copyOf(pendingOfficialFields);
            sourceDataNotes = List.copyOf(sourceDataNotes);
        }
    }

    public record FixtureView(
            String fixtureId, int roundNumber, LocalDate date, String scheduleStatus,
            String executionMode, String firstTeamCode, String secondTeamCode,
            String lifecycleStatus, String seriesId, String jobStatus,
            boolean pendingOutbox
    ) {}

    public record SourceDataNote(String subject, String status) {}

    public record AdvanceResult(
            boolean replayed, boolean pending, int httpStatus, String stopReason,
            boolean backgroundAccepted, CalendarView calendar
    ) {}

    private record OverlayProjection(
            String seasonId,
            CareerCalendarTemplate.FixtureOverlay overlay,
            List<FixtureView> fixtures
    ) {}

    private record ProcessedEvent(String eventId, LocalDate date) {}
}
