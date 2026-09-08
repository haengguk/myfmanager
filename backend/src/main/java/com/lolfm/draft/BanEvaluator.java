package com.lolfm.draft;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.composition.ChampionCompositionProfileCatalog;
import com.lolfm.composition.CompositionCapability;
import com.lolfm.domain.Position;
import com.lolfm.simulator.TeamSide;
import java.util.EnumMap;
import java.util.Set;

public final class BanEvaluator {
    private final ChampionCatalog champions;
    private final DraftMetaCatalog meta;
    private final ChampionCompositionProfileCatalog composition;
    private final RoleAssignmentSolver assignments;
    private final DraftAvailability availability;
    private final DraftCompositionEvaluator draftComposition;
    private final DraftMatchupEvaluator matchup;
    private final DraftScoringPolicy policy;
    private final DraftAbilityEvaluator ability;

    public BanEvaluator(ChampionCatalog champions, DraftMetaCatalog meta,
                        ChampionCompositionProfileCatalog composition,
                        RoleAssignmentSolver assignments, DraftAvailability availability,
                        DraftCompositionEvaluator draftComposition, DraftMatchupEvaluator matchup,
                        DraftScoringPolicy policy) {
        this(champions, meta, composition, assignments, availability, draftComposition, matchup, policy, null);
    }

    public BanEvaluator(ChampionCatalog champions, DraftMetaCatalog meta,
                        ChampionCompositionProfileCatalog composition,
                        RoleAssignmentSolver assignments, DraftAvailability availability,
                        DraftCompositionEvaluator draftComposition, DraftMatchupEvaluator matchup,
                        DraftScoringPolicy policy, DraftAbilityEvaluator ability) {
        this.ability=ability;
        this.champions = champions; this.meta = meta; this.composition = composition;
        this.assignments = assignments; this.availability = availability;
        this.draftComposition = draftComposition; this.matchup = matchup; this.policy = policy;
    }

    public BanEvaluation evaluate(DraftState state, TeamSide side, ChampionId candidate,
                                  DraftTeamContext own, DraftTeamContext enemy,
                                  DraftPlanPortfolio ownPortfolio, DraftPlanPortfolio enemyPortfolio) {
        return evaluate(state, side, candidate, own, enemy, ownPortfolio, enemyPortfolio,
                DraftComputationContext.uncached());
    }

    BanEvaluation evaluate(DraftState state, TeamSide side, ChampionId candidate,
                           DraftTeamContext own, DraftTeamContext enemy,
                           DraftPlanPortfolio ownPortfolio,
                           DraftPlanPortfolio enemyPortfolio,
                           DraftComputationContext context) {
        if(ability!=null)return abilityBan(state,side,candidate,own,enemy,ownPortfolio,enemyPortfolio,context);
        boolean opponentCanComplete = availability.canComplete(
                state, side.opposite(), candidate, context);
        Set<Position> enemyPositions = assignments.feasibleCandidatePositions(
                state.picks(side.opposite()), candidate, context);
        double enemyExpected = opponentCanComplete
                ? opponentExpectedValue(state, side, candidate, enemy, enemyPortfolio,
                        context) : 0.0;
        double threat = opponentCanComplete ? structuralThreat(candidate, ownPortfolio, enemyPositions) : 0.0;
        double metaValue = opponentCanComplete
                ? best(candidate, enemyPositions, key -> meta.priority(key)) : 0.0;
        double flex = opponentCanComplete
                ? assignments.practicalFlexValue(state.picks(side.opposite()), candidate,
                        enemy, context) : 0.0;
        if (!Double.isFinite(flex)) flex = 0.0;
        double compression = opponentCanComplete
                ? availability.rolePoolCompression(
                        state, side.opposite(), candidate, context) : 0.0;
        double protection = opponentCanComplete
                ? protectionValue(state, side, candidate, ownPortfolio, enemyPositions,
                        context) : 0.0;
        Set<Position> ownPositions = assignments.feasibleCandidatePositions(
                state.picks(side), candidate, context);
        double lost = availability.canComplete(state, side, candidate, context)
                ? best(candidate, ownPositions, key -> meta.priority(key) * 0.55 + own.proficiency(key) * 0.45)
                : 0.0;
        if (side == TeamSide.RED || state.nextTurnIndex() >= 6) lost *= 0.82;
        EnumMap<BanScoreComponent, Double> components = new EnumMap<>(BanScoreComponent.class);
        components.put(BanScoreComponent.OPPONENT_EXPECTED_PICK_VALUE, enemyExpected);
        components.put(BanScoreComponent.THREAT_TO_OUR_PLAN_PORTFOLIO, threat);
        components.put(BanScoreComponent.META_PRIORITY, metaValue);
        components.put(BanScoreComponent.OPPONENT_FLEX_VALUE, flex);
        components.put(BanScoreComponent.ROLE_POOL_COMPRESSION, compression);
        components.put(BanScoreComponent.PROTECTION_VALUE, protection);
        components.put(BanScoreComponent.OUR_LOST_PICK_OPPORTUNITY, lost);
        double total = components.entrySet().stream().mapToDouble(entry -> entry.getValue() * policy.banWeights().get(entry.getKey())).sum();
        return new BanEvaluation(candidate, total, components);
    }

