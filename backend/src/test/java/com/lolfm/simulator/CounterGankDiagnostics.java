package com.lolfm.simulator;

import com.lolfm.domain.*;
import java.util.*;

/** Structured counter-gank diagnostics. Event message/description is never read. */
public final class CounterGankDiagnostics {
    private static final int CHECKPOINT = 840;

    public static void main(String[] args) {
        System.out.println("COUNTER_GANK A-G ON/OFF seeds=1..1000 (structured fields only)");
        for (Scenario scenario : Scenario.base()) {
            Summary on = run(scenario, 1_000, true, true);
            Summary off = run(scenario, 1_000, false, false);
            print(scenario.name(), "ON", on);
            print(scenario.name(), "OFF", off);
            printDelta(scenario.name(), on, off);
        }
        System.out.println("COUNTER_GANK MIRROR B-G seeds=1..500");
        for (Scenario scenario : Scenario.mirrors()) print(scenario.name(), "ON", run(scenario, 500, true, true));
        System.out.println("LIMIT: same-seed ON/OFF is a population comparison, not an event-by-event counterfactual; response/outcome rolls change the shared Random stream.");
    }

    private static Summary run(Scenario scenario, int runs, boolean counterEnabled, boolean replay) {
        Summary summary = new Summary(runs);
        for (long seed = 1; seed <= runs; seed++) {
            MatchSimulator.SimulationResult result = simulator(counterEnabled)
                    .simulateWithDiagnostics(scenario.blueTeam(), scenario.redTeam(), seed);
            MatchTimeline timeline = result.timeline();
            summary.collect(timeline, result.combatExecutionStats());
            if (replay) {
                MatchTimeline again = simulator(counterEnabled).simulate(scenario.blueTeam(), scenario.redTeam(), seed);
                if (!signature(timeline).equals(signature(again))) summary.replayMismatch++;
            }
        }
        return summary;
    }

