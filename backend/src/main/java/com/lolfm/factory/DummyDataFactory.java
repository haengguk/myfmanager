package com.lolfm.factory;

import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.player.PlayerId;
import java.util.List;
import org.springframework.stereotype.Component;

/** Legacy/demo fixture factory. Real LCK rosters are assembled by LckTeamAssembler. */
@Component
public class DummyDataFactory {

    public Team createBlueTeam() {
        return new Team(
                "블루 미라지",
                List.of(
                        fixture("player-fixture-blue-atlas", "Atlas", Position.TOP, 15, 13, 15, 15),
                        fixture("player-fixture-blue-river", "River", Position.JUNGLE, 15, 16, 13, 16),
                        fixture("player-fixture-blue-pulse", "Pulse", Position.MID, 17, 14, 16, 16),
                        fixture("player-fixture-blue-nova", "Nova", Position.ADC, 18, 14, 18, 15),
                        fixture("player-fixture-blue-bell", "Bell", Position.SUPPORT, 13, 11, 8, 17)
                )
        );
    }

    public Team createRedTeam() {
        return new Team(
                "레드 템페스트",
                List.of(
                        fixture("player-fixture-red-blade", "Blade", Position.TOP, 14, 14, 14, 14),
                        fixture("player-fixture-red-shade", "Shade", Position.JUNGLE, 15, 16, 13, 15),
                        fixture("player-fixture-red-flux", "Flux", Position.MID, 17, 15, 16, 16),
                        fixture("player-fixture-red-viper", "Viper", Position.ADC, 18, 14, 18, 14),
                        fixture("player-fixture-red-mint", "Mint", Position.SUPPORT, 13, 11, 8, 17)
                )
        );
    }

    private Player fixture(String playerId, String name, Position position,
                           int mechanics, int aggression, int farming, int teamfighting) {
        return new Player(new PlayerId(playerId), name, position,
                new PlayerAttributes(mechanics, aggression, farming, teamfighting));
    }
}
