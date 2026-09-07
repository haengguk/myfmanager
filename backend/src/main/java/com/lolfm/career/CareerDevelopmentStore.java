package com.lolfm.career;

import com.lolfm.champion.ChampionCatalog;
import com.lolfm.league.LeagueFixtureGameReceiptV1;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.lolfm.career.CareerRosterStore.*;
import static com.lolfm.career.CareerDevelopmentState.*;

@Service
public final class CareerDevelopmentStore {
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final Set<String> legal;
    public CareerDevelopmentStore(JdbcTemplate jdbc,PlatformTransactionManager manager,ChampionCatalog champions) {
        this.jdbc=jdbc;tx=new TransactionTemplate(manager);legal=champions.legalRoleKeys().stream().map(k->CareerDevelopmentPolicy.key(k.championId().value(),k.position())).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public record Saved(long revision,CareerDevelopmentState state) {}
    public record Request(String schemaVersion,int sourceYear,long expectedRevision,String playerId,Plan plan,boolean clearOverride,String clientCommandId) {}
    public record Receipt(String clientCommandId,String careerId,int sourceYear,long resultingRevision,LocalDate effectiveOn,String stateHash) {}
    public record Change(boolean replayed,Receipt receipt,View development) {}
    public record Profile(String playerId,Integer potentialAbility,int currentAbility,String growthStatus,Player development,Plan effectivePlan,int trainingEfficiency) {}
    public record View(String schemaVersion,String careerId,int seasonYear,long revision,LocalDate currentDate,String managedTeam,boolean readOnly,
            Schedule teamPlan,List<Profile> players,List<Gain> recentChanges,List<Gain> monthlySummaries,Map<String,List<String>> legalChampions,String policyVersion) {}
    public static Saved load(JdbcTemplate jdbc,String career) {
        var markers=jdbc.query("SELECT development_version FROM career_player_directory WHERE career_id=?",(r,n)->r.getString(1),career);
        if(markers.isEmpty()||markers.getFirst()==null) {
            if(jdbc.queryForObject("SELECT COUNT(*) FROM career_development_state WHERE career_id=?",Integer.class,career)>0)throw new IllegalStateException("DEVELOPMENT_INITIALIZATION_CONFLICT");
            return null;
        }
        if(!CareerDevelopmentPolicy.VERSION.equals(markers.getFirst()))throw new IllegalStateException("DEVELOPMENT_POLICY_UNSUPPORTED");
        var rows=jdbc.query("SELECT revision,state_json,state_hash FROM career_development_state WHERE career_id=?",(r,n)->{
            if(!hash(r.getString(2)).equals(r.getString(3)))throw new IllegalStateException("DEVELOPMENT_STATE_INTEGRITY");
            return new Saved(r.getLong(1),read(r.getString(2),CareerDevelopmentState.class));
        },career);
        if(rows.isEmpty())throw new IllegalStateException("DEVELOPMENT_REQUIRED_STATE_MISSING");return rows.getFirst();
    }
    public static void initialize(JdbcTemplate jdbc,String career) {
        lockCareer(jdbc,career);if(load(jdbc,career)!=null)return;
        if(jdbc.queryForObject("SELECT COUNT(*) FROM career_player_directory WHERE career_id=?",Integer.class,career)==0)return;
        String json=write(CareerDevelopmentEngine.initial(baseDirectory(jdbc,career),CareerMarketStore.date(jdbc,career)));
        if(jdbc.queryForObject("SELECT COUNT(*) FROM career_development_state WHERE career_id=?",Integer.class,career)>0)throw new IllegalStateException("DEVELOPMENT_INITIALIZATION_CONFLICT");
        jdbc.update("INSERT INTO career_development_state VALUES (?,0,?,?)",career,json,hash(json));
        jdbc.update("UPDATE career_player_directory SET development_version=? WHERE career_id=?",CareerDevelopmentPolicy.VERSION,career);
    }
    public void recover(){for(String id:jdbc.query("SELECT career_id FROM career_player_directory ORDER BY career_id",(r,n)->r.getString(1)))tx.executeWithoutResult(status->initialize(jdbc,id));}
    static void persist(JdbcTemplate jdbc,String career,Saved old,CareerDevelopmentEngine engine) {
        String json=write(engine.state());
        if(jdbc.update("UPDATE career_development_state SET revision=revision+1,state_json=?,state_hash=? WHERE career_id=? AND revision=?",json,hash(json),career,old.revision())!=1)throw CareerException.calendarStaleRevision();
        var life=CareerLifecycleStore.load(jdbc,career);if(life!=null){var lifecycle=new CareerLifecycleEngine(life);engine.players.forEach((id,p)->{var person=lifecycle.people.get(id);if(person==null)throw new IllegalStateException("LIFECYCLE_PLAYER_REFERENCE");lifecycle.people.put(id,person.peak(CareerLifecyclePolicy.ca(p)));});CareerLifecycleStore.persist(jdbc,career,lifecycle);}
    }
    public static Directory current(JdbcTemplate jdbc,String career,Directory base){var s=load(jdbc,career);return s==null?base:new CareerDevelopmentEngine(base,s.state()).directory();}
    public static Directory historicalDirectory(JdbcTemplate jdbc,String career,int year) {
        var base=baseDirectory(jdbc,career);var rows=closed(jdbc,career,year);
        return rows.isEmpty()?sourceDirectory(jdbc,career):new CareerDevelopmentEngine(historicalBase(jdbc,career,year,base,rows.getFirst()),rows.getFirst()).directory();
    }
    private static Directory historicalBase(JdbcTemplate jdbc,String career,int year,Directory base,CareerDevelopmentState state) {
        var historicalLife=jdbc.query("SELECT lifecycle_json,lifecycle_hash FROM career_development_season_close WHERE career_id=? AND season_year=?",(r,n)->{
            String json=r.getString(1);if(json==null)return null;if(!hash(json).equals(r.getString(2)))throw new IllegalStateException("LIFECYCLE_HISTORY_INTEGRITY");return read(json,CareerLifecycleState.class);
        },career,year);
        var life=historicalLife.isEmpty()?null:historicalLife.getFirst();
        var definitions=new TreeMap<String,com.lolfm.player.ExpandedPlayerCatalog.Definition>();
        for(String id:state.players().keySet()) {
            var d=base.players().get(id);if(d==null)throw new IllegalStateException("DEVELOPMENT_HISTORY_REFERENCE");
            var details=read(d.detailsJson(),com.fasterxml.jackson.databind.node.ObjectNode.class);details.remove("careerLifecycleStatus");
            if(life!=null){var person=life.players().get(id);if(person==null)throw new IllegalStateException("LIFECYCLE_HISTORY_REFERENCE");details.put("careerLifecycleStatus",person.status().name());}
            definitions.put(id,new com.lolfm.player.ExpandedPlayerCatalog.Definition(id,d.nickname(),d.position(),d.gameplay(),d.provisional(),d.initialOrganizationId(),d.initialOwnerTeam(),d.initialSquad(),d.eligibilityReason(),details.toString()));
        }
        return new Directory(definitions,base.organizations());
    }
    private static List<CareerDevelopmentState> closed(JdbcTemplate jdbc,String career,int year) {
        return jdbc.query("SELECT state_json,state_hash FROM career_development_season_close WHERE career_id=? AND season_year=?",(r,n)->{
            if(!hash(r.getString(1)).equals(r.getString(2)))throw new IllegalStateException("DEVELOPMENT_SEASON_INTEGRITY");return read(r.getString(1),CareerDevelopmentState.class);
        },career,year);
    }
    public static void closeSeason(JdbcTemplate jdbc,String career,int year,LocalDate date) {
        var saved=load(jdbc,career);if(saved==null||!closed(jdbc,career,year).isEmpty())return;String json=write(saved.state());
        jdbc.update("INSERT INTO career_development_season_close(career_id,season_year,closed_date,state_json,state_hash) VALUES (?,?,?,?,?)",career,year,date,json,hash(json));
    }
    /** Finalize the still-active season after its last day, before switching the active year. */
    public static void finishSeason(JdbcTemplate jdbc,String career,int year) {
        var saved=load(jdbc,career);if(saved==null)return;
        if(activeYear(jdbc,career)!=year)throw new IllegalStateException("DEVELOPMENT_SEASON_ALREADY_CLOSED");
        closeSeason(jdbc,career,year,saved.state().nextSettlement());String json=write(saved.state());
        jdbc.update("UPDATE career_development_season_close SET closed_date=?,state_json=?,state_hash=? WHERE career_id=? AND season_year=?",saved.state().nextSettlement(),json,hash(json),career,year);
        var life=CareerLifecycleStore.load(jdbc,career);if(life!=null){String lifecycle=write(life);jdbc.update("UPDATE career_development_season_close SET lifecycle_json=?,lifecycle_hash=? WHERE career_id=? AND season_year=?",lifecycle,hash(lifecycle),career,year);}
    }
    static Map<String,List<LocalDate>> fixtures(JdbcTemplate jdbc,String career) {
        var result=new TreeMap<String,List<LocalDate>>();
        jdbc.query("SELECT scheduled_date,first_team_code,second_team_code FROM career_competition_fixture WHERE career_id=? AND lifecycle_status<>'COMPLETED'",(org.springframework.jdbc.core.RowCallbackHandler)r->{
            for(int col:List.of(2,3)){String team=r.getString(col);if(team!=null)result.computeIfAbsent(team.contains(":")?team:"LCK:"+team,k->new ArrayList<>()).add(r.getObject(1,LocalDate.class));}
        },career);
        var template=new CareerCalendarTemplate(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
        int year=activeYear(jdbc,career);var rounds=template.leagueRoundDates(year);
        jdbc.query("SELECT f.round_number,f.first_team_code,f.second_team_code FROM league_fixture f JOIN career_season s ON s.season_id=f.season_id WHERE s.career_id=? AND s.season_year=? AND f.lifecycle_status<>'COMPLETED'",(org.springframework.jdbc.core.RowCallbackHandler)r->{
            LocalDate date=rounds.get(r.getInt(1));
            for(int col:List.of(2,3))result.computeIfAbsent("LCK:"+r.getString(col),k->new ArrayList<>()).add(date);
        },career,year);return result;
    }
    static void capture(JdbcTemplate jdbc,String career,String identity) {
        if(load(jdbc,career)==null)return;
        jdbc.update("INSERT INTO career_development_binding VALUES (?,?,?,NULL)",career,identity,CareerDevelopmentPolicy.VERSION);
    }
    public static void complete(JdbcTemplate jdbc,String career,String identity,String receipt,List<LeagueFixtureGameReceiptV1> games) {
        var bindings=jdbc.query("SELECT policy_version,completion_hash FROM career_development_binding WHERE career_id=? AND fixture_identity=?",(r,n)->new String[]{r.getString(1),r.getString(2)},career,identity);
        if(bindings.isEmpty())return; // Explicit legacy exclusion, including starts before initialization.
        var b=bindings.getFirst();String payload=hash(receipt+'|'+write(games));
        if(!CareerDevelopmentPolicy.VERSION.equals(b[0]))throw new IllegalStateException("DEVELOPMENT_BINDING_VERSION");
        if(b[1]!=null){if(!payload.equals(b[1]))throw new IllegalStateException("DEVELOPMENT_COMPLETION_CONFLICT");return;}
        if(games.isEmpty())throw new IllegalStateException("DEVELOPMENT_GAME_EVIDENCE_REQUIRED");
        var captured=jdbc.queryForObject("SELECT snapshot_json FROM career_appearance_binding WHERE career_id=? AND fixture_identity=?",String.class,career,identity);
        var a=read(captured,CareerManagementState.Appearance.class);var selected=a.opportunities().stream().filter(CareerManagementState.Opportunity::selected).map(CareerManagementState.Opportunity::playerId).collect(java.util.stream.Collectors.toSet());
        var old=load(jdbc,career);if(old==null)throw new IllegalStateException("DEVELOPMENT_REQUIRED_STATE_MISSING");
        var engine=new CareerDevelopmentEngine(baseDirectory(jdbc,career),old.state());
        for(var game:games) {
            var ids=game.orderedFinalAssignments().stream().map(v->v.playerId().value()).collect(java.util.stream.Collectors.toSet());
            if(!ids.equals(selected)||ids.size()!=10)throw new IllegalStateException("DEVELOPMENT_ASSIGNMENT_BINDING");
            for(var v:game.orderedFinalAssignments())engine.game(v.playerId().value(),v.championId().value(),v.position(),a.date(),a.seasonYear());
        }
        persist(jdbc,career,old,engine);
        jdbc.update("UPDATE career_development_binding SET completion_hash=? WHERE career_id=? AND fixture_identity=?",payload,career,identity);
    }
    public View view(String career,int year){return tx.execute(s->{lockCareer(jdbc,career);return readView(career,year);});}
    private View readView(String career,int year) {
        var saved=load(jdbc,career);if(saved==null)throw CareerException.invalid("development","서버 시작 시 성장 이주가 필요합니다.");
        var history=year==activeYear(jdbc,career)?List.<CareerDevelopmentState>of():closed(jdbc,career,year);
        var state=history.isEmpty()?saved.state():history.getFirst();
        var base=baseDirectory(jdbc,career);var engine=new CareerDevelopmentEngine(history.isEmpty()?base:historicalBase(jdbc,career,year,base,state),state);var roster=CareerRosterStore.saved(jdbc,career,year);if(roster==null)throw CareerException.notFound();
        LocalDate date=history.isEmpty()?CareerMarketStore.date(jdbc,career):state.nextSettlement();String managed=managed(jdbc,career);var fixtures=year==activeYear(jdbc,career)?fixtures(jdbc,career):Map.<String,List<LocalDate>>of();var profiles=new ArrayList<Profile>();
        for(var entry:(year!=activeYear(jdbc,career)&&history.isEmpty()?Map.<String,Player>of():engine.players).entrySet()) {
            String id=entry.getKey();var p=entry.getValue();var m=engine.metadata.get(id);int sum=CareerDevelopmentPolicy.sum(p),ca=com.lolfm.player.PlayerAbilityPolicy.currentAbility(p.internalRatings().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue()/1000)));
            String status=engine.retired.contains(id)?"RETIRED":m.potential()==null?"PA_MISSING":sum>CareerDevelopmentPolicy.ceiling(m.potential())?ca>m.potential()?"INITIAL_CA_ABOVE_PA":"INITIAL_ROUNDING_BOUNDARY":sum==CareerDevelopmentPolicy.ceiling(m.potential())?"PA_REACHED":"GROWING";
            var member=roster.state().members().get(id);var dates=member.ownerTeam()==null?List.<LocalDate>of():fixtures.getOrDefault(member.ownerTeam(),List.of());
            profiles.add(new Profile(id,m.potential(),ca,status,p,engine.effective(id,managed,member,date,dates),CareerDevelopmentPolicy.efficiency(p.fatigue())));
        }
        var legalByRole=new TreeMap<String,List<String>>();for(var role:com.lolfm.domain.Position.values())legalByRole.put(role.name(),legal.stream().filter(k->k.endsWith("|"+role)).map(k->k.substring(0,k.lastIndexOf('|'))).sorted().toList());
        return new View("CAREER_DEVELOPMENT_VIEW_V1",career,year,year==activeYear(jdbc,career)?saved.revision():0,date,managed,year!=activeYear(jdbc,career),CareerDevelopmentPolicy.activate(engine.teams.get(managed),date,managed),profiles,
            engine.gains.values().stream().filter(g->g.seasonYear()==year).toList(),engine.monthly.values().stream().filter(g->g.seasonYear()==year).toList(),legalByRole,CareerDevelopmentPolicy.VERSION);
    }
    private static String managed(JdbcTemplate jdbc,String career){return "LCK:"+jdbc.queryForObject("SELECT managed_team_code FROM career_save WHERE career_id=?",String.class,career);}
    public Change change(String career,Request request) {
        if(request==null||!"CAREER_TRAINING_COMMAND_V1".equals(request.schemaVersion())||request.expectedRevision()<0)throw CareerException.invalid(null,"훈련 요청 형식을 확인하세요.");
        String command;try{command=CareerIdentity.canonicalCommandId(request.clientCommandId());}catch(RuntimeException e){throw CareerException.invalid("clientCommandId","원본 UUID가 필요합니다.");}
        String payload=hash(career+'|'+write(request));
        return tx.execute(status->{
            lockCareer(jdbc,career);
            var prior=jdbc.query("SELECT payload_hash,receipt_json,receipt_hash FROM career_training_command WHERE career_id=? AND client_command_id=?",(r,n)->{
                if(!payload.equals(r.getString(1)))throw CareerException.calendarCommandConflict();if(!hash(r.getString(2)).equals(r.getString(3)))throw new IllegalStateException("TRAINING_RECEIPT_INTEGRITY");return read(r.getString(2),Receipt.class);
            },career,command);
            if(!prior.isEmpty())return new Change(true,prior.getFirst(),readView(career,activeYear(jdbc,career)));
            var old=load(jdbc,career);if(old==null||old.revision()!=request.expectedRevision()||request.sourceYear()!=activeYear(jdbc,career))throw CareerException.calendarStaleRevision();
            String managed=managed(jdbc,career);var engine=new CareerDevelopmentEngine(baseDirectory(jdbc,career),old.state());var date=CareerMarketStore.date(jdbc,career);var when=date.plusDays(1);
            String id=request.playerId();var definition=id==null?null:engine.base.players().get(id);
            if(id!=null&&(definition==null||engine.retired.contains(id)||!managed.equals(CareerRosterStore.saved(jdbc,career,request.sourceYear()).state().members().get(id).ownerTeam())))throw CareerException.invalid("playerId","현재 우리 구단에서 훈련하는 선수만 변경할 수 있습니다.");
            try {if(!request.clearOverride())CareerDevelopmentPolicy.validate(request.plan(),definition,legal);else if(id==null||request.plan()!=null)throw new IllegalArgumentException();}
            catch(IllegalArgumentException e){throw CareerException.invalid("plan","포지션에 맞는 능력치 또는 합법 챔피언 1~2개와 훈련 강도를 확인하세요.");}
            var previous=CareerDevelopmentPolicy.activate(id==null?engine.teams.get(managed):engine.players.get(id).override(),date,managed);
            var schedule=new Schedule(managed,previous==null?(id==null?CareerDevelopmentPolicy.DEFAULT:null):previous.current(),previous==null?date:previous.effectiveOn(),request.plan(),when,request.clearOverride());
            if(id==null)engine.teams.put(managed,schedule);else {var p=engine.players.get(id);engine.players.put(id,CareerDevelopmentPolicy.copy(p,p.fatigue(),p.playedOn(),schedule));}
            persist(jdbc,career,old,engine);var receipt=new Receipt(command,career,request.sourceYear(),old.revision()+1,when,hash(write(engine.state())));String json=write(receipt);
            jdbc.update("INSERT INTO career_training_command VALUES (?,?,?,?,?)",career,command,payload,json,hash(json));return new Change(false,receipt,readView(career,request.sourceYear()));
        });
    }
}
