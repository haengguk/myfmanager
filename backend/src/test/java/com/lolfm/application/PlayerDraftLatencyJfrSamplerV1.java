package com.lolfm.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingFile;

/** Small test-only CPU/allocation sample for Player Draft and Match Engine boundaries. */
public final class PlayerDraftLatencyJfrSamplerV1 {
    public static final Duration EXECUTION_PERIOD = Duration.ofMillis(10);

    private PlayerDraftLatencyJfrSamplerV1() { }

    public static Session start() {
        Recording recording = new Recording();
        recording.setName("player-draft-interactive-simulation-latency-v1");
        recording.enable("jdk.ExecutionSample").withPeriod(EXECUTION_PERIOD);
        recording.enable("jdk.ObjectAllocationSample");
        recording.start();
        return new Session(recording);
    }

    public static final class Session implements AutoCloseable {
        private final Recording recording;
        private boolean finished;

        private Session(Recording recording) {
            this.recording = recording;
        }

        public Profile finish(Path destination) {
            if (finished) throw new IllegalStateException("JFR session already finished");
            finished = true;
            try {
                Files.createDirectories(destination.toAbsolutePath().getParent());
                recording.stop();
                recording.dump(destination);
                return read(destination);
            } catch (IOException error) {
                throw new IllegalStateException("PLAYER_DRAFT_LATENCY_JFR_CAPTURE_FAILED", error);
            } finally {
                recording.close();
            }
        }

        @Override
        public void close() {
            if (!finished) {
                finished = true;
                recording.stop();
                recording.close();
            }
        }
    }

    public static Profile read(Path recording) {
        try {
            Map<String, Long> cpu = new LinkedHashMap<>();
            Map<String, Long> allocationFrames = new LinkedHashMap<>();
            Map<String, Long> allocationClasses = new LinkedHashMap<>();
            long allCpu = 0L;
            long relevantCpu = 0L;
            long allAllocations = 0L;
            long relevantAllocations = 0L;
            long relevantSampledBytes = 0L;
            for (RecordedEvent event : RecordingFile.readAllEvents(recording)) {
                String type = event.getEventType().getName();
                if ("jdk.ExecutionSample".equals(type)) {
                    allCpu++;
                    String frame = firstRelevantFrame(event);
                    if (frame != null) {
                        relevantCpu++;
                        cpu.merge(frame, 1L, Long::sum);
                    }
                } else if ("jdk.ObjectAllocationSample".equals(type)) {
                    allAllocations++;
                    String frame = firstRelevantFrame(event);
                    if (frame != null) {
                        relevantAllocations++;
                        long weight = event.hasField("weight")
                                ? event.getLong("weight") : 1L;
                        relevantSampledBytes += weight;
                        allocationFrames.merge(frame, weight, Long::sum);
                        if (event.hasField("objectClass")) {
                            RecordedClass objectClass = event.getClass("objectClass");
                            if (objectClass != null) {
                                allocationClasses.merge(objectClass.getName(), weight, Long::sum);
                            }
                        }
                    }
                }
            }
            return new Profile(EXECUTION_PERIOD.toMillis(), allCpu, relevantCpu,
                    allAllocations, relevantAllocations, relevantSampledBytes,
                    top(cpu), top(allocationFrames), top(allocationClasses),
                    "JFR_SAMPLE_WEIGHTS_ARE_HOTSPOT_CANDIDATES_NOT_CPU_PERCENTAGES_OR_EXACT_ALLOCATION_TOTALS");
        } catch (IOException error) {
            throw new IllegalStateException("PLAYER_DRAFT_LATENCY_JFR_READ_FAILED", error);
        }
    }

    private static String firstRelevantFrame(RecordedEvent event) {
        if (event.getStackTrace() == null) return null;
        for (RecordedFrame frame : event.getStackTrace().getFrames()) {
            if (frame.getMethod() == null || frame.getMethod().getType() == null) continue;
            String owner = frame.getMethod().getType().getName();
            if (owner.startsWith("com.lolfm.draft.")
                    || owner.startsWith("com.lolfm.application.")
                    || owner.startsWith("com.lolfm.simulator.")) {
                return owner + "." + frame.getMethod().getName();
            }
        }
        return null;
    }

    private static List<Hotspot> top(Map<String, Long> values) {
        ArrayList<Hotspot> result = new ArrayList<>();
        values.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(30)
                .forEach(entry -> result.add(new Hotspot(entry.getKey(), entry.getValue())));
        return List.copyOf(result);
    }

    public record Profile(
            long executionSamplePeriodMillis,
            long allExecutionSamples,
            long relevantExecutionSamples,
            long allAllocationSamples,
            long relevantAllocationSamples,
            long relevantAllocationSampledBytes,
            List<Hotspot> cpuHotspots,
            List<Hotspot> allocationHotspots,
            List<Hotspot> allocatedClasses,
            String interpretationLimit
    ) {
        public Profile {
            cpuHotspots = List.copyOf(cpuHotspots);
            allocationHotspots = List.copyOf(allocationHotspots);
            allocatedClasses = List.copyOf(allocatedClasses);
        }
    }

    public record Hotspot(String key, long sampledValue) { }
}
