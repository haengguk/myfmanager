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

    public static String autoPolicyId(java.util.List<DraftTurnControlEvidence> turns) {
        var ids=turns.stream().filter(t->t.authority()==DraftDecisionAuthority.AI)
                .map(t->t.autoSelectionTrace().policyId()).distinct().toList();
        if(ids.size()>1)throw new IllegalArgumentException("Mixed AI policy versions");
        String id=ids.isEmpty()?AutoDraftSelectionPolicy.POLICY_ID:ids.getFirst();
        AutoDraftSelectionPolicy.resolve(id); return id;
    }
    public static String id(java.util.List<DraftTurnControlEvidence> turns) {
        return idForAuto(autoPolicyId(turns));
    }
    public static String policyHash(java.util.List<DraftTurnControlEvidence> turns) {
        return hashForAuto(autoPolicyId(turns));
    }
    public static String idForAuto(String autoId) {
        AutoDraftSelectionPolicy.resolve(autoId);
        return autoId.equals(AutoDraftSelectionPolicy.POLICY_ID)?POLICY_ID:autoId.equals("AUTO_DRAFT_ABILITY_V2")?"PLAYER_CONTROLLED_DRAFT_ABILITY_V2":"PLAYER_CONTROLLED_DRAFT_ABILITY_V1";
    }
    public static String hashForAuto(String autoId) {
        if(idForAuto(autoId).equals(POLICY_ID))return POLICY_HASH;
        return hash(canonicalPolicy().replace(POLICY_ID,idForAuto(autoId))
                .replace(AutoDraftSelectionPolicy.POLICY_ID,AutoDraftSelectionPolicy.resolve(autoId).policyId())
                .replace(AutoDraftSelectionPolicy.APPROVED_POLICY_SHA256,AutoDraftSelectionPolicy.resolve(autoId).policyHash()));
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
