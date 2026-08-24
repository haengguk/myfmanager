import { ChampionPortrait } from '../../../components/champion/ChampionPortrait';
import type { ChampionViewModel, TeamSide, TeamSnapshotViewModel } from '../realMatch.types';

export function LiveChampionPanel({ side, teamCode, snapshot, championsById }: { side: TeamSide; teamCode: string; snapshot: TeamSnapshotViewModel; championsById: Readonly<Record<string, ChampionViewModel>> }) {
  return (
    <aside className={`rm-live-side rm-side-${side.toLowerCase()}`} aria-label={`${teamCode} 챔피언 생존 상태`}>
      <header><strong>{teamCode} 챔피언</strong><span>{side}</span></header>
      {snapshot.champions.map((state) => {
        const champion = championsById[state.championId];
        return <ChampionLevelSlot key={state.playerId} side={side} champion={champion} state={state} />;
      })}
    </aside>
  );
}

function ChampionLevelSlot({ side, champion, state }: { side: TeamSide; champion: ChampionViewModel; state: TeamSnapshotViewModel['champions'][number] }) {
  return <article className={`rm-live-champion${state.alive ? '' : ' is-dead'}`} aria-label={`${side} ${state.position} ${state.playerName}, ${champion.name}, 레벨 ${state.level}, ${state.alive ? '생존' : `사망, ${state.respawnSeconds}초 후 부활`}`}><span className="rm-portrait"><ChampionPortrait name={champion.name} portraitUrl={champion.portraitUrl} /></span><span className="rm-live-copy"><strong>Lv. {state.level}</strong><span>{state.alive ? '생존' : '사망'}</span></span>{!state.alive ? <span className="rm-respawn">{state.respawnSeconds}초</span> : null}</article>;
}
