import { LeagueApiFailure } from './api/leagueApi.failure.ts';
import type { LeagueCompletionStatusResponseDto, LeagueFixtureViewDto } from './api/leagueApi.types.ts';
import { isAmbiguousLeagueFailure, logicalLeagueCommand } from './leagueCommandReconciliation.ts';
import type { LeagueCommandRef } from './league.pointer.ts';

export type LeagueCompletionReconciliationState =
  | 'IDLE'
  | 'CANDIDATE_DISCOVERED'
  | 'RECONCILING'
  | 'POLLING'
  | 'RETRY_WAIT'
  | 'APPLIED'
  | 'TERMINAL_FAILURE'
  | 'ABORTED';

export type LeagueCompletionTrigger = 'AUTO' | 'MANUAL';
export type LeagueCompletionResult = 'APPLIED' | 'RETRY_PENDING' | 'TERMINAL_FAILURE' | 'ABORTED' | 'NO_OP';

export interface LeagueCompletionTarget {
  leagueId: string;
  seasonId: string;
  fixtureId: string;
  bindingHash: string;
  expectedRevision: number;
}

export interface LeagueCompletionSnapshot {
  state: LeagueCompletionReconciliationState;
  operationKey: string | null;
  trigger: LeagueCompletionTrigger | null;
  retryAttempt: number;
  exhausted: boolean;
}

export interface LeagueCompletionDependencies {
  readCommand: () => LeagueCommandRef | null;
  saveCommand: (command: LeagueCommandRef) => void;
  clearCommand: (command: LeagueCommandRef) => void;
  postCompletion: (target: LeagueCompletionTarget, command: LeagueCommandRef, signal: AbortSignal) => Promise<LeagueCompletionStatusResponseDto>;
  getCompletion: (target: LeagueCompletionTarget, signal: AbortSignal) => Promise<LeagueCompletionStatusResponseDto>;
  refreshAuthoritativeViews: (signal: AbortSignal) => Promise<void>;
  wait: (milliseconds: number, signal: AbortSignal) => Promise<void>;
  isVisible: () => boolean;
  createCommandId?: () => string;
  onStateChange?: (snapshot: LeagueCompletionSnapshot) => void;
  onRecoverableFailure?: (failure: LeagueApiFailure, exhausted: boolean) => void;
  onTerminalFailure?: (failure: LeagueApiFailure) => void;
  onNotFound?: (failure: LeagueApiFailure) => void;
  onApplied?: () => void;
}

export interface LeagueCompletionReconcilerConfig {
  pollDelays: readonly number[];
  retryDelays: readonly number[];
}

const DEFAULT_CONFIG: LeagueCompletionReconcilerConfig = {
  pollDelays: [400, 800, ...Array.from({ length: 18 }, () => 1000)],
  retryDelays: [600, 1500, 3000],
};

function operationKey(target: LeagueCompletionTarget): string {
  return [target.leagueId, target.seasonId, target.fixtureId, target.bindingHash, target.expectedRevision].join(':');
}

function sameCompletionScope(command: LeagueCommandRef | null, target: LeagueCompletionTarget): command is LeagueCommandRef & { expectedRevision: number } {
  return command?.kind === 'COMPLETE_PLAYER_SERIES'
    && command.scopeKey === target.fixtureId
    && command.bindingHash === target.bindingHash
    && command.expectedRevision !== null;
}

function failureFrom(cause: unknown): LeagueApiFailure {
  return cause instanceof LeagueApiFailure
    ? cause
    : new LeagueApiFailure('NETWORK', 'League 완료 결과를 확인하지 못했습니다.');
}

function isAbort(cause: unknown, signal: AbortSignal): boolean {
  return signal.aborted || (cause instanceof DOMException && cause.name === 'AbortError');
}

function isNotFound(failure: LeagueApiFailure): boolean {
  return ['LEAGUE_SEASON_NOT_FOUND', 'LEAGUE_FIXTURE_NOT_FOUND', 'LEAGUE_PLAYER_SERIES_NOT_FOUND'].includes(failure.code ?? '');
}

function canReplay(response: LeagueCompletionStatusResponseDto): boolean {
  return response.completion.allowedCommands.includes('RECONCILE_PLAYER_SERIES_COMPLETION');
}

export function leagueCompletionOperationKey(target: LeagueCompletionTarget): string {
  return operationKey(target);
}

export function selectLeagueCompletionCandidate(
  fixtures: readonly LeagueFixtureViewDto[],
  command: LeagueCommandRef | null,
): LeagueFixtureViewDto | undefined {
  const advertised = fixtures.find((fixture) => fixture.allowedCommands.includes('RECONCILE_PLAYER_SERIES_COMPLETION'));
  if (advertised) return advertised;
  if (command?.kind !== 'COMPLETE_PLAYER_SERIES') return undefined;
  return fixtures.find((fixture) => fixture.fixtureId === command.scopeKey && fixture.bindingHash === command.bindingHash);
}

