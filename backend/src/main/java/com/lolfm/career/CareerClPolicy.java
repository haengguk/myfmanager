package com.lolfm.career;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Explicit game policy, not a claim to reproduce the official CL rulebook. */
public final class CareerClPolicy {
    public static final String ID="LCK_CL",VERSION="LCK_CL_GAME_POLICY_V1";
    public static final List<String> TEAMS=List.of("BFX","BRO","DK","DNS","GEN","HLE","KRX","KT","NS","T1");
    public static final int MINIMUM_PLAYERS=5,MAXIMUM_REGISTERED=15;
    public static final String SIDE=CareerDomesticCompetition.SIDE_POLICY;
    private CareerClPolicy(){}
    public record Scheduled(String id,int round,LocalDate date,String first,String second) {}
    public static List<Scheduled> schedule(int year) {
        LocalDate start=LocalDate.of(year,4,1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        var ring=new ArrayList<>(TEAMS);var first=new ArrayList<Scheduled>();
        for(int round=0;round<9;round++) {
            for(int pair=0;pair<5;pair++) {
                String a=ring.get(pair),b=ring.get(9-pair);if((round+pair)%2==1){String swap=a;a=b;b=swap;}
                first.add(new Scheduled(String.format(Locale.ROOT,"CL_R%02d_M%d",round+1,pair+1),round+1,start.plusWeeks(round),a,b));
            }
            ring.add(1,ring.removeLast());
        }
        var all=new ArrayList<>(first);for(var m:first)all.add(new Scheduled(String.format(Locale.ROOT,"CL_R%02d_M%d",m.round()+9,first.indexOf(m)%5+1),m.round()+9,m.date().plusWeeks(9),m.second(),m.first()));
        return List.copyOf(all);
    }
    static CareerCompetitionRules.CompetitionRule rule(){return new CareerCompetitionRules.CompetitionRule(ID,"GAME_POLICY_DEFINED",null,List.of(),"MIXED_BO1_BO3_BO5",true,List.of(),List.of("04-01","09-07"));}
    static String teamToken(String code){return "LCK:"+code;}
    static boolean isCl(String competition){return ID.equals(competition);}
}
