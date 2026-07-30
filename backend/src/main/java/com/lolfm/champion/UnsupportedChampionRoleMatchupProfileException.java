package com.lolfm.champion;

public final class UnsupportedChampionRoleMatchupProfileException extends IllegalArgumentException {
    public static final String CODE = "UNSUPPORTED_CHAMPION_ROLE_MATCHUP_PROFILE";
    private final ChampionRoleKey roleKey;
    public UnsupportedChampionRoleMatchupProfileException(ChampionRoleKey roleKey) {
        super(CODE + ": " + roleKey.stableId());
        this.roleKey = roleKey;
    }
    public String code() { return CODE; }
    public ChampionRoleKey roleKey() { return roleKey; }
}
