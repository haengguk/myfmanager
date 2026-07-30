package com.lolfm.champion;

import com.lolfm.simulator.ProgressionCombatContext;
import java.util.*;

/** Diagnostics-only generic formula shapes over the frozen centered vector. */
public final class InteractionShapeFormula {
    public enum Type { PRODUCT_CENTERED_V1, EXPOSURE_GATED_PRODUCT_V2, EXPOSURE_GATED_GEOMETRIC_V2 }
    private final ChampionMatchupRuleCatalog rules = new ChampionMatchupRuleCatalog();

    public Result evaluate(Type type, ChampionRoleMatchupProfile source,
                           ChampionRoleMatchupProfile opponent,
                           ProgressionCombatContext context) {
        Objects.requireNonNull(type); Objects.requireNonNull(source); Objects.requireNonNull(opponent); Objects.requireNonNull(context);
        if (source.roleKey().position() != opponent.roleKey().position()) throw new IllegalArgumentException("Cross-position interaction");
        if (type == Type.EXPOSURE_GATED_GEOMETRIC_V2) {
            var production = new ChampionMatchupEvaluator(ThirtyChampionRoleProfiles.catalog())
                    .evaluate(source.roleKey(), opponent.roleKey(), context, ChampionMatchupMode.GEOMETRIC_V2);
            var values = production.contributions().stream().map(c -> new Contribution(
                    c.ruleType(), c.forwardDirectional(), c.reverseDirectional(),
                    c.antisymmetricRuleEdge(), c.contextWeight(), c.weightedContribution())).toList();
            return new Result(type, source.roleKey(), opponent.roleKey(), context, values,
                    production.weightedRawEdge(), production.contextIntensity(),
                    production.unclampedEdge(), production.finalEdge(), production.clamped());
        }
        var a=ChampionMatchupInteractionVector.from(source); var b=ChampionMatchupInteractionVector.from(opponent);
        List<Contribution> cs=new ArrayList<>(); double raw=0;
        for(var rule:ChampionMatchupRuleType.values()){
            double forward=directional(type,a,b,rule), reverse=directional(type,b,a,rule);
            double antisymmetric=zero(forward-reverse), weighted=zero(antisymmetric*rules.weight(context,rule)); raw+=weighted;
            cs.add(new Contribution(rule,forward,reverse,antisymmetric,rules.weight(context,rule),weighted));
        }
        raw=zero(raw); double unclamped=zero(raw*rules.intensity(context)*.30); double edge=clamp(unclamped);
        return new Result(type,source.roleKey(),opponent.roleKey(),context,List.copyOf(cs),raw,rules.intensity(context),unclamped,edge,edge!=unclamped);
    }

    private double directional(Type type,ChampionMatchupInteractionVector s,ChampionMatchupInteractionVector o,ChampionMatchupRuleType rule){
        double capability, exposure;
        if(rule==ChampionMatchupRuleType.PEEL_ANTI_DIVE_RESPONSE){
            capability=s.meanStrength(ChampionMatchupTrait.DISENGAGE,ChampionMatchupTrait.ANTI_DIVE,ChampionMatchupTrait.CROWD_CONTROL);
            double dependency=o.meanStrength(ChampionMatchupTrait.ENGAGE,ChampionMatchupTrait.GAP_CLOSE,ChampionMatchupTrait.BURST);
            if(type==Type.PRODUCT_CENTERED_V1)return zero(capability*dependency);
            exposure=o.meanVulnerability(ChampionMatchupTrait.DURABILITY,ChampionMatchupTrait.DISENGAGE,ChampionMatchupTrait.MOBILITY);
            return zero(capability*dependency*exposureGate(exposure));
        }
        switch(rule){
            case RANGE_POKE_PRESSURE->{capability=s.meanStrength(ChampionMatchupTrait.RANGE_CONTROL,ChampionMatchupTrait.POKE);exposure=o.meanVulnerability(ChampionMatchupTrait.SUSTAIN,ChampionMatchupTrait.MOBILITY,ChampionMatchupTrait.WAVE_CONTROL);}
            case ACCESS_ENGAGE_THREAT->{capability=s.meanStrength(ChampionMatchupTrait.GAP_CLOSE,ChampionMatchupTrait.ENGAGE,ChampionMatchupTrait.CROWD_CONTROL);exposure=o.meanVulnerability(ChampionMatchupTrait.DISENGAGE,ChampionMatchupTrait.MOBILITY,ChampionMatchupTrait.ANTI_DIVE);}
            case BURST_PICK_WINDOW->{capability=s.meanStrength(ChampionMatchupTrait.BURST,ChampionMatchupTrait.PICK,ChampionMatchupTrait.CROWD_CONTROL);exposure=o.meanVulnerability(ChampionMatchupTrait.DURABILITY,ChampionMatchupTrait.MOBILITY,ChampionMatchupTrait.ANTI_DIVE);}
            case EXTENDED_FIGHT_PRESSURE->{capability=s.meanStrength(ChampionMatchupTrait.SUSTAINED_DAMAGE,ChampionMatchupTrait.SUSTAIN,ChampionMatchupTrait.ANTI_TANK);exposure=o.meanVulnerability(ChampionMatchupTrait.DURABILITY,ChampionMatchupTrait.DISENGAGE,ChampionMatchupTrait.RANGE_CONTROL);}
            case WAVE_TEMPO_CONTROL->{capability=s.meanStrength(ChampionMatchupTrait.WAVE_CONTROL,ChampionMatchupTrait.RANGE_CONTROL,ChampionMatchupTrait.POKE);exposure=o.meanVulnerability(ChampionMatchupTrait.WAVE_CONTROL,ChampionMatchupTrait.SUSTAIN,ChampionMatchupTrait.RANGE_CONTROL);}
            case MOBILITY_PICK_ACCESS->{capability=s.meanStrength(ChampionMatchupTrait.MOBILITY,ChampionMatchupTrait.PICK,ChampionMatchupTrait.GAP_CLOSE);exposure=o.meanVulnerability(ChampionMatchupTrait.MOBILITY,ChampionMatchupTrait.DISENGAGE,ChampionMatchupTrait.DURABILITY);}
            default->throw new IllegalStateException();
        }
        return zero(capability*exposure);
    }
    public static double exposureGate(double exposure){return zero(.25+.75*Math.max(0,Math.min(1,exposure)));}
    private static double clamp(double v){double x=Math.max(-.30,Math.min(.30,v));return zero(x);} private static double zero(double v){return Math.abs(v)<1e-12?0.0:v;}
    public record Contribution(ChampionMatchupRuleType ruleType,double forwardDirectional,double reverseDirectional,double antisymmetricRuleEdge,double contextWeight,double weightedContribution){}
    public record Result(Type formulaType,ChampionRoleKey source,ChampionRoleKey opponent,ProgressionCombatContext context,List<Contribution> contributions,double weightedRawEdge,double contextIntensity,double unclampedEdge,double finalEdge,boolean clamped){public Result{contributions=List.copyOf(contributions);} public double peelContribution(){return contributions.stream().filter(c->c.ruleType()==ChampionMatchupRuleType.PEEL_ANTI_DIVE_RESPONSE).mapToDouble(Contribution::weightedContribution).sum()*contextIntensity*.30;} public double nonPeelContribution(){return finalEdge()-peelContribution();}}
}
