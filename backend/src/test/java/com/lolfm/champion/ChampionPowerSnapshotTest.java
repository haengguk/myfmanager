package com.lolfm.champion;

import static org.assertj.core.api.Assertions.*;
import com.lolfm.domain.PlayerSnapshot;
import com.lolfm.domain.Position;
import com.lolfm.simulator.SnapshotFactory;
import org.junit.jupiter.api.Test;

class ChampionPowerSnapshotTest {
    @Test void pastSnapshotIsImmutableAndLatestLevelAndItemAreReevaluatedWithoutContext() {
        var f=new ChampionPowerTestFixture(true); SnapshotFactory snapshots=new SnapshotFactory(f.champions);
        PlayerSnapshot before=player(snapshots.create(f.state),Position.TOP); double old=before.getChampion().powerProfile().currentNonContextModifier();
        ChampionPowerTestFixture.grow(f.blue.playerAt(Position.TOP),5500,10500);
        PlayerSnapshot after=player(snapshots.create(f.state),Position.TOP);
        assertThat(after.getChampion().powerProfile().currentLevelModifier()).isNotEqualTo(before.getChampion().powerProfile().currentLevelModifier());
        assertThat(after.getChampion().powerProfile().currentItemModifier()).isNotEqualTo(before.getChampion().powerProfile().currentItemModifier());
        assertThat(before.getChampion().powerProfile().currentNonContextModifier()).isEqualTo(old);
        assertThat(f.state.getChampionPowerExecutionStats().snapshot().samples()).isEmpty();
    }
    private PlayerSnapshot player(com.lolfm.domain.MatchSnapshot snapshot,Position position){return snapshot.getPlayerSnapshots().stream().filter(p->p.getTeamSide()==com.lolfm.simulator.TeamSide.BLUE&&p.getPosition()==position).findFirst().orElseThrow();}
}
