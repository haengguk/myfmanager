package com.lolfm.simulator;

import com.lolfm.domain.*;
import java.util.*;

/** Large A-G macro distribution diagnostics. Excluded from normal test execution. */
public final class MidGameMacroDiagnostics {
    private static final int FULL_SEEDS = 1_000;
    private static final int MIRROR_SEEDS = 500;

    private MidGameMacroDiagnostics() { }

    public static void main(String[] args) {
        printSeed7Audit();
        List<Scenario> scenarios = scenarios();
        Map<String, Report> reports = new LinkedHashMap<>();
        for (Scenario scenario : scenarios) runPair(scenario, FULL_SEEDS, reports);
        for (Scenario scenario : scenarios) if (!scenario.key().equals("A")) {
            runPair(scenario.mirror(), MIRROR_SEEDS, reports);
        }
        reports.values().forEach(report -> System.out.println(report.line()));
        for (Scenario scenario : scenarios) {
            printDelta(reports.get(scenario.key() + ":true"), reports.get(scenario.key() + ":false"));
            if (!scenario.key().equals("A")) {
                Report original = reports.get(scenario.key() + ":true");
                Report mirror = reports.get(scenario.key() + "_MIRROR:true");
                printMirror(original, mirror);
                printDelta(mirror, reports.get(scenario.key() + "_MIRROR:false"));
            }
        }
    }

    private static void printSeed7Audit() {
        Scenario scenario = scenarios().getFirst();
        MatchTimeline timeline = simulator(options(true, true)).simulate(
                scenario.team(TeamSide.BLUE), scenario.team(TeamSide.RED), 7L);
        MatchSnapshot last = timeline.getSnapshots().getLast();
        MidGameMacroSnapshot macro = last.getMidGameMacro();
        System.out.println("SEED7_SUMMARY midGameStartedAt=" + last.getLanePhase().midGameStartedAtSeconds()
                + " duration=" + timeline.getDurationSeconds() + " finalTime=" + last.getTimeSeconds()
                + " blueStatus=" + macro.blueTeam().status() + " redStatus=" + macro.redTeam().status()
                + " blueNext=" + macro.blueTeam().nextEvaluationAtSeconds()
                + " redNext=" + macro.redTeam().nextEvaluationAtSeconds());
        for (MidGameMacroEvaluationData evaluation : macro.evaluationHistory()) {
            System.out.println("SEED7_EVALUATION due=" + evaluation.dueAtSeconds()
                    + " actual=" + evaluation.actualEvaluationAtSeconds()
                    + " blue=" + decisionSummary(evaluation.blueDecision(), evaluation.bluePreviousPlan(),
                            evaluation.blueNextEvaluationAtSeconds())
                    + " red=" + decisionSummary(evaluation.redDecision(), evaluation.redPreviousPlan(),
                            evaluation.redNextEvaluationAtSeconds())
                    + " skipped=" + evaluation.evaluationSkippedReason()
                    + " selectionRandom=" + evaluation.selectionRandomConsumptionCount());
        }
    }

    private static String decisionSummary(MidGameMacroDecisionData decision, TeamMacroPlan previous, int next) {
        if (decision == null) return "{plan=null,started=-1,until=-1,previous=" + previous + ",next=" + next + "}";
        return "{plan=" + decision.selectedPlan() + ",started=" + decision.startedAtSeconds()
                + ",until=" + decision.activeUntilSeconds() + ",previous=" + previous + ",next=" + next + "}";
    }

    private static void runPair(Scenario scenario, int seeds, Map<String, Report> reports) {
        for (boolean enabled : List.of(true, false)) {
            Report report = new Report(scenario.key(), enabled, seeds);
            MatchSimulator simulator = simulator(options(enabled, true));
            for (long seed = 1; seed <= seeds; seed++) {
                report.add(simulator.simulateWithDiagnostics(
                        scenario.team(TeamSide.BLUE), scenario.team(TeamSide.RED), seed));
            }
            MatchTimeline replayA = simulator.simulate(scenario.team(TeamSide.BLUE), scenario.team(TeamSide.RED), 1);
            MatchTimeline replayB = simulator.simulate(scenario.team(TeamSide.BLUE), scenario.team(TeamSide.RED), 1);
            report.replayMismatch = signature(replayA).equals(signature(replayB)) ? 0 : 1;
            MatchTimeline diagnosticOn = simulator(options(enabled, true)).simulate(
                    scenario.team(TeamSide.BLUE), scenario.team(TeamSide.RED), 2);
            MatchTimeline diagnosticOff = simulator(options(enabled, false)).simulate(
                    scenario.team(TeamSide.BLUE), scenario.team(TeamSide.RED), 2);
            report.diagnosticsMismatch = signature(diagnosticOn).equals(signature(diagnosticOff)) ? 0 : 1;
            reports.put(scenario.key() + ":" + enabled, report);
        }
    }

