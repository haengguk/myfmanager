package com.lolfm.career;

import com.lolfm.application.SeriesFormat;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.league.LeagueSeasonFrozenSnapshot;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Server-authored canonical authority for one Career competition Series. */
public final class CareerCompetitionSeriesBindingV1 {
    public static final String SCHEMA = "CAREER_COMPETITION_SERIES_BINDING_V1";
    public static final String HASH_ALGORITHM =
            "SHA256_UTF8_EXPLICIT_ORDERED_CAREER_COMPETITION_BINDING_LINES_V1";

    private final String careerId;
    private final int seasonYear;
    private final String competitionId;
    private final String ruleResourceHash;
    private final String ruleVersion;
    private final String gamePolicyVersion;
    private final String cycleHashAlgorithm;
    private final String instanceStateHash;
    private final long instanceRevision;
    private final String fixtureId;
    private final String matchId;
    private final int matchOrder;
    private final String stageId;
    private final CareerCompetitionRules.ParticipantSelector firstSelector;
    private final CareerCompetitionRules.ParticipantSelector secondSelector;
    private final String firstTeamCode;
    private final String secondTeamCode;
    private final String managedTeamCode;
    private final SeriesFormat seriesFormat;
    private final boolean hardFearless;
    private final String executionMode;
    private final String sideSelectionPolicy;
    private final String game1BlueTeamCode;
    private final String game1RedTeamCode;
    private final long fixtureRootSeed;
    private final String seedAlgorithm;
    private final String boundSeriesId;
    private final String initialHistoryHash;
    private final Set<com.lolfm.champion.ChampionId> initialHistoryPicks;
    private final String initializationPolicyId;
    private final String initializationInputHash;
    private final String materializationPolicyId;
    private final String materializationReceiptHash;
    private final String productionSnapshotIdentity;
    private final String firstTeamSnapshotIdentity;
    private final String secondTeamSnapshotIdentity;
    private final String playerResourceIdentity;
    private final String championDraftResourceIdentity;
    private final String matchupCompositionResourceIdentity;
    private final String productionRuntimeIdentity;
    private final String resourceProvenanceHash;
    private final CompetitionRosterSnapshot frozenRosters;
    private final String bindingHash;

