package com.lolfm.simulator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** Phase 13B.6 audit orchestrator; never mutates production rules. */
public final class SideOrientationAudit {
    private final SideOrientationMatchExecutor executor = new SideOrientationMatchExecutor();
    private final SideOrientationVerdictEvaluator verdicts = new SideOrientationVerdictEvaluator();

    public static void main(String[] args) throws Exception {
        int primary = Integer.getInteger("sideAudit.primarySeeds", 5_000);
        int screening = Integer.getInteger("sideAudit.screeningSeeds", 500);
        int escalation = Integer.getInteger("sideAudit.escalationSeeds", 3_000);
        Path out = Path.of(System.getProperty("sideAudit.output",
                "build/reports/side-orientation-audit"));
        new SideOrientationAudit().run(new SideOrientationAuditConfig(
                primary, screening, escalation, out));
    }

    SideOrientationAuditResult run(SideOrientationAuditConfig config) throws Exception {
        int integrity = verifyInstrumentation();
        List<SideOrientationCellStatistics> raw = new ArrayList<>();
        Map<String, SideOrientationVerdictEvaluator.CellEvidence> evidence = new LinkedHashMap<>();
        int primaryGames = 0, screeningGames = 0, extraGames = 0, escalatedCells = 0;
        SideOrientationCsvWriter.Counts counts;
        try (SideOrientationCsvWriter writer = new SideOrientationCsvWriter(config.outputDirectory())) {
            for (var fixture : SideOrientationFixtureFactory.neutralFixtures()) {
                var rows = cell(fixture, "PRIMARY", "CHAMPION_OFF", "NEUTRAL",
                        1, config.primarySeeds());
                primaryGames += rows.size();
                integrity += integrityErrors(rows);
                record(rows, raw, evidence, writer);
            }
            Map<String, CellRun> screens = new LinkedHashMap<>();
            for (String skill : List.of("S0", "S3")) {
                for (var fixture : SideOrientationFixtureFactory.focused(skill)) {
                    for (String mode : List.of("CHAMPION_OFF", "CHAMPION_ON")) {
                        var rows = cell(fixture, "SECONDARY", mode, skill,
                                1, config.screeningSeeds());
                        screeningGames += rows.size();
                        screens.put(key(rows), new CellRun(fixture, mode, skill, rows));
                    }
                }
            }
            for (CellRun run : screens.values()) {
                var screenStat = SideOrientationCellStatistics.calculate(run.rows);
                boolean expand = shouldEscalate(run.rows, screenStat, screens);
                List<SideOrientationMatchRow> finalRows = run.rows;
                if (expand && config.escalationSeeds() > config.screeningSeeds()) {
                    var added = cell(run.fixture, "SECONDARY", run.mode, run.skill,
                            config.screeningSeeds() + 1, config.escalationSeeds());
                    finalRows = new ArrayList<>(run.rows);
                    finalRows.addAll(added);
                    extraGames += added.size();
                    escalatedCells++;
                }
                integrity += integrityErrors(finalRows);
                record(finalRows, raw, evidence, writer);
            }
            counts = writer.counts();
        }
        var classified = verdicts.classify(raw, evidence);
        String verdict = verdicts.verdict(classified, integrity);
        var result = new SideOrientationAuditResult(classified, evidence,
                primaryGames, screeningGames, extraGames, escalatedCells,
                integrity, verdict, counts);
        new SideOrientationReportWriter().write(config.outputDirectory(), result);
        System.out.println("Side orientation audit complete: " + verdict);
        return result;
    }

    private List<SideOrientationMatchRow> cell(SideOrientationFixture fixture, String group,
            String mode, String skill, int from, int to) {
        return IntStream.rangeClosed(from, to).parallel().boxed().flatMap(seed ->
                List.of(SideOrientationFixture.Orientation.values()).stream().map(orientation ->
                        executor.run(fixture, orientation, seed, group, mode, skill,
                                SideOrientationAuditConfig.FIXED_TRACE_SEEDS.contains(seed)))).toList();
    }

