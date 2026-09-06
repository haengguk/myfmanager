package com.lolfm.career;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Additive rules authority: existing domestic V2/V3 resources remain immutable. */
public final class CareerInternationalRules {
    public static final String VERSION = "career-international-rules-2026-v1";
    public static final String POLICY = "CAREER_INTERNATIONAL_GAME_POLICY_V1";
    public static final String ROFS = "INTERNATIONAL_ROFS_FIRST_PICK_OTHER_RED_LOSER_ROFS_V1";
    public static final String RODS = "INTERNATIONAL_RODS_BLUE_FIRST_PICK_LOSER_ROFS_V1";
    public static final List<String> COMPETITIONS = List.of("FIRST_STAND", "MSI", "EWC_LOL", "WORLDS");
    public static final List<String> REFERENCE_REGIONS = List.of("LCK", "LPL", "LEC", "LCS", "LCP", "CBLOL");
    public static final String RESOURCE = "/career/international-rules-2026-v1.json";
    public static final String RESOURCE_HASH = resourceHash(RESOURCE);
    public static final String VERSION_V2 = "career-international-rules-v2";
    public static final String POLICY_V2 = "CAREER_INTERNATIONAL_GAME_POLICY_V2";
    public static final String RESOURCE_V2 = "/career/international-rules-v2.json";
    public static final String RESOURCE_HASH_V2 = resourceHash(RESOURCE_V2);
    static boolean authority(String version, String hash, String policy) {
        return VERSION.equals(version) && RESOURCE_HASH.equals(hash) && POLICY.equals(policy)
                || VERSION_V2.equals(version) && RESOURCE_HASH_V2.equals(hash) && POLICY_V2.equals(policy);
    }
    static boolean supportedPolicy(String policy) { return POLICY.equals(policy) || POLICY_V2.equals(policy); }
    private CareerInternationalRules() {}
    private static String resourceHash(String resource) {
        try (var in = CareerInternationalRules.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("INTERNATIONAL_RULES_MISSING");
            return CareerCompetitionRules.sha256(in.readAllBytes());
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    static String hash(String value) { return CareerCompetitionRules.sha256(value.getBytes(StandardCharsets.UTF_8)); }
}
