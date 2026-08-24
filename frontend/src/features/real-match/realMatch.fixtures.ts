import type {
  CombatSource,
  DraftViewModel,
  LiveChampionStateViewModel,
  MatchSnapshotViewModel,
  PlaybackEventViewModel,
  PlaybackEventType,
  PlaybackViewModel,
  Position,
  PositionComparisonViewModel,
  TeamSide,
} from './realMatch.types';
import { realMatchChampions } from './realMatch.champions.fixture';

export { realMatchChampions } from './realMatch.champions.fixture';

const teams = {
  BLUE: { side: 'BLUE', code: 'GEN', record: 'Spring Split · 12승 3패', seriesScore: 1 },
  RED: { side: 'RED', code: 'T1', record: 'Spring Split · 10승 5패', seriesScore: 0 },
} as const;

export const draftFixture: DraftViewModel = {
  matchId: 'spring-2026-gen-t1-g2',
  simulationSeed: 73,
  seasonLabel: '2026년 8월 24일 · Spring Split',
  gameNumber: 2,
  seriesType: 'BO3',
  initialSeconds: 30,
  teams,
  champions: realMatchChampions,
  rosters: {
    BLUE: [
      ['gen-kiin', 'Kiin', 'TOP', null], ['gen-canyon', 'Canyon', 'JUNGLE', null],
      ['gen-chovy', 'Chovy', 'MID', null], ['gen-ruler', 'Ruler', 'ADC', null], ['gen-duro', 'Duro', 'SUPPORT', null],
    ].map(([playerId, playerName, position, championId]) => ({ playerId, playerName, position, championId })) as DraftViewModel['rosters']['BLUE'],
    RED: [
      ['t1-doran', 'Doran', 'TOP', null], ['t1-oner', 'Oner', 'JUNGLE', null],
      ['t1-faker', 'Faker', 'MID', null], ['t1-gumayusi', 'Gumayusi', 'ADC', null], ['t1-keria', 'Keria', 'SUPPORT', null],
    ].map(([playerId, playerName, position, championId]) => ({ playerId, playerName, position, championId })) as DraftViewModel['rosters']['RED'],
  },
  bans: { BLUE: [], RED: [] },
  fearlessChampionIds: ['ksante', 'taliyah', 'kaisa'],
  turnQueue: [
    { id: 'ban-1-blue', phase: 'BAN', side: 'BLUE', position: null, round: 1, order: 1 },
    { id: 'ban-1-red', phase: 'BAN', side: 'RED', position: null, round: 1, order: 2 },
    { id: 'ban-2-blue', phase: 'BAN', side: 'BLUE', position: null, round: 1, order: 3 },
    { id: 'ban-2-red', phase: 'BAN', side: 'RED', position: null, round: 1, order: 4 },
    { id: 'ban-3-blue', phase: 'BAN', side: 'BLUE', position: null, round: 1, order: 5 },
    { id: 'ban-3-red', phase: 'BAN', side: 'RED', position: null, round: 1, order: 6 },
    { id: 'pick-1-blue', phase: 'PICK', side: 'BLUE', position: 'TOP', round: 1, order: 7 },
    { id: 'pick-1-red', phase: 'PICK', side: 'RED', position: 'TOP', round: 1, order: 8 },
    { id: 'pick-2-red', phase: 'PICK', side: 'RED', position: 'JUNGLE', round: 1, order: 9 },
    { id: 'pick-2-blue', phase: 'PICK', side: 'BLUE', position: 'JUNGLE', round: 1, order: 10 },
    { id: 'pick-3-blue', phase: 'PICK', side: 'BLUE', position: 'MID', round: 1, order: 11 },
    { id: 'pick-3-red', phase: 'PICK', side: 'RED', position: 'MID', round: 1, order: 12 },
    { id: 'ban-4-red', phase: 'BAN', side: 'RED', position: null, round: 2, order: 13 },
    { id: 'ban-4-blue', phase: 'BAN', side: 'BLUE', position: null, round: 2, order: 14 },
    { id: 'ban-5-red', phase: 'BAN', side: 'RED', position: null, round: 2, order: 15 },
    { id: 'ban-5-blue', phase: 'BAN', side: 'BLUE', position: null, round: 2, order: 16 },
    { id: 'pick-4-red', phase: 'PICK', side: 'RED', position: 'ADC', round: 2, order: 17 },
    { id: 'pick-4-blue', phase: 'PICK', side: 'BLUE', position: 'ADC', round: 2, order: 18 },
    { id: 'pick-5-blue', phase: 'PICK', side: 'BLUE', position: 'SUPPORT', round: 2, order: 19 },
    { id: 'pick-5-red', phase: 'PICK', side: 'RED', position: 'SUPPORT', round: 2, order: 20 },
  ],
};

