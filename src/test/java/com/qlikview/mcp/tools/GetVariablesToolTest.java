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

class GetVariablesToolTest {

    @TempDir
    Path tempDir;

    private final QlikViewGateway gateway = mock(QlikViewGateway.class);
    private final SecretRedactor secretRedactor = new SecretRedactor();
    private GetVariablesTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetVariablesTool(guard(), gateway, secretRedactor, outputLimiter());
    }

    @Test
    void readsVariablesFromGateway() {
        Path document = tempDir.resolve("report.qvw");
        when(gateway.getVariables(document)).thenReturn(new QlikViewGateway.VariableDescription[]{
            new QlikViewGateway.VariableDescription("vLimit", "42", false),
            new QlikViewGateway.VariableDescription("ThousandSep", ",", true)
        });

        List<GetVariablesTool.VariableSummary> result = tool.getVariables(document.toString(), null).variables();

        assertThat(result).containsExactlyInAnyOrder(
            new GetVariablesTool.VariableSummary("vLimit", "42", false, false),
            new GetVariablesTool.VariableSummary("ThousandSep", ",", true, false));
    }

    @Test
    void flagsVariableWithSecretLikeName() {
        Path document = tempDir.resolve("report.qvw");
        when(gateway.getVariables(document)).thenReturn(new QlikViewGateway.VariableDescription[]{
            new QlikViewGateway.VariableDescription("vPassword", "hunter2", false)
        });

        List<GetVariablesTool.VariableSummary> result = tool.getVariables(document.toString(), null).variables();
        assertThat(result).containsExactly(new GetVariablesTool.VariableSummary("vPassword", "hunter2", false, true));
    }

    @Test
    void doesNotFlagOrdinaryVariableName() {
        Path document = tempDir.resolve("report.qvw");
        when(gateway.getVariables(document)).thenReturn(new QlikViewGateway.VariableDescription[]{
            new QlikViewGateway.VariableDescription("vLimit", "10", false)
        });

        List<GetVariablesTool.VariableSummary> result = tool.getVariables(document.toString(), null).variables();
        assertThat(result).extracting(GetVariablesTool.VariableSummary::possibleSecret).containsExactly(false);
    }

    @Test
    void nameFilterAppliesCaseInsensitively() {
        Path document = tempDir.resolve("report.qvw");
        when(gateway.getVariables(document)).thenReturn(new QlikViewGateway.VariableDescription[]{
            new QlikViewGateway.VariableDescription("vLimit", "42", false),
            new QlikViewGateway.VariableDescription("vOther", "1", false)
        });

        List<GetVariablesTool.VariableSummary> result = tool.getVariables(document.toString(), "limit").variables();
        assertThat(result).extracting(GetVariablesTool.VariableSummary::name).containsExactly("vLimit");
    }

    @Test
    void truncatesWhenVariableCountExceedsConfiguredLimit() {
        tool = new GetVariablesTool(guard(), gateway, secretRedactor, outputLimiter(1));
        Path document = tempDir.resolve("report.qvw");
        when(gateway.getVariables(document)).thenReturn(new QlikViewGateway.VariableDescription[]{
            new QlikViewGateway.VariableDescription("vLimit", "42", false),
            new QlikViewGateway.VariableDescription("vOther", "1", false)
        });

        List<GetVariablesTool.VariableSummary> result = tool.getVariables(document.toString(), null).variables();
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
            new QlikViewProperties.Call(Duration.ofSeconds(1)),
            new QlikViewProperties.Output(1000, maxItems));
    }
}
