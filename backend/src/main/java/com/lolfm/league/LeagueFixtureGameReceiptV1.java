package com.lolfm.league;

import com.lolfm.application.MatchEngineV1Input;
import com.lolfm.application.MatchEngineV1Output;
import com.lolfm.champion.ChampionId;
import com.lolfm.domain.Position;
import com.lolfm.draft.DraftActionType;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.player.PlayerId;
import com.lolfm.simulator.GameEndReason;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Compact, structured evidence for one attempted Production V9 game. */
public record LeagueFixtureGameReceiptV1(
        String schemaVersion,
        String matchIdentity,
        int gameNumber,
        String blueTeamCode,
        String redTeamCode,
        long gameSeed,
        String historyBeforeHash,
        String historyAfterHash,
        List<ChampionId> historyBeforePicks,
        List<ChampionId> historyAfterPicks,
        String draftRuleSetIdentity,
        String draftRuleSetHash,
        String draftScoringPolicyHash,
        String draftSelectionPolicyId,
        String draftSelectionPolicyHash,
        String draftSelectionTraceHash,
        String draftDecisionHash,
        List<DraftTurnEvidence> orderedDraftDecisions,
        List<ChampionId> blueBans,
        List<ChampionId> redBans,
        List<ChampionId> bluePicks,
        List<ChampionId> redPicks,
        String draftMetaVersion,
        String requiredLegalRoleKeyHash,
        String actualLegalRoleKeyHash,
        String finalDraftHash,
        String finalAssignmentHash,
        List<FinalAssignmentEvidence> orderedFinalAssignments,
        String rosterIdentityHash,
        String policyId,
        String policyHash,
        String runtimeProfileId,
        String configurationHash,
        String engineImplementationVersion,
        String activeGameplayRulesVersion,
        String resourceProvenanceHash,
        String inputHash,
        String replayProvenanceHash,
        String replayProvenanceHashAlgorithm,
        String simulatorTimelineHash,
        String structuredTimelineHash,
        String structuredTimelineHashAlgorithm,
        String outputHash,
        String outputHashAlgorithm,
        String outputHashScope,
        String randomFingerprintSchema,
        long randomDrawCount,
        String randomTraceHash,
        String randomTraceHashAlgorithm,
        TeamSide winnerSide,
        String winnerTeamCode,
        int durationSeconds,
        GameEndReason endReason
) {
    public static final String SCHEMA = "AI_LEAGUE_FIXTURE_GAME_RECEIPT_V1";

    public LeagueFixtureGameReceiptV1 {
        if (!SCHEMA.equals(required(schemaVersion, "schemaVersion"))) {
            throw new IllegalArgumentException("Unsupported League game receipt schema");
        }
        matchIdentity = required(matchIdentity, "matchIdentity");
        if (gameNumber < 1) throw new IllegalArgumentException("gameNumber");
        LeagueIdentity.requireTeamCode(blueTeamCode);
        LeagueIdentity.requireTeamCode(redTeamCode);
        if (blueTeamCode.equals(redTeamCode)) throw new IllegalArgumentException("game teams");
        hash(historyBeforeHash, "historyBeforeHash");
        hash(historyAfterHash, "historyAfterHash");
        historyBeforePicks = canonicalChampions(historyBeforePicks, "historyBeforePicks");
        historyAfterPicks = canonicalChampions(historyAfterPicks, "historyAfterPicks");
        if (!historyBeforeHash.equals(SeriesDraftHistory.identityHash(
                gameNumber - 1, Set.copyOf(historyBeforePicks)))
                || !historyAfterHash.equals(SeriesDraftHistory.identityHash(
                gameNumber, Set.copyOf(historyAfterPicks)))) {
            throw new IllegalArgumentException("Hard Fearless history hash mismatch");
        }
        draftRuleSetIdentity = required(draftRuleSetIdentity, "draftRuleSetIdentity");
        hash(draftRuleSetHash, "draftRuleSetHash");
        hash(draftScoringPolicyHash, "draftScoringPolicyHash");
        draftSelectionPolicyId = required(draftSelectionPolicyId, "draftSelectionPolicyId");
        hash(draftSelectionPolicyHash, "draftSelectionPolicyHash");
        hash(draftSelectionTraceHash, "draftSelectionTraceHash");
        hash(draftDecisionHash, "draftDecisionHash");
        orderedDraftDecisions = List.copyOf(orderedDraftDecisions);
        if (orderedDraftDecisions.size() != 20) {
            throw new IllegalArgumentException("Draft decision cardinality");
        }
        for (int index = 0; index < orderedDraftDecisions.size(); index++) {
            if (orderedDraftDecisions.get(index).turn() != index + 1) {
                throw new IllegalArgumentException("Draft decision ordering");
            }
        }
        StringBuilder decisionCanonical = new StringBuilder();
        orderedDraftDecisions.forEach(value -> decisionCanonical
                .append(value.turn()).append(':').append(value.side()).append(':')
                .append(value.actionType()).append(':')
                .append(value.championId().value()).append('\n'));
        if (!draftDecisionHash.equals(LeagueIdentity.sha256(
                decisionCanonical.toString()))) {
            throw new IllegalArgumentException("Draft decision hash mismatch");
        }
        blueBans = List.copyOf(blueBans);
        redBans = List.copyOf(redBans);
        bluePicks = List.copyOf(bluePicks);
        redPicks = List.copyOf(redPicks);
        if (blueBans.size() != Position.values().length
                || redBans.size() != Position.values().length
                || bluePicks.size() != Position.values().length
                || redPicks.size() != Position.values().length) {
            throw new IllegalArgumentException("Draft champion cardinality");
        }
        if (!selected(orderedDraftDecisions, TeamSide.BLUE, DraftActionType.BAN)
                .equals(blueBans)
                || !selected(orderedDraftDecisions, TeamSide.RED, DraftActionType.BAN)
                .equals(redBans)
                || !selected(orderedDraftDecisions, TeamSide.BLUE, DraftActionType.PICK)
                .equals(bluePicks)
                || !selected(orderedDraftDecisions, TeamSide.RED, DraftActionType.PICK)
                .equals(redPicks)) {
            throw new IllegalArgumentException("Ordered Draft decision/list mismatch");
        }
        HashSet<ChampionId> currentPicks = new HashSet<>(bluePicks);
        currentPicks.addAll(redPicks);
        if (currentPicks.size() != 10) throw new IllegalArgumentException("Duplicate final pick");
        HashSet<ChampionId> expectedAfter = new HashSet<>(historyBeforePicks);
        expectedAfter.addAll(currentPicks);
        if (expectedAfter.size() != historyBeforePicks.size() + 10
                || !expectedAfter.equals(Set.copyOf(historyAfterPicks))) {
            throw new IllegalArgumentException("Hard Fearless transition mismatch");
        }
        draftMetaVersion = required(draftMetaVersion, "draftMetaVersion");
        hash(requiredLegalRoleKeyHash, "requiredLegalRoleKeyHash");
        hash(actualLegalRoleKeyHash, "actualLegalRoleKeyHash");
        hash(finalDraftHash, "finalDraftHash");
        hash(finalAssignmentHash, "finalAssignmentHash");
        orderedFinalAssignments = List.copyOf(orderedFinalAssignments);
        validateAssignments(orderedFinalAssignments, currentPicks);
        hash(rosterIdentityHash, "rosterIdentityHash");
        policyId = required(policyId, "policyId");
        hash(policyHash, "policyHash");
        runtimeProfileId = required(runtimeProfileId, "runtimeProfileId");
        hash(configurationHash, "configurationHash");
        engineImplementationVersion = required(
                engineImplementationVersion, "engineImplementationVersion");
        activeGameplayRulesVersion = required(
                activeGameplayRulesVersion, "activeGameplayRulesVersion");
        hash(resourceProvenanceHash, "resourceProvenanceHash");
        hash(inputHash, "inputHash");
        hash(replayProvenanceHash, "replayProvenanceHash");
        replayProvenanceHashAlgorithm = required(
                replayProvenanceHashAlgorithm, "replayProvenanceHashAlgorithm");
        hash(simulatorTimelineHash, "simulatorTimelineHash");
        hash(structuredTimelineHash, "structuredTimelineHash");
        structuredTimelineHashAlgorithm = required(
                structuredTimelineHashAlgorithm, "structuredTimelineHashAlgorithm");
        hash(outputHash, "outputHash");
        outputHashAlgorithm = required(outputHashAlgorithm, "outputHashAlgorithm");
        outputHashScope = required(outputHashScope, "outputHashScope");
        randomFingerprintSchema = required(
                randomFingerprintSchema, "randomFingerprintSchema");
        if (randomDrawCount < 0 || durationSeconds < 0) {
            throw new IllegalArgumentException("Invalid game receipt number");
        }
        hash(randomTraceHash, "randomTraceHash");
        randomTraceHashAlgorithm = required(
                randomTraceHashAlgorithm, "randomTraceHashAlgorithm");
        Objects.requireNonNull(endReason, "endReason");
        String expectedWinner = winnerSide == null ? null
                : winnerSide == TeamSide.BLUE ? blueTeamCode : redTeamCode;
        if (!Objects.equals(expectedWinner, winnerTeamCode)) {
            throw new IllegalArgumentException("Winner side/team mismatch");
        }
        if (endReason == GameEndReason.NEXUS_DESTROYED && winnerSide == null
                || endReason == GameEndReason.SIMULATION_TIMEOUT && winnerSide != null) {
            throw new IllegalArgumentException("Winner/end reason mismatch");
        }
    }

    public String canonicalText() {
        StringBuilder value = new StringBuilder();
        append(value, "schemaVersion", schemaVersion);
        append(value, "matchIdentity", matchIdentity);
        append(value, "gameNumber", gameNumber);
        append(value, "blueTeamCode", blueTeamCode);
        append(value, "redTeamCode", redTeamCode);
        append(value, "gameSeed", gameSeed);
        append(value, "historyBeforeHash", historyBeforeHash);
        append(value, "historyAfterHash", historyAfterHash);
        historyBeforePicks.forEach(champion -> append(value, "historyBeforePick", champion.value()));
        historyAfterPicks.forEach(champion -> append(value, "historyAfterPick", champion.value()));
        append(value, "draftRuleSetIdentity", draftRuleSetIdentity);
        append(value, "draftRuleSetHash", draftRuleSetHash);
        append(value, "draftScoringPolicyHash", draftScoringPolicyHash);
        append(value, "draftSelectionPolicyId", draftSelectionPolicyId);
        append(value, "draftSelectionPolicyHash", draftSelectionPolicyHash);
        append(value, "draftSelectionTraceHash", draftSelectionTraceHash);
        append(value, "draftDecisionHash", draftDecisionHash);
        orderedDraftDecisions.forEach(decision -> append(value, "draftDecision",
                decision.turn() + "|" + decision.side() + "|" + decision.actionType()
                        + "|" + decision.championId().value()));
        appendChampions(value, "blueBan", blueBans);
        appendChampions(value, "redBan", redBans);
        appendChampions(value, "bluePick", bluePicks);
        appendChampions(value, "redPick", redPicks);
        append(value, "draftMetaVersion", draftMetaVersion);
        append(value, "requiredLegalRoleKeyHash", requiredLegalRoleKeyHash);
        append(value, "actualLegalRoleKeyHash", actualLegalRoleKeyHash);
        append(value, "finalDraftHash", finalDraftHash);
        append(value, "finalAssignmentHash", finalAssignmentHash);
        orderedFinalAssignments.forEach(assignment -> append(value, "finalAssignment",
                assignment.teamSide() + "|" + assignment.position() + "|"
                        + assignment.playerId().value() + "|"
                        + assignment.championId().value()));
        append(value, "rosterIdentityHash", rosterIdentityHash);
        append(value, "policyId", policyId);
        append(value, "policyHash", policyHash);
        append(value, "runtimeProfileId", runtimeProfileId);
        append(value, "configurationHash", configurationHash);
        append(value, "engineImplementationVersion", engineImplementationVersion);
        append(value, "activeGameplayRulesVersion", activeGameplayRulesVersion);
        append(value, "resourceProvenanceHash", resourceProvenanceHash);
        append(value, "inputHash", inputHash);
        append(value, "replayProvenanceHash", replayProvenanceHash);
        append(value, "replayProvenanceHashAlgorithm", replayProvenanceHashAlgorithm);
        append(value, "simulatorTimelineHash", simulatorTimelineHash);
        append(value, "structuredTimelineHash", structuredTimelineHash);
        append(value, "structuredTimelineHashAlgorithm", structuredTimelineHashAlgorithm);
        append(value, "outputHash", outputHash);
        append(value, "outputHashAlgorithm", outputHashAlgorithm);
        append(value, "outputHashScope", outputHashScope);
        append(value, "randomFingerprintSchema", randomFingerprintSchema);
        append(value, "randomDrawCount", randomDrawCount);
        append(value, "randomTraceHash", randomTraceHash);
        append(value, "randomTraceHashAlgorithm", randomTraceHashAlgorithm);
        append(value, "winnerSide", winnerSide == null ? "NONE" : winnerSide);
        append(value, "winnerTeamCode", winnerTeamCode == null ? "NONE" : winnerTeamCode);
        append(value, "durationSeconds", durationSeconds);
        append(value, "endReason", endReason);
        return value.toString();
    }

    public static LeagueFixtureGameReceiptV1 from(
            MatchEngineV1Input input,
            MatchEngineV1Output output,
            List<ChampionId> historyAfter
    ) {
        MatchEngineV1Input.DraftInput draft = output.finalDraft();
        var execution = output.executionProvenance();
        var random = execution.randomFingerprint();
        List<DraftTurnEvidence> decisions = draft.decisions().stream()
                .map(value -> new DraftTurnEvidence(value.turn(), value.side(),
                        value.actionType(), value.selectedChampionId())).toList();
        List<FinalAssignmentEvidence> assignments = input.championAssignments().stream()
                .sorted(Comparator.comparing((MatchEngineV1Input.ChampionAssignmentInput value)
                                -> value.teamSide().ordinal())
                        .thenComparing(value -> value.position().ordinal()))
                .map(value -> new FinalAssignmentEvidence(value.teamSide(), value.position(),
                        value.playerId(), value.championId())).toList();
        TeamSide winner = output.resultSummary().winner();
        return new LeagueFixtureGameReceiptV1(
                SCHEMA, output.matchIdentity(), draft.seriesGameNumber(),
                input.blueTeam().teamIdentity(), input.redTeam().teamIdentity(),
                input.matchSeed(), input.seriesHistoryBeforeHash(),
                SeriesDraftHistory.identityHash(draft.seriesGameNumber(), Set.copyOf(historyAfter)),
                draft.hardFearlessExclusions(), historyAfter,
                draft.draftRuleSetIdentity(), draft.draftRuleSetHash(),
                draft.draftScoringPolicyHash(), draft.draftSelectionPolicyId(),
                draft.draftSelectionPolicyHash(), draft.draftSelectionTraceHash(),
                draft.draftDecisionHash(), decisions, draft.blueBans(), draft.redBans(),
                draft.bluePicks(), draft.redPicks(), draft.draftMetaVersion(),
                draft.requiredLegalRoleKeyHash(), draft.actualLegalRoleKeyHash(),
                draft.finalDraftHash(), draft.finalAssignmentHash(), assignments,
                input.rosterIdentityHash(), output.productionPolicy().policyId(),
                output.productionPolicy().policyHash(), execution.runtimeProfileId().name(),
                output.configurationHash(), execution.engineImplementationVersion(),
                execution.activeGameplayRulesVersion(),
                execution.resourceProvenance().resourceProvenanceHash(), output.inputHash(),
                execution.replayProvenanceHash(), execution.replayProvenanceHashAlgorithm(),
                output.simulatorTimelineHash(), output.structuredTimelineHash(),
                output.structuredTimelineHashAlgorithm(), output.outputHash(),
                output.outputHashAlgorithm(), output.outputHashScope(), random.schemaVersion(),
                random.randomDrawCount(), random.randomTraceHash(),
                random.randomTraceHashAlgorithm(), winner,
                winner == null ? null : winner == TeamSide.BLUE
                        ? input.blueTeam().teamIdentity() : input.redTeam().teamIdentity(),
                output.resultSummary().durationSeconds(), output.resultSummary().endReason());
    }

    public record DraftTurnEvidence(
            int turn, TeamSide side, DraftActionType actionType, ChampionId championId
    ) {
        public DraftTurnEvidence {
            if (turn < 1) throw new IllegalArgumentException("turn");
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(actionType, "actionType");
            Objects.requireNonNull(championId, "championId");
        }
    }

    public record FinalAssignmentEvidence(
            TeamSide teamSide, Position position, PlayerId playerId, ChampionId championId
    ) {
        public FinalAssignmentEvidence {
            Objects.requireNonNull(teamSide, "teamSide");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(championId, "championId");
        }
    }

    private static void validateAssignments(
            List<FinalAssignmentEvidence> values,
            Set<ChampionId> currentPicks
    ) {
        if (values.size() != 10) throw new IllegalArgumentException("Assignment cardinality");
        HashSet<String> slots = new HashSet<>();
        HashSet<PlayerId> players = new HashSet<>();
        values.forEach(value -> {
            if (!slots.add(value.teamSide() + ":" + value.position())
                    || !players.add(value.playerId())) {
                throw new IllegalArgumentException("Assignment identity duplicate");
            }
        });
        if (slots.size() != EnumSet.allOf(TeamSide.class).size()
                * EnumSet.allOf(Position.class).size()) {
            throw new IllegalArgumentException("Assignment slot coverage");
        }
        if (!values.stream().map(FinalAssignmentEvidence::championId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet())
                .equals(currentPicks)) {
            throw new IllegalArgumentException("Assignment/pick mismatch");
        }
    }

    private static List<ChampionId> selected(
            List<DraftTurnEvidence> decisions,
            TeamSide side,
            DraftActionType actionType
    ) {
        return decisions.stream().filter(value -> value.side() == side
                        && value.actionType() == actionType)
                .map(DraftTurnEvidence::championId).toList();
    }

    private static List<ChampionId> canonicalChampions(List<ChampionId> values, String field) {
        Objects.requireNonNull(values, field);
        ArrayList<ChampionId> result = new ArrayList<>(values);
        result.sort(Comparator.comparing(ChampionId::value));
        if (new HashSet<>(result).size() != result.size()) {
            throw new IllegalArgumentException("Duplicate " + field);
        }
        return List.copyOf(result);
    }

    private static void appendChampions(
            StringBuilder value, String field, List<ChampionId> champions
    ) {
        for (int index = 0; index < champions.size(); index++) {
            append(value, field, index + "|" + champions.get(index).value());
        }
    }

    private static void append(StringBuilder target, String field, Object value) {
        target.append(field).append('=').append(value).append('\n');
    }

    private static String required(String value, String field) {
        String result = Objects.requireNonNull(value, field).trim();
        if (result.isEmpty() || result.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(field);
        }
        return result;
    }

    private static void hash(String value, String field) {
        LeagueSeasonFrozenSnapshot.requireSha256(value, field);
    }
}
