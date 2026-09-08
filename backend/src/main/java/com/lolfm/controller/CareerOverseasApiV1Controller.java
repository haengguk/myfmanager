package com.lolfm.controller;

import com.lolfm.career.CareerOverseasStore;
import com.lolfm.career.CareerClStore;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins="http://localhost:5173")
@RequestMapping("/api/v1/careers/{careerId}/overseas/{year}")
public final class CareerOverseasApiV1Controller {
    private final CareerOverseasStore store;
    public CareerOverseasApiV1Controller(CareerOverseasStore store){this.store=store;}
    @GetMapping public CareerOverseasStore.View view(@PathVariable String careerId,@PathVariable int year,@RequestParam String league,@RequestParam(required=false) String event){return store.view(careerId,year,league,event);}
    @GetMapping("/{event}/results/{match}") public CareerClStore.MatchResult result(@PathVariable String careerId,@PathVariable int year,@PathVariable String event,@PathVariable String match){return store.result(careerId,year,event,match);}
}
