import { realMatchConfig } from '../../real-match/realMatch.config';
import { CareerApiFailure } from './careerApi.failure';
export { CareerApiFailure } from './careerApi.failure';
import type { CareerAdvanceRequestDto, CareerAdvanceResponseDto, CareerCalendarViewDto, CareerCreateRequestDto, CareerCreateResponseDto, CareerListResponseDto, CareerViewDto } from './careerApi.types';
import { CareerContractError, validateCareerAdvanceResponse, validateCareerCalendar, validateCareerCreateResponse, validateCareerError, validateCareerListResponse, validateCareerView } from './careerApi.validation';

const ROOT = `${realMatchConfig.apiBaseUrl}/api/v1/careers`;
const REQUEST_TIMEOUT_MS = 30_000;
const SAFE_COPY: Readonly<Record<string, string>> = {
  CAREER_REQUEST_INVALID: '입력한 Career 정보를 다시 확인하세요.',
  CAREER_MANAGED_TEAM_NOT_FOUND: '현재 LCK 팀 목록에 없는 관리 팀입니다.',
  CAREER_COMMAND_CONFLICT: '이 생성 작업 ID가 다른 입력에 이미 사용되었습니다. 입력을 확인한 뒤 새로 시작하세요.',
  CAREER_CAPACITY_REACHED: 'Career 저장 슬롯 100개가 모두 사용 중입니다. V1에서는 삭제·보관을 지원하지 않습니다.',
  CAREER_NOT_FOUND: '저장된 Career를 찾을 수 없습니다.',
  CAREER_TEMPORARILY_UNAVAILABLE: 'Career 저장소를 일시적으로 사용할 수 없습니다. 같은 요청으로 다시 시도하세요.',
  CAREER_COMMAND_RECEIPT_INTEGRITY_FAILURE: '생성 기록의 무결성을 확인할 수 없습니다. 저장 ID는 유지하고 지원이 필요합니다.',
  CAREER_LINKED_SEASON_INTEGRITY_FAILURE: 'Career와 연결된 League Season 무결성을 확인할 수 없습니다. 저장 ID는 유지하고 지원이 필요합니다.',
  CAREER_RESOURCE_INTEGRITY_FAILURE: 'Career 복원에 필요한 reference resource 무결성을 확인할 수 없습니다.',
  CAREER_CALENDAR_NOT_FOUND: '저장된 Career의 캘린더 상태를 찾을 수 없습니다.',
  CAREER_CALENDAR_STALE_REVISION: '캘린더가 이미 변경되었습니다. 최신 상태를 다시 불러오세요.',
  CAREER_CALENDAR_COMMAND_CONFLICT: '이 날짜 진행 작업 ID가 다른 입력에 이미 사용되었습니다.',
  CAREER_CALENDAR_ADVANCE_ALREADY_PENDING: '완료되지 않은 날짜 진행이 있습니다. 기존 작업으로 다시 확인하세요.',
  CAREER_CALENDAR_COMMAND_INTEGRITY_FAILURE: '날짜 진행 기록의 무결성을 확인할 수 없습니다.',
  CAREER_CALENDAR_MIGRATION_REQUIRED: '이 저장은 캘린더 마이그레이션 확인이 필요합니다.',
  CAREER_CALENDAR_INTEGRITY_FAILURE: '캘린더 또는 연결 일정의 무결성을 확인할 수 없습니다.',
  CAREER_CALENDAR_BACKGROUND_UNAVAILABLE: '경기 작업은 저장됐지만 worker를 깨우지 못했습니다. 같은 진행 작업으로 다시 시도하세요.',
  CAREER_INTERNAL_ERROR: 'Career 요청을 처리하지 못했습니다. 잠시 뒤 다시 확인하세요.',
};

