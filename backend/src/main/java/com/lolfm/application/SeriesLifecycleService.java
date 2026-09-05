package com.lolfm.application;

import com.lolfm.champion.ChampionId;
import com.lolfm.controller.SeriesApiV1Exception;
import com.lolfm.domain.Team;
import com.lolfm.draft.DraftSelectionContext;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.dto.SeriesApiV1Dtos;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.league.LeagueIdentity;
import com.lolfm.league.LeagueFixtureSeriesBindingV1;
import com.lolfm.league.LeaguePlayerSeriesKernelPort;
import com.lolfm.career.CareerCompetitionSeriesBindingV1;
import com.lolfm.simulator.TeamSide;
import com.lolfm.simulator.SimulationInstrumentation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Backend-owned BO3/BO5 state machine and Production V9 compare-and-commit boundary. */
@Service
public final class SeriesLifecycleService {
    private final LckTeamAssembler teams;
    private final PlayerControlledDraftEngine drafts;
    private final SeriesRepository repository;
    private final SeriesMatchExecutor matches;

    public SeriesLifecycleService(
            LckTeamAssembler teams,
            PlayerControlledDraftEngine drafts,
            SeriesRepository repository,
            SeriesMatchExecutor matches
    ) {
        this.teams = Objects.requireNonNull(teams, "teams");
        this.drafts = Objects.requireNonNull(drafts, "drafts");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.matches = Objects.requireNonNull(matches, "matches");
    }

    public CreateExecution create(SeriesApiV1Dtos.CreateRequest request) {
        validateCreate(request);
        String canonical = canonicalCreate(request);
        String payloadHash = SeriesIdentity.sha256(canonical);
        String seriesId = SeriesIdentity.seriesId(canonical);
        long rootSeed = Long.parseLong(request.rootSeed());
        Instant now = repository.now();
        String historyHash = SeriesIdentity.historyHash(0, Set.of());
        String red = request.game1BlueTeamCode().equals(request.teamACode())
                ? request.teamBCode() : request.teamACode();
        SeriesGame first = newGame(seriesId, 1, request.game1BlueTeamCode(), red,
                request.managedTeamCode(), request.rootSeed(), historyHash, Set.of());
        LinkedHashMap<String, Integer> score = new LinkedHashMap<>();
        score.put(request.teamACode(), 0);
        score.put(request.teamBCode(), 0);
        SeriesAggregate aggregate = new SeriesAggregate(
                seriesId, 0, SeriesStatus.ACTIVE, null, request.format(),
                request.teamACode(), request.teamBCode(), request.managedTeamCode(),
                request.game1BlueTeamCode(), request.rootSeed(), rootSeed, score,
                List.of(first), Set.of(), historyHash, null, now, now,
                repository.parentExpiresAt(now), Map.of());
        try {
            SeriesRepository.CreateResult result = repository.create(
                    request.clientCommandId(), payloadHash, aggregate);
            return new CreateExecution(result.aggregate(), result.replayed());
        } catch (SeriesRepository.RepositoryFailure error) {
            throw repositoryError(error);
        }
    }

    SeriesRepository.CreateResult createLeagueBound(
            LeagueFixtureSeriesBindingV1 binding
    ) {
        Objects.requireNonNull(binding, "binding");
        Instant now = repository.now();
        String red = binding.game1BlueTeamCode().equals(binding.firstTeamCode())
                ? binding.secondTeamCode() : binding.firstTeamCode();
        SeriesGame first = newGame(binding.boundSeriesId(), 1,
                binding.game1BlueTeamCode(), red, binding.managedTeamCode(),
                Long.toString(binding.fixtureRootSeed()), binding.initialHistoryHash(),
                Set.of(), SeriesOrigin.LEAGUE_BOUND, binding.fixtureRootSeed(),
                binding.seedAnchorTeamCode());
        LinkedHashMap<String, Integer> score = new LinkedHashMap<>();
        score.put(binding.firstTeamCode(), 0);
        score.put(binding.secondTeamCode(), 0);
        SeriesAggregate aggregate = new SeriesAggregate(
                binding.boundSeriesId(), 0, SeriesStatus.ACTIVE, null,
                binding.seriesFormat(), binding.firstTeamCode(), binding.secondTeamCode(),
                binding.managedTeamCode(), binding.game1BlueTeamCode(),
                Long.toString(binding.fixtureRootSeed()), binding.fixtureRootSeed(), score,
                List.of(first), Set.of(), binding.initialHistoryHash(), null, now, now,
                repository.parentExpiresAt(now), Map.of(), SeriesOrigin.LEAGUE_BOUND,
                binding.bindingHash(), binding.seedAnchorTeamCode());
        SeriesRepository.CreateResult result = repository.create(
                "LEAGUE_BINDING:" + binding.bindingHash(), binding.bindingHash(), aggregate);
        requireLeagueBinding(result.aggregate(), binding);
        return result;
    }

    boolean canCompleteLeagueBoundInitialDraft() {
        return drafts.canCompleteSeriesDraft(Set.of());
    }

    SeriesAggregate resumeLeagueBound(LeagueFixtureSeriesBindingV1 binding) {
        SeriesAggregate aggregate = get(binding.boundSeriesId());
        requireLeagueBinding(aggregate, binding);
        return aggregate;
    }

    SeriesRepository.CreateResult createCompetitionBound(
            CareerCompetitionSeriesBindingV1 binding
    ) {
        Objects.requireNonNull(binding, "binding");
        Instant now = repository.now();
        SeriesGame first = newGame(binding.boundSeriesId(), 1,
                binding.game1BlueTeamCode(), binding.game1RedTeamCode(),
                binding.managedTeamCode(), Long.toString(binding.fixtureRootSeed()),
                binding.initialHistoryHash(), binding.initialHistoryPicks(), SeriesOrigin.COMPETITION_BOUND,
                binding.fixtureRootSeed(), binding.seedAnchorTeamCode());
        LinkedHashMap<String, Integer> score = new LinkedHashMap<>();
        score.put(binding.firstTeamCode(), 0);
        score.put(binding.secondTeamCode(), 0);
        SeriesAggregate aggregate = new SeriesAggregate(binding.boundSeriesId(), 0,
                SeriesStatus.ACTIVE, null, binding.seriesFormat(),
                binding.firstTeamCode(), binding.secondTeamCode(),
                binding.managedTeamCode(), binding.game1BlueTeamCode(),
                Long.toString(binding.fixtureRootSeed()), binding.fixtureRootSeed(),
                score, List.of(first), binding.initialHistoryPicks(), binding.initialHistoryHash(), null,
                now, now, repository.parentExpiresAt(now), Map.of(),
                SeriesOrigin.COMPETITION_BOUND, binding.bindingHash(),
                binding.seedAnchorTeamCode(), binding.loserChoosesNextSide() ? binding.sideSelectionPolicy() : null, binding.frozenRosters());
        SeriesRepository.CreateResult result = repository.create(
                "COMPETITION_BINDING:" + binding.bindingHash(),
                binding.bindingHash(), aggregate);
        requireCompetitionBinding(result.aggregate(), binding);
        return result;
    }

    SeriesAggregate resumeCompetitionBound(CareerCompetitionSeriesBindingV1 binding) {
        SeriesAggregate aggregate = get(binding.boundSeriesId());
        requireCompetitionBinding(aggregate, binding);
        return aggregate;
    }

    LeaguePlayerSeriesKernelPort.CompletedSeriesEvidence completedCompetitionEvidence(
            CareerCompetitionSeriesBindingV1 binding,
            SimulationInstrumentation instrumentation
    ) {
        Objects.requireNonNull(instrumentation, "instrumentation");
        SeriesAggregate before = resumeCompetitionBound(binding);
        if (before.status() != SeriesStatus.COMPLETED) {
            throw new IllegalStateException("PLAYER_SERIES_NOT_COMPLETED");
        }
        ArrayList<LeaguePlayerSeriesKernelPort.CompletedGameEvidence> games =
                completedBoundGames(before, instrumentation);
        SeriesAggregate after = resumeCompetitionBound(binding);
        if (!after.equals(before)) {
            throw new IllegalStateException("PLAYER_SERIES_COMPLETION_READ_MUTATED_STATE");
        }
        return completedEvidence(before, games);
    }