    private CareerCompetitionSeriesBindingV1(
            String careerId, int seasonYear, String competitionId,
            String ruleResourceHash, String ruleVersion, String gamePolicyVersion,
            String cycleHashAlgorithm, String instanceStateHash, long instanceRevision,
            String fixtureId, String matchId, int matchOrder, String stageId,
            CareerCompetitionRules.ParticipantSelector firstSelector,
            CareerCompetitionRules.ParticipantSelector secondSelector,
            String firstTeamCode, String secondTeamCode, String managedTeamCode,
            SeriesFormat seriesFormat, boolean hardFearless, String executionMode,
            String sideSelectionPolicy, String game1BlueTeamCode,
            String game1RedTeamCode, long fixtureRootSeed, String seedAlgorithm,
            String boundSeriesId, String initialHistoryHash, Set<com.lolfm.champion.ChampionId> initialHistoryPicks,
            String initializationPolicyId, String initializationInputHash,
            String materializationPolicyId, String materializationReceiptHash,
            String productionSnapshotIdentity,
            String firstTeamSnapshotIdentity,
            String secondTeamSnapshotIdentity,
            String playerResourceIdentity,
            String championDraftResourceIdentity,
            String matchupCompositionResourceIdentity,
            String productionRuntimeIdentity,
            String resourceProvenanceHash,
            String expectedBindingHash, CompetitionRosterSnapshot frozenRosters
    ) {
        CareerIdentity.requireCareerId(careerId);
        if (seasonYear < 2026 || instanceRevision < 0 || matchOrder < 1) {
            throw new IllegalArgumentException("Competition binding revision/order");
        }
        required(competitionId, "competitionId");
        CareerIdentity.requireSha256(ruleResourceHash, "ruleResourceHash");
        required(ruleVersion, "ruleVersion");
        required(gamePolicyVersion, "gamePolicyVersion");
        required(cycleHashAlgorithm, "cycleHashAlgorithm");
        CareerIdentity.requireSha256(instanceStateHash, "instanceStateHash");
        required(fixtureId, "fixtureId");
        required(matchId, "matchId");
        required(stageId, "stageId");
        Objects.requireNonNull(firstSelector, "firstSelector");
        Objects.requireNonNull(secondSelector, "secondSelector");
        team(firstTeamCode);
        team(secondTeamCode);
        team(managedTeamCode);
        if (firstTeamCode.equals(secondTeamCode)
                || "PLAYER_CONTROLLED".equals(executionMode)
                && !Set.of(firstTeamCode, secondTeamCode).contains(managedTeamCode)) {
            throw new IllegalArgumentException("Competition binding team scope");
        }
        Objects.requireNonNull(seriesFormat, "seriesFormat");
        if (!hardFearless) throw new IllegalArgumentException(
                "Competition Series requires Hard Fearless");
        if (!Set.of("FULL_AUTO", "PLAYER_CONTROLLED").contains(executionMode)) {
            throw new IllegalArgumentException("Competition execution mode");
        }
        required(sideSelectionPolicy, "sideSelectionPolicy");
        team(game1BlueTeamCode);
        team(game1RedTeamCode);
        if (!Set.of(firstTeamCode, secondTeamCode).equals(
                Set.of(game1BlueTeamCode, game1RedTeamCode))) {
            throw new IllegalArgumentException("Competition binding side scope");
        }
        required(seedAlgorithm, "seedAlgorithm");
        if (!boundSeriesId.matches("series_[0-9a-f]{64}")) {
            throw new IllegalArgumentException("boundSeriesId");
        }
        CareerIdentity.requireSha256(initialHistoryHash, "initialHistoryHash");
        if (!initialHistoryHash.equals(SeriesDraftHistory.identityHash(0, initialHistoryPicks))) {
            throw new IllegalArgumentException("initialHistoryHash");
        }
        required(initializationPolicyId, "initializationPolicyId");
        CareerIdentity.requireSha256(initializationInputHash, "initializationInputHash");
        required(materializationPolicyId, "materializationPolicyId");
        CareerIdentity.requireSha256(materializationReceiptHash,
                "materializationReceiptHash");
        CareerIdentity.requireSha256(productionSnapshotIdentity,
                "productionSnapshotIdentity");
        CareerIdentity.requireSha256(firstTeamSnapshotIdentity,
                "firstTeamSnapshotIdentity");
        CareerIdentity.requireSha256(secondTeamSnapshotIdentity,
                "secondTeamSnapshotIdentity");
        CareerIdentity.requireSha256(playerResourceIdentity,
                "playerResourceIdentity");
        CareerIdentity.requireSha256(championDraftResourceIdentity,
                "championDraftResourceIdentity");
        CareerIdentity.requireSha256(matchupCompositionResourceIdentity,
                "matchupCompositionResourceIdentity");
        CareerIdentity.requireSha256(productionRuntimeIdentity,
                "productionRuntimeIdentity");
        CareerIdentity.requireSha256(resourceProvenanceHash,
                "resourceProvenanceHash");
        this.careerId = careerId;
        this.seasonYear = seasonYear;
        this.competitionId = competitionId;
        this.ruleResourceHash = ruleResourceHash;
        this.ruleVersion = ruleVersion;
        this.gamePolicyVersion = gamePolicyVersion;
        this.cycleHashAlgorithm = cycleHashAlgorithm;
        this.instanceStateHash = instanceStateHash;
        this.instanceRevision = instanceRevision;
        this.fixtureId = fixtureId;
        this.matchId = matchId;
        this.matchOrder = matchOrder;
        this.stageId = stageId;
        this.firstSelector = firstSelector;
        this.secondSelector = secondSelector;
        this.firstTeamCode = firstTeamCode;
        this.secondTeamCode = secondTeamCode;
        this.managedTeamCode = managedTeamCode;
        this.seriesFormat = seriesFormat;
        this.hardFearless = hardFearless;
        this.executionMode = executionMode;
        this.sideSelectionPolicy = sideSelectionPolicy;
        this.game1BlueTeamCode = game1BlueTeamCode;
        this.game1RedTeamCode = game1RedTeamCode;
        this.fixtureRootSeed = fixtureRootSeed;
        this.seedAlgorithm = seedAlgorithm;
        this.boundSeriesId = boundSeriesId;
        this.initialHistoryHash = initialHistoryHash;
        this.initialHistoryPicks = Set.copyOf(initialHistoryPicks);
        this.initializationPolicyId = initializationPolicyId;
        this.initializationInputHash = initializationInputHash;
        this.materializationPolicyId = materializationPolicyId;
        this.materializationReceiptHash = materializationReceiptHash;
        this.productionSnapshotIdentity = productionSnapshotIdentity;
        this.firstTeamSnapshotIdentity = firstTeamSnapshotIdentity;
        this.secondTeamSnapshotIdentity = secondTeamSnapshotIdentity;
        this.playerResourceIdentity = playerResourceIdentity;
        this.championDraftResourceIdentity = championDraftResourceIdentity;
        this.matchupCompositionResourceIdentity = matchupCompositionResourceIdentity;
        this.productionRuntimeIdentity = productionRuntimeIdentity;
        this.resourceProvenanceHash = resourceProvenanceHash;
        this.frozenRosters = frozenRosters;
        if (frozenRosters != null && (!frozenRosters.teams().keySet().equals(Set.of(firstTeamCode, secondTeamCode))
                || !frozenRosters.identity().equals(productionSnapshotIdentity)
                || !frozenRosters.roster(firstTeamCode).identity().equals(firstTeamSnapshotIdentity)
                || !frozenRosters.roster(secondTeamCode).identity().equals(secondTeamSnapshotIdentity))) {
            throw new IllegalArgumentException("COMPETITION_FROZEN_ROSTER_BINDING_MISMATCH");
        }
        String actual = CareerCompetitionRules.sha256(payloadText().getBytes(
                StandardCharsets.UTF_8));
        if (expectedBindingHash != null && !actual.equals(expectedBindingHash)) {
            throw new IllegalArgumentException("Competition binding hash mismatch");
        }
        this.bindingHash = actual;
    }