    private static void print(String name, String mode, Summary s) {
        System.out.printf(Locale.ROOT,
                "%s %s GANK trigger[B/R]=%d/%d attempt[B/R]=%d/%d target[T/M/B]=%d/%d/%d normal=%d counterReplaced=%d%n",
                name,mode,s.triggerB,s.triggerR,s.attemptB,s.attemptR,s.target.get(Lane.TOP),s.target.get(Lane.MID),
                s.target.get(Lane.BOT),s.normalGanks,s.counters);
        System.out.printf(Locale.ROOT,
                "%s %s EXEC @14m jungleEval=%d triggerFail=%d jungleAttempt=%d counter=%d laneCalls=%d laneTriggers=%d laneAttempts=%d laneKills=%d skirmCalls=%d skirmKills=%d%n",
                name,mode,s.jungleEvaluations,s.jungleTriggerFailures,s.jungleAttempts,s.counterExecutionAttempts,
                s.laneResolverCalls,s.laneTriggeredLanes,s.laneAttempts,s.laneKills,s.genericCalls,s.genericKills);
        System.out.printf(Locale.ROOT,
                "%s %s ELIG eligible=%d deadJg=%d cooldown=%d deadLane=%d responseRoll=%d success/fail=%d/%d rate=%.4f initial[T succ/rate]=%d/%d/%.4f initial[F succ/rate]=%d/%d/%.4f avgChance=%.4f avgOver=%.3f%n",
                name,mode,s.eligible,s.deadJg,s.cooldown,s.deadLane,s.responseRoll,s.responseSuccess,
                s.responseRoll-s.responseSuccess,s.rate(s.responseSuccess,s.responseRoll),
                s.initialTrue,s.initialTrueSuccess,s.rate(s.initialTrueSuccess,s.initialTrue),
                s.initialFalse,s.initialFalseSuccess,s.rate(s.initialFalseSuccess,s.initialFalse),
                s.avg(s.responseChance,s.responseRoll),s.avg(s.overextension,s.responseRoll));
        System.out.printf(Locale.ROOT,
                "%s %s COUNTER total=%d lane[T/M/B]=%d/%d/%d outcome[N/A/D]=%d/%d/%d attackerWinRate=%.4f kill[B/R]=%d/%d avg[edge/dec/win]=%.3f/%.4f/%.4f pressureBefore/After=%.3f/%.3f%n",
                name,mode,s.counters,s.counterLane.get(Lane.TOP),s.counterLane.get(Lane.MID),s.counterLane.get(Lane.BOT),
                s.noKill,s.attackKill,s.defendKill,s.rate(s.attackKill,s.attackKill+s.defendKill),s.killB,s.killR,
                s.avg(s.edge,s.counters),s.avg(s.decisive,s.counters),s.avg(s.winChance,s.counters),
                s.avg(s.pressureBefore,s.counters),s.avg(s.pressureAfter,s.counters));
        System.out.printf(Locale.ROOT,
                "%s %s PARTICIPANT soloKiller[J/L]=%d/%d soloVictim[J/L]=%d/%d botKiller[J/A/S]=%d/%d/%d botVictim[J/A/S]=%d/%d/%d assists[ok/missing/wrong/dup]=%d/%d/%d/%d%n",
                name,mode,s.soloJgKiller,s.soloLaneKiller,s.soloJgVictim,s.soloLaneVictim,
                s.botJgKiller,s.botAdcKiller,s.botSupKiller,s.botJgVictim,s.botAdcVictim,s.botSupVictim,
                s.assistOk,s.assistMissing,s.wrongAssist,s.duplicateAssist);
        System.out.printf(Locale.ROOT,
                "%s %s JG_COST blocks[A/D]=%d/%d blockedTicks[A/D]=%d/%d missedCS[A/D]=%.2f/%.2f missedGold[A/D]=%.2f/%.2f outcomeCost[N/A/D]=%d/%d/%d overlap=%d duplicateLoss=%d invalidFarm=%d resumeFail=%d%n",
                name,mode,s.attackBlocks,s.defendBlocks,s.attackBlockedTicks,s.defendBlockedTicks,
                s.attackMissedCs,s.defendMissedCs,s.attackMissedCs*PositionEconomyRuleConfig.CS_GOLD,
                s.defendMissedCs*PositionEconomyRuleConfig.CS_GOLD,s.noKillCosts,s.attackWinCosts,s.defendWinCosts,
                s.recoveryOverlap,s.duplicateLoss,s.invalidFarm,s.resumeFailure);
        for (int time : List.of(600,840)) {
            Checkpoint c=s.checkpoints.get(time);
            System.out.printf(Locale.ROOT,
                    "%s %s @%d JG CS[B/R/gap]=%.2f/%.2f/%.2f farmGold[B/R]=%.2f/%.2f totalGold[B/R]=%.2f/%.2f deaths[B/R]=%.3f/%.3f LINE_CS T[B/R]=%.2f/%.2f M[B/R]=%.2f/%.2f A[B/R]=%.2f/%.2f%n",
                    name,mode,time,c.cs(TeamSide.BLUE,Position.JUNGLE,s.runs),c.cs(TeamSide.RED,Position.JUNGLE,s.runs),
                    c.gap(Position.JUNGLE,s.runs),c.cs(TeamSide.BLUE,Position.JUNGLE,s.runs)*20,
                    c.cs(TeamSide.RED,Position.JUNGLE,s.runs)*20,c.gold(TeamSide.BLUE,Position.JUNGLE,s.runs),
                    c.gold(TeamSide.RED,Position.JUNGLE,s.runs),c.deaths(TeamSide.BLUE,Position.JUNGLE,s.runs),
                    c.deaths(TeamSide.RED,Position.JUNGLE,s.runs),c.cs(TeamSide.BLUE,Position.TOP,s.runs),
                    c.cs(TeamSide.RED,Position.TOP,s.runs),c.cs(TeamSide.BLUE,Position.MID,s.runs),
                    c.cs(TeamSide.RED,Position.MID,s.runs),c.cs(TeamSide.BLUE,Position.ADC,s.runs),
                    c.cs(TeamSide.RED,Position.ADC,s.runs));
            System.out.printf(Locale.ROOT,
                    "%s %s @%d PRESS T/M/B=%.3f/%.3f/%.3f priority B/N/R T=%.3f/%.3f/%.3f M=%.3f/%.3f/%.3f B=%.3f/%.3f/%.3f%n",
                    name,mode,time,c.pressure(Lane.TOP,s.runs),c.pressure(Lane.MID,s.runs),c.pressure(Lane.BOT,s.runs),
                    c.priority(Lane.TOP,LanePriority.BLUE,s.runs),c.priority(Lane.TOP,LanePriority.NEUTRAL,s.runs),c.priority(Lane.TOP,LanePriority.RED,s.runs),
                    c.priority(Lane.MID,LanePriority.BLUE,s.runs),c.priority(Lane.MID,LanePriority.NEUTRAL,s.runs),c.priority(Lane.MID,LanePriority.RED,s.runs),
                    c.priority(Lane.BOT,LanePriority.BLUE,s.runs),c.priority(Lane.BOT,LanePriority.NEUTRAL,s.runs),c.priority(Lane.BOT,LanePriority.RED,s.runs));
        }
        System.out.printf(Locale.ROOT,
                "%s %s VICTIM total=%d pos[T/J/M/A/S]=%d/%d/%d/%d/%d avgMissed[ticks/cs/gold]=%.3f/%.3f/%.2f%n",
                name,mode,s.victims,s.victimPos.get(Position.TOP),s.victimPos.get(Position.JUNGLE),
                s.victimPos.get(Position.MID),s.victimPos.get(Position.ADC),s.victimPos.get(Position.SUPPORT),
                s.avg(s.victimMissedTicks,s.victims),s.avg(s.victimMissedCs,s.victims),
                s.avg(s.victimMissedCs*20,s.victims));
        System.out.printf(Locale.ROOT,
                "%s %s KILLS Counter/Gank/Lane/Skirm/Team/Obj/Other=%d/%d/%d/%d/%d/%d/%d INTEGRITY overlapEvent=%d multiCombat=%d deadParticipant=%d multiDeath=%d duplicateReward=%d cooldownViolation=%d repeatMissing=%d ineligibleCounter=%d outcomePreconsume=%d supportCs=%d replayMismatch=%d%n",
                name,mode,s.kills.get(CombatSource.COUNTER_GANK),s.kills.get(CombatSource.JUNGLE_GANK),
                s.kills.get(CombatSource.LANE_COMBAT),s.kills.get(CombatSource.SKIRMISH),
                s.kills.get(CombatSource.TEAMFIGHT),s.kills.get(CombatSource.OBJECTIVE_FIGHT),
                s.kills.get(CombatSource.OTHER),s.overlapEvent,s.multiCombat,s.deadParticipant,s.multiDeath,
                s.duplicateReward,s.cooldownViolation,s.repeatMissing,s.ineligibleCounter,s.outcomePreconsume,
                s.supportCs,s.replayMismatch);
        System.out.printf(Locale.ROOT,
                "%s %s END win[B/R]=%.4f/%.4f duration[avg/median/p90/p95]=%.1f/%d/%d/%d over40=%.4f over60=%.4f timeout=%d%n",
                name,mode,s.blueWins/(double)s.runs,s.redWins/(double)s.runs,s.duration/(double)s.runs,
                s.percentile(.50),s.percentile(.90),s.percentile(.95),s.over(2400),s.over(3600),s.timeouts);
    }

