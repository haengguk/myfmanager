import assert from 'node:assert/strict';
import { LeagueApiFailure } from '../src/features/league/api/leagueApi.failure.ts';
import { LeagueCompletionReconciler, selectLeagueCompletionCandidate } from '../src/features/league/leagueCompletionReconciliation.ts';

const H = 'a'.repeat(64);
const target = {
  leagueId: `league_${'1'.repeat(64)}`,
  seasonId: `season_${'2'.repeat(64)}`,
  fixtureId: `fixture_${'3'.repeat(64)}`,
  bindingHash: H,
  expectedRevision: 4,
};

function completion(scope, applied, allowReplay = !applied) {
  return {
    schemaVersion: 'AI_LEAGUE_COMPLETION_STATUS_VIEW_V1',
    replayed: false,
    completion: {
      leagueId: scope.leagueId,
      seasonId: scope.seasonId,
      fixtureId: scope.fixtureId,
      fixtureStatus: applied ? 'COMPLETED' : 'COMPLETION_PENDING_VERIFICATION',
      bindingStatus: applied ? 'VERIFIED' : 'COMPLETION_PENDING_VERIFICATION',
      receiptHash: H,
      outboxStatus: applied ? 'DELIVERED' : 'PENDING',
      standingsApplied: applied,
      standingsRevision: applied ? 1 : 0,
      allowedCommands: allowReplay ? ['VIEW_FIXTURE', 'RECONCILE_PLAYER_SERIES_COMPLETION'] : ['VIEW_FIXTURE'],
    },
  };
}

function harness(overrides = {}) {
  let command = overrides.initialCommand ?? null;
  let ids = 0;
  const evidence = { posts: [], gets: 0, refreshes: 0, clears: 0, saves: 0, waits: 0, transitions: [], recoverable: [], terminal: 0, notFound: 0, applied: 0 };
  const dependencies = {
    readCommand: () => command,
    saveCommand: (next) => { command = next; evidence.saves += 1; },
    clearCommand: (expected) => { if (command?.clientCommandId === expected.clientCommandId) { command = null; evidence.clears += 1; } },
    postCompletion: async (scope, next, signal) => { evidence.posts.push(next.clientCommandId); return (overrides.postCompletion ?? (async () => completion(scope, true)))(scope, next, signal, evidence.posts.length); },
    getCompletion: async (scope, signal) => { evidence.gets += 1; return (overrides.getCompletion ?? (async () => completion(scope, true)))(scope, signal, evidence.gets); },
    refreshAuthoritativeViews: async (signal) => { evidence.refreshes += 1; return overrides.refreshAuthoritativeViews?.(signal, evidence.refreshes); },
    wait: async (milliseconds, signal) => { evidence.waits += 1; if (overrides.wait) return overrides.wait(milliseconds, signal, evidence.waits); if (signal.aborted) throw signal.reason; },
    isVisible: () => overrides.isVisible?.() ?? true,
    createCommandId: overrides.createCommandId ?? (() => `completion-command-${++ids}`),
    onStateChange: (snapshot) => evidence.transitions.push(snapshot.state),
    onRecoverableFailure: (failure, exhausted) => evidence.recoverable.push([failure.kind, exhausted]),
    onTerminalFailure: () => { evidence.terminal += 1; },
    onNotFound: () => { evidence.notFound += 1; },
    onApplied: () => { evidence.applied += 1; },
  };
  return { dependencies, evidence, command: () => command, idCount: () => ids };
}

async function scenario(label, action) {
  try { await action(); console.log(`PASS ${label}`); }
  catch (error) { console.error(`FAIL ${label}`, error); process.exitCode = 1; }
}

await scenario('completion-ready fixture posts once, polls, refreshes, then clears command', async () => {
  const h = harness({
    postCompletion: async (scope) => completion(scope, false, false),
    getCompletion: async (scope, _signal, count) => completion(scope, count >= 2),
  });
  const result = await new LeagueCompletionReconciler({ pollDelays: [0, 0], retryDelays: [] }).reconcile(target, 'AUTO', h.dependencies);
  assert.equal(result, 'APPLIED'); assert.equal(h.evidence.posts.length, 1); assert.equal(h.evidence.gets, 2); assert.equal(h.evidence.refreshes, 1); assert.equal(h.command(), null);
});

await scenario('response loss checks authoritative applied state before another POST', async () => {
  const h = harness({ postCompletion: async () => { throw new LeagueApiFailure('NETWORK', 'lost'); }, getCompletion: async (scope) => completion(scope, true) });
  const result = await new LeagueCompletionReconciler({ pollDelays: [], retryDelays: [0] }).reconcile(target, 'AUTO', h.dependencies);
  assert.equal(result, 'APPLIED'); assert.equal(h.evidence.posts.length, 1); assert.equal(h.evidence.gets, 1); assert.equal(h.idCount(), 1);
});

