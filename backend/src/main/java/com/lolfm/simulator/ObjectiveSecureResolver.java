package com.lolfm.simulator;

import com.lolfm.domain.ObjectiveSecureDecisionData;
import com.lolfm.domain.Position;
import java.util.Objects;
import java.util.Random;

/** Stateless contested-objective secure and steal decision. */
public final class ObjectiveSecureResolver {
    private final PlayerSkillEvaluator playerSkills = new PlayerSkillEvaluator();

    public ObjectiveSecureDecisionData resolve(
            GameState state, ObjectiveType type, TeamSide fightWinner, Random random) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(fightWinner, "fightWinner");
        Objects.requireNonNull(random, "random");

        TeamSide challengingSide = fightWinner.opposite();
        PlayerState winningJungler = state.getTeamState(fightWinner).playerAt(Position.JUNGLE);
        PlayerState challengingJungler = state.getTeamState(challengingSide).playerAt(Position.JUNGLE);
        if (!objectiveAvailable(state, type)) {
            return ineligible(ObjectiveSecureIneligibleReason.OBJECTIVE_UNAVAILABLE,
                    winningJungler, challengingJungler, fightWinner);
        }
        if (!winningJungler.hasMatchPerformance()) {
            return ineligible(ObjectiveSecureIneligibleReason.WINNING_JUNGLER_PROFILE_UNAVAILABLE,
                    winningJungler, challengingJungler, fightWinner);
        }
        if (!challengingJungler.hasMatchPerformance()) {
            return ineligible(ObjectiveSecureIneligibleReason.CHALLENGING_JUNGLER_PROFILE_UNAVAILABLE,
                    winningJungler, challengingJungler, fightWinner);
        }
        int time = state.getCurrentTimeSeconds();
        if (!winningJungler.isAlive(time) || !winningJungler.canParticipateInMajorCombatAt(time)) {
            return ineligible(ObjectiveSecureIneligibleReason.WINNING_JUNGLER_UNAVAILABLE,
                    winningJungler, challengingJungler, fightWinner);
        }
        if (!challengingJungler.isAlive(time) || !challengingJungler.canParticipateInMajorCombatAt(time)) {
            return ineligible(ObjectiveSecureIneligibleReason.CHALLENGING_JUNGLER_UNAVAILABLE,
                    winningJungler, challengingJungler, fightWinner);
        }

        double winningSecure = playerSkills.objectiveSecure(winningJungler);
        double challengingSecure = playerSkills.objectiveSecure(challengingJungler);
        double winningSetup = setupControl(state.getTeamState(fightWinner), time);
        double challengingSetup = setupControl(state.getTeamState(challengingSide), time);
        double secureContribution = (challengingSecure - winningSecure)
                * ObjectivePlayerSkillRuleConfig.STEAL_CHANCE_PER_SECURE_EDGE_POINT;
        double setupContribution = (challengingSetup - winningSetup)
                * ObjectivePlayerSkillRuleConfig.STEAL_CHANCE_PER_SETUP_EDGE_POINT;
        double chance = stealChance(secureContribution, setupContribution);
        double roll = random.nextDouble();
        boolean stolen = roll < chance;
        TeamSide captureSide = stolen ? challengingSide : fightWinner;
        return new ObjectiveSecureDecisionData(true, null,
                winningJungler.getStructuredPlayerId(), challengingJungler.getStructuredPlayerId(),
                fightWinner, challengingSide, winningSecure, challengingSecure,
                winningSetup, challengingSetup, ObjectivePlayerSkillRuleConfig.BASE_STEAL_CHANCE,
                secureContribution, setupContribution, chance, true, roll, captureSide, stolen,
                null, false);
    }

    double stealChance(double secureContribution, double setupContribution) {
        return clamp(ObjectivePlayerSkillRuleConfig.BASE_STEAL_CHANCE
                        + secureContribution + setupContribution,
                ObjectivePlayerSkillRuleConfig.MIN_STEAL_CHANCE,
                ObjectivePlayerSkillRuleConfig.MAX_STEAL_CHANCE);
    }

    private double setupControl(TeamState team, int time) {
        PlayerState support = team.playerAt(Position.SUPPORT);
        if (!support.hasMatchPerformance() || !support.isAlive(time)
                || !support.canParticipateInMajorCombatAt(time)) {
            return ObjectivePlayerSkillRuleConfig.BASELINE_SKILL;
        }
        return playerSkills.areaSetup(support) * ObjectivePlayerSkillRuleConfig.SECURE_SETUP_AREA_WEIGHT
                + playerSkills.visionControl(support) * ObjectivePlayerSkillRuleConfig.SECURE_SETUP_VISION_WEIGHT;
    }

    private ObjectiveSecureDecisionData ineligible(
            ObjectiveSecureIneligibleReason reason, PlayerState winner, PlayerState challenger,
            TeamSide fightWinner) {
        return new ObjectiveSecureDecisionData(false, reason,
                winner.getStructuredPlayerId(), challenger.getStructuredPlayerId(),
                fightWinner, fightWinner.opposite(), 0, 0,
                0, 0, ObjectivePlayerSkillRuleConfig.BASE_STEAL_CHANCE,
                0, 0, 0, false, null, fightWinner, false, null, false);
    }

    private boolean objectiveAvailable(GameState state, ObjectiveType type) {
        if (state.isFinished()) return false;
        return switch (type) {
            case DRAGON -> state.getObjectiveState().isElementalDragonPhase()
                    && state.getObjectiveState().isDragonAlive();
            case BARON -> state.getObjectiveState().isBaronAlive();
            case ELDER -> state.getObjectiveState().isElderPhase()
                    && state.getObjectiveState().isElderAlive();
        };
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
