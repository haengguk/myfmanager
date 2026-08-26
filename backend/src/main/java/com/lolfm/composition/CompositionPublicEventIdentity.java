package com.lolfm.composition;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.simulator.Lane;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Structured, display-text-free identity for a public event bound to a Composition attempt.
 * The ordinal is the event's deterministic index in the match timeline.
 */
public record CompositionPublicEventIdentity(
        int eventTimeSeconds,
        int eventOrdinal,
        String actionId,
        String parentActionId,
        MatchEventType eventType,
        String combatSource,
        Lane combatLane,
        String structuredPayloadSha256
) {
    public CompositionPublicEventIdentity {
        if (eventTimeSeconds < 0 || eventOrdinal < 0) {
            throw new IllegalArgumentException("Public event time/ordinal must be non-negative");
        }
        Objects.requireNonNull(eventType, "eventType");
        requireHash(structuredPayloadSha256);
    }

    public static CompositionPublicEventIdentity from(MatchEvent event, int eventOrdinal) {
        Objects.requireNonNull(event, "event");
        String source = event.getCombatSource() == null ? null : event.getCombatSource().name();
        StringBuilder payload = new StringBuilder("schema=COMPOSITION_PUBLIC_EVENT_PAYLOAD_V2\n");
        append(payload, "time", event.getTimeSeconds());
        append(payload, "type", event.getType());
        append(payload, "actionId", event.getActionId());
        append(payload, "parentActionId", event.getParentActionId());
        append(payload, "actorPlayerId", event.getActorPlayerId());
        append(payload, "killerPlayerId", event.getKillerPlayerId());
        append(payload, "victimPlayerId", event.getVictimPlayerId());
        append(payload, "assistPlayerIds", event.getAssistPlayerIds());
        append(payload, "combatSource", event.getCombatSource());
        append(payload, "combatLane", event.getCombatLane());
        append(payload, "goldAmount", event.getGoldAmount());
        append(payload, "laneCombat", event.getLaneCombat());
        append(payload, "jungleGank", event.getJungleGank());
        append(payload, "counterGank", event.getCounterGank());
        append(payload, "roam", event.getRoam());
        append(payload, "objectivePriorityDecision", event.getObjectivePriorityDecision());
        append(payload, "objectiveDecision", event.getObjectiveDecision());
        append(payload, "objectiveFight", event.getObjectiveFight());
        append(payload, "structureActionSource", event.getStructureActionSource());
        append(payload, "midGameMacroDecision", event.getMidGameMacroDecision());
        append(payload, "midGameMacroAction", event.getMidGameMacroAction());
        append(payload, "structureKind", event.getStructureKind());
        append(payload, "structureTowerTier", event.getStructureTowerTier());
        append(payload, "structureLane", event.getStructureLane());
        append(payload, "structureAttackingSide", event.getStructureAttackingSide());
        append(payload, "structureDefendingSide", event.getStructureDefendingSide());
        append(payload, "outerTurretSiege", event.getOuterTurretSiege());
        append(payload, "structureAction", event.getStructureAction());
        append(payload, "matchPhaseChange", event.getMatchPhaseChange());
        append(payload, "lateGameDecision", event.getLateGameDecision());
        append(payload, "progressionEvent", event.getProgressionEvent());
        append(payload, "killEvent", event.getKillEvent());
        append(payload, "assistEvent", event.getAssistEvent());
        return new CompositionPublicEventIdentity(event.getTimeSeconds(), eventOrdinal,
                event.getActionId(), event.getParentActionId(), event.getType(), source,
                event.getCombatLane(), sha256(payload.toString()));
    }

    /** True when only the deterministic timeline ordinal differs. */
    public boolean sameStructuredEventExceptOrdinal(CompositionPublicEventIdentity other) {
        return other != null
                && eventTimeSeconds == other.eventTimeSeconds
                && Objects.equals(actionId, other.actionId)
                && Objects.equals(parentActionId, other.parentActionId)
                && eventType == other.eventType
                && Objects.equals(combatSource, other.combatSource)
                && combatLane == other.combatLane
                && structuredPayloadSha256.equals(other.structuredPayloadSha256);
    }

    private static void append(StringBuilder target, String name, Object value) {
        String encoded = Objects.toString(value, "<null>");
        target.append(name).append('=').append(encoded.length()).append(':').append(encoded).append('\n');
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static void requireHash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Structured public payload hash is invalid");
        }
    }
}
