package com.lolfm.domain;
import com.lolfm.domain.Position;import java.util.Map;
public record TeamProgressionSnapshot(double averageLevel,int totalCoreCount,int level18Count,Map<Position,PlayerProgressionSnapshot> players){public TeamProgressionSnapshot{players=Map.copyOf(players);}}
