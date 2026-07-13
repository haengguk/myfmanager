package com.lolfm.simulator;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.lolfm.domain.Position;

public class TeamState {

    private final String teamName;
    private int kills;
    private int gold;
    private int dragons;
    private int towersDestroyed;
    private boolean hasDragonSoul;
    private boolean hasBaronBuff;
    private int baronBuffExpiresAtSeconds = -1;
    private final List<PlayerState> players;
    private final EnumMap<Position, PlayerState> playersByPosition = new EnumMap<>(Position.class);

    public TeamState(String teamName, List<PlayerState> players) {
        this.teamName = teamName;
        this.players = new ArrayList<>(players);
        this.gold = calculateStartingGold(players);
        for (PlayerState player : this.players) playersByPosition.putIfAbsent(player.getPosition(), player);
    }

    public String getTeamName() {
        return teamName;
    }

    public int getKills() {
        return kills;
    }

    public int getGold() {
        return gold;
    }

    public int getDragons() {
        return dragons;
    }

    public int getTowersDestroyed() {
        return towersDestroyed;
    }

    public boolean hasDragonSoul() {
        return hasDragonSoul;
    }

    public boolean hasBaronBuff() {
        return hasBaronBuff;
    }

    public int getBaronBuffExpiresAtSeconds() {
        return baronBuffExpiresAtSeconds;
    }

    public void grantBaronBuff(int currentTimeSeconds, int durationSeconds) {
        hasBaronBuff = true;
        baronBuffExpiresAtSeconds = currentTimeSeconds + durationSeconds;
    }

    public boolean hasActiveBaronBuff(int currentTimeSeconds) {
        return hasBaronBuff && currentTimeSeconds < baronBuffExpiresAtSeconds;
    }

    public void expireBaronBuffIfNeeded(int currentTimeSeconds) {
        if (hasBaronBuff && currentTimeSeconds >= baronBuffExpiresAtSeconds) {
            hasBaronBuff = false;
            baronBuffExpiresAtSeconds = -1;
        }
    }

    public List<PlayerState> getPlayers() { return players; }

    public Optional<PlayerState> findPlayerAt(Position position) {
        return Optional.ofNullable(playersByPosition.get(position));
    }

    public PlayerState playerAt(Position position) {
        return findPlayerAt(position).orElseThrow(() -> new IllegalArgumentException("Missing position: " + position));
    }

    /** Validates the five-player invariant at match start, rather than for partial unit-test teams. */
    public void validateCompleteLineup() {
        if (players.size() != Position.values().length) throw new IllegalStateException("Expected complete five-player lineup");
        Map<PlayerState, Boolean> uniquePlayers = new IdentityHashMap<>();
        for (PlayerState player : players) {
            if (uniquePlayers.put(player, Boolean.TRUE) != null) throw new IllegalStateException("The same PlayerState appears more than once");
        }
        for (Position position : Position.values()) {
            long count = players.stream().filter(player -> player.getPosition() == position).count();
            if (count != 1) throw new IllegalStateException("Expected exactly one " + position + ", found " + count);
        }
    }

    public void addKill() {
        kills++;
    }

    public void addGold(int amount) {
        gold += amount;
    }

    public void addDragon() {
        if (dragons >= 4) {
            throw new IllegalStateException("A team cannot capture more than four elemental dragons.");
        }
        dragons++;
    }

    public void setHasDragonSoul(boolean hasDragonSoul) {
        this.hasDragonSoul = hasDragonSoul;
    }

    public void addTowerDestroyed() {
        towersDestroyed++;
    }

    public void setHasBaronBuff(boolean hasBaronBuff) {
        this.hasBaronBuff = hasBaronBuff;
        if (!hasBaronBuff) {
            baronBuffExpiresAtSeconds = -1;
        }
    }

    public PlayerState getPlayerState(String playerName) {
        for (PlayerState playerState : players) {
            if (playerState.getPlayerName().equals(playerName)) {
                return playerState;
            }
        }

        throw new IllegalArgumentException("Unknown player state: " + playerName);
    }

    private int calculateStartingGold(List<PlayerState> playerStates) {
        int total = 0;
        for (PlayerState playerState : playerStates) {
            total += playerState.getGold();
        }
        return total;
    }
}
