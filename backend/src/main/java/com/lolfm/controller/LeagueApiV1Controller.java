package com.lolfm.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lolfm.dto.LeagueApiV1Dtos;
import com.lolfm.league.LeagueApiV1Facade;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leagues")
@CrossOrigin(origins = "http://localhost:5173")
public final class LeagueApiV1Controller {
    private final LeagueApiV1Facade league;
    private final LeagueApiV1RequestParser parser;

    public LeagueApiV1Controller(
            LeagueApiV1Facade league,
            LeagueApiV1RequestParser parser
    ) {
        this.league = league;
        this.parser = parser;
    }

    @PostMapping
    public ResponseEntity<LeagueApiV1Dtos.SeasonResponse> create(
            @RequestBody JsonNode body
    ) {
        var result = league.create(parser.create(body));
        return ResponseEntity.status(result.httpStatus()).body(result.body());
    }

    @GetMapping("/{leagueId}/seasons/{seasonId}")
    public LeagueApiV1Dtos.SeasonResponse season(
            @PathVariable String leagueId,
            @PathVariable String seasonId
    ) {
        return league.season(leagueId, seasonId);
    }

    @GetMapping("/{leagueId}/seasons/{seasonId}/standings")
    public LeagueApiV1Dtos.StandingsResponse standings(
            @PathVariable String leagueId,
            @PathVariable String seasonId
    ) {
        return league.standings(leagueId, seasonId);
    }

    @GetMapping("/{leagueId}/seasons/{seasonId}/fixtures")
    public LeagueApiV1Dtos.FixturesResponse fixtures(
            @PathVariable String leagueId,
            @PathVariable String seasonId
    ) {
        return league.fixtures(leagueId, seasonId);
    }

    @GetMapping("/{leagueId}/seasons/{seasonId}/fixtures/{fixtureId}")
    public LeagueApiV1Dtos.FixtureResponse fixture(
            @PathVariable String leagueId,
            @PathVariable String seasonId,
            @PathVariable String fixtureId
    ) {
        return league.fixture(leagueId, seasonId, fixtureId);
    }

    @PostMapping("/{leagueId}/seasons/{seasonId}/commands/run-current-round")
    public ResponseEntity<LeagueApiV1Dtos.RunResponse> runCurrentRound(
            @PathVariable String leagueId,
            @PathVariable String seasonId,
            @RequestBody JsonNode body
    ) {
        var result = league.runCurrentRound(leagueId, seasonId, parser.run(body));
        return ResponseEntity.status(result.httpStatus()).body(result.body());
    }

    @PostMapping("/{leagueId}/seasons/{seasonId}/commands/pause")
    public ResponseEntity<LeagueApiV1Dtos.SeasonResponse> pause(
            @PathVariable String leagueId,
            @PathVariable String seasonId,
            @RequestBody JsonNode body
    ) {
        var result = league.pause(leagueId, seasonId, parser.lifecycle(body));
        return ResponseEntity.status(result.httpStatus()).body(result.body());
    }

    @PostMapping("/{leagueId}/seasons/{seasonId}/commands/resume")
    public ResponseEntity<LeagueApiV1Dtos.SeasonResponse> resume(
            @PathVariable String leagueId,
            @PathVariable String seasonId,
            @RequestBody JsonNode body
    ) {
        var result = league.resume(leagueId, seasonId, parser.lifecycle(body));
        return ResponseEntity.status(result.httpStatus()).body(result.body());
    }

    @DeleteMapping("/{leagueId}/seasons/{seasonId}")
    public ResponseEntity<Void> cancel(
            @PathVariable String leagueId,
            @PathVariable String seasonId,
            @RequestBody JsonNode body
    ) {
        var result = league.cancel(leagueId, seasonId, parser.lifecycle(body));
        return ResponseEntity.status(result.httpStatus()).build();
    }

    @GetMapping("/{leagueId}/seasons/{seasonId}/jobs/{jobId}")
    public LeagueApiV1Dtos.JobResponse job(
            @PathVariable String leagueId,
            @PathVariable String seasonId,
            @PathVariable String jobId
    ) {
        return league.job(leagueId, seasonId, jobId);
    }

    @PostMapping("/{leagueId}/seasons/{seasonId}/fixtures/{fixtureId}/player-series")
    public ResponseEntity<LeagueApiV1Dtos.PlayerSeriesResponse> startPlayerSeries(
            @PathVariable String leagueId,
            @PathVariable String seasonId,
            @PathVariable String fixtureId,
            @RequestBody JsonNode body
    ) {
        var result = league.startPlayerSeries(leagueId, seasonId, fixtureId,
                parser.playerSeries(body));
        return ResponseEntity.status(result.httpStatus()).body(result.body());
    }

    @GetMapping("/{leagueId}/seasons/{seasonId}/fixtures/{fixtureId}/player-series")
    public LeagueApiV1Dtos.PlayerSeriesResponse playerSeries(
            @PathVariable String leagueId,
            @PathVariable String seasonId,
            @PathVariable String fixtureId
    ) {
        return league.playerSeries(leagueId, seasonId, fixtureId);
    }

    @PostMapping("/{leagueId}/seasons/{seasonId}/fixtures/{fixtureId}/player-series/completion")
    public ResponseEntity<LeagueApiV1Dtos.CompletionStatusResponse> completePlayerSeries(
            @PathVariable String leagueId,
            @PathVariable String seasonId,
            @PathVariable String fixtureId,
            @RequestBody JsonNode body
    ) {
        var result = league.completePlayerSeries(leagueId, seasonId, fixtureId,
                parser.completion(body));
        return ResponseEntity.status(result.httpStatus()).body(result.body());
    }

    @GetMapping("/{leagueId}/seasons/{seasonId}/fixtures/{fixtureId}/completion-status")
    public LeagueApiV1Dtos.CompletionStatusResponse completionStatus(
            @PathVariable String leagueId,
            @PathVariable String seasonId,
            @PathVariable String fixtureId
    ) {
        return league.completionStatus(leagueId, seasonId, fixtureId);
    }
}
