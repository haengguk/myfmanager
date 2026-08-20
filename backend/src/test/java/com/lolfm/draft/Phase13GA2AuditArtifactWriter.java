package com.lolfm.draft;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.champion.ChampionDefinition;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.Position;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Writes the stable, sorted Phase 13G-A2 audit artifact set. */
public final class Phase13GA2AuditArtifactWriter {
    private Phase13GA2AuditArtifactWriter() { }

    public static void write(Phase13GA2StructuralIntegratedAudit.AuditRun audit, Path output) throws IOException {
        Files.createDirectories(output);
        ObjectMapper mapper = new ObjectMapper()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        writeJson(mapper, output.resolve("phase13g-a-v2-structural-integrated-audit-summary.json"), audit.summary());
        writeJson(mapper, output.resolve("phase13g-a-v2-case-schedule.json"), schedule(audit));
        writeJson(mapper, output.resolve("phase13g-a-v2-synthetic-contexts.json"), contexts(audit));
        writeJson(mapper, output.resolve("phase13g-a-v2-integration-schedule.json"), integrationSchedule(audit));
        writeJson(mapper, output.resolve("phase13g-a-v2-review-details.json"), reviewDetails(audit));
        Files.writeString(output.resolve("phase13g-a-v2-game1-draft-distribution.csv"), gameOneCsv(audit), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("phase13g-a-v2-candidate-coverage.csv"), candidateCoverageCsv(audit), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("phase13g-a-v2-fearless-series.csv"), fearlessCsv(audit), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("phase13g-a-v2-component-distribution.csv"), componentCsv(audit), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("phase13g-a-v2-structural-integrated-audit.md"), markdown(mapper, audit), StandardCharsets.UTF_8);
        writeShaManifest(output);
    }

    private static void writeJson(ObjectMapper mapper, Path path, Object value) throws IOException {
        mapper.writeValue(path.toFile(), value);
    }

