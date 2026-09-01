package com.lolfm.controller;

import com.lolfm.dto.LeagueApiV1Dtos;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LeagueApiV1Controller.class)
public final class LeagueApiV1ExceptionHandler {
    @ExceptionHandler(LeagueApiV1Exception.class)
    public ResponseEntity<LeagueApiV1Dtos.ErrorResponse> league(
            LeagueApiV1Exception error
    ) {
        return ResponseEntity.status(error.status()).body(new LeagueApiV1Dtos.ErrorResponse(
                LeagueApiV1Dtos.ERROR_SCHEMA, error.code(), error.field(),
                error.clientMessage(), error.retryable(),
                error.currentLifecycleRevision(), error.currentLifecycleStatus()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<LeagueApiV1Dtos.ErrorResponse> malformed() {
        return error(HttpStatus.BAD_REQUEST, "LEAGUE_MALFORMED_REQUEST",
                "요청 본문은 유효한 JSON 객체여야 합니다.", false);
    }

    @ExceptionHandler(TransientDataAccessException.class)
    public ResponseEntity<LeagueApiV1Dtos.ErrorResponse> temporarilyUnavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "LEAGUE_TEMPORARILY_UNAVAILABLE",
                "League 저장소를 일시적으로 사용할 수 없습니다.", true);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<LeagueApiV1Dtos.ErrorResponse> internal() {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "LEAGUE_INTERNAL_ERROR",
                "League 요청을 처리하지 못했습니다.", false);
    }

    private static ResponseEntity<LeagueApiV1Dtos.ErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            boolean retryable
    ) {
        return ResponseEntity.status(status).body(new LeagueApiV1Dtos.ErrorResponse(
                LeagueApiV1Dtos.ERROR_SCHEMA, code, null, message, retryable,
                null, null));
    }
}
