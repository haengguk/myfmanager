package com.lolfm.controller;

import com.lolfm.dto.SeriesApiV1Dtos;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SeriesApiV1Controller.class)
public final class SeriesApiV1ExceptionHandler {
    @ExceptionHandler(SeriesApiV1Exception.class)
    public ResponseEntity<SeriesApiV1Dtos.ErrorResponse> series(SeriesApiV1Exception error) {
        return ResponseEntity.status(error.status()).body(new SeriesApiV1Dtos.ErrorResponse(
                SeriesApiV1Dtos.ERROR_SCHEMA, error.code(), error.field(),
                error.clientMessage(), error.retryable(), error.currentRevision(),
                error.currentStatus()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<SeriesApiV1Dtos.ErrorResponse> malformed() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new SeriesApiV1Dtos.ErrorResponse(SeriesApiV1Dtos.ERROR_SCHEMA,
                        "MALFORMED_REQUEST", null,
                        "요청 본문은 유효한 JSON 객체여야 합니다.", false, null, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<SeriesApiV1Dtos.ErrorResponse> internal() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new SeriesApiV1Dtos.ErrorResponse(SeriesApiV1Dtos.ERROR_SCHEMA,
                        "SERIES_INTERNAL_ERROR", null, "Series 요청을 처리하지 못했습니다.",
                        false, null, null));
    }
}
