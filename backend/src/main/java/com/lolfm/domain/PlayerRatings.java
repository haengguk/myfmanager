package com.lolfm.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PlayerRatings {
    public static final int MIN = 1;
    public static final int MAX = 20;
    public static final int NEUTRAL = 14;
    private final Position position;
    private final Map<PlayerSkill, Integer> values;

    public PlayerRatings(Position position, Map<PlayerSkill, Integer> values) {
        this.position = Objects.requireNonNull(position, "position");
        Set<PlayerSkill> required = PlayerSkill.forPosition(position);
        if (!values.keySet().equals(required)) {
            throw new IllegalArgumentException("Expected exactly the 12 ratings for " + position);
        }
        EnumMap<PlayerSkill, Integer> copy = new EnumMap<>(PlayerSkill.class);
        values.forEach((skill, rating) -> copy.put(skill, validate(rating)));
        this.values = Map.copyOf(copy);
    }

    public static PlayerRatings neutral(Position position) {
        EnumMap<PlayerSkill, Integer> values = new EnumMap<>(PlayerSkill.class);
        for (PlayerSkill skill : PlayerSkill.forPosition(position)) values.put(skill, NEUTRAL);
        return new PlayerRatings(position, values);
    }

    public PlayerRatings with(PlayerSkill skill, int rating) {
        if (!skill.appliesTo(position)) throw new IllegalArgumentException(skill + " does not apply to " + position);
        EnumMap<PlayerSkill, Integer> copy = new EnumMap<>(PlayerSkill.class);
        copy.putAll(values);
        copy.put(skill, validate(rating));
        return new PlayerRatings(position, copy);
    }

    public int get(PlayerSkill skill) {
        Integer value = values.get(skill);
        if (value == null) throw new IllegalArgumentException(skill + " does not apply to " + position);
        return value;
    }

    public Position position() { return position; }
    public Map<PlayerSkill, Integer> asMap() { return values; }

    private static int validate(Integer value) {
        if (value == null || value < MIN || value > MAX) {
            throw new IllegalArgumentException("Player rating must be 1..20: " + value);
        }
        return value;
    }
}
