package com.lolfm.controller;

import com.lolfm.dto.TeamPlayerInformationApiV1Dtos;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Error boundary scoped to the additive reference controller. */
@RestControllerAdvice(assignableTypes = {TeamPlayerInformationApiV1Controller.class, GlobalRosterApiV1Controller.class})
public final class TeamPlayerInformationApiV1ExceptionHandler {
    @ExceptionHandler(TeamPlayerInformationApiV1Exception.class)
    public ResponseEntity<TeamPlayerInformationApiV1Dtos.ErrorResponse> reference(
            TeamPlayerInformationApiV1Exception error
    ) {
        return ResponseEntity.status(error.status()).body(response(
                error.code(), error.field(), error.clientMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<TeamPlayerInformationApiV1Dtos.ErrorResponse> integrityFailure() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response(
                "PLAYER_INFORMATION_RESOURCE_INTEGRITY_FAILURE", null,
                "선수 정보 resource 무결성을 확인할 수 없습니다."));
    }

    private static TeamPlayerInformationApiV1Dtos.ErrorResponse response(
            String code,
            String field,
            String message
    ) {
        return new TeamPlayerInformationApiV1Dtos.ErrorResponse(
                TeamPlayerInformationApiV1Dtos.ERROR_SCHEMA, code, field, message);
    }
}
