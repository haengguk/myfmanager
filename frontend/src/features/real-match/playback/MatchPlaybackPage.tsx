import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { comparisonAt, selectSnapshot } from '../realMatch.adapter';
import { MatchToast, MatchUtilityBar } from '../MatchChrome';
import type { PlaybackViewModel } from '../realMatch.types';
import { EventLog } from './EventLog';
import { LiveChampionPanel } from './LiveChampionPanel';
import { MatchScoreboard } from './MatchScoreboard';
import { PlaybackControls, type PlaybackSpeed } from './PlaybackControls';
import { PlayerComparison } from './PlayerComparison';
import { ResultModal } from './ResultModal';

export function MatchPlaybackPage({ viewModel, onBack, onDraft, onComplete }: { viewModel: PlaybackViewModel; onBack: () => void; onDraft: () => void; onComplete: () => void }) {
  const [currentSeconds, setCurrentSeconds] = useState(viewModel.initialSeconds);
  const [speed, setSpeed] = useState<PlaybackSpeed>(1);
  const [playing, setPlaying] = useState(false);
  const [resultOpen, setResultOpen] = useState(false);
  const [toast, setToast] = useState({ title: '', message: '', visible: false });
  const toastTimer = useRef<number | null>(null);
  const resultButtonRef = useRef<HTMLButtonElement>(null);
  const completeAtEndRef = useRef(false);
  const snapshot = useMemo(() => selectSnapshot(viewModel, currentSeconds), [viewModel, currentSeconds]);
  const comparison = useMemo(() => comparisonAt(viewModel, currentSeconds), [viewModel, currentSeconds]);

  const showToast = useCallback((title: string, message: string) => {
    if (toastTimer.current !== null) window.clearTimeout(toastTimer.current);
    setToast({ title, message, visible: true });
    toastTimer.current = window.setTimeout(() => setToast((current) => ({ ...current, visible: false })), 2600);
  }, []);

  useEffect(() => {
    if (!playing) return;
    const timer = window.setInterval(() => setCurrentSeconds((current) => {
      const next = Math.min(viewModel.durationSeconds, current + speed / 4);
      if (next >= viewModel.durationSeconds) { setPlaying(false); completeAtEndRef.current = true; }
      return next;
    }), 250);
    return () => window.clearInterval(timer);
  }, [playing, speed, viewModel.durationSeconds]);

  useEffect(() => {
    if (currentSeconds < viewModel.durationSeconds || !completeAtEndRef.current) return;
    completeAtEndRef.current = false;
    onComplete();
  }, [currentSeconds, onComplete, viewModel.durationSeconds]);

  useEffect(() => () => { if (toastTimer.current !== null) window.clearTimeout(toastTimer.current); }, []);

  const changeSpeed = (nextSpeed: PlaybackSpeed) => { setSpeed(nextSpeed); showToast('재생 속도 변경', `x${nextSpeed} 속도로 설정했습니다.`); };
  const applyResult = () => { setResultOpen(false); setCurrentSeconds(viewModel.durationSeconds); setPlaying(false); onComplete(); };
  const outcomeLabel = viewModel.winner === null ? '승자 없음' : `${viewModel.teams[viewModel.winner].code} 승리`;

  return (
    <div className="rm-playback-app">
      <MatchUtilityBar meta={viewModel.seasonLabel} onBack={onBack} secondaryLabel="Draft 다시 보기" onSecondary={onDraft} />
      <MatchScoreboard viewModel={viewModel} snapshot={snapshot} currentSeconds={currentSeconds} playing={playing} />
      <main className="rm-match-core">
        <LiveChampionPanel side="BLUE" teamCode={viewModel.teams.BLUE.code} snapshot={snapshot.teams.BLUE} championsById={viewModel.championsById} />
        <EventLog events={viewModel.events} currentSeconds={currentSeconds} />
        <LiveChampionPanel side="RED" teamCode={viewModel.teams.RED.code} snapshot={snapshot.teams.RED} championsById={viewModel.championsById} />
      </main>
      <PlaybackControls currentSeconds={currentSeconds} durationSeconds={viewModel.durationSeconds} speed={speed} playing={playing} resultButtonRef={resultButtonRef} onToggle={() => { if (currentSeconds >= viewModel.durationSeconds) setCurrentSeconds(0); setPlaying((current) => !current); }} onSpeedChange={changeSpeed} onSeek={(seconds) => { setCurrentSeconds(seconds); setPlaying(false); }} onOpenResult={() => setResultOpen(true)} />
      <PlayerComparison rows={comparison} currentSeconds={currentSeconds} championsById={viewModel.championsById} blueTeamCode={viewModel.teams.BLUE.code} redTeamCode={viewModel.teams.RED.code} />
      <ResultModal open={resultOpen} blueName={viewModel.teams.BLUE.code} redName={viewModel.teams.RED.code} blueScore={viewModel.finalScore.BLUE} redScore={viewModel.finalScore.RED} outcomeLabel={outcomeLabel} returnFocusRef={resultButtonRef} onClose={() => setResultOpen(false)} onConfirm={applyResult} />
      <MatchToast {...toast} />
    </div>
  );
}
