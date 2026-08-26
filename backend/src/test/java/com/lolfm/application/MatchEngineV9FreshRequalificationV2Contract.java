package com.lolfm.application;

import java.util.Set;

/** Explicit V2 facade; the predecessor V1 artifacts remain read-only on disk. */
public final class MatchEngineV9FreshRequalificationV2Contract {
    public static final String CONTRACT_SCHEMA =
            MatchEngineV9FreshRequalificationContract.CONTRACT_SCHEMA;
    public static final String SCHEDULE_VERSION =
            MatchEngineV9FreshRequalificationContract.SCHEDULE_VERSION;
    public static final String SEED_NAMESPACE =
            MatchEngineV9FreshRequalificationContract.SEED_NAMESPACE;
    public static final String DRAFT_REUSE_POLICY =
            MatchEngineV9FreshRequalificationContract.DRAFT_REUSE_POLICY;

    private MatchEngineV9FreshRequalificationV2Contract() { }

    public static MatchEngineV9FreshRequalificationContract.Schedule schedule() {
        return MatchEngineV9FreshRequalificationContract.schedule();
    }

    public static MatchEngineV9FreshRequalificationContract.SeedOverlapAudit requireNoSeedOverlap(
            Set<Long> historicalSeeds) {
        return MatchEngineV9FreshRequalificationContract.requireNoSeedOverlap(
                schedule(), historicalSeeds);
    }
}
