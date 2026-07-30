package com.lolfm.simulator;

import com.lolfm.champion.ChampionMatchupMode;
import com.lolfm.champion.ChampionMatchupProductionPolicy;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;

/** Artifact-only Phase 13C-5a verdict finalizer. */
public final class ChampionMatchupProductionActivationFinalizer {
    static final Path REPORT=Path.of("build/reports/champion-matchup-production-activation");
    static final Path SUMMARY=REPORT.resolve("champion-matchup-production-activation-summary.csv");
    static final Path LOG=REPORT.resolve("champion-matchup-production-activation-audit.log");
    static final Map<String,String> FROZEN_ARTIFACT_HASHES=Map.of(
            "champion-matchup-production-matrix-regression.csv","41cb106be07cbdbaac7a7e94f9feda3ef778af5ffdb1d37a13b757d41c067c5e",
            "champion-matchup-production-dynamic-parity.csv","8b5b30f1b24b320a5fcad6fc8ad9d9511ce14a25240a9574e87cd6ca6b4e7e4a",
            "champion-matchup-production-full-match.csv","d18b6a48058ece52c938091430fd2c7c40156b205c47c87dd5dff8dbcc0545f4",
            "champion-matchup-production-paired.csv","2a658282c6380d8b9f23a2e223032a3f024eec23adf5edcec4f9c5f24cb8103f",
            "champion-matchup-production-mode-isolation.csv","163aef445643c1c4fa654f9bbd9f901c2cda11309e3d7efce52d1b7b3411b5c8");
    private ChampionMatchupProductionActivationFinalizer(){}