    private static SimulationOptions options(boolean macroEnabled, boolean diagnosticsEnabled) {
        return SimulationOptions.productionDefaults().withMidGameMacroEnabled(macroEnabled)
                .withDiagnosticsEnabled(diagnosticsEnabled);
    }

    private static MatchSimulator simulator(SimulationOptions options) {
        return new MatchSimulator(new TeamfightResolver(), new EndGameEvaluator(), new SnapshotFactory(),
                new ObjectiveResolver(), new PostFightResolver(), new ObjectiveAttemptResolver(),
                new StructureResolver(), new PushResolver(), options);
    }

    private static String signature(MatchTimeline timeline) {
        StringBuilder result = new StringBuilder().append(timeline.getDurationSeconds()).append('|');
        for (MatchEvent event : timeline.getEvents()) result.append(event.getTimeSeconds()).append(':')
                .append(event.getType()).append(':').append(event.getCombatSource()).append(':')
                .append(event.getStructureActionSource()).append(':').append(event.getStructureKind()).append(':')
                .append(event.getStructureTowerTier()).append(':').append(event.getStructureLane()).append(':')
                .append(event.getStructureAttackingSide()).append(':').append(event.getMidGameMacroDecision()).append(':')
                .append(event.getMidGameMacroAction()).append(':').append(event.getObjectivePriorityDecision()).append(';');
        for (MatchSnapshot snapshot : timeline.getSnapshots()) result.append(snapshot.getTimeSeconds()).append(':')
                .append(snapshot.getMidGameMacro()).append(':').append(snapshot.getObjectivePriority()).append(':')
                .append(snapshot.getBlueGold()).append(':').append(snapshot.getRedGold()).append(':')
                .append(snapshot.getPlayerSnapshots().stream().map(PlayerSnapshot::getCs).toList()).append(';');
        return result.toString();
    }

    private static void printDelta(Report on, Report off) {
        System.out.println("DELTA key=" + on.key + " seeds=" + on.games
                + " evaluations=" + (on.blueEvaluations + on.redEvaluations
                    - off.blueEvaluations - off.redEvaluations)
                + " selections=" + (on.totalSelections() - off.totalSelections())
                + " macroTowers=" + (on.macroTowerEvents - off.macroTowerEvents)
                + " setupStarts=" + (on.totalSetupStarts() - off.totalSetupStarts())
                + " avgDuration=" + format(on.averageDuration() - off.averageDuration())
                + " timeout=" + (on.timeouts - off.timeouts)
                + " integrityErrors=" + on.integrityErrors() + "/" + off.integrityErrors());
    }

    private static void printMirror(Report original, Report mirror) {
        for (TeamMacroPlan plan : TeamMacroPlan.values()) {
            System.out.println("MIRROR_CHECK key=" + original.key + " plan=" + plan
                    + " originalBlueWeight=" + format(original.averageWeight(TeamSide.BLUE, plan))
                    + " mirrorRedWeight=" + format(mirror.averageWeight(TeamSide.RED, plan))
                    + " weightDelta=" + format(original.averageWeight(TeamSide.BLUE, plan)
                        - mirror.averageWeight(TeamSide.RED, plan))
                    + " originalBlueSelections=" + original.selections.get(TeamSide.BLUE).get(plan)
                    + " mirrorRedSelections=" + mirror.selections.get(TeamSide.RED).get(plan)
                    + " directionReversed=" + (original.selections.get(TeamSide.BLUE).get(plan) > 0
                        && mirror.selections.get(TeamSide.RED).get(plan) > 0));
        }
    }

    private static List<Scenario> scenarios() {
        Map<Position, Profile> equal = all(Profile.equal());
        List<Scenario> scenarios = new ArrayList<>();
        scenarios.add(new Scenario("A", equal, equal));
        scenarios.add(scenario("B", List.of(Position.TOP), Profile.strong(), Profile.weak()));
        scenarios.add(scenario("C", List.of(Position.MID), Profile.strong(), Profile.weak()));
        scenarios.add(scenario("D", List.of(Position.ADC, Position.SUPPORT), Profile.strong(), Profile.weak()));
        scenarios.add(scenario("E", List.of(Position.JUNGLE, Position.MID, Position.ADC, Position.SUPPORT),
                Profile.teamfightStrong(), Profile.teamfightWeak()));
        scenarios.add(scenario("F", List.of(Position.TOP, Position.MID, Position.ADC),
                Profile.farmingStrong(), Profile.farmingWeak()));
        scenarios.add(scenario("G", List.of(Position.values()), Profile.actionStrong(), Profile.actionWeak()));
        return scenarios;
    }

    private static Scenario scenario(String key, List<Position> positions, Profile blue, Profile red) {
        Map<Position, Profile> b = all(Profile.equal());
        Map<Position, Profile> r = all(Profile.equal());
        for (Position position : positions) { b.put(position, blue); r.put(position, red); }
        return new Scenario(key, b, r);
    }

