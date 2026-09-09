package com.lolfm.career;
import static org.assertj.core.api.Assertions.*;
import static com.lolfm.career.CareerPerformancePolicy.*;
import com.lolfm.domain.Position;
import com.lolfm.simulator.TeamSide;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CareerPerformanceAwardsPolicyTest {
    static CareerGameStatistics game(boolean quiet,int supportCs) {
        var players=new ArrayList<CareerGameStatistics.Player>();for(var side:TeamSide.values())for(var position:Position.values())players.add(new CareerGameStatistics.Player(side+"_"+position,side,position,"test-champion",side.name(),quiet?0:2,quiet?0:2,quiet?0:5,position==Position.SUPPORT?supportCs:200,10000,14000,16));
        return new CareerGameStatistics(1,"a".repeat(64),1800,"BLUE","NEXUS_DESTROYED",players);
    }
    @Test void noOpportunityIsNeutralButQuietZeroDeathsDoNotAddASurvivalBonus() {
        var game=game(true,0);for(var p:game.players()){var r=rate(game,p);assertThat(r.kpStatus()).isEqualTo("NO_OPPORTUNITY");assertThat(r.combat()).isLessThan(decimal(50));assertThat(r.survival()).isEqualByComparingTo("50");assertThat(r.rating()).isLessThan(decimal(50));}
        var normal=game(false,0);var changed=game(false,900);
        for(var p:normal.players())if(p.position()==Position.SUPPORT)assertThat(rate(normal,p)).isEqualTo(rate(changed,changed.players().stream().filter(c->c.playerId().equals(p.playerId())).findFirst().orElseThrow()));
        assertThat(rate(new CareerGameStatistics(1,normal.outputHash(),0,"BLUE",normal.endReason(),normal.players()),normal.players().getFirst()).status()).isEqualTo("INPUT_INCOMPLETE");
    }
    @Test void seriesLengthDoesNotWeightFullParticipationAndShrinkageOccursOnlyOnce() {
        var sweep=new Sample(decimal(80),BigDecimal.ONE,true,2);var full=new Sample(decimal(80),BigDecimal.ONE,true,3);
        assertThat(period(List.of(sweep))).isEqualTo(period(List.of(full)));
        assertThat(series(List.of(decimal(80)),3)).isEqualByComparingTo("60");
        var partial=period(List.of(new Sample(decimal(80),div(BigDecimal.ONE,decimal(3)),true,1)));
        assertThat(partial.adjustedMean()).isEqualByComparingTo("51.578947368421");
        var weighted=period(List.of(new Sample(decimal(20),decimal(.1),false,1),new Sample(decimal(80),decimal(.9),true,9)));
        assertThat(weighted.consistency()).isEqualByComparingTo("50.666666666667");
    }
    @ParameterizedTest @CsvSource({"LCK_REGULAR,41,20,18,false","LCK_REGULAR,42,20,18,true","LEC_SPRING,5,5,7,false","LEC_SPRING,6,6,7,true","LCK_CL,18,8.999,18,false","LCK_CL,18,9,18,true","LPL_SPLIT_2,2,1,2,true","LPL_SPLIT_2,1,.5,2,false"})
    void minimumAdoptedUnits(String scope,int sets,double played,double planned,boolean accepted){assertThat(eligible(scope,sets,decimal(played),decimal(planned))).isEqualTo(accepted);}
    @ParameterizedTest @CsvSource({"25,2027,2027,false,false,true,FALSE","26,2027,2027,false,false,true,TRUE","26,2027,2028,false,false,true,TRUE","26,2027,2029,false,false,true,FALSE","26,2027,2027,false,false,false,UNKNOWN","26,2027,2027,true,false,false,FALSE","26,2027,2028,false,true,true,FALSE"})
    void rookieBoundariesDoNotInventMissingHistory(int sets,int first,int year,boolean foreign,boolean prior,boolean complete,String expected){assertThat(rookieEligible(sets,first,year,foreign,prior,complete)).isEqualTo(expected);}
    @Test void cupEliminationPlacesPreserveSharedEighthAndDoNotCallRegularWinnersChampions() {
        var outcomes=new TreeMap<String,List<String>>();String[] matches={"PO_FINAL","PO_LOWER_FINAL","PO_LBR3","PO_LBR2","PO_LBR1","PI_FINAL","PI_R1_M1","PI_R1_M2"};
        for(int i=0;i<matches.length;i++)outcomes.put(matches[i],List.of("team1","team"+(i+2)));
        var teams=new TreeSet<String>();for(int i=1;i<=10;i++)teams.add("team"+i);
        var places=CareerHistoryStore.cupPlacements(outcomes,teams);
        assertThat(places).hasSize(10).containsEntry("team1",1).containsEntry("team2",2).containsEntry("team3",3).containsEntry("team8",8).containsEntry("team9",8).containsEntry("team10",10);
        outcomes.remove("PO_FINAL");assertThat(CareerHistoryStore.cupPlacements(outcomes,teams)).isEmpty();
    }
    @Test void adoptionSlotsExclusionsAliasesAndCutoffsHaveOneMeaning() {
        assertThat(CareerAwardPolicy.DEFINITIONS).hasSize(87);
        assertThat(CareerAwardPolicy.DEFINITIONS.stream().filter(d->d.status().equals("DO_NOT_CREATE"))).hasSize(2);
        assertThat(CareerAwardPolicy.DEFINITIONS.stream().filter(d->d.scope().equals("FIRST_STAND")&&d.status().equals("ACTIVE_GAME_POLICY"))).hasSize(1).allMatch(d->d.period().equals("EVENT"));
        assertThat(CareerAwardPolicy.DEFINITIONS.stream().filter(d->d.scope().startsWith("LCP"))).noneMatch(d->d.status().equals("ACTIVE_GAME_POLICY"));
        assertThat(CareerAwardPolicy.DEFINITIONS).allMatch(d->!d.source().path("automaticGameExecutionEnabled").asBoolean());
        assertThat(CareerAwardPolicy.scope("LCK_REGULAR_R1_R2")).isEqualTo(CareerAwardPolicy.scope("LCK_REGULAR_R3_R4"));
        assertThat(CareerAwardPolicy.inScope("LCK_REGULAR","LCK_PLAYOFFS","LCK_PLAYOFFS")).isFalse();
        assertThat(CareerAwardPolicy.inScope("LCK_REGULAR","LCK_REGULAR_R3_R4","TIEBREAKER")).isFalse();
        assertThat(CareerAwardClosure.finalMatch("LCK_CUP","PI_FINAL")).isFalse();
        assertThat(CareerAwardClosure.finalMatch("LPL_REGIONAL_FINALS","RF_F")).isFalse();
        var candidate=new CareerAwardsStore.Candidate("p","name","team",Position.TOP,42,decimal(20),"TRUE","eligible",decimal(70),decimal(65),null,CareerAwardPolicy.tie(1,"award","p"));
        assertThat(CareerAwardsStore.slots(List.of(candidate),List.of(1,2,3),false)).hasSize(15).filteredOn(s->s.playerId()!=null).hasSize(1);
        assertThat(CareerAwardsStore.slots(List.of(candidate),List.of(1),false)).hasSize(5);
        assertThat(CareerAwardPolicy.tie(1,"23","4")).isNotEqualTo(CareerAwardPolicy.tie(12,"3","4"));
        assertThat(CareerAwardPolicy.tie(1,"award","p")).isEqualTo(CareerAwardPolicy.tie(1,"award","p"));
    }
}