interface AbortContext { signal: AbortSignal; timedOut: () => boolean; cleanup: () => void }
function abortContext(external: AbortSignal): AbortContext {
  const controller = new AbortController(); let timeout = false;
  const onAbort = () => controller.abort(external.reason);
  if (external.aborted) onAbort(); else external.addEventListener('abort', onAbort, { once: true });
  const timer = window.setTimeout(() => { timeout = true; controller.abort(new DOMException('Career timeout', 'TimeoutError')); }, REQUEST_TIMEOUT_MS);
  return { signal: controller.signal, timedOut: () => timeout, cleanup: () => { window.clearTimeout(timer); external.removeEventListener('abort', onAbort); } };
}
function parse(raw: string): unknown {
  try { return JSON.parse(raw) as unknown; }
  catch { throw new CareerApiFailure('INVALID_JSON', '서버의 Career 응답을 해석하지 못했습니다.'); }
}
function backendFailure(raw: string, status: number): CareerApiFailure {
  try {
    const error = validateCareerError(parse(raw));
    return new CareerApiFailure('BACKEND', SAFE_COPY[error.code] ?? `Career 요청을 처리하지 못했습니다. (HTTP ${status})`, status, error.code, error.field, status === 503);
  } catch (cause) {
    if (cause instanceof CareerApiFailure && cause.kind === 'INVALID_JSON') return new CareerApiFailure('BACKEND', `서버가 예상하지 못한 오류 응답을 보냈습니다. (HTTP ${status})`, status);
    return new CareerApiFailure('BACKEND', `Career 요청을 처리하지 못했습니다. (HTTP ${status})`, status);
  }
}
function normalize(error: unknown, context: AbortContext): CareerApiFailure {
  if (error instanceof CareerApiFailure) return error;
  if (error instanceof CareerContractError) return new CareerApiFailure('CONTRACT', `서버의 Career API 계약이 화면과 일치하지 않습니다. (${error.path})`);
  if (context.signal.aborted) return context.timedOut()
    ? new CareerApiFailure('TIMEOUT', 'Career 요청 시간이 초과되었습니다. 같은 생성 작업 ID로 다시 확인하세요.', null, null, null, true)
    : new CareerApiFailure('CANCELLED', 'Career 응답 수신을 중단했습니다. 서버 작업은 완료되었을 수 있습니다.');
  return new CareerApiFailure('NETWORK', 'Career 백엔드에 연결하지 못했습니다. 서버 상태를 확인한 뒤 다시 시도하세요.', null, null, null, true);
}

async function request<T>(url: string, init: RequestInit, signal: AbortSignal, validate: (value: unknown) => T, statuses: readonly number[]): Promise<T> {
  const context = abortContext(signal);
  try {
    const response = await fetch(url, { ...init, headers: { Accept: 'application/json', ...(init.headers ?? {}) }, signal: context.signal });
    const raw = await response.text(); if (!response.ok) throw backendFailure(raw, response.status);
    if (!statuses.includes(response.status)) throw new CareerApiFailure('CONTRACT', `허용되지 않은 HTTP ${response.status} 응답입니다.`);
    return validate(parse(raw));
  } catch (error) { throw normalize(error, context); }
  finally { context.cleanup(); }
}

export function getCareers(signal: AbortSignal): Promise<CareerListResponseDto> {
  return request(ROOT, { method: 'GET' }, signal, validateCareerListResponse, [200]);
}
export function getCareer(careerId: string, signal: AbortSignal): Promise<CareerViewDto> {
  return request(`${ROOT}/${encodeURIComponent(careerId)}`, { method: 'GET' }, signal, validateCareerView, [200]);
}
export function createCareer(body: CareerCreateRequestDto, signal: AbortSignal): Promise<CareerCreateResponseDto> {
  return request(ROOT, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }, signal, validateCareerCreateResponse, [200, 201]);
}
export function getCareerCalendar(careerId: string, signal: AbortSignal): Promise<CareerCalendarViewDto> {
  return request(`${ROOT}/${encodeURIComponent(careerId)}/calendar`, { method: 'GET' }, signal, validateCareerCalendar, [200]);
}
export function advanceCareerCalendar(careerId: string, body: CareerAdvanceRequestDto, signal: AbortSignal): Promise<CareerAdvanceResponseDto> {
  return request(`${ROOT}/${encodeURIComponent(careerId)}/advance`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }, signal, validateCareerAdvanceResponse, [200, 202]);
}
