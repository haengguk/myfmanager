package com.lolfm.simulator;

import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.PlayerSnapshot;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import java.util.EnumMap;
import java.util.List;

/** Manual, reproducible distribution check; it deliberately does not affect API responses. */
public final class PositionEconomyDiagnostics {
    private static final int RUNS = 1_000;
    private static final int[] CHECKPOINTS = {600, 840};

    public static void main(String[] args) {
        for (Scenario scenario : Scenario.values()) run(scenario);
    }

    private static void run(Scenario scenario) {
        EnumMap<Position, Totals>[] checkpoints = new EnumMap[CHECKPOINTS.length];
        for (int i = 0; i < CHECKPOINTS.length; i++) checkpoints[i] = newTotals();
        EnumMap<Position, Totals> ending = newTotals();
        int timeouts = 0, deadFarmCs = 0, duplicateFarmSuspicions = 0, replayMismatches = 0;
        long duration = 0;
        for (long seed = 1; seed <= RUNS; seed++) {
            MatchSimulator simulator = simulator();
            MatchSimulator.SimulationResult result = simulator.simulateWithDiagnostics(team("BLUE", scenario.bluePosition, scenario.blueFarming),
                    team("RED", scenario.redPosition, scenario.redFarming), seed);
            MatchTimeline timeline = result.timeline();
            MatchTimeline replay = simulator.simulate(team("BLUE", scenario.bluePosition, scenario.blueFarming),
                    team("RED", scenario.redPosition, scenario.redFarming), seed);
            deadFarmCs += result.deadPlayerFarmAwards();
            duplicateFarmSuspicions += result.duplicateEconomyResolutions();
            if (!signature(timeline).equals(signature(replay))) replayMismatches++;
            duration += timeline.getDurationSeconds();
            if (timeline.getWinner() == null) timeouts++;
            for (int i = 0; i < CHECKPOINTS.length; i++) collect(snapshotAtOrBefore(timeline, CHECKPOINTS[i]), checkpoints[i]);
            collect(timeline.getSnapshots().getLast(), ending);
        }
        System.out.printf("%nSCENARIO %s runs=%d timeoutRate=%.4f replayMismatch=%d deadFarmAwards=%d duplicateFarmSuspicions=%d avgDuration=%.1f%n", scenario, RUNS, timeouts / (double) RUNS, replayMismatches, deadFarmCs, duplicateFarmSuspicions, duration / (double) RUNS);
        for (int i = 0; i < CHECKPOINTS.length; i++) printCheckpoint(CHECKPOINTS[i], checkpoints[i]);
        Totals end = ending.get(Position.TOP);
        System.out.printf("END avgTeamCs blue=%.2f red=%.2f avgTeamGold blue=%.2f red=%.2f%n", end.teamBlueCs / (double) RUNS, end.teamRedCs / (double) RUNS, end.teamBlueGold / (double) RUNS, end.teamRedGold / (double) RUNS);
    }

    private static void printCheckpoint(int time, EnumMap<Position, Totals> totals) {
        System.out.printf("T=%dm", time / 60);
        for (Position p : Position.values()) {
            Totals t = totals.get(p);
            System.out.printf(" %s[cs %.2f/%.2f d%+.2f gold %.2f/%.2f d%+.2f]", p, t.blueCs / (double) RUNS, t.redCs / (double) RUNS, (t.blueCs - t.redCs) / (double) RUNS, t.blueGold / (double) RUNS, t.redGold / (double) RUNS, (t.blueGold - t.redGold) / (double) RUNS);
        }
        Totals all = totals.get(Position.TOP);
        System.out.printf(" team[cs %.2f/%.2f gold %.2f/%.2f] supportCs %.2f/%.2f%n", all.teamBlueCs / (double) RUNS, all.teamRedCs / (double) RUNS, all.teamBlueGold / (double) RUNS, all.teamRedGold / (double) RUNS, totals.get(Position.SUPPORT).blueCs / (double) RUNS, totals.get(Position.SUPPORT).redCs / (double) RUNS);
    }

    private static EnumMap<Position, Totals> newTotals() { EnumMap<Position, Totals> map = new EnumMap<>(Position.class); for (Position p : Position.values()) map.put(p, new Totals()); return map; }
    private static void collect(MatchSnapshot snapshot, EnumMap<Position, Totals> totals) { for (PlayerSnapshot p : snapshot.getPlayerSnapshots()) { Totals t = totals.get(p.getPosition()); boolean blue = p.getTeamName().equals("BLUE"); if (blue) { t.blueCs += p.getCs(); t.blueGold += p.getGold(); } else { t.redCs += p.getCs(); t.redGold += p.getGold(); } } Totals anchor = totals.get(Position.TOP); anchor.teamBlueCs += snapshot.getPlayerSnapshots().stream().filter(p -> p.getTeamName().equals("BLUE")).mapToInt(PlayerSnapshot::getCs).sum(); anchor.teamRedCs += snapshot.getPlayerSnapshots().stream().filter(p -> p.getTeamName().equals("RED")).mapToInt(PlayerSnapshot::getCs).sum(); anchor.teamBlueGold += snapshot.getBlueGold(); anchor.teamRedGold += snapshot.getRedGold(); }
    private static MatchSnapshot snapshotAtOrBefore(MatchTimeline timeline, int seconds) { MatchSnapshot result = timeline.getSnapshots().getFirst(); for (MatchSnapshot snapshot : timeline.getSnapshots()) { if (snapshot.getTimeSeconds() > seconds) break; result = snapshot; } return result; }
    private static Team team(String name, Position modified, int farming) { return new Team(name, List.of(player("TOP", Position.TOP, modified, farming), player("JUNGLE", Position.JUNGLE, modified, farming), player("MID", Position.MID, modified, farming), player("ADC", Position.ADC, modified, farming), player("SUPPORT", Position.SUPPORT, modified, farming))); }
    private static Player player(String name, Position position, Position modified, int farming) { return new Player(name, position, new PlayerAttributes(14, 14, position == modified ? farming : 14, 14)); }
    private static MatchSimulator simulator() { return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(), new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(), new StructureResolver(), new PushResolver()); }
    private static String signature(MatchTimeline timeline) { return timeline.getDurationSeconds() + ":" + timeline.getWinner() + ":" + timeline.getEvents().size() + ":" + timeline.getSnapshots().getLast().getBlueGold() + ":" + timeline.getSnapshots().getLast().getRedGold(); }
    private enum Scenario { A(null, 14, null, 14), B(Position.TOP, 18, Position.TOP, 10), C(Position.JUNGLE, 18, Position.JUNGLE, 10), D(Position.ADC, 18, Position.ADC, 10), E(Position.SUPPORT, 18, Position.SUPPORT, 10); final Position bluePosition, redPosition; final int blueFarming, redFarming; Scenario(Position bp, int bf, Position rp, int rf) { bluePosition = bp; blueFarming = bf; redPosition = rp; redFarming = rf; } }
    private static final class Totals { long blueCs, redCs, blueGold, redGold, teamBlueCs, teamRedCs, teamBlueGold, teamRedGold; }
}
