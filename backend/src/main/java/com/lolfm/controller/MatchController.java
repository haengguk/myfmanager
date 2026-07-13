package com.lolfm.controller;

import com.lolfm.domain.MatchTimeline;
import com.lolfm.domain.Team;
import com.lolfm.dto.MatchSimulateRequest;
import com.lolfm.dto.MatchSimulateResponse;
import com.lolfm.factory.DummyDataFactory;
import com.lolfm.simulator.MatchSimulator;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matches")
@CrossOrigin(origins = "http://localhost:5173")
public class MatchController {

    private final MatchSimulator matchSimulator;
    private final DummyDataFactory dummyDataFactory;

    public MatchController(MatchSimulator matchSimulator, DummyDataFactory dummyDataFactory) {
        this.matchSimulator = matchSimulator;
        this.dummyDataFactory = dummyDataFactory;
    }

    @PostMapping("/simulate")
    public MatchSimulateResponse simulate(@RequestBody(required = false) MatchSimulateRequest request) {
        long seed = request != null && request.getSeed() != null
                ? request.getSeed()
                : System.currentTimeMillis();

        Team blueTeam = dummyDataFactory.createBlueTeam();
        Team redTeam = dummyDataFactory.createRedTeam();
        MatchTimeline timeline = matchSimulator.simulate(blueTeam, redTeam, seed);

        return new MatchSimulateResponse(seed, blueTeam, redTeam, timeline);
    }
}
