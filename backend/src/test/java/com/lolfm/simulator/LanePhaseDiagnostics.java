package com.lolfm.simulator;

import com.lolfm.domain.*;
import java.util.*;

/** Large structured LanePhase distribution diagnostics; excluded from normal tests. */
public final class LanePhaseDiagnostics {
    private static final int FULL_SEEDS=1000,MIRROR_SEEDS=500;
    private static final List<Integer>TIMES=List.of(300,480,600,720,840);

    public static void main(String[] args){
        List<Scenario> scenarios=scenarios();Map<String,Report> reports=new LinkedHashMap<>();
        for(Scenario scenario:scenarios){audit(scenario);runPair(scenario,FULL_SEEDS,reports);}
        for(Scenario scenario:scenarios)if(!scenario.key.equals("A")){Scenario mirror=scenario.mirror();audit(mirror);runPair(mirror,MIRROR_SEEDS,reports);}
        for(Scenario scenario:scenarios){delta(scenario.key,reports);if(!scenario.key.equals("A"))delta(scenario.key+"_MIRROR",reports);}
        System.out.println("LIMIT ON/OFF results are population averages; siege Random and structure changes alter the later shared Random stream.");
    }
    private static void runPair(Scenario s,int seeds,Map<String,Report> reports){
        for(boolean enabled:List.of(true,false)){Report r=run(s,seeds,enabled);reports.put(s.key+":"+enabled,r);System.out.println(r.line());}
    }
    private static Report run(Scenario scenario,int seeds,boolean enabled){
        SimulationOptions options=SimulationOptions.productionDefaults().withLanePhaseEnabled(enabled);
        MatchSimulator simulator=simulator(options);Report report=new Report(scenario.key,enabled,seeds);
        for(long seed=1;seed<=seeds;seed++)report.add(simulator.simulateWithDiagnostics(scenario.team(TeamSide.BLUE),scenario.team(TeamSide.RED),seed));
        report.replayMismatch=signature(simulator.simulate(scenario.team(TeamSide.BLUE),scenario.team(TeamSide.RED),1)).equals(signature(simulator.simulate(scenario.team(TeamSide.BLUE),scenario.team(TeamSide.RED),1)))?0:1;
        MatchTimeline on=simulator(options.withDiagnosticsEnabled(true)).simulate(scenario.team(TeamSide.BLUE),scenario.team(TeamSide.RED),2);
        MatchTimeline off=simulator(options.withDiagnosticsEnabled(false)).simulate(scenario.team(TeamSide.BLUE),scenario.team(TeamSide.RED),2);
        report.diagnosticsMismatch=signature(on).equals(signature(off))?0:1;
        return report;
    }
    private static MatchSimulator simulator(SimulationOptions o){return new MatchSimulator(new TeamfightResolver(),new EndGameEvaluator(),new SnapshotFactory(),new ObjectiveResolver(),new PostFightResolver(),new ObjectiveAttemptResolver(),new StructureResolver(),new PushResolver(),o);}
    private static String signature(MatchTimeline t){
        StringBuilder b=new StringBuilder().append(t.getDurationSeconds()).append('|');
        for(MatchEvent e:t.getEvents())b.append(e.getTimeSeconds()).append(':').append(e.getType()).append(':').append(e.getCombatSource()).append(':').append(e.getStructureActionSource()).append(':').append(e.getStructureLane()).append(':').append(e.getStructureTowerTier()).append(':').append(e.getOuterTurretSiege()).append(':').append(e.getMatchPhaseChange()).append(';');
        for(MatchSnapshot s:t.getSnapshots())b.append(s.getTimeSeconds()).append(':').append(s.getBlueKills()).append(':').append(s.getRedKills()).append(':').append(s.getBlueGold()).append(':').append(s.getRedGold()).append(':').append(s.getLanePhase()).append(':').append(s.getObjectivePriority()).append(';');
        return b.toString();
    }
    private static void audit(Scenario s){
        Team b1=s.team(TeamSide.BLUE),b2=s.team(TeamSide.BLUE),r=s.team(TeamSide.RED);
        if(b1==b2||b1==r)throw new IllegalStateException("shared Team "+s.key);
        for(Position p:Position.values())if(find(b1,p).getAttributes()==find(b2,p).getAttributes())throw new IllegalStateException("shared attributes "+s.key+" "+p);
        System.out.println("AUDIT key="+s.key+" blue="+profileLine(s.blue)+" red="+profileLine(s.red)+" freshTeams=true freshAttributes=true mirror="+s.key.endsWith("_MIRROR"));
    }
    private static Player find(Team t,Position p){return t.getPlayers().stream().filter(x->x.getPosition()==p).findFirst().orElseThrow();}
    private static String profileLine(Map<Position,Profile> map){StringJoiner j=new StringJoiner(",");for(Position p:Position.values())j.add(p+"="+map.get(p));return j.toString();}
    private static void delta(String key,Map<String,Report> reports){Report on=reports.get(key+":true"),off=reports.get(key+":false");System.out.println("DELTA key="+key+" sieges="+on.sieges+" outer="+(on.outerDestroyed-off.outerDestroyed)+" earlyMid="+(on.allOpenTransitions-off.allOpenTransitions)+" laneCombat="+(on.combatAttempts.get(MatchEventType.LANE_COMBAT)-off.combatAttempts.get(MatchEventType.LANE_COMBAT))+" gank="+(on.combatAttempts.get(MatchEventType.JUNGLE_GANK)-off.combatAttempts.get(MatchEventType.JUNGLE_GANK))+" roam="+(on.combatAttempts.get(MatchEventType.ROAM)-off.combatAttempts.get(MatchEventType.ROAM))+" blueWins="+(on.blueWins-off.blueWins)+" avgDuration="+f(on.avgDuration()-off.avgDuration()));}

