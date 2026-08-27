package com.lolfm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.draft.DraftResourceSet;
import com.lolfm.draft.DraftRuleSet;
import com.lolfm.draft.DraftScoringPolicy;
import com.lolfm.draft.PlayerControlledDraftEngine;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Explicit injectable runtime dependencies for the bounded V1 session feature. */
@Configuration
public class PlayerDraftConfiguration {
    @Bean
    Clock playerDraftClock() {
        return Clock.systemUTC();
    }

    @Bean
    PlayerControlledDraftEngine playerControlledDraftEngine(
            ObjectMapper mapper, ChampionCatalog champions
    ) {
        return new PlayerControlledDraftEngine(
                DraftResourceSet.loadDefault(mapper, champions),
                DraftRuleSet.professional(), DraftScoringPolicy.standard());
    }
}
