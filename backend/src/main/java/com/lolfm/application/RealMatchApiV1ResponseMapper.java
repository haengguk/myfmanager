package com.lolfm.application;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionDefinition;
import com.lolfm.domain.Player;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.dto.RealMatchApiV1Dtos;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.SimulationRandomFingerprint;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** Field-by-field projection from the frozen engine output into HTTP transport records. */
@Component
public final class RealMatchApiV1ResponseMapper {
    private static final int EXPECTED_TEAM_COUNT = 10;
    private static final int EXPECTED_PLAYER_COUNT = 50;

    private final LckTeamAssembler teams;
    private final ChampionCatalog champions;
    private final SimulationProvenanceService provenance;

    public RealMatchApiV1ResponseMapper(
            LckTeamAssembler teams,
            ChampionCatalog champions,
            SimulationProvenanceService provenance
    ) {
        this.teams = Objects.requireNonNull(teams, "teams");
        this.champions = Objects.requireNonNull(champions, "champions");
        this.provenance = Objects.requireNonNull(provenance, "provenance");
    }

    public RealMatchApiV1Dtos.OptionsResponse options() {
        Set<String> playerIds = new HashSet<>();
        List<RealMatchApiV1Dtos.OptionTeam> optionTeams = teams.teamCodes().stream()
                .sorted()
                .map(teamCode -> optionTeam(teamCode, teams.assemble(teamCode), playerIds))
                .toList();
        if (optionTeams.size() != EXPECTED_TEAM_COUNT
                || playerIds.size() != EXPECTED_PLAYER_COUNT) {
            throw new IllegalStateException("REAL_MATCH_OPTIONS_ROSTER_CARDINALITY_DRIFT");
        }
        TreeMap<String, String> versions = new TreeMap<>();
        provenance.resourceProvenance().resources().forEach(resource ->
                versions.put(resource.role(), resource.version()));
        return new RealMatchApiV1Dtos.OptionsResponse(
                RealMatchApiV1Dtos.OPTIONS_SCHEMA,
                MatchEngineV1Policy.CONTRACT_SCHEMA,
                productionPolicy(),
                new RealMatchApiV1Dtos.SeedPolicy(true, RealMatchApiV1Dtos.SEED_ENCODING),
                optionTeams,
                new RealMatchApiV1Dtos.ResourceVersions(
                        provenance.resourceProvenance().resourceProvenanceHash(), versions));
    }

    public RealMatchApiV1Dtos.Response response(MatchEngineV1Output output) {
        Objects.requireNonNull(output, "output");
        SimulationExecutionProvenance execution = Objects.requireNonNull(
                output.executionProvenance(), "executionProvenance");
        List<RealMatchApiV1Dtos.TeamPresentation> presentation = List.of(
                teamPresentation(output, TeamSide.BLUE, execution.blueTeamCode()),
                teamPresentation(output, TeamSide.RED, execution.redTeamCode()));
        return new RealMatchApiV1Dtos.Response(
                RealMatchApiV1Dtos.RESPONSE_SCHEMA,
                output.matchIdentity(),
                Long.toString(execution.matchSeed()),
                presentation,
                draft(output),
                result(output.resultSummary()),
                timeline(output.timeline()),
                integrity(output, execution));
    }

    private RealMatchApiV1Dtos.OptionTeam optionTeam(
            String teamCode, Team team, Set<String> allPlayerIds
    ) {
        List<RealMatchApiV1Dtos.OptionPlayer> lineup = team.getPlayers().stream()
                .sorted(Comparator.comparing(Player::getPosition))
                .map(player -> {
                    String playerId = player.requirePlayerId().value();
                    if (!allPlayerIds.add(playerId)) {
                        throw new IllegalStateException(
                                "REAL_MATCH_OPTIONS_DUPLICATE_PLAYER_ID: " + playerId);
                    }
                    return new RealMatchApiV1Dtos.OptionPlayer(
                            playerId, player.getName(), player.getPosition());
                }).toList();
        if (lineup.size() != Position.values().length
                || !lineup.stream().map(RealMatchApiV1Dtos.OptionPlayer::position)
                .collect(java.util.stream.Collectors.toSet())
                .equals(EnumSet.allOf(Position.class))) {
            throw new IllegalStateException(
                    "REAL_MATCH_OPTIONS_POSITION_COVERAGE_DRIFT: " + teamCode);
        }
        return new RealMatchApiV1Dtos.OptionTeam(teamCode, team.getName(), lineup);
    }

