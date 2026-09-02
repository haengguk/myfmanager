import { useCallback, useEffect, useRef, useState } from 'react';
import { LeagueApiFailure, cancelLeague, completeLeaguePlayerSeries, createLeague, getLeagueCompletion, getLeagueFixtures, getLeagueJob, getLeaguePlayerSeries, getLeagueSeason, pauseLeague, resumeLeague, runLeagueRound, startLeaguePlayerSeries } from './api/leagueApi.client';
import type { LeagueFixtureViewDto, LeagueJobViewDto, LeaguePlayerSeriesViewDto, LeagueSeasonViewDto } from './api/leagueApi.types';
import { LeagueCancelDialog } from './LeagueCancelDialog';
import { createLeagueRequest, LeagueCreation, type LeagueCreateSelection } from './LeagueCreation';
import { LeagueDashboard } from './LeagueDashboard';
import { LeagueCompletionReconciler, selectLeagueCompletionCandidate, type LeagueCompletionTarget, type LeagueCompletionTrigger } from './leagueCompletionReconciliation';
import { isAmbiguousLeagueFailure, logicalLeagueCommand, seasonCommandApplied, shouldApplyLeagueSeason } from './leagueCommandReconciliation';
import { clearLeaguePointer, leaguePointerRecoveryAction, readLeaguePointer, updateLeagueCommand, writeLeaguePointer, type LeagueCommandRef, type LeaguePointer } from './league.pointer';

const CREATE_DRAFT_KEY = 'lolmanager.league.create-command.v1';
const POLL_DELAYS = [500, 800, 1200, ...Array.from({ length: 177 }, () => 2000)] as const;
interface CreateDraft { selection: LeagueCreateSelection; clientCommandId: string }
function createKey(selection: LeagueCreateSelection): string { return JSON.stringify(selection); }
function readCreateDraft(): CreateDraft | null { try { const value = JSON.parse(window.sessionStorage.getItem(CREATE_DRAFT_KEY) ?? 'null') as CreateDraft | null; return value && typeof value.clientCommandId === 'string' && typeof value.selection === 'object' ? value : null; } catch { window.sessionStorage.removeItem(CREATE_DRAFT_KEY); return null; } }
function wait(milliseconds: number, signal: AbortSignal): Promise<void> { return new Promise((resolve, reject) => { const timer = window.setTimeout(resolve, milliseconds); signal.addEventListener('abort', () => { window.clearTimeout(timer); reject(new DOMException('League polling aborted', 'AbortError')); }, { once: true }); }); }
function failureCopy(error: unknown): string { return error instanceof LeagueApiFailure ? error.userMessage : 'League 요청을 완료하지 못했습니다.'; }

