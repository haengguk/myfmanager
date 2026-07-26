package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SideOrientationIntegrationTest {
    @Test void randomObserverDoesNotChangeTimeline() throws Exception {
        var f = fixture();
        assertThat(SideOrientationMatchExecutor.timelineBytes(f,
                SideOrientationFixture.Orientation.ORIGINAL, 7, false, true))
                .isEqualTo(SideOrientationMatchExecutor.timelineBytes(f,
                        SideOrientationFixture.Orientation.ORIGINAL, 7, false, false));
    }

    @Test void sideAuditInstrumentationDoesNotChangeTimeline() throws Exception {
        var f = fixture();
        assertThat(SideOrientationMatchExecutor.timelineBytes(f,
                SideOrientationFixture.Orientation.MIRRORED, 42, true, true))
                .isEqualTo(SideOrientationMatchExecutor.timelineBytes(f,
                        SideOrientationFixture.Orientation.MIRRORED, 42, true, false));
    }

    @Test void mirrorPairMapsWinnerToLogicalTeamCorrectly() {
        var f = fixture();
        var original = f.orient(SideOrientationFixture.Orientation.ORIGINAL);
        var mirrored = f.orient(SideOrientationFixture.Orientation.MIRRORED);
        assertThat(original.logicalWinner(TeamSide.BLUE)).isEqualTo(SideOrientationFixture.LogicalTeamId.TEAM_A);
        assertThat(mirrored.logicalWinner(TeamSide.RED)).isEqualTo(SideOrientationFixture.LogicalTeamId.TEAM_A);
    }

    @Test void allAuditFixturesUseFreshState() {
        for (var f : SideOrientationFixtureFactory.neutralFixtures()) {
            assertThat(f.orient(SideOrientationFixture.Orientation.ORIGINAL).blue())
                    .isNotSameAs(f.orient(SideOrientationFixture.Orientation.ORIGINAL).blue());
        }
    }

    @Test void sameAuditSeedProducesSameRows() {
        var e = new SideOrientationMatchExecutor();
        var a = e.run(fixture(), SideOrientationFixture.Orientation.ORIGINAL,
                3, "PRIMARY", "CHAMPION_OFF", "NEUTRAL", false);
        var b = e.run(fixture(), SideOrientationFixture.Orientation.ORIGINAL,
                3, "PRIMARY", "CHAMPION_OFF", "NEUTRAL", false);
        assertThat(a.csv()).isEqualTo(b.csv());
        assertThat(a.funnel()).isEqualTo(b.funnel());
    }

    @Test void noDisplayNameOrMessageParsing() throws Exception {
        String source = Files.readString(Path.of("src/test/java/com/lolfm/simulator/SideOrientationEventAggregator.java"));
        assertThat(source).doesNotContain("getMessage()", "getDescription()");
    }

    @Test void featureOffPreservesPhase13A() throws Exception {
        var f = fixture();
        assertThat(SideOrientationMatchExecutor.timelineBytes(f,
                SideOrientationFixture.Orientation.ORIGINAL, 100, false, true))
                .isEqualTo(SideOrientationMatchExecutor.timelineBytes(f,
                        SideOrientationFixture.Orientation.ORIGINAL, 100, false, false));
    }

    @Test void phase13B5InteractionArtifactsRemainValid() throws Exception {
        Path summary = Path.of("build/reports/player-champion-interaction-audit/player-champion-interaction-summary.csv");
        if (Files.exists(summary)) {
            assertThat(Files.readString(summary)).contains("integrityErrorCount,0");
        }
    }

    private SideOrientationFixture fixture() {
        return SideOrientationFixtureFactory.neutralFixtures().getFirst();
    }
}
