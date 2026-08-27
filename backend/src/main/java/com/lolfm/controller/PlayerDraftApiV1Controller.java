package com.lolfm.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lolfm.application.PlayerDraftApiV1ResponseMapper;
import com.lolfm.application.PlayerDraftApiV1Service;
import com.lolfm.dto.PlayerDraftApiV1Dtos;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Additive session API; Draft completion and match simulation are deliberately separate. */
@RestController
@RequestMapping("/api/v1/player-drafts/sessions")
@CrossOrigin(origins = "http://localhost:5173")
public final class PlayerDraftApiV1Controller {
    private final PlayerDraftApiV1RequestParser requests;
    private final PlayerDraftApiV1Service service;
    private final PlayerDraftApiV1ResponseMapper responses;

    public PlayerDraftApiV1Controller(
            PlayerDraftApiV1RequestParser requests,
            PlayerDraftApiV1Service service,
            PlayerDraftApiV1ResponseMapper responses
    ) {
        this.requests = requests;
        this.service = service;
        this.responses = responses;
    }

    @PostMapping
    public PlayerDraftApiV1Dtos.SessionResponse start(
            @RequestBody(required = false) JsonNode body
    ) {
        return responses.session(service.start(requests.start(body)));
    }

    @GetMapping("/{sessionId}")
    public PlayerDraftApiV1Dtos.SessionResponse get(@PathVariable String sessionId) {
        return responses.session(service.get(requests.sessionId(sessionId)));
    }

    @PostMapping("/{sessionId}/actions")
    public PlayerDraftApiV1Dtos.SessionResponse action(
            @PathVariable String sessionId,
            @RequestBody(required = false) JsonNode body
    ) {
        return responses.session(service.action(
                requests.sessionId(sessionId), requests.action(body)));
    }

    @PostMapping("/{sessionId}/simulate")
    public PlayerDraftApiV1Dtos.SimulationResponse simulate(
            @PathVariable String sessionId,
            @RequestBody(required = false) JsonNode body
    ) {
        var execution = service.simulate(
                requests.sessionId(sessionId), requests.simulate(body));
        return responses.simulation(execution.session(), execution.output());
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> cancel(@PathVariable String sessionId) {
        service.cancel(requests.sessionId(sessionId));
        return ResponseEntity.noContent().build();
    }
}
