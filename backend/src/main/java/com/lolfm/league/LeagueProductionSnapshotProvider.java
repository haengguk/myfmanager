package com.lolfm.league;

import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.application.SimulationProvenanceService;
import com.lolfm.application.SimulationResourceProvenance;
import com.lolfm.application.VersionedResourceIdentity;
import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.player.LckTeamAssembler;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** Builds the exact frozen League snapshot from current authored production objects. */
@Component
public final class LeagueProductionSnapshotProvider
        implements LeagueFrozenProductionIdentityProvider {
    private static final Set<String> PLAYER_RESOURCE_ROLES = Set.of(
            "PLAYER_IDENTITY", "PLAYER_RATINGS", "PLAYER_PROFICIENCY");
    private static final Set<String> MATCHUP_COMPOSITION_RESOURCE_ROLES = Set.of(
            "CHAMPION_MATCHUP", "CHAMPION_COMPOSITION");

    private final LckTeamAssembler teams;
    private final SimulationProvenanceService provenance;

    public LeagueProductionSnapshotProvider(
            LckTeamAssembler teams,
            SimulationProvenanceService provenance
    ) {
        this.teams = Objects.requireNonNull(teams, "teams");
        this.provenance = Objects.requireNonNull(provenance, "provenance");
    }

    /** The value Season creation must freeze before any fixture can run. */
    @Override
    public LeagueSeasonFrozenSnapshot currentSnapshot(Set<String> expectedTeamCodes) {
        Objects.requireNonNull(expectedTeamCodes, "expectedTeamCodes");
        if (!Set.copyOf(teams.teamCodes()).equals(Set.copyOf(expectedTeamCodes))) {
            throw new IllegalArgumentException("LEAGUE_PRODUCTION_TEAM_SET_MISMATCH");
        }
        TreeMap<String, String> teamIdentities = new TreeMap<>();
        expectedTeamCodes.stream().sorted().forEach(teamCode -> teamIdentities.put(
                teamCode, teamSnapshotIdentity(teamCode, teams.assemble(teamCode))));
        SimulationResourceProvenance resources = provenance.resourceProvenance();
        return new LeagueSeasonFrozenSnapshot(
                teamIdentities,
                resourceGroupIdentity("AI_LEAGUE_PLAYER_RESOURCES_V1", resources.resources(),
                        PLAYER_RESOURCE_ROLES, false),
                resourceGroupIdentity("AI_LEAGUE_CHAMPION_DRAFT_RESOURCES_V1",
                        resources.resources(), MATCHUP_COMPOSITION_RESOURCE_ROLES, true),
                resourceGroupIdentity("AI_LEAGUE_MATCHUP_COMPOSITION_RESOURCES_V1",
                        resources.resources(), MATCHUP_COMPOSITION_RESOURCE_ROLES, false),
                productionRuntimeIdentity(resources));
    }

    @Override
    public String currentResourceProvenanceHash() {
        return provenance.resourceProvenance().resourceProvenanceHash();
    }

    private static String teamSnapshotIdentity(String teamCode, Team team) {
        StringBuilder canonical = new StringBuilder(
                "teamSnapshotSchema=AI_LEAGUE_TEAM_ROSTER_PLAYER_SNAPSHOT_V1\n")
                .append("teamCode=").append(teamCode).append('\n');
        for (Position position : Position.values()) {
            Player player = team.getPlayers().stream()
                    .filter(value -> value.getPosition() == position)
                    .findFirst().orElseThrow();
            canonical.append("player=").append(position).append('|')
                    .append(player.requirePlayerId().value()).append('\n');
            PlayerSkill.orderedForPosition(position).forEach(skill -> canonical
                    .append("rating=").append(position).append('|').append(skill)
                    .append('|').append(player.getRatings().get(skill)).append('\n'));
            player.getChampionProficiencies().asMap().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            java.util.Comparator.comparing(key -> key.stableId())))
                    .forEach(entry -> canonical.append("proficiency=")
                            .append(position).append('|')
                            .append(entry.getKey().stableId()).append('|')
                            .append(entry.getValue()).append('\n'));
        }
        return LeagueIdentity.sha256(canonical.toString());
    }

    private String productionRuntimeIdentity(SimulationResourceProvenance resources) {
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();
        String canonical = "productionRuntimeSchema=AI_LEAGUE_PRODUCTION_RUNTIME_V1\n"
                + "policySchema=" + policy.schemaVersion() + '\n'
                + "policyId=" + policy.policyId() + '\n'
                + "policyHash=" + policy.policyHash() + '\n'
                + "runtimeProfileId=" + policy.retainedRuntimeProfileId() + '\n'
                + "configurationHash=" + policy.configurationHash() + '\n'
                + "activeGameplayRulesVersion=" + policy.activeGameplayRulesVersion() + '\n'
                + "engineImplementationVersion=" + policy.engineImplementationVersion() + '\n'
                + "draftRuleSetIdentity=" + provenance.draftRuleSetIdentity() + '\n'
                + "draftRuleSetHash=" + provenance.draftRuleSetHash() + '\n'
                + "draftScoringPolicyHash=" + provenance.draftScoringPolicyHash() + '\n'
                + "draftSelectionPolicyId=" + MatchEngineV1Policy.DRAFT_SELECTION_POLICY_ID + '\n'
                + "draftSelectionPolicyHash="
                + MatchEngineV1Policy.DRAFT_SELECTION_POLICY_SHA256 + '\n'
                + "resourceProvenanceHash=" + resources.resourceProvenanceHash() + '\n';
        return LeagueIdentity.sha256(canonical);
    }

    private static String resourceGroupIdentity(
            String schema,
            List<VersionedResourceIdentity> resources,
            Set<String> selectedRoles,
            boolean complement
    ) {
        StringBuilder canonical = new StringBuilder("resourceGroupSchema=")
                .append(schema).append('\n');
        resources.stream().filter(value -> complement
                        ? !selectedRoles.contains(value.role())
                        && !PLAYER_RESOURCE_ROLES.contains(value.role())
                        : selectedRoles.contains(value.role()))
                .sorted(java.util.Comparator.comparing(VersionedResourceIdentity::role))
                .forEach(value -> canonical.append("resource=")
                        .append(value.role()).append('|')
                        .append(value.classpathResource()).append('|')
                        .append(value.version()).append('|')
                        .append(value.sha256()).append('\n'));
        return LeagueIdentity.sha256(canonical.toString());
    }
}
