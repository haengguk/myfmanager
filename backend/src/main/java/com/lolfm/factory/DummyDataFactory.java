package com.lolfm.factory;

import com.lolfm.domain.Player;
import com.lolfm.domain.PlayerAttributes;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DummyDataFactory {

    public Team createBlueTeam() {
        return new Team(
                "블루 미라지",
                List.of(
                        new Player("Atlas", Position.TOP, new PlayerAttributes(15, 13, 15, 15)),
                        new Player("River", Position.JUNGLE, new PlayerAttributes(15, 16, 13, 16)),
                        new Player("Pulse", Position.MID, new PlayerAttributes(17, 14, 16, 16)),
                        new Player("Nova", Position.ADC, new PlayerAttributes(18, 14, 18, 15)),
                        new Player("Bell", Position.SUPPORT, new PlayerAttributes(13, 11, 8, 17))
                )
        );
    }

    public Team createRedTeam() {
        return new Team(
                "레드 템페스트",
                List.of(
                        new Player("Blade", Position.TOP, new PlayerAttributes(14, 14, 14, 14)),
                        new Player("Shade", Position.JUNGLE, new PlayerAttributes(15, 16, 13, 15)),
                        new Player("Flux", Position.MID, new PlayerAttributes(17, 15, 16, 16)),
                        new Player("Viper", Position.ADC, new PlayerAttributes(18, 14, 18, 14)),
                        new Player("Mint", Position.SUPPORT, new PlayerAttributes(13, 11, 8, 17))
                )
        );
    }
}
