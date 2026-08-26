package com.lolfm.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionId;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.draft.DraftEngine;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.FinalDraftResult;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.dto.RealMatchApiV1Dtos;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.TeamSide;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.zip.GZIPOutputStream;

/**
 * Test-side, observational decomposition of the production Real Match V1 call path.
 * No timing value is passed into gameplay or included in any gameplay identity.
 */
public final class RealMatchPerformanceBaselineV1Harness {
    private final ObjectMapper mapper;
    private final com.lolfm.controller.RealMatchApiV1RequestParser requests;
    private final RealMatchApiV1Service service;
    private final LckTeamAssembler teams;
    private final DraftEngine drafts;
    private final RealDraftMatchPreflightValidator preflight;
    private final MatchEngineV1InputFactory inputs;
    private final MatchEngineV1 engine;
    private final RealMatchApiV1ResponseMapper responses;
    private final MatchEngineV1Canonicalizer canonicalizer;
    private final Method validateRequest;
    private final Method validateOutput;
    private final LongSupplier clock;

    public RealMatchPerformanceBaselineV1Harness(
            ObjectMapper mapper,
            com.lolfm.controller.RealMatchApiV1RequestParser requests,
            RealMatchApiV1Service service,
            RealDraftMatchOrchestrator orchestrator,
            LckTeamAssembler teams,
            RealDraftMatchPreflightValidator preflight,
            MatchEngineV1InputFactory inputs,
            MatchEngineV1 engine,
            RealMatchApiV1ResponseMapper responses,
            MatchEngineV1Canonicalizer canonicalizer
    ) {
        this(mapper, requests, service, orchestrator, teams, preflight, inputs, engine,
                responses, canonicalizer, System::nanoTime);
    }

    RealMatchPerformanceBaselineV1Harness(
            ObjectMapper mapper,
            com.lolfm.controller.RealMatchApiV1RequestParser requests,
            RealMatchApiV1Service service,
            RealDraftMatchOrchestrator orchestrator,
            LckTeamAssembler teams,
            RealDraftMatchPreflightValidator preflight,
            MatchEngineV1InputFactory inputs,
            MatchEngineV1 engine,
            RealMatchApiV1ResponseMapper responses,
            MatchEngineV1Canonicalizer canonicalizer,
            LongSupplier clock
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.requests = Objects.requireNonNull(requests, "requests");
        this.service = Objects.requireNonNull(service, "service");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.preflight = Objects.requireNonNull(preflight, "preflight");
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.responses = Objects.requireNonNull(responses, "responses");
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
        this.clock = Objects.requireNonNull(clock, "clock");
        drafts = field(orchestrator, "drafts", DraftEngine.class);
        validateRequest = method(RealMatchApiV1Service.class, "validateRequest",
                RealMatchApiV1Dtos.SimulateRequest.class);
        validateOutput = method(RealMatchApiV1Service.class, "validateOutput",
                RealMatchApiV1Dtos.SimulateRequest.class, long.class,
                MatchEngineV1Output.class);
    }

