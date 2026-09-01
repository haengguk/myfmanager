package com.lolfm.league;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Transactional single-node reference adapter for Player fixture ownership. */
@Component
final class JdbcLeaguePlayerSeriesBindingAdapter
        implements LeaguePlayerSeriesBindingPort {
    private final LeagueRelationalStore store;

    JdbcLeaguePlayerSeriesBindingAdapter(LeagueRelationalStore store) {
        this.store = store;
    }

    @Override
    public Registration createOrLoad(
            String commandId,
            String commandPayloadHash,
            LeagueFixtureSeriesBindingV1 binding
    ) {
        requireCommand(commandId, commandPayloadHash);
        return store.transactions().execute(ignored -> {
            lockFixture(binding.seasonId(), binding.fixtureId());
            Optional<CommandRow> prior = findCommand(commandId);
            if (prior.isPresent()) {
                requireSameCommand(prior.get(), commandPayloadHash, binding.bindingHash());
                State priorState = findByBindingHash(prior.get().bindingHash())
                        .orElseThrow(() -> new IllegalStateException(
                                "PLAYER_SERIES_BINDING_NOT_FOUND"));
                return new Registration(priorState, false, true);
            }
            Optional<State> existing = findByFixture(binding.seasonId(), binding.fixtureId());
            boolean owner = existing.isEmpty();
            State state;
            if (owner) {
                var now = store.now();
                store.jdbc().update("""
                        INSERT INTO league_player_binding(
                          binding_hash, season_id, fixture_id, binding_schema,
                          binding_canonical, binding_json, revision, lifecycle_status,
                          created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, 0, 'CREATED', ?, ?)
                        """, binding.bindingHash(), binding.seasonId(), binding.fixtureId(),
                        LeagueFixtureSeriesBindingV1.SCHEMA, binding.canonicalText(),
                        binding.canonicalText(), now, now);
                store.jdbc().update("""
                        UPDATE league_fixture SET lifecycle_status =
                          'PLAYER_SERIES_RESERVED', revision = revision + 1
                        WHERE season_id = ? AND fixture_id = ?
                        """, binding.seasonId(), binding.fixtureId());
                state = new State(binding, 0, Status.CREATED, null, null);
            } else {
                state = existing.get();
                if (!state.binding().equals(binding)) {
                    throw new IllegalStateException("PLAYER_SERIES_FIXTURE_BINDING_CONFLICT");
                }
            }
            insertCommand(commandId, commandPayloadHash, binding);
            return new Registration(state, owner, false);
        });
    }

    @Override
    public Registration recordResume(
            String commandId,
            String commandPayloadHash,
            String bindingHash
    ) {
        requireCommand(commandId, commandPayloadHash);
        return store.transactions().execute(ignored -> {
            State state = lockBinding(bindingHash);
            Optional<CommandRow> prior = findCommand(commandId);
            if (prior.isPresent()) {
                requireSameCommand(prior.get(), commandPayloadHash, bindingHash);
                return new Registration(state, false, true);
            }
            insertCommand(commandId, commandPayloadHash, state.binding());
            return new Registration(state, false, false);
        });
    }

    @Override
    public Optional<State> findByFixture(String seasonId, String fixtureId) {
        List<State> rows = store.jdbc().query("""
                SELECT binding_hash, binding_canonical, revision, lifecycle_status,
                       reason, completion_receipt_hash
                FROM league_player_binding
                WHERE season_id = ? AND fixture_id = ?
                """, (result, row) -> state(result), seasonId, fixtureId);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<State> findByBindingHash(String bindingHash) {
        List<State> rows = store.jdbc().query("""
                SELECT binding_hash, binding_canonical, revision, lifecycle_status,
                       reason, completion_receipt_hash
                FROM league_player_binding WHERE binding_hash = ?
                """, (result, row) -> state(result), bindingHash);
        return rows.stream().findFirst();
    }

    @Override
    public CompletionClaim claimCompletion(String bindingHash) {
        return store.transactions().execute(ignored -> {
            State current = lockBinding(bindingHash);
            if (current.status() == Status.ACTIVE) {
                State pending = update(current, Status.COMPLETION_PENDING_VERIFICATION,
                        null, null);
                return new CompletionClaim(pending, true);
            }
            return new CompletionClaim(current, false);
        });
    }

    @Override
    public State transition(
            String bindingHash,
            long expectedRevision,
            Status expectedStatus,
            Status nextStatus,
            String reason,
            LeagueFixtureCompletionReceiptV2 completionReceipt
    ) {
        return store.transactions().execute(ignored -> {
            State current = lockBinding(bindingHash);
            if (current.revision() != expectedRevision || current.status() != expectedStatus) {
                throw new IllegalStateException("PLAYER_SERIES_BINDING_STALE_TRANSITION");
            }
            State next = update(current, nextStatus, reason, completionReceipt);
            String fixtureStatus = switch (nextStatus) {
                case CREATED -> "PLAYER_SERIES_RESERVED";
                case ACTIVE -> "PLAYER_SERIES_ACTIVE";
                case COMPLETION_PENDING_VERIFICATION -> "COMPLETION_PENDING_VERIFICATION";
                case VERIFIED -> "COMPLETION_PENDING_VERIFICATION";
                case PLAYER_SERIES_RESTART_REQUIRED -> "PLAYER_SERIES_RESTART_REQUIRED";
                case BLOCKED -> "BLOCKED";
            };
            store.jdbc().update("""
                    UPDATE league_fixture SET lifecycle_status = ?, revision = revision + 1,
                      failure_code = ? WHERE season_id = ? AND fixture_id = ?
                    """, fixtureStatus, reason, current.binding().seasonId(),
                    current.binding().fixtureId());
            if (nextStatus == Status.VERIFIED) {
                store.storeReceiptAndOutbox(completionReceipt);
            }
            return next;
        });
    }

    private State update(
            State current,
            Status nextStatus,
            String reason,
            LeagueFixtureCompletionReceiptV2 receipt
    ) {
        long nextRevision = Math.addExact(current.revision(), 1);
        int updated = store.jdbc().update("""
                UPDATE league_player_binding SET revision = ?, lifecycle_status = ?,
                  reason = ?, completion_receipt_hash = ?, updated_at = ?
                WHERE binding_hash = ? AND revision = ? AND lifecycle_status = ?
                """, nextRevision, nextStatus.name(), reason,
                receipt == null ? null : receipt.canonicalFixtureReceiptHash(), store.now(),
                current.binding().bindingHash(), current.revision(), current.status().name());
        if (updated != 1) {
            throw new IllegalStateException("PLAYER_SERIES_BINDING_STALE_TRANSITION");
        }
        return new State(current.binding(), nextRevision, nextStatus, reason, receipt);
    }

    private State lockBinding(String bindingHash) {
        List<State> rows = store.jdbc().query("""
                SELECT binding_hash, binding_canonical, revision, lifecycle_status,
                       reason, completion_receipt_hash
                FROM league_player_binding WHERE binding_hash = ? FOR UPDATE
                """, (result, row) -> state(result), bindingHash);
        if (rows.isEmpty()) throw new IllegalStateException("PLAYER_SERIES_BINDING_NOT_FOUND");
        return rows.getFirst();
    }

    private State state(ResultSet result) throws SQLException {
        LeagueFixtureSeriesBindingV1 binding =
                LeagueFixtureSeriesBindingV1.restoreCanonical(result.getString(2));
        String receiptHash = result.getString(6);
        LeagueFixtureCompletionReceiptV2 receipt = receiptHash == null
                ? null : store.loadReceipt(receiptHash);
        return new State(binding, result.getLong(3), Status.valueOf(result.getString(4)),
                result.getString(5), receipt);
    }

    private void lockFixture(String seasonId, String fixtureId) {
        List<String> rows = store.jdbc().query("""
                SELECT fixture_id FROM league_fixture
                WHERE season_id = ? AND fixture_id = ? FOR UPDATE
                """, (result, row) -> result.getString(1), seasonId, fixtureId);
        if (rows.isEmpty()) throw new IllegalStateException("LEAGUE_FIXTURE_NOT_PERSISTED");
    }

    private Optional<CommandRow> findCommand(String commandId) {
        List<CommandRow> rows = store.jdbc().query("""
                SELECT payload_hash, binding_hash FROM league_player_binding_command
                WHERE command_id = ?
                """, (result, row) -> new CommandRow(result.getString(1),
                result.getString(2)), commandId);
        return rows.stream().findFirst();
    }

    private void insertCommand(
            String commandId,
            String payloadHash,
            LeagueFixtureSeriesBindingV1 binding
    ) {
        store.jdbc().update("""
                INSERT INTO league_player_binding_command(
                  season_id, fixture_id, command_id, payload_hash,
                  binding_hash, created_at) VALUES (?, ?, ?, ?, ?, ?)
                """, binding.seasonId(), binding.fixtureId(), commandId, payloadHash,
                binding.bindingHash(), store.now());
    }

    private static void requireCommand(String commandId, String payloadHash) {
        if (commandId == null || commandId.isBlank() || commandId.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("commandId");
        }
        LeagueSeasonFrozenSnapshot.requireSha256(payloadHash, "commandPayloadHash");
    }

    private static void requireSameCommand(
            CommandRow prior,
            String payloadHash,
            String bindingHash
    ) {
        if (!prior.payloadHash().equals(payloadHash)
                || !prior.bindingHash().equals(bindingHash)) {
            throw new IllegalStateException("PLAYER_SERIES_COMMAND_ID_PAYLOAD_CONFLICT");
        }
    }

    private record CommandRow(String payloadHash, String bindingHash) {}
}
