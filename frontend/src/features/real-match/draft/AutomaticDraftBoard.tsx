import { ChampionPortrait } from '../../../components/champion/ChampionPortrait';
import type { ChampionViewModel, DraftViewModel } from '../realMatch.types';

function championFor(viewModel: DraftViewModel, championId: string): ChampionViewModel {
  return viewModel.championsById[championId] ?? { id: championId, name: championId, nameEn: championId, portraitUrl: '' };
}

export function AutomaticDraftBoard({ viewModel, onContinue }: { viewModel: DraftViewModel; onContinue: () => void }) {
  return (
    <section className="rm-draft-board rm-auto-draft-board" aria-labelledby="rm-auto-draft-heading">
      <header className="rm-auto-draft-head">
        <div><span>REAL_MATCH_API_V1</span><h1 id="rm-auto-draft-heading">자동 Draft 결과</h1></div>
        <span className="rm-state-badge is-confirmed">완료 · 읽기 전용</span>
      </header>
      <div className="rm-auto-draft-summary">
        <span><small>경기</small><strong>GEN vs T1</strong></span>
        <span><small>게임</small><strong>Fresh Game 1</strong></span>
        <span><small>seed</small><strong className="rm-tech">{viewModel.simulationSeed}</strong></span>
      </div>
      <ol className="rm-draft-decision-list rm-scroll-area" aria-label="자동 Draft 결정 순서 20개">
        {viewModel.decisions.map((decision) => {
          const champion = championFor(viewModel, decision.championId);
          return <li className={`rm-draft-decision rm-side-${decision.side.toLowerCase()}`} key={decision.turn}>
            <span className="rm-draft-turn">{String(decision.turn).padStart(2, '0')}</span>
            <span className={`rm-draft-action is-${decision.actionType.toLowerCase()}`}>{decision.actionType}</span>
            <span className="rm-draft-decision-portrait"><ChampionPortrait name={champion.name} portraitUrl={champion.portraitUrl} /></span>
            <span><strong>{champion.name}</strong><small>{decision.side} · {decision.championId}</small></span>
          </li>;
        })}
      </ol>
      <footer className="rm-auto-draft-actions">
        <div><strong>{viewModel.draftRuleSetIdentity}</strong><span className="rm-tech" title={viewModel.finalDraftHash}>Draft hash {viewModel.finalDraftHash}</span></div>
        <button className="rm-primary-action" type="button" onClick={onContinue}>경기 재생으로 이동</button>
      </footer>
    </section>
  );
}
