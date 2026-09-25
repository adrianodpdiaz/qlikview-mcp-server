package com.qlikview.mcp.analysis;

import com.qlikview.mcp.config.QlikViewProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void findsQvwAndQvfFilesUnderRoots() throws IOException {
        Files.createFile(tempDir.resolve("report.qvw"));
        Files.createFile(tempDir.resolve("dashboard.qvf"));
        Files.createFile(tempDir.resolve("notes.txt"));

        List<DocumentScanner.DocumentInfo> results = scannerWithRoots(tempDir.toString()).listDocuments();

        assertThat(results).extracting(DocumentScanner.DocumentInfo::name)
            .containsExactlyInAnyOrder("report.qvw", "dashboard.qvf");
    }

    @Test
    void findsDocumentsInSubfolders() throws IOException {
        Path subfolder = Files.createDirectory(tempDir.resolve("sub"));
        Files.createFile(subfolder.resolve("nested.qvw"));

        List<DocumentScanner.DocumentInfo> results = scannerWithRoots(tempDir.toString()).listDocuments();

        assertThat(results).extracting(DocumentScanner.DocumentInfo::name).containsExactly("nested.qvw");
    }

    @Test
    void returnsEmptyListWhenNoRootsConfigured() {
        List<DocumentScanner.DocumentInfo> results = scannerWithRoots().listDocuments();
        assertThat(results).isEmpty();
    }

    @Test
    void ignoresRootThatDoesNotExist() {
        List<DocumentScanner.DocumentInfo> results =
            new DocumentScanner(propertiesWithRoots(tempDir.resolve("does-not-exist").toString())).listDocuments();
        assertThat(results).isEmpty();
    }

    private DocumentScanner scannerWithRoots(String... roots) {
        return new DocumentScanner(propertiesWithRoots(roots));
    }

    private QlikViewProperties propertiesWithRoots(String... roots) {
        return new QlikViewProperties(
            List.of(roots),
            new QlikViewProperties.Call(Duration.ofSeconds(1)),
            new QlikViewProperties.Output(1000, 1000));
    }
}
