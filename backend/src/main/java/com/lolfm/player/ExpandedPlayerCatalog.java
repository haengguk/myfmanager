package com.lolfm.player;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.career.CompetitionRosterSnapshot;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.Position;
import java.util.*;
import org.springframework.stereotype.Component;

/** Immutable player directory. Team roster size constraints belong to lineups, not this directory. */
@Component
public final class ExpandedPlayerCatalog {
    public static final String VERSION = "EXPANDED_PLAYER_DIRECTORY_V1";
    public static final String PLACEMENT_POLICY = "PRESERVE_280_STARTERS_EXPLICIT_RESERVE_SELECTION_V1";
    public static final String MOVEMENT_POLICY = "EXPLICIT_ASSOCIATED_ORGANIZATIONS_GAME_POLICY_V1";
    private final Map<String, Definition> players;
    private final Map<String, Organization> organizations;
    private final Map<String, List<String>> initialLineups;
    private final List<JsonNode> normalizationChanges;

    public record Organization(String organizationId, String displayName, String competitiveTeam, String kind) {}
    public record Definition(String playerId, String nickname, Position position,
                             CompetitionRosterSnapshot.Starter gameplay, boolean provisional,
                             String initialOrganizationId, String initialOwnerTeam, String initialSquad,
                             String eligibilityReason, String detailsJson) {}

