package com.qlikview.mcp.tools;

import com.qlikview.mcp.config.QlikViewProperties;
import com.qlikview.mcp.gateway.QlikViewGateway;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.OutputLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetSheetsToolTest {

    @TempDir
    Path tempDir;

    private final QlikViewGateway gateway = mock(QlikViewGateway.class);
    private GetSheetsTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetSheetsTool(guard(), gateway, outputLimiter());
    }

    @Test
    void readsSheetsFromGateway() {
        Path document = tempDir.resolve("report.qvw");
        when(gateway.getSheets(document)).thenReturn(new QlikViewGateway.SheetInfo[]{
            new QlikViewGateway.SheetInfo("Main", new QlikViewGateway.SheetObjectInfo[]{
                new QlikViewGateway.SheetObjectInfo("Document\\CH03", "11")
            })
        });

        List<GetSheetsTool.SheetSummary> result = tool.getSheets(document.toString());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().caption()).isEqualTo("Main");
        assertThat(result.getFirst().objects().getFirst().objectType()).isEqualTo("11");
    }

    @Test
    void truncatesWhenSheetCountExceedsConfiguredLimit() {
        tool = new GetSheetsTool(guard(), gateway, outputLimiter(1));
        Path document = tempDir.resolve("report.qvw");
        when(gateway.getSheets(document)).thenReturn(new QlikViewGateway.SheetInfo[]{
            new QlikViewGateway.SheetInfo("Main", new QlikViewGateway.SheetObjectInfo[0]),
            new QlikViewGateway.SheetInfo("Second", new QlikViewGateway.SheetObjectInfo[0])
        });

        List<GetSheetsTool.SheetSummary> result = tool.getSheets(document.toString());
        assertThat(result).hasSize(1);
    }

    private DocumentPathGuard guard() {
        return new DocumentPathGuard(properties(1000));
    }

    private OutputLimiter outputLimiter() {
        return outputLimiter(1000);
    }

    private OutputLimiter outputLimiter(int maxItems) {
        return new OutputLimiter(properties(maxItems));
    }

    private QlikViewProperties properties(int maxItems) {
        return new QlikViewProperties(
            List.of(tempDir.toString()),
            new QlikViewProperties.Worker("unused"),
            new QlikViewProperties.Call(Duration.ofSeconds(1)),
            new QlikViewProperties.Output(1000, maxItems));
    }
}
