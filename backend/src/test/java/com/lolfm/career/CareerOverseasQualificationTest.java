package com.lolfm.career;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.lolfm.career.CareerOverseasRules.*;
import static com.lolfm.career.CareerOverseasTournament.*;

class CareerOverseasQualificationTest {
    private static CareerOverseasStore.State finished(Event event){var input=CareerOverseasTournamentTest.input(event);return new CareerOverseasStore.State(VERSION,SOURCE_HASH,input,CareerOverseasTournamentTest.finish(input,new TreeMap<>()));}
    @Test void pacificActualChampionAndPointQualifierNeverDuplicateAndIgnoreRatingOrder(){
        for(var event:List.of(Event.LCP_SPLIT_2,Event.LCP_SPLIT_3)){
            var state=finished(event);var rank=state.plan().ranking();var cp=Map.of(rank.getFirst(),999,rank.get(5),800,rank.get(6),700);
            var order=CareerOverseasQualification.order(event==Event.LCP_SPLIT_2?"MSI":"WORLDS",state,null,cp);
            assertThat(order.teams()).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(state.input().entrants());
            int direct=event==Event.LCP_SPLIT_2?1:2;assertThat(order.teams().subList(0,direct)).containsExactlyElementsOf(rank.subList(0,direct));
            assertThat(order.teams().get(direct)).isEqualTo(rank.get(5));assertThat(order.paths().get(rank.get(5))).contains("HIGHEST_CP_EXCLUDING");
            assertThat(order.playoffEligible()).containsExactlyInAnyOrderElementsOf(state.plan().playoffTeams());
        }
        assertThat(CareerOverseasQualification.required("EWC_LOL")).doesNotContain(Event.LCP_SPLIT_3,Event.LEC_SUMMER,Event.LPL_REGIONAL_FINALS);
        assertThat(CareerOverseasQualification.required("MSI")).doesNotContain(Event.AMERICAS_CUP);
    }
    @Test void lplFourteenPartnersReturnAfterActualSplitEliminationWithNoFixedOmgUpExclusion(){
        var first=finished(Event.LPL_SPLIT_1);assertThat(first.input().entrants()).hasSize(14);
        var teams=new ArrayList<>(first.plan().ranking());teams.removeAll(List.of("LPL:OMG","LPL:UP"));teams.addFirst("LPL:UP");teams.addFirst("LPL:OMG");
        var secondTeams=lplEntrants(Event.LPL_SPLIT_2,teams,first.plan().seasonEliminated());assertThat(secondTeams).hasSize(14);
        var secondInput=new Input(Event.LPL_SPLIT_2,2027,51,secondTeams,Map.of("ASCEND",secondTeams.subList(0,8),"NIRVANA",secondTeams.subList(8,14)),Map.of(),first.plan().points(),4);
        var second=CareerOverseasTournamentTest.finish(secondInput,new TreeMap<>());
        assertThat(second.seasonEliminated()).hasSize(2).doesNotContain("LPL:OMG","LPL:UP");
        var thirdTeams=lplEntrants(Event.LPL_SPLIT_3,second.ranking(),second.seasonEliminated());assertThat(thirdTeams).hasSize(12).contains("LPL:OMG","LPL:UP").doesNotContainAnyElementsOf(second.seasonEliminated());
        assertThat(lplEntrants(Event.LPL_SPLIT_1,thirdTeams,second.seasonEliminated())).hasSize(14).containsAll(second.seasonEliminated());
    }
    @Test void lplWorldsSeedsKeepChampionThenChampionshipPointsThenRegionalFinals(){
        var split=finished(Event.LPL_SPLIT_3);var rank=split.plan().ranking();var cp=new TreeMap<String,Integer>();
        cp.put(rank.getFirst(),900);cp.put(rank.get(7),500);cp.put(rank.get(5),200);cp.put(rank.get(6),200);
        var candidates=new ArrayList<>(CareerOverseasStore.cpOrder(split,cp));candidates.remove(rank.getFirst());assertThat(candidates.removeFirst()).isEqualTo(rank.get(7));
        var input=new Input(Event.LPL_REGIONAL_FINALS,2027,4,candidates.subList(0,4),Map.of(),Map.of(),cp,4);
        var rf=new CareerOverseasStore.State(VERSION,SOURCE_HASH,input,CareerOverseasTournamentTest.finish(input,new TreeMap<>()));
        var order=CareerOverseasQualification.order("WORLDS",rf,split,cp);
        assertThat(order.teams().subList(0,4)).containsExactly(rank.getFirst(),rank.get(7),rf.plan().ranking().get(0),rf.plan().ranking().get(1));
        assertThat(order.teams()).doesNotHaveDuplicates();assertThat(order.paths().get(rank.get(7))).contains("HIGHEST_CP");
        assertThat(CareerOverseasStore.cpOrder(split,Map.of(rank.get(5),100,rank.get(6),100)).getFirst()).isEqualTo(rank.get(5));
    }
    @Test void eachCompletePrizeDistributionAcceptsTheActualGraphClassification(){
        var rules=CareerFinanceReference.rules(CareerFinanceReference.json());
        for(var event:Event.values()){
            var rule=rules.get(event.reference());if(event==Event.LPL_REGIONAL_FINALS){assertThat(rule.complete()).isFalse();continue;}
            var state=finished(event);long total=0;
            for(var rank:CareerFinanceStore.ranks(state.plan().placements()).values())total+=CareerFinancePolicy.placement(rule,rank.from(),rank.through(),false);
            long expected=CareerFinanceReference.json().path("prizes").findValues("eventId").size();
            var source=CareerFinanceReference.json().path("prizes");for(var row:source)if(row.path("eventId").asText().equals(event.reference()))expected=row.path("teamPrizePool").asLong();
            assertThat(total).as(event.name()).isEqualTo(expected);
        }
    }
}
