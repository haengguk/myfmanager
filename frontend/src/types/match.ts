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
  type: string;
  message: string;
  killer: string | null;
  victim: string | null;
  assists: string[];
  goldAmount: number;
  combatSource: 'COUNTER_GANK' | 'JUNGLE_GANK' | 'LANE_COMBAT' | 'SKIRMISH' | 'TEAMFIGHT' | 'OBJECTIVE_FIGHT' | 'OTHER' | null;
  laneCombat: LaneCombatData | null;
  jungleGank: JungleGankData | null;
  counterGank: CounterGankData | null;
}

export interface JungleGankData {
  gankingSide: 'BLUE' | 'RED';
  junglerPlayerId: string;
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
}

export interface LaneSnapshot {
  lane: 'TOP' | 'MID' | 'BOT';
  pressure: number;
  priority: 'BLUE' | 'NEUTRAL' | 'RED';
}

export interface PlayerSnapshot {
  playerName: string;
  teamName: string;
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
  hasShutdownBounty: boolean;
  totalShutdownGoldEarned: number;
  totalShutdownGoldGiven: number;
  bountyProgress: number;
}
