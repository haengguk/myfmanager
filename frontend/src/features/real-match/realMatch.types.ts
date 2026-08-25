import type {
  CombatSource, GameEndReason, Lane, MatchDataSource, MatchEventType, PlayerAbilityProfileViewModel, Position,
  StructureActionSource, StructureKind, TeamSide, TowerTier,
} from './realMatch.contract';

export type { CombatSource, GameEndReason, Lane, MatchDataSource, MatchEventType, Position, TeamSide };

export interface ChampionViewModel { id: string; name: string; nameEn: string; portraitUrl: string; }
export interface TeamViewModel { side: TeamSide; code: string; displayName: string; detail: string; }
export interface DraftRosterSlotViewModel { playerId: string; playerName: string; position: Position; championId: string; }
export interface DraftDecisionViewModel { turn: number; side: TeamSide; actionType: 'BAN' | 'PICK'; championId: string; }

// 향후 수동 Draft 프로토타입 전용 타입. Real Match V1 경로에서는 사용하지 않는다.
export interface DraftTurnViewModel {
  id: string; phase: 'BAN' | 'PICK'; side: TeamSide; position: Position | null; round: 1 | 2; order: number;
}

export interface DraftViewModel {
  matchId: string;
  simulationSeed: string;
  seasonLabel: string;
  gameNumber: number;
  seriesType: string;
  source: MatchDataSource;
  sourceLabel: string;
  teams: Record<TeamSide, TeamViewModel>;
  championsById: Readonly<Record<string, ChampionViewModel>>;
  rosters: Record<TeamSide, readonly DraftRosterSlotViewModel[]>;
  bans: Record<TeamSide, readonly string[]>;
  picks: Record<TeamSide, readonly string[]>;
  decisions: readonly DraftDecisionViewModel[];
  draftRuleSetIdentity: string;
  finalDraftHash: string;
  finalAssignmentHash: string;
}

export interface PlaybackEventViewModel {
  id: string; occurredAtSeconds: number; eventType: MatchEventType;
  actorSide: TeamSide | null; actorPosition: Position | null; lane: Lane | null;
  actorPlayerId: string | null; killerPlayerId: string | null; victimPlayerId: string | null;
  assistantPlayerIds: readonly string[]; combatSource: CombatSource | null;
  structureActionSource: StructureActionSource | null; structureKind: StructureKind | null;
  structureTowerTier: TowerTier | null; structureAttackingSide: TeamSide | null;
  structureDefendingSide: TeamSide | null; actionId: string | null; parentActionId: string | null;
  structureAction: StructureActionViewModel | null;
  goldAmount: number; displayMessage: string; isMajor: boolean; showInLog: boolean;
}

export type StructureActionPhase = 'STARTED' | 'DAMAGE' | 'DESTROYED' | 'REPELLED' | 'ABORTED' | 'RESPAWNED';
export interface StructureActionViewModel {
  phase: StructureActionPhase; targetId: string; healthBefore: number; damage: number;
  healthAfter: number; maxHealth: number; platesClaimed: number; wavePresent: boolean;
  backdoorProtection: boolean; siegeContinues: boolean; stopReason: string | null;
}

export interface LiveChampionStateViewModel {
  playerId: string; playerName: string; championId: string; position: Position;
  kills: number; deaths: number; assists: number; cs: number; gold: number;
  totalExperience: number; level: number; alive: boolean; respawnSeconds: number; shutdownBountyGold: number;
}

export interface TeamSnapshotViewModel {
  side: TeamSide; kills: number; gold: number; towersDestroyed: number; dragons: number;
  inhibitorsRemaining: number; nexusTurretsRemaining: number; nexusAlive: boolean;
  champions: readonly LiveChampionStateViewModel[];
}

export interface MatchSnapshotViewModel { atSeconds: number; teams: Record<TeamSide, TeamSnapshotViewModel>; }

export interface PlayerComparisonViewModel {
  playerId: string; playerName: string; championId: string; position: Position;
  kills: number; deaths: number; assists: number; cs: number; gold: number;
  totalExperience: number; level: number; shutdownBountyGold: number;
}

export interface PositionComparisonViewModel { position: Position; blue: PlayerComparisonViewModel; red: PlayerComparisonViewModel; }

export interface PlaybackViewModel {
  matchId: string; simulationSeed: string; seasonLabel: string; gameNumber: number; seriesType: string;
  source: MatchDataSource; sourceLabel: string; durationSeconds: number; initialSeconds: number;
  teams: Record<TeamSide, TeamViewModel>;
  championsById: Readonly<Record<string, ChampionViewModel>>;
  playerNamesById: Readonly<Record<string, string>>;
  events: readonly PlaybackEventViewModel[];
  snapshots: readonly MatchSnapshotViewModel[];
  finalScore: Record<TeamSide, number>;
  winner: TeamSide | null;
  endReason: GameEndReason;
  projection: { sourceEventCount: number; includedEventCount: number; sourceSnapshotCount: number; includedSnapshotCount: number; };
}

export interface PlayerAbilityViewModel {
  playerId: string; playerName: string; championId: string; championName: string; position: Position;
  profile: PlayerAbilityProfileViewModel;
}
