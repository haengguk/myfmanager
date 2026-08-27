package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionSelectionMode;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.draft.DraftDecisionAuthority;
import com.lolfm.draft.DraftSelectionPoolEntry;
import com.lolfm.draft.DraftSelectionTrace;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.DraftTurnControlEvidence;
import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.draft.PlayerControlledDraftResult;
import com.lolfm.draft.PlayerManualSelectionEvidence;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.TeamSide;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlayerControlledDraftMatchInputBoundaryTest {
    @Autowired LckTeamAssembler teams;
    @Autowired PlayerControlledDraftEngine drafts;
    @Autowired PlayerControlledDraftMatchInputBoundary boundary;
    @Autowired MatchEngineV1 matches;
    @Autowired MatchEngineV1Canonicalizer canonicalizer;

    private Team blue;
    private Team red;
    private PlayerControlledDraftResult completed;

    @BeforeAll
    void fixture() {
        blue = teams.assemble("GEN");
        red = teams.assemble("T1");
        var blueContext = DraftTeamContext.from(blue);
        var redContext = DraftTeamContext.from(red);
        var selectionContext = RealDraftSelectionContextFactory.create(
                73L, "GEN", blue, "T1", red, 1, Set.of());
        PlayerControlledDraftEngine.Progress progress = drafts.start(
                blueContext, redContext, selectionContext, TeamSide.BLUE);
        int action = 0;
        while (!progress.complete()) {
            var view = drafts.view(progress, blueContext, redContext);
            progress = drafts.select(progress, blueContext, redContext, selectionContext,
                    view.selectable().getFirst().championId(), "boundary-" + action++);
        }
        completed = progress.result();
    }

    @Test
    void validMixedDraftUsesSingleBoundaryAndExecutesProductionMatch() {
        MatchEngineV1Input input = boundary.validateAndCreateInput(
                "GEN", blue, "T1", red, 73L, completed);
        MatchEngineV1Output output = matches.execute(
                input, SimulationInstrumentation.enabled());

        assertThat(input.finalDraft().controlEvidence()).isNotNull();
        assertThat(output.finalDraft().finalDraftHash())
                .isEqualTo(input.finalDraft().finalDraftHash());
        assertThat(output.hasValidOutputHash(canonicalizer)).isTrue();
        assertThat(output.executionProvenance().matchSeed()).isEqualTo(73L);
    }

    @Test
    void rawUncheckedFactoryCannotBeCalledAsPublicProductionApi() {
        assertThat(java.util.Arrays.stream(MatchEngineV1InputFactory.class.getMethods())
                .filter(method -> java.util.Arrays.asList(method.getParameterTypes())
                        .contains(PlayerControlledDraftResult.class)))
                .isEmpty();
        var unchecked = java.util.Arrays.stream(
                        MatchEngineV1InputFactory.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(
                        "fromValidatedPlayerControlledDraft"))
                .findFirst().orElseThrow();
        assertThat(Modifier.isPublic(unchecked.getModifiers())).isFalse();
        assertThat(java.util.Arrays.stream(
                        PlayerControlledDraftMatchInputBoundary.ValidatedDraft.class
                                .getDeclaredConstructors()))
                .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers()));
    }

    @Test
    void boundaryRejectsManualEvidenceAuthorityActionAndSelectableSetTampering() {
        DraftTurnControlEvidence player = completed.turnEvidence().stream()
                .filter(value -> value.authority() == DraftDecisionAuthority.PLAYER)
                .findFirst().orElseThrow();
        int playerIndex = player.turn() - 1;
        PlayerManualSelectionEvidence manual = player.playerSelectionEvidence();

        PlayerManualSelectionEvidence forgedSet = new PlayerManualSelectionEvidence(
                manual.controlledSide(), manual.turn(), manual.actionType(), manual.championId(),
                manual.stateBeforeHash(), "f".repeat(64), manual.legalityResult(),
                manual.clientActionId());
        PlayerControlledDraftResult selectableTampered = withTurn(playerIndex,
                new DraftTurnControlEvidence(
                        player.turn(), player.side(), player.actionType(), player.championId(),
                        player.authority(), player.stateBeforeHash(), player.stateAfterHash(),
                        null, forgedSet));
        assertThat(selectableTampered.controlEvidence().controlEvidenceHash())
                .isNotEqualTo(completed.controlEvidence().controlEvidenceHash());
        assertRejected(selectableTampered, 73L, blue, red, "GEN", "T1");

        var anotherChampion = completed.turnEvidence().get(
                playerIndex == 0 ? 1 : 0).championId();
        PlayerManualSelectionEvidence forgedChampion = new PlayerManualSelectionEvidence(
                manual.controlledSide(), manual.turn(), manual.actionType(), anotherChampion,
                manual.stateBeforeHash(), manual.selectableSetIdentity(),
                manual.legalityResult(), manual.clientActionId());
        assertRejected(withTurn(playerIndex, new DraftTurnControlEvidence(
                player.turn(), player.side(), player.actionType(), player.championId(),
                player.authority(), player.stateBeforeHash(), player.stateAfterHash(),
                null, forgedChampion)), 73L, blue, red, "GEN", "T1");

        DraftSelectionTrace unrelatedAiTrace = completed.turnEvidence().stream()
                .filter(value -> value.authority() == DraftDecisionAuthority.AI)
                .map(DraftTurnControlEvidence::autoSelectionTrace).findFirst().orElseThrow();
        assertRejected(withTurn(playerIndex, new DraftTurnControlEvidence(
                player.turn(), player.side(), player.actionType(), player.championId(),
                DraftDecisionAuthority.AI, player.stateBeforeHash(), player.stateAfterHash(),
                unrelatedAiTrace, null)), 73L, blue, red, "GEN", "T1");
    }

    @Test
    void boundaryRejectsAuthoritativeAiTraceAndStateHashTampering() {
        DraftTurnControlEvidence ai = completed.turnEvidence().stream()
                .filter(value -> value.authority() == DraftDecisionAuthority.AI)
                .findFirst().orElseThrow();
        int aiIndex = ai.turn() - 1;
        DraftSelectionTrace trace = ai.autoSelectionTrace();
        ArrayList<DraftSelectionPoolEntry> pool = new ArrayList<>(trace.eligiblePool());
        DraftSelectionPoolEntry first = pool.getFirst();
        pool.set(0, new DraftSelectionPoolEntry(
                first.championId(), first.canonicalRank(), first.rawFinalSearchScore() + 0.25,
                first.canonicalFinalScore(), first.canonicalScoreLoss(), first.rankWeight()));
        DraftSelectionTrace forgedTrace = new DraftSelectionTrace(
                trace.policyId(), trace.policyMode(), trace.policyHash(),
                trace.selectionContextHash(), trace.turn(), trace.side(), trace.actionType(),
                trace.bestCandidateId(), trace.bestCanonicalScore(), pool,
                trace.selectedChampionId(), trace.selectedRank(),
                trace.selectedCanonicalScoreLoss(), trace.drawBucket(),
                trace.totalEligibleWeight(), trace.reason());
        assertRejected(withTurn(aiIndex, new DraftTurnControlEvidence(
                ai.turn(), ai.side(), ai.actionType(), ai.championId(), ai.authority(),
                ai.stateBeforeHash(), ai.stateAfterHash(), forgedTrace, null)),
                73L, blue, red, "GEN", "T1");

        assertRejected(withTurn(aiIndex, new DraftTurnControlEvidence(
                ai.turn(), ai.side(), ai.actionType(), ai.championId(), ai.authority(),
                "e".repeat(64), ai.stateAfterHash(), ai.autoSelectionTrace(), null)),
                73L, blue, red, "GEN", "T1");
    }

    @Test
    void boundaryRejectsFinalRolePlayerAssignmentAndRosterSeedContextTampering() {
        LinkedHashMap<com.lolfm.champion.ChampionId, Position> blueRoles =
                new LinkedHashMap<>(completed.blueFinalRoleAssignments());
        var roleEntries = new ArrayList<>(blueRoles.entrySet());
        var firstRole = roleEntries.get(0);
        var secondRole = roleEntries.get(1);
        blueRoles.put(firstRole.getKey(), secondRole.getValue());
        blueRoles.put(secondRole.getKey(), firstRole.getValue());
        assertRejected(copy(completed.turnEvidence(), blueRoles,
                        completed.redFinalRoleAssignments(), completed.matchChampionAssignments()),
                73L, blue, red, "GEN", "T1");

        ArrayList<ChampionAssignment> assignments = new ArrayList<>(
                completed.matchChampionAssignments().asMap().values());
        ChampionAssignment first = assignments.get(0);
        ChampionAssignment second = assignments.get(1);
        assignments.set(0, new ChampionAssignment(
                first.playerKey(), second.championId(), first.selectedPosition()));
        assignments.set(1, new ChampionAssignment(
                second.playerKey(), first.championId(), second.selectedPosition()));
        MatchChampionAssignments forgedAssignments = new MatchChampionAssignments(
                assignments, ChampionSelectionMode.EXPLICIT);
        assertRejected(copy(completed.turnEvidence(), completed.blueFinalRoleAssignments(),
                        completed.redFinalRoleAssignments(), forgedAssignments),
                73L, blue, red, "GEN", "T1");

        assertRejected(completed, 74L, blue, red, "GEN", "T1");
        Team differentRed = teams.assemble("DK");
        assertRejected(completed, 73L, blue, differentRed, "GEN", "DK");
    }

    private PlayerControlledDraftResult withTurn(
            int index, DraftTurnControlEvidence replacement
    ) {
        ArrayList<DraftTurnControlEvidence> turns = new ArrayList<>(completed.turnEvidence());
        turns.set(index, replacement);
        return copy(turns, completed.blueFinalRoleAssignments(),
                completed.redFinalRoleAssignments(), completed.matchChampionAssignments());
    }

    private PlayerControlledDraftResult copy(
            List<DraftTurnControlEvidence> turns,
            Map<com.lolfm.champion.ChampionId, Position> blueRoles,
            Map<com.lolfm.champion.ChampionId, Position> redRoles,
            MatchChampionAssignments assignments
    ) {
        return new PlayerControlledDraftResult(
                completed.ruleSet(), completed.controlledSide(), completed.blueBans(),
                completed.redBans(), completed.bluePicks(), completed.redPicks(), turns,
                blueRoles, redRoles, assignments, completed.hardFearlessExclusions(),
                completed.draftMetaVersion(), completed.requiredLegalRoleKeyHash(),
                completed.actualLegalRoleKeyHash());
    }

    private void assertRejected(
            PlayerControlledDraftResult result, long seed,
            Team selectedBlue, Team selectedRed,
            String blueCode, String redCode
    ) {
        assertThatThrownBy(() -> boundary.validateAndCreateInput(
                blueCode, selectedBlue, redCode, selectedRed, seed, result))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