await scenario('POST timeout exact-replays with the same completion UUID', async () => {
  const h = harness({
    postCompletion: async (scope, _command, _signal, count) => { if (count === 1) throw new LeagueApiFailure('TIMEOUT', 'timeout'); return completion(scope, true); },
    getCompletion: async (scope) => completion(scope, false, true),
  });
  const result = await new LeagueCompletionReconciler({ pollDelays: [], retryDelays: [0] }).reconcile(target, 'AUTO', h.dependencies);
  assert.equal(result, 'APPLIED'); assert.deepEqual(h.evidence.posts, ['completion-command-1', 'completion-command-1']); assert.equal(h.idCount(), 1);
});

await scenario('polling timeout preserves the command and manual retry resumes it', async () => {
  let applied = false;
  const h = harness({ postCompletion: async (scope) => completion(scope, false, false), getCompletion: async (scope) => completion(scope, applied, false) });
  const owner = new LeagueCompletionReconciler({ pollDelays: [0], retryDelays: [] });
  assert.equal(await owner.reconcile(target, 'AUTO', h.dependencies), 'RETRY_PENDING');
  const savedId = h.command().clientCommandId; applied = true;
  assert.equal(await owner.reconcile(target, 'MANUAL', h.dependencies), 'APPLIED');
  assert.equal(h.evidence.posts.length, 1); assert.equal(savedId, 'completion-command-1'); assert.equal(h.idCount(), 1);
});

await scenario('retryable 503 retries with one UUID and bounded backoff', async () => {
  const h = harness({
    postCompletion: async (scope, _command, _signal, count) => { if (count === 1) throw new LeagueApiFailure('BACKEND', 'worker', 503, 'LEAGUE_TEMPORARILY_UNAVAILABLE', null, true); return completion(scope, true); },
    getCompletion: async (scope) => completion(scope, false, true),
  });
  assert.equal(await new LeagueCompletionReconciler({ pollDelays: [], retryDelays: [0] }).reconcile(target, 'AUTO', h.dependencies), 'APPLIED');
  assert.equal(new Set(h.evidence.posts).size, 1); assert.equal(h.evidence.waits, 1);
});

await scenario('non-retryable payload conflict stops the automatic loop', async () => {
  const h = harness({ postCompletion: async () => { throw new LeagueApiFailure('BACKEND', 'conflict', 409, 'LEAGUE_COMMAND_ID_PAYLOAD_CONFLICT'); } });
  const owner = new LeagueCompletionReconciler({ pollDelays: [], retryDelays: [0, 0] });
  assert.equal(await owner.reconcile(target, 'AUTO', h.dependencies), 'TERMINAL_FAILURE');
  assert.equal(await owner.reconcile(target, 'AUTO', h.dependencies), 'NO_OP');
  assert.equal(h.evidence.posts.length, 1); assert.equal(h.evidence.waits, 0); assert.equal(h.evidence.terminal, 1); assert.equal(h.command(), null);
});

await scenario('candidate skipped by another pending operation starts after pending clears', async () => {
  const h = harness(); const owner = new LeagueCompletionReconciler({ pollDelays: [], retryDelays: [] });
  assert.equal(await owner.reconcileIfAvailable(target, 'AUTO', 'REFRESH', h.dependencies), 'NO_OP'); assert.equal(h.evidence.posts.length, 0);
  assert.equal(await owner.reconcileIfAvailable(target, 'AUTO', null, h.dependencies), 'APPLIED'); assert.equal(h.evidence.posts.length, 1);
});

await scenario('automatic and manual triggers share one in-flight POST', async () => {
  let release; const deferred = new Promise((resolve) => { release = resolve; });
  const h = harness({ postCompletion: async () => deferred }); const owner = new LeagueCompletionReconciler({ pollDelays: [], retryDelays: [] });
  const automatic = owner.reconcile(target, 'AUTO', h.dependencies); const manual = owner.reconcile(target, 'MANUAL', h.dependencies);
  assert.equal(automatic, manual); assert.equal(h.evidence.posts.length, 1); release(completion(target, true));
  assert.equal(await automatic, 'APPLIED'); assert.equal(await manual, 'APPLIED');
});

