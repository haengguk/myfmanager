import type { ClFixture } from './careerCl.contract';

export const OVERSEAS_EVENTS = {
  LPL: [['LPL_SPLIT_1', 'Split 1'], ['LPL_SPLIT_2', 'Split 2'], ['LPL_SPLIT_3', 'Split 3'], ['LPL_REGIONAL_FINALS', 'Regional Finals']],
  LEC: [['LEC_VERSUS', 'Versus'], ['LEC_SPRING', 'Spring'], ['LEC_SUMMER', 'Summer']],
  LCP: [['LCP_SPLIT_1', 'Split 1'], ['LCP_SPLIT_2', 'Split 2'], ['LCP_SPLIT_3', 'Split 3']],
  CBLOL: [['CBLOL_COPA', 'Copa'], ['CBLOL_ETAPA_1', 'Etapa 1'], ['CBLOL_ETAPA_2', 'Etapa 2']],
  LCS: [['LCS_LOCK_IN', 'Lock-In'], ['LCS_SPRING', 'Spring'], ['LCS_SUMMER', 'Summer'], ['AMERICAS_CUP', 'Americas Cup']],
} as const;
export type OverseasLeague = keyof typeof OVERSEAS_EVENTS;
export const OVERSEAS_EVENT_IDS = Object.values(OVERSEAS_EVENTS).flat().map(e => e[0]);
export interface OverseasEvent {
  eventId: string; name: string; league: OverseasLeague; status: string; waitingReason: string | null;
  result: null | { complete: boolean; groupRanks: Record<string, string[]>; regularRanking: string[]; playoffTeams: string[]; ranking: string[]; placements: Record<string, number>; points: Record<string, number>; seasonEliminated: string[] };
  fixtures: ClFixture[]; scores: Record<string, { first: number; second: number }>;
  standings: Record<string, { team: string; wins: number; losses: number; gameWins: number; gameLosses: number }>;
  championshipPoints: Record<string, number>;
  qualifications: Record<string, { team: string; region: string; regionalSeed: number; qualification: string }[]>;
}
export interface CareerOverseas {
  careerId: string; seasonYear: number; league: OverseasLeague; active: boolean; readOnly: boolean;
  activation: null | { activationYear: number; introducedYear: number; extensionApplied: boolean };
  events: OverseasEvent[]; scheduleExplanation: string;
}
const object = (v: unknown): Record<string, unknown> => { if (!v || typeof v !== 'object' || Array.isArray(v)) throw new Error('해외 대회 응답 형식 오류'); return v as Record<string, unknown>; };
function check(v: unknown): asserts v { if (!v) throw new Error('해외 대회 데이터 범위 오류'); }
const integer = (v: unknown) => Number.isSafeInteger(v) && Number(v) >= 0;
const ids = (v: unknown): v is string[] => Array.isArray(v) && v.every(t => typeof t === 'string') && new Set(v).size === v.length;
const points = (v: unknown) => Object.values(object(v)).every(integer);
export function validateOverseas(raw: unknown): CareerOverseas {
  const v = object(raw);
  check(typeof v.careerId === 'string' && integer(v.seasonYear) && typeof v.league === 'string' && v.league in OVERSEAS_EVENTS);
  check(typeof v.active === 'boolean' && typeof v.readOnly === 'boolean' && typeof v.scheduleExplanation === 'string');
  if (v.activation !== null) { const a = object(v.activation); check(integer(a.activationYear) && integer(a.introducedYear) && Number(a.activationYear) >= Number(a.introducedYear) && typeof a.extensionApplied === 'boolean'); check(v.active === (Number(v.seasonYear) >= Number(a.activationYear))); }
  else check(!v.active);
  check(Array.isArray(v.events) && v.events.length <= 4 && (v.active || v.events.length === 0));
  const events = new Set<string>();
  for (const rawEvent of v.events) {
    const e = object(rawEvent);
    check(typeof e.eventId === 'string' && !events.has(e.eventId) && OVERSEAS_EVENTS[v.league as OverseasLeague].some(row => row[0] === e.eventId) && e.league === v.league);
    events.add(e.eventId);check(typeof e.name === 'string' && typeof e.status === 'string' && (e.waitingReason === null || typeof e.waitingReason === 'string'));
    check(Array.isArray(e.fixtures) && e.fixtures.length <= 200);const fixtures = new Map<string, Record<string, unknown>>();
    for (const rawFixture of e.fixtures) {
      const f = object(rawFixture);
      check(f.competitionId === e.eventId && typeof f.matchId === 'string' && !fixtures.has(f.matchId) && typeof f.seriesId === 'string' && typeof f.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(f.date));
      check(typeof f.firstTeamCode === 'string' && typeof f.secondTeamCode === 'string' && f.firstTeamCode !== f.secondTeamCode && f.executionMode === 'FULL_AUTO' && ['BO1', 'BO3', 'BO5'].includes(String(f.seriesFormat)) && typeof f.stageId === 'string' && typeof f.lifecycleStatus === 'string');
      fixtures.set(f.matchId, f);
    }
    for (const [id, rawScore] of Object.entries(object(e.scores))) { const score = object(rawScore), fixture = fixtures.get(id); check(fixture?.lifecycleStatus === 'COMPLETED' && integer(score.first) && integer(score.second)); const needed = (Number(String(fixture.seriesFormat).slice(2)) + 1) / 2;check(Math.max(Number(score.first), Number(score.second)) === needed && Math.min(Number(score.first), Number(score.second)) < needed); }
    for (const [team, rawStanding] of Object.entries(object(e.standings))) { const r = object(rawStanding);check(r.team === team && [r.wins, r.losses, r.gameWins, r.gameLosses].every(integer)); }
    check(points(e.championshipPoints));
    if (e.result !== null) { const r = object(e.result);check(typeof r.complete === 'boolean' && ids(r.regularRanking) && ids(r.playoffTeams) && ids(r.ranking) && ids(r.seasonEliminated) && points(r.points) && points(r.placements));check(!r.complete || r.ranking.length > 0);for (const group of Object.values(object(r.groupRanks))) check(ids(group)); }
    for (const entries of Object.values(object(e.qualifications))) { check(Array.isArray(entries) && entries.length <= 4);const seeds = new Set<number>();for (const rawEntry of entries) { const q = object(rawEntry);check(typeof q.team === 'string' && q.region === v.league && integer(q.regionalSeed) && Number(q.regionalSeed) > 0 && !seeds.has(Number(q.regionalSeed)) && typeof q.qualification === 'string');seeds.add(Number(q.regionalSeed)); } }
  }
  return v as unknown as CareerOverseas;
}
