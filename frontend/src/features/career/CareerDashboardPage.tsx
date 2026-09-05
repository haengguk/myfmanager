import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { fetchLckTeams, TeamPlayerApiFailure } from '../team-player/api/teamPlayerApi.client';
import type { TeamSummaryDto } from '../team-player/api/teamPlayerApi.types';
import { CareerApiFailure, advanceCareerCalendar, createCareer, getCareer, getCareerCalendar, getCareers, reconcileCareerCompetition, startOrResumeCareerCompetition } from './api/careerApi.client';
import { CAREER_SCHEMAS, type CareerAdvanceMode, type CareerCalendarViewDto, type CareerListResponseDto, type CareerSummaryDto, type CareerViewDto } from './api/careerApi.types';
import { CAREER_RESUME_COPY } from './career.adapter';
import { CareerCreateDialog } from './CareerCreateDialog';
import { CareerCalendarPanel } from './CareerCalendarPanel';
import {
  careerPointerRecoveryAction, clearCareerAdvanceOperation, clearCareerCompetitionOperation, clearCareerCreateOperation, clearCareerPointer,
  isAmbiguousCareerCreateFailure, logicalCareerCreate, readCareerCreateOperation,
  logicalCareerAdvance, logicalCareerCompetition, readCareerAdvanceOperation, readCareerPointer,
  reconcileCareerAdvanceOperation, reconcileCareerCompetitionOperation, writeCareerPointer,
  type CareerAdvanceOperation, type CareerCreateSelection,
} from './career.pointer';

const EMPTY_CAPACITY: CareerListResponseDto = { schemaVersion: CAREER_SCHEMAS.list, careers: [], currentCount: 0, maximumCount: 100, remainingCount: 100 };
const RESUME_KIND_COPY = { LEAGUE_DASHBOARD: '리그 대시보드', PLAYER_SERIES: 'Player Series', SEASON_COMPLETE: '시즌 완료', ATTENTION_REQUIRED: '확인 필요' } as const;
const COMMAND_COPY: Readonly<Record<string, string>> = {
  VIEW_STANDINGS: '순위표 보기', RUN_CURRENT_ROUND_AUTO_FIXTURES: '현재 라운드 Auto 경기 실행', PAUSE_SEASON: '시즌 일시 정지', RESUME_SEASON: '시즌 재개', CANCEL_SEASON: '시즌 취소', START_PLAYER_SERIES: 'Player Series 시작', RESUME_PLAYER_SERIES: 'Player Series 계속', RECONCILE_PLAYER_SERIES_COMPLETION: 'Series 완료 반영', VIEW_FIXTURE: '경기 보기',
};

function dateTime(value: string): string { return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)); }
function wait(milliseconds: number, signal: AbortSignal): Promise<void> { return new Promise((resolve, reject) => { const timer = window.setTimeout(resolve, milliseconds); signal.addEventListener('abort', () => { window.clearTimeout(timer); reject(new DOMException('aborted', 'AbortError')); }, { once: true }); }); }
function summaryFromView(career: CareerViewDto): CareerSummaryDto {
  return { careerId: career.careerId, saveName: career.saveName, managerName: career.managerName, managedTeamCode: career.managedTeamCode, currentDate: career.currentDate, leagueId: career.leagueId, seasonId: career.seasonId, lifecycleStatus: career.lifecycleStatus, resumeKind: career.resume.kind, updatedAt: career.updatedAt };
}
function withCreatedCareer(current: CareerListResponseDto, career: CareerViewDto): CareerListResponseDto {
  const existed = current.careers.some((entry) => entry.careerId === career.careerId);
  const careers = [summaryFromView(career), ...current.careers.filter((entry) => entry.careerId !== career.careerId)]
    .sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt) || left.careerId.localeCompare(right.careerId));
  const currentCount = Math.min(current.maximumCount, current.currentCount + (existed ? 0 : 1));
  return { ...current, careers, currentCount, remainingCount: current.maximumCount - currentCount };
}
function loadFailure(error: unknown): string {
  if (error instanceof CareerApiFailure || error instanceof TeamPlayerApiFailure) return error.userMessage;
  return 'Career 화면에 필요한 서버 정보를 불러오지 못했습니다.';
}
interface CatalogIdentity { catalogVersion: string; catalogHash: string }
function requireCareerReference(career: CareerViewDto, teams: readonly TeamSummaryDto[], catalog: CatalogIdentity | null): void {
  if (!teams.some((entry) => entry.teamCode === career.managedTeamCode)) throw new CareerApiFailure('CONTRACT', 'Career 관리 팀이 현재 LCK reference에 없습니다.');
  if (!catalog || career.referenceCatalogVersion !== catalog.catalogVersion || career.referenceCatalogHash !== catalog.catalogHash) throw new CareerApiFailure('CONTRACT', 'Career와 LCK reference generation이 일치하지 않습니다.');
}

