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
    private CareerInternationalParticipants initialParticipants;

    @Autowired
    public CareerRelationalStore(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, CareerInternationalParticipants participants, CareerRosterStore rosters) {
        this(jdbc,transactionManager,participants);this.rosters=rosters;
    }
    @Autowired(required=false) private CareerClStore cl;
    private CareerRosterStore rosters;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private CareerOverseasStore overseas;
    public CareerRelationalStore(JdbcTemplate jdbc, PlatformTransactionManager manager, CareerInternationalParticipants participants) {
        this(jdbc,manager);this.initialParticipants=participants;
    }
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
        return createOrReplay(commandId, payloadHash, creator, ignored -> {}, false);
    }

    /** Creator, Career insert, initializer, and command receipt share one transaction. */
    public CreateResult createOrReplay(
            String commandId,
            String payloadHash,
            Supplier<NewCareer> creator,
            Consumer<NewCareer> initializer
    ) {
        return createOrReplay(commandId,payloadHash,creator,initializer,true);
    }
    private CreateResult createOrReplay(String commandId,String payloadHash,Supplier<NewCareer> creator,
                                       Consumer<NewCareer> initializer,boolean initializeSeason) {
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
            if (initializeSeason) {
            jdbc.update("""
                INSERT INTO career_season(career_id, season_year, season_ordinal, league_id, season_id,
                    season_root_seed, frozen_snapshot_hash, product_decision_hash, lifecycle_status)
                SELECT s.career_id, c.active_calendar_season_year, 1, s.league_id, s.season_id,
                    s.career_root_seed, s.league_frozen_snapshot_hash, s.league_product_decision_hash, 'ACTIVE'
                FROM career_save s JOIN career_calendar_state c ON c.career_id = s.career_id WHERE s.career_id = ?
                """, requested.careerId());
            if (initialParticipants != null) {
                int year = jdbc.queryForObject("SELECT season_year FROM career_season WHERE career_id = ?",Integer.class,requested.careerId());
                var rosters = new java.util.LinkedHashMap<String,CompetitionRosterSnapshot.Roster>();
                initialParticipants.overseas(requested.careerId(),year,"FIRST_STAND").rankings().values().stream().flatMap(List::stream)
                        .forEach(r -> rosters.put(CompetitionRosterSnapshot.token(r.team()),r));
                jdbc.query("SELECT team_code FROM league_standing WHERE season_id = ? ORDER BY team_code",(r,n) -> r.getString(1),requested.seasonId())
                        .forEach(t -> {var r=initialParticipants.roster(new com.lolfm.player.GlobalTeamRosterCatalog.TeamKey("LCK",t));rosters.put(CompetitionRosterSnapshot.token(r.team()),r);});
                var snapshot = new CompetitionRosterSnapshot(rosters);
                jdbc.update("UPDATE career_season SET roster_json = ?, roster_hash = ? WHERE career_id = ? AND season_year = ?",snapshot.encoded(),snapshot.identity(),requested.careerId(),year);
            }
            }
            if (rosters != null && initializeSeason) {
                int year=jdbc.queryForObject("SELECT active_calendar_season_year FROM career_calendar_state WHERE career_id=?",Integer.class,requested.careerId());
                rosters.initializeNew(requested.careerId(),year);
                CareerMarketStore.initializeNew(jdbc,requested.careerId());
                CareerDevelopmentStore.initialize(jdbc,requested.careerId());
                CareerLifecycleStore.initialize(jdbc,requested.careerId());
                if(cl!=null)cl.initializeNew(requested.careerId(),year);
                if(overseas!=null)overseas.initializeNew(requested.careerId(),year);
            }
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

    public SeasonRow activeSeason(CareerRow career) {
        var rows = jdbc.query("""
            SELECT s.* FROM career_season s JOIN career_calendar_state c ON c.career_id = s.career_id
              AND c.active_calendar_season_year = s.season_year WHERE s.career_id = ?
            """, (r,n) -> new SeasonRow(r.getInt("season_year"),r.getInt("season_ordinal"),r.getString("league_id"),
                r.getString("season_id"),r.getLong("season_root_seed"),r.getString("frozen_snapshot_hash"),r.getString("product_decision_hash")),career.careerId());
        if (rows.size() != 1) throw new IllegalStateException("CAREER_ACTIVE_SEASON_INTEGRITY");
        var active=rows.getFirst();
        String expectedLeague=career.leagueId(),expectedSeason=career.seasonId();long expectedSeed=career.rootSeed();
        if(active.ordinal()>1) {
            String identity=CareerInternationalRules.hash(career.careerId()+"|SEASON_ROLLOVER_V1|"+active.year()+"|"+active.ordinal());
            expectedLeague="league_"+identity;expectedSeason="season_"+identity;
            expectedSeed=CareerCompetitionAggregate.deriveSeed(career.rootSeed(),active.year(),"CAREER_SEASON","ORDINAL:"+active.ordinal());
        }
        if(!active.leagueId().equals(expectedLeague)||!active.seasonId().equals(expectedSeason)||active.rootSeed()!=expectedSeed
                ||!active.frozenSnapshotHash().equals(career.frozenSnapshotHash())||!active.productDecisionHash().equals(career.productDecisionHash()))
            throw new IllegalStateException("CAREER_ACTIVE_SEASON_BINDING_INTEGRITY");
        return active;
    }
    public record SeasonRow(int year, int ordinal, String leagueId, String seasonId, long rootSeed,
                            String frozenSnapshotHash, String productDecisionHash) {}

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
