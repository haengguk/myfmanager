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

/** Writes the stable, sorted Phase 13G-A audit artifact set. */
public final class Phase13GAAuditArtifactWriter {
    private Phase13GAAuditArtifactWriter() { }

    public static void write(Phase13GAStructuralIntegratedAudit.AuditRun audit, Path output) throws IOException {
        Files.createDirectories(output);
        ObjectMapper mapper = new ObjectMapper()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        writeJson(mapper, output.resolve("phase13g-a-structural-integrated-audit-summary.json"), audit.summary());
        writeJson(mapper, output.resolve("phase13g-a-case-schedule.json"), schedule(audit));
        writeJson(mapper, output.resolve("phase13g-a-synthetic-contexts.json"), contexts(audit));
        Files.writeString(output.resolve("phase13g-a-game1-draft-distribution.csv"), gameOneCsv(audit), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("phase13g-a-fearless-series.csv"), fearlessCsv(audit), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("phase13g-a-component-distribution.csv"), componentCsv(audit), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("phase13g-a-structural-integrated-audit.md"), markdown(mapper, audit), StandardCharsets.UTF_8);
        writeSums(audit, output);
    }

    private static void writeJson(ObjectMapper mapper, Path path, Object value) throws IOException {
        mapper.writeValue(path.toFile(), value);
    }

