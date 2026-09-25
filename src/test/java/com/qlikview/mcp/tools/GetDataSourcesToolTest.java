package com.qlikview.mcp.tools;

import com.qlikview.mcp.analysis.DataSourceParser;
import com.qlikview.mcp.config.QlikViewProperties;
import com.qlikview.mcp.gateway.QlikViewGateway;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.OutputLimiter;
import com.qlikview.mcp.guard.SecretRedactor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetDataSourcesToolTest {

    @TempDir
    Path tempDir;

    private final QlikViewGateway gateway = mock(QlikViewGateway.class);
    private final DataSourceParser dataSourceParser = new DataSourceParser();
    private final SecretRedactor secretRedactor = new SecretRedactor();
    private GetDataSourcesTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetDataSourcesTool(guard(), gateway, dataSourceParser, secretRedactor, outputLimiter());
    }

    @Test
    void redactsCredentialsInConnectionString() {
        Path document = tempDir.resolve("report.qvw");
        when(gateway.getScript(document)).thenReturn(
            "ODBC CONNECT TO [MyDataSource];SET ConnectString='UID=admin;PWD=hunter2;Server=x';");

        GetDataSourcesTool.DataSourcesSummary result = tool.getDataSources(document.toString());

        assertThat(result.connections()).hasSize(1);
        String connectionString = result.connections().getFirst().connectionString();
        assertThat(connectionString).contains("PWD=***REDACTED***").doesNotContain("hunter2");
    }

    @Test
    void leavesConnectionStringUnchangedWhenNoCredentials() {
        Path document = tempDir.resolve("report.qvw");
        when(gateway.getScript(document)).thenReturn("ODBC CONNECT TO [MyDataSource];");

        GetDataSourcesTool.DataSourcesSummary result = tool.getDataSources(document.toString());
        assertThat(result.connections()).containsExactly(new GetDataSourcesTool.ConnectionSummary("ODBC", "[MyDataSource]"));
    }

    @Test
    void parsesFileSourcesIncludesAndBinary() {
        Path document = tempDir.resolve("report.qvw");
        when(gateway.getScript(document)).thenReturn(
            "BINARY 'other.qvw';\nLOAD * FROM 'sales.qvd' (qvd);\n$(Include='setup.qvs');");

        GetDataSourcesTool.DataSourcesSummary result = tool.getDataSources(document.toString());

        assertThat(result.binarySource()).isEqualTo("other.qvw");
        assertThat(result.fileSources()).containsExactly(new GetDataSourcesTool.FileSourceSummary("sales.qvd", "(qvd)"));
        assertThat(result.includes()).containsExactly(new GetDataSourcesTool.IncludeSummary("setup.qvs", false));
    }

    @Test
    void truncatesEachListIndependentlyWhenExceedingConfiguredLimit() {
        tool = new GetDataSourcesTool(guard(), gateway, dataSourceParser, secretRedactor, outputLimiter(1));
        Path document = tempDir.resolve("report.qvw");
        when(gateway.getScript(document)).thenReturn(
            """
                ODBC CONNECT TO [First];
                CONNECT TO [Second];
                LOAD * FROM 'a.qvd' (qvd);
                LOAD * FROM 'b.qvd' (qvd);
                $(Include='one.qvs');
                $(Include='two.qvs');""");

        GetDataSourcesTool.DataSourcesSummary result = tool.getDataSources(document.toString());

        assertThat(result.connections()).hasSize(1);
        assertThat(result.fileSources()).hasSize(1);
        assertThat(result.includes()).hasSize(1);
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
            new QlikViewProperties.Call(Duration.ofSeconds(1)),
            new QlikViewProperties.Output(1000, maxItems));
    }
}
