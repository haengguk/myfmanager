import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';

const [beforeCsvPath, afterCsvPath, browserInputDirectory, outputDirectory] =
  process.argv.slice(2);
if (!beforeCsvPath || !afterCsvPath || !browserInputDirectory || !outputDirectory) {
  throw new Error('usage: node artifacts.mjs BEFORE.csv AFTER.csv BROWSER_INPUT_DIR OUTPUT_DIR');
}

const SCRIPT = ['garen', 'galio', 'gangplank', 'gragas', 'graves',
  'nami', 'gwen', 'gnar', 'nilah', 'diana'];
const SCRIPT_HASH = '3546272f10c63feb56ab19d1c64bd794f495d800facaa572a4c3a5958322954f';
const STATUS = 'PLAYER_DRAFT_PERFORMANCE_PARTIALLY_HARDENED';
const FILES = ['performance-contract.json', 'backend-before-after.csv',
  'browser-before-after.csv', 'summary.json', 'analysis.md'];

const text = async file => readFile(file, 'utf8');
const sha = bytes => createHash('sha256').update(bytes).digest('hex');
const rawSha = async file => sha(await readFile(file));
const csvRows = source => {
  const [header, ...lines] = source.trim().split(/\r?\n/);
  const keys = header.split(',');
  return lines.map(line => Object.fromEntries(line.split(',').map((value, index) =>
    [keys[index], value])));
};
const median = values => {
  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
};
const p90 = values => [...values].sort((a, b) => a - b)[Math.ceil(values.length * 0.9) - 1];
const improve = (before, after) => (1 - after / before) * 100;
const round = value => Number(value.toFixed(3));
const phaseValues = (rows, kind) => rows.filter(row => row.kind === kind)
  .map(row => Number(row.nanos) / 1_000_000);
const phaseSummary = (beforeRows, afterRows, kind) => {
  const before = phaseValues(beforeRows, kind);
  const after = phaseValues(afterRows, kind);
  return {
    beforeMedianMs: round(median(before)), afterMedianMs: round(median(after)),
    medianImprovementPercent: round(improve(median(before), median(after))),
    beforeP90Ms: round(p90(before)), afterP90Ms: round(p90(after)),
    p90ImprovementPercent: round(improve(p90(before), p90(after))),
    samplesPerPhase: before.length,
  };
};

const beforeSource = await text(beforeCsvPath);
const afterSource = await text(afterCsvPath);
const beforeRows = csvRows(beforeSource);
const afterRows = csvRows(afterSource);
for (const [phase, rows] of [['before', beforeRows], ['after', afterRows]]) {
  if (rows.length !== 132 || rows.some(row => row.phase !== phase
      || !Number.isFinite(Number(row.nanos)) || Number(row.nanos) < 0)) {
    throw new Error(`invalid backend ${phase} schedule`);
  }
}
for (const side of ['BLUE', 'RED']) {
  const identity = rows => new Set(rows.filter(row => row.side === side
      && row.kind === 'simulateFirst').map(row => [row.inputHash, row.outputHash,
    row.randomDrawCount, row.randomTraceHash].join('|')));
  const beforeIdentity = identity(beforeRows);
  const afterIdentity = identity(afterRows);
  if (beforeIdentity.size !== 1 || afterIdentity.size !== 1
      || [...beforeIdentity][0] !== [...afterIdentity][0]) {
    throw new Error(`backend parity drift: ${side}`);
  }
}

const browsers = [];
for (const phase of ['before', 'after']) {
  for (const side of ['blue', 'red']) {
    const file = path.join(browserInputDirectory, `browser-${phase}-${side}.json`);
    const value = JSON.parse(await text(file));
    const actions = value.rows.filter(row => row.operation === 'ACTION');
    const simulations = value.rows.filter(row => row.operation === 'SIMULATION');
    const correlations = new Set(value.rows.map(row => row.correlationId));
    const phases = ['totalToDomMs', 'fetchToHeadersMs', 'headersToBodyCompleteMs',
      'jsonParseMs', 'runtimeValidationMs', 'stateUpdateMs', 'stateToDomMs'];
    if (value.schemaVersion !== 'PLAYER_DRAFT_BROWSER_RUN_V1'
        || value.blueTeamCode !== 'GEN' || value.redTeamCode !== 'T1'
        || value.seed !== '73' || value.controlledSide !== side.toUpperCase()
        || JSON.stringify(value.actionScript) !== JSON.stringify(SCRIPT)
        || value.actionScriptHash !== SCRIPT_HASH || actions.length !== 10
        || simulations.length !== 1 || correlations.size !== 11
        || value.consoleErrors.length || value.pageErrors.length
        || value.referenceFallbackCount !== 0
        || value.rows.some(row => row.httpStatus !== 200
          || row.contentEncoding !== 'gzip' || row.decodedJsonBytes <= 0
          || row.encodedBodyBytes <= 0 || phases.some(key => row[key] == null
            || row[key] < 0))) {
      throw new Error(`invalid browser ${phase}/${side} input`);
    }
    browsers.push({ phase, side: side.toUpperCase(), file, value });
  }
}

