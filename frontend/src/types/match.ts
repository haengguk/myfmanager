export interface MatchSimulateResponse {
  seed: number;
  timeline: MatchTimeline;
  championMetadata: ChampionMatchMetadata;
}

export type Position = 'TOP' | 'JUNGLE' | 'MID' | 'ADC' | 'SUPPORT';
export type ChampionId = string;
export interface ChampionDefinitionDto {
  id: ChampionId;
  displayNameKo: string;
  displayNameEn: string;
  riotAssetId: string;
  primaryPosition: Position;
  supportedPositions: Position[];
  portraitUrl: string;
  championPoolVersion: string;
  riotDataVersion: string;
}
export interface ChampionLineupRequest { top: ChampionId; jgl: ChampionId; mid: ChampionId; adc: ChampionId; sup: ChampionId; }
export interface ChampionSelectionRequest { blue: ChampionLineupRequest; red: ChampionLineupRequest; }
export interface ChampionCatalogResponse {
  championPoolVersion: string;
  championBalanceVersion: string;
  riotDataVersion: string;
  defaultSelection: ChampionSelectionRequest;
  champions: ChampionDefinitionDto[];
}
export interface ChampionSnapshot {
  id: ChampionId;
  displayNameKo: string;
  displayNameEn: string;
  portraitUrl: string;
  primaryPosition: Position;
  poolVersion: string;
}
export interface ChampionLineupSnapshot { top: ChampionSnapshot; jgl: ChampionSnapshot; mid: ChampionSnapshot; adc: ChampionSnapshot; sup: ChampionSnapshot; }
export interface ChampionMatchMetadata {
  championPoolVersion: string;
  championBalanceVersion: string;
  riotDataVersion: string;
  selectionMode: 'DEFAULT_FIXED' | 'EXPLICIT';
  blue: ChampionLineupSnapshot;
  red: ChampionLineupSnapshot;
}

export interface MatchTimeline {
  durationSeconds: number;
  winner: string;
  events: MatchEvent[];
  snapshots: MatchSnapshot[];
}

export interface MatchEvent {
  timeSeconds: number;
  roam: RoamData | null;
  type: string;
  message: string;
  killer: string | null;
  victim: string | null;
  assists: string[];
  goldAmount: number;
  combatSource: 'COUNTER_GANK' | 'JUNGLE_GANK' | 'ROAM' | 'LANE_COMBAT' | 'SKIRMISH' | 'TEAMFIGHT' | 'OBJECTIVE_FIGHT' | 'LATE_GAME_SIEGE' | 'BASE_DEFENSE' | 'OTHER' | null;
  laneCombat: LaneCombatData | null;
  jungleGank: JungleGankData | null;
  counterGank: CounterGankData | null;
  objectivePriorityDecision?: ObjectivePriorityDecisionData | null;
  objectiveDecision?: ObjectiveDecisionData | null;
  structureActionSource?: StructureActionSource | null;
  structureKind?: StructureKind | null;
  structureTowerTier?: TowerTier | null;
  structureLane?: Lane | null;
  structureAttackingSide?: TeamSide | null;
  structureDefendingSide?: TeamSide | null;
  outerTurretSiege?: OuterTurretSiegeData | null;
  midGameMacroDecision?: MidGameMacroDecisionData | null;
  midGameMacroAction?: MidGameMacroActionData | null;
  matchPhaseChange?: MatchPhaseChangeData | null;
  lateGameDecision?: LateGameDecisionData | null;
  progressionEvent?: ProgressionEventData | null;
}


export type ObjectiveType = 'DRAGON' | 'BARON' | 'ELDER';
export type TeamSide = 'BLUE' | 'RED';
export type Lane = 'TOP' | 'MID' | 'BOT';
export type MatchPhase = 'LANING' | 'MID_GAME' | 'LATE_GAME';
export type LanePhase = 'LANING' | 'OPEN';
export type MidGameTransitionReason = 'TIME_LIMIT' | 'ALL_LANES_OPEN';
export type StructureActionSource = 'LANE_PRESSURE' | 'POST_FIGHT' | 'BARON_PRESSURE' | 'MACRO_PLAY' | 'MID_GAME_MACRO' | 'OBJECTIVE_TRADE' | 'LATE_GAME_SIEGE' | 'LATE_GAME_CROSS_MAP' | 'NEXUS_FINISH';
export type StructureKind = 'TOWER' | 'INHIBITOR' | 'NEXUS_TURRET' | 'NEXUS';
export type TowerTier = 'OUTER' | 'INNER' | 'INHIBITOR';