    public static void main(String[]args)throws Exception{
        LinkedHashMap<String,String> previous=readSummary();
        ArtifactEvidence evidence=validateArtifacts();
        TestSummary tests=readTests(Path.of("build/test-results/test"));
        String targetedStatus=Files.exists(REPORT.resolve("champion-matchup-production-targeted-test-result.txt"))
                ?Files.readString(REPORT.resolve("champion-matchup-production-targeted-test-result.txt")).trim():"MISSING";
        boolean targetedPassed=targetedStatus.equals("PASSED");
        boolean fullSuiteExecuted=Files.exists(REPORT.resolve("champion-matchup-production-full-suite-result.txt"))
                && Files.readString(REPORT.resolve("champion-matchup-production-full-suite-result.txt")).trim().equals("EXECUTED");
        boolean currentDefaultOn=com.lolfm.simulator.SimulationOptions.productionDefaults().championMatchupMode()==ChampionMatchupMode.GEOMETRIC_V2;
        boolean activated=evidence.valid()&&targetedPassed&&fullSuiteExecuted&&tests.successful()&&currentDefaultOn;
        int integrity=activated?0:1;
        LinkedHashMap<String,Object> values=new LinkedHashMap<>();
        previous.forEach(values::put);
        values.put("auditVersion","phase-13c-5a-artifact-finalizer-v1");
        values.put("activationAuditEvaluatedMode","GEOMETRIC_V2");
        values.putAll(finalModeFields(activated?ChampionMatchupMode.GEOMETRIC_V2:ChampionMatchupMode.OFF,activated));
        values.put("rollbackReason",activated?"NONE":!targetedPassed?"TARGETED_TEST_FAILURE":!fullSuiteExecuted?"FULL_BACKEND_TEST_NOT_RUN":!tests.successful()?"BACKEND_TEST_FAILURE":"ARTIFACT_INTEGRITY_FAILURE");
        values.put("explicitOffRollbackAvailable",true);
        values.put("simulationRerun",false);
        values.put("matrixArtifactReused",true);
        values.put("dynamicArtifactReused",true);
        values.put("fullMatchArtifactReused",true);
        values.put("pairedArtifactReused",true);
        values.put("modeIsolationArtifactReused",true);
        values.put("artifactMatrixRows",evidence.matrixRows());
        values.put("artifactMatrixExactRows",evidence.matrixExactRows());
        values.put("artifactDynamicRows",evidence.dynamicRows());
        values.put("artifactDynamicExactRows",evidence.dynamicExactRows());
        values.put("artifactUnsupportedParityRows",evidence.unsupportedRows());
        values.put("artifactFullMatchRows",evidence.fullRows());
        values.put("artifactPairedRows",evidence.pairedRows());
        values.put("artifactModeIsolationExact",evidence.modeIsolationExact());
        values.put("existingSimulationArtifactsUnchanged",evidence.hashesExact());
        values.put("legacyCrossModeReplayHashDifferentCount",evidence.legacyHashDifferent());
        values.put("correctedDownstreamGameplayBranchDivergedCount",evidence.gameplayDiverged());
        values.put("targetedTests",targetedStatus);
        values.put("targetedTestSuites",tests.suites());
        values.put("targetedTestCount",tests.tests());
        values.put("targetedTestFailures",tests.failures());
        values.put("fullBackendTestExecuted",fullSuiteExecuted);
        values.put("backendTestSuites",fullSuiteExecuted?tests.suites():"NOT_RUN");
        values.put("backendTests",fullSuiteExecuted?tests.tests():"NOT_RUN");
        values.put("backendTestFailures",fullSuiteExecuted?tests.failures():"NOT_RUN");
        values.put("backendTestErrors",fullSuiteExecuted?tests.errors():"NOT_RUN");
        values.put("backendTestSkipped",fullSuiteExecuted?tests.skipped():"NOT_RUN");
        values.put("backendBuildSuccessful",fullSuiteExecuted&&tests.successful());
        values.put("backendTestTimestamp",tests.timestamp());
        values.put("gitHead",command("git","rev-parse","HEAD").trim());
        values.put("workingTreeMarker",sha(command("git","status","--porcelain")));
        values.put("productionDefaultMode",activated?"GEOMETRIC_V2":"OFF");
        values.put("warningCodes",activated?"NONE":!targetedPassed?"TARGETED_TEST_FAILURE":!fullSuiteExecuted?"FULL_BACKEND_TEST_NOT_RUN":!tests.successful()?"BACKEND_TEST_FAILURE":"ARTIFACT_INTEGRITY_FAILURE");
        values.put("integrityErrorCount",integrity);
        values.put("verdict",activated?"MATCHUP_PRODUCTION_ACTIVATED":"BLOCKED_BY_MATCHUP_PRODUCTION_INTEGRITY");
        values.put("productionActivationAllowed",activated);
        values.put("productionActivated",activated);
        values.put("nextPhase",activated?"PHASE_13D_TEAM_COMPOSITION":"MATCHUP_PRODUCTION_INTEGRITY_REVIEW");
        ChampionMatchupRuleEngineCsv.summary(SUMMARY,values);
        List<String> log=new ArrayList<>();values.forEach((k,v)->log.add(k+"="+v));Files.write(LOG,log);
        System.out.println("Champion matchup activation finalized from artifacts: "+values.get("verdict"));
        System.out.println("Summary SHA-256: "+sha(Files.readString(SUMMARY)));
        System.out.println("Audit SHA-256: "+sha(Files.readString(LOG)));
    }

