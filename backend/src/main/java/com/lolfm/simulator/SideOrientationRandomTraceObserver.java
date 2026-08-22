package com.lolfm.simulator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

/**
 * Match-scoped observer for the existing seeded Random stream.
 * It delegates every draw to java.util.Random and never requests a draw itself.
 */
public final class SideOrientationRandomTraceObserver extends Random {
    private final List<Draw> trace = new ArrayList<>();
    private final long seed;
    private long drawIndex;
    private Source source = Source.NONE;
    private TeamSide side;
    private int tickSeconds;
    private final String orientation;
    private final String blueLogicalTeam;
    private final String redLogicalTeam;
    private final boolean captureTrace;
    private final MessageDigest traceDigest;
    private String finalizedTraceHash;

    public SideOrientationRandomTraceObserver(
            long seed,
            String orientation,
            String blueLogicalTeam,
            String redLogicalTeam,
            boolean captureTrace
    ) {
        super(seed);
        this.seed = seed;
        this.orientation = orientation;
        this.blueLogicalTeam = blueLogicalTeam;
        this.redLogicalTeam = redLogicalTeam;
        this.captureTrace = captureTrace;
        traceDigest = sha256Digest();
        traceDigest.update(("simulationRandomFingerprintSchema="
                + SimulationRandomFingerprint.SCHEMA + '\n').getBytes(StandardCharsets.UTF_8));
    }

    public void context(Source source, TeamSide side, int tickSeconds) {
        this.source = source == null ? Source.NONE : source;
        this.side = side;
        this.tickSeconds = tickSeconds;
    }

    @Override
    protected int next(int bits) {
        if (finalizedTraceHash != null) {
            throw new IllegalStateException("Random trace was already finalized");
        }
        int value = super.next(bits);
        long index = ++drawIndex;
        String logicalTeam = logicalTeam(side);
        String canonical = "draw=" + index + '|'
                + source.name() + '|'
                + (side == null ? "NONE" : side.name()) + '|'
                + tickSeconds + '|'
                + "NEXT_BITS" + '|'
                + bits + '|'
                + value + '\n';
        traceDigest.update(canonical.getBytes(StandardCharsets.UTF_8));
        if (captureTrace) {
            trace.add(new Draw(
                    index,
                    source,
                    side,
                    tickSeconds,
                    "NEXT_BITS",
                    bits,
                    value,
                    orientation,
                    logicalTeam
            ));
        }
        return value;
    }

    public long drawCount() {
        return drawIndex;
    }

    public long seed() {
        return seed;
    }

    public List<Draw> trace() {
        return List.copyOf(trace);
    }

    public String traceHash() {
        if (finalizedTraceHash == null) {
            finalizedTraceHash = HexFormat.of().formatHex(traceDigest.digest());
        }
        return finalizedTraceHash;
    }

    public SimulationRandomFingerprint fingerprint() {
        return new SimulationRandomFingerprint(
                SimulationRandomFingerprint.SCHEMA,
                drawIndex,
                traceHash(),
                SimulationRandomFingerprint.TRACE_HASH_ALGORITHM);
    }

    private String logicalTeam(TeamSide value) {
        if (value == null) return "NONE";
        return value == TeamSide.BLUE ? blueLogicalTeam : redLogicalTeam;
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public enum Source {
        NONE,
        ECONOMY,
        LANE_PRESSURE,
        JUNGLE_GANK,
        ROAM,
        LANE_COMBAT,
        GENERIC_SKIRMISH,
        TEAMFIGHT,
        OBJECTIVE_FIGHT,
        OBJECTIVE_CAPTURE,
        STRUCTURE_PUSH,
        MIDGAME_MACRO,
        LATE_GAME_SIEGE,
        BASE_DEFENSE,
        NEXUS_FINISH
    }

    public record Draw(
            long drawIndex,
            Source resolverSource,
            TeamSide side,
            int tickSeconds,
            String drawType,
            int boundOrBits,
            int returnedValue,
            String orientation,
            String logicalTeamId
    ) {
    }
}
