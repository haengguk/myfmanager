package com.lolfm.testsupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/** Scans only explicitly supported UTF-8 frontend source files. */
public final class FrontendTextSourceScanner {
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".ts", ".tsx", ".js", ".jsx", ".css", ".html", ".json",
            ".mjs", ".cjs", ".md", ".svg");
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            "node_modules", "dist", "build", "out", "coverage");

    private FrontendTextSourceScanner() {
    }

    public static List<Path> textSourceFiles(Path frontendSourceRoot) throws IOException {
        Path root = Objects.requireNonNull(frontendSourceRoot, "frontendSourceRoot")
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IOException("Missing frontend source directory: " + root);
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(path -> path.startsWith(root))
                    .filter(path -> !hasExcludedDirectory(root.relativize(path)))
                    .filter(FrontendTextSourceScanner::hasTextExtension)
                    .sorted(Comparator.comparing(path -> relativePath(root, path)))
                    .toList();
        }
    }

    public static List<Path> filesContaining(Path frontendSourceRoot, String forbiddenText)
            throws IOException {
        String forbidden = Objects.requireNonNull(forbiddenText, "forbiddenText");
        ArrayList<Path> matches = new ArrayList<>();
        for (Path path : textSourceFiles(frontendSourceRoot)) {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            if (source.contains(forbidden)) matches.add(path);
        }
        return List.copyOf(matches);
    }

    private static boolean hasExcludedDirectory(Path relativePath) {
        for (Path part : relativePath) {
            if (EXCLUDED_DIRECTORIES.contains(part.toString())) return true;
        }
        return false;
    }

    private static boolean hasTextExtension(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return TEXT_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static String relativePath(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }
}
