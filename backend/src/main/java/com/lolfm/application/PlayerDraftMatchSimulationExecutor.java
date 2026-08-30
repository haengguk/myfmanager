package com.lolfm.application;

import com.lolfm.controller.PlayerDraftApiV1Exception;
import com.lolfm.simulator.SimulationInstrumentation;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Transient Production V9 execution; only its compact receipt may enter session state. */
@Component
final class PlayerDraftMatchSimulationExecutor {
    private final PlayerControlledDraftMatchInputBoundary inputs;
    private final MatchEngineV1 matches;
    private final MatchEngineV1Canonicalizer canonicalizer;

    PlayerDraftMatchSimulationExecutor(
            PlayerControlledDraftMatchInputBoundary inputs,
            MatchEngineV1 matches,
            MatchEngineV1Canonicalizer canonicalizer
    ) {
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.matches = Objects.requireNonNull(matches, "matches");
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
    }

    Execution execute(PlayerDraftSession session) {
        MatchEngineV1Input input;
        try {
            input = inputs.validateAndCreateTrustedStandaloneInput(
                    session.completionBinding(), session.sessionId(), session.revision(),
                    session.blueTeamCode(), session.redTeamCode(), session.controlledSide(),
                    session.matchSeed(), session.progress().result());
        } catch (IllegalArgumentException error) {
            throw PlayerDraftApiV1Exception.unprocessable(
                    "PLAYER_DRAFT_MATCH_PREFLIGHT_FAILED", null,
                    "드래프트 증거 또는 최종 역할 배치를 검증하지 못했습니다.", error);
        }
        try {
            MatchEngineV1Output output = matches.execute(
                    input, SimulationInstrumentation.enabled());
            validateOutput(session, output);
            return new Execution(output, SimulationReceipt.from(output));
        } catch (PlayerDraftApiV1Exception error) {
            throw error;
        } catch (RuntimeException error) {
            throw PlayerDraftApiV1Exception.internal(error);
        }
    }

    PlayerDraftCompletionBinding bind(
            String sessionId, long completionRevision, String blueTeamCode,
            String redTeamCode, com.lolfm.simulator.TeamSide controlledSide,
            long matchSeed, com.lolfm.draft.PlayerControlledDraftResult result
    ) {
        return inputs.bindStandalone(sessionId, completionRevision, blueTeamCode,
                redTeamCode, controlledSide, matchSeed, result);
    }

    private void validateOutput(PlayerDraftSession session, MatchEngineV1Output output) {
        try {
            SimulationExecutionProvenance execution = Objects.requireNonNull(
                    output, "output").executionProvenance();
            var control = output.finalDraft().controlEvidence();
            boolean valid = output.productionPolicy().equals(
                    MatchEngineV1Policy.authoritative())
                    && output.configurationHash().equals(
                    MatchEngineV1Policy.authoritative().configurationHash())
                    && execution.runtimeProfileId()
                    == MatchEngineV1Policy.authoritative().retainedRuntimeProfileId()
                    && execution.blueTeamCode().equals(session.blueTeamCode())
                    && execution.redTeamCode().equals(session.redTeamCode())
                    && execution.matchSeed() == session.matchSeed()
                    && execution.seriesGameNumber() == 1
                    && output.finalDraft().seriesGameNumber() == 1
                    && output.finalDraft().hardFearlessExclusions().isEmpty()
                    && control != null
                    && execution.draftSelectionPolicyId().equals(control.policyId())
                    && execution.draftSelectionPolicyHash().equals(control.policyHash())
                    && execution.draftSelectionTraceHash().equals(
                    control.controlEvidenceHash())
                    && output.resultSummary().finalDraftHash().equals(
                    output.finalDraft().finalDraftHash())
                    && output.resultSummary().finalAssignmentHash().equals(
                    output.finalDraft().finalAssignmentHash())
                    && output.simulatorTimelineHash().equals(execution.timelineHash())
                    && output.hasValidOutputHash(canonicalizer);
            if (!valid) throw PlayerDraftApiV1Exception.internal(null);
        } catch (PlayerDraftApiV1Exception error) {
            throw error;
        } catch (RuntimeException error) {
            throw PlayerDraftApiV1Exception.internal(error);
        }
    }

    record Execution(MatchEngineV1Output output, SimulationReceipt receipt) {
        Execution {
            Objects.requireNonNull(output, "output");
            Objects.requireNonNull(receipt, "receipt");
        }
    }
}