    LeaguePlayerSeriesKernelPort.CompletedSeriesEvidence completedLeagueEvidence(
            LeagueFixtureSeriesBindingV1 binding,
            SimulationInstrumentation instrumentation
    ) {
        Objects.requireNonNull(instrumentation, "instrumentation");
        SeriesAggregate before = resumeLeagueBound(binding);
        if (before.status() != SeriesStatus.COMPLETED) {
            throw new IllegalStateException("PLAYER_SERIES_NOT_COMPLETED");
        }
        ArrayList<LeaguePlayerSeriesKernelPort.CompletedGameEvidence> games =
                completedBoundGames(before, instrumentation);
        SeriesAggregate after = resumeLeagueBound(binding);
        if (!after.equals(before)) {
            throw new IllegalStateException("PLAYER_SERIES_COMPLETION_READ_MUTATED_STATE");
        }
        return completedEvidence(before, games);
    }

    private ArrayList<LeaguePlayerSeriesKernelPort.CompletedGameEvidence>
            completedBoundGames(
            SeriesAggregate before,
            SimulationInstrumentation instrumentation
    ) {
        ArrayList<LeaguePlayerSeriesKernelPort.CompletedGameEvidence> games =
                new ArrayList<>();
        for (SeriesGame game : before.games()) {
            if (game.status() != SeriesGameStatus.COMMITTED
                    || game.completedDraft() == null || game.receipt() == null
                    || game.childDraft() == null
                    || game.childDraft().completionBinding() == null) {
                throw new IllegalStateException("PLAYER_SERIES_GAME_EVIDENCE_INCOMPLETE");
            }
            SeriesChildDraft child = game.childDraft();
            SeriesMatchExecutor.Execution replay = matches.executeTrusted(
                    binding(before, game), child.childId(), child.generation(),
                    child.revision(), child.completionBinding(), game.completedDraft(),
                    instrumentation);
            if (!game.receipt().equals(replay.receipt())) {
                throw new IllegalStateException("PLAYER_SERIES_GAME_RECEIPT_MISMATCH");
            }
            games.add(new LeaguePlayerSeriesKernelPort.CompletedGameEvidence(
                    game.gameNumber(), game.blueTeamCode(), game.redTeamCode(),
                    game.controlledSide(), game.matchSeed(), game.historyBefore(),
                    game.historyBeforeHash(), game.completedDraft(), game.receipt(),
                    replay.input(), replay.output()));
        }
        return games;
    }

    private static LeaguePlayerSeriesKernelPort.CompletedSeriesEvidence completedEvidence(
            SeriesAggregate before,
            List<LeaguePlayerSeriesKernelPort.CompletedGameEvidence> games
    ) {
        return new LeaguePlayerSeriesKernelPort.CompletedSeriesEvidence(
                before.seriesId(), before.leagueBindingHash(), before.revision(),
                before.format(), before.teamACode(), before.teamBCode(),
                before.managedTeamCode(), before.rootSeed(), before.score(),
                before.consumedPicks().stream().toList(), before.historyHash(),
                before.winnerTeamCode(), games);
    }

    public SeriesAggregate get(String seriesId) {
        try { return requireVisible(repository.get(seriesId)); }
        catch (SeriesRepository.RepositoryFailure error) { throw repositoryError(error); }
    }

    private static void requireLeagueBinding(
            SeriesAggregate aggregate,
            LeagueFixtureSeriesBindingV1 binding
    ) {
        boolean valid = aggregate.origin() == SeriesOrigin.LEAGUE_BOUND
                && aggregate.seriesId().equals(binding.boundSeriesId())
                && aggregate.leagueBindingHash().equals(binding.bindingHash())
                && aggregate.leagueSeedAnchorTeamCode().equals(
                binding.seedAnchorTeamCode())
                && aggregate.format() == binding.seriesFormat()
                && aggregate.teamACode().equals(binding.firstTeamCode())
                && aggregate.teamBCode().equals(binding.secondTeamCode())
                && aggregate.managedTeamCode().equals(binding.managedTeamCode())
                && aggregate.game1BlueTeamCode().equals(binding.game1BlueTeamCode())
                && aggregate.rootSeed() == binding.fixtureRootSeed();
        if (!valid) throw new IllegalStateException("PLAYER_SERIES_BINDING_MISMATCH");
    }

    private static void requireCompetitionBinding(
            SeriesAggregate aggregate,
            CareerCompetitionSeriesBindingV1 binding
    ) {
        boolean valid = aggregate.origin() == SeriesOrigin.COMPETITION_BOUND
                && aggregate.seriesId().equals(binding.boundSeriesId())
                && aggregate.leagueBindingHash().equals(binding.bindingHash())
                && aggregate.leagueSeedAnchorTeamCode().equals(binding.seedAnchorTeamCode())
                && aggregate.format() == binding.seriesFormat()
                && aggregate.teamACode().equals(binding.firstTeamCode())
                && aggregate.teamBCode().equals(binding.secondTeamCode())
                && aggregate.managedTeamCode().equals(binding.managedTeamCode())
                && aggregate.game1BlueTeamCode().equals(binding.game1BlueTeamCode())
                && aggregate.rootSeed() == binding.fixtureRootSeed()
                && Set.copyOf(aggregate.games().getFirst().historyBefore()).equals(binding.initialHistoryPicks())
                && aggregate.games().getFirst().historyBeforeHash().equals(binding.initialHistoryHash())
                && Objects.equals(aggregate.competitionSidePolicy(), binding.loserChoosesNextSide() ? binding.sideSelectionPolicy() : null)
                && Objects.equals(aggregate.frozenCompetitionRosters(), binding.frozenRosters());
        if (!valid) throw new IllegalStateException(
                "COMPETITION_PLAYER_SERIES_BINDING_MISMATCH");
    }

    public SeriesGame getGame(String seriesId, int gameNumber) {
        return findGame(get(seriesId), gameNumber);
    }

