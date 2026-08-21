package com.lolfm.domain;

import com.lolfm.player.PlayerId;
import java.util.Objects;

public class Player {

    private final PlayerId playerId;
    private final String name;
    private final Position position;
    private final PlayerAttributes attributes;
    private final PlayerRatings ratings;
    private final ChampionProficiencies championProficiencies;
    private final boolean legacyProfile;

    /** Legacy fixture boundary. It deliberately does not infer a PlayerId from display text. */
    public Player(String name, Position position, PlayerAttributes attributes) {
        this(null, name, position, attributes, legacyRatings(position, attributes),
                ChampionProficiencies.neutral(), true);
    }

    public Player(PlayerId playerId, String name, Position position, PlayerAttributes attributes) {
        this(Objects.requireNonNull(playerId, "playerId"), name, position, attributes,
                legacyRatings(position, attributes), ChampionProficiencies.neutral(), true);
    }

    /** Legacy-compatible real-rating boundary without an inferred identity. */
    public Player(String name, Position position, PlayerRatings ratings,
                  ChampionProficiencies championProficiencies) {
        this(null, name, position, attributesFrom(position, ratings), ratings,
                championProficiencies, false);
    }

    public Player(PlayerId playerId, String name, Position position, PlayerRatings ratings,
                  ChampionProficiencies championProficiencies) {
        this(Objects.requireNonNull(playerId, "playerId"), name, position, new PlayerAttributes(
                ratings.get(PlayerSkill.MECHANICS),
                ratings.get(PlayerSkill.DECISION_MAKING),
                position == Position.JUNGLE || position == Position.SUPPORT
                        ? PlayerRatings.NEUTRAL : ratings.get(PlayerSkill.FARMING),
                ratings.get(PlayerSkill.COMBAT_EXECUTION)), ratings, championProficiencies, false);
    }

    private Player(PlayerId playerId, String name, Position position, PlayerAttributes attributes,
                   PlayerRatings ratings, ChampionProficiencies championProficiencies,
                   boolean legacyProfile) {
        this.playerId = playerId;
        this.name = Objects.requireNonNull(name, "name").trim();
        if (this.name.isBlank()) throw new IllegalArgumentException("name is required");
        this.position = Objects.requireNonNull(position, "position");
        this.attributes = Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(ratings, "ratings");
        if (ratings.position() != position) throw new IllegalArgumentException("Rating position mismatch");
        this.ratings = ratings;
        this.championProficiencies = Objects.requireNonNull(
                championProficiencies, "championProficiencies");
        this.legacyProfile = legacyProfile;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public PlayerId getPlayerId() { return playerId; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean hasStablePlayerId() { return playerId != null; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public PlayerId requirePlayerId() {
        if (playerId == null) {
            throw new IllegalStateException("Legacy fixture player has no stable PlayerId: " + name);
        }
        return playerId;
    }
    public String getName() { return name; }
    public Position getPosition() { return position; }
    public PlayerAttributes getAttributes() { return attributes; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public PlayerRatings getRatings() { return ratings; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public ChampionProficiencies getChampionProficiencies() { return championProficiencies; }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isLegacyProfile() { return legacyProfile; }

    private static PlayerAttributes attributesFrom(Position position, PlayerRatings ratings) {
        return new PlayerAttributes(
                ratings.get(PlayerSkill.MECHANICS),
                ratings.get(PlayerSkill.DECISION_MAKING),
                position == Position.JUNGLE || position == Position.SUPPORT
                        ? PlayerRatings.NEUTRAL : ratings.get(PlayerSkill.FARMING),
                ratings.get(PlayerSkill.COMBAT_EXECUTION));
    }

    private static PlayerRatings legacyRatings(Position position, PlayerAttributes attributes) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(attributes, "attributes");
        PlayerRatings ratings = PlayerRatings.neutral(position)
                .with(PlayerSkill.MECHANICS, attributes.getMechanics())
                .with(PlayerSkill.DECISION_MAKING, attributes.getAggression())
                .with(PlayerSkill.CONSISTENCY, PlayerRatings.MAX)
                .with(PlayerSkill.COMBAT_EXECUTION, attributes.getTeamfighting());
        if (position == Position.TOP || position == Position.MID || position == Position.ADC) {
            ratings = ratings.with(PlayerSkill.FARMING, attributes.getFarming())
                    .with(PlayerSkill.TRADING, attributes.getAggression())
                    .with(PlayerSkill.LANE_PRESSURE, attributes.getAggression());
        } else if (position == Position.JUNGLE) {
            ratings = ratings.with(PlayerSkill.JUNGLE_RESOURCE_MANAGEMENT, attributes.getFarming())
                    .with(PlayerSkill.LANE_INTERVENTION, attributes.getAggression());
        } else {
            ratings = ratings.with(PlayerSkill.LANE_SUPPORT, attributes.getFarming())
                    .with(PlayerSkill.ENGAGE_EXECUTION, attributes.getAggression());
        }
        return ratings;
    }
}
