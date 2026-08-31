import type {
  FinalPlayerComparisonViewModel, FinalPlayerViewModel, MatchResultViewModel, MatchSessionPerformance,
  MatchSessionViewModel, MatchSetupOptionsViewModel, MatchSetupSelection, MatchTeamOptionViewModel,
  TeamFinalStatsViewModel,
} from './matchSession.types';
import type {
  RealMatchEventDto, RealMatchOptionsDto, RealMatchPlayerResultDto, RealMatchResponseDto,
  RealMatchResultDto, RealMatchTeamPresentationDto, RealMatchTeamResultDto, RealMatchTimelineDto,
} from './api/realMatchApi.types';
import type {
  ChampionViewModel, DraftViewModel, MatchSnapshotViewModel, PlaybackEventViewModel, PlaybackViewModel,
  Position, StructureActionPhase, StructureActionViewModel, TeamSide, TeamViewModel,
} from './realMatch.types';

const POSITIONS: readonly Position[] = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];
const MAJOR_EVENT_TYPES = new Set([
  'KILL', 'JUNGLE_GANK', 'COUNTER_GANK', 'LANE_COMBAT', 'DRAGON', 'BARON', 'ELDER',
  'TOWER', 'TEAMFIGHT', 'TEAMFIGHT_RESULT', 'ACE', 'GAME_END',
]);
const HIDDEN_LOG_EVENT_TYPES = new Set([
  'ASSIST', 'MATCH_PHASE_CHANGE', 'LEVEL_UP', 'ITEM_STAGE_REACHED',
]);

export interface CommonLiveMatchSource {
  matchIdentity: string; seed: string;
  teams: readonly RealMatchTeamPresentationDto[];
  draft: {
    seriesGameNumber: number; decisions: readonly { turn: number; teamSide: TeamSide; actionType: 'BAN' | 'PICK'; championId: string }[];
    blueBans: readonly string[]; redBans: readonly string[]; bluePicks: readonly string[]; redPicks: readonly string[];
    draftRuleSetIdentity: string; finalDraftHash: string; finalAssignmentHash: string;
  };
  result: RealMatchResultDto; timeline: RealMatchTimelineDto;
  integrity: {
    runtimeProfileId: string; configurationHash: string; policyHash: string; engineImplementationVersion: string;
    resourceProvenanceHash: string; replayProvenanceHash: string; simulatorTimelineHash: string;
    structuredTimelineHash: string; outputHash: string;
    randomFingerprint: { randomDrawCount: number; randomTraceHash: string };
  };
}

const STRUCTURE_SOURCE_LABELS = {
  LANE_PRESSURE: '라인 압박', POST_FIGHT: '한타 후 공성', BARON_PRESSURE: '바론 압박',
  MACRO_PLAY: '일반 운영', MID_GAME_MACRO: '미드게임 운영', OBJECTIVE_TRADE: '오브젝트 교환',
  LATE_GAME_SIEGE: '후반 공성', LATE_GAME_CROSS_MAP: '교차 맵 운영', NEXUS_FINISH: '넥서스 마무리',
} as const;

function sourceLabel(options: RealMatchOptionsDto): string {
  return `${options.productionPolicy.engineImplementationVersion} · ${options.productionPolicy.runtimeProfileId}`;
}

export function createLiveMatchSetupOptions(source: RealMatchOptionsDto): MatchSetupOptionsViewModel {
  const defaultBlueTeamCode = source.teams[0].teamCode;
  const defaultRedTeamCode = source.teams.find((team) => team.teamCode !== defaultBlueTeamCode)!.teamCode;
  return {
    source: 'LIVE',
    sourceLabel: sourceLabel(source),
    seasonLabel: 'REAL_MATCH_API_V1 · LIVE',
    gameNumber: 1,
    seriesType: '단판 · Fresh Game 1',
    draftRule: 'Professional Draft · 자동',
    defaultSeed: '73',
    defaultBlueTeamCode,
    defaultRedTeamCode,
    engineImplementationVersion: source.productionPolicy.engineImplementationVersion,
    runtimeProfile: source.productionPolicy.runtimeProfileId,
    configurationHash: source.productionPolicy.configurationHash,
    teams: source.teams.map((team): MatchTeamOptionViewModel => ({
      teamId: team.teamCode,
      code: team.teamCode,
      name: team.displayName,
      sourceLabel: 'LIVE Options API · 선발 5명',
      roster: team.lineup.map((player) => ({ playerId: player.playerId, playerName: player.nickname, position: player.position })),
    })),
  };
}