    static Map<String,Object> finalModeFields(ChampionMatchupMode mode,boolean successful){
        LinkedHashMap<String,Object>v=new LinkedHashMap<>();v.put("activationAuditEvaluatedMode","GEOMETRIC_V2");v.put("finalProductionDefaultMode",mode.name());v.put("rollbackApplied",!successful);return v;
    }
    static LinkedHashMap<String,String> readSummary()throws Exception{LinkedHashMap<String,String>v=new LinkedHashMap<>();for(String line:Files.readAllLines(SUMMARY).subList(1,Files.readAllLines(SUMMARY).size())){int comma=line.indexOf(',');if(comma>0)v.put(line.substring(0,comma),line.substring(comma+1));}return v;}
    static ArtifactEvidence validateArtifacts()throws Exception{
        var matrix=readCsv(REPORT.resolve("champion-matchup-production-matrix-regression.csv"));var dynamic=readCsv(REPORT.resolve("champion-matchup-production-dynamic-parity.csv"));var full=readCsv(REPORT.resolve("champion-matchup-production-full-match.csv"));var paired=readCsv(REPORT.resolve("champion-matchup-production-paired.csv"));var isolation=readCsv(REPORT.resolve("champion-matchup-production-mode-isolation.csv"));
        long matrixExact=matrix.rows.stream().filter(r->Boolean.parseBoolean(r.get("exact"))).count();long dynamicExact=dynamic.rows.stream().filter(r->Boolean.parseBoolean(r.get("exact"))).count();long unsupported=dynamic.rows.stream().filter(r->Boolean.parseBoolean(r.get("unsupported"))).count();boolean isolated=isolation.rows.stream().allMatch(r->Boolean.parseBoolean(r.get("exact")));boolean hashes=FROZEN_ARTIFACT_HASHES.entrySet().stream().allMatch(e->{try{return sha(Files.readString(REPORT.resolve(e.getKey()))).equals(e.getValue());}catch(Exception x){throw new RuntimeException(x);}});
        long legacy=paired.rows.stream().filter(r->Boolean.parseBoolean(r.get("downstreamBranchDivergence"))).count();long gameplay=paired.rows.stream().filter(ChampionMatchupProductionActivationFinalizer::gameplayDiverged).count();boolean valid=matrix.rows.size()==675&&matrixExact==675&&dynamic.rows.size()==1920&&dynamicExact==1920&&unsupported==0&&full.rows.size()==2400&&paired.rows.size()==1200&&isolated&&hashes;
        return new ArtifactEvidence(matrix.rows.size(),matrixExact,dynamic.rows.size(),dynamicExact,unsupported,full.rows.size(),paired.rows.size(),isolated,hashes,legacy,gameplay,valid);
    }
    static boolean gameplayDiverged(Map<String,String>r){if(Boolean.parseBoolean(r.get("winnerChanged")))return true;for(String k:List.of("durationDelta","killDelta","goldDelta","objectiveDelta","structureDelta","randomDrawDifference"))if(Double.parseDouble(r.get(k))!=0)return true;String p=r.get("positions");java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:kda|gold|level|pressure)=(-?\\d+(?:\\.\\d+)?(?:E-?\\d+)?)").matcher(p);while(m.find())if(Double.parseDouble(m.group(1))!=0)return true;return false;}
    static Csv readCsv(Path p)throws Exception{List<String>lines=Files.readAllLines(p);List<String>h=parse(lines.getFirst());List<Map<String,String>>rows=new ArrayList<>();for(String line:lines.subList(1,lines.size())){List<String>x=parse(line);LinkedHashMap<String,String>r=new LinkedHashMap<>();for(int i=0;i<h.size();i++)r.put(h.get(i),x.get(i));rows.add(Map.copyOf(r));}return new Csv(h,List.copyOf(rows));}
    static List<String>parse(String line){List<String>v=new ArrayList<>();StringBuilder b=new StringBuilder();boolean q=false;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"'){if(q&&i+1<line.length()&&line.charAt(i+1)=='"'){b.append('"');i++;}else q=!q;}else if(c==','&&!q){v.add(b.toString());b.setLength(0);}else b.append(c);}v.add(b.toString());return v;}
    static TestSummary readTests(Path dir)throws Exception{int suites=0,tests=0,failures=0,errors=0,skipped=0;String timestamp="NONE";try(var paths=Files.list(dir)){for(Path p:paths.filter(x->x.getFileName().toString().startsWith("TEST-")&&x.toString().endsWith(".xml")).toList()){var root=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(p.toFile()).getDocumentElement();suites++;tests+=Integer.parseInt(root.getAttribute("tests"));failures+=Integer.parseInt(root.getAttribute("failures"));errors+=Integer.parseInt(root.getAttribute("errors"));skipped+=Integer.parseInt(root.getAttribute("skipped"));if(!root.getAttribute("timestamp").isBlank()&&(timestamp.equals("NONE")||root.getAttribute("timestamp").compareTo(timestamp)<0))timestamp=root.getAttribute("timestamp");}}return new TestSummary(suites,tests,failures,errors,skipped,timestamp,tests>0&&failures==0&&errors==0);}
    static String command(String...args)throws Exception{Process p=new ProcessBuilder(args).redirectErrorStream(true).start();String s=new String(p.getInputStream().readAllBytes());if(p.waitFor()!=0)throw new IllegalStateException(s);return s;}
    static String sha(String s)throws Exception{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    record Csv(List<String>headers,List<Map<String,String>>rows){}
    record ArtifactEvidence(long matrixRows,long matrixExactRows,long dynamicRows,long dynamicExactRows,long unsupportedRows,long fullRows,long pairedRows,boolean modeIsolationExact,boolean hashesExact,long legacyHashDifferent,long gameplayDiverged,boolean valid){}
    record TestSummary(int suites,int tests,int failures,int errors,int skipped,String timestamp,boolean successful){}
}
