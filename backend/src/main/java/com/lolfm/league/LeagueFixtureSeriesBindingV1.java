package com.lolfm.league;

import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.application.SeriesFormat;
import com.lolfm.draft.SeriesDraftHistory;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Server-created canonical authority for one PLAYER_CONTROLLED fixture Series.
 * The constructor is private so request/DTO callers cannot author League context.
 */
public final class LeagueFixtureSeriesBindingV1 {
    public static final String SCHEMA = "AI_LEAGUE_FIXTURE_SERIES_BINDING_V1";
    public static final String HASH_ALGORITHM =
            "SHA256_UTF8_EXPLICIT_ORDERED_LEAGUE_SERIES_BINDING_LINES_TRAILING_NEWLINE_V1";
    public static final int MAX_CANONICAL_BYTES = 32 * 1024;

    private final String leagueId;
    private final String seasonId;
    private final String fixtureId;
    private final long expectedSeasonRevision;
    private final String reservationIdentity;
    private final String boundSeriesId;
    private final String firstTeamCode;
    private final String secondTeamCode;
    private final String managedTeamCode;
    private final SeriesFormat seriesFormat;
    private final String game1BlueTeamCode;
    private final String game1RedTeamCode;
    private final long fixtureRootSeed;
    private final String seedAnchorTeamCode;
    private final String initialHistoryHash;
    private final String scheduleIdentity;
    private final String productDecisionHash;
    private final String frozenSnapshotIdentity;
    private final String firstTeamSnapshotIdentity;
    private final String secondTeamSnapshotIdentity;
    private final String playerResourceIdentity;
    private final String championDraftResourceIdentity;
    private final String matchupCompositionResourceIdentity;
    private final String productionRuntimeIdentity;
    private final String resourceProvenanceHash;
    private final String policyId;
    private final String policyHash;
    private final String runtimeProfileId;
    private final String configurationHash;
    private final String activeGameplayRulesVersion;
    private final String engineImplementationVersion;
    private final long bindingRevision;
    private final String bindingHash;