export interface OuterTurretSiegeData {
  timeSeconds: number;
  lane: Lane;
  attackingSide: TeamSide;
  defendingSide: TeamSide;
  lanePressure: number;
  integrityBefore: number;
  pressureDamage: number;
  defenderAbsentBonus: number;
  botSupportBonus: number;
  randomVariance: number;
  finalDamage: number;
  integrityAfter: number;
  destroyed: boolean;
}

export interface MatchPhaseChangeData {
  previousPhase: MatchPhase;
  newPhase: MatchPhase;
  transitionTimeSeconds: number;
  reason: MidGameTransitionReason;
  alreadyOpenLanes: Lane[];
  forcedOpenLanes: Lane[];
  lateGameTransitionReason: LateGameTransitionReason | null;
  triggerSide: TeamSide | null;
  triggerLane: Lane | null;
  triggerStructure: LateGameStructureTarget | null;
}

export interface OuterTurretSnapshot {
  alive: boolean;
  remainingIntegrity: number;
  destroyedAtSeconds: number;
}

export interface LanePhaseLaneSnapshot {
  lane: Lane;
  phase: LanePhase;
  pressure: number;
  pressureFarmModifierActive: boolean;
  blueOuter: OuterTurretSnapshot;
  redOuter: OuterTurretSnapshot;
}

export interface LanePhaseSnapshot {
  enabled: boolean;
  matchPhase: MatchPhase;
  midGameStartedAtSeconds: number;
  transitionReason: MidGameTransitionReason | null;
  lateGameStartedAtSeconds: number;
  lateGameTransitionReason: LateGameTransitionReason | null;
  lanes: LanePhaseLaneSnapshot[];
}


export interface ObjectiveSelectionWeightBreakdown {
  aliveContribution: number;
  goldContribution: number;
  killContribution: number;
  recentBigWinContribution: number;
  recentAceContribution: number;
  otherContribution: number;
  totalExistingWeight: number;
}

export interface ObjectivePriorityDecisionData {
  objectiveType: ObjectiveType;
  evaluationTimeSeconds: number;
  priorityEnabled: boolean;
  generalAttempt: boolean;
  postFightLinked: boolean;
  priorityApplied: boolean;
  lanePressureScore: number;
  recentControl: number;
  signedPriority: number;
  bluePriority: number;
  redPriority: number;
  existingBaseAttemptChance: number;
  priorityAttemptBonus: number;
  finalAttemptChance: number;
  attemptRollExecuted: boolean;
  attemptRollSucceeded: boolean;
  blueEligible: boolean;
  redEligible: boolean;
  blueExistingWeight: ObjectiveSelectionWeightBreakdown;
  redExistingWeight: ObjectiveSelectionWeightBreakdown;
  bluePriorityMultiplier: number;
  redPriorityMultiplier: number;
  finalBlueSelectionWeight: number;
  finalRedSelectionWeight: number;
  sideSelectionRollExecuted: boolean;
  selectedSide: TeamSide | null;
}

export interface ObjectivePrioritySnapshot {
  enabled: boolean;
  dragonLanePressureScore: number;
  dragonRecentControl: number;
  dragonSignedPriority: number;
  blueDragonPriority: number;
  redDragonPriority: number;
  baronLanePressureScore: number;
  baronRecentControl: number;
  baronSignedPriority: number;
  blueBaronPriority: number;
  redBaronPriority: number;
  dragonMacroSetupControl: number;
  baronMacroSetupControl: number;
  evaluationHistory: MidGameMacroEvaluationData[];
  matchEnded: boolean;
}

