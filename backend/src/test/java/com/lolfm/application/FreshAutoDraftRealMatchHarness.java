package com.lolfm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.domain.Team;
import com.lolfm.draft.DraftEngine;
import com.lolfm.draft.DraftResourceSet;
import com.lolfm.draft.DraftRuleSet;
import com.lolfm.draft.DraftScoringPolicy;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.FinalDraftResult;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.player.PlayerIdentityCatalog;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import com.lolfm.simulator.Phase13GB1SimulationExecutor;
import com.lolfm.simulator.MatchEngineV9InstrumentationExecutor;
import com.lolfm.simulator.ResolvedSimulationRuntimeProfile;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Draft-only production-equivalent preparation followed by paired profile simulation. */
public final class FreshAutoDraftRealMatchHarness {
    public static final String SCHEMA = "FRESH_AUTO_DRAFT_REAL_MATCH_HARNESS_V1";

    private final LckTeamAssembler teams;
    private final DraftEngine drafts;
    private final RealDraftMatchPreflightValidator preflight;
    private final MatchEngineV1InputFactory inputs;
    private final ConfiguredMatchSimulatorFactory simulators;
    private final SimulationProvenanceService provenance;

    public FreshAutoDraftRealMatchHarness(
            ObjectMapper mapper,
            ChampionCatalog champions,
            LckTeamAssembler teams,
            RealDraftMatchPreflightValidator preflight,
            MatchEngineV1InputFactory inputs,
            ConfiguredMatchSimulatorFactory simulators,
            PlayerIdentityCatalog identities,
            PlayerRatingCatalog ratings,
            ChampionProficiencyCatalog proficiencies
    ) {
        DraftResourceSet resources = DraftResourceSet.loadDefault(mapper, champions);
        DraftRuleSet rules = DraftRuleSet.professional();
        DraftScoringPolicy scoring = DraftScoringPolicy.standard();
        this.teams = Objects.requireNonNull(teams);
        drafts = new DraftEngine(resources, rules, scoring);
        this.preflight = Objects.requireNonNull(preflight);
        this.inputs = Objects.requireNonNull(inputs);
        this.simulators = Objects.requireNonNull(simulators);
        provenance = new SimulationProvenanceService(
                mapper, resources, identities, ratings, proficiencies, rules, scoring);
    }

    /** Runs the target game's production Auto Draft exactly once and performs no simulation. */
    public PreparedInput prepare(
            MatchEngineV9FreshRequalificationContract.Fixture fixture, long seed
    ) {
        Objects.requireNonNull(fixture);
        Team blue = teams.assemble(normalize(fixture.blueTeamCode()));
        Team red = teams.assemble(normalize(fixture.redTeamCode()));
        DraftTeamContext blueContext = DraftTeamContext.from(blue);
        DraftTeamContext redContext = DraftTeamContext.from(red);
        SeriesDraftHistory history = new SeriesDraftHistory();
        if (fixture.seriesGameNumber() == 2) {
            long historySeed = MatchEngineV9FreshRequalificationContract
                    .historyPreparationSeed(fixture);
            FinalDraftResult gameOne = draft(fixture, blue, red, blueContext, redContext,
                    history, historySeed);
            history.commitCompleted(gameOne);
            if (history.committedGameCount() != 1 || history.consumedPicks().size() != 10) {
                throw new IllegalStateException("Hard Fearless history preparation mismatch");
            }
        }
        Set<ChampionId> exclusions = history.consumedPicks();
        if (history.committedGameCount() + 1 != fixture.seriesGameNumber()) {
            throw new IllegalStateException("Fixture series history mismatch");
        }
        FinalDraftResult result = draft(fixture, blue, red, blueContext, redContext,
                history, seed);
        MatchEngineV1Input input = inputs.fromRealDraft(
                "FRESH_REQUALIFICATION:" + fixture.fixtureId() + ":SEED:" + seed,
                fixture.blueTeamCode(), blue, fixture.redTeamCode(), red, seed,
                fixture.seriesGameNumber(), exclusions, result);
        if (!input.finalDraft().draftSelectionPolicyId().equals(
                com.lolfm.draft.AutoDraftSelectionPolicy.production().policyId())
                || input.finalDraft().selectionTraces().size() != 20) {
            throw new IllegalStateException("Target Draft did not use production Auto Draft evidence");
        }
        return new PreparedInput(SCHEMA, fixture, seed, blue, red, result, input,
                exclusions, 1,
                MatchEngineV9FreshRequalificationContract.DRAFT_REUSE_POLICY);
    }

