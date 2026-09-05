package com.lolfm.career;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

/** Domestic transition coordinator. Invoked only inside the Competition store transaction. */
final class CareerDomesticCompetition {
    static final String SIDE_POLICY = "LCK_ROFS_FIRST_PICK_OTHER_TEAM_RED_LOSER_ROFS_V1";
    static final String SCHEDULE_POLICY = "LCK_TIE_SAME_DUE_DAY_ORDERED_BEFORE_DEPENDENTS_V1";
    private final CareerCompetitionRelationalStore store;
    private final JdbcTemplate db;
    CareerDomesticCompetition(CareerCompetitionRelationalStore store) { this.store = store; this.db = store.jdbc; }

    void advance(String career, int year, String competition) {
        switch (competition) {
            case "LCK_CUP" -> cup(career, year);
            case "LCK_REGULAR_R1_R2" -> r1r2(career, year);
            case "LCK_REGULAR_R3_R4" -> r3r4(career, year);
            case "LCK_PLAY_IN", "LCK_PLAYOFFS" -> playoffs(career, year);
            default -> { }
        }
    }

    void r1r2(String career, int year) {
        if (store.findCycle(career, year, false).getFirst().r1r2ImportHash() != null) return;
        List<CareerDomesticRanking.Match> matches = CareerDomesticEvidence.league(db, store.json, career, year);
        if (matches.size() != 90) return;
        List<String> teams = teams(matches);
        var decision = CareerDomesticRanking.regular(teams, teams, Map.of(), matches);
        var order = resolve(career, year, "LCK_REGULAR_R1_R2", "R1_R2", decision, matches, LocalDate.of(year, 5, 31));
        refresh(career, year, "LCK_REGULAR_R1_R2");
        if (order.isEmpty()) return;
        var source = db.queryForMap("""
                SELECT l.schedule_identity, l.revision FROM career_save c JOIN league_season l ON l.season_id = c.season_id
                WHERE c.career_id = ?
                """, career);
        var binding = store.careerBinding(career);
        store.sealR1R2(career, year, binding.managedTeamCode(), binding.rootSeed(),
                (String) source.get("schedule_identity"), ((Number) source.get("revision")).longValue(), seeded(order, decision.records()));
    }

