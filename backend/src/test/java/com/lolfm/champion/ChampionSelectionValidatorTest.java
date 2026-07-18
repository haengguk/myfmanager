package com.lolfm.champion;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.domain.Position;
import com.lolfm.simulator.PlayerKey;
import com.lolfm.simulator.TeamSide;
import org.junit.jupiter.api.Test;

class ChampionSelectionValidatorTest {
    private final ChampionSelectionValidator validator = new ChampionSelectionValidator(new ChampionCatalog(new ObjectMapper()));
    private ChampionLineupRequest blue() { return new ChampionLineupRequest("renekton", "sejuani", "azir", "jinx", "nautilus"); }
    private ChampionLineupRequest red() { return new ChampionLineupRequest("jax", "lee-sin", "ahri", "kaisa", "rakan"); }

    @Test void defaultAndExplicitProduceTenImmutableStructuredAssignments() {
        var defaults = validator.resolve(null);
        assertThat(defaults.selectionMode()).isEqualTo(ChampionSelectionMode.DEFAULT_FIXED);
        assertThat(defaults.asMap()).hasSize(10);
        assertThat(defaults.get(new PlayerKey(TeamSide.BLUE, Position.JUNGLE)).championId().value()).isEqualTo("sejuani");
        assertThatThrownBy(() -> defaults.asMap().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(validator.resolve(new ChampionSelectionRequest(blue(), red())).selectionMode()).isEqualTo(ChampionSelectionMode.EXPLICIT);
    }

    @Test void rejectsMissingUnknownWrongPositionAndBlankWithStructuredFields() {
        assertCode(new ChampionSelectionRequest(null, red()), "CHAMPION_SELECTION_MISSING");
        assertCode(new ChampionSelectionRequest(new ChampionLineupRequest(null,"sejuani","azir","jinx","nautilus"), red()), "CHAMPION_POSITION_MISSING");
        assertCode(new ChampionSelectionRequest(new ChampionLineupRequest("unknown","sejuani","azir","jinx","nautilus"), red()), "UNKNOWN_CHAMPION");
        assertCode(new ChampionSelectionRequest(new ChampionLineupRequest("azir","sejuani","renekton","jinx","nautilus"), red()), "CHAMPION_POSITION_MISMATCH");
        assertCode(new ChampionSelectionRequest(new ChampionLineupRequest(" ","sejuani","azir","jinx","nautilus"), red()), "CHAMPION_POSITION_MISSING");
    }

    @Test void rejectsSameSideAndCrossSideDuplicates() {
        assertCode(new ChampionSelectionRequest(new ChampionLineupRequest("renekton","sejuani","azir","jinx","jinx"), red()), "DUPLICATE_CHAMPION");
        assertCode(new ChampionSelectionRequest(blue(), new ChampionLineupRequest("renekton","lee-sin","ahri","kaisa","rakan")), "DUPLICATE_CHAMPION");
    }

    private void assertCode(ChampionSelectionRequest request, String code) {
        assertThatThrownBy(() -> validator.resolve(request)).isInstanceOfSatisfying(ChampionSelectionException.class,
                error -> { assertThat(error.getCode()).isEqualTo(code); assertThat(error.getField()).isNotBlank(); });
    }
}
