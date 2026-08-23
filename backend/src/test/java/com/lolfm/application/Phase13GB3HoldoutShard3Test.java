package com.lolfm.application;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Tag("diagnostic")
public class Phase13GB3HoldoutShard3Test extends Phase13GB3HoldoutShardTestSupport {
    @Test void executesShard() throws Exception { runShard(3); }
}
