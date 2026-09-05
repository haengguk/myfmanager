package com.lolfm.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Validated, year-neutral Career calendar template and deterministic projector. */
@Component
public final class CareerCalendarTemplate {
    public static final String RESOURCE =
            "/calendar/lck-career-calendar-reference-2026-v1.json";
    public static final String RESOURCE_SCHEMA =
            "CAREER_CALENDAR_TEMPLATE_RESOURCE_V1";
    public static final String VERSION = "lck-career-calendar-reference-2026-v1";
    public static final String HASH_ALGORITHM =
            "SHA256_CANONICAL_JSON_TEMPLATE_BODY_V1";
    public static final String HASH =
            "34a837ad384c49518093cc045d054540b889292002f9b71c01d53c20e1382e38";
    public static final String PROJECTION_POLICY =
            "SAME_LOCAL_MONTH_DAY_FROM_2026_REFERENCE_V1";
    public static final String ANCHOR_ALGORITHM =
            "FIRST_FULL_CYCLE_AFTER_CURRENT_DATE_V1";
    public static final String FIXTURE_ALLOCATION_POLICY =
            "ROUND_LINEAR_INCLUSIVE_WINDOW_ONE_SLOT_PER_ROUND_V1";
    public static final String FIXTURE_OVERLAY_SCHEMA_V1 =
            "CAREER_R1_R2_FIXTURE_OVERLAY_V1";
    public static final String FIXTURE_OVERLAY_SCHEMA_V2 =
            "CAREER_R1_R2_FIXTURE_OVERLAY_PROVENANCE_V2";
    public static final String FIXTURE_OVERLAY_HASH_ALGORITHM_V2 =
            "SHA256_UTF8_EXPLICIT_ORDERED_R1_R2_OVERLAY_PROVENANCE_V2";
    public static final String CALENDAR_SCHEMA = "CAREER_CALENDAR_STATE_V1";
    public static final String STATE_HASH_ALGORITHM =
            "CAREER_CALENDAR_STATE_SHA256_CANONICAL_V1";
    public static final String ADVANCE_COMMAND_SCHEMA =
            "CAREER_CALENDAR_ADVANCE_COMMAND_V1";
    public static final int REFERENCE_YEAR = 2026;
    public static final int UPCOMING_LIMIT = 8;

    private static final Set<String> OFFICIAL_STATUSES = Set.of(
            "OFFICIAL_CONFIRMED", "OFFICIAL_BY_NO_CHANGE_STATEMENT",
            "OFFICIAL_PARTIAL", "DERIVED", "OFFICIAL_PENDING", "SUPERSEDED");
    private static final Set<String> PARTICIPATION_TYPES = Set.of(
            "ALL_LCK", "RANKING_QUALIFIED", "REGION_SLOT", "NATIONAL_TEAM_RELEASE");
    private static final Set<String> EXECUTION_STATUSES = Set.of(
            "LINKED_EXISTING_LEAGUE_FIXTURES",
            "FORMAT_DEFINED_EXECUTION_NOT_IMPLEMENTED");
    private static final Set<String> COMPETITION_SERIES_EXECUTION_TEMPLATE_IDS = Set.of(
            "LCK_CUP", "LCK_ROAD_TO_MSI", "LCK_REGULAR_R3_R4", "LCK_PLAY_IN", "LCK_PLAYOFFS", "FIRST_STAND", "MSI", "EWC_LOL", "WORLDS");
    private static final Set<String> EXPECTED_TEMPLATE_IDS = Set.of(
            "LCK_CUP", "FIRST_STAND", "LCK_REGULAR_R1_R2", "LCK_ROAD_TO_MSI",
            "MSI", "EWC_LOL", "LCK_REGULAR_R3_R4", "LCK_PLAY_IN",
            "LCK_PLAYOFFS", "ASIAN_GAMES_LOL_RELEASE", "WORLDS");

    private final Envelope resource;

    @org.springframework.beans.factory.annotation.Autowired
    public CareerCalendarTemplate(ObjectMapper mapper) {
        this(load(mapper, CareerCalendarTemplate.class.getResourceAsStream(RESOURCE)));
    }

    CareerCalendarTemplate(Envelope resource) {
        this.resource = Objects.requireNonNull(resource, "resource");
    }

