package com.lolfm.domain;

import com.lolfm.simulator.Lane;
import com.lolfm.simulator.StructureActionSource;
import com.lolfm.simulator.StructureKind;
import com.lolfm.simulator.TeamSide;
import com.lolfm.simulator.TowerTier;
import java.util.List;

public class MatchEvent {

    private final int timeSeconds;
    private final MatchEventType type;
    private final String message;
    /** Existing display fields retained for API compatibility. */
    private final String killer;
    private final String victim;
    private final List<String> assists;
    /** Additive stable identity fields; display names are never stored here. */
    private String killerPlayerId;
    private String victimPlayerId;
    private List<String> assistPlayerIds = List.of();
    private final int goldAmount;
    private final double bountyRawBeforePayout;
    private LaneCombatData laneCombat;
    private JungleGankData jungleGank;
    private CounterGankData counterGank;
    private RoamData roam;
    private CombatSource combatSource;
    private ObjectivePriorityDecisionData objectivePriorityDecision;
    private ObjectiveDecisionData objectiveDecision;
    private StructureActionSource structureActionSource;
    private MidGameMacroDecisionData midGameMacroDecision;
    private MidGameMacroActionData midGameMacroAction;
    private StructureKind structureKind;
    private TowerTier structureTowerTier;
    private Lane structureLane;
    private TeamSide structureAttackingSide;
    private TeamSide structureDefendingSide;
    private OuterTurretSiegeData outerTurretSiege;
    private MatchPhaseChangeData matchPhaseChange;
    private LateGameDecisionData lateGameDecision;
    private ProgressionEventData progressionEvent;

    public MatchEvent(int timeSeconds, MatchEventType type, String message, String killer,
                      String victim, List<String> assists) {
        this(timeSeconds, type, message, killer, victim, assists, 0, 0.0);
    }

    public MatchEvent(int timeSeconds, MatchEventType type, String message, String killer,
                      String victim, List<String> assists, int goldAmount) {
        this(timeSeconds, type, message, killer, victim, assists, goldAmount, 0.0);
    }

    public MatchEvent(int timeSeconds, MatchEventType type, String message, String killer,
                      String victim, List<String> assists, int goldAmount,
                      double bountyRawBeforePayout) {
        this.timeSeconds = timeSeconds;
        this.type = type;
        this.message = message;
        this.killer = killer;
        this.victim = victim;
        this.assists = assists == null ? List.of() : List.copyOf(assists);
        this.goldAmount = Math.max(0, goldAmount);
        this.bountyRawBeforePayout = Math.max(0.0, bountyRawBeforePayout);
    }

    public int getTimeSeconds() { return timeSeconds; }
    public MatchEventType getType() { return type; }
    public String getMessage() { return message; }
    public String getKiller() { return killer; }
    public String getVictim() { return victim; }
    public List<String> getAssists() { return assists; }
    public String getKillerPlayerId() { return killerPlayerId; }
    public String getVictimPlayerId() { return victimPlayerId; }
    public List<String> getAssistPlayerIds() { return assistPlayerIds; }
    public void setParticipantPlayerIds(String killerPlayerId, String victimPlayerId,
                                        List<String> assistPlayerIds) {
        this.killerPlayerId = killerPlayerId;
        this.victimPlayerId = victimPlayerId;
        this.assistPlayerIds = assistPlayerIds == null ? List.of() : List.copyOf(assistPlayerIds);
    }
    public int getGoldAmount() { return goldAmount; }
    public double getBountyRawBeforePayout() { return bountyRawBeforePayout; }
    public LaneCombatData getLaneCombat() { return laneCombat; }
    public void setLaneCombat(LaneCombatData laneCombat) { this.laneCombat = laneCombat; }
    public JungleGankData getJungleGank() { return jungleGank; }
    public void setJungleGank(JungleGankData jungleGank) { this.jungleGank = jungleGank; }
    public CounterGankData getCounterGank() { return counterGank; }
    public void setCounterGank(CounterGankData counterGank) { this.counterGank = counterGank; }
    public RoamData getRoam() { return roam; }
    public void setRoam(RoamData roam) { this.roam = roam; }
    public CombatSource getCombatSource() { return combatSource; }
    public void setCombatSource(CombatSource combatSource) { this.combatSource = combatSource; }
    public ObjectivePriorityDecisionData getObjectivePriorityDecision() { return objectivePriorityDecision; }
    public void setObjectivePriorityDecision(ObjectivePriorityDecisionData value) {
        objectivePriorityDecision = value;
    }
    public ObjectiveDecisionData getObjectiveDecision() { return objectiveDecision; }
    public void setObjectiveDecision(ObjectiveDecisionData value) { objectiveDecision = value; }
    public StructureActionSource getStructureActionSource() { return structureActionSource; }
    public MidGameMacroDecisionData getMidGameMacroDecision() { return midGameMacroDecision; }
    public void setMidGameMacroDecision(MidGameMacroDecisionData value) { midGameMacroDecision = value; }
    public MidGameMacroActionData getMidGameMacroAction() { return midGameMacroAction; }
    public void setMidGameMacroAction(MidGameMacroActionData value) { midGameMacroAction = value; }
    public StructureKind getStructureKind() { return structureKind; }
    public void setStructureKind(StructureKind value) { structureKind = value; }
    public TowerTier getStructureTowerTier() { return structureTowerTier; }
    public void setStructureTowerTier(TowerTier value) { structureTowerTier = value; }
    public void setStructureActionSource(StructureActionSource value) { structureActionSource = value; }
    public Lane getStructureLane() { return structureLane; }
    public void setStructureLane(Lane value) { structureLane = value; }
    public TeamSide getStructureAttackingSide() { return structureAttackingSide; }
    public void setStructureAttackingSide(TeamSide value) { structureAttackingSide = value; }
    public TeamSide getStructureDefendingSide() { return structureDefendingSide; }
    public void setStructureDefendingSide(TeamSide value) { structureDefendingSide = value; }
    public OuterTurretSiegeData getOuterTurretSiege() { return outerTurretSiege; }
    public void setOuterTurretSiege(OuterTurretSiegeData value) { outerTurretSiege = value; }
    public MatchPhaseChangeData getMatchPhaseChange() { return matchPhaseChange; }
    public void setMatchPhaseChange(MatchPhaseChangeData value) { matchPhaseChange = value; }
    public LateGameDecisionData getLateGameDecision() { return lateGameDecision; }
    public void setLateGameDecision(LateGameDecisionData value) { lateGameDecision = value; }
    public ProgressionEventData getProgressionEvent() { return progressionEvent; }
    public void setProgressionEvent(ProgressionEventData value) { progressionEvent = value; }
}
