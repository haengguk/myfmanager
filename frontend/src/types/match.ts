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
  respawnRemainingSeconds: number;
  hasElderBuff: boolean;
  elderBuffRemainingSeconds: number;
  shutdownBountyGold: number;
  hasShutdownBounty: boolean;
  totalShutdownGoldEarned: number;
  totalShutdownGoldGiven: number;
  bountyProgress: number;
}
