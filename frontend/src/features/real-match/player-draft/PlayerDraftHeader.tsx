import { ChampionPortrait } from '../../../components/champion/ChampionPortrait';
import { TeamEmblem } from '../MatchChrome';
import type { MatchTeamOptionViewModel, MatchSetupOptionsViewModel } from '../matchSession.types';
import type { PlayerDraftSessionResponseDto } from './api/playerDraftApi.types';
import type { PlayerDraftChampionCatalogEntry } from './playerDraft.types';

function TeamHeader({ side, team, gameNumber, seasonLabel }: {
  side: 'BLUE' | 'RED'; team: MatchTeamOptionViewModel; gameNumber: number; seasonLabel: string;
}) {
  return (
    <div className={`rm-team-head rm-side-${side.toLowerCase()}`}>
      <TeamEmblem side={side} code={team.code} />
      <div className="rm-team-copy">
        <span>{side} · {side === 'BLUE' ? '블루 진영' : '레드 진영'}</span>
        <h2>{team.code}</h2>
        <p>{seasonLabel} · {team.name}</p>
      </div>
      <div className="rm-series-record"><span>Game</span><strong>{gameNumber}</strong></div>
    </div>
  );
}

export function PlayerDraftHeader({ session, options, blueTeam, redTeam, catalog }: {
  session: PlayerDraftSessionResponseDto;
  options: MatchSetupOptionsViewModel;
  blueTeam: MatchTeamOptionViewModel;
  redTeam: MatchTeamOptionViewModel;
  catalog: Readonly<Record<string, PlayerDraftChampionCatalogEntry>>;
}) {
  const turn = session.currentTurn;
  const phaseLabel = turn ? (turn.actionType === 'BAN' ? '밴 단계' : '픽 단계') : '밴픽 완료';
  const turnLabel = turn
    ? `${turn.teamSide} · ${turn.actionType === 'BAN' ? '밴 선택' : '픽 선택'} 차례`
    : '모든 선택 완료';
  return (
    <section className="rm-match-header" aria-label="직접 밴픽 경기 정보">
      <TeamHeader side="BLUE" team={blueTeam} gameNumber={options.gameNumber} seasonLabel={options.seasonLabel} />
      <div className="rm-draft-clock">
        <div className="rm-clock-meta"><strong>Game {options.gameNumber}</strong><span>·</span><span>{options.seriesType}</span><span>·</span><span>{phaseLabel}</span></div>
        <div className="rm-timer" aria-live="polite">{String(turn?.turn ?? session.decisions.length).padStart(2, '0')}</div>
        <div className="rm-turn-copy">{turnLabel}</div>
        <div className="rm-fearless-row" aria-label="하드 피어리스 제외 챔피언">
          {session.state.hardFearlessExclusions.length ? <>
            <span>하드 피어리스</span>
            {session.state.hardFearlessExclusions.slice(0, 5).map((championId) => {
              const champion = catalog[championId]?.champion;
              return <span className="rm-portrait" title={champion?.displayNameKo ?? championId} key={championId}><ChampionPortrait name={champion?.displayNameKo ?? championId} portraitUrl={champion?.portraitUrl ?? ''} /></span>;
            })}
          </> : <span>Game {options.gameNumber} · 피어리스 제외 챔피언 없음</span>}
        </div>
      </div>
      <TeamHeader side="RED" team={redTeam} gameNumber={options.gameNumber} seasonLabel={options.seasonLabel} />
    </section>
  );
}
