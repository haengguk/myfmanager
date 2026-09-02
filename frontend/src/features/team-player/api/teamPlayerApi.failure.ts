export type TeamPlayerFailureKind = 'NETWORK' | 'TIMEOUT' | 'CANCELLED' | 'INVALID_JSON' | 'CONTRACT' | 'BACKEND';

export class TeamPlayerApiFailure extends Error {
  constructor(
    public readonly kind: TeamPlayerFailureKind,
    public readonly userMessage: string,
    public readonly httpStatus: number | null = null,
    public readonly code: string | null = null,
    public readonly field: string | null = null,
    public readonly retryable = false,
  ) {
    super(userMessage);
    this.name = 'TeamPlayerApiFailure';
  }
}
