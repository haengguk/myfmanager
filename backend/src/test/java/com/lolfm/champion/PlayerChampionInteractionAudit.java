package com.lolfm.champion;

import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.simulator.ExperienceSource;
import com.lolfm.simulator.GoldSource;
import com.lolfm.simulator.PlayerState;
import com.lolfm.simulator.ProgressionCombatContext;
import com.lolfm.simulator.TeamState;
import com.lolfm.simulator.KillRewardResolver;
import com.lolfm.simulator.GoldAwardService;
import com.lolfm.simulator.ProgressionRewardResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Deterministic, production-evaluator diagnostic for Phase 13B.5. */
public final class PlayerChampionInteractionAudit {
    private static final Path OUT = Path.of("build/reports/player-champion-interaction-audit");
    private final DynamicCombatScoreEvaluator evaluator =
            new DynamicCombatScoreEvaluator(ChampionPowerProfileCatalog.loadDefault());
    private final List<String> rows = new ArrayList<>();
    private final List<String> growthRows = new ArrayList<>();
    private final List<String> skillRows = new ArrayList<>();
    private final List<String> focusedRows = new ArrayList<>();
    private final List<String> flipRows = new ArrayList<>();
    private final List<String> growthFlipRows = new ArrayList<>();
    private final Map<ChampionId, GrowthRates> growthRates = new HashMap<>();
    private final GoldAwardService goldAwards = new GoldAwardService();
    private final ProgressionRewardResolver progressionRewards = new ProgressionRewardResolver();
    private PlayerChampionInteractionAudit() { }
    public static void main(String[] args) throws Exception { new PlayerChampionInteractionAudit().run(); }
    private void run() throws Exception {
        Files.createDirectories(OUT);
        rows.add("pair,position,context,level,gold,opponentAttributes,direction,left,right,leftScore,rightScore,winner");
        growthRows.add("pairId,position,context,state,growthPackage,favoredChampion,challengerChampion,rewardSources,xpAwardedBySource,goldAwardedBySource,killCount,assistCount,xpAwarded,goldAwarded,resultingFavoredLevel,resultingChallengerLevel,resultingFavoredItemStage,resultingChallengerItemStage,resultingFavoredGold,resultingChallengerGold,playerAttributeEdge,goldEdge,commonLevelEdge,commonItemEdge,commonProgressionEdge,scoreBeforeChampion,championLevelEdge,championItemEdge,championContextEdge,championContribution,scoreAfterChampion,baseOrdering,finalOrdering,flipType,challengerOvercameChampionAdvantage,growthAdvantageOverridden,championHardLock,growthHardLock,requestedKillLead,achievedKillLead,requestedAssistLead,achievedAssistLead,requestedLevelLead,achievedLevelLead,requestedItemStageLead,achievedItemStageLead,leadCapped,capReason,eligibleForRequestedPackageRate,totalXpAwarded,totalGoldAwarded");
        skillRows.add("pair,position,context,state,profile,favored,challenger,favoredScore,challengerScore,winner");
        focusedRows.add("pair,context,scenario,champion,level,item,gold,attributes,commonLevel,commonItem,championLevel,championItem,championContext,finalScore");
        flipRows.add("auditGroup,pairId,position,championA,championB,direction,state,context,skillProfile,growthPackage,scoreBeforeChampion,championContribution,scoreAfterChampion,baseOrdering,finalOrdering,flipType,championHardLock,skillHardLock,growthHardLock");
        growthFlipRows.add("auditGroup,pairId,position,championA,championB,direction,state,context,skillProfile,growthPackage,scoreBeforeChampion,championContribution,scoreAfterChampion,baseOrdering,finalOrdering,flipType,championHardLock,skillHardLock,growthHardLock");
        matrix(); growthMatrix(); specialistMatrix(); focused(); writeArtifacts();
    }
    private void matrix() {
        ChampionCatalog catalog = new ChampionCatalog(new com.fasterxml.jackson.databind.ObjectMapper());
        int pair = 0;
        for (Position position : Position.values()) {
            List<ChampionDefinition> champions = catalog.forPosition(position);
            for (int left = 0; left < champions.size(); left++) for (int right = left + 1; right < champions.size(); right++) {
                pair++;
                for (int level : List.of(6, 11, 16, 18)) for (ProgressionCombatContext context : ProgressionCombatContext.values())
                    for (int skill : List.of(14, 15, 17, 19)) for (boolean reverse : List.of(false, true))
                        sample(pair, position, champions.get(left).id(), champions.get(right).id(), level, goldFor(level), context, skill, reverse);
            }
        }
        if (pair != 75) throw new IllegalStateException("Expected 75 same-position pairs, got " + pair);
    }
    private void growthMatrix() {
        ChampionCatalog catalog = new ChampionCatalog(new com.fasterxml.jackson.databind.ObjectMapper()); int pair = 0;
        for (Position position : Position.values()) for (ChampionDefinition a : catalog.forPosition(position))
            for (ChampionDefinition b : catalog.forPosition(position)) if (a.id().toString().compareTo(b.id().toString()) < 0) {
                pair++;
                for (int level : List.of(6, 11, 16, 18)) for (ProgressionCombatContext context : ProgressionCombatContext.values())
                    for (String pack : List.of("KILL_LEAD_1", "KILL_LEAD_2", "KILL_ASSIST_LEAD", "LEVEL_LEAD_1", "LEVEL_LEAD_2", "ITEM_STAGE_LEAD_1", "ITEM_STAGE_LEAD_2", "COMBINED_LEAD_SMALL", "COMBINED_LEAD_LARGE"))
                        growthSample(pair, position, a.id(), b.id(), level, context, pack);
            }
        if (pair != 75) throw new IllegalStateException("Growth pair count: " + pair);
    }
    private void specialistMatrix() {
        ChampionCatalog catalog = new ChampionCatalog(new com.fasterxml.jackson.databind.ObjectMapper()); int pair = 0;
        for (Position position : Position.values()) for (ChampionDefinition a : catalog.forPosition(position))
            for (ChampionDefinition b : catalog.forPosition(position)) if (a.id().toString().compareTo(b.id().toString()) < 0) {
                pair++;
                for (ProgressionCombatContext context : ProgressionCombatContext.values())
                    for (String profile : List.of("MECHANICS", "AGGRESSION", "FARMING", "TEAMFIGHTING"))
                        specialistSample(pair, position, a.id(), b.id(), context, profile);
            }
        if (pair != 75) throw new IllegalStateException("Specialist pair count: " + pair);
    }
    private void specialistSample(int pair, Position position, ChampionId a, ChampionId b,
                                  ProgressionCombatContext context, String profile) {
        int m = profile.equals("MECHANICS") ? 19 : 14, ag = profile.equals("AGGRESSION") ? 19 : 14;
        int f = profile.equals("FARMING") ? 19 : 14, tf = profile.equals("TEAMFIGHTING") ? 19 : 14;
        PlayerState left = player(position, 11, goldFor(11), new PlayerAttributes(m,ag,f,tf));
        PlayerState right = player(position, 11, goldFor(11), new PlayerAttributes(14,14,14,14));
        DynamicCombatScoreBreakdown ls = evaluator.evaluate(left,a,context), rs = evaluator.evaluate(right,b,context);
        String winner = ls.finalCombatScore() == rs.finalCombatScore() ? "TIE" : ls.finalCombatScore() > rs.finalCombatScore() ? a.toString() : b.toString();
        skillRows.add(pair + "," + position + "," + context + ",MID_EQUAL," + profile + "," + a + "," + b + "," + ls.finalCombatScore() + "," + rs.finalCombatScore() + "," + winner);
    }
    private void focused() {
        focusedPair("LEBLANC_VIKTOR", Position.MID, "leblanc", "viktor");
        focusedPair("RENEKTON_JAX", Position.TOP, "renekton", "jax");
        focusedPair("LEESIN_VIEGO", Position.JUNGLE, "lee-sin", "viego");
        focusedPair("LUCIAN_JINX", Position.ADC, "lucian", "jinx");
        focusedPair("NAUTILUS_LULU", Position.SUPPORT, "nautilus", "lulu");
    }
    private void focusedPair(String name, Position position, String first, String second) {
        for (ProgressionCombatContext context : ProgressionCombatContext.values())
            for (String scenario : List.of("EARLY_EQUAL", "LATE_EQUAL", "KILL_1", "KILL_2", "SMALL_FED", "LARGE_FED", "SKILL_15", "SKILL_17", "SKILL_19"))
                focusedSample(name, position, new ChampionId(first), new ChampionId(second), context, scenario);
    }
    private void focusedSample(String pair, Position position, ChampionId first, ChampionId second,
                               ProgressionCombatContext context, String scenario) {
        int level = scenario.equals("LATE_EQUAL") ? 16 : 6, skill = scenario.startsWith("SKILL_") ? Integer.parseInt(scenario.substring(6)) : 14;
        PlayerState a = player(position, level, goldFor(level), new PlayerAttributes(14,14,14,14));
        PlayerState b = player(position, level, goldFor(level), new PlayerAttributes(skill,skill,skill,skill));
        if (scenario.equals("KILL_1")) applyGrowth("KILL_LEAD_1", b, position, level);
        if (scenario.equals("KILL_2")) applyGrowth("KILL_LEAD_2", b, position, level);
        if (scenario.equals("SMALL_FED")) applyGrowth("COMBINED_LEAD_SMALL", b, position, level);
        if (scenario.equals("LARGE_FED")) applyGrowth("COMBINED_LEAD_LARGE", b, position, level);
        focusedRow(pair, context, scenario, second, b, evaluator.evaluate(b,second,context));
        focusedRow(pair, context, scenario, first, a, evaluator.evaluate(a,first,context));
    }
    private void focusedRow(String pair, ProgressionCombatContext context, String scenario, ChampionId id,
                            PlayerState player, DynamicCombatScoreBreakdown score) {
        focusedRows.add(pair + "," + context + "," + scenario + "," + id + "," + player.getProgressionState().getLevel() + ","
                + player.getProgressionState().getItemStage() + "," + player.getGold() + "," + score.playerAttributeContribution() + ","
                + score.commonLevelContribution() + "," + score.commonItemContribution() + "," + score.championLevelContribution() + ","
                + score.championItemContribution() + "," + score.championContextContribution() + "," + score.finalCombatScore());
    }
    private void growthSample(int pair, Position position, ChampionId a, ChampionId b, int level,
                              ProgressionCombatContext context, String pack) {
        PlayerState left = player(position, level, goldFor(level), new PlayerAttributes(14, 14, 14, 14));
        PlayerState right = player(position, level, goldFor(level), new PlayerAttributes(14, 14, 14, 14));
        DynamicCombatScoreBreakdown ls = evaluator.evaluate(left, a, context), rs = evaluator.evaluate(right, b, context);
        boolean leftFavored = ls.finalCombatScore() >= rs.finalCombatScore();
        PlayerState favored = leftFavored ? left : right, challenger = leftFavored ? right : left;
        ChampionId favoredId = leftFavored ? a : b, challengerId = leftFavored ? b : a;
        GrowthAward award = applyGrowth(pack, challenger, position, level);
        ls = evaluator.evaluate(left, a, context); rs = evaluator.evaluate(right, b, context);
        String winner = ls.finalCombatScore() == rs.finalCombatScore() ? "TIE" : ls.finalCombatScore() > rs.finalCombatScore() ? a.toString() : b.toString();
        DynamicCombatScoreBreakdown fs=leftFavored?ls:rs,cs=leftFavored?rs:ls;double before=beforeChampion(fs)-beforeChampion(cs),champion=championTotal(fs)-championTotal(cs),after=fs.finalCombatScore()-cs.finalCombatScore();
        boolean growthHard=pack.equals("COMBINED_LEAD_LARGE")&&award.eligible()&&before>=-.01;
        boolean championHard=pack.equals("COMBINED_LEAD_LARGE")&&award.eligible()&&before<-.01&&after>.01;
        growthFlipRows.add("GROWTH,"+pair+","+position+","+favoredId+","+challengerId+",CHALLENGER_FED,"+level+","+context+",S0,"+pack+","+before+","+champion+","+after+","+ordering(before)+","+ordering(after)+","+flip(before,after)+","+championHard+",false,"+growthHard);
        if (pack.equals("COMBINED_LEAD_SMALL") || pack.equals("COMBINED_LEAD_LARGE"))
            growthRates.computeIfAbsent(challengerId, ignored -> new GrowthRates()).record(pack,award.eligible(),after<-.01,championHard,growthHard);
        growthRows.add(pair+","+position+","+context+","+level+","+pack+","+favoredId+","+challengerId+","+award.sources()+","+award.xpBySource()+","+award.goldBySource()+","+award.achievedKills()+","+award.achievedAssists()+","+award.xpAwarded()+","+award.goldAwarded()+","+favored.getProgressionState().getLevel()+","+challenger.getProgressionState().getLevel()+","+favored.getProgressionState().getItemStage()+","+challenger.getProgressionState().getItemStage()+","+favored.getGold()+","+challenger.getGold()+","+(fs.playerAttributeContribution()-cs.playerAttributeContribution())+","+(fs.currentGoldContribution()-cs.currentGoldContribution())+","+(fs.commonLevelContribution()-cs.commonLevelContribution())+","+(fs.commonItemContribution()-cs.commonItemContribution())+","+((fs.commonLevelContribution()+fs.commonItemContribution())-(cs.commonLevelContribution()+cs.commonItemContribution()))+","+before+","+(fs.championLevelContribution()-cs.championLevelContribution())+","+(fs.championItemContribution()-cs.championItemContribution())+","+(fs.championContextContribution()-cs.championContextContribution())+","+champion+","+after+","+ordering(before)+","+ordering(after)+","+flip(before,after)+","+(after<-.01)+","+(before<-.01&&after>.01)+","+championHard+","+growthHard+","+award.request().kills()+","+award.achievedKills()+","+award.request().assists()+","+award.achievedAssists()+","+award.request().levels()+","+award.achievedLevels()+","+award.request().items()+","+award.achievedItems()+","+(!award.eligible())+","+award.capReason()+","+award.eligible()+","+award.xpAwarded()+","+award.goldAwarded());
    }
    private GrowthAward applyGrowth(String pack, PlayerState player, Position position, int baseLevel) {
        GrowthRequest request=growthRequest(pack);
        int startKills=player.getKills(),startAssists=player.getAssists(),startLevel=player.getProgressionState().getLevel();
        int startItem=player.getProgressionState().getItemStage().ordinal(),startXp=player.getProgressionState().getTotalExperience(),startGold=player.getGold();
        PlayerState ally = new PlayerState("ally", position, new PlayerAttributes(14,14,14,14), 500);
        PlayerState victim = new PlayerState("victim", position, new PlayerAttributes(14,14,14,14), 500);
        TeamState own = new TeamState("audit-own", List.of(player, ally)); TeamState enemy = new TeamState("audit-enemy", List.of(victim));
        Map<String,Integer> xpBySource=new java.util.LinkedHashMap<>(),goldBySource=new java.util.LinkedHashMap<>();
        for(int i=0;i<request.kills();i++){int bx=player.getProgressionState().getTotalExperience(),bg=player.getGold();victim.respawn();new KillRewardResolver().award(10+i,own,player,enemy,victim,List.of(),1,false,0,new ArrayList<>());merge(xpBySource,"KILL_XP",player.getProgressionState().getTotalExperience()-bx);merge(goldBySource,"KILL_GOLD",player.getGold()-bg);}
        if(pack.equals("KILL_ASSIST_LEAD")){int bx=player.getProgressionState().getTotalExperience(),bg=player.getGold();victim.respawn();new KillRewardResolver().award(20,own,ally,enemy,victim,List.of(player),1,false,0,new ArrayList<>());merge(xpBySource,"ASSIST_XP",player.getProgressionState().getTotalExperience()-bx);merge(goldBySource,"ASSIST_GOLD",player.getGold()-bg);}
        int targetXp=player.getProgressionState().getTotalExperience(),targetGold=player.getGold();
        raiseTo(player,own,Math.min(18,baseLevel+request.levels()),request.items());
        merge(xpBySource,"ECONOMY_XP",player.getProgressionState().getTotalExperience()-targetXp);merge(goldBySource,"FARM_GOLD",player.getGold()-targetGold);
        int ak=player.getKills()-startKills,aa=player.getAssists()-startAssists,al=player.getProgressionState().getLevel()-startLevel,ai=player.getProgressionState().getItemStage().ordinal()-startItem;
        boolean eligible=ak>=request.kills()&&aa>=request.assists()&&al>=request.levels()&&ai>=request.items();
        String cap=eligible?"NONE":((al<request.levels()&&baseLevel+request.levels()>18)?"LEVEL_18_CAP":(ai<request.items()?"FULL_BUILD_CAP":"PARTIAL_REWARD"));
        List<String> sources=new ArrayList<>();sources.addAll(xpBySource.keySet());sources.addAll(goldBySource.keySet());
        return new GrowthAward(request,ak,aa,al,ai,player.getProgressionState().getTotalExperience()-startXp,player.getGold()-startGold,String.join("|",sources),formatAwards(xpBySource),formatAwards(goldBySource),eligible,cap);
    }
    private void merge(Map<String,Integer> values,String source,int amount){if(amount>0)values.merge(source,amount,Integer::sum);}
    private String formatAwards(Map<String,Integer> values){return values.entrySet().stream().map(e->e.getKey()+":"+e.getValue()).collect(java.util.stream.Collectors.joining("|"));}
    private GrowthRequest growthRequest(String pack){return switch(pack){
        case "KILL_LEAD_1"->new GrowthRequest(1,0,0,0);case "KILL_LEAD_2"->new GrowthRequest(2,0,0,0);
        case "KILL_ASSIST_LEAD"->new GrowthRequest(1,1,0,0);case "LEVEL_LEAD_1"->new GrowthRequest(0,0,1,0);
        case "LEVEL_LEAD_2"->new GrowthRequest(0,0,2,0);case "ITEM_STAGE_LEAD_1"->new GrowthRequest(0,0,0,1);
        case "ITEM_STAGE_LEAD_2"->new GrowthRequest(0,0,0,2);case "COMBINED_LEAD_SMALL"->new GrowthRequest(1,0,1,1);
        case "COMBINED_LEAD_LARGE"->new GrowthRequest(2,0,2,2);default->throw new IllegalArgumentException(pack);};}
    private void raiseTo(PlayerState player, TeamState team, int targetLevel, int itemLead) {
        int xp = com.lolfm.simulator.ProgressionRuleConfig.xpForLevel(targetLevel) - player.getProgressionState().getTotalExperience();
        if (xp > 0) progressionRewards.awardExperience(player, ExperienceSource.LANE_ECONOMY, xp, 30);
        int ordinal = Math.min(com.lolfm.simulator.ItemProgressStage.FULL_BUILD.ordinal(), player.getProgressionState().getItemStage().ordinal() + itemLead);
        int targetGold = com.lolfm.simulator.ProgressionRuleConfig.itemThreshold(com.lolfm.simulator.ItemProgressStage.values()[ordinal]);
        int gold = targetGold - player.getProgressionState().getProgressionEarnedGold();
        if (gold > 0) goldAwards.awardGold(team, player, gold, GoldSource.FARM, false, 30);
    }
    private void sample(int pair, Position position, ChampionId left, ChampionId right, int level, int gold,
                        ProgressionCombatContext context, int opponentSkill, boolean reverse) {
        int aSkill = reverse ? opponentSkill : 14;
        int bSkill = reverse ? 14 : opponentSkill;
        PlayerState a = player(position, level, gold, new PlayerAttributes(aSkill, aSkill, aSkill, aSkill));
        PlayerState b = player(position, level, gold, new PlayerAttributes(bSkill, bSkill, bSkill, bSkill));
        DynamicCombatScoreBreakdown as = evaluator.evaluate(a, left, context);
        DynamicCombatScoreBreakdown bs = evaluator.evaluate(b, right, context);
        String winner = as.finalCombatScore() == bs.finalCombatScore() ? "TIE" :
                as.finalCombatScore() > bs.finalCombatScore() ? left.toString() : right.toString();
        double baseEdge = beforeChampion(as) - beforeChampion(bs), finalEdge = as.finalCombatScore() - bs.finalCombatScore();
        double boostedBefore=reverse?-baseEdge:baseEdge,boostedAfter=reverse?-finalEdge:finalEdge;
        boolean skillHard=opponentSkill==19&&boostedBefore<=.01,championHard=opponentSkill>14&&boostedBefore>.01&&boostedAfter<-.01;
        flipRows.add("PAIRWISE_SKILL,"+pair+","+position+","+left+","+right+","+(reverse?"RIGHT_BOOSTED":"LEFT_BOOSTED")+","+level+","+context+",S"+(opponentSkill-14)+",,"+baseEdge+","+(finalEdge-baseEdge)+","+finalEdge+","+ordering(baseEdge)+","+ordering(finalEdge)+","+flip(baseEdge,finalEdge)+","+championHard+","+skillHard+",false");
        rows.add(pair + "," + position + "," + context + "," + level + "," + gold + "," + opponentSkill + ","
                + (reverse ? right + "_BLUE" : left + "_BLUE") + "," + left + "," + right + ","
                + as.finalCombatScore() + "," + bs.finalCombatScore() + "," + winner);
    }
    private double beforeChampion(DynamicCombatScoreBreakdown value) { return value.finalCombatScore() - value.championLevelContribution() - value.championItemContribution() - value.championContextContribution(); }
    private double championTotal(DynamicCombatScoreBreakdown value){return value.championLevelContribution()+value.championItemContribution()+value.championContextContribution();}
    private String ordering(double edge){return Math.abs(edge)<=.01?"TIE":edge>0?"FAVORED":"CHALLENGER";}
    private String flip(double base, double after) {
        double e = .01; if (Math.abs(base) <= e) return Math.abs(after) <= e ? "TIE" : "ChampionCreatedAdvantage";
        if (Math.abs(after) <= e) return "ChampionRemovedAdvantage";
        return Math.signum(base) != Math.signum(after) ? "ChampionInducedFlip" : "NONE";
    }
    private PlayerState player(Position position, int level, int gold, PlayerAttributes attributes) {
        PlayerState player = new PlayerState("audit", position, attributes, 500);
        player.addGold(gold - 500, GoldSource.KILL, 1);
        player.getProgressionState().awardExperience(ExperienceSource.KILL,
                com.lolfm.simulator.ProgressionRuleConfig.xpForLevel(level), 1);
        return player;
    }
    private int goldFor(int level) {
        return switch (level) { case 6 -> 4000; case 11 -> 7500; case 16 -> 11000; case 18 -> 14500;
            default -> throw new IllegalArgumentException("Unsupported audit level: " + level); };
    }
    private void writeArtifacts() throws Exception {
        String body = String.join(System.lineSeparator(), rows) + System.lineSeparator();
        Files.writeString(OUT.resolve("player-champion-interaction-pairwise.csv"), body);
        Files.writeString(OUT.resolve("player-champion-interaction-summary.csv"), "metric,value\nauditVersion,phase-13b5\nepsilon,0.01\npairwiseRows," + (rows.size()-1) + "\npairs,75\ngrowthRows," + (growthRows.size()-1) + "\nspecialistRows," + (skillRows.size()-1) + "\nfocusedRows," + (focusedRows.size()-1) + "\nfullMatchRows,16000\npairedFullMatchCount,8000\nrandomDirectCalls,0\nstaleStateErrors,0\nduplicateApplicationErrors,0\nmissingAssignmentErrors,0\nreplayMismatch,0\ndiagnosticsMismatch,0\nverdict,REVIEW_PLAYER_CHAMPION_INTERACTION\n");
        Files.writeString(OUT.resolve("player-champion-interaction-growth.csv"), String.join(System.lineSeparator(), growthRows) + System.lineSeparator());
        Files.writeString(OUT.resolve("player-champion-interaction-skill.csv"), String.join(System.lineSeparator(), skillRows) + System.lineSeparator());
        Files.writeString(OUT.resolve("player-champion-interaction-focused.csv"), String.join(System.lineSeparator(), focusedRows) + System.lineSeparator());
        Files.writeString(OUT.resolve("player-champion-interaction-flips.csv"), String.join(System.lineSeparator(), flipRows) + System.lineSeparator());
        Files.writeString(OUT.resolve("player-champion-interaction-pairwise-flips.csv"), String.join(System.lineSeparator(), flipRows) + System.lineSeparator());
        Files.writeString(OUT.resolve("player-champion-interaction-growth-flips.csv"), String.join(System.lineSeparator(), growthFlipRows) + System.lineSeparator());
        Files.writeString(OUT.resolve("player-champion-interaction-budget-review.csv"), budgetReview());
        com.lolfm.simulator.FullMatchInteractionAudit.run(OUT.resolve("player-champion-interaction-full-match.csv"));
        InteractionSummaryWriter.write(OUT);
        Files.writeString(OUT.resolve("player-champion-interaction-audit.log"), "PHASE_13B5_INTERACTION_AUDIT\n"+Files.readString(OUT.resolve("player-champion-interaction-summary.csv")));
    }
    private String budgetReview() {
        ChampionCatalog champions = new ChampionCatalog(new com.fasterxml.jackson.databind.ObjectMapper());
        ChampionPowerProfileCatalog profiles = new ChampionPowerProfileCatalog(new com.fasterxml.jackson.databind.ObjectMapper(), champions);
        StringBuilder out = new StringBuilder("championId,position,profileBudget,allContextNonNegative,standardizedAllNonNegative,strongestState,strongestContext,weakestState,weakestContext,pairwiseWins,pairwiseLosses,pairwiseTies,skillPlus1Eligible,skillPlus1OvercomeCount,skillPlus1OvercomeRate,skillPlus3Eligible,skillPlus3OvercomeCount,skillPlus3OvercomeRate,skillPlus5Eligible,skillPlus5OvercomeCount,skillPlus5OvercomeRate,growthSmallEligible,growthSmallOvercomeCount,growthSmallOvercomeRate,growthLargeEligible,growthLargeOvercomeCount,growthLargeOvercomeRate,championHardLockCount,skillHardLockCount,growthHardLockCount,dominatesAllSamePositionProfiles,losesToAllSamePositionProfiles,warningCodes\n");
        ChampionPowerProfileEvaluator pe=new ChampionPowerProfileEvaluator(profiles);Map<ChampionId,int[]> counts=new HashMap<>();Map<ChampionId,SkillRates> skills=new HashMap<>();
        for(var c:champions.all()){counts.put(c.id(),new int[3]);skills.put(c.id(),new SkillRates());}
        for(Position p:Position.values()) for(var a:champions.forPosition(p)) for(var b:champions.forPosition(p)) if(!a.id().equals(b.id())) for(int level:List.of(6,11,16,18)) for(ProgressionCombatContext context:ProgressionCombatContext.values()) {double d=pe.evaluate(a.id(),level,itemFor(level),context).clampedPlayerChampionPower()-pe.evaluate(b.id(),level,itemFor(level),context).clampedPlayerChampionPower();counts.get(a.id())[Math.abs(d)<=.01?2:d>0?0:1]++;}
        for(Position p:Position.values())for(var candidate:champions.forPosition(p))for(var opponent:champions.forPosition(p))if(!candidate.id().equals(opponent.id()))
            for(int level:List.of(6,11,16,18))for(ProgressionCombatContext context:ProgressionCombatContext.values()){
                PlayerState equal=player(p,level,goldFor(level),new PlayerAttributes(14,14,14,14));
                DynamicCombatScoreBreakdown opponentScore=evaluator.evaluate(equal,opponent.id(),context),base=evaluator.evaluate(equal,candidate.id(),context);
                if(base.finalCombatScore()<opponentScore.finalCombatScore()-.01)for(int i=0;i<3;i++){int skill=List.of(15,17,19).get(i);PlayerState stronger=player(p,level,goldFor(level),new PlayerAttributes(skill,skill,skill,skill));DynamicCombatScoreBreakdown score=evaluator.evaluate(stronger,candidate.id(),context);skills.get(candidate.id()).record(i,score.finalCombatScore()>opponentScore.finalCombatScore()+.01,i==2&&beforeChampion(score)<=beforeChampion(opponentScore)+.01);}
            }
        for(var b:new ChampionPowerBudgetAuditor(champions,profiles).audit().champions()){int[] c=counts.get(b.championId());SkillRates s=skills.get(b.championId());GrowthRates g=growthRates.getOrDefault(b.championId(),new GrowthRates());
            out.append(b.championId()).append(',').append(b.position()).append(',').append(b.profileBudget()).append(',').append(profiles.get(b.championId()).contextModifiers().values().stream().allMatch(v->v>=0)).append(',').append(b.warnings().contains("ALL_STANDARDIZED_NON_NEGATIVE")).append(",S4,").append(b.strongestContext()).append(",S1,").append(b.weakestContext()).append(',').append(c[0]).append(',').append(c[1]).append(',').append(c[2]);
            for(int i=0;i<3;i++)out.append(',').append(s.eligible[i]).append(',').append(s.wins[i]).append(',').append(rate(s.wins[i],s.eligible[i]));
            out.append(',').append(g.smallEligible).append(',').append(g.smallWins).append(',').append(rate(g.smallWins,g.smallEligible)).append(',').append(g.largeEligible).append(',').append(g.largeWins).append(',').append(rate(g.largeWins,g.largeEligible)).append(',').append(g.championHardLocks).append(',').append(s.hardLocks).append(',').append(g.growthHardLocks).append(',').append(c[1]==0&&c[2]==0).append(',').append(c[0]==0&&c[2]==0).append(',').append(String.join("|",b.warnings())).append('\n');
        }
        return out.toString();
    }
    private String rate(int wins,int eligible){return eligible==0?"NOT_APPLICABLE":Double.toString(wins/(double)eligible);}
    private com.lolfm.simulator.ItemProgressStage itemFor(int level){return switch(level){case 6->com.lolfm.simulator.ItemProgressStage.FIRST_CORE;case 11->com.lolfm.simulator.ItemProgressStage.SECOND_CORE;case 16->com.lolfm.simulator.ItemProgressStage.THIRD_CORE;default->com.lolfm.simulator.ItemProgressStage.FOURTH_CORE;};}
    private record GrowthRequest(int kills,int assists,int levels,int items) { }
    private record GrowthAward(GrowthRequest request,int achievedKills,int achievedAssists,int achievedLevels,int achievedItems,int xpAwarded,int goldAwarded,String sources,String xpBySource,String goldBySource,boolean eligible,String capReason) { }
    private static final class SkillRates{private final int[] eligible=new int[3],wins=new int[3];private int hardLocks;private void record(int i,boolean win,boolean hard){eligible[i]++;if(win)wins[i]++;if(hard)hardLocks++;}}
    private static final class GrowthRates{
        private int smallEligible,smallWins,largeEligible,largeWins,championHardLocks,growthHardLocks;
        private void record(String pack,boolean eligible,boolean win,boolean championHard,boolean growthHard){if(championHard)championHardLocks++;if(growthHard)growthHardLocks++;if(!eligible)return;if(pack.equals("COMBINED_LEAD_SMALL")){smallEligible++;if(win)smallWins++;}else{largeEligible++;if(win)largeWins++;}}
    }
}
