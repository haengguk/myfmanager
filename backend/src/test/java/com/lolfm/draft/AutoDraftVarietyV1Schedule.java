package com.lolfm.draft;

import java.util.List;

/** Fixed before execution; these seeds are variety observations, never balance holdouts. */
public final class AutoDraftVarietyV1Schedule {
    public static final List<Long> SEEDS = List.of(
            Long.MIN_VALUE,
            -4_611_686_018_427_387_904L,
            -73L,
            0L,
            73L,
            2_026_082_600_001L,
            4_611_686_018_427_387_903L,
            Long.MAX_VALUE);

    public static final List<Fixture> FIXTURES = List.of(
            new Fixture("BFX_BLUE_BRO_RED", "BFX", "BRO"),
            new Fixture("BRO_BLUE_DK_RED", "BRO", "DK"),
            new Fixture("DK_BLUE_DNS_RED", "DK", "DNS"),
            new Fixture("DNS_BLUE_GEN_RED", "DNS", "GEN"),
            new Fixture("GEN_BLUE_HLE_RED", "GEN", "HLE"),
            new Fixture("HLE_BLUE_KRX_RED", "HLE", "KRX"),
            new Fixture("KRX_BLUE_KT_RED", "KRX", "KT"),
            new Fixture("KT_BLUE_NS_RED", "KT", "NS"),
            new Fixture("NS_BLUE_T1_RED", "NS", "T1"),
            new Fixture("T1_BLUE_BFX_RED", "T1", "BFX"));

    private AutoDraftVarietyV1Schedule() {
    }

    public record Fixture(String fixtureId, String blueTeamCode, String redTeamCode) {
    }
}
