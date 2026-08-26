package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
import com.lolfm.composition.CompositionActionType;
import com.lolfm.composition.CompositionAttemptDescriptor;
import com.lolfm.composition.GameplayAttemptId;
import com.lolfm.composition.TeamCompositionContext;
import com.lolfm.composition.CompositionBaselineScoreDomain;
import com.lolfm.composition.CompositionLocalDecisionComparison;
import com.lolfm.composition.FightScale;
import com.lolfm.composition.*;
import com.lolfm.domain.Player;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TeamfightResolver {

    private final PlayerSkillEvaluator playerSkills = new PlayerSkillEvaluator();
    private final CombatParticipantSelector participantSelector = new CombatParticipantSelector();
    private static final int SIMULATION_SAFETY_TIMEOUT_SECONDS = MatchSimulator.SIMULATION_SAFETY_TIMEOUT_SECONDS;
    private final KillRewardResolver killRewards = new KillRewardResolver();

    public Optional<TeamfightOutcome> maybeResolveTeamfight(GameState gameState,Team blueTeam,Team redTeam,Random random,List<MatchEvent> events) {
        return maybeResolveTeamfight(gameState,blueTeam,redTeam,random,events,ProgressionCombatContext.TEAMFIGHT);
    }

    private Optional<TeamfightOutcome> maybeResolveTeamfight(GameState gameState, Team blueTeam, Team redTeam, Random random,
                                                               List<MatchEvent> events, ProgressionCombatContext progressionContext) {
        return maybeResolveTeamfight(gameState, blueTeam, redTeam, random, events, progressionContext, null);
    }

    private Optional<TeamfightOutcome> maybeResolveTeamfight(GameState gameState, Team blueTeam, Team redTeam, Random random,
                                                               List<MatchEvent> events, ProgressionCombatContext progressionContext,
                                                               TeamSide structuredAttackingSide) {
        int currentTime = gameState.getCurrentTimeSeconds();
        int minimumAlive = progressionContext == ProgressionCombatContext.TEAMFIGHT
                ? CombatRealismRuleConfig.MIN_ALIVE_PLAYERS_FOR_STANDARD_TEAMFIGHT : 1;
        List<PlayerState> blueParticipants = eligibleParticipants(
                gameState.getBlueTeamState(), currentTime);
        List<PlayerState> redParticipants = eligibleParticipants(
                gameState.getRedTeamState(), currentTime);
        if (currentTime < 900
                || blueParticipants.size() < minimumAlive
                || redParticipants.size() < minimumAlive) {
            return Optional.empty();
        }
        double triggerChance = hasElderTeamWithThreeParticipants(
                blueParticipants, redParticipants, currentTime)
                ? ElderRuleConfig.TEAMFIGHT_TRIGGER_CHANCE
                : CombatRealismRuleConfig.STANDARD_TEAMFIGHT_TRIGGER_CHANCE;
        if (random.nextDouble() >= triggerChance) return Optional.empty();

        int eventStart = events.size();
        String actionId = CombatActionIdentity.actualAt(currentTime);
        TeamfightSides sides = determineTeamfightSides(gameState, blueTeam, redTeam, random,
                progressionContext, structuredAttackingSide, actionId);
        CompositionActionType compositionAction = progressionContext == ProgressionCombatContext.BASE_DEFENSE
                ? CompositionActionType.BASE_DEFENSE
                : progressionContext == ProgressionCombatContext.LATE_GAME_SIEGE
                ? CompositionActionType.SIEGE_COMBAT : CompositionActionType.TEAMFIGHT;
        TeamSide structuredAttemptOwner = compositionAction == CompositionActionType.BASE_DEFENSE
                && structuredAttackingSide != null ? structuredAttackingSide : sides.winningSide();
        TeamSide structuredDefender = structuredAttackingSide == null
                ? structuredAttemptOwner.opposite() : structuredAttackingSide.opposite();
        gameState.getCompositionRuntimeState().recordActualAttempt(
                compositionAction, structuredAttemptOwner, structuredAttemptOwner, structuredDefender,
                FightScale.FORMAL, null, false, null, null, currentTime,
                compositionAction == CompositionActionType.TEAMFIGHT
                        ? CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE
                        : compositionAction == CompositionActionType.SIEGE_COMBAT
                        ? CompositionBaselineScoreDomain.SIEGE_PUSH_SCORE
                        : CompositionBaselineScoreDomain.BASE_DEFENSE_SCORE,
                teamfightScore(gameState, sides.winningSide(), sides.winningTeam()),
                teamfightScore(gameState, sides.winningSide().opposite(), sides.losingTeam()));
        blueParticipants.forEach(gameState::markMajorCombatParticipant);
        redParticipants.forEach(gameState::markMajorCombatParticipant);
        GameplayAttemptId compositionAttemptId = gameState.getCompositionRuntimeState().lastActualAttemptId();
        if (gameState.getCompositionRuntimeState().isAuditSemantics()) {
            TeamSide attacker = sides.structuredAttackingSide();
            TeamSide defender = attacker == null ? null : attacker.opposite();
            gameState.getCompositionRuntimeState().recordAuditWinnerObservation(
                    compositionAttemptId, currentTime, sides.auditAdjustment(), TeamSide.BLUE, attacker, defender,
                    new CombatOutcomeProbabilityEvaluator().uniformAdvantageProbability(
                            sides.auditAdjustment().winnerDecisionGap()),
                    new CombatOutcomeProbabilityEvaluator().uniformAdvantageProbability(
                            sides.auditAdjustment().baselineGap()),
                    sides.localDecision().sample(), sides.localDecision().sampleIdentity(), sides.winningSide());
        }
        if (gameState.getCompositionRuntimeState().isAuditSemantics()
                || gameState.getCompositionRuntimeState().isProductionV2()) {
            recordWinnerDecisionProvenance(gameState, sides, compositionAttemptId, compositionContext(compositionAction), compositionAction,
                    compositionAction == CompositionActionType.TEAMFIGHT ? CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE
                            : compositionAction == CompositionActionType.SIEGE_COMBAT ? CompositionBaselineScoreDomain.SIEGE_PUSH_SCORE
                            : CompositionBaselineScoreDomain.BASE_DEFENSE_SCORE);
        }
        GradeDecision gradeDecision = determineFightGrade(gameState, sides, random,
                progressionContext, compositionAttemptId, actionId);
        gameState.getCompositionRuntimeState().recordProductionFightGradeDecision(
                compositionAttemptId, gradeDecision.baselineDecision(),
                gradeDecision.candidateDecision(), gradeDecision.changed());
        FightGrade plannedGrade = gradeDecision.candidateGrade();
        if (gameState.getCompositionRuntimeState().isCandidate() && sides.localDecision() != null) {
            String applicationKey = sides.localDecision().applicationKey();
            boolean sideChanged = !sides.localDecision().baselineDecision()
                    .equals(sides.localDecision().candidateDecision());
            boolean changed = sideChanged || gradeDecision.changed();
            boolean perspectiveWasBlue = sides.winningSide() == TeamSide.BLUE;
            double baselinePerspective = perspectiveWasBlue
                    ? sides.localDecision().baselineScore() : -sides.localDecision().baselineScore();
            double adjustedPerspective = perspectiveWasBlue
                    ? sides.localDecision().candidateScore() : -sides.localDecision().candidateScore();
            String band = com.lolfm.composition.FrozenCompositionGameplayGainPolicy.marginBand(
                    applicationKey, baselinePerspective);
            String baselineDecision = sides.localDecision().baselineDecision()
                    + "|GRADE:" + gradeDecision.baselineDecision();
            String candidateDecision = sides.localDecision().candidateDecision()
                    + "|GRADE:" + gradeDecision.candidateDecision();
            gameState.getCompositionRuntimeState().recordLocalDecisionComparison(new CompositionLocalDecisionComparison(
                    gameState.getCompositionRuntimeState().matchSeed(),
                    gameState.getCompositionRuntimeState().lastActualAttemptId(), currentTime,
                    applicationKey, "UNIFORM_ADVANTAGE_SIDE_SELECTION_AND_FIGHT_GRADE", sides.winningSide(),
                    sides.localDecision().sampleIdentity(), sides.localDecision().sample(),
                    baselinePerspective, 0.0, adjustedPerspective, 0.0,
                    baselineDecision, candidateDecision,
                    changed, band, sideChanged, changed,
                    "HIGH".equals(band) && changed, true, ""));
        }
        int winningKillTarget = Math.min(
                determineWinningTeamKillCount(plannedGrade, random),
                eligibleParticipants(sides.losingTeamState(), currentTime).size()
        );
        int counterKillTarget = determineCounterKillCount(plannedGrade, winningKillTarget, random);
        Set<PlayerState> deadPlayers = new HashSet<>();
        Map<PlayerState, Integer> frozenShutdownGold = freezeShutdownGold(gameState, currentTime);

        events.add(new MatchEvent(
                currentTime,
                MatchEventType.TEAMFIGHT,
                buildTeamfightStartMessage(currentTime, random, sides.winningTeamState().getTeamName()),
                null,
                null,
                List.of()
        ));

        int nextEventTime = currentTime;
        int winningKillsCreated = 0;
        int losingKillsCreated = 0;
        int openingWinningKills = counterKillTarget > 0
                ? Math.min(winningKillTarget - 1, 1 + random.nextInt(Math.max(1, Math.min(2, winningKillTarget - 1))))
                : winningKillTarget;

        for (int index = 0; index < openingWinningKills; index++) {
            if (!resolveKill(
                    nextEventTime,
                    random,
                    sides.winningTeam(),
                    sides.winningTeamState(),
                    sides.losingTeam(),
                    sides.losingTeamState(),
                    events,
                    true,
                    deadPlayers, frozenShutdownGold
            )) {
                break;
            }
            winningKillsCreated++;
        }

        if (counterKillTarget > 0) {
            if (resolveKill(
                    nextEventTime,
                    random,
                    sides.losingTeam(),
                    sides.losingTeamState(),
                    sides.winningTeam(),
                    sides.winningTeamState(),
                    events,
                    true,
                    deadPlayers, frozenShutdownGold
            )) {
                losingKillsCreated++;
            }
        }

        while (winningKillsCreated < winningKillTarget) {
            if (!resolveKill(
                    nextEventTime,
                    random,
                    sides.winningTeam(),
                    sides.winningTeamState(),
                    sides.losingTeam(),
                    sides.losingTeamState(),
                    events,
                    true,
                    deadPlayers, frozenShutdownGold
            )) {
                break;
            }
            winningKillsCreated++;
        }

        FightGrade resolvedGrade = resolveFightGrade(
                plannedGrade,
                winningKillsCreated,
                losingKillsCreated
        );
        if (resolvedGrade == FightGrade.BIG_WIN) {
            gameState.recordBigWin(sides.winningSide());
        }
        if (resolvedGrade == FightGrade.ACE) {
            gameState.recordAce(sides.winningSide());
        }
        commitPendingCombatProgress(gameState.getBlueTeamState());
        commitPendingCombatProgress(gameState.getRedTeamState());

        events.add(new MatchEvent(
                nextEventTime,
                resolvedGrade == FightGrade.ACE ? MatchEventType.ACE : MatchEventType.TEAMFIGHT_RESULT,
                resolvedGrade == FightGrade.ACE
                        ? buildAceMessage(sides.winningTeamState().getTeamName())
                        : buildTeamfightResultMessage(
                                sides.winningTeamState().getTeamName(),
                                resolvedGrade, winningKillsCreated, losingKillsCreated
                        ),
                null,
                null,
                List.of()
        ));

        for (int i = eventStart; i < events.size(); i++) events.get(i).setActionId(actionId);
        for (int i = eventStart; i < events.size(); i++) {
            gameState.getCompositionRuntimeState().bindPublicAction(
                    compositionAttemptId, events.get(i), i);
        }
        gameState.getCombatOutcomeExecutionStats().record(progressionContext,currentTime,true,sides.winningSide(),blueParticipants,redParticipants);
        return Optional.of(new TeamfightOutcome(
                sides.winningSide(),
                resolvedGrade,
                winningKillsCreated,
                losingKillsCreated,
                nextEventTime,
                deadPlayers.stream().map(PlayerState::getPlayerName).toList(),
                actionId
        ));
    }

    public Optional<TeamfightOutcome> resolveForcedTeamfight(GameState state, Team blueTeam, Team redTeam, Random random, List<MatchEvent> events, com.lolfm.domain.CombatSource source) {
        return resolveForcedTeamfight(state, blueTeam, redTeam, random, events, source, null);
    }

    public Optional<TeamfightOutcome> resolveForcedTeamfight(GameState state, Team blueTeam, Team redTeam, Random random,
                                                               List<MatchEvent> events, com.lolfm.domain.CombatSource source,
                                                               TeamSide structuredAttackingSide) {
        int before = events.size();
        ProgressionCombatContext context = source == com.lolfm.domain.CombatSource.BASE_DEFENSE
                ? ProgressionCombatContext.BASE_DEFENSE : ProgressionCombatContext.LATE_GAME_SIEGE;
        Optional<TeamfightOutcome> outcome = maybeResolveTeamfight(state, blueTeam, redTeam,
                new ForcedTriggerRandom(random), events, context, structuredAttackingSide);
        for (int i = before; i < events.size(); i++) {
            MatchEventType type = events.get(i).getType();
            if (type == MatchEventType.KILL || type == MatchEventType.ASSIST
                    || type == MatchEventType.SHUTDOWN) {
                events.get(i).setCombatSource(source);
            }
        }
        return outcome;
    }

    private static long randomDrawOrdinal(Random random) {
        if (random instanceof SideOrientationRandomTraceObserver observer) return observer.drawCount();
        if (random instanceof ForcedTriggerRandom forced) return randomDrawOrdinal(forced.delegate);
        return -1L;
    }

    private static final class ForcedTriggerRandom extends Random {
        private final Random delegate; private boolean firstDouble = true;
        ForcedTriggerRandom(Random delegate) { this.delegate = delegate; }
         public double nextDouble() { if (firstDouble) { firstDouble=false; return 0.0; } return delegate.nextDouble(); }
         public int nextInt(int bound) { return delegate.nextInt(bound); }
         public boolean nextBoolean() { return delegate.nextBoolean(); }
    }

    public boolean resolveKill(
            int timeSeconds,
            Random random,
            Team attackingTeam,
            TeamState attackingTeamState,
            Team defendingTeam,
            TeamState defendingTeamState,
            List<MatchEvent> events,
            boolean teamfight,
            Set<PlayerState> deadPlayers
    ) {
        return resolveKill(timeSeconds, random, attackingTeam, attackingTeamState, defendingTeam,
                defendingTeamState, events, teamfight, deadPlayers, null, null);
    }

    public boolean resolveLocalizedSkirmishKill(
            int timeSeconds,
            Lane lane,
            Random random,
            Team attackingTeam,
            TeamState attackingTeamState,
            Team defendingTeam,
            TeamState defendingTeamState,
            List<MatchEvent> events,
            Set<PlayerState> deadPlayers
    ) {
        return resolveKill(timeSeconds, random, attackingTeam, attackingTeamState,
                defendingTeam, defendingTeamState, events, false, deadPlayers, null,
                localizedPositions(lane));
    }

    /** Eligibility probe used before a localized skirmish consumes trigger or selection Random. */
    boolean canResolveLocalizedSkirmishKill(
            int timeSeconds,
            Lane lane,
            TeamState attackingTeamState,
            TeamState defendingTeamState
    ) {
        Set<Position> positions = localizedPositions(lane);
        return hasEligibleLocalizedPlayer(attackingTeamState, timeSeconds, positions)
                && hasEligibleLocalizedPlayer(defendingTeamState, timeSeconds, positions);
    }

    private boolean hasEligibleLocalizedPlayer(
            TeamState teamState,
            int timeSeconds,
            Set<Position> positions
    ) {
        return teamState.getPlayers().stream()
                .anyMatch(player -> positions.contains(player.getPosition())
                        && player.canParticipateInMajorCombatAt(timeSeconds));
    }

    private boolean resolveKill(
            int timeSeconds,
            Random random,
            Team attackingTeam,
            TeamState attackingTeamState,
            Team defendingTeam,
            TeamState defendingTeamState,
            List<MatchEvent> events,
            boolean teamfight,
            Set<PlayerState> deadPlayers,
            Map<PlayerState, Integer> frozenShutdownGold
    ) {
        return resolveKill(timeSeconds, random, attackingTeam, attackingTeamState,
                defendingTeam, defendingTeamState, events, teamfight, deadPlayers,
                frozenShutdownGold, null);
    }

    private boolean resolveKill(
            int timeSeconds,
            Random random,
            Team attackingTeam,
            TeamState attackingTeamState,
            Team defendingTeam,
            TeamState defendingTeamState,
            List<MatchEvent> events,
            boolean teamfight,
            Set<PlayerState> deadPlayers,
            Map<PlayerState, Integer> frozenShutdownGold,
            Set<Position> allowedPositions
    ) {
        List<Player> killerCandidates = filterEligiblePlayers(
                attackingTeam.getPlayers(), attackingTeamState, timeSeconds, deadPlayers,
                allowedPositions
        );
        List<Player> victimCandidates = filterEligiblePlayers(
                defendingTeam.getPlayers(), defendingTeamState, timeSeconds, deadPlayers,
                allowedPositions
        );
        if (killerCandidates.isEmpty() || victimCandidates.isEmpty()) {
            return false;
        }

        List<PlayerState> killerStateCandidates = killerCandidates.stream()
                .map(player -> attackingTeamState.playerAt(player.getPosition())).toList();
        List<Double> killerRolePriors = killerCandidates.stream()
                .map(player -> CombatParticipantRuleConfig.teamfightKillerRolePrior(
                        player.getPosition(), teamfight)).toList();
        PlayerState killerState = participantSelector.selectKiller(
                killerStateCandidates, killerRolePriors, random);
        Player killer = playerAtPosition(killerCandidates, killerState.getPosition());

        List<PlayerState> victimStateCandidates = victimCandidates.stream()
                .map(player -> defendingTeamState.playerAt(player.getPosition())).toList();
        List<Double> victimRolePriors = victimCandidates.stream()
                .map(player -> CombatParticipantRuleConfig.teamfightVictimRolePrior(
                        player.getPosition())).toList();
        PlayerState victimState = participantSelector.selectTeamfightVictim(
                victimStateCandidates, victimRolePriors, random);
        Player victim = playerAtPosition(victimCandidates, victimState.getPosition());
        List<Player> assistantPlayers = pickAssistPlayers(
                attackingTeam, attackingTeamState, killer, timeSeconds, random, teamfight,
                deadPlayers, allowedPositions
        );

        List<PlayerState> assistantStates = assistantPlayers.stream()
                .map(player -> attackingTeamState.playerAt(player.getPosition()))
                .toList();
        int eventStart = events.size();
        killRewards.award(
                timeSeconds, attackingTeamState, killerState, defendingTeamState, victimState, assistantStates,
                calculateRespawnDelaySeconds(timeSeconds), teamfight,
                frozenShutdownGold == null ? null : frozenShutdownGold.get(victimState), events
        );
        deadPlayers.add(victimState);
        com.lolfm.domain.CombatSource source = teamfight
                ? com.lolfm.domain.CombatSource.TEAMFIGHT
                : com.lolfm.domain.CombatSource.SKIRMISH;
        Lane localizedLane = !teamfight && allowedPositions != null
                ? laneForLocalizedPositions(allowedPositions) : null;
        for (int i = eventStart; i < events.size(); i++) {
            events.get(i).setCombatSource(source);
            if (localizedLane != null) events.get(i).setCombatLane(localizedLane);
        }
        if (!teamfight) {
            commitPendingCombatProgress(attackingTeamState);
            commitPendingCombatProgress(defendingTeamState);
        }
        return true;
    }

    private Map<PlayerState, Integer> freezeShutdownGold(GameState gameState, int timeSeconds) {
        Map<PlayerState, Integer> values = new HashMap<>();
        snapshotTeamShutdownGold(gameState.getBlueTeamState(), gameState.getRedTeamState(), timeSeconds, values);
        snapshotTeamShutdownGold(gameState.getRedTeamState(), gameState.getBlueTeamState(), timeSeconds, values);
        return values;
    }

    private void snapshotTeamShutdownGold(TeamState own, TeamState enemy, int time, Map<PlayerState, Integer> values) {
        for (PlayerState player : own.getPlayers()) {
            int displayed = BountyService.displayedShutdownGold(player, own, enemy, time);
            player.setLastVisibleShutdownGold(displayed);
            values.put(player, displayed);
        }
    }

    void commitPendingCombatProgress(TeamState team) {
        for (PlayerState player : team.getPlayers()) player.commitPendingCombatBountyProgress();
    }

    private TeamfightSides determineTeamfightSides(GameState state, Team blueTeam, Team redTeam, Random random,
                                                       ProgressionCombatContext context, TeamSide structuredAttackingSide,
                                                       String actionId) {
        TeamState blue = state.getBlueTeamState();
        TeamState red = state.getRedTeamState();
        double goldContribution = (blue.getGold() - red.getGold())
                / CombatRealismRuleConfig.TEAMFIGHT_GOLD_EDGE_DIVISOR;
        double blueRuntimeBase = teamfightScore(state, TeamSide.BLUE, blueTeam);
        double redRuntimeBase = teamfightScore(state, TeamSide.RED, redTeam);
        boolean productionCounterfactual = state.getCompositionRuntimeState().isProductionV2();
        double blueBaseline = productionCounterfactual
                ? teamfightScoreWithoutComposition(state, TeamSide.BLUE, blueTeam) : blueRuntimeBase;
        double redBaseline = productionCounterfactual
                ? teamfightScoreWithoutComposition(state, TeamSide.RED, redTeam) : redRuntimeBase;
        CompositionActionType action = compositionAction(context);
        CompositionBaselineScoreDomain domain = compositionDomain(context);
        TeamCompositionContext compositionContext = compositionContext(action);
        double blueScore = state.getCompositionRuntimeState().adjustedScoreForCandidate(TeamSide.BLUE,
                compositionContext, action, domain, blueRuntimeBase, redRuntimeBase);
        double redScore = state.getCompositionRuntimeState().adjustedScoreForCandidate(TeamSide.RED,
                compositionContext, action, domain, redRuntimeBase, blueRuntimeBase);
        double killContribution = Math.max(-CombatRealismRuleConfig.MAX_TEAMFIGHT_KILL_EDGE,
                Math.min(CombatRealismRuleConfig.MAX_TEAMFIGHT_KILL_EDGE,
                        (blue.getKills() - red.getKills())
                                * CombatRealismRuleConfig.TEAMFIGHT_KILL_EDGE_WEIGHT));
        double baselineExisting = goldContribution + killContribution + blueBaseline - redBaseline;
        double existing = goldContribution + killContribution + blueScore - redScore;
        CombatProgressionEvaluator progression = new CombatProgressionEvaluator();
        List<PlayerState> blueEligible = eligibleParticipants(blue, state.getCurrentTimeSeconds());
        List<PlayerState> redEligible = eligibleParticipants(red, state.getCurrentTimeSeconds());
        CombatProgressionBreakdown baselineBreakdown;
        CombatProgressionBreakdown historicalBreakdown;
        if (productionCounterfactual) {
            baselineBreakdown = progression.evaluatePure(state, context, blueEligible, redEligible,
                    baselineExisting, goldContribution, ProgressionApplicationStage.COMBAT_SCORE);
            historicalBreakdown = progression.evaluateAndRecord(state, context, blueEligible, redEligible,
                    existing, goldContribution, ProgressionApplicationStage.COMBAT_SCORE,
                    actionId);
        } else {
            historicalBreakdown = progression.evaluateAndRecord(state, context, blueEligible, redEligible,
                    existing, goldContribution, ProgressionApplicationStage.COMBAT_SCORE,
                    actionId);
            baselineBreakdown = historicalBreakdown;
        }
        ProgressionCombatSample runtimeProgressionSample = state.getProgressionExecutionStats()
                .snapshot().combatSamples().getLast();
        double baselineScoreWithoutNoise = baselineExisting + baselineBreakdown.finalContribution();
        double historicalScoreWithoutNoise = existing + historicalBreakdown.finalContribution();
        double exactExistingNonScalarComposition = (blueRuntimeBase - blueBaseline)
                - (redRuntimeBase - redBaseline);
        CompositionCombatRole blueRole = context == ProgressionCombatContext.BASE_DEFENSE
                ? structuredAttackingSide == TeamSide.BLUE ? CompositionCombatRole.ATTACKER : CompositionCombatRole.DEFENDER
                : CompositionCombatRole.SYMMETRIC;
        double scalarAdjustmentBaseline = productionCounterfactual
                ? historicalScoreWithoutNoise : baselineScoreWithoutNoise;
        CompositionWinnerDecisionAdjustment auditAdjustment = state.getCompositionRuntimeState().auditWinnerAdjustment(
                TeamSide.BLUE, compositionContext, action, domain, scalarAdjustmentBaseline, blueRole);
        double rawScoreWithoutNoise = state.getCompositionRuntimeState().isAuditSemantics()
                || state.getCompositionRuntimeState().isProductionV2()
                ? auditAdjustment.winnerDecisionGap() : historicalScoreWithoutNoise;
        double scoreWithoutNoise = clampTeamfightDecisionEdge(rawScoreWithoutNoise);
        double baselineDecisionScore = clampTeamfightDecisionEdge(baselineScoreWithoutNoise);
        double sample = random.nextDouble();
        long sampleOrdinal = randomDrawOrdinal(random);
        CombatOutcomeProbabilityEvaluator evaluator = new CombatOutcomeProbabilityEvaluator();
        double baselinePressure = evaluator.resolveUniformAdvantageScore(baselineDecisionScore, sample);
        double pressure = evaluator.resolveUniformAdvantageScore(scoreWithoutNoise, sample);
        LocalDecision local = new LocalDecision(sample, sampleOrdinal, baselineDecisionScore, scoreWithoutNoise,
                baselinePressure >= 0 ? "BLUE" : "RED", pressure >= 0 ? "BLUE" : "RED",
                compositionContext.name() + "|" + action.name() + "|" + domain.name());
        DecisionFactorCapture factors = captureDecisionFactors(state, blueRuntimeBase, redRuntimeBase,
                blueBaseline, redBaseline, goldContribution,
                killContribution, runtimeProgressionSample, historicalBreakdown, auditAdjustment,
                exactExistingNonScalarComposition);
        double baselineClampDelta = baselineDecisionScore - baselineScoreWithoutNoise;
        double candidateClampDelta = scoreWithoutNoise - rawScoreWithoutNoise;
        double clampEffect = candidateClampDelta - baselineClampDelta;
        return pressure >= 0
                ? new TeamfightSides(TeamSide.BLUE, blueTeam, blue, redTeam, red, pressure,
                        Math.abs(baselinePressure), local, auditAdjustment, structuredAttackingSide, factors,
                        baselineScoreWithoutNoise, rawScoreWithoutNoise,
                        exactExistingNonScalarComposition, baselineClampDelta,
                        candidateClampDelta, clampEffect)
                : new TeamfightSides(TeamSide.RED, redTeam, red, blueTeam, blue, Math.abs(pressure),
                        Math.abs(baselinePressure), local, auditAdjustment, structuredAttackingSide, factors,
                        baselineScoreWithoutNoise, rawScoreWithoutNoise,
                        exactExistingNonScalarComposition, baselineClampDelta,
                        candidateClampDelta, clampEffect);
    }

    private DecisionFactorCapture captureDecisionFactors(
            GameState state, double blueRuntimeBase, double redRuntimeBase,
            double blueBaseline, double redBaseline, double goldContribution, double killContribution,
            ProgressionCombatSample progressionSample, CombatProgressionBreakdown breakdown,
            CompositionWinnerDecisionAdjustment adjustment,
            double exactExistingNonScalarComposition) {
        List<CompositionDecisionScoreStage> stages = new ArrayList<>();
        double score = 0.0;
        double base = blueBaseline - redBaseline;
        stages.add(new CompositionDecisionScoreStage("PLAYER_OR_TEAM_BASE_POWER", score, base, base, score += base, CompositionFactorAvailability.EXACT_RUNTIME_COMPONENT));
        double existingComposition = exactExistingNonScalarComposition;
        stages.add(new CompositionDecisionScoreStage("EXISTING_COMPOSITION_SUPPORT_TOOL", score,
                existingComposition, existingComposition, score += existingComposition,
                CompositionFactorAvailability.EXACT_RUNTIME_COMPONENT));
        stages.add(new CompositionDecisionScoreStage("ECONOMY_GOLD", score, goldContribution, goldContribution, score += goldContribution, CompositionFactorAvailability.EXACT_RUNTIME_COMPONENT));
        stages.add(new CompositionDecisionScoreStage("CURRENT_GAME_STATE_KILLS", score, killContribution, killContribution, score += killContribution, CompositionFactorAvailability.EXACT_RUNTIME_COMPONENT));
        stages.add(new CompositionDecisionScoreStage("PROGRESSION", score, breakdown.progressionEdge(), breakdown.commonProgressionContribution(), score += breakdown.commonProgressionContribution(), CompositionFactorAvailability.EXACT_RUNTIME_COMPONENT));
        stages.add(new CompositionDecisionScoreStage("CHAMPION_CURRENT_POWER", score, breakdown.championBreakdown().rawChampionEdge(), breakdown.championContribution(), score += breakdown.championContribution(), CompositionFactorAvailability.EXACT_RUNTIME_COMPONENT));
        stages.add(new CompositionDecisionScoreStage("CHAMPION_MATCHUP", score, breakdown.championMatchupBreakdown().matchupRawEdgeSum(), breakdown.championMatchupContribution(), score += breakdown.championMatchupContribution(), CompositionFactorAvailability.EXACT_RUNTIME_COMPONENT));
        stages.add(new CompositionDecisionScoreStage("COMPOSITION", score, adjustment.rawEdge(), adjustment.winnerModifier(), score += adjustment.winnerModifier(), CompositionFactorAvailability.EXACT_RUNTIME_COMPONENT));
        TeamState blue = state.getBlueTeamState(), red = state.getRedTeamState();
        MapState map = state.getMapState(); int time = state.getCurrentTimeSeconds();
        return new DecisionFactorCapture(blue.getGold(), red.getGold(), blue.getKills(), red.getKills(),
                countAlivePlayers(blue, time), countAlivePlayers(red, time), blueRuntimeBase, redRuntimeBase, goldContribution,
                killContribution, progressionSample.levelContribution(), progressionSample.itemContribution(),
                breakdown.commonProgressionContribution(), breakdown.championContribution(), breakdown.championMatchupContribution(),
                state.getObjectiveState().isSoulOwner(TeamSide.BLUE), state.getObjectiveState().isSoulOwner(TeamSide.RED),
                blue.hasActiveBaronBuff(time), red.hasActiveBaronBuff(time), activeElderPlayers(blue, time) > 0, activeElderPlayers(red, time) > 0,
                map.getDestroyedTowerCountByAttackingSide(TeamSide.BLUE), map.getDestroyedTowerCountByAttackingSide(TeamSide.RED),
                map.getAliveInhibitorCount(TeamSide.BLUE), map.getAliveInhibitorCount(TeamSide.RED),
                map.getBaseState(TeamSide.BLUE).getNexusTurretsRemaining(), map.getBaseState(TeamSide.RED).getNexusTurretsRemaining(),
                map.getBaseState(TeamSide.BLUE).isNexusAlive(), map.getBaseState(TeamSide.RED).isNexusAlive(), List.copyOf(stages));
    }

    private void recordWinnerDecisionProvenance(GameState state, TeamfightSides sides, GameplayAttemptId attemptId,
                                                 TeamCompositionContext context, CompositionActionType action,
                                                 CompositionBaselineScoreDomain domain) {
        DecisionFactorCapture f = sides.factors();
        CombatOutcomeProbabilityEvaluator evaluator = new CombatOutcomeProbabilityEvaluator();
        TeamSide attacker = sides.structuredAttackingSide();
        TeamSide defender = attacker == null ? null : attacker.opposite();
        state.getCompositionRuntimeState().recordWinnerDecisionProvenance(new CompositionWinnerDecisionProvenance(
                state.getCompositionRuntimeState().matchSeed(), state.getCompositionRuntimeState().isAuditSemantics()
                        ? state.getCompositionRuntimeState().semanticsAuditAuthorization().diagnosticCaseIndex() : -1,
                attemptId, context.name() + "|" + action.name() + "|" + domain.name(), context, action, domain,
                state.getCurrentTimeSeconds(), TeamSide.BLUE, CompositionScoreOrientation.BLUE_MINUS_RED,
                attacker, defender, sides.auditAdjustment().perspectiveRole(),
                CompositionRuntimeDecisionKind.UNIFORM_NOISE_THRESHOLD,
                CompositionRuntimeComparisonOperator.NOISY_SCORE_GREATER_THAN_OR_EQUAL_ZERO,
                sides.localDecision().baselineScore(), sides.auditAdjustment().rawEdge(), sides.auditAdjustment().referenceGain(),
                sides.auditAdjustment().winnerModifier(), sides.localDecision().candidateScore(),
                sides.baselineScoreBeforeClamp(), sides.candidateScoreBeforeClamp(),
                sides.existingNonScalarCompositionComponent(), sides.baselineClampDelta(),
                sides.candidateClampDelta(), sides.clampEffect(),
                evaluator.uniformAdvantageProbability(sides.localDecision().baselineScore()),
                evaluator.uniformAdvantageProbability(sides.localDecision().candidateScore()), sides.localDecision().sample(),
                sides.localDecision().sampleIdentity(),
                .5 - sides.localDecision().candidateScore() / CombatOutcomeProbabilityEvaluator.UNIFORM_ADVANTAGE_SPAN,
                TeamSide.valueOf(sides.localDecision().baselineDecision()), sides.winningSide(),
                f.blueGold(), f.redGold(), f.blueKills(), f.redKills(), f.blueAlive(), f.redAlive(),
                f.blueBaseTeamPower(), f.redBaseTeamPower(), f.goldContribution(), f.killContribution(),
                f.levelContribution(), f.itemContribution(), f.progressionContribution(), f.championPowerContribution(), f.matchupContribution(),
                f.blueDragonSoul(), f.redDragonSoul(), f.blueBaronBuff(), f.redBaronBuff(), f.blueElderBuff(), f.redElderBuff(),
                f.blueTowersDestroyed(), f.redTowersDestroyed(), f.blueInhibitorsRemaining(), f.redInhibitorsRemaining(),
                f.blueNexusTurretsRemaining(), f.redNexusTurretsRemaining(), f.blueNexusAlive(), f.redNexusAlive(),
                CompositionFactorAvailability.EXACT_RUNTIME_COMPONENT, CompositionFactorAvailability.EXACT_RUNTIME_COMPONENT,
                CompositionFactorAvailability.EXACT_RUNTIME_COMPONENT, CompositionFactorAvailability.EXACT_RUNTIME_COMPONENT, f.stages()));
    }

    private CompositionActionType compositionAction(ProgressionCombatContext context) {
        return context == ProgressionCombatContext.BASE_DEFENSE ? CompositionActionType.BASE_DEFENSE
                : context == ProgressionCombatContext.LATE_GAME_SIEGE ? CompositionActionType.SIEGE_COMBAT
                : CompositionActionType.TEAMFIGHT;
    }

    private CompositionBaselineScoreDomain compositionDomain(ProgressionCombatContext context) {
        return context == ProgressionCombatContext.BASE_DEFENSE ? CompositionBaselineScoreDomain.BASE_DEFENSE_SCORE
                : context == ProgressionCombatContext.LATE_GAME_SIEGE ? CompositionBaselineScoreDomain.SIEGE_PUSH_SCORE
                : CompositionBaselineScoreDomain.TEAMFIGHT_COMBAT_SCORE;
    }

    private TeamCompositionContext compositionContext(CompositionActionType action) {
        return action == CompositionActionType.BASE_DEFENSE ? TeamCompositionContext.BASE_DEFENSE
                : action == CompositionActionType.SIEGE_COMBAT ? TeamCompositionContext.SIEGE
                : TeamCompositionContext.TEAMFIGHT;
    }

    private List<PlayerState> eligibleParticipants(TeamState team, int time) {
        return team.getPlayers().stream()
                .filter(player -> player.canParticipateInMajorCombatAt(time))
                .toList();
    }

    private double clampTeamfightDecisionEdge(double value) {
        return Math.max(-CombatRealismRuleConfig.MAX_TEAMFIGHT_DECISION_EDGE,
                Math.min(CombatRealismRuleConfig.MAX_TEAMFIGHT_DECISION_EDGE, value));
    }

    double teamfightScore(GameState state, TeamSide side, Team team) {
        return teamfightScore(state, side, team, true);
    }

    double teamfightScoreWithoutComposition(GameState state, TeamSide side, Team team) {
        return teamfightScore(state, side, team, false);
    }

    private double teamfightScore(GameState state, TeamSide side, Team team,
                                  boolean useRuntimeCompositionTools) {
        TeamState teamState = state.getTeamState(side);
        int currentTime = state.getCurrentTimeSeconds();
        int alive = 0;
        double totalTeamfighting = 0.0;
        double totalMechanics = 0.0;
        for (PlayerState player : teamState.getPlayers()) {
            if (!player.canParticipateInMajorCombatAt(currentTime)) continue;
            alive++;
            if (player.hasMatchPerformance()) {
                totalTeamfighting += playerSkills.combatExecution(player);
                totalMechanics += playerSkills.exposureSafety(player);
            } else {
                totalTeamfighting += player.getTeamfighting();
                totalMechanics += player.getMechanics();
            }
        }
        if (alive == 0) return 0.0;
        double score = totalTeamfighting / alive * PlayerImpactRuleConfig.TEAMFIGHTING_SCORE_WEIGHT
                + totalMechanics / alive * PlayerImpactRuleConfig.TEAMFIGHT_MECHANICS_SCORE_WEIGHT
                + alive * PlayerImpactRuleConfig.ALIVE_PLAYER_SCORE_WEIGHT;
        score += useRuntimeCompositionTools
                ? supportToolExecution(state, side) : supportToolExecutionWithoutComposition(state, side);
        if (state.getObjectiveState().isSoulOwner(side)) score += DragonSoulRuleConfig.SOUL_TEAMFIGHT_SCORE_BONUS;
        if (teamState.hasActiveBaronBuff(currentTime)) score += PlayerImpactRuleConfig.BARON_TEAMFIGHT_SCORE_BONUS;
        score += Math.min(ElderRuleConfig.MAX_TEAMFIGHT_SCORE_BONUS, activeElderPlayers(teamState, currentTime) * ElderRuleConfig.TEAMFIGHT_SCORE_BONUS_PER_PLAYER);
        return score;
    }

    private GradeDecision determineFightGrade(GameState state, TeamfightSides sides, Random random,
                                               ProgressionCombatContext context, GameplayAttemptId compositionAttemptId,
                                               String actionId) {
        int currentTime = state.getCurrentTimeSeconds();
        int goldLead = Math.max(0, sides.winningTeamState().getGold() - sides.losingTeamState().getGold());
        boolean productionCounterfactual = state.getCompositionRuntimeState().isProductionV2();
        double runtimeWinningScore = teamfightScore(state, sides.winningSide(), sides.winningTeam());
        double runtimeLosingScore = teamfightScore(state, sides.winningSide().opposite(), sides.losingTeam());
        double baselineWinningScore = productionCounterfactual
                ? teamfightScoreWithoutComposition(state, sides.winningSide(), sides.winningTeam())
                : runtimeWinningScore;
        double baselineLosingScore = productionCounterfactual
                ? teamfightScoreWithoutComposition(state, sides.winningSide().opposite(), sides.losingTeam())
                : runtimeLosingScore;
        double baselineGap = baselineWinningScore - baselineLosingScore;
        double historicalCandidateGap = state.getCompositionRuntimeState().adjustedGapFor(
                compositionAttemptId, runtimeWinningScore, runtimeLosingScore);
        CombatProgressionEvaluator progression = new CombatProgressionEvaluator();
        List<PlayerState> winningEligible = eligibleParticipants(sides.winningTeamState(), currentTime);
        List<PlayerState> losingEligible = eligibleParticipants(sides.losingTeamState(), currentTime);
        double baselineProgression;
        double historicalProgression;
        if (productionCounterfactual) {
            baselineProgression = progression.evaluatePure(state, context, winningEligible,
                    losingEligible, baselineGap, 0, ProgressionApplicationStage.FIGHT_GRADE)
                    .finalContribution();
            historicalProgression = progression.evaluateAndRecord(state, context, winningEligible,
                    losingEligible, historicalCandidateGap, 0,
                    ProgressionApplicationStage.FIGHT_GRADE, actionId)
                    .finalContribution();
        } else {
            historicalProgression = progression.evaluateAndRecord(state, context, winningEligible,
                    losingEligible, historicalCandidateGap, 0,
                    ProgressionApplicationStage.FIGHT_GRADE, actionId)
                    .finalContribution();
            baselineProgression = historicalProgression;
        }
        double baselineTeamfightGap = Math.max(0.0, baselineGap + baselineProgression);
        double historicalCandidateTeamfightGap = Math.max(0.0,
                historicalCandidateGap + historicalProgression);
        CompositionActionType action = compositionAction(context);
        CompositionBaselineScoreDomain domain = compositionDomain(context);
        TeamCompositionContext compositionContext = compositionContext(action);
        boolean audit = state.getCompositionRuntimeState().isAuditSemantics();
        CompositionSeverityDecisionAdjustment severity = state.getCompositionRuntimeState().auditSeverityAdjustment(
                compositionContext, action, domain, baselineTeamfightGap);
        double actualTeamfightGap = audit ? severity.finalSeverityInput() : historicalCandidateTeamfightGap;
        double lateBonus = currentTime >= 2_100 ? 0.018 : currentTime >= 1_800 ? 0.012 : currentTime >= 1_500 ? 0.008 : 0.0;
        double objectiveBonus = isMajorObjectiveMoment(currentTime) ? 0.01 : 0.0;
        double historicalDominanceBonus = Math.min(0.025, sides.advantageScore() / 1_800.0);
        double baselineDominanceBonus = Math.min(0.025, sides.baselineAdvantageScore() / 1_800.0);
        double actualDominanceBonus = audit ? baselineDominanceBonus : historicalDominanceBonus;
        double elderAceBonus = activeElderPlayers(sides.winningTeamState(), currentTime) > 0 ? ElderRuleConfig.ACE_CHANCE_BONUS : 0.0;
        double elderBigBonus = activeElderPlayers(sides.winningTeamState(), currentTime) > 0 ? ElderRuleConfig.BIG_WIN_CHANCE_BONUS : 0.0;
        double baselineAceChance = Math.min(0.10, 0.005 + elderAceBonus + goldLead / 400_000.0
                + baselineTeamfightGap / PlayerImpactRuleConfig.TEAMFIGHT_GRADE_GAP_DIVISOR
                + lateBonus + objectiveBonus + baselineDominanceBonus);
        double actualAceChance = Math.min(0.10, 0.005 + elderAceBonus + goldLead / 400_000.0
                + actualTeamfightGap / PlayerImpactRuleConfig.TEAMFIGHT_GRADE_GAP_DIVISOR
                + lateBonus + objectiveBonus + actualDominanceBonus);
        long beforeDrawOrdinal = randomDrawOrdinal(random);
        double aceSample = random.nextDouble();
        long aceOrdinal = Math.max(1L, randomDrawOrdinal(random));
        List<FightGradeBranchDiagnostic> branches = new ArrayList<>();
        branches.add(FightGradeBranchDiagnostic.drawn("ACE", actualAceChance, aceSample, aceOrdinal));
        boolean baselineAce = aceSample < baselineAceChance;
        boolean actualAce = aceSample < actualAceChance;
        boolean changed = baselineAce != actualAce;
        FightGrade selected;
        String baselineDecision;
        if (actualAce) {
            selected = FightGrade.ACE;
            baselineDecision = baselineAce ? "ACE" : "NOT_ACE";
            branches.add(FightGradeBranchDiagnostic.notReached("BIG_WIN"));
            branches.add(FightGradeBranchDiagnostic.notReached("NORMAL_WIN"));
        } else {
            double baselineBigChance = Math.min(0.42, 0.18 + elderBigBonus + goldLead / 260_000.0
                    + baselineTeamfightGap / PlayerImpactRuleConfig.TEAMFIGHT_GRADE_GAP_DIVISOR
                    + lateBonus + objectiveBonus + baselineDominanceBonus);
            double actualBigChance = Math.min(0.42, 0.18 + elderBigBonus + goldLead / 260_000.0
                    + actualTeamfightGap / PlayerImpactRuleConfig.TEAMFIGHT_GRADE_GAP_DIVISOR
                    + lateBonus + objectiveBonus + actualDominanceBonus);
            double bigSample = random.nextDouble();
            long bigOrdinal = Math.max(aceOrdinal + 1L, randomDrawOrdinal(random));
            branches.add(FightGradeBranchDiagnostic.drawn("BIG_WIN", actualBigChance, bigSample, bigOrdinal));
            boolean baselineBig = bigSample < baselineBigChance;
            boolean actualBig = bigSample < actualBigChance;
            changed |= baselineBig != actualBig;
            if (actualBig) {
                selected = FightGrade.BIG_WIN;
                baselineDecision = baselineAce ? "ACE" : baselineBig ? "BIG_WIN" : "NOT_BIG_WIN";
                branches.add(FightGradeBranchDiagnostic.notReached("NORMAL_WIN"));
            } else {
                double baselineNormalChance = Math.min(0.78, 0.46 + goldLead / 320_000.0
                        + baselineTeamfightGap / PlayerImpactRuleConfig.TEAMFIGHT_GRADE_GAP_DIVISOR
                        + baselineDominanceBonus);
                double actualNormalChance = Math.min(0.78, 0.46 + goldLead / 320_000.0
                        + actualTeamfightGap / PlayerImpactRuleConfig.TEAMFIGHT_GRADE_GAP_DIVISOR
                        + actualDominanceBonus);
                double normalSample = random.nextDouble();
                long normalOrdinal = Math.max(bigOrdinal + 1L, randomDrawOrdinal(random));
                branches.add(FightGradeBranchDiagnostic.drawn("NORMAL_WIN", actualNormalChance, normalSample, normalOrdinal));
                boolean baselineNormal = normalSample < baselineNormalChance;
                boolean actualNormal = normalSample < actualNormalChance;
                changed |= baselineNormal != actualNormal;
                selected = actualNormal ? FightGrade.NORMAL_WIN : FightGrade.SMALL_WIN;
                baselineDecision = baselineAce ? "ACE" : baselineBig ? "BIG_WIN"
                        : baselineNormal ? "NORMAL_WIN" : "SMALL_WIN";
            }
        }
        if (audit) {
            double legacyGain = switch (compositionContext) {
                case TEAMFIGHT -> FrozenCompositionGameplayGainPolicy.TEAMFIGHT_GAIN;
                case SIEGE -> FrozenCompositionGameplayGainPolicy.SIEGE_GAIN;
                case BASE_DEFENSE -> FrozenCompositionGameplayGainPolicy.BASE_DEFENSE_GAIN;
                default -> 0.0;
            };
            double legacyGapContribution = legacyGain * state.getCompositionRuntimeState()
                    .edgeFor(sides.winningSide(), compositionContext);
            double legacyBlueScore = sides.localDecision().baselineScore()
                    + legacyGain
                    * state.getCompositionRuntimeState().edgeFor(TeamSide.BLUE, compositionContext);
            double legacyPressure = new CombatOutcomeProbabilityEvaluator().resolveUniformAdvantageScore(
                    legacyBlueScore, sides.localDecision().sample());
            double legacyDominance = Math.min(0.025, Math.abs(legacyPressure) / 1_800.0);
            double legacyDominanceContribution = legacyDominance - baselineDominanceBonus;
            int drawCount = (int) branches.stream()
                    .filter(x -> x.drawState() == FightGradeBranchDrawState.DRAWN).count();
            FightGradeCounterfactualCoverageClass coverage = drawCount == 3
                    ? FightGradeCounterfactualCoverageClass.FULL_FOR_ACTUAL_REACHED_BRANCHES
                    : FightGradeCounterfactualCoverageClass.PARTIAL_UNOBSERVED_LATER_BRANCH_RANDOM;
            TeamSide attacker = sides.structuredAttackingSide();
            state.getCompositionRuntimeState().recordFightGradeDiagnostic(new FightGradeDecisionDiagnostic(
                    state.getCompositionRuntimeState().matchSeed(),
                    state.getCompositionRuntimeState().semanticsAuditAuthorization().diagnosticCaseIndex(),
                    compositionAttemptId, compositionContext, action, domain, currentTime, sides.winningSide(),
                    sides.winningSide().opposite(), attacker, attacker == null ? null : attacker.opposite(),
                    baselineTeamfightGap, sides.baselineAdvantageScore(), baselineDominanceBonus,
                    sides.auditAdjustment().winnerModifier(), severity.severityModifier(), severity.finalSeverityInput(),
                    legacyGapContribution, legacyDominanceContribution,
                    legacyGapContribution + legacyDominanceContribution, branches, selected,
                    beforeDrawOrdinal < 0 ? aceOrdinal : beforeDrawOrdinal + 1, drawCount, 0,
                    sides.auditAdjustment().winnerModifier() != 0.0, false, coverage,
                    reconstructGrade(branches) == selected));
        }
        return new GradeDecision(selected, baselineDecision, gradeName(selected), changed);
    }

    private static FightGrade reconstructGrade(List<FightGradeBranchDiagnostic> branches) {
        FightGradeBranchDiagnostic ace = branches.get(0);
        if (ace.randomSample() < ace.threshold()) return FightGrade.ACE;
        FightGradeBranchDiagnostic big = branches.get(1);
        if (big.drawState() == FightGradeBranchDrawState.DRAWN && big.randomSample() < big.threshold()) return FightGrade.BIG_WIN;
        FightGradeBranchDiagnostic normal = branches.get(2);
        return normal.drawState() == FightGradeBranchDrawState.DRAWN && normal.randomSample() < normal.threshold()
                ? FightGrade.NORMAL_WIN : FightGrade.SMALL_WIN;
    }

    private static String gradeName(FightGrade grade) {
        return switch (grade) {
            case ACE -> "ACE";
            case BIG_WIN -> "BIG_WIN";
            case NORMAL_WIN -> "NORMAL_WIN";
            case SMALL_WIN -> "SMALL_WIN";
        };
    }

    private boolean hasElderTeamWithThreeParticipants(
            List<PlayerState> blueParticipants,
            List<PlayerState> redParticipants,
            int time
    ) {
        return activeElderPlayers(blueParticipants, time) >= 3
                || activeElderPlayers(redParticipants, time) >= 3;
    }

    private double supportToolExecution(GameState state, TeamSide side) {
        CompositionRuntimeState runtime = state.getCompositionRuntimeState();
        PlayerState support = state.getTeamState(side).playerAt(Position.SUPPORT);
        if (!support.canParticipateInMajorCombatAt(state.getCurrentTimeSeconds())) return 0.0;
        if (!runtime.isActive() || !runtime.initialized()) {
            return supportToolExecutionWithoutComposition(state, side);
        }
        TeamCompositionAnalysis analysis = side == TeamSide.BLUE ? runtime.blueAnalysis() : runtime.redAnalysis();
        double engageTool = supportCapability(analysis, CompositionCapability.ENGAGE);
        double peelTool = supportCapability(analysis, CompositionCapability.PEEL);
        return (playerSkills.engageExecution(support) - 14) * engageTool * .30
                + (playerSkills.allyProtection(support) - 14) * peelTool * .30;
    }

    private double supportToolExecutionWithoutComposition(GameState state, TeamSide side) {
        PlayerState support = state.getTeamState(side).playerAt(Position.SUPPORT);
        if (!support.canParticipateInMajorCombatAt(state.getCurrentTimeSeconds())) return 0.0;
        return (playerSkills.engageExecution(support) - PlayerImpactRuleConfig.BASELINE_ATTRIBUTE)
                * CombatParticipantRuleConfig.COMPOSITION_OFF_ENGAGE_SCORE_PER_POINT
                + (playerSkills.allyProtection(support) - PlayerImpactRuleConfig.BASELINE_ATTRIBUTE)
                * CombatParticipantRuleConfig.COMPOSITION_OFF_PROTECTION_SCORE_PER_POINT;
    }

    private double supportCapability(TeamCompositionAnalysis analysis, CompositionCapability capability) {
        return analysis.coverage().capability(capability).contributors().stream()
                .filter(contributor -> contributor.position() == Position.SUPPORT)
                .mapToDouble(CapabilityContributor::normalizedValue)
                .findFirst().orElse(0.0);
    }

    private int activeElderPlayers(TeamState team, int time) {
        return activeElderPlayers(eligibleParticipants(team, time), time);
    }

    private int activeElderPlayers(List<PlayerState> participants, int time) {
        return (int) participants.stream()
                .filter(player -> player.hasActiveElderBuff(time))
                .count();
    }

    private FightGrade resolveFightGrade(FightGrade planned, int winningKills, int losingKills) {
        if (winningKills == 5) {
            return FightGrade.ACE;
        }
        if (winningKills >= 4) {
            return FightGrade.BIG_WIN;
        }
        if (winningKills == 3) {
            return losingKills == 0 ? FightGrade.BIG_WIN : FightGrade.NORMAL_WIN;
        }
        if (winningKills == 2) {
            return FightGrade.NORMAL_WIN;
        }
        return FightGrade.SMALL_WIN;
    }

    private int determineWinningTeamKillCount(FightGrade grade, Random random) {
        return switch (grade) {
            case SMALL_WIN -> 1 + random.nextInt(2);
            case NORMAL_WIN -> 2 + random.nextInt(2);
            case BIG_WIN -> 3 + random.nextInt(2);
            case ACE -> 5;
        };
    }

    private int determineCounterKillCount(FightGrade grade, int winningKillTarget, Random random) {
        if (winningKillTarget < 2 || grade == FightGrade.SMALL_WIN) {
            return 0;
        }
        double chance = switch (grade) {
            case NORMAL_WIN -> 0.34;
            case BIG_WIN -> 0.22;
            case ACE -> 0.12;
            case SMALL_WIN -> 0.0;
        };
        return random.nextDouble() < chance ? 1 : 0;
    }

    private List<Player> filterEligiblePlayers(
            List<Player> players, TeamState state, int time, Set<PlayerState> blocked,
            Set<Position> allowedPositions
    ) {
        List<Player> candidates = new ArrayList<>();
        for (Player player : players) {
            PlayerState playerState = state.playerAt(player.getPosition());
            if ((allowedPositions == null || allowedPositions.contains(player.getPosition()))
                    && !blocked.contains(playerState)
                    && playerState.canParticipateInMajorCombatAt(time)) {
                candidates.add(player);
            }
        }
        return candidates;
    }

    private List<Player> pickAssistPlayers(
            Team team, TeamState state, Player killer, int time, Random random, boolean teamfight,
            Set<PlayerState> deadPlayers,
            Set<Position> allowedPositions
    ) {
        List<Player> candidates = new ArrayList<>();
        PlayerState killerState = state.playerAt(killer.getPosition());
        for (Player player : team.getPlayers()) {
            PlayerState playerState = state.playerAt(player.getPosition());
            if (playerState != killerState
                    && (allowedPositions == null || allowedPositions.contains(player.getPosition()))
                    && !deadPlayers.contains(playerState)
                    && playerState.canParticipateInMajorCombatAt(time)) {
                candidates.add(player);
            }
        }
        int minimum = teamfight ? 2 : 1;
        int maximum = Math.min(candidates.size(), teamfight ? 4 : 3);
        int assistCount = maximum < minimum ? maximum
                : minimum + random.nextInt(maximum - minimum + 1);
        List<Player> assists = new ArrayList<>();
        for (int index = 0; index < assistCount && !candidates.isEmpty(); index++) {
            List<PlayerState> candidateStates = candidates.stream()
                    .map(player -> state.playerAt(player.getPosition())).toList();
            PlayerState selected = participantSelector.selectAssist(candidateStates, random);
            assists.add(candidates.remove(indexOfPosition(candidates, selected.getPosition())));
        }
        return assists;
    }

    private Player playerAtPosition(List<Player> players, Position position) {
        return players.stream().filter(player -> player.getPosition() == position)
                .findFirst().orElseThrow();
    }

    private int indexOfPosition(List<Player> players, Position position) {
        for (int index = 0; index < players.size(); index++) {
            if (players.get(index).getPosition() == position) return index;
        }
        throw new IllegalArgumentException("No combat participant at " + position);
    }

    private Set<Position> localizedPositions(Lane lane) {
        return switch (lane) {
            case TOP -> java.util.EnumSet.of(Position.TOP, Position.JUNGLE);
            case MID -> java.util.EnumSet.of(Position.JUNGLE, Position.MID, Position.SUPPORT);
            case BOT -> java.util.EnumSet.of(Position.JUNGLE, Position.ADC, Position.SUPPORT);
        };
    }

    private Lane laneForLocalizedPositions(Set<Position> positions) {
        if (positions.contains(Position.ADC)) return Lane.BOT;
        if (positions.contains(Position.MID)) return Lane.MID;
        return Lane.TOP;
    }

    private int countAlivePlayers(TeamState state, int time) {
        int count = 0;
        for (PlayerState player : state.getPlayers()) {
            if (player.isAlive(time)) {
                count++;
            }
        }
        return count;
    }

    int calculateRespawnDelaySeconds(int time) {
        if (time < 600) return RespawnRuleConfig.BEFORE_10_MINUTES_SECONDS;
        if (time < 1_200) return RespawnRuleConfig.FROM_10_TO_20_MINUTES_SECONDS;
        if (time < 1_800) return RespawnRuleConfig.FROM_20_TO_30_MINUTES_SECONDS;
        if (time < 2_100) return RespawnRuleConfig.FROM_30_TO_35_MINUTES_SECONDS;
        if (time < 2_400) return RespawnRuleConfig.FROM_35_TO_40_MINUTES_SECONDS;
        if (time < 2_700) return RespawnRuleConfig.FROM_40_TO_45_MINUTES_SECONDS;
        if (time < 3_000) return RespawnRuleConfig.FROM_45_TO_50_MINUTES_SECONDS;
        return RespawnRuleConfig.FROM_50_MINUTES_SECONDS;
    }

    private boolean isMajorObjectiveMoment(int time) {
        return isNearObjectiveCycle(time, 300, 40) || isNearObjectiveCycle(time, 360, 40);
    }

    private boolean isNearObjectiveCycle(int time, int cycle, int window) {
        if (time < cycle) return false;
        int offset = time % cycle;
        return offset <= window || offset >= cycle - window;
    }

    private String buildTeamfightStartMessage(int time, Random random, String winner) {
        String[] messages = isMajorObjectiveMoment(time)
                ? new String[] {winner + "가 오브젝트 앞에서 좋은 구도를 만들며 대규모 한타를 엽니다.", winner + "가 먼저 자리를 잡고 한타를 설계합니다."}
                : new String[] {winner + "가 강하게 이니시에이팅을 걸며 대규모 한타를 엽니다.", winner + "가 먼저 진형을 파고들며 한타를 시작합니다."};
        return messages[random.nextInt(messages.length)];
    }

    private String buildTeamfightResultMessage(String winner, FightGrade grade, int winningKills, int losingKills) {
        if (winningKills == 0) return winner + "가 한타 주도권만 챙긴 채 교전을 정리합니다.";
        return switch (grade) {
            case SMALL_WIN -> winner + "가 짧은 교전에서 " + winningKills + "킬을 챙깁니다.";
            case NORMAL_WIN -> losingKills > 0 ? winner + "가 치열한 한타 끝에 " + winningKills + "킬을 가져갑니다."
                    : winner + "가 한타에서 " + winningKills + "킬을 기록하며 이득을 봅니다.";
            case BIG_WIN -> winner + "가 한타에서 " + winningKills + "킬을 기록하며 대승합니다.";
            case ACE -> buildAceMessage(winner);
        };
    }

    private String buildAceMessage(String winner) {
        return winner + "가 에이스를 띄우며 경기를 크게 굴립니다.";
    }

    private String buildKillMessage(String killer, String victim, List<String> assists) {
        return assists.isEmpty() ? killer + "가 " + victim + "을 잡아냈습니다."
                : killer + "가 " + victim + "을 잡아냈습니다. 합류: " + String.join(", ", assists);
    }

    private record TeamfightSides(
            TeamSide winningSide,
            Team winningTeam,
            TeamState winningTeamState,
            Team losingTeam,
            TeamState losingTeamState,
            double advantageScore,
            double baselineAdvantageScore,
            LocalDecision localDecision,
            CompositionWinnerDecisionAdjustment auditAdjustment,
            TeamSide structuredAttackingSide,
            DecisionFactorCapture factors,
            double baselineScoreBeforeClamp,
            double candidateScoreBeforeClamp,
            double existingNonScalarCompositionComponent,
            double baselineClampDelta,
            double candidateClampDelta,
            double clampEffect
    ) {
    }

    private record DecisionFactorCapture(
            int blueGold, int redGold, int blueKills, int redKills, int blueAlive, int redAlive,
            double blueBaseTeamPower, double redBaseTeamPower, double goldContribution, double killContribution,
            double levelContribution, double itemContribution, double progressionContribution,
            double championPowerContribution, double matchupContribution,
            boolean blueDragonSoul, boolean redDragonSoul, boolean blueBaronBuff, boolean redBaronBuff,
            boolean blueElderBuff, boolean redElderBuff, int blueTowersDestroyed, int redTowersDestroyed,
            int blueInhibitorsRemaining, int redInhibitorsRemaining, int blueNexusTurretsRemaining,
            int redNexusTurretsRemaining, boolean blueNexusAlive, boolean redNexusAlive,
            List<CompositionDecisionScoreStage> stages) {
        private DecisionFactorCapture { stages = List.copyOf(stages); }
    }

    private record GradeDecision(FightGrade candidateGrade, String baselineDecision, String candidateDecision, boolean changed) {}

    private record LocalDecision(
            double sample,
            long sampleIdentity,
            double baselineScore,
            double candidateScore,
            String baselineDecision,
            String candidateDecision,
            String applicationKey
    ) {}
}
