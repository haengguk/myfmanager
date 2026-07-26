package com.lolfm.simulator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SideOrientationCsvWriter implements AutoCloseable {
    private final BufferedWriter fullMatch;
    private final BufferedWriter paired;
    private final BufferedWriter funnel;
    private final BufferedWriter arbitration;
    private final BufferedWriter ties;
    private final BufferedWriter randomTrace;
    private long fullMatchRows;
    private long pairedRows;
    private long funnelRows;
    private long arbitrationRows;
    private long tieRows;
    private long traceRows;

    SideOrientationCsvWriter(Path output) throws IOException {
        Files.createDirectories(output);
        fullMatch = writer(output, "side-orientation-full-match.csv",
                "fixtureId,auditGroup,mode,skillProfile,orientation,seed,logicalTeamABlue,winnerSide,"
                        + "winnerLogicalTeam,durationSeconds,blueKills,redKills,blueGold,redGold,"
                        + "blueObjectives,redObjectives,blueStructures,redStructures,combatOutcomeCount,"
                        + "randomDrawCount,replayMismatch,diagnosticsMismatch");
        paired = writer(output, "side-orientation-paired-mirror.csv",
                "fixtureId,mode,skillProfile,seed,originalLogicalWinner,mirroredLogicalWinner,"
                        + "originalWinnerSide,mirroredWinnerSide,discordant,originalOnlyTeamAWin,"
                        + "mirroredOnlyTeamAWin,durationDifference,killDifference,objectiveDifference,"
                        + "structureDifference");
        funnel = writer(output, "side-orientation-resolver-funnel.csv",
                "auditGroup,fixtureId,mode,skillProfile,resolver,side,evaluations,eligible,triggered,"
                        + "attempted,outcomes,successes,kills,objectiveCaptures,mutations,nexusMutations,"
                        + "majorSlotConsumed,blockedByMajorSlot,blockedByEarlierAttempt,"
                        + "blockedByEarlierMutation,blockedByCooldown,blockedByNoCandidate,"
                        + "blockedByDeadParticipant,blockedByOtherEligibility,evaluatedFirst,"
                        + "attemptedFirst,mutatedFirst,eligibilityRate,triggerRate,attemptRate,outcomeRate,"
                        + "successRate,sharedSlotBlockRate,earlierAttemptBlockRate,mutationBlockRate,"
                        + "firstEvaluationRate,firstAttemptRate,firstMutationRate");
        arbitration = writer(output, "side-orientation-arbitration.csv",
                "tick,resolver,fixture,seed,bothEvaluated,bothEligible,bothTriggered,blueFirst,redFirst,"
                        + "blueAttempted,redAttempted,sharedSlotWinner,secondSideBlocked,blockReason,"
                        + "actualOutcomeSide");
        ties = writer(output, "side-orientation-tie-break.csv",
                "resolver,fixture,seed,tick,blueScore,redScore,scoreDifference,tieType,"
                        + "resolutionSource,winnerSide");
        randomTrace = writer(output, "side-orientation-random-trace.csv",
                "fixture,seed,drawIndex,resolverSource,side,tickSeconds,drawType,boundOrProbability,"
                        + "returnedValue,orientation,logicalTeamId");
    }

    void writeCell(List<SideOrientationMatchRow> rows) throws IOException {
        List<SideOrientationMatchRow> ordered = rows.stream()
                .sorted(Comparator.comparing(SideOrientationMatchRow::orientation)
                        .thenComparingInt(SideOrientationMatchRow::seed))
                .toList();
        for (SideOrientationMatchRow row : ordered) {
            fullMatch.write(row.csv());
            fullMatch.newLine();
            fullMatchRows++;
            writeTies(row);
            writeArbitrations(row);
            writeTrace(row);
        }
        writePairs(ordered);
        writeFunnel(ordered);
    }

    Counts counts() {
        return new Counts(fullMatchRows, pairedRows, funnelRows, arbitrationRows, tieRows, traceRows);
    }

    @Override
    public void close() throws IOException {
        fullMatch.close();
        paired.close();
        funnel.close();
        arbitration.close();
        ties.close();
        randomTrace.close();
    }

    private void writePairs(List<SideOrientationMatchRow> rows) throws IOException {
        Map<Integer, SideOrientationMatchRow> original = new HashMap<>();
        Map<Integer, SideOrientationMatchRow> mirrored = new HashMap<>();
        for (SideOrientationMatchRow row : rows) {
            (row.orientation() == SideOrientationFixture.Orientation.ORIGINAL ? original : mirrored)
                    .put(row.seed(), row);
        }
        for (int seed : original.keySet().stream().sorted().toList()) {
            SideOrientationMatchRow a = original.get(seed), b = mirrored.get(seed);
            boolean aWin = a.winnerLogicalTeam() == SideOrientationFixture.LogicalTeamId.TEAM_A;
            boolean bWin = b.winnerLogicalTeam() == SideOrientationFixture.LogicalTeamId.TEAM_A;
            paired.write(String.join(",", a.fixtureId(), a.mode(), a.skillProfile(),
                    Integer.toString(seed), a.winnerLogicalTeam().toString(),
                    b.winnerLogicalTeam().toString(), a.winnerSide().toString(),
                    b.winnerSide().toString(), Boolean.toString(aWin != bWin),
                    Boolean.toString(aWin && !bWin), Boolean.toString(!aWin && bWin),
                    Integer.toString(a.durationSeconds() - b.durationSeconds()),
                    Integer.toString((a.blueKills() + a.redKills()) - (b.blueKills() + b.redKills())),
                    Integer.toString((a.blueObjectives() + a.redObjectives())
                            - (b.blueObjectives() + b.redObjectives())),
                    Integer.toString((a.blueStructures() + a.redStructures())
                            - (b.blueStructures() + b.redStructures()))));
            paired.newLine();
            pairedRows++;
        }
    }

    private void writeFunnel(List<SideOrientationMatchRow> rows) throws IOException {
        SideOrientationFunnelAccumulator accumulator = new SideOrientationFunnelAccumulator();
        accumulator.add(rows);
        SideOrientationMatchRow first = rows.getFirst();
        for (SideOrientationResolver resolver : SideOrientationResolver.values()) {
            for (TeamSide side : TeamSide.values()) {
                funnel.write(accumulator.csv(first.auditGroup(), first.fixtureId(), first.mode(),
                        first.skillProfile(), resolver, side));
                funnel.newLine();
                funnelRows++;
            }
        }
    }

    private void writeTies(SideOrientationMatchRow row) throws IOException {
        for (SideOrientationMatchRow.TieRow tie : row.ties()) {
            ties.write(String.join(",", tie.resolver().toString(), tie.fixture(),
                    Integer.toString(tie.seed()), Integer.toString(tie.tick()),
                    Double.toString(tie.blueScore()), Double.toString(tie.redScore()),
                    Double.toString(tie.difference()), tie.tieType(), tie.resolutionSource(),
                    tie.winnerSide() == null ? "NONE" : tie.winnerSide().toString()));
            ties.newLine();
            tieRows++;
        }
    }

    private void writeArbitrations(SideOrientationMatchRow row) throws IOException {
        for (var a : row.arbitrations()) {
            arbitration.write(String.join(",", Integer.toString(a.tick()),
                    a.resolver().toString(), a.fixture(), Integer.toString(a.seed()),
                    Boolean.toString(a.bothEvaluated()), Boolean.toString(a.bothEligible()),
                    Boolean.toString(a.bothTriggered()), Boolean.toString(a.blueFirst()),
                    Boolean.toString(a.redFirst()), Boolean.toString(a.blueAttempted()),
                    Boolean.toString(a.redAttempted()), side(a.sharedSlotWinner()),
                    Boolean.toString(a.secondSideBlocked()), a.blockReason(),
                    side(a.actualOutcomeSide())));
            arbitration.newLine();
            arbitrationRows++;
        }
    }

    private String side(TeamSide side) {
        return side == null ? "NONE" : side.toString();
    }

    private void writeTrace(SideOrientationMatchRow row) throws IOException {
        for (var draw : row.trace()) {
            randomTrace.write(String.join(",", row.fixtureId(), Integer.toString(row.seed()),
                    Long.toString(draw.drawIndex()), draw.resolverSource().toString(),
                    draw.side() == null ? "NONE" : draw.side().toString(), Integer.toString(draw.tickSeconds()), draw.drawType(),
                    Integer.toString(draw.boundOrBits()), Integer.toString(draw.returnedValue()),
                    draw.orientation(), draw.logicalTeamId()));
            randomTrace.newLine();
            traceRows++;
        }
    }

    private BufferedWriter writer(Path output, String name, String header) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(output.resolve(name));
        writer.write(header);
        writer.newLine();
        return writer;
    }

    record Counts(long fullMatch, long paired, long funnel, long arbitration, long ties, long trace) {
    }
}