    private static EnumMap<Position, Profile> all(Profile profile) {
        EnumMap<Position, Profile> result = new EnumMap<>(Position.class);
        for (Position position : Position.values()) result.put(position, profile);
        return result;
    }

    private record Profile(int mechanics, int aggression, int farming, int teamfighting) {
        static Profile equal() { return new Profile(14, 14, 14, 14); }
        static Profile strong() { return new Profile(18, 16, 18, 16); }
        static Profile weak() { return new Profile(10, 12, 10, 12); }
        static Profile teamfightStrong() { return new Profile(14, 14, 14, 18); }
        static Profile teamfightWeak() { return new Profile(14, 14, 14, 10); }
        static Profile farmingStrong() { return new Profile(14, 14, 18, 14); }
        static Profile farmingWeak() { return new Profile(14, 14, 10, 14); }
        static Profile actionStrong() { return new Profile(18, 18, 14, 18); }
        static Profile actionWeak() { return new Profile(10, 10, 14, 10); }
    }

    private record Scenario(String key, Map<Position, Profile> blue, Map<Position, Profile> red) {
        Scenario mirror() { return new Scenario(key + "_MIRROR", new EnumMap<>(red), new EnumMap<>(blue)); }
        Team team(TeamSide side) {
            Map<Position, Profile> profiles = side == TeamSide.BLUE ? blue : red;
            List<Player> players = new ArrayList<>();
            for (Position position : Position.values()) {
                Profile p = profiles.get(position);
                players.add(new Player(key + "_" + side + "_" + position, position,
                        new PlayerAttributes(p.mechanics(), p.aggression(), p.farming(), p.teamfighting())));
            }
            return new Team(key + "_" + side, players);
        }
    }

    private static final class Report {
        final String key;
        final boolean enabled;
        final int games;
        final EnumMap<TeamSide, EnumMap<TeamMacroPlan, Long>> selections = sidePlanLong();
        final EnumMap<TeamSide, EnumMap<TeamMacroPlan, Double>> weightSums = sidePlanDouble();
        final EnumMap<TeamSide, EnumMap<TeamMacroPlan, Long>> weightSamples = sidePlanLong();
        final EnumMap<Position, Long> assignments = positionLong();
        final EnumMap<Position, Long> farmBlockedTicks = positionLong();
        final EnumMap<TeamMacroPlan, Long> pushAttempts = planLong();
        final EnumMap<TeamMacroPlan, Long> pushSuccess = planLong();
        final EnumMap<TeamMacroPlan, Long> pushFailure = planLong();
        final EnumMap<TeamMacroPlan, Double> pushChanceSums = planDouble();
        final EnumMap<TeamMacroPlan, Long> pushChanceSamples = planLong();
        final EnumMap<TowerTier, Long> towerTiers = enumLong(TowerTier.class);
        final EnumMap<Lane, Long> towerLanes = enumLong(Lane.class);
        final EnumMap<TeamSide, Long> towerSides = enumLong(TeamSide.class);
        final EnumMap<ObjectiveType, EnumMap<TeamSide, Long>> setupStarts = objectiveSideLong();
        final Map<Integer, EnumMap<Position, Double>> csSums = milestonePositionDoubleMap();
        final Map<Integer, EnumMap<Position, Long>> csSamples = milestonePositionLongMap();
        final List<Integer> durations = new ArrayList<>();
        long blueEvaluations, redEvaluations, selectionRandom, repeatPenalty;
        long deadAssignmentErrors, combatParticipantAssignmentErrors, duplicateAssignmentErrors;
        long existingPushBlocked, macroTowerEvents, sameTeamSameTickStructure, duplicateReward;
        long structureOrderViolation, forbiddenBaseStructure;
        long setupActiveSeconds, simultaneousSetupSeconds, netZeroSetupSeconds;
        long allPlanExpired, setupLifecycleStarts, dragonSetupExpired, baronSetupExpired;
        long setupObjectiveCaptured, setupReplaced, setupGameFinished, setupFeatureDisabled, setupStillActiveAtCutoff;
        long lifecycleDuplicateIdentity, lifecycleEndCountErrors, lifecycleBalanceErrors, setupControlAfterEndErrors;
        long snapshotRepeatEndAccounting, elderSetupErrors, postFightSetupErrors, setupDoubleApplication;
        long directCsSubtractionErrors, catchUpErrors, blockedFarmRandomErrors, supportCsErrors;
        long dragons, barons, souls, elders, blueWins, redWins, nexusEnds, timeouts;
        long offMutation, duplicateEvaluation, duplicateMacroAction, randomMismatch;
        long diagnosticsMismatch, replayMismatch;

        Report(String key, boolean enabled, int games) {
            this.key = key; this.enabled = enabled; this.games = games;
        }

