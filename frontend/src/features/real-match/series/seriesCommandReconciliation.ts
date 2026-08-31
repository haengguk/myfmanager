import type { PlayerDraftSessionResponseDto } from '../player-draft/api/playerDraftApi.types';
import type { SeriesViewDto } from './api/seriesApi.types';

export const AMBIGUOUS_SERIES_FAILURE_KINDS = ['NETWORK', 'TIMEOUT', 'CANCELLED'] as const;

export interface SeriesCancelCommand {
  readonly seriesId: string;
  readonly expectedRevision: number;
  readonly clientCommandId: string;
}

export interface SeriesDraftCancelCommand extends SeriesCancelCommand {
  readonly gameNumber: number;
  readonly draftSessionId: string;
  readonly draftRevision: number;
}

export type CancelReconciliation = 'SUCCEEDED' | 'RETRY_SAME_COMMAND' | 'STATE_MOVED';

export function tryBeginSeriesCommand(pending: { current: boolean }): boolean {
  if (pending.current) return false;
  pending.current = true; return true;
}

export function finishSeriesCommand(pending: { current: boolean }): void { pending.current = false; }

export function isAmbiguousSeriesFailure(kind: string): boolean {
  return AMBIGUOUS_SERIES_FAILURE_KINDS.includes(kind as (typeof AMBIGUOUS_SERIES_FAILURE_KINDS)[number]);
}

export function seriesCancelCommand(
  current: SeriesCancelCommand | null,
  target: Omit<SeriesCancelCommand, 'clientCommandId'>,
  commandId: () => string,
): SeriesCancelCommand {
  if (current?.seriesId === target.seriesId && current.expectedRevision === target.expectedRevision) return current;
  return { ...target, clientCommandId: commandId() };
}

export function seriesDraftCancelCommand(
  current: SeriesDraftCancelCommand | null,
  target: Omit<SeriesDraftCancelCommand, 'clientCommandId'>,
  commandId: () => string,
): SeriesDraftCancelCommand {
  if (current?.seriesId === target.seriesId
    && current.gameNumber === target.gameNumber
    && current.expectedRevision === target.expectedRevision
    && current.draftSessionId === target.draftSessionId
    && current.draftRevision === target.draftRevision) return current;
  return { ...target, clientCommandId: commandId() };
}

export function reconcileSeriesCancel(series: SeriesViewDto, command: SeriesCancelCommand): CancelReconciliation {
  if (series.seriesId !== command.seriesId) return 'STATE_MOVED';
  if (series.status === 'CANCELLED') return 'SUCCEEDED';
  if (series.status === 'ACTIVE'
    && series.revision === command.expectedRevision
    && series.allowedCommands.includes('CANCEL_SERIES')) return 'RETRY_SAME_COMMAND';
  return 'STATE_MOVED';
}

export function reconcileSeriesDraftCancel(
  series: SeriesViewDto,
  session: Pick<PlayerDraftSessionResponseDto, 'sessionId' | 'revision'>,
  command: SeriesDraftCancelCommand,
): CancelReconciliation {
  if (series.seriesId !== command.seriesId) return 'STATE_MOVED';
  const game = series.games.find((candidate) => candidate.gameNumber === command.gameNumber);
  if (series.status === 'CANCELLED' || game?.status === 'DRAFT_CANCELLED') return 'SUCCEEDED';
  if (series.status === 'ACTIVE'
    && series.revision === command.expectedRevision
    && game?.childDraftSessionId === command.draftSessionId
    && game.childDraftRevision === command.draftRevision
    && session.sessionId === command.draftSessionId
    && session.revision === command.draftRevision
    && series.activeDraftSession?.session.sessionId === command.draftSessionId
    && series.activeDraftSession.session.revision === command.draftRevision
    && series.allowedCommands.includes('CANCEL_DRAFT_SESSION')) return 'RETRY_SAME_COMMAND';
  return 'STATE_MOVED';
}