export interface RoamData {
  roamingSide: 'BLUE' | 'RED'; roamerPlayerId: string; roamerPosition: 'MID' | 'SUPPORT';
  originLane: 'TOP' | 'MID' | 'BOT'; targetLane: 'TOP' | 'MID' | 'BOT';
  outcome: 'NO_KILL' | 'ROAMING_SIDE_KILL' | 'DEFENDING_SIDE_KILL'; winningSide: 'BLUE' | 'RED' | null;
  killerPlayerId: string | null; victimPlayerId: string | null; assistantPlayerIds: string[];
  originPressureBefore: number; originPressureAfter: number; targetPressureBefore: number; targetPressureAfter: number;
  originPriority: number; targetEnemyOverextension: number; activityUntilSeconds: number; roamFarmBlockedUntilSeconds: number;
  repeatTarget: boolean; repeatPenaltyApplied: boolean;
  attemptChance: number; targetWeight: number; combatEdge: number; decisiveChance: number; roamSuccessChance: number;
}

export interface JungleGankData {
  junglerPlayerId: string;
  gankingSide: 'BLUE' | 'RED';
  targetLane: 'TOP' | 'MID' | 'BOT';
  outcome: 'NO_KILL' | 'GANK_SUCCESS' | 'DEFENDER_REVERSE_KILL';
  winningSide: 'BLUE' | 'RED' | null;
  killerPlayerId: string | null;
  victimPlayerId: string | null;
  assistantPlayerIds: string[];
  pressureBefore: number;
  pressureAfter: number;
  enemyOverextension: number;
  jungleFarmBlockedUntilSeconds: number;
  attemptChance: number;
  targetWeight: number;
  combatEdge: number;
  decisiveChance: number;
  gankSuccessChance: number;
  blueTriggered: boolean;
  redTriggered: boolean;
  counterEligible: boolean;
  counterIneligibility: 'NONE' | 'OUTSIDE_WINDOW' | 'DEFENDING_JUNGLER_DEAD' | 'DEFENDING_JUNGLER_COOLDOWN' | 'LANE_PARTICIPANT_DEAD';
  defenderInitiallyTriggered: boolean;
  counterResponseRolled: boolean;
  counterResponseChance: number;
  counterResponseSucceeded: boolean;
}

export interface CounterGankData {
  attackingSide: 'BLUE' | 'RED';
  defendingSide: 'BLUE' | 'RED';
  attackingJunglerPlayerId: string;
  defendingJunglerPlayerId: string;
  targetLane: 'TOP' | 'MID' | 'BOT';
  defenderInitiallyTriggered: boolean;
  responseChance: number;
  outcome: 'NO_KILL' | 'ATTACKING_SIDE_KILL' | 'DEFENDING_SIDE_KILL';
  winningSide: 'BLUE' | 'RED' | null;
  killerPlayerId: string | null;
  victimPlayerId: string | null;
  assistantPlayerIds: string[];
  pressureBefore: number;
  pressureAfter: number;
  enemyOverextension: number;
  attackingJungleFarmBlockedUntilSeconds: number;
  defendingJungleFarmBlockedUntilSeconds: number;
  combatEdge: number;
  decisiveChance: number;
  attackingSideWinChance: number;
  attackingGroupMechanics: number;
  defendingGroupMechanics: number;
  attackingGroupGold: number;
  defendingGroupGold: number;
}

export interface LaneCombatData {
  lane: 'TOP' | 'MID' | 'BOT';
  initiatorSide: 'BLUE' | 'RED';
  outcome: 'NO_KILL' | 'ATTACKER_KILL' | 'DEFENDER_REVERSE_KILL';
  winningSide: 'BLUE' | 'RED' | null;
  killerPlayerId: string | null;
  victimPlayerId: string | null;
  assistantPlayerIds: string[];
  pressureBefore: number;
  pressureAfter: number;
}

