package com.lolfm.league;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import org.springframework.stereotype.Component;

/** Durable, globally serialized V1 API command idempotency boundary. */
@Component
final class LeagueApiCommandStore {
    static final String RESPONSE_MARKER =
            "{\"schemaVersion\":\"AI_LEAGUE_API_COMMAND_RESULT_V1\"}";

    private final LeagueRelationalStore store;

    LeagueApiCommandStore(LeagueRelationalStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    Result execute(
            String commandId,
            String commandType,
            String payloadHash,
            String leagueId,
            String seasonId,
            String fixtureId,
            IntSupplier action
    ) {
        require(commandId, commandType, payloadHash);
        Objects.requireNonNull(action, "action");
        return store.transactions().execute(ignored -> {
            store.lockApiCommands();
            List<Row> prior = store.jdbc().query("""
                    SELECT command_type, payload_hash, lifecycle_status, http_status
                    FROM league_api_command WHERE client_command_id = ?
                    """, (result, row) -> new Row(result.getString(1),
                    result.getString(2), result.getString(3),
                    (Integer) result.getObject(4)), commandId);
            if (!prior.isEmpty()) {
                Row row = prior.getFirst();
                if (!row.commandType().equals(commandType)
                        || !row.payloadHash().equals(payloadHash)) {
                    throw new CommandConflict("LEAGUE_COMMAND_ID_PAYLOAD_CONFLICT");
                }
                if (!"COMPLETED".equals(row.status()) || row.httpStatus() == null) {
                    throw new CommandConflict("LEAGUE_COMMAND_IN_PROGRESS");
                }
                return new Result(true, row.httpStatus());
            }
            OffsetDateTime now = store.now();
            store.jdbc().update("""
                    INSERT INTO league_api_command(
                      client_command_id, command_type, payload_hash, league_id,
                      season_id, fixture_id, lifecycle_status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'IN_PROGRESS', ?)
                    """, commandId, commandType, payloadHash, leagueId, seasonId,
                    fixtureId, now);
            int httpStatus = action.getAsInt();
            int updated = store.jdbc().update("""
                    UPDATE league_api_command SET lifecycle_status = 'COMPLETED',
                      http_status = ?, response_json = ?, completed_at = ?
                    WHERE client_command_id = ? AND lifecycle_status = 'IN_PROGRESS'
                    """, httpStatus, RESPONSE_MARKER, store.now(), commandId);
            if (updated != 1) {
                throw new IllegalStateException("LEAGUE_API_COMMAND_COMMIT_FAILED");
            }
            return new Result(false, httpStatus);
        });
    }

    private static void require(String commandId, String commandType, String payloadHash) {
        if (commandId == null || !commandId.matches("[0-9A-Za-z][0-9A-Za-z._:-]{0,159}")) {
            throw new IllegalArgumentException("clientCommandId");
        }
        if (commandType == null || !commandType.matches("[A-Z][A-Z0-9_]{2,63}")) {
            throw new IllegalArgumentException("commandType");
        }
        LeagueSeasonFrozenSnapshot.requireSha256(payloadHash, "payloadHash");
    }

    record Result(boolean replayed, int httpStatus) {}
    private record Row(String commandType, String payloadHash, String status,
                       Integer httpStatus) {}

    static final class CommandConflict extends RuntimeException {
        CommandConflict(String code) { super(code); }
    }
}
