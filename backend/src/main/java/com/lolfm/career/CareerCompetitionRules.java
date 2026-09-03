package com.lolfm.career;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.MonthDay;
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
    public static final String RESOURCE =
            "/competition/lck-career-competition-rules-2026-v1.json";
    public static final String SCHEMA = "CAREER_COMPETITION_RULE_RESOURCE_V1";
    public static final String VERSION = "lck-career-competition-rules-2026-v1";
    public static final String RESOURCE_HASH =
            "64acfab316162ca7f17c898c434b7ecce496f085370ff45012a83332d445b770";
    public static final String GAME_POLICY_VERSION = "CAREER_COMPETITION_GAME_POLICY_V1";
    public static final String PROJECTION_POLICY =
            "SAME_LOCAL_MONTH_DAY_FROM_2026_REFERENCE_V1";
    public static final String R3_R4_ALLOCATION_POLICY =
            "LCK_R3_R4_TEN_MATCHDAYS_LINEAR_INCLUSIVE_WINDOW_V1";

    private static final Set<String> RULE_STATUSES = Set.of(
            "RULE_SOURCE_COMPLETE", "RULE_SOURCE_INCOMPLETE", "PRODUCT_POLICY_REQUIRED");
    private static final Set<String> SELECTORS = Set.of(
            "R1_R2_RANK", "MATCH_WINNER", "MATCH_LOSER", "PLAY_IN_SEED");
    private static final Set<String> EXPECTED = Set.of(
            "LCK_CUP", "LCK_REGULAR_R1_R2", "LCK_ROAD_TO_MSI",
            "LCK_REGULAR_R3_R4", "LCK_PLAY_IN", "LCK_PLAYOFFS", "FIRST_STAND",
            "MSI", "EWC_LOL", "WORLDS", "ASIAN_GAMES_LOL_RELEASE");
    private static final Map<String, String> RAW_HASHES = Map.of(
            "README", "853851cb54843a6b5393d89915220220faada8b9284593822bd271af837bab26",
            "CALENDAR_FORMATS", "b47a681950382b3a67be7d4d7d43ed957796470b667c490dc4ce51e2bf3f7e01",
            "OFFICIAL_REPORT", "86b16a278d09763260bdd46b0be1047146eca02c2ebd893f66be6e74f7812b0a",
            "SOURCE_LEDGER", "0dd2a2818d24d3e212e9f00b51790b8f599b0b57ef67e23486491d80a2dd09b6");

    private final ResourceBody body;
    private final Map<String, CompetitionRule> indexed;

    @org.springframework.beans.factory.annotation.Autowired
    public CareerCompetitionRules(ObjectMapper mapper) {
        this(load(mapper));
    }

    CareerCompetitionRules(ResourceBody body) {
        this.body = Objects.requireNonNull(body, "body");
        LinkedHashMap<String, CompetitionRule> values = new LinkedHashMap<>();
        body.competitions().forEach(value -> values.put(value.competitionId(), value));
        this.indexed = Map.copyOf(values);
    }

    public CompetitionRule rule(String competitionId) {
        CompetitionRule value = indexed.get(competitionId);
        if (value == null) throw new IllegalArgumentException("UNKNOWN_COMPETITION");
        return value;
    }

    public List<CompetitionRule> competitions() { return body.competitions(); }
    public String resourceHash() { return RESOURCE_HASH; }
    public Map<String, String> rawSources() { return body.rawSources(); }

    public LocalDate projectDate(int year, String monthDay) {
        if (year < 2026 || year > 9999) throw new IllegalArgumentException("seasonYear");
        MonthDay value = MonthDay.parse("--" + monthDay);
        if (!value.isValidYear(year)) throw new IllegalArgumentException("monthDay");
        return value.atYear(year);
    }

    private static ResourceBody load(ObjectMapper mapper) {
        InputStream input = CareerCompetitionRules.class.getResourceAsStream(RESOURCE);
        if (input == null) throw new IllegalStateException("Competition rule resource missing");
        try (input) {
            byte[] bytes = input.readAllBytes();
            if (!RESOURCE_HASH.equals(sha256(bytes))) {
                throw new IllegalStateException("Competition rule resource hash mismatch");
            }
            ResourceBody value = mapper.readValue(bytes, ResourceBody.class);
            validate(value);
            return value;
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
            throw new IllegalStateException("Competition rule provenance mismatch: "
                    + (value == null ? "null" : value.schemaVersion() + "|"
                    + value.ruleVersion() + "|" + value.gamePolicyVersion() + "|"
                    + value.projectionPolicy() + "|" + value.r3r4AllocationPolicy()
                    + "|" + value.rawSources()));
        }
        Set<String> ids = new HashSet<>();
        for (CompetitionRule competition : value.competitions()) {
            if (!ids.add(required(competition.competitionId()))
                    || !RULE_STATUSES.contains(competition.ruleStatus())
                    || competition.sourceIds().isEmpty()
                    || competition.hardFearless() == null
                    && competition.competitionId().startsWith("LCK_")
                    && !"LCK_PLAYOFFS".equals(competition.competitionId())) {
                throw new IllegalStateException("Competition rule identity mismatch");
            }
            Set<String> matchIds = new HashSet<>();
            for (MatchRule match : competition.matches()) {
                if (!matchIds.add(required(match.matchId()))) {
                    throw new IllegalStateException("Duplicate competition match");
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
                || rule(value, "LCK_ROAD_TO_MSI").matches().size() != 5
                || rule(value, "LCK_PLAY_IN").matches().size() != 3
                || rule(value, "LCK_PLAYOFFS").scheduledMonthDays().size() != 10
                || !"LCK_PLAYOFF_BRACKET_RULE_SOURCE_INCOMPLETE".equals(
                rule(value, "LCK_PLAYOFFS").blockingReason())
                || !"INITIAL_CYCLE_PRIOR_SEASON_RESULT_REQUIRED".equals(
                rule(value, "LCK_CUP").blockingReason())) {
            throw new IllegalStateException("Competition rule set mismatch");
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
        if (("R1_R2_RANK".equals(selector.type())
                || "PLAY_IN_SEED".equals(selector.type()))) {
            int rank = Integer.parseInt(selector.value());
            if (rank < 1 || rank > 10) throw new IllegalStateException(
                    "Competition seed out of range");
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
            String schemaVersion,
            String ruleVersion,
            String gamePolicyVersion,
            String projectionPolicy,
            String r3r4AllocationPolicy,
            Map<String, String> rawSources,
            List<CompetitionRule> competitions
    ) {
        public ResourceBody {
            rawSources = Map.copyOf(rawSources);
            competitions = List.copyOf(competitions);
        }
    }

    public record CompetitionRule(
            String competitionId,
            String ruleStatus,
            String blockingReason,
            List<String> sourceIds,
            String seriesFormat,
            Boolean hardFearless,
            List<MatchRule> matches,
            List<String> scheduledMonthDays
    ) {
        public CompetitionRule {
            sourceIds = List.copyOf(sourceIds);
            matches = List.copyOf(matches);
            scheduledMonthDays = List.copyOf(scheduledMonthDays);
        }
    }

    public record MatchRule(
            String matchId,
            String monthDay,
            ParticipantSelector first,
            ParticipantSelector second,
            List<String> winnerOutputs,
            List<String> loserOutputs
    ) {
        public MatchRule {
            winnerOutputs = winnerOutputs == null ? List.of() : List.copyOf(winnerOutputs);
            loserOutputs = loserOutputs == null ? List.of() : List.copyOf(loserOutputs);
        }
    }

    public record ParticipantSelector(String type, String value) {}
}