const playerLineups: Record<TeamSide, readonly [string, string, string, Position, number][]> = {
  BLUE: [
    ['gen-kiin', 'Kiin', 'renekton', 'TOP', 16], ['gen-canyon', 'Canyon', 'nidalee', 'JUNGLE', 15],
    ['gen-chovy', 'Chovy', 'ahri', 'MID', 17], ['gen-ruler', 'Ruler', 'aphelios', 'ADC', 16],
    ['gen-duro', 'Duro', 'nautilus', 'SUPPORT', 13],
  ],
  RED: [
    ['t1-doran', 'Doran', 'aatrox', 'TOP', 15], ['t1-oner', 'Oner', 'lee-sin', 'JUNGLE', 14],
    ['t1-faker', 'Faker', 'orianna', 'MID', 16], ['t1-gumayusi', 'Gumayusi', 'varus', 'ADC', 15],
    ['t1-keria', 'Keria', 'rakan', 'SUPPORT', 13],
  ],
};

function liveTeam(side: TeamSide, dead: Readonly<Record<string, number>> = {}, levels?: readonly number[]): readonly LiveChampionStateViewModel[] {
  return playerLineups[side].map(([playerId, playerName, championId, position, level], index) => ({
    playerId, playerName, championId, position, level: levels?.[index] ?? level,
    alive: dead[playerId] === undefined,
    respawnSeconds: dead[playerId] ?? 0,
  }));
}

function snapshot(atSeconds: number, blueKills: number, blueGold: number, redKills: number, redGold: number, dead: Partial<Record<TeamSide, Readonly<Record<string, number>>>> = {}, levels?: Partial<Record<TeamSide, readonly number[]>>): MatchSnapshotViewModel {
  return {
    atSeconds,
    teams: {
      BLUE: { side: 'BLUE', kills: blueKills, gold: blueGold, champions: liveTeam('BLUE', dead.BLUE, levels?.BLUE) },
      RED: { side: 'RED', kills: redKills, gold: redGold, champions: liveTeam('RED', dead.RED, levels?.RED) },
    },
  };
}

