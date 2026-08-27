package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MatchEngineV1CrossJvmDeterminismTest {
    @TempDir Path temp;

    @Test
    void twoFreshJvmsProduceByteIdenticalOutputSummaryHashesAndManifest() throws Exception {
        Path first = temp.resolve("jvm-a");
        Path second = temp.resolve("jvm-b");

        MatchEngineV1CrossJvmProbe.ProcessResult firstRun =
                MatchEngineV1CrossJvmProbe.launchFreshJvm(first);
        MatchEngineV1CrossJvmProbe.ProcessResult secondRun =
                MatchEngineV1CrossJvmProbe.launchFreshJvm(second);

        assertThat(firstRun.exitCode()).as(firstRun.log()).isZero();
        assertThat(secondRun.exitCode()).as(secondRun.log()).isZero();
        for (String file : MatchEngineV1CrossJvmProbe.PAYLOAD_FILES) {
            assertThat(Files.readAllBytes(first.resolve(file)))
                    .as(file).isEqualTo(Files.readAllBytes(second.resolve(file)));
        }
        assertThat(Files.readAllBytes(first.resolve(MatchEngineV1CrossJvmProbe.MANIFEST)))
                .isEqualTo(Files.readAllBytes(
                        second.resolve(MatchEngineV1CrossJvmProbe.MANIFEST)));
        assertThat(Files.readString(first.resolve(
                MatchEngineV1CrossJvmProbe.PAYLOAD_FILES.get(2))))
                .contains("\"runtimeProfileId\":\"PRODUCTION_MATCHUP_COMPOSITION_V1\"")
                .contains("\"configurationHash\":\"caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d\"")
                .contains("\"replayProvenanceHashAlgorithm\":\""
                        + SimulationProvenanceService
                        .MATCH_ENGINE_V1_REPLAY_PROVENANCE_HASH_ALGORITHM + "\"")
                .contains("\"outputHash\":");
    }
}
