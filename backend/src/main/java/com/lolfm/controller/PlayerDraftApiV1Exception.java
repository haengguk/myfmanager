package com.lolfm.controller;

import java.util.Objects;
import org.springframework.http.HttpStatus;

/** Stable client-facing failure for the isolated player Draft boundary. */
public final class PlayerDraftApiV1Exception extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String field;
    private final String clientMessage;

    private PlayerDraftApiV1Exception(
            HttpStatus status, String code, String field, String message, Throwable cause
    ) {
        super(code, cause);
        this.status = Objects.requireNonNull(status, "status");
        this.code = required(code, "code");
        this.field = field;
        clientMessage = required(message, "message");
    }

    public static PlayerDraftApiV1Exception badRequest(
            String code, String field, String message
    ) {
        return new PlayerDraftApiV1Exception(
                HttpStatus.BAD_REQUEST, code, field, message, null);
    }

    public static PlayerDraftApiV1Exception notFound() {
        return new PlayerDraftApiV1Exception(
                HttpStatus.NOT_FOUND, "PLAYER_DRAFT_SESSION_NOT_FOUND", null,
                "플레이어 드래프트 세션을 찾을 수 없습니다.", null);
    }

    public static PlayerDraftApiV1Exception gone() {
        return new PlayerDraftApiV1Exception(
                HttpStatus.GONE, "PLAYER_DRAFT_SESSION_EXPIRED", null,
                "플레이어 드래프트 세션이 만료되었습니다.", null);
    }

    public static PlayerDraftApiV1Exception conflict(
            String code, String field, String message
    ) {
        return new PlayerDraftApiV1Exception(
                HttpStatus.CONFLICT, code, field, message, null);
    }

    public static PlayerDraftApiV1Exception unprocessable(
            String code, String field, String message, Throwable cause
    ) {
        return new PlayerDraftApiV1Exception(
                HttpStatus.UNPROCESSABLE_ENTITY, code, field, message, cause);
    }

    public static PlayerDraftApiV1Exception internal(Throwable cause) {
        return new PlayerDraftApiV1Exception(
                HttpStatus.INTERNAL_SERVER_ERROR, "PLAYER_DRAFT_INTERNAL_ERROR", null,
                "플레이어 드래프트 요청을 처리하지 못했습니다.", cause);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String field() {
        return field;
    }

    public String clientMessage() {
        return clientMessage;
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
