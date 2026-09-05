package com.lolfm.career;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.league.LeagueFixtureCompletionReceiptV2;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reads the existing verified League/Competition ledgers; owns no second result ledger. */
final class CareerDomesticEvidence {
    private CareerDomesticEvidence() {}
    static List<CareerDomesticRanking.Match> league(JdbcTemplate jdbc, ObjectMapper json,
                                                  String career, int year) {
        return jdbc.query("""
                SELECT r.receipt_json, r.receipt_hash, r.receipt_canonical,
                       s.season_id, f.fixture_id, f.first_team_code, f.second_team_code,
                       f.bound_series_id, l.schedule_identity, s.league_id
                FROM career_save s
                JOIN career_calendar_state c ON c.career_id = s.career_id
                JOIN league_season l ON l.season_id = s.season_id AND l.league_id = s.league_id
                JOIN league_fixture f ON f.season_id = s.season_id
                JOIN league_standings_application a ON a.season_id = f.season_id AND a.fixture_id = f.fixture_id
                JOIN league_completion_receipt r ON r.receipt_hash = a.receipt_hash
                  AND r.season_id = f.season_id AND r.fixture_id = f.fixture_id
                WHERE s.career_id = ? AND c.active_calendar_season_year = ?
                  AND f.lifecycle_status = 'COMPLETED'
                ORDER BY f.round_number, f.fixture_id
                """, (r, row) -> {
            var envelope = read(json, r.getString(1), LeagueFixtureCompletionReceiptV2.class);
            var receipt = envelope.fixtureReceipt();
            if (!envelope.canonicalFixtureReceiptHash().equals(r.getString(2))
                    || !envelope.canonicalText().equals(r.getString(3))
                    || !receipt.seasonId().equals(r.getString(4))
                    || !receipt.fixtureId().equals(r.getString(5))
                    || !receipt.firstTeamCode().equals(r.getString(6))
                    || !receipt.secondTeamCode().equals(r.getString(7))
                    || !receipt.boundSeriesId().equals(r.getString(8))
                    || !receipt.scheduleIdentity().equals(r.getString(9)) || !envelope.leagueId().equals(r.getString(10)))
                throw new IllegalStateException("DOMESTIC_LEAGUE_RECEIPT_SCOPE_MISMATCH");
            validateGames(receipt.firstTeamCode(), receipt.secondTeamCode(), receipt.firstTeamGameWins(), receipt.secondTeamGameWins(), receipt.orderedGameReceipts());
            return new CareerDomesticRanking.Match("LEAGUE:" + receipt.seasonId() + ":" + receipt.fixtureId(),
                    receipt.firstTeamCode(), receipt.secondTeamCode(), receipt.firstTeamGameWins(),
                    receipt.secondTeamGameWins(), receipt.orderedGameReceipts().stream()
                    .map(g -> new CareerDomesticRanking.Game(g.winnerTeamCode(), g.durationSeconds())).toList());
        }, career, year);
    }
    static List<CareerDomesticRanking.Match> competition(JdbcTemplate jdbc, ObjectMapper json,
            String career, int year, String competition, String stage) {
        return jdbc.query("""
                SELECT r.receipt_json, r.receipt_hash, r.receipt_canonical, f.match_id,
                       f.first_team_code, f.second_team_code, f.series_id, b.binding_canonical
                FROM career_competition_fixture f
                JOIN career_competition_result_detail d
                  ON d.career_id = f.career_id AND d.calendar_season_year = f.calendar_season_year
                 AND d.competition_id = f.competition_id AND d.match_id = f.match_id
                JOIN career_competition_completion_receipt r ON r.receipt_hash = d.receipt_hash
                JOIN career_competition_series_binding b ON b.binding_hash = r.binding_hash
                JOIN career_competition_application a ON a.receipt_hash = r.receipt_hash
                 AND a.career_id = f.career_id AND a.calendar_season_year = f.calendar_season_year
                 AND a.competition_id = f.competition_id AND a.match_id = f.match_id
                WHERE f.career_id = ? AND f.calendar_season_year = ? AND f.competition_id = ?
                  AND (f.stage_id = ? OR ? = 'LEGEND_RISE' AND f.stage_id IN ('LEGEND','RISE')) AND f.lifecycle_status = 'COMPLETED'
                ORDER BY f.match_order, f.match_id
                """, (r, row) -> {
            var receipt = read(json, r.getString(1), CareerCompetitionFixtureCompletionReceiptV1.class);
            var binding = CareerCompetitionSeriesBindingV1.restoreCanonical(r.getString(8));
            if (!receipt.receiptHash().equals(r.getString(2)) || !receipt.canonicalText().equals(r.getString(3))
                    || !receipt.careerId().equals(career) || receipt.seasonYear() != year
                    || !receipt.competitionId().equals(competition) || !receipt.matchId().equals(r.getString(4))
                    || !receipt.firstTeamCode().equals(r.getString(5)) || !receipt.secondTeamCode().equals(r.getString(6))
                    || !receipt.seriesId().equals(r.getString(7)) || !receipt.bindingHash().equals(binding.bindingHash()))
                throw new IllegalStateException("DOMESTIC_COMPETITION_RECEIPT_SCOPE_MISMATCH");
            validateGames(receipt.firstTeamCode(), receipt.secondTeamCode(), receipt.firstScore(), receipt.secondScore(), receipt.orderedGames());
            return new CareerDomesticRanking.Match("COMPETITION:" + competition + ":" + receipt.matchId(),
                    receipt.firstTeamCode(), receipt.secondTeamCode(), receipt.firstScore(), receipt.secondScore(),
                    receipt.orderedGames().stream().map(g -> new CareerDomesticRanking.Game(g.winnerTeamCode(), g.durationSeconds())).toList());
        }, career, year, competition, stage, stage);
    }
    private static void validateGames(String first, String second, int firstWins, int secondWins,
            List<com.lolfm.league.LeagueFixtureGameReceiptV1> games) {
        if (games.size() != firstWins + secondWins || games.stream().filter(g -> first.equals(g.winnerTeamCode())).count() != firstWins
                || games.stream().filter(g -> second.equals(g.winnerTeamCode())).count() != secondWins)
            throw new IllegalStateException("DOMESTIC_GAME_WINNER_EVIDENCE_REQUIRED");
    }
    static <T> T read(ObjectMapper json, String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new IllegalStateException("DOMESTIC_EVIDENCE_JSON_INVALID", invalid);
        }
    }
}
