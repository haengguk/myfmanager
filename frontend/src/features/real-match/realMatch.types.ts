export type TeamSide = 'BLUE' | 'RED';
export type Position = 'TOP' | 'JUNGLE' | 'MID' | 'ADC' | 'SUPPORT';
export type Lane = 'TOP' | 'MID' | 'BOT';
export type ObjectiveType = 'VOID_GRUBS' | 'DRAGON' | 'BARON' | 'TOWER' | 'INHIBITOR' | 'NEXUS';

export interface ChampionViewModel {
  id: string;
  name: string;
  position: Position;
  portraitUrl: string;
}

export interface TeamViewModel {
  side: TeamSide;
  code: string;
  record: string;
  seriesScore: number;
}

export interface DraftRosterSlotViewModel {
  playerId: string;
  playerName: string;
  position: Position;
  championId: string | null;
}

export type DraftDecisionPhase = 'BAN' | 'PICK';

export interface DraftTurnViewModel {
  id: string;
  phase: DraftDecisionPhase;
  side: TeamSide;
  position: Position | null;
  round: 1 | 2;
  order: number;
}

export interface DraftViewModel {
  matchId: string;
  simulationSeed: string;
  seasonLabel: string;
  gameNumber: number;
  seriesType: string;
  initialSeconds: number;
  teams: Record<TeamSide, TeamViewModel>;
  champions: readonly ChampionViewModel[];
  rosters: Record<TeamSide, readonly DraftRosterSlotViewModel[]>;
  bans: Record<TeamSide, readonly string[]>;
  fearlessChampionIds: readonly string[];
  turnQueue: readonly DraftTurnViewModel[];
}

export interface DraftResultViewModel {
  rosters: Record<TeamSide, readonly DraftRosterSlotViewModel[]>;
  bans: Record<TeamSide, readonly string[]>;
}

export type PlaybackEventType =
  | 'MATCH_START'
  | 'ROAM'
  | 'GANK_ATTEMPT'
  | 'GOLD_LEAD'
  | 'KILL'
  | 'ASSIST'
  | 'PHASE_CHANGE'
  | 'OBJECTIVE'
  | 'TOWER'
  | 'TEAMFIGHT'
  | 'INHIBITOR'
  | 'MATCH_END';

export type CombatSource = 'JUNGLE_GANK' | 'LANE_COMBAT' | 'TEAMFIGHT' | 'OBJECTIVE_FIGHT' | null;

export interface StructuredEventParticipant {
  playerId: string;
  teamSide: TeamSide;
  role: 'KILLER' | 'VICTIM' | 'ASSISTANT' | 'PARTICIPANT';
}

export interface PlaybackEventViewModel {
  id: string;
  occurredAtSeconds: number;
  eventType: PlaybackEventType;
  teamSide: TeamSide | null;
  combatSource: CombatSource;
  engagementId: string | null;
  summaryOfEngagementId: string | null;
  engagementRole: 'SUMMARY' | 'DETAIL' | null;
  killerPlayerId: string | null;
  victimPlayerId: string | null;
  assistantPlayerIds: readonly string[];
  objective: ObjectiveType | null;
  lane: Lane | null;
  participants: readonly StructuredEventParticipant[];
  description: string;
  isMajor: boolean;
}

export interface LiveChampionStateViewModel {
  playerId: string;
  playerName: string;
  championId: string;
  position: Position;
  level: number;
  alive: boolean;
  respawnSeconds: number;
}

export interface TeamSnapshotViewModel {
  side: TeamSide;
  kills: number;
  gold: number;
  champions: readonly LiveChampionStateViewModel[];
}

export interface MatchSnapshotViewModel {
  atSeconds: number;
  teams: Record<TeamSide, TeamSnapshotViewModel>;
}

export interface PlayerComparisonViewModel {
  playerId: string;
  playerName: string;
  championId: string;
  position: Position;
  kills: number;
  deaths: number;
  assists: number;
  cs: number;
  gold: number;
  level: number;
}

export interface PositionComparisonViewModel {
  position: Position;
  blue: PlayerComparisonViewModel;
  red: PlayerComparisonViewModel;
}

export interface PlaybackViewModel {
  matchId: string;
  simulationSeed: string;
  seasonLabel: string;
  gameNumber: number;
  seriesType: string;
  durationSeconds: number;
  initialSeconds: number;
  comparisonReferenceSeconds: number;
  teams: Record<TeamSide, TeamViewModel>;
  championsById: Readonly<Record<string, ChampionViewModel>>;
  events: readonly PlaybackEventViewModel[];
  snapshots: readonly MatchSnapshotViewModel[];
  comparisonAtInitialTime: readonly PositionComparisonViewModel[];
  finalScore: Record<TeamSide, number>;
  winner: TeamSide | null;
  endReason: 'NEXUS_DESTROYED' | 'TIMEOUT';
}
