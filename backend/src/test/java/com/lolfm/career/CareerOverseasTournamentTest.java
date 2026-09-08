package com.lolfm.career;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.lolfm.career.CareerOverseasRules.*;
import static com.lolfm.career.CareerOverseasTournament.*;

/** Controlled scores prove graph rules; these are not match-engine results. */
class CareerOverseasTournamentTest {
    static Input input(Event event){
        var teams=new ArrayList<>(partners(event.league));if(event==Event.LEC_VERSUS)teams.addAll(List.of("LEC:LR","LEC:KCB"));
        if(event==Event.AMERICAS_CUP)teams=new ArrayList<>(List.of("LCS:C9","LCS:FLY","CBLOL:PNG","CBLOL:LOUD"));
        teams=new ArrayList<>(teams.subList(0,event.count));var groups=new TreeMap<String,List<String>>();
        if(event==Event.LPL_SPLIT_1){groups.put("ASCEND",teams.subList(0,6));groups.put("PERSEVERANCE",teams.subList(6,10));groups.put("NIRVANA",teams.subList(10,14));}
        if(event==Event.LPL_SPLIT_2||event==Event.LPL_SPLIT_3){groups.put("ASCEND",teams.subList(0,8));groups.put("NIRVANA",teams.subList(8,teams.size()));}
        return new Input(event,2027,491L,teams,groups,Map.of(),Map.of(),4);
    }
    static Plan finish(Input input,Map<String,Score> scores){for(int turn=0;turn<50;turn++){var p=project(input,scores);if(p.complete())return p;boolean added=false;for(var b:p.bouts())if(!scores.containsKey(b.id())){int win=b.bestOf()/2+1,lose=b.bestOf()==1?0:Math.floorMod(b.id().hashCode(),win);boolean first=input.entrants().indexOf(b.first())<input.entrants().indexOf(b.second());scores.put(b.id(),new Score(first?win:lose,first?lose:win));added=true;}assertThat(added).as("graph must progress: %s",input.event()).isTrue();}throw new AssertionError("finite graph budget: "+input.event());}
    @ParameterizedTest @EnumSource(Event.class)
    void allSeventeenGraphsTerminateWithExactCountsAndStableIdentity(Event event){
        var input=input(event);var scores=new LinkedHashMap<String,Score>();var p=finish(input,scores);
        assertThat(p.ranking()).containsExactlyInAnyOrderElementsOf(input.entrants());assertThat(new HashSet<>(p.ranking())).hasSize(input.entrants().size());assertThat(p.placements().values()).contains(1,2);
        int rr=switch(event){case LPL_SPLIT_1->54;case LPL_SPLIT_2->71;case LPL_SPLIT_3->68;case LEC_VERSUS->66;case LEC_SPRING,LEC_SUMMER->45;case LPL_REGIONAL_FINALS,LCS_LOCK_IN,LCP_SPLIT_3,AMERICAS_CUP->0;default->28;};
        assertThat(p.bouts().stream().filter(b->b.stage().equals("REGULAR"))).hasSize(rr);
        int po=switch(event){case LPL_SPLIT_1,LPL_SPLIT_2,LEC_VERSUS->14;case LPL_SPLIT_3->12;case CBLOL_COPA,CBLOL_ETAPA_1,CBLOL_ETAPA_2,LCS_SUMMER->10;case LCP_SPLIT_3,AMERICAS_CUP->6;case LPL_REGIONAL_FINALS->0;default->8;};
        assertThat(p.bouts().stream().filter(b->b.stage().equals("PLAYOFFS"))).hasSize(po);
        if(event.league.equals("LPL")&&event!=Event.LPL_REGIONAL_FINALS)assertThat(p.bouts().stream().filter(b->b.stage().equals("KNIGHTS"))).hasSize(event==Event.LPL_SPLIT_1?6:event==Event.LPL_SPLIT_2?4:2);
        var dates=new HashSet<String>();for(var b:p.bouts()){assertThat(dates.add(b.first()+"|"+b.date())).isTrue();assertThat(dates.add(b.second()+"|"+b.date())).isTrue();assertThat(b.date()).isBeforeOrEqualTo(event.date(input.year(),event.end).plusDays(SCHEDULE_EXTENSION_DAYS));}
        var reversed=new LinkedHashMap<String,Score>();scores.entrySet().stream().sorted(Map.Entry.<String,Score>comparingByKey().reversed()).forEach(e->reversed.put(e.getKey(),e.getValue()));assertThat(project(input,reversed)).isEqualTo(p);
        assertThat(p.bouts()).extracting(Bout::id).doesNotHaveDuplicates();
    }
    @Test void hybridEntryAndMixedFormatsRetainTheirOwnRules(){
        for(Event e:List.of(Event.LCP_SPLIT_1,Event.LCP_SPLIT_2)){var p=finish(input(e),new TreeMap<>());assertThat(p.bouts().stream().filter(b->b.id().startsWith("ENTRY_"))).hasSize(2);assertThat(p.playoffTeams()).hasSize(6);}
        var copa=finish(input(Event.CBLOL_COPA),new TreeMap<>());assertThat(copa.bouts().stream().filter(b->b.stage().equals("PLAY_IN")).map(Bout::bestOf)).containsExactly(3,3,5);
        assertThat(copa.bouts().stream().filter(b->b.id().startsWith("PO_Q")||b.id().startsWith("PO_S")).map(Bout::bestOf)).containsOnly(3);
        assertThat(finish(input(Event.AMERICAS_CUP),new TreeMap<>()).entitlements()).containsOnlyKeys("KOREA_BOOTCAMP_SUPPORT");
        assertThat(finish(input(Event.AMERICAS_CUP),new TreeMap<>()).bouts().stream().map(Bout::bestOf)).containsExactly(3,3,5,5,5,5);
        var lock=finish(input(Event.LCS_LOCK_IN),new TreeMap<>());assertThat(lock.bouts().stream().filter(b->b.stage().equals("SWISS"))).hasSize(12);assertThat(lock.bouts().stream().filter(b->b.stage().equals("LAST_CHANCE")).map(Bout::bestOf)).containsExactly(LAST_CHANCE_BO);
    }
    @Test void pacificPointsFloorOnlyTheGameComponentAndSwissUsesDecidingBo5(){
        assertThat(lcpRegularPoints(Event.LCP_SPLIT_2,3,2,5)).isEqualTo(5);assertThat(lcpRegularPoints(Event.LCP_SPLIT_2,1,10,4)).isEqualTo(19);assertThat(lcpRegularPoints(Event.LCP_SPLIT_1,1,10,4)).isEqualTo(13);
        var scores=new TreeMap<String,Score>();var p=finish(input(Event.LCP_SPLIT_3),scores);var wins=new TreeMap<String,Integer>();var losses=new TreeMap<String,Integer>();
        for(var b:p.bouts().stream().filter(b->b.stage().equals("SWISS")).toList()){boolean deciding=List.of(b.first(),b.second()).stream().anyMatch(t->wins.getOrDefault(t,0)==2||losses.getOrDefault(t,0)==2);assertThat(b.bestOf()).isEqualTo(deciding?5:3);var s=scores.get(b.id());wins.merge(s.first()>s.second()?b.first():b.second(),1,Integer::sum);losses.merge(s.first()>s.second()?b.second():b.first(),1,Integer::sum);}
        assertThat(wins.values().stream().filter(w->w==3)).hasSize(4);assertThat(p.playoffTeams()).hasSize(4);
        String third=p.placements().entrySet().stream().filter(e->e.getValue()==3).findFirst().orElseThrow().getKey();assertThat(p.points().get(third)).isEqualTo(lcpSwissPoints(3,losses.getOrDefault(third,0))+15);
    }
    @Test void regionalFinalsThreeSlotPolicyHasOneWinningPath(){var source=input(Event.LPL_REGIONAL_FINALS);var p=finish(new Input(source.event(),source.year(),source.seed(),source.entrants(),source.groups(),source.previousRanks(),source.priorPoints(),3),new TreeMap<>());assertThat(p.bouts()).hasSize(3);assertThat(p.placements().values().stream().filter(v->v==1)).hasSize(1);assertThat(p.bouts()).extracting(Bout::id).containsExactly("RF_1","RF_2","RF_F");}
}
