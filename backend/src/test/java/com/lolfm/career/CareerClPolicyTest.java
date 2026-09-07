package com.lolfm.career;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class CareerClPolicyTest {
    @Test void ninetyFixturesHaveEighteenSeriesAndOppositeFirstGameOpportunities(){
        var fixtures=CareerClPolicy.schedule(2027);assertThat(fixtures).hasSize(90).isEqualTo(CareerClPolicy.schedule(2027));assertThat(fixtures.stream().map(CareerClPolicy.Scheduled::id)).doesNotHaveDuplicates();
        for(String team:CareerClPolicy.TEAMS)assertThat(fixtures.stream().filter(f->f.first().equals(team)||f.second().equals(team))).hasSize(18);
        for(var f:fixtures){assertThat(f.first()).isNotEqualTo(f.second());assertThat(fixtures.stream().filter(r->r.first().equals(f.second())&&r.second().equals(f.first()))).hasSize(1);}
        assertThat(fixtures.getFirst().date()).isEqualTo(java.time.LocalDate.of(2027,4,5));assertThat(fixtures.getLast().date()).isEqualTo(java.time.LocalDate.of(2027,8,2));
    }
    @Test void importantCompleteTiesFinishWithBoundedActualGamesAndLowerPlacesNeedNone(){
        var strength=new TreeMap<String,Integer>();CareerClPolicy.TEAMS.forEach(t->strength.put(t,0));
        for(int size=2;size<=10;size++){
            var teams=CareerClPolicy.TEAMS.subList(0,size);var results=new TreeMap<String,CareerDomesticTiebreak.Outcome>();int rounds=0;
            while(true){var progress=CareerDomesticTiebreak.advance("CL_TEST_"+size,teams,results,List.of(),strength,Math.min(6,size));if(!progress.ranking().isEmpty()){assertThat(progress.ranking()).containsExactlyInAnyOrderElementsOf(teams);break;}
                assertThat(progress.pending()).isNotEmpty();for(var pending:progress.pending())assertThat(results.put(pending.matchId(),new CareerDomesticTiebreak.Outcome(pending.second(),pending.first()))).isNull();assertThat(++rounds).isLessThan(15);
            }assertThat(results.size()).isLessThanOrEqualTo(30);
        }
        assertThat(CareerDomesticTiebreak.advance("LOWER",CareerClPolicy.TEAMS,Map.of(),List.of(),strength,0).pending()).isEmpty();
    }
    @Test void standingsUseExactRateThenDifferenceAndRecursiveHeadToHead(){
        var matches=List.of(m("a","A","B",2,0),m("b","B","C",2,1),m("c","C","A",2,1));
        var decision=CareerDomesticRanking.development(List.of("A","B","C"),matches);assertThat(decision.ordered()).containsExactly("A","C","B");
        var tie=CareerDomesticRanking.development(List.of("A","B","C"),List.of(m("a","A","B",2,0),m("b","B","C",2,0),m("c","C","A",2,0)));assertThat(tie.groups()).hasSize(1);
        assertThatThrownBy(()->CareerDomesticRanking.development(List.of("A","B"),List.of(m("a","A","B",2,0),m("a","B","A",2,0)))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void eighteenWeeklyClOpportunitiesCoverAnInclusive120DaysAndOtherTiersStayNeutral(){
        CareerMarketEngineTest.catalog();var directory=CareerMarketEngineTest.directory;var roster=CareerMarketEngineTest.roster;var start=java.time.LocalDate.of(2027,1,1);String career="career_"+"b".repeat(64),id="player-haetae";
        var m=new CareerMarketEngine(career,"LCK:T1",directory,roster,CareerMarketEngine.initialize(career,41,start,2027,directory,roster));m.clEnabled=true;
        var d=directory.players().get(id);var age=CareerLifecyclePolicy.ageProfile(d,41,start);var person=new CareerLifecycleState.Person(age,"AUTHORED",null,start,140,start,CareerLifecycleState.Status.ACTIVE,null,null,"test",0,0,0,null,"NOT_YET_REVIEWED",null,start,"DEVELOPMENT");
        m.lifecycle=new CareerLifecycleEngine(new CareerLifecycleState(CareerLifecyclePolicy.VERSION,start,null,Map.of(id,person)));m.lifecycle.clEnabled=true;
        var series=new HashSet<String>();int number=0;for(var f:CareerClPolicy.schedule(2027))if(f.first().equals("T1")||f.second().equals("T1")){String key="fixture-"+number++;series.add(key);m.promiseEngine.appearances.put(key,new CareerManagementState.Appearance(key,key,key,2027,f.date(),2,List.of(new CareerManagementState.Opportunity(id,"LCK:T1",com.lolfm.domain.Position.TOP,"promise",true,false,"CONTRACT_AT_START")),"DEVELOPMENT","LCK_CL"));}
        var observation=CareerLifecycleStore.observation(m,id,2027,java.time.LocalDate.of(2027,8,2),series);assertThat(observation.coverage()).isEqualTo("FULL_DEVELOPMENT_SEASON");assertThat(observation.opportunities()).isEqualTo(18);assertThat(observation.starts()).isZero();
        m.clEnabled=false;assertThat(CareerLifecycleStore.observation(m,id,2027,java.time.LocalDate.of(2027,8,2),series).full()).isFalse();m.clEnabled=true;
        m.lifecycle.people.put(id,person.domesticSince(java.time.LocalDate.of(2027,5,1),"DEVELOPMENT"));assertThat(CareerLifecycleStore.observation(m,id,2027,java.time.LocalDate.of(2027,8,2),series).coverage()).isEqualTo("PARTIAL_EMPLOYMENT");
        var growth=new CareerDevelopmentEngine(directory,CareerDevelopmentEngine.initial(directory,start));
        var light=new CareerDevelopmentState.Plan(CareerDevelopmentState.Intensity.LIGHT,CareerDevelopmentState.Focus.BALANCED,null,List.of());
        growth.teams.put("LCK:T1|DEVELOPMENT",new CareerDevelopmentState.Schedule("LCK:T1",light,start,null,null,false));
        assertThat(growth.effective(id,"LCK:T1",m.members.get(id),start,List.of())).isEqualTo(light);
        assertThat(growth.effective("player-doran","LCK:T1",m.members.get("player-doran"),start,List.of())).isEqualTo(CareerDevelopmentPolicy.DEFAULT);
    }
    private static CareerDomesticRanking.Match m(String id,String a,String b,int x,int y){return new CareerDomesticRanking.Match(id,a,b,x,y,List.of());}
}
