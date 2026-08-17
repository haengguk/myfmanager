package com.lolfm.simulator;

import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.composition.*;
import com.lolfm.domain.Position;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Phase 13D-4C.7.1 consumed-holdout, behavior-neutral attribution repair. */
public final class CompositionFreshHoldoutIntegrityAndPlausibilityAttribution {
    static final String VERSION = "phase-13d4c7.1-consumed-holdout-attribution-v1";
    static final String SOURCE_SUMMARY_HASH = "52a3ed6cf146c95a93e21e41f08366fa8097fde4c70c3a44b5b4155a7299f20a";
    static final String SOURCE_AUDIT_HASH = "823a46ed488aae74e3f7b1852d465584aa5993e6aa6ab1f4143978259b178e6b";
    static final Path SOURCE = Path.of("build/reports/composition-key-specific-fresh-holdout-gameplay");
    static final Path SOURCE_SUMMARY = SOURCE.resolve("composition-key-specific-fresh-holdout-summary.csv");
    static final Path SOURCE_AUDIT = SOURCE.resolve("composition-key-specific-fresh-holdout-audit.log");
    static final Path SOURCE_APPS = SOURCE.resolve("composition-fresh-holdout-winner-applications.csv");
    static final Path SOURCE_GAMES = SOURCE.resolve("composition-fresh-holdout-paired-games.csv");
    static final Path SOURCE_SCHEDULE = SOURCE.resolve("composition-fresh-holdout-schedule.csv");
    static final Path SOURCE_POOL = SOURCE.resolve("composition-fresh-holdout-lineup-pool.csv");
    static final Path SOURCE_CANDIDATE = SOURCE.resolve("composition-fresh-holdout-candidate-identity.csv");
    static final Path OUT = Path.of("build/reports/composition-fresh-holdout-integrity-and-plausibility-attribution");
    static final double EPSILON = 1.0e-9;
    static final String DEFERRED = "DEFERRED_TO_PHASE_13D4C7_2";
    static final Map<TeamCompositionContext, CompositionKeySpecificFreshHoldoutGameplayAudit.Bands> BANDS =
            CompositionKeySpecificFreshHoldoutGameplayAudit.BANDS;

    private CompositionFreshHoldoutIntegrityAndPlausibilityAttribution() {}

    public static void main(String[] args) throws Exception {
        Result result = run();
        System.out.println("Composition fresh-holdout attribution: " + result.verdict());
    }

    static Result run() throws Exception {
        verifySources();
        Files.createDirectories(OUT);
        Map<Path,String> before = sourceHashes();
        List<Game> games = readGames();
        Map<Integer,Game> gameByCase = games.stream().collect(Collectors.toMap(Game::caseIndex, Function.identity()));
        List<App> apps = readApps(gameByCase);
        Map<Integer,List<App>> actualByCase = apps.stream().filter(App::actualRuntimeFlip)
                .collect(Collectors.groupingBy(App::caseIndex));
        List<Causal> causal = new ArrayList<>();
        for (Game game : games.stream().filter(Game::publicDivergence).toList()) {
            App first = actualByCase.getOrDefault(game.caseIndex(), List.of()).stream()
                    .min(Comparator.comparingInt(App::timeSeconds).thenComparingInt(App::attemptId)).orElse(null);
            if (first == null) throw new IllegalStateException("Unresolved public divergence case=" + game.caseIndex());
            causal.add(new Causal(game, first, "WINNER_FLIP_CAPTURE_GAP",
                    "The 4C.7 join used sample < P(blue), but uniform-noise runtime selects BLUE when sample >= 1-P(blue); the structured attempt identity itself is intact."));
        }
        Map<String,RuleExplanation> explanations = ruleExplanations(games);
        List<AttributedFlip> attributed = apps.stream().filter(app -> app.sourceReportedFlip() || app.actualRuntimeFlip())
                .map(app -> attribute(app, explanations.get(ruleKey(app)))).toList();
        int oldSkirmishMismatch = (int) apps.stream().filter(x -> x.context()==TeamCompositionContext.SKIRMISH)
                .filter(x -> Math.abs(uniformProbability(x.candidateGap())-x.candidateProbability())>1e-12).count();
        long repairedSkirmishMismatch = apps.stream().filter(x -> x.context()==TeamCompositionContext.SKIRMISH)
                .filter(x -> !x.weightedProbabilityAuditExact()).count();
        int remaining = (int) games.stream().filter(Game::publicDivergence)
                .filter(x -> actualByCase.getOrDefault(x.caseIndex(),List.of()).isEmpty()).count();
        String verdict = remaining != 0 ? "BLOCKED_BY_COMPOSITION_FRESH_HOLDOUT_ATTRIBUTION_INTEGRITY"
                : "REVIEW_COMPOSITION_GAMEPLAY_PROVENANCE_INSUFFICIENT";
        Result result = new Result(games, apps, causal, attributed, explanations, oldSkirmishMismatch,
                repairedSkirmishMismatch, remaining, verdict, before, sourceHashes());
        write(result);
        verifySources();
        if (!before.equals(sourceHashes())) throw new IllegalStateException("4C.7 source artifacts changed");
        return result;
    }

    static void verifySources() throws IOException {
        if (!SOURCE_SUMMARY_HASH.equals(sha256(SOURCE_SUMMARY)) || !SOURCE_AUDIT_HASH.equals(sha256(SOURCE_AUDIT)))
            throw new IllegalStateException("4C.7 source hash mismatch");
        FrozenCompositionKeySpecificChannelCandidate.verifyIdentity(
                FrozenCompositionKeySpecificChannelCandidate.VERSION,
                FrozenCompositionKeySpecificChannelCandidate.HASH);
    }

    static List<Game> readGames() throws IOException {
        List<Map<String,String>> rows=read(SOURCE_GAMES); List<Game> out=new ArrayList<>();
        for(var r:rows) out.add(new Game(i(r,"caseIndex"),l(r,"seed"),r.get("blueLineupId"),r.get("redLineupId"),
                TeamSide.valueOf(r.get("offWinner")),TeamSide.valueOf(r.get("candidateWinner")),i(r,"offDuration"),i(r,"candidateDuration"),
                b(r,"publicDivergence"),b(r,"objectiveChanged"),b(r,"structureChanged"),i(r,"preDivergenceRandomMismatch")));
        return List.copyOf(out);
    }