    public ChildExecution createDraft(
            String seriesId, SeriesApiV1Dtos.DraftCreateRequest request
    ) {
        String payload = hash("CREATE_DRAFT", request.schemaVersion(),
                Long.toString(request.expectedRevision()), request.clientCommandId());
        try {
            ChildMutation result = repository.mutate(seriesId, aggregate -> {
                SeriesCommandReceipt prior = replay(aggregate, request.clientCommandId(),
                        "CREATE_DRAFT", payload);
                if (prior != null) {
                    rethrowFailure(aggregate, prior);
                    SeriesGame priorGame = receiptGame(aggregate, prior);
                    SeriesChildDraft priorChild = receiptChild(aggregate, prior);
                    return new SeriesRepository.Mutation<>(aggregate,
                            new ChildMutation(aggregate, priorGame, priorChild, true, null));
                }
                requireReceiptCapacity(aggregate);
                ensureActive(aggregate);
                SeriesGame game = aggregate.currentGame();
                stale(aggregate, request.expectedRevision());
                if (game.childDraft() != null
                        && game.childDraft().status() != PlayerDraftSessionStatus.CANCELLED
                        && game.childDraft().status() != PlayerDraftSessionStatus.EXPIRED) {
                    throw conflict(aggregate, "SERIES_ACTIVE_DRAFT_SESSION_EXISTS", false);
                }
                if (!drafts.canCompleteSeriesDraft(Set.copyOf(game.historyBefore()))) {
                    SeriesGame blocked = replaceGame(game, SeriesGameStatus.BLOCKED,
                            "HARD_FEARLESS_LEGAL_POOL_EXHAUSTED", game.childGeneration(),
                            game.childDraft(), null, game.completedDraft(),
                            game.resultSummary(), game.receipt());
                    long revision = aggregate.revision() + 1;
                    Instant now = repository.now();
                    Map<String, SeriesCommandReceipt> receipts = putReceipt(aggregate,
                            failedReceipt(request.clientCommandId(), "CREATE_DRAFT", payload,
                                    revision, SeriesStatus.BLOCKED, blocked, null,
                                    "HARD_FEARLESS_LEGAL_POOL_EXHAUSTED",
                                    "SERIES_HARD_FEARLESS_POOL_EXHAUSTED",
                                    HttpStatus.UNPROCESSABLE_ENTITY, false));
                    SeriesAggregate updated = aggregate.copy(revision,
                            SeriesStatus.BLOCKED, "HARD_FEARLESS_LEGAL_POOL_EXHAUSTED",
                            aggregate.score(), replaceLast(aggregate.games(), blocked),
                            aggregate.consumedPicks(), aggregate.historyHash(),
                            aggregate.winnerTeamCode(), now,
                            repository.parentExpiresAt(now), receipts);
                    return new SeriesRepository.Mutation<>(updated,
                            new ChildMutation(updated, blocked, null, false,
                                    "SERIES_HARD_FEARLESS_POOL_EXHAUSTED"));
                }
                Team blue = assemble(aggregate, game.blueTeamCode());
                Team red = assemble(aggregate, game.redTeamCode());
                DraftSelectionContext context = selectionContext(game, blue, red);
                DraftTeamContext blueContext = DraftTeamContext.from(blue);
                DraftTeamContext redContext = DraftTeamContext.from(red);
                var computation = drafts.newInteractiveComputationContext();
                PlayerControlledDraftEngine.Progress progress = drafts.startSeriesInteractive(
                        blueContext, redContext, context,
                        game.controlledSide(), Set.copyOf(game.historyBefore()), computation);
                Instant now = repository.now();
                int generation = game.childGeneration() + 1;
                String childId = SeriesIdentity.childId(
                        seriesId, game.gameNumber(), generation);
                var projection = progress.complete() ? null
                        : drafts.project(progress, blueContext, redContext, computation);
                PlayerDraftCompletionBinding completionBinding = progress.complete()
                        ? matches.bind(binding(aggregate, game), childId, generation, 0,
                                progress.result()) : null;
                SeriesChildDraft child = new SeriesChildDraft(
                        childId,
                        generation, 0, progress.complete() ? PlayerDraftSessionStatus.COMPLETED
                        : PlayerDraftSessionStatus.ACTIVE, now, now,
                        repository.childExpiresAt(now, repository.parentExpiresAt(now)),
                        progress, projection, completionBinding,
                        progress.complete() ? null : computation);
                SeriesGame nextGame = replaceGame(game,
                        progress.complete() ? SeriesGameStatus.DRAFT_COMPLETED
                                : SeriesGameStatus.DRAFT_ACTIVE,
                        null, generation, child, null, progress.result(), null, null);
                long revision = aggregate.revision() + 1;
                Map<String, SeriesCommandReceipt> receipts = putReceipt(aggregate,
                        succeededReceipt(request.clientCommandId(), "CREATE_DRAFT", payload,
                                revision, aggregate.status(), nextGame, child,
                                child.childId()));
                SeriesAggregate updated = aggregate.replaceCurrentGame(nextGame, revision,
                        now, repository.parentExpiresAt(now), receipts);
                return new SeriesRepository.Mutation<>(updated,
                        new ChildMutation(updated, nextGame, child, false, null));
            });
            if (result.errorCode() != null) throw unprocessable(
                    result.aggregate(), result.errorCode(), false);
            return new ChildExecution(result.aggregate(), result.game(), result.child(),
                    result.replayed());
        } catch (SeriesRepository.RepositoryFailure error) {
            throw repositoryError(error);
        }
    }

    public ChildExecution getDraft(String seriesId, int gameNumber) {
        SeriesAggregate aggregate = get(seriesId);
        SeriesGame game = findGame(aggregate, gameNumber);
        if (game.childDraft() == null) throw notFound(aggregate,
                "SERIES_DRAFT_SESSION_NOT_FOUND");
        if (game.childDraft().status() == PlayerDraftSessionStatus.EXPIRED) {
            throw gone(aggregate, "SERIES_DRAFT_SESSION_EXPIRED");
        }
        return new ChildExecution(aggregate, game, game.childDraft(), false);
    }

    public ChildExecution draftAction(
            String seriesId, int gameNumber, SeriesApiV1Dtos.DraftActionRequest request
    ) {
        ChampionId champion;
        try { champion = new ChampionId(request.championId()); }
        catch (IllegalArgumentException error) {
            throw SeriesApiV1Exception.of(HttpStatus.BAD_REQUEST,
                    "SERIES_INVALID_CHAMPION_ID", "championId",
                    "championId 형식이 올바르지 않습니다.", false, null, null);
        }
        String payload = hash("DRAFT_ACTION", request.schemaVersion(),
                Long.toString(request.expectedSeriesRevision()),
                Long.toString(request.expectedDraftRevision()), request.clientCommandId(),
                request.championId(), Integer.toString(gameNumber));
        try {
            ChildMutation result = repository.mutate(seriesId, aggregate -> {
                SeriesCommandReceipt prior = replay(aggregate, request.clientCommandId(),
                        "DRAFT_ACTION", payload);
                if (prior != null) {
                    rethrowFailure(aggregate, prior);
                    SeriesGame priorGame = receiptGame(aggregate, prior);
                    return new SeriesRepository.Mutation<>(aggregate,
                            new ChildMutation(aggregate, priorGame,
                                    receiptChild(aggregate, prior), true, null));
                }
                requireReceiptCapacity(aggregate);
                ensureActive(aggregate);
                wrongGame(aggregate, gameNumber);
                stale(aggregate, request.expectedSeriesRevision());
                SeriesGame game = aggregate.currentGame();
                SeriesChildDraft child = requireActionableChild(aggregate, game);
                if (child.revision() != request.expectedDraftRevision()) {
                    throw conflict(aggregate, "SERIES_STALE_DRAFT_REVISION", false);
                }
                Team blue = assemble(aggregate, game.blueTeamCode());
                Team red = assemble(aggregate, game.redTeamCode());
                DraftTeamContext blueContext = DraftTeamContext.from(blue);
                DraftTeamContext redContext = DraftTeamContext.from(red);
                PlayerControlledDraftEngine.Progress progress;
                var computation = child.computationContext() == null
                        ? drafts.newInteractiveComputationContext()
                        : child.computationContext();
                try {
                    var currentProjection = child.selectionProjection() == null
                            ? drafts.project(child.progress(), blueContext, redContext,
                                    computation)
                            : child.selectionProjection();
                    progress = drafts.selectProjected(child.progress(), blueContext,
                            redContext, selectionContext(game, blue, red), currentProjection,
                            champion, request.clientCommandId(), computation);
                } catch (IllegalArgumentException error) {
                    if (error.getMessage() != null
                            && error.getMessage().startsWith("Unknown ChampionId")) {
                        throw SeriesApiV1Exception.of(HttpStatus.BAD_REQUEST,
                                "SERIES_UNKNOWN_CHAMPION", "championId",
                                "지원하지 않는 챔피언 ID입니다.", false,
                                aggregate.revision(), aggregate.status());
                    }
                    throw unprocessable(aggregate, "SERIES_ILLEGAL_DRAFT_SELECTION", false);
                }
                Instant now = repository.now();
                long nextDraftRevision = child.revision() + 1;
                var nextProjection = progress.complete() ? null
                        : drafts.project(progress, blueContext, redContext, computation);
                PlayerDraftCompletionBinding completionBinding = progress.complete()
                        ? matches.bind(binding(aggregate, game), child.childId(),
                                child.generation(), nextDraftRevision, progress.result()) : null;
                SeriesChildDraft nextChild = new SeriesChildDraft(child.childId(),
                        child.generation(), nextDraftRevision,
                        progress.complete() ? PlayerDraftSessionStatus.COMPLETED
                                : PlayerDraftSessionStatus.ACTIVE,
                        child.createdAt(), now,
                        repository.childExpiresAt(now, repository.parentExpiresAt(now)),
                        progress, nextProjection, completionBinding,
                        progress.complete() ? null : computation);
                if (progress.complete()) computation.clear();
                SeriesGame nextGame = replaceGame(game,
                        progress.complete() ? SeriesGameStatus.DRAFT_COMPLETED
                                : SeriesGameStatus.DRAFT_ACTIVE,
                        null, game.childGeneration(), nextChild, null, progress.result(),
                        null, null);
                long revision = aggregate.revision() + 1;
                Map<String, SeriesCommandReceipt> receipts = putReceipt(aggregate,
                        succeededReceipt(request.clientCommandId(), "DRAFT_ACTION", payload,
                                revision, aggregate.status(), nextGame, nextChild,
                                progress.result() == null
                                        ? progress.turnEvidence().getLast().stateAfterHash()
                                        : progress.result().draftIdentity()));
                SeriesAggregate updated = aggregate.replaceCurrentGame(nextGame, revision,
                        now, repository.parentExpiresAt(now), receipts);
                return new SeriesRepository.Mutation<>(updated,
                        new ChildMutation(updated, nextGame, nextChild, false, null));
            });
            return new ChildExecution(result.aggregate(), result.game(), result.child(),
                    result.replayed());
        } catch (SeriesRepository.RepositoryFailure error) { throw repositoryError(error); }
    }

