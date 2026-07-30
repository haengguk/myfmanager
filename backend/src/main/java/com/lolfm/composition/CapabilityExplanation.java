package com.lolfm.composition;
import java.util.*;public record CapabilityExplanation(CompositionCapability capability,CompositionAggregationType aggregationType,double coverage,List<CapabilityContributor> contributors){public CapabilityExplanation{contributors=List.copyOf(contributors);}}
