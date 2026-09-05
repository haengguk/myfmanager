package com.lolfm.career;

import java.util.Objects;

/** Opaque capability minted only after canonical Series evidence verification. */
public final class VerifiedCompetitionFixtureCompletion {
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
