package com.lolfm.application;

import com.lolfm.champion.ChampionId;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.PlayerSnapshot;
import com.lolfm.player.PlayerId;
import com.lolfm.simulator.GameEndReason;
import com.lolfm.simulator.Lane;
import com.lolfm.simulator.StructuredMatchSimulationOutcome;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Deep-copy projection from mutable simulator playback objects to immutable V1 contracts. */
@Component
final class MatchEngineV1Projector {
    private final MatchEngineV1Canonicalizer canonicalizer;

    MatchEngineV1Projector(MatchEngineV1Canonicalizer canonicalizer) {
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
    }

    MatchEngineV1Output project(
            MatchEngineV1Input input,
            StructuredMatchSimulationOutcome outcome,
            SimulationExecutionProvenance provenance
    ) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(provenance, "provenance");
        if (outcome.timeline().getSnapshots().isEmpty()) {
            throw new IllegalArgumentException("Match Engine V1 requires a final snapshot");
        }
        List<MatchEngineV1Output.EventV1> events = outcome.timeline().getEvents().stream()
                .map(value -> event(input, value)).toList();
        List<MatchEngineV1Output.SnapshotV1> snapshots = outcome.timeline().getSnapshots().stream()
                .map(value -> snapshot(input, value)).toList();
        MatchEngineV1Output.TimelineV1 timeline = new MatchEngineV1Output.TimelineV1(
                MatchEngineV1Output.TimelineV1.SCHEMA, outcome.endedAtSeconds(),
                outcome.winnerSide(), outcome.endReason(), events, snapshots);
        String structuredTimelineHash = canonicalizer.hash(gameplayTimelineIdentity(timeline));
        MatchEngineV1Output.MatchResultSummaryV1 summary = summary(
                input, outcome, provenance, snapshots.getLast());
        String inputHash = input.inputHash();
        String outputHash = canonicalizer.hash(MatchEngineV1Output.outputHashMaterial(
                MatchEngineV1Output.SCHEMA, input.matchIdentity(),
                MatchEngineV1Policy.authoritative(), provenance.configurationHash(),
                inputHash, summary, input.finalDraft(), structuredTimelineHash, provenance));
        return new MatchEngineV1Output(
                MatchEngineV1Output.SCHEMA, input.matchIdentity(),
                MatchEngineV1Policy.authoritative(), provenance.configurationHash(), summary,
                input.finalDraft(), timeline, provenance, inputHash,
                MatchEngineV1Input.INPUT_HASH_ALGORITHM, provenance.timelineHash(),
                structuredTimelineHash, MatchEngineV1Canonicalizer.HASH_ALGORITHM,
                outputHash, MatchEngineV1Canonicalizer.HASH_ALGORITHM,
                MatchEngineV1Output.OUTPUT_HASH_SCOPE);
    }

    private MatchEngineV1Output.MatchResultSummaryV1 summary(
            MatchEngineV1Input input,
            StructuredMatchSimulationOutcome outcome,
            SimulationExecutionProvenance provenance,
            MatchEngineV1Output.SnapshotV1 last
    ) {
        List<MatchEngineV1Output.TeamResultV1> teams = List.of(
                result(last.blueTeam()), result(last.redTeam()));
        List<MatchEngineV1Output.PlayerResultV1> players = last.players().stream()
                .map(value -> new MatchEngineV1Output.PlayerResultV1(
                        value.playerId(), value.teamSide(), value.position(), value.championId(),
                        value.kills(), value.deaths(), value.assists(), value.cs(), value.gold(),
                        value.totalExperience(), value.level())).toList();
        return new MatchEngineV1Output.MatchResultSummaryV1(
                MatchEngineV1Output.MatchResultSummaryV1.SCHEMA,
                outcome.winnerSide(), outcome.endReason(), outcome.endedAtSeconds(), teams, players,
                input.finalDraft().finalDraftHash(), input.finalDraft().finalAssignmentHash(),
                provenance.runtimeProfileId().name(), provenance.configurationHash(),
                provenance.resourceProvenance().resourceProvenanceHash(),
                provenance.replayProvenanceHash());
    }

    private static MatchEngineV1Output.TeamResultV1 result(
            MatchEngineV1Output.TeamStateV1 value
    ) {
        return new MatchEngineV1Output.TeamResultV1(
                value.teamIdentity(), value.teamSide(), value.kills(), value.gold(),
                value.dragons(), value.hasDragonSoul(), value.hasBaronBuff(),
                value.hasElderBuff(), value.towersDestroyed(), value.inhibitorsRemaining(),
                value.nexusTurretsRemaining(), value.nexusAlive(), value.alivePlayers());
    }

    private MatchEngineV1Output.EventV1 event(MatchEngineV1Input input, MatchEvent source) {
        PlayerId killer = participant(source.getKillerPlayerId(), input);
        PlayerId victim = participant(source.getVictimPlayerId(), input);
        List<PlayerId> assistants = source.getAssistPlayerIds().stream()
                .map(value -> participant(value, input)).toList();
        TeamSide actorSide = actorSide(source, killer, input);
        com.lolfm.domain.Position actorPosition = killer == null
                ? source.getRoam() == null ? null : source.getRoam().roamerPosition()
                : input.player(killer).position();
        Lane lane = eventLane(source);
        List<ChampionId> assistantChampions = assistants.stream()
                .map(value -> champion(input, value)).toList();
        LinkedHashMap<String, Object> structured = new LinkedHashMap<>();
        put(structured, "laneCombat", source.getLaneCombat());
        put(structured, "jungleGank", source.getJungleGank());
        put(structured, "counterGank", source.getCounterGank());
        put(structured, "roam", source.getRoam());
        put(structured, "objectivePriorityDecision", source.getObjectivePriorityDecision());
        put(structured, "objectiveDecision", source.getObjectiveDecision());
        put(structured, "midGameMacroDecision", source.getMidGameMacroDecision());
        put(structured, "midGameMacroAction", source.getMidGameMacroAction());
        put(structured, "outerTurretSiege", source.getOuterTurretSiege());
        put(structured, "matchPhaseChange", source.getMatchPhaseChange());
        put(structured, "lateGameDecision", source.getLateGameDecision());
        put(structured, "progressionEvent", source.getProgressionEvent());
        return new MatchEngineV1Output.EventV1(
                source.getTimeSeconds(), source.getType(), actorSide, actorPosition, lane,
                killer, victim, assistants,
                killer == null ? null : champion(input, killer),
                victim == null ? null : champion(input, victim),
                assistantChampions, source.getCombatSource(), source.getStructureActionSource(),
                source.getStructureKind(), source.getStructureTowerTier(),
                source.getStructureAttackingSide(), source.getStructureDefendingSide(),
                source.getGoldAmount(), source.getBountyRawBeforePayout(), source.getMessage(),
                canonicalizer.immutableObject(structured));
    }

    private MatchEngineV1Output.SnapshotV1 snapshot(
            MatchEngineV1Input input, MatchSnapshot source
    ) {
        List<MatchEngineV1Output.PlayerStateV1> players = source.getPlayerSnapshots().stream()
                .map(value -> player(input, value)).sorted(java.util.Comparator
                        .comparing(MatchEngineV1Output.PlayerStateV1::teamSide)
                        .thenComparing(MatchEngineV1Output.PlayerStateV1::position)).toList();
        LinkedHashMap<String, Object> state = new LinkedHashMap<>();
        state.put("elderAlive", source.isElderAlive());
        put(state, "laneSnapshots", source.getLaneSnapshots());
        put(state, "objectivePriority", source.getObjectivePriority());
        put(state, "lanePhase", source.getLanePhase());
        put(state, "midGameMacro", source.getMidGameMacro());
        put(state, "objectiveDecision", source.getObjectiveDecision());
        put(state, "lateGame", source.getLateGame());
        put(state, "progression", source.getProgression());
        return new MatchEngineV1Output.SnapshotV1(
                source.getTimeSeconds(), team(input, source, TeamSide.BLUE),
                team(input, source, TeamSide.RED), players,
                canonicalizer.immutableObject(state));
    }

    private MatchEngineV1Output.PlayerStateV1 player(
            MatchEngineV1Input input, PlayerSnapshot source
    ) {
        MatchEngineV1Input.PlayerInput player = input.player(
                source.getTeamSide(), source.getPosition());
        MatchEngineV1Input.ChampionAssignmentInput assignment = input.assignment(
                source.getTeamSide(), source.getPosition());
        if (!assignment.championId().value().equals(source.getChampionId())) {
            throw new IllegalArgumentException("MATCH_ENGINE_V1_SNAPSHOT_CHAMPION_MISMATCH");
        }
        return new MatchEngineV1Output.PlayerStateV1(
                player.playerId(), source.getTeamSide(), source.getPosition(),
                assignment.championId(), source.getKills(), source.getDeaths(),
                source.getAssists(), source.getCs(), source.getGold(), source.isAlive(),
                source.getRespawnAtSeconds(), source.getRespawnRemainingSeconds(),
                source.isCanFarm(), source.getFarmResumeAtSeconds(),
                source.getFarmReturnSecondsRemaining(), source.getShutdownBountyGold(),
                source.getBountyProgress(), source.getActivityType(),
                source.getActivityOriginLane(), source.getActivityTargetLane(),
                source.getActivityUntilSeconds(), source.getTotalExperience(), source.getLevel(),
                source.getItemStage().name(), canonicalizer.immutableObject(
                        Map.of("progression", source.getProgression(),
                                "champion", source.getChampion())));
    }

    private static MatchEngineV1Output.TeamStateV1 team(
            MatchEngineV1Input input, MatchSnapshot source, TeamSide side
    ) {
        boolean blue = side == TeamSide.BLUE;
        MatchEngineV1Input.TeamInput team = blue ? input.blueTeam() : input.redTeam();
        return new MatchEngineV1Output.TeamStateV1(
                team.teamIdentity(), side,
                blue ? source.getBlueKills() : source.getRedKills(),
                blue ? source.getBlueGold() : source.getRedGold(),
                blue ? source.getBlueDragons() : source.getRedDragons(),
                blue ? source.isBlueHasDragonSoul() : source.isRedHasDragonSoul(),
                blue ? source.isBlueHasBaronBuff() : source.isRedHasBaronBuff(),
                blue ? source.isBlueHasElderBuff() : source.isRedHasElderBuff(),
                blue ? source.getBlueElderBuffRemainingSeconds()
                        : source.getRedElderBuffRemainingSeconds(),
                blue ? source.getBlueTowersDestroyed() : source.getRedTowersDestroyed(),
                blue ? source.getBlueInhibitorsRemaining() : source.getRedInhibitorsRemaining(),
                blue ? source.getBlueNexusTurretsRemaining()
                        : source.getRedNexusTurretsRemaining(),
                blue ? source.isBlueNexusAlive() : source.isRedNexusAlive(),
                blue ? source.getBlueAlivePlayers() : source.getRedAlivePlayers());
    }

    private static PlayerId participant(String value, MatchEngineV1Input input) {
        if (value == null) return null;
        PlayerId playerId = new PlayerId(value);
        input.player(playerId);
        return playerId;
    }

    private static ChampionId champion(MatchEngineV1Input input, PlayerId playerId) {
        MatchEngineV1Input.PlayerInput player = input.player(playerId);
        return input.assignment(player.teamSide(), player.position()).championId();
    }

    private static TeamSide actorSide(
            MatchEvent event, PlayerId killer, MatchEngineV1Input input
    ) {
        if (killer != null) return input.player(killer).teamSide();
        if (event.getStructureAttackingSide() != null) return event.getStructureAttackingSide();
        if (event.getLaneCombat() != null) return event.getLaneCombat().initiatorSide();
        if (event.getJungleGank() != null) return event.getJungleGank().gankingSide();
        if (event.getCounterGank() != null) return event.getCounterGank().attackingSide();
        if (event.getRoam() != null) return event.getRoam().roamingSide();
        if (event.getObjectiveDecision() != null) return event.getObjectiveDecision().initiativeSide();
        if (event.getObjectivePriorityDecision() != null) {
            return event.getObjectivePriorityDecision().selectedSide();
        }
        if (event.getMidGameMacroDecision() != null) {
            return event.getMidGameMacroDecision().teamSide();
        }
        if (event.getMidGameMacroAction() != null) return event.getMidGameMacroAction().teamSide();
        if (event.getLateGameDecision() != null) return event.getLateGameDecision().initiativeSide();
        if (event.getProgressionEvent() != null) return event.getProgressionEvent().side();
        return null;
    }

    private static Lane eventLane(MatchEvent event) {
        if (event.getStructureLane() != null) return event.getStructureLane();
        if (event.getLaneCombat() != null) return event.getLaneCombat().lane();
        if (event.getJungleGank() != null) return event.getJungleGank().targetLane();
        if (event.getCounterGank() != null) return event.getCounterGank().targetLane();
        if (event.getRoam() != null) return event.getRoam().targetLane();
        if (event.getOuterTurretSiege() != null) return event.getOuterTurretSiege().lane();
        if (event.getMidGameMacroDecision() != null) return event.getMidGameMacroDecision().targetLane();
        if (event.getMidGameMacroAction() != null) return event.getMidGameMacroAction().targetLane();
        if (event.getLateGameDecision() != null) return event.getLateGameDecision().targetLane();
        return null;
    }

    private Map<String, Object> gameplayTimelineIdentity(MatchEngineV1Output.TimelineV1 timeline) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", timeline.schemaVersion());
        result.put("durationSeconds", timeline.durationSeconds());
        result.put("winner", timeline.winner() == null ? "NONE" : timeline.winner().name());
        result.put("endReason", timeline.endReason().name());
        result.put("events", timeline.events().stream().map(this::eventIdentity).toList());
        result.put("snapshots", timeline.snapshots());
        return result;
    }

    private Map<String, Object> eventIdentity(MatchEngineV1Output.EventV1 event) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("timeSeconds", event.timeSeconds());
        result.put("eventType", event.eventType());
        put(result, "actorSide", event.actorSide());
        put(result, "actorPosition", event.actorPosition());
        put(result, "lane", event.lane());
        put(result, "killerPlayerId", event.killerPlayerId());
        put(result, "victimPlayerId", event.victimPlayerId());
        result.put("assistantPlayerIds", event.assistantPlayerIds());
        put(result, "killerChampionId", event.killerChampionId());
        put(result, "victimChampionId", event.victimChampionId());
        result.put("assistantChampionIds", event.assistantChampionIds());
        put(result, "combatSource", event.combatSource());
        put(result, "structureActionSource", event.structureActionSource());
        put(result, "structureKind", event.structureKind());
        put(result, "structureTowerTier", event.structureTowerTier());
        put(result, "structureAttackingSide", event.structureAttackingSide());
        put(result, "structureDefendingSide", event.structureDefendingSide());
        result.put("goldAmount", event.goldAmount());
        result.put("bountyRawBeforePayout", event.bountyRawBeforePayout());
        result.put("structuredData", event.structuredData());
        return result;
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }
}
