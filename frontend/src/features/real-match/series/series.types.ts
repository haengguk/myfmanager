import type { MatchSessionViewModel, MatchSetupOptionsViewModel, MatchSetupSelection } from '../matchSession.types';
import type { PlayerDraftChampionCatalogEntry } from '../player-draft/playerDraft.types';
import type { SeriesChildDraftEnvelopeDto, SeriesFormat, SeriesGameViewDto, SeriesViewDto } from './api/seriesApi.types';
import type { TeamSide } from '../realMatch.contract';

export interface SeriesSetupSelection {
  format: SeriesFormat;
  managedTeamCode: string;
  opponentTeamCode: string;
  game1ManagedSide: TeamSide;
  rootSeed: string;
}

export interface SeriesScreenState {
  series: SeriesViewDto;
  options: MatchSetupOptionsViewModel;
  draft: SeriesChildDraftEnvelopeDto | null;
  reviewDraft: SeriesChildDraftEnvelopeDto | null;
  championsById: Readonly<Record<string, PlayerDraftChampionCatalogEntry>>;
  matchSession: MatchSessionViewModel | null;
  matchGameNumber: number | null;
}

export interface SeriesMatchContext {
  game: SeriesGameViewDto;
  selection: MatchSetupSelection;
}

export const SERIES_POINTER_KEY = 'lolmanager.activeSeries.v1';
