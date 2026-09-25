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

        List<ListDocumentsTool.DocumentSummary> result = toolWithMaxItems(1000).listDocuments().documents();

        assertThat(result).extracting(ListDocumentsTool.DocumentSummary::name)
            .containsExactlyInAnyOrder("report.qvw", "dashboard.qvf");
    }

    @Test
    void returnsEmptyListWhenNoDocumentsFound() {
        assertThat(toolWithMaxItems(1000).listDocuments().documents()).isEmpty();
    }

    @Test
    void truncatesWhenDocumentCountExceedsConfiguredLimit() throws IOException {
        Files.createFile(tempDir.resolve("a.qvw"));
        Files.createFile(tempDir.resolve("b.qvw"));

        List<ListDocumentsTool.DocumentSummary> result = toolWithMaxItems(1).listDocuments().documents();

        assertThat(result).hasSize(1);
    }

    private ListDocumentsTool toolWithMaxItems(int maxItems) {
        QlikViewProperties properties = new QlikViewProperties(
            List.of(tempDir.toString()),
            new QlikViewProperties.Call(Duration.ofSeconds(1)),
            new QlikViewProperties.Output(1000, maxItems));
        return new ListDocumentsTool(new DocumentScanner(properties), new OutputLimiter(properties));
    }
}