    static Envelope load(ObjectMapper mapper, InputStream input) {
        Objects.requireNonNull(mapper, "mapper");
        if (input == null) throw new IllegalStateException("Career calendar resource missing");
        byte[] bytes;
        try (input) {
            bytes = input.readAllBytes();
        } catch (IOException failure) {
            throw new IllegalStateException("Career calendar resource read failure", failure);
        }
        try {
            JsonNode root = mapper.readTree(bytes);
            Envelope envelope = mapper.treeToValue(root, Envelope.class);
            String measured = sha256(mapper.writeValueAsBytes(root.path("template")));
            validate(envelope, measured);
            return envelope;
        } catch (IOException failure) {
            throw new IllegalStateException("Career calendar resource parse failure", failure);
        }
    }

    public String version() { return resource.templateVersion(); }
    public String templateHash() { return resource.templateHash(); }
    public TemplateBody body() { return resource.template(); }

    public int anchorYear(LocalDate currentDate) {
        Objects.requireNonNull(currentDate, "currentDate");
        int candidate = Math.max(currentDate.getYear(), REFERENCE_YEAR);
        while (!firstEventDate(candidate).isAfter(currentDate)) {
            candidate = Math.addExact(candidate, 1);
        }
        return candidate;
    }

    public ProjectedCalendar project(int seasonYear) {
        if (seasonYear < REFERENCE_YEAR || seasonYear > 9999) {
            throw new IllegalArgumentException("calendarSeasonYear");
        }
        List<ProjectedEvent> events = resource.template().competitions().stream()
                .map(value -> project(value, seasonYear)).toList();
        for (int index = 1; index < events.size(); index++) {
            if (events.get(index).startDate().isBefore(events.get(index - 1).startDate())) {
                throw new IllegalStateException("Projected event order reversed");
            }
        }
        return new ProjectedCalendar(seasonYear,
                seasonYear == REFERENCE_YEAR ? "REFERENCE_YEAR_SOURCE"
                        : "GAME_PROJECTED_FROM_2026_TEMPLATE", events);
    }