    static CareerCompetitionSeriesBindingV1 create(CareerCompetitionRelationalStore.CycleView cycle,
            CareerCompetitionRelationalStore.InstanceRow instance, CareerCompetitionRelationalStore.FixtureRow fixture,
            String managedTeamCode, String ruleResourceHash, LeagueSeasonFrozenSnapshot snapshot, String provenance) {
        return create(cycle, instance, fixture, managedTeamCode, ruleResourceHash, snapshot, provenance, Set.of());
    }

    static CareerCompetitionSeriesBindingV1 create(
            CareerCompetitionRelationalStore.CycleView cycle,
            CareerCompetitionRelationalStore.InstanceRow instance,
            CareerCompetitionRelationalStore.FixtureRow fixture,
            String managedTeamCode,
            String ruleResourceHash,
            LeagueSeasonFrozenSnapshot productionSnapshot,
            String resourceProvenanceHash, Set<com.lolfm.champion.ChampionId> initialPicks
    ) {
        if (!"READY".equals(fixture.lifecycleStatus())
                || fixture.firstTeamCode() == null || fixture.secondTeamCode() == null) {
            throw new IllegalStateException("COMPETITION_FIXTURE_NOT_READY");
        }
        String blue = firstGameBlue(fixture);
        String red = blue.equals(fixture.firstTeamCode())
                ? fixture.secondTeamCode() : fixture.firstTeamCode();
        return new CareerCompetitionSeriesBindingV1(cycle.careerId(), cycle.seasonYear(),
                fixture.competitionId(), ruleResourceHash, cycle.ruleVersion(),
                cycle.gamePolicyVersion(), cycle.hashAlgorithm(),
                instance.stateHash(), instance.revision(), fixture.fixtureId(),
                fixture.matchId(), fixture.matchOrder(), fixture.stageId(),
                new CareerCompetitionRules.ParticipantSelector(
                        fixture.firstSelectorType(), fixture.firstSelectorValue()),
                new CareerCompetitionRules.ParticipantSelector(
                        fixture.secondSelectorType(), fixture.secondSelectorValue()),
                fixture.firstTeamCode(), fixture.secondTeamCode(), managedTeamCode,
                SeriesFormat.valueOf(fixture.seriesFormat()), fixture.hardFearless(),
                fixture.executionMode(), fixture.sideSelectionPolicy(), blue, red,
                fixture.rootSeed(), CareerCompetitionAggregate.SEED_ALGORITHM,
                fixture.seriesId(), SeriesDraftHistory.identityHash(0, initialPicks), initialPicks,
                cycle.initializationPolicyId(), cycle.initializationInputHash(),
                instance.materializationPolicyId(), instance.materializationReceiptHash(),
                productionSnapshot.snapshotIdentity(),
                productionSnapshot.teamSnapshotIdentity(fixture.firstTeamCode()),
                productionSnapshot.teamSnapshotIdentity(fixture.secondTeamCode()),
                productionSnapshot.playerResourceIdentity(),
                productionSnapshot.championDraftResourceIdentity(),
                productionSnapshot.matchupCompositionResourceIdentity(),
                productionSnapshot.productionRuntimeIdentity(), resourceProvenanceHash,
                null, null);
    }

