package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.domain.OuterTurretSiegeData;
import com.lolfm.domain.Position;
import com.lolfm.domain.StructureActionData;
import com.lolfm.domain.StructureActionPhase;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Stateless mechanical owner of structure eligibility, durability, rewards and
 * siege continuity. All mutable state lives in GameState/MapState children.
 */
@Component
public class StructureResolver {

    public boolean canAttemptSiege(GameState state, StructureAttackRequest request) {
        if (state.isFinished()) return false;
        if (request.actionId() == null
                && state.wasStructureActionAttemptedThisTick(request.attackingSide())) return false;
        Optional<StructureTargetId> resolved = resolveRequestedTarget(state, request);
        if (resolved.isEmpty() || !participantsAvailable(state, request)) return false;
        StructureTargetId target = resolved.get();
        int minimum = isBaseTarget(target)
                ? StructureRuleConfig.MIN_BASE_SIEGE_ATTACKERS
                : StructureRuleConfig.MIN_LANE_SIEGE_ATTACKERS;
        if (request.participants().size() < minimum) return false;
        if (request.mode() == StructureAttackMode.WITH_WAVE) {
            if (request.routeLane() == null) return false;
            LaneWaveState wave = state.getMapState().getWaveState(
                    request.attackingSide(), request.routeLane());
            boolean continuation = request.actionId() != null && request.persistent();
            if (continuation) {
                if (!wave.hasActiveWaveAt(state.getCurrentTimeSeconds())) return false;
            } else if (!wave.hasActiveWaveAt(state.getCurrentTimeSeconds())
                    && !wave.canPrepareAt(state.getCurrentTimeSeconds())) return false;
        }
        if (request.persistent() && request.actionId() == null
                && state.getBaseSiegeState(request.attackingSide()).isActive()) return false;
        if (request.actionId() != null && request.persistent()) {
            BaseSiegeState siege = state.getBaseSiegeState(request.attackingSide());
            if (!siege.isActive() || !request.actionId().equals(siege.getActionId())
                    || state.getCurrentTimeSeconds() < siege.getNextAttackAtSeconds()) return false;
        }
        return isTargetAttackable(state, target);
    }

