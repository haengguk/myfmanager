package com.lolfm.composition;

import java.util.Objects;

/** One existing gameplay branch draw, or an explicit typed non-draw. */
public record FightGradeBranchDiagnostic(
        String branch,
        Double threshold,
        FightGradeBranchDrawState drawState,
        Double randomSample,
        Long randomDrawOrdinal
) {
    public FightGradeBranchDiagnostic {
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(drawState, "drawState");
        if (drawState == FightGradeBranchDrawState.DRAWN) {
            if (threshold == null || randomSample == null || randomDrawOrdinal == null
                    || !Double.isFinite(threshold) || !Double.isFinite(randomSample)
                    || randomSample < 0.0 || randomSample >= 1.0 || randomDrawOrdinal < 1) {
                throw new IllegalArgumentException("Drawn grade branch requires a finite captured threshold/sample/ordinal");
            }
        } else if (threshold != null || randomSample != null || randomDrawOrdinal != null) {
            throw new IllegalArgumentException("An unreached grade branch must not fabricate threshold/sample/ordinal");
        }
    }

    public static FightGradeBranchDiagnostic drawn(String branch, double threshold, double sample, long ordinal) {
        return new FightGradeBranchDiagnostic(branch, threshold, FightGradeBranchDrawState.DRAWN, sample, ordinal);
    }

    public static FightGradeBranchDiagnostic notReached(String branch) {
        return new FightGradeBranchDiagnostic(branch, null,
                FightGradeBranchDrawState.NOT_DRAWN_BRANCH_NOT_REACHED, null, null);
    }
}
