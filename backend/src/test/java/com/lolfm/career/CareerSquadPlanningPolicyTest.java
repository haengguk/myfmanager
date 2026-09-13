package com.lolfm.career;

import java.time.*;
import java.util.*;
import com.lolfm.domain.Position;
import com.lolfm.player.ExpandedPlayerCatalog.Definition;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.lolfm.career.CareerMarketState.*;

class CareerSquadPlanningPolicyTest {
    static final LocalDate MONDAY=LocalDate.of(2027,1,11);
    @BeforeAll static void catalog(){CareerMarketEngineTest.catalog();}
    static CareerMarketEngine setup(){var m=CareerMarketEngineTest.engine("LCK:T1");m.clEnabled=true;m.planner.repair(CareerMarketEngineTest.DATE);return m;}
    static String top(CareerMarketEngine m,String squad){return m.members.values().stream().filter(v->"LCK:BRO".equals(v.ownerTeam())&&squad.equals(v.squad())&&m.player(v.playerId()).position()==Position.TOP).map(CareerRosterStore.Membership::playerId).findFirst().orElseThrow();}
    static void rating(CareerMarketEngine m,String id,int value,int pa){
        var d=m.player(id);var old=d.gameplay();var ratings=new EnumMap<>(old.ratings());ratings.replaceAll((k,v)->value);
        var details=CareerRosterStore.read(d.detailsJson(),com.fasterxml.jackson.databind.node.ObjectNode.class);details.withObject("abilityMetadata").put("potentialAbility",pa);
        var definitions=new TreeMap<>(m.directory.players());definitions.put(id,new Definition(id,d.nickname(),d.position(),new CompetitionRosterSnapshot.Starter(id,d.nickname(),d.position(),ratings,old.proficiencies()),d.provisional(),d.initialOrganizationId(),d.initialOwnerTeam(),d.initialSquad(),d.eligibilityReason(),details.toString()));
        m.directory=new CareerRosterStore.Directory(definitions,m.directory.organizations());
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"175,CAREER_SQUAD_PLANNING_V1","190,CAREER_SQUAD_PLANNING_V2","190,CAREER_SQUAD_PLANNING_V3"})
    void promotionAndLegalClSwapUseCurrentAbilityAndPreservePromises(int pa,String savedVersion){
        var m=setup();String first=top(m,"FIRST_TEAM"),cl=top(m,"DEVELOPMENT");rating(m,first,12,200);rating(m,cl,16,pa);
        assertThat(CareerDevelopmentPolicy.metadata(m.player(cl)).potential()).isEqualTo(pa);
        var promises=m.management().promises();var user=m.lineups.get(m.managed);m.planner.review(MONDAY);
        assertThat(m.lineups.get("LCK:BRO")).contains(cl).doesNotContain(first);assertThat(m.clLineups.get("LCK:BRO")).contains(first).doesNotContain(cl);
        assertThat(m.management().promises()).isEqualTo(promises);assertThat(m.lineups.get(m.managed)).isEqualTo(user);
        var once=m.state();m.planner.review(MONDAY);assertThat(m.state()).isEqualTo(once);
        var restored=new CareerMarketEngine(m.career,m.managed,m.directory,m.roster(),CareerRosterStore.read(CareerRosterStore.write(once).replace(CareerSquadPlanningPolicy.VERSION,savedVersion),CareerMarketState.class));
        restored.clEnabled=true;restored.clLineups.putAll(m.clLineups);rating(restored,first,20,200);restored.planner.review(MONDAY.plusWeeks(1));
        assertThat(restored.lineups.get("LCK:BRO")).contains(cl);assertThat(restored.state().squadPlanning().cooldowns()).containsEntry("LCK:BRO|TOP|FIRST_TEAM",MONDAY.plusDays(28));
    }
    @Test void smallDifferenceRetainsAndUnavailableClReplacementDefers(){
        var small=setup();String first=top(small,"FIRST_TEAM"),cl=top(small,"DEVELOPMENT");rating(small,first,15,190);rating(small,cl,16,190);
        small.planner.review(MONDAY);assertThat(small.lineups.get("LCK:BRO")).contains(first);
        var blocked=setup();first=top(blocked,"FIRST_TEAM");cl=top(blocked,"DEVELOPMENT");rating(blocked,first,12,190);rating(blocked,cl,18,190);
        blocked.squadRestrictions.add(new CareerSquadPlanner.Restriction(first,"FIRST_TEAM",MONDAY.minusDays(1),true));blocked.planner.review(MONDAY);
        assertThat(blocked.lineups.get("LCK:BRO")).contains(first);assertThat(blocked.state().squadPlanning().decisions()).anyMatch(d->d.team().equals("LCK:BRO")&&d.action().equals("PROMOTE")&&d.status().equals("DEFERRED"));
    }
    @Test void dueDevelopmentRenewalUsesExistingConsentAndCombinedWeeklyLimit(){
        var m=setup();String id=top(m,"DEVELOPMENT");var c=m.active(id,MONDAY);var end=MONDAY.plusDays(40);
        m.contracts.put(c.contractId(),new Contract(c.contractId(),c.careerId(),id,c.team(),c.organizationId(),c.signedDate(),new Terms(c.terms().startDate(),end,c.terms().annualSalary(),0,Role.DEVELOPMENT),c.status(),c.revision(),c.policyVersion(),c.origin(),c.terminationPolicy(),c.endedDate(),c.paidThrough()));
        // Isolate renewal from lawful outside upgrades: other TOP players are bound in ongoing series.
        m.members.values().stream().filter(v->!"LCK:BRO".equals(v.ownerTeam())&&m.player(v.playerId()).position()==Position.TOP)
            .forEach(v->m.squadRestrictions.add(new CareerSquadPlanner.Restriction(v.playerId(),v.squad(),MONDAY,true)));
        m.planner.review(MONDAY);assertThat(m.offers.values()).as("BRO choices: %s",m.state().squadPlanning().decisions().stream().filter(d->d.team().equals("LCK:BRO")).toList()).anyMatch(o->o.playerId().equals(id)&&o.team().equals("LCK:BRO")&&o.terms().role()==Role.DEVELOPMENT&&o.terms().startDate().equals(end.plusDays(1)));
        assertThat(m.active(id,MONDAY).terms().endDate()).isEqualTo(end);assertThat(m.scheduled(id)).isNull();
        for(String team:m.accounts.keySet())assertThat(m.offers.values().stream().filter(o->o.team().equals(team)&&o.submittedDate().equals(MONDAY)).count()+m.tradeEngine.trades.values().stream().filter(t->t.terms().buyer().equals(team)&&t.submittedDate().equals(MONDAY)).count()).isLessThanOrEqualTo(CareerSquadPlanningPolicy.NEW_PROPOSALS_PER_CLUB);
        assertThat(m.offers.values()).noneMatch(o->o.team().equals(m.managed));
        m.advance(MONDAY.plusDays(CareerMarketPolicy.DECISION_DAYS));
        assertThat(m.scheduled(id)).isNotNull();assertThat(m.scheduled(id).terms().role()).isEqualTo(Role.DEVELOPMENT);assertThat(m.members.get(id).ownerTeam()).isEqualTo("LCK:BRO");
        System.out.println("AI_CL_RENEWAL player="+id+" preparedEnd="+end+" before="+m.active(id,MONDAY)+" agreed="+m.scheduled(id));
    }
    @ParameterizedTest @ValueSource(ints={14,20})
    void recruitmentRoleMatchesFirstTeamUpgradeOrDevelopmentUse(int value){
        var m=setup();String first=top(m,"FIRST_TEAM"),cl=top(m,"DEVELOPMENT");rating(m,first,15,200);rating(m,cl,10,200);
        String candidate=m.directory.players().keySet().stream().filter(id->m.player(id).position()==Position.TOP&&!"LCK:BRO".equals(m.members.get(id).ownerTeam())).sorted().findFirst().orElseThrow();
        // Only one available outside TOP; names and money do not define the intended role.
        for(String id:new ArrayList<>(m.directory.players().keySet()))if(m.player(id).position()==Position.TOP&&!id.equals(first)&&!id.equals(cl)){
            if(id.equals(candidate)){m.contracts.values().removeIf(c->c.playerId().equals(id));m.freeAgents.add(id);m.members.put(id,new CareerRosterStore.Membership(id,null,"FREE_AGENT","FREE_AGENT",null));rating(m,id,value,200);}
            else m.squadRestrictions.add(new CareerSquadPlanner.Restriction(id,"FIRST_TEAM",MONDAY,true));
        }
        m.planner.review(MONDAY);
        assertThat(m.offers.values()).anySatisfy(o->{assertThat(o.team()).isEqualTo("LCK:BRO");assertThat(o.playerId()).isEqualTo(candidate);assertThat(o.terms().role()).isEqualTo(value==20?Role.STARTER:Role.DEVELOPMENT);});
    }
    @Test void unaffordableFirstTeamUpgradeFallsThroughToAffordableClCandidate(){
        var m=setup();String team="LCK:BRO",first=top(m,"FIRST_TEAM"),cl=top(m,"DEVELOPMENT");rating(m,first,15,200);rating(m,cl,10,200);
        var candidates=m.directory.players().keySet().stream().filter(id->m.player(id).position()==Position.TOP&&!team.equals(m.members.get(id).ownerTeam())).sorted().limit(2).toList();
        for(String id:m.directory.players().keySet())if(m.player(id).position()==Position.TOP&&!id.equals(first)&&!id.equals(cl)){
            if(candidates.contains(id)){m.contracts.values().removeIf(c->c.playerId().equals(id));m.freeAgents.add(id);m.members.put(id,new CareerRosterStore.Membership(id,null,"FREE_AGENT","FREE_AGENT",null));rating(m,id,id.equals(candidates.getFirst())?20:14,200);}
            else m.squadRestrictions.add(new CareerSquadPlanner.Restriction(id,"FIRST_TEAM",MONDAY,true));
        }
        var account=m.accounts.get(team);m.accounts.put(team,new Account(team,m.peakSalary(team)+230_000,account.cash(),account.rosterLimit()));m.planner.review(MONDAY);
        assertThat(m.offers.values().stream().filter(o->o.team().equals(team)&&m.player(o.playerId()).position()==Position.TOP)).singleElement().satisfies(o->{assertThat(o.playerId()).isEqualTo(candidates.getLast());assertThat(o.terms().role()).isEqualTo(Role.DEVELOPMENT);});
    }
    @ParameterizedTest @ValueSource(ints={12,13,18})
    void clWeeklySelectionRespectsDifferenceCooldownAndPendingSeries(int value){
        var m=CareerMarketEngineTest.engine("LCK:GEN");m.clEnabled=true;m.planner.repair(CareerMarketEngineTest.DATE);String team="LCK:T1";
        String first=m.lineups.get(team).stream().filter(id->m.player(id).position()==Position.TOP).findFirst().orElseThrow();
        var tops=m.members.values().stream().filter(v->team.equals(v.ownerTeam())&&v.squad().equals("DEVELOPMENT")&&m.player(v.playerId()).position()==Position.TOP).map(CareerRosterStore.Membership::playerId).sorted().toList();
        String incumbent=tops.getFirst(),candidate=tops.getLast();rating(m,first,20,200);rating(m,incumbent,12,200);rating(m,candidate,value,200);
        var lineup=new ArrayList<>(m.clLineups.get(team));lineup.removeIf(id->m.player(id).position()==Position.TOP);lineup.add(incumbent);m.clLineups.put(team,lineup);
        var original=m.state();var waits=new TreeMap<>(original.squadPlanning().cooldowns());waits.put(team+"|TOP",MONDAY.plusDays(28));
        m.planner=new CareerSquadPlanner(m,new CareerSquadPlanningPolicy.State(CareerSquadPlanningPolicy.VERSION,null,waits,List.of()));
        m.squadRestrictions.add(new CareerSquadPlanner.Restriction(incumbent,"DEVELOPMENT",MONDAY,true));m.planner.review(MONDAY);assertThat(m.clLineups.get(team)).contains(incumbent);
        m.squadRestrictions.clear();m.planner.review(MONDAY.plusWeeks(1));assertThat(m.clLineups.get(team)).contains(value>13?candidate:incumbent);
        if(value>13){rating(m,incumbent,20,200);m.planner.review(MONDAY.plusWeeks(2));assertThat(m.clLineups.get(team)).contains(candidate);}
    }
    @ParameterizedTest @ValueSource(ints={11,15})
    void clSkipsStrongerIneligibleCandidateBeforeApplyingThresholdAndWait(int value){
        var m=CareerMarketEngineTest.engine("LCK:GEN");m.clEnabled=true;m.planner.repair(CareerMarketEngineTest.DATE);String team="LCK:T1";
        var tops=m.members.values().stream().filter(v->team.equals(v.ownerTeam())&&v.squad().equals("DEVELOPMENT")&&m.player(v.playerId()).position()==Position.TOP).map(CareerRosterStore.Membership::playerId).sorted().toList();
        String incumbent=tops.getFirst(),eligible=tops.getLast(),blocked=top(m,"DEVELOPMENT");
        var c=m.active(blocked,MONDAY);m.contracts.put(c.contractId(),new Contract(c.contractId(),c.careerId(),blocked,team,team+":DEVELOPMENT",c.signedDate(),c.terms(),c.status(),c.revision(),c.policyVersion(),c.origin(),c.terminationPolicy(),c.endedDate(),c.paidThrough()));
        m.members.put(blocked,new CareerRosterStore.Membership(blocked,team,team+":DEVELOPMENT","DEVELOPMENT",null));
        m.lineups.get(team).stream().filter(id->m.player(id).position()==Position.TOP).forEach(id->rating(m,id,20,200));
        rating(m,incumbent,10,200);rating(m,eligible,value,200);rating(m,blocked,18,200);
        var lineup=new ArrayList<>(m.clLineups.get(team));lineup.removeIf(id->m.player(id).position()==Position.TOP);lineup.add(incumbent);m.clLineups.put(team,lineup);
        m.squadRestrictions.add(new CareerSquadPlanner.Restriction(blocked,"FIRST_TEAM",MONDAY,false));
        m.planner.review(MONDAY);assertThat(m.clLineups.get(team)).contains(value==15?eligible:incumbent).doesNotContain(blocked);
        var once=m.state();m.planner.review(MONDAY);assertThat(m.state()).isEqualTo(once);
        if(value==15){rating(m,incumbent,19,200);m.planner.review(MONDAY.plusWeeks(1));assertThat(m.clLineups.get(team)).contains(eligible);}
    }
    @Test void confirmedLoanReturnCoversOnlyDatesAfterReturnAndBeforeContractExpiry(){
        var m=setup();String id=top(m,"FIRST_TEAM");var c=m.active(id,MONDAY);String parent="LCK:BRO",borrower="LCK:BFX";
        var member=m.members.get(id);var end=MONDAY.plusDays(10);
        m.tradeEngine.loans.put("forecast-loan",new CareerManagementState.Loan("forecast-loan","trade",c.contractId(),id,parent,borrower,MONDAY.minusDays(1),end,0,50,Role.RESERVE,member.organizationId(),member.squad(),"ACTIVE",CareerManagementPolicy.VERSION));
        m.members.put(id,new CareerRosterStore.Membership(id,borrower,borrower,"FIRST_TEAM",null));
        m.developmentFixtures=Map.of(parent,List.of(MONDAY.plusDays(7)));
        assertThat(m.planner.committedCover(parent,Position.TOP,"FIRST_TEAM",MONDAY)).isFalse();
        m.developmentFixtures=Map.of(parent,List.of(end.plusDays(1)));
        assertThat(m.planner.committedCover(parent,Position.TOP,"FIRST_TEAM",MONDAY)).isTrue();
        assertThat(m.planner.committedCover(borrower,Position.TOP,"FIRST_TEAM",MONDAY)).isFalse();
        assertThat(m.planner.committedCover(parent,Position.TOP,"DEVELOPMENT",MONDAY)).isFalse();
        m.developmentFixtures=Map.of(parent,List.of(MONDAY.minusDays(1)));
        assertThat(m.planner.committedCover(parent,Position.TOP,"FIRST_TEAM",MONDAY)).isFalse();
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.EnumSource(CareerManagementState.Kind.class)
    void agreedIncomingCoverRespectsEffectDatesRegistrationAndUnconfirmedConsent(CareerManagementState.Kind kind){
        var m=setup();String pid=top(m,"FIRST_TEAM"),buyer="LCK:BFX";var c=m.active(pid,MONDAY);var start=MONDAY.plusDays(10);
        var terms=new CareerManagementState.TradeTerms(kind,pid,c.team(),buyer,start,start.plusDays(100),0,50,new Terms(start,start.plusDays(100),c.terms().annualSalary(),0,Role.STARTER),null);
        var agreed=new CareerManagementState.Trade("future-cover",c.contractId(),buyer,terms,MONDAY,MONDAY,MONDAY,start,0,null,CareerManagementState.TradeStatus.AGREED,true,true,0,0,0,100L,"fixture",CareerManagementPolicy.VERSION,null);
        m.tradeEngine.trades.put(agreed.tradeId(),agreed);m.developmentFixtures=Map.of(buyer,List.of(start.minusDays(1)));
        assertThat(m.planner.committedCover(buyer,Position.TOP,"FIRST_TEAM",MONDAY)).isFalse();
        m.developmentFixtures=Map.of(buyer,List.of(start));assertThat(m.planner.committedCover(buyer,Position.TOP,"FIRST_TEAM",MONDAY)).isTrue();
        m.internationalPools.put(buyer+"|MSI",Set.of("other"));assertThat(m.planner.committedCover(buyer,Position.TOP,"FIRST_TEAM",MONDAY)).isFalse();
        m.internationalPools.put(buyer+"|MSI",Set.of(pid));assertThat(m.planner.committedCover(buyer,Position.TOP,"FIRST_TEAM",MONDAY)).isTrue();
        m.squadRestrictions.add(new CareerSquadPlanner.Restriction(pid,"DEVELOPMENT",MONDAY,true));assertThat(m.planner.committedCover(buyer,Position.TOP,"FIRST_TEAM",MONDAY)).isFalse();
        m.squadRestrictions.clear();m.squadRestrictions.add(new CareerSquadPlanner.Restriction(pid,"FIRST_TEAM",MONDAY,true));assertThat(m.planner.committedCover(buyer,Position.TOP,"FIRST_TEAM",MONDAY)).isTrue();
        m.tradeEngine.trades.put(agreed.tradeId(),new CareerManagementState.Trade(agreed.tradeId(),c.contractId(),buyer,terms,MONDAY,MONDAY,MONDAY,start,0,null,CareerManagementState.TradeStatus.PLAYER_PENDING,true,true,0,0,0,null,"fixture",CareerManagementPolicy.VERSION,null));
        assertThat(m.planner.committedCover(buyer,Position.TOP,"FIRST_TEAM",MONDAY)).isFalse();
    }
    @Test void fatigueDoesNotChangeSelectionAndCrossSquadOrInternationalRegistrationBlocksMove(){
        var a=setup();var b=setup();String id=top(a,"DEVELOPMENT"),first=top(a,"FIRST_TEAM");
        for(var m:List.of(a,b)){rating(m,id,18,190);rating(m,first,12,190);m.development=new CareerDevelopmentEngine(m.directory,CareerDevelopmentEngine.initial(m.directory,CareerMarketEngineTest.DATE));}
        var p=b.development.players.get(id);b.development.players.put(id,new CareerDevelopmentState.Player(p.internalRatings(),p.internalProficiencies(),p.remainder(),p.proficiencyRemainders(),p.cursors(),900,p.playedOn(),p.override(),p.growthSubRemainder()));
        a.planner.review(MONDAY);b.planner.review(MONDAY);assertThat(a.roster()).isEqualTo(b.roster());
        a.squadRestrictions.add(new CareerSquadPlanner.Restriction(id,"FIRST_TEAM",MONDAY,false));assertThat(a.planner.movable(id,"DEVELOPMENT",MONDAY)).isFalse();assertThat(a.planner.movable(id,"DEVELOPMENT",MONDAY.plusDays(1))).isTrue();
        a.internationalPools.put("LCK:BRO|MSI",Set.of(id));assertThat(a.planner.canDepart("LCK:BRO",id,MONDAY.plusDays(1))).isFalse();
    }
    @Test void scheduledDevelopmentNeedIsStillPlannedWhileTheFirstTeamRoleIsMissing(){
        var m=setup();String team="LCK:BRO";
        for(var member:new ArrayList<>(m.members.values()))if(team.equals(member.ownerTeam())&&m.player(member.playerId()).position()==Position.TOP){m.contracts.values().removeIf(c->c.playerId().equals(member.playerId()));m.freeAgents.remove(member.playerId());}
        var account=m.accounts.get(team);m.accounts.put(team,new Account(team,1,account.cash(),account.rosterLimit()));
        m.developmentFixtures=Map.of(team,List.of(MONDAY.plusDays(7)),team+"|DEVELOPMENT",List.of(MONDAY.plusDays(8)));
        m.planner.review(MONDAY);
        assertThat(m.planner.inspections.stream().filter(i->i.team().equals(team)&&i.position()==Position.TOP).map(CareerSquadPlanner.Inspection::squad)).containsExactly("FIRST_TEAM","DEVELOPMENT");
        assertThat(m.offers.values()).noneMatch(o->o.team().equals(team));
        assertThat(m.planner.state().decisions()).anyMatch(d->d.team().equals(team)&&d.position()==Position.TOP&&d.squad().equals("DEVELOPMENT")&&d.action().equals("COVERAGE")&&d.status().equals("DEFERRED"));
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void incumbentRenewalGetsAnInspectionBeforeExternalRosterFailures(boolean affordable) {
        var m=setup();m.clEnabled=false;String team="LCK:BRO";
        String incumbent=m.lineups.get(team).stream().filter(id->m.player(id).position()==Position.TOP).findFirst().orElseThrow();
        var c=m.active(incumbent,MONDAY);var end=MONDAY.plusDays(40);
        m.contracts.put(c.contractId(),new Contract(c.contractId(),c.careerId(),c.playerId(),c.team(),c.organizationId(),c.signedDate(),new Terms(c.terms().startDate(),end,c.terms().annualSalary(),0,c.terms().role()),c.status(),c.revision(),c.policyVersion(),c.origin(),c.terminationPolicy(),c.endedDate(),c.paidThrough()));
        var external=m.directory.players().keySet().stream().filter(id->m.player(id).position()==Position.TOP&&!team.equals(m.members.get(id).ownerTeam())).sorted().limit(CareerSquadPlanningPolicy.COMMON_CHECKS).toList();
        for(String id:m.directory.players().keySet())if(m.player(id).position()==Position.TOP){
            if(team.equals(m.members.get(id).ownerTeam()))rating(m,id,10,200);
            else if(external.contains(id)){m.contracts.values().removeIf(k->k.playerId().equals(id));m.freeAgents.add(id);m.members.put(id,new CareerRosterStore.Membership(id,null,"FREE_AGENT","FREE_AGENT",null));rating(m,id,20,200);}
            else {m.freeAgents.remove(id);m.squadRestrictions.add(new CareerSquadPlanner.Restriction(id,"FIRST_TEAM",MONDAY,true));}
        }
        int held=(int)m.contracts.values().stream().filter(k->team.equals(k.team())&&k.status()==ContractStatus.ACTIVE&&!MONDAY.isBefore(k.terms().startDate())&&!MONDAY.isAfter(k.terms().endDate())).map(Contract::playerId).distinct().count();
        m.accounts.put(team,new Account(team,affordable?1_000_000_000L:1,1_000_000_000L,held));
        var original=m.active(incumbent,MONDAY);var user=List.copyOf(m.lineups.get(m.managed));
        m.planner.review(MONDAY);var once=m.state();m.planner.review(MONDAY);assertThat(m.state()).isEqualTo(once);
        var offers=m.offers.values().stream().filter(o->o.team().equals(team)&&m.player(o.playerId()).position()==Position.TOP).toList();
        assertThat(m.planner.inspections).allMatch(i->i.commonChecks()<=CareerSquadPlanningPolicy.COMMON_CHECKS);
        System.out.println("RENEWAL_INSPECTIONS "+m.planner.inspections.stream().filter(i->i.team().equals(team)&&i.position()==Position.TOP).toList());
        System.out.println("RENEWAL_BOUNDARY affordable="+affordable+" incumbent="+incumbent+" cap="+held+" offers="+CareerRosterStore.write(offers));
        if(affordable)assertThat(offers).singleElement().satisfies(o->{assertThat(o.playerId()).isEqualTo(incumbent);assertThat(o.terms().startDate()).isEqualTo(end.plusDays(1));});
        else assertThat(offers).isEmpty();
        assertThat(m.active(incumbent,MONDAY)).isEqualTo(original);assertThat(m.scheduled(incumbent)).isNull();assertThat(m.lineups.get(m.managed)).isEqualTo(user);
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    void essentialPositionsKeepRoomForEachOtherBeforeAnExpensiveTop(boolean affordable) {
        var m=setup();String team="LCK:BRO";m.clEnabled=false;
        var tops=m.directory.players().keySet().stream().filter(id->m.player(id).position()==Position.TOP&&!team.equals(m.members.get(id).ownerTeam())).sorted().limit(2).toList();
        String support=m.directory.players().keySet().stream().filter(id->m.player(id).position()==Position.SUPPORT&&!team.equals(m.members.get(id).ownerTeam())).sorted().findFirst().orElseThrow();
        var chosen=Set.of(tops.getFirst(),tops.getLast(),support);
        for(String id:m.directory.players().keySet())if(Set.of(Position.TOP,Position.SUPPORT).contains(m.player(id).position())){
            if(team.equals(m.members.get(id).ownerTeam())){m.contracts.values().removeIf(c->c.playerId().equals(id));m.freeAgents.remove(id);}
            else if(chosen.contains(id)){m.contracts.values().removeIf(c->c.playerId().equals(id));m.freeAgents.add(id);m.members.put(id,new CareerRosterStore.Membership(id,null,"FREE_AGENT","FREE_AGENT",null));rating(m,id,id.equals(tops.getFirst())?20:10,200);}
            else m.squadRestrictions.add(new CareerSquadPlanner.Restriction(id,"FIRST_TEAM",MONDAY,true));
        }
        m.lineups.values().forEach(ids->ids.removeIf(chosen::contains));m.lineups.get(team).removeIf(id->Set.of(Position.TOP,Position.SUPPORT).contains(m.player(id).position()));
        var a=m.accounts.get(team);long held=m.peakSalary(team);m.accounts.put(team,new Account(team,held+(affordable?310_000:1),a.cash(),a.rosterLimit()));
        m.developmentFixtures=Map.of(team,List.of(MONDAY.plusDays(28)));
        m.planner.review(MONDAY);var offers=m.offers.values().stream().filter(o->o.team().equals(team)).toList();
        assertThat(m.planner.inspections).allMatch(i->i.commonChecks()<=CareerSquadPlanningPolicy.COMMON_CHECKS);
        System.out.println("JOINT_INSPECTIONS "+m.planner.inspections.stream().filter(i->i.team().equals(team)).toList());
        System.out.println("JOINT_COVERAGE affordable="+affordable+" existingSalary="+held+" cap="+m.accounts.get(team).annualBudget()+" offers="+CareerRosterStore.write(offers));
        if(affordable)assertThat(offers).extracting(Offer::playerId).contains(tops.getLast(),support).doesNotContain(tops.getFirst());else assertThat(offers).isEmpty();
        var once=m.state();m.planner.review(MONDAY);assertThat(m.state()).isEqualTo(once);
        assertThat(m.peakSalary(team)).isLessThanOrEqualTo(m.accounts.get(team).annualBudget());
        if(affordable){
            var top=offers.stream().filter(o->o.playerId().equals(tops.getLast())).findFirst().orElseThrow();long reserved=m.reservedCash(team);
            m.withdraw(team,top.offerId(),MONDAY);assertThat(m.reservedCash(team)).isEqualTo(reserved-top.terms().signingBonus());
            m.planner.review(MONDAY.plusWeeks(1));assertThat(m.offers.values().stream().filter(o->o.team().equals(team)&&o.playerId().equals(top.playerId()))).hasSize(1);
            assertThat(m.offers.get(top.offerId()).status()).isEqualTo(OfferStatus.WITHDRAWN);
            assertThat(m.planner.state().lastReview()).isEqualTo(MONDAY.plusWeeks(1));
            assertThat(m.planner.state().decisions()).anyMatch(d->d.team().equals(team)&&d.position()==Position.TOP&&d.date().equals(MONDAY.plusWeeks(1))&&d.status().equals("DEFERRED"));
            var replanned=m.state();m.planner.review(MONDAY.plusWeeks(1));assertThat(m.state()).isEqualTo(replanned);
        }
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"4,true","4,false","25,true","25,false"})
    void coverageSearchReachesAffordableCandidateThroughCommonApproval(int candidateCount,boolean affordable) {
        var m=setup();String team="LCK:BRO";m.clEnabled=false;
        var candidates=m.directory.players().keySet().stream().filter(id->m.player(id).position()==Position.TOP&&!team.equals(m.members.get(id).ownerTeam())).sorted().limit(candidateCount).toList();
        for(String id:m.directory.players().keySet())if(m.player(id).position()==Position.TOP){
            if(team.equals(m.members.get(id).ownerTeam())){m.contracts.values().removeIf(c->c.playerId().equals(id));m.freeAgents.remove(id);}
            else if(candidates.contains(id)){m.contracts.values().removeIf(c->c.playerId().equals(id));m.freeAgents.add(id);m.members.put(id,new CareerRosterStore.Membership(id,null,"FREE_AGENT","FREE_AGENT",null));rating(m,id,id.equals(candidates.getLast())?10:20,200);}
            else m.squadRestrictions.add(new CareerSquadPlanner.Restriction(id,"FIRST_TEAM",MONDAY,true));
        }
        m.lineups.values().forEach(ids->ids.removeIf(candidates::contains));
        m.lineups.get(team).removeIf(id->m.player(id).position()==Position.TOP);
        var a=m.accounts.get(team);long budget=m.peakSalary(team)+(affordable?160_000:0);m.accounts.put(team,new Account(team,budget,a.cash(),a.rosterLimit()));
        m.planner.review(MONDAY);
        var offers=m.offers.values().stream().filter(o->o.team().equals(team)&&m.player(o.playerId()).position()==Position.TOP).toList();
        if(!affordable){assertThat(offers).isEmpty();assertThat(m.state().squadPlanning().decisions()).anyMatch(d->d.team().equals(team)&&d.position()==Position.TOP&&d.reason().startsWith("FINANCE_BLOCKED"));return;}
        assertThat(offers).as("coverage search over %s candidates: %s",candidateCount,m.state().squadPlanning().decisions().stream().filter(d->d.team().equals(team)&&d.position()==Position.TOP).toList()).singleElement().satisfies(o->assertThat(o.playerId()).isEqualTo(candidates.getLast()));
        var offer=offers.getFirst();assertThat(offer.terms().role()).isEqualTo(Role.STARTER);
        m.advance(offer.decisionDate());
        assertThat(m.offers.get(offer.offerId()).status()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(m.active(offer.playerId(),offer.terms().startDate())).isNotNull();
        System.out.println("COVERAGE_CANDIDATE team="+team+" candidates="+candidates+" budget="+budget+" offer="+CareerRosterStore.write(offer));
    }

    @Test void laterCoverageCandidateAccountsForEarlierProposalsInTheSameReview(){
        var m=setup();String team="LCK:BRO";m.clEnabled=false;var review=java.time.LocalDate.of(2027,12,27);
        // Current-year salaries are already paid; the FA start is Jan 1, covered by the ordinary
        // next annual allocation. The small remaining cash therefore constrains signing bonuses.
        for(var c:new ArrayList<>(m.contracts.values())){
            var end=java.time.LocalDate.of(2027,12,31);var t=c.terms();
            m.contracts.put(c.contractId(),new Contract(c.contractId(),c.careerId(),c.playerId(),c.team(),c.organizationId(),c.signedDate(),new Terms(t.startDate(),end,t.annualSalary(),t.signingBonus(),t.role()),c.status(),c.revision(),c.policyVersion(),c.origin(),c.terminationPolicy(),c.endedDate(),end));
        }
        var tops=m.directory.players().keySet().stream().filter(id->m.player(id).position()==Position.TOP&&!team.equals(m.members.get(id).ownerTeam())).sorted().toList();
        var junglers=m.directory.players().keySet().stream().filter(id->m.player(id).position()==Position.JUNGLE&&!team.equals(m.members.get(id).ownerTeam())).sorted().limit(2).toList();
        String top=tops.getFirst(),expensive=junglers.getFirst(),cheap=junglers.getLast();var chosen=Set.of(top,expensive,cheap);
        for(String id:m.directory.players().keySet())if(Set.of(Position.TOP,Position.JUNGLE).contains(m.player(id).position())){
            m.contracts.values().removeIf(c->c.playerId().equals(id));
            m.freeAgents.remove(id);
            if(chosen.contains(id)){m.freeAgents.add(id);m.members.put(id,new CareerRosterStore.Membership(id,null,"FREE_AGENT","FREE_AGENT",null));rating(m,id,id.equals(cheap)?1:20,200);}
            else m.squadRestrictions.add(new CareerSquadPlanner.Restriction(id,"FIRST_TEAM",MONDAY,true));
        }
        m.lineups.values().forEach(ids->ids.removeIf(chosen::contains));m.lineups.get(team).removeIf(id->Set.of(Position.TOP,Position.JUNGLE).contains(m.player(id).position()));
        var a=m.accounts.get(team);m.accounts.put(team,new Account(team,a.annualBudget(),26_000,a.rosterLimit()));
        m.planner.review(review);
        var offers=m.offers.values().stream().filter(o->o.team().equals(team)).toList();
        assertThat(offers).extracting(Offer::playerId).contains(top,cheap).doesNotContain(expensive);
        assertThat(m.reservedCash(team)).isLessThanOrEqualTo(26_000);
    }

}
