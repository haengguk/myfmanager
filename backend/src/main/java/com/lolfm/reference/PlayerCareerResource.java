package com.lolfm.reference;

import com.lolfm.domain.Position;
import com.lolfm.player.PlayerId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Immutable projection of one validated player entry from the authored career resource. */
public record PlayerCareerResource(
        PlayerId playerId,
        String nickname,
        String teamCode,
        Position position,
        String snapshotAt,
        Personal personal,
        Contract contract,
        Career career,
        Honors honors,
        CareerPrizeMoney careerPrizeMoney,
        List<Source> sources,
        DataQuality dataQuality
) {
    public PlayerCareerResource {
        Objects.requireNonNull(playerId, "playerId");
        nickname = required(nickname, "nickname");
        teamCode = required(teamCode, "teamCode");
        Objects.requireNonNull(position, "position");
        snapshotAt = required(snapshotAt, "snapshotAt");
        Objects.requireNonNull(personal, "personal");
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(career, "career");
        Objects.requireNonNull(honors, "honors");
        Objects.requireNonNull(careerPrizeMoney, "careerPrizeMoney");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        Objects.requireNonNull(dataQuality, "dataQuality");
    }

    public record Personal(
            String legalName,
            String birthDate,
            int ageAsOfSnapshot,
            List<String> nationality
    ) {
        public Personal {
            legalName = required(legalName, "legalName");
            birthDate = required(birthDate, "birthDate");
            nationality = requiredStrings(nationality, "nationality");
        }
    }

    public record Contract(
            String endDate,
            int daysRemainingAsOfSnapshot,
            String status,
            String sourceType,
            String sourceSnapshotAt,
            String checkedAt
    ) {
        public Contract {
            endDate = required(endDate, "endDate");
            status = required(status, "status");
            sourceType = required(sourceType, "sourceType");
            sourceSnapshotAt = required(sourceSnapshotAt, "sourceSnapshotAt");
            checkedAt = required(checkedAt, "checkedAt");
        }
    }

    public record Career(
            String debutDate,
            BigDecimal yearsActiveAsOfSnapshot,
            List<TeamHistory> teamHistory,
            String coverage
    ) {
        public Career {
            debutDate = required(debutDate, "debutDate");
            Objects.requireNonNull(yearsActiveAsOfSnapshot, "yearsActiveAsOfSnapshot");
            teamHistory = List.copyOf(Objects.requireNonNull(teamHistory, "teamHistory"));
            coverage = required(coverage, "coverage");
        }
    }

    public record TeamHistory(
            String team,
            String from,
            String to,
            Position role,
            String datePrecision
    ) {
        public TeamHistory {
            team = required(team, "team");
            from = required(from, "from");
            to = nullable(to, "to");
            Objects.requireNonNull(role, "role");
            datePrecision = required(datePrecision, "datePrecision");
        }
    }

    public record Honors(
            List<TeamAchievement> teamAchievements,
            List<IndividualAward> individualAwards,
            String coverage
    ) {
        public Honors {
            teamAchievements = List.copyOf(Objects.requireNonNull(
                    teamAchievements, "teamAchievements"));
            individualAwards = List.copyOf(Objects.requireNonNull(
                    individualAwards, "individualAwards"));
            coverage = required(coverage, "coverage");
        }
    }

    public record TeamAchievement(
            String season,
            String competition,
            String team,
            String result,
            String sourceUrl
    ) {
        public TeamAchievement {
            season = required(season, "season");
            competition = required(competition, "competition");
            team = required(team, "team");
            result = required(result, "result");
            sourceUrl = nullable(sourceUrl, "sourceUrl");
        }
    }

    public record IndividualAward(
            String season,
            String award,
            String competition,
            String sourceUrl
    ) {
        public IndividualAward {
            season = required(season, "season");
            award = required(award, "award");
            competition = required(competition, "competition");
            sourceUrl = nullable(sourceUrl, "sourceUrl");
        }
    }

    public record CareerPrizeMoney(
            BigDecimal amountUsd,
            String status,
            String sourceType,
            String checkedAt
    ) {
        public CareerPrizeMoney {
            Objects.requireNonNull(amountUsd, "amountUsd");
            status = required(status, "status");
            sourceType = required(sourceType, "sourceType");
            checkedAt = required(checkedAt, "checkedAt");
        }
    }

    public record Source(
            String type,
            String path,
            String url,
            String checkedAt,
            String sourceSnapshotAt
    ) {
        public Source {
            type = required(type, "type");
            path = nullable(path, "path");
            url = nullable(url, "url");
            checkedAt = required(checkedAt, "checkedAt");
            sourceSnapshotAt = nullable(sourceSnapshotAt, "sourceSnapshotAt");
        }
    }

    public record DataQuality(
            String personal,
            String contract,
            String career,
            String honors,
            String prizeMoney
    ) {
        public DataQuality {
            personal = required(personal, "personal");
            contract = required(contract, "contract");
            career = required(career, "career");
            honors = required(honors, "honors");
            prizeMoney = required(prizeMoney, "prizeMoney");
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private static String nullable(String value, String field) {
        return value == null ? null : required(value, field);
    }

    private static List<String> requiredStrings(List<String> values, String field) {
        List<String> copy = Objects.requireNonNull(values, field).stream()
                .map(value -> required(value, field)).toList();
        if (copy.isEmpty()) throw new IllegalArgumentException(field + " must not be empty");
        return List.copyOf(copy);
    }
}
