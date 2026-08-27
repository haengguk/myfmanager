package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.RealDraftSelectionContextFactory;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.domain.Team;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlayerControlledDraftEngineTest {
    @Autowired ObjectMapper mapper;
    @Autowired ChampionCatalog champions;
    @Autowired LckTeamAssembler teams;
    @Autowired PlayerControlledDraftEngine engine;

    private Team blue;
    private Team red;
    private DraftTeamContext blueContext;
    private DraftTeamContext redContext;
    private DraftSelectionContext selectionContext;

    @BeforeAll
    void fixture() {
        blue = teams.assemble("GEN");
        red = teams.assemble("T1");
        blueContext = DraftTeamContext.from(blue);
        redContext = DraftTeamContext.from(red);
        selectionContext = RealDraftSelectionContextFactory.create(
                73L, "GEN", blue, "T1", red, 1, Set.of());
    }

    @Test
    void controlPolicyHasFrozenCanonicalHash() {
        assertThat(PlayerDraftControlPolicy.hash(
                PlayerDraftControlPolicy.canonicalPolicy()))
                .isEqualTo("8f6488f07c44a6529e88bd022fff3124458a8237cc919bd7dd3e140eaa4a0752")
                .isEqualTo(PlayerDraftControlPolicy.POLICY_HASH);
    }

    @Test
    void blueAndRedControlCompleteWithHonestAuthorityAndFreshReconstruction() {
        for (TeamSide controlled : TeamSide.values()) {
            PlayerControlledDraftEngine.Progress completed = completeArbitrarily(
                    controlled, "complete-" + controlled.name().toLowerCase());
            PlayerControlledDraftResult result = completed.result();

            assertThat(result.turnEvidence()).hasSize(20);
            assertThat(result.turnEvidence()).allSatisfy(turn -> {
                assertThat(turn.authority()).isEqualTo(
                        turn.side() == controlled
                                ? DraftDecisionAuthority.PLAYER
                                : DraftDecisionAuthority.AI);
                if (turn.authority() == DraftDecisionAuthority.PLAYER) {
                    assertThat(turn.playerSelectionEvidence()).isNotNull();
                    assertThat(turn.autoSelectionTrace()).isNull();
                } else {
                    assertThat(turn.autoSelectionTrace()).isNotNull();
                    assertThat(turn.playerSelectionEvidence()).isNull();
                }
            });
            assertThat(result.bluePicks()).hasSize(5);
            assertThat(result.redPicks()).hasSize(5);
            assertThat(new HashSet<>(allChampions(result))).hasSize(20);
            assertThat(result.hardFearlessExclusions()).isEmpty();
            assertThat(result.controlEvidence().policyId())
                    .isEqualTo(PlayerDraftControlPolicy.POLICY_ID);
            engine.validateCompleted(
                    result, blueContext, redContext, selectionContext);
        }
    }

    @Test
    void playerMayChooseOutsideAdvisoryTopThreeAndFlexRolesStayUnassigned() {
        PlayerControlledDraftEngine.Progress progress = engine.start(
                blueContext, redContext, selectionContext, TeamSide.BLUE);
        PlayerControlledDraftEngine.SelectionView view = engine.view(
                progress, blueContext, redContext);
        Set<ChampionId> recommended = view.recommendations().stream()
                .map(PlayerControlledDraftEngine.Recommendation::championId)
                .collect(java.util.stream.Collectors.toSet());
        PlayerControlledDraftEngine.SelectableChampion chosen = view.selectable().stream()
                .filter(value -> !recommended.contains(value.championId()))
                .findFirst().orElseThrow();

        PlayerControlledDraftEngine.Progress after = engine.select(
                progress, blueContext, redContext, selectionContext,
                chosen.championId(), "outside-top-three");

        assertThat(after.turnEvidence().getFirst().championId())
                .isEqualTo(chosen.championId());
        assertThat(after.turnEvidence().getFirst().authority())
                .isEqualTo(DraftDecisionAuthority.PLAYER);
        assertThat(after.turnEvidence().getFirst().playerSelectionEvidence())
                .extracting(PlayerManualSelectionEvidence::legalityResult)
                .isEqualTo(PlayerSelectionLegality.LEGAL);

        PlayerControlledDraftEngine.Progress pickTurn = after;
        int suffix = 0;
        while (pickTurn.state().currentTurn().actionType() == DraftActionType.BAN) {
            PlayerControlledDraftEngine.SelectionView next = engine.view(
                    pickTurn, blueContext, redContext);
            pickTurn = engine.select(pickTurn, blueContext, redContext, selectionContext,
                    next.selectable().getFirst().championId(), "reach-pick-" + suffix++);
        }
        PlayerControlledDraftEngine.SelectionView pickView = engine.view(
                pickTurn, blueContext, redContext);
        assertThat(pickView.selectable())
                .anyMatch(value -> value.feasibleRoles().size() > 1);
    }

    @Test
    void illegalFutureCompletionChoiceIsRejectedWithoutMutation() {
        PlayerControlledDraftEngine.Progress progress = engine.start(
                blueContext, redContext, selectionContext, TeamSide.BLUE);
        PlayerControlledDraftEngine.SelectionView view = engine.view(
                progress, blueContext, redContext);
        ChampionId selected = view.selectable().getFirst().championId();
        progress = engine.select(progress, blueContext, redContext, selectionContext,
                selected, "first-valid");
        String stateHash = DraftStateHasher.hash(progress.state());
        int evidenceCount = progress.turnEvidence().size();
        PlayerControlledDraftEngine.Progress immutableBeforeFailure = progress;

        assertThatThrownBy(() -> engine.select(
                immutableBeforeFailure, blueContext, redContext, selectionContext,
                selected, "illegal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PLAYER_DRAFT_ILLEGAL_SELECTION");
        assertThat(DraftStateHasher.hash(progress.state())).isEqualTo(stateHash);
        assertThat(progress.turnEvidence()).hasSize(evidenceCount);
    }

    @Test
    void followingAutomaticChoicesProvesAiTurnsKeepExactProductionSemantics() {
        DraftEngine automatic = new DraftEngine(
                DraftResourceSet.loadDefault(mapper, champions));
        FinalDraftResult reference = automatic.draft(
                blueContext, redContext, new SeriesDraftHistory(), selectionContext);
        PlayerControlledDraftEngine.Progress progress = engine.start(
                blueContext, redContext, selectionContext, TeamSide.BLUE);
        int playerAction = 0;
        while (!progress.complete()) {
            int turn = progress.state().currentTurn().number();
            ChampionId referenceChoice = reference.decisions().get(turn - 1)
                    .selectedChampionId();
            progress = engine.select(progress, blueContext, redContext, selectionContext,
                    referenceChoice, "parity-" + playerAction++);
        }

        assertThat(progress.result().decisions()).extracting(DraftAction::championId)
                .containsExactlyElementsOf(reference.decisions().stream()
                        .map(DraftDecision::selectedChampionId).toList());
        for (DraftTurnControlEvidence evidence : progress.result().turnEvidence()) {
            if (evidence.authority() == DraftDecisionAuthority.AI) {
                assertThat(evidence.autoSelectionTrace())
                        .isEqualTo(reference.selectionTraces().get(evidence.turn() - 1));
            }
        }
    }

    @Test
    void sameGameplayTranscriptIgnoresClientActionIdsButReplaysExactly() {
        PlayerControlledDraftEngine.Progress first = completeArbitrarily(
                TeamSide.RED, "first");
        PlayerControlledDraftEngine.Progress replay = replayPlayerChoices(
                first.result(), TeamSide.RED, "retry-different-id");

        assertThat(replay.result().draftIdentity()).isEqualTo(first.result().draftIdentity());
        assertThat(replay.result().controlEvidence().controlEvidenceHash())
                .isEqualTo(first.result().controlEvidence().controlEvidenceHash());
        com.fasterxml.jackson.databind.JsonNode replayEvidence =
                mapper.valueToTree(replay.result().controlEvidence());
        com.fasterxml.jackson.databind.JsonNode firstEvidence =
                mapper.valueToTree(first.result().controlEvidence());
        assertThat(replayEvidence).isEqualTo(firstEvidence);
        assertThat(replay.result().matchChampionAssignments().asMap())
                .isEqualTo(first.result().matchChampionAssignments().asMap());
        engine.validateCompleted(replay.result(), blueContext, redContext, selectionContext);
    }

    @Test
    void finalAssignmentsCannotBeInjectedAfterTranscriptCompletion() {
        PlayerControlledDraftResult source = completeArbitrarily(
                TeamSide.BLUE, "tamper").result();
        var tamperedBlue = new java.util.LinkedHashMap<>(
                source.blueFinalRoleAssignments());
        var entries = new ArrayList<>(tamperedBlue.entrySet());
        var first = entries.get(0);
        var second = entries.get(1);
        tamperedBlue.put(first.getKey(), second.getValue());
        tamperedBlue.put(second.getKey(), first.getValue());
        PlayerControlledDraftResult tampered = new PlayerControlledDraftResult(
                source.ruleSet(), source.controlledSide(), source.blueBans(), source.redBans(),
                source.bluePicks(), source.redPicks(), source.turnEvidence(), tamperedBlue,
                source.redFinalRoleAssignments(), source.matchChampionAssignments(),
                source.hardFearlessExclusions(), source.draftMetaVersion(),
                source.requiredLegalRoleKeyHash(), source.actualLegalRoleKeyHash());

        assertThatThrownBy(() -> engine.validateCompleted(
                tampered, blueContext, redContext, selectionContext))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FINAL_ASSIGNMENT_MISMATCH");
    }

    private PlayerControlledDraftEngine.Progress completeArbitrarily(
            TeamSide controlled, String actionPrefix
    ) {
        PlayerControlledDraftEngine.Progress progress = engine.start(
                blueContext, redContext, selectionContext, controlled);
        int action = 0;
        while (!progress.complete()) {
            PlayerControlledDraftEngine.SelectionView view = engine.view(
                    progress, blueContext, redContext);
            ChampionId choice = view.selectable().getFirst().championId();
            progress = engine.select(progress, blueContext, redContext, selectionContext,
                    choice, actionPrefix + '-' + action++);
        }
        return progress;
    }

    private PlayerControlledDraftEngine.Progress replayPlayerChoices(
            PlayerControlledDraftResult source, TeamSide controlled, String actionPrefix
    ) {
        PlayerControlledDraftEngine.Progress progress = engine.start(
                blueContext, redContext, selectionContext, controlled);
        int action = 0;
        while (!progress.complete()) {
            ChampionId choice = source.turnEvidence().get(
                    progress.state().currentTurn().number() - 1).championId();
            progress = engine.select(progress, blueContext, redContext, selectionContext,
                    choice, actionPrefix + '-' + action++);
        }
        return progress;
    }

    private static List<ChampionId> allChampions(PlayerControlledDraftResult result) {
        return java.util.stream.Stream.of(
                        result.blueBans(), result.redBans(),
                        result.bluePicks(), result.redPicks())
                .flatMap(List::stream).toList();
    }
}
