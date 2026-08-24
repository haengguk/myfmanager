import type {
  FinalPlayerComparisonViewModel, FinalPlayerViewModel, MatchResultViewModel, MatchSessionViewModel,
  MatchSetupOptionsViewModel, MatchSetupSelection, MatchTeamOptionViewModel, TeamFinalStatsViewModel,
} from './matchSession.types';
import { realMatchV8ReferenceProjection } from './reference/realMatchReference.fixture';
import type {
  RealMatchV8ReferenceProjection, ReferencePlayerResult, ReferenceProjectedTeamState,
  ReferenceTeamPresentation, ReferenceTeamResult,
} from './reference/realMatchReference.contract';
import type {
  ChampionViewModel, DraftViewModel, MatchSnapshotViewModel, PlaybackEventViewModel, PlaybackViewModel,
  Position, TeamSide, TeamViewModel,
} from './realMatch.types';

const SIDES: readonly TeamSide[] = ['BLUE', 'RED'];
const POSITIONS: readonly Position[] = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];
const MAJOR_EVENT_TYPES = new Set([
  'KILL', 'JUNGLE_GANK', 'COUNTER_GANK', 'LANE_COMBAT', 'DRAGON', 'BARON', 'ELDER',
  'TOWER', 'TEAMFIGHT', 'TEAMFIGHT_RESULT', 'ACE', 'GAME_END',
]);

const reference = realMatchV8ReferenceProjection;

export const referenceMatchSetupOptions: MatchSetupOptionsViewModel = {
  seasonLabel: 'REAL_MATCH_API_V1 · V8 Reference',
  gameNumber: reference.match.draft.seriesGameNumber,
  seriesType: '단판 · Fresh Game 1',
  draftRule: 'Professional Draft · 자동',
  defaultSeed: reference.request.seed,
  referenceBlueTeamCode: reference.request.blueTeamCode,
  referenceRedTeamCode: reference.request.redTeamCode,
  referenceLabel: reference.provenance.referenceLabel,
  teams: reference.options.teams.map((team): MatchTeamOptionViewModel => ({
    teamId: team.teamCode,
    code: team.teamCode,
    name: team.displayName,
    sourceLabel: 'Backend options reference · 선발 5명',
    roster: team.lineup.map((player) => ({
      playerId: player.playerId,
      playerName: player.nickname,
      position: player.position,
    })),
  })),
};

export function isReferenceSelection(selection: MatchSetupSelection): boolean {
  return selection.blueTeamId === reference.request.blueTeamCode
    && selection.redTeamId === reference.request.redTeamCode
    && selection.seed === reference.request.seed
    && selection.gameNumber === reference.match.draft.seriesGameNumber;
}

function bySide<T extends { teamSide: TeamSide }>(values: readonly T[], side: TeamSide): T {
  const value = values.find((candidate) => candidate.teamSide === side);
  if (!value) throw new Error(`${side} reference 데이터를 찾을 수 없습니다.`);
  return value;
}

function toTeamViewModel(team: ReferenceTeamPresentation): TeamViewModel {
  return {
    side: team.teamSide,
    code: team.teamCode,
    displayName: team.displayName,
    detail: 'V8 Reference · Fresh Game 1',
  };
}

function createChampionMap(source: RealMatchV8ReferenceProjection): Readonly<Record<string, ChampionViewModel>> {
  const entries = source.presentation.draftChampions.map((champion) => [
    champion.championId,
    {
      id: champion.championId,
      name: champion.displayNameKo,
      nameEn: champion.displayNameEn,
      portraitUrl: champion.portraitUrl,
    } satisfies ChampionViewModel,
  ] as const);
  return Object.fromEntries(entries);
}

function createDraft(source: RealMatchV8ReferenceProjection, teams: Record<TeamSide, TeamViewModel>, championsById: Readonly<Record<string, ChampionViewModel>>): DraftViewModel {
  return {
    matchId: source.match.matchIdentity,
    simulationSeed: source.match.seed,
    seasonLabel: referenceMatchSetupOptions.seasonLabel,
    gameNumber: source.match.draft.seriesGameNumber,
    seriesType: referenceMatchSetupOptions.seriesType,
    referenceLabel: source.provenance.referenceLabel,
    teams,
    championsById,
    rosters: {
      BLUE: bySide(source.match.teams, 'BLUE').lineup.map((player) => ({ playerId: player.playerId, playerName: player.nickname, position: player.position, championId: player.championId })),
      RED: bySide(source.match.teams, 'RED').lineup.map((player) => ({ playerId: player.playerId, playerName: player.nickname, position: player.position, championId: player.championId })),
    },
    bans: { BLUE: source.match.draft.blueBans, RED: source.match.draft.redBans },
    picks: { BLUE: source.match.draft.bluePicks, RED: source.match.draft.redPicks },
    decisions: source.match.draft.decisions.map((decision) => ({ turn: decision.turn, side: decision.teamSide, actionType: decision.actionType, championId: decision.championId })),
    draftRuleSetIdentity: source.match.draft.draftRuleSetIdentity,
    finalDraftHash: source.match.draft.finalDraftHash,
    finalAssignmentHash: source.match.draft.finalAssignmentHash,
  };
}

