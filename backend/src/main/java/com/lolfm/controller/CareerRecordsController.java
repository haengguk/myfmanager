package com.lolfm.controller;

import com.lolfm.career.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/careers/{careerId}/records")
@CrossOrigin(origins="http://localhost:5173")
public final class CareerRecordsController {
    private final CareerRecordsQuery records;private final CareerRecordsRestoration restoration;
    public CareerRecordsController(CareerRecordsQuery records,CareerRecordsRestoration restoration){this.records=records;this.restoration=restoration;}
    @GetMapping public CareerRecordsQuery.Directory directory(@PathVariable String careerId){return records.directory(careerId);}
    @GetMapping("/view") public CareerRecordsQuery.View view(@PathVariable String careerId,@RequestParam(defaultValue="ALL") String kind,@RequestParam(defaultValue="") String entity,@RequestParam(required=false) Integer year,@RequestParam(required=false) Long asOf,@RequestParam(defaultValue="0") long cursor,@RequestParam(required=false) String competition,@RequestParam(defaultValue="false") boolean organization,@RequestParam(defaultValue="0") long awardCursor){return records.view(careerId,kind,entity,year,asOf,cursor,competition,organization,awardCursor);}
    @GetMapping("/matches/{recordId}") public CareerRecordsStore.Series match(@PathVariable String careerId,@PathVariable String recordId){return records.match(careerId,recordId);}
    @GetMapping("/matches/{recordId}/awards") public java.util.List<CareerAwardsStore.Award> matchAwards(@PathVariable String careerId,@PathVariable String recordId){return records.matchAwards(careerId,recordId);}
    @GetMapping("/players/{playerId}/reference") public com.fasterxml.jackson.databind.JsonNode reference(@PathVariable String careerId,@PathVariable String playerId){return records.reference(careerId,playerId);}
    @PostMapping("/restore") public CareerRecordsRestoration.Result restore(@PathVariable String careerId){return restoration.restore(careerId);}
}
