package com.lolfm.career;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lolfm.domain.Position;
import com.lolfm.player.ExpandedPlayerCatalog;
import com.lolfm.player.GlobalTeamRosterCatalog.TeamKey;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Career-owned directory snapshot and season-owned membership/lineup. No mutable match objects are stored. */
@Service
public final class CareerRosterStore {
    public static final String POLICY = "CAREER_ROSTER_LINEUP_V1";
    public static final String OPERATING_POLICY = "CAREER_OPERATING_ROSTER_V2";
    public static final String REGISTRATION_POLICY = "CAREER_REGISTERED_FIRST_TEAM_POOL_V1";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ExpandedPlayerCatalog catalog;
    private final com.lolfm.player.PlayerDataStore playerData;

    public CareerRosterStore(JdbcTemplate jdbc, PlatformTransactionManager manager, ExpandedPlayerCatalog catalog) {
        this(jdbc,manager,catalog,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public CareerRosterStore(JdbcTemplate jdbc, PlatformTransactionManager manager, ExpandedPlayerCatalog catalog, com.lolfm.player.PlayerDataStore playerData) {
        this.jdbc = jdbc; this.transactions = new TransactionTemplate(manager); this.catalog = catalog; this.playerData=playerData;
    }
    public record Directory(Map<String, ExpandedPlayerCatalog.Definition> players,
                            Map<String, ExpandedPlayerCatalog.Organization> organizations) {
        public Directory { players = Map.copyOf(players); organizations = Map.copyOf(organizations); }
    }
    public record Membership(String playerId, String ownerTeam, String organizationId, String squad, String eligibilityReason) {}
    public record State(String policyVersion, Map<String, Membership> members, Map<String, List<String>> lineups) {
        public State {
            if (!POLICY.equals(policyVersion) && !OPERATING_POLICY.equals(policyVersion)) throw new IllegalArgumentException("ROSTER_POLICY");
            members = Map.copyOf(members);
            var copy = new TreeMap<String, List<String>>(); lineups.forEach((team, ids) -> copy.put(team, List.copyOf(ids)));
            lineups = Collections.unmodifiableMap(copy);
        }
    }
    public record Saved(long revision, State state) {}
    public record Request(String schemaVersion, int sourceYear, String team, String playerId, String action,
                          String targetOrganizationId, String replacementPlayerId, long expectedRevision, String clientCommandId) {}
    public record Receipt(String clientCommandId, String careerId, int sourceYear, long resultingRevision,
                          String stateHash, String applicationPolicy) {}
    public record Change(boolean replayed, Receipt receipt, View roster) {}
    public record View(String schemaVersion, String careerId, int seasonYear, long revision, boolean readOnly,
                       String managedTeam, Directory directory, State state, Map<String, List<String>> registeredPlayers,
                       Map<String, List<String>> activeSeriesPlayers, List<String> allowedCommands, String applicationPolicy) {}

    /** Called only by create/startup migration, never by GET. Idempotent and preserves user selections. */
    public void initialize(String careerId, int year) {
        initialize(careerId,year,false);
    }
    public void initializeNew(String careerId, int year) {
        initialize(careerId,year,true);
    }
    private void initialize(String careerId, int year, boolean newCareer) {
        transactions.executeWithoutResult(ignored -> {
            lockCareer(jdbc, careerId);
            if (!newCareer&&!CareerSaveCompatibility.recoverySupported(jdbc,careerId)) return;
            if (!CareerSaveCompatibility.directoryVersionSupported(jdbc,careerId)) return;
            if (saved(jdbc, careerId, year) != null) return;
            boolean hasDirectory=jdbc.queryForObject("SELECT COUNT(*) FROM career_player_directory WHERE career_id=?",Integer.class,careerId)>0;
            if(!newCareer&&!hasDirectory) {
                try{CareerSaveCompatibility.requireOriginalReference(jdbc,careerId);}
                catch(CareerException unsupported){if(CareerSaveCompatibility.unsupported(unsupported))return;throw unsupported;}
            }
            if(!newCareer&&hasDirectory&&CareerMarketStore.exists(jdbc,careerId))return; // Lost operating membership cannot be recreated from initial ownership.
            var definitions = new LinkedHashMap<>(hasDirectory?sourceDirectory(jdbc,careerId).players():catalog.players());
            var lineups = new LinkedHashMap<String,List<String>>();
            if(hasDirectory)definitions.values().stream().filter(p->p.initialOwnerTeam()!=null&&p.initialSquad().equals("FIRST_TEAM")&&p.eligibilityReason()==null).forEach(p->lineups.computeIfAbsent(p.initialOwnerTeam(),k->new ArrayList<>()).add(p.playerId()));
            else lineups.putAll(catalog.initialLineups());
            var frozen = jdbc.query("SELECT roster_json FROM career_season WHERE career_id=? AND season_year=?",
                    (r,n) -> r.getString(1), careerId, year);
            if (!frozen.isEmpty() && frozen.getFirst() != null) {
                var old = CompetitionRosterSnapshot.decode(frozen.getFirst());
                old.teams().forEach((team, roster) -> {
                    lineups.put(team, roster.players().stream().map(CompetitionRosterSnapshot.Starter::playerId).toList());
                    for (var player : roster.players()) {
                        var reference = definitions.get(player.playerId());
                        if (reference == null) throw new IllegalStateException("SAVED_PLAYER_REFERENCE_MISSING");
                        definitions.put(player.playerId(), new ExpandedPlayerCatalog.Definition(player.playerId(), player.nickname(), player.position(), player,
                                reference.provisional(), reference.initialOrganizationId(), team, "FIRST_TEAM", null, reference.detailsJson()));
                    }
                });
            }
            if (newCareer && playerData!=null || !newCareer && (frozen.isEmpty() || frozen.getFirst()==null)) {
                // Only reachable before operating membership exists. Keep the recovered initial input
                // durable before later startup stages initialize the market and development state.
                if(newCareer){definitions.clear(); definitions.putAll(playerData.snapshot());}
                var teams=new TreeMap<String,CompetitionRosterSnapshot.Roster>();
                lineups.forEach((team,ids)-> {
                    var players=ids.stream().map(id->definitions.get(id).gameplay()).toList();
                    String[] parts=team.split(":");
                    teams.put(team,new CompetitionRosterSnapshot.Roster(new TeamKey(parts[0],parts[1]),hash(write(players)),players));
                });
                var snapshot=new CompetitionRosterSnapshot(teams);
                jdbc.update("UPDATE career_season SET roster_json=?,roster_hash=? WHERE career_id=? AND season_year=?",
                        snapshot.encoded(),snapshot.identity(),careerId,year);
            }
            if (jdbc.queryForObject("SELECT COUNT(*) FROM career_player_directory WHERE career_id=?",Integer.class,careerId)==0) {
                String payload=write(new Directory(definitions,catalog.organizations()));
                jdbc.update("INSERT INTO career_player_directory (career_id,directory_version,directory_json,directory_hash) VALUES (?,?,?,?)",careerId,ExpandedPlayerCatalog.VERSION,payload,hash(payload));
            }
            var directory=directory(jdbc,careerId);
            var members=new LinkedHashMap<String,Membership>();
            directory.players().forEach((id,p) -> members.put(id,new Membership(id,p.initialOwnerTeam(),p.initialOrganizationId(),p.initialSquad(),p.eligibilityReason())));
            State state=new State(POLICY,members,lineups);validate(state,directory);
            String json=write(state);jdbc.update("INSERT INTO career_roster_state VALUES (?,?,0,?,?)",careerId,year,json,hash(json));
        });
    }
    public void recover() {
        jdbc.query("SELECT s.career_id,s.season_year FROM career_season s LEFT JOIN career_roster_state r ON r.career_id=s.career_id AND r.season_year=s.season_year WHERE r.career_id IS NULL ORDER BY s.career_id,s.season_year",
                (r,n) -> Map.entry(r.getString(1),r.getInt(2))).forEach(row -> initialize(row.getKey(),row.getValue()));
    }
    public View view(String careerId, int year) {
        return transactions.execute(ignored -> { lockCareer(jdbc,careerId); return readView(careerId,year); });
    }
    private View readView(String careerId,int year) {
        int active=activeYear(jdbc,careerId); var saved=saved(jdbc,careerId,year);
        if(saved==null)throw CareerException.notFound();var directory=year==active?directory(jdbc,careerId):CareerDevelopmentStore.historicalDirectory(jdbc,careerId,year);validate(saved.state(),directory);
        String managed="LCK:"+jdbc.queryForObject("SELECT managed_team_code FROM career_save WHERE career_id=?",String.class,careerId);
        var registrations=new TreeMap<String,List<String>>();
        jdbc.query("SELECT competition_id,pool_json,pool_hash FROM career_registered_player_pool WHERE career_id=? AND season_year=? ORDER BY competition_id",
                (org.springframework.jdbc.core.RowCallbackHandler) r -> {
                    var pool=pool(r.getString(2),r.getString(3));
                    if(pool.containsKey(managed))registrations.put(r.getString(1),pool.get(managed));
                },careerId,year);
        // Old registrations keep their exact five starters and have no new candidate pool.
        jdbc.query("SELECT competition_id,state_json,state_hash FROM career_international_state WHERE career_id=? AND calendar_season_year=?",
                (org.springframework.jdbc.core.RowCallbackHandler) r -> {
                    if(!registrations.containsKey(r.getString(1))) {
                        if(!hash(r.getString(2)).equals(r.getString(3)))throw new IllegalStateException("INTERNATIONAL_STATE_INTEGRITY");
                        var state=read(r.getString(2),CareerInternationalState.class);
                        if(state.rosters().teams().containsKey(managed))registrations.put(r.getString(1),state.rosters().roster(managed).players().stream().map(CompetitionRosterSnapshot.Starter::playerId).toList());
                    }
                },careerId,year);
        jdbc.query("SELECT competition_id,player_id FROM career_registration_supplement WHERE career_id=? AND season_year=? AND team=? ORDER BY competition_id,revision",
                (org.springframework.jdbc.core.RowCallbackHandler) r->{var ids=new TreeSet<>(registrations.getOrDefault(r.getString(1),List.of()));ids.add(r.getString(2));registrations.put(r.getString(1),List.copyOf(ids));},careerId,year,managed);
        var activeSeries=new TreeMap<String,List<String>>();
        jdbc.query("SELECT series_id,binding_canonical FROM career_competition_series_binding WHERE career_id=? AND calendar_season_year=? AND lifecycle_status<>'COMPLETED'",
                (org.springframework.jdbc.core.RowCallbackHandler) r -> {
                    var binding=CareerCompetitionSeriesBindingV1.restoreCanonical(r.getString(2));
                    var frozen=binding.frozenRosters();
                    if(frozen!=null) frozen.teams().values().stream().filter(t->CompetitionRosterSnapshot.token(t.team()).equals(managed))
                            .findFirst().ifPresent(t->activeSeries.put(binding.boundSeriesId(),t.players().stream().map(CompetitionRosterSnapshot.Starter::playerId).toList()));
                    else if(binding.firstTeamCode().equals(managed.substring(4)) || binding.secondTeamCode().equals(managed.substring(4)))
                        activeSeries.put(binding.boundSeriesId(),initialPlayers(directory,managed));
                },careerId,year);
        jdbc.query("""
            SELECT f.bound_series_id,r.roster_json FROM league_fixture f JOIN career_season s ON s.season_id=f.season_id
            LEFT JOIN career_league_fixture_roster r ON r.season_id=f.season_id AND r.fixture_id=f.fixture_id
            WHERE s.career_id=? AND s.season_year=? AND f.bound_series_id IS NOT NULL AND f.lifecycle_status<>'COMPLETED'
            AND (EXISTS (SELECT 1 FROM league_player_binding b WHERE b.season_id=f.season_id AND b.fixture_id=f.fixture_id)
              OR EXISTS (SELECT 1 FROM league_job j WHERE j.season_id=f.season_id AND j.fixture_id=f.fixture_id))
            AND (f.first_team_code=? OR f.second_team_code=?)
            """, (org.springframework.jdbc.core.RowCallbackHandler) r -> {
                var frozen=r.getString(2)==null?null:CompetitionRosterSnapshot.decode(r.getString(2));
                activeSeries.put(r.getString(1),frozen==null?initialPlayers(directory,managed):frozen.roster(managed.substring(4)).players().stream().map(CompetitionRosterSnapshot.Starter::playerId).toList());
            },careerId,year,managed.substring(4),managed.substring(4));
        return new View(OPERATING_POLICY.equals(saved.state().policyVersion())?"CAREER_ROSTER_VIEW_V2":"CAREER_ROSTER_VIEW_V1",careerId,year,saved.revision(),active!=year,managed,directory,saved.state(),registrations,activeSeries,
                active==year?List.of("SELECT_STARTER","MOVE_SQUAD"):List.of(),"UNSTARTED_SERIES_WITHIN_REGISTERED_POOL_ELSE_NEXT_REGISTRATION");
    }
    private static List<String> initialPlayers(Directory directory,String team) {
        // Original five remain identifiable from the old, immutable 280-player reference population.
        return directory.players().values().stream().filter(p->!p.provisional() && team.equals(p.initialOwnerTeam()))
                .sorted(Comparator.comparing(p->p.position().ordinal())).map(ExpandedPlayerCatalog.Definition::playerId).toList();
    }
    public Change change(String careerId,Request request) {
        if(request==null || !"CAREER_ROSTER_COMMAND_V1".equals(request.schemaVersion()) || request.sourceYear()<2026 || request.expectedRevision()<0)
            throw CareerException.invalid(null,"명단 변경 요청을 확인해 주세요.");
        String command;
        try {command=CareerIdentity.canonicalCommandId(request.clientCommandId());}catch(RuntimeException invalid){throw CareerException.invalid("clientCommandId","UUID가 필요합니다.");}
        String payload=hash(careerId+'\n'+write(request));
        return transactions.execute(ignored -> {
            lockCareer(jdbc,careerId);CareerContinuousGuard.requireCommand(jdbc,careerId);
            var old=jdbc.query("SELECT payload_hash,receipt_json,receipt_hash FROM career_roster_command WHERE client_command_id=?",(r,n)-> {
                if(!payload.equals(r.getString(1)))throw CareerException.calendarCommandConflict();
                if(!hash(r.getString(2)).equals(r.getString(3)))throw new IllegalStateException("ROSTER_RECEIPT_INTEGRITY");
                return read(r.getString(2),Receipt.class);
            },command);
            if(!old.isEmpty())return new Change(true,old.getFirst(),view(careerId,activeYear(jdbc,careerId)));
            if(activeYear(jdbc,careerId)!=request.sourceYear())throw CareerException.calendarStaleRevision();
            var saved=saved(jdbc,careerId,request.sourceYear());
            if(saved==null)throw new IllegalStateException("CAREER_ROSTER_MIGRATION_REQUIRED");
            if(saved.revision()!=request.expectedRevision())throw CareerException.calendarStaleRevision();
            String managed="LCK:"+jdbc.queryForObject("SELECT managed_team_code FROM career_save WHERE career_id=?",String.class,careerId);
            if(!managed.equals(request.team()))throw CareerException.invalid("team","관리 구단의 명단만 변경할 수 있습니다.");
            var directory=directory(jdbc,careerId);
            CareerMarketStore.requireEligible(jdbc,careerId,managed,request.playerId());
            var member=saved.state().members().get(request.playerId());
            if(member==null || !managed.equals(member.ownerTeam()) || member.eligibilityReason()!=null)
                throw CareerException.invalid("playerId","보유 선수의 소속과 출전 가능 상태를 확인해 주세요.");
            var members=new LinkedHashMap<>(saved.state().members());var lineups=new LinkedHashMap<>(saved.state().lineups());
            var lineup=new ArrayList<>(lineups.get(managed));
            if("SELECT_STARTER".equals(request.action())) {
                if(!"FIRST_TEAM".equals(member.squad()) || request.targetOrganizationId()!=null || request.replacementPlayerId()!=null)
                    throw CareerException.invalid("playerId","1군 명부의 같은 포지션 선수만 선발할 수 있습니다.");
                Position role=directory.players().get(member.playerId()).position();
                int slot=slot(lineup,directory,role);if(slot<0)lineup.add(member.playerId());else lineup.set(slot,member.playerId());
                lineup.sort(Comparator.comparing(id->directory.players().get(id).position()));
            } else if("MOVE_SQUAD".equals(request.action())) {
                var target=directory.organizations().get(request.targetOrganizationId());
                if(target==null || !managed.equals(target.competitiveTeam()))
                    throw CareerException.invalid("targetOrganizationId","명시적으로 연결된 구단·육성팀 사이에서만 이동할 수 있습니다.");
                String squad=target.kind().equals("DEVELOPMENT")?"DEVELOPMENT":"FIRST_TEAM";
                if(lineup.contains(member.playerId()) && !"FIRST_TEAM".equals(squad)) {
                    var replacement=members.get(request.replacementPlayerId());
                    if(replacement==null || replacement.playerId().equals(member.playerId()) || !managed.equals(replacement.ownerTeam())
                            || !"FIRST_TEAM".equals(replacement.squad()) || replacement.eligibilityReason()!=null
                            || directory.players().get(replacement.playerId()).position()!=directory.players().get(member.playerId()).position())
                        throw CareerException.invalid("replacementPlayerId","선발 선수를 내리려면 같은 포지션의 1군 대체 선수를 지정해야 합니다.");
                    lineup.set(lineup.indexOf(member.playerId()),replacement.playerId());
                } else if(request.replacementPlayerId()!=null)throw CareerException.invalid("replacementPlayerId","이 이동에는 대체 선수 지정이 필요하지 않습니다.");
                members.put(member.playerId(),new Membership(member.playerId(),managed,target.organizationId(),squad,null));
            } else throw CareerException.invalid("action","지원하지 않는 명단 변경입니다.");
            lineups.put(managed,lineup);State next=new State(saved.state().policyVersion(),members,lineups);validate(next,directory);
            CareerLifecycleStore.placementChanged(jdbc,careerId,request.playerId(),members.get(request.playerId()),CareerMarketStore.executionDate(jdbc,careerId));
            String json=write(next);long revision=saved.revision()+1;
            if(jdbc.update("UPDATE career_roster_state SET revision=?,state_json=?,state_hash=? WHERE career_id=? AND season_year=? AND revision=?",
                    revision,json,hash(json),careerId,request.sourceYear(),saved.revision())!=1)throw CareerException.calendarStaleRevision();
            var receipt=new Receipt(command,careerId,request.sourceYear(),revision,hash(json),"EXISTING_SERIES_UNCHANGED; NEXT_ELIGIBLE_SERIES_OR_NEXT_REGISTRATION");
            jdbc.update("UPDATE career_save SET updated_at=CURRENT_TIMESTAMP WHERE career_id=?",careerId);
            String encoded=write(receipt);jdbc.update("INSERT INTO career_roster_command VALUES (?,?,?,?,?,?)",command,careerId,request.sourceYear(),payload,encoded,hash(encoded));
            return new Change(false,receipt,view(careerId,request.sourceYear()));
        });
    }
    private static int slot(List<String> lineup,Directory directory,Position role) {
        for(int i=0;i<lineup.size();i++)if(directory.players().get(lineup.get(i)).position()==role)return i;
        return -1;
    }
    static void validate(State state,Directory directory) {
        if(!state.members().keySet().equals(directory.players().keySet()))throw new IllegalStateException("DIRECTORY_MEMBERSHIP_MISMATCH");
        for(var entry:state.members().entrySet()) {
            var member=entry.getValue();if(!entry.getKey().equals(member.playerId()))throw new IllegalStateException("MEMBER_ID_MISMATCH");
            if(member.ownerTeam()!=null && (!state.lineups().containsKey(member.ownerTeam())
                    || !directory.organizations().containsKey(member.organizationId())
                    || !member.ownerTeam().equals(directory.organizations().get(member.organizationId()).competitiveTeam())))
                throw new IllegalStateException("MEMBER_ORGANIZATION_MISMATCH");
        }
        var selected=new HashSet<String>();
        state.lineups().forEach((team,ids)->{
            if((POLICY.equals(state.policyVersion())?ids.size()!=5:ids.size()>5) || new HashSet<>(ids).size()!=ids.size())throw new IllegalStateException("FIVE_DISTINCT_STARTERS_REQUIRED");
            var positions=EnumSet.noneOf(Position.class);
            for(String id:ids) {
                var member=state.members().get(id);var player=directory.players().get(id);
                if(member==null || !team.equals(member.ownerTeam()) || !"FIRST_TEAM".equals(member.squad()) || member.eligibilityReason()!=null
                        || !selected.add(id) || !positions.add(player.position()))throw new IllegalStateException("INELIGIBLE_OR_DUPLICATE_STARTER");
            }
        });
    }
    public static Saved saved(JdbcTemplate jdbc,String career,int year) {
        var rows=jdbc.query("SELECT revision,state_json,state_hash FROM career_roster_state WHERE career_id=? AND season_year=?",(r,n)->{
            if(!hash(r.getString(2)).equals(r.getString(3)))throw new IllegalStateException("ROSTER_STATE_INTEGRITY");
            return new Saved(r.getLong(1),read(r.getString(2),State.class));
        },career,year);return rows.isEmpty()?null:rows.getFirst();
    }
    public static Directory directory(JdbcTemplate jdbc,String career) {
        return CareerDevelopmentStore.current(jdbc,career,baseDirectory(jdbc,career));
    }
    public static Directory baseDirectory(JdbcTemplate jdbc,String career) {return CareerLifecycleStore.compose(jdbc,career,sourceDirectory(jdbc,career));}
    public static Directory sourceDirectory(JdbcTemplate jdbc,String career) {
        return jdbc.queryForObject("SELECT directory_json,directory_hash,directory_version FROM career_player_directory WHERE career_id=?",(r,n)->{
            CareerSaveCompatibility.requireDirectoryVersion(r.getString(3));
            if(!hash(r.getString(1)).equals(r.getString(2)))throw new IllegalStateException("DIRECTORY_INTEGRITY");
            return read(r.getString(1),Directory.class);
        },career);
    }
    public static CompetitionRosterSnapshot currentRosters(JdbcTemplate jdbc,String career,int year) {
        var saved=saved(jdbc,career,year);if(saved==null)return null;
        var directory=directory(jdbc,career);validate(saved.state(),directory);
        var teams=new LinkedHashMap<String,CompetitionRosterSnapshot.Roster>();
        saved.state().lineups().forEach((team,ids)->{
            if(ids.size()!=5)return;
            var players=ids.stream().map(id->directory.players().get(id).gameplay()).toList();
            String[] key=team.split(":");teams.put(team,new CompetitionRosterSnapshot.Roster(new TeamKey(key[0],key[1]),hash(write(players)),players));
        });return new CompetitionRosterSnapshot(teams);
    }
    public static CompetitionRosterSnapshot eligiblePair(JdbcTemplate jdbc,String career,int year,String first,String second) {
        var saved=saved(jdbc,career,year);var directory=directory(jdbc,career);
        var authority=CareerMarketStore.eligibility(jdbc,career);
        var rosters=new TreeMap<String,CompetitionRosterSnapshot.Roster>();
        for(String team:List.of(first,second)) {
            var ids=saved.state().lineups().getOrDefault(team,List.of());
            if(ids.size()!=5)throw CareerException.invalid("lineup",team+"의 선발 포지션이 비었습니다. 계약·승격·선발 설정으로 5명을 구성해 주세요.");
            for(String id:ids)if(!authority.allows(team,id))throw CareerException.invalid("lineup",team+"의 현재 계약 자격을 확인해 주세요.");
            var players=ids.stream().map(id->directory.players().get(id).gameplay()).toList();
            String[] parts=team.split(":");rosters.put(team,new CompetitionRosterSnapshot.Roster(new TeamKey(parts[0],parts[1]),hash(write(players)),players));
        }
        return new CompetitionRosterSnapshot(rosters);
    }
    public static void carry(JdbcTemplate jdbc,String career,int source,int destination) {
        var old=saved(jdbc,career,source);if(old==null)return;
        String json=write(old.state());jdbc.update("INSERT INTO career_roster_state VALUES (?,?,?,?,?)",career,destination,old.revision(),json,hash(json));
    }
    public static void lockCareer(JdbcTemplate jdbc,String career) {
        var rows=jdbc.query("SELECT career_id FROM career_calendar_state WHERE career_id=? FOR UPDATE",(r,n)->r.getString(1),career);
        if(rows.isEmpty())throw CareerException.notFound();
    }
    public static int activeYear(JdbcTemplate jdbc,String career) {
        var rows=jdbc.query("SELECT active_calendar_season_year FROM career_calendar_state WHERE career_id=?",(r,n)->r.getInt(1),career);
        if(rows.isEmpty())throw CareerException.notFound();return rows.getFirst();
    }
    static Map<String,List<String>> pool(String json,String expectedHash) {
        if(!hash(json).equals(expectedHash))throw new IllegalStateException("REGISTERED_POOL_INTEGRITY");
        try{return JSON.readValue(json,new com.fasterxml.jackson.core.type.TypeReference<Map<String,List<String>>>(){});}catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    static void registerPool(JdbcTemplate jdbc,String career,int year,String competition,Collection<String> teams) {
        var state=saved(jdbc,career,year);if(state==null)return;
        var registered=new TreeMap<String,List<String>>();
        for(String team:teams)registered.put(team,state.state().members().values().stream()
                .filter(m->CareerOverseasRoster.owner(team).equals(m.ownerTeam()) && (team.equals("LEC:KCB")?"DEVELOPMENT":"FIRST_TEAM").equals(m.squad()) && m.eligibilityReason()==null)
                .map(Membership::playerId).sorted().toList());
        jdbc.update("INSERT INTO career_opportunity_registration VALUES (?,?,?,?)",career,year,competition,CareerMarketStore.executionDate(jdbc,career));
        String json=write(registered);jdbc.update("INSERT INTO career_registered_player_pool VALUES (?,?,?,?,?,?)",
                career,year,competition,REGISTRATION_POLICY,json,hash(json));
    }
    static CompetitionRosterSnapshot registeredPair(JdbcTemplate jdbc,String career,int year,String competition,
                                                    CompetitionRosterSnapshot registered,String first,String second) {
        if(saved(jdbc,career,year)==null)return registered.pair(first,second);
        var directory=directory(jdbc,career);var state=saved(jdbc,career,year).state();
        var authority=CareerMarketStore.eligibility(jdbc,career);
        var teams=new TreeMap<String,CompetitionRosterSnapshot.Roster>();
        String managed="LCK:"+jdbc.queryForObject("SELECT managed_team_code FROM career_save WHERE career_id=?",String.class,career);
        for(String team:List.of(first,second)) {
            var base=registered.roster(team);
            var allowed=new ArrayList<>(CareerMarketStore.registeredIds(jdbc,career,year,competition,team,registered));
            var players=new ArrayList<CompetitionRosterSnapshot.Starter>();
            for(var original:base.players()) {
                Position role=original.position();
                java.util.function.Predicate<String> eligible=id->directory.players().get(id).position()==role
                        && CareerOverseasRoster.owner(team).equals(state.members().get(id).ownerTeam()) && (team.equals("LEC:KCB")?"DEVELOPMENT":"FIRST_TEAM").equals(state.members().get(id).squad())
                        && state.members().get(id).eligibilityReason()==null && authority.allows(CareerOverseasRoster.owner(team),id);
                var valid=allowed.stream().filter(eligible).toList();
                if(valid.isEmpty()&&!team.equals(managed)&&authority.enabled()) {
                    var candidates=team.equals("LEC:KCB")?state.members().keySet().stream().filter(eligible).sorted(Comparator.comparingInt((String id)->CareerMarketPolicy.strength(directory.players().get(id))).reversed().thenComparing(id->id)).toList():state.lineups().getOrDefault(team,List.of());
                    var replacement=candidates.stream().filter(eligible).findFirst();
                    if(replacement.isPresent()){CareerMarketStore.supplement(jdbc,career,year,competition,team,replacement.get(),CareerMarketStore.executionDate(jdbc,career));allowed.add(replacement.get());valid=List.of(replacement.get());}
                }
                if(valid.isEmpty())throw CareerException.invalid("registration",team+"의 "+role+" 등록 선수가 현재 출전할 수 없습니다. 계약·선발을 복구하고 보충등록해 주세요.");
                var selected=state.lineups().getOrDefault(team,List.of()).stream().filter(eligible).filter(allowed::contains).findFirst();
                String id=selected.orElse(valid.contains(original.playerId())?original.playerId():valid.getFirst());
                players.add(directory.players().get(id).gameplay());
            }
            teams.put(team,new CompetitionRosterSnapshot.Roster(base.team(),hash(write(players)),players));
        }
        return new CompetitionRosterSnapshot(teams);
    }

    public void lockLeagueSeason(String seasonId) {
        var careers=jdbc.query("SELECT career_id FROM career_season WHERE season_id=?",(r,n)->r.getString(1),seasonId);
        if(!careers.isEmpty())lockCareer(jdbc,careers.getFirst());
    }
    public CompetitionRosterSnapshot leagueFixtureRoster(String seasonId,String fixtureId) {
        var values=jdbc.query("SELECT roster_json,roster_hash FROM career_league_fixture_roster WHERE season_id=? AND fixture_id=?",(r,n)->{
            var roster=CompetitionRosterSnapshot.decode(r.getString(1));
            if(!roster.identity().equals(r.getString(2)))throw new IllegalStateException("LEAGUE_FIXTURE_ROSTER_INTEGRITY");return roster;
        },seasonId,fixtureId);return values.isEmpty()?null:values.getFirst();
    }
    public CompetitionRosterSnapshot freezeLeagueFixture(String seasonId,String fixtureId) {
        // Caller owns the Calendar lock before taking any League/fixture lock.
        var prior=leagueFixtureRoster(seasonId,fixtureId);if(prior!=null)return prior;
        var scope=jdbc.query("SELECT career_id,season_year FROM career_season WHERE season_id=?",(r,n)->Map.entry(r.getString(1),r.getInt(2)),seasonId);
        if(scope.isEmpty())return null;var key=scope.getFirst();
        var state=saved(jdbc,key.getKey(),key.getValue());if(state==null)return null;
        if(jdbc.queryForObject("SELECT COUNT(*) FROM league_player_binding WHERE season_id=? AND fixture_id=?",Integer.class,seasonId,fixtureId)>0
                ||jdbc.queryForObject("SELECT COUNT(*) FROM league_job WHERE season_id=? AND fixture_id=?",Integer.class,seasonId,fixtureId)>0)return null;
        if(activeYear(jdbc,key.getKey())!=key.getValue())throw new IllegalStateException("HISTORICAL_LEAGUE_ROSTER_READ_ONLY");
        var teams=jdbc.queryForObject("SELECT first_team_code,second_team_code FROM league_fixture WHERE season_id=? AND fixture_id=?",(r,n)->List.of(r.getString(1),r.getString(2)),seasonId,fixtureId);
        var frozen=eligiblePair(jdbc,key.getKey(),key.getValue(),"LCK:"+teams.get(0),"LCK:"+teams.get(1)).domesticPair(teams.get(0),teams.get(1));
        jdbc.update("INSERT INTO career_league_fixture_roster VALUES (?,?,?,?,?,?,?)",seasonId,fixtureId,key.getKey(),key.getValue(),state.revision(),frozen.encoded(),frozen.identity());
        String series=jdbc.queryForObject("SELECT bound_series_id FROM league_fixture WHERE season_id=? AND fixture_id=?",String.class,seasonId,fixtureId);
        CareerAppearanceStore.capture(jdbc,key.getKey(),key.getValue(),"LEAGUE|"+seasonId+'|'+fixtureId,series,null,frozen);
        return frozen;
    }

    public static String write(Object value) { try{return JSON.writeValueAsString(value);}catch(java.io.IOException e){throw new IllegalStateException(e);} }
    public static <T>T read(String value,Class<T> type) { try{return JSON.readValue(value,type);}catch(java.io.IOException e){throw new IllegalStateException(e);} }
    public static String hash(String value) { return CareerInternationalRules.hash(value); }
}
