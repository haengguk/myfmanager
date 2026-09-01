package com.lolfm.league;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LeagueAutomatedSeriesRunnerCrossJvmDeterminismTest {
    @TempDir Path temp;

    @Test
    void twoFreshJvmsProduceByteIdenticalCanonicalFixtureReceipt() throws Exception {
        Path first = temp.resolve("jvm-a.receipt");
        Path second = temp.resolve("jvm-b.receipt");

        var firstRun = LeagueAutomatedSeriesRunnerCrossJvmProbe.launchFreshJvm(first);
        var secondRun = LeagueAutomatedSeriesRunnerCrossJvmProbe.launchFreshJvm(second);

        assertThat(firstRun.exitCode()).as(firstRun.log()).isZero();
        assertThat(secondRun.exitCode()).as(secondRun.log()).isZero();
        assertThat(Files.readAllBytes(first)).isEqualTo(Files.readAllBytes(second));
        String canonical = Files.readString(first);
        assertThat(canonical)
                .contains("schemaVersion=" + LeagueFixtureCompletionReceiptV2.SCHEMA)
                .contains("canonicalHashAlgorithm="
                        + LeagueFixtureCompletionReceiptV2.HASH_ALGORITHM)
                .contains("playerSeriesBindingHash=NONE")
                .contains("runtimeProfileId=PRODUCTION_MATCHUP_COMPOSITION_V1")
                .contains("engineImplementationVersion=MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9")
                .contains("canonicalFixtureReceiptHash=");
    }
}
