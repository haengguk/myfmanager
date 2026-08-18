package com.lolfm.simulator;
import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.*;
import org.junit.jupiter.api.Test;
class ChampionPairInteractionShapeTest {
 @Test void matrixHasExactlyTwoThousandTwentyFiveRows(){var c=HistoricalChampionCatalog.initialThirty();long n=0;for(var t:InteractionShapeFormula.Type.values())n+=InteractionShapeGeneratedCatalog.build(c,t).rows().size();assertThat(n).isEqualTo(2025);}
 @Test void productV1ExactlyPreservesFrozenFormula(){var c=HistoricalChampionCatalog.initialThirty();var a=PairInteractionGeneratedCatalog.build(c).rows();var b=InteractionShapeGeneratedCatalog.build(c,InteractionShapeFormula.Type.PRODUCT_CENTERED_V1).rows();assertThat(b).hasSameSizeAs(a);for(int i=0;i<a.size();i++)assertThat(b.get(i).forwardEdge()).isEqualTo(a.get(i).interactionEdge());}
 @Test void candidatesAreAntisymmetricAndFinite(){var c=HistoricalChampionCatalog.initialThirty();for(var t:InteractionShapeFormula.Type.values())assertThat(InteractionShapeGeneratedCatalog.build(c,t).rows()).allMatch(r->r.directionalityValid()&&Double.isFinite(r.forwardEdge())&&Math.abs(r.forwardEdge())<=.30);}
 @Test void helperBoundariesNormalizeZero(){assertThat(com.lolfm.champion.ChampionMatchupEvaluator.geometricInteraction(0,.5)).isEqualTo(0.0);assertThat(InteractionShapeFormula.exposureGate(-1)).isEqualTo(.25);assertThat(InteractionShapeFormula.exposureGate(2)).isEqualTo(1.0);}
}