        void add(MatchSimulator.SimulationResult result) {
            MatchTimeline timeline = result.timeline();
            MatchSnapshot last = timeline.getSnapshots().getLast();
            durations.add(timeline.getDurationSeconds());
            if (result.winnerSide() == TeamSide.BLUE) blueWins++;
            if (result.winnerSide() == TeamSide.RED) redWins++;
            if (result.endReason() == GameEndReason.NEXUS_DESTROYED) nexusEnds++;
            if (result.endReason() == GameEndReason.SIMULATION_TIMEOUT) timeouts++;
            if (last.isBlueHasDragonSoul() || last.isRedHasDragonSoul()) souls++;

            MidGameMacroExecutionStatsSnapshot stats = result.midGameMacroExecutionStats();
            deadAssignmentErrors += stats.deadAssignmentErrors();
            combatParticipantAssignmentErrors += stats.combatParticipantAssignmentErrors();
            existingPushBlocked += stats.existingStructureActionBlocked();
            for (Position position : Position.values()) {
                farmBlockedTicks.merge(position,
                        (long) stats.farmBlockedTicks().getOrDefault(position, 0), Long::sum);
            }

            MidGameMacroSnapshot macro = last.getMidGameMacro();
            Set<String> evaluationKeys = new HashSet<>();
            long historySelectionRandom = 0;
            for (MidGameMacroEvaluationData evaluation : macro.evaluationHistory()) {
                historySelectionRandom += evaluation.selectionRandomConsumptionCount();
                if (evaluation.blueDecision() != null) addDecision(evaluation.blueDecision(), evaluationKeys);
                if (evaluation.redDecision() != null) addDecision(evaluation.redDecision(), evaluationKeys);
            }
            selectionRandom += historySelectionRandom;
            if (historySelectionRandom != stats.selectionRolls()) randomMismatch++;

            Map<String, Integer> structurePerTick = new HashMap<>();
            Set<String> uniqueStructures = new HashSet<>();
            Map<String, Integer> laneProgress = new HashMap<>();
            Set<String> macroActions = new HashSet<>();
            long gamePushActions = 0;
            long gameSetupActions = 0;
            for (MatchEvent event : timeline.getEvents()) {
                if (event.getType() == MatchEventType.DRAGON) dragons++;
                if (event.getType() == MatchEventType.BARON) barons++;
                if (event.getType() == MatchEventType.ELDER) elders++;
                if (event.getObjectivePriorityDecision() != null
                        && event.getObjectivePriorityDecision().postFightLinked()
                        && Math.abs(event.getObjectivePriorityDecision().macroSetupControl()) > 1e-9) {
                    postFightSetupErrors++;
                }
                MidGameMacroActionData action = event.getMidGameMacroAction();
                if (action != null) {
                    String actionKey = event.getTimeSeconds() + ":" + action.teamSide() + ":" + action.actionType();
                    if (!macroActions.add(actionKey)) duplicateMacroAction++;
                    if (action.actionType() == MacroActionType.STRUCTURE_PUSH) {
                        gamePushActions++;
                        pushAttempts.merge(action.plan(), 1L, Long::sum);
                        pushChanceSums.merge(action.plan(), action.finalPushChance(), Double::sum);
                        pushChanceSamples.merge(action.plan(), 1L, Long::sum);
                        if (action.result() == MacroActionResult.STRUCTURE_DESTROYED) pushSuccess.merge(action.plan(), 1L, Long::sum);
                        else pushFailure.merge(action.plan(), 1L, Long::sum);
                    } else if (action.actionType() == MacroActionType.OBJECTIVE_SETUP) {
                        gameSetupActions++;
                        setupStarts.get(action.targetObjective()).merge(action.teamSide(), 1L, Long::sum);
                    }
                }
                if (event.getStructureKind() != null && event.getStructureAttackingSide() != null) {
                    String tickKey = event.getTimeSeconds() + ":" + event.getStructureAttackingSide();
                    structurePerTick.merge(tickKey, 1, Integer::sum);
                    String identity = tickKey + ":" + event.getStructureDefendingSide() + ":"
                            + event.getStructureLane() + ":" + event.getStructureKind() + ":" + event.getStructureTowerTier();
                    if (!uniqueStructures.add(identity)) duplicateReward++;
                }
                if (event.getStructureKind() == StructureKind.TOWER && event.getStructureLane() != null
                        && event.getStructureTowerTier() != null) {
                    String laneKey = event.getStructureDefendingSide() + ":" + event.getStructureLane();
                    int expected = laneProgress.getOrDefault(laneKey, 0);
                    if (event.getStructureTowerTier().ordinal() != expected) structureOrderViolation++;
                    else laneProgress.put(laneKey, expected + 1);
                }
                if (event.getStructureActionSource() == StructureActionSource.MID_GAME_MACRO) {
                    macroTowerEvents++;
                    if (event.getStructureKind() != StructureKind.TOWER) forbiddenBaseStructure++;
                    if (event.getStructureTowerTier() != null) towerTiers.merge(event.getStructureTowerTier(), 1L, Long::sum);
                    if (event.getStructureLane() != null) towerLanes.merge(event.getStructureLane(), 1L, Long::sum);
                    towerSides.merge(event.getStructureAttackingSide(), 1L, Long::sum);
                }
            }
            sameTeamSameTickStructure += structurePerTick.values().stream().filter(v -> v > 1).mapToLong(v -> v - 1).sum();
            if (stats.pushRolls() != gamePushActions) randomMismatch++;
            addLifecycleAccounting(macro, timeline.getSnapshots(), gameSetupActions);

            addSetupDurations(timeline.getSnapshots());
            addCsMilestones(timeline.getSnapshots());
            directCsSubtractionErrors += csDecreaseErrors(timeline.getSnapshots());
            supportCsErrors += last.getPlayerSnapshots().stream()
                    .filter(p -> p.getPosition() == Position.SUPPORT && p.getCs() != 0).count();
            if (!enabled && (!macro.evaluationHistory().isEmpty()
                    || timeline.getEvents().stream().anyMatch(e -> e.getType() == MatchEventType.MACRO_ACTION))) offMutation++;
        }

