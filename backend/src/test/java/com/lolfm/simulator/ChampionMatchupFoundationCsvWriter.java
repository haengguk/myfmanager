package com.lolfm.simulator;

import com.lolfm.champion.ChampionMatchupProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

final class ChampionMatchupFoundationCsvWriter {
    void write(Path directory, ChampionMatchupFoundationAuditResult result) throws Exception {
        Files.createDirectories(directory);
        writeSummary(directory.resolve("champion-matchup-foundation-summary.csv"), result);
        writeCatalog(directory.resolve("champion-matchup-foundation-catalog.csv"), result);
        writeDirectionality(
                directory.resolve("champion-matchup-foundation-directionality.csv"), result);
        writeApplications(
                directory.resolve("champion-matchup-foundation-application.csv"), result);
        writeFullMatches(
                directory.resolve("champion-matchup-foundation-full-match.csv"), result);
        writePaired(directory.resolve("champion-matchup-foundation-paired.csv"), result);
        writeMirror(directory.resolve("champion-matchup-foundation-mirror.csv"), result);
        writeLog(directory.resolve("champion-matchup-foundation-audit.log"), result);
    }

    private void writeSummary(
            Path path,
            ChampionMatchupFoundationAuditResult result
    ) throws Exception {
        StringBuilder csv = new StringBuilder("metric,value\n");
        result.summary().forEach((key, value) -> row(csv, key, value));
        Files.writeString(path, csv);
    }

    private void writeCatalog(
            Path path,
            ChampionMatchupFoundationAuditResult result
    ) throws Exception {
        StringBuilder csv = new StringBuilder(
                "catalogVersion,position,firstChampion,secondChampion,context,"
                        + "edge,finite,neutral\n");
        for (ChampionMatchupProfile profile : orderedProfiles(result)) {
            for (ProgressionCombatContext context : ProgressionCombatContext.values()) {
                double edge = profile.edge(context);
                row(csv, result.catalog().version(), profile.pair().position(),
                        profile.pair().first(), profile.pair().second(), context,
                        edge, Double.isFinite(edge), edge == 0.0);
            }
        }
        Files.writeString(path, csv);
    }

    private void writeDirectionality(
            Path path,
            ChampionMatchupFoundationAuditResult result
    ) throws Exception {
        StringBuilder csv = new StringBuilder(
                "position,firstChampion,secondChampion,context,forwardEdge,"
                        + "reverseEdge,sum,directionalityValid\n");
        for (ChampionMatchupProfile profile : orderedProfiles(result)) {
            for (ProgressionCombatContext context : ProgressionCombatContext.values()) {
                double forward = result.catalog().contribution(
                        profile.pair().first(), profile.pair().second(),
                        profile.pair().position(), context);
                double reverse = result.catalog().contribution(
                        profile.pair().second(), profile.pair().first(),
                        profile.pair().position(), context);
                row(csv, profile.pair().position(), profile.pair().first(),
                        profile.pair().second(), context, forward, reverse,
                        forward + reverse, forward + reverse == 0.0);
            }
        }
        Files.writeString(path, csv);
    }

    private void writeApplications(
            Path path,
            ChampionMatchupFoundationAuditResult result
    ) throws Exception {
        StringBuilder csv = new StringBuilder(
                "pairId,position,context,direction,participantMode,featureMode,"
                        + "sourceChampion,opponentChampion,sourceSide,opponentSide,"
                        + "sourceAlive,opponentAlive,sourceParticipant,opponentParticipant,"
                        + "eligiblePairCount,expectedEdge,actualEdge,applicationCount,"
                        + "skipReason,directRandomCalls,mutationDetected,result\n");
        for (ChampionMatchupApplicationRow value : result.applications()) {
            row(csv, value.pairId(), value.position(), value.context(), value.direction(),
                    value.participantMode(), value.featureMode(), value.sourceChampion(),
                    value.opponentChampion(), value.sourceSide(), value.opponentSide(),
                    value.sourceAlive(), value.opponentAlive(), value.sourceParticipant(),
                    value.opponentParticipant(), value.eligiblePairCount(),
                    value.expectedEdge(), value.actualEdge(), value.applicationCount(),
                    value.skipReason(), value.directRandomCalls(),
                    value.mutationDetected(), value.result());
        }
        Files.writeString(path, csv);
    }