    private static List<Scenario> scenarios(){
        Map<Position,Profile> eq=all(Profile.equal());List<Scenario> result=new ArrayList<>();result.add(new Scenario("A",eq,eq));
        result.add(scenario("B",List.of(Position.TOP),Profile.laneStrong(),Profile.laneWeak()));
        result.add(scenario("C",List.of(Position.MID),Profile.laneStrong(),Profile.laneWeak()));
        result.add(scenario("D",List.of(Position.ADC,Position.SUPPORT),Profile.laneStrong(),Profile.laneWeak()));
        result.add(scenario("E",List.of(Position.TOP,Position.MID,Position.ADC,Position.SUPPORT),Profile.laneStrong(),Profile.laneWeak()));
        result.add(scenario("F",List.of(Position.TOP,Position.MID,Position.ADC,Position.SUPPORT),Profile.farmingStrong(),Profile.farmingWeak()));
        result.add(scenario("G",List.of(Position.values()),Profile.actionStrong(),Profile.actionWeak()));return result;
    }
    private static Scenario scenario(String key,List<Position> positions,Profile blue,Profile red){Map<Position,Profile>b=all(Profile.equal()),r=all(Profile.equal());positions.forEach(p->{b.put(p,blue);r.put(p,red);});return new Scenario(key,b,r);}
    private static EnumMap<Position,Profile> all(Profile p){EnumMap<Position,Profile>m=new EnumMap<>(Position.class);for(Position x:Position.values())m.put(x,p);return m;}
    private record Profile(int mechanics,int aggression,int farming,int teamfighting){
        static Profile equal(){return new Profile(14,14,14,14);}static Profile laneStrong(){return new Profile(18,14,18,14);}static Profile laneWeak(){return new Profile(10,14,10,14);}static Profile farmingStrong(){return new Profile(14,14,18,14);}static Profile farmingWeak(){return new Profile(14,14,10,14);}static Profile actionStrong(){return new Profile(18,18,14,18);}static Profile actionWeak(){return new Profile(10,10,14,10);}
        public String toString(){return mechanics+"/"+aggression+"/"+farming+"/"+teamfighting;}
    }
    private record Scenario(String key,Map<Position,Profile>blue,Map<Position,Profile>red){
        Scenario mirror(){return new Scenario(key+"_MIRROR",new EnumMap<>(red),new EnumMap<>(blue));}
        Team team(TeamSide side){Map<Position,Profile> map=side==TeamSide.BLUE?blue:red;List<Player>players=new ArrayList<>();for(Position position:Position.values()){Profile p=map.get(position);players.add(new Player(key+"_"+side+"_"+position,position,new PlayerAttributes(p.mechanics,p.aggression,p.farming,p.teamfighting)));}return new Team(key+"_"+side,players);}
    }