export interface MatchSnapshot {
  timeSeconds: number;
  blueKills: number;
  redKills: number;
  blueGold: number;
  redGold: number;
  blueDragons: number;
  redDragons: number;
  blueHasDragonSoul: boolean;
  redHasDragonSoul: boolean;
  blueHasBaronBuff: boolean;
  redHasBaronBuff: boolean;
  elderAlive: boolean;
  blueHasElderBuff: boolean;
  redHasElderBuff: boolean;
  blueElderBuffRemainingSeconds: number;
  redElderBuffRemainingSeconds: number;
  blueTowersDestroyed: number;
  redTowersDestroyed: number;
  blueInhibitorsRemaining: number;
  redInhibitorsRemaining: number;
  blueNexusTurretsRemaining: number;
  redNexusTurretsRemaining: number;
  blueNexusAlive: boolean;
  redNexusAlive: boolean;
  blueAlivePlayers: number;
  redAlivePlayers: number;
  playerSnapshots: PlayerSnapshot[];
  laneSnapshots: LaneSnapshot[];
  objectivePriority: ObjectivePrioritySnapshot;
  lanePhase: LanePhaseSnapshot;
  midGameMacro?: MidGameMacroSnapshot | null;
  objectiveDecision?: ObjectiveDecisionSnapshot | null;
  lateGame?: LateGameSnapshot | null;
  progression?: ProgressionSnapshot | null;
}

export interface LaneSnapshot {
  lane: 'TOP' | 'MID' | 'BOT';
  pressure: number;
  priority: 'BLUE' | 'NEUTRAL' | 'RED';
}

export type ItemProgressStage = 'STARTING' | 'COMPONENT' | 'FIRST_CORE' | 'SECOND_CORE' | 'THIRD_CORE' | 'FOURTH_CORE' | 'FULL_BUILD';
export type ProgressionEventType = 'EXPERIENCE_GAINED' | 'LEVEL_UP' | 'ITEM_STAGE_REACHED';
export type ExperienceSource = 'LANE_ECONOMY' | 'BOT_SHARED_ECONOMY' | 'BOT_SOLO_ECONOMY' | 'JUNGLE_ECONOMY' | 'KILL' | 'ASSIST';
export interface ProgressionPowerSnapshot { levelPower:number; itemPower:number; championMultiplier:number; championSpikeBonus:number; totalPower:number; }
export interface PlayerProgressionSnapshot { enabled:boolean; level:number; totalExperience:number; currentLevelStartExperience:number; nextLevelTotalExperience:number; levelProgressRatio:number; itemStage:ItemProgressStage; progressionEarnedGold:number; nextItemStageGold:number; itemProgressRatio:number; progressionPower:ProgressionPowerSnapshot; }
export interface TeamProgressionSnapshot { averageLevel:number; totalCoreCount:number; level18Count:number; players:Record<string,PlayerProgressionSnapshot>; }
export interface ProgressionSnapshot { enabled:boolean; powerEnabled:boolean; blue:TeamProgressionSnapshot|null; red:TeamProgressionSnapshot|null; }
export interface ProgressionEventData { side:TeamSide; playerKey:{side:TeamSide;position:string}; position:string; type:ProgressionEventType; experienceSource:ExperienceSource|null; previousExperience:number; newExperience:number; experienceGained:number; previousLevel:number; newLevel:number; previousItemStage:ItemProgressStage; newItemStage:ItemProgressStage; progressionEarnedGold:number; threshold:number; timeSeconds:number; }

export interface PlayerSnapshot {
  playerName: string;
  teamName: string;
  teamSide: 'BLUE' | 'RED';
  position: string;
  kills: number;
  deaths: number;
  assists: number;
  cs: number;
  gold: number;
  alive: boolean;
  respawnAtSeconds: number;
  respawnRemainingSeconds: number;
  canFarm: boolean;
  farmResumeAtSeconds: number;
  farmReturnSecondsRemaining: number;
  hasElderBuff: boolean;
  elderBuffRemainingSeconds: number;
  shutdownBountyGold: number;
  activityType: 'DEFAULT_ROLE' | 'ROAMING';
  activityOriginLane: 'TOP' | 'MID' | 'BOT' | null;
  activityTargetLane: 'TOP' | 'MID' | 'BOT' | null;
  activityUntilSeconds: number;
  activitySecondsRemaining: number;
  roamFarmBlockedUntilSeconds: number;
  hasShutdownBounty: boolean;
  totalShutdownGoldEarned: number;
  totalShutdownGoldGiven: number;
  bountyProgress: number;
  progression: PlayerProgressionSnapshot;
  level: number;
  totalExperience: number;
  currentLevelStartExperience: number;
  nextLevelTotalExperience: number;
  levelProgressRatio: number;
  itemStage: ItemProgressStage;
  progressionEarnedGold: number;
  nextItemStageGold: number;
  itemProgressRatio: number;
  progressionPower: ProgressionPowerSnapshot;
  champion: ChampionSnapshot;
}

