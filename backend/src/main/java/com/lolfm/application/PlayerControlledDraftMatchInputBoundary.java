package com.lolfm.application;

import com.lolfm.domain.Team;
import com.lolfm.draft.DraftSelectionContext;
import com.lolfm.draft.DraftTeamContext;
import com.lolfm.draft.PlayerControlledDraftEngine;
import com.lolfm.draft.PlayerControlledDraftResult;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.TeamSide;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** The only production boundary from a raw mixed Draft result to Match Engine input. */
@Component
public final class PlayerControlledDraftMatchInputBoundary {
    private final LckTeamAssembler teams;
    private final PlayerControlledDraftEngine drafts;
    private final MatchEngineV1InputFactory inputs;

    public PlayerControlledDraftMatchInputBoundary(
            LckTeamAssembler teams,
            PlayerControlledDraftEngine drafts,
            MatchEngineV1InputFactory inputs
    ) {
        this.teams = Objects.requireNonNull(teams, "teams");
        this.drafts = Objects.requireNonNull(drafts, "drafts");
        this.inputs = Objects.requireNonNull(inputs, "inputs");
    }

    public MatchEngineV1Input validateAndCreateInput(
            String blueTeamCode,
            String redTeamCode,
            long matchSeed,
            PlayerControlledDraftResult result
    ) {
        blueTeamCode = canonicalTeamCode(blueTeamCode, "blueTeamCode");
        redTeamCode = canonicalTeamCode(redTeamCode, "redTeamCode");
        if (blueTeamCode.equals(redTeamCode)) {
            throw new IllegalArgumentException("PLAYER_DRAFT_TEAM_IDENTITY_COLLISION");
        }
        Objects.requireNonNull(result, "result");
        Team blueTeam = teams.assemble(blueTeamCode);
        Team redTeam = teams.assemble(redTeamCode);
        DraftTeamContext blueContext = DraftTeamContext.from(blueTeam);
        DraftTeamContext redContext = DraftTeamContext.from(redTeam);
        if (blueTeam.getPlayers().size() != 5 || redTeam.getPlayers().size() != 5
                || !blueContext.hasStablePlayerIdentities()
                || !redContext.hasStablePlayerIdentities()) {
            throw new IllegalArgumentException("PLAYER_DRAFT_REAL_ROSTER_PREFLIGHT_FAILED");
        }
        DraftSelectionContext selectionContext = RealDraftSelectionContextFactory.create(
                matchSeed, blueTeamCode, blueTeam, redTeamCode, redTeam, 1, Set.of());
        drafts.validateCompleted(result, blueContext, redContext, selectionContext);
        return inputs.fromValidatedPlayerControlledDraft(new ValidatedDraft(
                blueTeamCode, blueTeam, redTeamCode, redTeam, matchSeed, result));
    }

    PlayerDraftCompletionBinding bindStandalone(
            String sessionId,
            long completionRevision,
            String blueTeamCode,
            String redTeamCode,
            TeamSide controlledSide,
            long matchSeed,
            PlayerControlledDraftResult result
    ) {
        MatchEngineV1Input input = projectStandalone(
                blueTeamCode, redTeamCode, matchSeed, result);
        return completionBinding(PlayerDraftCompletionBinding.STANDALONE, sessionId, 1,
                completionRevision, controlledSide, input, result);
    }

    MatchEngineV1Input validateAndCreateTrustedStandaloneInput(
            PlayerDraftCompletionBinding binding,
            String sessionId,
            long completionRevision,
            String blueTeamCode,
            String redTeamCode,
            TeamSide controlledSide,
            long matchSeed,
            PlayerControlledDraftResult result
    ) {
        requireBinding(binding, PlayerDraftCompletionBinding.STANDALONE, sessionId, 1,
                completionRevision, blueTeamCode, redTeamCode, controlledSide, matchSeed,
                1, Set.of(), SimulationProvenanceService.seriesHistoryHash(0, Set.of()),
                result);
        MatchEngineV1Input input = projectStandalone(
                blueTeamCode, redTeamCode, matchSeed, result);
        requireProjectedIdentity(binding, input);
        return input;
    }

