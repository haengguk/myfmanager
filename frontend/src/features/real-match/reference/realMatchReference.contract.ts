export type TeamSide = 'BLUE' | 'RED';
export type Position = 'TOP' | 'JUNGLE' | 'MID' | 'ADC' | 'SUPPORT';
export type Lane = 'TOP' | 'MID' | 'BOT';
export type DraftActionType = 'PICK' | 'BAN';
export type GameEndReason = 'NEXUS_DESTROYED' | 'SIMULATION_TIMEOUT';
export type MatchEventType =
  | 'GAME_START' | 'KILL' | 'ASSIST' | 'JUNGLE_GANK' | 'COUNTER_GANK' | 'LANE_COMBAT'
  | 'ROAM' | 'SHUTDOWN' | 'DRAGON' | 'BARON' | 'ELDER' | 'TOWER' | 'TEAMFIGHT'
  | 'TEAMFIGHT_RESULT' | 'ACE' | 'MATCH_PHASE_CHANGE' | 'MACRO_ACTION' | 'LATE_GAME_ACTION'
  | 'LEVEL_UP' | 'ITEM_STAGE_REACHED' | 'GAME_END';
export type CombatSource =
  | 'COUNTER_GANK' | 'JUNGLE_GANK' | 'LANE_COMBAT' | 'ROAM' | 'SKIRMISH' | 'TEAMFIGHT'
  | 'OBJECTIVE_FIGHT' | 'LATE_GAME_SIEGE' | 'BASE_DEFENSE' | 'OTHER';
export type StructureActionSource =
  | 'LANE_PRESSURE' | 'POST_FIGHT' | 'BARON_PRESSURE' | 'MACRO_PLAY' | 'MID_GAME_MACRO'
  | 'OBJECTIVE_TRADE' | 'LATE_GAME_SIEGE' | 'LATE_GAME_CROSS_MAP' | 'NEXUS_FINISH';
export type StructureKind = 'TOWER' | 'INHIBITOR' | 'NEXUS_TURRET' | 'NEXUS';
export type TowerTier = 'OUTER' | 'INNER' | 'INHIBITOR';
export type PlayerActivityType = 'DEFAULT_ROLE' | 'ROAMING';

export type AbilityRatingKey =
  | 'COMBAT_EXECUTION' | 'CONSISTENCY' | 'DECISION_MAKING' | 'FARMING' | 'LANE_PRESSURE'
  | 'MAP_AWARENESS' | 'MECHANICS' | 'POSITIONING' | 'PRIORITY_CONVERSION' | 'SIDE_LANE'
  | 'TRADING' | 'WAVE_MANAGEMENT';

export interface ReferencePlayerOption { nickname: string; playerId: string; position: Position; }
export interface ReferenceTeamOption { displayName: string; lineup: readonly ReferencePlayerOption[]; teamCode: string; }
export interface ReferenceChampionPresentation { championId: string; displayNameKo: string; displayNameEn: string; portraitUrl: string; }
export interface ReferencePlayerPresentation extends ReferencePlayerOption { championId: string; champion: ReferenceChampionPresentation; }
export interface ReferenceTeamPresentation { teamSide: TeamSide; teamCode: string; displayName: string; lineup: readonly ReferencePlayerPresentation[]; }
export interface ReferenceDraftDecision { turn: number; teamSide: TeamSide; actionType: DraftActionType; championId: string; }
export interface ReferenceFinalAssignment { playerId: string; teamSide: TeamSide; position: Position; championId: string; }

export interface ReferenceDraft {
  schemaVersion: string;
  seriesGameNumber: number;
  draftRuleSetIdentity: string;
  draftRuleSetHash: string;
  draftScoringPolicyHash: string;
  hardFearlessExclusionsBeforeDraft: readonly string[];
  decisions: readonly ReferenceDraftDecision[];
  blueBans: readonly string[];
  bluePicks: readonly string[];
  redBans: readonly string[];
  redPicks: readonly string[];
  finalAssignments: readonly ReferenceFinalAssignment[];
  finalDraftHash: string;
  finalAssignmentHash: string;
}

export interface ReferenceAbilityProfile {
  schemaVersion: 'PLAYER_ABILITY_PROFILE_V1';
  baseRatings: Readonly<Record<AbilityRatingKey, number>>;
  realizedRatings: Readonly<Record<AbilityRatingKey, number>>;
  realizationDeltas: Readonly<Record<AbilityRatingKey, number>>;
  selectedChampionProficiency: number;
  proficiencyExecutionAdjustment: number;
}

export interface ReferencePlayerResult {
  playerId: string; teamSide: TeamSide; position: Position; championId: string;
  kills: number; deaths: number; assists: number; cs: number; gold: number;
  totalExperience: number; level: number; abilityProfile: ReferenceAbilityProfile;
}

