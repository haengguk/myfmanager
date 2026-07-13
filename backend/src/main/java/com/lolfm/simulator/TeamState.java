package com.lolfm.simulator;

import java.util.ArrayList;
import java.util.List;

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

    public TeamState(String teamName, List<PlayerState> players) {
        this.teamName = teamName;
        this.players = new ArrayList<>(players);
        this.gold = calculateStartingGold(players);
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

    public List<PlayerState> getPlayers() {
        return players;
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
