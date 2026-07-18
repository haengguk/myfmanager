package com.lolfm.controller;

public record ApiErrorResponse(String code, String field, String championId, String message) { }
