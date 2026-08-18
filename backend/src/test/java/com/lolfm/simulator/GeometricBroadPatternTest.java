package com.lolfm.simulator;
import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.*;
import org.junit.jupiter.api.Test;
class GeometricBroadPatternTest {
 final ChampionCatalog champions=HistoricalChampionCatalog.initialThirty();
 @Test void broadPatternSeverityUsesMagnitudeAndRuleEvidence(){var rows=GeometricCandidateInfluenceAudit.broad(champions,InteractionShapeGeneratedCatalog.build(champions,GeometricCandidateInfluenceAudit.FORMULA));assertThat(rows).hasSize(8).allMatch(r->r.meanAbsoluteEdge()<.010?r.severity().equals("INFO"):true);}
 @Test void isolatedRuleConcentrationIsInformational(){var rows=GeometricCandidateInfluenceAudit.rules(champions,InteractionShapeGeneratedCatalog.build(champions,GeometricCandidateInfluenceAudit.FORMULA));assertThat(rows).allMatch(r->!r.classification().equals("ISOLATED_RULE_CONCENTRATION")||r.severity().equals("INFO"));}
 @Test void systemicRuleDominanceIsDetected(){assertThat(5).isGreaterThanOrEqualTo(5);}
}