    private RealMatchApiV1Dtos.TeamPresentation teamPresentation(
            MatchEngineV1Output output, TeamSide side, String teamCode
    ) {
        Team source = teams.assemble(teamCode);
        MatchEngineV1Output.TeamResultV1 result = output.resultSummary().teams().stream()
                .filter(value -> value.teamSide() == side).findFirst().orElseThrow();
        if (!result.teamIdentity().equals(teamCode)) {
            throw new IllegalStateException("REAL_MATCH_PRESENTATION_TEAM_IDENTITY_MISMATCH");
        }
        List<RealMatchApiV1Dtos.PlayerPresentation> lineup = new ArrayList<>();
        for (Position position : Position.values()) {
            MatchEngineV1Output.PlayerResultV1 playerResult = output.resultSummary().players()
                    .stream().filter(value -> value.teamSide() == side
                            && value.position() == position).findFirst().orElseThrow();
            Player player = source.getPlayers().stream()
                    .filter(value -> value.getPosition() == position).findFirst().orElseThrow();
            if (!player.requirePlayerId().equals(playerResult.playerId())) {
                throw new IllegalStateException(
                        "REAL_MATCH_PRESENTATION_PLAYER_IDENTITY_MISMATCH");
            }
            ChampionDefinition champion = champions.get(playerResult.championId());
            lineup.add(new RealMatchApiV1Dtos.PlayerPresentation(
                    playerResult.playerId().value(), player.getName(), position,
                    champion.id().value(), new RealMatchApiV1Dtos.ChampionPresentation(
                    champion.id().value(), champion.displayNameKo(), champion.displayNameEn(),
                    champion.portraitUrl())));
        }
        return new RealMatchApiV1Dtos.TeamPresentation(
                side, teamCode, source.getName(), lineup);
    }

    private static RealMatchApiV1Dtos.Draft draft(MatchEngineV1Output output) {
        MatchEngineV1Input.DraftInput source = output.finalDraft();
        List<RealMatchApiV1Dtos.FinalAssignment> assignments = output.resultSummary().players()
                .stream().sorted(Comparator
                        .comparing(MatchEngineV1Output.PlayerResultV1::teamSide)
                        .thenComparing(MatchEngineV1Output.PlayerResultV1::position))
                .map(player -> new RealMatchApiV1Dtos.FinalAssignment(
                        player.playerId().value(), player.teamSide(), player.position(),
                        player.championId().value())).toList();
        return new RealMatchApiV1Dtos.Draft(
                RealMatchApiV1Dtos.DRAFT_SCHEMA,
                source.seriesGameNumber(),
                source.draftRuleSetIdentity(),
                source.draftRuleSetHash(),
                source.draftScoringPolicyHash(),
                source.draftSelectionPolicyId(),
                source.draftSelectionPolicyHash(),
                com.lolfm.draft.DraftSelectionTraceHasher.TRACE_HASH_ALGORITHM,
                source.draftSelectionTraceHash(),
                source.selectionTraces().stream().map(trace ->
                        new RealMatchApiV1Dtos.DraftSelectionTrace(
                                trace.policyId(), trace.policyMode(), trace.policyHash(),
                                trace.selectionContextHash(), trace.turn(), trace.side(),
                                trace.actionType(), trace.bestCandidateId().value(),
                                trace.bestCanonicalScore(), trace.eligiblePool().stream()
                                .map(entry -> new RealMatchApiV1Dtos.DraftSelectionPoolEntry(
                                        entry.championId().value(), entry.canonicalRank(),
                                        entry.rawFinalSearchScore(), entry.canonicalFinalScore(),
                                        entry.canonicalScoreLoss(), entry.rankWeight())).toList(),
                                trace.selectedChampionId().value(), trace.selectedRank(),
                                trace.selectedCanonicalScoreLoss(), trace.drawBucket(),
                                trace.totalEligibleWeight(), trace.reason().name())).toList(),
                ids(source.hardFearlessExclusions()),
                source.decisions().stream().map(decision ->
                        new RealMatchApiV1Dtos.DraftDecision(
                                decision.turn(), decision.side(), decision.actionType(),
                                decision.selectedChampionId().value())).toList(),
                ids(source.blueBans()), ids(source.bluePicks()),
                ids(source.redBans()), ids(source.redPicks()),
                assignments, source.finalDraftHash(), source.finalAssignmentHash());
    }