export type TeamMacroPlan = 'GROUP_MID' | 'SIDE_LANE_TOP' | 'SIDE_LANE_BOT' | 'OBJECTIVE_SETUP_DRAGON' | 'OBJECTIVE_SETUP_BARON' | 'RESET_AND_FARM';
export type MacroActionType = 'STRUCTURE_PUSH' | 'OBJECTIVE_SETUP' | 'RESET';
export type MacroActionResult = 'NOT_ATTEMPTED' | 'INELIGIBLE' | 'PUSH_FAILED' | 'STRUCTURE_DESTROYED' | 'SETUP_STARTED' | 'RESET_STARTED';
export type MacroPlanStatus = 'DISABLED' | 'NOT_STARTED' | 'WAITING_FOR_EVALUATION' | 'ACTIVE' | 'EXPIRED' | 'CANCELLED' | 'MATCH_ENDED';
export type MacroPlanEndReason = 'EXPIRED' | 'REPLACED' | 'OBJECTIVE_CAPTURED' | 'OBJECTIVE_UNAVAILABLE' | 'LATE_GAME_TRANSITION' | 'FEATURE_DISABLED' | 'MATCH_ENDED';

export interface MacroPlanWeightBreakdown {
  plan: TeamMacroPlan;
  eligible: boolean;
  baseWeight: number;
  goldEdge: number;
  goldContribution: number;
  attributeEdge: number;
  attributeContribution: number;
  objectivePriorityEdge: number;
  objectiveContribution: number;
  soulBonus: number;
  resetBehindContribution: number;
  resetMissingPlayerContribution: number;
  repeatMultiplier: number;
  finalWeight: number;
  ineligibleReason: string | null;
}

export interface TeamMacroSnapshot {
  currentPlan: TeamMacroPlan | null;
  targetLane: Lane | null;
  targetObjective: ObjectiveType | null;
  startedAtSeconds: number;
  activeUntilSeconds: number;
  nextEvaluationAtSeconds: number;
  assignedPositions: string[];
  lastActionResult: MacroActionResult;
  lastDestroyedStructure: StructureKind | null;
  lastDestroyedTowerTier: TowerTier | null;
  lastStructureLane: Lane | null;
  lastSelectedPlan: TeamMacroPlan | null;
  status: MacroPlanStatus;
  endReason: MacroPlanEndReason | null;
  lastEvaluationDueAtSeconds: number;
  lastEvaluationAtSeconds: number;
  lastEvaluationSkippedReason: string | null;
  lastSelectionRandomConsumptionCount: number;
}

export interface MidGameMacroEvaluationData {
  dueAtSeconds: number;
  actualEvaluationAtSeconds: number;
  blueDecision: MidGameMacroDecisionData | null;
  redDecision: MidGameMacroDecisionData | null;
  bluePreviousPlan: TeamMacroPlan | null;
  redPreviousPlan: TeamMacroPlan | null;
  bluePreviousPlanEndReason: MacroPlanEndReason | null;
  redPreviousPlanEndReason: MacroPlanEndReason | null;
  blueNextEvaluationAtSeconds: number;
  redNextEvaluationAtSeconds: number;
  evaluationSkippedReason: string | null;
  selectionRandomConsumptionCount: number;
}

export interface MacroPlanLifecycleData {
  teamSide: TeamSide;
  planSequence: number;
  plan: TeamMacroPlan;
  startedAtSeconds: number;
  activeUntilSeconds: number;
  endTimeSeconds: number | null;
  endReason: MacroPlanEndReason | null;
  endRecordCount: number;
  setupPlan: boolean;
}

export interface MidGameMacroSnapshot {
  enabled: boolean;
  matchPhase: MatchPhase;
  currentTimeSeconds: number;
  blueTeam: TeamMacroSnapshot;
  redTeam: TeamMacroSnapshot;
  dragonMacroSetupControl: number;
  baronMacroSetupControl: number;
  evaluationHistory: MidGameMacroEvaluationData[];
  planLifecycleHistory: MacroPlanLifecycleData[];
  matchEnded: boolean;
}

