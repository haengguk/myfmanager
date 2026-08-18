package com.lolfm.domain;

public class Player {

    private final String name;
    private final Position position;
    private final PlayerAttributes attributes;
    private final PlayerRatings ratings;
    private final ChampionProficiencies championProficiencies;
    private final boolean legacyProfile;

    public Player(String name, Position position, PlayerAttributes attributes) {
        this(name, position, attributes, legacyRatings(position, attributes), ChampionProficiencies.neutral(), true);
    }

    public Player(String name, Position position, PlayerRatings ratings,
                  ChampionProficiencies championProficiencies) {
        this(name, position, new PlayerAttributes(
                ratings.get(PlayerSkill.MECHANICS),
                ratings.get(PlayerSkill.DECISION_MAKING),
                position == Position.JUNGLE || position == Position.SUPPORT
                        ? PlayerRatings.NEUTRAL : ratings.get(PlayerSkill.FARMING),
                ratings.get(PlayerSkill.COMBAT_EXECUTION)), ratings, championProficiencies, false);
    }

    private Player(String name, Position position, PlayerAttributes attributes, PlayerRatings ratings,
                   ChampionProficiencies championProficiencies, boolean legacyProfile) {
        this.name = name;
        this.position = position;
        this.attributes = attributes;
        if (ratings.position() != position) throw new IllegalArgumentException("Rating position mismatch");
        this.ratings = ratings;
        this.championProficiencies = championProficiencies;
        this.legacyProfile = legacyProfile;
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

    private static PlayerRatings legacyRatings(Position position, PlayerAttributes attributes) {
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
