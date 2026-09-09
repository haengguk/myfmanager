import { realMatchConfig } from '../../real-match/realMatch.config';
export interface Entity { id: string; name: string; team: string | null; status: string }
export interface Definition { id: string; scope: string; name: string; category: string; status: string; period: string; eligibility: string; tiers: number[] }
export interface RecordDirectory { careerId: string; seasons: number[]; players: Entity[]; teams: Entity[]; definitions: Definition[]; revision: number }
export interface Rating { status: string; combat: number | null; economy: number | null; survival: number | null; rating: number | null; kpStatus: string }
export interface PlayerGame { playerId: string; name: string; team: string; employer: string | null; evaluation: Rating; statistics: { championId: string; position: string; kills: number; deaths: number; assists: number; cs: number; gold: number; experience: number } | null; won: boolean }
export interface Game { gameNumber: number; seconds: number; winner: string; coverage: string; players: PlayerGame[] }
export interface RecordSeries { recordId: string; careerId: string; seasonYear: number; competition: string; stage: string; seriesId: string; date: string; firstTeam: string; secondTeam: string; winner: string; coverage: string; games: Game[] }
export interface Candidate { playerId: string; name: string; team: string; position: string; sets: number; participation: number; eligibility: string; eligibilityReason: string; score: number; combat: number; period: { adjustedMean: number; consistency: number; teamPerformance: number } | null }
export interface Award { instanceId: string; definitionId: string; name: string; scope: string; occurrence: string; gameYear: number; status: string; reason: string; aliases: string[]; analysisBadge: boolean; cutoffDate: string; publishedOn?: string | null; publicationStatus?: string; inputRecords: string[]; candidates: Candidate[]; slots: { tier: number; position: string | null; playerId: string | null; reason: string }[]; entitlements: { playerId: string; currency: string; amount: number; krw: number | null; status: string; minimumDelayDays: number | null }[]; tiePolicy: string }
export type RecordSelection = { kind: 'PLAYER' | 'TEAM' | 'ALL'; entity: string; year: number | null; competition: string; organization?: boolean };
export interface RecordsView { careerId: string; kind: string; entity: string; seasonYear: number | null; asOf: number; nextCursor: number; nextAwardCursor: number; awardCounts: Record<string, number>; organization: boolean; competition: string; organizationTotals: Record<string, number>; historicalRoster: { playerid: string; name: string; team: string; series: number; sets: number }[]; awardsCoverage: string; historyCoverage: string; factsCoverage: string; statisticsCoverage: string; totals: Record<string, string | number | null>[]; champions: Record<string, string | number | null>[]; matches: RecordSeries[]; awards: Award[]; observations: { seasonYear: number; kind: string; value: Record<string, unknown> }[]; collectionNote: string; observationAsOf: number; nextObservationCursor: number; growthSeason: number; growthObservations: RecordsView['observations'] }
export function recordsSelectionKey(career: string) { return `career-records:${career}`; }
export function restoreRecordSelection(raw: string | null, year: number): RecordSelection {
  try { const v = JSON.parse(raw ?? 'null'); if (v && ['PLAYER', 'TEAM', 'ALL'].includes(v.kind) && typeof v.entity === 'string' && (v.year === null || Number.isInteger(v.year)) && typeof v.competition === 'string' && (v.organization === undefined || typeof v.organization === 'boolean')) return v; } catch { /* A bad optional filter cannot block a save. */ }
  return { kind: 'ALL', entity: '', year, competition: '' };
}
export function acceptRecordView(value: unknown, career: string, selection: RecordSelection): RecordsView {
  const v = value as RecordsView;
  if (!v || v.careerId !== career || v.kind !== selection.kind || v.entity !== selection.entity || v.seasonYear !== selection.year || v.organization !== !!selection.organization || v.competition !== selection.competition || !Number.isInteger(v.asOf) || !Array.isArray(v.matches) || !Array.isArray(v.awards) || v.matches.some(m => m.careerId !== career)) throw new Error('기록 응답의 Career 또는 조회 범위가 다릅니다.');
  return v;
}
export async function recordRequest<T>(career: string, path: string, signal: AbortSignal, method = 'GET'): Promise<T> {
  const response = await fetch(`${realMatchConfig.apiBaseUrl}/api/v1/careers/${encodeURIComponent(career)}/records${path}`, { signal, method, headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error(`기록을 불러오지 못했습니다 (${response.status}).`);
  return response.json() as Promise<T>;
}
