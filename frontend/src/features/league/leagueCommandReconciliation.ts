import type { LeagueCommandKind, LeagueCommandRef } from './league.pointer';
import type { LeagueApiFailure } from './api/leagueApi.failure';
import type { LeagueSeasonViewDto } from './api/leagueApi.types';

export function logicalLeagueCommand(current: LeagueCommandRef | null, input: Omit<LeagueCommandRef, 'clientCommandId'>, createId: () => string = () => crypto.randomUUID()): LeagueCommandRef {
  return current && current.kind === input.kind && current.scopeKey === input.scopeKey && current.expectedRevision === input.expectedRevision && current.bindingHash === input.bindingHash ? current : { ...input, clientCommandId: createId() };
}
export function isAmbiguousLeagueFailure(failure: LeagueApiFailure): boolean { return ['NETWORK', 'TIMEOUT', 'CANCELLED'].includes(failure.kind) || failure.retryable || failure.httpStatus === 503; }
export function seasonCommandApplied(kind: LeagueCommandKind, season: LeagueSeasonViewDto, expectedRevision: number): boolean {
  if (season.lifecycleRevision <= expectedRevision) return false;
  if (kind === 'PAUSE') return season.lifecycleStatus === 'PAUSED';
  if (kind === 'RESUME') return ['READY', 'RUNNING', 'WAITING_FOR_PLAYER'].includes(season.lifecycleStatus);
  if (kind === 'CANCEL') return season.lifecycleStatus === 'CANCELLED';
  return false;
}
export function shouldApplyLeagueSeason(current: LeagueSeasonViewDto | null, next: LeagueSeasonViewDto): boolean {
  if (!current) return true;
  if (current.leagueId !== next.leagueId || current.seasonId !== next.seasonId) return false;
  return next.lifecycleRevision > current.lifecycleRevision || (next.lifecycleRevision === current.lifecycleRevision && next.standingsRevision >= current.standingsRevision);
}
