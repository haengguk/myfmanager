package com.lolfm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionId;
import com.lolfm.domain.Team;
import com.lolfm.draft.DraftSelectionContext;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.draft.PlayerControlledDraftLatencyPhaseProbe;
import com.lolfm.dto.PlayerDraftApiV1Dtos;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.TeamSide;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

/** Test-side decomposition of the standalone Player Draft action/simulate path. */
public final class PlayerDraftLatencyProfilingV1Harness {
    private final ObjectMapper mapper;
    private final LckTeamAssembler teams;
    private final PlayerControlledDraftEngine drafts;
    private final PlayerDraftApiV1Service service;
    private final PlayerDraftSessionRepository sessions;
    private final PlayerControlledDraftMatchInputBoundary inputs;
    private final MatchEngineV1 matches;
    private final PlayerDraftMatchSimulationExecutor simulations;
    private final PlayerDraftApiV1ResponseMapper responses;
    private final MatchEngineV1Canonicalizer canonicalizer;
    private final PlayerControlledDraftLatencyPhaseProbe draftProbe;
    private final Method validateOutput;
    private final LongSupplier clock;

    public PlayerDraftLatencyProfilingV1Harness(
            ObjectMapper mapper,
            LckTeamAssembler teams,
            PlayerControlledDraftEngine drafts,
            PlayerDraftApiV1Service service,
            PlayerDraftSessionRepository sessions,
            PlayerControlledDraftMatchInputBoundary inputs,
            MatchEngineV1 matches,
            PlayerDraftMatchSimulationExecutor simulations,
            PlayerDraftApiV1ResponseMapper responses,
            MatchEngineV1Canonicalizer canonicalizer
    ) {
        this(mapper, teams, drafts, service, sessions, inputs, matches, simulations,
                responses, canonicalizer, System::nanoTime);
    }

    PlayerDraftLatencyProfilingV1Harness(
            ObjectMapper mapper,
            LckTeamAssembler teams,
            PlayerControlledDraftEngine drafts,
            PlayerDraftApiV1Service service,
            PlayerDraftSessionRepository sessions,
            PlayerControlledDraftMatchInputBoundary inputs,
            MatchEngineV1 matches,
            PlayerDraftMatchSimulationExecutor simulations,
            PlayerDraftApiV1ResponseMapper responses,
            MatchEngineV1Canonicalizer canonicalizer,
            LongSupplier clock
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.drafts = Objects.requireNonNull(drafts, "drafts");
        this.service = Objects.requireNonNull(service, "service");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.matches = Objects.requireNonNull(matches, "matches");
        this.simulations = Objects.requireNonNull(simulations, "simulations");
        this.responses = Objects.requireNonNull(responses, "responses");
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
        this.clock = Objects.requireNonNull(clock, "clock");
        draftProbe = new PlayerControlledDraftLatencyPhaseProbe(drafts);
        validateOutput = method(PlayerDraftMatchSimulationExecutor.class,
                "validateOutput", PlayerDraftSession.class, MatchEngineV1Output.class);
    }

