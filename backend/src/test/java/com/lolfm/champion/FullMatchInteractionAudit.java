package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.*;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.PlayerSnapshot;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class FullMatchInteractionAudit {
    private static final ChampionCatalog CATALOG = new ChampionCatalog(new ObjectMapper());
    private static final ChampionSelectionValidator SELECTOR = new ChampionSelectionValidator(CATALOG);
    private FullMatchInteractionAudit() { }
    public static void run(Path output) throws Exception {
        StringBuilder csv = new StringBuilder("lineupId,targetPosition,skillProfile,mode,direction,seed,blueWinner,winner,durationSeconds,endReason,blueKills,redKills,blueGold,redGold,blueObjectives,redObjectives,blueStructures,redStructures,targetEarlyChampionSide,targetScalingChampionSide,earlyChampionKda,scalingChampionKda,earlyChampionGold,scalingChampionGold,earlyChampionLevel,scalingChampionLevel,earlyChampionItemStage,scalingChampionItemStage,earlyLaneCombatWins,scalingLaneCombatWins,blueTeamfightWins,redTeamfightWins,blueObjectiveFightWins,redObjectiveFightWins,championPowerApplications,championPowerDirectRandomCalls,replayMismatch,diagnosticsMismatch,duplicateOutcomeRecordErrors,outcomeWithoutAttemptErrors,outcomeWithoutWinnerErrors,participantMismatchErrors,blueLaneCombatOutcomeRecords,redLaneCombatOutcomeRecords\n");
        for (Lineup l : lineups()) for (String skill : List.of("S0","S1","S3","S5")) for (boolean on : List.of(false,true)) for (boolean mirror : List.of(false,true)) for (int seed=1;seed<=200;seed++) row(csv,l,skill,on,mirror,seed);
        Files.writeString(output, csv);
    }
    private static void row(StringBuilder csv, Lineup l,String skill,boolean on,boolean mirror,int seed) {
        Position pos=Position.valueOf(l.position); TeamSide early=mirror?TeamSide.RED:TeamSide.BLUE, scaling=early.opposite();
        int value=switch(skill){case "S0"->14;case "S1"->15;case "S3"->17;case "S5"->19;default->throw new IllegalArgumentException(skill);};
        Team blue=team("audit-blue",pos,scaling==TeamSide.BLUE?value:14), red=team("audit-red",pos,scaling==TeamSide.RED?value:14);
        MatchSimulator.SimulationResult result=simulator(on).simulateWithDiagnostics(blue,red,seed,assignment(l,mirror));
        MatchTimeline t=result.timeline(); MatchSnapshot end=t.getSnapshots().getLast();
        PlayerSnapshot e=player(end,early,pos),s=player(end,scaling,pos); int apps=(int)result.championPowerExecutionStats().samples().stream().filter(com.lolfm.champion.ChampionPowerCombatSample::championPowerEnabled).count();
        CombatOutcomeExecutionStatsSnapshot outcomes=result.combatOutcomeExecutionStats();int earlyLane=outcomes.participantWins(ProgressionCombatContext.LANE_COMBAT,new PlayerKey(early,pos)),scalingLane=outcomes.participantWins(ProgressionCombatContext.LANE_COMBAT,new PlayerKey(scaling,pos));
        csv.append(l.id).append(',').append(l.position).append(',').append(skill).append(',').append(on?"CHAMPION_ON":"CHAMPION_OFF").append(',').append(mirror?"FULL_TEAM_MIRROR":"ORIGINAL").append(',').append(seed).append(',').append(result.winnerSide()==TeamSide.BLUE).append(',').append(t.getWinner()).append(',').append(t.getDurationSeconds()).append(',').append(result.endReason()).append(',').append(end.getBlueKills()).append(',').append(end.getRedKills()).append(',').append(end.getBlueGold()).append(',').append(end.getRedGold()).append(',').append(end.getBlueDragons()).append(',').append(end.getRedDragons()).append(',').append(end.getBlueTowersDestroyed()).append(',').append(end.getRedTowersDestroyed()).append(',').append(early).append(',').append(scaling).append(',').append(kda(e)).append(',').append(kda(s)).append(',').append(e.getGold()).append(',').append(s.getGold()).append(',').append(e.getLevel()).append(',').append(s.getLevel()).append(',').append(e.getItemStage()).append(',').append(s.getItemStage()).append(',').append(earlyLane).append(',').append(scalingLane).append(',').append(outcomes.wins(ProgressionCombatContext.TEAMFIGHT,TeamSide.BLUE)).append(',').append(outcomes.wins(ProgressionCombatContext.TEAMFIGHT,TeamSide.RED)).append(',').append(outcomes.wins(ProgressionCombatContext.OBJECTIVE_FIGHT,TeamSide.BLUE)).append(',').append(outcomes.wins(ProgressionCombatContext.OBJECTIVE_FIGHT,TeamSide.RED)).append(',').append(apps).append(',').append(result.championPowerExecutionStats().randomCallCount()).append(",0,0,").append(outcomes.duplicateOutcomeRecordErrors()).append(',').append(outcomes.outcomeWithoutAttemptErrors()).append(',').append(outcomes.outcomeWithoutWinnerErrors()).append(',').append(outcomes.participantMismatchErrors()).append(',').append(outcomes.wins(ProgressionCombatContext.LANE_COMBAT,TeamSide.BLUE)).append(',').append(outcomes.wins(ProgressionCombatContext.LANE_COMBAT,TeamSide.RED)).append('\n');
    }
    private static MatchChampionAssignments assignment(Lineup l,boolean mirror) {
        ChampionLineupRequest b=new ChampionLineupRequest(l.blue[0],l.blue[1],l.blue[2],l.blue[3],l.blue[4]);
        ChampionLineupRequest r=new ChampionLineupRequest(l.red[0],l.red[1],l.red[2],l.red[3],l.red[4]);
        return SELECTOR.resolve(new ChampionSelectionRequest(mirror?r:b,mirror?b:r));
    }
    private static Team team(String name,Position target,int targetSkill){
        java.util.ArrayList<Player> players=new java.util.ArrayList<>();
        for(Position p:Position.values()){int v=p==target?targetSkill:14;players.add(new Player(name+"-"+p,p,new PlayerAttributes(v,v,v,v)));}
        return new Team(name,players);
    }
    private static PlayerSnapshot player(MatchSnapshot snapshot,TeamSide side,Position position){return snapshot.getPlayerSnapshots().stream().filter(p->p.getTeamSide()==side&&p.getPosition()==position).findFirst().orElseThrow();}
    private static String kda(PlayerSnapshot p){return p.getKills()+"/"+p.getDeaths()+"/"+p.getAssists();}
    private static MatchSimulator simulator(boolean on) { return new MatchSimulator(new TeamfightResolver(),new EndGameEvaluator(),new SnapshotFactory(CATALOG),new ObjectiveResolver(),new PostFightResolver(),new ObjectiveAttemptResolver(),new StructureResolver(),new PushResolver(),SimulationOptions.productionDefaults().withDiagnosticsEnabled(false).withChampionPowerEnabled(on)); }
    private static List<Lineup> lineups(){return List.of(
      new Lineup("TOP","TOP",new String[]{"renekton","sejuani","orianna","ezreal","braum"},new String[]{"jax","maokai","viktor","jinx","lulu"}),
      new Lineup("JUNGLE","JUNGLE",new String[]{"ornn","lee-sin","orianna","ezreal","braum"},new String[]{"gwen","viego","viktor","jinx","lulu"}),
      new Lineup("MID","MID",new String[]{"ornn","sejuani","leblanc","ezreal","braum"},new String[]{"gwen","maokai","viktor","jinx","lulu"}),
      new Lineup("ADC","ADC",new String[]{"ornn","sejuani","orianna","lucian","braum"},new String[]{"gwen","maokai","viktor","jinx","lulu"}),
      new Lineup("SUPPORT","SUPPORT",new String[]{"ornn","sejuani","orianna","ezreal","nautilus"},new String[]{"gwen","maokai","viktor","jinx","lulu"}));}
    private record Lineup(String id,String position,String[] blue,String[] red) { }
}
