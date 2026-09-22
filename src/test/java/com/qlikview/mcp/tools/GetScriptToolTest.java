package com.qlikview.mcp.tools;

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

class GetScriptToolTest {

    @TempDir
    Path tempDir;

    private final ScriptReader scriptReader = new ScriptReader();
    private final SecretRedactor secretRedactor = new SecretRedactor();
    private GetScriptTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetScriptTool(guard(), scriptReader, outputLimiter(1000), secretRedactor);
    }

    @Test
    void returnsScriptTextUnchangedWhenNoCredentials() throws IOException {
        Path document = writeDocumentWithScript("LOAD RecNo() as FactID AUTOGENERATE 10;");
        assertThat(tool.getScript(document.toString())).isEqualTo("LOAD RecNo() as FactID AUTOGENERATE 10;");
    }

    @Test
    void redactsConnectionCredentialsInScript() throws IOException {
        Path document = writeDocumentWithScript(
            "ODBC CONNECT TO [DataSource];\nSET ConnectString = 'UID=admin;PWD=hunter2;Server=x';");

        String result = tool.getScript(document.toString());
        assertThat(result).contains("PWD=***REDACTED***").doesNotContain("hunter2");
    }

    @Test
    void truncatesWhenScriptExceedsConfiguredLimit() throws IOException {
        tool = new GetScriptTool(guard(), scriptReader, outputLimiter(10), secretRedactor);
        Path document = writeDocumentWithScript("LOAD RecNo() as FactID AUTOGENERATE 10;");
        assertThat(tool.getScript(document.toString())).hasSize(10);
    }

    @Test
    void throwsWhenNoPrjExportExists() {
        Path document = tempDir.resolve("nodoc.qvw");

        assertThatThrownBy(() -> tool.getScript(document.toString()))
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

    private OutputLimiter outputLimiter(int maxChars) {
        return new OutputLimiter(properties(maxChars));
    }

    private QlikViewProperties properties(int maxChars) {
        return new QlikViewProperties(
            List.of(tempDir.toString()),
            new QlikViewProperties.Worker("unused"),
            new QlikViewProperties.Call(Duration.ofSeconds(1)),
            new QlikViewProperties.Output(maxChars));
    }
}
