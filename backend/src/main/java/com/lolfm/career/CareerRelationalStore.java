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
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Relational authority for Career save slots and create-command receipts. */
@Component
public final class CareerRelationalStore {
    public static final int MAX_CAREERS = 100;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final int maximumCareers;

    @Autowired
    public CareerRelationalStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager
    ) {
        this(jdbc, transactionManager, Clock.systemUTC(), MAX_CAREERS);
    }

    CareerRelationalStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this(jdbc, transactionManager, clock, MAX_CAREERS);
    }

    public CareerRelationalStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            Clock clock,
            int maximumCareers
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maximumCareers < 1 || maximumCareers > MAX_CAREERS) {
            throw new IllegalArgumentException("maximumCareers");
        }
        this.maximumCareers = maximumCareers;
    }

    /** The supplier runs inside the same transaction as the command and Career insert. */
    public CreateResult createOrReplay(
            String commandId,
            String payloadHash,
            Supplier<NewCareer> creator
    ) {
        return createOrReplay(commandId, payloadHash, creator, ignored -> {});
    }

    /** Creator, Career insert, initializer, and command receipt share one transaction. */
    public CreateResult createOrReplay(
            String commandId,
            String payloadHash,
            Supplier<NewCareer> creator,
            Consumer<NewCareer> initializer
    ) {
        Objects.requireNonNull(creator, "creator");
        Objects.requireNonNull(initializer, "initializer");
        CareerIdentity.canonicalCommandId(commandId);
        CareerIdentity.requireSha256(payloadHash, "payloadHash");
        return transactions.execute(ignored -> {
            lockCreateCommands();
            Optional<CommandRow> prior = findCommand(commandId);
            if (prior.isPresent()) {
                CommandRow command = prior.get();
                if (!command.clientCommandId().equals(commandId)
                        || !CareerIdentity.COMMAND_SCHEMA.equals(command.commandSchema())
                        || !CareerIdentity.careerId(commandId).equals(command.careerId())) {
                    throw new CommandReceiptIntegrityFailure();
                }
                if (!command.payloadHash().equals(payloadHash)) {
                    throw new CommandConflict();
                }
                CareerRow career = find(command.careerId()).orElseThrow(
                        CommandReceiptIntegrityFailure::new);
                return new CreateResult(true, career);
            }

            Integer current = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM career_save", Integer.class);
            if (current == null || current < 0 || current > maximumCareers) {
                throw new CareerListIntegrityFailure();
            }
            if (current == maximumCareers) throw new CapacityReached();

            NewCareer requested = Objects.requireNonNull(creator.get(), "createdCareer");
            OffsetDateTime now = now();
            jdbc.update("""
                    INSERT INTO career_save(
                      career_id, save_name, manager_name, managed_team_code,
                      start_game_date, current_game_date, league_id, season_id,
                      career_root_seed, seed_algorithm_id,
                      league_frozen_snapshot_hash, league_product_decision_hash,
                      reference_catalog_version, reference_catalog_hash,
                      career_binding_schema, career_binding_hash,
                      career_schema, lifecycle_status, revision, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, requested.careerId(), requested.saveName(),
                    requested.managerName(), requested.managedTeamCode(),
                    requested.startDate(), requested.currentDate(), requested.leagueId(),
                    requested.seasonId(), requested.rootSeed(), requested.seedAlgorithmId(),
                    requested.frozenSnapshotHash(), requested.productDecisionHash(),
                    requested.referenceCatalogVersion(), requested.referenceCatalogHash(),
                    requested.bindingSchema(), requested.bindingHash(),
                    requested.careerSchema(), requested.lifecycleStatus(),
                    requested.revision(), now, now);
            initializer.accept(requested);
            jdbc.update("""
                    INSERT INTO career_create_command(
                      client_command_id, command_schema, payload_hash,
                      career_id, completed_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, commandId, CareerIdentity.COMMAND_SCHEMA, payloadHash,
                    requested.careerId(), now);
            return new CreateResult(false, find(requested.careerId()).orElseThrow());
        });
    }

    public Optional<CareerRow> find(String careerId) {
        return jdbc.query("""
                SELECT career_id, save_name, manager_name, managed_team_code,
                       start_game_date, current_game_date, league_id, season_id,
                       career_root_seed, seed_algorithm_id,
                       league_frozen_snapshot_hash, league_product_decision_hash,
                       reference_catalog_version, reference_catalog_hash,
                       career_binding_schema, career_binding_hash,
                       career_schema, lifecycle_status, revision, created_at, updated_at
                FROM career_save WHERE career_id = ?
                """, (result, row) -> career(result), careerId).stream().findFirst();
    }

    public List<CareerRow> list() {
        List<CareerRow> rows = jdbc.query("""
                SELECT career_id, save_name, manager_name, managed_team_code,
                       start_game_date, current_game_date, league_id, season_id,
                       career_root_seed, seed_algorithm_id,
                       league_frozen_snapshot_hash, league_product_decision_hash,
                       reference_catalog_version, reference_catalog_hash,
                       career_binding_schema, career_binding_hash,
                       career_schema, lifecycle_status, revision, created_at, updated_at
                FROM career_save ORDER BY updated_at DESC, career_id
                LIMIT ?
                """, (result, row) -> career(result), maximumCareers + 1);
        if (rows.size() > maximumCareers) throw new CareerListIntegrityFailure();
        return rows;
    }

    public int maximumCareers() { return maximumCareers; }

    private Optional<CommandRow> findCommand(String commandId) {
        return jdbc.query("""
                SELECT client_command_id, command_schema, payload_hash, career_id
                FROM career_create_command
                WHERE client_command_id = ?
                """, (result, row) -> new CommandRow(
                result.getString(1), result.getString(2), result.getString(3),
                result.getString(4)), commandId)
                .stream().findFirst();
    }

    private void lockCreateCommands() {
        List<String> rows = jdbc.query("""
                SELECT lock_name FROM career_operation_lock
                WHERE lock_name = 'CREATE_COMMANDS' FOR UPDATE
                """, (result, row) -> result.getString(1));
        if (rows.size() != 1) {
            throw new IllegalStateException("CAREER_CREATE_LOCK_MISSING");
        }
    }

    private OffsetDateTime now() {
        return clock.instant().atOffset(ZoneOffset.UTC);
    }

    private static CareerRow career(ResultSet result) throws SQLException {
        return new CareerRow(result.getString(1), result.getString(2), result.getString(3),
                result.getString(4), result.getObject(5, LocalDate.class),
                result.getObject(6, LocalDate.class), result.getString(7),
                result.getString(8), result.getLong(9), result.getString(10),
                result.getString(11), result.getString(12), result.getString(13),
                result.getString(14), result.getString(15), result.getString(16),
                result.getString(17), result.getString(18), result.getLong(19),
                result.getObject(20, OffsetDateTime.class),
                result.getObject(21, OffsetDateTime.class));
    }

    public record NewCareer(
            String careerId,
            String saveName,
            String managerName,
            String managedTeamCode,
            LocalDate startDate,
            LocalDate currentDate,
            String leagueId,
            String seasonId,
            long rootSeed,
            String seedAlgorithmId,
            String frozenSnapshotHash,
            String productDecisionHash,
            String referenceCatalogVersion,
            String referenceCatalogHash,
            String bindingSchema,
            String bindingHash,
            String careerSchema,
            String lifecycleStatus,
            long revision
    ) {}

    public record CareerRow(
            String careerId,
            String saveName,
            String managerName,
            String managedTeamCode,
            LocalDate startDate,
            LocalDate currentDate,
            String leagueId,
            String seasonId,
            long rootSeed,
            String seedAlgorithmId,
            String frozenSnapshotHash,
            String productDecisionHash,
            String referenceCatalogVersion,
            String referenceCatalogHash,
            String bindingSchema,
            String bindingHash,
            String careerSchema,
            String lifecycleStatus,
            long revision,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record CreateResult(boolean replayed, CareerRow career) {}

    private record CommandRow(
            String clientCommandId,
            String commandSchema,
            String payloadHash,
            String careerId
    ) {}

    public static final class CommandConflict extends RuntimeException {}
    public static final class CapacityReached extends RuntimeException {}
    public static final class CommandReceiptIntegrityFailure extends RuntimeException {}
    public static final class CareerListIntegrityFailure extends RuntimeException {}
}
