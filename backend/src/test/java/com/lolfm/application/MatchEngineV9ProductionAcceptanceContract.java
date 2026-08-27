package com.lolfm.application;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.champion.ChampionId;
import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.composition.ChampionCompositionProfileCatalog;
import com.lolfm.composition.CompositionCapability;
import com.lolfm.composition.CompositionInteractionEvaluator;
import com.lolfm.composition.CompositionInteractionFormula;
import com.lolfm.composition.CompositionInteractionInput;
import com.lolfm.composition.CompositionPattern;
import com.lolfm.composition.TeamCompositionAnalysis;
import com.lolfm.composition.TeamCompositionAnalyzer;
import com.lolfm.composition.TeamCompositionContext;
import com.lolfm.composition.TeamCompositionLineup;
import com.lolfm.domain.Player;
import com.lolfm.domain.Position;
import com.lolfm.domain.Team;
import com.lolfm.player.ChampionProficiencyCatalog;
import com.lolfm.player.ChampionProficiencyEntry;
import com.lolfm.player.LckTeamAssembler;
import com.lolfm.simulator.SimulationRuntimeProfileId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Frozen, test-side product-sanity contract. It is not a calibration or holdout schedule. */
public final class MatchEngineV9ProductionAcceptanceContract {
    public static final String DIAGNOSTIC_IDENTITY =
            "T1_GEN_FIXED_DRAFT_COMPOSITION_ARCHETYPE_DIAGNOSTIC_V1";
    public static final String PURPOSE_NAMESPACE =
            "PRODUCT_SANITY_ONLY_NOT_CALIBRATION_OR_HOLDOUT";
    public static final String BAN_CONTROL =
            "NOT_APPLICABLE_AFTER_FINAL_ASSIGNMENT";
    public static final String CONTRACT_SCHEMA =
            "MATCH_ENGINE_V9_PRODUCTION_ACCEPTANCE_CONTRACT_V1";
    public static final int SEED_COUNT = 50;
    public static final int CORE_SIMULATION_COUNT = 1_200;
    public static final int PAIRED_CELL_COUNT = 400;
    public static final long SEED_BASE = 9_270_001L;
    public static final long SEED_STEP = 104_729L;

    public static final Lineup POKE = lineup("POKE", "rumble", "nidalee", "azir",
            "varus", "karma");
    public static final Lineup ENGAGE = lineup("ENGAGE_DIVE", "camille", "wukong",
            "galio", "kaisa", "rakan");
    public static final Lineup COUNTER = lineup("COUNTER_RESPONSE", "sion", "poppy",
            "taliyah", "xayah", "braum");

    public static final List<Scenario> SCENARIOS = List.of(
            new Scenario("POKE_VS_COUNTER", POKE, COUNTER),
            new Scenario("ENGAGE_VS_COUNTER", ENGAGE, COUNTER));
    public static final List<Orientation> ORIENTATIONS = List.of(
            new Orientation("T1_ARCHETYPE_BLUE__GEN_COUNTER_RED", "T1", Role.ARCHETYPE,
                    "GEN", Role.COUNTER),
            new Orientation("GEN_COUNTER_BLUE__T1_ARCHETYPE_RED", "GEN", Role.COUNTER,
                    "T1", Role.ARCHETYPE),
            new Orientation("GEN_ARCHETYPE_BLUE__T1_COUNTER_RED", "GEN", Role.ARCHETYPE,
                    "T1", Role.COUNTER),
            new Orientation("T1_COUNTER_BLUE__GEN_ARCHETYPE_RED", "T1", Role.COUNTER,
                    "GEN", Role.ARCHETYPE));
    public static final List<SimulationRuntimeProfileId> PROFILES = List.of(
            SimulationRuntimeProfileId.BASELINE_V1,
            SimulationRuntimeProfileId.MATCHUP_ONLY_CANDIDATE_V1,
            SimulationRuntimeProfileId.PRODUCTION_MATCHUP_COMPOSITION_V1);

    private MatchEngineV9ProductionAcceptanceContract() {
    }

    public static long seed(int index) {
        if (index < 0 || index >= SEED_COUNT) throw new IllegalArgumentException("seed index");
        return SEED_BASE + SEED_STEP * index;
    }

