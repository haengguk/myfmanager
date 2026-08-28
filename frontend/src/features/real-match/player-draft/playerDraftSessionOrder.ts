import type { PlayerDraftSessionResponseDto, PlayerDraftTurnEvidenceDto } from './api/playerDraftApi.types';

const STATUS_ORDER = {
  ACTIVE: 0,
  COMPLETED: 1,
  SIMULATED: 2,
  CANCELLED: 3,
  EXPIRED: 3,
} as const;

export function shouldApplyPlayerDraftSession(
  current: PlayerDraftSessionResponseDto,
  incoming: PlayerDraftSessionResponseDto,
): boolean {
  if (incoming.sessionId !== current.sessionId) return false;
  if (incoming.revision !== current.revision) return incoming.revision > current.revision;
  return STATUS_ORDER[incoming.status] >= STATUS_ORDER[current.status];
}

export function playerDraftActionWasApplied(
  decisions: readonly PlayerDraftTurnEvidenceDto[],
  clientActionId: string,
): boolean {
  return decisions.some((decision) => decision.playerSelectionEvidence?.clientActionId === clientActionId);
}
