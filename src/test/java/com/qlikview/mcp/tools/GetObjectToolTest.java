package com.qlikview.mcp.tools;

import com.qlikview.mcp.config.QlikViewProperties;
import com.qlikview.mcp.gateway.QlikViewGateway;
import com.qlikview.mcp.guard.DocumentPathGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetObjectToolTest {

    @TempDir
    Path tempDir;

    private final QlikViewGateway gateway = mock(QlikViewGateway.class);
    private GetObjectTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetObjectTool(guard(), gateway);
    }

    @Test
    void readsChartObjectFromGateway() {
        Path document = tempDir.resolve("report.qvw");
        when(gateway.getObject(document, "Document\\CH03")).thenReturn(new QlikViewGateway.ObjectDetail(
            "Document\\CH03", "11", new String[]{"CustomerName"}, new String[]{"Sum(SalesAmount)"}));

        GetObjectTool.ObjectSummary result = tool.getObject(document.toString(), "Document\\CH03");

        assertThat(result.type()).isEqualTo("11");
        assertThat(result.dimensions()).containsExactly("CustomerName");
        assertThat(result.expressions()).containsExactly("Sum(SalesAmount)");
    }

    @Test
    void readsNonChartObjectWithEmptyDimensionsAndExpressions() {
        Path document = tempDir.resolve("report.qvw");
        when(gateway.getObject(document, "Document\\CS01")).thenReturn(new QlikViewGateway.ObjectDetail(
            "Document\\CS01", "23", new String[0], new String[0]));

        GetObjectTool.ObjectSummary result = tool.getObject(document.toString(), "Document\\CS01");

        assertThat(result.type()).isEqualTo("23");
        assertThat(result.dimensions()).isEmpty();
        assertThat(result.expressions()).isEmpty();
    }

    private DocumentPathGuard guard() {
        QlikViewProperties properties = new QlikViewProperties(
            List.of(tempDir.toString()),
            new QlikViewProperties.Worker("unused"),
            new QlikViewProperties.Call(Duration.ofSeconds(1)),
            new QlikViewProperties.Output(1000, 1000));
        return new DocumentPathGuard(properties);
    }
}
