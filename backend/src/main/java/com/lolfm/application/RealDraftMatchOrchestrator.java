package com.lolfm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.draft.DraftEngine;
import com.lolfm.draft.DraftResourceSet;
import com.lolfm.draft.DraftRuleSet;
import com.lolfm.draft.DraftScoringPolicy;
import com.lolfm.draft.DraftSelectionContext;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.FinalDraftResult;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.player.PlayerIdentityCatalog;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import com.lolfm.simulator.ObservedMatchSimulation;
import com.lolfm.simulator.ResolvedSimulationRuntimeProfile;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import com.lolfm.simulator.TeamSide;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Deterministic application flow from authored LCK rosters through DraftEngine into MatchSimulator. */
@Component
public final class RealDraftMatchOrchestrator {
    private final LckTeamAssembler teams;
    private final DraftEngine drafts;
    private final ConfiguredMatchSimulatorFactory matches;
    private final RealDraftMatchPreflightValidator preflight;
    private final SimulationProvenanceService provenance;
    private final MatchEngineV1InputFactory matchEngineV1Inputs;
    private final MatchEngineV1 matchEngineV1;

    @Autowired
    public RealDraftMatchOrchestrator(ObjectMapper mapper, ChampionCatalog champions,
                                      LckTeamAssembler teams,
                                      ConfiguredMatchSimulatorFactory matches,
                                      RealDraftMatchPreflightValidator preflight,
                                      PlayerIdentityCatalog identities,
                                      PlayerRatingCatalog ratings,
                                      ChampionProficiencyCatalog proficiencies,
                                      MatchEngineV1InputFactory matchEngineV1Inputs,
                                      MatchEngineV1 matchEngineV1) {
        DraftResourceSet resources = DraftResourceSet.loadDefault(mapper, champions);
        DraftRuleSet rules = DraftRuleSet.professional();
        DraftScoringPolicy policy = DraftScoringPolicy.standard();
        this.teams = Objects.requireNonNull(teams, "teams");
        this.drafts = new DraftEngine(resources, rules, policy);
        this.matches = Objects.requireNonNull(matches, "matches");
        this.preflight = Objects.requireNonNull(preflight, "preflight");
        provenance = new SimulationProvenanceService(
                mapper, resources, identities, ratings, proficiencies, rules, policy);
        this.matchEngineV1Inputs = Objects.requireNonNull(
                matchEngineV1Inputs, "matchEngineV1Inputs");
        this.matchEngineV1 = Objects.requireNonNull(matchEngineV1, "matchEngineV1");
    }

    /** One isolated game with a fresh series scope. */
    public RealDraftMatchResult orchestrate(String blueTeamCode, String redTeamCode, long matchSeed) {
        return orchestrate(blueTeamCode, redTeamCode, new SeriesDraftHistory(), matchSeed,
                MatchEngineV1Policy.authoritative().retainedRuntimeProfileId(),
                SimulationInstrumentation.enabled());
    }

    /** One isolated game with an explicit, closed-set runtime profile. */
    public RealDraftMatchResult orchestrate(
            String blueTeamCode,
            String redTeamCode,
            long matchSeed,
            SimulationRuntimeProfileId profileId
    ) {
        return orchestrate(blueTeamCode, redTeamCode, new SeriesDraftHistory(), matchSeed,
                profileId, SimulationInstrumentation.enabled());
    }

    /** One game in a caller-owned series; successful completion commits this game's picks. */
    public RealDraftMatchResult orchestrate(String blueTeamCode, String redTeamCode,
                                            SeriesDraftHistory seriesHistory, long matchSeed) {
        return orchestrate(blueTeamCode, redTeamCode, seriesHistory, matchSeed,
                MatchEngineV1Policy.authoritative().retainedRuntimeProfileId(),
                SimulationInstrumentation.enabled());
    }

    /** One series game with an explicit, closed-set runtime profile. */
    public RealDraftMatchResult orchestrate(
            String blueTeamCode,
            String redTeamCode,
            SeriesDraftHistory seriesHistory,
            long matchSeed,
            SimulationRuntimeProfileId profileId
    ) {
        return orchestrate(blueTeamCode, redTeamCode, seriesHistory, matchSeed,
                profileId, SimulationInstrumentation.enabled());
    }