    public Optional<StructureAttackResult> attemptSiege(
            GameState state, StructureAttackRequest request) {
        if (!canAttemptSiege(state, request)) return Optional.empty();
        StructureTargetId target = resolveRequestedTarget(state, request).orElseThrow();
        StructureActionSource source = request.sourceOverride() == null
                ? source(request.reason()) : request.sourceOverride();
        boolean siegeStarted = request.persistent() && request.actionId() == null;
        String actionId = request.actionId() == null
                ? state.nextStructureActionId(request.attackingSide(), source)
                : request.actionId();
        StructureActionKey key = new StructureActionKey(
                state.getCurrentTimeSeconds(), request.attackingSide(), actionId,
                request.attackSequence());
        StructureActionReservation reservation = state.reserveStructureMutation(key);
        if (reservation != StructureActionReservation.RESERVED) return Optional.empty();

        BaseSiegeState siege = state.getBaseSiegeState(request.attackingSide());
        try {
            if (siegeStarted) {
                boolean baronEmpowered = state.getTeamState(request.attackingSide())
                        .hasActiveBaronBuff(state.getCurrentTimeSeconds());
                int duration = StructureRuleConfig.siegeDurationSeconds(
                        request.reason(), baronEmpowered);
                int attackOpportunities = request.mode() == StructureAttackMode.BACKDOOR
                        ? StructureRuleConfig.BACKDOOR_ATTACK_OPPORTUNITIES
                        : StructureRuleConfig.waveAttackOpportunities(
                                request.reason(), baronEmpowered);
                if (request.durationSecondsOverride() != null) {
                    duration = request.durationSecondsOverride();
                }
                if (request.attackOpportunityOverride() != null) {
                    attackOpportunities = request.attackOpportunityOverride();
                }
                siege.start(actionId, request.routeLane(), target, request.reason(), source,
                        request.parentActionId(), request.participants(), request.mode(),
                        state.getCurrentTimeSeconds(), duration, attackOpportunities);
                beginSiegeActivities(state, siege);
            }

            if (request.mode() == StructureAttackMode.WITH_WAVE) {
                LaneWaveState wave = state.getMapState().getWaveState(
                        request.attackingSide(), request.routeLane());
                if (!wave.hasActiveWaveAt(state.getCurrentTimeSeconds())) {
                    boolean baronEmpowered = state.getTeamState(request.attackingSide())
                            .hasActiveBaronBuff(state.getCurrentTimeSeconds());
                    int attackOpportunities = siegeStarted
                            ? siege.getAttackOpportunityLimit()
                            : StructureRuleConfig.waveAttackOpportunities(
                                    request.reason(), baronEmpowered);
                    wave.prepareAt(state.getCurrentTimeSeconds(),
                            attackOpportunities);
                }
                if (!wave.consumeAttack(state.getCurrentTimeSeconds())) {
                    if (siegeStarted) {
                        siege.stop(SiegeStopReason.WAVE_LOST);
                        releaseSiegeActivities(state, siege);
                    }
                    state.cancelStructureMutation(key);
                    return Optional.empty();
                }
            }

            double before = currentHealth(state, target);
            double requestedDamage = request.fixedDamage() == null
                    ? calculatedDamage(state, request, target)
                    : request.fixedDamage();
            double after = applyDamage(state, target, requestedDamage);
            double actualDamage = Math.max(0.0, before - after);
            if (actualDamage <= 0) {
                if (siegeStarted) {
                    siege.stop(SiegeStopReason.TARGET_PROTECTED);
                    releaseSiegeActivities(state, siege);
                }
                state.cancelStructureMutation(key);
                return Optional.empty();
            }

            int plates = claimReachedPlates(state, target);
            awardPlateGold(state, request.attackingSide(), request.participants(), plates);
            boolean firstTurret = false;
            StructureOutcome destruction = null;
            if (after <= 0) {
                if (target.kind() == StructureKind.TOWER) {
                    firstTurret = state.claimFirstTurret();
                    if (firstTurret) awardLocalGold(state, request.attackingSide(),
                            request.participants(), StructureRuleConfig.FIRST_TURRET_LOCAL_GOLD);
                }
                destruction = finalizeDestruction(state, request.attackingSide(), target,
                        request.reason(), source, null).orElseThrow();
            }
            state.commitStructureMutation(key);

            if (request.persistent() && siege.isActive() && !state.isFinished()) {
                if (destruction != null) {
                    Optional<StructureTargetId> next = resolvePreferredTarget(
                            state, request.attackingSide(), siege.getRouteLane());
                    if (next.isPresent()) {
                        siege.retarget(next.get());
                        if (next.get().kind() == StructureKind.NEXUS
                                && destruction.structureKind() == StructureKind.NEXUS_TURRET
                                && request.mode() == StructureAttackMode.WITH_WAVE
                                && siege.grantNexusCommit(state.getCurrentTimeSeconds())) {
                            state.getMapState().getWaveState(
                                    request.attackingSide(), request.routeLane())
                                    .ensureAttackOpportunities(
                                            StructureRuleConfig.NEXUS_COMMIT_BONUS_ATTACKS,
                                            state.getCurrentTimeSeconds());
                            extendSiegeActivities(state, siege);
                        }
                    }
                    else {
                        siege.stop(SiegeStopReason.NO_TARGET);
                        releaseSiegeActivities(state, siege);
                    }
                }
                if (siege.isActive()) siege.scheduleNextAttack(state.getCurrentTimeSeconds());
            }
            if (state.isFinished() && siege.isActive()) {
                siege.stop(SiegeStopReason.MATCH_FINISHED);
                releaseSiegeActivities(state, siege);
            }
            return Optional.of(new StructureAttackResult(
                    target, before, actualDamage, after, plates, firstTurret, destruction,
                    actionId, request.attackSequence(), request.parentActionId(), source,
                    request.participants(), request.mode(), siegeStarted,
                    request.persistent() && siege.isActive()));
        } catch (RuntimeException exception) {
            state.cancelStructureMutation(key);
            if (siegeStarted && siege.isActive()) {
                siege.stop(SiegeStopReason.TARGET_PROTECTED);
                releaseSiegeActivities(state, siege);
            }
            throw exception;
        }
    }

    public void resolveActiveSieges(GameState state, List<MatchEvent> events) {
        if (state.isFinished()) return;
        int time = state.getCurrentTimeSeconds();
        for (TeamSide side : TeamSide.values()) {
            BaseSiegeState siege = state.getBaseSiegeState(side);
            if (!siege.isActive() || time < siege.getNextAttackAtSeconds()) continue;
            SiegeStopReason stop = continuationStopReason(state, siege);
            if (stop != null) {
                siege.stop(stop);
                releaseSiegeActivities(state, siege);
                events.add(createSiegeStopEvent(state, siege, stop));
                continue;
            }
            StructureAttackRequest request = StructureAttackRequest.continuation(siege);
            Optional<StructureAttackResult> attack = attemptSiege(state, request);
            if (attack.isPresent()) {
                addAttackEvents(state, attack.get(), events);
            } else if (siege.isActive()) {
                siege.stop(SiegeStopReason.TARGET_PROTECTED);
                releaseSiegeActivities(state, siege);
                events.add(createSiegeStopEvent(
                        state, siege, SiegeStopReason.TARGET_PROTECTED));
            }
        }
    }

    public void addLifecycleEvents(GameState state, List<MatchEvent> events) {
        for (StructureRespawnFact fact : state.drainStructureRespawnFacts()) {
            StructureTargetId target = fact.target();
            StructureActionData data = new StructureActionData(
                    StructureActionPhase.RESPAWNED, target.stableId(), target.kind(), target.lane(),
                    target.towerTier(), target.nexusTurretIndex(), null, fact.defendingSide(),
                    null, 0, 0, fact.currentHealth(), fact.maxHealth(), 0, false,
                    Set.of(), false, false, false, null);
            MatchEvent event = baseEvent(fact.timeSeconds(), MatchEventType.STRUCTURE_ACTION,
                    "넥서스 포탑이 40% 체력으로 재생성됐습니다.", target, null, null);
            event.setActionId("STRUCTURE_RESPAWN:" + fact.timeSeconds() + ":" + target.stableId());
            event.setStructureAction(data);
            events.add(event);
        }
    }

