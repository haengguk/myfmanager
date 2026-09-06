package com.lolfm.controller;

import com.lolfm.career.CareerRosterStore;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/careers/{careerId}/roster")
@CrossOrigin(origins="http://localhost:5173")
public final class CareerRosterApiV1Controller {
    private final CareerRosterStore rosters;
    private final CareerApiV1RequestParser parser;
    public CareerRosterApiV1Controller(CareerRosterStore rosters,CareerApiV1RequestParser parser) {this.rosters=rosters;this.parser=parser;}
    @GetMapping("/{year}") public CareerRosterStore.View view(@PathVariable String careerId,@PathVariable int year) {return rosters.view(careerId,year);}
    @PostMapping public CareerRosterStore.Change change(@PathVariable String careerId,@RequestBody byte[] body) {return rosters.change(careerId,parser.rosterCommand(body));}
}
