package com.lolfm.champion;

import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.simulator.ExperienceSource;
import com.lolfm.simulator.GoldAwardService;
import com.lolfm.simulator.GoldSource;
import com.lolfm.simulator.ItemProgressStage;
import com.lolfm.simulator.KillRewardResolver;
import com.lolfm.simulator.PlayerState;
import com.lolfm.simulator.ProgressionRewardResolver;
import com.lolfm.simulator.ProgressionRuleConfig;
import com.lolfm.simulator.TeamState;
import java.util.ArrayList;
import java.util.List;

final class ChampionMatchupAuditPlayerFactory {
    private final GoldAwardService gold = new GoldAwardService();
    private final ProgressionRewardResolver experience = new ProgressionRewardResolver();

    PlayerBundle create(
            Position position,
            AuditState state,
            int skillGap
    ) {
        int value = 14 + skillGap;
        PlayerState player = new PlayerState(
                "audit-" + position, position,
                new PlayerAttributes(value, value, value, value), 500);
        PlayerState ally = new PlayerState(
                "audit-ally-" + position, position,
                new PlayerAttributes(14, 14, 14, 14), 500);
        TeamState team = new TeamState("audit-team", List.of(player, ally));
        awardBaseState(player, team, state);
        return new PlayerBundle(player, team, state);
    }

    GrowthOutcome applyGrowth(
            PlayerBundle bundle,
            ChampionMatchupIndependentScenario.GrowthPackage growth
    ) {
        PlayerState player = bundle.player();
        int startKills = player.getKills();
        int startLevel = player.getProgressionState().getLevel();
        int startItem = player.getProgressionState().getItemStage().ordinal();
        PlayerState victim = new PlayerState(
                "audit-victim", player.getPosition(),
                new PlayerAttributes(14, 14, 14, 14), 500);
        TeamState enemy = new TeamState("audit-enemy", List.of(victim));
        for (int index = 0; index < growth.kills(); index++) {
            victim.respawn();
            new KillRewardResolver().award(
                    10 + index, bundle.team(), player, enemy, victim,
                    List.of(), 1, false, 0, new ArrayList<>());
        }
        int targetLevel = Math.min(
                ProgressionRuleConfig.MAX_LEVEL,
                bundle.state().level() + growth.levels());
        int xp = ProgressionRuleConfig.xpForLevel(targetLevel)
                - player.getProgressionState().getTotalExperience();
        if (xp > 0) {
            experience.awardExperience(
                    player, ExperienceSource.LANE_ECONOMY, xp, 30);
        }
        int targetItem = Math.min(
                ItemProgressStage.FULL_BUILD.ordinal(),
                bundle.state().itemStage().ordinal() + growth.itemStages());
        int targetGold = ProgressionRuleConfig.itemThreshold(
                ItemProgressStage.values()[targetItem]);
        int neededGold = targetGold
                - player.getProgressionState().getProgressionEarnedGold();
        if (neededGold > 0) {
            gold.awardGold(bundle.team(), player, neededGold,
                    GoldSource.FARM, false, 30);
        }
        int achievedKills = player.getKills() - startKills;
        int achievedLevels = player.getProgressionState().getLevel() - startLevel;
        int achievedItems = player.getProgressionState().getItemStage().ordinal()
                - startItem;
        boolean eligible = achievedKills >= growth.kills()
                && achievedLevels >= growth.levels()
                && achievedItems >= growth.itemStages();
        String reason = eligible ? "NONE"
                : achievedLevels < growth.levels() ? "LEVEL_18_CAP"
                : achievedItems < growth.itemStages() ? "FULL_BUILD_CAP"
                : "PARTIAL_REWARD";
        return new GrowthOutcome(
                growth.kills(), achievedKills,
                growth.levels(), achievedLevels,
                growth.itemStages(), achievedItems,
                !eligible, reason, eligible);
    }

    private void awardBaseState(
            PlayerState player,
            TeamState team,
            AuditState state
    ) {
        int xp = ProgressionRuleConfig.xpForLevel(state.level());
        if (xp > 0) {
            experience.awardExperience(
                    player, ExperienceSource.LANE_ECONOMY, xp, 1);
        }
        int target = ProgressionRuleConfig.itemThreshold(state.itemStage());
        if (target > 0) {
            gold.awardGold(team, player, target, GoldSource.FARM, false, 1);
        }
    }

    enum AuditState {
        LEVEL_6_FIRST_CORE(6, ItemProgressStage.FIRST_CORE),
        LEVEL_11_SECOND_CORE(11, ItemProgressStage.SECOND_CORE),
        LEVEL_16_THIRD_CORE(16, ItemProgressStage.THIRD_CORE),
        LEVEL_18_FULL_BUILD(18, ItemProgressStage.FULL_BUILD);

        private final int level;
        private final ItemProgressStage itemStage;

        AuditState(int level, ItemProgressStage itemStage) {
            this.level = level;
            this.itemStage = itemStage;
        }

        int level() { return level; }
        ItemProgressStage itemStage() { return itemStage; }
    }

    record PlayerBundle(
            PlayerState player,
            TeamState team,
            AuditState state
    ) { }

    record GrowthOutcome(
            int requestedKillLead,
            int achievedKillLead,
            int requestedLevelLead,
            int achievedLevelLead,
            int requestedItemStageLead,
            int achievedItemStageLead,
            boolean leadCapped,
            String capReason,
            boolean eligibleForRequestedPackageRate
    ) { }
}
