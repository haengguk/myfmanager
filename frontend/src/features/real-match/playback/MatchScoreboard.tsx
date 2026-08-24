import { formatMatchTime } from '../realMatch.adapter';
import { TeamEmblem } from '../MatchChrome';
import type { MatchSnapshotViewModel, PlaybackViewModel, TeamSide } from '../realMatch.types';

export function MatchScoreboard({ viewModel, snapshot, currentSeconds, playing }: { viewModel: PlaybackViewModel; snapshot: MatchSnapshotViewModel; currentSeconds: number; playing: boolean }) {
  return <section className="rm-scoreboard" aria-label="경기 스코어보드"><TeamScoreBlock side="BLUE" viewModel={viewModel} kills={snapshot.teams.BLUE.kills} gold={snapshot.teams.BLUE.gold} otherGold={snapshot.teams.RED.gold} /><div className="rm-score-center"><div><strong>Game {viewModel.gameNumber}</strong><span>·</span><span>{viewModel.seriesType}</span></div><time>{formatMatchTime(currentSeconds)}</time><span>{currentSeconds >= viewModel.durationSeconds ? '경기 종료' : playing ? '재생 중' : '일시정지'}</span></div><TeamScoreBlock side="RED" viewModel={viewModel} kills={snapshot.teams.RED.kills} gold={snapshot.teams.RED.gold} otherGold={snapshot.teams.BLUE.gold} /></section>;
}

function TeamScoreBlock({ side, viewModel, kills, gold, otherGold }: { side: TeamSide; viewModel: PlaybackViewModel; kills: number; gold: number; otherGold: number }) {
  const team = viewModel.teams[side];
  const lead = gold - otherGold;
  return <div className={`rm-score-team rm-side-${side.toLowerCase()}`}><TeamEmblem side={side} code={team.code} /><div className="rm-score-team-copy"><span>{side} · Fresh Game 1</span><h2>{team.code}</h2><small>{team.detail}</small></div><div className="rm-score-stat"><span>킬</span><strong>{kills}</strong></div><div className="rm-score-stat"><span>총 골드</span><strong>{(gold / 1000).toFixed(1)}K</strong>{lead > 0 ? <em>{team.code} +{(lead / 1000).toFixed(1)}K</em> : null}</div></div>;
}
