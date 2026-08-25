export type MatchDataSource = 'LIVE' | 'REFERENCE';
export type TeamSide = 'BLUE' | 'RED';
export type Position = 'TOP' | 'JUNGLE' | 'MID' | 'ADC' | 'SUPPORT';
export type Lane = 'TOP' | 'MID' | 'BOT';
export type DraftActionType = 'PICK' | 'BAN';
export type GameEndReason = 'NEXUS_DESTROYED' | 'SIMULATION_TIMEOUT';
export type MatchEventType =
  | 'GAME_START' | 'KILL' | 'ASSIST' | 'JUNGLE_GANK' | 'COUNTER_GANK' | 'LANE_COMBAT'
  | 'ROAM' | 'SHUTDOWN' | 'DRAGON' | 'BARON' | 'ELDER' | 'TOWER' | 'TEAMFIGHT'
  | 'STRUCTURE_ACTION'
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
export type PlayerActivityType = 'DEFAULT_ROLE' | 'ROAMING' | 'SIEGING';

export type AbilityRatingKey =
  | 'ALLY_PROTECTION' | 'AREA_SETUP' | 'COMBAT_EXECUTION' | 'CONSISTENCY' | 'DECISION_MAKING'
  | 'ENEMY_JUNGLE_TRACKING' | 'ENGAGE_EXECUTION' | 'FARMING' | 'JUNGLE_RESOURCE_MANAGEMENT'
  | 'LANE_INTERVENTION' | 'LANE_PRESSURE' | 'LANE_SUPPORT' | 'MAP_AWARENESS' | 'MECHANICS'
  | 'OBJECTIVE_DECISION' | 'OBJECTIVE_SECURE' | 'PATHING' | 'POSITIONING' | 'PRIORITY_CONVERSION'
  | 'ROTATION_PLANNING' | 'SIDE_LANE' | 'TRADING' | 'VISION_CONTROL' | 'WAVE_MANAGEMENT';

export interface PlayerAbilityProfileViewModel {
  schemaVersion: 'PLAYER_ABILITY_PROFILE_V1';
  baseRatings: Readonly<Record<AbilityRatingKey, number>>;
  realizedRatings: Readonly<Record<AbilityRatingKey, number>>;
  realizationDeltas: Readonly<Record<AbilityRatingKey, number>>;
  selectedChampionProficiency: number;
  proficiencyExecutionAdjustment: number;
}
