export type CareerApiFailureKind = 'NETWORK' | 'TIMEOUT' | 'CANCELLED' | 'INVALID_JSON' | 'CONTRACT' | 'BACKEND';

export class CareerApiFailure extends Error {
  readonly kind: CareerApiFailureKind;
  readonly userMessage: string;
  readonly httpStatus: number | null;
  readonly code: string | null;
  readonly field: string | null;
  readonly retryable: boolean;

  constructor(
    kind: CareerApiFailureKind,
    userMessage: string,
    httpStatus: number | null = null,
    code: string | null = null,
    field: string | null = null,
    retryable = false,
  ) {
    super(userMessage);
    this.name = 'CareerApiFailure';
    this.kind = kind;
    this.userMessage = userMessage;
    this.httpStatus = httpStatus;
    this.code = code;
    this.field = field;
    this.retryable = retryable;
  }
}