    static CareerCompetitionSeriesBindingV1 createInternational(
            CareerCompetitionRelationalStore.CycleView cycle,
            CareerCompetitionRelationalStore.InstanceRow instance,
            CareerCompetitionRelationalStore.FixtureRow fixture, String managedTeamCode,
            String ruleResourceHash, LeagueSeasonFrozenSnapshot productionSnapshot,
            String resourceProvenanceHash, CompetitionRosterSnapshot rosters) {
        if (!"READY".equals(fixture.lifecycleStatus())) throw new IllegalStateException("COMPETITION_FIXTURE_NOT_READY");
        Set<com.lolfm.champion.ChampionId> initialPicks = Set.of();
        String blue = firstGameBlue(fixture);
        String red = blue.equals(fixture.firstTeamCode()) ? fixture.secondTeamCode() : fixture.firstTeamCode();
        return new CareerCompetitionSeriesBindingV1(cycle.careerId(), cycle.seasonYear(),
                fixture.competitionId(), ruleResourceHash, CareerInternationalRules.VERSION,
                CareerInternationalRules.POLICY, cycle.hashAlgorithm(),
                instance.stateHash(), instance.revision(), fixture.fixtureId(),
                fixture.matchId(), fixture.matchOrder(), fixture.stageId(),
                new CareerCompetitionRules.ParticipantSelector(
                        fixture.firstSelectorType(), fixture.firstSelectorValue()),
                new CareerCompetitionRules.ParticipantSelector(
                        fixture.secondSelectorType(), fixture.secondSelectorValue()),
                fixture.firstTeamCode(), fixture.secondTeamCode(), managedTeamCode,
                SeriesFormat.valueOf(fixture.seriesFormat()), fixture.hardFearless(),
                fixture.executionMode(), fixture.sideSelectionPolicy(), blue, red,
                fixture.rootSeed(), CareerCompetitionAggregate.SEED_ALGORITHM,
                fixture.seriesId(), SeriesDraftHistory.identityHash(0, initialPicks), initialPicks,
                cycle.initializationPolicyId(), cycle.initializationInputHash(),
                instance.materializationPolicyId(), instance.materializationReceiptHash(),
                rosters.identity(),
                rosters.roster(fixture.firstTeamCode()).identity(),
                rosters.roster(fixture.secondTeamCode()).identity(),
                rosters.identity(),
                productionSnapshot.championDraftResourceIdentity(),
                productionSnapshot.matchupCompositionResourceIdentity(),
                productionSnapshot.productionRuntimeIdentity(), resourceProvenanceHash,
                null, rosters);
    }

