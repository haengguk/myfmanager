package com.lolfm.player;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PlayerIdTest {
    @Test
    void hasStableStringSerializationAndValueEquality() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        PlayerId first = new PlayerId("player-chovy");
        PlayerId same = new PlayerId("player-chovy");

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(mapper.writeValueAsString(first)).isEqualTo("\"player-chovy\"");
        assertThat(mapper.readValue("\"player-chovy\"", PlayerId.class)).isEqualTo(first);
    }
}
