package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.Position;
import java.util.ArrayList;
import java.util.List;

final class SideOrientationEventAggregator {
    private static final double TIE_EPSILON = 1e-9;

    Aggregation aggregate(String fixture, int seed, MatchSimulator.SimulationResult result) {
        SideOrientationExecutionStats stats = new SideOrientationExecutionStats();
        List<SideOrientationMatchRow.TieRow> ties = new ArrayList<>();
        List<SideOrientationMatchRow.ArbitrationRow> arbitrations = new ArrayList<>();
        for (MatchEvent event : result.timeline().getEvents()) {
            if (event.getLaneCombat() != null) {
                var data = event.getLaneCombat();
                actual(stats, SideOrientationResolver.LANE_COMBAT, data.initiatorSide(),
                        data.winningSide(), data.outcome() != LaneCombatOutcome.NO_KILL);
            } else if (event.getCounterGank() != null) {
                var data = event.getCounterGank();
                actual(stats, SideOrientationResolver.COUNTER_GANK, data.attackingSide(),
                        data.winningSide(), data.outcome() != CounterGankOutcome.NO_KILL);
                tie(ties, fixture, seed, event.getTimeSeconds(), SideOrientationResolver.COUNTER_GANK,
                        data.combatEdge(), data.winningSide());
            } else if (event.getJungleGank() != null) {
                var data = event.getJungleGank();
                actual(stats, SideOrientationResolver.JUNGLE_GANK, data.gankingSide(),
                        data.winningSide(), data.outcome() != JungleGankOutcome.NO_KILL);
                tie(ties, fixture, seed, event.getTimeSeconds(), SideOrientationResolver.JUNGLE_GANK,
                        data.combatEdge(), data.winningSide());
                arbitrations.add(new SideOrientationMatchRow.ArbitrationRow(
                        event.getTimeSeconds(), SideOrientationResolver.JUNGLE_GANK, fixture, seed,
                        true, data.blueTriggered() && data.redTriggered(),
                        data.blueTriggered() && data.redTriggered(), true, false,
                        data.gankingSide() == TeamSide.BLUE, data.gankingSide() == TeamSide.RED,
                        data.gankingSide(), data.blueTriggered() && data.redTriggered(),
                        data.blueTriggered() && data.redTriggered() ? "SHARED_WEIGHTED_SELECTION" : "NONE",
                        data.winningSide()));
            } else if (event.getRoam() != null) {
                var data = event.getRoam();
                actual(stats, SideOrientationResolver.ROAM, data.roamingSide(),
                        data.winningSide(), data.outcome() != RoamOutcome.NO_KILL);
                tie(ties, fixture, seed, event.getTimeSeconds(), SideOrientationResolver.ROAM,
                        data.combatEdge(), data.winningSide());
            }
            if (event.getStructureAttackingSide() != null) {
                SideOrientationResolver resolver = event.getStructureKind() == StructureKind.NEXUS
                        ? SideOrientationResolver.NEXUS_FINISH : SideOrientationResolver.STRUCTURE_PUSH;
                var counters = stats.counters(resolver, event.getStructureAttackingSide());
                counters.evaluation(true);
                counters.trigger();
                counters.attempt(false);
                counters.outcome(true);
                counters.structureMutation(event.getStructureKind() == StructureKind.NEXUS);
                counters.mutatedFirst();
            }
            if (event.getMidGameMacroAction() != null) {
                var data = event.getMidGameMacroAction();
                var counters = stats.counters(SideOrientationResolver.MIDGAME_MACRO, data.teamSide());
                counters.evaluation(data.result() != MacroActionResult.INELIGIBLE);
                if (data.result() != MacroActionResult.INELIGIBLE) counters.trigger();
                if (data.pushRollExecuted()) counters.attempt(false);
                if (data.pushRollExecuted()) counters.outcome(data.pushSucceeded());
            }
            if (event.getLateGameDecision() != null && event.getLateGameDecision().initiativeSide() != null) {
                var data = event.getLateGameDecision();
                SideOrientationResolver resolver = data.targetStructure() == LateGameStructureTarget.NEXUS
                        ? SideOrientationResolver.NEXUS_FINISH : SideOrientationResolver.LATE_GAME_SIEGE;
                var counters = stats.counters(resolver, data.initiativeSide());
                counters.evaluation(data.result() != LateGameActionResult.NO_INITIATIVE);
                if (data.result() != LateGameActionResult.NO_INITIATIVE) counters.trigger();
                if (data.majorCombatConsumed() || data.attackerStructureActionConsumed()) {
                    counters.attempt(data.majorCombatConsumed());
                }
                if (data.fightWinner() != null) counters.outcome(true);
                if (data.structureSucceeded()) {
                    counters.structureMutation(data.targetStructure() == LateGameStructureTarget.NEXUS);
                }
            }
        }
        addNonSummaryCombat(stats, result.combatOutcomeExecutionStats());
        addObjectiveCaptures(stats, result);
        addEvaluationCounters(stats, result);
        return new Aggregation(stats.snapshot(), List.copyOf(ties), List.copyOf(arbitrations));
    }