    public FlowObservation run(
            String runId,
            String blueTeamCode,
            String redTeamCode,
            long seed,
            TeamSide controlledSide,
            List<String> requestedChampionScript,
            boolean captureTimings,
            RunKind runKind
    ) {
        PlayerDraftSessionView started = service.start(new PlayerDraftApiV1Dtos.StartRequest(
                PlayerDraftApiV1Dtos.START_REQUEST_SCHEMA, blueTeamCode, redTeamCode,
                controlledSide, Long.toString(seed)));
        Team blue = teams.assemble(blueTeamCode);
        Team red = teams.assemble(redTeamCode);
        DraftTeamContext blueContext = DraftTeamContext.from(blue);
        DraftTeamContext redContext = DraftTeamContext.from(red);
        DraftSelectionContext selectionContext = RealDraftSelectionContextFactory.create(
                seed, blueTeamCode, blue, redTeamCode, red, 1, Set.of());

        ArrayList<ActionObservation> actions = new ArrayList<>();
        ArrayList<AiTurnObservation> aiTurns = new ArrayList<>();
        ArrayList<String> actualScript = new ArrayList<>();
        PlayerDraftSessionView current = started;
        for (int playerActionIndex = 0; !current.progress().complete();
                playerActionIndex++) {
            PlayerDraftApiV1Dtos.SessionResponse beforeResponse = responses.session(current);
            String championId = playerActionIndex < requestedChampionScript.size()
                    ? requestedChampionScript.get(playerActionIndex)
                    : beforeResponse.selectableChampions().getFirst().champion().championId();
            if (beforeResponse.selectableChampions().stream().noneMatch(value ->
                    value.champion().championId().equals(championId))) {
                throw new IllegalStateException("Action script champion is not selectable: "
                        + championId + " at revision " + current.revision());
            }
            actualScript.add(championId);
            String clientActionId = runId + "-action-" + (playerActionIndex + 1);
            var phase = draftProbe.select(current.progress(), blueContext, redContext,
                    selectionContext, new ChampionId(championId), clientActionId,
                    captureTimings);
            long decisionCountBefore = current.progress().turnEvidence().size();

            PlayerDraftApiV1Dtos.ActionRequest request =
                    new PlayerDraftApiV1Dtos.ActionRequest(
                            PlayerDraftApiV1Dtos.ACTION_REQUEST_SCHEMA,
                            current.revision(), clientActionId, championId);
            long serviceStart = tick(captureTimings);
            PlayerDraftSessionView next = service.action(current.sessionId(), request);
            long serviceNanos = elapsed(serviceStart, captureTimings);
            if (!sameProgress(next.progress(), phase.progress())) {
                throw new IllegalStateException("Phase probe changed Player Draft semantics at "
                        + current.progress().state().currentTurn().number());
            }

            long replayStart = tick(captureTimings);
            PlayerDraftSessionView replay = service.action(current.sessionId(), request);
            long exactReplayNanos = elapsed(replayStart, captureTimings);
            if (!replay.equals(next)) {
                throw new IllegalStateException("Exact action replay drifted");
            }

            long mappingStart = tick(captureTimings);
            PlayerDraftApiV1Dtos.SessionResponse response = responses.session(next);
            long responseProjectionNanos = elapsed(mappingStart, captureTimings);
            long serializationStart = tick(captureTimings);
            byte[] json = write(response);
            long serializationNanos = elapsed(serializationStart, captureTimings);
            long gzipStart = tick(captureTimings);
            byte[] gzip = RealMatchPerformanceBaselineV1Harness.gzip(json);
            long gzipNanos = elapsed(gzipStart, captureTimings);
            int aiDecisionCount = phase.aiTurns().size();
            long expectedDecisionDelta = 1L + aiDecisionCount;
            if (next.progress().turnEvidence().size() - decisionCountBefore
                    != expectedDecisionDelta) {
                throw new IllegalStateException("AI decision cardinality mismatch");
            }
            actions.add(new ActionObservation(
                    runId, runKind, controlledSide, playerActionIndex + 1,
                    current.progress().state().currentTurn().number(),
                    current.progress().state().currentTurn().actionType().name(), championId,
                    decisionCountBefore, next.progress().turnEvidence().size(),
                    aiDecisionCount, phase.playerLegalityViewNanos(),
                    phase.playerApplyEvidenceNanos(), phase.aiFollowUpTotalNanos(),
                    phase.completionNanos(), serviceNanos, exactReplayNanos,
                    responseProjectionNanos, serializationNanos, gzipNanos,
                    json.length, gzip.length, response.status().name(),
                    response.stateHash(), response.completedDraft() == null
                    ? null : response.completedDraft().draftIdentity()));
            for (int index = 0; index < phase.aiTurns().size(); index++) {
                var ai = phase.aiTurns().get(index);
                aiTurns.add(new AiTurnObservation(
                        runId, runKind, controlledSide, playerActionIndex + 1,
                        index + 1, ai.turn(), ai.side(), ai.actionType().name(),
                        ai.championId().value(), ai.candidateEvaluationCount(),
                        ai.elapsedNanos(), ai.plannerCandidatePhysicalComputations(),
                        ai.roleAssignmentPhysicalComputations(),
                        ai.completionPhysicalComputations(),
                        ai.poolHealthPhysicalComputations(),
                        ai.rolePositionPhysicalComputations(), ai.peakCacheEntries()));
            }
            current = next;
        }
        if (actualScript.size() != 10 || !current.progress().complete()) {
            throw new IllegalStateException("Player Draft flow did not complete in 10 actions");
        }
        SimulationObservation simulation = simulate(runId, runKind, current,
                captureTimings);
        return new FlowObservation(runId, runKind, controlledSide,
                List.copyOf(actualScript), current.progress(), actions, aiTurns, simulation);
    }

