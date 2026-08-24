package com.lolfm.application;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionResourceManifest;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Player;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.draft.BanScoreComponent;
import com.lolfm.draft.DraftResourceSet;
import com.lolfm.draft.DraftRuleSet;
import com.lolfm.draft.DraftScoringPolicy;
import com.lolfm.draft.FinalDraftResult;
import com.lolfm.draft.PickScoreComponent;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.ChampionProficiencyResourceLoader;
import com.lolfm.player.PlayerIdentityCatalog;
import com.lolfm.player.PlayerIdentityResourceLoader;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.player.PlayerRatingResourceLoader;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.ResolvedSimulationRuntimeProfile;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.SimulationRandomFingerprint;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import com.lolfm.simulator.TeamSide;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Pure observer that hashes explicit match inputs and the complete timeline. */
@Component
public final class SimulationProvenanceService {
    /** Compatibility alias retained for the V1 baseline document. */
    public static final String ENGINE_RULES_VERSION =
            SimulationRuntimeProfiles.PRE_JUNGLE_ACTIVE_GAMEPLAY_RULES_VERSION;
    public static final String ENGINE_IMPLEMENTATION_VERSION =
            "MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V8";
    public static final String ORDERED_LINES_HASH_ALGORITHM =
            "SHA256_UTF8_EXPLICIT_ORDERED_FIELD_LINES_TRAILING_NEWLINE_V1";
    public static final String MATCH_ENGINE_V1_REPLAY_PROVENANCE_HASH_ALGORITHM =
            "SHA256_UTF8_EXPLICIT_ORDERED_MATCH_ENGINE_V1_REPLAY_BINDING_LINES_"
                    + "TRAILING_NEWLINE_V1";
    public static final String TIMELINE_HASH_ALGORITHM =
            "SHA256_CANONICAL_JSON_SORTED_PROPERTIES_AND_MAP_KEYS_V1";

    private final ObjectMapper resourceMapper;
    private final ObjectMapper canonicalJsonMapper;
    private final SimulationResourceProvenance resourceProvenance;
    private final DraftRuleSet draftRules;
    private final DraftScoringPolicy draftPolicy;
    private final String draftRuleSetHash;
    private final String draftScoringPolicyHash;

    @Autowired
    public SimulationProvenanceService(
            ObjectMapper mapper,
            ChampionCatalog champions,
            PlayerIdentityCatalog identities,
            PlayerRatingCatalog ratings,
            ChampionProficiencyCatalog proficiencies
    ) {
        this(mapper, DraftResourceSet.loadDefault(mapper, champions), identities, ratings,
                proficiencies, DraftRuleSet.professional(), DraftScoringPolicy.standard());
    }

