package com.lolfm.career;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.player.GlobalTeamRosterCatalog;
import com.lolfm.player.PlayerId;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Persisted five-starter gameplay inputs, independent of later roster catalog changes. */
public record CompetitionRosterSnapshot(Map<String, Roster> teams) {
    public static final String POLICY = "INTERNATIONAL_FROZEN_FIVE_STARTERS_V1";
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public CompetitionRosterSnapshot {
        teams = Map.copyOf(teams);
        if (teams.isEmpty()) throw new IllegalArgumentException("EMPTY_COMPETITION_ROSTER");
        var players = new java.util.HashSet<String>();
        teams.forEach((token, roster) -> {
            if (!token.equals(token(roster.team()))) throw new IllegalArgumentException("ROSTER_TEAM_SCOPE");
            for (Starter player : roster.players()) {
                if (!players.add(player.playerId())) throw new IllegalArgumentException("DUPLICATE_COMPETITION_PLAYER");
            }
        });
    }

    public static String token(GlobalTeamRosterCatalog.TeamKey key) {
        return key.leagueCode() + ':' + key.teamCode();
    }

    public static String managedToken(String code) { return "LCK:" + code; }

    public static Roster capture(GlobalTeamRosterCatalog.TeamRosterSnapshot source) {
        return new Roster(source.team(), source.snapshotIdentity(), source.players().stream()
                .map(p -> new Starter(p.playerId().value(), p.nickname(), p.position(), p.ratings().asMap(),
                        p.proficiencies().asMap().entrySet().stream()
                                .map(e -> new Proficiency(e.getKey().championId().value(), e.getKey().position(), e.getValue()))
                                .sorted(Comparator.comparing(Proficiency::championId).thenComparing(Proficiency::position)).toList()))
                .toList());
    }

    public CompetitionRosterSnapshot pair(String first, String second) {
        return new CompetitionRosterSnapshot(Map.of(first, roster(first), second, roster(second)));
    }

    public Roster roster(String token) {
        Roster result = teams.get(token);
        if (result == null) throw new IllegalArgumentException("FROZEN_COMPETITION_TEAM_MISSING:" + token);
        return result;
    }

    /** New mutable domain objects for every draft/game/reprojection. */
    public Team assemble(String token) { return roster(token).assemble(); }
    public String canonical() {
        try { return JSON.writeValueAsString(this); }
        catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    public String identity() { return hash(POLICY + '\n' + canonical()); }
    public String encoded() { return Base64.getEncoder().encodeToString(canonical().getBytes(StandardCharsets.UTF_8)); }
    public static CompetitionRosterSnapshot decode(String encoded) {
        try {
            String text = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            var value = JSON.readValue(text, CompetitionRosterSnapshot.class);
            if (!value.canonical().equals(text)) throw new IllegalArgumentException("NON_CANONICAL_COMPETITION_ROSTER");
            return value;
        } catch (java.io.IOException e) { throw new IllegalArgumentException("INVALID_COMPETITION_ROSTER", e); }
    }
    private static String hash(String text) { return CareerCompetitionRules.sha256(text.getBytes(StandardCharsets.UTF_8)); }

    public record Roster(GlobalTeamRosterCatalog.TeamKey team, String sourceSnapshotIdentity, List<Starter> players) {
        public Roster {
            Objects.requireNonNull(team, "team");
            CareerIdentity.requireSha256(sourceSnapshotIdentity, "sourceSnapshotIdentity");
            players = List.copyOf(players);
            if (players.size() != 5 || players.stream().map(Starter::position).distinct().count() != 5
                    || players.stream().map(Starter::playerId).distinct().count() != 5)
                throw new IllegalArgumentException("INVALID_FIVE_STARTER_ROSTER");
        }
        public String identity() {
            try { return hash(JSON.writeValueAsString(this)); }
            catch (java.io.IOException e) { throw new IllegalStateException(e); }
        }
        public Team assemble() { return new Team(token(team), players.stream().map(Starter::assemble).toList()); }
        /** Equal weight for each of the 60 authored, position-appropriate rating values. */
        public int strength() { return players.stream().flatMap(p -> p.ratings().values().stream()).mapToInt(Integer::intValue).sum(); }
    }

    public record Starter(String playerId, String nickname, Position position,
                          Map<PlayerSkill, Integer> ratings, List<Proficiency> proficiencies) {
        public Starter {
            new PlayerId(playerId);
            Objects.requireNonNull(nickname, "nickname");
            ratings = Map.copyOf(ratings);
            proficiencies = List.copyOf(proficiencies);
            new PlayerRatings(position, ratings);
            if (proficiencies.stream().map(p -> p.championId() + ':' + p.position()).distinct().count() != proficiencies.size())
                throw new IllegalArgumentException("DUPLICATE_FROZEN_PROFICIENCY");
            proficiencies.forEach(p -> { if (p.position() != position) throw new IllegalArgumentException("PROFICIENCY_ROLE_MISMATCH"); });
        }
        Player assemble() {
            Map<ChampionRoleKey, Integer> skills = new java.util.HashMap<>();
            proficiencies.forEach(p -> skills.put(new ChampionRoleKey(new ChampionId(p.championId()), p.position()), p.value()));
            return new Player(new PlayerId(playerId), nickname, position, new PlayerRatings(position, ratings), new ChampionProficiencies(skills));
        }
    }
    public record Proficiency(String championId, Position position, int value) {
        public Proficiency {
            new ChampionId(championId); Objects.requireNonNull(position, "position");
            if (value < 1 || value > 20) throw new IllegalArgumentException("INVALID_FROZEN_PROFICIENCY");
        }
    }
}
