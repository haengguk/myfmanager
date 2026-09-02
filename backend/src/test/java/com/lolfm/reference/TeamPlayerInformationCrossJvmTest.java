package com.lolfm.reference;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeamPlayerInformationCrossJvmTest {
    @TempDir Path temp;

    @Test
    void twoFreshJvmsProduceEqualCatalogHashAndApiBytes() throws Exception {
        Path first = temp.resolve("jvm-a");
        Path second = temp.resolve("jvm-b");
        TeamPlayerInformationCrossJvmProbe.ProcessResult firstRun =
                TeamPlayerInformationCrossJvmProbe.launchFreshJvm(first);
        TeamPlayerInformationCrossJvmProbe.ProcessResult secondRun =
                TeamPlayerInformationCrossJvmProbe.launchFreshJvm(second);

        assertThat(firstRun.exitCode()).as(firstRun.log()).isZero();
        assertThat(secondRun.exitCode()).as(secondRun.log()).isZero();
        for (String file : TeamPlayerInformationCrossJvmProbe.PAYLOAD_FILES) {
            assertThat(Files.readAllBytes(first.resolve(file))).as(file)
                    .isEqualTo(Files.readAllBytes(second.resolve(file)));
        }
        JsonNode metadata = new ObjectMapper().readTree(
                Files.readAllBytes(first.resolve("metadata.json")));
        assertThat(metadata.path("catalog").path("catalogHash").asText())
                .matches("[0-9a-f]{64}");
        assertThat(metadata.path("counts").path("teams").asInt()).isEqualTo(10);
        assertThat(metadata.path("counts").path("players").asInt()).isEqualTo(50);
    }
}
