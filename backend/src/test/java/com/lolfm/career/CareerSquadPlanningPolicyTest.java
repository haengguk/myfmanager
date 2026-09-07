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
    @ParameterizedTest @ValueSource(ints={175,190})
    void promotionAndLegalClSwapUseCurrentAbilityAndPreservePromises(int pa){
        var m=setup();String first=top(m,"FIRST_TEAM"),cl=top(m,"DEVELOPMENT");rating(m,first,12,200);rating(m,cl,16,pa);
        assertThat(CareerDevelopmentPolicy.metadata(m.player(cl)).potential()).isEqualTo(pa);
        var promises=m.management().promises();var user=m.lineups.get(m.managed);m.planner.review(MONDAY);
        assertThat(m.lineups.get("LCK:BRO")).contains(cl).doesNotContain(first);assertThat(m.clLineups.get("LCK:BRO")).contains(first).doesNotContain(cl);
        assertThat(m.management().promises()).isEqualTo(promises);assertThat(m.lineups.get(m.managed)).isEqualTo(user);
        var once=m.state();m.planner.review(MONDAY);assertThat(m.state()).isEqualTo(once);
        var restored=new CareerMarketEngine(m.career,m.managed,m.directory,m.roster(),CareerRosterStore.read(CareerRosterStore.write(once),CareerMarketState.class));
        restored.clEnabled=true;restored.clLineups.putAll(m.clLineups);rating(restored,first,20,200);restored.planner.review(MONDAY.plusWeeks(1));
        assertThat(restored.lineups.get("LCK:BRO")).contains(cl);assertThat(restored.state().squadPlanning().cooldowns()).containsEntry("LCK:BRO|TOP",MONDAY.plusDays(28));
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
    @Test void fatigueDoesNotChangeSelectionAndCrossSquadOrInternationalRegistrationBlocksMove(){
        var a=setup();var b=setup();String id=top(a,"DEVELOPMENT"),first=top(a,"FIRST_TEAM");
        for(var m:List.of(a,b)){rating(m,id,18,190);rating(m,first,12,190);m.development=new CareerDevelopmentEngine(m.directory,CareerDevelopmentEngine.initial(m.directory,CareerMarketEngineTest.DATE));}
        var p=b.development.players.get(id);b.development.players.put(id,new CareerDevelopmentState.Player(p.internalRatings(),p.internalProficiencies(),p.remainder(),p.proficiencyRemainders(),p.cursors(),900,p.playedOn(),p.override(),p.growthSubRemainder()));
        a.planner.review(MONDAY);b.planner.review(MONDAY);assertThat(a.roster()).isEqualTo(b.roster());
        a.squadRestrictions.add(new CareerSquadPlanner.Restriction(id,"FIRST_TEAM",MONDAY,false));assertThat(a.planner.movable(id,"DEVELOPMENT",MONDAY)).isFalse();assertThat(a.planner.movable(id,"DEVELOPMENT",MONDAY.plusDays(1))).isTrue();
        a.internationalPools.put("LCK:BRO|MSI",Set.of(id));assertThat(a.planner.canDepart("LCK:BRO",id,MONDAY.plusDays(1))).isFalse();
    }
}
