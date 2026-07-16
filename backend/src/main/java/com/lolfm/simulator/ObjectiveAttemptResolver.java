package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.ObjectivePriorityDecisionData;
import com.lolfm.domain.ObjectiveSelectionWeightBreakdown;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.springframework.stereotype.Component;

/** Stateless general-objective attempt resolver. */
@Component
public class ObjectiveAttemptResolver {
    private final ObjectivePriorityResolver priority = new ObjectivePriorityResolver();
    private final ObjectiveDecisionResolver decisions = new ObjectiveDecisionResolver();

    public Optional<MatchEvent> maybeAttemptObjective(
            GameState gameState, Random random, ObjectiveResolver objectiveResolver
    ) {
        return maybeAttemptObjective(gameState, random, objectiveResolver, new StructureResolver(), new ArrayList<>());
    }

    public Optional<MatchEvent> maybeAttemptObjective(
            GameState gameState, Random random, ObjectiveResolver objectiveResolver,
            StructureResolver structureResolver, List<MatchEvent> events
    ) {
        ObjectiveState objectives = gameState.getObjectiveState();
        int currentTime = gameState.getCurrentTimeSeconds();
        if (objectives.isElderAttemptDue(currentTime)) {
            objectives.markElderAttempted(currentTime);
            Optional<MatchEvent> elder = maybeAttemptElder(gameState, random, objectiveResolver, structureResolver, events);
            if (elder.isPresent()) return elder;
        }
        if (objectives.isElementalDragonPhase() && objectives.isDragonAttemptDue(currentTime)) {
            objectives.markDragonAttempted(currentTime);
            gameState.recordGeneralDragonAttempt();
            Optional<MatchEvent> dragon = maybeAttemptDragon(gameState, random, objectiveResolver, structureResolver, events);
            if (dragon.isPresent()) return dragon;
        }
        if (objectives.isBaronAttemptDue(currentTime)) {
            objectives.markBaronAttempted(currentTime);
            return maybeAttemptBaron(gameState, random, objectiveResolver, structureResolver, events);
        }
        return Optional.empty();
    }

    private Optional<MatchEvent> maybeAttemptElder(GameState state, Random random, ObjectiveResolver resolver, StructureResolver structures, List<MatchEvent> events) {
        int time = state.getCurrentTimeSeconds();
        List<TeamSide> eligible = eligibleSides(state, time, 4);
        int aliveFor = Math.max(0, time - state.getObjectiveState().getElderSpawnedAtSeconds());
        double base = ElderRuleConfig.GENERAL_CAPTURE_BASE_CHANCE
                + (aliveFor >= 60 ? .08 : 0) + (aliveFor >= 120 ? .12 : 0) + (aliveFor >= 180 ? .15 : 0);
        if (eligible.isEmpty()) {
            record(state, decision(state, ObjectiveType.ELDER, time, false, 0, 0, 0, base, 0, base,
                    false, false, eligible, ObjectiveSelectionWeightBreakdown.zero(), ObjectiveSelectionWeightBreakdown.zero(),
                    1, 1, 0, 0, false, null));
            return Optional.empty();
        }
        boolean success = random.nextDouble() < base;
        if (!success) {
            record(state, decision(state, ObjectiveType.ELDER, time, false, 0, 0, 0, base, 0, base,
                    true, false, eligible, ObjectiveSelectionWeightBreakdown.zero(), ObjectiveSelectionWeightBreakdown.zero(),
                    1, 1, 0, 0, false, null));
            return Optional.empty();
        }
        Selection selection = selectSide(state, ObjectiveType.ELDER, eligible, 0, false, random);
        ObjectivePriorityDecisionData decision = decision(state, ObjectiveType.ELDER, time, false, 0, 0, 0,
                base, 0, base, true, true, eligible, selection.blueExisting(), selection.redExisting(),
                1, 1, selection.blueExisting().totalExistingWeight(), selection.redExisting().totalExistingWeight(),
                selection.rollExecuted(), selection.side());
        record(state, decision);
        if (state.isObjectiveDecisionEnabled()) return decisions.resolve(state, ObjectiveType.ELDER, selection.side(), 0, random, resolver, structures, events, decision);
        Optional<MatchEvent> result = resolver.captureElder(state, selection.side(), time,
                random.nextBoolean() ? "장로 드래곤을 확보합니다." : "시야 주도권을 바탕으로 장로 드래곤을 처치합니다.")
                .map(ElderCaptureOutcome::event);
        result.ifPresent(event -> event.setObjectivePriorityDecision(decision));
        return result;
    }

