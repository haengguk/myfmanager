import { useEffect, useRef } from 'react';
import type { AbilityRatingKey } from '../reference/realMatchReference.contract';
import type { FinalPlayerViewModel } from '../matchSession.types';
import type { ChampionViewModel } from '../realMatch.types';

const RATING_LABELS: Readonly<Record<AbilityRatingKey, string>> = {
  COMBAT_EXECUTION: '전투 수행', CONSISTENCY: '일관성', DECISION_MAKING: '판단', FARMING: '파밍',
  LANE_PRESSURE: '라인 압박', MAP_AWARENESS: '맵 인지', MECHANICS: '메카닉', POSITIONING: '포지셔닝',
  PRIORITY_CONVERSION: '우선권 전환', SIDE_LANE: '사이드 운영', TRADING: '딜 교환', WAVE_MANAGEMENT: '웨이브 관리',
};

export function AbilityProfileModal({ player, champion, onClose }: { player: FinalPlayerViewModel | null; champion?: ChampionViewModel; onClose: () => void }) {
  const closeRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (!player) return;
    closeRef.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose(); };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [onClose, player]);
  if (!player) return null;
  const keys = Object.keys(RATING_LABELS) as AbilityRatingKey[];
  return (
    <div className="rm-modal-layer" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <div className="rm-dialog rm-ability-dialog" role="dialog" aria-modal="true" aria-labelledby="rm-ability-title">
        <p className="rm-dialog-kicker">PLAYER_ABILITY_PROFILE_V1</p>
        <h2 id="rm-ability-title">{player.playerName} · {champion?.name ?? player.championId}</h2>
        <p>{player.position} · proficiency {player.abilityProfile.selectedChampionProficiency.toFixed(1)} · 실행 보정 {player.abilityProfile.proficiencyExecutionAdjustment.toFixed(2)}</p>
        <div className="rm-ability-grid" role="table" aria-label={`${player.playerName} 실제 능력치`}>
          <div role="row"><strong role="columnheader">항목</strong><strong role="columnheader">기본</strong><strong role="columnheader">실현</strong><strong role="columnheader">변화</strong></div>
          {keys.map((key) => <div role="row" key={key}><span role="cell">{RATING_LABELS[key]}</span><span role="cell">{player.abilityProfile.baseRatings[key].toFixed(1)}</span><span role="cell">{player.abilityProfile.realizedRatings[key].toFixed(2)}</span><span role="cell" className={player.abilityProfile.realizationDeltas[key] >= 0 ? 'is-positive' : 'is-negative'}>{player.abilityProfile.realizationDeltas[key] >= 0 ? '+' : ''}{player.abilityProfile.realizationDeltas[key].toFixed(2)}</span></div>)}
        </div>
        <div className="rm-dialog-actions"><button className="rm-primary-action" ref={closeRef} type="button" onClick={onClose}>확인</button></div>
      </div>
    </div>
  );
}
