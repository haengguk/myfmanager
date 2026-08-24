import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const FRONTEND_DIR = resolve(SCRIPT_DIR, '..');
const REPORT_DIR = resolve(FRONTEND_DIR, '../backend/build/reports/real-match-api-v1');
const OUTPUT_PATH = resolve(FRONTEND_DIR, 'src/features/real-match/reference/real-match-v8.reference.json');
const CHAMPION_CATALOG_PATH = resolve(FRONTEND_DIR, '../backend/src/main/resources/champions/champion-pool-full-173-2026-08-v1.json');

const EXPECTED_MANIFEST_SHA = 'fc4f96158d6c6b1d6e9b30d8441da89a2643f9d25faa8e7218434b49b4909525';
const EXPECTED_OUTPUT_HASH = 'bdc597af083aa4f081cf4fe7a242d0e36eec7744b186d998d6f83b717648e874';
const EXPECTED_ENGINE = 'MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V8';
const EXPECTED_CONFIGURATION = 'c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215';
const EXPECTED_TEAM_CODES = ['BFX', 'BRO', 'DK', 'DNS', 'GEN', 'HLE', 'KRX', 'KT', 'NS', 'T1'];
const EXPECTED_MANIFEST_FILES = [
  'real-match-api-v1-contract.json',
  'real-match-api-v1-options-example.json',
  'real-match-api-v1-fixed-request.json',
  'real-match-api-v1-fixed-response.json',
  'real-match-api-v1-error-contract.json',
  'real-match-api-v1-handoff.json',
];

const INCLUDED_EVENT_TYPES = new Set([
  'GAME_START', 'KILL', 'ASSIST', 'JUNGLE_GANK', 'COUNTER_GANK', 'LANE_COMBAT', 'ROAM', 'SHUTDOWN',
  'DRAGON', 'BARON', 'ELDER', 'TOWER', 'TEAMFIGHT', 'TEAMFIGHT_RESULT', 'ACE', 'MATCH_PHASE_CHANGE',
  'MACRO_ACTION', 'LATE_GAME_ACTION', 'GAME_END',
]);

const sha256 = (bytes) => createHash('sha256').update(bytes).digest('hex');
const invariant = (condition, message) => {
  if (!condition) throw new Error(`[real-match-reference] ${message}`);
};
const readJson = (name) => JSON.parse(readFileSync(resolve(REPORT_DIR, name), 'utf8'));

function verifyManifest() {
  const manifestBytes = readFileSync(resolve(REPORT_DIR, 'SHA256SUMS.txt'));
  invariant(sha256(manifestBytes) === EXPECTED_MANIFEST_SHA, 'SHA256SUMS.txt raw SHA가 승인된 V8 값과 다릅니다.');

  const entries = manifestBytes.toString('utf8').trim().split(/\r?\n/).map((line) => {
    const match = line.match(/^([a-f0-9]{64})\s{2}(.+)$/);
    invariant(match, `manifest 행을 해석할 수 없습니다: ${line}`);
    return { hash: match[1], name: match[2] };
  });
  invariant(entries.length === EXPECTED_MANIFEST_FILES.length, 'manifest JSON 항목 수가 6개가 아닙니다.');
  invariant(entries.every((entry, index) => entry.name === EXPECTED_MANIFEST_FILES[index]), 'manifest 파일 순서가 승인 계약과 다릅니다.');
  for (const entry of entries) {
    invariant(sha256(readFileSync(resolve(REPORT_DIR, entry.name))) === entry.hash, `${entry.name} raw SHA가 manifest와 다릅니다.`);
  }
}

