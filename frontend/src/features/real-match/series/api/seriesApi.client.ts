import type { MatchRequestStage } from '../../api/realMatchApi.types';
import { realMatchConfig } from '../../realMatch.config';
import type {
  SeriesApiErrorDto,
  SeriesCancelRequestDto,
  SeriesCreateRequestDto,
  SeriesDraftActionRequestDto,
  SeriesDraftCancelRequestDto,
  SeriesDraftCreateRequestDto,
  SeriesDraftResponseDto,
  SeriesGameViewDto,
  SeriesReplayRequestDto,
  SeriesReplayResult,
  SeriesRequestPerformance,
  SeriesSimulateRequestDto,
  SeriesSimulationResult,
  SeriesStatus,
  SeriesViewDto,
} from './seriesApi.types';
import {
  SeriesContractError,
  validateSeriesApiErrorPayload,
  validateSeriesDraftResponsePayload,
  validateSeriesMatchPayload,
  validateSeriesReplayEnvelopePayload,
  validateSeriesSimulationEnvelopePayload,
  validateSeriesViewPayload,
} from './seriesApi.validation';

export type SeriesFailureKind = 'NETWORK' | 'CANCELLED' | 'TIMEOUT' | 'BACKEND' | 'INVALID_JSON' | 'CONTRACT';

export class SeriesApiFailure extends Error {
  constructor(
    public readonly kind: SeriesFailureKind,
    public readonly userMessage: string,
    public readonly status: number | null = null,
    public readonly code: string | null = null,
    public readonly field: string | null = null,
    public readonly retryable = false,
    public readonly currentRevision: number | null = null,
    public readonly currentStatus: SeriesStatus | null = null,
  ) {
    super(userMessage); this.name = 'SeriesApiFailure';
  }
}

interface AbortContext { signal: AbortSignal; timedOut: () => boolean; cleanup: () => void; }

function abortContext(external: AbortSignal, timeoutMs: number): AbortContext {
  const controller = new AbortController(); let timeoutTriggered = false;
  const onAbort = () => controller.abort(external.reason);
  if (external.aborted) onAbort(); else external.addEventListener('abort', onAbort, { once: true });
  const timer = window.setTimeout(() => { timeoutTriggered = true; controller.abort(new DOMException('Series request timeout', 'TimeoutError')); }, timeoutMs);
  return { signal: controller.signal, timedOut: () => timeoutTriggered, cleanup: () => { window.clearTimeout(timer); external.removeEventListener('abort', onAbort); } };
}

function parseJson(raw: string): unknown {
  try { return JSON.parse(raw) as unknown; }
  catch { throw new SeriesApiFailure('INVALID_JSON', '서버의 시리즈 응답을 해석하지 못했습니다.'); }
}

