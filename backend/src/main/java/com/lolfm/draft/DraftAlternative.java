package com.lolfm.draft;

import com.lolfm.champion.ChampionId;

public record DraftAlternative(ChampionId championId, double finalSearchScore) { }