function verifyOptions(options) {
  invariant(options.schemaVersion === 'REAL_MATCH_OPTIONS_V1', 'options schema가 REAL_MATCH_OPTIONS_V1이 아닙니다.');
  invariant(options.productionPolicy.engineImplementationVersion === EXPECTED_ENGINE, 'options engine이 V8이 아닙니다.');
  invariant(options.productionPolicy.configurationHash === EXPECTED_CONFIGURATION, 'options configuration hash가 다릅니다.');
  invariant(options.teams.length === 10, 'options 팀 수가 10개가 아닙니다.');
  invariant(options.teams.every((team) => team.lineup.length === 5), '팀당 lineup이 5명이 아닙니다.');
  invariant(JSON.stringify(options.teams.map((team) => team.teamCode)) === JSON.stringify(EXPECTED_TEAM_CODES), 'canonical team ordering이 다릅니다.');
  const playerIds = options.teams.flatMap((team) => team.lineup.map((player) => player.playerId));
  invariant(playerIds.length === 50 && new Set(playerIds).size === 50, 'stable PlayerId가 50개 unique가 아닙니다.');
  const t1Adc = options.teams.find((team) => team.teamCode === 'T1')?.lineup.find((player) => player.position === 'ADC');
  invariant(t1Adc?.playerId === 'player-peyz' && t1Adc.nickname === 'Peyz', 'T1 ADC identity가 player-peyz/Peyz가 아닙니다.');
}

function verifyReferenceIdentity(request, response, handoff) {
  invariant(request.schemaVersion === 'REAL_MATCH_SIMULATE_REQUEST_V1', 'fixed request schema가 다릅니다.');
  invariant(request.blueTeamCode === 'GEN' && request.redTeamCode === 'T1' && request.seed === '73', 'fixed request가 GEN/T1/73이 아닙니다.');
  invariant(handoff.productionPolicy.engineImplementationVersion === EXPECTED_ENGINE, 'handoff engine이 V8이 아닙니다.');
  invariant(handoff.fixedFixture.outputHash === EXPECTED_OUTPUT_HASH, 'handoff output hash가 승인 값과 다릅니다.');
  invariant(response.schemaVersion === 'REAL_MATCH_RESPONSE_V1', 'fixed response schema가 다릅니다.');
  invariant(response.matchIdentity === 'REAL_DRAFT:GEN:T1:GAME:1:SEED:73', 'match identity가 승인 경기와 다릅니다.');
  invariant(response.seed === '73', 'response seed가 73이 아닙니다.');
  invariant(response.integrity.engineImplementationVersion === EXPECTED_ENGINE, 'response engine이 V8이 아닙니다.');
  invariant(response.integrity.configurationHash === EXPECTED_CONFIGURATION, 'response configuration hash가 다릅니다.');
  invariant(response.integrity.outputHash === EXPECTED_OUTPUT_HASH, 'response output hash가 승인 값과 다릅니다.');
  invariant(response.draft.seriesGameNumber === 1, 'Draft가 fresh Game 1이 아닙니다.');
  invariant(response.draft.hardFearlessExclusionsBeforeDraft.length === 0, 'fresh Game 1에 Fearless exclusion이 존재합니다.');
  invariant(response.draft.decisions.length === 20 && response.draft.finalAssignments.length === 10, 'Draft decision/assignment cardinality가 다릅니다.');
  invariant(response.result.winner === 'BLUE' && response.timeline.winner === 'BLUE', '승자가 BLUE/GEN이 아닙니다.');
  invariant(response.result.endReason === 'NEXUS_DESTROYED' && response.timeline.endReason === 'NEXUS_DESTROYED', '종료 사유가 NEXUS_DESTROYED가 아닙니다.');
  invariant(response.result.durationSeconds === 3430 && response.timeline.durationSeconds === 3430, '경기 시간이 3,430초가 아닙니다.');
  invariant(response.result.players.length === 10 && response.result.players.every((player) => player.abilityProfile?.schemaVersion === 'PLAYER_ABILITY_PROFILE_V1'), '10명 V8 ability profile이 완전하지 않습니다.');
  const finalSnapshot = response.timeline.snapshots.at(-1);
  invariant(finalSnapshot?.timeSeconds === 3430, '마지막 snapshot이 3,430초가 아닙니다.');
  for (const player of response.result.players) {
    const finalPlayer = finalSnapshot.players.find((candidate) => candidate.playerId === player.playerId);
    invariant(finalPlayer, `마지막 snapshot에 ${player.playerId}가 없습니다.`);
    for (const key of ['teamSide', 'position', 'championId', 'kills', 'deaths', 'assists', 'cs', 'gold', 'totalExperience', 'level']) {
      invariant(finalPlayer[key] === player[key], `${player.playerId} final snapshot/result ${key}가 다릅니다.`);
    }
  }
}

