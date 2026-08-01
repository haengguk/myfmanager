package com.lolfm.composition;

import java.util.List;
import java.util.Objects;

public record DirectedCompositionPressure(
        TeamCompositionContext context,
        TeamCompositionLineup source,
        TeamCompositionLineup opponent,
        double pressure,
        List<CompositionInteractionRuleEvaluation> rules
) {
    public DirectedCompositionPressure {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(opponent, "opponent");
        if (!Double.isFinite(pressure) || pressure < 0.0 || pressure > 1.0) throw new IllegalArgumentException("Invalid directed pressure");
        rules = List.copyOf(rules);
        if (rules.size() != 3) throw new IllegalArgumentException("Each context requires exactly three rules");
        pressure = pressure == 0.0 ? 0.0 : pressure;
    }
}
