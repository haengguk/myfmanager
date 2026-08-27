import { ChampionPortrait } from '../../../components/champion/ChampionPortrait';
import type { PlayerDraftTurnEvidenceDto } from './api/playerDraftApi.types';
import type { PlayerDraftChampionCatalogEntry } from './playerDraft.types';

export function PlayerDraftHistory({ decisions, catalog, revealFrom }: {
  decisions: readonly PlayerDraftTurnEvidenceDto[];
  catalog: Readonly<Record<string, PlayerDraftChampionCatalogEntry>>;
  revealFrom: number;
}) {
  return (
    <section className="pd-history" aria-labelledby="pd-history-heading">
      <header><div><span>ORDERED CONTROL EVIDENCE</span><h2 id="pd-history-heading">20턴 결정 기록</h2></div><strong>{decisions.length} / 20</strong></header>
      <ol>
        {Array.from({ length: 20 }, (_, index) => {
          const decision = decisions[index];
          if (!decision) return <li className="is-empty" key={`empty-${index + 1}`}><span>{String(index + 1).padStart(2, '0')}</span><i>대기</i></li>;
          const champion = catalog[decision.championId]?.champion;
          return <li className={`${decision.authority === 'PLAYER' ? 'is-player' : 'is-ai'}${index >= revealFrom ? ' is-new' : ''}`} key={decision.turn}>
            <span>{String(decision.turn).padStart(2, '0')}</span>
            <span className="pd-history-portrait"><ChampionPortrait name={champion?.displayNameKo ?? decision.championId} portraitUrl={champion?.portraitUrl ?? ''} /></span>
            <strong>{champion?.displayNameKo ?? decision.championId}</strong>
            <small>{decision.teamSide} · {decision.actionType === 'BAN' ? '밴' : '픽'}</small>
            <em>{decision.authority === 'PLAYER' ? 'PLAYER' : 'AI'}</em>
          </li>;
        })}
      </ol>
    </section>
  );
}
