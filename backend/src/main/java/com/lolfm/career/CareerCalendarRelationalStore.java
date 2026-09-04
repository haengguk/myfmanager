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

    /** Read-only load. Legacy activation is owned by explicit startup recovery. */
    public CalendarRow loadReady(CareerRelationalStore.CareerRow career) {
        CalendarRow result = find(career.careerId()).orElseThrow(CalendarNotFound::new);
        requireFrozenTemplate(result);
        if ("MIGRATION_REQUIRED".equals(result.lifecycleStatus())
                || "MIGRATION_PENDING".equals(result.lifecycleStatus())) {
            throw new CalendarMigrationRequired();
        }
        requireStateIntegrity(result);
        return result;
    }

    /** Explicit idempotent startup recovery for V5 state rows; never runs from GET. */
    public void recoverLegacyStates() {
        List<String> careerIds = jdbc.query("""
                SELECT career_id FROM career_calendar_state
                WHERE lifecycle_status = 'MIGRATION_PENDING' ORDER BY career_id
                """, (result, ignored) -> result.getString(1));
        for (String careerId : careerIds) {
            transactions.executeWithoutResult(ignored -> recoverLegacyState(careerId));
        }
    }

    private void recoverLegacyState(String careerId) {
        CalendarRow row = findForUpdate(careerId).orElseThrow(CalendarNotFound::new);
        if (!"MIGRATION_PENDING".equals(row.lifecycleStatus())) return;
        requireFrozenTemplate(row);
        LocalDate careerDate = jdbc.queryForObject(
                "SELECT current_game_date FROM career_save WHERE career_id = ?",
                LocalDate.class, careerId);
        if (row.calendarRevision() != 0 || row.eventCursor() != 0
                || !row.currentDate().equals(careerDate)
                || !row.calendarStateHash().chars().allMatch(value -> value == '0')) {
            markMigrationRequired(row.careerId());
            return;
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
    }

    public Optional<CalendarRow> find(String careerId) {
        List<CalendarRow> rows = jdbc.query(selectState() + " WHERE career_id = ?",
                (result, ignored) -> calendar(result), careerId);
        if (rows.size() > 1) throw new CalendarIntegrityFailure();
        return rows.stream().findFirst();
    }

    public Optional<PendingAdvance> activePending(String careerId) {
        return pendingStatus(careerId).pending();
    }

    public PendingStatus pendingStatus(String careerId) {
        CareerIdentity.requireCareerId(careerId);
        List<CommandRow> rows = jdbc.query(selectCommand() + """
                 WHERE career_id = ? AND command_status = 'PENDING'
                 ORDER BY created_at, client_command_id
                """, (result, ignored) -> command(result), careerId);
        if (rows.size() > 1) throw new CommandReceiptIntegrityFailure();
        if (rows.isEmpty()) return new PendingStatus(Optional.empty(), null);
        CommandRow row = rows.getFirst();
        if (row.completedAt() != null || row.createdAt() == null
                || row.updatedAt() == null || row.updatedAt().isBefore(row.createdAt())) {
            throw new CommandReceiptIntegrityFailure();
        }
        if (row.requestMode() == null && row.requestExpectedRevision() == null) {
            return new PendingStatus(Optional.empty(),
                    "LEGACY_PENDING_RECONCILIATION_REQUIRED");
        }
        requireCanonicalRequest(row);
        return new PendingStatus(Optional.of(new PendingAdvance(row.commandId(), row.requestMode(),
                row.requestExpectedRevision(), row.commandStatus(), row.createdAt(),
                row.updatedAt())), null);
    }

    public AdvanceStoreResult execute(
            String commandId,
            String careerId,
            long expectedRevision,
            String mode,
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
                    return completedReplay(command, state, mode, expectedRevision);
                }
                if (command.requestMode() == null
                        && command.requestExpectedRevision() == null) {
                    throw new LegacyPendingReconciliationRequired();
                }
                requireCanonicalRequest(command);
                if (!mode.equals(command.requestMode())
                        || expectedRevision != command.requestExpectedRevision()) {
                    throw new CommandReceiptIntegrityFailure();
                }
            } else {
                Integer pending = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM career_calendar_advance_command
                        WHERE career_id = ? AND command_status = 'PENDING'
                        """, Integer.class, careerId);
                if (pending == null || pending < 0 || pending > 1) {
                    throw new CommandReceiptIntegrityFailure();
                }
                if (pending == 1) {
                    PendingStatus status = pendingStatus(careerId);
                    if (status.recoveryBlocker() != null) {
                        throw new LegacyPendingReconciliationRequired();
                    }
                    throw new AdvanceAlreadyPending();
                }
                jdbc.update("""
                        INSERT INTO career_calendar_advance_command(
                          client_command_id, career_id, command_schema, payload_hash,
                          command_status, request_mode, request_expected_revision,
                          background_required, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'PENDING', ?, ?, FALSE, ?, ?)
                        """, commandId, careerId,
                        CareerCalendarTemplate.ADVANCE_COMMAND_SCHEMA, payloadHash,
                        mode, expectedRevision, now(), now());
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
                        background_required = ?, completed_at = ?, updated_at = ?
                    WHERE client_command_id = ? AND command_status = 'PENDING'
                    """, mutation.pending() ? "PENDING" : "COMPLETED",
                    row.seasonYear(), mutation.currentDate(), cursor, revision, stateHash,
                    mutation.lastProcessedEventId(), mutation.lastProcessedDate(),
                    mutation.lifecycleStatus(), mutation.blockingReason(),
                    mutation.httpStatus(), mutation.stopReason(),
                    mutation.backgroundRequired(), completedAt, now(), commandId);
            if (receipt != 1) throw new CommandReceiptIntegrityFailure();
            CalendarRow updated = find(careerId).orElseThrow(CalendarNotFound::new);
            CommandRow storedCommand = findCommand(commandId).orElseThrow(
                    CommandReceiptIntegrityFailure::new);
            return new AdvanceStoreResult(continuingPending, mutation.pending(),
                    mutation.httpStatus(), mutation.stopReason(),
                    mutation.backgroundRequired(), commandResult(storedCommand,
                    mode, expectedRevision), updated);
        });
    }

    private AdvanceStoreResult completedReplay(
            CommandRow command,
            CalendarRow state,
            String suppliedMode,
            long suppliedExpectedRevision
    ) {
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
        return new AdvanceStoreResult(true, false, command.httpStatus(),
                command.stopReason(), command.backgroundRequired(),
                commandResult(command, suppliedMode, suppliedExpectedRevision), state);
    }

    private CommandResult commandResult(
            CommandRow command,
            String suppliedMode,
            long suppliedExpectedRevision
    ) {
        String mode = command.requestMode() == null
                ? suppliedMode : command.requestMode();
        long expectedRevision = command.requestExpectedRevision() == null
                ? suppliedExpectedRevision : command.requestExpectedRevision();
        String expectedPayload = template.advancePayloadHash(command.careerId(),
                expectedRevision, mode);
        if (!expectedPayload.equals(command.payloadHash())
                || command.createdAt() == null || command.updatedAt() == null
                || command.updatedAt().isBefore(command.createdAt())) {
            throw new CommandReceiptIntegrityFailure();
        }
        return new CommandResult(command.commandId(), mode, expectedRevision,
                command.commandStatus(), command.resultSeasonYear(), command.resultDate(),
                command.resultEventCursor(), command.resultRevision(),
                command.resultStateHash(), command.resultLifecycleStatus(),
                command.resultBlockingReason(), command.httpStatus(),
                command.stopReason(), command.backgroundRequired(), command.createdAt(),
                command.updatedAt(), command.completedAt());
    }

    private Optional<CommandRow> findCommand(String commandId) {
        List<CommandRow> rows = jdbc.query(selectCommand()
                + " WHERE client_command_id = ?", (result, ignored) -> command(result),
                commandId);
        if (rows.size() > 1) throw new CommandReceiptIntegrityFailure();
        return rows.stream().findFirst();
    }

    private static String selectCommand() {
        return """
                SELECT client_command_id, career_id, command_schema, payload_hash,
                       command_status, request_mode, request_expected_revision,
                       result_active_calendar_season_year,
                       result_current_game_date, result_event_cursor,
                       result_calendar_revision, result_state_hash,
                       result_last_processed_event_id, result_last_processed_date,
                       result_lifecycle_status, result_blocking_reason, http_status,
                       stop_reason, background_required, created_at, updated_at,
                       completed_at
                FROM career_calendar_advance_command
                """;
    }

    private static CommandRow command(ResultSet result) throws SQLException {
        return new CommandRow(result.getString(1), result.getString(2),
                result.getString(3), result.getString(4), result.getString(5),
                result.getString(6), (Long) result.getObject(7),
                (Integer) result.getObject(8), result.getObject(9, LocalDate.class),
                (Integer) result.getObject(10), (Long) result.getObject(11),
                result.getString(12), result.getString(13),
                result.getObject(14, LocalDate.class), result.getString(15),
                result.getString(16), (Integer) result.getObject(17),
                result.getString(18), result.getBoolean(19),
                result.getObject(20, OffsetDateTime.class),
                result.getObject(21, OffsetDateTime.class),
                result.getObject(22, OffsetDateTime.class));
    }

    private void requireCanonicalRequest(CommandRow command) {
        if (!CareerCalendarTemplate.ADVANCE_COMMAND_SCHEMA.equals(
                command.commandSchema()) || command.requestMode() == null
                || command.requestExpectedRevision() == null
                || !template.advancePayloadHash(command.careerId(),
                command.requestExpectedRevision(), command.requestMode())
                .equals(command.payloadHash())) {
            throw new CommandReceiptIntegrityFailure();
        }
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
            boolean backgroundRequired, CommandResult commandResult, CalendarRow state
    ) {}

    public record PendingAdvance(
            String clientCommandId, String mode, long expectedRevision, String status,
            OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {}

    public record PendingStatus(
            Optional<PendingAdvance> pending, String recoveryBlocker
    ) {}

    public record CommandResult(
            String clientCommandId, String mode, long expectedRevision, String status,
            Integer resultingSeasonYear, LocalDate resultingDate,
            Integer resultingEventCursor, Long resultingRevision,
            String resultingStateHash, String resultingLifecycleStatus,
            String resultingBlockingReason, Integer httpStatus, String stopReason,
            boolean backgroundRequired, OffsetDateTime createdAt,
            OffsetDateTime updatedAt, OffsetDateTime completedAt
    ) {}

    private record CommandRow(
            String commandId, String careerId, String commandSchema, String payloadHash,
            String commandStatus, String requestMode, Long requestExpectedRevision,
            Integer resultSeasonYear, LocalDate resultDate,
            Integer resultEventCursor, Long resultRevision, String resultStateHash,
            String resultLastProcessedEventId, LocalDate resultLastProcessedDate,
            String resultLifecycleStatus, String resultBlockingReason,
            Integer httpStatus, String stopReason,
            boolean backgroundRequired, OffsetDateTime createdAt,
            OffsetDateTime updatedAt, OffsetDateTime completedAt
    ) {}

    public static final class CalendarNotFound extends RuntimeException {}
    public static final class CalendarIntegrityFailure extends RuntimeException {}
    public static final class CalendarMigrationRequired extends RuntimeException {}
    public static final class CommandConflict extends RuntimeException {}
    public static final class CommandReceiptIntegrityFailure extends RuntimeException {}
    public static final class AdvanceAlreadyPending extends RuntimeException {}
    public static final class LegacyPendingReconciliationRequired extends RuntimeException {}
    public static final class StaleRevision extends RuntimeException {}
}