const ERROR_COPY: Readonly<Record<string, string>> = {
  SERIES_UNSUPPORTED_FORMAT: '시리즈 형식은 BO3 또는 BO5여야 합니다.',
  SERIES_UNKNOWN_TEAM: 'LIVE Options에 없는 팀입니다. 팀 목록을 새로고침하세요.',
  SERIES_SAME_TEAM_NOT_ALLOWED: '내 팀과 상대 팀은 서로 달라야 합니다.',
  SERIES_INVALID_MANAGED_TEAM: '내 팀이 참가 팀에 포함되지 않았습니다.',
  SERIES_INVALID_ROOT_SEED: 'root seed는 signed 64-bit 정수의 표준 문자열이어야 합니다.',
  SERIES_NOT_FOUND: '시리즈를 찾을 수 없습니다. 백엔드 재시작으로 진행 기록이 사라졌을 수 있습니다.',
  SERIES_EXPIRED: '시리즈가 만료되었습니다. 새 시리즈를 시작하세요.',
  SERIES_STALE_REVISION: '다른 응답이 먼저 반영되었습니다. 서버의 최신 시리즈 상태를 다시 확인합니다.',
  SERIES_STALE_DRAFT_REVISION: 'Draft가 이미 다음 상태로 진행되었습니다. 최신 상태를 다시 확인합니다.',
  SERIES_ILLEGAL_DRAFT_SELECTION: '현재 턴에는 선택할 수 없는 챔피언입니다.',
  SERIES_UNKNOWN_CHAMPION: '현재 챔피언 카탈로그에 없는 챔피언입니다.',
  SERIES_COMMAND_ID_PAYLOAD_CONFLICT: '같은 작업 ID가 다른 요청에 사용되었습니다. 자동 재시도하지 않았습니다.',
  SERIES_SIMULATION_ALREADY_IN_PROGRESS: '경기 계산이 이미 진행 중입니다. 현재 상태를 확인하세요.',
  SERIES_SIMULATION_FAILED: '경기 계산에 실패했습니다. 서버가 재시도를 허용하면 새 실행으로 다시 시도할 수 있습니다.',
  SERIES_GAME_NO_DECISIVE_RESULT: '승자를 확정할 수 없어 시리즈가 차단되었습니다.',
  SERIES_HARD_FEARLESS_POOL_EXHAUSTED: '누적 피어리스 규칙으로 다음 Draft를 완성할 수 없습니다.',
  SERIES_COMMAND_RECEIPT_CAPACITY_REACHED: '이 시리즈의 명령 기록 한도에 도달해 새 작업을 실행할 수 없습니다.',
  SERIES_GAME_REPLAY_IDENTITY_UNAVAILABLE: '현재 서버 리소스로 이 경기의 전체 재생을 복원할 수 없습니다.',
  SERIES_GAME_RECEIPT_MISMATCH: '저장된 경기 영수증과 재생 결과가 일치하지 않습니다.',
  SERIES_ALREADY_COMPLETED: '이미 완료된 시리즈입니다.',
  SERIES_CANCELLED: '이미 취소된 시리즈입니다.',
  SERIES_BLOCKED: '서버 무결성 규칙에 의해 시리즈가 차단되었습니다.',
  SERIES_CAPACITY_REACHED: '현재 유지 중인 시리즈가 많습니다. 잠시 후 다시 시도하세요.',
};

function backendFailure(raw: string, status: number): SeriesApiFailure {
  try {
    const payload: SeriesApiErrorDto = validateSeriesApiErrorPayload(parseJson(raw));
    return new SeriesApiFailure(
      'BACKEND', ERROR_COPY[payload.code] ?? `시리즈 요청을 처리하지 못했습니다. (HTTP ${status})`,
      status, payload.code, payload.field, payload.retryable, payload.currentRevision, payload.currentStatus,
    );
  } catch (error) {
    if (error instanceof SeriesApiFailure && error.kind === 'INVALID_JSON') return new SeriesApiFailure('BACKEND', `서버가 예상하지 못한 오류 응답을 보냈습니다. (HTTP ${status})`, status);
    return new SeriesApiFailure('BACKEND', `서버가 시리즈 요청을 처리하지 못했습니다. (HTTP ${status})`, status);
  }
}

function normalizeFailure(error: unknown, context: AbortContext, operation: 'READ' | 'COMMAND' | 'SIMULATION'): SeriesApiFailure {
  if (error instanceof SeriesApiFailure) return error;
  if (error instanceof SeriesContractError) return new SeriesApiFailure('CONTRACT', '서버의 Series API 계약이 현재 화면과 일치하지 않습니다. 서버와 프런트엔드 버전을 확인하세요.');
  if (context.signal.aborted) return context.timedOut()
    ? new SeriesApiFailure('TIMEOUT', operation === 'SIMULATION'
      ? '경기 계산 또는 응답 다운로드 시간이 초과되었습니다. 같은 작업 ID의 상태를 다시 확인하세요.'
      : '시리즈 요청 시간이 초과되었습니다. 서버의 최신 상태를 확인하세요.')
    : new SeriesApiFailure('CANCELLED', '시리즈 응답 수신을 중단했습니다. 서버 작업은 완료되었을 수 있습니다.');
  return new SeriesApiFailure('NETWORK', '백엔드 서버에 연결하지 못했습니다. 서버 실행 상태를 확인하세요.');
}

