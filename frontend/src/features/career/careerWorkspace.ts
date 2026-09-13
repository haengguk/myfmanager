import type { InboxLink } from './api/careerInbox';

export const careerPages = [
  ['home', '구단 홈', 'home'], ['inbox', '소식함 · 현재 업무', 'inbox'],
  ['roster', '선수단', 'users'], ['schedule', '일정 · 대회', 'calendar'],
  ['training', '훈련 · 성장', 'training'], ['scouting', '스카우팅', 'scout'],
  ['market', '이적 시장', 'transfer'], ['finance', '재정', 'finance'], ['records', '기록', 'league'],
] as const;
export type CareerPage = typeof careerPages[number][0];
export interface CareerLocation { page: CareerPage; year: number | null; focus: InboxLink | null; scheduleTab?: string; trainingTab?: string }
export const homeLocation = (): CareerLocation => ({ page: 'home', year: null, focus: null });
const key = (career: string) => `lolmanager.career.location.v1.${career}`;
export function pageForLink(link: InboxLink): CareerPage {
  return ({ MATCH: 'schedule', SEASONS: 'schedule', ROSTER: 'roster', TRAINING: 'training', LIFECYCLE: 'training',
    MARKET: 'market', TRADE: 'market', FINANCE: 'finance', RECORDS: 'records', AWARDS: 'records' } as Record<string, CareerPage>)[link.panel] ?? 'inbox';
}
export function readCareerLocation(storage: Pick<Storage, 'getItem'>, career: string): CareerLocation {
  try {
    const v = JSON.parse(storage.getItem(key(career)) ?? 'null');
    if (!v || !careerPages.some(([page]) => page === v.page)) return homeLocation();
    const year = Number.isInteger(v.year) && v.year >= 2026 ? v.year : null;
    const f = v.focus;
    const focus = f && typeof f.panel === 'string' && Number.isInteger(f.seasonYear)
      && ['playerId', 'sourceId', 'competition', 'seriesId'].every(k => f[k] === null || typeof f[k] === 'string')
      && Array.isArray(f.positions) && f.positions.every((p: unknown) => typeof p === 'string') ? f as InboxLink : null;
    return { page: v.page, year, focus, scheduleTab: ['calendar','seasons','overseas','cl'].includes(v.scheduleTab) ? v.scheduleTab : 'calendar', trainingTab: v.trainingTab === 'lifecycle' ? 'lifecycle' : 'training' };
  } catch { return homeLocation(); }
}
export function writeCareerLocation(storage: Pick<Storage, 'setItem'>, career: string, location: CareerLocation) {
  storage.setItem(key(career), JSON.stringify(location));
}
