package com.lolfm.simulator;
import com.lolfm.domain.MatchEvent;
import java.util.List;
public record ElderCaptureOutcome(TeamSide capturingSide, int occurredAtSeconds, int nextSpawnSeconds, List<String> buffedPlayerNames, MatchEvent event) {}
