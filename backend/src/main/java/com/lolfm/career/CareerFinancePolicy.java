package com.lolfm.career;

import java.math.*;
import java.time.LocalDate;
import java.util.*;
import static com.lolfm.career.CareerFinanceState.*;

/** Explicit game settings, not market FX, legal taxes, disclosed wages or actual sponsorship agreements. */
public final class CareerFinancePolicy {
    public static final String VERSION="CAREER_FINANCE_KRW_V1", FX="GAME_FIXED_FX_V1", FUNDING="GAME_RECURRING_FINANCE_V1";
    public static final long LEGACY_KRW=1000,MAX_MONEY=100_000_000_000L,MAX_SAFE=9_007_199_254_740_991L;
    public static final int SUPPORT_PERCENT=70,SPONSOR_PERCENT=30,PRIZE_DELAY=7,EWC_DELAY=42,
            SUPPORT_FLOOR=80,SUPPORT_CEILING=120,RESERVE_MONTHS=2,REFERENCE_STRENGTH=180,
            TARGET_BONUS_MET=5,TARGET_BONUS_EXCEEDED=10,FUNDING_CHANGE=5,TOP_TARGET=3,MID_TARGET=6,LOW_TARGET=10,WORLDS_TARGET=8,WORLDS_EXCEEDED=4;
    public static final long DEFAULT_DEVELOPMENT_REFERENCE=45_000_000;
    public static final Map<String,String> EVENT_IDS=events();
    private static Map<String,String> events(){var events=new TreeMap<>(Map.of("LCK_CUP","lck_cup_2026","LCK_PLAYOFFS","lck_season_2026",
            "LCK_CL","lck_cl_season_2026","FIRST_STAND","first_stand_2026","MSI","msi_2026","EWC_LOL","ewc_lol_2026","WORLDS","worlds_2026"));
        for(var event:CareerOverseasRules.Event.values())events.put(event.name(),event.reference());
        return Collections.unmodifiableMap(events);
    }
    private CareerFinancePolicy(){}
    public static long safe(long amount){if(amount>MAX_SAFE||amount< -MAX_SAFE)throw new IllegalArgumentException("MONEY_SAFE_INTEGER");return amount;}
    public static long convert(long original,String rate){return safe(BigDecimal.valueOf(original).multiply(new BigDecimal(rate)).setScale(0,RoundingMode.HALF_UP).longValueExact());}
    public static long legacy(long amount){return safe(Math.multiplyExact(amount,LEGACY_KRW));}
    public static long ratio(long amount,long numerator,long denominator){return safe(BigInteger.valueOf(amount).multiply(BigInteger.valueOf(numerator)).divide(BigInteger.valueOf(denominator)).longValueExact());}
    public static long pct(long amount,long percent){return ratio(amount,percent,100);}
    public static Map<String,Long> allocate(long total,Map<String,Integer> weights){
        var result=new TreeMap<String,Long>();long sum=weights.values().stream().mapToLong(Integer::longValue).sum();if(sum==0)return result;
        long allocated=0;for(String id:new TreeSet<>(weights.keySet())){long value=ratio(total,weights.get(id),sum);result.put(id,value);allocated+=value;}
        long rest=total-allocated;for(String id:result.keySet())if(rest-->0)result.put(id,result.get(id)+1);return result;
    }
    public static long placement(PrizeRule rule,int from,int through,boolean sharedCl){
        if(!rule.complete())throw new IllegalArgumentException("PRIZE_CLASSIFICATION_UNAVAILABLE");
        if(sharedCl&&rule.eventId().equals("lck_cl_season_2026")&&from==3&&through==4)return (placement(rule,3,3,false)+placement(rule,4,4,false))/2;
        var values=new TreeSet<Long>();for(int rank=from;rank<=through;rank++){final int r=rank;values.add(rule.placements().stream().filter(p->p.from()<=r&&p.through()>=r).findFirst().orElseThrow(()->new IllegalArgumentException("PRIZE_RANK_MISSING")).amount());}
        if(values.size()!=1)throw new IllegalArgumentException("PRIZE_DISTINCT_RANK_REQUIRED");return values.first();
    }
    public static LocalDate due(String competition,LocalDate completed){return completed.plusDays(competition.equals("EWC_LOL")?EWC_DELAY:PRIZE_DELAY);}
}
