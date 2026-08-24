package com.lolfm.domain;

import com.lolfm.simulator.ObjectiveSecureIneligibleReason;
import com.lolfm.simulator.TeamSide;
import java.util.Objects;

public record ObjectiveSecureDecisionData(
        boolean eligible,
        ObjectiveSecureIneligibleReason ineligibleReason,
        String winningJunglerPlayerId,
        String challengingJunglerPlayerId,
        TeamSide fightWinner,
        TeamSide challengingSide,
        double winningObjectiveSecureScore,
        double challengingObjectiveSecureScore,
        double winningSetupControlScore,
        double challengingSetupControlScore,
        double baseStealChance,
        double secureSkillEdgeContribution,
        double setupEdgeContribution,
        double finalStealChance,
        boolean rollExecuted,
        Double roll,
        TeamSide selectedCaptureSide,
        boolean secureWon,
        Boolean captureSucceeded,
        boolean actualSteal
) {
    public ObjectiveSecureDecisionData {
        Objects.requireNonNull(fightWinner, "fightWinner");
        Objects.requireNonNull(challengingSide, "challengingSide");
        Objects.requireNonNull(selectedCaptureSide, "selectedCaptureSide");
        if (challengingSide != fightWinner.opposite()) {
            throw new IllegalArgumentException("challengingSide must oppose fightWinner");
        }
        if (winningJunglerPlayerId == null || winningJunglerPlayerId.isBlank()
                || challengingJunglerPlayerId == null || challengingJunglerPlayerId.isBlank()) {
            throw new IllegalArgumentException("structured jungler identities are required");
        }
        if (!finite(winningObjectiveSecureScore, challengingObjectiveSecureScore,
                winningSetupControlScore, challengingSetupControlScore, baseStealChance,
                secureSkillEdgeContribution, setupEdgeContribution, finalStealChance)) {
            throw new IllegalArgumentException("secure decision numeric fields must be finite");
        }
        if (baseStealChance < 0 || baseStealChance > 1) {
            throw new IllegalArgumentException("base steal chance must be between 0 and 1");
        }
        if (eligible) {
            if (winningJunglerPlayerId.equals(challengingJunglerPlayerId)) {
                throw new IllegalArgumentException("eligible secure decision requires distinct jungler identities");
            }
            if (ineligibleReason != null || !rollExecuted || roll == null) {
                throw new IllegalArgumentException("eligible secure decision requires exactly one roll");
            }
            if (!Double.isFinite(roll) || roll < 0 || roll >= 1) {
                throw new IllegalArgumentException("secure roll must be in [0, 1)");
            }
            if (!Double.isFinite(finalStealChance)
                    || finalStealChance < 0 || finalStealChance > 1) {
                throw new IllegalArgumentException("steal chance must be between 0 and 1");
            }
            if (secureWon != (roll < finalStealChance)) {
                throw new IllegalArgumentException("secureWon flag and secure threshold disagree");
            }
            if (secureWon != (selectedCaptureSide == challengingSide)) {
                throw new IllegalArgumentException("secureWon flag and selected capture side disagree");
            }
        } else {
            if (ineligibleReason == null || rollExecuted || roll != null || secureWon
                    || selectedCaptureSide != fightWinner || finalStealChance != 0) {
                throw new IllegalArgumentException("ineligible secure decision must preserve fight winner without a roll");
            }
        }
        boolean expectedActualSteal = Boolean.TRUE.equals(captureSucceeded)
                && eligible && secureWon;
        if (actualSteal != expectedActualSteal) {
            throw new IllegalArgumentException("actual steal requires a won secure roll and successful capture");
        }
    }

    public ObjectiveSecureDecisionData withCaptureResult(boolean succeeded) {
        if (captureSucceeded != null) {
            throw new IllegalStateException("objective capture result is already finalized");
        }
        return new ObjectiveSecureDecisionData(eligible, ineligibleReason,
                winningJunglerPlayerId, challengingJunglerPlayerId, fightWinner,
                challengingSide, winningObjectiveSecureScore, challengingObjectiveSecureScore,
                winningSetupControlScore, challengingSetupControlScore, baseStealChance,
                secureSkillEdgeContribution, setupEdgeContribution, finalStealChance,
                rollExecuted, roll, selectedCaptureSide, secureWon, succeeded,
                succeeded && eligible && secureWon);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
