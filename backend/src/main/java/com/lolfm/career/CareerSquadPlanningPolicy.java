package com.lolfm.career;

import java.time.LocalDate;
import java.util.*;
import com.lolfm.domain.Position;
import static com.lolfm.career.CareerMarketPolicy.*;

/** Current ability only. Thresholds use the existing twelve-rating strength scale. */
public final class CareerSquadPlanningPolicy {
    public static final String VERSION="CAREER_SQUAD_PLANNING_V2", LEGACY="CAREER_SQUAD_PLANNING_V1";
    public static final int IMPROVEMENT=AI_IMPROVEMENT_POINTS,REVIEW_WAIT_DAYS=28;
    public static final int COVERAGE_CANDIDATES=24,COMMON_CHECKS=8;
    public static final int CANDIDATES=3,NEW_PROPOSALS_PER_CLUB=4,TRADES_PER_CLUB=2;
    public static final int PROFICIENCY_TOP=3,EXPIRY_DAYS=NEGOTIATION_DAYS,HISTORY_LIMIT=1000;
    private CareerSquadPlanningPolicy(){}
    public record Decision(String id,int seasonYear,LocalDate date,String team,Position position,String squad,
            String action,String status,String playerId,String previousPlayerId,LocalDate effectiveDate,String referenceId,String reason){}
    public record State(String policyVersion,LocalDate lastReview,Map<String,LocalDate> cooldowns,List<Decision> decisions) {
        public State {if(!Set.of(VERSION,LEGACY).contains(policyVersion))throw new IllegalArgumentException("SQUAD_PLANNING_POLICY");cooldowns=Map.copyOf(cooldowns);decisions=List.copyOf(decisions);}
    }
    static int proficiency(CareerMarketEngine m,String id) {
        return (int)m.player(id).gameplay().proficiencies().stream().filter(p->p.position()==m.player(id).position())
                .sorted(Comparator.comparingInt(CompetitionRosterSnapshot.Proficiency::value).reversed()).limit(PROFICIENCY_TOP)
                .mapToInt(CompetitionRosterSnapshot.Proficiency::value).average().orElse(0);
    }
    static Comparator<String> order(CareerMarketEngine m) {
        return Comparator.comparingInt((String id)->strength(m.player(id))).reversed()
                .thenComparing(Comparator.comparingInt((String id)->proficiency(m,id)).reversed()).thenComparing(id->id);
    }
}
