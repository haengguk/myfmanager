import { PlayerDataEditor } from '../player-data/PlayerDataEditor';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import type { AppSection } from '../../layout/Sidebar';
import { PlayerPortrait } from '../team-player/components/PlayerPortrait';
import type { TeamSummaryDto } from '../team-player/api/teamPlayerApi.types';
import { getCareer } from './api/careerApi.client';
import type { CareerListResponseDto, CareerViewDto } from './api/careerApi.types';
import { normalizeCareerSelection, type CareerCreateOperation, type CareerCreateSelection } from './career.pointer';
import { displayNameError, draftKey, readEntryDraft, recentCareer, type CareerEntryView } from './careerEntry';
import './careerEntry.css';

const savedAt = (value: string) => new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
export function CareerEntryScreen({ view, api, list, loading, error, preferred, teams, teamsLoading, teamsError, pending, operation, createError, onNavigate, onLoad, onCreate, onRefresh, onRetryTeams, onTool }: {
  view: Exclude<CareerEntryView, 'club'>; api: string; list: CareerListResponseDto; loading: boolean; error: string | null; preferred: string | null;
  teams: readonly TeamSummaryDto[]; teamsLoading: boolean; teamsError: string | null; pending: boolean; operation: CareerCreateOperation | null; createError: string | null;
  onNavigate: (view: CareerEntryView) => void; onLoad: (id: string) => void; onCreate: (selection: CareerCreateSelection) => void; onRefresh: () => void; onRetryTeams: () => void; onTool?: (tool: AppSection) => void;
}) {
  const [query, setQuery] = useState(''), [selectedId, setSelectedId] = useState<string | null>(null);
  const [preview, setPreview] = useState<CareerViewDto | null>(null), [previewError, setPreviewError] = useState(''), [previewLoading, setPreviewLoading] = useState(false);
  const [draft, setDraft] = useState(() => readEntryDraft(sessionStorage, api));
  const [submitted, setSubmitted] = useState(false), [editorOpen, setEditorOpen] = useState(false);
  const title = useRef<HTMLHeadingElement>(null), previewGeneration = useRef(0);
  const recent = recentCareer(list.careers, preferred);
  const selected = list.careers.find(c => c.careerId === selectedId) ?? null;
  const target = view === 'main' ? recent.career : selected;
  useEffect(() => { title.current?.focus(); document.title = `${view === 'main' ? 'LOL MANAGER' : view === 'new' ? '새 게임' : '불러오기'} — lolmanager`; }, [view]);
  useEffect(() => {
    const controller = new AbortController(), token = ++previewGeneration.current;
    setPreview(null); setPreviewError(''); setPreviewLoading(false);
    if (loading || !target || target.compatibility?.status === 'UNSUPPORTED') return;
    setPreviewLoading(true);
    void getCareer(target.careerId, controller.signal).then(value => {
      if (!controller.signal.aborted && token === previewGeneration.current) {
        if (value.careerId !== target.careerId) throw new Error('선택한 저장과 조회 결과가 다릅니다.');
        setPreview(value);
      }
    }).catch(cause => { if (!controller.signal.aborted && token === previewGeneration.current) setPreviewError(cause instanceof Error ? cause.message : '저장 상세를 불러오지 못했습니다.'); })
      .finally(() => { if (!controller.signal.aborted && token === previewGeneration.current) setPreviewLoading(false); });
    return () => { ++previewGeneration.current; controller.abort(); };
  }, [target?.careerId, view, list, loading]);
  useEffect(() => { sessionStorage.setItem(draftKey(api), JSON.stringify(draft)); }, [api, draft]);
  const input = operation?.selection ?? draft;
  const team = teams.find(t => t.teamCode === input.managedTeamCode);
  const locked = pending || !!operation;
  const nameError = submitted ? displayNameError(input.managerName) : null, saveError = submitted ? displayNameError(input.saveName) : null;
  const teamError = submitted && !team ? '서버 목록에서 관리할 구단을 선택하세요.' : null;
  const canLoad = !!preview && preview.careerId === target?.careerId && preview.compatibility?.status !== 'UNSUPPORTED' && !previewLoading && !loading && !error;
  function submit(event: FormEvent) {
    event.preventDefault(); if (pending) return;
    if (operation) { onCreate(operation.selection); return; }
    setSubmitted(true);
    if (!team || displayNameError(input.managerName) || displayNameError(input.saveName)) { document.getElementById(!team ? 'ce-teams-title' : displayNameError(input.managerName) ? 'ce-manager' : 'ce-save')?.focus(); return; }
    onCreate(normalizeCareerSelection(input));
  }
  const previewContent = target ? <>
    <p className="ce-eyebrow">{target.compatibility?.managedTeamName ?? target.managedTeamCode}</p><h2>{target.saveName}</h2>
    <dl className="ce-save-facts"><div><dt>감독</dt><dd>{target.managerName}</dd></div><div><dt>게임 날짜</dt><dd>{target.currentDate}</dd></div><div><dt>최근 서버 저장</dt><dd>{savedAt(target.updatedAt)}</dd></div><div><dt>호환 상태</dt><dd>{target.compatibility?.status === 'UNSUPPORTED' ? '지원하지 않는 저장' : preview ? '불러오기 가능' : '확인 중'}</dd></div></dl>
    {target.compatibility?.status === 'UNSUPPORTED' ? <p role="alert">{target.compatibility.message}</p> : null}
    {previewLoading ? <p role="status">저장 상태 확인 중…</p> : null}{previewError ? <p role="alert">{previewError} 저장은 삭제하지 않았습니다.</p> : null}
    {preview?.compatibility?.status === 'UNSUPPORTED' ? <p role="alert">{preview.compatibility.message}</p> : null}
    <button className="lm-primary-button" disabled={!canLoad || pending} onClick={() => onLoad(target.careerId)}>{view === 'main' ? '최근 게임 이어하기' : '선택한 게임 불러오기'}</button>
  </> : <p>{loading ? '저장 목록 확인 중…' : view === 'main' ? '새 게임에서 첫 구단을 선택하세요.' : '목록에서 저장을 선택하면 여기에서 내용을 확인할 수 있습니다.'}</p>;
  return <div className={`ce-app ce-${view}`}>
    <header className="ce-top"><button className="ce-brand" onClick={() => onNavigate('main')} aria-label="게임 메인">LOL MANAGER<span>CAREER</span></button><nav aria-label="게임 메뉴">{view !== 'main' ? <button onClick={() => onNavigate('main')}>메인으로</button> : null}{view !== 'load' ? <button onClick={() => onNavigate('load')}>불러오기</button> : null}{view !== 'new' ? <button onClick={() => onNavigate('new')}>새 게임</button> : null}</nav></header>
    <main className="ce-content">
      <h1 ref={title} tabIndex={-1}>{view === 'main' ? <>LOL<br />MANAGER<span>구단 운영을 시작하세요.</span></> : view === 'new' ? '새 게임' : '저장 불러오기'}</h1>
      {error ? <div className="ce-error" role="alert">{error}<button onClick={onRefresh}>저장 목록 다시 확인</button></div> : null}
      {view === 'main' ? <div className="ce-main-actions"><section aria-label="최근 게임"><p className="ce-eyebrow">{!recent.career ? '첫 게임' : recent.previouslyEntered ? '최근 진입한 게임' : '서버에서 최근 갱신된 게임'}</p>{previewContent}</section><button className={!recent.career ? 'lm-primary-button ce-new-action' : 'ce-new-action'} onClick={() => onNavigate('new')}>새 게임 시작 <span aria-hidden="true">↗</span></button><button onClick={() => onNavigate('load')}>다른 저장 불러오기</button>{operation ? <p role="status">결과를 확인하지 못한 생성 요청이 있습니다. <button onClick={() => onNavigate('new')}>원래 생성 결과 확인</button></p> : null}</div> : null}
      {view === 'load' ? <><div className="ce-list-tools"><label>저장 검색<input type="search" value={query} onChange={e => setQuery(e.target.value)} placeholder="저장 이름 · 감독 · 구단" /></label><button onClick={onRefresh} disabled={loading}>목록 새로고침</button><p>{loading ? '저장 목록 확인 중…' : `저장 ${list.currentCount} / ${list.maximumCount}`}</p></div><div className="ce-load-layout"><section aria-label="저장 목록"><ul className="ce-save-list">{list.careers.filter(c => `${c.saveName} ${c.managerName} ${c.managedTeamCode} ${c.compatibility?.managedTeamName ?? ''}`.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase())).map(c => <li key={c.careerId}><button disabled={loading} aria-pressed={selectedId === c.careerId} onClick={() => setSelectedId(c.careerId)}><strong>{c.saveName}</strong><span>{c.compatibility?.managedTeamName ?? c.managedTeamCode} · {c.managerName} 감독</span><span>게임 날짜 {c.currentDate}</span><small>{c.compatibility?.status === 'UNSUPPORTED' ? '미지원 · 선택해 사유 확인' : '상세 확인 가능'} · 서버 저장 {savedAt(c.updatedAt)}</small></button></li>)}</ul>{!loading && !list.careers.length ? <p>저장된 게임이 없습니다. 새 게임을 시작하세요.</p> : null}{query && !list.careers.some(c => `${c.saveName} ${c.managerName} ${c.managedTeamCode} ${c.compatibility?.managedTeamName ?? ''}`.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase())) ? <p>검색 결과가 없습니다.</p> : null}</section><section className="ce-preview" aria-label="선택한 저장 미리보기">{previewContent}</section></div></> : null}
      {view === 'new' ? <form noValidate onSubmit={submit}>
        <p className="ce-intro">관리할 LCK 구단을 고르고 감독 이름과 저장 이름을 정하세요.</p>
        {operation ? <div className="ce-error" role="status"><strong>이전 생성 요청의 결과를 확인해야 합니다.</strong><p>원래 구단과 이름을 유지했습니다. 아래 버튼으로 같은 요청의 결과를 확인하세요.</p></div> : null}
        <div className="ce-team-layout"><section aria-labelledby="ce-teams-title"><h2 id="ce-teams-title" tabIndex={-1}>01 · 관리 구단</h2>{teamsLoading ? <p role="status">선택 가능한 구단 확인 중…</p> : null}{teamsError ? <p role="alert">{teamsError} <button type="button" onClick={onRetryTeams}>구단 다시 확인</button></p> : null}<div className="ce-team-list">{teams.map(t => <button type="button" key={t.teamCode} disabled={locked} aria-pressed={team?.teamCode === t.teamCode} onClick={() => setDraft(d => ({ ...d, managedTeamCode: t.teamCode }))}><strong>{t.teamCode}</strong><span>LCK · 참고 주전 {t.starterCount}명</span></button>)}</div>{teamError ? <p className="ce-field-error" role="alert">{teamError}</p> : null}</section>
        <section className="ce-lineup" aria-label="선택 구단 주전 미리보기"><div className="ce-lineup-title"><p className="ce-eyebrow">LCK</p><h2>{team?.teamCode ?? '관리할 구단을 선택하세요'}</h2></div>{team ? <><ul key={team.teamCode}>{team.lineup.map(p => <li key={p.playerId}><span>{p.position}</span><PlayerPortrait playerId={p.playerId} nickname={p.nickname} size="profile" /><strong>{p.nickname}</strong></li>)}</ul><p>현재 참고 자료의 주전 명단입니다. 후보·육성팀 전체 명부가 아니며 생성 후 운영 명부는 구단 홈에서 확인합니다.</p></> : <p>구단을 고르면 포지션별 주전 명단을 확인할 수 있습니다.</p>}</section></div>
        <section className="ce-manager" aria-labelledby="ce-manager-title"><h2 id="ce-manager-title">02 · 감독과 저장 이름</h2><div className="ce-inputs"><label htmlFor="ce-manager">감독 이름<input id="ce-manager" aria-label="감독 이름" value={input.managerName} disabled={locked} autoComplete="name" aria-invalid={!!nameError} aria-describedby={nameError ? 'ce-manager-error' : undefined} onChange={e => setDraft(d => ({ ...d, managerName: e.target.value }))} />{nameError ? <span id="ce-manager-error" className="ce-field-error">{nameError}</span> : null}</label><label htmlFor="ce-save">저장 이름<input id="ce-save" aria-label="저장 이름" value={input.saveName} disabled={locked} autoComplete="off" aria-invalid={!!saveError} aria-describedby={saveError ? 'ce-save-error' : undefined} onChange={e => setDraft(d => ({ ...d, saveName: e.target.value }))} />{saveError ? <span id="ce-save-error" className="ce-field-error">{saveError}</span> : null}</label></div><p>전역 선수 데이터 편집은 새 Career 생성에 적용됩니다. 이미 운영 중인 저장은 바뀌지 않습니다.</p></section>
        <footer className="ce-create-footer"><div><strong>{input.managedTeamCode || '구단 미선택'} · {input.managerName.normalize('NFC').trim() || '감독 이름 미입력'}</strong><p>{input.saveName.normalize('NFC').trim() || '저장 이름을 입력하세요.'}</p></div><button className="lm-primary-button" disabled={pending || !operation && (teamsLoading || !teams.length || loading || list.remainingCount === 0)} type="submit">{pending ? '생성 결과 확인 중…' : operation ? '원래 생성 결과 확인' : '이 구단으로 게임 시작'}</button></footer>
        {createError ? <p className="ce-error" role="alert">{createError}</p> : null}{!loading && list.remainingCount === 0 ? <p role="alert">사용할 수 있는 저장 슬롯이 없습니다. 기존 저장을 불러오세요.</p> : null}
      </form> : null}
    </main>
    {editorOpen ? <section className="ce-content ce-data-editor" aria-label="새 게임용 선수 데이터 편집"><h2>새 게임용 선수 데이터</h2><p>새 Career 생성에 적용할 기본 자료입니다. 이미 운영 중인 Career의 선수 상태는 바뀌지 않습니다.</p><button onClick={() => setEditorOpen(false)}>데이터 편집 닫기</button><PlayerDataEditor /></section> : null}
    <footer className="ce-bottom"><span>LOL MANAGER · 구단 운영</span>{onTool ? <details><summary>추가 모드 · 선수 데이터</summary><button onClick={() => onTool('match')}>단독 경기 · Series</button><button onClick={() => onTool('league')}>독립 AI 리그</button><button onClick={() => onTool('squad')}>전역 선수 데이터 조회</button><button onClick={() => setEditorOpen(v => !v)}>새 게임용 선수 데이터 편집</button></details> : null}</footer>
  </div>;
}
