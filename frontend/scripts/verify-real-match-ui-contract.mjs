import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, extname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const FRONTEND_DIR = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const FIXTURE_PATH = resolve(FRONTEND_DIR, 'src/features/real-match/reference/real-match-v8.reference.json');
const fixture = JSON.parse(readFileSync(FIXTURE_PATH, 'utf8'));
const invariant = (condition, message) => { if (!condition) throw new Error(`[real-match-ui-contract] ${message}`); };
const bySide = (values, side) => values.find((value) => value.teamSide === side);

function validSeed(value) {
  if (!/^(?:0|-[1-9]\d*|[1-9]\d*)$/.test(value)) return false;
  const parsed = BigInt(value);
  return parsed >= -(1n << 63n) && parsed <= (1n << 63n) - 1n;
}

const validSeeds = ['0', '73', '-1', '9223372036854775807', '-9223372036854775808'];
const invalidSeeds = ['01', '+73', '-0', ' 73 ', 'LM-A7K2Q9', 'TIMEOUT-73', '9223372036854775808', '-9223372036854775809'];
invariant(validSeeds.every(validSeed), 'signed int64 유효 seed 경계가 거부되었습니다.');
invariant(invalidSeeds.every((seed) => !validSeed(seed)), 'canonical signed int64 무효 seed가 허용되었습니다.');

const players = fixture.options.teams.flatMap((team) => team.lineup);
invariant(fixture.options.teams.length === 10 && players.length === 50 && new Set(players.map((player) => player.playerId)).size === 50, 'options 10팀/50명/stable ID 계약이 깨졌습니다.');
invariant(fixture.request.blueTeamCode === 'GEN' && fixture.request.redTeamCode === 'T1' && fixture.request.seed === '73', 'fixed request identity가 다릅니다.');
invariant(fixture.match.draft.decisions.length === 20 && fixture.match.draft.finalAssignments.length === 10, '자동 Draft cardinality가 다릅니다.');
const draftChampionIds = [...new Set(fixture.match.draft.decisions.map((decision) => decision.championId))];
invariant(fixture.presentation.draftChampions.length === draftChampionIds.length, 'Draft champion presentation cardinality가 다릅니다.');
for (const championId of draftChampionIds) {
  const champion = fixture.presentation.draftChampions.find((candidate) => candidate.championId === championId);
  invariant(champion?.displayNameKo && champion?.displayNameEn && champion?.portraitUrl.startsWith('https://ddragon.leagueoflegends.com/cdn/16.15.1/img/champion/'), `${championId} Draft 초상화 presentation이 완전하지 않습니다.`);
}
invariant([...fixture.match.draft.blueBans, ...fixture.match.draft.redBans].every((championId) => fixture.presentation.draftChampions.some((champion) => champion.championId === championId && champion.portraitUrl)), '밴 챔피언 초상화가 완전하지 않습니다.');
invariant(fixture.match.timeline.winner === fixture.match.result.winner, 'playback/result winner가 다릅니다.');
invariant(fixture.match.timeline.durationSeconds === fixture.match.result.durationSeconds, 'playback/result duration이 다릅니다.');
invariant(fixture.match.timeline.endReason === fixture.match.result.endReason, 'playback/result end reason이 다릅니다.');
invariant(fixture.match.integrity.outputHash === fixture.provenance.sourceOutputHash, 'projection/output hash 결속이 다릅니다.');

for (const assignment of fixture.match.draft.finalAssignments) {
  const team = bySide(fixture.match.teams, assignment.teamSide);
  const lineup = team?.lineup.find((player) => player.playerId === assignment.playerId);
  const result = fixture.match.result.players.find((player) => player.playerId === assignment.playerId);
  invariant(lineup?.position === assignment.position && lineup?.championId === assignment.championId, `${assignment.playerId} Draft/team assignment가 다릅니다.`);
  invariant(result?.position === assignment.position && result?.championId === assignment.championId, `${assignment.playerId} Draft/result assignment가 다릅니다.`);
  invariant(Object.keys(result.abilityProfile.baseRatings).length === 12 && Object.keys(result.abilityProfile.realizedRatings).length === 12 && Object.keys(result.abilityProfile.realizationDeltas).length === 12, `${assignment.playerId} ability profile 12개 rating이 완전하지 않습니다.`);
}

const finalSnapshot = fixture.match.timeline.snapshots.at(-1);
invariant(finalSnapshot.timeSeconds === 3430, '마지막 projection snapshot이 57:10이 아닙니다.');
for (const result of fixture.match.result.players) {
  const snapshot = finalSnapshot.players.find((player) => player.playerId === result.playerId);
  for (const key of ['teamSide', 'position', 'championId', 'kills', 'deaths', 'assists', 'cs', 'gold', 'totalExperience', 'level']) {
    invariant(snapshot?.[key] === result[key], `${result.playerId} final snapshot/result ${key}가 다릅니다.`);
  }
}

function sourceFiles(directory) {
  return readdirSync(directory).flatMap((name) => {
    const path = resolve(directory, name);
    return statSync(path).isDirectory() ? sourceFiles(path) : ['.ts', '.tsx'].includes(extname(path)) ? [path] : [];
  });
}
const realMatchSource = sourceFiles(resolve(FRONTEND_DIR, 'src/features/real-match'))
  .filter((path) => !path.endsWith('verify-real-match-ui-contract.mjs'))
  .map((path) => readFileSync(path, 'utf8')).join('\n');
for (const forbidden of ['Date.now(', 'Math.random(', "seed === 'TIMEOUT-73'", 'replaceDisplayNames(', '.description.includes(']) {
  invariant(!realMatchSource.includes(forbidden), `Real Match V1 source에 금지된 합성/문자열 파싱 경로가 남았습니다: ${forbidden}`);
}

console.log(`[real-match-ui-contract] OK teams=10 players=50 decisions=20 events=${fixture.provenance.includedEventCount}/${fixture.provenance.sourceEventCount} snapshots=${fixture.provenance.includedSnapshotCount}/${fixture.provenance.sourceSnapshotCount}`);
