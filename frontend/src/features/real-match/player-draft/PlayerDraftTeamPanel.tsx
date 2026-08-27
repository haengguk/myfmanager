import { ChampionPortrait } from '../../../components/champion/ChampionPortrait';
import { TeamEmblem } from '../MatchChrome';
import type { MatchRosterPlayerViewModel } from '../matchSession.types';
import type { TeamSide } from '../realMatch.contract';
import type { PlayerDraftCompletedDraftDto } from './api/playerDraftApi.types';
import type { PlayerDraftChampionCatalogEntry } from './playerDraft.types';

const POSITION_LABELS = { TOP: '탑', JUNGLE: '정글', MID: '미드', ADC: '원딜', SUPPORT: '서포터' } as const;

function DraftPortrait({ championId, catalog, compact = false }: {
  championId: string; catalog: Readonly<Record<string, PlayerDraftChampionCatalogEntry>>; compact?: boolean;
}) {
  const champion = catalog[championId]?.champion;
  return (
    <span className={compact ? 'pd-mini-portrait' : 'pd-pick-portrait'} title={champion?.displayNameKo ?? championId}>
      <ChampionPortrait name={champion?.displayNameKo ?? championId} portraitUrl={champion?.portraitUrl ?? ''} />
    </span>
  );
}

export function PlayerDraftTeamPanel({ side, teamCode, roster, bans, picks, controlledSide, catalog, completedDraft }: {
  side: TeamSide; teamCode: string; roster: readonly MatchRosterPlayerViewModel[];
  bans: readonly string[]; picks: readonly string[]; controlledSide: TeamSide;
  catalog: Readonly<Record<string, PlayerDraftChampionCatalogEntry>>;
  completedDraft: PlayerDraftCompletedDraftDto | null;
}) {
  return (
    <aside className={`pd-team-panel rm-side-${side.toLowerCase()}`} aria-labelledby={`pd-${side.toLowerCase()}-team`}>
      <header>
        <TeamEmblem side={side} code={teamCode} />
        <div><span>{side} SIDE</span><h2 id={`pd-${side.toLowerCase()}-team`}>{teamCode}</h2></div>
        <span className="pd-authority-badge">{controlledSide === side ? 'PLAYER · 내 선택' : 'AI · 자동 대응'}</span>
      </header>
      <section className="pd-team-section" aria-label={`${teamCode} 로스터`}>
        <h3>{completedDraft ? '최종 포지션 배치' : '선발 로스터'}</h3>
        <div className="pd-roster-list">
          {roster.map((player) => {
            const assignment = completedDraft?.finalAssignments.find((item) => item.playerId === player.playerId);
            const champion = assignment ? catalog[assignment.championId]?.champion : null;
            return <div className="pd-roster-row" key={player.playerId}>
              {assignment ? <DraftPortrait championId={assignment.championId} catalog={catalog} /> : <span className="pd-position-mark">{player.position.slice(0, 2)}</span>}
              <span><small>{POSITION_LABELS[player.position]}</small><strong>{player.playerName}</strong></span>
              {champion ? <em>{champion.displayNameKo}</em> : null}
            </div>;
          })}
        </div>
      </section>
      <section className="pd-team-section">
        <h3>픽 순서 <span>{picks.length} / 5</span></h3>
        <div className="pd-pick-slots">
          {Array.from({ length: 5 }, (_, index) => {
            const championId = picks[index]; const champion = championId ? catalog[championId]?.champion : null;
            return <div className={championId ? 'is-filled' : ''} key={`${side}-pick-${index}`}>
              {championId ? <DraftPortrait championId={championId} catalog={catalog} /> : <span>{index + 1}</span>}
              <small>{champion?.displayNameKo ?? '대기'}</small>
            </div>;
          })}
        </div>
      </section>
      <section className="pd-team-section pd-ban-section">
        <h3>밴 <span>{bans.length} / 5</span></h3>
        <div className="pd-ban-list">
          {Array.from({ length: 5 }, (_, index) => {
            const championId = bans[index];
            return championId ? <DraftPortrait compact championId={championId} catalog={catalog} key={`${side}-ban-${championId}`} /> : <span className="pd-empty-ban" key={`${side}-ban-${index}`}>{index + 1}</span>;
          })}
        </div>
      </section>
    </aside>
  );
}