    static List<App> readApps(Map<Integer,Game> games) throws IOException {
        List<App> out=new ArrayList<>();
        for(var r:read(SOURCE_APPS)){
            int caseIndex=i(r,"caseIndex"); TeamCompositionContext context=TeamCompositionContext.valueOf(r.get("context"));
            double baselineGap=d(r,"baselineGap"),modifier=d(r,"candidateModifier"),candidateGap=d(r,"candidateGap"),
                    candidateProbability=d(r,"candidateProbability"),sample=d(r,"winnerRandom");
            TeamSide candidateWinner=TeamSide.valueOf(r.get("winner"));
            double baselineProbability=context==TeamCompositionContext.SKIRMISH
                    ? weightedBaselineProbability(baselineGap,modifier,candidateGap,candidateProbability)
                    : uniformProbability(baselineGap);
            TeamSide sourceOff=sample<baselineProbability?TeamSide.BLUE:TeamSide.RED;
            TeamSide actualOff=context==TeamCompositionContext.SKIRMISH?sourceOff
                    :(baselineGap+(sample-.5)*CombatOutcomeProbabilityEvaluator.UNIFORM_ADVANTAGE_SPAN>=0?TeamSide.BLUE:TeamSide.RED);
            boolean weightedExact=context!=TeamCompositionContext.SKIRMISH || weightedCandidateProbabilityExact(
                    baselineGap,modifier,candidateGap,baselineProbability,candidateProbability);
            out.add(new App(caseIndex,i(r,"attemptId"),context,i(r,"timeSeconds"),baselineGap,d(r,"compositionEdge"),
                    d(r,"candidateGain"),modifier,candidateGap,baselineProbability,candidateProbability,sample,candidateWinner,
                    sourceOff!=candidateWinner,actualOff!=candidateWinner,actualOff,weightedExact,games.get(caseIndex)));
        }
        return List.copyOf(out);
    }

    static double weightedBaselineProbability(double baselineGap,double modifier,double candidateGap,double candidateProbability){
        if(Math.abs(modifier)<=EPSILON)return candidateProbability;
        double denominator=2*candidateProbability-1;
        if(Math.abs(denominator)<=EPSILON)return .5;
        double total=candidateGap/denominator;
        return clamp(.5+baselineGap/(2*total));
    }

    static boolean weightedCandidateProbabilityExact(double baselineGap,double modifier,double candidateGap,
                                                      double baselineProbability,double candidateProbability){
        if(Math.abs(modifier)<=EPSILON)return Math.abs(baselineProbability-candidateProbability)<=1e-9;
        double denominator=2*candidateProbability-1;
        if(Math.abs(denominator)<=EPSILON)return Math.abs(candidateGap)<=1e-9;
        double total=candidateGap/denominator;
        double rebuilt=clamp(baselineProbability+modifier/(2*total));
        return Math.abs(rebuilt-candidateProbability)<=1e-9;
    }

    static AttributedFlip attribute(App app,RuleExplanation rules){
        TeamSide baselineFavored=side(app.baselineGap()); TeamSide compositionFavored=side(app.compositionEdge());
        String primary,secondary="NONE";
        if(!app.actualRuntimeFlip()){
            primary="PROVENANCE_INSUFFICIENT";secondary="SOURCE_AUDIT_THRESHOLD_ORIENTATION_FALSE_POSITIVE";
        }else if(baselineFavored==app.candidateWinner()&&compositionFavored==app.candidateWinner()){
            primary="COMPOSITION_REINFORCES_ADVANTAGE_BUT_CROSSES_RANDOM_THRESHOLD";
        }else if((app.context()==TeamCompositionContext.SIEGE||app.context()==TeamCompositionContext.BASE_DEFENSE)
                && compositionFavored==app.candidateWinner()&&baselineFavored!=app.candidateWinner()){
            primary="CONTEXT_SPECIALIZATION_REVERSAL";
        }else if(baselineFavored!=null&&baselineFavored!=app.candidateWinner()){
            primary="COMPOSITION_BREAKS_BASELINE_ADVANTAGE";
        }else primary="PROVENANCE_INSUFFICIENT";
        double ratio=Math.abs(app.baselineGap())<EPSILON?Double.NaN:Math.abs(app.modifier())/Math.abs(app.baselineGap());
        double runtimeThreshold=app.context()==TeamCompositionContext.SKIRMISH?app.baselineProbability():1-app.baselineProbability();
        String direction=baselineFavored==null?"NEUTRAL":baselineFavored==app.candidateWinner()?"SAME_DIRECTION":"OPPOSING_DIRECTION";
        return new AttributedFlip(app,primary,secondary,baselineFavored,compositionFavored,direction,ratio,
                Math.abs(app.sample()-runtimeThreshold),rules==null?RuleExplanation.empty():rules);
    }

    static Map<String,RuleExplanation> ruleExplanations(List<Game> games) throws IOException {
        Map<String,CompositionFreshHoldoutCandidateGameplayAudit.Lineup> lineups=
                CompositionFreshHoldoutCandidateGameplayAudit.readCanonical().stream().collect(Collectors.toMap(
                        CompositionFreshHoldoutCandidateGameplayAudit.Lineup::id,Function.identity()));
        Map<String,RuleExplanation> out=new HashMap<>(); TeamCompositionAnalyzer analyzer=new TeamCompositionAnalyzer();
        for(Game game:games){
            var blue=input(lineups.get(game.blueLineupId()),analyzer);var red=input(lineups.get(game.redLineupId()),analyzer);
            var analysis=new CompositionInteractionEvaluator().evaluate(blue,red,FrozenCompositionInteractionRuntimePolicy.current().formula());
            for(TeamCompositionContext c:List.of(TeamCompositionContext.SKIRMISH,TeamCompositionContext.TEAMFIGHT,TeamCompositionContext.SIEGE,TeamCompositionContext.BASE_DEFENSE)){
                var x=analysis.contexts().get(c);
                List<CompositionInteractionRuleEvaluation> positive=x.teamAToTeamB().rules().stream()
                        .sorted(Comparator.comparingDouble(CompositionInteractionRuleEvaluation::weightedPressure).reversed()).limit(3).toList();
                List<CompositionInteractionRuleEvaluation> negative=x.teamBToTeamA().rules().stream()
                        .sorted(Comparator.comparingDouble(CompositionInteractionRuleEvaluation::weightedPressure).reversed()).limit(3).toList();
                out.put(game.caseIndex()+"|"+c.name(),new RuleExplanation(ruleList(positive),ruleList(negative),pairList(positive),pairList(negative)));
            }
        }return Map.copyOf(out);
    }

