package com.lolfm.controller;
import com.lolfm.career.CareerClStore;
import org.springframework.web.bind.annotation.*;
@RestController
@CrossOrigin(origins="http://localhost:5173")
@RequestMapping("/api/v1/careers/{careerId}/cl")
public final class CareerClApiV1Controller {
    private final CareerClStore cl;private final CareerApiV1RequestParser parser;
    public CareerClApiV1Controller(CareerClStore cl,CareerApiV1RequestParser parser){this.cl=cl;this.parser=parser;}
    @GetMapping("/{year}") public CareerClStore.View view(@PathVariable String careerId,@PathVariable int year){return cl.view(careerId,year);}
    @GetMapping("/{year}/results/{matchId}") public CareerClStore.MatchResult result(@PathVariable String careerId,@PathVariable int year,@PathVariable String matchId){return cl.result(careerId,year,matchId);}
    @PostMapping public CareerClStore.Change change(@PathVariable String careerId,@RequestBody byte[] body){return cl.change(careerId,parser.clCommand(body));}
}
