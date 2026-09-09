import { realMatchConfig } from '../../real-match/realMatch.config';
export interface InboxLink { panel: string; playerId: string | null; sourceId: string | null; competition: string | null; seasonYear: number; seriesId: string | null; positions: string[]; teamId?: string | null; current?: boolean; matchState?: string | null }
export interface InboxDecision { id: string; revision: string; type: string; status: string; responsibility: string; blocksProgress: boolean; date: string; deadline: string | null; title: string; summary: string; link: InboxLink }
export interface InboxEntry { sequence: number; read: boolean; currentStatus: string; item: { sourceKey: string; kind: string; date: string; title: string; summary: string; team: string | null; playerId: string | null; competition: string | null; development: boolean; link: InboxLink; facts: Record<string, unknown> } }
export interface InboxFeed { careerId: string; seasonYear: number | null; kind: string; includeDevelopment: boolean; asOf: number; nextCursor: number; unread: number; items: InboxEntry[]; decisions: InboxDecision[]; collectionNote: string }
export function acceptInbox(value: InboxFeed, career: string, year: number | null, kind: string, development: boolean) {
  if (!value || value.careerId !== career || value.seasonYear !== year || value.kind !== kind || value.includeDevelopment !== development || !Number.isSafeInteger(value.asOf) || !Array.isArray(value.items) || !Array.isArray(value.decisions) || value.items.some(e => e.sequence > value.asOf)) throw new Error('소식 응답의 Career 또는 조회 범위가 다릅니다.');
  return value;
}
export function currentDecision(old: InboxDecision, latest: InboxFeed) { return latest.decisions.find(d => d.id === old.id && d.revision === old.revision && d.status === 'OPEN') ?? null; }
export async function inboxRequest<T>(career: string, path: string, signal: AbortSignal, body?: unknown): Promise<T> {
  const r = await fetch(`${realMatchConfig.apiBaseUrl}/api/v1/careers/${encodeURIComponent(career)}/inbox${path}`, { signal, method: body === undefined ? 'GET' : 'POST', headers: { Accept: 'application/json', 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
  if (!r.ok) throw new Error(`소식 요청을 확인하지 못했습니다 (${r.status}).`); return r.json() as Promise<T>;
}
export const inboxKinds: Record<string, string> = { STOVE: '스토브 개시', PROMISE: '출전 약속', RETIREMENT: '은퇴 결정', ROOKIE_SUPPLY: '신인 공급', SERIES_RESULT: '경기 결과 · 경기상', OPERATIONS: '구단 운영', CONTRACT_RESPONSE: '계약 역제안', TRADE: '이적 · 임대', AWARD: '개인상', PLACEMENT: '대회 순위', TITLE: '우승', FINANCE: '재정', MONTHLY_GROWTH: '월별 성장', SEASON_TARGET: '시즌 목표', SEASON_RECAP: '시즌 결산', TRAINING: '훈련 적용' };
