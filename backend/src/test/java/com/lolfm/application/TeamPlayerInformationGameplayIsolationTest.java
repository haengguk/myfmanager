package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR"})
class TeamPlayerInformationGameplayIsolationTest {
    @Autowired RealDraftMatchOrchestrator matches;
    @Autowired TeamPlayerInformationApiV1Service information;

    @Test
    void informationReadsDoNotEnterGameplayProvenanceRandomOrTimelineIdentity() {
        MatchEngineV1Output before = matches.orchestrateV1("GEN", "T1", 73L);

        information.metadata("LCK");
        information.teams("LCK");
        information.team("LCK", "GEN");
        information.players("LCK", "GEN", "MID");
        information.player("LCK", "player-chovy");

        MatchEngineV1Output after = matches.orchestrateV1("GEN", "T1", 73L);
        assertThat(after.outputHash()).isEqualTo(before.outputHash());
        assertThat(after.inputHash()).isEqualTo(before.inputHash());
        assertThat(after.finalDraft()).isEqualTo(before.finalDraft());
        assertThat(after.structuredTimelineHash()).isEqualTo(
                before.structuredTimelineHash());
        assertThat(after.timeline()).isEqualTo(before.timeline());
        assertThat(after.executionProvenance().randomFingerprint()).isEqualTo(
                before.executionProvenance().randomFingerprint());
        assertThat(after.executionProvenance().resourceProvenance()).isEqualTo(
                before.executionProvenance().resourceProvenance());
        assertThat(after.executionProvenance().resourceProvenance().resources())
                .noneMatch(resource -> resource.sha256().equals(
                        "4e4f01fe72f68aca7dcb93afb72b43273201ce0daa7d63613f628597ff41ff19")
                        || resource.role().toLowerCase(java.util.Locale.ROOT)
                        .contains("career"));
    }
}
