import type { MatchResultViewModel } from '../matchSession.types';

export function ResultActions({ result, draftContextLabel, onDraft, onPlayback, onNewMatch, onRerun }: { result: MatchResultViewModel; draftContextLabel: string; onDraft: () => void; onPlayback: () => void; onNewMatch: () => void; onRerun: () => void }) {
  return (
    <section className="rm-result-actions" aria-label="경기 결과 행동">
      <div><strong>{result.seasonLabel}</strong><span>Game {result.gameNumber} · seed {result.seed} · {draftContextLabel}</span></div>
      <span className="rm-action-spacer" />
      <button className="rm-text-action" type="button" onClick={onDraft}>{draftContextLabel} 다시 보기</button>
      <button className="rm-text-action" type="button" onClick={onPlayback}>경기 재생 보기</button>
      <button className="rm-secondary-action" type="button" onClick={onNewMatch}>새 경기</button>
      <button className="rm-primary-action" type="button" onClick={onRerun}>처음부터 재생</button>
    </section>
  );
}
