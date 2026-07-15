package com.lolfm.domain;

import com.lolfm.simulator.Lane;
import com.lolfm.simulator.LanePhase;

public record LanePhaseLaneSnapshot(
        Lane lane,
        LanePhase phase,
        double pressure,
        boolean pressureFarmModifierActive,
        OuterTurretSnapshot blueOuter,
        OuterTurretSnapshot redOuter
) { }
