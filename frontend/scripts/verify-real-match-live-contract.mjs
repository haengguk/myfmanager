import { readFileSync, readdirSync, statSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { dirname, extname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';

const FRONTEND_DIR = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const REPOSITORY_DIR = resolve(FRONTEND_DIR, '..');
const HANDOFF_DIR = process.env.LOLMANAGER_REAL_MATCH_HANDOFF_DIR
  ? resolve(REPOSITORY_DIR, process.env.LOLMANAGER_REAL_MATCH_HANDOFF_DIR)
  : null;
const OPTIONS_PATH = resolve(
  REPOSITORY_DIR,
  process.env.LOLMANAGER_REAL_MATCH_OPTIONS_PATH
    ?? (HANDOFF_DIR ? resolve(HANDOFF_DIR, 'real-match-api-v1-options-example.json')
      : 'backend/build/reports/real-match-api-v1/real-match-api-v1-options-example.json'),
);
const RESPONSE_PATH = resolve(
  REPOSITORY_DIR,
  process.env.LOLMANAGER_REAL_MATCH_RESPONSE_PATH
    ?? (HANDOFF_DIR ? resolve(HANDOFF_DIR, 'real-match-api-v1-fixed-response.json')
      : 'backend/build/reports/real-match-api-v1/real-match-api-v1-fixed-response.json'),
);
const invariant = (condition, message) => { if (!condition) throw new Error(`[real-match-live-contract] ${message}`); };
const readSource = (path) => readFileSync(resolve(FRONTEND_DIR, path), 'utf8');
const rawSha256 = (bytes) => createHash('sha256').update(bytes).digest('hex');
const EXPECTED_V9 = {
  policyId: 'MATCH_ENGINE_V1_MATCHUP_COMPOSITION_ACCEPTED_PRODUCTION_POLICY',
  policyHash: '78c3bb1cffe2cd90a1f7acab6923a1813fea40acd135186ff522eabf95d38493',
  runtimeProfileId: 'PRODUCTION_MATCHUP_COMPOSITION_V1',
  configurationHash: 'caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d',
  engineImplementationVersion: 'MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9',
};

function parseArtifact(bytes, path) {
  try { return JSON.parse(bytes.toString('utf8')); }
  catch (error) {
    const summary = error instanceof Error ? error.message : String(error);
    throw new Error(`[real-match-live-contract] JSON을 해석할 수 없습니다: ${path} (${summary})`);
  }
}

function preflightV9(options, response) {
  const optionPolicy = options?.productionPolicy;
  const responseIntegrity = response?.integrity;
  const mismatches = [
    ['options policyId', optionPolicy?.policyId, EXPECTED_V9.policyId],
    ['options policyHash', optionPolicy?.policyHash, EXPECTED_V9.policyHash],
    ['options runtimeProfileId', optionPolicy?.runtimeProfileId, EXPECTED_V9.runtimeProfileId],
    ['options configurationHash', optionPolicy?.configurationHash, EXPECTED_V9.configurationHash],
    ['options engineImplementationVersion', optionPolicy?.engineImplementationVersion, EXPECTED_V9.engineImplementationVersion],
    ['response policyId', responseIntegrity?.policyId, EXPECTED_V9.policyId],
    ['response policyHash', responseIntegrity?.policyHash, EXPECTED_V9.policyHash],
    ['response runtimeProfileId', responseIntegrity?.runtimeProfileId, EXPECTED_V9.runtimeProfileId],
    ['response configurationHash', responseIntegrity?.configurationHash, EXPECTED_V9.configurationHash],
    ['response engineImplementationVersion', responseIntegrity?.engineImplementationVersion, EXPECTED_V9.engineImplementationVersion],
  ].filter(([, actual, expected]) => actual !== expected);
  if (!mismatches.length) return;
  const summary = mismatches.map(([label, actual, expected]) => `${label}: ${String(actual)} (expected ${expected})`).join('; ');
  throw new Error(
    `[real-match-live-contract] 현재 Production V9 handoff가 아닙니다. ${summary}. `
      + '기존 V8/BASELINE artifact를 승격하지 말고 LOLMANAGER_REAL_MATCH_HANDOFF_DIR 또는 '
      + 'LOLMANAGER_REAL_MATCH_OPTIONS_PATH/LOLMANAGER_REAL_MATCH_RESPONSE_PATH로 검증할 V9 입력을 명시하세요.',
  );
}

async function loadRuntimeContract() {
  const bundled = await build({
    stdin: {
      contents: [
        "export { RealMatchContractError, validateRealMatchOptionsPayload, validateRealMatchResponsePayload } from './src/features/real-match/api/realMatchApi.validation.ts';",
        "export { createLiveMatchSetupOptions, createLiveMatchSession } from './src/features/real-match/liveMatchSession.adapter.ts';",
        "export { validateCanonicalSignedInt64Seed } from './src/features/real-match/seedValidation.ts';",
      ].join('\n'),
      loader: 'ts',
      resolveDir: FRONTEND_DIR,
      sourcefile: 'real-match-live-contract-entry.ts',
    },
    bundle: true,
    format: 'esm',
    platform: 'node',
    target: 'node20',
    write: false,
  });
  const source = bundled.outputFiles[0].text;
  try {
    return await import(
      `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
    );
  } catch (error) {
    const summary =
      error instanceof Error ? `${error.name}: ${error.message}` : String(error);
    throw new Error(
      `[real-match-live-contract] runtime bundle import failed: ${summary}`,
    );
  }
}

const runtime = await loadRuntimeContract();
const optionsBytes = readFileSync(OPTIONS_PATH); const responseBytes = readFileSync(RESPONSE_PATH);
const optionsSha256 = rawSha256(optionsBytes); const responseSha256 = rawSha256(responseBytes);
const optionsPayload = parseArtifact(optionsBytes, OPTIONS_PATH);
const responsePayload = parseArtifact(responseBytes, RESPONSE_PATH);
preflightV9(optionsPayload, responsePayload);
const request = {
  schemaVersion: 'REAL_MATCH_SIMULATE_REQUEST_V1',
  blueTeamCode: 'GEN',
  redTeamCode: 'T1',
  seed: '73',
};

let options; let response;
try {
  options = runtime.validateRealMatchOptionsPayload(optionsPayload);
  response = runtime.validateRealMatchResponsePayload(responsePayload, request);
} catch (error) {
  const summary = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
  throw new Error(`[real-match-live-contract] V9 artifact 계약 검증 실패: ${summary}. options=${OPTIONS_PATH} response=${RESPONSE_PATH}`);
}
const setupOptions = runtime.createLiveMatchSetupOptions(options);
const selection = {
  blueTeamId: request.blueTeamCode,
  redTeamId: request.redTeamCode,
  seed: request.seed,
  gameNumber: setupOptions.gameNumber,
  seriesType: setupOptions.seriesType,
  draftMode: 'AUTO',
  controlledSide: 'BLUE',
};
const session = runtime.createLiveMatchSession(response, setupOptions, selection, {
  payloadBytes: statSync(RESPONSE_PATH).size,
  requestAndDownloadMs: 0,
  jsonParseMs: 0,
  runtimeValidationMs: 0,
  requestStartedAt: 0,
});
const expectedFixedResult = {
  events: response.timeline.events.length,
  snapshots: response.timeline.snapshots.length,
  winner: response.timeline.winner,
  durationSeconds: response.timeline.durationSeconds,
  runtimeProfile: EXPECTED_V9.runtimeProfileId,
};

invariant(options.teams.length === 10, '실제 Options validator가 10개 팀을 확인하지 못했습니다.');
invariant(options.teams.flatMap((team) => team.lineup).length === 50, '실제 Options validator 결과가 50명이 아닙니다.');
invariant(session.source === 'LIVE', '실제 adapter가 LIVE session을 만들지 않았습니다.');
invariant(session.selectedTeams.BLUE.code === 'GEN' && session.selectedTeams.RED.code === 'T1', '실제 adapter의 선택 팀 identity가 다릅니다.');
invariant(session.draft.decisions.length === 20 && session.draft.rosters.BLUE.length === 5 && session.draft.rosters.RED.length === 5, '실제 adapter의 Draft 구성이 완전하지 않습니다.');
invariant(session.playback.events.length === expectedFixedResult.events && session.playback.snapshots.length === expectedFixedResult.snapshots, '실제 adapter의 전체 timeline 구성이 다릅니다.');
invariant(session.playback.winner === expectedFixedResult.winner && session.result.winner === expectedFixedResult.winner, '실제 adapter의 고정 승자가 다릅니다.');
invariant(session.playback.durationSeconds === expectedFixedResult.durationSeconds && session.result.durationSeconds === expectedFixedResult.durationSeconds, '실제 adapter의 경기 시간이 다릅니다.');
invariant(session.result.integrity.runtimeProfile === expectedFixedResult.runtimeProfile, '실제 adapter의 runtime profile이 고정 응답과 다릅니다.');
invariant(!Object.hasOwn(session, 'response') && !Object.hasOwn(session, 'rawResponse'), '정규화 session이 raw 응답을 보유합니다.');

function expectContractFailure(label, payload, payloadRequest = request) {
  let failure = null;
  try {
    runtime.validateRealMatchResponsePayload(payload, payloadRequest);
  } catch (error) {
    failure = error;
  }
  invariant(failure instanceof runtime.RealMatchContractError, `${label} 변형을 실제 Response validator가 거부하지 않았습니다.`);
}

function mutateResponse(label, mutate) {
  const payload = structuredClone(responsePayload);
  mutate(payload);
  expectContractFailure(label, payload);
}

const duplicateOptionsPlayer = structuredClone(optionsPayload);
duplicateOptionsPlayer.teams[1].lineup[0].playerId = duplicateOptionsPlayer.teams[0].lineup[0].playerId;
let duplicateOptionsFailure = null;
try {
  runtime.validateRealMatchOptionsPayload(duplicateOptionsPlayer);
} catch (error) {
  duplicateOptionsFailure = error;
}
invariant(duplicateOptionsFailure instanceof runtime.RealMatchContractError, '중복 Options player ID를 실제 validator가 거부하지 않았습니다.');

mutateResponse('잘못된 schemaVersion', (payload) => { payload.schemaVersion = 'REAL_MATCH_RESPONSE_V0'; });
mutateResponse('Draft decision champion 중복', (payload) => { payload.draft.decisions[1].championId = payload.draft.decisions[0].championId; });
mutateResponse('Draft decision/list 불일치', (payload) => { payload.draft.blueBans[0] = payload.draft.redBans[0]; });
mutateResponse('최종 배치/pick 불일치', (payload) => { payload.draft.finalAssignments[0].championId = payload.draft.redPicks[0]; });
mutateResponse('snapshot team identity 불일치', (payload) => { payload.timeline.snapshots[0].blueTeam.teamIdentity = 'T1'; });
mutateResponse('필수 integrity hash 누락', (payload) => { delete payload.integrity.outputHash; });
mutateResponse('지원하지 않는 event enum', (payload) => { payload.timeline.events[0].eventType = 'UNKNOWN_EVENT'; });
mutateResponse('알 수 없는 structured participant', (payload) => {
  const event = payload.timeline.events.find((candidate) => candidate.killerPlayerId !== null);
  event.killerPlayerId = 'player-unknown';
});
mutateResponse('ability profile rating 불일치', (payload) => {
  const profile = payload.result.players[0].abilityProfile;
  const key = Object.keys(profile.realizedRatings)[0];
  profile.realizedRatings[key] += 1;
});

const validSeeds = ['0', '73', '-1', '9223372036854775807', '-9223372036854775808'];
const invalidSeeds = ['01', '+73', '-0', ' 73 ', 'LM-A7K2Q9', 'TIMEOUT-73', '9223372036854775808', '-9223372036854775809'];
invariant(validSeeds.every((seed) => runtime.validateCanonicalSignedInt64Seed(seed) === null), '실제 seed validator가 signed int64 유효 경계를 거부했습니다.');
invariant(invalidSeeds.every((seed) => runtime.validateCanonicalSignedInt64Seed(seed) !== null), '실제 seed validator가 canonical signed int64 무효 값을 허용했습니다.');

const rootApp = readSource('src/RootApp.tsx');
const dataSource = readSource('src/features/real-match/matchDataSource.ts');
const config = readSource('src/features/real-match/realMatch.config.ts');
const client = readSource('src/features/real-match/api/realMatchApi.client.ts');
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

function sourceFiles(directory) {
  return readdirSync(directory).flatMap((name) => {
    const path = resolve(directory, name);
    return statSync(path).isDirectory() ? sourceFiles(path) : ['.ts', '.tsx'].includes(extname(path)) ? [path] : [];
  });
}
const liveSourceFiles = sourceFiles(resolve(FRONTEND_DIR, 'src'))
  .filter((path) => {
    const sourcePath = relative(FRONTEND_DIR, path).replaceAll('\\', '/');
    return !sourcePath.includes('/reference/') && !sourcePath.endsWith('/matchSession.adapter.ts');
  });
for (const path of liveSourceFiles) {
  const source = readFileSync(path, 'utf8');
  invariant(!source.includes('real-match-v8.reference.json'), `LIVE source가 reference JSON을 직접 참조합니다: ${relative(FRONTEND_DIR, path)}`);
}

console.log(`[real-match-live-contract] OK actual-validator=true actual-adapter=true teams=10 players=50 decisions=20 assignments=10 events=${session.playback.events.length} snapshots=${session.playback.snapshots.length} fixed=${session.playback.winner}/${session.playback.durationSeconds}s mutations=10`);
console.log(`[real-match-live-contract] INPUT options=${OPTIONS_PATH} sha256=${optionsSha256}`);
console.log(`[real-match-live-contract] INPUT response=${RESPONSE_PATH} sha256=${responseSha256}`);
console.log(`[real-match-live-contract] V9 policy=${EXPECTED_V9.policyHash} profile=${response.integrity.runtimeProfileId} engine=${response.integrity.engineImplementationVersion}`);
