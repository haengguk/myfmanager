package com.lolfm.application;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Objects;

/** Canonical raw-byte serialization shared by immutable baseline generators. */
final class BaselineArtifactJson {
    private static final String CANONICAL_LINE_SEPARATOR = "\r\n";

    private BaselineArtifactJson() {
    }

    static byte[] write(ObjectMapper mapper, Object value) throws IOException {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentObjectsWith(new DefaultIndenter("  ", CANONICAL_LINE_SEPARATOR));
        return Objects.requireNonNull(mapper, "mapper")
                .writer(printer)
                .writeValueAsBytes(Objects.requireNonNull(value, "value"));
    }
}