export function CareerDashboardPage({ searchValue, onResume, onOpenCompetitionSeries, onNotify }: {
  searchValue: string;
  onResume: (career: CareerViewDto) => void;
  onOpenCompetitionSeries?: (seriesId: string, career: CareerViewDto, matchup: string) => void;
  onNotify: (title: string, message: string) => void;
}) {
  const [list, setList] = useState<CareerListResponseDto>(EMPTY_CAPACITY);
  const [teams, setTeams] = useState<readonly TeamSummaryDto[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<CareerViewDto | null>(null);
  const [calendar, setCalendar] = useState<CareerCalendarViewDto | null>(null);
  const [calendarLoading, setCalendarLoading] = useState(false);
  const [calendarError, setCalendarError] = useState<string | null>(null);
  const [advancePending, setAdvancePending] = useState(false);
  const [competitionPending, setCompetitionPending] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [integrityError, setIntegrityError] = useState(false);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [createPending, setCreatePending] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const requestRef = useRef<AbortController | null>(null);
  const createRequestRef = useRef<AbortController | null>(null);
  const advanceRequestRef = useRef<AbortController | null>(null);
  const competitionRequestRef = useRef<AbortController | null>(null);
  const restoredAdvanceRef = useRef<string | null>(null);
  const generationRef = useRef(0);
  const newCareerRef = useRef<HTMLButtonElement>(null);
  const detailTitleRef = useRef<HTMLHeadingElement>(null);
  const teamsRef = useRef(teams); teamsRef.current = teams;
  const catalogRef = useRef<CatalogIdentity | null>(null);

  const applyDetail = useCallback((career: CareerViewDto, focus = false) => {
    writeCareerPointer(window.sessionStorage, career.careerId); setSelectedId(career.careerId); setDetail(career); setIntegrityError(false);
    if (focus) window.requestAnimationFrame(() => detailTitleRef.current?.focus());
  }, []);

  const loadDetail = useCallback(async (careerId: string, focus = false) => {
    const generation = ++generationRef.current; const controller = new AbortController(); requestRef.current?.abort(); requestRef.current = controller;
    setSelectedId(careerId); setDetail(null); setCalendar(null); setDetailLoading(true); setCalendarLoading(true); setCalendarError(null); setError(null); setIntegrityError(false);
    try {
      const career = await getCareer(careerId, controller.signal); if (generation !== generationRef.current || controller.signal.aborted) return;
      requireCareerReference(career, teamsRef.current, catalogRef.current);
      applyDetail(career, focus);
      try {
        const calendarView = await getCareerCalendar(careerId, controller.signal); if (generation !== generationRef.current || controller.signal.aborted) return;
        const operation = reconcileCareerAdvanceOperation(window.sessionStorage, careerId, calendarView.activePendingAdvance); if (!calendarView.activePendingAdvance && operation) restoredAdvanceRef.current = null; reconcileCareerCompetitionOperation(window.sessionStorage, careerId, calendarView.competition.revision, calendarView.competition.activePendingCommand); setCalendar(calendarView); setCalendarError(null);
      } catch (cause) {
        if (controller.signal.aborted || generation !== generationRef.current) return;
        setCalendarError(loadFailure(cause));
      }
    } catch (cause) {
      if (controller.signal.aborted || generation !== generationRef.current) return;
      const failure = cause instanceof CareerApiFailure ? cause : new CareerApiFailure('NETWORK', loadFailure(cause)); const action = careerPointerRecoveryAction(failure);
      if (action === 'CLEAR_NOT_FOUND') { clearCareerPointer(window.sessionStorage); setSelectedId(null); setDetail(null); setError('선택한 저장을 서버에서 찾을 수 없어 브라우저의 Career ID를 정리했습니다.'); }
      else { setError(failure.userMessage); setIntegrityError(action === 'KEEP_INTEGRITY' || action === 'KEEP_CONTRACT'); }
    } finally { if (!controller.signal.aborted && generation === generationRef.current) { setDetailLoading(false); setCalendarLoading(false); } if (requestRef.current === controller) requestRef.current = null; }
  }, [applyDetail]);

  const loadWorkspace = useCallback(async () => {
    const generation = ++generationRef.current; const controller = new AbortController(); requestRef.current?.abort(); requestRef.current = controller;
    setInitialLoading(true); setError(null); setIntegrityError(false);
    try {
      const [nextList, teamResponse] = await Promise.all([getCareers(controller.signal), fetchLckTeams(controller.signal)]);
      if (controller.signal.aborted || generation !== generationRef.current) return;
      const catalog = { catalogVersion: teamResponse.catalog.catalogVersion, catalogHash: teamResponse.catalog.catalogHash };
      const teamCodes = new Set(teamResponse.teams.map((entry) => entry.teamCode));
      if (nextList.careers.some((career) => !teamCodes.has(career.managedTeamCode))) throw new CareerApiFailure('CONTRACT', 'Career 목록에 현재 LCK reference에 없는 관리 팀이 있습니다.');
      setList(nextList); setTeams(teamResponse.teams); teamsRef.current = teamResponse.teams; catalogRef.current = catalog;
      const pointer = readCareerPointer(window.sessionStorage); const target = pointer ?? nextList.careers[0]?.careerId ?? null;
      if (!target) { setSelectedId(null); setDetail(null); return; }
      try {
        const career = await getCareer(target, controller.signal); if (controller.signal.aborted || generation !== generationRef.current) return;
        if (!nextList.careers.some((entry) => entry.careerId === career.careerId)) throw new CareerApiFailure('CONTRACT', 'Career 목록과 상세의 identity가 일치하지 않습니다.');
        requireCareerReference(career, teamResponse.teams, catalog);
        applyDetail(career);
        setCalendarLoading(true);
        try { const calendarView = await getCareerCalendar(career.careerId, controller.signal); reconcileCareerAdvanceOperation(window.sessionStorage, career.careerId, calendarView.activePendingAdvance); reconcileCareerCompetitionOperation(window.sessionStorage, career.careerId, calendarView.competition.revision, calendarView.competition.activePendingCommand); setCalendar(calendarView); setCalendarError(null); }
        catch (calendarCause) { if (!controller.signal.aborted) setCalendarError(loadFailure(calendarCause)); }
        finally { if (!controller.signal.aborted) setCalendarLoading(false); }
      } catch (cause) {
        if (controller.signal.aborted || generation !== generationRef.current) return;
        const failure = cause instanceof CareerApiFailure ? cause : new CareerApiFailure('NETWORK', loadFailure(cause)); const action = careerPointerRecoveryAction(failure);
        if (action === 'CLEAR_NOT_FOUND') { clearCareerPointer(window.sessionStorage); setSelectedId(null); setDetail(null); setError('저장된 Career ID가 서버에 없어 pointer를 정리했습니다. 목록에서 다시 선택하세요.'); }
        else { setError(failure.userMessage); setIntegrityError(action === 'KEEP_INTEGRITY' || action === 'KEEP_CONTRACT'); }
      }
    } catch (cause) { if (!controller.signal.aborted && generation === generationRef.current) setError(loadFailure(cause)); }
    finally { if (!controller.signal.aborted && generation === generationRef.current) setInitialLoading(false); if (requestRef.current === controller) requestRef.current = null; }
  }, [applyDetail]);

  useEffect(() => { void loadWorkspace(); return () => { requestRef.current?.abort(); createRequestRef.current?.abort(); advanceRequestRef.current?.abort(); competitionRequestRef.current?.abort(); }; }, [loadWorkspace]);

  const create = useCallback(async (selection: CareerCreateSelection) => {
    if (createPending) return;
    const operation = logicalCareerCreate(window.sessionStorage, selection); const controller = new AbortController(); createRequestRef.current?.abort(); createRequestRef.current = controller;
    setCreatePending(true); setCreateError(null);
    try {
      const response = await createCareer({ schemaVersion: CAREER_SCHEMAS.createRequest, ...operation.selection, clientCommandId: operation.clientCommandId }, controller.signal);
      requireCareerReference(response.career, teamsRef.current, catalogRef.current);
      clearCareerCreateOperation(window.sessionStorage); setError(null); setIntegrityError(false); applyDetail(response.career, true); setDialogOpen(false);
      setCalendarLoading(true); setCalendarError(null);
      try { const calendarView = await getCareerCalendar(response.career.careerId, controller.signal); reconcileCareerAdvanceOperation(window.sessionStorage, response.career.careerId, calendarView.activePendingAdvance); reconcileCareerCompetitionOperation(window.sessionStorage, response.career.careerId, calendarView.competition.revision, calendarView.competition.activePendingCommand); setCalendar(calendarView); }
      catch (calendarCause) { if (!controller.signal.aborted) setCalendarError(loadFailure(calendarCause)); }
      finally { if (!controller.signal.aborted) setCalendarLoading(false); }
      onNotify(response.replayed ? 'Career 생성 결과 복원' : 'Career 생성 완료', `${response.career.saveName} · ${response.career.managedTeamCode} Hybrid Season을 서버에서 확인했습니다.`);
      try { setList(await getCareers(controller.signal)); } catch { setList((current) => withCreatedCareer(current, response.career)); }
    } catch (cause) {
      if (controller.signal.aborted) return;
      const failure = cause instanceof CareerApiFailure ? cause : new CareerApiFailure('NETWORK', loadFailure(cause));
      if (!isAmbiguousCareerCreateFailure(failure)) clearCareerCreateOperation(window.sessionStorage);
      setCreateError(failure.userMessage);
    } finally { if (!controller.signal.aborted) setCreatePending(false); if (createRequestRef.current === controller) createRequestRef.current = null; }
  }, [applyDetail, createPending, onNotify]);

  const advance = useCallback(async (mode: CareerAdvanceMode, restored?: CareerAdvanceOperation) => {
    if (!detail || !calendar || advancePending) return;
    const operation = restored ?? logicalCareerAdvance(window.sessionStorage, detail.careerId, calendar.calendarRevision, mode);
    const controller = new AbortController(); advanceRequestRef.current?.abort(); advanceRequestRef.current = controller;
    setAdvancePending(true); setCalendarError(null);
    try {
      let response = await advanceCareerCalendar(detail.careerId, { schemaVersion: CAREER_SCHEMAS.advanceRequest, expectedCalendarRevision: operation.expectedCalendarRevision, mode: operation.mode, clientCommandId: operation.clientCommandId }, controller.signal);
      reconcileCareerCompetitionOperation(window.sessionStorage, detail.careerId, response.calendar.competition.revision, response.calendar.competition.activePendingCommand); setCalendar(response.calendar);
      for (const delay of [400, 800, 1_200, 2_000, 3_000]) {
        if (!response.pending) break;
        await wait(delay, controller.signal);
        response = await advanceCareerCalendar(detail.careerId, { schemaVersion: CAREER_SCHEMAS.advanceRequest, expectedCalendarRevision: operation.expectedCalendarRevision, mode: operation.mode, clientCommandId: operation.clientCommandId }, controller.signal);
        reconcileCareerCompetitionOperation(window.sessionStorage, detail.careerId, response.calendar.competition.revision, response.calendar.competition.activePendingCommand); setCalendar(response.calendar);
      }
      if (response.pending) {
        setCalendarError('Auto 경기 작업은 서버에서 계속 실행 중입니다. 같은 진행 작업 ID를 유지한 채 다시 확인할 수 있습니다.');
        return;
      }
      clearCareerAdvanceOperation(window.sessionStorage, detail.careerId); restoredAdvanceRef.current = null;
      setError(null); setIntegrityError(false);
      const latest = await getCareer(detail.careerId, controller.signal); requireCareerReference(latest, teamsRef.current, catalogRef.current); applyDetail(latest);
      try { setList(await getCareers(controller.signal)); } catch { setList((current) => ({ ...current, careers: current.careers.map((entry) => entry.careerId === detail.careerId ? { ...entry, currentDate: response.calendar.currentDate } : entry) })); }
      if (response.stopReason === 'MANAGED_FIXTURE_REQUIRED') onNotify('관리 경기 도착', 'League 화면에서 관리 팀 Player Series를 완료하면 날짜 진행을 이어갈 수 있습니다.');
      else if (response.stopReason === 'ATTENTION_REQUIRED') onNotify('League 확인 필요', '차단 또는 재시작 필요 상태를 해결하기 전에는 날짜가 지나가지 않습니다.');
      else if (response.stopReason === 'SEASON_ROLLOVER_REQUIRED') onNotify('시즌 경계 도착', '새 시즌을 자동 생성하지 않고 시즌 전환 지점에서 멈췄습니다.');
      else onNotify(response.replayed ? '날짜 진행 결과 복원' : '날짜 진행 완료', `${response.calendar.currentDate} 서버 상태를 확인했습니다.`);
    } catch (cause) {
      if (controller.signal.aborted) return;
      const failure = cause instanceof CareerApiFailure ? cause : new CareerApiFailure('NETWORK', loadFailure(cause));
      if (!isAmbiguousCareerCreateFailure(failure)) { clearCareerAdvanceOperation(window.sessionStorage, detail.careerId); restoredAdvanceRef.current = null; }
      setCalendarError(failure.userMessage);
      try { const calendarView = await getCareerCalendar(detail.careerId, controller.signal); reconcileCareerAdvanceOperation(window.sessionStorage, detail.careerId, calendarView.activePendingAdvance); reconcileCareerCompetitionOperation(window.sessionStorage, detail.careerId, calendarView.competition.revision, calendarView.competition.activePendingCommand); setCalendar(calendarView); } catch { /* original failure remains visible */ }
    } finally { if (!controller.signal.aborted) setAdvancePending(false); if (advanceRequestRef.current === controller) advanceRequestRef.current = null; }
  }, [advancePending, applyDetail, calendar, detail, onNotify]);

  const executeCompetition = useCallback(async () => {
    if (!detail || !calendar || competitionPending) return;
    const fixture = calendar.competition.nextFixture;
    const command = calendar.competition.allowedCommands[0];
    if (!fixture || !command) return;
    const operation = logicalCareerCompetition(window.sessionStorage,
      detail.careerId, calendar.competition.revision);
    const body = {
      schemaVersion: CAREER_SCHEMAS.competitionCommandRequest,
      expectedCompetitionRevision: operation.expectedCompetitionRevision,
      clientCommandId: operation.clientCommandId,
    } as const;
    const controller = new AbortController(); competitionRequestRef.current?.abort(); competitionRequestRef.current = controller;
    setCompetitionPending(true); setCalendarError(null);
    try {
      const response = command === 'RECONCILE_COMPETITION_FIXTURE'
        ? await reconcileCareerCompetition(detail.careerId, body, controller.signal)
        : await startOrResumeCareerCompetition(detail.careerId, body, controller.signal);
      if (response.executionMode === 'PLAYER_CONTROLLED' && response.status !== 'COMPLETED') {
        if (!response.seriesId || !onOpenCompetitionSeries) throw new CareerApiFailure('CONTRACT', '관리 대회 Series 진입 정보를 확인할 수 없습니다.');
        onOpenCompetitionSeries(response.seriesId, detail,
          `${fixture.firstTeamCode ?? 'TBD'} vs ${fixture.secondTeamCode ?? 'TBD'}`);
        return;
      }
      if (response.status === 'BLOCKED') {
        setCalendarError(response.failureCode ?? '대회 Auto 경기 실행이 차단되었습니다.');
        return;
      }
      let latest = calendar;
      if (response.executionMode === 'FULL_AUTO' && ['PENDING', 'RUNNING'].includes(response.status)) {
        for (const delay of [400, 800, 1_200, 2_000, 3_000]) {
          await wait(delay, controller.signal);
          latest = await getCareerCalendar(detail.careerId, controller.signal); reconcileCareerCompetitionOperation(window.sessionStorage, detail.careerId, latest.competition.revision, latest.competition.activePendingCommand); setCalendar(latest);
          if (latest.competition.nextFixture?.fixtureId !== fixture.fixtureId
            || latest.competition.nextFixture?.resultApplicationStatus === 'APPLIED') break;
        }
        if (latest.competition.nextFixture?.fixtureId === fixture.fixtureId
          && latest.competition.nextFixture.resultApplicationStatus !== 'APPLIED') {
          setCalendarError('Auto 대회 경기는 서버에서 계속 실행 중입니다. 같은 작업 ID로 상태를 다시 확인할 수 있습니다.');
          return;
        }
      }
      clearCareerCompetitionOperation(window.sessionStorage, detail.careerId);
      await loadDetail(detail.careerId);
      onNotify(response.replayed ? '대회 결과 복원' : '대회 경기 완료',
        `${fixture.competitionId} ${fixture.matchId} 결과가 다음 대진에 반영됐습니다.`);
    } catch (cause) {
      if (controller.signal.aborted) return;
      const failure = cause instanceof CareerApiFailure ? cause : new CareerApiFailure('NETWORK', loadFailure(cause));
      if (!isAmbiguousCareerCreateFailure(failure)) clearCareerCompetitionOperation(window.sessionStorage, detail.careerId);
      setCalendarError(failure.userMessage);
      try { const latest = await getCareerCalendar(detail.careerId, controller.signal); reconcileCareerCompetitionOperation(window.sessionStorage, detail.careerId, latest.competition.revision, latest.competition.activePendingCommand); setCalendar(latest); } catch { /* original failure remains visible */ }
    } finally {
      if (!controller.signal.aborted) setCompetitionPending(false);
      if (competitionRequestRef.current === controller) competitionRequestRef.current = null;
    }
  }, [calendar, competitionPending, detail, loadDetail, onNotify, onOpenCompetitionSeries]);

  useEffect(() => {
    if (!detail || !calendar || advancePending) return;
    const operation = readCareerAdvanceOperation(window.sessionStorage, detail.careerId);
    if (!operation || restoredAdvanceRef.current === operation.clientCommandId) return;
    restoredAdvanceRef.current = operation.clientCommandId; void advance(operation.mode, operation);
  }, [advance, advancePending, calendar, detail]);

  const query = searchValue.normalize('NFC').trim().toLocaleLowerCase('ko-KR');
  const careers = useMemo(() => !query ? list.careers : list.careers.filter((career) => [career.saveName, career.managerName, career.managedTeamCode].some((value) => value.toLocaleLowerCase('ko-KR').includes(query))), [list.careers, query]);
  const full = list.remainingCount === 0;
  const pendingOperation = readCareerCreateOperation(window.sessionStorage);

  return <main className="ca-workspace" aria-labelledby="ca-page-title">
    <header className="ca-page-head"><div><span>CAREER / SAVE SLOTS</span><h1 id="ca-page-title">커리어 저장소</h1><p>Career identity와 연결된 LCK 시즌을 서버 상태 그대로 불러옵니다.</p></div><div className="ca-capacity" aria-label={`저장 슬롯 ${list.currentCount}개 중 ${list.maximumCount}개`}><span>사용 중</span><strong>{list.currentCount}<i>/</i>{list.maximumCount}</strong><small>{full ? '저장 슬롯이 가득 찼습니다' : `${list.remainingCount}개 남음`}</small></div><button ref={newCareerRef} className="lm-secondary-button" type="button" disabled={full || initialLoading} onClick={() => { setCreateError(null); setDialogOpen(true); }}>새 커리어</button></header>
    {initialLoading ? <section className="ca-loading" role="status" aria-live="polite"><span aria-hidden="true" /><strong>Career 저장 목록 확인 중</strong><p>브라우저 캐시가 아닌 서버의 최신 저장 상태를 읽고 있습니다.</p></section> : error && list.careers.length === 0 ? <section className={`ca-error${integrityError ? ' is-integrity' : ''}`} role="alert"><strong>{integrityError ? '저장 무결성 확인 필요' : 'Career를 불러오지 못했습니다'}</strong><p>{error}</p><button type="button" className="lm-secondary-button" onClick={() => { void loadWorkspace(); }}>다시 시도</button></section> : <div className="ca-layout">
      <aside className="ca-saves" aria-label="Career 저장 목록"><header><div><span>SAVED CAREERS</span><strong>{query ? `검색 결과 ${careers.length}` : `${list.currentCount}개 저장`}</strong></div><small>최근 운영 저장 순</small></header>{careers.length === 0 ? <div className="ca-empty"><strong>{list.currentCount === 0 ? '저장된 커리어가 없습니다' : '검색 결과가 없습니다'}</strong><p>{list.currentCount === 0 ? 'LCK 관리 팀과 감독 이름을 정해 첫 Career를 만드세요.' : '저장 이름, 감독 또는 팀 코드로 다시 검색하세요.'}</p>{list.currentCount === 0 && !full ? <button type="button" className="lm-primary-button" onClick={() => setDialogOpen(true)}>첫 커리어 만들기</button> : null}</div> : <ol>{careers.map((career) => <li key={career.careerId}><button type="button" aria-current={selectedId === career.careerId ? 'true' : undefined} onClick={() => { void loadDetail(career.careerId); }}><span className={`ca-save-state is-${career.resumeKind.toLowerCase()}`}>{RESUME_KIND_COPY[career.resumeKind]}</span><strong>{career.saveName}</strong><span>{career.managedTeamCode} · {career.managerName}</span><small><time dateTime={career.currentDate}>{career.currentDate}</time><time dateTime={career.updatedAt}>{dateTime(career.updatedAt)}</time></small></button></li>)}</ol>}</aside>
      <section className="ca-detail" aria-live="polite">{detailLoading ? <div className="ca-detail-loading" role="status"><span aria-hidden="true" /><strong>선택한 Career 확인 중</strong><p>연결된 Season의 navigation projection을 조회합니다.</p></div> : error ? <div className={`ca-error${integrityError ? ' is-integrity' : ''}`} role="alert"><strong>{integrityError ? '복구 불가 상태 확인 필요' : '상세를 불러오지 못했습니다'}</strong><p>{error}</p>{selectedId ? <button type="button" className="lm-secondary-button" onClick={() => { void loadDetail(selectedId); }}>다시 시도</button> : null}</div> : detail ? <><header><div><span>ACTIVE CAREER</span><h2 ref={detailTitleRef} tabIndex={-1}>{detail.saveName}</h2><p>{detail.managedTeamCode} 구단을 맡은 {detail.managerName} 감독의 저장입니다.</p></div><span className="ca-active-mark">ACTIVE</span></header><div className="ca-facts"><dl><div><dt>관리 팀</dt><dd>{detail.managedTeamCode}</dd></div><div><dt>감독</dt><dd>{detail.managerName}</dd></div><div><dt>게임 날짜</dt><dd>{detail.currentDate}</dd></div><div><dt>Career 상태</dt><dd>{detail.lifecycleStatus}</dd></div></dl><section><span>LINKED SEASON</span><h3>Round {detail.resume.currentRound} <i>/ 18</i></h3><p>{detail.resume.seasonLifecycleStatus}</p><dl><div><dt>시작 날짜</dt><dd>{detail.startDate}</dd></div><div><dt>Standings revision</dt><dd>{detail.resume.standingsRevision}</dd></div><div><dt>Lifecycle revision</dt><dd>{detail.resume.lifecycleRevision}</dd></div><div><dt>생성 시각</dt><dd>{dateTime(detail.createdAt)}</dd></div><div><dt>운영 저장 시각</dt><dd>{dateTime(detail.updatedAt)}</dd></div></dl></section></div><CareerCalendarPanel calendar={calendar} loading={calendarLoading} pending={advancePending} competitionPending={competitionPending} error={calendarError} onAdvance={(mode) => { void advance(mode); }} onCompetitionAction={() => { void executeCompetition(); }} onRefresh={() => { void loadDetail(detail.careerId); }} /><section className="ca-identities"><span>SERVER-OWNED IDENTITY</span><dl><div><dt>Career</dt><dd><code>{detail.careerId}</code></dd></div><div><dt>League</dt><dd><code>{detail.leagueId}</code></dd></div><div><dt>Season</dt><dd><code>{detail.seasonId}</code></dd></div></dl></section></> : <div className="ca-empty ca-empty--detail"><strong>저장을 선택하세요</strong><p>선택한 Career만 GET으로 다시 검증해 상세를 표시합니다.</p></div>}</section>
      <aside className="ca-resume" aria-label="이어하기 문맥">{detail ? <><span>NEXT ACTION</span><div className={`ca-resume__kind is-${detail.resume.kind.toLowerCase()}`}><small>{RESUME_KIND_COPY[detail.resume.kind]}</small><strong>{CAREER_RESUME_COPY[detail.resume.kind].label}</strong></div><p>{CAREER_RESUME_COPY[detail.resume.kind].description}</p>{detail.resume.kind === 'ATTENTION_REQUIRED' ? <div className="ca-resume__notice" role="note">로컬에서 복구 상태를 추측하지 않습니다. League 화면의 최신 허용 작업을 확인하세요.</div> : null}{detail.resume.kind === 'SEASON_COMPLETE' ? <div className="ca-resume__notice" role="note">캘린더는 시즌 전환 지점에서 멈추며 다음 시즌을 자동 생성하지 않습니다.</div> : null}<div className="ca-commands"><span>서버 허용 작업</span>{detail.resume.allowedCommands.length ? <ul>{detail.resume.allowedCommands.map((command) => <li key={command}>{COMMAND_COPY[command] ?? command}</li>)}</ul> : <p>Player command 없음</p>}</div><button type="button" className="lm-primary-button ca-resume__action" onClick={() => onResume(detail)}>{CAREER_RESUME_COPY[detail.resume.kind].label}</button><button type="button" className="lm-text-button" onClick={() => { void loadDetail(detail.careerId); }}>서버 상태 새로고침</button></> : <><span>NEXT ACTION</span><strong>저장 선택 대기</strong><p>목록에서 Career를 선택하면 서버의 resume projection을 확인합니다.</p></>}</aside>
    </div>}
    {full ? <div className="ca-capacity-note" role="status"><strong>저장 슬롯 100개가 모두 사용 중입니다.</strong><span>기존 Career는 계속 불러올 수 있지만 V1에서는 삭제·보관 기능이 없습니다.</span></div> : null}
    {dialogOpen ? <CareerCreateDialog teams={teams} initial={pendingOperation?.selection ?? null} pending={createPending} error={createError} returnFocus={newCareerRef.current} onClose={() => { if (!createPending) setDialogOpen(false); }} onCreate={(selection) => { void create(selection); }} /> : null}
  </main>;
}
