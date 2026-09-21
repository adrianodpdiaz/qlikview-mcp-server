package com.qlikview.mcp.tools;

import com.qlikview.mcp.analysis.ScriptReader;
import com.qlikview.mcp.analysis.VariableParser;
import com.qlikview.mcp.config.QlikViewProperties;
import com.qlikview.mcp.gateway.QlikViewGateway;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.GuardException;
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

class GetVariablesToolTest {

    @TempDir
    Path tempDir;

    private final ScriptReader scriptReader = new ScriptReader();
    private final VariableParser variableParser = new VariableParser();
    private final QlikViewGateway gateway = mock(QlikViewGateway.class);
    private GetVariablesTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetVariablesTool(guard(), scriptReader, variableParser, gateway);
    }

    @Test
    void defaultsToStaticScriptVariables() throws IOException {
        Path document = writeDocumentWithScript("SET vLimit = 10;\nLET vToday = Today();");

        List<GetVariablesTool.VariableSummary> result = tool.getVariables(document.toString(), null, null);

        assertThat(result).containsExactlyInAnyOrder(
            new GetVariablesTool.VariableSummary("vLimit", "10", false, false),
            new GetVariablesTool.VariableSummary("vToday", "Today()", true, false));
    }

    @Test
    void liveTrueReadsFromGatewayInstead() throws IOException {
        Path document = writeDocumentWithScript("SET vLimit = 10;");
        when(gateway.getVariables(document)).thenReturn(new QlikViewGateway.VariableDescription[]{
            new QlikViewGateway.VariableDescription("vLimit", "42", false),
            new QlikViewGateway.VariableDescription("ThousandSep", ",", true)
        });

        List<GetVariablesTool.VariableSummary> result = tool.getVariables(document.toString(), null, true);

        assertThat(result).containsExactlyInAnyOrder(
                new GetVariablesTool.VariableSummary("vLimit", "42", false, false),
                new GetVariablesTool.VariableSummary("ThousandSep", ",", false, true));
    }

    @Test
    void liveFalseExplicitlyUsesStaticSource() throws IOException {
        Path document = writeDocumentWithScript("SET vLimit = 10;");

        List<GetVariablesTool.VariableSummary> result = tool.getVariables(document.toString(), null, false);
        assertThat(result).containsExactly(new GetVariablesTool.VariableSummary("vLimit", "10", false, false));
    }

    @Test
    void nameFilterAppliesToStaticVariables() throws IOException {
        Path document = writeDocumentWithScript("SET vLimit = 10;\nSET vOther = 20;");

        List<GetVariablesTool.VariableSummary> result = tool.getVariables(document.toString(), "limit", null);
        assertThat(result).extracting(GetVariablesTool.VariableSummary::name).containsExactly("vLimit");
    }

    @Test
    void nameFilterAppliesToLiveVariablesToo() throws IOException {
        Path document = writeDocumentWithScript("SET vLimit = 10;");
        when(gateway.getVariables(document)).thenReturn(new QlikViewGateway.VariableDescription[]{
                new QlikViewGateway.VariableDescription("vLimit", "42", false),
                new QlikViewGateway.VariableDescription("ThousandSep", ",", true)
        });

        List<GetVariablesTool.VariableSummary> result = tool.getVariables(document.toString(), "limit", true);
        assertThat(result).extracting(GetVariablesTool.VariableSummary::name).containsExactly("vLimit");
    }

    @Test
    void throwsWhenNoPrjExportExistsForStaticSource() {
        Path document = tempDir.resolve("nodoc.qvw");

        assertThatThrownBy(() -> tool.getVariables(document.toString(), null, null))
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
        QlikViewProperties properties = new QlikViewProperties(
            List.of(tempDir.toString()),
            new QlikViewProperties.Worker("unused"),
            new QlikViewProperties.Call(Duration.ofSeconds(1)),
            new QlikViewProperties.Output(1000));
        return new DocumentPathGuard(properties);
    }
}
