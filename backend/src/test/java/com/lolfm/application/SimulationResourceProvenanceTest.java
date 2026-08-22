package com.lolfm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationResourceProvenanceTest {
    private static final String HASH = "0".repeat(64);

    @Test
    void genericResourceSnapshotRecordsFutureEnabledJungleProfilesWithoutOwningPolicy() {
        SimulationResourceProvenance provenance = provenance(2);

        assertThat(provenance.jungleClearGameplayEnabledProfileCount()).isEqualTo(2);
    }

    @Test
    void enabledProfileCountCannotBeNegative() {
        assertThatThrownBy(() -> provenance(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be negative");
    }

    private static SimulationResourceProvenance provenance(int enabledCount) {
        return new SimulationResourceProvenance(
                SimulationResourceProvenance.SCHEMA,
                List.of(new VersionedResourceIdentity(
                        "RESOURCE", "/resource.json", "v1", HASH)),
                HASH,
                HASH,
                enabledCount,
                HASH);
    }
}