export function LeaguePage({ onOpenSeries, onNotify }: { onOpenSeries: (playerSeries: LeaguePlayerSeriesViewDto, fixture: LeagueFixtureViewDto) => void; onNotify: (title: string, message: string) => void }) {
  const [pointer, setPointerState] = useState<LeaguePointer | null>(() => readLeaguePointer(window.sessionStorage));
  const [season, setSeason] = useState<LeagueSeasonViewDto | null>(null); const [fixtures, setFixtures] = useState<readonly LeagueFixtureViewDto[]>([]); const [jobs, setJobs] = useState<ReadonlyMap<string, LeagueJobViewDto>>(() => new Map());
  const [loading, setLoading] = useState(Boolean(pointer)); const [pending, setPending] = useState<string | null>(null); const [error, setError] = useState<string | null>(null); const [completionWake, setCompletionWake] = useState(0);
  const [cancelOpen, setCancelOpen] = useState(false); const [cancelReturnFocus, setCancelReturnFocus] = useState<HTMLElement | null>(null);
  const requestRef = useRef<AbortController | null>(null); const pollRef = useRef<AbortController | null>(null); const restoreRunRef = useRef(false); const completionRef = useRef(new LeagueCompletionReconciler()); const completionUnmountTimerRef = useRef<number | null>(null);
  const pointerRef = useRef(pointer); const seasonRef = useRef(season); pointerRef.current = pointer; seasonRef.current = season;
  const createDraftRef = useRef<CreateDraft | null>(readCreateDraft());
  const setPointer = useCallback((next: LeaguePointer | null) => { setPointerState(next); if (next) writeLeaguePointer(window.sessionStorage, next); else clearLeaguePointer(window.sessionStorage); }, []);
  const setCommand = useCallback((command: LeagueCommandRef | null) => { setPointerState((current) => current ? updateLeagueCommand(window.sessionStorage, current, command) : current); }, []);
  const clearCompletionCommand = useCallback((command: LeagueCommandRef) => { setPointerState((current) => current?.command?.clientCommandId === command.clientCommandId ? updateLeagueCommand(window.sessionStorage, current, null) : current); }, []);
  const applySeason = useCallback((next: LeagueSeasonViewDto) => setSeason((current) => shouldApplyLeagueSeason(current, next) ? next : current), []);

  const loadJobs = useCallback(async (scope: LeaguePointer, values: readonly LeagueFixtureViewDto[], currentRound: number, signal: AbortSignal) => {
    const ids = values.filter((fixture) => fixture.jobId !== null && fixture.roundNumber === currentRound).map((fixture) => fixture.jobId!); const results = await Promise.allSettled(ids.map((jobId) => getLeagueJob(scope, jobId, signal)));
    setJobs((current) => { const next = new Map(current); results.forEach((result) => { if (result.status === 'fulfilled') { const prior = next.get(result.value.job.jobId); if (!prior || result.value.job.revision >= prior.revision) next.set(result.value.job.jobId, result.value.job); } }); return next; });
  }, []);

  const refresh = useCallback(async (scope: LeaguePointer, signal: AbortSignal) => {
    const [seasonResponse, fixtureResponse] = await Promise.all([getLeagueSeason(scope, signal), getLeagueFixtures(scope, signal)]);
    applySeason(seasonResponse.season); setFixtures((current) => fixtureResponse.standingsRevision >= (season?.standingsRevision ?? -1) ? fixtureResponse.fixtures : current); await loadJobs(scope, fixtureResponse.fixtures, seasonResponse.season.currentRound, signal); return { season: seasonResponse.season, fixtures: fixtureResponse.fixtures };
  }, [applySeason, loadJobs, season?.standingsRevision]);

  const reconcilePlayerSeries = useCallback((fixture: LeagueFixtureViewDto, trigger: LeagueCompletionTrigger, blockedByOperation: string | null = null) => {
    const currentPointer = pointerRef.current; const currentSeason = seasonRef.current;
    if (!currentPointer || !currentSeason) return Promise.resolve('NO_OP' as const);
    if (!fixture.bindingHash) { setError('완료 반영에 필요한 binding hash가 없습니다.'); return Promise.resolve('TERMINAL_FAILURE' as const); }
    const target: LeagueCompletionTarget = { leagueId: currentPointer.leagueId, seasonId: currentPointer.seasonId, fixtureId: fixture.fixtureId, bindingHash: fixture.bindingHash, expectedRevision: currentSeason.lifecycleRevision };
    return completionRef.current.reconcileIfAvailable(target, trigger, blockedByOperation, {
      readCommand: () => pointerRef.current?.command ?? null,
      saveCommand: setCommand,
      clearCommand: clearCompletionCommand,
      postCompletion: (scope, command, signal) => completeLeaguePlayerSeries(scope, { schemaVersion: 'AI_LEAGUE_PLAYER_COMPLETION_COMMAND_V1', expectedLifecycleRevision: command.expectedRevision!, clientCommandId: command.clientCommandId, bindingHash: scope.bindingHash }, signal),
      getCompletion: (scope, signal) => getLeagueCompletion(scope, signal),
      refreshAuthoritativeViews: async (signal) => { const scope = pointerRef.current; if (!scope || scope.leagueId !== target.leagueId || scope.seasonId !== target.seasonId) throw new LeagueApiFailure('CONTRACT', 'League completion scope가 변경되었습니다.'); await refresh(scope, signal); },
      wait,
      isVisible: () => !document.hidden,
      onStateChange: (snapshot) => {
        const active = ['CANDIDATE_DISCOVERED', 'RECONCILING', 'POLLING'].includes(snapshot.state);
        setPending((current) => active ? 'COMPLETE_PLAYER_SERIES' : current === 'COMPLETE_PLAYER_SERIES' ? null : current);
        if (snapshot.state === 'CANDIDATE_DISCOVERED') setError(null);
      },
      onRecoverableFailure: (failure, exhausted) => setError(exhausted ? `${failureCopy(failure)} 자동 확인은 잠시 멈췄습니다. 새로고침하거나 완료 결과 반영을 눌러 같은 작업으로 다시 확인하세요.` : `${failureCopy(failure)} 같은 완료 작업으로 서버 상태를 다시 확인합니다.`),
      onTerminalFailure: (failure) => setError(failureCopy(failure)),
      onNotFound: (failure) => {
        if (failure.code === 'LEAGUE_SEASON_NOT_FOUND') { setPointer(null); setSeason(null); setFixtures([]); }
        else setError(failureCopy(failure));
      },
      onApplied: () => { setError(null); onNotify('Player Series 결과 반영', '검증된 Series 결과를 순위표에 한 번만 반영했습니다.'); },
    });
  }, [clearCompletionCommand, onNotify, refresh, setCommand, setPointer]);

  useEffect(() => {
    if (!pointer) { setLoading(false); return; }
    const controller = new AbortController(); requestRef.current = controller; setLoading(true); setError(null);
    refresh(pointer, controller.signal).then(async (view) => {
      if (controller.signal.aborted) return;
      const command = pointer.command;
      if (command && ['PAUSE', 'RESUME', 'CANCEL'].includes(command.kind) && command.expectedRevision !== null && seasonCommandApplied(command.kind, view.season, command.expectedRevision)) setCommand(null);
      if (command?.kind === 'RUN_ROUND' && !restoreRunRef.current) {
        restoreRunRef.current = true;
        const hasDurableWork = view.fixtures.some((fixture) => fixture.roundNumber === view.season.currentRound && ['QUEUED', 'RETRY_PENDING'].includes(fixture.jobStatus ?? ''));
        if (hasDurableWork && command.expectedRevision !== null) {
          try { const run = await runLeagueRound(pointer, { schemaVersion: 'AI_LEAGUE_RUN_ROUND_COMMAND_V1', expectedLifecycleRevision: command.expectedRevision, clientCommandId: command.clientCommandId }, controller.signal); applySeason(run.season); setJobs(new Map(run.jobs.map((job) => [job.jobId, job]))); }
          catch (cause) { if (!isAmbiguousLeagueFailure(cause instanceof LeagueApiFailure ? cause : new LeagueApiFailure('NETWORK', ''))) setCommand(null); }
        }
      }
    }).catch((cause: unknown) => {
      if (controller.signal.aborted) return; const failure = cause instanceof LeagueApiFailure ? cause : new LeagueApiFailure('NETWORK', failureCopy(cause)); const action = leaguePointerRecoveryAction(failure);
      if (action === 'CLEAR_NOT_FOUND') { setPointer(null); setSeason(null); setFixtures([]); onNotify('저장된 시즌 정리', '백엔드에서 시즌을 찾을 수 없어 저장된 League ID를 정리했습니다.'); }
      else setError(action === 'KEEP_VERSION' ? `${failure.userMessage} 저장된 ID는 유지했지만 로컬 view를 사용하지 않았습니다.` : failure.userMessage);
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); if (requestRef.current === controller) requestRef.current = null; });
    return () => controller.abort();
  }, [applySeason, onNotify, pointer?.leagueId, pointer?.seasonId, refresh, setCommand, setPointer]);

  const pollJobIds = fixtures.filter((fixture) => fixture.roundNumber === season?.currentRound && fixture.jobId !== null).map((fixture) => fixture.jobId!).sort();
  const pollKey = pollJobIds.join(':');
  useEffect(() => {
    if (!pointer || !season || pollJobIds.length === 0) return;
    const controller = new AbortController(); pollRef.current?.abort(); pollRef.current = controller;
    void (async () => { try { for (const delay of POLL_DELAYS) { await wait(delay, controller.signal); const results = await Promise.all(pollJobIds.map((jobId) => getLeagueJob(pointer, jobId, controller.signal))); const latest = results.map((result) => result.job); if (latest.every((job) => ['COMPLETED', 'BLOCKED', 'CANCELLED'].includes(job.lifecycleStatus))) { setJobs((current) => { const next = new Map(current); latest.forEach((job) => next.set(job.jobId, job)); return next; }); const view = await refresh(pointer, controller.signal); if (pointer.command?.kind === 'RUN_ROUND') setCommand(null); const blocked = latest.find((job) => job.lifecycleStatus === 'BLOCKED'); onNotify(blocked ? '라운드 작업 확인 필요' : '라운드 계산 완료', blocked ? `${blocked.failureCode ?? '작업 차단'} 상태를 확인하세요.` : `Round ${season.currentRound} Auto 경기 결과와 순위를 반영했습니다.`); if (view.season.playableManagedFixture) onNotify('Player Series 대기', '관리 팀 경기를 직접 진행할 수 있습니다.'); return; } setJobs((current) => { const next = new Map(current); latest.forEach((job) => { const prior = next.get(job.jobId); if (!prior || job.revision >= prior.revision) next.set(job.jobId, job); }); return next; }); } setError('자동 확인 시간이 끝났습니다. 작업은 취소되지 않았습니다. 같은 실행 작업으로 worker를 다시 깨울 수 있습니다.'); } catch (cause) { if (!(cause instanceof DOMException && cause.name === 'AbortError')) setError(failureCopy(cause)); } })();
    return () => controller.abort();
  }, [onNotify, pointer?.leagueId, pointer?.seasonId, pollKey, refresh, season?.currentRound, setCommand]);

  const completionCandidate = selectLeagueCompletionCandidate(fixtures, pointer?.command ?? null);
  const completionCandidateKey = completionCandidate ? `${completionCandidate.fixtureId}:${completionCandidate.revision}:${completionCandidate.bindingHash ?? ''}:${season?.lifecycleRevision ?? -1}` : '';
  useEffect(() => {
    if (!season || !pointer || !completionCandidate) return;
    void reconcilePlayerSeries(completionCandidate, 'AUTO', pending);
  }, [completionCandidateKey, completionWake, pending, pointer?.leagueId, pointer?.seasonId, reconcilePlayerSeries, season, completionCandidate]);

  useEffect(() => {
    if (completionUnmountTimerRef.current !== null) { window.clearTimeout(completionUnmountTimerRef.current); completionUnmountTimerRef.current = null; }
    const onVisibility = () => { if (document.hidden) completionRef.current.abort(); else { completionRef.current.releaseRetryWait(); setCompletionWake((value) => value + 1); } };
    document.addEventListener('visibilitychange', onVisibility);
    return () => {
      document.removeEventListener('visibilitychange', onVisibility); requestRef.current?.abort(); pollRef.current?.abort();
      completionUnmountTimerRef.current = window.setTimeout(() => { completionRef.current.abort(); completionUnmountTimerRef.current = null; }, 0);
    };
  }, []);

  const create = async (selection: LeagueCreateSelection) => {
    if (pending) return; const prior = createDraftRef.current; const draft = prior && createKey(prior.selection) === createKey(selection) ? prior : { selection, clientCommandId: crypto.randomUUID() }; createDraftRef.current = draft; window.sessionStorage.setItem(CREATE_DRAFT_KEY, JSON.stringify(draft)); const controller = new AbortController(); requestRef.current?.abort(); requestRef.current = controller; setPending('CREATE'); setError(null);
    try { const response = await createLeague(createLeagueRequest(selection, draft.clientCommandId), controller.signal); const next: LeaguePointer = { schemaVersion: 'AI_LEAGUE_POINTER_V1', leagueId: response.season.leagueId, seasonId: response.season.seasonId, command: null }; setPointer(next); applySeason(response.season); const all = await getLeagueFixtures(next, controller.signal); setFixtures(all.fixtures); createDraftRef.current = null; window.sessionStorage.removeItem(CREATE_DRAFT_KEY); onNotify('AI 리그 생성 완료', `${response.season.seasonMode === 'HYBRID_MANAGER' ? 'Hybrid' : 'Spectator'} 시즌의 90경기 일정을 고정했습니다.`); }
    catch (cause) { setError(failureCopy(cause)); }
    finally { if (requestRef.current === controller) requestRef.current = null; setPending(null); }
  };

  const runCommand = async (kind: 'PAUSE' | 'RESUME') => {
    if (!pointer || !season || pending) return; const command = logicalLeagueCommand(pointer.command, { kind, scopeKey: pointer.seasonId, expectedRevision: season.lifecycleRevision, bindingHash: null }); setCommand(command); const controller = new AbortController(); requestRef.current?.abort(); requestRef.current = controller; setPending(kind); setError(null);
    try { const body = { schemaVersion: 'AI_LEAGUE_LIFECYCLE_COMMAND_V1' as const, expectedLifecycleRevision: command.expectedRevision!, clientCommandId: command.clientCommandId }; const response = kind === 'PAUSE' ? await pauseLeague(pointer, body, controller.signal) : await resumeLeague(pointer, body, controller.signal); applySeason(response.season); setCommand(null); }
    catch (cause) { setError(failureCopy(cause)); if (!(cause instanceof LeagueApiFailure) || !isAmbiguousLeagueFailure(cause)) setCommand(null); try { await refresh(pointer, controller.signal); } catch { /* original failure remains visible */ } }
    finally { if (requestRef.current === controller) requestRef.current = null; setPending(null); }
  };

  const runRound = async () => {
    if (!pointer || !season || pending) return; const command = logicalLeagueCommand(pointer.command, { kind: 'RUN_ROUND', scopeKey: `${pointer.seasonId}:${season.currentRound}`, expectedRevision: season.lifecycleRevision, bindingHash: null }); setCommand(command); const controller = new AbortController(); requestRef.current?.abort(); requestRef.current = controller; setPending('RUN_ROUND'); setError(null);
    try { const response = await runLeagueRound(pointer, { schemaVersion: 'AI_LEAGUE_RUN_ROUND_COMMAND_V1', expectedLifecycleRevision: command.expectedRevision!, clientCommandId: command.clientCommandId }, controller.signal); applySeason(response.season); setJobs(new Map(response.jobs.map((job) => [job.jobId, job]))); const byFixture = new Map(response.jobs.map((job) => [job.fixtureId, job])); setFixtures((current) => current.map((fixture) => { const job = byFixture.get(fixture.fixtureId); return job ? { ...fixture, jobId: job.jobId, jobStatus: job.lifecycleStatus } : fixture; })); onNotify(response.replayed ? '저장된 작업 다시 연결' : '라운드 작업 접수', `${response.jobs.length}개 Auto 경기를 서버에서 계산합니다.`); }
    catch (cause) { setError(failureCopy(cause)); if (!(cause instanceof LeagueApiFailure) || !isAmbiguousLeagueFailure(cause)) setCommand(null); try { await refresh(pointer, controller.signal); } catch { /* keep command for explicit retry */ } }
    finally { if (requestRef.current === controller) requestRef.current = null; setPending(null); }
  };

  async function handlePlayerSeries(fixture: LeagueFixtureViewDto) {
    if (!pointer || !season || pending) return; const reconcile = fixture.allowedCommands.includes('RECONCILE_PLAYER_SERIES_COMPLETION');
    if (reconcile) { await reconcilePlayerSeries(fixture, 'MANUAL'); return; }
    const kind = 'START_PLAYER_SERIES'; const command = logicalLeagueCommand(pointer.command, { kind, scopeKey: fixture.fixtureId, expectedRevision: season.lifecycleRevision, bindingHash: null }); setCommand(command); const controller = new AbortController(); requestRef.current?.abort(); requestRef.current = controller; setPending(kind); setError(null); const scope = { ...pointer, fixtureId: fixture.fixtureId };
    try {
      let response; try { response = await startLeaguePlayerSeries(scope, { schemaVersion: 'AI_LEAGUE_PLAYER_SERIES_COMMAND_V1', expectedLifecycleRevision: command.expectedRevision!, clientCommandId: command.clientCommandId }, controller.signal); } catch (cause) { if (!(cause instanceof LeagueApiFailure) || !isAmbiguousLeagueFailure(cause)) throw cause; response = await getLeaguePlayerSeries(scope, controller.signal); } setCommand(null); onOpenSeries(response.playerSeries, fixture);
    } catch (cause) { setError(failureCopy(cause)); if (!(cause instanceof LeagueApiFailure) || !isAmbiguousLeagueFailure(cause)) setCommand(null); }
    finally { if (requestRef.current === controller) requestRef.current = null; setPending(null); }
  }

  const confirmCancel = async () => {
    if (!pointer || !season || pending) return; const command = logicalLeagueCommand(pointer.command, { kind: 'CANCEL', scopeKey: pointer.seasonId, expectedRevision: season.lifecycleRevision, bindingHash: null }); setCommand(command); const controller = new AbortController(); requestRef.current?.abort(); requestRef.current = controller; setPending('CANCEL'); setError(null);
    try { await cancelLeague(pointer, { schemaVersion: 'AI_LEAGUE_LIFECYCLE_COMMAND_V1', expectedLifecycleRevision: command.expectedRevision!, clientCommandId: command.clientCommandId }, controller.signal); const latest = await getLeagueSeason(pointer, controller.signal); applySeason(latest.season); setCommand(null); setCancelOpen(false); }
    catch (cause) { setError(failureCopy(cause)); if (!(cause instanceof LeagueApiFailure) || !isAmbiguousLeagueFailure(cause)) setCommand(null); try { const latest = await getLeagueSeason(pointer, controller.signal); applySeason(latest.season); if (latest.season.lifecycleStatus === 'CANCELLED') { setCommand(null); setCancelOpen(false); } } catch { /* preserve retryable command */ } }
    finally { if (requestRef.current === controller) requestRef.current = null; setPending(null); }
  };

  if (loading && !season) return <main className="lg-loading" aria-live="polite"><span className="lg-spinner" aria-hidden="true" /><strong>저장된 AI 리그 시즌 확인 중</strong><p>로컬 점수 대신 서버의 최신 revision을 불러옵니다.</p></main>;
  if (!pointer || !season) return <LeagueCreation pending={pending === 'CREATE'} initial={createDraftRef.current?.selection ?? null} error={error} onCreate={(selection) => { void create(selection); }} />;
  return <><LeagueDashboard season={season} fixtures={fixtures} jobs={jobs} pending={pending} error={error} onRunRound={() => { void runRound(); }} onPause={() => { void runCommand('PAUSE'); }} onResume={() => { void runCommand('RESUME'); }} onCancel={(target) => { setCancelReturnFocus(target); setCancelOpen(true); }} onRefresh={() => { const controller = new AbortController(); requestRef.current?.abort(); requestRef.current = controller; completionRef.current.releaseRetryWait(); setPending('REFRESH'); setError(null); void refresh(pointer, controller.signal).catch((cause) => setError(failureCopy(cause))).finally(() => { if (requestRef.current === controller) requestRef.current = null; setPending(null); setCompletionWake((value) => value + 1); }); }} onNewSeason={() => { pollRef.current?.abort(); completionRef.current.abort(); setPointer(null); setSeason(null); setFixtures([]); setJobs(new Map()); restoreRunRef.current = false; }} onPlayerSeries={(fixture) => { void handlePlayerSeries(fixture); }} /><LeagueCancelDialog open={cancelOpen} pending={pending === 'CANCEL'} returnFocus={cancelReturnFocus} onClose={() => { if (pending !== 'CANCEL') setCancelOpen(false); }} onConfirm={() => { void confirmCancel(); }} /></>;
}