function projectEvent(event, index) {
  return {
    projectionId: `event-${String(index + 1).padStart(3, '0')}`,
    timeSeconds: event.timeSeconds,
    eventType: event.eventType,
    actorSide: event.actorSide,
    actorPosition: event.actorPosition,
    lane: event.lane,
    actorPlayerId: event.actorPlayerId,
    killerPlayerId: event.killerPlayerId,
    victimPlayerId: event.victimPlayerId,
    assistantPlayerIds: event.assistantPlayerIds,
    killerChampionId: event.killerChampionId,
    victimChampionId: event.victimChampionId,
    assistantChampionIds: event.assistantChampionIds,
    combatSource: event.combatSource,
    structureActionSource: event.structureActionSource,
    structureKind: event.structureKind,
    structureTowerTier: event.structureTowerTier,
    structureAttackingSide: event.structureAttackingSide,
    structureDefendingSide: event.structureDefendingSide,
    goldAmount: event.goldAmount,
    actionId: event.actionId,
    parentActionId: event.parentActionId,
    displayMessage: event.displayMessage,
  };
}

function projectTeamState(team) {
  return {
    teamIdentity: team.teamIdentity,
    teamSide: team.teamSide,
    kills: team.kills,
    gold: team.gold,
    towersDestroyed: team.towersDestroyed,
    dragons: team.dragons,
    inhibitorsRemaining: team.inhibitorsRemaining,
    nexusTurretsRemaining: team.nexusTurretsRemaining,
    nexusAlive: team.nexusAlive,
    alivePlayers: team.alivePlayers,
    hasBaronBuff: team.hasBaronBuff,
    hasDragonSoul: team.hasDragonSoul,
    hasElderBuff: team.hasElderBuff,
  };
}

function projectPlayerState(player) {
  return {
    playerId: player.playerId,
    teamSide: player.teamSide,
    position: player.position,
    championId: player.championId,
    kills: player.kills,
    deaths: player.deaths,
    assists: player.assists,
    cs: player.cs,
    gold: player.gold,
    totalExperience: player.totalExperience,
    level: player.level,
    alive: player.alive,
    respawnRemainingSeconds: player.respawnRemainingSeconds,
    canFarm: player.canFarm,
    farmReturnSecondsRemaining: player.farmReturnSecondsRemaining,
    activityType: player.activityType,
    activityOriginLane: player.activityOriginLane,
    activityTargetLane: player.activityTargetLane,
  };
}

function projectDraftChampionCatalog(options, response, championCatalog) {
  const expectedCatalogIdentity = `pool=${championCatalog.championPoolVersion};balance=${championCatalog.championBalanceVersion};riot=${championCatalog.riotDataVersion}`;
  invariant(options.resourceVersions.versions.CHAMPION_CATALOG === expectedCatalogIdentity, 'Draft champion presentation catalog이 handoff resource version과 다릅니다.');

  const catalogById = new Map(championCatalog.champions.map((champion) => [champion.id, champion]));
  const championIds = [...new Set(response.draft.decisions.map((decision) => decision.championId))];
  const presentations = championIds.map((championId) => {
    const champion = catalogById.get(championId);
    invariant(champion, `Draft champion catalog에 ${championId}가 없습니다.`);
    return {
      championId,
      displayNameKo: champion.displayNameKo,
      displayNameEn: champion.displayNameEn,
      portraitUrl: `https://ddragon.leagueoflegends.com/cdn/${championCatalog.riotDataVersion}/img/champion/${champion.riotAssetId}.png`,
    };
  });

  for (const team of response.teams) {
    for (const player of team.lineup) {
      const presentation = presentations.find((champion) => champion.championId === player.championId);
      invariant(presentation, `${player.championId} final pick presentation이 없습니다.`);
      invariant(
        presentation.displayNameKo === player.champion.displayNameKo
          && presentation.displayNameEn === player.champion.displayNameEn
          && presentation.portraitUrl === player.champion.portraitUrl,
        `${player.championId} catalog/response presentation이 다릅니다.`,
      );
    }
  }
  return presentations;
}

