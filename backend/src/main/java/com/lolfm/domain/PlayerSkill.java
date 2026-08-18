package com.lolfm.domain;

import java.util.EnumSet;
import java.util.Set;

public enum PlayerSkill {
    MECHANICS("조작 능력"), DECISION_MAKING("상황 판단"), MAP_AWARENESS("전장 인식"),
    POSITIONING("위치 선정"), COMBAT_EXECUTION("교전 수행"), CONSISTENCY("안정성"),
    FARMING("미니언 수급"), TRADING("교환전"), WAVE_MANAGEMENT("라인 관리"),
    LANE_PRESSURE("라인 압박"), PRIORITY_CONVERSION("주도권 활용"), SIDE_LANE("사이드 운영"),
    PATHING("동선 설계"), JUNGLE_RESOURCE_MANAGEMENT("정글 자원 관리"),
    ENEMY_JUNGLE_TRACKING("상대 동선 추적"), LANE_INTERVENTION("라인 개입"),
    OBJECTIVE_DECISION("목표물 판단"), OBJECTIVE_SECURE("목표물 마무리"),
    VISION_CONTROL("시야 장악"), LANE_SUPPORT("라인 보조"), ROTATION_PLANNING("합류 설계"),
    ENGAGE_EXECUTION("교전 개시"), ALLY_PROTECTION("아군 보호"), AREA_SETUP("지역 선점");

    private static final EnumSet<PlayerSkill> COMMON = EnumSet.of(
            MECHANICS, DECISION_MAKING, MAP_AWARENESS, POSITIONING, COMBAT_EXECUTION, CONSISTENCY);
    private static final EnumSet<PlayerSkill> LANER = EnumSet.of(
            FARMING, TRADING, WAVE_MANAGEMENT, LANE_PRESSURE, PRIORITY_CONVERSION, SIDE_LANE);
    private static final EnumSet<PlayerSkill> JUNGLE = EnumSet.of(
            PATHING, JUNGLE_RESOURCE_MANAGEMENT, ENEMY_JUNGLE_TRACKING,
            LANE_INTERVENTION, OBJECTIVE_DECISION, OBJECTIVE_SECURE);
    private static final EnumSet<PlayerSkill> SUPPORT = EnumSet.of(
            VISION_CONTROL, LANE_SUPPORT, ROTATION_PLANNING,
            ENGAGE_EXECUTION, ALLY_PROTECTION, AREA_SETUP);

    private final String koreanLabel;

    PlayerSkill(String koreanLabel) {
        this.koreanLabel = koreanLabel;
    }

    public String koreanLabel() {
        return koreanLabel;
    }

    public static Set<PlayerSkill> forPosition(Position position) {
        EnumSet<PlayerSkill> result = EnumSet.copyOf(COMMON);
        result.addAll(switch (position) {
            case TOP, MID, ADC -> LANER;
            case JUNGLE -> JUNGLE;
            case SUPPORT -> SUPPORT;
        });
        return Set.copyOf(result);
    }

    public boolean appliesTo(Position position) {
        return forPosition(position).contains(this);
    }
}
