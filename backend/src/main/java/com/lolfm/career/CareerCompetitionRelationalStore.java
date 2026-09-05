package com.lolfm.career;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.league.LeagueSeasonFrozenSnapshot;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Transactional Career competition graph, sealed inputs and receipt ledger. */
@Component
public final class CareerCompetitionRelationalStore {
    public static final String CYCLE_SCHEMA = "CAREER_COMPETITION_CYCLE_V2";
    public static final String LEGACY_CYCLE_SCHEMA = "CAREER_COMPETITION_CYCLE_V1";
    public static final String INSTANCE_HASH_ALGORITHM =
            "CAREER_COMPETITION_INSTANCE_SHA256_CANONICAL_V2";
    public static final String CYCLE_HASH_ALGORITHM =
            "CAREER_COMPETITION_CYCLE_SHA256_CANONICAL_V2";
    public static final String LEGACY_INSTANCE_HASH_ALGORITHM =
            "CAREER_COMPETITION_INSTANCE_SHA256_CANONICAL_V1";
    public static final String LEGACY_CYCLE_HASH_ALGORITHM =
            "CAREER_COMPETITION_CYCLE_SHA256_CANONICAL_V1";
    private static final String LEGACY_RULE_RESOURCE_HASH =
            "64acfab316162ca7f17c898c434b7ecce496f085370ff45012a83332d445b770";
    private static final String LEGACY_RULE_VERSION =
            "lck-career-competition-rules-2026-v1";
    private static final String LEGACY_GAME_POLICY_VERSION =
            "CAREER_COMPETITION_GAME_POLICY_V1";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final CareerCompetitionRules rules;
    private final ObjectMapper json;

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
        this.json = new ObjectMapper().findAndRegisterModules();
    }

    public CycleView initialize(
            CareerRelationalStore.NewCareer career,
            int calendarSeasonYear
    ) {
        return initialize(career.careerId(), calendarSeasonYear,
                career.managedTeamCode(), career.rootSeed(),
                rules.initialCupInitialization(calendarSeasonYear), true);
    }

    public CycleView initialize(String careerId, int calendarSeasonYear) {
        CareerBinding binding = careerBinding(careerId);
        return initialize(careerId, calendarSeasonYear, binding.managedTeamCode(),
                binding.rootSeed(), rules.initialCupInitialization(calendarSeasonYear), true);
    }

    /**
     * Legacy signature is intentionally non-authoritative. A caller-built ranking, even
     * with a well-formed hash, can never initialize a future Career cycle.
     */
    @Deprecated
    public CycleView initializeFuture(
            String careerId,
            int calendarSeasonYear,
            int seasonOrdinal,
            CareerCompetitionRules.PriorLckRanking priorRanking
    ) {
        throw new IllegalArgumentException("UNVERIFIED_PRIOR_LCK_RANKING_REJECTED");
    }

    /** Initializes a future cycle only from a verified SEALED DB ranking snapshot. */
    public CycleView initializeFuture(String careerId, int calendarSeasonYear) {
        return transactions.execute(ignored -> {
            VerifiedPriorLckRanking verified = verifiedPriorLckRanking(
                    careerId, calendarSeasonYear);
            CareerBinding binding = careerBinding(careerId);
            return initialize(careerId, calendarSeasonYear, binding.managedTeamCode(),
                    binding.rootSeed(), rules.futureCupInitialization(
                            verified.seasonOrdinal() + 1, calendarSeasonYear,
                            verified.ranking()), false);
        });
    }

    private CycleView initialize(
            String careerId,
            int calendarSeasonYear,
            String managedTeamCode,
            long careerRootSeed,
            CareerCompetitionRules.CupInitialization cupInitialization,
            boolean initialBootstrapRequest
    ) {
        return transactions.execute(ignored -> {
            List<CycleRow> prior = findCycle(careerId, calendarSeasonYear, true);
            if (!prior.isEmpty()) {
                requireV2(prior.getFirst());
                if (prior.getFirst().seasonOrdinal() != cupInitialization.seasonOrdinal()
                        || !Objects.equals(prior.getFirst().initializationPolicyId(),
                        cupInitialization.policyId())
                        || !Objects.equals(prior.getFirst().initializationInputHash(),
                        cupInitialization.inputHash())) {
                    throw new IllegalStateException(
                            "CAREER_COMPETITION_INITIALIZATION_CONFLICT");
                }
                return validateAndView(prior.getFirst());
            }
            if (initialBootstrapRequest) {
                requireFirstCycleBootstrapAuthority(careerId, calendarSeasonYear);
            } else if (!CareerCompetitionRules.FUTURE_CUP_POLICY.equals(
                    cupInitialization.policyId())) {
                throw new IllegalStateException("PRIOR_SEASON_SEALED_RANKING_REQUIRED");
            }
            OffsetDateTime now = now();
            jdbc.update("""
                    INSERT INTO career_competition_cycle(
                      career_id, calendar_season_year, cycle_schema, rule_version,
                      rule_resource_hash, game_policy_version, projection_policy,
                      r3_r4_allocation_policy, lifecycle_status, blocking_reason,
                      r1_r2_import_hash, r1_r2_standings_revision, revision,
                      state_hash, created_at, updated_at, hash_algorithm,
                      season_ordinal, initialization_policy_id,
                      initialization_input_hash)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', NULL, NULL, NULL,
                      0, ?, ?, ?, ?, ?, ?, ?)
                    """, careerId, calendarSeasonYear, CYCLE_SCHEMA,
                    CareerCompetitionRules.VERSION, rules.resourceHash(),
                    CareerCompetitionRules.GAME_POLICY_VERSION,
                    CareerCompetitionRules.PROJECTION_POLICY,
                    CareerCompetitionRules.R3_R4_ALLOCATION_POLICY,
                    "0".repeat(64), now, now, CYCLE_HASH_ALGORITHM,
                    cupInitialization.seasonOrdinal(), cupInitialization.policyId(),
                    cupInitialization.inputHash());
            for (CareerCompetitionRules.CompetitionRule rule : rules.competitions()) {
                String lifecycle = initialLifecycle(rule.competitionId());
                String blocker = initialBlocker(rule);
                boolean cup = "LCK_CUP".equals(rule.competitionId());
                jdbc.update("""
                        INSERT INTO career_competition_instance(
                          career_id, calendar_season_year, competition_id, rule_status,
                          lifecycle_status, blocking_reason, source_input_hash, revision,
                          state_hash, created_at, updated_at, hash_algorithm,
                          materialization_policy_id, materialization_receipt_hash)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?)
                        """, careerId, calendarSeasonYear,
                        rule.competitionId(), rule.ruleStatus(), lifecycle, blocker,
                        cup ? cupInitialization.inputHash() : null,
                        "0".repeat(64), now, now, INSTANCE_HASH_ALGORITHM,
                        cup ? cupInitialization.policyId() : null,
                        cup ? rules.cupMaterializationReceiptHash(cupInitialization) : null);
            }
            materializeCupGraph(careerId, calendarSeasonYear, managedTeamCode,
                    careerRootSeed, cupInitialization);
            refreshAllInstanceHashes(careerId, calendarSeasonYear);
            refreshCycleHash(careerId, calendarSeasonYear);
            return validateAndView(findCycle(careerId, calendarSeasonYear,
                    false).getFirst());
        });
    }

    public CycleView load(String careerId, int seasonYear) {
        List<CycleRow> rows = findCycle(careerId, seasonYear, false);
        if (rows.size() != 1) throw new IllegalStateException(
                "CAREER_COMPETITION_CYCLE_NOT_FOUND");
        requireV2(rows.getFirst());
        return validateAndView(rows.getFirst());
    }

    private static void requireV2(CycleRow cycle) {
        if (!CYCLE_HASH_ALGORITHM.equals(cycle.hashAlgorithm())) {
            throw new IllegalStateException("COMPETITION_STATE_MIGRATION_REQUIRED");
        }
    }

    /** Explicit startup recovery. A V1 graph is proven with V1 bytes before V2 writes. */
    public void recoverLegacyCompetitions() {
        List<String> keys = jdbc.query("""
                SELECT career_id || '|' || calendar_season_year
                FROM career_competition_cycle
                WHERE hash_algorithm = ? ORDER BY career_id, calendar_season_year
                """, (result, ignored) -> result.getString(1),
                LEGACY_CYCLE_HASH_ALGORITHM);
        for (String key : keys) {
            int separator = key.lastIndexOf('|');
            String careerId = key.substring(0, separator);
            int year = Integer.parseInt(key.substring(separator + 1));
            try {
                transactions.executeWithoutResult(ignored -> upgradeLegacyCycle(careerId, year));
            } catch (RuntimeException invalidLegacyGraph) {
                // Deliberately leave V1 untouched. Reads expose migration-required.
            }
        }
        List<String> missing = jdbc.query("""
                SELECT s.career_id || '|' || s.active_calendar_season_year
                FROM career_calendar_state s
                LEFT JOIN career_competition_cycle c
                  ON c.career_id = s.career_id
                 AND c.calendar_season_year = s.active_calendar_season_year
                WHERE c.career_id IS NULL AND s.lifecycle_status = 'ACTIVE'
                ORDER BY s.career_id
                """, (result, ignored) -> result.getString(1));
        for (String key : missing) {
            int separator = key.lastIndexOf('|');
            try {
                initialize(key.substring(0, separator),
                        Integer.parseInt(key.substring(separator + 1)));
            } catch (IllegalStateException missingFutureAuthority) {
                if (!"PRIOR_SEASON_SEALED_RANKING_REQUIRED".equals(
                        missingFutureAuthority.getMessage())) throw missingFutureAuthority;
                // Future cycle recovery is intentionally non-mutating until a verified
                // previous in-game LCK final ranking exists.
            }
        }
    }

    private void requireFirstCycleBootstrapAuthority(String careerId, int year) {
        Integer cycleCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM career_competition_cycle WHERE career_id = ?
                """, Integer.class, careerId);
        Integer activeYear = jdbc.queryForObject("""
                SELECT active_calendar_season_year FROM career_calendar_state
                WHERE career_id = ?
                """, Integer.class, careerId);
        if (cycleCount == null || cycleCount != 0 || activeYear == null
                || activeYear != year) {
            throw new IllegalStateException("PRIOR_SEASON_SEALED_RANKING_REQUIRED");
        }
    }

    private VerifiedPriorLckRanking verifiedPriorLckRanking(
            String careerId, int targetYear
    ) {
        int priorYear = targetYear - 1;
        List<PriorRankingHeader> headers = jdbc.query("""
                SELECT season_ordinal, source_season_id, lifecycle_status, state_hash
                FROM career_lck_final_ranking_snapshot
                WHERE career_id = ? AND calendar_season_year = ?
                FOR UPDATE
                """, (result, ignored) -> new PriorRankingHeader(result.getInt(1),
                result.getString(2), result.getString(3), result.getString(4)),
                careerId, priorYear);
        if (headers.size() != 1 || !"SEALED".equals(
                headers.getFirst().lifecycleStatus())) {
            throw new IllegalStateException("PRIOR_SEASON_SEALED_RANKING_REQUIRED");
        }
        PriorRankingHeader header = headers.getFirst();
        List<CycleRow> priorCycles = findCycle(careerId, priorYear, false);
        if (priorCycles.size() != 1
                || priorCycles.getFirst().seasonOrdinal() != header.seasonOrdinal()) {
            throw new IllegalStateException("PRIOR_SEASON_SEALED_RANKING_REQUIRED");
        }
        List<CareerCompetitionAggregate.SeededTeam> ranking = jdbc.query("""
                SELECT rank_number, team_code, series_wins, series_losses,
                       game_wins, game_losses
                FROM career_lck_final_ranking_row
                WHERE career_id = ? AND calendar_season_year = ?
                ORDER BY rank_number
                """, (result, ignored) -> new CareerCompetitionAggregate.SeededTeam(
                result.getInt(1), result.getString(2), result.getInt(3),
                result.getInt(4), result.getInt(5), result.getInt(6)), careerId,
                priorYear);
        String calculated = finalRankingStateHash(careerId, priorYear,
                header.seasonOrdinal(), header.sourceSeasonId(), ranking);
        if (!calculated.equals(header.stateHash())) {
            throw new IllegalStateException("PRIOR_SEASON_RANKING_INTEGRITY_FAILURE");
        }
        return new VerifiedPriorLckRanking(header.seasonOrdinal(),
                new CareerCompetitionRules.PriorLckRanking(careerId, priorYear,
                        header.lifecycleStatus(), calculated, ranking));
    }

    public static String finalRankingStateHash(
            String careerId, int seasonYear, int seasonOrdinal, String sourceSeasonId,
            List<CareerCompetitionAggregate.SeededTeam> ranking
    ) {
        CareerIdentity.requireCareerId(careerId);
        if (seasonOrdinal < 1 || sourceSeasonId == null || sourceSeasonId.isBlank()) {
            throw new IllegalArgumentException("Invalid LCK final ranking identity");
        }
        StringBuilder canonical = new StringBuilder(
                "schema=CAREER_LCK_FINAL_RANKING_SNAPSHOT_V1\ncareerId=")
                .append(careerId).append("\ncalendarSeasonYear=").append(seasonYear)
                .append("\nseasonOrdinal=").append(seasonOrdinal)
                .append("\nsourceSeasonId=").append(sourceSeasonId)
                .append("\nlifecycleStatus=SEALED\n");
        ranking.stream().sorted(java.util.Comparator.comparingInt(
                CareerCompetitionAggregate.SeededTeam::seed)).forEach(value -> canonical
                .append("rank=").append(value.seed()).append('|')
                .append(value.teamCode()).append('|').append(value.seriesWins())
                .append('|').append(value.seriesLosses()).append('|')
                .append(value.gameWins()).append('|').append(value.gameLosses())
                .append('\n'));
        return CareerCompetitionRules.sha256(canonical.toString().getBytes(
                StandardCharsets.UTF_8));
    }

    private void upgradeLegacyCycle(String careerId, int year) {
        CycleRow cycle = lockCycle(careerId, year);
        if (!LEGACY_CYCLE_HASH_ALGORITHM.equals(cycle.hashAlgorithm())) return;
        List<InstanceRow> instances = loadInstances(careerId, year);
        validateLegacyCycle(cycle, instances);
        normalizeLegacyR3R4Selectors(careerId, year);
        normalizeLegacyFixtureOrder(careerId, year);
        CareerBinding binding = careerBinding(careerId);
        CareerCompetitionRules.CupInitialization cupInitialization =
                rules.initialCupInitialization(year);
        OffsetDateTime now = now();
        jdbc.update("""
                UPDATE career_competition_cycle
                SET cycle_schema = ?, rule_version = ?, rule_resource_hash = ?,
                    game_policy_version = ?, projection_policy = ?,
                    r3_r4_allocation_policy = ?, hash_algorithm = ?, state_hash = ?,
                    season_ordinal = ?, initialization_policy_id = ?,
                    initialization_input_hash = ?, updated_at = ?
                WHERE career_id = ? AND calendar_season_year = ?
                """, CYCLE_SCHEMA, CareerCompetitionRules.VERSION, rules.resourceHash(),
                CareerCompetitionRules.GAME_POLICY_VERSION,
                CareerCompetitionRules.PROJECTION_POLICY,
                CareerCompetitionRules.R3_R4_ALLOCATION_POLICY,
                CYCLE_HASH_ALGORITHM, "0".repeat(64), 1,
                cupInitialization.policyId(), cupInitialization.inputHash(), now,
                careerId, year);
        for (InstanceRow instance : instances) {
            CareerCompetitionRules.CompetitionRule rule = rules.rule(
                    instance.competitionId());
            boolean cup = "LCK_CUP".equals(instance.competitionId());
            String inputHash = cup ? cupInitialization.inputHash()
                    : instance.sourceInputHash();
            String policy = cup ? cupInitialization.policyId()
                    : instance.sourceInputHash() == null ? null
                    : "SEALED_COMPETITION_INPUT_MIGRATED_FROM_V1";
            String receipt = cup ? rules.cupMaterializationReceiptHash(cupInitialization)
                    : instance.sourceInputHash();
            String lifecycle = cup ? "READY" : instance.lifecycleStatus();
            String blocker = cup ? null : instance.blockingReason();
            jdbc.update("""
                    UPDATE career_competition_instance
                    SET rule_status = ?, lifecycle_status = ?, blocking_reason = ?,
                        source_input_hash = ?, hash_algorithm = ?, state_hash = ?,
                        materialization_policy_id = ?,
                        materialization_receipt_hash = ?, updated_at = ?
                    WHERE career_id = ? AND calendar_season_year = ?
                      AND competition_id = ?
                    """, rule.ruleStatus(), lifecycle, blocker, inputHash,
                    INSTANCE_HASH_ALGORITHM, "0".repeat(64), policy, receipt, now,
                    careerId, year, instance.competitionId());
        }
        CareerCompetitionRules.CompetitionRule kespa = rules.rule("KESPA_CUP");
        jdbc.update("""
                INSERT INTO career_competition_instance(
                  career_id, calendar_season_year, competition_id, rule_status,
                  lifecycle_status, blocking_reason, source_input_hash, revision,
                  state_hash, created_at, updated_at, hash_algorithm,
                  materialization_policy_id, materialization_receipt_hash)
                VALUES (?, ?, 'KESPA_CUP', ?, 'SOURCE_GAP', ?, NULL, 0, ?, ?, ?, ?,
                  NULL, NULL)
                """, careerId, year, kespa.ruleStatus(), kespa.blockingReason(),
                "0".repeat(64), now, now, INSTANCE_HASH_ALGORITHM);
        materializeCupGraph(careerId, year, binding.managedTeamCode(),
                binding.rootSeed(), cupInitialization);
        refreshAllInstanceHashes(careerId, year);
        refreshCycleHash(careerId, year);
    }

    private void validateLegacyCycle(CycleRow cycle, List<InstanceRow> instances) {
        if (!LEGACY_CYCLE_SCHEMA.equals(cycle.cycleSchema())
                || !LEGACY_RULE_VERSION.equals(cycle.ruleVersion())
                || !LEGACY_RULE_RESOURCE_HASH.equals(cycle.ruleResourceHash())
                || !LEGACY_GAME_POLICY_VERSION.equals(cycle.gamePolicyVersion())
                || !CareerCompetitionRules.PROJECTION_POLICY.equals(
                cycle.projectionPolicy())
                || !CareerCompetitionRules.R3_R4_ALLOCATION_POLICY.equals(
                cycle.r3r4AllocationPolicy())
                || !LEGACY_CYCLE_HASH_ALGORITHM.equals(cycle.hashAlgorithm())
                || !legacyCycleHash(cycle.careerId(), cycle.seasonYear(),
                cycle.revision(), cycle.r1r2ImportHash(),
                cycle.r1r2StandingsRevision()).equals(cycle.stateHash())) {
            throw new IllegalStateException("LEGACY_COMPETITION_CYCLE_INTEGRITY_FAILURE");
        }
        if (instances.size() != 11) throw new IllegalStateException(
                "LEGACY_COMPETITION_INSTANCE_COUNT_MISMATCH");
        for (InstanceRow instance : instances) validateLegacyInstance(cycle, instance);
    }

    private void validateLegacyInstance(CycleRow cycle, InstanceRow instance) {
        if (!LEGACY_INSTANCE_HASH_ALGORITHM.equals(instance.hashAlgorithm())) {
            throw new IllegalStateException("LEGACY_COMPETITION_INSTANCE_ALGORITHM_MISMATCH");
        }
        List<CareerCompetitionAggregate.Fixture> fixtures;
        try {
            fixtures = legacyFixtures(cycle.careerId(), cycle.seasonYear(),
                    instance.competitionId());
        } catch (IllegalArgumentException invalidSelector) {
            fixtures = List.of();
        }
        Map<String, String> outputs = legacyOutputs(cycle.careerId(), cycle.seasonYear(),
                instance.competitionId());
        String expected;
        if ("LCK_REGULAR_R3_R4".equals(instance.competitionId())
                && instance.sourceInputHash() != null) {
            expected = legacyR3R4Hash(cycle.careerId(), cycle.seasonYear(), instance);
        } else if (fixtures.isEmpty() && outputs.isEmpty()) {
            expected = instanceHash(cycle.careerId(), cycle.seasonYear(),
                    instance.competitionId(), instance.lifecycleStatus(),
                    instance.blockingReason(), instance.sourceInputHash(),
                    instance.revision());
        } else {
            expected = CareerCompetitionAggregate.legacyStateHash(cycle.careerId(),
                    cycle.seasonYear(), instance.competitionId(),
                    instance.sourceInputHash(), instance.revision(), fixtures, outputs);
        }
        if (!expected.equals(instance.stateHash())) throw new IllegalStateException(
                "LEGACY_COMPETITION_INSTANCE_INTEGRITY_FAILURE");
    }

    private List<CareerCompetitionAggregate.Fixture> legacyFixtures(
            String careerId, int year, String competitionId
    ) {
        return jdbc.query("""
                SELECT match_id, fixture_id, scheduled_date, series_format, hard_fearless,
                       first_selector_type, first_selector_value, second_selector_type,
                       second_selector_value, first_team_code, second_team_code,
                       lifecycle_status, execution_mode, fixture_root_seed, series_id,
                       winner_output_ids, loser_output_ids, winner_team_code,
                       loser_team_code, completion_receipt_hash
                FROM career_competition_fixture
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = ? ORDER BY scheduled_date, match_id
                """, (result, ignored) -> fixture(result), careerId, year, competitionId);
    }

    private Map<String, String> legacyOutputs(String careerId, int year,
                                               String competitionId) {
        LinkedHashMap<String, String> outputs = new LinkedHashMap<>();
        jdbc.query("""
                SELECT output_id, team_code FROM career_competition_output
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = ? ORDER BY output_id
                """, (RowCallbackHandler) result -> outputs.put(result.getString(1),
                result.getString(2)), careerId, year, competitionId);
        return Map.copyOf(outputs);
    }

    private String legacyR3R4Hash(String careerId, int year, InstanceRow instance) {
        List<CareerCompetitionAggregate.SeededTeam> ranking = jdbc.query("""
                SELECT seed_number, team_code, imported_series_wins,
                       imported_series_losses, imported_game_wins, imported_game_losses
                FROM career_competition_seed
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = 'LCK_REGULAR_R3_R4'
                ORDER BY seed_number
                """, (result, ignored) -> new CareerCompetitionAggregate.SeededTeam(
                result.getInt(1), result.getString(2), result.getInt(3), result.getInt(4),
                result.getInt(5), result.getInt(6)), careerId, year);
        List<CareerCompetitionAggregate.R3R4Fixture> fixtures = jdbc.query("""
                SELECT match_id, fixture_id, series_id, scheduled_date, first_team_code,
                       second_team_code, execution_mode, fixture_root_seed,
                       series_format, hard_fearless
                FROM career_competition_fixture
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = 'LCK_REGULAR_R3_R4'
                ORDER BY scheduled_date, match_id
                """, (result, ignored) -> {
            String matchId = result.getString(1);
            String group = matchId.substring(0, matchId.indexOf("_R"));
            int round = Integer.parseInt(matchId.substring(matchId.indexOf("_R") + 2,
                    matchId.indexOf("_M")));
            return new CareerCompetitionAggregate.R3R4Fixture(matchId,
                    result.getString(2), result.getString(3), group, round,
                    result.getObject(4, LocalDate.class), result.getString(5),
                    result.getString(6), result.getString(7), result.getLong(8),
                    result.getString(9), result.getBoolean(10));
        }, careerId, year);
        return CareerCompetitionAggregate.legacyR3R4StateHash(careerId, year,
                instance.sourceInputHash(), ranking, fixtures);
    }

    private void normalizeLegacyR3R4Selectors(String careerId, int year) {
        Map<String, Integer> ranks = new LinkedHashMap<>();
        jdbc.query("""
                SELECT team_code, seed_number FROM career_competition_seed
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = 'LCK_REGULAR_R3_R4'
                """, (RowCallbackHandler) result -> ranks.put(result.getString(1),
                result.getInt(2)), careerId, year);
        for (Map.Entry<String, Integer> entry : ranks.entrySet()) {
            jdbc.update("""
                    UPDATE career_competition_fixture
                    SET first_selector_type = 'R1_R2_RANK', first_selector_value = ?
                    WHERE career_id = ? AND calendar_season_year = ?
                      AND competition_id = 'LCK_REGULAR_R3_R4'
                      AND first_team_code = ?
                    """, Integer.toString(entry.getValue()), careerId, year,
                    entry.getKey());
            jdbc.update("""
                    UPDATE career_competition_fixture
                    SET second_selector_type = 'R1_R2_RANK', second_selector_value = ?
                    WHERE career_id = ? AND calendar_season_year = ?
                      AND competition_id = 'LCK_REGULAR_R3_R4'
                      AND second_team_code = ?
                    """, Integer.toString(entry.getValue()), careerId, year,
                    entry.getKey());
        }
    }

    private void normalizeLegacyFixtureOrder(String careerId, int year) {
        for (CareerCompetitionRules.CompetitionRule rule : rules.competitions()) {
            List<String> matches = jdbc.query("""
                    SELECT match_id FROM career_competition_fixture
                    WHERE career_id = ? AND calendar_season_year = ?
                      AND competition_id = ? ORDER BY scheduled_date, match_id
                    """, (result, ignored) -> result.getString(1), careerId, year,
                    rule.competitionId());
            for (int index = 0; index < matches.size(); index++) {
                String matchId = matches.get(index);
                String stage = "LCK_REGULAR_R3_R4".equals(rule.competitionId())
                        ? matchId.substring(0, matchId.indexOf("_R"))
                        : rule.competitionId();
                jdbc.update("""
                        UPDATE career_competition_fixture
                        SET match_order = ?, stage_id = ?
                        WHERE career_id = ? AND calendar_season_year = ?
                          AND competition_id = ? AND match_id = ?
                        """, index + 1, stage, careerId, year,
                        rule.competitionId(), matchId);
            }
        }
    }

    private CareerBinding careerBinding(String careerId) {
        List<CareerBinding> values = jdbc.query("""
                SELECT managed_team_code, career_root_seed
                FROM career_save WHERE career_id = ?
                """, (result, ignored) -> new CareerBinding(result.getString(1),
                result.getLong(2)), careerId);
        if (values.size() != 1) throw new IllegalStateException("CAREER_NOT_FOUND");
        return values.getFirst();
    }

    private void materializeCupGraph(
            String careerId,
            int year,
            String managedTeamCode,
            long careerRootSeed,
            CareerCompetitionRules.CupInitialization initialization
    ) {
        Integer fixtureCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM career_competition_fixture
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = 'LCK_CUP'
                """, Integer.class, careerId, year);
        if (fixtureCount == null || fixtureCount != 0) throw new IllegalStateException(
                "LCK_CUP_MATERIALIZATION_REQUIRES_EMPTY_GRAPH");
        for (CareerCompetitionRules.CupGroupSeed group : initialization.groups()) {
            jdbc.update("""
                    INSERT INTO career_competition_seed(
                      career_id, calendar_season_year, competition_id, seed_scope,
                      seed_number, team_code, imported_series_wins,
                      imported_series_losses, imported_game_wins, imported_game_losses,
                      source_input_hash)
                    VALUES (?, ?, 'LCK_CUP', ?, ?, ?, 0, 0, 0, 0, ?)
                    """, careerId, year, "CUP_GROUP_" + group.groupId(),
                    group.groupSeed(), group.teamCode(), initialization.inputHash());
        }
        CareerCompetitionAggregate aggregate = CareerCompetitionAggregate.materializeCup(
                rules, careerId, year, managedTeamCode, careerRootSeed, initialization);
        Map<String, CareerCompetitionRules.MatchRule> matchRules = rules.rule("LCK_CUP")
                .matches().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                        CareerCompetitionRules.MatchRule::matchId, value -> value));
        for (CareerCompetitionAggregate.Fixture fixture : aggregate.fixtures()) {
            CareerCompetitionRules.MatchRule match = matchRules.get(fixture.matchId());
            if (match == null) throw new IllegalStateException(
                    "LCK_CUP_MATCH_RULE_MISSING");
            insertFixture(careerId, year, "LCK_CUP", fixture, match.stageId(),
                    match.matchOrder(), match.groupId(), match.groupPointValue(),
                    match.selectionRightOwner(), match.opponentChoicePolicy(),
                    match.sideSelectionPolicy(), match.scheduleStatus());
        }
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
            requireV2(cycle);
            validateCycle(cycle, loadInstances(careerId, seasonYear));
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
            for (int index = 0; index < road.fixtures().size(); index++) {
                insertFixture(careerId, seasonYear, "LCK_ROAD_TO_MSI",
                        road.fixtures().get(index), "ROAD_TO_MSI", index + 1,
                        null, null, null, null, null,
                        "OFFICIAL_PROJECTED_DATE");
            }
            CareerCompetitionAggregate.R3R4Stage r3r4 =
                    CareerCompetitionAggregate.materializeR3R4(careerId, seasonYear,
                            managedTeamCode, careerRootSeed, importHash, ranking);
            for (int index = 0; index < r3r4.fixtures().size(); index++) {
                insertR3R4Fixture(careerId, seasonYear, r3r4.fixtures().get(index),
                        ranking, index + 1);
            }
            OffsetDateTime now = now();
            updateInstance(careerId, seasonYear, "LCK_REGULAR_R1_R2",
                    "COMPLETED", null, importHash, standingsRevision,
                    "0".repeat(64), now);
            updateInstance(careerId, seasonYear, "LCK_ROAD_TO_MSI", "READY",
                    null, importHash, road.revision(), "0".repeat(64), now);
            updateInstance(careerId, seasonYear, "LCK_REGULAR_R3_R4", "READY",
                    null, importHash, 0, "0".repeat(64), now);
            setInstanceMaterializationAuthority(careerId, seasonYear,
                    "LCK_ROAD_TO_MSI", "SEALED_R1_R2_RANKING_GRAPH_V1",
                    road.stateHash(), now);
            setInstanceMaterializationAuthority(careerId, seasonYear,
                    "LCK_REGULAR_R3_R4",
                    CareerCompetitionRules.R3_R4_ALLOCATION_POLICY,
                    r3r4.stateHash(), now);
            refreshInstanceHash(careerId, seasonYear, "LCK_REGULAR_R1_R2");
            refreshInstanceHash(careerId, seasonYear, "LCK_ROAD_TO_MSI");
            refreshInstanceHash(careerId, seasonYear, "LCK_REGULAR_R3_R4");
            long nextRevision = cycle.revision() + 1;
            int updated = jdbc.update("""
                    UPDATE career_competition_cycle
                    SET r1_r2_import_hash = ?, r1_r2_standings_revision = ?,
                      revision = ?, state_hash = ?, updated_at = ?
                    WHERE career_id = ? AND calendar_season_year = ? AND revision = ?
                    """, importHash, standingsRevision, nextRevision, "0".repeat(64), now,
                    careerId, seasonYear, cycle.revision());
            if (updated != 1) throw new IllegalStateException(
                    "CAREER_COMPETITION_CYCLE_CAS_FAILED");
            refreshCycleHash(careerId, seasonYear);
            return new SealResult(load(careerId, seasonYear), false, importHash);
        });
    }

    /** Creates or replays the immutable server-owned execution authority. */
    public CareerCompetitionSeriesBindingV1 bindFixture(
            String careerId, int seasonYear, String matchId,
            LeagueSeasonFrozenSnapshot productionSnapshot,
            String resourceProvenanceHash
    ) {
        return transactions.execute(ignored -> {
            CycleView cycle = load(careerId, seasonYear);
            FixtureRow fixture = cycle.fixtures().stream()
                    .filter(value -> value.matchId().equals(matchId))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException(
                            "COMPETITION_MATCH_NOT_FOUND"));
            InstanceRow instance = cycle.competitions().stream()
                    .filter(value -> value.competitionId().equals(
                            fixture.competitionId()))
                    .findFirst().orElseThrow();
            String managedTeam = careerBinding(careerId).managedTeamCode();
            CareerCompetitionSeriesBindingV1 candidate =
                    CareerCompetitionSeriesBindingV1.create(cycle, instance,
                            fixture, managedTeam, rules.resourceHash(),
                            productionSnapshot, resourceProvenanceHash);
            List<String> prior = jdbc.query("""
                    SELECT binding_canonical FROM career_competition_series_binding
                    WHERE career_id = ? AND calendar_season_year = ?
                      AND competition_id = ? AND match_id = ?
                    """, (result, row) -> result.getString(1), careerId, seasonYear,
                    fixture.competitionId(), matchId);
            if (!prior.isEmpty()) {
                CareerCompetitionSeriesBindingV1 restored =
                        CareerCompetitionSeriesBindingV1.restoreCanonical(
                                prior.getFirst());
                if (!restored.bindingHash().equals(candidate.bindingHash())) {
                    throw new IllegalStateException(
                            "COMPETITION_FIXTURE_BINDING_CONFLICT");
                }
                return restored;
            }
            OffsetDateTime now = now();
            jdbc.update("""
                    INSERT INTO career_competition_series_binding(
                      binding_hash, career_id, calendar_season_year,
                      competition_id, match_id, fixture_id, series_id,
                      execution_mode, binding_schema, binding_canonical,
                      lifecycle_status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CREATED', ?, ?)
                    """, candidate.bindingHash(), careerId, seasonYear,
                    fixture.competitionId(), matchId, fixture.fixtureId(),
                    fixture.seriesId(), fixture.executionMode(),
                    CareerCompetitionSeriesBindingV1.SCHEMA,
                    candidate.canonicalText(), now, now);
            return candidate;
        });
    }

    public CareerCompetitionSeriesBindingV1 loadBinding(
            String careerId, int seasonYear, String matchId
    ) {
        List<String> values = jdbc.query("""
                SELECT binding_canonical FROM career_competition_series_binding
                WHERE career_id = ? AND calendar_season_year = ? AND match_id = ?
                """, (result, row) -> result.getString(1), careerId, seasonYear,
                matchId);
        if (values.size() != 1) throw new IllegalStateException(
                "COMPETITION_FIXTURE_BINDING_NOT_FOUND");
        return CareerCompetitionSeriesBindingV1.restoreCanonical(values.getFirst());
    }

    public ExecutionProjection executionProjection(
            String careerId, int seasonYear, String matchId
    ) {
        List<ExecutionProjection> values = jdbc.query("""
                SELECT b.binding_hash, b.series_id, b.lifecycle_status,
                       j.job_id, j.lifecycle_status,
                       COALESCE(j.client_command_id, c.client_command_id),
                       j.failure_code,
                       CASE WHEN r.match_id IS NULL THEN 'NOT_APPLIED'
                            ELSE 'APPLIED' END
                FROM career_competition_series_binding b
                LEFT JOIN career_competition_job j
                  ON j.binding_hash = b.binding_hash
                LEFT JOIN career_competition_command c
                  ON c.binding_hash = b.binding_hash
                 AND c.command_type = 'START_OR_RESUME'
                LEFT JOIN career_competition_result_detail r
                  ON r.binding_hash = b.binding_hash
                WHERE b.career_id = ? AND b.calendar_season_year = ?
                  AND b.match_id = ?
                ORDER BY j.created_at DESC NULLS LAST,
                         c.created_at ASC, c.client_command_id ASC
                LIMIT 1
                """, (result, row) -> new ExecutionProjection(result.getString(1),
                result.getString(2), result.getString(3), result.getString(4),
                result.getString(5), result.getString(6), result.getString(7),
                result.getString(8)), careerId, seasonYear, matchId);
        return values.isEmpty() ? null : values.getFirst();
    }

    public List<CupStandingView> cupStandings(String careerId, int seasonYear) {
        Map<String, Integer> points = new LinkedHashMap<>();
        points.put("BARON", 0);
        points.put("ELDER", 0);
        jdbc.query("""
                SELECT s.seed_scope, SUM(f.group_point_value)
                FROM career_competition_fixture f
                JOIN career_competition_seed s
                  ON s.career_id = f.career_id
                 AND s.calendar_season_year = f.calendar_season_year
                 AND s.competition_id = f.competition_id
                 AND s.team_code = f.winner_team_code
                WHERE f.career_id = ? AND f.calendar_season_year = ?
                  AND f.competition_id = 'LCK_CUP'
                  AND f.stage_id = 'GROUP_BATTLE'
                  AND f.lifecycle_status = 'COMPLETED'
                  AND s.seed_scope IN ('CUP_GROUP_BARON', 'CUP_GROUP_ELDER')
                GROUP BY s.seed_scope
                ORDER BY s.seed_scope
                """, (RowCallbackHandler) result -> points.put(
                result.getString(1).substring("CUP_GROUP_".length()),
                result.getInt(2)), careerId, seasonYear);
        return jdbc.query("""
                SELECT group_id, group_rank, team_code, match_wins, match_losses,
                       game_wins, game_losses, strength_of_victory,
                       win_time_seconds, tie_break_trace, standings_hash
                FROM career_lck_cup_standing
                WHERE career_id = ? AND calendar_season_year = ?
                ORDER BY group_id, group_rank
                """, (result, row) -> new CupStandingView(result.getString(1),
                points.getOrDefault(result.getString(1), 0), result.getInt(2),
                result.getString(3), result.getInt(4), result.getInt(5),
                result.getInt(6), result.getInt(7), result.getInt(8),
                result.getInt(9), result.getString(10), result.getString(11)),
                careerId, seasonYear);
    }

    public List<SeedView> currentSeeds(String careerId, int seasonYear) {
        return jdbc.query("""
                SELECT competition_id, seed_scope, seed_number, team_code,
                       source_input_hash
                FROM career_competition_seed
                WHERE career_id = ? AND calendar_season_year = ?
                  AND seed_scope IN ('CUP_PLAY_IN_SEED', 'CUP_PLAYOFF_SEED',
                    'PLAY_IN_SEED')
                ORDER BY competition_id, seed_scope, seed_number
                """, (result, row) -> new SeedView(result.getString(1),
                result.getString(2), result.getInt(3), result.getString(4),
                result.getString(5)), careerId, seasonYear);
    }

    /** Lock the job before touching the graph; completion and fence share one commit. */
    public CompletionResult applyAutoVerifiedCompletion(
            VerifiedCompetitionFixtureCompletion verification, String jobId,
            String leaseToken, java.time.Duration duration
    ) {
        return transactions.execute(ignored -> {
            OffsetDateTime at = now();
            int owned = jdbc.update("""
                    UPDATE career_competition_job SET lease_expires_at = ?, updated_at = ?
                    WHERE job_id = ? AND binding_hash = ? AND lifecycle_status = 'RUNNING'
                      AND lease_token = ? AND lease_expires_at > ?
                    """, at.plus(duration), at, jobId, verification.receipt().bindingHash(),
                    leaseToken, at);
            if (owned != 1) throw new IllegalStateException(CareerCompetitionJobLease.FENCE_REJECTED);
            CompletionResult applied = applyVerifiedCompletion(verification);
            int completed = jdbc.update("""
                    UPDATE career_competition_job
                    SET lifecycle_status = 'COMPLETED', completion_receipt_hash = ?,
                        lease_token = NULL, lease_expires_at = NULL, updated_at = ?
                    WHERE job_id = ? AND lifecycle_status = 'RUNNING'
                      AND lease_token = ? AND lease_expires_at > ?
                    """, verification.receipt().receiptHash(), now(), jobId, leaseToken, now());
            if (completed != 1) throw new IllegalStateException(CareerCompetitionJobLease.FENCE_REJECTED);
            return applied;
        });
    }

    /** Durable verified evidence, application and fixture must agree before bypassing replay. */
    public boolean hasAppliedCompletion(CareerCompetitionSeriesBindingV1 binding) {
        List<String> hashes = jdbc.query("""
                SELECT completion_receipt_hash FROM career_competition_series_binding
                WHERE binding_hash = ? AND lifecycle_status = 'COMPLETED'
                """, (result, row) -> result.getString(1), binding.bindingHash());
        if (hashes.isEmpty()) return false;
        List<CareerCompetitionFixtureCompletionReceiptV1> receipts = jdbc.query("""
                SELECT r.receipt_json, r.receipt_canonical
                FROM career_competition_completion_receipt r
                JOIN career_competition_application a ON a.receipt_hash = r.receipt_hash
                JOIN career_competition_result_detail d ON d.receipt_hash = r.receipt_hash
                JOIN career_competition_fixture f
                  ON f.career_id = a.career_id AND f.calendar_season_year = a.calendar_season_year
                 AND f.competition_id = a.competition_id AND f.match_id = a.match_id
                WHERE r.receipt_hash = ? AND r.binding_hash = ?
                  AND a.career_id = ? AND a.calendar_season_year = ?
                  AND a.competition_id = ? AND a.match_id = ? AND a.series_id = ?
                  AND d.career_id = a.career_id AND d.calendar_season_year = a.calendar_season_year
                  AND d.competition_id = a.competition_id AND d.match_id = a.match_id
                  AND d.binding_hash = r.binding_hash
                  AND d.first_score = r.first_score AND d.second_score = r.second_score
                  AND d.total_duration_seconds = r.total_duration_seconds
                  AND f.completion_receipt_hash = r.receipt_hash AND f.lifecycle_status = 'COMPLETED'
                  AND f.winner_team_code = r.winner_team_code AND f.loser_team_code = r.loser_team_code
                  AND f.first_team_code = ? AND f.second_team_code = ?
                """, (result, row) -> {
            try {
                var receipt = json.readValue(result.getString(1),
                        CareerCompetitionFixtureCompletionReceiptV1.class);
                if (!receipt.canonicalText().equals(result.getString(2))) {
                    throw new IllegalStateException("COMPETITION_PERSISTED_COMPLETION_MISMATCH");
                }
                return receipt;
            } catch (JsonProcessingException invalid) {
                throw new IllegalStateException("COMPETITION_PERSISTED_COMPLETION_MISMATCH", invalid);
            }
        }, hashes.getFirst(), binding.bindingHash(), binding.careerId(), binding.seasonYear(),
                binding.competitionId(), binding.matchId(), binding.boundSeriesId(),
                binding.firstTeamCode(), binding.secondTeamCode());
        if (receipts.size() != 1) throw new IllegalStateException("COMPETITION_PERSISTED_COMPLETION_MISMATCH");
        var receipt = receipts.getFirst();
        requireReceiptScope(binding, receipt);
        if (!receipt.receiptHash().equals(hashes.getFirst())) {
            throw new IllegalStateException("COMPETITION_PERSISTED_COMPLETION_MISMATCH");
        }
        load(binding.careerId(), binding.seasonYear()); // Validate canonical graph and ledger integrity.
        return true;
    }

    private static void requireReceiptScope(CareerCompetitionSeriesBindingV1 binding,
                                           CareerCompetitionFixtureCompletionReceiptV1 receipt) {
        if (!binding.bindingHash().equals(receipt.bindingHash())
                || !binding.careerId().equals(receipt.careerId())
                || binding.seasonYear() != receipt.seasonYear()
                || !binding.competitionId().equals(receipt.competitionId())
                || !binding.matchId().equals(receipt.matchId())
                || !binding.fixtureId().equals(receipt.fixtureId())
                || !binding.boundSeriesId().equals(receipt.seriesId())
                || !binding.firstTeamCode().equals(receipt.firstTeamCode())
                || !binding.secondTeamCode().equals(receipt.secondTeamCode())) {
            throw new IllegalArgumentException("COMPETITION_VERIFIED_RECEIPT_SCOPE_MISMATCH");
        }
    }

    /** Applies only an opaque token minted by the Series replay verifier. */
    public CompletionResult applyVerifiedCompletion(
            VerifiedCompetitionFixtureCompletion verification
    ) {
        Objects.requireNonNull(verification, "verification");
        return transactions.execute(ignored -> {
            CareerCompetitionFixtureCompletionReceiptV1 receipt =
                    verification.receipt();
            lockCycle(receipt.careerId(), receipt.seasonYear());
            CareerCompetitionSeriesBindingV1 binding = loadBinding(
                    receipt.careerId(), receipt.seasonYear(), receipt.matchId());
            requireReceiptScope(binding, receipt);
            if (hasAppliedCompletion(binding)) {
                String appliedHash = jdbc.queryForObject("""
                        SELECT completion_receipt_hash FROM career_competition_series_binding
                        WHERE binding_hash = ?
                        """, String.class, binding.bindingHash());
                if (!receipt.receiptHash().equals(appliedHash)) {
                    throw new IllegalStateException("COMPETITION_RECEIPT_HASH_CONFLICT");
                }
                return new CompletionResult(loadAggregate(receipt.careerId(),
                        receipt.seasonYear(), receipt.competitionId()), true);
            }
            List<String> existing = jdbc.query("""
                    SELECT receipt_canonical FROM career_competition_completion_receipt
                    WHERE receipt_hash = ?
                    """, (result, row) -> result.getString(1), receipt.receiptHash());
            if (!existing.isEmpty() && !existing.getFirst().equals(
                    receipt.canonicalText())) {
                throw new IllegalStateException("COMPETITION_RECEIPT_HASH_CONFLICT");
            }
            if (existing.isEmpty()) {
                jdbc.update("""
                        INSERT INTO career_competition_completion_receipt(
                          receipt_hash, binding_hash, receipt_schema,
                          receipt_canonical, receipt_json, first_score, second_score,
                          winner_team_code, loser_team_code, total_duration_seconds,
                          created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, receipt.receiptHash(), receipt.bindingHash(),
                        CareerCompetitionFixtureCompletionReceiptV1.SCHEMA,
                        receipt.canonicalText(), writeJson(receipt),
                        receipt.firstScore(), receipt.secondScore(),
                        receipt.winnerTeamCode(), receipt.loserTeamCode(),
                        receipt.totalDurationSeconds(), now());
            }
            CompletionResult result = applyCompletionState(receipt.careerId(),
                    receipt.seasonYear(), receipt.competitionId(), receipt.matchId(),
                    receipt.seriesId(), receipt.firstTeamCode(),
                    receipt.secondTeamCode(), receipt.winnerTeamCode(),
                    receipt.receiptHash());
            if (!result.replayed()) {
                jdbc.update("""
                        INSERT INTO career_competition_result_detail(
                          career_id, calendar_season_year, competition_id, match_id,
                          binding_hash, receipt_hash, first_score, second_score,
                          total_duration_seconds, applied_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, receipt.careerId(), receipt.seasonYear(),
                        receipt.competitionId(), receipt.matchId(),
                        receipt.bindingHash(), receipt.receiptHash(),
                        receipt.firstScore(), receipt.secondScore(),
                        receipt.totalDurationSeconds(), now());
                advanceResultGraph(receipt.careerId(), receipt.seasonYear(),
                        receipt.competitionId());
            }
            jdbc.update("""
                    UPDATE career_competition_series_binding
                    SET lifecycle_status = 'COMPLETED', completion_receipt_hash = ?,
                        updated_at = ? WHERE binding_hash = ?
                    """, receipt.receiptHash(), now(), receipt.bindingHash());
            return new CompletionResult(loadAggregate(receipt.careerId(),
                    receipt.seasonYear(), receipt.competitionId()), result.replayed());
        });
    }

    private void advanceResultGraph(String careerId, int year, String competitionId) {
        if ("LCK_CUP".equals(competitionId)) {
            advanceCupGraph(careerId, year);
        } else if ("LCK_REGULAR_R3_R4".equals(competitionId)) {
            materializeLckPlayInWhenReady(careerId, year);
        }
        refreshInstanceHash(careerId, year, competitionId);
        refreshCycleHash(careerId, year);
    }

    private void advanceCupGraph(String careerId, int year) {
        Integer completedGroups = jdbc.queryForObject("""
                SELECT COUNT(*) FROM career_competition_fixture
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = 'LCK_CUP' AND stage_id = 'GROUP_BATTLE'
                  AND lifecycle_status = 'COMPLETED'
                """, Integer.class, careerId, year);
        if (completedGroups != null && completedGroups == 25
                && countRows("career_lck_cup_standing", careerId, year) == 0) {
            sealCupGroupStandings(careerId, year);
        }
        if (countRows("career_lck_cup_standing", careerId, year) == 10) {
            deriveCupPlayoffSeeds(careerId, year);
            resolveCupFixtures(careerId, year);
        }
    }

    private int countRows(String table, String careerId, int year) {
        Integer count = jdbc.queryForObject(("SELECT COUNT(*) FROM " + table
                + " WHERE career_id = ? AND calendar_season_year = ?"),
                Integer.class, careerId, year);
        return count == null ? 0 : count;
    }

    private void sealCupGroupStandings(String careerId, int year) {
        Map<String, String> groups = new LinkedHashMap<>();
        jdbc.query("""
                SELECT seed_scope, team_code FROM career_competition_seed
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = 'LCK_CUP'
                  AND seed_scope IN ('CUP_GROUP_BARON', 'CUP_GROUP_ELDER')
                ORDER BY seed_scope, seed_number
                """, (RowCallbackHandler) result -> groups.put(result.getString(2),
                result.getString(1).substring("CUP_GROUP_".length())), careerId, year);
        if (groups.size() != 10) throw new IllegalStateException(
                "LCK_CUP_GROUP_MEMBERSHIP_INTEGRITY_FAILURE");
        LinkedHashMap<String, MutableCupStanding> standings = new LinkedHashMap<>();
        groups.forEach((team, group) -> standings.put(team,
                new MutableCupStanding(team, group)));
        List<CupGroupResultRow> results = jdbc.query("""
                SELECT f.match_id, f.first_team_code, f.second_team_code,
                       f.winner_team_code, f.group_point_value, f.series_format,
                       d.first_score, d.second_score, d.total_duration_seconds
                FROM career_competition_fixture f
                JOIN career_competition_result_detail d
                  ON d.career_id = f.career_id
                 AND d.calendar_season_year = f.calendar_season_year
                 AND d.competition_id = f.competition_id
                 AND d.match_id = f.match_id
                WHERE f.career_id = ? AND f.calendar_season_year = ?
                  AND f.competition_id = 'LCK_CUP'
                  AND f.stage_id = 'GROUP_BATTLE'
                ORDER BY f.match_order
                """, (result, row) -> new CupGroupResultRow(result.getString(1),
                result.getString(2), result.getString(3), result.getString(4),
                result.getInt(5), result.getString(6), result.getInt(7),
                result.getInt(8), result.getInt(9)), careerId, year);
        if (results.size() != 25) throw new IllegalStateException(
                "LCK_CUP_GROUP_RESULT_EVIDENCE_REQUIRED");
        LinkedHashMap<String, Integer> groupPoints = new LinkedHashMap<>();
        groupPoints.put("BARON", 0);
        groupPoints.put("ELDER", 0);
        HashSet<String> exposure = new HashSet<>();
        for (CupGroupResultRow result : results) {
            MutableCupStanding first = standings.get(result.firstTeam());
            MutableCupStanding second = standings.get(result.secondTeam());
            String exposureKey = result.firstTeam().compareTo(result.secondTeam()) < 0
                    ? result.firstTeam() + '|' + result.secondTeam()
                    : result.secondTeam() + '|' + result.firstTeam();
            if (first == null || second == null || first.group.equals(second.group)
                    || !exposure.add(exposureKey)
                    || result.pointValue() != ("BO5".equals(result.seriesFormat())
                    ? 2 : 1)) {
                throw new IllegalStateException(
                        "LCK_CUP_GROUP_EXPOSURE_INTEGRITY_FAILURE");
            }
            first.gameWins += result.firstScore();
            first.gameLosses += result.secondScore();
            second.gameWins += result.secondScore();
            second.gameLosses += result.firstScore();
            MutableCupStanding winner = standings.get(result.winnerTeam());
            if (winner != first && winner != second) throw new IllegalStateException(
                    "LCK_CUP_GROUP_WINNER_INTEGRITY_FAILURE");
            MutableCupStanding loser = winner == first ? second : first;
            winner.matchWins++;
            winner.winTime += result.durationSeconds();
            winner.defeated.add(loser.team);
            loser.matchLosses++;
            groupPoints.compute(winner.group,
                    (ignored, value) -> Objects.requireNonNull(value)
                            + result.pointValue());
        }
        if (exposure.size() != 25) throw new IllegalStateException(
                "LCK_CUP_GROUP_EXPOSURE_INTEGRITY_FAILURE");
        standings.values().forEach(value -> value.strength = value.defeated.stream()
                .mapToInt(team -> standings.get(team).matchWins).sum());
        Map<String, List<MutableCupStanding>> ordered = new LinkedHashMap<>();
        for (String group : List.of("BARON", "ELDER")) {
            List<MutableCupStanding> rows = standings.values().stream()
                    .filter(value -> value.group.equals(group))
                    .sorted(java.util.Comparator
                            .comparingInt((MutableCupStanding value) -> value.matchWins)
                            .reversed()
                            .thenComparing(java.util.Comparator.comparingInt(
                                    MutableCupStanding::gameDifferential).reversed())
                            .thenComparing(java.util.Comparator.comparingInt(
                                    (MutableCupStanding value) -> value.strength).reversed())
                            .thenComparingInt(value -> value.matchWins == 0
                                    ? Integer.MAX_VALUE : value.winTime))
                    .toList();
            if (rows.size() != 5 || unresolvedCupTie(rows)) {
                blockCup(careerId, year, "LCK_CUP_TIEBREAKER_REQUIRED");
                return;
            }
            ordered.put(group, rows);
        }
        int baronDiff = ordered.get("BARON").stream().mapToInt(
                MutableCupStanding::gameDifferential).sum();
        int elderDiff = ordered.get("ELDER").stream().mapToInt(
                MutableCupStanding::gameDifferential).sum();
        String winnerGroup;
        if (!groupPoints.get("BARON").equals(groupPoints.get("ELDER"))) {
            winnerGroup = groupPoints.get("BARON") > groupPoints.get("ELDER")
                    ? "BARON" : "ELDER";
        } else if (baronDiff != elderDiff) {
            winnerGroup = baronDiff > elderDiff ? "BARON" : "ELDER";
        } else {
            blockCup(careerId, year, "LCK_CUP_TIEBREAKER_REQUIRED");
            return;
        }
        String standingsHash = cupStandingsHash(careerId, year, groupPoints,
                ordered, winnerGroup);
        for (Map.Entry<String, List<MutableCupStanding>> group : ordered.entrySet()) {
            for (int index = 0; index < group.getValue().size(); index++) {
                MutableCupStanding row = group.getValue().get(index);
                jdbc.update("""
                        INSERT INTO career_lck_cup_standing(
                          career_id, calendar_season_year, group_id, group_rank,
                          team_code, match_wins, match_losses, game_wins, game_losses,
                          strength_of_victory, win_time_seconds, tie_break_trace,
                          standings_hash)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, careerId, year, group.getKey(), index + 1, row.team,
                        row.matchWins, row.matchLosses, row.gameWins, row.gameLosses,
                        row.strength, row.winTime,
                        "MATCH_WINS>GAME_DIFFERENTIAL>HEAD_TO_HEAD_NOT_APPLICABLE_"
                                + "CROSS_GROUP>STRENGTH_OF_VICTORY>WIN_TIME",
                        standingsHash);
            }
        }
        String loserGroup = winnerGroup.equals("BARON") ? "ELDER" : "BARON";
        List<MutableCupStanding> winner = ordered.get(winnerGroup);
        List<MutableCupStanding> loser = ordered.get(loserGroup);
        insertDerivedSeeds(careerId, year, "CUP_PLAYOFF_SEED",
                List.of(winner.get(0).team, winner.get(1).team, loser.get(0).team),
                standingsHash, 1);
        insertDerivedSeeds(careerId, year, "CUP_PLAY_IN_SEED",
                List.of(winner.get(2).team, loser.get(1).team, winner.get(3).team,
                        loser.get(2).team, winner.get(4).team, loser.get(3).team),
                standingsHash, 1);
        jdbc.update("""
                UPDATE career_competition_instance
                SET lifecycle_status = 'RUNNING', blocking_reason = NULL,
                    updated_at = ?
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = 'LCK_CUP'
                """, now(), careerId, year);
    }

    private static boolean unresolvedCupTie(List<MutableCupStanding> ordered) {
        for (int index = 1; index < ordered.size(); index++) {
            MutableCupStanding left = ordered.get(index - 1);
            MutableCupStanding right = ordered.get(index);
            if (left.matchWins == right.matchWins
                    && left.gameDifferential() == right.gameDifferential()
                    && left.strength == right.strength
                    && left.winTime == right.winTime) return true;
        }
        return false;
    }

    private void blockCup(String careerId, int year, String reason) {
        jdbc.update("""
                UPDATE career_competition_instance
                SET lifecycle_status = 'BLOCKED', blocking_reason = ?, updated_at = ?
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = 'LCK_CUP'
                """, reason, now(), careerId, year);
    }

    private String cupStandingsHash(
            String careerId, int year, Map<String, Integer> points,
            Map<String, List<MutableCupStanding>> ordered, String winnerGroup
    ) {
        StringBuilder canonical = new StringBuilder(
                "schema=CAREER_LCK_CUP_GROUP_STANDINGS_V1\n")
                .append("careerId=").append(careerId).append('\n')
                .append("calendarSeasonYear=").append(year).append('\n')
                .append("winnerGroup=").append(winnerGroup).append('\n');
        for (String group : List.of("BARON", "ELDER")) {
            canonical.append("groupPoints=").append(group).append('|')
                    .append(points.get(group)).append('\n');
            List<MutableCupStanding> rows = ordered.get(group);
            for (int index = 0; index < rows.size(); index++) {
                MutableCupStanding row = rows.get(index);
                canonical.append("standing=").append(group).append('|')
                        .append(index + 1).append('|').append(row.team).append('|')
                        .append(row.matchWins).append('|').append(row.matchLosses)
                        .append('|').append(row.gameWins).append('|')
                        .append(row.gameLosses).append('|').append(row.strength)
                        .append('|').append(row.winTime).append('\n');
            }
        }
        return CareerCompetitionRules.sha256(canonical.toString().getBytes(
                StandardCharsets.UTF_8));
    }

    private void insertDerivedSeeds(
            String careerId, int year, String scope, List<String> teams,
            String sourceHash, int firstSeed
    ) {
        for (int index = 0; index < teams.size(); index++) {
            jdbc.update("""
                    INSERT INTO career_competition_seed(
                      career_id, calendar_season_year, competition_id, seed_scope,
                      seed_number, team_code, imported_series_wins,
                      imported_series_losses, imported_game_wins,
                      imported_game_losses, source_input_hash)
                    VALUES (?, ?, 'LCK_CUP', ?, ?, ?, 0, 0, 0, 0, ?)
                    """, careerId, year, scope, firstSeed + index,
                    teams.get(index), sourceHash);
        }
    }

    private void deriveCupPlayoffSeeds(String careerId, int year) {
        Map<String, Integer> playInSeeds = seedByTeam(careerId, year,
                "CUP_PLAY_IN_SEED");
        Map<String, Outcome> outcomes = cupOutcomes(careerId, year);
        Outcome first = outcomes.get("PI_R2_M1");
        Outcome second = outcomes.get("PI_R2_M2");
        if (first != null && second != null
                && seedByNumber(careerId, year, "CUP_PLAYOFF_SEED").size() == 3) {
            List<String> winners = List.of(first.winner(), second.winner()).stream()
                    .sorted(java.util.Comparator.comparingInt(playInSeeds::get))
                    .toList();
            String source = CareerCompetitionRules.sha256(("schema=CUP_PLAY_IN_"
                    + "PLAYOFF_SEED_4_5_V1\nfirst=" + winners.get(0)
                    + "\nsecond=" + winners.get(1) + '\n').getBytes(
                    StandardCharsets.UTF_8));
            insertDerivedSeeds(careerId, year, "CUP_PLAYOFF_SEED", winners,
                    source, 4);
        }
        Outcome finalResult = outcomes.get("PI_FINAL");
        if (finalResult != null
                && seedByNumber(careerId, year, "CUP_PLAYOFF_SEED").size() == 5) {
            String source = CareerCompetitionRules.sha256((
                    "schema=CUP_PLAY_IN_PLAYOFF_SEED_6_V1\nwinner="
                            + finalResult.winner() + '\n').getBytes(
                    StandardCharsets.UTF_8));
            insertDerivedSeeds(careerId, year, "CUP_PLAYOFF_SEED",
                    List.of(finalResult.winner()), source, 6);
        }
    }

    private void resolveCupFixtures(String careerId, int year) {
        Map<Integer, String> playIn = seedByNumber(careerId, year,
                "CUP_PLAY_IN_SEED");
        Map<Integer, String> playoff = seedByNumber(careerId, year,
                "CUP_PLAYOFF_SEED");
        Map<String, Integer> allSeeds = new LinkedHashMap<>(seedByTeam(careerId,
                year, "CUP_PLAY_IN_SEED"));
        allSeeds.putAll(seedByTeam(careerId, year, "CUP_PLAYOFF_SEED"));
        Map<String, Outcome> outcomes = cupOutcomes(careerId, year);
        List<PendingCupFixture> fixtures = jdbc.query("""
                SELECT match_id, first_selector_type, first_selector_value,
                       second_selector_type, second_selector_value,
                       first_team_code, second_team_code, opponent_choice_policy
                FROM career_competition_fixture
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = 'LCK_CUP'
                  AND lifecycle_status = 'WAITING_FOR_PREDECESSOR'
                ORDER BY match_order
                """, (result, row) -> new PendingCupFixture(result.getString(1),
                result.getString(2), result.getString(3), result.getString(4),
                result.getString(5), result.getString(6), result.getString(7),
                result.getString(8)), careerId, year);
        Map<String, String> choices = existingChoices(careerId, year);
        for (PendingCupFixture fixture : fixtures) {
            String first = fixture.firstTeam() == null ? resolveCupSelector(careerId,
                    year, fixture.matchId(), fixture.firstType(), fixture.firstValue(),
                    playIn, playoff, allSeeds, outcomes, choices, null)
                    : fixture.firstTeam();
            String second = fixture.secondTeam() == null ? resolveCupSelector(careerId,
                    year, fixture.matchId(), fixture.secondType(), fixture.secondValue(),
                    playIn, playoff, allSeeds, outcomes, choices, first)
                    : fixture.secondTeam();
            if (first != null && second != null && !first.equals(second)) {
                String mode = Set.of(first, second).contains(
                        careerBinding(careerId).managedTeamCode())
                        ? "PLAYER_CONTROLLED" : "FULL_AUTO";
                jdbc.update("""
                        UPDATE career_competition_fixture
                        SET first_team_code = ?, second_team_code = ?,
                            execution_mode = ?, lifecycle_status = 'READY',
                            revision = revision + 1
                        WHERE career_id = ? AND calendar_season_year = ?
                          AND competition_id = 'LCK_CUP' AND match_id = ?
                          AND lifecycle_status = 'WAITING_FOR_PREDECESSOR'
                        """, first, second, mode, careerId, year, fixture.matchId());
            }
        }
        if (playoff.size() == 6) {
            recordFixedChoice(careerId, year, "PO_UBR1_M1", playoff.get(3),
                    List.of(seed(playoff, 4), seed(playoff, 5), seed(playoff, 6)));
        }
    }

    private String resolveCupSelector(
            String careerId, int year, String targetMatch, String type, String value,
            Map<Integer, String> playIn, Map<Integer, String> playoff,
            Map<String, Integer> allSeeds, Map<String, Outcome> outcomes,
            Map<String, String> choices, String choiceOwner
    ) {
        if ("CUP_PLAY_IN_SEED".equals(type)) return playIn.get(Integer.parseInt(value));
        if ("CUP_PLAYOFF_SEED".equals(type)) return playoff.get(Integer.parseInt(value));
        if ("MATCH_WINNER".equals(type)) return outcome(outcomes, value, true);
        if ("MATCH_LOSER".equals(type)) return outcome(outcomes, value, false);
        if ("LOWEST_AVAILABLE_SEED_MATCH_WINNER".equals(type)) {
            List<CareerCompetitionAggregate.SeededTeam> candidates = matchValues(value)
                    .stream().map(outcomes::get).filter(Objects::nonNull)
                    .map(Outcome::winner).map(team -> seed(allSeeds, team)).toList();
            if (candidates.size() != matchValues(value).size() || choiceOwner == null) {
                return null;
            }
            CareerCompetitionRules.OpponentChoiceReceipt choice =
                    rules.chooseCupOpponent(choiceOwner, candidates);
            storeChoice(careerId, year, targetMatch, choice);
            choices.put(value, choice.chosenTeamCode());
            return choice.chosenTeamCode();
        }
        if ("REMAINING_MATCH_WINNER".equals(type)) {
            String chosen = choices.get(value);
            if (chosen == null) return null;
            return matchValues(value).stream().map(outcomes::get)
                    .filter(Objects::nonNull).map(Outcome::winner)
                    .filter(team -> !team.equals(chosen)).findFirst().orElse(null);
        }
        if ("HIGHER_PLAYOFF_SEED_MATCH_LOSER".equals(type)
                || "LOWER_PLAYOFF_SEED_MATCH_LOSER".equals(type)) {
            List<String> losers = matchValues(value).stream().map(outcomes::get)
                    .filter(Objects::nonNull).map(Outcome::loser).toList();
            if (losers.size() != matchValues(value).size()) return null;
            java.util.Comparator<String> comparator =
                    java.util.Comparator.comparingInt(allSeeds::get);
            return ("HIGHER_PLAYOFF_SEED_MATCH_LOSER".equals(type)
                    ? losers.stream().min(comparator) : losers.stream().max(comparator))
                    .orElse(null);
        }
        return null;
    }

    private void recordFixedChoice(
            String careerId, int year, String matchId, String owner,
            List<CareerCompetitionAggregate.SeededTeam> eligible
    ) {
        if (owner == null || eligible.stream().anyMatch(value ->
                value.teamCode() == null)) return;
        storeChoice(careerId, year, matchId,
                rules.chooseCupOpponent(owner, eligible));
    }

    private void storeChoice(
            String careerId, int year, String matchId,
            CareerCompetitionRules.OpponentChoiceReceipt choice
    ) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM career_competition_opponent_choice
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = 'LCK_CUP' AND match_id = ?
                """, Integer.class, careerId, year, matchId);
        if (count != null && count > 0) return;
        jdbc.update("""
                INSERT INTO career_competition_opponent_choice(
                  choice_hash, career_id, calendar_season_year, competition_id,
                  match_id, choice_owner_team_code, eligible_seed_order,
                  chosen_team_code, policy_id, policy_hash, created_at)
                VALUES (?, ?, ?, 'LCK_CUP', ?, ?, ?, ?, ?, ?, ?)
                """, choice.receiptHash(), careerId, year, matchId,
                choice.choiceOwnerTeamCode(), String.join(",",
                        choice.canonicalEligibleOrder()), choice.chosenTeamCode(),
                choice.policyId(), choice.policyHash(), now());
    }

    private Map<String, String> existingChoices(String careerId, int year) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        jdbc.query("""
                SELECT f.second_selector_value, c.chosen_team_code
                FROM career_competition_opponent_choice c
                JOIN career_competition_fixture f
                  ON f.career_id = c.career_id
                 AND f.calendar_season_year = c.calendar_season_year
                 AND f.competition_id = c.competition_id
                 AND f.match_id = c.match_id
                WHERE c.career_id = ? AND c.calendar_season_year = ?
                  AND c.competition_id = 'LCK_CUP'
                  AND f.second_selector_type = 'LOWEST_AVAILABLE_SEED_MATCH_WINNER'
                """, (RowCallbackHandler) result -> values.put(
                result.getString(1), result.getString(2)), careerId, year);
        return values;
    }

    private Map<Integer, String> seedByNumber(
            String careerId, int year, String scope
    ) {
        LinkedHashMap<Integer, String> values = new LinkedHashMap<>();
        jdbc.query("""
                SELECT seed_number, team_code FROM career_competition_seed
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = 'LCK_CUP' AND seed_scope = ?
                ORDER BY seed_number
                """, (RowCallbackHandler) result -> values.put(result.getInt(1),
                result.getString(2)), careerId, year, scope);
        return values;
    }

    private Map<String, Integer> seedByTeam(
            String careerId, int year, String scope
    ) {
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        seedByNumber(careerId, year, scope).forEach((seed, team) ->
                values.put(team, seed));
        return values;
    }

    private Map<String, Outcome> cupOutcomes(String careerId, int year) {
        LinkedHashMap<String, Outcome> values = new LinkedHashMap<>();
        jdbc.query("""
                SELECT match_id, winner_team_code, loser_team_code
                FROM career_competition_fixture
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = 'LCK_CUP'
                  AND lifecycle_status = 'COMPLETED'
                ORDER BY match_order
                """, (RowCallbackHandler) result -> values.put(result.getString(1),
                new Outcome(result.getString(2), result.getString(3))), careerId,
                year);
        return values;
    }

    private static String outcome(Map<String, Outcome> outcomes, String match,
                                  boolean winner) {
        Outcome value = outcomes.get(match);
        return value == null ? null : winner ? value.winner() : value.loser();
    }

    private static List<String> matchValues(String value) {
        return List.of(value.split(","));
    }

    private static CareerCompetitionAggregate.SeededTeam seed(
            Map<Integer, String> seeds, int number
    ) {
        return new CareerCompetitionAggregate.SeededTeam(number, seeds.get(number),
                0, 0, 0, 0);
    }

    private static CareerCompetitionAggregate.SeededTeam seed(
            Map<String, Integer> seeds, String team
    ) {
        Integer number = seeds.get(team);
        if (number == null) throw new IllegalStateException(
                "LCK_CUP_TEAM_SEED_NOT_FOUND");
        return new CareerCompetitionAggregate.SeededTeam(number, team, 0, 0, 0, 0);
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("COMPETITION_RECEIPT_JSON_FAILED", failure);
        }
    }

    private void materializeLckPlayInWhenReady(String careerId, int year) {
        Integer completed = jdbc.queryForObject("""
                SELECT COUNT(*) FROM career_competition_fixture
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = 'LCK_REGULAR_R3_R4'
                  AND lifecycle_status = 'COMPLETED'
                """, Integer.class, careerId, year);
        if (completed == null || completed != 40) return;
        Integer evidence = jdbc.queryForObject("""
                SELECT COUNT(*) FROM career_competition_result_detail
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = 'LCK_REGULAR_R3_R4'
                """, Integer.class, careerId, year);
        if (evidence == null || evidence != 40) throw new IllegalStateException(
                "R3_R4_VERIFIED_RESULT_EVIDENCE_REQUIRED");
        Integer existing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM career_competition_fixture
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = 'LCK_PLAY_IN'
                """, Integer.class, careerId, year);
        if (existing != null && existing > 0) return;

        LinkedHashMap<String, MutableStageStanding> standings = new LinkedHashMap<>();
        jdbc.query("""
                SELECT seed_number, team_code, imported_series_wins,
                       imported_series_losses, imported_game_wins,
                       imported_game_losses
                FROM career_competition_seed
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = 'LCK_REGULAR_R3_R4'
                  AND seed_scope = 'R1_R2_FINAL_RANK'
                ORDER BY seed_number
                """, (RowCallbackHandler) result -> {
            int originalRank = result.getInt(1);
            standings.put(result.getString(2), new MutableStageStanding(
                    result.getString(2), originalRank <= 5 ? "LEGEND" : "RISE",
                    result.getInt(3), result.getInt(4), result.getInt(5),
                    result.getInt(6)));
        }, careerId, year);
        if (standings.size() != 10) throw new IllegalStateException(
                "R3_R4_CARRIED_STANDINGS_REQUIRED");

        List<StageResultRow> results = jdbc.query("""
                SELECT f.first_team_code, f.second_team_code, f.winner_team_code,
                       d.first_score, d.second_score
                FROM career_competition_fixture f
                JOIN career_competition_result_detail d
                  ON d.career_id = f.career_id
                 AND d.calendar_season_year = f.calendar_season_year
                 AND d.competition_id = f.competition_id
                 AND d.match_id = f.match_id
                WHERE f.career_id = ? AND f.calendar_season_year = ?
                  AND f.competition_id = 'LCK_REGULAR_R3_R4'
                ORDER BY f.match_order, f.match_id
                """, (result, row) -> new StageResultRow(result.getString(1),
                result.getString(2), result.getString(3), result.getInt(4),
                result.getInt(5)), careerId, year);
        if (results.size() != 40) throw new IllegalStateException(
                "R3_R4_VERIFIED_RESULT_EVIDENCE_REQUIRED");
        for (StageResultRow result : results) {
            MutableStageStanding first = standings.get(result.firstTeam());
            MutableStageStanding second = standings.get(result.secondTeam());
            if (first == null || second == null || !first.group.equals(second.group)
                    || !java.util.Set.of(first.team, second.team).contains(
                    result.winnerTeam())) {
                throw new IllegalStateException("R3_R4_RESULT_SCOPE_MISMATCH");
            }
            first.gameWins += result.firstScore();
            first.gameLosses += result.secondScore();
            second.gameWins += result.secondScore();
            second.gameLosses += result.firstScore();
            MutableStageStanding winner = result.winnerTeam().equals(first.team)
                    ? first : second;
            MutableStageStanding loser = winner == first ? second : first;
            winner.seriesWins++;
            loser.seriesLosses++;
        }

        LinkedHashMap<String, List<MutableStageStanding>> ordered =
                new LinkedHashMap<>();
        for (String group : List.of("LEGEND", "RISE")) {
            List<MutableStageStanding> rows = standings.values().stream()
                    .filter(value -> value.group.equals(group))
                    .sorted(java.util.Comparator
                            .comparingInt((MutableStageStanding value) ->
                                    value.seriesWins).reversed()
                            .thenComparing(java.util.Comparator.comparingInt(
                                    MutableStageStanding::gameDifferential).reversed())
                            .thenComparing(java.util.Comparator.comparingInt(
                                    (MutableStageStanding value) -> value.gameWins)
                                    .reversed()))
                    .toList();
            if (rows.size() != 5 || unresolvedStageTie(rows)) {
                blockInstance(careerId, year, "LCK_REGULAR_R3_R4",
                        "LCK_R3_R4_TIEBREAKER_REQUIRED");
                return;
            }
            ordered.put(group, rows);
        }
        String standingsHash = r3r4StandingsHash(careerId, year, ordered);
        List<MutableStageStanding> legend = ordered.get("LEGEND");
        List<MutableStageStanding> rise = ordered.get("RISE");

        insertTransitionOutput(careerId, year, "LCK_REGULAR_R3_R4",
                "LCK_PLAYOFF_SEED_1", legend.get(0).team);
        insertTransitionOutput(careerId, year, "LCK_REGULAR_R3_R4",
                "LCK_PLAYOFF_SEED_2", legend.get(1).team);
        insertTransitionOutput(careerId, year, "LCK_REGULAR_R3_R4",
                "LCK_PLAYOFF_SEED_3", legend.get(2).team);
        insertTransitionOutput(careerId, year, "LCK_REGULAR_R3_R4",
                "LCK_PLAYOFF_SEED_4", legend.get(3).team);
        insertTransitionOutput(careerId, year, "LCK_REGULAR_R3_R4",
                "LCK_SEASON_PLACE_9", rise.get(3).team);
        insertTransitionOutput(careerId, year, "LCK_REGULAR_R3_R4",
                "LCK_SEASON_PLACE_10", rise.get(4).team);

        List<CareerCompetitionAggregate.SeededTeam> playInSeeds = List.of(
                stageSeed(1, legend.get(4)), stageSeed(2, rise.get(0)),
                stageSeed(3, rise.get(1)), stageSeed(4, rise.get(2)));
        insertSeeds(careerId, year, "LCK_PLAY_IN", "PLAY_IN_SEED",
                standingsHash, playInSeeds);
        CareerBinding career = careerBinding(careerId);
        CareerCompetitionAggregate aggregate = CareerCompetitionAggregate.materialize(
                rules, careerId, year, "LCK_PLAY_IN", career.managedTeamCode(),
                career.rootSeed(), standingsHash, playInSeeds);
        Map<String, CareerCompetitionRules.MatchRule> matchRules =
                rules.rule("LCK_PLAY_IN").matches().stream().collect(
                        java.util.stream.Collectors.toUnmodifiableMap(
                                CareerCompetitionRules.MatchRule::matchId,
                                value -> value));
        for (CareerCompetitionAggregate.Fixture fixture : aggregate.fixtures()) {
            CareerCompetitionRules.MatchRule match = matchRules.get(fixture.matchId());
            insertFixture(careerId, year, "LCK_PLAY_IN", fixture,
                    match.stageId(), match.matchOrder(), match.groupId(),
                    match.groupPointValue(), match.selectionRightOwner(),
                    match.opponentChoicePolicy(), match.sideSelectionPolicy(),
                    match.scheduleStatus());
        }
        OffsetDateTime transitionTime = now();
        updateInstance(careerId, year, "LCK_PLAY_IN", "READY", null,
                standingsHash, aggregate.revision(), "0".repeat(64), transitionTime);
        setInstanceMaterializationAuthority(careerId, year, "LCK_PLAY_IN",
                "SEALED_R3_R4_STANDINGS_PLAY_IN_GRAPH_V1", standingsHash,
                transitionTime);
        refreshInstanceHash(careerId, year, "LCK_PLAY_IN");
    }

    private static CareerCompetitionAggregate.SeededTeam stageSeed(
            int seed, MutableStageStanding value
    ) {
        return new CareerCompetitionAggregate.SeededTeam(seed, value.team,
                value.seriesWins, value.seriesLosses, value.gameWins,
                value.gameLosses);
    }

    private static boolean unresolvedStageTie(
            List<MutableStageStanding> ordered
    ) {
        for (int index = 1; index < ordered.size(); index++) {
            MutableStageStanding first = ordered.get(index - 1);
            MutableStageStanding second = ordered.get(index);
            if (first.seriesWins == second.seriesWins
                    && first.gameDifferential() == second.gameDifferential()
                    && first.gameWins == second.gameWins) return true;
        }
        return false;
    }

    private static String r3r4StandingsHash(
            String careerId, int year,
            Map<String, List<MutableStageStanding>> ordered
    ) {
        StringBuilder canonical = new StringBuilder(
                "schema=CAREER_LCK_R3_R4_FINAL_STANDINGS_V1\n")
                .append("careerId=").append(careerId).append('\n')
                .append("calendarSeasonYear=").append(year).append('\n')
                .append("recordCarry=R1_R2_SERIES_AND_GAMES\n")
                .append("ranking=SERIES_WINS>GAME_DIFFERENTIAL>GAME_WINS>"
                        + "OFFICIAL_TIEBREAKER_REQUIRED\n");
        for (String group : List.of("LEGEND", "RISE")) {
            List<MutableStageStanding> rows = ordered.get(group);
            for (int index = 0; index < rows.size(); index++) {
                MutableStageStanding value = rows.get(index);
                canonical.append("standing=").append(group).append('|')
                        .append(index + 1).append('|').append(value.team).append('|')
                        .append(value.seriesWins).append('|')
                        .append(value.seriesLosses).append('|')
                        .append(value.gameWins).append('|')
                        .append(value.gameLosses).append('\n');
            }
        }
        return CareerCompetitionRules.sha256(canonical.toString().getBytes(
                StandardCharsets.UTF_8));
    }

    private void insertTransitionOutput(
            String careerId, int year, String competitionId, String outputId,
            String teamCode
    ) {
        List<OutputRow> prior = jdbc.query("""
                SELECT competition_id, output_id, team_code
                FROM career_competition_output
                WHERE career_id = ? AND calendar_season_year = ? AND output_id = ?
                """, (result, row) -> new OutputRow(result.getString(1),
                result.getString(2), result.getString(3)), careerId, year, outputId);
        if (!prior.isEmpty()) {
            OutputRow value = prior.getFirst();
            if (!competitionId.equals(value.competitionId())
                    || !teamCode.equals(value.teamCode())) {
                throw new IllegalStateException("COMPETITION_OUTPUT_CONFLICT");
            }
            return;
        }
        List<OutcomeSource> sources = jdbc.query("""
                SELECT match_id, completion_receipt_hash
                FROM career_competition_fixture
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = ? AND lifecycle_status = 'COMPLETED'
                ORDER BY match_order DESC, match_id DESC
                LIMIT 1
                """, (result, row) -> new OutcomeSource(result.getString(1),
                result.getString(2)), careerId, year, competitionId);
        if (sources.size() != 1 || sources.getFirst().receiptHash() == null) {
            throw new IllegalStateException("COMPETITION_OUTPUT_SOURCE_REQUIRED");
        }
        OutcomeSource source = sources.getFirst();
        jdbc.update("""
                INSERT INTO career_competition_output(
                  career_id, calendar_season_year, competition_id, output_id,
                  team_code, source_match_id, source_receipt_hash, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, careerId, year, competitionId, outputId, teamCode,
                source.matchId(), source.receiptHash(), now());
    }

    private void blockInstance(
            String careerId, int year, String competitionId, String reason
    ) {
        jdbc.update("""
                UPDATE career_competition_instance
                SET lifecycle_status = 'BLOCKED', blocking_reason = ?, updated_at = ?
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = ?
                """, reason, now(), careerId, year, competitionId);
    }

    CompletionResult applyCompletionForTesting(
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
        return applyCompletionState(careerId, seasonYear, competitionId, matchId,
                seriesId, firstTeamCode, secondTeamCode, winnerTeamCode,
                receiptHash);
    }

    private CompletionResult applyCompletionState(
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
            requireV2(cycle);
            validateCycle(cycle, loadInstances(careerId, seasonYear));
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
                    next.sourceInputHash(), next.revision(), "0".repeat(64), now());
            refreshInstanceHash(careerId, seasonYear, competitionId);
            long cycleRevision = cycle.revision() + 1;
            int cycleUpdated = jdbc.update("""
                    UPDATE career_competition_cycle
                    SET revision = ?, state_hash = ?, updated_at = ?
                    WHERE career_id = ? AND calendar_season_year = ? AND revision = ?
                    """, cycleRevision, "0".repeat(64), now(), careerId, seasonYear,
                    cycle.revision());
            if (cycleUpdated != 1) throw new IllegalStateException(
                    "CAREER_COMPETITION_CYCLE_CAS_FAILED");
            refreshCycleHash(careerId, seasonYear);
            return new CompletionResult(loadAggregate(careerId, seasonYear,
                    competitionId), false);
        });
    }

    public CareerCompetitionAggregate loadAggregate(
            String careerId, int seasonYear, String competitionId
    ) {
        InstanceRow instance = instance(careerId, seasonYear, competitionId);
        if (instance.sourceInputHash() == null) throw new IllegalStateException(
                "COMPETITION_NOT_MATERIALIZED");
        if (!INSTANCE_HASH_ALGORITHM.equals(instance.hashAlgorithm())) {
            throw new IllegalStateException("COMPETITION_STATE_MIGRATION_REQUIRED");
        }
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
                  AND competition_id = ? ORDER BY match_order, match_id
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
        String certifiedHash = instanceHashV2(careerId, seasonYear, instance);
        if (!certifiedHash.equals(instance.stateHash())) {
            throw new IllegalStateException("COMPETITION_INSTANCE_INTEGRITY_FAILURE");
        }
        return new CareerCompetitionAggregate(careerId, seasonYear, competitionId,
                managed, rootSeed, instance.sourceInputHash(), instance.revision(),
                fixtures, outputs, certifiedHash);
    }

    private CycleView validateAndView(CycleRow cycle) {
        List<InstanceRow> instances = jdbc.query("""
                SELECT competition_id, rule_status, lifecycle_status, blocking_reason,
                       source_input_hash, revision, state_hash, hash_algorithm,
                       materialization_policy_id, materialization_receipt_hash
                FROM career_competition_instance
                WHERE career_id = ? AND calendar_season_year = ?
                ORDER BY CASE competition_id
                  WHEN 'LCK_CUP' THEN 1 WHEN 'FIRST_STAND' THEN 2
                  WHEN 'LCK_REGULAR_R1_R2' THEN 3 WHEN 'LCK_ROAD_TO_MSI' THEN 4
                  WHEN 'MSI' THEN 5 WHEN 'EWC_LOL' THEN 6
                  WHEN 'LCK_REGULAR_R3_R4' THEN 7 WHEN 'LCK_PLAY_IN' THEN 8
                  WHEN 'LCK_PLAYOFFS' THEN 9 WHEN 'ASIAN_GAMES_LOL_RELEASE' THEN 10
                  WHEN 'WORLDS' THEN 11 WHEN 'KESPA_CUP' THEN 12 ELSE 99 END
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
                    || !INSTANCE_HASH_ALGORITHM.equals(value.hashAlgorithm())
                    || !instanceHashV2(cycle.careerId(), cycle.seasonYear(), value)
                    .equals(value.stateHash())) {
                throw new IllegalStateException("COMPETITION_INSTANCE_INTEGRITY_FAILURE");
            }
        });
        validateCycle(cycle, instances);
        List<FixtureRow> fixtures = jdbc.query("""
                SELECT competition_id, match_id, fixture_id, series_id, scheduled_date,
                       schedule_status, series_format, hard_fearless, first_team_code,
                       second_team_code, execution_mode, lifecycle_status,
                       fixture_root_seed, completion_receipt_hash, stage_id, match_order,
                       first_selector_type, first_selector_value, second_selector_type,
                       second_selector_value, winner_team_code, loser_team_code,
                       group_id, group_point_value, selection_right_owner,
                       opponent_choice_policy, side_selection_policy
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
                cycle.hashAlgorithm(), cycle.seasonOrdinal(),
                cycle.initializationPolicyId(), cycle.initializationInputHash(),
                List.copyOf(instances), List.copyOf(fixtures), List.copyOf(outputs));
    }

    private List<CycleRow> findCycle(String careerId, int year, boolean lock) {
        return jdbc.query("""
                SELECT career_id, calendar_season_year, cycle_schema, rule_version,
                       rule_resource_hash, game_policy_version, projection_policy,
                       r3_r4_allocation_policy, lifecycle_status, blocking_reason,
                       r1_r2_import_hash, r1_r2_standings_revision, revision, state_hash,
                       hash_algorithm, season_ordinal, initialization_policy_id,
                       initialization_input_hash
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

    private void validateCycle(CycleRow value, List<InstanceRow> instances) {
        if (!CYCLE_SCHEMA.equals(value.cycleSchema())
                || !CareerCompetitionRules.VERSION.equals(value.ruleVersion())
                || !rules.resourceHash().equals(value.ruleResourceHash())
                || !CareerCompetitionRules.GAME_POLICY_VERSION.equals(
                value.gamePolicyVersion())
                || !CareerCompetitionRules.PROJECTION_POLICY.equals(
                value.projectionPolicy())
                || !CareerCompetitionRules.R3_R4_ALLOCATION_POLICY.equals(
                value.r3r4AllocationPolicy())
                || !CYCLE_HASH_ALGORITHM.equals(value.hashAlgorithm())
                || !cycleHashV2(value, instances).equals(value.stateHash())) {
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
            CareerCompetitionAggregate.Fixture value, String stageId, int matchOrder,
            String groupId, Integer groupPointValue, String selectionRightOwner,
            String opponentChoicePolicy, String sideSelectionPolicy,
            String scheduleStatus
    ) {
        jdbc.update("""
                INSERT INTO career_competition_fixture(
                  career_id, calendar_season_year, competition_id, match_id,
                  fixture_id, series_id, scheduled_date, schedule_status,
                  series_format, hard_fearless, first_selector_type,
                  first_selector_value, second_selector_type, second_selector_value,
                  first_team_code, second_team_code, execution_mode, fixture_root_seed,
                  seed_algorithm, lifecycle_status, winner_output_ids, loser_output_ids,
                  winner_team_code, loser_team_code, completion_receipt_hash, revision,
                  stage_id, match_order, group_id, group_point_value,
                  selection_right_owner, opponent_choice_policy, side_selection_policy)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                  ?, ?, NULL, NULL, NULL, 0, ?, ?, ?, ?, ?, ?, ?)
                """, careerId, year, competitionId, value.matchId(), value.fixtureId(),
                value.seriesId(), value.date(), scheduleStatus, value.seriesFormat(),
                value.hardFearless(), value.firstSelector().type(),
                value.firstSelector().value(), value.secondSelector().type(),
                value.secondSelector().value(), value.firstTeamCode(),
                value.secondTeamCode(), value.executionMode(), value.rootSeed(),
                CareerCompetitionAggregate.SEED_ALGORITHM, value.lifecycleStatus(),
                String.join(",", value.winnerOutputs()),
                String.join(",", value.loserOutputs()), stageId, matchOrder, groupId,
                groupPointValue, selectionRightOwner, opponentChoicePolicy,
                sideSelectionPolicy);
    }

    private void insertR3R4Fixture(
            String careerId, int year, CareerCompetitionAggregate.R3R4Fixture value,
            List<CareerCompetitionAggregate.SeededTeam> ranking, int matchOrder
    ) {
        Map<String, Integer> ranks = ranking.stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                        CareerCompetitionAggregate.SeededTeam::teamCode,
                        CareerCompetitionAggregate.SeededTeam::seed));
        CareerCompetitionAggregate.Fixture fixture = new CareerCompetitionAggregate.Fixture(
                value.matchId(), value.fixtureId(), value.date(), value.seriesFormat(),
                value.hardFearless(), new CareerCompetitionRules.ParticipantSelector(
                "R1_R2_RANK", Integer.toString(ranks.get(value.firstTeamCode()))),
                new CareerCompetitionRules.ParticipantSelector(
                        "R1_R2_RANK", Integer.toString(ranks.get(value.secondTeamCode()))),
                value.firstTeamCode(),
                value.secondTeamCode(), "READY", value.executionMode(), value.rootSeed(),
                value.seriesId(), List.of(), List.of(), null, null, null);
        insertFixture(careerId, year, "LCK_REGULAR_R3_R4", fixture,
                value.groupId(), matchOrder, value.groupId(), null, null, null, null,
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

    private void setInstanceMaterializationAuthority(
            String careerId, int year, String competitionId, String policyId,
            String receiptHash, OffsetDateTime updatedAt
    ) {
        CareerIdentity.requireSha256(receiptHash, "materializationReceiptHash");
        int updated = jdbc.update("""
                UPDATE career_competition_instance
                SET materialization_policy_id = ?, materialization_receipt_hash = ?,
                    updated_at = ?
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = ?
                """, policyId, receiptHash, updatedAt, careerId, year,
                competitionId);
        if (updated != 1) throw new IllegalStateException(
                "CAREER_COMPETITION_INSTANCE_NOT_FOUND");
    }

    private InstanceRow instance(String careerId, int year, String competitionId) {
        List<InstanceRow> values = jdbc.query("""
                SELECT competition_id, rule_status, lifecycle_status, blocking_reason,
                       source_input_hash, revision, state_hash, hash_algorithm,
                       materialization_policy_id, materialization_receipt_hash
                FROM career_competition_instance
                WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ?
                """, (result, row) -> instance(result), careerId, year, competitionId);
        if (values.size() != 1) throw new IllegalStateException(
                "COMPETITION_INSTANCE_NOT_FOUND");
        return values.getFirst();
    }

    private List<InstanceRow> loadInstances(String careerId, int year) {
        return jdbc.query("""
                SELECT competition_id, rule_status, lifecycle_status, blocking_reason,
                       source_input_hash, revision, state_hash, hash_algorithm,
                       materialization_policy_id, materialization_receipt_hash
                FROM career_competition_instance
                WHERE career_id = ? AND calendar_season_year = ?
                ORDER BY CASE competition_id
                  WHEN 'LCK_CUP' THEN 1 WHEN 'FIRST_STAND' THEN 2
                  WHEN 'LCK_REGULAR_R1_R2' THEN 3 WHEN 'LCK_ROAD_TO_MSI' THEN 4
                  WHEN 'MSI' THEN 5 WHEN 'EWC_LOL' THEN 6
                  WHEN 'LCK_REGULAR_R3_R4' THEN 7 WHEN 'LCK_PLAY_IN' THEN 8
                  WHEN 'LCK_PLAYOFFS' THEN 9 WHEN 'ASIAN_GAMES_LOL_RELEASE' THEN 10
                  WHEN 'WORLDS' THEN 11 WHEN 'KESPA_CUP' THEN 12 ELSE 99 END
                """, (result, row) -> instance(result), careerId, year);
    }

    private void refreshAllInstanceHashes(String careerId, int year) {
        for (InstanceRow value : loadInstances(careerId, year)) {
            refreshInstanceHash(careerId, year, value.competitionId());
        }
    }

    private void refreshInstanceHash(String careerId, int year, String competitionId) {
        InstanceRow value = instance(careerId, year, competitionId);
        String hash = instanceHashV2(careerId, year, value);
        int updated = jdbc.update("""
                UPDATE career_competition_instance SET state_hash = ?, updated_at = ?
                WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ?
                """, hash, now(), careerId, year, competitionId);
        if (updated != 1) throw new IllegalStateException(
                "CAREER_COMPETITION_INSTANCE_NOT_FOUND");
    }

    private void refreshCycleHash(String careerId, int year) {
        CycleRow cycle = lockCycle(careerId, year);
        String hash = cycleHashV2(cycle, loadInstances(careerId, year));
        int updated = jdbc.update("""
                UPDATE career_competition_cycle SET state_hash = ?, updated_at = ?
                WHERE career_id = ? AND calendar_season_year = ?
                """, hash, now(), careerId, year);
        if (updated != 1) throw new IllegalStateException(
                "CAREER_COMPETITION_CYCLE_NOT_FOUND");
    }

    private String instanceHashV2(String careerId, int year, InstanceRow instance) {
        StringBuilder canonical = new StringBuilder();
        field(canonical, "schema", "CAREER_COMPETITION_INSTANCE_CANONICAL_V2");
        field(canonical, "hashAlgorithm", INSTANCE_HASH_ALGORITHM);
        field(canonical, "careerId", careerId);
        field(canonical, "calendarSeasonYear", year);
        field(canonical, "competitionId", instance.competitionId());
        field(canonical, "ruleVersion", CareerCompetitionRules.VERSION);
        field(canonical, "ruleResourceHash", rules.resourceHash());
        field(canonical, "gamePolicyVersion", CareerCompetitionRules.GAME_POLICY_VERSION);
        field(canonical, "ruleStatus", instance.ruleStatus());
        field(canonical, "materializationPolicyId", instance.materializationPolicyId());
        field(canonical, "materializationReceiptHash",
                instance.materializationReceiptHash());
        field(canonical, "sourceInputHash", instance.sourceInputHash());
        field(canonical, "lifecycleStatus", instance.lifecycleStatus());
        field(canonical, "blockingReason", instance.blockingReason());
        field(canonical, "revision", instance.revision());

        List<String> seeds = jdbc.query("""
                SELECT seed_scope, seed_number, team_code, imported_series_wins,
                       imported_series_losses, imported_game_wins,
                       imported_game_losses, source_input_hash
                FROM career_competition_seed
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = ?
                ORDER BY seed_scope, seed_number, team_code
                """, (result, row) -> row("seed", result.getString(1),
                result.getInt(2), result.getString(3), result.getInt(4),
                result.getInt(5), result.getInt(6), result.getInt(7),
                result.getString(8)), careerId, year, instance.competitionId());
        rows(canonical, "seed", seeds);

        List<String> fixtures = jdbc.query("""
                SELECT stage_id, match_order, match_id, fixture_id, series_id,
                       scheduled_date, schedule_status, series_format, hard_fearless,
                       first_selector_type, first_selector_value, second_selector_type,
                       second_selector_value, first_team_code, second_team_code,
                       execution_mode, fixture_root_seed, seed_algorithm,
                       lifecycle_status, winner_output_ids, loser_output_ids,
                       winner_team_code, loser_team_code, completion_receipt_hash,
                       revision, group_id, group_point_value, selection_right_owner,
                       opponent_choice_policy, side_selection_policy
                FROM career_competition_fixture
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = ?
                ORDER BY match_order, match_id
                """, (result, ignored) -> row("fixture", result.getString(1),
                result.getInt(2), result.getString(3), result.getString(4),
                result.getString(5), result.getObject(6, LocalDate.class),
                result.getString(7), result.getString(8), result.getBoolean(9),
                result.getString(10), result.getString(11), result.getString(12),
                result.getString(13), result.getString(14), result.getString(15),
                result.getString(16), result.getLong(17), result.getString(18),
                result.getString(19), result.getString(20), result.getString(21),
                result.getString(22), result.getString(23), result.getString(24),
                result.getLong(25), result.getString(26), result.getObject(27),
                result.getString(28), result.getString(29), result.getString(30)),
                careerId, year, instance.competitionId());
        rows(canonical, "fixture", fixtures);

        List<String> outputs = jdbc.query("""
                SELECT output_id, team_code, source_match_id, source_receipt_hash
                FROM career_competition_output
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = ?
                ORDER BY output_id
                """, (result, ignored) -> row("output", result.getString(1),
                result.getString(2), result.getString(3), result.getString(4)),
                careerId, year, instance.competitionId());
        rows(canonical, "output", outputs);

        List<String> applications = jdbc.query("""
                SELECT match_id, series_id, receipt_hash, applied_revision
                FROM career_competition_application
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = ?
                ORDER BY match_id, receipt_hash
                """, (result, ignored) -> row("application", result.getString(1),
                result.getString(2), result.getString(3), result.getLong(4)), careerId, year,
                instance.competitionId());
        rows(canonical, "application", applications);

        List<String> resultDetails = jdbc.query("""
                SELECT match_id, binding_hash, receipt_hash, first_score,
                       second_score, total_duration_seconds
                FROM career_competition_result_detail
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = ?
                ORDER BY match_id, receipt_hash
                """, (result, ignored) -> row("resultDetail", result.getString(1),
                result.getString(2), result.getString(3), result.getInt(4),
                result.getInt(5), result.getInt(6)), careerId, year,
                instance.competitionId());
        if (!resultDetails.isEmpty()) rows(canonical, "resultDetail", resultDetails);

        List<String> choices = jdbc.query("""
                SELECT match_id, choice_hash, choice_owner_team_code,
                       eligible_seed_order, chosen_team_code, policy_id, policy_hash
                FROM career_competition_opponent_choice
                WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = ?
                ORDER BY match_id, choice_hash
                """, (result, ignored) -> row("opponentChoice",
                result.getString(1), result.getString(2), result.getString(3),
                result.getString(4), result.getString(5), result.getString(6),
                result.getString(7)), careerId, year, instance.competitionId());
        if (!choices.isEmpty()) rows(canonical, "opponentChoice", choices);

        if ("LCK_CUP".equals(instance.competitionId())) {
            List<String> standings = jdbc.query("""
                    SELECT group_id, group_rank, team_code, match_wins,
                           match_losses, game_wins, game_losses,
                           strength_of_victory, win_time_seconds, tie_break_trace,
                           standings_hash
                    FROM career_lck_cup_standing
                    WHERE career_id = ? AND calendar_season_year = ?
                    ORDER BY group_id, group_rank
                    """, (result, ignored) -> row("cupStanding",
                    result.getString(1), result.getInt(2), result.getString(3),
                    result.getInt(4), result.getInt(5), result.getInt(6),
                    result.getInt(7), result.getInt(8), result.getInt(9),
                    result.getString(10), result.getString(11)), careerId, year);
            if (!standings.isEmpty()) rows(canonical, "cupStanding", standings);
        }
        return CareerCompetitionRules.sha256(canonical.toString().getBytes(
                StandardCharsets.UTF_8));
    }

    private static String cycleHashV2(CycleRow cycle, List<InstanceRow> instances) {
        StringBuilder canonical = new StringBuilder();
        field(canonical, "schema", "CAREER_COMPETITION_CYCLE_CANONICAL_V2");
        field(canonical, "hashAlgorithm", CYCLE_HASH_ALGORITHM);
        field(canonical, "careerId", cycle.careerId());
        field(canonical, "calendarSeasonYear", cycle.seasonYear());
        field(canonical, "seasonOrdinal", cycle.seasonOrdinal());
        field(canonical, "ruleVersion", cycle.ruleVersion());
        field(canonical, "ruleResourceHash", cycle.ruleResourceHash());
        field(canonical, "gamePolicyVersion", cycle.gamePolicyVersion());
        field(canonical, "projectionPolicy", cycle.projectionPolicy());
        field(canonical, "r3r4AllocationPolicy", cycle.r3r4AllocationPolicy());
        field(canonical, "initializationPolicyId", cycle.initializationPolicyId());
        field(canonical, "initializationInputHash", cycle.initializationInputHash());
        field(canonical, "lifecycleStatus", cycle.lifecycleStatus());
        field(canonical, "blockingReason", cycle.blockingReason());
        field(canonical, "r1r2ImportHash", cycle.r1r2ImportHash());
        field(canonical, "r1r2StandingsRevision", cycle.r1r2StandingsRevision());
        field(canonical, "revision", cycle.revision());
        field(canonical, "instanceCount", instances.size());
        for (InstanceRow instance : instances) {
            field(canonical, "instance", instance.competitionId() + "|"
                    + instance.stateHash());
        }
        return CareerCompetitionRules.sha256(canonical.toString().getBytes(
                StandardCharsets.UTF_8));
    }

    private static void rows(StringBuilder target, String name, List<String> values) {
        field(target, name + "Count", values.size());
        values.forEach(value -> field(target, name, value));
    }

    private static String row(String kind, Object... values) {
        StringBuilder result = new StringBuilder(kind);
        for (Object value : values) {
            String text = Objects.toString(value, "");
            result.append('|').append(text.length()).append(':').append(text);
        }
        return result.toString();
    }

    private static void field(StringBuilder target, String name, Object value) {
        String text = Objects.toString(value, "");
        target.append(name).append('=').append(text.length()).append(':')
                .append(text).append('\n');
    }

    private OffsetDateTime now() { return clock.instant().atOffset(ZoneOffset.UTC); }

    private static String initialLifecycle(String id) {
        return switch (id) {
            case "LCK_CUP" -> "READY";
            case "LCK_PLAYOFFS" -> "BLOCKED";
            case "LCK_REGULAR_R1_R2" -> "ACTIVE";
            case "FIRST_STAND", "MSI", "EWC_LOL", "WORLDS" -> "EXTERNAL_ONLY";
            case "ASIAN_GAMES_LOL_RELEASE" -> "WINDOW_ONLY";
            case "KESPA_CUP" -> "SOURCE_GAP";
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

    private static String legacyCycleHash(
            String careerId, int year, long revision, String importHash,
            Long standingsRevision
    ) {
        String canonical = "schema=" + LEGACY_CYCLE_SCHEMA + '\n'
                + "careerId=" + careerId + '\n' + "seasonYear=" + year + '\n'
                + "ruleVersion=" + LEGACY_RULE_VERSION + '\n'
                + "ruleResourceHash=" + LEGACY_RULE_RESOURCE_HASH + '\n'
                + "gamePolicyVersion=" + LEGACY_GAME_POLICY_VERSION + '\n'
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
                result.getString(14), result.getString(15), result.getInt(16),
                result.getString(17), result.getString(18));
    }

    private static InstanceRow instance(ResultSet result) throws SQLException {
        return new InstanceRow(result.getString(1), result.getString(2),
                result.getString(3), result.getString(4), result.getString(5),
                result.getLong(6), result.getString(7), result.getString(8),
                result.getString(9), result.getString(10));
    }

    private static FixtureRow fixtureRow(ResultSet result) throws SQLException {
        return new FixtureRow(result.getString(1), result.getString(2),
                result.getString(3), result.getString(4), result.getObject(5,
                LocalDate.class), result.getString(6), result.getString(7),
                result.getBoolean(8), result.getString(9), result.getString(10),
                result.getString(11), result.getString(12), result.getLong(13),
                result.getString(14), result.getString(15), result.getInt(16),
                result.getString(17), result.getString(18), result.getString(19),
                result.getString(20), result.getString(21), result.getString(22),
                result.getString(23), (Integer) result.getObject(24), result.getString(25),
                result.getString(26), result.getString(27));
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
            String hashAlgorithm, int seasonOrdinal, String initializationPolicyId,
            String initializationInputHash,
            List<InstanceRow> competitions, List<FixtureRow> fixtures,
            List<OutputRow> outputs
    ) {}
    public record InstanceRow(
            String competitionId, String ruleStatus, String lifecycleStatus,
            String blockingReason, String sourceInputHash, long revision,
            String stateHash, String hashAlgorithm, String materializationPolicyId,
            String materializationReceiptHash
    ) {}
    public record FixtureRow(
            String competitionId, String matchId, String fixtureId, String seriesId,
            LocalDate date, String scheduleStatus, String seriesFormat,
            boolean hardFearless, String firstTeamCode, String secondTeamCode,
            String executionMode, String lifecycleStatus, long rootSeed,
            String receiptHash, String stageId, int matchOrder,
            String firstSelectorType, String firstSelectorValue,
            String secondSelectorType, String secondSelectorValue,
            String winnerTeamCode, String loserTeamCode, String groupId,
            Integer groupPointValue, String selectionRightOwner,
            String opponentChoicePolicy, String sideSelectionPolicy
    ) {}
    public record OutputRow(String competitionId, String outputId, String teamCode) {}
    public record ExecutionProjection(
            String bindingHash, String seriesId, String bindingStatus,
            String jobId, String jobStatus, String clientCommandId,
            String failureCode, String resultApplicationStatus
    ) {}
    public record CupStandingView(
            String groupId, int groupPoints, int groupRank, String teamCode,
            int matchWins, int matchLosses, int gameWins, int gameLosses,
            int strengthOfVictory, int winTimeSeconds, String tieBreakTrace,
            String standingsHash
    ) {}
    public record SeedView(
            String competitionId, String seedScope, int seedNumber,
            String teamCode, String sourceInputHash
    ) {}
    public record SealResult(CycleView cycle, boolean replayed, String importHash) {}
    public record CompletionResult(CareerCompetitionAggregate aggregate, boolean replayed) {}
    private record CycleRow(
            String careerId, int seasonYear, String cycleSchema, String ruleVersion,
            String ruleResourceHash, String gamePolicyVersion, String projectionPolicy,
            String r3r4AllocationPolicy, String lifecycleStatus, String blockingReason,
            String r1r2ImportHash, Long r1r2StandingsRevision, long revision,
            String stateHash, String hashAlgorithm, int seasonOrdinal,
            String initializationPolicyId, String initializationInputHash
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
    private record CareerBinding(String managedTeamCode, long rootSeed) {}
    private record PriorRankingHeader(
            int seasonOrdinal, String sourceSeasonId, String lifecycleStatus,
            String stateHash
    ) {}
    private record VerifiedPriorLckRanking(
            int seasonOrdinal, CareerCompetitionRules.PriorLckRanking ranking
    ) {}
    private record CupGroupResultRow(
            String matchId, String firstTeam, String secondTeam, String winnerTeam,
            int pointValue, String seriesFormat, int firstScore, int secondScore,
            int durationSeconds
    ) {}
    private record PendingCupFixture(
            String matchId, String firstType, String firstValue,
            String secondType, String secondValue, String firstTeam,
            String secondTeam, String opponentChoicePolicy
    ) {}
    private record Outcome(String winner, String loser) {}
    private record StageResultRow(
            String firstTeam, String secondTeam, String winnerTeam,
            int firstScore, int secondScore
    ) {}
    private record OutcomeSource(String matchId, String receiptHash) {}
    private static final class MutableCupStanding {
        private final String team;
        private final String group;
        private final List<String> defeated = new ArrayList<>();
        private int matchWins;
        private int matchLosses;
        private int gameWins;
        private int gameLosses;
        private int strength;
        private int winTime;

        private MutableCupStanding(String team, String group) {
            this.team = team;
            this.group = group;
        }

        private int gameDifferential() { return gameWins - gameLosses; }
    }
    private static final class MutableStageStanding {
        private final String team;
        private final String group;
        private int seriesWins;
        private int seriesLosses;
        private int gameWins;
        private int gameLosses;

        private MutableStageStanding(
                String team, String group, int seriesWins, int seriesLosses,
                int gameWins, int gameLosses
        ) {
            this.team = team;
            this.group = group;
            this.seriesWins = seriesWins;
            this.seriesLosses = seriesLosses;
            this.gameWins = gameWins;
            this.gameLosses = gameLosses;
        }

        private int gameDifferential() { return gameWins - gameLosses; }
    }
}