    private LeagueFixtureSeriesBindingV1(
            String leagueId,
            String seasonId,
            String fixtureId,
            long expectedSeasonRevision,
            String reservationIdentity,
            String boundSeriesId,
            String firstTeamCode,
            String secondTeamCode,
            String managedTeamCode,
            SeriesFormat seriesFormat,
            String game1BlueTeamCode,
            String game1RedTeamCode,
            long fixtureRootSeed,
            String seedAnchorTeamCode,
            String initialHistoryHash,
            String scheduleIdentity,
            String productDecisionHash,
            String frozenSnapshotIdentity,
            String firstTeamSnapshotIdentity,
            String secondTeamSnapshotIdentity,
            String playerResourceIdentity,
            String championDraftResourceIdentity,
            String matchupCompositionResourceIdentity,
            String productionRuntimeIdentity,
            String resourceProvenanceHash,
            String policyId,
            String policyHash,
            String runtimeProfileId,
            String configurationHash,
            String activeGameplayRulesVersion,
            String engineImplementationVersion,
            long bindingRevision,
            String bindingHash
    ) {
        LeagueIdentity.requireLeagueId(leagueId);
        LeagueIdentity.requireSeasonId(seasonId);
        if (fixtureId == null || !fixtureId.matches("fixture_[0-9a-f]{64}")) {
            throw new IllegalArgumentException("fixtureId");
        }
        if (expectedSeasonRevision < 0 || bindingRevision < 0) {
            throw new IllegalArgumentException("binding revision");
        }
        LeagueSeasonFrozenSnapshot.requireSha256(
                reservationIdentity, "reservationIdentity");
        if (boundSeriesId == null || !boundSeriesId.matches("series_[0-9a-f]{64}")) {
            throw new IllegalArgumentException("boundSeriesId");
        }
        LeagueIdentity.requireTeamCode(firstTeamCode);
        LeagueIdentity.requireTeamCode(secondTeamCode);
        LeagueIdentity.requireTeamCode(managedTeamCode);
        LeagueIdentity.requireTeamCode(game1BlueTeamCode);
        LeagueIdentity.requireTeamCode(game1RedTeamCode);
        LeagueIdentity.requireTeamCode(seedAnchorTeamCode);
        if (firstTeamCode.compareTo(secondTeamCode) >= 0
                || !Set.of(firstTeamCode, secondTeamCode).contains(managedTeamCode)
                || !Set.of(firstTeamCode, secondTeamCode)
                .equals(Set.of(game1BlueTeamCode, game1RedTeamCode))
                || !seedAnchorTeamCode.equals(firstTeamCode)) {
            throw new IllegalArgumentException("binding team invariant");
        }
        Objects.requireNonNull(seriesFormat, "seriesFormat");
        LeagueSeasonFrozenSnapshot.requireSha256(initialHistoryHash, "initialHistoryHash");
        if (!initialHistoryHash.equals(SeriesDraftHistory.identityHash(0, Set.of()))) {
            throw new IllegalArgumentException("binding initial history invariant");
        }
        LeagueSeasonFrozenSnapshot.requireSha256(scheduleIdentity, "scheduleIdentity");
        LeagueSeasonFrozenSnapshot.requireSha256(productDecisionHash, "productDecisionHash");
        LeagueSeasonFrozenSnapshot.requireSha256(
                frozenSnapshotIdentity, "frozenSnapshotIdentity");
        LeagueSeasonFrozenSnapshot.requireSha256(
                firstTeamSnapshotIdentity, "firstTeamSnapshotIdentity");
        LeagueSeasonFrozenSnapshot.requireSha256(
                secondTeamSnapshotIdentity, "secondTeamSnapshotIdentity");
        LeagueSeasonFrozenSnapshot.requireSha256(
                playerResourceIdentity, "playerResourceIdentity");
        LeagueSeasonFrozenSnapshot.requireSha256(
                championDraftResourceIdentity, "championDraftResourceIdentity");
        LeagueSeasonFrozenSnapshot.requireSha256(
                matchupCompositionResourceIdentity, "matchupCompositionResourceIdentity");
        LeagueSeasonFrozenSnapshot.requireSha256(
                productionRuntimeIdentity, "productionRuntimeIdentity");
        LeagueSeasonFrozenSnapshot.requireSha256(
                resourceProvenanceHash, "resourceProvenanceHash");
        if (policyId == null || policyId.isBlank()
                || runtimeProfileId == null || runtimeProfileId.isBlank()
                || activeGameplayRulesVersion == null
                || activeGameplayRulesVersion.isBlank()
                || engineImplementationVersion == null
                || engineImplementationVersion.isBlank()) {
            throw new IllegalArgumentException("binding production identity");
        }
        LeagueSeasonFrozenSnapshot.requireSha256(policyHash, "policyHash");
        LeagueSeasonFrozenSnapshot.requireSha256(configurationHash, "configurationHash");
        String payload = payloadText(leagueId, seasonId, fixtureId, expectedSeasonRevision,
                reservationIdentity, boundSeriesId, firstTeamCode, secondTeamCode,
                managedTeamCode, seriesFormat, game1BlueTeamCode, game1RedTeamCode,
                fixtureRootSeed, seedAnchorTeamCode, initialHistoryHash, scheduleIdentity,
                productDecisionHash, frozenSnapshotIdentity, firstTeamSnapshotIdentity,
                secondTeamSnapshotIdentity, playerResourceIdentity,
                championDraftResourceIdentity, matchupCompositionResourceIdentity,
                productionRuntimeIdentity, resourceProvenanceHash, policyId, policyHash,
                runtimeProfileId, configurationHash, activeGameplayRulesVersion,
                engineImplementationVersion, bindingRevision);
        String expectedHash = LeagueIdentity.sha256(payload);
        if (bindingHash != null && !expectedHash.equals(bindingHash)) {
            throw new IllegalArgumentException("Canonical League Series binding hash mismatch");
        }
        this.leagueId = leagueId;
        this.seasonId = seasonId;
        this.fixtureId = fixtureId;
        this.expectedSeasonRevision = expectedSeasonRevision;
        this.reservationIdentity = reservationIdentity;
        this.boundSeriesId = boundSeriesId;
        this.firstTeamCode = firstTeamCode;
        this.secondTeamCode = secondTeamCode;
        this.managedTeamCode = managedTeamCode;
        this.seriesFormat = seriesFormat;
        this.game1BlueTeamCode = game1BlueTeamCode;
        this.game1RedTeamCode = game1RedTeamCode;
        this.fixtureRootSeed = fixtureRootSeed;
        this.seedAnchorTeamCode = seedAnchorTeamCode;
        this.initialHistoryHash = initialHistoryHash;
        this.scheduleIdentity = scheduleIdentity;
        this.productDecisionHash = productDecisionHash;
        this.frozenSnapshotIdentity = frozenSnapshotIdentity;
        this.firstTeamSnapshotIdentity = firstTeamSnapshotIdentity;
        this.secondTeamSnapshotIdentity = secondTeamSnapshotIdentity;
        this.playerResourceIdentity = playerResourceIdentity;
        this.championDraftResourceIdentity = championDraftResourceIdentity;
        this.matchupCompositionResourceIdentity = matchupCompositionResourceIdentity;
        this.productionRuntimeIdentity = productionRuntimeIdentity;
        this.resourceProvenanceHash = resourceProvenanceHash;
        this.policyId = policyId;
        this.policyHash = policyHash;
        this.runtimeProfileId = runtimeProfileId;
        this.configurationHash = configurationHash;
        this.activeGameplayRulesVersion = activeGameplayRulesVersion;
        this.engineImplementationVersion = engineImplementationVersion;
        this.bindingRevision = bindingRevision;
        this.bindingHash = expectedHash;
        if (canonicalBytes().length > MAX_CANONICAL_BYTES) {
            throw new IllegalArgumentException("League Series binding exceeds compact limit");
        }
    }

