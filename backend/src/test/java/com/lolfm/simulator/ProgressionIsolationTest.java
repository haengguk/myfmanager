package com.lolfm.simulator;
import static org.assertj.core.api.Assertions.*;
import com.lolfm.domain.*;
import com.lolfm.factory.DummyDataFactory;
import java.util.*;
import org.junit.jupiter.api.Test;
class ProgressionIsolationTest {
 private MatchSimulator sim(SimulationOptions o){return new MatchSimulator(new TeamfightResolver(),new EndGameEvaluator(),new SnapshotFactory(),new ObjectiveResolver(),new PostFightResolver(),new ObjectiveAttemptResolver(),new StructureResolver(),new PushResolver(),o);}
 private MatchTimeline run(SimulationOptions o,int seed){DummyDataFactory f=new DummyDataFactory();return sim(o).simulate(f.createBlueTeam(),f.createRedTeam(),seed);}
 private List<String> gameplay(MatchTimeline t){return t.getEvents().stream().filter(e->e.getType()!=MatchEventType.LEVEL_UP&&e.getType()!=MatchEventType.ITEM_STAGE_REACHED).map(e->e.getTimeSeconds()+"|"+e.getType()+"|"+e.getMessage()).toList();}
 @Test void stateOnlyPreservesLegacyGameplayRandomPath(){var off=run(SimulationOptions.productionDefaults().withProgressionEnabled(false),19);var state=run(SimulationOptions.productionDefaults().withProgressionPowerEnabled(false),19);assertThat(gameplay(state)).containsExactlyElementsOf(gameplay(off));assertThat(state.getWinner()).isEqualTo(off.getWinner());assertThat(state.getDurationSeconds()).isEqualTo(off.getDurationSeconds());}
 @Test void diagnosticsInstrumentationDoesNotChangeTimeline(){var a=run(SimulationOptions.productionDefaults().withDiagnosticsEnabled(false),23);var b=run(SimulationOptions.productionDefaults().withDiagnosticsEnabled(true),23);assertThat(a).usingRecursiveComparison().isEqualTo(b);}
 @Test void progressionOffEmitsNoProgressionEventsAndNeutralSnapshots(){var t=run(SimulationOptions.productionDefaults().withProgressionEnabled(false),7);assertThat(t.getEvents()).noneMatch(e->e.getProgressionEvent()!=null);assertThat(t.getSnapshots()).allMatch(s->!s.getProgression().enabled()&&s.getPlayerSnapshots().stream().allMatch(p->p.getLevel()==1&&p.getTotalExperience()==0&&p.getItemStage()==ItemProgressStage.STARTING));}
 @Test void productionDefaultsEnableStateAndPower(){assertThat(SimulationOptions.productionDefaults().progressionEnabled()).isTrue();assertThat(SimulationOptions.productionDefaults().progressionPowerEnabled()).isTrue();}
}
