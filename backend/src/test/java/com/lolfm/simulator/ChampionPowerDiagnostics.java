package com.lolfm.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lolfm.champion.*;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.PlayerSnapshot;
import com.lolfm.domain.Position;
import com.lolfm.factory.DummyDataFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** Large deterministic Phase 13B audit. Deliberately excluded from the normal test task. */
public final class ChampionPowerDiagnostics {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ChampionCatalog CHAMPIONS = new ChampionCatalog(JSON);
    private static final ChampionPowerProfileCatalog PROFILES = new ChampionPowerProfileCatalog(JSON, CHAMPIONS);
    private static final ChampionSelectionValidator SELECTIONS = new ChampionSelectionValidator(CHAMPIONS);
    private static final double EPSILON = 1e-9;

    enum Mode { FOUNDATION_OFF, CHAMPION_ON }

    public static void main(String[] args) throws Exception {
        StringBuilder log = new StringBuilder();
        StringBuilder summary = new StringBuilder("scenario,mode,seeds,blueWins,redWins,blueWinRate,avgDuration,medianDuration,p90Duration,p95Duration,nexusFinish,timeout,avgBlueKills,avgRedKills,avgBlueGold,avgRedGold,avgLevel,avgChampionNonContextModifier,combatSamples,playerClamps,teamClamps,offContributionErrors,missingAssignments,participantErrors,randomCalls\n");
        StringBuilder context = new StringBuilder("scenario,mode,dimension,count,avgBluePower,avgRedPower,avgSignedEdge,avgAbsoluteEdge,p50AbsoluteEdge,p90AbsoluteEdge,p95AbsoluteEdge,maxAbsoluteEdge,avgFinalContribution,avgAbsoluteContribution,avgParticipants,positiveRatio,negativeRatio,zeroRatio,playerClamps,teamClamps,shareMean,shareP50,shareP90,shareP95,shareMax,shareOverScoreRatio\n");
        StringBuilder lineup = new StringBuilder("scenario,seeds,offBlueWinRate,onBlueWinRate,winRateDelta,offAvgDuration,onAvgDuration,durationDelta,durationDeltaRatio,pairedWinnerChanges\n");

        Map<String, Map<Mode, Aggregate>> results = new LinkedHashMap<>();
        Map<String, List<String>> offProjectionHashes = new LinkedHashMap<>();
        int replayMismatch = replayMismatch();
        for (Scenario scenario : scenarios()) {
            EnumMap<Mode, Aggregate> modes = new EnumMap<>(Mode.class);
            for (Mode mode : Mode.values()) {
                Aggregate aggregate = new Aggregate(scenario.label(), mode);
                List<String> hashes = mode == Mode.FOUNDATION_OFF && !scenario.mirrored() ? new ArrayList<>() : null;
                for (int seed = 1; seed <= scenario.seeds(); seed++) {
                    MatchSimulator.SimulationResult result = run(scenario, mode, seed);
                    aggregate.add(result);
                    if (hashes != null) hashes.add(gameplayHash(result.timeline()));
                    if (seed % 100 == 0) System.out.printf(Locale.ROOT,
                            "CHAMPION_POWER_PROGRESS scenario=%s mode=%s seeds=%d/%d%n",
                            scenario.label(), mode, seed, scenario.seeds());
                }
                modes.put(mode, aggregate);
                aggregate.writeSummary(summary);
                aggregate.writeContexts(context);
                if (hashes != null) offProjectionHashes.put(scenario.key(), List.copyOf(hashes));
            }
            results.put(scenario.label(), Map.copyOf(modes));
        }

        int offGameplayMismatches = compareOffProjections(offProjectionHashes);
        for (Scenario scenario : scenarios()) {
            Aggregate off = results.get(scenario.label()).get(Mode.FOUNDATION_OFF);
            Aggregate on = results.get(scenario.label()).get(Mode.CHAMPION_ON);
            lineup.append(csv(scenario.label(), scenario.seeds(), off.winRate(), on.winRate(), on.winRate() - off.winRate(),
                    off.avgDuration(), on.avgDuration(), on.avgDuration() - off.avgDuration(),
                    off.avgDuration() == 0 ? 0 : (on.avgDuration() - off.avgDuration()) / off.avgDuration(),
                    pairedWinnerChanges(off, on))).append('\n');
        }

        ChampionPowerBudgetAuditor.Audit budget = new ChampionPowerBudgetAuditor(CHAMPIONS, PROFILES).audit();
        String budgetCsv = budgetCsv(budget);
        Pairwise pairwise = pairwiseCsv();
        String warnings = warnings(budget, results);
        int integrityErrors = offGameplayMismatches + replayMismatch + pairwise.integrityErrors();
        for (Map<Mode, Aggregate> modes : results.values()) for (Aggregate value : modes.values()) {
            integrityErrors += value.offContributionErrors + value.missingAssignments + value.participantErrors + value.randomCalls;
        }

        log.append("CHAMPION_POWER_PROFILE_VERSION=").append(PROFILES.profileVersion()).append('\n');
        log.append("CHAMPION_POOL_VERSION=").append(CHAMPIONS.championPoolVersion()).append('\n');
        log.append("PROFILE_COUNT=").append(PROFILES.all().size()).append(" LEVEL_CURVES=").append(PROFILES.levelCurves().size())
                .append(" ITEM_CURVES=").append(PROFILES.itemCurves().size()).append('\n');
        log.append("OFF_GAMEPLAY_MISMATCH=").append(offGameplayMismatches).append('\n');
        log.append("REPLAY_MISMATCH=").append(replayMismatch).append('\n');
        log.append("PAIRWISE_UNIQUE_PAIRS=").append(pairwise.uniquePairs()).append(" ROWS=").append(pairwise.rows()).append('\n');
        log.append("BUDGET overallAverage=").append(f(budget.overallAverage())).append(" p10=").append(f(budget.p10()))
                .append(" p50=").append(f(budget.p50())).append(" p90=").append(f(budget.p90()))
                .append(" min=").append(f(budget.minimum())).append(" max=").append(f(budget.maximum())).append('\n');
        for (var position : budget.positions().values()) log.append("POSITION_BUDGET ").append(position.position())
                .append(" average=").append(f(position.average())).append(" min=").append(f(position.minimum()))
                .append(" max=").append(f(position.maximum())).append(" range=").append(f(position.range()))
                .append(" warnings=").append(position.warnings()).append('\n');
        log.append("WARNINGS=").append(warnings).append('\n');
        log.append("INTEGRITY_ERRORS=").append(integrityErrors).append('\n');

        Files.writeString(Path.of("champion-power-diagnostics.log"), log);
        Files.writeString(Path.of("champion-power-summary.csv"), summary);
        Files.writeString(Path.of("champion-power-context.csv"), context);
        Files.writeString(Path.of("champion-power-budget.csv"), budgetCsv);
        Files.writeString(Path.of("champion-power-pairwise.csv"), pairwise.csv());
        Files.writeString(Path.of("champion-power-lineup-comparison.csv"), lineup);
        System.out.print(log);
        if (integrityErrors != 0) throw new IllegalStateException("Champion Power diagnostics integrity failure: " + integrityErrors);
        System.out.println("CHAMPION_POWER_DIAGNOSTICS=PASS");
    }

