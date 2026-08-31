import { createLiveMatchSessionFromCommon, type CommonLiveMatchSource } from '../liveMatchSession.adapter.ts';
import type { MatchSessionViewModel, MatchSetupOptionsViewModel, MatchSetupSelection } from '../matchSession.types';
import type { PlayerDraftSimulationResult } from './api/playerDraftApi.client';
import type { PlayerDraftMatchPayloadDto, PlayerDraftSessionResponseDto, PlayerDraftSimulationResponseDto } from './api/playerDraftApi.types';
import type { PlayerDraftChampionCatalogEntry } from './playerDraft.types';
import type { PlayerDraftChampionCatalogResource } from './api/playerDraftApi.client';
import { markPlayerDraftLatency } from './playerDraftLatencyObserver.ts';

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

export function createPlayerDraftChampionCatalog(
  resource: PlayerDraftChampionCatalogResource,
): Readonly<Record<string, PlayerDraftChampionCatalogEntry>> {
  return Object.fromEntries(Object.entries(resource.presentationsByChampionId).map(([championId, champion]) => [
    championId,
    {
      champion,
      roles: resource.rolesByChampionId[championId] ?? [],
      feasibleRoles: [],
      unavailableReason: null,
    } satisfies PlayerDraftChampionCatalogEntry,
  ]));
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

export function createPlayerDraftMatchSessionFromPayload(
  session: PlayerDraftSessionResponseDto,
  match: PlayerDraftMatchPayloadDto,
  performance: PlayerDraftSimulationResult['performance'],
  options: MatchSetupOptionsViewModel,
  selection: MatchSetupSelection,
): MatchSessionViewModel {
  const correlationId = session.sessionId;
  markPlayerDraftLatency('PLAYER_DRAFT_SIMULATE_NORMALIZATION_START', correlationId);
  const completed = session.completedDraft;
  if (!completed) throw new Error('완료된 Player Draft assignment가 필요합니다.');
  const response: PlayerDraftSimulationResponseDto = {
    schemaVersion: 'PLAYER_DRAFT_MATCH_RESPONSE_V1', session, match,
  };
  const result = createLiveMatchSessionFromCommon(
    toCommonSource(response), options, selection, performance,
    {
      mode: 'PLAYER_CONTROLLED', sessionId: session.sessionId,
      controlledSide: session.controlledSide,
      controlEvidenceHash: completed.controlEvidenceHash,
      draftIdentity: completed.draftIdentity,
    },
  );
  markPlayerDraftLatency('PLAYER_DRAFT_SIMULATE_NORMALIZATION_COMPLETE', correlationId, { normalizationMs: result.performance.normalizationMs });
  return result;
}

export function createPlayerDraftMatchSession(
  simulation: PlayerDraftSimulationResult,
  options: MatchSetupOptionsViewModel,
  selection: MatchSetupSelection,
): MatchSessionViewModel {
  return createPlayerDraftMatchSessionFromPayload(
    simulation.response.session, simulation.response.match, simulation.performance,
    options, selection,
  );
}
