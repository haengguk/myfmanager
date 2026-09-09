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
        if(enabled)worker.schedule(this::cycle,0,TimeUnit.MILLISECONDS);
    }
    private void cycle() {
        tick();
        if(worker.isShutdown())return;
        long delay=1000;
        try {if(!store.due().isEmpty())delay=0;}
        catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Continuous wake lookup failed",failure);}
        try {worker.schedule(this::cycle,delay,TimeUnit.MILLISECONDS);}
        catch(RejectedExecutionException shuttingDown){if(!worker.isShutdown())throw shuttingDown;}
    }
    void tick() {
        long started=System.nanoTime(); // Operational fairness budget; never a gameplay input.

        try {for(String career:store.due()) {
            try {service.step(career,owner);}
            catch(RuntimeException error) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Continuous run {} could not be read or scheduled",career,error);
                // Preserve corrupt payloads. A broken save must not starve other Careers.
                try {store.defer(career);} catch(RuntimeException deferFailure) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("Continuous retry scheduling failed",deferFailure);
                }
            }
            // Yield the executor after a bounded batch. Due ordering rotates Careers whose
            // completed step wrote a new wake time; pending external jobs keep their backoff.
            if(System.nanoTime()-started>=TimeUnit.MILLISECONDS.toNanos(250))break;
        }}
        catch(RuntimeException error){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Continuous coordinator scheduling failed",error);}
    }
    @PreDestroy public void close(){worker.shutdownNow();}
}
