package com.lolfm.application;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionDefinition;
import com.lolfm.champion.ChampionId;
import com.lolfm.domain.Player;
import com.lolfm.domain.Team;
import com.lolfm.draft.AutoDraftSelectionPolicy;
import com.lolfm.draft.DraftSelectionTrace;
import com.lolfm.draft.DraftSelectionTraceHasher;
import com.lolfm.draft.DraftStateHasher;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.DraftTurnControlEvidence;
import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.draft.PlayerControlledDraftResult;
import com.lolfm.draft.PlayerDraftControlPolicy;
import com.lolfm.dto.PlayerDraftApiV1Dtos;
import com.lolfm.dto.RealMatchApiV1Dtos;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.SimulationRandomFingerprint;
import com.lolfm.simulator.TeamSide;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Honest mixed-authority projection; it never fabricates Auto Draft evidence for player turns. */
@Component
public final class PlayerDraftApiV1ResponseMapper {
    private final LckTeamAssembler teams;
    private final ChampionCatalog champions;
    private final SimulationProvenanceService provenance;
    private final PlayerControlledDraftEngine drafts;
    private final RealMatchApiV1ResponseMapper commonMatches;

    public PlayerDraftApiV1ResponseMapper(
            LckTeamAssembler teams,
            ChampionCatalog champions,
            SimulationProvenanceService provenance,
            PlayerControlledDraftEngine drafts,
            RealMatchApiV1ResponseMapper commonMatches
    ) {
        this.teams = Objects.requireNonNull(teams, "teams");
        this.champions = Objects.requireNonNull(champions, "champions");
        this.provenance = Objects.requireNonNull(provenance, "provenance");
        this.drafts = Objects.requireNonNull(drafts, "drafts");
        this.commonMatches = Objects.requireNonNull(commonMatches, "commonMatches");
    }

    public PlayerDraftApiV1Dtos.SessionResponse session(PlayerDraftSessionView view) {
        Team blue = teams.assemble(view.blueTeamCode());
        Team red = teams.assemble(view.redTeamCode());
        DraftTeamContext blueContext = DraftTeamContext.from(blue);
        DraftTeamContext redContext = DraftTeamContext.from(red);
        PlayerControlledDraftEngine.SelectionView selection = null;
        if (view.status() == PlayerDraftSessionStatus.ACTIVE) {
            selection = drafts.view(view.progress(), blueContext, redContext);
        }
        var state = view.progress().state();
        var current = view.status() != PlayerDraftSessionStatus.ACTIVE
                ? null : new PlayerDraftApiV1Dtos.CurrentTurn(
                state.currentTurn().number(), state.currentTurn().side(),
                state.currentTurn().actionType());
        return new PlayerDraftApiV1Dtos.SessionResponse(
                PlayerDraftApiV1Dtos.SESSION_SCHEMA, view.sessionId(), view.revision(),
                view.status(), List.of(
                new PlayerDraftApiV1Dtos.TeamIdentity(
                        TeamSide.BLUE, view.blueTeamCode(), blue.getName()),
                new PlayerDraftApiV1Dtos.TeamIdentity(
                        TeamSide.RED, view.redTeamCode(), red.getName())),
                view.controlledSide(), Long.toString(view.matchSeed()),
                view.seriesGameNumber(),
                new PlayerDraftApiV1Dtos.RuleIdentity(
                        state.ruleSet().identity(), provenance.draftRuleSetHash()),
                new PlayerDraftApiV1Dtos.PolicyIdentity(
                        "DRAFT_SCORING_POLICY_STANDARD_V1",
                        provenance.draftScoringPolicyHash()),
                autoPolicy(), controlPolicy(), current,
                new PlayerDraftApiV1Dtos.DraftState(
                        ids(state.blueBans()), ids(state.redBans()),
                        ids(state.bluePicks()), ids(state.redPicks()),
                        state.fearlessExclusions().stream().map(ChampionId::value)
                                .sorted().toList()),
                evidence(view.progress().turnEvidence()),
                selection == null ? List.of() : selection.selectable().stream()
                        .map(value -> new PlayerDraftApiV1Dtos.ChampionOption(
                                champion(value.championId()), value.feasibleRoles().stream()
                                .sorted().toList())).toList(),
                selection == null ? List.of() : selection.unavailable().stream()
                        .map(value -> new PlayerDraftApiV1Dtos.UnavailableChampion(
                                champion(value.championId()), value.reason())).toList(),
                selection == null ? List.of() : selection.recommendations().stream()
                        .map(value -> new PlayerDraftApiV1Dtos.Recommendation(
                                champion(value.championId()), value.advisoryRank(),
                                value.immediateScore(), value.continuationScore(),
                                value.finalSearchScore(), true)).toList(),
                selection == null ? null : selection.selectableSetIdentity(),
                DraftStateHasher.hash(state), completed(view, blue, red));
    }

