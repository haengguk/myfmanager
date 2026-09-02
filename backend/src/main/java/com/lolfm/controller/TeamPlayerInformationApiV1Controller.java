package com.lolfm.controller;

import com.lolfm.application.TeamPlayerInformationApiV1Service;
import com.lolfm.dto.TeamPlayerInformationApiV1Dtos;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Additive read-only HTTP boundary for current LCK team and player information. */
@RestController
@RequestMapping("/api/v1/reference/leagues")
@CrossOrigin(origins = "http://localhost:5173")
public final class TeamPlayerInformationApiV1Controller {
    private final TeamPlayerInformationApiV1Service service;

    public TeamPlayerInformationApiV1Controller(TeamPlayerInformationApiV1Service service) {
        this.service = service;
    }

    @GetMapping("/{leagueCode}")
    public TeamPlayerInformationApiV1Dtos.MetadataResponse metadata(
            @PathVariable String leagueCode
    ) {
        return service.metadata(leagueCode);
    }

    @GetMapping("/{leagueCode}/teams")
    public TeamPlayerInformationApiV1Dtos.TeamsResponse teams(
            @PathVariable String leagueCode
    ) {
        return service.teams(leagueCode);
    }

    @GetMapping("/{leagueCode}/teams/{teamCode}")
    public TeamPlayerInformationApiV1Dtos.TeamResponse team(
            @PathVariable String leagueCode,
            @PathVariable String teamCode
    ) {
        return service.team(leagueCode, teamCode);
    }

    @GetMapping("/{leagueCode}/players")
    public TeamPlayerInformationApiV1Dtos.PlayersResponse players(
            @PathVariable String leagueCode,
            @RequestParam MultiValueMap<String, String> query
    ) {
        for (String field : query.keySet()) {
            if (!Set.of("teamCode", "position").contains(field)
                    || query.get(field) == null || query.get(field).size() != 1) {
                throw TeamPlayerInformationApiV1Exception.invalidQuery(field);
            }
        }
        return service.players(leagueCode, query.getFirst("teamCode"),
                query.getFirst("position"));
    }

    @GetMapping("/{leagueCode}/players/{playerId}")
    public TeamPlayerInformationApiV1Dtos.PlayerResponse player(
            @PathVariable String leagueCode,
            @PathVariable String playerId
    ) {
        return service.player(leagueCode, playerId);
    }
}
