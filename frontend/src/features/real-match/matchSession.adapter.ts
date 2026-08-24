import { draftFixture, playbackFixture } from './realMatch.fixtures';
import type {
  FinalPlayerComparisonViewModel,
  MatchResultViewModel,
  MatchSessionViewModel,
  MatchSetupOptionsViewModel,
  MatchSetupSelection,
  MatchTeamOptionViewModel,
  TeamFinalStatsViewModel,
} from './matchSession.types';
import type {
  DraftResultViewModel,
  DraftViewModel,
  PlaybackViewModel,
  Position,
  TeamSide,
  TeamViewModel,
} from './realMatch.types';
import { applyDraftResult } from './realMatch.adapter';

const sides: readonly TeamSide[] = ['BLUE', 'RED'];

function toTeamViewModel(side: TeamSide, team: MatchTeamOptionViewModel): TeamViewModel {
  return { side, code: team.code, record: `${team.league} · ${team.record}`, seriesScore: 0 };
}

function selectedTeam(options: MatchSetupOptionsViewModel, teamId: string): MatchTeamOptionViewModel {
  const selected = options.teams.find((team) => team.teamId === teamId);
  if (!selected) throw new Error(`선택한 팀(${teamId})을 찾을 수 없습니다.`);
  return selected;
}

function createDraft(
  options: MatchSetupOptionsViewModel,
  selection: MatchSetupSelection,
  selectedTeams: Record<TeamSide, MatchTeamOptionViewModel>,
  matchId: string,
): DraftViewModel {
  return {
    ...draftFixture,
    matchId,
    simulationSeed: selection.seed,
    seasonLabel: options.seasonLabel,
    gameNumber: selection.gameNumber,
    seriesType: selection.seriesType,
    teams: {
      BLUE: toTeamViewModel('BLUE', selectedTeams.BLUE),
      RED: toTeamViewModel('RED', selectedTeams.RED),
    },
    rosters: {
      BLUE: selectedTeams.BLUE.roster.map((player) => ({ ...player, championId: null })),
      RED: selectedTeams.RED.roster.map((player) => ({ ...player, championId: null })),
    },
    bans: { BLUE: [], RED: [] },
  };
}

function createPlayerIdentityMap(
  base: PlaybackViewModel,
  selectedTeams: Record<TeamSide, MatchTeamOptionViewModel>,
) {
  const identityMap = new Map<string, { playerId: string; playerName: string }>();
  const nameReplacements = new Map<string, string>();
  const teamReplacements = new Map<string, string>();

  for (const side of sides) {
    const basePlayers = base.snapshots[0].teams[side].champions;
    for (const basePlayer of basePlayers) {
      const replacement = selectedTeams[side].roster.find((player) => player.position === basePlayer.position);
      if (!replacement) continue;
      identityMap.set(basePlayer.playerId, replacement);
      nameReplacements.set(basePlayer.playerName, replacement.playerName);
    }
    teamReplacements.set(base.teams[side].code, selectedTeams[side].code);
  }

  return { identityMap, nameReplacements, teamReplacements };
}

function replaceDisplayNames(value: string, replacements: readonly Map<string, string>[]): string {
  return replacements.reduce((copy, map) => [...map.entries()].reduce(
    (current, [source, target]) => current.split(source).join(target),
    copy,
  ), value);
}

