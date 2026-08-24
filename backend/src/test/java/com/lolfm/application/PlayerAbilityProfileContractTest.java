package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lolfm.champion.ChampionId;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.Position;
import com.lolfm.dto.RealMatchApiV1Dtos;
import com.lolfm.player.PlayerId;
import com.lolfm.simulator.PlayerRatingRuleConfig;
import com.lolfm.simulator.TeamSide;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class PlayerAbilityProfileContractTest {

    @Test
    void engineProfileRejectsNonFiniteAndInconsistentRealization() {
        ProfileMaps maps = maps(Position.TOP);
        TreeMap<String, Double> nonFinite = new TreeMap<>(maps.realized());
        nonFinite.put(PlayerSkill.MECHANICS.name(), Double.NaN);
        assertThatThrownBy(() -> engineProfile(maps.base(), nonFinite, maps.deltas()))
                .isInstanceOf(IllegalArgumentException.class);

        TreeMap<String, Double> inconsistent = new TreeMap<>(maps.deltas());
        inconsistent.put(PlayerSkill.MECHANICS.name(), 1.0);
        assertThatThrownBy(() -> engineProfile(
                maps.base(), maps.realized(), inconsistent))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void engineResultRejectsAbilityKeysFromAnotherPosition() {
        ProfileMaps jungle = maps(Position.JUNGLE);
        MatchEngineV1Output.PlayerAbilityProfileV1 profile = engineProfile(
                jungle.base(), jungle.realized(), jungle.deltas());

        assertThatThrownBy(() -> new MatchEngineV1Output.PlayerResultV1(
                new PlayerId("player-contract-top"), TeamSide.BLUE, Position.TOP,
                new ChampionId("aatrox"), 0, 0, 0, 0, 500, 0, 1, profile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match position");
    }

    @Test
    void httpProfileMirrorsEngineNumericAndPositionInvariants() {
        ProfileMaps top = maps(Position.TOP);
        TreeMap<String, Double> inconsistent = new TreeMap<>(top.deltas());
        inconsistent.put(PlayerSkill.MECHANICS.name(), -1.0);
        assertThatThrownBy(() -> httpProfile(
                top.base(), top.realized(), inconsistent))
                .isInstanceOf(IllegalArgumentException.class);

        ProfileMaps jungle = maps(Position.JUNGLE);
        RealMatchApiV1Dtos.PlayerAbilityProfile profile = httpProfile(
                jungle.base(), jungle.realized(), jungle.deltas());
        assertThatThrownBy(() -> new RealMatchApiV1Dtos.PlayerResult(
                "player-contract-top", TeamSide.BLUE, Position.TOP, "aatrox",
                0, 0, 0, 0, 500, 0, 1, profile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match position");
    }

    private MatchEngineV1Output.PlayerAbilityProfileV1 engineProfile(
            Map<String, Integer> base,
            Map<String, Double> realized,
            Map<String, Double> deltas
    ) {
        return new MatchEngineV1Output.PlayerAbilityProfileV1(
                MatchEngineV1Output.PlayerAbilityProfileV1.SCHEMA,
                base, realized, deltas, 14,
                PlayerRatingRuleConfig.proficiencyAdjustment(14));
    }

    private RealMatchApiV1Dtos.PlayerAbilityProfile httpProfile(
            Map<String, Integer> base,
            Map<String, Double> realized,
            Map<String, Double> deltas
    ) {
        return new RealMatchApiV1Dtos.PlayerAbilityProfile(
                RealMatchApiV1Dtos.PlayerAbilityProfile.SCHEMA,
                base, realized, deltas, 14,
                PlayerRatingRuleConfig.proficiencyAdjustment(14));
    }

    private ProfileMaps maps(Position position) {
        TreeMap<String, Integer> base = new TreeMap<>();
        TreeMap<String, Double> realized = new TreeMap<>();
        TreeMap<String, Double> deltas = new TreeMap<>();
        PlayerRatings.neutral(position).asMap().forEach((skill, value) -> {
            base.put(skill.name(), value);
            realized.put(skill.name(), value.doubleValue());
            deltas.put(skill.name(), 0.0);
        });
        return new ProfileMaps(base, realized, deltas);
    }

    private record ProfileMaps(
            Map<String, Integer> base,
            Map<String, Double> realized,
            Map<String, Double> deltas
    ) { }
}
