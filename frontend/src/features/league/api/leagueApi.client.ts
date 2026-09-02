import { realMatchConfig } from '../../real-match/realMatch.config';
import { LeagueApiFailure } from './leagueApi.failure';
export { LeagueApiFailure } from './leagueApi.failure';
import type {
  LeagueCompletionRequestDto, LeagueCompletionStatusResponseDto, LeagueCreateRequestDto,
  LeagueFixtureListDto, LeagueFixtureResponseDto, LeagueJobResponseDto,
  LeagueLifecycleRequestDto, LeaguePlayerSeriesRequestDto, LeaguePlayerSeriesResponseDto,
  LeagueRunRequestDto, LeagueRunResponseDto, LeagueSeasonResponseDto, LeagueStandingsDto,
} from './leagueApi.types';
import {
  LeagueContractError, validateLeagueApiError, validateLeagueCompletionStatusResponse,
  validateLeagueFixtureList, validateLeagueFixtureResponse, validateLeagueJobResponse,
  validateLeaguePlayerSeriesResponse, validateLeagueRunResponse, validateLeagueSeasonResponse,
  validateLeagueStandings,
} from './leagueApi.validation';

interface Scope { leagueId: string; seasonId: string }
interface FixtureScope extends Scope { fixtureId: string }
interface AbortContext { signal: AbortSignal; timedOut: () => boolean; cleanup: () => void }
const REQUEST_TIMEOUT_MS = 30_000;

const COPY: Readonly<Record<string, string>> = {
  LEAGUE_BACKGROUND_EXECUTION_UNAVAILABLE: '경기 작업은 저장됐지만 계산 worker를 깨우지 못했습니다. 같은 작업으로 다시 시도하세요.',
  LEAGUE_STALE_LIFECYCLE_REVISION: '시즌 상태가 먼저 변경되었습니다. 최신 상태를 다시 불러왔습니다.',
  LEAGUE_COMMAND_ID_PAYLOAD_CONFLICT: '같은 작업 ID가 다른 요청에 사용되었습니다. 자동 재시도를 중단했습니다.',
  LEAGUE_STABLE_KEY_CONFLICT: '이미 사용 중인 리그 또는 시즌 키입니다.',
  LEAGUE_SEASON_NOT_FOUND: '저장된 시즌을 찾을 수 없습니다.',
  LEAGUE_FIXTURE_NOT_FOUND: '요청한 경기 일정을 찾을 수 없습니다.',
  LEAGUE_PLAYER_SERIES_NOT_FOUND: '이 경기에 연결된 Player Series를 찾을 수 없습니다.',
  PLAYER_SERIES_NOT_COMPLETED: 'Player Series가 아직 끝나지 않았습니다.',
};

function abortContext(external: AbortSignal): AbortContext {
  const controller = new AbortController(); let timeout = false;
  const onAbort = () => controller.abort(external.reason);
  if (external.aborted) onAbort(); else external.addEventListener('abort', onAbort, { once: true });
  const timer = window.setTimeout(() => { timeout = true; controller.abort(new DOMException('League timeout', 'TimeoutError')); }, REQUEST_TIMEOUT_MS);
  return { signal: controller.signal, timedOut: () => timeout, cleanup: () => { window.clearTimeout(timer); external.removeEventListener('abort', onAbort); } };
}
function parse(raw: string): unknown { try { return JSON.parse(raw) as unknown; } catch { throw new LeagueApiFailure('INVALID_JSON', '서버의 League 응답을 해석하지 못했습니다.'); } }
function backendFailure(raw: string, status: number): LeagueApiFailure {
  try {
    const error = validateLeagueApiError(parse(raw));
    return new LeagueApiFailure('BACKEND', COPY[error.code] ?? error.message, status, error.code, error.field, error.retryable, error.currentLifecycleRevision, error.currentLifecycleStatus);
  } catch (cause) {
    if (cause instanceof LeagueApiFailure && cause.kind === 'INVALID_JSON') return new LeagueApiFailure('BACKEND', `서버가 예상하지 못한 오류 응답을 보냈습니다. (HTTP ${status})`, status);
    return new LeagueApiFailure('BACKEND', `League 요청을 처리하지 못했습니다. (HTTP ${status})`, status);
  }
}
function normalize(error: unknown, context: AbortContext): LeagueApiFailure {
  if (error instanceof LeagueApiFailure) return error;
  if (error instanceof LeagueContractError) return new LeagueApiFailure('CONTRACT', `서버의 League API 계약이 화면과 일치하지 않습니다. (${error.path})`);
  if (context.signal.aborted) return context.timedOut() ? new LeagueApiFailure('TIMEOUT', 'League 요청 시간이 초과되었습니다. 같은 작업 ID로 상태를 다시 확인하세요.') : new LeagueApiFailure('CANCELLED', 'League 응답 수신을 중단했습니다. 서버 작업은 완료되었을 수 있습니다.');
  return new LeagueApiFailure('NETWORK', 'League 백엔드에 연결하지 못했습니다. 서버 실행 상태를 확인하세요.');
}
function base(scope?: Scope): string { const root = `${realMatchConfig.apiBaseUrl}/api/v1/leagues`; return scope ? `${root}/${encodeURIComponent(scope.leagueId)}/seasons/${encodeURIComponent(scope.seasonId)}` : root; }
function fixtureBase(scope: FixtureScope): string { return `${base(scope)}/fixtures/${encodeURIComponent(scope.fixtureId)}`; }

