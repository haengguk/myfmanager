import type { SeriesStatus } from './seriesApi.types';

export type SeriesFailureKind = 'NETWORK' | 'CANCELLED' | 'TIMEOUT' | 'BACKEND' | 'INVALID_JSON' | 'CONTRACT';

export class SeriesApiFailure extends Error {
  public readonly kind: SeriesFailureKind;
  public readonly userMessage: string;
  public readonly status: number | null;
  public readonly code: string | null;
  public readonly field: string | null;
  public readonly retryable: boolean;
  public readonly currentRevision: number | null;
  public readonly currentStatus: SeriesStatus | null;

  constructor(
    kind: SeriesFailureKind,
    userMessage: string,
    status: number | null = null,
    code: string | null = null,
    field: string | null = null,
    retryable = false,
    currentRevision: number | null = null,
    currentStatus: SeriesStatus | null = null,
  ) {
    super(userMessage);
    this.name = 'SeriesApiFailure';
    this.kind = kind;
    this.userMessage = userMessage;
    this.status = status;
    this.code = code;
    this.field = field;
    this.retryable = retryable;
    this.currentRevision = currentRevision;
    this.currentStatus = currentStatus;
  }
}
