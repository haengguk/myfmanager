package com.lolfm.composition;
public record DamageChannelProfile(int physicalThreat,int magicThreat,int trueDamageThreat){public DamageChannelProfile{check(physicalThreat);check(magicThreat);check(trueDamageThreat);}private static void check(int v){if(v<0||v>20)throw new IllegalArgumentException("Damage threat must be 0..20");}}
