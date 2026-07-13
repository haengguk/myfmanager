package com.lolfm.simulator;

import com.lolfm.domain.CombatSource;
import com.lolfm.domain.JungleGankData;
import com.lolfm.domain.LaneSnapshot;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.PlayerSnapshot;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Structured jungle-gank diagnostics. Event message/description is never read. */
public final class JungleGankDiagnostics {
    private static final int CHECKPOINT = 840;

    public static void main(String[] args) {
        System.out.println("JUNGLE_GANK A-G ON/OFF (seeds 1..1000; 10m/14m/end)");
        for (Scenario scenario : Scenario.base()) {
            Summary on = run(scenario, 1_000, true, true);
            Summary off = run(scenario, 1_000, false, false);
            print(scenario.name(), "ON", on);
            print(scenario.name(), "OFF", off);
            printDelta(scenario.name(), on, off);
        }
        System.out.println("JUNGLE_GANK MIRROR B-G (seeds 1..500)");
        for (Scenario scenario : Scenario.mirrors()) print(scenario.name(), "ON", run(scenario, 500, true, true));
    }

    private static Summary run(Scenario scenario, int runs, boolean enabled, boolean replay) {
        Summary summary = new Summary(runs);
        for (long seed = 1; seed <= runs; seed++) {
            MatchTimeline timeline = simulator(enabled).simulate(scenario.blueTeam(), scenario.redTeam(), seed);
            summary.collect(timeline, scenario);
            if (replay) {
                MatchTimeline again = simulator(enabled).simulate(scenario.blueTeam(), scenario.redTeam(), seed);
                if (!signature(timeline).equals(signature(again))) summary.replayMismatch++;
            }
        }
        return summary;
    }

