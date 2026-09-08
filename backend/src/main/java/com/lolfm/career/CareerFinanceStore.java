package com.lolfm.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.lolfm.career.CareerRosterStore.*;

/** All writes execute under the caller's Calendar lock and transaction. Queries never settle money. */
final class CareerFinanceStore {
    static final String MARKET_COMMAND="CAREER_MARKET_COMMAND_KRW_V1",TRADE_COMMAND="CAREER_TRADE_COMMAND_KRW_V1";
    private static final Set<String> MONEY=Set.of("annualSalary","signingBonus","annualBudget","cash","requestedSalary","fee","referenceValue","sellerDemand","buyerLimit","amount");
    static CareerMarketState convertLegacy(String json){var tree=read(json,JsonNode.class);convert(tree);return read(write(tree),CareerMarketState.class);}
    private static void convert(JsonNode node){
        if(node.isObject())for(var e:new ArrayList<>(node.properties())){if(MONEY.contains(e.getKey())&&!e.getValue().isNull()){
            if(!e.getValue().isIntegralNumber())throw new IllegalStateException("LEGACY_MONEY_TYPE");((ObjectNode)node).put(e.getKey(),CareerFinancePolicy.legacy(e.getValue().longValue()));
        }else convert(e.getValue());}else if(node.isArray())node.forEach(CareerFinanceStore::convert);
    }
    static void migrate(JdbcTemplate jdbc,String career,CareerMarketStore.Saved old){
        if(old.state().finance()!=null)return;
        String raw=jdbc.queryForObject("SELECT state_json FROM career_market_state WHERE career_id=?",String.class,career);
        jdbc.update("INSERT INTO career_finance_transition VALUES (?,?,?,?,?,?)",career,raw,hash(raw),old.state().processedThrough(),CareerFinancePolicy.VERSION,CareerFinanceReference.HASH);
        int year=activeYear(jdbc,career);var converted=new CareerMarketStore.Saved(old.revision(),convertLegacy(raw));
        var engine=CareerMarketStore.engine(jdbc,career,year,converted);
        var excluded=new TreeSet<>(jdbc.query("SELECT calendar_season_year,competition_id FROM career_competition_instance WHERE career_id=? AND lifecycle_status='COMPLETED'",(r,n)->r.getInt(1)+"|"+r.getString(2),career));
        engine.finance=new CareerFinanceEngine(engine,CareerFinanceReference.initialize(engine,year,true,excluded));
        engine.finance.initializeTargets(year,engine.state().processedThrough(),true);
        if(closed(jdbc,career,year))engine.finance.close(year,engine.state().processedThrough(),Map.of(),Map.of());
        CareerMarketStore.persist(jdbc,career,year,old,engine);
    }
    private static boolean closed(JdbcTemplate jdbc,String career,int year){return jdbc.queryForObject("SELECT COUNT(*) FROM career_market_season_close WHERE career_id=? AND season_year=?",Integer.class,career,year)>0;}
    /** Repair the interrupted legacy boundary without rewriting the already sealed season. */
    static boolean repairClosedLegacy(JdbcTemplate jdbc,String career,CareerMarketStore.Saved old){
        if(old==null||old.state().finance()==null||!old.state().finance().legacyTransition())return false;
        int year=activeYear(jdbc,career);var finance=old.state().finance();
        if(old.state().accounts().keySet().stream().noneMatch(team->finance.targets().containsKey(team+"|"+year)&&finance.targets().get(team+"|"+year).partial()&&!finance.approvals().containsKey(team+"|"+(year+1)))||!closed(jdbc,career,year))return false;
        var engine=CareerMarketStore.engine(jdbc,career,year,old);
        engine.finance.close(year,old.state().processedThrough(),Map.of(),Map.of());
        CareerMarketStore.persist(jdbc,career,year,old,engine);return true;
    }
    static void requirePolicy(CareerMarketState state,String schema,boolean trade){
        if(state.finance()!=null&&!(trade?TRADE_COMMAND:MARKET_COMMAND).equals(schema))throw CareerException.moneyPolicyRefresh();
        if(state.finance()==null&&(trade?TRADE_COMMAND:MARKET_COMMAND).equals(schema))throw CareerException.invalid("schemaVersion","원화 상태를 새로 조회한 뒤 제출해 주세요.");
    }
    record View(String policyVersion,String currency,int referenceSeason,String scenario,String sourceHash,String fxPolicyVersion,Map<String,String> fx,
        LocalDate introducedOn,boolean legacyTransition,CareerFinanceState.Team reference,CareerFinanceState.Approval currentFunding,
        long operatingArrears,List<CareerFinanceState.Award> prizes,List<CareerFinanceState.Target> targets,List<CareerFinanceState.Approval> approvals,Map<String,String> heldPrizes,String policyExplanation){}
    static View view(CareerMarketEngine m,LocalDate date){
        if(m.finance==null)return null;var f=m.finance.state();String team=m.managed;
        return new View(f.policyVersion(),f.currency(),f.referenceSeason(),f.scenario(),f.sourceHash(),f.fxPolicyVersion(),f.fx(),f.introducedOn(),f.legacyTransition(),f.teams().get(team),m.finance.approval(team,date),f.operatingArrears().getOrDefault(team,0L),
            f.awards().values().stream().filter(a->a.team().equals(team)).sorted(Comparator.comparing(CareerFinanceState.Award::recognizedOn).thenComparing(CareerFinanceState.Award::id)).toList(),
            f.targets().values().stream().filter(t->t.team().equals(team)).sorted(Comparator.comparingInt(CareerFinanceState.Target::seasonYear)).toList(),
            f.approvals().values().stream().filter(a->a.team().equals(team)).sorted(Comparator.comparingInt(CareerFinanceState.Approval::seasonYear)).toList(),f.heldPrizes(),
            "2026 base 추정 자료·고정 게임 환율. 구단 지원 70%/가상 후원 30%, 팀 상금 구단 보유 100%는 게임 설정입니다. 미래 시즌은 동일 기준표 투영이며 실제 계약·세율·재무 공시가 아닙니다.");
    }
    static Map<String,CareerFinanceEngine.Rank> ranks(Map<String,Integer> source){
        var ranks=new TreeMap<String,CareerFinanceEngine.Rank>();source.forEach((team,rank)->ranks.put(team,new CareerFinanceEngine.Rank(rank,rank+(int)source.values().stream().filter(r->r.equals(rank)).count()-1)));return ranks;
    }
    static void recognize(CareerCompetitionRelationalStore store,String career,int year){recognize(store,career,year,null);}
    static void recognize(CareerCompetitionRelationalStore store,String career,int year,String completedCompetition){
        if(completedCompetition!=null){
            if(!CareerFinancePolicy.EVENT_IDS.containsKey(completedCompetition))return;
            if(store.jdbc.queryForObject("SELECT COUNT(*) FROM career_competition_instance WHERE career_id=? AND calendar_season_year=? AND competition_id=? AND lifecycle_status='COMPLETED'",Integer.class,career,year,completedCompetition)==0)return;
        }
        var old=CareerMarketStore.load(store.jdbc,career);if(old==null||old.state().finance()==null)return;
        var m=CareerMarketStore.engine(store.jdbc,career,year,old);var before=write(m.finance.state());LocalDate date=CareerMarketStore.date(store.jdbc,career);
        for(String competition:completedCompetition==null?CareerFinancePolicy.EVENT_IDS.keySet():Set.of(completedCompetition)){
            var statuses=store.jdbc.query("SELECT lifecycle_status FROM career_competition_instance WHERE career_id=? AND calendar_season_year=? AND competition_id=?",(r,n)->r.getString(1),career,year,competition);
            if(statuses.isEmpty()||!statuses.getFirst().equals("COMPLETED"))continue;
            var ranks=new TreeMap<String,CareerFinanceEngine.Rank>();String evidence;
            if(CareerInternationalRules.COMPETITIONS.contains(competition)){
                var states=store.jdbc.query("SELECT state_json,state_hash FROM career_international_state WHERE career_id=? AND calendar_season_year=? AND competition_id=?",(r,n)->{if(!hash(r.getString(1)).equals(r.getString(2)))throw new IllegalStateException("INTERNATIONAL_FINANCE_INTEGRITY");return read(r.getString(1),CareerInternationalState.class);},career,year,competition);
                if(states.isEmpty())continue;var s=states.getFirst();if(!s.plan().complete())continue;
                if(s.plan().placements().keySet().equals(s.rosters().teams().keySet()))ranks.putAll(ranks(s.plan().placements()));evidence=hash(write(s.plan()));
            }else if(CareerOverseasRules.isOverseas(competition)){
                var state=CareerOverseasStore.load(store.jdbc,career,year,CareerOverseasRules.Event.valueOf(competition));
                if(state==null||!state.plan().complete())continue;
                ranks.putAll(ranks(state.plan().placements()));evidence=hash(write(state));
            }else if(competition.equals("LCK_PLAYOFFS")){
                var finalRank=store.finalRanking(career,year);if(finalRank==null)continue;
                finalRank.ranking().forEach(r->ranks.put(r.teamCode(),new CareerFinanceEngine.Rank(r.seed(),r.seed())));evidence=finalRank.stateHash();
            }else{
                var outcomes=new TreeMap<String,CareerDomesticTiebreak.Outcome>();
                store.jdbc.query("SELECT f.match_id,f.winner_team_code,f.loser_team_code FROM career_competition_fixture f JOIN career_competition_application a ON a.career_id=f.career_id AND a.calendar_season_year=f.calendar_season_year AND a.competition_id=f.competition_id AND a.match_id=f.match_id WHERE f.career_id=? AND f.calendar_season_year=? AND f.competition_id=? AND f.lifecycle_status='COMPLETED'",(org.springframework.jdbc.core.RowCallbackHandler)r->outcomes.put(r.getString(1),new CareerDomesticTiebreak.Outcome(r.getString(2),r.getString(3))),career,year,competition);
                if(competition.equals("LCK_CL")){
                    var order=CareerClStore.load(store.jdbc,career,year).ranking();
                    if(order.size()==10&&outcomes.keySet().containsAll(List.of("CL_FINAL","CL_SF_1","CL_SF_2","CL_QF_1","CL_QF_2"))){
                        for(int i=6;i<10;i++)ranks.put(order.get(i),new CareerFinanceEngine.Rank(i+1,i+1));
                        for(String id:List.of("CL_QF_1","CL_QF_2"))ranks.put(outcomes.get(id).loser(),new CareerFinanceEngine.Rank(5,6));
                        for(String id:List.of("CL_SF_1","CL_SF_2"))ranks.put(outcomes.get(id).loser(),new CareerFinanceEngine.Rank(3,4));
                        ranks.put(outcomes.get("CL_FINAL").winner(),new CareerFinanceEngine.Rank(1,1));ranks.put(outcomes.get("CL_FINAL").loser(),new CareerFinanceEngine.Rank(2,2));
                    }
                }else{
                    // Cup's zero-prize classification retains broad unranked placement bounds, never regular seeds.
                    var participants=new TreeSet<String>();outcomes.values().forEach(o->{participants.add(o.winner());participants.add(o.loser());});
                    if(participants.size()==10)participants.forEach(t->ranks.put(t,new CareerFinanceEngine.Rank(1,10)));
                }
                evidence=hash(write(outcomes));
            }
            m.finance.recognize(year,competition,ranks,date,evidence);
        }
        if(!before.equals(write(m.finance.state())))CareerMarketStore.persist(store.jdbc,career,year,old,m);
    }
    static void close(CareerCompetitionRelationalStore store,String career,int year,LocalDate date){
        recognize(store,career,year);var old=CareerMarketStore.load(store.jdbc,career);if(old==null||old.state().finance()==null)return;
        var m=CareerMarketStore.engine(store.jdbc,career,year,old);var domestic=new TreeMap<String,Integer>();var worlds=new TreeMap<String,Integer>();
        var finalRank=store.finalRanking(career,year);if(finalRank!=null)finalRank.ranking().forEach(r->domestic.put("LCK:"+r.teamCode(),r.seed()));
        m.finance.awards.values().stream().filter(a->a.seasonYear()==year&&a.competition().equals("WORLDS")).forEach(a->worlds.put(a.team(),a.placementFrom()));
        m.finance.close(year,date,domestic,worlds);CareerMarketStore.persist(store.jdbc,career,year,old,m);
    }
    static void startSeason(JdbcTemplate jdbc,String career,int year){var old=CareerMarketStore.load(jdbc,career);if(old==null||old.state().finance()==null)return;var m=CareerMarketStore.engine(jdbc,career,year,old);m.finance.initializeTargets(year,m.state().processedThrough(),false);CareerMarketStore.persist(jdbc,career,year,old,m);}
}
