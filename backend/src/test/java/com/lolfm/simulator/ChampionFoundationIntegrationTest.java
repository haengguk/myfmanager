package com.lolfm.simulator;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.*;
import com.lolfm.domain.Position;
import com.lolfm.factory.DummyDataFactory;
import org.junit.jupiter.api.Test;

class ChampionFoundationIntegrationTest {
    private final ChampionCatalog catalog = new ChampionCatalog(new ObjectMapper());
    private final ChampionSelectionValidator validator = new ChampionSelectionValidator(catalog);

    @Test void allSnapshotsKeepTheSameStructuredAssignmentAndPastSnapshotsAreImmutable() {
        var timeline = simulator().simulate(new DummyDataFactory().createBlueTeam(), new DummyDataFactory().createRedTeam(), 7,
                validator.resolve(null));
        assertThat(timeline.getSnapshots()).allSatisfy(snapshot -> {
            assertThat(snapshot.getPlayerSnapshots()).hasSize(10).allSatisfy(player -> {
                assertThat(player.getChampion()).isNotNull();
                assertThat(player.getChampionPosition()).isEqualTo(player.getPosition());
            });
        });
        var first = timeline.getSnapshots().getFirst().getPlayerSnapshots().getFirst().getChampion();
        var last = timeline.getSnapshots().getLast().getPlayerSnapshots().getFirst().getChampion();
        assertThat(first.id()).isEqualTo(last.id());
        assertThat(first.powerProfile().profileVersion()).isEqualTo("full-173-power-2026-08-v1");
    }

    @Test void differentValidLineupsChangeOnlyChampionMetadata() {
        var a = runOff(77, lineupC());
        var b = runOff(77, lineupD());
        assertThat(a).usingRecursiveComparison().ignoringFieldsMatchingRegexes(
                ".*playerSnapshots.*champion", ".*champion.*").isEqualTo(b);
        assertThat(a.getSnapshots().getFirst().getPlayerSnapshots()).extracting(p -> p.getChampionId())
                .isNotEqualTo(b.getSnapshots().getFirst().getPlayerSnapshots().stream().map(p -> p.getChampionId()).toList());
    }

    @Test void sameSeedAndLineupIsFullyReproducibleAndMatchesAreIsolated() {
        var first = run(101, lineupC());
        var second = run(101, lineupC());
        assertThat(first).usingRecursiveComparison().isEqualTo(second);
        var different = run(101, lineupD());
        assertThat(first.getSnapshots().getFirst().getPlayerSnapshots().stream()
                .filter(p -> p.getTeamSide() == TeamSide.BLUE && p.getPosition() == Position.TOP)
                .findFirst().orElseThrow().getChampionId()).isEqualTo("renekton");
        assertThat(different.getSnapshots().getFirst().getPlayerSnapshots().stream()
                .filter(p -> p.getTeamSide() == TeamSide.BLUE && p.getPosition() == Position.TOP)
                .findFirst().orElseThrow().getChampionId()).isEqualTo("ornn");
    }

    private com.lolfm.domain.MatchTimeline run(long seed, ChampionSelectionRequest selection) {
        DummyDataFactory factory = new DummyDataFactory();
        return simulator().simulate(factory.createBlueTeam(), factory.createRedTeam(), seed, validator.resolve(selection));
    }
    private com.lolfm.domain.MatchTimeline runOff(long seed, ChampionSelectionRequest selection) { DummyDataFactory factory=new DummyDataFactory();return simulatorOff().simulate(factory.createBlueTeam(),factory.createRedTeam(),seed,validator.resolve(selection)); }
    private MatchSimulator simulator() { return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(),
            new SnapshotFactory(catalog), new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
            new StructureResolver(), new PushResolver(), SimulationOptions.productionDefaults()); }
    private MatchSimulator simulatorOff(){return new MatchSimulator(new TeamfightResolver(),new EndGameEvaluator(),new SnapshotFactory(catalog),new ObjectiveResolver(),new PostFightResolver(),new ObjectiveAttemptResolver(),new StructureResolver(),new PushResolver(),SimulationOptions.productionDefaults().withChampionPowerEnabled(false).withChampionMatchupMode(ChampionMatchupMode.OFF));}
    private ChampionSelectionRequest lineupC() { return new ChampionSelectionRequest(
            new ChampionLineupRequest("renekton","sejuani","azir","jinx","nautilus"),
            new ChampionLineupRequest("jax","lee-sin","ahri","kaisa","rakan")); }
    private ChampionSelectionRequest lineupD() { return new ChampionSelectionRequest(
            new ChampionLineupRequest("ornn","vi","orianna","lucian","lulu"),
            new ChampionLineupRequest("gwen","nidalee","leblanc","ezreal","braum")); }
}
