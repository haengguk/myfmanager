package com.lolfm.career;

import com.lolfm.domain.PlayerSkill;
import com.lolfm.domain.Position;
import com.lolfm.player.ExpandedPlayerCatalog;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.lolfm.career.CareerDevelopmentState.*;
import static com.lolfm.career.CareerDevelopmentPolicy.*;

class CareerDevelopmentPolicyTest {
    final LocalDate date=LocalDate.of(2027,1,1);
    static ExpandedPlayerCatalog.Definition definition(int value,Integer pa,int age) {
        var r=new EnumMap<PlayerSkill,Integer>(PlayerSkill.class);PlayerSkill.forPosition(Position.TOP).forEach(k->r.put(k,value));
        return new ExpandedPlayerCatalog.Definition("player-test","합성 선수",Position.TOP,new CompetitionRosterSnapshot.Starter("player-test","합성 선수",Position.TOP,r,List.of(new CompetitionRosterSnapshot.Proficiency("aatrox",Position.TOP,14))),false,"LCK:T1","LCK:T1","FIRST_TEAM",null,
            "{\"personal\":{\"birthDate\":\""+(2027-age)+"-01-01\"},\"abilityMetadata\":{\"potentialAbility\":"+pa+"}}");
    }
    @ParameterizedTest @NullSource @ValueSource(ints={1,148,149,190,200})
    void initialProjectionAndStrictCeiling(Integer pa) {
        var d=definition(15,pa,19);var p=initial(d);assertThat(p.internalRatings().values()).containsOnly(15000);
        for(int i=0;i<1000;i++)p=grow(p,d,metadata(d),date,100,1000,1000,DEFAULT);
        if(pa==null||ceiling(pa)<180000)assertThat(sum(p)).isEqualTo(180000);else assertThat(sum(p)).isLessThanOrEqualTo(ceiling(pa));
        if(pa==null||ceiling(pa)<=sum(p))assertThat(p.remainder()).isZero();
    }
    @ParameterizedTest @ValueSource(ints={0,1,11,12})
    void positiveHeadroomAccumulatesBelowFormerPrecisionBoundary(int remaining) {
        var d=definition(20,200,19);var ratings=new EnumMap<PlayerSkill,Integer>(initial(d).internalRatings());ratings.put(PlayerSkill.MECHANICS,20000-remaining);
        var p=new Player(ratings,Map.of(),12345,Map.of(),Map.of(),0,null,null);
        // An old JSON row has no fine remainder, but keeps exactly the same coarse units.
        String legacy=CareerRosterStore.write(p).replace(",\"growthSubRemainder\":0", "");
        var restored=CareerRosterStore.read(legacy,Player.class);assertThat(restored.remainder()).isEqualTo(12345);assertThat(restored.growthSubRemainder()).isZero();
        for(int i=0;i<12000;i++) {
            p=grow(p,d,metadata(d),date,1,1000,1000,DEFAULT);
            restored=grow(restored,d,metadata(d),date,1,1000,1000,DEFAULT);
            if(i==37)restored=CareerRosterStore.read(CareerRosterStore.write(restored),Player.class);
        }
        assertThat(restored).isEqualTo(p);assertThat(sum(p)).isLessThanOrEqualTo(ceiling(200));
        if(remaining>0)assertThat(sum(p)).isGreaterThan(ceiling(200)-remaining);
        else {assertThat(p.remainder()).isZero();assertThat(p.growthSubRemainder()).isZero();}
    }
    @Test void balancedBudgetAndRemainderSurviveRestartAndSaturation() {
        var d=definition(15,200,19);var p=initial(d);
        for(int i=0;i<150;i++)p=grow(p,d,metadata(d),date,10,1000,1000,DEFAULT);
        assertThat(sum(p)).isEqualTo(181500);assertThat(p.internalRatings().get(PlayerSkill.MECHANICS)).isEqualTo(16000);assertThat(p.internalRatings().get(PlayerSkill.DECISION_MAKING)).isEqualTo(15500);
        var restored=CareerRosterStore.read(CareerRosterStore.write(p),Player.class);
        assertThat(grow(restored,d,metadata(d),date,10,1000,1000,DEFAULT)).isEqualTo(grow(p,d,metadata(d),date,10,1000,1000,DEFAULT));
        var ratings=new EnumMap<PlayerSkill,Integer>(p.internalRatings());ratings.put(PlayerSkill.MECHANICS,20000);
        var saturated=new Player(ratings,Map.of(),0,Map.of(),Map.of(),0,null,null);
        var n=grow(saturated,d,metadata(d),date,2000,1000,1000,DEFAULT);
        assertThat(n.internalRatings().get(PlayerSkill.MECHANICS)).isEqualTo(20000);assertThat(sum(n)-sum(saturated)).isEqualTo(2000);
        var low=definition(15,190,26);var q=initial(low);for(int i=0;i<10;i++)q=grow(q,low,metadata(low),date,1,1000,1000,DEFAULT);
        assertThat(sum(q)).isEqualTo(180003);
    }
    @Test void groupFocusInterleavesItsSecondaryShareWithinFirstTenPoints() {
        var d=definition(15,200,19);var p=grow(initial(d),d,metadata(d),date,10000,1000,1000,new Plan(Intensity.NORMAL,Focus.COMMON_SKILLS,null,List.of()));
        int common=PlayerSkill.forPosition(Position.SUPPORT).stream().filter(k->k.appliesTo(Position.TOP)).mapToInt(k->p.internalRatings().get(k)-15000).sum();
        assertThat(common).isEqualTo(7000);assertThat(sum(p)-180000-common).isEqualTo(3000);
    }
    @Test void masterySharesOneBudgetAndDampsAtHighLevel() {
        var d=definition(15,null,19);var p=initial(d);
        var one=day(p,d,metadata(d),date,new Plan(Intensity.NORMAL,Focus.CHAMPION_FOCUS,null,List.of("aatrox")),false);
        var two=day(p,d,metadata(d),date,new Plan(Intensity.NORMAL,Focus.CHAMPION_FOCUS,null,List.of("aatrox","garen")),false);
        assertThat(one.internalProficiencies().get("aatrox|TOP")-14000).isEqualTo(40);
        assertThat(two.internalProficiencies().get("aatrox|TOP")-14000+two.internalProficiencies().get("garen|TOP")-14000).isEqualTo(40);
        var high=new Player(p.internalRatings(),Map.of("aatrox|TOP",19000),0,Map.of(),Map.of(),0,null,null);
        assertThat(proficiency(high,"aatrox|TOP",80000,1000).internalProficiencies().get("aatrox|TOP")-19000).isBetween(8,14);
        var cap=new Player(p.internalRatings(),Map.of("aatrox|TOP",20000),0,Map.of(),Map.of(),0,null,null);
        assertThat(proficiency(cap,"aatrox|TOP",80000,1000)).isEqualTo(cap);
        assertThat(sum(one)).isEqualTo(sum(p)); // Missing PA holds ability only.
        assertThatThrownBy(()->validate(new Plan(Intensity.NORMAL,Focus.CHAMPION_FOCUS,null,List.of("garen","garen")),d,Set.of("garen|TOP"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->validate(new Plan(Intensity.NORMAL,Focus.SPECIFIC_SKILL,PlayerSkill.PATHING,List.of()),d,Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->validate(new Plan(Intensity.NORMAL,Focus.CHAMPION_FOCUS,null,List.of("unknown")),d,Set.of())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void scheduledPlanOwnershipMatchDayAndRecovery() {
        var d=definition(15,200,19);var p=initial(d);var recovery=new Plan(Intensity.RECOVERY,Focus.BALANCED,null,List.of());
        var s=new Schedule("LCK:T1",DEFAULT,date,recovery,date.plusDays(1),false);
        assertThat(activate(s,date,"LCK:T1").current()).isEqualTo(DEFAULT);assertThat(activate(s,date.plusDays(1),"LCK:T1").current()).isEqualTo(recovery);assertThat(activate(s,date,"LCK:KT")).isNull();
        var played=game(p,d,metadata(d),date,"aatrox");var twice=game(played,d,metadata(d),date,"garen");
        assertThat(day(twice,d,metadata(d),date,DEFAULT,false).internalRatings()).isEqualTo(twice.internalRatings());assertThat(day(twice,d,metadata(d),date,DEFAULT,false).fatigue()).isEqualTo(60);
        var tired=copy(p,900,null,null);for(int i=0;i<4;i++)tired=day(tired,d,metadata(d),date.plusDays(i),recovery,false);
        assertThat(tired.fatigue()).isEqualTo(100);assertThat(tired.internalRatings()).isEqualTo(p.internalRatings());
        var base=new CareerRosterStore.Directory(Map.of(d.playerId(),d),Map.of());
        var clean=new CareerDevelopmentEngine(base,CareerDevelopmentEngine.initial(base,date));
        var dirtyState=clean.state();var dirty=new CareerDevelopmentEngine(base,new CareerDevelopmentState(dirtyState.policyVersion(),dirtyState.initializationVersion(),date,date,Map.of(d.playerId(),copy(p,1000,null,null)),Map.of(),List.of(),Map.of()));
        assertThat(clean.directory()).isEqualTo(dirty.directory()); // Fatigue has exactly zero match input effect.
    }
    @Test void aiDoesNotInspectPotentialAndBirthdayUsesGameDate() {
        var a=definition(15,150,20);var b=definition(15,200,20);var p=initial(a);
        assertThat(ai(a,metadata(a),p,date,5)).isEqualTo(ai(b,metadata(b),p,date,5));
        assertThat(age(metadata(a),date.minusDays(1))).isEqualTo(19);assertThat(age(metadata(a),date)).isEqualTo(20);
        assertThat(age(new Metadata(null,null),date)).isEqualTo(23);
    }
    @Test void representativeYearAndWeeklyCalculationsWithoutMatchEngine() {
        for(int[] c:List.of(new int[]{19,100,190},new int[]{19,0,190},new int[]{23,100,190},new int[]{26,100,190},new int[]{29,100,190},new int[]{19,100,149},new int[]{19,100,0})) {
            var d=definition(15,c[2]==0?null:c[2],c[0]);var p=initial(d);
            for(int i=0;i<365;i++){var day=date.plusDays(i);if(i%3==0&&i/3<c[1])p=game(p,d,metadata(d),day,"aatrox");p=day(p,d,metadata(d),day,DEFAULT,false);}
            var displayed=new EnumMap<PlayerSkill,Integer>(PlayerSkill.class);p.internalRatings().forEach((k,v)->displayed.put(k,v/1000));
            System.out.println("DEVELOPMENT_YEAR "+Arrays.toString(c)+" ratings="+p.internalRatings()+" CA="+com.lolfm.player.PlayerAbilityPolicy.currentAbility(displayed)+" proficiency="+p.internalProficiencies()+" fatigue="+p.fatigue());
        }
        for(int schedule=0;schedule<4;schedule++) {
            var d=definition(15,190,19);var p=initial(d);int peak=0;
            for(int i=0;i<28;i++) {
                var day=date.plusDays(i);int games=schedule==0&&i%7==0?3:schedule==1&&(i%7==0||i%7==3)?3:schedule==3&&i<3?5:0;
                for(int g=0;g<games;g++)p=game(p,d,metadata(d),day,"aatrox");
                var intensity=schedule==2?Intensity.INTENSIVE:schedule==3&&i>=3&&i<7?Intensity.RECOVERY:Intensity.NORMAL;
                peak=Math.max(peak,Math.min(1000,p.fatigue()+(games==0?load(intensity):0)));p=day(p,d,metadata(d),day,new Plan(intensity,Focus.BALANCED,null,List.of()),false);
            }
            System.out.println("FATIGUE_MODEL schedule="+schedule+" peak="+peak+" end="+p.fatigue());
        }
    }
}
