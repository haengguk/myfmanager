package com.lolfm.draft;

import com.lolfm.champion.ChampionId;
import com.lolfm.domain.Position;
import com.lolfm.simulator.TeamSide;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Mutable memoization state owned by exactly one {@link DraftEngine#draft} call. */
final class DraftComputationContext {
    private static final Comparator<ChampionId> CHAMPION_ORDER =
            Comparator.comparing(ChampionId::value);

    private final boolean cacheEnabled;
    private final Map<ChampionCombinationKey, List<RoleAssignmentSolver.RoleAssignment>>
            roleAssignments = new HashMap<>();
    private final Map<CandidateRoleKey, Set<Position>> candidatePositions = new HashMap<>();
    private final Map<CandidateRoleKey, Set<Position>> pickedPositions = new HashMap<>();
    private final Map<CompletionKey, Boolean> completion = new HashMap<>();
    private final Map<PoolHealthKey, Double> poolHealth = new HashMap<>();

    private long roleAssignmentRequests;
    private long roleAssignmentHits;
    private long roleAssignmentMisses;
    private long roleAssignmentPhysicalComputations;
    private long rolePositionRequests;
    private long rolePositionHits;
    private long rolePositionMisses;
    private long completionRequests;
    private long completionHits;
    private long completionMisses;
    private long completionPhysicalComputations;
    private long poolHealthRequests;
    private long poolHealthHits;
    private long poolHealthMisses;
    private long poolHealthPhysicalComputations;
    private long plannerCandidatePhysicalComputations;
    private long plannerCandidateLocalReuses;
    private int peakEntries;

    private DraftComputationContext(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    static DraftComputationContext cached() {
        return new DraftComputationContext(true);
    }

    static DraftComputationContext uncached() {
        return new DraftComputationContext(false);
    }

    boolean reuseEnabled() {
        return cacheEnabled;
    }

    List<RoleAssignmentSolver.RoleAssignment> roleAssignments(
            List<ChampionId> champions,
            Supplier<List<RoleAssignmentSolver.RoleAssignment>> computation) {
        roleAssignmentRequests++;
        ChampionCombinationKey key = ChampionCombinationKey.of(champions);
        if (cacheEnabled) {
            List<RoleAssignmentSolver.RoleAssignment> cached = roleAssignments.get(key);
            if (cached != null) {
                roleAssignmentHits++;
                return cached;
            }
        }
        roleAssignmentMisses++;
        roleAssignmentPhysicalComputations++;
        List<RoleAssignmentSolver.RoleAssignment> value = computation.get();
        if (cacheEnabled) {
            roleAssignments.put(key, value);
            updatePeakEntries();
        }
        return value;
    }

    Set<Position> candidatePositions(List<ChampionId> picks, ChampionId candidate,
                                     Supplier<Set<Position>> computation) {
        return positions(candidatePositions, picks, candidate, computation);
    }

    Set<Position> pickedPositions(List<ChampionId> picks, ChampionId champion,
                                  Supplier<Set<Position>> computation) {
        return positions(pickedPositions, picks, champion, computation);
    }

    private Set<Position> positions(Map<CandidateRoleKey, Set<Position>> cache,
                                    List<ChampionId> picks, ChampionId champion,
                                    Supplier<Set<Position>> computation) {
        rolePositionRequests++;
        CandidateRoleKey key = new CandidateRoleKey(
                ChampionCombinationKey.of(picks), champion);
        if (cacheEnabled) {
            Set<Position> cached = cache.get(key);
            if (cached != null) {
                rolePositionHits++;
                return cached;
            }
        }
        rolePositionMisses++;
        Set<Position> value = computation.get();
        if (cacheEnabled) {
            cache.put(key, value);
            updatePeakEntries();
        }
        return value;
    }

    boolean completion(DraftState state, TeamSide side, ChampionId candidate,
                       Position targetPosition, BooleanSupplier computation) {
        completionRequests++;
        CompletionKey key = new CompletionKey(StateKey.of(state), side, candidate,
                targetPosition);
        if (cacheEnabled && completion.containsKey(key)) {
            completionHits++;
            return completion.get(key);
        }
        completionMisses++;
        completionPhysicalComputations++;
        boolean value = computation.getAsBoolean();
        if (cacheEnabled) {
            completion.put(key, value);
            updatePeakEntries();
        }
        return value;
    }

    double poolHealth(DraftState state, TeamSide side, ChampionId candidate,
                      DoubleSupplier computation) {
        poolHealthRequests++;
        PoolHealthKey key = new PoolHealthKey(StateKey.of(state), side, candidate);
        if (cacheEnabled && poolHealth.containsKey(key)) {
            poolHealthHits++;
            return poolHealth.get(key);
        }
        poolHealthMisses++;
        poolHealthPhysicalComputations++;
        double value = computation.getAsDouble();
        if (cacheEnabled) {
            poolHealth.put(key, value);
            updatePeakEntries();
        }
        return value;
    }

    void recordPlannerCandidatePhysicalComputation() {
        plannerCandidatePhysicalComputations++;
    }

    void recordPlannerCandidateLocalReuse() {
        plannerCandidateLocalReuses++;
    }

    Snapshot snapshot() {
        return new Snapshot(cacheEnabled, roleAssignmentRequests, roleAssignmentHits,
                roleAssignmentMisses, roleAssignmentPhysicalComputations,
                roleAssignments.size(), rolePositionRequests, rolePositionHits,
                rolePositionMisses, candidatePositions.size() + pickedPositions.size(),
                completionRequests, completionHits, completionMisses,
                completionPhysicalComputations, completion.size(), poolHealthRequests,
                poolHealthHits, poolHealthMisses, poolHealthPhysicalComputations,
                poolHealth.size(), plannerCandidatePhysicalComputations,
                plannerCandidateLocalReuses, peakEntries);
    }

    private void updatePeakEntries() {
        peakEntries = Math.max(peakEntries, roleAssignments.size()
                + candidatePositions.size() + pickedPositions.size()
                + completion.size() + poolHealth.size());
    }

    private record ChampionCombinationKey(List<ChampionId> champions) {
        private static ChampionCombinationKey of(List<ChampionId> champions) {
            ArrayList<ChampionId> canonical = new ArrayList<>(champions);
            canonical.sort(CHAMPION_ORDER);
            return new ChampionCombinationKey(List.copyOf(canonical));
        }
    }

    private record CandidateRoleKey(ChampionCombinationKey picks, ChampionId champion) { }

    private record CompletionKey(StateKey state, TeamSide side, ChampionId candidate,
                                 Position targetPosition) { }

    private record PoolHealthKey(StateKey state, TeamSide side, ChampionId candidate) { }

    private record StateKey(int nextTurnIndex, List<ChampionId> bluePicks,
                            List<ChampionId> redPicks, List<ChampionId> blueBans,
                            List<ChampionId> redBans,
                            List<ChampionId> fearlessExclusions) {
        private static StateKey of(DraftState state) {
            ArrayList<ChampionId> exclusions = new ArrayList<>(state.fearlessExclusions());
            exclusions.sort(CHAMPION_ORDER);
            return new StateKey(state.nextTurnIndex(), state.bluePicks(), state.redPicks(),
                    state.blueBans(), state.redBans(), List.copyOf(exclusions));
        }
    }

    record Snapshot(boolean cacheEnabled, long roleAssignmentRequests,
                    long roleAssignmentHits, long roleAssignmentMisses,
                    long roleAssignmentPhysicalComputations,
                    int roleAssignmentEntries, long rolePositionRequests,
                    long rolePositionHits, long rolePositionMisses,
                    int rolePositionEntries, long completionRequests,
                    long completionHits, long completionMisses,
                    long completionPhysicalComputations, int completionEntries,
                    long poolHealthRequests, long poolHealthHits,
                    long poolHealthMisses, long poolHealthPhysicalComputations,
                    int poolHealthEntries,
                    long plannerCandidatePhysicalComputations,
                    long plannerCandidateLocalReuses, int peakEntries) { }
}
