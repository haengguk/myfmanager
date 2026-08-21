package com.lolfm.player;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable, explicitly authored person identity. */
public record PlayerId(String value) implements Comparable<PlayerId> {
    private static final Pattern FORMAT = Pattern.compile("player-[a-z0-9]+(?:-[a-z0-9]+)*");

    @JsonCreator
    public PlayerId {
        value = Objects.requireNonNull(value, "value").trim();
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Malformed PlayerId: " + value);
        }
    }

    @JsonValue
    @Override
    public String value() {
        return value;
    }

    @Override
    public int compareTo(PlayerId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