    private static void printDelta(String name, Summary on, Summary off) {
        System.out.printf(Locale.ROOT,
                "%s ON-OFF counter=%d normalGank=%d kills[C/G/L/S/T/O/X]=%d/%d/%d/%d/%d/%d/%d JG14CS[B/R]=%.2f/%.2f teamGoldGap14=%.2f winB=%.4f duration=%.1f%n",
                name,on.counters,on.normalGanks-off.normalGanks,
                on.kills.get(CombatSource.COUNTER_GANK)-off.kills.get(CombatSource.COUNTER_GANK),
                on.kills.get(CombatSource.JUNGLE_GANK)-off.kills.get(CombatSource.JUNGLE_GANK),
                on.kills.get(CombatSource.LANE_COMBAT)-off.kills.get(CombatSource.LANE_COMBAT),
                on.kills.get(CombatSource.SKIRMISH)-off.kills.get(CombatSource.SKIRMISH),
                on.kills.get(CombatSource.TEAMFIGHT)-off.kills.get(CombatSource.TEAMFIGHT),
                on.kills.get(CombatSource.OBJECTIVE_FIGHT)-off.kills.get(CombatSource.OBJECTIVE_FIGHT),
                on.kills.get(CombatSource.OTHER)-off.kills.get(CombatSource.OTHER),
                on.checkpoints.get(840).cs(TeamSide.BLUE,Position.JUNGLE,on.runs)-off.checkpoints.get(840).cs(TeamSide.BLUE,Position.JUNGLE,off.runs),
                on.checkpoints.get(840).cs(TeamSide.RED,Position.JUNGLE,on.runs)-off.checkpoints.get(840).cs(TeamSide.RED,Position.JUNGLE,off.runs),
                on.goldGap14/on.runs-off.goldGap14/off.runs,
                on.blueWins/(double)on.runs-off.blueWins/(double)off.runs,
                on.duration/(double)on.runs-off.duration/(double)off.runs);
    }

