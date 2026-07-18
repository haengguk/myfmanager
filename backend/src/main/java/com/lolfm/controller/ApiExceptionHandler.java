package com.lolfm.controller;

import com.lolfm.champion.ChampionSelectionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ChampionSelectionException.class)
    public ResponseEntity<ApiErrorResponse> championSelection(ChampionSelectionException error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse(
                error.getCode(), error.getField(), error.getChampionId(), error.getMessage()));
    }
}
