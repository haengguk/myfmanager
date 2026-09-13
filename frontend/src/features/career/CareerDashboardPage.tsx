import { observeCareerAuto, type CareerAutoTarget } from './careerAutoObservation';
import { CareerEntryScreen } from './CareerEntryScreen';
import { entryKey, draftKey, recentKey, readEntryView, type CareerEntryView } from './careerEntry';
import { realMatchConfig } from '../real-match/realMatch.config';
import { CareerShell } from './CareerShell';
import { CareerHomePage } from './CareerHomePage';
import { careerPages, pageForLink, readCareerLocation, writeCareerLocation, type CareerPage } from './careerWorkspace';
import type { AppSection } from '../../layout/Sidebar';
import { CareerReturnResult } from './CareerReturnResult';
import { calendarMatchLink, focusPlayTarget } from './careerPlayFlow';
import { scoutingRequest, type Opponent } from './api/careerScouting';
import { CareerScoutingPanel } from './CareerScoutingPanel';
import { currentNavigation, matchDestination, preparationStillCurrent } from './careerNavigation';
import { CareerInboxPanel } from './CareerInboxPanel';
import type { InboxLink } from './api/careerInbox';
import { CareerRecordsPanel } from './CareerRecordsPanel';
import { CareerContinuousPanel } from './CareerContinuousPanel';
import { CareerOverseasPanel } from './CareerOverseasPanel';
import { CareerLifecyclePanel } from './CareerLifecyclePanel';
import { CareerTrainingPanel } from './CareerTrainingPanel';
import { CareerMarketPanel } from './CareerMarketPanel';
import { CareerClPanel } from './CareerClPanel';
import { CareerRosterPanel } from './CareerRosterPanel';
import { CareerMutationGate } from './career.mutation';
import { CareerSeasonsPanel } from './CareerSeasonsPanel';
import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchLckTeams, TeamPlayerApiFailure } from '../team-player/api/teamPlayerApi.client';
import type { TeamSummaryDto } from '../team-player/api/teamPlayerApi.types';
import { CareerApiFailure, advanceCareerCalendar, createCareer, getCareer, getCareerCalendar, getCareers, reconcileCareerCompetition, startOrResumeCareerCompetition } from './api/careerApi.client';
import { CAREER_SCHEMAS, type CareerAdvanceMode, type CareerCalendarViewDto, type CareerListResponseDto, type CareerSummaryDto, type CareerViewDto } from './api/careerApi.types';
import { CAREER_RESUME_COPY } from './career.adapter';
import { CareerCalendarPanel } from './CareerCalendarPanel';
import {
  careerPointerRecoveryAction, clearCareerAdvanceOperation, clearCareerCompetitionOperation, clearCareerCreateOperation, clearCareerPointer,
  isAmbiguousCareerCreateFailure, logicalCareerCreate, readCareerCreateOperation,
  logicalCareerAdvance, logicalCareerCompetition, readCareerAdvanceOperation, readCareerPointer, readCareerCompetitionOperation, normalizeCareerSelection,
  reconcileCareerAdvanceOperation, reconcileCareerCompetitionOperation, writeCareerPointer,
  type CareerAdvanceOperation, type CareerCreateSelection,
} from './career.pointer';

const EMPTY_CAPACITY: CareerListResponseDto = { schemaVersion: CAREER_SCHEMAS.list, careers: [], currentCount: 0, maximumCount: 100, remainingCount: 100 };


function dateTime(value: string): string { return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)); }
function wait(milliseconds: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) { reject(new DOMException('aborted', 'AbortError')); return; }
    const abort = () => { window.clearTimeout(timer); reject(new DOMException('aborted', 'AbortError')); };
    const timer = window.setTimeout(() => { signal.removeEventListener('abort', abort); resolve(); }, milliseconds);
    signal.addEventListener('abort', abort, { once: true });
  });
}
function summaryFromView(career: CareerViewDto): CareerSummaryDto {
  return { careerId: career.careerId, saveName: career.saveName, managerName: career.managerName, managedTeamCode: career.managedTeamCode, currentDate: career.currentDate, leagueId: career.leagueId, seasonId: career.seasonId, lifecycleStatus: career.lifecycleStatus, resumeKind: career.resume.kind, updatedAt: career.updatedAt, compatibility: career.compatibility };
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
  if (career.compatibility?.status === 'UNSUPPORTED') throw new CareerApiFailure('CONTRACT', career.compatibility.message ?? '현재 버전에서 지원하지 않는 저장입니다.');
  if (career.compatibility?.status === 'SUPPORTED' && career.compatibility.dataSource === 'SAVED_CAREER' && career.compatibility.managedTeamCode === career.managedTeamCode) return;
  if (!teams.some((entry) => entry.teamCode === career.managedTeamCode)) throw new CareerApiFailure('CONTRACT', 'Career 관리 팀이 현재 LCK reference에 없습니다.');
  if (!catalog || career.referenceCatalogVersion !== catalog.catalogVersion || career.referenceCatalogHash !== catalog.catalogHash) throw new CareerApiFailure('CONTRACT', 'Career와 LCK reference generation이 일치하지 않습니다.');
}