    public static List<Cell> schedule() {
        ArrayList<Cell> result = new ArrayList<>(CORE_SIMULATION_COUNT);
        for (Scenario scenario : SCENARIOS) {
            for (Orientation orientation : ORIENTATIONS) {
                for (SimulationRuntimeProfileId profile : PROFILES) {
                    for (int seedIndex = 0; seedIndex < SEED_COUNT; seedIndex++) {
                        result.add(new Cell(scenario.id(), orientation.id(), profile,
                                seedIndex, seed(seedIndex)));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    public static String scheduleHash() {
        StringBuilder value = new StringBuilder();
        schedule().forEach(cell -> value.append(cell.scenarioId()).append('|')
                .append(cell.orientationId()).append('|').append(cell.profileId()).append('|')
                .append(cell.seedIndex()).append('|').append(cell.seed()).append('\n'));
        return sha256(value.toString());
    }

    public static Preflight preflight(
            ChampionCatalog champions,
            LckTeamAssembler teams,
            ChampionProficiencyCatalog proficiencies,
            ChampionCompositionProfileCatalog compositions
    ) {
        Objects.requireNonNull(champions, "champions");
        Objects.requireNonNull(teams, "teams");
        Objects.requireNonNull(proficiencies, "proficiencies");
        Objects.requireNonNull(compositions, "compositions");
        List<Lineup> lineups = List.of(POKE, ENGAGE, COUNTER);
        int illegalRoles = (int) lineups.stream().flatMap(value -> value.roles().values().stream())
                .filter(role -> !champions.supports(role)).count();

        TeamCompositionAnalyzer analyzer = new TeamCompositionAnalyzer();
        LinkedHashMap<String, TeamCompositionAnalysis> analyses = new LinkedHashMap<>();
        lineups.forEach(lineup -> analyses.put(lineup.id(),
                analyzer.analyze(lineup.asCompositionLineup(), compositions.profiles())));
        TeamCompositionAnalysis poke = analyses.get(POKE.id());
        TeamCompositionAnalysis engage = analyses.get(ENGAGE.id());
        TeamCompositionAnalysis counter = analyses.get(COUNTER.id());
        ArchetypeScores scores = new ArchetypeScores(
                poke.patterns().get(CompositionPattern.POKE_SIEGE).readiness(),
                engage.patterns().get(CompositionPattern.ENGAGE_CHAIN).readiness(),
                counter.coverage().capability(CompositionCapability.DISENGAGE).coverage(),
                counter.coverage().capability(CompositionCapability.PEEL).coverage(),
                counter.coverage().capability(CompositionCapability.FRONTLINE).coverage(),
                counter.coverage().capability(CompositionCapability.WAVE_CLEAR).coverage());

        CompositionInteractionEvaluator interaction = new CompositionInteractionEvaluator();
        LinkedHashMap<String, Map<String, Double>> interactionEdges = new LinkedHashMap<>();
        for (Lineup archetype : List.of(POKE, ENGAGE)) {
            var evaluated = interaction.evaluate(
                    CompositionInteractionInput.fromAnalysis(analyses.get(archetype.id())),
                    CompositionInteractionInput.fromAnalysis(counter),
                    CompositionInteractionFormula.PRODUCT_EXPOSURE);
            LinkedHashMap<String, Double> contexts = new LinkedHashMap<>();
            for (TeamCompositionContext context : TeamCompositionContext.values()) {
                contexts.put(context.name(),
                        evaluated.contexts().get(context).teamASignedEdge());
            }
            interactionEdges.put(archetype.id() + "__VS__" + COUNTER.id(),
                    Collections.unmodifiableMap(contexts));
        }

        ArrayList<ProficiencyBinding> bindings = new ArrayList<>();
        int neutralFallback = 0;
        int identityMismatch = 0;
        for (String teamCode : List.of("T1", "GEN")) {
            Team team = teams.assemble(teamCode);
            for (Lineup lineup : lineups) {
                for (Position position : Position.values()) {
                    Player player = player(team, position);
                    ChampionRoleKey role = lineup.roles().get(position);
                    ChampionProficiencyEntry authored = proficiencies.authoredEntries().stream()
                            .filter(value -> value.playerId().equals(player.requirePlayerId())
                                    && value.championRoleKey().equals(role))
                            .findFirst().orElse(null);
                    if (authored == null) neutralFallback++;
                    else if (!authored.sourceRatingKey().teamCode().equals(teamCode)
                            || authored.sourceRatingKey().position() != position) identityMismatch++;
                    bindings.add(new ProficiencyBinding(teamCode, position,
                            player.requirePlayerId().value(), lineup.id(),
                            role.championId().value(), authored == null ? null : authored.value(),
                            authored != null));
                }
            }
        }
        bindings.sort(Comparator.comparing(ProficiencyBinding::teamCode)
                .thenComparing(value -> value.position().ordinal())
                .thenComparing(ProficiencyBinding::lineupId));

        int duplicatePickErrors = 0;
        for (Scenario scenario : SCENARIOS) {
            for (Orientation orientation : ORIENTATIONS) {
                Lineup blue = orientation.blueRole() == Role.ARCHETYPE
                        ? scenario.archetype() : scenario.counter();
                Lineup red = orientation.redRole() == Role.ARCHETYPE
                        ? scenario.archetype() : scenario.counter();
                if (java.util.stream.Stream.concat(blue.roles().values().stream(),
                                red.roles().values().stream())
                        .map(ChampionRoleKey::championId).distinct().count() != 10) {
                    duplicatePickErrors++;
                }
            }
        }
        boolean thresholds = scores.pokeSiegeReadiness() >= 0.85
                && scores.engageChainReadiness() >= 0.90
                && scores.counterDisengageCoverage() >= 0.95
                && scores.counterPeelCoverage() >= 0.95
                && scores.counterFrontlineCoverage() >= 0.90
                && scores.counterWaveClearCoverage() >= 0.90;
        boolean clean = illegalRoles == 0 && duplicatePickErrors == 0
                && bindings.size() == 30 && neutralFallback == 0 && identityMismatch == 0
                && bindings.stream().allMatch(ProficiencyBinding::authored) && thresholds
                && schedule().size() == CORE_SIMULATION_COUNT;
        if (!clean) {
            throw new IllegalStateException("FIXED_DRAFT_ACCEPTANCE_PREFLIGHT_DRIFT");
        }
        return new Preflight(CONTRACT_SCHEMA, DIAGNOSTIC_IDENTITY, PURPOSE_NAMESPACE,
                BAN_CONTROL, scores, Collections.unmodifiableMap(interactionEdges),
                illegalRoles, duplicatePickErrors, bindings.size(), neutralFallback,
                identityMismatch, List.copyOf(bindings), compositions.version(),
                compositions.profileHash(), scheduleHash(), clean);
    }

    public static Scenario scenario(String id) {
        return SCENARIOS.stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
    }

    public static Orientation orientation(String id) {
        return ORIENTATIONS.stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
    }

    public static Lineup lineupFor(Scenario scenario, Role role) {
        return role == Role.ARCHETYPE ? scenario.archetype() : scenario.counter();
    }

    private static Player player(Team team, Position position) {
        return team.getPlayers().stream().filter(value -> value.getPosition() == position)
                .findFirst().orElseThrow();
    }

    private static Lineup lineup(String id, String top, String jungle, String mid,
                                 String adc, String support) {
        EnumMap<Position, ChampionRoleKey> roles = new EnumMap<>(Position.class);
        roles.put(Position.TOP, role(top, Position.TOP));
        roles.put(Position.JUNGLE, role(jungle, Position.JUNGLE));
        roles.put(Position.MID, role(mid, Position.MID));
        roles.put(Position.ADC, role(adc, Position.ADC));
        roles.put(Position.SUPPORT, role(support, Position.SUPPORT));
        return new Lineup(id, roles);
    }

    private static ChampionRoleKey role(String champion, Position position) {
        return new ChampionRoleKey(new ChampionId(champion), position);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public enum Role { ARCHETYPE, COUNTER }

    public record Lineup(String id, Map<Position, ChampionRoleKey> roles) {
        public Lineup {
            id = Objects.requireNonNull(id, "id");
            EnumMap<Position, ChampionRoleKey> ordered = new EnumMap<>(Position.class);
            ordered.putAll(roles);
            if (ordered.size() != Position.values().length) throw new IllegalArgumentException();
            roles = Collections.unmodifiableMap(ordered);
        }

        public TeamCompositionLineup asCompositionLineup() {
            return new TeamCompositionLineup(roles);
        }
    }

    public record Scenario(String id, Lineup archetype, Lineup counter) { }

    public record Orientation(String id, String blueTeamCode, Role blueRole,
                              String redTeamCode, Role redRole) { }

    public record Cell(String scenarioId, String orientationId,
                       SimulationRuntimeProfileId profileId, int seedIndex, long seed) { }

    public record ArchetypeScores(double pokeSiegeReadiness, double engageChainReadiness,
                                  double counterDisengageCoverage, double counterPeelCoverage,
                                  double counterFrontlineCoverage,
                                  double counterWaveClearCoverage) { }

    public record ProficiencyBinding(String teamCode, Position position, String playerId,
                                     String lineupId, String championId, Integer proficiency,
                                     boolean authored) { }

    public record Preflight(String schemaVersion, String diagnosticIdentity,
                            String purposeNamespace, String banControl,
                            ArchetypeScores archetypeScores,
                            Map<String, Map<String, Double>> interactionEdges,
                            int illegalRoleCount, int duplicatePickErrorCount,
                            int authoredProficiencyBindingCount, int neutralFallbackCount,
                            int identityMismatchCount, List<ProficiencyBinding> bindings,
                            String compositionProfileVersion, String compositionProfileHash,
                            String scheduleHash, boolean clean) {
        public Preflight {
            interactionEdges = Collections.unmodifiableMap(new LinkedHashMap<>(interactionEdges));
            bindings = List.copyOf(bindings);
        }
    }
}
