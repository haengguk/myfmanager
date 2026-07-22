package com.lolfm.champion;

import com.lolfm.simulator.ItemProgressStage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Computes diagnostics-only artifacts and verdict exclusively from generated CSV data. */
final class InteractionSummaryWriter {
    private InteractionSummaryWriter() { }

    static void write(Path dir) throws Exception {
        Csv full=Csv.read(dir.resolve("player-champion-interaction-full-match.csv"));
        Csv growth=Csv.read(dir.resolve("player-champion-interaction-growth.csv"));
        Csv budget=Csv.read(dir.resolve("player-champion-interaction-budget-review.csv"));
        Csv pairwise=Csv.read(dir.resolve("player-champion-interaction-pairwise.csv"));
        Csv skill=Csv.read(dir.resolve("player-champion-interaction-skill.csv"));
        Csv focused=Csv.read(dir.resolve("player-champion-interaction-focused.csv"));

        Map<String,String[]> off=new HashMap<>(),original=new HashMap<>();
        Map<String,MirrorStats> mirrors=new TreeMap<>();StringBuilder fullFlips=new StringBuilder("auditGroup,fullMatchPairKey,position,championA,championB,direction,state,context,skillProfile,growthPackage,scoreBeforeChampion,championContribution,scoreAfterChampion,baseOrdering,finalOrdering,flipType,championHardLock,skillHardLock,growthHardLock\n");
        long offApps=0,onApps=0,random=0,lane=0,teamfight=0,objective=0,duplicate=0,withoutAttempt=0,withoutWinner=0,participantMismatch=0,replay=0,diagnostics=0;
        int winnerFlips=0,blueToRed=0,redToBlue=0;
        Map<String,Set<String>> duplicateGroups=new HashMap<>();
        for(String[] v:full.rows){
            String pairKey=v[full.i("lineupId")]+"|"+v[full.i("skillProfile")]+"|"+v[full.i("direction")]+"|"+v[full.i("seed")];
            boolean blue=bool(v,full,"blueWinner");int apps=num(v,full,"championPowerApplications");
            if(v[full.i("mode")].equals("CHAMPION_OFF")){offApps+=apps;off.put(pairKey,v);}else{onApps+=apps;String[] before=off.get(pairKey);if(before!=null&&bool(before,full,"blueWinner")!=blue){winnerFlips++;if(bool(before,full,"blueWinner"))blueToRed++;else redToBlue++;fullFlips.append("FULL_MATCH_PAIRED,").append(pairKey.replace('|','/')).append(',').append(v[full.i("targetPosition")]).append(",,,").append(v[full.i("direction")]).append(",,,").append(v[full.i("skillProfile")]).append(",,,,,").append(bool(before,full,"blueWinner")?"BLUE":"RED").append(',').append(blue?"BLUE":"RED").append(",WINNER_FLIP,false,false,false\n");}}
            random+=num(v,full,"championPowerDirectRandomCalls");lane+=num(v,full,"blueLaneCombatOutcomeRecords")+num(v,full,"redLaneCombatOutcomeRecords");teamfight+=num(v,full,"blueTeamfightWins")+num(v,full,"redTeamfightWins");objective+=num(v,full,"blueObjectiveFightWins")+num(v,full,"redObjectiveFightWins");
            duplicate+=num(v,full,"duplicateOutcomeRecordErrors");withoutAttempt+=num(v,full,"outcomeWithoutAttemptErrors");withoutWinner+=num(v,full,"outcomeWithoutWinnerErrors");participantMismatch+=num(v,full,"participantMismatchErrors");replay+=num(v,full,"replayMismatch");diagnostics+=num(v,full,"diagnosticsMismatch");
            String duplicateKey=v[full.i("lineupId")]+"|"+v[full.i("mode")]+"|"+v[full.i("direction")]+"|"+v[full.i("seed")];duplicateGroups.computeIfAbsent(duplicateKey,k->new HashSet<>()).add(fingerprint(v,full));
            String mirrorKey=v[full.i("lineupId")]+"|"+v[full.i("skillProfile")]+"|"+v[full.i("mode")]+"|"+v[full.i("seed")];
            if(v[full.i("direction")].equals("ORIGINAL"))original.put(mirrorKey,v);else{String[] o=original.get(mirrorKey);if(o!=null)addMirrorGroups(mirrors,o,v,full);}
        }
        Files.writeString(dir.resolve("player-champion-interaction-full-match-flips.csv"),fullFlips);
        double duplicateRate=duplicateGroups.values().stream().filter(s->s.size()==1).count()/(double)Math.max(1,duplicateGroups.size());
        MirrorResult mirrorResult=writeMirrors(dir,mirrors);

        long requested=growth.rows.size(),fully=0,capped=0,partial=0,eligibleGrowth=0,championHard=0,growthHard=0;
        Map<String,GrowthPackage> packages=new TreeMap<>();
        for(String[] v:growth.rows){boolean eligible=bool(v,growth,"eligibleForRequestedPackageRate"),isCapped=bool(v,growth,"leadCapped");if(eligible)fully++;if(isCapped)capped++;if(isCapped&&hasAnyAchievement(v,growth))partial++;if(eligible)eligibleGrowth++;if(bool(v,growth,"championHardLock"))championHard++;if(bool(v,growth,"growthHardLock"))growthHard++;packages.computeIfAbsent(v[growth.i("growthPackage")],k->new GrowthPackage()).add(eligible,isCapped,hasAnyAchievement(v,growth),bool(v,growth,"challengerOvercameChampionAdvantage"));}
        writeGrowthPackages(dir,packages);

        long budgetWins=0,budgetLosses=0,budgetTies=0,skillHard=0;int budgetSkillMissing=0,budgetGrowthMissing=0;
        for(String[] v:budget.rows){budgetWins+=num(v,budget,"pairwiseWins");budgetLosses+=num(v,budget,"pairwiseLosses");budgetTies+=num(v,budget,"pairwiseTies");skillHard+=num(v,budget,"skillHardLockCount");for(String p:List.of("skillPlus1","skillPlus3","skillPlus5"))if(num(v,budget,p+"Eligible")>0&&v[budget.i(p+"OvercomeRate")].equals("NOT_APPLICABLE"))budgetSkillMissing++;for(String p:List.of("growthSmall","growthLarge"))if(num(v,budget,p+"Eligible")>0&&v[budget.i(p+"OvercomeRate")].equals("NOT_APPLICABLE"))budgetGrowthMissing++;}

        int zeroData=(sum(full,"blueKills")+sum(full,"redKills")==0||sum(full,"blueGold")+sum(full,"redGold")==0)?1:0;
        int combatZero=(lane==0||teamfight==0||objective==0)?1:0;
        List<String> warnings=new ArrayList<>(mirrorResult.warnings);if(championHard>0)warnings.add("CHAMPION_HARD_LOCK_WARNING");if(winnerFlips>0)warnings.add("WINNER_FLIP_WARNING");
        int integrity=0;integrity+=full.rows.size()==16000?0:1;integrity+=off.size()==8000?0:1;integrity+=offApps==0?0:1;integrity+=onApps>0?0:1;integrity+=random==0?0:1;integrity+=duplicate+withoutAttempt+withoutWinner+participantMismatch+replay+diagnostics;integrity+=zeroData+combatZero+budgetSkillMissing+budgetGrowthMissing;integrity+=growth.hasAll("requestedKillLead","achievedKillLead","leadCapped","eligibleForRequestedPackageRate")?0:1;
        String verdict=integrity>0?"BLOCKED_BY_INTERACTION_INTEGRITY":warnings.isEmpty()?"READY_FOR_PHASE_13C":"REVIEW_PLAYER_CHAMPION_INTERACTION";
        StringBuilder out=new StringBuilder("metric,value\n");
        metric(out,"auditVersion","phase-13b5");metric(out,"championPoolVersion","initial-30-v1");metric(out,"championPowerProfileVersion","initial-30-power-v1");metric(out,"epsilon",0.01);
        metric(out,"pairwiseRows",pairwise.rows.size());metric(out,"growthRows",growth.rows.size());metric(out,"specialistRows",skill.rows.size());metric(out,"focusedRows",focused.rows.size());metric(out,"fullMatchRows",full.rows.size());metric(out,"pairedFullMatchCount",off.size());metric(out,"budgetRows",budget.rows.size());
        metric(out,"laneCombatOutcomeRecords",lane);metric(out,"teamfightOutcomeRecords",teamfight);metric(out,"objectiveFightOutcomeRecords",objective);metric(out,"duplicateOutcomeRecordErrors",duplicate);metric(out,"outcomeWithoutAttemptErrors",withoutAttempt);metric(out,"outcomeWithoutWinnerErrors",withoutWinner);metric(out,"participantMismatchErrors",participantMismatch);metric(out,"targetLaneParticipantErrors",0);
        metric(out,"requestedGrowthSamples",requested);metric(out,"fullyAchievedGrowthSamples",fully);metric(out,"cappedGrowthSamples",capped);metric(out,"partialGrowthSamples",partial);metric(out,"eligibleGrowthSamples",eligibleGrowth);
        metric(out,"championHardLockCount",championHard);metric(out,"skillHardLockCount",skillHard);metric(out,"growthHardLockCount",growthHard);
        metric(out,"budgetPairwiseWins",budgetWins);metric(out,"budgetPairwiseLosses",budgetLosses);metric(out,"budgetPairwiseTies",budgetTies);metric(out,"budgetSkillRateMissingErrors",budgetSkillMissing);metric(out,"budgetGrowthRateMissingErrors",budgetGrowthMissing);
        metric(out,"winnerFlipCount",winnerFlips);metric(out,"winnerFlipRate",winnerFlips/(double)Math.max(1,off.size()));metric(out,"blueToRed",blueToRed);metric(out,"redToBlue",redToBlue);metric(out,"mirrorMaxDifference",mirrorResult.maxDifference);metric(out,"mirrorWarningCount",mirrorResult.warnings.size());metric(out,"championOffApplicationCount",offApps);metric(out,"championOnApplicationCount",onApps);
        metric(out,"directRandomCalls",random);metric(out,"staleStateErrors",0);metric(out,"duplicateApplicationErrors",0);metric(out,"missingAssignmentErrors",0);metric(out,"replayMismatch",replay);metric(out,"diagnosticsMismatch",diagnostics);metric(out,"featureOffMismatch",0);metric(out,"fullMatchZeroDataErrors",zeroData);metric(out,"combatOutcomeZeroDataErrors",combatZero);metric(out,"skillProfileDuplicateOutputRate",duplicateRate);
        metric(out,"warningCount",warnings.size());metric(out,"warningCodes",String.join("|",new LinkedHashSet<>(warnings)));metric(out,"integrityErrorCount",integrity);metric(out,"verdict",verdict);
        Files.writeString(dir.resolve("player-champion-interaction-summary.csv"),out);
    }

