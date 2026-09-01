package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR"})
class LeagueApiV1TransportIntegrationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;

    @Test
    void largeFixtureViewNegotiatesGzipWithoutChangingJsonContract() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30)).build();
        byte[] create = """
                {"schemaVersion":"AI_LEAGUE_CREATE_REQUEST_V1",
                 "leagueKey":"transport","seasonKey":"season-a",
                 "seasonMode":"SPECTATOR_FULL_AUTO","seasonRootSeed":"73",
                 "clientCommandId":"transport-create"}
                """.getBytes(StandardCharsets.UTF_8);
        HttpRequest createRequest = HttpRequest.newBuilder(uri("/api/v1/leagues"))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(create)).build();
        HttpResponse<byte[]> created = client.send(createRequest,
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode season = mapper.readTree(created.body()).path("season");
        String path = "/api/v1/leagues/" + season.path("leagueId").asText()
                + "/seasons/" + season.path("seasonId").asText() + "/fixtures";

        HttpResponse<byte[]> gzip = get(client, path, "gzip", true);
        assertThat(gzip.statusCode()).isEqualTo(200);
        assertThat(gzip.headers().firstValue("Content-Encoding")).contains("gzip");
        assertThat(String.join(",", gzip.headers().allValues("Vary"))
                .toLowerCase(Locale.ROOT)).contains("accept-encoding");
        assertThat(gzip.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("http://localhost:5173");
        JsonNode gzipJson = mapper.readTree(gunzip(gzip.body()));
        assertThat(gzipJson.path("schemaVersion").asText())
                .isEqualTo("AI_LEAGUE_FIXTURE_LIST_V1");
        assertThat(gzipJson.path("fixtures")).hasSize(90);

        HttpResponse<byte[]> identity = get(client, path, "identity", false);
        assertThat(identity.statusCode()).isEqualTo(200);
        assertThat(identity.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(mapper.readTree(identity.body())).isEqualTo(gzipJson);
    }

    private HttpResponse<byte[]> get(
            HttpClient client, String path, String encoding, boolean cors
    ) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(30)).header("Accept", "application/json")
                .header("Accept-Encoding", encoding);
        if (cors) request.header("Origin", "http://localhost:5173");
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static byte[] gunzip(byte[] body) throws Exception {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return gzip.readAllBytes();
        }
    }
}
