package com.lolfm.composition;
import java.util.*;
public record DeficiencyDefinition(CompositionDeficiency deficiency,Map<CompositionCapability,Double> thresholds,String explanationKey){public DeficiencyDefinition{Objects.requireNonNull(deficiency);thresholds=Map.copyOf(thresholds);if(explanationKey==null||explanationKey.isBlank())throw new IllegalArgumentException();}}
