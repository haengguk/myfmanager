package com.lolfm.career;

import com.lolfm.application.MatchEngineV1Policy;
import com.lolfm.league.CareerCompetitionAutomatedSeriesKernel;
import com.lolfm.league.LeagueFixtureGameReceiptV1;
import com.lolfm.league.LeagueIdentity;
import com.lolfm.league.LeaguePlayerSeriesKernelPort;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Compact canonical completion derived only from replay-verified Series evidence. */
public record CareerCompetitionFixtureCompletionReceiptV1(
        String schemaVersion,
        String bindingHash,
        String careerId,
        int seasonYear,
        String competitionId,
        String fixtureId,
        String matchId,
        String seriesId,
        String firstTeamCode,
        String secondTeamCode,
        int firstScore,
        int secondScore,
        String winnerTeamCode,
        String loserTeamCode,
        int totalDurationSeconds,
        List<LeagueFixtureGameReceiptV1> orderedGames,
        String receiptHash
) {
    public static final String SCHEMA =
            "CAREER_COMPETITION_FIXTURE_COMPLETION_RECEIPT_V1";
    public static final String HASH_ALGORITHM =
            "SHA256_UTF8_EXPLICIT_ORDERED_COMPETITION_RECEIPT_LINES_V1";

    public CareerCompetitionFixtureCompletionReceiptV1 {
        if (!SCHEMA.equals(schemaVersion)) throw new IllegalArgumentException(
                "Unsupported Competition receipt schema");
        CareerIdentity.requireSha256(bindingHash, "bindingHash");
        CareerIdentity.requireCareerId(careerId);
        if (seasonYear < 2026 || firstScore < 0 || secondScore < 0
                || totalDurationSeconds <= 0) {
            throw new IllegalArgumentException("Competition receipt numbers");
        }
        orderedGames = List.copyOf(orderedGames);
        if (!Set.of(firstTeamCode, secondTeamCode).equals(
                Set.of(winnerTeamCode, loserTeamCode))) {
            throw new IllegalArgumentException("Competition receipt teams");
        }
        String actual = hash(canonicalWithoutHash(bindingHash, careerId, seasonYear,
                competitionId, fixtureId, matchId, seriesId, firstTeamCode,
                secondTeamCode, firstScore, secondScore, winnerTeamCode,
                loserTeamCode, totalDurationSeconds, orderedGames));
        if (receiptHash != null && !actual.equals(receiptHash)) {
            throw new IllegalArgumentException("Competition receipt hash mismatch");
        }
        receiptHash = actual;
    }

    static VerifiedCompetitionFixtureCompletion verifyAutomated(
            CareerCompetitionSeriesBindingV1 binding,
            CareerCompetitionAutomatedSeriesKernel.CompletedSeriesEvidence evidence
    ) {
        if (!binding.bindingHash().equals(evidence.bindingHash())) {
            throw new IllegalArgumentException("COMPETITION_BINDING_EVIDENCE_MISMATCH");
        }
        return verify(binding, evidence.score(), evidence.winnerTeamCode(),
                evidence.orderedGames());
    }

    static VerifiedCompetitionFixtureCompletion verifyPlayer(
            CareerCompetitionSeriesBindingV1 binding,
            LeaguePlayerSeriesKernelPort.CompletedSeriesEvidence evidence
    ) {
        if (!binding.bindingHash().equals(evidence.bindingHash())
                || !binding.boundSeriesId().equals(evidence.seriesId())
                || binding.seriesFormat() != evidence.format()
                || !binding.firstTeamCode().equals(evidence.firstTeamCode())
                || !binding.secondTeamCode().equals(evidence.secondTeamCode())
                || binding.fixtureRootSeed() != evidence.rootSeed()) {
            throw new IllegalArgumentException("COMPETITION_PLAYER_EVIDENCE_MISMATCH");
        }
        List<LeagueFixtureGameReceiptV1> games = evidence.orderedGames().stream()
                .map(value -> LeagueFixtureGameReceiptV1.from(value.verifiedInput(),
                        value.verifiedOutput(), union(value.historyBefore(),
                                value.completedDraft().bluePicks(),
                                value.completedDraft().redPicks())))
                .toList();
        return verify(binding, evidence.score(), evidence.winnerTeamCode(), games);
    }

    private static VerifiedCompetitionFixtureCompletion verify(
            CareerCompetitionSeriesBindingV1 binding,
            Map<String, Integer> claimedScore,
            String claimedWinner,
            List<LeagueFixtureGameReceiptV1> games
    ) {
        Objects.requireNonNull(binding, "binding");
        if (games.isEmpty() || games.size() > binding.seriesFormat().maximumGames()) {
            throw new IllegalArgumentException("COMPETITION_GAME_CARDINALITY_MISMATCH");
        }
        MatchEngineV1Policy.Snapshot policy = MatchEngineV1Policy.authoritative();
        LinkedHashMap<String, Integer> score = new LinkedHashMap<>();
        score.put(binding.firstTeamCode(), 0);
        score.put(binding.secondTeamCode(), 0);
        String history = binding.initialHistoryHash();
        for (int index = 0; index < games.size(); index++) {
            if (score.values().stream().anyMatch(value ->
                    value >= binding.seriesFormat().winsRequired())) {
                throw new IllegalArgumentException(
                        "COMPETITION_GAME_AFTER_SERIES_CLINCH");
            }
            LeagueFixtureGameReceiptV1 game = games.get(index);
            int gameNumber = index + 1;
            String blue = binding.loserChoosesNextSide() && index > 0
                    ? games.get(index - 1).winnerTeamCode().equals(binding.firstTeamCode()) ? binding.secondTeamCode() : binding.firstTeamCode()
                    : gameNumber % 2 == 1 ? binding.game1BlueTeamCode() : binding.game1RedTeamCode();
            String red = blue.equals(binding.firstTeamCode())
                    ? binding.secondTeamCode() : binding.firstTeamCode();
            long seed = LeagueIdentity.gameSeed(binding.boundSeriesId(),
                    binding.fixtureRootSeed(), gameNumber, blue, red,
                    binding.seedAnchorTeamCode(), history);
            boolean valid = game.gameNumber() == gameNumber
                    && validMatchIdentity(binding, game, gameNumber)
                    && game.blueTeamCode().equals(blue)
                    && game.redTeamCode().equals(red)
                    && game.gameSeed() == seed
                    && game.historyBeforeHash().equals(history)
                    && game.winnerTeamCode() != null
                    && Set.of(binding.firstTeamCode(), binding.secondTeamCode())
                    .contains(game.winnerTeamCode())
                    && game.policyId().equals(policy.policyId())
                    && game.policyHash().equals(policy.policyHash())
                    && game.configurationHash().equals(policy.configurationHash())
                    && game.runtimeProfileId().equals(
                    policy.retainedRuntimeProfileId().name())
                    && game.engineImplementationVersion().equals(
                    policy.engineImplementationVersion())
                    && game.activeGameplayRulesVersion().equals(
                    policy.activeGameplayRulesVersion())
                    && game.resourceProvenanceHash().equals(
                    binding.resourceProvenanceHash());
            if (!valid) throw new IllegalArgumentException(
                    "COMPETITION_GAME_RECEIPT_BINDING_MISMATCH");
            score.compute(game.winnerTeamCode(),
                    (ignored, value) -> Objects.requireNonNull(value) + 1);
            history = game.historyAfterHash();
        }
        String winner = score.get(binding.firstTeamCode())
                > score.get(binding.secondTeamCode())
                ? binding.firstTeamCode() : binding.secondTeamCode();
        if (score.get(winner) != binding.seriesFormat().winsRequired()
                || !score.equals(claimedScore) || !winner.equals(claimedWinner)) {
            throw new IllegalArgumentException("COMPETITION_DECISIVE_SCORE_MISMATCH");
        }
        String loser = winner.equals(binding.firstTeamCode())
                ? binding.secondTeamCode() : binding.firstTeamCode();
        CareerCompetitionFixtureCompletionReceiptV1 receipt =
                new CareerCompetitionFixtureCompletionReceiptV1(SCHEMA,
                        binding.bindingHash(), binding.careerId(), binding.seasonYear(),
                        binding.competitionId(), binding.fixtureId(), binding.matchId(),
                        binding.boundSeriesId(), binding.firstTeamCode(),
                        binding.secondTeamCode(), score.get(binding.firstTeamCode()),
                        score.get(binding.secondTeamCode()), winner, loser,
                        games.stream().mapToInt(
                                LeagueFixtureGameReceiptV1::durationSeconds).sum(),
                        games, null);
        return new VerifiedCompetitionFixtureCompletion(receipt);
    }

    private static boolean validMatchIdentity(
            CareerCompetitionSeriesBindingV1 binding,
            LeagueFixtureGameReceiptV1 game,
            int gameNumber
    ) {
        if ("FULL_AUTO".equals(binding.executionMode())) {
            return game.matchIdentity().equals("CAREER_COMPETITION:"
                    + binding.careerId() + ':' + binding.seasonYear() + ':'
                    + binding.competitionId() + ':' + binding.matchId() + ":SERIES:"
                    + binding.boundSeriesId() + ":GAME:" + gameNumber);
        }
        String prefix = "SERIES_PLAYER_DRAFT:" + binding.boundSeriesId() + ":GAME:"
                + gameNumber + ':';
        return game.matchIdentity().startsWith(prefix)
                && game.matchIdentity().contains(":DRAFT:");
    }

    private static List<com.lolfm.champion.ChampionId> union(
            List<com.lolfm.champion.ChampionId> before,
            List<com.lolfm.champion.ChampionId> blue,
            List<com.lolfm.champion.ChampionId> red
    ) {
        java.util.HashSet<com.lolfm.champion.ChampionId> values =
                new java.util.HashSet<>(before);
        values.addAll(blue);
        values.addAll(red);
        return values.stream().sorted(java.util.Comparator.comparing(
                com.lolfm.champion.ChampionId::value)).toList();
    }

    public String canonicalText() {
        return canonicalWithoutHash(bindingHash, careerId, seasonYear, competitionId,
                fixtureId, matchId, seriesId, firstTeamCode, secondTeamCode,
                firstScore, secondScore, winnerTeamCode, loserTeamCode,
                totalDurationSeconds, orderedGames) + "receiptHash=" + receiptHash + '\n';
    }

    private static String canonicalWithoutHash(
            String bindingHash, String careerId, int year, String competitionId,
            String fixtureId, String matchId, String seriesId, String firstTeam,
            String secondTeam, int firstScore, int secondScore, String winner,
            String loser, int duration, List<LeagueFixtureGameReceiptV1> games
    ) {
        StringBuilder value = new StringBuilder("schemaVersion=").append(SCHEMA)
                .append("\nhashAlgorithm=").append(HASH_ALGORITHM)
                .append("\nbindingHash=").append(bindingHash)
                .append("\ncareerId=").append(careerId)
                .append("\ncalendarSeasonYear=").append(year)
                .append("\ncompetitionId=").append(competitionId)
                .append("\nfixtureId=").append(fixtureId)
                .append("\nmatchId=").append(matchId)
                .append("\nseriesId=").append(seriesId)
                .append("\nfirstTeamCode=").append(firstTeam)
                .append("\nsecondTeamCode=").append(secondTeam)
                .append("\nfirstScore=").append(firstScore)
                .append("\nsecondScore=").append(secondScore)
                .append("\nwinnerTeamCode=").append(winner)
                .append("\nloserTeamCode=").append(loser)
                .append("\ntotalDurationSeconds=").append(duration).append('\n');
        for (LeagueFixtureGameReceiptV1 game : games) {
            value.append("gameReceiptHash=").append(hash(game.canonicalText()))
                    .append('\n');
        }
        return value.toString();
    }

    private static String hash(String value) {
        return CareerCompetitionRules.sha256(value.getBytes(StandardCharsets.UTF_8));
    }
}
