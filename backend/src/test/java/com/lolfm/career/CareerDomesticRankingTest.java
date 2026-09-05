package com.lolfm.career;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CareerDomesticRankingTest {
    private static CareerDomesticRanking.Match match(String id, String a, String b, int aw, int bw, int seconds) {
        List<CareerDomesticRanking.Game> games = new ArrayList<>();
        for (int i = 0; i < aw; i++) games.add(new CareerDomesticRanking.Game(a, seconds));
        for (int i = 0; i < bw; i++) games.add(new CareerDomesticRanking.Game(b, seconds + 50));
        return new CareerDomesticRanking.Match(id, a, b, aw, bw, games);
    }

    @Test void nonQualifyingPlacesHaveStablePlacementWithoutInventedMatchResults() {
        var teams = List.of("AA", "BB", "CC", "DD");
        var strength = Map.of("AA", 0, "BB", 0, "CC", 0, "DD", 0);
        var outcomes = new LinkedHashMap<String, CareerDomesticTiebreak.Outcome>();
        CareerDomesticTiebreak.Progress progress;
        do {
            progress = CareerDomesticTiebreak.advance("TB_PLACEMENT", teams, outcomes, List.of(), strength, 2);
            progress.pending().forEach(m -> outcomes.put(m.matchId(), new CareerDomesticTiebreak.Outcome(m.first(), m.second())));
        } while (progress.ranking().isEmpty());
        assertThat(outcomes).hasSize(3);
        assertThat(progress.ranking()).containsExactlyInAnyOrderElementsOf(teams);
        var shared = CareerDomesticTiebreak.advance("TB_SHARED", teams, Map.of(), List.of(), strength, 0);
        assertThat(shared.pending()).isEmpty();
        assertThat(shared.ranking()).hasSize(4);
    }

    @Test void halfPointWeightsAndCompetitionRanksUseTheOpponentsGroup() {
        var games = List.of(match("1", "AA", "XX", 2, 0, 1200), match("2", "BB", "YY", 2, 0, 1200),
                match("3", "AA", "YY", 0, 2, 1200), match("4", "BB", "XX", 0, 2, 1200),
                match("5", "CC", "ZZ", 2, 0, 1200));
        var teams = List.of("AA", "BB", "CC", "XX", "YY", "ZZ");
        var groups = Map.of("AA", "BARON", "BB", "BARON", "CC", "BARON", "XX", "ELDER", "YY", "ELDER", "ZZ", "ELDER");
        var decision = CareerDomesticRanking.cup(List.of("AA", "BB", "CC"), teams, groups, games, false);
        // XX/YY are joint first, ZZ is third: 5.0, 5.0, 4.0 rather than opponent win totals.
        assertThat(decision.strengthTwice()).containsEntry("AA", 10).containsEntry("BB", 10).containsEntry("CC", 8);
        var integrated = CareerDomesticRanking.cup(List.of("AA", "CC"), teams, groups, games, true);
        assertThat(integrated.strengthTwice().get("CC")).isLessThan(decision.strengthTwice().get("CC"));
    }

    @Test void winningGameMeanIncludesGamesWonInsideLostSeriesAndUsesExactFractions() {
        var matches = List.of(match("1", "AA", "BB", 1, 2, 1000), match("2", "AA", "CC", 2, 0, 2000));
        var againstTied = CareerDomesticRanking.averages(List.of("AA", "BB"), Set.of("AA", "BB"), matches);
        assertThat(againstTied.get("AA")).isEqualTo(new CareerDomesticRanking.Fraction(1000, 1));
        assertThat(CareerDomesticRanking.averages(List.of("AA"), null, matches).get("AA"))
                .isEqualTo(new CareerDomesticRanking.Fraction(5000, 3));
        assertThat(new CareerDomesticRanking.Fraction(5000, 3).compareTo(new CareerDomesticRanking.Fraction(3333, 2))).isPositive();
    }

    @Test void noWinningSampleStaysUnknownAndPartialTiesContinueAtTheNextCriterion() {
        var matches = List.of(match("1", "AA", "XX", 0, 2, 1200), match("2", "BB", "XX", 0, 2, 1200));
        var decision = CareerDomesticRanking.cup(List.of("AA", "BB"), List.of("AA", "BB", "XX"),
                Map.of("AA", "BARON", "BB", "BARON", "XX", "ELDER"), matches, false);
        assertThat(decision.resolved()).isFalse();
        assertThat(CareerDomesticRanking.averages(List.of("AA", "BB"), null, matches)).isEmpty();
        assertThat(decision.groups().getFirst().appliedCriteria())
                .contains("MEAN_WON_GAMES_ALL_OPPONENTS_NO_COMPARABLE_SAMPLE").doesNotHaveDuplicates();
        assertThatThrownBy(() -> new CareerDomesticRanking.Fraction(0, 0)).hasMessage("EMPTY_AVERAGE");
    }

    @Test void headToHeadOverridesTheOldOverallGameWinSurrogate() {
        List<CareerDomesticRanking.Match> matches = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            matches.add(match("AB" + i, "AA", "BB", 2, 0, 1200));
            matches.add(match("AC" + i, "AA", "CC", 2, 1, 1200));
            matches.add(match("AD" + i, "AA", "DD", 0, 2, 1200));
            matches.add(match("AE" + i, "AA", "EE", 0, 2, 1200));
            matches.add(match("BC" + i, "BB", "CC", 2, 1, 1200));
            matches.add(match("BD" + i, "BB", "DD", 2, 1, 1200));
            matches.add(match("BE" + i, "BB", "EE", 1, 2, 1200));
        }
        var decision = CareerDomesticRanking.regular(List.of("AA", "BB"), List.of("AA", "BB", "CC", "DD", "EE"), Map.of(), matches);
        assertThat(decision.records().get("AA").wins()).isEqualTo(decision.records().get("BB").wins());
        assertThat(decision.records().get("AA").difference()).isEqualTo(decision.records().get("BB").difference());
        assertThat(decision.records().get("AA").gameWins()).isLessThan(decision.records().get("BB").gameWins());
        assertThat(decision.ordered()).containsExactly("AA", "BB");
        assertThat(decision.resolved()).isTrue();
    }

    @Test void threeTeamMiniLeagueFixesTheUniqueTeamThenReevaluatesTheRemainingPair() {
        var matches = List.of(match("AB", "AA", "BB", 2, 0, 1000), match("AC", "AA", "CC", 2, 0, 1000),
                match("BC1", "BB", "CC", 2, 0, 1000), match("BC2", "BB", "CC", 1, 2, 1000));
        var groups = CareerDomesticRanking.regularTie(new CareerDomesticRanking.Group(List.of("CC", "AA", "BB"), List.of("MATCH_WINS", "GAME_DIFFERENTIAL")), matches);
        assertThat(groups.stream().flatMap(g -> g.teams().stream())).containsExactly("AA", "BB", "CC");
        assertThat(groups).allSatisfy(g -> assertThat(g.teams()).hasSize(1));
    }

    @ParameterizedTest @CsvSource({"2,1", "3,2", "4,4", "5,5", "6,7", "7,9", "8,12", "9,13", "10,15"})
    void allMultiTeamBracketsResumeWithoutDuplicateMatches(int teamCount, int matchCount) {
        List<String> teams = java.util.stream.IntStream.rangeClosed(1, teamCount).mapToObj(n -> "T" + n).toList();
        Map<String, Integer> strength = new LinkedHashMap<>(); teams.forEach(t -> strength.put(t, 0));
        Map<String, CareerDomesticTiebreak.Outcome> outcomes = new LinkedHashMap<>();
        CareerDomesticTiebreak.Progress progress;
        for (int step = 0; ; step++) {
            assertThat(step).isLessThan(20);
            progress = CareerDomesticTiebreak.advance("TB_TEST", teams, outcomes, List.of(), strength);
            assertThat(CareerDomesticTiebreak.advance("TB_TEST", teams, outcomes, List.of(), strength)).isEqualTo(progress);
            if (!progress.ranking().isEmpty()) break;
            assertThat(progress.pending()).isNotEmpty();
            for (var match : progress.pending()) {
                assertThat(outcomes.put(match.matchId(), new CareerDomesticTiebreak.Outcome(match.first(), match.second()))).isNull();
            }
        }
        assertThat(outcomes).hasSize(matchCount);
        assertThat(progress.ranking()).containsExactlyInAnyOrderElementsOf(teams).doesNotHaveDuplicates();
        assertThat(progress.pending()).isEmpty();
    }

    @Test void duplicatedMatchIdentityCannotInflateCarriedRecords() {
        var game = match("league:season:fixture", "AA", "BB", 2, 0, 1000);
        assertThatThrownBy(() -> CareerDomesticRanking.records(List.of("AA", "BB"), List.of(game, game)))
                .hasMessage("RANKING_MATCH_SCOPE_OR_DUPLICATE");
    }
}