    private static Map<String, Object> schedule(Phase13GA2StructuralIntegratedAudit.AuditRun audit) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("scheduleVersion", Phase13GA2AuditSchedule.SCHEDULE_VERSION);
        value.put("scheduleHash", audit.schedule().scheduleHash());
        value.put("permutation", audit.schedule().permutation());
        value.put("unorderedPairs", audit.schedule().unorderedPairs().stream().map(pair -> Map.of(
                "pairId", pair.pairId(), "firstContextId", pair.firstContextId(),
                "secondContextId", pair.secondContextId())).toList());
        value.put("gameOneCases", audit.schedule().gameOneCases().stream().map(caseValue -> Map.of(
                "caseId", caseValue.caseId(), "pairId", caseValue.pairId(),
                "blueContextId", caseValue.blueContextId(), "redContextId", caseValue.redContextId(),
                "orientation", caseValue.orientation())).toList());
        value.put("fearlessSeries", audit.schedule().fearlessSeries().stream().map(caseValue -> Map.of(
                "seriesId", caseValue.seriesId(), "blueContextId", caseValue.blueContextId(),
                "redContextId", caseValue.redContextId())).toList());
        value.put("controlledProbes", audit.schedule().controlledProbes().stream().map(probe -> Map.of(
                "probeId", probe.probeId(), "blueContextId", probe.blueContextId(),
                "redContextId", probe.redContextId())).toList());
        value.put("integrationSeeds", Phase13GA2StructuralIntegratedAudit.INTEGRATION_SEEDS);
        value.put("gameOneReplayCases", Phase13GA2StructuralIntegratedAudit.GAME_ONE_REPLAY_CASES);
        value.put("seriesReplayCases", Phase13GA2StructuralIntegratedAudit.SERIES_REPLAY_CASES);
        return value;
    }

    private static List<Map<String, Object>> contexts(Phase13GA2StructuralIntegratedAudit.AuditRun audit) {
        return audit.syntheticContexts().stream().sorted(Comparator.comparing(
                Phase13GASyntheticContextFactory.SyntheticContext::id)).map(context -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", context.id());
            value.put("kind", context.kind());
            value.put("algorithm", Phase13GASyntheticContextFactory.ALGORITHM_VERSION);
            value.put("roleKeyCount", context.proficiencyByRole().size());
            value.put("proficiency", context.proficiencyByRole().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(ChampionRoleKey::stableId)))
                    .collect(Collectors.toMap(entry -> entry.getKey().stableId(), Map.Entry::getValue,
                            (a, b) -> a, TreeMap::new)));
            return value;
        }).toList();
    }

    private static String gameOneCsv(Phase13GA2StructuralIntegratedAudit.AuditRun audit) {
        var distribution = audit.gameOneDistribution();
        int cases = ((Number) distribution.metrics().getOrDefault("caseCount", 0)).intValue();
        int totalPicks = ((Number) distribution.metrics().getOrDefault("totalPickOccurrences", 0)).intValue();
        int totalBans = ((Number) distribution.metrics().getOrDefault("totalBanOccurrences", 0)).intValue();
        StringBuilder out = new StringBuilder("championId,pickOccurrences,banOccurrences,pickOrBanPresence,pickRate,banRate,roleAssignments,roleAssignmentKeys\n");
        for (ChampionDefinition champion : audit.resources().champions().catalog().all().stream()
                .sorted(Comparator.comparing(value -> value.id().value())).toList()) {
            String id = champion.id().value();
            int pick = distribution.pickOccurrences().getOrDefault(id, 0);
            int ban = distribution.banOccurrences().getOrDefault(id, 0);
            int presence = distribution.pickOrBanPresence().getOrDefault(id, 0);
            Map<String, Integer> role = distribution.roleAssignmentOccurrences().entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(id + ":"))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, TreeMap::new));
            int assignments = role.values().stream().mapToInt(Integer::intValue).sum();
            out.append(csv(id)).append(',').append(pick).append(',').append(ban).append(',').append(presence).append(',')
                    .append(decimal(totalPicks == 0 ? 0 : pick / (double) totalPicks)).append(',')
                    .append(decimal(totalBans == 0 ? 0 : ban / (double) totalBans)).append(',')
                    .append(assignments).append(',').append(csv(role.entrySet().stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("|")))).append('\n');
        }
        return out.toString();
    }

    private static String candidateCoverageCsv(Phase13GA2StructuralIntegratedAudit.AuditRun audit) {
        StringBuilder out = new StringBuilder("championId,pickOccurrences,banOccurrences,pickOrBanPresence,candidateAppearanceCount,pickCandidateAppearanceCount,banCandidateAppearanceCount,candidateCasePresence,highProficiencyContextCount,highProficiencyTeamSlotCount,roleAssignmentOccurrences,roleAssignmentKeys,candidateAppearanceRate,selectedFromCandidateRate\n");
        audit.candidateCoverage().stream()
                .sorted(Comparator.comparing(row -> String.valueOf(row.get("championId"))))
                .forEach(row -> {
                    out.append(csv(String.valueOf(row.get("championId")))).append(",")
                            .append(field(row, "pickOccurrences")).append(",")
                            .append(field(row, "banOccurrences")).append(",")
                            .append(field(row, "pickOrBanPresence")).append(",")
                            .append(field(row, "candidateAppearanceCount")).append(",")
                            .append(field(row, "pickCandidateAppearanceCount")).append(",")
                            .append(field(row, "banCandidateAppearanceCount")).append(",")
                            .append(field(row, "candidateCasePresence")).append(",")
                            .append(field(row, "highProficiencyContextCount")).append(",")
                            .append(field(row, "highProficiencyTeamSlotCount")).append(",")
                            .append(field(row, "roleAssignmentOccurrences")).append(",")
                            .append(csv(String.valueOf(row.get("roleAssignmentKeys")))).append(",")
                            .append(field(row, "candidateAppearanceRate")).append(",")
                            .append(field(row, "selectedFromCandidateRate")).append("\n");
                });
        return out.toString();
    }

    private static Object field(Map<String, Object> row, String key) {
        return row.getOrDefault(key, "");
    }

    private static String fearlessCsv(Phase13GA2StructuralIntegratedAudit.AuditRun audit) {
        StringBuilder out = new StringBuilder("seriesId,gameNumber,blueContextId,redContextId,complete,priorExclusionCount,newPickCount,uniquePickCount,bannedCount,minRawLegalActionCandidateCount,avgRawLegalActionCandidateCount,minGeneratedShortlistCount,avgGeneratedShortlistCount,availableChampionCount,availableLegalRoleKeyCount,engineDraftMillis,validationMillis,totalAuditCaseMillis,violations\n");
        for (var series : audit.fearlessSeries().stream().sorted(Comparator.comparing(
                Phase13GA2StructuralIntegratedAudit.FearlessSeriesAudit::seriesId)).toList()) {
            for (int index = 0; index < series.games().size(); index++) {
                var game = series.games().get(index);
                int prior = game.result() == null ? 0 : game.result().hardFearlessExclusions().size();
                int picks = game.result() == null ? 0 : game.result().bluePicks().size() + game.result().redPicks().size();
                int unique = game.result() == null ? 0 : (int) java.util.stream.Stream.concat(
                        game.result().bluePicks().stream(), game.result().redPicks().stream()).distinct().count();
                int banned = game.result() == null ? 0 : game.result().blueBans().size() + game.result().redBans().size();
                List<Phase13GA2StructuralIntegratedAudit.CandidateTrace> traces = game.candidateTrace();
                int minRaw = traces.stream().mapToInt(Phase13GA2StructuralIntegratedAudit.CandidateTrace::rawLegalActionCandidateCount).min().orElse(0);
                double avgRaw = traces.stream().mapToInt(Phase13GA2StructuralIntegratedAudit.CandidateTrace::rawLegalActionCandidateCount).average().orElse(0.0);
                int minShortlist = traces.stream().mapToInt(Phase13GA2StructuralIntegratedAudit.CandidateTrace::generatedShortlistCount).min().orElse(0);
                double avgShortlist = traces.stream().mapToInt(Phase13GA2StructuralIntegratedAudit.CandidateTrace::generatedShortlistCount).average().orElse(0.0);
                int availableChampions = traces.stream().mapToInt(Phase13GA2StructuralIntegratedAudit.CandidateTrace::rawAvailableChampionCount).findFirst().orElse(0);
                int availableRoles = traces.stream().mapToInt(Phase13GA2StructuralIntegratedAudit.CandidateTrace::rawAvailableLegalRoleKeyCount).findFirst().orElse(0);
                out.append(csv(series.seriesId())).append(",").append(index + 1).append(",")
                        .append(csv(series.blueContextId())).append(",").append(csv(series.redContextId())).append(",")
                        .append(series.complete()).append(",").append(prior).append(",").append(picks).append(",")
                        .append(unique).append(",").append(banned).append(",").append(minRaw).append(",")
                        .append(decimal(avgRaw)).append(",").append(minShortlist).append(",").append(decimal(avgShortlist)).append(",")
                        .append(availableChampions).append(",").append(availableRoles).append(",")
                        .append(game.engineDraftMillis()).append(",").append(game.validationMillis()).append(",")
                        .append(game.totalAuditCaseMillis()).append(",")
                        .append(csv(String.join("|", game.violations()))).append("\n");
            }
        }
        return out.toString();
    }

    private static String componentCsv(Phase13GA2StructuralIntegratedAudit.AuditRun audit) {
        StringBuilder out = new StringBuilder("scope,actionType,component,sampleCount,min,max,mean,median,p10,p90,zeroRate,positiveRate,nonFiniteCount\n");
        audit.componentDistribution().stream().sorted(Comparator.comparing(
                Phase13GA2StructuralIntegratedAudit.ComponentDistribution::scope)
                .thenComparing(Phase13GA2StructuralIntegratedAudit.ComponentDistribution::actionType)
                .thenComparing(Phase13GA2StructuralIntegratedAudit.ComponentDistribution::component)).forEach(value ->
                out.append(value.scope()).append(",").append(value.actionType()).append(",").append(value.component()).append(",")
                        .append(value.sampleCount()).append(",").append(decimal(value.min())).append(",").append(decimal(value.max())).append(",")
                        .append(decimal(value.mean())).append(",").append(decimal(value.median())).append(",").append(decimal(value.p10())).append(",")
                        .append(decimal(value.p90())).append(",").append(decimal(value.zeroRate())).append(",")
                        .append(decimal(value.positiveRate())).append(",").append(value.nonFiniteCount()).append("\n"));
        return out.toString();
    }

    private static Map<String, Object> integrationSchedule(Phase13GA2StructuralIntegratedAudit.AuditRun audit) {
        List<Phase13GA2StructuralIntegratedAudit.IntegrationAudit> ordered = audit.integrations().stream()
                .sorted(Comparator.comparing(Phase13GA2StructuralIntegratedAudit.IntegrationAudit::draftId)
                        .thenComparingLong(Phase13GA2StructuralIntegratedAudit.IntegrationAudit::seed)).toList();
        Map<String, Phase13GA2StructuralIntegratedAudit.IntegrationAudit> unique = ordered.stream()
                .collect(Collectors.toMap(Phase13GA2StructuralIntegratedAudit.IntegrationAudit::draftId,
                        value -> value, (left, right) -> left, TreeMap::new));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("integrationDraftCount", unique.size());
        result.put("seeds", Phase13GA2StructuralIntegratedAudit.INTEGRATION_SEEDS);
        result.put("selectedDrafts", unique.values().stream().map(Phase13GA2AuditArtifactWriter::integrationRow).toList());
        result.put("runs", ordered.stream().map(Phase13GA2AuditArtifactWriter::integrationRunRow).toList());
        return result;
    }

    private static Map<String, Object> integrationRow(Phase13GA2StructuralIntegratedAudit.IntegrationAudit value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("draftId", value.draftId());
        row.put("source", value.source());
        row.put("seriesId", value.seriesId());
        row.put("gameNumber", value.gameNumber());
        row.put("blueContextId", value.blueContextId());
        row.put("redContextId", value.redContextId());
        return row;
    }

    private static Map<String, Object> integrationRunRow(Phase13GA2StructuralIntegratedAudit.IntegrationAudit value) {
        Map<String, Object> row = new LinkedHashMap<>(integrationRow(value));
        row.put("seed", value.seed());
        row.put("success", value.success());
        row.put("replayMismatch", value.replayMismatch());
        return row;
    }

    private static Map<String, Object> reviewDetails(Phase13GA2StructuralIntegratedAudit.AuditRun audit) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("details", audit.reviewDetails());
        result.put("infoCodes", audit.infoCodes());
        result.put("reviewCodes", audit.reviewCodes());
        result.put("blockerCodes", audit.blockerCodes());
        return result;
    }

    private static String markdown(ObjectMapper mapper, Phase13GA2StructuralIntegratedAudit.AuditRun audit) throws IOException {
        Map<String, Object> summary = audit.summary();
        StringBuilder out = new StringBuilder();
        out.append("# Phase 13G-A2 Structural Audit Baseline V2\n\n");
        out.append("## A. Resumed partial state\n\n");
        out.append("The V2 schedule, runner, writer, and focused-test artifacts were preserved and completed in place. V1 historical artifacts remain outside this output directory.\n\n");
        out.append("## B. Files and frozen scope\n\n");
        out.append("Test-side audit only; production draft semantics, gameplay, API, frontend, resources, and RNG are frozen.\n\n");
        out.append("## C. V2 schedule\n\n").append(json(mapper, schedule(audit))).append("\n\n");
        out.append("## D. Candidate metrics\n\n").append(json(mapper, Map.of(
                "game1CaseCount", audit.gameOneDrafts().size(),
                "rawLegalCandidateViolations", audit.gameOneDrafts().stream().flatMap(value -> value.violations().stream()).filter(value -> value.startsWith("RAW_LEGAL_BELOW")).count(),
                "shortlistLimit", 12))).append("\n\n");
        out.append("## E. Candidate coverage\n\n").append("Rows: ").append(audit.candidateCoverage().size()).append(".\n\n");
        out.append("## F. Game1 distribution\n\n").append(json(mapper, audit.gameOneDistribution().metrics())).append("\n\n");
        out.append("## G. Hard Fearless\n\n").append(json(mapper, Map.of(
                "seriesCount", audit.fearlessSeries().size(),
                "completedGame5", audit.fearlessSeries().stream().filter(Phase13GA2StructuralIntegratedAudit.FearlessSeriesAudit::complete).count(),
                "laterDraftCount", audit.fearlessSeries().stream().flatMap(value -> value.games().stream().skip(1)).count()))).append("\n\n");
        out.append("## H. Components\n\n").append(json(mapper, audit.componentDistribution())).append("\n\n");
        out.append("## I. End-to-end integration\n\n").append(json(mapper, integrationSchedule(audit))).append("\n\n");
        out.append("## J. Performance\n\n").append(json(mapper, summary.get("latency"))).append("\n\n");
        out.append("## K. Backend regression\n\n");
        out.append("tests=").append(summary.get("backendTests")).append(", failures=").append(summary.get("backendFailures"))
                .append(", errors=").append(summary.get("backendErrors")).append(", skipped=").append(summary.get("backendSkipped")).append(".\n\n");
        out.append("## L. Artifact finalization\n\n");
        out.append("Output directory: `build/reports/phase13g-a-v2`; SHA-256 manifest is generated after the final artifact writes.\n\n");
        out.append("## M. Reviews\n\n").append(json(mapper, audit.reviewDetails())).append("\n\n");
        out.append("## N. Blockers\n\n").append(json(mapper, audit.blockerCodes())).append("\n\n");
        out.append("## O. Final verdict\n\n`").append(summary.get("verdict")).append("`\n\n");
        out.append("## P. Next phase\n\n`").append(summary.get("nextPhase")).append("`\n");
        return out.toString();
    }

    private static String json(ObjectMapper mapper, Object value) throws IOException {
        return "```json\n" + mapper.writeValueAsString(value) + "\n```";
    }

    public static List<String> finalArtifactNames() {
        return List.of(
                "phase13g-a-v2-structural-integrated-audit-summary.json",
                "phase13g-a-v2-structural-integrated-audit.md",
                "phase13g-a-v2-case-schedule.json",
                "phase13g-a-v2-synthetic-contexts.json",
                "phase13g-a-v2-game1-draft-distribution.csv",
                "phase13g-a-v2-candidate-coverage.csv",
                "phase13g-a-v2-fearless-series.csv",
                "phase13g-a-v2-component-distribution.csv",
                "phase13g-a-v2-integration-schedule.json",
                "phase13g-a-v2-review-details.json");
    }

    public static void writeShaManifest(Path output) throws IOException {
        Files.createDirectories(output);
        StringBuilder out = new StringBuilder();
        for (String name : finalArtifactNames()) {
            out.append(hash(Files.readAllBytes(output.resolve(name)))).append("  ").append(name).append("\n");
        }
        Files.writeString(output.resolve("phase13g-a-v2-SHA256SUMS.txt"), out.toString(), StandardCharsets.UTF_8);
    }

    public static boolean verifyShaManifest(Path output) throws IOException {
        Path manifest = output.resolve("phase13g-a-v2-SHA256SUMS.txt");
        if (!Files.isRegularFile(manifest)) throw new IOException("Missing V2 SHA manifest: " + manifest);
        List<String> expectedNames = finalArtifactNames();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            String[] parts = line.split("  ", 2);
            if (parts.length != 2 || !expectedNames.contains(parts[1]) || !seen.add(parts[1])) {
                throw new IOException("Invalid V2 SHA manifest line: " + line);
            }
            String actual = hash(Files.readAllBytes(output.resolve(parts[1])));
            if (!actual.equals(parts[0])) throw new IOException("V2 SHA mismatch: " + parts[1]);
        }
        if (seen.size() != expectedNames.size()) throw new IOException("V2 SHA manifest is incomplete");
        return true;
    }

    private static String csv(String value) {
        if (value == null) return "";
        return value.contains(",") || value.contains("\"") || value.contains("\n")
                ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    private static String decimal(double value) { return Double.toString(value); }

    private static String hash(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
