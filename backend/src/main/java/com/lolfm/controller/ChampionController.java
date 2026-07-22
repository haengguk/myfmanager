package com.lolfm.controller;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionCatalogResponse;
import com.lolfm.champion.ChampionPowerProfileCatalog;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/champions")
@CrossOrigin(origins = "http://localhost:5173")
public class ChampionController {
    private final ChampionCatalog catalog;
    private final ChampionPowerProfileCatalog profiles;
    public ChampionController(ChampionCatalog catalog,ChampionPowerProfileCatalog profiles){this.catalog=catalog;this.profiles=profiles;}

    @GetMapping
    public ChampionCatalogResponse getCatalog() {
        return new ChampionCatalogResponse(catalog.championPoolVersion(),catalog.championBalanceVersion(),profiles.profileVersion(),catalog.riotDataVersion(),catalog.defaultSelection(),catalog.all().stream().map(d->d.withProfile(profiles.get(d.id()))).toList());
    }
}
