package com.lolfm.application;

import com.lolfm.champion.ChampionAssignment;
import com.lolfm.domain.Player;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.draft.FinalDraftResult;
import com.lolfm.player.PlayerId;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.TeamSide;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Controlled adapter from the Draft-owned final result into an immutable V1 match input. */
@Component
public final class MatchEngineV1InputFactory {
    private final SimulationProvenanceService provenance;

    public MatchEngineV1InputFactory(SimulationProvenanceService provenance) {
        this.provenance = Objects.requireNonNull(provenance, "provenance");
    }

    public MatchEngineV1Input fromRealDraft(
            String blueTeamCode,
            Team blueTeam,
            String redTeamCode,
            Team redTeam,
            long matchSeed,
            int seriesGameNumber,
            Set<com.lolfm.champion.ChampionId> seriesExclusionsBeforeDraft,
            FinalDraftResult draftResult
    ) {
        String matchIdentity = "REAL_DRAFT:" + blueTeamCode + ":" + redTeamCode
                + ":GAME:" + seriesGameNumber + ":SEED:" + matchSeed;
        return fromRealDraft(matchIdentity, blueTeamCode, blueTeam, redTeamCode, redTeam,
                matchSeed, seriesGameNumber, seriesExclusionsBeforeDraft, draftResult);
    }

    public MatchEngineV1Input fromRealDraft(
            String matchIdentity,
            String blueTeamCode,
            Team blueTeam,
            String redTeamCode,
            Team redTeam,
            long matchSeed,
            int seriesGameNumber,
            Set<com.lolfm.champion.ChampionId> seriesExclusionsBeforeDraft,
            FinalDraftResult draftResult
    ) {
        Objects.requireNonNull(draftResult, "draftResult");
        MatchEngineV1Input.TeamInput blue = team(
                TeamSide.BLUE, blueTeamCode, blueTeam);
        MatchEngineV1Input.TeamInput red = team(
                TeamSide.RED, redTeamCode, redTeam);
        List<MatchEngineV1Input.ChampionAssignmentInput> assignments =
                draftResult.matchChampionAssignments().asMap().values().stream()
                        .sorted(Comparator.comparing(value -> value.playerKey().stableId()))
                        .map(value -> assignment(value, blue, red)).toList();
        String finalAssignmentHash = SimulationProvenanceService.finalAssignmentHash(draftResult);
        String finalDraftHash = SimulationProvenanceService.finalDraftHash(
                draftResult, finalAssignmentHash);
        MatchEngineV1Input.DraftInput draft = new MatchEngineV1Input.DraftInput(
                seriesGameNumber,
                draftResult.ruleSet().identity(),
                provenance.draftRuleSetHash(),
                provenance.draftScoringPolicyHash(),
                draftResult.decisions().stream().map(value ->
                        new MatchEngineV1Input.DraftDecisionInput(
                                value.turn(), value.side(), value.actionType(),
                                value.selectedChampionId())).toList(),
                draftResult.draftIdentity(),
                draftResult.blueBans(), draftResult.redBans(),
                draftResult.bluePicks(), draftResult.redPicks(),
                seriesExclusionsBeforeDraft.stream()
                        .sorted(Comparator.comparing(com.lolfm.champion.ChampionId::value))
                        .toList(),
                draftResult.draftMetaVersion(),
                draftResult.requiredLegalRoleKeyHash(),
                draftResult.actualLegalRoleKeyHash(),
                finalAssignmentHash, finalDraftHash);
        return new MatchEngineV1Input(
                MatchEngineV1Input.SCHEMA, matchIdentity, blue, red, assignments, draft,
                matchSeed,
                SimulationProvenanceService.rosterIdentityHash(
                        blueTeamCode, blueTeam, redTeamCode, redTeam),
                SimulationProvenanceService.seriesHistoryHash(
                        seriesGameNumber - 1, seriesExclusionsBeforeDraft),
                MatchEngineV1Policy.requirement());
    }

    private static MatchEngineV1Input.TeamInput team(
            TeamSide side, String teamCode, Team team
    ) {
        Objects.requireNonNull(team, "team");
        List<MatchEngineV1Input.PlayerInput> lineup = team.getPlayers().stream()
                .sorted(Comparator.comparing(Player::getPosition))
                .map(player -> player(side, player)).toList();
        return new MatchEngineV1Input.TeamInput(
                teamCode, team.getName(), side, lineup);
    }

    private static MatchEngineV1Input.PlayerInput player(TeamSide side, Player player) {
        return new MatchEngineV1Input.PlayerInput(
                player.requirePlayerId(), player.getName(), side, player.getPosition(),
                player.getRatings().asMap(), player.getChampionProficiencies().asMap());
    }

    private static MatchEngineV1Input.ChampionAssignmentInput assignment(
            ChampionAssignment assignment,
            MatchEngineV1Input.TeamInput blue,
            MatchEngineV1Input.TeamInput red
    ) {
        PlayerKey key = assignment.playerKey();
        MatchEngineV1Input.TeamInput team = key.side() == TeamSide.BLUE ? blue : red;
        PlayerId playerId = team.lineup().stream()
                .filter(value -> value.position() == key.position())
                .map(MatchEngineV1Input.PlayerInput::playerId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Missing Draft assignment player " + key.stableId()));
        return new MatchEngineV1Input.ChampionAssignmentInput(
                playerId, key.side(), key.position(), assignment.championId());
    }
}
