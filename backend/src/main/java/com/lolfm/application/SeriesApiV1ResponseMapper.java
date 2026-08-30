package com.lolfm.application;

import com.lolfm.dto.SeriesApiV1Dtos;
import com.lolfm.player.LckTeamAssembler;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Structured Series projection; no gameplay meaning is reconstructed from display text. */
@Component
public final class SeriesApiV1ResponseMapper {
    private final LckTeamAssembler teams;
    private final PlayerDraftApiV1ResponseMapper playerDrafts;
    private final com.lolfm.draft.PlayerControlledDraftEngine draftEngine;
    private final SeriesLifecycleConfiguration lifecycleConfiguration;

    public SeriesApiV1ResponseMapper(
            LckTeamAssembler teams,
            PlayerDraftApiV1ResponseMapper playerDrafts,
            com.lolfm.draft.PlayerControlledDraftEngine draftEngine,
            SeriesLifecycleConfiguration lifecycleConfiguration
    ) {
        this.teams = Objects.requireNonNull(teams, "teams");
        this.playerDrafts = Objects.requireNonNull(playerDrafts, "playerDrafts");
        this.draftEngine = Objects.requireNonNull(draftEngine, "draftEngine");
        this.lifecycleConfiguration = Objects.requireNonNull(
                lifecycleConfiguration, "lifecycleConfiguration");
    }

    public SeriesApiV1Dtos.SeriesView series(SeriesAggregate aggregate) {
        SeriesGame current = aggregate.currentGame();
        SeriesChildDraft child = current.childDraft();
        SeriesApiV1Dtos.ChildDraftEnvelope childEnvelope = child == null ? null
                : childEnvelope(aggregate, current, child);
        SeriesApiV1Dtos.ReservationView reservation = current.reservation() == null ? null
                : new SeriesApiV1Dtos.ReservationView(
                current.reservation().commandId(), current.reservation().createdAt(),
                current.reservation().leaseExpiresAt());
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();
        return new SeriesApiV1Dtos.SeriesView(
                SeriesApiV1Dtos.VIEW_SCHEMA, aggregate.seriesId(), aggregate.revision(),
                aggregate.status(), aggregate.terminalReason(), aggregate.format(),
                aggregate.format().winsRequired(), List.of(
                new SeriesApiV1Dtos.TeamIdentity(aggregate.teamACode(),
                        teams.assemble(aggregate.teamACode()).getName()),
                new SeriesApiV1Dtos.TeamIdentity(aggregate.teamBCode(),
                        teams.assemble(aggregate.teamBCode()).getName())),
                aggregate.managedTeamCode(), aggregate.managedTeamCode().equals(
                aggregate.teamACode()) ? aggregate.teamBCode() : aggregate.teamACode(),
                aggregate.score(), current.gameNumber(), aggregate.canonicalRootSeed(),
                SeriesIdentity.GAME_SEED_ALGORITHM, Long.toString(current.matchSeed()),
                aggregate.consumedPicks().stream().map(value -> value.value()).sorted().toList(),
                aggregate.historyHash(), aggregate.games().stream().map(this::game).toList(),
                childEnvelope, reservation, allowedCommands(aggregate),
                aggregate.winnerTeamCode(), aggregate.createdAt(), aggregate.lastActivityAt(),
                aggregate.expiresAt(), true, new SeriesApiV1Dtos.ProductionIdentity(
                policy.policyId(), policy.policyHash(), policy.retainedRuntimeProfileId().name(),
                policy.configurationHash(), policy.activeGameplayRulesVersion(),
                policy.engineImplementationVersion(), draftEngine.activeDraftMetaVersion(),
                draftEngine.activeRequiredLegalRoleKeyHash(),
                draftEngine.activeActualLegalRoleKeyHash()));
    }

