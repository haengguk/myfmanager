package com.lolfm.career;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Strict executable rules; presentation text is deliberately absent. */
@Component
public final class CareerCompetitionRules {
    public static final String PREVIOUS_VERSION = "lck-career-competition-rules-2026-v2";
    public static final String PREVIOUS_RESOURCE_HASH = "f544319c2bb126fb26304a856a612edd8f75a77db551f9a4e923b69ad1ae2468";
    public static final String PREVIOUS_POLICY = "CAREER_COMPETITION_GAME_POLICY_V2";
    public static final String PLAYOFF_OPPONENT_POLICY = "LCK_PLAYOFFS_LOWEST_AVAILABLE_SEED_OPPONENT_SELECTION_V1";
    public static final String RESOURCE =
            "/competition/lck-career-competition-rules-2026-v3.json";
    public static final String SCHEMA = "CAREER_COMPETITION_RULE_RESOURCE_V3";
    public static final String VERSION = "lck-career-competition-rules-2026-v3";
    public static final String RESOURCE_HASH =
            "3cc24980f76293d06e202a299d544a58e16c72965b82a5054e6e61fef952ba2a";
    public static final String GAME_POLICY_VERSION = "CAREER_COMPETITION_GAME_POLICY_V3";
    public static final String PROJECTION_POLICY =
            "SAME_LOCAL_MONTH_DAY_FROM_2026_REFERENCE_V1";
    public static final String R3_R4_ALLOCATION_POLICY =
            "LCK_R3_R4_TEN_MATCHDAYS_LINEAR_INCLUSIVE_WINDOW_V1";
    public static final String INITIAL_CUP_POLICY = "OFFICIAL_2026_INITIAL_BOOTSTRAP";
    public static final String FUTURE_CUP_POLICY =
            "LCK_CUP_PREVIOUS_IN_GAME_LCK_RANKING_GROUP_DRAFT_V1";
    public static final String CUP_OPPONENT_POLICY =
            "LCK_CUP_LOWEST_AVAILABLE_SEED_OPPONENT_SELECTION_V1";

    private static final Set<String> RULE_STATUSES = Set.of(
            "RULE_SOURCE_COMPLETE", "RULE_SOURCE_INCOMPLETE", "PRODUCT_POLICY_REQUIRED",
            "REFERENCE_TEMPLATE_ONLY");
    private static final Set<String> SCHEDULE_STATUSES = Set.of(
            "OFFICIAL_PROJECTED_DATE", "GAME_DERIVED_SCHEDULE_POLICY");
    private static final Set<String> SERIES_FORMATS = Set.of(
            "BO1", "BO3", "BO5", "MIXED_BO3_BO5", "MIXED_BO1_BO3_BO5", "NONE");
    private static final Set<String> SELECTORS = Set.of(
            "R1_R2_RANK", "MATCH_WINNER", "MATCH_LOSER", "PLAY_IN_SEED",
            "INITIAL_BOOTSTRAP_TEAM", "CUP_GROUP_SEED", "LCK_FINAL_RANK",
            "CUP_PLAY_IN_SEED", "CUP_PLAYOFF_SEED", "LCK_PLAYOFF_SEED",
            "LOWEST_AVAILABLE_PLAYOFF_SEED", "REMAINING_PLAYOFF_SEED",
            "LOWEST_AVAILABLE_SEED_MATCH_WINNER", "REMAINING_MATCH_WINNER",
            "HIGHER_PLAYOFF_SEED_MATCH_LOSER", "LOWER_PLAYOFF_SEED_MATCH_LOSER");
    private static final Set<String> EXPECTED = Set.of(
            "LCK_CUP", "LCK_REGULAR_R1_R2", "LCK_ROAD_TO_MSI",
            "LCK_REGULAR_R3_R4", "LCK_PLAY_IN", "LCK_PLAYOFFS", "FIRST_STAND",
            "MSI", "EWC_LOL", "WORLDS", "ASIAN_GAMES_LOL_RELEASE", "KESPA_CUP");
    private static final Set<String> INITIAL_LCK_TEAM_CODES = Set.of(
            "GEN", "T1", "NS", "DNS", "BRO", "HLE", "DK", "KT", "BFX", "KRX");
    private static final Map<String, String> RAW_HASHES = Map.of(
            "README", "853851cb54843a6b5393d89915220220faada8b9284593822bd271af837bab26",
            "CALENDAR_FORMATS", "b47a681950382b3a67be7d4d7d43ed957796470b667c490dc4ce51e2bf3f7e01",
            "OFFICIAL_REPORT", "86b16a278d09763260bdd46b0be1047146eca02c2ebd893f66be6e74f7812b0a",
            "SOURCE_LEDGER", "0dd2a2818d24d3e212e9f00b51790b8f599b0b57ef67e23486491d80a2dd09b6",
            "LCK_EVENT_RULEBOOK_2026", "e4c52706feb67dfbac23669ce8cc0a01d5a780be71772b149849d41e9698a670",
            "KESPA_CUP_RULEBOOK_2025", "574ee7d8993b07a073e3ecc18f96227bbff48dc886ce4b336a61f5d8f4f8542a");