    private void cup(String career, int year) {
        if (count("career_lck_cup_standing", career, year, null) == 10) {
            store.deriveCupPlayoffSeeds(career, year); store.resolveCupFixtures(career, year); return;
        }
        List<CareerDomesticRanking.Match> matches = CareerDomesticEvidence.competition(db, store.json, career, year, "LCK_CUP", "GROUP_BATTLE");
        if (matches.size() != 25) return;
        Map<String, String> membership = membership(career, year, "LCK_CUP", "CUP_GROUP_%");
        List<String> teams = new ArrayList<>(membership.keySet());
        if (teams.size() != 10) throw new IllegalStateException("CUP_MEMBERSHIP_REQUIRED");
        Map<String, List<String>> groupOrder = new LinkedHashMap<>();
        Map<String, CareerDomesticRanking.Decision> decisions = new LinkedHashMap<>();
        LocalDate due = LocalDate.of(year, 2, 1);
        for (String name : List.of("BARON", "ELDER")) {
            var candidates = teams.stream().filter(t -> name.equals(membership.get(t))).toList();
            var decision = CareerDomesticRanking.cup(candidates, teams, membership, matches, false);
            decisions.put(name, decision);
        }
        int groupTieGames = decisions.values().stream().mapToInt(CareerDomesticCompetition::tieGameBudget).sum();
        for (String name : List.of("BARON", "ELDER")) groupOrder.put(name,
                resolve(career, year, "LCK_CUP", "CUP_" + name, decisions.get(name), matches, due, groupTieGames));
        if (groupOrder.values().stream().anyMatch(List::isEmpty)) return;
        Map<String, Integer> points = new LinkedHashMap<>(Map.of("BARON", 0, "ELDER", 0));
        db.query("""
                SELECT winner_team_code, group_point_value FROM career_competition_fixture
                WHERE career_id = ? AND calendar_season_year = ? AND competition_id = 'LCK_CUP' AND stage_id = 'GROUP_BATTLE'
                """, (RowCallbackHandler) r -> points.compute(membership.get(r.getString(1)), (k, v) -> {
                    try { return v + r.getInt(2); } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
                }), career, year);
        var rows = decisions.get("BARON").records();
        int diff = groupOrder.get("BARON").stream().mapToInt(t -> rows.get(t).difference()).sum();
        String winningGroup = !points.get("BARON").equals(points.get("ELDER"))
                ? points.get("BARON") > points.get("ELDER") ? "BARON" : "ELDER"
                : diff != 0 ? diff > 0 ? "BARON" : "ELDER" : mixedGroup(career, year, groupOrder, membership, due, hash(json(matches)));
        if (winningGroup == null) return;
        List<String> winner = groupOrder.get(winningGroup), loser = groupOrder.get(winningGroup.equals("BARON") ? "ELDER" : "BARON");
        List<String> eligible = new ArrayList<>(winner.subList(2, 5)); eligible.addAll(loser.subList(1, 4));
        var integrated = CareerDomesticRanking.cup(eligible, teams, membership, matches, true);
        List<String> playIn = resolve(career, year, "LCK_CUP", "CUP_PLAY_IN", integrated, matches, due);
        if (playIn.isEmpty()) return;
        String standingsHash = hash(json(List.of(groupOrder, playIn, winningGroup, points, matches, CareerDomesticRanking.POLICY)));
        var means = CareerDomesticRanking.averages(teams, null, matches);
        for (String group : List.of("BARON", "ELDER")) {
            List<String> ordered = groupOrder.get(group);
            for (int i = 0; i < ordered.size(); i++) {
                String team = ordered.get(i); var row = rows.get(team); var mean = means.get(team);
                int strength = decisions.get(group).strengthTwice().get(team);
                db.update("""
                        INSERT INTO career_lck_cup_standing(career_id, calendar_season_year, group_id, group_rank,
                          team_code, match_wins, match_losses, game_wins, game_losses, strength_of_victory,
                          win_time_seconds, tie_break_trace, standings_hash, strength_twice, winning_game_seconds, winning_game_count)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, career, year, group, i + 1, team, row.wins(), row.losses(), row.gameWins(), row.gameLosses(),
                        strength / 2, mean == null ? 0 : mean.numerator() / mean.denominator(),
                        CareerDomesticRanking.POLICY + ":EXACT_METRICS_IN_DOMESTIC_DECISION", standingsHash, strength,
                        mean == null ? null : mean.numerator(), mean == null ? null : mean.denominator());
            }
        }
        store.insertDerivedSeeds(career, year, "CUP_PLAYOFF_SEED", List.of(winner.get(0), winner.get(1), loser.get(0)), standingsHash, 1);
        store.insertDerivedSeeds(career, year, "CUP_PLAY_IN_SEED", playIn, standingsHash, 1);
        store.deriveCupPlayoffSeeds(career, year); store.resolveCupFixtures(career, year);
    }

    private String mixedGroup(String career, int year, Map<String, List<String>> orders,
                              Map<String, String> membership, LocalDate date, String input) {
        var completed = outcomes(career, year, "LCK_CUP");
        Map<String, Integer> score = new LinkedHashMap<>(Map.of("BARON", 0, "ELDER", 0));
        List<String> games = new ArrayList<>();
        for (int game = 1; game <= 5; game++) {
            String id = "TB_CUP_GROUP_G" + game;
            var result = completed.get(id);
            if (result == null) {
                int rank = game % 2 == 1 ? 4 : 0;
                String first = orders.get("BARON").get(rank), second = orders.get("ELDER").get(rank);
                // The first group's RoFS is a seeded draw; subsequent games use the losing group.
                String owner = game == 1 ? (CareerDomesticRanking.draw(career + year + root(career), id).charAt(0) % 2 == 0 ? "BARON" : "ELDER")
                        : membership.get(completed.get("TB_CUP_GROUP_G" + (game - 1)).loser());
                if (owner.equals("ELDER")) { String swap = first; first = second; second = swap; }
                insertTie(career, year, "LCK_CUP", id, first, second, "BO1", date, "CUP_GROUP_TIEBREAKER", input);
                save(career, year, "LCK_CUP", "CUP_GROUP_WINNER", input,
                        new MixedGroupState(orders, score, games, id, "ALTERNATING_RANK_5_RANK_1_SHARED_FEARLESS_V1"), false);
                return null;
            }
            String winner = membership.get(result.winner()); score.compute(winner, (k, v) -> v + 1); games.add(id);
            if (score.get(winner) == 3) {
                save(career, year, "LCK_CUP", "CUP_GROUP_WINNER", input,
                        new MixedGroupState(orders, score, games, null, "ALTERNATING_RANK_5_RANK_1_SHARED_FEARLESS_V1"), true);
                return winner;
            }
        }
        throw new IllegalStateException("CUP_GROUP_DECISIVE_RESULT_REQUIRED");
    }

    private void r3r4(String career, int year) {
        if (count("career_competition_fixture", career, year, "LCK_PLAY_IN") > 0) { playoffs(career, year); return; }
        var stage = CareerDomesticEvidence.competition(db, store.json, career, year, "LCK_REGULAR_R3_R4", "LEGEND_RISE");
        if (stage.size() != 40) return;
        var prior = CareerDomesticEvidence.league(db, store.json, career, year);
        if (prior.size() != 90) throw new IllegalStateException("R1_R2_MATCH_LEDGER_REQUIRED");
        List<CareerDomesticRanking.Match> matches = new ArrayList<>(prior); matches.addAll(stage);
        Map<String, String> membership = new LinkedHashMap<>();
        db.query("""
                SELECT seed_number, team_code FROM career_competition_seed WHERE career_id = ? AND calendar_season_year = ?
                  AND competition_id = 'LCK_REGULAR_R3_R4' AND seed_scope = 'R1_R2_FINAL_RANK' ORDER BY seed_number
                """, (RowCallbackHandler) r -> membership.put(r.getString(2), r.getInt(1) <= 5 ? "LEGEND" : "RISE"), career, year);
        List<String> teams = new ArrayList<>(membership.keySet());
        Map<String, List<String>> groups = new LinkedHashMap<>();
        Map<String, CareerDomesticRanking.Record> rows = CareerDomesticRanking.records(teams, matches);
        Map<String, CareerDomesticRanking.Decision> decisions = new LinkedHashMap<>();
        for (String group : List.of("LEGEND", "RISE")) {
            var decision = CareerDomesticRanking.regular(teams.stream().filter(t -> group.equals(membership.get(t))).toList(), teams, membership, matches);
            decisions.put(group, decision);
        }
        int groupTieGames = decisions.entrySet().stream().mapToInt(e -> tieGameBudget(e.getValue(), "R3_R4_" + e.getKey())).sum();
        for (String group : List.of("LEGEND", "RISE")) groups.put(group,
                resolve(career, year, "LCK_REGULAR_R3_R4", "R3_R4_" + group, decisions.get(group), matches, LocalDate.of(year, 8, 23), groupTieGames));
        if (groups.values().stream().anyMatch(List::isEmpty)) return;
        String input = hash(json(List.of(groups, matches, CareerDomesticRanking.POLICY)));
        List<String> legend = groups.get("LEGEND"), rise = groups.get("RISE");
        for (int i = 0; i < 4; i++) store.insertTransitionOutput(career, year, "LCK_REGULAR_R3_R4", "LCK_PLAYOFF_SEED_" + (i + 1), legend.get(i));
        store.insertTransitionOutput(career, year, "LCK_REGULAR_R3_R4", "LCK_SEASON_PLACE_9", rise.get(3));
        store.insertTransitionOutput(career, year, "LCK_REGULAR_R3_R4", "LCK_SEASON_PLACE_10", rise.get(4));
        var seeds = seeded(List.of(legend.get(4), rise.get(0), rise.get(1), rise.get(2)), rows);
        store.insertSeeds(career, year, "LCK_PLAY_IN", "PLAY_IN_SEED", input, seeds);
        materialize(career, year, "LCK_PLAY_IN", input, seeds);
        refresh(career, year, "LCK_PLAY_IN");
    }

    private List<String> resolve(String career, int year, String competition, String scope,
            CareerDomesticRanking.Decision decision, List<CareerDomesticRanking.Match> matches, LocalDate due) {
        return resolve(career, year, competition, scope, decision, matches, due, tieGameBudget(decision, scope));
    }
    private static int tieGameBudget(CareerDomesticRanking.Decision decision) {
        return tieGameBudget(decision, "ALL");
    }
    private static int tieGameBudget(CareerDomesticRanking.Decision decision, String scope) {
        int lastRelevant = scope.equals("R1_R2") ? 6 : scope.equals("R3_R4_RISE") ? 3 : decision.ordered().size();
        int first = 1, count = 0;
        for (var group : decision.groups()) {
            int n = group.teams().size();
            if (first <= lastRelevant && n > 1) count += n == 2 ? 1 : n == 3 ? 2 : 3;
            first += n;
        }
        return count;
    }
    private List<String> resolve(String career, int year, String competition, String scope,
            CareerDomesticRanking.Decision decision, List<CareerDomesticRanking.Match> matches, LocalDate due, int total) {
        String input = hash(json(List.of(career, year, competition, scope, root(career), decision, matches, total)));
        var outcomes = outcomes(career, year, competition);
        List<String> ranking = new ArrayList<>(); List<CareerDomesticTiebreak.Pending> pending = new ArrayList<>();
        boolean unresolved = false;
        String format = total == 1 ? "BO5" : total == 2 ? "BO3" : "BO1";
        for (int i = 0; i < decision.groups().size(); i++) {
            var group = decision.groups().get(i);
            if (group.teams().size() == 1) { ranking.addAll(group.teams()); continue; }
            String id = "TB_" + scope + "_" + input.substring(0, 10) + "_" + i;
            var seeds = CareerDomesticRanking.tieSeeds(group, matches, decision.strengthTwice(), input);
            int lastRelevant = scope.equals("R1_R2") ? 6 : scope.equals("R3_R4_RISE") ? 3 : decision.ordered().size();
            int firstPlace = decision.groups().subList(0, i).stream().mapToInt(g -> g.teams().size()).sum() + 1;
            var progress = CareerDomesticTiebreak.advance(id, seeds, outcomes, matches, decision.strengthTwice(), lastRelevant - firstPlace + 1);
            if (progress.ranking().isEmpty()) unresolved = true; else ranking.addAll(progress.ranking());
            pending.addAll(progress.pending());
        }
        for (var match : pending) insertTie(career, year, competition, match.matchId(), match.first(), match.second(), format, due, scope + "_TIEBREAKER", input);
        save(career, year, competition, scope, input,
                new RankingState(scope, decision, unresolved ? List.of() : ranking, pending, format, SCHEDULE_POLICY, "SHARED_NON_QUALIFYING_PLACES_USE_SEEDED_ORDER_WITHOUT_MATCH_RESULT_V1"), !unresolved);
        return unresolved ? List.of() : ranking;
    }

    private void insertTie(String career, int year, String competition, String match, String first, String second,
                           String format, LocalDate date, String stage, String input) {
        List<String> prior = db.query("SELECT fixture_id FROM career_competition_fixture WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ? AND match_id = ?",
                (r, n) -> r.getString(1), career, year, competition, match);
        if (!prior.isEmpty()) return;
        var binding = store.careerBinding(career);
        LocalDate current = db.queryForObject("SELECT current_game_date FROM career_calendar_state WHERE career_id = ?", LocalDate.class, career);
        if (current != null && current.isAfter(date)) date = current;
        int order = db.queryForObject("SELECT COALESCE(MAX(match_order), 0) + 1 FROM career_competition_fixture WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ?", Integer.class, career, year, competition);
        var fixture = new CareerCompetitionAggregate.Fixture(match,
                "competition_fixture_" + hash(career + "|" + year + "|" + competition + "|" + match), date, format, true,
                new CareerCompetitionRules.ParticipantSelector("INITIAL_BOOTSTRAP_TEAM", first), new CareerCompetitionRules.ParticipantSelector("INITIAL_BOOTSTRAP_TEAM", second),
                first, second, "READY", first.equals(binding.managedTeamCode()) || second.equals(binding.managedTeamCode()) ? "PLAYER_CONTROLLED" : "FULL_AUTO",
                CareerCompetitionAggregate.deriveSeed(binding.rootSeed(), year, competition, match),
                "series_" + hash("SERIES|" + career + "|" + year + "|" + competition + "|" + match), List.of(), List.of(), null, null, null);
        store.insertFixture(career, year, competition, fixture, stage, order, null, null, first, null, SIDE_POLICY, "GAME_DERIVED_SCHEDULE_POLICY");
        db.update("""
                UPDATE career_competition_instance SET lifecycle_status = 'RUNNING', blocking_reason = NULL,
                  source_input_hash = COALESCE(source_input_hash, ?), materialization_policy_id = COALESCE(materialization_policy_id, ?),
                  materialization_receipt_hash = COALESCE(materialization_receipt_hash, ?) WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ?
                """, input, CareerDomesticRanking.POLICY, input, career, year, competition);
    }

    private void materialize(String career, int year, String competition, String input, List<CareerCompetitionAggregate.SeededTeam> seeds) {
        var owner = store.careerBinding(career);
        var graph = CareerCompetitionAggregate.materialize(store.rules, career, year, competition, owner.managedTeamCode(), owner.rootSeed(), input, seeds);
        var rules = store.rules.rule(competition).matches();
        for (int i = 0; i < graph.fixtures().size(); i++) {
            var f = graph.fixtures().get(i); var rule = rules.get(i);
            store.insertFixture(career, year, competition, f, rule.stageId(), rule.matchOrder(), rule.groupId(), rule.groupPointValue(), rule.selectionRightOwner(), rule.opponentChoicePolicy(), rule.sideSelectionPolicy(), rule.scheduleStatus());
        }
        store.updateInstance(career, year, competition, "READY", null, input, 0, "0".repeat(64), store.now());
        store.setInstanceMaterializationAuthority(career, year, competition, CareerDomesticRanking.POLICY, input, store.now());
    }

    private void playoffs(String career, int year) {
        Map<String, String> outputs = new LinkedHashMap<>();
        db.query("SELECT output_id, team_code FROM career_competition_output WHERE career_id = ? AND calendar_season_year = ? AND competition_id IN ('LCK_REGULAR_R3_R4','LCK_PLAY_IN','LCK_PLAYOFFS') ORDER BY output_id",
                (RowCallbackHandler) r -> outputs.put(r.getString(1), r.getString(2)), career, year);
        List<String> teams = new ArrayList<>();
        for (int seed = 1; seed <= 6; seed++) { String team = outputs.get("LCK_PLAYOFF_SEED_" + seed); if (team == null) return; teams.add(team); }
        String input = hash(json(List.of(career, year, teams, CareerCompetitionRules.PLAYOFF_OPPONENT_POLICY)));
        if (count("career_competition_fixture", career, year, "LCK_PLAYOFFS") == 0) {
            var seeds = seeded(teams, Map.of()); store.insertSeeds(career, year, "LCK_PLAYOFFS", "LCK_PLAYOFF_SEED", input, seeds);
            materialize(career, year, "LCK_PLAYOFFS", input, seeds);
        }
        Map<String, Integer> seeds = new LinkedHashMap<>(); for (int i = 0; i < teams.size(); i++) seeds.put(teams.get(i), i + 1);
        var results = outcomes(career, year, "LCK_PLAYOFFS");
        for (var match : store.rules.rule("LCK_PLAYOFFS").matches()) {
            if (results.containsKey(match.matchId())) continue;
            String first = select(match.first(), teams, seeds, results, outputs);
            String second;
            if (match.matchId().equals("PO_U1A") || match.matchId().equals("PO_U2A")) {
                List<String> eligible = match.matchId().equals("PO_U1A") ? teams.subList(4, 6)
                        : results.containsKey("PO_U1A") && results.containsKey("PO_U1B") ? List.of(results.get("PO_U1A").winner(), results.get("PO_U1B").winner()) : List.of();
                if (first == null || eligible.isEmpty()) continue;
                var choice = store.rules.choosePlayoffOpponent(first, eligible.stream().map(t -> new CareerCompetitionAggregate.SeededTeam(seeds.get(t), t, 0, 0, 0, 0)).toList());
                second = choice.chosenTeamCode(); storeChoice(career, year, match.matchId(), choice);
                outputs.put("CHOSEN_" + match.matchId(), second);
            } else if (match.matchId().equals("PO_U1B") || match.matchId().equals("PO_U2B")) {
                String a = match.matchId().equals("PO_U1B") ? "PO_U1A" : "PO_U2A";
                List<String> eligible = a.equals("PO_U1A") ? teams.subList(4, 6)
                        : results.containsKey("PO_U1A") && results.containsKey("PO_U1B") ? List.of(results.get("PO_U1A").winner(), results.get("PO_U1B").winner()) : List.of();
                String chosen = outputs.get("CHOSEN_" + a);
                if (chosen == null) chosen = db.query("SELECT chosen_team_code FROM career_competition_opponent_choice WHERE career_id = ? AND calendar_season_year = ? AND competition_id = 'LCK_PLAYOFFS' AND match_id = ?", (r,n) -> r.getString(1), career, year, a).stream().findFirst().orElse(null);
                String selected = chosen; second = eligible.stream().filter(t -> !t.equals(selected)).findFirst().orElse(null);
            } else second = select(match.second(), teams, seeds, results, outputs);
            if (first == null || second == null) continue;
            String owner = match.selectionRightOwner().equals("SECOND") ? second : match.selectionRightOwner().equals("COIN")
                    ? CareerDomesticRanking.draw(input + root(career), match.matchId()).charAt(0) % 2 == 0 ? first : second : first;
            String managed = store.careerBinding(career).managedTeamCode();
            db.update("""
                    UPDATE career_competition_fixture SET first_team_code = ?, second_team_code = ?, lifecycle_status = 'READY',
                      execution_mode = ?, selection_right_owner = ? WHERE career_id = ? AND calendar_season_year = ?
                      AND competition_id = 'LCK_PLAYOFFS' AND match_id = ? AND lifecycle_status <> 'COMPLETED'
                      AND (first_team_code IS NULL OR second_team_code IS NULL OR selection_right_owner <> ?)
                    """, first, second, Set.of(first, second).contains(managed) ? "PLAYER_CONTROLLED" : "FULL_AUTO", owner, career, year, match.matchId(), owner);
        }
        refresh(career, year, "LCK_PLAYOFFS");
        if (results.containsKey("PO_F")) sealFinal(career, year, outputs);
    }

    private String select(CareerCompetitionRules.ParticipantSelector selector, List<String> teams, Map<String, Integer> seeds,
                          Map<String, CareerDomesticTiebreak.Outcome> outcomes, Map<String, String> outputs) {
        String type = selector.type(), value = selector.value();
        if (type.equals("LCK_PLAYOFF_SEED")) return teams.get(Integer.parseInt(value) - 1);
        if (type.equals("MATCH_WINNER") || type.equals("MATCH_LOSER")) {
            var result = outcomes.get(value); return result == null ? null : type.equals("MATCH_WINNER") ? result.winner() : result.loser();
        }
        if (type.equals("HIGHER_PLAYOFF_SEED_MATCH_LOSER") || type.equals("LOWER_PLAYOFF_SEED_MATCH_LOSER")) {
            List<String> losers = java.util.Arrays.stream(value.split(",")).map(outcomes::get).filter(java.util.Objects::nonNull).map(CareerDomesticTiebreak.Outcome::loser).sorted(Comparator.comparingInt(seeds::get)).toList();
            return losers.size() == 2 ? losers.get(type.startsWith("HIGHER") ? 0 : 1) : null;
        }
        return null;
    }

    private void storeChoice(String career, int year, String match, CareerCompetitionRules.OpponentChoiceReceipt choice) {
        var prior = db.query("SELECT choice_hash FROM career_competition_opponent_choice WHERE career_id = ? AND calendar_season_year = ? AND competition_id = 'LCK_PLAYOFFS' AND match_id = ?", (r,n) -> r.getString(1), career, year, match);
        if (!prior.isEmpty()) { if (!prior.getFirst().equals(choice.receiptHash())) throw new IllegalStateException("PLAYOFF_CHOICE_CONFLICT"); return; }
        db.update("""
                INSERT INTO career_competition_opponent_choice(choice_hash, career_id, calendar_season_year, competition_id,
                  match_id, choice_owner_team_code, eligible_seed_order, chosen_team_code, policy_id, policy_hash, created_at)
                VALUES (?, ?, ?, 'LCK_PLAYOFFS', ?, ?, ?, ?, ?, ?, ?)
                """, choice.receiptHash(), career, year, match, choice.choiceOwnerTeamCode(), String.join(",", choice.canonicalEligibleOrder()), choice.chosenTeamCode(), choice.policyId(), choice.policyHash(), store.now());
    }

    private void sealFinal(String career, int year, Map<String, String> outputs) {
        if (count("career_lck_final_ranking_snapshot", career, year, null) > 0) return;
        List<String> order = new ArrayList<>();
        for (int place = 1; place <= 10; place++) { String team = outputs.get("LCK_SEASON_PLACE_" + place); if (team == null) return; order.add(team); }
        if (Set.copyOf(order).size() != 10) throw new IllegalStateException("LCK_FINAL_RANKING_NOT_TEN_DISTINCT_TEAMS");
        var matches = new ArrayList<>(CareerDomesticEvidence.league(db, store.json, career, year));
        matches.addAll(CareerDomesticEvidence.competition(db, store.json, career, year, "LCK_REGULAR_R3_R4", "LEGEND_RISE"));
        if (matches.size() != 130 || !Set.copyOf(teams(matches)).equals(Set.copyOf(order))) throw new IllegalStateException("LCK_FINAL_REGULAR_RECORD_EVIDENCE_REQUIRED");
        var ranking = seeded(order, CareerDomesticRanking.records(order, matches));
        var cycle = store.findCycle(career, year, false).getFirst();
        String season = db.queryForObject("SELECT season_id FROM career_save WHERE career_id = ?", String.class, career);
        String state = CareerCompetitionRelationalStore.finalRankingStateHash(career, year, cycle.seasonOrdinal(), season, ranking);
        List<String> receipts = db.query("""
                SELECT competition_id || ':' || match_id || ':' || receipt_hash FROM career_competition_application
                WHERE career_id = ? AND calendar_season_year = ? AND competition_id IN ('LCK_REGULAR_R1_R2','LCK_REGULAR_R3_R4','LCK_PLAY_IN','LCK_PLAYOFFS')
                ORDER BY competition_id, match_id
                """, (r,n) -> r.getString(1), career, year);
        String evidence = hash(json(List.of(matches, receipts, outputs)));
        String authority = finalAuthority(state, evidence, order.get(0), order.get(1));
        db.update("""
                INSERT INTO career_lck_final_ranking_snapshot(career_id, calendar_season_year, season_ordinal, source_season_id,
                  lifecycle_status, state_hash, created_at, rule_version, policy_version, result_evidence_hash,
                  champion_team_code, runner_up_team_code, record_scope, authority_hash)
                VALUES (?, ?, ?, ?, 'SEALED', ?, ?, ?, ?, ?, ?, ?, 'REGULAR_R1_R2_AND_R3_R4_ONLY', ?)
                """, career, year, cycle.seasonOrdinal(), season, state, store.now(), CareerCompetitionRules.VERSION, CareerDomesticRanking.POLICY,
                evidence, order.get(0), order.get(1), authority);
        for (var row : ranking) db.update("""
                INSERT INTO career_lck_final_ranking_row(career_id, calendar_season_year, rank_number, team_code,
                  series_wins, series_losses, game_wins, game_losses) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, career, year, row.seed(), row.teamCode(), row.seriesWins(), row.seriesLosses(), row.gameWins(), row.gameLosses());
        save(career, year, "LCK_PLAYOFFS", "LCK_FINAL_RANKING", evidence,
                new FinalState("SEALED", order.get(0), order.get(1), season, ranking, state, evidence, authority,
                        "PENDING_IN_GAME_INTERNATIONAL_EVIDENCE", List.of("REGIONAL_SLOT_ALLOCATION", "MSI_CHAMPION_AND_DOMESTIC_PLAYOFF_ELIGIBILITY")), true);
        refresh(career, year, "LCK_PLAYOFFS");
    }
    static String finalAuthority(String state, String evidence, String champion, String runnerUp) {
        return hash(String.join("\n", CareerCompetitionRules.VERSION, CareerDomesticRanking.POLICY, "REGULAR_R1_R2_AND_R3_R4_ONLY", state, evidence, champion, runnerUp));
    }

    private String json(Object value) {
        try { return store.json.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("DOMESTIC_JSON_FAILED", e); }
    }
    private void save(String career, int year, String competition, String id, String input, Object state, boolean sealed) {
        String json = json(state), digest = hash(json);
        var prior = db.query("SELECT input_hash, decision_hash, lifecycle_status FROM career_domestic_ranking_decision WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ? AND decision_id = ?", (r,n) -> List.of(r.getString(1), r.getString(2), r.getString(3)), career, year, competition, id);
        if (!prior.isEmpty()) {
            if (!prior.getFirst().get(0).equals(input)) throw new IllegalStateException("DOMESTIC_RANKING_INPUT_CONFLICT");
            if (prior.getFirst().get(1).equals(digest)) return;
            if (prior.getFirst().get(2).equals("SEALED")) throw new IllegalStateException("SEALED_DOMESTIC_DECISION_CONFLICT");
            db.update("UPDATE career_domestic_ranking_decision SET decision_json = ?, decision_hash = ?, lifecycle_status = ? WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ? AND decision_id = ?", json, digest, sealed ? "SEALED" : "RUNNING", career, year, competition, id);
        } else db.update("""
                INSERT INTO career_domestic_ranking_decision(career_id, calendar_season_year, competition_id, decision_id,
                  input_hash, policy_version, decision_json, decision_hash, lifecycle_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, career, year, competition, id, input, CareerDomesticRanking.POLICY, json, digest, sealed ? "SEALED" : "RUNNING");
    }
    private Map<String, String> membership(String career, int year, String competition, String pattern) {
        Map<String, String> result = new LinkedHashMap<>();
        db.query("SELECT seed_scope, team_code FROM career_competition_seed WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ? AND seed_scope LIKE ? ORDER BY seed_scope, seed_number",
                (RowCallbackHandler) r -> result.put(r.getString(2), r.getString(1).substring("CUP_GROUP_".length())), career, year, competition, pattern);
        return result;
    }
    private Map<String, CareerDomesticTiebreak.Outcome> outcomes(String career, int year, String competition) {
        Map<String, CareerDomesticTiebreak.Outcome> result = new LinkedHashMap<>();
        db.query("SELECT match_id, winner_team_code, loser_team_code FROM career_competition_fixture WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ? AND lifecycle_status = 'COMPLETED' ORDER BY match_order",
                (RowCallbackHandler) r -> result.put(r.getString(1), new CareerDomesticTiebreak.Outcome(r.getString(2), r.getString(3))), career, year, competition);
        return result;
    }
    private int count(String table, String career, int year, String competition) {
        return competition == null ? db.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE career_id = ? AND calendar_season_year = ?", Integer.class, career, year)
                : db.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE career_id = ? AND calendar_season_year = ? AND competition_id = ?", Integer.class, career, year, competition);
    }
    private long root(String career) { return store.careerBinding(career).rootSeed(); }
    private void refresh(String career, int year, String competition) { store.refreshInstanceHash(career, year, competition); store.refreshCycleHash(career, year); }
    private static List<String> teams(List<CareerDomesticRanking.Match> matches) {
        return matches.stream().flatMap(m -> java.util.stream.Stream.of(m.first(), m.second())).distinct().sorted().toList();
    }
    private static List<CareerCompetitionAggregate.SeededTeam> seeded(List<String> teams, Map<String, CareerDomesticRanking.Record> records) {
        List<CareerCompetitionAggregate.SeededTeam> result = new ArrayList<>();
        for (int i = 0; i < teams.size(); i++) {
            String team = teams.get(i); var row = records.getOrDefault(team, new CareerDomesticRanking.Record(team, 0, 0, 0, 0));
            result.add(new CareerCompetitionAggregate.SeededTeam(i + 1, team, row.wins(), row.losses(), row.gameWins(), row.gameLosses()));
        }
        return result;
    }
    static String hash(String value) { return CareerCompetitionRules.sha256(value.getBytes(StandardCharsets.UTF_8)); }
    record RankingState(String scope, CareerDomesticRanking.Decision evidence, List<String> ranking,
                        List<CareerDomesticTiebreak.Pending> pendingMatches, String seriesFormat, String schedulePolicy, String nonQualifyingPlacementPolicy) {}
    record MixedGroupState(Map<String, List<String>> groupRankings, Map<String, Integer> score,
                           List<String> completedMatches, String nextMatchId, String policy) {}
    record FinalState(String status, String championTeamCode, String runnerUpTeamCode, String sourceSeasonId,
                      List<CareerCompetitionAggregate.SeededTeam> ranking, String stateHash, String resultEvidenceHash,
                      String authorityHash, String worldsStatus, List<String> requiredInternationalEvidence) {}
}
