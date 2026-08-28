import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { ChampionPortrait } from '../../../components/champion/ChampionPortrait';
import type { Position } from '../realMatch.contract';
import type { PlayerDraftCurrentTurnDto } from './api/playerDraftApi.types';
import {
  playerDraftUnavailableReasonBadges, playerDraftUnavailableReasonLabels,
  playerDraftEntryMatchesRole, type PlayerDraftChampionCatalogEntry,
} from './playerDraft.types';

type RoleFilter = 'ALL' | Position;
const ROLE_FILTERS: readonly RoleFilter[] = ['ALL', 'TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];
const ROLE_LABELS: Readonly<Record<RoleFilter, string>> = { ALL: '전체', TOP: 'TOP', JUNGLE: 'JUNGLE', MID: 'MID', ADC: 'ADC', SUPPORT: 'SUPPORT' };

export function PlayerDraftChampionWorkspace({ catalog, currentTurn, decisionCount, selectedChampionId, busy, error, onSelect, onConfirm, onRefresh }: {
  catalog: Readonly<Record<string, PlayerDraftChampionCatalogEntry>>;
  currentTurn: PlayerDraftCurrentTurnDto;
  decisionCount: number;
  selectedChampionId: string | null;
  busy: boolean;
  error: string | null;
  onSelect: (championId: string | null) => void;
  onConfirm: () => void;
  onRefresh: () => void;
}) {
  const [query, setQuery] = useState(''); const [role, setRole] = useState<RoleFilter>('ALL'); const [rovingId, setRovingId] = useState<string | null>(null);
  const buttonRefs = useRef(new Map<string, HTMLButtonElement>());
  const previousTurnRef = useRef(currentTurn.turn);
  const entries = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase('ko-KR');
    return Object.values(catalog).filter((entry) => {
      const matchesText = !normalized || [entry.champion.displayNameKo, entry.champion.displayNameEn, entry.champion.championId].some((value) => value.toLocaleLowerCase('ko-KR').includes(normalized));
      return matchesText && playerDraftEntryMatchesRole(entry, role);
    }).sort((left, right) => left.champion.displayNameKo.localeCompare(right.champion.displayNameKo, 'ko-KR', { sensitivity: 'base' }));
  }, [catalog, query, role]);
  useEffect(() => {
    if (selectedChampionId && entries.some((entry) => entry.champion.championId === selectedChampionId)) setRovingId(selectedChampionId);
    else if (!rovingId || !entries.some((entry) => entry.champion.championId === rovingId)) setRovingId(entries[0]?.champion.championId ?? null);
  }, [entries, rovingId, selectedChampionId]);
  useEffect(() => {
    if (previousTurnRef.current === currentTurn.turn) return;
    previousTurnRef.current = currentTurn.turn;
    const next = entries.find((entry) => entry.unavailableReason === null) ?? entries[0];
    if (!next) return;
    const id = next.champion.championId; setRovingId(id);
    const frame = window.requestAnimationFrame(() => buttonRefs.current.get(id)?.focus());
    return () => window.cancelAnimationFrame(frame);
  }, [currentTurn.turn, entries]);
  const selected = selectedChampionId ? catalog[selectedChampionId] : null;
  const focusAt = (index: number) => {
    const next = entries[Math.max(0, Math.min(entries.length - 1, index))];
    if (!next) return; const id = next.champion.championId; setRovingId(id); buttonRefs.current.get(id)?.focus();
  };
  const gridKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    const rendered = entries.map((entry) => buttonRefs.current.get(entry.champion.championId)).filter((button): button is HTMLButtonElement => Boolean(button));
    const firstTop = rendered[0]?.offsetTop; const columns = Math.max(1, rendered.filter((button) => button.offsetTop === firstTop).length); let next = index;
    if (event.key === 'ArrowRight') next = index + 1; else if (event.key === 'ArrowLeft') next = index - 1;
    else if (event.key === 'ArrowDown') next = index + columns; else if (event.key === 'ArrowUp') next = index - columns;
    else if (event.key === 'Home') next = 0; else if (event.key === 'End') next = entries.length - 1; else return;
    event.preventDefault(); focusAt(next);
  };
  const actionLabel = currentTurn.actionType === 'BAN' ? '밴' : '픽';
  const inlineStatus = error ? '상태 확인 필요' : busy ? '상대 AI 처리 중' : selected ? '선택 중' : '선택 가능';

  return (
    <section className="rm-draft-board pd-player-draft-board" aria-labelledby="pd-pool-heading">
      <div className="rm-board-top">
        <label className="rm-champion-search">
          <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6" /><path d="m16 16 4 4" /></svg>
          <span className="lm-sr-only">챔피언 검색</span>
          <input type="search" value={query} placeholder="챔피언 검색…" autoComplete="off" onChange={(event) => setQuery(event.target.value)} />
        </label>
        <span className={`rm-inline-status${error ? ' is-error' : ''}`} role="status"><i /> <span>{inlineStatus}</span></span>
        <span className="rm-decision-count" role="status" aria-live="polite">결정 {decisionCount} / 20</span>
      </div>
      <nav className="rm-role-filters" aria-label="포지션 필터">
        {ROLE_FILTERS.map((filter) => <button type="button" key={filter} aria-pressed={role === filter} className={role === filter ? 'is-active' : ''} onClick={() => setRole(filter)}>{ROLE_LABELS[filter]}</button>)}
      </nav>
      <div className="rm-grid-wrap">
        <h2 id="pd-pool-heading" className="lm-sr-only">전체 챔피언 풀</h2>
        <div className="rm-champion-grid rm-scroll-area" aria-label={`${ROLE_LABELS[role]} 챔피언 목록 ${entries.length}개. 방향키와 Home, End 키로 이동합니다.`} aria-busy={busy}>
          {entries.map((entry, index) => {
            const id = entry.champion.championId; const unavailable = entry.unavailableReason !== null; const active = id === selectedChampionId;
            const reason = entry.unavailableReason ? playerDraftUnavailableReasonLabels[entry.unavailableReason] : null;
            const badge = entry.unavailableReason ? playerDraftUnavailableReasonBadges[entry.unavailableReason] : null;
            return (
              <button type="button" key={id} ref={(node) => { if (node) buttonRefs.current.set(id, node); else buttonRefs.current.delete(id); }}
                tabIndex={rovingId === id ? 0 : -1} aria-pressed={active} aria-disabled={busy || unavailable}
                aria-label={`${entry.champion.displayNameKo}${reason ? `, ${reason}` : `, ${actionLabel} 가능`}`}
                className={`rm-champion-card${active ? ' is-selected' : ''}${unavailable ? ' is-unavailable' : ''}`}
                title={reason ?? `${entry.champion.displayNameKo} ${actionLabel} 선택`}
                onFocus={() => setRovingId(id)} onKeyDown={(event) => gridKeyDown(event, index)} onClick={() => { if (!busy && !unavailable) onSelect(id); }}>
                <span className="rm-portrait"><ChampionPortrait name={entry.champion.displayNameKo} portraitUrl={entry.champion.portraitUrl} /></span>
                <span className="rm-champion-meta">
                  <span className="rm-champion-name">{entry.champion.displayNameKo}</span>
                  {badge ? <span className="rm-disabled-reason">{badge}</span> : null}
                </span>
              </button>
            );
          })}
          {!entries.length ? <div className="rm-no-results">검색 결과가 없습니다.<br />검색어나 포지션 필터를 바꿔 보세요.</div> : null}
        </div>
        {busy || error ? <div className="rm-board-overlay"><div>
          {busy ? <><span className="rm-spinner" aria-hidden="true" /><h2>상대 AI 선택 반영 중</h2><p>다음 내 차례까지 서버의 Draft 상태를 계산하고 있습니다.</p></> : <><span className="rm-state-badge">API 확인 필요</span><h2>밴픽 상태를 갱신하지 못했습니다</h2><p>{error}</p><button className="rm-secondary-action" type="button" onClick={onRefresh}>최신 상태 다시 확인</button></>}
        </div></div> : null}
      </div>
      <footer className="rm-selection-bar">
        <div className={`rm-portrait rm-preview-portrait${selected ? '' : ' is-empty'}`}>
          {selected ? <ChampionPortrait name={selected.champion.displayNameKo} portraitUrl={selected.champion.portraitUrl} /> : <span>미선택</span>}
        </div>
        <div className="rm-preview-copy" role="status" aria-live="polite" aria-atomic="true">
          <strong>{selected ? selected.champion.displayNameKo : '챔피언을 선택하세요'}</strong>
          <span>{selected ? `${selected.roles.join(' · ')} · ${actionLabel} 후보` : `TURN ${currentTurn.turn} · 현재 차례에 ${actionLabel}할 챔피언을 고르세요.`}</span>
        </div>
        <div className="rm-selection-actions">
          <button className="rm-secondary-action" type="button" disabled={!selected || busy} onClick={() => onSelect(null)}>선택 취소</button>
          <button className="rm-primary-action" type="button" disabled={!selected || busy} onClick={onConfirm}>{busy ? '처리 중…' : selected ? `${selected.champion.displayNameKo} ${actionLabel} 확정` : `${actionLabel} 확정`}</button>
        </div>
      </footer>
    </section>
  );
}
