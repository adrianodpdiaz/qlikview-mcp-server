package com.qlikview.mcp.tools;

import com.qlikview.mcp.analysis.DocumentScanner;
import com.qlikview.mcp.config.QlikViewProperties;
import com.qlikview.mcp.guard.OutputLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListDocumentsToolTest {

    @TempDir
    Path tempDir;

    @Test
    void listsDocumentsFoundUnderConfiguredRoots() throws IOException {
        Files.createFile(tempDir.resolve("report.qvw"));
        Files.createFile(tempDir.resolve("dashboard.qvf"));
        Files.createFile(tempDir.resolve("notes.txt"));

        List<ListDocumentsTool.DocumentSummary> result = toolWithMaxItems(1000).listDocuments();

        assertThat(result).extracting(ListDocumentsTool.DocumentSummary::name)
            .containsExactlyInAnyOrder("report.qvw", "dashboard.qvf");
    }

    @Test
    void reportsPrjFolderPresence() throws IOException {
        Files.createFile(tempDir.resolve("report.qvw"));
        Files.createDirectory(tempDir.resolve("report-prj"));
        Files.createFile(tempDir.resolve("other.qvw"));

        List<ListDocumentsTool.DocumentSummary> result = toolWithMaxItems(1000).listDocuments();

        assertThat(result)
            .filteredOn(d -> d.name().equals("report.qvw"))
            .extracting(ListDocumentsTool.DocumentSummary::hasPrjFolder)
            .containsExactly(true);
        assertThat(result)
            .filteredOn(d -> d.name().equals("other.qvw"))
            .extracting(ListDocumentsTool.DocumentSummary::hasPrjFolder)
            .containsExactly(false);
    }

    @Test
    void returnsEmptyListWhenNoDocumentsFound() {
        assertThat(toolWithMaxItems(1000).listDocuments()).isEmpty();
    }

    @Test
    void truncatesWhenDocumentCountExceedsConfiguredLimit() throws IOException {
        Files.createFile(tempDir.resolve("a.qvw"));
        Files.createFile(tempDir.resolve("b.qvw"));

        List<ListDocumentsTool.DocumentSummary> result = toolWithMaxItems(1).listDocuments();

        assertThat(result).hasSize(1);
    }

    private ListDocumentsTool toolWithMaxItems(int maxItems) {
        QlikViewProperties properties = new QlikViewProperties(
            List.of(tempDir.toString()),
            new QlikViewProperties.Worker("unused"),
            new QlikViewProperties.Call(Duration.ofSeconds(1)),
            new QlikViewProperties.Output(1000, maxItems));
        return new ListDocumentsTool(new DocumentScanner(properties), new OutputLimiter(properties));
    }
}
