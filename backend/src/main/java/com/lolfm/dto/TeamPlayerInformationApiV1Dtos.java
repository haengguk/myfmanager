package com.lolfm.dto;

import com.lolfm.domain.Position;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Immutable transport contracts for the additive LCK team/player reference API. */
public final class TeamPlayerInformationApiV1Dtos {
    public static final String METADATA_SCHEMA = "TEAM_PLAYER_INFORMATION_METADATA_V1";
    public static final String TEAMS_SCHEMA = "TEAM_PLAYER_INFORMATION_TEAMS_V1";
    public static final String TEAM_SCHEMA = "TEAM_PLAYER_INFORMATION_TEAM_V1";
    public static final String PLAYERS_SCHEMA = "TEAM_PLAYER_INFORMATION_PLAYERS_V1";
    public static final String PLAYER_SCHEMA = "TEAM_PLAYER_INFORMATION_PLAYER_V1";
    public static final String ERROR_SCHEMA = "TEAM_PLAYER_INFORMATION_API_ERROR_V1";

    private TeamPlayerInformationApiV1Dtos() {
    }

    public record MetadataResponse(
            String schemaVersion,
            String leagueCode,
            CatalogMetadata catalog,
            CatalogCounts counts,
            DataSemantics semantics,
            Limitations limitations
    ) {
    }

    public record TeamsResponse(
            String schemaVersion,
            String leagueCode,
            CatalogMetadata catalog,
            List<TeamSummary> teams
    ) {
        public TeamsResponse {
            teams = List.copyOf(teams);
        }
    }

    public record TeamResponse(
            String schemaVersion,
            String leagueCode,
            CatalogMetadata catalog,
            TeamSummary team
    ) {
    }

    public record PlayersResponse(
            String schemaVersion,
            String leagueCode,
            CatalogMetadata catalog,
            AppliedFilters filters,
            List<PlayerSummary> players
    ) {
        public PlayersResponse {
            players = List.copyOf(players);
        }
    }

    public record PlayerResponse(
            String schemaVersion,
            String leagueCode,
            CatalogMetadata catalog,
            PlayerDetail player
    ) {
    }

    public record CatalogMetadata(
            String catalogSchemaVersion,
            String catalogVersion,
            String catalogHashAlgorithm,
            String catalogHash,
            String championPoolVersion,
            List<ResourceMetadata> sourceResources
    ) {
        public CatalogMetadata {
            sourceResources = List.copyOf(sourceResources);
        }
    }

    public record ResourceMetadata(
            String role,
            String version,
            String rawSha256,
            String snapshotAt,
            String researchAsOf,
            String dataCutoff
    ) {
    }

    public record CatalogCounts(
            int teams,
            int players,
            int uniquePlayerIds,
            int teamHistoryRows,
            int teamAchievementRows,
            int individualAwardRows,
            int sourceRows,
            int authoredProficiencies,
            int neutralFallbackKeys,
            int playersWithMajorHonorsListed
    ) {
    }

    public record DataSemantics(
            String contract,
            String career,
            String honors,
            String prizeMoney,
            String age
    ) {
    }

    public record Limitations(
            boolean currentLckOnly,
            boolean startersOnly,
            boolean substitutesIncluded,
            boolean salaryIncluded,
            boolean marketValueIncluded,
            boolean overallRatingIncluded,
            boolean mutableCareerStateIncluded,
            boolean affectsGameplayOrRandomIdentity
    ) {
    }

    public record TeamSummary(
            String teamCode,
            int starterCount,
            List<LineupPlayer> lineup
    ) {
        public TeamSummary {
            teamCode = required(teamCode, "teamCode");
            lineup = List.copyOf(lineup);
        }
    }

    public record LineupPlayer(String playerId, String nickname, Position position) {
        public LineupPlayer {
            playerId = required(playerId, "playerId");
            nickname = required(nickname, "nickname");
            Objects.requireNonNull(position, "position");
        }
    }

