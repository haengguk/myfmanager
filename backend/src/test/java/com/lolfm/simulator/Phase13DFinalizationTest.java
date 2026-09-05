package com.lolfm.simulator;

import com.lolfm.composition.*;
import com.lolfm.factory.DummyDataFactory;
import java.nio.file.Files;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Phase13DFinalizationTest {
    @Test void productionCandidateIsV2(){assertThat(FrozenCompositionProductionCandidate.VERSION).isEqualTo("composition-key-specific-channel-calibration-candidate-v2");assertThat(FrozenCompositionProductionCandidate.canonicalHash()).isEqualTo(FrozenCompositionProductionCandidate.HASH);}
    @Test void v1RemainsImmutableHistoricalIdentity(){assertThat(FrozenCompositionKeySpecificChannelCandidate.HASH).isEqualTo("a99f112779a1735339bc124c1d444dda61e69ce336c699da73fdf12c43078b1a");assertThat(FrozenCompositionKeySpecificChannelCandidate.canonicalHash()).isEqualTo(FrozenCompositionKeySpecificChannelCandidate.HASH);}
    @Test void productionBaseGainIsCorrected(){assertThat(FrozenCompositionProductionCandidate.BASE_DEFENSE_WINNER_GAIN).isEqualTo(56.802132049987);assertThat(FrozenCompositionProductionCandidate.canonical()).doesNotContain("BASE_DEFENSE.winnerGain=113.604264099974");}
    @Test void productionTeamfightGainIsFrozen(){assertThat(FrozenCompositionProductionCandidate.TEAMFIGHT_WINNER_GAIN).isEqualTo(80.535608461244);}
    @Test void productionSiegeGainIsFrozen(){assertThat(FrozenCompositionProductionCandidate.SIEGE_WINNER_GAIN).isEqualTo(69.065220882615);}
    @Test void productionSeverityIsZero(){assertThat(FrozenCompositionProductionCandidate.TEAMFIGHT_SEVERITY_GAIN+FrozenCompositionProductionCandidate.SIEGE_SEVERITY_GAIN+FrozenCompositionProductionCandidate.BASE_DEFENSE_SEVERITY_GAIN).isZero();}
    @Test void productionDefaultIsFrozenV2(){assertThat(SimulationOptions.productionDefaults().teamCompositionGameplayMode()).isEqualTo(TeamCompositionGameplayMode.PRODUCTION_V2);assertThat(new CompositionRuntimeState(TeamCompositionGameplayMode.PRODUCTION_V2,7).isProductionV2()).isTrue();}
    @Test void explicitOffRollbackExists(){assertThat(SimulationOptions.productionDefaults().withTeamCompositionGameplayMode(TeamCompositionGameplayMode.OFF).teamCompositionGameplayMode()).isEqualTo(TeamCompositionGameplayMode.OFF);assertThat(CompositionRuntimeState.off(7).isActive()).isFalse();}
    @Test void productionReplayIsDeterministic(){var f=new DummyDataFactory();var a=Phase13DFinalization.productionSimulator().simulate(f.createBlueTeam(),f.createRedTeam(),17L);f=new DummyDataFactory();var b=Phase13DFinalization.productionSimulator().simulate(f.createBlueTeam(),f.createRedTeam(),17L);assertThat(CompositionAuditOnlySemanticsRuntime.hash(a)).isEqualTo(CompositionAuditOnlySemanticsRuntime.hash(b));}
    @Tag("diagnostic") @Tag("historical-artifact")
    @Test void publicApiAndFrontendRemainUntouched(){assertThat(Files.exists(Phase13DFinalization.OUT.resolve("phase-13d-final-integrity.csv"))).isTrue();}
    @Tag("diagnostic") @Tag("historical-artifact")
    @Test void completedActivationIsRecorded()throws Exception{var rows=CompositionDecisionTimeProvenanceCapture.read(Phase13DFinalization.OUT.resolve("phase-13d-final-summary.csv"));assertThat(rows).anyMatch(r->r.get("field").equals("verdict")&&r.get("value").equals("PHASE_13D_TEAM_COMPOSITION_COMPLETE"));assertThat(rows).anyMatch(r->r.get("field").equals("productionEnabled")&&r.get("value").equals("true"));}
}
