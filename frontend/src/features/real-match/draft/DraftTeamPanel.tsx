import { ChampionPortrait } from '../../../components/champion/ChampionPortrait';
import type { ChampionViewModel, DraftRosterSlotViewModel, TeamSide } from '../realMatch.types';

interface DraftTeamPanelProps {
  side: TeamSide;
  teamCode: string;
  roster: readonly DraftRosterSlotViewModel[];
  bans: readonly string[];
  currentPosition: string | null;
  championsById: Readonly<Record<string, ChampionViewModel>>;
}

export function DraftTeamPanel({ side, teamCode, roster, bans, currentPosition, championsById }: DraftTeamPanelProps) {
  return (
    <aside className={`rm-draft-side rm-side-${side.toLowerCase()}`} aria-label={`${teamCode} ${side === 'BLUE' ? '블루' : '레드'} 진영 선수와 픽`}>
      <header className="rm-draft-side-head"><strong>{teamCode} 선수·픽</strong><span>TOP → SUPPORT</span></header>
      <div className="rm-draft-roster">
        {roster.map((slot) => <PlayerPickSlot key={slot.playerId} slot={slot} champion={slot.championId ? championsById[slot.championId] : null} current={slot.position === currentPosition} />)}
      </div>
      <TeamBanList side={side} teamCode={teamCode} bans={bans} championsById={championsById} />
    </aside>
  );
}

function PlayerPickSlot({ slot, champion, current }: { slot: DraftRosterSlotViewModel; champion: ChampionViewModel | null; current: boolean }) {
  return <article className={`rm-player-slot${current ? ' is-current' : ''}`} aria-current={current ? 'step' : undefined}><div className={`rm-portrait rm-slot-portrait${champion ? '' : ' is-empty'}`}>{champion ? <ChampionPortrait name={champion.name} portraitUrl={champion.portraitUrl} /> : <span>미선택</span>}</div><div className="rm-slot-copy"><div className="rm-slot-meta"><span className="rm-slot-role">{slot.position}</span><span className={`rm-state-badge${champion ? ' is-confirmed' : current ? ' is-current' : ''}`}>{champion ? '선택 확정' : current ? '선택 중' : '선택 대기'}</span></div><h3>{slot.playerName}</h3><span className="rm-slot-champion">{champion?.name ?? '챔피언 선택 대기'}</span></div></article>;
}

function TeamBanList({ side, teamCode, bans, championsById }: Pick<DraftTeamPanelProps, 'side' | 'teamCode' | 'bans' | 'championsById'>) {
  return <section className="rm-bans" aria-label={`${teamCode} 밴`}><header><strong>{side} 밴</strong><span>{bans.length} / 5</span></header><div className="rm-ban-list">{Array.from({ length: 5 }, (_, index) => { const champion = bans[index] ? championsById[bans[index]] : null; return <div className="rm-ban-slot" key={`${side}-ban-${index}`} title={champion ? `${index + 1}번째 밴: ${champion.name}` : `${index + 1}번째 밴 빈 슬롯`}>{champion ? <><span className="rm-ban-order">{index + 1}</span><ChampionPortrait name={champion.name} portraitUrl={champion.portraitUrl} /></> : index + 1}</div>; })}</div></section>;
}
