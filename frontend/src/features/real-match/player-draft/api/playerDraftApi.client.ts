import type { MatchRequestStage } from '../../api/realMatchApi.types';
import { realMatchConfig } from '../../realMatch.config';
import type {
  PlayerDraftActionRequestDto, PlayerDraftApiErrorDto, PlayerDraftSessionExpectation,
  PlayerDraftSessionResponseDto, PlayerDraftSimulationResponseDto, PlayerDraftStartRequestDto,
} from './playerDraftApi.types';
import {
  PlayerDraftContractError, validatePlayerDraftApiErrorPayload, validatePlayerDraftSessionPayload,
  validatePlayerDraftSimulationPayload,
} from './playerDraftApi.validation';

export type PlayerDraftFailureKind = 'NETWORK' | 'CANCELLED' | 'TIMEOUT' | 'BACKEND' | 'INVALID_JSON' | 'CONTRACT';

export class PlayerDraftApiFailure extends Error {
  constructor(
    public readonly kind: PlayerDraftFailureKind,
    public readonly userMessage: string,
    public readonly status: number | null = null,
    public readonly code: string | null = null,
    public readonly field: string | null = null,
  ) {
    super(userMessage); this.name = 'PlayerDraftApiFailure';
  }
}

interface AbortContext { signal: AbortSignal; timedOut: () => boolean; cleanup: () => void; }
function abortContext(externalSignal: AbortSignal, timeoutMs: number): AbortContext {
  const controller = new AbortController(); let timeoutTriggered = false;
  const onAbort = () => controller.abort(externalSignal.reason);
  if (externalSignal.aborted) onAbort(); else externalSignal.addEventListener('abort', onAbort, { once: true });
  const timer = window.setTimeout(() => { timeoutTriggered = true; controller.abort(new DOMException('Player Draft timeout', 'TimeoutError')); }, timeoutMs);
  return { signal: controller.signal, timedOut: () => timeoutTriggered, cleanup: () => { window.clearTimeout(timer); externalSignal.removeEventListener('abort', onAbort); } };
}

function parseJson(raw: string): unknown {
  try { return JSON.parse(raw) as unknown; }
  catch { throw new PlayerDraftApiFailure('INVALID_JSON', '서버 응답을 해석하지 못했습니다. 응답 형식을 확인한 뒤 다시 시도하세요.'); }
}

const ERROR_COPY: Readonly<Record<string, string>> = {
  PLAYER_DRAFT_SESSION_CAPACITY_REACHED: '현재 유지 중인 직접 밴픽이 많습니다. 잠시 후 다시 시도하세요.',
  STALE_DRAFT_REVISION: '다른 응답이 먼저 반영되어 최신 Draft 상태를 다시 불러옵니다.',
  CLIENT_ACTION_ID_PAYLOAD_CONFLICT: '같은 작업 ID에 다른 선택이 연결되었습니다. 자동 재시도하지 않았습니다.',
  ILLEGAL_DRAFT_SELECTION: '현재 턴에는 선택할 수 없는 챔피언입니다. Draft 상태를 확인하세요.',
  PLAYER_DRAFT_SESSION_NOT_FOUND: '직접 밴픽 세션을 찾을 수 없습니다. 설정에서 새로 시작하세요.',
  PLAYER_DRAFT_SESSION_EXPIRED: '직접 밴픽 세션이 만료되었습니다. 설정에서 새로 시작하세요.',
  PLAYER_DRAFT_SESSION_CANCELLED: '이미 취소된 직접 밴픽입니다.',
  PLAYER_DRAFT_NOT_COMPLETE: 'Draft 20턴을 완료한 뒤 경기를 실행할 수 있습니다.',
  PLAYER_DRAFT_SIMULATION_RECEIPT_MISMATCH: '경기 실행 결과의 무결성을 확인하지 못했습니다. 같은 세션으로 다시 시도하세요.',
  PLAYER_DRAFT_INTERNAL_ERROR: '서버가 직접 밴픽 요청을 처리하지 못했습니다. 같은 작업을 다시 시도할 수 있습니다.',
};

function backendFailure(raw: string, status: number): PlayerDraftApiFailure {
  try {
    const payload: PlayerDraftApiErrorDto = validatePlayerDraftApiErrorPayload(parseJson(raw));
    return new PlayerDraftApiFailure('BACKEND', ERROR_COPY[payload.code] ?? `직접 밴픽 요청을 처리하지 못했습니다. (HTTP ${status})`, status, payload.code, payload.field);
  } catch (error) {
    if (error instanceof PlayerDraftApiFailure && error.kind === 'INVALID_JSON') return new PlayerDraftApiFailure('BACKEND', `서버가 예상하지 못한 오류 응답을 보냈습니다. (HTTP ${status})`, status);
    return new PlayerDraftApiFailure('BACKEND', `서버가 직접 밴픽 요청을 처리하지 못했습니다. (HTTP ${status})`, status);
  }
}

