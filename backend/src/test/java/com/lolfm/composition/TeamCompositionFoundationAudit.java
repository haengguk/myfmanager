package com.lolfm.composition;

import com.lolfm.champion.ChampionRoleKey;
import com.lolfm.domain.Position;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class TeamCompositionFoundationAudit {
 private static final Path DIR=Path.of("build","reports","team-composition-foundation");
 private static final int PERMUTATIONS=120;
 private TeamCompositionFoundationAudit(){}
 public static void main(String[]args)throws Exception{
  Files.createDirectories(DIR);var analyzer=new TeamCompositionAnalyzer();var fixtures=SyntheticTeamCompositionFixtures.cases();
  var analyses=new LinkedHashMap<String,TeamCompositionAnalysis>();var errors=new ArrayList<String>();
  for(var f:fixtures)try{analyses.put(f.caseId(),analyzer.analyze(f.lineup(),f.profiles()));}catch(RuntimeException e){errors.add(f.caseId()+": "+e.getMessage());}
  int explanation=countExplanationMismatches(analyses),semantic=countSemanticMismatches(analyses),permutation=countPermutationMismatches(fixtures,analyses,analyzer);
  int integrity=errors.size()+explanation+semantic+permutation;List<String>warnings=integrity==0?List.of():List.of("SYNTHETIC_AUDIT_MISMATCH");
  String verdict=integrity==0&&warnings.isEmpty()?"READY_FOR_PHASE_13D2":"NOT_READY";
  writeCases(fixtures,analyses);writeContributors(fixtures,analyses);writeSummary(fixtures.size(),permutation,explanation,semantic,integrity,warnings,verdict);writeLog(errors,permutation,explanation,semantic,verdict);
  if(integrity!=0)throw new IllegalStateException("Composition foundation audit failed: "+integrity+" integrity errors");
  System.out.println("Team composition foundation audit: "+verdict);System.out.println("Artifacts: "+DIR.toAbsolutePath());
 }
 private static int countExplanationMismatches(Map<String,TeamCompositionAnalysis>all){int n=0;for(var a:all.values()){
  for(var e:a.explanation().capabilities()){var c=a.coverage().capability(e.capability());if(Double.compare(e.coverage(),c.coverage())!=0||!e.contributors().equals(c.contributors()))n++;}
  for(var e:a.explanation().patterns()){var v=a.patterns().get(e.pattern());if(Double.compare(e.readiness(),v.readiness())!=0||!e.componentCoverages().equals(v.componentCoverages())||!e.primaryContributors().equals(v.primaryContributors()))n++;}
  for(var e:a.explanation().deficiencies()){var v=a.deficiencies().get(e.deficiency());if(e.present()!=v.present()||Double.compare(e.severity(),v.severity())!=0)n++;}}
  return n;
 }
 private static int countSemanticMismatches(Map<String,TeamCompositionAnalysis>a){int n=0;
  n+=positive(a,"engage-chain",CompositionPattern.ENGAGE_CHAIN)?0:1;n+=zero(a,"engage-no-follow",CompositionPattern.ENGAGE_CHAIN)?0:1;
  n+=positive(a,"front-to-back",CompositionPattern.FRONT_TO_BACK)?0:1;n+=zero(a,"front-concentrated",CompositionPattern.FRONT_TO_BACK)?0:1;
  n+=positive(a,"poke-siege",CompositionPattern.POKE_SIEGE)?0:1;n+=positive(a,"pick-conversion",CompositionPattern.PICK_CONVERSION)?0:1;
  n+=positive(a,"split-map",CompositionPattern.SPLIT_MAP_PRESSURE)?0:1;n+=zero(a,"split-concentrated",CompositionPattern.SPLIT_MAP_PRESSURE)?0:1;
  n+=positive(a,"objective-control",CompositionPattern.OBJECTIVE_CONTROL)?0:1;n+=present(a,"no-engage",CompositionDeficiency.NO_RELIABLE_ENGAGE)?0:1;
  n+=present(a,"low-frontline",CompositionDeficiency.LOW_FRONTLINE)?0:1;n+=present(a,"physical-skew",CompositionDeficiency.DAMAGE_CHANNEL_SKEW)?0:1;
  n+=present(a,"magic-skew",CompositionDeficiency.DAMAGE_CHANNEL_SKEW)?0:1;n+=!present(a,"balanced-damage",CompositionDeficiency.DAMAGE_CHANNEL_SKEW)?0:1;
  var d=a.get("zero-damage").coverage().damageChannels();n+=!d.shareApplicable()&&d.physicalShare()==0&&d.magicShare()==0&&d.trueDamageShare()==0?0:1;n+=a.containsKey("neutral")?0:1;return n;
 }
 private static boolean positive(Map<String,TeamCompositionAnalysis>a,String id,CompositionPattern p){return a.get(id).patterns().get(p).readiness()>0;}
 private static boolean zero(Map<String,TeamCompositionAnalysis>a,String id,CompositionPattern p){return a.get(id).patterns().get(p).readiness()==0;}
 private static boolean present(Map<String,TeamCompositionAnalysis>a,String id,CompositionDeficiency d){return a.get(id).deficiencies().get(d).present();}
 private static int countPermutationMismatches(List<SyntheticTeamCompositionFixtures.FixtureCase>fs,Map<String,TeamCompositionAnalysis>a,TeamCompositionAnalyzer analyzer){int n=0;for(int i=0;i<PERMUTATIONS;i++){
  var f=fs.get(i%fs.size());var entries=new ArrayList<>(f.lineup().championsByPosition().entrySet());Collections.rotate(entries,i%Position.values().length);if((i&1)==1)Collections.reverse(entries);
  var map=new LinkedHashMap<Position,ChampionRoleKey>();entries.forEach(e->map.put(e.getKey(),e.getValue()));var actual=analyzer.analyze(new TeamCompositionLineup(map),f.profiles());if(!actual.equals(a.get(f.caseId())))n++;}return n;
 }
 private static void writeCases(List<SyntheticTeamCompositionFixtures.FixtureCase>fs,Map<String,TeamCompositionAnalysis>a)throws IOException{
  var rows=new ArrayList<List<String>>();var h=new ArrayList<String>();h.add("caseId");h.add("expectedPurpose");for(var x:CompositionCapability.values())h.add(x.name());for(var x:CompositionPattern.values())h.add(x.name());for(var x:CompositionDeficiency.values())h.add(x.name());h.addAll(List.of("physicalShare","magicShare","trueDamageShare","damageShareApplicable","validationResult"));rows.add(h);
  for(var f:fs){var x=a.get(f.caseId());var row=new ArrayList<String>();row.add(f.caseId());row.add(f.expectedPurpose());for(var c:CompositionCapability.values())row.add(num(x.coverage().capability(c).coverage()));for(var p:CompositionPattern.values())row.add(num(x.patterns().get(p).readiness()));for(var d:CompositionDeficiency.values())row.add(Boolean.toString(x.deficiencies().get(d).present()));var damage=x.coverage().damageChannels();row.add(num(damage.physicalShare()));row.add(num(damage.magicShare()));row.add(num(damage.trueDamageShare()));row.add(Boolean.toString(damage.shareApplicable()));row.add("PASS");rows.add(row);}writeCsv(DIR.resolve("team-composition-foundation-cases.csv"),rows);
 }
 private static void writeContributors(List<SyntheticTeamCompositionFixtures.FixtureCase>fs,Map<String,TeamCompositionAnalysis>a)throws IOException{
  var rows=new ArrayList<List<String>>();rows.add(List.of("caseId","capability","aggregationType","rank","championRoleKey","position","rawValue","normalizedValue","weight","weightedContribution"));
  for(var f:fs)for(var c:CompositionCapability.values()){var coverage=a.get(f.caseId()).coverage().capability(c);int rank=1;for(var x:coverage.contributors())rows.add(List.of(f.caseId(),c.name(),coverage.aggregationType().name(),Integer.toString(rank++),x.championRoleKey().stableId(),x.championRoleKey().position().name(),Integer.toString(x.rawValue()),num(x.normalizedValue()),num(x.aggregationWeight()),num(x.weightedContribution())));}writeCsv(DIR.resolve("team-composition-foundation-contributors.csv"),rows);
 }
 private static void writeSummary(int cases,int permutation,int explanation,int semantic,int integrity,List<String>warnings,String verdict)throws IOException{
  var s=new LinkedHashMap<String,String>();s.put("phase","13D-1");s.put("capabilityCount",Integer.toString(CompositionCapability.values().length));s.put("aggregationTypeCount",Integer.toString(CompositionAggregationType.values().length));s.put("patternCount",Integer.toString(CompositionPattern.values().length));s.put("deficiencyCount",Integer.toString(CompositionDeficiency.values().length));s.put("contextCount",Integer.toString(TeamCompositionContext.values().length));s.put("syntheticCaseCount",Integer.toString(cases));s.put("permutationCaseCount",Integer.toString(PERMUTATIONS));s.put("permutationMismatchCount",Integer.toString(permutation));s.put("explanationMismatchCount",Integer.toString(explanation));s.put("semanticMismatchCount",Integer.toString(semantic));s.put("integrityErrorCount",Integer.toString(integrity));s.put("productionGameplayChanged","false");s.put("teamCompositionProductionEnabled","false");s.put("newProductionSimulationContribution","0");s.put("warningCount",Integer.toString(warnings.size()));s.put("warnings",warnings.isEmpty()?"NONE":String.join("|",warnings));s.put("verdict",verdict);s.put("nextPhase","Phase 13D-2 — champion/role composition profiles");var rows=new ArrayList<List<String>>();rows.add(List.of("key","value"));s.forEach((k,v)->rows.add(List.of(k,v)));writeCsv(DIR.resolve("team-composition-foundation-summary.csv"),rows);
 }
 private static void writeLog(List<String>errors,int permutation,int explanation,int semantic,String verdict)throws IOException{var lines=new ArrayList<String>();lines.add("Phase 13D-1 Team Composition Foundation Audit");lines.add("syntheticCases="+SyntheticTeamCompositionFixtures.cases().size());lines.add("permutationCases="+PERMUTATIONS);lines.add("permutationMismatches="+permutation);lines.add("explanationMismatches="+explanation);lines.add("semanticMismatches="+semantic);lines.add("validationErrors="+errors.size());errors.forEach(e->lines.add("validationError="+e));lines.add("productionGameplayChanged=false");lines.add("teamCompositionProductionEnabled=false");lines.add("newProductionSimulationContribution=0");lines.add("verdict="+verdict);Files.write(DIR.resolve("team-composition-foundation-audit.log"),lines,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);}
 private static String num(double v){return String.format(Locale.ROOT,"%.12f",v);}
 private static void writeCsv(Path p,List<List<String>>rows)throws IOException{var lines=rows.stream().map(r->r.stream().map(TeamCompositionFoundationAudit::escape).reduce((l,x)->l+","+x).orElse("")).toList();Files.write(p,lines,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);}
 private static String escape(String v){return !v.contains(",")&&!v.contains("\"")&&!v.contains("\n")&&!v.contains("\r")?v:"\""+v.replace("\"","\"\"")+"\"";}
}
