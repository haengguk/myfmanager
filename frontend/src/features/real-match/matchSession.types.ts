import type { MatchDataSource, PlayerAbilityProfileViewModel } from './realMatch.contract';
import type { DraftViewModel, GameEndReason, PlaybackViewModel, Position, TeamSide, TeamViewModel } from './realMatch.types';

export interface MatchRosterPlayerViewModel { playerId: string; playerName: string; position: Position; }
export interface MatchTeamOptionViewModel {
  teamId: string; code: string; name: string; sourceLabel: string; roster: readonly MatchRosterPlayerViewModel[];
}

export interface MatchSetupOptionsViewModel {
  source: MatchDataSource; sourceLabel: string; seasonLabel: string; gameNumber: number; seriesType: string;
  draftRule: string; defaultSeed: string; defaultBlueTeamCode: string; defaultRedTeamCode: string;
  engineImplementationVersion: string; runtimeProfile: string; configurationHash: string;
  teams: readonly MatchTeamOptionViewModel[];
}

export type MatchDraftMode = 'AUTO' | 'PLAYER_CONTROLLED';
export interface MatchSetupSelection {
  blueTeamId: string; redTeamId: string; seed: string; gameNumber: number; seriesType: string;
  draftMode: MatchDraftMode; controlledSide: TeamSide;
}

export interface TeamFinalStatsViewModel {
  kills: number; deaths: number; assists: number; gold: number; goldDifference: number;
  towers: number; dragons: number; barons: number; inhibitorsDestroyed: number;
}

export interface FinalPlayerViewModel {
  playerId: string; playerName: string; championId: string; position: Position;
  kills: number; deaths: number; assists: number; cs: number; gold: number;
  totalExperience: number; level: number; goldDifference: number;
  abilityProfile: PlayerAbilityProfileViewModel;
}

export interface FinalPlayerComparisonViewModel { position: Position; blue: FinalPlayerViewModel; red: FinalPlayerViewModel; }

export interface GoldDifferencePointViewModel {
  timeSeconds: number; blueGold: number; redGold: number; difference: number;
}

export interface MatchIntegrityViewModel {
  source: MatchDataSource; sourceLabel: string; seed: string; runtimeProfile: string; configurationHash: string;
  policyHash: string; engineImplementationVersion: string; resourceProvenanceHash: string;
  replayHash: string; simulatorTimelineHash: string; structuredTimelineHash: string; outputHash: string;
  randomDrawCount: number; randomTraceHash: string; manifestRawSha256: string | null;
}

export interface MatchResultViewModel {
  matchId: string; seasonLabel: string; gameNumber: number; seriesType: string; seed: string;
  durationSeconds: number; winner: TeamSide | null; endReason: GameEndReason;
  teams: Record<TeamSide, TeamViewModel>; teamStats: Record<TeamSide, TeamFinalStatsViewModel>;
  players: readonly FinalPlayerComparisonViewModel[]; bans: Record<TeamSide, readonly string[]>;
  goldTimeline: readonly GoldDifferencePointViewModel[];
  integrity: MatchIntegrityViewModel;
}

export interface MatchSessionViewModel {
  sessionId: string; source: MatchDataSource; setup: MatchSetupSelection;
  selectedTeams: Record<TeamSide, MatchTeamOptionViewModel>;
  draft: DraftViewModel; playback: PlaybackViewModel; result: MatchResultViewModel;
  draftOrigin: { mode: 'AUTO' } | {
    mode: 'PLAYER_CONTROLLED'; sessionId: string; controlledSide: TeamSide;
    controlEvidenceHash: string; draftIdentity: string;
  };
  performance: MatchSessionPerformance;
}

export interface MatchSessionPerformance {
  payloadBytes: number; requestAndDownloadMs: number; jsonParseMs: number;
  runtimeValidationMs: number; normalizationMs: number; requestStartedAt: number;
}