export function CareerDashboardPage({ onResume, onOpenCompetitionSeries, onNotify, returnedSeries, onCareerSelectionChange, onTool }: {
  onTool?: (tool: AppSection) => void;
  onCareerSelectionChange?: () => void;
  returnedSeries?: { careerId: string; seriesId: string } | null;
  searchValue: string;
  onResume: (career: CareerViewDto) => void;
  onOpenCompetitionSeries?: (seriesId: string, career: CareerViewDto, matchup: string) => void;
  onNotify: (title: string, message: string) => void;
}) {
  const [page, setPage] = useState<CareerPage>('home');
  const apiScope = realMatchConfig.apiBaseUrl || window.location.origin;
  const [entryView, setEntryView] = useState<CareerEntryView>(() => readEntryView(window.sessionStorage, apiScope, readCareerPointer(window.sessionStorage)));
  const entryViewRef = useRef(entryView); entryViewRef.current = entryView;
  const [managing, setManaging] = useState(() => entryView === 'club');
  const [teamsLoading, setTeamsLoading] = useState(false), [teamsError, setTeamsError] = useState<string | null>(null);
  const [teamRefresh, setTeamRefresh] = useState(0);
  const createLock = useRef(false);
  const commitEntry = useCallback((view: CareerEntryView, replace = false) => {
    entryViewRef.current = view; setEntryView(view); setManaging(view === 'club');
    window.sessionStorage.setItem(entryKey(apiScope), view);
    const state = { ...window.history.state, careerEntry: { api: apiScope, view } };
    if (replace) window.history.replaceState(state, ''); else window.history.pushState(state, '');
  }, [apiScope]);
  const [scheduleTab, setScheduleTab] = useState('calendar');
  const [trainingTab, setTrainingTab] = useState('training');
  const [recordScope, setRecordScope] = useState<{ careerId: string; year: number | null } | null>(null);
  const onRecordScope = useCallback((careerId: string, year: number | null) => setRecordScope({ careerId, year }), []);
  const [continuousStatus, setContinuousStatus] = useState('진행 상태 확인 중');
  const queuedPage = useRef<CareerPage | null>(null);
  const pageTitle = useRef<HTMLHeadingElement>(null);
  const [list, setList] = useState<CareerListResponseDto>(EMPTY_CAPACITY);
  const [teams, setTeams] = useState<readonly TeamSummaryDto[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<CareerViewDto | null>(null);
  const latestCareer = useRef(detail); latestCareer.current = detail;
  const [historical, setHistorical] = useState(false);
  const [historyYear, setHistoryYear] = useState<number | null>(null);
  const appliedContinuous = useRef(new Map<string, string>());
  const [continuous, setContinuous] = useState<{ career: string; active: boolean; date?: string } | null>(null);
  const [mutationPending, setMutationPending] = useState(false);
  const [operatingRevision, setOperatingRevision] = useState(0);
  const [inboxFocus, setInboxFocus] = useState<InboxLink | null>(null);

  const [opponentRequest, setOpponentRequest] = useState(0);
  const navigationGeneration = useRef(0);
  useEffect(() => { ++navigationGeneration.current; }, [selectedId]);
  const [marketPlayer, setMarketPlayer] = useState<string | null>(null);
  const mutationGate = useRef<CareerMutationGate>();
  mutationGate.current ??= new CareerMutationGate(setMutationPending);
  const [calendar, setCalendar] = useState<CareerCalendarViewDto | null>(null);
  const [calendarLoading, setCalendarLoading] = useState(false);
  const continuousBusy = !!detail && (calendarLoading || continuous?.career !== detail.careerId || continuous.active);
  const [calendarError, setCalendarError] = useState<string | null>(null);
  const [advancePending, setAdvancePending] = useState(false);
  const [competitionPending, setCompetitionPending] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [integrityError, setIntegrityError] = useState(false);
  const [createPending, setCreatePending] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const requestRef = useRef<AbortController | null>(null);
  const createRequestRef = useRef<AbortController | null>(null);
  const advanceRequestRef = useRef<AbortController | null>(null);
  const competitionRequestRef = useRef<AbortController | null>(null);
  const restoredAdvanceRef = useRef<string | null>(null);
  const generationRef = useRef(0);
  const selectedIdRef = useRef<string | null>(null);
  const detailTitleRef = pageTitle;
  const teamsRef = useRef(teams); teamsRef.current = teams;
  const catalogRef = useRef<CatalogIdentity | null>(null);

  const applyDetail = useCallback((career: CareerViewDto, focus = false) => {
    if (selectedIdRef.current !== career.careerId) {
      onCareerSelectionChange?.(); setCalendar(null);
      const location = readCareerLocation(window.sessionStorage, career.careerId);
      setPage(location.page); setScheduleTab(location.scheduleTab ?? 'calendar'); setTrainingTab(location.trainingTab ?? 'training'); setHistorical(location.year !== null); setHistoryYear(location.year);
      setInboxFocus(location.focus); setMarketPlayer(location.focus?.playerId ?? null);
    }
    latestCareer.current = career; selectedIdRef.current = career.careerId;
    try { window.localStorage.setItem(recentKey(apiScope), career.careerId); } catch { /* Optional recent-game hint. */ }
    writeCareerPointer(window.sessionStorage, career.careerId); setSelectedId(career.careerId); setDetail(career); setIntegrityError(false);
    if (focus) window.requestAnimationFrame(() => detailTitleRef.current?.focus());
  }, [onCareerSelectionChange, apiScope]);

  const invalidateScreenRequests = useCallback(() => {
    ++generationRef.current;
    requestRef.current?.abort(); createRequestRef.current?.abort();
    advanceRequestRef.current?.abort(); competitionRequestRef.current?.abort();
    setAdvancePending(false); setCompetitionPending(false); setCreatePending(false);
    restoredAdvanceRef.current = null;
  }, []);

  const loadDetail = useCallback(async (careerId: string, focus = false, continuousStamp?: string) => {
    const switching = selectedIdRef.current !== careerId; if (switching) onCareerSelectionChange?.(); invalidateScreenRequests(); const generation = generationRef.current; const controller = new AbortController(); requestRef.current?.abort(); requestRef.current = controller;
    selectedIdRef.current = careerId;
    setSelectedId(careerId); if (!continuousStamp) setManaging(true); if (switching) {
      ++navigationGeneration.current; queuedPage.current = null;
      const location = readCareerLocation(window.sessionStorage, careerId);
      setPage(location.page); setScheduleTab(location.scheduleTab ?? 'calendar'); setTrainingTab(location.trainingTab ?? 'training'); setHistorical(location.year !== null); setHistoryYear(location.year);
      setInboxFocus(location.focus); setMarketPlayer(location.focus?.playerId ?? null);
      setContinuousStatus('진행 상태 확인 중'); setDetail(null); setCalendar(null);
    } setDetailLoading(switching); setCalendarLoading(true); setCalendarError(null); setError(null); setIntegrityError(false);
    try {
      const career = await getCareer(careerId, controller.signal); if (generation !== generationRef.current || controller.signal.aborted) return;
      if (!career.compatibility && !catalogRef.current) { const reference = await fetchLckTeams(controller.signal); if (generation !== generationRef.current || controller.signal.aborted) return; teamsRef.current = reference.teams; catalogRef.current = reference.catalog; }
      requireCareerReference(career, teamsRef.current, catalogRef.current);
      applyDetail(career, focus);
      try {
        const calendarView = await getCareerCalendar(careerId, controller.signal); if (generation !== generationRef.current || controller.signal.aborted) return;
        const operation = reconcileCareerAdvanceOperation(window.sessionStorage, careerId, calendarView.activePendingAdvance); if (!calendarView.activePendingAdvance && operation) restoredAdvanceRef.current = null; reconcileCareerCompetitionOperation(window.sessionStorage, careerId, calendarView.competition.revision, calendarView.competition.activePendingCommand, calendarView.activeCalendarSeasonYear); if (continuousStamp) appliedContinuous.current.set(careerId, continuousStamp); setCalendar(calendarView); setCalendarError(null); return true;
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
  }, [applyDetail, invalidateScreenRequests, onCareerSelectionChange]);

  const loadWorkspace = useCallback(async () => {
    invalidateScreenRequests(); const generation = generationRef.current; const controller = new AbortController(); requestRef.current = controller;
    setInitialLoading(true); setError(null); setIntegrityError(false);
    try {
      const nextList = await getCareers(controller.signal);
      if (controller.signal.aborted || generation !== generationRef.current) return;
      setList(nextList);
      const pointer = readCareerPointer(window.sessionStorage);
      if (entryViewRef.current === 'club' && pointer) { setInitialLoading(false); await loadDetail(pointer); }
      else if (entryViewRef.current === 'club') commitEntry('main', true);
    } catch (cause) { if (!controller.signal.aborted && generation === generationRef.current) setError(loadFailure(cause)); }
    finally { if (!controller.signal.aborted) setInitialLoading(false); if (requestRef.current === controller) requestRef.current = null; }
  }, [invalidateScreenRequests, loadDetail, commitEntry]);

  useEffect(() => { void loadWorkspace(); return () => { ++generationRef.current; requestRef.current?.abort(); createRequestRef.current?.abort(); advanceRequestRef.current?.abort(); competitionRequestRef.current?.abort(); }; }, [loadWorkspace]);

  useEffect(() => {
    if (entryView !== 'new') return;
    const controller = new AbortController(); setTeamsLoading(true); setTeamsError(null);
    void fetchLckTeams(controller.signal).then(value => { if (!controller.signal.aborted) { setTeams(value.teams); teamsRef.current = value.teams; catalogRef.current = value.catalog; } })
      .catch(cause => { if (!controller.signal.aborted) setTeamsError(loadFailure(cause)); }).finally(() => { if (!controller.signal.aborted) setTeamsLoading(false); });
    return () => controller.abort();
  }, [entryView, teamRefresh]);

  const create = useCallback(async (selection: CareerCreateSelection) => {
    if (createPending || createLock.current) return;
    const prior = readCareerCreateOperation(window.sessionStorage);
    if (prior && prior.canonicalSelectionKey !== JSON.stringify(normalizeCareerSelection(selection))) { setCreateError('먼저 원래 생성 요청의 결과를 확인하세요.'); return; }
    const release = mutationGate.current!.acquire(); if (!release) return;
    createLock.current = true;
    const operation = logicalCareerCreate(window.sessionStorage, selection); const controller = new AbortController(); createRequestRef.current?.abort(); createRequestRef.current = controller;
    const generation = generationRef.current;
    const isCurrent = () => !controller.signal.aborted && generation === generationRef.current && createRequestRef.current === controller;
    setCreatePending(true); setCreateError(null);
    try {
      const response = await createCareer({ schemaVersion: CAREER_SCHEMAS.createRequest, ...operation.selection, clientCommandId: operation.clientCommandId }, controller.signal);
      if (!isCurrent()) return;
      requireCareerReference(response.career, teamsRef.current, catalogRef.current);
      clearCareerCreateOperation(window.sessionStorage); window.sessionStorage.removeItem(draftKey(apiScope));
      setError(null); setIntegrityError(false); applyDetail(response.career, true);
      setPage('home'); setHistorical(false); setHistoryYear(null); setInboxFocus(null); setMarketPlayer(null); setScheduleTab('calendar'); setTrainingTab('training');
      commitEntry('club');
      setCalendarLoading(true); setCalendarError(null);
      try { const calendarView = await getCareerCalendar(response.career.careerId, controller.signal); if (!isCurrent()) return; reconcileCareerAdvanceOperation(window.sessionStorage, response.career.careerId, calendarView.activePendingAdvance); reconcileCareerCompetitionOperation(window.sessionStorage, response.career.careerId, calendarView.competition.revision, calendarView.competition.activePendingCommand, calendarView.activeCalendarSeasonYear); setCalendar(calendarView); }
      catch (calendarCause) { if (isCurrent()) setCalendarError(loadFailure(calendarCause)); }
      finally { if (isCurrent()) setCalendarLoading(false); }
      if (!isCurrent()) return;
      onNotify(response.replayed ? '저장 생성 결과 확인' : '새 게임 준비 완료', `${response.career.saveName} · ${response.career.managedTeamCode} 구단 운영을 시작합니다.`);
      try { const refreshed = await getCareers(controller.signal); if (isCurrent()) setList(refreshed); } catch { if (isCurrent()) setList((current) => withCreatedCareer(current, response.career)); }
    } catch (cause) {
      if (!isCurrent()) return;
      const failure = cause instanceof CareerApiFailure ? cause : new CareerApiFailure('NETWORK', loadFailure(cause));
      if (!isAmbiguousCareerCreateFailure(failure)) clearCareerCreateOperation(window.sessionStorage);
      setCreateError(failure.userMessage);
    } finally { release(); createLock.current = false; if (isCurrent()) setCreatePending(false); if (createRequestRef.current === controller) createRequestRef.current = null; }
  }, [applyDetail, createPending, onNotify, commitEntry, apiScope]);

  const advance = useCallback(async (mode: CareerAdvanceMode, restored?: CareerAdvanceOperation) => {
    if (!detail || !calendar || advancePending || historical || continuousBusy) return;
    const release = mutationGate.current!.acquire(); if (!release) return;
    const controller = new AbortController(); advanceRequestRef.current?.abort(); advanceRequestRef.current = controller;
    const generation = generationRef.current;
    const careerId = detail.careerId;
    const isCurrent = () => !controller.signal.aborted && generation === generationRef.current
      && selectedIdRef.current === careerId && advanceRequestRef.current === controller;
    setAdvancePending(true); setCalendarError(null);
    try {
    const operation = restored ?? logicalCareerAdvance(window.sessionStorage, detail.careerId, calendar.calendarRevision, mode);
      let response = await advanceCareerCalendar(detail.careerId, { schemaVersion: CAREER_SCHEMAS.advanceRequest, expectedCalendarRevision: operation.expectedCalendarRevision, mode: operation.mode, clientCommandId: operation.clientCommandId }, controller.signal); if (!isCurrent()) return;
      reconcileCareerCompetitionOperation(window.sessionStorage, detail.careerId, response.calendar.competition.revision, response.calendar.competition.activePendingCommand, response.calendar.activeCalendarSeasonYear); setCalendar(response.calendar);
      for (const delay of [400, 800, 1_200, 2_000, 3_000]) {
        if (!response.pending) break;
        await wait(delay, controller.signal); if (!isCurrent()) return;
        response = await advanceCareerCalendar(detail.careerId, { schemaVersion: CAREER_SCHEMAS.advanceRequest, expectedCalendarRevision: operation.expectedCalendarRevision, mode: operation.mode, clientCommandId: operation.clientCommandId }, controller.signal); if (!isCurrent()) return;
        reconcileCareerCompetitionOperation(window.sessionStorage, detail.careerId, response.calendar.competition.revision, response.calendar.competition.activePendingCommand, response.calendar.activeCalendarSeasonYear); setCalendar(response.calendar);
      }
      if (response.pending) {
        setCalendarError('Auto 경기 작업은 서버에서 계속 실행 중입니다. 같은 진행 작업 ID를 유지한 채 다시 확인할 수 있습니다.');
        return;
      }
      clearCareerAdvanceOperation(window.sessionStorage, detail.careerId); restoredAdvanceRef.current = null;
      setError(null); setIntegrityError(false);
      const latest = await getCareer(detail.careerId, controller.signal); if (!isCurrent()) return; requireCareerReference(latest, teamsRef.current, catalogRef.current); applyDetail(latest);
      try { const refreshed = await getCareers(controller.signal); if (!isCurrent()) return; setList(refreshed); } catch { if (isCurrent()) setList((current) => ({ ...current, careers: current.careers.map((entry) => entry.careerId === detail.careerId ? { ...entry, currentDate: response.calendar.currentDate } : entry) })); }
      if (!isCurrent()) return;
      if (response.stopReason === 'MANAGED_FIXTURE_REQUIRED') onNotify('관리 경기 도착', 'League 화면에서 관리 팀 Player Series를 완료하면 날짜 진행을 이어갈 수 있습니다.');
      else if (response.stopReason === 'ATTENTION_REQUIRED') onNotify('League 확인 필요', '차단 또는 재시작 필요 상태를 해결하기 전에는 날짜가 지나가지 않습니다.');
      else if (response.stopReason === 'SEASON_ROLLOVER_REQUIRED') onNotify('시즌 경계 도착', '새 시즌을 자동 생성하지 않고 시즌 전환 지점에서 멈췄습니다.');
      else onNotify(response.replayed ? '날짜 진행 결과 복원' : '날짜 진행 완료', `${response.calendar.currentDate} 서버 상태를 확인했습니다.`);
    } catch (cause) {
      if (!isCurrent()) return;
      const failure = cause instanceof CareerApiFailure ? cause : new CareerApiFailure('NETWORK', loadFailure(cause));
      if (!isAmbiguousCareerCreateFailure(failure)) { clearCareerAdvanceOperation(window.sessionStorage, detail.careerId); restoredAdvanceRef.current = null; }
      setCalendarError(failure.userMessage);
      try { const calendarView = await getCareerCalendar(detail.careerId, controller.signal); if (!isCurrent()) return; reconcileCareerAdvanceOperation(window.sessionStorage, detail.careerId, calendarView.activePendingAdvance); reconcileCareerCompetitionOperation(window.sessionStorage, detail.careerId, calendarView.competition.revision, calendarView.competition.activePendingCommand, calendarView.activeCalendarSeasonYear); setCalendar(calendarView); } catch { /* original failure remains visible */ }
    } finally { release(); if (isCurrent()) setAdvancePending(false); if (advanceRequestRef.current === controller) advanceRequestRef.current = null; }
  }, [advancePending, applyDetail, calendar, detail, historical, onNotify, continuousBusy]);

  const autoScope = useRef({ managing, historical, year: calendar?.activeCalendarSeasonYear });
  autoScope.current = { managing, historical, year: calendar?.activeCalendarSeasonYear };
  useEffect(() => {
    competitionRequestRef.current?.abort(); setCompetitionPending(false);
  }, [historical, historyYear, managing]);

  const observeAcceptedAuto = useCallback(async (target: CareerAutoTarget, controller: AbortController, generation: number) => {
    const isCurrent = () => !controller.signal.aborted && generation === generationRef.current
      && selectedIdRef.current === target.careerId && competitionRequestRef.current === controller
      && autoScope.current.managing && !autoScope.current.historical && autoScope.current.year === target.sourceYear;
    return observeCareerAuto(target, {
      signal: controller.signal, current: isCurrent,
      read: signal => getCareerCalendar(target.careerId, signal),
      calendar: next => { setCalendar(next); },
      message: setCalendarError,
      complete: async next => {
        const latest = await getCareer(target.careerId, controller.signal);
        if (!isCurrent()) return;
        requireCareerReference(latest, teamsRef.current, catalogRef.current);
        applyDetail(latest); setCalendar(next); setOperatingRevision(v => v + 1);
        clearCareerCompetitionOperation(window.sessionStorage, target.careerId);
        onNotify('대회 경기 완료', '원래 경기 결과가 반영됐습니다. 다음 일정을 확인하세요.');
      },
    });
  }, [applyDetail, onNotify]);

  const executeCompetition = useCallback(async () => {
    if (!detail || !calendar || competitionPending || historical || continuousBusy || mutationGate.current!.busy) return;
    const fixture = calendar.competition.nextFixture;
    const command = calendar.competition.allowedCommands[0];
    if (!fixture || !command) return;
    const release = mutationGate.current!.acquire(); if (!release) return;
    const controller = new AbortController(); competitionRequestRef.current?.abort(); competitionRequestRef.current = controller;
    const generation = generationRef.current;
    const careerId = detail.careerId;
    const isCurrent = () => !controller.signal.aborted && generation === generationRef.current
      && selectedIdRef.current === careerId && competitionRequestRef.current === controller;
    setCompetitionPending(true); setCalendarError(null);
    try {
    const operation = logicalCareerCompetition(window.sessionStorage,
      detail.careerId, calendar.competition.revision, undefined, calendar.activeCalendarSeasonYear);
    const body = {
      schemaVersion: CAREER_SCHEMAS.competitionCommandRequest,
      expectedCompetitionRevision: operation.expectedCompetitionRevision,
      clientCommandId: operation.clientCommandId,
      sourceYear: operation.sourceYear,
    } as const;
      const response = command === 'RECONCILE_COMPETITION_FIXTURE'
        ? await reconcileCareerCompetition(detail.careerId, body, controller.signal)
        : await startOrResumeCareerCompetition(detail.careerId, body, controller.signal); if (!isCurrent()) return;
      if (response.executionMode === 'PLAYER_CONTROLLED' && response.status !== 'COMPLETED') {
        if (!response.seriesId || !onOpenCompetitionSeries) throw new CareerApiFailure('CONTRACT', '관리 대회 Series 진입 정보를 확인할 수 없습니다.');
        onOpenCompetitionSeries(response.seriesId, detail,
          `${fixture.firstTeamCode ?? 'TBD'}${fixture.competitionId === 'LCK_CL' ? ' CL' : ''} vs ${fixture.secondTeamCode ?? 'TBD'}${fixture.competitionId === 'LCK_CL' ? ' CL' : ''}`);
        return;
      }
      if (response.status === 'BLOCKED') {
        setCalendarError(response.failureCode ?? '대회 Auto 경기 실행이 차단되었습니다.');
        return;
      }
      if (response.executionMode === 'FULL_AUTO' && ['PENDING', 'RUNNING'].includes(response.status)) {
        await observeAcceptedAuto({ careerId, sourceYear: calendar.activeCalendarSeasonYear,
          fixtureId: fixture.fixtureId, clientCommandId: operation.clientCommandId, jobId: response.jobId }, controller, generation);
        return;
      }
      clearCareerCompetitionOperation(window.sessionStorage, detail.careerId);
      const latestDetail = await getCareer(careerId, controller.signal); if (!isCurrent()) return;
      requireCareerReference(latestDetail, teamsRef.current, catalogRef.current);
      const latestCalendar = await getCareerCalendar(careerId, controller.signal); if (!isCurrent()) return;
      reconcileCareerAdvanceOperation(window.sessionStorage, careerId, latestCalendar.activePendingAdvance);
      reconcileCareerCompetitionOperation(window.sessionStorage, careerId, latestCalendar.competition.revision, latestCalendar.competition.activePendingCommand);
      applyDetail(latestDetail); setCalendar(latestCalendar); setOperatingRevision(v => v + 1);
      onNotify(response.replayed ? '대회 결과 복원' : '대회 경기 완료',
        `${fixture.competitionId} ${fixture.matchId} 결과가 다음 대진에 반영됐습니다.`);
    } catch (cause) {
      if (!isCurrent()) return;
      const failure = cause instanceof CareerApiFailure ? cause : new CareerApiFailure('NETWORK', loadFailure(cause));
      if (!isAmbiguousCareerCreateFailure(failure)) clearCareerCompetitionOperation(window.sessionStorage, detail.careerId);
      const original = readCareerCompetitionOperation(window.sessionStorage, careerId);
      setCalendarError(failure.userMessage);
      try { const latest = await getCareerCalendar(detail.careerId, controller.signal); if (!isCurrent()) return; reconcileCareerCompetitionOperation(window.sessionStorage, detail.careerId, latest.competition.revision, latest.competition.activePendingCommand, latest.activeCalendarSeasonYear); setCalendar(latest); } catch { /* original failure remains visible */ }
      if (isAmbiguousCareerCreateFailure(failure) && fixture.executionMode === 'FULL_AUTO' && isCurrent()) {
        if (original) await observeAcceptedAuto({ careerId, sourceYear: calendar.activeCalendarSeasonYear,
          fixtureId: fixture.fixtureId, clientCommandId: original.clientCommandId, jobId: fixture.jobId }, controller, generation);
      }
    } finally {
      release();
      if (isCurrent()) setCompetitionPending(false);
      if (competitionRequestRef.current === controller) competitionRequestRef.current = null;
    }
  }, [calendar, competitionPending, detail, historical, applyDetail, onNotify, onOpenCompetitionSeries, continuousBusy, observeAcceptedAuto]);

  useEffect(() => {
    if (!calendar || historical || competitionPending || continuousBusy || calendar.careerId !== selectedId || !managing) return;
    const fixture = calendar.competition.nextFixture, pending = calendar.competition.activePendingCommand;
    if (!fixture || fixture.executionMode !== 'FULL_AUTO' || fixture.resultApplicationStatus === 'APPLIED'
      || !['PENDING', 'RUNNING'].includes(fixture.jobStatus ?? '') || !pending
      || pending.competitionId !== fixture.competitionId || pending.matchId !== fixture.matchId) return;
    const release = mutationGate.current!.acquire(); if (!release) return;
    const controller = new AbortController(); competitionRequestRef.current?.abort(); competitionRequestRef.current = controller;
    const generation = generationRef.current;
    setCompetitionPending(true);
    void observeAcceptedAuto({ careerId: calendar.careerId, sourceYear: calendar.activeCalendarSeasonYear,
      fixtureId: fixture.fixtureId, clientCommandId: pending.clientCommandId, jobId: fixture.jobId }, controller, generation)
      .finally(() => {
        release();
        if (competitionRequestRef.current === controller) {
          competitionRequestRef.current = null;
          if (generation === generationRef.current) setCompetitionPending(false);
        }
      });
    // Scope invalidation owns cancellation. Calendar refreshes must not restart this observer.
  }, [calendar, historical, selectedId, competitionPending, continuousBusy, observeAcceptedAuto, managing]);

  useEffect(() => {
    if (!managing || !detail || !calendar || continuousBusy || advancePending || historical || mutationGate.current!.busy) return;
    const operation = readCareerAdvanceOperation(window.sessionStorage, detail.careerId);
    if (!operation || restoredAdvanceRef.current === operation.clientCommandId) return;
    restoredAdvanceRef.current = operation.clientCommandId; void advance(operation.mode, operation);
  }, [advance, advancePending, calendar, detail, historical, mutationPending, continuousBusy, managing]);

  const pendingOperation = readCareerCreateOperation(window.sessionStorage);

  useEffect(() => {
    if (!inboxFocus || inboxFocus.panel !== 'MATCH' || !inboxFocus.current || !calendar || !selectedId || continuousBusy) return;
    const focus = inboxFocus, controller = new AbortController();
    if (focus.seasonYear !== calendar.activeCalendarSeasonYear || calendar.blockingReason === 'SEASON_CANCELLED') {
      setInboxFocus(current => current === focus ? null : current); return;
    }
    void scoutingRequest<Opponent>(selectedId, '/opponent', controller.signal).then(view => {
      if (!controller.signal.aborted && view.careerId === selectedId && !preparationStillCurrent(focus, view.next?.link ?? null, calendar.activeCalendarSeasonYear)) {
        setInboxFocus(current => current === focus ? null : current);
      }
    }).catch(() => { /* A failed observation cannot prove the prepared fixture ended. */ });
    return () => controller.abort();
  }, [inboxFocus, selectedId, calendar?.calendarRevision, calendar?.competition.revision, calendar?.activeCalendarSeasonYear, calendar?.blockingReason, continuousBusy]);

  const navigateCareer = (link: InboxLink) => {
    if (!detail || mutationGate.current!.busy) return;
    const career = detail, token = ++navigationGeneration.current;
    void currentNavigation(link, () => loadDetail(career.careerId), () => token === navigationGeneration.current && selectedIdRef.current === career.careerId, () => {
      if (link.current) { setHistorical(false); setHistoryYear(null); }
      else { setHistorical(link.seasonYear !== calendar?.activeCalendarSeasonYear); setHistoryYear(link.seasonYear); }
      setManaging(true); setPage(pageForLink(link));
      if (link.panel === 'MATCH') setScheduleTab('calendar');
      if (link.panel === 'SEASONS') setScheduleTab('seasons');
      if (link.panel === 'LIFECYCLE') setTrainingTab('lifecycle');
      if (link.panel === 'TRAINING') setTrainingTab('training');
      setInboxFocus({ ...link }); if (link.playerId) setMarketPlayer(link.playerId);
      if (link.panel === 'MATCH') {
        const destination = matchDestination(link);
        if (destination === 'SERIES') onOpenCompetitionSeries?.(link.seriesId!, latestCareer.current ?? career, '관리 경기');
        else if (destination === 'LEAGUE_PREPARATION') onResume({ ...career, ...latestCareer.current });
      }
    });
  };
  useEffect(() => {
    if (!inboxFocus || calendarLoading || detailLoading || ['MARKET', 'TRADE'].includes(inboxFocus.panel)) return;
    const selectors: Record<string, string> = { MATCH: '[aria-label="Career 캘린더"]', FINANCE: '[aria-label="구단 재정과 시즌 목표"]', ROSTER: '#career-roster', TRAINING: '#career-training', LIFECYCLE: '#career-lifecycle', SEASONS: '[aria-label="시즌 전환과 기록"]', RECORDS: '.ca-records', AWARDS: '.ca-records' };
    focusPlayTarget(selectors[inboxFocus.panel] ?? '.ca-records');
  }, [inboxFocus, calendarLoading, detailLoading, page, scheduleTab, trainingTab]);
  function openPage(next: CareerPage) {
    ++navigationGeneration.current;
    if (mutationGate.current!.busy) { queuedPage.current = next; onNotify('화면 이동 대기', '접수한 요청의 응답을 확인한 뒤 선택한 화면으로 이동합니다.'); return; }
    if (historical && ['home', 'inbox', 'scouting'].includes(next) && detail) {
      const token = navigationGeneration.current, careerId = detail.careerId;
      void loadDetail(careerId).then(ok => { if (ok && token === navigationGeneration.current && selectedIdRef.current === careerId) { setHistorical(false); setHistoryYear(null); setManaging(true); setPage(next); setInboxFocus(null); } });
      return;
    }
    if (next !== 'market') setMarketPlayer(null);
    setManaging(true); setPage(next); setInboxFocus(null);
  }
  useEffect(() => { if (!mutationPending && queuedPage.current) { const next = queuedPage.current; queuedPage.current = null; openPage(next); if (next === 'schedule') setScheduleTab('calendar'); } }, [mutationPending]);
  useEffect(() => { if (selectedId && detail?.careerId === selectedId && !detailLoading) writeCareerLocation(window.sessionStorage, selectedId, { page, year: historical ? historyYear : null, focus: inboxFocus, scheduleTab, trainingTab }); }, [selectedId, detail, detailLoading, page, historical, historyYear, inboxFocus, scheduleTab, trainingTab]);
  useEffect(() => { if (managing && !detailLoading) pageTitle.current?.focus(); }, [page, managing, detailLoading]);
  const navigateEntry = useCallback((next: CareerEntryView, replace = false) => {
    if (mutationGate.current!.busy && !createLock.current) { onNotify('요청 확인 중', '접수한 요청의 응답을 확인한 뒤 이동할 수 있습니다.'); return; }
    ++navigationGeneration.current; queuedPage.current = null; invalidateScreenRequests(); onCareerSelectionChange?.();
    commitEntry(next, replace);
    if (next === 'club') { const id = readCareerPointer(window.sessionStorage); if (id) void loadDetail(id); else commitEntry('main', true); }
  }, [invalidateScreenRequests, onCareerSelectionChange, commitEntry, loadDetail, onNotify]);
  useEffect(() => {
    const pop = (event: PopStateEvent) => {
      const saved = event.state?.careerEntry;
      if (saved?.api !== apiScope || !['main', 'load', 'new', 'club'].includes(saved.view)) return;
      if (mutationGate.current!.busy && !createLock.current) { commitEntry(entryViewRef.current, true); return; }
      navigateEntry(saved.view, true);
    };
    window.history.replaceState({ ...window.history.state, careerEntry: { api: apiScope, view: entryViewRef.current } }, '');
    window.addEventListener('popstate', pop); return () => window.removeEventListener('popstate', pop);
  }, [apiScope, navigateEntry, commitEntry]);
  const showSaves = () => navigateEntry('load');
  const goProgress = () => { openPage('schedule'); if (!mutationGate.current!.busy) setScheduleTab('calendar'); };
  const currentSeason = async () => { if (!detail || mutationGate.current!.busy) return; const careerId = detail.careerId, token = ++navigationGeneration.current; if (await loadDetail(careerId) && token === navigationGeneration.current && selectedIdRef.current === careerId) { setHistorical(false); setHistoryYear(null); setInboxFocus(null); } };
  const ready = !!detail && !detailLoading && !error;
  if (!managing) {
    let preferred = readCareerPointer(window.sessionStorage);
    try { preferred ??= window.localStorage.getItem(recentKey(apiScope)); } catch { /* Optional hint. */ }
    return <CareerEntryScreen view={entryView === 'club' ? 'main' : entryView} api={apiScope} list={list} loading={initialLoading} error={error} preferred={preferred}
      teams={teams} teamsLoading={teamsLoading} teamsError={teamsError} pending={createPending} operation={pendingOperation} createError={createError}
      onNavigate={navigateEntry} onLoad={id => { commitEntry('club'); void loadDetail(id, true); }} onCreate={selection => { void create(selection); }}
      onRefresh={() => { void loadWorkspace(); }} onRetryTeams={() => setTeamRefresh(v => v + 1)} onTool={onTool ? tool => { invalidateScreenRequests(); onCareerSelectionChange?.(); onTool(tool); } : undefined} />;
  }
  return <CareerShell page={page} managing={managing} club={ready ? detail.compatibility?.managedTeamName ?? detail.managedTeamCode : null}
    date={ready ? continuous?.career === detail.careerId && continuous.active && continuous.date ? continuous.date : calendar?.currentDate ?? detail.currentDate : null} year={ready ? historical ? historyYear : calendar?.activeCalendarSeasonYear ?? null : null}
    scopeLabel={page === 'records' && recordScope?.careerId === selectedId ? recordScope.year === null ? '조회 범위 · 통산 기록' : `조회 시즌 ${recordScope.year} · 경기 기록` : undefined} historical={historical} status={mutationPending ? '요청 처리 · 응답 확인 중' : ready ? continuousStatus : '저장 선택 대기'} disabled={mutationPending || detailLoading}
    onPage={openPage} onMain={() => navigateEntry('main')} onSaves={showSaves} onTool={onTool ? tool => { if (!mutationGate.current!.busy) onTool(tool); } : undefined} onContinue={goProgress} onRefresh={() => { if (detail && !mutationGate.current!.busy) void loadDetail(detail.careerId); }}>
    <main className="ca-workspace ca-operation-workspace" aria-labelledby="ca-page-title">
      <header className="ca-operation-head"><div><p>{managing && detail ? `${detail.saveName} · ${detail.managerName} 감독` : 'CAREER'}</p><h1 id="ca-page-title" ref={pageTitle} tabIndex={-1}>{managing ? careerPages.find(([id]) => id === page)?.[1] : '구단 운영 시작'}</h1></div>
        <button className="lm-text-button" onClick={showSaves}>저장 바꾸기</button>
      </header>
      {initialLoading ? <section className="ca-loading" role="status">저장 상태 확인 중…</section> : null}
      {error ? <section className="ca-error" role="alert"><strong>저장 확인 필요</strong><p>{error}</p><button onClick={() => { void loadWorkspace(); }}>목록 다시 확인</button><button onClick={showSaves}>다른 저장 선택</button></section> : null}
      {managing && detailLoading ? <p role="status">선택한 구단을 불러오는 중…</p> : null}
      {managing && ready ? <section className="ca-detail ca-operation-detail">
        {historical ? <div className="ca-history-notice" role="status">게임 날짜 {calendar?.currentDate ?? detail.currentDate} · 조회 시즌 {historyYear}. 과거 자료의 운영 명령은 제한됩니다. <button disabled={mutationPending} onClick={() => { void currentSeason(); }}>현재 시즌으로 전환</button></div> : null}
        {detail.compatibility?.sourceChanged ? <p>기본 자료가 변경되었습니다. 이 Career는 저장된 선수·계약·성장 상태로 운영합니다.</p> : null}
        {inboxFocus ? <p role="status">선택한 원본 {inboxFocus.seasonYear} 시즌 업무·기록 <button onClick={() => openPage('inbox')}>현재 업무로 돌아가기</button></p> : null}
        {page === 'home' && calendar ? <CareerHomePage key={detail.careerId} careerId={detail.careerId} year={calendar.activeCalendarSeasonYear} revision={calendar.calendarRevision + operatingRevision} returnedSeriesId={returnedSeries?.careerId === detail.careerId ? returnedSeries.seriesId : undefined} busy={mutationPending || continuousBusy} onNavigate={navigateCareer} onPage={openPage} /> : null}
        {(page === 'home' || page === 'schedule') && returnedSeries?.careerId === detail.careerId ? <CareerReturnResult key={`${detail.careerId}:${returnedSeries.seriesId}`} careerId={detail.careerId} seriesId={returnedSeries.seriesId} revision={operatingRevision} busy={mutationPending || continuousBusy} onNavigate={navigateCareer} /> : null}
        {calendar && (page === 'inbox') ? <CareerInboxPanel key={`inbox:${detail.careerId}`} careerId={detail.careerId} year={calendar.activeCalendarSeasonYear} revision={calendar.calendarRevision + operatingRevision} running={continuousBusy} busy={mutationPending || continuousBusy} onNavigate={navigateCareer} /> : null}
        {calendar && (page === 'scouting') ? <CareerScoutingPanel key={`scouting:${detail.careerId}`} careerId={detail.careerId} year={calendar.activeCalendarSeasonYear} revision={calendar.calendarRevision + operatingRevision} busy={mutationPending || continuousBusy} onNavigate={navigateCareer} showOpponent={opponentRequest} /> : null}
        {page === 'schedule' ? <nav className="ca-subnav" aria-label="일정 대회 보기">{[['calendar','Calendar · 진행'],['seasons','국내 · 국제 · 시즌'],['overseas','해외 리그'],['cl','CL']].map(([id,label]) => <button key={id} aria-current={scheduleTab === id ? 'page' : undefined} disabled={mutationPending} onClick={() => { setScheduleTab(id); setInboxFocus(null); }}>{label}</button>)}</nav> : null}
        {page === 'training' ? <nav className="ca-subnav" aria-label="훈련 성장 보기">{[['training','훈련 · 숙련도 · 피로'],['lifecycle','성장 · 생애주기']].map(([id,label]) => <button key={id} aria-current={trainingTab === id ? 'page' : undefined} onClick={() => setTrainingTab(id)} disabled={mutationPending}>{label}</button>)}</nav> : null}
        {calendar && (page === 'schedule' && scheduleTab === 'seasons') ? <CareerSeasonsPanel selectedYear={historical ? historyYear : null} key={detail.careerId} careerId={detail.careerId} revision={calendar?.calendarRevision ?? 0} busy={mutationPending || continuousBusy} onBegin={() => { if (continuousBusy) return null; const release = mutationGate.current!.acquire(); if (!release) return null; invalidateScreenRequests(); return release; }} onChanged={async () => { setOperatingRevision(v => v + 1); await loadDetail(detail.careerId); }} onHistory={(past, year) => { ++navigationGeneration.current; setInboxFocus(null); setHistorical(past); setHistoryYear(past ? year ?? null : null); }} onReplay={(series, matchup) => onOpenCompetitionSeries?.(series, detail, matchup)} /> : null}
        {calendar && (page === 'schedule' && scheduleTab === 'calendar' && !historical) ? <CareerCalendarPanel onClosePreparation={() => setInboxFocus(null)} onAnalyzeOpponent={() => { setOpponentRequest(v => v + 1); openPage('scouting'); }} focusFixture={inboxFocus?.panel === 'MATCH' ? inboxFocus.sourceId : null} calendar={calendar} loading={calendarLoading} pending={advancePending || mutationPending || continuousBusy} competitionPending={competitionPending || mutationPending || continuousBusy} error={calendarError} onAdvance={(mode) => { void advance(mode); }} onCompetitionAction={() => { void executeCompetition(); }} onRefresh={() => { void loadDetail(detail.careerId); }} /> : null}
        {calendar && (page === 'schedule' && scheduleTab === 'overseas') ? <CareerOverseasPanel careerId={detail.careerId} year={historical && historyYear ? historyYear : calendar.activeCalendarSeasonYear} revision={calendar.calendarRevision + calendar.competition.revision + operatingRevision} onReplay={(series, matchup) => onOpenCompetitionSeries?.(series, detail, matchup)} /> : null}
        {calendar && (page === 'schedule' && scheduleTab === 'cl') ? <CareerClPanel onRoster={() => openPage('roster')} onMarket={() => openPage('market')} key={`cl:${detail.careerId}`} careerId={detail.careerId} year={historical && historyYear ? historyYear : calendar.activeCalendarSeasonYear} revision={calendar.calendarRevision + operatingRevision} historical={historical} busy={mutationPending || continuousBusy} nextMatch={calendar.competition?.nextFixture?.competitionId === 'LCK_CL' ? calendar.competition.nextFixture.matchId : null} onBegin={() => historical || continuousBusy ? null : mutationGate.current!.acquire()} onChanged={async () => { setOperatingRevision(v => v + 1); await loadDetail(detail.careerId); }} onExecute={() => { void executeCompetition(); }} onReplay={(series, matchup) => onOpenCompetitionSeries?.(series, detail, matchup)} /> : null}
        {calendar && (page === 'roster') ? <CareerRosterPanel onInbox={() => openPage('inbox')} focus={inboxFocus} onManageContract={id => { setMarketPlayer(id); openPage('market'); }} key={`roster:${detail.careerId}`} careerId={detail.careerId} year={historical && historyYear ? historyYear : calendar.activeCalendarSeasonYear} revision={calendar.calendarRevision + operatingRevision} historical={historical} busy={mutationPending || continuousBusy} onBegin={() => historical || continuousBusy ? null : mutationGate.current!.acquire()} onChanged={async () => { setOperatingRevision(v => v + 1); await loadDetail(detail.careerId); }} /> : null}
        {calendar && (page === 'training' && trainingTab === 'lifecycle') ? <CareerLifecyclePanel focusPlayer={inboxFocus?.panel === 'LIFECYCLE' ? inboxFocus.playerId : null} key={`lifecycle:${detail.careerId}`} careerId={detail.careerId} year={historical && historyYear ? historyYear : calendar.activeCalendarSeasonYear} revision={calendar.calendarRevision + operatingRevision} historical={historical} busy={mutationPending || continuousBusy} onMarket={id => { setMarketPlayer(id); openPage('market'); }} /> : null}
        {calendar && (page === 'training' && trainingTab === 'training') ? <CareerTrainingPanel focusPlayer={inboxFocus?.panel === 'TRAINING' ? inboxFocus.playerId : null} key={`training:${detail.careerId}`} careerId={detail.careerId} year={historical && historyYear ? historyYear : calendar.activeCalendarSeasonYear} revision={calendar.calendarRevision + operatingRevision} historical={historical} busy={mutationPending || continuousBusy} onBegin={() => historical || continuousBusy ? null : mutationGate.current!.acquire()} onChanged={async () => { setOperatingRevision(v => v + 1); await loadDetail(detail.careerId); }} /> : null}
        {calendar && (page === 'market' || page === 'finance') ? <CareerMarketPanel onCloseDetail={() => { setMarketPlayer(null); setInboxFocus(null); }} section={page === 'finance' ? 'finance' : 'market'} onInbox={() => openPage('inbox')} focus={inboxFocus} key={`market:${detail.careerId}`} careerId={detail.careerId} year={historical && historyYear ? historyYear : calendar.activeCalendarSeasonYear} revision={calendar.calendarRevision + operatingRevision} historical={historical} busy={mutationPending || continuousBusy} focusPlayer={marketPlayer} onBegin={() => historical || continuousBusy ? null : mutationGate.current!.acquire()} onChanged={async () => { setOperatingRevision(v => v + 1); await loadDetail(detail.careerId); }} /> : null}
        {calendar && (page === 'records') ? <CareerRecordsPanel initiallyOpen onScope={onRecordScope} focus={inboxFocus && ['RECORDS', 'AWARDS'].includes(inboxFocus.panel) ? inboxFocus : null} key={`records:${detail.careerId}`} careerId={detail.careerId} year={historical && historyYear ? historyYear : calendar?.activeCalendarSeasonYear ?? Number(detail.currentDate.slice(0, 4))} revision={(calendar?.calendarRevision ?? 0) + operatingRevision} busy={continuousBusy || mutationPending} /> : null}
        {calendarError ? <p role="alert">{calendarError}</p> : null}
        {page === 'schedule' && scheduleTab === 'calendar' ? <details><summary>정규리그 준비 · 연결 상태</summary><button disabled={historical || mutationPending || continuousBusy} onClick={() => { if (!mutationGate.current!.busy) onResume(detail); }}>{CAREER_RESUME_COPY[detail.resume.kind].label}</button></details> : null}
        <details className="ca-diagnostics"><summary>저장 연결 상세</summary><p>Career {detail.careerId}</p><p>League {detail.leagueId} · Season {detail.seasonId}</p><p>저장 시각 {dateTime(detail.updatedAt)} · Calendar revision {calendar?.calendarRevision ?? '미제공'}</p></details>
      </section> : null}
      {ready && calendar ? <CareerContinuousPanel visible={managing && !historical && (page === 'home' || page === 'schedule' && scheduleTab === 'calendar')} onStatus={setContinuousStatus} key={`continuous:${detail.careerId}`} careerId={detail.careerId} currentDate={calendar.currentDate} seasonYear={calendar.activeCalendarSeasonYear} busy={mutationPending} onBusy={(career, active, date) => setContinuous({ career, active, date })} onBegin={() => mutationGate.current!.acquire()} onAction={(action) => { if (action === 'MATCH') { const link = calendarMatchLink(calendar); if (link) navigateCareer(link); else goProgress(); } else { openPage(action === 'SEASONS' ? 'schedule' : 'inbox'); if (action === 'SEASONS') setScheduleTab('seasons'); } }} appliedRun={appliedContinuous.current.get(detail.careerId) ?? null} onStopped={async stamp => { setOperatingRevision(v => v + 1); return await loadDetail(detail.careerId, false, stamp) === true; }} /> : null}
    </main>
  </CareerShell>;
}