    private static MatchSimulator simulator(boolean counter) {
        return new MatchSimulator(new TeamfightResolver(),new EndGameEvaluator(),new SnapshotFactory(),
                new ObjectiveResolver(),new PostFightResolver(),new ObjectiveAttemptResolver(),
                new StructureResolver(),new PushResolver(),true,true,true,counter);
    }

    private static final class Summary {
        final int runs;
        long jungleEvaluations,jungleTriggerFailures,jungleAttempts,counterExecutionAttempts;
        long laneResolverCalls,laneTriggeredLanes,laneAttempts,laneKills,genericCalls,genericKills;
        int triggerB,triggerR,attemptB,attemptR,normalGanks,counters;
        final EnumMap<Lane,Integer> target=ints(Lane.class),counterLane=ints(Lane.class);
        int eligible,deadJg,cooldown,deadLane,responseRoll,responseSuccess,initialTrue,initialTrueSuccess,initialFalse,initialFalseSuccess;
        double responseChance,overextension,edge,decisive,winChance,pressureBefore,pressureAfter;
        int noKill,attackKill,defendKill,killB,killR;
        int soloJgKiller,soloLaneKiller,soloJgVictim,soloLaneVictim;
        int botJgKiller,botAdcKiller,botSupKiller,botJgVictim,botAdcVictim,botSupVictim;
        int assistOk,assistMissing,wrongAssist,duplicateAssist;
        int attackBlocks,defendBlocks,attackBlockedTicks,defendBlockedTicks,recoveryOverlap,duplicateLoss,invalidFarm,resumeFailure;
        double attackMissedCs,defendMissedCs;
        int noKillCosts,attackWinCosts,defendWinCosts,victims,victimMissedTicks; double victimMissedCs;
        final EnumMap<Position,Integer> victimPos=ints(Position.class);
        final EnumMap<CombatSource,Integer> kills=ints(CombatSource.class);
        int overlapEvent,multiCombat,deadParticipant,multiDeath,duplicateReward,cooldownViolation,repeatMissing,ineligibleCounter,outcomePreconsume,supportCs,replayMismatch;
        int blueWins,redWins,timeouts; long duration; double goldGap14; final List<Integer>durations=new ArrayList<>();
        final Map<Integer,Checkpoint>checkpoints=Map.of(600,new Checkpoint(),840,new Checkpoint());

        Summary(int runs){this.runs=runs;}

