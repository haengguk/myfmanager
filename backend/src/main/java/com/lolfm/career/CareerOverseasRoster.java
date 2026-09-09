package com.lolfm.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lolfm.champion.ChampionCatalog;
import com.lolfm.domain.Position;
import com.lolfm.player.ExpandedPlayerCatalog.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.lolfm.career.CareerRosterStore.*;
import static com.lolfm.career.CareerMarketState.*;
import static com.lolfm.career.CareerFinanceState.*;

/** One-time runtime extension. Original roster/finance resource packs are never edited. */
final class CareerOverseasRoster {
    static final String EXTENSION="OVERSEAS_PARTICIPANT_EXTENSION_V1";
    static final Map<String,String> OWNERS=Map.of("LEC:KCB","LEC:KC");
    static String owner(String team){return OWNERS.getOrDefault(team,team);}
    static void activate(JdbcTemplate db,String career,int year,boolean fresh,ChampionCatalog champions){
        var marker=CareerOverseasStore.activation(db,career);if(marker.extensionApplied())return;
        var old=CareerMarketStore.load(db,career);var m=CareerMarketStore.engine(db,career,year,old);var source=sourceDirectory(db,career);var definitions=new TreeMap<>(source.players());var organizations=new TreeMap<>(source.organizations());LocalDate date=m.state().processedThrough();
        for(String team:List.of("LPL:OMG","LPL:UP","LEC:LR")){organizations.put(team,new Organization(team,team,team,"CLUB"));m.lineups.putIfAbsent(team,new ArrayList<>());}
        organizations.put("LEC:KCB",new Organization("LEC:KCB","Karmine Corp Blue","LEC:KC","DEVELOPMENT"));
        addFinance(m,year,date);
        for(var p:source.players().values().stream().sorted(Comparator.comparing(Definition::playerId)).toList()){
            if(!Set.of("LPL:OMG","LPL:UP","LEC:KCB").contains(p.initialOrganizationId()==null?"":p.initialOrganizationId()))continue;
            var details=read(p.detailsJson(),JsonNode.class);if(!p.position().name().equals(details.path("roster").path("currentRegisteredRole").asText()))continue;
            var member=m.members.get(p.playerId());if(member==null||!Objects.equals(member.organizationId(),p.initialOrganizationId()))continue;
            String club=owner(p.initialOrganizationId());var contract=m.active(p.playerId(),date);
            if(!fresh&&contract==null)continue; // A new season never revives an expired/transferred/retired contract.
            var adopted=(ObjectNode)details.deepCopy();adopted.putObject("careerOverseasAdoption").put("policy",EXTENSION).put("date",date.toString()).put("employmentOwner",club).put("competitionTeam",p.initialOrganizationId());
            definitions.put(p.playerId(),new Definition(p.playerId(),p.nickname(),p.position(),p.gameplay(),p.provisional(),p.initialOrganizationId(),p.initialOwnerTeam(),p.initialSquad(),null,adopted.toString()));
            m.members.put(p.playerId(),new Membership(p.playerId(),club,p.initialOrganizationId(),p.initialSquad(),null));
            if(contract!=null)m.contracts.put(contract.contractId(),new Contract(contract.contractId(),contract.careerId(),contract.playerId(),club,contract.organizationId(),contract.signedDate(),contract.terms(),contract.status(),contract.revision(),contract.policyVersion(),contract.origin(),contract.terminationPolicy(),contract.endedDate(),contract.paidThrough()));
            else{
                String id=CareerMarketEngine.id(career,"OVERSEAS_INITIAL|"+p.playerId());LocalDate end=date.withMonth(11).withDayOfMonth(30);String publicEnd=details.path("contract").path("endDate").asText();if(!publicEnd.isBlank())end=LocalDate.parse(publicEnd).plusYears(year-2026);if(end.isBefore(date))throw new IllegalStateException("OVERSEAS_INITIAL_CONTRACT_DATE");
                Role role=p.initialSquad().equals("DEVELOPMENT")?Role.DEVELOPMENT:Role.STARTER;
                // Initial source adoption preserves V1 reference pay; V2 applies to new negotiations only.
                m.contracts.put(id,new Contract(id,career,p.playerId(),club,p.initialOrganizationId(),date,new Terms(date,end,m.finance.legacyDemand(p.playerId()),0,role),ContractStatus.ACTIVE,0,CareerMarketPolicy.VERSION,EXTENSION,"REMAINING_SALARY_25_PERCENT_V1",null,date.minusDays(1)));
            }
        }
        String json=write(new Directory(definitions,organizations));db.update("UPDATE career_player_directory SET directory_json=?,directory_hash=? WHERE career_id=?",json,hash(json),career);
        m.directory=directory(db,career);m.promiseEngine.initialize(date);if(m.lifecycle!=null){m.lifecycle.overseasEnabled=true;m.members.forEach((id,member)->m.lifecycle.observePlacement(id,member,date));}
        // Select legal held players through the ordinary planner. Vacancies remain ordinary recruitment needs.
        m.planner.repair(date);CareerMarketStore.persist(db,career,year,old,m);
        supply(db,career,year,champions);
        db.update("UPDATE career_overseas_activation SET extension_applied=TRUE WHERE career_id=?",career);
    }
    private static void addFinance(CareerMarketEngine m,int year,LocalDate date){
        var f=m.finance.state();var teams=new TreeMap<>(f.teams());var approvals=new TreeMap<>(f.approvals());var prizes=new TreeMap<>(f.prizeRules());CareerFinanceReference.rules(CareerFinanceReference.json()).forEach(prizes::putIfAbsent);
        for(String team:List.of("LPL:OMG","LPL:UP","LEC:LR"))if(!m.accounts.containsKey(team)){
            String region=team.substring(0,3);var candidates=f.teams().values().stream().filter(t->t.team().startsWith(region+":")).sorted(Comparator.comparingLong(Team::operatingBudget).thenComparing(Team::team)).toList();var ref=candidates.get(candidates.size()/2);
            // Fixed within-region median reference is a game estimate, not the named club's published budget.
            var t=new Team(team,ref.sourceCurrency(),"GAME_ESTIMATE","REGIONAL_MEDIAN_EXTENSION",List.of(EXTENSION,"MEDIAN_REFERENCE:"+ref.team()),ref.originalBase(),ref.playerCompensation(),ref.operatingBudget(),ref.nonWage(),ref.contingency(),ref.wageLimit(),ref.openingCash(),ref.protectedCash(),ref.includedTransferAllowance(),0,ref.playerCompensation());teams.put(team,t);
            m.accounts.put(team,new Account(team,t.wageLimit(),t.openingCash(),CareerMarketPolicy.MIN_ROSTER_LIMIT));long income=t.playerCompensation()+t.nonWage(),support=CareerFinancePolicy.pct(income,CareerFinancePolicy.SUPPORT_PERCENT);
            approvals.put(team+"|"+year,new Approval(team,year,date,income,support,income-support,t.nonWage(),t.wageLimit(),0,0,"NOT_EVALUATED",CareerFinancePolicy.FUNDING));
            m.ledger.add(new Ledger(CareerMarketEngine.id(m.career,EXTENSION+"|"+team),date,team,null,"OVERSEAS_EXTENSION_INITIAL_ALLOCATION",t.openingCash()));
        }
        m.finance=new CareerFinanceEngine(m,new CareerFinanceState(f.policyVersion(),f.currency(),f.referenceSeason(),f.scenario(),f.sourceHash(),f.sourceHashes(),f.fxPolicyVersion(),f.fx(),f.introducedOn(),f.operatingThrough(),f.legacyTransition(),f.recurringEffectiveOn(),teams,f.prices(),prizes,approvals,f.targets(),f.awards(),f.excludedInstances(),f.operatingArrears(),f.heldPrizes()));m.finance.initializeTargets(year,date,false);
    }
    private static void supply(JdbcTemplate db,String career,int year,ChampionCatalog champions){
        var old=CareerMarketStore.load(db,career);var m=CareerMarketStore.engine(db,career,year,old);if(m.lifecycle==null)throw new IllegalStateException("OVERSEAS_LIFECYCLE_REQUIRED");var growthSaved=CareerDevelopmentStore.load(db,career);var growth=new CareerDevelopmentEngine(baseDirectory(db,career),growthSaved.state());LocalDate date=m.state().processedThrough();var names=new HashSet<String>();m.directory.players().values().forEach(p->names.add(p.nickname().toLowerCase(Locale.ROOT)));
        int sequence=db.queryForObject("SELECT COUNT(*)+1 FROM career_generated_player WHERE career_id=? AND intake_year=?",Integer.class,career,year);
        for(String region:List.of("LPL","LEC")){
            var gaps=new EnumMap<Position,Integer>(Position.class);for(Position role:Position.values()){int count=0;for(String team:region.equals("LPL")?List.of("LPL:OMG","LPL:UP"):List.of("LEC:LR","LEC:KCB"))if(candidates(m,team).stream().noneMatch(id->m.player(id).position()==role))count++;gaps.put(role,count);}
            var rookies=CareerRookieFactory.generate(career,m.seed,year,date,gaps,true,sequence,champions,List.of(region),names);sequence+=rookies.size();
            for(var rookie:rookies){var d=rookie.definition();var details=read(d.detailsJson(),ObjectNode.class);details.withObject("/generated").put("supplyReason",EXTENSION);d=new Definition(d.playerId(),d.nickname(),d.position(),d.gameplay(),false,null,null,"UNAFFILIATED",null,details.toString());String json=write(d);db.update("INSERT INTO career_generated_player VALUES (?,?,?,?,?,?)",career,d.playerId(),year,date,json,hash(json));
                m.lifecycle.people.put(d.playerId(),new CareerLifecycleState.Person(rookie.age(),"GENERATED",year,date,com.lolfm.player.PlayerAbilityPolicy.currentAbility(d.gameplay().ratings()),date,CareerLifecycleState.Status.ACTIVE,null,null,"해외 확장 시 포지션 공백의 제한된 FA 공급",0,0,0,null,"NOT_YET_REVIEWED",date));growth.players.put(d.playerId(),CareerDevelopmentPolicy.initial(d));m.members.put(d.playerId(),new Membership(d.playerId(),null,null,"UNAFFILIATED",null));m.preferences.put(d.playerId(),CareerMarketPolicy.preference(m.seed,d));m.freeAgents.add(d.playerId());}
        }
        CareerLifecycleStore.persist(db,career,m.lifecycle);var composed=new CareerDevelopmentEngine(baseDirectory(db,career),growth.state());CareerDevelopmentStore.persist(db,career,growthSaved,composed);m.directory=composed.directory();CareerMarketStore.persist(db,career,year,old,m);
    }
    static List<String> candidates(CareerMarketEngine m,String team){String employer=owner(team);return m.members.values().stream().filter(v->employer.equals(v.ownerTeam())&&v.eligibilityReason()==null&&m.eligible(v.playerId(),employer,m.state().processedThrough())&&(team.equals("LEC:KCB")?"DEVELOPMENT".equals(v.squad()):"FIRST_TEAM".equals(v.squad()))).map(Membership::playerId).sorted(Comparator.comparingInt((String id)->CareerMarketPolicy.strength(m.player(id))).reversed().thenComparing(id->id)).toList();}
    static CompetitionRosterSnapshot.Roster roster(CareerMarketEngine m,String team){
        List<String> selected=team.equals("LEC:KCB")?Arrays.stream(Position.values()).map(role->candidates(m,team).stream().filter(id->m.player(id).position()==role).findFirst().orElse(null)).filter(Objects::nonNull).toList():m.lineups.getOrDefault(team,List.of());
        if(selected.size()!=5||selected.stream().anyMatch(id->!m.eligible(id,owner(team),m.state().processedThrough())))throw CareerException.invalid("lineup",team+"의 계약과 포지션별 선발을 보완해야 합니다.");var players=selected.stream().map(id->m.player(id).gameplay()).toList();String[] parts=team.split(":");return new CompetitionRosterSnapshot.Roster(new com.lolfm.player.GlobalTeamRosterCatalog.TeamKey(parts[0],parts[1]),hash(write(players)),players);
    }
    static CompetitionRosterSnapshot pair(JdbcTemplate db,String career,int year,String first,String second){var m=CareerMarketStore.engine(db,career,year,CareerMarketStore.load(db,career));var rosters=new TreeMap<String,CompetitionRosterSnapshot.Roster>();for(String team:List.of(first,second))rosters.put(team,roster(m,team));
        var result=new CompetitionRosterSnapshot(rosters);CareerAppearanceStore.requireNoCrossSquad(db,career,"OVERSEAS_PRECHECK",m.state().processedThrough(),"FIRST_TEAM",rosters.values().stream().flatMap(r->r.players().stream()).map(CompetitionRosterSnapshot.Starter::playerId).collect(java.util.stream.Collectors.toSet()),true);return result;}
}
