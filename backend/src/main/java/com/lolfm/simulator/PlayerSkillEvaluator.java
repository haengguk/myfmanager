package com.lolfm.simulator;

import com.lolfm.domain.PlayerSkill;

/**
 * Responsibility matrix in executable form. Each score has one primary rating and at most two
 * supporting ratings; proficiency is read only through execution(...) at champion-tool checks.
 */
public final class PlayerSkillEvaluator {
    public double farming(PlayerState p) {
        return p.getPosition() == com.lolfm.domain.Position.JUNGLE
                ? jungleResourceManagement(p)
                : p.rating(PlayerSkill.FARMING);
    }

    public double jungleResourceManagement(PlayerState p) {
        return p.rating(PlayerSkill.JUNGLE_RESOURCE_MANAGEMENT);
    }

    public double laneTrade(PlayerState p) {
        return p.execution(PlayerSkill.TRADING) * .75
                + p.execution(PlayerSkill.MECHANICS) * .25;
    }

    public double lanePressure(PlayerState p) {
        return weighted(p.rating(PlayerSkill.LANE_PRESSURE), .60,
                p.execution(PlayerSkill.TRADING), .25,
                p.rating(PlayerSkill.WAVE_MANAGEMENT), .15);
    }

    public double waveManagement(PlayerState p) { return p.rating(PlayerSkill.WAVE_MANAGEMENT); }

    public double priorityConversion(PlayerState p) {
        return weighted(p.rating(PlayerSkill.PRIORITY_CONVERSION), .60,
                p.rating(PlayerSkill.DECISION_MAKING), .25,
                p.rating(PlayerSkill.MAP_AWARENESS), .15);
    }

    public double sideLane(PlayerState p) {
        return weighted(p.rating(PlayerSkill.SIDE_LANE), .60,
                p.rating(PlayerSkill.DECISION_MAKING), .25,
                p.rating(PlayerSkill.MAP_AWARENESS), .15);
    }

    public double pathing(PlayerState p) {
        return p.rating(PlayerSkill.PATHING) * .75 + p.rating(PlayerSkill.DECISION_MAKING) * .25;
    }

    public double jungleResources(PlayerState p) {
        return p.rating(PlayerSkill.JUNGLE_RESOURCE_MANAGEMENT) * .80
                + p.rating(PlayerSkill.PATHING) * .20;
    }

    public double jungleTracking(PlayerState p) {
        return p.rating(PlayerSkill.ENEMY_JUNGLE_TRACKING) * .70
                + p.rating(PlayerSkill.MAP_AWARENESS) * .30;
    }

    public double laneIntervention(PlayerState p) {
        return weighted(p.execution(PlayerSkill.LANE_INTERVENTION), .60,
                p.execution(PlayerSkill.MECHANICS), .25,
                p.rating(PlayerSkill.MAP_AWARENESS), .15);
    }

    public double objectiveDecision(PlayerState p) {
        return p.rating(PlayerSkill.OBJECTIVE_DECISION) * .70
                + p.rating(PlayerSkill.DECISION_MAKING) * .30;
    }

    public double objectiveSecure(PlayerState p) {
        return p.execution(PlayerSkill.OBJECTIVE_SECURE) * .70
                + p.execution(PlayerSkill.MECHANICS) * .30;
    }

    public double visionControl(PlayerState p) {
        return p.rating(PlayerSkill.VISION_CONTROL) * .75
                + p.rating(PlayerSkill.MAP_AWARENESS) * .25;
    }

    public double laneSupport(PlayerState p) {
        return p.rating(PlayerSkill.LANE_SUPPORT) * .70
                + p.rating(PlayerSkill.MAP_AWARENESS) * .30;
    }

    public double rotationPlanning(PlayerState p) {
        return weighted(p.rating(PlayerSkill.ROTATION_PLANNING), .65,
                p.rating(PlayerSkill.MAP_AWARENESS), .20,
                p.rating(PlayerSkill.DECISION_MAKING), .15);
    }

    public double engageExecution(PlayerState p) {
        return p.execution(PlayerSkill.ENGAGE_EXECUTION) * .70
                + p.execution(PlayerSkill.MECHANICS) * .30;
    }

