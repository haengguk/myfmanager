package com.lolfm.champion;

public enum ChampionTag {
    EARLY_PRESSURE("초반 압박"), SCALING("성장"), HYPER_SCALING("초고속 성장"), LANE_BULLY("라인전"), SAFE_LANER("안정적 라인전"),
    GANKING("갱킹"), COUNTER_GANK("역갱"), ROAMING("로밍"), PICK("픽"), ENGAGE("이니시"), DISENGAGE("역이니시"),
    PEEL("보호"), FRONTLINE("전방"), POKE("포킹"), TEAMFIGHT("한타"), WAVE_CLEAR("웨이브 정리"), SIEGE("공성"),
    SPLIT_PUSH("스플릿"), OBJECTIVE_DAMAGE("오브젝트 화력"), OBJECTIVE_CONTROL("오브젝트 제어"), SKIRMISH("교전");

    private final String displayNameKo;
    ChampionTag(String displayNameKo) { this.displayNameKo = displayNameKo; }
    public String displayNameKo() { return displayNameKo; }
}
