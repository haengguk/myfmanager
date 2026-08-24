import type {
  CombatSource, DraftActionType, GameEndReason, Lane, MatchEventType, PlayerAbilityProfileViewModel,
  PlayerActivityType, Position, StructureActionSource, StructureKind, TeamSide, TowerTier,
} from '../realMatch.contract';

export interface RealMatchSimulateRequestDto {
  schemaVersion: 'REAL_MATCH_SIMULATE_REQUEST_V1';
  blueTeamCode: string;
  redTeamCode: string;
  seed: string;
}

export interface RealMatchProductionPolicyDto {
  policyId: string; policyHash: string; runtimeProfileId: string; configurationHash: string;
  activeGameplayRulesVersion: string; engineImplementationVersion: string; matchupMode: string;
  compositionMode: string; jungleClearContribution: string; economyCandidateActivation: boolean;
  tempoCandidateActivation: boolean; diagnosticsExcludedFromGameplayIdentity: boolean;
}

export interface RealMatchOptionPlayerDto { playerId: string; nickname: string; position: Position; }
export interface RealMatchOptionTeamDto { teamCode: string; displayName: string; lineup: readonly RealMatchOptionPlayerDto[]; }
export interface RealMatchOptionsDto {
  schemaVersion: 'REAL_MATCH_OPTIONS_V1'; matchEngineContract: string;
  productionPolicy: RealMatchProductionPolicyDto;
  seedPolicy: { required: boolean; encoding: 'SIGNED_INT64_DECIMAL_STRING' };
  teams: readonly RealMatchOptionTeamDto[];
  resourceVersions: { resourceProvenanceHash: string; versions: Readonly<Record<string, string>> };
}

export interface RealMatchChampionPresentationDto {
  championId: string; displayNameKo: string; displayNameEn: string; portraitUrl: string;
}
export interface RealMatchPlayerPresentationDto extends RealMatchOptionPlayerDto {
  championId: string; champion: RealMatchChampionPresentationDto;
}
export interface RealMatchTeamPresentationDto {
  teamSide: TeamSide; teamCode: string; displayName: string; lineup: readonly RealMatchPlayerPresentationDto[];
}
export interface RealMatchDraftDecisionDto { turn: number; teamSide: TeamSide; actionType: DraftActionType; championId: string; }
export interface RealMatchFinalAssignmentDto { playerId: string; teamSide: TeamSide; position: Position; championId: string; }
export interface RealMatchDraftDto {
  schemaVersion: 'REAL_MATCH_DRAFT_V1'; seriesGameNumber: number; draftRuleSetIdentity: string;
  draftRuleSetHash: string; draftScoringPolicyHash: string; hardFearlessExclusionsBeforeDraft: readonly string[];
  decisions: readonly RealMatchDraftDecisionDto[]; blueBans: readonly string[]; bluePicks: readonly string[];
  redBans: readonly string[]; redPicks: readonly string[]; finalAssignments: readonly RealMatchFinalAssignmentDto[];
  finalDraftHash: string; finalAssignmentHash: string;
}

export interface RealMatchTeamResultDto {
  teamIdentity: string; teamSide: TeamSide; kills: number; totalGold: number; dragons: number;
  hasDragonSoul: boolean; hasBaronBuff: boolean; hasElderBuff: boolean; towersDestroyed: number;
  inhibitorsRemaining: number; nexusTurretsRemaining: number; nexusAlive: boolean; alivePlayers: number;
}
export interface RealMatchPlayerResultDto {
  playerId: string; teamSide: TeamSide; position: Position; championId: string; kills: number; deaths: number;
  assists: number; cs: number; gold: number; totalExperience: number; level: number;
  abilityProfile: PlayerAbilityProfileViewModel;
}
export interface RealMatchResultDto {
  schemaVersion: 'MATCH_RESULT_SUMMARY_V1'; winner: TeamSide | null; endReason: GameEndReason;
  durationSeconds: number; teams: readonly RealMatchTeamResultDto[]; players: readonly RealMatchPlayerResultDto[];
  finalDraftHash: string; finalAssignmentHash: string; runtimeProfileId: string; configurationHash: string;
  resourceProvenanceHash: string; replayProvenanceHash: string;
}

