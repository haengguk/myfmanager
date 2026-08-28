package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SeriesIdentityTest {
    @Test
    void bo3AndBo5ContractsAreClosedAndExact() {
        assertThat(SeriesFormat.BO3.winsRequired()).isEqualTo(2);
        assertThat(SeriesFormat.BO3.maximumGames()).isEqualTo(3);
        assertThat(SeriesFormat.BO5.winsRequired()).isEqualTo(3);
        assertThat(SeriesFormat.BO5.maximumGames()).isEqualTo(5);
    }

    @Test
    void gameSeedUsesFirstEightSha256BytesAsSignedBigEndianLong() {
        long seed = SeriesIdentity.deriveGameSeed(
                "series_" + "a".repeat(64), "73", 1, "GEN", "T1", "GEN",
                "0".repeat(64));

        assertThat(seed).isEqualTo(-4890914805524307556L);
        assertThat(SeriesIdentity.GAME_SEED_ALGORITHM).isEqualTo(
                "SERIES_GAME_SEED_SHA256_FIRST_8_BYTES_BIG_ENDIAN_SIGNED_LONG_V1");
        assertThat(SeriesIdentity.deriveGameSeed(
                "series_" + "a".repeat(64), "73", 2, "T1", "GEN", "GEN",
                "0".repeat(64))).isNotEqualTo(seed);
    }

    @Test
    void seriesAndGameIdsAreDeterministicAndDomainSeparated() {
        String canonical = "createSchema=SERIES_CREATE_COMMAND_V1\ncommand=x\n";
        String first = SeriesIdentity.seriesId(canonical);
        assertThat(SeriesIdentity.seriesId(canonical)).isEqualTo(first)
                .matches("series_[0-9a-f]{64}");
        assertThat(SeriesIdentity.gameId(first, 1)).isNotEqualTo(
                SeriesIdentity.gameId(first, 2));
    }
}
