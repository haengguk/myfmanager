package com.lolfm.career;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Transactional Career competition graph, sealed inputs and receipt ledger. */
@Component
public final class CareerCompetitionRelationalStore {
    public static final String CYCLE_SCHEMA = "CAREER_COMPETITION_CYCLE_V1";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final CareerCompetitionRules rules;

    public CareerCompetitionRelationalStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            Clock clock,
            CareerCompetitionRules rules
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager"));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    public CycleView initialize(
            CareerRelationalStore.NewCareer career,
            int calendarSeasonYear
    ) {
        return initialize(career.careerId(), calendarSeasonYear);
    }

    public CycleView initialize(String careerId, int calendarSeasonYear) {
        return transactions.execute(ignored -> {
            List<CycleRow> prior = findCycle(careerId, calendarSeasonYear, true);
            if (!prior.isEmpty()) return validateAndView(prior.getFirst());
            OffsetDateTime now = now();
            String initialHash = cycleHash(careerId, calendarSeasonYear,
                    0, null, null);
            jdbc.update("""
                    INSERT INTO career_competition_cycle(
                      career_id, calendar_season_year, cycle_schema, rule_version,
                      rule_resource_hash, game_policy_version, projection_policy,
                      r3_r4_allocation_policy, lifecycle_status, blocking_reason,
                      r1_r2_import_hash, r1_r2_standings_revision, revision,
                      state_hash, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', NULL, NULL, NULL,
                      0, ?, ?, ?)
                    """, careerId, calendarSeasonYear, CYCLE_SCHEMA,
                    CareerCompetitionRules.VERSION, rules.resourceHash(),
                    CareerCompetitionRules.GAME_POLICY_VERSION,
                    CareerCompetitionRules.PROJECTION_POLICY,
                    CareerCompetitionRules.R3_R4_ALLOCATION_POLICY,
                    initialHash, now, now);
            for (CareerCompetitionRules.CompetitionRule rule : rules.competitions()) {
                String lifecycle = initialLifecycle(rule.competitionId());
                String blocker = initialBlocker(rule);
                String stateHash = instanceHash(careerId, calendarSeasonYear,
                        rule.competitionId(), lifecycle, blocker, null, 0);
                jdbc.update("""
                        INSERT INTO career_competition_instance(
                          career_id, calendar_season_year, competition_id, rule_status,
                          lifecycle_status, blocking_reason, source_input_hash, revision,
                          state_hash, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, NULL, 0, ?, ?, ?)
                        """, careerId, calendarSeasonYear,
                        rule.competitionId(), rule.ruleStatus(), lifecycle, blocker,
                        stateHash, now, now);
            }
            return validateAndView(findCycle(careerId, calendarSeasonYear,
                    false).getFirst());
        });
    }

    public CycleView load(String careerId, int seasonYear) {
        List<CycleRow> rows = findCycle(careerId, seasonYear, false);
        if (rows.size() != 1) throw new IllegalStateException(
                "CAREER_COMPETITION_CYCLE_NOT_FOUND");
        return validateAndView(rows.getFirst());
    }

    public SealResult sealR1R2(
            String careerId,
            int seasonYear,
            String managedTeamCode,
            long careerRootSeed,
            String scheduleIdentity,
            long standingsRevision,
            List<CareerCompetitionAggregate.SeededTeam> ranking
    ) {
        String importHash = importHash(careerId, seasonYear, scheduleIdentity,
                standingsRevision, ranking);
        return transactions.execute(ignored -> {
            CycleRow cycle = lockCycle(careerId, seasonYear);
            validateCycle(cycle);
            if (cycle.r1r2ImportHash() != null) {
                if (!cycle.r1r2ImportHash().equals(importHash)
                        || !Objects.equals(cycle.r1r2StandingsRevision(),
                        standingsRevision)) {
                    throw new IllegalStateException("R1_R2_SEALED_INPUT_CONFLICT");
                }
                return new SealResult(load(careerId, seasonYear), true, importHash);
            }
            if (ranking.size() != 10) throw new IllegalArgumentException(
                    "R1_R2_FINAL_RANKING_REQUIRES_TEN_TEAMS");
            insertSeeds(careerId, seasonYear, "LCK_ROAD_TO_MSI",
                    "R1_R2_FINAL_RANK", importHash, ranking);
            insertSeeds(careerId, seasonYear, "LCK_REGULAR_R3_R4",
                    "R1_R2_FINAL_RANK", importHash, ranking);
            CareerCompetitionAggregate road = CareerCompetitionAggregate.materialize(
                    rules, careerId, seasonYear, "LCK_ROAD_TO_MSI", managedTeamCode,
                    careerRootSeed, importHash, ranking);
            road.fixtures().forEach(value -> insertFixture(careerId, seasonYear,
                    "LCK_ROAD_TO_MSI", value, "OFFICIAL_PROJECTED_DATE"));
            CareerCompetitionAggregate.R3R4Stage r3r4 =
                    CareerCompetitionAggregate.materializeR3R4(careerId, seasonYear,
                            managedTeamCode, careerRootSeed, importHash, ranking);
            r3r4.fixtures().forEach(value -> insertR3R4Fixture(careerId,
                    seasonYear, value));
            OffsetDateTime now = now();
            updateInstance(careerId, seasonYear, "LCK_REGULAR_R1_R2",
                    "COMPLETED", null, importHash, standingsRevision,
                    instanceHash(careerId, seasonYear, "LCK_REGULAR_R1_R2",
                            "COMPLETED", null, importHash, standingsRevision), now);
            updateInstance(careerId, seasonYear, "LCK_ROAD_TO_MSI", "READY",
                    null, importHash, road.revision(), road.stateHash(), now);
            updateInstance(careerId, seasonYear, "LCK_REGULAR_R3_R4", "READY",
                    null, importHash, 0, r3r4.stateHash(), now);
            long nextRevision = cycle.revision() + 1;
            String nextHash = cycleHash(careerId, seasonYear, nextRevision,
                    importHash, standingsRevision);
            int updated = jdbc.update("""
                    UPDATE career_competition_cycle
                    SET r1_r2_import_hash = ?, r1_r2_standings_revision = ?,
                      revision = ?, state_hash = ?, updated_at = ?
                    WHERE career_id = ? AND calendar_season_year = ? AND revision = ?
                    """, importHash, standingsRevision, nextRevision, nextHash, now,
                    careerId, seasonYear, cycle.revision());
            if (updated != 1) throw new IllegalStateException(
                    "CAREER_COMPETITION_CYCLE_CAS_FAILED");
            return new SealResult(load(careerId, seasonYear), false, importHash);
        });
    }

    public CompletionResult applyCompletion(
            String careerId,
            int seasonYear,
            String competitionId,
            String matchId,
            String seriesId,
            String firstTeamCode,
            String secondTeamCode,
            String winnerTeamCode,
            String receiptHash
    ) {
        return transactions.execute(ignored -> {
            CycleRow cycle = lockCycle(careerId, seasonYear);
            List<ApplicationRow> prior = jdbc.query("""
                    SELECT career_id, calendar_season_year, competition_id, match_id,
                           series_id FROM career_competition_application
                    WHERE receipt_hash = ?
                    """, (result, row) -> new ApplicationRow(result.getString(1),
                    result.getInt(2), result.getString(3), result.getString(4),
                    result.getString(5)), receiptHash);
            if (!prior.isEmpty()) {
                ApplicationRow applied = prior.getFirst();
                if (!applied.matches(careerId, seasonYear, competitionId, matchId,
                        seriesId)) {
                    throw new IllegalArgumentException(
                            "COMPETITION_RECEIPT_SCOPE_MISMATCH");
                }
                CareerCompetitionAggregate replay = loadAggregate(careerId, seasonYear,
                        competitionId);
                CareerCompetitionAggregate.Fixture replayFixture = replay.fixtures().stream()
                        .filter(value -> matchId.equals(value.matchId())).findFirst()
                        .orElseThrow();
                if (!receiptHash.equals(replayFixture.receiptHash())
                        || !Objects.equals(firstTeamCode, replayFixture.firstTeamCode())
                        || !Objects.equals(secondTeamCode, replayFixture.secondTeamCode())
                        || !Objects.equals(winnerTeamCode,
                        replayFixture.winnerTeamCode())) {
                    throw new IllegalArgumentException(
                            "COMPETITION_RECEIPT_REPLAY_MISMATCH");
                }
                return new CompletionResult(replay, true);
            }
            CareerCompetitionAggregate current = loadAggregate(careerId, seasonYear,
                    competitionId);
            CareerCompetitionAggregate.CompletionResult applied =
                    current.applyVerifiedCompletion(matchId, seriesId, firstTeamCode,
                            secondTeamCode, winnerTeamCode, receiptHash);
            if (applied.replayed()) return new CompletionResult(current, true);
            CareerCompetitionAggregate next = applied.aggregate();
            for (CareerCompetitionAggregate.Fixture fixture : next.fixtures()) {
                jdbc.update("""
                        UPDATE career_competition_fixture
                        SET first_team_code = ?, second_team_code = ?,
                          execution_mode = ?, lifecycle_status = ?, winner_team_code = ?,
                          loser_team_code = ?, completion_receipt_hash = ?,
                          revision = revision + 1
                        WHERE career_id = ? AND calendar_season_year = ?
                          AND competition_id = ? AND match_id = ?
                        """, fixture.firstTeamCode(), fixture.secondTeamCode(),
                        fixture.executionMode(), fixture.lifecycleStatus(),
                        fixture.winnerTeamCode(), fixture.loserTeamCode(),
                        fixture.receiptHash(), careerId, seasonYear, competitionId,
                        fixture.matchId());
            }
            Map<String, String> priorOutputs = current.qualificationOutputs();
            for (Map.Entry<String, String> output : next.qualificationOutputs().entrySet()) {
                if (priorOutputs.containsKey(output.getKey())) continue;
                jdbc.update("""
                        INSERT INTO career_competition_output(
                          career_id, calendar_season_year, competition_id, output_id,
                          team_code, source_match_id, source_receipt_hash, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, careerId, seasonYear, competitionId, output.getKey(),
                        output.getValue(), matchId, receiptHash, now());
            }
            jdbc.update("""
                    INSERT INTO career_competition_application(
                      receipt_hash, career_id, calendar_season_year, competition_id,
                      match_id, series_id, applied_revision, applied_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, receiptHash, careerId, seasonYear, competitionId, matchId,
                    seriesId, next.revision(), now());
            String status = next.fixtures().stream().allMatch(value ->
                    "COMPLETED".equals(value.lifecycleStatus())) ? "COMPLETED" : "RUNNING";
            updateInstance(careerId, seasonYear, competitionId, status, null,
                    next.sourceInputHash(), next.revision(), next.stateHash(), now());
            long cycleRevision = cycle.revision() + 1;
            String cycleStateHash = cycleHash(careerId, seasonYear, cycleRevision,
                    cycle.r1r2ImportHash(), cycle.r1r2StandingsRevision());
            int cycleUpdated = jdbc.update("""
                    UPDATE career_competition_cycle
                    SET revision = ?, state_hash = ?, updated_at = ?
                    WHERE career_id = ? AND calendar_season_year = ? AND revision = ?
                    """, cycleRevision, cycleStateHash, now(), careerId, seasonYear,
                    cycle.revision());
            if (cycleUpdated != 1) throw new IllegalStateException(
                    "CAREER_COMPETITION_CYCLE_CAS_FAILED");
            return new CompletionResult(next, false);
        });
    }

    public CareerCompetitionAggregate loadAggregate(
            String careerId, int seasonYear, String competitionId
    ) {
        InstanceRow instance = instance(careerId, seasonYear, competitionId);
        if (instance.sourceInputHash() == null) throw new IllegalStateException(
                "COMPETITION_NOT_MATERIALIZED");
        Long rootSeed = jdbc.queryForObject(
                "SELECT career_root_seed FROM career_save WHERE career_id = ?",
                Long.class, careerId);
        String managed = jdbc.queryForObject(
                "SELECT managed_team_code FROM career_save WHERE career_id = ?",
                String.class, careerId);
        List<CareerCompetitionAggregate.Fixture> fixtures = jdbc.query("""
                SELECT match_id, fixture_id, scheduled_date, series_format, hard_fearless,
                       first_selector_type, first_selector_value, second_selector_type,
                       second_selector_value, first_team_code, second_team_code,
                       lifecycle_status, execution_mode, fixture_root_seed, series_id,
                       winner_output_ids, loser_output_ids, winner_team_code,
                       loser_team_code, completion_receipt_hash
                FROM career_competition_fixture
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = ? ORDER BY scheduled_date, match_id
                """, (result, row) -> fixture(result), careerId, seasonYear,
                competitionId);
        Map<String, String> outputs = new LinkedHashMap<>();
        jdbc.query("""
                SELECT output_id, team_code FROM career_competition_output
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = ? ORDER BY output_id
                """, (RowCallbackHandler) result -> outputs.put(result.getString(1),
                        result.getString(2)),
                careerId, seasonYear, competitionId);
        return new CareerCompetitionAggregate(careerId, seasonYear, competitionId,
                managed, rootSeed, instance.sourceInputHash(), instance.revision(),
                fixtures, outputs, instance.stateHash());
    }

    private CycleView validateAndView(CycleRow cycle) {
        validateCycle(cycle);
        List<InstanceRow> instances = jdbc.query("""
                SELECT competition_id, rule_status, lifecycle_status, blocking_reason,
                       source_input_hash, revision, state_hash
                FROM career_competition_instance
                WHERE career_id = ? AND calendar_season_year = ?
                ORDER BY CASE competition_id
                  WHEN 'LCK_CUP' THEN 1 WHEN 'FIRST_STAND' THEN 2
                  WHEN 'LCK_REGULAR_R1_R2' THEN 3 WHEN 'LCK_ROAD_TO_MSI' THEN 4
                  WHEN 'MSI' THEN 5 WHEN 'EWC_LOL' THEN 6
                  WHEN 'LCK_REGULAR_R3_R4' THEN 7 WHEN 'LCK_PLAY_IN' THEN 8
                  WHEN 'LCK_PLAYOFFS' THEN 9 WHEN 'ASIAN_GAMES_LOL_RELEASE' THEN 10
                  WHEN 'WORLDS' THEN 11 ELSE 99 END
                """, (result, row) -> instance(result), cycle.careerId(),
                cycle.seasonYear());
        if (instances.size() != rules.competitions().size()) {
            throw new IllegalStateException("COMPETITION_INSTANCE_COUNT_MISMATCH");
        }
        instances.forEach(value -> {
            CareerCompetitionRules.CompetitionRule rule = rules.rule(
                    value.competitionId());
            CareerIdentity.requireSha256(value.stateHash(), "competitionStateHash");
            if (!rule.ruleStatus().equals(value.ruleStatus())
                    || value.sourceInputHash() == null
                    && !instanceHash(cycle.careerId(), cycle.seasonYear(),
                    value.competitionId(), value.lifecycleStatus(), value.blockingReason(),
                    null, value.revision()).equals(value.stateHash())
                    || ("LCK_CUP".equals(value.competitionId())
                    || "LCK_PLAYOFFS".equals(value.competitionId()))
                    && !Objects.equals(rule.blockingReason(), value.blockingReason())) {
                throw new IllegalStateException("COMPETITION_INSTANCE_INTEGRITY_FAILURE");
            }
        });
        List<FixtureRow> fixtures = jdbc.query("""
                SELECT competition_id, match_id, fixture_id, series_id, scheduled_date,
                       schedule_status, series_format, hard_fearless, first_team_code,
                       second_team_code, execution_mode, lifecycle_status,
                       fixture_root_seed, completion_receipt_hash
                FROM career_competition_fixture
                WHERE career_id = ? AND calendar_season_year = ?
                ORDER BY scheduled_date, competition_id, match_id
                """, (result, row) -> fixtureRow(result), cycle.careerId(),
                cycle.seasonYear());
        List<OutputRow> outputs = jdbc.query("""
                SELECT competition_id, output_id, team_code
                FROM career_competition_output
                WHERE career_id = ? AND calendar_season_year = ? ORDER BY output_id
                """, (result, row) -> new OutputRow(result.getString(1),
                result.getString(2), result.getString(3)), cycle.careerId(),
                cycle.seasonYear());
        return new CycleView(cycle.careerId(), cycle.seasonYear(), cycle.lifecycleStatus(),
                cycle.blockingReason(), cycle.revision(), cycle.stateHash(),
                cycle.r1r2ImportHash(), cycle.r1r2StandingsRevision(),
                List.copyOf(instances), List.copyOf(fixtures), List.copyOf(outputs));
    }

    private List<CycleRow> findCycle(String careerId, int year, boolean lock) {
        return jdbc.query("""
                SELECT career_id, calendar_season_year, cycle_schema, rule_version,
                       rule_resource_hash, game_policy_version, projection_policy,
                       r3_r4_allocation_policy, lifecycle_status, blocking_reason,
                       r1_r2_import_hash, r1_r2_standings_revision, revision, state_hash
                FROM career_competition_cycle
                WHERE career_id = ? AND calendar_season_year = ?
                """ + (lock ? " FOR UPDATE" : ""), (result, row) -> cycle(result),
                careerId, year);
    }

    private CycleRow lockCycle(String careerId, int year) {
        List<CycleRow> rows = findCycle(careerId, year, true);
        if (rows.size() != 1) throw new IllegalStateException(
                "CAREER_COMPETITION_CYCLE_NOT_FOUND");
        return rows.getFirst();
    }

    private void validateCycle(CycleRow value) {
        if (!CYCLE_SCHEMA.equals(value.cycleSchema())
                || !CareerCompetitionRules.VERSION.equals(value.ruleVersion())
                || !rules.resourceHash().equals(value.ruleResourceHash())
                || !CareerCompetitionRules.GAME_POLICY_VERSION.equals(
                value.gamePolicyVersion())
                || !CareerCompetitionRules.PROJECTION_POLICY.equals(
                value.projectionPolicy())
                || !CareerCompetitionRules.R3_R4_ALLOCATION_POLICY.equals(
                value.r3r4AllocationPolicy())
                || !cycleHash(value.careerId(), value.seasonYear(), value.revision(),
                value.r1r2ImportHash(), value.r1r2StandingsRevision()).equals(
                value.stateHash())) {
            throw new IllegalStateException("CAREER_COMPETITION_CYCLE_INTEGRITY_FAILURE");
        }
    }

    private void insertSeeds(
            String careerId, int year, String competitionId, String scope,
            String inputHash, List<CareerCompetitionAggregate.SeededTeam> ranking
    ) {
        for (CareerCompetitionAggregate.SeededTeam team : ranking) {
            jdbc.update("""
                    INSERT INTO career_competition_seed(
                      career_id, calendar_season_year, competition_id, seed_scope,
                      seed_number, team_code, imported_series_wins,
                      imported_series_losses, imported_game_wins, imported_game_losses,
                      source_input_hash)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, careerId, year, competitionId, scope, team.seed(),
                    team.teamCode(), team.seriesWins(), team.seriesLosses(),
                    team.gameWins(), team.gameLosses(), inputHash);
        }
    }

    private void insertFixture(
            String careerId, int year, String competitionId,
            CareerCompetitionAggregate.Fixture value, String scheduleStatus
    ) {
        jdbc.update("""
                INSERT INTO career_competition_fixture(
                  career_id, calendar_season_year, competition_id, match_id,
                  fixture_id, series_id, scheduled_date, schedule_status,
                  series_format, hard_fearless, first_selector_type,
                  first_selector_value, second_selector_type, second_selector_value,
                  first_team_code, second_team_code, execution_mode, fixture_root_seed,
                  seed_algorithm, lifecycle_status, winner_output_ids, loser_output_ids,
                  winner_team_code, loser_team_code, completion_receipt_hash, revision)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                  ?, ?, NULL, NULL, NULL, 0)
                """, careerId, year, competitionId, value.matchId(), value.fixtureId(),
                value.seriesId(), value.date(), scheduleStatus, value.seriesFormat(),
                value.hardFearless(), value.firstSelector().type(),
                value.firstSelector().value(), value.secondSelector().type(),
                value.secondSelector().value(), value.firstTeamCode(),
                value.secondTeamCode(), value.executionMode(), value.rootSeed(),
                CareerCompetitionAggregate.SEED_ALGORITHM, value.lifecycleStatus(),
                String.join(",", value.winnerOutputs()),
                String.join(",", value.loserOutputs()));
    }

    private void insertR3R4Fixture(
            String careerId, int year, CareerCompetitionAggregate.R3R4Fixture value
    ) {
        CareerCompetitionAggregate.Fixture fixture = new CareerCompetitionAggregate.Fixture(
                value.matchId(), value.fixtureId(), value.date(), value.seriesFormat(),
                value.hardFearless(), new CareerCompetitionRules.ParticipantSelector(
                "FIXED_TEAM", value.firstTeamCode()),
                new CareerCompetitionRules.ParticipantSelector(
                        "FIXED_TEAM", value.secondTeamCode()), value.firstTeamCode(),
                value.secondTeamCode(), "READY", value.executionMode(), value.rootSeed(),
                value.seriesId(), List.of(), List.of(), null, null, null);
        insertFixture(careerId, year, "LCK_REGULAR_R3_R4", fixture,
                "GAME_DERIVED_SCHEDULE_POLICY");
    }

    private void updateInstance(
            String careerId, int year, String competitionId, String lifecycle,
            String blocker, String sourceInputHash, long revision, String stateHash,
            OffsetDateTime now
    ) {
        int updated = jdbc.update("""
                UPDATE career_competition_instance
                SET lifecycle_status = ?, blocking_reason = ?, source_input_hash = ?,
                  revision = ?, state_hash = ?, updated_at = ?
                WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ?
                """, lifecycle, blocker, sourceInputHash, revision, stateHash, now,
                careerId, year, competitionId);
        if (updated != 1) throw new IllegalStateException(
                "CAREER_COMPETITION_INSTANCE_NOT_FOUND");
    }

    private InstanceRow instance(String careerId, int year, String competitionId) {
        List<InstanceRow> values = jdbc.query("""
                SELECT competition_id, rule_status, lifecycle_status, blocking_reason,
                       source_input_hash, revision, state_hash
                FROM career_competition_instance
                WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ?
                """, (result, row) -> instance(result), careerId, year, competitionId);
        if (values.size() != 1) throw new IllegalStateException(
                "COMPETITION_INSTANCE_NOT_FOUND");
        return values.getFirst();
    }

    private OffsetDateTime now() { return clock.instant().atOffset(ZoneOffset.UTC); }

    private static String initialLifecycle(String id) {
        return switch (id) {
            case "LCK_CUP", "LCK_PLAYOFFS" -> "BLOCKED";
            case "LCK_REGULAR_R1_R2" -> "ACTIVE";
            case "FIRST_STAND", "MSI", "EWC_LOL", "WORLDS" -> "EXTERNAL_ONLY";
            case "ASIAN_GAMES_LOL_RELEASE" -> "WINDOW_ONLY";
            default -> "WAITING_FOR_INPUT";
        };
    }

    private static String initialBlocker(CareerCompetitionRules.CompetitionRule rule) {
        return switch (rule.competitionId()) {
            case "LCK_ROAD_TO_MSI", "LCK_REGULAR_R3_R4" ->
                    "R1_R2_FINAL_STANDINGS_REQUIRED";
            case "LCK_PLAY_IN" -> "R3_R4_FINAL_STANDINGS_REQUIRED";
            default -> rule.blockingReason();
        };
    }

    private static String importHash(
            String careerId, int year, String scheduleIdentity, long revision,
            List<CareerCompetitionAggregate.SeededTeam> ranking
    ) {
        CareerIdentity.requireSha256(scheduleIdentity, "scheduleIdentity");
        StringBuilder canonical = new StringBuilder(
                "schema=CAREER_R1_R2_FINAL_STANDINGS_IMPORT_V1\ncareerId=")
                .append(careerId).append("\nseasonYear=").append(year)
                .append("\nscheduleIdentity=").append(scheduleIdentity)
                .append("\nstandingsRevision=").append(revision).append('\n');
        ranking.stream().sorted(java.util.Comparator.comparingInt(
                CareerCompetitionAggregate.SeededTeam::seed)).forEach(value ->
                canonical.append("rank=").append(value.seed()).append('|')
                        .append(value.teamCode()).append('|').append(value.seriesWins())
                        .append('|').append(value.seriesLosses()).append('|')
                        .append(value.gameWins()).append('|').append(value.gameLosses())
                        .append('\n'));
        return CareerCompetitionRules.sha256(canonical.toString().getBytes(
                StandardCharsets.UTF_8));
    }

    private static String cycleHash(
            String careerId, int year, long revision, String importHash,
            Long standingsRevision
    ) {
        String canonical = "schema=" + CYCLE_SCHEMA + '\n'
                + "careerId=" + careerId + '\n' + "seasonYear=" + year + '\n'
                + "ruleVersion=" + CareerCompetitionRules.VERSION + '\n'
                + "ruleResourceHash=" + CareerCompetitionRules.RESOURCE_HASH + '\n'
                + "gamePolicyVersion=" + CareerCompetitionRules.GAME_POLICY_VERSION + '\n'
                + "projectionPolicy=" + CareerCompetitionRules.PROJECTION_POLICY + '\n'
                + "r3r4AllocationPolicy=" + CareerCompetitionRules.R3_R4_ALLOCATION_POLICY + '\n'
                + "revision=" + revision + '\n'
                + "r1r2ImportHash=" + Objects.toString(importHash, "") + '\n'
                + "r1r2StandingsRevision=" + Objects.toString(standingsRevision, "") + '\n';
        return CareerCompetitionRules.sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private static String instanceHash(
            String careerId, int year, String competitionId, String lifecycle,
            String blocker, String sourceInputHash, long revision
    ) {
        return CareerCompetitionRules.sha256((
                "schema=CAREER_COMPETITION_INSTANCE_V1\ncareerId=" + careerId
                        + "\nseasonYear=" + year + "\ncompetitionId=" + competitionId
                        + "\nlifecycleStatus=" + lifecycle + "\nblockingReason="
                        + Objects.toString(blocker, "") + "\nsourceInputHash="
                        + Objects.toString(sourceInputHash, "") + "\nrevision=" + revision
                        + '\n').getBytes(StandardCharsets.UTF_8));
    }

    private static CycleRow cycle(ResultSet result) throws SQLException {
        return new CycleRow(result.getString(1), result.getInt(2), result.getString(3),
                result.getString(4), result.getString(5), result.getString(6),
                result.getString(7), result.getString(8), result.getString(9),
                result.getString(10), result.getString(11),
                result.getObject(12, Long.class), result.getLong(13),
                result.getString(14));
    }

    private static InstanceRow instance(ResultSet result) throws SQLException {
        return new InstanceRow(result.getString(1), result.getString(2),
                result.getString(3), result.getString(4), result.getString(5),
                result.getLong(6), result.getString(7));
    }

    private static FixtureRow fixtureRow(ResultSet result) throws SQLException {
        return new FixtureRow(result.getString(1), result.getString(2),
                result.getString(3), result.getString(4), result.getObject(5,
                LocalDate.class), result.getString(6), result.getString(7),
                result.getBoolean(8), result.getString(9), result.getString(10),
                result.getString(11), result.getString(12), result.getLong(13),
                result.getString(14));
    }

    private static CareerCompetitionAggregate.Fixture fixture(ResultSet result)
            throws SQLException {
        return new CareerCompetitionAggregate.Fixture(result.getString(1),
                result.getString(2), result.getObject(3, LocalDate.class),
                result.getString(4), result.getBoolean(5),
                new CareerCompetitionRules.ParticipantSelector(result.getString(6),
                        result.getString(7)),
                new CareerCompetitionRules.ParticipantSelector(result.getString(8),
                        result.getString(9)), result.getString(10), result.getString(11),
                result.getString(12), result.getString(13), result.getLong(14),
                result.getString(15), split(result.getString(16)),
                split(result.getString(17)), result.getString(18), result.getString(19),
                result.getString(20));
    }

    private static List<String> split(String value) {
        return value == null || value.isEmpty() ? List.of() : List.of(value.split(","));
    }

    public record CycleView(
            String careerId, int seasonYear, String lifecycleStatus,
            String blockingReason, long revision, String stateHash,
            String r1r2ImportHash, Long r1r2StandingsRevision,
            List<InstanceRow> competitions, List<FixtureRow> fixtures,
            List<OutputRow> outputs
    ) {}
    public record InstanceRow(
            String competitionId, String ruleStatus, String lifecycleStatus,
            String blockingReason, String sourceInputHash, long revision,
            String stateHash
    ) {}
    public record FixtureRow(
            String competitionId, String matchId, String fixtureId, String seriesId,
            LocalDate date, String scheduleStatus, String seriesFormat,
            boolean hardFearless, String firstTeamCode, String secondTeamCode,
            String executionMode, String lifecycleStatus, long rootSeed,
            String receiptHash
    ) {}
    public record OutputRow(String competitionId, String outputId, String teamCode) {}
    public record SealResult(CycleView cycle, boolean replayed, String importHash) {}
    public record CompletionResult(CareerCompetitionAggregate aggregate, boolean replayed) {}
    private record CycleRow(
            String careerId, int seasonYear, String cycleSchema, String ruleVersion,
            String ruleResourceHash, String gamePolicyVersion, String projectionPolicy,
            String r3r4AllocationPolicy, String lifecycleStatus, String blockingReason,
            String r1r2ImportHash, Long r1r2StandingsRevision, long revision,
            String stateHash
    ) {}
    private record ApplicationRow(
            String careerId, int seasonYear, String competitionId, String matchId,
            String seriesId
    ) {
        boolean matches(String career, int year, String competition, String match,
                        String series) {
            return careerId.equals(career) && seasonYear == year
                    && competitionId.equals(competition) && matchId.equals(match)
                    && seriesId.equals(series);
        }
    }
}
