package com.lolfm.champion;

import com.lolfm.domain.Position;
import com.lolfm.simulator.ProgressionCombatContext;
import java.util.*;

public final class InteractionShapeGeneratedCatalog {
    private InteractionShapeGeneratedCatalog() { }
    public static BuildResult build(ChampionCatalog champions, InteractionShapeFormula.Type type) { return build(champions,type,1.0); }
    public static BuildResult build(ChampionCatalog champions, InteractionShapeFormula.Type type, double gain) {
        if (!Double.isFinite(gain) || gain <= 0) throw new IllegalArgumentException("Finite positive gain required");
        var profiles=ThirtyChampionRoleProfiles.catalog(); var formula=new InteractionShapeFormula();
        List<ChampionMatchupProfile> catalogProfiles=new ArrayList<>(); List<Row> rows=new ArrayList<>(675);
        for(Position position:Position.values()){
            List<ChampionDefinition> pool=champions.forPosition(position);
            for(int i=0;i<pool.size();i++)for(int j=i+1;j<pool.size();j++){
                var pair=ChampionMatchupPair.of(pool.get(i),pool.get(j));
                var first=profiles.find(new ChampionRoleKey(pair.first(),position)).orElseThrow();
                var second=profiles.find(new ChampionRoleKey(pair.second(),position)).orElseThrow();
                LinkedHashMap<ProgressionCombatContext,Double> edges=new LinkedHashMap<>();
                for(var context:ProgressionCombatContext.values()){
                    var f=formula.evaluate(type,first,second,context);var r=formula.evaluate(type,second,first,context);
                    var dominant=f.contributions().stream().max(Comparator.comparingDouble(c->Math.abs(c.weightedContribution()))).orElseThrow();
                    double sum=f.contributions().stream().mapToDouble(c->Math.abs(c.weightedContribution())).sum();
                    double forward=clamp(f.finalEdge()*gain),reverse=clamp(r.finalEdge()*gain);edges.put(context,forward);rows.add(new Row(type,position,pair.first().value()+"/"+pair.second().value(),context,forward,reverse,Math.abs(forward+reverse)<1e-12,Math.abs(forward),forward==0?0:forward>0?1:-1,dominant.ruleType(),sum==0?0:Math.abs(dominant.weightedContribution())/sum,f.peelContribution()*gain,f.nonPeelContribution()*gain,Math.abs(f.finalEdge()*gain)>.30));
                }
                catalogProfiles.add(new ChampionMatchupProfile(pair,edges));
            }
        }
        return new BuildResult(ChampionMatchupCatalog.generatedDiagnosticsCatalog("diagnostics-interaction-shape-"+type.name().toLowerCase()+(gain==1.0?"":"-gain-"+gain),champions,catalogProfiles),List.copyOf(rows));
    }
    private static double clamp(double value){double result=Math.max(-.30,Math.min(.30,value));return result==0?0:result;}
    public record Row(InteractionShapeFormula.Type formulaType,Position position,String pair,ProgressionCombatContext context,double forwardEdge,double reverseEdge,boolean directionalityValid,double absoluteEdge,int sign,ChampionMatchupRuleType dominantRule,double dominantRuleShare,double peelContribution,double nonPeelContribution,boolean clamped){}
    public record BuildResult(ChampionMatchupCatalog catalog,List<Row> rows){public BuildResult{rows=List.copyOf(rows);}}
}
