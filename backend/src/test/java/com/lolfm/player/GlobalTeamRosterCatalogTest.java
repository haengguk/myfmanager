package com.lolfm.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.domain.Position;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.player.GlobalTeamRosterCatalog.TeamKey;
import java.util.HashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GlobalTeamRosterCatalogTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final PlayerRatingCatalog LCK = PlayerRatingCatalog.loadDefault();
    private static final ChampionCatalog CHAMPIONS = new ChampionCatalog(MAPPER);
    private static final ChampionProficiencyCatalog LCK_PROFICIENCIES = ChampionProficiencyCatalog.loadDefault(LCK, CHAMPIONS);
    private static final GlobalTeamRosterCatalog CATALOG = new GlobalTeamRosterCatalog(MAPPER, LCK, LCK_PROFICIENCIES, CHAMPIONS);

    @Test
    void loadsSixLeaguePopulationsWithDistinctPeopleAndPreservesDomesticCatalogs() {
        Map<String, Integer> expected = Map.of("LCK", 10, "LPL", 12, "LEC", 10, "LCS", 8, "LCP", 8, "CBLOL", 8);
        assertThat(CATALOG.leagueCodes()).containsExactly("LCK", "LPL", "LEC", "LCS", "LCP", "CBLOL");
        HashSet<PlayerId> ids = new HashSet<>();
        expected.forEach((code, teams) -> {
            var league = CATALOG.league(code);
            assertThat(league.ratings().teamCodes()).hasSize(teams);
            assertThat(league.ratings().identities().all()).hasSize(teams * 5);
            league.ratings().identities().all().forEach(identity -> assertThat(ids.add(identity.playerId())).isTrue());
            String teamCode = league.ratings().teamCodes().iterator().next();
            var team = CATALOG.assemble(new TeamKey(code, teamCode));
            assertThat(DraftTeamContext.from(team).hasStablePlayerIdentities()).isTrue();
            assertThat(team.getPlayers()).extracting(player -> player.getPosition()).containsExactly(Position.values());
        });
        assertThat(ids).hasSize(280);
        assertThat(CATALOG.league("LCK").ratings()).isSameAs(LCK);
        assertThat(CATALOG.league("LCK").proficiencies()).isSameAs(LCK_PROFICIENCIES);
        assertThat(new LckTeamAssembler(LCK, LCK_PROFICIENCIES).teamCodes()).hasSize(10);
        assertThatThrownBy(() -> CATALOG.assemble(new TeamKey("LCK", "G2"))).hasMessageContaining("Unknown roster team");
    }

    @Test
    void overseasSnapshotsAreStableAndAssembleFreshRealProfiles() {
        TeamKey key = new TeamKey("lec", "g2");
        var snapshot = CATALOG.snapshot(key);
        assertThat(CATALOG.snapshot(key)).isEqualTo(snapshot);
        assertThat(CATALOG.snapshot(new TeamKey("LPL", "BLG")).snapshotIdentity()).isNotEqualTo(snapshot.snapshotIdentity());
        var first = snapshot.assemble();
        var second = CATALOG.assemble(key);
        assertThat(first).isNotSameAs(second);
        for (int i = 0; i < first.getPlayers().size(); i++) {
            var a = first.getPlayers().get(i);
            var b = second.getPlayers().get(i);
            assertThat(a).isNotSameAs(b);
            assertThat(a.requirePlayerId()).isEqualTo(b.requirePlayerId());
            assertThat(a.getRatings()).isEqualTo(b.getRatings());
            assertThat(a.getRatings().asMap()).hasSize(12);
            assertThat(a.getChampionProficiencies()).isEqualTo(b.getChampionProficiencies());
            assertThat(a.isLegacyProfile()).isFalse();
        }
    }

    @Test
    void preservesUnknownValuesConflictNotesAndDefensivelyCopiesCareerData() {
        var career = CATALOG.league("LPL").career();
        PlayerId breathe = new PlayerId("player-breathe");
        ObjectNode reference = (ObjectNode) career.player(breathe);
        assertThat(reference.at("/contract/endDate").isNull()).isTrue();
        assertThat(reference.at("/contract/daysRemainingAsOfSnapshot").isNull()).isTrue();
        assertThat(reference.at("/rosterValidation/status").asText()).contains("CONFLICTS");
        assertThat(reference.at("/contract/note").asText()).isNotBlank();
        reference.withObject("/contract").put("endDate", "2099-01-01");
        assertThat(career.player(breathe).at("/contract/endDate").isNull()).isTrue();
        assertThat(CATALOG.league("LCS").career().player(new PlayerId("player-denathor"))
                .at("/personal/birthDate").isNull()).isTrue();
        assertThat(CATALOG.league("LCS").career().player(new PlayerId("player-lyonz"))
                .at("/careerPrizeMoney/amountUsd").isNull()).isTrue();
    }

    @Test
    void rejectsCareerBytesAndCrossCatalogIdentityMismatchBeforeRegistration() throws Exception {
        var spec = GlobalRosterDatasets.OVERSEAS.getFirst().career();
        var identities = CATALOG.league("LPL").ratings().identities();
        assertThatThrownBy(() -> RosterCareerReferences.load(MAPPER, new byte[0], spec, identities))
                .hasMessageContaining("SHA-256 mismatch");
        ObjectNode root;
        try (var input = getClass().getResourceAsStream(spec.resource())) { root = (ObjectNode) MAPPER.readTree(input); }
        ((ObjectNode) root.withArray("players").get(0)).put("team", "GEN");
        byte[] bytes = MAPPER.writeValueAsBytes(root);
        var changed = new PlayerResourceSpec(spec.leagueCode(), spec.teamCount(), spec.version(), spec.snapshotAt(),
                RosterCareerReferences.digest(bytes), spec.dataCutoff());
        assertThatThrownBy(() -> RosterCareerReferences.load(MAPPER, bytes, changed, identities))
                .hasMessageContaining("identity binding mismatch");
    }

    @Test
    void selectedLeagueScopeCannotBeTakenFromAnUnrelatedResource() {
        var spec = GlobalRosterDatasets.OVERSEAS.getFirst().ratings();
        var wrongLeague = new PlayerResourceSpec("LCK", spec.teamCount(), spec.version(), spec.snapshotAt(), spec.sha256(), spec.dataCutoff());
        assertThatThrownBy(() -> PlayerRatingResourceLoader.load(MAPPER,
                getClass().getResourceAsStream(spec.resource()), wrongLeague)).hasMessageContaining("scope");
    }
}