function bySide<T extends { teamSide: TeamSide }>(values: readonly T[], side: TeamSide): T {
  const value = values.find((candidate) => candidate.teamSide === side);
  if (!value) throw new Error(`${side} LIVE 데이터를 찾을 수 없습니다.`);
  return value;
}

function toTeamViewModel(team: RealMatchTeamPresentationDto, label: string): TeamViewModel {
  return { side: team.teamSide, code: team.teamCode, displayName: team.displayName, detail: `LIVE · ${label}` };
}

const RIOT_ASSET_ID_OVERRIDES: Readonly<Record<string, string>> = {
  'aurelion-sol': 'AurelionSol', belveth: 'Belveth', chogath: 'Chogath', 'dr-mundo': 'DrMundo',
  'jarvan-iv': 'JarvanIV', kaisa: 'Kaisa', khazix: 'Khazix', kogmaw: 'KogMaw', ksante: 'KSante',
  'lee-sin': 'LeeSin', leblanc: 'Leblanc', 'master-yi': 'MasterYi', 'miss-fortune': 'MissFortune',
  'nunu-willump': 'Nunu', reksai: 'RekSai', 'renata-glasc': 'Renata', 'tahm-kench': 'TahmKench',
  'twisted-fate': 'TwistedFate', velkoz: 'Velkoz', wukong: 'MonkeyKing', 'xin-zhao': 'XinZhao',
};

function riotAssetId(championId: string): string {
  return RIOT_ASSET_ID_OVERRIDES[championId]
    ?? championId.split('-').map((part) => `${part.charAt(0).toUpperCase()}${part.slice(1)}`).join('');
}

function createChampionMap(source: CommonLiveMatchSource): Readonly<Record<string, ChampionViewModel>> {
  const presented = Object.fromEntries(source.teams.flatMap((team) => team.lineup.map((player) => [
    player.championId,
    { id: player.championId, name: player.champion.displayNameKo, nameEn: player.champion.displayNameEn, portraitUrl: player.champion.portraitUrl } satisfies ChampionViewModel,
  ] as const)));
  const portraitBaseUrl = source.teams.flatMap((team) => team.lineup).find((player) => player.champion.portraitUrl)
    ?.champion.portraitUrl.replace(/[^/]+\.png(?:\?.*)?$/, '') ?? 'https://ddragon.leagueoflegends.com/cdn/16.15.1/img/champion/';
  for (const decision of source.draft.decisions) {
    if (presented[decision.championId]) continue;
    const displayName = decision.championId.split('-').map((part) => `${part.charAt(0).toUpperCase()}${part.slice(1)}`).join(' ');
    presented[decision.championId] = {
      id: decision.championId,
      name: displayName,
      nameEn: displayName,
      portraitUrl: `${portraitBaseUrl}${riotAssetId(decision.championId)}.png`,
    };
  }
  return presented;
}