    private BanEvaluation abilityBan(DraftState state,TeamSide side,ChampionId id,DraftTeamContext own,DraftTeamContext enemy,
            DraftPlanPortfolio ownPlan,DraftPlanPortfolio enemyPlan,DraftComputationContext context) {
        var roles=assignments.feasibleCandidatePositions(state.picks(side.opposite()),id,context);
        double value=0,replacement=0; com.lolfm.domain.Position bestRole=null;
        for(var p:roles) {
            double v=context.forecast(ability,enemy,new ChampionRoleKey(id,p),enemyPlan.preferred().archetype()).value();
            if(v>value){value=v;bestRole=p;}
        }
        if(bestRole!=null && availability.canComplete(state,side.opposite(),id,context)) {
            var role=bestRole;
            replacement=champions.all().stream().map(c->c.id()).filter(c->!c.equals(id)&&!state.unavailableChampions().contains(c))
                    .filter(c->assignments.feasibleCandidatePositions(state.picks(side.opposite()),c,context).contains(role))
                    .filter(c->availability.canComplete(state,side.opposite(),c,context))
                    .mapToDouble(c->context.forecast(ability,enemy,new ChampionRoleKey(c,role),enemyPlan.preferred().archetype()).value()).max().orElse(0);
        } else value=0;
        int ownDistance=nextPickDistance(state,side),enemyDistance=nextPickDistance(state,side.opposite());
        double opponentChance=value<=0?0:Math.clamp(0.5+(value-replacement)/10+(ownDistance-enemyDistance)*0.08,0.05,0.95);
        double ownValue=assignments.feasibleCandidatePositions(state.picks(side),id,context).stream()
                .map(p->new ChampionRoleKey(id,p)).mapToDouble(k->context.forecast(ability,own,k,ownPlan.preferred().archetype()).value()).max().orElse(0);
        double ownChance=ownDistance<enemyDistance?0.8:Math.pow(0.65,Math.max(1,ownDistance-enemyDistance));
        var c=new EnumMap<BanScoreComponent,Double>(BanScoreComponent.class);
        c.put(BanScoreComponent.OPPONENT_EXPECTED_PICK_VALUE,Math.max(0,value-replacement)*opponentChance);
        c.put(BanScoreComponent.THREAT_TO_OUR_PLAN_PORTFOLIO,value==0?0:Math.max(0,structuralThreat(id,ownPlan,roles)-10)*opponentChance);
        c.put(BanScoreComponent.META_PRIORITY,value==0?0:policy.metaScale()*best(id,roles,k->meta.priority(k))/20.0);
        c.put(BanScoreComponent.OPPONENT_FLEX_VALUE,Math.max(0,roles.size()-1)*opponentChance);
        c.put(BanScoreComponent.ROLE_POOL_COMPRESSION,value==0?0:Math.clamp(availability.rolePoolCompression(state,side.opposite(),id,context),0,5));
        c.put(BanScoreComponent.PROTECTION_VALUE,value==0?0:Math.clamp(protectionValue(state,side,id,ownPlan,roles,context),0,10)*opponentChance);
        c.put(BanScoreComponent.OUR_LOST_PICK_OPPORTUNITY,Math.max(0,ownValue-25)*ownChance);
        c.put(BanScoreComponent.OPPONENT_AVAILABLE_VALUE,value);
        c.put(BanScoreComponent.OPPONENT_REPLACEMENT_VALUE,replacement);
        c.put(BanScoreComponent.OPPONENT_PICK_PROBABILITY,opponentChance);
        c.put(BanScoreComponent.OUR_NEXT_PICK_DISTANCE,(double)ownDistance);
        c.put(BanScoreComponent.OPPONENT_NEXT_PICK_DISTANCE,(double)enemyDistance);
        double total=c.entrySet().stream().mapToDouble(e->e.getValue()*policy.banWeights().getOrDefault(e.getKey(),0.0)).sum();
        return new BanEvaluation(id,total,c);
    }
    static int nextPickDistance(DraftState state,TeamSide side) {
        for(int i=state.nextTurnIndex();i<state.ruleSet().turns().size();i++) {
            var t=state.ruleSet().turns().get(i);
            if(t.side()==side && t.actionType()==DraftActionType.PICK)return i-state.nextTurnIndex();
        }
        return state.ruleSet().turns().size();
    }

