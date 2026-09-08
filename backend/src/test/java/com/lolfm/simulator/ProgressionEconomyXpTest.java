package com.lolfm.simulator;import static org.assertj.core.api.Assertions.*;import com.lolfm.domain.Position;import org.junit.jupiter.api.Test;
class ProgressionEconomyXpTest{private GameState state(){return LateGameTestSupport.state();}
@Test void topMidAndJungleReceiveConfiguredXp(){GameState s=state();s.advanceTimeSeconds(10);new ProgressionEconomyResolver().resolve(s,10);assertThat(s.getBlueTeamState().playerAt(Position.TOP).getProgressionState().getTotalExperience()).isEqualTo(70);assertThat(s.getBlueTeamState().playerAt(Position.MID).getProgressionState().getTotalExperience()).isEqualTo(70);assertThat(s.getBlueTeamState().playerAt(Position.JUNGLE).getProgressionState().getTotalExperience()).isEqualTo(60);}
@Test void deadAndFarmRecoveryPlayersReceiveZero(){GameState s=state();s.advanceTimeSeconds(10);var p=s.getBlueTeamState().playerAt(Position.TOP);p.markDead(10,20);new ProgressionEconomyResolver().resolve(s,10);assertThat(p.getProgressionState().getTotalExperience()).isZero();}
@Test void activityBlockedPlayerReceivesZero(){GameState s=state();s.advanceTimeSeconds(300);var p=s.getBlueTeamState().playerAt(Position.MID);p.beginRoamActivity(Lane.MID,Lane.TOP,300);new ProgressionEconomyResolver().resolve(s,300);assertThat(p.getProgressionState().getTotalExperience()).isZero();}
@Test void jungleBlockStopsAndThenRestoresXp(){GameState s=state();s.advanceTimeSeconds(300);var j=s.getBlueTeamState().playerAt(Position.JUNGLE);s.jungleActionState(TeamSide.BLUE).recordGankAttempt(300,Lane.TOP);new ProgressionEconomyResolver().resolve(s,300);assertThat(j.getProgressionState().getTotalExperience()).isZero();int end=s.jungleActionState(TeamSide.BLUE).getJungleFarmBlockedUntilSeconds();s.advanceTimeSeconds(end-300);new ProgressionEconomyResolver().resolve(s,end);assertThat(j.getProgressionState().getTotalExperience()).isEqualTo(60);}
@Test void featureOffMutatesNoXp(){GameState s=state();s.configureProgression(false,false);s.advanceTimeSeconds(10);new ProgressionEconomyResolver().resolve(s,10);assertThat(s.getBlueTeamState().playerAt(Position.TOP).getProgressionState().getTotalExperience()).isZero();}

@Test void realismSharesOneLaneOpportunityAndSupportMovementLeavesAdcSolo() {
    GameState s=state(); s.configureRealism(true); s.advanceTimeSeconds(480);
    var adc=s.getBlueTeamState().playerAt(Position.ADC);
    var support=s.getBlueTeamState().playerAt(Position.SUPPORT);
    var resolver=new ProgressionEconomyResolver();
    resolver.resolve(s,480);
    assertThat(adc.getProgressionState().getTotalExperience()).isEqualTo(56);
    assertThat(support.getProgressionState().getTotalExperience()).isEqualTo(56);
    resolver.resolve(s,480);
    assertThat(adc.getProgressionState().getTotalExperience()).isEqualTo(56);
    new LaneResourceResolver().rotateSupport(s);
    s.advanceTimeSeconds(10); resolver.resolve(s,490);
    assertThat(adc.getProgressionState().getTotalExperience()).isEqualTo(126);
    assertThat(support.getProgressionState().getTotalExperience()).isEqualTo(56);
    s.advanceTimeSeconds(20); s.expireBaronBuffsIfNeeded(); resolver.resolve(s,510);
    assertThat(support.getProgressionState().getTotalExperience()).isEqualTo(112);
    assertThat(support.getCs()).isZero();
}
@Test void realismLastHitBlockDoesNotRemoveLivingLaneProximityXp() {
    GameState s=state(); s.configureRealism(true); s.advanceTimeSeconds(100);
    var top=s.getBlueTeamState().playerAt(Position.TOP); top.blockFarmUntil(130);
    new ProgressionEconomyResolver().resolve(s,100);
    assertThat(top.getProgressionState().getTotalExperience()).isEqualTo(70);
    assertThat(top.canFarmAt(100)).isFalse();
}
}