    public PlayerDraftApiV1Dtos.SimulationResponse simulation(
            PlayerDraftSessionView session, MatchEngineV1Output output
    ) {
        PlayerDraftApiV1Dtos.SessionResponse sessionResponse = session(session);
        RealMatchApiV1ResponseMapper.SharedMatchComponents common =
                commonMatches.sharedMatchComponents(output);
        SimulationExecutionProvenance execution = output.executionProvenance();
        var control = Objects.requireNonNull(
                output.finalDraft().controlEvidence(), "controlEvidence");
        SimulationRandomFingerprint random = execution.randomFingerprint();
        PlayerDraftApiV1Dtos.MatchDraftBinding draft =
                new PlayerDraftApiV1Dtos.MatchDraftBinding(
                        output.finalDraft().draftDecisionHash(),
                        output.finalDraft().finalDraftHash(),
                        output.finalDraft().finalAssignmentHash(), autoPolicy(), controlPolicy(),
                        output.finalDraft().draftSelectionTraceHash(),
                        control.controlEvidenceHash(), evidence(control.turns()));
        PlayerDraftApiV1Dtos.MatchIntegrity integrity =
                new PlayerDraftApiV1Dtos.MatchIntegrity(
                        execution.runtimeProfileId().name(), execution.configurationHash(),
                        execution.engineImplementationVersion(),
                        execution.activeGameplayRulesVersion(), control.policyId(),
                        control.policyHash(), control.controlEvidenceHash(), output.inputHash(),
                        execution.replayProvenanceHash(),
                        execution.resourceProvenance().resourceProvenanceHash(),
                        output.simulatorTimelineHash(), output.structuredTimelineHash(),
                        output.outputHash(), new RealMatchApiV1Dtos.RandomFingerprint(
                        random.schemaVersion(), random.randomDrawCount(),
                        random.randomTraceHash(), random.randomTraceHashAlgorithm()), true);
        return new PlayerDraftApiV1Dtos.SimulationResponse(
                PlayerDraftApiV1Dtos.SIMULATION_RESPONSE_SCHEMA, sessionResponse,
                new PlayerDraftApiV1Dtos.MatchPayload(
                        PlayerDraftApiV1Dtos.MATCH_PAYLOAD_SCHEMA, output.matchIdentity(),
                        Long.toString(execution.matchSeed()), common.productionPolicy(),
                        common.teams(), draft, common.result(), common.timeline(), integrity));
    }

    private PlayerDraftApiV1Dtos.CompletedDraft completed(
            PlayerDraftSessionView view, Team blue, Team red
    ) {
        if (!view.progress().complete()) return null;
        PlayerControlledDraftResult result = Objects.requireNonNull(
                view.progress().result(), "completed Draft result");
        var control = result.controlEvidence();
        return new PlayerDraftApiV1Dtos.CompletedDraft(
                result.draftIdentity(), control.schemaVersion(), control.controlEvidenceHash(),
                PlayerDraftControlPolicy.HASH_ALGORITHM,
                result.matchChampionAssignments().asMap().entrySet().stream()
                        .sorted(java.util.Map.Entry.comparingByKey(
                                Comparator.comparing(PlayerKey::stableId)))
                        .map(entry -> assignment(entry.getKey(), entry.getValue().championId(),
                                entry.getKey().side() == TeamSide.BLUE ? blue : red))
                        .toList());
    }

