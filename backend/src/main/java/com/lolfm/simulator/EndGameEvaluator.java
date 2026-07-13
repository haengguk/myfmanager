package com.lolfm.simulator;

import org.springframework.stereotype.Component;

@Component
public class EndGameEvaluator {

    public EndGameDecision evaluateAfterTick(GameState state) {
        if (state.isFinished()) {
            return decisionFromState(state);
        }
        if (state.getCurrentTimeSeconds() >= MatchSimulator.SIMULATION_SAFETY_TIMEOUT_SECONDS) {
            state.timeout();
            return decisionFromState(state);
        }
        return EndGameDecision.continueGame();
    }

    public String buildGameEndMessage(EndGameDecision decision) {
        if (decision.getReason() == GameEndReason.NEXUS_DESTROYED) {
            return decision.getWinner() + "가 적 넥서스를 파괴하며 승리합니다.";
        }
        return "시뮬레이션 안전 제한에 도달해 경기를 중단합니다. 승자는 판정하지 않습니다.";
    }

    private EndGameDecision decisionFromState(GameState state) {
        String winner = state.getWinnerSide() == null
                ? null
                : state.getTeamState(state.getWinnerSide()).getTeamName();
        return new EndGameDecision(true, winner, state.getEndReason());
    }

    public static final class EndGameDecision {
        private final boolean finished;
        private final String winner;
        private final GameEndReason reason;

        public EndGameDecision(boolean finished, String winner, GameEndReason reason) {
            this.finished = finished;
            this.winner = winner;
            this.reason = reason;
        }

        public static EndGameDecision continueGame() {
            return new EndGameDecision(false, null, null);
        }

        public boolean isFinished() { return finished; }
        public String getWinner() { return winner; }
        public GameEndReason getReason() { return reason; }
    }
}
