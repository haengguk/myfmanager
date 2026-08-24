package com.lolfm.draft;

import com.lolfm.simulator.TeamSide;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AutoDraftScalabilityScheduleV1 {
    public static final List<Fixture> FIXTURES = List.of(
            new Fixture(1, "BFX", "BRO"),
            new Fixture(2, "BRO", "DK"),
            new Fixture(3, "DK", "DNS"),
            new Fixture(4, "DNS", "GEN"),
            new Fixture(5, "GEN", "HLE"),
            new Fixture(6, "HLE", "KRX"),
            new Fixture(7, "KRX", "KT"),
            new Fixture(8, "KT", "NS"),
            new Fixture(9, "NS", "T1"),
            new Fixture(10, "T1", "BFX"),
            new Fixture(11, "GEN", "T1"),
            new Fixture(12, "HLE", "DK"));
    public static final String HASH_ALGORITHM =
            "SHA256_UTF8_ORDERED_INDEX_BLUE_RED_LINES_TRAILING_NEWLINE_V1";
    public static final String SCHEDULE_HASH = sha256(canonicalSerialization());

    private AutoDraftScalabilityScheduleV1() { }

    public static String canonicalSerialization() {
        StringBuilder value = new StringBuilder();
        FIXTURES.forEach(fixture -> value.append(fixture.index()).append('|')
                .append(fixture.blueTeamCode()).append('|')
                .append(fixture.redTeamCode()).append('\n'));
        return value.toString();
    }

    public static Set<String> teams(TeamSide side) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        FIXTURES.forEach(fixture -> values.add(side == TeamSide.BLUE
                ? fixture.blueTeamCode() : fixture.redTeamCode()));
        return Set.copyOf(values);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public record Fixture(int index, String blueTeamCode, String redTeamCode) {
        public String id() {
            return "%02d_%s_BLUE__%s_RED".formatted(index, blueTeamCode, redTeamCode);
        }
    }
}