    private static MatchSimulator.SimulationResult run(Scenario scenario, Mode mode, long seed) {
        DummyDataFactory teams = new DummyDataFactory();
        MatchChampionAssignments assignments = SELECTIONS.resolve(scenario.selection());
        return simulator(mode).simulateWithDiagnostics(teams.createBlueTeam(), teams.createRedTeam(), seed, assignments);
    }
    private static MatchSimulator simulator(Mode mode) {
        SimulationOptions options = SimulationOptions.productionDefaults().withDiagnosticsEnabled(true)
                .withChampionPowerEnabled(mode == Mode.CHAMPION_ON);
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(CHAMPIONS),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), options);
    }

    private static int replayMismatch() throws Exception {
        Scenario c = scenarios().getFirst();
        MatchTimeline a = run(c, Mode.CHAMPION_ON, 7).timeline();
        MatchTimeline b = run(c, Mode.CHAMPION_ON, 7).timeline();
        return JSON.writeValueAsString(a).equals(JSON.writeValueAsString(b)) ? 0 : 1;
    }
    private static int pairedWinnerChanges(Aggregate off, Aggregate on) {
        int changes = 0;
        for (int i = 0; i < off.winners.size(); i++) if (off.winners.get(i) != on.winners.get(i)) changes++;
        return changes;
    }
    private static int compareOffProjections(Map<String, List<String>> hashes) {
        List<String> c = hashes.get("C"), d = hashes.get("D"), e = hashes.get("E");
        int mismatches = 0;
        for (int i = 0; i < 1000; i++) { if (!c.get(i).equals(d.get(i))) mismatches++; if (!c.get(i).equals(e.get(i))) mismatches++; }
        return mismatches;
    }
    private static String gameplayHash(MatchTimeline timeline) throws Exception {
        JsonNode tree = JSON.valueToTree(timeline); removeChampionMetadata(tree);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(tree.toString().getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
    private static void removeChampionMetadata(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<String> remove = new ArrayList<>();
            object.fieldNames().forEachRemaining(name -> { String lower = name.toLowerCase(Locale.ROOT);
                if (lower.startsWith("champion") || lower.equals("powerprofile")) remove.add(name); });
            remove.forEach(object::remove); object.elements().forEachRemaining(ChampionPowerDiagnostics::removeChampionMetadata);
        } else if (node.isArray()) node.elements().forEachRemaining(ChampionPowerDiagnostics::removeChampionMetadata);
    }

    private static String budgetCsv(ChampionPowerBudgetAuditor.Audit audit) {
        StringBuilder out = new StringBuilder("championId,position,levelCurveId,itemCurveId,profileBudget,minStandardizedValue,maxStandardizedValue,strongestContext,weakestContext,playerClampSamples,standardizedSamples,warnings\n");
        for (var value : audit.champions()) out.append(csv(value.championId(), value.position(), value.levelCurveId(), value.itemCurveId(),
                value.profileBudget(), value.minimumStandardizedValue(), value.maximumStandardizedValue(), value.strongestContext(),
                value.weakestContext(), value.playerClampSamples(), value.standardizedSamples(), String.join("|", value.warnings()))).append('\n');
        return out.toString();
    }
    private static Pairwise pairwiseCsv() {
        StringBuilder out = new StringBuilder("position,championA,championB,state,context,aModifier,bModifier,difference,sign\n");
        record State(String id, int level, ItemProgressStage item) { }
        List<State> states = List.of(new State("EARLY", 6, ItemProgressStage.FIRST_CORE),
                new State("MID", 11, ItemProgressStage.SECOND_CORE), new State("LATE", 16, ItemProgressStage.THIRD_CORE),
                new State("FULL", 18, ItemProgressStage.FOURTH_CORE));
        ChampionPowerProfileEvaluator evaluator = new ChampionPowerProfileEvaluator(PROFILES);
        Set<String> pairs = new HashSet<>(); int rows = 0;
        for (Position position : Position.values()) {
            List<ChampionDefinition> champions = CHAMPIONS.forPosition(position);
            for (int a = 0; a < champions.size(); a++) for (int b = a + 1; b < champions.size(); b++) {
                ChampionId one = champions.get(a).id(), two = champions.get(b).id(); pairs.add(position + ":" + one + ":" + two);
                for (State state : states) for (ProgressionCombatContext context : ProgressionCombatContext.values()) {
                    double av = evaluator.evaluate(one, state.level(), state.item(), context).clampedPlayerChampionPower();
                    double bv = evaluator.evaluate(two, state.level(), state.item(), context).clampedPlayerChampionPower();
                    double difference = av - bv; String sign = difference > EPSILON ? "A_GT_B" : difference < -EPSILON ? "A_LT_B" : "EQUAL";
                    out.append(csv(position, one, two, state.id(), context, av, bv, difference, sign)).append('\n'); rows++;
                }
            }
        }
        return new Pairwise(out.toString(), pairs.size(), rows, pairs.size() == 75 && rows == 2700 ? 0 : 1);
    }
    private static String warnings(ChampionPowerBudgetAuditor.Audit audit, Map<String, Map<Mode, Aggregate>> results) {
        List<String> values = new ArrayList<>(audit.catalogWarnings()); values.addAll(audit.globalWarnings());
        audit.champions().forEach(v -> v.warnings().forEach(w -> values.add(v.championId() + ":" + w)));
        audit.positions().values().forEach(v -> v.warnings().forEach(w -> values.add(v.position() + ":" + w)));
        for (var scenario : results.entrySet()) {
            Aggregate off = scenario.getValue().get(Mode.FOUNDATION_OFF), on = scenario.getValue().get(Mode.CHAMPION_ON);
            if (Math.abs(on.winRate() - off.winRate()) > .15) values.add(scenario.getKey() + ":WIN_RATE_DELTA_GT_15PP");
            if (off.avgDuration() > 0 && Math.abs(on.avgDuration() - off.avgDuration()) / off.avgDuration() > .10) values.add(scenario.getKey() + ":DURATION_DELTA_GT_10_PERCENT");
        }
        return values.toString();
    }

    private static List<Scenario> scenarios() {
        Scenario c = scenario("C", List.of("renekton","sejuani","azir","jinx","nautilus"), List.of("jax","lee-sin","ahri","kaisa","rakan"), false);
        Scenario d = scenario("D", List.of("ornn","vi","orianna","lucian","lulu"), List.of("gwen","nidalee","leblanc","ezreal","braum"), false);
        Scenario e = scenario("E", List.of("kennen","viego","sylas","aphelios","renata-glasc"), List.of("ksante","maokai","viktor","varus","bard"), false);
        return List.of(c, d, e, mirror(c), mirror(d), mirror(e));
    }
    private static Scenario scenario(String key, List<String> blue, List<String> red, boolean mirror) {
        return new Scenario(key, key + (mirror ? "_MIRROR" : ""), selection(blue, red), mirror, mirror ? 500 : 1000);
    }
    private static Scenario mirror(Scenario source) {
        return new Scenario(source.key(), source.key() + "_MIRROR",
                new ChampionSelectionRequest(source.selection().red(), source.selection().blue()), true, 500);
    }
    private static ChampionSelectionRequest selection(List<String> blue, List<String> red) {
        return new ChampionSelectionRequest(lineup(blue), lineup(red));
    }
    private static ChampionLineupRequest lineup(List<String> ids) {
        return new ChampionLineupRequest(ids.get(0), ids.get(1), ids.get(2), ids.get(3), ids.get(4));
    }

    private record Scenario(String key, String label, ChampionSelectionRequest selection, boolean mirrored, int seeds) { }
    private record Pairwise(String csv, int uniquePairs, int rows, int integrityErrors) { }

    private static final class Aggregate {
        final String scenario; final Mode mode; long seeds, blueWins, redWins, nexus, timeout, blueKills, redKills;
        double blueGold, redGold, level, nonContext; long players, offContributionErrors, missingAssignments, participantErrors, randomCalls, playerClamps, teamClamps;
        final List<Integer> durations = new ArrayList<>();
        final List<TeamSide> winners = new ArrayList<>();
        final EnumMap<ProgressionCombatContext, Samples> contexts = samples(ProgressionCombatContext.class);
        final EnumMap<ProgressionApplicationStage, Samples> stages = samples(ProgressionApplicationStage.class);
        Aggregate(String scenario, Mode mode) { this.scenario = scenario; this.mode = mode; }
        void add(MatchSimulator.SimulationResult result) {
            seeds++; winners.add(result.winnerSide()); if (result.winnerSide() == TeamSide.BLUE) blueWins++; else redWins++;
            if (result.endReason() == GameEndReason.NEXUS_DESTROYED) nexus++; if (result.endReason() == GameEndReason.SIMULATION_TIMEOUT) timeout++;
            durations.add(result.timeline().getDurationSeconds()); MatchSnapshot end = result.timeline().getSnapshots().getLast();
            blueKills += end.getBlueKills(); redKills += end.getRedKills(); blueGold += end.getBlueGold(); redGold += end.getRedGold();
            for (PlayerSnapshot player : end.getPlayerSnapshots()) { players++; level += player.getLevel();
                if (player.getChampion() != null && player.getChampion().powerProfile() != null) nonContext += player.getChampion().powerProfile().currentNonContextModifier(); }
            var stats = result.championPowerExecutionStats(); missingAssignments += stats.missingAssignment();
            participantErrors += stats.deadParticipantIncludedError() + stats.nonparticipantIncludedError() + stats.duplicateParticipantError(); randomCalls += stats.randomCallCount();
            for (ChampionPowerCombatSample sample : stats.samples()) {
                if (!sample.championPowerEnabled() && Math.abs(sample.finalChampionContribution()) > EPSILON) offContributionErrors++;
                if (sample.playerClampApplied()) playerClamps++; if (sample.teamClampApplied()) teamClamps++;
                contexts.get(sample.context()).add(sample); stages.get(sample.applicationStage()).add(sample);
            }
        }
        void writeSummary(StringBuilder out) {
            out.append(csv(scenario, mode, seeds, blueWins, redWins, winRate(), avgDuration(), percentile(durations,.5), percentile(durations,.9), percentile(durations,.95), nexus, timeout,
                    blueKills/(double)seeds, redKills/(double)seeds, blueGold/seeds, redGold/seeds, level/players, nonContext/players,
                    contexts.values().stream().mapToLong(s->s.count).sum(), playerClamps, teamClamps, offContributionErrors, missingAssignments, participantErrors, randomCalls)).append('\n');
        }
        void writeContexts(StringBuilder out) {
            contexts.forEach((key,value)->value.write(out,scenario,mode,"CONTEXT:"+key)); stages.forEach((key,value)->value.write(out,scenario,mode,"STAGE:"+key));
        }
        double winRate(){return seeds==0?0:blueWins/(double)seeds;} double avgDuration(){return durations.stream().mapToInt(Integer::intValue).average().orElse(0);}
    }
    private static final class Samples {
        long count, positive, negative, zero, playerClamps, teamClamps, shareOver; double blue, red, edge, absEdge, contribution, absContribution, participants;
        final List<Double> edges = new ArrayList<>(), shares = new ArrayList<>();
        void add(ChampionPowerCombatSample sample) {
            count++; boolean ownBlue = sample.ownSide() == TeamSide.BLUE;
            blue += ownBlue ? sample.ownAverageChampionPower() : sample.enemyAverageChampionPower();
            red += ownBlue ? sample.enemyAverageChampionPower() : sample.ownAverageChampionPower();
            double signed = ownBlue ? sample.finalEdge() : -sample.finalEdge(); double finalValue = ownBlue ? sample.finalChampionContribution() : -sample.finalChampionContribution();
            edge += signed; absEdge += Math.abs(signed); edges.add(Math.abs(signed)); contribution += finalValue; absContribution += Math.abs(finalValue); participants += sample.participantCount();
            if (finalValue > EPSILON) positive++; else if (finalValue < -EPSILON) negative++; else zero++;
            if (sample.playerClampApplied()) playerClamps++; if (sample.teamClampApplied()) teamClamps++;
            double share = Math.abs(finalValue) / Math.max(Math.abs(sample.existingScoreBeforeProgression()), .01); shares.add(share); if (share > 1) shareOver++;
        }
        void write(StringBuilder out, String scenario, Mode mode, String dimension) {
            out.append(csv(scenario,mode,dimension,count,ratio(blue,count),ratio(red,count),ratio(edge,count),ratio(absEdge,count),percentileD(edges,.5),percentileD(edges,.9),percentileD(edges,.95),max(edges),ratio(contribution,count),ratio(absContribution,count),ratio(participants,count),ratio(positive,count),ratio(negative,count),ratio(zero,count),playerClamps,teamClamps,average(shares),percentileD(shares,.5),percentileD(shares,.9),percentileD(shares,.95),max(shares),ratio(shareOver,count))).append('\n');
        }
    }
    private static <E extends Enum<E>> EnumMap<E, Samples> samples(Class<E> type) { EnumMap<E,Samples> result = new EnumMap<>(type); for(E value:type.getEnumConstants()) result.put(value,new Samples()); return result; }
    private static int percentile(List<Integer> values,double q){if(values.isEmpty())return 0;List<Integer> copy=new ArrayList<>(values);Collections.sort(copy);return copy.get((int)Math.min(copy.size()-1,Math.ceil(q*copy.size())-1));}
    private static double percentileD(List<Double> values,double q){if(values.isEmpty())return 0;List<Double> copy=new ArrayList<>(values);Collections.sort(copy);return copy.get((int)Math.min(copy.size()-1,Math.ceil(q*copy.size())-1));}
    private static double average(List<Double> values){return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);}
    private static double max(List<Double> values){return values.stream().mapToDouble(Double::doubleValue).max().orElse(0);}
    private static double ratio(double numerator,double denominator){return denominator==0?0:numerator/denominator;}
    private static String f(double value){return String.format(Locale.ROOT,"%.6f",value);}
    private static String csv(Object... values){return Arrays.stream(values).map(v->v instanceof Double d?f(d):String.valueOf(v)).reduce((a,b)->a+","+b).orElse("");}
}
