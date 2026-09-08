package com.lolfm.draft;

import com.lolfm.champion.*;
import com.lolfm.composition.*;
import com.lolfm.domain.*;
import com.lolfm.simulator.*;

/** Public-input forecast. Uses the same clamped champion evaluator as match combat; no GameState or Random. */
public final class DraftAbilityEvaluator {
    public static final String VERSION="DRAFT_ABILITY_SCORING_V1";
    private final ChampionResourceSet resources;
    private final ChampionPowerProfileEvaluator power;
    public DraftAbilityEvaluator(ChampionResourceSet resources) {
        this.resources=resources; power=new ChampionPowerProfileEvaluator(resources.power());
    }
    public Forecast evaluate(ChampionRoleKey role, DraftTeamContext team, DraftPlanArchetype plan) {
        ProgressionCombatContext midContext = switch(plan) {
            case POKE_SIEGE -> ProgressionCombatContext.LATE_GAME_SIEGE;
            case PICK_CONTROL -> ProgressionCombatContext.GENERIC_SKIRMISH;
            case DIVE -> ProgressionCombatContext.JUNGLE_GANK;
            case FRONT_TO_BACK -> ProgressionCombatContext.TEAMFIGHT;
        };
        // 6/component, 11/first core, 16/third core. Context modifiers are used once, here.
        double early=stage(role,6,ItemProgressStage.COMPONENT,ProgressionCombatContext.LANE_COMBAT);
        double mid=stage(role,11,ItemProgressStage.FIRST_CORE,midContext);
        double late=stage(role,16,ItemProgressStage.THIRD_CORE,ProgressionCombatContext.TEAMFIGHT);
        double ew=switch(plan){case DIVE->0.45;case PICK_CONTROL->0.35;case POKE_SIEGE->0.25;case FRONT_TO_BACK->0.20;};
        double lw=switch(plan){case DIVE->0.20;case PICK_CONTROL->0.25;case POKE_SIEGE->0.30;case FRONT_TO_BACK->0.45;};
        ChampionCompositionProfile profile=resources.composition().profiles().get(role);
        PlayerRatings ratings=team.ratings(role.position());
        double weighted=0,weights=0;
        for(PlayerSkill skill:PlayerSkill.orderedForPosition(role.position())) {
            double weight=1+profile.capability(axis(skill))/20.0;
            weighted+=ratings.get(skill)*weight; weights+=weight;
        }
        double fit=(weighted/weights)*0.65+team.proficiency(role)*0.35;
        double clear=0;
        if(role.position()==Position.JUNGLE) {
            var p=resources.jungleClear().get(role);
            if(p.gameplayEnabled()) clear=10*(new ChampionJungleClearEvaluator().evaluate(p,360)-1)
                    *PositionEconomyResolver.resourceManagementMultiplier(
                            ratings.get(PlayerSkill.JUNGLE_RESOURCE_MANAGEMENT),360,true);
        }
        // A scaling champion needs the current player's resource skills to bridge its weak opening.
        double resource=role.position()==Position.SUPPORT ? ratings.get(PlayerSkill.LANE_SUPPORT)
                :role.position()==Position.JUNGLE?ratings.get(PlayerSkill.JUNGLE_RESOURCE_MANAGEMENT):ratings.get(PlayerSkill.FARMING);
        double risk=Math.max(0,late-early)*(20-resource)/20.0;
        return new Forecast(early*ew,mid*(1-ew-lw),late*lw,fit,clear,risk);
    }
    private double stage(ChampionRoleKey role,int level,ItemProgressStage item,ProgressionCombatContext context) {
        return 10+power.evaluate(role.championId(),level,item,context).clampedPlayerChampionPower()*5;
    }
    private CompositionCapability axis(PlayerSkill s) {
        return switch(s) {
            case MECHANICS,COMBAT_EXECUTION,TRADING -> CompositionCapability.BURST_DAMAGE;
            case DECISION_MAKING,MAP_AWARENESS,CONSISTENCY,ENEMY_JUNGLE_TRACKING,VISION_CONTROL -> CompositionCapability.ZONE_CONTROL;
            case POSITIONING,FARMING,JUNGLE_RESOURCE_MANAGEMENT -> CompositionCapability.SUSTAINED_DAMAGE;
            case WAVE_MANAGEMENT,LANE_SUPPORT -> CompositionCapability.WAVE_CLEAR;
            case LANE_PRESSURE,SIDE_LANE -> CompositionCapability.SIDE_LANE_PRESSURE;
            case PRIORITY_CONVERSION,PATHING,LANE_INTERVENTION,ROTATION_PLANNING -> CompositionCapability.PICK;
            case OBJECTIVE_DECISION,OBJECTIVE_SECURE,AREA_SETUP -> CompositionCapability.OBJECTIVE_DAMAGE;
            case ENGAGE_EXECUTION -> CompositionCapability.ENGAGE;
            case ALLY_PROTECTION -> CompositionCapability.PEEL;
        };
    }
    public record Forecast(double early, double mid, double late, double playerFit, double jungleClear,double resourceRisk) {
        public double value(){return early+mid+late+0.8*playerFit+0.5*jungleClear-0.5*resourceRisk;}
    }
}