const eventSeed: readonly [number, PlaybackEventType, string, boolean, CombatSource, string | null, string | null][] = [
  [0, 'MATCH_START', '경기가 시작되었습니다.', false, null, null, null],
  [82, 'ROAM', 'Faker가 상단 강가 시야를 확보했습니다.', false, null, null, null],
  [134, 'GANK_ATTEMPT', 'Oner가 TOP 갱킹을 시도했지만 킬 없이 종료되었습니다.', false, 'JUNGLE_GANK', 'gank-134', null],
  [201, 'GOLD_LEAD', 'GEN이 라인 단계에서 600 골드 앞서기 시작했습니다.', false, null, null, null],
  [264, 'KILL', 'Canyon이 Oner를 처치했습니다.', false, 'JUNGLE_GANK', 'gank-264', null],
  [264, 'ASSIST', 'Chovy가 첫 킬에 도움을 기록했습니다.', false, 'JUNGLE_GANK', 'gank-264', null],
  [338, 'PHASE_CHANGE', 'GEN이 공허 유충 지역의 시야를 장악했습니다.', false, null, null, null],
  [371, 'OBJECTIVE', 'GEN이 첫 번째 공허 유충 무리를 확보했습니다.', false, null, null, null],
  [430, 'ROAM', 'Keria가 MID로 로밍해 압박을 만들었습니다.', false, null, null, null],
  [512, 'TOWER', 'T1이 바텀 포탑 방패 3개를 획득했습니다.', false, null, null, null],
  [588, 'GANK_ATTEMPT', 'Canyon과 Chovy가 MID에서 Faker를 압박했습니다.', false, 'JUNGLE_GANK', 'gank-588', null],
  [645, 'KILL', 'Ruler가 Gumayusi를 처치했습니다.', false, 'LANE_COMBAT', 'lane-645', null],
  [720, 'KILL', 'Chovy가 Faker를 처치했습니다.', false, 'LANE_COMBAT', 'lane-720', null],
  [782, 'GOLD_LEAD', 'GEN의 골드 격차가 +2.4K로 벌어졌습니다.', false, null, null, null],
  [872, 'OBJECTIVE', 'GEN이 첫 번째 드래곤을 처치했습니다.', false, null, null, null],
  [905, 'PHASE_CHANGE', '라인 단계가 종료되고 중반 운영이 시작되었습니다.', false, null, null, null],
  [1038, 'ROAM', 'Duro가 상단 강가로 이동했습니다.', false, null, null, null],
  [1085, 'TOWER', 'T1이 미드 1차 포탑을 파괴했습니다.', false, null, null, null],
  [1144, 'TEAMFIGHT', '드래곤 앞에서 첫 대규모 한타가 시작되었습니다.', true, 'OBJECTIVE_FIGHT', 'fight-1144', null],
  [1162, 'KILL', 'GEN이 한타에서 2명을 처치했습니다.', false, 'OBJECTIVE_FIGHT', 'fight-1144', 'fight-1144'],
  [1218, 'GOLD_LEAD', 'GEN이 총 골드 4.1K 차이를 만들었습니다.', false, null, null, null],
  [1324, 'INHIBITOR', 'GEN이 미드 억제기 포탑을 압박했습니다.', false, null, null, null],
  [1390, 'OBJECTIVE', '양 팀이 바론 시야를 두고 대치했습니다.', false, null, null, null],
  [1481, 'OBJECTIVE', 'GEN이 바론 처치를 시작했습니다.', false, null, null, null],
  [1508, 'OBJECTIVE', 'GEN이 바론을 처치했습니다.', false, null, null, null],
  [1563, 'TOWER', 'GEN이 탑 2차 포탑을 파괴했습니다.', false, null, null, null],
  [1650, 'KILL', 'Faker가 Chovy를 처치했습니다.', false, 'LANE_COMBAT', 'lane-1650', null],
  [1718, 'OBJECTIVE', 'T1이 세 번째 드래곤을 처치했습니다.', false, null, null, null],
  [1802, 'PHASE_CHANGE', '장로 드래곤 생성까지 2분 남았습니다.', false, null, null, null],
  [1860, 'TEAMFIGHT', 'T1이 미드 강가에서 한타를 열었습니다.', true, 'TEAMFIGHT', 'fight-1860', null],
  [1882, 'KILL', 'T1이 한타에서 3명을 처치했습니다.', false, 'TEAMFIGHT', 'fight-1860', 'fight-1860'],
  [1944, 'GOLD_LEAD', '골드 격차가 GEN +3.2K로 좁혀졌습니다.', false, null, null, null],
  [2038, 'OBJECTIVE', 'GEN이 두 번째 바론을 처치했습니다.', false, null, null, null],
  [2115, 'INHIBITOR', 'GEN이 바텀 억제기를 파괴했습니다.', false, null, null, null],
  [2201, 'TEAMFIGHT', '마지막 한타에서 GEN이 4명을 처치했습니다.', true, 'TEAMFIGHT', 'fight-2201', null],
  [2248, 'MATCH_END', 'GEN이 넥서스를 파괴했습니다.', true, null, null, null],
  [2260, 'MATCH_END', '경기가 종료되었습니다.', false, null, null, null],
];

