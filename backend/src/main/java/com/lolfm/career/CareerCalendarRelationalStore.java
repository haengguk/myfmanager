package com.lolfm.career;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Relational authority for mutable Career time and idempotent advance commands. */
@Component
public final class CareerCalendarRelationalStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final CareerCalendarTemplate template;

    @Autowired
    public CareerCalendarRelationalStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            CareerCalendarTemplate template
    ) {
        this(jdbc, transactionManager, Clock.systemUTC(), template);
    }

    CareerCalendarRelationalStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            Clock clock,
            CareerCalendarTemplate template
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.template = Objects.requireNonNull(template, "template");
    }

    /** Called after career_save is inserted, inside that same outer transaction. */
    public void initializeNew(CareerRelationalStore.NewCareer career) {
        int year = template.anchorYear(career.currentDate());
        String hash = template.stateHash(career.careerId(), year, career.currentDate(),
                0, 0, null, null, "ACTIVE", null);
        OffsetDateTime now = now();
        int inserted = jdbc.update("""
                INSERT INTO career_calendar_state(
                  career_id, calendar_schema, template_version, template_hash,
                  projection_policy, anchor_algorithm, fixture_allocation_policy,
                  active_calendar_season_year, current_game_date, event_cursor,
                  calendar_revision, calendar_state_hash, last_processed_event_id,
                  last_processed_date, lifecycle_status, blocking_reason,
                  created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, NULL, NULL,
                        'ACTIVE', NULL, ?, ?)
                """, career.careerId(), CareerCalendarTemplate.CALENDAR_SCHEMA,
                template.version(), template.templateHash(),
                CareerCalendarTemplate.PROJECTION_POLICY,
                CareerCalendarTemplate.ANCHOR_ALGORITHM,
                CareerCalendarTemplate.FIXTURE_ALLOCATION_POLICY,
                year, career.currentDate(), hash, now, now);
        if (inserted != 1) throw new CalendarIntegrityFailure();
    }

    /**
     * V5 legacy rows are activated only after the unchanged Career V1 binding was
     * independently validated by CareerApplicationService.
     */
    public CalendarRow loadReady(CareerRelationalStore.CareerRow career) {
        CalendarRow result = transactions.execute(ignored -> {
            CalendarRow row = findForUpdate(career.careerId()).orElseThrow(
                    CalendarNotFound::new);
            requireFrozenTemplate(row);
            if ("MIGRATION_PENDING".equals(row.lifecycleStatus())) {
                if (row.calendarRevision() != 0 || row.eventCursor() != 0
                        || !row.currentDate().equals(career.currentDate())
                        || !row.calendarStateHash().chars().allMatch(value -> value == '0')) {
                    markMigrationRequired(row.careerId());
                    return findForUpdate(row.careerId()).orElseThrow(
                            CalendarNotFound::new);
                }
                int year = template.anchorYear(row.currentDate());
                String hash = template.stateHash(row.careerId(), year,
                        row.currentDate(), 0, 0, null, null, "ACTIVE", null);
                int updated = jdbc.update("""
                        UPDATE career_calendar_state
                        SET active_calendar_season_year = ?, calendar_state_hash = ?,
                            lifecycle_status = 'ACTIVE', blocking_reason = NULL,
                            updated_at = ?
                        WHERE career_id = ? AND lifecycle_status = 'MIGRATION_PENDING'
                        """, year, hash, now(), row.careerId());
                if (updated != 1) throw new CalendarIntegrityFailure();
                row = findForUpdate(row.careerId()).orElseThrow(CalendarNotFound::new);
            }
            requireStateIntegrity(row);
            return row;
        });
        if ("MIGRATION_REQUIRED".equals(result.lifecycleStatus())) {
            throw new CalendarMigrationRequired();
        }
        return result;
    }

    public Optional<CalendarRow> find(String careerId) {
        List<CalendarRow> rows = jdbc.query(selectState() + " WHERE career_id = ?",
                (result, ignored) -> calendar(result), careerId);
        if (rows.size() > 1) throw new CalendarIntegrityFailure();
        return rows.stream().findFirst();
    }

    public AdvanceStoreResult execute(
            String commandId,
            String careerId,
            long expectedRevision,
            String payloadHash,
            Function<CalendarRow, AdvanceMutation> operation
    ) {
        Objects.requireNonNull(operation, "operation");
        CareerIdentity.canonicalCommandId(commandId);
        CareerIdentity.requireCareerId(careerId);
        CareerIdentity.requireSha256(payloadHash, "payloadHash");
        return transactions.execute(ignored -> {
            lockAdvanceCommands();
            Optional<CommandRow> prior = findCommand(commandId);
            if (prior.isPresent()) {
                CommandRow command = prior.get();
                if (!CareerCalendarTemplate.ADVANCE_COMMAND_SCHEMA.equals(
                        command.commandSchema())) {
                    throw new CommandReceiptIntegrityFailure();
                }
                if (!careerId.equals(command.careerId())
                        || !payloadHash.equals(command.payloadHash())) {
                    throw new CommandConflict();
                }
                if ("COMPLETED".equals(command.commandStatus())) {
                    CalendarRow state = find(command.careerId()).orElseThrow(
                            CommandReceiptIntegrityFailure::new);
                    requireFrozenTemplate(state);
                    requireStateIntegrity(state);
                    return completedReplay(command, state);
                }
            } else {
                Integer pending = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM career_calendar_advance_command
                        WHERE career_id = ? AND command_status = 'PENDING'
                        """, Integer.class, careerId);
                if (pending == null || pending < 0 || pending > 1) {
                    throw new CommandReceiptIntegrityFailure();
                }
                if (pending == 1) throw new AdvanceAlreadyPending();
                jdbc.update("""
                        INSERT INTO career_calendar_advance_command(
                          client_command_id, career_id, command_schema, payload_hash,
                          command_status, background_required, created_at)
                        VALUES (?, ?, ?, ?, 'PENDING', FALSE, ?)
                        """, commandId, careerId,
                        CareerCalendarTemplate.ADVANCE_COMMAND_SCHEMA, payloadHash, now());
            }

            CalendarRow row = findForUpdate(careerId).orElseThrow(CalendarNotFound::new);
            requireFrozenTemplate(row);
            requireStateIntegrity(row);
            if ("MIGRATION_REQUIRED".equals(row.lifecycleStatus())
                    || "MIGRATION_PENDING".equals(row.lifecycleStatus())) {
                throw new CalendarMigrationRequired();
            }
            boolean continuingPending = prior.isPresent();
            if (!continuingPending && row.calendarRevision() != expectedRevision) {
                throw new StaleRevision();
            }
            AdvanceMutation mutation = Objects.requireNonNull(operation.apply(row),
                    "advanceMutation");
            if (mutation.currentDate().isBefore(row.currentDate())
                    || mutation.eventCursor() < row.eventCursor()) {
                throw new CalendarIntegrityFailure();
            }
            if (!mutation.stateChanged()
                    && (!mutation.currentDate().equals(row.currentDate())
                    || mutation.eventCursor() != row.eventCursor()
                    || !Objects.equals(mutation.lastProcessedEventId(),
                    row.lastProcessedEventId())
                    || !Objects.equals(mutation.lastProcessedDate(),
                    row.lastProcessedDate())
                    || !mutation.lifecycleStatus().equals(row.lifecycleStatus())
                    || !Objects.equals(mutation.blockingReason(), row.blockingReason()))) {
                throw new CalendarIntegrityFailure();
            }
            long revision = mutation.stateChanged()
                    ? Math.addExact(row.calendarRevision(), 1) : row.calendarRevision();
            int cursor = mutation.eventCursor();
            String stateHash = template.stateHash(row.careerId(), row.seasonYear(),
                    mutation.currentDate(), cursor, revision,
                    mutation.lastProcessedEventId(), mutation.lastProcessedDate(),
                    mutation.lifecycleStatus(), mutation.blockingReason());
            OffsetDateTime completedAt = mutation.pending() ? null : now();
            if (mutation.stateChanged()) {
                int updated = jdbc.update("""
                        UPDATE career_calendar_state
                        SET current_game_date = ?, event_cursor = ?, calendar_revision = ?,
                            calendar_state_hash = ?, last_processed_event_id = ?,
                            last_processed_date = ?, lifecycle_status = ?,
                            blocking_reason = ?, updated_at = ?
                        WHERE career_id = ? AND calendar_revision = ?
                        """, mutation.currentDate(), cursor, revision, stateHash,
                        mutation.lastProcessedEventId(), mutation.lastProcessedDate(),
                        mutation.lifecycleStatus(), mutation.blockingReason(), now(),
                        row.careerId(), row.calendarRevision());
                if (updated != 1) throw new StaleRevision();
                jdbc.update("UPDATE career_save SET updated_at = ? WHERE career_id = ?",
                        now(), row.careerId());
            }
            int receipt = jdbc.update("""
                    UPDATE career_calendar_advance_command
                    SET command_status = ?, result_active_calendar_season_year = ?,
                        result_current_game_date = ?, result_event_cursor = ?,
                        result_calendar_revision = ?, result_state_hash = ?,
                        result_last_processed_event_id = ?,
                        result_last_processed_date = ?, result_lifecycle_status = ?,
                        result_blocking_reason = ?, http_status = ?, stop_reason = ?,
                        background_required = ?, completed_at = ?
                    WHERE client_command_id = ? AND command_status = 'PENDING'
                    """, mutation.pending() ? "PENDING" : "COMPLETED",
                    row.seasonYear(), mutation.currentDate(), cursor, revision, stateHash,
                    mutation.lastProcessedEventId(), mutation.lastProcessedDate(),
                    mutation.lifecycleStatus(), mutation.blockingReason(),
                    mutation.httpStatus(), mutation.stopReason(),
                    mutation.backgroundRequired(), completedAt, commandId);
            if (receipt != 1) throw new CommandReceiptIntegrityFailure();
            CalendarRow updated = find(careerId).orElseThrow(CalendarNotFound::new);
            return new AdvanceStoreResult(continuingPending, mutation.pending(),
                    mutation.httpStatus(), mutation.stopReason(),
                    mutation.backgroundRequired(), updated);
        });
    }

    private AdvanceStoreResult completedReplay(CommandRow command, CalendarRow state) {
        if (command.resultSeasonYear() == null || command.resultDate() == null
                || command.resultEventCursor() == null
                || command.resultRevision() == null || command.resultStateHash() == null
                || command.resultLifecycleStatus() == null
                || command.httpStatus() == null || command.completedAt() == null) {
            throw new CommandReceiptIntegrityFailure();
        }
        String expectedHash = template.stateHash(command.careerId(),
                command.resultSeasonYear(), command.resultDate(),
                command.resultEventCursor(), command.resultRevision(),
                command.resultLastProcessedEventId(), command.resultLastProcessedDate(),
                command.resultLifecycleStatus(), command.resultBlockingReason());
        if (!expectedHash.equals(command.resultStateHash())) {
            throw new CommandReceiptIntegrityFailure();
        }
        CalendarRow result = new CalendarRow(state.careerId(), state.calendarSchema(),
                state.templateVersion(), state.templateHash(), state.projectionPolicy(),
                state.anchorAlgorithm(), state.fixtureAllocationPolicy(),
                command.resultSeasonYear(), command.resultDate(),
                command.resultEventCursor(), command.resultRevision(),
                command.resultStateHash(), command.resultLastProcessedEventId(),
                command.resultLastProcessedDate(), command.resultLifecycleStatus(),
                command.resultBlockingReason(), state.createdAt(), state.updatedAt());
        return new AdvanceStoreResult(true, false, command.httpStatus(),
                command.stopReason(), command.backgroundRequired(), result);
    }

    private Optional<CommandRow> findCommand(String commandId) {
        List<CommandRow> rows = jdbc.query("""
                SELECT client_command_id, career_id, command_schema, payload_hash,
                       command_status, result_active_calendar_season_year,
                       result_current_game_date, result_event_cursor,
                       result_calendar_revision, result_state_hash,
                       result_last_processed_event_id, result_last_processed_date,
                       result_lifecycle_status, result_blocking_reason, http_status,
                       stop_reason, background_required, completed_at
                FROM career_calendar_advance_command WHERE client_command_id = ?
                """, (result, ignored) -> new CommandRow(result.getString(1),
                result.getString(2), result.getString(3), result.getString(4),
                result.getString(5), (Integer) result.getObject(6),
                result.getObject(7, LocalDate.class), (Integer) result.getObject(8),
                (Long) result.getObject(9), result.getString(10), result.getString(11),
                result.getObject(12, LocalDate.class), result.getString(13),
                result.getString(14), (Integer) result.getObject(15),
                result.getString(16), result.getBoolean(17),
                result.getObject(18, OffsetDateTime.class)),
                commandId);
        if (rows.size() > 1) throw new CommandReceiptIntegrityFailure();
        return rows.stream().findFirst();
    }

    private Optional<CalendarRow> findForUpdate(String careerId) {
        return jdbc.query(selectState() + " WHERE career_id = ? FOR UPDATE",
                (result, ignored) -> calendar(result), careerId).stream().findFirst();
    }

    private static String selectState() {
        return """
                SELECT career_id, calendar_schema, template_version, template_hash,
                       projection_policy, anchor_algorithm, fixture_allocation_policy,
                       active_calendar_season_year, current_game_date, event_cursor,
                       calendar_revision, calendar_state_hash, last_processed_event_id,
                       last_processed_date, lifecycle_status, blocking_reason,
                       created_at, updated_at
                FROM career_calendar_state
                """;
    }

    private void requireFrozenTemplate(CalendarRow row) {
        if (!CareerCalendarTemplate.CALENDAR_SCHEMA.equals(row.calendarSchema())
                || !template.version().equals(row.templateVersion())
                || !template.templateHash().equals(row.templateHash())
                || !CareerCalendarTemplate.PROJECTION_POLICY.equals(row.projectionPolicy())
                || !CareerCalendarTemplate.ANCHOR_ALGORITHM.equals(row.anchorAlgorithm())
                || !CareerCalendarTemplate.FIXTURE_ALLOCATION_POLICY.equals(
                row.fixtureAllocationPolicy())) {
            throw new CalendarIntegrityFailure();
        }
    }

    private void requireStateIntegrity(CalendarRow row) {
        String expected = template.stateHash(row.careerId(), row.seasonYear(),
                row.currentDate(), row.eventCursor(), row.calendarRevision(),
                row.lastProcessedEventId(), row.lastProcessedDate(),
                row.lifecycleStatus(), row.blockingReason());
        if (!expected.equals(row.calendarStateHash())) {
            throw new CalendarIntegrityFailure();
        }
    }

    private void markMigrationRequired(String careerId) {
        jdbc.update("""
                UPDATE career_calendar_state
                SET lifecycle_status = 'MIGRATION_REQUIRED',
                    blocking_reason = 'CAREER_CALENDAR_MIGRATION_REQUIRED',
                    updated_at = ? WHERE career_id = ?
                """, now(), careerId);
    }

    private void lockAdvanceCommands() {
        List<String> locks = jdbc.query("""
                SELECT lock_name FROM career_calendar_operation_lock
                WHERE lock_name = 'ADVANCE_COMMANDS' FOR UPDATE
                """, (result, ignored) -> result.getString(1));
        if (locks.size() != 1) throw new CalendarIntegrityFailure();
    }

    private OffsetDateTime now() {
        return clock.instant().atOffset(ZoneOffset.UTC);
    }

    private static CalendarRow calendar(ResultSet result) throws SQLException {
        return new CalendarRow(result.getString(1), result.getString(2),
                result.getString(3), result.getString(4), result.getString(5),
                result.getString(6), result.getString(7), result.getInt(8),
                result.getObject(9, LocalDate.class), result.getInt(10),
                result.getLong(11), result.getString(12), result.getString(13),
                result.getObject(14, LocalDate.class), result.getString(15),
                result.getString(16), result.getObject(17, OffsetDateTime.class),
                result.getObject(18, OffsetDateTime.class));
    }

    public record CalendarRow(
            String careerId, String calendarSchema, String templateVersion,
            String templateHash, String projectionPolicy, String anchorAlgorithm,
            String fixtureAllocationPolicy, int seasonYear, LocalDate currentDate,
            int eventCursor, long calendarRevision, String calendarStateHash,
            String lastProcessedEventId, LocalDate lastProcessedDate,
            String lifecycleStatus, String blockingReason,
            OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {}

    public record AdvanceMutation(
            LocalDate currentDate, int eventCursor, String lastProcessedEventId,
            LocalDate lastProcessedDate, String lifecycleStatus, String blockingReason,
            boolean stateChanged, boolean pending, int httpStatus, String stopReason,
            boolean backgroundRequired
    ) {}

    public record AdvanceStoreResult(
            boolean replayed, boolean pending, int httpStatus, String stopReason,
            boolean backgroundRequired, CalendarRow state
    ) {}

    private record CommandRow(
            String commandId, String careerId, String commandSchema, String payloadHash,
            String commandStatus, Integer resultSeasonYear, LocalDate resultDate,
            Integer resultEventCursor, Long resultRevision, String resultStateHash,
            String resultLastProcessedEventId, LocalDate resultLastProcessedDate,
            String resultLifecycleStatus, String resultBlockingReason,
            Integer httpStatus, String stopReason,
            boolean backgroundRequired, OffsetDateTime completedAt
    ) {}

    public static final class CalendarNotFound extends RuntimeException {}
    public static final class CalendarIntegrityFailure extends RuntimeException {}
    public static final class CalendarMigrationRequired extends RuntimeException {}
    public static final class CommandConflict extends RuntimeException {}
    public static final class CommandReceiptIntegrityFailure extends RuntimeException {}
    public static final class AdvanceAlreadyPending extends RuntimeException {}
    public static final class StaleRevision extends RuntimeException {}
}
