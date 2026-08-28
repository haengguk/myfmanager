import { ChampionPortrait } from '../../../components/champion/ChampionPortrait';
import type { MatchRosterPlayerViewModel } from '../matchSession.types';
import type { TeamSide } from '../realMatch.contract';
import type { PlayerDraftCompletedDraftDto } from './api/playerDraftApi.types';
import type { PlayerDraftChampionCatalogEntry } from './playerDraft.types';

function ChampionSlotPortrait({ championId, catalog }: {
  championId: string | null;
  catalog: Readonly<Record<string, PlayerDraftChampionCatalogEntry>>;
}) {
  const champion = championId ? catalog[championId]?.champion : null;
  return (
    <div className={`rm-portrait rm-slot-portrait${champion ? '' : ' is-empty'}`}>
      {champion ? <ChampionPortrait name={champion.displayNameKo} portraitUrl={champion.portraitUrl} /> : <span>미선택</span>}
    </div>
  );
}

export function PlayerDraftTeamPanel({ side, teamCode, roster, bans, picks, controlledSide, catalog, completedDraft }: {
  side: TeamSide; teamCode: string; roster: readonly MatchRosterPlayerViewModel[];
  bans: readonly string[]; picks: readonly string[]; controlledSide: TeamSide;
  catalog: Readonly<Record<string, PlayerDraftChampionCatalogEntry>>;
  completedDraft: PlayerDraftCompletedDraftDto | null;
}) {
  return (
    <aside className={`rm-draft-side rm-side-${side.toLowerCase()}`} aria-label={`${teamCode} ${side === 'BLUE' ? '블루' : '레드'} 진영 선수와 픽`}>
      <header className="rm-draft-side-head">
        <strong>{teamCode} 선수·픽</strong>
        <span title={controlledSide === side ? 'PLAYER 제어 진영' : 'AI 자동 대응 진영'}>{completedDraft ? 'TOP → SUPPORT' : '픽 순서 · 배치 대기'}</span>
      </header>
      <div className="rm-draft-roster">
        {roster.map((player, index) => {
          const assignment = completedDraft?.finalAssignments.find((item) => item.playerId === player.playerId);
          const championId = assignment?.championId ?? (!completedDraft ? picks[index] ?? null : null);
          const champion = championId ? catalog[championId]?.champion : null;
          const pendingAssignment = !completedDraft && Boolean(championId);
          return (
            <article className="rm-player-slot" key={player.playerId}>
              <ChampionSlotPortrait championId={championId} catalog={catalog} />
              <div className="rm-slot-copy">
                <div className="rm-slot-meta">
                  <span className="rm-slot-role">{pendingAssignment ? `PICK ${index + 1}` : player.position}</span>
                  <span className={`rm-state-badge${assignment ? ' is-confirmed' : pendingAssignment ? ' is-current' : ''}`}>
                    {assignment ? '선택 확정' : pendingAssignment ? '포지션 미확정' : '선택 대기'}
                  </span>
                </div>
                <h3>{pendingAssignment ? champion?.displayNameKo ?? championId : player.playerName}</h3>
                <span className="rm-slot-champion">
                  {assignment ? champion?.displayNameKo ?? championId : pendingAssignment ? '20턴 완료 후 선수 배치 확정' : '챔피언 선택 대기'}
                </span>
              </div>
            </article>
          );
        })}
      </div>
      <section className="rm-bans" aria-label={`${teamCode} 밴`}>
        <header><strong>{side} 밴</strong><span>{bans.length} / 5</span></header>
        <div className="rm-ban-list">
          {Array.from({ length: 5 }, (_, index) => {
            const championId = bans[index]; const champion = championId ? catalog[championId]?.champion : null;
            return (
              <div className="rm-ban-slot" key={`${side}-ban-${index}`} title={championId ? `${index + 1}번째 밴: ${champion?.displayNameKo ?? championId}` : `${index + 1}번째 밴 빈 슬롯`}>
                {championId ? <><span className="rm-ban-order">{index + 1}</span>{champion ? <ChampionPortrait name={champion.displayNameKo} portraitUrl={champion.portraitUrl} /> : <strong>{championId.slice(0, 3).toUpperCase()}</strong>}</> : index + 1}
              </div>
            );
          })}
        </div>
      </section>
    </aside>
  );
}
