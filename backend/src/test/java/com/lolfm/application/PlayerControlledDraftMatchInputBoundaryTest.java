package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionAssignment;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionSelectionMode;
import com.lolfm.champion.MatchChampionAssignments;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.draft.DraftDecisionAuthority;
import com.lolfm.draft.DraftSelectionPoolEntry;
import com.lolfm.draft.DraftSelectionTrace;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.DraftTurnControlEvidence;
import com.lolfm.draft.DraftResourceSet;
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
    @Autowired ObjectMapper mapper;
    @Autowired ChampionCatalog champions;
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
                "GEN", "T1", 73L, completed);
        MatchEngineV1Output output = matches.execute(
                input, SimulationInstrumentation.enabled());

        for (Position position : Position.values()) {
            assertThat(input.player(TeamSide.BLUE, position).playerId()).isEqualTo(
                    playerAt(blue, position).requirePlayerId());
            assertThat(input.player(TeamSide.RED, position).playerId()).isEqualTo(
                    playerAt(red, position).requirePlayerId());
        }
        assertThat(input.finalDraft().controlEvidence()).isNotNull();
        assertThat(output.finalDraft().finalDraftHash())
                .isEqualTo(input.finalDraft().finalDraftHash());
        assertThat(output.hasValidOutputHash(canonicalizer)).isTrue();
        assertThat(output.executionProvenance().matchSeed()).isEqualTo(73L);
    }

    @Test
    void rawUncheckedFactoryCannotBeCalledAsPublicProductionApi() {
        var publicBoundary = java.util.Arrays.stream(
                        PlayerControlledDraftMatchInputBoundary.class.getMethods())
                .filter(method -> method.getName().equals("validateAndCreateInput"))
                .findFirst().orElseThrow();
        assertThat(publicBoundary.getParameterTypes()).containsExactly(
                String.class, String.class, long.class,
                PlayerControlledDraftResult.class);
        assertThat(java.util.Arrays.stream(publicBoundary.getParameterTypes()))
                .doesNotContain(Team.class);
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
    void boundaryRejectsSameOrUnknownTeamCodesBeforeInputCreation() {
        assertRejected(completed, 73L, "GEN", "gen");
        assertRejected(completed, 73L, "UNKNOWN", "T1");
        assertRejected(completed, 73L, "GEN", "UNKNOWN");
    }

    @Test
    void boundaryBindsAllThreeDraftMetaFieldsToActiveResources() {
        var activeMeta = DraftResourceSet.loadDefault(mapper, champions).meta();
        assertThat(completed.draftMetaVersion()).isEqualTo(activeMeta.metaVersion());
        assertThat(completed.requiredLegalRoleKeyHash())
                .isEqualTo(activeMeta.requiredLegalRoleKeyHash());
        assertThat(completed.actualLegalRoleKeyHash())
                .isEqualTo(activeMeta.actualLegalRoleKeyHash());

        assertMetaRejected(copyWithMeta(
                completed.draftMetaVersion() + "-forged",
                completed.requiredLegalRoleKeyHash(), completed.actualLegalRoleKeyHash()),
                73L, "GEN", "T1");
        assertMetaRejected(copyWithMeta(
                completed.draftMetaVersion(), differentHash(
                        completed.requiredLegalRoleKeyHash()),
                completed.actualLegalRoleKeyHash()), 73L, "GEN", "T1");
        assertMetaRejected(copyWithMeta(
                completed.draftMetaVersion(), completed.requiredLegalRoleKeyHash(),
                differentHash(completed.actualLegalRoleKeyHash())), 73L, "GEN", "T1");
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
        assertRejected(selectableTampered, 73L, "GEN", "T1");

        var anotherChampion = completed.turnEvidence().get(
                playerIndex == 0 ? 1 : 0).championId();
        PlayerManualSelectionEvidence forgedChampion = new PlayerManualSelectionEvidence(
                manual.controlledSide(), manual.turn(), manual.actionType(), anotherChampion,
                manual.stateBeforeHash(), manual.selectableSetIdentity(),
                manual.legalityResult(), manual.clientActionId());
        assertRejected(withTurn(playerIndex, new DraftTurnControlEvidence(
                player.turn(), player.side(), player.actionType(), player.championId(),
                player.authority(), player.stateBeforeHash(), player.stateAfterHash(),
                null, forgedChampion)), 73L, "GEN", "T1");

        DraftSelectionTrace unrelatedAiTrace = completed.turnEvidence().stream()
                .filter(value -> value.authority() == DraftDecisionAuthority.AI)
                .map(DraftTurnControlEvidence::autoSelectionTrace).findFirst().orElseThrow();
        assertRejected(withTurn(playerIndex, new DraftTurnControlEvidence(
                player.turn(), player.side(), player.actionType(), player.championId(),
                DraftDecisionAuthority.AI, player.stateBeforeHash(), player.stateAfterHash(),
                unrelatedAiTrace, null)), 73L, "GEN", "T1");
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
                73L, "GEN", "T1");

        assertRejected(withTurn(aiIndex, new DraftTurnControlEvidence(
                ai.turn(), ai.side(), ai.actionType(), ai.championId(), ai.authority(),
                "e".repeat(64), ai.stateAfterHash(), ai.autoSelectionTrace(), null)),
                73L, "GEN", "T1");
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
                73L, "GEN", "T1");

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
                73L, "GEN", "T1");

        assertRejected(completed, 74L, "GEN", "T1");
        assertRejected(completed, 73L, "GEN", "DK");
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

    private PlayerControlledDraftResult copyWithMeta(
            String draftMetaVersion,
            String requiredLegalRoleKeyHash,
            String actualLegalRoleKeyHash
    ) {
        return new PlayerControlledDraftResult(
                completed.ruleSet(), completed.controlledSide(), completed.blueBans(),
                completed.redBans(), completed.bluePicks(), completed.redPicks(),
                completed.turnEvidence(), completed.blueFinalRoleAssignments(),
                completed.redFinalRoleAssignments(), completed.matchChampionAssignments(),
                completed.hardFearlessExclusions(), draftMetaVersion,
                requiredLegalRoleKeyHash, actualLegalRoleKeyHash);
    }

    private static String differentHash(String value) {
        String candidate = "a".repeat(64);
        return candidate.equals(value) ? "b".repeat(64) : candidate;
    }

    private static com.lolfm.domain.Player playerAt(Team team, Position position) {
        return team.getPlayers().stream()
                .filter(player -> player.getPosition() == position)
                .findFirst().orElseThrow();
    }

    private void assertRejected(
            PlayerControlledDraftResult result, long seed,
            String blueCode, String redCode
    ) {
        assertThatThrownBy(() -> boundary.validateAndCreateInput(
                blueCode, redCode, seed, result))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertMetaRejected(
            PlayerControlledDraftResult result, long seed,
            String blueCode, String redCode
    ) {
        assertThatThrownBy(() -> boundary.validateAndCreateInput(
                blueCode, redCode, seed, result))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PLAYER_DRAFT_META_RESOURCE_IDENTITY_MISMATCH");
    }
}