    private static RealMatchApiV1Dtos.Result result(
            MatchEngineV1Output.MatchResultSummaryV1 source
    ) {
        return new RealMatchApiV1Dtos.Result(
                source.schemaVersion(), source.winner(), source.endReason(),
                source.durationSeconds(),
                source.teams().stream().map(team -> new RealMatchApiV1Dtos.TeamResult(
                        team.teamIdentity(), team.teamSide(), team.kills(), team.totalGold(),
                        team.dragons(), team.hasDragonSoul(), team.hasBaronBuff(),
                        team.hasElderBuff(), team.towersDestroyed(), team.inhibitorsRemaining(),
                        team.nexusTurretsRemaining(), team.nexusAlive(), team.alivePlayers()
                )).toList(),
                source.players().stream().map(player -> new RealMatchApiV1Dtos.PlayerResult(
                        player.playerId().value(), player.teamSide(), player.position(),
                        player.championId().value(), player.kills(), player.deaths(),
                        player.assists(), player.cs(), player.gold(), player.totalExperience(),
                        player.level(), abilityProfile(player.abilityProfile()))).toList(),
                source.finalDraftHash(), source.finalAssignmentHash(),
                source.runtimeProfileId(), source.configurationHash(),
                source.resourceProvenanceHash(), source.replayProvenanceHash());
    }

    private static RealMatchApiV1Dtos.PlayerAbilityProfile abilityProfile(
            MatchEngineV1Output.PlayerAbilityProfileV1 source
    ) {
        if (source == null) return null;
        return new RealMatchApiV1Dtos.PlayerAbilityProfile(
                source.schemaVersion(), source.baseRatings(), source.realizedRatings(),
                source.realizationDeltas(), source.selectedChampionProficiency(),
                source.proficiencyExecutionAdjustment());
    }

    private static RealMatchApiV1Dtos.Timeline timeline(MatchEngineV1Output.TimelineV1 source) {
        return new RealMatchApiV1Dtos.Timeline(
                source.schemaVersion(), source.durationSeconds(), source.winner(),
                source.endReason(), source.events().stream().map(
                RealMatchApiV1ResponseMapper::event).toList(),
                source.snapshots().stream().map(
                        RealMatchApiV1ResponseMapper::snapshot).toList());
    }

    private static RealMatchApiV1Dtos.Event event(MatchEngineV1Output.EventV1 source) {
        return new RealMatchApiV1Dtos.Event(
                source.timeSeconds(), source.eventType(), source.actorSide(),
                source.actorPosition(), source.lane(), nullableId(source.actorPlayerId()),
                nullableId(source.killerPlayerId()),
                nullableId(source.victimPlayerId()),
                source.assistantPlayerIds().stream().map(value -> value.value()).toList(),
                nullableChampionId(source.killerChampionId()),
                nullableChampionId(source.victimChampionId()),
                source.assistantChampionIds().stream().map(value -> value.value()).toList(),
                source.combatSource(), source.structureActionSource(), source.structureKind(),
                source.structureTowerTier(), source.structureAttackingSide(),
                source.structureDefendingSide(), source.goldAmount(),
                source.bountyRawBeforePayout(), source.actionId(), source.parentActionId(),
                source.displayMessage(),
                source.structuredData());
    }

    private static RealMatchApiV1Dtos.Snapshot snapshot(
            MatchEngineV1Output.SnapshotV1 source
    ) {
        return new RealMatchApiV1Dtos.Snapshot(
                source.timeSeconds(), teamState(source.blueTeam()), teamState(source.redTeam()),
                source.players().stream().map(
                        RealMatchApiV1ResponseMapper::playerState).toList(),
                source.structuredState());
    }