    private final ResourceBody body;
    private final Map<String, CompetitionRule> indexed;
    private final Map<String, CompetitionRule> previous;

    @org.springframework.beans.factory.annotation.Autowired
    public CareerCompetitionRules(ObjectMapper mapper) {
        this(load(mapper));
    }

    CareerCompetitionRules(ResourceBody body) {
        this.body = Objects.requireNonNull(body, "body");
        validate(body);
        LinkedHashMap<String, CompetitionRule> values = new LinkedHashMap<>();
        body.competitions().forEach(value -> values.put(value.competitionId(), value));
        this.indexed = Map.copyOf(values);
        try (InputStream input = CareerCompetitionRules.class.getResourceAsStream(
                "/competition/lck-career-competition-rules-2026-v2.json")) {
            byte[] bytes = Objects.requireNonNull(input).readAllBytes();
            if (!PREVIOUS_RESOURCE_HASH.equals(sha256(bytes))) throw new IllegalStateException("Previous competition resource mismatch");
            ResourceBody old = new ObjectMapper().readValue(bytes, ResourceBody.class);
            this.previous = old.competitions().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(CompetitionRule::competitionId, v -> v));
        } catch (IOException error) { throw new IllegalStateException("Previous competition resource unavailable", error); }
    }

    public CompetitionRule rule(String competitionId, String version) {
        if (VERSION.equals(version)) return rule(competitionId);
        if (PREVIOUS_VERSION.equals(version) && previous.containsKey(competitionId)) return previous.get(competitionId);
        throw new IllegalArgumentException("UNSUPPORTED_COMPETITION_RULE_VERSION");
    }
    static boolean supportedIdentity(String version, String resource, String policy) {
        return VERSION.equals(version) && RESOURCE_HASH.equals(resource) && GAME_POLICY_VERSION.equals(policy)
                || PREVIOUS_VERSION.equals(version) && PREVIOUS_RESOURCE_HASH.equals(resource) && PREVIOUS_POLICY.equals(policy);
    }

    public OpponentChoiceReceipt choosePlayoffOpponent(String owner, List<CareerCompetitionAggregate.SeededTeam> eligible) {
        OpponentChoiceReceipt cup = chooseCupOpponent(owner, eligible);
        String policyHash = sha256((PLAYOFF_OPPONENT_POLICY + "\nOFFICIAL_OWNER_AND_ELIGIBLE_POOL\nLOWEST_AVAILABLE_SEED\n").getBytes(StandardCharsets.UTF_8));
        String content = PLAYOFF_OPPONENT_POLICY + '\n' + policyHash + '\n' + owner + '\n'
                + String.join(",", cup.canonicalEligibleOrder()) + '\n' + cup.chosenTeamCode() + '\n';
        return new OpponentChoiceReceipt(PLAYOFF_OPPONENT_POLICY, policyHash, owner,
                cup.canonicalEligibleOrder(), cup.chosenTeamCode(), sha256(content.getBytes(StandardCharsets.UTF_8)));
    }

    public CompetitionRule rule(String competitionId) {
        CompetitionRule value = indexed.get(competitionId);
        if (value == null) throw new IllegalArgumentException("UNKNOWN_COMPETITION");
        return value;
    }

    public List<CompetitionRule> competitions() { return body.competitions(); }
    public String resourceHash() { return RESOURCE_HASH; }
    public Map<String, String> rawSources() { return body.rawSources(); }
    public List<SourceReference> sources() { return body.sources(); }
    public LckCupRule lckCup() { return body.lckCup(); }
    public KespaCupReference kespaCup() { return body.kespaCup(); }

    public OpponentChoiceReceipt chooseCupOpponent(
            String choiceOwnerTeamCode,
            List<CareerCompetitionAggregate.SeededTeam> eligible
    ) {
        required(choiceOwnerTeamCode);
        if (eligible == null || eligible.isEmpty()) {
            throw new IllegalArgumentException("LCK_CUP_OPPONENT_ELIGIBILITY_REQUIRED");
        }
        HashSet<String> teams = new HashSet<>();
        List<CareerCompetitionAggregate.SeededTeam> ordered = eligible.stream()
                .peek(value -> {
                    if (choiceOwnerTeamCode.equals(value.teamCode())
                            || !teams.add(value.teamCode())) {
                        throw new IllegalArgumentException(
                                "LCK_CUP_OPPONENT_ELIGIBILITY_INVALID");
                    }
                })
                .sorted(Comparator.comparingInt(
                        CareerCompetitionAggregate.SeededTeam::seed).reversed()
                        .thenComparing(CareerCompetitionAggregate.SeededTeam::teamCode))
                .toList();
        String policyHash = cupOpponentPolicyHash();
        String chosen = ordered.getFirst().teamCode();
        StringBuilder canonical = new StringBuilder(
                "schema=CAREER_LCK_CUP_OPPONENT_CHOICE_RECEIPT_V1\n")
                .append("policyId=").append(CUP_OPPONENT_POLICY).append('\n')
                .append("policyHash=").append(policyHash).append('\n')
                .append("choiceOwnerTeamCode=").append(choiceOwnerTeamCode).append('\n');
        ordered.forEach(value -> canonical.append("eligible=").append(value.seed())
                .append('|').append(value.teamCode()).append('\n'));
        canonical.append("chosenTeamCode=").append(chosen).append('\n');
        return new OpponentChoiceReceipt(CUP_OPPONENT_POLICY, policyHash,
                choiceOwnerTeamCode, ordered.stream().map(value ->
                value.seed() + ":" + value.teamCode()).toList(), chosen,
                sha256(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    }

    public String cupOpponentPolicyHash() {
        OpponentChoicePolicy policy = body.lckCup().opponentChoicePolicy();
        String canonical = "schema=CAREER_LCK_CUP_OPPONENT_CHOICE_POLICY_V1\n"
                + "policyId=" + policy.policyId() + '\n'
                + "officialChoiceRights=" + policy.officialChoiceRights() + '\n'
                + "productChoiceRule=" + policy.productChoiceRule() + '\n'
                + "finalTieBreak=" + policy.finalTieBreak() + '\n';
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    public LocalDate projectDate(int year, String monthDay) {
        if (year < 2026 || year > 9999) throw new IllegalArgumentException("seasonYear");
        MonthDay value = MonthDay.parse("--" + monthDay);
        if (!value.isValidYear(year)) throw new IllegalArgumentException("monthDay");
        return value.atYear(year);
    }

    public CupInitialization initialCupInitialization(int calendarYear) {
        InitialBootstrap bootstrap = body.lckCup().initialBootstrap();
        List<CupGroupSeed> groups = bootstrap.groups().stream()
                .map(value -> new CupGroupSeed(value.groupId(), value.groupSeed(),
                        value.stableTeamCode(), "INITIAL_BOOTSTRAP_TEAM",
                        value.stableTeamCode())).toList();
        return cupInitialization(1, calendarYear, INITIAL_CUP_POLICY,
                bootstrap.sourceReferenceYear(), null, null, null, groups);
    }

    public CupInitialization futureCupInitialization(
            int seasonOrdinal,
            int calendarYear,
            PriorLckRanking prior
    ) {
        if (seasonOrdinal < 2 || prior == null
                || prior.seasonYear() != calendarYear - 1
                || !"SEALED".equals(prior.lifecycleStatus())) {
            throw new IllegalArgumentException("LCK_CUP_PRIOR_SEASON_RANKING_REQUIRED");
        }
        CareerIdentity.requireCareerId(prior.careerId());
        CareerIdentity.requireSha256(prior.stateHash(), "priorLckRankingStateHash");
        List<CareerCompetitionAggregate.SeededTeam> ranking = prior.ranking().stream()
                .sorted(Comparator.comparingInt(CareerCompetitionAggregate.SeededTeam::seed))
                .toList();
        Map<Integer, CareerCompetitionAggregate.SeededTeam> indexed = new LinkedHashMap<>();
        Set<String> teams = new HashSet<>();
        ranking.forEach(value -> {
            if (indexed.put(value.seed(), value) != null || !teams.add(value.teamCode())) {
                throw new IllegalArgumentException("LCK_CUP_PRIOR_RANKING_NOT_TOTAL_ORDER");
            }
        });
        if (indexed.size() != 10 || !indexed.keySet().equals(Set.of(
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10))) {
            throw new IllegalArgumentException("LCK_CUP_PRIOR_RANKING_NOT_TOTAL_ORDER");
        }
        ArrayList<CupGroupSeed> groups = new ArrayList<>();
        groups.add(new CupGroupSeed("BARON", 1, indexed.get(1).teamCode(),
                "LCK_FINAL_RANK", "1"));
        groups.add(new CupGroupSeed("ELDER", 1, indexed.get(2).teamCode(),
                "LCK_FINAL_RANK", "2"));
        Map<String, Integer> nextGroupSeed = new LinkedHashMap<>();
        nextGroupSeed.put("BARON", 2);
        nextGroupSeed.put("ELDER", 2);
        List<SelectionTurn> turns = body.lckCup().futureGroupPolicy().selectionTurns();
        for (int index = 0; index < turns.size(); index++) {
            int rank = index + 3;
            String group = turns.get(index).targetGroup();
            groups.add(new CupGroupSeed(group, nextGroupSeed.get(group),
                    indexed.get(rank).teamCode(), "LCK_FINAL_RANK", Integer.toString(rank)));
            nextGroupSeed.put(group, nextGroupSeed.get(group) + 1);
        }
        groups.sort(Comparator.comparing(CupGroupSeed::groupId)
                .thenComparingInt(CupGroupSeed::groupSeed));
        return cupInitialization(seasonOrdinal, calendarYear, FUTURE_CUP_POLICY,
                null, prior.careerId(), prior.seasonYear(), prior.stateHash(), groups);
    }

    public String cupMaterializationReceiptHash(CupInitialization initialization) {
        StringBuilder canonical = new StringBuilder(
                "schema=CAREER_LCK_CUP_MATERIALIZATION_RECEIPT_V1\n");
        canonical.append("initializationPolicyId=").append(initialization.policyId())
                .append('\n').append("initializationInputHash=")
                .append(initialization.inputHash()).append('\n')
                .append("opponentChoicePolicy=").append(CUP_OPPONENT_POLICY).append('\n')
                .append("opponentChoicePolicyHash=").append(cupOpponentPolicyHash())
                .append('\n');
        rule("LCK_CUP").matches().stream().sorted(Comparator.comparingInt(
                        MatchRule::matchOrder)).forEach(match -> canonical.append("match=")
                .append(match.canonical()).append('\n'));
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static CupInitialization cupInitialization(
            int ordinal, int calendarYear, String policyId, Integer sourceYear,
            String sourceCareerId, Integer sourceSeasonYear, String sourceStateHash,
            List<CupGroupSeed> groups
    ) {
        StringBuilder canonical = new StringBuilder(
                "schema=CAREER_LCK_CUP_INITIALIZATION_INPUT_V1\n")
                .append("seasonOrdinal=").append(ordinal).append('\n')
                .append("calendarYear=").append(calendarYear).append('\n')
                .append("policyId=").append(policyId).append('\n')
                .append("sourceReferenceYear=").append(Objects.toString(sourceYear, ""))
                .append('\n').append("sourceCareerId=")
                .append(Objects.toString(sourceCareerId, "")).append('\n')
                .append("sourceSeasonYear=")
                .append(Objects.toString(sourceSeasonYear, "")).append('\n')
                .append("sourceStateHash=").append(Objects.toString(sourceStateHash, ""))
                .append('\n');
        groups.stream().sorted(Comparator.comparing(CupGroupSeed::groupId)
                        .thenComparingInt(CupGroupSeed::groupSeed))
                .forEach(value -> canonical.append("groupSeed=")
                        .append(value.canonical()).append('\n'));
        String inputHash = sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
        return new CupInitialization(ordinal, calendarYear, policyId, sourceYear,
                sourceCareerId, sourceSeasonYear, sourceStateHash, inputHash,
                List.copyOf(groups));
    }

    private static ResourceBody load(ObjectMapper mapper) {
        InputStream input = CareerCompetitionRules.class.getResourceAsStream(RESOURCE);
        if (input == null) throw new IllegalStateException("Competition rule resource missing");
        try (input) {
            byte[] bytes = input.readAllBytes();
            if (!RESOURCE_HASH.equals(sha256(bytes))) {
                throw new IllegalStateException("Competition rule resource hash mismatch");
            }
            return mapper.readValue(bytes, ResourceBody.class);
        } catch (IOException failure) {
            throw new IllegalStateException("Competition rule resource read failure", failure);
        }
    }

    private static void validate(ResourceBody value) {
        if (value == null || !SCHEMA.equals(value.schemaVersion())
                || !VERSION.equals(value.ruleVersion())
                || !GAME_POLICY_VERSION.equals(value.gamePolicyVersion())
                || !PROJECTION_POLICY.equals(value.projectionPolicy())
                || !R3_R4_ALLOCATION_POLICY.equals(value.r3r4AllocationPolicy())
                || !RAW_HASHES.equals(value.rawSources())) {
            throw new IllegalStateException("Competition rule provenance mismatch");
        }
        Set<String> sourceIds = new HashSet<>();
        for (SourceReference source : value.sources()) {
            required(source.organization());
            required(source.title());
            required(source.url());
            required(source.publicationOrVersionDate());
            required(source.retrievedAt());
            if (!sourceIds.add(required(source.sourceId()))
                    || !Set.of("OFFICIAL_PRIMARY", "LOCAL_FROZEN_SOURCE").contains(
                    source.classification())
                    || source.provesFields().isEmpty()
                    || source.rawSha256() != null
                    && !source.rawSha256().matches("[0-9a-f]{64}")) {
                throw new IllegalStateException("Competition source ledger mismatch");
            }
        }
        Set<String> ids = new HashSet<>();
        for (CompetitionRule competition : value.competitions()) {
            if (!ids.add(required(competition.competitionId()))
                    || !RULE_STATUSES.contains(competition.ruleStatus())
                    || competition.sourceIds().isEmpty()
                    || !sourceIds.containsAll(competition.sourceIds())
                    || !SERIES_FORMATS.contains(competition.seriesFormat())
                    || competition.hardFearless() == null
                    && competition.competitionId().startsWith("LCK_")
                    && !"LCK_PLAYOFFS".equals(competition.competitionId())) {
                throw new IllegalStateException("Competition rule identity mismatch");
            }
            Set<String> matchIds = new HashSet<>();
            int order = 0;
            for (MatchRule match : competition.matches()) {
                if (!matchIds.add(required(match.matchId()))
                        || match.matchOrder() != ++order || match.groupPointValue() != null
                        && match.groupPointValue() < 0
                        || !SCHEDULE_STATUSES.contains(match.scheduleStatus())
                        || !Set.of("BO3", "BO5").contains(match.seriesFormat())
                        || match.hardFearless() == null) {
                    throw new IllegalStateException("Competition match identity mismatch");
                }
                MonthDay.parse("--" + required(match.monthDay()));
                validateSelector(match.first(), matchIds);
                validateSelector(match.second(), matchIds);
                validateOutputs(match.winnerOutputs());
                validateOutputs(match.loserOutputs());
            }
            if (!competition.scheduledMonthDays().equals(
                    competition.scheduledMonthDays().stream().sorted().toList())) {
                throw new IllegalStateException("Competition dates not ordered");
            }
        }
        if (!ids.equals(EXPECTED)
                || rule(value, "LCK_CUP").matches().size() != 40
                || rule(value, "LCK_ROAD_TO_MSI").matches().size() != 5
                || rule(value, "LCK_PLAY_IN").matches().size() != 3
                || rule(value, "LCK_PLAYOFFS").scheduledMonthDays().size() != 10
                || rule(value, "LCK_PLAYOFFS").matches().size() != 10
                || rule(value, "LCK_PLAYOFFS").matches().stream().anyMatch(m -> !"BO5".equals(m.seriesFormat()) || !m.hardFearless())
                || !"RULE_SOURCE_COMPLETE".equals(rule(value, "LCK_CUP").ruleStatus())
                || rule(value, "LCK_CUP").blockingReason() != null
                || !"REFERENCE_TEMPLATE_ONLY".equals(
                rule(value, "KESPA_CUP").ruleStatus())) {
            throw new IllegalStateException("Competition rule set mismatch");
        }
        validateLckCup(value.lckCup(), rule(value, "LCK_CUP"), sourceIds);
        validateKespa(value.kespaCup(), sourceIds);
    }

    private static void validateLckCup(
            LckCupRule cup, CompetitionRule competition, Set<String> sourceIds
    ) {
        if (cup.sourceReferenceYear() != 2026
                || !INITIAL_CUP_POLICY.equals(cup.initialBootstrap().policyId())
                || cup.initialBootstrap().sourceReferenceYear() != 2026
                || cup.initialBootstrap().appliedSeasonOrdinal() != 1
                || !sourceIds.containsAll(cup.officialSourceIds())
                || !sourceIds.containsAll(cup.initialBootstrap().sourceIds())
                || !FUTURE_CUP_POLICY.equals(cup.futureGroupPolicy().policyId())
                || cup.futureGroupPolicy().minimumSeasonOrdinal() != 2
                || !"CHAMPION".equals(cup.futureGroupPolicy().firstSelectionOwner())
                || !CUP_OPPONENT_POLICY.equals(cup.opponentChoicePolicy().policyId())
                || !cup.futureGroupPolicy().selectionTurns().stream().map(
                SelectionTurn::targetGroup).toList().equals(List.of(
                "BARON", "ELDER", "ELDER", "BARON",
                "BARON", "ELDER", "ELDER", "BARON"))) {
            throw new IllegalStateException("LCK Cup policy mismatch");
        }
        List<BootstrapGroupSeed> bootstrap = cup.initialBootstrap().groups();
        Set<String> stableTeams = new HashSet<>();
        if (bootstrap.size() != 10 || bootstrap.stream().anyMatch(value ->
                !Set.of("BARON", "ELDER").contains(value.groupId())
                        || value.groupSeed() < 1 || value.groupSeed() > 5
                        || !stableTeams.add(required(value.stableTeamCode())))
                || !stableTeams.equals(INITIAL_LCK_TEAM_CODES)) {
            throw new IllegalStateException("LCK Cup bootstrap mismatch");
        }
        Set<String> aliases = new HashSet<>();
        for (TeamAlias alias : cup.initialBootstrap().teamAliases()) {
            String identity = required(alias.sourceTeamName()) + '|'
                    + required(alias.sourceTeamCode()) + '|'
                    + required(alias.stableTeamCode());
            if (!aliases.add(identity)) {
                throw new IllegalStateException("LCK Cup team alias mismatch");
            }
        }
        if (aliases.size() != 10 || bootstrap.stream().anyMatch(value ->
                !aliases.contains(value.sourceTeamName() + '|' + value.sourceTeamCode()
                        + '|' + value.stableTeamCode()))) {
            throw new IllegalStateException("LCK Cup team alias mismatch");
        }
        for (String group : List.of("BARON", "ELDER")) {
            if (!bootstrap.stream().filter(value -> group.equals(value.groupId()))
                    .map(BootstrapGroupSeed::groupSeed).collect(
                            java.util.stream.Collectors.toSet())
                    .equals(Set.of(1, 2, 3, 4, 5))) {
                throw new IllegalStateException("LCK Cup group seed mismatch");
            }
        }
        List<MatchRule> groupMatches = competition.matches().stream()
                .filter(value -> "GROUP_BATTLE".equals(value.stageId())).toList();
        Set<String> pairs = new HashSet<>();
        long superWeek = groupMatches.stream().filter(value ->
                value.groupPointValue() != null && value.groupPointValue() == 2).count();
        Map<String, Set<String>> teamsByDate = new LinkedHashMap<>();
        for (MatchRule match : groupMatches) {
            if (!"CUP_GROUP_SEED".equals(match.first().type())
                    || !match.first().value().startsWith("BARON:")
                    || !"CUP_GROUP_SEED".equals(match.second().type())
                    || !match.second().value().startsWith("ELDER:")
                    || !pairs.add(match.first().value() + "|" + match.second().value())
                    || match.groupPointValue() == null
                    || !Set.of(1, 2).contains(match.groupPointValue())
                    || match.groupPointValue() == 1
                    && !"BO3".equals(match.seriesFormat())
                    || match.groupPointValue() == 2
                    && !"BO5".equals(match.seriesFormat())) {
                throw new IllegalStateException("LCK Cup cross-group match mismatch");
            }
            Set<String> daily = teamsByDate.computeIfAbsent(match.monthDay(),
                    ignored -> new HashSet<>());
            if (!daily.add(match.first().value()) || !daily.add(match.second().value())) {
                throw new IllegalStateException("LCK Cup daily team collision");
            }
        }
        if (groupMatches.size() != 25 || pairs.size() != 25 || superWeek != 5
                || groupMatches.stream().filter(value ->
                value.groupPointValue() == 1).count() != 20
                || groupMatches.stream().filter(value -> value.groupPointValue() == 2)
                .anyMatch(value -> !value.first().value().substring(6).equals(
                        value.second().value().substring(6)))) {
            throw new IllegalStateException("LCK Cup group graph mismatch");
        }
        List<MatchRule> playIn = competition.matches().stream().filter(value ->
                "CUP_PLAY_IN".equals(value.stageId())).toList();
        List<MatchRule> playoffs = competition.matches().stream().filter(value ->
                "CUP_PLAYOFFS".equals(value.stageId())).toList();
        if (playIn.size() != 5 || !playIn.subList(0, 4).stream().allMatch(value ->
                "BO3".equals(value.seriesFormat()))
                || !"BO5".equals(playIn.getLast().seriesFormat())
                || playoffs.size() != 10 || !playoffs.stream().allMatch(value ->
                "BO5".equals(value.seriesFormat()))
                || !competition.matches().stream().allMatch(value ->
                Boolean.TRUE.equals(value.hardFearless()))
                || competition.matches().stream().filter(value ->
                CUP_OPPONENT_POLICY.equals(value.opponentChoicePolicy())).count() != 3) {
            throw new IllegalStateException("LCK Cup bracket mismatch");
        }
        List<MatchRule> choices = competition.matches().stream().filter(value ->
                CUP_OPPONENT_POLICY.equals(value.opponentChoicePolicy())).toList();
        if (!choices.stream().map(MatchRule::matchId).toList().equals(List.of(
                "PI_R2_M1", "PO_UBR1_M1", "PO_UBR2_M1"))
                || choices.stream().anyMatch(value ->
                value.selectionRightOwner() == null)
                || !competition.matches().getLast().winnerOutputs().equals(
                List.of("FIRST_STAND_LCK_SEED_1"))
                || !competition.matches().getLast().loserOutputs().equals(
                List.of("FIRST_STAND_LCK_SEED_2"))) {
            throw new IllegalStateException("LCK Cup choice/output mismatch");
        }
    }

    private static void validateKespa(KespaCupReference value, Set<String> sourceIds) {
        if (value.sourceReferenceYear() != 2025
                || !"KESPA_CUP_REFERENCE_TEMPLATE_2025".equals(value.templateId())
                || !"REFERENCE_TEMPLATE_NOT_OFFICIAL_FOR_2026_OR_FUTURE".equals(
                value.status())
                || !value.executionBlockers().equals(List.of(
                "KESPA_CUP_2026_RULE_SOURCE_INCOMPLETE",
                "EXTERNAL_PARTICIPANT_ROSTER_AUTHORITY_MISSING"))
                || value.stages().size() != 3 || value.participantSlots().size() != 14
                || value.participantSlots().stream().filter(slot -> !slot.resolved()).count()
                != 14 || !sourceIds.containsAll(value.sourceIds())
                || value.stages().stream().anyMatch(stage ->
                !sourceIds.containsAll(stage.sourceIds()))) {
            throw new IllegalStateException("KeSPA Cup reference mismatch");
        }
    }

    private static CompetitionRule rule(ResourceBody body, String id) {
        return body.competitions().stream().filter(value -> id.equals(
                value.competitionId())).findFirst().orElseThrow();
    }

    private static void validateSelector(ParticipantSelector selector, Set<String> prior) {
        if (selector == null || !SELECTORS.contains(selector.type())
                || required(selector.value()).isBlank()) {
            throw new IllegalStateException("Competition selector mismatch");
        }
        if (("MATCH_WINNER".equals(selector.type())
                || "MATCH_LOSER".equals(selector.type()))
                && !prior.contains(selector.value())) {
            throw new IllegalStateException("Competition routing is not ordered");
        }
        if (Set.of("LOWEST_AVAILABLE_SEED_MATCH_WINNER", "REMAINING_MATCH_WINNER",
                "HIGHER_PLAYOFF_SEED_MATCH_LOSER", "LOWER_PLAYOFF_SEED_MATCH_LOSER")
                .contains(selector.type())) {
            for (String matchId : selector.value().split(",")) {
                if (!prior.contains(matchId)) throw new IllegalStateException(
                        "Competition selection pool is not ordered");
            }
        }
        if (Set.of("R1_R2_RANK", "PLAY_IN_SEED", "CUP_PLAY_IN_SEED",
                "CUP_PLAYOFF_SEED", "LCK_PLAYOFF_SEED", "LCK_FINAL_RANK").contains(selector.type())) {
            int rank = Integer.parseInt(selector.value());
            if (rank < 1 || rank > 10) throw new IllegalStateException(
                    "Competition seed out of range");
        }
        if ("CUP_GROUP_SEED".equals(selector.type())
                && !selector.value().matches("(?:BARON|ELDER):[1-5]")) {
            throw new IllegalStateException("LCK Cup group selector mismatch");
        }
    }

    private static void validateOutputs(List<String> outputs) {
        if (outputs.stream().anyMatch(value -> !required(value).matches("[A-Z0-9_]+"))
                || new HashSet<>(outputs).size() != outputs.size()) {
            throw new IllegalStateException("Competition output mismatch");
        }
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalStateException(
                "Competition required value missing");
        return value;
    }

    static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record ResourceBody(
            String schemaVersion, String ruleVersion, String gamePolicyVersion,
            String projectionPolicy, String r3r4AllocationPolicy,
            Map<String, String> rawSources, List<SourceReference> sources,
            LckCupRule lckCup, KespaCupReference kespaCup,
            List<CompetitionRule> competitions
    ) {
        public ResourceBody {
            rawSources = Map.copyOf(rawSources);
            sources = List.copyOf(sources);
            competitions = List.copyOf(competitions);
        }
    }

    public record SourceReference(
            String sourceId, String organization, String title, String url,
            String publicationOrVersionDate, String retrievedAt,
            String classification, String rawSha256, List<String> provesFields
    ) {
        public SourceReference { provesFields = List.copyOf(provesFields); }
    }

    public record LckCupRule(
            int sourceReferenceYear, String sourceCompletenessStatus,
            List<String> officialSourceIds, InitialBootstrap initialBootstrap,
            FutureGroupPolicy futureGroupPolicy, OpponentChoicePolicy opponentChoicePolicy,
            String scheduleAllocationPolicy, List<String> individualRankingCriteria,
            List<String> groupWinnerCriteria, String playInSeedAllocationPolicy,
            String playoffSeedAllocationPolicy, List<String> qualificationOutputs
    ) {
        public LckCupRule {
            officialSourceIds = List.copyOf(officialSourceIds);
            individualRankingCriteria = List.copyOf(individualRankingCriteria);
            groupWinnerCriteria = List.copyOf(groupWinnerCriteria);
            qualificationOutputs = List.copyOf(qualificationOutputs);
        }
    }

    public record InitialBootstrap(
            String policyId, int sourceReferenceYear, int appliedSeasonOrdinal,
            List<String> sourceIds, List<BootstrapGroupSeed> groups,
            List<TeamAlias> teamAliases
    ) {
        public InitialBootstrap {
            sourceIds = List.copyOf(sourceIds);
            groups = List.copyOf(groups);
            teamAliases = List.copyOf(teamAliases);
        }
    }

    public record BootstrapGroupSeed(
            String groupId, int groupSeed, String sourceTeamName,
            String sourceTeamCode, String stableTeamCode
    ) {}
    public record TeamAlias(String sourceTeamName, String sourceTeamCode,
                            String stableTeamCode) {}
    public record FutureGroupPolicy(
            String policyId, int minimumSeasonOrdinal, String rankingAuthority,
            String firstSelectionOwner, List<SelectionTurn> selectionTurns
    ) {
        public FutureGroupPolicy { selectionTurns = List.copyOf(selectionTurns); }
    }
    public record SelectionTurn(int turn, String selectingSlot, String targetGroup) {}
    public record OpponentChoicePolicy(
            String policyId, String officialChoiceRights,
            String productChoiceRule, String finalTieBreak
    ) {}

    public record KespaCupReference(
            String templateId, int sourceReferenceYear, String status,
            String futureEventStatus, List<String> sourceIds,
            List<String> executionBlockers, List<ParticipantSlot> participantSlots,
            List<ReferenceStage> stages
    ) {
        public KespaCupReference {
            sourceIds = List.copyOf(sourceIds);
            executionBlockers = List.copyOf(executionBlockers);
            participantSlots = List.copyOf(participantSlots);
            stages = List.copyOf(stages);
        }
    }
    public record ParticipantSlot(String slotId, String authority, boolean resolved) {}
    public record ReferenceStage(
            String stageId, int teamCount, int matchCount, String seriesFormat,
            String routing, List<String> sourceIds
    ) {
        public ReferenceStage { sourceIds = List.copyOf(sourceIds); }
    }

    public record CompetitionRule(
            String competitionId, String ruleStatus, String blockingReason,
            List<String> sourceIds, String seriesFormat, Boolean hardFearless,
            List<MatchRule> matches, List<String> scheduledMonthDays
    ) {
        public CompetitionRule {
            sourceIds = List.copyOf(sourceIds);
            matches = List.copyOf(matches);
            scheduledMonthDays = List.copyOf(scheduledMonthDays);
        }
    }

    public record MatchRule(
            String matchId, String stageId, int matchOrder, String monthDay,
            String scheduleStatus, String seriesFormat, Boolean hardFearless,
            ParticipantSelector first, ParticipantSelector second,
            List<String> winnerOutputs, List<String> loserOutputs,
            String groupId, Integer groupPointValue, String selectionRightOwner,
            String opponentChoicePolicy, String sideSelectionPolicy
    ) {
        public MatchRule {
            winnerOutputs = winnerOutputs == null ? List.of() : List.copyOf(winnerOutputs);
            loserOutputs = loserOutputs == null ? List.of() : List.copyOf(loserOutputs);
        }
        String canonical() {
            return String.join("|", matchId, stageId, Integer.toString(matchOrder),
                    monthDay, scheduleStatus, seriesFormat,
                    Boolean.toString(Boolean.TRUE.equals(hardFearless)),
                    first.type() + ":" + first.value(), second.type() + ":" + second.value(),
                    String.join(",", winnerOutputs), String.join(",", loserOutputs),
                    Objects.toString(groupId, ""), Objects.toString(groupPointValue, ""),
                    Objects.toString(selectionRightOwner, ""),
                    Objects.toString(opponentChoicePolicy, ""),
                    Objects.toString(sideSelectionPolicy, ""));
        }
    }

    public record ParticipantSelector(String type, String value) {
        public ParticipantSelector {
            if (!SELECTORS.contains(type) || value == null || value.isBlank()) {
                throw new IllegalArgumentException("COMPETITION_SELECTOR_VOCABULARY_MISMATCH");
            }
        }
    }

    public record PriorLckRanking(
            String careerId, int seasonYear, String lifecycleStatus, String stateHash,
            List<CareerCompetitionAggregate.SeededTeam> ranking
    ) {
        public PriorLckRanking { ranking = List.copyOf(ranking); }
    }

    public record CupGroupSeed(
            String groupId, int groupSeed, String teamCode,
            String sourceSelectorType, String sourceSelectorValue
    ) {
        String canonical() {
            return groupId + '|' + groupSeed + '|' + teamCode + '|'
                    + sourceSelectorType + '|' + sourceSelectorValue;
        }
    }

    public record CupInitialization(
            int seasonOrdinal, int calendarYear, String policyId,
            Integer sourceReferenceYear, String sourceCareerId,
            Integer sourceSeasonYear, String sourceStateHash, String inputHash,
            List<CupGroupSeed> groups
    ) {
        public CupInitialization { groups = List.copyOf(groups); }
    }

    public record OpponentChoiceReceipt(
            String policyId, String policyHash, String choiceOwnerTeamCode,
            List<String> canonicalEligibleOrder, String chosenTeamCode,
            String receiptHash
    ) {
        public OpponentChoiceReceipt {
            canonicalEligibleOrder = List.copyOf(canonicalEligibleOrder);
        }
    }
}
