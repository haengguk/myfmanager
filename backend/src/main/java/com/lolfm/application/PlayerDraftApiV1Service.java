package com.lolfm.application;

import com.lolfm.champion.ChampionId;
import com.lolfm.controller.PlayerDraftApiV1Exception;
import com.lolfm.domain.Team;
import com.lolfm.draft.DraftSelectionContext;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.dto.PlayerDraftApiV1Dtos;
import com.lolfm.player.LckTeamAssembler;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Atomic session workflow from player actions through explicit Production V9 simulation. */
@Service
public final class PlayerDraftApiV1Service {
    private final LckTeamAssembler teams;
    private final PlayerControlledDraftEngine drafts;
    private final PlayerDraftSessionRepository sessions;
    private final PlayerDraftMatchSimulationExecutor simulations;

    public PlayerDraftApiV1Service(
            LckTeamAssembler teams,
            PlayerControlledDraftEngine drafts,
            PlayerDraftSessionRepository sessions,
            PlayerDraftMatchSimulationExecutor simulations
    ) {
        this.teams = Objects.requireNonNull(teams, "teams");
        this.drafts = Objects.requireNonNull(drafts, "drafts");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.simulations = Objects.requireNonNull(simulations, "simulations");
    }

    public PlayerDraftSessionView start(PlayerDraftApiV1Dtos.StartRequest request) {
        validateStart(request);
        long seed;
        try {
            seed = request.seedAsLong();
        } catch (NumberFormatException error) {
            throw PlayerDraftApiV1Exception.badRequest(
                    "INVALID_SEED", "seed",
                    "seed는 canonical signed 64-bit decimal string이어야 합니다.");
        }
        Team blue = teams.assemble(request.blueTeamCode());
        Team red = teams.assemble(request.redTeamCode());
        DraftTeamContext blueContext = DraftTeamContext.from(blue);
        DraftTeamContext redContext = DraftTeamContext.from(red);
        DraftSelectionContext selectionContext = selectionContext(
                request.blueTeamCode(), blue, request.redTeamCode(), red, seed);
        var computation = drafts.newInteractiveComputationContext();
        PlayerControlledDraftEngine.Progress progress = drafts.startInteractive(
                blueContext, redContext, selectionContext, request.controlledSide(),
                computation);
        PlayerControlledDraftEngine.AuthoritativeSelectionProjection projection =
                progress.complete() ? null
                        : drafts.project(progress, blueContext, redContext, computation);
        Instant created = sessions.now();
        String sessionId = UUID.randomUUID().toString();
        PlayerDraftCompletionBinding binding = progress.complete()
                ? simulations.bind(sessionId, 0, request.blueTeamCode(),
                        request.redTeamCode(), request.controlledSide(), seed,
                        progress.result()) : null;
        PlayerDraftSession session = new PlayerDraftSession(
                sessionId, 0, progress.complete()
                ? PlayerDraftSessionStatus.COMPLETED : PlayerDraftSessionStatus.ACTIVE,
                request.blueTeamCode(), request.redTeamCode(), request.controlledSide(),
                seed, created, sessions.expiresAt(created), progress, projection, binding,
                progress.complete() ? null : computation, Map.of(), null);
        try {
            return sessions.create(session).view();
        } catch (PlayerDraftSessionRepository.RepositoryFailure error) {
            throw repositoryFailure(error);
        }
    }

    public PlayerDraftSessionView get(String sessionId) {
        try {
            PlayerDraftSession session = sessions.get(sessionId);
            if (session.status() == PlayerDraftSessionStatus.EXPIRED) {
                throw PlayerDraftApiV1Exception.gone();
            }
            return session.view();
        } catch (PlayerDraftSessionRepository.RepositoryFailure error) {
            throw repositoryFailure(error);
        }
    }