type EventContext = Partial<Pick<PlaybackEventViewModel, 'teamSide' | 'killerPlayerId' | 'victimPlayerId' | 'assistantPlayerIds' | 'objective' | 'lane' | 'participants'>>;
const eventContextByIndex: Readonly<Record<number, EventContext>> = {
  2: { teamSide: 'RED', participants: [{ playerId: 't1-faker', teamSide: 'RED', role: 'PARTICIPANT' }] },
  3: { teamSide: 'RED', lane: 'TOP', participants: [{ playerId: 't1-oner', teamSide: 'RED', role: 'PARTICIPANT' }, { playerId: 'gen-kiin', teamSide: 'BLUE', role: 'PARTICIPANT' }] },
  5: { teamSide: 'BLUE', killerPlayerId: 'gen-canyon', victimPlayerId: 't1-oner', assistantPlayerIds: ['gen-chovy'], lane: 'MID', participants: [{ playerId: 'gen-canyon', teamSide: 'BLUE', role: 'KILLER' }, { playerId: 't1-oner', teamSide: 'RED', role: 'VICTIM' }, { playerId: 'gen-chovy', teamSide: 'BLUE', role: 'ASSISTANT' }] },
  6: { teamSide: 'BLUE', killerPlayerId: 'gen-canyon', victimPlayerId: 't1-oner', assistantPlayerIds: ['gen-chovy'], participants: [{ playerId: 'gen-chovy', teamSide: 'BLUE', role: 'ASSISTANT' }] },
  8: { teamSide: 'BLUE', objective: 'VOID_GRUBS' },
  10: { teamSide: 'RED', objective: 'TOWER', lane: 'BOT' },
  11: { teamSide: 'BLUE', lane: 'MID', participants: [{ playerId: 'gen-canyon', teamSide: 'BLUE', role: 'PARTICIPANT' }, { playerId: 'gen-chovy', teamSide: 'BLUE', role: 'PARTICIPANT' }, { playerId: 't1-faker', teamSide: 'RED', role: 'PARTICIPANT' }] },
  12: { teamSide: 'BLUE', killerPlayerId: 'gen-ruler', victimPlayerId: 't1-gumayusi', lane: 'BOT', participants: [{ playerId: 'gen-ruler', teamSide: 'BLUE', role: 'KILLER' }, { playerId: 't1-gumayusi', teamSide: 'RED', role: 'VICTIM' }] },
  13: { teamSide: 'BLUE', killerPlayerId: 'gen-chovy', victimPlayerId: 't1-faker', lane: 'MID', participants: [{ playerId: 'gen-chovy', teamSide: 'BLUE', role: 'KILLER' }, { playerId: 't1-faker', teamSide: 'RED', role: 'VICTIM' }] },
  15: { teamSide: 'BLUE', objective: 'DRAGON' },
  18: { teamSide: 'RED', objective: 'TOWER', lane: 'MID' },
  19: { teamSide: 'BLUE', objective: 'DRAGON', lane: 'BOT', participants: [...playerLineups.BLUE.slice(1).map(([playerId]) => ({ playerId, teamSide: 'BLUE' as const, role: 'PARTICIPANT' as const })), ...playerLineups.RED.slice(1).map(([playerId]) => ({ playerId, teamSide: 'RED' as const, role: 'PARTICIPANT' as const }))] },
  20: { teamSide: 'BLUE', objective: 'DRAGON' },
  22: { teamSide: 'BLUE', objective: 'INHIBITOR', lane: 'MID' },
  23: { objective: 'BARON' },
  24: { teamSide: 'BLUE', objective: 'BARON' },
  25: { teamSide: 'BLUE', objective: 'BARON' },
  26: { teamSide: 'BLUE', objective: 'TOWER', lane: 'TOP' },
  27: { teamSide: 'RED', killerPlayerId: 't1-faker', victimPlayerId: 'gen-chovy', lane: 'MID', participants: [{ playerId: 't1-faker', teamSide: 'RED', role: 'KILLER' }, { playerId: 'gen-chovy', teamSide: 'BLUE', role: 'VICTIM' }] },
  28: { teamSide: 'RED', objective: 'DRAGON' },
  30: { teamSide: 'RED', lane: 'MID', participants: [...playerLineups.BLUE.map(([playerId]) => ({ playerId, teamSide: 'BLUE' as const, role: 'PARTICIPANT' as const })), ...playerLineups.RED.map(([playerId]) => ({ playerId, teamSide: 'RED' as const, role: 'PARTICIPANT' as const }))] },
  31: { teamSide: 'RED', lane: 'MID' },
  33: { teamSide: 'BLUE', objective: 'BARON' },
  34: { teamSide: 'BLUE', objective: 'INHIBITOR', lane: 'BOT' },
  35: { teamSide: 'BLUE', participants: [...playerLineups.BLUE.map(([playerId]) => ({ playerId, teamSide: 'BLUE' as const, role: 'PARTICIPANT' as const })), ...playerLineups.RED.map(([playerId]) => ({ playerId, teamSide: 'RED' as const, role: 'PARTICIPANT' as const }))] },
  36: { teamSide: 'BLUE', objective: 'NEXUS' },
};

