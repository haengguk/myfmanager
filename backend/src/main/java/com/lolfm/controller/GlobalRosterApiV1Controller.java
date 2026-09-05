package com.lolfm.controller;

import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.dto.GlobalRosterApiV1Dtos.*;
import com.lolfm.player.GlobalTeamRosterCatalog;
import com.lolfm.player.GlobalTeamRosterCatalog.LeagueRoster;
import com.lolfm.player.GlobalTeamRosterCatalog.PlayerSnapshot;
import com.lolfm.player.GlobalTeamRosterCatalog.TeamKey;
import com.lolfm.player.PlayerId;
import com.lolfm.player.PlayerIdentity;
import com.lolfm.player.PlayerRatingResourceLoader;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static com.lolfm.dto.GlobalRosterApiV1Dtos.SCHEMA;

/** Read-only access to all registered snapshots without expanding domestic Career participation. */
@RestController
@RequestMapping("/api/v1/reference/rosters")
@CrossOrigin(origins = "http://localhost:5173")
public final class GlobalRosterApiV1Controller {
    private final GlobalTeamRosterCatalog catalog;

    public GlobalRosterApiV1Controller(GlobalTeamRosterCatalog catalog) { this.catalog = catalog; }

    @GetMapping
    public LeaguesResponse leagues() {
        return new LeaguesResponse(SCHEMA, catalog.leagueCodes().stream().map(code -> {
            LeagueRoster league = catalog.league(code);
            return new LeagueSummary(code, league.ratings().teamCount(), league.ratings().playerCount());
        }).toList());
    }

    @GetMapping("/{leagueCode}")
    public LeagueResponse league(@PathVariable String leagueCode) {
        LeagueRoster league = requireLeague(leagueCode);
        var ratings = league.ratings();
        var identities = ratings.identities();
        var proficiency = league.proficiencies();
        var career = league.career().source();
        List<Source> sources = List.of(
                source("PLAYER_IDENTITY", identities.version(), identities.snapshotAt(), identities.resourceSha256()),
                source("PLAYER_RATING", ratings.version(), ratings.snapshotAt(), ratings.resourceSha256()),
                source("CHAMPION_PROFICIENCY", proficiency.version(), proficiency.researchAsOf(), proficiency.resourceSha256()),
                source("PLAYER_CAREER", career.version(), career.snapshotAt(), career.sha256()));
        List<TeamSummary> teams = ratings.teamCodes().stream().sorted().map(code ->
                new TeamSummary(league.leagueCode(), code, identities.all().stream()
                        .filter(player -> player.ratingKey().teamCode().equals(code))
                        .map(player -> new PlayerSummary(player.playerId().value(), player.nickname(),
                                player.ratingKey().position())).toList())).toList();
        return new LeagueResponse(SCHEMA, league.leagueCode(), ratings.playerCount(), teams,
                sources, league.career().metadata());
    }

    @GetMapping("/{leagueCode}/teams/{teamCode}")
    public TeamResponse team(@PathVariable String leagueCode, @PathVariable String teamCode) {
        LeagueRoster league = requireLeague(leagueCode);
        TeamKey key;
        try {
            key = new TeamKey(league.leagueCode(), teamCode);
        } catch (IllegalArgumentException error) {
            throw TeamPlayerInformationApiV1Exception.teamNotFound();
        }
        if (!league.ratings().teamCodes().contains(key.teamCode())) throw TeamPlayerInformationApiV1Exception.teamNotFound();
        var snapshot = catalog.snapshot(key);
        return new TeamResponse(SCHEMA, key.leagueCode(), key.teamCode(), snapshot.snapshotIdentity(),
                snapshot.players().stream().map(player -> detail(league, player)).toList());
    }

    @GetMapping("/{leagueCode}/players/{playerId}")
    public PlayerResponse player(@PathVariable String leagueCode, @PathVariable String playerId) {
        LeagueRoster league = requireLeague(leagueCode);
        PlayerId id;
        try {
            id = new PlayerId(playerId);
        } catch (IllegalArgumentException error) {
            throw TeamPlayerInformationApiV1Exception.playerNotFound();
        }
        PlayerIdentity identity = league.ratings().identities().find(id)
                .orElseThrow(TeamPlayerInformationApiV1Exception::playerNotFound);
        var snapshot = catalog.snapshot(new TeamKey(league.leagueCode(), identity.ratingKey().teamCode()));
        var player = snapshot.players().stream().filter(value -> value.playerId().equals(id)).findFirst().orElseThrow();
        return new PlayerResponse(SCHEMA, league.leagueCode(), identity.ratingKey().teamCode(),
                snapshot.snapshotIdentity(), detail(league, player));
    }

    private LeagueRoster requireLeague(String leagueCode) {
        try {
            return catalog.league(leagueCode);
        } catch (IllegalArgumentException error) {
            throw TeamPlayerInformationApiV1Exception.leagueNotFound();
        }
    }

    private static PlayerDetail detail(LeagueRoster league, PlayerSnapshot player) {
        Map<String, Integer> ratings = new LinkedHashMap<>();
        PlayerSkill.orderedForPosition(player.position()).forEach(skill ->
                ratings.put(PlayerRatingResourceLoader.jsonName(skill), player.ratings().get(skill)));
        List<Proficiency> proficiencies = player.proficiencies().asMap().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(key -> key.stableId())))
                .map(entry -> new Proficiency(entry.getKey().championId().value(),
                        entry.getKey().position(), entry.getValue())).toList();
        return new PlayerDetail(player.playerId().value(), player.nickname(), player.position(), ratings,
                proficiencies, ChampionProficiencies.NEUTRAL, league.career().player(player.playerId()));
    }

    private static Source source(String role, String version, String snapshot, String sha) {
        return new Source(role, "/players/" + version + ".json", version, snapshot, sha);
    }
}
