package com.lolfm.career;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import static com.lolfm.career.CareerFinanceState.*;
import static com.lolfm.career.CareerFinancePolicy.*;
import static com.lolfm.career.CareerMarketState.*;

/** Research stays read-only. Only the explicitly adopted base game policy enters a Career snapshot. */
final class CareerFinanceReference {
    private static final String TEXT=resource();
    static final String HASH=CareerRosterStore.hash(TEXT);
    private static String resource(){try(var in=CareerFinanceReference.class.getResourceAsStream("/career/finance-reference-2026-v1.json")){return new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException("FINANCE_REFERENCE",e);}}
    static JsonNode json(){return CareerRosterStore.read(TEXT,JsonNode.class);}
    static List<String> strings(JsonNode value){var out=new ArrayList<String>();value.forEach(v->out.add(v.asText()));return out;}
    static Map<String,String> stringMap(JsonNode value){var out=new TreeMap<String,String>();value.properties().forEach(e->out.put(e.getKey(),e.getValue().asText()));return out;}
    static Map<String,PrizeRule> rules(JsonNode root){
        var out=new TreeMap<String,PrizeRule>();for(var c:root.path("prizes"))if(EVENT_IDS.containsValue(c.path("eventId").asText())){
            var placements=new ArrayList<Placement>();for(var p:c.path("placements"))placements.add(new Placement(p.path("placementFrom").asInt(),p.path("placementTo").asInt(),p.path("amountPerTeam").asLong()));
            out.put(c.path("eventId").asText(),new PrizeRule(c.path("eventId").asText(),c.path("currency").isNull()?"KRW":c.path("currency").asText(),c.path("evidenceStatus").asText(),c.path("distributionComplete").asBoolean(),placements,strings(c.path("sourceIds"))));
        }return out;
    }
    static CareerFinanceState initialize(CareerMarketEngine m,int year,boolean legacy,Set<String> excluded){
        var root=json();var fx=stringMap(root.path("krwPerUnit"));var teams=new TreeMap<String,Team>();var prices=new TreeMap<String,Price>();var approvals=new TreeMap<String,Approval>();
        LocalDate date=m.state().processedThrough();
        if(!legacy){m.contracts.values().removeIf(c->m.members.get(c.playerId()).eligibilityReason()!=null||c.team()==null||!m.accounts.containsKey(c.team()));
            m.promiseEngine.promises.values().removeIf(p->!m.contracts.containsKey(p.contractId()));}
        for(String team:m.accounts.keySet()){
            var t=root.path("teams").path(team);if(t.isMissingNode())throw new IllegalStateException("FINANCE_TEAM_ID:"+team);
            String currency=t.path("currency").asText(),rate=fx.get(currency);var original=new TreeMap<String,Long>();
            t.path("estimates").properties().forEach(e->original.put(e.getKey(),e.getValue().path("base").asLong()));
            t.path("gameAmounts").properties().forEach(e->original.put(e.getKey(),e.getValue().path("base").asLong()));
            long allocated=0;
            for(Role role:Role.values()){
                String field=role==Role.STARTER?"starterCompensation":role==Role.RESERVE?"firstTeamReserveCompensation":"developmentPlayerCompensation";
                String count=role==Role.STARTER?"starters":role==Role.RESERVE?"additionalFirstTeam":"development";
                var members=m.contracts.values().stream().filter(c->team.equals(c.team())&&c.status()==ContractStatus.ACTIVE&&c.terms().role()==role&&m.members.get(c.playerId()).eligibilityReason()==null).toList();
                var weights=new TreeMap<String,Integer>();for(var c:members)weights.put(c.playerId(),CareerMarketPolicy.strength(m.player(c.playerId())));
                long total=convert(original.get(field),rate);int expected=t.path("counts").path(count).asInt();long available=expected==0?0:ratio(total,Math.min(expected,members.size()),expected);
                var shares=allocate(available,weights);
                for(var c:members){long salary=shares.getOrDefault(c.playerId(),0L);if(salary==0)continue;allocated+=salary;
                    prices.put(c.playerId(),new Price(legacy?legacy(CareerMarketPolicy.demand(m.player(c.playerId()))):salary,Math.max(1,CareerMarketPolicy.strength(m.player(c.playerId()))),CareerMarketPolicy.region(team),role.name(),legacy?"LEGACY_FIXED_REFERENCE":"INITIAL_GROUP_WEIGHTED_REFERENCE"));
                    if(!legacy)m.contracts.put(c.contractId(),new Contract(c.contractId(),c.careerId(),c.playerId(),c.team(),c.organizationId(),c.signedDate(),new Terms(c.terms().startDate(),c.terms().endDate(),salary,0,role),c.status(),c.revision(),c.policyVersion(),"GAME_INITIAL_GROUP_ALLOCATION_2026_BASE",c.terminationPolicy(),c.endedDate(),c.paidThrough()));
                }
            }
            long player=convert(original.get("totalPlayerCompensation"),rate),nonWage=0;
            for(String k:List.of("staffCompensation","facilitiesTravelAdministration","developmentNonWage","employerCostProvision"))nonWage=Math.addExact(nonWage,convert(original.get(k),rate));
            long wage=convert(original.get("annualWageBudget"),rate),cash=convert(original.get("initialCash"),rate);
            var basis=new Team(team,currency,t.path("confidence").asText(),t.path("evidenceClass").asText(),strings(t.path("sourceIds")),original,player,convert(original.get("annualOperatingBudget"),rate),nonWage,convert(original.get("contingency"),rate),wage,cash,convert(original.get("protectedOperatingCash"),rate),convert(original.get("transferAllowanceIncludedInCash"),rate),allocated,Math.max(0,player-allocated));teams.put(team,basis);
            if(!legacy){var old=m.accounts.get(team);m.accounts.put(team,new Account(team,wage,cash,old.rosterLimit()));}
            long income=player+nonWage;
            approvals.put(team+"|"+year,new Approval(team,year,date,legacy?m.accounts.get(team).annualBudget():income,legacy?m.accounts.get(team).annualBudget():pct(income,SUPPORT_PERCENT),legacy?0:income-pct(income,SUPPORT_PERCENT),legacy?0:nonWage,m.accounts.get(team).annualBudget(),0,0,"NOT_EVALUATED",legacy?"LEGACY_FUNDING_UNTIL_NEXT_SEASON":FUNDING));
        }
        for(String id:m.directory.players().keySet())if(!prices.containsKey(id)){
            var p=m.player(id);String region=p.initialOrganizationId()==null?"LCK":p.initialOrganizationId().split(":")[0];if(!root.path("profiles").has(region))region="LCK";
            String role="DEVELOPMENT".equals(m.members.get(id).squad())?"DEVELOPMENT":"RESERVE";var profile=root.path("profiles").path(region);
            long reference=legacy?legacy(CareerMarketPolicy.demand(p)):convert(profile.path(role.equals("DEVELOPMENT")?"developmentUnit":"reserveUnit").asLong(),fx.get(profile.path("currency").asText()));
            prices.put(id,new Price(reference,legacy?Math.max(1,CareerMarketPolicy.strength(p)):REFERENCE_STRENGTH,region,role,legacy?"LEGACY_FIXED_REFERENCE":"REGIONAL_ROLE_REFERENCE"));
        }
        if(!legacy){m.ledger.removeIf(l->l.kind().equals("INITIAL_ALLOCATION"));for(var a:m.accounts.values())m.ledger.add(new Ledger(CareerMarketEngine.id(m.career,"INITIAL_KRW|"+a.team()),date,a.team(),null,"INITIAL_ALLOCATION",a.cash()));}
        return new CareerFinanceState(VERSION,"KRW",2026,"base",HASH,stringMap(root.path("sourceHashes")),FX,fx,date,date.minusDays(1),legacy,legacy?null:date,teams,prices,rules(root),approvals,Map.of(),Map.of(),excluded,Map.of(),Map.of());
    }
}
