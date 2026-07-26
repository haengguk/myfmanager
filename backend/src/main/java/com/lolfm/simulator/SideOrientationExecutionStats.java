package com.lolfm.simulator;

import java.util.EnumMap;
import java.util.Map;

/** Mutable, explicitly match-scoped side audit counters. */
public final class SideOrientationExecutionStats {
    private final EnumMap<SideOrientationResolver, EnumMap<TeamSide, Counters>> values =
            new EnumMap<>(SideOrientationResolver.class);

    public SideOrientationExecutionStats() {
        for (SideOrientationResolver resolver : SideOrientationResolver.values()) {
            EnumMap<TeamSide, Counters> sides = new EnumMap<>(TeamSide.class);
            for (TeamSide side : TeamSide.values()) sides.put(side, new Counters());
            values.put(resolver, sides);
        }
    }

    public Counters counters(SideOrientationResolver resolver, TeamSide side) {
        return values.get(resolver).get(side);
    }

    public Map<SideOrientationResolver, Map<TeamSide, Snapshot>> snapshot() {
        EnumMap<SideOrientationResolver, Map<TeamSide, Snapshot>> result =
                new EnumMap<>(SideOrientationResolver.class);
        values.forEach((resolver, sides) -> {
            EnumMap<TeamSide, Snapshot> sideResult = new EnumMap<>(TeamSide.class);
            sides.forEach((side, counters) -> sideResult.put(side, counters.snapshot()));
            result.put(resolver, Map.copyOf(sideResult));
        });
        return Map.copyOf(result);
    }

    public static final class Counters {
        private long evaluations;
        private long eligibleEvaluations;
        private long triggerSuccesses;
        private long actualAttempts;
        private long actualOutcomes;
        private long successfulOutcomes;
        private long kills;
        private long objectiveCaptures;
        private long structureMutations;
        private long nexusMutations;
        private long majorCombatSlotConsumed;
        private long blockedByMajorCombatSlot;
        private long blockedByEarlierSideAttempt;
        private long blockedByEarlierMutation;
        private long blockedByCooldown;
        private long blockedByNoCandidate;
        private long blockedByDeadParticipant;
        private long blockedByOtherEligibility;
        private long evaluatedFirst;
        private long attemptedFirst;
        private long mutatedFirst;

        public void evaluation(boolean eligible) {
            evaluations++;
            if (eligible) eligibleEvaluations++;
        }
        public void trigger() { triggerSuccesses++; }
        public void attempt(boolean majorSlot) {
            actualAttempts++;
            if (majorSlot) majorCombatSlotConsumed++;
        }
        public void outcome(boolean successful) {
            actualOutcomes++;
            if (successful) successfulOutcomes++;
        }
        public void kill() { kills++; }
        public void objectiveCapture() { objectiveCaptures++; }
        public void structureMutation(boolean nexus) {
            structureMutations++;
            if (nexus) nexusMutations++;
        }
        public void block(BlockReason reason) {
            switch (reason) {
                case MAJOR_COMBAT_SLOT -> blockedByMajorCombatSlot++;
                case EARLIER_SIDE_ATTEMPT -> blockedByEarlierSideAttempt++;
                case EARLIER_MUTATION -> blockedByEarlierMutation++;
                case COOLDOWN -> blockedByCooldown++;
                case NO_CANDIDATE -> blockedByNoCandidate++;
                case DEAD_PARTICIPANT -> blockedByDeadParticipant++;
                case OTHER_ELIGIBILITY -> blockedByOtherEligibility++;
            }
        }
        public void evaluatedFirst() { evaluatedFirst++; }
        public void attemptedFirst() { attemptedFirst++; }
        public void mutatedFirst() { mutatedFirst++; }

        public Snapshot snapshot() {
            return new Snapshot(evaluations, eligibleEvaluations, triggerSuccesses, actualAttempts,
                    actualOutcomes, successfulOutcomes, kills, objectiveCaptures, structureMutations,
                    nexusMutations, majorCombatSlotConsumed, blockedByMajorCombatSlot,
                    blockedByEarlierSideAttempt, blockedByEarlierMutation, blockedByCooldown,
                    blockedByNoCandidate, blockedByDeadParticipant, blockedByOtherEligibility,
                    evaluatedFirst, attemptedFirst, mutatedFirst);
        }
    }

    public enum BlockReason {
        MAJOR_COMBAT_SLOT, EARLIER_SIDE_ATTEMPT, EARLIER_MUTATION, COOLDOWN,
        NO_CANDIDATE, DEAD_PARTICIPANT, OTHER_ELIGIBILITY
    }

    public record Snapshot(
            long evaluations, long eligibleEvaluations, long triggerSuccesses, long actualAttempts,
            long actualOutcomes, long successfulOutcomes, long kills, long objectiveCaptures,
            long structureMutations, long nexusMutations, long majorCombatSlotConsumed,
            long blockedByMajorCombatSlot, long blockedByEarlierSideAttempt,
            long blockedByEarlierMutation, long blockedByCooldown, long blockedByNoCandidate,
            long blockedByDeadParticipant, long blockedByOtherEligibility, long evaluatedFirst,
            long attemptedFirst, long mutatedFirst
    ) {
    }
}
