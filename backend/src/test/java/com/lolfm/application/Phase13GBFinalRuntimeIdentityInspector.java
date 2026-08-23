package com.lolfm.application;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.controller.MatchController;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.dto.MatchSimulateRequest;
import com.lolfm.factory.DummyDataFactory;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import com.lolfm.simulator.JungleClearContribution;
import com.lolfm.simulator.MatchSimulator;
import com.lolfm.simulator.ObservedMatchSimulation;
import com.lolfm.simulator.ResolvedSimulationRuntimeProfile;
import com.lolfm.simulator.SimulationGameplayConfiguration;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.SimulationOptions;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Executes bounded, fixed-seed checks against the production registry and Spring-wired objects. */
final class Phase13GBFinalRuntimeIdentityInspector {
    private static final long REAL_DRAFT_SEED = 2026082301L;
    private static final long LEGACY_SIMULATOR_SEED = 2026082302L;
    private static final long HTTP_SEED = 2026082303L;

    private final ObjectMapper canonicalMapper;
    private final RealDraftMatchOrchestrator orchestrator;
    private final MatchSimulator autowiredSimulator;
    private final ConfiguredMatchSimulatorFactory configuredFactory;
    private final ChampionCatalog champions;
    private final DummyDataFactory dummyDataFactory;
    private final MatchController controller;

