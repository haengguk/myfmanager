package com.lolfm.controller;

import com.lolfm.application.SeriesStatus;
import java.util.Objects;
import org.springframework.http.HttpStatus;

/** Sanitized stable failure for the isolated Series API. */
public final class SeriesApiV1Exception extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String field;
    private final String clientMessage;
    private final boolean retryable;
    private final Long currentRevision;
    private final SeriesStatus currentStatus;

    private SeriesApiV1Exception(
            HttpStatus status, String code, String field, String message,
            boolean retryable, Long currentRevision, SeriesStatus currentStatus,
            Throwable cause
    ) {
        super(code, cause);
        this.status = Objects.requireNonNull(status, "status");
        this.code = Objects.requireNonNull(code, "code");
        this.field = field;
        clientMessage = Objects.requireNonNull(message, "message");
        this.retryable = retryable;
        this.currentRevision = currentRevision;
        this.currentStatus = currentStatus;
    }

    public static SeriesApiV1Exception of(
            HttpStatus status, String code, String field, String message,
            boolean retryable, Long revision, SeriesStatus seriesStatus
    ) {
        return new SeriesApiV1Exception(status, code, field, message, retryable,
                revision, seriesStatus, null);
    }

    public static SeriesApiV1Exception internal(String code, Throwable cause) {
        return new SeriesApiV1Exception(HttpStatus.INTERNAL_SERVER_ERROR, code, null,
                "Series 요청을 처리하지 못했습니다.", false, null, null, cause);
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String field() { return field; }
    public String clientMessage() { return clientMessage; }
    public boolean retryable() { return retryable; }
    public Long currentRevision() { return currentRevision; }
    public SeriesStatus currentStatus() { return currentStatus; }
}
