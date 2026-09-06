package com.lolfm.controller;

import com.lolfm.career.CareerMarketStore;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/careers/{careerId}/market")
@CrossOrigin(origins="http://localhost:5173")
public final class CareerMarketApiV1Controller {
    private final CareerMarketStore market;
    private final CareerApiV1RequestParser parser;
    public CareerMarketApiV1Controller(CareerMarketStore market,CareerApiV1RequestParser parser) {this.market=market;this.parser=parser;}
    @GetMapping("/{year}") public CareerMarketStore.View view(@PathVariable String careerId,@PathVariable int year) {return market.view(careerId,year);}
    @PostMapping("/trades") public CareerMarketStore.Change trade(@PathVariable String careerId,@RequestBody byte[] body) {return market.tradeCommand(careerId,parser.tradeCommand(body));}
    @PostMapping public CareerMarketStore.Change command(@PathVariable String careerId,@RequestBody byte[] body) {return market.command(careerId,parser.marketCommand(body));}
}