function mapTeamSnapshot(source: ReferenceProjectedTeamState, side: TeamSide, players: MatchSnapshotViewModel['teams'][TeamSide]['champions']): MatchSnapshotViewModel['teams'][TeamSide] {
  return {
    side,
    kills: source.kills,
    gold: source.gold,
    towersDestroyed: source.towersDestroyed,
    dragons: source.dragons,
    inhibitorsRemaining: source.inhibitorsRemaining,
    champions: players,
  };
}

function createPlayback(source: RealMatchV8ReferenceProjection, teams: Record<TeamSide, TeamViewModel>, championsById: Readonly<Record<string, ChampionViewModel>>): PlaybackViewModel {
  const playerNamesById = Object.fromEntries(source.match.teams.flatMap((team) => team.lineup.map((player) => [player.playerId, player.nickname])));
  const events: readonly PlaybackEventViewModel[] = source.match.timeline.events.map((event) => ({
    id: event.projectionId,
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
    actionId: event.actionId,
    parentActionId: event.parentActionId,
    displayMessage: event.displayMessage ?? event.eventType,
    isMajor: MAJOR_EVENT_TYPES.has(event.eventType),
  }));
  const snapshots: readonly MatchSnapshotViewModel[] = source.match.timeline.snapshots.map((snapshot) => {
    const playerState = (side: TeamSide) => snapshot.players.filter((player) => player.teamSide === side).map((player) => ({
      playerId: player.playerId,
      playerName: playerNamesById[player.playerId] ?? player.playerId,
      championId: player.championId,
      position: player.position,
      kills: player.kills,
      deaths: player.deaths,
      assists: player.assists,
      cs: player.cs,
      gold: player.gold,
      totalExperience: player.totalExperience,
      level: player.level,
      alive: player.alive,
      respawnSeconds: player.respawnRemainingSeconds,
    }));
    return {
      atSeconds: snapshot.timeSeconds,
      teams: {
        BLUE: mapTeamSnapshot(snapshot.blueTeam, 'BLUE', playerState('BLUE')),
        RED: mapTeamSnapshot(snapshot.redTeam, 'RED', playerState('RED')),
      },
    };
  });
  return {
    matchId: source.match.matchIdentity,
    simulationSeed: source.match.seed,
    seasonLabel: referenceMatchSetupOptions.seasonLabel,
    gameNumber: source.match.draft.seriesGameNumber,
    seriesType: referenceMatchSetupOptions.seriesType,
    referenceLabel: source.provenance.referenceLabel,
    durationSeconds: source.match.timeline.durationSeconds,
    initialSeconds: 0,
    teams,
    championsById,
    playerNamesById,
    events,
    snapshots,
    finalScore: {
      BLUE: bySide(source.match.result.teams, 'BLUE').kills,
      RED: bySide(source.match.result.teams, 'RED').kills,
    },
    winner: source.match.timeline.winner,
    endReason: source.match.timeline.endReason,
    projection: {
      sourceEventCount: source.provenance.sourceEventCount,
      includedEventCount: source.provenance.includedEventCount,
      sourceSnapshotCount: source.provenance.sourceSnapshotCount,
      includedSnapshotCount: source.provenance.includedSnapshotCount,
    },
  };
}

function playerView(player: ReferencePlayerResult, playerName: string, goldDifference: number): FinalPlayerViewModel {
  return {
    playerId: player.playerId, playerName, championId: player.championId, position: player.position,
    kills: player.kills, deaths: player.deaths, assists: player.assists, cs: player.cs, gold: player.gold,
    totalExperience: player.totalExperience, level: player.level, goldDifference, abilityProfile: player.abilityProfile,
  };
}

function resultPlayers(source: RealMatchV8ReferenceProjection, names: Readonly<Record<string, string>>): readonly FinalPlayerComparisonViewModel[] {
  return POSITIONS.map((position) => {
    const blue = source.match.result.players.find((player) => player.teamSide === 'BLUE' && player.position === position);
    const red = source.match.result.players.find((player) => player.teamSide === 'RED' && player.position === position);
    if (!blue || !red) throw new Error(`${position} result 비교 데이터를 찾을 수 없습니다.`);
    const difference = blue.gold - red.gold;
    return { position, blue: playerView(blue, names[blue.playerId] ?? blue.playerId, difference), red: playerView(red, names[red.playerId] ?? red.playerId, -difference) };
  });
}

