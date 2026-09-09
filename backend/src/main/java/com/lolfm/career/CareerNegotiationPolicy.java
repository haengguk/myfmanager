package com.lolfm.career;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.*;
import static com.lolfm.career.CareerFinancePolicy.*;
import static com.lolfm.career.CareerMarketState.*;

/** V2 game asking prices. Frozen contract pay and initial research allocations are never repriced. */
public final class CareerNegotiationPolicy {
    public static final String VERSION="CAREER_NEGOTIATION_PRICE_V2";
    public static final int DEVELOPMENT_STRENGTH=120,RESERVE_STRENGTH=156,STARTER_STRENGTH=180,STARTERS=5;
    private record Profile(String currency,long development,long reserve,long starter) {}
    private static final Map<String,Profile> PROFILES=profiles();
    private static Map<String,Profile> profiles(){
        JsonNode root=CareerFinanceReference.json();var result=new TreeMap<String,Profile>();
        root.path("profiles").properties().forEach(e->{String region=e.getKey();var p=e.getValue();
            var salaries=new ArrayList<Long>();root.path("teams").properties().stream().filter(t->t.getKey().startsWith(region+":"))
                .forEach(t->salaries.add(t.getValue().path("estimates").path("starterCompensation").path("base").asLong()/STARTERS));
            salaries.sort(Long::compareTo);int n=salaries.size();long median=n%2==1?salaries.get(n/2):(salaries.get(n/2-1)+salaries.get(n/2))/2;
            result.put(region,new Profile(p.path("currency").asText(),p.path("developmentUnit").asLong(),p.path("reserveUnit").asLong(),median));
        });return Map.copyOf(result);
    }
    public record Quote(String policyVersion,LocalDate pricedOn,String region,String marketBasis,int strength,
            String abilityBand,long annualDemand,String referenceHash,String fxPolicyVersion) {
        public Quote {if(!VERSION.equals(policyVersion)||pricedOn==null||!known(region)||strength<12||strength>240||annualDemand<1||annualDemand>MAX_MONEY||marketBasis==null||abilityBand==null||referenceHash==null||!FX.equals(fxPolicyVersion))throw new IllegalArgumentException("NEGOTIATION_PRICE_POLICY");}
    }
    static boolean known(String region){return region!=null&&PROFILES.containsKey(region);}
    static long annual(String region,int strength,Map<String,String> fx){
        var p=PROFILES.get(region);long amount;
        if(strength<=DEVELOPMENT_STRENGTH)amount=ratio(p.development(),strength,DEVELOPMENT_STRENGTH);
        else if(strength<=RESERVE_STRENGTH)amount=p.development()+ratio(p.reserve()-p.development(),strength-DEVELOPMENT_STRENGTH,RESERVE_STRENGTH-DEVELOPMENT_STRENGTH);
        else if(strength<=STARTER_STRENGTH)amount=p.reserve()+ratio(p.starter()-p.reserve(),strength-RESERVE_STRENGTH,STARTER_STRENGTH-RESERVE_STRENGTH);
        else amount=ratio(p.starter(),(long)strength*strength,(long)STARTER_STRENGTH*STARTER_STRENGTH);
        return Math.max(1,convert(amount,fx.get(p.currency())));
    }
    static Quote quote(CareerMarketEngine m,String id,LocalDate date){
        String market=null,basis=null;var loan=m.loan(id,date);var contract=m.active(id,date);
        if(loan!=null){market=CareerMarketPolicy.region(loan.borrowingTeam());basis="ACTIVE_LOAN_OPERATING_MARKET";}
        else if(contract!=null&&contract.team()!=null){market=CareerMarketPolicy.region(contract.team());basis="ACTIVE_CONTRACT_MARKET";}
        else {
            var latest=m.contracts.values().stream().filter(c->c.playerId().equals(id)&&c.team()!=null&&!c.terms().startDate().isAfter(date)
                &&Set.of(ContractStatus.EXPIRED,ContractStatus.RELEASED,ContractStatus.TRANSFERRED,ContractStatus.RETIRED).contains(c.status()))
                .max(Comparator.comparing((Contract c)->c.endedDate()==null?c.terms().endDate():c.endedDate()).thenComparing(Contract::signedDate).thenComparing(Contract::contractId));
            if(latest.isPresent()){market=CareerMarketPolicy.region(latest.get().team());basis="LAST_CONTRACT_MARKET";}
        }
        if(!known(market)){
            var p=m.player(id);market=CareerRosterStore.read(p.detailsJson(),JsonNode.class).path("leagueContext").asText(null);basis="STRUCTURED_ORIGIN_MARKET";
            if(!known(market)){market=p.initialOwnerTeam()==null?null:CareerMarketPolicy.region(p.initialOwnerTeam());basis="INITIAL_OWNER_MARKET";}
            if(!known(market)){market=p.initialOrganizationId()==null?null:p.initialOrganizationId().split(":")[0];basis="INITIAL_ORGANIZATION_MARKET";}
            if(!known(market)){market="LCK";basis="UNKNOWN_MARKET_FALLBACK";}
        }
        int strength=CareerMarketPolicy.strength(m.player(id));
        return new Quote(VERSION,date,market,basis,strength,strength<RESERVE_STRENGTH?"DEVELOPMENT_TO_RESERVE":strength<STARTER_STRENGTH?"RESERVE_TO_STARTER":"STARTER_REFERENCE",
            annual(market,strength,m.finance.basis.fx()),CareerFinanceReference.HASH,m.finance.basis.fxPolicyVersion());
    }
    private CareerNegotiationPolicy(){}
}
