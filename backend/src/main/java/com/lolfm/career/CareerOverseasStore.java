package com.lolfm.career;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.lolfm.career.CareerRosterStore.*;
import static com.lolfm.career.CareerOverseasRules.*;
import static com.lolfm.career.CareerOverseasTournament.*;

/** Regional state shares Calendar's transaction and the common fixture/application ledger. */
@Service
public final class CareerOverseasStore {
    @org.springframework.beans.factory.annotation.Autowired private com.lolfm.champion.ChampionCatalog champions;
    private final JdbcTemplate db;private final TransactionTemplate tx;private final CareerCompetitionRelationalStore competitions;
    public CareerOverseasStore(JdbcTemplate db,PlatformTransactionManager manager,CareerCompetitionRelationalStore competitions){this.db=db;tx=new TransactionTemplate(manager);this.competitions=competitions;}
    record State(String policyVersion,String ruleHash,Input input,Plan plan){}
    public record Activation(int introducedYear,int activationYear,LocalDate introducedOn,String policyVersion,String ruleHash,boolean extensionApplied){}
    public record EventView(String eventId,String name,String league,String status,String waitingReason,Plan result,List<CareerCompetitionRelationalStore.FixtureRow> fixtures,Map<String,Score> scores,Map<String,CareerOverseasRanking.Record> standings,Map<String,Integer> championshipPoints,Map<String,List<CareerInternationalState.Entry>> qualifications){}
    public record View(String careerId,int seasonYear,Activation activation,boolean active,boolean readOnly,String league,List<EventView> events,String scheduleExplanation){}
    static Activation activation(JdbcTemplate db,String career){var rows=db.query("SELECT introduced_year,activation_year,introduced_on,policy_version,rule_hash,extension_applied,rule_json FROM career_overseas_activation WHERE career_id=?",(r,n)->{if(!hash(r.getString(7)).equals(r.getString(5)))throw new IllegalStateException("OVERSEAS_RULE_SNAPSHOT_INTEGRITY");return new Activation(r.getInt(1),r.getInt(2),r.getObject(3,LocalDate.class),r.getString(4),r.getString(5),r.getBoolean(6));},career);return rows.isEmpty()?null:rows.getFirst();}
    static boolean active(JdbcTemplate db,String career,int year){var a=activation(db,career);return a!=null&&year>=a.activationYear();}
    private void introduce(String career,int year,boolean fresh){if(activation(db,career)!=null)return;String text;try(var in=getClass().getResourceAsStream("/career/overseas-rules-2026-v1.json")){text=new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException("OVERSEAS_REFERENCE",e);}
        db.update("INSERT INTO career_overseas_activation(career_id,introduced_year,activation_year,introduced_on,policy_version,rule_json,rule_hash) VALUES (?,?,?,?,?,?,?)",career,year,fresh?year:year+1,CareerMarketStore.date(db,career),VERSION,text,hash(text));}
    public void initializeNew(String career,int year){tx.executeWithoutResult(s->{lockCareer(db,career);introduce(career,year,true);CareerOverseasRoster.activate(db,career,year,true,champions);initialize(competitions,career,year);});}
    void prepareSeason(String career,int year){if(active(db,career,year))CareerOverseasRoster.activate(db,career,year,false,champions);}
    public void recover(){
        for(String career:db.query("SELECT career_id FROM career_player_directory WHERE directory_version=? ORDER BY career_id",(r,n)->r.getString(1),com.lolfm.player.ExpandedPlayerCatalog.VERSION))tx.executeWithoutResult(s->{
            lockCareer(db,career);int year=activeYear(db,career);introduce(career,year,false);
            if(active(db,career,year))for(Event event:Event.values()){
                var instance=competitions.instance(career,year,event.name());var state=load(db,career,year,event);
                if(state==null&&!"WAITING_FOR_QUALIFICATION".equals(instance.lifecycleStatus()))throw new IllegalStateException("OVERSEAS_ACTIVE_STATE_MISSING:"+event);
                if(state!=null&&!state.ruleHash().equals(activation(db,career).ruleHash()))throw new IllegalStateException("OVERSEAS_FROZEN_RULE_CONFLICT");
            }
        });
    }
    static State load(JdbcTemplate db,String career,int year,Event event){var rows=db.query("SELECT state_json,state_hash FROM career_overseas_state WHERE career_id=? AND season_year=? AND competition_id=?",(r,n)->{if(!hash(r.getString(1)).equals(r.getString(2)))throw new IllegalStateException("OVERSEAS_STATE_INTEGRITY");var v=read(r.getString(1),State.class);if(!Set.of(VERSION,PROJECTION_VERSION).contains(v.policyVersion())||v.input().event()!=event||v.input().year()!=year)throw new IllegalStateException("OVERSEAS_STATE_SCOPE");return v;},career,year,event.name());return rows.isEmpty()?null:rows.getFirst();}
    private static void save(JdbcTemplate db,String career,State state){String text=write(state);int year=state.input().year();String event=state.input().event().name();if(db.update("UPDATE career_overseas_state SET state_json=?,state_hash=? WHERE career_id=? AND season_year=? AND competition_id=?",text,hash(text),career,year,event)==0)db.update("INSERT INTO career_overseas_state VALUES (?,?,?,?,?)",career,year,event,text,hash(text));}
    static void initialize(CareerCompetitionRelationalStore store,String career,int year){if(!active(store.jdbc,career,year))return;var a=activation(store.jdbc,career);
        for(Event e:Event.values())if(store.jdbc.queryForObject("SELECT COUNT(*) FROM career_competition_instance WHERE career_id=? AND calendar_season_year=? AND competition_id=?",Integer.class,career,year,e.name())==0){String input=hash(career+'|'+year+'|'+e+'|'+a.ruleHash());store.jdbc.update("""
            INSERT INTO career_competition_instance(career_id,calendar_season_year,competition_id,rule_status,lifecycle_status,blocking_reason,source_input_hash,revision,state_hash,created_at,updated_at,hash_algorithm,materialization_policy_id,materialization_receipt_hash)
            VALUES (?,?,?,'GAME_POLICY_DEFINED','WAITING_FOR_QUALIFICATION','OVERSEAS_PRIOR_RESULT_REQUIRED',?,0,?,?,?,?,?,?)
            """,career,year,e.name(),input,"0".repeat(64),store.now(),store.now(),CareerCompetitionRelationalStore.INSTANCE_HASH_ALGORITHM,VERSION,input);}
        reconcile(store,career,year);store.refreshAllInstanceHashes(career,year);store.refreshCycleHash(career,year);
    }
    static Map<String,Score> scores(CareerCompetitionRelationalStore store,String career,int year,Event event){var out=new TreeMap<String,Score>();store.jdbc.query("""
        SELECT f.match_id,d.first_score,d.second_score FROM career_competition_fixture f
        JOIN career_competition_application a ON a.career_id=f.career_id AND a.calendar_season_year=f.calendar_season_year AND a.competition_id=f.competition_id AND a.match_id=f.match_id
        JOIN career_competition_result_detail d ON d.career_id=f.career_id AND d.calendar_season_year=f.calendar_season_year AND d.competition_id=f.competition_id AND d.match_id=f.match_id
        WHERE f.career_id=? AND f.calendar_season_year=? AND f.competition_id=? AND f.lifecycle_status='COMPLETED'
        """,(org.springframework.jdbc.core.RowCallbackHandler)r->out.put(r.getString(1),new Score(r.getInt(2),r.getInt(3))),career,year,event.name());return out;}
    static void reconcile(CareerCompetitionRelationalStore store,String career,int year){if(!active(store.jdbc,career,year))return;
        for(Event event:Event.values()){
            var state=load(store.jdbc,career,year,event);if(state==null){var input=input(store,career,year,event);if(input==null)continue;state=new State(PROJECTION_VERSION,activation(store.jdbc,career).ruleHash(),input,project(input,Map.of()));}
            if(state.plan().complete())continue; // Historical ranking, awards and qualification evidence are sealed.
            boolean upgrade=VERSION.equals(state.policyVersion())&&store.jdbc.queryForObject("SELECT COUNT(*) FROM career_competition_series_binding WHERE career_id=? AND calendar_season_year=? AND competition_id=?",Integer.class,career,year,event.name())==0
                    &&store.jdbc.queryForObject("SELECT COUNT(*) FROM career_competition_fixture WHERE career_id=? AND calendar_season_year=? AND competition_id=? AND lifecycle_status<>'READY'",Integer.class,career,year,event.name())==0
                    &&store.jdbc.queryForObject("SELECT COUNT(*) FROM career_competition_application WHERE career_id=? AND calendar_season_year=? AND competition_id=?",Integer.class,career,year,event.name())==0;
            String policy=upgrade?PROJECTION_VERSION:state.policyVersion();
            var plan=project(state.input(),scores(store,career,year,event),policy);var next=new State(policy,state.ruleHash(),state.input(),plan);
            if(upgrade)for(var bout:plan.bouts())store.jdbc.update("UPDATE career_competition_fixture SET scheduled_date=? WHERE career_id=? AND calendar_season_year=? AND competition_id=? AND match_id=? AND lifecycle_status='READY'",bout.date(),career,year,event.name(),bout.id());if(!next.equals(state)||load(store.jdbc,career,year,event)==null)save(store.jdbc,career,next);
            for(int i=0;i<plan.bouts().size();i++)insert(store,career,year,event,plan.bouts().get(i),i+1);
            store.jdbc.update("UPDATE career_competition_instance SET lifecycle_status=?,blocking_reason=NULL WHERE career_id=? AND calendar_season_year=? AND competition_id=?",plan.complete()?"COMPLETED":"RUNNING",career,year,event.name());store.refreshInstanceHash(career,year,event.name());
        }
        store.refreshCycleHash(career,year);
    }
    static Input input(CareerCompetitionRelationalStore store,String career,int year,Event e){
        var db=store.jdbc;State prior=e.previous()==null?null:load(db,career,year,e.previous());if(e.previous()!=null&&(prior==null||!prior.plan().complete()))return null;
        var previous=new TreeMap<String,Integer>();if(prior!=null)for(int i=0;i<prior.plan().ranking().size();i++)previous.put(prior.plan().ranking().get(i),i+1);
        var cp=points(db,career,year,e.league);var teams=new ArrayList<>(partners(e.league));var groups=new TreeMap<String,List<String>>();int slots=4;
        long seed=CareerCompetitionAggregate.deriveSeed(store.careerBinding(career).rootSeed(),year,e.name(),"REGIONAL_DRAW");
        if(e==Event.LEC_VERSUS)teams.addAll(List.of("LEC:LR","LEC:KCB"));
        if(e==Event.LPL_SPLIT_1){var last=load(db,career,year-1,Event.LPL_SPLIT_3);if(last==null)teams.sort(Comparator.comparing(t->hash(seed+"|BOOTSTRAP|"+t)));else teams.sort(Comparator.comparingInt((String t)->last.plan().ranking().indexOf(t)<0?Integer.MAX_VALUE:last.plan().ranking().indexOf(t)).thenComparing(t->hash(seed+"|RETURN|"+t)));groups.put("ASCEND",List.copyOf(teams.subList(0,6)));groups.put("PERSEVERANCE",List.copyOf(teams.subList(6,10)));groups.put("NIRVANA",List.copyOf(teams.subList(10,14)));}
        else if(e==Event.LPL_SPLIT_2||e==Event.LPL_SPLIT_3){teams=new ArrayList<>(lplEntrants(e,prior.plan().ranking(),prior.plan().seasonEliminated()));groups.put("ASCEND",List.copyOf(teams.subList(0,8)));groups.put("NIRVANA",List.copyOf(teams.subList(8,teams.size())));}
        else if(e==Event.LCP_SPLIT_3)teams=new ArrayList<>(prior.plan().ranking());
        else if(e==Event.AMERICAS_CUP){var na=load(db,career,year,Event.LCS_LOCK_IN);var br=load(db,career,year,Event.CBLOL_COPA);if(na==null||br==null||!na.plan().complete()||!br.plan().complete())return null;teams=new ArrayList<>(List.of(na.plan().ranking().get(1),br.plan().ranking().get(1),na.plan().ranking().get(2),br.plan().ranking().get(2)));}
        else if(e==Event.LPL_REGIONAL_FINALS){var msi=CareerInternationalCompetition.load(store,career,year,"MSI");if(msi==null||!msi.plan().complete())return null;String region=msi.entries().stream().filter(v->v.team().equals(msi.plan().champion())).findFirst().orElseThrow().region();String other=msi.plan().regionalPerformance().stream().filter(v->!v.equals(region)).findFirst().orElseThrow();slots=3+(region.equals("LPL")||other.equals("LPL")?1:0);var candidates=cpOrder(prior,cp);String champion=prior.plan().ranking().getFirst();candidates.remove(champion);candidates.removeFirst();teams=new ArrayList<>(candidates.subList(0,4));}
        return new Input(e,year,seed,teams,groups,previous,cp,slots);
    }
    static Map<String,Integer> points(JdbcTemplate db,String career,int year,String league){var points=new TreeMap<String,Integer>();for(Event e:Event.values())if(e.league.equals(league)&&e!=Event.LPL_REGIONAL_FINALS){var s=load(db,career,year,e);if(s!=null&&s.plan().complete())s.plan().points().forEach((t,p)->points.merge(t,p,Integer::sum));}return points;}
    static ArrayList<String> cpOrder(State latest,Map<String,Integer> cp){var result=new ArrayList<>(latest.input().entrants());result.sort(Comparator.comparingInt((String t)->cp.getOrDefault(t,0)).reversed().thenComparing(Comparator.comparingInt((String t)->latest.input().event().league.equals("LPL")?latest.plan().points().getOrDefault(t,0):0).reversed()).thenComparingInt(t->latest.plan().ranking().indexOf(t)));return result;}
    private static void insert(CareerCompetitionRelationalStore store,String career,int year,Event event,Bout b,int order){var db=store.jdbc;if(db.queryForObject("SELECT COUNT(*) FROM career_competition_fixture WHERE career_id=? AND calendar_season_year=? AND competition_id=? AND match_id=?",Integer.class,career,year,event.name(),b.id())>0)return;
        LocalDate date=b.date();var today=CareerMarketStore.date(db,career);if(date.isBefore(today))date=today;
        LocalDate end=event.date(year,event.end).plusDays(SCHEDULE_EXTENSION_DAYS);
        while(!date.isAfter(end)&&db.queryForObject("SELECT COUNT(*) FROM career_competition_fixture WHERE career_id=? AND calendar_season_year=? AND scheduled_date=? AND (first_team_code IN (?,?) OR second_team_code IN (?,?))",Integer.class,career,year,date,b.first(),b.second(),b.first(),b.second())>0)date=date.plusDays(1);
        if(date.isAfter(end))throw new IllegalStateException("OVERSEAS_GLOBAL_SCHEDULE_EXHAUSTED:"+event+":"+b.id());String identity=career+'|'+year+'|'+event+'|'+b.id();
        var fixture=new CareerCompetitionAggregate.Fixture(b.id(),"competition_fixture_"+hash(identity),date,"BO"+b.bestOf(),true,new CareerCompetitionRules.ParticipantSelector("REGISTERED_TEAM",b.first()),new CareerCompetitionRules.ParticipantSelector("REGISTERED_TEAM",b.second()),b.first(),b.second(),"READY","FULL_AUTO",CareerCompetitionAggregate.deriveSeed(store.careerBinding(career).rootSeed(),year,event.name(),b.id()),"series_"+hash("SERIES|"+identity),List.of(),List.of(),null,null,null);
        store.insertFixture(career,year,event.name(),fixture,b.stage(),order,b.group(),null,b.selectionOwner(),null,SIDE,"GAME_DERIVED_SCHEDULE_POLICY");
    }
    /** Delayed execution and international overlaps are scheduled before binding, under the Calendar lock. */
    static void alignSchedule(CareerCompetitionRelationalStore store,String career,int year){
        var db=store.jdbc;if(!active(db,career,year))return;var today=CareerMarketStore.date(db,career);
        var fixtures=store.load(career,year).fixtures();var bound=new HashSet<>(db.query("SELECT fixture_id FROM career_competition_series_binding WHERE career_id=? AND calendar_season_year=?",(r,n)->r.getString(1),career,year));
        var occupied=new HashSet<String>();var pending=new ArrayList<CareerCompetitionRelationalStore.FixtureRow>();
        for(var f:fixtures){boolean movable=(isOverseas(f.competitionId())||CareerInternationalRules.COMPETITIONS.contains(f.competitionId()))&&f.lifecycleStatus().equals("READY")&&!bound.contains(f.fixtureId());
            if(movable)pending.add(f);else{occupied.add(token(f.firstTeamCode())+"|"+f.date());occupied.add(token(f.secondTeamCode())+"|"+f.date());}}
        var roundDates=new CareerCalendarTemplate(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()).leagueRoundDates(year);
        db.query("SELECT round_number,first_team_code,second_team_code FROM league_fixture f JOIN career_season s ON s.season_id=f.season_id WHERE s.career_id=? AND s.season_year=?",(org.springframework.jdbc.core.RowCallbackHandler)r->{occupied.add(token(r.getString(2))+"|"+roundDates.get(r.getInt(1)));occupied.add(token(r.getString(3))+"|"+roundDates.get(r.getInt(1)));},career,year);
        pending.sort(Comparator.comparing(CareerCompetitionRelationalStore.FixtureRow::date).thenComparing(CareerCompetitionRelationalStore.FixtureRow::competitionId).thenComparingInt(CareerCompetitionRelationalStore.FixtureRow::matchOrder));var changed=new TreeSet<String>();
        for(var f:pending){LocalDate date=f.date().isBefore(today)?today:f.date();LocalDate end=scheduleLimit(f.competitionId(),year);String first=token(f.firstTeamCode()),second=token(f.secondTeamCode());
            while(!date.isAfter(end)&&(occupied.contains(first+"|"+date)||occupied.contains(second+"|"+date)))date=date.plusDays(1);
            if(date.isAfter(end))throw new IllegalStateException("OVERSEAS_SCHEDULE_WINDOW_EXHAUSTED:"+f.competitionId()+":"+f.matchId());
            occupied.add(first+"|"+date);occupied.add(second+"|"+date);
            if(!date.equals(f.date())){db.update("UPDATE career_competition_fixture SET scheduled_date=? WHERE career_id=? AND calendar_season_year=? AND competition_id=? AND match_id=?",date,career,year,f.competitionId(),f.matchId());changed.add(f.competitionId());}
        }
        for(String event:changed)store.refreshInstanceHash(career,year,event);if(!changed.isEmpty())store.refreshCycleHash(career,year);
    }
    private static String token(String team){return team==null?"UNRESOLVED":team.contains(":")?team:"LCK:"+team;}
    public CareerClStore.MatchResult result(String career,int year,String event,String match){
        requireEvent(event);return tx.execute(s->CareerClStore.result(db,competitions,career,year,event,match));
    }
    private static Event requireEvent(String event){try{return Event.valueOf(event);}catch(RuntimeException bad){throw CareerException.invalid("event","해외 대회를 선택하세요.");}}
    public View view(String career,int year,String league,String event){return tx.execute(s->{
        lockCareer(db,career);
        if(!List.of("LPL","LEC","LCP","CBLOL","LCS").contains(league)||event!=null&&!requireEvent(event).league.equals(league))throw CareerException.invalid("league","리그에 속한 해외 대회를 선택하세요.");
        var activation=activation(db,career);var views=new ArrayList<EventView>();
        if(active(db,career,year)){
            var cycle=competitions.load(career,year);
            var qualifications=new TreeMap<String,List<CareerInternationalState.Entry>>();
            for(String id:CareerInternationalRules.COMPETITIONS){var international=CareerInternationalCompetition.load(competitions,career,year,id);if(international!=null)qualifications.put(id,international.entries().stream().filter(e->e.region().equals(league)).toList());}
            for(Event e:Event.values())if(e.league.equals(league)&&(event==null||e.name().equals(event))){
                var state=load(db,career,year,e);var instance=competitions.instance(career,year,e.name());
                var fixtures=cycle.fixtures().stream().filter(f->f.competitionId().equals(e.name())).toList();var scores=scores(competitions,career,year,e);
                var records=state==null?Map.<String,CareerOverseasRanking.Record>of():CareerOverseasRanking.records(state.input().entrants(),state.plan().bouts().stream().filter(b->Set.of("REGULAR","SWISS").contains(b.stage())).toList(),scores);
                var cp=new TreeMap<String,Integer>();if(state!=null){cp.putAll(state.input().priorPoints());state.plan().points().forEach((team,point)->cp.merge(team,point,Integer::sum));}
                String waiting=instance.blockingReason()==null?null:e.previous()==null?"선행 대회의 확정 결과를 기다립니다.":e.previous().label+" 결과 확정 후 대진을 생성합니다.";
                views.add(new EventView(e.name(),e.label,e.league,instance.lifecycleStatus(),waiting,state==null?null:state.plan(),fixtures,scores,records,cp,qualifications));
            }
        }
        return new View(career,year,activation,active(db,career,year),year!=activeYear(db,career),league,views,"2026 참고 규칙과 명시된 게임 보완 정책을 적용합니다. 현지 참고 일자를 게임 연도로 옮기며, 충돌 시 한정된 기간 안에서 순연합니다. 실제 완료 결과로 다음 대진·진출권을 확정합니다.");
    });}
}
