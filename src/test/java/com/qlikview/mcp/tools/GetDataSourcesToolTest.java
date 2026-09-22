package com.qlikview.mcp.tools;

import com.qlikview.mcp.analysis.DataSourceParser;
import com.qlikview.mcp.analysis.ScriptReader;
import com.qlikview.mcp.config.QlikViewProperties;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.GuardException;
import com.qlikview.mcp.guard.OutputLimiter;
import com.qlikview.mcp.guard.SecretRedactor;
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

class GetDataSourcesToolTest {

    @TempDir
    Path tempDir;

    private final ScriptReader scriptReader = new ScriptReader();
    private final DataSourceParser dataSourceParser = new DataSourceParser();
    private final SecretRedactor secretRedactor = new SecretRedactor();
    private GetDataSourcesTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetDataSourcesTool(guard(), scriptReader, dataSourceParser, secretRedactor, outputLimiter());
    }

    @Test
    void redactsCredentialsInConnectionString() throws IOException {
        Path document = writeDocumentWithScript(
            "ODBC CONNECT TO [MyDataSource];SET ConnectString='UID=admin;PWD=hunter2;Server=x';");

        GetDataSourcesTool.DataSourcesSummary result = tool.getDataSources(document.toString());

        assertThat(result.connections()).hasSize(1);
        String connectionString = result.connections().getFirst().connectionString();
        assertThat(connectionString).contains("PWD=***REDACTED***").doesNotContain("hunter2");
    }

    @Test
    void leavesConnectionStringUnchangedWhenNoCredentials() throws IOException {
        Path document = writeDocumentWithScript("ODBC CONNECT TO [MyDataSource];");

        GetDataSourcesTool.DataSourcesSummary result = tool.getDataSources(document.toString());
        assertThat(result.connections()).containsExactly(new GetDataSourcesTool.ConnectionSummary("ODBC", "[MyDataSource]"));
    }

    @Test
    void parsesFileSourcesIncludesAndBinary() throws IOException {
        Path document = writeDocumentWithScript(
            "BINARY 'other.qvw';\nLOAD * FROM 'sales.qvd' (qvd);\n$(Include='setup.qvs');");

        GetDataSourcesTool.DataSourcesSummary result = tool.getDataSources(document.toString());

        assertThat(result.binarySource()).isEqualTo("other.qvw");
        assertThat(result.fileSources()).containsExactly(new GetDataSourcesTool.FileSourceSummary("sales.qvd", "(qvd)"));
        assertThat(result.includes()).containsExactly(new GetDataSourcesTool.IncludeSummary("setup.qvs", false));
    }

    @Test
    void truncatesEachListIndependentlyWhenExceedingConfiguredLimit() throws IOException {
        tool = new GetDataSourcesTool(guard(), scriptReader, dataSourceParser, secretRedactor, outputLimiter(1));
        Path document = writeDocumentWithScript(
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

    @Test
    void throwsWhenNoPrjExportExists() {
        Path document = tempDir.resolve("nodoc.qvw");

        assertThatThrownBy(() -> tool.getDataSources(document.toString()))
            .isInstanceOf(GuardException.class)
            .hasMessageContaining("No -prj export found");
    }

    private Path writeDocumentWithScript(String script) throws IOException {
        Path document = tempDir.resolve("report.qvw");
        Path prjFolder = Files.createDirectory(tempDir.resolve("report-prj"));
        Files.writeString(prjFolder.resolve("LoadScript.txt"), script, StandardCharsets.UTF_8);
        return document;
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