        private void addDecision(MidGameMacroDecisionData decision, Set<String> keys) {
            if (decision.teamSide() == TeamSide.BLUE) blueEvaluations++; else redEvaluations++;
            String key = decision.teamSide() + ":" + decision.evaluationTimeSeconds();
            if (!keys.add(key)) duplicateEvaluation++;
            selections.get(decision.teamSide()).merge(decision.selectedPlan(), 1L, Long::sum);
            for (MacroPlanWeightBreakdown candidate : decision.candidates()) {
                weightSums.get(decision.teamSide()).merge(candidate.plan(), candidate.finalWeight(), Double::sum);
                weightSamples.get(decision.teamSide()).merge(candidate.plan(), 1L, Long::sum);
            }
            MacroPlanWeightBreakdown selected = decision.candidates().stream()
                    .filter(c -> c.plan() == decision.selectedPlan()).findFirst().orElseThrow();
            if (selected.repeatMultiplier() != 1) repeatPenalty++;
            for (Position position : decision.assignedPositions()) assignments.merge(position, 1L, Long::sum);
        }

        private void addLifecycleAccounting(MidGameMacroSnapshot macro, List<MatchSnapshot> snapshots,
                                            long gameSetupActions) {
            List<MacroPlanLifecycleData> lifecycles = macro.planLifecycleHistory();
            Set<String> identities = new HashSet<>();
            long gameStarts = 0, gameExpired = 0, gameCaptured = 0, gameReplaced = 0;
            long gameFinished = 0, gameFeatureDisabled = 0, gameStillActive = 0;
            for (MacroPlanLifecycleData lifecycle : lifecycles) {
                String identity = lifecycle.teamSide() + ":" + lifecycle.planSequence();
                if (!identities.add(identity)) {
                    lifecycleDuplicateIdentity++;
                    snapshotRepeatEndAccounting++;
                }
                if (lifecycle.endReason() == MacroPlanEndReason.EXPIRED) allPlanExpired++;
                if (!lifecycle.setupPlan()) continue;
                gameStarts++;
                setupLifecycleStarts++;
                if (lifecycle.endRecordCount() < 0 || lifecycle.endRecordCount() > 1) lifecycleEndCountErrors++;
                if (lifecycle.endRecordCount() == 0) {
                    gameStillActive++;
                    setupStillActiveAtCutoff++;
                    if (lifecycle.endReason() != null || lifecycle.endTimeSeconds() != null) lifecycleEndCountErrors++;
                    continue;
                }
                if (lifecycle.endReason() == null || lifecycle.endTimeSeconds() == null) lifecycleEndCountErrors++;
                if (lifecycle.endReason() == MacroPlanEndReason.EXPIRED) {
                    gameExpired++;
                    if (lifecycle.plan() == TeamMacroPlan.OBJECTIVE_SETUP_DRAGON) dragonSetupExpired++;
                    else if (lifecycle.plan() == TeamMacroPlan.OBJECTIVE_SETUP_BARON) baronSetupExpired++;
                } else if (lifecycle.endReason() == MacroPlanEndReason.OBJECTIVE_CAPTURED) {
                    gameCaptured++;
                    setupObjectiveCaptured++;
                } else if (lifecycle.endReason() == MacroPlanEndReason.REPLACED) {
                    gameReplaced++;
                    setupReplaced++;
                } else if (lifecycle.endReason() == MacroPlanEndReason.MATCH_ENDED) {
                    gameFinished++;
                    setupGameFinished++;
                } else if (lifecycle.endReason() == MacroPlanEndReason.FEATURE_DISABLED) {
                    gameFeatureDisabled++;
                    setupFeatureDisabled++;
                }
            }
            long gameClosedOrActive = gameExpired + gameCaptured + gameReplaced
                    + gameFinished + gameFeatureDisabled + gameStillActive;
            if (gameStarts != gameClosedOrActive || gameStarts != gameSetupActions) lifecycleBalanceErrors++;
            if (macro.matchEnded() && gameStillActive != 0) lifecycleBalanceErrors++;

            for (MatchSnapshot snapshot : snapshots) {
                int time = snapshot.getTimeSeconds();
                double expectedDragon = expectedSetupControl(lifecycles, TeamMacroPlan.OBJECTIVE_SETUP_DRAGON, time);
                double expectedBaron = expectedSetupControl(lifecycles, TeamMacroPlan.OBJECTIVE_SETUP_BARON, time);
                if (Math.abs(expectedDragon - snapshot.getMidGameMacro().dragonMacroSetupControl()) > 1e-9
                        || Math.abs(expectedBaron - snapshot.getMidGameMacro().baronMacroSetupControl()) > 1e-9) {
                    setupControlAfterEndErrors++;
                }
            }
        }

