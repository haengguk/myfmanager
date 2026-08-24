import { fetchLiveMatchOptions, simulateLiveMatch } from './api/realMatchApi.client';
import type { MatchRequestStage, RealMatchSimulateRequestDto } from './api/realMatchApi.types';
import { createLiveMatchSession, createLiveMatchSetupOptions } from './liveMatchSession.adapter';
import type { MatchSessionViewModel, MatchSetupOptionsViewModel, MatchSetupSelection } from './matchSession.types';
import type { MatchDataSource } from './realMatch.contract';

export async function loadMatchSetupOptions(source: MatchDataSource, signal: AbortSignal): Promise<MatchSetupOptionsViewModel> {
  if (source === 'REFERENCE') {
    const module = await import('./matchSession.adapter');
    if (signal.aborted) throw new DOMException('Reference options request aborted', 'AbortError');
    return module.referenceMatchSetupOptions;
  }
  return createLiveMatchSetupOptions(await fetchLiveMatchOptions(signal));
}

export async function createMatchSession(
  source: MatchDataSource,
  options: MatchSetupOptionsViewModel,
  selection: MatchSetupSelection,
  signal: AbortSignal,
  onStage: (stage: MatchRequestStage) => void,
): Promise<MatchSessionViewModel> {
  if (source === 'REFERENCE') {
    onStage('NORMALIZING');
    const module = await import('./matchSession.adapter');
    if (signal.aborted) throw new DOMException('Reference session request aborted', 'AbortError');
    return module.createReferenceMatchSession(selection);
  }
  const request: RealMatchSimulateRequestDto = {
    schemaVersion: 'REAL_MATCH_SIMULATE_REQUEST_V1',
    blueTeamCode: selection.blueTeamId,
    redTeamCode: selection.redTeamId,
    seed: selection.seed,
  };
  const live = await simulateLiveMatch(request, signal, onStage);
  if (signal.aborted) throw new DOMException('Live session request aborted', 'AbortError');
  onStage('NORMALIZING');
  return createLiveMatchSession(live.response, options, selection, live.performance);
}
