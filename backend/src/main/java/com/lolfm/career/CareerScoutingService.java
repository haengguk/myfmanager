package com.lolfm.career;

import static com.lolfm.career.CareerRosterStore.*;
import java.time.LocalDate;
import java.util.*;
import com.lolfm.player.ExpandedPlayerCatalog;
import com.lolfm.player.PlayerAbilityPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Public current Career information. No scout randomness, pricing policy or gameplay writes. */
@Service
public final class CareerScoutingService {
    private final JdbcTemplate db;private final TransactionTemplate readTx,writeTx;private final CareerMarketStore market;private final CareerRecordsQuery records;
    public CareerScoutingService(JdbcTemplate db,PlatformTransactionManager manager,CareerMarketStore market,CareerRecordsQuery records){this.db=db;this.market=market;this.records=records;readTx=new TransactionTemplate(manager);readTx.setReadOnly(true);readTx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);writeTx=new TransactionTemplate(manager);}
    public record Interest(String playerId,long revision,boolean selected) {}
    public record InterestRequest(String playerId,long expectedRevision,boolean selected) {}
    public record Candidate(ExpandedPlayerCatalog.Definition player,Membership membership,int ca,Integer pa,Integer age,String ageBasis,String source,String status,boolean starter,Long salary,LocalDate expires,CareerMarketStore.PlayerMarket market,CareerMarketStore.TradeQuote quote,Interest interest) {}
    public record Page(String careerId,int activeYear,LocalDate date,String asOf,int population,int total,int nextCursor,List<Candidate> players,Map<String,String> teams,List<String> starters,Map<String,String> starterByPosition,CareerMarketStore.Finance finance) {}
    record Context(int year,LocalDate date,String stamp,List<Candidate> people,Map<String,String> teams,List<String> starters,CareerMarketStore.Finance finance) {}
    private Context context(String career){
        int year=activeYear(db,career);var roster=saved(db,career,year);var directory=directory(db,career);var life=CareerLifecycleStore.load(db,career);var view=market.readView(career,year);var interests=interests(career);
        var offers=new HashMap<String,CareerMarketStore.PlayerMarket>();view.players().forEach(p->offers.put(p.playerId(),p));var quotes=new HashMap<String,CareerMarketStore.TradeQuote>();view.management().quotes().forEach(q->quotes.put(q.playerId(),q));var contracts=new HashMap<String,CareerMarketState.Contract>();view.contracts().forEach(c->contracts.put(c.contractId(),c));
        var rows=new ArrayList<Candidate>();
        for(var p:directory.players().values()){var m=roster.state().members().get(p.playerId());var pm=offers.get(p.playerId());var c=pm==null?null:contracts.get(pm.currentContractId());var l=life==null?null:life.players().get(p.playerId());
            String status=l!=null&&l.status()==CareerLifecycleState.Status.RETIRED?"RETIRED":pm==null?"UNAVAILABLE":pm.status();boolean starter=m!=null&&m.ownerTeam()!=null&&roster.state().lineups().getOrDefault(m.ownerTeam(),List.of()).contains(p.playerId());
            rows.add(new Candidate(p,m,PlayerAbilityPolicy.currentAbility(p.gameplay().ratings()),CareerDevelopmentPolicy.metadata(p).potential(),l==null?null:CareerLifecyclePolicy.age(l.age(),view.currentDate()),l==null?"UNKNOWN":l.age().basis(),l==null?"SAVED_DIRECTORY":l.source(),status,starter,c==null?null:c.terms().annualSalary(),c==null?null:c.terms().endDate(),pm,quotes.get(p.playerId()),interests.getOrDefault(p.playerId(),new Interest(p.playerId(),0,false))));
        }
        var teams=new TreeMap<String,String>();directory.organizations().values().forEach(o->{if(o.competitiveTeam()!=null)teams.put(o.competitiveTeam(),o.displayName());});
        String stamp=hash(write(List.of(year,view.currentDate(),roster.revision(),view.revision(),CareerDevelopmentStore.load(db,career).revision(),CareerRecordsStore.revision(db,career),new TreeMap<>(interests))));
        return new Context(year,view.currentDate(),stamp,rows,teams,roster.state().lineups().getOrDefault(view.managedTeam(),List.of()),view.finances().stream().filter(f->f.team().equals(view.managedTeam())).findFirst().orElse(null));
    }
    Map<String,Interest> interests(String career){var result=new TreeMap<String,Interest>();db.query("SELECT player_id,revision,selected FROM career_scout_interest WHERE career_id=?",(org.springframework.jdbc.core.RowCallbackHandler)r->result.put(r.getString(1),new Interest(r.getString(1),r.getLong(2),r.getBoolean(3))),career);return result;}
    public Interest interest(String career,InterestRequest request){return writeTx.execute(t->{
        if(request==null||request.expectedRevision()<0||!baseDirectory(db,career).players().containsKey(request.playerId()))throw CareerException.invalid("playerId","현재 Career의 선수를 선택하세요.");
        try{db.update("INSERT INTO career_scout_interest SELECT ?,?,0,FALSE WHERE NOT EXISTS (SELECT 1 FROM career_scout_interest WHERE career_id=? AND player_id=?)",career,request.playerId(),career,request.playerId());}catch(org.springframework.dao.DuplicateKeyException race){/* concurrent first metadata request; compare revision below */}
        db.update("UPDATE career_scout_interest SET selected=?,revision=revision+1 WHERE career_id=? AND player_id=? AND revision=? AND selected<>?",request.selected(),career,request.playerId(),request.expectedRevision(),request.selected());
        return interests(career).get(request.playerId());
    });}
    public Page search(String career,Map<String,String> q){return readTx.execute(t->{var c=context(career);int cursor=integer(q,"cursor",0);if(cursor<0)throw CareerException.invalid("cursor","페이지가 올바르지 않습니다.");if(q.containsKey("asOf")&&!q.get("asOf").equals(c.stamp()))throw CareerException.calendarStaleRevision();
        var selected=c.people().stream().filter(p->matches(p,q,c.finance()==null?"":c.finance().team())).sorted(order(q.getOrDefault("sort","ca"),q.getOrDefault("direction","desc"))).toList();var page=selected.stream().skip(cursor).limit(20).toList();return new Page(career,c.year(),c.date(),c.stamp(),c.people().size(),selected.size(),cursor+20<selected.size()?cursor+20:-1,page,c.teams(),c.starters(),c.people().stream().filter(p->c.starters().contains(p.player().playerId())).collect(java.util.stream.Collectors.toMap(p->p.player().position().name(),p->p.player().playerId())),c.finance());
    });}
    static boolean matches(Candidate p,Map<String,String> q,String managed){
        boolean unknown=Boolean.parseBoolean(q.getOrDefault("unknown","false")),watch=Boolean.parseBoolean(q.getOrDefault("watch","false"));String status=q.getOrDefault("status","");
        if(watch&&!p.interest().selected()||!watch&&status.isEmpty()&&p.status().equals("RETIRED"))return false;
        boolean statusMatches=switch(status){
            case "UNCONFIRMED" -> !p.status().equals("FREE_AGENT")&&(p.membership()==null||"AFFILIATION_UNCONFIRMED".equals(p.membership().eligibilityReason()));
            case "STARTER" -> p.starter();
            case "BENCH" -> !p.starter()&&p.membership()!=null&&p.membership().squad().equals("FIRST_TEAM");
            case "FIRST_TEAM","DEVELOPMENT" -> p.membership()!=null&&p.membership().squad().equals(status);
            default -> status.isEmpty()||status.equals(p.status());
        };if(!statusMatches)return false;
        if(!q.getOrDefault("position",p.player().position().name()).equals(p.player().position().name()))return false;
        String search=q.getOrDefault("search","").toLowerCase(Locale.ROOT);var details=read(p.player().detailsJson(),com.fasterxml.jackson.databind.JsonNode.class);
        if(!(p.player().nickname()+" "+details.path("personal").path("legalName").asText()).toLowerCase(Locale.ROOT).contains(search))return false;
        String team=p.membership()==null?null:p.membership().ownerTeam();if(q.containsKey("team")&&!Objects.equals(team,q.get("team")))return false;if(q.containsKey("region")&&(team==null||!team.startsWith(q.get("region")+":")))return false;
        if(!range(p.ca(),q,"caMin","caMax",unknown)||!range(p.age(),q,"ageMin","ageMax",unknown)||!range(p.market()==null?null:p.market().askingSalary(),q,null,"salaryMax",unknown)||!range(p.quote()==null||!p.status().equals("CONTRACTED")?null:p.quote().suggestedTransferFee(),q,null,"feeMax",unknown))return false;
        if(q.containsKey("skill")){Number value=p.player().gameplay().ratings().entrySet().stream().filter(e->e.getKey().name().equals(q.get("skill"))).map(Map.Entry::getValue).findFirst().orElse(null);if(!range(value,q,"skillMin",null,unknown))return false;}
        if(q.containsKey("champion")){Number value=p.player().gameplay().proficiencies().stream().filter(v->v.championId().equals(q.get("champion"))&&v.position()==p.player().position()).map(v->v.value()).findFirst().orElse(null);if(!range(value,q,"proficiencyMin",null,unknown))return false;}
        if(q.containsKey("expiryFrom")&&(p.expires()==null?!unknown:p.expires().isBefore(LocalDate.parse(q.get("expiryFrom")))))return false;if(q.containsKey("expiryThrough")&&(p.expires()==null?!unknown:p.expires().isAfter(LocalDate.parse(q.get("expiryThrough")))))return false;
        if(Boolean.parseBoolean(q.getOrDefault("negotiable","false"))&&!negotiable(p,managed))return false;return true;
    }
    static boolean negotiable(Candidate p,String managed){return !p.status().equals("RETIRED")&&p.market()!=null&&(p.market().availableStart()!=null&&p.market().eligibilityReason()==null||p.quote()!=null&&p.quote().unavailableReason()==null&&p.status().equals("CONTRACTED")&&(p.membership()==null||!Objects.equals(p.membership().ownerTeam(),managed)));}
    static boolean range(Number value,Map<String,String> q,String min,String max,boolean unknown){boolean lo=min!=null&&q.containsKey(min),hi=max!=null&&q.containsKey(max);if(!lo&&!hi)return true;if(value==null)return unknown;return (!lo||value.doubleValue()>=Double.parseDouble(q.get(min)))&&(!hi||value.doubleValue()<=Double.parseDouble(q.get(max)));}
    static Comparator<Candidate> order(String key,String direction){java.util.function.Function<Candidate,Comparable> field=switch(key){case "age"->p->p.age();case "expiry"->p->p.expires();case "salary"->p->p.market()==null?null:p.market().askingSalary();case "fee"->p->p.quote()==null||!p.status().equals("CONTRACTED")?null:p.quote().suggestedTransferFee();default->p->p.ca();};Comparator<Comparable> values=(a,b)->a.compareTo(b);if(!direction.equals("asc"))values=values.reversed();return Comparator.comparing(field,Comparator.nullsLast(values)).thenComparing(p->p.player().playerId());}
    static int integer(Map<String,String> q,String key,int fallback){try{return q.containsKey(key)?Integer.parseInt(q.get(key)):fallback;}catch(NumberFormatException e){throw CareerException.invalid(key,"숫자 조건을 확인하세요.");}}
    public record Comparison(String careerId,int activeYear,LocalDate date,String asOf,int statisticsYear,String competition,List<Candidate> players,List<Map<String,Object>> statistics,List<Map<String,Object>> champions,List<Map<String,Object>> awards,Map<String,List<Map<String,Object>>> growth,CareerMarketStore.Finance finance) {}
    public Comparison compare(String career,List<String> ids,int year,String competition){return readTx.execute(t->{
        var c=context(career);if(ids.size()<2||ids.size()>4||new HashSet<>(ids).size()!=ids.size())throw CareerException.invalid("players","서로 다른 선수 2~4명을 선택하세요.");var players=ids.stream().map(id->c.people().stream().filter(p->p.player().playerId().equals(id)).findFirst().orElseThrow(CareerException::notFound)).toList();if(players.stream().map(p->p.player().position()).distinct().count()!=1)throw CareerException.invalid("position","같은 포지션만 비교할 수 있습니다.");
        String marks=String.join(",",Collections.nCopies(ids.size(),"?"));var args=new ArrayList<Object>(List.of(career,year));args.addAll(ids);String filter="p.career_id=? AND p.season_year=? AND p.player_id IN ("+marks+")";if(competition!=null&&!competition.isBlank()){filter+=" AND s.competition_id=?";args.add(competition);}
        var stats=CareerRecordsQuery.statistics(db.queryForList("SELECT p.player_id AS playerId,p.team_id AS team,s.competition_id AS competition,COUNT(DISTINCT p.record_id) AS series,COUNT(*) AS sets,SUM(CASE WHEN p.won THEN 1 ELSE 0 END) AS wins,SUM(p.kills) AS kills,SUM(p.deaths) AS deaths,SUM(p.assists) AS assists,SUM(p.cs) AS observedCs,SUM(CASE WHEN p.cs IS NOT NULL THEN p.seconds END) AS csObservedSeconds,COUNT(p.cs) AS csObservedUnits,COUNT(p.kills) AS kdaObservedUnits FROM career_record_player p JOIN career_record_series s ON s.record_id=p.record_id WHERE "+filter+" GROUP BY p.player_id,p.team_id,s.competition_id ORDER BY p.player_id,p.team_id,s.competition_id",args.toArray()),true);
        var champions=CareerRecordsQuery.normalize(db.queryForList("SELECT p.player_id AS playerId,p.team_id AS team,s.competition_id AS competition,p.champion_id AS champion,COUNT(*) AS sets,SUM(CASE WHEN p.won THEN 1 ELSE 0 END) AS wins FROM career_record_player p JOIN career_record_series s ON s.record_id=p.record_id WHERE "+filter+" GROUP BY p.player_id,p.team_id,s.competition_id,p.champion_id ORDER BY p.player_id,sets DESC",args.toArray()));
        // Rating versions are read only for these selected players, never averaged across versions.
        var ratings=new TreeMap<String,List<java.math.BigDecimal>>();db.query("SELECT p.player_id,p.team_id,s.competition_id,p.player_json FROM career_record_player p JOIN career_record_series s ON s.record_id=p.record_id WHERE "+filter,(org.springframework.jdbc.core.RowCallbackHandler)r->{var v=read(r.getString(4),CareerRecordsStore.PlayerGame.class).evaluation();if(v.rating()!=null)ratings.computeIfAbsent(r.getString(1)+"|"+r.getString(2)+"|"+r.getString(3)+"|"+v.version(),k->new ArrayList<>()).add(v.rating());},args.toArray());
        for(var row:stats){String prefix=row.get("playerid")+"|"+row.get("team")+"|"+row.get("competition")+"|";row.put("ratings",ratings.entrySet().stream().filter(e->e.getKey().startsWith(prefix)).map(e->Map.of("version",e.getKey().substring(prefix.length()),"count",e.getValue().size(),"mean",e.getValue().stream().mapToDouble(java.math.BigDecimal::doubleValue).average().orElseThrow())).toList());}
        var awardArgs=new ArrayList<Object>(List.of(career,year));awardArgs.addAll(ids);String af="a.career_id=? AND a.season_year=? AND c.player_id IN ("+marks+") AND c.winner=TRUE AND a.status='FINALIZED'";if(competition!=null&&!competition.isBlank()){af+=" AND a.scope_id=?";awardArgs.add(CareerAwardPolicy.scope(competition));}
        var awards=db.query("SELECT c.player_id,a.award_json,a.award_hash FROM career_record_award a JOIN career_record_award_candidate c ON c.instance_id=a.instance_id WHERE "+af+" ORDER BY a.instance_id",(r,n)->{if(!hash(r.getString(2)).equals(r.getString(3)))throw new IllegalStateException("AWARD_INTEGRITY");var a=read(r.getString(2),CareerAwardsStore.Award.class);return a.analysisBadge()?null:Map.<String,Object>of("playerId",r.getString(1),"instanceId",a.instanceId(),"name",a.name(),"scope",a.scope());},awardArgs.toArray()).stream().filter(Objects::nonNull).toList();
        var growth=new TreeMap<String,List<Map<String,Object>>>();for(String id:ids)growth.put(id,records.observations(career,year,"PLAYER",id,CareerRecordsStore.revision(db,career),false,null,0).growth());
        return new Comparison(career,c.year(),c.date(),c.stamp(),year,competition,players,stats,champions,awards,growth,c.finance());
    });}
}
