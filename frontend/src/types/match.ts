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
  structureActionSource?: StructureActionSource | null;
  structureKind?: StructureKind | null;
  structureTowerTier?: TowerTier | null;
  structureLane?: Lane | null;
  structureAttackingSide?: TeamSide | null;
  structureDefendingSide?: TeamSide | null;
  outerTurretSiege?: OuterTurretSiegeData | null;
  matchPhaseChange?: MatchPhaseChangeData | null;
}


export type ObjectiveType = 'DRAGON' | 'BARON' | 'ELDER';
export type TeamSide = 'BLUE' | 'RED';
export type Lane = 'TOP' | 'MID' | 'BOT';
export type MatchPhase = 'LANING' | 'MID_GAME';
export type LanePhase = 'LANING' | 'OPEN';
export type MidGameTransitionReason = 'TIME_LIMIT' | 'ALL_LANES_OPEN';
export type StructureActionSource = 'LANE_PRESSURE' | 'POST_FIGHT' | 'BARON_PRESSURE' | 'MACRO_PLAY';
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