    private void writeFullMatches(
            Path path,
            ChampionMatchupFoundationAuditResult result
    ) throws Exception {
        StringBuilder csv = new StringBuilder(
                "lineupId,targetPosition,skillProfile,matchupMode,direction,seed,"
                        + "winner,winnerSide,duration,timelineHash,snapshotHash,"
                        + "randomDrawCount,matchupApplications,nonZeroMatchupApplications,"
                        + "endReason,mismatch\n");
        for (ChampionMatchupFullMatchRow value : result.fullMatches()) {
            row(csv, value.lineupId(), value.targetPosition(), value.skillProfile(),
                    value.matchupMode(), value.direction(), value.seed(), value.winner(),
                    value.winnerSide(), value.duration(), value.timelineHash(),
                    value.snapshotHash(), value.randomDrawCount(),
                    value.matchupApplications(), value.nonZeroMatchupApplications(),
                    value.endReason(), value.mismatch());
        }
        Files.writeString(path, csv);
    }

    private void writePaired(
            Path path,
            ChampionMatchupFoundationAuditResult result
    ) throws Exception {
        StringBuilder csv = new StringBuilder(
                "lineupId,skillProfile,direction,seed,offWinner,onWinner,"
                        + "winnerMismatch,durationMismatch,timelineMismatch,"
                        + "snapshotMismatch,randomDrawMismatch,offApplications,"
                        + "onApplications,onNonZeroApplications\n");
        for (ChampionMatchupPairedRow value : result.pairedMatches()) {
            row(csv, value.lineupId(), value.skillProfile(), value.direction(),
                    value.seed(), value.offWinner(), value.onWinner(),
                    value.winnerMismatch(), value.durationMismatch(),
                    value.timelineMismatch(), value.snapshotMismatch(),
                    value.randomDrawMismatch(), value.offApplications(),
                    value.onApplications(), value.onNonZeroApplications());
        }
        Files.writeString(path, csv);
    }

    private void writeMirror(
            Path path,
            ChampionMatchupFoundationAuditResult result
    ) throws Exception {
        StringBuilder csv = new StringBuilder(
                "pairId,position,context,originalBlueEdge,mirroredBlueEdge,"
                        + "originalLogicalTeamAEdge,mirroredLogicalTeamAEdge,"
                        + "neutralOriginalEdge,neutralMirroredEdge,applicationCount,"
                        + "directRandomCalls,logicalIdentityPreserved,sideEdgeReversed,"
                        + "exactZeroStable,result\n");
        for (ChampionMatchupMirrorRow value : result.mirrorRows()) {
            row(csv, value.pairId(), value.position(), value.context(),
                    value.originalBlueEdge(), value.mirroredBlueEdge(),
                    value.originalLogicalTeamAEdge(), value.mirroredLogicalTeamAEdge(),
                    value.neutralOriginalEdge(), value.neutralMirroredEdge(),
                    value.applicationCount(), value.directRandomCalls(),
                    value.logicalIdentityPreserved(), value.sideEdgeReversed(),
                    value.exactZeroStable(), value.result());
        }
        Files.writeString(path, csv);
    }

    private void writeLog(
            Path path,
            ChampionMatchupFoundationAuditResult result
    ) throws Exception {
        String log = String.join("\n",
                "auditVersion=" + result.summary().get("auditVersion"),
                "catalogVersion=" + result.summary().get("catalogVersion"),
                "catalogRows=" + result.catalog().profiles().size()
                        * ProgressionCombatContext.values().length,
                "applicationRows=" + result.applications().size(),
                "neutralFullMatchRows=" + result.fullMatches().size(),
                "neutralPairedMatches=" + result.pairedMatches().size(),
                "mirrorRows=" + result.mirrorRows().size(),
                "warningCodes=" + result.summary().get("warningCodes"),
                "integrityErrorCount=" + result.summary().get("integrityErrorCount"),
                "verdict=" + result.summary().get("verdict")) + "\n";
        Files.writeString(path, log);
    }

    private List<ChampionMatchupProfile> orderedProfiles(
            ChampionMatchupFoundationAuditResult result
    ) {
        return result.catalog().profiles().values().stream()
                .sorted(Comparator
                        .comparing((ChampionMatchupProfile value) ->
                                value.pair().position().toString())
                        .thenComparing(value -> value.pair().first().value())
                        .thenComparing(value -> value.pair().second().value()))
                .toList();
    }

    private void row(StringBuilder target, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) target.append(',');
            String value = String.valueOf(values[index]);
            if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                target.append('"').append(value.replace("\"", "\"\"")).append('"');
            } else {
                target.append(value);
            }
        }
        target.append('\n');
    }
}
