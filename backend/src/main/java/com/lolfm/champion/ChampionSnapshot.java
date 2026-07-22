package com.lolfm.champion;

import com.lolfm.domain.Position;

public record ChampionSnapshot(String id, String displayNameKo, String displayNameEn, String portraitUrl,
                               Position primaryPosition, String poolVersion,ChampionPowerProfileSnapshot powerProfile) {
    public ChampionSnapshot(String id,String ko,String en,String portrait,Position position,String pool){this(id,ko,en,portrait,position,pool,null);}
    public static ChampionSnapshot from(ChampionDefinition definition) {
        return new ChampionSnapshot(definition.id().value(), definition.displayNameKo(), definition.displayNameEn(),
                definition.portraitUrl(), definition.primaryPosition(), definition.championPoolVersion());
    }
    public static ChampionSnapshot from(ChampionDefinition d,ChampionPowerProfile p,int level,com.lolfm.simulator.ItemProgressStage stage,boolean enabled){double l=p.levelCurve().valueAt(level),i=p.itemModifiers().get(stage);return new ChampionSnapshot(d.id().value(),d.displayNameKo(),d.displayNameEn(),d.portraitUrl(),d.primaryPosition(),d.championPoolVersion(),new ChampionPowerProfileSnapshot(p.profileVersion(),p.levelCurveId(),p.itemCurveId(),p.tags(),p.summaryKo(),l,i,l+i,enabled));}
}
