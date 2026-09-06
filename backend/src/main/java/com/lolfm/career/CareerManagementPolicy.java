package com.lolfm.career;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import com.lolfm.player.ExpandedPlayerCatalog.Definition;

/** Initial game design values; no potential, growth, real transfer prices or match Random. */
public final class CareerManagementPolicy {
    public static final String VERSION="CAREER_PROMISE_TRANSFER_LOAN_V1";
    public static final int OBSERVATION_DAYS=28, MIN_SERIES=6, EVALUATION_DAYS=14;
    public static final int INITIAL_SATISFACTION=60, INITIAL_TRUST=50, STARTER_PERCENT=70;
    public static final int BREACH_LOSS=4, TRUST_LOSS=3, ARREARS_LOSS=6, RECOVERY=2, TRUST_RECOVERY=1;
    public static final int MOVE_INTEREST=40, TRUST_SCORE_WEIGHT=12, SATISFACTION_SCORE_WEIGHT=8;
    public static final int LOAN_MIN_DAYS=28, LOAN_MAX_DAYS=366, LOAN_FEE_PERCENT=20, LOAN_SHARE_PERCENT=50;
    public static final int TRADE_DECISION_DAYS=7, TRADE_RESPONSE_DAYS=2, TRADE_MAX_ROUNDS=3, RECONTACT_DAYS=28;
    public static final int SELL_BASE_PERCENT=110, SELL_STARTER_PREMIUM=25, SELL_UNHAPPY_DISCOUNT=15, SELL_ARREARS_DISCOUNT=10;
    public static final int BUY_BASE_PERCENT=110, BUY_URGENT_PERCENT=150, BUY_IMPROVEMENT_PERCENT=130;
    public static final int AI_TRADE_CANDIDATES=3, AI_MAX_NEW_TRADES=2, LOAN_ACCEPT_SCORE=5800;
    private static final int[] MONTHS={0,6,12,24,36}, COEFFICIENT_PERMILLE={0,500,1000,1600,2100};
    private CareerManagementPolicy() {}
    /** Single lookup boundary for permanent saved Career ratings; future growth can supply this definition. */
    public static long referenceSalary(Definition player) {return CareerMarketPolicy.demand(player);}
    public static long value(Definition player,LocalDate date,LocalDate inclusiveEnd) {
        if(inclusiveEnd.isBefore(date))return 0;
        long remaining=ChronoUnit.DAYS.between(date,inclusiveEnd.plusDays(1));
        for(int i=1;i<MONTHS.length;i++) {
            long upper=ChronoUnit.DAYS.between(date,date.plusMonths(MONTHS[i]));
            if(remaining<=upper) {
                long lower=ChronoUnit.DAYS.between(date,date.plusMonths(MONTHS[i-1]));
                long numerator=COEFFICIENT_PERMILLE[i-1]*(upper-lower)+(remaining-lower)*(COEFFICIENT_PERMILLE[i]-COEFFICIENT_PERMILLE[i-1]);
                return Math.multiplyExact(referenceSalary(player),numerator)/Math.multiplyExact(1000L,upper-lower);
            }
        }
        return Math.multiplyExact(referenceSalary(player),COEFFICIENT_PERMILLE[4])/1000;
    }
    public static long loanFee(Definition player,LocalDate from,LocalDate through) {
        return CareerMarketPolicy.wages(referenceSalary(player),from,through)*LOAN_FEE_PERCENT/100;
    }
    public static int clamp(int value) {return Math.max(0,Math.min(100,value));}
}