    private static final class Report {
        final String key;final boolean enabled;final int games;
        final Map<Integer,TimeSum> times=new LinkedHashMap<>();
        final EnumMap<TeamSide,Long>siegeSides=counts(TeamSide.class);
        final EnumMap<Lane,Long>siegeLanes=counts(Lane.class),destroyedLanes=counts(Lane.class),openCounts=counts(Lane.class);
        final EnumMap<StructureActionSource,Long>destroyedSources=counts(StructureActionSource.class);
        final EnumMap<TeamSide,Long>destroyedOwners=counts(TeamSide.class),firstSides=counts(TeamSide.class);
        final EnumMap<Lane,Long>firstLanes=counts(Lane.class);
        final EnumMap<MatchEventType,Long>combatAttempts=counts(MatchEventType.class);
        final EnumMap<CombatSource,Long>combatKills=counts(CombatSource.class);
        final List<Integer>firstTimes=new ArrayList<>(),durations=new ArrayList<>();
        long evaluations,sieges,rolls,below,phaseIneligible,attackerDead,attackerActivity,targetDestroyed;
        double totalDamage,totalPressure;long absentBonus,supportBonus,minClamp,maxClamp;
        long outerDestroyed,timeLimitTransitions,allOpenTransitions,phaseEvents,forcedOpen,openBefore14,aliveAt14;
        long positiveDecay,negativeDecay,nearNeutral,laneCombatExcluded,gankExcluded,roamOriginExcluded,roamTargetExcluded;
        long supportCsErrors,majorDup,structureDup,rewardDup,orderViolation,stateMismatch,offMutation,replayMismatch,diagnosticsMismatch;
        long blueWins,redWins,timeouts,kills,totalGold,dragonAttempts,baronAttempts,objectiveCaptures;
        Report(String key,boolean enabled,int games){this.key=key;this.enabled=enabled;this.games=games;for(int time:TIMES)times.put(time,new TimeSum());}
        void add(MatchSimulator.SimulationResult result){
            MatchTimeline timeline=result.timeline();durations.add(timeline.getDurationSeconds());if(result.winnerSide()==TeamSide.BLUE)blueWins++;if(result.winnerSide()==TeamSide.RED)redWins++;if(result.endReason()==GameEndReason.SIMULATION_TIMEOUT)timeouts++;
            for(int time:TIMES)times.get(time).add(snapshotAt(timeline,time));
            MatchSnapshot last=timeline.getSnapshots().getLast();kills+=last.getBlueKills()+last.getRedKills();totalGold+=last.getBlueGold()+last.getRedGold();
            for(PlayerSnapshot p:last.getPlayerSnapshots())if(p.getPosition()==Position.SUPPORT&&p.getCs()!=0)supportCsErrors++;
            MatchSnapshot at14=snapshotAt(timeline,840);for(var lane:at14.getLanePhase().lanes()){if(lane.blueOuter().alive())aliveAt14++;if(lane.redOuter().alive())aliveAt14++;}
            var stats=result.lanePhaseExecutionStats();evaluations+=stats.evaluationTicks();sieges+=stats.actualSieges();rolls+=stats.siegeRandomRolls();below+=stats.pressureBelowThreshold();phaseIneligible+=stats.lanePhaseIneligible();attackerDead+=stats.attackerDead();attackerActivity+=stats.attackerActivityIneligible();targetDestroyed+=stats.targetAlreadyDestroyed();positiveDecay+=stats.positivePressureDecays();negativeDecay+=stats.negativePressureDecays();nearNeutral+=stats.pressureNearNeutral();laneCombatExcluded+=stats.laneCombatExcluded();gankExcluded+=stats.jungleGankExcluded();roamOriginExcluded+=stats.roamOriginExcluded();roamTargetExcluded+=stats.roamTargetExcluded();
            for(OuterTurretSiegeData d:stats.sieges()){siegeSides.merge(d.attackingSide(),1L,Long::sum);siegeLanes.merge(d.lane(),1L,Long::sum);totalDamage+=d.finalDamage();totalPressure+=Math.abs(d.lanePressure());if(d.defenderAbsentBonus()>0)absentBonus++;if(d.botSupportBonus()>0)supportBonus++;if(close(d.finalDamage(),LanePhaseRuleConfig.MIN_OUTER_SIEGE_DAMAGE))minClamp++;if(close(d.finalDamage(),LanePhaseRuleConfig.MAX_OUTER_SIEGE_DAMAGE))maxClamp++;}
            Map<Integer,Integer>majorAt=new HashMap<>();MatchEvent firstOuter=null;Set<String>structureKeys=new HashSet<>();
            for(MatchEvent e:timeline.getEvents()){
                if(Set.of(MatchEventType.LANE_COMBAT,MatchEventType.JUNGLE_GANK,MatchEventType.COUNTER_GANK,MatchEventType.ROAM,MatchEventType.TEAMFIGHT).contains(e.getType())){combatAttempts.merge(e.getType(),1L,Long::sum);majorAt.merge(e.getTimeSeconds(),1,Integer::sum);}
                if(e.getType()==MatchEventType.KILL&&e.getCombatSource()!=null)combatKills.merge(e.getCombatSource(),1L,Long::sum);
                if(e.getType()==MatchEventType.TOWER&&e.getStructureTowerTier()==TowerTier.OUTER){
                    outerDestroyed++;destroyedOwners.merge(e.getStructureDefendingSide(),1L,Long::sum);destroyedLanes.merge(e.getStructureLane(),1L,Long::sum);destroyedSources.merge(e.getStructureActionSource(),1L,Long::sum);
                    String identity=e.getStructureDefendingSide()+":"+e.getStructureLane()+":OUTER";if(!structureKeys.add(identity))structureDup++;if(firstOuter==null)firstOuter=e;
                }
                if(e.getType()==MatchEventType.MATCH_PHASE_CHANGE){phaseEvents++;if(e.getMatchPhaseChange().reason()==MidGameTransitionReason.TIME_LIMIT)timeLimitTransitions++;else allOpenTransitions++;forcedOpen+=e.getMatchPhaseChange().forcedOpenLanes().size();}
                if(e.getObjectivePriorityDecision()!=null){if(e.getObjectivePriorityDecision().objectiveType()==ObjectiveType.DRAGON&&e.getObjectivePriorityDecision().generalAttempt())dragonAttempts++;if(e.getObjectivePriorityDecision().objectiveType()==ObjectiveType.BARON&&e.getObjectivePriorityDecision().generalAttempt())baronAttempts++;objectiveCaptures++;}
            }
            majorDup+=majorAt.values().stream().filter(v->v>1).count();
            if(firstOuter!=null){firstSides.merge(firstOuter.getStructureAttackingSide(),1L,Long::sum);firstLanes.merge(firstOuter.getStructureLane(),1L,Long::sum);firstTimes.add(firstOuter.getTimeSeconds());}
            for(Lane lane:Lane.values()){int opened=firstOpen(timeline,lane);if(opened>=0){openCounts.merge(lane,1L,Long::sum);if(opened<840)openBefore14++;}}
            for(MatchSnapshot snapshot:timeline.getSnapshots())for(var lane:snapshot.getLanePhase().lanes())for(var tower:List.of(lane.blueOuter(),lane.redOuter())){if(tower.remainingIntegrity()<0||tower.remainingIntegrity()>100)stateMismatch++;if(tower.alive()&&tower.remainingIntegrity()<=0)stateMismatch++;if(!tower.alive()&&tower.remainingIntegrity()>0)stateMismatch++;}
            if(!enabled&&(stats.actualSieges()!=0||stats.positivePressureDecays()!=0||stats.negativePressureDecays()!=0||phaseEvents>0))offMutation++;
        }
        String line(){return "REPORT key="+key+" mode="+(enabled?"ON":"OFF")+" seeds="+games+" pressure="+times+" siege={eval="+evaluations+",actual="+sieges+",side="+siegeSides+",lane="+siegeLanes+",rolls="+rolls+",avgPressure="+avg(totalPressure,sieges)+",avgDamage="+avg(totalDamage,sieges)+",totalDamage="+f(totalDamage)+",absent="+absentBonus+",support="+supportBonus+",clamp="+minClamp+"/"+maxClamp+",below="+below+",phaseIneligible="+phaseIneligible+",attacker="+attackerDead+"/"+attackerActivity+",destroyedTarget="+targetDestroyed+"} outer={count="+outerDestroyed+",owner="+destroyedOwners+",lane="+destroyedLanes+",source="+destroyedSources+",firstSide="+firstSides+",firstLane="+firstLanes+",firstTime="+percentiles(firstTimes)+",aliveAt14="+aliveAt14+"} phase={open="+openCounts+",before14="+openBefore14+",time="+timeLimitTransitions+",all="+allOpenTransitions+",events="+phaseEvents+",forced="+forcedOpen+",decay="+positiveDecay+"/"+negativeDecay+"/"+nearNeutral+"} combat={attempt="+combatAttempts+",kills="+combatKills+",excluded="+laneCombatExcluded+"/"+gankExcluded+"/"+roamOriginExcluded+"/"+roamTargetExcluded+"} objective={dragonAttempt="+dragonAttempts+",baronAttempt="+baronAttempts+",captures="+objectiveCaptures+"} result={blue="+pct(blueWins,games)+",red="+pct(redWins,games)+",duration="+f(avgDuration())+"/"+percentile(durations,.5)+"/"+percentile(durations,.9)+"/"+percentile(durations,.95)+",40m="+durations.stream().filter(x->x>=2400).count()+",60m="+durations.stream().filter(x->x>=3600).count()+",timeout="+timeouts+",kills="+kills+",gold="+totalGold+"} integrity={supportCs="+supportCsErrors+",majorDup="+majorDup+",structureDup="+structureDup+",rewardDup="+rewardDup+",order="+orderViolation+",state="+stateMismatch+",offMutation="+offMutation+",replay="+replayMismatch+",diagnostics="+diagnosticsMismatch+"}";}
        double avgDuration(){return durations.stream().mapToInt(x->x).average().orElse(0);}
    }
    private static final class TimeSum {
        long n;double top,mid,bot,dragon,baron,blueIntegrity,redIntegrity,topCs,midCs,adcCs,blueGold,redGold;long open;
        void add(MatchSnapshot s){n++;for(LaneSnapshot lane:s.getLaneSnapshots()){if(lane.lane()==Lane.TOP)top+=lane.pressure();if(lane.lane()==Lane.MID)mid+=lane.pressure();if(lane.lane()==Lane.BOT)bot+=lane.pressure();}dragon+=s.getObjectivePriority().dragonSignedPriority();baron+=s.getObjectivePriority().baronSignedPriority();for(var lane:s.getLanePhase().lanes()){blueIntegrity+=lane.blueOuter().remainingIntegrity();redIntegrity+=lane.redOuter().remainingIntegrity();if(lane.phase()==LanePhase.OPEN)open++;}for(PlayerSnapshot p:s.getPlayerSnapshots())if(p.getTeamSide()==TeamSide.BLUE){if(p.getPosition()==Position.TOP)topCs+=p.getCs();if(p.getPosition()==Position.MID)midCs+=p.getCs();if(p.getPosition()==Position.ADC)adcCs+=p.getCs();}blueGold+=s.getBlueGold();redGold+=s.getRedGold();}
        public String toString(){return "P("+avg(top,n)+"/"+avg(mid,n)+"/"+avg(bot,n)+") O("+avg(blueIntegrity,n*3)+"/"+avg(redIntegrity,n*3)+",open="+avg(open,n)+") CS("+avg(topCs,n)+"/"+avg(midCs,n)+"/"+avg(adcCs,n)+") Pri("+avg(dragon,n)+"/"+avg(baron,n)+") G("+avg(blueGold,n)+"/"+avg(redGold,n)+")";}
    }
    private static int firstOpen(MatchTimeline timeline,Lane lane){for(MatchSnapshot s:timeline.getSnapshots())for(var l:s.getLanePhase().lanes())if(l.lane()==lane&&l.phase()==LanePhase.OPEN)return s.getTimeSeconds();return -1;}
    private static MatchSnapshot snapshotAt(MatchTimeline t,int time){MatchSnapshot found=t.getSnapshots().getFirst();for(MatchSnapshot s:t.getSnapshots()){if(s.getTimeSeconds()>time)break;found=s;}return found;}
    private static <E extends Enum<E>>EnumMap<E,Long>counts(Class<E>type){EnumMap<E,Long>m=new EnumMap<>(type);for(E e:type.getEnumConstants())m.put(e,0L);return m;}
    private static String percentiles(List<Integer>v){return v.isEmpty()?"-":percentile(v,.5)+"/"+percentile(v,.9)+"/"+f(v.stream().mapToInt(x->x).average().orElse(0));}
    private static int percentile(List<Integer>v,double p){if(v.isEmpty())return 0;List<Integer>x=new ArrayList<>(v);Collections.sort(x);return x.get(Math.min(x.size()-1,(int)Math.ceil(x.size()*p)-1));}
    private static String avg(double v,long n){return f(n==0?0:v/n);}private static String f(double v){return String.format(Locale.ROOT,"%.3f",v);}private static String pct(long n,long d){return f(d==0?0:n*100.0/d)+"%";}private static boolean close(double a,double b){return Math.abs(a-b)<1e-9;}
}
