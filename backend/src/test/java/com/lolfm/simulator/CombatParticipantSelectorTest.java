package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.Position;
import com.lolfm.player.PlayerId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CombatParticipantSelectorTest {
    private final CombatParticipantSelector selector = new CombatParticipantSelector();

    @Test
    void neutralRatingsPreserveAuthoredKillerAndVictimRolePriors() {
        PlayerState adc = legacy(Position.ADC, 14, 14);
        PlayerState support = legacy(Position.SUPPORT, 14, 14);

        assertEquals(List.of(.75, .25), selector.effectiveKillerWeights(
                List.of(adc, support), List.of(.75, .25)));
        assertEquals(List.of(.65, .35), selector.effectiveVictimWeights(
                List.of(adc, support), List.of(.65, .35)));
    }

    @Test
    void mechanicsDecisionAndCombatExecutionEachIncreaseKillConversion() {
        PlayerState baseline = explicit(Position.MID, ratings(Position.MID));
        PlayerState mechanics = explicit(Position.MID,
                ratings(Position.MID).with(PlayerSkill.MECHANICS, 20));
        PlayerState decision = explicit(Position.MID,
                ratings(Position.MID).with(PlayerSkill.DECISION_MAKING, 20));
        PlayerState combat = explicit(Position.MID,
                ratings(Position.MID).with(PlayerSkill.COMBAT_EXECUTION, 20));

        assertTrue(selector.killerMultiplier(mechanics) > selector.killerMultiplier(baseline));
        assertTrue(selector.killerMultiplier(decision) > selector.killerMultiplier(baseline));
        assertTrue(selector.killerMultiplier(combat) > selector.killerMultiplier(baseline));
    }

    @Test
    void positioningProducesDistinctNonFloorVictimRiskAcrossProfessionalRange() {
        PlayerState at14 = explicit(Position.ADC, ratings(Position.ADC));
        PlayerState at17 = explicit(Position.ADC,
                ratings(Position.ADC).with(PlayerSkill.POSITIONING, 17));
        PlayerState at20 = explicit(Position.ADC,
                ratings(Position.ADC).with(PlayerSkill.POSITIONING, 20));

        assertTrue(selector.victimMultiplier(at14) > selector.victimMultiplier(at17));
        assertTrue(selector.victimMultiplier(at17) > selector.victimMultiplier(at20));
        assertTrue(selector.victimMultiplier(at20)
                > CombatParticipantRuleConfig.MIN_VICTIM_MULTIPLIER);
    }

    @Test
    void legacyTeamfightVictimFormulaKeepsMechanicsAndAggressionAndNeutralPrior() {
        double adcPrior = CombatParticipantRuleConfig.teamfightVictimRolePrior(Position.ADC);
        PlayerState neutral = legacy(Position.ADC, 14, 14);
        PlayerState saferMechanics = legacy(Position.ADC, 20, 14);
        PlayerState riskierAggression = legacy(Position.ADC, 14, 20);

        assertEquals(adcPrior + 14 * PlayerImpactRuleConfig.VICTIM_AGGRESSION_RISK_WEIGHT
                        - 14 * PlayerImpactRuleConfig.VICTIM_MECHANICS_PROTECTION_WEIGHT,
                selector.teamfightVictimWeight(neutral, adcPrior), 1e-12);
        assertTrue(selector.teamfightVictimWeight(saferMechanics, adcPrior)
                < selector.teamfightVictimWeight(neutral, adcPrior));
        assertTrue(selector.teamfightVictimWeight(riskierAggression, adcPrior)
                > selector.teamfightVictimWeight(neutral, adcPrior));
    }

    @Test
    void mapDecisionAndRoleJoiningEachIncreaseAssistParticipation() {
        PlayerState baseline = explicit(Position.SUPPORT, ratings(Position.SUPPORT));
        PlayerState map = explicit(Position.SUPPORT,
                ratings(Position.SUPPORT).with(PlayerSkill.MAP_AWARENESS, 20));
        PlayerState decision = explicit(Position.SUPPORT,
                ratings(Position.SUPPORT).with(PlayerSkill.DECISION_MAKING, 20));
        PlayerState rotation = explicit(Position.SUPPORT,
                ratings(Position.SUPPORT).with(PlayerSkill.ROTATION_PLANNING, 20));

        assertTrue(selector.assistWeight(map) > selector.assistWeight(baseline));
        assertTrue(selector.assistWeight(decision) > selector.assistWeight(baseline));
        assertTrue(selector.assistWeight(rotation) > selector.assistWeight(baseline));
    }

    @Test
    void killerAndVictimSelectionKeepOneDoubleDrawPerExistingChoice() {
        List<PlayerState> candidates = List.of(
                legacy(Position.ADC, 14, 14), legacy(Position.SUPPORT, 14, 14));
        CountingRandom killerRandom = new CountingRandom(.20);
        CountingRandom victimRandom = new CountingRandom(.80);

        assertSame(candidates.getFirst(), selector.selectKiller(
                candidates, List.of(.75, .25), killerRandom));
        assertSame(candidates.getLast(), selector.selectVictim(
                candidates, List.of(.65, .35), victimRandom));
        assertEquals(1, killerRandom.doubleCalls);
        assertEquals(0, killerRandom.intCalls);
        assertEquals(1, victimRandom.doubleCalls);
        assertEquals(0, victimRandom.intCalls);
    }

    @Test
    void neutralAssistSelectionKeepsOriginalCandidateBoundAndOneNext31Draw() {
        List<PlayerState> candidates = List.of(
                legacy(Position.JUNGLE, 14, 14), legacy(Position.SUPPORT, 14, 14));
        BitsRandom random = new BitsRandom();
        CountingRandom bound = new CountingRandom(0.0);

        assertSame(candidates.getFirst(), selector.selectAssist(candidates, random));
        selector.selectAssist(candidates, bound);
        assertEquals(List.of(31), random.requestedBits);
        assertEquals(candidates.size(), bound.lastIntBound);
    }

    @Test
    void weightedAssistSelectionUsesOnePowerOfTwoNext31Draw() {
        List<PlayerState> candidates = List.of(
                explicit(Position.SUPPORT, ratings(Position.SUPPORT)),
                explicit(Position.SUPPORT, ratings(Position.SUPPORT)
                        .with(PlayerSkill.MAP_AWARENESS, 20)));
        BitsRandom bits = new BitsRandom();
        CountingRandom bound = new CountingRandom(0.0);

        selector.selectAssist(candidates, bits);
        selector.selectAssist(candidates, bound);

        assertEquals(List.of(31), bits.requestedBits);
        assertEquals(1, bound.intCalls);
        assertEquals(CombatParticipantRuleConfig.ASSIST_SELECTION_BUCKETS, bound.lastIntBound);
    }

    private PlayerRatings ratings(Position position) {
        return PlayerRatings.neutral(position).with(PlayerSkill.CONSISTENCY, 20);
    }

    private PlayerState explicit(Position position, PlayerRatings ratings) {
        TeamSide side = TeamSide.BLUE;
        PlayerMatchPerformance performance = PlayerMatchPerformance.realize(
                ratings, 14, 73L, side);
        return new PlayerState(new PlayerKey(side, position),
                new PlayerId("player-selector-" + position.name().toLowerCase()),
                "selector-" + position, position, new PlayerAttributes(14, 14, 14, 14),
                performance, 500, true);
    }

    private PlayerState legacy(Position position, int mechanics, int aggression) {
        return new PlayerState("legacy-" + position, position,
                new PlayerAttributes(mechanics, aggression, 14, 14), 500);
    }

    private static final class CountingRandom extends Random {
        private final double value;
        private int doubleCalls;
        private int intCalls;
        private int lastIntBound = -1;

        private CountingRandom(double value) { this.value = value; }
        @Override public double nextDouble() { doubleCalls++; return value; }
        @Override public int nextInt(int bound) { intCalls++; lastIntBound = bound; return 0; }
    }

    private static final class BitsRandom extends Random {
        private final List<Integer> requestedBits = new ArrayList<>();
        @Override protected int next(int bits) { requestedBits.add(bits); return 0; }
    }
}
