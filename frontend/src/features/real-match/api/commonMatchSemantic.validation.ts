import type { Position, TeamSide } from '../realMatch.contract';

type JsonRecord = Record<string, unknown>;
type ContractFailure = (path: string, message: string) => never;

export interface CommonMatchSemanticSections {
  teams: unknown;
  result: unknown;
  timeline: unknown;
  assignments?: unknown;
  paths: {
    teams: string;
    result: string;
    timeline: string;
    assignments?: string;
  };
}

interface PresentationIdentity {
  side: TeamSide;
  position: Position;
  championId: string;
}

const FINAL_PLAYER_FIELDS = [
  'teamSide', 'position', 'championId', 'kills', 'deaths', 'assists', 'cs', 'gold',
  'totalExperience', 'level',
] as const;

function object(value: unknown, path: string, fail: ContractFailure): JsonRecord {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    fail(path, 'JSON 객체가 필요합니다.');
  }
  return value as JsonRecord;
}

function list(value: unknown, path: string, fail: ContractFailure): readonly unknown[] {
  if (!Array.isArray(value)) fail(path, '배열이 필요합니다.');
  return value;
}

function stringValue(value: unknown, path: string, fail: ContractFailure): string {
  if (typeof value !== 'string' || value.length === 0) fail(path, '비어 있지 않은 문자열이 필요합니다.');
  return value;
}

/**
 * Cross-envelope semantic checks shared by automatic and player-controlled match payloads.
 * Envelope/schema validation remains in each API validator; gameplay identity does not.
 */
