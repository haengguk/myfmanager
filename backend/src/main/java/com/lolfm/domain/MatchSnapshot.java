package com.lolfm.domain;

import java.util.List;

public class MatchSnapshot {

    private final int timeSeconds;
    private final int blueKills;
    private final int redKills;
    private final int blueGold;
    private final int redGold;
    private final int blueDragons;
    private final int redDragons;
    private final boolean blueHasDragonSoul;
    private final boolean redHasDragonSoul;
    private final boolean blueHasBaronBuff;
    private final boolean redHasBaronBuff;
    private final boolean elderAlive;
    private final boolean blueHasElderBuff;
    private final boolean redHasElderBuff;
    private final int blueElderBuffRemainingSeconds;
    private final int redElderBuffRemainingSeconds;
    private final int blueTowersDestroyed;
    private final int redTowersDestroyed;
    private final int blueInhibitorsRemaining;
    private final int redInhibitorsRemaining;
    private final int blueNexusTurretsRemaining;
    private final int redNexusTurretsRemaining;
    private final boolean blueNexusAlive;
    private final boolean redNexusAlive;
    private final int blueAlivePlayers;
    private final int redAlivePlayers;
    private final List<PlayerSnapshot> playerSnapshots;
    private final List<LaneSnapshot> laneSnapshots;
    private final ObjectivePrioritySnapshot objectivePriority;

    public MatchSnapshot(
            int timeSeconds,
            int blueKills,
            int redKills,
            int blueGold,
            int redGold,
            int blueDragons,
            int redDragons,
            boolean blueHasDragonSoul,
            boolean redHasDragonSoul,
            boolean blueHasBaronBuff,
            boolean redHasBaronBuff,
            boolean elderAlive,
            boolean blueHasElderBuff,
            boolean redHasElderBuff,
            int blueElderBuffRemainingSeconds,
            int redElderBuffRemainingSeconds,
            int blueTowersDestroyed,
            int redTowersDestroyed,
            int blueInhibitorsRemaining,
            int redInhibitorsRemaining,
            int blueNexusTurretsRemaining,
            int redNexusTurretsRemaining,
            boolean blueNexusAlive,
            boolean redNexusAlive,
            int blueAlivePlayers,
            int redAlivePlayers,
            List<PlayerSnapshot> playerSnapshots,
            List<LaneSnapshot> laneSnapshots,
            ObjectivePrioritySnapshot objectivePriority
    ) {
        this.timeSeconds = timeSeconds;
        this.blueKills = blueKills;
        this.redKills = redKills;
        this.blueGold = blueGold;
        this.redGold = redGold;
        this.blueDragons = blueDragons;
        this.redDragons = redDragons;
        this.blueHasDragonSoul = blueHasDragonSoul;
        this.redHasDragonSoul = redHasDragonSoul;
        this.blueHasBaronBuff = blueHasBaronBuff;
        this.redHasBaronBuff = redHasBaronBuff;
        this.elderAlive = elderAlive;
        this.blueHasElderBuff = blueHasElderBuff;
        this.redHasElderBuff = redHasElderBuff;
        this.blueElderBuffRemainingSeconds = blueElderBuffRemainingSeconds;
        this.redElderBuffRemainingSeconds = redElderBuffRemainingSeconds;
        this.blueTowersDestroyed = blueTowersDestroyed;
        this.redTowersDestroyed = redTowersDestroyed;
        this.blueInhibitorsRemaining = blueInhibitorsRemaining;
        this.redInhibitorsRemaining = redInhibitorsRemaining;
        this.blueNexusTurretsRemaining = blueNexusTurretsRemaining;
        this.redNexusTurretsRemaining = redNexusTurretsRemaining;
        this.blueNexusAlive = blueNexusAlive;
        this.redNexusAlive = redNexusAlive;
        this.blueAlivePlayers = blueAlivePlayers;
        this.redAlivePlayers = redAlivePlayers;
        this.playerSnapshots = List.copyOf(playerSnapshots);
        this.laneSnapshots = List.copyOf(laneSnapshots);
        this.objectivePriority = objectivePriority;
    }

    public int getTimeSeconds() { return timeSeconds; }
    public int getBlueKills() { return blueKills; }
    public int getRedKills() { return redKills; }
    public int getBlueGold() { return blueGold; }
    public int getRedGold() { return redGold; }
    public int getBlueDragons() { return blueDragons; }
    public int getRedDragons() { return redDragons; }
    public boolean isBlueHasDragonSoul() { return blueHasDragonSoul; }
    public boolean isRedHasDragonSoul() { return redHasDragonSoul; }
    public boolean isBlueHasBaronBuff() { return blueHasBaronBuff; }
    public boolean isRedHasBaronBuff() { return redHasBaronBuff; }
    public boolean isElderAlive() { return elderAlive; }
    public boolean isBlueHasElderBuff() { return blueHasElderBuff; }
    public boolean isRedHasElderBuff() { return redHasElderBuff; }
    public int getBlueElderBuffRemainingSeconds() { return blueElderBuffRemainingSeconds; }
    public int getRedElderBuffRemainingSeconds() { return redElderBuffRemainingSeconds; }
    public int getBlueTowersDestroyed() { return blueTowersDestroyed; }
    public int getRedTowersDestroyed() { return redTowersDestroyed; }
    public int getBlueInhibitorsRemaining() { return blueInhibitorsRemaining; }
    public int getRedInhibitorsRemaining() { return redInhibitorsRemaining; }
    public int getBlueNexusTurretsRemaining() { return blueNexusTurretsRemaining; }
    public int getRedNexusTurretsRemaining() { return redNexusTurretsRemaining; }
    public boolean isBlueNexusAlive() { return blueNexusAlive; }
    public boolean isRedNexusAlive() { return redNexusAlive; }
    public int getBlueAlivePlayers() { return blueAlivePlayers; }
    public int getRedAlivePlayers() { return redAlivePlayers; }
    public List<PlayerSnapshot> getPlayerSnapshots() { return playerSnapshots; }
    public List<LaneSnapshot> getLaneSnapshots() { return laneSnapshots; }
    public ObjectivePrioritySnapshot getObjectivePriority() { return objectivePriority; }
}