    public void cancelDraft(
            String seriesId, int gameNumber, SeriesApiV1Dtos.DraftCancelRequest request
    ) {
        String payload = hash("CANCEL_DRAFT", request.schemaVersion(),
                Long.toString(request.expectedRevision()), request.clientCommandId(),
                Integer.toString(gameNumber));
        try {
            repository.mutate(seriesId, aggregate -> {
                SeriesCommandReceipt prior = replay(aggregate, request.clientCommandId(),
                        "CANCEL_DRAFT", payload);
                if (prior != null) {
                    rethrowFailure(aggregate, prior);
                    return new SeriesRepository.Mutation<>(aggregate, null);
                }
                requireReceiptCapacity(aggregate);
                ensureActive(aggregate); wrongGame(aggregate, gameNumber);
                stale(aggregate, request.expectedRevision());
                SeriesGame game = aggregate.currentGame();
                SeriesChildDraft child = requireActionableChild(aggregate, game);
                if (child.status() == PlayerDraftSessionStatus.COMPLETED) {
                    throw conflict(aggregate, "SERIES_DRAFT_ALREADY_COMPLETED", false);
                }
                SeriesChildDraft cancelled = new SeriesChildDraft(child.childId(),
                        child.generation(), child.revision(), PlayerDraftSessionStatus.CANCELLED,
                        child.createdAt(), child.lastActivityAt(), child.expiresAt(),
                        child.progress());
                SeriesGame next = replaceGame(game, SeriesGameStatus.DRAFT_CANCELLED,
                        aggregate.origin().durableBound()
                                ? "PLAYER_SERIES_RESTART_REQUIRED" : "DRAFT_CANCELLED",
                        game.childGeneration(), cancelled, null,
                        null, null, null);
                long revision = aggregate.revision() + 1;
                Instant now = repository.now();
                SeriesStatus nextStatus = aggregate.origin().durableBound()
                        ? SeriesStatus.BLOCKED : aggregate.status();
                Map<String, SeriesCommandReceipt> receipts = putReceipt(aggregate,
                        succeededReceipt(request.clientCommandId(), "CANCEL_DRAFT", payload,
                                revision, nextStatus, next, cancelled,
                                child.childId()));
                SeriesAggregate updated = aggregate.copy(revision, nextStatus,
                        nextStatus == SeriesStatus.BLOCKED
                                ? "PLAYER_SERIES_RESTART_REQUIRED"
                                : aggregate.terminalReason(),
                        aggregate.score(), replaceLast(aggregate.games(), next),
                        aggregate.consumedPicks(), aggregate.historyHash(),
                        aggregate.winnerTeamCode(), now,
                        repository.parentExpiresAt(now), receipts);
                return new SeriesRepository.Mutation<>(updated, null);
            });
        } catch (SeriesRepository.RepositoryFailure error) { throw repositoryError(error); }
    }

    public SimulationExecution simulate(
            String seriesId, int gameNumber, SeriesApiV1Dtos.SimulateRequest request
    ) {
        String payload = hash("SIMULATE", request.schemaVersion(),
                Long.toString(request.expectedSeriesRevision()),
                Long.toString(request.expectedDraftRevision()), request.clientCommandId(),
                Integer.toString(gameNumber));
        ReservationStart start;
        try {
            start = repository.mutate(seriesId, aggregate -> {
                SeriesCommandReceipt prior = replay(aggregate, request.clientCommandId(),
                        "SIMULATE", payload);
                if (prior != null) {
                    rethrowFailure(aggregate, prior);
                    SeriesGame priorGame = receiptGame(aggregate, prior);
                    if (prior.completion() == SeriesCommandCompletion.IN_PROGRESS) {
                        SeriesSimulationReservation reservation = priorGame.reservation();
                        if (reservation == null
                                || !reservation.commandId().equals(prior.commandId())
                                || !reservation.payloadHash().equals(prior.payloadHash())) {
                            throw conflict(aggregate,
                                    "SERIES_COMMAND_REPLAY_IDENTITY_UNAVAILABLE", false);
                        }
                        return new SeriesRepository.Mutation<>(aggregate,
                                ReservationStart.inProgress(aggregate, priorGame));
                    }
                    return new SeriesRepository.Mutation<>(aggregate,
                            ReservationStart.replayed(aggregate, priorGame));
                }
                requireReceiptCapacity(aggregate);
                ensureActive(aggregate); wrongGame(aggregate, gameNumber);
                SeriesGame game = aggregate.currentGame();
                if (game.reservation() != null) {
                    throw conflict(aggregate, "SERIES_SIMULATION_ALREADY_IN_PROGRESS", true);
                }
                stale(aggregate, request.expectedSeriesRevision());
                SeriesChildDraft child = game.childDraft();
                if (child == null || child.status() != PlayerDraftSessionStatus.COMPLETED
                        || !child.progress().complete()) {
                    throw conflict(aggregate, "SERIES_DRAFT_NOT_COMPLETE", false);
                }
                if (child.revision() != request.expectedDraftRevision()) {
                    throw conflict(aggregate, "SERIES_STALE_DRAFT_REVISION", false);
                }
                String inputBinding = bindingHash(aggregate, game, child);
                Instant now = repository.now();
                long revision = aggregate.revision() + 1;
                SeriesSimulationReservation reservation = new SeriesSimulationReservation(
                        SeriesIdentity.reservationToken(seriesId, gameNumber,
                                request.clientCommandId(), revision),
                        request.clientCommandId(), payload, revision, child.revision(), now,
                        repository.reservationExpiresAt(now), inputBinding);
                SeriesGame reserved = replaceGame(game,
                        SeriesGameStatus.SIMULATION_IN_PROGRESS, null,
                        game.childGeneration(), child, reservation,
                        child.progress().result(), null, null);
                Map<String, SeriesCommandReceipt> receipts = putReceipt(aggregate,
                        inProgressReceipt(request.clientCommandId(), "SIMULATE", payload,
                                revision, aggregate.status(), reserved, child,
                                reservation.token()));
                SeriesAggregate updated = aggregate.replaceCurrentGame(reserved, revision,
                        now, repository.parentExpiresAt(now), receipts);
                return new SeriesRepository.Mutation<>(updated,
                        ReservationStart.execute(updated, reserved, reservation,
                                binding(updated, reserved), child.progress().result()));
            });
        } catch (SeriesRepository.RepositoryFailure error) { throw repositoryError(error); }

        if (start.replayed()) return new SimulationExecution(
                start.aggregate(), start.game(), null, true, false);
        if (start.inProgress()) return new SimulationExecution(
                start.aggregate(), start.game(), null, false, true);

        SeriesMatchExecutor.Execution execution;
        try {
            SeriesChildDraft child = start.game().childDraft();
            execution = child != null && child.completionBinding() != null
                    ? matches.executeTrusted(start.binding(), child.childId(),
                            child.generation(), child.revision(), child.completionBinding(),
                            start.completedDraft())
                    : matches.execute(start.binding(), start.completedDraft());
        } catch (SeriesMatchIntegrityException error) {
            SeriesAggregate blocked = finishFailure(seriesId, start,
                    "SERIES_ENGINE_OUTPUT_INTEGRITY_FAILED", true,
                    HttpStatus.INTERNAL_SERVER_ERROR, false);
            throw receiptFailure(blocked, blocked.commandReceipts().get(
                    start.reservation().commandId()));
        } catch (RuntimeException error) {
            SeriesAggregate failed = finishFailure(seriesId, start,
                    "SERIES_SIMULATION_FAILED", false,
                    HttpStatus.INTERNAL_SERVER_ERROR, true);
            throw receiptFailure(failed, failed.commandReceipts().get(
                    start.reservation().commandId()));
        }

        CommitResult committed;
        try {
            committed = repository.mutate(seriesId, aggregate -> commit(
                    aggregate, start, execution, request.clientCommandId(), payload));
        } catch (SeriesRepository.RepositoryFailure error) { throw repositoryError(error); }
        if (committed.errorCode() != null) throw receiptFailure(
                committed.aggregate(), committed.aggregate().commandReceipts().get(
                        request.clientCommandId()));
        return new SimulationExecution(committed.aggregate(), committed.game(),
                execution.output(), false, false);
    }

