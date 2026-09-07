package com.lolfm.career;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lolfm.player.ExpandedPlayerCatalog.Definition;
import java.util.*;
import static com.lolfm.career.CareerRosterStore.*;

/** Current display metadata only. Frozen match snapshots retain their original names. */
public final class CareerGeneratedNames {
    public static final String POLICY="CAREER_GENERATED_NAMES_V1";
    private CareerGeneratedNames(){}
    private static int pick(long seed,String id,String purpose,int size){return (int)Long.remainderUnsigned(Long.parseUnsignedLong(hash(POLICY+'|'+seed+'|'+id+'|'+purpose).substring(0,16),16),size);}
    private static String part(long seed,String id,String purpose,String pool){var parts=pool.split(",");return parts[pick(seed,id,purpose,parts.length)];}
    static boolean placeholder(Definition d) {
        var n=read(d.detailsJson(),ObjectNode.class);var g=n.path("generated");
        if(!"GENERATED".equals(g.path("source").asText())||g.has("namePolicyVersion")||!n.path("personal").path("legalName").asText("").isBlank())return false;
        String sequence=d.playerId().substring(d.playerId().lastIndexOf('-')+1);
        if(!sequence.matches("[0-9]{3,}"))return false;
        String expected=n.path("leagueContext").asText()+" 신인 "+g.path("intakeYear").asInt()+"-"+Integer.parseInt(sequence);
        return expected.equals(d.nickname())&&expected.equals(n.path("name").asText())&&expected.equals(n.path("nickname").asText())&&expected.equals(d.gameplay().nickname());
    }
    public static Definition assign(Definition d,long seed,Set<String> occupied) {
        var n=read(d.detailsJson(),ObjectNode.class);String id=d.playerId(),region=n.path("leagueContext").asText();String nickname=null;
        for(int attempt=0;attempt<64;attempt++) {
            String a=part(seed,id,"NICK_A_"+attempt,"Aven,Vey,Orin,Syl,Nyx,Elar,Riveno,Zeno,Kael,Vel,Ardo,Lumi,Seon,Yun,Hae,Do,Ren,Lian,Kai,Min");
            String b=part(seed,id,"NICK_B_"+attempt,"ra,on,ix,el,io,en,us,an,or,is,ve,ro");String candidate=a+b;
            if(attempt>=32)candidate+=hash(POLICY+'|'+id+'|'+attempt).substring(0,5);
            if(occupied.add(candidate.toLowerCase(Locale.ROOT))){nickname=candidate;break;}
        }
        if(nickname==null)throw new IllegalStateException("GENERATED_NAME_COLLISION_LIMIT");
        String surname,given;
        switch(region) {
            case "LCK"->{surname=part(seed,id,"FAMILY","김,이,박,정,최,강,윤,장,임,서");given=part(seed,id,"GIVEN_A","도,시,준,민,현,우,태,서")+part(seed,id,"GIVEN_B","윤,호,준,재,우,건,진,현");}
            case "LPL"->{surname=part(seed,id,"FAMILY","Lin,Chen,Zhou,Xu,Shen,Guo,He,Tang")+" ";given=part(seed,id,"GIVEN_A","Yu,Zi,Ming,Hao,Jia,Yun")+part(seed,id,"GIVEN_B","chen,wen,yu,hao,xuan,lin");}
            case "LCP"->{surname=part(seed,id,"FAMILY","Nguyen,Tran,Le,Pham,Hoang,Vu")+" ";given=part(seed,id,"GIVEN_A","Minh,Quang,Duc,Thanh,Hoang")+" "+part(seed,id,"GIVEN_B","Khai,Long,An,Son,Huy,Phong");}
            case "CBLOL"->{given=part(seed,id,"GIVEN","Caio,Ivo,Lucas,Rafael,Tiago,Bruno");surname=" "+part(seed,id,"FAMILY","Alves,Costa,Moraes,Rocha,Lima,Prado");var swap=surname;surname=given;given=swap;}
            case "LEC"->{surname=part(seed,id,"GIVEN","Lucien,Adrien,Noel,Remi,Theo,Florian")+" ";given=part(seed,id,"FAMILY","Morel,Laurent,Chevalier,Fontaine,Roux,Garnier");}
            default->{surname=part(seed,id,"GIVEN","Evan,Adrian,Noah,Julian,Miles,Owen")+" ";given=part(seed,id,"FAMILY","Reed,Hayes,Brooks,Rowan,Mercer,Ellis");}
        }
        n.put("name",nickname);n.put("nickname",nickname);((ObjectNode)n.withObject("/personal")).put("legalName",surname+given).put("nameSource","GENERATED_FICTIONAL_NAME");
        ((ObjectNode)n.get("generated")).put("namePolicyVersion",POLICY).put("nameStatus","GENERATED_NAME");
        var p=d.gameplay();return new Definition(id,nickname,d.position(),new CompetitionRosterSnapshot.Starter(id,nickname,d.position(),p.ratings(),p.proficiencies()),d.provisional(),d.initialOrganizationId(),d.initialOwnerTeam(),d.initialSquad(),d.eligibilityReason(),n.toString());
    }
}