export class LeagueCompletionReconciler {
  private readonly config: LeagueCompletionReconcilerConfig;
  private active: { key: string; controller: AbortController; promise: Promise<LeagueCompletionResult> } | null = null;
  private readonly queued = new Map<string, Promise<LeagueCompletionResult>>();
  private abortGeneration = 0;
  private snapshot: LeagueCompletionSnapshot = { state: 'IDLE', operationKey: null, trigger: null, retryAttempt: 0, exhausted: false };

  constructor(config: Partial<LeagueCompletionReconcilerConfig> = {}) {
    this.config = { pollDelays: config.pollDelays ?? DEFAULT_CONFIG.pollDelays, retryDelays: config.retryDelays ?? DEFAULT_CONFIG.retryDelays };
  }

  current(): LeagueCompletionSnapshot {
    return { ...this.snapshot };
  }

  releaseRetryWait(): void {
    if (this.snapshot.state === 'RETRY_WAIT' && this.snapshot.exhausted) {
      this.snapshot = { state: 'IDLE', operationKey: null, trigger: null, retryAttempt: 0, exhausted: false };
    }
  }

  abort(): void {
    this.abortGeneration += 1;
    this.active?.controller.abort(new DOMException('League completion reconciliation aborted', 'AbortError'));
    this.queued.clear();
  }

  reconcileIfAvailable(target: LeagueCompletionTarget, trigger: LeagueCompletionTrigger, blockedByOperation: string | null, dependencies: LeagueCompletionDependencies): Promise<LeagueCompletionResult> {
    return blockedByOperation ? Promise.resolve('NO_OP') : this.reconcile(target, trigger, dependencies);
  }

  reconcile(target: LeagueCompletionTarget, trigger: LeagueCompletionTrigger, dependencies: LeagueCompletionDependencies): Promise<LeagueCompletionResult> {
    const requestedKey = operationKey(target);
    if (this.active) {
      if (this.active.key === requestedKey) return this.active.promise;
      return this.enqueue(target, trigger, requestedKey, dependencies);
    }
    const queued = this.queued.get(requestedKey);
    if (queued) return queued;

    const persisted = dependencies.readCommand();
    const effectiveTarget = sameCompletionScope(persisted, target)
      ? { ...target, expectedRevision: persisted.expectedRevision }
      : target;
    const key = operationKey(effectiveTarget);

    if (trigger === 'AUTO' && this.snapshot.operationKey === key && (this.snapshot.state === 'APPLIED' || (this.snapshot.state === 'RETRY_WAIT' && this.snapshot.exhausted) || this.snapshot.state === 'TERMINAL_FAILURE')) {
      return Promise.resolve('NO_OP');
    }

    const descriptor = {
      kind: 'COMPLETE_PLAYER_SERIES' as const,
      scopeKey: effectiveTarget.fixtureId,
      expectedRevision: effectiveTarget.expectedRevision,
      bindingHash: effectiveTarget.bindingHash,
    };
    const command = logicalLeagueCommand(persisted, descriptor, dependencies.createCommandId);
    const recovering = command === persisted;
    if (!recovering) dependencies.saveCommand(command);
    this.transition('CANDIDATE_DISCOVERED', key, trigger, 0, false, dependencies);

    const controller = new AbortController();
    const promise = this.execute(effectiveTarget, command, recovering, trigger, controller, dependencies)
      .finally(() => { if (this.active?.promise === promise) this.active = null; });
    this.active = { key, controller, promise };
    return promise;
  }

  private enqueue(
    target: LeagueCompletionTarget,
    trigger: LeagueCompletionTrigger,
    key: string,
    dependencies: LeagueCompletionDependencies,
  ): Promise<LeagueCompletionResult> {
    const existing = this.queued.get(key);
    if (existing) return existing;
    const predecessor = this.active?.promise ?? Promise.resolve<LeagueCompletionResult>('NO_OP');
    const generation = this.abortGeneration;
    let promise: Promise<LeagueCompletionResult>;
    const start = (): Promise<LeagueCompletionResult> | LeagueCompletionResult => {
      if (generation !== this.abortGeneration) return 'ABORTED';
      if (this.queued.get(key) === promise) this.queued.delete(key);
      return this.reconcile(target, trigger, dependencies);
    };
    promise = predecessor.then(start, start).finally(() => {
      if (this.queued.get(key) === promise) this.queued.delete(key);
    });
    this.queued.set(key, promise);
    return promise;
  }

