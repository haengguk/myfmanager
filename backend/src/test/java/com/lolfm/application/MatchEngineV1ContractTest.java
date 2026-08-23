package com.lolfm.application;

import static com.lolfm.testing.CompleteTimelineAssertions.assertCompleteTimelineEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.composition.TeamCompositionGameplayMode;
import com.lolfm.domain.CombatSource;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.PlayerSnapshot;
import com.lolfm.domain.Position;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import com.lolfm.simulator.JungleClearContribution;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.SimulationOptions;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatchEngineV1ContractTest {
    private static final long SEED = 73L;

    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired MatchEngineV1InputFactory inputs;
    @Autowired MatchEngineV1 engine;
    @Autowired MatchEngineV1Canonicalizer canonicalizer;

    private RealDraftMatchResult legacy;
    private MatchEngineV1Input input;
    private MatchEngineV1.MatchEngineV1Execution execution;
    private MatchEngineV1Output replay;
    private MatchEngineV1Output diagnosticsOff;
    private MatchEngineV1Output orchestratedV1;

    @BeforeAll
    void executeRepresentativeFixedMatch() {
        legacy = orchestrator.orchestrate("GEN", "T1", SEED);
        input = inputs.fromRealDraft(
                legacy.blueTeamCode(), legacy.blueTeam(),
                legacy.redTeamCode(), legacy.redTeam(), legacy.matchSeed(),
                legacy.seriesGameNumber(), legacy.hardFearlessExclusionsBeforeDraft(),
                legacy.draftResult());
        execution = engine.executeDetailed(input, SimulationInstrumentation.enabled());
        replay = engine.execute(input);
        diagnosticsOff = engine.execute(input, SimulationInstrumentation.disabled());
        orchestratedV1 = orchestrator.orchestrateV1("GEN", "T1", SEED);
    }

    @Test
    void productionPolicyFreezesApprovedBaselineAndSeparatesLowLevelDefaults() {
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();

        assertThat(policy.retainedRuntimeProfileId())
                .isEqualTo(SimulationRuntimeProfileId.BASELINE_V1);
        assertThat(policy.configurationHash())
                .isEqualTo("c8cc557bd721228c473e30d31b7258510f9608a18098578bc1da36e603536215");
        assertThat(policy.activeGameplayRulesVersion())
                .isEqualTo("MATCH_SIMULATOR_PRE_JUNGLE_RULES_V2");
        assertThat(policy.engineImplementationVersion())
                .isEqualTo("MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V6");
        assertThat(policy.gameplayConfiguration().championMatchupMode())
                .isEqualTo(ChampionMatchupMode.OFF);
        assertThat(policy.gameplayConfiguration().teamCompositionGameplayMode())
                .isEqualTo(TeamCompositionGameplayMode.OFF);
        assertThat(policy.gameplayConfiguration().jungleClearContribution())
                .isEqualTo(JungleClearContribution.DISABLED_NOT_INTEGRATED);
        assertThat(policy.economyCandidateActivation()).isFalse();
        assertThat(policy.tempoCandidateActivation()).isFalse();
        assertThat(policy.diagnosticsExcludedFromGameplayIdentity()).isTrue();

        SimulationOptions lowLevel = SimulationOptions.productionDefaults();
        assertThat(lowLevel.championMatchupMode()).isEqualTo(ChampionMatchupMode.GEOMETRIC_V2);
        assertThat(lowLevel.teamCompositionGameplayMode())
                .isEqualTo(TeamCompositionGameplayMode.PRODUCTION_V2);
        assertThat(lowLevel.jungleClearContribution())
                .isEqualTo(JungleClearContribution.DISABLED_NOT_INTEGRATED);
        assertThat(MatchEngineV1Policy.isLowLevelProductionDefaultsAuthoritative()).isFalse();
    }

    @Test
    void inputIsCompleteCanonicalAndUsesStructuredRosterAndAssignmentIdentity() {
        assertThat(input.schemaVersion()).isEqualTo(MatchEngineV1Input.SCHEMA);
        assertThat(input.blueTeam().lineup()).hasSize(5);
        assertThat(input.redTeam().lineup()).hasSize(5);
        assertThat(input.championAssignments()).hasSize(10);
        assertThat(input.blueTeam().lineup()).extracting(MatchEngineV1Input.PlayerInput::position)
                .containsExactlyInAnyOrder(Position.values());
        assertThat(input.redTeam().lineup()).extracting(MatchEngineV1Input.PlayerInput::position)
                .containsExactlyInAnyOrder(Position.values());
        assertThat(input.championAssignments()).allSatisfy(assignment -> {
            MatchEngineV1Input.PlayerInput player = input.player(
                    assignment.teamSide(), assignment.position());
            assertThat(assignment.playerId()).isEqualTo(player.playerId());
        });
        assertThat(input.inputHash()).matches("[0-9a-f]{64}");
        assertThat(input.canonicalGameplaySerialization()).endsWith("\n")
                .doesNotContain(input.blueTeam().lineup().getFirst().displayName());
        assertThat(input.finalDraft().finalDraftHash())
                .isEqualTo(legacy.executionProvenance().finalDraftHash());
        assertThat(input.finalDraft().finalAssignmentHash())
                .isEqualTo(legacy.executionProvenance().finalAssignmentHash());
        assertThat(execution.output().hasValidOutputHash(canonicalizer)).isTrue();
    }

    @Test
    void invalidRosterAssignmentDraftAndPolicyAreRejectedBeforeExecution() {
        assertThatThrownBy(() -> new MatchEngineV1Input.TeamInput(
                "GEN", "GEN", TeamSide.BLUE,
                input.blueTeam().lineup().subList(0, 4)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LINEUP_CARDINALITY");

        ArrayList<MatchEngineV1Input.PlayerInput> duplicatePositions =
                new ArrayList<>(input.blueTeam().lineup());
        duplicatePositions.set(1, duplicatePositions.getFirst());
        assertThatThrownBy(() -> new MatchEngineV1Input.TeamInput(
                "GEN", "GEN", TeamSide.BLUE, duplicatePositions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("POSITION_COVERAGE");

        MatchEngineV1Input.PlayerInput blueTop = input.player(TeamSide.BLUE, Position.TOP);
        MatchEngineV1Input.PlayerInput redTop = input.player(TeamSide.RED, Position.TOP);
        ArrayList<MatchEngineV1Input.PlayerInput> duplicateIds =
                new ArrayList<>(input.redTeam().lineup());
        duplicateIds.set(duplicateIds.indexOf(redTop), new MatchEngineV1Input.PlayerInput(
                blueTop.playerId(), redTop.displayName(), TeamSide.RED, Position.TOP,
                redTop.ratings(), redTop.proficiencies()));
        MatchEngineV1Input.TeamInput duplicateIdTeam = new MatchEngineV1Input.TeamInput(
                "T1", "T1", TeamSide.RED, duplicateIds);
        assertThatThrownBy(() -> copy(input, input.blueTeam(), duplicateIdTeam,
                input.championAssignments(), input.finalDraft(), input.productionPolicy()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DUPLICATE_PLAYER_ID");

        MatchEngineV1Input.TeamInput collidingTeam = new MatchEngineV1Input.TeamInput(
                "GEN", input.redTeam().displayName(), TeamSide.RED, input.redTeam().lineup());
        assertThatThrownBy(() -> copy(input, input.blueTeam(), collidingTeam,
                input.championAssignments(), input.finalDraft(), input.productionPolicy()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TEAM_IDENTITY_COLLISION");

        assertThatThrownBy(() -> copy(input, input.blueTeam(), input.redTeam(),
                input.championAssignments().subList(0, 9), input.finalDraft(),
                input.productionPolicy()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ASSIGNMENT_CARDINALITY");

        MatchEngineV1Input.DraftInput mismatchedDraft = copyDraft(
                input.finalDraft(), input.finalDraft().decisions(),
                input.finalDraft().bluePicks(), input.finalDraft().redPicks(),
                input.finalDraft().draftDecisionHash(),
                input.finalDraft().finalAssignmentHash(), "0".repeat(64));
        assertThatThrownBy(() -> copy(input, input.blueTeam(), input.redTeam(),
                input.championAssignments(), mismatchedDraft, input.productionPolicy()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FINAL_DRAFT_HASH_MISMATCH");

        MatchEngineV1Policy.Requirement candidate = new MatchEngineV1Policy.Requirement(
                MatchEngineV1Policy.POLICY_ID,
                SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1,
                SimulationRuntimeProfiles.resolve(
                        SimulationRuntimeProfileId.FULL_SYSTEM_WITH_JUNGLE_ECONOMY_CANDIDATE_V1)
                        .configurationHash(), true, false);
        assertThatThrownBy(() -> copy(input, input.blueTeam(), input.redTeam(),
                input.championAssignments(), input.finalDraft(), candidate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PRODUCTION_POLICY_MISMATCH");
    }

    @Test
    void illegalChampionFailsBeforeSimulatorRandomAndSeriesMutation() {
        MatchEngineV1Input illegal = withIllegalChampion(input);
        ConfiguredMatchSimulatorFactory simulators = mock(ConfiguredMatchSimulatorFactory.class);
        SimulationProvenanceService provenance = mock(SimulationProvenanceService.class);
        MatchEngineV1Projector projector = mock(MatchEngineV1Projector.class);
        ChampionCatalog champions = mock(ChampionCatalog.class);
        when(champions.supports(new ChampionRoleKey(
                new ChampionId("not-a-real-champion"), Position.TOP))).thenReturn(false);
        MatchEngineV1 isolated = new MatchEngineV1(
                simulators, provenance, projector, champions);
        SeriesDraftHistory history = new SeriesDraftHistory();

        assertThatThrownBy(() -> isolated.execute(illegal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ILLEGAL_CHAMPION_ASSIGNMENT");
        verifyNoInteractions(simulators, provenance, projector);
        assertThat(history.committedGameCount()).isZero();
        assertThat(history.consumedPicks()).isEmpty();
    }

    @Test
    void outputSummaryUsesStructuredFinalSnapshotAndDoesNotDoubleCountEvents() {
        MatchEngineV1Output output = execution.output();
        MatchSnapshot finalSnapshot = legacy.timeline().getSnapshots().getLast();

        assertThat(output.resultSummary().winner()).isEqualTo(output.timeline().winner());
        assertThat(output.resultSummary().durationSeconds())
                .isEqualTo(legacy.timeline().getDurationSeconds());
        assertThat(output.resultSummary().teams()).anySatisfy(team -> {
            if (team.teamSide() == TeamSide.BLUE) {
                assertThat(team.kills()).isEqualTo(finalSnapshot.getBlueKills());
                assertThat(team.totalGold()).isEqualTo(finalSnapshot.getBlueGold());
                assertThat(team.dragons()).isEqualTo(finalSnapshot.getBlueDragons());
                assertThat(team.towersDestroyed()).isEqualTo(
                        finalSnapshot.getBlueTowersDestroyed());
            }
        });
        assertThat(output.resultSummary().players()).hasSize(10).allSatisfy(player -> {
            PlayerSnapshot snapshot = finalSnapshot.getPlayerSnapshots().stream()
                    .filter(value -> value.getTeamSide() == player.teamSide()
                            && value.getPosition() == player.position())
                    .findFirst().orElseThrow();
            assertThat(player.playerId()).isEqualTo(
                    input.player(player.teamSide(), player.position()).playerId());
            assertThat(player.championId().value()).isEqualTo(snapshot.getChampionId());
            assertThat(player.kills()).isEqualTo(snapshot.getKills());
            assertThat(player.deaths()).isEqualTo(snapshot.getDeaths());
            assertThat(player.assists()).isEqualTo(snapshot.getAssists());
            assertThat(player.cs()).isEqualTo(snapshot.getCs());
            assertThat(player.gold()).isEqualTo(snapshot.getGold());
            assertThat(player.totalExperience()).isEqualTo(snapshot.getTotalExperience());
            assertThat(player.level()).isEqualTo(snapshot.getLevel());
        });
        int teamKills = output.resultSummary().teams().stream()
                .mapToInt(MatchEngineV1Output.TeamResultV1::kills).sum();
        long structuredKillEvents = output.timeline().events().stream()
                .filter(value -> value.eventType() == MatchEventType.KILL).count();
        long summaryActionEvents = output.timeline().events().stream()
                .filter(value -> value.eventType() == MatchEventType.LANE_COMBAT
                        || value.eventType() == MatchEventType.JUNGLE_GANK
                        || value.eventType() == MatchEventType.COUNTER_GANK
                        || value.eventType() == MatchEventType.ROAM
                        || value.eventType() == MatchEventType.TEAMFIGHT_RESULT)
                .count();
        long structuredAssistLinks = output.timeline().events().stream()
                .filter(value -> value.eventType() == MatchEventType.KILL)
                .mapToLong(value -> value.assistantPlayerIds().size()).sum();
        assertThat(structuredKillEvents).isEqualTo(teamKills);
        assertThat(summaryActionEvents).isPositive();
        assertThat(structuredAssistLinks).isPositive();
        assertThat(structuredKillEvents + summaryActionEvents).isGreaterThan(teamKills);
    }

    @Test
    void projectionIsDeeplyImmutableAndDetachedFromMutableTimelineSources() {
        MatchEngineV1.MatchEngineV1Execution local = engine.executeDetailed(
                input, SimulationInstrumentation.enabled());
        MatchEngineV1Output output = local.output();
        String before = canonicalizer.canonicalJson(output);

        local.legacyTimeline().getEvents().getFirst().setCombatSource(CombatSource.TEAMFIGHT);
        local.legacyTimeline().getSnapshots().getFirst().setProgression(null);

        assertThat(canonicalizer.canonicalJson(output)).isEqualTo(before);
        assertThatThrownBy(() -> output.timeline().events().add(
                output.timeline().events().getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> output.timeline().snapshots().getFirst()
                .structuredState().put("mutation", true))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> output.timeline().events().stream()
                .filter(value -> !value.structuredData().isEmpty()).findFirst().orElseThrow()
                .structuredData().put("mutation", true))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> input.blueTeam().lineup().add(
                input.blueTeam().lineup().getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void sameInputIsExactAndDiagnosticsRemainObservational() {
        assertThat(replay).isEqualTo(execution.output());
        assertThat(replay.outputHash()).isEqualTo(execution.output().outputHash());
        assertThat(diagnosticsOff.timeline()).isEqualTo(execution.output().timeline());
        assertThat(diagnosticsOff.resultSummary()).isEqualTo(execution.output().resultSummary());
        assertThat(diagnosticsOff.executionProvenance().configurationHash())
                .isEqualTo(execution.output().executionProvenance().configurationHash());
        assertThat(diagnosticsOff.executionProvenance().replayProvenanceHash())
                .isEqualTo(execution.output().executionProvenance().replayProvenanceHash());
        assertThat(diagnosticsOff.executionProvenance().randomFingerprint())
                .isEqualTo(execution.output().executionProvenance().randomFingerprint());
        assertThat(diagnosticsOff.structuredTimelineHash())
                .isEqualTo(execution.output().structuredTimelineHash());
        assertThat(diagnosticsOff.outputHash()).isEqualTo(execution.output().outputHash());
        assertThat(diagnosticsOff.executionProvenance().instrumentation().diagnosticsEnabled())
                .isFalse();
    }

    @Test
    void legacyAndV1RealDraftPathsHaveExactGameplayAndProvenanceParity() {
        assertCompleteTimelineEquals(legacy.timeline(), execution.legacyTimeline());
        assertThat(execution.executionProvenance()).isEqualTo(legacy.executionProvenance());
        assertThat(execution.output().executionProvenance()).isEqualTo(
                legacy.executionProvenance());
        assertThat(orchestratedV1).isEqualTo(execution.output());

        SeriesDraftHistory history = new SeriesDraftHistory();
        MatchEngineV1Output seriesGame = orchestrator.orchestrateV1("GEN", "T1", history, SEED);
        assertThat(seriesGame.finalDraft().seriesGameNumber()).isEqualTo(1);
        assertThat(history.committedGameCount()).isEqualTo(1);
        assertThat(history.consumedPicks()).hasSize(10);
    }

    @Test
    void displayLabelsDoNotOwnGameplayIdentityOrOutputHash() {
        MatchEngineV1Input renamed = renamedDisplayInput(input);
        MatchEngineV1Output renamedOutput = engine.execute(renamed);

        assertThat(renamed.inputHash()).isEqualTo(input.inputHash());
        assertThat(renamed.rosterIdentityHash()).isEqualTo(input.rosterIdentityHash());
        assertThat(renamedOutput.resultSummary()).isEqualTo(execution.output().resultSummary());
        assertThat(renamedOutput.executionProvenance().replayProvenanceHash())
                .isEqualTo(execution.output().executionProvenance().replayProvenanceHash());
        assertThat(renamedOutput.executionProvenance().randomFingerprint())
                .isEqualTo(execution.output().executionProvenance().randomFingerprint());
        assertThat(renamedOutput.structuredTimelineHash())
                .isEqualTo(execution.output().structuredTimelineHash());
        assertThat(renamedOutput.outputHash()).isEqualTo(execution.output().outputHash());
    }

    private static MatchEngineV1Input copy(
            MatchEngineV1Input source,
            MatchEngineV1Input.TeamInput blue,
            MatchEngineV1Input.TeamInput red,
            List<MatchEngineV1Input.ChampionAssignmentInput> assignments,
            MatchEngineV1Input.DraftInput draft,
            MatchEngineV1Policy.Requirement policy
    ) {
        return new MatchEngineV1Input(
                source.schemaVersion(), source.matchIdentity(), blue, red, assignments, draft,
                source.matchSeed(), source.rosterIdentityHash(),
                source.seriesHistoryBeforeHash(), policy);
    }

    private static MatchEngineV1Input.DraftInput copyDraft(
            MatchEngineV1Input.DraftInput source,
            List<MatchEngineV1Input.DraftDecisionInput> decisions,
            List<ChampionId> bluePicks,
            List<ChampionId> redPicks,
            String decisionHash,
            String assignmentHash,
            String finalDraftHash
    ) {
        return new MatchEngineV1Input.DraftInput(
                source.seriesGameNumber(), source.draftRuleSetIdentity(),
                source.draftRuleSetHash(), source.draftScoringPolicyHash(), decisions,
                decisionHash, source.blueBans(), source.redBans(), bluePicks, redPicks,
                source.hardFearlessExclusions(), source.draftMetaVersion(),
                source.requiredLegalRoleKeyHash(), source.actualLegalRoleKeyHash(),
                assignmentHash, finalDraftHash);
    }

    private static MatchEngineV1Input withIllegalChampion(MatchEngineV1Input source) {
        ChampionId illegalChampion = new ChampionId("not-a-real-champion");
        MatchEngineV1Input.ChampionAssignmentInput replaced = source.assignment(
                TeamSide.BLUE, Position.TOP);
        ArrayList<MatchEngineV1Input.ChampionAssignmentInput> assignments =
                new ArrayList<>(source.championAssignments());
        assignments.set(assignments.indexOf(replaced),
                new MatchEngineV1Input.ChampionAssignmentInput(
                        replaced.playerId(), replaced.teamSide(), replaced.position(),
                        illegalChampion));
        List<ChampionId> bluePicks = source.finalDraft().bluePicks().stream()
                .map(value -> value.equals(replaced.championId()) ? illegalChampion : value).toList();
        List<MatchEngineV1Input.DraftDecisionInput> decisions =
                source.finalDraft().decisions().stream().map(value ->
                        value.selectedChampionId().equals(replaced.championId())
                                ? new MatchEngineV1Input.DraftDecisionInput(
                                value.turn(), value.side(), value.actionType(), illegalChampion)
                                : value).toList();
        String decisionHash = MatchEngineV1Input.draftDecisionHash(decisions);
        String assignmentHash = MatchEngineV1Input.finalAssignmentHash(assignments);
        MatchEngineV1Input.DraftInput unsigned = copyDraft(
                source.finalDraft(), decisions, bluePicks, source.finalDraft().redPicks(),
                decisionHash, assignmentHash, "0".repeat(64));
        String finalDraftHash = MatchEngineV1Input.finalDraftHash(unsigned, assignments);
        MatchEngineV1Input.DraftInput draft = copyDraft(
                source.finalDraft(), decisions, bluePicks, source.finalDraft().redPicks(),
                decisionHash, assignmentHash, finalDraftHash);
        return copy(source, source.blueTeam(), source.redTeam(), assignments, draft,
                source.productionPolicy());
    }

    private static MatchEngineV1Input renamedDisplayInput(MatchEngineV1Input source) {
        MatchEngineV1Input.TeamInput blue = renamedTeam(source.blueTeam(), "Blue display renamed");
        MatchEngineV1Input.TeamInput red = renamedTeam(source.redTeam(), "Red display renamed");
        return copy(source, blue, red, source.championAssignments(), source.finalDraft(),
                source.productionPolicy());
    }

    private static MatchEngineV1Input.TeamInput renamedTeam(
            MatchEngineV1Input.TeamInput source, String teamDisplay
    ) {
        List<MatchEngineV1Input.PlayerInput> lineup = source.lineup().stream()
                .map(value -> new MatchEngineV1Input.PlayerInput(
                        value.playerId(), "renamed-" + value.position(), value.teamSide(),
                        value.position(), value.ratings(), value.proficiencies())).toList();
        return new MatchEngineV1Input.TeamInput(
                source.teamIdentity(), teamDisplay, source.teamSide(), lineup);
    }
}