    public SimulationProvenanceService(
            ObjectMapper mapper,
            DraftResourceSet resources,
            PlayerIdentityCatalog identities,
            PlayerRatingCatalog ratings,
            ChampionProficiencyCatalog proficiencies,
            DraftRuleSet draftRules,
            DraftScoringPolicy draftPolicy
    ) {
        resourceMapper = Objects.requireNonNull(mapper, "mapper");
        canonicalJsonMapper = mapper.copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.INDENT_OUTPUT);
        this.draftRules = Objects.requireNonNull(draftRules, "draftRules");
        this.draftPolicy = Objects.requireNonNull(draftPolicy, "draftPolicy");
        resourceProvenance = captureResources(
                Objects.requireNonNull(resources, "resources"),
                Objects.requireNonNull(identities, "identities"),
                Objects.requireNonNull(ratings, "ratings"),
                Objects.requireNonNull(proficiencies, "proficiencies"));
        draftRuleSetHash = orderedLinesHash(canonicalDraftRules(draftRules));
        draftScoringPolicyHash = orderedLinesHash(canonicalDraftPolicy(draftPolicy));
    }

    public SimulationResourceProvenance resourceProvenance() {
        return resourceProvenance;
    }

    public String draftRuleSetIdentity() {
        return draftRules.identity();
    }

    public String draftRuleSetHash() {
        return draftRuleSetHash;
    }

    public String draftScoringPolicyHash() {
        return draftScoringPolicyHash;
    }

    public SimulationExecutionProvenance create(
            ResolvedSimulationRuntimeProfile profile,
            SimulationInstrumentation instrumentation,
            String blueTeamCode,
            Team blueTeam,
            String redTeamCode,
            Team redTeam,
            long matchSeed,
            int seriesGameNumber,
            Set<ChampionId> seriesExclusionsBeforeDraft,
            FinalDraftResult draftResult,
            MatchTimeline timeline,
            SimulationRandomFingerprint randomFingerprint
    ) {
        profile = SimulationRuntimeProfiles.requireRegistered(profile);
        Objects.requireNonNull(instrumentation, "instrumentation");
        Objects.requireNonNull(draftResult, "draftResult");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(randomFingerprint, "randomFingerprint");
        if (!draftRules.equals(draftResult.ruleSet())) {
            throw new IllegalArgumentException("Draft result rule set differs from runtime identity");
        }

        String rosterIdentityHash = rosterIdentityHash(
                blueTeamCode, blueTeam, redTeamCode, redTeam);
        String historyBeforeHash = seriesHistoryHash(
                seriesGameNumber - 1, seriesExclusionsBeforeDraft);
        String finalAssignmentHash = finalAssignmentHash(draftResult);
        String finalDraftHash = finalDraftHash(draftResult, finalAssignmentHash);
        String replayHash = replayProvenanceHash(
                ENGINE_IMPLEMENTATION_VERSION, profile.activeGameplayRulesVersion(),
                profile.configurationHash(), resourceProvenance.resourceProvenanceHash(),
                blueTeamCode, redTeamCode, rosterIdentityHash, matchSeed, seriesGameNumber,
                historyBeforeHash, draftResult.ruleSet().identity(), draftRuleSetHash,
                draftScoringPolicyHash, draftResult.draftIdentity(), finalDraftHash,
                finalAssignmentHash);

        return new SimulationExecutionProvenance(
                SimulationExecutionProvenance.SCHEMA,
                profile.profileId(), profile.gameplayConfiguration(), profile.configurationHash(),
                SimulationRuntimeProfiles.CONFIGURATION_HASH_ALGORITHM,
                instrumentation, profile.activeGameplayRulesVersion(),
                ENGINE_IMPLEMENTATION_VERSION, profile.activeGameplayRulesVersion(),
                resourceProvenance,
                blueTeamCode, redTeamCode, rosterIdentityHash, matchSeed, seriesGameNumber,
                historyBeforeHash, draftResult.ruleSet().identity(), draftRuleSetHash,
                draftScoringPolicyHash, draftResult.draftIdentity(), finalDraftHash,
                finalAssignmentHash, replayHash, ORDERED_LINES_HASH_ALGORITHM,
                timelineHash(timeline), TIMELINE_HASH_ALGORITHM, randomFingerprint);
    }

    /** Creates V1 provenance whose replay identity binds the complete immutable input snapshot. */
    public SimulationExecutionProvenance createV1(
            MatchEngineV1Input input,
            SimulationInstrumentation instrumentation,
            MatchTimeline timeline,
            SimulationRandomFingerprint randomFingerprint
    ) {
        Objects.requireNonNull(input, "input");
        MatchEngineV1Policy.requireAuthoritative(input.productionPolicy());
        Objects.requireNonNull(instrumentation, "instrumentation");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(randomFingerprint, "randomFingerprint");
        ResolvedSimulationRuntimeProfile profile =
                MatchEngineV1Policy.resolvedRuntimeProfile();
        MatchEngineV1Input.DraftInput draft = input.finalDraft();
        if (!resourceProvenance.resourceProvenanceHash().equals(
                MatchEngineV1Policy.APPROVED_RESOURCE_PROVENANCE_SHA256)
                || !draft.draftRuleSetIdentity().equals(draftRules.identity())
                || !draft.draftRuleSetHash().equals(draftRuleSetHash)
                || !draft.draftScoringPolicyHash().equals(draftScoringPolicyHash)) {
            throw new IllegalStateException("MATCH_ENGINE_V1_PROVENANCE_IDENTITY_DRIFT");
        }
        String legacyReplayHash = replayProvenanceHash(
                ENGINE_IMPLEMENTATION_VERSION, profile.activeGameplayRulesVersion(),
                profile.configurationHash(), resourceProvenance.resourceProvenanceHash(),
                input.blueTeam().teamIdentity(), input.redTeam().teamIdentity(),
                input.rosterIdentityHash(), input.matchSeed(), draft.seriesGameNumber(),
                input.seriesHistoryBeforeHash(), draft.draftRuleSetIdentity(),
                draft.draftRuleSetHash(), draft.draftScoringPolicyHash(),
                draft.draftDecisionHash(), draft.finalDraftHash(),
                draft.finalAssignmentHash());
        String replayHash = matchEngineV1ReplayProvenanceHash(
                legacyReplayHash, input.inputHash());
        return new SimulationExecutionProvenance(
                SimulationExecutionProvenance.SCHEMA,
                profile.profileId(), profile.gameplayConfiguration(), profile.configurationHash(),
                SimulationRuntimeProfiles.CONFIGURATION_HASH_ALGORITHM,
                instrumentation, profile.activeGameplayRulesVersion(),
                ENGINE_IMPLEMENTATION_VERSION, profile.activeGameplayRulesVersion(),
                resourceProvenance,
                input.blueTeam().teamIdentity(), input.redTeam().teamIdentity(),
                input.rosterIdentityHash(), input.matchSeed(), draft.seriesGameNumber(),
                input.seriesHistoryBeforeHash(), draft.draftRuleSetIdentity(),
                draft.draftRuleSetHash(), draft.draftScoringPolicyHash(),
                draft.draftDecisionHash(), draft.finalDraftHash(),
                draft.finalAssignmentHash(), replayHash,
                MATCH_ENGINE_V1_REPLAY_PROVENANCE_HASH_ALGORITHM,
                timelineHash(timeline), TIMELINE_HASH_ALGORITHM, randomFingerprint);
    }

    public String timelineHash(MatchTimeline timeline) {
        try {
            return sha256(canonicalJsonMapper.writeValueAsBytes(
                    Objects.requireNonNull(timeline, "timeline")));
        } catch (IOException error) {
            throw new IllegalStateException("Failed to canonicalize match timeline", error);
        }
    }

    private SimulationResourceProvenance captureResources(
            DraftResourceSet resources,
            PlayerIdentityCatalog identities,
            PlayerRatingCatalog ratings,
            ChampionProficiencyCatalog proficiencies
    ) {
        ChampionResourceManifest manifest = resources.champions().manifest();
        List<VersionedResourceIdentity> values = List.of(
                resource("CHAMPION_MANIFEST", ChampionResourceManifest.BOOTSTRAP_RESOURCE,
                        manifest.manifestVersion()),
                resource("CHAMPION_CATALOG", manifest.catalog(),
                        "pool=" + resources.champions().catalog().championPoolVersion()
                                + ";balance=" + resources.champions().catalog().championBalanceVersion()
                                + ";riot=" + resources.champions().catalog().riotDataVersion()),
                resource("CHAMPION_POWER", manifest.power(),
                        resources.champions().power().profileVersion()),
                resource("CHAMPION_MATCHUP", manifest.matchup(),
                        resources.champions().matchup().version()),
                resource("CHAMPION_COMPOSITION", manifest.composition(),
                        resources.champions().composition().version()),
                resource("CHAMPION_JUNGLE_CLEAR", manifest.jungleClear(),
                        jsonText(manifest.jungleClear(), "profileVersion")),
                verifiedResource("PLAYER_IDENTITY", PlayerIdentityResourceLoader.RESOURCE,
                        identities.version(), identities.resourceSha256()),
                verifiedResource("PLAYER_RATINGS", PlayerRatingResourceLoader.RESOURCE,
                        ratings.version(), ratings.resourceSha256()),
                verifiedResource("PLAYER_PROFICIENCY", ChampionProficiencyResourceLoader.RESOURCE,
                        proficiencies.version(), proficiencies.resourceSha256()),
                resource("DRAFT_META", "/" + com.lolfm.draft.DraftMetaCatalog.RESOURCE,
                        resources.meta().metaVersion()));

        int enabledJungleClearProfiles = (int) resources.champions().jungleClear().profiles()
                .values().stream().filter(value -> value.gameplayEnabled()).count();
        String canonical = canonicalResources(
                values, resources.champions().composition().profileHash(),
                resources.meta().actualLegalRoleKeyHash(), enabledJungleClearProfiles);
        return new SimulationResourceProvenance(
                SimulationResourceProvenance.SCHEMA, values,
                resources.champions().composition().profileHash(),
                resources.meta().actualLegalRoleKeyHash(), enabledJungleClearProfiles,
                orderedLinesHash(canonical));
    }

    private VersionedResourceIdentity verifiedResource(
            String role, String path, String version, String expectedSha256
    ) {
        VersionedResourceIdentity identity = resource(role, path, version);
        if (!identity.sha256().equals(expectedSha256)) {
            throw new IllegalStateException(role + " raw resource hash differs from catalog identity");
        }
        return identity;
    }

    private VersionedResourceIdentity resource(String role, String path, String version) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return new VersionedResourceIdentity(
                role, normalizedPath, version, sha256(readResource(normalizedPath)));
    }

    private String jsonText(String path, String field) {
        try {
            String value = resourceMapper.readTree(readResource(path)).path(field).asText();
            if (value.isBlank()) throw new IllegalStateException("Missing " + field + " in " + path);
            return value;
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read resource identity " + path, error);
        }
    }

    private static byte[] readResource(String path) {
        String normalized = path.startsWith("/") ? path : "/" + path;
        try (InputStream input = SimulationProvenanceService.class.getResourceAsStream(normalized)) {
            if (input == null) throw new IllegalStateException("Missing runtime resource " + normalized);
            return input.readAllBytes();
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read runtime resource " + normalized, error);
        }
    }

    private static String canonicalResources(
            List<VersionedResourceIdentity> resources,
            String compositionProfileHash,
            String draftLegalRoleKeyHash,
            int jungleClearGameplayEnabledProfileCount
    ) {
        StringBuilder canonical = new StringBuilder()
                .append("resourceProvenanceSchema=")
                .append(SimulationResourceProvenance.SCHEMA).append('\n');
        for (VersionedResourceIdentity resource : resources) {
            canonical.append("resource=").append(resource.role()).append('|')
                    .append(resource.classpathResource()).append('|')
                    .append(resource.version()).append('|')
                    .append(resource.sha256()).append('\n');
        }
        return canonical.append("compositionProfileHash=").append(compositionProfileHash).append('\n')
                .append("draftLegalRoleKeyHash=").append(draftLegalRoleKeyHash).append('\n')
                .append("jungleClearGameplayEnabledProfileCount=")
                .append(jungleClearGameplayEnabledProfileCount).append('\n').toString();
    }

    private static String canonicalDraftRules(DraftRuleSet rules) {
        StringBuilder canonical = new StringBuilder("draftRuleSetSchema=DRAFT_RULE_SET_V1\n")
                .append("identity=").append(rules.identity()).append('\n');
        rules.turns().forEach(turn -> canonical.append("turn=").append(turn.number()).append('|')
                .append(turn.side().name()).append('|').append(turn.actionType().name()).append('\n'));
        return canonical.toString();
    }

    private static String canonicalDraftPolicy(DraftScoringPolicy policy) {
        StringBuilder canonical = new StringBuilder("draftScoringPolicySchema=DRAFT_SCORING_POLICY_V1\n")
                .append("candidateLimit=").append(policy.candidateLimit()).append('\n')
                .append("structuralRepairSlots=").append(policy.structuralRepairSlots()).append('\n')
                .append("searchDepth=").append(policy.searchDepth()).append('\n')
                .append("beamWidth=").append(policy.beamWidth()).append('\n');
        for (PickScoreComponent component : PickScoreComponent.values()) {
            canonical.append("pickWeight=").append(component.name()).append('|')
                    .append(Double.toHexString(requiredWeight(policy.pickWeights(), component)))
                    .append('\n');
        }
        for (BanScoreComponent component : BanScoreComponent.values()) {
            canonical.append("banWeight=").append(component.name()).append('|')
                    .append(Double.toHexString(requiredWeight(policy.banWeights(), component)))
                    .append('\n');
        }
        return canonical.toString();
    }

    private static <E extends Enum<E>> double requiredWeight(Map<E, Double> weights, E key) {
        Double value = weights.get(key);
        if (value == null || !Double.isFinite(value)) {
            throw new IllegalStateException("Missing or invalid draft weight " + key);
        }
        return value;
    }

    static String rosterIdentityHash(
            String blueTeamCode, Team blueTeam, String redTeamCode, Team redTeam
    ) {
        StringBuilder canonical = new StringBuilder("rosterIdentitySchema=REAL_MATCH_ROSTER_V1\n");
        appendRoster(canonical, TeamSide.BLUE, blueTeamCode, blueTeam);
        appendRoster(canonical, TeamSide.RED, redTeamCode, redTeam);
        return orderedLinesHash(canonical.toString());
    }

    private static void appendRoster(
            StringBuilder canonical, TeamSide side, String teamCode, Team team
    ) {
        Objects.requireNonNull(team, "team");
        for (Position position : Position.values()) {
            Player player = team.getPlayers().stream()
                    .filter(value -> value.getPosition() == position)
                    .findFirst().orElseThrow(() -> new IllegalArgumentException(
                            "Missing roster position " + side + ":" + position));
            canonical.append("roster=").append(side.name()).append('|')
                    .append(teamCode).append('|').append(position.name()).append('|')
                    .append(player.requirePlayerId().value()).append('\n');
        }
    }

    static String seriesHistoryHash(int committedGames, Set<ChampionId> exclusions) {
        if (committedGames < 0) throw new IllegalArgumentException("committedGames must not be negative");
        StringBuilder canonical = new StringBuilder("seriesHistorySchema=HARD_FEARLESS_HISTORY_V1\n")
                .append("committedGameCount=").append(committedGames).append('\n');
        Objects.requireNonNull(exclusions, "exclusions").stream()
                .map(ChampionId::value).sorted()
                .forEach(value -> canonical.append("consumedPick=").append(value).append('\n'));
        return orderedLinesHash(canonical.toString());
    }

    static String finalAssignmentHash(FinalDraftResult result) {
        StringBuilder canonical = new StringBuilder(
                "finalAssignmentSchema=MATCH_CHAMPION_ASSIGNMENT_V1\n")
                .append("selectionMode=")
                .append(result.matchChampionAssignments().selectionMode().name()).append('\n');
        result.matchChampionAssignments().asMap().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(PlayerKey::stableId)))
                .map(Map.Entry::getValue)
                .forEach(assignment -> appendAssignment(canonical, assignment));
        return orderedLinesHash(canonical.toString());
    }

    private static void appendAssignment(StringBuilder canonical, ChampionAssignment assignment) {
        canonical.append("assignment=").append(assignment.playerKey().stableId()).append('|')
                .append(assignment.championId().value()).append('|')
                .append(assignment.selectedPosition().name()).append('\n');
    }

    static String finalDraftHash(FinalDraftResult result, String assignmentHash) {
        StringBuilder canonical = new StringBuilder("finalDraftSchema=FINAL_DRAFT_RESULT_V1\n")
                .append("ruleSetIdentity=").append(result.ruleSet().identity()).append('\n')
                .append("draftDecisionHash=").append(result.draftIdentity()).append('\n');
        appendChampionList(canonical, "blueBan", result.blueBans());
        appendChampionList(canonical, "redBan", result.redBans());
        appendChampionList(canonical, "bluePick", result.bluePicks());
        appendChampionList(canonical, "redPick", result.redPicks());
        appendRoles(canonical, TeamSide.BLUE, result.blueFinalRoleAssignments());
        appendRoles(canonical, TeamSide.RED, result.redFinalRoleAssignments());
        result.hardFearlessExclusions().stream().map(ChampionId::value).sorted()
                .forEach(value -> canonical.append("hardFearlessExclusion=")
                        .append(value).append('\n'));
        canonical.append("draftMetaVersion=").append(result.draftMetaVersion()).append('\n')
                .append("requiredLegalRoleKeyHash=")
                .append(result.requiredLegalRoleKeyHash()).append('\n')
                .append("actualLegalRoleKeyHash=")
                .append(result.actualLegalRoleKeyHash()).append('\n')
                .append("finalAssignmentHash=").append(assignmentHash).append('\n');
        return orderedLinesHash(canonical.toString());
    }

    private static void appendChampionList(
            StringBuilder canonical, String field, List<ChampionId> champions
    ) {
        for (int index = 0; index < champions.size(); index++) {
            canonical.append(field).append('=').append(index).append('|')
                    .append(champions.get(index).value()).append('\n');
        }
    }

    private static void appendRoles(
            StringBuilder canonical, TeamSide side, Map<ChampionId, Position> roles
    ) {
        roles.entrySet().stream().sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ChampionId::value)))
                .forEach(entry -> canonical.append("finalRole=").append(side.name()).append('|')
                        .append(entry.getKey().value()).append('|')
                        .append(entry.getValue().name()).append('\n'));
    }

    static String replayProvenanceHash(
            String engineImplementationVersion,
            String activeGameplayRulesVersion,
            String configurationHash,
            String resourceProvenanceHash,
            String blueTeamCode,
            String redTeamCode,
            String rosterIdentityHash,
            long seed,
            int seriesGameNumber,
            String seriesHistoryBeforeHash,
            String draftRuleSetIdentity,
            String draftRuleSetHash,
            String draftScoringPolicyHash,
            String draftDecisionHash,
            String finalDraftHash,
            String finalAssignmentHash
    ) {
        String canonical = "replayProvenanceSchema=REPLAY_PROVENANCE_V2\n"
                + "engineImplementationVersion=" + engineImplementationVersion + '\n'
                + "activeGameplayRulesVersion=" + activeGameplayRulesVersion + '\n'
                + "configurationHash=" + configurationHash + '\n'
                + "resourceProvenanceHash=" + resourceProvenanceHash + '\n'
                + "blueTeamCode=" + blueTeamCode + '\n'
                + "redTeamCode=" + redTeamCode + '\n'
                + "rosterIdentityHash=" + rosterIdentityHash + '\n'
                + "matchSeed=" + seed + '\n'
                + "seriesGameNumber=" + seriesGameNumber + '\n'
                + "seriesHistoryBeforeHash=" + seriesHistoryBeforeHash + '\n'
                + "draftRuleSetIdentity=" + draftRuleSetIdentity + '\n'
                + "draftRuleSetHash=" + draftRuleSetHash + '\n'
                + "draftScoringPolicyHash=" + draftScoringPolicyHash + '\n'
                + "draftDecisionHash=" + draftDecisionHash + '\n'
                + "finalDraftHash=" + finalDraftHash + '\n'
                + "finalAssignmentHash=" + finalAssignmentHash + '\n';
        return orderedLinesHash(canonical);
    }

    static String matchEngineV1ReplayProvenanceHash(
            String legacyReplayProvenanceHash,
            String matchEngineInputHash
    ) {
        String legacyHash = MatchEngineV1Policy.requiredHash(
                legacyReplayProvenanceHash, "legacyReplayProvenanceHash");
        String inputHash = MatchEngineV1Policy.requiredHash(
                matchEngineInputHash, "matchEngineInputHash");
        String canonical = "matchEngineV1ReplayProvenanceSchema="
                + "MATCH_ENGINE_V1_REPLAY_PROVENANCE_V1\n"
                + "legacyReplayProvenanceHashAlgorithm="
                + ORDERED_LINES_HASH_ALGORITHM + '\n'
                + "legacyReplayProvenanceHash=" + legacyHash + '\n'
                + "matchEngineInputHashAlgorithm="
                + MatchEngineV1Input.INPUT_HASH_ALGORITHM + '\n'
                + "matchEngineInputHash=" + inputHash + '\n';
        return orderedLinesHash(canonical);
    }

    private static String orderedLinesHash(String canonical) {
        if (!canonical.endsWith("\n")) {
            throw new IllegalArgumentException("Canonical ordered lines require trailing newline");
        }
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