    Phase13GBFinalRuntimeIdentityInspector(
            ObjectMapper mapper,
            RealDraftMatchOrchestrator orchestrator,
            MatchSimulator autowiredSimulator,
            ConfiguredMatchSimulatorFactory configuredFactory,
            ChampionCatalog champions,
            DummyDataFactory dummyDataFactory,
            MatchController controller
    ) {
        canonicalMapper = Objects.requireNonNull(mapper, "mapper").copy()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.INDENT_OUTPUT);
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.autowiredSimulator = Objects.requireNonNull(autowiredSimulator,
                "autowiredSimulator");
        this.configuredFactory = Objects.requireNonNull(configuredFactory, "configuredFactory");
        this.champions = Objects.requireNonNull(champions, "champions");
        this.dummyDataFactory = Objects.requireNonNull(dummyDataFactory, "dummyDataFactory");
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    Phase13GBFinalRuntimeIdentityEvidence.Evidence inspect(Path backendRoot) throws Exception {
        ResolvedSimulationRuntimeProfile baseline = SimulationRuntimeProfiles.resolve(
                SimulationRuntimeProfileId.BASELINE_V1);
        RealDraftMatchResult defaultRun = orchestrator.orchestrate(
                "GEN", "T1", REAL_DRAFT_SEED);
        RealDraftMatchResult explicitRun = orchestrator.orchestrate(
                "GEN", "T1", REAL_DRAFT_SEED, SimulationRuntimeProfileId.BASELINE_V1);
        SimulationExecutionProvenance provenance = defaultRun.executionProvenance();
        require(defaultRun.executionProvenance().equals(explicitRun.executionProvenance()),
                "RealDraft default/explicit execution provenance mismatch");
        require(timelineHash(defaultRun.timeline()).equals(timelineHash(explicitRun.timeline())),
                "RealDraft default/explicit complete timeline mismatch");
        require(provenance.runtimeProfileId() == SimulationRuntimeProfileId.BASELINE_V1,
                "RealDraft default did not resolve BASELINE_V1");
        require(provenance.resolvedGameplayConfiguration().equals(
                        baseline.gameplayConfiguration())
                        && provenance.configurationHash().equals(baseline.configurationHash())
                        && provenance.activeGameplayRulesVersion().equals(
                        baseline.activeGameplayRulesVersion()),
                "RealDraft execution provenance does not match the closed registry");

        var legacyBlue = dummyDataFactory.createBlueTeam();
        var legacyRed = dummyDataFactory.createRedTeam();
        var assignments = new ChampionSelectionValidator(champions).resolve(null);
        MatchTimeline autowiredTimeline = autowiredSimulator.simulate(
                legacyBlue, legacyRed, LEGACY_SIMULATOR_SEED, assignments);
        ObservedMatchSimulation explicitBaseline = configuredFactory.create(
                        SimulationRuntimeProfileId.BASELINE_V1,
                        SimulationInstrumentation.enabled())
                .simulateObserved(dummyDataFactory.createBlueTeam(),
                        dummyDataFactory.createRedTeam(), LEGACY_SIMULATOR_SEED, assignments);
        boolean springTimelineExact = timelineHash(autowiredTimeline).equals(
                timelineHash(explicitBaseline.timeline()));
        require(springTimelineExact,
                "Spring autowired MatchSimulator is not exact BASELINE_V1 timeline parity");

        verifyHttpMappingAndInjectedObjects();
        MatchSimulateRequest request = new MatchSimulateRequest(HTTP_SEED);
        MatchTimeline controllerTimeline = controller.simulate(request).getTimeline();
        MatchTimeline directHttpTimeline = autowiredSimulator.simulate(
                dummyDataFactory.createBlueTeam(), dummyDataFactory.createRedTeam(), HTTP_SEED,
                new ChampionSelectionValidator(champions).resolve(null));
        boolean httpTimelineExact = timelineHash(controllerTimeline).equals(
                timelineHash(directHttpTimeline));
        require(httpTimelineExact,
                "HTTP controller path is not exact injected MatchSimulator parity");

        SimulationOptions lowLevel = SimulationOptions.productionDefaults();
        SimulationGameplayConfiguration lowLevelConfiguration = gameplayConfiguration(lowLevel);
        String lowLevelConfigurationHash = SimulationRuntimeProfiles.configurationHash(
                lowLevelConfiguration);
        require(lowLevel.championMatchupMode().name().equals("GEOMETRIC_V2")
                        && lowLevel.teamCompositionGameplayMode().name().equals("PRODUCTION_V2")
                        && lowLevel.jungleClearContribution()
                        == JungleClearContribution.DISABLED_NOT_INTEGRATED,
                "SimulationOptions.productionDefaults() semantics changed");
        require(!lowLevelConfigurationHash.equals(baseline.configurationHash()),
                "Low-level productionDefaults must remain distinct from application BASELINE_V1");

        Phase13GB1AuditArtifactWriter.SourceTreeIdentity sourceTree =
                Phase13GB1AuditArtifactWriter.productionSourceTree(backendRoot);
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        put(values, "schemaVersion", Phase13GBFinalRuntimeIdentityEvidence.SCHEMA);
        put(values, "evidenceStatus", "VERIFIED_FROM_PRODUCTION_REGISTRY_AND_WIRING");
        put(values, "retainedRuntimeProfileId", baseline.profileId().name());
        put(values, "retainedConfigurationHash", baseline.configurationHash());
        put(values, "configurationHashAlgorithm",
                SimulationRuntimeProfiles.CONFIGURATION_HASH_ALGORITHM);
        addGameplayConfiguration(values, baseline.gameplayConfiguration());
        put(values, "diagnosticsInstrumentationSeparated", true);
        put(values, "activeGameplayRulesVersion", baseline.activeGameplayRulesVersion());
        put(values, "engineImplementationVersion", provenance.engineImplementationVersion());
        put(values, "productionSourceTreeHashAlgorithm", sourceTree.hashAlgorithm());
        put(values, "productionSourceTreeHash", sourceTree.hash());
        put(values, "productionSourceTreeFileCount", sourceTree.fileCount());
        put(values, "resourceProvenanceHash",
                provenance.resourceProvenance().resourceProvenanceHash());
        put(values, "draftRuleSetIdentity", provenance.draftRuleSetIdentity());
        put(values, "draftRuleSetHash", provenance.draftRuleSetHash());
        put(values, "draftScoringPolicyHash", provenance.draftScoringPolicyHash());

        put(values, "realDraftDefaultResolvedProfileId", provenance.runtimeProfileId().name());
        put(values, "realDraftDefaultAuthoritativeApplicationRuntimeDefault", true);
        put(values, "realDraftDefaultParityVerified", true);
        put(values, "realDraftDefaultRole", "AUTHORITATIVE_REAL_DRAFT_DEFAULT_OVERLOAD");
        put(values, "realDraftExplicitBaselineResolvedProfileId",
                explicitRun.executionProvenance().runtimeProfileId().name());
        put(values, "realDraftExplicitBaselineAuthoritativeApplicationRuntimeDefault", false);
        put(values, "realDraftExplicitBaselineParityVerified", true);
        put(values, "realDraftDefaultVsExplicitReplayIdentityExact",
                provenance.replayProvenanceHash().equals(
                        explicitRun.executionProvenance().replayProvenanceHash()));
        put(values, "realDraftDefaultVsExplicitTimelineExact", true);
        put(values, "realDraftExplicitBaselineRole", "EXPLICIT_CLOSED_PROFILE_CONTROL");
        put(values, "springAutowiredResolvedProfileId", baseline.profileId().name());
        put(values, "springAutowiredAuthoritativeApplicationRuntimeDefault", true);
        put(values, "springAutowiredParityVerified", true);
        put(values, "springAutowiredTimelineExact", springTimelineExact);
        put(values, "springAutowiredRole", "LEGACY_APPLICATION_SIMULATOR_BEAN");
        put(values, "httpResolvedProfileId", baseline.profileId().name());
        put(values, "httpAuthoritativeApplicationRuntimeDefault", true);
        put(values, "httpParityVerified", httpTimelineExact);
        put(values, "httpInjectedAutowiredSimulatorExact", true);
        put(values, "httpInputRosterSource", "DUMMY_DATA_FACTORY");
        put(values, "httpRealDraftTransitionPerformed", false);
        put(values, "httpRole", "LEGACY_POST_API_MATCHES_SIMULATE");
        put(values, "lowLevelProductionDefaultsIdentity",
                "LOW_LEVEL_SIMULATION_OPTIONS_PRODUCTION_DEFAULTS");
        put(values, "lowLevelProductionDefaultsAuthoritativeApplicationRuntimeDefault", false);
        put(values, "lowLevelProductionDefaultsConfigurationHash", lowLevelConfigurationHash);
        put(values, "lowLevelProductionDefaultsChampionMatchupMode",
                lowLevel.championMatchupMode().name());
        put(values, "lowLevelProductionDefaultsTeamCompositionGameplayMode",
                lowLevel.teamCompositionGameplayMode().name());
        put(values, "lowLevelProductionDefaultsJungleClearContribution",
                lowLevel.jungleClearContribution().name());
        put(values, "lowLevelProductionDefaultsRole",
                "LOW_LEVEL_CONSTRUCTOR_DEFAULT_NOT_APPLICATION_RUNTIME_AUTHORITY");
        put(values, "jungleEconomyCandidateActivation", false);
        put(values, "jungleTempoCandidateActivation", false);
        put(values, "productionGameplayChanged", false);
        put(values, "automaticTuningPerformed", false);
        put(values, "holdoutRerunPerformed", false);
        put(values, "runtimeIdentityHashAlgorithm",
                Phase13GBFinalRuntimeIdentityEvidence.HASH_ALGORITHM);
        return Phase13GBFinalRuntimeIdentityEvidence.create(values);
    }