    public FixtureOverlay overlay(
            int seasonYear,
            String leagueId,
            String seasonId,
            String scheduleIdentity,
            List<FixtureInput> fixtures
    ) {
        ProjectedEvent window = project(seasonYear).events().stream()
                .filter(value -> "LCK_REGULAR_R1_R2".equals(value.templateId()))
                .findFirst().orElseThrow();
        if (fixtures == null || fixtures.size() != 90) {
            throw new IllegalStateException("R1_R2_FIXTURE_COUNT_MISMATCH");
        }
        List<FixtureInput> ordered = fixtures.stream()
                .sorted(Comparator.comparingInt(FixtureInput::roundNumber)
                        .thenComparing(FixtureInput::fixtureId)).toList();
        Set<String> ids = new HashSet<>();
        Map<Integer, Set<String>> teamsByRound = new HashMap<>();
        ArrayList<FixtureDate> dates = new ArrayList<>();
        long inclusiveSpan = java.time.temporal.ChronoUnit.DAYS.between(
                window.startDate(), window.endDate());
        for (FixtureInput fixture : ordered) {
            if (fixture.roundNumber() < 1 || fixture.roundNumber() > 18
                    || !ids.add(required(fixture.fixtureId(), "fixtureId"))
                    || fixture.firstTeamCode().equals(fixture.secondTeamCode())
                    || !("FULL_AUTO".equals(fixture.executionMode())
                    || "PLAYER_CONTROLLED".equals(fixture.executionMode()))) {
                throw new IllegalStateException("R1_R2_FIXTURE_IDENTITY_MISMATCH");
            }
            requireIdentity(fixture.fixtureId(), "fixture_", "fixtureId");
            requireIdentity(fixture.boundSeriesId(), "series_", "boundSeriesId");
            requireTeamCode(fixture.firstTeamCode());
            requireTeamCode(fixture.secondTeamCode());
            Set<String> roundTeams = teamsByRound.computeIfAbsent(
                    fixture.roundNumber(), ignored -> new HashSet<>());
            if (!roundTeams.add(required(fixture.firstTeamCode(), "firstTeamCode"))
                    || !roundTeams.add(required(fixture.secondTeamCode(),
                    "secondTeamCode"))) {
                throw new IllegalStateException("R1_R2_TEAM_SLOT_CONFLICT");
            }
            long offset = (long) (fixture.roundNumber() - 1) * inclusiveSpan / 17L;
            LocalDate date = window.startDate().plusDays(offset);
            dates.add(new FixtureDate(fixture.fixtureId(), fixture.roundNumber(), date,
                    fixture.executionMode(), fixture.firstTeamCode(),
                    fixture.secondTeamCode()));
        }
        if (teamsByRound.size() != 18
                || teamsByRound.values().stream().anyMatch(value -> value.size() != 10)
                || dates.stream().anyMatch(value -> value.date().isBefore(window.startDate())
                || value.date().isAfter(window.endDate()))) {
            throw new IllegalStateException("R1_R2_ROUND_STRUCTURE_MISMATCH");
        }
        String canonicalLeagueId = requireIdentity(leagueId, "league_", "leagueId");
        String canonicalSeasonId = requireIdentity(seasonId, "season_", "seasonId");
        requireSha256(scheduleIdentity, "scheduleIdentity");
        StringBuilder legacyCanonical = new StringBuilder()
                .append("overlaySchema=").append(FIXTURE_OVERLAY_SCHEMA_V1).append('\n')
                .append("templateHash=").append(templateHash()).append('\n')
                .append("seasonYear=").append(seasonYear).append('\n')
                .append("policy=").append(FIXTURE_ALLOCATION_POLICY).append('\n');
        dates.forEach(value -> legacyCanonical.append(value.roundNumber()).append('|')
                .append(value.fixtureId()).append('|').append(value.date()).append('\n'));
        StringBuilder canonicalV2 = new StringBuilder()
                .append("overlaySchema=").append(FIXTURE_OVERLAY_SCHEMA_V2).append('\n')
                .append("hashAlgorithm=").append(FIXTURE_OVERLAY_HASH_ALGORITHM_V2)
                .append('\n')
                .append("calendarSchema=").append(CALENDAR_SCHEMA).append('\n')
                .append("templateVersion=").append(version()).append('\n')
                .append("templateHash=").append(templateHash()).append('\n')
                .append("calendarSeasonYear=").append(seasonYear).append('\n')
                .append("allocationPolicy=").append(FIXTURE_ALLOCATION_POLICY).append('\n')
                .append("leagueId=").append(canonicalLeagueId).append('\n')
                .append("seasonId=").append(canonicalSeasonId).append('\n')
                .append("scheduleIdentity=").append(scheduleIdentity).append('\n')
                .append("fixtureCount=").append(ordered.size()).append('\n');
        for (int index = 0; index < ordered.size(); index++) {
            FixtureInput input = ordered.get(index);
            FixtureDate date = dates.get(index);
            canonicalV2.append("fixture[").append(index).append("]=")
                    .append(input.fixtureId()).append('|')
                    .append(input.roundNumber()).append('|')
                    .append(date.date()).append('|')
                    .append(input.executionMode()).append('|')
                    .append(input.firstTeamCode()).append('|')
                    .append(input.secondTeamCode()).append('|')
                    .append(input.fixtureRootSeed()).append('|')
                    .append(input.boundSeriesId()).append('\n');
        }
        OverlayProvenanceV2 provenance = new OverlayProvenanceV2(
                FIXTURE_OVERLAY_SCHEMA_V2, FIXTURE_OVERLAY_HASH_ALGORITHM_V2,
                canonicalLeagueId, canonicalSeasonId, scheduleIdentity,
                sha256(canonicalV2.toString().getBytes(StandardCharsets.UTF_8)));
        return new FixtureOverlay(FIXTURE_OVERLAY_SCHEMA_V1,
                FIXTURE_ALLOCATION_POLICY, sha256(legacyCanonical.toString()
                .getBytes(StandardCharsets.UTF_8)), provenance, List.copyOf(dates));
    }

