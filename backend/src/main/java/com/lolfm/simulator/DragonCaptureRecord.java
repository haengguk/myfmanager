package com.lolfm.simulator;

public record DragonCaptureRecord(
        TeamSide capturingSide,
        int captureTimeSeconds,
        int spawnedAliveSeconds,
        DragonCaptureSource source
) {
}