    /**
     * One series game with gameplay identity and observational instrumentation
     * supplied through separate contracts.
     */
    public RealDraftMatchResult orchestrate(
            String blueTeamCode,
            String redTeamCode,
            SeriesDraftHistory seriesHistory,
            long matchSeed,
            SimulationRuntimeProfileId profileId,
            SimulationInstrumentation instrumentation
    ) {
        Objects.requireNonNull(seriesHistory, "seriesHistory");
        ResolvedSimulationRuntimeProfile profile = SimulationRuntimeProfiles.resolve(profileId);
        var configuredMatchSimulator = matches.create(
                profileId, Objects.requireNonNull(instrumentation, "instrumentation"));
        String normalizedBlueTeamCode = normalizeTeamCode(blueTeamCode, "blueTeamCode");
        String normalizedRedTeamCode = normalizeTeamCode(redTeamCode, "redTeamCode");
        Team blueTeam = teams.assemble(normalizedBlueTeamCode);
        Team redTeam = teams.assemble(normalizedRedTeamCode);
        DraftTeamContext blueContext = DraftTeamContext.from(blueTeam);
        DraftTeamContext redContext = DraftTeamContext.from(redTeam);
        Set<ChampionId> exclusionsBeforeDraft = seriesHistory.consumedPicks();
        int gameNumber = seriesHistory.committedGameCount() + 1;

        DraftSelectionContext selectionContext = RealDraftSelectionContextFactory.create(
                matchSeed, normalizedBlueTeamCode, blueTeam, normalizedRedTeamCode, redTeam,
                gameNumber, exclusionsBeforeDraft);
        FinalDraftResult draftResult = drafts.draft(
                blueContext, redContext, seriesHistory, selectionContext);
        preflight.validate(normalizedBlueTeamCode, blueTeam, normalizedRedTeamCode, redTeam,
                blueContext, redContext, draftResult, seriesHistory);

        // The Draft-owned object is passed through directly; no reinterpretation or reassignment occurs.
        ObservedMatchSimulation observed = configuredMatchSimulator.simulateObserved(
                blueTeam, redTeam, matchSeed, draftResult.matchChampionAssignments());
        MatchTimeline timeline = observed.timeline();
        SimulationExecutionProvenance executionProvenance = provenance.create(
                profile, instrumentation, normalizedBlueTeamCode, blueTeam,
                normalizedRedTeamCode, redTeam, matchSeed, gameNumber,
                exclusionsBeforeDraft, draftResult, timeline, observed.randomFingerprint());

        seriesHistory.commitCompleted(draftResult);
        validateCommittedHistory(seriesHistory, draftResult, exclusionsBeforeDraft, gameNumber);
        return new RealDraftMatchResult(normalizedBlueTeamCode, normalizedRedTeamCode,
                blueTeam, redTeam, blueContext, redContext, draftResult,
                timeline, matchSeed, gameNumber, exclusionsBeforeDraft,
                seriesHistory.consumedPicks(), executionProvenance);
    }

    /** One isolated real-Draft game projected through the authoritative Match Engine V1 boundary. */
    public MatchEngineV1Output orchestrateV1(
            String blueTeamCode, String redTeamCode, long matchSeed
    ) {
        return orchestrateV1(blueTeamCode, redTeamCode, new SeriesDraftHistory(), matchSeed,
                SimulationInstrumentation.enabled());
    }

    /**
     * One caller-owned series game through V1. The completed Draft is committed exactly once,
     * and only after simulation, mandatory provenance and immutable output creation succeed.
     */
    public MatchEngineV1Output orchestrateV1(
            String blueTeamCode,
            String redTeamCode,
            SeriesDraftHistory seriesHistory,
            long matchSeed
    ) {
        return orchestrateV1(blueTeamCode, redTeamCode, seriesHistory, matchSeed,
                SimulationInstrumentation.enabled());
    }

    public MatchEngineV1Output orchestrateV1(
            String blueTeamCode,
            String redTeamCode,
            SeriesDraftHistory seriesHistory,
            long matchSeed,
            SimulationInstrumentation instrumentation
    ) {
        Objects.requireNonNull(seriesHistory, "seriesHistory");
        Objects.requireNonNull(instrumentation, "instrumentation");
        String normalizedBlueTeamCode = normalizeTeamCode(blueTeamCode, "blueTeamCode");
        String normalizedRedTeamCode = normalizeTeamCode(redTeamCode, "redTeamCode");
        Team blueTeam = teams.assemble(normalizedBlueTeamCode);
        Team redTeam = teams.assemble(normalizedRedTeamCode);
        DraftTeamContext blueContext = DraftTeamContext.from(blueTeam);
        DraftTeamContext redContext = DraftTeamContext.from(redTeam);
        Set<ChampionId> exclusionsBeforeDraft = seriesHistory.consumedPicks();
        int gameNumber = seriesHistory.committedGameCount() + 1;
        DraftSelectionContext selectionContext = RealDraftSelectionContextFactory.create(
                matchSeed, normalizedBlueTeamCode, blueTeam, normalizedRedTeamCode, redTeam,
                gameNumber, exclusionsBeforeDraft);
        FinalDraftResult draftResult = drafts.draft(
                blueContext, redContext, seriesHistory, selectionContext);
        preflight.validate(normalizedBlueTeamCode, blueTeam, normalizedRedTeamCode, redTeam,
                blueContext, redContext, draftResult, seriesHistory);
        MatchEngineV1Input input = matchEngineV1Inputs.fromRealDraft(
                normalizedBlueTeamCode, blueTeam, normalizedRedTeamCode, redTeam,
                matchSeed, gameNumber, exclusionsBeforeDraft, draftResult);
        MatchEngineV1.MatchEngineV1Execution execution = matchEngineV1.executeDetailed(
                input, instrumentation);
        seriesHistory.commitCompleted(draftResult);
        validateCommittedHistory(seriesHistory, draftResult, exclusionsBeforeDraft, gameNumber);
        return execution.output();
    }

    private static String normalizeTeamCode(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private static void validateCommittedHistory(SeriesDraftHistory history, FinalDraftResult result,
                                                 Set<ChampionId> exclusionsBeforeDraft,
                                                 int gameNumber) {
        if (history.committedGameCount() != gameNumber) {
            throw new IllegalStateException("HARD_FEARLESS_COMMIT_COUNT_MISMATCH");
        }
        LinkedHashSet<ChampionId> expected = new LinkedHashSet<>(exclusionsBeforeDraft);
        expected.addAll(result.bluePicks());
        expected.addAll(result.redPicks());
        if (!history.consumedPicks().equals(Set.copyOf(expected))) {
            throw new IllegalStateException("HARD_FEARLESS_COMMIT_PICK_MISMATCH");
        }
        int expectedCount = gameNumber * TeamSide.values().length * Position.values().length;
        if (expected.size() != expectedCount) {
            throw new IllegalStateException("HARD_FEARLESS_COMMIT_CARDINALITY_MISMATCH");
        }
    }
}
