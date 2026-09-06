package com.lolfm.career;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.player.*;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CareerInternationalTournamentTest {
    static RatedCareerInternationalParticipants provider;
    static CareerInternationalParticipants.Selection selection;
    static List<CompetitionRosterSnapshot.Roster> lck;
    static final String CAREER="career_"+"b".repeat(64);
    @BeforeAll static void catalog(){
        var mapper=new ObjectMapper();var ratings=PlayerRatingCatalog.loadDefault();var champions=new ChampionCatalog(mapper);
        provider=new RatedCareerInternationalParticipants(new GlobalTeamRosterCatalog(mapper,ratings,ChampionProficiencyCatalog.loadDefault(ratings,champions),champions));
        selection=provider.overseas(CAREER,2027,"FIRST_STAND");
        lck=ratings.teamCodes().stream().sorted().map(t->provider.roster(new GlobalTeamRosterCatalog.TeamKey("LCK",t))).toList();
    }
    static CareerInternationalState registration(String competition,List<String> regions,CareerInternationalState msi){
        return CareerInternationalRegistration.create(CAREER,2027,competition,1926,selection,lck,"TEST_DOMESTIC_RESULTS",regions,msi);
    }
    record Finished(CareerInternationalState state,Map<String,CareerInternationalTournament.Outcome> results){}
    static Finished finish(CareerInternationalState state,String preferredRegion){
        Map<String,CareerInternationalTournament.Outcome> outcomes=new LinkedHashMap<>();
        for(int limit=0;limit<100;limit++){
            var plan=CareerInternationalTournament.project(state,outcomes);
            if(plan.complete())return new Finished(state.withPlan(plan),Map.copyOf(outcomes));
            var next=plan.bouts().stream().filter(b->!outcomes.containsKey(b.id())).findFirst().orElseThrow();
            String winner=next.second().startsWith(preferredRegion+":")?next.second():next.first();
            outcomes.put(next.id(),new CareerInternationalTournament.Outcome(winner,winner.equals(next.first())?next.second():next.first()));
        }throw new AssertionError("Finite tournament failed to complete");
    }
    @Test void allFourTournamentsCompleteWithCorrectFormatsCrossingAndPlacements(){
        var fst=finish(registration("FIRST_STAND",CareerInternationalRules.REFERENCE_REGIONS,null),"CBLOL");
        assertThat(fst.state.plan().bouts()).hasSize(13).allMatch(b->b.format().equals("BO5"));
        assertThat(fst.state.plan().champion()).startsWith("CBLOL:");
        var msi=finish(registration("MSI",fst.state.plan().regionalPerformance(),null),"CBLOL");
        assertThat(msi.state.entries().stream().filter(e->e.phase().equals("MAIN"))).hasSize(7);
        assertThat(msi.state.entries().stream().filter(e->e.phase().equals("PLAY_IN"))).hasSize(4);
        assertThat(msi.state.plan().bouts()).hasSize(20).allMatch(b->b.format().equals("BO5"));
        for(int i=0;i<2;i++){
            String matchId="MSI_L2_"+i;
            var bout=msi.state.plan().bouts().stream().filter(b->b.id().equals(matchId)).findFirst().orElseThrow();
            assertThat(bout.second()).isEqualTo(msi.results.get("MSI_U2_"+(1-i)).loser());
        }
        var ewc=finish(registration("EWC_LOL",msi.state.plan().regionalPerformance(),msi.state),"LEC");
        assertThat(ewc.state.plan().bouts()).hasSize(28);
        assertThat(ewc.state.entries()).anyMatch(e->e.qualification().equals("FIRST_CYCLE_MISSING_TITLE_SLOT_LCK_REFERENCE")&&e.regionalSeed()==4);
        assertThat(ewc.state.entries()).anyMatch(e->e.region().equals("LCP")&&e.regionalSeed()==2&&e.pool()==1);
        assertThat(ewc.state.plan().bouts().stream().filter(b->b.format().equals("BO1"))).hasSize(12);
        assertThat(ewc.state.plan().bouts().stream().filter(b->b.format().equals("BO5"))).hasSize(1);
        var worlds=finish(registration("WORLDS",msi.state.plan().regionalPerformance(),msi.state),"LCK");
        assertThat(worlds.state.entries().stream().filter(e->e.region().equals("CBLOL"))).hasSize(3);
        assertThat(worlds.state.plan().bouts()).hasSize(46);
        assertThat(worlds.state.plan().placements()).hasSize(19);
        assertThat(worlds.state.plan().placements().values().stream().filter(p->p<=8)).hasSize(8);
        assertThat(CareerInternationalTournament.project(worlds.state,worlds.results)).isEqualTo(worlds.state.plan());
        assertSwiss(worlds);assertEwcGroupsAndKnockout(ewc);
    }
    @Test void correctedSelectionSeparatesPlayInSeedsAndUsesActualKnockoutDraw() {
        var fst = finish(registration("FIRST_STAND",CareerInternationalRules.REFERENCE_REGIONS,null),"LCK").state;
        var msi = finish(registration("MSI",fst.plan().regionalPerformance(),null),"LCK");
        var seeds = new HashMap<String,Integer>();
        msi.state.entries().stream().filter(e -> e.phase().equals("PLAY_IN")).forEach(e -> {
            assertThat(e.regionalSeed()).isEqualTo(2); seeds.put(e.team(), e.playInSeed());
        });
        var ordered = seeds.keySet().stream().sorted(Comparator.comparingInt(seeds::get)).toList();
        assertThat(msi.state.plan().draws().getFirst().teams()).containsExactly(ordered.get(0),ordered.get(3),ordered.get(1),ordered.get(2));
        for (var b : msi.state.plan().bouts().stream().filter(b -> b.id().startsWith("MSI_PI_O")).toList())
            assertThat(b.selectionOwner()).isEqualTo(seeds.get(b.first()) < seeds.get(b.second()) ? b.first() : b.second());
        var worlds = finish(registration("WORLDS",msi.state.plan().regionalPerformance(),msi.state),"LCK");
        for (var finished : List.of(msi, worlds)) {
            var state = finished.state;
            var byTeam = new HashMap<String,CareerInternationalState.Entry>();state.entries().forEach(e -> byTeam.put(e.team(),e));
            var losses = new HashMap<String,Integer>();
            state.plan().bouts().stream().filter(b -> b.stage().startsWith("SWISS")).forEach(b -> losses.merge(finished.results.get(b.id()).loser(),1,Integer::sum));
            var draw = state.plan().draws().stream().filter(d -> d.scope().equals("KNOCKOUT")).findFirst();
            int equalRecord = 0;
            for (var b : state.plan().bouts()) {
                String local = b.id().substring(state.competitionId().length()+1);
                if (local.equals("PI_F")) {
                    assertThat(b.selectionOwner()).isEqualTo(finished.results.get(state.competitionId()+"_PI_U").winner());
                    assertThat(b.sidePolicy()).isEqualTo(CareerInternationalRules.RODS);
                } else if (List.of("PI_U","PI_D","PI_L").contains(local) || state.competitionId().equals("WORLDS") && local.startsWith("PI_O")) {
                    String scope = local.startsWith("PI_O") ? local : local+":ROFS";
                    assertThat(b.selectionOwner()).isEqualTo(List.of(b.first(), b.second()).stream().min(Comparator.comparing(t -> CareerInternationalRules.hash(
                            state.drawSeed()+"|"+state.careerId()+"|"+state.year()+"|"+state.competitionId()+"|"+CareerInternationalRules.POLICY+"|"+scope+"|"+t))).orElseThrow());
                } else if (b.stage().startsWith("SWISS") && byTeam.get(b.first()).regionalSeed() != byTeam.get(b.second()).regionalSeed()) {
                    assertThat(b.selectionOwner()).isEqualTo(byTeam.get(b.first()).regionalSeed() < byTeam.get(b.second()).regionalSeed() ? b.first() : b.second());
                } else if (b.stage().equals("QUARTERFINAL") && state.competitionId().equals("WORLDS")) {
                    int a = losses.getOrDefault(b.first(),0), c = losses.getOrDefault(b.second(),0);
                    if (a == c) equalRecord++;
                    assertThat(b.selectionOwner()).isEqualTo(a == c
                            ? draw.orElseThrow().teams().indexOf(b.first()) < draw.orElseThrow().teams().indexOf(b.second()) ? b.first() : b.second()
                            : a < c ? b.first() : b.second());
                }
            }
            if (state.competitionId().equals("WORLDS")) assertThat(equalRecord).isPositive();
        }
    }
    @Test void legacyProjectionAndBoundSelectionRemainStableDuringUpgrade() throws Exception {
        var current = registration("MSI",CareerInternationalRules.REFERENCE_REGIONS,null);
        var legacy = new CareerInternationalState(current.careerId(),current.year(),current.competitionId(),CareerInternationalRules.VERSION,
                CareerInternationalRules.RESOURCE_HASH,CareerInternationalRules.POLICY,current.selectionPolicy(),current.inputEvidence(),
                current.drawSeed(),current.entries().stream().map(e -> new CareerInternationalState.Entry(e.team(),e.region(),e.regionalSeed(),e.pool(),e.phase(),e.qualification())).toList(),
                current.rosters(),current.regionOrder(),null);
        legacy = legacy.withPlan(CareerInternationalTournament.project(legacy,Map.of()));
        var mapper = new ObjectMapper().findAndRegisterModules();
        assertThat(mapper.writeValueAsString(legacy)).doesNotContain("playInSeed", "selectionUpgrade");
        var restored = mapper.readValue(mapper.writeValueAsString(legacy),CareerInternationalState.class);
        assertThat(CareerInternationalTournament.project(restored,Map.of())).isEqualTo(legacy.plan());
        var first = legacy.plan().bouts().getFirst();
        var upgraded = new CareerInternationalState(current.careerId(),current.year(),current.competitionId(),current.ruleVersion(),
                current.ruleResourceHash(),current.policyVersion(),current.selectionPolicy(),current.inputEvidence(),current.drawSeed(),
                current.entries(),current.rosters(),current.regionOrder(),legacy.plan(),new CareerInternationalState.SelectionUpgrade(
                    "a".repeat(64),List.of(first),legacy.plan().draws().getFirst().teams()));
        var plan = CareerInternationalTournament.project(upgraded,Map.of());
        assertThat(plan.bouts().getFirst()).isEqualTo(first);
        assertThat(plan.draws()).isEqualTo(legacy.plan().draws());
        assertThat(finish(upgraded,"LCK").state.plan().complete()).isTrue();
    }
    @Test void futureEwcUsesActualForeignChampionAndExplicitDuplicateOrUnavailableSuccession() {
        var prior=finish(registration("EWC_LOL",CareerInternationalRules.REFERENCE_REGIONS,null),"LEC").state;
        String champion=prior.plan().champion();assertThat(champion).startsWith("LEC:");
        var duplicate=CareerInternationalRegistration.create(CAREER,2028,"EWC_LOL",1926,selection,lck,"PREVIOUS_EWC",CareerInternationalRules.REFERENCE_REGIONS,null,prior);
        assertThat(duplicate.entries()).anyMatch(e->e.qualification().equals("PREVIOUS_EWC_CHAMPION_DUPLICATE_REGIONAL_SUCCESSION")&&e.region().equals("LEC"));
        var rankings=new LinkedHashMap<>(selection.rankings());var lec=new ArrayList<>(rankings.get("LEC"));
        var title=lec.stream().filter(r->CompetitionRosterSnapshot.token(r.team()).equals(champion)).findFirst().orElseThrow();
        lec.remove(title);lec.add(4,title);rankings.put("LEC",lec);
        var newSelection=new CareerInternationalParticipants.Selection(selection.policy(),"NEW_YEAR_PROVIDER_ORDER",rankings);
        var direct=CareerInternationalRegistration.create(CAREER,2028,"EWC_LOL",1926,newSelection,lck,"PREVIOUS_EWC",CareerInternationalRules.REFERENCE_REGIONS,null,prior);
        assertThat(direct.entries()).anyMatch(e->e.team().equals(champion)&&e.qualification().equals("PREVIOUS_EWC_CHAMPION_CURRENT_ROSTER"));
        assertThat(direct.rosters().roster(champion)).isEqualTo(title);
        lec.remove(title);rankings.put("LEC",lec);
        var unavailable=CareerInternationalRegistration.create(CAREER,2028,"EWC_LOL",1926,new CareerInternationalParticipants.Selection(selection.policy(),"UNAVAILABLE_CHAMPION",rankings),lck,"PREVIOUS_EWC",CareerInternationalRules.REFERENCE_REGIONS,null,prior);
        assertThat(unavailable.entries()).noneMatch(e->e.team().equals(champion));
        assertThat(unavailable.entries()).anyMatch(e->e.qualification().equals("PREVIOUS_EWC_CHAMPION_UNAVAILABLE_SUCCESSION")&&e.region().equals("LEC"));
        assertThat(finish(duplicate,"LCK").state.plan().complete()).isTrue();
        assertThat(finish(direct,"LCK").state.plan().complete()).isTrue();
        assertThat(finish(unavailable,"LCK").state.plan().complete()).isTrue();
    }
    @Test void frozenInputsRoundTripAndAssembleFreshMutablePlayers(){
        var state=registration("FIRST_STAND",CareerInternationalRules.REFERENCE_REGIONS,null);
        var frozen=state.rosters();var restored=CompetitionRosterSnapshot.decode(frozen.encoded());
        assertThat(restored).isEqualTo(frozen);assertThat(restored.identity()).isEqualTo(frozen.identity());
        var team=state.entries().getFirst().team();var first=frozen.assemble(team);var second=restored.assemble(team);
        assertThat(first).isNotSameAs(second);
        for(int i=0;i<5;i++){assertThat(first.getPlayers().get(i)).isNotSameAs(second.getPlayers().get(i));assertThat(first.getPlayers().get(i).getRatings().asMap()).isEqualTo(second.getPlayers().get(i).getRatings().asMap());}
        assertThat(provider.overseas(CAREER,2027,"FIRST_STAND")).isEqualTo(selection);
        selection.rankings().values().forEach(r->assertThat(r).isSortedAccordingTo(Comparator.comparingInt(CompetitionRosterSnapshot.Roster::strength).reversed().thenComparing(p->CompetitionRosterSnapshot.token(p.team()))));
    }
    @Test void lckMsiChampionMustActuallyMakeDomesticPlayoffsAndCanUseFourthSlot(){
        var fst=finish(registration("FIRST_STAND",CareerInternationalRules.REFERENCE_REGIONS,null),"LCK");
        var msi=finish(registration("MSI",fst.state.plan().regionalPerformance(),null),"LCK").state;
        var champion=msi.rosters().roster(msi.plan().champion());
        var finalOrder=new ArrayList<>(lck);finalOrder.remove(champion);finalOrder.add(4,champion);
        var qualified=CareerInternationalRegistration.create(CAREER,2027,"WORLDS",1926,selection,finalOrder,"SEALED_LCK_ORDER",msi.plan().regionalPerformance(),msi);
        assertThat(qualified.entries()).anyMatch(e->e.team().equals(msi.plan().champion())&&e.regionalSeed()==4&&e.qualification().equals("MSI_CHAMPION_HOME_PLAYOFF_ELIGIBLE"));
        finalOrder.remove(champion);finalOrder.add(6,champion);
        var excluded=CareerInternationalRegistration.create(CAREER,2027,"WORLDS",1926,selection,finalOrder,"SEALED_LCK_ORDER",msi.plan().regionalPerformance(),msi);
        assertThat(excluded.entries()).noneMatch(e->e.team().equals(msi.plan().champion()));
        assertThat(excluded.entries().stream().filter(e->e.region().equals("LCK"))).hasSize(4);
        assertThat(excluded.entries()).hasSize(19);
    }
    @Test void rejectsForgedOutcomeAndSeparatesCareerDrawScope(){
        var state=registration("FIRST_STAND",CareerInternationalRules.REFERENCE_REGIONS,null);var first=state.plan().bouts().getFirst();
        assertThatThrownBy(()->CareerInternationalTournament.project(state,Map.of(first.id(),new CareerInternationalTournament.Outcome(first.first(),"LCK:UNKNOWN")))).hasMessageContaining("PARTICIPANT_MISMATCH");
        var other=CareerInternationalRegistration.create("career_"+"c".repeat(64),2027,"FIRST_STAND",1926,selection,lck,"TEST",CareerInternationalRules.REFERENCE_REGIONS,null);
        assertThat(other.drawSeed()).isNotEqualTo(state.drawSeed());assertThat(other.rosters()).isEqualTo(state.rosters());
    }
    @Test void exhaustiveMatchingBacktracksBeforeDeclaringDrawImpossible() {
        Set<Set<String>> edges = Set.of(Set.of("A","B"), Set.of("A","C"), Set.of("B","D"));
        assertThat(CareerInternationalTournament.matchPairs(List.of("A","B","C","D"), (a,b)->edges.contains(Set.of(a,b))))
                .containsExactly("A","C","B","D");
        assertThat(CareerInternationalTournament.matchPairs(List.of("A","B","C","D"), (a,b)->false)).isNull();
        assertThatThrownBy(()->CareerInternationalTournament.matchPairs(List.of("A","B","C"), (a,b)->true)).hasMessage("ODD_SWISS_RECORD_GROUP");
    }
    static void assertSwiss(Finished finished){
        Map<String,Integer>wins=new HashMap<>(),losses=new HashMap<>();Set<Set<String>>seen=new HashSet<>();
        for(var bout:finished.state.plan().bouts().stream().filter(b->b.stage().startsWith("SWISS")).toList()){
            assertThat(wins.getOrDefault(bout.first(),0)).isEqualTo(wins.getOrDefault(bout.second(),0));
            assertThat(losses.getOrDefault(bout.first(),0)).isEqualTo(losses.getOrDefault(bout.second(),0));
            assertThat(seen.add(Set.of(bout.first(),bout.second()))).isTrue();
            boolean deciding=wins.getOrDefault(bout.first(),0)==2||losses.getOrDefault(bout.first(),0)==2;
            assertThat(bout.format()).isEqualTo(deciding?"BO3":"BO1");
            if(bout.stage().equals("SWISS_R1"))assertThat(finished.state.rosters().roster(bout.first()).team().leagueCode()).isNotEqualTo(finished.state.rosters().roster(bout.second()).team().leagueCode());
            var result=finished.results.get(bout.id());wins.merge(result.winner(),1,Integer::sum);losses.merge(result.loser(),1,Integer::sum);
        }
        assertThat(wins.values().stream().filter(v->v==3)).hasSize(8);assertThat(losses.values().stream().filter(v->v==3)).hasSize(8);
    }
    static void assertEwcGroupsAndKnockout(Finished ewc){
        var groupDraw=ewc.state.plan().draws().stream().filter(d->d.scope().equals("GROUPS")).findFirst().orElseThrow().teams();
        for(int g=0;g<4;g++)assertThat(groupDraw.subList(g*4,g*4+4).stream().map(t->ewc.state.rosters().roster(t).team().leagueCode()).distinct()).hasSize(4);
        Map<String,Integer>half=new HashMap<>();
        ewc.state.plan().bouts().stream().filter(b->b.stage().equals("QUARTERFINAL")).forEach(b->{int h=Integer.parseInt(b.id().substring(b.id().length()-1))/2;half.put(b.first(),h);half.put(b.second(),h);});
        for(int g=0;g<4;g++)assertThat(half.get(ewc.results.get("EWC_LOL_G"+g+"_U").winner())).isNotEqualTo(half.get(ewc.results.get("EWC_LOL_G"+g+"_F").winner()));
    }
}