    private Optional<MatchEvent> maybeAttemptDragon(GameState state, Random random, ObjectiveResolver resolver, StructureResolver structures, List<MatchEvent> events) {
        int time = state.getCurrentTimeSeconds();
        List<TeamSide> eligible = eligibleSides(state, time, 3);
        double lane = priority.dragonLanePressureScore(state);
        double recent = state.getObjectivePriorityState().isEnabled()
                ? state.getObjectivePriorityState().getDragonRecentControl() : 0;
        double signed = priority.dragonSignedPriority(state);
        double base = dragonExistingBaseAttemptChance(state);
        double bonus = priorityAttemptBonus(signed, state.getObjectivePriorityState().isEnabled());
        double finalChance = finalAttemptChance(base, signed, state.getObjectivePriorityState().isEnabled());
        if (eligible.isEmpty()) {
            record(state, decision(state, ObjectiveType.DRAGON, time, state.getObjectivePriorityState().isEnabled(), lane,
                    recent, signed, base, bonus, finalChance, false, false, eligible,
                    ObjectiveSelectionWeightBreakdown.zero(), ObjectiveSelectionWeightBreakdown.zero(), 1, 1, 0, 0, false, null));
            return Optional.empty();
        }
        boolean success = random.nextDouble() < finalChance;
        if (!success) {
            record(state, decision(state, ObjectiveType.DRAGON, time, state.getObjectivePriorityState().isEnabled(), lane,
                    recent, signed, base, bonus, finalChance, true, false, eligible,
                    ObjectiveSelectionWeightBreakdown.zero(), ObjectiveSelectionWeightBreakdown.zero(), 1, 1, 0, 0, false, null));
            return Optional.empty();
        }
        Selection selection = selectSide(state, ObjectiveType.DRAGON, eligible, signed,
                state.getObjectivePriorityState().isEnabled(), random);
        ObjectivePriorityDecisionData decision = decision(state, ObjectiveType.DRAGON, time,
                state.getObjectivePriorityState().isEnabled(), lane, recent, signed, base, bonus, finalChance,
                true, true, eligible, selection.blueExisting(), selection.redExisting(), selection.blueMultiplier(),
                selection.redMultiplier(), selection.finalBlue(), selection.finalRed(), selection.rollExecuted(), selection.side());
        record(state, decision);
        if (state.isObjectiveDecisionEnabled()) return decisions.resolve(state, ObjectiveType.DRAGON, selection.side(), signed, random, resolver, structures, events, decision);
        String message = random.nextBoolean()
                ? "시야와 인원 우위를 바탕으로 드래곤을 확보합니다."
                : "상대보다 먼저 드래곤 지역을 장악합니다.";
        Optional<MatchEvent> result = resolver.captureDragon(state, selection.side(), time, DragonCaptureSource.GENERAL, message);
        result.ifPresent(event -> event.setObjectivePriorityDecision(decision));
        return result;
    }

