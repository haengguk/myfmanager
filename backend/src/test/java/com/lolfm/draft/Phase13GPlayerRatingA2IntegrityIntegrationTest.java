package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.Position;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Phase13GPlayerRatingA2IntegrityIntegrationTest {
    private static final String LEGAL_ROLE_HASH =
            "18036bba3ec815a732d251e82cdc72d7d6dbed0f9fc3b373b2840da936b72b8e";
    private static final String COMPOSITION_HASH =
            "23d616cab6abea69d5ad783f405b0b4518a14608b0be4eac3d53f669acab6877";
    private static final String SCHEDULE_HASH =
            "0cf6907685b14323fad3323748fd5c2979e14be8a30e1863b5cb33988f8008b0";

    @Test
    void existingSyntheticA2ContextsRemainIndependent() {
        DraftResourceSet resources = DraftResourceSet.loadDefault();
        assertThat(resources.champions().catalog().all()).hasSize(173);
        assertThat(resources.champions().catalog().legalRoleKeys()).hasSize(216);
        assertThat(resources.meta().metaVersion()).isEqualTo("draft-meta-full-173-216-role-2026-08-18-v3");
        assertThat(resources.meta().actualLegalRoleKeyHash()).isEqualTo(LEGAL_ROLE_HASH);
        assertThat(resources.champions().composition().profileHash()).isEqualTo(COMPOSITION_HASH);
        assertThat(new DraftEngine(resources).draft(
                new DraftTeamContext(Map.of()), new DraftTeamContext(Map.of()), new SeriesDraftHistory())
                .decisions()).hasSize(20);
    }

    @Test
    void frozenChampionAndDraftHashesRemainExact() {
        DraftResourceSet resources = DraftResourceSet.loadDefault();
        assertThat(resources.meta().requiredLegalRoleKeyHash()).isEqualTo(LEGAL_ROLE_HASH);
        assertThat(resources.meta().actualLegalRoleKeyHash()).isEqualTo(LEGAL_ROLE_HASH);
        assertThat(resources.champions().composition().profileHash()).isEqualTo(COMPOSITION_HASH);
        assertThat(resources.meta().metaVersion()).isEqualTo("draft-meta-full-173-216-role-2026-08-18-v3");
        assertThat(resources.meta().requiredLegalRoleKeyCount()).isEqualTo(216);
        assertThat(resources.champions().catalog().all()).hasSize(173);
        assertThat(resources.champions().catalog().forPosition(Position.TOP)).hasSize(54);
        assertThat(resources.champions().catalog().forPosition(Position.JUNGLE)).hasSize(51);
        assertThat(resources.champions().catalog().forPosition(Position.MID)).hasSize(45);
        assertThat(resources.champions().catalog().forPosition(Position.ADC)).hasSize(31);
        assertThat(resources.champions().catalog().forPosition(Position.SUPPORT)).hasSize(35);
    }

    @Test
    void frozenA2ScheduleAndSearchBoundsRemainExact() {
        DraftResourceSet resources = DraftResourceSet.loadDefault();
        Phase13GA2AuditSchedule.Schedule schedule = Phase13GA2AuditSchedule.freeze(
                Phase13GASyntheticContextFactory.create(resources));
        assertThat(schedule.scheduleHash()).isEqualTo(SCHEDULE_HASH);
        DraftScoringPolicy policy = DraftScoringPolicy.standard();
        assertThat(policy.candidateLimit()).isEqualTo(12);
        assertThat(policy.structuralRepairSlots()).isEqualTo(4);
        assertThat(policy.searchDepth()).isEqualTo(3);
        assertThat(policy.beamWidth()).isEqualTo(2);
    }

    @Test
    void a2BaselineArtifactIsOptionalButNeverRewrittenByThisPhase() throws Exception {
        Path directory = Path.of("build/reports/phase13g-a-v2");
        Path manifest = directory.resolve("phase13g-a-v2-SHA256SUMS.txt");
        if (!Files.isRegularFile(manifest)) return;
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("  ", 2);
            assertThat(parts).hasSize(2);
            Path artifact = directory.resolve(parts[1]);
            assertThat(Files.isRegularFile(artifact)).isTrue();
            assertThat(sha256(Files.readAllBytes(artifact))).isEqualTo(parts[0]);
        }
    }

    @Test
    void playerRatingsDoNotCreateRuntimeChampionProficiency() {
        assertThat(com.lolfm.player.PlayerRatingResource.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("championProficiency", "championProficiencies");
        assertThat(PlayerRatings.neutral(Position.MID).asMap()).hasSize(12);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
