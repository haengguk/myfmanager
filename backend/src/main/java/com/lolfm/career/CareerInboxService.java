package com.lolfm.career;

import static com.lolfm.career.CareerRosterStore.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public final class CareerInboxService {
    private final JdbcTemplate db;private final TransactionTemplate read,writeTx;
    private final CareerApplicationService careers;private final CareerCalendarApplicationService calendar;private final CareerCalendarLeaguePort leagues;
    public CareerInboxService(JdbcTemplate db,PlatformTransactionManager manager,CareerApplicationService careers,CareerCalendarApplicationService calendar,CareerCalendarLeaguePort leagues){
        this.db=db;this.careers=careers;this.calendar=calendar;this.leagues=leagues;read=new TransactionTemplate(manager);read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);writeTx=new TransactionTemplate(manager);
    }
    public record Entry(long sequence,boolean read,CareerInboxStore.Item item,String currentStatus) {}
    public record Feed(String careerId,Integer seasonYear,String kind,boolean includeDevelopment,long asOf,long nextCursor,long unread,List<Entry> items,List<CareerDecisions.Decision> decisions,String collectionNote) {}
    public Feed feed(String career,Integer year,String kind,boolean development,Long upper,long cursor) {return read.execute(t->{
        var decisions=decisions(career);return page(career,year,kind,development,upper,cursor,decisions);
    });}
    List<CareerDecisions.Decision> decisions(String career) {
        var row=careers.get(career).career();var view=calendar.view(row);var provenance=view.fixtureOverlay().provenanceV2();var market=CareerMarketStore.load(db,career);
        var current=CareerDecisions.current(market==null?null:market.state(),"LCK:"+row.managedTeamCode(),view,leagues.load(provenance.leagueId(),provenance.seasonId()),calendar.registrationRepairWaits(row,view.state().seasonYear(),view.state().currentDate()));
        if(current.stream().noneMatch(d->d.link().playerId()!=null))return current;
        var players=baseDirectory(db,career).players();
        return current.stream().map(d->{var player=d.link().playerId()==null?null:players.get(d.link().playerId());
            return player==null?d:new CareerDecisions.Decision(d.id(),d.revision(),d.type(),d.status(),d.responsibility(),d.blocksProgress(),d.date(),d.deadline(),player.nickname()+" · "+d.title(),d.summary(),d.link(),d.stop());
        }).toList();
    }
    Feed page(String career,Integer year,String kind,boolean development,Long upper,long cursor,List<CareerDecisions.Decision> decisions) {
        require(career);if(cursor<0||upper!=null&&upper<0)throw CareerException.invalid("cursor","소식 조회 기준이 올바르지 않습니다.");
        long maximum=db.queryForObject("SELECT COALESCE(MAX(sequence),0) FROM career_inbox_item WHERE career_id=?",Long.class,career);long asOf=upper==null?maximum:Math.min(maximum,upper);
        var args=new ArrayList<Object>(List.of(career,asOf));String filter=filter(year,kind,development,args);
        long unread=db.queryForObject("SELECT COUNT(*) FROM career_inbox_item WHERE "+filter+" AND read_flag=FALSE",Long.class,args.toArray());
        args.add(cursor==0?asOf+1:cursor);
        var rows=db.query("SELECT sequence,read_flag,item_json FROM career_inbox_item WHERE "+filter+" AND sequence<? ORDER BY sequence DESC LIMIT 26",(r,n)->new Entry(r.getLong(1),r.getBoolean(2),read(r.getString(3),CareerInboxStore.Item.class),"INFORMATION"),args.toArray());
        return new Feed(career,year,kind,development,asOf,rows.size()>25?rows.get(24).sequence():-1,unread,rows.stream().limit(25).toList(),decisions,"도입 이후 원본 저장 경계에서 수집한 소식입니다. 도입 이전 일반 소식은 전체 복원되지 않았으며 기록 화면에서 보존 자료를 조회할 수 있습니다. 현재 처리 목록은 도입 시점과 관계없이 현재 원본 상태를 확인합니다.");
    }
    private String filter(Integer year,String kind,boolean development,List<Object> args){String q="career_id=? AND sequence<=?";if(year!=null){q+=" AND season_year=?";args.add(year);}if(kind!=null&&!kind.isBlank()){q+=" AND kind=?";args.add(kind);}if(!development)q+=" AND development=FALSE";return q;}
    public Entry detail(String career,long sequence){return read.execute(t->{
        require(career);var rows=db.query("SELECT read_flag,item_json FROM career_inbox_item WHERE career_id=? AND sequence=?",(r,n)->new Entry(sequence,r.getBoolean(1),read(r.getString(2),CareerInboxStore.Item.class),"INFORMATION"),career,sequence);if(rows.isEmpty())throw CareerException.notFound();var entry=rows.getFirst();
        String status="INFORMATION";var link=entry.item().link();
        if(link.sourceId()!=null&&Set.of("MARKET","TRADE").contains(link.panel())) {var saved=CareerMarketStore.load(db,career);
            if(saved!=null){var offer=saved.state().offers().get(link.sourceId());var trade=saved.state().management()==null?null:saved.state().management().trades().get(link.sourceId());
                if(offer!=null)status=offer.status()==CareerMarketState.OfferStatus.COUNTER?"OPEN":offer.status().name();
                else if(trade!=null)status=trade.status().name();else status="SOURCE_UNAVAILABLE";
            }
        }
        return new Entry(sequence,entry.read(),entry.item(),status);
    });}
    public record ReadRequest(Long sequence,Long through,Integer seasonYear,String kind,boolean includeDevelopment) {}
    public record ReadResult(String careerId,long changed) {}
    public ReadResult markRead(String career,ReadRequest request){return writeTx.execute(t->{
        require(career);if(request==null||(request.sequence()==null)==(request.through()==null)||request.sequence()!=null&&request.sequence()<1||request.through()!=null&&request.through()<0)throw CareerException.invalid("sequence","읽은 소식 또는 조회 상한을 지정하세요.");
        if(request.sequence()!=null){if(db.queryForObject("SELECT COUNT(*) FROM career_inbox_item WHERE career_id=? AND sequence=?",Integer.class,career,request.sequence())==0)throw CareerException.notFound();return new ReadResult(career,db.update("UPDATE career_inbox_item SET read_flag=TRUE WHERE career_id=? AND sequence=? AND read_flag=FALSE",career,request.sequence()));}
        var args=new ArrayList<Object>(List.of(career,request.through()));return new ReadResult(career,db.update("UPDATE career_inbox_item SET read_flag=TRUE WHERE "+filter(request.seasonYear(),request.kind(),request.includeDevelopment(),args)+" AND read_flag=FALSE",args.toArray()));
    });}
    private void require(String career){if(db.queryForObject("SELECT COUNT(*) FROM career_save WHERE career_id=?",Integer.class,career)==0)throw CareerException.notFound();}
}