    private double opponentExpectedValue(DraftState state, TeamSide side, ChampionId candidate,
                                         DraftTeamContext enemy,
                                         DraftPlanPortfolio enemyPortfolio,
                                         DraftComputationContext context) {
        Set<Position> feasiblePositions = assignments.feasibleCandidatePositions(
                state.picks(side.opposite()), candidate, context);
        double base = best(candidate, feasiblePositions,
                key -> meta.priority(key) * 0.48 + enemy.proficiency(key) * 0.30);
        double plan = enemyPortfolio.plans().stream().filter(value -> value.coreCandidates().contains(candidate))
                .mapToDouble(DraftPlan::viability).max().orElse(0.0);
        double flex = assignments.practicalFlexValue(
                state.picks(side.opposite()), candidate, enemy, context);
        double fit = draftComposition.compositionFit(state.picks(side.opposite()), candidate,
                enemy, enemyPortfolio, context);
        return base + plan * 0.10 + Math.max(0.0, flex) * 0.10 + Math.max(0.0, fit) * 0.10;
    }

    private double protectionValue(DraftState state, TeamSide side, ChampionId threat,
                                   DraftPlanPortfolio ownPortfolio,
                                   Set<Position> threatPositions,
                                   DraftComputationContext context) {
        double pickedProtection = state.picks(side).stream().mapToDouble(core -> directCoreThreat(
                threat, core, threatPositions,
                assignments.feasiblePickedPositions(
                        state.picks(side), core, context))).max().orElse(0.0);
        double futureProtection = ownPortfolio.plans().stream()
                .flatMap(plan -> plan.coreCandidates().stream().limit(5))
                .filter(core -> !state.unavailableChampions().contains(core))
                .filter(core -> availability.canComplete(state, side, core, context))
                .mapToDouble(core -> directCoreThreat(threat, core, threatPositions,
                        assignments.feasibleCandidatePositions(
                                state.picks(side), core, context)))
                .max().orElse(0.0);
        return Math.max(pickedProtection, futureProtection);
    }

    private double directCoreThreat(ChampionId threat, ChampionId core, Set<Position> threatPositions,
                                    Set<Position> protectedPositions) {
        return threatPositions.stream()
                .filter(protectedPositions::contains)
                .mapToDouble(position -> DraftMatchupEvaluator.positiveThreatScore(matchup.roleEdge(
                        new ChampionRoleKey(threat, position), new ChampionRoleKey(core, position))))
                .max().orElse(0.0);
    }

    private double structuralThreat(ChampionId candidate, DraftPlanPortfolio portfolio,
                                    Set<Position> threatPositions) {
        return portfolio.plans().stream().mapToDouble(plan -> threatPositions.stream()
                .map(position -> composition.profiles().get(new ChampionRoleKey(candidate, position)))
                .mapToDouble(profile -> plan.structuralVulnerabilities().stream().mapToInt(profile::capability).average().orElse(0.0))
                .max().orElse(0.0) * viabilityWeight(plan, portfolio)).max().orElse(0.0);
    }
    private static double viabilityWeight(DraftPlan plan, DraftPlanPortfolio portfolio) {
        double preferred = Math.max(1.0, portfolio.preferred().viability());
        return Math.max(0.45, Math.min(1.2, plan.viability() / preferred));
    }
    private double best(ChampionId candidate, Set<Position> positions,
                        java.util.function.ToDoubleFunction<ChampionRoleKey> function) {
        return positions.stream().map(position -> new ChampionRoleKey(candidate, position))
                .mapToDouble(function).max().orElse(0.0);
    }
}
