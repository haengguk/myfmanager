package com.lolfm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
class TeamPlayerInformationTransportIntegrationTest {
    private static final String PLAYER_PATH =
            "/api/v1/reference/leagues/LCK/players/player-chovy";

    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired Environment environment;

    @Test
    void largePlayerDetailNegotiatesGzipWithSemanticIdentityParity() throws Exception {
        assertThat(environment.getProperty("server.compression.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("server.compression.min-response-size"))
                .isEqualTo("8KB");
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30)).build();

        HttpResponse<byte[]> identity = get(client, PLAYER_PATH, "identity", false);
        assertThat(identity.statusCode()).isEqualTo(200);
        assertThat(identity.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(identity.body().length).isGreaterThanOrEqualTo(8 * 1024);
        JsonNode identityJson = mapper.readTree(identity.body());
        assertThat(identityJson.path("player").path("summary").path("playerId").asText())
                .isEqualTo("player-chovy");

        HttpResponse<byte[]> gzip = get(client, PLAYER_PATH, "gzip", true);
        assertThat(gzip.statusCode()).isEqualTo(200);
        assertThat(gzip.headers().firstValue("Content-Encoding")).contains("gzip");
        assertThat(String.join(",", gzip.headers().allValues("Vary"))
                .toLowerCase(Locale.ROOT)).contains("accept-encoding");
        assertThat(gzip.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("http://localhost:5173");
        assertThat(mapper.readTree(gunzip(gzip.body()))).isEqualTo(identityJson);

        HttpResponse<byte[]> options = get(client, "/api/v1/real-matches/options",
                "identity", false);
        assertThat(options.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(options.body()).path("teams")).hasSize(10);
    }

    private HttpResponse<byte[]> get(
            HttpClient client,
            String path,
            String encoding,
            boolean cors
    ) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30)).header("Accept", "application/json")
                .header("Accept-Encoding", encoding);
        if (cors) request.header("Origin", "http://localhost:5173");
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static byte[] gunzip(byte[] body) throws Exception {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return gzip.readAllBytes();
        }
    }
}
