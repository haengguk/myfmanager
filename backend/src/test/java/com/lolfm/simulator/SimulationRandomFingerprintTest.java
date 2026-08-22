package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;

class SimulationRandomFingerprintTest {
    @Test
    void observerDelegatesTheExactJavaRandomSequenceAndRecordsInternalDraws() {
        long seed = 20260822L;
        Random expected = new Random(seed);
        SideOrientationRandomTraceObserver observed = new SideOrientationRandomTraceObserver(
                seed, "ORIENTATION", "BLUE_LOGICAL", "RED_LOGICAL", true);
        observed.context(SideOrientationRandomTraceObserver.Source.ECONOMY, TeamSide.BLUE, 10);

        assertThat(observed.nextInt()).isEqualTo(expected.nextInt());
        assertThat(observed.nextInt(173)).isEqualTo(expected.nextInt(173));
        assertThat(observed.nextLong()).isEqualTo(expected.nextLong());
        assertThat(observed.nextBoolean()).isEqualTo(expected.nextBoolean());
        assertThat(observed.nextDouble()).isEqualTo(expected.nextDouble());

        SimulationRandomFingerprint fingerprint = observed.fingerprint();
        assertThat(fingerprint.randomDrawCount()).isEqualTo(observed.trace().size()).isPositive();
        assertThat(fingerprint.randomTraceHash()).matches("[0-9a-f]{64}");
    }

    @Test
    void traceCaptureAndPresentationLabelsDoNotChangeTheGameplayFingerprint() {
        SideOrientationRandomTraceObserver captured = new SideOrientationRandomTraceObserver(
                73L, "ORIGINAL", "GEN", "T1", true);
        SideOrientationRandomTraceObserver uncaptured = new SideOrientationRandomTraceObserver(
                73L, "RENAMED", "BLUE_PRESENTATION", "RED_PRESENTATION", false);

        for (SideOrientationRandomTraceObserver random : new SideOrientationRandomTraceObserver[]{
                captured, uncaptured}) {
            random.context(SideOrientationRandomTraceObserver.Source.JUNGLE_GANK,
                    TeamSide.RED, 180);
            random.nextDouble();
            random.nextInt(5);
            random.context(SideOrientationRandomTraceObserver.Source.OBJECTIVE_FIGHT,
                    null, 360);
            random.nextBoolean();
        }

        assertThat(uncaptured.fingerprint()).isEqualTo(captured.fingerprint());
        assertThat(captured.trace()).isNotEmpty();
        assertThat(uncaptured.trace()).isEmpty();
    }
}
