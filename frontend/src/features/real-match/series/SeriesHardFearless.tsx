import type { PlayerDraftChampionCatalogEntry } from '../player-draft/playerDraft.types';
import type { SeriesViewDto } from './api/seriesApi.types';

export function SeriesHardFearless({ series, catalog }: {
  series: SeriesViewDto;
  catalog: Readonly<Record<string, PlayerDraftChampionCatalogEntry>>;
}) {
  const completedGame = Math.max(0, ...series.games.filter(game => game.status === 'COMMITTED').map(game => game.gameNumber));
  return (
    <details className="sr-fearless">
      <summary>
        <span>HARD FEARLESS</span>
        <strong>{series.excludedChampionIds.length}</strong>
        <small>{completedGame > 0 ? `Game ${completedGame}까지 누적된 양 팀 픽` : '이전 게임 제외 없음'}</small>
      </summary>
      <div>
        {series.excludedChampionIds.length ? series.excludedChampionIds.map((championId) => {
          const entry = catalog[championId];
          return <figure key={championId} title={`${entry?.champion.displayNameKo ?? championId} · 이전 게임 픽`}>
            {entry?.champion.portraitUrl ? <img src={entry.champion.portraitUrl} alt="" /> : <span aria-hidden="true">?</span>}
            <figcaption>{entry?.champion.displayNameKo ?? championId}</figcaption>
          </figure>;
        }) : <p>Game 1은 누적 제외 챔피언 없이 시작합니다.</p>}
      </div>
    </details>
  );
}
