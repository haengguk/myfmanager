package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.application.MatchEngineV1Canonicalizer;
import com.lolfm.application.MatchEngineV1Input;
import com.lolfm.application.MatchEngineV1InputFactory;
import com.lolfm.application.RealDraftMatchOrchestrator;
import com.lolfm.domain.Team;
import com.lolfm.draft.DraftEngine;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.FinalDraftResult;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.player.LckTeamAssembler;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR"})
@Tag("diagnostic")
@Tag("auto-draft-cross-jvm-v1")
class AutoDraftCrossJvmProbeV1Test {
    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired LckTeamAssembler teams;
    @Autowired MatchEngineV1InputFactory inputs;
    @Autowired MatchEngineV1Canonicalizer canonicalizer;

    @Test
    void writeRepresentativeProductionIdentity() throws Exception {
        Team blue = teams.assemble("GEN");
        Team red = teams.assemble("T1");
        DraftEngine drafts = field(orchestrator, "drafts", DraftEngine.class);
        FinalDraftResult result = drafts.draftDeterministicBest(DraftTeamContext.from(blue),
                DraftTeamContext.from(red), new SeriesDraftHistory());
        MatchEngineV1Input input = inputs.fromRealDraft(
                "GEN", blue, "T1", red, 73L, 1, Set.of(), result);

        LinkedHashMap<String, Object> identity = new LinkedHashMap<>();
        identity.put("schemaVersion", "AUTO_DRAFT_CROSS_JVM_IDENTITY_V1");
        identity.put("blueTeamCode", "GEN");
        identity.put("redTeamCode", "T1");
        identity.put("draftIdentity", result.draftIdentity());
        identity.put("decisions", result.decisions());
        identity.put("blueBans", result.blueBans());
        identity.put("redBans", result.redBans());
        identity.put("bluePicks", result.bluePicks());
        identity.put("redPicks", result.redPicks());
        identity.put("blueFinalRoles", roles(result.blueFinalRoleAssignments()));
        identity.put("redFinalRoles", roles(result.redFinalRoleAssignments()));
        identity.put("matchAssignments", result.matchChampionAssignments().asMap()
                .entrySet().stream().sorted(Comparator.comparing(
                        entry -> entry.getKey().stableId())).map(entry -> Map.of(
                        "playerKey", entry.getKey().stableId(),
                        "championId", entry.getValue().championId().value(),
                        "position", entry.getValue().selectedPosition().name())).toList());
        identity.put("finalDraftHash", input.finalDraft().finalDraftHash());
        identity.put("finalAssignmentHash", input.finalDraft().finalAssignmentHash());
        identity.put("inputHash", input.inputHash());

        Path output = Path.of(System.getProperty("autoDraftProbeOutput"))
                .toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Files.writeString(output, canonicalizer.canonicalJson(identity) + '\n',
                StandardCharsets.UTF_8);
        assertThat(result.decisions()).hasSize(20);
        assertThat(input.inputHash()).hasSize(64);
    }

    private static <T> T field(Object owner, String name, Class<T> type) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(owner));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static List<Map<String, String>> roles(
            Map<com.lolfm.champion.ChampionId, com.lolfm.domain.Position> values) {
        return values.entrySet().stream().sorted(Comparator.comparing(
                        entry -> entry.getKey().value()))
                .map(entry -> Map.of("championId", entry.getKey().value(),
                        "position", entry.getValue().name())).toList();
    }
}
