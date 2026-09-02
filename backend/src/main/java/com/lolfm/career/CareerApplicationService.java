package com.lolfm.career;

import com.lolfm.dto.CareerApiV1Dtos;
import com.lolfm.reference.TeamPlayerInformationCatalog;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Career save/load coordinator. League remains the authority for all Season state. */
@Service
public final class CareerApplicationService {
    private final CareerRelationalStore careers;
    private final SeasonProvisioningPort provisioning;
    private final SeasonReadPort seasons;
    private final TeamPlayerInformationCatalog references;

    public CareerApplicationService(
            CareerRelationalStore careers,
            SeasonProvisioningPort provisioning,
            SeasonReadPort seasons,
            TeamPlayerInformationCatalog references
    ) {
        this.careers = Objects.requireNonNull(careers, "careers");
        this.provisioning = Objects.requireNonNull(provisioning, "provisioning");
        this.seasons = Objects.requireNonNull(seasons, "seasons");
        this.references = Objects.requireNonNull(references, "references");
    }

    public CreateResult create(CareerApiV1Dtos.CreateRequest request) {
        Objects.requireNonNull(request, "request");
        String saveName = display(request.saveName(), "saveName");
        String managerName = display(request.managerName(), "managerName");
        String teamCode = request.managedTeamCode();
        if (teamCode == null || references.findTeam(teamCode).isEmpty()) {
            throw CareerException.teamNotFound();
        }
        String commandId;
        try {
            commandId = CareerIdentity.canonicalCommandId(request.clientCommandId());
        } catch (IllegalArgumentException invalid) {
            throw CareerException.invalid("clientCommandId",
                    "clientCommandId는 UUID 형식이어야 합니다.");
        }
        String careerId = CareerIdentity.careerId(commandId);
        String leagueId = CareerIdentity.leagueId(careerId);
        String seasonId = CareerIdentity.seasonId(careerId, leagueId);
        long rootSeed = CareerIdentity.rootSeed(careerId);
        LocalDate startDate = referenceSnapshotDate();
        String referenceVersion = references.provenance().catalogVersion();
        String referenceHash = references.provenance().catalogHash();
        String payloadHash = CareerIdentity.createPayloadHash(
                CareerApiV1Dtos.CREATE_REQUEST_SCHEMA, saveName, managerName, teamCode);

        try {
            CareerRelationalStore.CreateResult stored = careers.createOrReplay(
                    commandId, payloadHash, () -> {
                        ProvisionedSeason provisioned = provisioning.provision(
                                leagueId, seasonId, teamCode, rootSeed);
                        requireProvisioned(provisioned, leagueId, seasonId, teamCode, rootSeed);
                        String bindingHash = CareerIdentity.bindingHash(careerId, teamCode,
                                startDate, startDate, leagueId, seasonId, rootSeed,
                                provisioned.frozenSnapshotIdentity(),
                                provisioned.productDecisionIdentity(), referenceVersion,
                                referenceHash);
                        return new CareerRelationalStore.NewCareer(careerId, saveName,
                                managerName, teamCode, startDate, startDate, leagueId,
                                seasonId, rootSeed, CareerIdentity.SEED_ALGORITHM,
                                provisioned.frozenSnapshotIdentity(),
                                provisioned.productDecisionIdentity(), referenceVersion,
                                referenceHash, CareerIdentity.BINDING_SCHEMA, bindingHash,
                                CareerIdentity.CAREER_SCHEMA, "ACTIVE", 0);
                    });
            return new CreateResult(stored.replayed(), loadView(stored.career()));
        } catch (CareerRelationalStore.CommandConflict conflict) {
            throw CareerException.commandConflict();
        } catch (CareerRelationalStore.CapacityReached full) {
            throw CareerException.capacityReached();
        } catch (CareerRelationalStore.CommandReceiptIntegrityFailure corrupted) {
            throw CareerException.commandReceiptIntegrity();
        } catch (CareerException known) {
            throw known;
        } catch (DataIntegrityViolationException | IllegalArgumentException
                | IllegalStateException failure) {
            throw CareerException.resourceIntegrity();
        }
    }

    public CareerListState list() {
        List<CareerRelationalStore.CareerRow> rows;
        try {
            rows = careers.list();
        } catch (CareerRelationalStore.CareerListIntegrityFailure corrupted) {
            throw CareerException.resourceIntegrity();
        }
        rows.forEach(this::validateCareerIdentity);
        List<SeasonReference> references = rows.stream()
                .map(row -> new SeasonReference(row.leagueId(), row.seasonId())).toList();
        Map<SeasonReference, LinkedSeason> linked;
        try {
            linked = seasons.loadAll(references);
        } catch (RuntimeException failure) {
            throw CareerException.linkedSeasonIntegrity(failure);
        }
        List<CareerViewState> views = rows.stream().map(row -> {
            LinkedSeason season = linked.get(new SeasonReference(
                    row.leagueId(), row.seasonId()));
            if (season == null) throw CareerException.linkedSeasonIntegrity();
            return linkedView(row, season);
        }).toList();
        int current = views.size();
        int maximum = careers.maximumCareers();
        return new CareerListState(views, current, maximum, maximum - current);
    }

    public CareerViewState get(String careerId) {
        try {
            CareerIdentity.requireCareerId(careerId);
        } catch (IllegalArgumentException invalid) {
            throw CareerException.notFound();
        }
        return careers.find(careerId).map(this::loadView)
                .orElseThrow(CareerException::notFound);
    }

    private CareerViewState loadView(CareerRelationalStore.CareerRow row) {
        validateCareerIdentity(row);
        LinkedSeason linked;
        try {
            linked = seasons.load(row.leagueId(), row.seasonId());
        } catch (RuntimeException failure) {
            throw CareerException.linkedSeasonIntegrity(failure);
        }
        return linkedView(row, linked);
    }

