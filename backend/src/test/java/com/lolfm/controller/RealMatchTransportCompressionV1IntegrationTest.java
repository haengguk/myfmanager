package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.dto.RealMatchApiV1Dtos;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR"})
class RealMatchTransportCompressionV1IntegrationTest {
    private static final String FIXED_OUTPUT_HASH =
            "86a8a09be83d20d6ac90a584888237762909f35f107de6ba3bffcafaf7a77b04";

    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired Environment environment;

    @Test
    void negotiatesGzipWithoutChangingTheRealMatchContract() throws Exception {
        assertThat(environment.getProperty("server.compression.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("server.compression.mime-types"))
                .isEqualTo("application/json");
        assertThat(environment.getProperty("server.compression.min-response-size"))
                .isEqualTo("8KB");

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30)).build();
        byte[] request = mapper.writeValueAsBytes(mapper.createObjectNode()
                .put("schemaVersion", RealMatchApiV1Dtos.REQUEST_SCHEMA)
                .put("blueTeamCode", "GEN")
                .put("redTeamCode", "T1")
                .put("seed", "73"));

        HttpResponse<byte[]> gzip = post(client, request, "gzip", true);
        assertThat(gzip.statusCode()).isEqualTo(200);
        assertThat(gzip.headers().firstValue("Content-Type").orElse(""))
                .startsWith("application/json");
        assertThat(gzip.headers().firstValue("Content-Encoding")).contains("gzip");
        assertThat(String.join(",", gzip.headers().allValues("Vary"))
                .toLowerCase(Locale.ROOT)).contains("accept-encoding");
        assertThat(gzip.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("http://localhost:5173");
        assertThat(isGzip(gzip.body())).isTrue();
        byte[] decodedGzip = gunzip(gzip.body());
        assertThat(isGzip(decodedGzip)).isFalse();
        JsonNode gzipJson = mapper.readTree(decodedGzip);
        assertFixedIdentity(gzipJson);

        HttpResponse<byte[]> identity = post(client, request, "identity", false);
        assertThat(identity.statusCode()).isEqualTo(200);
        assertThat(identity.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(isGzip(identity.body())).isFalse();
        JsonNode identityJson = mapper.readTree(identity.body());
        assertThat(identityJson).isEqualTo(gzipJson);

        HttpResponse<byte[]> unspecified = post(client, request, null, false);
        assertThat(unspecified.statusCode()).isEqualTo(200);
        assertThat(unspecified.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(mapper.readTree(unspecified.body())).isEqualTo(identityJson);

        HttpResponse<byte[]> options = get(client, "/api/v1/real-matches/options", "gzip");
        assertThat(options.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(decodedBody(options)).path("schemaVersion").asText())
                .isEqualTo(RealMatchApiV1Dtos.OPTIONS_SCHEMA);
        HttpResponse<byte[]> identityOptions = get(
                client, "/api/v1/real-matches/options", "identity");
        assertThat(identityOptions.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(mapper.readTree(identityOptions.body())).isEqualTo(
                mapper.readTree(decodedBody(options)));

        HttpResponse<byte[]> invalid = post(client, "{}".getBytes(StandardCharsets.UTF_8),
                "gzip", false);
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(mapper.readTree(decodedBody(invalid)).path("schemaVersion").asText())
                .isEqualTo(RealMatchApiV1Dtos.ERROR_SCHEMA);
        HttpResponse<byte[]> identityInvalid = post(client,
                "{}".getBytes(StandardCharsets.UTF_8), "identity", false);
        assertThat(identityInvalid.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(mapper.readTree(identityInvalid.body())).isEqualTo(
                mapper.readTree(decodedBody(invalid)));
    }

    private HttpResponse<byte[]> post(
            HttpClient client, byte[] body, String acceptEncoding, boolean cors
    ) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/real-matches/simulate"))
                .timeout(Duration.ofMinutes(5))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
        if (acceptEncoding != null) request.header("Accept-Encoding", acceptEncoding);
        if (cors) request.header("Origin", "http://localhost:5173");
        return client.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> get(
            HttpClient client, String path, String acceptEncoding
    ) throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(uri(path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Accept-Encoding", acceptEncoding).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static byte[] gunzip(byte[] body) throws Exception {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return gzip.readAllBytes();
        }
    }

    private static byte[] decodedBody(HttpResponse<byte[]> response) throws Exception {
        if (response.headers().firstValue("Content-Encoding")
                .map(value -> value.equalsIgnoreCase("gzip")).orElse(false)) {
            assertThat(isGzip(response.body())).isTrue();
            return gunzip(response.body());
        }
        assertThat(isGzip(response.body())).isFalse();
        return response.body();
    }

    private static boolean isGzip(byte[] body) {
        return body.length >= 2 && (body[0] & 0xff) == 0x1f && (body[1] & 0xff) == 0x8b;
    }

    private static void assertFixedIdentity(JsonNode response) {
        assertThat(response.path("draft").path("decisions")).hasSize(20);
        assertThat(response.path("result").path("winner").asText()).isEqualTo("RED");
        assertThat(response.path("result").path("durationSeconds").asInt()).isEqualTo(1_750);
        assertThat(response.path("timeline").path("events")).hasSize(350);
        assertThat(response.path("timeline").path("snapshots")).hasSize(176);
        assertThat(response.path("integrity").path("outputHash").asText())
                .isEqualTo(FIXED_OUTPUT_HASH);
        assertThat(response.path("integrity").path("replayProvenanceHash").asText())
                .isNotBlank();
        assertThat(response.path("integrity").path("simulatorTimelineHash").asText())
                .isNotBlank();
        assertThat(response.path("integrity").path("structuredTimelineHash").asText())
                .isNotBlank();
        assertThat(response.path("integrity").path("randomFingerprint")
                .path("randomTraceHash").asText()).isNotBlank();
    }
}