    public String stateHash(
            String careerId,
            int seasonYear,
            LocalDate currentDate,
            int eventCursor,
            long revision,
            String lastProcessedEventId,
            LocalDate lastProcessedDate,
            String lifecycleStatus,
            String blockingReason
    ) {
        String canonical = "stateHashAlgorithm=" + STATE_HASH_ALGORITHM + '\n'
                + "calendarSchema=" + CALENDAR_SCHEMA + '\n'
                + "careerId=" + required(careerId, "careerId") + '\n'
                + "templateVersion=" + version() + '\n'
                + "templateHash=" + templateHash() + '\n'
                + "projectionPolicy=" + PROJECTION_POLICY + '\n'
                + "anchorAlgorithm=" + ANCHOR_ALGORITHM + '\n'
                + "fixtureAllocationPolicy=" + FIXTURE_ALLOCATION_POLICY + '\n'
                + "seasonYear=" + seasonYear + '\n'
                + "currentDate=" + currentDate + '\n'
                + "eventCursor=" + eventCursor + '\n'
                + "revision=" + revision + '\n'
                + "lastProcessedEventId=" + nullable(lastProcessedEventId) + '\n'
                + "lastProcessedDate=" + nullable(lastProcessedDate) + '\n'
                + "lifecycleStatus=" + required(lifecycleStatus, "lifecycleStatus") + '\n'
                + "blockingReason=" + nullable(blockingReason) + '\n';
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    public String advancePayloadHash(
            String careerId,
            long expectedRevision,
            String mode
    ) {
        String canonical = "commandSchema=" + ADVANCE_COMMAND_SCHEMA + '\n'
                + "careerId=" + required(careerId, "careerId") + '\n'
                + "expectedRevision=" + expectedRevision + '\n'
                + "mode=" + required(mode, "mode") + '\n';
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    public int eventCursor(ProjectedCalendar calendar, LocalDate currentDate) {
        int cursor = 0;
        while (cursor < calendar.events().size()
                && calendar.events().get(cursor).endDate().isBefore(currentDate)) {
            cursor++;
        }
        return cursor;
    }

    public ProjectedEvent currentEvent(ProjectedCalendar calendar, LocalDate currentDate) {
        return calendar.events().stream().filter(value -> !currentDate.isBefore(
                value.startDate()) && !currentDate.isAfter(value.endDate()))
                .findFirst().orElse(null);
    }

    public ProjectedEvent nextEvent(ProjectedCalendar calendar, LocalDate currentDate) {
        return calendar.events().stream().filter(value -> value.startDate()
                .isAfter(currentDate)).findFirst().orElse(null);
    }

    private LocalDate firstEventDate(int year) {
        Competition first = resource.template().competitions().getFirst();
        return projectDate(first.startMonthDay(), year, "firstEvent");
    }

    private ProjectedEvent project(Competition value, int year) {
        LocalDate start = projectDate(value.startMonthDay(), year, value.templateId());
        LocalDate end = projectDate(value.endMonthDay(), year, value.templateId());
        List<ProjectedStage> stages = value.stages().stream().map(stage ->
                new ProjectedStage(stage.stageId(), stage.displayNameKo(),
                        projectNullable(stage.startMonthDay(), year, stage.stageId()),
                        projectNullable(stage.endMonthDay(), year, stage.stageId()),
                        stage.officialStatus(), stage.teamCount(), stage.seriesCount(),
                        stage.format(), stage.seriesRules())).toList();
        String id = "calendar_event_" + sha256(("eventSchema=CAREER_CALENDAR_EVENT_V1\n"
                + "templateHash=" + templateHash() + '\n'
                + "templateId=" + value.templateId() + '\n'
                + "seasonYear=" + year + '\n').getBytes(StandardCharsets.UTF_8));
        return new ProjectedEvent(id, value.templateId(), value.sourceReferenceId(),
                year + " " + value.displayNameKo(), start, end, value.timezone(),
                value.timezoneScope(), value.locations(), value.officialStatus(),
                year == REFERENCE_YEAR ? "REFERENCE_YEAR_SOURCE"
                        : "GAME_PROJECTED_FROM_2026_TEMPLATE",
                value.participationType(), value.participation(), value.teamCount(),
                value.seriesCount(), value.format(), value.seriesRules(),
                value.draftMode(), value.draftStatus(), projectedExecutionStatus(value),
                stages);
    }

    private static String projectedExecutionStatus(Competition value) {
        if ("ASIAN_GAMES_LOL_RELEASE".equals(value.templateId())) return "EXCLUDED_BY_GAME_POLICY";
        return COMPETITION_SERIES_EXECUTION_TEMPLATE_IDS.contains(value.templateId())
                ? "LINKED_COMPETITION_SERIES_EXECUTION"
                : value.executionStatus();
    }

    private static void validate(Envelope value, String measuredHash) {
        if (value == null || !RESOURCE_SCHEMA.equals(value.schemaVersion())
                || !VERSION.equals(value.templateVersion())
                || !HASH_ALGORITHM.equals(value.templateHashAlgorithm())
                || !HASH.equals(value.templateHash())
                || !value.templateHash().equals(measuredHash)) {
            throw new IllegalStateException("Career calendar envelope identity mismatch");
        }
        TemplateBody body = Objects.requireNonNull(value.template(), "template");
        Counts counts = Objects.requireNonNull(body.counts(), "counts");
        if (body.referenceYear() != REFERENCE_YEAR
                || !"2026-08-23".equals(body.sourceAsOf())
                || !"2026-08-24".equals(body.referenceCatalogSnapshotAt())
                || !PROJECTION_POLICY.equals(body.projectionPolicy())
                || !ANCHOR_ALGORITHM.equals(body.anchorAlgorithm())
                || !FIXTURE_ALLOCATION_POLICY.equals(body.fixtureAllocationPolicy())
                || counts.sources() != 15 || counts.calendarDefinitions() != 11
                || counts.qualificationEdges() != 6
                || counts.derivedRestWindows() != 7
                || counts.pendingOfficialFields() != 6
                || body.evidenceSources().size() != counts.sources()
                || body.competitions().size() != counts.calendarDefinitions()
                || body.qualificationEdges().size() != counts.qualificationEdges()
                || body.derivedRestWindows().size() != counts.derivedRestWindows()
                || body.pendingOfficialFields().size() != counts.pendingOfficialFields()
                || !new HashSet<>(body.statusLegend()).equals(OFFICIAL_STATUSES)) {
            throw new IllegalStateException("Career calendar resource contract mismatch");
        }
        Map<String, String> requiredRaw = Map.of(
                "README", "853851cb54843a6b5393d89915220220faada8b9284593822bd271af837bab26",
                "CALENDAR_FORMATS", "b47a681950382b3a67be7d4d7d43ed957796470b667c490dc4ce51e2bf3f7e01",
                "OFFICIAL_REPORT", "86b16a278d09763260bdd46b0be1047146eca02c2ebd893f66be6e74f7812b0a",
                "SOURCE_LEDGER", "0dd2a2818d24d3e212e9f00b51790b8f599b0b57ef67e23486491d80a2dd09b6");
        Map<String, String> actualRaw = new LinkedHashMap<>();
        body.rawSources().forEach(source -> actualRaw.put(source.role(),
                source.rawSha256()));
        if (!actualRaw.equals(requiredRaw)) {
            throw new IllegalStateException("Career calendar raw source identity mismatch");
        }
        Set<String> sourceIds = new HashSet<>();
        body.evidenceSources().forEach(source -> {
            if (!sourceIds.add(required(source.id(), "sourceId"))
                    || source.priority() < 1 || source.priority() > 4) {
                throw new IllegalStateException("Career calendar source mismatch");
            }
        });
        Set<String> templateIds = new HashSet<>();
        LocalDate previous = null;
        for (Competition competition : body.competitions()) {
            if (!templateIds.add(required(competition.templateId(), "templateId"))
                    || competition.templateId().matches(".*\\d{4}.*")
                    || !OFFICIAL_STATUSES.contains(competition.officialStatus())
                    || !PARTICIPATION_TYPES.contains(competition.participationType())
                    || !EXECUTION_STATUSES.contains(competition.executionStatus())
                    || !sourceIds.containsAll(competition.sourceIds())) {
                throw new IllegalStateException("Career calendar competition mismatch");
            }
            LocalDate start = projectDate(competition.startMonthDay(), REFERENCE_YEAR,
                    competition.templateId());
            LocalDate end = projectDate(competition.endMonthDay(), REFERENCE_YEAR,
                    competition.templateId());
            if (end.isBefore(start) || previous != null && start.isBefore(previous)) {
                throw new IllegalStateException("Career calendar date order mismatch");
            }
            previous = start;
            if ("SINGLE_IANA_ZONE".equals(competition.timezoneScope())) {
                ZoneId.of(required(competition.timezone(), "timezone"));
            } else if (!"MULTI_ZONE".equals(competition.timezoneScope())
                    || competition.timezone() != null) {
                throw new IllegalStateException("Career calendar timezone mismatch");
            }
            for (Stage stage : competition.stages()) {
                if (!OFFICIAL_STATUSES.contains(stage.officialStatus())) {
                    throw new IllegalStateException("Career calendar stage status mismatch");
                }
                LocalDate stageStart = projectNullable(stage.startMonthDay(),
                        REFERENCE_YEAR, stage.stageId());
                LocalDate stageEnd = projectNullable(stage.endMonthDay(),
                        REFERENCE_YEAR, stage.stageId());
                if ((stageStart == null) != (stageEnd == null)
                        || stageStart != null && (stageEnd.isBefore(stageStart)
                        || stageStart.isBefore(start) || stageEnd.isAfter(end))) {
                    throw new IllegalStateException("Career calendar stage date mismatch");
                }
            }
        }
        if (!templateIds.equals(EXPECTED_TEMPLATE_IDS)
                || templateIds.stream().anyMatch(valueId -> valueId.contains("KESPA"))) {
            throw new IllegalStateException("Career calendar definition set mismatch");
        }
        body.qualificationEdges().forEach(edge -> {
            if (!templateIds.contains(edge.from()) || !templateIds.contains(edge.to())
                    || !OFFICIAL_STATUSES.contains(edge.officialStatus())) {
                throw new IllegalStateException("Career qualification edge mismatch");
            }
        });
        body.derivedRestWindows().forEach(window -> {
            if (!templateIds.contains(window.after())
                    || !templateIds.contains(window.before())
                    || window.fullFreeDays() < 0
                    || !"DERIVED".equals(window.officialStatus())) {
                throw new IllegalStateException("Career rest window mismatch");
            }
            LocalDate start = projectDate(window.startMonthDay(), REFERENCE_YEAR,
                    "restStart");
            LocalDate end = projectDate(window.endMonthDay(), REFERENCE_YEAR,
                    "restEnd");
            long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
            if (end.isBefore(start) || days != window.fullFreeDays()) {
                throw new IllegalStateException("Career rest window duration mismatch");
            }
        });
        if (body.pendingOfficialFields().stream().anyMatch(field ->
                required(field.reason(), "pendingReason").isBlank())) {
            throw new IllegalStateException("Career pending official field mismatch");
        }
    }

    private static LocalDate projectDate(String value, int year, String field) {
        MonthDay monthDay;
        try {
            monthDay = MonthDay.parse("--" + required(value, field));
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Invalid Career calendar month/day", failure);
        }
        if (!monthDay.isValidYear(year)) {
            throw new IllegalStateException("Career calendar leap-day projection failure");
        }
        return monthDay.atYear(year);
    }

    private static LocalDate projectNullable(String value, int year, String field) {
        return value == null ? null : projectDate(value, year, field);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalStateException(field);
        return value;
    }

    private static String requireIdentity(String value, String prefix, String field) {
        String identity = required(value, field);
        if (!identity.matches(java.util.regex.Pattern.quote(prefix) + "[0-9a-f]{64}")) {
            throw new IllegalStateException(field);
        }
        return identity;
    }

    private static void requireSha256(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException(field);
        }
    }

    private static void requireTeamCode(String value) {
        if (value == null || !value.matches("[A-Z0-9]{2,16}")) {
            throw new IllegalStateException("teamCode");
        }
    }

    private static String nullable(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public record Envelope(String schemaVersion, String templateVersion,
                           String templateHashAlgorithm, String templateHash,
                           TemplateBody template) {}

    public record TemplateBody(int referenceYear, String sourceAsOf,
                               String referenceCatalogSnapshotAt,
                               String sourceBundleVersion, String projectionPolicy,
                               String anchorAlgorithm, String fixtureAllocationPolicy,
                               List<RawSource> rawSources, Counts counts,
                               List<String> statusLegend,
                               List<EvidenceSource> evidenceSources,
                               List<Competition> competitions,
                               List<QualificationEdge> qualificationEdges,
                               List<RestWindow> derivedRestWindows,
                               List<PendingOfficialField> pendingOfficialFields) {
        public TemplateBody {
            rawSources = List.copyOf(rawSources);
            statusLegend = List.copyOf(statusLegend);
            evidenceSources = List.copyOf(evidenceSources);
            competitions = List.copyOf(competitions);
            qualificationEdges = List.copyOf(qualificationEdges);
            derivedRestWindows = List.copyOf(derivedRestWindows);
            pendingOfficialFields = List.copyOf(pendingOfficialFields);
        }
    }

    public record RawSource(String role, String fileName, String rawSha256) {}
    public record Counts(int sources, int calendarDefinitions,
                         int qualificationEdges, int derivedRestWindows,
                         int pendingOfficialFields) {}
    public record EvidenceSource(String id, String title, String organization,
                                 int priority) {}
    public record Competition(String templateId, String sourceReferenceId,
                              String displayNameKo, String startMonthDay,
                              String endMonthDay, String timezone,
                              String timezoneScope, List<String> locations,
                              String officialStatus, String participationType,
                              String participation, Integer teamCount,
                              Integer seriesCount, String format,
                              List<String> seriesRules, String draftMode,
                              String draftStatus, String executionStatus,
                              List<String> sourceIds, List<Stage> stages) {
        public Competition {
            locations = List.copyOf(locations);
            seriesRules = List.copyOf(seriesRules);
            sourceIds = List.copyOf(sourceIds);
            stages = List.copyOf(stages);
        }
    }
    public record Stage(String stageId, String displayNameKo,
                        String startMonthDay, String endMonthDay,
                        String officialStatus, Integer teamCount,
                        Integer seriesCount, String format,
                        List<String> seriesRules) {
        public Stage { seriesRules = List.copyOf(seriesRules); }
    }
    public record QualificationEdge(String from, String to, String rule,
                                    String officialStatus) {}
    public record RestWindow(String after, String before, String startMonthDay,
                             String endMonthDay, int fullFreeDays,
                             String officialStatus) {}
    public record PendingOfficialField(String id, String field, String reason) {}

    public record ProjectedCalendar(int seasonYear, String projectionStatus,
                                    List<ProjectedEvent> events) {
        public ProjectedCalendar { events = List.copyOf(events); }
    }
    public record ProjectedEvent(String eventId, String templateId,
                                 String sourceReferenceId, String displayNameKo,
                                 LocalDate startDate, LocalDate endDate,
                                 String timezone, String timezoneScope,
                                 List<String> locations, String officialStatus,
                                 String projectionStatus,
                                 String participationType, String participation,
                                 Integer teamCount, Integer seriesCount,
                                 String format, List<String> seriesRules,
                                 String draftMode, String draftStatus,
                                 String executionStatus,
                                 List<ProjectedStage> stages) {
        public ProjectedEvent {
            locations = List.copyOf(locations);
            seriesRules = List.copyOf(seriesRules);
            stages = List.copyOf(stages);
        }
    }
    public record ProjectedStage(String stageId, String displayNameKo,
                                 LocalDate startDate, LocalDate endDate,
                                 String officialStatus, Integer teamCount,
                                 Integer seriesCount, String format,
                                 List<String> seriesRules) {
        public ProjectedStage { seriesRules = List.copyOf(seriesRules); }
    }
    public record FixtureInput(String fixtureId, int roundNumber,
                               String executionMode, String firstTeamCode,
                               String secondTeamCode, long fixtureRootSeed,
                               String boundSeriesId) {}
    public record FixtureDate(String fixtureId, int roundNumber, LocalDate date,
                              String executionMode, String firstTeamCode,
                              String secondTeamCode) {}
    public record OverlayProvenanceV2(
            String schemaVersion, String hashAlgorithm, String leagueId,
            String seasonId, String scheduleIdentity, String overlayHash
    ) {}
    public record FixtureOverlay(String schemaVersion, String allocationPolicy,
                                 String overlayHash, OverlayProvenanceV2 provenanceV2,
                                 List<FixtureDate> fixtures) {
        public FixtureOverlay { fixtures = List.copyOf(fixtures); }
    }
}
