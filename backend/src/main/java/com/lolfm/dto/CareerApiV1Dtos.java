package com.lolfm.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Additive immutable wire contract for /api/v1/careers. */
public final class CareerApiV1Dtos {
    public static final String CREATE_REQUEST_SCHEMA = "CAREER_CREATE_REQUEST_V1";
    public static final String CREATE_RESPONSE_SCHEMA = "CAREER_CREATE_RESPONSE_V1";
    public static final String LIST_SCHEMA = "CAREER_LIST_V1";
    public static final String VIEW_SCHEMA = "CAREER_VIEW_V1";
    public static final String ERROR_SCHEMA = "CAREER_API_ERROR_V1";

    private CareerApiV1Dtos() {}

    public record CreateRequest(
            String schemaVersion,
            String saveName,
            String managerName,
            String managedTeamCode,
            String clientCommandId
    ) {}

    public record CreateResponse(
            String schemaVersion,
            boolean replayed,
            CareerView career
    ) {}

    public record ListResponse(
            String schemaVersion,
            List<CareerSummary> careers,
            int currentCount,
            int maximumCount,
            int remainingCount
    ) {
        public ListResponse { careers = List.copyOf(careers); }
    }

    public record CareerSummary(
            String careerId,
            String saveName,
            String managerName,
            String managedTeamCode,
            LocalDate currentDate,
            String leagueId,
            String seasonId,
            String lifecycleStatus,
            String resumeKind,
            OffsetDateTime updatedAt
    ) {}

    public record CareerView(
            String schemaVersion,
            String careerId,
            String saveName,
            String managerName,
            String managedTeamCode,
            LocalDate startDate,
            LocalDate currentDate,
            String lifecycleStatus,
            long revision,
            String leagueId,
            String seasonId,
            String rootSeedAlgorithmId,
            String rootSeed,
            String leagueFrozenSnapshotIdentity,
            String leagueProductDecisionIdentity,
            String referenceCatalogVersion,
            String referenceCatalogHash,
            String bindingSchemaVersion,
            String bindingHash,
            ResumeProjection resume,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record ResumeProjection(
            String kind,
            String leagueId,
            String seasonId,
            String fixtureId,
            String seriesId,
            String seasonLifecycleStatus,
            int currentRound,
            long lifecycleRevision,
            long standingsRevision,
            List<String> allowedCommands
    ) {
        public ResumeProjection { allowedCommands = List.copyOf(allowedCommands); }
    }

    public record ErrorResponse(
            String schemaVersion,
            String code,
            String field,
            String message
    ) {}
}
