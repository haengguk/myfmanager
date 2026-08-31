import type { PlayerDraftChampionCatalogEntry } from '../player-draft/playerDraft.types';
import type { SeriesViewDto } from './api/seriesApi.types';
import { SeriesGameRail } from './SeriesGameRail';
import { SeriesHardFearless } from './SeriesHardFearless';
import { SeriesScoreboard } from './SeriesScoreboard';

const STATUS_LABELS: Readonly<Record<SeriesViewDto['status'], string>> = {
  ACTIVE: '진행 중', BLOCKED: '진행 차단', COMPLETED: '시리즈 완료', CANCELLED: '시리즈 취소', EXPIRED: '시리즈 만료',
};

export function SeriesContextBar({ series, catalog, onOpenGame }: {
  series: SeriesViewDto;
  catalog: Readonly<Record<string, PlayerDraftChampionCatalogEntry>>;
  onOpenGame?: (gameNumber: number) => void;
}) {
  const game = series.games.find((candidate) => candidate.gameNumber === series.currentGameNumber) ?? series.games[series.games.length - 1];
  return (
    <header className="sr-context-bar">
      <div className="sr-context-heading">
        <div><span>SERIES DESK</span><h1>{series.format} · Game {series.currentGameNumber}</h1></div>
        <dl>
          <div><dt>내 팀</dt><dd>{series.managedTeamCode}</dd></div>
          <div><dt>현재 진영</dt><dd className={`is-${game.controlledSide.toLowerCase()}`}>{game.controlledSide}</dd></div>
          <div><dt>상태</dt><dd>{STATUS_LABELS[series.status]}</dd></div>
        </dl>
      </div>
      <SeriesScoreboard series={series} />
      <SeriesGameRail series={series} onOpenGame={onOpenGame} />
      <SeriesHardFearless series={series} catalog={catalog} />
    </header>
  );
}
