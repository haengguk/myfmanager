import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { ChampionPortrait } from '../../../components/champion/ChampionPortrait';
import type { Position } from '../realMatch.contract';
import type { PlayerDraftCurrentTurnDto, PlayerDraftRecommendationDto } from './api/playerDraftApi.types';
import { playerDraftUnavailableReasonLabels, type PlayerDraftChampionCatalogEntry } from './playerDraft.types';

type RoleFilter = 'ALL' | Position;
const ROLE_FILTERS: readonly RoleFilter[] = ['ALL', 'TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];
const ROLE_LABELS: Readonly<Record<RoleFilter, string>> = { ALL: '전체', TOP: 'TOP', JUNGLE: 'JUNGLE', MID: 'MID', ADC: 'ADC', SUPPORT: 'SUPPORT' };

export function PlayerDraftChampionWorkspace({ catalog, recommendations, currentTurn, selectedChampionId, busy, onSelect, onConfirm }: {
  catalog: Readonly<Record<string, PlayerDraftChampionCatalogEntry>>;
  recommendations: readonly PlayerDraftRecommendationDto[];
  currentTurn: PlayerDraftCurrentTurnDto;
  selectedChampionId: string | null;
  busy: boolean;
  onSelect: (championId: string) => void;
  onConfirm: () => void;
}) {
  const [query, setQuery] = useState(''); const [role, setRole] = useState<RoleFilter>('ALL'); const [rovingId, setRovingId] = useState<string | null>(null);
  const buttonRefs = useRef(new Map<string, HTMLButtonElement>());
  const entries = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase('ko-KR');
    return Object.values(catalog).filter((entry) => {
      const matchesText = !normalized || [entry.champion.displayNameKo, entry.champion.displayNameEn, entry.champion.championId].some((value) => value.toLocaleLowerCase('ko-KR').includes(normalized));
      const matchesRole = role === 'ALL' || entry.feasibleRoles.includes(role);
      return matchesText && matchesRole;
    }).sort((left, right) => left.champion.displayNameKo.localeCompare(right.champion.displayNameKo, 'ko-KR', { sensitivity: 'base' }));
  }, [catalog, query, role]);
  useEffect(() => {
    if (selectedChampionId && entries.some((entry) => entry.champion.championId === selectedChampionId)) setRovingId(selectedChampionId);
    else if (!rovingId || !entries.some((entry) => entry.champion.championId === rovingId)) setRovingId(entries[0]?.champion.championId ?? null);
  }, [entries, rovingId, selectedChampionId]);
  const selected = selectedChampionId ? catalog[selectedChampionId] : null;
  const focusAt = (index: number) => {
    const next = entries[Math.max(0, Math.min(entries.length - 1, index))];
    if (!next) return; const id = next.champion.championId; setRovingId(id); buttonRefs.current.get(id)?.focus();
  };
  const gridKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    const columns = window.innerWidth < 1340 ? 6 : 7; let next = index;
    if (event.key === 'ArrowRight') next = index + 1; else if (event.key === 'ArrowLeft') next = index - 1;
    else if (event.key === 'ArrowDown') next = index + columns; else if (event.key === 'ArrowUp') next = index - columns;
    else if (event.key === 'Home') next = 0; else if (event.key === 'End') next = entries.length - 1; else return;
    event.preventDefault(); focusAt(next);
  };
  const actionLabel = currentTurn.actionType === 'BAN' ? '밴' : '픽';

  return (
    <section className="pd-champion-workspace" aria-labelledby="pd-pool-heading">
      <header className="pd-turn-focus" key={currentTurn.turn} aria-live="polite">
        <div><span>TURN {String(currentTurn.turn).padStart(2, '0')} · {currentTurn.teamSide}</span><h1>{actionLabel}할 챔피언을 선택하세요</h1></div>
        <strong>{currentTurn.turn} <small>/ 20</small></strong>
      </header>
      <section className="pd-recommendations" aria-labelledby="pd-recommend-heading">
        <div><h2 id="pd-recommend-heading">종합 추천</h2><span>참고용 · 추천 밖의 legal champion도 선택 가능</span></div>
        <div>
          {recommendations.map((recommendation) => {
            const id = recommendation.champion.championId; const active = selectedChampionId === id;
            return <button type="button" key={id} aria-pressed={active} className={active ? 'is-selected' : ''} disabled={busy} onClick={() => onSelect(id)}>
              <span>추천 {recommendation.advisoryRank}</span>
              <span className="pd-rec-portrait"><ChampionPortrait name={recommendation.champion.displayNameKo} portraitUrl={recommendation.champion.portraitUrl} /></span>
              <strong>{recommendation.champion.displayNameKo}</strong>
              <small title={`즉시 ${recommendation.immediateScore.toFixed(2)} · 이후 ${recommendation.continuationScore.toFixed(2)} · 최종 ${recommendation.finalSearchScore.toFixed(2)}`}>점수 상세</small>
            </button>;
          })}
        </div>
      </section>
      <div className="pd-pool-toolbar">
        <label><span>챔피언 검색</span><input type="search" value={query} placeholder="한글 · 영문 · champion ID" onChange={(event) => setQuery(event.target.value)} /></label>
        <div className="pd-role-filters" role="group" aria-label="포지션 필터">
          {ROLE_FILTERS.map((filter) => <button type="button" key={filter} aria-pressed={role === filter} className={role === filter ? 'is-selected' : ''} onClick={() => setRole(filter)}>{ROLE_LABELS[filter]}</button>)}
        </div>
        <span>{entries.length}개 표시</span>
      </div>
      <h2 id="pd-pool-heading" className="lm-sr-only">선택 가능한 전체 챔피언 풀</h2>
      <div className="pd-champion-grid rm-scroll-area" aria-label="챔피언 선택 그리드" aria-busy={busy}>
        {entries.map((entry, index) => {
          const id = entry.champion.championId; const unavailable = entry.unavailableReason !== null; const active = id === selectedChampionId;
          const reason = entry.unavailableReason ? playerDraftUnavailableReasonLabels[entry.unavailableReason] : null;
          return <button type="button" key={id} ref={(node) => { if (node) buttonRefs.current.set(id, node); else buttonRefs.current.delete(id); }}
            tabIndex={rovingId === id ? 0 : -1} aria-pressed={active} aria-disabled={busy || unavailable}
            aria-describedby={reason ? `pd-reason-${id}` : undefined}
            className={`${active ? 'is-selected ' : ''}${unavailable ? 'is-unavailable' : 'is-selectable'}`}
            title={reason ?? `${entry.champion.displayNameKo} ${actionLabel} 선택`}
            onKeyDown={(event) => gridKeyDown(event, index)} onClick={() => { if (!busy && !unavailable) onSelect(id); }}>
            <span className="pd-champion-portrait"><ChampionPortrait name={entry.champion.displayNameKo} portraitUrl={entry.champion.portraitUrl} /></span>
            <strong>{entry.champion.displayNameKo}</strong>
            <small>{unavailable ? '선택 불가' : currentTurn.actionType === 'BAN' ? '밴 가능' : entry.feasibleRoles.join(' · ')}</small>
            {reason ? <span className="pd-unavailable-reason" id={`pd-reason-${id}`}>{reason}</span> : null}
          </button>;
        })}
        {!entries.length ? <div className="pd-empty-pool">검색 조건에 맞는 챔피언이 없습니다.</div> : null}
      </div>
      <footer className={`pd-confirm-bar${selected ? ' has-selection' : ''}`}>
        {selected ? <><span className="pd-confirm-portrait"><ChampionPortrait name={selected.champion.displayNameKo} portraitUrl={selected.champion.portraitUrl} /></span><div><small>현재 선택 · TURN {currentTurn.turn}</small><strong>{selected.champion.displayNameKo} {actionLabel}</strong><span>{selected.champion.displayNameEn} · {currentTurn.actionType === 'BAN' ? '밴 가능' : selected.feasibleRoles.join(' / ')}</span></div></> : <div><small>선택 대기</small><strong>챔피언을 먼저 선택하세요</strong><span>타일 선택만으로는 서버에 반영되지 않습니다.</span></div>}
        <button className="rm-primary-action" type="button" disabled={!selected || busy} onClick={onConfirm}>{busy ? '상대 AI 계산 중…' : selected ? `${selected.champion.displayNameKo} ${actionLabel} 확정` : `${actionLabel} 확정`}</button>
      </footer>
    </section>
  );
}