const backend = Object.fromEntries(['inputValidation', 'simulateFirst', 'simulateRetry',
  'projection', 'actionService'].map(kind => [kind,
  phaseSummary(beforeRows, afterRows, kind)]));
const endpoint = phase => {
  const rows = phase === 'before' ? beforeRows : afterRows;
  const service = rows.filter(row => row.kind === 'actionService');
  const projection = new Map(rows.filter(row => row.kind === 'projection')
    .map(row => [`${row.runId}:${row.side}:${row.index}`, Number(row.nanos) / 1_000_000]));
  return service.map(row => Number(row.nanos) / 1_000_000
    + projection.get(`${row.runId}:${row.side}:${row.index}`));
};
const beforeEndpoint = endpoint('before');
const afterEndpoint = endpoint('after');
backend.actionRoute = {
  beforeMedianMs: round(median(beforeEndpoint)),
  afterMedianMs: round(median(afterEndpoint)),
  medianImprovementPercent: round(improve(median(beforeEndpoint), median(afterEndpoint))),
};
const lastAction = phaseRows => phaseRows.filter(row => row.kind === 'actionService'
  && row.index === '10').map(row => Number(row.nanos) / 1_000_000);
backend.lastAction = {
  beforeMedianMs: round(median(lastAction(beforeRows))),
  afterMedianMs: round(median(lastAction(afterRows))),
  regressionPercent: round((median(lastAction(afterRows)) / median(lastAction(beforeRows)) - 1) * 100),
};

const browserActions = phase => browsers.filter(run => run.phase === phase)
  .flatMap(run => run.value.rows.filter(row => row.operation === 'ACTION'));
const beforeActions = browserActions('before');
const afterActions = browserActions('after');
const actionBefore = beforeActions.map(row => row.totalToDomMs);
const actionAfter = afterActions.map(row => row.totalToDomMs);
const parse = rows => rows.map(row => row.jsonParseMs + row.runtimeValidationMs);
const render = rows => rows.map(row => row.stateToDomMs);
const browser = {
  actionDom: {
    beforeMedianMs: round(median(actionBefore)), afterMedianMs: round(median(actionAfter)),
    medianImprovementPercent: round(improve(median(actionBefore), median(actionAfter))),
    beforeP90Ms: round(p90(actionBefore)), afterP90Ms: round(p90(actionAfter)),
    p90ImprovementPercent: round(improve(p90(actionBefore), p90(actionAfter))),
  },
  parseAndValidationMedianMs: {
    before: round(median(parse(beforeActions))), after: round(median(parse(afterActions))),
  },
  renderMedianMs: {
    before: round(median(render(beforeActions))), after: round(median(render(afterActions))),
  },
  simulation: {}, errors: 0, referenceFallbacks: 0,
};
for (const side of ['BLUE', 'RED']) {
  const simulation = phase => browsers.find(run => run.phase === phase && run.side === side)
    .value.rows.find(row => row.operation === 'SIMULATION').totalToDomMs;
  const before = simulation('before');
  const after = simulation('after');
  browser.simulation[side] = { beforeMs: before, afterMs: after,
    improvementPercent: round(improve(before, after)) };
}

const binding = {};
for (const side of ['BLUE', 'RED']) {
  const row = beforeRows.find(value => value.side === side && value.kind === 'simulateFirst');
  binding[side] = { inputHash: row.inputHash, outputHash: row.outputHash,
    randomDrawCount: Number(row.randomDrawCount), randomTraceHash: row.randomTraceHash };
}
const collectorFiles = [
  path.resolve(browserInputDirectory,
    '../player-draft-interactive-simulation-latency-profiling-v1-inputs/player-draft-live-flow-blue.js'),
  path.resolve(browserInputDirectory,
    '../player-draft-interactive-simulation-latency-profiling-v1-inputs/player-draft-live-flow-red.js'),
  path.resolve(browserInputDirectory,
    '../player-draft-interactive-simulation-latency-profiling-v1-inputs/run-playwright-code.cjs'),
];
const collectorSha256 = Object.fromEntries(await Promise.all(collectorFiles.map(async file =>
  [path.basename(file), await rawSha(file)])));
