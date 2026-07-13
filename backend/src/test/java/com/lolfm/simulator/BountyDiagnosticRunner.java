package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runs the required fixed-seed audit without parsing event messages. */
public final class BountyDiagnosticRunner {
    private static final int RUNS = 2_000;

    public static void main(String[] args) {
        MatchSimulator simulator = new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(), new StructureResolver(), new PushResolver());
        for (Scenario scenario : List.of(new Scenario("A", 14, 14), new Scenario("B", 15, 14), new Scenario("C", 16, 14),
                new Scenario("D", 18, 10), new Scenario("E", 10, 18))) {
            Metrics metrics = new Metrics(scenario);
            Team blue = team("BLUE", scenario.blue());
            Team red = team("RED", scenario.red());
            for (long seed = 1; seed <= RUNS; seed++) metrics.record(simulator.simulateWithDiagnostics(blue, red, seed));
            metrics.print();
        }
    }

    private static Team team(String name, int attribute) {
        List<Player> players = new ArrayList<>();
        for (Position position : Position.values()) players.add(new Player(name + " " + position, position,
                new PlayerAttributes(attribute, attribute, attribute, attribute)));
        return new Team(name, players);
    }

    private record Scenario(String name, int blue, int red) { }

    private static final class Metrics {
        private final Scenario scenario;
        private int blueWins, redWins, timeouts, visibleActivations, visibleWhileBehind, shutdowns, shutdownsWhileBehind, behindShutdowns, behindShutdownWins, carryGames;
        private long totalDuration, totalShutdownGold;
        private double maxProgress, gapAtShutdownTotal, gapChangeFiveMinutesTotal;
        private int gapSamples, gapChangeSamples;
        private final List<Integer> durations = new ArrayList<>();
        private final Map<Integer, Integer> payouts = new LinkedHashMap<>();
        private final List<Double> strongShutdownGold = new ArrayList<>(), weakShutdownGold = new ArrayList<>();
        private int weakShutdownGames, weakShutdownWins, weakNoShutdownGames, weakNoShutdownWins;

        Metrics(Scenario scenario) { this.scenario = scenario; for (int i = 150; i <= 700; i += 50) payouts.put(i, 0); }

        void record(MatchSimulator.SimulationResult result) {
            MatchTimeline timeline = result.timeline();
            durations.add(timeline.getDurationSeconds()); totalDuration += timeline.getDurationSeconds();
            if ("BLUE".equals(timeline.getWinner())) blueWins++; else if ("RED".equals(timeline.getWinner())) redWins++;
            if (result.endReason() == GameEndReason.SIMULATION_TIMEOUT) timeouts++;
            Map<String, Boolean> priorVisible = new java.util.HashMap<>();
            Map<String, String> playerTeams = new java.util.HashMap<>();
            for (MatchSnapshot snapshot : timeline.getSnapshots()) {
                snapshot.getPlayerSnapshots().forEach(player -> {
                    playerTeams.put(player.getPlayerName(), player.getTeamName());
                    maxProgress = Math.max(maxProgress, player.getBountyProgress());
                    boolean visible = player.isHasShutdownBounty();
                    if (visible && !priorVisible.getOrDefault(player.getPlayerName(), false)) {
                        visibleActivations++;
                        boolean bluePlayer = "BLUE".equals(player.getTeamName());
                        if ((bluePlayer && snapshot.getBlueGold() < snapshot.getRedGold()) || (!bluePlayer && snapshot.getRedGold() < snapshot.getBlueGold())) visibleWhileBehind++;
                    }
                    priorVisible.put(player.getPlayerName(), visible);
                });
            }
            boolean weakIsBlue = scenario.blue() < scenario.red(); boolean weakGotShutdown = false; boolean carryThisGame = false;
            for (MatchEvent event : timeline.getEvents()) if (event.getType() == MatchEventType.SHUTDOWN) {
                shutdowns++; totalShutdownGold += event.getGoldAmount(); payouts.compute(event.getGoldAmount(), (k, v) -> v == null ? 1 : v + 1);
                if (event.getBountyRawBeforePayout() > 700) carryThisGame = true;
                MatchSnapshot before = snapshotAtOrBefore(timeline, event.getTimeSeconds());
                MatchSnapshot after = snapshotAtOrBefore(timeline, event.getTimeSeconds() + 300);
                boolean blueKiller = "BLUE".equals(playerTeams.get(event.getKiller()));
                int signedGap = blueKiller ? before.getBlueGold() - before.getRedGold() : before.getRedGold() - before.getBlueGold();
                gapAtShutdownTotal += signedGap; gapSamples++;
                if (signedGap < 0) { shutdownsWhileBehind++; behindShutdowns++; if ((blueKiller && "BLUE".equals(timeline.getWinner())) || (!blueKiller && "RED".equals(timeline.getWinner()))) behindShutdownWins++; }
                int afterGap = blueKiller ? after.getBlueGold() - after.getRedGold() : after.getRedGold() - after.getBlueGold();
                gapChangeFiveMinutesTotal += afterGap - signedGap; gapChangeSamples++;
                boolean strongKiller = scenario.blue() > scenario.red() ? blueKiller : !blueKiller;
                (strongKiller ? strongShutdownGold : weakShutdownGold).add((double) event.getGoldAmount());
                if (!strongKiller) weakGotShutdown = true;
            }
            boolean weakWon = weakIsBlue ? "BLUE".equals(timeline.getWinner()) : "RED".equals(timeline.getWinner());
            if (weakGotShutdown) { weakShutdownGames++; if (weakWon) weakShutdownWins++; } else { weakNoShutdownGames++; if (weakWon) weakNoShutdownWins++; }
            if (carryThisGame) carryGames++;
        }

        private MatchSnapshot snapshotAtOrBefore(MatchTimeline timeline, int seconds) { MatchSnapshot answer = timeline.getSnapshots().getFirst(); for (MatchSnapshot s : timeline.getSnapshots()) { if (s.getTimeSeconds() > seconds) break; answer = s; } return answer; }
        private int percentile(double p) { List<Integer> sorted = new ArrayList<>(durations); sorted.sort(Integer::compareTo); return sorted.get((int) Math.ceil(p * sorted.size()) - 1); }
        private double rate(int count) { return count / (double) RUNS; }
        private double avg(List<Double> values) { return values.isEmpty() ? 0 : values.stream().mapToDouble(Double::doubleValue).average().orElse(0); }
        void print() {
            long over40 = durations.stream().filter(v -> v >= 2400).count(), over60 = durations.stream().filter(v -> v >= 3600).count();
            System.out.printf("SCENARIO %s blueWin=%.4f redWin=%.4f avgDuration=%.1f median=%d p90=%d p95=%d over40=%.4f over60=%.4f timeout=%.4f visiblePerGame=%.4f shutdownPerGame=%.4f shutdownGoldPerGame=%.2f avgPayout=%.2f maxProgress=%.3f carryGameRate=%.4f visibleBehind=%d visibleBehindRate=%.4f shutdownBehind=%d avgGapAtShutdown=%.2f behindShutdown=%d behindShutdownWinRate=%.4f fiveMinGapChange=%.2f payouts=%s%n",
                    scenario.name, rate(blueWins), rate(redWins), totalDuration/(double)RUNS, percentile(.5), percentile(.9), percentile(.95), rate((int)over40), rate((int)over60), rate(timeouts), visibleActivations/(double)RUNS, shutdowns/(double)RUNS, totalShutdownGold/(double)RUNS, shutdowns == 0 ? 0 : totalShutdownGold/(double)shutdowns, maxProgress, rate(carryGames), visibleWhileBehind, visibleActivations == 0 ? 0 : visibleWhileBehind/(double)visibleActivations, shutdownsWhileBehind, gapSamples == 0 ? 0 : gapAtShutdownTotal/gapSamples, behindShutdowns, behindShutdowns == 0 ? 0 : behindShutdownWins/(double)behindShutdowns, gapChangeSamples == 0 ? 0 : gapChangeFiveMinutesTotal/gapChangeSamples, payouts);
            if (scenario.blue != scenario.red) System.out.printf("STRENGTH %s strongWin=%.4f strongAvgShutdown=%.2f weakAvgShutdown=%.2f weakShutdownGameRate=%.4f weakWinWithShutdown=%.4f weakWinWithoutShutdown=%.4f%n", scenario.name, scenario.blue > scenario.red ? rate(blueWins) : rate(redWins), avg(strongShutdownGold), avg(weakShutdownGold), rate(weakShutdownGames), weakShutdownGames == 0 ? 0 : weakShutdownWins/(double)weakShutdownGames, weakNoShutdownGames == 0 ? 0 : weakNoShutdownWins/(double)weakNoShutdownGames);
        }
    }
}
