package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.Position;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DraftMetaCatalogTest {
    private final DraftResourceSet resources = DraftTestSupport.RESOURCES;

    @Test
    void authoritativeV3ExactlyMatchesThe173Champion216RoleCatalog() {
        DraftMetaCatalog meta = resources.meta();
        assertThat(resources.champions().catalog().all()).hasSize(173);
        assertThat(resources.champions().catalog().legalRoleKeys()).hasSize(216);
        assertThat(meta.profiles()).hasSize(216);
        assertThat(meta.profiles().keySet()).isEqualTo(resources.champions().catalog().legalRoleKeys());
        assertThat(meta.metaVersion()).isEqualTo(DraftMetaCatalog.VERSION);
        assertThat(meta.requiredLegalRoleKeyHash()).isEqualTo("18036bba3ec815a732d251e82cdc72d7d6dbed0f9fc3b373b2840da936b72b8e");
        assertThat(meta.actualLegalRoleKeyHash()).isEqualTo(meta.requiredLegalRoleKeyHash());
        assertThat(meta.profiles().values()).allSatisfy(profile -> assertThat(profile.priority()).isBetween(1, 20));
    }

    @Test
    void newRolesHaveExactAuthoredPrioritiesAndAniviaSupportIsIllegal() {
        assertThat(priority("varus", Position.TOP)).isEqualTo(18);
        assertThat(priority("anivia", Position.TOP)).isEqualTo(17);
        assertThat(priority("cassiopeia", Position.ADC)).isEqualTo(9);
        assertThat(priority("taliyah", Position.ADC)).isEqualTo(13);
        assertThat(resources.champions().catalog().supports(key("anivia", Position.SUPPORT))).isFalse();
        assertThat(resources.meta().profiles()).doesNotContainKey(key("anivia", Position.SUPPORT));
    }

    @Test
    void stale212RoleMetadataFailsFastWithoutFallback() {
        String stale = """
                {"metaVersion":"draft-meta-full-173-216-role-2026-08-18-v3",
                 "requiredChampionPoolVersion":"full-173-2026-08-v1",
                 "requiredLegalRoleKeyCount":212,
                 "requiredLegalRoleKeyHash":"18036bba3ec815a732d251e82cdc72d7d6dbed0f9fc3b373b2840da936b72b8e",
                 "legalRoleKeyHashAlgorithm":"SHA256_UTF8_SORTED_CHAMPION_ID_COLON_POSITION_LINES_TRAILING_NEWLINE_V1",
                 "asOfDate":"2026-08-18","priorityScale":"1-20","hardFearlessContext":true,"profiles":[]}
                """;
        assertThatThrownBy(() -> DraftMetaCatalog.load(new ObjectMapper(), resources.champions().catalog(),
                new ByteArrayInputStream(stale.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("count mismatch");
    }

    @Test
    void legalRoleHashContractIncludesSortedLinesAndTrailingNewline() {
        assertThat(DraftMetaCatalog.legalRoleKeyHash(resources.champions().catalog().legalRoleKeys()))
                .isEqualTo("18036bba3ec815a732d251e82cdc72d7d6dbed0f9fc3b373b2840da936b72b8e");
    }

    private int priority(String champion, Position position) { return resources.meta().priority(key(champion, position)); }
    private static ChampionRoleKey key(String champion, Position position) { return new ChampionRoleKey(new ChampionId(champion), position); }
}
