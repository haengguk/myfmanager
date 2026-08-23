package com.lolfm.domain;

/** Structured, auditable payout data for one actual kill. */
public record KillEventData(
        boolean firstBlood,
        int baseKillGold,
        int firstBloodBonusGold,
        int shutdownGold,
        int killerTotalGoldAwarded,
        int assistGoldPerPlayer,
        int totalAssistGold
) { }
