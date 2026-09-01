package com.lolfm.league;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LeaguePlayerSeriesHandoffCrossJvmDeterminismTest {
    @TempDir Path temp;

    @Test
    void twoFreshJvmsProduceExactBindingAndPlayerFixtureReceipt() throws Exception {
        Path first = temp.resolve("player-jvm-a.receipt");
        Path second = temp.resolve("player-jvm-b.receipt");

        var firstRun = LeaguePlayerSeriesHandoffCrossJvmProbe.launchFreshJvm(first);
        var secondRun = LeaguePlayerSeriesHandoffCrossJvmProbe.launchFreshJvm(second);

        assertThat(firstRun.exitCode()).as(firstRun.log()).isZero();
        assertThat(secondRun.exitCode()).as(secondRun.log()).isZero();
        assertThat(Files.readAllBytes(first)).isEqualTo(Files.readAllBytes(second));
        assertThat(Files.readString(first))
                .contains("schemaVersion=" + LeagueFixtureSeriesBindingV1.SCHEMA)
                .contains("canonicalHashAlgorithm="
                        + LeagueFixtureSeriesBindingV1.HASH_ALGORITHM)
                .contains("executionMode=PLAYER_CONTROLLED")
                .contains("gameSeedAlgorithm=" + LeagueIdentity.GAME_SEED_ALGORITHM)
                .contains("schemaVersion=" + LeagueFixtureCompletionReceiptV2.SCHEMA)
                .contains("playerSeriesBindingHash=")
                .contains("draftAuthorityExecutionMode=PLAYER_CONTROLLED")
                .contains("runtimeProfileId=PRODUCTION_MATCHUP_COMPOSITION_V1")
                .contains("engineImplementationVersion="
                        + "MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9");
    }
}
