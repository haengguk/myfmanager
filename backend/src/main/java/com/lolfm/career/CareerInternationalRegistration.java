package com.lolfm.career;

import com.lolfm.career.CareerInternationalState.Entry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Qualification policy over explicit domestic results and replaceable foreign ranking inputs. */
final class CareerInternationalRegistration {
    private final List<Entry> entries = new ArrayList<>();
    private final Map<String,CompetitionRosterSnapshot.Roster> frozen = new LinkedHashMap<>();
    private final Map<String,List<CompetitionRosterSnapshot.Roster>> rankings;
    private final String competition;
    private CareerInternationalRegistration(String competition, Map<String,List<CompetitionRosterSnapshot.Roster>> rankings) {
        this.competition=competition;this.rankings=rankings;
    }
    static CareerInternationalState create(String career,int year,String competition,long root,
            CareerInternationalParticipants.Selection selection,List<CompetitionRosterSnapshot.Roster> domestic,
            String domesticEvidence,List<String> regionOrder,CareerInternationalState msi) {
        var rankings=new LinkedHashMap<>(selection.rankings());rankings.put("LCK",List.copyOf(domestic));
        if(!rankings.keySet().equals(java.util.Set.copyOf(CareerInternationalRules.REFERENCE_REGIONS)))throw new IllegalArgumentException("SIX_REGIONAL_INPUTS_REQUIRED");
        var builder=new CareerInternationalRegistration(competition,rankings);
        switch(competition){
            case "FIRST_STAND" -> {
                for(String region:CareerInternationalRules.REFERENCE_REGIONS) {
                    boolean two=region.equals("LCK")||region.equals("LPL");
                    builder.add(region,1,two?1:2,"MAIN",region.equals("LCK")?"ACTUAL_CUP_RESULT":"TEMPORARY_OVERSEAS_RANK");
                    if(two)builder.add(region,2,3,"MAIN",region.equals("LCK")?"ACTUAL_CUP_RESULT":"TEMPORARY_OVERSEAS_RANK");
                }
            }
            case "MSI" -> {
                String extra=regionOrder.stream().filter(r->!r.equals("CBLOL")).findFirst().orElseThrow();
                for(String region:regionOrder){
                    builder.add(region,1,regionOrder.indexOf(region)/2+1,"MAIN",region.equals("LCK")?"ACTUAL_ROAD_TO_MSI":"TEMPORARY_OVERSEAS_RANK");
                    if(!region.equals("CBLOL"))builder.add(region,2,4,region.equals(extra)?"MAIN":"PLAY_IN",
                            region.equals(extra)?"FST_HIGHEST_ELIGIBLE_TWO_SEED_REGION":"REGIONAL_SECOND_SEED");
                }
            }
            case "EWC_LOL" -> {
                builder.add("LCK",1,1,"MAIN","DOMESTIC_RANK_DIRECT_REFERENCE");builder.add("LCP",2,1,"MAIN","OFFICIAL_LITERAL_LCP_2");
                builder.add("LEC",1,1,"MAIN","REGIONAL_FIRST_DIRECT");builder.add("LPL",1,1,"MAIN","REGIONAL_FIRST_DIRECT");
                builder.remaining("LPL",2,"CHINA_QUALIFIER_REPLACEMENT_1");builder.remaining("LCK",2,"KOREA_QUALIFIER_REPLACEMENT_1");
                builder.add("LCS",1,2,"MAIN","REGIONAL_FIRST_DIRECT");
                builder.remaining("LCP",3,"APAC_QUALIFIER_REPLACEMENT_1");builder.remaining("LPL",3,"CHINA_QUALIFIER_REPLACEMENT_2");
                builder.remaining("LEC",3,"EUROPE_QUALIFIER_REPLACEMENT_1");builder.remaining("LCK",3,"KOREA_QUALIFIER_REPLACEMENT_2");
                // Missing defender is selected after all ordinary LCK slots have been allocated.
                builder.remaining("LCK",2,"FIRST_CYCLE_MISSING_TITLE_SLOT_LCK_REFERENCE");
                builder.add("CBLOL",1,4,"MAIN","REGIONAL_FIRST_DIRECT");builder.remaining("LEC",4,"EUROPE_QUALIFIER_REPLACEMENT_2");
                builder.remaining("LCS",4,"NORTH_AMERICA_QUALIFIER_REPLACEMENT");builder.remaining("CBLOL",4,"SOUTH_AMERICA_QUALIFIER_REPLACEMENT");
            }
            case "WORLDS" -> {
                if(msi==null||!msi.plan().complete())throw new IllegalArgumentException("MSI_COMPLETION_REQUIRED");
                String champion=msi.plan().champion();String championRegion=msi.entries().stream().filter(e->e.team().equals(champion)).findFirst().orElseThrow().region();
                String otherBonus=regionOrder.stream().filter(r->!r.equals(championRegion)).findFirst().orElseThrow();
                for(String region:regionOrder){
                    int count=(region.equals("CBLOL")?2:3)+(region.equals(championRegion)?1:0)+(region.equals(otherBonus)?1:0);
                    var candidates=rankings.get(region);var qualified=new ArrayList<>(candidates.subList(0,count));
                    int championRank=-1;for(int i=0;i<candidates.size();i++)if(token(candidates.get(i)).equals(champion))championRank=i;
                    boolean eligible=region.equals(championRegion)&&championRank>=0&&championRank<6;
                    if(eligible&&!qualified.contains(candidates.get(championRank)))qualified.set(count-1,candidates.get(championRank));
                    int regionalOrder=regionOrder.indexOf(region);
                    for(int seed=1;seed<=count;seed++){
                        boolean pi=regionalOrder>=2&&seed==count;
                        int pool=seed==1?(regionalOrder<4?1:2):seed==2?(regionalOrder<2?2:regionalOrder<4?3:4):seed==3&&regionalOrder<2?3:4;
                        String qualification=token(qualified.get(seed-1)).equals(champion)&&eligible?"MSI_CHAMPION_HOME_PLAYOFF_ELIGIBLE":
                                region.equals(championRegion)&&seed==count&&!eligible?"MSI_BONUS_REGION_INELIGIBLE_CHAMPION_REPLACEMENT":"REGIONAL_FINAL_RANK";
                        builder.addRoster(qualified.get(seed-1),seed,pool,pi?"PLAY_IN":"MAIN",qualification);
                    }
                }
            }
            default -> throw new IllegalArgumentException("INTERNATIONAL_COMPETITION");
        }
        int expected=switch(competition){case "FIRST_STAND"->8;case "MSI"->11;case "EWC_LOL"->16;default->19;};
        if(builder.entries.size()!=expected)throw new IllegalStateException("INTERNATIONAL_QUALIFICATION_CARDINALITY");
        var state=new CareerInternationalState(career,year,competition,CareerInternationalRules.VERSION,CareerInternationalRules.RESOURCE_HASH,
                CareerInternationalRules.POLICY,selection.policy(),domesticEvidence, CareerCompetitionAggregate.deriveSeed(root,year,competition,"REGISTRATION_DRAW:"+career+":"+CareerInternationalRules.POLICY),
                builder.entries,new CompetitionRosterSnapshot(builder.frozen),regionOrder,null);
        return state.withPlan(CareerInternationalTournament.project(state,Map.of()));
    }
    private void add(String region,int seed,int pool,String phase,String path){addRoster(rankings.get(region).get(seed-1),seed,pool,phase,path);}
    private void remaining(String region,int pool,String path){
        var candidates=rankings.get(region);for(int i=0;i<candidates.size();i++)if(!frozen.containsKey(token(candidates.get(i)))){add(region,i+1,pool,"MAIN",path);return;}
        throw new IllegalStateException("NO_ELIGIBLE_REPLACEMENT:"+competition+":"+region);
    }
    private void addRoster(CompetitionRosterSnapshot.Roster roster,int seed,int pool,String phase,String path){
        if(frozen.putIfAbsent(token(roster),roster)!=null)throw new IllegalArgumentException("DUPLICATE_QUALIFICATION_TEAM");
        entries.add(new Entry(token(roster),roster.team().leagueCode(),seed,pool,phase,path));
    }
    private static String token(CompetitionRosterSnapshot.Roster roster){return CompetitionRosterSnapshot.token(roster.team());}
}
