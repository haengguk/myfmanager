package com.lolfm.draft;

import com.lolfm.composition.CompositionCapability;
import java.util.Set;

public enum DraftPlanArchetype {
    POKE_SIEGE(
            Set.of(CompositionCapability.POKE, CompositionCapability.SIEGE, CompositionCapability.WAVE_CLEAR, CompositionCapability.ZONE_CONTROL),
            Set.of(CompositionCapability.ENGAGE, CompositionCapability.BACKLINE_ACCESS)),
    FRONT_TO_BACK(
            Set.of(CompositionCapability.FRONTLINE, CompositionCapability.PEEL, CompositionCapability.SUSTAINED_DAMAGE, CompositionCapability.FOLLOW_UP),
            Set.of(CompositionCapability.BACKLINE_ACCESS, CompositionCapability.BURST_DAMAGE)),
    PICK_CONTROL(
            Set.of(CompositionCapability.PICK, CompositionCapability.ZONE_CONTROL, CompositionCapability.BURST_DAMAGE, CompositionCapability.WAVE_CLEAR),
            Set.of(CompositionCapability.FRONTLINE, CompositionCapability.DISENGAGE)),
    DIVE(
            Set.of(CompositionCapability.ENGAGE, CompositionCapability.BACKLINE_ACCESS, CompositionCapability.FOLLOW_UP, CompositionCapability.BURST_DAMAGE),
            Set.of(CompositionCapability.PEEL, CompositionCapability.DISENGAGE, CompositionCapability.FRONTLINE));

    private final Set<CompositionCapability> desired;
    private final Set<CompositionCapability> vulnerabilities;
    DraftPlanArchetype(Set<CompositionCapability> desired, Set<CompositionCapability> vulnerabilities) {
        this.desired = desired; this.vulnerabilities = vulnerabilities;
    }
    public Set<CompositionCapability> desired() { return desired; }
    public Set<CompositionCapability> vulnerabilities() { return vulnerabilities; }
}
