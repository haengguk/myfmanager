package com.lolfm.career;

import java.time.*;
import java.util.*;

/** Adopted regional formats. Missing public operational details are explicit game policies. */
final class CareerOverseasRules {
    static final String VERSION="CAREER_OVERSEAS_EXECUTION_V1";
    static final String SOURCE_HASH="3a0fdf9602c84c1f2351141138385502aa46c2eff240354bd4195e5ed6b3f163";
    static final String SIDE=CareerDomesticCompetition.SIDE_POLICY;
    static final String SELECTION="OVERSEAS_SEEDED_FIRST_MEETING_RETURN_ALTERNATION_HIGHER_SEED_KNOCKOUT_V1";
    static final int LAST_CHANCE_BO=3; // GAME_POLICY: N01/N02 do not publish a length; never inherited from Swiss.
    static final int SCHEDULE_EXTENSION_DAYS=7;
    static LocalDate scheduleLimit(String competition,int year){
        if(isOverseas(competition))return Event.valueOf(competition).date(year,Event.valueOf(competition).end).plusDays(SCHEDULE_EXTENSION_DAYS);
        String end=switch(competition){case "FIRST_STAND"->"03-22";case "MSI"->"07-12";case "EWC_LOL"->"07-19";case "WORLDS"->"11-14";default->throw new IllegalArgumentException("SCHEDULE_EVENT");};
        return LocalDate.parse(year+"-"+end).plusDays(SCHEDULE_EXTENSION_DAYS);
    }
    enum Event {
        LPL_SPLIT_1("LPL","LPL Split 1","01-14","02-06","02-09","03-08",14),
        LPL_SPLIT_2("LPL","LPL Split 2","04-04","05-17","05-23","06-14",14),
        LPL_SPLIT_3("LPL","LPL Split 3","07-22","08-23","08-28","09-13",12),
        LPL_REGIONAL_FINALS("LPL","LPL Regional Finals","09-17","09-17","09-17","09-19",4),
        LEC_VERSUS("LEC","LEC Versus","01-17","02-08","02-16","03-01",12),
        LEC_SPRING("LEC","LEC Spring","03-28","05-10","05-23","06-07",10),
        LEC_SUMMER("LEC","LEC Summer","07-24","08-30","09-05","09-20",10),
        LCP_SPLIT_1("LCP","LCP Split 1","01-16","02-08","02-14","03-01",8),
        LCP_SPLIT_2("LCP","LCP Split 2","04-04","05-17","05-23","06-07",8),
        LCP_SPLIT_3("LCP","LCP Split 3","07-25","08-12","08-13","08-30",8),
        CBLOL_COPA("CBLOL","Copa CBLOL","01-17","02-01","02-03","03-01",8),
        CBLOL_ETAPA_1("CBLOL","CBLOL Etapa 1","03-28","05-03","05-09","06-06",8),
        CBLOL_ETAPA_2("CBLOL","CBLOL Etapa 2","07-25","08-30","09-05","10-10",8),
        LCS_LOCK_IN("LCS","LCS Lock-In","01-24","02-08","02-09","03-01",8),
        LCS_SPRING("LCS","LCS Spring","04-04","05-17","05-23","06-14",8),
        LCS_SUMMER("LCS","LCS Summer","07-25","09-06","09-12","10-04",8),
        AMERICAS_CUP("LCS","Americas Cup","03-04","03-04","03-04","03-08",4);
        final String league,label,start,regularEnd,post,end;final int count;
        Event(String league,String label,String start,String regularEnd,String post,String end,int count){this.league=league;this.label=label;this.start=start;this.regularEnd=regularEnd;this.post=post;this.end=end;this.count=count;}
        LocalDate date(int year,String md){return MonthDay.parse("--"+md).atYear(year);}
        String reference(){return name().toLowerCase(Locale.ROOT)+"_2026";}
        Event previous(){return switch(this){case LPL_SPLIT_2->LPL_SPLIT_1;case LPL_SPLIT_3->LPL_SPLIT_2;case LPL_REGIONAL_FINALS->LPL_SPLIT_3;case LEC_SPRING->LEC_VERSUS;case LEC_SUMMER->LEC_SPRING;case LCP_SPLIT_2->LCP_SPLIT_1;case LCP_SPLIT_3->LCP_SPLIT_2;case CBLOL_ETAPA_1->CBLOL_COPA;case CBLOL_ETAPA_2->CBLOL_ETAPA_1;case LCS_SPRING->LCS_LOCK_IN;case LCS_SUMMER->LCS_SPRING;default->null;};}
    }
    static boolean isOverseas(String id){return Arrays.stream(Event.values()).anyMatch(e->e.name().equals(id));}
    static List<String> partners(String league){return switch(league){
        case "LPL"->tokens(league,"AL BLG EDG IG JDG LGD LNG NIP OMG TT TES UP WE WBG");
        case "LEC"->tokens(league,"FNC G2 GX KC MKOI NAVI SHFT SK TH VIT");
        case "LCP"->tokens(league,"CFO DFM DCG GAM GZ MVK SHG TSW");
        case "LCS"->tokens(league,"C9 DIG DSG FLY LYON SEN SR TLAW");
        case "CBLOL"->tokens(league,"FXW7 FUR LEV LOS LOUD PNG RED VKS");default->throw new IllegalArgumentException("OVERSEAS_LEAGUE");};}
    private static List<String> tokens(String league,String codes){return Arrays.stream(codes.split(" ")).map(c->league+":"+c).toList();}
    static List<String> lplEntrants(Event event,List<String> previous,List<String> eliminated){
        if(event==Event.LPL_SPLIT_1)return partners("LPL");
        var teams=new ArrayList<>(previous);if(event==Event.LPL_SPLIT_3)teams.removeAll(eliminated);
        if(teams.size()!=event.count||teams.stream().distinct().count()!=event.count)throw new IllegalArgumentException("LPL_SPLIT_PARTICIPANTS");
        return List.copyOf(teams);
    }
    static CareerCompetitionRules.CompetitionRule rule(String id){var e=Event.valueOf(id);return new CareerCompetitionRules.CompetitionRule(id,"GAME_POLICY_DEFINED",null,List.of(),"MIXED_BO1_BO3_BO5",true,List.of(),List.of(e.start,e.end));}
    static int lplPoints(Event event,int place){int[] table=switch(event){case LPL_SPLIT_1->new int[]{80,50,40,20,10,10,5,5};case LPL_SPLIT_2->new int[]{110,80,50,30,15,15,10,10};case LPL_SPLIT_3->new int[]{300,110,80,50,30,30,15,15};default->new int[0];};return place<=table.length?table[place-1]:0;}
    static int lcpRegularPoints(Event event,int place,int won,int lost){return Math.max(0,(won-lost)*(event==Event.LCP_SPLIT_2?2:1))+8-place;}
    static int lcpSwissPoints(int wins,int losses){if(wins==3)return new int[]{50,40,30}[losses];if(losses==3)return new int[]{0,3,15}[wins];throw new IllegalArgumentException("SWISS_NOT_FINAL");}
    private CareerOverseasRules(){}
}