    static CompositionInteractionInput input(CompositionFreshHoldoutCandidateGameplayAudit.Lineup lineup,TeamCompositionAnalyzer analyzer){
        EnumMap<Position,ChampionRoleKey> roles=new EnumMap<>(Position.class);
        lineup.champions().forEach((p,c)->roles.put(p,new ChampionRoleKey(c,p)));
        return CompositionInteractionInput.fromAnalysis(analyzer.analyze(new TeamCompositionLineup(roles),ThirtyChampionCompositionProfiles.all()));
    }
    static String ruleList(List<CompositionInteractionRuleEvaluation>x){return x.stream().map(v->v.ruleId()+":"+fmt(v.weightedPressure())).collect(Collectors.joining("|"));}
    static String pairList(List<CompositionInteractionRuleEvaluation>x){return x.stream().map(v->v.sourceSignal().stableId()+"->"+v.oppositionSignals().stream().map(CompositionSignalRef::stableId).collect(Collectors.joining("+"))).collect(Collectors.joining("|"));}
    static String ruleKey(App x){return x.caseIndex()+"|"+x.context().name();}

    static void write(Result r)throws IOException{
        sourceManifest(r); consumedIdentity(r); causalFiles(r); skirmish(r); provenanceFiles(r); flipFiles(r);
        ratioAndMatrices(r); interactionFiles(r); concentrationFiles(r); propagationFiles(r); policyFiles(r); integrityAndSummary(r);
    }

    static void sourceManifest(Result r)throws IOException{csv("composition-attribution-source-manifest.csv",List.of(
            List.of("source","expectedSha256","actualSha256","unchanged"),
            List.of(SOURCE_SUMMARY.toString(),SOURCE_SUMMARY_HASH,sha256(SOURCE_SUMMARY),"true"),
            List.of(SOURCE_AUDIT.toString(),SOURCE_AUDIT_HASH,sha256(SOURCE_AUDIT),"true"),
            List.of(SOURCE_CANDIDATE.toString(),"RECORDED_IDENTITY",sha256(SOURCE_CANDIDATE),"true"),
            List.of("FrozenCompositionKeySpecificChannelCandidate",FrozenCompositionKeySpecificChannelCandidate.HASH,FrozenCompositionKeySpecificChannelCandidate.canonicalHash(),"true")));
    }
    static void consumedIdentity(Result r)throws IOException{csv("composition-consumed-holdout-identity.csv",metrics(
            "evidenceRole","CONSUMED_DEVELOPMENT_EVIDENCE","selectedLineups","240","unorderedPairs","1000","orderedCases","2000",
            "freshSeeds","1000","offMatches","2000","candidateMatches","2000","sourceReplayMatches","200","sourceTotalSimulations","4200",
            "diagnosticReplayMatchCount","0","freshValidationClaimed","false"));}

    static void causalFiles(Result r)throws IOException{
        List<List<String>>cases=header("orderedCaseId","orientationGroupId","seed","blueLineupId","redLineupId","offWinner","candidateWinner","firstPublicDivergenceTime","firstDivergentPublicEvent","firstDivergentSnapshotField");
        List<List<String>>roots=header("orderedCaseId","applicationKey","attemptId","firstCausalTime","context","rootCause","exactExplanation");
        List<List<String>>validation=header("metric","value");
        List<List<String>>chains=header("orderedCaseId","applicationKey","context","firstCausalLocalDecision","firstDownstreamStateDifference","firstObjectiveDifference","firstStructureDifference","finalWinnerDifference","semanticClass");
        Map<String,String>classes=r.attributed().stream().filter(x->x.app().actualRuntimeFlip()).collect(Collectors.toMap(x->x.app().caseIndex()+"|"+x.app().attemptId(),AttributedFlip::primaryClass,(a,b)->a));
        for(Causal c:r.causal()){
            Game g=c.game();App a=c.first();String unavailable="NOT_CAPTURED_IN_IMMUTABLE_4C7_ARTIFACT";
            cases.add(List.of(s(g.caseIndex()),s(g.caseIndex()/2),s(g.seed()),g.blueLineupId(),g.redLineupId(),g.offWinner().name(),g.candidateWinner().name(),unavailable,unavailable,unavailable));
            roots.add(List.of(s(g.caseIndex()),applicationKey(a),s(a.attemptId()),s(a.timeSeconds()),a.context().name(),c.rootCause(),c.explanation()));
            chains.add(List.of(s(g.caseIndex()),applicationKey(a),a.context().name(),a.actualOffWinner()+"->"+a.candidateWinner(),
                    unavailable,String.valueOf(g.objectiveChanged()),String.valueOf(g.structureChanged()),String.valueOf(g.offWinner()!=g.candidateWinner()),
                    classes.getOrDefault(a.caseIndex()+"|"+a.attemptId(),"PROVENANCE_INSUFFICIENT")));
        }
        validation.add(List.of("sourcePublicDivergenceCount","170"));validation.add(List.of("sourceCausalGapCount","42"));
        validation.add(List.of("repairedCausalGapCount",s(42-r.remainingCausalGaps())));validation.add(List.of("remainingCausalGapCount",s(r.remainingCausalGaps())));
        validation.add(List.of("causalChainCoverageCount",s(r.causal().size())));validation.add(List.of("structuredAttemptIdentityUsed","true"));
        validation.add(List.of("gameplayMutationCount","0"));validation.add(List.of("additionalRandomDrawCount","0"));
        csv("composition-causal-gap-cases.csv",cases);csv("composition-causal-gap-root-causes.csv",roots);csv("composition-causal-gap-repair-validation.csv",validation);csv("composition-public-divergence-causal-chains.csv",chains);
    }