        private double expectedSetupControl(List<MacroPlanLifecycleData> lifecycles,
                                            TeamMacroPlan plan, int timeSeconds) {
            double result = 0;
            for (MacroPlanLifecycleData lifecycle : lifecycles) {
                if (lifecycle.plan() != plan || lifecycle.startedAtSeconds() > timeSeconds) continue;
                if (lifecycle.endTimeSeconds() != null && timeSeconds >= lifecycle.endTimeSeconds()) continue;
                result += lifecycle.teamSide() == TeamSide.BLUE
                        ? MidGameMacroRuleConfig.MACRO_SETUP_CONTROL : -MidGameMacroRuleConfig.MACRO_SETUP_CONTROL;
            }
            return result;
        }

        private void addSetupDurations(List<MatchSnapshot> snapshots) {
            for (int i = 1; i < snapshots.size(); i++) {
                MatchSnapshot previous = snapshots.get(i - 1);
                MatchSnapshot current = snapshots.get(i);
                int seconds = Math.max(0, current.getTimeSeconds() - previous.getTimeSeconds());
                TeamMacroSnapshot blue = previous.getMidGameMacro().blueTeam();
                TeamMacroSnapshot red = previous.getMidGameMacro().redTeam();
                boolean blueSetup = blue.targetObjective() == ObjectiveType.DRAGON || blue.targetObjective() == ObjectiveType.BARON;
                boolean redSetup = red.targetObjective() == ObjectiveType.DRAGON || red.targetObjective() == ObjectiveType.BARON;
                if (blueSetup) setupActiveSeconds += seconds;
                if (redSetup) setupActiveSeconds += seconds;
                if (blueSetup && redSetup) {
                    simultaneousSetupSeconds += seconds;
                    if (blue.targetObjective() == red.targetObjective()) {
                        double control = blue.targetObjective() == ObjectiveType.DRAGON
                                ? previous.getMidGameMacro().dragonMacroSetupControl()
                                : previous.getMidGameMacro().baronMacroSetupControl();
                        if (Math.abs(control) < 1e-9) netZeroSetupSeconds += seconds;
                    }
                }
                if ((previous.isBlueHasDragonSoul() || previous.isRedHasDragonSoul())
                        && Math.abs(previous.getMidGameMacro().dragonMacroSetupControl()) > 1e-9) elderSetupErrors++;
                if (Math.abs(previous.getObjectivePriority().dragonMacroSetupControl()
                        - previous.getMidGameMacro().dragonMacroSetupControl()) > 1e-9
                        || Math.abs(previous.getObjectivePriority().baronMacroSetupControl()
                        - previous.getMidGameMacro().baronMacroSetupControl()) > 1e-9) setupDoubleApplication++;
            }
        }

        private void addCsMilestones(List<MatchSnapshot> snapshots) {
            for (int milestone : List.of(1200, 1500, 1800)) {
                MatchSnapshot sample = snapshots.stream().filter(s -> s.getTimeSeconds() >= milestone)
                        .findFirst().orElse(snapshots.getLast());
                for (PlayerSnapshot player : sample.getPlayerSnapshots()) {
                    csSums.get(milestone).merge(player.getPosition(), (double) player.getCs(), Double::sum);
                    csSamples.get(milestone).merge(player.getPosition(), 1L, Long::sum);
                }
            }
        }

        private long csDecreaseErrors(List<MatchSnapshot> snapshots) {
            Map<String, Integer> previous = new HashMap<>();
            long errors = 0;
            for (MatchSnapshot snapshot : snapshots) for (PlayerSnapshot player : snapshot.getPlayerSnapshots()) {
                String key = player.getTeamSide() + ":" + player.getPosition();
                Integer before = previous.put(key, player.getCs());
                if (before != null && player.getCs() < before) errors++;
            }
            return errors;
        }