function createDraft(source: CommonLiveMatchSource, options: MatchSetupOptionsViewModel, teams: Record<TeamSide, TeamViewModel>, championsById: Readonly<Record<string, ChampionViewModel>>): DraftViewModel {
  return {
    matchId: source.matchIdentity,
    simulationSeed: source.seed,
    seasonLabel: options.seasonLabel,
    gameNumber: source.draft.seriesGameNumber,
    seriesType: options.seriesType,
    source: 'LIVE',
    sourceLabel: options.sourceLabel,
    teams,
    championsById,
    rosters: {
      BLUE: bySide(source.teams, 'BLUE').lineup.map((player) => ({ playerId: player.playerId, playerName: player.nickname, position: player.position, championId: player.championId })),
      RED: bySide(source.teams, 'RED').lineup.map((player) => ({ playerId: player.playerId, playerName: player.nickname, position: player.position, championId: player.championId })),
    },
    bans: { BLUE: source.draft.blueBans, RED: source.draft.redBans },
    picks: { BLUE: source.draft.bluePicks, RED: source.draft.redPicks },
    decisions: source.draft.decisions.map((decision) => ({ turn: decision.turn, side: decision.teamSide, actionType: decision.actionType, championId: decision.championId })),
    draftRuleSetIdentity: source.draft.draftRuleSetIdentity,
    finalDraftHash: source.draft.finalDraftHash,
    finalAssignmentHash: source.draft.finalAssignmentHash,
  };
}

function hasKoreanBatchim(value: string): boolean {
  const last = value.trim().slice(-1); if (!last) return false;
  const code = last.charCodeAt(0); if (code >= 0xac00 && code <= 0xd7a3) return (code - 0xac00) % 28 !== 0;
  return /[nml]$/i.test(last);
}
function objectHasKoreanBatchim(value: string): boolean {
  const last = value.trim().slice(-1); if (!last) return false;
  const code = last.charCodeAt(0); if (code >= 0xac00 && code <= 0xd7a3) return (code - 0xac00) % 28 !== 0;
  return /[nm]$/i.test(last);
}
function killDisplayMessage(event: RealMatchEventDto, playerNamesById: Readonly<Record<string, string>>): string {
  const killer = event.killerPlayerId ? playerNamesById[event.killerPlayerId] ?? event.killerPlayerId : '킬 기록 선수';
  const victim = event.victimPlayerId ? playerNamesById[event.victimPlayerId] ?? event.victimPlayerId : '상대 선수';
  const assistants = event.assistantPlayerIds.map((playerId) => playerNamesById[playerId] ?? playerId);
  const assistText = assistants.length > 0 ? ` (어시스트: ${assistants.join(', ')})` : '';
  return `${killer}${hasKoreanBatchim(killer) ? '이' : '가'} ${victim}${objectHasKoreanBatchim(victim) ? '을' : '를'} 처치했습니다. +${event.goldAmount}G${assistText}`;
}
function recordValue(value: unknown): Readonly<Record<string, unknown>> | null {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? value as Readonly<Record<string, unknown>> : null;
}
function numberValue(value: unknown): number { return typeof value === 'number' && Number.isFinite(value) ? value : 0; }
function structureAction(event: RealMatchEventDto): StructureActionViewModel | null {
  const value = recordValue(event.structuredData.structureAction);
  if (!value || typeof value.phase !== 'string' || typeof value.targetId !== 'string') return null;
  return {
    phase: value.phase as StructureActionPhase,
    targetId: value.targetId,
    healthBefore: numberValue(value.healthBefore), damage: numberValue(value.damage),
    healthAfter: numberValue(value.healthAfter), maxHealth: numberValue(value.maxHealth),
    platesClaimed: numberValue(value.platesClaimed), wavePresent: value.wavePresent === true,
    backdoorProtection: value.backdoorProtected === true, siegeContinues: value.siegeContinues === true,
    stopReason: typeof value.stopReason === 'string' ? value.stopReason : null,
  };
}
function structureDisplayMessage(event: RealMatchEventDto, teams: Record<TeamSide, TeamViewModel>, action: StructureActionViewModel | null): string {
  const side = event.structureAttackingSide ?? event.actorSide;
  const teamCode = side ? teams[side].code : '공격 팀';
  const lane = event.lane === 'TOP' ? '탑 ' : event.lane === 'MID' ? '미드 ' : event.lane === 'BOT' ? '바텀 ' : '';
  const target = event.structureKind === 'INHIBITOR' ? `${lane}억제기`
    : event.structureKind === 'NEXUS_TURRET' ? '넥서스 포탑'
      : event.structureKind === 'NEXUS' ? '넥서스'
        : `${lane}${event.structureTowerTier === 'OUTER' ? '외곽 ' : event.structureTowerTier === 'INNER' ? '내부 ' : event.structureTowerTier === 'INHIBITOR' ? '억제기 ' : ''}포탑`;
  const source = event.structureActionSource ? STRUCTURE_SOURCE_LABELS[event.structureActionSource] : null;
  const sourceText = source ? ` · ${source}` : '';
  if (action && (action.phase === 'STARTED' || action.phase === 'DAMAGE')) {
    const protection = action.backdoorProtection ? ' · 백도어 보호 적용' : action.wavePresent ? ' · 미니언 웨이브' : '';
    return `${teamCode} · ${target} ${Math.round(action.damage)} 피해 · HP ${Math.round(action.healthAfter)}/${Math.round(action.maxHealth)}${sourceText}${protection}`;
  }
  if (action?.phase === 'RESPAWNED') return event.displayMessage ?? `넥서스 포탑이 HP ${Math.round(action.healthAfter)}/${Math.round(action.maxHealth)}로 재생성됐습니다.`;
  if (action?.phase === 'REPELLED' || action?.phase === 'ABORTED') return `${event.displayMessage ?? `${teamCode}의 공성이 종료됐습니다.`}${sourceText}`;
  return `${event.displayMessage ?? `${teamCode} · ${target} 파괴.`}${sourceText}`;
}

