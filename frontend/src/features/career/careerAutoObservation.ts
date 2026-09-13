import type { CareerCalendarViewDto } from './api/careerApi.types';

export interface CareerAutoTarget {
  careerId: string; sourceYear: number; fixtureId: string; clientCommandId: string; jobId: string | null;
}
export function waitForCareerObservation(milliseconds: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) { reject(new DOMException('aborted', 'AbortError')); return; }
    const abort = () => { clearTimeout(timer); reject(new DOMException('aborted', 'AbortError')); };
    const timer = setTimeout(() => { signal.removeEventListener('abort', abort); resolve(); }, milliseconds);
    signal.addEventListener('abort', abort, { once: true });
  });
}

/** Observes an accepted fixture or performs bounded acceptance recovery. The caller owns one cancellable loop per screen scope. */
export async function observeCareerAuto(target: CareerAutoTarget, options: {
  signal: AbortSignal;
  read: (signal: AbortSignal) => Promise<CareerCalendarViewDto>;
  current: () => boolean;
  calendar: (view: CareerCalendarViewDto) => void;
  message: (message: string | null) => void;
  complete: (view: CareerCalendarViewDto) => Promise<void>;
  wait?: (milliseconds: number, signal: AbortSignal) => Promise<void>;
}): Promise<'COMPLETED' | 'BLOCKED' | 'STALE' | 'UNCONFIRMED'> {
  let attempt = 0, accepted = !!target.jobId, acceptedJob = target.jobId;
  while (!options.signal.aborted && options.current()) {
    try {
      await (options.wait ?? waitForCareerObservation)([400, 800, 1200, 2000, 3000][Math.min(attempt++, 4)]!, options.signal);
      if (!options.current() || options.signal.aborted) return 'STALE';
      const next = await options.read(options.signal);
      if (!options.current() || options.signal.aborted || next.careerId !== target.careerId || next.activeCalendarSeasonYear !== target.sourceYear) return 'STALE';
      const fixture = next.competition.nextFixture;
      const pending = next.competition.activePendingCommand;
      if (fixture?.fixtureId === target.fixtureId && ((acceptedJob && fixture.jobId && fixture.jobId !== acceptedJob)
        || pending && pending.clientCommandId !== target.clientCommandId)) {
        options.message('원래 경기와 현재 작업 정보가 달라 상태를 다시 확인해야 합니다.'); return 'BLOCKED';
      }
      if (fixture?.fixtureId === target.fixtureId && (fixture.jobId || pending?.clientCommandId === target.clientCommandId)) accepted = true;
      if (fixture?.fixtureId === target.fixtureId && fixture.jobId) acceptedJob = fixture.jobId;
      options.calendar(next);
      if (fixture?.fixtureId !== target.fixtureId || fixture.resultApplicationStatus === 'APPLIED') {
        // A failed terminal refresh is retried by this same loop before releasing the busy state.
        await options.complete(next);
        if (!options.current() || options.signal.aborted) return 'STALE';
        options.message(null); return 'COMPLETED';
      }
      if (['FAILED', 'BLOCKED', 'CANCELLED'].includes(fixture.jobStatus ?? '') || fixture.failureCode || fixture.blockingReason) {
        options.message('경기가 중단되었습니다. 캘린더에서 필요한 조치를 확인해 주세요.'); return 'BLOCKED';
      }
      if (!accepted && attempt >= 3) break;
      options.message(attempt >= 5 ? '서버에서 경기를 처리 중입니다. 완료되면 자동으로 갱신됩니다.' : null);
    } catch (error) {
      const failure = error as { kind?: string; retryable?: boolean; httpStatus?: number; message?: string };
      if (failure?.kind && !['NETWORK', 'TIMEOUT', 'CANCELLED'].includes(failure.kind) && !failure.retryable && failure.httpStatus !== 503) {
        if (!options.current() || options.signal.aborted) return 'STALE';
        options.message(failure.message ?? '경기 상태 조회가 거절되었습니다. 오류를 확인한 뒤 다시 시도해 주세요.'); return 'BLOCKED';
      }
      if (!options.current() || options.signal.aborted) return 'STALE';
      if (!accepted && attempt >= 3) break;
      options.message('경기 상태 연결을 다시 확인 중입니다. 서버의 원래 경기는 계속 처리됩니다.');
    }
  }
  if (!options.signal.aborted && options.current() && !accepted) { options.message('접수 여부를 확인하지 못했습니다. 실행 버튼으로 원래 요청을 다시 확인할 수 있습니다.'); return 'UNCONFIRMED'; }
  return 'STALE';
}
