package com.lolfm.career;

import com.lolfm.LolfmApplication;
import com.lolfm.dto.CareerApiV1Dtos;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;

/** Opt-in local measurement helper. Compile outside production/test output; use only a disposable DB. */
public class CareerPlaySpeedProbe {
    public static void main(String[] args) throws Exception {
        if(args[0].equals("evidence")) {
            var db=new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(args[2],"sa",""));
            var evidence=new TreeMap<String,Object>();
            for(String table:List.of("career_competition_completion_receipt","career_record_series","career_record_draft","career_record_award","career_market_state","career_development_state","career_lifecycle_state","career_appearance_binding","career_appearance_performance","career_calendar_advance_command")) {
                evidence.put(table,db.query("SELECT * FROM "+table,(r,n)->{
                    var row=new TreeMap<String,String>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getString(i));return row;
                }));
            }
            Files.writeString(Path.of(args[1]).resolve("evidence.json"),CareerRosterStore.write(evidence));return;
        }
        boolean prepare=args[0].equals("prepare");
        var app=new SpringApplication(LolfmApplication.class);
        var options=new ArrayList<String>(List.of(Arrays.copyOfRange(args,2,args.length)));
        if(prepare)options.addAll(List.of("--spring.main.web-application-type=none","--lolfm.career.continuous.background.enabled=false","--lolfm.career.competition.background.enabled=false"));
        var context=app.run(options.toArray(String[]::new));
        var db=context.getBean(JdbcTemplate.class);var out=Path.of(args[1]);Files.createDirectories(out);
        if(prepare){
            var c=context.getBean(CareerApplicationService.class).create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,"플레이 속도 V1","측정 감독","GEN","63978045-45bc-4e5e-afee-322471d420b1")).career().career();
            var calendar=context.getBean(CareerCalendarApplicationService.class);var date=calendar.view(c).state().currentDate();int year=calendar.view(c).state().seasonYear();
            var competition=context.getBean(CareerCompetitionRelationalStore.class);var store=context.getBean(CareerContinuousStore.class);
            var fixtures=competition.load(c.careerId(),year).fixtures().stream().filter(f->f.competitionId().equals("LCK_CUP")&&f.lifecycleStatus().equals("READY")).toList();
            var auto=fixtures.stream().filter(f->f.executionMode().equals("FULL_AUTO")).findFirst().orElseThrow();
            var player=fixtures.stream().filter(f->f.executionMode().equals("PLAYER_CONTROLLED")).findFirst().orElseThrow();
            store.tx.executeWithoutResult(t->{store.lock(c.careerId());
                db.update("UPDATE career_competition_fixture SET scheduled_date=? WHERE career_id=? AND fixture_id=?",date,c.careerId(),auto.fixtureId());
                db.update("UPDATE career_competition_fixture SET scheduled_date=? WHERE career_id=? AND fixture_id=?",date.plusDays(2),c.careerId(),player.fixtureId());
                competition.refreshInstanceHash(c.careerId(),year,"LCK_CUP");competition.refreshCycleHash(c.careerId(),year);
            });
            Files.writeString(out.resolve("fixture.json"),CareerRosterStore.write(Map.of("career",c.careerId(),"date",date.toString(),"stopDate",date.plusDays(2).toString(),"auto",auto,"player",player)));
            context.close();return;
        }
        db.execute("SET QUERY_STATISTICS_MAX_ENTRIES 10000");db.execute("SET QUERY_STATISTICS TRUE");
        String career=db.queryForObject("SELECT career_id FROM career_save",String.class);
        var service=context.getBean(CareerContinuousApplicationService.class);
        long begin=0;String previous="";
        while(context.isActive()){
            var view=service.view(career);var run=view.run();
            if(run!=null){
                if(begin==0)begin=System.nanoTime();
                String key=run.status+"/"+run.steps+"/"+(run.intent==null?"none":run.intent.action());
                if(!key.equals(previous)){Files.writeString(out.resolve("transitions.csv"),(System.nanoTime()-begin)/1e6+","+key+","+view.currentDate()+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);previous=key;}
                if(!CareerContinuousProgress.active(run.status)){
                    Files.writeString(out.resolve("final.json"),CareerRosterStore.write(view));
                    Files.writeString(out.resolve("sql.json"),CareerRosterStore.write(db.queryForList("SELECT * FROM INFORMATION_SCHEMA.QUERY_STATISTICS ORDER BY EXECUTION_COUNT DESC")));
                    Files.writeString(out.resolve("results.json"),CareerRosterStore.write(db.queryForList("SELECT * FROM career_competition_result_detail WHERE career_id=?",career)));
                    break;
                }
            }
            Thread.sleep(100);
        }
        // Keep HTTP available for the final browser reflection and read-only auxiliary GETs.
    }
}