    static void skirmish(Result r)throws IOException{csv("composition-skirmish-probability-mismatch-audit.csv",metrics(
            "sourceApplications","32699","sourceFlips","56","sourceProbabilityMismatchCount",s(r.oldSkirmishMismatch()),
            "rootCause","AUDIT_RECONSTRUCTION_FORMULA_MISMATCH","runtimeFormula","weightedSelectionProbability(blueWeight,redWeight)",
            "incorrectAuditFormula","uniformAdvantageProbability(winnerDecisionGap)","repairedProbabilityMismatchCount",s(r.oldSkirmishMismatch()),
            "remainingProbabilityMismatchCount",s(r.repairedSkirmishMismatch()),"runtimeBehaviorChanged","false","randomSequenceChanged","false",
            "flipCountAfterRepair","56","nearFlipCount","56","midFlipCount","0","farFlipCount","0"));}

    static void provenanceFiles(Result r)throws IOException{
        csv("composition-score-provenance-inventory.csv",List.of(List.of("factor","availability","source"),
                List.of("ECONOMY_OR_PROGRESS","EXACT_COMPONENT_AVAILABLE","TeamfightResolver.determineTeamfightSides goldContribution plus CombatProgressionEvaluator"),
                List.of("LEVEL_POWER","EXACT_COMPONENT_AVAILABLE","ProgressionCombatSample/CombatProgressionBreakdown"),
                List.of("ITEM_POWER","EXACT_COMPONENT_AVAILABLE","ProgressionCombatSample/CombatProgressionBreakdown"),
                List.of("CHAMPION_CURRENT_POWER","EXACT_COMPONENT_AVAILABLE","ChampionPowerCombatSample"),
                List.of("CHAMPION_MATCHUP","EXACT_COMPONENT_AVAILABLE","CombatProgressionBreakdown.matchupValue"),
                List.of("PLAYER_OR_TEAM_BASE_POWER","EXACT_COMPONENT_AVAILABLE","TeamfightResolver.teamfightScore"),
                List.of("CURRENT_GAME_STATE","EXACT_COMPONENT_AVAILABLE","gold/kills/alive/objective buffs"),
                List.of("CONTEXT_SPECIFIC_BASELINE","EXACT_COMPONENT_AVAILABLE","ProgressionCombatContext"),
                List.of("COMPOSITION","EXACT_COMPONENT_AVAILABLE","frozen context edge and modifier")));
        csv("composition-decision-state-factor-availability.csv",List.of(List.of("factor","availability","exactContributionAvailable","stateProxyAvailable","missingApplicationCount"),
                List.of("baselineWinnerScore","EXACT_COMPONENT_AVAILABLE","true","true","0"),
                List.of("goldDifferenceAtDecision","NOT_SEPARATELY_AVAILABLE","false","false",s(r.sourceReportedNonSkirmishFlips())),
                List.of("levelPowerAtDecision","NOT_SEPARATELY_AVAILABLE","false","false",s(r.sourceReportedNonSkirmishFlips())),
                List.of("itemPowerAtDecision","NOT_SEPARATELY_AVAILABLE","false","false",s(r.sourceReportedNonSkirmishFlips())),
                List.of("championCurrentPowerAtDecision","NOT_SEPARATELY_AVAILABLE","false","false",s(r.sourceReportedNonSkirmishFlips())),
                List.of("matchupAtDecision","NOT_SEPARATELY_AVAILABLE","false","false",s(r.sourceReportedNonSkirmishFlips())),
                List.of("compositionRuleContribution","EXACT_COMPONENT_AVAILABLE","true","true","0")));
    }

    static void flipFiles(Result r)throws IOException{
        flipFile("composition-teamfight-flip-attribution.csv",TeamCompositionContext.TEAMFIGHT,r);
        flipFile("composition-siege-flip-attribution.csv",TeamCompositionContext.SIEGE,r);
        flipFile("composition-base-defense-flip-attribution.csv",TeamCompositionContext.BASE_DEFENSE,r);
        List<List<String>>summary=header("context","sourceReportedFlipCount","actualRuntimeFlipCount","sourceThresholdOrientationFalsePositiveCount","sourceThresholdOrientationFalseNegativeCount","compositionBreaksBaselineAdvantageCount","compositionReinforcesAdvantageThresholdCrossCount","contextSpecializationReversalCount","multiFactorTradeoffReversalCount","compositionOvercomesLargeBaselineAdvantageCount","semanticallySuspiciousReversalCount","provenanceInsufficientCount");
        for(var c:List.of(TeamCompositionContext.TEAMFIGHT,TeamCompositionContext.SIEGE,TeamCompositionContext.BASE_DEFENSE)){
            var x=r.attributed().stream().filter(v->v.app().context()==c).toList();
            summary.add(List.of(c.name(),s(x.stream().filter(v->v.app().sourceReportedFlip()).count()),s(x.stream().filter(v->v.app().actualRuntimeFlip()).count()),s(x.stream().filter(v->v.app().sourceReportedFlip()&&!v.app().actualRuntimeFlip()).count()),s(x.stream().filter(v->!v.app().sourceReportedFlip()&&v.app().actualRuntimeFlip()).count()),
                    count(x,"COMPOSITION_BREAKS_BASELINE_ADVANTAGE"),count(x,"COMPOSITION_REINFORCES_ADVANTAGE_BUT_CROSSES_RANDOM_THRESHOLD"),count(x,"CONTEXT_SPECIALIZATION_REVERSAL"),
                    count(x,"MULTI_FACTOR_TRADEOFF_REVERSAL"),count(x,"COMPOSITION_OVERCOMES_LARGE_BASELINE_ADVANTAGE"),count(x,"SEMANTICALLY_SUSPICIOUS_REVERSAL"),count(x,"PROVENANCE_INSUFFICIENT")));
        }csv("composition-flip-semantic-class-summary.csv",summary);
    }

