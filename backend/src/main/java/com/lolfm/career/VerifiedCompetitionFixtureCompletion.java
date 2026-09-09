package com.lolfm.career;

import java.util.Objects;

/** Opaque capability minted only after canonical Series evidence verification. */
public final class VerifiedCompetitionFixtureCompletion {
    private java.util.List<CareerGameStatistics> statistics=java.util.List.of();
    java.util.List<CareerGameStatistics> statistics(){return statistics;}
    VerifiedCompetitionFixtureCompletion statistics(java.util.List<CareerGameStatistics> value){statistics=java.util.List.copyOf(value);return this;}
    private final CareerCompetitionFixtureCompletionReceiptV1 receipt;

    VerifiedCompetitionFixtureCompletion(
            CareerCompetitionFixtureCompletionReceiptV1 receipt
    ) {
        this.receipt = Objects.requireNonNull(receipt, "receipt");
    }

    CareerCompetitionFixtureCompletionReceiptV1 receipt() {
        return receipt;
    }
}
