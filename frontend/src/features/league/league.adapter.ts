import type { LeagueFixtureStatus, LeagueSeasonStatus } from './api/leagueApi.types';

export const LEAGUE_TEAMS = [
  { code: 'GEN', name: 'Gen.G' }, { code: 'T1', name: 'T1' }, { code: 'HLE', name: 'Hanwha Life Esports' },
  { code: 'DK', name: 'Dplus KIA' }, { code: 'KT', name: 'KT Rolster' }, { code: 'NS', name: 'Nongshim RedForce' },
  { code: 'BFX', name: 'BNK FEARX' }, { code: 'BRO', name: 'OKSavingsBank BRION' },
  { code: 'DNS', name: 'DN SOOPers' }, { code: 'KRX', name: 'DRX' },
] as const;
const NAMES = new Map<string, string>(LEAGUE_TEAMS.map((team) => [team.code, team.name]));
export function teamName(code: string): string { return NAMES.get(code) ?? code; }
export const SEASON_STATUS_COPY: Readonly<Record<LeagueSeasonStatus, string>> = { DRAFT: '생성 중', FROZEN: '스냅샷 고정', READY: '진행 가능', RUNNING: '라운드 진행 중', PAUSED: '일시 정지', WAITING_FOR_PLAYER: 'Player 경기 대기', COMPLETED: '시즌 완료', BLOCKED: '진행 차단', CANCELLED: '취소됨' };
export const FIXTURE_STATUS_COPY: Readonly<Record<LeagueFixtureStatus, string>> = { SCHEDULED: '예정', QUEUED: '대기열', LEASED: '작업 할당', RUNNING: '계산 중', AWAITING_PLAYER: 'Player 대기', PLAYER_SERIES_RESERVED: 'Series 준비', PLAYER_SERIES_ACTIVE: 'Series 진행', COMPLETION_PENDING_VERIFICATION: '결과 검증', RETRY_PENDING: '재시도 대기', PLAYER_SERIES_RESTART_REQUIRED: 'Series 재시작 필요', COMPLETED: '완료', BLOCKED: '차단', CANCELLED: '취소' };
export function formatSeriesRecord(wins: number, losses: number): string { return `${wins}승 ${losses}패`; }
