package com.lolfm.career;

import com.lolfm.LolfmApplication;
import com.lolfm.dto.CareerApiV1Dtos;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import com.lolfm.champion.*;
import com.lolfm.domain.*;
import com.lolfm.player.*;
import com.lolfm.simulator.*;
import com.lolfm.draft.*;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;

/** Opt-in local measurement helper. Compile outside production/test output; use only a disposable DB. */
public class CareerPlaySpeedProbe {
    public static void main(String[] args) throws Exception {
        if(args[0].startsWith("long-term")){longTerm(Path.of(args[1]),args[0].equals("long-term-smoke"),args[0].equals("long-term-first")?1:2);return;}
        if(args[0].equals("policies")){policies(Path.of(args[1]));return;}
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
        boolean finalFlow=args[0].equals("prepare-final"),prepare=args[0].equals("prepare")||finalFlow,matches=args[0].equals("matches");
        var app=new SpringApplication(LolfmApplication.class);
        var options=new ArrayList<String>(List.of(Arrays.copyOfRange(args,2,args.length)));
        if(prepare||matches)options.addAll(List.of("--spring.main.web-application-type=none","--lolfm.career.continuous.background.enabled=false","--lolfm.career.competition.background.enabled=false"));
        var context=app.run(options.toArray(String[]::new));
        var db=context.getBean(JdbcTemplate.class);var out=Path.of(args[1]);Files.createDirectories(out);
        if(matches){try{matches(context,out);}finally{context.close();}return;}
        if(prepare){
            var c=context.getBean(CareerApplicationService.class).create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,"플레이 속도 V1","측정 감독","GEN","63978045-45bc-4e5e-afee-322471d420b1")).career().career();
            var calendar=context.getBean(CareerCalendarApplicationService.class);var date=calendar.view(c).state().currentDate();int year=calendar.view(c).state().seasonYear();
            var competition=context.getBean(CareerCompetitionRelationalStore.class);var store=context.getBean(CareerContinuousStore.class);
            var fixtures=competition.load(c.careerId(),year).fixtures().stream().filter(f->f.competitionId().equals("LCK_CUP")&&f.lifecycleStatus().equals("READY")).toList();
            var auto=fixtures.stream().filter(f->f.executionMode().equals("FULL_AUTO")).findFirst().orElseThrow();
            var player=fixtures.stream().filter(f->f.executionMode().equals("PLAYER_CONTROLLED")).findFirst().orElseThrow();
            store.tx.executeWithoutResult(t->{store.lock(c.careerId());
                db.update("UPDATE career_competition_fixture SET scheduled_date=? WHERE career_id=? AND fixture_id=?",finalFlow?date.plusDays(7):date,c.careerId(),auto.fixtureId());
                db.update("UPDATE career_competition_fixture SET scheduled_date=? WHERE career_id=? AND fixture_id=?",date.plusDays(finalFlow?9:2),c.careerId(),player.fixtureId());
                competition.refreshInstanceHash(c.careerId(),year,"LCK_CUP");competition.refreshCycleHash(c.careerId(),year);
            });
            if(finalFlow){
                var review=context.getBean(CareerLifecycleStore.class).review(c.careerId(),year,date);
                var old=CareerMarketStore.load(db,c.careerId());var market=CareerMarketStore.engine(db,c.careerId(),year,old);String team="LCK:"+auto.firstTeamCode();
                String id=review.rookieIds().stream().filter(p->market.player(p).position()==Position.TOP).min(Comparator.comparingLong(market::demand)).orElseThrow();
                // Explicit roster-gap setup uses common release; no budget injection or forced acceptance.
                for(var member:new ArrayList<>(market.members.values()))if(team.equals(member.ownerTeam())&&market.player(member.playerId()).position()==Position.TOP)market.release(team,member.playerId(),null,date);
                var start=market.availableStart(id,date);var offer=market.submit(team,id,new CareerMarketState.Terms(start,start.plusYears(2).minusDays(1),market.demand(id)*125/100,0,CareerMarketState.Role.STARTER),null,date);
                CareerMarketStore.persist(db,c.careerId(),year,old,market);
                for(int n=0;n<7;n++)calendar.advance(c,CareerApiV1Dtos.ADVANCE_REQUEST_SCHEMA,calendar.view(c).state().calendarRevision(),CareerCalendarApplicationService.ADVANCE_ONE_DAY,UUID.randomUUID().toString());
                old=CareerMarketStore.load(db,c.careerId());var accepted=CareerMarketStore.engine(db,c.careerId(),year,old);var actual=calendar.currentDate(c);
                if(!team.equals(accepted.members.get(id).ownerTeam()))throw new IllegalStateException("Final flow did not obtain player consent: "+accepted.offers.get(offer.offerId()));
                for(var member:new ArrayList<>(accepted.members.values()))if(team.equals(member.ownerTeam())&&!id.equals(member.playerId())&&accepted.player(member.playerId()).position()==Position.TOP)accepted.release(team,member.playerId(),null,actual);
                accepted.select(team,id,actual);CareerMarketStore.persist(db,c.careerId(),year,old,accepted);
                Files.writeString(out.resolve("negotiation.json"),CareerRosterStore.write(Map.of("player",id,"offer",accepted.offers.get(offer.offerId()),"contract",accepted.active(id,actual),"lineup",accepted.lineups.get(team),"date",actual,"team",team)));
            }
            Files.writeString(out.resolve("fixture.json"),CareerRosterStore.write(Map.of("career",c.careerId(),"date",date.plusDays(finalFlow?7:0).toString(),"stopDate",date.plusDays(finalFlow?9:2).toString(),"auto",auto,"player",player)));
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
    /** Opt-in synthetic paths through product functions, never Career appearances or award ledgers. */
    static void policies(Path out) throws Exception {
        Files.createDirectories(out);long begin=System.nanoTime();var json=new com.fasterxml.jackson.databind.ObjectMapper();var champions=new ChampionCatalog(json);
        var ratings=PlayerRatingCatalog.loadDefault();var catalog=new ExpandedPlayerCatalog(json,new GlobalTeamRosterCatalog(json,ratings,ChampionProficiencyCatalog.loadDefault(ratings,champions),champions),champions);
        String career="career_"+"c".repeat(64);var start=LocalDate.of(2027,1,1);
        var growth=new StringBuilder("seed,profile,source,year,age,pa,ca_start,ca_end,strength_end,internal_growth,decline,model_sets,retirement_probability,retirement_roll,retired,asking_krw\n");
        for(long seed:List.of(41L,73L)){
            var rookie=CareerRookieFactory.generate(career,seed,2027,start,Map.of(Position.TOP,1),false,1,champions,List.of("LCP")).getFirst();
            for(String profile:List.of("FIRST_TEAM","CL","BENCH","FA","VETERAN")){
                var d=profile.equals("VETERAN")?catalog.players().get("player-kiin"):rookie.definition();
                var birth=profile.equals("VETERAN")?CareerLifecyclePolicy.ageProfile(d,seed,start).simulationBirthDate():rookie.age().simulationBirthDate();
                var meta=new CareerDevelopmentPolicy.Metadata(CareerDevelopmentPolicy.metadata(d).potential(),birth);var player=CareerDevelopmentPolicy.initial(d);int remainder=0,cursor=0;
                for(int year=2027;year<=2031;year++){
                    int initial=CareerLifecyclePolicy.ca(player),sum=CareerDevelopmentPolicy.sum(player),sets=0;var from=LocalDate.of(year,1,1);var through=LocalDate.of(year,12,31);
                    for(var date=from;!date.isAfter(through);date=date.plusDays(1)){
                        boolean season=date.getDayOfYear()<=224,first=profile.equals("FIRST_TEAM")||profile.equals("VETERAN");
                        boolean played=season&&(profile.equals("CL")&&date.getDayOfWeek()==DayOfWeek.WEDNESDAY||first&&(date.getDayOfWeek()==DayOfWeek.WEDNESDAY||date.getDayOfWeek()==DayOfWeek.SATURDAY));
                        if(played)for(int set=0;set<2;set++){player=CareerDevelopmentPolicy.game(player,d,meta,date,d.gameplay().proficiencies().getFirst().championId());sets++;}
                        var plan=CareerDevelopmentPolicy.ai(d,meta,player,date,played?0:3);player=CareerDevelopmentPolicy.day(player,d,meta,date,plan,profile.equals("FA"));
                    }
                    int age=CareerDevelopmentPolicy.age(meta,through),gained=CareerDevelopmentPolicy.sum(player)-sum,beforeDecline=CareerLifecyclePolicy.ca(player);
                    var observation=new CareerLifecycleState.Observation(profile.equals("FA")?"PARTIAL":profile.equals("CL")?"FULL_DEVELOPMENT_SEASON":"FULL_DOMESTIC_SEASON",profile.equals("CL")?32:64,sets/2,sets,from,through);
                    // A newly generated intake is not reviewed for decline/retirement in its intake year.
                    var decline=CareerLifecyclePolicy.decline(player,d.position(),age,remainder,cursor,observation);
                    boolean intake=!profile.equals("VETERAN")&&year==2027;
                    if(!intake){player=decline.player();remainder=decline.carried();cursor=decline.cursor();}
                    int probability=intake?0:CareerLifecyclePolicy.retirementProbability(age,observation,profile.equals("BENCH")?year-2027:0,profile.equals("FA")&&year>=2029,profile.equals("FA"),beforeDecline-CareerLifecyclePolicy.ca(player),CareerLifecyclePolicy.ca(player),60,!profile.equals("FA"));
                    int roll=CareerLifecyclePolicy.draw(seed,d.playerId(),year,"RETIREMENT_REVIEW",10000);boolean retired=roll<probability*100;
                    int strength=player.internalRatings().values().stream().mapToInt(v->v/1000).sum();String region=profile.equals("VETERAN")?"LCK":"LCP";
                    growth.append(seed+","+profile+","+(profile.equals("VETERAN")?"ORIGINAL":"GENERATED")+","+year+","+age+","+meta.potential()+","+initial+","+CareerLifecyclePolicy.ca(player)+","+strength+","+gained+","+(intake?0:decline.applied())+","+sets+","+probability+","+roll+","+retired+","+CareerNegotiationPolicy.annual(region,strength,CareerFinanceReference.stringMap(CareerFinanceReference.json().path("krwPerUnit")))+"\n");
                    if(retired)break;
                }
            }
        }
        Files.writeString(out.resolve("growth.csv"),growth);
        var boundary=new StringBuilder("efficiency,completed_tick,worked_seconds,discarded_work,tempo_credit_at_completion,credit_after_gap\n");
        for(double efficiency:List.of(.99,1.0,1.2,1.49,1.5)){
            var camps=new JungleCampState();var tempo=new JungleTempoState();int tick=0;double attempted=0;
            for(int time=90;time<180;time+=10){tick++;tempo.recordClearWork(time,10,efficiency);attempted=camps.workSeconds()+10*efficiency;if(camps.clear(time,10,efficiency)){double credit=tempo.snapshot().creditSeconds();tempo.recordClearWork(time+300,10,efficiency);boundary.append(efficiency+","+tick+","+(tick*10)+","+(attempted-MatchRealismRuleConfig.CAMP_CLEAR_WORK_SECONDS)+","+credit+","+tempo.snapshot().creditSeconds()+"\n");break;}}
        }
        Files.writeString(out.resolve("jungle-boundary.csv"),boundary);
        var award=new StringBuilder("path,series,mean,wins,sets,adjusted_mean,consistency,team_score,all_pro,regular_mvp,tournament_mvp\n");
        for(int[] path:List.of(new int[]{4,4,3},new int[]{8,6,3},new int[]{4,4,5},new int[]{8,8,3},new int[]{2,2,3})){
            var samples=new ArrayList<CareerPerformancePolicy.Sample>();for(int n=0;n<path[0];n++)samples.add(new CareerPerformancePolicy.Sample(java.math.BigDecimal.valueOf(80),java.math.BigDecimal.ONE,n<path[1],path[2]));
            var period=CareerPerformancePolicy.period(samples);award.append("S"+path[0]+"W"+path[1]+"BO"+path[2]+","+path[0]+",80,"+path[1]+","+(path[0]*path[2])+","+period.adjustedMean()+","+period.consistency()+","+period.teamPerformance()+","+period.allPro()+","+period.regularMvp()+","+period.tournamentMvp()+"\n");
        }
        Files.writeString(out.resolve("award-paths.csv"),award);Files.writeString(out.resolve("observation-time.txt"),Double.toString((System.nanoTime()-begin)/1e9));
    }

