package com.lolfm.champion;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public record ChampionId(String value) {
    public ChampionId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("ChampionId must not be blank");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("Invalid ChampionId: " + value);
        }
    }

    @JsonValue public String jsonValue() { return value; }
    @Override public String toString() { return value; }
}