    public ReplayExecution replay(
            String seriesId, int gameNumber, SeriesApiV1Dtos.ReplayRequest request
    ) {
        SeriesAggregate before = get(seriesId);
        SeriesGame game = findGame(before, gameNumber);
        if (game.status() != SeriesGameStatus.COMMITTED || game.receipt() == null
                || game.completedDraft() == null) {
            throw conflict(before, "SERIES_GAME_NOT_COMMITTED", false);
        }
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();
        if (!game.receipt().policyId().equals(policy.policyId())
                || !game.receipt().policyHash().equals(policy.policyHash())
                || !game.receipt().runtimeProfileId().equals(
                policy.retainedRuntimeProfileId().name())
                || !game.receipt().configurationHash().equals(policy.configurationHash())
                || !game.receipt().engineImplementationVersion().equals(
                policy.engineImplementationVersion())
                || !game.receipt().activeGameplayRulesVersion().equals(
                policy.activeGameplayRulesVersion())) {
            throw conflict(before, "SERIES_GAME_REPLAY_IDENTITY_UNAVAILABLE", false);
        }
        SeriesMatchExecutor.Execution replay;
        try {
            SeriesChildDraft child = game.childDraft();
            replay = child != null && child.completionBinding() != null
                    ? matches.executeTrusted(binding(before, game), child.childId(),
                            child.generation(), child.revision(), child.completionBinding(),
                            game.completedDraft())
                    : matches.execute(binding(before, game), game.completedDraft());
        }
        catch (RuntimeException error) {
            throw conflict(before, "SERIES_GAME_REPLAY_IDENTITY_UNAVAILABLE", false);
        }
        if (!game.receipt().equals(replay.receipt())) {
            throw error(before, HttpStatus.INTERNAL_SERVER_ERROR,
                    "SERIES_GAME_RECEIPT_MISMATCH", false);
        }
        SeriesAggregate after = get(seriesId);
        if (!after.equals(before)) {
            throw error(before, HttpStatus.INTERNAL_SERVER_ERROR,
                    "SERIES_GAME_REPLAY_MUTATION_DETECTED", false);
        }
        return new ReplayExecution(before, game, replay.output());
    }

    public void cancel(String seriesId, SeriesApiV1Dtos.CancelRequest request) {
        String payload = hash("CANCEL_SERIES", request.schemaVersion(),
                Long.toString(request.expectedRevision()), request.clientCommandId());
        try {
            repository.mutate(seriesId, aggregate -> {
                SeriesCommandReceipt prior = replay(aggregate, request.clientCommandId(),
                        "CANCEL_SERIES", payload);
                if (prior != null) {
                    rethrowFailure(aggregate, prior);
                    return new SeriesRepository.Mutation<>(aggregate, null);
                }
                requireReceiptCapacity(aggregate);
                if (aggregate.status() == SeriesStatus.COMPLETED) {
                    throw conflict(aggregate, "SERIES_ALREADY_COMPLETED", false);
                }
                if (aggregate.status() == SeriesStatus.CANCELLED) {
                    throw conflict(aggregate, "SERIES_CANCELLED", false);
                }
                if (aggregate.status() == SeriesStatus.EXPIRED) {
                    throw gone(aggregate, "SERIES_EXPIRED");
                }
                stale(aggregate, request.expectedRevision());
                long revision = aggregate.revision() + 1;
                SeriesGame game = aggregate.currentGame();
                SeriesChildDraft child = game.childDraft();
                if (child != null && child.status() != PlayerDraftSessionStatus.CANCELLED
                        && child.status() != PlayerDraftSessionStatus.EXPIRED
                        && child.status() != PlayerDraftSessionStatus.SIMULATED) {
                    child = new SeriesChildDraft(child.childId(), child.generation(),
                            child.revision(), PlayerDraftSessionStatus.CANCELLED,
                            child.createdAt(), child.lastActivityAt(), child.expiresAt(),
                            child.progress());
                }
                SeriesGame cancelledGame = replaceGame(game,
                        game.status() == SeriesGameStatus.COMMITTED
                                ? SeriesGameStatus.COMMITTED
                                : SeriesGameStatus.DRAFT_CANCELLED,
                        aggregate.origin().durableBound()
                                ? "PLAYER_SERIES_RESTART_REQUIRED" : "SERIES_CANCELLED",
                        game.childGeneration(), child, null,
                        game.completedDraft(), game.resultSummary(), game.receipt());
                SeriesStatus cancelStatus = aggregate.origin().durableBound()
                        ? SeriesStatus.BLOCKED : SeriesStatus.CANCELLED;
                LinkedHashMap<String, SeriesCommandReceipt> receipts = new LinkedHashMap<>(
                        aggregate.commandReceipts());
                if (game.reservation() != null) {
                    SeriesCommandReceipt simulation = receipts.get(
                            game.reservation().commandId());
                    if (simulation != null
                            && simulation.completion() == SeriesCommandCompletion.IN_PROGRESS) {
                        receipts.put(simulation.commandId(), simulation.completed(
                                SeriesCommandCompletion.FAILED, revision,
                                cancelStatus, cancelledGame.status(),
                                "SERIES_CANCELLED", "SERIES_SIMULATION_RESERVATION_INVALIDATED",
                                HttpStatus.CONFLICT.value(), false));
                    }
                }
                SeriesCommandReceipt cancelReceipt = succeededReceipt(
                        request.clientCommandId(), "CANCEL_SERIES", payload, revision,
                        cancelStatus, cancelledGame, child,
                        aggregate.origin().durableBound()
                                ? "PLAYER_SERIES_RESTART_REQUIRED" : "CANCELLED");
                receipts.put(cancelReceipt.commandId(), cancelReceipt);
                SeriesAggregate cancelled = aggregate.copy(revision, cancelStatus,
                        aggregate.origin().durableBound()
                                ? "PLAYER_SERIES_RESTART_REQUIRED"
                                : "CANCELLED_BY_CLIENT",
                        aggregate.score(), replaceLast(
                        aggregate.games(), cancelledGame),
                        aggregate.consumedPicks(), aggregate.historyHash(),
                        aggregate.winnerTeamCode(), repository.now(), aggregate.expiresAt(),
                        receipts);
                return new SeriesRepository.Mutation<>(cancelled, null);
            });
        } catch (SeriesRepository.RepositoryFailure error) { throw repositoryError(error); }
    }