        long totalSelections() { return selections.values().stream().flatMap(m -> m.values().stream()).mapToLong(Long::longValue).sum(); }
        long totalSetupStarts() { return setupStarts.values().stream().flatMap(m -> m.values().stream()).mapToLong(Long::longValue).sum(); }
        double averageWeight(TeamSide side, TeamMacroPlan plan) {
            long samples = weightSamples.get(side).get(plan);
            return samples == 0 ? 0 : weightSums.get(side).get(plan) / samples;
        }
        double averagePushChance(TeamMacroPlan plan) {
            long samples = pushChanceSamples.get(plan);
            return samples == 0 ? 0 : pushChanceSums.get(plan) / samples;
        }
        double averageDuration() { return durations.stream().mapToInt(Integer::intValue).average().orElse(0); }
        int percentile(double p) {
            if (durations.isEmpty()) return 0;
            List<Integer> sorted = new ArrayList<>(durations); Collections.sort(sorted);
            return sorted.get(Math.min(sorted.size() - 1, Math.max(0, (int) Math.ceil(sorted.size() * p) - 1)));
        }
        long integrityErrors() {
            return offMutation + duplicateEvaluation + duplicateMacroAction + duplicateAssignmentErrors
                    + randomMismatch + diagnosticsMismatch + replayMismatch + deadAssignmentErrors
                    + combatParticipantAssignmentErrors + sameTeamSameTickStructure + duplicateReward
                    + structureOrderViolation + forbiddenBaseStructure + elderSetupErrors
                    + postFightSetupErrors + setupDoubleApplication + directCsSubtractionErrors
                    + catchUpErrors + blockedFarmRandomErrors + supportCsErrors
                    + lifecycleDuplicateIdentity + lifecycleEndCountErrors + lifecycleBalanceErrors
                    + setupControlAfterEndErrors + snapshotRepeatEndAccounting;
        }

        String line() {
            return "REPORT key=" + key + " mode=" + (enabled ? "ON" : "OFF") + " seeds=" + games
                    + " evaluations={BLUE=" + blueEvaluations + ",RED=" + redEvaluations + "}"
                    + " plans=" + planSummary() + " weights=" + weightSummary()
                    + " selection={random=" + selectionRandom + ",repeatPenalty=" + repeatPenalty
                    + ",assignments=" + assignments + ",deadErrors=" + deadAssignmentErrors
                    + ",combatErrors=" + combatParticipantAssignmentErrors + "}"
                    + " structure={attempt=" + pushAttempts + ",success=" + pushSuccess + ",failure=" + pushFailure
                    + ",avgChance=" + pushChanceSummary() + ",tiers=" + towerTiers + ",lanes=" + towerLanes
                    + ",sides=" + towerSides + ",existingBlocked=" + existingPushBlocked
                    + ",sameTeamSameTick=" + sameTeamSameTickStructure + ",duplicateReward=" + duplicateReward
                    + ",orderViolation=" + structureOrderViolation + ",forbiddenBase=" + forbiddenBaseStructure + "}"
                    + " setup={starts=" + setupStarts + ",activeSeconds=" + setupActiveSeconds
                    + ",simultaneous=" + simultaneousSetupSeconds + ",netZero=" + netZeroSetupSeconds
                    + ",allPlanExpired=" + allPlanExpired
                    + ",dragonSetupExpired=" + dragonSetupExpired + ",baronSetupExpired=" + baronSetupExpired
                    + ",setupObjectiveCaptured=" + setupObjectiveCaptured + ",setupReplaced=" + setupReplaced
                    + ",setupGameFinished=" + setupGameFinished + ",setupFeatureDisabled=" + setupFeatureDisabled
                    + ",stillActiveAtCutoff=" + setupStillActiveAtCutoff
                    + ",lifecycleEquation=" + setupLifecycleStarts + "="
                    + (dragonSetupExpired + baronSetupExpired) + "+" + setupObjectiveCaptured + "+"
                    + setupReplaced + "+" + setupGameFinished + "+" + setupFeatureDisabled + "+"
                    + setupStillActiveAtCutoff
                    + ",identityErrors=" + lifecycleDuplicateIdentity + ",endCountErrors=" + lifecycleEndCountErrors
                    + ",balanceErrors=" + lifecycleBalanceErrors + ",snapshotRepeat=" + snapshotRepeatEndAccounting
                    + ",controlAfterEndErrors=" + setupControlAfterEndErrors
                    + ",elderError=" + elderSetupErrors + ",postFightError=" + postFightSetupErrors
                    + ",doubleApply=" + setupDoubleApplication + "}"
                    + " farm={blockedTicks=" + farmBlockedTicks + ",cs=" + csSummary()
                    + ",directSubtract=" + directCsSubtractionErrors + ",catchUp=" + catchUpErrors
                    + ",blockedRandom=" + blockedFarmRandomErrors + ",supportError=" + supportCsErrors + "}"
                    + " outcome={dragon=" + dragons + ",baron=" + barons + ",soul=" + souls + ",elder=" + elders
                    + ",blueWin=" + percent(blueWins, games) + ",redWin=" + percent(redWins, games)
                    + ",avg=" + format(averageDuration()) + ",median=" + percentile(.50)
                    + ",p90=" + percentile(.90) + ",p95=" + percentile(.95)
                    + ",nexus=" + nexusEnds + ",timeout=" + timeouts + "}"
                    + " integrity={offMutation=" + offMutation + ",duplicateEvaluation=" + duplicateEvaluation
                    + ",duplicateAction=" + duplicateMacroAction + ",duplicateAssignment=" + duplicateAssignmentErrors
                    + ",randomMismatch=" + randomMismatch + ",diagnosticsMismatch=" + diagnosticsMismatch
                    + ",replayMismatch=" + replayMismatch + ",total=" + integrityErrors() + "}";
        }

