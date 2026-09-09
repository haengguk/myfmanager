package com.lolfm.controller;

import com.lolfm.career.CareerInboxService;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins="http://localhost:5173")
@RequestMapping("/api/v1/careers/{careerId}/inbox")
public final class CareerInboxController {
    private final CareerInboxService inbox;
    public CareerInboxController(CareerInboxService inbox){this.inbox=inbox;}
    @GetMapping public CareerInboxService.Feed feed(@PathVariable String careerId,@RequestParam(required=false) Integer year,@RequestParam(defaultValue="") String kind,@RequestParam(defaultValue="false") boolean includeDevelopment,@RequestParam(required=false) Long asOf,@RequestParam(defaultValue="0") long cursor){return inbox.feed(careerId,year,kind,includeDevelopment,asOf,cursor);}
    @GetMapping("/{sequence}") public CareerInboxService.Entry detail(@PathVariable String careerId,@PathVariable long sequence){return inbox.detail(careerId,sequence);}
    @PostMapping("/read") public CareerInboxService.ReadResult read(@PathVariable String careerId,@RequestBody CareerInboxService.ReadRequest request){return inbox.markRead(careerId,request);}
}