    public Execution simulate(JsonNode requestBody, boolean captureTimings) {
        long boundaryStart = tick(captureTimings);

        long requestStart = tick(captureTimings);
        RealMatchApiV1Dtos.SimulateRequest request = requests.parse(requestBody);
        long matchSeed = invokeLong(validateRequest, service, request);
        long requestNanos = elapsed(requestStart, captureTimings);

        SeriesDraftHistory history = new SeriesDraftHistory();
        long preparationStart = tick(captureTimings);
        String blueTeamCode = request.blueTeamCode();
        String redTeamCode = request.redTeamCode();
        Team blueTeam = teams.assemble(blueTeamCode);
        Team redTeam = teams.assemble(redTeamCode);
        DraftTeamContext blueContext = DraftTeamContext.from(blueTeam);
        DraftTeamContext redContext = DraftTeamContext.from(redTeam);
        Set<ChampionId> exclusionsBeforeDraft = history.consumedPicks();
        int gameNumber = history.committedGameCount() + 1;
        FinalDraftResult draftResult = drafts.draft(blueContext, redContext, history,
                RealDraftSelectionContextFactory.create(matchSeed, blueTeamCode, blueTeam,
                        redTeamCode, redTeam, gameNumber, exclusionsBeforeDraft));
        long preparationNanos = elapsed(preparationStart, captureTimings);

        long preflightStart = tick(captureTimings);
        preflight.validate(blueTeamCode, blueTeam, redTeamCode, redTeam,
                blueContext, redContext, draftResult, history);
        long preflightNanos = elapsed(preflightStart, captureTimings);

        preparationStart = tick(captureTimings);
        MatchEngineV1Input input = inputs.fromRealDraft(
                blueTeamCode, blueTeam, redTeamCode, redTeam, matchSeed, gameNumber,
                exclusionsBeforeDraft, draftResult);
        preparationNanos += elapsed(preparationStart, captureTimings);

        long engineStart = tick(captureTimings);
        MatchEngineV1.MatchEngineV1Execution execution = engine.executeDetailed(
                input, SimulationInstrumentation.enabled());
        long engineNanos = elapsed(engineStart, captureTimings);

        long finalizationStart = tick(captureTimings);
        history.commitCompleted(draftResult);
        validateCommittedHistory(history, draftResult, exclusionsBeforeDraft, gameNumber);
        long finalizationNanos = elapsed(finalizationStart, captureTimings);

        MatchEngineV1Output output = execution.output();
        long integrityStart = tick(captureTimings);
        invokeVoid(validateOutput, service, request, matchSeed, output);
        long integrityNanos = elapsed(integrityStart, captureTimings);

        long mappingStart = tick(captureTimings);
        RealMatchApiV1Dtos.Response response = responses.response(output);
        long mappingNanos = elapsed(mappingStart, captureTimings);
        long applicationBoundaryNanos = elapsed(boundaryStart, captureTimings);

        long serializationStart = tick(captureTimings);
        byte[] serialized = writeBytes(response);
        long serializationNanos = elapsed(serializationStart, captureTimings);
        long requestToJsonNanos = elapsed(boundaryStart, captureTimings);

        long requestAndPreflightNanos = requestNanos + preflightNanos;
        long phaseSumNanos = requestAndPreflightNanos + preparationNanos + engineNanos
                + finalizationNanos + integrityNanos + mappingNanos;
        PhaseTimings timings = captureTimings
                ? new PhaseTimings(
                requestAndPreflightNanos, preparationNanos, engineNanos,
                finalizationNanos, integrityNanos, mappingNanos, serializationNanos,
                applicationBoundaryNanos, phaseSumNanos,
                applicationBoundaryNanos - phaseSumNanos, requestToJsonNanos)
                : PhaseTimings.disabled();
        return new Execution(
                request, response, serialized, gzip(serialized), timings,
                sectionSizes(response), canonicalizer.hash(response),
                canonicalizer.hash(response.result()),
                canonicalizer.hash(response.timeline()));
    }

    private Map<String, Long> sectionSizes(RealMatchApiV1Dtos.Response response) {
        LinkedHashMap<String, Long> sizes = new LinkedHashMap<>();
        sizes.put("teams", (long) writeBytes(response.teams()).length);
        sizes.put("draft", (long) writeBytes(response.draft()).length);
        sizes.put("result", (long) writeBytes(response.result()).length);
        sizes.put("timeline", (long) writeBytes(response.timeline()).length);
        sizes.put("integrity", (long) writeBytes(response.integrity()).length);
        return Map.copyOf(sizes);
    }

    private byte[] writeBytes(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (IOException error) {
            throw new IllegalStateException("REAL_MATCH_PERFORMANCE_SERIALIZATION_FAILED", error);
        }
    }

