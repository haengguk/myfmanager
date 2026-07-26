package com.lolfm.simulator;

import java.util.EnumMap;
import java.util.List;

final class SideOrientationFunnelAccumulator {
    private final EnumMap<SideOrientationResolver, EnumMap<TeamSide, long[]>> totals =
            new EnumMap<>(SideOrientationResolver.class);

    SideOrientationFunnelAccumulator() {
        for (SideOrientationResolver resolver : SideOrientationResolver.values()) {
            EnumMap<TeamSide, long[]> sides = new EnumMap<>(TeamSide.class);
            for (TeamSide side : TeamSide.values()) sides.put(side, new long[21]);
            totals.put(resolver, sides);
        }
    }

    void add(List<SideOrientationMatchRow> rows) {
        for (SideOrientationMatchRow row : rows) {
            for (SideOrientationResolver resolver : SideOrientationResolver.values()) {
                for (TeamSide side : TeamSide.values()) {
                    add(totals.get(resolver).get(side), row.funnel().get(resolver).get(side));
                }
            }
        }
    }

    long[] values(SideOrientationResolver resolver, TeamSide side) {
        return totals.get(resolver).get(side).clone();
    }

    String csv(
            String auditGroup,
            String fixture,
            String mode,
            String skillProfile,
            SideOrientationResolver resolver,
            TeamSide side
    ) {
        long[] v = totals.get(resolver).get(side);
        return String.join(",", auditGroup, fixture, mode, skillProfile, resolver.toString(),
                side.toString(), numbers(v),
                rate(v[1], v[0]), rate(v[2], v[1]), rate(v[3], v[2]),
                rate(v[4], v[3]), rate(v[5], v[4]), rate(v[11], v[1]),
                rate(v[12], v[1]), rate(v[13], v[1]), rate(v[18], v[0]),
                rate(v[19], v[3]), rate(v[20], v[8]));
    }

    private void add(long[] v, SideOrientationExecutionStats.Snapshot s) {
        v[0] += s.evaluations();
        v[1] += s.eligibleEvaluations();
        v[2] += s.triggerSuccesses();
        v[3] += s.actualAttempts();
        v[4] += s.actualOutcomes();
        v[5] += s.successfulOutcomes();
        v[6] += s.kills();
        v[7] += s.objectiveCaptures();
        v[8] += s.structureMutations();
        v[9] += s.nexusMutations();
        v[10] += s.majorCombatSlotConsumed();
        v[11] += s.blockedByMajorCombatSlot();
        v[12] += s.blockedByEarlierSideAttempt();
        v[13] += s.blockedByEarlierMutation();
        v[14] += s.blockedByCooldown();
        v[15] += s.blockedByNoCandidate();
        v[16] += s.blockedByDeadParticipant();
        v[17] += s.blockedByOtherEligibility();
        v[18] += s.evaluatedFirst();
        v[19] += s.attemptedFirst();
        v[20] += s.mutatedFirst();
    }

    private String numbers(long[] values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append(',');
            out.append(values[i]);
        }
        return out.toString();
    }

    private String rate(long numerator, long denominator) {
        return denominator == 0 ? "NOT_APPLICABLE"
                : Double.toString(numerator / (double) denominator);
    }
}
