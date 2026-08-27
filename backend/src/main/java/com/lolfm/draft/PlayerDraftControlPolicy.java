package com.lolfm.draft;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Frozen V1 policy for one-side player control with authoritative Auto Draft opposition. */
public final class PlayerDraftControlPolicy {
    public static final String POLICY_ID = "PLAYER_CONTROLLED_DRAFT_V1";
    public static final String APPROVED_POLICY_SHA256 =
            "8f6488f07c44a6529e88bd022fff3124458a8237cc919bd7dd3e140eaa4a0752";
    public static final String POLICY_HASH = APPROVED_POLICY_SHA256;
    public static final String EVIDENCE_SCHEMA = "PLAYER_CONTROLLED_DRAFT_EVIDENCE_V1";
    public static final String HASH_ALGORITHM =
            "SHA256_UTF8_ORDERED_PLAYER_CONTROL_EVIDENCE_LINES_EXCLUDING_REQUEST_IDS_V1";

    private PlayerDraftControlPolicy() {
    }

    static {
        if (!APPROVED_POLICY_SHA256.equals(hash(canonicalPolicy()))) {
            throw new IllegalStateException("Player Draft control policy hash drift");
        }
    }

    public static String canonicalPolicy() {
        AutoDraftSelectionPolicy auto = AutoDraftSelectionPolicy.production();
        return "policySchema=PLAYER_CONTROLLED_DRAFT_POLICY_V1\n"
                + "policyId=" + POLICY_ID + '\n'
                + "seriesGameNumber=1\n"
                + "hardFearlessHistory=EMPTY\n"
                + "controlledSide=EXACTLY_ONE_OF_BLUE_OR_RED\n"
                + "playerAuthority=ALL_CONTROLLED_SIDE_TURNS\n"
                + "manualSelection=ANY_DOMAIN_LEGAL_FUTURE_COMPLETABLE_CHAMPION\n"
                + "playerSelectionRandomDraws=0\n"
                + "draftCompletionAutoSimulates=false\n"
                + "autoPolicyId=" + auto.policyId() + '\n'
                + "autoPolicyHash=" + auto.policyHash() + '\n';
    }

    static String hash(String canonical) {
        if (!canonical.endsWith("\n")) {
            throw new IllegalArgumentException("Canonical evidence requires trailing newline");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
