package com.lolfm.controller;

import com.lolfm.career.CareerSeasonApplicationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/careers/{careerId}/seasons")
@CrossOrigin(origins = "http://localhost:5173")
public final class CareerSeasonApiV1Controller {
    private final CareerSeasonApplicationService seasons;
    private final CareerApiV1RequestParser parser;
    public CareerSeasonApiV1Controller(CareerSeasonApplicationService seasons,CareerApiV1RequestParser parser){this.seasons=seasons;this.parser=parser;}
    @GetMapping public CareerSeasonApplicationService.SeasonList list(@PathVariable String careerId){return seasons.list(careerId);}
    @GetMapping("/{year}") public CareerSeasonApplicationService.SeasonDetail detail(@PathVariable String careerId,@PathVariable int year){return seasons.detail(careerId,year);}
    @PostMapping("/transition") public CareerSeasonApplicationService.Transition transition(@PathVariable String careerId,@RequestBody byte[] body){return seasons.transition(careerId,parser.seasonTransition(body));}
}