function createPlayback(
  selection: MatchSetupSelection,
  selectedTeams: Record<TeamSide, MatchTeamOptionViewModel>,
  matchId: string,
): PlaybackViewModel {
  const base = playbackFixture;
  const { identityMap, nameReplacements, teamReplacements } = createPlayerIdentityMap(base, selectedTeams);
  const identityFor = (playerId: string) => identityMap.get(playerId) ?? { playerId, playerName: playerId };
  const mapPlayerId = (playerId: string | null) => playerId ? identityFor(playerId).playerId : null;
  const teams = {
    BLUE: toTeamViewModel('BLUE', selectedTeams.BLUE),
    RED: toTeamViewModel('RED', selectedTeams.RED),
  };
  const timeout = selection.seed === 'TIMEOUT-73';
  const winner: TeamSide | null = timeout
    ? null
    : selectedTeams.BLUE.code === 'GEN'
      ? 'BLUE'
      : selectedTeams.RED.code === 'GEN'
        ? 'RED'
        : 'BLUE';
  const finalScore = timeout
    ? { BLUE: 14, RED: 14 }
    : winner === 'BLUE'
      ? { BLUE: 18, RED: 11 }
      : { BLUE: 12, RED: 17 };
  const durationSeconds = timeout ? 2700 : winner === 'BLUE' ? 1938 : 2046;
  const timeFactor = durationSeconds / base.durationSeconds;
  const finalGold = timeout
    ? { BLUE: 62, RED: 61.8 }
    : winner === 'BLUE'
      ? { BLUE: 55.8, RED: 49.2 }
      : { BLUE: 51.2, RED: 54.8 };

  return {
    ...base,
    matchId,
    simulationSeed: selection.seed,
    gameNumber: selection.gameNumber,
    seriesType: selection.seriesType,
    teams,
    winner,
    endReason: timeout ? 'TIMEOUT' : 'NEXUS_DESTROYED',
    durationSeconds,
    comparisonReferenceSeconds: Math.round(base.comparisonReferenceSeconds * timeFactor),
    finalScore,
    events: base.events.map((event) => ({
      ...event,
      occurredAtSeconds: Math.round(event.occurredAtSeconds * timeFactor),
      killerPlayerId: mapPlayerId(event.killerPlayerId),
      victimPlayerId: mapPlayerId(event.victimPlayerId),
      assistantPlayerIds: event.assistantPlayerIds.map((id) => identityFor(id).playerId),
      participants: event.participants.map((participant) => ({ ...participant, playerId: identityFor(participant.playerId).playerId })),
      description: replaceDisplayNames(event.description, [nameReplacements, teamReplacements]),
    })),
    snapshots: base.snapshots.map((snapshot) => ({
      ...snapshot,
      atSeconds: Math.round(snapshot.atSeconds * timeFactor),
      teams: {
        BLUE: {
          ...snapshot.teams.BLUE,
          kills: snapshot.atSeconds === base.durationSeconds ? finalScore.BLUE : snapshot.teams.BLUE.kills,
          gold: snapshot.atSeconds === base.durationSeconds ? finalGold.BLUE : snapshot.teams.BLUE.gold,
          champions: snapshot.teams.BLUE.champions.map((player) => ({ ...player, ...identityFor(player.playerId) })),
        },
        RED: {
          ...snapshot.teams.RED,
          kills: snapshot.atSeconds === base.durationSeconds ? finalScore.RED : snapshot.teams.RED.kills,
          gold: snapshot.atSeconds === base.durationSeconds ? finalGold.RED : snapshot.teams.RED.gold,
          champions: snapshot.teams.RED.champions.map((player) => ({ ...player, ...identityFor(player.playerId) })),
        },
      },
    })),
    comparisonAtInitialTime: base.comparisonAtInitialTime.map((row) => ({
      ...row,
      blue: { ...row.blue, ...identityFor(row.blue.playerId) },
      red: { ...row.red, ...identityFor(row.red.playerId) },
    })),
  };
}

export function createMatchSession(
  options: MatchSetupOptionsViewModel,
  selection: MatchSetupSelection,
): MatchSessionViewModel {
  const selectedTeams = {
    BLUE: selectedTeam(options, selection.blueTeamId),
    RED: selectedTeam(options, selection.redTeamId),
  };
  const sessionId = `fixture-${selection.blueTeamId}-${selection.redTeamId}-${selection.gameNumber}-${Date.now()}`;
  return {
    sessionId,
    createdAt: new Date().toISOString(),
    setup: selection,
    selectedTeams,
    draft: createDraft(options, selection, selectedTeams, sessionId),
    draftResult: null,
    playback: createPlayback(selection, selectedTeams, sessionId),
    result: null,
  };
}

export function completeSessionDraft(
  session: MatchSessionViewModel,
  draftResult: DraftResultViewModel,
): MatchSessionViewModel {
  return {
    ...session,
    draftResult,
    playback: applyDraftResult(session.playback, draftResult),
  };
}

