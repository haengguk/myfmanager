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
        String canonical = Files.readString(first);
        String replay = Files.readString(second);
        assertThat(replay).as(firstDifference(canonical, replay)).isEqualTo(canonical);
        assertThat(Files.readAllBytes(second)).isEqualTo(Files.readAllBytes(first));
        assertThat(canonical)
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

    private static String firstDifference(String expected, String actual) {
        int common = 0;
        int commonLength = Math.min(expected.length(), actual.length());
        while (common < commonLength && expected.charAt(common) == actual.charAt(common)) {
            common++;
        }
        if (common < commonLength && expected.indexOf('\n') < 0) {
            int from = Math.max(0, common - 160);
            int expectedTo = Math.min(expected.length(), common + 240);
            int actualTo = Math.min(actual.length(), common + 240);
            return "first different character " + common + ": expected <"
                    + expected.substring(from, expectedTo) + "> but was <"
                    + actual.substring(from, actualTo) + ">";
        }
        String[] expectedLines = expected.split("\\n", -1);
        String[] actualLines = actual.split("\\n", -1);
        int length = Math.min(expectedLines.length, actualLines.length);
        for (int index = 0; index < length; index++) {
            if (!expectedLines[index].equals(actualLines[index])) {
                int character = 0;
                int lineLength = Math.min(expectedLines[index].length(),
                        actualLines[index].length());
                while (character < lineLength && expectedLines[index].charAt(character)
                        == actualLines[index].charAt(character)) {
                    character++;
                }
                int from = Math.max(0, character - 160);
                int expectedTo = Math.min(expectedLines[index].length(), character + 240);
                int actualTo = Math.min(actualLines[index].length(), character + 240);
                return "first different line " + (index + 1) + " character "
                        + character + ": expected <"
                        + expectedLines[index].substring(from, expectedTo) + "> but was <"
                        + actualLines[index].substring(from, actualTo) + ">";
            }
        }
        return "different line counts: expected " + expectedLines.length
                + " but was " + actualLines.length;
    }
}
