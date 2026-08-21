package com.lolfm.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.Player;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.draft.DraftTeamContext;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class LckTeamAssemblerTest {
    private static final LckTeamAssembler ASSEMBLER = LckTeamAssembler.loadDefault();

    @Test
    void allTenCurrentTeamsAssembleDeterministicallyWithoutDummyData() {
        assertThat(ASSEMBLER.teamCodes()).hasSize(10);
        assertThat(ASSEMBLER.assembleAll()).hasSize(10);
        for (Team team : ASSEMBLER.assembleAll()) {
            assertThat(team.getPlayers()).hasSize(5);
            assertThat(team.getPlayers()).extracting(Player::getPosition)
                    .containsExactlyElementsOf(EnumSet.allOf(Position.class));
            assertThat(team.getPlayers()).extracting(Player::requirePlayerId).doesNotHaveDuplicates();
            assertThat(team.getPlayers()).allSatisfy(player -> {
                assertThat(player.hasStablePlayerId()).isTrue();
                assertThat(player.isLegacyProfile()).isFalse();
                assertThat(player.getRatings().asMap()).hasSize(12);
            });
            DraftTeamContext context = DraftTeamContext.from(team);
            assertThat(context.hasStablePlayerIdentities()).isTrue();
            assertThat(context.playerIds()).hasSize(5);
        }
        assertThat(snapshot(ASSEMBLER.assemble("GEN")))
                .isEqualTo(snapshot(ASSEMBLER.assemble("gen")));
    }

    @Test
    void defaultAssemblerUsesOneCoherentImmutableCatalogGraph() {
        PlayerRatingCatalog ratings = ASSEMBLER.ratingsCatalog();
        ChampionProficiencyCatalog proficiencies = ASSEMBLER.proficiencyCatalog();

        assertThat(proficiencies.ratingsCatalog()).isSameAs(ratings);
        assertThat(ratings.identities()).isSameAs(proficiencies.ratingsCatalog().identities());
        assertThat(proficiencies.requiredPlayerRatingResourceVersion())
                .isEqualTo(ratings.version());
        assertThat(proficiencies.requiredChampionPoolVersion())
                .isEqualTo(proficiencies.championCatalog().championPoolVersion());
        assertThat(proficiencies.requiredLegalRoleKeyCount())
                .isEqualTo(proficiencies.championCatalog().legalRoleKeys().size());
    }

    @Test
    void assembledGenAndT1ExposeKnownRealProficiencies() {
        Team gen = ASSEMBLER.assemble("GEN");
        Team t1 = ASSEMBLER.assemble("T1");

        assertThat(proficiency(gen, Position.MID, "azir")).isEqualTo(20);
        assertThat(proficiency(gen, Position.JUNGLE, "nidalee")).isEqualTo(20);
        assertThat(proficiency(t1, Position.MID, "leblanc")).isEqualTo(20);
        assertThat(proficiency(t1, Position.SUPPORT, "bard")).isEqualTo(20);
        assertThat(player(gen, Position.MID).requirePlayerId()).isEqualTo(new PlayerId("player-chovy"));
        assertThat(player(t1, Position.MID).requirePlayerId()).isEqualTo(new PlayerId("player-faker"));
    }

    @Test
    void productionBindingProbesUseRealPlayerAndDraftObjects() {
        PlayerId chovy = new PlayerId("player-chovy");
        PlayerId faker = new PlayerId("player-faker");
        PlayerRatingKey chovyKey = new PlayerRatingKey("GEN", Position.MID);

        Player matching = ASSEMBLER.assemblePlayer(chovy, chovyKey, chovy);
        Team contextTeam = ASSEMBLER.assemble("GEN");
        assertThat(matching.getChampionProficiencies().get(
                new ChampionRoleKey(new ChampionId("azir"), Position.MID))).isEqualTo(20);
        assertThat(DraftTeamContext.from(contextTeam).proficiency(
                new ChampionRoleKey(new ChampionId("azir"), Position.MID))).isEqualTo(20);

        assertThatThrownBy(() -> ASSEMBLER.assemblePlayer(chovy, chovyKey, faker))
                .hasMessageContaining("PROFICIENCY_BINDING_MISMATCH");
        assertThatThrownBy(() -> ASSEMBLER.assemblePlayer(faker, chovyKey, faker))
                .hasMessageContaining("PLAYER_ID_RATING_KEY_MISMATCH");
    }

    private Player player(Team team, Position position) {
        return team.getPlayers().stream().filter(value -> value.getPosition() == position)
                .findFirst().orElseThrow();
    }

    private int proficiency(Team team, Position position, String championId) {
        return player(team, position).getChampionProficiencies().get(
                new ChampionRoleKey(new ChampionId(championId), position));
    }

    private java.util.List<String> snapshot(Team team) {
        return team.getPlayers().stream()
                .map(player -> player.requirePlayerId() + ":" + player.getPosition() + ":" + player.getName())
                .toList();
    }
}
