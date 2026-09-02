package com.lolfm.application;

import com.lolfm.champion.ChampionDefinition;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.Position;
import com.lolfm.dto.TeamPlayerInformationApiV1Dtos;
import com.lolfm.player.ChampionProficiencyEntry;
import com.lolfm.player.PlayerIdentity;
import com.lolfm.player.PlayerRatingResourceLoader;
import com.lolfm.reference.PlayerCareerResource;
import com.lolfm.reference.TeamPlayerInformationCatalog;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Deterministic field-by-field projection from the immutable reference catalog. */
@Component
public final class TeamPlayerInformationApiV1ResponseMapper {
    private static final String CONTRACT_DAYS_MEANING =
            "Days remaining at the 2026-08-24 data snapshot; not recalculated from wall-clock time.";

    private final TeamPlayerInformationCatalog catalog;
    private final TeamPlayerInformationApiV1Dtos.CatalogMetadata catalogMetadata;
    private final String ratingVersion;
    private final String proficiencyVersion;

    public TeamPlayerInformationApiV1ResponseMapper(TeamPlayerInformationCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.catalogMetadata = metadata(catalog.provenance());
        this.ratingVersion = resourceVersion("PLAYER_RATING");
        this.proficiencyVersion = resourceVersion("CHAMPION_PROFICIENCY");
    }

    public TeamPlayerInformationApiV1Dtos.MetadataResponse metadata() {
        TeamPlayerInformationCatalog.CatalogCounts counts = catalog.counts();
        var semantics = catalog.careerSemantics();
        return new TeamPlayerInformationApiV1Dtos.MetadataResponse(
                TeamPlayerInformationApiV1Dtos.METADATA_SCHEMA,
                TeamPlayerInformationCatalog.LEAGUE_CODE,
                catalogMetadata,
                new TeamPlayerInformationApiV1Dtos.CatalogCounts(
                        counts.teams(), counts.players(), counts.uniquePlayerIds(),
                        counts.teamHistoryRows(), counts.teamAchievementRows(),
                        counts.individualAwardRows(), counts.sourceRows(),
                        counts.authoredProficiencies(), counts.neutralFallbackKeys(),
                        counts.playersWithMajorHonorsListed()),
                new TeamPlayerInformationApiV1Dtos.DataSemantics(
                        semantics.contract(), semantics.career(), semantics.honors(),
                        semantics.prizeMoney(), semantics.age()),
                new TeamPlayerInformationApiV1Dtos.Limitations(
                        true, true, false, false, false, false, false, false));
    }

    public TeamPlayerInformationApiV1Dtos.TeamsResponse teams() {
        return new TeamPlayerInformationApiV1Dtos.TeamsResponse(
                TeamPlayerInformationApiV1Dtos.TEAMS_SCHEMA,
                TeamPlayerInformationCatalog.LEAGUE_CODE,
                catalogMetadata,
                catalog.teams().stream().map(this::teamSummary).toList());
    }

    public TeamPlayerInformationApiV1Dtos.TeamResponse team(
            TeamPlayerInformationCatalog.TeamInformation team
    ) {
        return new TeamPlayerInformationApiV1Dtos.TeamResponse(
                TeamPlayerInformationApiV1Dtos.TEAM_SCHEMA,
                TeamPlayerInformationCatalog.LEAGUE_CODE,
                catalogMetadata,
                teamSummary(team));
    }

    public TeamPlayerInformationApiV1Dtos.PlayersResponse players(
            String teamCode,
            Position position,
            List<TeamPlayerInformationCatalog.PlayerInformation> players
    ) {
        return new TeamPlayerInformationApiV1Dtos.PlayersResponse(
                TeamPlayerInformationApiV1Dtos.PLAYERS_SCHEMA,
                TeamPlayerInformationCatalog.LEAGUE_CODE,
                catalogMetadata,
                new TeamPlayerInformationApiV1Dtos.AppliedFilters(teamCode, position),
                players.stream().map(this::playerSummary).toList());
    }

    public TeamPlayerInformationApiV1Dtos.PlayerResponse player(
            TeamPlayerInformationCatalog.PlayerInformation player
    ) {
        return new TeamPlayerInformationApiV1Dtos.PlayerResponse(
                TeamPlayerInformationApiV1Dtos.PLAYER_SCHEMA,
                TeamPlayerInformationCatalog.LEAGUE_CODE,
                catalogMetadata,
                playerDetail(player));
    }

    private TeamPlayerInformationApiV1Dtos.CatalogMetadata metadata(
            TeamPlayerInformationCatalog.CatalogProvenance provenance
    ) {
        return new TeamPlayerInformationApiV1Dtos.CatalogMetadata(
                provenance.catalogSchemaVersion(), provenance.catalogVersion(),
                provenance.catalogHashAlgorithm(), provenance.catalogHash(),
                provenance.championPoolVersion(), provenance.resources().stream()
                .map(value -> new TeamPlayerInformationApiV1Dtos.ResourceMetadata(
                        value.role(), value.version(), value.rawSha256(), value.snapshotAt(),
                        value.researchAsOf(), value.dataCutoff())).toList());
    }

    private TeamPlayerInformationApiV1Dtos.TeamSummary teamSummary(
            TeamPlayerInformationCatalog.TeamInformation team
    ) {
        return new TeamPlayerInformationApiV1Dtos.TeamSummary(team.teamCode(),
                team.lineup().size(), team.lineup().stream().map(value -> {
                    PlayerIdentity identity = value.identity();
                    return new TeamPlayerInformationApiV1Dtos.LineupPlayer(
                            identity.playerId().value(), identity.nickname(),
                            identity.ratingKey().position());
                }).toList());
    }

