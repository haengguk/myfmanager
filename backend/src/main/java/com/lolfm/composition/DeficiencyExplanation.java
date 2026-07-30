package com.lolfm.composition;
import java.util.*;public record DeficiencyExplanation(CompositionDeficiency deficiency,boolean present,double severity,Map<CompositionCapability,Double> evidence){public DeficiencyExplanation{evidence=Map.copyOf(evidence);}}
