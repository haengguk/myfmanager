package com.lolfm.career;

import com.lolfm.player.ExpandedPlayerCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.lolfm.career.CareerRosterStore.*;

/** Current reference provenance is not a replacement for the Career's persisted player authority. */
public final class CareerSaveCompatibility {
    public static final String POLICY="CAREER_SAVED_PLAYER_DIRECTORY_V1";
    public record View(String policyVersion,String dataSource,boolean sourceChanged,String managedTeamCode,
                       String managedTeamName,String directoryVersion,String status,String reasonCode,String message) {
        View(String policy,String source,boolean changed,String code,String name,String version){this(policy,source,changed,code,name,version,"SUPPORTED",null,null);}
        static View unsupported(CareerRelationalStore.CareerRow row,CareerException error){return new View(POLICY,"UNAVAILABLE",false,row.managedTeamCode(),row.managedTeamCode(),null,"UNSUPPORTED",error.type().name(),error.clientMessage());}
    }
    static boolean unsupported(CareerException error) {return java.util.Set.of(CareerException.Type.SAVE_COMPATIBILITY_DATA_MISSING,CareerException.Type.SAVE_COMPATIBILITY_VERSION_UNSUPPORTED,CareerException.Type.SAVE_COMPATIBILITY_ORGANIZATION_UNSUPPORTED).contains(error.type());}
    static void requireOriginalReference(JdbcTemplate jdbc,String career) {
        var current=com.lolfm.reference.TeamPlayerInformationCatalog.loadDefault();
        Boolean same=jdbc.queryForObject("SELECT reference_catalog_version,reference_catalog_hash,managed_team_code FROM career_save WHERE career_id=?",(r,n)->current.provenance().catalogVersion().equals(r.getString(1))&&current.provenance().catalogHash().equals(r.getString(2))&&current.findTeam(r.getString(3)).isPresent(),career);
        if(!Boolean.TRUE.equals(same))throw CareerException.compatibility(CareerException.Type.SAVE_COMPATIBILITY_DATA_MISSING,"이 저장에 없는 선수 원본은 생성 당시 참고 데이터가 있어야 복구할 수 있습니다. 현재 기본 데이터를 과거 자료로 가져오지 않습니다.");
    }
    static boolean directoryVersionSupported(JdbcTemplate jdbc,String career) {
        return jdbc.query("SELECT directory_version FROM career_player_directory WHERE career_id=?",(r,n)->r.getString(1),career)
                .stream().allMatch(ExpandedPlayerCatalog.VERSION::equals);
    }
    static void requireDirectoryVersion(String version) {
        if(!ExpandedPlayerCatalog.VERSION.equals(version))throw CareerException.compatibility(CareerException.Type.SAVE_COMPATIBILITY_VERSION_UNSUPPORTED,
                "지원하지 않는 저장 선수 명부 형식입니다: "+version+". 해당 형식을 지원하는 버전에서 이 저장을 열어야 합니다.");
    }
    private CareerSaveCompatibility() {}
    static View inspect(JdbcTemplate jdbc,CareerRelationalStore.CareerRow row,boolean sameReference) {
        var versions=jdbc.query("SELECT directory_version FROM career_player_directory WHERE career_id=?",(r,n)->r.getString(1),row.careerId());
        if(versions.isEmpty()) {
            if(!sameReference)throw CareerException.compatibility(CareerException.Type.SAVE_COMPATIBILITY_DATA_MISSING,
                    "이 저장에는 선수·조직 원본이 보존되지 않았습니다. 생성 당시 참고 데이터로 복구해야 하며 최신 기본 데이터로 대체하지 않습니다.");
            return new View(POLICY,"LEGACY_MATCHING_REFERENCE",false,row.managedTeamCode(),row.managedTeamCode(),null);
        }
        String version=versions.getFirst();
        requireDirectoryVersion(version);
        try {
            var source=sourceDirectory(jdbc,row.careerId());
            source.players().forEach((id,p)->{
                if(!id.equals(p.playerId())||!id.equals(p.gameplay().playerId())||p.position()!=p.gameplay().position())throw new IllegalStateException("SAVED_PLAYER_IDENTITY");
            });
            source.organizations().forEach((id,o)->{if(!id.equals(o.organizationId()))throw new IllegalStateException("SAVED_ORGANIZATION_IDENTITY");});
            var team=source.organizations().get("LCK:"+row.managedTeamCode());
            if(team==null||!team.organizationId().equals(team.competitiveTeam())||!team.kind().equals("CLUB"))throw CareerException.compatibility(CareerException.Type.SAVE_COMPATIBILITY_ORGANIZATION_UNSUPPORTED,
                    "저장된 관리 구단 정의가 없어 현재 구단과 연결할 수 없습니다. 원래 조직 정의가 보존된 저장 또는 해당 형식을 지원하는 버전이 필요합니다.");
            int year=activeYear(jdbc,row.careerId());var roster=saved(jdbc,row.careerId(),year);
            if(roster==null)throw CareerException.compatibility(CareerException.Type.SAVE_COMPATIBILITY_DATA_MISSING,"현재 시즌의 저장 선수단이 없습니다. 원래 시즌 명부가 보존된 저장을 복구해야 합니다.");
            var current=directory(jdbc,row.careerId());validate(roster.state(),current);
            var snapshots=jdbc.query("SELECT roster_json,roster_hash FROM career_season WHERE career_id=? AND season_year=?",(r,n)->{
                String json=r.getString(1);if(json==null)return false;
                var frozen=CompetitionRosterSnapshot.decode(json);if(!frozen.identity().equals(r.getString(2)))throw new IllegalStateException("SAVED_SEASON_ROSTER_HASH");
                for(var savedTeam:frozen.teams().values())for(var player:savedTeam.players()) {
                    var definition=current.players().get(player.playerId());
                    if(definition==null||definition.position()!=player.position())throw new IllegalStateException("SAVED_SEASON_PLAYER_IDENTITY");
                }
                return true;
            },row.careerId(),year);
            if(snapshots.size()!=1||!snapshots.getFirst())throw CareerException.compatibility(CareerException.Type.SAVE_COMPATIBILITY_DATA_MISSING,"현재 시즌의 고정 선수 입력이 없습니다. 생성 당시 자료와 일치하는 보존 입력이 필요합니다.");
            return new View(POLICY,"SAVED_CAREER",!sameReference,row.managedTeamCode(),team.displayName(),version);
        } catch(CareerException known){throw known;}
        catch(IllegalArgumentException|IllegalStateException invalid){throw CareerException.resourceIntegrity();}
    }
}