    private static Map<String, Object> schedule(Phase13GAStructuralIntegratedAudit.AuditRun audit) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("gameOneCases", audit.schedule().gameOneCases().stream().map(caseValue -> Map.of(
                "caseId", caseValue.caseId(), "blueContextId", caseValue.blueContextId(),
                "redContextId", caseValue.redContextId())).toList());
        value.put("fearlessSeries", audit.schedule().fearlessSeries().stream().map(caseValue -> Map.of(
                "seriesId", caseValue.seriesId(), "blueContextId", caseValue.blueContextId(),
                "redContextId", caseValue.redContextId())).toList());
        value.put("integrationSeeds", Phase13GAStructuralIntegratedAudit.INTEGRATION_SEEDS);
        value.put("gameOneReplayCases", Phase13GAStructuralIntegratedAudit.GAME_ONE_REPLAY_CASES);
        value.put("seriesReplayCases", Phase13GAStructuralIntegratedAudit.SERIES_REPLAY_CASES);
        return value;
    }

    private static List<Map<String, Object>> contexts(Phase13GAStructuralIntegratedAudit.AuditRun audit) {
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

    private static String gameOneCsv(Phase13GAStructuralIntegratedAudit.AuditRun audit) {
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

    private static String fearlessCsv(Phase13GAStructuralIntegratedAudit.AuditRun audit) {
        StringBuilder out = new StringBuilder("seriesId,gameNumber,blueContextId,redContextId,complete,priorExclusionCount,newPickCount,uniquePickCount,bannedCount,minCandidatePool,avgCandidatePool,availableChampionCount,availableRoleKeyCount,violations\n");
        for (var series : audit.fearlessSeries().stream().sorted(Comparator.comparing(
                Phase13GAStructuralIntegratedAudit.FearlessSeriesAudit::seriesId)).toList()) {
            for (int index = 0; index < series.games().size(); index++) {
                var game = series.games().get(index);
                int prior = game.result() == null ? 0 : game.result().hardFearlessExclusions().size();
                int picks = game.result() == null ? 0 : game.result().bluePicks().size() + game.result().redPicks().size();
                int unique = game.result() == null ? 0 : (int) java.util.stream.Stream.concat(
                        game.result().bluePicks().stream(), game.result().redPicks().stream()).distinct().count();
                int banned = game.result() == null ? 0 : game.result().blueBans().size() + game.result().redBans().size();
                int min = game.candidateTrace().stream().mapToInt(value -> value.candidates().size()).min().orElse(0);
                double avg = game.candidateTrace().stream().mapToInt(value -> value.candidates().size()).average().orElse(0.0);
                int availableChampions = game.result() == null ? 0 : audit.resources().champions().catalog().all().size()
                        - game.result().hardFearlessExclusions().size();
                int availableRoles = game.result() == null ? 0 : (int) audit.resources().champions().catalog().legalRoleKeys().stream()
                        .filter(key -> !game.result().hardFearlessExclusions().contains(key.championId())).count();
                out.append(csv(series.seriesId())).append(',').append(index + 1).append(',')
                        .append(csv(series.blueContextId())).append(',').append(csv(series.redContextId())).append(',')
                        .append(series.complete()).append(',').append(prior).append(',').append(picks).append(',')
                        .append(unique).append(',').append(banned).append(',').append(min).append(',').append(decimal(avg)).append(',')
                        .append(availableChampions).append(',').append(availableRoles).append(',')
                        .append(csv(String.join("|", game.violations()))).append('\n');
            }
        }
        return out.toString();
    }

    private static String componentCsv(Phase13GAStructuralIntegratedAudit.AuditRun audit) {
        StringBuilder out = new StringBuilder("actionType,component,sampleCount,min,max,mean,median,p10,p90,zeroRate,positiveRate,nonFiniteCount\n");
        audit.componentDistribution().stream().sorted(Comparator.comparing(
                Phase13GAStructuralIntegratedAudit.ComponentDistribution::actionType)
                .thenComparing(Phase13GAStructuralIntegratedAudit.ComponentDistribution::component)).forEach(value ->
                out.append(value.actionType()).append(',').append(value.component()).append(',').append(value.sampleCount()).append(',')
                        .append(decimal(value.min())).append(',').append(decimal(value.max())).append(',').append(decimal(value.mean())).append(',')
                        .append(decimal(value.median())).append(',').append(decimal(value.p10())).append(',').append(decimal(value.p90())).append(',')
                        .append(decimal(value.zeroRate())).append(',').append(decimal(value.positiveRate())).append(',').append(value.nonFiniteCount()).append('\n'));
        return out.toString();
    }

    private static String markdown(ObjectMapper mapper, Phase13GAStructuralIntegratedAudit.AuditRun audit) throws IOException {
        Map<String, Object> summary = audit.summary();
        StringBuilder out = new StringBuilder();
        out.append("# Phase 13G-A Structural Integrated Audit\n\n");
        out.append("## A. Files and frozen scope\n\n");
        out.append("This artifact is test-side and observes the frozen Phase 13F production path. No player-authored data, API/frontend code, or production tuning is included.\n\n");
        out.append("## B. Static integrity\n\n").append(json(mapper, audit.staticIntegrity())).append("\n\n");
        out.append("## C. Synthetic contexts\n\n");
        out.append("Algorithm: `").append(Phase13GASyntheticContextFactory.ALGORITHM_VERSION).append("`; count: ")
                .append(audit.syntheticContexts().size()).append("; all contexts contain 216 structured role keys.\n\n");
        out.append("## D. Game-1 distribution\n\n").append(json(mapper, audit.gameOneDistribution().metrics())).append("\n\n");
        out.append("## E. Hard Fearless\n\n");
        out.append("Series: ").append(audit.fearlessSeries().size()).append("; completed through Game 5: ")
                .append(audit.fearlessSeries().stream().filter(Phase13GAStructuralIntegratedAudit.FearlessSeriesAudit::complete).count()).append(".\n\n");
        out.append("## F. Component audit\n\n").append(json(mapper, audit.componentDistribution())).append("\n\n");
        out.append("## G. End-to-end integration\n\n").append(json(mapper, audit.integrations())).append("\n\n");
        out.append("## H. Determinism and controlled probes\n\n").append(json(mapper, audit.controlledProbes())).append("\n\n");
        out.append("## I. Performance\n\n").append(json(mapper, summary.get("latency"))).append("\n\n");
        out.append("## J. Review codes\n\n").append(json(mapper, audit.reviewCodes())).append("\n\n");
        out.append("## K. Blockers\n\n").append(json(mapper, audit.blockerCodes())).append("\n\n");
        out.append("## L. Regression handoff\n\n");
        out.append("`backendTests` and related fields remain pending until the required final backend regression is executed after audit artifacts are produced.\n\n");
        out.append("## M. Integrity\n\n").append(json(mapper, summary)).append("\n\n");
        out.append("## N. Final verdict\n\n`" ).append(summary.get("verdict")).append("`\n");
        return out.toString();
    }

    private static String json(ObjectMapper mapper, Object value) throws IOException {
        return "```json\n" + mapper.writeValueAsString(value) + "\n```";
    }

    private static void writeSums(Phase13GAStructuralIntegratedAudit.AuditRun audit, Path output) throws IOException {
        List<String> names = List.of(
                "phase13g-a-case-schedule.json", "phase13g-a-component-distribution.csv",
                "phase13g-a-fearless-series.csv", "phase13g-a-game1-draft-distribution.csv",
                "phase13g-a-structural-integrated-audit-summary.json",
                "phase13g-a-structural-integrated-audit.md", "phase13g-a-synthetic-contexts.json");
        StringBuilder out = new StringBuilder();
        for (String name : names) out.append(hash(Files.readAllBytes(output.resolve(name)))).append("  ").append(name).append('\n');
        Files.writeString(output.resolve("phase13g-a-SHA256SUMS.txt"), out.toString(), StandardCharsets.UTF_8);
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