        void collect(MatchTimeline t, CombatExecutionStatsSnapshot execution) {
            jungleEvaluations += execution.jungleGankEvaluations();
            jungleTriggerFailures += execution.jungleGankAllTriggersFailed();
            jungleAttempts += execution.jungleGankAttempts();
            counterExecutionAttempts += execution.counterGankAttempts();
            laneResolverCalls += execution.laneCombatResolverCalls();
            laneTriggeredLanes += execution.laneCombatTriggeredLanes();
            laneAttempts += execution.laneCombatAttempts();
            laneKills += execution.laneCombatKills();
            genericCalls += execution.genericSkirmishCalls();
            genericKills += execution.genericSkirmishKills();
            MatchSnapshot s10=at(t,600),s14=at(t,840),end=t.getSnapshots().getLast();
            checkpoints.get(600).add(s10);checkpoints.get(840).add(s14);
            goldGap14+=Math.abs(s14.getBlueGold()-s14.getRedGold());
            if(end.getPlayerSnapshots().stream().filter(p->p.getPosition()==Position.SUPPORT).anyMatch(p->p.getCs()!=0))supportCs++;
            Map<Integer,Integer> majors=new HashMap<>(),cg=new HashMap<>(),jg=new HashMap<>();
            Map<TeamSide,Integer>lastAction=new EnumMap<>(TeamSide.class);
            Set<String> rewards=new HashSet<>();
            for(MatchEvent e:t.getEvents()){
                if(e.getTimeSeconds()>CHECKPOINT)continue;
                if(e.getType()==MatchEventType.JUNGLE_GANK) collectGank(e);
                if(e.getType()==MatchEventType.COUNTER_GANK) collectCounter(t,e,lastAction);
                if(e.getType()==MatchEventType.JUNGLE_GANK){majors.merge(e.getTimeSeconds(),1,Integer::sum);jg.merge(e.getTimeSeconds(),1,Integer::sum);recordAction(lastAction,e.getJungleGank().gankingSide(),e.getTimeSeconds());}
                else if(e.getType()==MatchEventType.COUNTER_GANK){majors.merge(e.getTimeSeconds(),1,Integer::sum);cg.merge(e.getTimeSeconds(),1,Integer::sum);}
                else if(e.getType()==MatchEventType.LANE_COMBAT||e.getType()==MatchEventType.TEAMFIGHT)majors.merge(e.getTimeSeconds(),1,Integer::sum);
                if(e.getType()==MatchEventType.KILL){
                    CombatSource source=e.getCombatSource()==null?CombatSource.OTHER:e.getCombatSource();kills.merge(source,1,Integer::sum);
                    if(source==CombatSource.SKIRMISH)majors.merge(e.getTimeSeconds(),1,Integer::sum);
                    if(!rewards.add(e.getTimeSeconds()+":"+e.getVictim()))duplicateReward++;
                }
            }
            overlapEvent+=(int)cg.keySet().stream().filter(jg::containsKey).count();
            multiCombat+=(int)majors.values().stream().filter(v->v>1).count();
            duration+=t.getDurationSeconds();durations.add(t.getDurationSeconds());
            if(t.getWinner()==null)timeouts++;else if(t.getWinner().equals("BLUE"))blueWins++;else redWins++;
        }

        void collectGank(MatchEvent e) {
            JungleGankData d=e.getJungleGank();normalGanks++;recordTrigger(d.blueTriggered(),d.redTriggered());
            if(d.gankingSide()==TeamSide.BLUE)attemptB++;else attemptR++;target.merge(d.targetLane(),1,Integer::sum);
            if(d.counterResponseRolled()){eligible++;responseRoll++;responseChance+=d.counterResponseChance();overextension+=d.enemyOverextension();collectInitial(d.defenderInitiallyTriggered(),false);}
            else switch(d.counterIneligibility()){
                case DEFENDING_JUNGLER_DEAD->deadJg++;
                case DEFENDING_JUNGLER_COOLDOWN->cooldown++;
                case LANE_PARTICIPANT_DEAD->deadLane++;
                default->{}
            }
        }

