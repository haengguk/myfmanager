package com.lolfm.career;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static com.lolfm.career.CareerRosterStore.*;

/** Prepared operating windows, not evidence that the omitted training/AI season was simulated. */
public final class CareerOperatingDateFixture {
    private CareerOperatingDateFixture() {}

    /** Shift the unused operating window, preserving contract durations, relative due dates and money. */
    public static void fresh(JdbcTemplate jdbc,String career,LocalDate date) {
        var growth=CareerDevelopmentStore.load(jdbc,career).state();
        // Its complete first-week daily history is retained, so monthly totals can be regrouped without losing history.
        if(java.time.temporal.ChronoUnit.DAYS.between(growth.initializedOn(),growth.nextSettlement())>7)
            throw new IllegalStateException("fixture requires the first operating week");
        long days=java.time.temporal.ChronoUnit.DAYS.between(CareerMarketStore.load(jdbc,career).state().processedThrough(),date);
        move(jdbc,career,date,()->{
            for(String table:java.util.List.of("career_market_command","career_training_command"))
                if(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE career_id=?",Integer.class,career)!=0)
                    throw new IllegalStateException("fixture requires no operating commands");
            var dates=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
                    .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            // Normalize typed LocalDate values for translation; persisted snapshots use Jackson date arrays.
            // Only mutable operating snapshots: never rewrite source directory, frozen rosters or command receipts.
            for(String table:java.util.List.of("career_market_state","career_development_state")) {
                String original=jdbc.queryForObject("SELECT state_json FROM "+table+" WHERE career_id=?",String.class,career);
                Object state=table.equals("career_market_state")?read(original,CareerMarketState.class):read(original,CareerDevelopmentState.class);
                com.fasterxml.jackson.databind.JsonNode tree=dates.valueToTree(state);
                shiftDates(tree,days);
                String json=write(table.equals("career_market_state")?dates.convertValue(tree,CareerMarketState.class):dates.convertValue(tree,CareerDevelopmentState.class));
                if(table.equals("career_development_state")) {
                    var shifted=read(json,CareerDevelopmentState.class);
                    var monthly=new java.util.TreeMap<String,CareerDevelopmentState.Gain>();
                    for(var gain:shifted.gains()) {
                        var month=gain.date().withDayOfMonth(1);
                        var total=new CareerDevelopmentState.Gain(month,gain.playerId(),gain.internalGain(),gain.proficiencyGain(),gain.integerRises(),gain.seasonYear());
                        monthly.merge(gain.seasonYear()+"|"+month+"|"+gain.playerId(),total,CareerDevelopmentEngine::merge);
                    }
                    json=write(new CareerDevelopmentState(shifted.policyVersion(),shifted.initializationVersion(),shifted.initializedOn(),shifted.nextSettlement(),shifted.players(),shifted.teamPlans(),shifted.gains(),monthly));
                }
                jdbc.update("UPDATE "+table+" SET revision=revision+1,state_json=?,state_hash=? WHERE career_id=?",json,hash(json),career);
            }
            if(!CareerMarketStore.load(jdbc,career).state().processedThrough().equals(date)
                    ||!CareerDevelopmentStore.load(jdbc,career).state().nextSettlement().equals(date))
                throw new IllegalStateException("fixture operating dates were not translated");
            CareerMarketStore.engine(jdbc,career,activeYear(jdbc,career),CareerMarketStore.load(jdbc,career)).validateIntegrity();
        });
    }
    private static void shiftDates(com.fasterxml.jackson.databind.JsonNode node,long days) {
        if(node.isObject()) {
            var object=(com.fasterxml.jackson.databind.node.ObjectNode)node;
            for(var field:new ArrayList<>(object.properties())) {
                var value=field.getValue();
                if(value.isTextual()&&value.asText().matches("\\d{4}-\\d{2}-\\d{2}"))
                    object.put(field.getKey(),LocalDate.parse(value.asText()).plusDays(days).toString());
                else shiftDates(value,days);
            }
        } else if(node.isArray())node.forEach(child->shiftDates(child,days));
    }

    /** Keep carried contracts, money, ratings and receipts; settle salary and start a short final-season window. */
    public static void carried(JdbcTemplate jdbc,String career,LocalDate date) {
        // Complete negotiations that were actually opened around the first rollover before preparing the later window.
        for(int i=0;i<4&&pendingNegotiation(CareerMarketStore.load(jdbc,career).state());i++) {
            var next=CareerMarketStore.load(jdbc,career).state().processedThrough().plusDays(7);
            if(!next.isBefore(date))throw new IllegalStateException("fixture has no omitted interval");
            move(jdbc,career,next,()->CareerMarketStore.processThrough(jdbc,career,next));
        }
        move(jdbc,career,date,()->{
            var old=CareerMarketStore.load(jdbc,career);var s=old.state();
            if(s.offers().values().stream().anyMatch(CareerMarketState.Offer::open)
                    ||s.contracts().values().stream().anyMatch(c->c.status()==CareerMarketState.ContractStatus.SCHEDULED&&!c.terms().startDate().isAfter(date)
                    ||c.status()==CareerMarketState.ContractStatus.ACTIVE&&c.terms().endDate().isBefore(date))
                    ||s.management().loans().values().stream().anyMatch(l->"ACTIVE".equals(l.status())&&!l.endDate().isAfter(date))
                    ||s.management().trades().values().stream().anyMatch(CareerManagementState.Trade::open))
                throw new IllegalStateException("fixture would skip a pending contract boundary at "+date+", offers="+s.offers().values().stream().filter(CareerMarketState.Offer::open).count()+", open trades="+s.management().trades().values().stream().filter(CareerManagementState.Trade::open).count()+", active loans="+s.management().loans().values().stream().filter(l->"ACTIVE".equals(l.status())).map(l->l.endDate().toString()).toList());
            var engine=CareerMarketStore.engine(jdbc,career,activeYear(jdbc,career),old);
            for(LocalDate month=s.processedThrough().withDayOfMonth(1).plusMonths(1).minusDays(1);month.isBefore(date);month=month.plusDays(1).plusMonths(1).minusDays(1)) {
                if(engine.finance!=null)engine.finance.date(month);
                for(var c:new ArrayList<>(engine.contracts.values()))if(c.status()==CareerMarketState.ContractStatus.ACTIVE)engine.pay(c,month);
            }
            for(var c:new ArrayList<>(engine.contracts.values()))if(c.status()==CareerMarketState.ContractStatus.ACTIVE)engine.pay(c,date);
            var paid=engine.state();
            var prepared=new CareerMarketState(paid.policyVersion(),paid.seed(),date,paid.contracts(),paid.offers(),paid.accounts(),paid.preferences(),paid.freeAgents(),paid.ledger(),paid.decisions(),paid.events(),paid.management(),paid.squadPlanning(),paid.finance());
            String json=write(prepared);
            jdbc.update("UPDATE career_market_state SET revision=revision+1,state_json=?,state_hash=? WHERE career_id=?",json,hash(json),career);
            var development=CareerDevelopmentStore.load(jdbc,career);var d=development.state();
            json=write(new CareerDevelopmentState(d.policyVersion(),d.initializationVersion(),d.initializedOn(),date,d.players(),d.teamPlans(),d.gains(),d.monthly()));
            jdbc.update("UPDATE career_development_state SET revision=revision+1,state_json=?,state_hash=? WHERE career_id=?",json,hash(json),career);
        });
    }

    private static boolean pendingNegotiation(CareerMarketState state) {
        return state.offers().values().stream().anyMatch(CareerMarketState.Offer::open)
                ||state.management().trades().values().stream().anyMatch(CareerManagementState.Trade::open);
    }

    /** Calendar command identity/revision and event cursor remain valid; all preparation shares its transaction. */
    public static void uninitialized(JdbcTemplate jdbc,String career,LocalDate date) {
        move(jdbc,career,date,()->{
            if(CareerMarketStore.load(jdbc,career)!=null||CareerDevelopmentStore.load(jdbc,career)!=null)
                throw new IllegalStateException("fixture requires uninitialized operation");
        });
    }
    private static void move(JdbcTemplate jdbc,String career,LocalDate date,Runnable preparation) {
        var template=new CareerCalendarTemplate(new com.fasterxml.jackson.databind.ObjectMapper());
        var store=new CareerCalendarRelationalStore(jdbc,new DataSourceTransactionManager(jdbc.getDataSource()),template);
        var state=store.find(career).orElseThrow();
        store.execute(UUID.randomUUID().toString(),career,state.calendarRevision(),"ADVANCE_ONE_DAY",
                template.advancePayloadHash(career,state.calendarRevision(),"ADVANCE_ONE_DAY"),row->{
                    if(!date.isAfter(row.currentDate()))throw new IllegalArgumentException("fixture date must advance");
                    preparation.run();
                    return new CareerCalendarRelationalStore.AdvanceMutation(date,template.eventCursor(template.project(activeYear(jdbc,career)),date),row.lastProcessedEventId(),row.lastProcessedDate(),"ACTIVE",null,true,false,200,null,false);
                });
    }
}