export interface MidGameMacroDecisionData {
  evaluationTimeSeconds: number;
  teamSide: TeamSide;
  featureEnabled: boolean;
  candidates: MacroPlanWeightBreakdown[];
  selectedPlan: TeamMacroPlan;
  targetLane: Lane | null;
  targetObjective: ObjectiveType | null;
  assignedPositions: string[];
  selectionRollExecuted: boolean;
  selectionRoll: number | null;
  startedAtSeconds: number;
  activeUntilSeconds: number;
}

export interface MidGameMacroActionData {
  teamSide: TeamSide;
  plan: TeamMacroPlan;
  actionType: MacroActionType;
  result: MacroActionResult;
  targetLane: Lane | null;
  targetObjective: ObjectiveType | null;
  participants: string[];
  targetTowerTier: TowerTier | null;
  existingBaseChance: number;
  goldBonus: number;
  aliveBonus: number;
  attributeBonus: number;
  baronBonus: number;
  finalPushChance: number;
  pushRollExecuted: boolean;
  pushSucceeded: boolean;
  structureKind: StructureKind | null;
  signedSetupControl: number;
  setupActiveUntilSeconds: number;
  farmBlockSeconds: number;
}

export type ObjectiveDecisionAction = 'TAKE' | 'CONTEST' | 'GIVE' | 'TRADE_STRUCTURE' | 'RESET';
export type ObjectiveDecisionRole = 'INITIATOR' | 'RESPONDER';
export type ObjectiveDecisionResult = 'NOT_EVALUATED' | 'INITIATOR_RESET' | 'UNCONTESTED_CAPTURE' | 'CONTEST_FIGHT' | 'TRADE_ATTEMPTED' | 'TRADE_SUCCEEDED' | 'TRADE_FAILED' | 'STALE_OBJECTIVE' | 'INELIGIBLE';

export interface ObjectiveDecisionWeightBreakdown {
  action: ObjectiveDecisionAction;
  role: ObjectiveDecisionRole;
  eligible: boolean;
  reason: string | null;
  baseWeight: number;
  priorityEdge: number;
  priorityContribution: number;
  aliveEdge: number;
  aliveContribution: number;
  goldEdge: number;
  goldContribution: number;
  teamfightEdge: number;
  teamfightContribution: number;
  farmingEdge: number;
  farmingContribution: number;
  urgencyContribution: number;
  missingPlayerContribution: number;
  tradeAvailabilityContribution: number;
  finalWeight: number;
}

export interface ObjectiveDecisionData {
  decisionSequence: number;
  evaluationTimeSeconds: number;
  objectiveType: ObjectiveType;
  featureEnabled: boolean;
  initiativeSide: TeamSide;
  responderSide: TeamSide;
  initiativeCandidates: ObjectiveDecisionWeightBreakdown[];
  initiativeAction: ObjectiveDecisionAction;
  initiativeSelectionRollExecuted: boolean;
  initiativeSelectionRoll: number | null;
  responderCandidates: ObjectiveDecisionWeightBreakdown[];
  responderAction: ObjectiveDecisionAction | null;
  responderSelectionRollExecuted: boolean;
  responderSelectionRoll: number | null;
  tradeTargetLane: Lane | null;
  tradeTargetStructure: TowerTier | null;
  tradePushChance: number;
  tradeRollExecuted: boolean;
  tradeSucceeded: boolean;
  contestedFight: boolean;
  fightWinner: TeamSide | null;
  captureSide: TeamSide | null;
  result: ObjectiveDecisionResult;
  majorCombatConsumed: boolean;
  structureActionConsumed: boolean;
  nextGeneralAttemptAtSeconds: number;
  postFightPath: boolean;
  elderPriorityAvailable: boolean;
}

export interface ObjectiveDecisionSnapshot {
  enabled: boolean;
  latestOverall: ObjectiveDecisionData | null;
  latestDragon: ObjectiveDecisionData | null;
  latestBaron: ObjectiveDecisionData | null;
  latestElder: ObjectiveDecisionData | null;
  latestBlue: ObjectiveDecisionData | null;
  latestRed: ObjectiveDecisionData | null;
}

