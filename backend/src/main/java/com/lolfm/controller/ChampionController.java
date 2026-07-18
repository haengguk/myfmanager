package com.lolfm.controller;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionCatalogResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/champions")
@CrossOrigin(origins = "http://localhost:5173")
public class ChampionController {
    private final ChampionCatalog catalog;
    public ChampionController(ChampionCatalog catalog) { this.catalog = catalog; }

    @GetMapping
    public ChampionCatalogResponse getCatalog() {
        return new ChampionCatalogResponse(catalog.championPoolVersion(), catalog.championBalanceVersion(),
                catalog.riotDataVersion(), catalog.defaultSelection(), catalog.all());
    }
}
