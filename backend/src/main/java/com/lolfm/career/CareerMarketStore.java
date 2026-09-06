package com.lolfm.career;

import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.lolfm.career.CareerMarketState.*;
import static com.lolfm.career.CareerRosterStore.*;

/** Calendar-row serialized market, membership and financial mutations with durable original-command receipts. */
@Service
public final class CareerMarketStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final CareerSeasonApplicationService seasons;
    public CareerMarketStore(JdbcTemplate jdbc,PlatformTransactionManager manager,CareerSeasonApplicationService seasons) {
        this.jdbc=jdbc;transactions=new TransactionTemplate(manager);this.seasons=seasons;
    }
    public record Saved(long revision,CareerMarketState state) {}
    public record Request(String schemaVersion,int sourceYear,long expectedRevision,String action,String playerId,
            String offerId,Terms terms,String replacementPlayerId,String competitionId,String clientCommandId) {}
    public record Receipt(String clientCommandId,String careerId,int sourceYear,long resultingRevision,String action,
            String referenceId,LocalDate appliedDate,String reason) {}
    public record Change(boolean replayed,Receipt receipt,View market) {}
    public record PlayerMarket(String playerId,String status,String currentContractId,String scheduledContractId,
            LocalDate availableStart,long askingSalary,long releaseCost,String eligibilityReason,Preference preference) {}
    public record Finance(String team,long annualBudget,long cash,long reservedCash,long currentAnnualSalary,long committedPeakSalary,int rosterLimit) {}
    public record Supplement(String competitionId,String team,String playerId,String position,LocalDate date,long revision,String reason) {}
    public record View(String schemaVersion,String policyVersion,String currency,String careerId,int seasonYear,
            LocalDate currentDate,long revision,boolean readOnly,String managedTeam,boolean offseason,
            LocalDate nextMarketEvent,List<PlayerMarket> players,List<Contract> contracts,List<Offer> offers,
            List<Finance> finances,List<Decision> decisions,List<Event> events,List<Ledger> ledger,
            Map<String,List<String>> missingPositions,List<Supplement> supplements,List<String> allowedCommands,String registrationPolicy) {}
    public static Saved load(JdbcTemplate jdbc,String career) {
        var rows=jdbc.query("SELECT revision,state_json,state_hash FROM career_market_state WHERE career_id=?",(r,n)->{
            if(!hash(r.getString(2)).equals(r.getString(3)))throw new IllegalStateException("MARKET_STATE_INTEGRITY");
            return new Saved(r.getLong(1),read(r.getString(2),CareerMarketState.class));
        },career);return rows.isEmpty()?null:rows.getFirst();
    }
    public static LocalDate date(JdbcTemplate jdbc,String career) {
        return jdbc.queryForObject("SELECT current_game_date FROM career_calendar_state WHERE career_id=?",LocalDate.class,career);
    }
    private static String managed(JdbcTemplate jdbc,String career) {return "LCK:"+jdbc.queryForObject("SELECT managed_team_code FROM career_save WHERE career_id=?",String.class,career);}
    public static void initialize(JdbcTemplate jdbc,String career) {
        lockCareer(jdbc,career);if(load(jdbc,career)!=null)return;
        int year=activeYear(jdbc,career);var roster=CareerRosterStore.saved(jdbc,career,year);if(roster==null)return;
        long seed=jdbc.queryForObject("SELECT career_root_seed FROM career_save WHERE career_id=?",Long.class,career);
        int first=jdbc.queryForObject("SELECT MIN(season_year) FROM career_season WHERE career_id=?",Integer.class,career);
        var state=CareerMarketEngine.initialize(career,seed,date(jdbc,career),first,directory(jdbc,career),roster.state());
        String json=write(state);jdbc.update("INSERT INTO career_market_state VALUES (?,0,?,?)",career,json,hash(json));
        var operating=new CareerRosterStore.State(OPERATING_POLICY,roster.state().members(),roster.state().lineups());
        String r=write(operating);jdbc.update("UPDATE career_roster_state SET state_json=?,state_hash=? WHERE career_id=? AND season_year=?",r,hash(r),career,year);
    }
    public void recover() {
        var ids=jdbc.query("SELECT s.career_id FROM career_save s LEFT JOIN career_market_state m ON m.career_id=s.career_id WHERE m.career_id IS NULL ORDER BY s.career_id",(r,n)->r.getString(1));
        for(String id:ids)transactions.executeWithoutResult(ignored->initialize(jdbc,id));
    }
    static CareerMarketEngine engine(JdbcTemplate jdbc,String career,int year,Saved market) {
        return new CareerMarketEngine(career,managed(jdbc,career),directory(jdbc,career),CareerRosterStore.saved(jdbc,career,year).state(),market.state());
    }
    public static boolean exists(JdbcTemplate jdbc,String career) {return jdbc.queryForObject("SELECT COUNT(*) FROM career_market_state WHERE career_id=?",Integer.class,career)>0;}
    public record Eligibility(boolean enabled,Map<String,String> employers) {
        public Eligibility {employers=Map.copyOf(employers);}
        public boolean allows(String team,String player) {return !enabled || team.equals(employers.get(player));}
    }
    public static Eligibility eligibility(JdbcTemplate jdbc,String career) {
        var market=load(jdbc,career);if(market==null)return new Eligibility(false,Map.of());
        // During Calendar mutation the processed market date is the target; both commit atomically.
        LocalDate date=market.state().processedThrough();Map<String,String> employers=new TreeMap<>();
        for(var c:market.state().contracts().values())if(c.team()!=null&&c.status()==ContractStatus.ACTIVE&&!date.isBefore(c.terms().startDate())&&!date.isAfter(c.terms().endDate()))employers.put(c.playerId(),c.team());
        return new Eligibility(true,employers);
    }
    public static boolean eligible(JdbcTemplate jdbc,String career,String team,String player) {return eligibility(jdbc,career).allows(team,player);}
    static boolean repairNeeded(JdbcTemplate jdbc,String career,int year,String first,String second,String competition) {
        var authority=eligibility(jdbc,career);if(!authority.enabled()||first==null||second==null)return false;
        var roster=CareerRosterStore.saved(jdbc,career,year).state();var directory=directory(jdbc,career);
        var registrations=competition==null?List.<CareerInternationalState>of():jdbc.query("SELECT state_json FROM career_international_state WHERE career_id=? AND calendar_season_year=? AND competition_id=?",(r,n)->read(r.getString(1),CareerInternationalState.class),career,year,competition);
        for(String raw:List.of(first,second)) {
            String team=raw.contains(":")?raw:"LCK:"+raw;
            var ids=roster.lineups().getOrDefault(team,List.of());if(ids.size()!=5||ids.stream().anyMatch(id->!authority.allows(team,id)))return true;
            if(!registrations.isEmpty()) {
                var allowed=registeredIds(jdbc,career,year,competition,team,registrations.getFirst().rosters());
                for(var role:com.lolfm.domain.Position.values())if(allowed.stream().noneMatch(id->directory.players().get(id).position()==role&&team.equals(roster.members().get(id).ownerTeam())&&authority.allows(team,id))) {
                    // AI supplemental registration is executed by bindFixture; only user repair needs a stop here.
                    if(team.equals(managed(jdbc,career)))return true;
                }
            }
        }return false;
    }
    public static void requireEligible(JdbcTemplate jdbc,String career,String team,String player) {
        if(!eligible(jdbc,career,team,player))throw CareerException.invalid("lineup",team+"의 선수가 현재 유효한 계약을 보유하지 않습니다. 계약과 선발을 확인해 주세요.");
    }
    public static LocalDate nextEvent(JdbcTemplate jdbc,String career,LocalDate current) {
        var s=load(jdbc,career);return s==null?null:engine(jdbc,career,activeYear(jdbc,career),s).nextEvent(current);
    }
    /** Caller already holds Calendar row. No GET calls this method. */
    public static void processThrough(JdbcTemplate jdbc,String career,LocalDate target) {
        var old=load(jdbc,career);if(old==null||!target.isAfter(old.state().processedThrough()))return;
        int year=activeYear(jdbc,career);var engine=engine(jdbc,career,year,old);engine.advance(target);
        persist(jdbc,career,year,old,engine);touch(jdbc,career);
    }
    static void persist(JdbcTemplate jdbc,String career,int year,Saved old,CareerMarketEngine engine) {
        engine.validateIntegrity();String json=write(engine.state());
        if(jdbc.update("UPDATE career_market_state SET revision=?,state_json=?,state_hash=? WHERE career_id=? AND revision=?",
                old.revision()+1,json,hash(json),career,old.revision())!=1)throw CareerException.calendarStaleRevision();
        var previous=CareerRosterStore.saved(jdbc,career,year);String next=write(engine.roster());
        if(!next.equals(write(previous.state())))jdbc.update("UPDATE career_roster_state SET revision=revision+1,state_json=?,state_hash=? WHERE career_id=? AND season_year=?",next,hash(next),career,year);
    }
    private static void touch(JdbcTemplate jdbc,String career) {jdbc.update("UPDATE career_save SET updated_at=CURRENT_TIMESTAMP WHERE career_id=?",career);}
    public View view(String career,int year) {
        return transactions.execute(ignored->{lockCareer(jdbc,career);return readView(career,year);});
    }
    private View readView(String career,int year) {
        int active=activeYear(jdbc,career);Saved saved=load(jdbc,career);if(saved==null)throw CareerException.invalid("market","서버 시작 시 계약 이주가 필요합니다.");
        boolean historical=year!=active;CareerMarketState state=saved.state();LocalDate date=date(jdbc,career);
        var roster=CareerRosterStore.saved(jdbc,career,year);if(roster==null)throw CareerException.notFound();
        if(historical) {
            var snapshot=jdbc.query("SELECT market_json,closed_date,roster_json FROM career_market_season_close WHERE career_id=? AND season_year=?",(r,n)->List.of(r.getString(1),r.getObject(2,LocalDate.class).toString(),r.getString(3)),career,year);
            if(!snapshot.isEmpty()){state=read(snapshot.getFirst().get(0),CareerMarketState.class);date=LocalDate.parse(snapshot.getFirst().get(1));roster=new CareerRosterStore.Saved(roster.revision(),read(snapshot.getFirst().get(2),CareerRosterStore.State.class));}
            else return new View("CAREER_MARKET_VIEW_V1",CareerMarketPolicy.VERSION,CareerMarketPolicy.CURRENCY,career,year,date,saved.revision(),true,managed(jdbc,career),false,null,List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),Map.of(),List.of(),List.of(),"이주 전 시즌에는 게임 계약 이력이 없습니다. 공개 조사 계약은 선수 상세에서 확인하세요.");
        }
        String managed=managed(jdbc,career);var engine=new CareerMarketEngine(career,managed,directory(jdbc,career),roster.state(),state);
        var directory=directory(jdbc,career);
        var players=new ArrayList<PlayerMarket>();
        for(String id:state.preferences().keySet().stream().sorted().toList()) {
            var c=engine.active(id,date);var future=engine.scheduled(id);LocalDate start=engine.availableStart(id,date);
            String status=c!=null?"CONTRACTED":state.freeAgents().contains(id)?"FREE_AGENT":"UNAVAILABLE";
            String reason=future!=null?"이미 미래 계약이 확정되어 있습니다.":start==null?(c==null?"소속 또는 영입 자격 미확인":"계약 만료 60일 전부터 협상할 수 있습니다."):null;
            players.add(new PlayerMarket(id,status,c==null?null:c.contractId(),future==null?null:future.contractId(),start,CareerMarketPolicy.demand(directory.players().get(id)),c==null?0:CareerMarketPolicy.releaseCost(c,date),reason,state.preferences().get(id)));
        }
        var finances=new ArrayList<Finance>();for(var a:state.accounts().values().stream().sorted(Comparator.comparing(Account::team)).toList())finances.add(new Finance(a.team(),a.annualBudget(),a.cash(),engine.reservedCash(a.team()),engine.salaryAt(a.team(),date,false),engine.peakSalary(a.team()),a.rosterLimit()));
        Map<String,List<String>> gaps=new TreeMap<>();
        for(var entry:roster.state().lineups().entrySet()) {
            Set<com.lolfm.domain.Position> roles=new HashSet<>();entry.getValue().forEach(id->roles.add(directory.players().get(id).position()));
            var missing=Arrays.stream(com.lolfm.domain.Position.values()).filter(p->!roles.contains(p)).map(Enum::name).toList();if(!missing.isEmpty())gaps.put(entry.getKey(),missing);
        }
        boolean offseason=jdbc.queryForObject("SELECT COUNT(*) FROM career_market_season_close WHERE career_id=? AND season_year=?",Integer.class,career,year)>0;
        return new View("CAREER_MARKET_VIEW_V1",CareerMarketPolicy.VERSION,CareerMarketPolicy.CURRENCY,career,year,date,saved.revision(),historical,managed,offseason,
                historical?null:engine.nextEvent(date),players,state.contracts().values().stream().sorted(Comparator.comparing(Contract::signedDate).thenComparing(Contract::contractId)).toList(),
                state.offers().values().stream().sorted(Comparator.comparing(Offer::submittedDate).reversed().thenComparing(Offer::offerId)).toList(),finances,
                state.decisions().values().stream().sorted(Comparator.comparing(Decision::date).reversed().thenComparing(Decision::eventId)).limit(100).toList(),
                state.events().stream().sorted(Comparator.comparing(Event::date).reversed().thenComparing(Event::eventId)).limit(150).toList(),
                state.ledger().stream().filter(l->managed.equals(l.team())).sorted(Comparator.comparing(Ledger::date).reversed()).limit(100).toList(),gaps,
                jdbc.query("SELECT competition_id,team,player_id,position,added_date,revision,reason FROM career_registration_supplement WHERE career_id=? AND season_year=? ORDER BY competition_id,revision",(r,n)->new Supplement(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getObject(5,LocalDate.class),r.getLong(6),r.getString(7)),career,year),
                historical?List.of():List.of("SUBMIT","REVISE","WITHDRAW","RELEASE","SUPPLEMENT","OPEN_STOVE"),
                "영입 후 선발은 별도 선택합니다. 등록 밖 선수는 다음 등록부터 적용하며, 유효한 등록 대체자가 전혀 없는 포지션만 명시적 보충등록할 수 있습니다. 시작된 Series는 유지됩니다.");
    }
    public Change command(String career,Request request) {
        if(request==null||!"CAREER_MARKET_COMMAND_V1".equals(request.schemaVersion())||request.sourceYear()<2026||request.expectedRevision()<0)throw CareerException.invalid(null,"시장 요청 형식을 확인해 주세요.");
        String command;try{command=CareerIdentity.canonicalCommandId(request.clientCommandId());}catch(RuntimeException invalid){throw CareerException.invalid("clientCommandId","원본 UUID가 필요합니다.");}
        String payload=hash(career+'|'+write(request));
        return transactions.execute(ignored->{
            lockCareer(jdbc,career);
            var prior=jdbc.query("SELECT payload_hash,receipt_json,receipt_hash FROM career_market_command WHERE career_id=? AND client_command_id=?",(r,n)->{
                if(!payload.equals(r.getString(1)))throw CareerException.calendarCommandConflict();
                if(!hash(r.getString(2)).equals(r.getString(3)))throw new IllegalStateException("MARKET_RECEIPT_INTEGRITY");return read(r.getString(2),Receipt.class);
            },career,command);
            if(!prior.isEmpty())return new Change(true,prior.getFirst(),readView(career,activeYear(jdbc,career)));
            var old=load(jdbc,career);if(old==null||activeYear(jdbc,career)!=request.sourceYear()||old.revision()!=request.expectedRevision())throw CareerException.calendarStaleRevision();
            var engine=engine(jdbc,career,request.sourceYear(),old);String team=managed(jdbc,career),reference=null;LocalDate date=date(jdbc,career);
            switch(request.action()) {
                case "SUBMIT","REVISE" -> {
                    if(request.playerId()==null||request.terms()==null||request.replacementPlayerId()!=null||request.competitionId()!=null||("REVISE".equals(request.action())!=(request.offerId()!=null)))throw CareerException.invalid(null,"제안 입력을 확인해 주세요.");
                    reference=engine.submit(team,request.playerId(),request.terms(),request.offerId(),date).offerId();
                }
                case "WITHDRAW" -> {if(request.playerId()!=null)throw CareerException.invalid("playerId","철회는 원본 제안으로 지정합니다.");requireEmpty(request,true,false,false);engine.withdraw(team,request.offerId(),date);reference=request.offerId();}
                case "RELEASE" -> {requireEmpty(request,false,true,false);engine.release(team,request.playerId(),request.replacementPlayerId(),date);reference=request.playerId();}
                case "SUPPLEMENT" -> {requireEmpty(request,false,false,true);supplement(jdbc,career,request.sourceYear(),request.competitionId(),team,request.playerId(),date);reference=request.playerId();}
                case "OPEN_STOVE" -> {requireEmpty(request,false,false,false);if(request.playerId()!=null)throw CareerException.invalid(null,"스토브 진입 요청을 확인해 주세요.");seasons.openStove(career);reference=Integer.toString(request.sourceYear());}
                default -> throw CareerException.invalid("action","지원하지 않는 시장 명령입니다.");
            }
            persist(jdbc,career,request.sourceYear(),old,engine);touch(jdbc,career);
            var receipt=new Receipt(command,career,request.sourceYear(),old.revision()+1,request.action(),reference,date,"변경 저장 완료 · 경기 시작/등록 자격은 별도 확인");
            String json=write(receipt);jdbc.update("INSERT INTO career_market_command VALUES (?,?,?,?,?)",career,command,payload,json,hash(json));
            return new Change(false,receipt,readView(career,request.sourceYear()));
        });
    }
    private static void requireEmpty(Request r,boolean offer,boolean replacement,boolean competition) {
        if(r.terms()!=null || (offer!=(r.offerId()!=null)) || (!replacement&&r.replacementPlayerId()!=null) || (competition!=(r.competitionId()!=null)))throw CareerException.invalid(null,"명령에 맞지 않는 조건입니다.");
    }
    public static void supplement(JdbcTemplate jdbc,String career,int year,String competition,String team,String player,LocalDate date) {
        var international=jdbc.query("SELECT state_json FROM career_international_state WHERE career_id=? AND calendar_season_year=? AND competition_id=?",(r,n)->read(r.getString(1),CareerInternationalState.class),career,year,competition);
        if(international.isEmpty()||!international.getFirst().rosters().teams().containsKey(team)||international.getFirst().plan().complete())throw CareerException.invalid("competitionId","진행 중인 참가 대회만 보충등록할 수 있습니다.");
        var directory=directory(jdbc,career);var definition=directory.players().get(player);if(definition==null)throw CareerException.invalid("playerId","선수 정보가 없습니다.");
        var membership=CareerRosterStore.saved(jdbc,career,year).state().members().get(player);
        requireEligible(jdbc,career,team,player);
        if(!team.equals(membership.ownerTeam())||!"FIRST_TEAM".equals(membership.squad()))throw CareerException.invalid("playerId","현재 적법하게 보유한 1군 선수만 보충등록할 수 있습니다.");
        var allowed=registeredIds(jdbc,career,year,competition,team,international.getFirst().rosters());
        var authority=eligibility(jdbc,career);
        if(allowed.stream().anyMatch(id->directory.players().get(id).position()==definition.position()&&authority.allows(team,id)))throw CareerException.invalid("playerId","같은 포지션의 유효한 등록 선수가 남아 있어 보충등록할 수 없습니다.");
        long revision=jdbc.queryForObject("SELECT COALESCE(MAX(revision),0)+1 FROM career_registration_supplement WHERE career_id=? AND season_year=? AND competition_id=?",Long.class,career,year,competition);
        jdbc.update("INSERT INTO career_registration_supplement VALUES (?,?,?,?,?,?,?,?,?)",career,year,competition,team,player,definition.position().name(),date,revision,"NO_CURRENTLY_ELIGIBLE_REGISTERED_PLAYER_FOR_POSITION_V1");
    }
    static List<String> registeredIds(JdbcTemplate jdbc,String career,int year,String competition,String team,CompetitionRosterSnapshot registered) {
        var pools=jdbc.query("SELECT pool_json,pool_hash FROM career_registered_player_pool WHERE career_id=? AND season_year=? AND competition_id=?",(r,n)->pool(r.getString(1),r.getString(2)),career,year,competition);
        var ids=new TreeSet<String>(pools.isEmpty()?registered.roster(team).players().stream().map(CompetitionRosterSnapshot.Starter::playerId).toList():pools.getFirst().getOrDefault(team,List.of()));
        ids.addAll(jdbc.query("SELECT player_id FROM career_registration_supplement WHERE career_id=? AND season_year=? AND competition_id=? AND team=? ORDER BY revision",(r,n)->r.getString(1),career,year,competition,team));
        return List.copyOf(ids);
    }
}
