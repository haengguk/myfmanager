import type { MatchRequestStage } from '../api/realMatchApi.types';
import type { PlayerDraftSessionResponseDto } from '../player-draft/api/playerDraftApi.types';
import { SeriesApiFailure } from './api/seriesApi.failure.ts';
import type {
  SeriesReplayRequestDto,
  SeriesReplayResult,
  SeriesSimulateRequestDto,
  SeriesSimulationResult,
  SeriesViewDto,
} from './api/seriesApi.types';
import { isAmbiguousSeriesFailure } from './seriesCommandReconciliation.ts';

export interface SeriesSimulationLogicalCommand {
  readonly seriesId: string;
  readonly gameNumber: number;
  readonly expectedSeriesRevision: number;
  readonly expectedDraftRevision: number;
  readonly simulationCommandId: string;
  readonly phase: 'SIMULATE_OR_RECONCILE' | 'REPLAY_COMMITTED';
  readonly replayCommandId: string | null;
}

interface CurrentRef<T> { current: T; }

export interface SeriesSimulationRecoveryOperations {
  simulate(
    seriesId: string,
    gameNumber: number,
    request: SeriesSimulateRequestDto,
    signal: AbortSignal,
    onStage: (stage: MatchRequestStage) => void,
  ): Promise<SeriesSimulationResult>;
  getSeries(seriesId: string, signal: AbortSignal): Promise<SeriesViewDto>;
  replay(
    seriesId: string,
    gameNumber: number,
    request: SeriesReplayRequestDto,
    signal: AbortSignal,
  ): Promise<SeriesReplayResult>;
}

export interface SeriesSimulationRecoveryInput {
  readonly series: Pick<SeriesViewDto, 'seriesId' | 'revision'>;
  readonly gameNumber: number;
  readonly session: PlayerDraftSessionResponseDto;
  readonly signal: AbortSignal;
  readonly onStage: (stage: MatchRequestStage) => void;
  readonly commandRef: CurrentRef<SeriesSimulationLogicalCommand | null>;
  readonly commandId: () => string;
  readonly operations: SeriesSimulationRecoveryOperations;
  readonly onStateChange: (
    series: SeriesViewDto,
    draft: SeriesViewDto['activeDraftSession'],
    session?: PlayerDraftSessionResponseDto,
  ) => void;
}

export interface SeriesSimulationTransportResult {
  readonly session: PlayerDraftSessionResponseDto;
  readonly result: SeriesSimulationResult;
}

function sameTarget(
  command: SeriesSimulationLogicalCommand,
  input: SeriesSimulationRecoveryInput,
): boolean {
  if (command.seriesId !== input.series.seriesId || command.gameNumber !== input.gameNumber) return false;
  if (command.phase === 'REPLAY_COMMITTED') return true;
  return command.expectedSeriesRevision === input.series.revision
    && command.expectedDraftRevision === input.session.revision;
}

function logicalCommand(input: SeriesSimulationRecoveryInput): SeriesSimulationLogicalCommand {
  const current = input.commandRef.current;
  if (current && sameTarget(current, input)) return current;
  return {
    seriesId: input.series.seriesId,
    gameNumber: input.gameNumber,
    expectedSeriesRevision: input.series.revision,
    expectedDraftRevision: input.session.revision,
    simulationCommandId: input.commandId(),
    phase: 'SIMULATE_OR_RECONCILE',
    replayCommandId: null,
  };
}

async function replayCommitted(
  input: SeriesSimulationRecoveryInput,
  command: SeriesSimulationLogicalCommand,
): Promise<SeriesSimulationTransportResult> {
  if (command.phase !== 'REPLAY_COMMITTED' || command.replayCommandId === null) {
    throw new Error('Committed Series replay identity is unavailable.');
  }
  const replay = await input.operations.replay(command.seriesId, command.gameNumber, {
    schemaVersion: 'SERIES_GAME_REPLAY_REQUEST_V1', clientCommandId: command.replayCommandId,
  }, input.signal);
  input.commandRef.current = null;
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

/**
 * Production Series simulate/reconcile/replay state machine.
 * Once GET observes COMMITTED, retries remain replay-only until a replay succeeds.
 */
export async function executeSeriesSimulationWithRecovery(
  input: SeriesSimulationRecoveryInput,
): Promise<SeriesSimulationTransportResult> {
  const command = logicalCommand(input);
  input.commandRef.current = command;
  if (command.phase === 'REPLAY_COMMITTED') return replayCommitted(input, command);

  try {
    const result = await input.operations.simulate(command.seriesId, command.gameNumber, {
      schemaVersion: 'SERIES_SIMULATE_REQUEST_V1',
      expectedSeriesRevision: command.expectedSeriesRevision,
      expectedDraftRevision: command.expectedDraftRevision,
      clientCommandId: command.simulationCommandId,
    }, input.signal, input.onStage);
    if (result.status === 200) input.commandRef.current = null;
    input.onStateChange(result.response.series, result.response.series.activeDraftSession,
      result.draftSession?.session);
    return {
      session: result.draftSession?.session
        ?? result.response.series.activeDraftSession?.session
        ?? input.session,
      result,
    };
  } catch (error) {
    if (!(error instanceof SeriesApiFailure) || !isAmbiguousSeriesFailure(error.kind)) {
      input.commandRef.current = null;
      throw error;
    }
  }

  const series = await input.operations.getSeries(command.seriesId, input.signal);
  const game = series.games.find((candidate) => candidate.gameNumber === command.gameNumber);
  if (!game) {
    input.commandRef.current = null;
    throw new SeriesApiFailure('CONTRACT', '경기 실행 조정 중 대상 Game을 찾을 수 없습니다.');
  }
  if (game.status === 'COMMITTED') {
    const replayCommand: SeriesSimulationLogicalCommand = {
      ...command,
      phase: 'REPLAY_COMMITTED',
      replayCommandId: command.replayCommandId ?? input.commandId(),
    };
    input.commandRef.current = replayCommand;
    return replayCommitted(input, replayCommand);
  }
  if (game.status === 'SIMULATION_IN_PROGRESS'
    && series.reservation?.commandId === command.simulationCommandId) {
    input.onStateChange(series, series.activeDraftSession);
    return {
      session: input.session,
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
  if (series.revision === command.expectedSeriesRevision
    && game.childDraftRevision === command.expectedDraftRevision
    && series.allowedCommands.includes('SIMULATE')) {
    input.onStateChange(series, series.activeDraftSession);
    throw new SeriesApiFailure('NETWORK', '경기 실행 응답이 유실되었을 수 있으나 서버에는 실행 전 상태입니다. 같은 실행 작업 ID를 유지했습니다. 다시 실행하면 중복 경기 없이 재전송합니다.');
  }
  input.commandRef.current = null;
  input.onStateChange(series, series.activeDraftSession);
  throw new SeriesApiFailure('BACKEND', '경기 실행 확인 중 서버 상태가 다른 방향으로 진행되었습니다. 최신 상태를 반영했으며 새 실행 명령을 자동 생성하지 않았습니다.');
}
