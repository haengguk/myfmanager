package com.lolfm.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.TeamPlayerInformationApiV1ResponseMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.PlayerIdentityCatalog;
import com.lolfm.player.PlayerRatingCatalog;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import com.lolfm.application.TeamPlayerInformationApiV1Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Fresh-process serializer probe for catalog hash and byte-stable API projections. */
public final class TeamPlayerInformationCrossJvmProbe {
    static final List<String> PAYLOAD_FILES = List.of(
            "metadata.json", "teams.json", "players.json", "player-chovy.json");

    private TeamPlayerInformationCrossJvmProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected output directory");
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                CatalogConfiguration.class).web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off", "logging.level.root=ERROR")
                .run()) {
            write(context, Path.of(args[0]));
        }
    }

    // Import the same production constructors and Boot Jackson configuration, without
    // component scanning unrelated gameplay, database, worker or web components.
    @TestConfiguration(proxyBeanMethods = false)
    @Import({JacksonAutoConfiguration.class, ChampionCatalog.class,
            PlayerIdentityCatalog.class, PlayerRatingCatalog.class,
            ChampionProficiencyCatalog.class, TeamPlayerInformationCatalog.class,
            TeamPlayerInformationApiV1ResponseMapper.class,
            TeamPlayerInformationApiV1Service.class})
    static class CatalogConfiguration { }

    static void write(ConfigurableApplicationContext context, Path output) throws IOException {
        Files.createDirectories(output);
        ObjectMapper mapper = context.getBean(ObjectMapper.class);
        TeamPlayerInformationApiV1Service service = context.getBean(
                TeamPlayerInformationApiV1Service.class);
        write(output.resolve(PAYLOAD_FILES.get(0)), mapper,
                service.metadata("LCK"));
        write(output.resolve(PAYLOAD_FILES.get(1)), mapper,
                service.teams("LCK"));
        write(output.resolve(PAYLOAD_FILES.get(2)), mapper,
                service.players("LCK", null, null));
        write(output.resolve(PAYLOAD_FILES.get(3)), mapper,
                service.player("LCK", "player-chovy"));
    }

    static ProcessResult launchFreshJvm(Path output) throws IOException, InterruptedException {
        String executable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                        ? "java.exe" : "java").toString();
        Process process = new ProcessBuilder(executable, "-Xms64m", "-Xmx512m",
                "-XX:MaxMetaspaceSize=192m", "-XX:+UseSerialGC", "-cp",
                System.getProperty("java.class.path"),
                TeamPlayerInformationCrossJvmProbe.class.getName(), output.toString())
                .redirectErrorStream(true).start();
        String log = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        return new ProcessResult(exitCode, log);
    }

    private static void write(Path path, ObjectMapper mapper, Object value) throws IOException {
        Files.write(path, mapper.writeValueAsBytes(value));
    }

    record ProcessResult(int exitCode, String log) {
    }
}