  private async execute(
    target: LeagueCompletionTarget,
    command: LeagueCommandRef,
    recovering: boolean,
    trigger: LeagueCompletionTrigger,
    controller: AbortController,
    dependencies: LeagueCompletionDependencies,
  ): Promise<LeagueCompletionResult> {
    const key = operationKey(target);
    let confirmFirst = recovering;
    let retryAttempt = 0;

    while (true) {
      try {
        if (!dependencies.isVisible()) {
          controller.abort(new DOMException('League page hidden', 'AbortError'));
          throw controller.signal.reason;
        }

        let response: LeagueCompletionStatusResponseDto;
        if (confirmFirst) {
          this.transition('RECONCILING', key, trigger, retryAttempt, false, dependencies);
          response = await dependencies.getCompletion(target, controller.signal);
          if (response.completion.standingsApplied) return await this.finishApplied(target, command, trigger, retryAttempt, dependencies, controller.signal);
          if (canReplay(response)) response = await dependencies.postCompletion(target, command, controller.signal);
        } else {
          this.transition('RECONCILING', key, trigger, retryAttempt, false, dependencies);
          response = await dependencies.postCompletion(target, command, controller.signal);
        }

        if (response.completion.standingsApplied) return await this.finishApplied(target, command, trigger, retryAttempt, dependencies, controller.signal);
        response = await this.poll(target, trigger, retryAttempt, dependencies, controller.signal);
        if (response.completion.standingsApplied) return await this.finishApplied(target, command, trigger, retryAttempt, dependencies, controller.signal);
        throw new LeagueApiFailure('TIMEOUT', '완료 증거는 접수됐지만 순위 반영 확인이 계속 진행 중입니다.');
      } catch (cause) {
        if (isAbort(cause, controller.signal)) {
          this.transition('ABORTED', key, trigger, retryAttempt, false, dependencies);
          return 'ABORTED';
        }

        const failure = failureFrom(cause);
        if (isNotFound(failure)) {
          dependencies.clearCommand(command);
          dependencies.onNotFound?.(failure);
          this.transition('TERMINAL_FAILURE', key, trigger, retryAttempt, false, dependencies);
          return 'TERMINAL_FAILURE';
        }
        if (!isAmbiguousLeagueFailure(failure)) {
          dependencies.clearCommand(command);
          dependencies.onTerminalFailure?.(failure);
          this.transition('TERMINAL_FAILURE', key, trigger, retryAttempt, false, dependencies);
          return 'TERMINAL_FAILURE';
        }

        const exhausted = retryAttempt >= this.config.retryDelays.length;
        dependencies.onRecoverableFailure?.(failure, exhausted);
        this.transition('RETRY_WAIT', key, trigger, retryAttempt, exhausted, dependencies);
        if (exhausted) return 'RETRY_PENDING';
        try { await dependencies.wait(this.config.retryDelays[retryAttempt], controller.signal); }
        catch (waitFailure) {
          if (isAbort(waitFailure, controller.signal)) {
            this.transition('ABORTED', key, trigger, retryAttempt, false, dependencies);
            return 'ABORTED';
          }
          throw waitFailure;
        }
        retryAttempt += 1;
        confirmFirst = true;
      }
    }
  }

  private async poll(
    target: LeagueCompletionTarget,
    trigger: LeagueCompletionTrigger,
    retryAttempt: number,
    dependencies: LeagueCompletionDependencies,
    signal: AbortSignal,
  ): Promise<LeagueCompletionStatusResponseDto> {
    this.transition('POLLING', operationKey(target), trigger, retryAttempt, false, dependencies);
    let latest: LeagueCompletionStatusResponseDto | null = null;
    for (const delay of this.config.pollDelays) {
      await dependencies.wait(delay, signal);
      latest = await dependencies.getCompletion(target, signal);
      if (latest.completion.standingsApplied) return latest;
    }
    if (latest) return latest;
    throw new LeagueApiFailure('TIMEOUT', '완료 결과 상태 확인 시간이 초과되었습니다.');
  }

  private async finishApplied(
    target: LeagueCompletionTarget,
    command: LeagueCommandRef,
    trigger: LeagueCompletionTrigger,
    retryAttempt: number,
    dependencies: LeagueCompletionDependencies,
    signal: AbortSignal,
  ): Promise<LeagueCompletionResult> {
    await dependencies.refreshAuthoritativeViews(signal);
    dependencies.clearCommand(command);
    this.transition('APPLIED', operationKey(target), trigger, retryAttempt, false, dependencies);
    dependencies.onApplied?.();
    return 'APPLIED';
  }

  private transition(
    state: LeagueCompletionReconciliationState,
    key: string,
    trigger: LeagueCompletionTrigger,
    retryAttempt: number,
    exhausted: boolean,
    dependencies: LeagueCompletionDependencies,
  ): void {
    this.snapshot = { state, operationKey: key, trigger, retryAttempt, exhausted };
    dependencies.onStateChange?.(this.current());
  }
}
