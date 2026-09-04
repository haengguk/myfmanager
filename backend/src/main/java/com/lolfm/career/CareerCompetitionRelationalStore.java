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
        return initialize(career.careerId(), calendarSeasonYear,
                career.managedTeamCode(), career.rootSeed(),
                rules.initialCupInitialization(calendarSeasonYear));
    }

    public CycleView initialize(String careerId, int calendarSeasonYear) {
        CareerBinding binding = careerBinding(careerId);
        return initialize(careerId, calendarSeasonYear, binding.managedTeamCode(),
                binding.rootSeed(), rules.initialCupInitialization(calendarSeasonYear));
    }

    public CycleView initializeFuture(
            String careerId,
            int calendarSeasonYear,
            int seasonOrdinal,
            CareerCompetitionRules.PriorLckRanking priorRanking
    ) {
        if (priorRanking == null || !careerId.equals(priorRanking.careerId())) {
            throw new IllegalArgumentException("LCK_CUP_PRIOR_SEASON_CAREER_MISMATCH");
        }
        CareerBinding binding = careerBinding(careerId);
        return initialize(careerId, calendarSeasonYear, binding.managedTeamCode(),
                binding.rootSeed(), rules.futureCupInitialization(seasonOrdinal,
                        calendarSeasonYear, priorRanking));
    }

    private CycleView initialize(
            String careerId,
            int calendarSeasonYear,
            String managedTeamCode,
            long careerRootSeed,
            CareerCompetitionRules.CupInitialization cupInitialization
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
            initialize(key.substring(0, separator),
                    Integer.parseInt(key.substring(separator + 1)));
        }
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
}