function endpoint(seriesId = ''): string {
  return `${realMatchConfig.apiBaseUrl}/api/v1/series${seriesId ? `/${encodeURIComponent(seriesId)}` : ''}`;
}
function gameEndpoint(seriesId: string, gameNumber: number): string { return `${endpoint(seriesId)}/games/${gameNumber}`; }

async function jsonRequest<T>(
  url: string,
  init: RequestInit,
  signal: AbortSignal,
  timeoutMs: number,
  operation: 'READ' | 'COMMAND' | 'SIMULATION',
  validate: (value: unknown) => T,
): Promise<{ value: T; status: number; performance: SeriesRequestPerformance }> {
  const context = abortContext(signal, timeoutMs); const requestStartedAt = performance.now();
  try {
    const response = await fetch(url, { ...init, headers: { Accept: 'application/json', ...(init.headers ?? {}) }, signal: context.signal });
    const raw = await response.text(); const requestAndDownloadMs = performance.now() - requestStartedAt;
    if (!response.ok) throw backendFailure(raw, response.status);
    const payloadBytes = new Blob([raw]).size; const parseStartedAt = performance.now(); const parsed = parseJson(raw); const jsonParseMs = performance.now() - parseStartedAt;
    const validationStartedAt = performance.now(); const value = validate(parsed); const runtimeValidationMs = performance.now() - validationStartedAt;
    return { value, status: response.status, performance: { payloadBytes, requestAndDownloadMs, jsonParseMs, runtimeValidationMs, requestStartedAt } };
  } catch (error) { throw normalizeFailure(error, context, operation); }
  finally { context.cleanup(); }
}

async function emptyRequest(url: string, body: object, signal: AbortSignal): Promise<void> {
  const context = abortContext(signal, realMatchConfig.playerDraftSessionTimeoutMs);
  try {
    const response = await fetch(url, {
      method: 'DELETE', headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify(body), signal: context.signal,
    });
    const raw = await response.text(); if (!response.ok) throw backendFailure(raw, response.status);
    if (response.status !== 204 || raw.length !== 0) throw new SeriesApiFailure('CONTRACT', '취소 응답이 204 empty body 계약과 일치하지 않습니다.');
  } catch (error) { throw normalizeFailure(error, context, 'COMMAND'); }
  finally { context.cleanup(); }
}

export async function createSeries(request: SeriesCreateRequestDto, signal: AbortSignal): Promise<SeriesViewDto> {
  const result = await jsonRequest(endpoint(), {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request),
  }, signal, realMatchConfig.playerDraftSessionTimeoutMs, 'COMMAND', validateSeriesViewPayload);
  if (result.status !== 200 && result.status !== 201) throw new SeriesApiFailure('CONTRACT', 'Series create 응답 status가 200/201이 아닙니다.');
  return result.value;
}

export async function getSeries(seriesId: string, signal: AbortSignal): Promise<SeriesViewDto> {
  return (await jsonRequest(endpoint(seriesId), { method: 'GET' }, signal, realMatchConfig.playerDraftSessionTimeoutMs, 'READ', validateSeriesViewPayload)).value;
}

export async function createSeriesDraft(seriesId: string, request: SeriesDraftCreateRequestDto, signal: AbortSignal): Promise<SeriesDraftResponseDto> {
  return (await jsonRequest(`${endpoint(seriesId)}/games/current/draft-session`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request),
  }, signal, realMatchConfig.playerDraftActionTimeoutMs, 'COMMAND', validateSeriesDraftResponsePayload)).value;
}