    public static byte[] gzip(byte[] bytes) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(bytes);
            }
            return output.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("REAL_MATCH_PERFORMANCE_GZIP_FAILED", error);
        }
    }

    private long tick(boolean capture) {
        return capture ? clock.getAsLong() : 0L;
    }

    private long elapsed(long start, boolean capture) {
        if (!capture) return 0L;
        long value = clock.getAsLong() - start;
        if (value < 0L) {
            throw new IllegalStateException("REAL_MATCH_PERFORMANCE_CLOCK_MOVED_BACKWARDS");
        }
        return value;
    }

    private static void validateCommittedHistory(
            SeriesDraftHistory history,
            FinalDraftResult result,
            Set<ChampionId> exclusionsBeforeDraft,
            int gameNumber
    ) {
        if (history.committedGameCount() != gameNumber) {
            throw new IllegalStateException("HARD_FEARLESS_COMMIT_COUNT_MISMATCH");
        }
        LinkedHashSet<ChampionId> expected = new LinkedHashSet<>(exclusionsBeforeDraft);
        expected.addAll(result.bluePicks());
        expected.addAll(result.redPicks());
        if (!history.consumedPicks().equals(Set.copyOf(expected))) {
            throw new IllegalStateException("HARD_FEARLESS_COMMIT_PICK_MISMATCH");
        }
        int expectedCount = gameNumber * TeamSide.values().length * Position.values().length;
        if (expected.size() != expectedCount) {
            throw new IllegalStateException("HARD_FEARLESS_COMMIT_CARDINALITY_MISMATCH");
        }
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        try {
            Method method = owner.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Missing production validation boundary " + name,
                    error);
        }
    }

    private static <T> T field(Object owner, String name, Class<T> type) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(owner));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Missing production orchestration field " + name,
                    error);
        }
    }

    private static long invokeLong(Method method, Object owner, Object... arguments) {
        return (long) invoke(method, owner, arguments);
    }

    private static void invokeVoid(Method method, Object owner, Object... arguments) {
        invoke(method, owner, arguments);
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

    public record PhaseTimings(
            long requestValidationAndPreflightNanos,
            long rosterDraftInputPreparationNanos,
            long matchEngineExecutionNanos,
            long orchestrationFinalizationNanos,
            long outputIntegrityValidationNanos,
            long responseMappingNanos,
            long jsonSerializationNanos,
            long applicationBoundaryTotalNanos,
            long applicationPhaseSumNanos,
            long unattributedApplicationOverheadNanos,
            long instrumentedRequestToJsonTotalNanos
    ) {
        public PhaseTimings {
            if (requestValidationAndPreflightNanos < 0L
                    || rosterDraftInputPreparationNanos < 0L
                    || matchEngineExecutionNanos < 0L
                    || orchestrationFinalizationNanos < 0L
                    || outputIntegrityValidationNanos < 0L
                    || responseMappingNanos < 0L
                    || jsonSerializationNanos < 0L
                    || applicationBoundaryTotalNanos < 0L
                    || applicationPhaseSumNanos < 0L
                    || unattributedApplicationOverheadNanos < 0L
                    || instrumentedRequestToJsonTotalNanos < 0L) {
                throw new IllegalArgumentException("Negative performance timing");
            }
            if (applicationPhaseSumNanos > applicationBoundaryTotalNanos
                    || applicationBoundaryTotalNanos > instrumentedRequestToJsonTotalNanos) {
                throw new IllegalArgumentException("Performance timing coverage mismatch");
            }
        }

        static PhaseTimings disabled() {
            return new PhaseTimings(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }
    }

    public record Execution(
            RealMatchApiV1Dtos.SimulateRequest request,
            RealMatchApiV1Dtos.Response response,
            byte[] serializedResponse,
            byte[] offlineGzipResponse,
            PhaseTimings timings,
            Map<String, Long> independentlySerializedSectionBytes,
            String responseCanonicalHash,
            String resultCanonicalHash,
            String timelineCanonicalHash
    ) {
        public Execution {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(response, "response");
            serializedResponse = serializedResponse.clone();
            offlineGzipResponse = offlineGzipResponse.clone();
            Objects.requireNonNull(timings, "timings");
            independentlySerializedSectionBytes = Map.copyOf(
                    independentlySerializedSectionBytes);
            Objects.requireNonNull(responseCanonicalHash, "responseCanonicalHash");
            Objects.requireNonNull(resultCanonicalHash, "resultCanonicalHash");
            Objects.requireNonNull(timelineCanonicalHash, "timelineCanonicalHash");
        }
    }
}