    private SimulationObservation simulate(
            String runId,
            RunKind runKind,
            PlayerDraftSessionView completedView,
            boolean captureTimings
    ) {
        PlayerDraftSession completed = sessions.get(completedView.sessionId());
        long lookupStart = tick(captureTimings);
        PlayerDraftSession lookedUp = sessions.get(completedView.sessionId());
        if (!lookedUp.progress().complete()
                || lookedUp.status() != PlayerDraftSessionStatus.COMPLETED) {
            throw new IllegalStateException("Completed session validation failed");
        }
        long lookupAndCompletedValidationNanos = elapsed(lookupStart, captureTimings);

        long inputStart = tick(captureTimings);
        MatchEngineV1Input input = inputs.validateAndCreateTrustedStandaloneInput(
                completed.completionBinding(), completed.sessionId(), completed.revision(),
                completed.blueTeamCode(), completed.redTeamCode(), completed.controlledSide(),
                completed.matchSeed(), completed.progress().result());
        long inputValidationNanos = elapsed(inputStart, captureTimings);

        long engineStart = tick(captureTimings);
        MatchEngineV1Output output = matches.execute(
                input, SimulationInstrumentation.enabled());
        long matchEngineNanos = elapsed(engineStart, captureTimings);

        long integrityStart = tick(captureTimings);
        invoke(validateOutput, simulations, completed, output);
        long outputIntegrityNanos = elapsed(integrityStart, captureTimings);

        long receiptStart = tick(captureTimings);
        SimulationReceipt receipt = SimulationReceipt.from(output);
        long receiptNanos = elapsed(receiptStart, captureTimings);

        long projectionStart = tick(captureTimings);
        PlayerDraftApiV1Dtos.SimulationResponse projected = responses.simulation(
                completedView, output);
        long responseProjectionNanos = elapsed(projectionStart, captureTimings);
        long serializationStart = tick(captureTimings);
        byte[] json = write(projected);
        long serializationNanos = elapsed(serializationStart, captureTimings);
        long gzipStart = tick(captureTimings);
        byte[] gzip = RealMatchPerformanceBaselineV1Harness.gzip(json);
        long gzipNanos = elapsed(gzipStart, captureTimings);

        PlayerDraftApiV1Dtos.SimulateRequest request =
                new PlayerDraftApiV1Dtos.SimulateRequest(
                        PlayerDraftApiV1Dtos.SIMULATE_REQUEST_SCHEMA);
        long serviceStart = tick(captureTimings);
        PlayerDraftApiV1Service.SimulationExecution first = service.simulate(
                completedView.sessionId(), request);
        long serviceTotalNanos = elapsed(serviceStart, captureTimings);
        assertSameOutput(output, first.output());

        long retryStart = tick(captureTimings);
        PlayerDraftApiV1Service.SimulationExecution retry = service.simulate(
                completedView.sessionId(), request);
        long exactRetryNanos = elapsed(retryStart, captureTimings);
        assertSameOutput(first.output(), retry.output());
        if (!receipt.equals(sessions.get(completedView.sessionId()).simulationReceipt())) {
            throw new IllegalStateException("Stored simulation receipt drifted");
        }
        return new SimulationObservation(
                runId, runKind, completedView.controlledSide(),
                lookupAndCompletedValidationNanos, inputValidationNanos,
                matchEngineNanos, outputIntegrityNanos, receiptNanos,
                responseProjectionNanos, serializationNanos, gzipNanos,
                serviceTotalNanos, exactRetryNanos, json.length, gzip.length,
                output.timeline().events().size(), output.timeline().snapshots().size(),
                receipt.canonicalBytes().length, output.resultSummary().winner(),
                output.resultSummary().durationSeconds(), output.inputHash(),
                output.executionProvenance().replayProvenanceHash(),
                output.simulatorTimelineHash(), output.structuredTimelineHash(),
                output.outputHash(), output.executionProvenance().randomFingerprint()
                .randomDrawCount(), output.executionProvenance().randomFingerprint()
                .randomTraceHash(), canonicalizer.hash(projected));
    }

    private void assertSameOutput(MatchEngineV1Output expected, MatchEngineV1Output actual) {
        if (!canonicalizer.canonicalJson(expected).equals(canonicalizer.canonicalJson(actual))) {
            throw new IllegalStateException("Profiling changed Match Engine output");
        }
    }

