package com.lolfm.controller;

import com.lolfm.dto.RealMatchApiV1Dtos;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Error boundary scoped to the additive controller so legacy API semantics stay unchanged. */
@RestControllerAdvice(assignableTypes = RealMatchApiV1Controller.class)
public final class RealMatchApiV1ExceptionHandler {
    @ExceptionHandler(RealMatchApiV1Exception.class)
    public ResponseEntity<RealMatchApiV1Dtos.ErrorResponse> realMatch(
            RealMatchApiV1Exception error
    ) {
        return ResponseEntity.status(error.status()).body(response(
                error.code(), error.field(), error.clientMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RealMatchApiV1Dtos.ErrorResponse> malformedJson() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response(
                "MALFORMED_REQUEST", null,
                "요청 본문은 유효한 JSON 객체여야 합니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RealMatchApiV1Dtos.ErrorResponse> internalFailure() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response(
                "REAL_MATCH_INTERNAL_ERROR", null,
                "실제 매치 요청을 처리하지 못했습니다."));
    }

    private static RealMatchApiV1Dtos.ErrorResponse response(
            String code, String field, String message
    ) {
        return new RealMatchApiV1Dtos.ErrorResponse(
                RealMatchApiV1Dtos.ERROR_SCHEMA, code, field, message);
    }
}
