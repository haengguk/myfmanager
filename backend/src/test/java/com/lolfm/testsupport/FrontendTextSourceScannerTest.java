package com.lolfm.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FrontendTextSourceScannerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void binaryAssetsAreNotTextScanTargets() throws Exception {
        Path source = Files.createDirectories(temporaryDirectory.resolve("src"));
        Path text = writeText(source.resolve("App.tsx"), "export const app = true;");
        Files.createDirectories(source.resolve("assets/fonts"));
        Files.write(source.resolve("assets/fonts/PretendardVariable.woff2"),
                new byte[]{(byte) 0xff, (byte) 0xfe, 0x00, 0x01});

        assertThat(FrontendTextSourceScanner.textSourceFiles(source)).containsExactly(text);
        assertThat(FrontendTextSourceScanner.filesContaining(source, "forbidden")).isEmpty();
    }

    @Test
    void forbiddenTextInTypeScriptAndTsxRemainsDetectable() throws Exception {
        Path source = Files.createDirectories(temporaryDirectory.resolve("src"));
        Path typescript = writeText(source.resolve("mode.ts"),
                "export const mode = 'FORBIDDEN_RUNTIME_FLAG';");
        Path component = writeText(source.resolve("Mode.tsx"),
                "export const Mode = () => <>FORBIDDEN_RUNTIME_FLAG</>;");

        assertThat(FrontendTextSourceScanner.filesContaining(
                source, "FORBIDDEN_RUNTIME_FLAG"))
                .containsExactly(component, typescript);
    }

    @Test
    void invalidUtf8InAllowedTextSourceIsNotSilentlyIgnored() throws Exception {
        Path source = Files.createDirectories(temporaryDirectory.resolve("src"));
        Files.write(source.resolve("broken.ts"), new byte[]{(byte) 0xc3, 0x28});

        assertThatThrownBy(() -> FrontendTextSourceScanner.filesContaining(source, "anything"))
                .isInstanceOf(MalformedInputException.class);
    }

    @Test
    void filesOutsideSourceAndBuildOutputDirectoriesAreNotScanned() throws Exception {
        Path source = Files.createDirectories(temporaryDirectory.resolve("frontend/src"));
        writeText(source.resolve("App.ts"), "export const app = true;");
        writeText(temporaryDirectory.resolve("frontend/outside.ts"), "FORBIDDEN_OUTSIDE");
        writeText(source.resolve("node_modules/dependency.ts"), "FORBIDDEN_OUTSIDE");
        writeText(source.resolve("dist/bundle.js"), "FORBIDDEN_OUTSIDE");
        writeText(source.resolve("build/generated.ts"), "FORBIDDEN_OUTSIDE");

        assertThat(FrontendTextSourceScanner.filesContaining(source, "FORBIDDEN_OUTSIDE"))
                .isEmpty();
    }

    private static Path writeText(Path path, String value) throws Exception {
        Files.createDirectories(path.getParent());
        return Files.writeString(path, value, StandardCharsets.UTF_8)
                .toAbsolutePath().normalize();
    }
}
