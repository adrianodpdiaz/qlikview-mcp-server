package com.qlikview.mcp.tools;

import com.qlikview.mcp.gateway.QlikViewGateway;
import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.OutputLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Evaluates a QlikView expression against a document's currently loaded data and selections.
 * This is the one MCP tool with no static, file-based equivalent: it requires QlikView Desktop installed,
 * licensed, and running, with the target document open or reachable, at the moment the tool is called.
 */
@Component
@RequiredArgsConstructor
public class EvaluateTool {

    private final DocumentPathGuard pathGuard;
    private final OutputLimiter outputLimiter;
    private final QlikViewGateway gateway;

    @McpTool(
        name = "evaluate",
        description = "Evaluate a QlikView expression against a document's currently loaded data and selections",
        annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false))
    public String evaluate(
            @McpToolParam(description = "Absolute path to the .qvw/.qvf document", required = true) String document,
            @McpToolParam(description = "QlikView expression to evaluate, e.g. =Sum(SalesAmount)", required = true) String expression) {
        Path resolved = pathGuard.resolve(document);
        String result = gateway.evaluate(resolved, expression);
        return outputLimiter.limit(result).text();
    }
}