    private static void print(String name, String mode, Summary s) {
        System.out.printf("%s %s TRIGGER B/R=%d/%d dual=%d attempts=%d side[B/R]=%d/%d twoSameTick=%d outcomes[N/S/R]=%d/%d/%d successRate=%.4f kills[B/R]=%d/%d avg[over/edge/dec/success]=%.3f/%.3f/%.4f/%.4f%n",
                name, mode, s.blueTriggers, s.redTriggers, s.dualTriggers, s.attempts, s.blueAttempts, s.redAttempts,
                s.twoGanksSameTick, s.noKill, s.success, s.reverse, s.successRate(), s.blueGankKills, s.redGankKills,
                s.avg(s.overextension), s.avg(s.combatEdge), s.avg(s.decisiveChance), s.avg(s.successChance));
        System.out.printf("%s %s TARGET T/M/B=%d/%d/%d ratio=%.4f/%.4f/%.4f repeat=%d penalty=%d cooldownViolation=%d ineligible=%d%n",
                name, mode, s.targets.get(Lane.TOP), s.targets.get(Lane.MID), s.targets.get(Lane.BOT),
                s.ratio(s.targets.get(Lane.TOP)), s.ratio(s.targets.get(Lane.MID)), s.ratio(s.targets.get(Lane.BOT)),
                s.repeatTargets, s.repeatPenaltyApplied, s.cooldownViolations, s.ineligibleAttempts);
        System.out.printf("%s %s PARTICIPANT soloKiller[J/L]=%d/%d soloReverseVictim[J/L]=%d/%d botKiller[J/A/S]=%d/%d/%d botVictim[A/S]=%d/%d botReverseKiller[A/S]=%d/%d botReverseVictim[J/A/S]=%d/%d/%d assists[ok/missing/wrong/dup]=%d/%d/%d/%d%n",
                name, mode, s.soloJungleKiller, s.soloLanerKiller, s.soloReverseJungleVictim, s.soloReverseLanerVictim,
                s.botJungleKiller, s.botAdcKiller, s.botSupportKiller, s.botAdcVictim, s.botSupportVictim,
                s.botReverseAdcKiller, s.botReverseSupportKiller, s.botReverseJungleVictim,
                s.botReverseAdcVictim, s.botReverseSupportVictim, s.assistOk, s.assistMissing, s.wrongTeamAssist, s.duplicateAssist);
        System.out.printf("%s %s JUNGLE_COST block[Ticks/expectedCs/gold]=%d/%.3f/%.2f overlap=%d doubleLoss=%d invalid[cs/gold/random/resume]=%d/%d/%d/%d JG10 CS/G[B/R]=%.2f/%.2f/%.2f/%.2f JG14 CS/G[B/R]=%.2f/%.2f/%.2f/%.2f%n",
                name, mode, s.jungleBlockedTicks, s.jungleMissedExpectedCs, s.jungleMissedGold, s.recoveryOverlapTicks,
                s.doubleLoss, s.blockedCsAwards, s.blockedGoldAwards, s.blockedRandomSuspicions, s.resumeFailures,
                s.blueJungleCs10/(double)s.runs, s.blueJungleGold10/(double)s.runs, s.redJungleCs10/(double)s.runs, s.redJungleGold10/(double)s.runs,
                s.blueJungleCs14/(double)s.runs, s.blueJungleGold14/(double)s.runs, s.redJungleCs14/(double)s.runs, s.redJungleGold14/(double)s.runs);
        System.out.printf("%s %s LANE14 deaths[T/J/M/A/S]=%.3f/%.3f/%.3f/%.3f/%.3f CS B/R/gap[T]=%.2f/%.2f/%.2f [J]=%.2f/%.2f/%.2f [M]=%.2f/%.2f/%.2f [A]=%.2f/%.2f/%.2f [S]=%.2f/%.2f/%.2f avgTeamGoldGap=%.2f%n",
                name,mode,s.death(Position.TOP),s.death(Position.JUNGLE),s.death(Position.MID),s.death(Position.ADC),s.death(Position.SUPPORT),
                s.blueCs(Position.TOP),s.redCs(Position.TOP),s.gap(Position.TOP),s.blueCs(Position.JUNGLE),s.redCs(Position.JUNGLE),s.gap(Position.JUNGLE),
                s.blueCs(Position.MID),s.redCs(Position.MID),s.gap(Position.MID),s.blueCs(Position.ADC),s.redCs(Position.ADC),s.gap(Position.ADC),
                s.blueCs(Position.SUPPORT),s.redCs(Position.SUPPORT),s.gap(Position.SUPPORT),s.goldGap/s.runs);
        System.out.printf("%s %s VICTIM count=%d pos[T/J/M/A/S]=%d/%d/%d/%d/%d missed[ticks/cs/gold]=%.3f/%.3f/%.2f pressure[T/M/B]=%.2f/%.2f/%.2f priorityB/N/R[T]=%.3f/%.3f/%.3f [M]=%.3f/%.3f/%.3f [B]=%.3f/%.3f/%.3f%n",
                name, mode, s.victimCount, s.victims.get(Position.TOP), s.victims.get(Position.JUNGLE),
                s.victims.get(Position.MID), s.victims.get(Position.ADC), s.victims.get(Position.SUPPORT),
                s.victimCount == 0 ? 0 : s.victimMissedTicks/(double)s.victimCount,
                s.victimCount == 0 ? 0 : s.victimMissedCs/s.victimCount,
                s.victimCount == 0 ? 0 : s.victimMissedGold/s.victimCount,
                s.pressure.get(Lane.TOP)/s.runs, s.pressure.get(Lane.MID)/s.runs, s.pressure.get(Lane.BOT)/s.runs,
                s.priority(Lane.TOP,LanePriority.BLUE),s.priority(Lane.TOP,LanePriority.NEUTRAL),s.priority(Lane.TOP,LanePriority.RED),
                s.priority(Lane.MID,LanePriority.BLUE),s.priority(Lane.MID,LanePriority.NEUTRAL),s.priority(Lane.MID,LanePriority.RED),
                s.priority(Lane.BOT,LanePriority.BLUE),s.priority(Lane.BOT,LanePriority.NEUTRAL),s.priority(Lane.BOT,LanePriority.RED));
        System.out.printf("%s %s COMBAT kills[Gank/Lane/Skirm/Team/Obj/Other]=%d/%d/%d/%d/%d/%d skipped[Lane/Generic/Teamfight]=%d/%d/%d invalid[multiCombat/dead/duplicateReward/supportCs/replay]=%d/%d/%d/%d/%d%n",
                name, mode, s.kills.get(CombatSource.JUNGLE_GANK), s.kills.get(CombatSource.LANE_COMBAT),
                s.kills.get(CombatSource.SKIRMISH), s.kills.get(CombatSource.TEAMFIGHT),
                s.kills.get(CombatSource.OBJECTIVE_FIGHT), s.kills.get(CombatSource.OTHER),
                s.attempts, s.attempts, s.attempts, s.multipleCombatTicks, s.deadParticipants,
                s.duplicateRewards, s.supportCsViolations, s.replayMismatch);
        System.out.printf("%s %s END win[B/R]=%.4f/%.4f duration[avg/median/p90/p95]=%.1f/%d/%d/%d over40=%.4f over60=%.4f timeout=%d%n",
                name, mode, s.blueWins/(double)s.runs, s.redWins/(double)s.runs, s.duration/(double)s.runs,
                s.percentile(.50), s.percentile(.90), s.percentile(.95), s.over(2400), s.over(3600), s.timeouts);
    }