    public void addAttackEvents(GameState state, StructureAttackResult result,
                                List<MatchEvent> events) {
        addAttackEvents(state, result, events, null);
    }

    public void addAttackEvents(GameState state, StructureAttackResult result,
                                List<MatchEvent> events, OuterTurretSiegeData outerSiege) {
        StructureTargetId target = result.target();
        StructureActionPhase phase = result.destroyed()
                ? StructureActionPhase.DESTROYED
                : result.siegeStarted() ? StructureActionPhase.STARTED : StructureActionPhase.DAMAGE;
        StructureActionData data = new StructureActionData(
                phase, target.stableId(), target.kind(), target.lane(), target.towerTier(),
                target.nexusTurretIndex(), result.destruction() == null
                        ? target.defendingSide().opposite() : result.destruction().attackingSide(),
                target.defendingSide(), result.source(), result.healthBefore(), result.damage(),
                result.healthAfter(), maxHealth(state, target), result.platesClaimed(),
                result.firstTurretBonus(), result.participants(),
                result.mode() != StructureAttackMode.BACKDOOR,
                result.mode() == StructureAttackMode.BACKDOOR, result.siegeContinues(), null);
        MatchEvent event;
        if (result.destroyed()) {
            event = createStructureEvent(state, result.destruction());
        } else {
            String team = state.getTeamState(target.defendingSide().opposite()).getTeamName();
            event = baseEvent(state.getCurrentTimeSeconds(), MatchEventType.STRUCTURE_ACTION,
                    team + "가 " + targetName(target) + "에 피해를 누적합니다. 남은 체력: "
                            + Math.round(result.healthAfter()) + "/" + Math.round(maxHealth(state, target)),
                    target, result.source(), target.defendingSide().opposite());
        }
        event.setActionId(result.actionId() + ":" + result.attackSequence());
        event.setParentActionId(result.parentActionId());
        event.setStructureAction(data);
        if (outerSiege != null) event.setOuterTurretSiege(outerSiege);
        events.add(event);
    }

    /** Compatibility path for focused setup/tests. Production resolvers use attemptSiege. */
    public Optional<StructureOutcome> destroyNextStructure(
            GameState state, TeamSide attackingSide, Lane lane, PushReason reason) {
        if (state.isFinished()) return Optional.empty();
        Optional<StructureTargetId> target = resolvePreferredTarget(state, attackingSide, lane);
        return target.flatMap(value -> forceDestroy(state, attackingSide, lane, value, reason));
    }

    /** Compatibility path with an explicit planning target and no lane sentinel. */
    public Optional<StructureOutcome> destroyTarget(
            GameState state, TeamSide attackingSide, Lane lane,
            LateGameStructureTarget target, PushReason reason) {
        if (state.isFinished() || target == null) return Optional.empty();
        StructureAttackRequest probe = StructureAttackRequest.fixed(attackingSide, lane, target,
                reason, allPositions(state, attackingSide), 1.0, source(reason),
                legacyActionId(state, attackingSide, reason));
        Optional<StructureTargetId> resolved = resolveRequestedTarget(state, probe);
        return resolved.flatMap(value -> forceDestroy(state, attackingSide, lane, value, reason));
    }

    /** Macro-only compatibility path; it cannot fall through to inhibitors or base targets. */
    public Optional<StructureOutcome> destroyNextTower(
            GameState state, TeamSide attackingSide, Lane lane, PushReason reason) {
        if (state.isFinished()) return Optional.empty();
        LaneStructureState laneState = state.getMapState().getLaneState(attackingSide.opposite(), lane);
        return laneState.nextAliveTower().flatMap(tier -> forceDestroy(
                state, attackingSide, lane,
                StructureTargetId.tower(attackingSide.opposite(), lane, tier), reason));
    }

    public Optional<StructureOutcome> destroyOuterFromLanePressure(
            GameState state, TeamSide attackingSide, Lane lane, OuterTurretSiegeData siege) {
        if (state.isFinished()) return Optional.empty();
        TeamSide defending = attackingSide.opposite();
        LaneStructureState laneState = state.getMapState().getLaneState(defending, lane);
        if (!laneState.isOuterTowerAlive() || laneState.getTowerCurrentHealth(TowerTier.OUTER) > 0) {
            return Optional.empty();
        }
        StructureTargetId target = StructureTargetId.tower(defending, lane, TowerTier.OUTER);
        StructureActionKey key = new StructureActionKey(state.getCurrentTimeSeconds(), attackingSide,
                "LANE_PRESSURE:" + state.getCurrentTimeSeconds() + ":" + attackingSide + ":" + lane,
                0);
        if (state.reserveStructureMutation(key) != StructureActionReservation.RESERVED) return Optional.empty();
        Optional<StructureOutcome> outcome = finalizeDestruction(state, attackingSide, target,
                PushReason.MACRO_PLAY, StructureActionSource.LANE_PRESSURE, siege);
        if (outcome.isPresent()) state.commitStructureMutation(key); else state.cancelStructureMutation(key);
        return outcome;
    }

