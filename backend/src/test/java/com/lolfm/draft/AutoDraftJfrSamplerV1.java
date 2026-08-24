package com.lolfm.draft;

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

/** Test-only JFR sampling. Counts are profiler samples, never exact operation counts. */
public final class AutoDraftJfrSamplerV1 {
    public static final Duration EXECUTION_SAMPLE_PERIOD = Duration.ofMillis(10);

    private AutoDraftJfrSamplerV1() { }

    public static Session start() {
        Recording recording = new Recording();
        recording.setName("auto-draft-scalability-v1");
        recording.enable("jdk.ExecutionSample").withPeriod(EXECUTION_SAMPLE_PERIOD);
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
                throw new IllegalStateException("AUTO_DRAFT_JFR_CAPTURE_FAILED", error);
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
            List<RecordedEvent> events = RecordingFile.readAllEvents(recording);
            Map<String, Long> execution = new LinkedHashMap<>();
            Map<String, Long> allocations = new LinkedHashMap<>();
            Map<String, Long> allocationClasses = new LinkedHashMap<>();
            long executionSamples = 0L;
            long draftExecutionSamples = 0L;
            long allocationSamples = 0L;
            long draftAllocationSamples = 0L;
            long draftAllocationWeight = 0L;
            for (RecordedEvent event : events) {
                String eventName = event.getEventType().getName();
                if ("jdk.ExecutionSample".equals(eventName)) {
                    executionSamples++;
                    String frame = firstDraftFrame(event);
                    if (frame != null) {
                        draftExecutionSamples++;
                        execution.merge(frame, 1L, Long::sum);
                    }
                } else if ("jdk.ObjectAllocationSample".equals(eventName)) {
                    allocationSamples++;
                    String frame = firstDraftFrame(event);
                    if (frame != null) {
                        draftAllocationSamples++;
                        long weight = event.hasField("weight") ? event.getLong("weight") : 1L;
                        draftAllocationWeight += weight;
                        allocations.merge(frame, weight, Long::sum);
                        if (event.hasField("objectClass")) {
                            RecordedClass objectClass = event.getClass("objectClass");
                            if (objectClass != null) {
                                allocationClasses.merge(objectClass.getName(), weight, Long::sum);
                            }
                        }
                    }
                }
            }
            return new Profile(
                    EXECUTION_SAMPLE_PERIOD.toMillis(), executionSamples,
                    draftExecutionSamples, allocationSamples, draftAllocationSamples,
                    draftAllocationWeight, top(execution), top(allocations),
                    top(allocationClasses));
        } catch (IOException error) {
            throw new IllegalStateException("AUTO_DRAFT_JFR_READ_FAILED", error);
        }
    }

    private static String firstDraftFrame(RecordedEvent event) {
        if (event.getStackTrace() == null) return null;
        for (RecordedFrame frame : event.getStackTrace().getFrames()) {
            if (frame.getMethod() == null || frame.getMethod().getType() == null) continue;
            String type = frame.getMethod().getType().getName();
            if (type.startsWith("com.lolfm.draft.")) {
                return type + "." + frame.getMethod().getName();
            }
        }
        return null;
    }

    private static List<Hotspot> top(Map<String, Long> values) {
        ArrayList<Hotspot> result = new ArrayList<>();
        values.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue(
                        Comparator.reverseOrder()).thenComparing(Map.Entry::getKey))
                .limit(30).forEach(entry -> result.add(
                        new Hotspot(entry.getKey(), entry.getValue())));
        return List.copyOf(result);
    }

    public record Profile(long executionSamplePeriodMillis, long allExecutionSamples,
                          long draftExecutionSamples, long allAllocationSamples,
                          long draftAllocationSamples, long draftAllocationSampledBytes,
                          List<Hotspot> draftCpuHotspots,
                          List<Hotspot> draftAllocationHotspots,
                          List<Hotspot> draftAllocatedClasses) {
        public Profile {
            draftCpuHotspots = List.copyOf(draftCpuHotspots);
            draftAllocationHotspots = List.copyOf(draftAllocationHotspots);
            draftAllocatedClasses = List.copyOf(draftAllocatedClasses);
        }
    }

    public record Hotspot(String key, long sampledValue) { }
}
