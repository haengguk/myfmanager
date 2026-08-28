package com.lolfm.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lolfm.application.SeriesApiV1Facade;
import com.lolfm.dto.SeriesApiV1Dtos;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/series")
@CrossOrigin(origins = "http://localhost:5173")
public final class SeriesApiV1Controller {
    private final SeriesApiV1RequestParser requests;
    private final SeriesApiV1Facade series;

    public SeriesApiV1Controller(
            SeriesApiV1RequestParser requests, SeriesApiV1Facade series
    ) {
        this.requests = requests;
        this.series = series;
    }

    @PostMapping
    public ResponseEntity<SeriesApiV1Dtos.SeriesView> create(
            @RequestBody(required = false) JsonNode body
    ) {
        var result = series.create(requests.create(body));
        return ResponseEntity.status(result.replayed() ? 200 : 201).body(result.series());
    }

    @GetMapping("/{seriesId}")
    public SeriesApiV1Dtos.SeriesView get(@PathVariable String seriesId) {
        return series.get(requests.seriesId(seriesId));
    }

    @PostMapping("/{seriesId}/games/current/draft-session")
    public ResponseEntity<SeriesApiV1Facade.DraftResponse> createDraft(
            @PathVariable String seriesId,
            @RequestBody(required = false) JsonNode body
    ) {
        var result = series.createDraft(requests.seriesId(seriesId),
                requests.draftCreate(body));
        return ResponseEntity.status(result.replayed() ? 200 : 201).body(result);
    }

    @GetMapping("/{seriesId}/games/{gameNumber}/draft-session")
    public SeriesApiV1Facade.DraftResponse getDraft(
            @PathVariable String seriesId, @PathVariable String gameNumber
    ) {
        return series.getDraft(requests.seriesId(seriesId),
                requests.gameNumber(gameNumber));
    }

    @PostMapping("/{seriesId}/games/{gameNumber}/draft-session/actions")
    public SeriesApiV1Facade.DraftResponse draftAction(
            @PathVariable String seriesId, @PathVariable String gameNumber,
            @RequestBody(required = false) JsonNode body
    ) {
        return series.draftAction(requests.seriesId(seriesId),
                requests.gameNumber(gameNumber), requests.draftAction(body));
    }

    @DeleteMapping("/{seriesId}/games/{gameNumber}/draft-session")
    public ResponseEntity<Void> cancelDraft(
            @PathVariable String seriesId, @PathVariable String gameNumber,
            @RequestBody(required = false) JsonNode body
    ) {
        series.cancelDraft(requests.seriesId(seriesId), requests.gameNumber(gameNumber),
                requests.draftCancel(body));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{seriesId}/games/{gameNumber}/simulate")
    public ResponseEntity<SeriesApiV1Dtos.SimulationResponse> simulate(
            @PathVariable String seriesId, @PathVariable String gameNumber,
            @RequestBody(required = false) JsonNode body
    ) {
        var result = series.simulate(requests.seriesId(seriesId),
                requests.gameNumber(gameNumber), requests.simulate(body));
        return ResponseEntity.status(result.accepted() ? 202 : 200).body(result.response());
    }

    @GetMapping("/{seriesId}/games/{gameNumber}")
    public SeriesApiV1Dtos.SeriesGameView getGame(
            @PathVariable String seriesId, @PathVariable String gameNumber
    ) {
        return series.getGame(requests.seriesId(seriesId), requests.gameNumber(gameNumber));
    }

    @PostMapping("/{seriesId}/games/{gameNumber}/replay")
    public SeriesApiV1Dtos.ReplayResponse replay(
            @PathVariable String seriesId, @PathVariable String gameNumber,
            @RequestBody(required = false) JsonNode body
    ) {
        return series.replay(requests.seriesId(seriesId), requests.gameNumber(gameNumber),
                requests.replay(body));
    }

    @DeleteMapping("/{seriesId}")
    public ResponseEntity<Void> cancel(
            @PathVariable String seriesId,
            @RequestBody(required = false) JsonNode body
    ) {
        series.cancel(requests.seriesId(seriesId), requests.cancel(body));
        return ResponseEntity.noContent().build();
    }
}
