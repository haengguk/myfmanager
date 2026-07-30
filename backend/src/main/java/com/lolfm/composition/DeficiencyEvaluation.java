package com.lolfm.composition;
import com.lolfm.champion.ChampionRoleKey;import java.util.*;
public record DeficiencyEvaluation(CompositionDeficiency deficiency,boolean present,double severity,Map<CompositionCapability,Double> capabilityEvidence,DamageChannelCoverage damageEvidence,List<ChampionRoleKey> relevantContributors,String explanationKey){public DeficiencyEvaluation{capabilityEvidence=Map.copyOf(capabilityEvidence);relevantContributors=List.copyOf(relevantContributors);if(!Double.isFinite(severity)||severity<0||severity>1||!present&&severity!=0)throw new IllegalArgumentException();}}