        void collectCounter(MatchTimeline t,MatchEvent e,Map<TeamSide,Integer>lastAction) {
            CounterGankData d=e.getCounterGank();counters++;eligible++;responseRoll++;responseSuccess++;
            recordTrigger(d.attackingSide()==TeamSide.BLUE||d.defenderInitiallyTriggered()&&d.defendingSide()==TeamSide.BLUE,
                    d.attackingSide()==TeamSide.RED||d.defenderInitiallyTriggered()&&d.defendingSide()==TeamSide.RED);
            if(d.attackingSide()==TeamSide.BLUE)attemptB++;else attemptR++;target.merge(d.targetLane(),1,Integer::sum);
            counterLane.merge(d.targetLane(),1,Integer::sum);responseChance+=d.responseChance();overextension+=d.enemyOverextension();
            collectInitial(d.defenderInitiallyTriggered(),true);edge+=d.combatEdge();decisive+=d.decisiveChance();winChance+=d.attackingSideWinChance();
            pressureBefore+=d.pressureBefore();pressureAfter+=d.pressureAfter();
            recordAction(lastAction,d.attackingSide(),e.getTimeSeconds());recordAction(lastAction,d.defendingSide(),e.getTimeSeconds());
            collectCost(t,e.getTimeSeconds(),d.attackingJunglerPlayerId(),true);
            collectCost(t,e.getTimeSeconds(),d.defendingJunglerPlayerId(),false);
            attackBlocks++;defendBlocks++;
            if(d.outcome()==CounterGankOutcome.NO_KILL){noKill++;noKillCosts++;}
            else{
                if(d.outcome()==CounterGankOutcome.ATTACKING_SIDE_KILL){attackKill++;attackWinCosts++;}else{defendKill++;defendWinCosts++;}
                if(d.winningSide()==TeamSide.BLUE)killB++;else killR++;
                collectParticipants(d);collectVictim(t,e.getTimeSeconds(),d.victimPlayerId());
                MatchSnapshot before=at(t,e.getTimeSeconds()-10),after=at(t,e.getTimeSeconds());
                int delta=after.getPlayerSnapshots().stream().mapToInt(PlayerSnapshot::getDeaths).sum()-before.getPlayerSnapshots().stream().mapToInt(PlayerSnapshot::getDeaths).sum();
                if(delta>1)multiDeath++;
                Set<String> ids=new HashSet<>(d.assistantPlayerIds());ids.add(d.killerPlayerId());
                for(String id:ids)if(!player(after,id).isAlive())deadParticipant++;
            }
        }

        void recordTrigger(boolean b,boolean r){if(b)triggerB++;if(r)triggerR++;}
        void collectInitial(boolean initial,boolean success){if(initial){initialTrue++;if(success)initialTrueSuccess++;}else{initialFalse++;if(success)initialFalseSuccess++;}}
        void recordAction(Map<TeamSide,Integer>last,TeamSide side,int time){Integer old=last.put(side,time);if(old!=null&&time-old<120)cooldownViolation++;}

        void collectParticipants(CounterGankData d){
            Position k=position(d.killerPlayerId()),v=position(d.victimPlayerId());
            if(d.targetLane()==Lane.BOT){if(k==Position.JUNGLE)botJgKiller++;else if(k==Position.ADC)botAdcKiller++;else botSupKiller++;
                if(v==Position.JUNGLE)botJgVictim++;else if(v==Position.ADC)botAdcVictim++;else botSupVictim++;}
            else{if(k==Position.JUNGLE)soloJgKiller++;else soloLaneKiller++;if(v==Position.JUNGLE)soloJgVictim++;else soloLaneVictim++;}
            int expected=d.targetLane()==Lane.BOT?2:1;if(d.assistantPlayerIds().size()==expected)assistOk++;else assistMissing++;
            if(d.assistantPlayerIds().stream().distinct().count()!=d.assistantPlayerIds().size())duplicateAssist++;
            if(d.assistantPlayerIds().stream().anyMatch(id->side(id)!=d.winningSide()))wrongAssist++;
        }

