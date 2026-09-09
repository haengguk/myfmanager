package com.lolfm.career;

import com.lolfm.domain.Position;
import java.math.*;
import java.util.*;

/** Initial GAME_POLICY, not a reconstruction of official votes or calibrated esports efficiency. */
public final class CareerPerformancePolicy {
    public static final String VERSION="CAREER_PERFORMANCE_V1";
    public static final int SCALE=12, WIN_BONUS=3;
    public static String rookieEligible(int sets,Integer firstYear,int year,boolean foreign,boolean prior,boolean complete){if(sets<26||firstYear!=null&&year-firstYear>=2||foreign||prior)return "FALSE";return complete&&firstYear!=null?"TRUE":"UNKNOWN";}
    private static final MathContext MC=new MathContext(24,RoundingMode.HALF_UP);
    private record Role(double kp,double involvement,double deaths,int combat,int economy,int survival) {}
    private static final Map<Position,Role> ROLES=Map.of(
            Position.TOP,new Role(.50,.30,.12,55,30,15),Position.JUNGLE,new Role(.65,.40,.15,65,15,20),
            Position.MID,new Role(.60,.42,.10,60,25,15),Position.ADC,new Role(.60,.42,.10,60,25,15),
            Position.SUPPORT,new Role(.70,.48,.16,70,10,20));
    public record Rating(String status,String kpStatus,BigDecimal combat,BigDecimal economy,BigDecimal survival,BigDecimal rating,String version) {
        static Rating missing(){return new Rating("INPUT_INCOMPLETE",null,null,null,null,null,VERSION);}
    }
    public record Sample(BigDecimal mean,BigDecimal participation,boolean won,int sets) {}
    public record Period(BigDecimal effectiveSeries,BigDecimal observedMean,BigDecimal adjustedMean,BigDecimal consistency,BigDecimal teamPerformance,BigDecimal allPro,BigDecimal regularMvp,BigDecimal tournamentMvp) {}
    public static BigDecimal decimal(double value){return BigDecimal.valueOf(value);}
    static BigDecimal fixed(BigDecimal n){return n.setScale(SCALE,RoundingMode.HALF_UP);}
    static BigDecimal div(BigDecimal a,BigDecimal b){return a.divide(b,MC);}
    static BigDecimal clamp(BigDecimal x,BigDecimal max){return x.max(BigDecimal.ZERO).min(max);}
    static BigDecimal n(BigDecimal x,double baseline,double spread){return clamp(decimal(50).add(div(x.subtract(decimal(baseline)),decimal(spread)).multiply(decimal(50))),decimal(100));}
    static BigDecimal weighted(BigDecimal a,double weight){return a.multiply(decimal(weight),MC);}
    public static Rating rate(CareerGameStatistics game,CareerGameStatistics.Player p) {
        if(game.seconds()<=0||game.players().size()!=10||game.players().stream().anyMatch(v->v.kills()<0||v.deaths()<0||v.assists()<0||v.cs()<0||v.gold()<0||v.experience()<0))return Rating.missing();
        var opponent=game.players().stream().filter(v->v.side()!=p.side()&&v.position()==p.position()).toList();
        if(opponent.size()!=1)return Rating.missing();
        long gold=0,otherGold=0,xp=0,otherXp=0,kills=0;
        for(var v:game.players())if(v.side()==p.side()){gold+=v.gold();xp+=v.experience();kills+=v.kills();}else{otherGold+=v.gold();otherXp+=v.experience();}
        if(gold<=0||otherGold<=0||xp<=0||otherXp<=0)return Rating.missing();
        var r=ROLES.get(p.position());var o=opponent.getFirst();var minutes=div(decimal(game.seconds()),decimal(60));
        var kp=kills==0?decimal(50):n(div(decimal(p.kills()+p.assists()),decimal(kills)),r.kp,.40);
        var c=weighted(kp,.70).add(weighted(n(div(decimal(p.kills()+p.assists()),minutes),r.involvement,.60),.30));
        var g=n(div(decimal(p.gold()),decimal(gold)).subtract(div(decimal(o.gold()),decimal(otherGold))),0,.10);
        var x=n(div(decimal(p.experience()),decimal(xp)).subtract(div(decimal(o.experience()),decimal(otherXp))),0,.10);
        var e=weighted(g,.65).add(weighted(x,.25));
        e=p.position()==Position.SUPPORT?div(e,decimal(.90)):e.add(weighted(n(div(decimal(p.cs()-o.cs()),minutes),0,4),.10));
        var d=decimal(100).subtract(n(div(decimal(p.deaths()),minutes),r.deaths,.18));
        if(d.compareTo(decimal(50))>0)d=decimal(50).add(d.subtract(decimal(50)).multiply(clamp(div(c.subtract(decimal(50)),decimal(25)),BigDecimal.ONE),MC));
        return new Rating("COMPLETE",kills==0?"NO_OPPORTUNITY":"OBSERVED",fixed(c),fixed(e),fixed(d),fixed(div(c.multiply(decimal(r.combat)).add(e.multiply(decimal(r.economy))).add(d.multiply(decimal(r.survival))),decimal(100))),VERSION);
    }
    public static BigDecimal series(List<BigDecimal> ratings,int totalSets) {
        if(totalSets<=0||ratings.isEmpty()||ratings.size()>totalSets)throw new IllegalArgumentException("SERIES_PARTICIPATION_INVALID");
        var sum=ratings.stream().map(r->r.subtract(decimal(50))).reduce(BigDecimal.ZERO,BigDecimal::add);
        return fixed(decimal(50).add(div(sum,decimal(totalSets))));
    }
    public static Period period(List<Sample> samples) {
        if(samples.isEmpty()||samples.stream().anyMatch(s->s.participation().signum()<=0||s.participation().compareTo(BigDecimal.ONE)>0))throw new IllegalArgumentException("PERIOD_PARTICIPATION_INVALID");
        var n=samples.stream().map(Sample::participation).reduce(BigDecimal.ZERO,BigDecimal::add);
        var sum=samples.stream().map(s->s.mean().multiply(s.participation())).reduce(BigDecimal.ZERO,BigDecimal::add);
        var mean=div(sum,n);var a=div(sum.add(decimal(300)),n.add(decimal(6)));
        var q=weighted(n,.25);var left=q;var lower=BigDecimal.ZERO;
        for(var s:samples.stream().sorted(Comparator.comparing(Sample::mean)).toList()){var w=s.participation().min(left);lower=lower.add(s.mean().multiply(w));left=left.subtract(w);if(left.signum()==0)break;}
        var consistency=div(lower.add(decimal(100)),q.add(decimal(2)));
        var wins=samples.stream().filter(Sample::won).map(Sample::participation).reduce(BigDecimal.ZERO,BigDecimal::add);
        var t=div(wins.multiply(decimal(100)),n);
        return new Period(fixed(n),fixed(mean),fixed(a),fixed(consistency),fixed(t),fixed(weighted(a,.85).add(weighted(consistency,.10)).add(weighted(t,.05))),fixed(weighted(a,.80).add(weighted(consistency,.10)).add(weighted(t,.10))),fixed(weighted(a,.90).add(weighted(t,.10))));
    }
    public static boolean eligible(String scope,int sets,BigDecimal effectiveSeries,BigDecimal scheduledOpportunities){
        if(sets<=0)return false;
        if(scope.equals("LCK_REGULAR"))return sets>=42;
        if(scope.startsWith("LEC_"))return sets>=6;
        return scheduledOpportunities!=null&&effectiveSeries.compareTo(weighted(scheduledOpportunities,.5))>=0;
    }
    private CareerPerformancePolicy() {}
}
