package com.lolfm.simulator;

import com.lolfm.domain.CombatSource;
import com.lolfm.domain.ObjectivePrioritySnapshot;

/** Stateless strategic-priority service. All mutable state is owned by GameState. */
public final class ObjectivePriorityResolver {
    public void decayRecentControl(GameState state, int currentTimeSeconds) {
        state.getObjectivePriorityState().advanceTo(currentTimeSeconds);
    }

    public double dragonLanePressureScore(GameState state) {
        if (!state.getObjectivePriorityState().isEnabled()) return 0;
        return state.laneState(Lane.MID).getPressure() * ObjectivePriorityRuleConfig.DRAGON_MID_PRESSURE_WEIGHT
                + state.laneState(Lane.BOT).getPressure() * ObjectivePriorityRuleConfig.DRAGON_BOT_PRESSURE_WEIGHT;
    }

    public double baronLanePressureScore(GameState state) {
        if (!state.getObjectivePriorityState().isEnabled()) return 0;
        return state.laneState(Lane.TOP).getPressure() * ObjectivePriorityRuleConfig.BARON_TOP_PRESSURE_WEIGHT
                + state.laneState(Lane.MID).getPressure() * ObjectivePriorityRuleConfig.BARON_MID_PRESSURE_WEIGHT;
    }

    public double dragonSignedPriority(GameState state) {
        if (!state.getObjectivePriorityState().isEnabled()) return 0;
        return clampPriority(dragonLanePressureScore(state) + state.getObjectivePriorityState().getDragonRecentControl());
    }

    public double baronSignedPriority(GameState state) {
        if (!state.getObjectivePriorityState().isEnabled()) return 0;
        return clampPriority(baronLanePressureScore(state) + state.getObjectivePriorityState().getBaronRecentControl());
    }

    public double blueDisplayPriority(double signedPriority) {
        return clamp(50 + signedPriority / 2.0, 0, 100);
    }

    public double redDisplayPriority(double signedPriority) { return 100 - blueDisplayPriority(signedPriority); }

    public ObjectivePrioritySnapshot snapshot(GameState state) {
        if (!state.getObjectivePriorityState().isEnabled()) {
            return new ObjectivePrioritySnapshot(false, 0, 0, 0, 50, 50, 0, 0, 0, 50, 50);
        }
        double dragonLane = dragonLanePressureScore(state);
        double dragonRecent = state.getObjectivePriorityState().getDragonRecentControl();
        double dragonSigned = clampPriority(dragonLane + dragonRecent);
        double baronLane = baronLanePressureScore(state);
        double baronRecent = state.getObjectivePriorityState().getBaronRecentControl();
        double baronSigned = clampPriority(baronLane + baronRecent);
        return new ObjectivePrioritySnapshot(true, dragonLane, dragonRecent, dragonSigned,
                blueDisplayPriority(dragonSigned), redDisplayPriority(dragonSigned),
                baronLane, baronRecent, baronSigned,
                blueDisplayPriority(baronSigned), redDisplayPriority(baronSigned));
    }

    public boolean applyLaneCombatKill(GameState state, int timeSeconds, Lane lane, TeamSide winningSide) {
        return applyLaneKill(state, timeSeconds, CombatSource.LANE_COMBAT, lane, winningSide,
                ObjectivePriorityRuleConfig.LANE_COMBAT_KILL_BASE_IMPACT);
    }

    public boolean applyLaneCombatOutcome(GameState state, int timeSeconds, Lane lane,
                                           TeamSide initiatingSide, LaneCombatOutcome outcome) {
        return switch (outcome) {
            case NO_KILL -> rejectNoKill(state);
            case ATTACKER_KILL -> applyLaneCombatKill(state, timeSeconds, lane, initiatingSide);
            case DEFENDER_REVERSE_KILL -> applyLaneCombatKill(state, timeSeconds, lane, initiatingSide.opposite());
        };
    }

    public boolean applyJungleGankKill(GameState state, int timeSeconds, Lane targetLane, TeamSide winningSide) {
        return applyLaneKill(state, timeSeconds, CombatSource.JUNGLE_GANK, targetLane, winningSide,
                ObjectivePriorityRuleConfig.JUNGLE_GANK_KILL_BASE_IMPACT);
    }

    public boolean applyJungleGankOutcome(GameState state, int timeSeconds, Lane targetLane,
                                           TeamSide gankingSide, JungleGankOutcome outcome) {
        return switch (outcome) {
            case NO_KILL -> rejectNoKill(state);
            case GANK_SUCCESS -> applyJungleGankKill(state, timeSeconds, targetLane, gankingSide);
            case DEFENDER_REVERSE_KILL -> applyJungleGankKill(state, timeSeconds, targetLane, gankingSide.opposite());
        };
    }

