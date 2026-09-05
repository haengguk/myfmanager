import { spawnSync } from 'node:child_process';
import { view, summary, hardenedCalendarView, competitionCommandResponse } from './verify-career-contract.mjs';

// Run through the existing Playwright CLI session and Vite server. No engine/server jobs are simulated.
// PLAYWRIGHT_CLI=/path/to/playwright_cli.sh PLAYWRIGHT_CLI_SESSION=... node --experimental-strip-types scripts/verify-career-request-isolation.mjs
const fixtures = { view: view(), summary: summary(), calendar: hardenedCalendarView(),
  auto: competitionCommandResponse('FULL_AUTO'), player: competitionCommandResponse() };

async function verifyScreen(page, data) {
  const origin = await page.evaluate(() => location.origin);
  const pageErrors = [];
  page.on('pageerror', error => pageErrors.push(error.message));
  await page.route('**/__career_request_verifier', route => route.fulfill({ contentType: 'text/html', body: `
    <html><head><title>Career request isolation</title></head><body><div id="root"></div>
    <script type="module">
    import RefreshRuntime from '/@react-refresh';
    RefreshRuntime.injectIntoGlobalHook(window); window.$RefreshReg$ = () => {}; window.$RefreshSig$ = () => type => type;
    window.__vite_plugin_react_preamble_installed__ = true;
    </script><script type="module">
    import React from '/node_modules/.vite/deps/react.js';
    import ReactDOM from '/node_modules/.vite/deps/react-dom_client.js';
    import {CareerDashboardPage} from '/src/features/career/CareerDashboardPage.tsx';
    window.mountCareer = () => ReactDOM.createRoot(document.getElementById('root')).render(React.createElement(CareerDashboardPage,
      {searchValue:'', onResume:()=>{}, onOpenCompetitionSeries:(...args)=>window.careerTest.navigation.push(args),
       onNotify:(...args)=>window.careerTest.notifications.push(args)}));
    </script></body></html>` }));
  const A = data.view.careerId;
  const B = `career_${'2'.repeat(64)}`;
  const state = () => page.evaluate(() => ({ text: document.querySelector('.ca-detail')?.textContent,
    notices: window.careerTest.notifications.length, navigation: window.careerTest.navigation.length,
    storage: sessionStorage.getItem('lolmanager.career.competition-operations.v1'),
    posts: window.careerTest.posts, aborted: window.careerTest.deferred.map(value => value.signal.aborted) }));
  const select = async label => {
    await page.locator('.ca-saves button').filter({ hasText: `Career ${label}` }).click();
    await page.locator('.ca-detail h2').filter({ hasText: `Career ${label}` }).waitFor();
    await page.locator('.ca-calendar__competition button').waitFor();
  };
  const start = () => page.locator('.ca-calendar__competition button').click();
  const hold = (careerId, kind) => page.evaluate(value => { window.careerTest.hold = value; }, { careerId, kind });
  const deferred = count => page.waitForFunction(value => window.careerTest.deferred.length >= value, count);
  const release = (index, failure = false) => page.evaluate(({ index, failure }) => {
    const request = window.careerTest.deferred[index];
    if (failure) request.reject(new Error('controlled late failure'));
    else request.resolve(new Response(JSON.stringify(request.body), { status: request.status }));
  }, { index, failure });
  const flush = () => page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  async function setup(mode = 'FULL_AUTO') {
    await page.goto(`${origin}/__career_request_verifier`);
    await page.waitForFunction(() => typeof window.mountCareer === 'function');
    await page.evaluate(({ data, mode, A, B }) => {
      sessionStorage.clear();
      const t = window.careerTest = { posts: [], deferred: [], notifications: [], navigation: [], hold: null, pending: {}, mode };
      const copy = value => structuredClone(value);
      const career = id => ({ ...copy(data.view), careerId: id, saveName: id === A ? 'Career A' : 'Career B' });
      const calendar = id => {
        const value = copy(data.calendar); value.careerId = id;
        value.competition.nextFixture.executionMode = mode;
        value.competition.nextFixture.managedTeamIncluded = mode === 'PLAYER_CONTROLLED';
        value.competition.allowedCommands = [t.pending[id] ? 'RECONCILE_COMPETITION_FIXTURE'
          : mode === 'FULL_AUTO' ? 'DISPATCH_AUTO_COMPETITION_FIXTURE' : 'START_PLAYER_COMPETITION_SERIES'];
        value.competition.activePendingCommand = t.pending[id] ?? null;
        return value;
      };
      const codes = ['BFX', 'BRO', 'DK', 'DNS', 'GEN', 'HLE', 'KRX', 'KT', 'NS', 'T1'];
      const teams = { schemaVersion: 'TEAM_PLAYER_INFORMATION_TEAMS_V1', leagueCode: 'LCK',
        catalog: { catalogSchemaVersion: 'TEAM_AND_PLAYER_INFORMATION_CATALOG_V1', catalogVersion: 'catalog-v1',
          catalogHashAlgorithm: 'SHA-256', catalogHash: 'a'.repeat(64), championPoolVersion: 'pool-v1',
          sourceResources: ['PLAYER_IDENTITY', 'PLAYER_RATING', 'CHAMPION_PROFICIENCY', 'PLAYER_CAREER'].map((role, i) => ({ role, version: `resource-${i}`, rawSha256: String(i + 1).repeat(64), snapshotAt: i === 3 ? '2026-08-24' : null, researchAsOf: null, dataCutoff: null })) },
        teams: codes.map(teamCode => ({ teamCode, starterCount: 5, lineup: ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'].map(position => ({ playerId: `player-${teamCode.toLowerCase()}-${position.toLowerCase()}`, nickname: `${teamCode}-${position}`, position })) })) };
      // Intentionally ignore abort in the transport: the page must also reject stale identities.
      window.fetch = async (url, init = {}) => {
        const path = new URL(url, location.href).pathname;
        const id = path.includes(A) ? A : path.includes(B) ? B : null;
        const kind = init.method === 'POST' ? 'command' : path.endsWith('/calendar') ? 'calendar' : id ? 'detail' : 'list';
        let body; let status = 200;
        if (path.endsWith('/teams')) body = teams;
        else if (kind === 'command') {
          const payload = JSON.parse(init.body); t.posts.push({ careerId: id, ...payload });
          t.pending[id] = { clientCommandId: payload.clientCommandId, competitionId: 'LCK_CUP', matchId: data.auto.matchId, commandStatus: 'RUNNING' };
          body = copy(mode === 'FULL_AUTO' ? data.auto : data.player); status = 202;
        } else if (kind === 'calendar') body = calendar(id);
        else if (kind === 'detail') body = career(id);
        else body = { schemaVersion: 'CAREER_LIST_V1', careers: [A, B].map(id => ({ ...copy(data.summary), careerId: id, saveName: id === A ? 'Career A' : 'Career B' })), currentCount: 2, maximumCount: 100, remainingCount: 98 };
        if (t.hold?.careerId === id && t.hold.kind === kind) {
          t.hold = null;
          return new Promise((resolve, reject) => t.deferred.push({ resolve, reject, signal: init.signal, body, status }));
        }
        return new Response(JSON.stringify(body), { status });
      };
      window.mountCareer();
    }, { data, mode, A, B });
    await page.locator('.ca-detail h2').filter({ hasText: 'Career A' }).waitFor();
    await page.locator('.ca-calendar__competition button').waitFor();
  }
  function check(value, message) { if (!value) throw new Error(message); }
  const passed = [];

  await setup(); await hold(A, 'command'); await start(); await deferred(1); await select('B');
  await release(0); await flush();
  let result = await state();
  check(result.text.includes('Career B') && result.aborted[0] && !result.notices && !result.navigation, 'late Auto response changed B');
  const original = result.posts[0].clientCommandId;
  check(result.storage.includes(original), 'switch cleared the original Auto UUID');
  await select('A'); await hold(A, 'command'); await start(); await deferred(2);
  result = await state(); check(result.posts[1].clientCommandId === original, 'return did not reuse server pending UUID');
  passed.push('late Auto start ignored; A return recovers original durable command');

  await setup(); await hold(A, 'calendar'); await start(); await deferred(1); await select('B');
  await page.evaluate(() => { window.careerTest.deferred[0].body.competition.revision = 1; window.careerTest.deferred[0].body.competition.activePendingCommand = null; });
  await release(0); await flush(); result = await state();
  check(result.text.includes('Career B') && result.storage.includes(result.posts[0].clientCommandId) && !result.notices, 'late poll polluted B or A recovery');
  passed.push('late polling Calendar cannot overwrite B or clear A recovery');

  await setup('PLAYER_CONTROLLED'); await hold(A, 'command'); await start(); await deferred(1); await select('B');
  await select('A'); await hold(A, 'command'); await start(); await deferred(2); await release(0); await flush();
  result = await state(); check(!result.navigation && await page.locator('.ca-calendar__competition button').isDisabled(), 'same Career new generation accepted old Player navigation/finally');
  await release(1); await flush(); result = await state(); check(result.navigation === 1, 'current Player response failed to navigate');
  passed.push('A → B → A rejects old Player navigation/finally; current response navigates');

  for (const failure of [false, true]) {
    await setup(); await hold(A, 'command'); await start(); await deferred(1); await select('B');
    await hold(B, 'command'); await start(); await deferred(2);
    if (!failure) await page.evaluate(() => { const r = window.careerTest.deferred[0]; r.body.status = 'COMPLETED'; r.body.backgroundAccepted = false; r.status = 200; });
    await release(0, failure); await flush(); result = await state();
    check(result.text.includes('Career B') && !result.notices && !result.navigation && result.storage.includes(result.posts[0].clientCommandId)
      && await page.locator('.ca-calendar__competition button').isDisabled(), 'old success/error/finally corrupted B pending state');
    passed.push(failure ? 'late failure leaves B pending/error and both UUIDs unchanged' : 'late completion cannot refresh old Career or notify/clear B pending');
  }

  await setup(); await hold(A, 'detail'); await select('B');
  // Start a detail request to A, then return to B before that detail arrives.
  await page.locator('.ca-saves button').filter({ hasText: 'Career A' }).click(); await deferred(1);
  await select('B'); await release(0); await flush(); result = await state();
  check(result.text.includes('Career B'), 'late detail changed current Career');
  passed.push('late detail selection response cannot resurrect A');

  check(pageErrors.length === 0, `browser errors: ${pageErrors.join('; ')}`);
  return { passed, browserErrors: pageErrors.length };
}

const code = `async (page) => { return await (${verifyScreen.toString()})(page, ${JSON.stringify(fixtures)}); }`;
const cli = process.env.PLAYWRIGHT_CLI;
if (!cli) throw new Error('Set PLAYWRIGHT_CLI to the existing playwright-cli or wrapper path');
const result = spawnSync(cli, ['run-code', code], { encoding: 'utf8', env: process.env });
console.log(result.stdout?.split('### Ran Playwright code')[0] ?? '');
if (result.stderr) console.error(result.stderr);
process.exitCode = result.status || (result.stdout?.includes('### Error') ? 1 : 0);