    public ExpandedPlayerCatalog(ObjectMapper mapper, GlobalTeamRosterCatalog starters, ChampionCatalog champions) {
        var loaded = new LinkedHashMap<String, Definition>();
        var orgs = new LinkedHashMap<String, Organization>();
        var lineups = new LinkedHashMap<String, List<String>>();
        for (String league : starters.leagueCodes()) {
            for (String code : starters.league(league).ratings().teamCodes().stream().sorted().toList()) {
                var team = starters.snapshot(new GlobalTeamRosterCatalog.TeamKey(league, code));
                String token = CompetitionRosterSnapshot.token(team.team());
                orgs.put(token, new Organization(token, code, token, "CLUB"));
                var roster = CompetitionRosterSnapshot.capture(team);
                lineups.put(token, roster.players().stream().map(CompetitionRosterSnapshot.Starter::playerId).toList());
                for (var player : roster.players()) {
                    var details = starters.league(league).career().player(new PlayerId(player.playerId()));
                    put(loaded, new Definition(player.playerId(), player.nickname(), player.position(), player, false,
                            token, token, "FIRST_TEAM", null, details.toString()));
                }
            }
        }
        if (loaded.size() != 280 || lineups.size() != 56) throw new IllegalStateException("INITIAL_DIRECTORY_CARDINALITY");
        try (var input = getClass().getResourceAsStream("/rosters/expanded-player-directory-v1.json")) {
            var root = mapper.readTree(Objects.requireNonNull(input));
            if (!VERSION.equals(root.path("version").asText())
                    || !PLACEMENT_POLICY.equals(root.path("initialPlacementPolicy").asText())
                    || !MOVEMENT_POLICY.equals(root.path("developmentMovementPolicy").asText()))
                throw new IllegalStateException("EXPANDED_DIRECTORY_POLICY");
            for (var node : root.path("organizations")) {
                var organization = mapper.treeToValue(node, Organization.class);
                if (organization.competitiveTeam() != null && !lineups.containsKey(organization.competitiveTeam()))
                    throw new IllegalStateException("UNKNOWN_ASSOCIATED_COMPETITION_TEAM");
                orgs.put(organization.organizationId(), organization);
            }
            for (var node : root.path("players")) {
                String id = node.path("playerId").asText(); new PlayerId(id);
                Position role = Position.valueOf(node.path("position").asText());
                var ratings = new EnumMap<PlayerSkill, Integer>(PlayerSkill.class);
                node.path("ratings").fields().forEachRemaining(e -> {
                    if (!e.getValue().isIntegralNumber()) throw new IllegalStateException("INVALID_RATING_TYPE");
                    ratings.put(PlayerSkill.valueOf(e.getKey()), e.getValue().intValue());
                });
                new PlayerRatings(role, ratings);
                var legacy = node.path("legacyAttributes");
                if (legacy.path("mechanics").asInt() != ratings.get(PlayerSkill.MECHANICS)
                        || legacy.path("aggression").asInt() != ratings.get(PlayerSkill.DECISION_MAKING)
                        || legacy.path("teamfighting").asInt() != ratings.get(PlayerSkill.COMBAT_EXECUTION)
                        || legacy.path("farming").asInt() != ratings.getOrDefault(PlayerSkill.FARMING, 14))
                    throw new IllegalStateException("LEGACY_RATING_DERIVATION");
                var authored = node.path("championProficiencies");
                if (authored.path("defaultLegalRoleValue").asInt() != 14
                        || !role.name().equals(authored.path("position").asText())) throw new IllegalStateException("PROFICIENCY_DEFAULT_ROLE");
                var overrides = new HashMap<String, Integer>();
                authored.path("overrides").fields().forEachRemaining(e -> {
                    if (!e.getValue().isIntegralNumber() || !champions.get(new ChampionId(e.getKey())).supportedPositions().contains(role))
                        throw new IllegalStateException("ILLEGAL_PROFICIENCY_ROLE");
                    overrides.put(e.getKey(), e.getValue().intValue());
                });
                var proficiencies = champions.forPosition(role).stream().map(c -> new CompetitionRosterSnapshot.Proficiency(
                        c.id().value(), role, overrides.getOrDefault(c.id().value(), 14)))
                        .sorted(Comparator.comparing(CompetitionRosterSnapshot.Proficiency::championId)).toList();
                if (proficiencies.size() != authored.path("legalRoleChampionCount").asInt()) throw new IllegalStateException("LEGAL_ROLE_CARDINALITY");
                String owner = nullable(node, "initialOwnerTeam"), organization = nullable(node, "initialOrganizationId");
                if (owner != null && !lineups.containsKey(owner) || organization != null && !orgs.containsKey(organization))
                    throw new IllegalStateException("UNKNOWN_PLAYER_ORGANIZATION");
                var gameplay = new CompetitionRosterSnapshot.Starter(id, node.path("nickname").asText(), role, ratings, proficiencies);
                put(loaded, new Definition(id, gameplay.nickname(), role, gameplay, true, organization, owner,
                        node.path("initialSquad").asText(), nullable(node, "eligibilityReason"), node.path("details").toString()));
            }
            normalizationChanges = mapper.convertValue(root.path("normalizationChanges"), new com.fasterxml.jackson.core.type.TypeReference<List<JsonNode>>() {});
        } catch (java.io.IOException e) { throw new IllegalStateException("EXPANDED_DIRECTORY_LOAD", e); }
        if (loaded.size() != 460) throw new IllegalStateException("EXPANDED_DIRECTORY_CARDINALITY");
        players = Collections.unmodifiableMap(loaded); organizations = Collections.unmodifiableMap(orgs); initialLineups = Collections.unmodifiableMap(lineups);
    }
    private static String nullable(JsonNode node, String key) { return node.path(key).isNull() ? null : node.path(key).asText(); }
    private static void put(Map<String, Definition> target, Definition player) {
        if (target.putIfAbsent(player.playerId(), player) != null) throw new IllegalStateException("DUPLICATE_DIRECTORY_PLAYER:" + player.playerId());
    }
    public Map<String, Definition> players() { return players; }
    public Map<String, Organization> organizations() { return organizations; }
    public Map<String, List<String>> initialLineups() { return initialLineups; }
    public List<JsonNode> normalizationChanges() { return normalizationChanges.stream().map(node -> (JsonNode) node.deepCopy()).toList(); }
}