export function validateCommonMatchSemantics(
  sections: CommonMatchSemanticSections,
  fail: ContractFailure,
): void {
  const presentationPlayers = new Map<string, PresentationIdentity>();
  const teamCodes = new Map<TeamSide, string>();
  list(sections.teams, sections.paths.teams, fail).forEach((teamValue, teamIndex) => {
    const teamPath = `${sections.paths.teams}[${teamIndex}]`;
    const team = object(teamValue, teamPath, fail);
    const side = stringValue(team.teamSide, `${teamPath}.teamSide`, fail) as TeamSide;
    teamCodes.set(side, stringValue(team.teamCode, `${teamPath}.teamCode`, fail));
    list(team.lineup, `${teamPath}.lineup`, fail).forEach((playerValue, playerIndex) => {
      const playerPath = `${teamPath}.lineup[${playerIndex}]`;
      const player = object(playerValue, playerPath, fail);
      const playerId = stringValue(player.playerId, `${playerPath}.playerId`, fail);
      if (presentationPlayers.has(playerId)) fail(`${playerPath}.playerId`, '중복 presentation 선수입니다.');
      presentationPlayers.set(playerId, {
        side,
        position: stringValue(player.position, `${playerPath}.position`, fail) as Position,
        championId: stringValue(player.championId, `${playerPath}.championId`, fail),
      });
    });
  });

  if (sections.assignments !== undefined) {
    const assignmentsPath = sections.paths.assignments ?? '$.assignments';
    list(sections.assignments, assignmentsPath, fail).forEach((assignmentValue, index) => {
      const path = `${assignmentsPath}[${index}]`;
      const assignment = object(assignmentValue, path, fail);
      const playerId = stringValue(assignment.playerId, `${path}.playerId`, fail);
      const identity = presentationPlayers.get(playerId);
      if (!identity
        || identity.side !== assignment.teamSide
        || identity.position !== assignment.position
        || identity.championId !== assignment.championId) {
        fail(path, 'match presentation과 final assignment가 일치하지 않습니다.');
      }
    });
  }

  const result = object(sections.result, sections.paths.result, fail);
  const resultTeams = new Map<TeamSide, JsonRecord>();
  list(result.teams, `${sections.paths.result}.teams`, fail).forEach((teamValue, index) => {
    const path = `${sections.paths.result}.teams[${index}]`;
    const team = object(teamValue, path, fail);
    const side = stringValue(team.teamSide, `${path}.teamSide`, fail) as TeamSide;
    if (team.teamIdentity !== teamCodes.get(side)) fail(`${path}.teamIdentity`, 'presentation 팀 identity와 일치하지 않습니다.');
    resultTeams.set(side, team);
  });
  const resultPlayers = new Map<string, JsonRecord>();
  list(result.players, `${sections.paths.result}.players`, fail).forEach((playerValue, index) => {
    const path = `${sections.paths.result}.players[${index}]`;
    const player = object(playerValue, path, fail);
    const playerId = stringValue(player.playerId, `${path}.playerId`, fail);
    const identity = presentationPlayers.get(playerId);
    if (!identity
      || identity.side !== player.teamSide
      || identity.position !== player.position
      || identity.championId !== player.championId) {
      fail(path, 'presentation과 선수 결과 identity가 일치하지 않습니다.');
    }
    resultPlayers.set(playerId, player);
  });

  const timeline = object(sections.timeline, sections.paths.timeline, fail);
  list(timeline.events, `${sections.paths.timeline}.events`, fail).forEach((eventValue, index) => {
    const path = `${sections.paths.timeline}.events[${index}]`;
    const event = object(eventValue, path, fail);
    const actorPlayerId = event.actorPlayerId as string | null;
    if (actorPlayerId !== null) {
      const actor = presentationPlayers.get(actorPlayerId);
      if (!actor) fail(`${path}.actorPlayerId`, 'presentation에 없는 선수입니다.');
      if (actor.side !== event.actorSide || actor.position !== event.actorPosition) {
        fail(`${path}.actorPlayerId`, 'actor side/position과 선수 identity가 일치하지 않습니다.');
      }
    }
    for (const role of ['killer', 'victim'] as const) {
      const playerId = event[`${role}PlayerId`] as string | null;
      const championId = event[`${role}ChampionId`] as string | null;
      if ((playerId === null) !== (championId === null)) fail(`${path}.${role}PlayerId`, 'player/champion identity는 함께 제공되어야 합니다.');
      if (playerId !== null) {
        const identity = presentationPlayers.get(playerId);
        if (!identity) fail(`${path}.${role}PlayerId`, 'presentation에 없는 선수입니다.');
        if (identity.championId !== championId) fail(`${path}.${role}ChampionId`, '선수의 최종 champion identity와 일치하지 않습니다.');
      }
    }
    const assistantPlayerIds = list(event.assistantPlayerIds, `${path}.assistantPlayerIds`, fail)
      .map((value, assistantIndex) => stringValue(value, `${path}.assistantPlayerIds[${assistantIndex}]`, fail));
    const assistantChampionIds = list(event.assistantChampionIds, `${path}.assistantChampionIds`, fail)
      .map((value, assistantIndex) => stringValue(value, `${path}.assistantChampionIds[${assistantIndex}]`, fail));
    if (assistantPlayerIds.length !== assistantChampionIds.length
      || new Set(assistantPlayerIds).size !== assistantPlayerIds.length) {
      fail(`${path}.assistantPlayerIds`, 'assistant player/champion identity가 중복 없이 쌍을 이뤄야 합니다.');
    }
    assistantPlayerIds.forEach((playerId, assistantIndex) => {
      const identity = presentationPlayers.get(playerId);
      if (!identity) fail(`${path}.assistantPlayerIds[${assistantIndex}]`, 'presentation에 없는 선수입니다.');
      if (identity.championId !== assistantChampionIds[assistantIndex]) {
        fail(`${path}.assistantChampionIds[${assistantIndex}]`, '선수의 최종 champion identity와 일치하지 않습니다.');
      }
    });
  });

  const snapshots = list(timeline.snapshots, `${sections.paths.timeline}.snapshots`, fail);
  if (!snapshots.length) fail(`${sections.paths.timeline}.snapshots`, '최종 snapshot이 필요합니다.');
  const finalPath = `${sections.paths.timeline}.snapshots[last]`;
  const finalSnapshot = object(snapshots[snapshots.length - 1], finalPath, fail);
  for (const [side, key] of [['BLUE', 'blueTeam'], ['RED', 'redTeam']] as const) {
    const finalTeam = object(finalSnapshot[key], `${finalPath}.${key}`, fail);
    const resultTeam = resultTeams.get(side);
    if (!resultTeam || finalTeam.kills !== resultTeam.kills || finalTeam.gold !== resultTeam.totalGold) {
      fail(`${finalPath}.${key}`, '최종 팀 결과와 kills/gold가 일치하지 않습니다.');
    }
  }
  list(finalSnapshot.players, `${finalPath}.players`, fail).forEach((playerValue, index) => {
    const path = `${finalPath}.players[${index}]`;
    const player = object(playerValue, path, fail);
    const resultPlayer = resultPlayers.get(String(player.playerId));
    if (!resultPlayer) fail(`${path}.playerId`, '최종 결과 선수를 찾을 수 없습니다.');
    FINAL_PLAYER_FIELDS.forEach((field) => {
      if (player[field] !== resultPlayer[field]) fail(`${path}.${field}`, '최종 선수 결과와 일치하지 않습니다.');
    });
  });
}
