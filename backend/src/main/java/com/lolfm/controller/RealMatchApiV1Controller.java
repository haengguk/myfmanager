package com.lolfm.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lolfm.application.RealMatchApiV1Service;
import com.lolfm.dto.RealMatchApiV1Dtos;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Additive HTTP boundary for one isolated real-roster Match Engine V1 game. */
@RestController
@RequestMapping("/api/v1/real-matches")
@CrossOrigin(origins = "http://localhost:5173")
public final class RealMatchApiV1Controller {
    private final RealMatchApiV1RequestParser requests;
    private final RealMatchApiV1Service service;

    public RealMatchApiV1Controller(
            RealMatchApiV1RequestParser requests, RealMatchApiV1Service service
    ) {
        this.requests = requests;
        this.service = service;
    }

    @GetMapping("/options")
    public RealMatchApiV1Dtos.OptionsResponse options() {
        return service.options();
    }

    @PostMapping("/simulate")
    public RealMatchApiV1Dtos.Response simulate(
            @RequestBody(required = false) JsonNode body
    ) {
        return service.simulate(requests.parse(body));
    }
}
