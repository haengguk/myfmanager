package com.lolfm.career;
import static com.lolfm.career.CareerPerformancePolicy.*;
import com.lolfm.domain.Position;
import java.math.BigDecimal;
import java.util.*;

/** GAME_POLICY V2: contribution ratios; confidence never supplies ranking or tie priority. */
public final class CareerPerformanceV2 {
    public static final String VERSION="CAREER_PERFORMANCE_V2";
    public record Support(String status,BigDecimal score,CareerTeamPlayStatistics.Observation observation) {}
    private record Role(double kp,int combat,int economy,int survival,int support) {}
    private static final Map<Position,Role> ROLES=Map.of(
        Position.TOP,new Role(.50,35,30,15,20),Position.JUNGLE,new Role(.65,35,15,15,35),
        Position.MID,new Role(.60,40,25,15,20),Position.ADC,new Role(.60,40,25,15,20),
        Position.SUPPORT,new Role(.70,30,10,20,40));
    public static Rating rate(CareerGameStatistics game,CareerGameStatistics.Player p) {
        var old=CareerPerformancePolicy.rate(game,p);
        if(!old.status().equals("COMPLETE"))return new Rating("INPUT_INCOMPLETE",null,null,null,null,null,VERSION,new Support("NOT_COLLECTED",null,null));
        var role=ROLES.get(p.position());
        var opponent=game.players().stream().filter(v->v.side()!=p.side()&&v.position()==p.position()).findFirst().orElseThrow();
        var ownGold=game.players().stream().filter(v->v.side()==p.side()).mapToLong(CareerGameStatistics.Player::gold).sum();
        var otherGold=game.players().stream().filter(v->v.side()!=p.side()).mapToLong(CareerGameStatistics.Player::gold).sum();
        var ownXp=game.players().stream().filter(v->v.side()==p.side()).mapToLong(CareerGameStatistics.Player::experience).sum();
        var otherXp=game.players().stream().filter(v->v.side()!=p.side()).mapToLong(CareerGameStatistics.Player::experience).sum();
        var gold=n(div(decimal(p.gold()),decimal(ownGold)).subtract(div(decimal(opponent.gold()),decimal(otherGold))),0,.1);
        var xp=n(div(decimal(p.experience()),decimal(ownXp)).subtract(div(decimal(opponent.experience()),decimal(otherXp))),0,.1);
        var economy=weighted(gold,.65).add(weighted(xp,.25));
        var cs=p.cs()+opponent.cs()==0?decimal(50):n(div(decimal(p.cs()),decimal((long)p.cs()+opponent.cs())),.5,.25);
        economy=p.position()==Position.SUPPORT?div(economy,decimal(.9)):economy.add(weighted(cs,.1));
        long kills=game.players().stream().filter(v->v.side()==p.side()).mapToLong(CareerGameStatistics.Player::kills).sum();
        long deaths=game.players().stream().filter(v->v.side()==p.side()).mapToLong(CareerGameStatistics.Player::deaths).sum();
        var combat=kills==0?decimal(50):n(div(decimal(p.kills()+p.assists()),decimal(kills)),role.kp,.40);
        Support support;
        if(game.teamPlay()==null)support=new Support("NOT_COLLECTED",null,null);
        else {
            game.teamPlay().validate(game);var o=game.teamPlay().observation(p);
            // Objective participation is orthogonal to KDA: a zero-kill attempt still counts once.
            // Roam success is an outcome rate, never added as another kill/assist or action-count bonus.
            BigDecimal score=null;
            if(o.objectiveOpportunities()>0)score=decimal(100).multiply(div(decimal(o.objectiveParticipations()),decimal(o.objectiveOpportunities())));
            if(o.roamAttempts()>0){var roam=decimal(50).add(decimal(50).multiply(div(decimal(o.roamWins()),decimal(o.roamAttempts()))));score=score==null?roam:weighted(score,.8).add(weighted(roam,.2));}
            support=new Support(score==null?"NO_OPPORTUNITY":"OBSERVED",score==null?null:fixed(score),o);
        }
        var involvement=combat;
        if(support.score()!=null)involvement=involvement.max(support.score());
        if(kills==0&&support.score()==null)involvement=decimal(50);
        var survival=deaths==0?decimal(100):decimal(100).subtract(n(div(decimal(p.deaths()),decimal(deaths)),.20,.20));
        if(survival.compareTo(decimal(50))>0)survival=decimal(50).add(survival.subtract(decimal(50)).multiply(clamp(div(involvement.subtract(decimal(50)),decimal(50)),BigDecimal.ONE)));
        int denominator=100-(support.score()==null?role.support:0);
        var total=combat.multiply(decimal(role.combat)).add(economy.multiply(decimal(role.economy))).add(survival.multiply(decimal(role.survival)));
        if(support.score()!=null)total=total.add(support.score().multiply(decimal(role.support)));
        return new Rating("COMPLETE",kills==0?"NO_OPPORTUNITY":"OBSERVED",fixed(combat),fixed(economy),fixed(survival),fixed(clamp(div(total,decimal(denominator)),decimal(100))),VERSION,support);
    }
    public static Period period(List<Sample> samples) {
        var confidence=CareerPerformancePolicy.period(samples);var n=confidence.effectiveSeries();
        var q=weighted(n,.25);var left=q;var sum=BigDecimal.ZERO;
        for(var s:samples.stream().sorted(Comparator.comparing(Sample::mean)).toList()){var w=s.participation().min(left);sum=sum.add(s.mean().multiply(w));left=left.subtract(w);if(left.signum()==0)break;}
        var mean=confidence.observedMean();var consistency=div(sum,q);var t=confidence.teamPerformance();
        return new Period(n,mean,confidence.adjustedMean(),fixed(consistency),t,
            fixed(weighted(mean,.85).add(weighted(consistency,.10)).add(weighted(t,.05))),
            fixed(weighted(mean,.80).add(weighted(consistency,.10)).add(weighted(t,.10))),
            fixed(weighted(mean,.90).add(weighted(t,.10))),VERSION);
    }
    public static boolean seriesEligible(int played,int total){return total>0&&played<=total&&played>0&&(total==1||played>=Math.max(2,(total+1)/2));}
    public static boolean eventEligible(int actualSeries,BigDecimal effective,BigDecimal opportunities){return actualSeries>=2&&opportunities!=null&&opportunities.signum()>0&&effective.compareTo(weighted(opportunities,.5))>=0;}
    private CareerPerformanceV2() {}
}
