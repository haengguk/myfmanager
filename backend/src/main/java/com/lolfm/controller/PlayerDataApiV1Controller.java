package com.lolfm.controller;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lolfm.player.PlayerDataStore;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/player-data")
@CrossOrigin(origins="http://localhost:5173")
public final class PlayerDataApiV1Controller {
    private final PlayerDataStore store;
    private final ObjectMapper json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public PlayerDataApiV1Controller(PlayerDataStore store) { this.store=store; }
    @GetMapping public PlayerDataStore.View view() { return store.view(); }
    @PostMapping public PlayerDataStore.Change edit(@RequestBody byte[] body) {
        PlayerDataStore.Edit request;
        try {
            var node=json.readTree(body);
            var keys=new java.util.HashSet<String>(); node.fieldNames().forEachRemaining(keys::add);
            if (!keys.equals(Set.of("playerId","ratings","potentialAbility","expectedRevision","clientCommandId"))
                    || !node.path("playerId").isTextual() || !node.path("clientCommandId").isTextual()
                    || !node.path("ratings").isObject() || !node.path("expectedRevision").isIntegralNumber()
                    || !node.path("expectedRevision").canConvertToLong()
                    || !(node.path("potentialAbility").isNull() || node.path("potentialAbility").isInt())) throw new IllegalArgumentException();
            for (var rating:node.path("ratings")) if (!rating.isInt()) throw new IllegalArgumentException();
            request=json.treeToValue(node,PlayerDataStore.Edit.class);
        } catch (Exception invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"선수 편집 요청 형식과 정수 값을 확인해 주세요."); }
        return store.edit(request);
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> failure(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message",error.getReason()==null?"선수 데이터 요청 실패":error.getReason()));
    }
}
