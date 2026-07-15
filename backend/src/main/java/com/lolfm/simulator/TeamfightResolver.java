package com.lolfm.simulator;

import com.lolfm.domain.MatchEvent;
import com.lolfm.domain.MatchEventType;
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

    private static final int SIMULATION_SAFETY_TIMEOUT_SECONDS = MatchSimulator.SIMULATION_SAFETY_TIMEOUT_SECONDS;
    private final KillRewardResolver killRewards = new KillRewardResolver();

    public Optional<TeamfightOutcome> maybeResolveTeamfight(
            GameState gameState,
            Team blueTeam,
            Team redTeam,
            Random random,
            List<MatchEvent> events
    ) {
        int currentTime = gameState.getCurrentTimeSeconds();
        double triggerChance = hasElderTeamWithThreeAlive(gameState, currentTime)
                ? ElderRuleConfig.TEAMFIGHT_TRIGGER_CHANCE : 0.03;
        if (currentTime < 900 || random.nextDouble() >= triggerChance) {
            return Optional.empty();
        }
        if (countAlivePlayers(gameState.getBlueTeamState(), currentTime) == 0
                || countAlivePlayers(gameState.getRedTeamState(), currentTime) == 0) {
            return Optional.empty();
        }

        TeamfightSides sides = determineTeamfightSides(gameState, blueTeam, redTeam, random);
        for (PlayerState player : gameState.getBlueTeamState().getPlayers()) if (player.canParticipateInMajorCombatAt(currentTime)) gameState.markMajorCombatParticipant(player);
        for (PlayerState player : gameState.getRedTeamState().getPlayers()) if (player.canParticipateInMajorCombatAt(currentTime)) gameState.markMajorCombatParticipant(player);
        FightGrade plannedGrade = determineFightGrade(gameState, sides, random);
        int winningKillTarget = Math.min(
                determineWinningTeamKillCount(plannedGrade, random),
                countAlivePlayers(sides.losingTeamState(), currentTime)
        );
        int counterKillTarget = determineCounterKillCount(plannedGrade, winningKillTarget, random);
        Set<String> deadPlayers = new HashSet<>();
        Map<String, Integer> frozenShutdownGold = freezeShutdownGold(gameState, currentTime);

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
            nextEventTime = Math.min(nextEventTime + 1, SIMULATION_SAFETY_TIMEOUT_SECONDS);
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
            nextEventTime = Math.min(nextEventTime + 1, SIMULATION_SAFETY_TIMEOUT_SECONDS);
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
            nextEventTime = Math.min(nextEventTime + 1, SIMULATION_SAFETY_TIMEOUT_SECONDS);
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

        nextEventTime = Math.min(nextEventTime + 1, SIMULATION_SAFETY_TIMEOUT_SECONDS);
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

        return Optional.of(new TeamfightOutcome(
                sides.winningSide(),
                resolvedGrade,
                winningKillsCreated,
                losingKillsCreated,
                nextEventTime,
                new ArrayList<>(deadPlayers)
        ));
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
            Set<String> deadPlayers
    ) {
        return resolveKill(timeSeconds, random, attackingTeam, attackingTeamState, defendingTeam,
                defendingTeamState, events, teamfight, deadPlayers, null);
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
            Set<String> deadPlayers,
            Map<String, Integer> frozenShutdownGold
    ) {
        List<Player> killerCandidates = filterEligiblePlayers(
                attackingTeam.getPlayers(), attackingTeamState, timeSeconds, deadPlayers
        );
        List<Player> victimCandidates = filterEligiblePlayers(
                defendingTeam.getPlayers(), defendingTeamState, timeSeconds, deadPlayers
        );
        if (killerCandidates.isEmpty() || victimCandidates.isEmpty()) {
            return false;
        }

        Player killer = pickWeightedPlayer(killerCandidates, random, player -> {
            PlayerState candidate = attackingTeamState.getPlayerState(player.getName());
            double weight = candidate.getMechanics() * PlayerImpactRuleConfig.KILLER_MECHANICS_WEIGHT
                    + candidate.getAggression() * PlayerImpactRuleConfig.KILLER_AGGRESSION_WEIGHT
                    + candidate.getTeamfighting() * PlayerImpactRuleConfig.KILLER_TEAMFIGHTING_WEIGHT;
            if (teamfight) weight += candidate.getTeamfighting() * 0.8;
            if (player.getPosition() == Position.ADC || player.getPosition() == Position.MID) weight += 8.0;
            return weight;
        });
        Player victim = pickWeightedPlayer(victimCandidates, random, player -> {
            PlayerState candidate = defendingTeamState.getPlayerState(player.getName());
            double positionRisk = switch (player.getPosition()) {
                case ADC -> 1.45;
                case MID -> 1.25;
                case SUPPORT -> 1.15;
                case JUNGLE -> 1.0;
                case TOP -> 0.9;
            };
            return Math.max(0.15, positionRisk
                    + candidate.getAggression() * PlayerImpactRuleConfig.VICTIM_AGGRESSION_RISK_WEIGHT
                    - candidate.getMechanics() * PlayerImpactRuleConfig.VICTIM_MECHANICS_PROTECTION_WEIGHT
                    + candidate.getDeaths() * 0.08);
        });
        List<String> assists = pickAssistNames(
                attackingTeam, attackingTeamState, killer, timeSeconds, random, teamfight, deadPlayers
        );

        PlayerState killerState = attackingTeamState.getPlayerState(killer.getName());
        PlayerState victimState = defendingTeamState.getPlayerState(victim.getName());
        List<PlayerState> assistantStates = new ArrayList<>();
        for (String assistName : assists) assistantStates.add(attackingTeamState.getPlayerState(assistName));
        killRewards.award(
                timeSeconds, attackingTeamState, killerState, defendingTeamState, victimState, assistantStates,
                calculateRespawnDelaySeconds(timeSeconds), teamfight,
                frozenShutdownGold == null ? null : frozenShutdownGold.get(victim.getName()), events
        );
        deadPlayers.add(victim.getName());

        MatchEvent killEvent = new MatchEvent(
                timeSeconds,
                MatchEventType.KILL,
                buildKillMessage(killer.getName(), victim.getName(), assists),
                killer.getName(),
                victim.getName(),
                assists
        );
        killEvent.setCombatSource(teamfight
                ? com.lolfm.domain.CombatSource.TEAMFIGHT
                : com.lolfm.domain.CombatSource.SKIRMISH);
        events.add(killEvent);
        if (!teamfight) {
            commitPendingCombatProgress(attackingTeamState);
            commitPendingCombatProgress(defendingTeamState);
        }
        return true;
    }

    private Map<String, Integer> freezeShutdownGold(GameState gameState, int timeSeconds) {
        Map<String, Integer> values = new HashMap<>();
        snapshotTeamShutdownGold(gameState.getBlueTeamState(), gameState.getRedTeamState(), timeSeconds, values);
        snapshotTeamShutdownGold(gameState.getRedTeamState(), gameState.getBlueTeamState(), timeSeconds, values);
        return values;
    }

    private void snapshotTeamShutdownGold(TeamState own, TeamState enemy, int time, Map<String, Integer> values) {
        for (PlayerState player : own.getPlayers()) {
            int displayed = BountyService.displayedShutdownGold(player, own, enemy, time);
            player.setLastVisibleShutdownGold(displayed);
            values.put(player.getPlayerName(), displayed);
        }
    }

    private void commitPendingCombatProgress(TeamState team) {
        for (PlayerState player : team.getPlayers()) player.commitPendingCombatBountyProgress();
    }

    private TeamfightSides determineTeamfightSides(GameState state, Team blueTeam, Team redTeam, Random random) {
        TeamState blue = state.getBlueTeamState();
        TeamState red = state.getRedTeamState();
        double pressure = (blue.getGold() - red.getGold()) / 500.0
                + (blue.getKills() - red.getKills()) * 11.0
                + teamfightScore(state, TeamSide.BLUE, blueTeam)
                - teamfightScore(state, TeamSide.RED, redTeam)
                + (random.nextDouble() - 0.5) * 56.0;
        return pressure >= 0
                ? new TeamfightSides(TeamSide.BLUE, blueTeam, blue, redTeam, red, pressure)
                : new TeamfightSides(TeamSide.RED, redTeam, red, blueTeam, blue, Math.abs(pressure));
    }

    double teamfightScore(GameState state, TeamSide side, Team team) {
        TeamState teamState = state.getTeamState(side);
        int currentTime = state.getCurrentTimeSeconds();
        int alive = 0;
        double totalTeamfighting = 0.0;
        double totalMechanics = 0.0;
        for (PlayerState player : teamState.getPlayers()) {
            if (!player.isAlive(currentTime)) continue;
            alive++;
            totalTeamfighting += player.getTeamfighting();
            totalMechanics += player.getMechanics();
        }
        if (alive == 0) return 0.0;
        double score = totalTeamfighting / alive * PlayerImpactRuleConfig.TEAMFIGHTING_SCORE_WEIGHT
                + totalMechanics / alive * PlayerImpactRuleConfig.TEAMFIGHT_MECHANICS_SCORE_WEIGHT
                + alive * PlayerImpactRuleConfig.ALIVE_PLAYER_SCORE_WEIGHT;
        if (state.getObjectiveState().isSoulOwner(side)) score += DragonSoulRuleConfig.SOUL_TEAMFIGHT_SCORE_BONUS;
        if (teamState.hasActiveBaronBuff(currentTime)) score += PlayerImpactRuleConfig.BARON_TEAMFIGHT_SCORE_BONUS;
        score += Math.min(ElderRuleConfig.MAX_TEAMFIGHT_SCORE_BONUS, activeElderPlayers(teamState, currentTime) * ElderRuleConfig.TEAMFIGHT_SCORE_BONUS_PER_PLAYER);
        return score;
    }

    private FightGrade determineFightGrade(GameState state, TeamfightSides sides, Random random) {
        int currentTime = state.getCurrentTimeSeconds();
        int goldLead = Math.max(0, sides.winningTeamState().getGold() - sides.losingTeamState().getGold());
        double teamfightGap = Math.max(0.0, teamfightScore(state, sides.winningSide(), sides.winningTeam())
                - teamfightScore(state, sides.winningSide().opposite(), sides.losingTeam()));
        double lateBonus = currentTime >= 2_100 ? 0.018 : currentTime >= 1_800 ? 0.012 : currentTime >= 1_500 ? 0.008 : 0.0;
        double objectiveBonus = isMajorObjectiveMoment(currentTime) ? 0.01 : 0.0;
        double dominanceBonus = Math.min(0.025, sides.advantageScore() / 1_800.0);
        double elderAceBonus = activeElderPlayers(sides.winningTeamState(), currentTime) > 0 ? ElderRuleConfig.ACE_CHANCE_BONUS : 0.0;
        double elderBigBonus = activeElderPlayers(sides.winningTeamState(), currentTime) > 0 ? ElderRuleConfig.BIG_WIN_CHANCE_BONUS : 0.0;
        double aceChance = Math.min(0.10, 0.005 + elderAceBonus + goldLead / 400_000.0 + teamfightGap / PlayerImpactRuleConfig.TEAMFIGHT_GRADE_GAP_DIVISOR
                + lateBonus + objectiveBonus + dominanceBonus);
        if (random.nextDouble() < aceChance) {
            return FightGrade.ACE;
        }
        double bigWinChance = Math.min(0.42, 0.18 + elderBigBonus + goldLead / 260_000.0 + teamfightGap / PlayerImpactRuleConfig.TEAMFIGHT_GRADE_GAP_DIVISOR
                + lateBonus + objectiveBonus + dominanceBonus);
        if (random.nextDouble() < bigWinChance) {
            return FightGrade.BIG_WIN;
        }
        double normalWinChance = Math.min(0.78, 0.46 + goldLead / 320_000.0 + teamfightGap / PlayerImpactRuleConfig.TEAMFIGHT_GRADE_GAP_DIVISOR + dominanceBonus);
        return random.nextDouble() < normalWinChance ? FightGrade.NORMAL_WIN : FightGrade.SMALL_WIN;
    }

    private boolean hasElderTeamWithThreeAlive(GameState state, int time) {
        return activeElderPlayers(state.getBlueTeamState(), time) >= 3 || activeElderPlayers(state.getRedTeamState(), time) >= 3;
    }

    private int activeElderPlayers(TeamState team, int time) { int count = 0; for (PlayerState player : team.getPlayers()) if (player.hasActiveElderBuff(time)) count++; return count; }

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

    private List<Player> filterEligiblePlayers(List<Player> players, TeamState state, int time, Set<String> blocked) {
        List<Player> candidates = new ArrayList<>();
        for (Player player : players) {
            if (!blocked.contains(player.getName()) && state.getPlayerState(player.getName()).canParticipateInMajorCombatAt(time)) {
                candidates.add(player);
            }
        }
        return candidates;
    }

    private List<String> pickAssistNames(
            Team team, TeamState state, Player killer, int time, Random random, boolean teamfight, Set<String> deadPlayers
    ) {
        List<Player> candidates = new ArrayList<>();
        for (Player player : team.getPlayers()) {
            if (!player.getName().equals(killer.getName())
                    && !deadPlayers.contains(player.getName())
                    && state.getPlayerState(player.getName()).canParticipateInMajorCombatAt(time)) {
                candidates.add(player);
            }
        }
        int assistCount = random.nextInt((teamfight ? 3 : 2) + 1);
        List<String> assists = new ArrayList<>();
        for (int index = 0; index < assistCount && !candidates.isEmpty(); index++) {
            assists.add(candidates.remove(random.nextInt(candidates.size())).getName());
        }
        return assists;
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

    private Player pickWeightedPlayer(List<Player> players, Random random, java.util.function.ToDoubleFunction<Player> weight) {
        double total = 0.0;
        for (Player player : players) total += Math.max(0.1, weight.applyAsDouble(player));
        double roll = random.nextDouble() * total;
        double cursor = 0.0;
        for (Player player : players) {
            cursor += Math.max(0.1, weight.applyAsDouble(player));
            if (roll <= cursor) return player;
        }
        return players.get(players.size() - 1);
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
            double advantageScore
    ) {
    }
}
