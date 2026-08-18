package com.lolfm.draft;

import java.util.Comparator;
import java.util.List;

public record DraftPlanPortfolio(List<DraftPlan> plans) {
    public DraftPlanPortfolio {
        plans = plans.stream().sorted(Comparator.comparingDouble(DraftPlan::viability).reversed()
                .thenComparing(plan -> plan.archetype().name())).toList();
        if (plans.isEmpty()) throw new IllegalArgumentException("At least one draft plan is required");
    }
    public DraftPlan preferred() { return plans.getFirst(); }
}