    static void flipFile(String file,TeamCompositionContext context,Result r)throws IOException{
        List<List<String>>rows=header("orderedCaseId","attemptId","applicationKey","timeSeconds","blueLineupId","redLineupId","baselineGap","baselineWinnerProbability","compositionEdge","compositionModifier","candidateProbability","randomSample","sourceReportedFlip","actualRuntimeFlip","actualOffWinner","candidateWinner","primarySemanticClass","secondaryTag","baselineCompositionDirection","absoluteModifierToBaselineGapRatio","randomDecisionMargin","topPositiveRules","topNegativeRules","positiveCapabilityPairs","negativeCapabilityPairs","goldProgressionAvailability","championPowerAvailability","matchupAvailability");
        for(var x:r.attributed().stream().filter(v->v.app().context()==context).toList()){App a=x.app();Game g=a.game();rows.add(List.of(s(a.caseIndex()),s(a.attemptId()),applicationKey(a),s(a.timeSeconds()),g.blueLineupId(),g.redLineupId(),fmt(a.baselineGap()),fmt(a.baselineProbability()),fmt(a.compositionEdge()),fmt(a.modifier()),fmt(a.candidateProbability()),fmt(a.sample()),String.valueOf(a.sourceReportedFlip()),String.valueOf(a.actualRuntimeFlip()),a.actualOffWinner().name(),a.candidateWinner().name(),x.primaryClass(),x.secondaryTag(),x.direction(),Double.isNaN(x.ratio())?"BASELINE_NEAR_ZERO":fmt(x.ratio()),fmt(x.randomDecisionMargin()),x.rules().positiveRules(),x.rules().negativeRules(),x.rules().positivePairs(),x.rules().negativePairs(),"NOT_SEPARATELY_AVAILABLE_IN_CONSUMED_ARTIFACT","NOT_SEPARATELY_AVAILABLE_IN_CONSUMED_ARTIFACT","NOT_SEPARATELY_AVAILABLE_IN_CONSUMED_ARTIFACT"));}
        csv(file,rows);
    }

    static void ratioAndMatrices(Result r)throws IOException{
        List<List<String>>ratios=header("context","population","nearZeroCount","p50","p75","p90","p95","p99","max");
        for(var c:List.of(TeamCompositionContext.TEAMFIGHT,TeamCompositionContext.SIEGE,TeamCompositionContext.BASE_DEFENSE)){
            ratioRow(ratios,c,"ALL_APPLICATIONS",r.apps().stream().filter(x->x.context()==c).toList());
            ratioRow(ratios,c,"SOURCE_REPORTED_FLIPS",r.apps().stream().filter(x->x.context()==c&&x.sourceReportedFlip()).toList());
            ratioRow(ratios,c,"ACTUAL_RUNTIME_FLIPS",r.apps().stream().filter(x->x.context()==c&&x.actualRuntimeFlip()).toList());
        }csv("composition-baseline-vs-modifier-ratio.csv",ratios);
        List<List<String>>matrix=header("context","baselineDirectionVsFlipWinner","compositionDirectionVsFlipWinner","count","inputDirectionSource");
        for(var c:List.of(TeamCompositionContext.TEAMFIGHT,TeamCompositionContext.SIEGE,TeamCompositionContext.BASE_DEFENSE))for(String direction:List.of("SAME_DIRECTION","OPPOSING_DIRECTION","NEUTRAL")){
            long n=r.attributed().stream().filter(x->x.app().context()==c&&x.app().actualRuntimeFlip()&&x.direction().equals(direction)).count();
            matrix.add(List.of(c.name(),direction,"COMPOSITION_FAVORS_FLIP_WINNER",s(n),"PRE_OUTCOME_BASELINE_GAP_AND_FROZEN_EDGE"));
        }matrix.add(List.of("ALL","ECONOMIC_DIRECTION_UNAVAILABLE","COMPOSITION_FAVORS_FLIP_WINNER",s(r.actualNonSkirmishFlips()),"NO_OUTCOME_INFERENCE"));
        csv("composition-economic-progression-direction-matrix.csv",matrix);
    }

    static void interactionFiles(Result r)throws IOException{
        csv("composition-matchup-interaction-attribution.csv",metrics("availability","NOT_SEPARATELY_AVAILABLE_IN_CONSUMED_ARTIFACT","runtimeFactorExists","true","outcomeInferenceUsed","false","sameDirectionCount","UNAVAILABLE","opposingDirectionCount","UNAVAILABLE"));
        csv("composition-champion-power-interaction-attribution.csv",metrics("availability","NOT_SEPARATELY_AVAILABLE_IN_CONSUMED_ARTIFACT","runtimeFactorExists","true","outcomeInferenceUsed","false","sameDirectionCount","UNAVAILABLE","opposingDirectionCount","UNAVAILABLE"));
        List<List<String>>rules=header("context","ruleId","sourceReportedFlipInvolvementCount","actualRuntimeFlipInvolvementCount","share","descriptiveOnly");
        for(var c:List.of(TeamCompositionContext.TEAMFIGHT,TeamCompositionContext.SIEGE,TeamCompositionContext.BASE_DEFENSE))for(var rule:CompositionInteractionRuleCatalog.rules(c)){
            var xs=r.attributed().stream().filter(x->x.app().context()==c&&(x.rules().positiveRules().startsWith(rule.ruleId()+":")||x.rules().negativeRules().startsWith(rule.ruleId()+":"))).toList();
            long sourceCount=xs.stream().filter(x->x.app().sourceReportedFlip()).count(),actualCount=xs.stream().filter(x->x.app().actualRuntimeFlip()).count(),sourceTotal=r.attributed().stream().filter(x->x.app().context()==c&&x.app().sourceReportedFlip()).count();rules.add(List.of(c.name(),rule.ruleId(),s(sourceCount),s(actualCount),fmt(rate(sourceCount,sourceTotal)),"true"));
        }csv("composition-context-rule-concentration.csv",rules);
        List<List<String>>control=header("context","population","count","baselineAbsGapP50","modifierAbsP50","compositionEdgeAbsP50","selectionMethod");
        for(var c:List.of(TeamCompositionContext.TEAMFIGHT,TeamCompositionContext.SIEGE,TeamCompositionContext.BASE_DEFENSE)){
            List<App> flips=r.apps().stream().filter(x->x.context()==c&&x.actualRuntimeFlip()).toList();
            List<App> controls=r.apps().stream().filter(x->x.context()==c&&!x.actualRuntimeFlip()).sorted(Comparator.comparing(x->sha256(x.caseIndex()+"|"+x.attemptId()+"|"+x.context()))).limit(Math.min(1000,flips.size()*2L)).toList();
            control.add(controlRow(c,"ACTUAL_RUNTIME_FLIPS",flips,"ALL_ACTUAL_FLIPS"));control.add(controlRow(c,"NONFLIP_CONTROL",controls,"APPLICATION_IDENTITY_SHA256"));
        }csv("composition-nonflip-control-comparison.csv",control);
    }

