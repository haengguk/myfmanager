package com.lolfm.career;

import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Bounded coordinator. The existing Auto executors own match work and their own fences/heartbeats. */
@Component
public final class CareerContinuousWorker {
    private final CareerContinuousStore store;
    private final CareerContinuousApplicationService service;
    private final boolean enabled;
    private final String owner=UUID.randomUUID().toString();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{
        Thread t=new Thread(r,"career-continuous-coordinator");t.setDaemon(true);return t;
    });
    public CareerContinuousWorker(CareerContinuousStore store,CareerContinuousApplicationService service,
            @Value("${lolfm.career.continuous.background.enabled:true}")boolean enabled) {
        this.store=store;this.service=service;this.enabled=enabled;
    }
    @EventListener(ApplicationReadyEvent.class)
    @Order(100)
    public void start() {
        if(enabled)worker.scheduleWithFixedDelay(this::tick,0,1,TimeUnit.SECONDS);
    }
    void tick() {
        try {for(String career:store.due()) {
            try {service.step(career,owner);}
            catch(RuntimeException error) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Continuous run {} could not be read or scheduled",career,error);
                // Preserve corrupt payloads. A broken save must not starve other Careers.
                try {store.defer(career);} catch(RuntimeException deferFailure) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("Continuous retry scheduling failed",deferFailure);
                }
            }
        }}
        catch(RuntimeException error){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Continuous coordinator scheduling failed",error);}
    }
    @PreDestroy public void close(){worker.shutdownNow();}
}
