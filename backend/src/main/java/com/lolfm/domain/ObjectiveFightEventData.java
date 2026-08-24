package com.lolfm.domain;

import com.lolfm.simulator.TeamSide;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Structured association shared by an objective-fight summary and its result event. */
public record ObjectiveFightEventData(
        TeamSide winningSide,
        List<String> participantPlayerIds,
        int kills,
        ObjectiveFightSkillImpactData skillImpact
) {
    public ObjectiveFightEventData {
        Objects.requireNonNull(winningSide, "winningSide");
        participantPlayerIds = List.copyOf(participantPlayerIds);
        if (participantPlayerIds.stream().anyMatch(id -> id == null || id.isBlank())
                || new HashSet<>(participantPlayerIds).size() != participantPlayerIds.size()) {
            throw new IllegalArgumentException("objective fight participant identities must be non-blank and unique");
        }
        if (kills < 0) throw new IllegalArgumentException("kills");
        Objects.requireNonNull(skillImpact, "skillImpact");
    }
}
