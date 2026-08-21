package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.controller.MatchController;
import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerSnapshot;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.FinalDraftResult;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.factory.DummyDataFactory;
import com.lolfm.player.PlayerId;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.TeamSide;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealDraftMatchOrchestratorTest {
    private static final long MATCH_SEED = 73L;
    private static final long GAME_TWO_MATCH_SEED = 74L;

    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired RealDraftMatchPreflightValidator preflight;
    @Autowired ChampionCatalog champions;

    private SeriesDraftHistory seriesHistory;
    private RealDraftMatchResult gameOne;
    private RealDraftMatchResult gameTwo;
    private RealDraftMatchResult replay;

    @BeforeAll
    void runRepresentativeRealGames() {
        seriesHistory = new SeriesDraftHistory();
        gameOne = orchestrator.orchestrate("GEN", "T1", seriesHistory, MATCH_SEED);
        gameTwo = orchestrator.orchestrate("GEN", "T1", seriesHistory, GAME_TWO_MATCH_SEED);
        replay = orchestrator.orchestrate("GEN", "T1", MATCH_SEED);
    }

    @Test
    void realGenVsT1RunsFromAuthoredRosterThroughDraftAndMatchWithoutDummyDependency() {
        assertThat(gameOne.blueTeam().getName()).isEqualTo("GEN");
        assertThat(gameOne.redTeam().getName()).isEqualTo("T1");
        assertThat(gameOne.blueTeamCode()).isEqualTo("GEN");
        assertThat(gameOne.redTeamCode()).isEqualTo("T1");
        assertThat(gameOne.blueTeam().getPlayers()).hasSize(5)
                .allMatch(Player::hasStablePlayerId);
        assertThat(gameOne.redTeam().getPlayers()).hasSize(5)
                .allMatch(Player::hasStablePlayerId);
        assertThat(gameOne.draftResult().decisions()).hasSize(20);
        assertThat(gameOne.matchChampionAssignments().asMap()).hasSize(10);
        assertThat(gameOne.timeline().getDurationSeconds()).isPositive();
        assertThat(gameOne.timeline().getWinner()).isIn("GEN", "T1");
        assertThat(gameOne.playerIdsByMatchSlot())
                .containsEntry(new PlayerKey(TeamSide.BLUE, Position.MID), new PlayerId("player-chovy"))
                .containsEntry(new PlayerKey(TeamSide.RED, Position.MID), new PlayerId("player-faker"));

        Set<Class<?>> dependencies = new HashSet<>();
        Arrays.stream(RealDraftMatchOrchestrator.class.getDeclaredFields())
                .map(Field::getType).forEach(dependencies::add);
        Arrays.stream(RealDraftMatchOrchestrator.class.getDeclaredConstructors())
                .map(Constructor::getParameterTypes).flatMap(Arrays::stream).forEach(dependencies::add);
        assertThat(dependencies).doesNotContain(DummyDataFactory.class);
    }

    @Test
    void finalDraftRolesAreTheOnlySourceOfExactMatchAssignments() {
        assertThat(gameOne.matchChampionAssignments())
                .isSameAs(gameOne.draftResult().matchChampionAssignments());
        assertExactFinalRoleMapping(gameOne);
        assertExactFinalRoleMapping(gameTwo);

        MatchSnapshot firstSnapshot = gameOne.timeline().getSnapshots().getFirst();
        for (PlayerSnapshot snapshot : firstSnapshot.getPlayerSnapshots()) {
            ChampionAssignment assignment = gameOne.matchChampionAssignments().get(
                    new PlayerKey(snapshot.getTeamSide(), snapshot.getPosition()));
            assertThat(snapshot.getChampionId()).isEqualTo(assignment.championId().value());
            Team team = snapshot.getTeamSide() == TeamSide.BLUE ? gameOne.blueTeam() : gameOne.redTeam();
            assertThat(team.getPlayers().stream()
                    .filter(player -> player.getPosition() == snapshot.getPosition())
                    .map(Player::getName)).containsExactly(snapshot.getPlayerName());
        }
    }

    @Test
    void flexChampionKeepsTheFinalResolvedPositionWhenMappedIntoMatch() {
        List<RealDraftMatchResult> games = List.of(gameOne, gameTwo);
        List<ChampionAssignment> flexAssignments = games.stream()
                .flatMap(game -> game.matchChampionAssignments().asMap().values().stream())
                .filter(assignment -> champions.get(assignment.championId())
                        .supportedPositions().size() > 1)
                .toList();

        assertThat(flexAssignments).as("representative real series must draft a legal flex champion")
                .isNotEmpty();
        assertThat(flexAssignments).allSatisfy(assignment -> {
            assertThat(champions.get(assignment.championId()).supportedPositions())
                    .contains(assignment.selectedPosition());
            RealDraftMatchResult owner = games.stream().filter(game -> game.matchChampionAssignments()
                    .asMap().containsValue(assignment)).findFirst().orElseThrow();
            Map<ChampionId, Position> finalRoles = assignment.playerKey().side() == TeamSide.BLUE
                    ? owner.draftResult().blueFinalRoleAssignments()
                    : owner.draftResult().redFinalRoleAssignments();
            assertThat(finalRoles.get(assignment.championId()))
                    .isEqualTo(assignment.selectedPosition());
        });
    }

    @Test
    void stableIdentitySurvivesRosterSlotMatchStateAndKillAssistEvents() {
        Set<String> playerIds = gameOne.playerIdsByMatchSlot().values().stream()
                .map(PlayerId::value).collect(java.util.stream.Collectors.toSet());
        Set<String> displayNames = allPlayers(gameOne).stream()
                .map(Player::getName).collect(java.util.stream.Collectors.toSet());
        List<MatchEvent> kills = gameOne.timeline().getEvents().stream()
                .filter(event -> event.getType() == MatchEventType.KILL).toList();

        assertThat(playerIds).hasSize(10);
        assertThat(kills).isNotEmpty().allSatisfy(event -> {
            assertThat(event.getKillerPlayerId()).isIn(playerIds);
            assertThat(event.getVictimPlayerId()).isIn(playerIds);
            assertThat(event.getAssistPlayerIds()).allMatch(playerIds::contains);
            assertThat(event.getKiller()).isIn(displayNames);
            assertThat(event.getVictim()).isIn(displayNames);
            assertThat(event.getKiller()).isNotEqualTo(event.getKillerPlayerId());
            assertThat(event.getVictim()).isNotEqualTo(event.getVictimPlayerId());
        });
        assertThat(kills).anyMatch(event -> !event.getAssistPlayerIds().isEmpty());
    }

    @Test
    void malformedRosterAndDraftContextBindingsFailFast() {
        Team incomplete = new Team("GEN", gameOne.blueTeam().getPlayers().subList(0, 4));
        assertThatThrownBy(() -> preflight.validate("GEN", incomplete, "T1", gameOne.redTeam(),
                gameOne.blueDraftContext(), gameOne.redDraftContext(), gameOne.draftResult(),
                new SeriesDraftHistory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID_REAL_TEAM_LINEUP");

        Team wrongIdentity = replacePlayerId(gameOne.blueTeam(), Position.TOP,
                new PlayerId("player-malformed-roster-binding"));
        assertThatThrownBy(() -> preflight.validate("GEN", wrongIdentity, "T1", gameOne.redTeam(),
                DraftTeamContext.from(wrongIdentity), gameOne.redDraftContext(),
                gameOne.draftResult(), new SeriesDraftHistory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PLAYER_ID_RATING_KEY_MISMATCH");

        assertThatThrownBy(() -> preflight.validate("GEN", gameOne.blueTeam(), "T1", gameOne.redTeam(),
                gameOne.redDraftContext(), gameOne.redDraftContext(), gameOne.draftResult(),
                new SeriesDraftHistory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DRAFT_CONTEXT_PLAYER_ID_MISMATCH");
    }

    @Test
    void malformedFinalAssignmentAndFearlessHistoryFailFast() {
        MatchChampionAssignments swapped = swapTopChampions(gameOne.matchChampionAssignments());
        FinalDraftResult wrongAssignments = copy(gameOne.draftResult(),
                gameOne.draftResult().blueFinalRoleAssignments(),
                gameOne.draftResult().redFinalRoleAssignments(), swapped);
        assertThatThrownBy(() -> preflight.validate("GEN", gameOne.blueTeam(), "T1", gameOne.redTeam(),
                gameOne.blueDraftContext(), gameOne.redDraftContext(), wrongAssignments,
                new SeriesDraftHistory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DRAFT_MATCH_ASSIGNMENT_MISMATCH");

        SeriesDraftHistory alreadyCommitted = new SeriesDraftHistory();
        alreadyCommitted.commitCompleted(gameOne.draftResult());
        assertThatThrownBy(() -> preflight.validate("GEN", gameOne.blueTeam(), "T1", gameOne.redTeam(),
                gameOne.blueDraftContext(), gameOne.redDraftContext(), gameOne.draftResult(),
                alreadyCommitted))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HARD_FEARLESS_HISTORY_MISMATCH");
    }

    @Test
    void duplicateStablePlayerIdAcrossTeamsFailsBeforeMatch() {
        PlayerId duplicate = gameOne.blueTeam().getPlayers().stream()
                .filter(player -> player.getPosition() == Position.TOP)
                .findFirst().orElseThrow().requirePlayerId();
        Team duplicateRed = replacePlayerId(gameOne.redTeam(), Position.TOP, duplicate);

        assertThatThrownBy(() -> preflight.validate("GEN", gameOne.blueTeam(), "T1", duplicateRed,
                gameOne.blueDraftContext(), DraftTeamContext.from(duplicateRed),
                gameOne.draftResult(), new SeriesDraftHistory()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DUPLICATE_MATCH_PLAYER_ID");
    }

    @Test
    void explicitTeamCodeNotDisplayNameOwnsRosterIdentityValidation() {
        Team renamedPresentation = new Team("Gen.G presentation label",
                gameOne.blueTeam().getPlayers());

        preflight.validate("GEN", renamedPresentation, "T1", gameOne.redTeam(),
                DraftTeamContext.from(renamedPresentation), gameOne.redDraftContext(),
                gameOne.draftResult(), new SeriesDraftHistory());
    }

    @Test
    void sameRealInputsAndSeedReplayDraftAssignmentsAndCompleteTimelineExactly() {
        assertThat(replay.draftResult()).usingRecursiveComparison()
                .isEqualTo(gameOne.draftResult());
        assertThat(replay.draftResult().decisions())
                .containsExactlyElementsOf(gameOne.draftResult().decisions());
        assertThat(replay.draftResult().blueFinalRoleAssignments())
                .isEqualTo(gameOne.draftResult().blueFinalRoleAssignments());
        assertThat(replay.draftResult().redFinalRoleAssignments())
                .isEqualTo(gameOne.draftResult().redFinalRoleAssignments());
        assertThat(replay.matchChampionAssignments().asMap())
                .isEqualTo(gameOne.matchChampionAssignments().asMap());
        assertThat(replay.timeline()).usingRecursiveComparison().isEqualTo(gameOne.timeline());
        assertThat(replay.timeline().getWinner()).isEqualTo(gameOne.timeline().getWinner());
        assertThat(replay.timeline().getDurationSeconds())
                .isEqualTo(gameOne.timeline().getDurationSeconds());
    }

    @Test
    void callerOwnedSeriesHistoryCommitsOnlyPicksAndFeedsGameTwoExclusions() {
        Set<ChampionId> gameOnePicks = new LinkedHashSet<>(gameOne.draftResult().bluePicks());
        gameOnePicks.addAll(gameOne.draftResult().redPicks());

        assertThat(gameOne.seriesGameNumber()).isEqualTo(1);
        assertThat(gameOne.hardFearlessExclusionsBeforeDraft()).isEmpty();
        assertThat(gameOne.seriesConsumedPicksAfterGame()).containsExactlyInAnyOrderElementsOf(gameOnePicks);
        assertThat(gameTwo.seriesGameNumber()).isEqualTo(2);
        assertThat(gameTwo.hardFearlessExclusionsBeforeDraft())
                .containsExactlyInAnyOrderElementsOf(gameOnePicks);
        assertThat(gameTwo.draftResult().bluePicks()).doesNotContainAnyElementsOf(gameOnePicks);
        assertThat(gameTwo.draftResult().redPicks()).doesNotContainAnyElementsOf(gameOnePicks);
        assertThat(seriesHistory.committedGameCount()).isEqualTo(2);
        assertThat(seriesHistory.consumedPicks()).hasSize(20);
    }

    @Test
    void legacyMatchControllerStillOwnsDummyFactoryAndNotRealOrchestrator() {
        Set<Class<?>> controllerFields = Arrays.stream(MatchController.class.getDeclaredFields())
                .map(Field::getType).collect(java.util.stream.Collectors.toSet());
        assertThat(controllerFields).contains(DummyDataFactory.class)
                .doesNotContain(RealDraftMatchOrchestrator.class);
    }

    private void assertExactFinalRoleMapping(RealDraftMatchResult game) {
        for (TeamSide side : TeamSide.values()) {
            Map<ChampionId, Position> roles = side == TeamSide.BLUE
                    ? game.draftResult().blueFinalRoleAssignments()
                    : game.draftResult().redFinalRoleAssignments();
            assertThat(roles.values()).containsExactlyInAnyOrder(Position.values());
            for (Map.Entry<ChampionId, Position> entry : roles.entrySet()) {
                ChampionAssignment assignment = game.matchChampionAssignments().get(
                        new PlayerKey(side, entry.getValue()));
                assertThat(assignment.championId()).isEqualTo(entry.getKey());
                assertThat(assignment.selectedPosition()).isEqualTo(entry.getValue());
            }
        }
    }

    private Team replacePlayerId(Team source, Position position, PlayerId replacementId) {
        List<Player> players = source.getPlayers().stream().map(player -> {
            if (player.getPosition() != position) return player;
            return new Player(replacementId, player.getName(), player.getPosition(),
                    player.getRatings(), player.getChampionProficiencies());
        }).toList();
        return new Team(source.getName(), players);
    }

    private MatchChampionAssignments swapTopChampions(MatchChampionAssignments source) {
        PlayerKey blueTop = new PlayerKey(TeamSide.BLUE, Position.TOP);
        PlayerKey redTop = new PlayerKey(TeamSide.RED, Position.TOP);
        ChampionId blueChampion = source.get(blueTop).championId();
        ChampionId redChampion = source.get(redTop).championId();
        List<ChampionAssignment> values = new ArrayList<>();
        for (ChampionAssignment assignment : source.asMap().values()) {
            if (assignment.playerKey().equals(blueTop)) {
                values.add(new ChampionAssignment(blueTop, redChampion, Position.TOP));
            } else if (assignment.playerKey().equals(redTop)) {
                values.add(new ChampionAssignment(redTop, blueChampion, Position.TOP));
            } else {
                values.add(assignment);
            }
        }
        return new MatchChampionAssignments(values, source.selectionMode());
    }

    private FinalDraftResult copy(FinalDraftResult source,
                                  Map<ChampionId, Position> blueRoles,
                                  Map<ChampionId, Position> redRoles,
                                  MatchChampionAssignments assignments) {
        return new FinalDraftResult(source.ruleSet(), source.blueBans(), source.redBans(),
                source.bluePicks(), source.redPicks(), source.decisions(), blueRoles, redRoles,
                assignments, source.blueInitialPortfolio(), source.redInitialPortfolio(),
                source.blueFinalPortfolio(), source.redFinalPortfolio(),
                source.hardFearlessExclusions(), source.draftMetaVersion(),
                source.requiredLegalRoleKeyHash(), source.actualLegalRoleKeyHash());
    }

    private List<Player> allPlayers(RealDraftMatchResult game) {
        ArrayList<Player> result = new ArrayList<>(game.blueTeam().getPlayers());
        result.addAll(game.redTeam().getPlayers());
        return result;
    }
}