        private String planSummary() {
            StringJoiner joiner = new StringJoiner(",", "{", "}");
            for (TeamMacroPlan plan : TeamMacroPlan.values()) {
                long b = selections.get(TeamSide.BLUE).get(plan), r = selections.get(TeamSide.RED).get(plan);
                joiner.add(plan + "=" + b + "/" + r + "/" + percent(b + r, totalSelections()));
            }
            return joiner.toString();
        }
        private String weightSummary() {
            StringJoiner joiner = new StringJoiner(",", "{", "}");
            for (TeamMacroPlan plan : TeamMacroPlan.values()) joiner.add(plan + "="
                    + format(averageWeight(TeamSide.BLUE, plan)) + "/" + format(averageWeight(TeamSide.RED, plan)));
            return joiner.toString();
        }
        private String pushChanceSummary() {
            StringJoiner joiner = new StringJoiner(",", "{", "}");
            for (TeamMacroPlan plan : TeamMacroPlan.values()) if (pushChanceSamples.get(plan) > 0) {
                joiner.add(plan + "=" + format(averagePushChance(plan)));
            }
            return joiner.toString();
        }
        private String csSummary() {
            StringJoiner joiner = new StringJoiner(",", "{", "}");
            for (int milestone : List.of(1200, 1500, 1800)) for (Position position : Position.values()) {
                long samples = csSamples.get(milestone).get(position);
                double average = samples == 0 ? 0 : csSums.get(milestone).get(position) / samples;
                joiner.add((milestone / 60) + "m-" + position + "=" + format(average));
            }
            return joiner.toString();
        }
    }

    private static EnumMap<TeamMacroPlan, Long> planLong() { return enumLong(TeamMacroPlan.class); }
    private static EnumMap<TeamMacroPlan, Double> planDouble() { return enumDouble(TeamMacroPlan.class); }
    private static EnumMap<Position, Long> positionLong() { return enumLong(Position.class); }
    private static <E extends Enum<E>> EnumMap<E, Long> enumLong(Class<E> type) {
        EnumMap<E, Long> result = new EnumMap<>(type); for (E value : type.getEnumConstants()) result.put(value, 0L); return result;
    }
    private static <E extends Enum<E>> EnumMap<E, Double> enumDouble(Class<E> type) {
        EnumMap<E, Double> result = new EnumMap<>(type); for (E value : type.getEnumConstants()) result.put(value, 0.0); return result;
    }
    private static EnumMap<TeamSide, EnumMap<TeamMacroPlan, Long>> sidePlanLong() {
        EnumMap<TeamSide, EnumMap<TeamMacroPlan, Long>> result = new EnumMap<>(TeamSide.class);
        for (TeamSide side : TeamSide.values()) result.put(side, planLong()); return result;
    }
    private static EnumMap<TeamSide, EnumMap<TeamMacroPlan, Double>> sidePlanDouble() {
        EnumMap<TeamSide, EnumMap<TeamMacroPlan, Double>> result = new EnumMap<>(TeamSide.class);
        for (TeamSide side : TeamSide.values()) result.put(side, planDouble()); return result;
    }
    private static EnumMap<ObjectiveType, EnumMap<TeamSide, Long>> objectiveSideLong() {
        EnumMap<ObjectiveType, EnumMap<TeamSide, Long>> result = new EnumMap<>(ObjectiveType.class);
        for (ObjectiveType objective : List.of(ObjectiveType.DRAGON, ObjectiveType.BARON)) {
            result.put(objective, enumLong(TeamSide.class));
        }
        return result;
    }
    private static Map<Integer, EnumMap<Position, Double>> milestonePositionDoubleMap() {
        Map<Integer, EnumMap<Position, Double>> result = new LinkedHashMap<>();
        for (int time : List.of(1200, 1500, 1800)) result.put(time, enumDouble(Position.class));
        return result;
    }
    private static Map<Integer, EnumMap<Position, Long>> milestonePositionLongMap() {
        Map<Integer, EnumMap<Position, Long>> result = new LinkedHashMap<>();
        for (int time : List.of(1200, 1500, 1800)) result.put(time, enumLong(Position.class));
        return result;
    }
    private static String percent(long value, long total) { return format(total == 0 ? 0 : value * 100.0 / total) + "%"; }
    private static String format(double value) { return String.format(Locale.ROOT, "%.3f", value); }
}
