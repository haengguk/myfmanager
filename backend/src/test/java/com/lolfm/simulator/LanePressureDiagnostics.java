package com.lolfm.simulator;

import com.lolfm.domain.*;
import java.util.*;

/** Reproducible lane-pressure distribution report; reads structured snapshots only. */
public final class LanePressureDiagnostics {
    public static void main(String[] args) { for (Scenario s: List.of(Scenario.A,Scenario.B,Scenario.C,Scenario.D,Scenario.E)) run(s, 1000); for (Scenario s: List.of(Scenario.MB,Scenario.MC,Scenario.MD,Scenario.ME)) run(s,500); }
    private static void run(Scenario scenario,int runs) {
        Stats ten=new Stats(), fourteen=new Stats(); int replay=0, timeout=0; long duration=0;
        for(long seed=1;seed<=runs;seed++) {
            MatchTimeline t=sim().simulate(team("BLUE",scenario.blue),team("RED",scenario.red),seed), again=sim().simulate(team("BLUE",scenario.blue),team("RED",scenario.red),seed);
            if(!signature(t).equals(signature(again))) replay++; if(t.getWinner()==null) timeout++; duration+=t.getDurationSeconds();
            collect(at(t,600),ten); collect(at(t,840),fourteen);
        }
        System.out.printf("%n%s runs=%d timeout=%.3f replayMismatch=%d avgDuration=%.1f%n",scenario.name,runs,timeout/(double)runs,replay,duration/(double)runs);
        print("10m",ten,runs); print("14m",fourteen,runs);
    }
    private static void collect(MatchSnapshot s,Stats x) { for(LaneSnapshot l:s.getLaneSnapshots()) x.lane.get(l.lane()).add(l); for(PlayerSnapshot p:s.getPlayerSnapshots()) x.players.get(p.getPosition()).add(p.getTeamName().equals("BLUE"),p.getCs(),p.getGold()); }
    private static void print(String label,Stats s,int n) { System.out.print(label); for(Lane l:Lane.values()) { L a=s.lane.get(l); System.out.printf(" %s[p %.2f abs %.2f min %.1f max %.1f pri B/N/R %.2f/%.2f/%.2f strong %.2f dom %.2f]",l,a.sum/n,a.abs/n,a.min,a.max,a.blue/(double)n,a.neutral/(double)n,a.red/(double)n,a.strong/(double)n,a.dominant/(double)n); } for(Position p:Position.values()) { P a=s.players.get(p); System.out.printf(" %s[cs %.2f/%.2f g %.2f/%.2f]",p,a.bc/(double)n,a.rc/(double)n,a.bg/(double)n,a.rg/(double)n); } System.out.println(); }
    private static MatchSnapshot at(MatchTimeline t,int sec) { MatchSnapshot r=t.getSnapshots().getFirst(); for(MatchSnapshot s:t.getSnapshots()) {if(s.getTimeSeconds()>sec)break;r=s;} return r; }
    private static String signature(MatchTimeline t) { return t.getDurationSeconds()+":"+t.getWinner()+":"+t.getSnapshots().stream().map(s->s.getLaneSnapshots().toString()+s.getBlueGold()+s.getRedGold()).toList(); }
    private static MatchSimulator sim(){return new MatchSimulator(new TeamfightResolver(),new EndGameEvaluator(),new SnapshotFactory(),new ObjectiveResolver(),new PostFightResolver(),new ObjectiveAttemptResolver(),new StructureResolver(),new PushResolver());}
    private static Team team(String name,Map<Position,Integer> values){List<Player> ps=new ArrayList<>();for(Position p:Position.values()){int v=values.getOrDefault(p,14);ps.add(new Player(p.name(),p,new PlayerAttributes(v,v,14,v)));}return new Team(name,ps);}
    private enum Scenario { A(Map.of(),Map.of()),B(Map.of(Position.TOP,18),Map.of(Position.TOP,10)),C(Map.of(Position.MID,18),Map.of(Position.MID,10)),D(Map.of(Position.ADC,18),Map.of(Position.ADC,10)),E(Map.of(Position.SUPPORT,18),Map.of(Position.SUPPORT,10)),MB(Map.of(Position.TOP,10),Map.of(Position.TOP,18)),MC(Map.of(Position.MID,10),Map.of(Position.MID,18)),MD(Map.of(Position.ADC,10),Map.of(Position.ADC,18)),ME(Map.of(Position.SUPPORT,10),Map.of(Position.SUPPORT,18)); final Map<Position,Integer> blue,red; final String name; Scenario(Map<Position,Integer>b,Map<Position,Integer>r){blue=b;red=r;name=name();} }
    private static final class Stats { final EnumMap<Lane,L> lane=new EnumMap<>(Lane.class);final EnumMap<Position,P> players=new EnumMap<>(Position.class); Stats(){for(Lane l:Lane.values())lane.put(l,new L());for(Position p:Position.values())players.put(p,new P());} }
    private static final class L { double sum,abs,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;int blue,neutral,red,strong,dominant;void add(LaneSnapshot x){double p=x.pressure();sum+=p;abs+=Math.abs(p);min=Math.min(min,p);max=Math.max(max,p);if(x.priority()==LanePriority.BLUE)blue++;else if(x.priority()==LanePriority.RED)red++;else neutral++;if(Math.abs(p)>=50)strong++;if(Math.abs(p)>=75)dominant++;} }
    private static final class P {long bc,rc,bg,rg;void add(boolean b,int c,int g){if(b){bc+=c;bg+=g;}else{rc+=c;rg+=g;}}}
}
