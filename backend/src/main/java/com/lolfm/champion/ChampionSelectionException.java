package com.lolfm.champion;

public class ChampionSelectionException extends RuntimeException {
    private final String code;
    private final String field;
    private final String championId;

    public ChampionSelectionException(String code, String field, String championId, String message) {
        super(message);
        this.code = code;
        this.field = field;
        this.championId = championId;
    }
    public String getCode() { return code; }
    public String getField() { return field; }
    public String getChampionId() { return championId; }
}
