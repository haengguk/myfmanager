package com.lolfm.career;
import static org.assertj.core.api.Assertions.*;
import static com.lolfm.career.CareerPerformancePolicy.*;
import com.lolfm.application.MatchEngineV1Output;
import com.lolfm.domain.*;
import com.lolfm.simulator.TeamSide;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class CareerPerformanceV2Test {
    static CareerGameStatistics game(){return CareerPerformanceAwardsPolicyTest.game(false,0);}
    static CareerGameStatistics.Player support(CareerGameStatistics g){return g.players().stream().filter(p->p.side()==TeamSide.BLUE&&p.position()==Position.SUPPORT).findFirst().orElseThrow();}
    static MatchEngineV1Output.EventV1 event(String id,MatchEventType type,Map<String,Object> data){return new MatchEngineV1Output.EventV1(100,type,null,null,null,null,null,null,List.of(),null,null,List.of(),null,null,null,null,null,null,0,0,id,null,"표시 문구는 근거가 아님",data);}
    static Map<String,Object> objective(List<String> ids){return Map.of("objectiveFight",Map.of("winningSide","BLUE","participantPlayerIds",ids));}
    static CareerGameStatistics actions(CareerGameStatistics g,boolean included){var ids=g.players().stream().filter(p->included||!p.playerId().equals(support(g).playerId())).map(CareerGameStatistics.Player::playerId).toList();return g.withTeamPlay(CareerTeamPlayStatistics.collect(g,List.of(event("action",MatchEventType.TEAMFIGHT_RESULT,objective(ids)))));}
    @Test void actualParticipantsDeduplicateSummaryKillAndRepeatedResultAndRejectBench() {
        var g=game();var ids=List.of(support(g).playerId(),"RED_TOP");var data=objective(ids);
        var event=event("same",MatchEventType.TEAMFIGHT_RESULT,data);
        var observation=CareerTeamPlayStatistics.collect(g,List.of(event("same",MatchEventType.TEAMFIGHT,data),event,event("same",MatchEventType.KILL,data),event));
        assertThat(observation.actions()).hasSize(1);assertThat(observation.observation(support(g)).objectiveParticipations()).isOne();
        assertThat(observation.observation(g.players().getFirst()).objectiveParticipations()).isZero();
        assertThatThrownBy(()->CareerTeamPlayStatistics.collect(g,List.of(event("bench",MatchEventType.TEAMFIGHT_RESULT,objective(List.of("BENCH")))))).hasMessage("TEAM_PLAY_PARTICIPANT_MISMATCH");
        assertThatThrownBy(()->new CareerTeamPlayStatistics("wrong",observation.actions()).validate(g)).hasMessage("TEAM_PLAY_OUTPUT_MISMATCH");
        // Death after entry does not erase a fact recorded before the kill, nor fabricate sacrifice credit.
        assertThat(support(g).deaths()).isPositive();assertThat(observation.observation(support(g)).objectiveWins()).isOne();
    }
    @Test void identicalKdaDifferentActionsLowKillsAndMissingAreDistinct() {
        var g=game();var p=support(g);var high=CareerPerformanceV2.rate(actions(g,true),p);var absent=CareerPerformanceV2.rate(actions(g,false),p);
        assertThat(high.support().score()).isEqualByComparingTo("100");assertThat(absent.support().score()).isZero();assertThat(high.rating()).isGreaterThan(absent.rating());
        var missing=CareerPerformanceV2.rate(g,p);assertThat(missing.support().status()).isEqualTo("NOT_COLLECTED");assertThat(missing.support().score()).isNull();
        var none=g.withTeamPlay(new CareerTeamPlayStatistics(g.outputHash(),List.of()));assertThat(CareerPerformanceV2.rate(none,p).support().status()).isEqualTo("NO_OPPORTUNITY");
        var quiet=CareerPerformanceAwardsPolicyTest.game(true,0);var active=CareerPerformanceV2.rate(actions(quiet,true),support(quiet));var idle=CareerPerformanceV2.rate(quiet,support(quiet));
        assertThat(active.rating()).isGreaterThan(high.combat());assertThat(active.rating()).isGreaterThan(idle.rating());assertThat(idle.rating()).isEqualByComparingTo("50");assertThat(idle.survival()).isEqualByComparingTo("50");
        assertThat(CareerPerformanceV2.rate(actions(g,true),p)).isEqualTo(high); // no mutable collector state
    }
    @Test void timeAndRepeatedProportionalActionsDoNotIncreaseRatings() {
        var g=actions(game(),true);var p=support(g);var longer=new CareerGameStatistics(1,g.outputHash(),g.seconds()*2,g.winner(),g.endReason(),g.players(),g.teamPlay());
        for(var player:g.players())assertThat(CareerPerformanceV2.rate(longer,player)).isEqualTo(CareerPerformanceV2.rate(g,player));
        var a=g.teamPlay().actions().getFirst();var twice=g.withTeamPlay(new CareerTeamPlayStatistics(g.outputHash(),List.of(a,new CareerTeamPlayStatistics.Action("second",a.kind(),a.side(),a.winner(),a.participants()))));
        assertThat(CareerPerformanceV2.rate(twice,p).rating()).isEqualByComparingTo(CareerPerformanceV2.rate(g,p).rating());
        assertThat(CareerRosterStore.write(g.base())).doesNotContain("teamPlay");
    }
    @Test void singleSeriesUsesObservedMeanAndExplicitPartialAppearanceQualification() {
        assertThat(CareerPerformanceV2.seriesEligible(1,1)).isTrue();assertThat(CareerPerformanceV2.seriesEligible(1,2)).isFalse();assertThat(CareerPerformanceV2.seriesEligible(2,2)).isTrue();assertThat(CareerPerformanceV2.seriesEligible(2,3)).isTrue();assertThat(CareerPerformanceV2.seriesEligible(2,5)).isFalse();assertThat(CareerPerformanceV2.seriesEligible(3,5)).isTrue();
        var db=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        org.mockito.Mockito.when(db.queryForObject("SELECT career_root_seed FROM career_save WHERE career_id=?",Long.class,"career")).thenReturn(17L);
        var source=actions(game(),true);var games=new ArrayList<CareerRecordsStore.Game>();
        for(int number=1;number<=3;number++) {
            var rows=new ArrayList<CareerRecordsStore.PlayerGame>();
            for(var p:source.players()) {
                String id=p.playerId().equals(support(source).playerId())&&number>1?"replacement":p.playerId();
                var stats=new CareerGameStatistics.Player(id,p.side(),p.position(),p.championId(),p.team(),p.kills(),p.deaths(),p.assists(),p.cs(),p.gold(),p.experience(),p.level());
                rows.add(new CareerRecordsStore.PlayerGame(number,id,id,p.team(),p.team(),p.team(),stats,CareerPerformanceV2.rate(source,p),p.side()==TeamSide.BLUE));
            }
            games.add(new CareerRecordsStore.Game(number,source.outputHash(),source.seconds(),"BLUE",source.endReason(),"COMPLETE",rows));
        }
        var series=new CareerRecordsStore.Series("record","career",2028,"origin","FIRST_STAND","FINAL","F","series",java.time.LocalDate.of(2028,3,1),"receipt","BLUE","BLUE","RED","COMPLETE",games);
        var candidates=CareerAwardsStore.seriesCandidates(db,series,"FINALS_MVP","CHAMPIONSHIP",true);
        assertThat(candidates).filteredOn(c->c.playerId().equals(support(source).playerId())).singleElement().satisfies(c->{assertThat(c.eligibility()).isEqualTo("FALSE");assertThat(c.eligibilityReason()).contains("1/3세트","미충족");});
        assertThat(candidates).filteredOn(c->c.playerId().equals("replacement")).singleElement().satisfies(c->{assertThat(c.eligibility()).isEqualTo("TRUE");assertThat(c.score()).isEqualByComparingTo(CareerPerformanceV2.rate(source,support(source)).rating());});
    }
    static List<Sample> samples(int n,int wins,int sets){var result=new ArrayList<Sample>();for(int i=0;i<n;i++)result.add(new Sample(decimal(80),BigDecimal.ONE,i<wins,sets));return result;}
    @Test void periodRanksSeparateConfidenceAndVolumeAcrossFourEightSixteenAndBoLengths() {
        var four=CareerPerformanceV2.period(samples(4,4,3));assertThat(period(samples(4,4,3)).tournamentMvp()).isEqualByComparingTo("65.8");assertThat(period(samples(8,6,3)).tournamentMvp()).isEqualByComparingTo("67.928571428571");
        assertThat(four.tournamentMvp()).isEqualByComparingTo("82");assertThat(CareerPerformanceV2.period(samples(8,6,3)).tournamentMvp()).isEqualByComparingTo("79.5");
        for(int n:List.of(8,16)){var longer=CareerPerformanceV2.period(samples(n,n,5));assertThat(longer.tournamentMvp()).isEqualTo(four.tournamentMvp());assertThat(longer.allPro()).isEqualTo(four.allPro());assertThat(longer.regularMvp()).isEqualTo(four.regularMvp());assertThat(longer.consistency()).isEqualTo(four.consistency());assertThat(longer.adjustedMean()).isNotEqualTo(four.adjustedMean());}
        var shortCandidate=new CareerAwardsStore.Candidate("p","name","team",Position.TOP,12,decimal(4),"TRUE","ok",four.tournamentMvp(),four.observedMean(),four,"same-tie");
        var longCandidate=new CareerAwardsStore.Candidate("p","renamed","renamed",Position.TOP,48,decimal(16),"TRUE","ok",four.tournamentMvp(),four.observedMean(),CareerPerformanceV2.period(samples(16,16,3)),"same-tie");
        assertThat(CareerAwardsStore.order().compare(shortCandidate,longCandidate)).isZero();assertThat(CareerAwardsStore.reason(List.of(shortCandidate,longCandidate),0)).doesNotContain("참여 우위");
        var distribution=List.of(new Sample(decimal(20),decimal(.5),false,1),new Sample(decimal(80),BigDecimal.ONE,true,3));var doubled=new ArrayList<>(distribution);doubled.addAll(distribution);
        assertThat(CareerPerformanceV2.period(distribution).allPro()).isEqualTo(CareerPerformanceV2.period(doubled).allPro());
        var improved=new ArrayList<>(samples(4,4,3));improved.add(new Sample(decimal(100),BigDecimal.ONE,true,3));assertThat(CareerPerformanceV2.period(improved).observedMean()).isGreaterThan(four.observedMean());
        assertThat(CareerPerformanceV2.eventEligible(1,BigDecimal.ONE,BigDecimal.ONE)).isFalse();assertThat(CareerPerformanceV2.eventEligible(2,decimal(2),decimal(2))).isTrue();assertThat(CareerPerformanceV2.eventEligible(2,decimal(2),decimal(5))).isFalse();
    }
}
