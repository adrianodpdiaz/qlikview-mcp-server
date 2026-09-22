package com.qlikview.mcp.guard;

import com.qlikview.mcp.config.QlikViewProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentPathGuardTest {

    @TempDir
    Path tempDir;

    @Test
    void acceptsQvwUnderAnAllowlistedRoot() throws IOException {
        Path document = Files.createFile(tempDir.resolve("report.qvw"));
        DocumentPathGuard guard = guardWithRoots(List.of(tempDir.toString()));

        Path resolved = guard.resolve(document.toString());
        assertThat(resolved).isEqualTo(document.toAbsolutePath().normalize());
    }

    @Test
    void acceptsQvfUnderAnAllowlistedRoot() throws IOException {
        Path document = Files.createFile(tempDir.resolve("report.qvf"));
        DocumentPathGuard guard = guardWithRoots(List.of(tempDir.toString()));

        Path resolved = guard.resolve(document.toString());
        assertThat(resolved).isEqualTo(document.toAbsolutePath().normalize());
    }

    @Test
    void rejectsDisallowedExtension() {
        DocumentPathGuard guard = guardWithRoots(List.of(tempDir.toString()));

        assertThatThrownBy(() -> guard.resolve(tempDir.resolve("report.txt").toString()))
            .isInstanceOf(GuardException.class)
            .hasMessageContaining(".qvw or .qvf");
    }

    @Test
    void rejectsPathOutsideAllowlistedRoots() {
        DocumentPathGuard guard = guardWithRoots(List.of(tempDir.resolve("allowed").toString()));

        assertThatThrownBy(() -> guard.resolve(tempDir.resolve("elsewhere/report.qvw").toString()))
            .isInstanceOf(GuardException.class)
            .hasMessageContaining("does not resolve under any allowlisted root");
    }

    @Test
    void rejectsEverythingWhenNoRootsConfigured() {
        DocumentPathGuard guard = guardWithRoots(List.of());

        assertThatThrownBy(() -> guard.resolve(tempDir.resolve("report.qvw").toString()))
            .isInstanceOf(GuardException.class)
            .hasMessageContaining("No allowlisted roots are configured");
    }

    @Test
    void rejectsBlankPath() {
        DocumentPathGuard guard = guardWithRoots(List.of(tempDir.toString()));

        assertThatThrownBy(() -> guard.resolve(" "))
            .isInstanceOf(GuardException.class)
            .hasMessageContaining("must not be blank");
    }

    private DocumentPathGuard guardWithRoots(List<String> roots) {
        QlikViewProperties properties = new QlikViewProperties(
            roots,
            new QlikViewProperties.Worker("unused"),
            new QlikViewProperties.Call(Duration.ofSeconds(1)),
            new QlikViewProperties.Output(1000, 1000));
        return new DocumentPathGuard(properties);
    }
}
