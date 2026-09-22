package com.qlikview.mcp.tools;

import com.qlikview.mcp.analysis.ChartReader;
import com.qlikview.mcp.analysis.ProjectIndexReader;
import com.qlikview.mcp.config.QlikViewProperties;
import com.qlikview.mcp.gateway.QlikViewGateway;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.GuardException;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetObjectToolTest {

    @TempDir
    Path tempDir;

    @Language("XML")
    private static final String CHART_FIXTURE = """
        <GraphProperties>
          <Dimensions>
            <ChartDimensionDataDef>
              <PseudoDef><Name>CustomerName</Name></PseudoDef>
            </ChartDimensionDataDef>
          </Dimensions>
          <Expressions>
            <ArrayOfMainExpressionData>
              <MainExpressionData>
                <Data>
                  <ExpressionData>
                    <Definition><v>Sum(SalesAmount)</v></Definition>
                  </ExpressionData>
                </Data>
              </MainExpressionData>
            </ArrayOfMainExpressionData>
          </Expressions>
        </GraphProperties>
        """;

    private static final String NON_CHART_FIXTURE = "<CurrentSelectionProperties></CurrentSelectionProperties>";

    private final ProjectIndexReader projectIndexReader = new ProjectIndexReader();
    private final ChartReader chartReader = new ChartReader();
    private final QlikViewGateway gateway = mock(QlikViewGateway.class);
    private GetObjectTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetObjectTool(guard(), projectIndexReader, chartReader, gateway);
    }

    @Test
    void defaultsToStaticSourceForChartObject() throws IOException {
        Path document = writeDocumentWithObject("CH03", CHART_FIXTURE);

        GetObjectTool.ObjectSummary result = tool.getObject(document.toString(), "Document\\CH03", null);

        assertThat(result.type()).isEqualTo("GraphProperties");
        assertThat(result.dimensions()).containsExactly("CustomerName");
        assertThat(result.expressions()).containsExactly("Sum(SalesAmount)");
    }

    @Test
    void staticSourceReturnsEmptyDimensionsForNonChartObject() throws IOException {
        Path document = writeDocumentWithObject("CS01", NON_CHART_FIXTURE);

        GetObjectTool.ObjectSummary result = tool.getObject(document.toString(), "Document\\CS01", null);

        assertThat(result.type()).isEqualTo("CurrentSelectionProperties");
        assertThat(result.dimensions()).isEmpty();
        assertThat(result.expressions()).isEmpty();
    }

    @Test
    void liveTrueReadsFromGatewayInstead() throws IOException {
        Path document = writeDocumentWithObject("CH03", CHART_FIXTURE);
        when(gateway.getObject(document, "Document\\CH03")).thenReturn(new QlikViewGateway.ObjectDetail(
            "Document\\CH03", "11", new String[]{"CustomerName"}, new String[]{"Sum(SalesAmount)"}));

        GetObjectTool.ObjectSummary result = tool.getObject(document.toString(), "Document\\CH03", true);

        assertThat(result.type()).isEqualTo("11");
        assertThat(result.dimensions()).containsExactly("CustomerName");
        assertThat(result.expressions()).containsExactly("Sum(SalesAmount)");
    }

    @Test
    void throwsWhenObjectNotFoundInStaticExport() throws IOException {
        Path document = writeDocumentWithObject("CH03", CHART_FIXTURE);

        assertThatThrownBy(() -> tool.getObject(document.toString(), "Document\\CH99", null))
            .isInstanceOf(GuardException.class)
            .hasMessageContaining("was not found");
    }

    private Path writeDocumentWithObject(String objectFileBaseName, String objectXml) throws IOException {
        Path document = tempDir.resolve("report.qvw");
        Path prjFolder = Files.createDirectory(tempDir.resolve("report-prj"));
        Files.writeString(prjFolder.resolve(objectFileBaseName + ".xml"), objectXml, StandardCharsets.UTF_8);
        return document;
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