async function concurrentTargetEvidence(nextTarget) {
  let release;
  const blocked = new Promise((resolve) => { release = resolve; });
  const first = harness({
    createCommandId: () => 'target-a-command',
    postCompletion: async () => blocked,
  });
  const second = harness({ createCommandId: () => 'target-b-command' });
  const owner = new LeagueCompletionReconciler({ pollDelays: [], retryDelays: [] });
  const firstResult = owner.reconcile(target, 'AUTO', first.dependencies);
  const secondResult = owner.reconcile(nextTarget, 'MANUAL', second.dependencies);
  release(completion(target, true));
  return { first, second, firstResult, secondResult };
}

await scenario('different fixture queues and evaluates its own authoritative dependency', async () => {
  const next = { ...target, fixtureId: `fixture_${'4'.repeat(64)}`, bindingHash: 'b'.repeat(64), expectedRevision: 5 };
  const evidence = await concurrentTargetEvidence(next);
  assert.notEqual(evidence.firstResult, evidence.secondResult);
  assert.equal(await evidence.firstResult, 'APPLIED'); assert.equal(await evidence.secondResult, 'APPLIED');
  assert.deepEqual(evidence.first.evidence.posts, ['target-a-command']);
  assert.deepEqual(evidence.second.evidence.posts, ['target-b-command']);
  assert.equal(evidence.first.evidence.clears, 1); assert.equal(evidence.second.evidence.clears, 1);
  assert.equal(evidence.first.command(), null); assert.equal(evidence.second.command(), null);
});

await scenario('same fixture with a different binding never shares the active result', async () => {
  const next = { ...target, bindingHash: 'b'.repeat(64), expectedRevision: 5 };
  const evidence = await concurrentTargetEvidence(next);
  assert.notEqual(evidence.firstResult, evidence.secondResult);
  assert.equal(await evidence.firstResult, 'APPLIED'); assert.equal(await evidence.secondResult, 'APPLIED');
  assert.equal(evidence.second.evidence.posts.length, 1);
});

await scenario('same fixture and binding with a different effective revision is independent', async () => {
  const next = { ...target, expectedRevision: target.expectedRevision + 1 };
  const evidence = await concurrentTargetEvidence(next);
  assert.notEqual(evidence.firstResult, evidence.secondResult);
  assert.equal(await evidence.firstResult, 'APPLIED'); assert.equal(await evidence.secondResult, 'APPLIED');
  assert.equal(evidence.second.evidence.posts.length, 1);
});

await scenario('queued target runs after the active target exhausts retry', async () => {
  const first = harness({ postCompletion: async () => { throw new LeagueApiFailure('NETWORK', 'offline'); } });
  const second = harness(); const owner = new LeagueCompletionReconciler({ pollDelays: [], retryDelays: [] });
  const firstResult = owner.reconcile(target, 'AUTO', first.dependencies);
  const next = { ...target, fixtureId: `fixture_${'5'.repeat(64)}`, bindingHash: 'c'.repeat(64), expectedRevision: 6 };
  const secondResult = owner.reconcile(next, 'AUTO', second.dependencies);
  assert.equal(await firstResult, 'RETRY_PENDING'); assert.equal(await secondResult, 'APPLIED');
  assert.equal(second.evidence.posts.length, 1);
});

await scenario('queued target runs after the active target reaches terminal failure', async () => {
  const first = harness({ postCompletion: async () => { throw new LeagueApiFailure('BACKEND', 'conflict', 409, 'LEAGUE_COMMAND_ID_PAYLOAD_CONFLICT'); } });
  const second = harness(); const owner = new LeagueCompletionReconciler({ pollDelays: [], retryDelays: [] });
  const firstResult = owner.reconcile(target, 'AUTO', first.dependencies);
  const next = { ...target, fixtureId: `fixture_${'6'.repeat(64)}`, bindingHash: 'd'.repeat(64), expectedRevision: 7 };
  const secondResult = owner.reconcile(next, 'AUTO', second.dependencies);
  assert.equal(await firstResult, 'TERMINAL_FAILURE'); assert.equal(await secondResult, 'APPLIED');
  assert.equal(second.evidence.posts.length, 1);
});

await scenario('abort clears queued work and a later remount can evaluate that target', async () => {
  const first = harness({
    postCompletion: async (_scope, _command, signal) => new Promise((_resolve, reject) => {
      signal.addEventListener('abort', () => reject(signal.reason), { once: true });
    }),
  });
  const second = harness(); const owner = new LeagueCompletionReconciler({ pollDelays: [], retryDelays: [] });
  const firstResult = owner.reconcile(target, 'AUTO', first.dependencies);
  const next = { ...target, fixtureId: `fixture_${'7'.repeat(64)}`, bindingHash: 'e'.repeat(64), expectedRevision: 8 };
  const queuedResult = owner.reconcile(next, 'AUTO', second.dependencies);
  owner.abort();
  assert.equal(await firstResult, 'ABORTED'); assert.equal(await queuedResult, 'ABORTED');
  assert.equal(second.evidence.posts.length, 0);
  assert.equal(await owner.reconcile(next, 'AUTO', second.dependencies), 'APPLIED');
  assert.equal(second.evidence.posts.length, 1);
});

