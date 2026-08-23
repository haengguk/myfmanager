package com.lolfm.controller;

import java.util.Objects;
import org.springframework.http.HttpStatus;

/** Stable client-facing failure at the isolated Real Match API V1 boundary. */
public final class RealMatchApiV1Exception extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String field;
    private final String clientMessage;

    private RealMatchApiV1Exception(
            HttpStatus status, String code, String field, String clientMessage, Throwable cause
    ) {
        super(code, cause);
        this.status = Objects.requireNonNull(status, "status");
        this.code = required(code, "code");
        this.field = field == null ? null : required(field, "field");
        this.clientMessage = required(clientMessage, "clientMessage");
    }

    public static RealMatchApiV1Exception badRequest(
            String code, String field, String clientMessage
    ) {
        return new RealMatchApiV1Exception(
                HttpStatus.BAD_REQUEST, code, field, clientMessage, null);
    }

    public static RealMatchApiV1Exception unprocessable(
            String code, String field, String clientMessage, Throwable cause
    ) {
        return new RealMatchApiV1Exception(
                HttpStatus.UNPROCESSABLE_ENTITY, code, field, clientMessage, cause);
    }

    public static RealMatchApiV1Exception integrityFailure(Throwable cause) {
        return new RealMatchApiV1Exception(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "ENGINE_OUTPUT_INTEGRITY_FAILED",
                null,
                "경기 결과 무결성을 확인할 수 없습니다.",
                cause);
    }

    public static RealMatchApiV1Exception internalFailure(Throwable cause) {
        return new RealMatchApiV1Exception(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "REAL_MATCH_INTERNAL_ERROR",
                null,
                "실제 매치 요청을 처리하지 못했습니다.",
                cause);
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
