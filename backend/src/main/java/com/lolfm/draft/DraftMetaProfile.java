package com.lolfm.draft;

import com.lolfm.champion.ChampionRoleKey;

public record DraftMetaProfile(ChampionRoleKey roleKey, int priority) {
    public DraftMetaProfile {
        if (roleKey == null) throw new IllegalArgumentException("roleKey is required");
        if (priority < 1 || priority > 20) {
            throw new IllegalArgumentException("Draft meta priority must be 1..20: " + priority);
        }
    }
}