    MatchEngineV1Input validateAndCreateSeriesInput(
            SeriesPlayerDraftBinding binding,
            PlayerControlledDraftResult result
    ) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(result, "result");
        String blueTeamCode = canonicalTeamCode(binding.blueTeamCode(), "blueTeamCode");
        String redTeamCode = canonicalTeamCode(binding.redTeamCode(), "redTeamCode");
        if (blueTeamCode.equals(redTeamCode)) {
            throw new IllegalArgumentException("PLAYER_DRAFT_TEAM_IDENTITY_COLLISION");
        }
        Team blueTeam = teams.assemble(blueTeamCode);
        Team redTeam = teams.assemble(redTeamCode);
        DraftTeamContext blueContext = DraftTeamContext.from(blueTeam);
        DraftTeamContext redContext = DraftTeamContext.from(redTeam);
        DraftSelectionContext selectionContext = RealDraftSelectionContextFactory.create(
                binding.matchSeed(), blueTeamCode, blueTeam, redTeamCode, redTeam,
                binding.gameNumber(), binding.hardFearlessExclusions());
        if (!selectionContext.seriesHistoryBeforeHash().equals(binding.historyBeforeHash())
                || result.controlledSide() != binding.controlledSide()) {
            throw new IllegalArgumentException("SERIES_DRAFT_BINDING_MISMATCH");
        }
        drafts.validateCompletedSeries(result, blueContext, redContext, selectionContext,
                binding.hardFearlessExclusions());
        return inputs.fromValidatedSeriesPlayerControlledDraft(new ValidatedSeriesDraft(
                binding, blueTeam, redTeam, result));
    }

    PlayerDraftCompletionBinding bindSeries(
            SeriesPlayerDraftBinding parent,
            String childId,
            int generation,
            long completionRevision,
            PlayerControlledDraftResult result
    ) {
        MatchEngineV1Input input = projectSeries(parent, result);
        return completionBinding(PlayerDraftCompletionBinding.SERIES, childId, generation,
                completionRevision, parent.controlledSide(), input, result);
    }

    MatchEngineV1Input validateAndCreateTrustedSeriesInput(
            SeriesPlayerDraftBinding parent,
            String childId,
            int generation,
            long completionRevision,
            PlayerDraftCompletionBinding binding,
            PlayerControlledDraftResult result
    ) {
        requireBinding(binding, PlayerDraftCompletionBinding.SERIES, childId, generation,
                completionRevision, parent.blueTeamCode(), parent.redTeamCode(),
                parent.controlledSide(), parent.matchSeed(), parent.gameNumber(),
                parent.hardFearlessExclusions(), parent.historyBeforeHash(), result);
        MatchEngineV1Input input = projectSeries(parent, result);
        requireProjectedIdentity(binding, input);
        return input;
    }

    private MatchEngineV1Input projectStandalone(
            String blueTeamCode, String redTeamCode, long matchSeed,
            PlayerControlledDraftResult result
    ) {
        blueTeamCode = canonicalTeamCode(blueTeamCode, "blueTeamCode");
        redTeamCode = canonicalTeamCode(redTeamCode, "redTeamCode");
        if (blueTeamCode.equals(redTeamCode)) {
            throw new IllegalArgumentException("PLAYER_DRAFT_TEAM_IDENTITY_COLLISION");
        }
        Objects.requireNonNull(result, "result");
        Team blueTeam = teams.assemble(blueTeamCode);
        Team redTeam = teams.assemble(redTeamCode);
        requireStableRoster(blueTeam, redTeam);
        return inputs.fromValidatedPlayerControlledDraft(new ValidatedDraft(
                blueTeamCode, blueTeam, redTeamCode, redTeam, matchSeed, result));
    }

    private MatchEngineV1Input projectSeries(
            SeriesPlayerDraftBinding binding, PlayerControlledDraftResult result
    ) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(result, "result");
        String blueTeamCode = canonicalTeamCode(binding.blueTeamCode(), "blueTeamCode");
        String redTeamCode = canonicalTeamCode(binding.redTeamCode(), "redTeamCode");
        if (blueTeamCode.equals(redTeamCode)) {
            throw new IllegalArgumentException("PLAYER_DRAFT_TEAM_IDENTITY_COLLISION");
        }
        Team blueTeam = teams.assemble(blueTeamCode);
        Team redTeam = teams.assemble(redTeamCode);
        requireStableRoster(blueTeam, redTeam);
        if (!SimulationProvenanceService.seriesHistoryHash(
                binding.gameNumber() - 1, binding.hardFearlessExclusions())
                .equals(binding.historyBeforeHash())
                || result.controlledSide() != binding.controlledSide()) {
            throw new IllegalArgumentException("SERIES_DRAFT_BINDING_MISMATCH");
        }
        return inputs.fromValidatedSeriesPlayerControlledDraft(new ValidatedSeriesDraft(
                binding, blueTeam, redTeam, result));
    }

    private PlayerDraftCompletionBinding completionBinding(
            String scope, String ownerId, int generation, long completionRevision,
            TeamSide controlledSide, MatchEngineV1Input input,
            PlayerControlledDraftResult result
    ) {
        if (result.controlledSide() != controlledSide
                || !drafts.activeDraftRuleIdentity().equals(result.ruleSet().identity())
                || !drafts.activeDraftMetaVersion().equals(result.draftMetaVersion())
                || !drafts.activeRequiredLegalRoleKeyHash().equals(
                        result.requiredLegalRoleKeyHash())
                || !drafts.activeActualLegalRoleKeyHash().equals(
                        result.actualLegalRoleKeyHash())) {
            throw new IllegalArgumentException("PLAYER_DRAFT_COMPLETION_BINDING_MISMATCH");
        }
        return new PlayerDraftCompletionBinding(scope, ownerId, generation,
                completionRevision, input.blueTeam().teamIdentity(),
                input.redTeam().teamIdentity(), controlledSide, input.matchSeed(),
                input.finalDraft().seriesGameNumber(),
                Set.copyOf(input.finalDraft().hardFearlessExclusions()),
                input.seriesHistoryBeforeHash(), result, result.draftIdentity(),
                result.controlEvidence().controlEvidenceHash(), result.ruleSet().identity(),
                result.draftMetaVersion(), result.requiredLegalRoleKeyHash(),
                result.actualLegalRoleKeyHash(), input.inputHash(), input.rosterIdentityHash(),
                input.finalDraft().draftDecisionHash(),
                input.finalDraft().finalAssignmentHash(),
                input.finalDraft().finalDraftHash(), input.productionPolicy());
    }

    private void requireBinding(
            PlayerDraftCompletionBinding binding, String scope, String ownerId,
            int generation, long completionRevision, String blueTeamCode,
            String redTeamCode, TeamSide controlledSide, long matchSeed,
            int gameNumber, Set<com.lolfm.champion.ChampionId> exclusions,
            String historyHash, PlayerControlledDraftResult result
    ) {
        Objects.requireNonNull(binding, "PLAYER_DRAFT_COMPLETION_BINDING_REQUIRED");
        boolean valid = binding.scope().equals(scope)
                && binding.ownerId().equals(ownerId)
                && binding.generation() == generation
                && binding.completionRevision() == completionRevision
                && binding.blueTeamCode().equals(canonicalTeamCode(
                        blueTeamCode, "blueTeamCode"))
                && binding.redTeamCode().equals(canonicalTeamCode(
                        redTeamCode, "redTeamCode"))
                && binding.controlledSide() == controlledSide
                && binding.matchSeed() == matchSeed
                && binding.seriesGameNumber() == gameNumber
                && binding.hardFearlessExclusions().equals(Set.copyOf(exclusions))
                && binding.historyBeforeHash().equals(historyHash)
                // A file-backed checkpoint reconstructs nested Draft evidence with new
                // object identities. Durable authority is therefore the sealed hashes
                // below plus requireProjectedIdentity(), not Java object equality.
                && binding.draftIdentity().equals(result.draftIdentity())
                && binding.controlEvidenceHash().equals(
                        result.controlEvidence().controlEvidenceHash())
                && binding.draftRuleIdentity().equals(result.ruleSet().identity())
                && drafts.activeDraftRuleIdentity().equals(binding.draftRuleIdentity())
                && binding.draftMetaVersion().equals(result.draftMetaVersion())
                && binding.requiredLegalRoleKeyHash().equals(
                        result.requiredLegalRoleKeyHash())
                && binding.actualLegalRoleKeyHash().equals(
                        result.actualLegalRoleKeyHash())
                && drafts.activeDraftMetaVersion().equals(binding.draftMetaVersion())
                && drafts.activeRequiredLegalRoleKeyHash().equals(
                        binding.requiredLegalRoleKeyHash())
                && drafts.activeActualLegalRoleKeyHash().equals(
                        binding.actualLegalRoleKeyHash())
                && binding.productionPolicy().equals(MatchEngineV1Policy.requirement());
        if (!valid) {
            throw new IllegalArgumentException("PLAYER_DRAFT_COMPLETION_BINDING_MISMATCH");
        }
    }

    private static void requireProjectedIdentity(
            PlayerDraftCompletionBinding binding, MatchEngineV1Input input
    ) {
        boolean valid = binding.inputHash().equals(input.inputHash())
                && binding.rosterIdentityHash().equals(input.rosterIdentityHash())
                && binding.historyBeforeHash().equals(input.seriesHistoryBeforeHash())
                && binding.draftDecisionHash().equals(
                        input.finalDraft().draftDecisionHash())
                && binding.finalAssignmentHash().equals(
                        input.finalDraft().finalAssignmentHash())
                && binding.finalDraftHash().equals(input.finalDraft().finalDraftHash())
                && binding.productionPolicy().equals(input.productionPolicy());
        if (!valid) {
            throw new IllegalArgumentException("PLAYER_DRAFT_COMPLETION_INPUT_DRIFT");
        }
    }

    private static void requireStableRoster(Team blueTeam, Team redTeam) {
        DraftTeamContext blue = DraftTeamContext.from(blueTeam);
        DraftTeamContext red = DraftTeamContext.from(redTeam);
        if (blueTeam.getPlayers().size() != 5 || redTeam.getPlayers().size() != 5
                || !blue.hasStablePlayerIdentities() || !red.hasStablePlayerIdentities()) {
            throw new IllegalArgumentException("PLAYER_DRAFT_REAL_ROSTER_PREFLIGHT_FAILED");
        }
    }

    /** Only this enclosing validator can construct the token accepted by the unchecked projector. */
    static final class ValidatedDraft {
        private final String blueTeamCode;
        private final Team blueTeam;
        private final String redTeamCode;
        private final Team redTeam;
        private final long matchSeed;
        private final PlayerControlledDraftResult result;

        private ValidatedDraft(
                String blueTeamCode, Team blueTeam,
                String redTeamCode, Team redTeam,
                long matchSeed, PlayerControlledDraftResult result
        ) {
            this.blueTeamCode = blueTeamCode;
            this.blueTeam = blueTeam;
            this.redTeamCode = redTeamCode;
            this.redTeam = redTeam;
            this.matchSeed = matchSeed;
            this.result = result;
        }

        String blueTeamCode() {
            return blueTeamCode;
        }

        Team blueTeam() {
            return blueTeam;
        }

        String redTeamCode() {
            return redTeamCode;
        }

        Team redTeam() {
            return redTeam;
        }

        long matchSeed() {
            return matchSeed;
        }

        PlayerControlledDraftResult result() {
            return result;
        }
    }

    static final class ValidatedSeriesDraft {
        private final SeriesPlayerDraftBinding binding;
        private final Team blueTeam;
        private final Team redTeam;
        private final PlayerControlledDraftResult result;

        private ValidatedSeriesDraft(
                SeriesPlayerDraftBinding binding,
                Team blueTeam,
                Team redTeam,
                PlayerControlledDraftResult result
        ) {
            this.binding = binding;
            this.blueTeam = blueTeam;
            this.redTeam = redTeam;
            this.result = result;
        }

        SeriesPlayerDraftBinding binding() { return binding; }
        Team blueTeam() { return blueTeam; }
        Team redTeam() { return redTeam; }
        PlayerControlledDraftResult result() { return result; }
    }

    record SeriesPlayerDraftBinding(
            String seriesId,
            String gameId,
            int gameNumber,
            String blueTeamCode,
            String redTeamCode,
            TeamSide controlledSide,
            long matchSeed,
            Set<com.lolfm.champion.ChampionId> hardFearlessExclusions,
            String historyBeforeHash
    ) {
        SeriesPlayerDraftBinding {
            seriesId = required(seriesId, "seriesId");
            gameId = required(gameId, "gameId");
            if (gameNumber < 1) throw new IllegalArgumentException("gameNumber");
            Objects.requireNonNull(controlledSide, "controlledSide");
            hardFearlessExclusions = Set.copyOf(hardFearlessExclusions);
            if (historyBeforeHash == null || !historyBeforeHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("historyBeforeHash");
            }
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty() || !normalized.equals(value)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }

    private static String canonicalTeamCode(String value, String field) {
        return required(value, field).toUpperCase(Locale.ROOT);
    }
}
