package com.lolfm.controller;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionMetadataFactory;
import com.lolfm.champion.ChampionSelectionValidator;
import com.lolfm.champion.MatchChampionAssignments;
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
    private final ChampionCatalog championCatalog;
    private final ChampionSelectionValidator championSelectionValidator;

    public MatchController(MatchSimulator matchSimulator, DummyDataFactory dummyDataFactory, ChampionCatalog championCatalog) {
        this.matchSimulator = matchSimulator;
        this.dummyDataFactory = dummyDataFactory;
        this.championCatalog = championCatalog;
        this.championSelectionValidator = new ChampionSelectionValidator(championCatalog);
    }

    @PostMapping("/simulate")
    public MatchSimulateResponse simulate(@RequestBody(required = false) MatchSimulateRequest request) {
        long seed = request != null && request.getSeed() != null
                ? request.getSeed()
                : System.currentTimeMillis();

        MatchChampionAssignments assignments = championSelectionValidator.resolve(
                request == null ? null : request.getChampionSelection());
        Team blueTeam = dummyDataFactory.createBlueTeam();
        Team redTeam = dummyDataFactory.createRedTeam();
        MatchTimeline timeline = matchSimulator.simulate(blueTeam, redTeam, seed, assignments);

        return new MatchSimulateResponse(seed, blueTeam, redTeam, timeline,
                ChampionMetadataFactory.create(championCatalog, assignments));
    }
}
