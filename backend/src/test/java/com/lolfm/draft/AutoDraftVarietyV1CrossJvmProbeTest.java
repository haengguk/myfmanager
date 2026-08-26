package com.lolfm.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.application.RealDraftSelectionContextFactory;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.domain.Team;
import com.lolfm.player.LckTeamAssembler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR"})
@Tag("diagnostic")
@Tag("auto-draft-variety-v1-cross-jvm")
class AutoDraftVarietyV1CrossJvmProbeTest {
    @Autowired ObjectMapper mapper;
    @Autowired ChampionCatalog champions;
    @Autowired LckTeamAssembler teams;

    @Test
    void writeCanonicalProductionDraftProbe() throws Exception {
        Team blue = teams.assemble("GEN");
        Team red = teams.assemble("T1");
        SeriesDraftHistory history = new SeriesDraftHistory();
        DraftEngine engine = new DraftEngine(DraftResourceSet.loadDefault(mapper, champions));
        long seed = AutoDraftVarietyV1Schedule.SEEDS.get(4);
        FinalDraftResult result = engine.draft(
                DraftTeamContext.from(blue), DraftTeamContext.from(red), history,
                RealDraftSelectionContextFactory.create(
                        seed, "GEN", blue, "T1", red, 1, history.consumedPicks()));

        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", "AUTO_DRAFT_VARIETY_V1_CROSS_JVM_PROBE_V1");
        value.put("seed", Long.toString(seed));
        value.put("draftSelectionPolicyId", result.draftSelectionPolicyId());
        value.put("draftSelectionPolicyHash", result.draftSelectionPolicyHash());
        value.put("draftSelectionTraceHash", result.selectionTraceHash());
        value.put("draftIdentity", result.draftIdentity());
        value.put("decisions", result.decisions());
        value.put("selectionTraces", result.selectionTraces());
        value.put("assignments", result.matchChampionAssignments().asMap().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(key -> key.stableId())))
                .map(entry -> Map.of(
                        "playerKey", entry.getKey().stableId(),
                        "championId", entry.getValue().championId().value(),
                        "position", entry.getValue().selectedPosition().name())).toList());

        Path output = Path.of(System.getProperty("autoDraftVarietyProbeOutput"));
        Files.createDirectories(output.getParent());
        ObjectMapper canonical = mapper.copy()
                .enable(com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.INDENT_OUTPUT);
        Files.writeString(output, canonical.writeValueAsString(value) + "\n");
    }
}
