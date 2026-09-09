package com.lolfm.career;

import static com.lolfm.career.CareerRosterStore.*;
import static com.lolfm.career.CareerPerformancePolicy.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Closes only the affected competition. Dynamic fixtures are materialized by the existing result graph first. */
final class CareerAwardClosure {
    record Fixture(String event,String stage,String match,String series,LocalDate date,String first,String second,boolean completed) {}
    static List<Fixture> fixtures(JdbcTemplate db,String career,int year,String scope) {
        var result=new ArrayList<>(db.query("SELECT competition_id,stage_id,match_id,series_id,scheduled_date,first_team_code,second_team_code,lifecycle_status FROM career_competition_fixture WHERE career_id=? AND calendar_season_year=? AND (competition_id=? OR (?='LCK_REGULAR' AND competition_id='LCK_REGULAR_R3_R4')) ORDER BY scheduled_date,fixture_id",
                (r,n)->new Fixture(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getObject(5,LocalDate.class),CareerRecordsStore.team(r.getString(6),r.getString(1)),CareerRecordsStore.team(r.getString(7),r.getString(1)),"COMPLETED".equals(r.getString(8))),career,year,scope,scope));
        if(scope.equals("LCK_REGULAR")) {
            var dates=CareerRecordsStore.roundDates(year);
            result.addAll(db.query("SELECT f.fixture_id,f.bound_series_id,f.round_number,f.first_team_code,f.second_team_code,f.lifecycle_status FROM league_fixture f JOIN career_season s ON s.season_id=f.season_id WHERE s.career_id=? AND s.season_year=?",(r,n)->new Fixture("LCK_REGULAR_R1_R2","R1_R2",r.getString(1),r.getString(2),dates.get(r.getInt(3)),"LCK:"+r.getString(4),"LCK:"+r.getString(5),"COMPLETED".equals(r.getString(6))),career,year));
        }
        return result;
    }
    static boolean finalMatch(String event,String match) {
        return switch(event){case "LCK_CUP"->match.equals("PO_FINAL");case "LCK_PLAYOFFS"->match.equals("PO_F");case "LCK_CL"->match.equals("CL_FINAL");case "FIRST_STAND","MSI","WORLDS","EWC_LOL"->match.equals("F");default->CareerOverseasRules.isOverseas(event)&&!event.equals("LPL_REGIONAL_FINALS")&&match.equals("PO_F");};
    }
    static void close(JdbcTemplate db,String career,int year,String event,String match) {
        String scope=CareerAwardPolicy.scope(event);long revision=CareerRecordsStore.revision(db,career);
        var definitions=CareerAwardPolicy.DEFINITIONS.stream().filter(d->d.status().equals("ACTIVE_GAME_POLICY")&&d.scope().equals(scope)).toList();
        var all=fixtures(db,career,year,scope);var regular=all.stream().filter(f->CareerAwardPolicy.regular(f.event(),f.stage())).toList();
        if(definitions.stream().anyMatch(d->d.period().equals("REGULAR")&&!exists(db,career,year,d,"REGULAR"))&&!regular.isEmpty()&&regular.stream().allMatch(Fixture::completed)&&(!scope.equals("LCK_REGULAR")||regular.stream().anyMatch(f->f.event().equals("LCK_REGULAR_R3_R4")))) {
            var series=CareerRecordsStore.period(db,career,year,scope,revision);boolean complete=series.size()==regular.size()&&CareerAwardsStore.complete(series);
            LocalDate cutoff=regular.stream().map(Fixture::date).max(Comparator.naturalOrder()).orElseThrow();
            for(var d:definitions)if(d.period().equals("REGULAR")&&!exists(db,career,year,d,"REGULAR")) {
                var candidates=complete?periodCandidates(db,career,year,d,series,regular,"REGULAR"):List.<CareerAwardsStore.Candidate>of();
                CareerAwardsStore.save(db,career,year,d.id(),d.name(),scope,"REGULAR",revision,cutoff,series,List.of("REGULAR"),false,candidates,complete,d);
            }
        }
        if(finalMatch(event,match)) {
            var finalFixture=all.stream().filter(f->f.match().equals(match)&&f.completed()).findFirst();if(finalFixture.isEmpty())return;
            var series=db.query("SELECT record_json,record_hash FROM career_record_series WHERE career_id=? AND season_year=? AND competition_id=? AND record_revision<=? ORDER BY played_date,record_revision",(r,n)->CareerRecordsStore.readSeries(r.getString(1),r.getString(2)),career,year,event,revision);
            var actualFinal=series.stream().filter(s->s.seriesId().equals(finalFixture.get().series())).findFirst();
            for(var d:definitions)if(Set.of("FINAL","EVENT").contains(d.period())&&!exists(db,career,year,d,"CHAMPIONSHIP")) {
                var inputs=d.period().equals("FINAL")?actualFinal.map(List::of).orElse(List.of()):series;
                var candidates=d.period().equals("FINAL")?actualFinal.map(s->CareerAwardsStore.seriesCandidates(db,s,d.id(),"CHAMPIONSHIP",true)).orElse(List.of()):periodCandidates(db,career,year,d,series,all,"CHAMPIONSHIP");
                boolean complete=CareerAwardsStore.complete(inputs)&&(d.period().equals("FINAL")||series.size()==all.stream().filter(Fixture::completed).count());
                CareerAwardsStore.save(db,career,year,d.id(),d.name(),scope,"CHAMPIONSHIP",revision,finalFixture.get().date(),inputs,List.of(d.period()),false,candidates,complete,d);
            }
            CareerHistoryStore.championship(db,career,year,event,finalFixture.get(),series,revision);
        }
    }
    static boolean exists(JdbcTemplate db,String career,int year,CareerAwardPolicy.Definition d,String occurrence){return db.queryForObject("SELECT COUNT(*) FROM career_record_award WHERE instance_id=?",Integer.class,CareerAwardPolicy.instance(career,year,d.id(),d.scope(),occurrence))>0;}
    static List<CareerAwardsStore.Candidate> periodCandidates(JdbcTemplate db,String career,int year,CareerAwardPolicy.Definition d,List<CareerRecordsStore.Series> series,List<Fixture> fixtures,String occurrence) {
        if(!CareerAwardsStore.complete(series))return List.of();
        var rows=new TreeMap<String,List<CareerRecordsStore.PlayerGame>>();for(var s:series)for(var g:s.games())for(var p:g.players())rows.computeIfAbsent(p.playerId(),k->new ArrayList<>()).add(p);
        var candidates=new ArrayList<CareerAwardsStore.Candidate>();long seed=CareerAwardsStore.seed(db,career);String instance=CareerAwardPolicy.instance(career,year,d.id(),d.scope(),occurrence);
        var market=CareerMarketStore.load(db,career);
        for(var entry:rows.entrySet()) {
            String player=entry.getKey();var played=entry.getValue();var last=played.getLast();
            var roles=new EnumMap<com.lolfm.domain.Position,Integer>(com.lolfm.domain.Position.class);played.forEach(p->roles.merge(p.statistics().position(),1,Integer::sum));
            var role=CareerAwardPolicy.positions().stream().max(Comparator.comparingInt((com.lolfm.domain.Position p)->roles.getOrDefault(p,0)).thenComparingInt(p->-CareerAwardPolicy.positions().indexOf(p))).orElseThrow();
            var samples=new ArrayList<Sample>();boolean missing=false;int sets=0;
            for(var s:series){var actual=s.games().stream().flatMap(g->g.players().stream()).filter(p->p.playerId().equals(player)&&(!d.category().equals("ALL_PRO")||p.statistics().position()==role)).toList();if(actual.isEmpty())continue;
                sets+=actual.size();if(actual.stream().anyMatch(p->p.evaluation().rating()==null)){missing=true;continue;}
                samples.add(new Sample(div(actual.stream().map(p->p.evaluation().rating()).reduce(BigDecimal.ZERO,BigDecimal::add),decimal(actual.size())),div(decimal(actual.size()),decimal(s.games().size())),s.winner().equals(actual.getFirst().team()),actual.size()));
            }
            if(samples.isEmpty())continue;var summary=period(samples);String eligibility="TRUE",reason="평가 범위 실제 출전자";
            if(missing){eligibility="UNKNOWN";reason="필수 경기 성적 누락";}
            else if(Set.of("REGULAR_MVP","ALL_PRO").contains(d.category())) {
                BigDecimal denominator=opportunities(db,career,year,d.scope(),player,fixtures,market);
                if(denominator==null){eligibility="UNKNOWN";reason="당시 재직·출전 기회를 확인할 자료가 부족합니다.";}
                else if(!eligible(d.scope(),played.size(),summary.effectiveSeries(),denominator)){eligibility="FALSE";reason="최소 출전 조건 미충족 · "+d.eligibility();}
                else reason=d.eligibility()+" · 예정 기회 "+denominator+" / 유효 출전 "+summary.effectiveSeries();
            }else if(d.category().equals("ROOKIE")) {
                eligibility=rookie(db,career,year,player,played.size());reason="LCK 최초 등록 후 두 시즌 이내·26세트·이전 후보/해외 등록 이력 · "+eligibility;
            }
            BigDecimal score=switch(d.category()){case "ALL_PRO"->summary.allPro();case "REGULAR_MVP","ROOKIE"->summary.regularMvp();case "EVENT_MVP"->summary.tournamentMvp();case "MOST_FEARLESS"->decimal(played.stream().map(p->p.statistics().championId()).distinct().count());case "MOST_MATCH_MVP"->decimal(pomCount(db,career,year,player));default->throw new IllegalStateException("AWARD_CATEGORY_UNSUPPORTED");};
            if(d.category().equals("MOST_MATCH_MVP")&&score.signum()==0){eligibility="FALSE";reason="확정된 POM 수상 없음";}
            candidates.add(new CareerAwardsStore.Candidate(player,last.name(),last.team(),role,sets,summary.effectiveSeries(),eligibility,reason,score,summary.adjustedMean(),summary,CareerAwardPolicy.tie(seed,instance,player)));
        }return candidates;
    }
    static BigDecimal opportunities(JdbcTemplate db,String career,int year,String scope,String player,List<Fixture> fixtures,CareerMarketStore.Saved market) {
        if(scope.equals("LCK_REGULAR")||scope.startsWith("LEC_"))return BigDecimal.ZERO;
        if(scope.equals("LCK_CL")) {
            var counts=new HashMap<String,Integer>();for(var f:fixtures){counts.merge(f.first(),1,Integer::sum);counts.merge(f.second(),1,Integer::sum);}return decimal(counts.values().stream().mapToInt(Integer::intValue).max().orElse(0));
        }
        if(market==null)return null;
        int count=0;for(var f:fixtures) {
            // The frozen legal start wins for same-day employment changes.
            var captured=db.query("SELECT snapshot_json FROM career_appearance_binding WHERE career_id=? AND fixture_identity=?",(r,n)->read(r.getString(1),CareerManagementState.Appearance.class),career,"COMP|"+year+'|'+f.event()+'|'+f.match());
            if(!captured.isEmpty()){if(captured.getFirst().opportunities().stream().anyMatch(p->p.playerId().equals(player)))count++;continue;}
            boolean employed=market.state().contracts().values().stream().anyMatch(c->c.playerId().equals(player)&&List.of(f.first(),f.second()).contains(c.team())&&!f.date().isBefore(c.terms().startDate())&&!f.date().isAfter(c.terms().endDate())&&(c.endedDate()==null||f.date().isBefore(c.endedDate())));
            var management=market.state().management();if(management!=null)for(var loan:management.loans().values())if(loan.playerId().equals(player)&&!f.date().isBefore(loan.startDate())&&!f.date().isAfter(loan.endDate()))employed=List.of(f.first(),f.second()).contains(loan.borrowingTeam());
            if(employed)count++;
        }return decimal(count);
    }
    static String rookie(JdbcTemplate db,String career,int year,String player,int sets) {
        if(sets<26)return "FALSE";
        var registrations=db.query("SELECT season_year,observation_json FROM career_record_observation WHERE career_id=? AND observation_key=? ORDER BY season_year",(r,n)->r.getString(2),career,"ROOKIE:"+player);
        if(registrations.isEmpty())return "UNKNOWN";
        var history=read(registrations.getFirst(),CareerHistoryStore.Registration.class);
        return rookieEligible(sets,history.firstLckYear(),year,history.foreignRegistered(),history.candidateYears().stream().anyMatch(y->y<year),history.complete());
    }
    static int pomCount(JdbcTemplate db,String career,int year,String player){return db.query("SELECT award_json FROM career_record_award WHERE career_id=? AND season_year=? AND definition_id='LCK_POM' AND status='FINALIZED'",(r,n)->read(r.getString(1),CareerAwardsStore.Award.class),career,year).stream().mapToInt(a->a.slots().stream().anyMatch(s->player.equals(s.playerId()))?1:0).sum();}
    private CareerAwardClosure() {}
}