export async function getSeriesDraft(seriesId: string, gameNumber: number, signal: AbortSignal): Promise<SeriesDraftResponseDto> {
  return (await jsonRequest(`${gameEndpoint(seriesId, gameNumber)}/draft-session`, { method: 'GET' }, signal, realMatchConfig.playerDraftSessionTimeoutMs, 'READ', validateSeriesDraftResponsePayload)).value;
}

export async function submitSeriesDraftAction(seriesId: string, gameNumber: number, request: SeriesDraftActionRequestDto, signal: AbortSignal): Promise<SeriesDraftResponseDto> {
  return (await jsonRequest(`${gameEndpoint(seriesId, gameNumber)}/draft-session/actions`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request),
  }, signal, realMatchConfig.playerDraftActionTimeoutMs, 'COMMAND', validateSeriesDraftResponsePayload)).value;
}

export function cancelSeriesDraft(seriesId: string, gameNumber: number, request: SeriesDraftCancelRequestDto, signal: AbortSignal): Promise<void> {
  return emptyRequest(`${gameEndpoint(seriesId, gameNumber)}/draft-session`, request, signal);
}

export async function simulateSeriesGame(
  seriesId: string,
  gameNumber: number,
  request: SeriesSimulateRequestDto,
  signal: AbortSignal,
  onStage: (stage: MatchRequestStage) => void,
): Promise<SeriesSimulationResult> {
  onStage('CONNECTING');
  const result = await jsonRequest(`${gameEndpoint(seriesId, gameNumber)}/simulate`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request),
  }, signal, realMatchConfig.simulateTimeoutMs, 'SIMULATION', (value) => {
    onStage('VALIDATING'); return validateSeriesSimulationEnvelopePayload(value);
  });
  if (result.status !== 200 && result.status !== 202) throw new SeriesApiFailure('CONTRACT', 'Series simulate 응답 status가 200/202가 아닙니다.');
  let match = null; let draftSession = result.value.series.activeDraftSession;
  if (result.value.match !== null) {
    onStage('NORMALIZING');
    const child = await getSeriesDraft(seriesId, result.value.game.gameNumber, signal);
    draftSession = child.draftSession;
    match = validateSeriesMatchPayload(result.value.match, result.value.series, result.value.game, child.draftSession);
  }
  return {
    response: { ...result.value, match }, status: result.status as 200 | 202,
    draftSession, performance: result.performance,
  };
}

export async function getSeriesGame(seriesId: string, gameNumber: number, signal: AbortSignal): Promise<SeriesGameViewDto> {
  const series = await getSeries(seriesId, signal);
  const value = await jsonRequest(gameEndpoint(seriesId, gameNumber), { method: 'GET' }, signal, realMatchConfig.playerDraftSessionTimeoutMs, 'READ', (payload) => {
    if (typeof payload !== 'object' || payload === null || Array.isArray(payload)) throw new SeriesContractError('$', 'game 객체가 필요합니다.');
    const expected = series.games.find((game) => game.gameNumber === gameNumber);
    if (!expected || JSON.stringify(payload) !== JSON.stringify(expected)) throw new SeriesContractError('$', 'Series view의 game projection과 일치하지 않습니다.');
    return expected;
  });
  return value.value;
}

export async function replaySeriesGame(seriesId: string, gameNumber: number, request: SeriesReplayRequestDto, signal: AbortSignal): Promise<SeriesReplayResult> {
  const result = await jsonRequest(`${gameEndpoint(seriesId, gameNumber)}/replay`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request),
  }, signal, realMatchConfig.simulateTimeoutMs, 'SIMULATION', validateSeriesReplayEnvelopePayload);
  const child = await getSeriesDraft(seriesId, gameNumber, signal);
  const match = validateSeriesMatchPayload(result.value.match, result.value.series, result.value.game, child.draftSession);
  return { response: { ...result.value, match }, draftSession: child.draftSession, performance: result.performance };
}

export function cancelSeries(seriesId: string, request: SeriesCancelRequestDto, signal: AbortSignal): Promise<void> {
  return emptyRequest(endpoint(seriesId), request, signal);
}