export interface RealMatchEventDto {
  timeSeconds: number; eventType: MatchEventType; actorSide: TeamSide | null; actorPosition: Position | null;
  lane: Lane | null; actorPlayerId: string | null; killerPlayerId: string | null; victimPlayerId: string | null;
  assistantPlayerIds: readonly string[]; killerChampionId: string | null; victimChampionId: string | null;
  assistantChampionIds: readonly string[]; combatSource: CombatSource | null;
  structureActionSource: StructureActionSource | null; structureKind: StructureKind | null;
  structureTowerTier: TowerTier | null; structureAttackingSide: TeamSide | null;
  structureDefendingSide: TeamSide | null; goldAmount: number; bountyRawBeforePayout: number;
  actionId: string | null; parentActionId: string | null; displayMessage: string | null;
  structuredData: Readonly<Record<string, unknown>>;
}
export interface RealMatchTeamStateDto {
  teamIdentity: string; teamSide: TeamSide; kills: number; gold: number; dragons: number;
  hasDragonSoul: boolean; hasBaronBuff: boolean; hasElderBuff: boolean; elderBuffRemainingSeconds: number;
  towersDestroyed: number; inhibitorsRemaining: number; nexusTurretsRemaining: number; nexusAlive: boolean;
  alivePlayers: number;
}
export interface RealMatchPlayerStateDto {
  playerId: string; teamSide: TeamSide; position: Position; championId: string; kills: number; deaths: number;
  assists: number; cs: number; gold: number; alive: boolean; respawnAtSeconds: number;
  respawnRemainingSeconds: number; canFarm: boolean; farmResumeAtSeconds: number;
  farmReturnSecondsRemaining: number; shutdownBountyGold: number; bountyProgress: number;
  activityType: PlayerActivityType | null; activityOriginLane: Lane | null; activityTargetLane: Lane | null;
  activityUntilSeconds: number; totalExperience: number; level: number; itemProgressStage: string;
  structuredProgression: Readonly<Record<string, unknown>>;
}
export interface RealMatchSnapshotDto {
  timeSeconds: number; blueTeam: RealMatchTeamStateDto; redTeam: RealMatchTeamStateDto;
  players: readonly RealMatchPlayerStateDto[]; structuredState: Readonly<Record<string, unknown>>;
}
export interface RealMatchTimelineDto {
  schemaVersion: 'MATCH_ENGINE_TIMELINE_V1'; durationSeconds: number; winner: TeamSide | null;
  endReason: GameEndReason; events: readonly RealMatchEventDto[]; snapshots: readonly RealMatchSnapshotDto[];
}

export interface RealMatchIntegrityDto {
  matchEngineContract: string; policyId: string; policyHash: string; runtimeProfileId: string;
  configurationHash: string; engineImplementationVersion: string; activeGameplayRulesVersion: string;
  inputHash: string; inputHashAlgorithm: string; resourceProvenanceHash: string; replayProvenanceHash: string;
  replayProvenanceHashAlgorithm: string; simulatorTimelineHash: string; simulatorTimelineHashAlgorithm: string;
  structuredTimelineHash: string; structuredTimelineHashAlgorithm: string; outputHash: string;
  outputHashAlgorithm: string; outputHashScope: string;
  randomFingerprint: { schemaVersion: string; randomDrawCount: number; randomTraceHash: string; randomTraceHashAlgorithm: string };
  diagnosticsExcludedFromGameplayIdentity: boolean;
}
export interface RealMatchResponseDto {
  schemaVersion: 'REAL_MATCH_RESPONSE_V1'; matchIdentity: string; seed: string;
  teams: readonly RealMatchTeamPresentationDto[]; draft: RealMatchDraftDto; result: RealMatchResultDto;
  timeline: RealMatchTimelineDto; integrity: RealMatchIntegrityDto;
}

export interface RealMatchApiErrorDto {
  schemaVersion: 'REAL_MATCH_API_ERROR_V1'; code: string; field: string | null; message: string;
}

export type MatchRequestStage = 'CONNECTING' | 'DOWNLOADING' | 'PARSING' | 'VALIDATING' | 'NORMALIZING';