    private static RealMatchApiV1Dtos.TeamState teamState(
            MatchEngineV1Output.TeamStateV1 source
    ) {
        return new RealMatchApiV1Dtos.TeamState(
                source.teamIdentity(), source.teamSide(), source.kills(), source.gold(),
                source.dragons(), source.hasDragonSoul(), source.hasBaronBuff(),
                source.hasElderBuff(), source.elderBuffRemainingSeconds(),
                source.towersDestroyed(), source.inhibitorsRemaining(),
                source.nexusTurretsRemaining(), source.nexusAlive(), source.alivePlayers());
    }

    private static RealMatchApiV1Dtos.PlayerState playerState(
            MatchEngineV1Output.PlayerStateV1 source
    ) {
        return new RealMatchApiV1Dtos.PlayerState(
                source.playerId().value(), source.teamSide(), source.position(),
                source.championId().value(), source.kills(), source.deaths(),
                source.assists(), source.cs(), source.gold(), source.alive(),
                source.respawnAtSeconds(), source.respawnRemainingSeconds(), source.canFarm(),
                source.farmResumeAtSeconds(), source.farmReturnSecondsRemaining(),
                source.shutdownBountyGold(), source.bountyProgress(), source.activityType(),
                source.activityOriginLane(), source.activityTargetLane(),
                source.activityUntilSeconds(), source.totalExperience(), source.level(),
                source.itemProgressStage(), source.structuredProgression());
    }

    private static RealMatchApiV1Dtos.Integrity integrity(
            MatchEngineV1Output output, SimulationExecutionProvenance execution
    ) {
        SimulationRandomFingerprint random = execution.randomFingerprint();
        return new RealMatchApiV1Dtos.Integrity(
                MatchEngineV1Policy.CONTRACT_SCHEMA,
                output.productionPolicy().policyId(), output.productionPolicy().policyHash(),
                execution.runtimeProfileId().name(), execution.configurationHash(),
                execution.engineImplementationVersion(), execution.activeGameplayRulesVersion(),
                execution.draftSelectionPolicyId(), execution.draftSelectionPolicyHash(),
                com.lolfm.draft.DraftSelectionTraceHasher.TRACE_HASH_ALGORITHM,
                execution.draftSelectionTraceHash(),
                output.inputHash(), output.inputHashAlgorithm(),
                execution.resourceProvenance().resourceProvenanceHash(),
                execution.replayProvenanceHash(), execution.replayProvenanceHashAlgorithm(),
                output.simulatorTimelineHash(), execution.timelineHashAlgorithm(),
                output.structuredTimelineHash(), output.structuredTimelineHashAlgorithm(),
                output.outputHash(), output.outputHashAlgorithm(), output.outputHashScope(),
                new RealMatchApiV1Dtos.RandomFingerprint(
                        random.schemaVersion(), random.randomDrawCount(), random.randomTraceHash(),
                        random.randomTraceHashAlgorithm()),
                output.productionPolicy().diagnosticsExcludedFromGameplayIdentity());
    }

    private static RealMatchApiV1Dtos.ProductionPolicy productionPolicy() {
        MatchEngineV1Policy.Snapshot source = MatchEngineV1Policy.authoritative();
        return new RealMatchApiV1Dtos.ProductionPolicy(
                source.policyId(), source.policyHash(), source.activationDecisionSchema(),
                source.activationDecisionCode(), source.knownDiagnosticLimitation(),
                source.statisticalHoldoutApproved(), source.draftSelectionPolicyId(),
                source.draftSelectionPolicyHash(), source.retainedRuntimeProfileId().name(),
                source.configurationHash(), source.activeGameplayRulesVersion(),
                source.engineImplementationVersion(),
                source.gameplayConfiguration().championMatchupMode().name(),
                source.gameplayConfiguration().teamCompositionGameplayMode().name(),
                source.gameplayConfiguration().jungleClearContribution().name(),
                source.economyCandidateActivation(), source.tempoCandidateActivation(),
                source.diagnosticsExcludedFromGameplayIdentity());
    }

    private static List<String> ids(List<com.lolfm.champion.ChampionId> values) {
        return values.stream().map(value -> value.value()).toList();
    }

    private static String nullableId(com.lolfm.player.PlayerId value) {
        return value == null ? null : value.value();
    }

    private static String nullableChampionId(com.lolfm.champion.ChampionId value) {
        return value == null ? null : value.value();
    }
}
