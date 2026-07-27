package com.lolfm.simulator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class ChampionMatchupRuleEngineCsv {
    private ChampionMatchupRuleEngineCsv() {
    }

    static void records(Path path, List<? extends Record> rows) throws IOException {
        if (rows.isEmpty()) throw new IllegalArgumentException("Record rows required");
        RecordComponent[] fields = rows.getFirst().getClass().getRecordComponents();
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            line(writer, java.util.Arrays.stream(fields)
                    .map(RecordComponent::getName).toArray(String[]::new));
            for (Record row : rows) {
                String[] values = new String[fields.length];
                for (int index = 0; index < fields.length; index++) {
                    try {
                        values[index] = String.valueOf(fields[index].getAccessor().invoke(row));
                    } catch (ReflectiveOperationException error) {
                        throw new IOException("Cannot serialize record", error);
                    }
                }
                line(writer, values);
            }
        }
    }

    static void summary(Path path, Map<String, ?> values) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            line(writer, "key", "value");
            for (Map.Entry<String, ?> value : values.entrySet()) {
                line(writer, value.getKey(), String.valueOf(value.getValue()));
            }
        }
    }

    static void headerOnly(Path path, String... header) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            line(writer, header);
        }
    }

    static void lines(Path path, String[] header, List<String[]> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            line(writer, header);
            for (String[] row : rows) line(writer, row);
        }
    }

    private static void line(BufferedWriter writer, String... values) throws IOException {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) writer.write(',');
            writer.write(escape(values[index]));
        }
        writer.newLine();
    }

    private static String escape(String value) {
        if (value == null) return "";
        if (!value.contains(",") && !value.contains("\"")
                && !value.contains("\n") && !value.contains("\r")) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