    static void matches(org.springframework.context.ConfigurableApplicationContext context,Path out) throws Exception {
        var orchestrator=context.getBean(com.lolfm.application.RealDraftMatchOrchestrator.class);var catalog=context.getBean(GlobalTeamRosterCatalog.class);var champions=context.getBean(ChampionCatalog.class);
        var availability=new DraftAvailability(champions,new RoleAssignmentSolver(champions));long begin=System.nanoTime();int executed=0;
        for(var pair:List.of(List.of("LCK","GEN","T1"),List.of("CBLOL","RED","PNG"))){
            String first=pair.get(0)+":"+pair.get(1),second=pair.get(0)+":"+pair.get(2);
            var frozen=new CompetitionRosterSnapshot(Map.of(first,CompetitionRosterSnapshot.capture(catalog.snapshot(new GlobalTeamRosterCatalog.TeamKey(pair.get(0),pair.get(1)))),second,CompetitionRosterSnapshot.capture(catalog.snapshot(new GlobalTeamRosterCatalog.TeamKey(pair.get(0),pair.get(2))))));
            var history=new SeriesDraftHistory();
            for(int game=1;game<=2;game++){
                long started=System.nanoTime();var prepared=orchestrator.prepareV1("CAREER_V2_OBSERVATION:"+pair.get(0)+":"+game,first,second,history,73L+game-1,SimulationInstrumentation.enabled(),frozen);executed++;
                var result=prepared.output();var draft=prepared.completedDraft();var rows=new ArrayList<Map<String,Object>>();var state=DraftState.fresh(draft.ruleSet(),history);
                for(var decision:draft.decisions()){
                    var current=state;var unavailable=current.unavailableChampions();var legal=champions.all().stream().map(ChampionDefinition::id).filter(id->!unavailable.contains(id)).filter(id->decision.actionType()==DraftActionType.BAN||availability.canComplete(current,decision.side(),id)).map(ChampionId::value).toList();
                    rows.add(Map.of("decision",decision,"legalBeforeSearch",legal,"selection",draft.selectionTraces().stream().filter(t->t.turn()==decision.turn()).findFirst().orElseThrow()));
                    state=state.apply(new DraftAction(decision.turn(),decision.side(),decision.actionType(),decision.selectedChampionId()));
                }
                var statistics=CareerGameStatistics.from(game,result.outputHash(),result.resultSummary());var evaluations=new TreeMap<String,CareerPerformancePolicy.Rating>();for(var player:statistics.players())evaluations.put(player.playerId(),CareerPerformancePolicy.rate(statistics,player));
                var data=new TreeMap<String,Object>();data.put("input",Map.of("inputHash",prepared.input().inputHash(),"policy",prepared.input().productionPolicy(),"rosters",frozen,"assignments",prepared.input().championAssignments(),"seed",prepared.input().matchSeed()));data.put("statistics",statistics);data.put("ratings",evaluations);data.put("draft",rows);data.put("exclusionsBefore",prepared.historyBefore());data.put("checkpoints",result.timeline().snapshots().stream().filter(snap->Set.of(600,900,1200).contains(snap.timeSeconds())).toList());data.put("wallSeconds",(System.nanoTime()-started)/1e9);
                Files.writeString(out.resolve(pair.get(0)+"-g"+game+".json"),CareerRosterStore.write(data));history.commitCompleted(draft);System.out.println("OBSERVATION_COMPLETED sets="+executed+" region="+pair.get(0)+" game="+game+" duration="+result.resultSummary().durationSeconds());
            }
        }
        Files.writeString(out.resolve("observation-time.txt"),Double.toString((System.nanoTime()-begin)/1e9));
    }


