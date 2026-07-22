package com.lolfm.champion;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ChampionPowerBudgetTest {
    @Test void standardizedWeightsAreUnitAndAllThirtyProfilesAreAuditedWithoutMutation() {
        ChampionCatalog champions=new ChampionCatalog(new ObjectMapper()); ChampionPowerProfileCatalog profiles=new ChampionPowerProfileCatalog(new ObjectMapper(),champions);
        ChampionPowerBudgetAuditor auditor=new ChampionPowerBudgetAuditor(champions,profiles); var before=profiles.all(); var audit=auditor.audit();
        assertThat(auditor.standardizedStates()).hasSize(5); assertThat(auditor.standardizedStates().stream().mapToDouble(ChampionPowerBudgetAuditor.StateWeight::weight).sum()).isEqualTo(1.0);
        assertThat(auditor.contextWeights()).hasSize(9); assertThat(auditor.contextWeights().values().stream().mapToDouble(Double::doubleValue).sum()).isEqualTo(1.0);
        assertThat(audit.champions()).hasSize(30); assertThat(audit.positions()).hasSize(5); assertThat(profiles.all()).containsExactlyElementsOf(before);
    }
}
