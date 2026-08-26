package com.lolfm.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Recursive raw-byte SHA-256 manifest with stable relative-path ordering. */
public final class RecursiveArtifactManifest {
    public static final String FILE_NAME = "SHA256SUMS.txt";
    private RecursiveArtifactManifest() { }

    public static String write(Path root) throws IOException {
        Path normalized = root.toAbsolutePath().normalize();
        List<Path> files = artifactFiles(normalized);
        StringBuilder output = new StringBuilder();
        for (Path file : files) {
            output.append(DiagnosticDependencyManifest.sha256(Files.readAllBytes(file)))
                    .append("  ").append(portable(normalized.relativize(file))).append('\n');
        }
        writeAtomic(normalized.resolve(FILE_NAME), output.toString().getBytes(StandardCharsets.UTF_8));
        verify(normalized);
        return DiagnosticDependencyManifest.sha256(
                Files.readAllBytes(normalized.resolve(FILE_NAME)));
    }

    public static Verification verify(Path root) throws IOException {
        Path normalized = root.toAbsolutePath().normalize();
        Path manifest = normalized.resolve(FILE_NAME);
        if (!Files.isRegularFile(manifest)) throw new IllegalStateException("Missing root manifest");
        LinkedHashMap<String, String> expected = new LinkedHashMap<>();
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            int separator = line.indexOf("  ");
            if (separator != 64) throw new IllegalStateException("Invalid recursive manifest line");
            String hash = line.substring(0, separator);
            String path = line.substring(separator + 2);
            if (!hash.matches("[0-9a-f]{64}") || path.isBlank()
                    || expected.put(path, hash) != null) {
                throw new IllegalStateException("Invalid recursive manifest identity");
            }
        }
        LinkedHashMap<String, String> actual = new LinkedHashMap<>();
        for (Path file : artifactFiles(normalized)) {
            actual.put(portable(normalized.relativize(file)),
                    DiagnosticDependencyManifest.sha256(Files.readAllBytes(file)));
        }
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Recursive artifact manifest coverage or payload mismatch");
        }
        long nested = actual.keySet().stream().filter(path -> path.contains("/")).count();
        return new Verification(actual.size(), (int) nested,
                DiagnosticDependencyManifest.sha256(Files.readAllBytes(manifest)));
    }

    private static List<Path> artifactFiles(Path root) throws IOException {
        ArrayList<Path> values = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> !path.equals(root.resolve(FILE_NAME)))
                    .filter(path -> !path.getFileName().toString().contains(".tmp-"))
                    .forEach(values::add);
        }
        values.sort(Comparator.comparing(path -> portable(root.relativize(path))));
        return List.copyOf(values);
    }

    private static void writeAtomic(Path path, byte[] payload) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp-"
                + ProcessHandle.current().pid());
        Files.write(temporary, payload);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException error) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String portable(Path value) {
        return value.normalize().toString().replace('\\', '/');
    }

    public record Verification(int fileCount, int nestedFileCount, String manifestRawSha256) { }
}