    private static void printDelta(String name, Summary on, Summary off) {
        System.out.printf("%s DELTA ON-OFF attempts=%d gankKill=%d laneKill=%d skirmKill=%d teamfightKill=%d totalKill=%d avgGoldGap=%.2f winBlue=%.4f avgDuration=%.1f%n",
                name, on.attempts, on.kills.get(CombatSource.JUNGLE_GANK),
                on.kills.get(CombatSource.LANE_COMBAT)-off.kills.get(CombatSource.LANE_COMBAT),
                on.kills.get(CombatSource.SKIRMISH)-off.kills.get(CombatSource.SKIRMISH),
                on.kills.get(CombatSource.TEAMFIGHT)-off.kills.get(CombatSource.TEAMFIGHT),
                on.totalKills()-off.totalKills(), on.goldGap/on.runs-off.goldGap/off.runs,
                on.blueWins/(double)on.runs-off.blueWins/(double)off.runs, on.duration/(double)on.runs-off.duration/(double)off.runs);
    }

    private static MatchSimulator simulator(boolean enabled) {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), true, true, enabled);
    }

    private static final class Summary {
        final int runs;
        int blueTriggers, redTriggers, dualTriggers, attempts, blueAttempts, redAttempts, twoGanksSameTick;
        int noKill, success, reverse, blueGankKills, redGankKills;
        double overextension, combatEdge, decisiveChance, successChance;
        final EnumMap<Lane,Integer> targets=ints(Lane.class);
        int repeatTargets, repeatPenaltyApplied, cooldownViolations, ineligibleAttempts;
        int soloJungleKiller, soloLanerKiller, soloReverseJungleVictim, soloReverseLanerVictim;
        int botJungleKiller,botAdcKiller,botSupportKiller,botAdcVictim,botSupportVictim;
        int botReverseAdcKiller,botReverseSupportKiller,botReverseJungleVictim,botReverseAdcVictim,botReverseSupportVictim;
        int assistOk,assistMissing,wrongTeamAssist,duplicateAssist;
        int jungleBlockedTicks,recoveryOverlapTicks,doubleLoss,blockedCsAwards,blockedGoldAwards,blockedRandomSuspicions,resumeFailures;
        double jungleMissedExpectedCs,jungleMissedGold;
        long blueJungleCs10,redJungleCs10,blueJungleGold10,redJungleGold10,blueJungleCs14,redJungleCs14,blueJungleGold14,redJungleGold14;
        int victimCount,victimMissedTicks; double victimMissedCs,victimMissedGold;
        final EnumMap<Position,Integer> victims=ints(Position.class);
        final EnumMap<Position,Long> blueCs=longs(Position.class),redCs=longs(Position.class),deaths=longs(Position.class);
        final EnumMap<Position,Double> csGap=doubles(Position.class);
        final EnumMap<Lane,Double> pressure=doubles(Lane.class);
        final EnumMap<Lane,EnumMap<LanePriority,Integer>> priorities=new EnumMap<>(Lane.class);
        final EnumMap<CombatSource,Integer> kills=ints(CombatSource.class);
        int multipleCombatTicks,deadParticipants,duplicateRewards,supportCsViolations,replayMismatch;
        int blueWins,redWins,timeouts; long duration; final List<Integer> durations=new ArrayList<>(); double goldGap;
        Summary(int runs){this.runs=runs;for(Lane l:Lane.values())priorities.put(l,ints(LanePriority.class));}

        void collect(MatchTimeline timeline, Scenario scenario) {
            MatchSnapshot ten=at(timeline,600), fourteen=at(timeline,840), end=timeline.getSnapshots().getLast();
            PlayerSnapshot b10=player(ten,"BLUE-JUNGLE"),r10=player(ten,"RED-JUNGLE"),b14=player(fourteen,"BLUE-JUNGLE"),r14=player(fourteen,"RED-JUNGLE");
            blueJungleCs10+=b10.getCs();redJungleCs10+=r10.getCs();blueJungleGold10+=b10.getGold();redJungleGold10+=r10.getGold();
            blueJungleCs14+=b14.getCs();redJungleCs14+=r14.getCs();blueJungleGold14+=b14.getGold();redJungleGold14+=r14.getGold();
            goldGap+=Math.abs(fourteen.getBlueGold()-fourteen.getRedGold());
            for(Position position:Position.values()){PlayerSnapshot bp=fourteen.getPlayerSnapshots().stream().filter(p->p.getTeamName().equals("BLUE")&&p.getPosition()==position).findFirst().orElseThrow();PlayerSnapshot rp=fourteen.getPlayerSnapshots().stream().filter(p->p.getTeamName().equals("RED")&&p.getPosition()==position).findFirst().orElseThrow();blueCs.merge(position,(long)bp.getCs(),Long::sum);redCs.merge(position,(long)rp.getCs(),Long::sum);csGap.merge(position,(double)Math.abs(bp.getCs()-rp.getCs()),Double::sum);deaths.merge(position,(long)bp.getDeaths()+rp.getDeaths(),Long::sum);}
            for(LaneSnapshot lane:fourteen.getLaneSnapshots()){pressure.merge(lane.lane(),lane.pressure(),Double::sum);priorities.get(lane.lane()).merge(lane.priority(),1,Integer::sum);}
            if(end.getPlayerSnapshots().stream().filter(p->p.getPosition()==Position.SUPPORT).anyMatch(p->p.getCs()!=0))supportCsViolations++;
            Map<TeamSide,Map<Lane,Integer>> lastBySide=new EnumMap<>(TeamSide.class);for(TeamSide side:TeamSide.values())lastBySide.put(side,new EnumMap<>(Lane.class));
            Map<TeamSide,Integer> lastByJungler=new EnumMap<>(TeamSide.class);Map<Integer,Integer> ganksAt=new HashMap<>(),combatAt=new HashMap<>();Set<String> rewards=new HashSet<>();
            for(MatchEvent event:timeline.getEvents()){
                if(event.getTimeSeconds()>CHECKPOINT)continue;
                if(event.getType()==MatchEventType.JUNGLE_GANK){
                    JungleGankData d=event.getJungleGank(); attempts++;ganksAt.merge(event.getTimeSeconds(),1,Integer::sum);combatAt.merge(event.getTimeSeconds(),1,Integer::sum);
                    if(d.blueTriggered())blueTriggers++;if(d.redTriggered())redTriggers++;if(d.blueTriggered()&&d.redTriggered())dualTriggers++;
                    if(d.gankingSide()==TeamSide.BLUE)blueAttempts++;else redAttempts++;targets.merge(d.targetLane(),1,Integer::sum);
                    Integer last=lastByJungler.put(d.gankingSide(),event.getTimeSeconds());if(last!=null&&event.getTimeSeconds()-last<120)cooldownViolations++;
                    Integer same=lastBySide.get(d.gankingSide()).put(d.targetLane(),event.getTimeSeconds());if(same!=null&&event.getTimeSeconds()-same<180){repeatTargets++;if(d.targetWeight()>0)repeatPenaltyApplied++;}
                    overextension+=d.enemyOverextension();combatEdge+=d.combatEdge();decisiveChance+=d.decisiveChance();successChance+=d.gankSuccessChance();
                    if(d.outcome()==JungleGankOutcome.NO_KILL)noKill++;else{victimCount++;Position vp=position(d.victimPlayerId());victims.merge(vp,1,Integer::sum);collectVictimLoss(timeline,d,event.getTimeSeconds(),vp);
                        if(d.outcome()==JungleGankOutcome.GANK_SUCCESS)success++;else reverse++;if(d.winningSide()==TeamSide.BLUE)blueGankKills++;else redGankKills++;collectParticipants(d);}
                    collectJungleCost(timeline,d,event.getTimeSeconds());
                } else if(event.getType()==MatchEventType.LANE_COMBAT){combatAt.merge(event.getTimeSeconds(),1,Integer::sum);if(event.getLaneCombat()!=null&&event.getLaneCombat().outcome()!=LaneCombatOutcome.NO_KILL)kills.merge(CombatSource.LANE_COMBAT,1,Integer::sum);}
                if(event.getType()==MatchEventType.TEAMFIGHT)combatAt.merge(event.getTimeSeconds(),1,Integer::sum);
                if(event.getType()==MatchEventType.KILL){CombatSource source=event.getCombatSource()==null?CombatSource.OTHER:event.getCombatSource();kills.merge(source,1,Integer::sum);if(source==CombatSource.SKIRMISH)combatAt.merge(event.getTimeSeconds(),1,Integer::sum);String key=event.getTimeSeconds()+":"+event.getVictim();if(!rewards.add(key))duplicateRewards++;}
            }
            twoGanksSameTick+=(int)ganksAt.values().stream().filter(v->v>1).count();multipleCombatTicks+=(int)combatAt.values().stream().filter(v->v>1).count();
            duration+=timeline.getDurationSeconds();durations.add(timeline.getDurationSeconds());if(timeline.getWinner()==null)timeouts++;else if(timeline.getWinner().equals("BLUE"))blueWins++;else redWins++;
        }

        void collectParticipants(JungleGankData d){
            Position killer=position(d.killerPlayerId()),victim=position(d.victimPlayerId());
            boolean own=d.outcome()==JungleGankOutcome.GANK_SUCCESS;
            int expected=d.targetLane()==Lane.BOT?(own?2:1):(own?1:0);if(d.assistantPlayerIds().size()==expected)assistOk++;else assistMissing++;
            if(d.assistantPlayerIds().stream().distinct().count()!=d.assistantPlayerIds().size())duplicateAssist++;
            TeamSide assistSide=own?d.gankingSide():d.gankingSide().opposite();if(d.assistantPlayerIds().stream().anyMatch(id->side(id)!=assistSide))wrongTeamAssist++;
            if(d.targetLane()!=Lane.BOT){if(own){if(killer==Position.JUNGLE)soloJungleKiller++;else soloLanerKiller++;}else{if(victim==Position.JUNGLE)soloReverseJungleVictim++;else soloReverseLanerVictim++;}}
            else if(own){if(killer==Position.JUNGLE)botJungleKiller++;else if(killer==Position.ADC)botAdcKiller++;else botSupportKiller++;if(victim==Position.ADC)botAdcVictim++;else botSupportVictim++;}
            else{if(killer==Position.ADC)botReverseAdcKiller++;else botReverseSupportKiller++;if(victim==Position.JUNGLE)botReverseJungleVictim++;else if(victim==Position.ADC)botReverseAdcVictim++;else botReverseSupportVictim++;}
        }

        void collectJungleCost(MatchTimeline t,JungleGankData d,int time){
            String id=(d.gankingSide()==TeamSide.BLUE?"BLUE":"RED")+"-JUNGLE";int previous=player(at(t,time),id).getCs();
            for(int tick=time+10;tick<d.jungleFarmBlockedUntilSeconds();tick+=10){PlayerSnapshot p=player(at(t,tick),id);int now=p.getCs();if(p.isCanFarm()){jungleBlockedTicks++;jungleMissedExpectedCs+=PositionEconomyRuleConfig.JUNGLE_BASE_CS_PER_MINUTE/6.0;jungleMissedGold+=PositionEconomyRuleConfig.JUNGLE_BASE_CS_PER_MINUTE/6.0*20;if(now!=previous)blockedCsAwards++;}else recoveryOverlapTicks++;previous=now;}
        }
        void collectVictimLoss(MatchTimeline t,JungleGankData d,int death,Position position){PlayerSnapshot victim=player(at(t,death),d.victimPlayerId());int ticks=0;for(int tick=death+10;tick<victim.getFarmResumeAtSeconds();tick+=10)ticks++;victimMissedTicks+=ticks;double cs=base(position)/6.0*ticks;victimMissedCs+=cs;victimMissedGold+=cs*20;}
        double blueCs(Position p){return blueCs.get(p)/(double)runs;}double redCs(Position p){return redCs.get(p)/(double)runs;}double gap(Position p){return csGap.get(p)/runs;}double death(Position p){return deaths.get(p)/(2.0*runs);}
        double avg(double v){return attempts==0?0:v/attempts;} double ratio(int v){return attempts==0?0:v/(double)attempts;} double priority(Lane l,LanePriority p){return priorities.get(l).get(p)/(double)runs;} int totalKills(){return kills.values().stream().mapToInt(Integer::intValue).sum();}
        double successRate(){return attempts==0?0:success/(double)attempts;}int percentile(double q){List<Integer>x=new ArrayList<>(durations);x.sort(Comparator.naturalOrder());return x.get(Math.min(x.size()-1,(int)Math.ceil(q*x.size())-1));}double over(int seconds){return durations.stream().filter(v->v>=seconds).count()/(double)runs;}
    }

    private record Scenario(String name,Map<Position,Values> blue,Map<Position,Values> red){
        static List<Scenario> base(){return List.of(eq("A"),change("B",Position.JUNGLE,18,18,10,10),change("C",Position.JUNGLE,14,18,14,10),change("D",Position.TOP,10,10,18,18),change("E",Position.MID,10,10,18,18),bot("F",false),mixed("G",false));}
        static List<Scenario> mirrors(){return List.of(change("B_M",Position.JUNGLE,10,10,18,18),change("C_M",Position.JUNGLE,14,10,14,18),change("D_M",Position.TOP,18,18,10,10),change("E_M",Position.MID,18,18,10,10),bot("F_M",true),mixed("G_M",true));}
        static Scenario eq(String n){return new Scenario(n,Map.of(),Map.of());}static Scenario change(String n,Position p,int bm,int ba,int rm,int ra){return new Scenario(n,Map.of(p,new Values(bm,ba)),Map.of(p,new Values(rm,ra)));}
        static Scenario bot(String n,boolean mirror){Values b=new Values(mirror?18:10,mirror?18:10),r=new Values(mirror?10:18,mirror?10:18);return new Scenario(n,Map.of(Position.ADC,b,Position.SUPPORT,b),Map.of(Position.ADC,r,Position.SUPPORT,r));}
        static Scenario mixed(String n,boolean mirror){return new Scenario(n,Map.of(Position.JUNGLE,new Values(mirror?10:18,mirror?10:18),Position.TOP,new Values(mirror?18:10,mirror?18:10)),Map.of(Position.JUNGLE,new Values(mirror?18:10,mirror?18:10),Position.TOP,new Values(mirror?10:18,mirror?10:18)));}
        Team blueTeam(){return team("BLUE",blue);}Team redTeam(){return team("RED",red);}static Team team(String side,Map<Position,Values> values){List<Player>p=new ArrayList<>();for(Position pos:Position.values()){Values v=values.getOrDefault(pos,new Values(14,14));p.add(new Player(side+"-"+pos,pos,new PlayerAttributes(v.mechanics,v.aggression,14,14)));}return new Team(side,p);}
    }
    private record Values(int mechanics,int aggression){}
    private static MatchSnapshot at(MatchTimeline t,int time){MatchSnapshot r=t.getSnapshots().getFirst();for(MatchSnapshot s:t.getSnapshots()){if(s.getTimeSeconds()>time)break;r=s;}return r;}
    private static PlayerSnapshot player(MatchSnapshot s,String id){return s.getPlayerSnapshots().stream().filter(p->p.getPlayerName().equals(id)).findFirst().orElseThrow();}
    private static Position position(String id){return Position.valueOf(id.substring(id.indexOf('-')+1));}private static TeamSide side(String id){return id.startsWith("BLUE-")?TeamSide.BLUE:TeamSide.RED;}
    private static double base(Position p){return switch(p){case TOP->7;case JUNGLE->5.8;case MID->7.1;case ADC->7.2;case SUPPORT->0;};}
    private static String signature(MatchTimeline t){return t.getDurationSeconds()+":"+t.getWinner()+":"+t.getEvents().stream().map(e->e.getTimeSeconds()+":"+e.getType()+":"+e.getCombatSource()+":"+e.getJungleGank()+":"+e.getKiller()+":"+e.getVictim()).toList()+":"+t.getSnapshots().stream().map(s->s.getTimeSeconds()+":"+s.getBlueGold()+":"+s.getRedGold()+":"+s.getLaneSnapshots()).toList();}
    private static <E extends Enum<E>>EnumMap<E,Integer>ints(Class<E>c){EnumMap<E,Integer>m=new EnumMap<>(c);for(E e:c.getEnumConstants())m.put(e,0);return m;}private static <E extends Enum<E>>EnumMap<E,Long>longs(Class<E>c){EnumMap<E,Long>m=new EnumMap<>(c);for(E e:c.getEnumConstants())m.put(e,0L);return m;}private static <E extends Enum<E>>EnumMap<E,Double>doubles(Class<E>c){EnumMap<E,Double>m=new EnumMap<>(c);for(E e:c.getEnumConstants())m.put(e,0d);return m;}
}
