package com.lolfm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.draft.*;
import com.lolfm.simulator.*;
import com.lolfm.player.LckTeamAssembler;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"spring.main.lazy-initialization=true","logging.level.root=ERROR"})
class RealismPolicyCompatibilityTest {
    @Autowired RealDraftMatchOrchestrator matches;
    @Autowired MatchEngineV1 engine;
    @Autowired MatchEngineV1Canonicalizer canonicalizer;
    @Autowired PlayerControlledDraftEngine drafts;
    @Autowired PlayerControlledDraftMatchInputBoundary boundary;
    @Autowired LckTeamAssembler teams;
    @Autowired ObjectMapper json;
    @Test void newDefaultRunsRealismAndSerializedInputReplaysItsBoundPolicy() throws Exception {
        var newGame=matches.prepareV1(null,"GEN","T1",new SeriesDraftHistory(),91008011,
                SimulationInstrumentation.enabled());
        assertThat(newGame.input().productionPolicy()).isEqualTo(MatchEngineV1Policy.requirement());
        assertThat(newGame.output().productionPolicy().gameplayConfiguration().realismEnabled()).isTrue();
        assertThat(newGame.completedDraft().draftSelectionPolicyId()).isEqualTo("AUTO_DRAFT_ABILITY_V2");
        assertThat(newGame.output().timeline().events()).anySatisfy(e->assertThat(e.structuredData()).containsKey("upperObjective"));
        var restored=json.readValue(json.writeValueAsBytes(newGame.input()),MatchEngineV1Input.class);
        var replay=engine.execute(restored,SimulationInstrumentation.disabled());
        assertThat(replay.outputHash()).isEqualTo(newGame.output().outputHash());
        assertThat(replay.hasValidOutputHash(canonicalizer)).isTrue();
        assertThatThrownBy(()->new MatchEngineV1Input(restored.schemaVersion(),restored.matchIdentity(),restored.blueTeam(),restored.redTeam(),
                restored.championAssignments(),restored.finalDraft(),restored.matchSeed(),restored.rosterIdentityHash(),restored.seriesHistoryBeforeHash(),
                MatchEngineV1Policy.requirement(MatchEngineV1Policy.legacy()))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void ongoingLegacyPlayerDraftAndNewDraftKeepSeparateAiVersionsAndManualChoices() throws Exception {
        var blue=DraftTeamContext.from(teams.assemble("GEN"));var red=DraftTeamContext.from(teams.assemble("T1"));
        var selection=RealDraftSelectionContextFactory.create(901,"GEN",teams.assemble("GEN"),"T1",teams.assemble("T1"),1,Set.of());
        for(String policy:List.of(AutoDraftSelectionPolicy.POLICY_ID,AutoDraftSelectionPolicy.ability().policyId(),AutoDraftSelectionPolicy.abilityV2().policyId())) {
            var progress=drafts.forPolicy(policy).start(blue,red,selection,TeamSide.BLUE);
            // Old saves predate this additive field. Missing means verified legacy, including an empty first turn.
            var tree=json.valueToTree(progress);
            if(policy.equals(AutoDraftSelectionPolicy.POLICY_ID))((com.fasterxml.jackson.databind.node.ObjectNode)tree).remove("selectionPolicyId");
            progress=json.treeToValue(tree,PlayerControlledDraftEngine.Progress.class);
            var first=drafts.project(progress,blue,red).view().selectableChampionIds().getLast();
            progress=drafts.select(progress,blue,red,selection,first,"manual-first");
            assertThat(progress.state().blueBans()).contains(first);
            while(!progress.complete()) {
                var chosen=drafts.project(progress,blue,red).view().selectableChampionIds().getFirst();
                progress=drafts.select(progress,blue,red,selection,chosen,"manual-"+progress.state().nextTurnIndex());
            }
            drafts.validateCompleted(progress.result(),blue,red,selection);
            var input=boundary.validateAndCreateInput("GEN","T1",901,progress.result());
            assertThat(input.productionPolicy()).isEqualTo(MatchEngineV1Policy.forSelection(policy));
            assertThat(input.finalDraft().blueBans()).contains(first);
        }
    }
}