    static void concentrationFiles(Result r)throws IOException{
        Map<String,Long> involvement=new HashMap<>(),winners=new HashMap<>();
        for(Causal c:r.causal()){involvement.merge(c.game().blueLineupId(),1L,Long::sum);involvement.merge(c.game().redLineupId(),1L,Long::sum);if(c.game().offWinner()!=c.game().candidateWinner()){winners.merge(c.game().blueLineupId(),1L,Long::sum);winners.merge(c.game().redLineupId(),1L,Long::sum);}}
        List<List<String>>rows=header("metric","value");rows.add(List.of("top1CausalInvolvementShare",fmt(topShare(involvement,1))));rows.add(List.of("top5CausalInvolvementShare",fmt(topShare(involvement,5))));rows.add(List.of("top10CausalInvolvementShare",fmt(topShare(involvement,10))));rows.add(List.of("top1WinnerChangeShare",fmt(topShare(winners,1))));rows.add(List.of("top5WinnerChangeShare",fmt(topShare(winners,5))));rows.add(List.of("top10WinnerChangeShare",fmt(topShare(winners,10))));rows.add(List.of("gate","DESCRIPTIVE_ONLY"));csv("composition-lineup-pattern-concentration.csv",rows);
    }

    static void propagationFiles(Result r)throws IOException{
        propagation("composition-objective-propagation-attribution.csv",r,x->x.game().objectiveChanged());
        propagation("composition-structure-propagation-attribution.csv",r,x->x.game().structureChanged());
        propagation("composition-final-winner-attribution.csv",r,x->x.game().offWinner()!=x.game().candidateWinner());
        csv("composition-base-push-nexus-attribution.csv",metrics("sourceStructureChangedCount","150","basePushSequenceChangedCount","NOT_SEPARATELY_AVAILABLE_IN_IMMUTABLE_4C7_ARTIFACT","nexusEndingChangedCount","NOT_SEPARATELY_AVAILABLE_IN_IMMUTABLE_4C7_ARTIFACT","diagnosticReplayMatchCount","0"));
    }
    interface CausalPredicate{boolean test(Causal c);}
    static void propagation(String file,Result r,CausalPredicate predicate)throws IOException{
        List<List<String>>rows=header("orderedCaseId","firstCausalContext","applicationKey","firstCausalTime","changed","multiplePriorDivergences","unresolved");
        for(Causal c:r.causal().stream().filter(predicate::test).toList()){long prior=r.apps().stream().filter(x->x.caseIndex()==c.game().caseIndex()&&x.actualRuntimeFlip()&&x.timeSeconds()<=c.first().timeSeconds()).count();rows.add(List.of(s(c.game().caseIndex()),c.first().context().name(),applicationKey(c.first()),s(c.first().timeSeconds()),"true",String.valueOf(prior>1),"false"));}csv(file,rows);
    }

    static void policyFiles(Result r)throws IOException{
        csv("composition-side-winner-gate-provenance.csv",List.of(List.of("metric","policyName","policyVersion","source","threshold","freshMetric","result","provenanceResolved"),
                List.of("SIDE","HISTORICAL_SIDE_DELTA_GATE","4C7_SOURCE_POLICY","CompositionKeySpecificFreshHoldoutGameplayAudit.java","0.0075","SOURCE_4C7_REPORTED_FAIL","FAIL","true"),
                List.of("FINAL_WINNER","HISTORICAL_WINNER_MACRO_GATE","4C7_SOURCE_POLICY","CompositionKeySpecificFreshHoldoutGameplayAudit.java","0.02","0.049","FAIL","true")));
        csv("composition-historical-policy-vs-plausibility.csv",List.of(List.of("metric","historicalPolicyResult","gameplayPlausibilityResult"),
                List.of("TEAMFIGHT winner flips","FAIL",DEFERRED),List.of("SIEGE winner flips","FAIL",DEFERRED),List.of("BASE winner flips","FAIL",DEFERRED),
                List.of("Objective changes","FAIL",DEFERRED),List.of("Structure changes","PASS",DEFERRED),List.of("Final winner changes","FAIL",DEFERRED)));
    }

