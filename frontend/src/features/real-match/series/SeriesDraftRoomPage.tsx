import { useMemo, useRef } from 'react';
import { PlayerDraftRoomPage, type PlayerDraftRoomTransport } from '../player-draft/PlayerDraftRoomPage';
import type { PlayerDraftSessionResponseDto } from '../player-draft/api/playerDraftApi.types';
import type { PlayerDraftScreenState } from '../player-draft/playerDraft.types';
import { SeriesApiFailure, cancelSeriesDraft, getSeries, getSeriesDraft, replaySeriesGame, simulateSeriesGame, submitSeriesDraftAction } from './api/seriesApi.client';
import type { SeriesChildDraftEnvelopeDto, SeriesSimulationResult, SeriesViewDto } from './api/seriesApi.types';
import {
  isAmbiguousSeriesFailure, reconcileSeriesDraftCancel, seriesDraftCancelCommand, type SeriesDraftCancelCommand,
} from './seriesCommandReconciliation';
import { SeriesContextBar } from './SeriesContextBar';
import type { SeriesScreenState } from './series.types';

function playerState(state: SeriesScreenState): PlayerDraftScreenState {
  if (!state.draft) throw new Error('Series child Draft가 필요합니다.');
  const binding = state.draft.binding;
  const seriesType = `${state.series.format} · ${state.series.winsRequired}선승`;
  return {
    session: state.draft.session,
    options: {
      ...state.options,
      gameNumber: binding.gameNumber,
      seriesType,
    },
    selection: {
      blueTeamId: binding.blueTeamCode, redTeamId: binding.redTeamCode,
      seed: binding.matchSeed, gameNumber: binding.gameNumber,
      seriesType,
      draftMode: 'PLAYER_CONTROLLED', controlledSide: binding.controlledSide,
    },
    championsById: state.championsById,
  };
}