    private SeriesRepository.Mutation<CommitResult> commit(
            SeriesAggregate aggregate,
            ReservationStart start,
            SeriesMatchExecutor.Execution execution,
            String commandId,
            String payload
    ) {
        SeriesGame game = aggregate.currentGame();
        SeriesSimulationReservation reservation = game.reservation();
        Instant now = repository.now();
        if (reservation == null || !reservation.equals(start.reservation())
                || !now.isBefore(reservation.leaseExpiresAt())
                || aggregate.status() != SeriesStatus.ACTIVE
                || aggregate.revision() != start.aggregate().revision()
                || aggregate.revision() != reservation.reservedSeriesRevision()
                || !game.equals(start.game())
                || !bindingHash(aggregate, game, game.childDraft()).equals(
                reservation.inputBindingHash())) {
            throw conflict(aggregate, "SERIES_SIMULATION_RESERVATION_INVALIDATED", false);
        }
        if (execution.output().resultSummary().winner() == null) {
            SeriesGame blockedGame = replaceGame(game, SeriesGameStatus.BLOCKED,
                    "NO_DECISIVE_RESULT", game.childGeneration(), game.childDraft(), null,
                    game.completedDraft(), execution.output().resultSummary(),
                    execution.receipt());
            LinkedHashMap<String, SeriesCommandReceipt> receipts = new LinkedHashMap<>(
                    aggregate.commandReceipts());
            SeriesCommandReceipt command = requireInProgressReceipt(
                    aggregate, commandId, payload);
            receipts.put(commandId, command.completed(SeriesCommandCompletion.FAILED,
                    aggregate.revision(), SeriesStatus.BLOCKED, blockedGame.status(),
                    execution.receipt().outputHash(), "SERIES_GAME_NO_DECISIVE_RESULT",
                    HttpStatus.UNPROCESSABLE_ENTITY.value(), false));
            SeriesAggregate blocked = aggregate.copy(aggregate.revision(),
                    SeriesStatus.BLOCKED, "NO_DECISIVE_RESULT", aggregate.score(),
                    replaceLast(aggregate.games(), blockedGame), aggregate.consumedPicks(),
                    aggregate.historyHash(), null, aggregate.lastActivityAt(),
                    aggregate.expiresAt(), receipts);
            return new SeriesRepository.Mutation<>(blocked,
                    new CommitResult(blocked, blockedGame,
                            "SERIES_GAME_NO_DECISIVE_RESULT"));
        }
        TeamSide winnerSide = execution.output().resultSummary().winner();
        String winnerTeam = winnerSide == TeamSide.BLUE
                ? game.blueTeamCode() : game.redTeamCode();
        LinkedHashMap<String, Integer> score = new LinkedHashMap<>(aggregate.score());
        score.put(winnerTeam, score.get(winnerTeam) + 1);
        LinkedHashSet<ChampionId> consumed = new LinkedHashSet<>(aggregate.consumedPicks());
        consumed.addAll(game.completedDraft().bluePicks());
        consumed.addAll(game.completedDraft().redPicks());
        if (consumed.size() != aggregate.consumedPicks().size() + 10) {
            throw error(aggregate, HttpStatus.CONFLICT,
                    "SERIES_HARD_FEARLESS_HISTORY_MISMATCH", false);
        }
        SeriesChildDraft child = game.childDraft();
        SeriesChildDraft simulated = new SeriesChildDraft(child.childId(), child.generation(),
                child.revision(), PlayerDraftSessionStatus.SIMULATED, child.createdAt(),
                child.lastActivityAt(), child.expiresAt(), child.progress(), null,
                child.completionBinding(), null);
        SeriesGame committedGame = replaceGame(game, SeriesGameStatus.COMMITTED, null,
                game.childGeneration(), simulated, null, game.completedDraft(),
                execution.output().resultSummary(), execution.receipt());
        ArrayList<SeriesGame> games = new ArrayList<>(aggregate.games());
        games.set(games.size() - 1, committedGame);
        int committedCount = aggregate.committedGameCount() + 1;
        String historyHash = SeriesIdentity.historyHash(committedCount, consumed);
        SeriesStatus status;
        String reason = null;
        String seriesWinner = null;
        if (score.get(winnerTeam) >= aggregate.format().winsRequired()) {
            status = SeriesStatus.COMPLETED;
            seriesWinner = winnerTeam;
        } else {
            int nextNumber = committedCount + 1;
            if (nextNumber > aggregate.format().maximumGames()) {
                throw error(aggregate, HttpStatus.INTERNAL_SERVER_ERROR,
                        "SERIES_GAME_COUNT_INVARIANT_FAILED", false);
            }
            String nextBlue = CareerCompetitionSeriesBindingV1.loserRoFs(aggregate.competitionSidePolicy())
                    ? winnerTeam.equals(game.blueTeamCode()) ? game.redTeamCode() : game.blueTeamCode()
                    : game.redTeamCode();
            String nextRed = nextBlue.equals(game.blueTeamCode()) ? game.redTeamCode() : game.blueTeamCode();
            SeriesGame next = newGame(aggregate.seriesId(), nextNumber, nextBlue, nextRed,
                    aggregate.managedTeamCode(), aggregate.canonicalRootSeed(), historyHash,
                    consumed, aggregate.origin(), aggregate.rootSeed(),
                    aggregate.leagueSeedAnchorTeamCode());
            if (!drafts.canCompleteSeriesDraft(consumed)) {
                next = replaceGame(next, SeriesGameStatus.BLOCKED,
                        "HARD_FEARLESS_LEGAL_POOL_EXHAUSTED", 0, null, null,
                        null, null, null);
                status = SeriesStatus.BLOCKED;
                reason = "HARD_FEARLESS_LEGAL_POOL_EXHAUSTED";
            } else {
                status = SeriesStatus.ACTIVE;
            }
            games.add(next);
        }
        LinkedHashMap<String, SeriesCommandReceipt> receipts = new LinkedHashMap<>(
                aggregate.commandReceipts());
        SeriesCommandReceipt command = requireInProgressReceipt(aggregate, commandId, payload);
        receipts.put(commandId, command.completed(SeriesCommandCompletion.SUCCEEDED,
                aggregate.revision(), status, committedGame.status(),
                execution.receipt().outputHash(), null, null, false));
        SeriesAggregate updated = aggregate.copy(aggregate.revision(), status, reason,
                score, games, consumed, historyHash, seriesWinner,
                aggregate.lastActivityAt(), aggregate.expiresAt(), receipts);
        return new SeriesRepository.Mutation<>(updated,
                new CommitResult(updated, committedGame, null));
    }

    private SeriesAggregate finishFailure(
            String seriesId, ReservationStart start, String reason, boolean blocked,
            HttpStatus httpStatus, boolean retryable
    ) {
        try {
            return repository.mutate(seriesId, aggregate -> {
                SeriesGame game = aggregate.currentGame();
                if (game.reservation() == null
                        || !game.reservation().equals(start.reservation())
                        || !game.equals(start.game())
                        || aggregate.revision() != start.aggregate().revision()) {
                    throw conflict(aggregate, "SERIES_SIMULATION_RESERVATION_INVALIDATED", false);
                }
                boolean effectiveBlocked = blocked
                        || aggregate.origin().durableBound();
                String effectiveReason = aggregate.origin().durableBound()
                        && !blocked ? "PLAYER_SERIES_RESTART_REQUIRED" : reason;
                SeriesGame failed = replaceGame(game,
                        effectiveBlocked ? SeriesGameStatus.BLOCKED
                                : SeriesGameStatus.SIMULATION_FAILED_RETRYABLE,
                        effectiveReason, game.childGeneration(), game.childDraft(), null,
                        game.completedDraft(), null, null);
                LinkedHashMap<String, SeriesCommandReceipt> receipts = new LinkedHashMap<>(
                        aggregate.commandReceipts());
                SeriesCommandReceipt command = requireInProgressReceipt(aggregate,
                        start.reservation().commandId(), start.reservation().payloadHash());
                SeriesStatus nextStatus = effectiveBlocked
                        ? SeriesStatus.BLOCKED : SeriesStatus.ACTIVE;
                receipts.put(command.commandId(), command.completed(
                        SeriesCommandCompletion.FAILED, aggregate.revision(), nextStatus,
                        failed.status(), effectiveReason, effectiveReason,
                        httpStatus.value(), effectiveBlocked ? false : retryable));
                SeriesAggregate updated = aggregate.copy(aggregate.revision(),
                        nextStatus,
                        effectiveBlocked ? effectiveReason : null, aggregate.score(),
                        replaceLast(aggregate.games(), failed), aggregate.consumedPicks(),
                        aggregate.historyHash(), aggregate.winnerTeamCode(),
                        aggregate.lastActivityAt(), aggregate.expiresAt(),
                        receipts);
                return new SeriesRepository.Mutation<>(updated, updated);
            });
        } catch (SeriesRepository.RepositoryFailure error) { throw repositoryError(error); }
    }

    private void validateCreate(SeriesApiV1Dtos.CreateRequest request) {
        if (request.teamACode().equals(request.teamBCode())) {
            throw SeriesApiV1Exception.of(HttpStatus.BAD_REQUEST,
                    "SERIES_SAME_TEAM_NOT_ALLOWED", "teamBCode",
                    "두 참가 팀은 서로 달라야 합니다.", false, null, null);
        }
        for (String code : List.of(request.teamACode(), request.teamBCode())) {
            if (!teams.teamCodes().contains(code)) {
                throw SeriesApiV1Exception.of(HttpStatus.BAD_REQUEST,
                        "SERIES_UNKNOWN_TEAM", "teamCode", "지원하지 않는 팀 코드입니다.",
                        false, null, null);
            }
        }
        Set<String> participants = Set.of(request.teamACode(), request.teamBCode());
        if (!participants.contains(request.managedTeamCode())) {
            throw SeriesApiV1Exception.of(HttpStatus.BAD_REQUEST,
                    "SERIES_INVALID_MANAGED_TEAM", "managedTeamCode",
                    "managedTeamCode는 참가 팀이어야 합니다.", false, null, null);
        }
        if (!participants.contains(request.game1BlueTeamCode())) {
            throw SeriesApiV1Exception.of(HttpStatus.BAD_REQUEST,
                    "SERIES_WRONG_SIDE_CONTEXT", "game1BlueTeamCode",
                    "Game 1 BLUE 팀은 참가 팀이어야 합니다.", false, null, null);
        }
    }

