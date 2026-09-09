package com.lolfm.career;

import java.util.*;

/** Fixed before results. Regional season representation is separate from prize entitlements and CP qualification. */
public final class CareerSportingFinancePolicy {
    public static final String VERSION="CAREER_SPORTING_FINANCE_V2";
    public static final int TOP_PERCENT=25,MIDDLE_PERCENT=60;
    public record Scope(String policyVersion,String region,String competition,int participants) {}
    public record Result(Integer domesticRankThrough,Integer worldsRankThrough,String worldsStatus) {}
    static final Map<String,String> COMPETITIONS=Map.of("LCK","LCK_PLAYOFFS","LPL","LPL_SPLIT_3","LEC","LEC_SUMMER","LCS","LCS_SUMMER","LCP","LCP_SPLIT_3","CBLOL","CBLOL_ETAPA_2");
    static Scope scope(String region){return new Scope(VERSION,region,COMPETITIONS.get(region),region.equals("LCK")?10:CareerOverseasRules.partners(region).size());}
    static int required(Scope scope,int rank){
        if(scope.region().equals("LCK"))return rank<=3?3:rank<=6?6:10;
        int top=(scope.participants()*TOP_PERCENT+99)/100,mid=(scope.participants()*MIDDLE_PERCENT+99)/100;
        return rank<=top?top:rank<=mid?mid:scope.participants();
    }
    static String evaluate(int target,CareerFinanceEngine.Rank actual,Integer worldsTarget,CareerFinanceEngine.Rank world,String worldsStatus){
        if(target<=0||actual==null||worldsTarget!=null&&world==null&&!"NOT_QUALIFIED".equals(worldsStatus))return "NOT_EVALUABLE";
        // Shared placement crossing the boundary counts as reaching it, but never as exceeding it.
        if(actual.from()>target||worldsTarget!=null&&(world==null||world.from()>worldsTarget))return "MISSED";
        return (actual.through()==1||actual.through()<target)&&(worldsTarget==null||world.through()<=CareerFinancePolicy.WORLDS_EXCEEDED)?"EXCEEDED":"MET";
    }
    private CareerSportingFinancePolicy(){}
}