function createProjection(options, request, response, handoff, championCatalog) {
  const events = response.timeline.events.filter((event) => INCLUDED_EVENT_TYPES.has(event.eventType));
  const snapshots = response.timeline.snapshots.filter((snapshot, index, all) => (
    index === 0 || index === all.length - 1 || snapshot.timeSeconds % 60 === 0
  ));
  return {
    projectionSchemaVersion: 'REAL_MATCH_V8_REFERENCE_PROJECTION_V1',
    provenance: {
      sourceHandoffSchemaVersion: handoff.schemaVersion,
      sourceResponseSchemaVersion: response.schemaVersion,
      sourceManifestRawSha256: EXPECTED_MANIFEST_SHA,
      sourceOutputHash: EXPECTED_OUTPUT_HASH,
      engineImplementationVersion: EXPECTED_ENGINE,
      sourceFullResponseBytes: statSync(resolve(REPORT_DIR, 'real-match-api-v1-fixed-response.json')).size,
      sourceEventCount: response.timeline.events.length,
      includedEventCount: events.length,
      sourceSnapshotCount: response.timeline.snapshots.length,
      includedSnapshotCount: snapshots.length,
      eventSelectionPolicy: 'ALL_STRUCTURED_GAMEPLAY_EVENTS_EXCEPT_LEVEL_UP_AND_ITEM_STAGE_REACHED_V1',
      snapshotSelectionPolicy: 'FIRST_LAST_AND_EXACT_60_SECOND_SNAPSHOTS_V1',
      referenceLabel: 'V8 Reference Fixture · 로컬 승인 응답 기준',
    },
    presentation: {
      draftChampions: projectDraftChampionCatalog(options, response, championCatalog),
    },
    options: {
      schemaVersion: options.schemaVersion,
      matchEngineContract: options.matchEngineContract,
      seedPolicy: options.seedPolicy,
      productionPolicy: options.productionPolicy,
      resourceVersions: options.resourceVersions,
      teams: options.teams,
    },
    request,
    match: {
      matchIdentity: response.matchIdentity,
      seed: response.seed,
      teams: response.teams,
      draft: response.draft,
      result: response.result,
      timeline: {
        schemaVersion: response.timeline.schemaVersion,
        winner: response.timeline.winner,
        endReason: response.timeline.endReason,
        durationSeconds: response.timeline.durationSeconds,
        events: events.map(projectEvent),
        snapshots: snapshots.map((snapshot) => ({
          timeSeconds: snapshot.timeSeconds,
          blueTeam: projectTeamState(snapshot.blueTeam),
          redTeam: projectTeamState(snapshot.redTeam),
          players: snapshot.players.map(projectPlayerState),
        })),
      },
      integrity: response.integrity,
    },
  };
}

verifyManifest();
const options = readJson('real-match-api-v1-options-example.json');
const request = readJson('real-match-api-v1-fixed-request.json');
const response = readJson('real-match-api-v1-fixed-response.json');
const handoff = readJson('real-match-api-v1-handoff.json');
const championCatalog = JSON.parse(readFileSync(CHAMPION_CATALOG_PATH, 'utf8'));
verifyOptions(options);
verifyReferenceIdentity(request, response, handoff);
const output = `${JSON.stringify(createProjection(options, request, response, handoff, championCatalog), null, 2)}\n`;

if (process.argv.includes('--check')) {
  invariant(readFileSync(OUTPUT_PATH, 'utf8') === output, 'checked-in compact projection이 현재 승인 artifact와 byte-identical하지 않습니다.');
  console.log(`[real-match-reference] OK ${sha256(Buffer.from(output))} (${Buffer.byteLength(output)} bytes)`);
} else {
  mkdirSync(dirname(OUTPUT_PATH), { recursive: true });
  writeFileSync(OUTPUT_PATH, output, 'utf8');
  console.log(`[real-match-reference] generated ${OUTPUT_PATH}`);
  console.log(`[real-match-reference] SHA-256 ${sha256(Buffer.from(output))} (${Buffer.byteLength(output)} bytes)`);
}
