import type { MatchSessionPerformance, MatchSetupOptionsViewModel, MatchSetupSelection } from '../matchSession.types';
import type { PlayerDraftChampionCatalogEntry } from '../player-draft/playerDraft.types';
import { createPlayerDraftMatchSessionFromPayload } from '../player-draft/playerDraft.adapter.ts';
import type { SeriesChildDraftEnvelopeDto, SeriesCreateRequestDto, SeriesGameViewDto, SeriesViewDto } from './api/seriesApi.types';
import type { SeriesScreenState, SeriesSetupSelection } from './series.types';
import type { PlayerDraftMatchPayloadDto } from '../player-draft/api/playerDraftApi.types';

export function createSeriesRequest(selection: SeriesSetupSelection, clientCommandId: string): SeriesCreateRequestDto {
  const game1BlueTeamCode = selection.game1ManagedSide === 'BLUE'
    ? selection.managedTeamCode : selection.opponentTeamCode;
  return {
    schemaVersion: 'SERIES_CREATE_REQUEST_V1',
    format: selection.format,
    teamACode: selection.managedTeamCode,
    teamBCode: selection.opponentTeamCode,
    managedTeamCode: selection.managedTeamCode,
    game1BlueTeamCode,
    rootSeed: selection.rootSeed,
    clientCommandId,
  };
}

export function createSeriesScreenState(
  series: SeriesViewDto,
  options: MatchSetupOptionsViewModel,
  championsById: Readonly<Record<string, PlayerDraftChampionCatalogEntry>>,
): SeriesScreenState {
  return { series, options, draft: series.activeDraftSession, reviewDraft: null, championsById, matchSession: null, matchGameNumber: null };
}

export function seriesMatchSelection(series: SeriesViewDto, game: SeriesGameViewDto): MatchSetupSelection {
  return {
    blueTeamId: game.blueTeamCode,
    redTeamId: game.redTeamCode,
    seed: game.matchSeed,
    gameNumber: game.gameNumber,
    seriesType: `${series.format} · ${series.winsRequired}선승`,
    draftMode: 'PLAYER_CONTROLLED',
    controlledSide: game.controlledSide,
  };
}

export function createSeriesMatchSession(
  series: SeriesViewDto,
  game: SeriesGameViewDto,
  child: SeriesChildDraftEnvelopeDto,
  match: PlayerDraftMatchPayloadDto,
  options: MatchSetupOptionsViewModel,
  performance: Omit<MatchSessionPerformance, 'normalizationMs'>,
) {
  return createPlayerDraftMatchSessionFromPayload(
    child.session, match, performance, options, seriesMatchSelection(series, game),
  );
}

export function shouldApplySeries(current: SeriesViewDto, next: SeriesViewDto): boolean {
  if (current.seriesId !== next.seriesId || next.revision < current.revision) return false;
  if (current.status === 'COMPLETED' && next.status !== 'COMPLETED') return false;
  if (['CANCELLED', 'EXPIRED'].includes(current.status) && next.status !== current.status) return false;
  return true;
}