        void collectCost(MatchTimeline t,int time,String id,boolean attacking){
            int previous=player(at(t,time),id).getCs();
            for(int tick=time+10;tick<time+30;tick+=10){
                PlayerSnapshot p=player(at(t,tick),id);double expected=PositionEconomyRuleConfig.JUNGLE_BASE_CS_PER_MINUTE/6.0;
                if(p.isCanFarm()){if(attacking){attackBlockedTicks++;attackMissedCs+=expected;}else{defendBlockedTicks++;defendMissedCs+=expected;}if(p.getCs()!=previous)invalidFarm++;}
                else recoveryOverlap++;
                previous=p.getCs();
            }
        }

        void collectVictim(MatchTimeline t,int death,String id){
            victims++;Position position=position(id);victimPos.merge(position,1,Integer::sum);
            PlayerSnapshot victim=player(at(t,death),id);int ticks=0;
            for(int tick=death+10;tick<victim.getFarmResumeAtSeconds();tick+=10)ticks++;
            victimMissedTicks+=ticks;victimMissedCs+=ticks*base(position)/6.0;
        }
        double avg(double value,int count){return count==0?0:value/count;}double rate(int value,int count){return count==0?0:value/(double)count;}
        int percentile(double q){List<Integer>x=new ArrayList<>(durations);x.sort(Integer::compareTo);return x.get(Math.min(x.size()-1,(int)Math.ceil(q*x.size())-1));}
        double over(int seconds){return durations.stream().filter(v->v>=seconds).count()/(double)runs;}
    }

    private static final class Checkpoint {
        final EnumMap<TeamSide,EnumMap<Position,Long>>cs=new EnumMap<>(TeamSide.class),gold=new EnumMap<>(TeamSide.class),deaths=new EnumMap<>(TeamSide.class);
        final EnumMap<Position,Double>gaps=doubles(Position.class);final EnumMap<Lane,Double>pressure=doubles(Lane.class);
        final EnumMap<Lane,EnumMap<LanePriority,Integer>>priority=new EnumMap<>(Lane.class);
        Checkpoint(){for(TeamSide side:TeamSide.values()){cs.put(side,longs(Position.class));gold.put(side,longs(Position.class));deaths.put(side,longs(Position.class));}for(Lane lane:Lane.values())priority.put(lane,ints(LanePriority.class));}
        void add(MatchSnapshot s){for(PlayerSnapshot p:s.getPlayerSnapshots()){TeamSide side=p.getTeamName().equals("BLUE")?TeamSide.BLUE:TeamSide.RED;cs.get(side).merge(p.getPosition(),(long)p.getCs(),Long::sum);gold.get(side).merge(p.getPosition(),(long)p.getGold(),Long::sum);deaths.get(side).merge(p.getPosition(),(long)p.getDeaths(),Long::sum);}
            for(Position p:Position.values())gaps.merge(p,(double)Math.abs(value(s,TeamSide.BLUE,p).getCs()-value(s,TeamSide.RED,p).getCs()),Double::sum);
            for(LaneSnapshot l:s.getLaneSnapshots()){pressure.merge(l.lane(),l.pressure(),Double::sum);priority.get(l.lane()).merge(l.priority(),1,Integer::sum);}}
        double cs(TeamSide s,Position p,int runs){return cs.get(s).get(p)/(double)runs;}double gold(TeamSide s,Position p,int runs){return gold.get(s).get(p)/(double)runs;}double deaths(TeamSide s,Position p,int runs){return deaths.get(s).get(p)/(double)runs;}double gap(Position p,int runs){return gaps.get(p)/runs;}double pressure(Lane l,int runs){return pressure.get(l)/runs;}double priority(Lane l,LanePriority p,int runs){return priority.get(l).get(p)/(double)runs;}
        PlayerSnapshot value(MatchSnapshot s,TeamSide side,Position position){return s.getPlayerSnapshots().stream().filter(p->p.getTeamName().equals(side.name())&&p.getPosition()==position).findFirst().orElseThrow();}
    }

