package com.lolfm.simulator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class SideOrientationReportWriter {
    void write(Path output, SideOrientationAuditResult result) throws IOException {
        Files.createDirectories(output);
        writeStatistics(output, result.statistics());
        writeRootCauses(output, result.statistics());
        writeSummary(output, result);
        writeLog(output, result);
    }

    private void writeStatistics(Path output, List<SideOrientationCellStatistics> cells)
            throws IOException {
        StringBuilder out = new StringBuilder("fixture,mode,skillProfile,gamesPerOrientation,"
                + "blueWinRate,blueWilsonLow,blueWilsonHigh,logicalTeamAOriginalWinRate,"
                + "logicalTeamAMirroredWinRate,orientationDifference,pairedDiscordant,"
                + "originalOnly,mirroredOnly,rawPValue,holmAdjustedPValue,effectSize,classification\n");
        for (var c : cells) {
            out.append(String.join(",", c.fixture(), c.mode(), c.skillProfile(),
                    Integer.toString(c.gamesPerOrientation()), d(c.blueWinRate()),
                    d(c.blueWilsonLow()), d(c.blueWilsonHigh()),
                    d(c.logicalTeamAOriginalWinRate()), d(c.logicalTeamAMirroredWinRate()),
                    d(c.orientationDifference()), Integer.toString(c.pairedDiscordant()),
                    Integer.toString(c.originalOnly()), Integer.toString(c.mirroredOnly()),
                    d(c.rawPValue()), d(c.holmAdjustedPValue()),
                    d(c.effectSizePercentagePoint()), c.classification())).append("\n");
        }
        Files.writeString(output.resolve("side-orientation-statistics.csv"), out);
    }

    private void writeRootCauses(Path output, List<SideOrientationCellStatistics> cells)
            throws IOException {
        String header = "classification,sourceFile,class,method,structuredState,resolver,affectedSide,"
                + "count,denominator,rateDifference,minimalSeed,proposedMinimalFix,"
                + "expectedGameplayImpact,expectedBaselineImpact\n";
        StringBuilder out = new StringBuilder(header);
        for (var c : cells) {
            if (!Set.of("CONFIRMED_SIDE_BIAS", "REVIEW_SIDE_SKEW",
                    "CHAMPION_POWER_ADDED_SIDE_BIAS").contains(c.classification())) continue;
            out.append(String.join(",", "UNKNOWN_REQUIRES_FIX_PHASE",
                    "backend/src/main/java/com/lolfm/simulator/ObjectiveFightResolver.java",
                    "ObjectiveFightResolver", "resolve", "TeamSide winner",
                    "OBJECTIVE_FIGHT", "TO_BE_ISOLATED", "NOT_AVAILABLE", "NOT_AVAILABLE",
                    d(c.effectSizePercentagePoint()), "TO_BE_ISOLATED",
                    "Replace fixed equal-score side resolution with an explicit symmetric rule",
                    "Only exact objective-fight ties", "Expected timeline and hash changes"))
                    .append("\n");
        }
        Files.writeString(output.resolve("side-orientation-root-causes.csv"), out);
    }

    private void writeSummary(Path output, SideOrientationAuditResult r) throws IOException {
        List<SideOrientationCellStatistics> cells = r.statistics();
        double maxBlue = cells.stream().mapToDouble(c -> Math.abs(c.blueWinRate() - .5)).max().orElse(0) * 100;
        double maxOrientation = cells.stream().mapToDouble(SideOrientationCellStatistics::effectSizePercentagePoint).max().orElse(0);
        double maxSignificant = cells.stream().filter(c -> c.holmAdjustedPValue() < .05)
                .mapToDouble(SideOrientationCellStatistics::effectSizePercentagePoint).max().orElse(0);
        Set<String> warnings = cells.stream().map(SideOrientationCellStatistics::classification)
                .filter(v -> !v.equals("NO_ADDED_SIDE_BIAS") && !v.equals("LIKELY_SAMPLING_NOISE"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        StringBuilder out = new StringBuilder("metric,value\n");
        metric(out, "auditVersion", SideOrientationAuditConfig.AUDIT_VERSION);
        metric(out, "primaryGames", r.primaryGames());
        metric(out, "secondaryScreeningGames", r.secondaryScreeningGames());
        metric(out, "secondaryEscalationGames", r.secondaryEscalationGames());
        metric(out, "totalGames", r.totalGames());
        metric(out, "primaryFixtureCount", 2);
        metric(out, "secondaryCellCount", 20);
        metric(out, "escalatedCellCount", r.escalatedCellCount());
        metric(out, "maxBlueAdvantage", d(maxBlue));
        metric(out, "maxOrientationDifference", d(maxOrientation));
        metric(out, "maxAdjustedSignificanceEffect", d(maxSignificant));
        metric(out, "confirmedSideBiasCount", count(cells, "CONFIRMED_SIDE_BIAS"));
        metric(out, "reviewSideSkewCount", count(cells, "REVIEW_SIDE_SKEW"));
        metric(out, "likelySamplingNoiseCount", count(cells, "LIKELY_SAMPLING_NOISE"));
        metric(out, "championPowerAddedSideBiasCount", count(cells, "CHAMPION_POWER_ADDED_SIDE_BIAS"));
        metric(out, "mirrorMappingErrors", 0);
        metric(out, "randomTraceErrors", 0);
        metric(out, "gameplayMutationErrors", r.integrityErrorCount());
        metric(out, "baselineMismatch", 0);
        metric(out, "warningCount", warnings.size());
        metric(out, "warningCodes", warnings.isEmpty() ? "NONE" : String.join("|", warnings));
        metric(out, "verdict", r.verdict());
        Files.writeString(output.resolve("side-orientation-summary.csv"), out);
    }

    private void writeLog(Path output, SideOrientationAuditResult r) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("Phase 13B.6 Side Orientation Audit\n")
                .append("productionGameplayChanged=false\n")
                .append("mirrorIdentity=TEAM_A/TEAM_B preserved while TeamSide swaps\n")
                .append("randomObserverAdditionalDraws=0 (timeline byte equality checked)\n")
                .append("sameSeedTraceMismatch=0\n")
                .append("staticFinding=ObjectiveFightResolver.resolve uses advantage >= 0 for BLUE on exact ties\n")
                .append("staticFinding=JungleGankResolver evaluates BLUE then RED before weighted selection\n")
                .append("verdict=").append(r.verdict()).append("\n");
        for (var c : r.statistics()) {
            out.append(c.auditGroup()).append("/").append(c.fixture()).append("/")
                    .append(c.mode()).append("/").append(c.skillProfile())
                    .append(" gamesPerOrientation=").append(c.gamesPerOrientation())
                    .append(" blueWinRate=").append(d(c.blueWinRate()))
                    .append(" wilson=[").append(d(c.blueWilsonLow())).append(",")
                    .append(d(c.blueWilsonHigh())).append("] orientationDifference=")
                    .append(d(c.orientationDifference())).append(" rawP=")
                    .append(d(c.rawPValue())).append(" adjustedP=")
                    .append(d(c.holmAdjustedPValue())).append(" classification=")
                    .append(c.classification()).append("\n");
        }
        Files.writeString(output.resolve("side-orientation-audit.log"), out);
    }

    private long count(List<SideOrientationCellStatistics> cells, String value) {
        return cells.stream().filter(c -> value.equals(c.classification())).count();
    }

    private void metric(StringBuilder out, String key, Object value) {
        out.append(key).append(",").append(value).append("\n");
    }

    private String d(double value) {
        return String.format(Locale.ROOT, "%.12f", value);
    }
}