    private static String canonicalCreate(SeriesApiV1Dtos.CreateRequest request) {
        return "createSchema=SERIES_CREATE_COMMAND_V1\n"
                + "requestSchema=" + request.schemaVersion() + '\n'
                + "format=" + request.format() + '\n'
                + "teamACode=" + request.teamACode() + '\n'
                + "teamBCode=" + request.teamBCode() + '\n'
                + "managedTeamCode=" + request.managedTeamCode() + '\n'
                + "game1BlueTeamCode=" + request.game1BlueTeamCode() + '\n'
                + "rootSeed=" + request.rootSeed() + '\n'
                + "clientCommandId=" + request.clientCommandId() + '\n';
    }

    private static SeriesGame newGame(
            String seriesId, int number, String blue, String red, String managed,
            String rootSeed, String historyHash, Set<ChampionId> history
    ) {
        return newGame(seriesId, number, blue, red, managed, rootSeed, historyHash,
                history, SeriesOrigin.STANDALONE, Long.parseLong(rootSeed), null);
    }

    private static SeriesGame newGame(
            String seriesId, int number, String blue, String red, String managed,
            String rootSeed, String historyHash, Set<ChampionId> history,
            SeriesOrigin origin, long parsedRootSeed, String seedAnchorTeamCode
    ) {
        TeamSide controlled = managed.equals(blue) ? TeamSide.BLUE : TeamSide.RED;
        long seed = origin.durableBound()
                ? LeagueIdentity.gameSeed(seriesId, parsedRootSeed, number, blue, red,
                seedAnchorTeamCode, historyHash)
                : SeriesIdentity.deriveGameSeed(seriesId, rootSeed, number, blue, red,
                managed, historyHash);
        return new SeriesGame(SeriesIdentity.gameId(seriesId, number), number, blue, red,
                controlled, seed, history.stream().sorted(
                java.util.Comparator.comparing(ChampionId::value)).toList(), historyHash,
                SeriesGameStatus.DRAFT_PENDING, null, 0, null, null, null, null, null);
    }

    private DraftSelectionContext selectionContext(SeriesGame game, Team blue, Team red) {
        return RealDraftSelectionContextFactory.create(game.matchSeed(), game.blueTeamCode(),
                blue, game.redTeamCode(), red, game.gameNumber(),
                Set.copyOf(game.historyBefore()));
    }

    private Team assemble(SeriesAggregate aggregate, String code) {
        return aggregate.frozenCompetitionRosters() == null ? teams.assemble(code)
                : aggregate.frozenCompetitionRosters().assemble(code);
    }

    private static PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding binding(
            SeriesAggregate aggregate, SeriesGame game
    ) {
        return new PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding(
                aggregate.seriesId(), game.gameId(), game.gameNumber(), game.blueTeamCode(),
                game.redTeamCode(), game.controlledSide(), game.matchSeed(),
                Set.copyOf(game.historyBefore()), game.historyBeforeHash(), aggregate.frozenCompetitionRosters());
    }

    private static String bindingHash(
            SeriesAggregate aggregate, SeriesGame game, SeriesChildDraft child
    ) {
        return SeriesIdentity.sha256("bindingSchema=SERIES_SIMULATION_INPUT_BINDING_V1\n"
                + "seriesId=" + aggregate.seriesId() + '\n'
                + "gameId=" + game.gameId() + '\n'
                + "gameNumber=" + game.gameNumber() + '\n'
                + "blueTeamCode=" + game.blueTeamCode() + '\n'
                + "redTeamCode=" + game.redTeamCode() + '\n'
                + "controlledSide=" + game.controlledSide() + '\n'
                + "matchSeed=" + game.matchSeed() + '\n'
                + "historyBeforeHash=" + game.historyBeforeHash() + '\n'
                + "childId=" + child.childId() + '\n'
                + "childRevision=" + child.revision() + '\n'
                + "draftIdentity=" + child.progress().result().draftIdentity() + '\n');
    }

    private static SeriesGame replaceGame(
            SeriesGame game, SeriesGameStatus status, String reason, int generation,
            SeriesChildDraft child, SeriesSimulationReservation reservation,
            com.lolfm.draft.PlayerControlledDraftResult completedDraft,
            MatchEngineV1Output.MatchResultSummaryV1 result,
            SeriesGameReceipt receipt
    ) {
        return new SeriesGame(game.gameId(), game.gameNumber(), game.blueTeamCode(),
                game.redTeamCode(), game.controlledSide(), game.matchSeed(),
                game.historyBefore(), game.historyBeforeHash(), status, reason, generation,
                child, reservation, completedDraft, result, receipt);
    }

    private static List<SeriesGame> replaceLast(List<SeriesGame> games, SeriesGame replacement) {
        ArrayList<SeriesGame> values = new ArrayList<>(games);
        values.set(values.size() - 1, replacement);
        return values;
    }

    private void requireReceiptCapacity(SeriesAggregate aggregate) {
        if (!repository.canCreateCommandReceipt(aggregate.commandReceipts().size())) {
            throw conflict(aggregate, "SERIES_COMMAND_RECEIPT_CAPACITY_REACHED", false);
        }
    }

    private static Map<String, SeriesCommandReceipt> putReceipt(
            SeriesAggregate aggregate, SeriesCommandReceipt receipt
    ) {
        LinkedHashMap<String, SeriesCommandReceipt> receipts = new LinkedHashMap<>(
                aggregate.commandReceipts());
        if (receipts.putIfAbsent(receipt.commandId(), receipt) != null) {
            throw new IllegalStateException("Series command receipt already exists");
        }
        return receipts;
    }

    private static SeriesCommandReceipt succeededReceipt(
            String commandId, String type, String payload, long revision,
            SeriesStatus seriesStatus, SeriesGame game, SeriesChildDraft child,
            String resultIdentity
    ) {
        return receipt(commandId, type, payload, SeriesCommandCompletion.SUCCEEDED,
                revision, seriesStatus, game, child, resultIdentity,
                null, null, false);
    }

    private static SeriesCommandReceipt inProgressReceipt(
            String commandId, String type, String payload, long revision,
            SeriesStatus seriesStatus, SeriesGame game, SeriesChildDraft child,
            String resultIdentity
    ) {
        return receipt(commandId, type, payload, SeriesCommandCompletion.IN_PROGRESS,
                revision, seriesStatus, game, child, resultIdentity,
                null, null, false);
    }

    private static SeriesCommandReceipt failedReceipt(
            String commandId, String type, String payload, long revision,
            SeriesStatus seriesStatus, SeriesGame game, SeriesChildDraft child,
            String resultIdentity, String errorCode, HttpStatus httpStatus,
            boolean retryable
    ) {
        return receipt(commandId, type, payload, SeriesCommandCompletion.FAILED,
                revision, seriesStatus, game, child, resultIdentity,
                errorCode, httpStatus.value(), retryable);
    }

    private static SeriesCommandReceipt receipt(
            String commandId, String type, String payload,
            SeriesCommandCompletion completion, long revision,
            SeriesStatus seriesStatus, SeriesGame game, SeriesChildDraft child,
            String resultIdentity, String errorCode, Integer httpStatus,
            boolean retryable
    ) {
        return new SeriesCommandReceipt(commandId, type, payload, completion,
                revision, seriesStatus, game.gameNumber(), game.gameId(), game.status(),
                child == null ? null : child.revision(),
                child == null ? null : child.childId(),
                child == null ? null : child.generation(), child, resultIdentity,
                errorCode, httpStatus, retryable);
    }

    private static SeriesCommandReceipt requireInProgressReceipt(
            SeriesAggregate aggregate, String commandId, String payload
    ) {
        SeriesCommandReceipt receipt = aggregate.commandReceipts().get(commandId);
        if (receipt == null || receipt.completion() != SeriesCommandCompletion.IN_PROGRESS
                || !receipt.payloadHash().equals(payload)) {
            throw conflict(aggregate, "SERIES_COMMAND_REPLAY_IDENTITY_UNAVAILABLE", false);
        }
        return receipt;
    }

    private static SeriesCommandReceipt replay(
            SeriesAggregate aggregate, String commandId, String type, String payload
    ) {
        SeriesCommandReceipt prior = aggregate.commandReceipts().get(commandId);
        if (prior == null) return null;
        if (!prior.commandType().equals(type) || !prior.payloadHash().equals(payload)) {
            throw conflict(aggregate, "SERIES_COMMAND_ID_PAYLOAD_CONFLICT", false);
        }
        return prior;
    }

