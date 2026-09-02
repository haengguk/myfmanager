package com.lolfm.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionDefinition;
import com.lolfm.champion.ChampionId;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.Position;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.ChampionProficiencyEntry;
import com.lolfm.player.PlayerId;
import com.lolfm.player.PlayerIdentity;
import com.lolfm.player.PlayerIdentityCatalog;
import com.lolfm.player.PlayerRatingCatalog;
import com.lolfm.player.PlayerRatingResource;
import com.lolfm.player.PlayerRatingResourceLoader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** Immutable LCK reference catalog joined exclusively by stable {@link PlayerId}. */
@Component
public final class TeamPlayerInformationCatalog {
    public static final String LEAGUE_CODE = "LCK";
    public static final String CATALOG_SCHEMA_VERSION =
            "TEAM_AND_PLAYER_INFORMATION_CATALOG_V1";
    public static final String CATALOG_VERSION =
            "lck-team-and-player-information-2026-08-24-v1";
    public static final String HASH_ALGORITHM = "SHA-256";

    private final PlayerIdentityCatalog identities;
    private final PlayerRatingCatalog ratings;
    private final ChampionProficiencyCatalog proficiencies;
    private final ChampionCatalog champions;
    private final PlayerCareerResourceLoader.LoadedResource career;
    private final List<PlayerInformation> players;
    private final Map<PlayerId, PlayerInformation> byPlayerId;
    private final Map<String, TeamInformation> byTeamCode;
    private final CatalogCounts counts;
    private final CatalogProvenance provenance;

    @org.springframework.beans.factory.annotation.Autowired
    public TeamPlayerInformationCatalog(
            ObjectMapper mapper,
            PlayerIdentityCatalog identities,
            PlayerRatingCatalog ratings,
            ChampionProficiencyCatalog proficiencies,
            ChampionCatalog champions
    ) {
        this(identities, ratings, proficiencies, champions,
                PlayerCareerResourceLoader.load(mapper,
                        PlayerCareerResourceLoader.class.getResourceAsStream(
                                PlayerCareerResourceLoader.RESOURCE)));
    }

