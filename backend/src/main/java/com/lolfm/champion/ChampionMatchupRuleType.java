package com.lolfm.champion;

public enum ChampionMatchupRuleType {
    RANGE_POKE_PRESSURE(
            traits(ChampionMatchupTrait.RANGE_CONTROL, ChampionMatchupTrait.POKE),
            traits(ChampionMatchupTrait.SUSTAIN, ChampionMatchupTrait.MOBILITY,
                    ChampionMatchupTrait.WAVE_CONTROL)),
    ACCESS_ENGAGE_THREAT(
            traits(ChampionMatchupTrait.GAP_CLOSE, ChampionMatchupTrait.ENGAGE,
                    ChampionMatchupTrait.CROWD_CONTROL),
            traits(ChampionMatchupTrait.DISENGAGE, ChampionMatchupTrait.MOBILITY,
                    ChampionMatchupTrait.ANTI_DIVE)),
    BURST_PICK_WINDOW(
            traits(ChampionMatchupTrait.BURST, ChampionMatchupTrait.PICK,
                    ChampionMatchupTrait.CROWD_CONTROL),
            traits(ChampionMatchupTrait.DURABILITY, ChampionMatchupTrait.MOBILITY,
                    ChampionMatchupTrait.ANTI_DIVE)),
    EXTENDED_FIGHT_PRESSURE(
            traits(ChampionMatchupTrait.SUSTAINED_DAMAGE, ChampionMatchupTrait.SUSTAIN,
                    ChampionMatchupTrait.ANTI_TANK),
            traits(ChampionMatchupTrait.DURABILITY, ChampionMatchupTrait.DISENGAGE,
                    ChampionMatchupTrait.RANGE_CONTROL)),
    WAVE_TEMPO_CONTROL(
            traits(ChampionMatchupTrait.WAVE_CONTROL, ChampionMatchupTrait.RANGE_CONTROL,
                    ChampionMatchupTrait.POKE),
            traits(ChampionMatchupTrait.WAVE_CONTROL, ChampionMatchupTrait.SUSTAIN,
                    ChampionMatchupTrait.RANGE_CONTROL)),
    PEEL_ANTI_DIVE_RESPONSE(
            traits(ChampionMatchupTrait.DISENGAGE, ChampionMatchupTrait.ANTI_DIVE,
                    ChampionMatchupTrait.CROWD_CONTROL),
            traits(ChampionMatchupTrait.ENGAGE, ChampionMatchupTrait.GAP_CLOSE,
                    ChampionMatchupTrait.BURST)),
    MOBILITY_PICK_ACCESS(
            traits(ChampionMatchupTrait.MOBILITY, ChampionMatchupTrait.PICK,
                    ChampionMatchupTrait.GAP_CLOSE),
            traits(ChampionMatchupTrait.MOBILITY, ChampionMatchupTrait.DISENGAGE,
                    ChampionMatchupTrait.DURABILITY));

    private final ChampionMatchupTrait[] sourceTraits;
    private final ChampionMatchupTrait[] opponentTraits;

    ChampionMatchupRuleType(
            ChampionMatchupTrait[] sourceTraits,
            ChampionMatchupTrait[] opponentTraits
    ) {
        this.sourceTraits = sourceTraits;
        this.opponentTraits = opponentTraits;
    }

    ChampionMatchupTrait[] sourceTraits() { return sourceTraits.clone(); }
    ChampionMatchupTrait[] opponentTraits() { return opponentTraits.clone(); }

    private static ChampionMatchupTrait[] traits(ChampionMatchupTrait... values) {
        return values;
    }
}