function createPlayback(source: CommonLiveMatchSource, options: MatchSetupOptionsViewModel, teams: Record<TeamSide, TeamViewModel>, championsById: Readonly<Record<string, ChampionViewModel>>): PlaybackViewModel {
  const playerNamesById = Object.fromEntries(source.teams.flatMap((team) => team.lineup.map((player) => [player.playerId, player.nickname])));
  const events: readonly PlaybackEventViewModel[] = source.timeline.events.map((event, index) => {
    const action = structureAction(event);
    return ({
    id: `${event.timeSeconds}:${event.eventType}:${event.actionId ?? 'event'}:${index}`,
    occurredAtSeconds: event.timeSeconds,
    eventType: event.eventType,
    actorSide: event.actorSide,
    actorPosition: event.actorPosition,
    lane: event.lane,
    actorPlayerId: event.actorPlayerId,
    killerPlayerId: event.killerPlayerId,
    victimPlayerId: event.victimPlayerId,
    assistantPlayerIds: event.assistantPlayerIds,
    combatSource: event.combatSource,
    structureActionSource: event.structureActionSource,
    structureKind: event.structureKind,
    structureTowerTier: event.structureTowerTier,
    structureAttackingSide: event.structureAttackingSide,
    structureDefendingSide: event.structureDefendingSide,
    structureAction: action,
    goldAmount: event.goldAmount,
    actionId: event.actionId,
    parentActionId: event.parentActionId,
    displayMessage: event.eventType === 'KILL' ? killDisplayMessage(event, playerNamesById)
      : event.eventType === 'TOWER' || event.eventType === 'STRUCTURE_ACTION' ? structureDisplayMessage(event, teams, action)
        : event.displayMessage ?? event.eventType,
    isMajor: MAJOR_EVENT_TYPES.has(event.eventType),
    showInLog: !HIDDEN_LOG_EVENT_TYPES.has(event.eventType),
    });
  });
  const snapshots: readonly MatchSnapshotViewModel[] = source.timeline.snapshots.map((snapshot) => {
    const playerState = (side: TeamSide) => snapshot.players.filter((player) => player.teamSide === side).map((player) => ({
      playerId: player.playerId, playerName: playerNamesById[player.playerId] ?? player.playerId,
      championId: player.championId, position: player.position, kills: player.kills, deaths: player.deaths,
      assists: player.assists, cs: player.cs, gold: player.gold, totalExperience: player.totalExperience,
      level: player.level, alive: player.alive, respawnSeconds: player.respawnRemainingSeconds,
      shutdownBountyGold: player.shutdownBountyGold,
    }));
    return {
      atSeconds: snapshot.timeSeconds,
      teams: {
        BLUE: { side: 'BLUE', kills: snapshot.blueTeam.kills, gold: snapshot.blueTeam.gold, towersDestroyed: snapshot.blueTeam.towersDestroyed, dragons: snapshot.blueTeam.dragons, inhibitorsRemaining: snapshot.blueTeam.inhibitorsRemaining, nexusTurretsRemaining: snapshot.blueTeam.nexusTurretsRemaining, nexusAlive: snapshot.blueTeam.nexusAlive, champions: playerState('BLUE') },
        RED: { side: 'RED', kills: snapshot.redTeam.kills, gold: snapshot.redTeam.gold, towersDestroyed: snapshot.redTeam.towersDestroyed, dragons: snapshot.redTeam.dragons, inhibitorsRemaining: snapshot.redTeam.inhibitorsRemaining, nexusTurretsRemaining: snapshot.redTeam.nexusTurretsRemaining, nexusAlive: snapshot.redTeam.nexusAlive, champions: playerState('RED') },
      },
    };
  });
  return {
    matchId: source.matchIdentity, simulationSeed: source.seed, seasonLabel: options.seasonLabel,
    gameNumber: source.draft.seriesGameNumber, seriesType: options.seriesType, source: 'LIVE', sourceLabel: options.sourceLabel,
    durationSeconds: source.timeline.durationSeconds, initialSeconds: 0, teams, championsById, playerNamesById,
    events, snapshots,
    finalScore: { BLUE: bySide(source.result.teams, 'BLUE').kills, RED: bySide(source.result.teams, 'RED').kills },
    winner: source.timeline.winner, endReason: source.timeline.endReason,
    projection: { sourceEventCount: events.length, includedEventCount: events.length, sourceSnapshotCount: snapshots.length, includedSnapshotCount: snapshots.length },
  };
}

