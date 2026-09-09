package com.lolfm.controller;
import com.lolfm.career.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@CrossOrigin(origins="http://localhost:5173")
@RequestMapping("/api/v1/careers/{careerId}/scouting")
public final class CareerScoutingController {
    private final CareerScoutingService scouting;private final CareerOpponentService opponents;
    public CareerScoutingController(CareerScoutingService scouting,CareerOpponentService opponents){this.scouting=scouting;this.opponents=opponents;}
    @GetMapping public CareerScoutingService.Page search(@PathVariable String careerId,@RequestParam Map<String,String> query){return scouting.search(careerId,query);}
    @PostMapping("/interest") public CareerScoutingService.Interest interest(@PathVariable String careerId,@RequestBody CareerScoutingService.InterestRequest request){return scouting.interest(careerId,request);}
    @GetMapping("/compare") public CareerScoutingService.Comparison compare(@PathVariable String careerId,@RequestParam List<String> players,@RequestParam int year,@RequestParam(defaultValue="") String competition){return scouting.compare(careerId,players,year,competition);}
    @GetMapping("/opponent") public CareerOpponentService.Analysis opponent(@PathVariable String careerId,@RequestParam(required=false) Integer year,@RequestParam(required=false) String team,@RequestParam(required=false) String competition,@RequestParam(defaultValue="5") int limit){return opponents.analyze(careerId,year,team,competition,limit);}
}
