import { useEffect, useMemo, useRef, useState } from 'react';
import { MatchToast, MatchUtilityBar } from '../MatchChrome';
import type { ChampionViewModel, DraftResultViewModel, DraftRosterSlotViewModel, DraftTurnViewModel, DraftViewModel, Position, TeamSide } from '../realMatch.types';
import { ChampionSelector } from './ChampionSelector';
import { DraftHeader } from './DraftHeader';
import { DraftTeamPanel } from './DraftTeamPanel';

type RoleFilter = 'ALL' | Position;
type Decision = { turn: DraftTurnViewModel; championId: string };

function withObjectParticle(value: string): string {
  const lastCodePoint = value.codePointAt(value.length - 1) ?? 0;
  const hasFinalConsonant = lastCodePoint >= 0xac00 && lastCodePoint <= 0xd7a3 && (lastCodePoint - 0xac00) % 28 !== 0;
  return `${value}${hasFinalConsonant ? '을' : '를'}`;
}

export function DraftRoomPage({ viewModel, onBack, onComplete }: { viewModel: DraftViewModel; onBack: () => void; onComplete: (result: DraftResultViewModel) => void }) {
  const [rosters, setRosters] = useState<Record<TeamSide, DraftRosterSlotViewModel[]>>(() => ({ BLUE: viewModel.rosters.BLUE.map((slot) => ({ ...slot })), RED: viewModel.rosters.RED.map((slot) => ({ ...slot })) }));
  const [bans, setBans] = useState<Record<TeamSide, string[]>>(() => ({ BLUE: [...viewModel.bans.BLUE], RED: [...viewModel.bans.RED] }));
  const [turnIndex, setTurnIndex] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [searchValue, setSearchValue] = useState('');
  const [roleFilter, setRoleFilter] = useState<RoleFilter>('ALL');
  const [seconds, setSeconds] = useState(viewModel.initialSeconds);
  const [history, setHistory] = useState<Decision[]>([]);
  const [toast, setToast] = useState({ title: '', message: '', visible: false });
  const toastTimer = useRef<number | null>(null);
  const complete = turnIndex >= viewModel.turnQueue.length;
  const currentTurn = complete ? null : viewModel.turnQueue[turnIndex];
  const championsById = useMemo(() => Object.fromEntries(viewModel.champions.map((champion) => [champion.id, champion])), [viewModel.champions]);
  const selectedIds = useMemo(() => new Set([...rosters.BLUE, ...rosters.RED].map((slot) => slot.championId).filter(Boolean)), [rosters]);

  const showToast = (title: string, message: string) => {
    if (toastTimer.current !== null) window.clearTimeout(toastTimer.current);
    setToast({ title, message, visible: true });
    toastTimer.current = window.setTimeout(() => setToast((current) => ({ ...current, visible: false })), 2600);
  };

  useEffect(() => {
    if (complete || seconds <= 0) return;
    const timer = window.setInterval(() => setSeconds((current) => Math.max(0, current - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [complete, seconds]);

  useEffect(() => () => { if (toastTimer.current !== null) window.clearTimeout(toastTimer.current); }, []);

  const disabledReason = (champion: ChampionViewModel): string | null => {
    if ([...bans.BLUE, ...bans.RED].includes(champion.id)) return '밴됨';
    if (selectedIds.has(champion.id)) return '이미 선택됨';
    if (viewModel.fearlessChampionIds.includes(champion.id)) return '피어리스 제외';
    return null;
  };

  const confirmSelection = () => {
    if (!selectedId || !currentTurn) {
      showToast('선택할 수 없음', complete ? '모든 밴픽이 완료되었습니다.' : '확정할 챔피언을 먼저 선택하세요.');
      return;
    }
    if (currentTurn.phase === 'BAN') {
      setBans((current) => ({ ...current, [currentTurn.side]: [...current[currentTurn.side], selectedId] }));
    } else if (currentTurn.position) {
      setRosters((current) => ({ ...current, [currentTurn.side]: current[currentTurn.side].map((slot) => slot.position === currentTurn.position ? { ...slot, championId: selectedId } : slot) }));
    }
    setHistory((current) => [...current, { turn: currentTurn, championId: selectedId }]);
    const selectedChampionName = withObjectParticle(championsById[selectedId].name);
    showToast(currentTurn.phase === 'BAN' ? '밴 확정' : '픽 확정', `${currentTurn.side} 진영이 ${selectedChampionName}${currentTurn.phase === 'BAN' ? ' 밴했습니다.' : ' 선택했습니다.'}`);
    setSelectedId(null);
    setTurnIndex((current) => current + 1);
    setSeconds(30);
  };

  const undo = () => {
    const decision = history[history.length - 1];
    if (!decision) { showToast('이전 단계 없음', '되돌릴 선택이 없습니다.'); return; }
    if (decision.turn.phase === 'BAN') {
      setBans((current) => ({ ...current, [decision.turn.side]: current[decision.turn.side].filter((id) => id !== decision.championId) }));
    } else if (decision.turn.position) {
      setRosters((current) => ({ ...current, [decision.turn.side]: current[decision.turn.side].map((slot) => slot.position === decision.turn.position ? { ...slot, championId: null } : slot) }));
    }
    setHistory((current) => current.slice(0, -1));
    setTurnIndex((current) => Math.max(0, current - 1));
    setSelectedId(decision.championId);
    setSeconds(30);
    showToast('이전 단계 복원', `${championsById[decision.championId].name} 결정을 되돌렸습니다.`);
  };

  return (
    <div className="rm-draft-app">
      <MatchUtilityBar meta={viewModel.seasonLabel} onBack={onBack} />
      <DraftHeader viewModel={viewModel} championsById={championsById} currentTurn={currentTurn} seconds={seconds} complete={complete} />
      <main className="rm-draft-stage">
        <DraftTeamPanel side="BLUE" teamCode={viewModel.teams.BLUE.code} roster={rosters.BLUE} bans={bans.BLUE} currentPosition={currentTurn?.phase === 'PICK' && currentTurn.side === 'BLUE' ? currentTurn.position : null} championsById={championsById} />
        <ChampionSelector champions={viewModel.champions} selectedId={selectedId} searchValue={searchValue} roleFilter={roleFilter} disabledReason={disabledReason} decisionCount={turnIndex} currentTurn={currentTurn} complete={complete} onSearchChange={setSearchValue} onRoleChange={setRoleFilter} onSelect={setSelectedId} onCancel={() => setSelectedId(null)} onConfirm={confirmSelection} onUndo={undo} onContinue={() => onComplete({ rosters, bans })} />
        <DraftTeamPanel side="RED" teamCode={viewModel.teams.RED.code} roster={rosters.RED} bans={bans.RED} currentPosition={currentTurn?.phase === 'PICK' && currentTurn.side === 'RED' ? currentTurn.position : null} championsById={championsById} />
      </main>
      <MatchToast {...toast} />
    </div>
  );
}
