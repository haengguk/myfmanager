package com.lolfm.controller;

import com.lolfm.application.CareerApiV1ResponseMapper;
import com.lolfm.career.CareerApplicationService;
import com.lolfm.career.CareerCalendarApplicationService;
import com.lolfm.dto.CareerApiV1Dtos;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/careers")
@CrossOrigin(origins = "http://localhost:5173")
public final class CareerApiV1Controller {
    private final CareerApplicationService careers;
    private final CareerApiV1RequestParser parser;
    private final CareerApiV1ResponseMapper mapper;
    private final CareerCalendarApplicationService calendar;

    public CareerApiV1Controller(
            CareerApplicationService careers,
            CareerApiV1RequestParser parser,
            CareerApiV1ResponseMapper mapper,
            CareerCalendarApplicationService calendar
    ) {
        this.careers = careers;
        this.parser = parser;
        this.mapper = mapper;
        this.calendar = calendar;
    }

    @PostMapping
    public ResponseEntity<CareerApiV1Dtos.CreateResponse> create(
            @RequestBody byte[] body
    ) {
        CareerApplicationService.CreateResult result = careers.create(parser.create(body));
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .body(mapper.created(result));
    }

    @GetMapping
    public CareerApiV1Dtos.ListResponse list() {
        return mapper.list(careers.list());
    }

    @GetMapping("/{careerId}")
    public CareerApiV1Dtos.CareerView get(@PathVariable String careerId) {
        return mapper.view(careers.get(careerId));
    }

    @GetMapping("/{careerId}/calendar")
    public CareerApiV1Dtos.CalendarView calendar(@PathVariable String careerId) {
        return mapper.calendar(calendar.view(careers.get(careerId).career()));
    }

    @PostMapping("/{careerId}/advance")
    public ResponseEntity<CareerApiV1Dtos.AdvanceResponse> advance(
            @PathVariable String careerId,
            @RequestBody byte[] body
    ) {
        CareerApiV1Dtos.AdvanceRequest request = parser.advance(body);
        CareerCalendarApplicationService.AdvanceResult result = calendar.advance(
                careers.get(careerId).career(), request.schemaVersion(),
                request.expectedCalendarRevision(), request.mode(),
                request.clientCommandId());
        return ResponseEntity.status(result.httpStatus()).body(mapper.advanced(result));
    }
}
