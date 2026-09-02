package com.lolfm.application;

import com.lolfm.controller.TeamPlayerInformationApiV1Exception;
import com.lolfm.domain.Position;
import com.lolfm.dto.TeamPlayerInformationApiV1Dtos;
import com.lolfm.player.PlayerId;
import com.lolfm.reference.TeamPlayerInformationCatalog;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Stateless read-only application service for the LCK information catalog. */
@Service
public final class TeamPlayerInformationApiV1Service {
    private final TeamPlayerInformationCatalog catalog;
    private final TeamPlayerInformationApiV1ResponseMapper responses;

    public TeamPlayerInformationApiV1Service(
            TeamPlayerInformationCatalog catalog,
            TeamPlayerInformationApiV1ResponseMapper responses
    ) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.responses = Objects.requireNonNull(responses, "responses");
    }

    public TeamPlayerInformationApiV1Dtos.MetadataResponse metadata(String leagueCode) {
        validateLeague(leagueCode);
        return project(responses::metadata);
    }

    public TeamPlayerInformationApiV1Dtos.TeamsResponse teams(String leagueCode) {
        validateLeague(leagueCode);
        return project(responses::teams);
    }

    public TeamPlayerInformationApiV1Dtos.TeamResponse team(
            String leagueCode,
            String teamCode
    ) {
        validateLeague(leagueCode);
        TeamPlayerInformationCatalog.TeamInformation team = catalog.findTeam(teamCode)
                .orElseThrow(TeamPlayerInformationApiV1Exception::teamNotFound);
        return project(() -> responses.team(team));
    }

    public TeamPlayerInformationApiV1Dtos.PlayersResponse players(
            String leagueCode,
            String teamCode,
            String position
    ) {
        validateLeague(leagueCode);
        String exactTeam = validateTeamFilter(teamCode);
        Position exactPosition = validatePositionFilter(position);
        return project(() -> responses.players(exactTeam, exactPosition,
                catalog.players(exactTeam, exactPosition)));
    }

    public TeamPlayerInformationApiV1Dtos.PlayerResponse player(
            String leagueCode,
            String playerId
    ) {
        validateLeague(leagueCode);
        PlayerId exactPlayerId;
        try {
            exactPlayerId = new PlayerId(playerId);
        } catch (RuntimeException error) {
            throw TeamPlayerInformationApiV1Exception.playerNotFound();
        }
        TeamPlayerInformationCatalog.PlayerInformation player =
                catalog.findPlayer(exactPlayerId)
                        .orElseThrow(TeamPlayerInformationApiV1Exception::playerNotFound);
        return project(() -> responses.player(player));
    }

    private static void validateLeague(String leagueCode) {
        if (!TeamPlayerInformationCatalog.LEAGUE_CODE.equals(leagueCode)) {
            throw TeamPlayerInformationApiV1Exception.leagueNotFound();
        }
    }

    private String validateTeamFilter(String teamCode) {
        if (teamCode == null) return null;
        if (teamCode.isBlank() || !catalog.findTeam(teamCode).isPresent()) {
            throw TeamPlayerInformationApiV1Exception.invalidQuery("teamCode");
        }
        return teamCode;
    }

    private static Position validatePositionFilter(String position) {
        if (position == null) return null;
        if (position.isBlank() || !position.equals(position.toUpperCase(Locale.ROOT))) {
            throw TeamPlayerInformationApiV1Exception.invalidQuery("position");
        }
        try {
            return Position.valueOf(position);
        } catch (IllegalArgumentException error) {
            throw TeamPlayerInformationApiV1Exception.invalidQuery("position");
        }
    }

    private static <T> T project(java.util.function.Supplier<T> projection) {
        try {
            return projection.get();
        } catch (TeamPlayerInformationApiV1Exception error) {
            throw error;
        } catch (RuntimeException error) {
            throw TeamPlayerInformationApiV1Exception.integrityFailure(error);
        }
    }
}
