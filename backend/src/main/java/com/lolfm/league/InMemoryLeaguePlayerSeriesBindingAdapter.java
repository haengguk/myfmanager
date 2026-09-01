package com.lolfm.league;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Reference adapter only. It deliberately provides no process-restart durability. */
final class InMemoryLeaguePlayerSeriesBindingAdapter
        implements LeaguePlayerSeriesBindingPort {
    private final Map<String, State> byBindingHash = new HashMap<>();
    private final Map<String, String> bindingByFixture = new HashMap<>();
    private final Map<String, CommandIndex> commands = new HashMap<>();

    @Override
    public synchronized Registration createOrLoad(
            String commandId,
            String commandPayloadHash,
            LeagueFixtureSeriesBindingV1 binding
    ) {
        requireCommand(commandId, commandPayloadHash);
        CommandIndex prior = commands.get(commandId);
        if (prior != null) {
            requireSameCommand(prior, commandPayloadHash, binding.bindingHash());
            return new Registration(requireState(prior.bindingHash()), false, true);
        }
        String fixtureKey = fixtureKey(binding.seasonId(), binding.fixtureId());
        String existingHash = bindingByFixture.get(fixtureKey);
        if (existingHash != null) {
            State existing = requireState(existingHash);
            if (!existing.binding().equals(binding)) {
                throw new IllegalStateException("PLAYER_SERIES_FIXTURE_BINDING_CONFLICT");
            }
            commands.put(commandId,
                    new CommandIndex(commandPayloadHash, binding.bindingHash()));
            return new Registration(existing, false, false);
        }
        State state = new State(binding, 0, Status.CREATED, null, null);
        byBindingHash.put(binding.bindingHash(), state);
        bindingByFixture.put(fixtureKey, binding.bindingHash());
        commands.put(commandId, new CommandIndex(commandPayloadHash, binding.bindingHash()));
        return new Registration(state, true, false);
    }

    @Override
    public synchronized Registration recordResume(
            String commandId,
            String commandPayloadHash,
            String bindingHash
    ) {
        requireCommand(commandId, commandPayloadHash);
        State state = requireState(bindingHash);
        CommandIndex prior = commands.get(commandId);
        if (prior != null) {
            requireSameCommand(prior, commandPayloadHash, bindingHash);
            return new Registration(state, false, true);
        }
        commands.put(commandId, new CommandIndex(commandPayloadHash, bindingHash));
        return new Registration(state, false, false);
    }

    @Override
    public synchronized Optional<State> findByFixture(String seasonId, String fixtureId) {
        String hash = bindingByFixture.get(fixtureKey(seasonId, fixtureId));
        return hash == null ? Optional.empty() : Optional.of(requireState(hash));
    }

    @Override
    public synchronized Optional<State> findByBindingHash(String bindingHash) {
        return Optional.ofNullable(byBindingHash.get(bindingHash));
    }

    @Override
    public synchronized CompletionClaim claimCompletion(String bindingHash) {
        State current = requireState(bindingHash);
        if (current.status() == Status.ACTIVE) {
            State pending = new State(current.binding(),
                    Math.addExact(current.revision(), 1),
                    Status.COMPLETION_PENDING_VERIFICATION, null, null);
            byBindingHash.put(bindingHash, pending);
            return new CompletionClaim(pending, true);
        }
        if (current.status() == Status.COMPLETION_PENDING_VERIFICATION
                || current.status() == Status.VERIFIED) {
            return new CompletionClaim(current, false);
        }
        return new CompletionClaim(current, false);
    }

    @Override
    public synchronized State transition(
            String bindingHash,
            long expectedRevision,
            Status expectedStatus,
            Status nextStatus,
            String reason,
            LeagueFixtureCompletionReceiptV2 completionReceipt
    ) {
        State current = requireState(bindingHash);
        if (current.revision() != expectedRevision || current.status() != expectedStatus) {
            throw new IllegalStateException("PLAYER_SERIES_BINDING_STALE_TRANSITION");
        }
        State next = new State(current.binding(), Math.addExact(current.revision(), 1),
                nextStatus, reason, completionReceipt);
        byBindingHash.put(bindingHash, next);
        return next;
    }

    private State requireState(String bindingHash) {
        State state = byBindingHash.get(bindingHash);
        if (state == null) throw new IllegalStateException("PLAYER_SERIES_BINDING_NOT_FOUND");
        return state;
    }

    private static String fixtureKey(String seasonId, String fixtureId) {
        return seasonId + '|' + fixtureId;
    }

    private static void requireCommand(String commandId, String payloadHash) {
        if (commandId == null || commandId.isBlank() || commandId.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("commandId");
        }
        LeagueSeasonFrozenSnapshot.requireSha256(payloadHash, "commandPayloadHash");
    }

    private static void requireSameCommand(
            CommandIndex prior,
            String payloadHash,
            String bindingHash
    ) {
        if (!prior.payloadHash().equals(payloadHash)
                || !prior.bindingHash().equals(bindingHash)) {
            throw new IllegalStateException("PLAYER_SERIES_COMMAND_ID_PAYLOAD_CONFLICT");
        }
    }

    private record CommandIndex(String payloadHash, String bindingHash) {}
}