    public MatchEvent createStructureEvent(GameState state, StructureOutcome outcome) {
        String team = state.getTeamState(outcome.attackingSide()).getTeamName();
        MatchEvent event = new MatchEvent(outcome.occurredAtSeconds(), MatchEventType.TOWER,
                message(team, outcome, state), null, null, List.of());
        event.setStructureActionSource(outcome.source());
        event.setStructureKind(outcome.structureKind());
        event.setStructureTowerTier(outcome.towerTier());
        event.setStructureLane(outcome.lane());
        event.setStructureAttackingSide(outcome.attackingSide());
        event.setStructureDefendingSide(outcome.defendingSide());
        event.setOuterTurretSiege(outcome.outerTurretSiege());
        event.setActionId("STRUCTURE:" + outcome.occurredAtSeconds() + ":"
                + outcome.attackingSide() + ":" + outcome.source());
        return event;
    }

    public StructureActionSource source(PushReason reason) {
        return switch (reason) {
            case POST_FIGHT -> StructureActionSource.POST_FIGHT;
            case BARON_PRESSURE -> StructureActionSource.BARON_PRESSURE;
            case MACRO_PLAY -> StructureActionSource.MACRO_PLAY;
            case MID_GAME_MACRO -> StructureActionSource.MID_GAME_MACRO;
            case OBJECTIVE_TRADE -> StructureActionSource.OBJECTIVE_TRADE;
            case LATE_GAME_SIEGE -> StructureActionSource.LATE_GAME_SIEGE;
            case LATE_GAME_CROSS_MAP -> StructureActionSource.LATE_GAME_CROSS_MAP;
            case NEXUS_FINISH -> StructureActionSource.NEXUS_FINISH;
        };
    }

    private Optional<StructureOutcome> forceDestroy(GameState state, TeamSide attackingSide,
                                                     Lane lane, StructureTargetId target,
                                                     PushReason reason) {
        StructureAttackRequest request = StructureAttackRequest.fixed(
                attackingSide, lane, target.planningTarget(), reason,
                allPositions(state, attackingSide), currentHealth(state, target), source(reason),
                legacyActionId(state, attackingSide, reason));
        return attemptSiege(state, request).map(StructureAttackResult::destruction);
    }

    private Optional<StructureTargetId> resolveRequestedTarget(
            GameState state, StructureAttackRequest request) {
        TeamSide defending = request.attackingSide().opposite();
        LateGameStructureTarget requested = request.requestedTarget();
        if (requested == LateGameStructureTarget.NEXUS) {
            return state.getMapState().isNexusVulnerable(defending)
                    ? Optional.of(StructureTargetId.nexus(defending)) : Optional.empty();
        }
        if (requested == LateGameStructureTarget.NEXUS_TURRET) {
            if (!state.getMapState().areNexusTurretsVulnerable(defending)) return Optional.empty();
            int index = state.getMapState().getBaseState(defending).nextAliveNexusTurretIndex();
            return index < 0 ? Optional.empty()
                    : Optional.of(StructureTargetId.nexusTurret(defending, index));
        }
        if (state.getMapState().isNexusVulnerable(defending)) {
            return requested == null ? Optional.of(StructureTargetId.nexus(defending)) : Optional.empty();
        }
        if (state.getMapState().areNexusTurretsVulnerable(defending)) {
            if (requested != null) return Optional.empty();
            int index = state.getMapState().getBaseState(defending).nextAliveNexusTurretIndex();
            return index < 0 ? Optional.empty()
                    : Optional.of(StructureTargetId.nexusTurret(defending, index));
        }
        if (request.routeLane() == null) return Optional.empty();
        LaneStructureState laneState = state.getMapState().getLaneState(defending, request.routeLane());
        Optional<StructureTargetId> current = laneState.nextAliveTower()
                .map(tier -> StructureTargetId.tower(defending, request.routeLane(), tier));
        if (current.isEmpty() && laneState.isInhibitorVulnerable()) {
            current = Optional.of(StructureTargetId.inhibitor(defending, request.routeLane()));
        }
        if (requested == null) return current;
        return current.filter(value -> value.planningTarget() == requested);
    }

    private Optional<StructureTargetId> resolvePreferredTarget(
            GameState state, TeamSide attackingSide, Lane routeLane) {
        return resolveRequestedTarget(state, new StructureAttackRequest(
                attackingSide, routeLane, null, PushReason.MACRO_PLAY,
                allPositions(state, attackingSide), null, StructureAttackMode.LANE_PRESSURE,
                false, 1.0, StructureActionSource.MACRO_PLAY,
                "TARGET_PROBE", 0, null, null));
    }

