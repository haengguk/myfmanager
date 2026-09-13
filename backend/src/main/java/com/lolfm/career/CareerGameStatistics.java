package com.lolfm.career;

import com.lolfm.application.MatchEngineV1Output;
import com.lolfm.domain.Position;
import com.lolfm.simulator.TeamSide;
import java.util.List;

/** Adjunct evidence. Never participates in the historical Match/receipt canonical identity. */
public record CareerGameStatistics(int gameNumber,String outputHash,int seconds,String winner,String endReason,List<Player> players, @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) CareerTeamPlayStatistics teamPlay) {
    public CareerGameStatistics(int number,String hash,int seconds,String winner,String end,List<Player> players){this(number,hash,seconds,winner,end,players,null);}
    public CareerGameStatistics base(){return new CareerGameStatistics(gameNumber,outputHash,seconds,winner,endReason,players);}
    public CareerGameStatistics withTeamPlay(CareerTeamPlayStatistics value){return new CareerGameStatistics(gameNumber,outputHash,seconds,winner,endReason,players,value);}
    public static CareerGameStatistics from(int number,MatchEngineV1Output output){return from(number,output.outputHash(),output.resultSummary()).withTeamPlay(CareerTeamPlayStatistics.collect(output));}
    public record Player(String playerId,TeamSide side,Position position,String championId,String team,
                         int kills,int deaths,int assists,int cs,int gold,int experience,int level) {}
    public CareerGameStatistics { players=List.copyOf(players); }
    public static CareerGameStatistics from(int number,String hash,MatchEngineV1Output.MatchResultSummaryV1 summary) {
        var teams=summary.teams().stream().collect(java.util.stream.Collectors.toMap(MatchEngineV1Output.TeamResultV1::teamSide,MatchEngineV1Output.TeamResultV1::teamIdentity));
        return new CareerGameStatistics(number,hash,summary.durationSeconds(),teams.get(summary.winner()),summary.endReason().name(),summary.players().stream().map(p->new Player(p.playerId().value(),p.teamSide(),p.position(),p.championId().value(),teams.get(p.teamSide()),p.kills(),p.deaths(),p.assists(),p.cs(),p.gold(),p.totalExperience(),p.level())).toList());
    }
}
