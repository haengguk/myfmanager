import type { ReferenceAbilityProfile } from './reference/realMatchReference.contract';
import type { DraftViewModel, GameEndReason, PlaybackViewModel, Position, TeamSide, TeamViewModel } from './realMatch.types';

export interface MatchRosterPlayerViewModel { playerId: string; playerName: string; position: Position; }
export interface MatchTeamOptionViewModel {
  teamId: string; code: string; name: string; sourceLabel: string; roster: readonly MatchRosterPlayerViewModel[];
}

export interface MatchSetupOptionsViewModel {
  seasonLabel: string; gameNumber: number; seriesType: string; draftRule: string; defaultSeed: string;
  referenceBlueTeamCode: string; referenceRedTeamCode: string; referenceLabel: string;
  teams: readonly MatchTeamOptionViewModel[];
}

export interface MatchSetupSelection {
  blueTeamId: string; redTeamId: string; seed: string; gameNumber: number; seriesType: string;
}

export type UiScenario = 'REFERENCE_SUCCESS' | 'REFERENCE_ERROR' | 'REFERENCE_TIMEOUT';

export interface TeamFinalStatsViewModel {
  kills: number; deaths: number; assists: number; gold: number; goldDifference: number;
  towers: number; dragons: number; barons: number; inhibitorsDestroyed: number;
}

export interface FinalPlayerViewModel {
  playerId: string; playerName: string; championId: string; position: Position;
  kills: number; deaths: number; assists: number; cs: number; gold: number;
  totalExperience: number; level: number; goldDifference: number;
  abilityProfile: ReferenceAbilityProfile;
}

export interface FinalPlayerComparisonViewModel { position: Position; blue: FinalPlayerViewModel; red: FinalPlayerViewModel; }

export interface MatchIntegrityViewModel {
  seed: string; referenceLabel: string; runtimeProfile: string; configurationHash: string;
  policyHash: string; engineImplementationVersion: string; resourceProvenanceHash: string;
  replayHash: string; simulatorTimelineHash: string; structuredTimelineHash: string; outputHash: string;
  randomDrawCount: number; randomTraceHash: string; manifestRawSha256: string;
}

export interface MatchResultViewModel {
  matchId: string; seasonLabel: string; gameNumber: number; seriesType: string; seed: string;
  durationSeconds: number; winner: TeamSide | null; endReason: GameEndReason;
  teams: Record<TeamSide, TeamViewModel>; teamStats: Record<TeamSide, TeamFinalStatsViewModel>;
  players: readonly FinalPlayerComparisonViewModel[]; bans: Record<TeamSide, readonly string[]>;
  integrity: MatchIntegrityViewModel;
}

export interface MatchSessionViewModel {
  sessionId: string; scenario: UiScenario; setup: MatchSetupSelection;
  selectedTeams: Record<TeamSide, MatchTeamOptionViewModel>;
  draft: DraftViewModel; playback: PlaybackViewModel; result: MatchResultViewModel;
}