    public TeamPlayerInformationCatalog(
            PlayerIdentityCatalog identities,
            PlayerRatingCatalog ratings,
            ChampionProficiencyCatalog proficiencies,
            ChampionCatalog champions,
            PlayerCareerResourceLoader.LoadedResource career
    ) {
        this.identities = Objects.requireNonNull(identities, "identities");
        this.ratings = Objects.requireNonNull(ratings, "ratings");
        this.proficiencies = Objects.requireNonNull(proficiencies, "proficiencies");
        this.champions = Objects.requireNonNull(champions, "champions");
        this.career = Objects.requireNonNull(career, "career");
        validateSubjectSets();

        Map<PlayerId, PlayerCareerResource> careerById = career.players().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        PlayerCareerResource::playerId, value -> value));
        Map<PlayerId, List<ChampionProficiencyEntry>> entriesByPlayer = new HashMap<>();
        for (ChampionProficiencyEntry entry : proficiencies.authoredEntries()) {
            entriesByPlayer.computeIfAbsent(entry.playerId(), ignored -> new ArrayList<>())
                    .add(entry);
        }
        Comparator<ChampionProficiencyEntry> proficiencyOrder = Comparator
                .comparingInt(ChampionProficiencyEntry::value).reversed()
                .thenComparing(value -> value.championRoleKey().championId().value());

        List<PlayerInformation> orderedPlayers = new ArrayList<>();
        for (PlayerIdentity identity : identities.all()) {
            PlayerRatingResource rating = ratings.get(identity.playerId());
            PlayerCareerResource careerPlayer = careerById.get(identity.playerId());
            List<ChampionProficiencyEntry> authored = entriesByPlayer
                    .getOrDefault(identity.playerId(), List.of()).stream()
                    .sorted(proficiencyOrder).toList();
            validateBinding(identity, rating, careerPlayer, authored);
            orderedPlayers.add(new PlayerInformation(identity, rating, careerPlayer, authored));
        }
        orderedPlayers.sort(playerOrder());
        players = List.copyOf(orderedPlayers);
        byPlayerId = players.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                value -> value.identity().playerId(), value -> value));

        TreeMap<String, TeamInformation> teams = new TreeMap<>();
        for (String teamCode : identities.teamCodes()) {
            List<PlayerInformation> lineup = players.stream()
                    .filter(value -> value.identity().ratingKey().teamCode().equals(teamCode))
                    .sorted(Comparator.comparingInt(value ->
                            value.identity().ratingKey().position().ordinal()))
                    .toList();
            validateTeam(teamCode, lineup);
            teams.put(teamCode, new TeamInformation(teamCode, lineup));
        }
        byTeamCode = Collections.unmodifiableMap(teams);
        counts = new CatalogCounts(byTeamCode.size(), players.size(), byPlayerId.size(),
                career.counts().teamHistoryCount(), career.counts().teamAchievementCount(),
                career.counts().individualAwardCount(), career.counts().sourceCount(),
                proficiencies.metrics().authoredOverrideCount(),
                proficiencies.metrics().neutralFallbackKeyCount(),
                career.counts().playersWithMajorHonors());
        provenance = createProvenance();
    }

    public static TeamPlayerInformationCatalog loadDefault() {
        ObjectMapper mapper = new ObjectMapper();
        PlayerIdentityCatalog identities = PlayerIdentityCatalog.loadDefault();
        PlayerRatingCatalog ratings = PlayerRatingCatalog.loadDefault(identities);
        ChampionCatalog champions = new ChampionCatalog(mapper);
        ChampionProficiencyCatalog proficiencies = ChampionProficiencyCatalog.loadDefault(
                ratings, champions);
        return new TeamPlayerInformationCatalog(identities, ratings, proficiencies, champions,
                PlayerCareerResourceLoader.load(mapper,
                        PlayerCareerResourceLoader.class.getResourceAsStream(
                                PlayerCareerResourceLoader.RESOURCE)));
    }

    public CatalogProvenance provenance() {
        return provenance;
    }

    public CatalogCounts counts() {
        return counts;
    }

    public PlayerCareerResourceLoader.Semantics careerSemantics() {
        return career.semantics();
    }

    public List<TeamInformation> teams() {
        return List.copyOf(byTeamCode.values());
    }

    public Optional<TeamInformation> findTeam(String teamCode) {
        return Optional.ofNullable(byTeamCode.get(Objects.requireNonNull(teamCode, "teamCode")));
    }

    public List<PlayerInformation> players() {
        return players;
    }

    public List<PlayerInformation> players(String teamCode, Position position) {
        return players.stream()
                .filter(value -> teamCode == null
                        || value.identity().ratingKey().teamCode().equals(teamCode))
                .filter(value -> position == null
                        || value.identity().ratingKey().position() == position)
                .toList();
    }

    public Optional<PlayerInformation> findPlayer(PlayerId playerId) {
        return Optional.ofNullable(byPlayerId.get(Objects.requireNonNull(playerId, "playerId")));
    }

    public ChampionDefinition champion(ChampionId championId) {
        return champions.get(Objects.requireNonNull(championId, "championId"));
    }

    private void validateSubjectSets() {
        Set<PlayerId> identitySubjects = identities.all().stream()
                .map(PlayerIdentity::playerId).collect(java.util.stream.Collectors.toSet());
        Set<PlayerId> ratingSubjects = ratings.all().stream()
                .map(value -> identities.get(value.playerKey()).playerId())
                .collect(java.util.stream.Collectors.toSet());
        Set<PlayerId> proficiencySubjects = proficiencies.all().keySet();
        Set<PlayerId> careerSubjects = career.players().stream()
                .map(PlayerCareerResource::playerId).collect(java.util.stream.Collectors.toSet());
        if (identitySubjects.size() != PlayerCareerResourceLoader.PLAYER_COUNT
                || !identitySubjects.equals(ratingSubjects)
                || !identitySubjects.equals(proficiencySubjects)
                || !identitySubjects.equals(careerSubjects)) {
            throw new IllegalStateException("Information catalog PlayerId subject mismatch; identity="
                    + identitySubjects.size() + " rating=" + ratingSubjects.size()
                    + " proficiency=" + proficiencySubjects.size()
                    + " career=" + careerSubjects.size());
        }
        Set<String> careerTeams = career.players().stream()
                .map(PlayerCareerResource::teamCode).collect(java.util.stream.Collectors.toSet());
        if (!identities.teamCodes().equals(ratings.teamCodes())
                || !identities.teamCodes().equals(careerTeams)) {
            throw new IllegalStateException("Information catalog team-code subject mismatch");
        }
    }

    private void validateBinding(
            PlayerIdentity identity,
            PlayerRatingResource rating,
            PlayerCareerResource careerPlayer,
            List<ChampionProficiencyEntry> authored
    ) {
        if (careerPlayer == null || !identity.nickname().equals(rating.nickname())
                || !identity.nickname().equals(careerPlayer.nickname())
                || !identity.ratingKey().teamCode().equals(careerPlayer.teamCode())
                || identity.ratingKey().position() != careerPlayer.position()) {
            throw new IllegalStateException(
                    "Information catalog current identity mismatch: " + identity.playerId());
        }
        if (rating.ratings().asMap().size() != 12
                || !rating.ratings().asMap().keySet().equals(
                        PlayerSkill.forPosition(identity.ratingKey().position()))
                || rating.ratings().asMap().values().stream().anyMatch(
                        value -> value < PlayerRatings.MIN || value > PlayerRatings.MAX)) {
            throw new IllegalStateException(
                    "Information catalog rating contract mismatch: " + identity.playerId());
        }
        Set<String> championIds = new HashSet<>();
        for (ChampionProficiencyEntry entry : authored) {
            if (!entry.playerId().equals(identity.playerId())
                    || !entry.sourceRatingKey().equals(identity.ratingKey())
                    || entry.championRoleKey().position() != identity.ratingKey().position()
                    || !championIds.add(entry.championRoleKey().championId().value())) {
                throw new IllegalStateException(
                        "Information catalog proficiency binding mismatch: " + identity.playerId());
            }
            champions.get(entry.championRoleKey().championId());
        }
    }

    private static void validateTeam(String teamCode, List<PlayerInformation> lineup) {
        if (lineup.size() != PlayerCareerResourceLoader.STARTERS_PER_TEAM
                || !lineup.stream().map(value -> value.identity().ratingKey().position())
                .collect(java.util.stream.Collectors.toSet())
                .equals(EnumSet.allOf(Position.class))) {
            throw new IllegalStateException(
                    "Information catalog team lineup mismatch: " + teamCode);
        }
    }

    private CatalogProvenance createProvenance() {
        List<ResourceProvenance> resources = List.of(
                new ResourceProvenance("PLAYER_IDENTITY", identities.version(),
                        identities.resourceSha256(), identities.snapshotAt(), null, null),
                new ResourceProvenance("PLAYER_RATING", ratings.version(),
                        ratings.resourceSha256(), ratings.snapshotAt(), null,
                        ratings.dataCutoff()),
                new ResourceProvenance("CHAMPION_PROFICIENCY", proficiencies.version(),
                        proficiencies.resourceSha256(), null, proficiencies.researchAsOf(), null),
                new ResourceProvenance("PLAYER_CAREER", career.version(),
                        career.resourceSha256(), career.snapshotAt(), null, null));
        String canonical = canonicalCatalogIdentity(resources);
        return new CatalogProvenance(CATALOG_SCHEMA_VERSION, CATALOG_VERSION, LEAGUE_CODE,
                HASH_ALGORITHM, sha256(canonical), champions.championPoolVersion(), resources);
    }

    private String canonicalCatalogIdentity(List<ResourceProvenance> resources) {
        StringBuilder canonical = new StringBuilder()
                .append("catalogSchemaVersion=").append(CATALOG_SCHEMA_VERSION).append('\n')
                .append("catalogVersion=").append(CATALOG_VERSION).append('\n')
                .append("leagueCode=").append(LEAGUE_CODE).append('\n')
                .append("championPoolVersion=").append(champions.championPoolVersion()).append('\n');
        resources.forEach(value -> canonical.append("resource=")
                .append(value.role()).append('|').append(value.version()).append('|')
                .append(value.rawSha256()).append('|').append(value.snapshotAt()).append('|')
                .append(value.researchAsOf()).append('|').append(value.dataCutoff()).append('\n'));
        for (PlayerInformation player : players) {
            PlayerIdentity identity = player.identity();
            canonical.append("player=").append(identity.playerId()).append('|')
                    .append(identity.ratingKey().teamCode()).append('|')
                    .append(identity.ratingKey().position()).append('|')
                    .append(identity.nickname()).append('\n');
            for (PlayerSkill skill : PlayerSkill.orderedForPosition(
                    identity.ratingKey().position())) {
                canonical.append("rating=").append(identity.playerId()).append('|')
                        .append(PlayerRatingResourceLoader.jsonName(skill)).append('|')
                        .append(player.rating().ratings().get(skill)).append('\n');
            }
            for (ChampionProficiencyEntry entry : player.authoredProficiencies()) {
                ChampionDefinition champion = champions.get(entry.championRoleKey().championId());
                canonical.append("proficiency=").append(identity.playerId()).append('|')
                        .append(champion.id()).append('|')
                        .append(entry.championRoleKey().position()).append('|')
                        .append(entry.value()).append('|').append(champion.displayNameKo())
                        .append('|').append(champion.displayNameEn()).append('\n');
            }
        }
        return canonical.toString();
    }

    private static Comparator<PlayerInformation> playerOrder() {
        return Comparator.comparing((PlayerInformation value) ->
                        value.identity().ratingKey().teamCode())
                .thenComparingInt(value ->
                        value.identity().ratingKey().position().ordinal());
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    public record PlayerInformation(
            PlayerIdentity identity,
            PlayerRatingResource rating,
            PlayerCareerResource career,
            List<ChampionProficiencyEntry> authoredProficiencies
    ) {
        public PlayerInformation {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(rating, "rating");
            Objects.requireNonNull(career, "career");
            authoredProficiencies = List.copyOf(authoredProficiencies);
        }
    }

    public record TeamInformation(String teamCode, List<PlayerInformation> lineup) {
        public TeamInformation {
            teamCode = Objects.requireNonNull(teamCode, "teamCode");
            lineup = List.copyOf(lineup);
        }
    }

    public record ResourceProvenance(
            String role,
            String version,
            String rawSha256,
            String snapshotAt,
            String researchAsOf,
            String dataCutoff
    ) {
    }

    public record CatalogProvenance(
            String catalogSchemaVersion,
            String catalogVersion,
            String leagueCode,
            String catalogHashAlgorithm,
            String catalogHash,
            String championPoolVersion,
            List<ResourceProvenance> resources
    ) {
        public CatalogProvenance {
            resources = List.copyOf(resources);
        }
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
}
