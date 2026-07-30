package com.lolfm.composition;
import com.lolfm.champion.*;import com.lolfm.domain.Position;import java.util.*;
final class SyntheticTeamCompositionFixtures{
 record FixtureCase(String caseId,String expectedPurpose,TeamCompositionLineup lineup,Map<ChampionRoleKey,ChampionCompositionProfile>profiles){}
 static List<FixtureCase> cases(){return List.of(
  build("engage-chain","complete ENGAGE_CHAIN",0,Map.of(Position.TOP,m(CompositionCapability.ENGAGE,20),Position.JUNGLE,m(CompositionCapability.FOLLOW_UP,20),Position.MID,m(CompositionCapability.BACKLINE_ACCESS,20)),balanced()),
  build("engage-no-follow","engage without follow-up",0,Map.of(Position.TOP,m(CompositionCapability.ENGAGE,20,CompositionCapability.BACKLINE_ACCESS,20)),balanced()),
  build("front-to-back","complete FRONT_TO_BACK",0,Map.of(Position.TOP,m(CompositionCapability.FRONTLINE,20),Position.SUPPORT,m(CompositionCapability.PEEL,20),Position.ADC,m(CompositionCapability.SUSTAINED_DAMAGE,20)),balanced()),
  build("front-concentrated","frontline and damage concentrated",0,Map.of(Position.TOP,m(CompositionCapability.FRONTLINE,20,CompositionCapability.PEEL,20,CompositionCapability.SUSTAINED_DAMAGE,20)),balanced()),
  build("poke-siege","complete POKE_SIEGE",0,Map.of(Position.MID,m(CompositionCapability.POKE,20,CompositionCapability.SIEGE,20,CompositionCapability.WAVE_CLEAR,20)),balanced()),
  build("pick-conversion","complete PICK_CONVERSION",0,Map.of(Position.MID,m(CompositionCapability.PICK,20,CompositionCapability.BURST_DAMAGE,20),Position.JUNGLE,m(CompositionCapability.OBJECTIVE_DAMAGE,20)),balanced()),
  build("split-map","complete SPLIT_MAP_PRESSURE",0,Map.of(Position.TOP,m(CompositionCapability.SIDE_LANE_PRESSURE,20),Position.MID,m(CompositionCapability.WAVE_CLEAR,20),Position.SUPPORT,m(CompositionCapability.DISENGAGE,20)),balanced()),
  build("split-concentrated","side and wave same contributor",0,Map.of(Position.TOP,m(CompositionCapability.SIDE_LANE_PRESSURE,20,CompositionCapability.WAVE_CLEAR,20,CompositionCapability.DISENGAGE,20)),balanced()),
  build("objective-control","complete OBJECTIVE_CONTROL",0,Map.of(Position.JUNGLE,m(CompositionCapability.OBJECTIVE_DAMAGE,20),Position.MID,m(CompositionCapability.ZONE_CONTROL,20),Position.TOP,m(CompositionCapability.FRONTLINE,20)),balanced()),
  build("no-engage","NO_RELIABLE_ENGAGE",10,all(CompositionCapability.ENGAGE,0),balanced()),
  build("low-frontline","LOW_FRONTLINE",10,all(CompositionCapability.FRONTLINE,0),balanced()),
  build("physical-skew","physical damage skew",10,Map.of(),new DamageChannelProfile(20,0,0)),
  build("magic-skew","magic damage skew",10,Map.of(),new DamageChannelProfile(0,20,0)),
  build("balanced-damage","balanced damage",10,Map.of(),balanced()),
  build("zero-damage","zero damage denominator",10,Map.of(),new DamageChannelProfile(0,0,0)),
  build("neutral","all capabilities midpoint",10,Map.of(),balanced()));}
 static FixtureCase build(String id,String purpose,int base,Map<Position,Map<CompositionCapability,Integer>>overrides,DamageChannelProfile damage){EnumMap<Position,ChampionRoleKey>line=new EnumMap<>(Position.class);LinkedHashMap<ChampionRoleKey,ChampionCompositionProfile>profiles=new LinkedHashMap<>();for(Position p:Position.values()){ChampionRoleKey k=new ChampionRoleKey(new ChampionId("synthetic-"+id+"-"+p.name().toLowerCase()),p);line.put(p,k);EnumMap<CompositionCapability,Integer>caps=values(base);caps.putAll(overrides.getOrDefault(p,Map.of()));profiles.put(k,new ChampionCompositionProfile(k,caps,damage));}return new FixtureCase(id,purpose,new TeamCompositionLineup(line),Map.copyOf(profiles));}
 static EnumMap<CompositionCapability,Integer>values(int v){EnumMap<CompositionCapability,Integer>m=new EnumMap<>(CompositionCapability.class);for(var c:CompositionCapability.values())m.put(c,v);return m;}
 static Map<Position,Map<CompositionCapability,Integer>>all(CompositionCapability c,int value){EnumMap<Position,Map<CompositionCapability,Integer>>m=new EnumMap<>(Position.class);for(var p:Position.values())m.put(p,Map.of(c,value));return m;}
 static Map<CompositionCapability,Integer>m(Object...x){EnumMap<CompositionCapability,Integer>m=new EnumMap<>(CompositionCapability.class);for(int i=0;i<x.length;i+=2)m.put((CompositionCapability)x[i],(Integer)x[i+1]);return m;}
 static DamageChannelProfile balanced(){return new DamageChannelProfile(10,10,0);}
}
