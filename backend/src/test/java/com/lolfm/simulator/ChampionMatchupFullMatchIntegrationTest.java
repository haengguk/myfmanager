package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChampionMatchupFullMatchIntegrationTest {
    private final ChampionMatchupFullMatchExecutor executor =
            new ChampionMatchupFullMatchExecutor();

    @Test void neutralOnAndOffProduceExactTimeline() {
        var fixture = SideOrientationFixtureFactory.focused("S0").getFirst();
        var result = executor.runPair(
                fixture, SideOrientationFixture.Orientation.ORIGINAL, "S0", 17);
        assertThat(result.paired().timelineMismatch()).isFalse();
        assertThat(result.paired().snapshotMismatch()).isFalse();
        assertThat(result.paired().randomDrawMismatch()).isFalse();
        assertThat(result.paired().winnerMismatch()).isFalse();
    }

    @Test void neutralMatchupDoesNotAddBlueAdvantage() {
        var fixture = SideOrientationFixtureFactory.focused("S3").get(2);
        for (SideOrientationFixture.Orientation direction :
                SideOrientationFixture.Orientation.values()) {
            var result = executor.runPair(fixture, direction, "S3", 42);
            assertThat(result.paired().winnerMismatch()).isFalse();
            assertThat(result.on().nonZeroMatchupApplications()).isZero();
        }
    }

    @Test void mirrorDoesNotCreateAdditionalSideBias() {
        var fixture = SideOrientationFixtureFactory.focused("S0").getLast();
        var original = executor.runPair(
                fixture, SideOrientationFixture.Orientation.ORIGINAL, "S0", 73);
        var mirrored = executor.runPair(
                fixture, SideOrientationFixture.Orientation.MIRRORED, "S0", 73);
        assertThat(original.paired().anyMismatch()).isFalse();
        assertThat(mirrored.paired().anyMismatch()).isFalse();
    }

    @Test void testOnlyMirrorPreservesLogicalIdentityAndReversesSideEdge() {
        assertThat(new ChampionMatchupMirrorAudit().run())
                .hasSize(5)
                .allMatch(row -> row.logicalIdentityPreserved()
                        && row.sideEdgeReversed()
                        && row.exactZeroStable()
                        && row.directRandomCalls() == 0);
    }
}
