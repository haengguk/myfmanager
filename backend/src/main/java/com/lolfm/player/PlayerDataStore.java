package com.lolfm.player;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lolfm.career.CareerIdentity;
import com.lolfm.career.CareerRosterStore;
import com.lolfm.domain.PlayerRatings;
import com.lolfm.domain.PlayerSkill;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public final class PlayerDataStore {
    public static final String APPLICATION = "NEW_CAREERS_ONLY";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    private final ExpandedPlayerCatalog catalog;
    private final Map<String,Integer> potentials;
    private final String source;

    public PlayerDataStore(JdbcTemplate jdbc, PlatformTransactionManager manager, ObjectMapper json, ExpandedPlayerCatalog catalog) {
        this.jdbc=jdbc; this.transactions=new TransactionTemplate(manager); this.json=json; this.catalog=catalog;
        try (var stream=getClass().getResourceAsStream("/players/player-potential-ability-v2.json")) {
            var root=json.readTree(Objects.requireNonNull(stream));
            if (!"player-potential-ability-v2".equals(root.path("version").asText())) throw new IllegalStateException("PA_VERSION");
            source=root.path("sourceVersion").asText();
            var values=new TreeMap<String,Integer>();
            for (var p:root.path("players")) {
                String id=p.path("playerId").asText(); var pa=p.path("potentialAbility");
                if (!pa.isInt() || pa.intValue()<1 || pa.intValue()>200 || values.putIfAbsent(id,pa.intValue())!=null)
                    throw new IllegalStateException("PA_ID_OR_RANGE");
            }
            if (!values.keySet().equals(catalog.players().keySet())) throw new IllegalStateException("PA_POPULATION_MISMATCH");
            potentials=Map.copyOf(values);
        } catch (java.io.IOException e) { throw new IllegalStateException("PA_LOAD",e); }
    }
    public record Player(ExpandedPlayerCatalog.Definition definition, int currentAbility, Integer potentialAbility, long revision) {}
    public record View(String applicationPolicy, String caPolicy, String paPolicy, long revision, List<Player> players) {}
    public record Edit(String playerId, Map<PlayerSkill,Integer> ratings, Integer potentialAbility, Long expectedRevision, String clientCommandId) {}
    public record Receipt(String clientCommandId, String playerId, long revision, String applicationPolicy, Player player) {}
    public record Change(boolean replayed, Receipt receipt) {}
    private record Override(String playerId, Map<PlayerSkill,Integer> ratings, Integer potential, long revision) {}
    private long lock() { return jdbc.queryForObject("SELECT revision FROM player_data_revision WHERE id=1 FOR UPDATE",Long.class); }
    private Map<String,Override> overrides() {
        var rows=new TreeMap<String,Override>();
        jdbc.query("SELECT player_id,ratings_json,potential_ability,revision FROM player_data_override",(org.springframework.jdbc.core.RowCallbackHandler) r -> {
            try { rows.put(r.getString(1),new Override(r.getString(1),json.readValue(r.getString(2),new TypeReference<Map<PlayerSkill,Integer>>(){}),r.getObject(3,Integer.class),r.getLong(4))); }
            catch (java.io.IOException e) { throw new IllegalStateException("PLAYER_EDIT_STORAGE",e); }
        });
        return rows;
    }
    private Player player(ExpandedPlayerCatalog.Definition base, Override override) {
        var ratings=override==null?base.gameplay().ratings():override.ratings();
        Integer pa=override==null?potentials.get(base.playerId()):override.potential();
        long revision=override==null?0:override.revision();
        var definition=PlayerAbilityPolicy.apply(json,base,ratings,pa,revision,override==null?source:"USER_EDITOR");
        return new Player(definition,PlayerAbilityPolicy.currentAbility(ratings),pa,revision);
    }
    public View view() {
        return transactions.execute(ignored -> {
            long revision=lock(); var edits=overrides();
            return new View(APPLICATION,PlayerAbilityPolicy.VERSION,PlayerAbilityPolicy.PA_POLICY,revision,
                    catalog.players().values().stream().sorted(Comparator.comparing(ExpandedPlayerCatalog.Definition::playerId))
                            .map(p->player(p,edits.get(p.playerId()))).toList());
        });
    }
    /** Called inside new-Career creation's transaction. No existing Career is ever reimported. */
    public Map<String,ExpandedPlayerCatalog.Definition> snapshot() {
        var result=new TreeMap<String,ExpandedPlayerCatalog.Definition>();
        view().players().forEach(p->result.put(p.definition().playerId(),p.definition()));
        return result;
    }
    public Change edit(Edit request) {
        if (request==null || request.expectedRevision()==null || request.expectedRevision()<0 || !catalog.players().containsKey(request.playerId()))
            throw bad("선수 ID와 편집 버전을 확인해 주세요.");
        String command;
        try {
            command=CareerIdentity.canonicalCommandId(request.clientCommandId());
            new PlayerRatings(catalog.players().get(request.playerId()).position(),request.ratings());
            if (request.potentialAbility()!=null && (request.potentialAbility()<1 || request.potentialAbility()>200)) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) { throw bad("해당 포지션의 능력치 12개(정수 1~20), PA(정수 1~200 또는 미입력), 요청 UUID가 필요합니다."); }
        String payload=CareerRosterStore.hash(CareerRosterStore.write(request));
        return transactions.execute(ignored -> {
            long revision=lock();
            var old=jdbc.query("SELECT payload_hash,receipt_json FROM player_data_command WHERE command_id=?",(r,n)-> {
                if (!payload.equals(r.getString(1))) throw conflict("같은 요청 ID에 다른 편집 내용을 보낼 수 없습니다.");
                return CareerRosterStore.read(r.getString(2),Receipt.class);
            },command);
            if (!old.isEmpty()) return new Change(true,old.getFirst());
            if (revision!=request.expectedRevision()) throw conflict("다른 편집이 저장되었습니다. 최신 데이터를 불러온 뒤 다시 저장해 주세요.");
            long next=revision+1;
            jdbc.update("MERGE INTO player_data_override (player_id,ratings_json,potential_ability,revision) KEY(player_id) VALUES (?,?,?,?)",
                    request.playerId(),CareerRosterStore.write(request.ratings()),request.potentialAbility(),next);
            jdbc.update("UPDATE player_data_revision SET revision=? WHERE id=1",next);
            var p=player(catalog.players().get(request.playerId()),new Override(request.playerId(),request.ratings(),request.potentialAbility(),next));
            var receipt=new Receipt(command,request.playerId(),next,APPLICATION,p);
            jdbc.update("INSERT INTO player_data_command VALUES (?,?,?)",command,payload,CareerRosterStore.write(receipt));
            return new Change(false,receipt);
        });
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
