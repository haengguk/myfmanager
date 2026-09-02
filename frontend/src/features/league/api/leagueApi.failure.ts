export type LeagueFailureKind = 'NETWORK' | 'TIMEOUT' | 'CANCELLED' | 'INVALID_JSON' | 'CONTRACT' | 'BACKEND';

export class LeagueApiFailure extends Error {
  readonly kind: LeagueFailureKind;
  readonly userMessage: string;
  readonly httpStatus: number | null;
  readonly code: string | null;
  readonly field: string | null;
  readonly retryable: boolean;
  readonly currentLifecycleRevision: number | null;
  readonly currentLifecycleStatus: string | null;
  constructor(
    kind: LeagueFailureKind, userMessage: string, httpStatus: number | null = null,
    code: string | null = null, field: string | null = null, retryable = false,
    currentLifecycleRevision: number | null = null, currentLifecycleStatus: string | null = null,
  ) {
    super(userMessage); this.name = 'LeagueApiFailure'; this.kind = kind;
    this.userMessage = userMessage; this.httpStatus = httpStatus; this.code = code;
    this.field = field; this.retryable = retryable;
    this.currentLifecycleRevision = currentLifecycleRevision;
    this.currentLifecycleStatus = currentLifecycleStatus;
  }
}
