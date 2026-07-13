package com.lolfm.domain;

import com.lolfm.simulator.Lane;
import com.lolfm.simulator.LanePriority;

public record LaneSnapshot(Lane lane, double pressure, LanePriority priority) { }
