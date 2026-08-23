package com.lolfm.simulator;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public class MapState {
    private final EnumMap<TeamSide, EnumMap<Lane, LaneStructureState>> laneStates = new EnumMap<>(TeamSide.class);
    private final EnumMap<TeamSide, BaseState> baseStates = new EnumMap<>(TeamSide.class);
    private int nextBluePushAttemptSeconds = 480;
    private int nextRedPushAttemptSeconds = 480;
    private int blueBasePressureUntilSeconds = -1;
    private int redBasePressureUntilSeconds = -1;

    public MapState() {
        for (TeamSide side : TeamSide.values()) {
            EnumMap<Lane, LaneStructureState> lanes = new EnumMap<>(Lane.class);
            for (Lane lane : Lane.values()) lanes.put(lane, new LaneStructureState());
            laneStates.put(side, lanes);
            baseStates.put(side, new BaseState());
        }
    }
    public void refreshAt(int currentTimeSeconds) {
        for (TeamSide side : TeamSide.values()) {
            for (Lane lane : Lane.values()) getLaneState(side, lane).refreshAt(currentTimeSeconds);
            getBaseState(side).refreshAt(currentTimeSeconds);
        }
    }
    public LaneStructureState getLaneState(TeamSide defendingSide, Lane lane) { return laneStates.get(defendingSide).get(lane); }
    public BaseState getBaseState(TeamSide defendingSide) { return baseStates.get(defendingSide); }
    public List<Lane> getAttackableLanes(TeamSide defendingSide) {
        List<Lane> lanes=new ArrayList<>();
        for(Lane lane:Lane.values()) if(getLaneState(defendingSide,lane).nextAliveTower().isPresent()) lanes.add(lane);
        return lanes;
    }
    public List<Lane> getPressureLanes(TeamSide defendingSide) {
        List<Lane> lanes=new ArrayList<>();
        for(Lane lane:Lane.values()) {
            LaneStructureState state=getLaneState(defendingSide,lane);
            if(state.nextAliveTower().isPresent() || state.isInhibitorVulnerable()) lanes.add(lane);
        }
        return lanes;
    }
    public int calculateLaneProgress(TeamSide defendingSide, Lane lane) {
        LaneStructureState state = getLaneState(defendingSide, lane);
        if (state.isOuterTowerAlive()) return 0;
        if (state.isInnerTowerAlive()) return 1;
        if (state.isInhibitorTowerAlive()) return 2;
        if (state.isInhibitorAlive()) return 3;
        return 4;
    }
    public void activateBasePressure(TeamSide attackingSide, int currentTimeSeconds) {
        if (attackingSide == TeamSide.BLUE) blueBasePressureUntilSeconds = currentTimeSeconds + PushRuleConfig.BASE_PRESSURE_DURATION_SECONDS;
        else redBasePressureUntilSeconds = currentTimeSeconds + PushRuleConfig.BASE_PRESSURE_DURATION_SECONDS;
    }
    public boolean hasActiveBasePressure(TeamSide attackingSide, int currentTimeSeconds) {
        return currentTimeSeconds < (attackingSide == TeamSide.BLUE ? blueBasePressureUntilSeconds : redBasePressureUntilSeconds);
    }
    public boolean hasDestroyedInhibitor(TeamSide defendingSide) { return getAliveInhibitorCount(defendingSide)<Lane.values().length; }
    public int getAliveInhibitorCount(TeamSide defendingSide) {
        int count=0; for(Lane lane:Lane.values()) if(getLaneState(defendingSide,lane).isInhibitorAlive()) count++; return count;
    }
    public boolean areNexusTurretsVulnerable(TeamSide defendingSide) {
        BaseState base=getBaseState(defendingSide); return hasDestroyedInhibitor(defendingSide)&&base.isNexusAlive()&&base.hasNexusTurrets();
    }
    public boolean isNexusVulnerable(TeamSide defendingSide) {
        BaseState base=getBaseState(defendingSide); return hasDestroyedInhibitor(defendingSide)&&base.isNexusAlive()&&base.areAllNexusTurretsDestroyed();
    }
    public boolean isNexusDestroyed(TeamSide defendingSide) { return !getBaseState(defendingSide).isNexusAlive(); }
    public int getDestroyedTowerCountByAttackingSide(TeamSide attackingSide) { int total=0; for(Lane lane:Lane.values()) total+=getLaneState(attackingSide.opposite(),lane).destroyedTowerCount(); return total; }
    public boolean isPushAttemptDue(TeamSide side,int time){return time>=(side==TeamSide.BLUE?nextBluePushAttemptSeconds:nextRedPushAttemptSeconds);}
    public void markPushAttempted(TeamSide side,int time,int interval){if(side==TeamSide.BLUE)nextBluePushAttemptSeconds=time+interval;else nextRedPushAttemptSeconds=time+interval;}
}