    public PlayerDraftSessionView action(
            String sessionId, PlayerDraftApiV1Dtos.ActionRequest request
    ) {
        ChampionId championId;
        try {
            championId = new ChampionId(request.championId());
        } catch (IllegalArgumentException error) {
            throw PlayerDraftApiV1Exception.badRequest(
                    "INVALID_CHAMPION_ID", "championId",
                    "championId 형식이 올바르지 않습니다.");
        }
        try {
            return sessions.mutate(sessionId, session -> {
                PlayerDraftSession.ActionReceipt prior =
                        session.actionReceipts().get(request.clientActionId());
                if (prior != null) {
                    if (prior.expectedRevision() != request.expectedRevision()
                            || !prior.championId().equals(championId)) {
                        throw PlayerDraftApiV1Exception.conflict(
                                "CLIENT_ACTION_ID_PAYLOAD_CONFLICT", "clientActionId",
                                "같은 clientActionId가 다른 요청 내용에 사용되었습니다.");
                    }
                    PlayerDraftSessionView replay = session.view(
                            prior.resultingRevision(), prior.resultingStatus(),
                            prior.resultingProgress(), prior.resultingProjection());
                    return new PlayerDraftSessionRepository.Mutation<>(session, replay);
                }
                ensureActionable(session);
                if (request.expectedRevision() != session.revision()) {
                    throw PlayerDraftApiV1Exception.conflict(
                            "STALE_DRAFT_REVISION", "expectedRevision",
                            "드래프트 revision이 최신 상태와 일치하지 않습니다.");
                }
                Team blue = teams.assemble(session.blueTeamCode());
                Team red = teams.assemble(session.redTeamCode());
                DraftTeamContext blueContext = DraftTeamContext.from(blue);
                DraftTeamContext redContext = DraftTeamContext.from(red);
                DraftSelectionContext context = selectionContext(
                        session.blueTeamCode(), blue, session.redTeamCode(), red,
                        session.matchSeed());
                PlayerControlledDraftEngine.Progress progress;
                var computation = session.computationContext() == null
                        ? drafts.newInteractiveComputationContext()
                        : session.computationContext();
                try {
                    var currentProjection = session.selectionProjection() == null
                            ? drafts.project(session.progress(), blueContext, redContext,
                                    computation)
                            : session.selectionProjection();
                    progress = drafts.selectProjected(session.progress(), blueContext,
                            redContext, context, currentProjection, championId,
                            request.clientActionId(), computation);
                } catch (IllegalArgumentException error) {
                    String message = error.getMessage();
                    if (message != null && message.startsWith("Unknown ChampionId")) {
                        throw PlayerDraftApiV1Exception.badRequest(
                                "UNKNOWN_CHAMPION", "championId",
                                "지원하지 않는 챔피언 ID입니다.");
                    }
                    throw PlayerDraftApiV1Exception.unprocessable(
                            "ILLEGAL_DRAFT_SELECTION", "championId",
                            "현재 드래프트 상태에서 선택할 수 없는 챔피언입니다.", error);
                }
                long nextRevision = session.revision() + 1;
                PlayerDraftSessionStatus status = progress.complete()
                        ? PlayerDraftSessionStatus.COMPLETED : PlayerDraftSessionStatus.ACTIVE;
                PlayerControlledDraftEngine.AuthoritativeSelectionProjection nextProjection =
                        progress.complete() ? null
                                : drafts.project(progress, blueContext, redContext, computation);
                PlayerDraftCompletionBinding completionBinding = progress.complete()
                        ? simulations.bind(session.sessionId(), nextRevision,
                                session.blueTeamCode(), session.redTeamCode(),
                                session.controlledSide(), session.matchSeed(),
                                progress.result()) : null;
                LinkedHashMap<String, PlayerDraftSession.ActionReceipt> receipts =
                        new LinkedHashMap<>(session.actionReceipts());
                receipts.put(request.clientActionId(), new PlayerDraftSession.ActionReceipt(
                        request.expectedRevision(), championId, nextRevision, status, progress,
                        nextProjection));
                PlayerDraftSession updated = session.withAction(
                        nextRevision, progress, nextProjection, completionBinding,
                        computation, receipts);
                return new PlayerDraftSessionRepository.Mutation<>(updated, updated.view());
            });
        } catch (PlayerDraftSessionRepository.RepositoryFailure error) {
            throw repositoryFailure(error);
        }
    }

