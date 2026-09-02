import { useEffect, useMemo, useRef, useState, type RefObject } from 'react';
import { fetchPlayerDetail, fetchTeamPlayerWorkspace, TeamPlayerApiFailure } from './api/teamPlayerApi.client';
import type { PlayerSummaryDto, TeamPlayerWorkspaceDto } from './api/teamPlayerApi.types';
import { createPlayerProfile, type PlayerProfileViewModel } from './teamPlayer.adapter';
import { PlayerDetailRequestCoordinator, resolveInitialSelection, selectTeam, selectedPlayer, type TeamPlayerSelection, writeSelection } from './teamPlayer.selection';
import { PlayerProfile } from './components/PlayerProfile';

type WorkspaceState =
  | { status: 'loading'; data: null; error: null }
  | { status: 'ready'; data: TeamPlayerWorkspaceDto; error: null }
  | { status: 'error'; data: null; error: TeamPlayerApiFailure };

type DetailState =
  | { status: 'idle' | 'loading'; profile: null; error: null }
  | { status: 'ready'; profile: PlayerProfileViewModel; error: null }
  | { status: 'error'; profile: null; error: TeamPlayerApiFailure };

interface TeamPlayerInformationPageProps {
  searchValue: string;
  onSearchChange: (value: string) => void;
}

function failureDetail(error: TeamPlayerApiFailure): string {
  return [error.httpStatus ? `HTTP ${error.httpStatus}` : null, error.code ? `CODE ${error.code}` : null, error.field ? `FIELD ${error.field}` : null]
    .filter(Boolean).join(' · ');
}

function ErrorState({ title, error, onRetry, retryRef }: { title: string; error: TeamPlayerApiFailure; onRetry: () => void; retryRef?: RefObject<HTMLButtonElement> }) {
  return (
    <div className="tp-error" role="alert">
      <span aria-hidden="true">!</span>
      <div><strong>{title}</strong><p>{error.userMessage}</p>{failureDetail(error) ? <code>{failureDetail(error)}</code> : null}</div>
      <button ref={retryRef} className="lm-secondary-button" type="button" onClick={onRetry}>다시 시도</button>
    </div>
  );
}

