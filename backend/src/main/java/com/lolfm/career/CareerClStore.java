package com.lolfm.career;

import java.time.LocalDate;
import java.util.*;
import com.lolfm.domain.Position;
import com.lolfm.player.GlobalTeamRosterCatalog.TeamKey;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.lolfm.career.CareerRosterStore.*;

/** Separate development registration and selection. Membership still denotes the operating club. */
@Service
public final class CareerClStore {
    @org.springframework.beans.factory.annotation.Autowired(required=false) private CareerLifecycleStore lifecycle;
    private final JdbcTemplate db;private final TransactionTemplate tx;private final CareerCompetitionRelationalStore competitions;
    public CareerClStore(JdbcTemplate db,PlatformTransactionManager manager,CareerCompetitionRelationalStore competitions){this.db=db;tx=new TransactionTemplate(manager);this.competitions=competitions;}
    public record State(long revision,Map<String,List<String>> lineups,Map<String,List<String>> registered,List<String> ranking,boolean supplyIssued){
        public State{lineups=Map.copyOf(lineups);registered=Map.copyOf(registered);ranking=List.copyOf(ranking);}
    }
    public record Club(String team,List<String> candidates,List<String> registered,List<String> lineup,List<String> blockers){}
    public record View(String careerId,int seasonYear,Integer activationYear,boolean active,boolean readOnly,long revision,long rosterRevision,
        String managedTeam,List<Club> clubs,List<CareerCompetitionRelationalStore.FixtureRow> fixtures,List<CareerDomesticRanking.Record> standings,List<String> ranking){}
    public record Request(int sourceYear,long expectedRevision,long expectedRosterRevision,String action,List<String> players,String matchId,String clientCommandId){}
    public record Receipt(String clientCommandId,String careerId,int seasonYear,long revision,String payloadHash){}
    public record Change(boolean replayed,Receipt receipt,View cl){}
    public static Integer activation(JdbcTemplate db,String career){var rows=db.query("SELECT cl_activation_year FROM career_player_directory WHERE career_id=?",(r,n)->(Integer)r.getObject(1),career);return rows.isEmpty()?null:rows.getFirst();}
    public static boolean active(JdbcTemplate db,String career,int year){Integer start=activation(db,career);return start!=null&&year>=start;}
    static State load(JdbcTemplate db,String career,int year){var rows=db.query("SELECT state_json,state_hash FROM career_cl_state WHERE career_id=? AND season_year=?",(r,n)->{if(!hash(r.getString(1)).equals(r.getString(2)))throw new IllegalStateException("CL_STATE_INTEGRITY");return read(r.getString(1),State.class);},career,year);if(rows.isEmpty())throw new IllegalStateException("CL_STATE_REQUIRED");return rows.getFirst();}
    static void save(JdbcTemplate db,String career,int year,State state){String json=write(state);if(db.update("UPDATE career_cl_state SET state_json=?,state_hash=? WHERE career_id=? AND season_year=?",json,hash(json),career,year)!=1)throw new IllegalStateException("CL_STATE_REQUIRED");}
    static void initializeState(JdbcTemplate db,String career,int year){
        if(db.queryForObject("SELECT COUNT(*) FROM career_cl_state WHERE career_id=? AND season_year=?",Integer.class,career,year)>0)return;
        String json=write(new State(0,Map.of(),Map.of(),List.of(),false));db.update("INSERT INTO career_cl_state VALUES (?,?,?,?)",career,year,json,hash(json));
    }
    public void initializeNew(String career,int year){tx.executeWithoutResult(s->{lockCareer(db,career);if(activation(db,career)!=null)return;
        db.update("UPDATE career_player_directory SET cl_activation_year=?,cl_introduced_on=? WHERE career_id=?",year,CareerMarketStore.date(db,career),career);
        new CareerClCompetition(competitions).initialize(career,year);if(lifecycle!=null)lifecycle.prepareClSupply(career,year);competitions.refreshAllInstanceHashes(career,year);competitions.refreshCycleHash(career,year);
    });}
    public void recover(){for(String id:db.query("SELECT career_id FROM career_player_directory ORDER BY career_id",(r,n)->r.getString(1)))tx.executeWithoutResult(s->{lockCareer(db,id);if(activation(db,id)==null)db.update("UPDATE career_player_directory SET cl_activation_year=?,cl_introduced_on=? WHERE career_id=?",activeYear(db,id)+1,CareerMarketStore.date(db,id),id);else if(active(db,id,activeYear(db,id)))load(db,id,activeYear(db,id));});}
    static List<String> candidates(CareerMarketEngine m,String team){return candidates(m,team,m.state().processedThrough());}
    private static List<String> candidates(CareerMarketEngine m,String team,LocalDate date){return m.members.values().stream().filter(v->team.equals(v.ownerTeam())&&"DEVELOPMENT".equals(v.squad())&&m.eligible(v.playerId(),team,date)).map(Membership::playerId).sorted(Comparator.comparingInt((String id)->CareerMarketPolicy.strength(m.player(id))).reversed().thenComparing(id->id)).toList();}
    /** Called by an explicit market/date mutation, never GET. AI cannot remove a first-team starter. */
    static void prepare(JdbcTemplate db,String career,int year){if(!active(db,career,year))return;var old=CareerMarketStore.load(db,career);var m=CareerMarketStore.engine(db,career,year,old);prepare(db,career,year,m);CareerMarketStore.persist(db,career,year,old,m);}
    static void prepare(JdbcTemplate db,String career,int year,CareerMarketEngine m){
        if(!active(db,career,year))return;var state=load(db,career,year);var lineups=new TreeMap<>(state.lineups());var registered=new TreeMap<>(state.registered());
        m.planner.repair(m.state().processedThrough());
        for(String code:CareerClPolicy.TEAMS){String team="LCK:"+code;if(team.equals(m.managed))continue;
            var eligible=candidates(m,team);var selected=m.clLineups.getOrDefault(team,List.of()).stream().filter(eligible::contains).toList();
            lineups.put(team,selected);var pool=new ArrayList<>(selected);eligible.stream().filter(id->!pool.contains(id)).limit(CareerClPolicy.MAXIMUM_REGISTERED-pool.size()).forEach(pool::add);registered.put(team,List.copyOf(pool));
        }
        if(!state.lineups().equals(lineups)||!state.registered().equals(registered))save(db,career,year,new State(state.revision()+1,lineups,registered,state.ranking(),state.supplyIssued()));
    }
    static List<String> blockers(CareerMarketEngine m,State state,String team){
        var reasons=new ArrayList<String>();var eligible=candidates(m,team);var lineup=state.lineups().getOrDefault(team,List.of());var pool=state.registered().getOrDefault(team,List.of());
        for(Position role:Position.values())if(lineup.stream().filter(eligible::contains).filter(pool::contains).noneMatch(id->m.player(id).position()==role))reasons.add(role+": 육성팀 배치·유효 계약·CL 등록 후 선발을 지정하세요.");
        if(lineup.size()!=5||new HashSet<>(lineup).size()!=5)reasons.add("CL 선발 5명을 확정하세요.");return reasons;
    }
    public static CompetitionRosterSnapshot pair(JdbcTemplate db,String career,int year,String first,String second){
        var state=load(db,career,year);var m=CareerMarketStore.engine(db,career,year,CareerMarketStore.load(db,career));var rosters=new TreeMap<String,CompetitionRosterSnapshot.Roster>();
        for(String code:List.of(first,second)){String team=code.startsWith("LCK:")?code:"LCK:"+code;var reasons=blockers(m,state,team);if(!reasons.isEmpty())throw CareerException.invalid("cl",team+" CL: "+String.join(" / ",reasons));
            var players=state.lineups().get(team).stream().map(id->m.player(id).gameplay()).toList();rosters.put(team,new CompetitionRosterSnapshot.Roster(new TeamKey("LCK",team.substring(4)),hash(write(players)),players));}
        CareerAppearanceStore.requireNoCrossSquad(db,career,"CL_PRECHECK",m.state().processedThrough(),"DEVELOPMENT",rosters.values().stream().flatMap(r->r.players().stream()).map(CompetitionRosterSnapshot.Starter::playerId).collect(java.util.stream.Collectors.toSet()));
        return new CompetitionRosterSnapshot(rosters);
    }
    public View view(String career,int year){return tx.execute(s->{lockCareer(db,career);return readView(career,year);});}
    private View readView(String career,int year){var roster=saved(db,career,year);if(roster==null)throw CareerException.notFound();boolean enabled=active(db,career,year);String managed="LCK:"+competitions.careerBinding(career).managedTeamCode();
        if(!enabled)return new View(career,year,activation(db,career),false,year!=activeYear(db,career),0,roster.revision(),managed,List.of(),List.of(),List.of(),List.of());
        var state=load(db,career,year);boolean historical=year!=activeYear(db,career);var m=CareerMarketStore.engine(db,career,year,CareerMarketStore.load(db,career));var clubs=new ArrayList<Club>();
        for(String code:CareerClPolicy.TEAMS){String team="LCK:"+code;clubs.add(new Club(team,historical?List.of():candidates(m,team),state.registered().getOrDefault(team,List.of()),state.lineups().getOrDefault(team,List.of()),historical?List.of():blockers(m,state,team)));}
        var matches=CareerDomesticEvidence.competition(db,competitions.json,career,year,CareerClPolicy.ID,"CL_REGULAR");var decision=CareerDomesticRanking.development(CareerClPolicy.TEAMS,matches);
        return new View(career,year,activation(db,career),true,historical,state.revision(),roster.revision(),managed,clubs,competitions.load(career,year).fixtures().stream().filter(f->CareerClPolicy.isCl(f.competitionId())).toList(),decision.ordered().stream().map(decision.records()::get).toList(),state.ranking());
    }
    public record Pick(String playerId,String nickname,Position position,String team,String champion){}
    public record GameResult(int game,String blueTeam,String redTeam,String winner,int durationSeconds,List<Pick> picks){}
    public record MatchResult(String careerId,int seasonYear,String matchId,String seriesId,String firstTeam,String secondTeam,int firstScore,int secondScore,List<GameResult> games,boolean replayAvailable){}
    public MatchResult result(String career,int year,String match){return tx.execute(s->{lockCareer(db,career);
        var binding=competitions.loadBinding(career,year,CareerClPolicy.ID,match);if(!competitions.hasAppliedCompletion(binding))throw CareerException.invalid("matchId","아직 검증·정산이 끝나지 않은 경기입니다.");
        var receipt=db.queryForObject("SELECT receipt_json,receipt_hash,receipt_canonical FROM career_competition_completion_receipt WHERE binding_hash=?",(r,n)->{
            var value=read(r.getString(1),CareerCompetitionFixtureCompletionReceiptV1.class);if(!value.receiptHash().equals(r.getString(2))||!value.canonicalText().equals(r.getString(3)))throw new IllegalStateException("CL_RESULT_INTEGRITY");return value;
        },binding.bindingHash());
        var names=new TreeMap<String,String>();if(binding.frozenRosters()!=null)binding.frozenRosters().teams().values().forEach(t->t.players().forEach(p->names.put(p.playerId(),p.nickname())));
        var games=new ArrayList<GameResult>();for(var g:receipt.orderedGames()){var picks=g.orderedFinalAssignments().stream().map(p->new Pick(p.playerId().value(),names.getOrDefault(p.playerId().value(),p.playerId().value()),p.position(),p.teamSide()==com.lolfm.simulator.TeamSide.BLUE?g.blueTeamCode():g.redTeamCode(),p.championId().value())).toList();games.add(new GameResult(g.gameNumber(),g.blueTeamCode(),g.redTeamCode(),g.winnerTeamCode(),g.durationSeconds(),picks));}
        boolean replay=db.queryForObject("SELECT COUNT(*) FROM career_competition_series_checkpoint WHERE binding_hash=? AND series_status='COMPLETED'",Integer.class,binding.bindingHash())>0;
        return new MatchResult(career,year,match,binding.boundSeriesId(),binding.firstTeamCode(),binding.secondTeamCode(),receipt.firstScore(),receipt.secondScore(),games,replay);
    });}
    public Change change(String career,Request r){if(r==null||r.action()==null)throw CareerException.invalid("cl","CL 명령을 확인하세요.");String uuid;try{uuid=CareerIdentity.canonicalCommandId(r.clientCommandId());}catch(RuntimeException e){throw CareerException.invalid("clientCommandId","원본 UUID가 필요합니다.");}String payload=hash(career+'|'+write(r));
        return tx.execute(s->{lockCareer(db,career);var prior=db.query("SELECT payload_hash,receipt_json,receipt_hash FROM career_cl_command WHERE client_command_id=?",(v,n)->{if(!payload.equals(v.getString(1)))throw CareerException.calendarCommandConflict();if(!hash(v.getString(2)).equals(v.getString(3)))throw new IllegalStateException("CL_RECEIPT_INTEGRITY");return read(v.getString(2),Receipt.class);},uuid);
            if(!prior.isEmpty())return new Change(true,prior.getFirst(),readView(career,activeYear(db,career)));
            if(r.sourceYear()!=activeYear(db,career)||!active(db,career,r.sourceYear()))throw CareerException.calendarStaleRevision();var state=load(db,career,r.sourceYear());
            if(state.revision()!=r.expectedRevision()||saved(db,career,r.sourceYear()).revision()!=r.expectedRosterRevision())throw CareerException.calendarStaleRevision();
            var m=CareerMarketStore.engine(db,career,r.sourceYear(),CareerMarketStore.load(db,career));
            if("CONFIRM_LINEUP".equals(r.action())){
                var eligible=candidates(m,m.managed);var ids=r.players();if(ids==null||ids.size()!=5||new HashSet<>(ids).size()!=5||!eligible.containsAll(ids)||ids.stream().map(id->m.player(id).position()).distinct().count()!=5)throw CareerException.invalid("players","각 포지션에 계약이 유효한 육성팀 선수 1명을 선택하세요.");
                var lineups=new TreeMap<>(state.lineups());lineups.put(m.managed,List.copyOf(ids));var pool=new TreeMap<>(state.registered());var registered=new ArrayList<>(ids);eligible.stream().filter(id->!ids.contains(id)).limit(CareerClPolicy.MAXIMUM_REGISTERED-5).forEach(registered::add);pool.put(m.managed,List.copyOf(registered));
                state=new State(state.revision()+1,lineups,pool,state.ranking(),state.supplyIssued());save(db,career,r.sourceYear(),state);
            }else if("SELECT_AUTO".equals(r.action())){
                var cycle=competitions.load(career,r.sourceYear());var next=cycle.fixtures().stream().filter(f->!"COMPLETED".equals(f.lifecycleStatus())).findFirst().orElseThrow(CareerException::notFound);
                if(!CareerClPolicy.isCl(next.competitionId())||!next.matchId().equals(r.matchId())||!Set.of(next.firstTeamCode(),next.secondTeamCode()).contains(m.managed.substring(4)))throw CareerException.invalid("matchId","현재 관리 구단의 다음 CL 경기만 선택할 수 있습니다.");
                if(db.queryForObject("SELECT COUNT(*) FROM career_competition_series_binding WHERE career_id=? AND calendar_season_year=? AND competition_id='LCK_CL' AND match_id=?",Integer.class,career,r.sourceYear(),r.matchId())>0)throw CareerException.invalid("matchId","이미 시작한 Series의 실행 방식은 바꿀 수 없습니다.");
                db.update("UPDATE career_competition_fixture SET execution_mode='FULL_AUTO' WHERE career_id=? AND calendar_season_year=? AND competition_id='LCK_CL' AND match_id=?",career,r.sourceYear(),r.matchId());
                state=new State(state.revision()+1,state.lineups(),state.registered(),state.ranking(),state.supplyIssued());save(db,career,r.sourceYear(),state);competitions.refreshInstanceHash(career,r.sourceYear(),CareerClPolicy.ID);competitions.refreshCycleHash(career,r.sourceYear());
            }else throw CareerException.invalid("action","지원하지 않는 CL 명령입니다.");
            var receipt=new Receipt(uuid,career,r.sourceYear(),state.revision(),payload);String json=write(receipt);db.update("INSERT INTO career_cl_command VALUES (?,?,?,?,?,?)",uuid,career,r.sourceYear(),payload,json,hash(json));return new Change(false,receipt,readView(career,r.sourceYear()));
        });
    }
}