    private static boolean sameProgress(
            PlayerControlledDraftEngine.Progress actual,
            PlayerControlledDraftEngine.Progress projected
    ) {
        if (actual.controlledSide() != projected.controlledSide()
                || !actual.state().equals(projected.state())
                || !actual.turnEvidence().equals(projected.turnEvidence())) {
            return false;
        }
        if (actual.result() == null || projected.result() == null) {
            return actual.result() == projected.result();
        }
        return actual.result().ruleSet().equals(projected.result().ruleSet())
                && actual.result().controlledSide() == projected.result().controlledSide()
                && actual.result().blueBans().equals(projected.result().blueBans())
                && actual.result().redBans().equals(projected.result().redBans())
                && actual.result().bluePicks().equals(projected.result().bluePicks())
                && actual.result().redPicks().equals(projected.result().redPicks())
                && actual.result().turnEvidence().equals(projected.result().turnEvidence())
                && actual.result().blueFinalRoleAssignments()
                        .equals(projected.result().blueFinalRoleAssignments())
                && actual.result().redFinalRoleAssignments()
                        .equals(projected.result().redFinalRoleAssignments())
                && actual.result().matchChampionAssignments().asMap()
                        .equals(projected.result().matchChampionAssignments().asMap())
                && actual.result().matchChampionAssignments().selectionMode()
                        == projected.result().matchChampionAssignments().selectionMode()
                && actual.result().hardFearlessExclusions()
                        .equals(projected.result().hardFearlessExclusions())
                && actual.result().draftMetaVersion()
                        .equals(projected.result().draftMetaVersion())
                && actual.result().requiredLegalRoleKeyHash()
                        .equals(projected.result().requiredLegalRoleKeyHash())
                && actual.result().actualLegalRoleKeyHash()
                        .equals(projected.result().actualLegalRoleKeyHash());
    }

    private byte[] write(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (Exception error) {
            throw new IllegalStateException("Player Draft profiling serialization failed", error);
        }
    }

    private long tick(boolean capture) {
        return capture ? clock.getAsLong() : 0L;
    }

    private long elapsed(long start, boolean capture) {
        if (!capture) return 0L;
        long value = clock.getAsLong() - start;
        if (value < 0L) throw new IllegalStateException("PROFILING_CLOCK_MOVED_BACKWARDS");
        return value;
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        try {
            Method value = owner.getDeclaredMethod(name, parameters);
            value.setAccessible(true);
            return value;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Missing profiling boundary " + name, error);
        }
    }

    private static Object invoke(Method method, Object owner, Object... arguments) {
        try {
            return method.invoke(owner, arguments);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(error.getCause());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    public enum RunKind { COLD, WARM, PARITY_ON, PARITY_OFF }

    public record ActionObservation(
            String runId, RunKind runKind, TeamSide controlledSide,
            int playerActionIndex, int playerTurn, String actionType, String championId,
            long decisionCountBefore, long decisionCountAfter, int aiDecisionCount,
            long playerLegalityViewNanos, long playerApplyEvidenceNanos,
            long aiFollowUpTotalNanos, long completionNanos, long backendServiceTotalNanos,
            long exactReplayRepositoryBoundaryNanos, long responseProjectionNanos,
            long jsonSerializationNanos, long offlineGzipNanos,
            long decodedJsonBytes, long offlineGzipBytes,
            String resultingStatus, String stateHash, String completedDraftIdentity
    ) { }

    public record AiTurnObservation(
            String runId, RunKind runKind, TeamSide controlledSide,
            int playerActionIndex, int aiDecisionIndex, int aiTurn, TeamSide aiSide,
            String actionType, String championId, int candidateEvaluationCount,
            long elapsedNanos, long plannerCandidatePhysicalComputations,
            long roleAssignmentPhysicalComputations,
            long completionPhysicalComputations, long poolHealthPhysicalComputations,
            long rolePositionPhysicalComputations, int peakCacheEntries
    ) { }

    public record SimulationObservation(
            String runId, RunKind runKind, TeamSide controlledSide,
            long lookupAndCompletedValidationNanos, long inputValidationNanos,
            long matchEngineNanos, long outputIntegrityNanos, long receiptNanos,
            long responseProjectionNanos, long jsonSerializationNanos,
            long offlineGzipNanos, long serviceTotalNanos, long exactRetryNanos,
            long decodedJsonBytes, long offlineGzipBytes, int eventCount,
            int snapshotCount, int receiptBytes, TeamSide winner, int durationSeconds,
            String inputHash, String replayProvenanceHash, String simulatorTimelineHash,
            String structuredTimelineHash, String outputHash, long randomDrawCount,
            String randomTraceHash, String responseCanonicalHash
    ) { }

    public record FlowObservation(
            String runId, RunKind runKind, TeamSide controlledSide,
            List<String> actionScript,
            PlayerControlledDraftEngine.Progress finalProgress,
            List<ActionObservation> actions,
            List<AiTurnObservation> aiTurns,
            SimulationObservation simulation
    ) {
        public FlowObservation {
            actionScript = List.copyOf(actionScript);
            actions = List.copyOf(actions);
            aiTurns = List.copyOf(aiTurns);
        }
    }
}
