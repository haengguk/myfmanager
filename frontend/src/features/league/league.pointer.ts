import type { LeagueApiFailure } from './api/leagueApi.failure';

export const LEAGUE_POINTER_KEY = 'lolmanager.league.pointer.v1';
const LEAGUE_ID = /^league_[0-9a-f]{64}$/; const SEASON_ID = /^season_[0-9a-f]{64}$/;
export type LeagueCommandKind = 'CREATE' | 'RUN_ROUND' | 'PAUSE' | 'RESUME' | 'CANCEL' | 'START_PLAYER_SERIES' | 'COMPLETE_PLAYER_SERIES';
export interface LeagueCommandRef { kind: LeagueCommandKind; scopeKey: string; clientCommandId: string; expectedRevision: number | null; bindingHash: string | null }
export interface LeaguePointer { schemaVersion: 'AI_LEAGUE_POINTER_V1'; leagueId: string; seasonId: string; command: LeagueCommandRef | null }
export interface PointerStorage { getItem(key: string): string | null; setItem(key: string, value: string): void; removeItem(key: string): void }

function validCommand(value: unknown): value is LeagueCommandRef {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false;
  const row = value as Record<string, unknown>;
  return ['CREATE', 'RUN_ROUND', 'PAUSE', 'RESUME', 'CANCEL', 'START_PLAYER_SERIES', 'COMPLETE_PLAYER_SERIES'].includes(String(row.kind))
    && typeof row.scopeKey === 'string' && row.scopeKey.length > 0
    && typeof row.clientCommandId === 'string' && /^[0-9A-Za-z][0-9A-Za-z._:-]{0,159}$/.test(row.clientCommandId)
    && (row.expectedRevision === null || (typeof row.expectedRevision === 'number' && Number.isSafeInteger(row.expectedRevision) && row.expectedRevision >= 0))
    && (row.bindingHash === null || (typeof row.bindingHash === 'string' && /^[0-9a-f]{64}$/.test(row.bindingHash)));
}
export function readLeaguePointer(storage: PointerStorage): LeaguePointer | null {
  const raw = storage.getItem(LEAGUE_POINTER_KEY); if (!raw) return null;
  try {
    const value = JSON.parse(raw) as Record<string, unknown>;
    if (value.schemaVersion !== 'AI_LEAGUE_POINTER_V1' || typeof value.leagueId !== 'string' || !LEAGUE_ID.test(value.leagueId) || typeof value.seasonId !== 'string' || !SEASON_ID.test(value.seasonId) || (value.command !== null && !validCommand(value.command))) throw new Error('invalid');
    return value as unknown as LeaguePointer;
  } catch { storage.removeItem(LEAGUE_POINTER_KEY); return null; }
}
export function writeLeaguePointer(storage: PointerStorage, pointer: LeaguePointer): void { storage.setItem(LEAGUE_POINTER_KEY, JSON.stringify(pointer)); }
export function clearLeaguePointer(storage: PointerStorage): void { storage.removeItem(LEAGUE_POINTER_KEY); }
export function updateLeagueCommand(storage: PointerStorage, pointer: LeaguePointer, command: LeagueCommandRef | null): LeaguePointer { const next = { ...pointer, command }; writeLeaguePointer(storage, next); return next; }
export function leaguePointerRecoveryAction(failure: LeagueApiFailure): 'CLEAR_NOT_FOUND' | 'KEEP_RETRYABLE' | 'KEEP_VERSION' | 'KEEP_OTHER' {
  if (failure.code === 'LEAGUE_SEASON_NOT_FOUND') return 'CLEAR_NOT_FOUND';
  if (['NETWORK', 'TIMEOUT', 'CANCELLED'].includes(failure.kind) || failure.retryable || failure.httpStatus === 503) return 'KEEP_RETRYABLE';
  if (failure.kind === 'CONTRACT' || failure.kind === 'INVALID_JSON') return 'KEEP_VERSION';
  return 'KEEP_OTHER';
}