    private boolean isTargetAttackable(GameState state, StructureTargetId target) {
        if (state.isFinished()) return false;
        return switch (target.kind()) {
            case TOWER -> state.getMapState().getLaneState(target.defendingSide(), target.lane())
                    .canDestroy(target.towerTier());
            case INHIBITOR -> state.getMapState().getLaneState(target.defendingSide(), target.lane())
                    .isInhibitorVulnerable();
            case NEXUS_TURRET -> state.getMapState().areNexusTurretsVulnerable(target.defendingSide())
                    && state.getMapState().getBaseState(target.defendingSide())
                    .getNexusTurretCurrentHealth(target.nexusTurretIndex()) > 0;
            case NEXUS -> state.getMapState().isNexusVulnerable(target.defendingSide());
        };
    }

    private boolean participantsAvailable(GameState state, StructureAttackRequest request) {
        boolean mayContinueAfterCombat = request.reason() == PushReason.POST_FIGHT
                || request.reason() == PushReason.LATE_GAME_SIEGE
                || request.reason() == PushReason.NEXUS_FINISH;
        boolean continuation = request.actionId() != null && request.persistent();
        for (Position position : request.participants()) {
            PlayerState player = state.getTeamState(request.attackingSide()).playerAt(position);
            if (!player.isAlive(state.getCurrentTimeSeconds())) return false;
            boolean assignedToThisSiege = continuation
                    && player.getActivityState().isSiegingAction(request.actionId());
            if (!assignedToThisSiege
                    && !player.canParticipateInMajorCombatAt(state.getCurrentTimeSeconds())) return false;
            if (!mayContinueAfterCombat && !assignedToThisSiege
                    && state.wasMajorCombatParticipantThisTick(player)) return false;
        }
        return true;
    }

    private void beginSiegeActivities(GameState state, BaseSiegeState siege) {
        for (Position position : siege.getParticipants()) {
            state.getTeamState(siege.getAttackingSide()).playerAt(position)
                    .beginSiegeActivity(siege.getRouteLane(), siege.getActionId(),
                            state.getCurrentTimeSeconds(), siege.getExpiresAtSeconds());
        }
    }

    private void extendSiegeActivities(GameState state, BaseSiegeState siege) {
        for (Position position : siege.getParticipants()) {
            state.getTeamState(siege.getAttackingSide()).playerAt(position)
                    .extendSiegeActivity(siege.getActionId(), siege.getExpiresAtSeconds());
        }
    }

    private void releaseSiegeActivities(GameState state, BaseSiegeState siege) {
        for (Position position : siege.getParticipants()) {
            state.getTeamState(siege.getAttackingSide()).playerAt(position)
                    .endSiegeActivity(siege.getActionId());
        }
    }

    private double calculatedDamage(GameState state, StructureAttackRequest request,
                                    StructureTargetId target) {
        double damage = request.participants().size()
                * StructureRuleConfig.EFFECTIVE_DAMAGE_PER_ATTACKER;
        damage *= switch (request.reason()) {
            case POST_FIGHT -> StructureRuleConfig.POST_FIGHT_DAMAGE_MULTIPLIER;
            case BARON_PRESSURE -> StructureRuleConfig.BARON_DAMAGE_MULTIPLIER;
            case OBJECTIVE_TRADE -> StructureRuleConfig.OBJECTIVE_TRADE_DAMAGE_MULTIPLIER;
            case MID_GAME_MACRO -> StructureRuleConfig.MID_GAME_DAMAGE_MULTIPLIER;
            case LATE_GAME_SIEGE, LATE_GAME_CROSS_MAP, NEXUS_FINISH ->
                    StructureRuleConfig.LATE_GAME_DAMAGE_MULTIPLIER;
            case MACRO_PLAY -> 1.0;
        };
        if (state.getTeamState(request.attackingSide())
                .hasActiveBaronBuff(state.getCurrentTimeSeconds())
                && request.reason() != PushReason.BARON_PRESSURE) {
            damage *= StructureRuleConfig.BARON_DAMAGE_MULTIPLIER;
        }
        if (request.mode() == StructureAttackMode.BACKDOOR) {
            damage *= StructureRuleConfig.BACKDOOR_DAMAGE_MULTIPLIER;
        }
        if (target.kind() == StructureKind.TOWER && target.towerTier() == TowerTier.OUTER
                && state.getCurrentTimeSeconds() < StructureRuleConfig.EARLY_OUTER_PROTECTION_END_SECONDS) {
            damage *= StructureRuleConfig.EARLY_OUTER_DAMAGE_MULTIPLIER;
        }
        int defenders = localDefenderCount(state, target);
        damage *= Math.max(StructureRuleConfig.MIN_LOCAL_DEFENSE_DAMAGE_MULTIPLIER,
                1.0 - defenders * StructureRuleConfig.LOCAL_DEFENDER_DAMAGE_REDUCTION_PER_PLAYER);
        return Math.max(1.0, damage);
    }