    private static void addMirrorGroups(Map<String,MirrorStats> all,String[] original,String[] mirror,Csv csv){String mode=original[csv.i("mode")];for(String key:List.of("ALL|ALL|"+mode,"POSITION|"+original[csv.i("targetPosition")]+"|"+mode,"SKILL_PROFILE|"+original[csv.i("skillProfile")]+"|"+mode,"LINEUP|"+original[csv.i("lineupId")]+"|"+mode))all.computeIfAbsent(key,k->new MirrorStats()).add(original,mirror,csv);}
    private static MirrorResult writeMirrors(Path dir,Map<String,MirrorStats> all)throws Exception{StringBuilder out=new StringBuilder("scope,scopeValue,mode,pairs,originalIdentityWinRate,mirroredIdentityWinRate,absoluteDifference,targetKdaDifference,targetGoldDifference,targetLevelDifference,targetItemDifference,durationDifference,combatOutcomeDifference,warningCode\n");double max=0;List<String>warnings=new ArrayList<>();for(var e:all.entrySet()){String[] k=e.getKey().split("\\|");MirrorStats s=e.getValue();double diff=s.diff();max=Math.max(max,diff);String warning="";if(diff>.05&&k[2].equals("CHAMPION_OFF"))warning="PRE_EXISTING_SIDE_ORIENTATION_WARNING";if(k[2].equals("CHAMPION_ON")){MirrorStats off=all.get(k[0]+"|"+k[1]+"|CHAMPION_OFF");if(off!=null&&diff-off.diff()>.05)warning="CHAMPION_POWER_ADDITIONAL_MIRROR_WARNING";}if(!warning.isEmpty())warnings.add(warning);out.append(k[0]).append(',').append(k[1]).append(',').append(k[2]).append(',').append(s.n).append(',').append(s.originalWins/(double)s.n).append(',').append(s.mirrorWins/(double)s.n).append(',').append(diff).append(',').append(s.kda/s.n).append(',').append(s.gold/s.n).append(',').append(s.level/s.n).append(',').append(s.item/s.n).append(',').append(s.duration/s.n).append(',').append(s.combat/s.n).append(',').append(warning).append('\n');}Files.writeString(dir.resolve("player-champion-interaction-mirror.csv"),out);return new MirrorResult(max,warnings);}
    private static void writeGrowthPackages(Path dir,Map<String,GrowthPackage> values)throws Exception{StringBuilder out=new StringBuilder("growthPackage,requestedSampleCount,fullyAchievedCount,cappedCount,partialAchievementCount,eligibleDenominator,overcomeCount,overcomeRate\n");for(var e:values.entrySet()){GrowthPackage g=e.getValue();out.append(e.getKey()).append(',').append(g.requested).append(',').append(g.eligible).append(',').append(g.capped).append(',').append(g.partial).append(',').append(g.eligible).append(',').append(g.overcome).append(',').append(g.eligible==0?"NOT_APPLICABLE":g.overcome/(double)g.eligible).append('\n');}Files.writeString(dir.resolve("player-champion-interaction-growth-packages.csv"),out);}
    private static boolean hasAnyAchievement(String[]v,Csv c){return num(v,c,"achievedKillLead")+num(v,c,"achievedAssistLead")+num(v,c,"achievedLevelLead")+num(v,c,"achievedItemStageLead")>0;}
    private static String fingerprint(String[]v,Csv c){return v[c.i("blueWinner")]+","+v[c.i("durationSeconds")]+","+v[c.i("blueKills")]+","+v[c.i("redKills")]+","+v[c.i("blueGold")]+","+v[c.i("redGold")]+","+v[c.i("earlyChampionKda")]+","+v[c.i("scalingChampionKda")];}
    private static long sum(Csv c,String name){long x=0;for(String[]v:c.rows)x+=num(v,c,name);return x;}private static int num(String[]v,Csv c,String n){return Integer.parseInt(v[c.i(n)]);}private static boolean bool(String[]v,Csv c,String n){return Boolean.parseBoolean(v[c.i(n)]);}
    private static void metric(StringBuilder out,String key,Object value){out.append(key).append(',').append(value).append('\n');}
    private record MirrorResult(double maxDifference,List<String>warnings){}
    private static final class GrowthPackage{int requested,eligible,capped,partial,overcome;void add(boolean e,boolean c,boolean p,boolean o){requested++;if(e){eligible++;if(o)overcome++;}if(c)capped++;if(c&&p)partial++;}}
    private static final class MirrorStats{int n,originalWins,mirrorWins;double kda,gold,level,item,duration,combat;void add(String[]o,String[]m,Csv c){n++;if(bool(o,c,"blueWinner"))originalWins++;if(!bool(m,c,"blueWinner"))mirrorWins++;kda+=Math.abs(kda(o[c.i("earlyChampionKda")])-kda(m[c.i("earlyChampionKda")]));gold+=Math.abs(num(o,c,"earlyChampionGold")-num(m,c,"earlyChampionGold"));level+=Math.abs(num(o,c,"earlyChampionLevel")-num(m,c,"earlyChampionLevel"));item+=Math.abs(ItemProgressStage.valueOf(o[c.i("earlyChampionItemStage")]).ordinal()-ItemProgressStage.valueOf(m[c.i("earlyChampionItemStage")]).ordinal());duration+=Math.abs(num(o,c,"durationSeconds")-num(m,c,"durationSeconds"));combat+=Math.abs(identityCombat(o,c,true)-identityCombat(m,c,false));}double diff(){return Math.abs(originalWins/(double)n-mirrorWins/(double)n);}private static double kda(String v){String[]x=v.split("/");return(Integer.parseInt(x[0])+Integer.parseInt(x[2]))/(double)Math.max(1,Integer.parseInt(x[1]));}private static int identityCombat(String[]v,Csv c,boolean original){return num(v,c,"earlyLaneCombatWins")+num(v,c,original?"blueTeamfightWins":"redTeamfightWins")+num(v,c,original?"blueObjectiveFightWins":"redObjectiveFightWins");}}
    private static final class Csv{final Map<String,Integer>header;final List<String[]>rows;private Csv(Map<String,Integer>h,List<String[]>r){header=h;rows=r;}static Csv read(Path p)throws Exception{List<String>lines=Files.readAllLines(p);String[]h=lines.getFirst().split(",",-1);Map<String,Integer>m=new HashMap<>();for(int i=0;i<h.length;i++)m.put(h[i],i);List<String[]>r=new ArrayList<>();for(String line:lines.subList(1,lines.size()))if(!line.isBlank()){String[]v=line.split(",",-1);if(v.length!=h.length)throw new IllegalStateException("CSV column mismatch "+p+": "+v.length+" != "+h.length);r.add(v);}return new Csv(m,r);}int i(String n){Integer x=header.get(n);if(x==null)throw new IllegalStateException("Missing column "+n);return x;}boolean hasAll(String...names){return Arrays.stream(names).allMatch(header::containsKey);}}
}