    public SimulationExecution simulate(
            String sessionId, PlayerDraftApiV1Dtos.SimulateRequest request
    ) {
        try {
            return sessions.mutate(sessionId, session -> {
                if (session.status() == PlayerDraftSessionStatus.CANCELLED) {
                    throw PlayerDraftApiV1Exception.conflict(
                            "PLAYER_DRAFT_SESSION_CANCELLED", null,
                            "취소된 드래프트 세션은 시뮬레이션할 수 없습니다.");
                }
                if (!session.progress().complete()) {
                    throw PlayerDraftApiV1Exception.conflict(
                            "PLAYER_DRAFT_NOT_COMPLETE", null,
                            "20개 드래프트 턴을 완료한 뒤 시뮬레이션해야 합니다.");
                }
                try {
                    PlayerDraftMatchSimulationExecutor.Execution execution =
                            simulations.execute(session);
                    if (session.simulationReceipt() != null) {
                        if (!session.simulationReceipt().equals(execution.receipt())) {
                            throw PlayerDraftApiV1Exception.internal(
                                    new IllegalStateException(
                                            "PLAYER_DRAFT_SIMULATION_RECEIPT_MISMATCH"));
                        }
                        return new PlayerDraftSessionRepository.Mutation<>(session,
                                new SimulationExecution(session.view(), execution.output()));
                    }
                    PlayerDraftSession updated = session.withSimulationReceipt(
                            execution.receipt());
                    return new PlayerDraftSessionRepository.Mutation<>(updated,
                            new SimulationExecution(updated.view(), execution.output()));
                } catch (PlayerDraftApiV1Exception error) {
                    throw error;
                } catch (RuntimeException error) {
                    throw PlayerDraftApiV1Exception.internal(error);
                }
            });
        } catch (PlayerDraftSessionRepository.RepositoryFailure error) {
            throw repositoryFailure(error);
        }
    }

    public void cancel(String sessionId) {
        try {
            sessions.mutate(sessionId, session -> {
                PlayerDraftSession cancelled = session.withStatus(
                        PlayerDraftSessionStatus.CANCELLED);
                return new PlayerDraftSessionRepository.Mutation<>(cancelled, null);
            });
        } catch (PlayerDraftSessionRepository.RepositoryFailure error) {
            throw repositoryFailure(error);
        }
    }

    private void validateStart(PlayerDraftApiV1Dtos.StartRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.blueTeamCode().equals(request.redTeamCode())) {
            throw PlayerDraftApiV1Exception.badRequest(
                    "SAME_TEAM_NOT_ALLOWED", "redTeamCode",
                    "BLUE 팀과 RED 팀은 서로 달라야 합니다.");
        }
        if (!teams.teamCodes().contains(request.blueTeamCode())) {
            throw PlayerDraftApiV1Exception.badRequest(
                    "UNKNOWN_TEAM", "blueTeamCode", "지원하지 않는 BLUE 팀 코드입니다.");
        }
        if (!teams.teamCodes().contains(request.redTeamCode())) {
            throw PlayerDraftApiV1Exception.badRequest(
                    "UNKNOWN_TEAM", "redTeamCode", "지원하지 않는 RED 팀 코드입니다.");
        }
    }

    private static void ensureActionable(PlayerDraftSession session) {
        if (session.status() == PlayerDraftSessionStatus.CANCELLED) {
            throw PlayerDraftApiV1Exception.conflict(
                    "PLAYER_DRAFT_SESSION_CANCELLED", null,
                    "취소된 드래프트 세션에는 선택을 제출할 수 없습니다.");
        }
        if (session.status() != PlayerDraftSessionStatus.ACTIVE) {
            throw PlayerDraftApiV1Exception.conflict(
                    "PLAYER_DRAFT_ALREADY_COMPLETE", null,
                    "이미 완료된 드래프트에는 선택을 제출할 수 없습니다.");
        }
    }

    private static DraftSelectionContext selectionContext(
            String blueTeamCode, Team blueTeam,
            String redTeamCode, Team redTeam,
            long seed
    ) {
        return RealDraftSelectionContextFactory.create(
                seed, blueTeamCode, blueTeam, redTeamCode, redTeam, 1, Set.of());
    }

    private static PlayerDraftApiV1Exception repositoryFailure(
            PlayerDraftSessionRepository.RepositoryFailure error
    ) {
        return switch (error.getMessage()) {
            case "PLAYER_DRAFT_SESSION_NOT_FOUND" -> PlayerDraftApiV1Exception.notFound();
            case "PLAYER_DRAFT_SESSION_EXPIRED" -> PlayerDraftApiV1Exception.gone();
            case "PLAYER_DRAFT_SESSION_CAPACITY_REACHED" ->
                    PlayerDraftApiV1Exception.conflict(
                            error.getMessage(), null,
                            "동시에 유지할 수 있는 드래프트 세션 수를 초과했습니다.");
            default -> PlayerDraftApiV1Exception.internal(error);
        };
    }

    public record SimulationExecution(
            PlayerDraftSessionView session, MatchEngineV1Output output
    ) {
    }
}