    private static void rethrowFailure(
            SeriesAggregate currentAggregate, SeriesCommandReceipt receipt
    ) {
        if (receipt.completion() == SeriesCommandCompletion.FAILED) {
            throw receiptFailure(currentAggregate, receipt);
        }
    }

    private static SeriesApiV1Exception receiptFailure(
            SeriesAggregate currentAggregate, SeriesCommandReceipt receipt
    ) {
        if (receipt == null || receipt.completion() != SeriesCommandCompletion.FAILED
                || receipt.httpStatus() == null || receipt.errorCode() == null) {
            throw new IllegalStateException("Series failed receipt unavailable");
        }
        HttpStatus status = HttpStatus.resolve(receipt.httpStatus());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return SeriesApiV1Exception.of(status, receipt.errorCode(), null,
                "Series 명령을 처리할 수 없습니다.", receipt.retryable(),
                currentAggregate.revision(), currentAggregate.status());
    }

    private static SeriesGame receiptGame(
            SeriesAggregate aggregate, SeriesCommandReceipt receipt
    ) {
        SeriesGame game = findGame(aggregate, receipt.gameNumber());
        if (!game.gameId().equals(receipt.gameId())) {
            throw conflict(aggregate, "SERIES_COMMAND_REPLAY_IDENTITY_UNAVAILABLE", false);
        }
        return game;
    }

    private static SeriesChildDraft receiptChild(
            SeriesAggregate aggregate, SeriesCommandReceipt receipt
    ) {
        SeriesChildDraft child = receipt.childSnapshot();
        if (child == null || receipt.childId() == null || receipt.childGeneration() == null
                || !child.childId().equals(receipt.childId())
                || child.generation() != receipt.childGeneration()) {
            throw conflict(aggregate, "SERIES_COMMAND_REPLAY_IDENTITY_UNAVAILABLE", false);
        }
        return child;
    }

    private static String hash(String... fields) {
        StringBuilder canonical = new StringBuilder("payloadSchema=SERIES_COMMAND_PAYLOAD_V1\n");
        for (int index = 0; index < fields.length; index++) {
            canonical.append("field").append(index).append('=').append(fields[index]).append('\n');
        }
        return SeriesIdentity.sha256(canonical.toString());
    }

    private static SeriesChildDraft requireActionableChild(
            SeriesAggregate aggregate, SeriesGame game
    ) {
        SeriesChildDraft child = game.childDraft();
        if (child == null) throw notFound(aggregate, "SERIES_DRAFT_SESSION_NOT_FOUND");
        if (child.status() == PlayerDraftSessionStatus.EXPIRED) {
            throw gone(aggregate, "SERIES_DRAFT_SESSION_EXPIRED");
        }
        if (child.status() != PlayerDraftSessionStatus.ACTIVE
                && child.status() != PlayerDraftSessionStatus.COMPLETED) {
            throw conflict(aggregate, "SERIES_CROSS_CONTEXT_DRAFT_SESSION", false);
        }
        return child;
    }

    private static void wrongGame(SeriesAggregate aggregate, int gameNumber) {
        if (aggregate.currentGame().gameNumber() != gameNumber) {
            throw conflict(aggregate, "SERIES_WRONG_GAME_NUMBER", false);
        }
    }

    private static void stale(SeriesAggregate aggregate, long expected) {
        if (aggregate.revision() != expected) {
            throw conflict(aggregate, "SERIES_STALE_REVISION", false);
        }
    }

    private static void ensureActive(SeriesAggregate aggregate) {
        switch (aggregate.status()) {
            case ACTIVE -> { }
            case COMPLETED -> throw conflict(aggregate, "SERIES_ALREADY_COMPLETED", false);
            case CANCELLED -> throw conflict(aggregate, "SERIES_CANCELLED", false);
            case BLOCKED -> throw conflict(aggregate, "SERIES_BLOCKED", false);
            case EXPIRED -> throw gone(aggregate, "SERIES_EXPIRED");
        }
    }

    private static SeriesAggregate requireVisible(SeriesAggregate aggregate) {
        if (aggregate.status() == SeriesStatus.EXPIRED) {
            throw gone(aggregate, "SERIES_EXPIRED");
        }
        return aggregate;
    }

    private static SeriesGame findGame(SeriesAggregate aggregate, int number) {
        return aggregate.games().stream().filter(game -> game.gameNumber() == number)
                .findFirst().orElseThrow(() -> notFound(aggregate, "SERIES_GAME_NOT_FOUND"));
    }

    private static SeriesApiV1Exception repositoryError(
            SeriesRepository.RepositoryFailure error
    ) {
        return switch (error.getMessage()) {
            case "SERIES_NOT_FOUND" -> SeriesApiV1Exception.of(HttpStatus.NOT_FOUND,
                    "SERIES_NOT_FOUND", null, "Series를 찾을 수 없습니다.",
                    false, null, null);
            case "SERIES_COMMAND_ID_PAYLOAD_CONFLICT" -> SeriesApiV1Exception.of(
                    HttpStatus.CONFLICT, error.getMessage(), "clientCommandId",
                    "같은 command ID가 다른 payload에 사용되었습니다.",
                    false, null, null);
            case "SERIES_CAPACITY_REACHED" -> SeriesApiV1Exception.of(HttpStatus.CONFLICT,
                    error.getMessage(), null, "동시에 유지할 수 있는 Series 수를 초과했습니다.",
                    true, null, null);
            default -> SeriesApiV1Exception.internal("SERIES_INTERNAL_ERROR", error);
        };
    }

    private static SeriesApiV1Exception conflict(
            SeriesAggregate aggregate, String code, boolean retryable
    ) {
        return error(aggregate, HttpStatus.CONFLICT, code, retryable);
    }

    private static SeriesApiV1Exception unprocessable(
            SeriesAggregate aggregate, String code, boolean retryable
    ) {
        return error(aggregate, HttpStatus.UNPROCESSABLE_ENTITY, code, retryable);
    }

    private static SeriesApiV1Exception notFound(SeriesAggregate aggregate, String code) {
        return error(aggregate, HttpStatus.NOT_FOUND, code, false);
    }

    private static SeriesApiV1Exception gone(SeriesAggregate aggregate, String code) {
        return error(aggregate, HttpStatus.GONE, code, false);
    }

    private static SeriesApiV1Exception error(
            SeriesAggregate aggregate, HttpStatus status, String code, boolean retryable
    ) {
        return SeriesApiV1Exception.of(status, code, null,
                "Series 명령을 처리할 수 없습니다.", retryable,
                aggregate.revision(), aggregate.status());
    }

    public record CreateExecution(SeriesAggregate aggregate, boolean replayed) {}
    public record ChildExecution(
            SeriesAggregate aggregate, SeriesGame game, SeriesChildDraft child,
            boolean replayed
    ) {}
    public record SimulationExecution(
            SeriesAggregate aggregate, SeriesGame game, MatchEngineV1Output output,
            boolean replayed, boolean inProgress
    ) {}
    public record ReplayExecution(
            SeriesAggregate aggregate, SeriesGame game, MatchEngineV1Output output
    ) {}

    private record ChildMutation(
            SeriesAggregate aggregate, SeriesGame game, SeriesChildDraft child,
            boolean replayed,
            String errorCode
    ) {}
    private record ReservationStart(
            SeriesAggregate aggregate, SeriesGame game,
            SeriesSimulationReservation reservation,
            PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding binding,
            com.lolfm.draft.PlayerControlledDraftResult completedDraft,
            boolean replayed, boolean inProgress
    ) {
        static ReservationStart execute(
                SeriesAggregate aggregate, SeriesGame game,
                SeriesSimulationReservation reservation,
                PlayerControlledDraftMatchInputBoundary.SeriesPlayerDraftBinding binding,
                com.lolfm.draft.PlayerControlledDraftResult draft
        ) { return new ReservationStart(aggregate, game, reservation, binding, draft,
                false, false); }
        static ReservationStart replayed(SeriesAggregate aggregate, SeriesGame game) {
            return new ReservationStart(aggregate, game, null, null, null, true, false);
        }
        static ReservationStart inProgress(SeriesAggregate aggregate, SeriesGame game) {
            return new ReservationStart(aggregate, game, game.reservation(), null, null,
                    false, true);
        }
    }
    private record CommitResult(
            SeriesAggregate aggregate, SeriesGame game, String errorCode
    ) {}
}
