package com.lolfm.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.domain.MatchSnapshot;
import java.util.List;

final class SideOrientationMatchExecutor {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ChampionCatalog CATALOG = new ChampionCatalog(MAPPER);
    private static final ChampionSelectionValidator SELECTOR = new ChampionSelectionValidator(CATALOG);
    private final ThreadLocal<MatchSimulator> offSimulator = ThreadLocal.withInitial(() -> simulator(false));
    private final ThreadLocal<MatchSimulator> onSimulator = ThreadLocal.withInitial(() -> simulator(true));
    private final SideOrientationEventAggregator aggregator = new SideOrientationEventAggregator();

    SideOrientationMatchRow run(
            SideOrientationFixture fixture,
            SideOrientationFixture.Orientation orientation,
            int seed,
            String auditGroup,
            String mode,
            String skillProfile,
            boolean captureTrace
    ) {
        var oriented = fixture.orient(orientation);
        var assignments = SELECTOR.resolve(oriented.champions());
        SideOrientationRandomTraceObserver random = new SideOrientationRandomTraceObserver(
                seed,
                orientation.toString(),
                oriented.blueLogicalTeam().toString(),
                oriented.redLogicalTeam().toString(),
                captureTrace
        );
        MatchSimulator.SimulationResult result = ("CHAMPION_ON".equals(mode) ? onSimulator : offSimulator)
                .get().simulateWithSideDiagnostics(
                        oriented.blue(), oriented.red(), assignments, random);
        MatchSnapshot end = result.timeline().getSnapshots().getLast();
        var aggregation = aggregator.aggregate(fixture.id(), seed, result);
        return new SideOrientationMatchRow(
                fixture.id(),
                auditGroup,
                mode,
                skillProfile,
                orientation,
                seed,
                oriented.blueLogicalTeam() == SideOrientationFixture.LogicalTeamId.TEAM_A,
                result.winnerSide(),
                oriented.logicalWinner(result.winnerSide()),
                result.timeline().getDurationSeconds(),
                end.getBlueKills(),
                end.getRedKills(),
                end.getBlueGold(),
                end.getRedGold(),
                end.getBlueDragons(),
                end.getRedDragons(),
                end.getBlueTowersDestroyed(),
                end.getRedTowersDestroyed(),
                combatOutcomeCount(result.combatOutcomeExecutionStats()),
                result.randomDrawCount(),
                championApplications(result, TeamSide.BLUE),
                championApplications(result, TeamSide.RED),
                0,
                integrityErrors(result),
                result.endReason(),
                aggregation.funnel(),
                aggregation.ties(),
                aggregation.arbitrations(),
                result.randomTrace()
        );
    }

    static byte[] timelineBytes(
            SideOrientationFixture fixture,
            SideOrientationFixture.Orientation orientation,
            int seed,
            boolean championPower,
            boolean observed
    ) throws Exception {
        var oriented = fixture.orient(orientation);
        var assignments = SELECTOR.resolve(oriented.champions());
        MatchSimulator simulator = simulator(championPower);
        var timeline = observed
                ? simulator.simulateWithSideDiagnostics(
                        oriented.blue(), oriented.red(), assignments,
                        new SideOrientationRandomTraceObserver(seed, orientation.toString(),
                                oriented.blueLogicalTeam().toString(),
                                oriented.redLogicalTeam().toString(), true)).timeline()
                : simulator.simulate(oriented.blue(), oriented.red(), seed, assignments);
        return MAPPER.writeValueAsBytes(timeline);
    }

    static List<SideOrientationRandomTraceObserver.Draw> trace(
            SideOrientationFixture fixture,
            SideOrientationFixture.Orientation orientation,
            int seed
    ) {
        var oriented = fixture.orient(orientation);
        var assignments = SELECTOR.resolve(oriented.champions());
        return simulator(false).simulateWithSideDiagnostics(
                oriented.blue(), oriented.red(), assignments,
                new SideOrientationRandomTraceObserver(seed, orientation.toString(),
                        oriented.blueLogicalTeam().toString(),
                        oriented.redLogicalTeam().toString(), true)).randomTrace();
    }

    private static int combatOutcomeCount(CombatOutcomeExecutionStatsSnapshot snapshot) {
        int count = 0;
        for (ProgressionCombatContext context : ProgressionCombatContext.values()) {
            for (TeamSide side : TeamSide.values()) count += snapshot.wins(context, side);
        }
        return count;
    }

    private static int championApplications(MatchSimulator.SimulationResult result, TeamSide side) {
        return (int) result.championPowerExecutionStats().samples().stream()
                .filter(sample -> sample.championPowerEnabled() && sample.ownSide() == side)
                .count();
    }

    private static int integrityErrors(MatchSimulator.SimulationResult result) {
        var outcomes = result.combatOutcomeExecutionStats();
        return outcomes.duplicateOutcomeRecordErrors() + outcomes.outcomeWithoutAttemptErrors()
                + outcomes.outcomeWithoutWinnerErrors() + outcomes.participantMismatchErrors()
                + result.structureActionExecutionStats().sameSideMultipleAttemptError()
                + result.structureActionExecutionStats().sameSideMultipleMutationError()
                + result.structureActionExecutionStats().postFightInternalBlockError();
    }

    private static MatchSimulator simulator(boolean championPower) {
        return new MatchSimulator(
                new TeamfightResolver(),
                new EndGameEvaluator(),
                new SnapshotFactory(CATALOG),
                new ObjectiveResolver(),
                new PostFightResolver(),
                new ObjectiveAttemptResolver(),
                new StructureResolver(),
                new PushResolver(),
                SimulationOptions.productionDefaults()
                        .withDiagnosticsEnabled(true)
                        .withChampionPowerEnabled(championPower)
        );
    }
}
