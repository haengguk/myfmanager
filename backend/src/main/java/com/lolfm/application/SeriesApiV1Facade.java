package com.lolfm.application;

import com.lolfm.dto.SeriesApiV1Dtos;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Public DTO-only facade keeps package-private aggregate ownership out of controllers. */
@Service
public final class SeriesApiV1Facade {
    private final SeriesLifecycleService lifecycle;
    private final SeriesApiV1ResponseMapper responses;

    public SeriesApiV1Facade(
            SeriesLifecycleService lifecycle, SeriesApiV1ResponseMapper responses
    ) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.responses = Objects.requireNonNull(responses, "responses");
    }

    public CreateResponse create(SeriesApiV1Dtos.CreateRequest request) {
        var result = lifecycle.create(request);
        return new CreateResponse(responses.series(result.aggregate()), result.replayed());
    }

    public SeriesApiV1Dtos.SeriesView get(String seriesId) {
        return responses.series(lifecycle.get(seriesId));
    }

    public DraftResponse createDraft(
            String seriesId, SeriesApiV1Dtos.DraftCreateRequest request
    ) {
        var result = lifecycle.createDraft(seriesId, request);
        return new DraftResponse(responses.series(result.aggregate()),
                responses.childEnvelope(result.aggregate(), result.game(), result.child()),
                result.replayed());
    }

    public DraftResponse getDraft(String seriesId, int gameNumber) {
        var result = lifecycle.getDraft(seriesId, gameNumber);
        return new DraftResponse(responses.series(result.aggregate()),
                responses.childEnvelope(result.aggregate(), result.game(), result.child()), false);
    }

    public DraftResponse draftAction(
            String seriesId, int gameNumber, SeriesApiV1Dtos.DraftActionRequest request
    ) {
        var result = lifecycle.draftAction(seriesId, gameNumber, request);
        return new DraftResponse(responses.series(result.aggregate()),
                responses.childEnvelope(result.aggregate(), result.game(), result.child()),
                result.replayed());
    }

    public void cancelDraft(
            String seriesId, int gameNumber, SeriesApiV1Dtos.DraftCancelRequest request
    ) {
        lifecycle.cancelDraft(seriesId, gameNumber, request);
    }

    public SimulationHttpResponse simulate(
            String seriesId, int gameNumber, SeriesApiV1Dtos.SimulateRequest request
    ) {
        var result = lifecycle.simulate(seriesId, gameNumber, request);
        return new SimulationHttpResponse(new SeriesApiV1Dtos.SimulationResponse(
                SeriesApiV1Dtos.SIMULATION_RESPONSE_SCHEMA, result.replayed(),
                responses.series(result.aggregate()), responses.game(result.game()),
                result.output() == null ? null : responses.match(result.output(), result.aggregate().frozenCompetitionRosters())),
                result.inProgress());
    }

    public SeriesApiV1Dtos.SeriesGameView getGame(String seriesId, int gameNumber) {
        return responses.game(lifecycle.getGame(seriesId, gameNumber));
    }

    public SeriesApiV1Dtos.ReplayResponse replay(
            String seriesId, int gameNumber, SeriesApiV1Dtos.ReplayRequest request
    ) {
        var result = lifecycle.replay(seriesId, gameNumber, request);
        return new SeriesApiV1Dtos.ReplayResponse(
                SeriesApiV1Dtos.REPLAY_RESPONSE_SCHEMA,
                responses.series(result.aggregate()), responses.game(result.game()),
                responses.match(result.output(), result.aggregate().frozenCompetitionRosters()));
    }

    public void cancel(String seriesId, SeriesApiV1Dtos.CancelRequest request) {
        lifecycle.cancel(seriesId, request);
    }

    public record CreateResponse(SeriesApiV1Dtos.SeriesView series, boolean replayed) {}
    public record DraftResponse(
            SeriesApiV1Dtos.SeriesView series,
            SeriesApiV1Dtos.ChildDraftEnvelope draftSession,
            boolean replayed
    ) {}
    public record SimulationHttpResponse(
            SeriesApiV1Dtos.SimulationResponse response, boolean accepted
    ) {}
}
