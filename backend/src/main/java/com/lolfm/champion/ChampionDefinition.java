package com.lolfm.champion;

import com.lolfm.domain.Position;
import java.util.Objects;
import java.util.Set;

public record ChampionDefinition(
        ChampionId id,
        String displayNameKo,
        String displayNameEn,
        String riotAssetId,
        Position primaryPosition,
        Set<Position> supportedPositions,
        String portraitUrl,
        String championPoolVersion,
        String riotDataVersion,String profileVersion,String levelCurveId,String itemCurveId,Set<ChampionTag> tags,String profileSummary,java.util.List<com.lolfm.simulator.ProgressionCombatContext> contextStrengths,java.util.List<com.lolfm.simulator.ProgressionCombatContext> contextWeaknesses
) {
    public ChampionDefinition(ChampionId id,String ko,String en,String riot,Position primary,Set<Position> supported,String portrait,String pool,String data){this(id,ko,en,riot,primary,supported,portrait,pool,data,null,null,null,Set.of(),null,java.util.List.of(),java.util.List.of());}
    public ChampionDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(primaryPosition, "primaryPosition");
        supportedPositions = Set.copyOf(Objects.requireNonNull(supportedPositions, "supportedPositions"));
        if (displayNameKo == null || displayNameKo.isBlank()) throw new IllegalArgumentException("Missing Korean name: " + id);
        if (displayNameEn == null || displayNameEn.isBlank()) throw new IllegalArgumentException("Missing English name: " + id);
        if (riotAssetId == null || riotAssetId.isBlank()) throw new IllegalArgumentException("Missing Riot asset id: " + id);
        if (portraitUrl == null || portraitUrl.isBlank()) throw new IllegalArgumentException("Missing portrait: " + id);
        tags=tags==null?Set.of():Set.copyOf(tags);contextStrengths=contextStrengths==null?java.util.List.of():java.util.List.copyOf(contextStrengths);contextWeaknesses=contextWeaknesses==null?java.util.List.of():java.util.List.copyOf(contextWeaknesses);
    }
    public ChampionDefinition withProfile(ChampionPowerProfile p){return new ChampionDefinition(id,displayNameKo,displayNameEn,riotAssetId,primaryPosition,supportedPositions,portraitUrl,championPoolVersion,riotDataVersion,p.profileVersion(),p.levelCurveId(),p.itemCurveId(),p.tags(),p.summaryKo(),p.contextModifiers().entrySet().stream().filter(e->e.getValue()>0).map(java.util.Map.Entry::getKey).toList(),p.contextModifiers().entrySet().stream().filter(e->e.getValue()<0).map(java.util.Map.Entry::getKey).toList());}
}
