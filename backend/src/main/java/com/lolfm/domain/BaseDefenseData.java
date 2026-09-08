package com.lolfm.domain;

import com.lolfm.simulator.TeamSide;
import java.util.List;

public record BaseDefenseData(TeamSide defendingSide, String reason,
        List<String> defenders, List<String> attackers, int arrivalAtSeconds,
        double defensePower, double attackPower, boolean actualAttempt) {
    public BaseDefenseData { defenders = List.copyOf(defenders); attackers = List.copyOf(attackers); }
}
