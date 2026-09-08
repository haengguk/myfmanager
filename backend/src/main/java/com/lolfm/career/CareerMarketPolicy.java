package com.lolfm.career;

import com.lolfm.player.ExpandedPlayerCatalog.Definition;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.lolfm.career.CareerMarketState.*;

/** Explicit game assumptions, not real wages, personalities, employment law or residency rules. */
public final class CareerMarketPolicy {
    public static final String VERSION="CAREER_CONTRACT_MARKET_GAME_POLICY_V1";
    public static final String FUNDING_POLICY="PAYROLL_CASH_FLOW_AND_ARREARS_RECOVERY_V2";
    public static final Set<String> INITIAL_GAME_FREE_AGENTS=Set.of("player-bo","player-beryl","player-fofo","player-fate");
    public static final String CURRENCY="GAME_CREDITS";
    public static final int REFERENCE_YEAR=2026, NEGOTIATION_DAYS=60, PROTECTION_DAYS=60;
    public static final int RESPONSE_DAYS=2, DECISION_DAYS=5, MAX_ROUNDS=3, MAX_YEARS=3;
    public static final int AI_CANDIDATES=3, MAX_POSITION_PLAYERS=2, MIN_ROSTER_LIMIT=10;
    public static final int MIN_ACCEPT_SCORE=6200, MIN_SALARY_PERCENT=75, COUNTER_SALARY_PERCENT=105;
    public static final long SALARY_PER_RATING_POINT=1000, MIN_BUDGET=1_200_000, MAX_MONEY=CareerFinancePolicy.MAX_MONEY;
    public static final int BUDGET_PERCENT=160, INITIAL_CASH_YEARS=2, RELEASE_COST_PERCENT=25;
    public static final int AI_MIN_BID_PERCENT=105, AI_BID_VARIANTS=21, AI_MAX_BID_PERCENT=135, AI_CONTRACT_YEARS=2, AI_BONUS_DIVISOR=10;
    public static final int AI_RENEWAL_ADVANTAGE=8, AI_IMPROVEMENT_POINTS=12, FA_START_DELAY_DAYS=7;
    public static final int NEAR_EQUAL_SCORE_BAND=10, TIE_VARIANTS=11, COMPENSATION_AT_DEMAND=80;
    public static final int SCORE_MAX=100, STARTER_OPPORTUNITY_FLOOR=25, STRONGER_COMPETITOR_PENALTY=40, CROWDING_PENALTY=10;
    public static final int RESERVE_OPPORTUNITY=50, DEVELOPMENT_OPPORTUNITY=35, MAX_PLAYER_STRENGTH=240;
    private CareerMarketPolicy() {}
    public static long demand(Definition player) {
        return (long)strength(player)*SALARY_PER_RATING_POINT;
    }
    public static int strength(Definition player) { return com.lolfm.player.PlayerAbilityPolicy.strength(player.gameplay().ratings()); }
    public static int variation(long seed,String identity,int bound) {
        String hash=CareerRosterStore.hash(VERSION+'|'+seed+'|'+identity);
        return (int)(Long.parseUnsignedLong(hash.substring(0,15),16)%bound);
    }
    public static Preference preference(long seed, Definition player) {
        int variant=variation(seed,"PREFERENCE|"+player.playerId(),3);
        return new Preference(variant==0?40:30,variant==1?35:25,20,10,variant==2?15:5,
                3+variation(seed,"RELOCATION|"+player.playerId(),8),
                List.of("COMPARE_OFFERS","SEEK_OPPORTUNITY","PREFER_RENEWAL").get(variant),
                player.initialOwnerTeam()==null?null:region(player.initialOwnerTeam()));
    }
    public static String region(String team) { return team.substring(0,team.indexOf(':')); }
    public static boolean overlaps(Terms a,Terms b) { return !a.startDate().isAfter(b.endDate()) && !b.startDate().isAfter(a.endDate()); }
    public static void validate(Terms terms) {
        if(terms==null || terms.startDate()==null || terms.endDate()==null || terms.role()==null
                || terms.endDate().isBefore(terms.startDate().plusMonths(6).minusDays(1))
                || terms.endDate().isAfter(terms.startDate().plusYears(MAX_YEARS).minusDays(1))
                || terms.annualSalary()<1 || terms.annualSalary()>MAX_MONEY
                || terms.signingBonus()<0 || terms.signingBonus()>MAX_MONEY)
            throw CareerException.invalid("terms","계약은 6개월~3년, 양의 연봉과 허용 범위의 계약금이 필요합니다.");
    }
    /** Exact integral prorating; adjacent ranges use cumulative floors and never lose a day's rounding. */
    public static long wages(long annual, LocalDate from, LocalDate through) {
        if(through.isBefore(from))return 0;
        long result=0;for(int year=from.getYear();year<=through.getYear();year++) {
            LocalDate start=LocalDate.of(year,1,1), end=LocalDate.of(year,12,31);
            LocalDate a=from.isAfter(start)?from:start,b=through.isBefore(end)?through:end;
            long days=start.lengthOfYear();
            result+=annual*(ChronoUnit.DAYS.between(start,b)+1)/days-annual*ChronoUnit.DAYS.between(start,a)/days;
        }return result;
    }
    public static long releaseCost(Contract c,LocalDate date) {
        return wages(c.terms().annualSalary(),date.plusDays(1),c.terms().endDate())*RELEASE_COST_PERCENT/100;
    }
}
