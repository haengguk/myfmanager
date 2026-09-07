package com.lolfm.career;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.player.ExpandedPlayerCatalog.Definition;
import java.time.*;
import java.util.*;
import static com.lolfm.career.CareerDevelopmentState.*;

/** Integer arithmetic only. No JDBC, clocks, Random, or mutable global state. */
public final class CareerDevelopmentPolicy {
    public static final String VERSION="CAREER_DEVELOPMENT_FIXED_POINT_V1";
    public static final String INITIALIZATION="SAVED_DIRECTORY_NO_RETROACTIVE_REWARD_V1";
    public static final int UNIT=1000,MAX=20000,TRAINING=10,GAME=60,GAME_FATIGUE=90,RECOVERY=120;
    public static final int CHAMPION_TRAINING=40,CHAMPION_GAME=80;
    public static final Plan DEFAULT=new Plan(Intensity.NORMAL,Focus.BALANCED,null,List.of());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<PlayerSkill> COMMON = Arrays.stream(PlayerSkill.values())
            .filter(s -> Arrays.stream(com.lolfm.domain.Position.values()).allMatch(s::appliesTo))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private CareerDevelopmentPolicy() {}
    public record Metadata(Integer potential,LocalDate birthday) {}
    public static Metadata metadata(Definition p) {
        try {
            var n=JSON.readTree(p.detailsJson());var pa=n.path("abilityMetadata").path("potentialAbility");
            String birth=n.path("personal").path("birthDate").asText("");LocalDate date=null;
            try {date=LocalDate.parse(birth);}catch(java.time.format.DateTimeParseException ignored){}
            return new Metadata(pa.isIntegralNumber()?pa.asInt():null,date);
        }catch(java.io.IOException e){throw new IllegalStateException("DEVELOPMENT_METADATA",e);}
    }
    public static int age(Metadata m,LocalDate date){return m.birthday()==null?23:Period.between(m.birthday(),date).getYears();}
    public static int ageFactor(int age){return age<=20?1000:age<=24?650:age<=27?300:80;}
    public static int ceiling(Integer pa){return pa==null?0:12000+(pa-1)*228000/199;}
    public static int sum(Player p){return p.internalRatings().values().stream().mapToInt(Integer::intValue).sum();}
    public static int efficiency(int fatigue){return fatigue<=300?1000:Math.max(250,1000-(fatigue-300)*750/700);}
    public static int load(Intensity i){return switch(i){case RECOVERY->0;case LIGHT->15;case NORMAL->40;case INTENSIVE->150;};}
    public static int trainingFactor(Intensity i){return switch(i){case RECOVERY->0;case LIGHT->500;case NORMAL->1000;case INTENSIVE->1200;};}
    public static String key(String champion,com.lolfm.domain.Position role){return champion+"|"+role.name();}
    public static Player initial(Definition d) {
        var ratings=new EnumMap<PlayerSkill,Integer>(PlayerSkill.class);d.gameplay().ratings().forEach((k,v)->ratings.put(k,v*UNIT));
        var prof=new TreeMap<String,Integer>();d.gameplay().proficiencies().forEach(p->prof.put(key(p.championId(),p.position()),p.value()*UNIT));
        return new Player(ratings,prof,0,Map.of(),Map.of(),0,null,null);
    }
    public static Plan validate(Plan p,Definition d,Set<String> legal) {
        if(p==null)throw new IllegalArgumentException("TRAINING_PLAN_REQUIRED");
        if((p.focus()==Focus.SPECIFIC_SKILL)!=(p.skill()!=null)||p.skill()!=null&&(d==null||!p.skill().appliesTo(d.position())))throw new IllegalArgumentException("TRAINING_SKILL_POSITION");
        if(p.focus()==Focus.CHAMPION_FOCUS) {
            if(d==null||p.champions().isEmpty()||p.champions().size()>2||new HashSet<>(p.champions()).size()!=p.champions().size()
                ||p.champions().stream().anyMatch(c->!legal.contains(key(c,d.position()))))throw new IllegalArgumentException("TRAINING_CHAMPION_TARGETS");
        }else if(!p.champions().isEmpty())throw new IllegalArgumentException("TRAINING_UNEXPECTED_CHAMPIONS");
        return p;
    }
    public static Schedule activate(Schedule s,LocalDate date,String team) {
        if(s==null||!Objects.equals(team,s.team()))return null;
        if(s.pendingOn()!=null&&!date.isBefore(s.pendingOn()))return s.pendingClear()?null:new Schedule(team,s.pending(),s.pendingOn(),null,null,false);
        return s;
    }
    public static Plan ai(Definition d,Metadata m,Player p,LocalDate date,int daysToMatch) {
        Intensity intensity=p.fatigue()>=600?Intensity.RECOVERY:p.fatigue()>=350||daysToMatch<=1?Intensity.LIGHT:
            age(m,date)<=20&&daysToMatch>4&&(date.getDayOfWeek()==DayOfWeek.TUESDAY||date.getDayOfWeek()==DayOfWeek.THURSDAY)?Intensity.INTENSIVE:Intensity.NORMAL;
        var weakest=PlayerSkill.orderedForPosition(d.position()).stream().min(Comparator.comparingInt(k->p.internalRatings().get(k)/UNIT)).orElseThrow();
        return new Plan(intensity,Focus.SPECIFIC_SKILL,weakest,List.of());
    }
    public static Player day(Player p,Definition d,Metadata m,LocalDate date,Plan plan,boolean autonomous) {
        boolean played=date.equals(p.playedOn());Player next=p;
        if(!played&&plan.intensity()!=Intensity.RECOVERY) {
            int factor=trainingFactor(plan.intensity())*(autonomous?50:100)/100;
            if(plan.focus()==Focus.CHAMPION_FOCUS) {
                next=grow(next,d,m,date,TRAINING,factor/2,efficiency(p.fatigue()),plan);
                // One shared daily time budget, split before proficiency-specific damping.
                int total=CHAMPION_TRAINING*factor;int count=plan.champions().size();
                for(int i=0;i<count;i++)next=proficiency(next,key(plan.champions().get(i),d.position()),total/count+(i<total%count?1:0),efficiency(p.fatigue()));
            }else next=grow(next,d,m,date,TRAINING,factor,efficiency(p.fatigue()),plan);
        }
        int fatigue=Math.max(0,Math.min(1000,next.fatigue()+(played?0:load(plan.intensity())))-RECOVERY-(plan.intensity()==Intensity.RECOVERY?80:0));
        return copy(next,fatigue,next.playedOn(),next.override());
    }
    public static Player game(Player p,Definition d,Metadata m,LocalDate date,String champion) {
        Player n=grow(p,d,m,date,GAME,1000,1000,DEFAULT);
        n=proficiency(n,key(champion,d.position()),CHAMPION_GAME*1000,1000);
        return copy(n,Math.min(1000,n.fatigue()+GAME_FATIGUE),date,n.override());
    }
    public static Player copy(Player p,int fatigue,LocalDate played,Schedule override){return new Player(p.internalRatings(),p.internalProficiencies(),p.remainder(),p.proficiencyRemainders(),p.cursors(),fatigue,played,override);}
    public static Player grow(Player p,Definition d,Metadata m,LocalDate date,int base,int intensity,int efficiency,Plan plan) {
        int remaining=Math.max(0,ceiling(m.potential())-sum(p));
        if(remaining==0)return new Player(p.internalRatings(),p.internalProficiencies(),0,p.proficiencyRemainders(),p.cursors(),p.fatigue(),p.playedOn(),p.override());
        int paFactor=Math.min(1000,remaining*1000/12000);
        // Denominator 10^12: age, intensity, efficiency, PA factors each per 1000.
        long numerator=p.remainder()+(long)base*ageFactor(age(m,date))*intensity*efficiency*paFactor;
        int budget=(int)Math.min(remaining,numerator/1_000_000_000_000L);long rest=numerator%1_000_000_000_000L;
        var ratings=new EnumMap<PlayerSkill,Integer>(PlayerSkill.class);ratings.putAll(p.internalRatings());var cursors=new TreeMap<>(p.cursors());
        var order=order(d,plan);String channel=plan.focus()+":"+plan.skill();var cursor=cursors.getOrDefault(channel,new Cursor(0,0));
        int index=cursor.index()%order.size(),filled=cursor.filled(),misses=0;
        while(budget>0&&misses<order.size()) {
            var skill=order.get(index);int room=MAX-ratings.get(skill);
            if(room==0){index=(index+1)%order.size();filled=0;misses++;continue;}
            int amount=Math.min(budget,Math.min(UNIT-filled,room));ratings.put(skill,ratings.get(skill)+amount);budget-=amount;filled+=amount;misses=0;
            if(filled==UNIT||ratings.get(skill)==MAX){index=(index+1)%order.size();filled=0;}
        }
        cursors.put(channel,new Cursor(index,filled));
        if(ratings.values().stream().mapToInt(Integer::intValue).sum()>=ceiling(m.potential()))rest=0;
        return new Player(ratings,p.internalProficiencies(),rest,p.proficiencyRemainders(),cursors,p.fatigue(),p.playedOn(),p.override());
    }
    static List<PlayerSkill> order(Definition d,Plan plan) {
        var all=PlayerSkill.orderedForPosition(d.position());var out=new ArrayList<PlayerSkill>();
        if(plan.focus()==Focus.COMMON_SKILLS||plan.focus()==Focus.ROLE_SKILLS) {
            var preferred=all.stream().filter(s->COMMON.contains(s)==(plan.focus()==Focus.COMMON_SKILLS)).toList();
            var other=all.stream().filter(s->!preferred.contains(s)).toList();int first=0,second=0;
            // Interleave 7:3 group blocks; do not postpone the secondary group for 42 points.
            for(int block=0;block<60;block++) {
                boolean secondary=Set.of(2,5,8).contains(block%10);
                out.add(secondary?other.get(second++%other.size()):preferred.get(first++%preferred.size()));
            }
        }else if(plan.focus()==Focus.SPECIFIC_SKILL) {
            // Interleave 13 target chunks with 11 other chunks (~54:46 over a complete cycle).
            for(var skill:all)if(skill!=plan.skill()){out.add(plan.skill());out.add(skill);}out.add(plan.skill());out.add(plan.skill());
        }else out.addAll(all);
        return out;
    }
    static Player proficiency(Player p,String key,int milliBudget,int efficiency) {
        int value=p.internalProficiencies().getOrDefault(key,14000);
        if(value==MAX)return p;
        int damping=Math.min(1000,Math.max(100,(MAX-value)*1000/6000));
        long numerator=p.proficiencyRemainders().getOrDefault(key,0L)+(long)milliBudget*efficiency*damping;
        int next=(int)Math.min(MAX,value+numerator/1_000_000_000L);
        var prof=new TreeMap<>(p.internalProficiencies());prof.put(key,next);
        var remainders=new TreeMap<>(p.proficiencyRemainders());remainders.put(key,next==MAX?0:numerator%1_000_000_000L);
        return new Player(p.internalRatings(),prof,p.remainder(),remainders,p.cursors(),p.fatigue(),p.playedOn(),p.override());
    }
}