    private TeamPlayerInformationApiV1Dtos.PlayerSummary playerSummary(
            TeamPlayerInformationCatalog.PlayerInformation value
    ) {
        PlayerIdentity identity = value.identity();
        PlayerCareerResource career = value.career();
        return new TeamPlayerInformationApiV1Dtos.PlayerSummary(
                identity.playerId().value(), identity.nickname(),
                identity.ratingKey().teamCode(), identity.ratingKey().position(),
                career.personal().nationality(), career.personal().birthDate(),
                career.contract().endDate(), career.contract().status());
    }

    private TeamPlayerInformationApiV1Dtos.PlayerDetail playerDetail(
            TeamPlayerInformationCatalog.PlayerInformation value
    ) {
        PlayerCareerResource source = value.career();
        return new TeamPlayerInformationApiV1Dtos.PlayerDetail(
                playerSummary(value),
                new TeamPlayerInformationApiV1Dtos.SnapshotSemantics(
                        source.snapshotAt(), catalog.careerSemantics().age(),
                        CONTRACT_DAYS_MEANING, catalog.careerSemantics().prizeMoney()),
                new TeamPlayerInformationApiV1Dtos.PersonalInformation(
                        source.personal().legalName(), source.personal().birthDate(),
                        source.personal().ageAsOfSnapshot(), source.personal().nationality()),
                new TeamPlayerInformationApiV1Dtos.ContractInformation(
                        source.contract().endDate(),
                        source.contract().daysRemainingAsOfSnapshot(),
                        source.contract().status(), source.contract().sourceType(),
                        source.contract().sourceSnapshotAt(), source.contract().checkedAt()),
                new TeamPlayerInformationApiV1Dtos.CareerInformation(
                        source.career().debutDate(),
                        source.career().yearsActiveAsOfSnapshot(),
                        source.career().teamHistory().stream().map(item ->
                                new TeamPlayerInformationApiV1Dtos.TeamHistory(
                                        item.team(), item.from(), item.to(), item.role(),
                                        item.datePrecision())).toList(),
                        source.career().coverage()),
                new TeamPlayerInformationApiV1Dtos.HonorsInformation(
                        source.honors().teamAchievements().stream().map(item ->
                                new TeamPlayerInformationApiV1Dtos.TeamAchievement(
                                        item.season(), item.competition(), item.team(),
                                        item.result(), item.sourceUrl())).toList(),
                        source.honors().individualAwards().stream().map(item ->
                                new TeamPlayerInformationApiV1Dtos.IndividualAward(
                                        item.season(), item.award(), item.competition(),
                                        item.sourceUrl())).toList(),
                        source.honors().coverage()),
                new TeamPlayerInformationApiV1Dtos.PrizeMoneyInformation(
                        source.careerPrizeMoney().amountUsd(), "USD",
                        source.careerPrizeMoney().status(),
                        source.careerPrizeMoney().sourceType(),
                        source.careerPrizeMoney().checkedAt(),
                        catalog.careerSemantics().prizeMoney()),
                new TeamPlayerInformationApiV1Dtos.DataQuality(
                        source.dataQuality().personal(), source.dataQuality().contract(),
                        source.dataQuality().career(), source.dataQuality().honors(),
                        source.dataQuality().prizeMoney()),
                ratings(value),
                proficiencies(value),
                source.sources().stream().map(item ->
                        new TeamPlayerInformationApiV1Dtos.SourceCitation(
                                item.type(), item.path(), item.url(), item.checkedAt(),
                                item.sourceSnapshotAt())).toList());
    }

    private TeamPlayerInformationApiV1Dtos.RatingProfile ratings(
            TeamPlayerInformationCatalog.PlayerInformation value
    ) {
        List<TeamPlayerInformationApiV1Dtos.RatingAttribute> attributes =
                PlayerSkill.orderedForPosition(value.identity().ratingKey().position()).stream()
                        .map(skill -> new TeamPlayerInformationApiV1Dtos.RatingAttribute(
                                PlayerRatingResourceLoader.jsonName(skill), skill.name(),
                                skill.koreanLabel(), value.rating().ratings().get(skill)))
                        .toList();
        return new TeamPlayerInformationApiV1Dtos.RatingProfile(
                PlayerRatings.MIN, PlayerRatings.MAX, ratingVersion, attributes);
    }

    private TeamPlayerInformationApiV1Dtos.ProficiencyProfile proficiencies(
            TeamPlayerInformationCatalog.PlayerInformation value
    ) {
        List<TeamPlayerInformationApiV1Dtos.ChampionProficiency> entries =
                value.authoredProficiencies().stream().map(entry ->
                        proficiency(entry)).toList();
        return new TeamPlayerInformationApiV1Dtos.ProficiencyProfile(
                1, 20, ChampionProficiencies.NEUTRAL, true,
                "Omitted legal champion-role keys resolve to neutral fallback 14.",
                proficiencyVersion, entries.size(), entries);
    }

    private TeamPlayerInformationApiV1Dtos.ChampionProficiency proficiency(
            ChampionProficiencyEntry entry
    ) {
        ChampionDefinition champion = catalog.champion(entry.championRoleKey().championId());
        return new TeamPlayerInformationApiV1Dtos.ChampionProficiency(
                champion.id().value(), champion.displayNameKo(), champion.displayNameEn(),
                champion.portraitUrl(), entry.championRoleKey().position(), entry.value());
    }

    private String resourceVersion(String role) {
        return catalog.provenance().resources().stream()
                .filter(resource -> role.equals(resource.role()))
                .findFirst().orElseThrow().version();
    }
}