    static LeagueFixtureSeriesBindingV1 create(
            LeagueSeasonAggregate season,
            LeagueFixture fixture,
            String resourceProvenanceHash
    ) {
        Objects.requireNonNull(season, "season");
        Objects.requireNonNull(fixture, "fixture");
        if (fixture.executionMode() != LeagueFixtureExecutionMode.PLAYER_CONTROLLED
                || season.seasonMode() != LeagueSeasonMode.HYBRID_MANAGER
                || !fixture.containsTeam(season.managedTeamCode())) {
            throw new IllegalArgumentException("PLAYER_SERIES_BINDING_NOT_ELIGIBLE");
        }
        LeagueSeasonFrozenSnapshot snapshot = season.frozenSnapshot();
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();
        String reservation = LeagueIdentity.sha256(
                "reservationSchema=AI_LEAGUE_PLAYER_FIXTURE_RESERVATION_V1\n"
                        + "leagueId=" + season.leagueId() + '\n'
                        + "seasonId=" + season.seasonId() + '\n'
                        + "fixtureId=" + fixture.fixtureId() + '\n'
                        + "expectedSeasonRevision=" + season.revision() + '\n'
                        + "boundSeriesId=" + fixture.boundSeriesId() + '\n');
        return new LeagueFixtureSeriesBindingV1(
                season.leagueId(), season.seasonId(), fixture.fixtureId(), season.revision(),
                reservation, fixture.boundSeriesId(), fixture.firstTeamCode(),
                fixture.secondTeamCode(), season.managedTeamCode(), fixture.seriesFormat(),
                fixture.game1BlueTeamCode(), fixture.game1RedTeamCode(),
                fixture.fixtureRootSeed(), fixture.seedAnchorTeamCode(),
                SeriesDraftHistory.identityHash(0, Set.of()),
                season.schedule().scheduleIdentity(), season.productDecisionHash(),
                snapshot.snapshotIdentity(),
                snapshot.teamSnapshotIdentity(fixture.firstTeamCode()),
                snapshot.teamSnapshotIdentity(fixture.secondTeamCode()),
                snapshot.playerResourceIdentity(), snapshot.championDraftResourceIdentity(),
                snapshot.matchupCompositionResourceIdentity(),
                snapshot.productionRuntimeIdentity(), resourceProvenanceHash,
                policy.policyId(), policy.policyHash(),
                policy.retainedRuntimeProfileId().name(), policy.configurationHash(),
                policy.activeGameplayRulesVersion(), policy.engineImplementationVersion(),
                0, null);
    }

