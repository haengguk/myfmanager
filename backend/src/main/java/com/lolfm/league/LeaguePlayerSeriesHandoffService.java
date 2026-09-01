package com.lolfm.league;

import com.lolfm.application.SeriesStatus;
import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.champion.ChampionId;
import com.lolfm.draft.DraftDecisionAuthority;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Server-owned start/resume/completion boundary for one managed Player fixture. */
@Service
public final class LeaguePlayerSeriesHandoffService {
    private final LeagueFrozenProductionIdentityProvider production;
    private final LeaguePlayerSeriesBindingPort bindings;
    private final LeaguePlayerSeriesKernelPort series;

    public LeaguePlayerSeriesHandoffService(
            LeagueFrozenProductionIdentityProvider production,
            LeaguePlayerSeriesBindingPort bindings,
            LeaguePlayerSeriesKernelPort series
    ) {
        this.production = Objects.requireNonNull(production, "production");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.series = Objects.requireNonNull(series, "series");
    }

    public StartResult startOrResume(StartCommand command) {
        Objects.requireNonNull(command, "command");
        try {
            FrozenContext context = validateFrozen(command.leagueId(), command.season(),
                    command.fixtureId(), command.expectedSeasonRevision(), true);
            String payloadHash = startPayloadHash(command);
            var existing = bindings.findByFixture(
                    command.season().seasonId(), command.fixtureId());
            if (existing.isPresent()) {
                LeaguePlayerSeriesBindingPort.State state = existing.orElseThrow();
                requireCurrentBinding(context, state.binding());
                bindings.recordResume(command.commandId(), payloadHash,
                        state.binding().bindingHash());
                return resumeExisting(state);
            }
            LeagueFixtureSeriesBindingV1 binding = LeagueFixtureSeriesBindingV1.create(
                    command.season(), context.fixture(),
                    production.currentResourceProvenanceHash());
            if (!series.canCompleteInitialDraft(binding)) {
                return StartResult.blocked("HARD_FEARLESS_LEGAL_POOL_EXHAUSTED");
            }
            LeaguePlayerSeriesBindingPort.Registration registration = bindings.create(
                    command.commandId(), payloadHash, binding);
            LeaguePlayerSeriesKernelPort.SeriesReference reference;
            try {
                reference = series.start(binding);
            } catch (RuntimeException error) {
                LeaguePlayerSeriesBindingPort.State restart = bindings.transition(
                        binding.bindingHash(), registration.state().revision(),
                        LeaguePlayerSeriesBindingPort.Status.CREATED,
                        LeaguePlayerSeriesBindingPort.Status.PLAYER_SERIES_RESTART_REQUIRED,
                        "PLAYER_SERIES_START_FAILED", null);
                return StartResult.state(StartStatus.PLAYER_SERIES_RESTART_REQUIRED,
                        "PLAYER_SERIES_START_FAILED", restart, null);
            }
            LeaguePlayerSeriesBindingPort.State active = bindings.transition(
                    binding.bindingHash(), registration.state().revision(),
                    LeaguePlayerSeriesBindingPort.Status.CREATED,
                    LeaguePlayerSeriesBindingPort.Status.ACTIVE, null, null);
            return StartResult.state(reference.replayedStart()
                            ? StartStatus.RESUMED : StartStatus.STARTED,
                    null, active, reference);
        } catch (RuntimeException error) {
            return StartResult.blocked(reason(error, "PLAYER_SERIES_START_REJECTED"));
        }
    }

    public CompletionResult complete(CompletionCommand command) {
        return complete(command, SimulationInstrumentation.enabled());
    }