export type LateGameAttackPlan = 'SIEGE_TOP' | 'SIEGE_MID' | 'SIEGE_BOT' | 'NEXUS_FINISH' | 'RESET_AND_REGROUP';
export type LateGameDefenseResponse = 'DEFEND' | 'GIVE_STRUCTURE' | 'CROSS_MAP_PUSH';
export type LateGameActionResult = 'NOT_EVALUATED' | 'NO_INITIATIVE' | 'ATTACKER_RESET' | 'SIEGE_REPELLED' | 'STRUCTURE_DESTROYED' | 'SIEGE_FIGHT_ATTACKER_WIN' | 'SIEGE_FIGHT_DEFENDER_WIN' | 'CROSS_MAP_SUCCEEDED' | 'CROSS_MAP_FAILED' | 'NEXUS_FINISH_ADVANCED' | 'NEXUS_DESTROYED' | 'STALE_TARGET' | 'INELIGIBLE' | 'GAME_FINISHED';
export type LateGameTransitionReason = 'TIME_LIMIT' | 'INHIBITOR_TOWER_DESTROYED' | 'INHIBITOR_DESTROYED';
export type BaseThreatLevel = 'NONE' | 'INHIBITOR_TOWER_THREAT' | 'INHIBITOR_THREAT' | 'NEXUS_TURRET_THREAT' | 'NEXUS_THREAT' | 'MATCH_ENDED';
export type LateGameStructureTarget = 'OUTER' | 'INNER' | 'INHIBITOR_TOWER' | 'INHIBITOR' | 'NEXUS_TURRET' | 'NEXUS';
export type LateGamePlanStatus = 'DISABLED' | 'NOT_STARTED' | 'WAITING_FOR_EVALUATION' | 'ACTIVE' | 'EXPIRED' | 'CANCELLED' | 'MATCH_ENDED';
export interface BaseThreatSnapshot { defendingSide: TeamSide; overallLevel: BaseThreatLevel; threatenedLanes: Lane[]; deepestThreatLane: Lane | null; nextThreatenedStructure: LateGameStructureTarget | null; destroyedInhibitorCount: number; remainingNexusTurrets: number; nexusExposed: boolean; nexusAlive: boolean; }
export interface LateGameRespawnSummary { deadCount: number; respawningSoonCount: number; longRespawnCount: number; longestRespawnSeconds: number; }
export interface LateGameTeamPlanSnapshot { role: string; attackPlan: LateGameAttackPlan | null; defenseResponse: LateGameDefenseResponse | null; targetLane: Lane | null; targetStructure: LateGameStructureTarget | null; assignedPositions: Position[]; startedAtSeconds: number; activeUntilSeconds: number; status: LateGamePlanStatus; lastResult: LateGameActionResult; endReason: string | null; }
export interface LateGameDecisionData { sequence: number; dueTimeSeconds: number; actualEvaluationTimeSeconds: number; initiativeSide: TeamSide | null; selectedAttackPlan: LateGameAttackPlan | null; targetLane: Lane | null; targetStructure: LateGameStructureTarget | null; assignedPositions: Position[]; selectedDefenseResponse: LateGameDefenseResponse | null; attackerAliveCount: number; defenderAliveCount: number; defenderRespawnSummary: LateGameRespawnSummary; siegeFightTriggered: boolean; fightGrade: string | null; fightWinner: TeamSide | null; structureSucceeded: boolean; crossMapTargetLane: Lane | null; crossMapTargetStructure: LateGameStructureTarget | null; crossMapSucceeded: boolean; result: LateGameActionResult; nextEvaluationAtSeconds: number; }
export interface LateGameSnapshot { enabled: boolean; matchPhase: MatchPhase; lateGameStartedAtSeconds: number; transitionReason: LateGameTransitionReason | null; nextEvaluationAtSeconds: number; latestDecision: LateGameDecisionData | null; blueBaseThreat: BaseThreatSnapshot; redBaseThreat: BaseThreatSnapshot; bluePlan: LateGameTeamPlanSnapshot; redPlan: LateGameTeamPlanSnapshot; structureActionStats?: { structureAttempted: number; structureMutationPerformed: number; laterResolverBlockedByAttempt: number; sameSideMultipleAttemptError: number; sameSideMultipleMutationError: number; postFightMultiStructureActions: number; postFightMultiStructureMutationCount: number; postFightInternalBlockError: number; }; }
