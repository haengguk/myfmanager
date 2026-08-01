package com.lolfm.composition;

public enum CompositionInteractionFormula {
    GAP_REFERENCE,
    PRODUCT_EXPOSURE,
    GEOMETRIC_EXPOSURE;

    public double exposure(double sourceStrength, double oppositionStrength) {
        validate(sourceStrength, "sourceStrength");
        validate(oppositionStrength, "oppositionStrength");
        double value = switch (this) {
            case GAP_REFERENCE -> Math.max(0.0, sourceStrength - oppositionStrength);
            case PRODUCT_EXPOSURE -> sourceStrength * (1.0 - oppositionStrength);
            case GEOMETRIC_EXPOSURE -> Math.sqrt(sourceStrength * (1.0 - oppositionStrength));
        };
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalStateException("Invalid interaction exposure");
        }
        return value == 0.0 ? 0.0 : value;
    }

    private static void validate(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and within [0,1]");
        }
    }
}
