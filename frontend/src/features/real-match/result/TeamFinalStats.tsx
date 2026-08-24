import type { MatchResultViewModel } from '../matchSession.types';

const formatNumber = (value: number) => value.toLocaleString('ko-KR');
const formatGold = (value: number) => `${(value / 1000).toFixed(1)}K`;
const formatDifference = (value: number) => `${value > 0 ? '+' : ''}${formatNumber(value)}`;

export function TeamFinalStats({ result }: { result: MatchResultViewModel }) {
  const blue = result.teamStats.BLUE;
  const red = result.teamStats.RED;
  const rows = [
    ['K / D / A', `${blue.kills} / ${blue.deaths} / ${blue.assists}`, `${red.kills} / ${red.deaths} / ${red.assists}`],
    ['총 골드', formatGold(blue.gold), formatGold(red.gold)],
    ['골드 격차', formatDifference(blue.goldDifference), formatDifference(red.goldDifference)],
    ['포탑', formatNumber(blue.towers), formatNumber(red.towers)],
    ['드래곤', formatNumber(blue.dragons), formatNumber(red.dragons)],
    ['바론', formatNumber(blue.barons), formatNumber(red.barons)],
    ['억제기 파괴', formatNumber(blue.inhibitorsDestroyed), formatNumber(red.inhibitorsDestroyed)],
    ['종료 사유', result.endReason === 'SIMULATION_TIMEOUT' ? '제한 시간 종료' : '넥서스 파괴', result.endReason === 'SIMULATION_TIMEOUT' ? '제한 시간 종료' : '넥서스 파괴'],
  ] as const;
  return (
    <section className="rm-team-final-stats" aria-labelledby="rm-team-stats-heading">
      <h2 id="rm-team-stats-heading">팀 종합 기록</h2>
      <div className="rm-final-stat-list">
        {rows.map(([label, blueValue, redValue]) => (
          <div className="rm-final-stat-row" key={label}>
            <strong className="rm-blue-value">{blueValue}</strong><span>{label}</span><strong className="rm-red-value">{redValue}</strong>
          </div>
        ))}
      </div>
      <div className="rm-result-bans">
        <div>{result.bans.BLUE.map((ban, index) => <span key={`${ban}-${index}`}>{ban.slice(0, 2).toUpperCase()}</span>)}</div>
        <strong>밴</strong>
        <div>{result.bans.RED.map((ban, index) => <span key={`${ban}-${index}`}>{ban.slice(0, 2).toUpperCase()}</span>)}</div>
      </div>
    </section>
  );
}
