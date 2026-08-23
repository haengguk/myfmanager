package com.lolfm.application;

import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.champion.ChampionSelectionMode;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.draft.DraftActionType;
import com.lolfm.player.PlayerId;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.TeamSide;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable, match-ready application input for the frozen Match Engine V1 boundary. */
public record MatchEngineV1Input(
        String schemaVersion,
        String matchIdentity,
        TeamInput blueTeam,
        TeamInput redTeam,
        List<ChampionAssignmentInput> championAssignments,
        DraftInput finalDraft,
        long matchSeed,
        String rosterIdentityHash,
        String seriesHistoryBeforeHash,
        MatchEngineV1Policy.Requirement productionPolicy
) {
    public static final String SCHEMA = "MATCH_ENGINE_V1_INPUT_V1";
    public static final String INPUT_HASH_ALGORITHM =
            "SHA256_UTF8_EXPLICIT_ORDERED_GAMEPLAY_INPUT_LINES_TRAILING_NEWLINE_V1";
    public static final String ROSTER_HASH_ALGORITHM =
            "SHA256_UTF8_REAL_MATCH_ROSTER_ORDERED_LINES_V1";

    public MatchEngineV1Input {
        schemaVersion = MatchEngineV1Policy.required(schemaVersion, "schemaVersion");
        if (!SCHEMA.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported Match Engine V1 input schema");
        }
        matchIdentity = MatchEngineV1Policy.required(matchIdentity, "matchIdentity");
        Objects.requireNonNull(blueTeam, "blueTeam");
        Objects.requireNonNull(redTeam, "redTeam");
        championAssignments = List.copyOf(championAssignments);
        Objects.requireNonNull(finalDraft, "finalDraft");
        rosterIdentityHash = MatchEngineV1Policy.requiredHash(
                rosterIdentityHash, "rosterIdentityHash");
        seriesHistoryBeforeHash = MatchEngineV1Policy.requiredHash(
                seriesHistoryBeforeHash, "seriesHistoryBeforeHash");
        MatchEngineV1Policy.requireAuthoritative(productionPolicy);
        validateTeams(blueTeam, redTeam);
        validateAssignments(blueTeam, redTeam, championAssignments, finalDraft);
        if (!rosterIdentityHash.equals(rosterIdentityHash(blueTeam, redTeam))) {
            throw new IllegalArgumentException("MATCH_ENGINE_V1_ROSTER_IDENTITY_MISMATCH");
        }
        if (!seriesHistoryBeforeHash.equals(seriesHistoryHash(
                finalDraft.seriesGameNumber() - 1,
                Set.copyOf(finalDraft.hardFearlessExclusions())))) {
            throw new IllegalArgumentException("MATCH_ENGINE_V1_SERIES_HISTORY_IDENTITY_MISMATCH");
        }
    }

    public String inputHash() {
        return hash(canonicalGameplaySerialization());
    }

    /** Display labels are deliberately excluded from this gameplay input identity. */
    public String canonicalGameplaySerialization() {
        StringBuilder value = new StringBuilder()
                .append("inputSchema=").append(schemaVersion).append('\n')
                .append("matchIdentity=").append(matchIdentity).append('\n')
                .append("policyId=").append(productionPolicy.policyId()).append('\n')
                .append("runtimeProfileId=").append(productionPolicy.runtimeProfileId()).append('\n')
                .append("configurationHash=").append(productionPolicy.configurationHash()).append('\n')
                .append("matchSeed=").append(matchSeed).append('\n')
                .append("seriesGameNumber=").append(finalDraft.seriesGameNumber()).append('\n')
                .append("rosterIdentityHash=").append(rosterIdentityHash).append('\n')
                .append("seriesHistoryBeforeHash=").append(seriesHistoryBeforeHash).append('\n');
        appendTeam(value, blueTeam);
        appendTeam(value, redTeam);
        championAssignments.stream().sorted(Comparator.comparing(
                        assignment -> new PlayerKey(assignment.teamSide(), assignment.position()).stableId()))
                .forEach(assignment -> value.append("assignment=")
                        .append(assignment.teamSide()).append('|').append(assignment.position()).append('|')
                        .append(assignment.playerId().value()).append('|')
                        .append(assignment.championId().value()).append('\n'));
        finalDraft.appendCanonical(value);
        return value.toString();
    }

    public Team domainBlueTeam() {
        return blueTeam.toDomainTeam();
    }

    public Team domainRedTeam() {
        return redTeam.toDomainTeam();
    }

    public MatchChampionAssignments domainChampionAssignments() {
        return new MatchChampionAssignments(championAssignments.stream()
                .map(ChampionAssignmentInput::toDomain).toList(), ChampionSelectionMode.EXPLICIT);
    }

    public PlayerInput player(TeamSide side, Position position) {
        TeamInput team = side == TeamSide.BLUE ? blueTeam : redTeam;
        return team.lineup().stream().filter(value -> value.position() == position)
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Validated input lost position " + side + ":" + position));
    }

    public PlayerInput player(PlayerId playerId) {
        return allPlayers(blueTeam, redTeam).stream()
                .filter(value -> value.playerId().equals(playerId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown playerId " + playerId));
    }

    public ChampionAssignmentInput assignment(TeamSide side, Position position) {
        return championAssignments.stream()
                .filter(value -> value.teamSide() == side && value.position() == position)
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Validated input lost assignment " + side + ":" + position));
    }

    private static void validateTeams(TeamInput blue, TeamInput red) {
        if (blue.teamSide() != TeamSide.BLUE || red.teamSide() != TeamSide.RED) {
            throw new IllegalArgumentException("MATCH_ENGINE_V1_TEAM_SIDE_MISMATCH");
        }
        if (blue.teamIdentity().equals(red.teamIdentity())) {
            throw new IllegalArgumentException("MATCH_ENGINE_V1_TEAM_IDENTITY_COLLISION");
        }
        Set<PlayerId> ids = new HashSet<>();
        for (PlayerInput player : allPlayers(blue, red)) {
            if (!ids.add(player.playerId())) {
                throw new IllegalArgumentException("MATCH_ENGINE_V1_DUPLICATE_PLAYER_ID");
            }
        }
    }

    private static void validateAssignments(
            TeamInput blue,
            TeamInput red,
            List<ChampionAssignmentInput> assignments,
            DraftInput draft
    ) {
        int expected = TeamSide.values().length * Position.values().length;
        if (assignments.size() != expected) {
            throw new IllegalArgumentException("MATCH_ENGINE_V1_ASSIGNMENT_CARDINALITY");
        }
        Map<PlayerKey, PlayerInput> playersBySlot = new LinkedHashMap<>();
        allPlayers(blue, red).forEach(player -> playersBySlot.put(
                new PlayerKey(player.teamSide(), player.position()), player));
        Set<PlayerKey> slots = new HashSet<>();
        Set<PlayerId> assignedPlayers = new HashSet<>();
        Set<ChampionId> champions = new HashSet<>();
        for (ChampionAssignmentInput assignment : assignments) {
            PlayerKey slot = new PlayerKey(assignment.teamSide(), assignment.position());
            PlayerInput player = playersBySlot.get(slot);
            if (player == null || !player.playerId().equals(assignment.playerId())) {
                throw new IllegalArgumentException("MATCH_ENGINE_V1_ASSIGNMENT_PLAYER_MISMATCH");
            }
            if (!slots.add(slot) || !assignedPlayers.add(assignment.playerId())) {
                throw new IllegalArgumentException("MATCH_ENGINE_V1_DUPLICATE_ASSIGNMENT");
            }
            if (!champions.add(assignment.championId())) {
                throw new IllegalArgumentException("MATCH_ENGINE_V1_DUPLICATE_CHAMPION");
            }
        }
        if (!slots.equals(playersBySlot.keySet())) {
            throw new IllegalArgumentException("MATCH_ENGINE_V1_ASSIGNMENT_COVERAGE");
        }
        Set<ChampionId> bluePicks = assignments.stream()
                .filter(value -> value.teamSide() == TeamSide.BLUE)
                .map(ChampionAssignmentInput::championId).collect(java.util.stream.Collectors.toSet());
        Set<ChampionId> redPicks = assignments.stream()
                .filter(value -> value.teamSide() == TeamSide.RED)
                .map(ChampionAssignmentInput::championId).collect(java.util.stream.Collectors.toSet());
        if (!bluePicks.equals(Set.copyOf(draft.bluePicks()))
                || !redPicks.equals(Set.copyOf(draft.redPicks()))) {
            throw new IllegalArgumentException("MATCH_ENGINE_V1_DRAFT_ASSIGNMENT_MISMATCH");
        }
        String assignmentHash = finalAssignmentHash(assignments);
        if (!assignmentHash.equals(draft.finalAssignmentHash())) {
            throw new IllegalArgumentException("MATCH_ENGINE_V1_FINAL_ASSIGNMENT_HASH_MISMATCH");
        }
        if (!draft.draftDecisionHash().equals(draftDecisionHash(draft.decisions()))) {
            throw new IllegalArgumentException("MATCH_ENGINE_V1_DRAFT_DECISION_HASH_MISMATCH");
        }
        if (!draft.finalDraftHash().equals(finalDraftHash(draft, assignments))) {
            throw new IllegalArgumentException("MATCH_ENGINE_V1_FINAL_DRAFT_HASH_MISMATCH");
        }
    }

    private static List<PlayerInput> allPlayers(TeamInput blue, TeamInput red) {
        ArrayList<PlayerInput> result = new ArrayList<>(blue.lineup());
        result.addAll(red.lineup());
        return List.copyOf(result);
    }

    private static void appendTeam(StringBuilder value, TeamInput team) {
        value.append("team=").append(team.teamSide()).append('|')
                .append(team.teamIdentity()).append('\n');
        team.lineup().stream().sorted(Comparator.comparing(PlayerInput::position))
                .forEach(player -> {
                    value.append("player=").append(player.teamSide()).append('|')
                            .append(player.position()).append('|')
                            .append(player.playerId().value()).append('\n');
                    PlayerSkill.orderedForPosition(player.position()).forEach(skill ->
                            value.append("rating=").append(player.playerId().value()).append('|')
                                    .append(skill).append('|').append(player.ratings().get(skill))
                                    .append('\n'));
                    player.proficiencies().entrySet().stream()
                            .sorted(Map.Entry.comparingByKey(
                                    Comparator.comparing(ChampionRoleKey::stableId)))
                            .forEach(entry -> value.append("proficiency=")
                                    .append(player.playerId().value()).append('|')
                                    .append(entry.getKey().stableId()).append('|')
                                    .append(entry.getValue()).append('\n'));
                });
    }

    static String rosterIdentityHash(TeamInput blue, TeamInput red) {
        StringBuilder canonical = new StringBuilder(
                "rosterIdentitySchema=REAL_MATCH_ROSTER_V1\n");
        appendRoster(canonical, blue);
        appendRoster(canonical, red);
        return hash(canonical.toString());
    }

    private static void appendRoster(StringBuilder canonical, TeamInput team) {
        for (Position position : Position.values()) {
            PlayerInput player = team.lineup().stream()
                    .filter(value -> value.position() == position).findFirst().orElseThrow();
            canonical.append("roster=").append(team.teamSide()).append('|')
                    .append(team.teamIdentity()).append('|').append(position).append('|')
                    .append(player.playerId().value()).append('\n');
        }
    }

    static String seriesHistoryHash(int committedGames, Set<ChampionId> exclusions) {
        if (committedGames < 0) {
            throw new IllegalArgumentException("committedGames must not be negative");
        }
        StringBuilder canonical = new StringBuilder(
                "seriesHistorySchema=HARD_FEARLESS_HISTORY_V1\n")
                .append("committedGameCount=").append(committedGames).append('\n');
        exclusions.stream().map(ChampionId::value).sorted()
                .forEach(value -> canonical.append("consumedPick=").append(value).append('\n'));
        return hash(canonical.toString());
    }

    static String finalAssignmentHash(List<ChampionAssignmentInput> assignments) {
        StringBuilder canonical = new StringBuilder(
                "finalAssignmentSchema=MATCH_CHAMPION_ASSIGNMENT_V1\n")
                .append("selectionMode=EXPLICIT\n");
        assignments.stream().sorted(Comparator.comparing(
                        assignment -> new PlayerKey(assignment.teamSide(), assignment.position()).stableId()))
                .forEach(assignment -> canonical.append("assignment=")
                        .append(assignment.teamSide()).append(':').append(assignment.position())
                        .append('|').append(assignment.championId().value()).append('|')
                        .append(assignment.position()).append('\n'));
        return hash(canonical.toString());
    }

    static String draftDecisionHash(List<DraftDecisionInput> decisions) {
        StringBuilder canonical = new StringBuilder();
        decisions.forEach(value -> canonical.append(value.turn()).append(':')
                .append(value.side()).append(':').append(value.actionType()).append(':')
                .append(value.selectedChampionId().value()).append('\n'));
        return hash(canonical.toString());
    }

    static String finalDraftHash(DraftInput draft, List<ChampionAssignmentInput> assignments) {
        StringBuilder canonical = new StringBuilder("finalDraftSchema=FINAL_DRAFT_RESULT_V1\n")
                .append("ruleSetIdentity=").append(draft.draftRuleSetIdentity()).append('\n')
                .append("draftDecisionHash=").append(draft.draftDecisionHash()).append('\n');
        appendChampionList(canonical, "blueBan", draft.blueBans());
        appendChampionList(canonical, "redBan", draft.redBans());
        appendChampionList(canonical, "bluePick", draft.bluePicks());
        appendChampionList(canonical, "redPick", draft.redPicks());
        for (TeamSide side : TeamSide.values()) {
            assignments.stream().filter(value -> value.teamSide() == side)
                    .sorted(Comparator.comparing(ChampionAssignmentInput::championId,
                            Comparator.comparing(ChampionId::value)))
                    .forEach(value -> canonical.append("finalRole=").append(value.teamSide())
                            .append('|').append(value.championId().value()).append('|')
                            .append(value.position()).append('\n'));
        }
        draft.hardFearlessExclusions().stream().map(ChampionId::value).sorted()
                .forEach(value -> canonical.append("hardFearlessExclusion=")
                        .append(value).append('\n'));
        canonical.append("draftMetaVersion=").append(draft.draftMetaVersion()).append('\n')
                .append("requiredLegalRoleKeyHash=")
                .append(draft.requiredLegalRoleKeyHash()).append('\n')
                .append("actualLegalRoleKeyHash=")
                .append(draft.actualLegalRoleKeyHash()).append('\n')
                .append("finalAssignmentHash=").append(draft.finalAssignmentHash()).append('\n');
        return hash(canonical.toString());
    }

    private static void appendChampionList(
            StringBuilder canonical, String field, List<ChampionId> champions
    ) {
        for (int index = 0; index < champions.size(); index++) {
            canonical.append(field).append('=').append(index).append('|')
                    .append(champions.get(index).value()).append('\n');
        }
    }

    static String hash(String canonical) {
        if (!canonical.endsWith("\n")) {
            throw new IllegalArgumentException("Canonical input requires a trailing newline");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public record TeamInput(
            String teamIdentity,
            String displayName,
            TeamSide teamSide,
            List<PlayerInput> lineup
    ) {
        public TeamInput {
            teamIdentity = MatchEngineV1Policy.required(teamIdentity, "teamIdentity")
                    .toUpperCase(java.util.Locale.ROOT);
            displayName = MatchEngineV1Policy.required(displayName, "displayName");
            Objects.requireNonNull(teamSide, "teamSide");
            lineup = List.copyOf(lineup);
            if (lineup.size() != Position.values().length) {
                throw new IllegalArgumentException("MATCH_ENGINE_V1_LINEUP_CARDINALITY");
            }
            EnumSet<Position> positions = EnumSet.noneOf(Position.class);
            for (PlayerInput player : lineup) {
                if (player.teamSide() != teamSide || !positions.add(player.position())) {
                    throw new IllegalArgumentException("MATCH_ENGINE_V1_LINEUP_POSITION_COVERAGE");
                }
            }
            if (!positions.equals(EnumSet.allOf(Position.class))) {
                throw new IllegalArgumentException("MATCH_ENGINE_V1_LINEUP_POSITION_COVERAGE");
            }
        }

        Team toDomainTeam() {
            return new Team(displayName, lineup.stream().sorted(
                    Comparator.comparing(PlayerInput::position)).map(PlayerInput::toDomain).toList());
        }
    }

    public record PlayerInput(
            PlayerId playerId,
            String displayName,
            TeamSide teamSide,
            Position position,
            Map<PlayerSkill, Integer> ratings,
            Map<ChampionRoleKey, Integer> proficiencies
    ) {
        public PlayerInput {
            Objects.requireNonNull(playerId, "playerId");
            displayName = MatchEngineV1Policy.required(displayName, "playerDisplayName");
            Objects.requireNonNull(teamSide, "teamSide");
            Objects.requireNonNull(position, "position");
            EnumMap<PlayerSkill, Integer> ratingCopy = new EnumMap<>(PlayerSkill.class);
            ratingCopy.putAll(ratings);
            ratings = Map.copyOf(ratingCopy);
            new PlayerRatings(position, ratings);
            proficiencies = Map.copyOf(proficiencies);
            new ChampionProficiencies(proficiencies);
        }

        Player toDomain() {
            return new Player(playerId, displayName, position,
                    new PlayerRatings(position, ratings),
                    new ChampionProficiencies(proficiencies));
        }
    }

    public record ChampionAssignmentInput(
            PlayerId playerId,
            TeamSide teamSide,
            Position position,
            ChampionId championId
    ) {
        public ChampionAssignmentInput {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(teamSide, "teamSide");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(championId, "championId");
        }

        ChampionAssignment toDomain() {
            return new ChampionAssignment(new PlayerKey(teamSide, position), championId, position);
        }
    }

    public record DraftDecisionInput(
            int turn,
            TeamSide side,
            DraftActionType actionType,
            ChampionId selectedChampionId
    ) {
        public DraftDecisionInput {
            if (turn < 1) throw new IllegalArgumentException("Draft turn must be positive");
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(actionType, "actionType");
            Objects.requireNonNull(selectedChampionId, "selectedChampionId");
        }
    }

    public record DraftInput(
            int seriesGameNumber,
            String draftRuleSetIdentity,
            String draftRuleSetHash,
            String draftScoringPolicyHash,
            List<DraftDecisionInput> decisions,
            String draftDecisionHash,
            List<ChampionId> blueBans,
            List<ChampionId> redBans,
            List<ChampionId> bluePicks,
            List<ChampionId> redPicks,
            List<ChampionId> hardFearlessExclusions,
            String draftMetaVersion,
            String requiredLegalRoleKeyHash,
            String actualLegalRoleKeyHash,
            String finalAssignmentHash,
            String finalDraftHash
    ) {
        public DraftInput {
            if (seriesGameNumber < 1) {
                throw new IllegalArgumentException("seriesGameNumber must be positive");
            }
            draftRuleSetIdentity = MatchEngineV1Policy.required(
                    draftRuleSetIdentity, "draftRuleSetIdentity");
            draftRuleSetHash = MatchEngineV1Policy.requiredHash(
                    draftRuleSetHash, "draftRuleSetHash");
            draftScoringPolicyHash = MatchEngineV1Policy.requiredHash(
                    draftScoringPolicyHash, "draftScoringPolicyHash");
            decisions = List.copyOf(decisions);
            draftDecisionHash = MatchEngineV1Policy.requiredHash(
                    draftDecisionHash, "draftDecisionHash");
            blueBans = List.copyOf(blueBans);
            redBans = List.copyOf(redBans);
            bluePicks = List.copyOf(bluePicks);
            redPicks = List.copyOf(redPicks);
            List<ChampionId> orderedExclusions = hardFearlessExclusions.stream()
                    .sorted(Comparator.comparing(ChampionId::value)).toList();
            if (new HashSet<>(orderedExclusions).size() != orderedExclusions.size()) {
                throw new IllegalArgumentException("Duplicate Hard Fearless exclusion");
            }
            hardFearlessExclusions = List.copyOf(orderedExclusions);
            draftMetaVersion = MatchEngineV1Policy.required(draftMetaVersion, "draftMetaVersion");
            requiredLegalRoleKeyHash = MatchEngineV1Policy.requiredHash(
                    requiredLegalRoleKeyHash, "requiredLegalRoleKeyHash");
            actualLegalRoleKeyHash = MatchEngineV1Policy.requiredHash(
                    actualLegalRoleKeyHash, "actualLegalRoleKeyHash");
            finalAssignmentHash = MatchEngineV1Policy.requiredHash(
                    finalAssignmentHash, "finalAssignmentHash");
            finalDraftHash = MatchEngineV1Policy.requiredHash(finalDraftHash, "finalDraftHash");
            if (!draftRuleSetIdentity.equals(MatchEngineV1Policy.DRAFT_RULE_SET_IDENTITY)
                    || !draftRuleSetHash.equals(MatchEngineV1Policy.DRAFT_RULE_SET_SHA256)
                    || !draftScoringPolicyHash.equals(
                    MatchEngineV1Policy.DRAFT_SCORING_POLICY_SHA256)) {
                throw new IllegalArgumentException("MATCH_ENGINE_V1_DRAFT_POLICY_MISMATCH");
            }
            if (decisions.size() != 20 || bluePicks.size() != Position.values().length
                    || redPicks.size() != Position.values().length) {
                throw new IllegalArgumentException("MATCH_ENGINE_V1_INCOMPLETE_FINAL_DRAFT");
            }
        }

        void appendCanonical(StringBuilder value) {
            value.append("draftRuleSetIdentity=").append(draftRuleSetIdentity).append('\n')
                    .append("draftRuleSetHash=").append(draftRuleSetHash).append('\n')
                    .append("draftScoringPolicyHash=").append(draftScoringPolicyHash).append('\n')
                    .append("draftDecisionHash=").append(draftDecisionHash).append('\n')
                    .append("finalAssignmentHash=").append(finalAssignmentHash).append('\n')
                    .append("finalDraftHash=").append(finalDraftHash).append('\n');
            decisions.forEach(decision -> value.append("draftDecision=")
                    .append(decision.turn()).append('|').append(decision.side()).append('|')
                    .append(decision.actionType()).append('|')
                    .append(decision.selectedChampionId().value()).append('\n'));
            appendChampionList(value, "blueBan", blueBans);
            appendChampionList(value, "redBan", redBans);
            appendChampionList(value, "bluePick", bluePicks);
            appendChampionList(value, "redPick", redPicks);
            hardFearlessExclusions.stream().map(ChampionId::value).sorted()
                    .forEach(champion -> value.append("hardFearlessExclusion=")
                            .append(champion).append('\n'));
            value.append("draftMetaVersion=").append(draftMetaVersion).append('\n')
                    .append("requiredLegalRoleKeyHash=").append(requiredLegalRoleKeyHash).append('\n')
                    .append("actualLegalRoleKeyHash=").append(actualLegalRoleKeyHash).append('\n');
        }
    }
}