    public static CareerCompetitionSeriesBindingV1 restoreCanonical(String canonical) {
        Objects.requireNonNull(canonical, "canonical");
        if (!canonical.endsWith("\n")) throw new IllegalArgumentException(
                "Competition binding canonical terminator");
        HashMap<String, String> fields = new HashMap<>();
        for (String line : canonical.split("\n")) {
            int separator = line.indexOf('=');
            if (separator < 1 || fields.put(line.substring(0, separator),
                    line.substring(separator + 1)) != null) {
                throw new IllegalArgumentException("Competition binding canonical field");
            }
        }
        if (!SCHEMA.equals(fields.get("schemaVersion"))
                || !HASH_ALGORITHM.equals(fields.get("canonicalHashAlgorithm"))) {
            throw new IllegalArgumentException("Competition binding canonical schema");
        }
        CareerCompetitionSeriesBindingV1 restored =
                new CareerCompetitionSeriesBindingV1(required(fields, "careerId"),
                        Integer.parseInt(required(fields, "calendarSeasonYear")),
                        required(fields, "competitionId"),
                        required(fields, "ruleResourceHash"),
                        required(fields, "ruleVersion"),
                        required(fields, "gamePolicyVersion"),
                        required(fields, "cycleHashAlgorithm"),
                        required(fields, "instanceStateHash"),
                        Long.parseLong(required(fields, "instanceRevision")),
                        required(fields, "fixtureId"), required(fields, "matchId"),
                        Integer.parseInt(required(fields, "matchOrder")),
                        required(fields, "stageId"), selector(fields, "firstSelector"),
                        selector(fields, "secondSelector"),
                        required(fields, "firstTeamCode"),
                        required(fields, "secondTeamCode"),
                        required(fields, "managedTeamCode"),
                        SeriesFormat.valueOf(required(fields, "seriesFormat")),
                        Boolean.parseBoolean(required(fields, "hardFearless")),
                        required(fields, "executionMode"),
                        required(fields, "sideSelectionPolicy"),
                        required(fields, "game1BlueTeamCode"),
                        required(fields, "game1RedTeamCode"),
                        Long.parseLong(required(fields, "fixtureRootSeed")),
                        required(fields, "seedAlgorithm"),
                        required(fields, "boundSeriesId"),
                        required(fields, "initialHistoryHash"),
                        fields.containsKey("initialHistoryPicks") ? java.util.Arrays.stream(fields.get("initialHistoryPicks").split(",")).map(com.lolfm.champion.ChampionId::new).collect(java.util.stream.Collectors.toUnmodifiableSet()) : Set.of(),
                        required(fields, "initializationPolicyId"),
                        required(fields, "initializationInputHash"),
                        required(fields, "materializationPolicyId"),
                        required(fields, "materializationReceiptHash"),
                        required(fields, "productionSnapshotIdentity"),
                        required(fields, "firstTeamSnapshotIdentity"),
                        required(fields, "secondTeamSnapshotIdentity"),
                        required(fields, "playerResourceIdentity"),
                        required(fields, "championDraftResourceIdentity"),
                        required(fields, "matchupCompositionResourceIdentity"),
                        required(fields, "productionRuntimeIdentity"),
                        required(fields, "resourceProvenanceHash"),
                        required(fields, "bindingHash"), fields.containsKey("frozenRosters")
                                ? CompetitionRosterSnapshot.decode(fields.get("frozenRosters")) : null);
        if (!canonical.equals(restored.canonicalText())) {
            throw new IllegalArgumentException("Competition binding canonical mismatch");
        }
        return restored;
    }

    private static CareerCompetitionRules.ParticipantSelector selector(
            Map<String, String> fields, String name
    ) {
        String value = required(fields, name);
        int separator = value.indexOf(':');
        if (separator < 1) throw new IllegalArgumentException(name);
        return new CareerCompetitionRules.ParticipantSelector(
                value.substring(0, separator), value.substring(separator + 1));
    }

    private static String firstGameBlue(
            CareerCompetitionRelationalStore.FixtureRow fixture
    ) {
        if (loserRoFs(fixture.sideSelectionPolicy())) {
            String owner = fixture.selectionRightOwner();
            if (!Set.of(fixture.firstTeamCode(), fixture.secondTeamCode()).contains(owner)) throw new IllegalStateException("COMPETITION_ROFS_OWNER_REQUIRED");
            return owner;
        }
        if (fixture.sideSelectionPolicy().contains("COIN_TOSS")) {
            String choice = CareerCompetitionRules.sha256((
                    "policy=" + fixture.sideSelectionPolicy() + '\n'
                            + "fixtureId=" + fixture.fixtureId() + '\n'
                            + "rootSeed=" + fixture.rootSeed() + '\n').getBytes(
                    StandardCharsets.UTF_8));
            return Character.digit(choice.charAt(0), 16) % 2 == 0
                    ? fixture.firstTeamCode() : fixture.secondTeamCode();
        }
        return fixture.firstTeamCode();
    }

