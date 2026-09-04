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
    private final CareerCompetitionApplicationService competitions;

    public CareerCalendarApplicationService(
            CareerCalendarRelationalStore calendars,
            CareerCalendarTemplate template,
            CareerCalendarLeaguePort leagues,
            CareerCompetitionApplicationService competitions
    ) {
        this.calendars = Objects.requireNonNull(calendars, "calendars");
        this.template = Objects.requireNonNull(template, "template");
        this.leagues = Objects.requireNonNull(leagues, "leagues");
        this.competitions = Objects.requireNonNull(competitions, "competitions");
    }

    public void initializeNew(CareerRelationalStore.NewCareer career) {
        calendars.initializeNew(career);
        competitions.initializeNew(career, template.anchorYear(career.currentDate()));
    }

    public LocalDate currentDate(CareerRelationalStore.CareerRow career) {
        return ready(career).currentDate();
    }

    public CalendarView view(CareerRelationalStore.CareerRow career) {
        try {
            return buildView(career, ready(career));
        } catch (CareerCalendarRelationalStore.CommandReceiptIntegrityFailure corrupt) {
            throw CareerException.calendarCommandIntegrity();
        } catch (DataIntegrityViolationException | IllegalArgumentException
                | IllegalStateException failure) {
            throw CareerException.calendarIntegrity(failure);
        }
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
                    commandId, career.careerId(), expectedCalendarRevision, mode,
                    payloadHash, state -> transition(career, state, mode));
            boolean backgroundAccepted = true;
            if (stored.backgroundRequired()) {
                backgroundAccepted = leagues.wakeBackground(commandId);
                if (!backgroundAccepted) {
                    throw CareerException.calendarBackgroundUnavailable();
                }
            }
            return new AdvanceResult(stored.replayed(), stored.pending(),
                    stored.httpStatus(), stored.stopReason(), backgroundAccepted,
                    new CommandResult(stored.commandResult(), backgroundAccepted),
                    buildView(career, stored.state()));
        } catch (CareerCalendarRelationalStore.StaleRevision stale) {
            throw CareerException.calendarStaleRevision();
        } catch (CareerCalendarRelationalStore.CommandConflict conflict) {
            throw CareerException.calendarCommandConflict();
        } catch (CareerCalendarRelationalStore.AdvanceAlreadyPending pending) {
            throw CareerException.calendarAdvanceAlreadyPending();
        } catch (CareerCalendarRelationalStore.LegacyPendingReconciliationRequired legacy) {
            throw CareerException.calendarLegacyPendingReconciliationRequired();
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
        competitions.reconcileForAdvance(career, state.seasonYear(), overlay.season());

        CareerCalendarLeaguePort.GateResult currentGate = gate(career, state.seasonYear(),
                state.currentDate(), projected, overlay);
        if (currentGate.stopReason() != null) {
            return gated(state, projected, state.currentDate(), currentGate, false);
        }
        if ("AUTO_FIXTURES_PENDING".equals(state.blockingReason())) {
            ProcessedEvent processed = lastProcessed(projected, state.currentDate());
            return mutation(state, state.currentDate(), state.eventCursor(),
                    processed.eventId(), processed.date(), "ACTIVE", null,
                    true, false, 200, null, false);
        }

        LocalDate target = targetDate(career, state.seasonYear(), mode,
                state.currentDate(), projected, overlay);
        if (target == null || target.isAfter(projected.events().getLast().endDate())) {
            return mutation(state, state.currentDate(), state.eventCursor(),
                    state.lastProcessedEventId(), state.lastProcessedDate(),
                    "SEASON_ROLLOVER_REQUIRED", "SEASON_ROLLOVER_REQUIRED",
                    true, false, 200, "SEASON_ROLLOVER_REQUIRED", false);
        }
        CareerCalendarLeaguePort.GateResult targetGate = gate(career, state.seasonYear(),
                target, projected, overlay);
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
            CareerRelationalStore.CareerRow career,
            int calendarSeasonYear,
            LocalDate date,
            CareerCalendarTemplate.ProjectedCalendar projected,
            OverlayProjection overlay
    ) {
        List<String> ids = overlay.fixtures().stream()
                .filter(value -> value.date().equals(date))
                .map(FixtureView::fixtureId).toList();
        CareerCalendarLeaguePort.GateResult league = leagues.gateAndDispatch(
                overlay.season().seasonId(), ids);
        if ("COMPETITION_TRANSITION_REQUIRED".equals(league.stopReason())
                && competitions.transitionReady(career, calendarSeasonYear,
                overlay.season())) {
            league = CareerCalendarLeaguePort.GateResult.clear();
        }
        if (league.stopReason() != null) return league;
        CareerCompetitionApplicationService.CompetitionGate competition =
                competitions.gate(career, calendarSeasonYear, date,
                        competitionIdAt(projected, date), overlay.season());
        return competition.stopReason() == null ? league
                : new CareerCalendarLeaguePort.GateResult(competition.stopReason(),
                false, false, competition.fixtureId(), competition.seriesId());
    }

    private static String competitionIdAt(
            CareerCalendarTemplate.ProjectedCalendar projected, LocalDate date
    ) {
        return projected.events().stream().filter(value -> !date.isBefore(
                value.startDate()) && !date.isAfter(value.endDate()))
                .map(CareerCalendarTemplate.ProjectedEvent::templateId)
                .findFirst().orElse(null);
    }

    private LocalDate targetDate(
            CareerRelationalStore.CareerRow career,
            int calendarSeasonYear,
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
        CareerCompetitionApplicationService.CompetitionView competition =
                competitions.view(career, calendarSeasonYear, current, null, null);
        if (competition.nextFixture() != null
                && competition.nextFixture().date().isAfter(current)) {
            candidates.add(competition.nextFixture().date());
        }
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
        CareerCalendarRelationalStore.PendingStatus pendingStatus =
                calendars.pendingStatus(state.careerId());
        CareerCalendarRelationalStore.PendingAdvance activePending =
                pendingStatus.pending().orElse(null);
        CareerCompetitionApplicationService.CompetitionView competition =
                competitions.view(career, state.seasonYear(),
                        state.currentDate(), current == null ? null : current.templateId(),
                        next == null ? null : next.templateId());
        CareerCompetitionApplicationService.CompetitionGate competitionGate =
                competitions.gate(career, state.seasonYear(), state.currentDate(),
                        current == null ? null : current.templateId(), overlay.season());
        boolean seasonCanAdvance = normalSeasonLifecycle(
                overlay.season().seasonLifecycleStatus())
                || "COMPLETED".equals(overlay.season().seasonLifecycleStatus())
                && overlay.season().allFixturesCompleted();
        List<String> commands = "ACTIVE".equals(state.lifecycleStatus())
                && seasonCanAdvance && competitionGate.stopReason() == null
                && activePending == null && pendingStatus.recoveryBlocker() == null
                ? ADVANCE_MODES : List.of();
        String blockingReason = lifecycleBlockingReason(
                overlay.season().seasonLifecycleStatus(),
                overlay.season().allFixturesCompleted());
        if (blockingReason == null && competitionGate.stopReason() != null) {
            blockingReason = competitionGate.stopReason();
        }
        if (pendingStatus.recoveryBlocker() != null) {
            blockingReason = pendingStatus.recoveryBlocker();
        }
        if (blockingReason == null && !seasonLifecycleBlockingReason(
                state.blockingReason())) {
            blockingReason = state.blockingReason();
        }
        return new CalendarView(state, template.body().referenceYear(),
                template.body().sourceAsOf(),
                template.body().referenceCatalogSnapshotAt(),
                CareerCalendarTemplate.STATE_HASH_ALGORITHM,
                projected.projectionStatus(), current, next, currentStage, nextStage,
                upcoming,
                overlay.overlay(), upcomingFixtures, nextManaged, commands,
                blockingReason, activePending, pendingStatus.recoveryBlocker(), competition,
                template.body().counts(), template.body().qualificationEdges(),
                template.body().pendingOfficialFields(),
                List.of(new SourceDataNote("KESPA_CUP",
                        "REFERENCE_TEMPLATE_NOT_OFFICIAL_FOR_2026_OR_FUTURE",
                        2025, "KESPA_CUP_REFERENCE_TEMPLATE_2025",
                        List.of("KESPA_CUP_2026_RULE_SOURCE_INCOMPLETE",
                                "EXTERNAL_PARTICIPANT_ROSTER_AUTHORITY_MISSING"))));
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
                        value.secondTeamCode(), value.fixtureRootSeed(),
                        value.boundSeriesId())).toList();
        CareerCalendarTemplate.FixtureOverlay overlay = template.overlay(
                state.seasonYear(), season.leagueId(), season.seasonId(),
                season.scheduleIdentity(), inputs);
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
        return new OverlayProjection(season, overlay, fixtures);
    }

    private static boolean normalSeasonLifecycle(String status) {
        return "READY".equals(status) || "RUNNING".equals(status)
                || "WAITING_FOR_PLAYER".equals(status);
    }

    private static String lifecycleBlockingReason(
            String status, boolean allFixturesCompleted
    ) {
        return switch (status) {
            case "READY", "RUNNING", "WAITING_FOR_PLAYER" -> null;
            case "PAUSED" -> "SEASON_PAUSED";
            case "BLOCKED" -> "ATTENTION_REQUIRED";
            case "CANCELLED" -> "SEASON_CANCELLED";
            case "COMPLETED" -> allFixturesCompleted
                    ? null : "COMPETITION_TRANSITION_REQUIRED";
            case "DRAFT", "FROZEN" -> "SEASON_NOT_READY";
            default -> throw new IllegalStateException(
                    "CAREER_CALENDAR_UNKNOWN_SEASON_STATUS");
        };
    }

    private static boolean seasonLifecycleBlockingReason(String reason) {
        return "SEASON_PAUSED".equals(reason)
                || "ATTENTION_REQUIRED".equals(reason)
                || "SEASON_CANCELLED".equals(reason)
                || "COMPETITION_TRANSITION_REQUIRED".equals(reason)
                || "SEASON_NOT_READY".equals(reason);
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
            String blockingReason,
            CareerCalendarRelationalStore.PendingAdvance activePendingAdvance,
            String advanceRecoveryStatus,
            CareerCompetitionApplicationService.CompetitionView competition,
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

    public record SourceDataNote(
            String subject, String status, int sourceReferenceYear,
            String ruleVersion, List<String> blockers
    ) {
        public SourceDataNote { blockers = List.copyOf(blockers); }
    }

    public record AdvanceResult(
            boolean replayed, boolean pending, int httpStatus, String stopReason,
            boolean backgroundAccepted, CommandResult commandResult,
            CalendarView calendar
    ) {}

    public record CommandResult(
            CareerCalendarRelationalStore.CommandResult receipt,
            boolean backgroundAccepted
    ) {}

    private record OverlayProjection(
            CareerCalendarLeaguePort.SeasonProjection season,
            CareerCalendarTemplate.FixtureOverlay overlay,
            List<FixtureView> fixtures
    ) {}

    private record ProcessedEvent(String eventId, LocalDate date) {}
}
