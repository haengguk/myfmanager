package com.lolfm.simulator;

import com.lolfm.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UpperObjectiveTest {
    private GameState at(int time) {
        GameState s=LateGameTestSupport.state(); s.configureRealism(true); s.advanceTimeSeconds(time); return s;
    }
    @Test void singleCyclePartialOwnershipExpiryAndDuplicateCapture() {
        UpperObjectiveState u=new UpperObjectiveState();
        assertThat(u.available(ObjectiveType.VOID_GRUB,479)).isFalse();
        assertThat(u.capture(ObjectiveType.VOID_GRUB,TeamSide.BLUE,Position.JUNGLE,480)).isEqualTo("VOID_GRUB:1:1");
        assertThat(u.capture(ObjectiveType.VOID_GRUB,TeamSide.RED,Position.JUNGLE,480)).isNull();
        assertThat(u.capture(ObjectiveType.VOID_GRUB,TeamSide.RED,Position.JUNGLE,500)).isEqualTo("VOID_GRUB:1:2");
        assertThat(u.capture(ObjectiveType.VOID_GRUB,TeamSide.BLUE,Position.JUNGLE,520)).isEqualTo("VOID_GRUB:1:3");
        assertThat(u.grubs(TeamSide.BLUE)).isEqualTo(2); assertThat(u.grubs(TeamSide.RED)).isOne();
        assertThat(u.available(ObjectiveType.VOID_GRUB,600)).isFalse();
        assertThat(new UpperObjectiveState().available(ObjectiveType.VOID_GRUB,885)).isFalse();
        assertThat(u.available(ObjectiveType.RIFT_HERALD,899)).isFalse();
        assertThat(u.available(ObjectiveType.RIFT_HERALD,900)).isTrue();
        assertThat(u.available(ObjectiveType.RIFT_HERALD,1185)).isFalse();
        assertThatThrownBy(()->u.capture(ObjectiveType.VOID_GRUB,TeamSide.BLUE,Position.TOP,500)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void failedTriggerAllowsFallthroughWithoutDuplicateRandomConsumption() {
        GameState s=at(480); var resolver=new UpperObjectiveResolver();
        var random=org.mockito.Mockito.mock(Random.class);
        org.mockito.Mockito.when(random.nextDouble()).thenReturn(1.0);
        var events=new ArrayList<MatchEvent>();
        for(int call=0;call<2;call++)assertThat(resolver.attempt(s,random,new ObjectiveResolver(),new StructureResolver(),events)).isEmpty();
        org.mockito.Mockito.verify(random,org.mockito.Mockito.times(1)).nextDouble();
        assertThat(s.wasMajorCombatAttemptedThisTick()).isFalse();
        assertThat(events).isEmpty();
    }
    @Test void returningLanePlayerKeepsProximityXpDuringLaterLastHitRestriction() {
        GameState s=at(480); var p=s.getBlueTeamState().playerAt(Position.MID);
        p.markDead(480,30);
        var lanes=new LaneResourceResolver();
        assertThat(lanes.present(p,500)).isFalse();
        int returned=p.getLaneReturnAtSeconds();
        p.blockFarmUntil(returned+60);
        assertThat(lanes.present(p,returned)).isTrue();
        assertThat(p.canFarmAt(returned)).isFalse();
    }
    @Test void capturePaysLocalMonsterRewardsOnceAndReturnCostsRemoveLanePresence() {
        GameState s=at(480); var resolver=new UpperObjectiveResolver();
        var jungle=s.getBlueTeamState().playerAt(Position.JUNGLE);
        int before=jungle.getGold();
        var e=resolver.capture(s,ObjectiveType.VOID_GRUB,TeamSide.BLUE).orElseThrow();
        assertThat(e.getUpperObjective().participants()).hasSize(3);
        assertThat(jungle.getGold()-before).isEqualTo(30);
        assertThat(jungle.getKills()).isZero();
        assertThat(s.getBlueTeamState().getPlayers().stream().mapToInt(p->p.getProgressionState().getTotalExperience()).sum()).isEqualTo(65);
        assertThat(s.getBlueTeamState().playerAt(Position.TOP).getActivityState().getActivityType()).isEqualTo(PlayerActivityType.UPPER_OBJECTIVE_RETURN);
        assertThat(new LaneResourceResolver().present(s.getBlueTeamState().playerAt(Position.TOP),480)).isFalse();
        assertThat(resolver.capture(s,ObjectiveType.VOID_GRUB,TeamSide.BLUE)).isEmpty();
        assertThat(jungle.getGold()-before).isEqualTo(30);
    }
    @Test void heldHeraldSummonsChargesCommonTowerOnceAndCannotBypassProtectedTarget() {
        GameState s=at(900); var resolver=new UpperObjectiveResolver(); var structures=new StructureResolver();
        resolver.capture(s,ObjectiveType.RIFT_HERALD,TeamSide.BLUE).orElseThrow();
        var u=s.getObjectiveState().upper(); List<MatchEvent> events=new ArrayList<>();
        resolver.lifecycle(s,structures,events); assertThat(events).isEmpty();
        s.advanceTimeSeconds(20); s.getBlueTeamState().getPlayers().forEach(p->p.getActivityState().expireIfNeeded(920));
        resolver.lifecycle(s,structures,events); assertThat(u.heraldPhase()).isEqualTo(UpperObjectiveState.HeraldPhase.SUMMONED);
        var lane=s.getMapState().getLaneState(TeamSide.RED,u.summonLane());
        double before=lane.getTowerCurrentHealth(TowerTier.OUTER);
        s.advanceTimeSeconds(10); resolver.lifecycle(s,structures,events);
        assertThat(lane.getTowerCurrentHealth(TowerTier.OUTER)).isLessThan(before);
        assertThat(events.stream().filter(e->e.getType()==MatchEventType.HERALD_CHARGE).count()).isOne();
        double after=lane.getTowerCurrentHealth(TowerTier.OUTER);
        resolver.lifecycle(s,structures,events); assertThat(lane.getTowerCurrentHealth(TowerTier.OUTER)).isEqualTo(after);
        var forged=StructureAttackRequest.fixed(TeamSide.BLUE,Lane.BOT,LateGameStructureTarget.INNER,PushReason.MACRO_PLAY,Set.of(),3000,StructureActionSource.RIFT_HERALD,"forged");
        assertThat(structures.attemptSiege(s,forged)).isEmpty();
    }
    @Test void deadHolderCannotSummonAndEyeExpiresWithoutTowerDamage() {
        GameState s=at(900); var resolver=new UpperObjectiveResolver(); var structures=new StructureResolver();
        resolver.capture(s,ObjectiveType.RIFT_HERALD,TeamSide.BLUE).orElseThrow();
        s.getBlueTeamState().playerAt(Position.JUNGLE).markDead(900,300);
        List<MatchEvent> events=new ArrayList<>(); s.advanceTimeSeconds(240); resolver.lifecycle(s,structures,events);
        assertThat(s.getObjectiveState().upper().heraldPhase()).isEqualTo(UpperObjectiveState.HeraldPhase.EXPIRED);
        assertThat(events).singleElement().satisfies(e->assertThat(e.getType()).isEqualTo(MatchEventType.HERALD_EXPIRED));
    }
}
