package com.lolfm.career;
import com.lolfm.application.MatchEngineV1Output;
import com.lolfm.simulator.TeamSide;
import com.lolfm.domain.Position;
import java.util.*;
import static com.lolfm.career.CareerRosterStore.*;

/** Read-only projection after output verification. No engine reference, Random or mutable shared state. */
public record CareerTeamPlayStatistics(String outputHash,List<Action> actions) {
    public record Action(String id,String kind,TeamSide side,TeamSide winner,List<String> participants) {
        public Action { participants=List.copyOf(participants); }
    }
    public record Observation(int objectiveOpportunities,int objectiveParticipations,int objectiveWins,int roamAttempts,int roamWins) {}
    public CareerTeamPlayStatistics { actions=List.copyOf(actions); }
    public static CareerTeamPlayStatistics collect(MatchEngineV1Output output) {
        return collect(CareerGameStatistics.from(1,output.outputHash(),output.resultSummary()),output.timeline().events());
    }
    static CareerTeamPlayStatistics collect(CareerGameStatistics game,List<MatchEngineV1Output.EventV1> events) {
        var actions=new TreeMap<String,Action>();
        for(var event:events) {
            String id=event.actionId();if(id==null||id.isBlank())continue;
            if(!Set.of("TEAMFIGHT_RESULT","ROAM").contains(event.eventType().name()))continue;
            var data=read(write(event.structuredData()),com.fasterxml.jackson.databind.JsonNode.class);
            Action action=null;
            if(event.eventType().name().equals("TEAMFIGHT_RESULT")&&data.has("objectiveFight")) {
                var d=data.get("objectiveFight");var ids=new ArrayList<String>();d.path("participantPlayerIds").forEach(v->ids.add(v.asText()));
                action=new Action(id,"OBJECTIVE_FIGHT",null,TeamSide.valueOf(d.path("winningSide").asText()),ids);
            }else if(event.eventType().name().equals("ROAM")&&data.has("roam")) {
                var d=data.get("roam");String outcome=d.path("outcome").asText();
                if(!Set.of("NO_KILL","ROAMING_SIDE_KILL","DEFENDING_SIDE_KILL").contains(outcome))continue;
                action=new Action(id,"ROAM",TeamSide.valueOf(d.path("roamingSide").asText()),d.path("winningSide").isTextual()?TeamSide.valueOf(d.path("winningSide").asText()):null,List.of(d.path("roamerPlayerId").asText()));
            }
            if(action!=null){var old=actions.putIfAbsent(id,action);if(old!=null&&!old.equals(action))throw new IllegalStateException("TEAM_PLAY_ACTION_CONFLICT");}
        }
        var result=new CareerTeamPlayStatistics(game.outputHash(),List.copyOf(actions.values()));
        result.validate(game);return result;
    }
    void validate(CareerGameStatistics game) {
        if(!Objects.equals(outputHash,game.outputHash()))throw new IllegalStateException("TEAM_PLAY_OUTPUT_MISMATCH");
        var players=new HashMap<String,CareerGameStatistics.Player>();game.players().forEach(p->players.put(p.playerId(),p));
        var ids=new HashSet<String>();
        for(var a:actions) {
            if(a.id()==null||a.id().isBlank()||!ids.add(a.id())||a.participants().isEmpty()||new HashSet<>(a.participants()).size()!=a.participants().size()||a.participants().stream().anyMatch(p->!players.containsKey(p)))throw new IllegalStateException("TEAM_PLAY_PARTICIPANT_MISMATCH");
            if(a.kind().equals("ROAM")) {
                if(a.side()==null||a.participants().size()!=1||players.get(a.participants().getFirst()).side()!=a.side())throw new IllegalStateException("TEAM_PLAY_ROAM_MISMATCH");
            }else if(!a.kind().equals("OBJECTIVE_FIGHT")||a.winner()==null||a.side()!=null)throw new IllegalStateException("TEAM_PLAY_ACTION_INVALID");
        }
    }
    public Observation observation(CareerGameStatistics.Player player) {
        int opportunities=0,participations=0,wins=0,roams=0,roamWins=0;
        for(var a:actions)if(a.kind().equals("OBJECTIVE_FIGHT")) {
            opportunities++;if(a.participants().contains(player.playerId())){participations++;if(a.winner()==player.side())wins++;}
        }else if(a.participants().contains(player.playerId())){roams++;if(a.winner()==player.side())roamWins++;}
        return new Observation(opportunities,participations,wins,roams,roamWins);
    }
}