    /** Rebuilds and revalidates a durable canonical binding without live authored inputs. */
    static LeagueFixtureSeriesBindingV1 restoreCanonical(String canonicalText) {
        Objects.requireNonNull(canonicalText, "canonicalText");
        if (!canonicalText.endsWith("\n")
                || canonicalText.getBytes(StandardCharsets.UTF_8).length
                > MAX_CANONICAL_BYTES) {
            throw new IllegalArgumentException("Invalid durable League Series binding");
        }
        Map<String, String> fields = new HashMap<>();
        for (String line : canonicalText.split("\n")) {
            int separator = line.indexOf('=');
            if (separator <= 0 || fields.put(line.substring(0, separator),
                    line.substring(separator + 1)) != null) {
                throw new IllegalArgumentException("Invalid canonical binding field");
            }
        }
        if (!SCHEMA.equals(fields.get("schemaVersion"))
                || !HASH_ALGORITHM.equals(fields.get("canonicalHashAlgorithm"))
                || !LeagueFixtureExecutionMode.PLAYER_CONTROLLED.name().equals(
                fields.get("executionMode"))
                || !"CREATED".equals(fields.get("bindingLifecycleStatus"))) {
            throw new IllegalArgumentException("Unsupported durable binding schema");
        }
        LeagueFixtureSeriesBindingV1 restored = new LeagueFixtureSeriesBindingV1(
                required(fields, "leagueId"), required(fields, "seasonId"),
                required(fields, "fixtureId"), longValue(fields, "expectedSeasonRevision"),
                required(fields, "reservationIdentity"), required(fields, "boundSeriesId"),
                required(fields, "firstTeamCode"), required(fields, "secondTeamCode"),
                required(fields, "managedTeamCode"),
                SeriesFormat.valueOf(required(fields, "seriesFormat")),
                required(fields, "game1BlueTeamCode"), required(fields, "game1RedTeamCode"),
                longValue(fields, "fixtureRootSeed"), required(fields, "seedAnchorTeamCode"),
                required(fields, "initialHistoryHash"), required(fields, "scheduleIdentity"),
                required(fields, "productDecisionHash"),
                required(fields, "frozenSnapshotIdentity"),
                required(fields, "firstTeamSnapshotIdentity"),
                required(fields, "secondTeamSnapshotIdentity"),
                required(fields, "playerResourceIdentity"),
                required(fields, "championDraftResourceIdentity"),
                required(fields, "matchupCompositionResourceIdentity"),
                required(fields, "productionRuntimeIdentity"),
                required(fields, "resourceProvenanceHash"), required(fields, "policyId"),
                required(fields, "policyHash"), required(fields, "runtimeProfileId"),
                required(fields, "configurationHash"),
                required(fields, "activeGameplayRulesVersion"),
                required(fields, "engineImplementationVersion"),
                longValue(fields, "bindingRevision"), required(fields, "bindingHash"));
        if (!restored.canonicalText().equals(canonicalText)) {
            throw new IllegalArgumentException("Durable binding canonical mismatch");
        }
        return restored;
    }

