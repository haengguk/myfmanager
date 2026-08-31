import type { SeriesFormat, SeriesGameStatus, SeriesViewDto } from './api/seriesApi.types';

const STATUS_LABELS: Readonly<Record<SeriesGameStatus, string>> = {
  DRAFT_PENDING: '예정', DRAFT_ACTIVE: 'Draft 진행', DRAFT_COMPLETED: 'Draft 완료',
  SIMULATION_IN_PROGRESS: '경기 실행 중', SIMULATION_FAILED_RETRYABLE: '재시도 가능',
  BLOCKED: '차단', COMMITTED: '완료', DRAFT_CANCELLED: 'Draft 취소', DRAFT_EXPIRED: 'Draft 만료',
};

function maximumGames(format: SeriesFormat): number { return format === 'BO3' ? 3 : 5; }

export function SeriesGameRail({ series, onOpenGame }: { series: SeriesViewDto; onOpenGame?: (gameNumber: number) => void }) {
  const byNumber = new Map(series.games.map((game) => [game.gameNumber, game]));
  return (
    <nav className="sr-game-rail" aria-label="시리즈 게임 진행 상황">
      {Array.from({ length: maximumGames(series.format) }, (_, index) => {
        const number = index + 1; const game = byNumber.get(number); const current = number === series.currentGameNumber;
        const winner = game?.result?.winnerTeamCode ?? null;
        const content = <>
          <span>GAME {number}</span>
          <strong>{game ? STATUS_LABELS[game.status] : '예정'}</strong>
          <small>{winner ? `${winner} 승` : game ? `${game.blueTeamCode} BLUE · ${game.redTeamCode} RED` : '서버 확정 전'}</small>
        </>;
        return game?.status === 'COMMITTED' && onOpenGame
          ? <button key={number} type="button" className={`${current ? 'is-current ' : ''}is-complete`} onClick={() => onOpenGame(number)}>{content}</button>
          : <div key={number} className={`${current ? 'is-current ' : ''}${game?.status === 'BLOCKED' ? 'is-blocked' : ''}`}>{content}</div>;
      })}
    </nav>
  );
}
