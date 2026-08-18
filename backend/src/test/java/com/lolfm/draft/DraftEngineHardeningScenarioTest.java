package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.ChampionProficiencies;
import com.lolfm.domain.Position;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DraftEngineHardeningScenarioTest {
    private final DraftResourceSet resources = DraftTestSupport.RESOURCES;
    private final DraftEngine engine = new DraftEngine(resources);

    @Test
    void flexHeavyProficiencyScenarioCompletesAStableLegalDraft() {
        Set<ChampionId> flexPool = Set.of(id("yasuo"), id("poppy"), id("varus"), id("taliyah"),
                id("galio"), id("camille"), id("anivia"), id("cassiopeia"));
        DraftTeamContext flexTeam = boosted(flexPool);
        FinalDraftResult result = engine.draft(flexTeam, DraftTestSupport.NEUTRAL, new SeriesDraftHistory());
        assertLegal(result);
        Set<ChampionId> drafted = new HashSet<>(result.bluePicks());
        drafted.addAll(result.blueBans()); drafted.addAll(result.redBans());
        assertThat(drafted).containsAnyElementsOf(flexPool);
    }

    @Test
    void diveAntiDiveAndRealBanPlanPivotScenarioCompletesLegally() {
        Set<ChampionId> dive = Set.of(id("naafiri"), id("kaisa"), id("vi"), id("nocturne"));
        Set<ChampionId> antiDive = Set.of(id("poppy"), id("renata-glasc"), id("braum"), id("janna"));
        FinalDraftResult result = engine.draft(boosted(antiDive), boosted(dive), new SeriesDraftHistory());
        assertLegal(result);
        Set<ChampionId> actions = new HashSet<>();
        actions.addAll(result.bluePicks()); actions.addAll(result.redPicks());
        actions.addAll(result.blueBans()); actions.addAll(result.redBans());
        assertThat(actions).containsAnyElementsOf(dive);
        assertThat(actions).containsAnyElementsOf(antiDive);
        boolean pivoted = result.blueInitialPortfolio().preferred().archetype()
                != result.blueFinalPortfolio().preferred().archetype()
                || result.redInitialPortfolio().preferred().archetype()
                != result.redFinalPortfolio().preferred().archetype();
        assertThat(pivoted).isTrue();
    }

    private void assertLegal(FinalDraftResult result) {
        assertThat(result.decisions()).hasSize(20);
        assertThat(result.blueBans()).hasSize(5); assertThat(result.redBans()).hasSize(5);
        assertThat(result.bluePicks()).hasSize(5); assertThat(result.redPicks()).hasSize(5);
        Set<ChampionId> unique = new HashSet<>();
        unique.addAll(result.blueBans()); unique.addAll(result.redBans());
        unique.addAll(result.bluePicks()); unique.addAll(result.redPicks());
        assertThat(unique).hasSize(20);
        assertThat(result.blueFinalRoleAssignments().values()).containsExactlyInAnyOrder(Position.values());
        assertThat(result.redFinalRoleAssignments().values()).containsExactlyInAnyOrder(Position.values());
        assertThat(result.decisions()).allSatisfy(decision -> {
            assertThat(decision.componentBreakdown()).isNotEmpty();
            assertThat(decision.finalSearchScore()).isEqualTo(decision.immediateScore() + decision.continuationScore());
        });
    }

    private DraftTeamContext boosted(Set<ChampionId> ids) {
        Map<ChampionRoleKey, Integer> values = new HashMap<>();
        resources.champions().catalog().legalRoleKeys().stream()
                .filter(key -> ids.contains(key.championId())).forEach(key -> values.put(key, 20));
        EnumMap<Position, ChampionProficiencies> byPosition = new EnumMap<>(Position.class);
        for (Position position : Position.values()) {
            Map<ChampionRoleKey, Integer> selected = values.entrySet().stream()
                    .filter(entry -> entry.getKey().position() == position)
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            byPosition.put(position, new ChampionProficiencies(selected));
        }
        return new DraftTeamContext(byPosition);
    }
    private static ChampionId id(String value) { return DraftTestSupport.id(value); }
}
