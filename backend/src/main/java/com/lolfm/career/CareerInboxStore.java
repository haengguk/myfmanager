package com.lolfm.career;

import static com.lolfm.career.CareerRosterStore.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Small factual adjunct written in the source transaction. Reading never invokes this writer. */
public final class CareerInboxStore {
    public record Link(String panel,String playerId,String sourceId,String competition,Integer seasonYear,String seriesId,List<String> positions) {
        static Link of(String panel,String player,String source,String competition,int year){return new Link(panel,player,source,competition,year,null,List.of());}
    }
    public record Item(String sourceKey,String kind,LocalDate date,String title,String summary,String team,String playerId,String competition,boolean development,Link link,JsonNode facts) {}
    static void add(JdbcTemplate db,String career,int year,Item item) {
        db.update("INSERT INTO career_inbox_item(career_id,season_year,source_key,kind,game_date,development,item_json) SELECT ?,?,?,?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM career_inbox_item WHERE career_id=? AND source_key=?)",career,year,item.sourceKey(),item.kind(),item.date(),item.development(),write(item),career,item.sourceKey());
    }
    static String managed(JdbcTemplate db,String career){return "LCK:"+db.queryForObject("SELECT managed_team_code FROM career_save WHERE career_id=?",String.class,career);}
    static JsonNode facts(Object value){return read(write(value),JsonNode.class);}
    static void series(JdbcTemplate db,CareerRecordsStore.Series s) {
        String team=managed(db,s.careerId());if(!CareerRecordsQuery.teams(team,true).contains(s.firstTeam())&&!CareerRecordsQuery.teams(team,true).contains(s.secondTeam()))return;
        add(db,s.careerId(),s.seasonYear(),new Item("SERIES:"+s.recordId(),"SERIES_RESULT",s.date(),s.firstTeam()+" · "+s.secondTeam()+" 경기 결과","승리 "+s.winner()+" · "+s.games().size()+"세트 · 경기상은 결과 상세에서 확인",team,null,s.competition(),s.competition().equals("LCK_CL"),Link.of("RECORDS",null,s.recordId(),s.competition(),s.seasonYear()),facts(Map.of("recordId",s.recordId(),"winner",s.winner(),"games",s.games().size()))));
    }
    static void award(JdbcTemplate db,String career,int year,CareerAwardsStore.Award a) {
        if(!a.status().equals("FINALIZED")||a.analysisBadge()||a.aliases().stream().anyMatch(v->Set.of("GAME","SERIES").contains(v)))return;
        String team=managed(db,career);var winners=a.slots().stream().map(CareerAwardsStore.Slot::playerId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        var own=a.candidates().stream().filter(c->winners.contains(c.playerId())&&CareerRecordsQuery.teams(team,true).contains(c.team())).toList();if(own.isEmpty())return;
        add(db,career,year,new Item("AWARD:"+a.instanceId(),"AWARD",a.publishedOn()==null?a.cutoffDate():a.publishedOn(),a.name(),String.join(", ",own.stream().map(CareerAwardsStore.Candidate::name).toList()),team,null,a.scope(),a.scope().equals("LCK_CL"),Link.of("AWARDS",null,a.instanceId(),a.scope(),year),facts(Map.of("instanceId",a.instanceId(),"name",a.name(),"status",a.status()))));
    }
    static void observation(JdbcTemplate db,String career,int year,String key,LocalDate date,JsonNode v) {
        if(!(key.startsWith("EVENT:")||key.startsWith("PLACEMENT:")||key.startsWith("TITLE:")))return;
        String team=managed(db,career),owner=v.hasNonNull("team")?v.path("team").asText():v.path("winner").asText();
        if(!CareerRecordsQuery.teams(team,true).contains(owner))return;
        String kind=key.startsWith("EVENT:")?"OPERATIONS":key.startsWith("TITLE:")?"TITLE":"PLACEMENT";
        String player=v.hasNonNull("playerId")?v.path("playerId").asText():null,competition=v.path("competition").asText(null);
        String title=kind.equals("OPERATIONS")?operatingTitle(v.path("kind").asText())+" · "+v.path("name").asText(""):kind.equals("TITLE")?competition+" 우승":competition+" 순위 확정";
        String summary=kind.equals("PLACEMENT")?v.path("from").asText()+"~"+v.path("through").asText()+"위":v.path("reason").asText("");
        String panel=kind.equals("OPERATIONS")?(Set.of("TRADE_COMPLETED","RETIREMENT_TRADE_CANCELLED").contains(v.path("kind").asText())?"TRADE":v.path("kind").asText().startsWith("RETIREMENT")?"LIFECYCLE":"MARKET"):"RECORDS";
        String reference=v.path("referenceId").asText(null);
        if(v.path("kind").asText().equals("LOAN_RETURNED")&&reference!=null){var market=CareerMarketStore.load(db,career);var loan=market==null||market.state().management()==null?null:market.state().management().loans().get(reference);if(loan!=null){panel="TRADE";reference=loan.tradeId();}}
        add(db,career,year,new Item("OBS:"+year+":"+key,kind,date,title,summary,owner,player,competition,owner.endsWith(":CL"),Link.of(panel,player,reference,competition,year),v));
    }
    static void market(JdbcTemplate db,String career,int year,CareerMarketState before,CareerMarketEngine engine,State oldRoster) {
        String managed=engine.managed;var after=engine.state();var prior=new HashSet<String>();before.events().forEach(e->prior.add(e.eventId()));
        for(var e:after.events())if(!prior.contains(e.eventId())&&e.playerId()!=null) {
            var member=oldRoster.members().get(e.playerId());var trade=after.management()==null||e.referenceId()==null?null:after.management().trades().get(e.referenceId());
            var loan=after.management()==null||e.referenceId()==null?null:after.management().loans().get(e.referenceId());
            boolean related=loan!=null&&(managed.equals(loan.parentTeam())||managed.equals(loan.borrowingTeam()))||e.kind().equals("RETIREMENT_EFFECTIVE")&&member!=null&&managed.equals(member.ownerTeam())||trade!=null&&(managed.equals(trade.terms().seller())||managed.equals(trade.terms().buyer()));
            if(related&&!managed.equals(e.team())){
                var p=engine.directory.players().get(e.playerId());var v=new CareerHistoryStore.Operating(e.eventId(),e.date(),e.kind(),e.playerId(),p==null?e.playerId():p.nickname(),e.team(),e.referenceId(),e.reason());
                add(db,career,year,new Item("OBS:"+year+":EVENT:"+hash(e.eventId()),"OPERATIONS",e.date(),operatingTitle(e.kind())+" · "+v.name(),e.reason(),e.team(),e.playerId(),null,false,Link.of(trade==null&&loan==null?"MARKET":"TRADE",e.playerId(),loan==null?e.referenceId():loan.tradeId(),null,year),facts(v)));
            }
        }
        for(var o:after.offers().values())if(managed.equals(o.team())&&o.status()==CareerMarketState.OfferStatus.COUNTER&&!o.equals(before.offers().get(o.offerId()))) {
            String name=engine.directory.players().get(o.playerId()).nickname();
            add(db,career,year,new Item("OFFER:"+o.offerId()+":"+o.revision(),"CONTRACT_RESPONSE",after.processedThrough(),name+" 계약 역제안","요청 연봉 "+o.requestedSalary()+"원 · 읽음과 계약 응답은 별개입니다.",managed,o.playerId(),null,false,Link.of("MARKET",o.playerId(),o.offerId(),null,year),facts(o)));
        }
        if(after.management()!=null)for(var trade:after.management().trades().values())if((managed.equals(trade.terms().seller())||managed.equals(trade.terms().buyer()))&&!trade.equals(before.management()==null?null:before.management().trades().get(trade.tradeId()))) {
            String id=trade.terms().playerId();var player=engine.directory.players().get(id);
            add(db,career,year,new Item("TRADE:"+trade.tradeId()+":"+hash(write(trade)),"TRADE",after.processedThrough(),(player==null?id:player.nickname())+" · "+trade.terms().kind(),trade.reason(),managed,id,null,false,Link.of("TRADE",id,trade.tradeId(),null,year),facts(trade)));
        }
        if(after.management()!=null)for(var promise:after.management().promises().values()) {
            var old=before.management()==null?null:before.management().promises().get(promise.promiseId());
            if(managed.equals(promise.team())&&old!=null&&(old.satisfaction()!=promise.satisfaction()||!old.status().equals(promise.status())&&!Set.of("NO_NEW_SERIES","INSUFFICIENT_SERIES","OBSERVATION_PENDING").contains(promise.status())))
                add(db,career,year,new Item("PROMISE:"+promise.promiseId()+":"+promise.lastEvaluation()+":"+promise.status(),"PROMISE",promise.lastEvaluation(),engine.directory.players().get(promise.playerId()).nickname()+" 출전 약속 변화",promise.reason()+" · 만족도 "+old.satisfaction()+" → "+promise.satisfaction(),managed,promise.playerId(),null,false,Link.of("MARKET",promise.playerId(),null,null,year),facts(promise)));
        }
        var oldLedger=new HashSet<String>();before.ledger().forEach(e->oldLedger.add(e.entryId()));
        for(var entry:after.ledger())if(managed.equals(entry.team())&&!oldLedger.contains(entry.entryId())&&(entry.kind().contains("PRIZE")||entry.kind().contains("ARREARS")))
            add(db,career,year,new Item("LEDGER:"+entry.entryId(),"FINANCE",entry.date(),entry.kind(),entry.amount()+"원 · 실제 구단 원장 반영",managed,null,null,false,Link.of("FINANCE",null,entry.entryId(),null,year),facts(entry)));
        if(after.finance()!=null)for(var target:after.finance().targets().values())if(managed.equals(target.team())&&target.evaluatedOn()!=null&&(before.finance()==null||!target.equals(before.finance().targets().get(target.team()+"|"+target.seasonYear()))))
            add(db,career,target.seasonYear(),new Item("TARGET:"+target.seasonYear(),"SEASON_TARGET",target.evaluatedOn(),target.seasonYear()+" 시즌 목표 결과",target.sportingStatus()+" · "+target.financeStatus(),managed,null,null,false,Link.of("FINANCE",null,null,null,target.seasonYear()),facts(target)));
    }
    static void growth(JdbcTemplate db,String career,int year,String kind,LocalDate date,CareerDevelopmentState state,State roster) {
        if(!kind.startsWith("MONTH:")&&!kind.equals("CLOSING_FINAL"))return;String team=managed(db,career);var people=new TreeMap<String,Object>();
        roster.members().forEach((id,m)->{if(team.equals(m.ownerTeam())&&state.players().containsKey(id))people.put(id,state.players().get(id));});
        String type=kind.equals("CLOSING_FINAL")?"SEASON_RECAP":"MONTHLY_GROWTH";
        var data=new TreeMap<String,Object>();data.put("seasonYear",year);data.put("recordRevision",CareerRecordsStore.revision(db,career));data.put("final",kind.equals("CLOSING_FINAL"));
        var opening=db.query("SELECT observation_json,observation_hash FROM career_record_observation WHERE career_id=? AND season_year=? AND observation_key='OPENING'",(r,n)->{if(!hash(r.getString(1)).equals(r.getString(2)))throw new IllegalStateException("OBSERVATION_INTEGRITY");return read(r.getString(1),JsonNode.class);},career,year);
        var directory=baseDirectory(db,career);var changes=new ArrayList<Map<String,Object>>();
        for(String id:people.keySet()){var row=new TreeMap<String,Object>();row.put("playerId",id);row.put("name",directory.players().get(id).nickname());long total=state.players().get(id).internalRatings().values().stream().mapToLong(Integer::longValue).sum();row.put("total",total);
            JsonNode start=opening.isEmpty()?null:opening.getFirst().path("players").path(id).get("internalRatings");Long delta=null;if(start!=null){long previous=0;for(var value:start)previous+=value.asLong();delta=total-previous;}row.put("seasonDelta",delta);changes.add(row);}
        data.put("growth",changes);
        if(kind.equals("CLOSING_FINAL")){var market=CareerMarketStore.load(db,career);if(market!=null&&market.state().finance()!=null){var f=market.state().finance();data.put("prizes",f.awards().values().stream().filter(a->a.team().equals(team)&&a.seasonYear()==year).toList());data.put("targets",f.targets().values().stream().filter(t->t.team().equals(team)&&t.seasonYear()==year).toList());}}
        add(db,career,year,new Item(type+":"+year+":"+kind,type,date,year+(type.equals("SEASON_RECAP")?" 시즌 최종 결산":" 월별 성장 관측"),people.size()+"명 · 당시 소속 기준. 전체 시즌 성장과 구단 재직 기간 성장은 구분합니다.",team,null,null,false,Link.of(type.equals("SEASON_RECAP")?"RECAP":"TRAINING",null,null,null,year),facts(data)));
    }
    static void training(JdbcTemplate db,String career,CareerDevelopmentState before,CareerDevelopmentState after) {
        if(before.teamPlans().values().stream().noneMatch(s->s.pendingOn()!=null)&&before.players().values().stream().noneMatch(p->p.override()!=null&&p.override().pendingOn()!=null))return;
        String managed=managed(db,career);int year=activeYear(db,career);var roster=CareerRosterStore.saved(db,career,year).state();
        before.teamPlans().forEach((team,s)->{if(team.equals(managed)||team.equals(managed+"|DEVELOPMENT"))training(db,career,year,managed,null,s,after.teamPlans().get(team));});
        before.players().forEach((id,p)->{var member=roster.members().get(id);if(member!=null&&managed.equals(member.ownerTeam())&&after.players().containsKey(id))training(db,career,year,managed,id,p.override(),after.players().get(id).override());});
    }
    private static void training(JdbcTemplate db,String career,int year,String team,String player,CareerDevelopmentState.Schedule before,CareerDevelopmentState.Schedule after) {
        if(before==null||before.pendingOn()==null||after!=null&&after.pendingOn()!=null)return;
        add(db,career,year,new Item("TRAINING:"+Objects.toString(player,before.team())+":"+before.pendingOn()+":"+hash(write(before)),"TRAINING",before.pendingOn(),"예약 훈련 적용",player==null?"구단 기본 훈련이 적용되었습니다.":"선수별 예약 훈련이 적용되었습니다.",team,player,null,false,Link.of("TRAINING",player,null,null,year),facts(before)));
    }
    private static String operatingTitle(String kind) {return switch(kind){
        case "OFFER_SUBMITTED" -> "계약 제안 제출";case "OFFER_REJECTED" -> "계약 제안 거절";case "OFFER_WITHDRAWN" -> "계약 제안 철회";
        case "COUNTER_REQUESTED" -> "계약 조건 수정 요청";case "CONTRACT_SIGNED" -> "계약 체결";case "CONTRACT_ACTIVATED" -> "입단";
        case "CONTRACT_RELEASED" -> "방출";case "CONTRACT_EXPIRED" -> "계약 만료";case "EXPIRY_WARNING" -> "계약 만료 예고";
        case "TRADE_COMPLETED" -> "이적·임대 완료";case "LOAN_RETURNED" -> "임대 복귀";case "SALARY_ARREARS_RECORDED" -> "급여 미지급 발생";
        case "RETIREMENT_EFFECTIVE","CONTRACT_RETIRED" -> "은퇴 효력";default -> "구단 운영 변화";
    };}
    private CareerInboxStore() {}
}