await scenario('unmount aborts retry wait without another request or timer', async () => {
  const h = harness({
    postCompletion: async () => { throw new LeagueApiFailure('NETWORK', 'offline'); },
    wait: async (_milliseconds, signal) => new Promise((resolve, reject) => { signal.addEventListener('abort', () => reject(signal.reason), { once: true }); }),
  });
  const owner = new LeagueCompletionReconciler({ pollDelays: [], retryDelays: [1000] }); const running = owner.reconcile(target, 'AUTO', h.dependencies);
  await new Promise((resolve) => setTimeout(resolve, 0)); owner.abort();
  assert.equal(await running, 'ABORTED'); assert.equal(h.evidence.posts.length, 1); assert.equal(owner.current().state, 'ABORTED');
});

await scenario('remount with a saved command GETs applied state and sends no POST', async () => {
  const existing = { kind: 'COMPLETE_PLAYER_SERIES', scopeKey: target.fixtureId, clientCommandId: 'saved-command', expectedRevision: target.expectedRevision, bindingHash: target.bindingHash };
  const h = harness({ initialCommand: existing, getCompletion: async (scope) => completion(scope, true) });
  assert.equal(await new LeagueCompletionReconciler({ pollDelays: [], retryDelays: [] }).reconcile(target, 'AUTO', h.dependencies), 'APPLIED');
  assert.equal(h.evidence.posts.length, 0); assert.equal(h.evidence.gets, 1); assert.equal(h.idCount(), 0); assert.equal(h.command(), null);
});

await scenario('remount candidate survives removal of the advertised reconcile command', async () => {
  const existing = { kind: 'COMPLETE_PLAYER_SERIES', scopeKey: target.fixtureId, clientCommandId: 'saved-command', expectedRevision: target.expectedRevision, bindingHash: target.bindingHash };
  const fixture = { fixtureId: target.fixtureId, bindingHash: target.bindingHash, allowedCommands: ['VIEW_FIXTURE'] };
  assert.equal(selectLeagueCompletionCandidate([fixture], existing), fixture);
  assert.equal(selectLeagueCompletionCandidate([fixture], null), undefined);
});

await scenario('scope or binding change never reuses a stale completion command', async () => {
  const existing = { kind: 'COMPLETE_PLAYER_SERIES', scopeKey: target.fixtureId, clientCommandId: 'stale-command', expectedRevision: target.expectedRevision, bindingHash: target.bindingHash };
  const next = { ...target, bindingHash: 'b'.repeat(64), expectedRevision: 5 };
  const h = harness({ initialCommand: existing });
  assert.equal(await new LeagueCompletionReconciler({ pollDelays: [], retryDelays: [] }).reconcile(next, 'MANUAL', h.dependencies), 'APPLIED');
  assert.deepEqual(h.evidence.posts, ['completion-command-1']); assert.equal(h.idCount(), 1);
});

await scenario('after one fixture applies, the next fixture starts independently', async () => {
  const h = harness(); const owner = new LeagueCompletionReconciler({ pollDelays: [], retryDelays: [] });
  assert.equal(await owner.reconcile(target, 'AUTO', h.dependencies), 'APPLIED');
  const next = { ...target, fixtureId: `fixture_${'4'.repeat(64)}`, bindingHash: 'b'.repeat(64), expectedRevision: 5 };
  assert.equal(await owner.reconcile(next, 'AUTO', h.dependencies), 'APPLIED');
  assert.equal(h.evidence.posts.length, 2); assert.notEqual(h.evidence.posts[0], h.evidence.posts[1]);
});

await scenario('NOT_FOUND clears only the matching completion command and reports recovery', async () => {
  const h = harness({ postCompletion: async () => { throw new LeagueApiFailure('BACKEND', 'missing', 404, 'LEAGUE_FIXTURE_NOT_FOUND'); } });
  assert.equal(await new LeagueCompletionReconciler({ pollDelays: [], retryDelays: [] }).reconcile(target, 'AUTO', h.dependencies), 'TERMINAL_FAILURE');
  assert.equal(h.evidence.notFound, 1); assert.equal(h.command(), null);
});

if (!process.exitCode) console.log('AI_LEAGUE_COMPLETION_RECOVERY_VERIFICATION_PASSED');