    private static String required(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null) throw new IllegalArgumentException("Missing binding field: " + name);
        return value;
    }

    private static long longValue(Map<String, String> fields, String name) {
        return Long.parseLong(required(fields, name));
    }

    public byte[] canonicalBytes() {
        return canonicalText().getBytes(StandardCharsets.UTF_8);
    }

    public String canonicalText() {
        return payloadText(leagueId, seasonId, fixtureId, expectedSeasonRevision,
                reservationIdentity, boundSeriesId, firstTeamCode, secondTeamCode,
                managedTeamCode, seriesFormat, game1BlueTeamCode, game1RedTeamCode,
                fixtureRootSeed, seedAnchorTeamCode, initialHistoryHash, scheduleIdentity,
                productDecisionHash, frozenSnapshotIdentity, firstTeamSnapshotIdentity,
                secondTeamSnapshotIdentity, playerResourceIdentity,
                championDraftResourceIdentity, matchupCompositionResourceIdentity,
                productionRuntimeIdentity, resourceProvenanceHash, policyId, policyHash,
                runtimeProfileId, configurationHash, activeGameplayRulesVersion,
                engineImplementationVersion, bindingRevision)
                + "bindingHash=" + bindingHash + '\n';
    }

    private static String payloadText(
            String leagueId, String seasonId, String fixtureId, long expectedSeasonRevision,
            String reservationIdentity, String boundSeriesId, String firstTeamCode,
            String secondTeamCode, String managedTeamCode, SeriesFormat seriesFormat,
            String game1BlueTeamCode, String game1RedTeamCode, long fixtureRootSeed,
            String seedAnchorTeamCode, String initialHistoryHash, String scheduleIdentity,
            String productDecisionHash, String frozenSnapshotIdentity,
            String firstTeamSnapshotIdentity, String secondTeamSnapshotIdentity,
            String playerResourceIdentity, String championDraftResourceIdentity,
            String matchupCompositionResourceIdentity, String productionRuntimeIdentity,
            String resourceProvenanceHash, String policyId, String policyHash,
            String runtimeProfileId, String configurationHash,
            String activeGameplayRulesVersion, String engineImplementationVersion,
            long bindingRevision
    ) {
        StringBuilder value = new StringBuilder();
        append(value, "schemaVersion", SCHEMA);
        append(value, "canonicalHashAlgorithm", HASH_ALGORITHM);
        append(value, "leagueId", leagueId);
        append(value, "seasonId", seasonId);
        append(value, "fixtureId", fixtureId);
        append(value, "expectedSeasonRevision", expectedSeasonRevision);
        append(value, "reservationIdentity", reservationIdentity);
        append(value, "executionMode", LeagueFixtureExecutionMode.PLAYER_CONTROLLED);
        append(value, "boundSeriesId", boundSeriesId);
        append(value, "firstTeamCode", firstTeamCode);
        append(value, "secondTeamCode", secondTeamCode);
        append(value, "managedTeamCode", managedTeamCode);
        append(value, "seriesFormat", seriesFormat);
        append(value, "game1BlueTeamCode", game1BlueTeamCode);
        append(value, "game1RedTeamCode", game1RedTeamCode);
        append(value, "fixtureRootSeed", fixtureRootSeed);
        append(value, "fixtureRootSeedAlgorithm", LeagueIdentity.FIXTURE_ROOT_SEED_ALGORITHM);
        append(value, "gameSeedAlgorithm", LeagueIdentity.GAME_SEED_ALGORITHM);
        append(value, "seedAnchorTeamCode", seedAnchorTeamCode);
        append(value, "initialCommittedGameCount", 0);
        append(value, "initialHistoryHash", initialHistoryHash);
        append(value, "scheduleIdentity", scheduleIdentity);
        append(value, "standingsPolicyId", LeagueStandings.STANDINGS_POLICY_ID);
        append(value, "productDecisionHash", productDecisionHash);
        append(value, "frozenSnapshotIdentity", frozenSnapshotIdentity);
        append(value, "firstTeamSnapshotIdentity", firstTeamSnapshotIdentity);
        append(value, "secondTeamSnapshotIdentity", secondTeamSnapshotIdentity);
        append(value, "playerResourceIdentity", playerResourceIdentity);
        append(value, "championDraftResourceIdentity", championDraftResourceIdentity);
        append(value, "matchupCompositionResourceIdentity", matchupCompositionResourceIdentity);
        append(value, "productionRuntimeIdentity", productionRuntimeIdentity);
        append(value, "resourceProvenanceHash", resourceProvenanceHash);
        append(value, "policyId", policyId);
        append(value, "policyHash", policyHash);
        append(value, "runtimeProfileId", runtimeProfileId);
        append(value, "configurationHash", configurationHash);
        append(value, "activeGameplayRulesVersion", activeGameplayRulesVersion);
        append(value, "engineImplementationVersion", engineImplementationVersion);
        append(value, "bindingRevision", bindingRevision);
        append(value, "bindingLifecycleStatus", "CREATED");
        return value.toString();
    }

    private static void append(StringBuilder target, String field, Object value) {
        target.append(field).append('=').append(value).append('\n');
    }

    public String leagueId() { return leagueId; }
    public String seasonId() { return seasonId; }
    public String fixtureId() { return fixtureId; }
    public long expectedSeasonRevision() { return expectedSeasonRevision; }
    public String reservationIdentity() { return reservationIdentity; }
    public LeagueFixtureExecutionMode executionMode() {
        return LeagueFixtureExecutionMode.PLAYER_CONTROLLED;
    }
    public String boundSeriesId() { return boundSeriesId; }
    public String firstTeamCode() { return firstTeamCode; }
    public String secondTeamCode() { return secondTeamCode; }
    public String managedTeamCode() { return managedTeamCode; }
    public SeriesFormat seriesFormat() { return seriesFormat; }
    public String game1BlueTeamCode() { return game1BlueTeamCode; }
    public String game1RedTeamCode() { return game1RedTeamCode; }
    public long fixtureRootSeed() { return fixtureRootSeed; }
    public String fixtureRootSeedAlgorithm() { return LeagueIdentity.FIXTURE_ROOT_SEED_ALGORITHM; }
    public String gameSeedAlgorithm() { return LeagueIdentity.GAME_SEED_ALGORITHM; }
    public String seedAnchorTeamCode() { return seedAnchorTeamCode; }
    public String initialHistoryHash() { return initialHistoryHash; }
    public String scheduleIdentity() { return scheduleIdentity; }
    public String productDecisionHash() { return productDecisionHash; }
    public String frozenSnapshotIdentity() { return frozenSnapshotIdentity; }
    public String firstTeamSnapshotIdentity() { return firstTeamSnapshotIdentity; }
    public String secondTeamSnapshotIdentity() { return secondTeamSnapshotIdentity; }
    public String playerResourceIdentity() { return playerResourceIdentity; }
    public String championDraftResourceIdentity() { return championDraftResourceIdentity; }
    public String matchupCompositionResourceIdentity() {
        return matchupCompositionResourceIdentity;
    }
    public String productionRuntimeIdentity() { return productionRuntimeIdentity; }
    public String resourceProvenanceHash() { return resourceProvenanceHash; }
    public String policyId() { return policyId; }
    public String policyHash() { return policyHash; }
    public String runtimeProfileId() { return runtimeProfileId; }
    public String configurationHash() { return configurationHash; }
    public String activeGameplayRulesVersion() { return activeGameplayRulesVersion; }
    public String engineImplementationVersion() { return engineImplementationVersion; }
    public long bindingRevision() { return bindingRevision; }
    public String bindingHash() { return bindingHash; }

    @Override
    public boolean equals(Object other) {
        return other instanceof LeagueFixtureSeriesBindingV1 value
                && bindingHash.equals(value.bindingHash)
                && canonicalText().equals(value.canonicalText());
    }

    @Override
    public int hashCode() {
        return bindingHash.hashCode();
    }
}