    private String payloadText() {
        return "schemaVersion=" + SCHEMA + '\n'
                + "canonicalHashAlgorithm=" + HASH_ALGORITHM + '\n'
                + "careerId=" + careerId + '\n'
                + "calendarSeasonYear=" + seasonYear + '\n'
                + "competitionId=" + competitionId + '\n'
                + "ruleResourceHash=" + ruleResourceHash + '\n'
                + "ruleVersion=" + ruleVersion + '\n'
                + "gamePolicyVersion=" + gamePolicyVersion + '\n'
                + "cycleHashAlgorithm=" + cycleHashAlgorithm + '\n'
                + "instanceStateHash=" + instanceStateHash + '\n'
                + "instanceRevision=" + instanceRevision + '\n'
                + "fixtureId=" + fixtureId + '\n'
                + "matchId=" + matchId + '\n'
                + "matchOrder=" + matchOrder + '\n'
                + "stageId=" + stageId + '\n'
                + "firstSelector=" + firstSelector.type() + ':' + firstSelector.value() + '\n'
                + "secondSelector=" + secondSelector.type() + ':' + secondSelector.value() + '\n'
                + "firstTeamCode=" + firstTeamCode + '\n'
                + "secondTeamCode=" + secondTeamCode + '\n'
                + "managedTeamCode=" + managedTeamCode + '\n'
                + "seriesFormat=" + seriesFormat + '\n'
                + "hardFearless=" + hardFearless + '\n'
                + "executionMode=" + executionMode + '\n'
                + "sideSelectionPolicy=" + sideSelectionPolicy + '\n'
                + "game1BlueTeamCode=" + game1BlueTeamCode + '\n'
                + "game1RedTeamCode=" + game1RedTeamCode + '\n'
                + "fixtureRootSeed=" + fixtureRootSeed + '\n'
                + "seedAlgorithm=" + seedAlgorithm + '\n'
                + "boundSeriesId=" + boundSeriesId + '\n'
                + "initialHistoryHash=" + initialHistoryHash + '\n'
                + (initialHistoryPicks.isEmpty() ? "" : "initialHistoryPicks=" + initialHistoryPicks.stream().map(com.lolfm.champion.ChampionId::value).sorted().collect(java.util.stream.Collectors.joining(",")) + '\n')
                + "initializationPolicyId=" + initializationPolicyId + '\n'
                + "initializationInputHash=" + initializationInputHash + '\n'
                + "materializationPolicyId=" + materializationPolicyId + '\n'
                + "materializationReceiptHash=" + materializationReceiptHash + '\n'
                + "productionSnapshotIdentity=" + productionSnapshotIdentity + '\n'
                + "firstTeamSnapshotIdentity=" + firstTeamSnapshotIdentity + '\n'
                + "secondTeamSnapshotIdentity=" + secondTeamSnapshotIdentity + '\n'
                + "playerResourceIdentity=" + playerResourceIdentity + '\n'
                + "championDraftResourceIdentity=" + championDraftResourceIdentity + '\n'
                + "matchupCompositionResourceIdentity="
                + matchupCompositionResourceIdentity + '\n'
                + "productionRuntimeIdentity=" + productionRuntimeIdentity + '\n'
                + "resourceProvenanceHash=" + resourceProvenanceHash + '\n'
                + (frozenRosters == null ? "" : "frozenRosters=" + frozenRosters.encoded() + '\n');
    }

