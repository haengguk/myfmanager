package com.lolfm.career;

import com.lolfm.domain.PlayerSkill;
import com.lolfm.player.ExpandedPlayerCatalog.Definition;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.lolfm.career.CareerDevelopmentState.*;
import static com.lolfm.career.CareerDevelopmentPolicy.*;

/** One operation's in-memory development state, consumed in market-day order. */
public final class CareerDevelopmentEngine {
    final CareerRosterStore.Directory base;
    final Map<String,Player> players=new TreeMap<>();
    final Map<String,Metadata> metadata=new TreeMap<>();
    final Map<String,Schedule> teams=new TreeMap<>();
    final Map<String,Gain> gains=new TreeMap<>();
    final Map<String,Gain> monthly=new TreeMap<>();
    final LocalDate initialized;LocalDate next;
    public CareerDevelopmentEngine(CareerRosterStore.Directory base,CareerDevelopmentState state) {
        this.base=base;players.putAll(state.players());teams.putAll(state.teamPlans());state.gains().forEach(g->gains.put(g.date()+"|"+g.playerId(),g));monthly.putAll(state.monthly());
        initialized=state.initializedOn();next=state.nextSettlement();
        if(!VERSION.equals(state.policyVersion())||!INITIALIZATION.equals(state.initializationVersion())||!players.keySet().equals(base.players().keySet()))throw new IllegalStateException("DEVELOPMENT_STATE_REFERENCE");
        base.players().forEach((id,p)->metadata.put(id,metadata(p)));
        for(var entry:players.entrySet()) {
            var p=entry.getValue();var d=base.players().get(entry.getKey());
            if(!p.internalRatings().keySet().equals(d.gameplay().ratings().keySet())||p.internalRatings().values().stream().anyMatch(v->v<UNIT||v>MAX)
                ||p.internalProficiencies().values().stream().anyMatch(v->v<UNIT||v>MAX)||p.fatigue()<0||p.fatigue()>1000||p.remainder()<0||p.remainder()>=1_000_000_000_000L
                ||p.cursors().values().stream().anyMatch(c->c.index()<0||c.filled()<0||c.filled()>=UNIT))throw new IllegalStateException("DEVELOPMENT_STATE_RANGE");
        }
    }
    public static CareerDevelopmentState initial(CareerRosterStore.Directory base,LocalDate date) {
        var ps=new TreeMap<String,Player>();base.players().forEach((id,p)->ps.put(id,CareerDevelopmentPolicy.initial(p)));
        return new CareerDevelopmentState(VERSION,INITIALIZATION,date,date,ps,Map.of(),List.of(),Map.of());
    }
    public CareerDevelopmentState state(){return new CareerDevelopmentState(VERSION,INITIALIZATION,initialized,next,players,teams,List.copyOf(gains.values()),monthly);}
    public CareerRosterStore.Directory directory() {
        var result=new TreeMap<String,Definition>();base.players().forEach((id,d)->{
            var p=players.get(id);var ratings=new EnumMap<PlayerSkill,Integer>(PlayerSkill.class);p.internalRatings().forEach((k,v)->ratings.put(k,v/UNIT));
            var prof=new ArrayList<CompetitionRosterSnapshot.Proficiency>();var authored=new HashSet<String>();
            for(var original:d.gameplay().proficiencies()) {
                String key=key(original.championId(),original.position());authored.add(key);
                prof.add(new CompetitionRosterSnapshot.Proficiency(original.championId(),original.position(),p.internalProficiencies().get(key)/UNIT));
            }
            p.internalProficiencies().entrySet().stream().filter(e->!authored.contains(e.getKey())).map(e->{int sep=e.getKey().lastIndexOf('|');return new CompetitionRosterSnapshot.Proficiency(e.getKey().substring(0,sep),com.lolfm.domain.Position.valueOf(e.getKey().substring(sep+1)),e.getValue()/UNIT);})
                .sorted(Comparator.comparing(CompetitionRosterSnapshot.Proficiency::championId).thenComparing(CompetitionRosterSnapshot.Proficiency::position)).forEach(prof::add);
            var gameplay=new CompetitionRosterSnapshot.Starter(id,d.nickname(),d.position(),ratings,prof);
            result.put(id,new Definition(id,d.nickname(),d.position(),gameplay,d.provisional(),d.initialOrganizationId(),d.initialOwnerTeam(),d.initialSquad(),d.eligibilityReason(),d.detailsJson()));
        });return new CareerRosterStore.Directory(result,base.organizations());
    }
    public Plan effective(String id,String managed,CareerRosterStore.Membership member,LocalDate date,List<LocalDate> fixtures) {
        var p=players.get(id);String team=member==null?null:member.ownerTeam();var override=activate(p.override(),date,team);
        if(override!=null&&override.current()!=null)return override.current();
        if(team==null)return new Plan(Intensity.LIGHT,Focus.BALANCED,null,List.of());
        if(!team.equals(managed))return ai(base.players().get(id),metadata.get(id),p,date,fixtures.stream().filter(d->!d.isBefore(date)).mapToInt(d->(int)ChronoUnit.DAYS.between(date,d)).min().orElse(365));
        var schedule=activate(teams.get(team),date,team);return schedule==null||schedule.current()==null?DEFAULT:schedule.current();
    }
    public void closeDay(LocalDate date,String managed,Map<String,CareerRosterStore.Membership> members,Map<String,List<LocalDate>> fixtures,int year) {
        if(!date.equals(next))throw new IllegalStateException("DEVELOPMENT_SETTLEMENT_ORDER");
        teams.replaceAll((team,s)->activate(s,date,team));
        for(String id:players.keySet()) {
            var old=players.get(id);var member=members.get(id);String team=member==null?null:member.ownerTeam();
            var current=copy(old,old.fatigue(),old.playedOn(),activate(old.override(),date,team));players.put(id,current);
            var plan=effective(id,managed,member,date,team==null?List.of():fixtures.getOrDefault(team,List.of()));
            var result=day(current,base.players().get(id),metadata.get(id),date,plan,team==null);record(id,date,old,result,year);players.put(id,result);
        }
        next=date.plusDays(1);gains.values().removeIf(g->g.date().isBefore(next.minusDays(30)));
    }
    public void game(String id,String champion,com.lolfm.domain.Position role,LocalDate date,int year) {
        if(!date.equals(next)||!players.containsKey(id)||base.players().get(id).position()!=role)throw new IllegalStateException("DEVELOPMENT_COMPLETION_SCOPE");
        var old=players.get(id);var result=CareerDevelopmentPolicy.game(old,base.players().get(id),metadata.get(id),date,champion);
        record(id,date,old,result,year);players.put(id,result);
    }
    private void record(String id,LocalDate date,Player old,Player result,int year) {
        int ability=sum(result)-sum(old);int prof=0;var rises=new TreeMap<String,Integer>();
        for(var e:result.internalRatings().entrySet()){int delta=e.getValue()/UNIT-old.internalRatings().get(e.getKey())/UNIT;if(delta>0)rises.put(e.getKey().name(),delta);}
        for(var e:result.internalProficiencies().entrySet()){int before=old.internalProficiencies().getOrDefault(e.getKey(),14000);prof+=e.getValue()-before;int delta=e.getValue()/UNIT-before/UNIT;if(delta>0)rises.put(e.getKey(),delta);}
        if(ability==0&&prof==0)return;
        var gain=new Gain(date,id,ability,prof,rises,year);
        // Merge same-day facts (including multiple Series); retain only rolling deltas plus monthly totals.
        gains.merge(date+"|"+id,gain,CareerDevelopmentEngine::merge);
        String month=year+"|"+date.withDayOfMonth(1)+"|"+id;
        var m=new Gain(date.withDayOfMonth(1),id,ability,prof,rises,year);monthly.merge(month,m,CareerDevelopmentEngine::merge);
    }
    static Gain merge(Gain a,Gain b){var rises=new TreeMap<>(a.integerRises());b.integerRises().forEach((k,v)->rises.merge(k,v,Integer::sum));return new Gain(a.date(),a.playerId(),a.internalGain()+b.internalGain(),a.proficiencyGain()+b.proficiencyGain(),rises,a.seasonYear());}
}
