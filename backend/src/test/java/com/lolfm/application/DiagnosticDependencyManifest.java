package com.lolfm.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Explicit source dependency identity for diagnostic harnesses. */
public final class DiagnosticDependencyManifest {
    public static final String SCHEMA = "DIAGNOSTIC_DEPENDENCY_MANIFEST_V1";
    private DiagnosticDependencyManifest() { }

    public static Manifest create(Path backendRoot, String manifestId, String inclusionRule,
                                  List<DependencySpec> specifications) throws IOException {
        Path root = backendRoot.toAbsolutePath().normalize();
        if (specifications.isEmpty()) throw new IllegalArgumentException("Dependency manifest is empty");
        Set<String> logicalPaths = new HashSet<>();
        ArrayList<Entry> entries = new ArrayList<>();
        for (DependencySpec specification : specifications) {
            if (!logicalPaths.add(specification.logicalPath())) {
                throw new IllegalArgumentException("Duplicate dependency logical path: "
                        + specification.logicalPath());
            }
            Path source = root.resolve(specification.sourcePath()).normalize();
            if (!source.startsWith(root) || !Files.isRegularFile(source)) {
                throw new IllegalArgumentException("Missing or unexpected dependency path: " + source);
            }
            byte[] raw = specification.sectionStart() == null
                    ? Files.readAllBytes(source) : sectionBytes(source, specification);
            byte[] canonical = canonicalize(raw);
            entries.add(new Entry(specification.logicalPath(), portable(specification.sourcePath()),
                    sha256(raw), sha256(canonical), "UTF8_CRLF_TO_LF"));
        }
        entries.sort(Comparator.comparing(Entry::logicalPath));
        StringBuilder identity = new StringBuilder("schema=").append(SCHEMA).append('\n')
                .append("manifestId=").append(manifestId).append('\n')
                .append("inclusionRule=").append(inclusionRule).append('\n');
        entries.forEach(entry -> identity.append(entry.logicalPath()).append('|')
                .append(entry.sourcePath()).append('|').append(entry.rawSha256()).append('|')
                .append(entry.canonicalSha256()).append('|')
                .append(entry.canonicalization()).append('\n'));
        return new Manifest(SCHEMA, manifestId, inclusionRule, List.copyOf(entries),
                sha256(identity.toString().getBytes(StandardCharsets.UTF_8)));
    }

    public static void verify(Manifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        if (!SCHEMA.equals(manifest.schemaVersion()) || manifest.dependencies().isEmpty()) {
            throw new IllegalArgumentException("Invalid dependency manifest");
        }
        Set<String> paths = new HashSet<>();
        StringBuilder identity = new StringBuilder("schema=").append(SCHEMA).append('\n')
                .append("manifestId=").append(manifest.manifestId()).append('\n')
                .append("inclusionRule=").append(manifest.inclusionRule()).append('\n');
        List<Entry> ordered = manifest.dependencies().stream()
                .sorted(Comparator.comparing(Entry::logicalPath)).toList();
        for (Entry entry : ordered) {
            if (!paths.add(entry.logicalPath())) {
                throw new IllegalArgumentException("Duplicate dependency logical path");
            }
            requireHash(entry.rawSha256());
            requireHash(entry.canonicalSha256());
            identity.append(entry.logicalPath()).append('|').append(entry.sourcePath()).append('|')
                    .append(entry.rawSha256()).append('|').append(entry.canonicalSha256()).append('|')
                    .append(entry.canonicalization()).append('\n');
        }
        String expected = sha256(identity.toString().getBytes(StandardCharsets.UTF_8));
        if (!expected.equals(manifest.harnessSourceHash())) {
            throw new IllegalArgumentException("Dependency manifest harness hash mismatch");
        }
    }

    public static Entry requireDependency(Manifest manifest, String logicalPath) {
        verify(manifest);
        return manifest.dependencies().stream()
                .filter(value -> value.logicalPath().equals(logicalPath)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Missing focused proof dependency: " + logicalPath));
    }

    private static byte[] sectionBytes(Path source, DependencySpec specification) throws IOException {
        String raw = Files.readString(source, StandardCharsets.UTF_8);
        int start = raw.indexOf(specification.sectionStart());
        int end = raw.indexOf(specification.sectionEnd());
        if (start < 0 || end < start
                || raw.indexOf(specification.sectionStart(), start + 1) >= 0
                || raw.indexOf(specification.sectionEnd(), end + 1) >= 0) {
            throw new IllegalArgumentException("Missing or duplicate source dependency section: "
                    + specification.logicalPath());
        }
        return (raw.substring(start, end + specification.sectionEnd().length()) + '\n')
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] canonicalize(byte[] raw) {
        return new String(raw, StandardCharsets.UTF_8).replace("\r\n", "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static void requireHash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid dependency SHA-256");
        }
    }

    private static String portable(Path value) {
        return value.normalize().toString().replace('\\', '/');
    }

    public record DependencySpec(String logicalPath, Path sourcePath,
                                 String sectionStart, String sectionEnd) {
        public DependencySpec {
            Objects.requireNonNull(logicalPath); Objects.requireNonNull(sourcePath);
            if ((sectionStart == null) != (sectionEnd == null)) {
                throw new IllegalArgumentException("Both section markers are required");
            }
        }

        public static DependencySpec file(String logicalPath) {
            return new DependencySpec(logicalPath, Path.of(logicalPath), null, null);
        }

        public static DependencySpec section(String logicalPath, String sourcePath,
                                             String start, String end) {
            return new DependencySpec(logicalPath, Path.of(sourcePath), start, end);
        }
    }

    public record Entry(String logicalPath, String sourcePath, String rawSha256,
                        String canonicalSha256, String canonicalization) { }

    public record Manifest(String schemaVersion, String manifestId, String inclusionRule,
                           List<Entry> dependencies, String harnessSourceHash) {
        public Manifest { dependencies = List.copyOf(dependencies); }
    }
}
