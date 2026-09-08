package com.lolfm.career;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiPredicate;
import static com.lolfm.career.CareerOverseasRules.*;

/** Finite pure graph projection. Production supplies only scores from applied verified receipts. */
final class CareerOverseasTournament {
    record Input(Event event,int year,long seed,List<String> entrants,Map<String,List<String>> groups,
                 Map<String,Integer> previousRanks,Map<String,Integer> priorPoints,int regionalSlots){
        Input{entrants=List.copyOf(entrants);var frozenGroups=new TreeMap<String,List<String>>();groups.forEach((key,value)->frozenGroups.put(key,List.copyOf(value)));groups=Map.copyOf(frozenGroups);previousRanks=Map.copyOf(previousRanks);priorPoints=Map.copyOf(priorPoints);
            if(entrants.size()!=event.count||new HashSet<>(entrants).size()!=entrants.size())throw new IllegalArgumentException("OVERSEAS_PARTICIPANTS");
            if(event.league.equals("LPL")&&event!=Event.LPL_REGIONAL_FINALS){
                var required=event==Event.LPL_SPLIT_1?Map.of("ASCEND",6,"PERSEVERANCE",4,"NIRVANA",4):Map.of("ASCEND",8,"NIRVANA",event==Event.LPL_SPLIT_2?6:4);
                var all=groups.values().stream().flatMap(Collection::stream).toList();
                if(!groups.keySet().equals(required.keySet())||all.size()!=entrants.size()||!new HashSet<>(all).equals(new HashSet<>(entrants)))throw new IllegalArgumentException("LPL_GROUP_PARTITION");
                for(var entry:required.entrySet())if(groups.get(entry.getKey()).size()!=entry.getValue())throw new IllegalArgumentException("LPL_GROUP_SIZE");
            }
        }
    }
    record Score(int first,int second){}
    record Bout(String id,String stage,String round,String group,LocalDate date,int bestOf,String first,String second,String selectionOwner){}
    record Draw(String round,List<String> teams,String policy){}
    record Plan(List<Bout> bouts,Map<String,List<String>> groupRanks,List<String> regularRanking,List<String> playoffTeams,
                Map<String,Integer> placements,List<String> ranking,Map<String,Integer> points,List<String> seasonEliminated,List<Draw> draws,boolean complete,Map<String,String> entitlements){
        Plan{bouts=List.copyOf(bouts);groupRanks=Map.copyOf(groupRanks);regularRanking=List.copyOf(regularRanking);playoffTeams=List.copyOf(playoffTeams);placements=Map.copyOf(placements);ranking=List.copyOf(ranking);points=Map.copyOf(points);seasonEliminated=List.copyOf(seasonEliminated);draws=List.copyOf(draws);entitlements=Map.copyOf(entitlements);}
    }
    private final Input in;private final Map<String,Score> scores;private final boolean corrected;
    private final List<Bout> bouts=new ArrayList<>();private final Map<String,List<String>> groupRanks=new TreeMap<>();
    private final Map<String,Integer> places=new TreeMap<>(),points=new TreeMap<>();private final List<Draw> draws=new ArrayList<>();
    private List<String> regular=List.of(),playoffs=List.of(),eliminated=List.of();private boolean complete;
    private CareerOverseasTournament(Input input,Map<String,Score> scores,String policy){in=input;this.scores=Map.copyOf(scores);if(!Set.of(VERSION,PROJECTION_VERSION).contains(policy))throw new IllegalArgumentException("OVERSEAS_PROJECTION_VERSION");corrected=PROJECTION_VERSION.equals(policy);}
    static Plan project(Input input,Map<String,Score> scores){return project(input,scores,PROJECTION_VERSION);}
    static Plan project(Input input,Map<String,Score> scores,String policy){var g=new CareerOverseasTournament(input,scores,policy);g.build();
        if(g.corrected&&g.complete)g.validatePlacements();
        if(!g.bouts.stream().map(Bout::id).collect(java.util.stream.Collectors.toSet()).containsAll(scores.keySet()))throw new IllegalArgumentException("OVERSEAS_ORPHAN_RESULT");
        var ranked=new ArrayList<>(input.entrants());ranked.sort(Comparator.comparingInt((String t)->g.places.getOrDefault(t,Integer.MAX_VALUE)).thenComparingInt(t->g.regular.contains(t)?g.regular.indexOf(t):input.entrants().indexOf(t)));
        return new Plan(g.bouts,g.groupRanks,g.regular,g.playoffs,g.places,g.complete?ranked:List.of(),g.points,g.eliminated,g.draws,g.complete,g.complete&&input.event()==Event.AMERICAS_CUP?Map.of("KOREA_BOOTCAMP_SUPPORT",ranked.getFirst()):Map.of());}
    private String winner(String id){var s=scores.get(id);var b=bout(id);return s==null||b==null?null:s.first()>s.second()?b.first():b.second();}
    private String loser(String id){var s=scores.get(id);var b=bout(id);return s==null||b==null?null:s.first()>s.second()?b.second():b.first();}
    private Bout bout(String id){return bouts.stream().filter(b->b.id().equals(id)).findFirst().orElse(null);}
    private int seed(String team){return playoffs.contains(team)?playoffs.indexOf(team):regular.contains(team)?regular.indexOf(team):in.entrants().indexOf(team);}
    private void add(String id,String stage,String round,String group,LocalDate proposed,int bo,String first,String second,boolean regularMatch){
        if(first==null||second==null)return;if(first.equals(second)||!in.entrants().containsAll(List.of(first,second)))throw new IllegalStateException("OVERSEAS_PAIR_SCOPE");
        LocalDate floor=corrected?stageStart(in.event(),in.year(),stage):proposed;
        LocalDate date=proposed.isBefore(floor)?floor:proposed;
        for(var b:bouts)if(Set.of(b.first(),b.second()).contains(first)||Set.of(b.first(),b.second()).contains(second))if(!b.date().isBefore(date))date=b.date().plusDays(1);
        if(date.isAfter(in.event().date(in.year(),in.event().end).plusDays(SCHEDULE_EXTENSION_DAYS)))throw new IllegalStateException("OVERSEAS_FINITE_SCHEDULE_EXHAUSTED:"+in.event()+":"+id);
        String owner=regularMatch?(Long.parseUnsignedLong(CareerRosterStore.hash(in.seed()+"|FIRST_SELECTION|"+pair(first,second)).substring(0,15),16)%2==0?first:second):seed(first)<seed(second)?first:second;
        // Reversed return fixtures invert the first meeting entitlement without consuming match Random.
        if(regularMatch&&bouts.stream().anyMatch(b->b.stage().equals(stage)&&pair(b.first(),b.second()).equals(pair(first,second))))owner=owner.equals(first)?second:first;
        var b=new Bout(id,stage,round,group,date,bo,first,second,owner);bouts.add(b);var s=scores.get(id);
        if(s!=null&&(Math.max(s.first(),s.second())!=bo/2+1||Math.min(s.first(),s.second())<0||Math.min(s.first(),s.second())>=bo/2+1))throw new IllegalArgumentException("OVERSEAS_SERIES_SCORE");
    }
    private void knockout(String id,String stage,int bo,String a,String b){
        LocalDate start=corrected?stageStart(in.event(),in.year(),stage):in.event().date(in.year(),in.event().post);
        if(corrected)for(var prior:bouts) {
            boolean prerequisite=stage.equals("PLAYOFFS")&&!prior.stage().equals("PLAYOFFS")
                    ||!stage.equals("TIEBREAKER")&&(prior.stage().equals("REGULAR")||prior.stage().equals("TIEBREAKER")||prior.stage().equals("SWISS"));
            if(prerequisite&&!prior.date().isBefore(start))start=prior.date().plusDays(1);
        }
        add(id,stage,id,null,start,bo,a,b,false);
    }
    private List<String> rr(String group,List<String> teams,int legs,int bo){
        var ring=new ArrayList<>(teams);int rounds=teams.size()-1;LocalDate start=in.event().date(in.year(),in.event().start),end=in.event().date(in.year(),in.event().regularEnd);
        for(int leg=0;leg<legs;leg++){
            ring.clear();ring.addAll(teams);
            for(int r=0;r<rounds;r++){
                LocalDate date=start.plusDays((long)(leg*rounds+r)*ChronoUnit.DAYS.between(start,end)/Math.max(1,legs*rounds-1));
                for(int p=0;p<teams.size()/2;p++){String a=ring.get(p),b=ring.get(teams.size()-1-p);if((r+p+leg)%2==1){var swap=a;a=b;b=swap;}
                    add("RR_"+group+"_"+(leg+1)+"_"+(r+1)+"_"+(p+1),"REGULAR","R"+(leg*rounds+r+1),group,date,bo,a,b,true);}
                ring.add(1,ring.removeLast());
            }
        }
        var fixtures=bouts.stream().filter(b->"REGULAR".equals(b.stage())&&group.equals(b.group())).toList();if(fixtures.stream().anyMatch(b->!scores.containsKey(b.id())))return List.of();
        var decision=CareerOverseasRanking.rank(in.event(),teams,fixtures,scores,in.previousRanks(),in.seed());var result=new ArrayList<>(decision.order());
        if(in.event().league.equals("LPL"))for(var tied:decision.unresolved()){
            // The public conditional BO3 has no full multi-team algorithm: seeded ladder fixes every place in n-1 games.
            var pool=result.stream().filter(tied::contains).toList();var order=new ArrayList<String>();String survivor=pool.getLast();
            for(int i=pool.size()-2;i>=0;i--){String id="TB_"+group+"_"+result.indexOf(pool.getFirst())+"_"+i;knockout(id,"TIEBREAKER",3,pool.get(i),survivor);if(winner(id)==null)return List.of();order.addFirst(loser(id));survivor=winner(id);}
            order.addFirst(survivor);int index=result.indexOf(pool.getFirst());for(int i=0;i<order.size();i++)result.set(index+i,order.get(i));
            draws.add(new Draw("TIEBREAKER_"+group,pool,"GAME_SEEDED_BO3_PLACEMENT_LADDER_V1"));
        }
        groupRanks.put(group,List.copyOf(result));return result;
    }
    private void build(){
        switch(in.event()){
            case LPL_SPLIT_1,LPL_SPLIT_2,LPL_SPLIT_3->lpl();
            case LPL_REGIONAL_FINALS->regional();
            case LCS_LOCK_IN->lockIn();
            case LCP_SPLIT_3->pacificSwiss();
            case AMERICAS_CUP->{regular=in.entrants();playoffs=regular;four("PO_",regular,3);finish("PO_F");}
            default->{regular=rr("ALL",in.entrants(),1,Set.of(Event.LEC_VERSUS,Event.CBLOL_COPA).contains(in.event())?1:3);if(regular.isEmpty())return;
                if(in.event()==Event.CBLOL_COPA){knockout("PI_1","PLAY_IN",3,regular.get(4),regular.get(5));knockout("PI_2","PLAY_IN",3,regular.get(6),regular.get(7));knockout("PI_3","PLAY_IN",5,loser("PI_1"),winner("PI_2"));if(winner("PI_3")==null)return;playoffs=new ArrayList<>(regular.subList(0,4));playoffs.add(winner("PI_1"));playoffs.add(winner("PI_3"));}
                else playoffs=regular.subList(0,in.event()==Event.LEC_VERSUS?8:6);
                if(in.event()==Event.LEC_VERSUS)eight(playoffs,true);
                else if(Set.of(Event.LCP_SPLIT_1,Event.LCP_SPLIT_2).contains(in.event()))hybrid(playoffs);
                else if(in.event().league.equals("CBLOL")||in.event()==Event.LCS_SUMMER)byeSix(playoffs,in.event()==Event.CBLOL_COPA,in.event()==Event.LCS_SUMMER);
                else six(playoffs);
                finish("PO_F");
            }
        }
        if(complete){
            if(in.event().league.equals("LPL"))places.forEach((t,p)->points.put(t,lplPoints(in.event(),p)));
            if(Set.of(Event.LCP_SPLIT_1,Event.LCP_SPLIT_2).contains(in.event())){var rs=CareerOverseasRanking.records(in.entrants(),bouts.stream().filter(b->b.stage().equals("REGULAR")).toList(),scores);for(String t:in.entrants()){var r=rs.get(t);int place=places.get(t);points.put(t,lcpRegularPoints(in.event(),regular.indexOf(t)+1,r.gameWins(),r.gameLosses())+(place<=4?new int[]{20,15,10,5}[place-1]:0));}}
            if(in.event()==Event.LCP_SPLIT_3)places.forEach((t,p)->{if(p==3)points.merge(t,15,Integer::sum);});
        }
    }
    private void markLoser(String id,int place){if(loser(id)!=null)places.put(loser(id),place);}
    private void finish(String id){if(winner(id)==null)return;places.put(winner(id),1);places.put(loser(id),2);if(corrected)assignRemainingPlaces();else for(String t:in.entrants())if(!places.containsKey(t))places.put(t,Math.max(playoffs.size()+1,regular.indexOf(t)+1));if(in.event().league.equals("CBLOL")){var tied=places.entrySet().stream().filter(e->e.getValue()==5).map(Map.Entry::getKey).sorted(Comparator.comparingInt(regular::indexOf)).toList();for(int i=0;i<tied.size();i++)places.put(tied.get(i),5+i);}complete=true;}
    private void assignRemainingPlaces() {
        if(in.event()==Event.LPL_SPLIT_1) {
            for(int i=0;i<2;i++){markLoser("K3_"+i,9);markLoser("K2_"+i,11);}
        } else if(in.event()==Event.LPL_SPLIT_2) {
            for(int i=0;i<4;i++)markLoser("K_"+i,9);
        } else if(in.event()==Event.LPL_SPLIT_3) {
            for(int i=0;i<2;i++)markLoser("K_"+i,9);
        } else if(in.event()==Event.CBLOL_COPA) {
            markLoser("PI_3",7);markLoser("PI_2",8);
        }
        // Remaining teams never reached the elimination bracket. Their regular order is the explicit fallback.
        for(String team:regular)if(!places.containsKey(team))places.put(team,places.size()+1);
    }
    private void validatePlacements() {
        if(!places.keySet().equals(new HashSet<>(in.entrants())))throw new IllegalStateException("OVERSEAS_FINAL_PARTITION");
        var counts=new TreeMap<Integer,Integer>();places.values().forEach(p->counts.merge(p,1,Integer::sum));
        int next=1;for(var group:counts.entrySet()) {
            if(group.getKey()!=next)throw new IllegalStateException("OVERSEAS_FINAL_RANK_INTERVAL");next+=group.getValue();
        }
        if(next!=in.entrants().size()+1)throw new IllegalStateException("OVERSEAS_FINAL_RANK_COVERAGE");
    }
    private void four(String prefix,List<String> t,int openingBo){
        knockout(prefix+"U1","PLAYOFFS",openingBo,t.get(0),t.get(3));knockout(prefix+"U2","PLAYOFFS",openingBo,t.get(1),t.get(2));
        knockout(prefix+"UF","PLAYOFFS",5,winner(prefix+"U1"),winner(prefix+"U2"));knockout(prefix+"L1","PLAYOFFS",5,loser(prefix+"U1"),loser(prefix+"U2"));
        knockout(prefix+"LF","PLAYOFFS",5,winner(prefix+"L1"),loser(prefix+"UF"));knockout(prefix+"F","PLAYOFFS",5,winner(prefix+"UF"),winner(prefix+"LF"));markLoser(prefix+"L1",4);markLoser(prefix+"LF",3);
    }
    private void six(List<String> t){
        knockout("PO_U1","PLAYOFFS",5,t.get(0),t.get(3));knockout("PO_U2","PLAYOFFS",5,t.get(1),t.get(2));
        knockout("PO_UF","PLAYOFFS",5,winner("PO_U1"),winner("PO_U2"));
        knockout("PO_L1","PLAYOFFS",5,t.get(4),loser("PO_U1"));knockout("PO_L2","PLAYOFFS",5,t.get(5),loser("PO_U2"));
        knockout("PO_LS","PLAYOFFS",5,winner("PO_L1"),winner("PO_L2"));knockout("PO_LF","PLAYOFFS",5,winner("PO_LS"),loser("PO_UF"));knockout("PO_F","PLAYOFFS",5,winner("PO_UF"),winner("PO_LF"));
        markLoser("PO_L1",5);markLoser("PO_L2",5);markLoser("PO_LS",4);markLoser("PO_LF",3);
    }
    private void hybrid(List<String> t){knockout("ENTRY_1","PLAYOFFS",5,t.get(2),t.get(5));knockout("ENTRY_2","PLAYOFFS",5,t.get(3),t.get(4));markLoser("ENTRY_1",5);markLoser("ENTRY_2",5);if(winner("ENTRY_1")!=null&&winner("ENTRY_2")!=null)four("PO_",List.of(t.get(0),t.get(1),winner("ENTRY_1"),winner("ENTRY_2")),5);}
    private void eight(List<String> t,boolean mixed){
        for(int i=0;i<4;i++)knockout("PO_Q"+i,"PLAYOFFS",mixed?3:5,t.get(i),t.get(7-i));
        for(int i=0;i<2;i++)knockout("PO_S"+i,"PLAYOFFS",mixed?3:5,winner("PO_Q"+(i*2)),winner("PO_Q"+(i*2+1)));
        knockout("PO_UF","PLAYOFFS",5,winner("PO_S0"),winner("PO_S1"));
        for(int i=0;i<2;i++){knockout("PO_L1_"+i,"PLAYOFFS",mixed?3:5,loser("PO_Q"+(i*2)),loser("PO_Q"+(i*2+1)));knockout("PO_L2_"+i,"PLAYOFFS",5,winner("PO_L1_"+i),loser("PO_S"+(1-i)));markLoser("PO_L1_"+i,7);markLoser("PO_L2_"+i,5);}
        knockout("PO_LS","PLAYOFFS",5,winner("PO_L2_0"),winner("PO_L2_1"));knockout("PO_LF","PLAYOFFS",5,winner("PO_LS"),loser("PO_UF"));knockout("PO_F","PLAYOFFS",5,winner("PO_UF"),winner("PO_LF"));markLoser("PO_LS",4);markLoser("PO_LF",3);
    }
    private void byeSix(List<String> t,boolean mixed,boolean gauntlet){
        knockout("PO_Q0","PLAYOFFS",mixed?3:5,t.get(2),t.get(5));knockout("PO_Q1","PLAYOFFS",mixed?3:5,t.get(3),t.get(4));
        var qw=ordered(winner("PO_Q0"),winner("PO_Q1"));if(qw.size()==2){knockout("PO_S0","PLAYOFFS",mixed?3:5,t.get(0),qw.getLast());knockout("PO_S1","PLAYOFFS",mixed?3:5,t.get(1),qw.getFirst());}
        knockout("PO_UF","PLAYOFFS",5,winner("PO_S0"),winner("PO_S1"));
        if(gauntlet){knockout("PO_L1","PLAYOFFS",5,loser("PO_Q0"),loser("PO_Q1"));var sl=ordered(loser("PO_S0"),loser("PO_S1"));if(sl.size()==2){knockout("PO_L2","PLAYOFFS",5,winner("PO_L1"),sl.getLast());knockout("PO_LS","PLAYOFFS",5,winner("PO_L2"),sl.getFirst());}markLoser("PO_L1",6);markLoser("PO_L2",5);}
        else{knockout("PO_L0","PLAYOFFS",5,loser("PO_Q0"),loser("PO_S1"));knockout("PO_L1","PLAYOFFS",5,loser("PO_Q1"),loser("PO_S0"));knockout("PO_LS","PLAYOFFS",5,winner("PO_L0"),winner("PO_L1"));markLoser("PO_L0",5);markLoser("PO_L1",5);}
        knockout("PO_LF","PLAYOFFS",5,winner("PO_LS"),loser("PO_UF"));knockout("PO_F","PLAYOFFS",5,winner("PO_UF"),winner("PO_LF"));markLoser("PO_LS",4);markLoser("PO_LF",3);
    }
    private List<String> ordered(String... teams){return Arrays.stream(teams).filter(Objects::nonNull).sorted(Comparator.comparingInt(this::seed)).toList();}
    private void lpl(){
        var all=new ArrayList<String>();boolean waiting=false;for(var e:new TreeMap<>(in.groups()).entrySet()){int legs=in.event()==Event.LPL_SPLIT_2&&e.getKey().equals("NIRVANA")?1:2;var group=rr(e.getKey(),e.getValue(),legs,3);if(group.isEmpty())waiting=true;}if(waiting)return;
        var a=groupRanks.get("ASCEND");var n=groupRanks.get("NIRVANA");all.addAll(a);
        if(in.event()==Event.LPL_SPLIT_1)all.addAll(groupRanks.get("PERSEVERANCE"));all.addAll(n);regular=all;
        if(in.event()==Event.LPL_SPLIT_1){var p=groupRanks.get("PERSEVERANCE");for(int i=0;i<2;i++){knockout("K1_"+i,"KNIGHTS",5,a.get(4+i),p.get(1-i));knockout("K2_"+i,"KNIGHTS",5,p.get(2+i),n.get(1-i));}
            var re=ordered(loser("K1_0"),loser("K1_1"),winner("K2_0"),winner("K2_1"));if(re.size()!=4)return;
            // Reseed by actual group precedence and placement, never the initial draw or team rating.
            re=re.stream().sorted(Comparator.comparingInt(regular::indexOf)).toList();draws.add(new Draw("KNIGHTS_R3",re,"ACTUAL_GROUP_RESEED_V1"));
            for(int i=0;i<2;i++)knockout("K3_"+i,"KNIGHTS",5,re.get(i),re.get(3-i));if(winner("K3_0")==null||winner("K3_1")==null)return;
            var po=new ArrayList<>(a.subList(0,4));po.addAll(List.of(winner("K1_0"),winner("K1_1"),winner("K3_0"),winner("K3_1")));playoffs=po;eight(po,false);
        }else if(in.event()==Event.LPL_SPLIT_2){eliminated=n.subList(4,6);for(int i=0;i<4;i++)knockout("K_"+i,"KNIGHTS",5,a.get(4+i),n.get(3-i));if(java.util.stream.IntStream.range(0,4).anyMatch(i->winner("K_"+i)==null))return;var po=new ArrayList<>(a.subList(0,4));for(int i=0;i<4;i++)po.add(winner("K_"+i));playoffs=po;eight(po,false);
        }else{for(int i=0;i<2;i++)knockout("K_"+i,"KNIGHTS",5,a.get(6+i),n.get(1-i));if(winner("K_0")==null||winner("K_1")==null)return;var po=new ArrayList<>(a.subList(0,6));po.add(winner("K_0"));po.add(winner("K_1"));playoffs=po;
            knockout("PO_Q0","PLAYOFFS",5,po.get(2),po.get(5));knockout("PO_Q1","PLAYOFFS",5,po.get(3),po.get(4));
            knockout("PO_S0","PLAYOFFS",5,po.get(0),winner("PO_Q1"));knockout("PO_S1","PLAYOFFS",5,po.get(1),winner("PO_Q0"));knockout("PO_UF","PLAYOFFS",5,winner("PO_S0"),winner("PO_S1"));
            for(int i=0;i<2;i++){knockout("PO_L1_"+i,"PLAYOFFS",5,po.get(6+i),loser("PO_Q"+i));knockout("PO_L2_"+i,"PLAYOFFS",5,winner("PO_L1_"+i),loser("PO_S"+i));markLoser("PO_L1_"+i,7);markLoser("PO_L2_"+i,5);}
            knockout("PO_LS","PLAYOFFS",5,winner("PO_L2_0"),winner("PO_L2_1"));knockout("PO_LF","PLAYOFFS",5,winner("PO_LS"),loser("PO_UF"));knockout("PO_F","PLAYOFFS",5,winner("PO_UF"),winner("PO_LF"));markLoser("PO_LS",4);markLoser("PO_LF",3);
        }finish("PO_F");
    }
    private void regional(){regular=in.entrants();playoffs=regular;var t=regular;
        if(in.regionalSlots()>=4){knockout("RF_U","REGIONAL_FINALS",5,t.get(0),t.get(1));knockout("RF_L","REGIONAL_FINALS",5,t.get(2),t.get(3));knockout("RF_F","REGIONAL_FINALS",5,loser("RF_U"),winner("RF_L"));if(winner("RF_F")==null)return;places.put(winner("RF_U"),1);places.put(winner("RF_F"),2);places.put(loser("RF_F"),3);places.put(loser("RF_L"),4);complete=true;
        }else{knockout("RF_1","REGIONAL_FINALS",5,t.get(2),t.get(3));knockout("RF_2","REGIONAL_FINALS",5,t.get(1),winner("RF_1"));knockout("RF_F","REGIONAL_FINALS",5,t.get(0),winner("RF_2"));markLoser("RF_1",4);markLoser("RF_2",3);finish("RF_F");}
    }
    private List<String> swiss(boolean pacific){
        var wins=new TreeMap<String,Integer>();var losses=new TreeMap<String,Integer>();in.entrants().forEach(t->{wins.put(t,0);losses.put(t,0);});var previous=new HashSet<String>();String crossHigh=null,crossLow=null;
        for(int round=1;round<=(pacific?5:3);round++){
            var groups=new TreeMap<String,List<String>>();for(String t:in.entrants())if(!pacific||wins.get(t)<3&&losses.get(t)<3)groups.computeIfAbsent(wins.get(t)+"-"+losses.get(t),k->new ArrayList<>()).add(t);
            if(groups.isEmpty())break;
            var pairs=new ArrayList<String>();String relaxation="SEEDED_NO_REMATCH_V1";
            if(pacific&&round==4){if(crossHigh==null||crossLow==null)throw new IllegalStateException("LCP_CROSS_RECORD_SOURCE");groups.get("2-1").remove(crossHigh);groups.get("1-2").remove(crossLow);pairs.add(crossHigh);pairs.add(crossLow);if(previous.contains(pair(crossHigh,crossLow)))relaxation="OFFICIAL_CROSS_RECORD_REMATCH_REQUIRED";}
            for(var group:groups.entrySet()){
                if(group.getValue().isEmpty())continue;final int r=round;
                BiPredicate<String,String> initial=(a,b)->r!=1||!pacific||(seed(a)<4)!=(seed(b)<4);
                var chosen=pairing("SWISS_"+round+"_"+group.getKey(),group.getValue(),(a,b)->initial.test(a,b)&&!previous.contains(pair(a,b)));
                if(chosen==null){chosen=pairing("SWISS_"+round+"_"+group.getKey(),group.getValue(),initial);relaxation="NO_LEGAL_PAIRING_FORCED_REMATCH";}
                if(chosen==null)throw new IllegalStateException("SWISS_RECORD_CARDINALITY");pairs.addAll(chosen);
            }
            draws.add(new Draw("SWISS_R"+round,List.copyOf(pairs),relaxation));var current=new ArrayList<String>();
            LocalDate start=in.event().date(in.year(),in.event().start),end=in.event().date(in.year(),in.event().regularEnd);LocalDate date=start.plusDays((round-1)*ChronoUnit.DAYS.between(start,end)/(pacific?4:2));
            for(int i=0;i<pairs.size();i+=2){String a=pairs.get(i),b=pairs.get(i+1),id="SWISS_R"+round+"_"+(i/2);int bo=pacific&&(wins.get(a)==2||wins.get(b)==2||losses.get(a)==2||losses.get(b)==2)?5:3;
                add(id,"SWISS","R"+round,wins.get(a)+"-"+losses.get(a),date,bo,a,b,false);current.add(id);}
            if(current.stream().anyMatch(id->!scores.containsKey(id)))return List.of();
            for(String id:current){var b=bout(id);if(pacific&&round==3&&wins.get(b.first())==2&&losses.get(b.first())==0)crossHigh=loser(id);if(pacific&&round==3&&wins.get(b.first())==0&&losses.get(b.first())==2)crossLow=winner(id);
                wins.merge(winner(id),1,Integer::sum);losses.merge(loser(id),1,Integer::sum);previous.add(pair(b.first(),b.second()));}
        }
        var fs=bouts.stream().filter(b->b.stage().equals("SWISS")).toList();var order=new ArrayList<>(CareerOverseasRanking.rank(in.event(),in.entrants(),fs,scores,in.previousRanks(),in.seed()).order());
        if(pacific){order.sort(Comparator.comparingInt((String t)->wins.get(t)).reversed().thenComparingInt(losses::get));in.entrants().forEach(t->points.put(t,lcpSwissPoints(wins.get(t),losses.get(t))));
            for(int l=0;l<3;l++){int loss=l;groupRanks.put("3-"+l,order.stream().filter(t->wins.get(t)==3&&losses.get(t)==loss).toList());}
            for(int w=0;w<3;w++){int win=w;groupRanks.put(w+"-3",order.stream().filter(t->wins.get(t)==win&&losses.get(t)==3).toList());}
        }
        return List.copyOf(order);
    }
    private void lockIn(){regular=swiss(false);if(regular.isEmpty())return;
        knockout("LAST_CHANCE","LAST_CHANCE",LAST_CHANCE_BO,regular.get(5),regular.get(6));if(winner("LAST_CHANCE")==null)return;
        var qualified=new ArrayList<>(regular.subList(0,5));qualified.add(winner("LAST_CHANCE"));playoffs=qualified;six(playoffs);markLoser("LAST_CHANCE",7);finish("PO_F");
    }
    private void pacificSwiss(){regular=swiss(true);if(regular.isEmpty())return;
        var top=new ArrayList<>(groupRanks.get("3-0"));var second=groupRanks.get("3-1");
        String challenger;
        if(second.size()==2){knockout("SEED_3","FINAL_SEEDING",5,second.get(0),second.get(1));challenger=winner("SEED_3");}else if(second.size()==1)challenger=second.getFirst();else throw new IllegalStateException("LCP_SWISS_QUALIFIER_SHAPE");
        knockout("SEED_1","FINAL_SEEDING",5,top.getFirst(),challenger);
        boolean waiting=winner("SEED_1")==null;
        for(String key:List.of("1-3","2-3")){var tied=groupRanks.get(key);if(tied.size()==2){String id="ELIM_TIE_"+key;knockout(id,"FINAL_SEEDING",5,tied.get(0),tied.get(1));if(winner(id)==null)waiting=true;else {points.merge(winner(id),5,Integer::sum);var order=new ArrayList<>(regular);int first=Math.min(order.indexOf(winner(id)),order.indexOf(loser(id))),last=Math.max(order.indexOf(winner(id)),order.indexOf(loser(id)));order.set(first,winner(id));order.set(last,loser(id));regular=order;}}}
        if(waiting)return;
        var seeds=new ArrayList<String>();seeds.add(winner("SEED_1"));seeds.add(loser("SEED_1"));regular.stream().filter(t->!seeds.contains(t)&&groupRanks.entrySet().stream().filter(e->e.getKey().startsWith("3-")).anyMatch(e->e.getValue().contains(t))).forEach(seeds::add);
        if(seeds.size()!=4)throw new IllegalStateException("LCP_FOUR_PLAYOFF_TEAMS");playoffs=seeds;four("PO_",seeds,5);finish("PO_F");
    }
    private static String pair(String a,String b){return a.compareTo(b)<0?a+"|"+b:b+"|"+a;}
    private List<String> pairing(String scope,List<String> teams,BiPredicate<String,String> allowed){var left=new ArrayList<>(teams);left.sort(Comparator.comparing(t->CareerRosterStore.hash(in.seed()+"|"+scope+"|"+t)));var chosen=new ArrayList<String>();return search(left,chosen,allowed)?chosen:null;}
    private static boolean search(List<String> left,List<String> chosen,BiPredicate<String,String> allowed){if(left.isEmpty())return true;String a=left.removeFirst();for(int i=0;i<left.size();i++){String b=left.get(i);if(!allowed.test(a,b))continue;left.remove(i);chosen.add(a);chosen.add(b);if(search(left,chosen,allowed))return true;chosen.removeLast();chosen.removeLast();left.add(i,b);}left.addFirst(a);return false;}
}
