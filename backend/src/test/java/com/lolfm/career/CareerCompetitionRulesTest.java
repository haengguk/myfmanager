package com.lolfm.career;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.league.CareerCompetitionAutomatedSeriesKernel;
import com.lolfm.league.LeagueFixtureGameReceiptV1;
import com.lolfm.league.LeagueIdentity;
import com.lolfm.league.LeagueSeasonFrozenSnapshot;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CareerCompetitionRulesTest {
    private static final String CAREER = "career_" + "1".repeat(64);
    private static final String INPUT = "2".repeat(64);
    private final CareerCompetitionRules rules = new CareerCompetitionRules(
            new ObjectMapper().findAndRegisterModules());

    @Test
    void dueWaitingFixtureFailsClosedAndStageComesFromPersistedNextFixture() {
        CareerCompetitionRelationalStore store = mock(
                CareerCompetitionRelationalStore.class);
        CareerRelationalStore.CareerRow career = mock(
                CareerRelationalStore.CareerRow.class);
        when(career.careerId()).thenReturn(CAREER);
        when(career.managedTeamCode()).thenReturn("T01");
        CareerCompetitionRelationalStore.FixtureRow waiting =
                new CareerCompetitionRelationalStore.FixtureRow(
                        "LCK_CUP", "PI_R1_M1", "fixture-1", "series-1",
                        LocalDate.of(2027, 2, 4), "OFFICIAL", "BO5", true,
                        null, null, "FULL_AUTO", "WAITING_FOR_PREDECESSOR", 7L,
                        null, "CUP_PLAY_IN", 26, "CUP_PLAY_IN_SEED", "1",
                        "CUP_PLAY_IN_SEED", "2", null, null, null, 2,
                        null, null, "HIGHER_SEED_BLUE");
        when(store.load(CAREER, 2027)).thenReturn(
                new CareerCompetitionRelationalStore.CycleView(CAREER, 2027,
                        "ACTIVE", null, 0, INPUT, null, null, "SHA-256", 1,
                        CareerCompetitionRules.INITIAL_CUP_POLICY, INPUT,
                        List.of(new CareerCompetitionRelationalStore.InstanceRow(
                                "LCK_CUP", "RULE_SOURCE_COMPLETE", "ACTIVE", null,
                                INPUT, 0, INPUT, "SHA-256", "POLICY", INPUT)),
                        List.of(waiting), List.of()));
        CareerCompetitionApplicationService service =
                new CareerCompetitionApplicationService(store, rules);

        CareerCompetitionApplicationService.CompetitionView view = service.view(
                career, 2027, LocalDate.of(2027, 2, 4), "LCK_CUP", null);
        assertThat(view.currentCompetition().stageId()).isEqualTo("CUP_PLAY_IN");
        assertThat(service.gate(career, 2027, LocalDate.of(2027, 2, 4),
                "LCK_CUP", null).stopReason())
                .isEqualTo("LCK_CUP_GROUP_STANDINGS_REQUIRED");
    }

    @Test
    void absentFutureCycleIsAStructuredPriorRankingBlocker() {
        CareerCompetitionRelationalStore store = mock(
                CareerCompetitionRelationalStore.class);
        CareerRelationalStore.CareerRow career = mock(
                CareerRelationalStore.CareerRow.class);
        when(career.careerId()).thenReturn(CAREER);
        when(store.load(CAREER, 2028)).thenThrow(
                new IllegalStateException("CAREER_COMPETITION_CYCLE_NOT_FOUND"));
        CareerCompetitionApplicationService.CompetitionView view =
                new CareerCompetitionApplicationService(store, rules).view(
                        career, 2028, LocalDate.of(2028, 1, 1), "LCK_CUP", null);

        assertThat(view.lifecycleStatus()).isEqualTo("BLOCKED");
        assertThat(view.currentCompetition().stageId()).isEqualTo("UNMATERIALIZED");
        assertThat(view.currentCompetition().blockingReason())
                .isEqualTo("PRIOR_SEASON_SEALED_RANKING_REQUIRED");
    }

    @Test
    void onlyDecisiveOrderedSeriesEvidenceMintsACompetitionCompletion() {
        CareerCompetitionSeriesBindingV1 binding = automatedBinding();
        String history0 = binding.initialHistoryHash();
        String history1 = "a".repeat(64);
        String history2 = "b".repeat(64);
        LeagueFixtureGameReceiptV1 first = gameReceipt(binding, 1, "GEN", "T1",
                history0, history1, "GEN");
        LeagueFixtureGameReceiptV1 second = gameReceipt(binding, 2, "T1", "GEN",
                history1, history2, "GEN");
        CareerCompetitionAutomatedSeriesKernel.CompletedSeriesEvidence evidence =
                new CareerCompetitionAutomatedSeriesKernel.CompletedSeriesEvidence(
                        binding.bindingHash(), Map.of("GEN", 2, "T1", 0), "GEN",
                        "T1", List.of(first, second));

        VerifiedCompetitionFixtureCompletion verified =
                CareerCompetitionFixtureCompletionReceiptV1.verifyAutomated(
                        binding, evidence);
        assertThat(verified.receipt()).extracting(
                CareerCompetitionFixtureCompletionReceiptV1::seriesId,
                CareerCompetitionFixtureCompletionReceiptV1::winnerTeamCode,
                CareerCompetitionFixtureCompletionReceiptV1::firstScore,
                CareerCompetitionFixtureCompletionReceiptV1::secondScore)
                .containsExactly(binding.boundSeriesId(), "GEN", 2, 0);
        assertThat(verified.receipt().receiptHash()).matches("[0-9a-f]{64}");

        LeagueFixtureGameReceiptV1 crossScope = gameReceipt(binding, 2, "T1", "GEN",
                history1, history2, "GEN");
        when(crossScope.matchIdentity()).thenReturn("CAREER_COMPETITION:other");
        var tampered = new CareerCompetitionAutomatedSeriesKernel.CompletedSeriesEvidence(
                binding.bindingHash(), Map.of("GEN", 2, "T1", 0), "GEN", "T1",
                List.of(first, crossScope));
        assertThatThrownBy(() ->
                CareerCompetitionFixtureCompletionReceiptV1.verifyAutomated(
                        binding, tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("COMPETITION_GAME_RECEIPT_BINDING_MISMATCH");
    }

    @Test
    void roadToMsiAndPlayInUseOnlyStructuredOrderedWinnerLoserEdges() {
        CareerCompetitionAggregate road = CareerCompetitionAggregate.materialize(
                rules, CAREER, 2027, "LCK_ROAD_TO_MSI", "T01", 41L,
                INPUT, ranking());
        assertThat(road.fixtures()).hasSize(5);
        assertThat(match(road, "M1")).extracting(
                CareerCompetitionAggregate.Fixture::firstTeamCode,
                CareerCompetitionAggregate.Fixture::secondTeamCode,
                CareerCompetitionAggregate.Fixture::seriesFormat,
                CareerCompetitionAggregate.Fixture::hardFearless)
                .containsExactly("T05", "T06", "BO5", true);
        assertThat(match(road, "M2").secondTeamCode()).isNull();
        assertThat(match(road, "M3").date()).isEqualTo(LocalDate.of(2027, 6, 12));

        road = complete(road, "M1", "T05", 1);
        assertThat(match(road, "M2").secondTeamCode()).isEqualTo("T05");
        road = complete(road, "M2", "T04", 2);
        assertThat(match(road, "M4").secondTeamCode()).isEqualTo("T04");
        road = complete(road, "M3", "T01", 3);
        assertThat(road.qualificationOutputs()).containsEntry("MSI_LCK_SEED_1", "T01");
        assertThat(match(road, "M5").firstTeamCode()).isEqualTo("T02");
        road = complete(road, "M4", "T03", 4);
        assertThat(match(road, "M5").secondTeamCode()).isEqualTo("T03");
        CareerCompetitionAggregate beforeFinal = road;
        CareerCompetitionAggregate.CompletionResult finalResult = apply(
                road, "M5", "T02", 5);
        road = finalResult.aggregate();
        assertThat(road.qualificationOutputs()).containsEntry("MSI_LCK_SEED_2", "T02");
        assertThat(road.revision()).isEqualTo(5);
        CareerCompetitionAggregate.CompletionResult replay = road.applyVerifiedCompletion(
                "M5", match(road, "M5").seriesId(),
                match(road, "M5").firstTeamCode(), match(road, "M5").secondTeamCode(),
                "T02", hash(5));
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.aggregate()).isEqualTo(road);
        assertThatThrownBy(() -> beforeFinal.applyVerifiedCompletion("M5",
                match(beforeFinal, "M5").seriesId(), "T99", "T03", "T02",
                hash(9))).isInstanceOf(IllegalArgumentException.class);

        CareerCompetitionAggregate playIn = CareerCompetitionAggregate.materialize(
                rules, CAREER, 2027, "LCK_PLAY_IN", "T03", 41L, INPUT,
                ranking().subList(0, 4));
        playIn = complete(playIn, "M1", "T01", 11);
        playIn = complete(playIn, "M2", "T04", 12);
        assertThat(playIn.qualificationOutputs())
                .containsEntry("LCK_PLAYOFF_SEED_5", "T01")
                .containsEntry("LCK_SEASON_PLACE_8", "T03");
        assertThat(match(playIn, "M3")).extracting(
                CareerCompetitionAggregate.Fixture::firstTeamCode,
                CareerCompetitionAggregate.Fixture::secondTeamCode)
                .containsExactly("T02", "T04");
        playIn = complete(playIn, "M3", "T02", 13);
        assertThat(playIn.qualificationOutputs())
                .containsEntry("LCK_PLAYOFF_SEED_6", "T02")
                .containsEntry("LCK_SEASON_PLACE_7", "T04");
    }

    @Test
    void r3r4SplitCarriesRecordsAndAllocatesFortyStableConflictFreeFixtures() {
        CareerCompetitionAggregate.R3R4Stage first =
                CareerCompetitionAggregate.materializeR3R4(CAREER, 2027, "T01",
                        41L, INPUT, ranking());
        CareerCompetitionAggregate.R3R4Stage replay =
                CareerCompetitionAggregate.materializeR3R4(CAREER, 2027, "T01",
                        41L, INPUT, ranking());
        assertThat(first).isEqualTo(replay);
        assertThat(first.legend()).extracting(
                CareerCompetitionAggregate.SeededTeam::teamCode)
                .containsExactly("T01", "T02", "T03", "T04", "T05");
        assertThat(first.rise()).extracting(
                CareerCompetitionAggregate.SeededTeam::teamCode)
                .containsExactly("T06", "T07", "T08", "T09", "T10");
        assertThat(first.fixtures()).hasSize(40).allSatisfy(value -> {
            assertThat(value.date()).isBetween(LocalDate.of(2027, 7, 29),
                    LocalDate.of(2027, 8, 23));
            assertThat(value.seriesFormat()).isEqualTo("BO3");
            assertThat(value.hardFearless()).isTrue();
        });
        assertThat(first.fixtures()).filteredOn(value ->
                "PLAYER_CONTROLLED".equals(value.executionMode())).hasSize(8);
        Map<String, Integer> pairs = new HashMap<>();
        Map<LocalDate, HashSet<String>> dailyTeams = new HashMap<>();
        first.fixtures().forEach(value -> {
            String pair = List.of(value.firstTeamCode(), value.secondTeamCode()).stream()
                    .sorted().reduce((a, b) -> a + ":" + b).orElseThrow();
            pairs.merge(value.groupId() + ":" + pair, 1, Integer::sum);
            HashSet<String> teams = dailyTeams.computeIfAbsent(value.date(),
                    ignored -> new HashSet<>());
            assertThat(teams.add(value.firstTeamCode())).isTrue();
            assertThat(teams.add(value.secondTeamCode())).isTrue();
        });
        assertThat(pairs).hasSize(20).allSatisfy((pair, count) ->
                assertThat(count).isEqualTo(2));
    }

    @Test
    void lckCupInitialBootstrapAndExecutableGraphAreExact() {
        assertThat(rules.resourceHash()).isEqualTo(
                CareerCompetitionRules.RESOURCE_HASH);
        assertThat(rules.rawSources()).hasSize(6);
        CareerCompetitionRules.CupInitialization initialization =
                rules.initialCupInitialization(2027);
        assertThat(initialization.seasonOrdinal()).isOne();
        assertThat(initialization.sourceReferenceYear()).isEqualTo(2026);
        assertThat(initialization.policyId())
                .isEqualTo(CareerCompetitionRules.INITIAL_CUP_POLICY);
        assertThat(initialization.groups()).extracting(
                CareerCompetitionRules.CupGroupSeed::teamCode)
                .containsExactlyInAnyOrder("GEN", "T1", "NS", "DNS", "BRO",
                        "HLE", "DK", "KT", "BFX", "KRX");

        CareerCompetitionAggregate cup = CareerCompetitionAggregate.materializeCup(
                rules, CAREER, 2027, "GEN", 41L, initialization);
        assertThat(cup.fixtures()).hasSize(40);
        assertThat(cup.fixtures()).filteredOn(value ->
                "READY".equals(value.lifecycleStatus())).hasSize(25);
        assertThat(rules.rule("LCK_CUP").matches()).filteredOn(value ->
                "GROUP_BATTLE".equals(value.stageId())).hasSize(25);
        assertThat(rules.rule("LCK_CUP").matches()).filteredOn(value ->
                Integer.valueOf(2).equals(value.groupPointValue())).hasSize(5)
                .allSatisfy(value -> assertThat(value.seriesFormat()).isEqualTo("BO5"));
        assertThat(rules.rule("LCK_CUP").matches()).filteredOn(value ->
                "CUP_PLAY_IN".equals(value.stageId())).hasSize(5);
        assertThat(rules.rule("LCK_CUP").matches()).filteredOn(value ->
                "CUP_PLAYOFFS".equals(value.stageId())).hasSize(10);
        assertThat(rules.rule("LCK_CUP").matches()).filteredOn(value ->
                CareerCompetitionRules.CUP_OPPONENT_POLICY.equals(
                        value.opponentChoicePolicy())).hasSize(3);
        assertThat(rules.lckCup().qualificationOutputs())
                .containsExactly("FIRST_STAND_LCK_SEED_1", "FIRST_STAND_LCK_SEED_2");
        assertThat(cup.qualificationOutputs()).isEmpty();

        List<CareerCompetitionAggregate.SeededTeam> eligible = List.of(
                new CareerCompetitionAggregate.SeededTeam(4, "T04", 0, 0, 0, 0),
                new CareerCompetitionAggregate.SeededTeam(6, "T06", 0, 0, 0, 0),
                new CareerCompetitionAggregate.SeededTeam(5, "T05", 0, 0, 0, 0));
        CareerCompetitionRules.OpponentChoiceReceipt choice =
                rules.chooseCupOpponent("T03", eligible);
        CareerCompetitionRules.OpponentChoiceReceipt reordered =
                rules.chooseCupOpponent("T03", eligible.reversed());
        assertThat(choice.chosenTeamCode()).isEqualTo("T06");
        assertThat(choice.canonicalEligibleOrder())
                .containsExactly("6:T06", "5:T05", "4:T04");
        assertThat(reordered).isEqualTo(choice);
        assertThat(choice.receiptHash()).matches("[0-9a-f]{64}");
    }

    @Test
    void futureCupUsesOnlySealedPriorInGameRankingAndKespaRemainsReferenceOnly() {
        CareerCompetitionRules.PriorLckRanking prior =
                new CareerCompetitionRules.PriorLckRanking(CAREER, 2027, "SEALED",
                        hash(7), ranking());
        CareerCompetitionRules.CupInitialization future =
                rules.futureCupInitialization(2, 2028, prior);
        assertThat(future.sourceReferenceYear()).isNull();
        assertThat(future.sourceCareerId()).isEqualTo(CAREER);
        assertThat(future.sourceSeasonYear()).isEqualTo(2027);
        assertThat(future.sourceStateHash()).isEqualTo(hash(7));
        assertThat(future.policyId()).isEqualTo(CareerCompetitionRules.FUTURE_CUP_POLICY);
        assertThat(future.groups().stream().filter(value ->
                "BARON".equals(value.groupId())).map(
                CareerCompetitionRules.CupGroupSeed::teamCode).toList())
                .containsExactly("T01", "T03", "T06", "T07", "T10");
        assertThat(future.groups().stream().filter(value ->
                "ELDER".equals(value.groupId())).map(
                CareerCompetitionRules.CupGroupSeed::teamCode).toList())
                .containsExactly("T02", "T04", "T05", "T08", "T09");
        assertThat(future.inputHash()).isNotEqualTo(
                rules.initialCupInitialization(2028).inputHash());
        assertThatThrownBy(() -> rules.futureCupInitialization(2, 2028,
                new CareerCompetitionRules.PriorLckRanking(CAREER, 2027, "RUNNING",
                        hash(7), ranking())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LCK_CUP_PRIOR_SEASON_RANKING_REQUIRED");

        assertThat(rules.kespaCup().sourceReferenceYear()).isEqualTo(2025);
        assertThat(rules.kespaCup().status())
                .isEqualTo("REFERENCE_TEMPLATE_NOT_OFFICIAL_FOR_2026_OR_FUTURE");
        assertThat(rules.kespaCup().participantSlots()).hasSize(14)
                .allSatisfy(slot -> assertThat(slot.resolved()).isFalse());
        assertThat(rules.rule("KESPA_CUP").matches()).isEmpty();
        assertThat(rules.rule("KESPA_CUP").ruleStatus())
                .isEqualTo("REFERENCE_TEMPLATE_ONLY");
        assertThat(rules.rule("LCK_PLAYOFFS").ruleStatus())
                .isEqualTo("RULE_SOURCE_COMPLETE");
        assertThat(rules.rule("LCK_PLAYOFFS").matches()).hasSize(10);
        assertThat(rules.rule("LCK_PLAYOFFS").scheduledMonthDays()).hasSize(10);
        assertThat(rules.competitions()).filteredOn(value ->
                "EXTERNAL_COMPETITION_EXECUTION_NOT_IMPLEMENTED".equals(
                        value.blockingReason())).hasSize(4);
    }

    private static List<CareerCompetitionAggregate.SeededTeam> ranking() {
        return java.util.stream.IntStream.rangeClosed(1, 10).mapToObj(seed ->
                new CareerCompetitionAggregate.SeededTeam(seed, "T%02d".formatted(seed),
                        19 - seed, seed - 1, 36 - seed, seed)).toList();
    }

    private static CareerCompetitionSeriesBindingV1 automatedBinding() {
        String seriesId = "series_" + "3".repeat(64);
        CareerCompetitionRelationalStore.FixtureRow fixture =
                new CareerCompetitionRelationalStore.FixtureRow(
                        "LCK_CUP", "GROUP_B01_E01",
                        "competition_fixture_" + "4".repeat(64), seriesId,
                        LocalDate.of(2027, 1, 14), "GAME_DERIVED_SCHEDULE_POLICY",
                        "BO3", true, "GEN", "T1", "FULL_AUTO", "READY", 41L,
                        null, "GROUP_BATTLE", 1, "INITIAL_BOOTSTRAP_TEAM", "GEN",
                        "INITIAL_BOOTSTRAP_TEAM", "T1",
                        null, null, "BARON", 1, null, null,
                        "HIGHER_SEED_BLUE");
        CareerCompetitionRelationalStore.InstanceRow instance =
                new CareerCompetitionRelationalStore.InstanceRow(
                        "LCK_CUP", "RULE_SOURCE_COMPLETE", "READY", null, INPUT,
                        0, INPUT, CareerCompetitionRelationalStore.INSTANCE_HASH_ALGORITHM,
                        "POLICY", INPUT);
        CareerCompetitionRelationalStore.CycleView cycle =
                new CareerCompetitionRelationalStore.CycleView(CAREER, 2027,
                        "ACTIVE", null, 0, INPUT, null, null,
                        CareerCompetitionRelationalStore.CYCLE_HASH_ALGORITHM, 1,
                        CareerCompetitionRules.INITIAL_CUP_POLICY, INPUT,
                        List.of(instance), List.of(fixture), List.of());
        LinkedHashMap<String, String> teams = new LinkedHashMap<>();
        for (String team : List.of("GEN", "T1", "HLE", "DK", "KT", "NS",
                "DNS", "BRO", "BFX", "KRX")) teams.put(team, hash(6));
        LeagueSeasonFrozenSnapshot snapshot = new LeagueSeasonFrozenSnapshot(teams,
                hash(7), hash(8), hash(9), hash(10));
        return CareerCompetitionSeriesBindingV1.create(cycle, instance, fixture,
                "DK", CareerCompetitionRules.RESOURCE_HASH, snapshot, hash(11));
    }

    private static LeagueFixtureGameReceiptV1 gameReceipt(
            CareerCompetitionSeriesBindingV1 binding, int gameNumber,
            String blue, String red, String historyBefore, String historyAfter,
            String winner
    ) {
        LeagueFixtureGameReceiptV1 receipt = mock(LeagueFixtureGameReceiptV1.class);
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();
        String matchIdentity = "CAREER_COMPETITION:" + binding.careerId() + ':'
                + binding.seasonYear() + ':' + binding.competitionId() + ':'
                + binding.matchId() + ":SERIES:" + binding.boundSeriesId()
                + ":GAME:" + gameNumber;
        when(receipt.gameNumber()).thenReturn(gameNumber);
        when(receipt.matchIdentity()).thenReturn(matchIdentity);
        when(receipt.blueTeamCode()).thenReturn(blue);
        when(receipt.redTeamCode()).thenReturn(red);
        when(receipt.gameSeed()).thenReturn(LeagueIdentity.gameSeed(
                binding.boundSeriesId(), binding.fixtureRootSeed(), gameNumber,
                blue, red, binding.seedAnchorTeamCode(), historyBefore));
        when(receipt.historyBeforeHash()).thenReturn(historyBefore);
        when(receipt.historyAfterHash()).thenReturn(historyAfter);
        when(receipt.winnerTeamCode()).thenReturn(winner);
        when(receipt.policyId()).thenReturn(policy.policyId());
        when(receipt.policyHash()).thenReturn(policy.policyHash());
        when(receipt.configurationHash()).thenReturn(policy.configurationHash());
        when(receipt.runtimeProfileId()).thenReturn(
                policy.retainedRuntimeProfileId().name());
        when(receipt.engineImplementationVersion()).thenReturn(
                policy.engineImplementationVersion());
        when(receipt.activeGameplayRulesVersion()).thenReturn(
                policy.activeGameplayRulesVersion());
        when(receipt.resourceProvenanceHash()).thenReturn(
                binding.resourceProvenanceHash());
        when(receipt.durationSeconds()).thenReturn(1800);
        when(receipt.canonicalText()).thenReturn("game=" + gameNumber + '\n');
        return receipt;
    }

    private static CareerCompetitionAggregate complete(
            CareerCompetitionAggregate value, String matchId, String winner, int receipt
    ) {
        return apply(value, matchId, winner, receipt).aggregate();
    }

    private static CareerCompetitionAggregate.CompletionResult apply(
            CareerCompetitionAggregate value, String matchId, String winner, int receipt
    ) {
        CareerCompetitionAggregate.Fixture fixture = match(value, matchId);
        return value.applyVerifiedCompletion(matchId, fixture.seriesId(),
                fixture.firstTeamCode(), fixture.secondTeamCode(), winner, hash(receipt));
    }

    private static CareerCompetitionAggregate.Fixture match(
            CareerCompetitionAggregate value, String matchId
    ) {
        return value.fixtures().stream().filter(candidate -> matchId.equals(
                candidate.matchId())).findFirst().orElseThrow();
    }

    private static String hash(int value) {
        return Integer.toHexString(value).repeat(64).substring(0, 64);
    }
}
