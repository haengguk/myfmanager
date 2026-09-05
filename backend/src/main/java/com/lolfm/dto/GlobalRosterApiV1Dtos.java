package com.lolfm.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.lolfm.domain.Position;
import java.util.List;
import java.util.Map;

/** Additive roster/reference contract; careerReference retains the versioned author's JSON fields. */
public final class GlobalRosterApiV1Dtos {
    public static final String SCHEMA = "GLOBAL_ROSTER_REFERENCE_V1";
    private GlobalRosterApiV1Dtos() { }

    public record LeaguesResponse(String schemaVersion, List<LeagueSummary> leagues) { }
    public record LeagueSummary(String leagueCode, int teamCount, int playerCount) { }
    public record Source(String role, String resource, String version, String snapshotAt, String sha256) { }
    public record LeagueResponse(String schemaVersion, String leagueCode, int playerCount,
                                 List<TeamSummary> teams, List<Source> sources,
                                 JsonNode careerReferenceMetadata) { }
    public record TeamSummary(String leagueCode, String teamCode, List<PlayerSummary> players) { }
    public record PlayerSummary(String playerId, String nickname, Position position) { }
    public record TeamResponse(String schemaVersion, String leagueCode, String teamCode,
                               String rosterSnapshotIdentity, List<PlayerDetail> players) { }
    public record PlayerResponse(String schemaVersion, String leagueCode, String teamCode,
                                 String rosterSnapshotIdentity, PlayerDetail player) { }
    public record PlayerDetail(String playerId, String nickname, Position position,
                               Map<String, Integer> ratings, List<Proficiency> authoredProficiencies,
                               int omittedLegalRoleProficiency, JsonNode careerReference) { }
    public record Proficiency(String championId, Position position, int value) { }
}