function resultPlayers(playback: PlaybackViewModel): readonly FinalPlayerComparisonViewModel[] {
  return playback.comparisonAtInitialTime.map((row) => {
    const bluePerformance = playback.winner === 'RED' ? row.red : row.blue;
    const redPerformance = playback.winner === 'RED' ? row.blue : row.red;
    const blue = { ...row.blue, kills: bluePerformance.kills, deaths: bluePerformance.deaths, assists: bluePerformance.assists, cs: bluePerformance.cs, gold: bluePerformance.gold, level: bluePerformance.level };
    const red = { ...row.red, kills: redPerformance.kills, deaths: redPerformance.deaths, assists: redPerformance.assists, cs: redPerformance.cs, gold: redPerformance.gold, level: redPerformance.level };
    const blueGold = Math.round(blue.gold * 1000);
    const redGold = Math.round(red.gold * 1000);
    const difference = blueGold - redGold;
    return {
      position: row.position,
      blue: { ...blue, gold: blueGold, laneGoldDifference: difference },
      red: { ...red, gold: redGold, laneGoldDifference: -difference },
    };
  });
}

function teamStats(winner: TeamSide | null): Record<TeamSide, TeamFinalStatsViewModel> {
  if (winner === null) {
    return {
      BLUE: { kills: 14, deaths: 14, assists: 37, gold: 62000, goldDifference: 200, towers: 7, dragons: 3, barons: 1, inhibitors: 1 },
      RED: { kills: 14, deaths: 14, assists: 35, gold: 61800, goldDifference: -200, towers: 7, dragons: 3, barons: 1, inhibitors: 1 },
    };
  }
  const winning: TeamFinalStatsViewModel = { kills: winner === 'BLUE' ? 18 : 17, deaths: winner === 'BLUE' ? 11 : 12, assists: winner === 'BLUE' ? 43 : 46, gold: winner === 'BLUE' ? 55800 : 54800, goldDifference: winner === 'BLUE' ? 6600 : 3600, towers: winner === 'BLUE' ? 9 : 10, dragons: 4, barons: 2, inhibitors: 2 };
  const losing: TeamFinalStatsViewModel = { kills: winner === 'BLUE' ? 11 : 12, deaths: winner === 'BLUE' ? 18 : 17, assists: winner === 'BLUE' ? 24 : 31, gold: winner === 'BLUE' ? 49200 : 51200, goldDifference: -winning.goldDifference, towers: winner === 'BLUE' ? 5 : 6, dragons: 2, barons: 0, inhibitors: 0 };
  return winner === 'BLUE' ? { BLUE: winning, RED: losing } : { BLUE: losing, RED: winning };
}

export function createMatchResult(session: MatchSessionViewModel): MatchResultViewModel {
  const playback = session.playback;
  const seedHash = [...session.setup.seed].reduce((total, character) => total + character.charCodeAt(0), 0).toString(16).padStart(4, '0');
  return {
    matchId: session.sessionId,
    seasonLabel: '2026 LCK SPRING',
    gameNumber: session.setup.gameNumber,
    seriesType: session.setup.seriesType,
    seed: session.setup.seed,
    durationSeconds: playback.durationSeconds,
    winner: playback.winner,
    endReason: playback.endReason,
    teams: playback.teams,
    teamStats: teamStats(playback.winner),
    players: resultPlayers(playback),
    bans: session.draftResult?.bans ?? { BLUE: [], RED: [] },
    integrity: {
      seed: session.setup.seed,
      outputHash: `sha256:${seedHash}4c53188f25a81b9f4387cfa738086b2b5f6609f169ba2a998ce4c3fde7187ab2e`,
      replayHash: `sha256:${seedHash}9e207a248e6f7cf10c08ea6545c5bd17e96fe78a11f0a2795e355f84fe6f07154e`,
      runtimeProfile: 'lol-sim-fixture-v1 / deterministic-cpu',
      responseTime: new Date().toISOString(),
    },
  };
}

export const resultStateFixtures = {
  blueWin: { winner: 'BLUE', endReason: 'NEXUS_DESTROYED' },
  redWin: { winner: 'RED', endReason: 'NEXUS_DESTROYED' },
  timeout: { winner: null, endReason: 'TIMEOUT' },
  loading: { status: 'loading' },
  error: { status: 'error' },
  partialIntegrity: { replayHash: null },
} as const;

export const positions: readonly Position[] = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];