    public boolean applyCounterGankKill(GameState state, int timeSeconds, Lane targetLane, TeamSide winningSide) {
        return applyLaneKill(state, timeSeconds, CombatSource.COUNTER_GANK, targetLane, winningSide,
                ObjectivePriorityRuleConfig.COUNTER_GANK_KILL_BASE_IMPACT);
    }

    public boolean applyCounterGankOutcome(GameState state, int timeSeconds, Lane targetLane,
                                            TeamSide attackingSide, CounterGankOutcome outcome) {
        return switch (outcome) {
            case NO_KILL -> rejectNoKill(state);
            case ATTACKING_SIDE_KILL -> applyCounterGankKill(state, timeSeconds, targetLane, attackingSide);
            case DEFENDING_SIDE_KILL -> applyCounterGankKill(state, timeSeconds, targetLane, attackingSide.opposite());
        };
    }

    public boolean applyRoamKill(GameState state, int timeSeconds, Lane targetLane, TeamSide winningSide) {
        return applyLaneKill(state, timeSeconds, CombatSource.ROAM, targetLane, winningSide,
                ObjectivePriorityRuleConfig.ROAM_KILL_BASE_IMPACT);
    }

    public boolean applyRoamOutcome(GameState state, int timeSeconds, Lane targetLane,
                                    TeamSide roamingSide, RoamOutcome outcome) {
        return switch (outcome) {
            case NO_KILL -> rejectNoKill(state);
            case ROAMING_SIDE_KILL -> applyRoamKill(state, timeSeconds, targetLane, roamingSide);
            case DEFENDING_SIDE_KILL -> applyRoamKill(state, timeSeconds, targetLane, roamingSide.opposite());
        };
    }

    public boolean applyTeamfightWin(GameState state, int timeSeconds, TeamfightOutcome outcome) {
        double impact = switch (outcome.grade()) {
            case SMALL_WIN -> ObjectivePriorityRuleConfig.TEAMFIGHT_SMALL_WIN_IMPACT;
            case NORMAL_WIN -> ObjectivePriorityRuleConfig.TEAMFIGHT_NORMAL_WIN_IMPACT;
            case BIG_WIN -> ObjectivePriorityRuleConfig.TEAMFIGHT_BIG_WIN_IMPACT;
            case ACE -> ObjectivePriorityRuleConfig.TEAMFIGHT_ACE_IMPACT;
        };
        return apply(state, new ObjectivePriorityState.ImpactKey(timeSeconds, CombatSource.TEAMFIGHT,
                        null, outcome.winningSide(), outcome.grade()),
                signed(outcome.winningSide(), impact), signed(outcome.winningSide(), impact));
    }

    private boolean rejectNoKill(GameState state) {
        state.getObjectivePriorityState().recordNoKillImpact();
        return false;
    }

    private boolean applyLaneKill(GameState state, int timeSeconds, CombatSource source, Lane lane,
                                  TeamSide winningSide, double baseImpact) {
        double signedImpact = signed(winningSide, baseImpact);
        return apply(state, new ObjectivePriorityState.ImpactKey(timeSeconds, source, lane, winningSide, null),
                signedImpact * dragonLaneMultiplier(lane), signedImpact * baronLaneMultiplier(lane));
    }

    private boolean apply(GameState state, ObjectivePriorityState.ImpactKey key, double dragon, double baron) {
        return state.getObjectivePriorityState().applyImpactOnce(key, dragon, baron);
    }

    private double dragonLaneMultiplier(Lane lane) {
        return switch (lane) {
            case TOP -> ObjectivePriorityRuleConfig.DRAGON_TOP_LANE_IMPACT_MULTIPLIER;
            case MID -> ObjectivePriorityRuleConfig.DRAGON_MID_LANE_IMPACT_MULTIPLIER;
            case BOT -> ObjectivePriorityRuleConfig.DRAGON_BOT_LANE_IMPACT_MULTIPLIER;
        };
    }

    private double baronLaneMultiplier(Lane lane) {
        return switch (lane) {
            case TOP -> ObjectivePriorityRuleConfig.BARON_TOP_LANE_IMPACT_MULTIPLIER;
            case MID -> ObjectivePriorityRuleConfig.BARON_MID_LANE_IMPACT_MULTIPLIER;
            case BOT -> ObjectivePriorityRuleConfig.BARON_BOT_LANE_IMPACT_MULTIPLIER;
        };
    }

    private double signed(TeamSide side, double amount) { return side == TeamSide.BLUE ? amount : -amount; }
    private double clampPriority(double value) { return clamp(value, ObjectivePriorityRuleConfig.PRIORITY_SCORE_MIN, ObjectivePriorityRuleConfig.PRIORITY_SCORE_MAX); }
    private double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
