import {
  validatePlayerDraftApiErrorPayload, validatePlayerDraftSessionPayload,
  validatePlayerDraftSimulationPayload,
} from '../src/features/real-match/player-draft/api/playerDraftApi.validation.ts';
import {
  playerDraftEntryMatchesRole, playerDraftUnavailableReasonLabels,
} from '../src/features/real-match/player-draft/playerDraft.types.ts';
import {
  playerDraftActionWasApplied, shouldApplyPlayerDraftSession,
} from '../src/features/real-match/player-draft/playerDraftSessionOrder.ts';

const H = 'a'.repeat(64); const C = 'b'.repeat(64); const R = 'c'.repeat(64);
const expectation = { sessionId: '00000000-0000-0000-0000-000000000073', blueTeamCode: 'GEN', redTeamCode: 'T1', controlledSide: 'BLUE', seed: '73' };
const turns = [
  ['BLUE', 'BAN'], ['RED', 'BAN'], ['BLUE', 'BAN'], ['RED', 'BAN'], ['BLUE', 'BAN'], ['RED', 'BAN'],
  ['BLUE', 'PICK'], ['RED', 'PICK'], ['RED', 'PICK'], ['BLUE', 'PICK'], ['BLUE', 'PICK'], ['RED', 'PICK'],
  ['RED', 'BAN'], ['BLUE', 'BAN'], ['RED', 'BAN'], ['BLUE', 'BAN'], ['RED', 'PICK'], ['BLUE', 'PICK'], ['BLUE', 'PICK'], ['RED', 'PICK'],
];
const positions = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];
const clone = (value) => structuredClone(value);
const champion = (id) => ({ championId: id, displayNameKo: `챔피언 ${id}`, displayNameEn: `Champion ${id}`, portraitUrl: `https://example.test/${id}.png` });
const policy = (id) => ({ policyId: id, policyHash: H });
const poolEntry = (id) => ({ championId: id, canonicalRank: 1, rawFinalSearchScore: 1, canonicalFinalScore: 1, canonicalScoreLoss: 0, rankWeight: 70 });
function decision(index) {
  const turn = index + 1; const [teamSide, actionType] = turns[index]; const championId = `champion-${turn}`; const authority = teamSide === 'BLUE' ? 'PLAYER' : 'AI';
  return {
    turn, teamSide, actionType, championId, authority, stateBeforeHash: H, stateAfterHash: H,
    autoSelectionTrace: authority === 'AI' ? {
      policyId: 'AUTO', policyMode: 'PRODUCTION', policyHash: H, selectionContextHash: H,
      turn, teamSide, actionType, bestCandidateId: championId, bestCanonicalScore: 1,
      eligiblePool: [poolEntry(championId)], selectedChampionId: championId, selectedRank: 1,
      selectedCanonicalScoreLoss: 0, drawBucket: null, totalEligibleWeight: 70, reason: 'ONLY_ONE_WITHIN_WINDOW',
    } : null,
    playerSelectionEvidence: authority === 'PLAYER' ? {
      controlledSide: 'BLUE', turn, actionType, championId, stateBeforeHash: H,
      selectableSetIdentity: H, legalityResult: 'LEGAL', clientActionId: `action-${turn}`,
    } : null,
  };
}
const decisions = turns.map((_, index) => decision(index));
const ids = (teamSide, actionType) => decisions.filter((item) => item.teamSide === teamSide && item.actionType === actionType).map((item) => item.championId);
const state = { blueBans: ids('BLUE', 'BAN'), redBans: ids('RED', 'BAN'), bluePicks: ids('BLUE', 'PICK'), redPicks: ids('RED', 'PICK'), hardFearlessExclusions: [] };
const assignments = ['BLUE', 'RED'].flatMap((teamSide) => positions.map((position, index) => ({ playerId: `${teamSide.toLowerCase()}-${position.toLowerCase()}`, teamSide, position, championId: state[teamSide === 'BLUE' ? 'bluePicks' : 'redPicks'][index] })));
const baseSession = {
  schemaVersion: 'PLAYER_DRAFT_SESSION_V1', sessionId: expectation.sessionId, revision: 0, status: 'ACTIVE',
  teams: [{ teamSide: 'BLUE', teamCode: 'GEN', displayName: 'Gen.G' }, { teamSide: 'RED', teamCode: 'T1', displayName: 'T1' }],
  controlledSide: 'BLUE', seed: '73', seriesGameNumber: 1, draftRules: { identity: 'PROFESSIONAL_DRAFT', hash: H },
  draftScoringPolicy: policy('SCORING'), autoDraftSelectionPolicy: policy('AUTO'), playerControlPolicy: policy('PLAYER'),
  currentTurn: { turn: 1, teamSide: 'BLUE', actionType: 'BAN' }, state: { blueBans: [], redBans: [], bluePicks: [], redPicks: [], hardFearlessExclusions: [] },
  decisions: [], selectableChampions: ['champion-a', 'champion-b', 'champion-c'].map((id) => ({ champion: champion(id), feasibleRoles: [] })),
  unavailableChampions: [{ champion: champion('champion-x'), reason: 'ALREADY_BANNED' }],
  advisoryRecommendations: ['champion-a', 'champion-b', 'champion-c'].map((id, index) => ({ champion: champion(id), advisoryRank: index + 1, immediateScore: 3 - index, continuationScore: 2 - index, finalSearchScore: 5 - index, advisoryOnly: true })),
  selectableSetIdentity: H, stateHash: H, completedDraft: null,
};
const completedSession = {
  ...clone(baseSession), revision: 10, status: 'COMPLETED', currentTurn: null, state, decisions,
  selectableChampions: [], unavailableChampions: [], advisoryRecommendations: [], selectableSetIdentity: null,
  completedDraft: { draftIdentity: H, controlEvidenceSchema: 'PLAYER_DRAFT_CONTROL_EVIDENCE_V1', controlEvidenceHash: C, controlEvidenceHashAlgorithm: 'SHA-256', finalAssignments: assignments },
};
const ratingKeys = {
  lane: ['COMBAT_EXECUTION', 'CONSISTENCY', 'DECISION_MAKING', 'FARMING', 'LANE_PRESSURE', 'MAP_AWARENESS', 'MECHANICS', 'POSITIONING', 'PRIORITY_CONVERSION', 'SIDE_LANE', 'TRADING', 'WAVE_MANAGEMENT'],
  jungle: ['COMBAT_EXECUTION', 'CONSISTENCY', 'DECISION_MAKING', 'ENEMY_JUNGLE_TRACKING', 'JUNGLE_RESOURCE_MANAGEMENT', 'LANE_INTERVENTION', 'MAP_AWARENESS', 'MECHANICS', 'OBJECTIVE_DECISION', 'OBJECTIVE_SECURE', 'PATHING', 'POSITIONING'],
  support: ['ALLY_PROTECTION', 'AREA_SETUP', 'COMBAT_EXECUTION', 'CONSISTENCY', 'DECISION_MAKING', 'ENGAGE_EXECUTION', 'LANE_SUPPORT', 'MAP_AWARENESS', 'MECHANICS', 'POSITIONING', 'ROTATION_PLANNING', 'VISION_CONTROL'],
};
const ratings = (position) => position === 'JUNGLE' ? ratingKeys.jungle : position === 'SUPPORT' ? ratingKeys.support : ratingKeys.lane;
const ability = (position) => ({ schemaVersion: 'PLAYER_ABILITY_PROFILE_V1', baseRatings: Object.fromEntries(ratings(position).map((key) => [key, 10])), realizedRatings: Object.fromEntries(ratings(position).map((key) => [key, 10])), realizationDeltas: Object.fromEntries(ratings(position).map((key) => [key, 0])), selectedChampionProficiency: 10, proficiencyExecutionAdjustment: 0 });
const lineup = (teamSide) => assignments.filter((item) => item.teamSide === teamSide).map((item) => ({ playerId: item.playerId, nickname: item.playerId, position: item.position, championId: item.championId, champion: champion(item.championId) }));
const resultPlayers = assignments.map((item) => ({ ...item, kills: 0, deaths: 0, assists: 0, cs: 0, gold: 500, totalExperience: 0, level: 1, abilityProfile: ability(item.position) }));
const teamResult = (teamSide, teamIdentity) => ({ teamIdentity, teamSide, kills: 0, totalGold: 2500, dragons: 0, hasDragonSoul: false, hasBaronBuff: false, hasElderBuff: false, towersDestroyed: 0, inhibitorsRemaining: 3, nexusTurretsRemaining: 2, nexusAlive: true, alivePlayers: 5 });
const playerState = (item) => ({ playerId: item.playerId, teamSide: item.teamSide, position: item.position, championId: item.championId, kills: 0, deaths: 0, assists: 0, cs: 0, gold: 500, alive: true, respawnAtSeconds: 0, respawnRemainingSeconds: 0, canFarm: true, farmResumeAtSeconds: 0, farmReturnSecondsRemaining: 0, shutdownBountyGold: 0, bountyProgress: 0, activityType: null, activityOriginLane: null, activityTargetLane: null, activityUntilSeconds: -1, totalExperience: 0, level: 1, itemProgressStage: 'START', structuredProgression: {} });
const snapshot = (timeSeconds) => ({ timeSeconds, blueTeam: { ...teamResult('BLUE', 'GEN'), gold: 2500, elderBuffRemainingSeconds: 0 }, redTeam: { ...teamResult('RED', 'T1'), gold: 2500, elderBuffRemainingSeconds: 0 }, players: assignments.map(playerState), structuredState: {} });
const productionPolicy = {
  policyId: 'PRODUCTION', policyHash: H, activationDecisionSchema: 'V1', activationDecisionCode: 'ACTIVE', acceptanceStatus: 'ACCEPTED', knownDiagnosticLimitation: 'fixture', knownDiagnosticLimitations: ['fixture'], statisticalHoldoutApproved: false, rollbackProfileId: 'ROLLBACK', rollbackMode: 'MANUAL', automaticFallback: false, draftSelectionPolicyId: 'AUTO', draftSelectionPolicyHash: H, runtimeProfileId: 'PRODUCTION_MATCHUP_COMPOSITION_V1', configurationHash: H, activeGameplayRulesVersion: 'V1', engineImplementationVersion: 'MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9', matchupMode: 'ACTIVE', compositionMode: 'ACTIVE', jungleClearContribution: 'DISABLED', economyCandidateActivation: false, tempoCandidateActivation: false, diagnosticsExcludedFromGameplayIdentity: true,
};
const simulatedSession = { ...clone(completedSession), status: 'SIMULATED' };
const cancelledSession = {
  ...clone(baseSession), status: 'CANCELLED', currentTurn: null,
  selectableChampions: [], unavailableChampions: [], advisoryRecommendations: [], selectableSetIdentity: null,
};
const simulation = {
  schemaVersion: 'PLAYER_DRAFT_MATCH_RESPONSE_V1', session: simulatedSession,
  match: {
    schemaVersion: 'PLAYER_DRAFT_MATCH_PAYLOAD_V1', matchIdentity: 'match-73', seed: '73', productionPolicy,
    teams: [{ teamSide: 'BLUE', teamCode: 'GEN', displayName: 'Gen.G', lineup: lineup('BLUE') }, { teamSide: 'RED', teamCode: 'T1', displayName: 'T1', lineup: lineup('RED') }],
    draft: { draftIdentity: H, finalDraftHash: H, finalAssignmentHash: R, autoDraftSelectionPolicy: policy('AUTO'), playerControlPolicy: policy('PLAYER'), autoSelectionTraceHash: H, controlEvidenceHash: C, decisions },
    result: { schemaVersion: 'MATCH_RESULT_SUMMARY_V1', winner: null, endReason: 'SIMULATION_TIMEOUT', durationSeconds: 1, teams: [teamResult('BLUE', 'GEN'), teamResult('RED', 'T1')], players: resultPlayers, finalDraftHash: H, finalAssignmentHash: R, runtimeProfileId: 'PRODUCTION_MATCHUP_COMPOSITION_V1', configurationHash: H, resourceProvenanceHash: H, replayProvenanceHash: H },
    timeline: { schemaVersion: 'MATCH_ENGINE_TIMELINE_V1', durationSeconds: 1, winner: null, endReason: 'SIMULATION_TIMEOUT', events: [{ timeSeconds: 0, eventType: 'GAME_START', actorSide: null, actorPosition: null, lane: null, actorPlayerId: null, killerPlayerId: null, victimPlayerId: null, assistantPlayerIds: [], killerChampionId: null, victimChampionId: null, assistantChampionIds: [], combatSource: null, structureActionSource: null, structureKind: null, structureTowerTier: null, structureAttackingSide: null, structureDefendingSide: null, goldAmount: 0, bountyRawBeforePayout: 0, actionId: null, parentActionId: null, displayMessage: '경기 시작', structuredData: {} }], snapshots: [snapshot(0), snapshot(1)] },
    integrity: { runtimeProfileId: 'PRODUCTION_MATCHUP_COMPOSITION_V1', configurationHash: H, engineImplementationVersion: 'MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9', activeGameplayRulesVersion: 'V1', controlPolicyId: 'PLAYER', controlPolicyHash: H, controlEvidenceHash: C, inputHash: H, replayProvenanceHash: H, resourceProvenanceHash: H, simulatorTimelineHash: H, structuredTimelineHash: H, outputHash: H, randomFingerprint: { schemaVersion: 'SIMULATION_RANDOM_FINGERPRINT_V1', randomDrawCount: 1, randomTraceHash: H, randomTraceHashAlgorithm: 'SHA-256' }, diagnosticsExcludedFromGameplayIdentity: true },
  },
};