    private void record(List<SideOrientationMatchRow> rows,
            List<SideOrientationCellStatistics> stats,
            Map<String, SideOrientationVerdictEvaluator.CellEvidence> evidence,
            SideOrientationCsvWriter writer) throws Exception {
        var cell = SideOrientationCellStatistics.calculate(rows);
        stats.add(cell);
        evidence.put(SideOrientationVerdictEvaluator.cellKey(cell),
                new SideOrientationVerdictEvaluator.CellEvidence(
                        SideOrientationVerdictEvaluator.structuralEvidence(rows),
                        SideOrientationVerdictEvaluator.championApplicationSkew(rows)));
        writer.writeCell(rows);
    }

    private boolean shouldEscalate(List<SideOrientationMatchRow> rows,
            SideOrientationCellStatistics s, Map<String, CellRun> screens) {
        if (s.orientationDifference() >= .03 || s.rawPValue() < .10
                || funnelDifference(rows, .03, false) || funnelDifference(rows, .02, true)) {
            return true;
        }
        if (rows.stream().flatMap(r -> r.ties().stream())
                .anyMatch(t -> "TEAM_SIDE_ORDER".equals(t.resolutionSource()))) return true;
        if ("CHAMPION_ON".equals(s.mode())) {
            CellRun off = screens.get(s.fixture() + "|" + s.skillProfile() + "|CHAMPION_OFF");
            return off != null && s.orientationDifference()
                    - SideOrientationCellStatistics.calculate(off.rows).orientationDifference() >= .02;
        }
        return false;
    }

    private boolean funnelDifference(List<SideOrientationMatchRow> rows,
            double threshold, boolean slot) {
        for (SideOrientationResolver resolver : SideOrientationResolver.values()) {
            long bNum = 0, rNum = 0, bDen = 0, rDen = 0;
            for (var row : rows) {
                var b = row.funnel().get(resolver).get(TeamSide.BLUE);
                var r = row.funnel().get(resolver).get(TeamSide.RED);
                bNum += slot ? b.blockedByMajorCombatSlot() : b.actualAttempts();
                rNum += slot ? r.blockedByMajorCombatSlot() : r.actualAttempts();
                bDen += slot ? b.eligibleEvaluations() : b.evaluations();
                rDen += slot ? r.eligibleEvaluations() : r.evaluations();
            }
            if (bDen > 0 && rDen > 0
                    && Math.abs(bNum / (double) bDen - rNum / (double) rDen) >= threshold) return true;
        }
        return false;
    }

    private int integrityErrors(List<SideOrientationMatchRow> rows) {
        return rows.stream().mapToInt(SideOrientationMatchRow::diagnosticsMismatch).sum();
    }

    private int verifyInstrumentation() throws Exception {
        var fixture = SideOrientationFixtureFactory.neutralFixtures().getFirst();
        byte[] unobserved = SideOrientationMatchExecutor.timelineBytes(fixture,
                SideOrientationFixture.Orientation.ORIGINAL, 7, false, false);
        byte[] observed = SideOrientationMatchExecutor.timelineBytes(fixture,
                SideOrientationFixture.Orientation.ORIGINAL, 7, false, true);
        var first = SideOrientationMatchExecutor.trace(fixture,
                SideOrientationFixture.Orientation.ORIGINAL, 7);
        var second = SideOrientationMatchExecutor.trace(fixture,
                SideOrientationFixture.Orientation.ORIGINAL, 7);
        int errors = java.util.Arrays.equals(unobserved, observed) ? 0 : 1;
        if (!first.equals(second)) errors++;
        return errors;
    }

    private String key(List<SideOrientationMatchRow> rows) {
        var r = rows.getFirst();
        return r.fixtureId() + "|" + r.skillProfile() + "|" + r.mode();
    }

    private record CellRun(SideOrientationFixture fixture, String mode, String skill,
            List<SideOrientationMatchRow> rows) {
    }
}
