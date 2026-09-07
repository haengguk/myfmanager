package com.lolfm.career;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.domain.*;
import com.lolfm.player.ExpandedPlayerCatalog.Definition;
import java.time.LocalDate;
import java.util.*;
import static com.lolfm.career.CareerLifecyclePolicy.*;
import static com.lolfm.career.CareerLifecycleState.*;

/** One deterministic class, built from legal current catalog keys and stored only in its Career. */
public final class CareerRookieFactory {
    private CareerRookieFactory(){}
    public record Rookie(Definition definition,Age age) {}
    public static List<Rookie> generate(String career,long seed,int year,LocalDate date,Map<Position,Integer> allocation,
            boolean emergency,int firstSequence,ChampionCatalog catalog,List<String> regions) {
        var roles=new ArrayList<Position>();for(var role:Position.values())for(int i=0;i<allocation.getOrDefault(role,0);i++)roles.add(role);
        int size=roles.size();if(size==0)return List.of();
        int legend=!emergency&&draw(seed,career,year,"LEGEND_CLASS",100)<5?draw(seed,career,year,"LEGEND_SLOT",size):-1;
        int elite=0,upper=0;var result=new ArrayList<Rookie>();var mapper=new ObjectMapper();
        for(int i=0;i<size;i++) {
            int sequence=firstSequence+i;String id="player-ng-"+CareerRosterStore.hash(career).substring(0,12)+'-'+year+'-'+String.format(java.util.Locale.ROOT,"%03d",sequence);
            var role=roles.get(i);int roll=draw(seed,id,year,"AGE",100),age=roll<25?17:roll<60?18:roll<90?19:20;
            int paRoll=draw(seed,id,year,"POTENTIAL_BAND",1000);int pa;
            if(emergency)pa=135+draw(seed,id,year,"EMERGENCY_PA",21);
            else if(i==legend){pa=195+draw(seed,id,year,"LEGEND_PA",6);elite++;}
            else if(legend<0&&elite==0&&paRoll>=980&&paRoll<995){pa=190+draw(seed,id,year,"ELITE_PA",5);elite++;}
            else if(paRoll>=900&&paRoll<980&&upper<3){pa=180+draw(seed,id,year,"UPPER_PA",10);upper++;}
            else if(paRoll>=650)pa=165+draw(seed,id,year,"HIGH_PA",15);
            else if(paRoll>=250)pa=150+draw(seed,id,year,"MID_PA",15);
            else pa=135+draw(seed,id,year,"LOW_PA",15);
            int low=age==17?18:age==18?15:age==19?12:8,range=age==17?15:age==18?14:age==19?13:11;
            int target=emergency?110+draw(seed,id,year,"EMERGENCY_CA",26):Math.max(90,Math.min(pa>=180?165:159,pa-low-draw(seed,id,year,"CA_GAP",range)));
            int total=(int)Math.round(12+(target-1)*228.0/199);total=Math.min(total,CareerDevelopmentPolicy.ceiling(pa)/1000);
            var skills=PlayerSkill.orderedForPosition(role);var ratings=new EnumMap<PlayerSkill,Integer>(PlayerSkill.class);
            int baseline=Math.max(1,total/12-3);for(var skill:skills)ratings.put(skill,baseline);
            int type=draw(seed,id,year,"ARCHETYPE",3);var order=new ArrayList<PlayerSkill>();
            for(var skill:skills){order.add(skill);boolean favored=type==0?(skill==PlayerSkill.MECHANICS||skill==PlayerSkill.COMBAT_EXECUTION):type==1?(skill==PlayerSkill.DECISION_MAKING||skill==PlayerSkill.MAP_AWARENESS):!Set.of(PlayerSkill.MECHANICS,PlayerSkill.COMBAT_EXECUTION,PlayerSkill.DECISION_MAKING,PlayerSkill.MAP_AWARENESS,PlayerSkill.POSITIONING,PlayerSkill.CONSISTENCY).contains(skill);if(favored){order.add(skill);order.add(skill);}}
            order.sort(Comparator.comparingInt((PlayerSkill skill)->draw(seed,id,year,"SKILL_ORDER_"+skill,100000)).thenComparing(Enum::name));
            for(int point=baseline*12,cursor=0;point<total;cursor++){var skill=order.get(cursor%order.size());if(ratings.get(skill)<20){ratings.merge(skill,1,Integer::sum);point++;}}
            var legal=catalog.forPosition(role).stream().sorted(Comparator.comparingInt((com.lolfm.champion.ChampionDefinition c)->draw(seed,id,year,"CHAMPION_"+c.id().value(),100000)).thenComparing(c->c.id().value())).toList();
            int specialties=Math.min(legal.size(),3+draw(seed,id,year,"SPECIALTIES",4));var prof=new ArrayList<CompetitionRosterSnapshot.Proficiency>();
            for(int j=0;j<specialties;j++)prof.add(new CompetitionRosterSnapshot.Proficiency(legal.get(j).id().value(),role,j==0?(draw(seed,id,year,"RARE_MASTERY",100)<5?18:17):j==1?16:15+draw(seed,id,year,"MASTERY_"+j,2)));
            String region=regions.get(draw(seed,id,year,"REGION",regions.size()));String nickname=region+" 신인 "+year+"-"+sequence;
            var birth=date.minusYears(age).minusDays(draw(seed,id,year,"BIRTH_DATE",300));
            var details=mapper.createObjectNode();details.put("playerId",id);details.put("name",nickname);details.put("nickname",nickname);details.put("snapshotAt",date.toString());details.put("leagueContext",region);
            details.putObject("personal").putNull("birthDate").putArray("nationality").add(switch(region){case "LCK"->"South Korea";case "LPL"->"China";case "LEC"->"France";case "CBLOL"->"Brazil";case "LCP"->"Vietnam";default->"United States";});
            details.putObject("abilityMetadata").put("potentialAbility",pa);details.putObject("generated").put("source","GENERATED").put("intakeYear",year).put("createdOn",date.toString()).put("simulationBirthDate",birth.toString()).put("policyVersion",VERSION).put("emergency",emergency).put("archetype",type);
            var definition=new Definition(id,nickname,role,new CompetitionRosterSnapshot.Starter(id,nickname,role,ratings,prof),false,null,null,"UNAFFILIATED",null,details.toString());
            result.add(new Rookie(definition,new Age(null,birth,"GENERATED_GAME_BIRTH_DATE",date,date)));
        }
        return List.copyOf(result);
    }
}
