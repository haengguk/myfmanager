import type { SeriesViewDto } from './api/seriesApi.types';

function teamName(series: SeriesViewDto, code: string): string {
  return series.teams.find((team) => team.teamCode === code)?.displayName ?? code;
}

export function SeriesScoreboard({ series }: { series: SeriesViewDto }) {
  const [teamA, teamB] = series.teams;
  return (
    <section className="sr-scoreboard" aria-label={`${series.format} 시리즈 점수`}>
      <div className={teamA.teamCode === series.managedTeamCode ? 'is-managed' : ''}>
        <span>{teamA.teamCode === series.managedTeamCode ? '내 팀' : '상대 팀'}</span>
        <strong>{teamA.teamCode}</strong>
        <small>{teamName(series, teamA.teamCode)}</small>
      </div>
      <p aria-live="polite" aria-atomic="true">
        <b>{series.score[teamA.teamCode]}</b><i aria-hidden="true">:</i><b>{series.score[teamB.teamCode]}</b>
        <span>{series.format} · {series.winsRequired}선승</span>
      </p>
      <div className={teamB.teamCode === series.managedTeamCode ? 'is-managed' : ''}>
        <span>{teamB.teamCode === series.managedTeamCode ? '내 팀' : '상대 팀'}</span>
        <strong>{teamB.teamCode}</strong>
        <small>{teamName(series, teamB.teamCode)}</small>
      </div>
    </section>
  );
}