function playerView(player: RealMatchPlayerResultDto, playerName: string, goldDifference: number): FinalPlayerViewModel {
  return {
    playerId: player.playerId, playerName, championId: player.championId, position: player.position,
    kills: player.kills, deaths: player.deaths, assists: player.assists, cs: player.cs, gold: player.gold,
    totalExperience: player.totalExperience, level: player.level, goldDifference, abilityProfile: player.abilityProfile,
  };
}
function resultPlayers(source: CommonLiveMatchSource, names: Readonly<Record<string, string>>): readonly FinalPlayerComparisonViewModel[] {
  return POSITIONS.map((position) => {
    const blue = source.result.players.find((player) => player.teamSide === 'BLUE' && player.position === position)!;
    const red = source.result.players.find((player) => player.teamSide === 'RED' && player.position === position)!;
    const difference = blue.gold - red.gold;
    return { position, blue: playerView(blue, names[blue.playerId] ?? blue.playerId, difference), red: playerView(red, names[red.playerId] ?? red.playerId, -difference) };
  });
}
function teamStats(source: CommonLiveMatchSource, side: TeamSide): TeamFinalStatsViewModel {
  const team: RealMatchTeamResultDto = bySide(source.result.teams, side);
  const opponent = bySide(source.result.teams, side === 'BLUE' ? 'RED' : 'BLUE');
  const players = source.result.players.filter((player) => player.teamSide === side);
  return {
    kills: team.kills, deaths: players.reduce((total, player) => total + player.deaths, 0),
    assists: players.reduce((total, player) => total + player.assists, 0), gold: team.totalGold,
    goldDifference: team.totalGold - opponent.totalGold, towers: team.towersDestroyed, dragons: team.dragons,
    barons: source.timeline.events.filter((event) => event.eventType === 'BARON' && event.actorSide === side).length,
    inhibitorsDestroyed: 3 - opponent.inhibitorsRemaining,
  };
}
function createResult(source: CommonLiveMatchSource, options: MatchSetupOptionsViewModel, teams: Record<TeamSide, TeamViewModel>, names: Readonly<Record<string, string>>): MatchResultViewModel {
  return {
    matchId: source.matchIdentity, seasonLabel: options.seasonLabel, gameNumber: source.draft.seriesGameNumber,
    seriesType: options.seriesType, seed: source.seed, durationSeconds: source.result.durationSeconds,
    winner: source.result.winner, endReason: source.result.endReason, teams,
    teamStats: { BLUE: teamStats(source, 'BLUE'), RED: teamStats(source, 'RED') },
    players: resultPlayers(source, names), bans: { BLUE: source.draft.blueBans, RED: source.draft.redBans },
    goldTimeline: source.timeline.snapshots.map((snapshot) => ({
      timeSeconds: snapshot.timeSeconds, blueGold: snapshot.blueTeam.gold, redGold: snapshot.redTeam.gold,
      difference: snapshot.blueTeam.gold - snapshot.redTeam.gold,
    })),
    integrity: {
      source: 'LIVE', sourceLabel: options.sourceLabel, seed: source.seed,
      runtimeProfile: source.integrity.runtimeProfileId, configurationHash: source.integrity.configurationHash,
      policyHash: source.integrity.policyHash, engineImplementationVersion: source.integrity.engineImplementationVersion,
      resourceProvenanceHash: source.integrity.resourceProvenanceHash, replayHash: source.integrity.replayProvenanceHash,
      simulatorTimelineHash: source.integrity.simulatorTimelineHash, structuredTimelineHash: source.integrity.structuredTimelineHash,
      outputHash: source.integrity.outputHash, randomDrawCount: source.integrity.randomFingerprint.randomDrawCount,
      randomTraceHash: source.integrity.randomFingerprint.randomTraceHash, manifestRawSha256: null,
    },
  };
}

