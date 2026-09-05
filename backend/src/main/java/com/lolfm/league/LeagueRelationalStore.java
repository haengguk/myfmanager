package com.lolfm.league;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Relational authority for frozen Seasons, compact receipts, standings and outbox. */
@Component
public final class LeagueRelationalStore {
    public static final String OUTBOX_SCHEMA = "AI_LEAGUE_FIXTURE_COMPLETED_OUTBOX_V1";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final TransactionTemplate repeatableReads;
    private final LeagueJsonCodec json;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public LeagueRelationalStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            LeagueJsonCodec json
    ) {
        this(jdbc, transactionManager, json, Clock.systemUTC());
    }

    LeagueRelationalStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            LeagueJsonCodec json,
            Clock clock
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        PlatformTransactionManager requiredTransactionManager = Objects.requireNonNull(
                transactionManager, "transactionManager");
        this.transactions = new TransactionTemplate(requiredTransactionManager);
        this.repeatableReads = new TransactionTemplate(requiredTransactionManager);
        this.repeatableReads.setIsolationLevel(
                TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.repeatableReads.setReadOnly(true);
        this.json = Objects.requireNonNull(json, "json");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Empty-schema creation is all-or-nothing and idempotent for the same frozen input. */
    public LeagueSeasonAggregate freeze(LeagueSeasonAggregate season) {
        Objects.requireNonNull(season, "season");
        return transactions.execute(ignored -> {
            Optional<LeagueSeasonAggregate> existing = findSeason(season.seasonId());
            if (existing.isPresent()) {
                requireSameFrozenSeason(existing.get(), season);
                return existing.get();
            }
            OffsetDateTime now = now();
            String snapshotJson = json.write(season.frozenSnapshot());
            Integer leagueCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM league_registry WHERE league_id = ?",
                    Integer.class, season.leagueId());
            if (leagueCount == null || leagueCount == 0) {
                jdbc.update("""
                        INSERT INTO league_registry(
                          league_id, lifecycle_status, revision, created_at, updated_at)
                        VALUES (?, 'ACTIVE', 0, ?, ?)
                        """, season.leagueId(), now, now);
            }
            jdbc.update("""
                    INSERT INTO league_season(
                      season_id, league_id, lifecycle_status, lifecycle_revision,
                      revision, season_mode,
                      managed_team_code, managed_team_snapshot_hash, season_root_seed,
                      product_decision_hash, schedule_identity, frozen_snapshot_json,
                      frozen_snapshot_hash, created_at, updated_at)
                    VALUES (?, ?, ?, 0, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, season.seasonId(), season.leagueId(),
                    LeaguePersistenceState.SeasonStatus.FROZEN.name(),
                    season.seasonMode().name(), season.managedTeamCode(),
                    season.managedTeamSnapshotIdentity(), season.seasonRootSeed(),
                    season.productDecisionHash(), season.schedule().scheduleIdentity(),
                    snapshotJson, season.frozenSnapshot().snapshotIdentity(), now, now);
            for (LeagueRound round : season.schedule().rounds()) {
                jdbc.update("""
                        INSERT INTO league_round(
                          season_id, round_number, lifecycle_status, revision)
                        VALUES (?, ?, 'READY', 0)
                        """, season.seasonId(), round.roundNumber());
                for (LeagueFixture fixture : round.fixtures()) {
                    LeaguePersistenceState.FixtureStatus status =
                            fixture.executionMode() == LeagueFixtureExecutionMode.FULL_AUTO
                                    ? LeaguePersistenceState.FixtureStatus.SCHEDULED
                                    : LeaguePersistenceState.FixtureStatus.AWAITING_PLAYER;
                    jdbc.update("""
                            INSERT INTO league_fixture(
                              season_id, fixture_id, round_number, execution_mode,
                              lifecycle_status, revision, bound_series_id,
                              first_team_code, second_team_code, game1_blue_team_code,
                              game1_red_team_code, series_format, fixture_root_seed,
                              seed_anchor_team_code)
                            VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?)
                            """, season.seasonId(), fixture.fixtureId(),
                            fixture.roundNumber(), fixture.executionMode().name(),
                            status.name(), fixture.boundSeriesId(), fixture.firstTeamCode(),
                            fixture.secondTeamCode(), fixture.game1BlueTeamCode(),
                            fixture.game1RedTeamCode(), fixture.seriesFormat().name(),
                            fixture.fixtureRootSeed(), fixture.seedAnchorTeamCode());
                }
            }
            season.standings().rows().values().forEach(row -> insertStanding(
                    season.seasonId(), row));
            return season;
        });
    }

    public LeagueSeasonAggregate loadSeason(String seasonId) {
        return findSeason(seasonId).orElseThrow(() ->
                new IllegalStateException("LEAGUE_SEASON_NOT_FOUND"));
    }

    public Optional<LeagueSeasonAggregate> findSeason(String seasonId) {
        return readConsistently(() -> findSeasonSnapshot(seasonId));
    }

    /** Keep a public projection and its nested season rebuild on the same committed snapshot. */
    <T> T readConsistently(java.util.function.Supplier<T> projection) {
        return repeatableReads.execute(ignored -> projection.get());
    }

    /**
     * Rebuilds a season from one database snapshot. The season revision, applied
     * receipts and materialized standings must never straddle an outbox commit.
     */
    private Optional<LeagueSeasonAggregate> findSeasonSnapshot(String seasonId) {
        List<SeasonRow> values = jdbc.query("""
                SELECT league_id, season_id, revision, season_mode, managed_team_code,
                       managed_team_snapshot_hash, season_root_seed, product_decision_hash,
                       schedule_identity, frozen_snapshot_json, frozen_snapshot_hash
                FROM league_season WHERE season_id = ?
                """, (result, row) -> seasonRow(result), seasonId);
        if (values.isEmpty()) return Optional.empty();
        if (values.size() != 1) throw new IllegalStateException("DUPLICATE_LEAGUE_SEASON");
        SeasonRow row = values.getFirst();
        LeagueSeasonFrozenSnapshot snapshot = json.read(
                row.snapshotJson(), LeagueSeasonFrozenSnapshot.class);
        if (!snapshot.snapshotIdentity().equals(row.snapshotHash())) {
            throw new IllegalStateException("DURABLE_SEASON_SNAPSHOT_HASH_MISMATCH");
        }
        LeagueSeasonAggregate season = LeagueSeasonAggregate.create(
                row.leagueId(), row.seasonId(), LeagueSeasonMode.valueOf(row.seasonMode()),
                row.managedTeamCode(), row.managedTeamSnapshotHash(), snapshot,
                row.seasonRootSeed(), LeagueSchedulePolicy.productionDefault());
        if (!season.productDecisionHash().equals(row.productDecisionHash())
                || !season.schedule().scheduleIdentity().equals(row.scheduleIdentity())) {
            throw new IllegalStateException("DURABLE_SEASON_IDENTITY_MISMATCH");
        }
        List<LeagueFixtureCompletionReceiptV2> applied = jdbc.query("""
                SELECT r.receipt_json
                FROM league_standings_application a
                JOIN league_completion_receipt r ON r.receipt_hash = a.receipt_hash
                WHERE a.season_id = ? ORDER BY a.applied_season_revision
                """, (result, ignored) -> json.read(result.getString(1),
                LeagueFixtureCompletionReceiptV2.class), seasonId);
        for (LeagueFixtureCompletionReceiptV2 receipt : applied) {
            LeagueFixtureSeriesBindingV1 binding = receipt.playerSeriesBindingHash() == null
                    ? null : loadBindingCanonical(receipt.playerSeriesBindingHash());
            season = season.applyVerifiedCompletion(
                    VerifiedLeagueFixtureCompletion.verifyPersisted(
                            season, receipt, binding));
        }
        if (season.revision() != row.revision()) {
            throw new IllegalStateException("DURABLE_SEASON_REVISION_MISMATCH");
        }
        verifyStandingsRows(season);
        return Optional.of(season);
    }

    public void storeVerifiedCompletion(
            LeagueFixtureCompletionReceiptV2 receipt,
            VerifiedLeagueFixtureCompletion verification
    ) {
        Objects.requireNonNull(verification, "verification");
        if (!receipt.canonicalFixtureReceiptHash().equals(
                verification.canonicalFixtureReceiptHash())) {
            throw new IllegalArgumentException("VERIFIED_RECEIPT_TOKEN_MISMATCH");
        }
        storeReceiptAndOutbox(receipt);
    }

    /** Internal binding adapter entry; caller owns cryptographic verification. */
    void storeReceiptAndOutbox(LeagueFixtureCompletionReceiptV2 receipt) {
        Objects.requireNonNull(receipt, "receipt");
        transactions.executeWithoutResult(ignored -> {
            String hash = receipt.canonicalFixtureReceiptHash();
            List<String> prior = jdbc.query(
                    "SELECT receipt_canonical FROM league_completion_receipt WHERE receipt_hash = ?",
                    (result, row) -> result.getString(1), hash);
            if (!prior.isEmpty()) {
                if (!prior.getFirst().equals(receipt.canonicalText())) {
                    throw new IllegalStateException("LEAGUE_RECEIPT_HASH_CONFLICT");
                }
                return;
            }
            OffsetDateTime now = now();
            try {
                jdbc.update("""
                        INSERT INTO league_completion_receipt(
                          receipt_hash, season_id, fixture_id, execution_mode,
                          player_binding_hash, receipt_schema, receipt_canonical,
                          receipt_json, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, hash, receipt.seasonId(), receipt.fixtureId(),
                        receipt.executionMode().name(), receipt.playerSeriesBindingHash(),
                        receipt.schemaVersion(), receipt.canonicalText(), json.write(receipt), now);
            } catch (DuplicateKeyException duplicate) {
                throw new IllegalStateException("FIXTURE_ALREADY_HAS_DIFFERENT_RECEIPT",
                        duplicate);
            }
            String eventId = "outbox_" + LeagueIdentity.sha256(
                    "outboxSchema=" + OUTBOX_SCHEMA + '\n'
                            + "receiptHash=" + hash + '\n');
            jdbc.update("""
                    INSERT INTO league_outbox(
                      event_id, event_schema, season_id, fixture_id, execution_mode,
                      player_binding_hash, receipt_hash, lifecycle_status,
                      delivery_attempts, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?)
                    """, eventId, OUTBOX_SCHEMA, receipt.seasonId(), receipt.fixtureId(),
                    receipt.executionMode().name(), receipt.playerSeriesBindingHash(), hash, now);
            jdbc.update("""
                    UPDATE league_fixture
                    SET lifecycle_status = 'COMPLETION_PENDING_VERIFICATION',
                        revision = revision + 1, completion_receipt_hash = ?
                    WHERE season_id = ? AND fixture_id = ?
                      AND completion_receipt_hash IS NULL
                    """, hash, receipt.seasonId(), receipt.fixtureId());
        });
    }

    /** Applies at most one pending event in one transaction. */
    public boolean deliverNextOutbox() {
        Boolean delivered = transactions.execute(ignored -> {
            List<OutboxRow> rows = jdbc.query("""
                    SELECT event_id, season_id, fixture_id, receipt_hash
                    FROM league_outbox
                    WHERE lifecycle_status = 'PENDING'
                    ORDER BY created_at, event_id LIMIT 1 FOR UPDATE
                    """, (result, row) -> new OutboxRow(result.getString(1),
                    result.getString(2), result.getString(3), result.getString(4)));
            if (rows.isEmpty()) return false;
            OutboxRow outbox = rows.getFirst();
            int already = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM league_standings_application
                    WHERE receipt_hash = ?
                    """, Integer.class, outbox.receiptHash());
            if (already > 0) {
                markOutboxDelivered(outbox.eventId());
                return true;
            }
            LeagueSeasonAggregate current = loadSeason(outbox.seasonId());
            LeagueFixtureCompletionReceiptV2 receipt = loadReceipt(outbox.receiptHash());
            LeagueFixtureSeriesBindingV1 binding = receipt.playerSeriesBindingHash() == null
                    ? null : loadBindingCanonical(receipt.playerSeriesBindingHash());
            verifyDurableProducer(receipt);
            VerifiedLeagueFixtureCompletion verified =
                    VerifiedLeagueFixtureCompletion.verifyPersisted(
                            current, receipt, binding);
            LeagueSeasonAggregate next = current.applyVerifiedCompletion(verified);
            OffsetDateTime now = now();
            jdbc.update("""
                    INSERT INTO league_standings_application(
                      season_id, fixture_id, receipt_hash,
                      applied_season_revision, applied_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, outbox.seasonId(), outbox.fixtureId(), outbox.receiptHash(),
                    next.revision(), now);
            next.standings().rows().values().forEach(row -> jdbc.update("""
                    UPDATE league_standing SET series_wins = ?, series_losses = ?,
                      game_wins = ?, game_losses = ?
                    WHERE season_id = ? AND team_code = ?
                    """, row.seriesWins(), row.seriesLosses(), row.gameWins(),
                    row.gameLosses(), next.seasonId(), row.teamCode()));
            jdbc.update("""
                    UPDATE league_fixture SET lifecycle_status = 'COMPLETED',
                      revision = revision + 1 WHERE season_id = ? AND fixture_id = ?
                    """, outbox.seasonId(), outbox.fixtureId());
            Integer completedRound = jdbc.queryForObject("""
                    SELECT round_number FROM league_fixture
                    WHERE season_id = ? AND fixture_id = ?
                    """, Integer.class, outbox.seasonId(), outbox.fixtureId());
            jdbc.update("""
                    UPDATE league_round SET lifecycle_status = 'COMPLETED',
                      revision = revision + 1
                    WHERE season_id = ? AND round_number = ?
                      AND NOT EXISTS (SELECT 1 FROM league_fixture f
                        WHERE f.season_id = ?
                          AND f.round_number = ?
                          AND f.lifecycle_status <> 'COMPLETED')
                    """, outbox.seasonId(), completedRound, outbox.seasonId(),
                    completedRound);
            String lifecycle = lifecycleAfterCompletion(outbox.seasonId());
            int seasonUpdated = jdbc.update("""
                    UPDATE league_season SET revision = ?, lifecycle_status = ?,
                      lifecycle_revision = lifecycle_revision + 1, updated_at = ?
                    WHERE season_id = ? AND revision = ?
                    """, next.revision(), lifecycle, now, next.seasonId(),
                    current.revision());
            if (seasonUpdated != 1) {
                throw new IllegalStateException("DURABLE_SEASON_STANDINGS_CAS_FAILED");
            }
            markOutboxDelivered(outbox.eventId());
            return true;
        });
        return Boolean.TRUE.equals(delivered);
    }

    public int drainOutbox(int limit) {
        if (limit < 1 || limit > 10_000) throw new IllegalArgumentException("limit");
        int count = 0;
        while (count < limit && deliverNextOutbox()) count++;
        return count;
    }

    public int pendingOutboxCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM league_outbox WHERE lifecycle_status = 'PENDING'",
                Integer.class);
    }

    void registerProcessIncarnation(String incarnationId) {
        OffsetDateTime current = now();
        int updated = jdbc.update("""
                UPDATE league_process_incarnation
                SET lifecycle_status = 'ACTIVE', last_seen_at = ?
                WHERE incarnation_id = ?
                """, current, incarnationId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO league_process_incarnation(
                      incarnation_id, lifecycle_status, started_at, last_seen_at)
                    VALUES (?, 'ACTIVE', ?, ?)
                    """, incarnationId, current, current);
        }
    }

    /** Serializes global active-count inspection and lease/cancel state mutation in the DB. */
    void lockGlobalFixtureLeases() {
        lockOperation("GLOBAL_FIXTURE_LEASES");
    }

    void lockApiCommands() {
        lockOperation("API_COMMANDS");
    }

    private void lockOperation(String lockName) {
        List<String> rows = jdbc.query("""
                SELECT lock_name FROM league_job_scheduler_lock
                WHERE lock_name = ? FOR UPDATE
                """, (result, row) -> result.getString(1), lockName);
        if (rows.size() != 1) {
            throw new IllegalStateException("LEAGUE_OPERATION_LOCK_MISSING");
        }
    }

    public LeagueFixtureCompletionReceiptV2 loadReceipt(String receiptHash) {
        return jdbc.queryForObject("""
                SELECT receipt_json FROM league_completion_receipt WHERE receipt_hash = ?
                """, (result, row) -> json.read(result.getString(1),
                LeagueFixtureCompletionReceiptV2.class), receiptHash);
    }

    LeagueFixtureSeriesBindingV1 loadBindingCanonical(String bindingHash) {
        return jdbc.queryForObject("""
                SELECT binding_canonical FROM league_player_binding WHERE binding_hash = ?
                """, (result, row) -> LeagueFixtureSeriesBindingV1.restoreCanonical(
                result.getString(1)), bindingHash);
    }

    JdbcTemplate jdbc() { return jdbc; }
    TransactionTemplate transactions() { return transactions; }
    OffsetDateTime now() { return clock.instant().atOffset(ZoneOffset.UTC); }

    private void markOutboxDelivered(String eventId) {
        jdbc.update("""
                UPDATE league_outbox SET lifecycle_status = 'DELIVERED',
                  delivery_attempts = delivery_attempts + 1, delivered_at = ?
                WHERE event_id = ?
                """, now(), eventId);
    }

    private String lifecycleAfterCompletion(String seasonId) {
        String current = jdbc.queryForObject("""
                SELECT lifecycle_status FROM league_season WHERE season_id = ?
                """, String.class, seasonId);
        if ("PAUSED".equals(current) || "CANCELLED".equals(current)
                || "BLOCKED".equals(current)) {
            return current;
        }
        Integer remaining = jdbc.queryForObject("""
                SELECT COUNT(*) FROM league_fixture
                WHERE season_id = ? AND lifecycle_status <> 'COMPLETED'
                """, Integer.class, seasonId);
        if (remaining == null || remaining == 0) return "COMPLETED";
        Integer autoRemaining = jdbc.queryForObject("""
                SELECT COUNT(*) FROM league_fixture
                WHERE season_id = ?
                  AND round_number = (SELECT MIN(round_number) FROM league_fixture
                    WHERE season_id = ? AND lifecycle_status <> 'COMPLETED')
                  AND execution_mode = 'FULL_AUTO'
                  AND lifecycle_status <> 'COMPLETED'
                """, Integer.class, seasonId, seasonId);
        Integer playerRemaining = jdbc.queryForObject("""
                SELECT COUNT(*) FROM league_fixture
                WHERE season_id = ?
                  AND round_number = (SELECT MIN(round_number) FROM league_fixture
                    WHERE season_id = ? AND lifecycle_status <> 'COMPLETED')
                  AND execution_mode = 'PLAYER_CONTROLLED'
                  AND lifecycle_status <> 'COMPLETED'
                """, Integer.class, seasonId, seasonId);
        return autoRemaining != null && autoRemaining == 0
                && playerRemaining != null && playerRemaining > 0
                ? "WAITING_FOR_PLAYER" : "RUNNING";
    }

    private void verifyDurableProducer(LeagueFixtureCompletionReceiptV2 receipt) {
        int producerCount;
        if (receipt.executionMode() == LeagueFixtureExecutionMode.FULL_AUTO) {
            producerCount = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM league_job
                    WHERE season_id = ? AND fixture_id = ?
                      AND lifecycle_status = 'COMPLETED'
                    """, Integer.class, receipt.seasonId(), receipt.fixtureId());
        } else {
            producerCount = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM league_player_binding
                    WHERE season_id = ? AND fixture_id = ? AND binding_hash = ?
                      AND lifecycle_status = 'VERIFIED'
                      AND completion_receipt_hash = ?
                    """, Integer.class, receipt.seasonId(), receipt.fixtureId(),
                    receipt.playerSeriesBindingHash(),
                    receipt.canonicalFixtureReceiptHash());
        }
        if (producerCount != 1) {
            throw new IllegalStateException("DURABLE_COMPLETION_PRODUCER_PROOF_MISMATCH");
        }
    }

    private void insertStanding(String seasonId, LeagueStanding row) {
        jdbc.update("""
                INSERT INTO league_standing(
                  season_id, team_code, series_wins, series_losses,
                  game_wins, game_losses) VALUES (?, ?, ?, ?, ?, ?)
                """, seasonId, row.teamCode(), row.seriesWins(), row.seriesLosses(),
                row.gameWins(), row.gameLosses());
    }

    private void verifyStandingsRows(LeagueSeasonAggregate season) {
        List<LeagueStanding> rows = jdbc.query("""
                SELECT team_code, series_wins, series_losses, game_wins, game_losses
                FROM league_standing WHERE season_id = ? ORDER BY team_code
                """, (result, ignored) -> new LeagueStanding(result.getString(1),
                result.getInt(2), result.getInt(3), result.getInt(4), result.getInt(5)),
                season.seasonId());
        if (!season.standings().rows().equals(rows.stream().collect(
                java.util.stream.Collectors.toMap(
                        LeagueStanding::teamCode, value -> value)))) {
            throw new IllegalStateException("DURABLE_STANDINGS_MISMATCH");
        }
    }

    private static SeasonRow seasonRow(ResultSet result) throws SQLException {
        return new SeasonRow(result.getString(1), result.getString(2), result.getLong(3),
                result.getString(4), result.getString(5), result.getString(6),
                result.getLong(7), result.getString(8), result.getString(9),
                result.getString(10), result.getString(11));
    }

    private static void requireSameFrozenSeason(
            LeagueSeasonAggregate existing, LeagueSeasonAggregate requested
    ) {
        if (!existing.leagueId().equals(requested.leagueId())
                || existing.seasonRootSeed() != requested.seasonRootSeed()
                || existing.seasonMode() != requested.seasonMode()
                || !Objects.equals(existing.managedTeamCode(), requested.managedTeamCode())
                || !existing.frozenSnapshot().equals(requested.frozenSnapshot())
                || !existing.schedule().scheduleIdentity().equals(
                requested.schedule().scheduleIdentity())) {
            throw new IllegalStateException("SEASON_ID_FROZEN_INPUT_CONFLICT");
        }
    }

    private record SeasonRow(
            String leagueId, String seasonId, long revision, String seasonMode,
            String managedTeamCode, String managedTeamSnapshotHash, long seasonRootSeed,
            String productDecisionHash, String scheduleIdentity, String snapshotJson,
            String snapshotHash
    ) {}

    private record OutboxRow(
            String eventId, String seasonId, String fixtureId, String receiptHash
    ) {}
}
