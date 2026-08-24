import { realMatchConfig } from '../realMatch.config';
import type {
  MatchRequestStage, RealMatchApiErrorDto, RealMatchOptionsDto, RealMatchResponseDto,
  RealMatchSimulateRequestDto,
} from './realMatchApi.types';
import {
  RealMatchContractError, validateRealMatchApiErrorPayload, validateRealMatchOptionsPayload,
  validateRealMatchResponsePayload,
} from './realMatchApi.validation';

export type RealMatchFailureKind = 'NETWORK' | 'CANCELLED' | 'TIMEOUT' | 'BACKEND' | 'INVALID_JSON' | 'CONTRACT';

export class RealMatchApiFailure extends Error {
  constructor(
    public readonly kind: RealMatchFailureKind,
    public readonly userMessage: string,
    public readonly status: number | null = null,
    public readonly code: string | null = null,
    public readonly field: string | null = null,
  ) {
    super(userMessage);
    this.name = 'RealMatchApiFailure';
  }
}

interface AbortContext {
  signal: AbortSignal;
  timedOut: () => boolean;
  cleanup: () => void;
}

function createAbortContext(externalSignal: AbortSignal, timeoutMs: number): AbortContext {
  const controller = new AbortController();
  let timeoutTriggered = false;
  const onExternalAbort = () => controller.abort(externalSignal.reason);
  if (externalSignal.aborted) onExternalAbort();
  else externalSignal.addEventListener('abort', onExternalAbort, { once: true });
  const timer = window.setTimeout(() => {
    timeoutTriggered = true;
    controller.abort(new DOMException('Real Match request timeout', 'TimeoutError'));
  }, timeoutMs);
  return {
    signal: controller.signal,
    timedOut: () => timeoutTriggered,
    cleanup: () => {
      window.clearTimeout(timer);
      externalSignal.removeEventListener('abort', onExternalAbort);
    },
  };
}

function parseJson(rawText: string): unknown {
  try { return JSON.parse(rawText) as unknown; }
  catch { throw new RealMatchApiFailure('INVALID_JSON', '서버 응답 JSON을 해석하지 못했습니다. 잠시 후 다시 시도하세요.'); }
}

function parseBackendError(rawText: string, status: number): RealMatchApiFailure {
  try {
    const error: RealMatchApiErrorDto = validateRealMatchApiErrorPayload(JSON.parse(rawText) as unknown);
    const fieldLabel = error.field === 'blueTeamCode' ? 'BLUE 팀'
      : error.field === 'redTeamCode' ? 'RED 팀'
        : error.field === 'seed' ? 'seed'
          : error.field === 'schemaVersion' ? '요청 버전'
            : error.field;
    const context = [fieldLabel ? `확인 항목: ${fieldLabel}` : null, `오류 코드: ${error.code}`].filter(Boolean).join(' · ');
    return new RealMatchApiFailure('BACKEND', `${error.message} (${context})`, status, error.code, error.field);
  } catch {
    return new RealMatchApiFailure('BACKEND', `서버가 경기 요청을 처리하지 못했습니다. (HTTP ${status})`, status);
  }
}

function normalizeFailure(error: unknown, context: AbortContext): RealMatchApiFailure {
  if (error instanceof RealMatchApiFailure) return error;
  if (error instanceof RealMatchContractError) {
    return new RealMatchApiFailure('CONTRACT', '서버 응답 계약이 현재 화면과 일치하지 않습니다. 서버와 프런트엔드 버전을 확인하세요.');
  }
  if (context.signal.aborted) {
    return context.timedOut()
      ? new RealMatchApiFailure('TIMEOUT', '경기 요청 시간이 초과되었습니다. 서버 상태를 확인한 뒤 다시 시도하세요.')
      : new RealMatchApiFailure('CANCELLED', '경기 요청을 취소했습니다.');
  }
  return new RealMatchApiFailure('NETWORK', '백엔드 서버에 연결하지 못했습니다. 서버 실행 상태와 API 주소를 확인하세요.');
}

export async function fetchLiveMatchOptions(signal: AbortSignal): Promise<RealMatchOptionsDto> {
  const context = createAbortContext(signal, realMatchConfig.optionsTimeoutMs);
  try {
    const response = await fetch(`${realMatchConfig.apiBaseUrl}/api/v1/real-matches/options`, {
      method: 'GET', headers: { Accept: 'application/json' }, signal: context.signal,
    });
    const rawText = await response.text();
    if (!response.ok) throw parseBackendError(rawText, response.status);
    return validateRealMatchOptionsPayload(parseJson(rawText));
  } catch (error) {
    throw normalizeFailure(error, context);
  } finally {
    context.cleanup();
  }
}

export interface LiveSimulationResult {
  response: RealMatchResponseDto;
  performance: {
    payloadBytes: number; requestAndDownloadMs: number; jsonParseMs: number;
    runtimeValidationMs: number; requestStartedAt: number;
  };
}

export async function simulateLiveMatch(
  request: RealMatchSimulateRequestDto,
  signal: AbortSignal,
  onStage: (stage: MatchRequestStage) => void,
): Promise<LiveSimulationResult> {
  const context = createAbortContext(signal, realMatchConfig.simulateTimeoutMs);
  const requestStartedAt = performance.now();
  try {
    onStage('CONNECTING');
    const response = await fetch(`${realMatchConfig.apiBaseUrl}/api/v1/real-matches/simulate`, {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
      signal: context.signal,
    });
    onStage('DOWNLOADING');
    const rawText = await response.text();
    const requestAndDownloadMs = performance.now() - requestStartedAt;
    if (!response.ok) throw parseBackendError(rawText, response.status);
    const payloadBytes = new Blob([rawText]).size;
    onStage('PARSING');
    const parseStartedAt = performance.now();
    const parsed = parseJson(rawText);
    const jsonParseMs = performance.now() - parseStartedAt;
    onStage('VALIDATING');
    const validationStartedAt = performance.now();
    const validated = validateRealMatchResponsePayload(parsed, request);
    const runtimeValidationMs = performance.now() - validationStartedAt;
    return {
      response: validated,
      performance: { payloadBytes, requestAndDownloadMs, jsonParseMs, runtimeValidationMs, requestStartedAt },
    };
  } catch (error) {
    throw normalizeFailure(error, context);
  } finally {
    context.cleanup();
  }
}
