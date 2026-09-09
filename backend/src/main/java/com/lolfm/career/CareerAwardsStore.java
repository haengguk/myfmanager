package com.lolfm.career;

import static com.lolfm.career.CareerRosterStore.*;
import static com.lolfm.career.CareerPerformancePolicy.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Small cutoff aggregates, evaluated once on closure; never on a record GET. */
public final class CareerAwardsStore {
    public record Candidate(String playerId,String name,String team,com.lolfm.domain.Position position,int sets,BigDecimal participation,
                            String eligibility,String eligibilityReason,BigDecimal score,BigDecimal combat,Period period,String tieKey) {}
    public record Slot(int tier,com.lolfm.domain.Position position,String playerId,String reason) {}
    public record Entitlement(String id,String playerId,String previousPrizeId,String currency,Long amount,String fxPolicy,String rate,Long krw,
                              String status,Integer minimumDelayDays,LocalDate exactPaymentDate) {}
    public record Award(String instanceId,String definitionId,String name,String scope,String occurrence,int gameYear,int referenceYear,
                        String status,String reason,String policyVersion,String sourceHash,long cutoffRevision,LocalDate cutoffDate,List<String> inputRecords,
                        String inputHash,List<String> aliases,boolean analysisBadge,List<Candidate> candidates,List<Slot> slots,List<Entitlement> entitlements,
                        String tiePolicy,String publicationStatus,LocalDate publishedOn,List<String> stateHistory) {}
    static void match(JdbcTemplate db,CareerRecordsStore.Series s) {
        long revision=CareerRecordsStore.revision(db,s.careerId());
        boolean complete=complete(List.of(s));
        for(var g:s.games()) {
            for(String team:List.of(s.firstTeam(),s.secondTeam())) {
                String id="TEAM_STANDOUT";String occurrence=s.origin()+"|GAME|"+g.gameNumber()+"|"+team;
                var candidates=gameCandidates(db,s,g,id,occurrence,0).stream().filter(p->p.team().equals(team)).toList();
                save(db,s.careerId(),s.seasonYear(),id,"팀 수훈선수",s.competition(),occurrence,revision,s.date(),List.of(s),List.of("ANALYSIS"),true,candidates,complete&&g.players().size()==10,null);
            }
            if(s.games().size()>1)save(db,s.careerId(),s.seasonYear(),"CAREER_POG","Career POG",s.competition(),s.origin()+"|GAME|"+g.gameNumber(),revision,s.date(),List.of(s),List.of("GAME"),false,gameCandidates(db,s,g,"CAREER_POG",s.origin()+"|GAME|"+g.gameNumber(),WIN_BONUS),complete,null);
        }
        var adopted=CareerAwardPolicy.DEFINITIONS.stream().filter(d->d.status().equals("ACTIVE_GAME_POLICY")&&d.category().equals("MATCH_MVP")&&d.scope().equals(CareerAwardPolicy.scope(s.competition()))&&CareerAwardPolicy.regular(s.competition(),s.stage())).findFirst();
        String definition=adopted.map(CareerAwardPolicy.Definition::id).orElse("CAREER_POM");String name=adopted.map(CareerAwardPolicy.Definition::name).orElse("Career POM");
        save(db,s.careerId(),s.seasonYear(),definition,name,s.competition(),s.origin(),revision,s.date(),List.of(s),s.games().size()==1?List.of("GAME","SERIES","CAREER_POG","CAREER_POM"):List.of("SERIES","CAREER_POM"),false,seriesCandidates(db,s,definition,s.origin(),false),complete,adopted.orElse(null));
    }
    static boolean complete(List<CareerRecordsStore.Series> series){return !series.isEmpty()&&series.stream().allMatch(s->s.coverage().equals("COMPLETE")&&s.games().stream().allMatch(g->g.players().size()==10&&g.players().stream().allMatch(p->p.evaluation().status().equals("COMPLETE")&&CareerPerformancePolicy.VERSION.equals(p.evaluation().version()))));}
    static long seed(JdbcTemplate db,String career){return db.queryForObject("SELECT career_root_seed FROM career_save WHERE career_id=?",Long.class,career);}
    private static List<Candidate> gameCandidates(JdbcTemplate db,CareerRecordsStore.Series s,CareerRecordsStore.Game game,String definition,String occurrence,int bonus) {
        String instance=CareerAwardPolicy.instance(s.careerId(),s.seasonYear(),definition,s.competition(),occurrence);long seed=seed(db,s.careerId());
        return game.players().stream().filter(p->p.evaluation().rating()!=null).map(p->new Candidate(p.playerId(),p.name(),p.team(),p.statistics().position(),1,BigDecimal.ONE,"TRUE","실제 세트 출전자",p.evaluation().rating().add(decimal(p.won()?bonus:0)),p.evaluation().combat(),null,CareerAwardPolicy.tie(seed,instance,p.playerId()))).toList();
    }
    static List<Candidate> seriesCandidates(JdbcTemplate db,CareerRecordsStore.Series s,String definition,String occurrence,boolean winnerOnly) {
        var grouped=new TreeMap<String,List<CareerRecordsStore.PlayerGame>>();for(var g:s.games())for(var p:g.players())grouped.computeIfAbsent(p.playerId(),k->new ArrayList<>()).add(p);
        var result=new ArrayList<Candidate>();String instance=CareerAwardPolicy.instance(s.careerId(),s.seasonYear(),definition,s.competition(),occurrence);long seed=seed(db,s.careerId());
        for(var list:grouped.values()) {
            var last=list.getLast();if(winnerOnly&&!last.team().equals(s.winner())||list.stream().anyMatch(p->p.evaluation().rating()==null))continue;
            var score=series(list.stream().map(p->p.evaluation().rating()).toList(),s.games().size());
            if(!winnerOnly&&last.team().equals(s.winner()))score=score.add(decimal(WIN_BONUS));
            var combat=div(list.stream().map(p->p.evaluation().combat()).reduce(BigDecimal.ZERO,BigDecimal::add),decimal(list.size()));
            result.add(new Candidate(last.playerId(),last.name(),last.team(),last.statistics().position(),list.size(),div(decimal(list.size()),decimal(s.games().size())),"TRUE",winnerOnly?"우승 팀 결승 실제 출전자 · 게임 정책":"실제 출전자 · 승리 팀 선정 점수 +3",score,combat,null,CareerAwardPolicy.tie(seed,instance,last.playerId())));
        }return result;
    }
    static Comparator<Candidate> order(){return Comparator.comparing(Candidate::score,Comparator.reverseOrder()).thenComparing(Candidate::combat,Comparator.reverseOrder()).thenComparing(Candidate::participation,Comparator.reverseOrder()).thenComparing(Candidate::tieKey).thenComparing(Candidate::playerId);}
    static List<Slot> slots(List<Candidate> candidates,List<Integer> tiers,boolean joint) {
        var eligible=candidates.stream().filter(c->"TRUE".equals(c.eligibility())).sorted(order()).toList();var result=new ArrayList<Slot>();
        if(!tiers.isEmpty())for(var position:CareerAwardPolicy.positions()) {
            var players=eligible.stream().filter(p->p.position()==position).toList();for(int i=0;i<tiers.size();i++)result.add(new Slot(tiers.get(i),position,i<players.size()?players.get(i).playerId():null,i<players.size()?reason(players,i):"자격을 충족하는 후보 부족"));
        }else if(eligible.isEmpty())result.add(new Slot(1,null,null,"자격을 충족하는 후보 부족"));
        else for(var p:eligible){if(!result.isEmpty()&&(!joint||p.score().compareTo(eligible.getFirst().score())!=0))break;result.add(new Slot(1,null,p.playerId(),joint?"JOINT_MAXIMUM":reason(eligible,0)));}
        return result;
    }
    static String reason(List<Candidate> ordered,int index) {
        if(index+1>=ordered.size())return "남은 자격 후보 중 단독 선정";
        var a=ordered.get(index);var b=ordered.get(index+1);
        if(a.score().compareTo(b.score())!=0)return "선정 점수 우위";
        if(a.combat().compareTo(b.combat())!=0)return "동점 · 같은 범위 전투/보정 평균 우위";
        if(a.participation().compareTo(b.participation())!=0)return "동점 · 실제 참여 우위";
        return a.tieKey().equals(b.tieKey())?"해시 충돌 · canonical 선수 ID 순서":"동점 · 결정적 시상 해시로 결정";
    }
    static void save(JdbcTemplate db,String career,int year,String definition,String name,String scope,String occurrence,long revision,LocalDate date,List<CareerRecordsStore.Series> input,List<String> aliases,boolean badge,List<Candidate> candidates,boolean complete,CareerAwardPolicy.Definition policy) {
        String id=CareerAwardPolicy.instance(career,year,definition,scope,occurrence);
        if(db.queryForObject("SELECT COUNT(*) FROM career_record_award WHERE instance_id=?",Integer.class,id)>0)return;
        var ordered=candidates.stream().sorted(order()).toList();boolean unknown=candidates.stream().anyMatch(c->"UNKNOWN".equals(c.eligibility()));
        var slots=complete&&!unknown?slots(ordered,policy==null?List.of():policy.tiers(),policy!=null&&Set.of("MOST_MATCH_MVP","MOST_FEARLESS").contains(policy.category())):List.<Slot>of();
        String status=complete&&!unknown?"FINALIZED":"INPUT_INCOMPLETE";
        var inputs=input.stream().map(s->s.recordId()+":"+s.receiptHash()).sorted().toList();var rights=new ArrayList<Entitlement>();
        if(policy!=null&&policy.prizeLink()!=null&&!policy.prizeLink().isNull()) {
            var link=policy.prizeLink();var market=CareerMarketStore.load(db,career);var finance=market==null?null:market.state().finance();String currency=link.path("currency").asText();long amount=link.path("amountPerRecipient").asLong();String rate="KRW".equals(currency)?"1":finance==null?null:finance.fx().get(currency);
            for(var slot:slots)if(slot.playerId()!=null)rights.add(new Entitlement(hash(write(List.of(id,slot.playerId(),link.path("previousPrizeAwardId").asText()))),slot.playerId(),link.path("previousPrizeAwardId").asText(),currency,amount,finance==null?null:finance.fxPolicyVersion(),rate,rate==null?null:new BigDecimal(rate).multiply(decimal(amount)).setScale(0,java.math.RoundingMode.HALF_UP).longValueExact(),"UNPAID","EWC_LOL_MVP".equals(definition)?35:null,null));
        }
        var award=new Award(id,definition,name,scope,occurrence,year,2026,status,complete?unknown?"후보의 과거 등록·신인 후보 이력이 확인되지 않아 시상을 확정하지 않았습니다.":"실제 성적과 게임 선정 정책으로 확정했습니다.":"평가 범위에 수집되지 않은 경기 성적이 있습니다.",CareerAwardPolicy.VERSION,CareerAwardPolicy.SOURCE_HASH,revision,date,inputs,hash(write(inputs)),aliases,badge,ordered,slots,rights,"선정 점수 → 같은 범위 전투 점수/보정 평균 → 실제 참여 → 저장 seed와 시상·선수 identity의 결정적 해시",status.equals("FINALIZED")?"PUBLISHED":"WITHHELD",status.equals("FINALIZED")?CareerMarketStore.date(db,career):null,List.of("COLLECTING","INPUT_SEALED",status));
        if(policy!=null&&policy.category().equals("ROOKIE")&&status.equals("FINALIZED"))CareerHistoryStore.candidateHistory(db,career,year,candidates,date);
        String json=write(award);db.update("INSERT INTO career_record_award VALUES (?,?,?,?,?,?,?,?,?,?)",id,career,year,definition,scope,occurrence,revision,status,json,hash(json));
        var winners=new HashSet<String>();slots.forEach(slot->{if(slot.playerId()!=null)winners.add(slot.playerId());});
        for(var candidate:ordered)db.update("INSERT INTO career_record_award_candidate VALUES (?,?,?,?)",id,candidate.playerId(),candidate.team(),winners.contains(candidate.playerId()));
        for(String record:input.stream().map(CareerRecordsStore.Series::recordId).distinct().toList())db.update("INSERT INTO career_record_award_input VALUES (?,?)",id,record);
    }
    private CareerAwardsStore() {}
}