    private static RealMatchApiV1Dtos.FinalAssignment assignment(
            PlayerKey key, ChampionId championId, Team team
    ) {
        Player player = team.getPlayers().stream()
                .filter(value -> value.getPosition() == key.position())
                .findFirst().orElseThrow();
        return new RealMatchApiV1Dtos.FinalAssignment(
                player.requirePlayerId().value(), key.side(), key.position(), championId.value());
    }

    private List<PlayerDraftApiV1Dtos.TurnEvidence> evidence(
            List<DraftTurnControlEvidence> values
    ) {
        return values.stream().map(value -> new PlayerDraftApiV1Dtos.TurnEvidence(
                value.turn(), value.side(), value.actionType(), value.championId().value(),
                value.authority(), value.stateBeforeHash(), value.stateAfterHash(),
                value.autoSelectionTrace() == null ? null : trace(value.autoSelectionTrace()),
                value.playerSelectionEvidence() == null ? null
                        : new PlayerDraftApiV1Dtos.ManualSelectionEvidence(
                        value.playerSelectionEvidence().controlledSide(),
                        value.playerSelectionEvidence().turn(),
                        value.playerSelectionEvidence().actionType(),
                        value.playerSelectionEvidence().championId().value(),
                        value.playerSelectionEvidence().stateBeforeHash(),
                        value.playerSelectionEvidence().selectableSetIdentity(),
                        value.playerSelectionEvidence().legalityResult().name(),
                        value.playerSelectionEvidence().clientActionId()))).toList();
    }

    private static RealMatchApiV1Dtos.DraftSelectionTrace trace(DraftSelectionTrace trace) {
        return new RealMatchApiV1Dtos.DraftSelectionTrace(
                trace.policyId(), trace.policyMode(), trace.policyHash(),
                trace.selectionContextHash(), trace.turn(), trace.side(), trace.actionType(),
                trace.bestCandidateId().value(), trace.bestCanonicalScore(),
                trace.eligiblePool().stream().map(entry ->
                        new RealMatchApiV1Dtos.DraftSelectionPoolEntry(
                                entry.championId().value(), entry.canonicalRank(),
                                entry.rawFinalSearchScore(), entry.canonicalFinalScore(),
                                entry.canonicalScoreLoss(), entry.rankWeight())).toList(),
                trace.selectedChampionId().value(), trace.selectedRank(),
                trace.selectedCanonicalScoreLoss(), trace.drawBucket(),
                trace.totalEligibleWeight(), trace.reason().name());
    }

    private RealMatchApiV1Dtos.ChampionPresentation champion(ChampionId id) {
        ChampionDefinition value = champions.get(id);
        return new RealMatchApiV1Dtos.ChampionPresentation(
                id.value(), value.displayNameKo(), value.displayNameEn(), value.portraitUrl());
    }

    private static PlayerDraftApiV1Dtos.PolicyIdentity autoPolicy() {
        AutoDraftSelectionPolicy policy = AutoDraftSelectionPolicy.production();
        return new PlayerDraftApiV1Dtos.PolicyIdentity(
                policy.policyId(), policy.policyHash());
    }

    private static PlayerDraftApiV1Dtos.PolicyIdentity controlPolicy() {
        return new PlayerDraftApiV1Dtos.PolicyIdentity(
                PlayerDraftControlPolicy.POLICY_ID, PlayerDraftControlPolicy.POLICY_HASH);
    }

    private static List<String> ids(List<ChampionId> ids) {
        return ids.stream().map(ChampionId::value).toList();
    }
}
