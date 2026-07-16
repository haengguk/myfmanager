export interface MatchSimulateResponse {
  seed: number;
  timeline: MatchTimeline;
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
  combatSource: 'COUNTER_GANK' | 'JUNGLE_GANK' | 'ROAM' | 'LANE_COMBAT' | 'SKIRMISH' | 'TEAMFIGHT' | 'OBJECTIVE_FIGHT' | 'OTHER' | null;
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
}


export type ObjectiveType = 'DRAGON' | 'BARON' | 'ELDER';
export type TeamSide = 'BLUE' | 'RED';
export type Lane = 'TOP' | 'MID' | 'BOT';
export type MatchPhase = 'LANING' | 'MID_GAME';
export type LanePhase = 'LANING' | 'OPEN';
export type MidGameTransitionReason = 'TIME_LIMIT' | 'ALL_LANES_OPEN';
export type StructureActionSource = 'LANE_PRESSURE' | 'POST_FIGHT' | 'BARON_PRESSURE' | 'MACRO_PLAY' | 'MID_GAME_MACRO' | 'OBJECTIVE_TRADE';
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
}

export interface LaneSnapshot {
  lane: 'TOP' | 'MID' | 'BOT';
  pressure: number;
  priority: 'BLUE' | 'NEUTRAL' | 'RED';
}

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
}

export type TeamMacroPlan = 'GROUP_MID' | 'SIDE_LANE_TOP' | 'SIDE_LANE_BOT' | 'OBJECTIVE_SETUP_DRAGON' | 'OBJECTIVE_SETUP_BARON' | 'RESET_AND_FARM';
export type MacroActionType = 'STRUCTURE_PUSH' | 'OBJECTIVE_SETUP' | 'RESET';
export type MacroActionResult = 'NOT_ATTEMPTED' | 'INELIGIBLE' | 'PUSH_FAILED' | 'STRUCTURE_DESTROYED' | 'SETUP_STARTED' | 'RESET_STARTED';
export type MacroPlanStatus = 'DISABLED' | 'NOT_STARTED' | 'WAITING_FOR_EVALUATION' | 'ACTIVE' | 'EXPIRED' | 'CANCELLED' | 'MATCH_ENDED';
export type MacroPlanEndReason = 'EXPIRED' | 'REPLACED' | 'OBJECTIVE_CAPTURED' | 'OBJECTIVE_UNAVAILABLE' | 'FEATURE_DISABLED' | 'MATCH_ENDED';

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