    private int localDefenderCount(GameState state, StructureTargetId target) {
        List<Position> positions;
        if (isBaseTarget(target)) {
            positions = List.of(Position.TOP, Position.JUNGLE, Position.MID,
                    Position.ADC, Position.SUPPORT);
        } else {
            positions = switch (target.lane()) {
                case TOP -> List.of(Position.TOP);
                case MID -> List.of(Position.MID, Position.JUNGLE, Position.SUPPORT);
                case BOT -> List.of(Position.ADC, Position.SUPPORT);
            };
        }
        int count = 0;
        for (Position position : positions) {
            PlayerState defender = state.getTeamState(target.defendingSide()).playerAt(position);
            if (defender.canParticipateInMajorCombatAt(state.getCurrentTimeSeconds())
                    && !state.wasMajorCombatParticipantThisTick(defender)) count++;
        }
        return count;
    }

    private double currentHealth(GameState state, StructureTargetId target) {
        return switch (target.kind()) {
            case TOWER -> state.getMapState().getLaneState(target.defendingSide(), target.lane())
                    .getTowerCurrentHealth(target.towerTier());
            case INHIBITOR -> state.getMapState().getLaneState(target.defendingSide(), target.lane())
                    .getInhibitorCurrentHealth();
            case NEXUS_TURRET -> state.getMapState().getBaseState(target.defendingSide())
                    .getNexusTurretCurrentHealth(target.nexusTurretIndex());
            case NEXUS -> state.getMapState().getBaseState(target.defendingSide()).getNexusCurrentHealth();
        };
    }

    private double maxHealth(GameState state, StructureTargetId target) {
        return switch (target.kind()) {
            case TOWER -> state.getMapState().getLaneState(target.defendingSide(), target.lane())
                    .getTowerMaxHealth(target.towerTier());
            case INHIBITOR -> state.getMapState().getLaneState(target.defendingSide(), target.lane())
                    .getInhibitorMaxHealth();
            case NEXUS_TURRET -> state.getMapState().getBaseState(target.defendingSide())
                    .getNexusTurretMaxHealth(target.nexusTurretIndex());
            case NEXUS -> state.getMapState().getBaseState(target.defendingSide()).getNexusMaxHealth();
        };
    }

    private double applyDamage(GameState state, StructureTargetId target, double damage) {
        return switch (target.kind()) {
            case TOWER -> state.getMapState().getLaneState(target.defendingSide(), target.lane())
                    .applyTowerDamage(target.towerTier(), damage);
            case INHIBITOR -> state.getMapState().getLaneState(target.defendingSide(), target.lane())
                    .applyInhibitorDamage(damage);
            case NEXUS_TURRET -> state.getMapState().getBaseState(target.defendingSide())
                    .applyNexusTurretDamage(target.nexusTurretIndex(), damage,
                            state.getCurrentTimeSeconds());
            case NEXUS -> state.getMapState().getBaseState(target.defendingSide())
                    .applyNexusDamage(damage);
        };
    }

    private int claimReachedPlates(GameState state, StructureTargetId target) {
        return switch (target.kind()) {
            case TOWER -> state.getMapState().getLaneState(target.defendingSide(), target.lane())
                    .claimReachedTowerPlates(target.towerTier());
            case NEXUS_TURRET -> state.getMapState().getBaseState(target.defendingSide())
                    .claimReachedNexusTurretPlates(target.nexusTurretIndex());
            case INHIBITOR, NEXUS -> 0;
        };
    }

    private Optional<StructureOutcome> finalizeDestruction(
            GameState state, TeamSide attacking, StructureTargetId target,
            PushReason reason, StructureActionSource source, OuterTurretSiegeData siege) {
        if (state.isFinished()) return Optional.empty();
        int time = state.getCurrentTimeSeconds();
        if (target.kind() == StructureKind.TOWER) {
            LaneStructureState laneState = state.getMapState()
                    .getLaneState(target.defendingSide(), target.lane());
            if (!laneState.canDestroy(target.towerTier())
                    || laneState.getTowerCurrentHealth(target.towerTier()) > 0) return Optional.empty();
            laneState.destroy(target.towerTier(), time, attacking, source);
            TeamState attackers = state.getTeamState(attacking);
            attackers.addTowerDestroyed();
            awardGlobalTowerGold(attackers, time);
            if (target.towerTier() == TowerTier.OUTER) {
                state.getLanePhaseExecutionStats().recordDestruction(
                        target.defendingSide(), target.lane(), source);
                state.getLanePhaseState().openLane(target.lane(), time);
            }
            return Optional.of(new StructureOutcome(attacking, target.defendingSide(),
                    StructureKind.TOWER, target.lane(), target.towerTier(), time,
                    reason, false, source, siege));
        }
        if (target.kind() == StructureKind.INHIBITOR) {
            LaneStructureState laneState = state.getMapState()
                    .getLaneState(target.defendingSide(), target.lane());
            if (laneState.getInhibitorCurrentHealth() > 0
                    || !laneState.destroyInhibitor(time)) return Optional.empty();
            state.getMapState().activateBasePressure(attacking, time);
            return Optional.of(new StructureOutcome(attacking, target.defendingSide(),
                    StructureKind.INHIBITOR, target.lane(), null, time, reason, false, source, null));
        }
        if (target.kind() == StructureKind.NEXUS_TURRET) {
            if (state.getMapState().getBaseState(target.defendingSide())
                    .getNexusTurretCurrentHealth(target.nexusTurretIndex()) > 0) return Optional.empty();
            return Optional.of(new StructureOutcome(attacking, target.defendingSide(),
                    StructureKind.NEXUS_TURRET, null, null, time, reason, false, source, null));
        }
        BaseState base = state.getMapState().getBaseState(target.defendingSide());
        if (base.getNexusCurrentHealth() > 0 || !base.destroyNexus(time)) return Optional.empty();
        state.finish(attacking, GameEndReason.NEXUS_DESTROYED);
        return Optional.of(new StructureOutcome(attacking, target.defendingSide(),
                StructureKind.NEXUS, null, null, time, reason, true, source, null));
    }

