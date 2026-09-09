package com.lolfm.career;
import com.fasterxml.jackson.databind.*;
import com.lolfm.domain.Position;
import java.util.*;
import static com.lolfm.career.CareerRosterStore.*;

/** Explicit adoption over an unchanged research pack. Immutable reference policy. */
public final class CareerAwardPolicy {
    public static final String VERSION="CAREER_AWARD_ADOPTION_V1";
    public record Definition(String id,String scope,String name,String category,String status,String period,String eligibility,List<Integer> tiers,JsonNode source,JsonNode prizeLink) {}
    public static final List<Definition> DEFINITIONS;
    public static final String SOURCE_HASH;
    static {
        try(var input=CareerAwardPolicy.class.getResourceAsStream("/career/individual-awards-v1.json")) {
            String json=new String(Objects.requireNonNull(input).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);SOURCE_HASH=hash(json);
            var mapper=new ObjectMapper();var tree=mapper.readTree(json);var list=new ArrayList<Definition>();for(var d:tree.get("definitions"))list.add(mapper.treeToValue(d,Definition.class));DEFINITIONS=List.copyOf(list);
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    public static String scope(String event){return Set.of("LCK_REGULAR_R1_R2","LCK_REGULAR_R3_R4").contains(event)?"LCK_REGULAR":event;}
    public static boolean regular(String event,String stage){return event.equals("LCK_REGULAR_R1_R2")&&stage.equals("R1_R2")||event.equals("LCK_REGULAR_R3_R4")&&stage.equals("LEGEND_RISE")||stage.equals("REGULAR")||event.equals("LCK_CL")&&stage.equals("CL_REGULAR");}
    public static boolean inScope(String scope,String event,String stage){return scope.equals(scope(event))&&regular(event,stage);}
    static String instance(String career,int year,String definition,String scope,String occurrence){return hash(write(List.of(VERSION,career,year,definition,scope,occurrence)));}
    /** Length-delimited JSON fields prevent seed/identity concatenation collisions. No gameplay Random. */
    public static String tie(long seed,String instance,String player){return hash(write(List.of(seed,instance,player)));}
    public static List<Position> positions(){return List.of(Position.TOP,Position.JUNGLE,Position.MID,Position.ADC,Position.SUPPORT);}
    private CareerAwardPolicy() {}
}
