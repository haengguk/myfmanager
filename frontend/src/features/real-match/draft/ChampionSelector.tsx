import { ChampionPortrait } from '../../../components/champion/ChampionPortrait';
import type { ChampionViewModel, DraftTurnViewModel, Position } from '../realMatch.types';

type RoleFilter = 'ALL' | Position;

interface ChampionSelectorProps {
  champions: readonly ChampionViewModel[];
  selectedId: string | null;
  searchValue: string;
  roleFilter: RoleFilter;
  disabledReason: (champion: ChampionViewModel) => string | null;
  decisionCount: number;
  currentTurn: DraftTurnViewModel | null;
  complete: boolean;
  onSearchChange: (value: string) => void;
  onRoleChange: (role: RoleFilter) => void;
  onSelect: (championId: string) => void;
  onCancel: () => void;
  onConfirm: () => void;
  onUndo: () => void;
  onContinue: () => void;
}

const filters: readonly { value: RoleFilter; label: string }[] = [
  { value: 'ALL', label: '전체' }, { value: 'TOP', label: 'TOP' }, { value: 'JUNGLE', label: 'JUNGLE' },
  { value: 'MID', label: 'MID' }, { value: 'ADC', label: 'ADC' }, { value: 'SUPPORT', label: 'SUPPORT' },
];

const koreanNameCollator = new Intl.Collator('ko-KR', {
  usage: 'sort',
  sensitivity: 'base',
  numeric: true,
});

export function ChampionSelector(props: ChampionSelectorProps) {
  const selected = props.champions.find((champion) => champion.id === props.selectedId) ?? null;
  const term = props.searchValue.trim().toLocaleLowerCase();
  const visible = props.champions.filter((champion) =>
    (props.roleFilter === 'ALL' || champion.position === props.roleFilter)
    && (!term || champion.name.toLocaleLowerCase().includes(term)),
  ).sort((left, right) => koreanNameCollator.compare(left.name, right.name) || left.id.localeCompare(right.id));

  return (
    <section className="rm-draft-board" aria-label="챔피언 선택">
      <div className="rm-board-top">
        <ChampionSearch value={props.searchValue} onChange={props.onSearchChange} />
        <span className="rm-inline-status"><i />{selected ? '선택 중' : props.currentTurn?.phase === 'BAN' ? '밴 선택 가능' : '픽 선택 가능'}</span>
        <span className="rm-decision-count">전체 {props.champions.length}명 · 결정 {props.decisionCount} / 20</span>
      </div>
      <PositionFilter value={props.roleFilter} onChange={props.onRoleChange} />
      <div className="rm-grid-wrap">
        <div className="rm-champion-grid rm-scroll-area">
          {visible.length ? visible.map((champion) => {
            const reason = props.disabledReason(champion);
            return (
              <button type="button" className={`rm-champion-card${props.selectedId === champion.id ? ' is-selected' : ''}`} key={champion.id} disabled={Boolean(reason)} title={reason ? `${champion.name}: ${reason}` : `${champion.name} 선택`} onClick={() => props.onSelect(champion.id)}>
                <span className="rm-portrait"><ChampionPortrait name={champion.name} portraitUrl={champion.portraitUrl} /></span>
                <span className="rm-champion-name">{champion.name}</span>
                {reason ? <span className="rm-disabled-reason">{reason}</span> : null}
              </button>
            );
          }) : <div className="rm-no-results">검색 결과가 없습니다.<br />검색어나 포지션 필터를 바꿔 보세요.</div>}
        </div>
        {props.complete ? (
          <div className="rm-board-overlay">
            <div><span className="rm-state-badge is-confirmed">완료</span><h2>전체 밴픽이 완료되었습니다</h2><p>최종 배정 10명과 밴 10개를 경기 데이터로 넘길 준비가 끝났습니다.</p><button className="rm-primary-action" type="button" onClick={props.onContinue}>경기 재생으로 이동</button></div>
          </div>
        ) : null}
      </div>
      <DraftActionBar selected={selected} currentTurn={props.currentTurn} onUndo={props.onUndo} onCancel={props.onCancel} onConfirm={props.onConfirm} />
    </section>
  );
}

function ChampionSearch({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return <label className="rm-champion-search"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6" /><path d="m15 15 5 5" /></svg><span className="lm-sr-only">챔피언 검색</span><input type="search" value={value} onChange={(event) => onChange(event.target.value)} placeholder="챔피언 검색…" autoComplete="off" /></label>;
}

function PositionFilter({ value, onChange }: { value: RoleFilter; onChange: (role: RoleFilter) => void }) {
  return <nav className="rm-role-filters" aria-label="포지션 필터">{filters.map((filter) => <button type="button" key={filter.value} className={value === filter.value ? 'is-active' : ''} aria-pressed={value === filter.value} onClick={() => onChange(filter.value)}>{filter.label}</button>)}</nav>;
}

function DraftActionBar({ selected, currentTurn, onUndo, onCancel, onConfirm }: { selected: ChampionViewModel | null; currentTurn: DraftTurnViewModel | null; onUndo: () => void; onCancel: () => void; onConfirm: () => void }) {
  const isBan = currentTurn?.phase === 'BAN';
  const selectionCopy = selected
    ? isBan ? `${selected.position} · ${currentTurn?.side} 밴 목록에 등록` : `${selected.position} · ${currentTurn?.side} ${currentTurn?.position} 슬롯에 배정`
    : isBan ? '이번 차례에 밴할 챔피언을 고르세요.' : '현재 선수 슬롯에 배정할 챔피언을 고르세요.';
  return <div className="rm-selection-bar"><div className={`rm-portrait rm-preview-portrait${selected ? '' : ' is-empty'}`}>{selected ? <ChampionPortrait name={selected.name} portraitUrl={selected.portraitUrl} /> : <span>미선택</span>}</div><div className="rm-preview-copy"><strong>{selected?.name ?? (isBan ? '밴할 챔피언을 선택하세요' : '챔피언을 선택하세요')}</strong><span>{selectionCopy}</span></div><div className="rm-selection-actions"><button className="rm-text-action" type="button" onClick={onUndo}>이전 단계</button><button className="rm-secondary-action" type="button" disabled={!selected} onClick={onCancel}>선택 취소</button><button className="rm-primary-action" type="button" onClick={onConfirm}>{isBan ? '밴 확정' : '픽 확정'}</button></div></div>;
}