    public String canonicalText() { return payloadText() + "bindingHash=" + bindingHash + '\n'; }
    public CompetitionRosterSnapshot frozenRosters() { return frozenRosters; }
    public String bindingHash() { return bindingHash; }
    public String careerId() { return careerId; }
    public int seasonYear() { return seasonYear; }
    public String competitionId() { return competitionId; }
    public String fixtureId() { return fixtureId; }
    public String matchId() { return matchId; }
    public String boundSeriesId() { return boundSeriesId; }
    public String firstTeamCode() { return firstTeamCode; }
    public String secondTeamCode() { return secondTeamCode; }
    public String managedTeamCode() { return managedTeamCode; }
    public SeriesFormat seriesFormat() { return seriesFormat; }
    public String executionMode() { return executionMode; }
    public String game1BlueTeamCode() { return game1BlueTeamCode; }
    public String game1RedTeamCode() { return game1RedTeamCode; }
    public String seedAnchorTeamCode() {
        return firstTeamCode.compareTo(secondTeamCode) < 0
                ? firstTeamCode : secondTeamCode;
    }
    public long fixtureRootSeed() { return fixtureRootSeed; }
    public String initialHistoryHash() { return initialHistoryHash; }
    public Set<com.lolfm.champion.ChampionId> initialHistoryPicks() { return initialHistoryPicks; }
    public String sideSelectionPolicy() { return sideSelectionPolicy; }
    public boolean loserChoosesNextSide() { return loserRoFs(sideSelectionPolicy); }
    public static boolean loserRoFs(String policy) {
        return "INTERNATIONAL_ROFS_FIRST_PICK_OTHER_RED_LOSER_ROFS_V1".equals(policy)
                || "INTERNATIONAL_RODS_BLUE_FIRST_PICK_LOSER_ROFS_V1".equals(policy)
                || "LCK_ROFS_FIRST_PICK_OTHER_TEAM_RED_LOSER_ROFS_V1".equals(policy)
                || "LCK_FINAL_UPPER_WINNER_BLUE_FIRST_PICK_LOSER_ROFS_V1".equals(policy);
    }
    public String ruleResourceHash() { return ruleResourceHash; }
    public String ruleVersion() { return ruleVersion; }
    public String gamePolicyVersion() { return gamePolicyVersion; }
    public String seedAlgorithm() { return seedAlgorithm; }
    public String productionSnapshotIdentity() { return productionSnapshotIdentity; }
    public String firstTeamSnapshotIdentity() { return firstTeamSnapshotIdentity; }
    public String secondTeamSnapshotIdentity() { return secondTeamSnapshotIdentity; }
    public String playerResourceIdentity() { return playerResourceIdentity; }
    public String championDraftResourceIdentity() { return championDraftResourceIdentity; }
    public String matchupCompositionResourceIdentity() { return matchupCompositionResourceIdentity; }
    public String productionRuntimeIdentity() { return productionRuntimeIdentity; }
    public String resourceProvenanceHash() { return resourceProvenanceHash; }

    public void requireProductionAuthority(
            LeagueSeasonFrozenSnapshot snapshot,
            String currentResourceProvenanceHash
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (frozenRosters != null) {
            if (!championDraftResourceIdentity.equals(snapshot.championDraftResourceIdentity())
                    || !matchupCompositionResourceIdentity.equals(snapshot.matchupCompositionResourceIdentity())
                    || !productionRuntimeIdentity.equals(snapshot.productionRuntimeIdentity())
                    || !resourceProvenanceHash.equals(currentResourceProvenanceHash))
                throw new IllegalStateException("COMPETITION_FROZEN_ENGINE_IDENTITY_MISMATCH");
            return;
        }
        boolean valid = productionSnapshotIdentity.equals(snapshot.snapshotIdentity())
                && firstTeamSnapshotIdentity.equals(
                snapshot.teamSnapshotIdentity(firstTeamCode))
                && secondTeamSnapshotIdentity.equals(
                snapshot.teamSnapshotIdentity(secondTeamCode))
                && playerResourceIdentity.equals(snapshot.playerResourceIdentity())
                && championDraftResourceIdentity.equals(
                snapshot.championDraftResourceIdentity())
                && matchupCompositionResourceIdentity.equals(
                snapshot.matchupCompositionResourceIdentity())
                && productionRuntimeIdentity.equals(snapshot.productionRuntimeIdentity())
                && resourceProvenanceHash.equals(currentResourceProvenanceHash);
        if (!valid) throw new IllegalStateException(
                "COMPETITION_FROZEN_PRODUCTION_IDENTITY_MISMATCH");
    }

    private static void team(String value) {
        if (value == null || !value.matches("(?:[A-Z0-9]{2,8}|(?:LCK|LPL|LEC|LCS|LCP|CBLOL):[A-Z0-9]{1,8})")) {
            throw new IllegalArgumentException("teamCode");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name);
        return value;
    }

    private static String required(Map<String, String> fields, String name) {
        return required(fields.get(name), name);
    }
}
