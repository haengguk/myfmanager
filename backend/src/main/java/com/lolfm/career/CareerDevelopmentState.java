package com.lolfm.career;

import com.lolfm.domain.PlayerSkill;
import java.time.LocalDate;
import java.util.*;

/** Career-owned fixed-point state. All mutable copies belong to one locked operation. */
public record CareerDevelopmentState(String policyVersion,String initializationVersion,LocalDate initializedOn,
        LocalDate nextSettlement,Map<String,Player> players,Map<String,Schedule> teamPlans,List<Gain> gains,Map<String,Gain> monthly) {
    public CareerDevelopmentState { players=Map.copyOf(players);teamPlans=Map.copyOf(teamPlans);gains=List.copyOf(gains);monthly=Map.copyOf(monthly); }
    public enum Intensity { RECOVERY,LIGHT,NORMAL,INTENSIVE }
    public enum Focus { BALANCED,COMMON_SKILLS,ROLE_SKILLS,SPECIFIC_SKILL,CHAMPION_FOCUS }
    public record Plan(Intensity intensity,Focus focus,PlayerSkill skill,List<String> champions) {
        public Plan { Objects.requireNonNull(intensity);Objects.requireNonNull(focus);champions=List.copyOf(champions); }
    }
    public record Schedule(String team,Plan current,LocalDate effectiveOn,Plan pending,LocalDate pendingOn,boolean pendingClear) {}
    public record Cursor(int index,int filled) {}
    public record Player(Map<PlayerSkill,Integer> internalRatings,Map<String,Integer> internalProficiencies,
            long remainder,Map<String,Long> proficiencyRemainders,Map<String,Cursor> cursors,int fatigue,
            LocalDate playedOn,Schedule override,int growthSubRemainder) {
        /** Legacy remainder keeps its 10^12 denominator; an absent sub-unit starts at zero. */
        public Player(Map<PlayerSkill,Integer> ratings,Map<String,Integer> proficiencies,long remainder,Map<String,Long> profRemainders,Map<String,Cursor> cursors,int fatigue,LocalDate playedOn,Schedule override) {
            this(ratings,proficiencies,remainder,profRemainders,cursors,fatigue,playedOn,override,0);
        }
        public Player { internalRatings=Map.copyOf(internalRatings);internalProficiencies=Map.copyOf(internalProficiencies);
            proficiencyRemainders=Map.copyOf(proficiencyRemainders);cursors=Map.copyOf(cursors); }
    }
    /** Daily deltas only for changed players, compact across all skills; no complete daily snapshots. */
    public record Gain(LocalDate date,String playerId,int internalGain,int proficiencyGain,
            Map<String,Integer> integerRises,int seasonYear) { public Gain { integerRises=Map.copyOf(integerRises); } }
}