const browserRawInputSha256 = Object.fromEntries(await Promise.all(browsers.map(async run =>
  [`${run.phase}-${run.side}`, await rawSha(run.file)])));
const contract = {
  schemaVersion: 'PLAYER_DRAFT_PERFORMANCE_HARDENING_CONTRACT_V1', status: STATUS,
  fixture: { blueTeamCode: 'GEN', redTeamCode: 'T1', seed: '73',
    controlledSides: ['BLUE', 'RED'], actionScript: SCRIPT, actionScriptHash: SCRIPT_HASH },
  source: { reviewBaselineCommit: process.env.LOLMANAGER_REVIEW_HEAD ?? 'UNKNOWN',
    finalSourceIdentity: process.env.LOLMANAGER_FINAL_SOURCE_IDENTITY ?? 'UNKNOWN',
    runtimeResources: 'SAME_CURRENT_LOCAL_AUTHORED_RESOURCES_FOR_BEFORE_AND_AFTER' },
  schedule: { backend: 'one unmeasured warmup plus BLUE/RED two measured sequential flows',
    browser: 'fresh backend boot per side; Chromium cache disabled; ACTION 10 + SIMULATION 1' },
  phaseSemantics: {
    actionService: 'PlayerDraftApiV1Service.action only',
    actionRoute: 'action service plus SessionResponse projection',
    inputValidation: 'trusted boundary input creation before Production V9',
    simulateFirst: 'fresh Production V9 plus compact receipt store',
    simulateRetry: 'fresh Production V9 plus compact receipt equality',
    browserDom: 'confirm/click to stable Draft or Playback DOM',
  },
  acceptance: { inputValidationPercent: 70, simulateFirstPercent: 45,
    simulateRetryPercent: 35, projectionPercent: 40, actionServicePercent: 25,
    actionDomMedianPercent: 20, actionDomP90NoRegression: true,
    simulationEachSidePercent: 30, lastActionMaxRegressionPercent: 10 },
  canonicalResultBinding: binding, collectorSha256, browserRawInputSha256,
  priorProfilingManifestSha256: '3009861416762f3ee61a56624d3cd1762bac0ab889d2f6a8092404c40e820d46',
};
const gates = {
  inputValidation: backend.inputValidation.medianImprovementPercent >= 70,
  simulateFirst: backend.simulateFirst.medianImprovementPercent >= 45,
  simulateRetry: backend.simulateRetry.medianImprovementPercent >= 35,
  projection: backend.projection.medianImprovementPercent >= 40,
  actionService: backend.actionService.medianImprovementPercent >= 25,
  lastAction: backend.lastAction.regressionPercent <= 10,
  actionDomMedian: browser.actionDom.medianImprovementPercent >= 20,
  actionDomP90: browser.actionDom.afterP90Ms <= browser.actionDom.beforeP90Ms,
  blueSimulation: browser.simulation.BLUE.improvementPercent >= 30,
  redSimulation: browser.simulation.RED.improvementPercent >= 30,
  browserErrorsAndFallbacks: browser.errors === 0 && browser.referenceFallbacks === 0,
};
const summary = { schemaVersion: 'PLAYER_DRAFT_PERFORMANCE_HARDENING_SUMMARY_V1',
  status: STATUS, backend, browser, gates,
  allTargetsPassed: Object.values(gates).every(Boolean),
  unmetTargets: Object.entries(gates).filter(([, passed]) => !passed).map(([name]) => name),
};

const backendCsv = `${beforeSource.trim()}\n${afterSource.trim().split(/\r?\n/).slice(1).join('\n')}\n`;
const browserHeader = 'phase,side,operation,actionIndex,playerTurn,actionType,aiDecisionCount,'
  + 'totalToDomMs,fetchToHeadersMs,headersToBodyCompleteMs,jsonParseMs,runtimeValidationMs,'
  + 'stateUpdateMs,stateToDomMs,httpStatus,contentEncoding,encodedBodyBytes,decodedJsonBytes,'
  + 'correlationId,actionScriptHash,inputHash,outputHash\n';