export interface ReferenceTeamResult {
  teamIdentity: string; teamSide: TeamSide; kills: number; totalGold: number;
  towersDestroyed: number; dragons: number; inhibitorsRemaining: number;
  nexusTurretsRemaining: number; nexusAlive: boolean; alivePlayers: number;
  hasBaronBuff: boolean; hasDragonSoul: boolean; hasElderBuff: boolean;
}

export interface ReferenceResult {
  schemaVersion: string; winner: TeamSide | null; endReason: GameEndReason; durationSeconds: number;
  teams: readonly ReferenceTeamResult[]; players: readonly ReferencePlayerResult[];
  finalDraftHash: string; finalAssignmentHash: string; runtimeProfileId: string;
  configurationHash: string; resourceProvenanceHash: string; replayProvenanceHash: string;
}

export interface ReferenceProjectedEvent {
  projectionId: string; timeSeconds: number; eventType: MatchEventType;
  actorSide: TeamSide | null; actorPosition: Position | null; lane: Lane | null;
  actorPlayerId: string | null; killerPlayerId: string | null; victimPlayerId: string | null;
  assistantPlayerIds: readonly string[]; killerChampionId: string | null; victimChampionId: string | null;
  assistantChampionIds: readonly string[]; combatSource: CombatSource | null;
  structureActionSource: StructureActionSource | null; structureKind: StructureKind | null;
  structureTowerTier: TowerTier | null; structureAttackingSide: TeamSide | null;
  structureDefendingSide: TeamSide | null; goldAmount: number; actionId: string | null;
  parentActionId: string | null; displayMessage: string | null;
}

export interface ReferenceProjectedTeamState {
  teamIdentity: string; teamSide: TeamSide; kills: number; gold: number; towersDestroyed: number;
  dragons: number; inhibitorsRemaining: number; nexusTurretsRemaining: number; nexusAlive: boolean;
  alivePlayers: number; hasBaronBuff: boolean; hasDragonSoul: boolean; hasElderBuff: boolean;
}

export interface ReferenceProjectedPlayerState {
  playerId: string; teamSide: TeamSide; position: Position; championId: string;
  kills: number; deaths: number; assists: number; cs: number; gold: number;
  totalExperience: number; level: number; alive: boolean; respawnRemainingSeconds: number;
  canFarm: boolean; farmReturnSecondsRemaining: number; activityType: PlayerActivityType | null;
  activityOriginLane: Lane | null; activityTargetLane: Lane | null;
}

export interface ReferenceProjectedSnapshot {
  timeSeconds: number; blueTeam: ReferenceProjectedTeamState; redTeam: ReferenceProjectedTeamState;
  players: readonly ReferenceProjectedPlayerState[];
}

export interface ReferenceIntegrity {
  matchEngineContract: string; policyId: string; policyHash: string; runtimeProfileId: string;
  configurationHash: string; engineImplementationVersion: string; activeGameplayRulesVersion: string;
  inputHash: string; inputHashAlgorithm: string; resourceProvenanceHash: string;
  replayProvenanceHash: string; replayProvenanceHashAlgorithm: string; simulatorTimelineHash: string;
  simulatorTimelineHashAlgorithm: string; structuredTimelineHash: string; structuredTimelineHashAlgorithm: string;
  outputHash: string; outputHashAlgorithm: string; outputHashScope: string;
  randomFingerprint: { schemaVersion: string; randomDrawCount: number; randomTraceHash: string; randomTraceHashAlgorithm: string; };
  diagnosticsExcludedFromGameplayIdentity: boolean;
}

export interface RealMatchV8ReferenceProjection {
  projectionSchemaVersion: 'REAL_MATCH_V8_REFERENCE_PROJECTION_V1';
  provenance: {
    sourceHandoffSchemaVersion: string; sourceResponseSchemaVersion: string; sourceManifestRawSha256: string;
    sourceOutputHash: string; engineImplementationVersion: string; sourceFullResponseBytes: number;
    sourceEventCount: number; includedEventCount: number; sourceSnapshotCount: number;
    includedSnapshotCount: number; eventSelectionPolicy: string; snapshotSelectionPolicy: string; referenceLabel: string;
  };
  options: {
    schemaVersion: string; matchEngineContract: string; seedPolicy: { encoding: string; required: boolean };
    productionPolicy: Record<string, string | boolean>;
    resourceVersions: { resourceProvenanceHash: string; versions: Record<string, string>; };
    teams: readonly ReferenceTeamOption[];
  };
  request: { schemaVersion: 'REAL_MATCH_SIMULATE_REQUEST_V1'; blueTeamCode: string; redTeamCode: string; seed: string; };
  match: {
    matchIdentity: string; seed: string; teams: readonly ReferenceTeamPresentation[]; draft: ReferenceDraft;
    result: ReferenceResult;
    timeline: { schemaVersion: string; winner: TeamSide | null; endReason: GameEndReason; durationSeconds: number; events: readonly ReferenceProjectedEvent[]; snapshots: readonly ReferenceProjectedSnapshot[]; };
    integrity: ReferenceIntegrity;
  };
}
