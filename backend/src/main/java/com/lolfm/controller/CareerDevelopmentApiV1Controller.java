package com.lolfm.controller;
import com.lolfm.career.CareerDevelopmentStore;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/careers/{careerId}/development")
@CrossOrigin(origins="http://localhost:5173")
public final class CareerDevelopmentApiV1Controller {
    private final CareerDevelopmentStore development;
    private final CareerApiV1RequestParser parser;
    public CareerDevelopmentApiV1Controller(CareerDevelopmentStore development,CareerApiV1RequestParser parser){this.development=development;this.parser=parser;}
    @GetMapping("/{year}") public CareerDevelopmentStore.View view(@PathVariable String careerId,@PathVariable int year){return development.view(careerId,year);}
    @PostMapping public CareerDevelopmentStore.Change change(@PathVariable String careerId,@RequestBody byte[] body){return development.change(careerId,parser.trainingCommand(body));}
}
