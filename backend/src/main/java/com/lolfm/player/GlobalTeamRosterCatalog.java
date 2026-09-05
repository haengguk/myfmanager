package com.lolfm.player;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.reference.PlayerCareerResourceLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** League-scoped immutable rosters, separate from the existing LCK season/provenance authority. */
@Component
public final class GlobalTeamRosterCatalog {
    private final Map<String, LeagueRoster> leagues;

    public GlobalTeamRosterCatalog(ObjectMapper mapper, PlayerRatingCatalog lckRatings,
                                   ChampionProficiencyCatalog lckProficiencies, ChampionCatalog champions) {
        Map<String, LeagueRoster> loaded = new LinkedHashMap<>();
        PlayerResourceSpec lckCareer = new PlayerResourceSpec("LCK", 10,
                PlayerCareerResourceLoader.VERSION, PlayerCareerResourceLoader.SNAPSHOT_AT,
                PlayerCareerResourceLoader.EXPECTED_SHA256, null);
        loaded.put("LCK", new LeagueRoster("LCK", lckRatings, lckProficiencies,
                RosterCareerReferences.load(mapper, lckCareer, lckRatings.identities())));
        for (GlobalRosterDatasets.Dataset dataset : GlobalRosterDatasets.OVERSEAS) {
            PlayerRatingResourceLoader.LoadedResource ratings = PlayerRatingResourceLoader.load(mapper,
                    GlobalTeamRosterCatalog.class.getResourceAsStream(dataset.ratings().resource()), dataset.ratings());
            PlayerIdentityCatalog identities = new PlayerIdentityCatalog(PlayerIdentityResourceLoader.load(mapper,
                    GlobalTeamRosterCatalog.class.getResourceAsStream(dataset.identities().resource()),
                    dataset.identities(), ratings));
            PlayerRatingCatalog ratingCatalog = new PlayerRatingCatalog(ratings, identities);
            ChampionProficiencyCatalog proficiencies = new ChampionProficiencyCatalog(
                    ChampionProficiencyResourceLoader.load(mapper,
                            GlobalTeamRosterCatalog.class.getResourceAsStream(dataset.proficiencies().resource()),
                            dataset.proficiencies(), ratingCatalog, champions), ratingCatalog, champions);
            String league = dataset.ratings().leagueCode();
            loaded.put(league, new LeagueRoster(league, ratingCatalog, proficiencies,
                    RosterCareerReferences.load(mapper, dataset.career(), identities)));
        }
        // These files describe one simultaneous starter population. Transfers need a separate roster policy.
        HashSet<PlayerId> players = new HashSet<>();
        for (LeagueRoster league : loaded.values()) {
            for (PlayerIdentity identity : league.ratings().identities().all()) {
                if (!players.add(identity.playerId())) {
                    throw new IllegalStateException("Duplicate cross-league PlayerId: " + identity.playerId());
                }
            }
        }
        leagues = Collections.unmodifiableMap(loaded);
    }

    public List<String> leagueCodes() { return List.copyOf(leagues.keySet()); }
    public LeagueRoster league(String leagueCode) {
        String normalized = normalize(leagueCode);
        LeagueRoster result = leagues.get(normalized);
        if (result == null) throw new IllegalArgumentException("Unknown roster league: " + normalized);
        return result;
    }

    public TeamRosterSnapshot snapshot(TeamKey key) {
        LeagueRoster league = league(key.leagueCode());
        if (!league.ratings().teamCodes().contains(key.teamCode())) {
            throw new IllegalArgumentException("Unknown roster team: " + key);
        }
        List<PlayerSnapshot> players = new ArrayList<>();
        for (Position position : Position.values()) {
            PlayerRatingKey ratingKey = new PlayerRatingKey(key.teamCode(), position);
            PlayerIdentity identity = league.ratings().identities().get(ratingKey);
            players.add(new PlayerSnapshot(identity.playerId(), identity.nickname(), position,
                    league.ratings().ratings(ratingKey),
                    league.proficiencies().bind(identity.playerId(), ratingKey, identity.playerId())));
        }
        String identity = RosterCareerReferences.digest(("GLOBAL_TEAM_ROSTER_SNAPSHOT_V1\n"
                + key.leagueCode() + "\n" + key.teamCode() + "\n"
                + league.ratings().identities().resourceSha256() + "\n"
                + league.ratings().resourceSha256() + "\n"
                + league.proficiencies().resourceSha256() + "\n"
                + league.proficiencies().requiredChampionPoolVersion() + "\n").getBytes(StandardCharsets.UTF_8));
        return new TeamRosterSnapshot(key, identity, players);
    }

    /** Every call creates a new Team and new Players; mutable match state is never cached here. */
    public Team assemble(TeamKey key) { return snapshot(key).assemble(); }

    public record TeamKey(String leagueCode, String teamCode) {
        public TeamKey {
            leagueCode = normalize(leagueCode);
            teamCode = normalize(teamCode);
            if (!leagueCode.matches("[A-Z]+") || !teamCode.matches("[A-Z0-9]+")) {
                throw new IllegalArgumentException("Invalid league/team code");
            }
        }
    }

    public record LeagueRoster(String leagueCode, PlayerRatingCatalog ratings,
                               ChampionProficiencyCatalog proficiencies, RosterCareerReferences career) { }

    public record PlayerSnapshot(PlayerId playerId, String nickname, Position position,
                                 PlayerRatings ratings, ChampionProficiencies proficiencies) {
        public PlayerSnapshot {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(nickname, "nickname");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(ratings, "ratings");
            Objects.requireNonNull(proficiencies, "proficiencies");
            if (ratings.position() != position) throw new IllegalArgumentException("Rating position mismatch");
        }
        Player assemble() { return new Player(playerId, nickname, position, ratings, proficiencies); }
    }

    public record TeamRosterSnapshot(TeamKey team, String snapshotIdentity, List<PlayerSnapshot> players) {
        public TeamRosterSnapshot {
            Objects.requireNonNull(team, "team");
            Objects.requireNonNull(snapshotIdentity, "snapshotIdentity");
            players = List.copyOf(players);
            if (players.size() != Position.values().length
                    || players.stream().map(PlayerSnapshot::position).distinct().count() != Position.values().length
                    || players.stream().map(PlayerSnapshot::playerId).distinct().count() != players.size()) {
                throw new IllegalArgumentException("Incomplete or duplicate roster snapshot");
            }
        }
        public Team assemble() { return new Team(team.teamCode(), players.stream().map(PlayerSnapshot::assemble).toList()); }
    }

    private static String normalize(String value) { return Objects.requireNonNull(value, "code").trim().toUpperCase(Locale.ROOT); }
}
