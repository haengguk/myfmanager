import { realMatchConfig } from '../../real-match/realMatch.config';
import { TeamPlayerApiFailure } from './teamPlayerApi.failure';
export { TeamPlayerApiFailure } from './teamPlayerApi.failure';
import type { CatalogMetadataDto, PlayerResponseDto, PlayerSummaryDto, TeamPlayerWorkspaceDto } from './teamPlayerApi.types';
import { TeamPlayerContractError, validateErrorResponse, validateMetadata, validatePlayerResponse, validatePlayers, validateTeams, validateWorkspace } from './teamPlayerApi.validation';

const REQUEST_TIMEOUT_MS = 30_000;
const ROOT = `${realMatchConfig.apiBaseUrl}/api/v1/reference/leagues/LCK`;

interface AbortContext { signal: AbortSignal; timedOut: () => boolean; cleanup: () => void }

function abortContext(external: AbortSignal): AbortContext {
  const controller = new AbortController();
  let timeout = false;
  const onAbort = () => controller.abort(external.reason);
  if (external.aborted) onAbort(); else external.addEventListener('abort', onAbort, { once: true });
  const timer = window.setTimeout(() => {
    timeout = true;
    controller.abort(new DOMException('Team/player request timeout', 'TimeoutError'));
  }, REQUEST_TIMEOUT_MS);
  return {
    signal: controller.signal,
    timedOut: () => timeout,
    cleanup: () => { window.clearTimeout(timer); external.removeEventListener('abort', onAbort); },
  };
}

function parse(raw: string): unknown {
  try { return JSON.parse(raw) as unknown; }
  catch { throw new TeamPlayerApiFailure('INVALID_JSON', '서버의 선수 정보 응답을 해석하지 못했습니다.', null, null, null, true); }
}

const SAFE_BACKEND_COPY: Readonly<Record<string, string>> = {
  REFERENCE_QUERY_INVALID: '지원하지 않는 선수 정보 조회 조건입니다.',
  REFERENCE_LEAGUE_NOT_FOUND: '요청한 리그 정보를 찾을 수 없습니다.',
  REFERENCE_TEAM_NOT_FOUND: '요청한 팀 정보를 찾을 수 없습니다.',
  REFERENCE_PLAYER_NOT_FOUND: '요청한 선수 정보를 찾을 수 없습니다.',
  PLAYER_INFORMATION_RESOURCE_INTEGRITY_FAILURE: '선수 정보 원본 무결성을 확인하지 못했습니다.',
};

function backendFailure(raw: string, status: number): TeamPlayerApiFailure {
  try {
    const error = validateErrorResponse(parse(raw));
    return new TeamPlayerApiFailure(
      'BACKEND', SAFE_BACKEND_COPY[error.code] ?? `선수 정보 요청을 처리하지 못했습니다. (HTTP ${status})`,
      status, error.code, error.field, status >= 500 || status === 404,
    );
  } catch (cause) {
    if (cause instanceof TeamPlayerApiFailure && cause.kind === 'INVALID_JSON') {
      return new TeamPlayerApiFailure('BACKEND', `서버가 예상하지 못한 오류 응답을 보냈습니다. (HTTP ${status})`, status, null, null, status >= 500);
    }
    return new TeamPlayerApiFailure('BACKEND', `선수 정보 요청을 처리하지 못했습니다. (HTTP ${status})`, status, null, null, status >= 500);
  }
}

function normalize(error: unknown, context: AbortContext): TeamPlayerApiFailure {
  if (error instanceof TeamPlayerApiFailure) return error;
  if (error instanceof TeamPlayerContractError) {
    return new TeamPlayerApiFailure('CONTRACT', `서버의 선수 정보 계약이 화면과 일치하지 않습니다. (${error.path})`);
  }
  if (context.signal.aborted) {
    return context.timedOut()
      ? new TeamPlayerApiFailure('TIMEOUT', '선수 정보 요청 시간이 초과되었습니다. 서버 상태를 확인한 뒤 다시 시도하세요.', null, null, null, true)
      : new TeamPlayerApiFailure('CANCELLED', '선수 정보 응답 수신을 중단했습니다.');
  }
  return new TeamPlayerApiFailure('NETWORK', '선수 정보 백엔드에 연결하지 못했습니다. 서버 실행 상태를 확인하세요.', null, null, null, true);
}

async function getUnknown(path: string, signal: AbortSignal): Promise<unknown> {
  const context = abortContext(signal);
  try {
    const response = await fetch(`${ROOT}${path}`, { method: 'GET', headers: { Accept: 'application/json' }, signal: context.signal });
    const raw = await response.text();
    if (!response.ok) throw backendFailure(raw, response.status);
    if (response.status !== 200) throw new TeamPlayerApiFailure('CONTRACT', `허용되지 않은 HTTP ${response.status} 응답입니다.`);
    return parse(raw);
  } catch (error) {
    throw normalize(error, context);
  } finally {
    context.cleanup();
  }
}

export async function fetchTeamPlayerWorkspace(signal: AbortSignal): Promise<TeamPlayerWorkspaceDto> {
  const [metadata, teams, players] = await Promise.all([
    getUnknown('', signal).then(validateMetadata),
    getUnknown('/teams', signal).then(validateTeams),
    getUnknown('/players', signal).then(validatePlayers),
  ]);
  return validateWorkspace(metadata, teams, players);
}

export async function fetchPlayerDetail(
  player: PlayerSummaryDto,
  catalog: CatalogMetadataDto,
  signal: AbortSignal,
): Promise<PlayerResponseDto> {
  const value = await getUnknown(`/players/${encodeURIComponent(player.playerId)}`, signal);
  return validatePlayerResponse(value, player, catalog);
}
