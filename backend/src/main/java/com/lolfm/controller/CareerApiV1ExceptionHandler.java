package com.lolfm.controller;

import com.lolfm.career.CareerException;
import com.lolfm.dto.CareerApiV1Dtos;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CareerApiV1Controller.class)
public final class CareerApiV1ExceptionHandler {
    @ExceptionHandler(CareerException.class)
    public ResponseEntity<CareerApiV1Dtos.ErrorResponse> career(CareerException error) {
        HttpStatus status = switch (error.type()) {
            case REQUEST_INVALID -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case MANAGED_TEAM_NOT_FOUND -> HttpStatus.UNPROCESSABLE_ENTITY;
            case COMMAND_CONFLICT -> HttpStatus.CONFLICT;
            case LINKED_SEASON_INTEGRITY_FAILURE, RESOURCE_INTEGRITY_FAILURE ->
                    HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return response(status, code(error.type()), error.field(), error.clientMessage());
    }

    @ExceptionHandler(TransientDataAccessException.class)
    public ResponseEntity<CareerApiV1Dtos.ErrorResponse> temporarilyUnavailable() {
        return response(HttpStatus.SERVICE_UNAVAILABLE,
                "CAREER_TEMPORARILY_UNAVAILABLE", null,
                "Career 저장소를 일시적으로 사용할 수 없습니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CareerApiV1Dtos.ErrorResponse> internal() {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "CAREER_INTERNAL_ERROR", null,
                "Career 요청을 처리하지 못했습니다.");
    }

    private static String code(CareerException.Type type) {
        return switch (type) {
            case REQUEST_INVALID -> "CAREER_REQUEST_INVALID";
            case NOT_FOUND -> "CAREER_NOT_FOUND";
            case MANAGED_TEAM_NOT_FOUND -> "CAREER_MANAGED_TEAM_NOT_FOUND";
            case COMMAND_CONFLICT -> "CAREER_COMMAND_CONFLICT";
            case LINKED_SEASON_INTEGRITY_FAILURE ->
                    "CAREER_LINKED_SEASON_INTEGRITY_FAILURE";
            case RESOURCE_INTEGRITY_FAILURE -> "CAREER_RESOURCE_INTEGRITY_FAILURE";
        };
    }

    private static ResponseEntity<CareerApiV1Dtos.ErrorResponse> response(
            HttpStatus status,
            String code,
            String field,
            String message
    ) {
        return ResponseEntity.status(status).body(new CareerApiV1Dtos.ErrorResponse(
                CareerApiV1Dtos.ERROR_SCHEMA, code, field, message));
    }
}
