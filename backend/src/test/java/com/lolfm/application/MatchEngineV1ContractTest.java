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
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.CombatSource;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.MatchSnapshot;
import com.lolfm.domain.PlayerSnapshot;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.Position;
import com.lolfm.draft.DraftSelectionPoolEntry;
import com.lolfm.draft.DraftSelectionTrace;
import com.lolfm.draft.DraftSelectionTraceHasher;
import com.lolfm.draft.SeriesDraftHistory;
import com.lolfm.simulator.ConfiguredMatchSimulatorFactory;
import com.lolfm.simulator.JungleClearContribution;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.PlayerMatchPerformanceSnapshot;
import com.lolfm.simulator.PlayerRatingRuleConfig;
import com.lolfm.simulator.SimulationInstrumentation;
import com.lolfm.simulator.SimulationOptions;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import com.lolfm.simulator.SimulationRuntimeProfiles;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.main.banner-mode=off", "logging.level.root=ERROR",
                "spring.main.lazy-initialization=true"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatchEngineV1ContractTest {
    private static final long SEED = 73L;

    @Autowired RealDraftMatchOrchestrator orchestrator;
    @Autowired MatchEngineV1InputFactory inputs;
    @Autowired MatchEngineV1 engine;
    @Autowired MatchEngineV1Canonicalizer canonicalizer;
    @Autowired RealMatchApiV1ResponseMapper realMatchResponses;

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
    void productionPolicyActivatesApprovedMatchupCompositionAndSeparatesLowLevelDefaults() {
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();

        assertThat(policy.schemaVersion())
                .isEqualTo("MATCH_ENGINE_V1_PRODUCTION_POLICY_V3");
        assertThat(policy.policyId())
                .isEqualTo("MATCH_ENGINE_V1_MATCHUP_COMPOSITION_ACCEPTED_PRODUCTION_POLICY");
        assertThat(policy.activationDecisionSchema()).isEqualTo(
                "MATCH_ENGINE_V9_MATCHUP_COMPOSITION_PRODUCTION_ACTIVATION_DECISION_V1");
        assertThat(policy.activationDecisionCode()).isEqualTo(
                "PRODUCT_DECISION_ACCEPT_WITH_KNOWN_DIAGNOSTIC_LIMITATION");
        assertThat(policy.knownDiagnosticLimitation()).isEqualTo(
                "MATCHUP_CAUSAL_LINEAGE_UNRESOLVED_399_OF_400_CALIBRATION_PUBLIC_DIVERGENCES");
        assertThat(policy.acceptanceStatus()).isEqualTo(
                "PRODUCT_ACCEPTED_WITH_KNOWN_LIMITATIONS_NOT_STATISTICAL_HOLDOUT");
        assertThat(policy.knownDiagnosticLimitations()).containsExactly(
                "MATCHUP_CAUSAL_LINEAGE_UNRESOLVED_399_OF_400_CALIBRATION_PUBLIC_DIVERGENCES",
                "COMPOSITION_NEXUS_ENDING_SENSITIVITY_9_25_PERCENT_EXCEEDS_PROPOSED_7_5_PERCENT_TOLERANCE");
        assertThat(policy.statisticalHoldoutApproved()).isFalse();
        assertThat(policy.rollbackProfileId()).isEqualTo(SimulationRuntimeProfileId.BASELINE_V1);
        assertThat(policy.rollbackMode()).isEqualTo("EXPLICIT_VERSIONED_POLICY_CHANGE_ONLY");
        assertThat(policy.automaticFallback()).isFalse();
        assertThat(policy.retainedRuntimeProfileId())
                .isEqualTo(SimulationRuntimeProfileId.PRODUCTION_MATCHUP_COMPOSITION_V1);
        assertThat(policy.configurationHash())
                .isEqualTo("caaf76274dc148040b0a95eae1ed5181790b2fc840f45af9b109ea7951c1fd5d");
        assertThat(policy.policyHash()).isEqualTo(
                "3afaa399f7c2b20a940c7cfd7510f6c7962ba43eec575cc75ed55218d53f0ce9");
        assertThat(policy.activeGameplayRulesVersion())
                .isEqualTo("MATCH_SIMULATOR_PRE_JUNGLE_RULES_V4");
        assertThat(policy.engineImplementationVersion())
                .isEqualTo("MATCH_SIMULATOR_ENGINE_IMPLEMENTATION_V9");
        assertThat(policy.gameplayConfiguration().championMatchupMode())
                .isEqualTo(ChampionMatchupMode.GEOMETRIC_V2);
        assertThat(policy.gameplayConfiguration().teamCompositionGameplayMode())
                .isEqualTo(TeamCompositionGameplayMode.PRODUCTION_V2);
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
        assertThat(MatchEngineV1Policy
                .isLowLevelProductionDefaultsAlignedWithAuthoritativeProfile()).isTrue();
        assertThat(MatchEngineV1Policy.isLowLevelProductionDefaultsAuthoritative()).isFalse();
        assertThat(policy.lowLevelProductionDefaultsAuthoritativeApplicationDefault()).isFalse();
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
    void outputExposesExactBaseRealizedAndSelectedChampionAbilityProfile() {
        assertThat(execution.output().resultSummary().players()).allSatisfy(result -> {
            MatchEngineV1Input.PlayerInput source = input.player(
                    result.teamSide(), result.position());
            MatchEngineV1Output.PlayerAbilityProfileV1 profile = result.abilityProfile();
            ChampionRoleKey roleKey = new ChampionRoleKey(
                    result.championId(), result.position());
            int expectedProficiency = new ChampionProficiencies(
                    source.proficiencies()).get(roleKey);
            PlayerMatchPerformanceSnapshot actualPerformance = execution.playerMatchPerformances()
                    .stream()
                    .filter(value -> value.playerKey().equals(
                            new PlayerKey(result.teamSide(), result.position())))
                    .findFirst().orElseThrow();

            assertThat(profile.schemaVersion())
                    .isEqualTo(MatchEngineV1Output.PlayerAbilityProfileV1.SCHEMA);
            assertThat(profile.baseRatings()).hasSize(12);
            assertThat(profile.realizedRatings()).hasSize(12);
            assertThat(profile.realizationDeltas()).hasSize(12);
            source.ratings().forEach((skill, value) -> {
                assertThat(profile.baseRatings()).containsEntry(skill.name(), value);
                assertThat(profile.realizedRatings()).containsEntry(
                        skill.name(), actualPerformance.realizedRatings().get(skill));
                assertThat(profile.realizationDeltas().get(skill.name()))
                        .isEqualTo(profile.realizedRatings().get(skill.name()) - value);
            });
            assertThat(profile.selectedChampionProficiency()).isEqualTo(expectedProficiency);
            assertThat(profile.proficiencyExecutionAdjustment())
                    .isEqualTo(PlayerRatingRuleConfig.proficiencyAdjustment(expectedProficiency));
        });
        MatchEngineV1Output.PlayerAbilityProfileV1 first = execution.output()
                .resultSummary().players().getFirst().abilityProfile();
        assertThatThrownBy(() -> first.baseRatings().put("MUTATION", 1))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> first.realizedRatings().put("MUTATION", 1.0))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void abilityProfileMapsToRealMatchHttpContractWithoutLosingValues() {
        com.lolfm.dto.RealMatchApiV1Dtos.Response response =
                realMatchResponses.response(execution.output());

        assertThat(response.result().players()).hasSize(10);
        assertThat(response.result().players()).allSatisfy(player -> {
            MatchEngineV1Output.PlayerResultV1 source = execution.output().resultSummary()
                    .players().stream()
                    .filter(value -> value.playerId().value().equals(player.playerId()))
                    .findFirst().orElseThrow();
            assertThat(player.abilityProfile().baseRatings())
                    .isEqualTo(source.abilityProfile().baseRatings());
            assertThat(player.abilityProfile().realizedRatings())
                    .isEqualTo(source.abilityProfile().realizedRatings());
        });
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
                MatchEngineV1Policy.DRAFT_SELECTION_POLICY_ID,
                MatchEngineV1Policy.DRAFT_SELECTION_POLICY_SHA256,
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
        MatchEngineV1Input.ChampionAssignmentInput top = input.assignment(
                TeamSide.BLUE, Position.TOP);
        ConfiguredMatchSimulatorFactory simulators = mock(ConfiguredMatchSimulatorFactory.class);
        SimulationProvenanceService provenance = mock(SimulationProvenanceService.class);
        MatchEngineV1Projector projector = mock(MatchEngineV1Projector.class);
        ChampionCatalog champions = mock(ChampionCatalog.class);
        when(champions.supports(new ChampionRoleKey(
                top.championId(), Position.TOP))).thenReturn(false);
        MatchEngineV1 isolated = new MatchEngineV1(
                simulators, provenance, projector, champions);
        SeriesDraftHistory history = new SeriesDraftHistory();

        assertThatThrownBy(() -> isolated.execute(input))
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
        List<MatchEngineV1Output.EventV1> killEvents = output.timeline().events().stream()
                .filter(value -> value.eventType() == MatchEventType.KILL).toList();
        List<MatchEngineV1Output.EventV1> assistEvents = output.timeline().events().stream()
                .filter(value -> value.eventType() == MatchEventType.ASSIST).toList();
        assertThat(structuredKillEvents).isEqualTo(teamKills);
        assertThat(summaryActionEvents).isPositive();
        assertThat(structuredAssistLinks).isPositive();
        assertThat(killEvents).allSatisfy(value -> {
            assertThat(value.goldAmount()).isPositive();
            assertThat(value.actorPlayerId()).isEqualTo(value.killerPlayerId());
            assertThat(value.structuredData()).containsKey("kill");
        });
        assertThat(killEvents.stream().filter(value -> {
            Object kill = value.structuredData().get("kill");
            return kill instanceof java.util.Map<?, ?> fields
                    && Boolean.TRUE.equals(fields.get("firstBlood"));
        })).hasSize(1);
        assertThat(assistEvents).hasSize((int) structuredAssistLinks).allSatisfy(value -> {
            assertThat(value.goldAmount()).isEqualTo(150);
            assertThat(value.actorPlayerId()).isNotNull();
            assertThat(value.structuredData()).containsKey("assist");
        });
        assertThat(killEvents.stream()
                .filter(value -> value.combatSource() == CombatSource.JUNGLE_GANK))
                .allSatisfy(value -> assertThat(value.lane()).isNotNull());
        assertThat(output.timeline().events().stream()
                .filter(value -> value.eventType() == MatchEventType.DRAGON
                        || value.eventType() == MatchEventType.BARON
                        || value.eventType() == MatchEventType.ELDER))
                .allSatisfy(value -> {
                    Object decision = value.structuredData().get("objectiveDecision");
                    assertThat(decision).isInstanceOf(java.util.Map.class);
                    assertThat(value.actorSide().name()).isEqualTo(
                            ((java.util.Map<?, ?>) decision).get("captureSide"));
                });
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
    void v1ReplayProvenanceBindsCompleteRatingAndProficiencyInput() {
        MatchEngineV1Input changedInput = withChangedPlayerGameplaySnapshot(input);
        MatchEngineV1Output changedOutput = engine.execute(changedInput);
        SimulationExecutionProvenance original = execution.output().executionProvenance();
        SimulationExecutionProvenance changed = changedOutput.executionProvenance();

        assertThat(changedInput.rosterIdentityHash()).isEqualTo(input.rosterIdentityHash());
        assertThat(changedInput.finalDraft()).isEqualTo(input.finalDraft());
        assertThat(changedInput.inputHash()).isNotEqualTo(input.inputHash());
        assertThat(changed.resourceProvenance()).isEqualTo(original.resourceProvenance());
        assertThat(original.replayProvenanceHash()).isEqualTo(
                SimulationProvenanceService.matchEngineV1ReplayProvenanceHash(
                        legacy.executionProvenance().replayProvenanceHash(), input.inputHash()));
        assertThat(changed.replayProvenanceHash()).isEqualTo(
                SimulationProvenanceService.matchEngineV1ReplayProvenanceHash(
                        legacy.executionProvenance().replayProvenanceHash(),
                        changedInput.inputHash()));
        assertThat(changed.replayProvenanceHash()).isNotEqualTo(original.replayProvenanceHash());
        assertThat(changed.replayProvenanceHashAlgorithm()).isEqualTo(
                SimulationProvenanceService.MATCH_ENGINE_V1_REPLAY_PROVENANCE_HASH_ALGORITHM);
        assertThat(changedOutput.outputHash()).isNotEqualTo(execution.output().outputHash());
    }

    @Test
    void outputHashValidationRecomputesActualStructuredTimeline() {
        MatchEngineV1Output source = execution.output();
        MatchEngineV1Output.EventV1 first = source.timeline().events().getFirst();
        int changedGold = first.goldAmount() == Integer.MAX_VALUE
                ? first.goldAmount() - 1 : first.goldAmount() + 1;
        MatchEngineV1Output tamperedGameplay = withFirstEvent(source, copyEvent(
                first, changedGold, first.displayMessage()));
        MatchEngineV1Output.SnapshotV1 last = source.timeline().snapshots().getLast();
        HashMap<String, Object> changedState = new HashMap<>(last.structuredState());
        changedState.put("tamperedSnapshotMarker", true);
        MatchEngineV1Output tamperedSnapshot = withLastSnapshot(source,
                new MatchEngineV1Output.SnapshotV1(
                        last.timeSeconds(), last.blueTeam(), last.redTeam(),
                        last.players(), changedState));
        MatchEngineV1Output displayOnly = withFirstEvent(source, copyEvent(
                first, first.goldAmount(), first.displayMessage() + " [display-only]"));

        assertThat(tamperedGameplay.hasValidOutputHash(canonicalizer)).isFalse();
        assertThat(tamperedSnapshot.hasValidOutputHash(canonicalizer)).isFalse();
        assertThat(displayOnly.hasValidOutputHash(canonicalizer)).isTrue();
    }

    @Test
    void legacyAndV1RealDraftPathsHaveExactGameplayAndInputBoundProvenance() {
        assertCompleteTimelineEquals(legacy.timeline(), execution.legacyTimeline());
        assertThat(execution.executionProvenance())
                .usingRecursiveComparison()
                .ignoringFields("replayProvenanceHash", "replayProvenanceHashAlgorithm")
                .isEqualTo(legacy.executionProvenance());
        assertThat(execution.executionProvenance().replayProvenanceHash())
                .isEqualTo(SimulationProvenanceService.matchEngineV1ReplayProvenanceHash(
                        legacy.executionProvenance().replayProvenanceHash(), input.inputHash()))
                .isNotEqualTo(legacy.executionProvenance().replayProvenanceHash());
        assertThat(execution.executionProvenance().replayProvenanceHashAlgorithm())
                .isEqualTo(
                        SimulationProvenanceService.MATCH_ENGINE_V1_REPLAY_PROVENANCE_HASH_ALGORITHM);
        assertThat(execution.output().executionProvenance()).isEqualTo(
                execution.executionProvenance());
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
        return copyDraft(source, source.selectionTraces(), decisions, bluePicks, redPicks,
                decisionHash, assignmentHash, finalDraftHash);
    }

    private static MatchEngineV1Input.DraftInput copyDraft(
            MatchEngineV1Input.DraftInput source,
            List<DraftSelectionTrace> traces,
            List<MatchEngineV1Input.DraftDecisionInput> decisions,
            List<ChampionId> bluePicks,
            List<ChampionId> redPicks,
            String decisionHash,
            String assignmentHash,
            String finalDraftHash
    ) {
        return new MatchEngineV1Input.DraftInput(
                source.seriesGameNumber(), source.draftRuleSetIdentity(),
                source.draftRuleSetHash(), source.draftScoringPolicyHash(),
                source.draftSelectionPolicyId(), source.draftSelectionPolicyHash(),
                traces, DraftSelectionTraceHasher.hash(traces), decisions,
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
        List<DraftSelectionTrace> traces = source.finalDraft().selectionTraces().stream()
                .map(trace -> replaceChampion(trace, replaced.championId(), illegalChampion))
                .toList();
        MatchEngineV1Input.DraftInput unsigned = copyDraft(
                source.finalDraft(), traces, decisions, bluePicks,
                source.finalDraft().redPicks(),
                decisionHash, assignmentHash, "0".repeat(64));
        String finalDraftHash = MatchEngineV1Input.finalDraftHash(unsigned, assignments);
        MatchEngineV1Input.DraftInput draft = copyDraft(
                source.finalDraft(), traces, decisions, bluePicks,
                source.finalDraft().redPicks(),
                decisionHash, assignmentHash, finalDraftHash);
        return copy(source, source.blueTeam(), source.redTeam(), assignments, draft,
                source.productionPolicy());
    }

    private static DraftSelectionTrace replaceChampion(
            DraftSelectionTrace trace, ChampionId original, ChampionId replacement
    ) {
        List<DraftSelectionPoolEntry> pool = trace.eligiblePool().stream()
                .map(entry -> new DraftSelectionPoolEntry(
                        entry.championId().equals(original) ? replacement : entry.championId(),
                        entry.canonicalRank(), entry.rawFinalSearchScore(),
                        entry.canonicalFinalScore(), entry.canonicalScoreLoss(),
                        entry.rankWeight())).toList();
        return new DraftSelectionTrace(trace.policyId(), trace.policyMode(), trace.policyHash(),
                trace.selectionContextHash(), trace.turn(), trace.side(), trace.actionType(),
                trace.bestCandidateId().equals(original) ? replacement : trace.bestCandidateId(),
                trace.bestCanonicalScore(), pool,
                trace.selectedChampionId().equals(original)
                        ? replacement : trace.selectedChampionId(),
                trace.selectedRank(), trace.selectedCanonicalScoreLoss(), trace.drawBucket(),
                trace.totalEligibleWeight(), trace.reason());
    }

    private static MatchEngineV1Input renamedDisplayInput(MatchEngineV1Input source) {
        MatchEngineV1Input.TeamInput blue = renamedTeam(source.blueTeam(), "Blue display renamed");
        MatchEngineV1Input.TeamInput red = renamedTeam(source.redTeam(), "Red display renamed");
        return copy(source, blue, red, source.championAssignments(), source.finalDraft(),
                source.productionPolicy());
    }

    private static MatchEngineV1Input withChangedPlayerGameplaySnapshot(
            MatchEngineV1Input source
    ) {
        MatchEngineV1Input.PlayerInput original = source.player(TeamSide.BLUE, Position.TOP);
        PlayerSkill skill = PlayerSkill.orderedForPosition(Position.TOP).getFirst();
        EnumMap<PlayerSkill, Integer> ratings = new EnumMap<>(PlayerSkill.class);
        ratings.putAll(original.ratings());
        int rating = ratings.get(skill);
        ratings.put(skill, rating == PlayerRatings.MAX ? rating - 1 : rating + 1);

        ChampionRoleKey role = new ChampionRoleKey(
                source.assignment(TeamSide.BLUE, Position.TOP).championId(), Position.TOP);
        HashMap<ChampionRoleKey, Integer> proficiencies = new HashMap<>(
                original.proficiencies());
        int proficiency = proficiencies.getOrDefault(role, ChampionProficiencies.NEUTRAL);
        proficiencies.put(role, proficiency == 20 ? proficiency - 1 : proficiency + 1);

        MatchEngineV1Input.PlayerInput changed = new MatchEngineV1Input.PlayerInput(
                original.playerId(), original.displayName(), original.teamSide(),
                original.position(), ratings, proficiencies);
        ArrayList<MatchEngineV1Input.PlayerInput> lineup = new ArrayList<>(
                source.blueTeam().lineup());
        lineup.set(lineup.indexOf(original), changed);
        MatchEngineV1Input.TeamInput blue = new MatchEngineV1Input.TeamInput(
                source.blueTeam().teamIdentity(), source.blueTeam().displayName(),
                source.blueTeam().teamSide(), lineup);
        return copy(source, blue, source.redTeam(), source.championAssignments(),
                source.finalDraft(), source.productionPolicy());
    }

    private static MatchEngineV1Output.EventV1 copyEvent(
            MatchEngineV1Output.EventV1 source,
            int goldAmount,
            String displayMessage
    ) {
        return new MatchEngineV1Output.EventV1(
                source.timeSeconds(), source.eventType(), source.actorSide(),
                source.actorPosition(), source.lane(), source.actorPlayerId(), source.killerPlayerId(),
                source.victimPlayerId(), source.assistantPlayerIds(),
                source.killerChampionId(), source.victimChampionId(),
                source.assistantChampionIds(), source.combatSource(),
                source.structureActionSource(), source.structureKind(),
                source.structureTowerTier(), source.structureAttackingSide(),
                source.structureDefendingSide(), goldAmount,
                source.bountyRawBeforePayout(), source.actionId(), source.parentActionId(),
                displayMessage, source.structuredData());
    }

    private static MatchEngineV1Output withFirstEvent(
            MatchEngineV1Output source,
            MatchEngineV1Output.EventV1 first
    ) {
        ArrayList<MatchEngineV1Output.EventV1> events = new ArrayList<>(
                source.timeline().events());
        events.set(0, first);
        MatchEngineV1Output.TimelineV1 timeline = new MatchEngineV1Output.TimelineV1(
                source.timeline().schemaVersion(), source.timeline().durationSeconds(),
                source.timeline().winner(), source.timeline().endReason(), events,
                source.timeline().snapshots());
        return new MatchEngineV1Output(
                source.schemaVersion(), source.matchIdentity(), source.productionPolicy(),
                source.configurationHash(), source.resultSummary(), source.finalDraft(), timeline,
                source.executionProvenance(), source.inputHash(), source.inputHashAlgorithm(),
                source.simulatorTimelineHash(), source.structuredTimelineHash(),
                source.structuredTimelineHashAlgorithm(), source.outputHash(),
                source.outputHashAlgorithm(), source.outputHashScope());
    }

    private static MatchEngineV1Output withLastSnapshot(
            MatchEngineV1Output source,
            MatchEngineV1Output.SnapshotV1 last
    ) {
        ArrayList<MatchEngineV1Output.SnapshotV1> snapshots = new ArrayList<>(
                source.timeline().snapshots());
        snapshots.set(snapshots.size() - 1, last);
        MatchEngineV1Output.TimelineV1 timeline = new MatchEngineV1Output.TimelineV1(
                source.timeline().schemaVersion(), source.timeline().durationSeconds(),
                source.timeline().winner(), source.timeline().endReason(),
                source.timeline().events(), snapshots);
        return new MatchEngineV1Output(
                source.schemaVersion(), source.matchIdentity(), source.productionPolicy(),
                source.configurationHash(), source.resultSummary(), source.finalDraft(), timeline,
                source.executionProvenance(), source.inputHash(), source.inputHashAlgorithm(),
                source.simulatorTimelineHash(), source.structuredTimelineHash(),
                source.structuredTimelineHashAlgorithm(), source.outputHash(),
                source.outputHashAlgorithm(), source.outputHashScope());
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