    /** Explicit no-match projection: daily runtime settlement, actual lifecycle review, modeled season scopes.
     * No fixture results, appearances, user acceptance, or production rollover receipts are manufactured.
     * The separate existing rollover integration test establishes the real command boundary.
     */
    static void longTerm(Path out,boolean smoke,int samples) throws Exception {
        Files.createDirectories(out); long begun=System.nanoTime();
        var app=new SpringApplication(LolfmApplication.class);
        try(var ctx=app.run("--spring.main.web-application-type=none","--spring.datasource.url=jdbc:h2:mem:career-long-term;DB_CLOSE_DELAY=-1",
                "--lolfm.career.continuous.background.enabled=false","--lolfm.career.competition.background.enabled=false")) {
            var db=ctx.getBean(JdbcTemplate.class);var tx=new org.springframework.transaction.support.TransactionTemplate(ctx.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            var rows=new ArrayList<Map<String,Object>>();
            for(int sample=1;sample<=(smoke?1:samples);sample++) {
                String command=UUID.nameUUIDFromBytes(("career-long-term-v1-"+sample).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                var c=ctx.getBean(CareerApplicationService.class).create(new CareerApiV1Dtos.CreateRequest(CareerApiV1Dtos.CREATE_REQUEST_SCHEMA,"장기 진단 "+sample,"관측 감독","GEN",command)).career().career();
                String id=c.careerId();int first=CareerRosterStore.activeYear(db,id);var gapDays=new TreeMap<String,Integer>();
                rows.add(longSnapshot(db,id,first,"START",gapDays,null));
                for(int year=first;year<first+(smoke?1:3);year++) {
                    final int y=year;LocalDate target=smoke?CareerMarketStore.load(db,id).state().processedThrough().plusDays(7):LocalDate.of(year,12,1);
                    longAdvance(db,tx,id,year,target,gapDays,smoke,sample);
                    CareerLifecycleState.Review review=smoke?null:tx.execute(t->ctx.getBean(CareerLifecycleStore.class).review(id,y,target));
                    if(!smoke)tx.executeWithoutResult(t->{
                        var old=CareerMarketStore.load(db,id);var m=CareerMarketStore.engine(db,id,y,old);
                        m.finance.close(y,target,Map.of(),Map.of()); // No invented standings or prize results.
                        CareerMarketStore.persist(db,id,y,old,m);
                        if(!review.equals(ctx.getBean(CareerLifecycleStore.class).review(id,y,target)))throw new IllegalStateException("DUPLICATE_LIFECYCLE_REVIEW");
                    });
                    var reviewData=longSnapshot(db,id,year,smoke?"SMOKE":"SEASON_REVIEW",gapDays,review);
                    if(!smoke)longAdvance(db,tx,id,year,LocalDate.of(year+1,1,1),gapDays,false,sample);
                    var endData=longSnapshot(db,id,year,smoke?"SMOKE":"SEASON_END",gapDays,null);
                    for(String key:List.of("rookies","supply","announced","declineInternal"))if(reviewData.containsKey(key))endData.put(key,reviewData.get(key));
                    endData.put("reviewDate",target.toString());rows.add(endData);
                    Files.writeString(out.resolve("summary.json"),CareerRosterStore.write(Map.of("mode","NO_MATCH_RUNTIME_PROJECTION","wallSeconds",(System.nanoTime()-begun)/1e9,"rows",rows)));
                    if(smoke||year<first+2)tx.executeWithoutResult(t->{
                        // Model-only scope carry, deliberately not a Career season-transition command.
                        String identity=CareerRosterStore.hash(id+"|DIAGNOSTIC_SCOPE|"+(y+1));
                        String prior=db.queryForObject("SELECT season_id FROM career_season WHERE career_id=? AND season_year=?",String.class,id,y);
                        long nextSeed=CareerCompetitionAggregate.deriveSeed(c.rootSeed(),y+1,"CAREER_SEASON","ORDINAL:"+(y-first+2));
                        var created=ctx.getBean(CareerApplicationService.SeasonProvisioningPort.class).provisionNext(prior,"league_"+identity,"season_"+identity,c.managedTeamCode(),nextSeed);
                        db.update("INSERT INTO career_season(career_id,season_year,season_ordinal,league_id,season_id,season_root_seed,frozen_snapshot_hash,product_decision_hash,lifecycle_status) VALUES (?,?,?,?,?,?,?,?,'ACTIVE')",id,y+1,y-first+2,created.leagueId(),created.seasonId(),created.rootSeed(),created.frozenSnapshotIdentity(),created.productDecisionIdentity());
                        CareerRosterStore.carry(db,id,y,y+1);CareerClStore.initializeState(db,id,y+1);
                        db.update("UPDATE career_calendar_state SET active_calendar_season_year=? WHERE career_id=?",y+1,id);
                        var old=CareerMarketStore.load(db,id);var m=CareerMarketStore.engine(db,id,y+1,old);m.finance.initializeTargets(y+1,m.processedThrough(),false);CareerMarketStore.persist(db,id,y+1,old,m);
                    });
                }
            }
        }
        System.out.println("LONG_DONE wallSeconds="+(System.nanoTime()-begun)/1e9);
    }
    static Map<String,Object> longSnapshot(JdbcTemplate db,String id,int year,String stage,Map<String,Integer> gaps,CareerLifecycleState.Review review) {
        var old=CareerMarketStore.load(db,id);var m=CareerMarketStore.engine(db,id,year,old);var growth=CareerDevelopmentStore.load(db,id).state();var row=new TreeMap<String,Object>();
        row.put("career",id);row.put("seed",Long.toString(m.seed));row.put("year",year);row.put("stage",stage);row.put("date",m.processedThrough().toString());
        row.put("policies",List.of(CareerDevelopmentPolicy.VERSION,CareerLifecyclePolicy.VERSION,CareerMarketPolicy.VERSION,CareerSportingFinancePolicy.VERSION,CareerSquadPlanningPolicy.VERSION));
        row.put("players",m.directory.players().size());row.put("accounts",m.accounts.size());row.put("participants",CareerSportingFinancePolicy.COMPETITIONS.keySet().stream().flatMap(r->CareerSportingFinancePolicy.participants(r).stream()).distinct().count());
        var groups=new TreeMap<String,List<Integer>>();var cases=new ArrayList<Map<String,Object>>();
        for(var e:growth.players().entrySet()) {
            String p=e.getKey();var d=m.player(p);var life=m.lifecycle.people.get(p);int ca=CareerLifecyclePolicy.ca(e.getValue());int age=CareerLifecyclePolicy.age(life.age(),m.processedThrough());var member=m.members.get(p);
            if(life.status()!=CareerLifecycleState.Status.RETIRED)for(String group:List.of("ALL_ACTIVE","AGE:"+(age<=20?"<=20":age<=24?"21-24":age<=29?"25-29":"30+"),"SOURCE:"+life.source(),"SQUAD:"+member.squad(),"REGION:"+(member.ownerTeam()==null?"UNAFFILIATED":CareerMarketPolicy.region(member.ownerTeam()))))groups.computeIfAbsent(group,k->new ArrayList<>()).add(ca);
            if(List.of("player-faker","player-chovy","player-jiwoo","player-doran","player-lumos","player-beryl").contains(p)) {
                var example=new TreeMap<String,Object>();example.put("id",p);example.put("name",d.nickname());example.put("age",age);example.put("CA",ca);example.put("internalSum",CareerDevelopmentPolicy.sum(e.getValue()));example.put("PA",CareerDevelopmentPolicy.metadata(d).potential());example.put("status",life.status());example.put("squad",member.squad());example.put("team",member.ownerTeam());example.put("fatigue",e.getValue().fatigue());cases.add(example);
            }
        }
        var distributions=new TreeMap<String,Object>();groups.forEach((k,v)->{Collections.sort(v);distributions.put(k,Map.of("n",v.size(),"p10",v.get((v.size()-1)/10),"p50",v.get((v.size()-1)/2),"p90",v.get((v.size()-1)*9/10),"ca160",v.stream().filter(n->n>=160).count(),"ca180",v.stream().filter(n->n>=180).count()));});row.put("CA",distributions);row.put("cases",cases);
        row.put("internalSum",growth.players().values().stream().mapToLong(CareerDevelopmentPolicy::sum).sum());row.put("retirement",m.lifecycle.people.values().stream().collect(java.util.stream.Collectors.groupingBy(p->p.status().name(),TreeMap::new,java.util.stream.Collectors.counting())));
        if(review!=null){row.put("supply",review.supply());row.put("announced",review.changes().stream().filter(c->c.outcome().equals("RETIREMENT_ANNOUNCED")).map(c->Map.of("id",c.playerId(),"age",c.age(),"effective",c.effectiveDate())).toList());row.put("declineInternal",review.changes().stream().mapToLong(CareerLifecycleState.Change::appliedDecline).sum());}
        row.put("trainingInternal",growth.monthly().values().stream().filter(g->g.seasonYear()==year).mapToLong(CareerDevelopmentState.Gain::internalGain).sum());
        row.put("paMissing",m.directory.players().values().stream().filter(d->CareerDevelopmentPolicy.metadata(d).potential()==null).count());
        if(review!=null) {
            var rookies=new ArrayList<Map<String,Object>>();for(String id2:review.rookieIds()){var d=m.player(id2);var life=m.lifecycle.people.get(id2);rookies.add(Map.of("id",id2,"name",d.nickname(),"role",d.position(),"age",CareerLifecyclePolicy.age(life.age(),m.processedThrough()),"CA",CareerLifecyclePolicy.ca(growth.players().get(id2)),"PA",CareerDevelopmentPolicy.metadata(d).potential()));}
            row.put("rookies",rookies);
            if(rookies.stream().map(r->r.get("id")).distinct().count()!=rookies.size())throw new IllegalStateException("DUPLICATE_ROOKIE_ID");
            for(String id2:review.rookieIds())if(m.directory.players().values().stream().filter(d->d.nickname().equalsIgnoreCase(m.player(id2).nickname())).count()!=1)throw new IllegalStateException("DUPLICATE_ROOKIE_NAME");
        }
        row.put("gapDays",new TreeMap<>(gaps));var teams=new TreeMap<String,Object>();
        for(String team:m.accounts.keySet()) {
            var a=m.accounts.get(team);long posted=m.ledger.stream().filter(l->l.team().equals(team)&&!l.kind().equals("SALARY_ACCRUED")).mapToLong(CareerMarketState.Ledger::amount).sum();
            if(posted!=a.cash())throw new IllegalStateException("CASH_LEDGER_MISMATCH:"+team+":"+posted+":"+a.cash());
            var flow=new TreeMap<String,Long>();m.ledger.stream().filter(l->l.team().equals(team)).forEach(l->flow.merge(l.kind(),l.amount(),Long::sum));
            teams.put(team,Map.of("cash",a.cash(),"flow",flow,"salaryArrears",m.salaryArrears(team),"operatingArrears",m.finance.debt.getOrDefault(team,0L),"reserved",m.reservedCash(team),"wageLimit",m.finance.approval(team,m.processedThrough()).wageLimit(),"members",m.members.values().stream().filter(v->team.equals(v.ownerTeam())).count(),"starters",m.lineups.getOrDefault(team,List.of()).size()));
        }
        if(m.ledger.stream().map(CareerMarketState.Ledger::entryId).distinct().count()!=m.ledger.size())throw new IllegalStateException("DUPLICATE_LEDGER");
        row.put("teams",teams);row.put("offers",m.offers.values().stream().collect(java.util.stream.Collectors.groupingBy(o->o.status().name(),TreeMap::new,java.util.stream.Collectors.counting())));
        row.put("trades",m.tradeEngine.trades.values().stream().collect(java.util.stream.Collectors.groupingBy(t->t.terms().kind()+":"+t.status().name(),TreeMap::new,java.util.stream.Collectors.counting())));
        row.put("coverageDecisions",m.planner.state().decisions().stream().filter(d->d.action().equals("COVERAGE")||d.status().equals("PROPOSED")).filter(d->Set.of("LCK:BRO","CBLOL:LEV","LEC:LR").contains(d.team())).toList());
        var rosterCases=new TreeMap<String,Object>();
        for(String team:List.of("LCK:BRO","CBLOL:LEV","LEC:LR","LCK:T1")) {
            var people=new ArrayList<Map<String,Object>>();
            for(var member:m.members.values())if(team.equals(member.ownerTeam())) {
                String p=member.playerId();var d=m.player(p);var c=m.active(p,m.processedThrough());
                people.add(Map.of("id",p,"name",d.nickname(),"position",d.position(),"squad",member.squad(),"selected",m.lineups.getOrDefault(team,List.of()).contains(p),"CA",CareerLifecyclePolicy.ca(growth.players().get(p)),"annualSalary",c==null?0:c.terms().annualSalary()));
            }
            rosterCases.put(team,people);
        }
        row.put("rosterCases",rosterCases);
        row.put("events",m.events.stream().collect(java.util.stream.Collectors.groupingBy(CareerMarketState.Event::kind,TreeMap::new,java.util.stream.Collectors.counting())));
        row.put("evaluations",m.decisions.values().stream().flatMap(d->d.evaluations().stream()).collect(java.util.stream.Collectors.groupingBy(CareerMarketState.Evaluation::reason,TreeMap::new,java.util.stream.Collectors.counting())));
        row.put("repeatContracts",m.contracts.values().stream().filter(c->c.origin().startsWith("NEGOTIATED")||c.origin().equals("PAID_TRANSFER_AGREEMENT")).collect(java.util.stream.Collectors.groupingBy(CareerMarketState.Contract::playerId,TreeMap::new,java.util.stream.Collectors.counting())).entrySet().stream().filter(e->e.getValue()>2).sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(10).toList());
        row.put("financeTargets",m.finance.targets.values().stream().filter(t->t.seasonYear()==year).collect(java.util.stream.Collectors.groupingBy(t->t.sportingStatus()+":"+t.financeStatus(),TreeMap::new,java.util.stream.Collectors.counting())));
        row.put("contractOrigins",m.contracts.values().stream().collect(java.util.stream.Collectors.groupingBy(CareerMarketState.Contract::origin,TreeMap::new,java.util.stream.Collectors.counting())));
        if(m.directory.players().values().stream().map(p->p.nickname().toLowerCase(Locale.ROOT)).distinct().count()!=m.directory.players().size())row.put("duplicateNames",true);
        return row;
    }


    static void longAdvance(JdbcTemplate db,org.springframework.transaction.support.TransactionTemplate tx,String id,int year,LocalDate target,Map<String,Integer> gapDays,boolean smoke,int sample) {
                    while(CareerMarketStore.load(db,id).state().processedThrough().isBefore(target)) {
                        tx.executeWithoutResult(t->{
                            CareerRosterStore.lockCareer(db,id);var old=CareerMarketStore.load(db,id);var m=CareerMarketStore.engine(db,id,year,old);
                            var growth=CareerDevelopmentStore.load(db,id);m.development=new CareerDevelopmentEngine(CareerRosterStore.baseDirectory(db,id),growth.state());
                            // No match schedule/appearance assumptions: training only, including overseas and CL.
                            m.developmentFixtures=Map.of();m.developmentYear=year;
                            LocalDate end=old.state().processedThrough().plusDays(smoke?7:28);if(end.isAfter(target))end=target;
                            for(LocalDate d=old.state().processedThrough().plusDays(1);!d.isAfter(end);d=d.plusDays(1)) {
                                m.advance(d);
                                for(String team:m.accounts.keySet())if(m.lineups.getOrDefault(team,List.of()).stream().filter(p->m.eligible(p,team,m.processedThrough())).map(p->m.player(p).position()).distinct().count()!=5)gapDays.merge(team,1,Integer::sum);
                            }
                            CareerDevelopmentStore.persist(db,id,growth,m.development);CareerMarketStore.persist(db,id,year,old,m);
                        });
                        System.out.println("LONG_PROGRESS "+sample+" "+year+" "+CareerMarketStore.load(db,id).state().processedThrough());
                    }
    }
}
