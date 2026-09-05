package com.lolfm.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.league.LeagueIdentity;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionSelectionMode;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.draft.DraftActionType;
import com.lolfm.draft.PlayerManualSelectionEvidence;
import com.lolfm.draft.PlayerSelectionLegality;
import com.lolfm.simulator.TeamSide;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Compact immutable-aggregate checkpoint for League and Career-owned Series. */
@Component
final class JdbcLeagueBoundSeriesCheckpointAdapter
        implements LeagueBoundSeriesPersistencePort {
    static final String SCHEMA = "AI_LEAGUE_BOUND_SERIES_CHECKPOINT_V1";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    JdbcLeagueBoundSeriesCheckpointAdapter(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            Clock clock
    ) {
        this.jdbc = jdbc;
        SimpleModule durableEvidence = new SimpleModule(
                "AI_LEAGUE_DURABLE_PLAYER_EVIDENCE_V1");
        durableEvidence.addSerializer(PlayerManualSelectionEvidence.class,
                new PlayerEvidenceSerializer());
        durableEvidence.addDeserializer(PlayerManualSelectionEvidence.class,
                new PlayerEvidenceDeserializer());
        durableEvidence.addSerializer(MatchChampionAssignments.class,
                new MatchAssignmentsSerializer());
        durableEvidence.addDeserializer(MatchChampionAssignments.class,
                new MatchAssignmentsDeserializer());
        this.mapper = mapper.copy().registerModule(durableEvidence)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
        this.clock = clock;
    }

    @Override
    public void save(SeriesAggregate aggregate) {
        if (!aggregate.origin().durableBound()) return;
        SeriesAggregate durable = sanitize(aggregate);
        String value = write(durable);
        String hash = LeagueIdentity.sha256(
                "checkpointSchema=" + SCHEMA + '\n' + "checkpointJson=" + value + '\n');
        OffsetDateTime now = clock.instant().atOffset(ZoneOffset.UTC);
        String checkpointTable = durable.origin() == SeriesOrigin.LEAGUE_BOUND
                ? "league_player_series_checkpoint"
                : "career_competition_series_checkpoint";
        int updated = jdbc.update("""
                UPDATE %s
                SET checkpoint_json = ?, checkpoint_hash = ?, series_revision = ?,
                    series_status = ?, updated_at = ?
                WHERE binding_hash = ? AND series_id = ?
                  AND series_revision <= ?
                """.formatted(checkpointTable), value, hash, durable.revision(),
                durable.status().name(), now,
                durable.leagueBindingHash(), durable.seriesId(), durable.revision());
        if (updated == 0) {
            try {
                jdbc.update("""
                        INSERT INTO %s(
                          binding_hash, series_id, checkpoint_schema, checkpoint_json,
                          checkpoint_hash, series_revision, series_status, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """.formatted(checkpointTable), durable.leagueBindingHash(),
                        durable.seriesId(), SCHEMA,
                        value, hash, durable.revision(), durable.status().name(), now);
            } catch (org.springframework.dao.DuplicateKeyException duplicate) {
                throw new IllegalStateException("LEAGUE_SERIES_CHECKPOINT_STALE_WRITE",
                        duplicate);
            }
        }
    }

    @Override
    public Optional<SeriesAggregate> load(String seriesId) {
        List<Row> rows = jdbc.query("""
                SELECT c.binding_hash, c.checkpoint_schema, c.checkpoint_json,
                       c.checkpoint_hash, c.series_revision, c.series_status,
                       b.binding_hash, 'LEAGUE_BOUND'
                FROM league_player_series_checkpoint c
                JOIN league_player_binding b ON b.binding_hash = c.binding_hash
                WHERE c.series_id = ?
                UNION ALL
                SELECT c.binding_hash, c.checkpoint_schema, c.checkpoint_json,
                       c.checkpoint_hash, c.series_revision, c.series_status,
                       b.binding_hash, 'COMPETITION_BOUND'
                FROM career_competition_series_checkpoint c
                JOIN career_competition_series_binding b
                  ON b.binding_hash = c.binding_hash
                WHERE c.series_id = ?
                """, (result, row) -> new Row(result.getString(1), result.getString(2),
                result.getString(3), result.getString(4), result.getLong(5),
                result.getString(6), result.getString(7),
                SeriesOrigin.valueOf(result.getString(8))), seriesId, seriesId);
        if (rows.isEmpty()) return Optional.empty();
        Row row = rows.getFirst();
        String expected = LeagueIdentity.sha256(
                "checkpointSchema=" + row.schema() + '\n'
                        + "checkpointJson=" + row.json() + '\n');
        if (!SCHEMA.equals(row.schema()) || !expected.equals(row.hash())
                || !row.bindingHash().equals(row.authorityBindingHash())) {
            throw new IllegalStateException("LEAGUE_SERIES_CHECKPOINT_INTEGRITY_FAILED");
        }
        SeriesAggregate aggregate = read(row.json());
        if (!aggregate.seriesId().equals(seriesId)
                || !aggregate.leagueBindingHash().equals(row.bindingHash())
                || aggregate.revision() != row.revision()
                || !aggregate.status().name().equals(row.status())
                || aggregate.origin() != row.origin()) {
            throw new IllegalStateException("BOUND_SERIES_CHECKPOINT_BINDING_MISMATCH");
        }
        SeriesAggregate recovered = recoverLostReservation(aggregate);
        if (recovered != aggregate) save(recovered);
        return Optional.of(recovered);
    }

    static SeriesAggregate recoverLostReservation(SeriesAggregate aggregate) {
        SeriesGame game = aggregate.currentGame();
        if (game.reservation() == null) return aggregate;
        SeriesSimulationReservation lost = game.reservation();
        SeriesGame released = new SeriesGame(game.gameId(), game.gameNumber(),
                game.blueTeamCode(), game.redTeamCode(), game.controlledSide(),
                game.matchSeed(), game.historyBefore(), game.historyBeforeHash(),
                SeriesGameStatus.SIMULATION_FAILED_RETRYABLE,
                "PROCESS_RESTART_DURING_SIMULATION", game.childGeneration(),
                game.childDraft(), null, game.completedDraft(), game.resultSummary(),
                game.receipt());
        LinkedHashMap<String, SeriesCommandReceipt> receipts = new LinkedHashMap<>(
                aggregate.commandReceipts());
        SeriesCommandReceipt command = receipts.get(lost.commandId());
        if (command != null && command.completion() == SeriesCommandCompletion.IN_PROGRESS) {
            receipts.put(command.commandId(), command.completed(
                    SeriesCommandCompletion.FAILED, aggregate.revision(),
                    aggregate.status(), released.status(),
                    "PROCESS_RESTART_DURING_SIMULATION",
                    "SERIES_SIMULATION_PROCESS_RESTART", 409, true));
        }
        return aggregate.copy(aggregate.revision(), aggregate.status(),
                aggregate.terminalReason(), aggregate.score(), replaceLast(
                aggregate.games(), released), aggregate.consumedPicks(),
                aggregate.historyHash(), aggregate.winnerTeamCode(),
                aggregate.lastActivityAt(), aggregate.expiresAt(), receipts);
    }

    private static SeriesAggregate sanitize(SeriesAggregate aggregate) {
        List<SeriesGame> games = aggregate.games().stream().map(game -> new SeriesGame(
                game.gameId(), game.gameNumber(), game.blueTeamCode(), game.redTeamCode(),
                game.controlledSide(), game.matchSeed(), game.historyBefore(),
                game.historyBeforeHash(), game.status(), game.reason(),
                game.childGeneration(), sanitize(game.childDraft()), game.reservation(),
                game.completedDraft(), game.resultSummary(), game.receipt())).toList();
        LinkedHashMap<String, SeriesCommandReceipt> receipts = new LinkedHashMap<>();
        aggregate.commandReceipts().forEach((key, value) -> receipts.put(key,
                new SeriesCommandReceipt(value.commandId(), value.commandType(),
                        value.payloadHash(), value.completion(),
                        value.resultingSeriesRevision(), value.resultingSeriesStatus(),
                        value.gameNumber(), value.gameId(), value.resultingGameStatus(),
                        value.resultingDraftRevision(), value.childId(),
                        value.childGeneration(), sanitize(value.childSnapshot()),
                        value.resultIdentity(), value.errorCode(), value.httpStatus(),
                        value.retryable())));
        return new SeriesAggregate(aggregate.seriesId(), aggregate.revision(),
                aggregate.status(), aggregate.terminalReason(), aggregate.format(),
                aggregate.teamACode(), aggregate.teamBCode(), aggregate.managedTeamCode(),
                aggregate.game1BlueTeamCode(), aggregate.canonicalRootSeed(),
                aggregate.rootSeed(), aggregate.score(), games, aggregate.consumedPicks(),
                aggregate.historyHash(), aggregate.winnerTeamCode(), aggregate.createdAt(),
                aggregate.lastActivityAt(), aggregate.expiresAt(), receipts,
                aggregate.origin(), aggregate.leagueBindingHash(),
                aggregate.leagueSeedAnchorTeamCode());
    }

    private static SeriesChildDraft sanitize(SeriesChildDraft child) {
        if (child == null) return null;
        return new SeriesChildDraft(child.childId(), child.generation(), child.revision(),
                child.status(), child.createdAt(), child.lastActivityAt(), child.expiresAt(),
                child.progress(), null, child.completionBinding(), null);
    }

    private String write(SeriesAggregate aggregate) {
        try {
            return mapper.writeValueAsString(aggregate);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("LEAGUE_SERIES_CHECKPOINT_WRITE_FAILED", exception);
        }
    }

    private SeriesAggregate read(String value) {
        try {
            return mapper.readValue(value, SeriesAggregate.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("LEAGUE_SERIES_CHECKPOINT_READ_FAILED", exception);
        }
    }

    private static List<SeriesGame> replaceLast(List<SeriesGame> values, SeriesGame game) {
        ArrayList<SeriesGame> next = new ArrayList<>(values);
        next.set(next.size() - 1, game);
        return next;
    }

    private record Row(
            String bindingHash, String schema, String json, String hash,
            long revision, String status, String authorityBindingHash,
            SeriesOrigin origin
    ) {}

    /** Public API hides clientActionId; this checkpoint-only codec retains it. */
    private static final class PlayerEvidenceSerializer
            extends JsonSerializer<PlayerManualSelectionEvidence> {
        @Override
        public void serialize(
                PlayerManualSelectionEvidence value,
                JsonGenerator output,
                SerializerProvider serializers
        ) throws IOException {
            output.writeStartObject();
            output.writeStringField("controlledSide", value.controlledSide().name());
            output.writeNumberField("turn", value.turn());
            output.writeStringField("actionType", value.actionType().name());
            output.writeStringField("championId", value.championId().value());
            output.writeStringField("stateBeforeHash", value.stateBeforeHash());
            output.writeStringField("selectableSetIdentity", value.selectableSetIdentity());
            output.writeStringField("legalityResult", value.legalityResult().name());
            output.writeStringField("clientActionId", value.clientActionId());
            output.writeEndObject();
        }
    }

    private static final class PlayerEvidenceDeserializer
            extends JsonDeserializer<PlayerManualSelectionEvidence> {
        @Override
        public PlayerManualSelectionEvidence deserialize(
                JsonParser input,
                DeserializationContext context
        ) throws IOException {
            JsonNode value = input.getCodec().readTree(input);
            return new PlayerManualSelectionEvidence(
                    TeamSide.valueOf(value.required("controlledSide").asText()),
                    value.required("turn").asInt(),
                    DraftActionType.valueOf(value.required("actionType").asText()),
                    new ChampionId(value.required("championId").asText()),
                    value.required("stateBeforeHash").asText(),
                    value.required("selectableSetIdentity").asText(),
                    PlayerSelectionLegality.valueOf(
                            value.required("legalityResult").asText()),
                    value.required("clientActionId").asText());
        }
    }

    private static final class MatchAssignmentsSerializer
            extends JsonSerializer<MatchChampionAssignments> {
        @Override
        public void serialize(
                MatchChampionAssignments value,
                JsonGenerator output,
                SerializerProvider serializers
        ) throws IOException {
            output.writeStartObject();
            output.writeStringField("selectionMode", value.selectionMode().name());
            output.writeArrayFieldStart("values");
            value.asMap().values().stream().sorted(java.util.Comparator.comparing(
                    assignment -> assignment.playerKey().toString())).forEach(assignment -> {
                        try {
                            serializers.defaultSerializeValue(assignment, output);
                        } catch (IOException exception) {
                            throw new java.io.UncheckedIOException(exception);
                        }
                    });
            output.writeEndArray();
            output.writeEndObject();
        }
    }

    private static final class MatchAssignmentsDeserializer
            extends JsonDeserializer<MatchChampionAssignments> {
        @Override
        public MatchChampionAssignments deserialize(
                JsonParser input,
                DeserializationContext context
        ) throws IOException {
            JsonNode value = input.getCodec().readTree(input);
            ArrayList<ChampionAssignment> assignments = new ArrayList<>();
            for (JsonNode assignment : value.required("values")) {
                assignments.add(input.getCodec().treeToValue(
                        assignment, ChampionAssignment.class));
            }
            return new MatchChampionAssignments(assignments,
                    ChampionSelectionMode.valueOf(
                            value.required("selectionMode").asText()));
        }
    }
}
