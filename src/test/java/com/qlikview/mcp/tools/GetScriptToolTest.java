package com.qlikview.mcp.tools;

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

class GetScriptToolTest {

    @TempDir
    Path tempDir;

    private final QlikViewGateway gateway = mock(QlikViewGateway.class);
    private final SecretRedactor secretRedactor = new SecretRedactor();
    private GetScriptTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetScriptTool(guard(), gateway, outputLimiter(1000), secretRedactor);
    }

    @Test
    void returnsScriptTextUnchangedWhenNoCredentials() {
        Path document = tempDir.resolve("report.qvw");
        when(gateway.getScript(document)).thenReturn("LOAD RecNo() as FactID AUTOGENERATE 10;");

        assertThat(tool.getScript(document.toString())).isEqualTo("LOAD RecNo() as FactID AUTOGENERATE 10;");
    }

    @Test
    void redactsConnectionCredentialsInScript() {
        Path document = tempDir.resolve("report.qvw");
        when(gateway.getScript(document)).thenReturn(
            "ODBC CONNECT TO [DataSource];\nSET ConnectString = 'UID=admin;PWD=hunter2;Server=x';");

        String result = tool.getScript(document.toString());

        assertThat(result).contains("PWD=***REDACTED***").doesNotContain("hunter2");
    }

    @Test
    void truncatesWhenScriptExceedsConfiguredLimit() {
        tool = new GetScriptTool(guard(), gateway, outputLimiter(10), secretRedactor);
        Path document = tempDir.resolve("report.qvw");
        when(gateway.getScript(document)).thenReturn("LOAD RecNo() as FactID AUTOGENERATE 10;");

        assertThat(tool.getScript(document.toString())).hasSize(10);
    }

    private DocumentPathGuard guard() {
        return new DocumentPathGuard(properties(1000));
    }

    private OutputLimiter outputLimiter(int maxChars) {
        return new OutputLimiter(properties(maxChars));
    }

    private QlikViewProperties properties(int maxChars) {
        return new QlikViewProperties(
            List.of(tempDir.toString()),
            new QlikViewProperties.Call(Duration.ofSeconds(1)),
            new QlikViewProperties.Output(maxChars, 1000));
    }
}