let browserCsv = browserHeader;
for (const run of browsers) for (const row of run.value.rows) {
  const bound = binding[run.side];
  browserCsv += [run.phase, run.side, row.operation, row.actionIndex ?? '',
    row.playerTurn ?? '', row.actionType ?? '', row.aiDecisionCount ?? '', row.totalToDomMs,
    row.fetchToHeadersMs, row.headersToBodyCompleteMs, row.jsonParseMs,
    row.runtimeValidationMs, row.stateUpdateMs, row.stateToDomMs, row.httpStatus,
    row.contentEncoding, row.encodedBodyBytes, row.decodedJsonBytes, row.correlationId,
    SCRIPT_HASH, bound.inputHash, bound.outputHash].join(',') + '\n';
}
const analysis = `# Player Draft Interactive and Simulation Performance Hardening V1\n\n`
  + `Status: \`${STATUS}\`\n\n`
  + `동일한 GEN–T1/73, BLUE/RED, 10-action script로 paired 측정했다. completed Draft의 `
  + `20턴 authoritative search/view 재생은 server-owned completion binding의 경량 검증으로 `
  + `대체했고, active revision의 legal/unavailable/advisory projection은 한 번 계산해 action, GET, `
  + `mapper와 exact replay가 공유한다. 검증을 삭제하지 않았고 binding/result/context/hash가 조금이라도 `
  + `달라지면 Match Engine 실행 전에 거부한다.\n\n`
  + `Backend input validation 중앙값은 ${backend.inputValidation.beforeMedianMs}ms → `
  + `${backend.inputValidation.afterMedianMs}ms (${backend.inputValidation.medianImprovementPercent}% 감소), `
  + `first simulate는 ${backend.simulateFirst.beforeMedianMs}ms → ${backend.simulateFirst.afterMedianMs}ms `
  + `(${backend.simulateFirst.medianImprovementPercent}% 감소), retry는 `
  + `${backend.simulateRetry.beforeMedianMs}ms → ${backend.simulateRetry.afterMedianMs}ms `
  + `(${backend.simulateRetry.medianImprovementPercent}% 감소)였다. response projection은 `
  + `${backend.projection.medianImprovementPercent}% 감소했다.\n\n`
  + `Actual Chromium action DOM 중앙값은 ${browser.actionDom.beforeMedianMs}ms → `
  + `${browser.actionDom.afterMedianMs}ms (${browser.actionDom.medianImprovementPercent}% 감소), `
  + `p90은 ${browser.actionDom.beforeP90Ms}ms → ${browser.actionDom.afterP90Ms}ms `
  + `(${browser.actionDom.p90ImprovementPercent}% 감소)였다. simulate는 BLUE `
  + `${browser.simulation.BLUE.beforeMs}ms → ${browser.simulation.BLUE.afterMs}ms, RED `
  + `${browser.simulation.RED.beforeMs}ms → ${browser.simulation.RED.afterMs}ms였다. `
  + `console/page/runtime error와 reference fallback은 0이다.\n\n`
  + `단독 actionService 중앙값 개선은 ${backend.actionService.medianImprovementPercent}%로 25% gate를 `
  + `통과하지 못했다. action route와 실제 DOM은 projection 중복 제거 효과로 목표를 통과했지만, `
  + `남은 AI follow-up/search 비용 때문에 milestone 상태는 partial이다. 후보 수, search depth, scoring, `
  + `Random 순서를 바꿔 숫자를 맞추지 않았다.\n`;

await mkdir(outputDirectory, { recursive: true });
const pretty = value => `${JSON.stringify(value, null, 2)}\n`;
await writeFile(path.join(outputDirectory, FILES[0]), pretty(contract));
await writeFile(path.join(outputDirectory, FILES[1]), backendCsv);
await writeFile(path.join(outputDirectory, FILES[2]), browserCsv);
await writeFile(path.join(outputDirectory, FILES[3]), pretty(summary));
await writeFile(path.join(outputDirectory, FILES[4]), analysis);
const sums = [];
for (const file of FILES) sums.push(`${await rawSha(path.join(outputDirectory, file))}  ${file}`);
await writeFile(path.join(outputDirectory, 'SHA256SUMS.txt'), `${sums.join('\n')}\n`);
console.log(`${STATUS} output=${path.resolve(outputDirectory)}`);