export function createLiveMatchSessionFromCommon(
  source: CommonLiveMatchSource,
  options: MatchSetupOptionsViewModel,
  selection: MatchSetupSelection,
  requestPerformance: Omit<MatchSessionPerformance, 'normalizationMs'>,
  draftOrigin: MatchSessionViewModel['draftOrigin'],
): MatchSessionViewModel {
  const normalizationStartedAt = performance.now();
  const selectedTeams = {
    BLUE: options.teams.find((team) => team.code === selection.blueTeamId),
    RED: options.teams.find((team) => team.code === selection.redTeamId),
  };
  if (!selectedTeams.BLUE || !selectedTeams.RED) throw new Error('선택한 LIVE 팀을 options에서 찾을 수 없습니다.');
  const teams = {
    BLUE: toTeamViewModel(bySide(source.teams, 'BLUE'), options.sourceLabel),
    RED: toTeamViewModel(bySide(source.teams, 'RED'), options.sourceLabel),
  };
  const championsById = createChampionMap(source);
  const playback = createPlayback(source, options, teams, championsById);
  const session: MatchSessionViewModel = {
    sessionId: source.matchIdentity, source: 'LIVE', setup: selection,
    selectedTeams: { BLUE: selectedTeams.BLUE, RED: selectedTeams.RED },
    draft: createDraft(source, options, teams, championsById), playback,
    result: createResult(source, options, teams, playback.playerNamesById),
    draftOrigin,
    performance: { ...requestPerformance, normalizationMs: 0 },
  };
  session.performance.normalizationMs = performance.now() - normalizationStartedAt;
  return session;
}

export function createLiveMatchSession(
  source: RealMatchResponseDto,
  options: MatchSetupOptionsViewModel,
  selection: MatchSetupSelection,
  requestPerformance: Omit<MatchSessionPerformance, 'normalizationMs'>,
): MatchSessionViewModel {
  return createLiveMatchSessionFromCommon(source, options, selection, requestPerformance, { mode: 'AUTO' });
}
