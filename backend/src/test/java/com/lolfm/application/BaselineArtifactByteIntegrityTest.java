package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BaselineArtifactByteIntegrityTest {
    private static final List<Path> BASELINE_DIRECTORIES = List.of(
            Path.of("baseline", "pre-jungle-runtime-v1"),
            Path.of("baseline", "pre-jungle-runtime-v2"),
            Path.of("baseline", "pre-jungle-tempo-runtime-v1"));

    @Test
    void canonicalWriterPinsCrLfInsteadOfTheHostLineSeparator() throws Exception {
        byte[] bytes = BaselineArtifactJson.write(
                new ObjectMapper(), Map.of("artifact", "baseline"));

        assertThat(new String(bytes, StandardCharsets.UTF_8))
                .isEqualTo("{\r\n  \"artifact\" : \"baseline\"\r\n}");
    }

    @Test
    void trackedBaselineBytesMatchTheirDeclaredChecksums() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        for (Path directory : BASELINE_DIRECTORIES) {
            String[] checksum = Files.readString(
                            directory.resolve("SHA256SUMS.txt"), StandardCharsets.UTF_8)
                    .strip()
                    .split("\\s+");
            assertThat(checksum).as("checksum format for %s", directory).hasSize(2);

            Path artifact = directory.resolve(checksum[1]);
            byte[] bytes = Files.readAllBytes(artifact);
            assertThat(sha256(bytes)).as("raw bytes for %s", artifact)
                    .isEqualTo(checksum[0]);
            String text = new String(bytes, StandardCharsets.UTF_8);
            assertThat(text).as("canonical CRLF for %s", artifact).contains("\r\n");
            assertThat(text.replace("\r\n", ""))
                    .as("no non-canonical line separators for %s", artifact)
                    .doesNotContain("\r", "\n");
            assertThat(BaselineArtifactJson.write(mapper, mapper.readTree(bytes)))
                    .as("canonical generator bytes for %s", artifact)
                    .isEqualTo(bytes);
        }
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
