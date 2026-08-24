package com.lolfm.simulator;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Stateless, ability-aware selection for actual combat participants. */
public final class CombatParticipantSelector {
    private final PlayerSkillEvaluator playerSkills = new PlayerSkillEvaluator();

    public PlayerState selectKiller(List<PlayerState> candidates, List<Double> rolePriors,
                                    Random random) {
        return selectWithDouble(candidates, effectiveKillerWeights(candidates, rolePriors), random);
    }

    public PlayerState selectVictim(List<PlayerState> candidates, List<Double> rolePriors,
                                    Random random) {
        return selectWithDouble(candidates, effectiveVictimWeights(candidates, rolePriors), random);
    }

    public PlayerState selectTeamfightVictim(List<PlayerState> candidates, List<Double> rolePriors,
                                             Random random) {
        validatePriors(candidates, rolePriors);
        List<Double> weights = java.util.stream.IntStream.range(0, candidates.size())
                .mapToObj(index -> teamfightVictimWeight(candidates.get(index), rolePriors.get(index)))
                .toList();
        return selectWithDouble(candidates, weights, random);
    }

    public PlayerState selectAssist(List<PlayerState> candidates, Random random) {
        validateCandidates(candidates, random);
        List<Double> weights = candidates.stream().map(this::assistWeight).toList();
        if (allEqual(weights)) return candidates.get(random.nextInt(candidates.size()));
        int bucket = random.nextInt(CombatParticipantRuleConfig.ASSIST_SELECTION_BUCKETS);
        double sample = bucket / (double) CombatParticipantRuleConfig.ASSIST_SELECTION_BUCKETS;
        return selectAtSample(candidates, weights, sample);
    }

    List<Double> effectiveKillerWeights(List<PlayerState> candidates, List<Double> rolePriors) {
        validatePriors(candidates, rolePriors);
        return java.util.stream.IntStream.range(0, candidates.size())
                .mapToObj(index -> rolePriors.get(index) * killerMultiplier(candidates.get(index)))
                .toList();
    }

    List<Double> effectiveVictimWeights(List<PlayerState> candidates, List<Double> rolePriors) {
        validatePriors(candidates, rolePriors);
        return java.util.stream.IntStream.range(0, candidates.size())
                .mapToObj(index -> rolePriors.get(index) * victimMultiplier(candidates.get(index)))
                .toList();
    }

    double killerMultiplier(PlayerState player) {
        return clamp(1.0 + (playerSkills.killConversion(player)
                        - PlayerImpactRuleConfig.BASELINE_ATTRIBUTE)
                        * CombatParticipantRuleConfig.ATTRIBUTE_MULTIPLIER_PER_POINT,
                CombatParticipantRuleConfig.MIN_ATTRIBUTE_MULTIPLIER,
                CombatParticipantRuleConfig.MAX_ATTRIBUTE_MULTIPLIER);
    }

    double victimMultiplier(PlayerState player) {
        if (!player.hasMatchPerformance()) {
            return clamp(1.0 + (player.getAggression() - PlayerImpactRuleConfig.BASELINE_ATTRIBUTE)
                            * CombatParticipantRuleConfig.ATTRIBUTE_MULTIPLIER_PER_POINT
                            - (player.getMechanics() - PlayerImpactRuleConfig.BASELINE_ATTRIBUTE)
                            * CombatParticipantRuleConfig.ATTRIBUTE_MULTIPLIER_PER_POINT
                            + player.getDeaths()
                            * CombatParticipantRuleConfig.VICTIM_REPEAT_DEATH_MULTIPLIER_PER_DEATH,
                    CombatParticipantRuleConfig.MIN_VICTIM_MULTIPLIER,
                    CombatParticipantRuleConfig.MAX_VICTIM_MULTIPLIER);
        }
        return clamp(1.0 + (PlayerImpactRuleConfig.BASELINE_ATTRIBUTE
                        - playerSkills.exposureSafety(player))
                        * CombatParticipantRuleConfig.VICTIM_EXPOSURE_MULTIPLIER_PER_POINT
                        + player.getDeaths()
                        * CombatParticipantRuleConfig.VICTIM_REPEAT_DEATH_MULTIPLIER_PER_DEATH,
                CombatParticipantRuleConfig.MIN_VICTIM_MULTIPLIER,
                CombatParticipantRuleConfig.MAX_VICTIM_MULTIPLIER);
    }

    double teamfightVictimWeight(PlayerState player, double rolePrior) {
        if (player.hasMatchPerformance()) return rolePrior * victimMultiplier(player);
        return Math.max(.15, rolePrior
                + player.getAggression() * PlayerImpactRuleConfig.VICTIM_AGGRESSION_RISK_WEIGHT
                - player.getMechanics() * PlayerImpactRuleConfig.VICTIM_MECHANICS_PROTECTION_WEIGHT
                + player.getDeaths()
                * CombatParticipantRuleConfig.LEGACY_TEAMFIGHT_REPEAT_DEATH_WEIGHT);
    }

    double assistWeight(PlayerState player) {
        return clamp(1.0 + (playerSkills.assistParticipation(player)
                        - PlayerImpactRuleConfig.BASELINE_ATTRIBUTE)
                        * CombatParticipantRuleConfig.ATTRIBUTE_MULTIPLIER_PER_POINT,
                CombatParticipantRuleConfig.MIN_ATTRIBUTE_MULTIPLIER,
                CombatParticipantRuleConfig.MAX_ATTRIBUTE_MULTIPLIER);
    }

    private PlayerState selectWithDouble(List<PlayerState> candidates, List<Double> weights,
                                         Random random) {
        validateCandidates(candidates, random);
        return selectAtSample(candidates, weights, random.nextDouble());
    }

    private PlayerState selectAtSample(List<PlayerState> candidates, List<Double> weights,
                                       double sample) {
        double total = weights.stream().mapToDouble(weight -> Math.max(.0001, weight)).sum();
        double roll = sample * total;
        for (int index = 0; index < candidates.size(); index++) {
            roll -= Math.max(.0001, weights.get(index));
            if (roll <= 0.0) return candidates.get(index);
        }
        return candidates.getLast();
    }

    private void validatePriors(List<PlayerState> candidates, List<Double> rolePriors) {
        Objects.requireNonNull(rolePriors, "rolePriors");
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("Combat participant candidates are required");
        }
        if (candidates.size() != rolePriors.size()) {
            throw new IllegalArgumentException("Combat participant prior count mismatch");
        }
        if (rolePriors.stream().anyMatch(value -> value == null || value <= 0.0)) {
            throw new IllegalArgumentException("Combat participant priors must be positive");
        }
    }

    private void validateCandidates(List<PlayerState> candidates, Random random) {
        Objects.requireNonNull(random, "random");
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("Combat participant candidates are required");
        }
    }

    private boolean allEqual(List<Double> weights) {
        double first = weights.getFirst();
        return weights.stream().allMatch(value -> Double.compare(first, value) == 0);
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
