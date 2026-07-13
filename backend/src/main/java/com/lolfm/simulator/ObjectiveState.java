package com.lolfm.simulator;

public class ObjectiveState {
    private boolean dragonAlive;
    private int nextDragonSpawnSeconds = ObjectiveRuleConfig.FIRST_DRAGON_SPAWN_SECONDS;
    private int nextDragonAttemptSeconds = ObjectiveRuleConfig.FIRST_DRAGON_SPAWN_SECONDS + ObjectiveRuleConfig.OBJECTIVE_FIRST_ATTEMPT_DELAY_SECONDS;
    private int dragonSpawnedAtSeconds = -1;
    private DragonPhase dragonPhase = DragonPhase.ELEMENTAL;
    private TeamSide soulOwner;
    private int soulClaimedAtSeconds = -1;
    private boolean baronAlive;
    private int nextBaronSpawnSeconds = ObjectiveRuleConfig.FIRST_BARON_SPAWN_SECONDS;
    private int nextBaronAttemptSeconds = ObjectiveRuleConfig.FIRST_BARON_SPAWN_SECONDS + ObjectiveRuleConfig.OBJECTIVE_FIRST_ATTEMPT_DELAY_SECONDS;
    private int baronSpawnedAtSeconds = -1;
    private TeamSide lastDragonSide, lastBaronSide, lastElderSide;
    private int lastDragonTimeSeconds = -1, lastBaronTimeSeconds = -1, lastElderTimeSeconds = -1;
    private boolean elderAlive;
    private int nextElderSpawnSeconds = -1, elderSpawnedAtSeconds = -1, nextElderAttemptSeconds = -1;
    public boolean isDragonAlive(){return dragonAlive;} public int getNextDragonSpawnSeconds(){return nextDragonSpawnSeconds;} public int getNextDragonAttemptSeconds(){return nextDragonAttemptSeconds;} public int getDragonSpawnedAtSeconds(){return dragonSpawnedAtSeconds;} public DragonPhase getDragonPhase(){return dragonPhase;} public TeamSide getSoulOwner(){return soulOwner;} public int getSoulClaimedAtSeconds(){return soulClaimedAtSeconds;} public boolean isElementalDragonPhase(){return dragonPhase==DragonPhase.ELEMENTAL;} public boolean isSoulClaimed(){return soulOwner!=null;} public boolean hasSoulOwner(){return soulOwner!=null;} public boolean isSoulOwner(TeamSide side){return soulOwner==side;}
    public boolean isBaronAlive(){return baronAlive;} public int getNextBaronSpawnSeconds(){return nextBaronSpawnSeconds;} public int getNextBaronAttemptSeconds(){return nextBaronAttemptSeconds;} public int getBaronSpawnedAtSeconds(){return baronSpawnedAtSeconds;} public TeamSide getLastDragonSide(){return lastDragonSide;} public int getLastDragonTimeSeconds(){return lastDragonTimeSeconds;} public TeamSide getLastBaronSide(){return lastBaronSide;} public int getLastBaronTimeSeconds(){return lastBaronTimeSeconds;}
    public boolean isElderAlive(){return elderAlive;} public boolean isElderPhase(){return dragonPhase==DragonPhase.ELDER;} public boolean isElderPending(){return dragonPhase==DragonPhase.ELDER_PENDING;} public int getNextElderSpawnSeconds(){return nextElderSpawnSeconds;} public int getElderSpawnedAtSeconds(){return elderSpawnedAtSeconds;} public int getNextElderAttemptSeconds(){return nextElderAttemptSeconds;} public TeamSide getLastElderSide(){return lastElderSide;} public int getLastElderTimeSeconds(){return lastElderTimeSeconds;}
    public void updateSpawnState(int time){ if(isElementalDragonPhase()&&!dragonAlive&&time>=nextDragonSpawnSeconds){dragonAlive=true;dragonSpawnedAtSeconds=time;} if(!baronAlive&&time>=nextBaronSpawnSeconds){baronAlive=true;baronSpawnedAtSeconds=time;} if((isElderPending()||isElderPhase())&&!elderAlive&&nextElderSpawnSeconds>=0&&time>=nextElderSpawnSeconds){elderAlive=true;elderSpawnedAtSeconds=time;nextElderAttemptSeconds=time+ElderRuleConfig.ELDER_FIRST_ATTEMPT_DELAY_SECONDS;dragonPhase=DragonPhase.ELDER;} }
    public boolean isDragonAttemptDue(int time){return isElementalDragonPhase()&&dragonAlive&&time>=nextDragonAttemptSeconds;} public boolean isBaronAttemptDue(int time){return baronAlive&&time>=nextBaronAttemptSeconds;} public boolean isElderAttemptDue(int time){return isElderPhase()&&elderAlive&&time>=nextElderAttemptSeconds;}
    public void markDragonAttempted(int t){nextDragonAttemptSeconds=t+ObjectiveRuleConfig.OBJECTIVE_ATTEMPT_INTERVAL_SECONDS;} public void markBaronAttempted(int t){nextBaronAttemptSeconds=t+ObjectiveRuleConfig.OBJECTIVE_ATTEMPT_INTERVAL_SECONDS;} public void markElderAttempted(int t){nextElderAttemptSeconds=t+ElderRuleConfig.ELDER_ATTEMPT_INTERVAL_SECONDS;}
    public boolean captureDragon(TeamSide s,int t){if(!isElementalDragonPhase()||!dragonAlive)return false;dragonAlive=false;dragonSpawnedAtSeconds=-1;lastDragonSide=s;lastDragonTimeSeconds=t;return true;} public void scheduleNextDragonSpawn(int t){if(!isElementalDragonPhase())return;nextDragonSpawnSeconds=t+ObjectiveRuleConfig.DRAGON_RESPAWN_SECONDS;nextDragonAttemptSeconds=nextDragonSpawnSeconds+ObjectiveRuleConfig.OBJECTIVE_FIRST_ATTEMPT_DELAY_SECONDS;}
    public void claimSoul(TeamSide s,int t){if(hasSoulOwner())return;dragonPhase=DragonPhase.ELDER_PENDING;soulOwner=s;soulClaimedAtSeconds=t;dragonAlive=false;nextDragonSpawnSeconds=-1;nextDragonAttemptSeconds=-1;nextElderSpawnSeconds=t+ElderRuleConfig.FIRST_ELDER_SPAWN_DELAY_SECONDS;}
    public boolean captureBaron(TeamSide s,int t){if(!baronAlive)return false;baronAlive=false;baronSpawnedAtSeconds=-1;nextBaronSpawnSeconds=t+ObjectiveRuleConfig.BARON_RESPAWN_SECONDS;nextBaronAttemptSeconds=nextBaronSpawnSeconds+ObjectiveRuleConfig.OBJECTIVE_FIRST_ATTEMPT_DELAY_SECONDS;lastBaronSide=s;lastBaronTimeSeconds=t;return true;}
    public boolean captureElder(TeamSide s,int t){if(!isElderPhase()||!elderAlive)return false;elderAlive=false;elderSpawnedAtSeconds=-1;nextElderSpawnSeconds=t+ElderRuleConfig.ELDER_RESPAWN_SECONDS;nextElderAttemptSeconds=-1;lastElderSide=s;lastElderTimeSeconds=t;return true;}
}