    private SiegeStopReason continuationStopReason(GameState state, BaseSiegeState siege) {
        int time = state.getCurrentTimeSeconds();
        if (state.isFinished()) return SiegeStopReason.MATCH_FINISHED;
        if (time >= siege.getExpiresAtSeconds()) return SiegeStopReason.EXPIRED;
        if (siege.getAttackSequence() >= siege.getAttackOpportunityLimit()) {
            return siege.getMode() == StructureAttackMode.BACKDOOR
                    ? SiegeStopReason.ATTACK_WINDOW_COMPLETE : SiegeStopReason.WAVE_LOST;
        }
        if (siege.getMode() == StructureAttackMode.WITH_WAVE
                && !state.getMapState().getWaveState(
                        siege.getAttackingSide(), siege.getRouteLane()).hasActiveWaveAt(time)) {
            return SiegeStopReason.WAVE_LOST;
        }
        StructureTargetId target = siege.getCurrentTarget();
        if (!isTargetAttackable(state, target)) return SiegeStopReason.TARGET_PROTECTED;
        int available = 0;
        for (Position position : siege.getParticipants()) {
            PlayerState player = state.getTeamState(siege.getAttackingSide()).playerAt(position);
            if (!player.isAlive(time)) return SiegeStopReason.ATTACKER_KILLED;
            available++;
        }
        int minimum = isBaseTarget(target)
                ? StructureRuleConfig.MIN_BASE_SIEGE_ATTACKERS
                : StructureRuleConfig.MIN_LANE_SIEGE_ATTACKERS;
        if (available < minimum) return SiegeStopReason.INSUFFICIENT_ATTACKERS;
        if (!participantsAvailable(state, StructureAttackRequest.continuation(siege))) {
            return SiegeStopReason.ATTACKERS_DISENGAGED;
        }
        if (isBaseTarget(target)
                && localDefenderCount(state, target) >= StructureRuleConfig.BASE_DEFENSE_RETURN_COUNT
                && !siege.isNexusCommitGranted()
                && !state.getTeamState(siege.getAttackingSide()).hasActiveBaronBuff(time)) {
            return SiegeStopReason.DEFENDERS_RETURNED;
        }
        return null;
    }

    private MatchEvent createSiegeStopEvent(GameState state, BaseSiegeState siege,
                                            SiegeStopReason reason) {
        StructureTargetId target = siege.getCurrentTarget();
        StructureActionPhase phase = reason == SiegeStopReason.DEFENDERS_RETURNED
                ? StructureActionPhase.REPELLED : StructureActionPhase.ABORTED;
        String team = state.getTeamState(siege.getAttackingSide()).getTeamName();
        MatchEvent event = baseEvent(state.getCurrentTimeSeconds(), MatchEventType.STRUCTURE_ACTION,
                reason == SiegeStopReason.DEFENDERS_RETURNED
                        ? team + "의 공성이 수비 복귀로 격퇴됐습니다."
                        : team + "의 공성이 종료됐습니다. 사유: " + reason,
                target, siege.getSource(), siege.getAttackingSide());
        event.setActionId(siege.getActionId() + ":STOP");
        event.setParentActionId(siege.getParentActionId());
        event.setStructureAction(new StructureActionData(
                phase, target.stableId(), target.kind(), target.lane(), target.towerTier(),
                target.nexusTurretIndex(), siege.getAttackingSide(), target.defendingSide(),
                siege.getSource(), currentHealth(state, target), 0, currentHealth(state, target),
                maxHealth(state, target), 0, false, siege.getParticipants(),
                false, siege.getMode() == StructureAttackMode.BACKDOOR, false, reason));
        return event;
    }

    private MatchEvent baseEvent(int time, MatchEventType type, String message,
                                 StructureTargetId target, StructureActionSource source,
                                 TeamSide attackingSide) {
        MatchEvent event = new MatchEvent(time, type, message, null, null, List.of());
        event.setStructureActionSource(source);
        event.setStructureKind(target.kind());
        event.setStructureTowerTier(target.towerTier());
        event.setStructureLane(target.lane());
        event.setStructureAttackingSide(attackingSide);
        event.setStructureDefendingSide(target.defendingSide());
        return event;
    }

    private void awardPlateGold(GameState state, TeamSide side, Set<Position> participants,
                                int plates) {
        if (plates <= 0) return;
        awardLocalGold(state, side, participants,
                plates * StructureRuleConfig.TURRET_PLATE_LOCAL_GOLD);
    }