    private record Scenario(String name,Map<Position,Values>blue,Map<Position,Values>red){
        static List<Scenario>base(){return List.of(eq("A"),
                one("B",Position.JUNGLE,new Values(18,18,18),new Values(10,10,10)),
                one("C",Position.JUNGLE,new Values(14,18,14),new Values(14,10,14)),
                one("D",Position.JUNGLE,new Values(18,18,18),new Values(18,18,18)),
                top("E",new Values(14,14,14),new Values(14,14,14)),
                top("F",new Values(18,18,18),new Values(18,18,18)),
                top("G",new Values(18,18,18),new Values(10,10,10)));}
        static List<Scenario>mirrors(){List<Scenario>r=new ArrayList<>();for(Scenario s:base().subList(1,7))r.add(new Scenario(s.name+"_M",s.red,s.blue));return r;}
        static Scenario eq(String n){return new Scenario(n,Map.of(),Map.of());}
        static Scenario one(String n,Position p,Values b,Values r){return new Scenario(n,Map.of(p,b),Map.of(p,r));}
        static Scenario top(String n,Values bj,Values rj){return new Scenario(n,
                Map.of(Position.TOP,new Values(10,10,10),Position.JUNGLE,bj),
                Map.of(Position.TOP,new Values(18,18,18),Position.JUNGLE,rj));}
        Team blueTeam(){return team("BLUE",blue);}Team redTeam(){return team("RED",red);}
        static Team team(String side,Map<Position,Values>values){List<Player>players=new ArrayList<>();for(Position p:Position.values()){Values v=values.getOrDefault(p,new Values(14,14,14));players.add(new Player(side+"-"+p,p,new PlayerAttributes(v.mechanics,v.aggression,14,v.teamfighting)));}return new Team(side,players);}
    }
    private record Values(int mechanics,int aggression,int teamfighting){}

    private static MatchSnapshot at(MatchTimeline t,int time){MatchSnapshot result=t.getSnapshots().getFirst();for(MatchSnapshot s:t.getSnapshots()){if(s.getTimeSeconds()>time)break;result=s;}return result;}
    private static PlayerSnapshot player(MatchSnapshot s,String id){return s.getPlayerSnapshots().stream().filter(p->p.getPlayerName().equals(id)).findFirst().orElseThrow();}
    private static Position position(String id){return Position.valueOf(id.substring(id.indexOf('-')+1));}
    private static TeamSide side(String id){return id.startsWith("BLUE-")?TeamSide.BLUE:TeamSide.RED;}
    private static double base(Position p){return switch(p){case TOP->7;case JUNGLE->5.8;case MID->7.1;case ADC->7.2;case SUPPORT->0;};}
    private static String signature(MatchTimeline t){return t.getDurationSeconds()+":"+t.getWinner()+":"+t.getEvents().stream().map(e->e.getTimeSeconds()+":"+e.getType()+":"+e.getCombatSource()+":"+e.getJungleGank()+":"+e.getCounterGank()+":"+e.getKiller()+":"+e.getVictim()).toList()+":"+t.getSnapshots().stream().map(s->s.getTimeSeconds()+":"+s.getBlueGold()+":"+s.getRedGold()+":"+s.getLaneSnapshots()+":"+s.getPlayerSnapshots().stream().map(p->p.getPlayerName()+":"+p.getCs()+":"+p.getGold()+":"+p.getDeaths()).toList()).toList();}
    private static <E extends Enum<E>>EnumMap<E,Integer>ints(Class<E>c){EnumMap<E,Integer>m=new EnumMap<>(c);for(E e:c.getEnumConstants())m.put(e,0);return m;}
    private static <E extends Enum<E>>EnumMap<E,Long>longs(Class<E>c){EnumMap<E,Long>m=new EnumMap<>(c);for(E e:c.getEnumConstants())m.put(e,0L);return m;}
    private static <E extends Enum<E>>EnumMap<E,Double>doubles(Class<E>c){EnumMap<E,Double>m=new EnumMap<>(c);for(E e:c.getEnumConstants())m.put(e,0d);return m;}
}
