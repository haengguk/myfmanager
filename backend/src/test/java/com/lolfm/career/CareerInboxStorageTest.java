package com.lolfm.career;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;

class CareerInboxStorageTest {
    @TempDir java.nio.file.Path directory;
    @Test void boundedPagesReadWatermarkReplayAndFileReopenPreserveOriginalFacts() {
        String url="jdbc:h2:file:"+directory.resolve("inbox")+";DB_CLOSE_ON_EXIT=FALSE";var ds=new DriverManagerDataSource(url,"sa","");var db=new JdbcTemplate(ds);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V26__career_inbox_and_observation_index.sql")).execute(ds);
        db.execute("CREATE TABLE career_save(career_id VARCHAR(80),managed_team_code VARCHAR(16))");db.update("INSERT INTO career_save VALUES ('career','T1'),('other','GEN')");
        var tx=new DataSourceTransactionManager(ds);var inbox=new CareerInboxService(db,tx,null,null,null);var date=LocalDate.of(2027,12,31);
        for(int n=0;n<30;n++)CareerInboxStore.add(db,"career",2027,item("fact-"+n,date));
        CareerInboxStore.add(db,"other",2027,item("other",date));
        var first=inbox.page("career",2027,"",false,null,0,List.of());assertThat(first.items()).hasSize(25);assertThat(first.unread()).isEqualTo(30);
        CareerInboxStore.add(db,"career",2027,item("arrived-during-reading",date));
        var second=inbox.page("career",2027,"",false,first.asOf(),first.nextCursor(),List.of());assertThat(second.items()).hasSize(5);assertThat(second.items()).extracting(CareerInboxService.Entry::sequence).doesNotContainAnyElementsOf(first.items().stream().map(CareerInboxService.Entry::sequence).toList());
        var request=new CareerInboxService.ReadRequest(null,first.asOf(),2027,"",false);assertThat(inbox.markRead("career",request).changed()).isEqualTo(30);assertThat(inbox.markRead("career",request).changed()).isZero();
        assertThat(inbox.page("career",2027,"",false,null,0,List.of()).unread()).isOne();assertThat(inbox.page("other",2027,"",false,null,0,List.of()).unread()).isOne();
        CareerInboxStore.add(db,"career",2027,item("fact-0",date));assertThat(db.queryForObject("SELECT COUNT(*) FROM career_inbox_item WHERE career_id='career'",Integer.class)).isEqualTo(31);
        var original=db.queryForList("SELECT source_key,item_json,read_flag FROM career_inbox_item ORDER BY sequence");db.execute("SHUTDOWN");
        var reopened=new JdbcTemplate(new DriverManagerDataSource(url,"sa",""));assertThat(reopened.queryForList("SELECT source_key,item_json,read_flag FROM career_inbox_item ORDER BY sequence")).isEqualTo(original);
        CareerInboxStore.add(reopened,"career",2028,item("new-season",date.plusDays(1)));
        var restored=new CareerInboxService(reopened,new DataSourceTransactionManager(reopened.getDataSource()),null,null,null);assertThat(restored.page("career",2027,"",false,null,0,List.of()).items()).allSatisfy(e->assertThat(e.item().date()).isEqualTo(date));
        assertThatThrownBy(()->restored.markRead("other",new CareerInboxService.ReadRequest(first.items().getFirst().sequence(),null,null,null,false))).isInstanceOf(CareerException.class);
        reopened.execute("SHUTDOWN");
    }
    static CareerInboxStore.Item item(String key,LocalDate date){return new CareerInboxStore.Item(key,"SEASON_RECAP",date,"당시 결산","보존 사실","LCK:T1",null,null,false,CareerInboxStore.Link.of("RECAP",null,null,null,date.getYear()),CareerInboxStore.facts(Map.of("seasonYear",date.getYear(),"closingCash",123)));}
}
