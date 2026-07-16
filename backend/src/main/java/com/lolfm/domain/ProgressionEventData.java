package com.lolfm.domain;
import com.lolfm.simulator.*;
public record ProgressionEventData(TeamSide side,PlayerKey playerKey,Position position,ProgressionEventType type,ExperienceSource experienceSource,int previousExperience,int newExperience,int experienceGained,int previousLevel,int newLevel,ItemProgressStage previousItemStage,ItemProgressStage newItemStage,int progressionEarnedGold,int threshold,int timeSeconds){}