function teamStats(source: RealMatchV8ReferenceProjection, side: TeamSide): TeamFinalStatsViewModel {
  const team: ReferenceTeamResult = bySide(source.match.result.teams, side);
  const opponent: ReferenceTeamResult = bySide(source.match.result.teams, side === 'BLUE' ? 'RED' : 'BLUE');
  const players = source.match.result.players.filter((player) => player.teamSide === side);
  return {
    kills: team.kills,
    deaths: players.reduce((total, player) => total + player.deaths, 0),
    assists: players.reduce((total, player) => total + player.assists, 0),
    gold: team.totalGold,
    goldDifference: team.totalGold - opponent.totalGold,
    towers: team.towersDestroyed,
    dragons: team.dragons,
    barons: source.match.timeline.events.filter((event) => event.eventType === 'BARON' && event.actorSide === side).length,
    inhibitorsDestroyed: 3 - opponent.inhibitorsRemaining,
  };
}

function createResult(source: RealMatchV8ReferenceProjection, teams: Record<TeamSide, TeamViewModel>, names: Readonly<Record<string, string>>): MatchResultViewModel {
  const integrity = source.match.integrity;
  return {
    matchId: source.match.matchIdentity,
    seasonLabel: referenceMatchSetupOptions.seasonLabel,
    gameNumber: source.match.draft.seriesGameNumber,
    seriesType: referenceMatchSetupOptions.seriesType,
    seed: source.match.seed,
    durationSeconds: source.match.result.durationSeconds,
    winner: source.match.result.winner,
    endReason: source.match.result.endReason,
    teams,
    teamStats: { BLUE: teamStats(source, 'BLUE'), RED: teamStats(source, 'RED') },
    players: resultPlayers(source, names),
    bans: { BLUE: source.match.draft.blueBans, RED: source.match.draft.redBans },
    goldTimeline: source.match.timeline.snapshots.map((snapshot) => ({
      timeSeconds: snapshot.timeSeconds,
      blueGold: snapshot.blueTeam.gold,
      redGold: snapshot.redTeam.gold,
      difference: snapshot.blueTeam.gold - snapshot.redTeam.gold,
    })),
    integrity: {
      seed: source.match.seed,
      referenceLabel: source.provenance.referenceLabel,
      runtimeProfile: integrity.runtimeProfileId,
      configurationHash: integrity.configurationHash,
      policyHash: integrity.policyHash,
      engineImplementationVersion: integrity.engineImplementationVersion,
      resourceProvenanceHash: integrity.resourceProvenanceHash,
      replayHash: integrity.replayProvenanceHash,
      simulatorTimelineHash: integrity.simulatorTimelineHash,
      structuredTimelineHash: integrity.structuredTimelineHash,
      outputHash: integrity.outputHash,
      randomDrawCount: integrity.randomFingerprint.randomDrawCount,
      randomTraceHash: integrity.randomFingerprint.randomTraceHash,
      manifestRawSha256: source.provenance.sourceManifestRawSha256,
    },
  };
}

export function createReferenceMatchSession(selection: MatchSetupSelection): MatchSessionViewModel {
  if (!isReferenceSelection(selection)) throw new Error('V1-A에서는 GEN 대 T1, seed 73 reference 경기만 확인할 수 있습니다.');
  const selectedTeams = {
    BLUE: referenceMatchSetupOptions.teams.find((team) => team.code === selection.blueTeamId),
    RED: referenceMatchSetupOptions.teams.find((team) => team.code === selection.redTeamId),
  };
  if (!selectedTeams.BLUE || !selectedTeams.RED) throw new Error('승인 reference 팀을 options에서 찾을 수 없습니다.');
  const teams = {
    BLUE: toTeamViewModel(bySide(reference.match.teams, 'BLUE')),
    RED: toTeamViewModel(bySide(reference.match.teams, 'RED')),
  };
  const championsById = createChampionMap(reference);
  const playback = createPlayback(reference, teams, championsById);
  return {
    sessionId: reference.match.matchIdentity,
    scenario: 'REFERENCE_SUCCESS',
    setup: selection,
    selectedTeams: { BLUE: selectedTeams.BLUE, RED: selectedTeams.RED },
    draft: createDraft(reference, teams, championsById),
    playback,
    result: createResult(reference, teams, playback.playerNamesById),
  };
}

export const referenceMatchSession = createReferenceMatchSession({
  blueTeamId: reference.request.blueTeamCode,
  redTeamId: reference.request.redTeamCode,
  seed: reference.request.seed,
  gameNumber: reference.match.draft.seriesGameNumber,
  seriesType: referenceMatchSetupOptions.seriesType,
});

export const positions = POSITIONS;
export const sides = SIDES;