    private void awardLocalGold(GameState state, TeamSide side, Set<Position> participants,
                                int totalGold) {
        if (totalGold <= 0 || participants.isEmpty()) return;
        List<Position> ordered = participants.stream().sorted().toList();
        int base = totalGold / ordered.size();
        int remainder = totalGold % ordered.size();
        TeamState team = state.getTeamState(side);
        for (int index = 0; index < ordered.size(); index++) {
            int reward = base + (index < remainder ? 1 : 0);
            team.playerAt(ordered.get(index)).addGold(
                    reward, GoldSource.STRUCTURE, state.getCurrentTimeSeconds());
            team.addGold(reward);
        }
    }

    private void awardGlobalTowerGold(TeamState team, int timeSeconds) {
        for (PlayerState player : team.getPlayers()) {
            player.addGold(StructureRuleConfig.TURRET_GLOBAL_GOLD_PER_PLAYER,
                    GoldSource.STRUCTURE, timeSeconds);
        }
        team.addGold(StructureRuleConfig.TURRET_GLOBAL_GOLD_PER_PLAYER * team.getPlayers().size());
    }

    private Set<Position> allPositions(GameState state, TeamSide side) {
        EnumSet<Position> result = EnumSet.noneOf(Position.class);
        for (PlayerState player : state.getTeamState(side).getPlayers()) result.add(player.getPosition());
        return result;
    }

    private boolean isBaseTarget(StructureTargetId target) {
        return target.kind() == StructureKind.NEXUS_TURRET || target.kind() == StructureKind.NEXUS;
    }

    private String legacyActionId(GameState state, TeamSide side, PushReason reason) {
        return "LEGACY_STRUCTURE:" + state.getCurrentTimeSeconds() + ":" + side + ":" + reason;
    }

    private String targetName(StructureTargetId target) {
        return switch (target.kind()) {
            case TOWER -> laneName(target.lane()) + " " + switch (target.towerTier()) {
                case OUTER -> "외곽 포탑";
                case INNER -> "내부 포탑";
                case INHIBITOR -> "억제기 포탑";
            };
            case INHIBITOR -> laneName(target.lane()) + " 억제기";
            case NEXUS_TURRET -> "넥서스 포탑";
            case NEXUS -> "넥서스";
        };
    }

    private String message(String team, StructureOutcome outcome, GameState state) {
        if (outcome.source() == StructureActionSource.LANE_PRESSURE) {
            return team + "가 라인 압박으로 " + laneName(outcome.lane()) + " 외곽 포탑을 파괴합니다.";
        }
        if (outcome.structureKind() == StructureKind.NEXUS) return team + "가 적 넥서스를 파괴합니다.";
        if (outcome.structureKind() == StructureKind.NEXUS_TURRET) {
            int remaining = state.getMapState().getBaseState(outcome.defendingSide()).getNexusTurretsRemaining();
            return team + (outcome.reason() == PushReason.BARON_PRESSURE
                    ? "가 바론 버프를 앞세워 " : "가 적 본진에 진입해 ")
                    + "넥서스 포탑을 파괴합니다. 남은 포탑: " + remaining;
        }
        String lane = laneName(outcome.lane());
        if (outcome.structureKind() == StructureKind.INHIBITOR) {
            return outcome.reason() == PushReason.POST_FIGHT
                    ? team + "가 한타 승리를 바탕으로 " + lane + " 억제기까지 무너뜨립니다."
                    : team + "가 " + lane + " 억제기를 파괴합니다.";
        }
        String tier = switch (outcome.towerTier()) {
            case OUTER -> "외곽 포탑";
            case INNER -> "내부 포탑";
            case INHIBITOR -> "억제기 포탑";
        };
        return switch (outcome.reason()) {
            case BARON_PRESSURE -> team + "가 바론 버프를 앞세워 " + lane + " " + tier + "을 무너뜨립니다.";
            case POST_FIGHT -> team + "가 한타 승리 이후 " + lane + " " + tier + "까지 진격합니다.";
            case MACRO_PLAY -> team + "가 운영 압박으로 " + lane + " " + tier + "을 파괴합니다.";
            case MID_GAME_MACRO -> team + "가 미드게임 팀 운영으로 " + lane + " " + tier + "을 파괴합니다.";
            case OBJECTIVE_TRADE -> team + "가 오브젝트를 양보하는 대신 " + lane + " " + tier + "을 파괴합니다.";
            case LATE_GAME_SIEGE -> team + "가 후반 공성으로 " + lane + " " + tier + "을 파괴합니다.";
            case LATE_GAME_CROSS_MAP -> team + "가 교차 맵 운영으로 " + lane + " " + tier + "을 파괴합니다.";
            case NEXUS_FINISH -> team + "가 넥서스 마무리 과정에서 " + lane + " " + tier + "을 파괴합니다.";
        };
    }

    private String laneName(Lane lane) {
        return switch (lane) {
            case TOP -> "탑";
            case MID -> "미드";
            case BOT -> "바텀";
        };
    }
}
