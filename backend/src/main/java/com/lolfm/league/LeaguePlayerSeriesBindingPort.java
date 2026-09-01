package com.lolfm.league;

import java.util.Optional;

/** Durable-ready authority port; the current adapter is intentionally process-local. */
public interface LeaguePlayerSeriesBindingPort {
    Registration create(
            String commandId,
            String commandPayloadHash,
            LeagueFixtureSeriesBindingV1 binding
    );

    Registration recordResume(
            String commandId,
            String commandPayloadHash,
            String bindingHash
    );

    Optional<State> findByFixture(String seasonId, String fixtureId);

    Optional<State> findByBindingHash(String bindingHash);

    State transition(
            String bindingHash,
            long expectedRevision,
            Status expectedStatus,
            Status nextStatus,
            String reason,
            LeagueFixtureCompletionReceiptV2 completionReceipt
    );

    enum Status {
        CREATED,
        ACTIVE,
        COMPLETION_PENDING_VERIFICATION,
        VERIFIED,
        PLAYER_SERIES_RESTART_REQUIRED,
        BLOCKED
    }

    record State(
            LeagueFixtureSeriesBindingV1 binding,
            long revision,
            Status status,
            String reason,
            LeagueFixtureCompletionReceiptV2 completionReceipt
    ) {
        public State {
            if (binding == null || revision < 0 || status == null) {
                throw new IllegalArgumentException("League Player binding state");
            }
            if (status == Status.VERIFIED && completionReceipt == null) {
                throw new IllegalArgumentException("Verified binding requires receipt");
            }
            if (completionReceipt != null
                    && (!completionReceipt.fixtureId().equals(binding.fixtureId())
                    || !completionReceipt.playerSeriesBindingHash()
                    .equals(binding.bindingHash()))) {
                throw new IllegalArgumentException("Binding completion receipt mismatch");
            }
        }
    }

    record Registration(State state, boolean replayedCommand) {}
}
