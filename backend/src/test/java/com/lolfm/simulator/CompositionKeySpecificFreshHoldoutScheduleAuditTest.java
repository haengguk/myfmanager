package com.lolfm.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("diagnostic")
@Tag("composition-holdout")
class CompositionKeySpecificFreshHoldoutScheduleAuditTest {
    private static CompositionKeySpecificFreshHoldoutGameplayAudit.Prepared prepared;
    private static String auditSource;

    @BeforeAll
    static void prepareFullPopulationSchedule() throws Exception {
        prepared = CompositionKeySpecificFreshHoldoutGameplayAudit.prepare();
        auditSource = Files.readString(Path.of(
                "src/test/java/com/lolfm/simulator/CompositionKeySpecificFreshHoldoutGameplayAudit.java"));
    }

    @Test
    void freshHoldoutUsesNoPriorOrderedPairs() {
        assertEquals(0, prepared.priorOrderedOverlap());
    }

    @Test
    void freshHoldoutUsesNoPriorUnorderedPairs() {
        assertEquals(0, prepared.priorUnorderedOverlap());
    }

    @Test
    void freshHoldoutUsesNoPriorSeeds() {
        assertEquals(0, prepared.priorSeedOverlap());
    }

    @Test
    void freshHoldoutUsesNoPriorLineupsWhenFeasible() {
        assertEquals(0, prepared.priorLineupOverlap());
    }

    @Test
    void scheduleIsFrozenBeforeGameplayExecution() {
        assertTrue(auditSource.indexOf("writeFrozenSchedule(prepared)")
                < auditSource.indexOf("for (CompositionAuditOnlySemanticsRuntime.ScheduleCase row"));
    }

    @Test
    void scheduleSelectionDoesNotUseGameplayOutcome() {
        assertFalse(auditSource.substring(
                        auditSource.indexOf("static Prepared prepare"),
                        auditSource.indexOf("static MatchSimulator candidateSimulator"))
                .contains("winnerSide"));
    }

    @Test
    void everyUnorderedPairHasBothOrientations() {
        assertEquals(0, CompositionAuditOnlySemanticsRuntime.missingReverse(prepared.schedule()));
    }

    @Test
    void crossTeamChampionOverlapIsZero() {
        assertTrue(prepared.edges().stream().noneMatch(edge ->
                CompositionKeySpecificFreshHoldoutGameplayAudit.championOverlap(
                        edge.left(), edge.right())));
    }
}
