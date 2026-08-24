package com.lolfm.simulator;

import com.lolfm.domain.CombatSource;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.ObjectiveFightSkillImpactData;
import com.lolfm.domain.ObjectiveFightEventData;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Stateless adapter around the existing teamfight score and common kill/reward path. */
public final class ObjectiveFightResolver {
    private final TeamfightResolver teamfights = new TeamfightResolver();
    private final PlayerSkillEvaluator playerSkills = new PlayerSkillEvaluator();

    public ObjectiveFightOutcome resolve(GameState state, Random random, List<MatchEvent> events) {
        return resolve(state, random, events,
                "OBJECTIVE_FIGHT:UNSCOPED:" + state.getCurrentTimeSeconds());
    }

    public ObjectiveFightOutcome resolve(
            GameState state, Random random, List<MatchEvent> events, String actionId) {
        java.util.Objects.requireNonNull(actionId, "actionId");
        int eventStart = events.size();
        Team blue = domainTeam(state.getBlueTeamState());
        Team red = domainTeam(state.getRedTeamState());
        ObjectiveFightSkillImpactData skillImpact = objectiveSkillImpact(state);
        double goldContribution=(state.getBlueTeamState().getGold()-state.getRedTeamState().getGold())/500.0;
        double existing=goldContribution+(state.getBlueTeamState().getKills()-state.getRedTeamState().getKills())*11.0+teamfights.teamfightScore(state,TeamSide.BLUE,blue)-teamfights.teamfightScore(state,TeamSide.RED,red)+skillImpact.setupEdgeContribution();
        double scoreWithoutNoise=existing+new CombatProgressionEvaluator().contribution(state,ProgressionCombatContext.OBJECTIVE_FIGHT,alive(state.getBlueTeamState(),state.getCurrentTimeSeconds()),alive(state.getRedTeamState(),state.getCurrentTimeSeconds()),existing,goldContribution);
        double advantage=new CombatOutcomeProbabilityEvaluator().resolveUniformAdvantageScore(scoreWithoutNoise,random);
        TeamSide winner = advantage >= 0 ? TeamSide.BLUE : TeamSide.RED;
        Team winningTeam = winner == TeamSide.BLUE ? blue : red;
        Team losingTeam = winner == TeamSide.BLUE ? red : blue;
        TeamState winningState = state.getTeamState(winner);
        TeamState losingState = state.getTeamState(winner.opposite());
        List<PlayerState> blueParticipants=participants(state.getBlueTeamState(),state.getCurrentTimeSeconds());
        List<PlayerState> redParticipants=participants(state.getRedTeamState(),state.getCurrentTimeSeconds());
        List<String> participants = new ArrayList<>();
        markParticipants(state, TeamSide.BLUE, state.getBlueTeamState(), participants);
        markParticipants(state, TeamSide.RED, state.getRedTeamState(), participants);
        MatchEvent startEvent = new MatchEvent(state.getCurrentTimeSeconds(), MatchEventType.TEAMFIGHT,
                "오브젝트 지역에서 양 팀이 교전을 시작합니다.", null, null, List.of());
        events.add(startEvent);
        Set<PlayerState> dead = new HashSet<>();
        boolean killed = teamfights.resolveKill(state.getCurrentTimeSeconds(), random, winningTeam, winningState,
                losingTeam, losingState, events, true, dead);
        teamfights.commitPendingCombatProgress(state.getBlueTeamState());
        teamfights.commitPendingCombatProgress(state.getRedTeamState());
        MatchEvent resultEvent = new MatchEvent(state.getCurrentTimeSeconds(), MatchEventType.TEAMFIGHT_RESULT,
                winningState.getTeamName() + "가 오브젝트 교전에서 승리합니다.", null, null, List.of());
        ObjectiveFightEventData eventData = new ObjectiveFightEventData(
                winner, participants, killed ? 1 : 0, skillImpact);
        startEvent.setObjectiveFight(eventData);
        resultEvent.setObjectiveFight(eventData);
        events.add(resultEvent);
        for (int index = eventStart; index < events.size(); index++) {
            events.get(index).setCombatSource(CombatSource.OBJECTIVE_FIGHT);
            events.get(index).setActionId(actionId);
        }
        state.getCombatOutcomeExecutionStats().record(ProgressionCombatContext.OBJECTIVE_FIGHT,state.getCurrentTimeSeconds(),true,winner,blueParticipants,redParticipants);
        return new ObjectiveFightOutcome(winner, killed ? 1 : 0, participants, skillImpact);
    }

    ObjectiveFightSkillImpactData objectiveSkillImpact(GameState state) {
        SupportSkill blue = supportSkill(state, TeamSide.BLUE);
        SupportSkill red = supportSkill(state, TeamSide.RED);
        return new ObjectiveFightSkillImpactData(blue.areaSetup(), red.areaSetup(),
                blue.visionControl(), red.visionControl(), blue.contribution(),
                red.contribution(), blue.contribution() - red.contribution());
    }

    private SupportSkill supportSkill(GameState state, TeamSide side) {
        PlayerState support = state.getTeamState(side).playerAt(Position.SUPPORT);
        int time = state.getCurrentTimeSeconds();
        if (!support.hasMatchPerformance() || !support.isAlive(time)
                || !support.canParticipateInMajorCombatAt(time)) {
            return new SupportSkill(ObjectivePlayerSkillRuleConfig.BASELINE_SKILL,
                    ObjectivePlayerSkillRuleConfig.BASELINE_SKILL, 0);
        }
        double area = playerSkills.areaSetup(support);
        double vision = playerSkills.visionControl(support);
        double contribution = (area - ObjectivePlayerSkillRuleConfig.BASELINE_SKILL)
                * ObjectivePlayerSkillRuleConfig.AREA_SETUP_FIGHT_SCORE_PER_POINT
                + (vision - ObjectivePlayerSkillRuleConfig.BASELINE_SKILL)
                * ObjectivePlayerSkillRuleConfig.VISION_CONTROL_FIGHT_SCORE_PER_POINT;
        return new SupportSkill(area, vision, contribution);
    }

    private List<PlayerState> alive(TeamState team,int time){return team.getPlayers().stream().filter(p->p.canParticipateInMajorCombatAt(time)).toList();}
    private List<PlayerState> participants(TeamState team,int time){return team.getPlayers().stream().filter(p->p.canParticipateInMajorCombatAt(time)).toList();}

    private void markParticipants(
            GameState state, TeamSide side, TeamState team, List<String> participants) {
        int time = state.getCurrentTimeSeconds();
        for (PlayerState player : team.getPlayers()) {
            if (player.canParticipateInMajorCombatAt(time)) {
                state.markMajorCombatParticipant(player);
                participants.add(structuredPlayerId(side, player));
            }
        }
    }

    private String structuredPlayerId(TeamSide side, PlayerState player) {
        if (player.hasStablePlayerId()) return player.requirePlayerId().value();
        if (player.getPlayerKey() != null) return player.getPlayerKey().stableId();
        return "LEGACY:" + side + ":" + player.getPosition();
    }

    private Team domainTeam(TeamState state) {
        List<Player> players = new ArrayList<>();
        for (PlayerState player : state.getPlayers()) {
            players.add(new Player(player.getPlayerName(), player.getPosition(), new PlayerAttributes(
                    player.getMechanics(), player.getAggression(), player.getFarming(), player.getTeamfighting())));
        }
        return new Team(state.getTeamName(), players);
    }

    private record SupportSkill(double areaSetup, double visionControl, double contribution) { }
}