    public CompletionResult complete(
            CompletionCommand command,
            SimulationInstrumentation instrumentation
    ) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(instrumentation, "instrumentation");
        try {
            FrozenContext context = validateFrozen(command.leagueId(), command.season(),
                    command.fixtureId(), command.season().revision(), false);
            LeaguePlayerSeriesBindingPort.State state = bindings.findByBindingHash(
                    command.bindingHash()).orElseThrow(() ->
                    new IllegalStateException("PLAYER_SERIES_BINDING_NOT_FOUND"));
            requireCurrentBinding(context, state.binding());
            if (state.status() == LeaguePlayerSeriesBindingPort.Status.VERIFIED) {
                VerifiedLeagueFixtureCompletion completion =
                        VerifiedLeagueFixtureCompletion.restoreVerified(
                                state.completionReceipt());
                return CompletionResult.verified(
                        state.completionReceipt(), completion, true, 0);
            }
            if (state.status()
                    == LeaguePlayerSeriesBindingPort.Status.COMPLETION_PENDING_VERIFICATION) {
                return CompletionResult.pending();
            }
            if (state.status() != LeaguePlayerSeriesBindingPort.Status.ACTIVE) {
                return CompletionResult.blocked(state.reason() == null
                        ? state.status().name() : state.reason());
            }
            LeaguePlayerSeriesBindingPort.State pending = bindings.transition(
                    state.binding().bindingHash(), state.revision(),
                    LeaguePlayerSeriesBindingPort.Status.ACTIVE,
                    LeaguePlayerSeriesBindingPort.Status.COMPLETION_PENDING_VERIFICATION,
                    null, null);
            LeaguePlayerSeriesKernelPort.CompletedSeriesEvidence evidence;
            try {
                evidence = series.completedEvidence(state.binding(), instrumentation);
            } catch (RuntimeException error) {
                String failure = reason(error, "PLAYER_SERIES_COMPLETION_READ_FAILED");
                LeaguePlayerSeriesBindingPort.Status next =
                        "PLAYER_SERIES_NOT_COMPLETED".equals(failure)
                                ? LeaguePlayerSeriesBindingPort.Status.ACTIVE
                                : LeaguePlayerSeriesBindingPort.Status.BLOCKED;
                bindings.transition(state.binding().bindingHash(), pending.revision(),
                        LeaguePlayerSeriesBindingPort.Status.COMPLETION_PENDING_VERIFICATION,
                        next, next == LeaguePlayerSeriesBindingPort.Status.ACTIVE
                                ? null : failure, null);
                return next == LeaguePlayerSeriesBindingPort.Status.ACTIVE
                        ? CompletionResult.notCompleted()
                        : CompletionResult.blocked(failure);
            }
            try {
                BuiltReceipt built = buildReceipt(context, state.binding(), evidence);
                VerifiedLeagueFixtureCompletion completion =
                        VerifiedLeagueFixtureCompletion.verifyPlayer(
                                command.season(), context.fixture(), state.binding(),
                                context.currentSnapshot(),
                                production.currentResourceProvenanceHash(), evidence,
                                built.gameReceipts(), built.authorities(),
                                built.unifiedReceipt());
                bindings.transition(state.binding().bindingHash(), pending.revision(),
                        LeaguePlayerSeriesBindingPort.Status
                                .COMPLETION_PENDING_VERIFICATION,
                        LeaguePlayerSeriesBindingPort.Status.VERIFIED, null,
                        built.unifiedReceipt());
                return CompletionResult.verified(
                        built.unifiedReceipt(), completion, false,
                        evidence.orderedGames().size());
            } catch (RuntimeException error) {
                String failure = reason(error, "PLAYER_SERIES_COMPLETION_REJECTED");
                bindings.transition(state.binding().bindingHash(), pending.revision(),
                        LeaguePlayerSeriesBindingPort.Status
                                .COMPLETION_PENDING_VERIFICATION,
                        LeaguePlayerSeriesBindingPort.Status.BLOCKED, failure, null);
                return CompletionResult.blocked(failure);
            }
        } catch (RuntimeException error) {
            return CompletionResult.blocked(reason(
                    error, "PLAYER_SERIES_COMPLETION_REJECTED"));
        }
    }

    private StartResult resumeExisting(LeaguePlayerSeriesBindingPort.State state) {
        if (state.status() == LeaguePlayerSeriesBindingPort.Status.VERIFIED) {
            return StartResult.state(StartStatus.COMPLETED, null, state, null);
        }
        if (state.status()
                == LeaguePlayerSeriesBindingPort.Status.COMPLETION_PENDING_VERIFICATION) {
            return StartResult.state(StartStatus.COMPLETION_PENDING_VERIFICATION,
                    null, state, null);
        }
        if (state.status() != LeaguePlayerSeriesBindingPort.Status.ACTIVE
                && state.status() != LeaguePlayerSeriesBindingPort.Status.CREATED) {
            return StartResult.state(
                    state.status()
                            == LeaguePlayerSeriesBindingPort.Status.PLAYER_SERIES_RESTART_REQUIRED
                            ? StartStatus.PLAYER_SERIES_RESTART_REQUIRED
                            : StartStatus.BLOCKED,
                    state.reason(), state, null);
        }
        try {
            LeaguePlayerSeriesKernelPort.SeriesReference reference =
                    series.resume(state.binding());
            if (reference.status() == SeriesStatus.COMPLETED) {
                return StartResult.state(StartStatus.COMPLETION_PENDING_VERIFICATION,
                        null, state, reference);
            }
            if (reference.status() != SeriesStatus.ACTIVE) {
                LeaguePlayerSeriesBindingPort.State restart = bindings.transition(
                        state.binding().bindingHash(), state.revision(), state.status(),
                        LeaguePlayerSeriesBindingPort.Status.PLAYER_SERIES_RESTART_REQUIRED,
                        "PLAYER_SERIES_RESTART_REQUIRED", null);
                return StartResult.state(StartStatus.PLAYER_SERIES_RESTART_REQUIRED,
                        "PLAYER_SERIES_RESTART_REQUIRED", restart, reference);
            }
            if (state.status() == LeaguePlayerSeriesBindingPort.Status.CREATED) {
                state = bindings.transition(state.binding().bindingHash(), state.revision(),
                        LeaguePlayerSeriesBindingPort.Status.CREATED,
                        LeaguePlayerSeriesBindingPort.Status.ACTIVE, null, null);
            }
            return StartResult.state(StartStatus.RESUMED, null, state, reference);
        } catch (RuntimeException error) {
            LeaguePlayerSeriesBindingPort.State restart = bindings.transition(
                    state.binding().bindingHash(), state.revision(), state.status(),
                    LeaguePlayerSeriesBindingPort.Status.PLAYER_SERIES_RESTART_REQUIRED,
                    "PLAYER_SERIES_RESTART_REQUIRED", null);
            return StartResult.state(StartStatus.PLAYER_SERIES_RESTART_REQUIRED,
                    reason(error, "PLAYER_SERIES_RESTART_REQUIRED"), restart, null);
        }
    }

    private BuiltReceipt buildReceipt(
            FrozenContext context,
            LeagueFixtureSeriesBindingV1 binding,
            LeaguePlayerSeriesKernelPort.CompletedSeriesEvidence evidence
    ) {
        LeagueFixture fixture = context.fixture();
        LinkedHashSet<ChampionId> history = new LinkedHashSet<>();
        ArrayList<LeagueFixtureGameReceiptV1> games = new ArrayList<>();
        ArrayList<LeagueFixtureDraftAuthorityReceiptV1> authorities = new ArrayList<>();
        for (LeaguePlayerSeriesKernelPort.CompletedGameEvidence game
                : evidence.orderedGames()) {
            if (!Set.copyOf(history).equals(Set.copyOf(game.historyBefore()))) {
                throw new IllegalArgumentException("PLAYER_SERIES_HISTORY_SEQUENCE_MISMATCH");
            }
            history.addAll(game.completedDraft().bluePicks());
            history.addAll(game.completedDraft().redPicks());
            List<ChampionId> after = history.stream()
                    .sorted(java.util.Comparator.comparing(ChampionId::value)).toList();
            LeagueFixtureGameReceiptV1 receipt = LeagueFixtureGameReceiptV1.from(
                    game.verifiedInput(), game.verifiedOutput(), after);
            var control = Objects.requireNonNull(
                    game.verifiedOutput().finalDraft().controlEvidence(),
                    "PLAYER_DRAFT_CONTROL_EVIDENCE_REQUIRED");
            if (control.controlledSide() != game.controlledSide()
                    || control.turns().size() != 20
                    || control.turns().stream().anyMatch(turn ->
                    turn.authority() != (turn.side() == game.controlledSide()
                            ? DraftDecisionAuthority.PLAYER : DraftDecisionAuthority.AI))) {
                throw new IllegalArgumentException("PLAYER_DRAFT_AUTHORITY_MISMATCH");
            }
            games.add(receipt);
            authorities.add(LeagueFixtureDraftAuthorityReceiptV1.player(
                    game.gameNumber(), game.controlledSide(), control.policyId(),
                    control.policyHash(), control.controlEvidenceHash()));
        }
        String winner = evidence.winnerTeamCode();
        String loser = winner.equals(fixture.firstTeamCode())
                ? fixture.secondTeamCode() : fixture.firstTeamCode();
        LeagueSeasonFrozenSnapshot snapshot = context.currentSnapshot();
        LeagueFixtureCompletionReceiptV1 core = new LeagueFixtureCompletionReceiptV1(
                LeagueFixtureCompletionReceiptV1.SCHEMA,
                LeagueFixtureCompletionReceiptV1.HASH_ALGORITHM,
                binding.seasonId(), binding.fixtureId(), binding.boundSeriesId(),
                LeagueFixtureExecutionMode.PLAYER_CONTROLLED,
                fixture.firstTeamCode(), fixture.secondTeamCode(),
                fixture.game1BlueTeamCode(), fixture.game1RedTeamCode(),
                fixture.seriesFormat(), fixture.fixtureRootSeed(),
                LeagueIdentity.FIXTURE_ROOT_SEED_ALGORITHM,
                LeagueIdentity.GAME_SEED_ALGORITHM,
                binding.scheduleIdentity(), binding.productDecisionHash(),
                snapshot.snapshotIdentity(),
                snapshot.teamSnapshotIdentity(fixture.firstTeamCode()),
                snapshot.teamSnapshotIdentity(fixture.secondTeamCode()),
                snapshot.playerResourceIdentity(),
                snapshot.championDraftResourceIdentity(),
                snapshot.matchupCompositionResourceIdentity(),
                snapshot.productionRuntimeIdentity(),
                production.currentResourceProvenanceHash(), games,
                evidence.score().get(fixture.firstTeamCode()),
                evidence.score().get(fixture.secondTeamCode()), winner, loser,
                games.size(), null);
        LeagueFixtureCompletionReceiptV2 unified =
                new LeagueFixtureCompletionReceiptV2(
                        LeagueFixtureCompletionReceiptV2.SCHEMA,
                        LeagueFixtureCompletionReceiptV2.HASH_ALGORITHM,
                        binding.leagueId(), binding.bindingHash(), core, authorities, null);
        return new BuiltReceipt(games, authorities, unified);
    }

    private FrozenContext validateFrozen(
            String leagueId,
            LeagueSeasonAggregate season,
            String fixtureId,
            long expectedSeasonRevision,
            boolean rejectApplied
    ) {
        Objects.requireNonNull(season, "season");
        LeagueIdentity.requireLeagueId(leagueId);
        if (!season.leagueId().equals(leagueId)) {
            throw new IllegalArgumentException("LEAGUE_SEASON_OWNERSHIP_MISMATCH");
        }
        if (season.revision() != expectedSeasonRevision) {
            throw new IllegalArgumentException("PLAYER_SERIES_STALE_SEASON_REVISION");
        }
        if (season.seasonMode() != LeagueSeasonMode.HYBRID_MANAGER
                || season.managedTeamCode() == null) {
            throw new IllegalArgumentException("PLAYER_SERIES_REQUIRES_HYBRID_MANAGER");
        }
        LeagueFixture fixture = season.schedule().fixture(fixtureId);
        if (fixture.executionMode() != LeagueFixtureExecutionMode.PLAYER_CONTROLLED
                || !fixture.containsTeam(season.managedTeamCode())) {
            throw new IllegalArgumentException("PLAYER_SERIES_FIXTURE_NOT_MANAGED");
        }
        if (fixture.seriesFormat() != com.lolfm.application.SeriesFormat.BO3) {
            throw new IllegalArgumentException("PLAYER_SERIES_PRODUCTION_FORMAT_NOT_BO3");
        }
        if (rejectApplied && season.standings().appliedCompletions()
                .containsKey(fixture.fixtureId())) {
            throw new IllegalArgumentException("PLAYER_SERIES_FIXTURE_ALREADY_COMPLETED");
        }
        if (!season.productDecisionHash().equals(
                LeagueV1ProductDecisions.productDecisionHash())) {
            throw new IllegalArgumentException("PLAYER_SERIES_PRODUCT_IDENTITY_MISMATCH");
        }
        LeagueSeasonFrozenSnapshot current = production.currentSnapshot(
                Set.copyOf(season.schedule().teamCodes()));
        if (!current.equals(season.frozenSnapshot())
                || !season.managedTeamSnapshotIdentity().equals(
                current.teamSnapshotIdentity(season.managedTeamCode()))) {
            throw new IllegalArgumentException("PLAYER_SERIES_FROZEN_IDENTITY_MISMATCH");
        }
        return new FrozenContext(season, fixture, current);
    }

    private void requireCurrentBinding(
            FrozenContext context,
            LeagueFixtureSeriesBindingV1 binding
    ) {
        LeagueFixture fixture = context.fixture();
        LeagueSeasonAggregate season = context.season();
        LeagueSeasonFrozenSnapshot snapshot = context.currentSnapshot();
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();
        boolean valid = binding.leagueId().equals(season.leagueId())
                && binding.seasonId().equals(season.seasonId())
                && binding.fixtureId().equals(fixture.fixtureId())
                && binding.boundSeriesId().equals(fixture.boundSeriesId())
                && binding.executionMode() == fixture.executionMode()
                && binding.seriesFormat() == fixture.seriesFormat()
                && binding.game1BlueTeamCode().equals(fixture.game1BlueTeamCode())
                && binding.game1RedTeamCode().equals(fixture.game1RedTeamCode())
                && binding.fixtureRootSeed() == fixture.fixtureRootSeed()
                && binding.seedAnchorTeamCode().equals(fixture.seedAnchorTeamCode())
                && binding.managedTeamCode().equals(season.managedTeamCode())
                && binding.scheduleIdentity().equals(season.schedule().scheduleIdentity())
                && binding.productDecisionHash().equals(season.productDecisionHash())
                && binding.frozenSnapshotIdentity().equals(snapshot.snapshotIdentity())
                && binding.resourceProvenanceHash().equals(
                production.currentResourceProvenanceHash())
                && binding.policyId().equals(policy.policyId())
                && binding.policyHash().equals(policy.policyHash())
                && binding.runtimeProfileId().equals(
                policy.retainedRuntimeProfileId().name())
                && binding.configurationHash().equals(policy.configurationHash())
                && binding.activeGameplayRulesVersion().equals(
                policy.activeGameplayRulesVersion())
                && binding.engineImplementationVersion().equals(
                policy.engineImplementationVersion());
        if (!valid) throw new IllegalArgumentException(
                "PLAYER_SERIES_STORED_BINDING_MISMATCH");
    }

    private static String startPayloadHash(StartCommand command) {
        if (command.commandId() == null || command.commandId().isBlank()
                || command.commandId().indexOf('\n') >= 0) {
            throw new IllegalArgumentException("PLAYER_SERIES_COMMAND_ID_INVALID");
        }
        return LeagueIdentity.sha256(
                "commandSchema=AI_LEAGUE_PLAYER_SERIES_START_COMMAND_V1\n"
                        + "leagueId=" + command.leagueId() + '\n'
                        + "seasonId=" + command.season().seasonId() + '\n'
                        + "fixtureId=" + command.fixtureId() + '\n'
                        + "expectedSeasonRevision=" + command.expectedSeasonRevision() + '\n');
    }

    private static String reason(RuntimeException error, String fallback) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? fallback : error.getMessage();
    }

    public record StartCommand(
            String leagueId,
            LeagueSeasonAggregate season,
            String fixtureId,
            long expectedSeasonRevision,
            String commandId
    ) {}

    public record CompletionCommand(
            String leagueId,
            LeagueSeasonAggregate season,
            String fixtureId,
            String bindingHash
    ) {}

    public enum StartStatus {
        STARTED,
        RESUMED,
        COMPLETION_PENDING_VERIFICATION,
        COMPLETED,
        PLAYER_SERIES_RESTART_REQUIRED,
        BLOCKED
    }

    public record StartResult(
            StartStatus status,
            String reason,
            LeaguePlayerSeriesBindingPort.State bindingState,
            LeaguePlayerSeriesKernelPort.SeriesReference seriesReference
    ) {
        static StartResult state(
                StartStatus status,
                String reason,
                LeaguePlayerSeriesBindingPort.State state,
                LeaguePlayerSeriesKernelPort.SeriesReference reference
        ) {
            return new StartResult(status, reason, state, reference);
        }

        static StartResult blocked(String reason) {
            return new StartResult(StartStatus.BLOCKED, reason, null, null);
        }
    }

    public enum CompletionStatus { VERIFIED, NOT_COMPLETED, PENDING, BLOCKED }

    public record CompletionResult(
            CompletionStatus status,
            String reason,
            LeagueFixtureCompletionReceiptV2 receipt,
            VerifiedLeagueFixtureCompletion verifiedCompletion,
            boolean replayed,
            int gameEngineExecutionCount
    ) {
        static CompletionResult verified(
                LeagueFixtureCompletionReceiptV2 receipt,
                VerifiedLeagueFixtureCompletion completion,
                boolean replayed,
                int executions
        ) {
            return new CompletionResult(CompletionStatus.VERIFIED, null, receipt,
                    completion, replayed, executions);
        }

        static CompletionResult notCompleted() {
            return new CompletionResult(CompletionStatus.NOT_COMPLETED,
                    "PLAYER_SERIES_NOT_COMPLETED", null, null, false, 0);
        }

        static CompletionResult pending() {
            return new CompletionResult(CompletionStatus.PENDING, null,
                    null, null, false, 0);
        }

        static CompletionResult blocked(String reason) {
            return new CompletionResult(CompletionStatus.BLOCKED, reason,
                    null, null, false, 0);
        }
    }

    private record FrozenContext(
            LeagueSeasonAggregate season,
            LeagueFixture fixture,
            LeagueSeasonFrozenSnapshot currentSnapshot
    ) {}

    private record BuiltReceipt(
            List<LeagueFixtureGameReceiptV1> gameReceipts,
            List<LeagueFixtureDraftAuthorityReceiptV1> authorities,
            LeagueFixtureCompletionReceiptV2 unifiedReceipt
    ) {}
}