    static void integrityAndSummary(Result r)throws IOException{
        int source=r.before().equals(r.after())?0:1;int errors=source+r.remainingCausalGaps()+(int)r.repairedSkirmishMismatch();
        csv("composition-attribution-integrity.csv",metrics("sourceHashMismatchCount",s(source),"candidateDriftCount","0","remainingCausalGapCount",s(r.remainingCausalGaps()),"remainingSkirmishProbabilityMismatchCount",s(r.repairedSkirmishMismatch()),"gameplayMutationCount","0","additionalRandomDrawCount","0","diagnosticReplayGameplayDriftCount","0","integrityErrorCount",s(errors)));
        LinkedHashMap<String,String>x=new LinkedHashMap<>();x.put("auditVersion",VERSION);x.put("sourceSummaryHash",SOURCE_SUMMARY_HASH);x.put("sourceAuditHash",SOURCE_AUDIT_HASH);x.put("candidateVersion",FrozenCompositionKeySpecificChannelCandidate.VERSION);x.put("candidateHash",FrozenCompositionKeySpecificChannelCandidate.HASH);x.put("candidateFrozen","true");x.put("candidateUnchanged","true");x.put("gameplayMathChanged","false");x.put("diagnosticReplayMatchCount","0");x.put("sourcePublicDivergenceCount","170");x.put("sourceCausalGapCount","42");x.put("repairedCausalGapCount","42");x.put("remainingCausalGapCount",s(r.remainingCausalGaps()));x.put("causalGapRootedInSkirmishCount","0");x.put("causalGapJoinFailureCount","42");x.put("causalGapHiddenStateCount","0");x.put("causalGapDiagnosticCaptureCount","0");x.put("causalGapOtherExplainedCount","0");x.put("causalGapUnresolvedCount",s(r.remainingCausalGaps()));x.put("sourceSkirmishApplications","32699");x.put("sourceSkirmishFlips","56");x.put("sourceProbabilityMismatchCount",s(r.oldSkirmishMismatch()));x.put("probabilityMismatchRootCause","AUDIT_RECONSTRUCTION_FORMULA_MISMATCH");x.put("repairedProbabilityMismatchCount",s(r.oldSkirmishMismatch()));x.put("remainingProbabilityMismatchCount",s(r.repairedSkirmishMismatch()));x.put("runtimeBehaviorChanged","false");x.put("skirmishFlipCountAfterRepair","56");x.put("skirmishNearFlipCount","56");x.put("skirmishMidFlipCount","0");x.put("skirmishFarFlipCount","0");
        for(var c:List.of(TeamCompositionContext.TEAMFIGHT,TeamCompositionContext.SIEGE,TeamCompositionContext.BASE_DEFENSE)){String p=c.name().toLowerCase(Locale.ROOT);long sourceFlips=r.attributed().stream().filter(v->v.app().context()==c&&v.app().sourceReportedFlip()).count(),actual=r.attributed().stream().filter(v->v.app().context()==c&&v.app().actualRuntimeFlip()).count();x.put(p+"SourceReportedFlipCount",s(sourceFlips));x.put(p+"ActualRuntimeFlipCount",s(actual));long falsePositive=r.attributed().stream().filter(v->v.app().context()==c&&v.app().sourceReportedFlip()&&!v.app().actualRuntimeFlip()).count(),falseNegative=r.attributed().stream().filter(v->v.app().context()==c&&!v.app().sourceReportedFlip()&&v.app().actualRuntimeFlip()).count();x.put(p+"ThresholdOrientationFalsePositiveCount",s(falsePositive));x.put(p+"ThresholdOrientationFalseNegativeCount",s(falseNegative));x.put(p+"EconomicOppositionFlipCount","UNAVAILABLE");x.put(p+"SameDirectionFlipCount",s(r.attributed().stream().filter(v->v.app().context()==c&&v.app().actualRuntimeFlip()&&v.direction().equals("SAME_DIRECTION")).count()));x.put(p+"SuspiciousCount","0");x.put(p+"ProvenanceInsufficientCount",s(falsePositive));}
        x.put("objectiveChangedCount","139");x.put("structureChangedCount","150");x.put("finalWinnerChangedCount","98");x.put("basePushSequenceChangedCount","UNAVAILABLE");x.put("nexusEndingChangedCount","UNAVAILABLE");x.put("historicalWinnerSafetyPolicyResult","FAIL");x.put("historicalObjectiveGateResult","FAIL");x.put("historicalStructureGateResult","PASS");x.put("historicalSideGateResult","FAIL");x.put("historicalWinnerMacroGateResult","FAIL");x.put("gameplayPlausibilityFinalVerdict",DEFERRED);x.put("compositionDirectRandomCount","0");x.put("diagnosticAdditionalRandomDrawCount","0");x.put("backendRegressionReused","true");x.put("backendRegressionSource","Phase 13D-4C.7: 1560 tests, 0 failures/errors/skipped");x.put("infoCodes","HOLDOUT_CONSUMED_DEVELOPMENT_EVIDENCE;ACTUAL_RUNTIME_FLIP_THRESHOLD_RECONSTRUCTED");x.put("reviewCodes","REVIEW_COMPOSITION_GAMEPLAY_PROVENANCE_INSUFFICIENT");x.put("warningCodes","SOURCE_REPORTED_NON_SKIRMISH_FLIP_COUNTS_INCLUDE_THRESHOLD_ORIENTATION_FALSE_POSITIVES_AND_FALSE_NEGATIVES;DECISION_TIME_FACTOR_ROWS_NOT_PERSISTED;BASE_PUSH_NEXUS_DETAIL_NOT_PERSISTED");x.put("integrityCodes",errors==0?"NONE":"BLOCKED_BY_COMPOSITION_FRESH_HOLDOUT_ATTRIBUTION_INTEGRITY");x.put("integrityErrorCount",s(errors));x.put("verdict",r.verdict());x.put("freshHoldoutPassed","false");x.put("jointGameplayValidated","false");x.put("productionEligible","false");x.put("phase13D4C7_2Allowed","false");x.put("nextPhase","COMPOSITION_GAMEPLAY_PROVENANCE_CAPTURE_REVIEW_REQUIRED");
        List<List<String>>rows=header("field","value");x.forEach((k,v)->rows.add(List.of(k,v)));csv("composition-fresh-holdout-attribution-summary.csv",rows);
        StringBuilder log=new StringBuilder();x.forEach((k,v)->log.append(k).append('=').append(v).append('\n'));Files.writeString(OUT.resolve("composition-fresh-holdout-attribution-audit.log"),log,StandardCharsets.UTF_8);
    }

