package com.lolfm.draft;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** GA-specific contracts; shared current-engine laws live in the GA2 test. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Phase13GAStructuralIntegratedAuditTest {
    private DraftResourceSet resources;
    private List<Phase13GASyntheticContextFactory.SyntheticContext> contexts;
    private Phase13GAAuditSchedule.Schedule schedule;

    @BeforeAll
    void setUp() {
        resources = DraftResourceSet.loadDefault();
        contexts = Phase13GASyntheticContextFactory.create(resources);
        schedule = Phase13GAAuditSchedule.freeze(contexts);
    }

    @Test
    void auditScheduleIsFrozenAndDeterministic() {
        assertThat(schedule.gameOneCases()).hasSizeGreaterThanOrEqualTo(96)
                .isEqualTo(Phase13GAAuditSchedule.freeze(contexts).gameOneCases());
        assertThat(schedule.fearlessSeries()).isEqualTo(Phase13GAAuditSchedule.freeze(contexts).fearlessSeries());
        assertThat(schedule.gameOneCases()).isSortedAccordingTo(Comparator.comparing(
                Phase13GAAuditSchedule.GameOneCase::caseId));
    }

    @Test
    void mirroredScheduleContainsBothSideOrientations() {
        boolean mirrored = schedule.gameOneCases().stream().anyMatch(value -> schedule.gameOneCases().stream()
                .anyMatch(reverse -> value.blueContextId().equals(reverse.redContextId())
                        && value.redContextId().equals(reverse.blueContextId())
                        && !value.caseId().equals(reverse.caseId())));
        assertThat(mirrored).isTrue();
    }

    @Test
    void frozenResourceHashesRemainExact() {
        assertThat(resources.meta().requiredLegalRoleKeyCount()).isEqualTo(216);
        assertThat(resources.meta().actualLegalRoleKeyHash()).isEqualTo(Phase13GAStructuralIntegratedAudit.LEGAL_ROLE_HASH);
        assertThat(resources.champions().composition().profileHash()).isEqualTo(Phase13GAStructuralIntegratedAudit.COMPOSITION_HASH);
    }
}
