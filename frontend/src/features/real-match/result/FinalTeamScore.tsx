import { formatMatchTime } from '../realMatch.adapter';
import type { MatchResultViewModel } from '../matchSession.types';
import type { TeamSide } from '../realMatch.types';

function verdict(side: TeamSide, winner: TeamSide | null): string {
  if (winner === null) return '무승부';
  return winner === side ? '승리' : '패배';
}

export function FinalTeamScore({ result }: { result: MatchResultViewModel }) {
  const timeout = result.endReason === 'SIMULATION_TIMEOUT';
  return (
    <section className={`rm-result-score${timeout ? ' is-timeout' : ''}`} aria-labelledby="rm-result-heading">
      <div className="rm-result-team rm-side-blue">
        <div className="rm-result-team-mark" role="img" aria-label={`${result.teams.BLUE.code} 약칭 로고 자리`}><span>{result.teams.BLUE.code}</span></div>
        <div><strong>{result.teams.BLUE.code}</strong><span>{result.teams.BLUE.detail}</span></div>
      </div>
      <div className="rm-result-kills"><span>킬</span><strong>{result.teamStats.BLUE.kills}</strong></div>
      <div className={`rm-result-verdict${result.winner === 'BLUE' ? ' is-winner' : ''}`}>{verdict('BLUE', result.winner)}</div>
      <div className="rm-result-time"><span>경기 시간</span><strong id="rm-result-heading">{formatMatchTime(result.durationSeconds)}</strong><em>{timeout ? '제한 시간 종료 · 승자 없음' : `Game ${result.gameNumber} · ${result.seriesType}`}</em></div>
      <div className={`rm-result-verdict rm-side-red${result.winner === 'RED' ? ' is-winner' : ''}`}>{verdict('RED', result.winner)}</div>
      <div className="rm-result-kills"><span>킬</span><strong>{result.teamStats.RED.kills}</strong></div>
      <div className="rm-result-team rm-side-red">
        <div className="rm-result-team-mark" role="img" aria-label={`${result.teams.RED.code} 약칭 로고 자리`}><span>{result.teams.RED.code}</span></div>
        <div><strong>{result.teams.RED.code}</strong><span>{result.teams.RED.detail}</span></div>
      </div>
    </section>
  );
}
