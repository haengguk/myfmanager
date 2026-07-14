package com.lolfm.domain;

import java.util.List;

public class MatchEvent {

    private final int timeSeconds;
    private final MatchEventType type;
    private final String message;
    private final String killer;
    private final String victim;
    private final List<String> assists;
    private final int goldAmount;
    private final double bountyRawBeforePayout;
    private LaneCombatData laneCombat;
    private JungleGankData jungleGank;
    private CounterGankData counterGank;
    private CombatSource combatSource;

    public MatchEvent(
            int timeSeconds,
            MatchEventType type,
            String message,
            String killer,
            String victim,
            List<String> assists
    ) {
        this(timeSeconds, type, message, killer, victim, assists, 0, 0.0);
    }

    public MatchEvent(
            int timeSeconds,
            MatchEventType type,
            String message,
            String killer,
            String victim,
            List<String> assists,
            int goldAmount
    ) {
        this(timeSeconds, type, message, killer, victim, assists, goldAmount, 0.0);
    }

    public MatchEvent(int timeSeconds, MatchEventType type, String message, String killer, String victim,
                      List<String> assists, int goldAmount, double bountyRawBeforePayout) {
        this.timeSeconds = timeSeconds;
        this.type = type;
        this.message = message;
        this.killer = killer;
        this.victim = victim;
        this.assists = assists == null ? List.of() : List.copyOf(assists);
        this.goldAmount = Math.max(0, goldAmount);
        this.bountyRawBeforePayout = Math.max(0.0, bountyRawBeforePayout);
    }

    public int getTimeSeconds() {
        return timeSeconds;
    }

    public MatchEventType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public String getKiller() {
        return killer;
    }

    public String getVictim() {
        return victim;
    }

    public List<String> getAssists() {
        return assists;
    }

    public int getGoldAmount() { return goldAmount; }
    public double getBountyRawBeforePayout() { return bountyRawBeforePayout; }
    public LaneCombatData getLaneCombat() { return laneCombat; }
    public void setLaneCombat(LaneCombatData laneCombat) { this.laneCombat = laneCombat; }
    public JungleGankData getJungleGank() { return jungleGank; }
    public void setJungleGank(JungleGankData jungleGank) { this.jungleGank = jungleGank; }
    public CounterGankData getCounterGank() { return counterGank; }
    public void setCounterGank(CounterGankData counterGank) { this.counterGank = counterGank; }
    public CombatSource getCombatSource() { return combatSource; }
    public void setCombatSource(CombatSource combatSource) { this.combatSource = combatSource; }
}