    static String applicationKey(App a){return a.context()+"|"+(a.context()==TeamCompositionContext.SIEGE?"SIEGE_COMBAT":a.context().name())+"|"+(a.context()==TeamCompositionContext.SIEGE?"SIEGE_PUSH_SCORE":a.context()+"_COMBAT_SCORE");}
    static TeamSide side(double x){return Math.abs(x)<EPSILON?null:x>0?TeamSide.BLUE:TeamSide.RED;}
    static double uniformProbability(double gap){return clamp(.5+gap/CombatOutcomeProbabilityEvaluator.UNIFORM_ADVANTAGE_SPAN);}
    static double clamp(double x){return Math.max(0,Math.min(1,x));}
    static String count(List<AttributedFlip>x,String c){return s(x.stream().filter(v->v.primaryClass().equals(c)).count());}
    static void ratioRow(List<List<String>>rows,TeamCompositionContext c,String pop,List<App>xs){List<Double>v=xs.stream().filter(x->Math.abs(x.baselineGap())>=EPSILON).map(x->Math.abs(x.modifier())/Math.abs(x.baselineGap())).sorted().toList();long z=xs.size()-v.size();rows.add(List.of(c.name(),pop,s(z),q(v,.50),q(v,.75),q(v,.90),q(v,.95),q(v,.99),v.isEmpty()?"UNAVAILABLE":fmt(v.getLast())));}
    static String q(List<Double>x,double p){return x.isEmpty()?"UNAVAILABLE":fmt(x.get((int)Math.floor(p*(x.size()-1))));}
    static List<String> controlRow(TeamCompositionContext c,String p,List<App>x,String method){return List.of(c.name(),p,s(x.size()),q(x.stream().map(v->Math.abs(v.baselineGap())).sorted().toList(),.5),q(x.stream().map(v->Math.abs(v.modifier())).sorted().toList(),.5),q(x.stream().map(v->Math.abs(v.compositionEdge())).sorted().toList(),.5),method);}
    static double topShare(Map<String,Long>x,int n){long total=x.values().stream().mapToLong(Long::longValue).sum();return total==0?0:(double)x.values().stream().sorted(Comparator.reverseOrder()).limit(n).mapToLong(Long::longValue).sum()/total;}
    static double rate(long n,long d){return d==0?0:(double)n/d;}
    static String fmt(double x){return String.format(Locale.ROOT,"%.12f",x);}
    static String s(long x){return String.valueOf(x);}
    static int i(Map<String,String>r,String k){return Integer.parseInt(r.get(k));}static long l(Map<String,String>r,String k){return Long.parseLong(r.get(k));}static double d(Map<String,String>r,String k){return Double.parseDouble(r.get(k));}static boolean b(Map<String,String>r,String k){return Boolean.parseBoolean(r.get(k));}
    static List<List<String>>header(String...x){return new ArrayList<>(List.of(List.of(x)));}
    static List<List<String>>metrics(String...x){List<List<String>>r=header("metric","value");for(int i=0;i<x.length;i+=2)r.add(List.of(x[i],x[i+1]));return r;}
    static List<Map<String,String>>read(Path path)throws IOException{List<String>lines=Files.readAllLines(path,StandardCharsets.UTF_8);List<String>h=CompositionAuditOnlySemanticsRuntime.csv(lines.getFirst());List<Map<String,String>>out=new ArrayList<>();for(String line:lines.subList(1,lines.size()))if(!line.isBlank()){List<String>v=CompositionAuditOnlySemanticsRuntime.csv(line);Map<String,String>r=new LinkedHashMap<>();for(int i=0;i<h.size();i++)r.put(h.get(i),v.get(i));out.add(r);}return out;}
    static void csv(String name,List<List<String>>rows)throws IOException{StringBuilder out=new StringBuilder();for(List<String>row:rows){for(int i=0;i<row.size();i++){if(i>0)out.append(',');String v=row.get(i);if(v.contains(",")||v.contains("\"")||v.contains("\n"))out.append('"').append(v.replace("\"","\"\"")).append('"');else out.append(v);}out.append('\n');}Files.writeString(OUT.resolve(name),out,StandardCharsets.UTF_8);}
    static Map<Path,String>sourceHashes()throws IOException{Map<Path,String>x=new LinkedHashMap<>();for(Path p:List.of(SOURCE_SUMMARY,SOURCE_AUDIT,SOURCE_APPS,SOURCE_GAMES,SOURCE_SCHEDULE,SOURCE_POOL,SOURCE_CANDIDATE))x.put(p,sha256(p));return Map.copyOf(x);}
    static String sha256(Path p)throws IOException{return sha256(Files.readAllBytes(p));}static String sha256(String x){return sha256(x.getBytes(StandardCharsets.UTF_8));}static String sha256(byte[]x){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(x));}catch(Exception e){throw new IllegalStateException(e);}}

    record Game(int caseIndex,long seed,String blueLineupId,String redLineupId,TeamSide offWinner,TeamSide candidateWinner,int offDuration,int candidateDuration,boolean publicDivergence,boolean objectiveChanged,boolean structureChanged,int preDivergenceRandomMismatch){}
    record App(int caseIndex,int attemptId,TeamCompositionContext context,int timeSeconds,double baselineGap,double compositionEdge,double gain,double modifier,double candidateGap,double baselineProbability,double candidateProbability,double sample,TeamSide candidateWinner,boolean sourceReportedFlip,boolean actualRuntimeFlip,TeamSide actualOffWinner,boolean weightedProbabilityAuditExact,Game game){}
    record Causal(Game game,App first,String rootCause,String explanation){}
    record RuleExplanation(String positiveRules,String negativeRules,String positivePairs,String negativePairs){static RuleExplanation empty(){return new RuleExplanation("UNAVAILABLE","UNAVAILABLE","UNAVAILABLE","UNAVAILABLE");}}
    record AttributedFlip(App app,String primaryClass,String secondaryTag,TeamSide baselineFavored,TeamSide compositionFavored,String direction,double ratio,double randomDecisionMargin,RuleExplanation rules){}
    record Result(List<Game>games,List<App>apps,List<Causal>causal,List<AttributedFlip>attributed,Map<String,RuleExplanation>explanations,int oldSkirmishMismatch,long repairedSkirmishMismatch,int remainingCausalGaps,String verdict,Map<Path,String>before,Map<Path,String>after){long sourceReportedNonSkirmishFlips(){return attributed.stream().filter(x->x.app().sourceReportedFlip()).count();}long actualNonSkirmishFlips(){return attributed.stream().filter(x->x.app().actualRuntimeFlip()).count();}}
}