    private CareerViewState linkedView(
            CareerRelationalStore.CareerRow row,
            LinkedSeason linked
    ) {
        if (!linked.leagueId().equals(row.leagueId())
                || !linked.seasonId().equals(row.seasonId())
                || !"HYBRID_MANAGER".equals(linked.seasonMode())
                || !linked.managedTeamCode().equals(row.managedTeamCode())
                || linked.rootSeed() != row.rootSeed()
                || !linked.frozenSnapshotIdentity().equals(row.frozenSnapshotHash())
                || !linked.productDecisionIdentity().equals(row.productDecisionHash())) {
            throw CareerException.linkedSeasonIntegrity();
        }
        return new CareerViewState(row, linked);
    }

    private void validateCareerIdentity(CareerRelationalStore.CareerRow row) {
        try {
            CareerIdentity.requireCareerId(row.careerId());
            String leagueId = CareerIdentity.leagueId(row.careerId());
            String seasonId = CareerIdentity.seasonId(row.careerId(), leagueId);
            long rootSeed = CareerIdentity.rootSeed(row.careerId());
            String binding = CareerIdentity.bindingHash(row.careerId(),
                    row.managedTeamCode(), row.startDate(), row.currentDate(),
                    row.leagueId(), row.seasonId(), row.rootSeed(),
                    row.frozenSnapshotHash(), row.productDecisionHash(),
                    row.referenceCatalogVersion(), row.referenceCatalogHash());
            if (!CareerIdentity.CAREER_SCHEMA.equals(row.careerSchema())
                    || !CareerIdentity.BINDING_SCHEMA.equals(row.bindingSchema())
                    || !CareerIdentity.SEED_ALGORITHM.equals(row.seedAlgorithmId())
                    || !"ACTIVE".equals(row.lifecycleStatus()) || row.revision() != 0
                    || !row.startDate().equals(row.currentDate())
                    || !leagueId.equals(row.leagueId())
                    || !seasonId.equals(row.seasonId())
                    || rootSeed != row.rootSeed()
                    || !binding.equals(row.bindingHash())) {
                throw new IllegalStateException("Career binding mismatch");
            }
        } catch (RuntimeException invalid) {
            throw CareerException.linkedSeasonIntegrity();
        }
        if (!references.provenance().catalogVersion().equals(
                row.referenceCatalogVersion())
                || !references.provenance().catalogHash().equals(row.referenceCatalogHash())
                || references.findTeam(row.managedTeamCode()).isEmpty()) {
            throw CareerException.resourceIntegrity();
        }
    }

    private LocalDate referenceSnapshotDate() {
        try {
            String value = references.provenance().resources().stream()
                    .filter(resource -> "PLAYER_CAREER".equals(resource.role()))
                    .map(TeamPlayerInformationCatalog.ResourceProvenance::snapshotAt)
                    .filter(Objects::nonNull).findFirst().orElseThrow();
            return LocalDate.parse(value);
        } catch (RuntimeException invalid) {
            throw CareerException.resourceIntegrity();
        }
    }

    private static String display(String value, String field) {
        try {
            return CareerIdentity.normalizeDisplayName(value, field);
        } catch (IllegalArgumentException invalid) {
            throw CareerException.invalid(field,
                    field + "은 1~80자의 제어문자 없는 이름이어야 합니다.");
        }
    }

    private static void requireProvisioned(
            ProvisionedSeason provisioned,
            String leagueId,
            String seasonId,
            String teamCode,
            long rootSeed
    ) {
        if (provisioned == null || !leagueId.equals(provisioned.leagueId())
                || !seasonId.equals(provisioned.seasonId())
                || !teamCode.equals(provisioned.managedTeamCode())
                || rootSeed != provisioned.rootSeed()
                || !"READY".equals(provisioned.lifecycleStatus())) {
            throw CareerException.resourceIntegrity();
        }
        CareerIdentity.requireSha256(provisioned.frozenSnapshotIdentity(),
                "frozenSnapshotIdentity");
        CareerIdentity.requireSha256(provisioned.productDecisionIdentity(),
                "productDecisionIdentity");
    }

    public interface SeasonProvisioningPort {
        ProvisionedSeason provision(
                String leagueId,
                String seasonId,
                String managedTeamCode,
                long rootSeed
        );
    }

    public interface SeasonReadPort {
        LinkedSeason load(String leagueId, String seasonId);

        Map<SeasonReference, LinkedSeason> loadAll(List<SeasonReference> references);
    }

    public record SeasonReference(String leagueId, String seasonId) {}

    public record ProvisionedSeason(
            String leagueId,
            String seasonId,
            String managedTeamCode,
            long rootSeed,
            String lifecycleStatus,
            String frozenSnapshotIdentity,
            String productDecisionIdentity
    ) {}

    public record LinkedSeason(
            String leagueId,
            String seasonId,
            String seasonMode,
            String managedTeamCode,
            long rootSeed,
            String frozenSnapshotIdentity,
            String productDecisionIdentity,
            ResumeState resume
    ) {}

    public record ResumeState(
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
        public ResumeState { allowedCommands = List.copyOf(allowedCommands); }
    }

    public record CareerViewState(
            CareerRelationalStore.CareerRow career,
            LinkedSeason linkedSeason
    ) {}

    public record CreateResult(boolean replayed, CareerViewState career) {}

    public record CareerListState(
            List<CareerViewState> careers,
            int currentCount,
            int maximumCount,
            int remainingCount
    ) {
        public CareerListState { careers = List.copyOf(careers); }
    }
}
