import type { RefObject } from 'react';
import { formatMatchTime } from '../realMatch.adapter';

export const playbackSpeeds = [1, 5, 10, 30] as const;
export type PlaybackSpeed = typeof playbackSpeeds[number];

interface PlaybackControlsProps {
  currentSeconds: number;
  durationSeconds: number;
  speed: PlaybackSpeed;
  playing: boolean;
  resultReady: boolean;
  onToggle: () => void;
  onSpeedChange: (speed: PlaybackSpeed) => void;
  onSeek: (seconds: number) => void;
  onReset: () => void;
  onResult: () => void;
  resultButtonRef: RefObject<HTMLButtonElement>;
}

export function PlaybackControls(props: PlaybackControlsProps) {
  return (
    <section className="rm-playback-controls" aria-label="경기 재생 제어">
      <button className="rm-primary-action rm-play-toggle" type="button" aria-pressed={props.playing} onClick={props.onToggle}>{props.playing ? '일시정지' : '재생'}</button>
      <div className="rm-speed-group" aria-label="재생 속도">
        {playbackSpeeds.map((speed) => <button className={props.speed === speed ? 'is-active' : ''} type="button" key={speed} aria-pressed={props.speed === speed} onClick={() => props.onSpeedChange(speed)}>x{speed}</button>)}
      </div>
      <PlaybackTimeline currentSeconds={props.currentSeconds} durationSeconds={props.durationSeconds} onSeek={props.onSeek} />
      <span className="rm-control-status">x{props.speed} · {props.playing ? '재생 중' : '일시정지'}</span>
      <button className="rm-text-action" type="button" onClick={props.onReset}>처음으로</button>
      <button className="rm-secondary-action" ref={props.resultButtonRef} type="button"
        aria-label={props.resultReady ? '경기 결과 창 열기' : '경기 종료 시점과 전체 로그 보기'}
        title={props.resultReady ? '경기 결과 창을 엽니다.' : '경기 종료 시점으로 이동해 전체 로그를 표시합니다.'}
        onClick={props.onResult}>경기 결과</button>
    </section>
  );
}

function PlaybackTimeline({ currentSeconds, durationSeconds, onSeek }: Pick<PlaybackControlsProps, 'currentSeconds' | 'durationSeconds' | 'onSeek'>) {
  return <div className="rm-timeline-wrap"><time>{formatMatchTime(currentSeconds)}</time><input type="range" min={0} max={durationSeconds} value={Math.floor(currentSeconds)} aria-label="경기 타임라인" onChange={(event) => onSeek(Number(event.target.value))} /><time>{formatMatchTime(durationSeconds)}</time></div>;
}