    private void actual(
            SideOrientationExecutionStats stats,
            SideOrientationResolver resolver,
            TeamSide initiator,
            TeamSide winner,
            boolean kill
    ) {
        var counters = stats.counters(resolver, initiator);
        counters.evaluation(true);
        counters.evaluatedFirst();
        counters.trigger();
        counters.attempt(true);
        counters.attemptedFirst();
        counters.outcome(kill);
        if (kill) stats.counters(resolver, winner).kill();
    }

    private void addNonSummaryCombat(
            SideOrientationExecutionStats stats,
            CombatOutcomeExecutionStatsSnapshot outcomes
    ) {
        addContext(stats, outcomes, ProgressionCombatContext.GENERIC_SKIRMISH,
                SideOrientationResolver.GENERIC_SKIRMISH);
        addContext(stats, outcomes, ProgressionCombatContext.TEAMFIGHT,
                SideOrientationResolver.TEAMFIGHT);
        addContext(stats, outcomes, ProgressionCombatContext.OBJECTIVE_FIGHT,
                SideOrientationResolver.OBJECTIVE_FIGHT);
        addContext(stats, outcomes, ProgressionCombatContext.LATE_GAME_SIEGE,
                SideOrientationResolver.LATE_GAME_SIEGE);
        addContext(stats, outcomes, ProgressionCombatContext.BASE_DEFENSE,
                SideOrientationResolver.BASE_DEFENSE);
    }

    private void addContext(
            SideOrientationExecutionStats stats,
            CombatOutcomeExecutionStatsSnapshot outcomes,
            ProgressionCombatContext context,
            SideOrientationResolver resolver
    ) {
        for (TeamSide side : TeamSide.values()) {
            int count = outcomes.wins(context, side);
            for (int i = 0; i < count; i++) {
                var counters = stats.counters(resolver, side);
                counters.evaluation(true);
                counters.trigger();
                counters.attempt(true);
                counters.outcome(true);
                counters.kill();
            }
        }
    }

    private void addObjectiveCaptures(
            SideOrientationExecutionStats stats,
            MatchSimulator.SimulationResult result
    ) {
        for (DragonCaptureRecord capture : result.dragonCaptures()) {
            var counters = stats.counters(SideOrientationResolver.OBJECTIVE_CAPTURE, capture.capturingSide());
            counters.evaluation(true);
            counters.trigger();
            counters.attempt(false);
            counters.outcome(true);
            counters.objectiveCapture();
        }
    }

    private void addEvaluationCounters(
            SideOrientationExecutionStats stats,
            MatchSimulator.SimulationResult result
    ) {
        var roam = result.roamExecutionStats();
        for (int i = 0; i < roam.midCandidateEvaluationsBlue() + roam.supportCandidateEvaluationsBlue(); i++) {
            stats.counters(SideOrientationResolver.ROAM, TeamSide.BLUE).evaluation(false);
        }
        for (int i = 0; i < roam.midCandidateEvaluationsRed() + roam.supportCandidateEvaluationsRed(); i++) {
            stats.counters(SideOrientationResolver.ROAM, TeamSide.RED).evaluation(false);
        }
        var macro = result.midGameMacroExecutionStats();
        for (int i = 0; i < macro.blueEvaluations(); i++) {
            stats.counters(SideOrientationResolver.MIDGAME_MACRO, TeamSide.BLUE).evaluation(false);
        }
        for (int i = 0; i < macro.redEvaluations(); i++) {
            stats.counters(SideOrientationResolver.MIDGAME_MACRO, TeamSide.RED).evaluation(false);
        }
    }

    private void tie(
            List<SideOrientationMatchRow.TieRow> rows,
            String fixture,
            int seed,
            int tick,
            SideOrientationResolver resolver,
            double edge,
            TeamSide winner
    ) {
        if (Math.abs(edge) > TIE_EPSILON) return;
        rows.add(new SideOrientationMatchRow.TieRow(
                resolver, fixture, seed, tick, edge, 0.0, edge,
                edge == 0.0 ? "EXACT" : "NEAR", "RANDOM", winner));
    }

    record Aggregation(
            java.util.Map<SideOrientationResolver,
                    java.util.Map<TeamSide, SideOrientationExecutionStats.Snapshot>> funnel,
            List<SideOrientationMatchRow.TieRow> ties,
            List<SideOrientationMatchRow.ArbitrationRow> arbitrations
    ) {
    }
}