function normalizeFailure(error: unknown, context: AbortContext, operation: 'session' | 'action' | 'simulation'): PlayerDraftApiFailure {
  if (error instanceof PlayerDraftApiFailure) return error;
  if (error instanceof PlayerDraftContractError) return new PlayerDraftApiFailure('CONTRACT', '서버의 직접 밴픽 응답 계약이 현재 화면과 일치하지 않습니다. 서버와 프런트엔드 버전을 확인하세요.');
  if (context.signal.aborted) return context.timedOut()
    ? new PlayerDraftApiFailure('TIMEOUT', operation === 'simulation' ? '경기 계산 또는 응답 다운로드 시간이 초과되었습니다. 같은 세션으로 다시 시도하세요.' : '직접 밴픽 요청 시간이 초과되었습니다. 최신 상태를 확인한 뒤 다시 시도하세요.')
    : new PlayerDraftApiFailure('CANCELLED', operation === 'action' ? '응답 수신을 중단했습니다. 서버 반영 여부는 최신 상태 확인으로 확인하세요.' : '요청을 취소했습니다.');
  return new PlayerDraftApiFailure('NETWORK', '백엔드 서버에 연결하지 못했습니다. 서버 실행 상태를 확인하세요.');
}

async function sessionRequest(
  url: string, init: RequestInit, expectation: PlayerDraftSessionExpectation,
  signal: AbortSignal, operation: 'session' | 'action', timeoutMs: number,
): Promise<PlayerDraftSessionResponseDto> {
  const context = abortContext(signal, timeoutMs);
  try {
    const response = await fetch(url, { ...init, headers: { Accept: 'application/json', ...(init.headers ?? {}) }, signal: context.signal });
    const raw = await response.text(); if (!response.ok) throw backendFailure(raw, response.status);
    return validatePlayerDraftSessionPayload(parseJson(raw), expectation);
  } catch (error) { throw normalizeFailure(error, context, operation); }
  finally { context.cleanup(); }
}

function endpoint(sessionId = ''): string {
  return `${realMatchConfig.apiBaseUrl}/api/v1/player-drafts/sessions${sessionId ? `/${encodeURIComponent(sessionId)}` : ''}`;
}

export function createPlayerDraftSession(request: PlayerDraftStartRequestDto, signal: AbortSignal): Promise<PlayerDraftSessionResponseDto> {
  return sessionRequest(endpoint(), {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request),
  }, { blueTeamCode: request.blueTeamCode, redTeamCode: request.redTeamCode, controlledSide: request.controlledSide, seed: request.seed }, signal, 'session', realMatchConfig.playerDraftSessionTimeoutMs);
}

export function refreshPlayerDraftSession(expectation: PlayerDraftSessionExpectation, signal: AbortSignal): Promise<PlayerDraftSessionResponseDto> {
  if (!expectation.sessionId) throw new Error('sessionId is required');
  return sessionRequest(endpoint(expectation.sessionId), { method: 'GET' }, expectation, signal, 'session', realMatchConfig.playerDraftSessionTimeoutMs);
}

export function submitPlayerDraftAction(expectation: PlayerDraftSessionExpectation, request: PlayerDraftActionRequestDto, signal: AbortSignal): Promise<PlayerDraftSessionResponseDto> {
  if (!expectation.sessionId) throw new Error('sessionId is required');
  return sessionRequest(`${endpoint(expectation.sessionId)}/actions`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request),
  }, expectation, signal, 'action', realMatchConfig.playerDraftActionTimeoutMs);
}

export interface PlayerDraftSimulationResult {
  response: PlayerDraftSimulationResponseDto;
  performance: { payloadBytes: number; requestAndDownloadMs: number; jsonParseMs: number; runtimeValidationMs: number; requestStartedAt: number; };
}

export async function simulatePlayerDraftMatch(
  expectation: PlayerDraftSessionExpectation, signal: AbortSignal, onStage: (stage: MatchRequestStage) => void,
): Promise<PlayerDraftSimulationResult> {
  if (!expectation.sessionId) throw new Error('sessionId is required');
  const context = abortContext(signal, realMatchConfig.simulateTimeoutMs); const requestStartedAt = performance.now();
  try {
    onStage('CONNECTING');
    const response = await fetch(`${endpoint(expectation.sessionId)}/simulate`, {
      method: 'POST', headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ schemaVersion: 'PLAYER_DRAFT_SIMULATE_REQUEST_V1' }), signal: context.signal,
    });
    onStage('DOWNLOADING'); const raw = await response.text(); const requestAndDownloadMs = performance.now() - requestStartedAt;
    if (!response.ok) throw backendFailure(raw, response.status);
    const payloadBytes = new Blob([raw]).size; onStage('PARSING'); const parseStarted = performance.now(); const parsed = parseJson(raw); const jsonParseMs = performance.now() - parseStarted;
    onStage('VALIDATING'); const validationStarted = performance.now(); const validated = validatePlayerDraftSimulationPayload(parsed, expectation); const runtimeValidationMs = performance.now() - validationStarted;
    return { response: validated, performance: { payloadBytes, requestAndDownloadMs, jsonParseMs, runtimeValidationMs, requestStartedAt } };
  } catch (error) { throw normalizeFailure(error, context, 'simulation'); }
  finally { context.cleanup(); }
}

export async function cancelPlayerDraftSession(sessionId: string, signal: AbortSignal): Promise<void> {
  const context = abortContext(signal, realMatchConfig.playerDraftSessionTimeoutMs);
  try {
    const response = await fetch(endpoint(sessionId), { method: 'DELETE', headers: { Accept: 'application/json' }, signal: context.signal });
    const raw = await response.text(); if (!response.ok) throw backendFailure(raw, response.status);
    if (response.status !== 204 || raw.length !== 0) throw new PlayerDraftApiFailure('CONTRACT', '직접 밴픽 취소 응답을 확인하지 못했습니다. 세션이 남아 있을 수 있습니다.');
  } catch (error) { throw normalizeFailure(error, context, 'session'); }
  finally { context.cleanup(); }
}