function accepts(label, fn) { try { fn(); console.log(`PASS ${label}`); } catch (error) { console.error(`FAIL ${label}`, error); process.exitCode = 1; } }
function rejects(label, mutate, validator = (value) => validatePlayerDraftSessionPayload(value, expectation)) {
  try { validator(mutate()); console.error(`FAIL ${label}: accepted invalid payload`); process.exitCode = 1; }
  catch { console.log(`PASS ${label}`); }
}

accepts('valid ACTIVE session', () => validatePlayerDraftSessionPayload(baseSession, expectation));
accepts('valid COMPLETED session', () => validatePlayerDraftSessionPayload(completedSession, expectation));
accepts('valid CANCELLED terminal session', () => validatePlayerDraftSessionPayload(cancelledSession, expectation));
accepts('valid SIMULATION response', () => validatePlayerDraftSimulationPayload(simulation, expectation));
rejects('wrong schema rejection', () => ({ ...clone(baseSession), schemaVersion: 'WRONG' }));
rejects('invalid status rejection', () => ({ ...clone(baseSession), status: 'PAUSED' }));
rejects('current turn side coherence rejection', () => { const value = clone(baseSession); value.currentTurn.teamSide = 'RED'; return value; });
rejects('PLAYER/AI evidence mismatch rejection', () => { const value = clone(completedSession); value.decisions[0].authority = 'AI'; return value; });
rejects('duplicate champion state rejection', () => { const value = clone(completedSession); value.decisions[1].championId = value.decisions[0].championId; return value; });
rejects('recommendation selectable mismatch rejection', () => { const value = clone(baseSession); value.advisoryRecommendations[0].champion = champion('not-selectable'); return value; });
rejects('final assignment mismatch rejection', () => { const value = clone(completedSession); value.completedDraft.finalAssignments[0].championId = 'wrong'; return value; });
rejects('control/integrity hash mismatch rejection', () => { const value = clone(simulation); value.match.integrity.controlEvidenceHash = H; return value; }, (value) => validatePlayerDraftSimulationPayload(value, expectation));
rejects('CANCELLED current turn rejection', () => ({ ...clone(cancelledSession), currentTurn: clone(baseSession.currentTurn) }));
rejects('CANCELLED selectable projection rejection', () => ({ ...clone(cancelledSession), selectableChampions: clone(baseSession.selectableChampions) }));
rejects('unknown event actor rejection', () => {
  const value = clone(simulation); const event = value.match.timeline.events[0];
  event.actorPlayerId = 'player-unknown'; event.actorSide = 'BLUE'; event.actorPosition = 'TOP'; return value;
}, (value) => validatePlayerDraftSimulationPayload(value, expectation));
rejects('killer player/champion mismatch rejection', () => {
  const value = clone(simulation); const event = value.match.timeline.events[0]; const player = value.match.teams[0].lineup[0];
  event.killerPlayerId = player.playerId; event.killerChampionId = value.match.teams[0].lineup[1].championId; return value;
}, (value) => validatePlayerDraftSimulationPayload(value, expectation));
rejects('victim player/champion mismatch rejection', () => {
  const value = clone(simulation); const event = value.match.timeline.events[0]; const player = value.match.teams[1].lineup[0];
  event.victimPlayerId = player.playerId; event.victimChampionId = value.match.teams[1].lineup[1].championId; return value;
}, (value) => validatePlayerDraftSimulationPayload(value, expectation));
rejects('assistant pair length mismatch rejection', () => {
  const value = clone(simulation); value.match.timeline.events[0].assistantPlayerIds = [value.match.teams[0].lineup[0].playerId]; return value;
}, (value) => validatePlayerDraftSimulationPayload(value, expectation));
rejects('duplicate assistant rejection', () => {
  const value = clone(simulation); const player = value.match.teams[0].lineup[0];
  value.match.timeline.events[0].assistantPlayerIds = [player.playerId, player.playerId];
  value.match.timeline.events[0].assistantChampionIds = [player.championId, player.championId]; return value;
}, (value) => validatePlayerDraftSimulationPayload(value, expectation));
rejects('final team kills mismatch rejection', () => {
  const value = clone(simulation); value.match.result.teams[0].kills += 1; return value;
}, (value) => validatePlayerDraftSimulationPayload(value, expectation));
rejects('final team gold mismatch rejection', () => {
  const value = clone(simulation); value.match.result.teams[0].totalGold += 1; return value;
}, (value) => validatePlayerDraftSimulationPayload(value, expectation));
rejects('final player KDA mismatch rejection', () => {
  const value = clone(simulation); value.match.result.players[0].kills += 1; return value;
}, (value) => validatePlayerDraftSimulationPayload(value, expectation));
rejects('final player CS mismatch rejection', () => {
  const value = clone(simulation); value.match.result.players[0].cs += 1; return value;
}, (value) => validatePlayerDraftSimulationPayload(value, expectation));
rejects('final player gold mismatch rejection', () => {
  const value = clone(simulation); value.match.result.players[0].gold += 1; return value;
}, (value) => validatePlayerDraftSimulationPayload(value, expectation));
rejects('final player XP mismatch rejection', () => {
  const value = clone(simulation); value.match.result.players[0].totalExperience += 1; return value;
}, (value) => validatePlayerDraftSimulationPayload(value, expectation));
rejects('final player level mismatch rejection', () => {
  const value = clone(simulation); value.match.result.players[0].level += 1; return value;
}, (value) => validatePlayerDraftSimulationPayload(value, expectation));
rejects('completed assignment/presentation mismatch rejection', () => {
  const value = clone(simulation); const player = value.match.teams[0].lineup[0];
  player.championId = value.match.teams[0].lineup[1].championId; player.champion = champion(player.championId); return value;
}, (value) => validatePlayerDraftSimulationPayload(value, expectation));
accepts('valid error DTO', () => validatePlayerDraftApiErrorPayload({ schemaVersion: 'PLAYER_DRAFT_API_ERROR_V1', code: 'STALE_DRAFT_REVISION', field: 'expectedRevision', message: 'stale' }));
rejects('HTML error rejection', () => '<html>error</html>', validatePlayerDraftApiErrorPayload);
accepts('unavailable reason Korean mapping coverage', () => {
  const expected = ['HARD_FEARLESS_EXCLUDED', 'ALREADY_BANNED', 'ALREADY_PICKED', 'PARTIAL_ROLE_ASSIGNMENT_INFEASIBLE', 'FUTURE_ROLE_COMPLETION_INFEASIBLE', 'BAN_WOULD_BREAK_FUTURE_COMPLETION'];
  if (expected.some((reason) => !playerDraftUnavailableReasonLabels[reason])) throw new Error('mapping missing');
});
accepts('BAN role filter uses stable catalog roles and keeps banned champions visible', () => {
  const bannedTop = {
    champion: champion('banned-top'), roles: ['TOP'], feasibleRoles: [], unavailableReason: 'ALREADY_BANNED',
  };
  const selectableTop = {
    champion: champion('selectable-top'), roles: ['TOP'], feasibleRoles: [], unavailableReason: null,
  };
  if (!playerDraftEntryMatchesRole(bannedTop, 'TOP')) throw new Error('banned TOP disappeared from TOP filter');
  if (!playerDraftEntryMatchesRole(selectableTop, 'TOP')) throw new Error('BAN option with empty feasibleRoles disappeared from TOP filter');
  if (playerDraftEntryMatchesRole(selectableTop, 'MID')) throw new Error('TOP champion leaked into MID filter');
});
accepts('session ordering rejects stale revision and terminal downgrade', () => {
  const revisionOne = { ...clone(baseSession), revision: 1 };
  if (shouldApplyPlayerDraftSession(revisionOne, baseSession)) throw new Error('stale revision accepted');
  if (!shouldApplyPlayerDraftSession(completedSession, simulatedSession)) throw new Error('SIMULATED transition rejected');
  if (shouldApplyPlayerDraftSession(simulatedSession, completedSession)) throw new Error('terminal downgrade accepted');
});
accepts('response-loss reconciliation finds the original clientActionId', () => {
  if (!playerDraftActionWasApplied(decisions, 'action-1')) throw new Error('accepted action not found');
  if (playerDraftActionWasApplied(decisions, 'action-missing')) throw new Error('unknown action was treated as accepted');
});
if (!process.exitCode) console.log('PLAYER_DRAFT_FRONTEND_CONTRACT_VERIFICATION_PASSED');