    private void verifyHttpMappingAndInjectedObjects() throws Exception {
        RequestMapping classMapping = MatchController.class.getAnnotation(RequestMapping.class);
        Method method = MatchController.class.getDeclaredMethod(
                "simulate", MatchSimulateRequest.class);
        PostMapping methodMapping = method.getAnnotation(PostMapping.class);
        require(classMapping != null && ListSupport.contains(classMapping.value(), "/api/matches")
                        && methodMapping != null
                        && ListSupport.contains(methodMapping.value(), "/simulate"),
                "MatchController POST mapping changed");
        require(field("matchSimulator").get(controller) == autowiredSimulator,
                "MatchController is not using the autowired MatchSimulator instance");
        require(field("dummyDataFactory").get(controller) == dummyDataFactory,
                "MatchController is not using the Spring DummyDataFactory instance");
        require(java.util.Arrays.stream(MatchController.class.getDeclaredFields())
                        .noneMatch(value -> value.getType() == RealDraftMatchOrchestrator.class),
                "HTTP path unexpectedly transitioned to RealDraftMatchOrchestrator");
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = MatchController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static SimulationGameplayConfiguration gameplayConfiguration(SimulationOptions options) {
        return new SimulationGameplayConfiguration(
                options.laneCombatEnabled(), options.farmRecoveryEnabled(),
                options.jungleGankEnabled(), options.counterGankEnabled(), options.roamEnabled(),
                options.objectivePriorityEnabled(), options.lanePhaseEnabled(),
                options.midGameMacroEnabled(), options.objectiveDecisionEnabled(),
                options.lateGameMacroEnabled(), options.progressionEnabled(),
                options.progressionPowerEnabled(), options.championPowerEnabled(),
                options.championMatchupMode(), options.teamCompositionGameplayMode(),
                options.jungleClearContribution());
    }

    private static void addGameplayConfiguration(
            Map<String, String> values,
            SimulationGameplayConfiguration configuration
    ) {
        put(values, "gameplayConfigurationSchema", SimulationGameplayConfiguration.SCHEMA);
        put(values, "laneCombatEnabled", configuration.laneCombatEnabled());
        put(values, "farmRecoveryEnabled", configuration.farmRecoveryEnabled());
        put(values, "jungleGankEnabled", configuration.jungleGankEnabled());
        put(values, "counterGankEnabled", configuration.counterGankEnabled());
        put(values, "roamEnabled", configuration.roamEnabled());
        put(values, "objectivePriorityEnabled", configuration.objectivePriorityEnabled());
        put(values, "lanePhaseEnabled", configuration.lanePhaseEnabled());
        put(values, "midGameMacroEnabled", configuration.midGameMacroEnabled());
        put(values, "objectiveDecisionEnabled", configuration.objectiveDecisionEnabled());
        put(values, "lateGameMacroEnabled", configuration.lateGameMacroEnabled());
        put(values, "progressionEnabled", configuration.progressionEnabled());
        put(values, "progressionPowerEnabled", configuration.progressionPowerEnabled());
        put(values, "championPowerEnabled", configuration.championPowerEnabled());
        put(values, "championMatchupMode", configuration.championMatchupMode().name());
        put(values, "teamCompositionGameplayMode",
                configuration.teamCompositionGameplayMode().name());
        put(values, "jungleClearContribution",
                configuration.jungleClearContribution().name());
    }

    private String timelineHash(MatchTimeline timeline) throws IOException {
        return sha256(canonicalMapper.writeValueAsBytes(timeline));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void put(Map<String, String> values, String key, Object value) {
        values.put(key, Objects.toString(value));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class ListSupport {
        private ListSupport() {
        }

        private static boolean contains(String[] values, String expected) {
            return java.util.Arrays.asList(values).contains(expected);
        }
    }
}
