import { createLiveMatchSessionFromCommon, type CommonLiveMatchSource } from '../liveMatchSession.adapter';
import type { MatchSessionViewModel, MatchSetupOptionsViewModel, MatchSetupSelection } from '../matchSession.types';
import type { PlayerDraftSimulationResult } from './api/playerDraftApi.client';
import type { PlayerDraftSimulationResponseDto } from './api/playerDraftApi.types';
import type { PlayerDraftChampionCatalogEntry } from './playerDraft.types';
import { markPlayerDraftLatency } from './playerDraftLatencyObserver';

export function mergePlayerDraftChampionCatalog(
  session: PlayerDraftSimulationResponseDto['session'],
  current: Readonly<Record<string, PlayerDraftChampionCatalogEntry>> = {},
  rolesByChampionId: Readonly<Record<string, PlayerDraftChampionCatalogEntry['roles']>> = {},
): Readonly<Record<string, PlayerDraftChampionCatalogEntry>> {
  const next: Record<string, PlayerDraftChampionCatalogEntry> = { ...current };
  session.selectableChampions.forEach((option) => {
    const championId = option.champion.championId;
    next[championId] = {
      champion: option.champion,
      roles: rolesByChampionId[championId] ?? next[championId]?.roles ?? [],
      feasibleRoles: option.feasibleRoles,
      unavailableReason: null,
    };
  });
  session.unavailableChampions.forEach((option) => {
    const championId = option.champion.championId;
    next[championId] = {
      champion: option.champion,
      roles: rolesByChampionId[championId] ?? next[championId]?.roles ?? [],
      feasibleRoles: next[championId]?.feasibleRoles ?? [],
      unavailableReason: option.reason,
    };
  });
  return next;
}

function toCommonSource(response: PlayerDraftSimulationResponseDto): CommonLiveMatchSource {
  const { session, match } = response;
  return {
    matchIdentity: match.matchIdentity,
    seed: match.seed,
    teams: match.teams,
    draft: {
      seriesGameNumber: session.seriesGameNumber,
      decisions: match.draft.decisions.map((decision) => ({
        turn: decision.turn, teamSide: decision.teamSide,
        actionType: decision.actionType, championId: decision.championId,
      })),
      blueBans: session.state.blueBans, redBans: session.state.redBans,
      bluePicks: session.state.bluePicks, redPicks: session.state.redPicks,
      draftRuleSetIdentity: session.draftRules.identity,
      finalDraftHash: match.draft.finalDraftHash,
      finalAssignmentHash: match.draft.finalAssignmentHash,
    },
    result: match.result,
    timeline: match.timeline,
    integrity: {
      runtimeProfileId: match.integrity.runtimeProfileId,
      configurationHash: match.integrity.configurationHash,
      policyHash: match.productionPolicy.policyHash,
      engineImplementationVersion: match.integrity.engineImplementationVersion,
      resourceProvenanceHash: match.integrity.resourceProvenanceHash,
      replayProvenanceHash: match.integrity.replayProvenanceHash,
      simulatorTimelineHash: match.integrity.simulatorTimelineHash,
      structuredTimelineHash: match.integrity.structuredTimelineHash,
      outputHash: match.integrity.outputHash,
      randomFingerprint: match.integrity.randomFingerprint,
    },
  };
}

export function createPlayerDraftMatchSession(
  simulation: PlayerDraftSimulationResult,
  options: MatchSetupOptionsViewModel,
  selection: MatchSetupSelection,
): MatchSessionViewModel {
  const correlationId = simulation.response.session.sessionId;
  markPlayerDraftLatency('PLAYER_DRAFT_SIMULATE_NORMALIZATION_START', correlationId);
  const completed = simulation.response.session.completedDraft;
  if (!completed) throw new Error('완료된 Player Draft assignment가 필요합니다.');
  const result = createLiveMatchSessionFromCommon(
    toCommonSource(simulation.response), options, selection, simulation.performance,
    {
      mode: 'PLAYER_CONTROLLED', sessionId: simulation.response.session.sessionId,
      controlledSide: simulation.response.session.controlledSide,
      controlEvidenceHash: completed.controlEvidenceHash,
      draftIdentity: completed.draftIdentity,
    },
  );
  markPlayerDraftLatency('PLAYER_DRAFT_SIMULATE_NORMALIZATION_COMPLETE', correlationId, { normalizationMs: result.performance.normalizationMs });
  return result;
}
