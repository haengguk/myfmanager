package com.lolfm.application;

import java.time.Duration;
import org.springframework.stereotype.Component;

/** Dedicated ownership of process-local Series V1 operating limits. */
@Component
public final class SeriesLifecycleConfiguration {
    public static final int DEFAULT_MAXIMUM_SERIES = 32;
    public static final Duration DEFAULT_PARENT_TTL = Duration.ofMinutes(120);
    public static final Duration DEFAULT_CHILD_IDLE_TTL = Duration.ofMinutes(30);
    public static final Duration DEFAULT_SIMULATION_LEASE = Duration.ofMinutes(5);
    public static final int DEFAULT_MAXIMUM_COMMAND_RECEIPTS = 256;

    private final int maximumSeries;
    private final Duration parentTtl;
    private final Duration childIdleTtl;
    private final Duration simulationLease;
    private final int maximumCommandReceipts;

    public SeriesLifecycleConfiguration() {
        this(DEFAULT_MAXIMUM_SERIES, DEFAULT_PARENT_TTL, DEFAULT_CHILD_IDLE_TTL,
                DEFAULT_SIMULATION_LEASE, DEFAULT_MAXIMUM_COMMAND_RECEIPTS);
    }

    SeriesLifecycleConfiguration(
            int maximumSeries,
            Duration parentTtl,
            Duration childIdleTtl,
            Duration simulationLease,
            int maximumCommandReceipts
    ) {
        if (maximumSeries < 1 || maximumCommandReceipts < 1
                || parentTtl.isZero() || parentTtl.isNegative()
                || childIdleTtl.isZero() || childIdleTtl.isNegative()
                || simulationLease.isZero() || simulationLease.isNegative()
                || childIdleTtl.compareTo(parentTtl) > 0) {
            throw new IllegalArgumentException("Invalid Series lifecycle configuration");
        }
        this.maximumSeries = maximumSeries;
        this.parentTtl = parentTtl;
        this.childIdleTtl = childIdleTtl;
        this.simulationLease = simulationLease;
        this.maximumCommandReceipts = maximumCommandReceipts;
    }

    public int maximumSeries() { return maximumSeries; }
    public Duration parentTtl() { return parentTtl; }
    public Duration childIdleTtl() { return childIdleTtl; }
    public Duration simulationLease() { return simulationLease; }
    public int maximumCommandReceipts() { return maximumCommandReceipts; }

    public boolean canCreateCommandReceipt(int currentReceiptCount) {
        if (currentReceiptCount < 0) {
            throw new IllegalArgumentException("currentReceiptCount");
        }
        return currentReceiptCount < maximumCommandReceipts;
    }
}
