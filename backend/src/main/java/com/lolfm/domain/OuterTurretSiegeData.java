package com.lolfm.domain;

import com.lolfm.simulator.Lane;
import com.lolfm.simulator.TeamSide;

public record OuterTurretSiegeData(
        int timeSeconds,
        Lane lane,
        TeamSide attackingSide,
        TeamSide defendingSide,
        double lanePressure,
        double integrityBefore,
        double pressureDamage,
        double defenderAbsentBonus,
        double botSupportBonus,
        double randomVariance,
        double finalDamage,
        double integrityAfter,
        boolean destroyed
) { }