    public double allyProtection(PlayerState p) {
        return p.execution(PlayerSkill.ALLY_PROTECTION) * .70
                + p.rating(PlayerSkill.POSITIONING) * .30;
    }

    public double areaSetup(PlayerState p) {
        return weighted(p.rating(PlayerSkill.AREA_SETUP), .65,
                p.rating(PlayerSkill.VISION_CONTROL), .20,
                p.rating(PlayerSkill.MAP_AWARENESS), .15);
    }

    public double combatExecution(PlayerState p) {
        return p.execution(PlayerSkill.COMBAT_EXECUTION) * .75
                + p.execution(PlayerSkill.MECHANICS) * .25;
    }

    /** Small-fight initiative while preserving the legacy aggression/mechanics/teamfight meaning. */
    public double combatInitiative(PlayerState p) {
        double aggressionWeight = PlayerImpactRuleConfig.SKIRMISH_INITIATIVE_AGGRESSION_WEIGHT;
        double mechanicsWeight = PlayerImpactRuleConfig.SKIRMISH_INITIATIVE_MECHANICS_WEIGHT;
        double teamfightingWeight = PlayerImpactRuleConfig.SKIRMISH_INITIATIVE_TEAMFIGHTING_WEIGHT;
        return (decisionQuality(p) * aggressionWeight
                + p.execution(PlayerSkill.MECHANICS) * mechanicsWeight
                + p.execution(PlayerSkill.COMBAT_EXECUTION) * teamfightingWeight)
                / (aggressionWeight + mechanicsWeight + teamfightingWeight);
    }

    /** Converts a won combat into individual kill credit without collapsing all skills into one. */
    public double killConversion(PlayerState p) {
        double mechanicsWeight = PlayerImpactRuleConfig.KILLER_MECHANICS_WEIGHT;
        double decisionWeight = PlayerImpactRuleConfig.KILLER_AGGRESSION_WEIGHT;
        double combatWeight = PlayerImpactRuleConfig.KILLER_TEAMFIGHTING_WEIGHT;
        return (p.execution(PlayerSkill.MECHANICS) * mechanicsWeight
                + decisionQuality(p) * decisionWeight
                + p.execution(PlayerSkill.COMBAT_EXECUTION) * combatWeight)
                / (mechanicsWeight + decisionWeight + combatWeight);
    }

    public double exposureSafety(PlayerState p) {
        return p.execution(PlayerSkill.POSITIONING)
                * CombatParticipantRuleConfig.EXPOSURE_POSITIONING_WEIGHT
                + p.rating(PlayerSkill.MAP_AWARENESS)
                * CombatParticipantRuleConfig.EXPOSURE_MAP_AWARENESS_WEIGHT;
    }

    /** Awareness and role-specific joining skill determine who is credited with an assist. */
    public double assistParticipation(PlayerState p) {
        double roleJoin = switch (p.getPosition()) {
            case TOP, MID, ADC -> p.rating(PlayerSkill.PRIORITY_CONVERSION);
            case JUNGLE -> p.execution(PlayerSkill.LANE_INTERVENTION);
            case SUPPORT -> p.rating(PlayerSkill.ROTATION_PLANNING);
        };
        return p.rating(PlayerSkill.MAP_AWARENESS)
                * CombatParticipantRuleConfig.ASSIST_MAP_AWARENESS_WEIGHT
                + p.rating(PlayerSkill.DECISION_MAKING)
                * CombatParticipantRuleConfig.ASSIST_DECISION_MAKING_WEIGHT
                + roleJoin * CombatParticipantRuleConfig.ASSIST_ROLE_JOIN_WEIGHT;
    }

    public double decisionQuality(PlayerState p) { return p.rating(PlayerSkill.DECISION_MAKING); }
    public double mapAwareness(PlayerState p) { return p.rating(PlayerSkill.MAP_AWARENESS); }

    private double weighted(double primary, double primaryWeight,
                            double supportA, double supportAWeight,
                            double supportB, double supportBWeight) {
        return primary * primaryWeight + supportA * supportAWeight + supportB * supportBWeight;
    }
}