    public SeriesApiV1Dtos.SeriesGameView game(SeriesGame game) {
        SeriesChildDraft child = game.childDraft();
        SeriesGameReceipt receipt = game.receipt();
        return new SeriesApiV1Dtos.SeriesGameView(
                SeriesApiV1Dtos.GAME_VIEW_SCHEMA, game.gameId(), game.gameNumber(),
                game.status(), game.reason(), game.blueTeamCode(), game.redTeamCode(),
                game.controlledSide(), Long.toString(game.matchSeed()),
                game.historyBefore().stream().map(value -> value.value()).toList(),
                game.historyBeforeHash(), child == null ? null : child.childId(),
                child == null ? null : child.status(), child == null ? null : child.revision(),
                compactResult(game), receipt == null ? null : new SeriesApiV1Dtos.CompactReceipt(
                receipt.schemaVersion(), receipt.inputHash(), receipt.replayProvenanceHash(),
                receipt.resourceProvenanceHash(), receipt.finalDraftHash(),
                receipt.finalAssignmentHash(), receipt.controlEvidenceHash(),
                receipt.simulatorTimelineHash(), receipt.structuredTimelineHash(),
                receipt.outputHash(), receipt.randomDrawCount(), receipt.randomTraceHash()));
    }

    public SeriesApiV1Dtos.ChildDraftEnvelope childEnvelope(
            SeriesAggregate aggregate, SeriesGame game, SeriesChildDraft child
    ) {
        return new SeriesApiV1Dtos.ChildDraftEnvelope(
                SeriesApiV1Dtos.CHILD_ENVELOPE_SCHEMA,
                new SeriesApiV1Dtos.SeriesBinding(
                        aggregate.seriesId(), game.gameId(), game.gameNumber(),
                        game.blueTeamCode(), game.redTeamCode(), aggregate.managedTeamCode(),
                        game.controlledSide(), Long.toString(game.matchSeed()),
                        game.historyBefore().stream().map(value -> value.value()).toList(),
                        game.historyBeforeHash()),
                playerDrafts.session(child.view(game)));
    }

    public com.lolfm.dto.PlayerDraftApiV1Dtos.MatchPayload match(
            MatchEngineV1Output output
    ) {
        return playerDrafts.matchPayload(output);
    }

    private static SeriesApiV1Dtos.CompactResult compactResult(SeriesGame game) {
        if (game.resultSummary() == null) return null;
        var summary = game.resultSummary();
        LinkedHashMap<String, Integer> kills = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> gold = new LinkedHashMap<>();
        summary.teams().forEach(team -> {
            kills.put(team.teamIdentity(), team.kills());
            gold.put(team.teamIdentity(), team.totalGold());
        });
        String winner = summary.winner() == null ? null
                : summary.winner() == com.lolfm.simulator.TeamSide.BLUE
                ? game.blueTeamCode() : game.redTeamCode();
        return new SeriesApiV1Dtos.CompactResult(
                winner, summary.winner(), summary.endReason().name(),
                summary.durationSeconds(), kills, gold);
    }

    private List<String> allowedCommands(SeriesAggregate aggregate) {
        if (!lifecycleConfiguration.canCreateCommandReceipt(
                aggregate.commandReceipts().size())) {
            return List.of("GET");
        }
        if (aggregate.status() == SeriesStatus.ACTIVE) {
            SeriesGame game = aggregate.currentGame();
            if (game.reservation() != null) return List.of("GET", "CANCEL_SERIES");
            return switch (game.status()) {
                case DRAFT_PENDING, DRAFT_CANCELLED, DRAFT_EXPIRED -> List.of(
                        "CREATE_DRAFT_SESSION", "CANCEL_SERIES");
                case SIMULATION_FAILED_RETRYABLE -> List.of("SIMULATE", "CANCEL_SERIES");
                case DRAFT_ACTIVE -> List.of("SUBMIT_DRAFT_ACTION",
                        "CANCEL_DRAFT_SESSION", "CANCEL_SERIES");
                case DRAFT_COMPLETED -> List.of("SIMULATE", "CANCEL_SERIES");
                default -> List.of("GET", "CANCEL_SERIES");
            };
        }
        if (aggregate.status() == SeriesStatus.BLOCKED) return List.of("GET", "CANCEL_SERIES");
        return List.of("GET");
    }
}
