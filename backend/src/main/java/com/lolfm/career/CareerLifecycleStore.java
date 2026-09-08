package com.lolfm.career;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.domain.Position;
import com.lolfm.player.ExpandedPlayerCatalog.Definition;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.lolfm.career.CareerLifecycleState.*;
import static com.lolfm.career.CareerRosterStore.*;

@Service
public final class CareerLifecycleStore {
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final ChampionCatalog champions;
    public CareerLifecycleStore(JdbcTemplate jdbc,PlatformTransactionManager manager,ChampionCatalog champions){this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.champions=champions;}
    public static CareerLifecycleState load(JdbcTemplate jdbc,String career) {
        var markers=jdbc.query("SELECT lifecycle_version FROM career_player_directory WHERE career_id=?",(r,n)->r.getString(1),career);
        var rows=jdbc.query("SELECT state_json,state_hash FROM career_lifecycle_state WHERE career_id=?",(r,n)->{
            if(!hash(r.getString(1)).equals(r.getString(2)))throw new IllegalStateException("LIFECYCLE_STATE_INTEGRITY");return read(r.getString(1),CareerLifecycleState.class);
        },career);
        boolean marked=!markers.isEmpty()&&markers.getFirst()!=null;
        if(!marked&&rows.isEmpty())return null;
        if(!marked||rows.isEmpty())throw new IllegalStateException("LIFECYCLE_INITIALIZATION_CONFLICT");
        if(!CareerLifecyclePolicy.VERSION.equals(markers.getFirst())||!CareerLifecyclePolicy.VERSION.equals(rows.getFirst().policyVersion()))throw new IllegalStateException("LIFECYCLE_POLICY_UNSUPPORTED");
        return rows.getFirst();
    }
    public static void initialize(JdbcTemplate jdbc,String career) {
        lockCareer(jdbc,career);if(load(jdbc,career)!=null)return;
        var development=CareerDevelopmentStore.load(jdbc,career);if(development==null)return;
        var directory=sourceDirectory(jdbc,career);var market=CareerMarketStore.load(jdbc,career);if(market==null)return;
        LocalDate date=market.state().processedThrough();var people=new TreeMap<String,Person>();
        for(var d:directory.players().values())people.put(d.playerId(),new Person(CareerLifecyclePolicy.ageProfile(d,market.state().seed(),date),"AUTHORED",null,date,
                CareerLifecyclePolicy.ca(development.state().players().get(d.playerId())),date,Status.ACTIVE,null,null,"현재부터 관측",0,0,0,null,"NOT_YET_REVIEWED",market.state().freeAgents().contains(d.playerId())?date:null));
        var operating=CareerRosterStore.saved(jdbc,career,activeYear(jdbc,career)).state();var observed=new CareerLifecycleEngine(new CareerLifecycleState(CareerLifecyclePolicy.VERSION,date,null,people));operating.members().forEach((id,member)->observed.observePlacement(id,member,date));people.clear();people.putAll(observed.people);
        int year=activeYear(jdbc,career);boolean stove=jdbc.queryForObject("SELECT COUNT(*) FROM career_market_season_close WHERE career_id=? AND season_year=?",Integer.class,career,year)>0;
        var state=new CareerLifecycleState(CareerLifecyclePolicy.VERSION,date,stove?year:null,people);String json=write(state);
        jdbc.update("INSERT INTO career_lifecycle_state VALUES (?,0,?,?)",career,json,hash(json));jdbc.update("UPDATE career_player_directory SET lifecycle_version=? WHERE career_id=?",CareerLifecyclePolicy.VERSION,career);
        if(stove)saveReview(jdbc,career,new Review(year,date,CareerLifecyclePolicy.VERSION,"LEGACY_STOVE_NO_RETROACTIVE_REVIEW",List.of(),List.of(),null));
    }
    static void placementChanged(JdbcTemplate jdbc,String career,String player,Membership member,LocalDate date) {
        var state=load(jdbc,career);if(state==null)return;var engine=new CareerLifecycleEngine(state);engine.clEnabled=CareerClStore.active(jdbc,career,activeYear(jdbc,career));engine.overseasEnabled=CareerOverseasStore.active(jdbc,career,activeYear(jdbc,career));engine.observePlacement(player,member,date);persist(jdbc,career,engine);
    }
    public void recover(){for(String career:jdbc.query("SELECT career_id FROM career_player_directory WHERE directory_version=? ORDER BY career_id",(r,n)->r.getString(1),com.lolfm.player.ExpandedPlayerCatalog.VERSION))tx.executeWithoutResult(s->{initialize(jdbc,career);migrateGeneratedNames(jdbc,career);});}
    static void migrateGeneratedNames(JdbcTemplate jdbc,String career) {
        lockCareer(jdbc,career);var market=CareerMarketStore.load(jdbc,career);if(market==null)return;
        var directory=compose(jdbc,career,sourceDirectory(jdbc,career));var occupied=new HashSet<String>();directory.players().values().forEach(d->occupied.add(d.nickname().toLowerCase(Locale.ROOT)));
        for(var d:jdbc.query("SELECT definition_json,definition_hash FROM career_generated_player WHERE career_id=? ORDER BY player_id",(r,n)->{if(!hash(r.getString(1)).equals(r.getString(2)))throw new IllegalStateException("GENERATED_PLAYER_INTEGRITY");return read(r.getString(1),Definition.class);},career)) {
            if(!CareerGeneratedNames.placeholder(d))continue;
            var named=CareerGeneratedNames.assign(d,market.state().seed(),occupied);String json=write(named);
            jdbc.update("UPDATE career_generated_player SET definition_json=?,definition_hash=? WHERE career_id=? AND player_id=?",json,hash(json),career,d.playerId());
        }
    }
    static void persist(JdbcTemplate jdbc,String career,CareerLifecycleEngine engine) {
        String json=write(engine.state()),digest=hash(json);var current=load(jdbc,career);if(current==null)throw new IllegalStateException("LIFECYCLE_REQUIRED_STATE");
        jdbc.update("UPDATE career_lifecycle_state SET revision=revision+1,state_json=?,state_hash=? WHERE career_id=? AND state_hash<>?",json,digest,career,digest);
    }
    public static Directory compose(JdbcTemplate jdbc,String career,Directory source) {
        var people=load(jdbc,career);var definitions=new TreeMap<>(source.players());
        for(var d:jdbc.query("SELECT definition_json,definition_hash FROM career_generated_player WHERE career_id=? ORDER BY player_id",(r,n)->{
            if(!hash(r.getString(1)).equals(r.getString(2)))throw new IllegalStateException("GENERATED_PLAYER_INTEGRITY");return read(r.getString(1),Definition.class);
        },career))if(definitions.putIfAbsent(d.playerId(),d)!=null)throw new IllegalStateException("GENERATED_PLAYER_ID_CONFLICT");
        if(people==null){if(definitions.size()!=source.players().size())throw new IllegalStateException("GENERATED_LIFECYCLE_MISSING");return source;}
        if(!definitions.keySet().equals(people.players().keySet()))throw new IllegalStateException("LIFECYCLE_POPULATION_REFERENCE");
        definitions.replaceAll((id,d)->{
            var p=people.players().get(id);var node=read(d.detailsJson(),ObjectNode.class);node.set("careerAge",read(write(p.age()),com.fasterxml.jackson.databind.JsonNode.class));node.put("careerLifecycleStatus",p.status().name());
            return new Definition(id,d.nickname(),d.position(),d.gameplay(),d.provisional(),d.initialOrganizationId(),d.initialOwnerTeam(),d.initialSquad(),d.eligibilityReason(),node.toString());
        });return new Directory(definitions,source.organizations());
    }
    static void saveReview(JdbcTemplate jdbc,String career,Review review){String json=write(review);jdbc.update("INSERT INTO career_lifecycle_review VALUES (?,?,?,?)",career,review.seasonYear(),json,hash(json));}
    static List<Review> reviews(JdbcTemplate jdbc,String career,int year){return jdbc.query("SELECT review_json,review_hash FROM career_lifecycle_review WHERE career_id=? AND season_year=?",(r,n)->{if(!hash(r.getString(1)).equals(r.getString(2)))throw new IllegalStateException("LIFECYCLE_REVIEW_INTEGRITY");return read(r.getString(1),Review.class);},career,year);}
    /** Called only after the existing complete-results gate, in the same Calendar transaction. */
    public Review review(String career,int year,LocalDate date) {
        lockCareer(jdbc,career);var existing=reviews(jdbc,career,year);if(!existing.isEmpty())return existing.getFirst();
        var life=load(jdbc,career);if(life==null)return null;
        if(year!=activeYear(jdbc,career)||life.lastReviewedSeason()!=null&&year<=life.lastReviewedSeason())throw new IllegalStateException("LIFECYCLE_REVIEW_SCOPE");
        var oldMarket=CareerMarketStore.load(jdbc,career);var m=CareerMarketStore.engine(jdbc,career,year,oldMarket);
        if(!date.equals(oldMarket.state().processedThrough()))throw new IllegalStateException("LIFECYCLE_REVIEW_DATE");
        var oldGrowth=CareerDevelopmentStore.load(jdbc,career);var development=new CareerDevelopmentEngine(baseDirectory(jdbc,career),oldGrowth.state());
        if(!development.state().nextSettlement().equals(date))throw new IllegalStateException("LIFECYCLE_REVIEW_GROWTH_DATE");
        var changes=new ArrayList<CareerLifecycleState.Change>();var domesticSeries=domesticSeries(career,year);var effective=CareerLifecyclePolicy.retirementDate(year,date);
        for(String id:new TreeSet<>(m.lifecycle.people.keySet())) {
            var p=m.lifecycle.people.get(id);if(p.status()==Status.RETIRED||p.status()==Status.RETIREMENT_ANNOUNCED||p.intakeYear()!=null&&p.intakeYear()>=year)continue;
            var before=development.players.get(id);int beforeCA=CareerLifecyclePolicy.ca(before),age=CareerLifecyclePolicy.age(p.age(),date);
            var observation=observation(m,id,year,date,domesticSeries);var decline=CareerLifecyclePolicy.decline(before,m.player(id).position(),age,p.declineRemainder(),p.declineCursor(),observation);
            development.players.put(id,decline.player());int ca=CareerLifecyclePolicy.ca(decline.player());
            int empty=observation.consecutiveEmpty(p,year);
            boolean youngFA=CareerLifecyclePolicy.longYoungFA(p,m.lifecycle.appliedOn,year);
            var scheduled=m.scheduled(id);var active=m.active(id,date);boolean next=scheduled!=null&&!scheduled.terms().endDate().isBefore(effective)||active!=null&&!active.terms().endDate().isBefore(effective);
            int probability=CareerLifecyclePolicy.retirementProbability(age,observation,empty,youngFA,m.freeAgents.contains(id),beforeCA-ca,ca,m.promiseEngine.mood(id,date),next);
            int roll=CareerLifecyclePolicy.draw(m.seed,id,year,"RETIREMENT_REVIEW",10000);boolean retirement=roll<probability*100;
            Status status=retirement?Status.RETIREMENT_ANNOUNCED:probability>=CareerLifecyclePolicy.CONSIDERING_THRESHOLD?Status.CONSIDERING_RETIREMENT:Status.ACTIVE;
            String reason="게임 나이 "+age+" · 관측 "+observation.coverage()+" · 실제 기회/선발 "+observation.opportunities()+"/"+observation.starts()+" · 은퇴 확률 "+probability+"% · PA 미사용";
            m.lifecycle.people.put(id,new Person(p.age(),p.source(),p.intakeYear(),p.introducedOn(),Math.max(p.peakCA(),beforeCA),p.peakObservedSince(),status,retirement?date:null,retirement?effective:null,reason,decline.carried(),decline.cursor(),empty,year,observation.coverage(),p.freeAgentSince(),p.domesticObservedSince(),p.observedSquad()));
            if(retirement)m.lifecycle.cancelReservations(m,id,date);
            changes.add(new CareerLifecycleState.Change(id,age,beforeCA,ca,decline.budget(),decline.applied(),decline.carried(),decline.limited(),decline.deltas(),observation,probability,roll,status.name(),retirement?date:null,retirement?effective:null,reason));
        }
        m.lifecycle.lastReview=year;
        var available=new EnumMap<Position,Integer>(Position.class);var missing=new EnumMap<Position,Integer>(Position.class);
        for(var role:Position.values()) {
            int candidates=0,clubs=0;
            for(String id:m.members.keySet())if(m.player(id).position()==role&&!m.lifecycle.announced(id)&&m.player(id).eligibilityReason()==null&&m.scheduled(id)==null&&!m.tradeEngine.hasAgreement(id)) {
                var c=m.active(id,date);if(m.freeAgents.contains(id)||c!=null&&c.terms().endDate().isBefore(effective))candidates++;
            }
            for(String team:m.accounts.keySet()) {
                boolean covered=m.contracts.values().stream().anyMatch(c->team.equals(c.team())&&(c.status()==CareerMarketState.ContractStatus.ACTIVE||c.status()==CareerMarketState.ContractStatus.SCHEDULED)&&!m.lifecycle.announced(c.playerId())&&m.player(c.playerId()).position()==role&&m.player(c.playerId()).eligibilityReason()==null&&!c.terms().startDate().isAfter(effective)&&!c.terms().endDate().isBefore(effective));
                if(!covered)clubs++;
            }
            available.put(role,candidates);missing.put(role,clubs);
        }
        int projected=(int)m.lifecycle.people.values().stream().filter(p->p.status()!=Status.RETIRED&&p.status()!=Status.RETIREMENT_ANNOUNCED).count();
        int normal=CareerLifecyclePolicy.normalCount(projected);var normalRoles=CareerLifecyclePolicy.allocate(normal,available);var shortfall=new EnumMap<Position,Integer>(Position.class);
        for(var role:Position.values())shortfall.put(role,Math.max(0,missing.get(role)-available.get(role)-normalRoles.get(role)));
        var urgentRoles=CareerLifecyclePolicy.emergencyAllocation(missing,available,normalRoles);int urgent=urgentRoles.values().stream().mapToInt(Integer::intValue).sum();
        var regions=m.accounts.keySet().stream().map(CareerMarketPolicy::region).distinct().sorted().toList();
        var occupiedNames=new HashSet<String>();m.directory.players().values().forEach(d->occupiedNames.add(d.nickname().toLowerCase(Locale.ROOT)));
        var rookies=new ArrayList<>(CareerRookieFactory.generate(career,m.seed,year+1,date,normalRoles,false,1,champions,regions,occupiedNames));
        rookies.addAll(CareerRookieFactory.generate(career,m.seed,year+1,date,urgentRoles,true,normal+1,champions,regions,occupiedNames));
        for(var rookie:rookies) {
            var d=rookie.definition();String json=write(d);jdbc.update("INSERT INTO career_generated_player VALUES (?,?,?,?,?,?)",career,d.playerId(),year+1,date,json,hash(json));
            int ca=com.lolfm.player.PlayerAbilityPolicy.currentAbility(d.gameplay().ratings());m.lifecycle.people.put(d.playerId(),new Person(rookie.age(),"GENERATED",year+1,date,ca,date,Status.ACTIVE,null,null,"생성 신인 · 게임 데이터",0,0,0,null,"NOT_YET_REVIEWED",date));
            development.players.put(d.playerId(),CareerDevelopmentPolicy.initial(d));m.members.put(d.playerId(),new Membership(d.playerId(),null,null,"UNAFFILIATED",null));m.preferences.put(d.playerId(),CareerMarketPolicy.preference(m.seed,d));m.freeAgents.add(d.playerId());
        }
        persist(jdbc,career,m.lifecycle);
        var composed=new CareerDevelopmentEngine(baseDirectory(jdbc,career),development.state());CareerDevelopmentStore.persist(jdbc,career,oldGrowth,composed);m.directory=composed.directory();
        CareerMarketStore.persist(jdbc,career,year,oldMarket,m);
        var generated=new TreeMap<String,Integer>();var unavailable=new TreeMap<String,Integer>();for(var role:Position.values()){generated.put(role.name(),normalRoles.get(role)+urgentRoles.get(role));unavailable.put(role.name(),Math.max(0,shortfall.get(role)-urgentRoles.get(role)));}
        var review=new Review(year,date,CareerLifecyclePolicy.VERSION,"SEASON_REVIEW",changes,rookies.stream().map(r->r.definition().playerId()).toList(),new Supply(projected,normal,urgent,stringKeys(available),stringKeys(missing),generated,unavailable));
        saveReview(jdbc,career,review);return review;
    }
    public void prepareClSupply(String career,int year){
        if(!CareerClStore.active(jdbc,career,year))return;var cl=CareerClStore.load(jdbc,career,year);if(cl.supplyIssued())return;
        var old=CareerMarketStore.load(jdbc,career);var m=CareerMarketStore.engine(jdbc,career,year,old);CareerClStore.prepare(jdbc,career,year,m);
        var missing=new EnumMap<Position,Integer>(Position.class);var available=new EnumMap<Position,Integer>(Position.class);
        for(var role:Position.values()){
            int gaps=0;for(String code:CareerClPolicy.TEAMS){String team="LCK:"+code;if(CareerClStore.candidates(m,team).stream().noneMatch(id->m.player(id).position()==role)&&m.members.values().stream().noneMatch(v->team.equals(v.ownerTeam())&&!m.lineups.get(team).contains(v.playerId())&&m.player(v.playerId()).position()==role&&m.eligible(v.playerId(),team,old.state().processedThrough()))&&m.contracts.values().stream().noneMatch(c->team.equals(c.team())&&c.status()==CareerMarketState.ContractStatus.SCHEDULED&&c.terms().role()==CareerMarketState.Role.DEVELOPMENT&&m.player(c.playerId()).position()==role))gaps++;}
            missing.put(role,gaps);available.put(role,(int)m.freeAgents.stream().filter(id->m.player(id).position()==role&&m.player(id).eligibilityReason()==null&&m.scheduled(id)==null&&!m.tradeEngine.hasAgreement(id)&&!m.lifecycle.announced(id)).count());
        }
        var allocation=CareerLifecyclePolicy.emergencyAllocation(missing,available,Map.of());var names=new HashSet<String>();m.directory.players().values().forEach(d->names.add(d.nickname().toLowerCase(Locale.ROOT)));
        int sequence=jdbc.queryForObject("SELECT COUNT(*)+1 FROM career_generated_player WHERE career_id=? AND intake_year=?",Integer.class,career,year);LocalDate date=old.state().processedThrough();
        var rookies=CareerRookieFactory.generate(career,m.seed,year,date,allocation,true,sequence,champions,List.of("LCK"),names);
        var oldGrowth=CareerDevelopmentStore.load(jdbc,career);var growth=new CareerDevelopmentEngine(baseDirectory(jdbc,career),oldGrowth.state());
        for(var rookie:rookies){var d=rookie.definition();String json=write(d);jdbc.update("INSERT INTO career_generated_player VALUES (?,?,?,?,?,?)",career,d.playerId(),year,date,json,hash(json));
            m.lifecycle.people.put(d.playerId(),new Person(rookie.age(),"GENERATED",year,date,com.lolfm.player.PlayerAbilityPolicy.currentAbility(d.gameplay().ratings()),date,Status.ACTIVE,null,null,"CL 개막 전 제한된 공급 · FA",0,0,0,null,"NOT_YET_REVIEWED",date));
            growth.players.put(d.playerId(),CareerDevelopmentPolicy.initial(d));m.members.put(d.playerId(),new Membership(d.playerId(),null,null,"UNAFFILIATED",null));m.preferences.put(d.playerId(),CareerMarketPolicy.preference(m.seed,d));m.freeAgents.add(d.playerId());
        }
        persist(jdbc,career,m.lifecycle);var composed=new CareerDevelopmentEngine(baseDirectory(jdbc,career),growth.state());CareerDevelopmentStore.persist(jdbc,career,oldGrowth,composed);m.directory=composed.directory();
        CareerMarketStore.persist(jdbc,career,year,old,m);cl=CareerClStore.load(jdbc,career,year);CareerClStore.save(jdbc,career,year,new CareerClStore.State(cl.revision()+1,cl.lineups(),cl.registered(),cl.ranking(),true));
    }
    private static Map<String,Integer> stringKeys(Map<Position,Integer> values){var result=new TreeMap<String,Integer>();values.forEach((k,v)->result.put(k.name(),v));return result;}
    private Set<String> domesticSeries(String career,int year) {
        var ids=new HashSet<>(jdbc.query("SELECT f.bound_series_id FROM league_fixture f JOIN career_season s ON s.season_id=f.season_id WHERE s.career_id=? AND s.season_year=?",(r,n)->r.getString(1),career,year));
        jdbc.query("SELECT competition_id,series_id FROM career_competition_fixture WHERE career_id=? AND calendar_season_year=?",(org.springframework.jdbc.core.RowCallbackHandler)r->{if(!CareerInternationalRules.COMPETITIONS.contains(r.getString(1)))ids.add(r.getString(2));},career,year);return ids;
    }
    static Observation observation(CareerMarketEngine m,String id,int year,LocalDate date,Set<String> domesticSeries) {
        var person=m.lifecycle.people.get(id);var member=m.members.get(id);boolean guest=m.overseasEnabled&&"LEC:KCB".equals(member.organizationId());String observedSquad=guest?"FIRST_TEAM":member.squad();int opportunities=0,starts=0,sets=0;LocalDate first=null,last=null;
        for(var a:m.promiseEngine.appearances.values())if(a.seasonYear()==year&&!a.date().isBefore(m.lifecycle.appliedOn)) {
            for(var o:a.opportunities())if(o.playerId().equals(id)&&o.selected())sets+=a.completedSets();
            if(!domesticSeries.contains(a.seriesId())||!a.squad().equals(observedSquad))continue;
            var o=a.opportunities().stream().filter(v->v.playerId().equals(id)&&v.eligible()).findFirst().orElse(null);if(o==null)continue;
            opportunities++;if(o.selected())starts++;if(first==null||a.date().isBefore(first))first=a.date();if(last==null||a.date().isAfter(last))last=a.date();
        }
        var employed=m.active(id,date);
        boolean continuous=person.domesticObservedSince()!=null&&!person.domesticObservedSince().isAfter(LocalDate.of(year,1,1))&&employed!=null&&!employed.terms().startDate().isAfter(LocalDate.of(year,1,1))&&m.tradeEngine.loans.values().stream().noneMatch(l->l.playerId().equals(id)&&!l.endDate().isBefore(LocalDate.of(year,1,1)));
        String coverage=member.ownerTeam()==null?"NO_DOMESTIC_EMPLOYMENT":!member.ownerTeam().startsWith("LCK:")&&!m.overseasEnabled||!guest&&"DEVELOPMENT".equals(member.squad())&&(!member.ownerTeam().startsWith("LCK:")||!m.clEnabled)?"OVERSEAS_OR_CL_UNOBSERVED":person.introducedOn().isAfter(LocalDate.of(year,1,1))||m.lifecycle.appliedOn.isAfter(LocalDate.of(year,1,1))?"PARTIAL_FIRST_SEASON":!continuous?"PARTIAL_EMPLOYMENT":opportunities<CareerLifecyclePolicy.MIN_OBSERVED_SERIES||first==null||ChronoUnit.DAYS.between(first,last)+("DEVELOPMENT".equals(member.squad())?1:0)<CareerLifecyclePolicy.MIN_OBSERVED_DAYS?"INSUFFICIENT_OPPORTUNITIES":"DEVELOPMENT".equals(member.squad())?"FULL_DEVELOPMENT_SEASON":"FULL_DOMESTIC_SEASON";
        return new Observation(coverage,opportunities,starts,sets,first,last);
    }
    public record Profile(String playerId,String nickname,String position,int currentCA,Integer potentialAbility,int gameAge,int seasonGrowth,int seasonDecline,int seasonNet,Person lifecycle) {}
    public record View(String schemaVersion,String careerId,int seasonYear,LocalDate date,boolean readOnly,int activeCount,int archivedCount,List<Profile> players,List<Review> reviews,String policyVersion) {}
    public View view(String career,int year){return tx.execute(s->{lockCareer(jdbc,career);var current=load(jdbc,career);if(current==null)throw CareerException.invalid("lifecycle","서버 시작 시 생애주기 이주가 필요합니다.");boolean historical=year!=activeYear(jdbc,career);CareerLifecycleState state=current;LocalDate date=CareerMarketStore.date(jdbc,career);
        if(historical){var rows=jdbc.query("SELECT lifecycle_json,lifecycle_hash,closed_date FROM career_development_season_close WHERE career_id=? AND season_year=?",(r,n)->new String[]{r.getString(1),r.getString(2),r.getString(3)},career,year);if(rows.isEmpty()||rows.getFirst()[0]==null)return new View("CAREER_LIFECYCLE_VIEW_V1",career,year,date,true,0,0,List.of(),reviews(jdbc,career,year),CareerLifecyclePolicy.VERSION);var row=rows.getFirst();if(!hash(row[0]).equals(row[1]))throw new IllegalStateException("LIFECYCLE_HISTORY_INTEGRITY");state=read(row[0],CareerLifecycleState.class);date=LocalDate.parse(row[2]);}
        var directory=historical?CareerDevelopmentStore.historicalDirectory(jdbc,career,year):directory(jdbc,career);var growthRows=historical?jdbc.query("SELECT state_json FROM career_development_season_close WHERE career_id=? AND season_year=?",(r,n)->read(r.getString(1),CareerDevelopmentState.class),career,year):List.of(CareerDevelopmentStore.load(jdbc,career).state());
        var gains=new HashMap<String,Integer>();if(!growthRows.isEmpty())growthRows.getFirst().monthly().values().stream().filter(g->g.seasonYear()==year).forEach(g->gains.merge(g.playerId(),g.internalGain(),Integer::sum));
        var losses=new HashMap<String,Integer>();reviews(jdbc,career,year).forEach(r->r.changes().forEach(c->losses.put(c.playerId(),c.appliedDecline())));
        var profiles=new ArrayList<Profile>();for(var e:state.players().entrySet()){var d=directory.players().get(e.getKey());if(d==null)throw new IllegalStateException("LIFECYCLE_HISTORY_REFERENCE");profiles.add(new Profile(e.getKey(),d.nickname(),d.position().name(),com.lolfm.player.PlayerAbilityPolicy.currentAbility(d.gameplay().ratings()),CareerDevelopmentPolicy.metadata(d).potential(),CareerLifecyclePolicy.age(e.getValue().age(),date),gains.getOrDefault(e.getKey(),0),losses.getOrDefault(e.getKey(),0),gains.getOrDefault(e.getKey(),0)-losses.getOrDefault(e.getKey(),0),e.getValue()));}
        profiles.sort(Comparator.comparing(Profile::playerId));int retired=(int)state.players().values().stream().filter(p->p.status()==Status.RETIRED).count();return new View("CAREER_LIFECYCLE_VIEW_V1",career,year,date,historical,profiles.size()-retired,retired,profiles,reviews(jdbc,career,year),CareerLifecyclePolicy.VERSION);});}
}