async function request<T>(url: string, init: RequestInit, signal: AbortSignal, validate: (value: unknown) => T, statuses: readonly number[]): Promise<T> {
  const context = abortContext(signal);
  try {
    const response = await fetch(url, { ...init, headers: { Accept: 'application/json', ...(init.headers ?? {}) }, signal: context.signal });
    const raw = await response.text(); if (!response.ok) throw backendFailure(raw, response.status);
    if (!statuses.includes(response.status)) throw new LeagueApiFailure('CONTRACT', `허용되지 않은 HTTP ${response.status} 응답입니다.`);
    return validate(parse(raw));
  } catch (error) { throw normalize(error, context); } finally { context.cleanup(); }
}
function post<T>(url: string, body: object, signal: AbortSignal, validate: (value: unknown) => T, statuses: readonly number[]): Promise<T> { return request(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }, signal, validate, statuses); }

export function createLeague(requestBody: LeagueCreateRequestDto, signal: AbortSignal): Promise<LeagueSeasonResponseDto> { return post(base(), requestBody, signal, (value) => validateLeagueSeasonResponse(value), [200, 201]); }
export function getLeagueSeason(scope: Scope, signal: AbortSignal): Promise<LeagueSeasonResponseDto> { return request(base(scope), { method: 'GET' }, signal, (value) => validateLeagueSeasonResponse(value, scope), [200]); }
export function getLeagueFixtures(scope: Scope, signal: AbortSignal): Promise<LeagueFixtureListDto> { return request(`${base(scope)}/fixtures`, { method: 'GET' }, signal, (value) => validateLeagueFixtureList(value, scope), [200]); }
export function getLeagueFixture(scope: FixtureScope, signal: AbortSignal): Promise<LeagueFixtureResponseDto> { return request(fixtureBase(scope), { method: 'GET' }, signal, validateLeagueFixtureResponse, [200]); }
export function getLeagueStandings(scope: Scope, signal: AbortSignal): Promise<LeagueStandingsDto> { return request(`${base(scope)}/standings`, { method: 'GET' }, signal, (value) => validateLeagueStandings(value, scope), [200]); }
export function runLeagueRound(scope: Scope, body: LeagueRunRequestDto, signal: AbortSignal): Promise<LeagueRunResponseDto> { return post(`${base(scope)}/commands/run-current-round`, body, signal, (value) => validateLeagueRunResponse(value, scope), [202]); }
export function pauseLeague(scope: Scope, body: LeagueLifecycleRequestDto, signal: AbortSignal): Promise<LeagueSeasonResponseDto> { return post(`${base(scope)}/commands/pause`, body, signal, (value) => validateLeagueSeasonResponse(value, scope), [200]); }
export function resumeLeague(scope: Scope, body: LeagueLifecycleRequestDto, signal: AbortSignal): Promise<LeagueSeasonResponseDto> { return post(`${base(scope)}/commands/resume`, body, signal, (value) => validateLeagueSeasonResponse(value, scope), [200]); }
export async function cancelLeague(scope: Scope, body: LeagueLifecycleRequestDto, signal: AbortSignal): Promise<void> {
  const context = abortContext(signal); try { const response = await fetch(base(scope), { method: 'DELETE', headers: { Accept: 'application/json', 'Content-Type': 'application/json' }, body: JSON.stringify(body), signal: context.signal }); const raw = await response.text(); if (!response.ok) throw backendFailure(raw, response.status); if (response.status !== 204 || raw.length > 0) throw new LeagueApiFailure('CONTRACT', 'Season 취소는 204 empty body여야 합니다.'); } catch (error) { throw normalize(error, context); } finally { context.cleanup(); }
}
export function getLeagueJob(scope: Scope, jobId: string, signal: AbortSignal): Promise<LeagueJobResponseDto> { return request(`${base(scope)}/jobs/${encodeURIComponent(jobId)}`, { method: 'GET' }, signal, (value) => validateLeagueJobResponse(value), [200]); }
export function startLeaguePlayerSeries(scope: FixtureScope, body: LeaguePlayerSeriesRequestDto, signal: AbortSignal): Promise<LeaguePlayerSeriesResponseDto> { return post(`${fixtureBase(scope)}/player-series`, body, signal, (value) => validateLeaguePlayerSeriesResponse(value, scope), [200, 201]); }
export function getLeaguePlayerSeries(scope: FixtureScope, signal: AbortSignal): Promise<LeaguePlayerSeriesResponseDto> { return request(`${fixtureBase(scope)}/player-series`, { method: 'GET' }, signal, (value) => validateLeaguePlayerSeriesResponse(value, scope), [200]); }
export function completeLeaguePlayerSeries(scope: FixtureScope, body: LeagueCompletionRequestDto, signal: AbortSignal): Promise<LeagueCompletionStatusResponseDto> { return post(`${fixtureBase(scope)}/player-series/completion`, body, signal, (value) => validateLeagueCompletionStatusResponse(value, scope), [200, 202]); }
export function getLeagueCompletion(scope: FixtureScope, signal: AbortSignal): Promise<LeagueCompletionStatusResponseDto> { return request(`${fixtureBase(scope)}/completion-status`, { method: 'GET' }, signal, (value) => validateLeagueCompletionStatusResponse(value, scope), [200]); }