    public record PlayerSummary(
            String playerId,
            String nickname,
            String currentTeamCode,
            Position position,
            List<String> nationality,
            String birthDate,
            String contractEndDate,
            String contractStatus
    ) {
        public PlayerSummary {
            playerId = required(playerId, "playerId");
            nickname = required(nickname, "nickname");
            currentTeamCode = required(currentTeamCode, "currentTeamCode");
            Objects.requireNonNull(position, "position");
            nationality = List.copyOf(nationality);
            birthDate = required(birthDate, "birthDate");
            contractEndDate = required(contractEndDate, "contractEndDate");
            contractStatus = required(contractStatus, "contractStatus");
        }
    }

    public record AppliedFilters(String teamCode, Position position) {
    }

    public record PlayerDetail(
            PlayerSummary summary,
            SnapshotSemantics snapshotSemantics,
            PersonalInformation personal,
            ContractInformation contract,
            CareerInformation career,
            HonorsInformation honors,
            PrizeMoneyInformation careerPrizeMoney,
            DataQuality dataQuality,
            RatingProfile ratings,
            ProficiencyProfile championProficiency,
            List<SourceCitation> sources
    ) {
        public PlayerDetail {
            sources = List.copyOf(sources);
        }
    }

    public record SnapshotSemantics(
            String snapshotAt,
            String ageMeaning,
            String contractDaysMeaning,
            String prizeMoneyMeaning
    ) {
    }

    public record PersonalInformation(
            String legalName,
            String birthDate,
            int ageAtSnapshot,
            List<String> nationality
    ) {
        public PersonalInformation {
            nationality = List.copyOf(nationality);
        }
    }

    public record ContractInformation(
            String endDate,
            int daysRemainingAtSnapshot,
            String status,
            String sourceType,
            String sourceSnapshotAt,
            String checkedAt
    ) {
    }

    public record CareerInformation(
            String debutDate,
            BigDecimal yearsActiveAtSnapshot,
            List<TeamHistory> teamHistory,
            String coverage
    ) {
        public CareerInformation {
            teamHistory = List.copyOf(teamHistory);
        }
    }

    public record TeamHistory(
            String team,
            String from,
            String to,
            Position role,
            String datePrecision
    ) {
    }

    public record HonorsInformation(
            List<TeamAchievement> teamAchievements,
            List<IndividualAward> individualAwards,
            String coverage
    ) {
        public HonorsInformation {
            teamAchievements = List.copyOf(teamAchievements);
            individualAwards = List.copyOf(individualAwards);
        }
    }

    public record TeamAchievement(
            String season,
            String competition,
            String team,
            String result,
            String sourceUrl
    ) {
    }

    public record IndividualAward(
            String season,
            String award,
            String competition,
            String sourceUrl
    ) {
    }

    public record PrizeMoneyInformation(
            BigDecimal amountUsd,
            String currency,
            String status,
            String sourceType,
            String checkedAt,
            String meaning
    ) {
    }

    public record DataQuality(
            String personal,
            String contract,
            String career,
            String honors,
            String prizeMoney
    ) {
    }

    public record RatingProfile(
            int scaleMin,
            int scaleMax,
            String resourceVersion,
            List<RatingAttribute> attributes
    ) {
        public RatingProfile {
            attributes = List.copyOf(attributes);
        }
    }

    public record RatingAttribute(
            String key,
            String skill,
            String displayNameKo,
            int value
    ) {
    }

    public record ProficiencyProfile(
            int scaleMin,
            int scaleMax,
            int neutralFallback,
            boolean sparseOverridesOnly,
            String omittedLegalRoleBehavior,
            String resourceVersion,
            int authoredEntryCount,
            List<ChampionProficiency> authoredEntries
    ) {
        public ProficiencyProfile {
            authoredEntries = List.copyOf(authoredEntries);
        }
    }

    public record ChampionProficiency(
            String championId,
            String displayNameKo,
            String displayNameEn,
            String portraitUrl,
            Position position,
            int value
    ) {
    }

    public record SourceCitation(
            String type,
            String path,
            String url,
            String checkedAt,
            String sourceSnapshotAt
    ) {
    }

    public record ErrorResponse(String schemaVersion, String code, String field, String message) {
        public ErrorResponse {
            schemaVersion = required(schemaVersion, "schemaVersion");
            code = required(code, "code");
            message = required(message, "message");
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
