import { useEffect, useState } from 'react';
import type { MatchResultViewModel } from '../matchSession.types';
import type { ChampionViewModel } from '../realMatch.types';
import { ConfirmModal, type ResultConfirmAction } from './ConfirmModal';
import { CopyToast } from './CopyToast';
import { IntegrityAccordion } from './IntegrityAccordion';
import { MatchResultHeader } from './MatchResultHeader';
import { PlayerResultComparison } from './PlayerResultComparison';
import { ResultActions } from './ResultActions';
import { TeamFinalStats } from './TeamFinalStats';

export function MatchResultPage({ result, championsById, onBack, onDraft, onPlayback, onRerun, onNewMatch }: { result: MatchResultViewModel; championsById: Readonly<Record<string, ChampionViewModel>>; onBack: () => void; onDraft: () => void; onPlayback: () => void; onRerun: () => void; onNewMatch: () => void }) {
  const [modalAction, setModalAction] = useState<ResultConfirmAction | null>(null);
  const [returnFocus, setReturnFocus] = useState<HTMLElement | null>(null);
  const [copyToast, setCopyToast] = useState({ visible: false, label: '' });

  useEffect(() => {
    if (!copyToast.visible) return;
    const timer = window.setTimeout(() => setCopyToast((current) => ({ ...current, visible: false })), 2400);
    return () => window.clearTimeout(timer);
  }, [copyToast]);

  const openModal = (action: ResultConfirmAction, element: HTMLElement) => { setReturnFocus(element); setModalAction(action); };
  const confirm = (action: ResultConfirmAction) => {
    setModalAction(null);
    if (action === 'rerun') onRerun();
    else onNewMatch();
  };

  return (
    <div className="rm-result-app">
      <MatchResultHeader result={result} onBack={onBack} />
      <main className="rm-result-stage" aria-label="경기 결과 상세">
        <TeamFinalStats result={result} />
        <PlayerResultComparison result={result} championsById={championsById} />
      </main>
      <ResultActions
        result={result}
        onDraft={onDraft}
        onPlayback={onPlayback}
        onNewMatch={() => openModal('new-match', document.activeElement as HTMLElement)}
        onRerun={() => openModal('rerun', document.activeElement as HTMLElement)}
      />
      <IntegrityAccordion integrity={result.integrity} onCopied={(label) => setCopyToast({ visible: true, label })} />
      <ConfirmModal action={modalAction} result={result} returnFocus={returnFocus} onClose={() => setModalAction(null)} onConfirm={confirm} />
      <CopyToast {...copyToast} />
    </div>
  );
}
