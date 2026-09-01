package com.lolfm.controller;

import org.springframework.http.HttpStatus;

public final class LeagueApiV1Exception extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String field;
    private final String clientMessage;
    private final boolean retryable;
    private final Long currentLifecycleRevision;
    private final String currentLifecycleStatus;

    private LeagueApiV1Exception(
            HttpStatus status,
            String code,
            String field,
            String clientMessage,
            boolean retryable,
            Long currentLifecycleRevision,
            String currentLifecycleStatus,
            Throwable cause
    ) {
        super(code, cause);
        this.status = status;
        this.code = code;
        this.field = field;
        this.clientMessage = clientMessage;
        this.retryable = retryable;
        this.currentLifecycleRevision = currentLifecycleRevision;
        this.currentLifecycleStatus = currentLifecycleStatus;
    }

    public static LeagueApiV1Exception of(
            HttpStatus status,
            String code,
            String field,
            String message
    ) {
        return new LeagueApiV1Exception(status, code, field, message,
                false, null, null, null);
    }

    public static LeagueApiV1Exception conflict(
            String code,
            String message,
            Long revision,
            String currentStatus
    ) {
        return new LeagueApiV1Exception(HttpStatus.CONFLICT, code, null, message,
                false, revision, currentStatus, null);
    }

    public static LeagueApiV1Exception retryable(String code, String message) {
        return new LeagueApiV1Exception(HttpStatus.SERVICE_UNAVAILABLE, code, null,
                message, true, null, null, null);
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String field() { return field; }
    public String clientMessage() { return clientMessage; }
    public boolean retryable() { return retryable; }
    public Long currentLifecycleRevision() { return currentLifecycleRevision; }
    public String currentLifecycleStatus() { return currentLifecycleStatus; }
}