export function SeriesDraftRoomPage({ state, onStateChange, onSimulation, onHub, onOpenGame }: {
  state: SeriesScreenState;
  onStateChange: (series: SeriesViewDto, draft: SeriesChildDraftEnvelopeDto | null, session?: PlayerDraftSessionResponseDto) => void;
  onSimulation: (result: SeriesSimulationResult) => void;
  onHub: () => void;
  onOpenGame: (gameNumber: number) => void;
}) {
  if (!state.draft) throw new Error('Series Draft 화면에는 active child가 필요합니다.');
  const simulateCommandRef = useRef<{ seriesRevision: number; draftRevision: number; id: string } | null>(null);
  const cancelCommandRef = useRef<SeriesDraftCancelCommand | null>(null);
  const viewState = playerState(state);
  const gameNumber = state.draft.binding.gameNumber;
  const transport = useMemo<PlayerDraftRoomTransport<SeriesSimulationResult>>(() => ({
    refresh: async (_screen, signal) => {
      const response = await getSeriesDraft(state.series.seriesId, gameNumber, signal);
      onStateChange(response.series, response.draftSession);
      return response.draftSession.session;
    },
    submit: async (_screen, session, action, signal) => {
      const response = await submitSeriesDraftAction(state.series.seriesId, gameNumber, {
        schemaVersion: 'SERIES_DRAFT_ACTION_REQUEST_V1',
        expectedSeriesRevision: state.series.revision,
        expectedDraftRevision: session.revision,
        clientCommandId: action.clientActionId,
        championId: action.championId,
      }, signal);
      onStateChange(response.series, response.draftSession);
      return response.draftSession.session;
    },
    simulate: async (_screen, session, signal, onStage) => {
      const logical = simulateCommandRef.current
        && simulateCommandRef.current.seriesRevision === state.series.revision
        && simulateCommandRef.current.draftRevision === session.revision
        ? simulateCommandRef.current
        : { seriesRevision: state.series.revision, draftRevision: session.revision, id: crypto.randomUUID() };
      simulateCommandRef.current = logical;
      try {
        const result = await simulateSeriesGame(state.series.seriesId, gameNumber, {
          schemaVersion: 'SERIES_SIMULATE_REQUEST_V1', expectedSeriesRevision: logical.seriesRevision,
          expectedDraftRevision: logical.draftRevision, clientCommandId: logical.id,
        }, signal, onStage);
        if (result.status === 200) simulateCommandRef.current = null;
        onStateChange(result.response.series, result.response.series.activeDraftSession, result.draftSession?.session);
        return { session: result.draftSession?.session ?? result.response.series.activeDraftSession?.session ?? session, result };
      } catch (error) {
        if (!(error instanceof SeriesApiFailure) || !isAmbiguousSeriesFailure(error.kind)) {
          simulateCommandRef.current = null; throw error;
        }
        const series = await getSeries(state.series.seriesId, signal);
        const game = series.games.find((candidate) => candidate.gameNumber === gameNumber);
        if (!game) { simulateCommandRef.current = null; throw new SeriesApiFailure('CONTRACT', '경기 실행 조정 중 대상 Game을 찾을 수 없습니다.'); }
        if (game.status === 'COMMITTED') {
          simulateCommandRef.current = null;
          const replay = await replaySeriesGame(series.seriesId, gameNumber, {
            schemaVersion: 'SERIES_GAME_REPLAY_REQUEST_V1', clientCommandId: crypto.randomUUID(),
          }, signal);
          return {
            session: replay.draftSession.session,
            result: {
              response: {
                schemaVersion: 'SERIES_SIMULATION_RESPONSE_V1', replayedCommand: true,
                series: replay.response.series, game: replay.response.game, match: replay.response.match,
              },
              status: 200,
              draftSession: replay.draftSession,
              performance: replay.performance,
            },
          };
        }
        if (game.status === 'SIMULATION_IN_PROGRESS' && series.reservation?.commandId === logical.id) {
          onStateChange(series, series.activeDraftSession);
          return {
            session,
            result: {
              response: {
                schemaVersion: 'SERIES_SIMULATION_RESPONSE_V1', replayedCommand: true,
                series, game, match: null,
              },
              status: 202,
              draftSession: series.activeDraftSession,
              performance: {
                payloadBytes: 0, requestAndDownloadMs: 0, jsonParseMs: 0,
                runtimeValidationMs: 0, requestStartedAt: performance.now(),
              },
            },
          };
        }
        if (series.revision === logical.seriesRevision
          && game.childDraftRevision === logical.draftRevision
          && series.allowedCommands.includes('SIMULATE')) {
          onStateChange(series, series.activeDraftSession);
          throw new SeriesApiFailure('NETWORK', '경기 실행 응답이 유실되었을 수 있으나 서버에는 실행 전 상태입니다. 같은 실행 작업 ID를 유지했습니다. 다시 실행하면 중복 경기 없이 재전송합니다.');
        }
        simulateCommandRef.current = null;
        onStateChange(series, series.activeDraftSession);
        throw new SeriesApiFailure('BACKEND', '경기 실행 확인 중 서버 상태가 다른 방향으로 진행되었습니다. 최신 상태를 반영했으며 새 실행 명령을 자동 생성하지 않았습니다.');
      }
    },
    cancel: async (_screen, session, signal) => {
      const logical = seriesDraftCancelCommand(cancelCommandRef.current, {
        seriesId: state.series.seriesId, gameNumber, expectedRevision: state.series.revision,
        draftSessionId: session.sessionId, draftRevision: session.revision,
      }, () => crypto.randomUUID());
      cancelCommandRef.current = logical;
      try {
        await cancelSeriesDraft(logical.seriesId, logical.gameNumber, {
          schemaVersion: 'SERIES_DRAFT_CANCEL_REQUEST_V1', expectedRevision: logical.expectedRevision,
          clientCommandId: logical.clientCommandId,
        }, signal);
      } catch (error) {
        if (!(error instanceof SeriesApiFailure) || !isAmbiguousSeriesFailure(error.kind)) {
          cancelCommandRef.current = null; throw error;
        }
      }
      const series = await getSeries(logical.seriesId, signal);
      onStateChange(series, series.activeDraftSession);
      const reconciliation = reconcileSeriesDraftCancel(series, session, logical);
      if (reconciliation === 'SUCCEEDED') { cancelCommandRef.current = null; return; }
      if (reconciliation === 'RETRY_SAME_COMMAND') {
        throw new SeriesApiFailure('NETWORK', 'Draft 취소 응답이 유실되었을 수 있으나 서버에는 아직 취소 전 상태입니다. 같은 취소 작업 ID를 유지했습니다. 다시 확인하면 중복 작업 없이 재전송합니다.');
      }
      cancelCommandRef.current = null;
      throw new SeriesApiFailure('BACKEND', 'Draft 취소 확인 중 시리즈가 다른 상태로 진행되었습니다. 최신 서버 상태를 반영했으며 새 취소 명령을 자동으로 만들지 않았습니다.');
    },
    failure: (error) => error instanceof SeriesApiFailure
      ? { userMessage: error.userMessage, code: error.code, kind: error.kind } : null,
  }), [gameNumber, onStateChange, state.series.revision, state.series.seriesId]);

  const commands = new Set(state.series.allowedCommands);
  const disabledReason = commands.size === 1 && commands.has('GET')
    ? '명령 기록 한도 또는 terminal 상태로 새 작업을 실행할 수 없습니다. 서버 상태는 조회할 수 있습니다.'
    : !commands.has('SUBMIT_DRAFT_ACTION') && state.draft.session.status === 'ACTIVE'
      ? '서버가 현재 Draft action을 허용하지 않습니다. 최신 상태를 확인하세요.'
      : !commands.has('SIMULATE') && state.draft.session.status === 'COMPLETED'
        ? '서버가 현재 경기 실행을 허용하지 않습니다. 최신 상태를 확인하세요.' : null;

  return <PlayerDraftRoomPage state={viewState} transport={transport}
    contextBar={<SeriesContextBar series={state.series} catalog={state.championsById} onOpenGame={onOpenGame} />}
    utilityMeta={`${state.series.format} · Game ${gameNumber} · Series-owned Draft`}
    backLabel="시리즈 허브로 돌아가기" simulateLabel={`Game ${gameNumber} Production V9 실행`}
    canSubmit={commands.has('SUBMIT_DRAFT_ACTION')} canSimulate={commands.has('SIMULATE')}
    canCancelDraft={commands.has('CANCEL_DRAFT_SESSION')} disabledReason={disabledReason}
    onSessionChange={(session) => {
      const draft = { ...state.draft!, session };
      onStateChange(state.series, draft, session);
    }}
    onSimulationComplete={onSimulation}
    onCancelled={onHub} onReviewBack={onHub} />;
}