const playbackEvents: readonly PlaybackEventViewModel[] = eventSeed.map(([occurredAtSeconds, eventType, description, isMajor, combatSource, engagementId, summaryOfEngagementId], index) => ({
  id: `event-${index + 1}`,
  occurredAtSeconds,
  eventType,
  teamSide: eventContextByIndex[index + 1]?.teamSide ?? null,
  combatSource,
  engagementId,
  summaryOfEngagementId,
  engagementRole: engagementId ? summaryOfEngagementId ? 'DETAIL' : 'SUMMARY' : null,
  killerPlayerId: eventContextByIndex[index + 1]?.killerPlayerId ?? null,
  victimPlayerId: eventContextByIndex[index + 1]?.victimPlayerId ?? null,
  assistantPlayerIds: eventContextByIndex[index + 1]?.assistantPlayerIds ?? [],
  objective: eventContextByIndex[index + 1]?.objective ?? null,
  lane: eventContextByIndex[index + 1]?.lane ?? null,
  participants: eventContextByIndex[index + 1]?.participants ?? [],
  description,
  isMajor,
}));

const comparison: readonly PositionComparisonViewModel[] = [
  ['TOP', ['gen-kiin', 'Kiin', 'renekton', 3, 1, 5, 268, 8.72, 16], ['t1-doran', 'Doran', 'aatrox', 1, 3, 2, 251, 7.90, 15]],
  ['JUNGLE', ['gen-canyon', 'Canyon', 'nidalee', 2, 0, 8, 221, 8.41, 15], ['t1-oner', 'Oner', 'lee-sin', 0, 4, 3, 184, 7.31, 14]],
  ['MID', ['gen-chovy', 'Chovy', 'ahri', 4, 1, 6, 302, 9.24, 17], ['t1-faker', 'Faker', 'orianna', 2, 2, 1, 276, 8.60, 16]],
  ['ADC', ['gen-ruler', 'Ruler', 'aphelios', 3, 0, 4, 318, 9.62, 16], ['t1-gumayusi', 'Gumayusi', 'varus', 1, 2, 2, 291, 8.70, 15]],
  ['SUPPORT', ['gen-duro', 'Duro', 'nautilus', 0, 2, 9, 44, 5.00, 13], ['t1-keria', 'Keria', 'rakan', 0, 1, 4, 41, 5.00, 13]],
].map(([position, blueSeed, redSeed]) => {
  const toPlayer = (seed: readonly unknown[]) => ({
    playerId: seed[0] as string, playerName: seed[1] as string, championId: seed[2] as string, position: position as Position,
    kills: seed[3] as number, deaths: seed[4] as number, assists: seed[5] as number, cs: seed[6] as number, gold: seed[7] as number, level: seed[8] as number,
  });
  return { position: position as Position, blue: toPlayer(blueSeed as readonly unknown[]), red: toPlayer(redSeed as readonly unknown[]) };
});

export const playbackFixture: PlaybackViewModel = {
  matchId: 'spring-2026-gen-t1-g2', simulationSeed: 73, seasonLabel: '2026년 8월 24일 · Spring Split', gameNumber: 2, seriesType: 'BO3',
  durationSeconds: 2260, initialSeconds: 0, comparisonReferenceSeconds: 1508, teams,
  championsById: Object.fromEntries(realMatchChampions.map((champion) => [champion.id, champion])),
  events: playbackEvents,
  snapshots: [
    snapshot(0, 0, 2.5, 0, 2.5, {}, { BLUE: [1, 1, 1, 1, 1], RED: [1, 1, 1, 1, 1] }),
    snapshot(872, 4, 24.6, 1, 22.2, {}, { BLUE: [9, 8, 10, 9, 7], RED: [8, 8, 9, 8, 7] }),
    snapshot(1508, 12, 40.9, 4, 34.3, { RED: { 't1-oner': 22, 't1-faker': 26 } }),
    snapshot(1882, 12, 49.6, 8, 46.4, { BLUE: { 'gen-chovy': 15 }, RED: { 't1-doran': 20, 't1-oner': 25, 't1-faker': 30 } }),
    snapshot(2260, 16, 68.4, 10, 59.8),
  ],
  comparisonAtInitialTime: comparison,
  finalScore: { BLUE: 16, RED: 10 }, winner: 'BLUE',
};
