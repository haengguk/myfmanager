import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, extname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const FRONTEND_DIR = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const REPOSITORY_DIR = resolve(FRONTEND_DIR, '..');
const OPTIONS_PATH = resolve(REPOSITORY_DIR, 'backend/build/reports/real-match-api-v1/real-match-api-v1-options-example.json');
const RESPONSE_PATH = resolve(REPOSITORY_DIR, 'backend/build/reports/real-match-api-v1/real-match-api-v1-fixed-response.json');
const invariant = (condition, message) => { if (!condition) throw new Error(`[real-match-live-contract] ${message}`); };
const readSource = (path) => readFileSync(resolve(FRONTEND_DIR, path), 'utf8');

const options = JSON.parse(readFileSync(OPTIONS_PATH, 'utf8'));
const response = JSON.parse(readFileSync(RESPONSE_PATH, 'utf8'));

invariant(options.schemaVersion === 'REAL_MATCH_OPTIONS_V1', 'Options schemaVersion이 다릅니다.');
invariant(options.teams.length === 10, 'Options 팀 수가 10이 아닙니다.');
const optionPlayers = options.teams.flatMap((team) => team.lineup);
invariant(optionPlayers.length === 50, 'Options 선수 수가 50이 아닙니다.');
invariant(new Set(options.teams.map((team) => team.teamCode)).size === 10, 'Options team code가 중복됩니다.');
invariant(new Set(optionPlayers.map((player) => player.playerId)).size === 50, 'Options player ID가 중복됩니다.');
for (const team of options.teams) {
  invariant(new Set(team.lineup.map((player) => player.position)).size === 5, `${team.teamCode} 포지션 구성이 완전하지 않습니다.`);
}

invariant(response.schemaVersion === 'REAL_MATCH_RESPONSE_V1', 'Response schemaVersion이 다릅니다.');
invariant(response.seed === '73', '고정 응답 seed가 73이 아닙니다.');
invariant(response.teams.find((team) => team.teamSide === 'BLUE')?.teamCode === 'GEN', '고정 BLUE 팀이 GEN이 아닙니다.');
invariant(response.teams.find((team) => team.teamSide === 'RED')?.teamCode === 'T1', '고정 RED 팀이 T1이 아닙니다.');
invariant(response.draft.decisions.length === 20, '자동 Draft decision이 20개가 아닙니다.');
invariant(response.draft.finalAssignments.length === 10, '최종 champion assignment가 10개가 아닙니다.');
invariant(response.timeline.events.length === 517, '고정 응답 timeline event가 517개가 아닙니다.');
invariant(response.timeline.snapshots.length === 344, '고정 응답 timeline snapshot이 344개가 아닙니다.');
invariant(response.timeline.winner === 'BLUE' && response.result.winner === 'BLUE', '고정 응답 승자가 BLUE가 아닙니다.');
invariant(response.timeline.durationSeconds === 3430 && response.result.durationSeconds === 3430, '고정 응답 경기 시간이 3430초가 아닙니다.');
invariant(response.timeline.snapshots.at(-1)?.timeSeconds === response.result.durationSeconds, 'final snapshot 시간이 result와 다릅니다.');

const rootApp = readSource('src/RootApp.tsx');
const dataSource = readSource('src/features/real-match/matchDataSource.ts');
const config = readSource('src/features/real-match/realMatch.config.ts');
const client = readSource('src/features/real-match/api/realMatchApi.client.ts');
const validator = readSource('src/features/real-match/api/realMatchApi.validation.ts');
invariant(!rootApp.includes("from './features/real-match/matchSession.adapter'"), 'RootApp이 reference adapter를 eager import합니다.');
invariant(dataSource.includes("import('./matchSession.adapter')"), 'reference adapter가 dynamic import되지 않습니다.');
invariant(config.includes("configuredSource === 'reference' ? 'REFERENCE' : 'LIVE'"), '기본 data source가 LIVE가 아닙니다.');
invariant(client.includes('/api/v1/real-matches/options') && client.includes('/api/v1/real-matches/simulate'), 'LIVE endpoint 연결이 누락됐습니다.');
for (const failureKind of ['NETWORK', 'CANCELLED', 'TIMEOUT', 'BACKEND', 'INVALID_JSON', 'CONTRACT']) {
  invariant(client.includes(`'${failureKind}'`), `${failureKind} 오류 구분이 누락됐습니다.`);
}
invariant(client.includes('new AbortController()'), 'API AbortController 처리가 누락됐습니다.');
invariant(rootApp.includes('matchRequestSequenceRef') && rootApp.includes('controller.signal.aborted'), 'Simulate stale response 차단이 누락됐습니다.');
invariant(!dataSource.includes('catch') && !client.includes('matchSession.adapter'), 'LIVE 실패에 reference fallback 경로가 존재합니다.');
for (const field of ['schemaVersion', 'blueTeamCode', 'redTeamCode', 'seed']) {
  invariant(dataSource.includes(field), `simulate request ${field}가 누락됐습니다.`);
}
for (const stage of ['CONNECTING', 'DOWNLOADING', 'PARSING', 'VALIDATING', 'NORMALIZING']) {
  invariant(rootApp.concat(client, dataSource).includes(stage), `${stage} 요청 단계가 누락됐습니다.`);
}
for (const contract of ['REAL_MATCH_OPTIONS_V1', 'REAL_MATCH_RESPONSE_V1', 'REAL_MATCH_API_ERROR_V1']) {
  invariant(validator.includes(contract), `${contract} runtime validation이 누락됐습니다.`);
}

function sourceFiles(directory) {
  return readdirSync(directory).flatMap((name) => {
    const path = resolve(directory, name);
    return statSync(path).isDirectory() ? sourceFiles(path) : ['.ts', '.tsx'].includes(extname(path)) ? [path] : [];
  });
}
const liveSourceFiles = sourceFiles(resolve(FRONTEND_DIR, 'src'))
  .filter((path) => !path.includes('/reference/') && !path.endsWith('/matchSession.adapter.ts'));
for (const path of liveSourceFiles) {
  const source = readFileSync(path, 'utf8');
  invariant(!source.includes('real-match-v8.reference.json'), `LIVE source가 reference JSON을 직접 참조합니다: ${relative(FRONTEND_DIR, path)}`);
}

console.log(`[real-match-live-contract] OK teams=10 players=50 decisions=20 assignments=10 events=${response.timeline.events.length} snapshots=${response.timeline.snapshots.length} fixed=${response.timeline.winner}/${response.timeline.durationSeconds}s`);