    private Optional<MatchEvent> maybeAttemptBaron(GameState state, Random random, ObjectiveResolver resolver, StructureResolver structures, List<MatchEvent> events) {
        int time = state.getCurrentTimeSeconds();
        List<TeamSide> eligible = eligibleSides(state, time, 4);
        double lane = priority.baronLanePressureScore(state);
        double recent = state.getObjectivePriorityState().isEnabled()
                ? state.getObjectivePriorityState().getBaronRecentControl() : 0;
        double signed = priority.baronSignedPriority(state);
        double base = baronExistingBaseAttemptChance(state);
        double bonus = priorityAttemptBonus(signed, state.getObjectivePriorityState().isEnabled());
        double finalChance = finalAttemptChance(base, signed, state.getObjectivePriorityState().isEnabled());
        if (eligible.isEmpty()) {
            record(state, decision(state, ObjectiveType.BARON, time, state.getObjectivePriorityState().isEnabled(), lane,
                    recent, signed, base, bonus, finalChance, false, false, eligible,
                    ObjectiveSelectionWeightBreakdown.zero(), ObjectiveSelectionWeightBreakdown.zero(), 1, 1, 0, 0, false, null));
            return Optional.empty();
        }
        boolean success = random.nextDouble() < finalChance;
        if (!success) {
            record(state, decision(state, ObjectiveType.BARON, time, state.getObjectivePriorityState().isEnabled(), lane,
                    recent, signed, base, bonus, finalChance, true, false, eligible,
                    ObjectiveSelectionWeightBreakdown.zero(), ObjectiveSelectionWeightBreakdown.zero(), 1, 1, 0, 0, false, null));
            return Optional.empty();
        }
        Selection selection = selectSide(state, ObjectiveType.BARON, eligible, signed,
                state.getObjectivePriorityState().isEnabled(), random);
        ObjectivePriorityDecisionData decision = decision(state, ObjectiveType.BARON, time,
                state.getObjectivePriorityState().isEnabled(), lane, recent, signed, base, bonus, finalChance,
                true, true, eligible, selection.blueExisting(), selection.redExisting(), selection.blueMultiplier(),
                selection.redMultiplier(), selection.finalBlue(), selection.finalRed(), selection.rollExecuted(), selection.side());
        record(state, decision);
        if (state.isObjectiveDecisionEnabled()) return decisions.resolve(state, ObjectiveType.BARON, selection.side(), signed, random, resolver, structures, events, decision);
        String message = random.nextBoolean()
                ? "상대의 빈틈을 노려 바론을 확보합니다."
                : "시야 주도권을 바탕으로 바론을 처치합니다.";
        Optional<MatchEvent> result = resolver.captureBaron(state, selection.side(), time, message);
        result.ifPresent(event -> event.setObjectivePriorityDecision(decision));
        return result;
    }

    double dragonExistingBaseAttemptChance(GameState state) {
        int aliveFor = Math.max(0, state.getCurrentTimeSeconds() - state.getObjectiveState().getDragonSpawnedAtSeconds());
        double chance = ObjectiveRuleConfig.DRAGON_GENERAL_BASE_CAPTURE_CHANCE;
        if (aliveFor >= 180) chance += ObjectiveRuleConfig.DRAGON_CAPTURE_CHANCE_AFTER_180_SECONDS;
        if (aliveFor >= 300) chance += ObjectiveRuleConfig.DRAGON_CAPTURE_CHANCE_AFTER_300_SECONDS;
        return clampChance(chance);
    }

    double baronExistingBaseAttemptChance(GameState state) {
        int time = state.getCurrentTimeSeconds();
        int aliveFor = Math.max(0, time - state.getObjectiveState().getBaronSpawnedAtSeconds());
        double chance = time >= 2_100 ? 0.14 : time >= 1_800 ? 0.08 : 0.04;
        chance += Math.min(0.06, Math.max(0, aliveFor - 240) / 2_000.0);
        return clampChance(chance);
    }

    double priorityAttemptBonus(double signedPriority, boolean enabled) {
        return enabled ? Math.abs(signedPriority) / 100.0
                * ObjectivePriorityRuleConfig.MAX_GENERAL_ATTEMPT_PRIORITY_BONUS : 0;
    }

    double finalAttemptChance(double existingBase, double signedPriority, boolean enabled) {
        return clampChance(existingBase + priorityAttemptBonus(signedPriority, enabled));
    }

    ObjectiveSelectionWeightBreakdown objectiveWeightBreakdown(GameState state, TeamSide side) {
        TeamState team = state.getTeamState(side);
        double alive = countAlivePlayers(team, state.getCurrentTimeSeconds()) * 160.0;
        double gold = team.getGold() / 90.0;
        double kills = team.getKills() * 65.0;
        double bigWin = state.hasRecentBigWin(side, 120) ? 450.0 : 0;
        double ace = state.hasRecentAce(side, 120) ? 800.0 : 0;
        double other = 0;
        return new ObjectiveSelectionWeightBreakdown(alive, gold, kills, bigWin, ace, other,
                alive + gold + kills + bigWin + ace + other);
    }

