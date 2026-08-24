import type {
  DraftResultViewModel,
  DraftViewModel,
  PlaybackViewModel,
  Position,
  PositionComparisonViewModel,
  TeamSide,
  TeamViewModel,
} from './realMatch.types';

export interface MatchRosterPlayerViewModel {
  playerId: string;
  playerName: string;
  position: Position;
}

export interface MatchTeamOptionViewModel {
  teamId: string;
  code: string;
  name: string;
  league: string;
  record: string;
  roster: readonly MatchRosterPlayerViewModel[];
}

export interface MatchSetupOptionsViewModel {
  seasonLabel: string;
  gameNumber: number;
  seriesType: string;
  draftRule: string;
  defaultSeed: string;
  teams: readonly MatchTeamOptionViewModel[];
}

export interface MatchSetupSelection {
  blueTeamId: string;
  redTeamId: string;
  seed: string;
  gameNumber: number;
  seriesType: string;
}

export type MatchEndReason = 'NEXUS_DESTROYED' | 'TIMEOUT';

export interface TeamFinalStatsViewModel {
  kills: number;
  deaths: number;
  assists: number;
  gold: number;
  goldDifference: number;
  towers: number;
  dragons: number;
  barons: number;
  inhibitors: number;
}

export type FinalPlayerViewModel = PositionComparisonViewModel['blue'] & {
  laneGoldDifference: number;
};

export interface FinalPlayerComparisonViewModel {
  position: Position;
  blue: FinalPlayerViewModel;
  red: FinalPlayerViewModel;
}

export interface MatchIntegrityViewModel {
  seed: string;
  outputHash: string | null;
  replayHash: string | null;
  runtimeProfile: string;
  responseTime: string;
}

export interface MatchResultViewModel {
  matchId: string;
  seasonLabel: string;
  gameNumber: number;
  seriesType: string;
  seed: string;
  durationSeconds: number;
  winner: TeamSide | null;
  endReason: MatchEndReason;
  teams: Record<TeamSide, TeamViewModel>;
  teamStats: Record<TeamSide, TeamFinalStatsViewModel>;
  players: readonly FinalPlayerComparisonViewModel[];
  bans: Record<TeamSide, readonly string[]>;
  integrity: MatchIntegrityViewModel;
}

export interface MatchSessionViewModel {
  sessionId: string;
  createdAt: string;
  setup: MatchSetupSelection;
  selectedTeams: Record<TeamSide, MatchTeamOptionViewModel>;
  draft: DraftViewModel;
  draftResult: DraftResultViewModel | null;
  playback: PlaybackViewModel;
  result: MatchResultViewModel | null;
}
