package com.lolfm.controller;

import com.lolfm.dto.PlayerDraftApiV1Dtos;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Sanitized error boundary scoped to the additive player Draft controller. */
@RestControllerAdvice(assignableTypes = PlayerDraftApiV1Controller.class)
public final class PlayerDraftApiV1ExceptionHandler {
    @ExceptionHandler(PlayerDraftApiV1Exception.class)
    ResponseEntity<PlayerDraftApiV1Dtos.ErrorResponse> playerDraft(
            PlayerDraftApiV1Exception error
    ) {
        return ResponseEntity.status(error.status()).body(response(
                error.code(), error.field(), error.clientMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<PlayerDraftApiV1Dtos.ErrorResponse> malformedJson() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response(
                "MALFORMED_REQUEST", null, "요청 본문은 유효한 JSON 객체여야 합니다."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<PlayerDraftApiV1Dtos.ErrorResponse> internalFailure() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response(
                "PLAYER_DRAFT_INTERNAL_ERROR", null,
                "플레이어 드래프트 요청을 처리하지 못했습니다."));
    }

    private static PlayerDraftApiV1Dtos.ErrorResponse response(
            String code, String field, String message
    ) {
        return new PlayerDraftApiV1Dtos.ErrorResponse(
                PlayerDraftApiV1Dtos.ERROR_SCHEMA, code, field, message);
    }
}