    double priorityMultiplier(double signedPriority, TeamSide side, boolean enabled) {
        if (!enabled) return 1;
        double direction = side == TeamSide.BLUE ? signedPriority : -signedPriority;
        return clamp(1 + direction / 100.0 * ObjectivePriorityRuleConfig.SIDE_SELECTION_STRENGTH,
                ObjectivePriorityRuleConfig.MIN_SIDE_SELECTION_WEIGHT,
                ObjectivePriorityRuleConfig.MAX_SIDE_SELECTION_WEIGHT);
    }

    private Selection selectSide(GameState state, ObjectiveType type, List<TeamSide> eligible,
                                 double signedPriority, boolean priorityEnabled, Random random) {
        ObjectiveSelectionWeightBreakdown blue = objectiveWeightBreakdown(state, TeamSide.BLUE);
        ObjectiveSelectionWeightBreakdown red = objectiveWeightBreakdown(state, TeamSide.RED);
        boolean applies = priorityEnabled && type != ObjectiveType.ELDER;
        double blueMultiplier = priorityMultiplier(signedPriority, TeamSide.BLUE, applies);
        double redMultiplier = priorityMultiplier(signedPriority, TeamSide.RED, applies);
        double finalBlue = blue.totalExistingWeight() * blueMultiplier;
        double finalRed = red.totalExistingWeight() * redMultiplier;
        if (eligible.size() == 1) {
            return new Selection(eligible.getFirst(), blue, red, blueMultiplier, redMultiplier, finalBlue, finalRed, false);
        }
        TeamSide side = random.nextDouble() < finalBlue / (finalBlue + finalRed) ? TeamSide.BLUE : TeamSide.RED;
        return new Selection(side, blue, red, blueMultiplier, redMultiplier, finalBlue, finalRed, true);
    }

    private ObjectivePriorityDecisionData decision(GameState state, ObjectiveType type, int time,
            boolean priorityApplied, double lane, double recent, double signed, double base, double bonus,
            double finalChance, boolean attemptRollExecuted, boolean attemptSucceeded, List<TeamSide> eligible,
            ObjectiveSelectionWeightBreakdown blue, ObjectiveSelectionWeightBreakdown red,
            double blueMultiplier, double redMultiplier, double finalBlue, double finalRed,
            boolean sideRoll, TeamSide selected) {
        boolean enabled = state.getObjectivePriorityState().isEnabled();
        double bluePriority = type == ObjectiveType.ELDER ? 50 : priority.blueDisplayPriority(signed);
        double redPriority = 100 - bluePriority;
        return new ObjectivePriorityDecisionData(type, time, enabled, true, false,
                priorityApplied && type != ObjectiveType.ELDER, lane, recent, signed, bluePriority, redPriority,
                base, bonus, finalChance, attemptRollExecuted, attemptSucceeded,
                eligible.contains(TeamSide.BLUE), eligible.contains(TeamSide.RED), blue, red,
                blueMultiplier, redMultiplier, finalBlue, finalRed, sideRoll, selected,
                type == ObjectiveType.DRAGON ? priority.dragonMacroSetupControl(state)
                        : type == ObjectiveType.BARON ? priority.baronMacroSetupControl(state) : 0);
    }

    private void record(GameState state, ObjectivePriorityDecisionData data) {
        state.getObjectivePriorityExecutionStats().recordDecision(data);
    }

    private List<TeamSide> eligibleSides(GameState state, int currentTime, int minimumAlivePlayers) {
        List<TeamSide> eligible = new ArrayList<>();
        for (TeamSide side : TeamSide.values()) {
            if (countAlivePlayers(state.getTeamState(side), currentTime) >= minimumAlivePlayers) eligible.add(side);
        }
        return eligible;
    }

    private int countAlivePlayers(TeamState team, int currentTime) {
        int count = 0;
        for (PlayerState player : team.getPlayers()) if (player.isAlive(currentTime)) count++;
        return count;
    }

    private double clampChance(double value) { return clamp(value, 0, 1); }
    private double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    private record Selection(TeamSide side, ObjectiveSelectionWeightBreakdown blueExisting,
                             ObjectiveSelectionWeightBreakdown redExisting, double blueMultiplier,
                             double redMultiplier, double finalBlue, double finalRed, boolean rollExecuted) { }
}
