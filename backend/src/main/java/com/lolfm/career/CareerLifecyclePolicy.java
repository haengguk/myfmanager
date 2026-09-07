package com.lolfm.career;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.domain.*;
import com.lolfm.player.ExpandedPlayerCatalog.Definition;
import com.lolfm.player.PlayerAbilityPolicy;
import java.time.*;
import java.util.*;
import static com.lolfm.career.CareerLifecycleState.*;

/** Small deterministic life rules. Purpose hashes never consume match or market Random. */
public final class CareerLifecyclePolicy {
    public static final String VERSION="CAREER_PLAYER_LIFECYCLE_RETIREMENT_AND_ROOKIE_SUPPLY_V1";
    public static final int TARGET_ACTIVE=470,MAX_ACTIVE=490,NORMAL_MAX=36,EMERGENCY_MAX=60;
    public static final int MIN_OBSERVED_SERIES=12,MIN_OBSERVED_DAYS=120,YOUNG_SLOTS=2,YOUNG_BUDGET_PERCENT=15;
    public static final int YOUNG_CANDIDATES=3,YOUNG_SALARY_PERCENT=110,YOUNG_CONTRACT_YEARS=2,CONSIDERING_THRESHOLD=20;
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Set<PlayerSkill> EXECUTION=Set.of(PlayerSkill.MECHANICS,PlayerSkill.POSITIONING,PlayerSkill.COMBAT_EXECUTION,PlayerSkill.FARMING,PlayerSkill.TRADING,PlayerSkill.LANE_PRESSURE,PlayerSkill.LANE_INTERVENTION,PlayerSkill.OBJECTIVE_SECURE,PlayerSkill.LANE_SUPPORT,PlayerSkill.ENGAGE_EXECUTION,PlayerSkill.ALLY_PROTECTION);
    private CareerLifecyclePolicy(){}
    public static int draw(long seed,String player,int year,String purpose,int bound) {
        String hash=CareerRosterStore.hash(VERSION+'|'+seed+'|'+player+'|'+year+'|'+purpose);
        return (int)Long.remainderUnsigned(Long.parseUnsignedLong(hash.substring(0,16),16),bound);
    }
    public static int age(Age age,LocalDate date){return Period.between(age.simulationBirthDate(),date).getYears();}
    public static Age ageProfile(Definition d,long seed,LocalDate applied) {
        try {
            var details=JSON.readTree(d.detailsJson());LocalDate publicDate=null;
            try{publicDate=LocalDate.parse(details.path("personal").path("birthDate").asText());}catch(java.time.format.DateTimeParseException ignored){}
            if(publicDate!=null)return new Age(publicDate,publicDate,"PUBLIC_BIRTH_DATE",publicDate,applied);
            LocalDate reference=LocalDate.of(2026,8,24);
            try{reference=LocalDate.parse(details.path("snapshotAt").asText());}catch(java.time.format.DateTimeParseException ignored){}
            var known=details.path("personal").path("ageAsOfSnapshot");int years;String basis;
            if(known.isIntegralNumber()&&known.asInt()>=15&&known.asInt()<=60){years=known.asInt();basis="REPORTED_AGE_AT_SNAPSHOT";}
            else if("DEVELOPMENT".equals(d.initialSquad())){years=18+draw(seed,d.playerId(),reference.getYear(),"ESTIMATED_DEVELOPMENT_AGE",3);basis="GAME_ESTIMATE_DEVELOPMENT_18_20";}
            else {years=23;basis="GAME_DEFAULT_23_AT_2026_REFERENCE";}
            var birthday=reference.minusYears(years).minusDays(draw(seed,d.playerId(),reference.getYear(),"SIMULATION_BIRTH_DATE",300));
            return new Age(null,birthday,basis,reference,applied);
        }catch(java.io.IOException e){throw new IllegalStateException("LIFECYCLE_AGE_METADATA",e);}
    }
    public static int ca(CareerDevelopmentState.Player p) {var ratings=new EnumMap<PlayerSkill,Integer>(PlayerSkill.class);p.internalRatings().forEach((k,v)->ratings.put(k,v/1000));return PlayerAbilityPolicy.currentAbility(ratings);}
    public static int baseDecline(int age){return age<=24?0:switch(age){case 25->400;case 26->800;case 27->1500;case 28->2600;case 29->4000;case 30->6000;default->8000;};}
    public static int declineLimit(int age){return age<=27?3000:age==28?4000:age==29?5000:6000;}
    public static int activityFactor(Observation o) {
        if(!o.full())return 1000;
        if(o.sets()==0)return 1250;
        int ratio=o.starts()*100/o.opportunities();return ratio>=60?850:ratio<20?1150:1000;
    }
    public record Decline(CareerDevelopmentState.Player player,int budget,int applied,int carried,int limited,int cursor,Map<String,Integer> deltas) {}
    public static Decline decline(CareerDevelopmentState.Player p,Position role,int age,int remainder,int cursor,Observation observation) {
        int budget=baseDecline(age)*activityFactor(observation)/1000;int accumulated=budget+remainder;
        int carried=accumulated%1000,available=Math.min(accumulated/1000*1000,declineLimit(age));
        var ratings=new EnumMap<PlayerSkill,Integer>(p.internalRatings());var changes=new TreeMap<String,Integer>();var order=declineOrder(role,age);
        int applied=0,attempts=0;
        while(applied<available&&attempts<order.size()) {
            var skill=order.get(Math.floorMod(cursor++,order.size()));attempts++;
            if(changes.containsKey(skill.name())||ratings.get(skill)<2000)continue;
            ratings.put(skill,ratings.get(skill)-1000);changes.put(skill.name(),-1000);applied+=1000;
        }
        var next=new CareerDevelopmentState.Player(ratings,p.internalProficiencies(),p.remainder(),p.proficiencyRemainders(),p.cursors(),p.fatigue(),p.playedOn(),p.override(),p.growthSubRemainder());
        return new Decline(next,budget,applied,carried,accumulated-carried-applied,Math.floorMod(cursor,order.size()),changes);
    }
    static List<PlayerSkill> declineOrder(Position role,int age) {
        var all=PlayerSkill.orderedForPosition(role);var execution=all.stream().filter(EXECUTION::contains).toList();
        var stability=all.stream().filter(s->s==PlayerSkill.CONSISTENCY).toList();var strategy=all.stream().filter(s->!EXECUTION.contains(s)&&s!=PlayerSkill.CONSISTENCY).toList();
        int ew=age>=30?50:age>=27?65:60,sw=age>=30?35:age>=27?25:30;
        var result=new ArrayList<PlayerSkill>();int e=0,s=0,c=0;
        for(int i=0;i<1200;i++){int slot=i*37%100;var group=slot<ew?execution:slot<ew+sw?strategy:stability;
            if(group.isEmpty())group=all;int at=group==execution?e++:group==strategy?s++:c++;result.add(group.get(at%group.size()));}
        return result;
    }
    public static int retirementProbability(int age,Observation o,int emptySeasons,boolean longYoungFA,boolean fa,int caDrop,int currentCA,int satisfaction,boolean nextContract) {
        if(age<=24&&!longYoungFA)return 0;
        int chance=age<=24?5:switch(age){case 25->1;case 26->3;case 27->7;case 28->14;case 29->25;case 30->40;case 31->58;default->75;};
        if(o.full()&&o.sets()==0)chance+=8+(emptySeasons>=3?20:emptySeasons==2?10:0);
        if(fa)chance+=10;if(caDrop>=4)chance+=8;if(satisfaction<=35)chance+=6;
        if(o.full()&&o.opportunities()>0&&o.starts()*100/o.opportunities()>=60)chance-=12;
        if(currentCA>=180)chance-=10;if(nextContract)chance-=8;return Math.max(0,Math.min(95,chance));
    }
    public static boolean longYoungFA(Person person,LocalDate applied,int year) {
        var firstFullSeason=LocalDate.of(year-1,1,1);
        return person.freeAgentSince()!=null&&!person.freeAgentSince().isAfter(firstFullSeason)&&!applied.isAfter(firstFullSeason)
            &&person.lastReviewYear()!=null&&person.lastReviewYear()==year-1;
    }
    public static int normalCount(int active){return Math.min(NORMAL_MAX,Math.min(Math.max(0,MAX_ACTIVE-active),Math.max(8,TARGET_ACTIVE-active)));}
    public static Map<Position,Integer> allocate(int count,Map<Position,Integer> supply) {
        var allocation=new EnumMap<Position,Integer>(Position.class);for(var role:Position.values())allocation.put(role,0);
        if(count>=5)for(var role:Position.values()){allocation.put(role,1);count--;}
        while(count-->0){var next=Arrays.stream(Position.values()).min(Comparator.comparingInt((Position p)->supply.getOrDefault(p,0)+allocation.get(p)).thenComparing(Enum::name)).orElseThrow();allocation.merge(next,1,Integer::sum);}
        return allocation;
    }
    public static Map<Position,Integer> emergencyAllocation(Map<Position,Integer> missing,Map<Position,Integer> supply,Map<Position,Integer> normal) {
        var gap=new EnumMap<Position,Integer>(Position.class);var result=new EnumMap<Position,Integer>(Position.class);
        for(var role:Position.values()){gap.put(role,Math.max(0,missing.getOrDefault(role,0)-supply.getOrDefault(role,0)-normal.getOrDefault(role,0)));result.put(role,0);}
        int count=Math.min(EMERGENCY_MAX,gap.values().stream().mapToInt(Integer::intValue).sum());
        for(int i=0;i<count;i++){var role=Arrays.stream(Position.values()).max(Comparator.comparingInt((Position r)->gap.get(r)-result.get(r)).thenComparing(Enum::name)).orElseThrow();result.merge(role,1,Integer::sum);}
        return result;
    }
    public static LocalDate retirementDate(int year,LocalDate announcement){var normal=LocalDate.of(year+1,1,1);return normal.isAfter(announcement)?normal:announcement.plusDays(1);}
}
