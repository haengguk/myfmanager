package com.lolfm.composition;
import java.util.*;public record TeamCompositionExplanation(List<CapabilityExplanation> capabilities,List<PatternExplanation> patterns,List<DeficiencyExplanation> deficiencies){public TeamCompositionExplanation{capabilities=List.copyOf(capabilities);patterns=List.copyOf(patterns);deficiencies=List.copyOf(deficiencies);}}