export function TeamPlayerInformationPage({ searchValue, onSearchChange }: TeamPlayerInformationPageProps) {
  const [workspaceRevision, setWorkspaceRevision] = useState(0);
  const [detailRevision, setDetailRevision] = useState(0);
  const [workspaceState, setWorkspaceState] = useState<WorkspaceState>({ status: 'loading', data: null, error: null });
  const [selection, setSelection] = useState<TeamPlayerSelection | null>(null);
  const [detailState, setDetailState] = useState<DetailState>({ status: 'idle', profile: null, error: null });
  const workspaceRetryRef = useRef<HTMLButtonElement>(null);
  const detailRetryRef = useRef<HTMLButtonElement>(null);
  const detailCoordinator = useRef(new PlayerDetailRequestCoordinator<ReturnType<typeof createPlayerProfile>>());

  useEffect(() => {
    const controller = new AbortController();
    setWorkspaceState({ status: 'loading', data: null, error: null });
    setSelection(null);
    setDetailState({ status: 'idle', profile: null, error: null });
    fetchTeamPlayerWorkspace(controller.signal).then((data) => {
      if (controller.signal.aborted) return;
      setWorkspaceState({ status: 'ready', data, error: null });
      setSelection(resolveInitialSelection(data, window.sessionStorage));
    }).catch((error: unknown) => {
      if (controller.signal.aborted) return;
      controller.abort();
      const failure = error instanceof TeamPlayerApiFailure ? error : new TeamPlayerApiFailure('NETWORK', '선수 정보 화면을 불러오지 못했습니다.', null, null, null, true);
      if (failure.kind !== 'CANCELLED') setWorkspaceState({ status: 'error', data: null, error: failure });
    });
    return () => controller.abort();
  }, [workspaceRevision]);

  const workspace = workspaceState.data;
  const selectedSummary = useMemo(() => workspace && selection ? selectedPlayer(workspace, selection.playerId) : null, [workspace, selection]);

  useEffect(() => {
    if (!workspace || !selection || !selectedSummary) return;
    writeSelection(window.sessionStorage, selection, workspace.metadata.catalog.catalogVersion);
    let active = true;
    setDetailState({ status: 'loading', profile: null, error: null });
    detailCoordinator.current.load(selectedSummary.playerId, (signal) => fetchPlayerDetail(selectedSummary, workspace.metadata.catalog, signal).then(createPlayerProfile))
      .then((profile) => { if (active) setDetailState({ status: 'ready', profile, error: null }); })
      .catch((error: unknown) => {
        if (!active) return;
        const failure = error instanceof TeamPlayerApiFailure ? error : new TeamPlayerApiFailure('NETWORK', '선수 상세 정보를 불러오지 못했습니다.', null, null, null, true);
        if (failure.kind !== 'CANCELLED') setDetailState({ status: 'error', profile: null, error: failure });
      });
    return () => { active = false; };
  }, [workspace, selection, selectedSummary, detailRevision]);

  useEffect(() => () => detailCoordinator.current.abort(), []);
  useEffect(() => { if (workspaceState.status === 'error') workspaceRetryRef.current?.focus(); }, [workspaceState.status]);
  useEffect(() => { if (detailState.status === 'error') detailRetryRef.current?.focus(); }, [detailState.status]);

  if (workspaceState.status === 'loading') {
    return (
      <main className="tp-workspace tp-workspace--center" aria-labelledby="tp-page-title" aria-busy="true">
        <div className="tp-loading" role="status" aria-live="polite"><span aria-hidden="true" /><p>LCK 선수단을 확인하고 있습니다.</p><small>metadata · teams · players</small></div>
      </main>
    );
  }

  if (workspaceState.status === 'error') {
    return (
      <main className="tp-workspace tp-workspace--center" aria-labelledby="tp-page-title">
        <h1 id="tp-page-title" className="lm-sr-only">LCK 선수단</h1>
        <ErrorState title="팀 목록을 불러오지 못했습니다" error={workspaceState.error} retryRef={workspaceRetryRef} onRetry={() => setWorkspaceRevision((value) => value + 1)} />
      </main>
    );
  }

  const readyWorkspace = workspaceState.data;
  const selectedTeam = readyWorkspace.teams.teams.find((team) => team.teamCode === selection?.teamCode) ?? readyWorkspace.teams.teams[0];
  const canonicalPlayers = searchValue.trim()
    ? readyWorkspace.players.players.filter((player) => {
      const query = searchValue.trim().toLocaleLowerCase('en-US');
      return [player.nickname, player.playerId, player.currentTeamCode, player.position].some((value) => value.toLocaleLowerCase('en-US').includes(query));
    })
    : selectedTeam.lineup.map((lineupPlayer) => readyWorkspace.players.players.find((player) => player.playerId === lineupPlayer.playerId)).filter((player): player is PlayerSummaryDto => player !== undefined);

  const choosePlayer = (player: PlayerSummaryDto) => {
    if (selection?.playerId === player.playerId) return;
    setSelection({ teamCode: player.currentTeamCode, playerId: player.playerId });
  };

  return (
    <main className="tp-workspace" aria-labelledby="tp-page-title">
      <header className="tp-workspace__header">
        <div><p>REFERENCE DATABASE · CURRENT STARTERS</p><h1 id="tp-page-title">LCK 선수단</h1></div>
        <dl>
          <div><dt>팀</dt><dd>{readyWorkspace.metadata.counts.teams}</dd></div>
          <div><dt>선수</dt><dd>{readyWorkspace.metadata.counts.players}</dd></div>
          <div><dt>스냅샷</dt><dd>2026-08-24</dd></div>
        </dl>
        <div className="tp-catalog-stamp" title={readyWorkspace.metadata.catalog.catalogHash}>
          <span>CATALOG</span><strong>{readyWorkspace.metadata.catalog.catalogVersion}</strong><code>{readyWorkspace.metadata.catalog.catalogHash.slice(0, 12)}…</code>
        </div>
      </header>
      <div className="tp-workspace__grid">
        <nav className="tp-team-nav" aria-label="LCK 팀 목록">
          <header><span>01</span><div><strong>TEAM INDEX</strong><small>API canonical order</small></div></header>
          <ol>
            {readyWorkspace.teams.teams.map((team, index) => {
              const active = team.teamCode === selectedTeam.teamCode;
              return (
                <li key={team.teamCode}><button type="button" className={active ? 'is-selected' : ''} aria-current={active ? 'true' : undefined} onClick={() => { if (!active) { onSearchChange(''); setSelection(selectTeam(readyWorkspace, team.teamCode)); } }}><span>{String(index + 1).padStart(2, '0')}</span><strong>{team.teamCode}</strong><small>{active ? '선택' : `${team.starterCount}명`}</small></button></li>
              );
            })}
          </ol>
        </nav>
        <section className="tp-roster" aria-labelledby="tp-roster-title">
          <header><div><p>{searchValue.trim() ? 'SEARCH RESULTS' : 'STARTING FIVE'}</p><h2 id="tp-roster-title">{searchValue.trim() ? `${canonicalPlayers.length}명 검색됨` : `${selectedTeam.teamCode} 주전`}</h2></div><span>{searchValue.trim() ? '전체 50명' : '5 / 5'}</span></header>
          {searchValue.trim() ? <button className="tp-clear-search" type="button" onClick={() => onSearchChange('')}>검색 지우기 · 팀 주전으로</button> : null}
          {canonicalPlayers.length === 0 ? <p className="tp-empty-state" role="status">현재 50명 summary에서 일치하는 선수가 없습니다.</p> : (
            <ol className="tp-player-list">
              {canonicalPlayers.map((player) => {
                const active = player.playerId === selection?.playerId;
                return <li key={player.playerId}><button type="button" className={active ? 'is-selected' : ''} aria-current={active ? 'true' : undefined} onClick={() => choosePlayer(player)}><span className="tp-player-list__position">{player.position}</span><span><strong>{player.nickname}</strong><code>{player.playerId}</code></span>{searchValue.trim() ? <small>{player.currentTeamCode}</small> : <small>{active ? '선택됨' : '보기'}</small>}<i aria-hidden="true" /></button></li>;
              })}
            </ol>
          )}
          <footer><span>Identity</span><strong>PlayerId · TeamCode · Position</strong></footer>
        </section>
        <section className="tp-detail" aria-label="선수 상세" aria-busy={detailState.status === 'loading'}>
          <div className="lm-sr-only" role="status" aria-live="polite">{detailState.status === 'loading' ? `${selectedSummary?.nickname ?? '선수'} 상세 정보를 불러오는 중입니다.` : detailState.status === 'ready' ? `${detailState.profile.nickname} 상세 정보를 표시했습니다.` : ''}</div>
          {detailState.status === 'loading' || detailState.status === 'idle' ? (
            <div className="tp-detail-loading" role="status"><span aria-hidden="true" /><strong>{selectedSummary?.nickname ?? '선수'} 프로필 로딩</strong><small>stable PlayerId로 상세 응답을 검증합니다.</small></div>
          ) : detailState.status === 'error' ? (
            <ErrorState title="선수 상세를 불러오지 못했습니다" error={detailState.error} retryRef={detailRetryRef} onRetry={() => setDetailRevision((value) => value + 1)} />
          ) : detailState.profile ? <PlayerProfile profile={detailState.profile} /> : null}
        </section>
      </div>
    </main>
  );
}
