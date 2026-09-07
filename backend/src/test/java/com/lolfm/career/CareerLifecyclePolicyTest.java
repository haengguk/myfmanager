package com.lolfm.career;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.domain.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.lolfm.career.CareerLifecyclePolicy.*;
import static com.lolfm.career.CareerLifecycleState.*;

class CareerLifecyclePolicyTest {
    static final LocalDate DATE=LocalDate.of(2027,10,31);
    static final Observation UNKNOWN=new Observation("OVERSEAS_OR_CL_UNOBSERVED",0,0,0,null,null);
    @Test void fullClEmptySeasonsAccumulateAndInterruptedEvidenceResets() {
        var d=CareerDevelopmentPolicyTest.definition(15,190,19);var start=LocalDate.of(2027,1,1);
        var p=new Person(ageProfile(d,1,start),"AUTHORED",null,start,150,start,Status.ACTIVE,null,null,"test",0,0,0,null,"NOT_YET_REVIEWED",null,start,"DEVELOPMENT");
        for(int year=2027;year<=2029;year++) {
            var observation=new Observation("FULL_DEVELOPMENT_SEASON",18,0,0,start,start.plusDays(120));
            int empty=observation.consecutiveEmpty(p,year);assertThat(empty).isEqualTo(year-2026);
            p=new Person(p.age(),p.source(),null,start,150,start,Status.ACTIVE,null,null,"test",0,0,empty,year,observation.coverage(),null,start,"DEVELOPMENT");
        }
        var full=new Observation("FULL_DEVELOPMENT_SEASON",18,0,0,start,start.plusDays(120));
        assertThat(full.consecutiveEmpty(p,2031)).isEqualTo(1);
        assertThat(full.consecutiveEmpty(p.appeared(),2030)).isEqualTo(1);
        assertThat(full.consecutiveEmpty(p.domesticSince(start.plusYears(3),"FIRST_TEAM"),2030)).isEqualTo(1);
        for(String coverage:List.of("PARTIAL_EMPLOYMENT","INSUFFICIENT_OPPORTUNITIES","OVERSEAS_OR_CL_UNOBSERVED"))
            assertThat(new Observation(coverage,3,0,0,start,start.plusDays(10)).consecutiveEmpty(p,2030)).isZero();
        assertThat(new Observation("FULL_DEVELOPMENT_SEASON",18,1,2,start,start.plusDays(120)).consecutiveEmpty(p,2030)).isZero();
    }
    @Test void declineBanksSixHundredRatherThanRoundingTwelveSkillsDown() {
        var d=CareerDevelopmentPolicyTest.definition(15,190,25);var p=CareerDevelopmentPolicy.initial(d);
        var first=decline(p,Position.TOP,25,200,0,UNKNOWN);
        assertThat(first.budget()).isEqualTo(400);assertThat(first.carried()).isEqualTo(600);assertThat(first.player()).isEqualTo(p);assertThat(first.deltas()).isEmpty();
        var second=decline(first.player(),Position.TOP,25,first.carried(),first.cursor(),UNKNOWN);
        assertThat(second.applied()).isEqualTo(1000);assertThat(second.deltas()).hasSize(1);assertThat(second.deltas().values()).containsOnly(-1000);
        assertThat(CareerDevelopmentPolicy.sum(p)-CareerDevelopmentPolicy.sum(second.player())).isEqualTo(1000);
        assertThat(second.carried()).isZero();assertThat(ca(p)-ca(second.player())).isBetween(0,1);
    }
    @ParameterizedTest @ValueSource(ints={24,25,27,29,32})
    void declinePreservesFractionsFloorsAndCapsWholeBudget(int age) {
        var d=CareerDevelopmentPolicyTest.definition(15,190,age);var original=CareerDevelopmentPolicy.initial(d);
        var ratings=new EnumMap<PlayerSkill,Integer>(original.internalRatings());ratings.replaceAll((k,v)->k==PlayerSkill.MECHANICS?1000:15599);
        var p=new CareerDevelopmentState.Player(ratings,original.internalProficiencies(),123,Map.of(),Map.of(),100,null,null,7);
        var result=decline(p,Position.TOP,age,600,0,UNKNOWN);
        assertThat(result.applied()).isLessThanOrEqualTo(declineLimit(age));assertThat(result.carried()).isBetween(0,999);
        assertThat(result.applied()+result.carried()+result.limited()).isEqualTo(result.budget()+600);
        result.player().internalRatings().forEach((k,v)->{assertThat(v).isGreaterThanOrEqualTo(1000);assertThat(ratings.get(k)-v).isIn(0,1000);assertThat(v%1000).isEqualTo(ratings.get(k)%1000);});
        assertThat(result.player().remainder()).isEqualTo(123);assertThat(result.player().growthSubRemainder()).isEqualTo(7);
        assertThat(decline(p,Position.TOP,age,600,0,UNKNOWN)).isEqualTo(result);
    }
    @Test void agesArePersistentAndRetirementUsesActualCoverage() {
        var d=CareerDevelopmentPolicyTest.definition(15,190,19);var publicAge=ageProfile(d,1,DATE);assertThat(publicAge.publicBirthDate()).isEqualTo(LocalDate.of(2008,1,1));assertThat(age(publicAge,DATE.plusYears(3))).isEqualTo(22);
        var unknown=new com.lolfm.player.ExpandedPlayerCatalog.Definition(d.playerId(),d.nickname(),d.position(),d.gameplay(),false,null,null,"DEVELOPMENT",null,"{}");
        var estimated=ageProfile(unknown,41,DATE);assertThat(estimated.publicBirthDate()).isNull();assertThat(age(estimated,estimated.referenceDate())).isBetween(18,20);
        assertThat(ageProfile(unknown,41,DATE)).isEqualTo(estimated);assertThat(age(estimated,DATE.plusYears(4))).isEqualTo(age(estimated,DATE)+4);
        assertThat(activityFactor(UNKNOWN)).isEqualTo(1000);
        assertThat(retirementProbability(24,UNKNOWN,3,false,true,8,100,0,false)).isZero();
        assertThat(retirementProbability(24,UNKNOWN,0,true,true,0,100,50,false)).isEqualTo(15);
        assertThat(retirementProbability(32,new Observation("FULL_DOMESTIC_SEASON",20,0,0,DATE.minusDays(150),DATE),3,false,true,4,150,20,false)).isEqualTo(95);
        assertThat(retirementProbability(32,UNKNOWN,3,false,false,0,181,50,true)).isEqualTo(57);
        assertThat(retirementDate(2027,DATE)).isEqualTo(LocalDate.of(2028,1,1));assertThat(retirementDate(2027,LocalDate.of(2028,1,5))).isEqualTo(LocalDate.of(2028,1,6));
    }
    @ParameterizedTest @ValueSource(ints={0,1,4,5,36})
    void rookieClassesAreLegalBoundedAndDeterministic(int count) {
        var champions=new ChampionCatalog(new ObjectMapper());var supplies=new EnumMap<Position,Integer>(Position.class);for(var p:Position.values())supplies.put(p,p==Position.JUNGLE?0:10);
        var allocation=allocate(count,supplies);assertThat(allocation.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(count);if(count>=5)assertThat(allocation.values()).allMatch(v->v>=1);if(count>0&&count<5)assertThat(allocation.get(Position.JUNGLE)).isEqualTo(count);
        var rookies=CareerRookieFactory.generate("career-test",41,2028,DATE,allocation,false,1,champions,List.of("LCK","LEC"));
        assertThat(CareerRookieFactory.generate("career-test",41,2028,DATE,allocation,false,1,champions,List.of("LCK","LEC"))).isEqualTo(rookies);
        assertThat(rookies).hasSize(count);assertThat(rookies.stream().filter(r->CareerDevelopmentPolicy.metadata(r.definition()).potential()>=190)).hasSizeLessThanOrEqualTo(1);
        assertThat(rookies.stream().filter(r->{int pa=CareerDevelopmentPolicy.metadata(r.definition()).potential();return pa>=180&&pa<=189;})).hasSizeLessThanOrEqualTo(3);
        for(var r:rookies){var d=r.definition();var p=CareerDevelopmentPolicy.initial(d);assertThat(d.gameplay().ratings().keySet()).containsExactlyInAnyOrderElementsOf(PlayerSkill.forPosition(d.position()));assertThat(d.gameplay().ratings().values()).allMatch(n->n>=1&&n<=20);assertThat(CareerDevelopmentPolicy.sum(p)).isLessThanOrEqualTo(CareerDevelopmentPolicy.ceiling(CareerDevelopmentPolicy.metadata(d).potential()));assertThat(ca(p)).isBetween(90,165);assertThat(d.gameplay().proficiencies()).hasSizeBetween(3,6);for(var c:d.gameplay().proficiencies())assertThat(champions.forPosition(d.position())).anyMatch(v->v.id().value().equals(c.championId()));}
        assertThat(normalCount(490)).isZero();assertThat(normalCount(489)).isEqualTo(1);assertThat(normalCount(486)).isEqualTo(4);assertThat(normalCount(460)).isEqualTo(10);assertThat(normalCount(400)).isEqualTo(36);
        var emergency=CareerRookieFactory.generate("career-test",41,2028,DATE,allocate(3,Map.of()),true,count+1,champions,List.of("LCK"));assertThat(emergency).allMatch(r->CareerDevelopmentPolicy.metadata(r.definition()).potential()<=155);assertThat(emergency).noneMatch(e->rookies.stream().anyMatch(r->r.definition().playerId().equals(e.definition().playerId())));
    }
    @Test void sevenLimitedPureGrowthAndAgingTrajectories() {
        int[][] examples={{17,190,165,100},{17,190,165,0},{17,190,165,-1},{19,175,155,100},{19,175,155,0},{18,155,132,100},{18,155,132,-1}};
        for(var example:examples) {
            int startAge=example[0],pa=example[1],target=example[2],games=example[3];var d=CareerDevelopmentPolicyTest.definition(15,pa,startAge);
            int total=Math.min(CareerDevelopmentPolicy.ceiling(pa)/1000,(int)Math.round(12+(target-1)*228.0/199));var stats=new EnumMap<PlayerSkill,Integer>(PlayerSkill.class);var keys=PlayerSkill.orderedForPosition(Position.TOP);for(int i=0;i<12;i++)stats.put(keys.get(i),1000*(total/12+(i<total%12?1:0)));
            var p=new CareerDevelopmentState.Player(stats,Map.of(),0,Map.of(),Map.of(),0,null,null);int remainder=0,cursor=0;var annual=new ArrayList<Integer>();
            for(int year=0;year<12;year++) {
                var begin=LocalDate.of(2027+year,1,1);
                for(int day=0;day<365;day++){var date=begin.plusDays(day);if(games>0&&day<300&&day%3==0)p=CareerDevelopmentPolicy.game(p,d,CareerDevelopmentPolicy.metadata(d),date,"aatrox");p=CareerDevelopmentPolicy.day(p,d,CareerDevelopmentPolicy.metadata(d),date,games<0?new CareerDevelopmentState.Plan(CareerDevelopmentState.Intensity.LIGHT,CareerDevelopmentState.Focus.BALANCED,null,List.of()):CareerDevelopmentPolicy.DEFAULT,games<0);}
                var observation=games<0?UNKNOWN:new Observation("FULL_DOMESTIC_SEASON",100,Math.max(0,games),Math.max(0,games),begin,begin.plusDays(299));
                var loss=decline(p,Position.TOP,startAge+year,remainder,cursor,observation);p=loss.player();remainder=loss.carried();cursor=loss.cursor();annual.add(ca(p));assertThat(CareerDevelopmentPolicy.sum(p)).isLessThanOrEqualTo(CareerDevelopmentPolicy.ceiling(pa));
            }
            System.out.println("LIFECYCLE_PURE_TRAJECTORY age="+startAge+" PA="+pa+" initialCA="+target+" games="+games+" annualCA="+annual);
        }
    }
    @Test void generatedNamesAreStableResolveCollisionsAndPreserveGameplay() {
        var champions=new ChampionCatalog(new ObjectMapper());
        var r=CareerRookieFactory.generate("career-test",41,2028,DATE,Map.of(Position.TOP,1),false,1,champions,List.of("LCK")).getFirst();
        var d=r.definition();var details=CareerRosterStore.read(d.detailsJson(),com.fasterxml.jackson.databind.node.ObjectNode.class);
        assertThat(details.path("personal").path("legalName").asText()).isNotBlank();assertThat(details.path("personal").path("nameSource").asText()).isEqualTo("GENERATED_FICTIONAL_NAME");
        var occupied=new HashSet<String>();occupied.add(d.nickname().toLowerCase(Locale.ROOT));var renamed=CareerGeneratedNames.assign(d,41,occupied);
        assertThat(renamed.nickname()).isNotEqualTo(d.nickname());assertThat(renamed.gameplay().ratings()).isEqualTo(d.gameplay().ratings());assertThat(renamed.gameplay().proficiencies()).isEqualTo(d.gameplay().proficiencies());
        assertThat(renamed.playerId()).isEqualTo(d.playerId());assertThat(CareerDevelopmentPolicy.metadata(renamed)).isEqualTo(CareerDevelopmentPolicy.metadata(d));
        assertThat(CareerGeneratedNames.assign(d,41,new HashSet<>(Set.of(d.nickname().toLowerCase(Locale.ROOT))))).isEqualTo(renamed);
        assertThat(CareerGeneratedNames.placeholder(d)).isFalse();
        ((com.fasterxml.jackson.databind.node.ObjectNode)details.path("generated")).remove(List.of("namePolicyVersion","nameStatus"));((com.fasterxml.jackson.databind.node.ObjectNode)details.path("personal")).remove(List.of("legalName","nameSource"));
        String old="LCK 신인 2028-1";details.put("name",old).put("nickname",old);
        var legacy=new com.lolfm.player.ExpandedPlayerCatalog.Definition(d.playerId(),old,d.position(),new CompetitionRosterSnapshot.Starter(d.playerId(),old,d.position(),d.gameplay().ratings(),d.gameplay().proficiencies()),d.provisional(),d.initialOrganizationId(),d.initialOwnerTeam(),d.initialSquad(),d.eligibilityReason(),details.toString());
        assertThat(CareerGeneratedNames.placeholder(legacy)).isTrue();assertThat(CareerGeneratedNames.assign(legacy,41,new HashSet<>())).isEqualTo(d);
        for(String field:List.of("name","nickname")) {
            var edited=details.deepCopy().put(field,"사용자 지정 이름");
            var custom=new com.lolfm.player.ExpandedPlayerCatalog.Definition(legacy.playerId(),legacy.nickname(),legacy.position(),legacy.gameplay(),legacy.provisional(),legacy.initialOrganizationId(),legacy.initialOwnerTeam(),legacy.initialSquad(),legacy.eligibilityReason(),edited.toString());
            assertThat(CareerGeneratedNames.placeholder(custom)).as("preserve user-edited %s",field).isFalse();
        }
    }
    @Test void cappedPotentialUsesCandidatePriorityRatherThanTraversalOrder() {
        var candidates=List.of(new CareerRookieFactory.PotentialCandidate("first-top",985),new CareerRookieFactory.PotentialCandidate("last-support",985));
        long seed=0; // Fixed small candidate case: the later role wins priority.
        assertThat(CareerRookieFactory.selectBand(seed,2028,candidates,980,995,1)).containsExactly("last-support");
        assertThat(CareerRookieFactory.selectBand(seed,2028,candidates.reversed(),980,995,1)).containsExactly("last-support");
        var upper=List.of("top","jungle","mid","adc","support").stream().map(id->new CareerRookieFactory.PotentialCandidate(id,950)).toList();
        assertThat(CareerRookieFactory.selectBand(seed,2028,upper,900,980,3)).hasSize(3).isEqualTo(CareerRookieFactory.selectBand(seed,2028,upper.reversed(),900,980,3));
        assertThat(CareerRookieFactory.selectBand(seed,2028,candidates,980,995,0)).isEmpty();
    }
    @Test void classLegendIsOneFivePercentDrawAndEmergencyCoversSeveralClubs() {
        assertThat(draw(59,"career-test",2028,"LEGEND_CLASS",100)).isEqualTo(2);
        var champions=new ChampionCatalog(new ObjectMapper());var roles=allocate(36,Map.of());
        var rookies=CareerRookieFactory.generate("career-test",59,2028,DATE,roles,false,1,champions,List.of("LCK"));
        assertThat(rookies).hasSize(36);assertThat(rookies.stream().filter(r->CareerDevelopmentPolicy.metadata(r.definition()).potential()>=195)).hasSize(1);
        assertThat(rookies.stream().filter(r->CareerDevelopmentPolicy.metadata(r.definition()).potential()>=190)).hasSize(1);
        var gap=emergencyAllocation(Map.of(Position.TOP,4,Position.JUNGLE,3),Map.of(Position.TOP,1),Map.of(Position.JUNGLE,1));assertThat(gap.get(Position.TOP)).isEqualTo(3);assertThat(gap.get(Position.JUNGLE)).isEqualTo(2);
        assertThat(emergencyAllocation(Map.of(Position.TOP,56,Position.JUNGLE,56),Map.of(),Map.of()).values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(EMERGENCY_MAX);
        assertThat(CareerRookieFactory.generate("career-test",59,2028,DATE,Map.of(),false,1,champions,List.of("LCK"))).isEmpty();
    }
    @Test void youngFreeAgencyRequiresTwoCompletedObservedSeasonsAndActualAppearanceResetsContinuity() {
        var age=new Age(null,LocalDate.of(2010,1,1),"TEST",DATE,DATE);
        var person=new Person(age,"AUTHORED",null,DATE,150,DATE,Status.ACTIVE,null,null,"test",0,0,3,2028,"FULL_DOMESTIC_SEASON",LocalDate.of(2027,10,1));
        assertThat(longYoungFA(person,LocalDate.of(2027,10,1),2028)).isFalse();assertThat(longYoungFA(person,LocalDate.of(2027,10,1),2029)).isTrue();
        assertThat(longYoungFA(person,LocalDate.of(2028,2,1),2029)).isFalse();assertThat(person.appeared().noAppearanceSeasons()).isZero();
    }

}
