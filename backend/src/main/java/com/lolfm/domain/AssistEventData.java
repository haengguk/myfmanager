package com.lolfm.domain;

/** One assistant's child event for an actual kill; it is never a combat attempt. */
public record AssistEventData(
        String assistantPlayerId,
        String killerPlayerId,
        String victimPlayerId,
        int goldAwarded
) { }
