package com.qlikview.mcp.tools;

import com.qlikview.mcp.config.QlikViewProperties;
import com.qlikview.mcp.gateway.QlikViewGateway;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.GuardException;
import com.qlikview.mcp.guard.OutputLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluateToolTest {

    @TempDir
    Path tempDir;

    private final QlikViewGateway gateway = mock(QlikViewGateway.class);
    private EvaluateTool tool;

    @BeforeEach
    void setUp() {
        tool = new EvaluateTool(guard(), outputLimiter(1000), gateway);
    }

    @Test
    void returnsGatewayEvaluationResult() {
        Path document = tempDir.resolve("report.qvw");
        when(gateway.evaluate(document, "=Sum(SalesAmount)")).thenReturn("12345");

        String result = tool.evaluate(document.toString(), "=Sum(SalesAmount)");
        assertThat(result).isEqualTo("12345");
    }

    @Test
    void truncatesWhenResultExceedsConfiguredLimit() {
        tool = new EvaluateTool(guard(), outputLimiter(5), gateway);
        Path document = tempDir.resolve("report.qvw");
        when(gateway.evaluate(document, "=Text")).thenReturn("this result is too long");

        String result = tool.evaluate(document.toString(), "=Text");
        assertThat(result).hasSize(5);
    }

    @Test
    void rejectsDocumentOutsideAllowlistedRoots() {
        Path outsideDocument = tempDir.resolve("..").resolve("elsewhere.qvw").normalize();
        assertThatThrownBy(() -> tool.evaluate(outsideDocument.toString(), "=1"))
            .isInstanceOf(GuardException.class);
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
            new QlikViewProperties.Output(maxChars, 1000));
    }
}