    public List<Executed> executeProfiles(PreparedInput prepared) {
        List<Executed> runs = MatchEngineV9FreshRequalificationContract.PROFILES.stream()
                .map(profile -> execute(prepared, profile))
                .toList();
        if (runs.stream().map(value -> value.provenance().draftDecisionHash())
                        .distinct().count() != 1
                || runs.stream().map(value -> value.provenance().finalDraftHash())
                        .distinct().count() != 1
                || runs.stream().map(value -> value.provenance().finalAssignmentHash())
                        .distinct().count() != 1
                || runs.stream().map(value -> value.prepared().input().inputHash())
                        .distinct().count() != 1) {
            throw new IllegalStateException("Profile loop did not share one immutable Draft input");
        }
        return runs;
    }

    public Executed execute(
            PreparedInput prepared,
            SimulationRuntimeProfileId profileId
    ) {
        Objects.requireNonNull(prepared);
        ResolvedSimulationRuntimeProfile profile = SimulationRuntimeProfiles.resolve(profileId);
        var execution = Phase13GB1SimulationExecutor.execute(
                simulators, prepared.blueTeam(), prepared.redTeam(),
                prepared.draftResult().matchChampionAssignments(), profileId,
                prepared.seed(), prepared.fixture().blueTeamCode(),
                prepared.fixture().redTeamCode());
        SimulationExecutionProvenance executionProvenance = provenance.create(
                profile, SimulationInstrumentation.enabled(), prepared.fixture().blueTeamCode(),
                prepared.blueTeam(), prepared.fixture().redTeamCode(), prepared.redTeam(),
                prepared.seed(), prepared.fixture().seriesGameNumber(),
                prepared.hardFearlessExclusionsBeforeDraft(), prepared.draftResult(),
                execution.timeline(), execution.randomFingerprint());
        MatchEngineV1Input.DraftInput inputDraft = prepared.input().finalDraft();
        if (!executionProvenance.draftDecisionHash().equals(inputDraft.draftDecisionHash())
                || !executionProvenance.finalDraftHash().equals(inputDraft.finalDraftHash())
                || !executionProvenance.finalAssignmentHash().equals(
                        inputDraft.finalAssignmentHash())) {
            throw new IllegalStateException("Draft/input/provenance binding mismatch");
        }
        return new Executed(prepared, profileId, execution, executionProvenance);
    }

    public MatchEngineV9InstrumentationExecutor.Result executeInstrumentationDisabled(
            PreparedInput prepared, SimulationRuntimeProfileId profileId
    ) {
        return MatchEngineV9InstrumentationExecutor.execute(
                simulators, prepared.blueTeam(), prepared.redTeam(),
                prepared.draftResult().matchChampionAssignments(), profileId,
                SimulationInstrumentation.disabled(), prepared.seed(),
                prepared.fixture().blueTeamCode(), prepared.fixture().redTeamCode());
    }

    public SimulationProvenanceService provenance() {
        return provenance;
    }

    private FinalDraftResult draft(
            MatchEngineV9FreshRequalificationContract.Fixture fixture,
            Team blue,
            Team red,
            DraftTeamContext blueContext,
            DraftTeamContext redContext,
            SeriesDraftHistory history,
            long seed
    ) {
        Set<ChampionId> exclusions = history.consumedPicks();
        int game = history.committedGameCount() + 1;
        FinalDraftResult result = drafts.draft(blueContext, redContext, history,
                RealDraftSelectionContextFactory.create(seed, fixture.blueTeamCode(), blue,
                        fixture.redTeamCode(), red, game, exclusions));
        preflight.validate(fixture.blueTeamCode(), blue, fixture.redTeamCode(), red,
                blueContext, redContext, result, history);
        return result;
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public record PreparedInput(
            String schemaVersion,
            MatchEngineV9FreshRequalificationContract.Fixture fixture,
            long seed,
            Team blueTeam,
            Team redTeam,
            FinalDraftResult draftResult,
            MatchEngineV1Input input,
            Set<ChampionId> hardFearlessExclusionsBeforeDraft,
            int targetProductionAutoDraftCount,
            String reusePolicy
    ) {
        public PreparedInput {
            hardFearlessExclusionsBeforeDraft = Set.copyOf(
                    hardFearlessExclusionsBeforeDraft);
            if (targetProductionAutoDraftCount != 1
                    || !MatchEngineV9FreshRequalificationContract.DRAFT_REUSE_POLICY
                    .equals(reusePolicy)) {
                throw new IllegalArgumentException("Invalid target Draft reuse evidence");
            }
        }
    }

    public record Executed(
            PreparedInput prepared,
            SimulationRuntimeProfileId profileId,
            Phase13GB1SimulationExecutor.Execution execution,
            SimulationExecutionProvenance provenance
    ) { }
}
